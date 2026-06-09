package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Settlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
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
    int freeze(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("frozenAmount") BigDecimal frozenAmount, @Param("freezeReason") String freezeReason);
    int unfreeze(@Param("id") Long id, @Param("toStatus") String toStatus);
    int retryFailed(@Param("id") Long id);
}
