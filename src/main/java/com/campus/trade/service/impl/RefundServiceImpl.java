package com.campus.trade.service.impl;

import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.entity.Refund;
import com.campus.trade.domain.enums.OrderStatus;
import com.campus.trade.domain.enums.RefundStatus;
import com.campus.trade.dto.request.RefundRequest;
import com.campus.trade.dto.response.RefundResponse;
import com.campus.trade.common.result.PageResult;
import com.campus.trade.common.util.BizNoGenerator;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.RefundMapper;
import com.campus.trade.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final com.campus.trade.mapper.PaymentMapper paymentMapper;
    private final OrderService orderService;
    private final SettlementService settlementService;
    private final AuditService auditService;
    private final DistributedLock distributedLock;

    @Override
    @Transactional
    public RefundResponse applyRefund(Long buyerId, RefundRequest req) {
        Order o = orderMapper.findById(req.getOrderId());
        if (o == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        if (!o.getBuyerId().equals(buyerId)) throw new BizException(ErrorCode.ORDER_NOT_BUYER);

        String s = o.getStatus();
        if (!OrderStatus.PAID.name().equals(s)
                && !OrderStatus.SHIPPED.name().equals(s)
                && !OrderStatus.RECEIVED.name().equals(s))
            throw new BizException(ErrorCode.REFUND_NOT_ALLOWED, s);

        Refund ex = refundMapper.findByOrderId(o.getId());
        if (ex != null && !RefundStatus.REJECTED.name().equals(ex.getStatus()))
            throw new BizException(ErrorCode.REFUND_ALREADY_EXISTS);

        if (req.getRefundAmount().compareTo(o.getTotalAmount()) > 0)
            throw new BizException(ErrorCode.REFUND_AMOUNT_EXCEED);

        Refund r = new Refund();
        r.setRefundNo(BizNoGenerator.refundNo());
        r.setOrderId(o.getId());
        r.setOrderNo(o.getOrderNo());
        r.setBuyerId(buyerId);
        r.setSellerId(o.getSellerId());
        r.setRefundAmount(req.getRefundAmount());
        r.setReason(req.getReason());
        r.setStatus(RefundStatus.PENDING.name());
        r.setEvidenceUrls(req.getEvidenceUrls());
        refundMapper.insert(r);

        orderService.transitionOrder(o.getId(), OrderStatus.REFUNDING.name(), buyerId, "Refund applied");

        // Freeze escrowed funds
        settlementService.freezeSettlement(o.getId(), "Refund applied: " + r.getRefundNo());

        auditService.log(buyerId, null, "REFUND", "APPLY", "ORDER", o.getId(), "refundNo=" + r.getRefundNo());
        return toResp(r);
    }

    @Override
    @Transactional
    public RefundResponse approveRefund(Long sellerId, Long refundId) {
        Refund r = refundMapper.findById(refundId);
        if (r == null) throw new BizException(ErrorCode.REFUND_NOT_FOUND);
        if (!r.getSellerId().equals(sellerId)) throw new BizException(ErrorCode.ORDER_NOT_SELLER);

        String lk = "refund:" + refundId;
        if (!distributedLock.tryLock(lk)) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            if (refundMapper.updateStatus(refundId, "PENDING", "APPROVED") == 0)
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
            refundMapper.updateApproval(refundId, sellerId, LocalDateTime.now(), null);
            orderService.transitionOrder(r.getOrderId(), OrderStatus.REFUNDED.name(), sellerId, "Refund approved");

            // Close payment (funds released from escrow to buyer)
            com.campus.trade.domain.entity.Payment p = paymentMapper.findByOrderId(r.getOrderId());
            if (p != null && com.campus.trade.domain.enums.PaymentStatus.FROZEN.name().equals(p.getStatus())) {
                paymentMapper.updateStatus(p.getId(), "FROZEN", "CLOSED");
                paymentMapper.updateUnfreezeInfo(p.getId(), LocalDateTime.now());
            }
        } finally {
            distributedLock.unlock(lk);
        }
        return toResp(refundMapper.findById(refundId));
    }

    @Override
    @Transactional
    public RefundResponse rejectRefund(Long sellerId, Long refundId, String reason) {
        Refund r = refundMapper.findById(refundId);
        if (r == null) throw new BizException(ErrorCode.REFUND_NOT_FOUND);
        if (!r.getSellerId().equals(sellerId)) throw new BizException(ErrorCode.ORDER_NOT_SELLER);

        String lk = "refund:" + refundId;
        if (!distributedLock.tryLock(lk)) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            if (refundMapper.updateStatus(refundId, "PENDING", "REJECTED") == 0)
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
            refundMapper.updateApproval(refundId, sellerId, LocalDateTime.now(), reason);
            orderService.transitionOrder(r.getOrderId(), OrderStatus.PAID.name(), sellerId, "Refund rejected: " + reason);

            // Unfreeze escrowed funds
            settlementService.unfreezeSettlement(r.getOrderId());
        } finally {
            distributedLock.unlock(lk);
        }
        return toResp(refundMapper.findById(refundId));
    }

    @Override
    public PageResult<RefundResponse> listRefunds(Long sellerId, String status, int page, int size) {
        int off = (page - 1) * size;
        List<Refund> list = refundMapper.findBySellerId(sellerId, status, off, size);
        return PageResult.of(list.stream().map(this::toResp).toList(), list.size(), page, size);
    }

    @Override
    public RefundResponse getRefund(Long id) {
        Refund r = refundMapper.findById(id);
        if (r == null) throw new BizException(ErrorCode.REFUND_NOT_FOUND);
        return toResp(r);
    }

    private RefundResponse toResp(Refund r) {
        RefundResponse x = new RefundResponse();
        x.setId(r.getId());
        x.setRefundNo(r.getRefundNo());
        x.setOrderId(r.getOrderId());
        x.setOrderNo(r.getOrderNo());
        x.setRefundAmount(r.getRefundAmount());
        x.setReason(r.getReason());
        x.setStatus(r.getStatus());
        x.setStatusDesc(RefundStatus.valueOf(r.getStatus()).getDesc());
        x.setEvidenceUrls(r.getEvidenceUrls());
        x.setRejectReason(r.getRejectReason());
        x.setCreatedAt(r.getCreatedAt());
        x.setApprovedAt(r.getApprovedAt());
        return x;
    }
}
