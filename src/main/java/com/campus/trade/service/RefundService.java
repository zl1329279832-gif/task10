package com.campus.trade.service;
import com.campus.trade.dto.request.RefundRequest;
import com.campus.trade.dto.response.RefundResponse;
import com.campus.trade.common.result.PageResult;
public interface RefundService { RefundResponse applyRefund(Long buyerId, RefundRequest request); RefundResponse approveRefund(Long sellerId, Long refundId); RefundResponse rejectRefund(Long sellerId, Long refundId, String rejectReason); PageResult<RefundResponse> listRefunds(Long sellerId, String status, int page, int size); RefundResponse getRefund(Long refundId); }
