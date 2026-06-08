package com.campus.trade.controller;
import com.campus.trade.common.result.Result;
import com.campus.trade.dto.request.LoginRequest;
import com.campus.trade.dto.request.RegisterRequest;
import com.campus.trade.dto.response.LoginResponse;
import com.campus.trade.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@Tag(name="Auth") @RestController @RequestMapping("/api/v1/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    @Operation(summary="Login") @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest r) { return Result.ok(authService.login(r)); }
    @Operation(summary="Register") @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest r) { authService.register(r); return Result.ok(); }
    @Operation(summary="Refresh") @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody Map<String,String> b) { return Result.ok(authService.refreshToken(b.get("refreshToken"))); }
}
