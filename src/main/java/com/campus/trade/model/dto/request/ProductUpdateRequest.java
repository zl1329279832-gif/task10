package com.campus.trade.model.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductUpdateRequest {
    @Size(max = 200, message = "标题不超过200字符")
    private String title;

    private String description;

    @DecimalMin(value = "0.01", message = "价格最低0.01元")
    private BigDecimal price;

    @Min(value = 0, message = "库存不能为负")
    private Integer stock;

    private String category;
    private String imageUrls;
}
