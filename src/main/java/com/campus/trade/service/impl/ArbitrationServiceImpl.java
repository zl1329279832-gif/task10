package com.campus.trade.service.impl;

import com.campus.trade.common.config.TradeConfig;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrationServiceImpl implements ArbitrationService {

    private final ArbitrationMapper arbitrationMapper;
    private final DisputeMapper disputeMapper;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final OrderService orderService;
    private final DisputeService disputeService;
    private final FundSplitService fundSplitService;
    private final EscrowService escrowService;
    private final AuditService auditService;
    private final DistributedLock distributedLock;
    private final TradeConfig tradeConfig;
    private final TransactionTemplate transactionTemplate;

    @Override
    public Arbitration arbitrate(Long adminId, ArbitrationRequest req) {
        Dispute d = disputeMapper.findById(req.getDisputeId());
        if (d == null) throw new BizException(ErrorCode.DISPUTE_NOT_FOUND);

        // Idempotent: if arbitration already exists for this dispute, return it
        Arbitration existing = arbitrationMapper.findByDisputeId(d.getId());
        if (existing != null) {
            log.info("Idempotent: arbitration already exists for disputeId={}, returning existing", d.getId());
            return existing;
        }

        if (!DisputeStatus.ARBITRATING.name().equals(d.getStatus()))
            disputeService.escalateToArbitration(req.getDisputeId());

        try {
            ArbitrationResult.valueOf(req.getResult());
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "Invalid result: " + req.getResult());
        }

        // Validate PARTIAL: refundAmount + sellerAmount must equal payment amount
        Order o = orderMapper.findById(d.getOrderId());
        Payment payment = paymentMapper.findByOrderId(o.getId());
        if (payment == null) throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);

        ArbitrationResult rt = ArbitrationResult.valueOf(req.getResult());
        BigDecimal buyerRefundAmount;
        BigDecimal sellerSettleAmount;

        if (rt == ArbitrationResult.PARTIAL) {
            if (req.getSellerAmount() == null)
                throw new BizException(ErrorCode.FUND_SPLIT_INVALID, "sellerAmount required for PARTIAL");
            buyerRefundAmount = req.getRefundAmount() != null ? req.getRefundAmount() : BigDecimal.ZERO;
            sellerSettleAmount = req.getSellerAmount();
            BigDecimal total = buyerRefundAmount.add(sellerSettleAmount);
            if (total.compareTo(payment.getAmount()) != 0)
                throw new BizException(ErrorCode.FUND_SPLIT_INVALID,
                        "refundAmount(" + buyerRefundAmount + ") + sellerAmount(" + sellerSettleAmount
                                + ") must equal payment amount(" + payment.getAmount() + ")");
        } else if (rt == ArbitrationResult.BUYER_WIN) {
            buyerRefundAmount = payment.getAmount();
            sellerSettleAmount = BigDecimal.ZERO;
        } else { // SELLER_WIN
            buyerRefundAmount = BigDecimal.ZERO;
            sellerSettleAmount = payment.getAmount();
        }

        // Acquire arbitrate lock, then run all DB ops in one transaction
        DistributedLock.LockHandle handle = distributedLock.tryLock("arbitrate:" + d.getId());
        if (handle == null) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            final BigDecimal bra = buyerRefundAmount;
            final BigDecimal ssa = sellerSettleAmount;
            return transactionTemplate.execute(status -> {
                Arbitration a = new Arbitration();
                a.setArbitrationNo(BizNoGenerator.arbitrationNo());
                a.setDisputeId(d.getId());
                a.setOrderId(d.getOrderId());
                a.setArbiterId(adminId);
                a.setResult(req.getResult());
                a.setDecision(req.getDecision());
                a.setRefundAmount(bra);
                a.setSellerAmount(ssa);
                arbitrationMapper.insert(a);

                disputeMapper.updateStatus(d.getId(), "ARBITRATING", "RESOLVED");

                // Use lock-free internal methods since caller holds arbitrate lock
                switch (rt) {
                    case BUYER_WIN -> {
                        fundSplitService.executeFundSplitInternal(a.getId(), o.getId(), payment.getId(),
                                bra, ssa, adminId);
                        orderService.transitionOrderInternal(o.getId(), OrderStatus.REFUNDED.name(), adminId,
                                "Arbitration: buyer wins");
                    }
                    case SELLER_WIN -> {
                        escrowService.unfreezeFundsInternal(o.getId(), adminId, "Arbitration: seller wins");
                        fundSplitService.executeFundSplitInternal(a.getId(), o.getId(), payment.getId(),
                                bra, ssa, adminId);
                        String ts = o.getShipTime() != null ? OrderStatus.SHIPPED.name() : OrderStatus.PAID.name();
                        orderService.transitionOrderInternal(o.getId(), ts, adminId, "Arbitration: seller wins");
                    }
                    case PARTIAL -> {
                        fundSplitService.executeFundSplitInternal(a.getId(), o.getId(), payment.getId(),
                                bra, ssa, adminId);
                        String ts2 = o.getShipTime() != null ? OrderStatus.SHIPPED.name() : OrderStatus.PAID.name();
                        orderService.transitionOrderInternal(o.getId(), ts2, adminId,
                                "Arbitration: partial refund=" + bra + ",sellerSettle=" + ssa);
                    }
                }

                auditService.log(adminId, null, "ARBITRATION", "DECIDE", "DISPUTE", d.getId(),
                        "result=" + req.getResult() + ",buyerRefund=" + bra + ",sellerSettle=" + ssa);
                return a;
            });
        } finally {
            distributedLock.unlock(handle);
        }
    }

    @Override
    public Arbitration reverseArbitration(Long adminId, ArbitrationRequest req) {
        Dispute d = disputeMapper.findById(req.getDisputeId());
        if (d == null) throw new BizException(ErrorCode.DISPUTE_NOT_FOUND);

        Arbitration existing = arbitrationMapper.findByDisputeId(d.getId());
        if (existing == null) throw new BizException(ErrorCode.ARBITRATION_NOT_FOUND);

        try {
            ArbitrationResult.valueOf(req.getResult());
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "Invalid result: " + req.getResult());
        }

        Order o = orderMapper.findById(d.getOrderId());
        Payment payment = paymentMapper.findByOrderId(o.getId());
        if (payment == null) throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);

        ArbitrationResult rt = ArbitrationResult.valueOf(req.getResult());
        BigDecimal buyerRefundAmount;
        BigDecimal sellerSettleAmount;

        if (rt == ArbitrationResult.PARTIAL) {
            if (req.getSellerAmount() == null)
                throw new BizException(ErrorCode.FUND_SPLIT_INVALID, "sellerAmount required for PARTIAL");
            buyerRefundAmount = req.getRefundAmount() != null ? req.getRefundAmount() : BigDecimal.ZERO;
            sellerSettleAmount = req.getSellerAmount();
            BigDecimal total = buyerRefundAmount.add(sellerSettleAmount);
            if (total.compareTo(payment.getAmount()) != 0)
                throw new BizException(ErrorCode.FUND_SPLIT_INVALID);
        } else if (rt == ArbitrationResult.BUYER_WIN) {
            buyerRefundAmount = payment.getAmount();
            sellerSettleAmount = BigDecimal.ZERO;
        } else {
            buyerRefundAmount = BigDecimal.ZERO;
            sellerSettleAmount = payment.getAmount();
        }

        DistributedLock.LockHandle handle = distributedLock.tryLock("arbitrate:" + d.getId());
        if (handle == null) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            final BigDecimal bra = buyerRefundAmount;
            final BigDecimal ssa = sellerSettleAmount;
            return transactionTemplate.execute(status -> {
                // Cancel old fund split using lock-free internal
                fundSplitService.cancelActiveSplitInternal(existing.getId(), adminId);

                // Update arbitration ruling in place
                arbitrationMapper.updateRuling(existing.getId(), req.getResult(), req.getDecision(),
                        bra, ssa);

                // Re-open dispute for re-resolution
                disputeMapper.updateStatus(d.getId(), "RESOLVED", "ARBITRATING");

                // Execute new fund split using lock-free internal methods
                switch (rt) {
                    case BUYER_WIN -> {
                        fundSplitService.executeFundSplitInternal(existing.getId(), o.getId(), payment.getId(),
                                bra, ssa, adminId);
                        if (!OrderStatus.REFUNDED.name().equals(o.getStatus())) {
                            orderService.transitionOrderInternal(o.getId(), OrderStatus.REFUNDED.name(), adminId,
                                    "Arbitration reversal: buyer wins");
                        }
                    }
                    case SELLER_WIN -> {
                        escrowService.unfreezeFundsInternal(o.getId(), adminId, "Arbitration reversal: seller wins");
                        fundSplitService.executeFundSplitInternal(existing.getId(), o.getId(), payment.getId(),
                                bra, ssa, adminId);
                        String ts = o.getShipTime() != null ? OrderStatus.SHIPPED.name() : OrderStatus.PAID.name();
                        if (!ts.equals(o.getStatus())) {
                            orderService.transitionOrderInternal(o.getId(), ts, adminId,
                                    "Arbitration reversal: seller wins");
                        }
                    }
                    case PARTIAL -> {
                        fundSplitService.executeFundSplitInternal(existing.getId(), o.getId(), payment.getId(),
                                bra, ssa, adminId);
                        String ts2 = o.getShipTime() != null ? OrderStatus.SHIPPED.name() : OrderStatus.PAID.name();
                        if (!ts2.equals(o.getStatus())) {
                            orderService.transitionOrderInternal(o.getId(), ts2, adminId,
                                    "Arbitration reversal: partial refund=" + bra);
                        }
                    }
                }

                // Re-resolve dispute
                disputeMapper.updateStatus(d.getId(), "ARBITRATING", "RESOLVED");

                auditService.log(adminId, null, "ARBITRATION", "REVERSE", "DISPUTE", d.getId(),
                        "newResult=" + req.getResult() + ",buyerRefund=" + bra + ",sellerSettle=" + ssa);
                return arbitrationMapper.findById(existing.getId());
            });
        } finally {
            distributedLock.unlock(handle);
        }
    }

    @Override
    public Arbitration getByDisputeId(Long disputeId) {
        Arbitration a = arbitrationMapper.findByDisputeId(disputeId);
        if (a == null) throw new BizException(ErrorCode.ARBITRATION_NOT_FOUND);
        return a;
    }
}
