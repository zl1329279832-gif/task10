package com.campus.trade.service;

import com.campus.trade.dto.response.SettlementResponse;

public interface EscrowService {
    void freezeFunds(Long orderId, Long operatorId, String reason);
    void unfreezeFunds(Long orderId, Long operatorId, String reason);
    void autoSettleOnReceipt(Long orderId, Long buyerId);
    SettlementResponse retrySettlement(Long settlementId, Long operatorId);
    /** Lock-free freeze — caller must hold the order lock. */
    void freezeFundsInternal(Long orderId, Long operatorId, String reason);
    /** Lock-free unfreeze — caller must hold the order lock. */
    void unfreezeFundsInternal(Long orderId, Long operatorId, String reason);
    /** Lock-free auto-settle — caller must hold the order lock. */
    void autoSettleOnReceiptInternal(Long orderId, Long buyerId);
}
