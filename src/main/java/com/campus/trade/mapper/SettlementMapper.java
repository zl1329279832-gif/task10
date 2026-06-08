package com.campus.trade.mapper;

import com.campus.trade.model.entity.Settlement;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface SettlementMapper {
    Settlement selectBySettlementNo(@Param("settlementNo") String settlementNo);
    Settlement selectByOrderNo(@Param("orderNo") String orderNo);
    List<Settlement> selectBySellerId(@Param("sellerId") Long sellerId, @Param("offset") int offset, @Param("limit") int limit);
    long countBySellerId(@Param("sellerId") Long sellerId);
    List<Settlement> selectAll(@Param("offset") int offset, @Param("limit") int limit);
    long countAll();
    int insert(Settlement settlement);
    int updateStatus(@Param("settlementNo") String settlementNo, @Param("status") String status);
}
