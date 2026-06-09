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
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional
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

        String lk = "arbitrate:" + d.getId();
        if (!distributedLock.tryLock(lk)) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            Arbitration a = new Arbitration();
            a.setArbitrationNo(BizNoGenerator.arbitrationNo());
            a.setDisputeId(d.getId());
            a.setOrderId(d.getOrderId());
            a.setArbiterId(adminId);
            a.setResult(req.getResult());
            a.setDecision(req.getDecision());
            a.setRefundAmount(buyerRefundAmount);
            a.setSellerAmount(sellerSettleAmount);
            arbitrationMapper.insert(a);

            disputeMapper.updateStatus(d.getId(), "ARBITRATING", "RESOLVED");

            // Execute fund operations based on ruling
            switch (rt) {
                case BUYER_WIN -> {
                    // Full refund to buyer, payment -> CLOSED
                    fundSplitService.executeFundSplit(a.getId(), o.getId(), payment.getId(),
                            buyerRefundAmount, sellerSettleAmount, adminId);
                    orderService.transitionOrder(o.getId(), OrderStatus.REFUNDED.name(), adminId, "Arbitration: buyer wins");
                }
                case SELLER_WIN -> {
                    // Full settle to seller, unfreeze handled inside executeFundSplit
                    fundSplitService.executeFundSplit(a.getId(), o.getId(), payment.getId(),
                            buyerRefundAmount, sellerSettleAmount, adminId);
                    String ts = o.getShipTime() != null ? OrderStatus.SHIPPED.name() : OrderStatus.PAID.name();
                    orderService.transitionOrder(o.getId(), ts, adminId, "Arbitration: seller wins");
                }
                case PARTIAL -> {
                    // Partial fund split
                    fundSplitService.executeFundSplit(a.getId(), o.getId(), payment.getId(),
                            buyerRefundAmount, sellerSettleAmount, adminId);
                    String ts2 = o.getShipTime() != null ? OrderStatus.SHIPPED.name() : OrderStatus.PAID.name();
                    orderService.transitionOrder(o.getId(), ts2, adminId,
                            "Arbitration: partial refund=" + buyerRefundAmount + ",sellerSettle=" + sellerSettleAmount);
                }
            }

            auditService.logSync(adminId, null, "ARBITRATION", "DECIDE", "DISPUTE", d.getId(),
                    "result=" + req.getResult() + ",buyerRefund=" + buyerRefundAmount + ",sellerSettle=" + sellerSettleAmount);
            return a;
        } finally {
            distributedLock.unlock(lk);
        }
    }

    @Override
    @Transactional
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

        String lk = "arbitrate:" + d.getId();
        if (!distributedLock.tryLock(lk)) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            // Cancel old fund split (reverses refund/settlement records and re-freezes payment)
            fundSplitService.cancelActiveSplit(existing.getId(), o.getId(), adminId);

            // Update arbitration ruling in place
            arbitrationMapper.updateRuling(existing.getId(), req.getResult(), req.getDecision(),
                    buyerRefundAmount, sellerSettleAmount);

            // Re-open dispute for re-resolution
            disputeMapper.updateStatus(d.getId(), "RESOLVED", "ARBITRATING");

            // Execute new fund split
            switch (rt) {
                case BUYER_WIN -> {
                    fundSplitService.executeFundSplit(existing.getId(), o.getId(), payment.getId(),
                            buyerRefundAmount, sellerSettleAmount, adminId);
                    if (!OrderStatus.REFUNDED.name().equals(o.getStatus())) {
                        orderService.transitionOrder(o.getId(), OrderStatus.REFUNDED.name(), adminId, "Arbitration reversal: buyer wins");
                    }
                }
                case SELLER_WIN -> {
                    fundSplitService.executeFundSplit(existing.getId(), o.getId(), payment.getId(),
                            buyerRefundAmount, sellerSettleAmount, adminId);
                    String ts = o.getShipTime() != null ? OrderStatus.SHIPPED.name() : OrderStatus.PAID.name();
                    if (!ts.equals(o.getStatus())) {
                        orderService.transitionOrder(o.getId(), ts, adminId, "Arbitration reversal: seller wins");
                    }
                }
                case PARTIAL -> {
                    fundSplitService.executeFundSplit(existing.getId(), o.getId(), payment.getId(),
                            buyerRefundAmount, sellerSettleAmount, adminId);
                    String ts2 = o.getShipTime() != null ? OrderStatus.SHIPPED.name() : OrderStatus.PAID.name();
                    if (!ts2.equals(o.getStatus())) {
                        orderService.transitionOrder(o.getId(), ts2, adminId,
                                "Arbitration reversal: partial refund=" + buyerRefundAmount);
                    }
                }
            }

            // Re-resolve dispute
            disputeMapper.updateStatus(d.getId(), "ARBITRATING", "RESOLVED");

            auditService.logSync(adminId, null, "ARBITRATION", "REVERSE", "DISPUTE", d.getId(),
                    "newResult=" + req.getResult() + ",buyerRefund=" + buyerRefundAmount + ",sellerSettle=" + sellerSettleAmount);
            return arbitrationMapper.findById(existing.getId());
        } finally {
            distributedLock.unlock(lk);
        }
    }

    @Override
    public Arbitration getByDisputeId(Long disputeId) {
        Arbitration a = arbitrationMapper.findByDisputeId(disputeId);
        if (a == null) throw new BizException(ErrorCode.ARBITRATION_NOT_FOUND);
        return a;
    }
}
