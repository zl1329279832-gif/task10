package com.campus.trade.model.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductCreateRequest {
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不超过200字符")
    private String title;

    private String description;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格最低0.01元")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;

    @NotBlank(message = "分类不能为空")
    private String category;

    private String imageUrls;
}
