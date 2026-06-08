package com.campus.trade.domain.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class Idempotent {
    private Long id; private String idempotentKey; private String bizType;
    private String status; private String result;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
