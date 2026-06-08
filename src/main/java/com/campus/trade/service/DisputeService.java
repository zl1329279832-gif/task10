package com.campus.trade.service;

import com.campus.trade.common.PageResult;
import com.campus.trade.model.dto.request.ArbitrateRequest;
import com.campus.trade.model.dto.request.DisputeRequest;
import com.campus.trade.model.entity.Dispute;

public interface DisputeService {
    String createDispute(Long initiatorId, DisputeRequest request);
    Dispute getDispute(String disputeNo);
    void submitEvidence(String disputeNo, Long userId, String evidence);
    void arbitrate(String disputeNo, Long arbitratorId, ArbitrateRequest request);
    PageResult<Dispute> listDisputes(String status, int page, int size);
}
