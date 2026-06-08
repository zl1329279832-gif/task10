package com.campus.trade.domain.entity;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class Sku {
    private Long id; private Long productId; private String skuName; private BigDecimal price;
    private Integer stock; private Integer lockStock; private Integer status;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
