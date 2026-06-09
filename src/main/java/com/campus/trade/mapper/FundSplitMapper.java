package com.campus.trade.mapper;
import com.campus.trade.domain.entity.FundSplit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface FundSplitMapper {
    FundSplit findById(@Param("id") Long id);
    FundSplit findBySplitNo(@Param("splitNo") String splitNo);
    List<FundSplit> findByArbitrationId(@Param("arbitrationId") Long arbitrationId);
    FundSplit findActiveByArbitrationId(@Param("arbitrationId") Long arbitrationId);
    List<FundSplit> findByOrderId(@Param("orderId") Long orderId);
    int insert(FundSplit fundSplit);
    int updateStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);
    int updateBuyerRefundNo(@Param("id") Long id, @Param("buyerRefundNo") String buyerRefundNo);
    int updateSellerSettlementNo(@Param("id") Long id, @Param("sellerSettlementNo") String sellerSettlementNo);
}
