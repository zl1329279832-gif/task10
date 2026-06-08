package com.campus.trade.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundRecord {
    private Long id;
    private String refundNo;
    private String orderNo;
    private Long buyerId;
    private Long sellerId;
    private BigDecimal amount;
    private String reason;
    private String evidenceUrls;
    private String status;
    private Long handlerId;
    private String handleRemark;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
