package com.campus.trade.service;

import com.campus.trade.dto.response.SettlementResponse;

public interface EscrowService {
    void freezeFunds(Long orderId, Long operatorId, String reason);
    void unfreezeFunds(Long orderId, Long operatorId, String reason);
    void autoSettleOnReceipt(Long orderId, Long buyerId);
    SettlementResponse retrySettlement(Long settlementId, Long operatorId);
}
