package com.campus.trade.service;

import com.campus.trade.common.PageResult;
import com.campus.trade.model.dto.request.OrderCreateRequest;
import com.campus.trade.model.dto.response.OrderResponse;

public interface OrderService {
    OrderResponse createOrder(Long buyerId, OrderCreateRequest request, String idempotentKey);
    OrderResponse getOrder(String orderNo, Long userId);
    PageResult<OrderResponse> listBuyerOrders(Long buyerId, String status, int page, int size);
    PageResult<OrderResponse> listSellerOrders(Long sellerId, String status, int page, int size);
    PageResult<OrderResponse> listAllOrders(String status, int page, int size);
    void cancelOrder(String orderNo, Long buyerId);
    void shipOrder(String orderNo, Long sellerId);
    void confirmReceive(String orderNo, Long buyerId);
    void closeOrder(String orderNo, String reason);
    void handleTimeoutOrders();
}
