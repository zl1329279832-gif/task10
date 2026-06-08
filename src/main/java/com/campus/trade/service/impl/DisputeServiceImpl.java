package com.campus.trade.service.impl;

import com.campus.trade.common.BusinessException;
import com.campus.trade.common.PageResult;
import com.campus.trade.common.ResultCode;
import com.campus.trade.mapper.DisputeMapper;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.ProductMapper;
import com.campus.trade.model.dto.request.ArbitrateRequest;
import com.campus.trade.model.dto.request.DisputeRequest;
import com.campus.trade.model.entity.Dispute;
import com.campus.trade.model.entity.Order;
import com.campus.trade.model.enums.DisputeStatus;
import com.campus.trade.model.enums.OrderStatus;
import com.campus.trade.service.*;
import com.campus.trade.statemachine.OrderEvent;
import com.campus.trade.statemachine.OrderStateMachine;
import com.campus.trade.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private final DisputeMapper disputeMapper;
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final InventoryService inventoryService;
    private final SettlementService settlementService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public String createDispute(Long initiatorId, DisputeRequest request) {
        Order order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!order.getBuyerId().equals(initiatorId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_PARTICIPANT);
        }

        // Must be in REFUND_REJECTED status to open dispute
        if (!OrderStatus.REFUND_REJECTED.name().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.DISPUTE_NOT_ALLOWED);
        }

        // Transition order status
        OrderStatus newStatus = OrderStateMachine.transition(
                OrderStatus.valueOf(order.getStatus()), OrderEvent.OPEN_DISPUTE);
        int affected = orderMapper.updateStatusWithVersion(order.getOrderNo(), newStatus.name(), order.getVersion());
        if (affected == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_CONFLICT);
        }

        Dispute dispute = new Dispute();
        dispute.setDisputeNo(OrderNoGenerator.generateDisputeNo());
        dispute.setOrderNo(request.getOrderNo());
        dispute.setRefundNo(request.getRefundNo());
        dispute.setInitiatorId(initiatorId);
        dispute.setReason(request.getReason());
        dispute.setBuyerEvidence(request.getEvidence());
        dispute.setStatus(DisputeStatus.OPEN.name());
        disputeMapper.insert(dispute);

        auditLogService.log(initiatorId, null, "DISPUTE", "CREATE",
                "ORDER", order.getOrderNo(), "发起争议: " + request.getReason(), null);

        log.info("争议创建: disputeNo={}, orderNo={}", dispute.getDisputeNo(), request.getOrderNo());
        return dispute.getDisputeNo();
    }

    @Override
    public Dispute getDispute(String disputeNo) {
        Dispute dispute = disputeMapper.selectByDisputeNo(disputeNo);
        if (dispute == null) {
            throw new BusinessException(ResultCode.DISPUTE_NOT_FOUND);
        }
        return dispute;
    }

    @Override
    public void submitEvidence(String disputeNo, Long userId, String evidence) {
        Dispute dispute = disputeMapper.selectByDisputeNo(disputeNo);
        if (dispute == null) {
            throw new BusinessException(ResultCode.DISPUTE_NOT_FOUND);
        }
        if (DisputeStatus.CLOSED.name().equals(dispute.getStatus())) {
            throw new BusinessException(ResultCode.DISPUTE_ALREADY_CLOSED);
        }

        Order order = orderMapper.selectByOrderNo(dispute.getOrderNo());
        if (order.getBuyerId().equals(userId)) {
            disputeMapper.updateEvidence(disputeNo, evidence, null);
        } else if (order.getSellerId().equals(userId)) {
            disputeMapper.updateEvidence(disputeNo, null, evidence);
        } else {
            throw new BusinessException(ResultCode.ORDER_NOT_PARTICIPANT);
        }

        log.info("提交举证: disputeNo={}, userId={}", disputeNo, userId);
    }

    @Override
    @Transactional
    public void arbitrate(String disputeNo, Long arbitratorId, ArbitrateRequest request) {
        Dispute dispute = disputeMapper.selectByDisputeNo(disputeNo);
        if (dispute == null) {
            throw new BusinessException(ResultCode.DISPUTE_NOT_FOUND);
        }
        if (DisputeStatus.CLOSED.name().equals(dispute.getStatus())) {
            throw new BusinessException(ResultCode.DISPUTE_ALREADY_CLOSED);
        }

        // Update dispute
        disputeMapper.updateArbitrate(disputeNo, DisputeStatus.CLOSED.name(),
                arbitratorId, request.getResult(), request.getRemark());

        // Transition order status
        Order order = orderMapper.selectByOrderNo(dispute.getOrderNo());
        OrderStatus newStatus = OrderStateMachine.transition(
                OrderStatus.valueOf(order.getStatus()), OrderEvent.ARBITRATE);
        orderMapper.updateStatusWithVersion(order.getOrderNo(), newStatus.name(), order.getVersion());

        // Execute arbitration result
        if ("BUYER_WIN".equals(request.getResult())) {
            // Refund: restore stock
            inventoryService.releaseStock(order.getProductId(), order.getQuantity());
            productMapper.restoreStock(order.getProductId(), order.getQuantity());
            log.info("仲裁结果: 买家胜诉, 执行退款: orderNo={}", order.getOrderNo());
        } else if ("SELLER_WIN".equals(request.getResult())) {
            // Complete order: create settlement
            settlementService.createSettlement(order);
            log.info("仲裁结果: 卖家胜诉, 完成结算: orderNo={}", order.getOrderNo());
        }

        auditLogService.log(arbitratorId, null, "DISPUTE", "ARBITRATE",
                "DISPUTE", disputeNo,
                "仲裁: result=" + request.getResult() + ", remark=" + request.getRemark(), null);

        log.info("争议仲裁完成: disputeNo={}, result={}", disputeNo, request.getResult());
    }

    @Override
    public PageResult<Dispute> listDisputes(String status, int page, int size) {
        int offset = (page - 1) * size;
        List<Dispute> disputes = disputeMapper.selectAll(status, offset, size);
        long total = disputeMapper.countAll(status);
        return PageResult.of(disputes, total, page, size);
    }
}
