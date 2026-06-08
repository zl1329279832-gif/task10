package com.campus.trade.mapper;

import com.campus.trade.model.entity.Dispute;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface DisputeMapper {
    Dispute selectByDisputeNo(@Param("disputeNo") String disputeNo);
    Dispute selectByOrderNo(@Param("orderNo") String orderNo);
    List<Dispute> selectAll(@Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);
    long countAll(@Param("status") String status);
    int insert(Dispute dispute);
    int updateEvidence(@Param("disputeNo") String disputeNo, @Param("buyerEvidence") String buyerEvidence,
                       @Param("sellerEvidence") String sellerEvidence);
    int updateArbitrate(@Param("disputeNo") String disputeNo, @Param("status") String status,
                        @Param("arbitratorId") Long arbitratorId, @Param("arbitrateResult") String arbitrateResult,
                        @Param("arbitrateRemark") String arbitrateRemark);
}
