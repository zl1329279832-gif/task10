package com.campus.trade.domain.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class User {
    private Long id; private String username; private String password; private String nickname;
    private String avatar; private String phone; private String email; private Integer status;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
