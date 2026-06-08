package com.campus.trade.dto.response;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class SettlementResponse { private Long id; private String settlementNo; private Long orderId; private String orderNo; private Long sellerId; private BigDecimal orderAmount; private BigDecimal platformFee; private BigDecimal settleAmount; private String status; private String statusDesc; private LocalDateTime settledAt; private LocalDateTime createdAt; }
