package com.campus.trade.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class CreateProductRequest { @NotBlank private String title; private String description; private String category; private String images; @NotBlank private String skuName; @NotNull @DecimalMin("0.01") private BigDecimal price; @NotNull @Min(1) private Integer stock; }
