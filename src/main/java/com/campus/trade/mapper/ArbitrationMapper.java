package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Arbitration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface ArbitrationMapper {
    Arbitration findById(@Param("id") Long id);
    Arbitration findByDisputeId(@Param("disputeId") Long disputeId);
    Arbitration findByArbitrationNo(@Param("arbitrationNo") String arbitrationNo);
    Arbitration findActiveByDisputeId(@Param("disputeId") Long disputeId);
    List<Arbitration> findAllByDisputeId(@Param("disputeId") Long disputeId);
    int insert(Arbitration arbitration);
    int deactivate(@Param("id") Long id, @Param("supersededBy") Long supersededBy);
}
