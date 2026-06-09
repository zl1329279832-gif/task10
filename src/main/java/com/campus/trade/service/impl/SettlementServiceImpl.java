package com.campus.trade.service.impl;

import com.campus.trade.common.config.TradeConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.entity.Payment;
import com.campus.trade.domain.entity.Settlement;
import com.campus.trade.domain.enums.OrderStatus;
import com.campus.trade.domain.enums.PaymentStatus;
import com.campus.trade.domain.enums.SettlementStatus;
import com.campus.trade.dto.response.SettlementResponse;
import com.campus.trade.common.result.PageResult;
import com.campus.trade.common.util.BizNoGenerator;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.PaymentMapper;
import com.campus.trade.mapper.SettlementMapper;
import com.campus.trade.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementMapper settlementMapper;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final OrderService orderService;
    private final AuditService auditService;
    private final TradeConfig tradeConfig;
    private final DistributedLock distributedLock;

    @Override
    @Transactional
    public SettlementResponse createSettlement(Long orderId) {
        Order o = orderMapper.findById(orderId);
        if (o == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        if (!OrderStatus.RECEIVED.name().equals(o.getStatus()))
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "Must be RECEIVED");
        if (settlementMapper.findByOrderId(orderId) != null)
            throw new BizException(ErrorCode.SETTLEMENT_ALREADY_EXISTS);

        BigDecimal fee = o.getTotalAmount()
                .multiply(tradeConfig.getPlatformFeeRate())
                .setScale(2, RoundingMode.HALF_UP);

        Settlement s = new Settlement();
        s.setSettlementNo(BizNoGenerator.settlementNo());
        s.setOrderId(orderId);
        s.setOrderNo(o.getOrderNo());
        s.setSellerId(o.getSellerId());
        s.setOrderAmount(o.getTotalAmount());
        s.setPlatformFee(fee);
        s.setSettleAmount(o.getTotalAmount().subtract(fee));
        s.setStatus(SettlementStatus.PENDING.name());
        settlementMapper.insert(s);

        auditService.log(null, null, "SETTLEMENT", "CREATE", "ORDER", orderId,
                "amount=" + s.getSettleAmount());
        return toResp(s);
    }

    @Override
    @Transactional
    public void executeSettlement(Long id) {
        Settlement s = settlementMapper.findById(id);
        if (s == null) throw new BizException(ErrorCode.SETTLEMENT_NOT_FOUND);

        String lk = "settlement:" + id;
        if (!distributedLock.tryLock(lk)) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            if (settlementMapper.updateStatus(id, "PENDING", "SETTLED") == 0)
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
            settlementMapper.updateSettledAt(id, LocalDateTime.now());

            // Release escrow: Payment ESCROW → SUCCESS
            Payment p = paymentMapper.findByOrderId(s.getOrderId());
            if (p != null && PaymentStatus.ESCROW.name().equals(p.getStatus())) {
                paymentMapper.updateStatus(p.getId(), "ESCROW", "SUCCESS");
            }

            orderService.transitionOrder(s.getOrderId(), OrderStatus.SETTLED.name(), null, "Settlement completed");
            auditService.log(null, null, "SETTLEMENT", "EXECUTE", "SETTLEMENT", id,
                    "settleAmount=" + s.getSettleAmount());
        } catch (BizException e) {
            // Mark as FAILED for retry if settlement execution fails
            handleSettlementFailure(s, e.getMessage());
            throw e;
        } finally {
            distributedLock.unlock(lk);
        }
    }

    @Override
    @Transactional
    public void freezeSettlement(Long orderId, String reason) {
        Payment p = paymentMapper.findByOrderId(orderId);
        if (p != null && PaymentStatus.ESCROW.name().equals(p.getStatus())) {
            if (paymentMapper.updateStatus(p.getId(), "ESCROW", "FROZEN") > 0) {
                paymentMapper.updateFreezeInfo(p.getId(), LocalDateTime.now(), reason);
                auditService.log(null, null, "PAYMENT", "FREEZE", "PAYMENT", p.getId(), "reason=" + reason);
            }
        }

        Settlement s = settlementMapper.findByOrderId(orderId);
        if (s != null && SettlementStatus.PENDING.name().equals(s.getStatus())) {
            if (settlementMapper.updateStatus(s.getId(), "PENDING", "FROZEN") > 0) {
                settlementMapper.updateFreezeInfo(s.getId(), LocalDateTime.now(), reason);
                auditService.log(null, null, "SETTLEMENT", "FREEZE", "SETTLEMENT", s.getId(), "reason=" + reason);
            }
        }
    }

    @Override
    @Transactional
    public void unfreezeSettlement(Long orderId) {
        LocalDateTime now = LocalDateTime.now();

        Payment p = paymentMapper.findByOrderId(orderId);
        if (p != null && PaymentStatus.FROZEN.name().equals(p.getStatus())) {
            if (paymentMapper.updateStatus(p.getId(), "FROZEN", "ESCROW") > 0) {
                paymentMapper.updateUnfreezeInfo(p.getId(), now);
                auditService.log(null, null, "PAYMENT", "UNFREEZE", "PAYMENT", p.getId(), "restored to ESCROW");
            }
        }

        Settlement s = settlementMapper.findByOrderId(orderId);
        if (s != null && SettlementStatus.FROZEN.name().equals(s.getStatus())) {
            if (settlementMapper.updateStatus(s.getId(), "FROZEN", "PENDING") > 0) {
                settlementMapper.updateUnfreezeInfo(s.getId(), now);
                auditService.log(null, null, "SETTLEMENT", "UNFREEZE", "SETTLEMENT", s.getId(), "restored to PENDING");
            }
        }
    }

    @Override
    @Transactional
    public void splitSettlement(Long orderId, BigDecimal buyerRefundAmount) {
        Order o = orderMapper.findById(orderId);
        if (o == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);

        if (buyerRefundAmount == null || buyerRefundAmount.compareTo(BigDecimal.ZERO) <= 0
                || buyerRefundAmount.compareTo(o.getTotalAmount()) > 0)
            throw new BizException(ErrorCode.REFUND_AMOUNT_INVALID);

        Settlement s = settlementMapper.findByOrderId(orderId);
        if (s == null) {
            // Create settlement if not exists (dispute before confirm-receive)
            BigDecimal fee = o.getTotalAmount()
                    .multiply(tradeConfig.getPlatformFeeRate())
                    .setScale(2, RoundingMode.HALF_UP);
            s = new Settlement();
            s.setSettlementNo(BizNoGenerator.settlementNo());
            s.setOrderId(orderId);
            s.setOrderNo(o.getOrderNo());
            s.setSellerId(o.getSellerId());
            s.setOrderAmount(o.getTotalAmount());
            s.setPlatformFee(fee);
            s.setSettleAmount(o.getTotalAmount().subtract(fee));
            s.setStatus(SettlementStatus.FROZEN.name());
            settlementMapper.insert(s);
        }

        // Recalculate: seller gets (orderAmount - buyerRefundAmount - platformFee)
        BigDecimal sellerAmount = o.getTotalAmount().subtract(buyerRefundAmount);
        BigDecimal fee = sellerAmount.multiply(tradeConfig.getPlatformFeeRate())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal settleAmount = sellerAmount.subtract(fee);

        settlementMapper.updateSettleAmounts(s.getId(), settleAmount, buyerRefundAmount, fee);

        // Unfreeze and execute partial settlement if seller amount > 0
        if (settleAmount.compareTo(BigDecimal.ZERO) > 0) {
            settlementMapper.updateStatus(s.getId(), s.getStatus(), "PENDING");
        }

        // Release payment from FROZEN → SUCCESS (partial settlement done externally)
        Payment p = paymentMapper.findByOrderId(orderId);
        if (p != null && PaymentStatus.FROZEN.name().equals(p.getStatus())) {
            paymentMapper.updateStatus(p.getId(), "FROZEN", "SUCCESS");
            paymentMapper.updateUnfreezeInfo(p.getId(), LocalDateTime.now());
        }

        auditService.log(null, null, "SETTLEMENT", "SPLIT", "ORDER", orderId,
                "buyerRefund=" + buyerRefundAmount + ",sellerSettle=" + settleAmount + ",fee=" + fee);
    }

    @Override
    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void retryFailedSettlements() {
        List<Settlement> failed = settlementMapper.findFailedForRetry(LocalDateTime.now(), 20);
        for (Settlement s : failed) {
            String lk = "settlement:retry:" + s.getId();
            if (!distributedLock.tryLock(lk, 5000)) continue;
            try {
                // Reset to PENDING for re-execution
                if (settlementMapper.updateStatus(s.getId(), "FAILED", "PENDING") > 0) {
                    log.info("Retrying settlement: {}, attempt={}", s.getSettlementNo(), s.getRetryCount() + 1);
                    try {
                        executeSettlement(s.getId());
                        auditService.log(null, null, "SETTLEMENT", "RETRY_SUCCESS", "SETTLEMENT", s.getId(),
                                "attempt=" + (s.getRetryCount() + 1));
                    } catch (Exception e) {
                        log.error("Settlement retry failed: {}, error={}", s.getSettlementNo(), e.getMessage());
                    }
                }
            } finally {
                distributedLock.unlock(lk);
            }
        }
    }

    @Override
    public PageResult<SettlementResponse> listSettlements(Long sellerId, String status, int page, int size) {
        int off = (page - 1) * size;
        List<Settlement> list = settlementMapper.findBySellerId(sellerId, status, off, size);
        return PageResult.of(list.stream().map(this::toResp).toList(),
                settlementMapper.countBySellerId(sellerId, status), page, size);
    }

    @Override
    public SettlementResponse getSettlement(Long id) {
        Settlement s = settlementMapper.findById(id);
        if (s == null) throw new BizException(ErrorCode.SETTLEMENT_NOT_FOUND);
        return toResp(s);
    }

    private void handleSettlementFailure(Settlement s, String reason) {
        int nextRetry = (s.getRetryCount() != null ? s.getRetryCount() : 0) + 1;
        if (nextRetry > (s.getMaxRetry() != null ? s.getMaxRetry() : 5)) {
            log.error("Settlement retry exhausted: {}", s.getSettlementNo());
            auditService.log(null, null, "SETTLEMENT", "RETRY_EXHAUSTED", "SETTLEMENT", s.getId(),
                    "maxRetry=" + s.getMaxRetry());
            return;
        }
        // Exponential backoff: 2^retryCount minutes
        long delayMinutes = (long) Math.pow(2, nextRetry);
        LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
        settlementMapper.updateStatus(s.getId(), "PENDING", "FAILED");
        settlementMapper.updateRetryInfo(s.getId(), nextRetry, nextRetryAt, reason);
        auditService.log(null, null, "SETTLEMENT", "FAILED", "SETTLEMENT", s.getId(),
                "reason=" + reason + ",nextRetry=" + nextRetryAt);
    }

    private SettlementResponse toResp(Settlement s) {
        SettlementResponse r = new SettlementResponse();
        r.setId(s.getId());
        r.setSettlementNo(s.getSettlementNo());
        r.setOrderId(s.getOrderId());
        r.setOrderNo(s.getOrderNo());
        r.setSellerId(s.getSellerId());
        r.setOrderAmount(s.getOrderAmount());
        r.setPlatformFee(s.getPlatformFee());
        r.setSettleAmount(s.getSettleAmount());
        r.setStatus(s.getStatus());
        r.setStatusDesc(SettlementStatus.valueOf(s.getStatus()).getDesc());
        r.setSettledAt(s.getSettledAt());
        r.setCreatedAt(s.getCreatedAt());
        return r;
    }
}
