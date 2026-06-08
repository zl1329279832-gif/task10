package com.campus.trade.service.impl;

import com.campus.trade.common.BusinessException;
import com.campus.trade.common.ResultCode;
import com.campus.trade.mapper.UserMapper;
import com.campus.trade.mapper.UserRoleMapper;
import com.campus.trade.model.dto.response.UserResponse;
import com.campus.trade.model.entity.User;
import com.campus.trade.model.entity.UserRole;
import com.campus.trade.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public UserResponse getCurrentUser(Long userId) {
        return getUserById(userId);
    }

    @Override
    public UserResponse getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toResponse(user);
    }

    @Override
    public void updateUser(Long userId, String nickname, String phone, String email, String avatarUrl) {
        User user = new User();
        user.setId(userId);
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setEmail(email);
        user.setAvatarUrl(avatarUrl);
        userMapper.updateById(user);
    }

    @Override
    public void updateUserStatus(Long userId, Integer status) {
        userMapper.updateStatus(userId, status);
    }

    private UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setPhone(user.getPhone());
        response.setEmail(user.getEmail());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setStatus(user.getStatus());
        response.setCreatedAt(user.getCreatedAt());

        List<String> roles = userRoleMapper.selectByUserId(user.getId()).stream()
                .map(UserRole::getRole)
                .collect(Collectors.toList());
        response.setRoles(roles);
        return response;
    }
}
