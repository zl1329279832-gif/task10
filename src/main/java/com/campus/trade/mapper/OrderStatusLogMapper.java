package com.campus.trade.mapper;
import com.campus.trade.domain.entity.OrderStatusLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface OrderStatusLogMapper {
    int insert(OrderStatusLog log);
    List<OrderStatusLog> findByOrderId(@Param("orderId") Long orderId);
    OrderStatusLog findLatestByOrderId(@Param("orderId") Long orderId);
}
