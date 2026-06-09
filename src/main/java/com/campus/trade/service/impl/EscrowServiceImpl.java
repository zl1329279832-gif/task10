package com.campus.trade.service.impl;

import com.campus.trade.common.annotation.Idempotent;
import com.campus.trade.common.config.TradeConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.common.util.BizNoGenerator;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.entity.Payment;
import com.campus.trade.domain.entity.Settlement;
import com.campus.trade.domain.enums.PaymentStateTransition;
import com.campus.trade.domain.enums.PaymentStatus;
import com.campus.trade.domain.enums.SettlementStateTransition;
import com.campus.trade.domain.enums.SettlementStatus;
import com.campus.trade.dto.response.SettlementResponse;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.PaymentMapper;
import com.campus.trade.mapper.SettlementMapper;
import com.campus.trade.service.AuditService;
import com.campus.trade.service.EscrowService;
import com.campus.trade.service.OrderService;
import com.campus.trade.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowServiceImpl implements EscrowService {

    private final PaymentMapper paymentMapper;
    private final SettlementMapper settlementMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final SettlementService settlementService;
    private final AuditService auditService;
    private final DistributedLock distributedLock;
    private final TradeConfig tradeConfig;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void freezeFunds(Long orderId, Long operatorId, String reason) {
        DistributedLock.LockHandle handle = distributedLock.tryLock("order:" + orderId);
        if (handle == null) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            transactionTemplate.executeWithoutResult(status -> freezeFundsInternal(orderId, operatorId, reason));
        } finally {
            distributedLock.unlock(handle);
        }
    }

    @Override
    public void freezeFundsInternal(Long orderId, Long operatorId, String reason) {
        // Freeze payment if SUCCESS
        Payment payment = paymentMapper.findByOrderId(orderId);
        if (payment != null && PaymentStatus.SUCCESS.name().equals(payment.getStatus())) {
            PaymentStatus from = PaymentStatus.SUCCESS;
            PaymentStatus to = PaymentStatus.FROZEN;
            if (!PaymentStateTransition.isValid(from, to))
                throw new BizException(ErrorCode.PAYMENT_FREEZE_FAILED);
            if (paymentMapper.freeze(payment.getId(), from.name(), payment.getAmount(), reason) == 0)
                throw new BizException(ErrorCode.PAYMENT_FREEZE_FAILED);
            auditService.log(operatorId, null, "ESCROW", "FREEZE_PAYMENT", "PAYMENT", payment.getId(),
                    "reason=" + reason + ",amount=" + payment.getAmount());
        }

        // Freeze settlement if PENDING
        Settlement settlement = settlementMapper.findByOrderId(orderId);
        if (settlement != null && SettlementStatus.PENDING.name().equals(settlement.getStatus())) {
            SettlementStatus from = SettlementStatus.PENDING;
            SettlementStatus to = SettlementStatus.FROZEN;
            if (!SettlementStateTransition.isValid(from, to))
                throw new BizException(ErrorCode.SETTLEMENT_FREEZE_FAILED);
            if (settlementMapper.freeze(settlement.getId(), from.name(), settlement.getSettleAmount(), reason) == 0)
                throw new BizException(ErrorCode.SETTLEMENT_FREEZE_FAILED);
            auditService.log(operatorId, null, "ESCROW", "FREEZE_SETTLEMENT", "SETTLEMENT", settlement.getId(),
                    "reason=" + reason + ",amount=" + settlement.getSettleAmount());
        }
    }

    @Override
    public void unfreezeFunds(Long orderId, Long operatorId, String reason) {
        DistributedLock.LockHandle handle = distributedLock.tryLock("order:" + orderId);
        if (handle == null) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            transactionTemplate.executeWithoutResult(status -> unfreezeFundsInternal(orderId, operatorId, reason));
        } finally {
            distributedLock.unlock(handle);
        }
    }

    @Override
    public void unfreezeFundsInternal(Long orderId, Long operatorId, String reason) {
        // Unfreeze payment if FROZEN
        Payment payment = paymentMapper.findByOrderId(orderId);
        if (payment != null && PaymentStatus.FROZEN.name().equals(payment.getStatus())) {
            if (paymentMapper.unfreeze(payment.getId(), PaymentStatus.SUCCESS.name()) == 0)
                throw new BizException(ErrorCode.PAYMENT_UNFREEZE_FAILED);
            auditService.log(operatorId, null, "ESCROW", "UNFREEZE_PAYMENT", "PAYMENT", payment.getId(),
                    "reason=" + reason);
        }

        // Unfreeze settlement if FROZEN
        Settlement settlement = settlementMapper.findByOrderId(orderId);
        if (settlement != null && SettlementStatus.FROZEN.name().equals(settlement.getStatus())) {
            if (settlementMapper.unfreeze(settlement.getId(), SettlementStatus.PENDING.name()) == 0)
                throw new BizException(ErrorCode.SETTLEMENT_NOT_FROZEN);
            auditService.log(operatorId, null, "ESCROW", "UNFREEZE_SETTLEMENT", "SETTLEMENT", settlement.getId(),
                    "reason=" + reason);
        }
    }

    @Override
    public void autoSettleOnReceipt(Long orderId, Long buyerId) {
        DistributedLock.LockHandle handle = distributedLock.tryLock("order:" + orderId);
        if (handle == null) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            transactionTemplate.executeWithoutResult(status -> autoSettleOnReceiptInternal(orderId, buyerId));
        } finally {
            distributedLock.unlock(handle);
        }
    }

    @Override
    public void autoSettleOnReceiptInternal(Long orderId, Long buyerId) {
        Payment payment = paymentMapper.findByOrderId(orderId);
        if (payment == null) {
            log.warn("No payment found for auto-settle, orderId={}", orderId);
            return;
        }
        // If frozen (dispute active), skip auto-settle
        if (PaymentStatus.FROZEN.name().equals(payment.getStatus())) {
            log.info("Payment frozen, skipping auto-settle for orderId={}", orderId);
            return;
        }
        if (!PaymentStatus.SUCCESS.name().equals(payment.getStatus())) {
            log.warn("Payment not SUCCESS for auto-settle, orderId={}, status={}", orderId, payment.getStatus());
            return;
        }

        // Check if settlement already exists
        Settlement existing = settlementMapper.findByOrderId(orderId);
        if (existing != null) {
            log.info("Settlement already exists for orderId={}, status={}", orderId, existing.getStatus());
            return;
        }

        // Create and execute settlement using lock-free internals (caller holds order lock)
        try {
            SettlementResponse resp = settlementService.createSettlementInternal(orderId);
            settlementService.executeSettlementInternal(resp.getId());
            auditService.log(buyerId, null, "ESCROW", "AUTO_SETTLE", "ORDER", orderId,
                    "settlementId=" + resp.getId());
            log.info("Auto-settle completed for orderId={}", orderId);
        } catch (Exception e) {
            log.error("Auto-settle failed for orderId={}: {}", orderId, e.getMessage());
            auditService.log(buyerId, null, "ESCROW", "AUTO_SETTLE_FAILED", "ORDER", orderId,
                    "error=" + e.getMessage());
            // Non-fatal: settlement can be retried manually
        }
    }

    @Override
    @Idempotent(key = "#settlementId.toString()", bizType = "SETTLEMENT_RETRY")
    public SettlementResponse retrySettlement(Long settlementId, Long operatorId) {
        DistributedLock.LockHandle handle = distributedLock.tryLock("settlement:" + settlementId);
        if (handle == null) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            return transactionTemplate.execute(status -> {
                Settlement s = settlementMapper.findById(settlementId);
                if (s == null) throw new BizException(404, "Settlement not found");
                if (!SettlementStatus.FAILED.name().equals(s.getStatus()))
                    throw new BizException(ErrorCode.SETTLEMENT_NOT_FAILED);
                if (settlementMapper.retryFailed(settlementId) == 0)
                    throw new BizException(ErrorCode.SETTLEMENT_RETRY_FAILED);
                auditService.log(operatorId, null, "ESCROW", "RETRY_SETTLEMENT", "SETTLEMENT", settlementId,
                        "retryCount=" + (s.getRetryCount() + 1));
                // Re-execute using lock-free internal
                settlementService.executeSettlementInternal(settlementId);
                return settlementService.getSettlement(settlementId);
            });
        } finally {
            distributedLock.unlock(handle);
        }
    }
}
