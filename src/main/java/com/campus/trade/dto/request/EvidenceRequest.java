package com.campus.trade.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class EvidenceRequest { @NotNull private Long disputeId; @NotBlank private String content; private String imageUrls; }
