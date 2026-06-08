package com.campus.trade.mapper;
import com.campus.trade.domain.entity.DisputeEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface DisputeEvidenceMapper {
    int insert(DisputeEvidence evidence);
    List<DisputeEvidence> findByDisputeId(@Param("disputeId") Long disputeId);
}
