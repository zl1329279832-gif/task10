package com.campus.trade.service;
import com.campus.trade.dto.request.CreateOrderRequest;
import com.campus.trade.dto.request.ShipRequest;
import com.campus.trade.dto.response.OrderResponse;
import com.campus.trade.common.result.PageResult;
public interface OrderService {
    OrderResponse createOrder(Long buyerId, CreateOrderRequest request);
    OrderResponse getOrder(Long orderId);
    OrderResponse getOrderByNo(String orderNo);
    PageResult<OrderResponse> listBuyerOrders(Long buyerId, String status, int page, int size);
    PageResult<OrderResponse> listSellerOrders(Long sellerId, String status, int page, int size);
    void cancelOrder(Long buyerId, Long orderId);
    void shipOrder(Long sellerId, ShipRequest request);
    void confirmReceive(Long buyerId, Long orderId);
    void transitionOrder(Long orderId, String toStatus, Long operatorId, String remark);
    /** Lock-free transition to PAID — caller must already hold the distributed lock on this order. */
    void transitionToPaidInternal(Long orderId, String remark);
    void closeExpiredOrders();
}
