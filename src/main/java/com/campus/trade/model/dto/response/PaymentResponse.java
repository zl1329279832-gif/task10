package com.campus.trade.model.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PaymentResponse {
    private String orderNo;
    private BigDecimal amount;
    private String payUrl;
    private String status;
}
