package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Arbitration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.util.List;
@Mapper
public interface ArbitrationMapper {
    Arbitration findById(@Param("id") Long id);
    Arbitration findByDisputeId(@Param("disputeId") Long disputeId);
    Arbitration findByArbitrationNo(@Param("arbitrationNo") String arbitrationNo);
    List<Arbitration> findByOrderId(@Param("orderId") Long orderId);
    int insert(Arbitration arbitration);
    int updateRuling(@Param("id") Long id, @Param("result") String result, @Param("decision") String decision, @Param("refundAmount") BigDecimal refundAmount, @Param("sellerAmount") BigDecimal sellerAmount);
}
