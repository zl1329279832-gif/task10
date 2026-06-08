package com.campus.trade.domain.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class DisputeEvidence {
    private Long id; private Long disputeId; private Long submitterId;
    private String content; private String imageUrls; private LocalDateTime createdAt;
}
