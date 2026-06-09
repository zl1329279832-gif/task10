package com.campus.trade.service.impl;

import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.domain.entity.*;
import com.campus.trade.domain.enums.ArbitrationResult;
import com.campus.trade.domain.enums.DisputeStatus;
import com.campus.trade.domain.enums.OrderStatus;
import com.campus.trade.domain.enums.PaymentStatus;
import com.campus.trade.dto.request.ArbitrationRequest;
import com.campus.trade.common.util.BizNoGenerator;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.mapper.*;
import com.campus.trade.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrationServiceImpl implements ArbitrationService {

    private final ArbitrationMapper arbitrationMapper;
    private final DisputeMapper disputeMapper;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final OrderService orderService;
    private final DisputeService disputeService;
    private final SettlementService settlementService;
    private final AuditService auditService;
    private final DistributedLock distributedLock;

    @Override
    @Transactional
    public Arbitration arbitrate(Long adminId, ArbitrationRequest req) {
        Dispute d = disputeMapper.findById(req.getDisputeId());
        if (d == null) throw new BizException(ErrorCode.DISPUTE_NOT_FOUND);

        // Ensure dispute is in ARBITRATING state
        if (!DisputeStatus.ARBITRATING.name().equals(d.getStatus())
                && !DisputeStatus.RESOLVED.name().equals(d.getStatus()))
            disputeService.escalateToArbitration(req.getDisputeId());

        // Validate result enum
        ArbitrationResult rt;
        try {
            rt = ArbitrationResult.valueOf(req.getResult());
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "Invalid result: " + req.getResult());
        }

        // Validate partial refund amount
        if (rt == ArbitrationResult.PARTIAL) {
            Order o = orderMapper.findById(d.getOrderId());
            if (req.getRefundAmount() == null || req.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0
                    || req.getRefundAmount().compareTo(o.getTotalAmount()) >= 0)
                throw new BizException(ErrorCode.REFUND_AMOUNT_INVALID);
        }

        // Check for existing active arbitration
        Arbitration existing = arbitrationMapper.findActiveByDisputeId(d.getId());
        if (existing != null) {
            // Idempotent: same result → return existing
            if (existing.getResult().equals(req.getResult())) {
                boolean sameAmount = (existing.getRefundAmount() == null && req.getRefundAmount() == null)
                        || (existing.getRefundAmount() != null && req.getRefundAmount() != null
                        && existing.getRefundAmount().compareTo(req.getRefundAmount()) == 0);
                if (sameAmount) {
                    log.info("Idempotent arbitration request for dispute {}", d.getId());
                    return existing;
                }
            }

            // Overrule: must explicitly request
            if (!Boolean.TRUE.equals(req.getOverrule()))
                throw new BizException(ErrorCode.ARBITRATION_OVERRULE_REQUIRED);

            // Reopen dispute for re-arbitration
            if (DisputeStatus.RESOLVED.name().equals(d.getStatus()))
                disputeMapper.updateStatus(d.getId(), "RESOLVED", "ARBITRATING");
        }

        String lk = "arbitrate:" + d.getId();
        if (!distributedLock.tryLock(lk)) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            // Deactivate previous arbitration if overruling
            if (existing != null && Boolean.TRUE.equals(req.getOverrule())) {
                // We need a placeholder for supersededBy; insert first then update
            }

            Arbitration a = new Arbitration();
            a.setArbitrationNo(BizNoGenerator.arbitrationNo());
            a.setDisputeId(d.getId());
            a.setOrderId(d.getOrderId());
            a.setArbiterId(adminId);
            a.setResult(req.getResult());
            a.setDecision(req.getDecision());
            a.setRefundAmount(req.getRefundAmount());
            a.setIsActive(1);
            arbitrationMapper.insert(a);

            // Deactivate old arbitration with reference to new one
            if (existing != null && Boolean.TRUE.equals(req.getOverrule())) {
                arbitrationMapper.deactivate(existing.getId(), a.getId());
                auditService.log(adminId, null, "ARBITRATION", "OVERRULE", "ARBITRATION", existing.getId(),
                        "supersededBy=" + a.getId());
            }

            disputeMapper.updateStatus(d.getId(), "ARBITRATING", "RESOLVED");

            // Execute the ruling
            Order o = orderMapper.findById(d.getOrderId());
            executeRuling(adminId, a, o, rt);

            auditService.log(adminId, null, "ARBITRATION", "DECIDE", "DISPUTE", d.getId(),
                    "result=" + req.getResult() + ",refundAmount=" + req.getRefundAmount());
            return a;
        } finally {
            distributedLock.unlock(lk);
        }
    }

    private void executeRuling(Long adminId, Arbitration a, Order o, ArbitrationResult rt) {
        switch (rt) {
            case BUYER_WIN -> {
                // Full refund: close payment, create refund record, order → REFUNDED
                Payment p = paymentMapper.findByOrderId(o.getId());
                if (p != null && PaymentStatus.FROZEN.name().equals(p.getStatus())) {
                    paymentMapper.updateStatus(p.getId(), "FROZEN", "CLOSED");
                    paymentMapper.updateUnfreezeInfo(p.getId(), LocalDateTime.now());
                }

                // Create refund record for the full amount
                Refund existingRefund = refundMapper.findByOrderId(o.getId());
                if (existingRefund == null) {
                    Refund r = new Refund();
                    r.setRefundNo(BizNoGenerator.refundNo());
                    r.setOrderId(o.getId());
                    r.setOrderNo(o.getOrderNo());
                    r.setBuyerId(o.getBuyerId());
                    r.setSellerId(o.getSellerId());
                    r.setRefundAmount(o.getTotalAmount());
                    r.setReason("Arbitration: buyer wins - " + a.getDecision());
                    r.setStatus("APPROVED");
                    refundMapper.insert(r);
                }

                orderService.transitionOrder(o.getId(), OrderStatus.REFUNDED.name(), adminId,
                        "Arbitration: buyer wins, full refund");
            }
            case SELLER_WIN -> {
                // Unfreeze funds and settle to seller, order → SETTLED
                settlementService.unfreezeSettlement(o.getId());
                try {
                    settlementService.createSettlement(o.getId());
                } catch (BizException e) {
                    // Settlement may already exist
                    log.debug("Settlement already exists for order {}", o.getId());
                }
                orderService.transitionOrder(o.getId(), OrderStatus.SETTLED.name(), adminId,
                        "Arbitration: seller wins, funds released");
            }
            case PARTIAL -> {
                // Split settlement: partial refund to buyer, rest to seller
                settlementService.splitSettlement(o.getId(), a.getRefundAmount());

                // Create partial refund record
                Refund existingRefund = refundMapper.findByOrderId(o.getId());
                if (existingRefund == null) {
                    Refund r = new Refund();
                    r.setRefundNo(BizNoGenerator.refundNo());
                    r.setOrderId(o.getId());
                    r.setOrderNo(o.getOrderNo());
                    r.setBuyerId(o.getBuyerId());
                    r.setSellerId(o.getSellerId());
                    r.setRefundAmount(a.getRefundAmount());
                    r.setReason("Arbitration: partial refund - " + a.getDecision());
                    r.setStatus("APPROVED");
                    refundMapper.insert(r);
                }

                orderService.transitionOrder(o.getId(), OrderStatus.SETTLED.name(), adminId,
                        "Arbitration: partial refund=" + a.getRefundAmount());
            }
        }
    }
}
