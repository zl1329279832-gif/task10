package com.campus.trade.domain.entity;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class Refund {
    private Long id; private String refundNo; private Long orderId; private String orderNo;
    private Long buyerId; private Long sellerId; private BigDecimal refundAmount; private String reason;
    private String status; private String evidenceUrls; private String rejectReason; private Long approvedBy;
    private LocalDateTime approvedAt; private String refundTradeNo;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
