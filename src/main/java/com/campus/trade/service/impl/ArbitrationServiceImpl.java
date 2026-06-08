package com.campus.trade.service.impl;

import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.domain.entity.*;
import com.campus.trade.domain.enums.ArbitrationResult;
import com.campus.trade.domain.enums.DisputeStatus;
import com.campus.trade.domain.enums.OrderStatus;
import com.campus.trade.dto.request.ArbitrationRequest;
import com.campus.trade.common.util.BizNoGenerator;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.mapper.*;
import com.campus.trade.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrationServiceImpl implements ArbitrationService {

    private final ArbitrationMapper arbitrationMapper;
    private final DisputeMapper disputeMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final DisputeService disputeService;
    private final AuditService auditService;
    private final DistributedLock distributedLock;

    @Override
    @Transactional
    public Arbitration arbitrate(Long adminId, ArbitrationRequest req) {
        Dispute d = disputeMapper.findById(req.getDisputeId());
        if (d == null) throw new BizException(ErrorCode.DISPUTE_NOT_FOUND);
        if (!DisputeStatus.ARBITRATING.name().equals(d.getStatus()))
            disputeService.escalateToArbitration(req.getDisputeId());
        if (arbitrationMapper.findByDisputeId(d.getId()) != null)
            throw new BizException(ErrorCode.ARBITRATION_ALREADY_DONE);
        try {
            ArbitrationResult.valueOf(req.getResult());
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "Invalid result: " + req.getResult());
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
            a.setRefundAmount(req.getRefundAmount());
            arbitrationMapper.insert(a);

            disputeMapper.updateStatus(d.getId(), "ARBITRATING", "RESOLVED");

            Order o = orderMapper.findById(d.getOrderId());
            ArbitrationResult rt = ArbitrationResult.valueOf(req.getResult());
            switch (rt) {
                case BUYER_WIN ->
                        orderService.transitionOrder(o.getId(), OrderStatus.REFUNDED.name(), adminId, "Arbitration: buyer wins");
                case SELLER_WIN -> {
                    String ts = o.getShipTime() != null ? OrderStatus.SHIPPED.name() : OrderStatus.PAID.name();
                    orderService.transitionOrder(o.getId(), ts, adminId, "Arbitration: seller wins");
                }
                case PARTIAL -> {
                    String ts2 = o.getShipTime() != null ? OrderStatus.SHIPPED.name() : OrderStatus.PAID.name();
                    orderService.transitionOrder(o.getId(), ts2, adminId,
                            "Arbitration: partial refund=" + req.getRefundAmount());
                }
            }

            auditService.log(adminId, null, "ARBITRATION", "DECIDE", "DISPUTE", d.getId(), "result=" + req.getResult());
            return a;
        } finally {
            distributedLock.unlock(lk);
        }
    }
}
