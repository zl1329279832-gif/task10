package com.campus.trade.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Settlement {
    private Long id;
    private String settlementNo;
    private String orderNo;
    private Long sellerId;
    private BigDecimal amount;
    private BigDecimal platformFee;
    private BigDecimal actualAmount;
    private String status;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
}
