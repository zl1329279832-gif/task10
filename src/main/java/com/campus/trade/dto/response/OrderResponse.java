package com.campus.trade.dto.response;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class OrderResponse {
    private Long id; private String orderNo; private Long buyerId; private String buyerName; private Long sellerId; private String sellerName;
    private Long productId; private String productTitle; private Long skuId; private String skuName; private Integer quantity;
    private BigDecimal unitPrice; private BigDecimal totalAmount; private String status; private String statusDesc;
    private LocalDateTime payTime; private LocalDateTime shipTime; private LocalDateTime receiveTime; private LocalDateTime closeTime;
    private String closeReason; private String logisticsNo; private String logisticsCompany; private String address; private String remark;
    private LocalDateTime payExpireAt; private LocalDateTime createdAt;
}
