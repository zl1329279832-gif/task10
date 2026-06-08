package com.campus.trade.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class DisputeRequest { @NotNull private Long orderId; @NotBlank private String reason; private String evidenceUrls; }
