package com.campus.trade.service.impl;

import com.campus.trade.common.BusinessException;
import com.campus.trade.common.ResultCode;
import com.campus.trade.mapper.UserMapper;
import com.campus.trade.mapper.UserRoleMapper;
import com.campus.trade.model.dto.request.LoginRequest;
import com.campus.trade.model.dto.request.RegisterRequest;
import com.campus.trade.model.dto.response.LoginResponse;
import com.campus.trade.model.entity.User;
import com.campus.trade.model.entity.UserRole;
import com.campus.trade.model.enums.RoleType;
import com.campus.trade.security.JwtTokenProvider;
import com.campus.trade.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public Long register(RegisterRequest request) {
        if (userMapper.selectByUsername(request.getUsername()) != null) {
            throw new BusinessException(ResultCode.USER_EXISTS);
        }
        if (request.getPhone() != null && userMapper.selectByPhone(request.getPhone()) != null) {
            throw new BusinessException(ResultCode.PHONE_EXISTS);
        }

        // Validate role
        try {
            RoleType.valueOf(request.getRole());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的角色类型");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        userMapper.insert(user);

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRole(request.getRole());
        userRoleMapper.insert(userRole);

        log.info("用户注册成功: userId={}, username={}, role={}", user.getId(), user.getUsername(), request.getRole());
        return user.getId();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        List<String> roles = userRoleMapper.selectByUserId(user.getId()).stream()
                .map(UserRole::getRole)
                .collect(Collectors.toList());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roles(roles)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Refresh token无效或已过期");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == 0) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        List<String> roles = userRoleMapper.selectByUserId(userId).stream()
                .map(UserRole::getRole)
                .collect(Collectors.toList());

        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, username, roles);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, username);

        // Blacklist old refresh token
        jwtTokenProvider.blacklistToken(refreshToken);

        return LoginResponse.builder()
                .userId(userId)
                .username(username)
                .nickname(user.getNickname())
                .roles(roles)
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Override
    public void logout(String token) {
        jwtTokenProvider.blacklistToken(token);
    }

    @Override
    @Transactional
    public void addRole(Long userId, String role) {
        try {
            RoleType.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的角色类型");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        if (userRoleMapper.countByUserIdAndRole(userId, role) > 0) {
            return; // Already has this role
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRole(role);
        userRoleMapper.insert(userRole);
        log.info("用户添加角色: userId={}, role={}", userId, role);
    }
}
