package com.campus.trade.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Product {
    private Long id;
    private Long sellerId;
    private String title;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String category;
    private String imageUrls;
    private Integer status;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
