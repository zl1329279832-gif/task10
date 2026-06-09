package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface PaymentMapper {
    Payment findById(@Param("id") Long id);
    Payment findByPaymentNo(@Param("paymentNo") String paymentNo);
    Payment findByOrderNo(@Param("orderNo") String orderNo);
    Payment findByOrderId(@Param("orderId") Long orderId);
    Payment findByTradeNo(@Param("tradeNo") String tradeNo);
    int insert(Payment payment);
    int updateStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);
    int updateTradeNo(@Param("id") Long id, @Param("tradeNo") String tradeNo, @Param("paidAt") java.time.LocalDateTime paidAt);
    int incrementNotifyCount(@Param("id") Long id);
    int updateCallbackContent(@Param("id") Long id, @Param("content") String content);
}
