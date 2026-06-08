package com.campus.trade.mapper;

import com.campus.trade.model.entity.Order;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface OrderMapper {
    Order selectByOrderNo(@Param("orderNo") String orderNo);
    Order selectByIdempotentKey(@Param("idempotentKey") String idempotentKey);
    List<Order> selectByBuyerId(@Param("buyerId") Long buyerId, @Param("status") String status,
                                 @Param("offset") int offset, @Param("limit") int limit);
    long countByBuyerId(@Param("buyerId") Long buyerId, @Param("status") String status);
    List<Order> selectBySellerId(@Param("sellerId") Long sellerId, @Param("status") String status,
                                  @Param("offset") int offset, @Param("limit") int limit);
    long countBySellerId(@Param("sellerId") Long sellerId, @Param("status") String status);
    List<Order> selectTimeoutOrders();
    List<Order> selectAll(@Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);
    long countAll(@Param("status") String status);
    int insert(Order order);
    int updateStatusWithVersion(@Param("orderNo") String orderNo, @Param("newStatus") String newStatus,
                                 @Param("version") int version);
    int updatePaidAt(@Param("orderNo") String orderNo, @Param("newStatus") String newStatus,
                     @Param("version") int version);
    int updateShippedAt(@Param("orderNo") String orderNo, @Param("newStatus") String newStatus,
                        @Param("version") int version);
    int updateReceivedAt(@Param("orderNo") String orderNo, @Param("newStatus") String newStatus,
                         @Param("version") int version);
    int updateClosed(@Param("orderNo") String orderNo, @Param("newStatus") String newStatus,
                     @Param("closeReason") String closeReason, @Param("version") int version);
}
