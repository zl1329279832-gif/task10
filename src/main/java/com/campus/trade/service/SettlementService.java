package com.campus.trade.service;

import com.campus.trade.common.PageResult;
import com.campus.trade.model.entity.Order;
import com.campus.trade.model.entity.Settlement;

public interface SettlementService {
    void createSettlement(Order order);
    Settlement getSettlement(String settlementNo);
    PageResult<Settlement> listSellerSettlements(Long sellerId, int page, int size);
    PageResult<Settlement> listAllSettlements(int page, int size);
}
