package com.campus.trade.domain.entity;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class Settlement {
    private Long id; private String settlementNo; private Long orderId; private String orderNo;
    private Long sellerId; private BigDecimal orderAmount; private BigDecimal platformFee;
    private BigDecimal settleAmount; private String status; private LocalDateTime settledAt;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
