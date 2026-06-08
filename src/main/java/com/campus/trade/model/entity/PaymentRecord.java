package com.campus.trade.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentRecord {
    private Long id;
    private String orderNo;
    private String tradeNo;
    private BigDecimal amount;
    private String status;
    private String payChannel;
    private String callbackRaw;
    private LocalDateTime callbackTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
