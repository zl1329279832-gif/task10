package com.campus.trade.service.impl;

import com.campus.trade.common.config.TradeConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.common.util.BizNoGenerator;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.domain.entity.FundSplit;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.entity.Payment;
import com.campus.trade.domain.entity.Refund;
import com.campus.trade.domain.entity.Settlement;
import com.campus.trade.domain.enums.FundSplitStatus;
import com.campus.trade.domain.enums.PaymentStatus;
import com.campus.trade.domain.enums.RefundStatus;
import com.campus.trade.domain.enums.SettlementStatus;
import com.campus.trade.mapper.*;
import com.campus.trade.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundSplitServiceImpl implements FundSplitService {

    private final FundSplitMapper fundSplitMapper;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final SettlementMapper settlementMapper;
    private final OrderMapper orderMapper;
    private final AuditService auditService;
    private final DistributedLock distributedLock;
    private final TradeConfig tradeConfig;
    private final TransactionTemplate transactionTemplate;

    @Override
    public FundSplit executeFundSplit(Long arbitrationId, Long orderId, Long paymentId,
                                      BigDecimal buyerRefundAmount, BigDecimal sellerSettleAmount,
                                      Long operatorId) {
        DistributedLock.LockHandle handle = distributedLock.tryLock("fundsplit:" + arbitrationId);
        if (handle == null) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            return transactionTemplate.execute(status ->
                    executeFundSplitInternal(arbitrationId, orderId, paymentId,
                            buyerRefundAmount, sellerSettleAmount, operatorId));
        } finally {
            distributedLock.unlock(handle);
        }
    }

    @Override
    public FundSplit executeFundSplitInternal(Long arbitrationId, Long orderId, Long paymentId,
                                              BigDecimal buyerRefundAmount, BigDecimal sellerSettleAmount,
                                              Long operatorId) {
        // Idempotency: if active EXECUTED split exists, return it
        FundSplit existing = fundSplitMapper.findActiveByArbitrationId(arbitrationId);
        if (existing != null && FundSplitStatus.EXECUTED.name().equals(existing.getStatus())) {
            log.info("Idempotent: returning existing EXECUTED fund split for arbitrationId={}", arbitrationId);
            return existing;
        }

        Payment payment = paymentMapper.findById(paymentId);
        if (payment == null) throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);

        // Fund conservation guard (Bug 4 fix): amounts must sum to payment total
        BigDecimal total = buyerRefundAmount.add(sellerSettleAmount);
        if (total.compareTo(payment.getAmount()) != 0)
            throw new BizException(ErrorCode.FUND_CONSERVATION_VIOLATED,
                    "buyerRefund(" + buyerRefundAmount + ") + sellerSettle(" + sellerSettleAmount
                            + ") != payment(" + payment.getAmount() + ")");

        // Calculate platform fee on seller portion
        BigDecimal platformFee = sellerSettleAmount
                .multiply(tradeConfig.getPlatformFeeRate())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellerNet = sellerSettleAmount.subtract(platformFee);

        // Create FundSplit record
        FundSplit split = new FundSplit();
        split.setSplitNo(BizNoGenerator.fundSplitNo());
        split.setArbitrationId(arbitrationId);
        split.setPaymentId(paymentId);
        split.setOrderId(orderId);
        split.setTotalAmount(payment.getAmount());
        split.setBuyerRefundAmount(buyerRefundAmount);
        split.setSellerSettleAmount(sellerSettleAmount);
        split.setPlatformFee(platformFee);
        split.setStatus(FundSplitStatus.PENDING.name());
        fundSplitMapper.insert(split);

        Order order = orderMapper.findById(orderId);

        // Buyer side: create refund record
        if (buyerRefundAmount.compareTo(BigDecimal.ZERO) > 0) {
            Refund refund = new Refund();
            refund.setRefundNo(BizNoGenerator.refundNo());
            refund.setOrderId(orderId);
            refund.setOrderNo(order.getOrderNo());
            refund.setBuyerId(order.getBuyerId());
            refund.setSellerId(order.getSellerId());
            refund.setRefundAmount(buyerRefundAmount);
            refund.setReason("Arbitration fund split refund");
            refund.setStatus(RefundStatus.SUCCESS.name());
            refundMapper.insert(refund);
            fundSplitMapper.updateBuyerRefundNo(split.getId(), refund.getRefundNo());
        }

        // Seller side: create settlement record and execute
        if (sellerSettleAmount.compareTo(BigDecimal.ZERO) > 0) {
            Settlement settlement = new Settlement();
            settlement.setSettlementNo(BizNoGenerator.settlementNo());
            settlement.setOrderId(orderId);
            settlement.setOrderNo(order.getOrderNo());
            settlement.setSellerId(order.getSellerId());
            settlement.setOrderAmount(sellerSettleAmount);
            settlement.setPlatformFee(platformFee);
            settlement.setSettleAmount(sellerNet);
            settlement.setEscrowAmount(sellerSettleAmount);
            settlement.setStatus(SettlementStatus.SETTLED.name());
            settlement.setSettledAt(LocalDateTime.now());
            settlementMapper.insert(settlement);
            fundSplitMapper.updateSellerSettlementNo(split.getId(), settlement.getSettlementNo());
        }

        // Unfreeze payment: if full refund to buyer -> CLOSED, else -> SUCCESS
        if (PaymentStatus.FROZEN.name().equals(payment.getStatus())) {
            String targetStatus = buyerRefundAmount.compareTo(payment.getAmount()) >= 0
                    ? PaymentStatus.CLOSED.name() : PaymentStatus.SUCCESS.name();
            paymentMapper.unfreeze(payment.getId(), targetStatus);
        }

        // Mark FundSplit as EXECUTED
        fundSplitMapper.updateStatus(split.getId(), FundSplitStatus.PENDING.name(), FundSplitStatus.EXECUTED.name());

        // Post-execution DB-level conservation audit
        BigDecimal totalDistributed = fundSplitMapper.sumActiveSplitAmountsByOrderId(orderId);
        if (totalDistributed.compareTo(payment.getAmount()) > 0) {
            log.error("FUND CONSERVATION VIOLATION: orderId={}, distributed={}, payment={}",
                    orderId, totalDistributed, payment.getAmount());
            auditService.log(operatorId, null, "FUND_SPLIT", "CONSERVATION_VIOLATION",
                    "ORDER", orderId,
                    "distributed=" + totalDistributed + ",payment=" + payment.getAmount());
        }

        auditService.log(operatorId, null, "FUND_SPLIT", "EXECUTE", "ORDER", orderId,
                "splitNo=" + split.getSplitNo() + ",buyerRefund=" + buyerRefundAmount
                        + ",sellerSettle=" + sellerSettleAmount + ",platformFee=" + platformFee);

        return fundSplitMapper.findById(split.getId());
    }

    @Override
    public void cancelActiveSplit(Long arbitrationId, Long operatorId) {
        DistributedLock.LockHandle handle = distributedLock.tryLock("fundsplit:" + arbitrationId);
        if (handle == null) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            transactionTemplate.executeWithoutResult(status ->
                    cancelActiveSplitInternal(arbitrationId, operatorId));
        } finally {
            distributedLock.unlock(handle);
        }
    }

    @Override
    public void cancelActiveSplitInternal(Long arbitrationId, Long operatorId) {
        FundSplit active = fundSplitMapper.findActiveByArbitrationId(arbitrationId);
        if (active == null) {
            log.info("No active fund split to cancel for arbitrationId={}", arbitrationId);
            return;
        }
        if (fundSplitMapper.updateStatus(active.getId(), active.getStatus(), FundSplitStatus.CANCELLED.name()) == 0)
            throw new BizException(ErrorCode.FUND_SPLIT_EXECUTE_FAILED, "Failed to cancel fund split");
        auditService.log(operatorId, null, "FUND_SPLIT", "CANCEL", "FUND_SPLIT", active.getId(),
                "arbitrationId=" + arbitrationId);
    }
}
