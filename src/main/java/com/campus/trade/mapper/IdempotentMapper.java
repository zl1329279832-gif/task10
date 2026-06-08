package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Idempotent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface IdempotentMapper {
    int insert(Idempotent record);
    Idempotent findByKey(@Param("bizType") String bizType, @Param("idempotentKey") String key);
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("result") String result);
}
