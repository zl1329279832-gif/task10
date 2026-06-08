package com.campus.trade.dto.response;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class RefundResponse { private Long id; private String refundNo; private Long orderId; private String orderNo; private BigDecimal refundAmount; private String reason; private String status; private String statusDesc; private String evidenceUrls; private String rejectReason; private LocalDateTime createdAt; private LocalDateTime approvedAt; }
