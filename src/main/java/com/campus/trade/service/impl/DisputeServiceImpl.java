package com.campus.trade.service.impl;

import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.domain.entity.*;
import com.campus.trade.domain.enums.DisputeStatus;
import com.campus.trade.domain.enums.OrderStatus;
import com.campus.trade.dto.request.DisputeRequest;
import com.campus.trade.dto.request.EvidenceRequest;
import com.campus.trade.dto.response.DisputeResponse;
import com.campus.trade.common.result.PageResult;
import com.campus.trade.common.util.BizNoGenerator;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.mapper.*;
import com.campus.trade.service.*;
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
    private final DisputeEvidenceMapper evidenceMapper;
    private final ArbitrationMapper arbitrationMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final AuditService auditService;
    private final DistributedLock distributedLock;
    private final EscrowService escrowService;

    @Override
    @Transactional
    public DisputeResponse createDispute(Long userId, DisputeRequest req) {
        Order o = orderMapper.findById(req.getOrderId());
        if (o == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);

        boolean isBuyer = o.getBuyerId().equals(userId);
        boolean isSeller = o.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) throw new BizException(ErrorCode.DISPUTE_NOT_PARTICIPANT);

        String os = o.getStatus();
        if (!OrderStatus.PAID.name().equals(os)
                && !OrderStatus.SHIPPED.name().equals(os)
                && !OrderStatus.RECEIVED.name().equals(os)
                && !OrderStatus.REFUNDING.name().equals(os))
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, os);

        Dispute ex = disputeMapper.findByOrderId(o.getId());
        if (ex != null
                && !DisputeStatus.CLOSED.name().equals(ex.getStatus())
                && !DisputeStatus.RESOLVED.name().equals(ex.getStatus()))
            throw new BizException(ErrorCode.DISPUTE_ALREADY_EXISTS);

        Dispute d = new Dispute();
        d.setDisputeNo(BizNoGenerator.disputeNo());
        d.setOrderId(o.getId());
        d.setOrderNo(o.getOrderNo());
        d.setInitiatorId(userId);
        d.setRespondentId(isBuyer ? o.getSellerId() : o.getBuyerId());
        d.setReason(req.getReason());
        d.setEvidenceUrls(req.getEvidenceUrls());
        d.setStatus(DisputeStatus.EVIDENCE.name());
        disputeMapper.insert(d);

        orderService.transitionOrder(o.getId(), OrderStatus.DISPUTED.name(), userId, "Dispute raised");

        // Freeze escrow funds when dispute is initiated
        try {
            escrowService.freezeFunds(o.getId(), userId, "Dispute raised: " + d.getDisputeNo());
        } catch (Exception e) {
            log.warn("Failed to freeze funds on dispute create, orderId={}: {}", o.getId(), e.getMessage());
        }

        return toResp(d);
    }

    @Override
    @Transactional
    public void submitEvidence(Long userId, EvidenceRequest req) {
        Dispute d = disputeMapper.findById(req.getDisputeId());
        if (d == null) throw new BizException(ErrorCode.DISPUTE_NOT_FOUND);
        if (!d.getInitiatorId().equals(userId) && !d.getRespondentId().equals(userId))
            throw new BizException(ErrorCode.DISPUTE_NOT_PARTICIPANT);
        if (!DisputeStatus.EVIDENCE.name().equals(d.getStatus())
                && !DisputeStatus.ARBITRATING.name().equals(d.getStatus()))
            throw new BizException(ErrorCode.DISPUTE_NOT_IN_EVIDENCE);

        DisputeEvidence e = new DisputeEvidence();
        e.setDisputeId(d.getId());
        e.setSubmitterId(userId);
        e.setContent(req.getContent());
        e.setImageUrls(req.getImageUrls());
        evidenceMapper.insert(e);
    }

    @Override
    @Transactional
    public void escalateToArbitration(Long disputeId) {
        Dispute d = disputeMapper.findById(disputeId);
        if (d == null) throw new BizException(ErrorCode.DISPUTE_NOT_FOUND);

        String lk = "dispute:" + disputeId;
        if (!distributedLock.tryLock(lk)) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            if (disputeMapper.updateStatus(disputeId, "EVIDENCE", "ARBITRATING") == 0)
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
        } finally {
            distributedLock.unlock(lk);
        }
    }

    @Override
    public PageResult<DisputeResponse> listDisputes(String status, int page, int size) {
        int off = (page - 1) * size;
        List<Dispute> list = disputeMapper.findAll(status, off, size);
        return PageResult.of(list.stream().map(this::toResp).toList(), disputeMapper.count(status), page, size);
    }

    @Override
    public DisputeResponse getDispute(Long id) {
        Dispute d = disputeMapper.findById(id);
        if (d == null) throw new BizException(ErrorCode.DISPUTE_NOT_FOUND);
        return toResp(d);
    }

    private DisputeResponse toResp(Dispute d) {
        DisputeResponse r = new DisputeResponse();
        r.setId(d.getId());
        r.setDisputeNo(d.getDisputeNo());
        r.setOrderId(d.getOrderId());
        r.setOrderNo(d.getOrderNo());
        r.setInitiatorId(d.getInitiatorId());
        r.setRespondentId(d.getRespondentId());
        r.setReason(d.getReason());
        r.setEvidenceUrls(d.getEvidenceUrls());
        r.setStatus(d.getStatus());
        r.setStatusDesc(DisputeStatus.valueOf(d.getStatus()).getDesc());
        r.setCreatedAt(d.getCreatedAt());
        r.setEvidences(evidenceMapper.findByDisputeId(d.getId()));
        r.setArbitration(arbitrationMapper.findByDisputeId(d.getId()));
        return r;
    }
}
