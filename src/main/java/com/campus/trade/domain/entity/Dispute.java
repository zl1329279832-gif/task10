package com.campus.trade.domain.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class Dispute {
    private Long id; private String disputeNo; private Long orderId; private String orderNo;
    private Long initiatorId; private Long respondentId; private String reason;
    private String evidenceUrls; private String status;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
