package com.campus.trade.service;
import com.campus.trade.dto.response.SettlementResponse;
import com.campus.trade.common.result.PageResult;
import java.math.BigDecimal;
public interface SettlementService {
    SettlementResponse createSettlement(Long orderId);
    void executeSettlement(Long settlementId);
    void freezeSettlement(Long orderId, String reason);
    void unfreezeSettlement(Long orderId);
    void splitSettlement(Long orderId, BigDecimal buyerRefundAmount);
    void retryFailedSettlements();
    PageResult<SettlementResponse> listSettlements(Long sellerId, String status, int page, int size);
    SettlementResponse getSettlement(Long settlementId);
}
