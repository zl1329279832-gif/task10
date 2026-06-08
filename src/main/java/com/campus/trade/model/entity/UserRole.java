package com.campus.trade.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserRole {
    private Long id;
    private Long userId;
    private String role;
    private LocalDateTime createdAt;
}
