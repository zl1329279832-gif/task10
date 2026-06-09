package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Settlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface SettlementMapper {
    Settlement findById(@Param("id") Long id);
    Settlement findBySettlementNo(@Param("settlementNo") String settlementNo);
    Settlement findByOrderId(@Param("orderId") Long orderId);
    List<Settlement> findBySellerId(@Param("sellerId") Long sellerId, @Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);
    long countBySellerId(@Param("sellerId") Long sellerId, @Param("status") String status);
    int insert(Settlement settlement);
    int updateStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);
    int updateSettledAt(@Param("id") Long id, @Param("settledAt") java.time.LocalDateTime settledAt);
    int updateFreezeInfo(@Param("id") Long id, @Param("frozenAt") java.time.LocalDateTime frozenAt, @Param("freezeReason") String freezeReason);
    int updateUnfreezeInfo(@Param("id") Long id, @Param("unfrozenAt") java.time.LocalDateTime unfrozenAt);
    int updateRetryInfo(@Param("id") Long id, @Param("retryCount") int retryCount, @Param("nextRetryAt") java.time.LocalDateTime nextRetryAt, @Param("failReason") String failReason);
    int updateSettleAmounts(@Param("id") Long id, @Param("settleAmount") java.math.BigDecimal settleAmount, @Param("buyerRefundAmount") java.math.BigDecimal buyerRefundAmount, @Param("platformFee") java.math.BigDecimal platformFee);
    java.util.List<Settlement> findFailedForRetry(@Param("now") java.time.LocalDateTime now, @Param("limit") int limit);
}
