package com.campus.trade.mapper;

import com.campus.trade.model.entity.IdempotentKey;
import org.apache.ibatis.annotations.Param;

public interface IdempotentKeyMapper {
    int insert(IdempotentKey key);
    IdempotentKey selectByKey(@Param("idempotentKey") String idempotentKey);
    int deleteExpired();
}
