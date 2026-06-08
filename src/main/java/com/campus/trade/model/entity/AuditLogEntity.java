package com.campus.trade.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditLogEntity {
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String ipAddress;
    private LocalDateTime createdAt;
}
