package com.campus.trade.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Dispute {
    private Long id;
    private String disputeNo;
    private String orderNo;
    private String refundNo;
    private Long initiatorId;
    private String reason;
    private String buyerEvidence;
    private String sellerEvidence;
    private String status;
    private Long arbitratorId;
    private String arbitrateResult;
    private String arbitrateRemark;
    private LocalDateTime arbitratedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
