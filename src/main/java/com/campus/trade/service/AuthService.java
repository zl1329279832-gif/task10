package com.campus.trade.service;

import com.campus.trade.model.dto.request.LoginRequest;
import com.campus.trade.model.dto.request.RegisterRequest;
import com.campus.trade.model.dto.response.LoginResponse;

public interface AuthService {
    Long register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    LoginResponse refreshToken(String refreshToken);
    void logout(String token);
    void addRole(Long userId, String role);
}
