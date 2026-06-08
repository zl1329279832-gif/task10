package com.campus.trade.service;

import com.campus.trade.model.dto.request.RefundRequest;
import com.campus.trade.model.entity.RefundRecord;
import java.util.List;

public interface RefundService {
    String applyRefund(Long buyerId, RefundRequest request);
    RefundRecord getRefund(String refundNo, Long userId);
    List<RefundRecord> getRefundsByOrderNo(String orderNo, Long userId);
    void approveRefund(String refundNo, Long sellerId, String remark);
    void rejectRefund(String refundNo, Long sellerId, String remark);
}
