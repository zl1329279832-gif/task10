package com.campus.trade.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class IdempotentKey {
    private Long id;
    private String idempotentKey;
    private String bizType;
    private String bizId;
    private LocalDateTime createdAt;
    private LocalDateTime expireAt;
}
