package com.campus.trade.domain.entity;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class Payment {
    private Long id; private String paymentNo; private Long orderId; private String orderNo;
    private String tradeNo; private BigDecimal amount; private String payChannel; private String status;
    private String callbackContent; private Integer notifyCount; private LocalDateTime paidAt;
    private BigDecimal frozenAmount; private String freezeReason; private LocalDateTime frozenAt;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
