package com.campus.trade.domain.entity;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class FundSplit {
    private Long id; private String splitNo; private Long arbitrationId; private Long paymentId;
    private Long orderId; private BigDecimal totalAmount;
    private BigDecimal buyerRefundAmount; private BigDecimal sellerSettleAmount; private BigDecimal platformFee;
    private String buyerRefundNo; private String sellerSettlementNo;
    private String status;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
