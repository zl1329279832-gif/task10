package com.campus.trade.domain.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class Role {
    private Long id; private String roleCode; private String roleName; private String description;
    private LocalDateTime createdAt;
}
