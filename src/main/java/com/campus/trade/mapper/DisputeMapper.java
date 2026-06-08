package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Dispute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface DisputeMapper {
    Dispute findById(@Param("id") Long id);
    Dispute findByDisputeNo(@Param("disputeNo") String disputeNo);
    Dispute findByOrderId(@Param("orderId") Long orderId);
    List<Dispute> findAll(@Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);
    long count(@Param("status") String status);
    int insert(Dispute dispute);
    int updateStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);
}
