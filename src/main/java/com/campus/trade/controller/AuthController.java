package com.campus.trade.controller;

import com.campus.trade.common.Result;
import com.campus.trade.model.dto.request.LoginRequest;
import com.campus.trade.model.dto.request.RegisterRequest;
import com.campus.trade.model.dto.response.LoginResponse;
import com.campus.trade.security.UserPrincipal;
import com.campus.trade.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<Map<String, Long>> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = authService.register(request);
        return Result.success(Map.of("userId", userId));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        LoginResponse response = authService.refreshToken(refreshToken);
        return Result.success(response);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String header) {
        String token = header.replace("Bearer ", "");
        authService.logout(token);
        return Result.success();
    }

    @PostMapping("/role/switch")
    public Result<Void> addRole(@AuthenticationPrincipal UserPrincipal principal,
                                @RequestBody Map<String, String> body) {
        authService.addRole(principal.getUserId(), body.get("role"));
        return Result.success();
    }
}
