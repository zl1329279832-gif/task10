package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface RefundMapper {
    Refund findById(@Param("id") Long id);
    Refund findByRefundNo(@Param("refundNo") String refundNo);
    Refund findByOrderId(@Param("orderId") Long orderId);
    List<Refund> findBySellerId(@Param("sellerId") Long sellerId, @Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);
    int insert(Refund refund);
    int updateStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);
    int updateApproval(@Param("id") Long id, @Param("approvedBy") Long approvedBy, @Param("approvedAt") java.time.LocalDateTime approvedAt, @Param("rejectReason") String rejectReason);
    int updateRefundTradeNo(@Param("id") Long id, @Param("tradeNo") String tradeNo);
}
