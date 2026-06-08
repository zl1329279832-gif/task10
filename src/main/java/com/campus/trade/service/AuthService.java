package com.campus.trade.service;
import com.campus.trade.dto.request.LoginRequest;
import com.campus.trade.dto.request.RegisterRequest;
import com.campus.trade.dto.response.LoginResponse;
public interface AuthService { LoginResponse login(LoginRequest request); void register(RegisterRequest request); LoginResponse refreshToken(String refreshToken); }
