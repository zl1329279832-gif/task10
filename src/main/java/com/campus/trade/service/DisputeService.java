package com.campus.trade.service;
import com.campus.trade.dto.request.DisputeRequest;
import com.campus.trade.dto.request.EvidenceRequest;
import com.campus.trade.dto.response.DisputeResponse;
import com.campus.trade.common.result.PageResult;
public interface DisputeService { DisputeResponse createDispute(Long userId, DisputeRequest request); void submitEvidence(Long userId, EvidenceRequest request); void escalateToArbitration(Long disputeId); PageResult<DisputeResponse> listDisputes(String status, int page, int size); DisputeResponse getDispute(Long disputeId); }
