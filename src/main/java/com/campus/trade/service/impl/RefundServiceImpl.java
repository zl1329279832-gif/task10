package com.campus.trade.service.impl;

import com.campus.trade.common.BusinessException;
import com.campus.trade.common.ResultCode;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.ProductMapper;
import com.campus.trade.mapper.RefundRecordMapper;
import com.campus.trade.model.dto.request.RefundRequest;
import com.campus.trade.model.entity.Order;
import com.campus.trade.model.entity.RefundRecord;
import com.campus.trade.model.enums.OrderStatus;
import com.campus.trade.model.enums.RefundStatus;
import com.campus.trade.service.AuditLogService;
import com.campus.trade.service.InventoryService;
import com.campus.trade.service.RefundService;
import com.campus.trade.statemachine.OrderEvent;
import com.campus.trade.statemachine.OrderStateMachine;
import com.campus.trade.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRecordMapper refundRecordMapper;
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final InventoryService inventoryService;
    private final AuditLogService auditLogService;

    private static final Set<String> REFUNDABLE_STATUSES = Set.of(
            OrderStatus.PAID.name(), OrderStatus.SHIPPED.name(), OrderStatus.RECEIVED.name()
    );

    @Override
    @Transactional
    public String applyRefund(Long buyerId, RefundRequest request) {
        Order order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_PARTICIPANT);
        }
        if (!REFUNDABLE_STATUSES.contains(order.getStatus())) {
            throw new BusinessException(ResultCode.REFUND_NOT_ALLOWED);
        }
        if (request.getAmount().compareTo(order.getTotalAmount()) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "退款金额不能超过订单金额");
        }

        // Transition order status
        OrderStatus newStatus = OrderStateMachine.transition(
                OrderStatus.valueOf(order.getStatus()), OrderEvent.REQUEST_REFUND);
        int affected = orderMapper.updateStatusWithVersion(order.getOrderNo(), newStatus.name(), order.getVersion());
        if (affected == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_CONFLICT);
        }

        // Create refund record
        RefundRecord record = new RefundRecord();
        record.setRefundNo(OrderNoGenerator.generateRefundNo());
        record.setOrderNo(request.getOrderNo());
        record.setBuyerId(buyerId);
        record.setSellerId(order.getSellerId());
        record.setAmount(request.getAmount());
        record.setReason(request.getReason());
        record.setEvidenceUrls(request.getEvidenceUrls());
        record.setStatus(RefundStatus.PENDING.name());
        refundRecordMapper.insert(record);

        auditLogService.log(buyerId, null, "REFUND", "APPLY",
                "ORDER", order.getOrderNo(), "退款申请: " + request.getReason(), null);

        log.info("退款申请: refundNo={}, orderNo={}, amount={}", record.getRefundNo(), request.getOrderNo(), request.getAmount());
        return record.getRefundNo();
    }

    @Override
    public RefundRecord getRefund(String refundNo, Long userId) {
        RefundRecord record = refundRecordMapper.selectByRefundNo(refundNo);
        if (record == null) {
            throw new BusinessException(ResultCode.REFUND_NOT_FOUND);
        }
        if (userId != null && !record.getBuyerId().equals(userId) && !record.getSellerId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_PARTICIPANT);
        }
        return record;
    }

    @Override
    public List<RefundRecord> getRefundsByOrderNo(String orderNo, Long userId) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (userId != null && !order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_PARTICIPANT);
        }
        return refundRecordMapper.selectByOrderNo(orderNo);
    }

    @Override
    @Transactional
    public void approveRefund(String refundNo, Long sellerId, String remark) {
        RefundRecord record = refundRecordMapper.selectByRefundNo(refundNo);
        if (record == null) {
            throw new BusinessException(ResultCode.REFUND_NOT_FOUND);
        }
        if (!record.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_PARTICIPANT);
        }
        if (!RefundStatus.PENDING.name().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.REFUND_ALREADY_HANDLED);
        }

        // Update refund status
        refundRecordMapper.updateHandle(refundNo, RefundStatus.APPROVED.name(), sellerId, remark);

        // Transition order status
        Order order = orderMapper.selectByOrderNo(record.getOrderNo());
        OrderStatus newStatus = OrderStateMachine.transition(
                OrderStatus.valueOf(order.getStatus()), OrderEvent.APPROVE_REFUND);
        orderMapper.updateStatusWithVersion(order.getOrderNo(), newStatus.name(), order.getVersion());

        // Restore stock
        inventoryService.releaseStock(order.getProductId(), order.getQuantity());
        productMapper.restoreStock(order.getProductId(), order.getQuantity());

        auditLogService.log(sellerId, null, "REFUND", "APPROVE",
                "REFUND", refundNo, "退款批准: " + remark, null);

        log.info("退款批准: refundNo={}, orderNo={}", refundNo, record.getOrderNo());
    }

    @Override
    @Transactional
    public void rejectRefund(String refundNo, Long sellerId, String remark) {
        RefundRecord record = refundRecordMapper.selectByRefundNo(refundNo);
        if (record == null) {
            throw new BusinessException(ResultCode.REFUND_NOT_FOUND);
        }
        if (!record.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_PARTICIPANT);
        }
        if (!RefundStatus.PENDING.name().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.REFUND_ALREADY_HANDLED);
        }

        // Update refund status
        refundRecordMapper.updateHandle(refundNo, RefundStatus.REJECTED.name(), sellerId, remark);

        // Transition order status
        Order order = orderMapper.selectByOrderNo(record.getOrderNo());
        OrderStatus newStatus = OrderStateMachine.transition(
                OrderStatus.valueOf(order.getStatus()), OrderEvent.REJECT_REFUND);
        orderMapper.updateStatusWithVersion(order.getOrderNo(), newStatus.name(), order.getVersion());

        auditLogService.log(sellerId, null, "REFUND", "REJECT",
                "REFUND", refundNo, "退款拒绝: " + remark, null);

        log.info("退款拒绝: refundNo={}, orderNo={}", refundNo, record.getOrderNo());
    }
}
