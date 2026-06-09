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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;

    @Override
    public SettlementResponse createSettlement(Long orderId) {
        DistributedLock.LockHandle handle = distributedLock.tryLock("settlement:create:" + orderId);
        if (handle == null) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            return transactionTemplate.execute(status -> createSettlementInternal(orderId));
        } finally {
            distributedLock.unlock(handle);
        }
    }

    @Override
    public SettlementResponse createSettlementInternal(Long orderId) {
        Order o = orderMapper.findById(orderId);
        if (o == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        if (!OrderStatus.RECEIVED.name().equals(o.getStatus()))
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "Must be RECEIVED");
        if (settlementMapper.findByOrderId(orderId) != null)
            throw new BizException(ErrorCode.SETTLEMENT_ALREADY_EXISTS);

        // Guard: check payment not frozen
        Payment payment = paymentMapper.findByOrderId(orderId);
        if (payment != null && PaymentStatus.FROZEN.name().equals(payment.getStatus()))
            throw new BizException(ErrorCode.PAYMENT_FROZEN);

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
        s.setEscrowAmount(payment != null ? payment.getAmount() : o.getTotalAmount());
        s.setStatus(SettlementStatus.PENDING.name());
        settlementMapper.insert(s);

        auditService.log(null, null, "SETTLEMENT", "CREATE", "ORDER", orderId,
                "amount=" + s.getSettleAmount());
        return toResp(s);
    }

    @Override
    public void executeSettlement(Long id) {
        DistributedLock.LockHandle handle = distributedLock.tryLock("settlement:" + id);
        if (handle == null) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            transactionTemplate.executeWithoutResult(status -> executeSettlementInternal(id));
        } finally {
            distributedLock.unlock(handle);
        }
    }

    @Override
    public void executeSettlementInternal(Long id) {
        Settlement s = settlementMapper.findById(id);
        if (s == null) throw new BizException(404, "Not found");

        // Guard: check settlement not frozen
        if (SettlementStatus.FROZEN.name().equals(s.getStatus()))
            throw new BizException(ErrorCode.SETTLEMENT_FROZEN);

        // Guard: check payment not frozen
        Payment payment = paymentMapper.findByOrderId(s.getOrderId());
        if (payment != null && PaymentStatus.FROZEN.name().equals(payment.getStatus()))
            throw new BizException(ErrorCode.PAYMENT_FROZEN);

        if (settlementMapper.updateStatus(id, "PENDING", "SETTLED") == 0)
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
        settlementMapper.updateSettledAt(id, LocalDateTime.now());
        // Use lock-free internal to avoid re-entrant lock on order
        orderService.transitionOrderInternal(s.getOrderId(), OrderStatus.SETTLED.name(), null, "Settlement completed");
    }

    @Override
    public SettlementResponse retrySettlement(Long id) {
        DistributedLock.LockHandle handle = distributedLock.tryLock("settlement:" + id);
        if (handle == null) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            return transactionTemplate.execute(status -> {
                Settlement s = settlementMapper.findById(id);
                if (s == null) throw new BizException(404, "Not found");
                if (!SettlementStatus.FAILED.name().equals(s.getStatus()))
                    throw new BizException(ErrorCode.SETTLEMENT_NOT_FAILED);
                if (settlementMapper.retryFailed(id) == 0)
                    throw new BizException(ErrorCode.SETTLEMENT_RETRY_FAILED);
                executeSettlementInternal(id);
                return getSettlement(id);
            });
        } finally {
            distributedLock.unlock(handle);
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
        if (s == null) throw new BizException(404, "Not found");
        return toResp(s);
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
        r.setFrozenAmount(s.getFrozenAmount());
        r.setFreezeReason(s.getFreezeReason());
        r.setFrozenAt(s.getFrozenAt());
        r.setEscrowAmount(s.getEscrowAmount());
        r.setRetryCount(s.getRetryCount());
        r.setCreatedAt(s.getCreatedAt());
        return r;
    }
}
