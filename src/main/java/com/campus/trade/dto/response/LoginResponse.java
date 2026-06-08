package com.campus.trade.dto.response;
import lombok.Data;
@Data
public class LoginResponse { private String accessToken; private String refreshToken; private Long userId; private String username; private String roles; }
