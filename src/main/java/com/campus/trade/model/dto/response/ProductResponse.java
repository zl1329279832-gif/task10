package com.campus.trade.model.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductResponse {
    private Long id;
    private Long sellerId;
    private String sellerName;
    private String title;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String category;
    private String imageUrls;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
