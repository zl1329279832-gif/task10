package com.campus.trade.domain.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class AuditLog {
    private Long id; private Long userId; private String username; private String module;
    private String action; private String targetType; private Long targetId; private String detail;
    private String ip; private LocalDateTime createdAt;
}
