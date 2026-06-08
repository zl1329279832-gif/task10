package com.campus.trade.domain.entity;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class Order {
    private Long id; private String orderNo; private Long buyerId; private Long sellerId;
    private Long productId; private Long skuId; private String skuName; private Integer quantity;
    private BigDecimal unitPrice; private BigDecimal totalAmount; private String status;
    private LocalDateTime payTime; private LocalDateTime shipTime; private LocalDateTime receiveTime;
    private LocalDateTime closeTime; private String closeReason; private String logisticsNo;
    private String logisticsCompany; private String address; private String remark;
    private LocalDateTime payExpireAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
