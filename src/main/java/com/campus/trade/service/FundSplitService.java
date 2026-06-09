package com.campus.trade.service;

import com.campus.trade.domain.entity.FundSplit;

public interface FundSplitService {
    FundSplit executeFundSplit(Long arbitrationId, Long orderId, Long paymentId,
                               java.math.BigDecimal buyerRefundAmount, java.math.BigDecimal sellerSettleAmount,
                               Long operatorId);
    void cancelActiveSplit(Long arbitrationId, Long orderId, Long operatorId);
}
