package com.campus.trade.mapper;

import com.campus.trade.model.entity.RefundRecord;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface RefundRecordMapper {
    RefundRecord selectByRefundNo(@Param("refundNo") String refundNo);
    List<RefundRecord> selectByOrderNo(@Param("orderNo") String orderNo);
    int insert(RefundRecord record);
    int updateHandle(@Param("refundNo") String refundNo, @Param("status") String status,
                     @Param("handlerId") Long handlerId, @Param("handleRemark") String handleRemark);
}
