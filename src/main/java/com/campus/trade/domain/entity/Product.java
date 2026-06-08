package com.campus.trade.domain.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class Product {
    private Long id; private Long sellerId; private String title; private String description;
    private String category; private String images; private Integer status;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
