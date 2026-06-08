package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Arbitration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface ArbitrationMapper {
    Arbitration findById(@Param("id") Long id);
    Arbitration findByDisputeId(@Param("disputeId") Long disputeId);
    Arbitration findByArbitrationNo(@Param("arbitrationNo") String arbitrationNo);
    int insert(Arbitration arbitration);
}
