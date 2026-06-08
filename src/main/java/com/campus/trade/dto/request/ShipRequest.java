package com.campus.trade.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class ShipRequest { @NotNull private Long orderId; @NotBlank private String logisticsNo; @NotBlank private String logisticsCompany; }
