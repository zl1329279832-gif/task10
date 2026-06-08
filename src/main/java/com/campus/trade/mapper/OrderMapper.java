package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
@Mapper
public interface OrderMapper {
    Order findById(@Param("id") Long id);
    Order findByOrderNo(@Param("orderNo") String orderNo);
    List<Order> findByBuyerId(@Param("buyerId") Long buyerId, @Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);
    long countByBuyerId(@Param("buyerId") Long buyerId, @Param("status") String status);
    List<Order> findBySellerId(@Param("sellerId") Long sellerId, @Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);
    long countBySellerId(@Param("sellerId") Long sellerId, @Param("status") String status);
    int insert(Order order);
    int updateStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);
    int updatePayInfo(@Param("id") Long id, @Param("payTime") LocalDateTime payTime);
    int updateShipInfo(@Param("id") Long id, @Param("logisticsNo") String logisticsNo, @Param("logisticsCompany") String logisticsCompany, @Param("shipTime") LocalDateTime shipTime);
    int updateReceiveInfo(@Param("id") Long id, @Param("receiveTime") LocalDateTime receiveTime);
    int updateCloseInfo(@Param("id") Long id, @Param("closeTime") LocalDateTime closeTime, @Param("closeReason") String closeReason);
    List<Order> findExpiredOrders(@Param("limit") int limit);
}
