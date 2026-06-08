package com.campus.trade.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class ArbitrationRequest { @NotNull private Long disputeId; @NotBlank private String result; @NotBlank private String decision; private BigDecimal refundAmount; }
