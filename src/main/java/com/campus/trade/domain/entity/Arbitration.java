package com.campus.trade.domain.entity;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class Arbitration {
    private Long id; private String arbitrationNo; private Long disputeId; private Long orderId;
    private Long arbiterId; private String result; private String decision;
    private BigDecimal refundAmount; private Long supersededBy;
    private Integer isActive; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
