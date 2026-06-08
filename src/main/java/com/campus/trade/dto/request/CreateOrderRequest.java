package com.campus.trade.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class CreateOrderRequest { @NotNull(message="SKU ID required") private Long skuId; @Min(value=1,message="Quantity>=1") private Integer quantity=1; @NotBlank(message="Address required") private String address; private String remark; }
