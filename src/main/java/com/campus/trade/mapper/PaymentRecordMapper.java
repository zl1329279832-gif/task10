package com.campus.trade.mapper;

import com.campus.trade.model.entity.PaymentRecord;
import org.apache.ibatis.annotations.Param;

public interface PaymentRecordMapper {
    PaymentRecord selectByTradeNo(@Param("tradeNo") String tradeNo);
    PaymentRecord selectByOrderNo(@Param("orderNo") String orderNo);
    int insert(PaymentRecord record);
    int updateStatus(@Param("tradeNo") String tradeNo, @Param("status") String status);
}
