package com.campus.trade.domain.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class OrderStatusLog {
    private Long id; private Long orderId; private String fromStatus; private String toStatus;
    private Long operatorId; private String remark; private LocalDateTime createdAt;
}
