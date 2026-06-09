package com.campus.trade.dto.response;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class FundSplitResponse {
    private Long id; private String splitNo; private Long arbitrationId; private Long orderId;
    private BigDecimal totalAmount; private BigDecimal buyerRefundAmount; private BigDecimal sellerSettleAmount;
    private BigDecimal platformFee; private String buyerRefundNo; private String sellerSettlementNo;
    private String status; private String statusDesc;
    private LocalDateTime createdAt;
}
