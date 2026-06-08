package com.campus.trade.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class RefundRequest { @NotNull private Long orderId; @NotNull @DecimalMin("0.01") private BigDecimal refundAmount; @NotBlank private String reason; private String evidenceUrls; }
