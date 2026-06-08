package com.campus.trade.model.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class LoginResponse {
    private Long userId;
    private String username;
    private String nickname;
    private List<String> roles;
    private String accessToken;
    private String refreshToken;
}
