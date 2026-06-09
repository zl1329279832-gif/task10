package com.campus.trade.service;
import com.campus.trade.dto.response.SettlementResponse;
import com.campus.trade.common.result.PageResult;
public interface SettlementService {
    SettlementResponse createSettlement(Long orderId);
    void executeSettlement(Long settlementId);
    SettlementResponse retrySettlement(Long settlementId);
    PageResult<SettlementResponse> listSettlements(Long sellerId, String status, int page, int size);
    SettlementResponse getSettlement(Long settlementId);
    /** Lock-free settlement creation — caller must hold the appropriate lock. */
    SettlementResponse createSettlementInternal(Long orderId);
    /** Lock-free settlement execution — caller must hold the appropriate lock. */
    void executeSettlementInternal(Long settlementId);
}
