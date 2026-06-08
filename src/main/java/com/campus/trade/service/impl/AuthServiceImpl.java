package com.campus.trade.service.impl;

import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.common.util.JwtUtil;
import com.campus.trade.domain.entity.User;
import com.campus.trade.dto.request.LoginRequest;
import com.campus.trade.dto.request.RegisterRequest;
import com.campus.trade.dto.response.LoginResponse;
import com.campus.trade.mapper.UserMapper;
import com.campus.trade.mapper.UserRoleMapper;
import com.campus.trade.service.AuthService;
import com.campus.trade.service.AuditService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null || user.getStatus() != 1)
            throw new BizException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        List<String> roles = userRoleMapper.findRoleCodesByUserId(user.getId());
        String rolesStr = String.join(",", roles);
        LoginResponse r = new LoginResponse();
        r.setAccessToken(jwtUtil.generateToken(user.getId(), user.getUsername(), rolesStr));
        r.setRefreshToken(jwtUtil.generateRefreshToken(user.getId()));
        r.setUserId(user.getId());
        r.setUsername(user.getUsername());
        r.setRoles(rolesStr);
        auditService.log(user.getId(), user.getUsername(), "AUTH", "LOGIN", "USER", user.getId(), null);
        return r;
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userMapper.findByUsername(request.getUsername()) != null)
            throw new BizException(400, "Username already exists");
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        userMapper.insert(user);
        String roleCode = request.getRoleCode() != null ? request.getRoleCode() : "BUYER";
        long roleId = switch (roleCode) {
            case "SELLER" -> 2L;
            case "ADMIN" -> 3L;
            default -> 1L;
        };
        userRoleMapper.insert(user.getId(), roleId);
        auditService.log(user.getId(), user.getUsername(), "AUTH", "REGISTER", "USER", user.getId(), "role=" + roleCode);
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        Claims claims;
        try {
            claims = jwtUtil.parseToken(refreshToken);
        } catch (Exception e) {
            throw new BizException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        if (!"refresh".equals(claims.get("type", String.class)))
            throw new BizException(ErrorCode.AUTH_TOKEN_INVALID);
        Long userId = Long.parseLong(claims.getSubject());
        User user = userMapper.findById(userId);
        if (user == null || user.getStatus() != 1)
            throw new BizException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        List<String> roles = userRoleMapper.findRoleCodesByUserId(userId);
        String rolesStr = String.join(",", roles);
        LoginResponse r = new LoginResponse();
        r.setAccessToken(jwtUtil.generateToken(user.getId(), user.getUsername(), rolesStr));
        r.setRefreshToken(jwtUtil.generateRefreshToken(user.getId()));
        r.setUserId(user.getId());
        r.setUsername(user.getUsername());
        r.setRoles(rolesStr);
        return r;
    }
}
