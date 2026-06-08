package com.campus.trade.service;

import com.campus.trade.model.dto.response.UserResponse;
import com.campus.trade.model.entity.User;

public interface UserService {
    UserResponse getCurrentUser(Long userId);
    UserResponse getUserById(Long userId);
    void updateUser(Long userId, String nickname, String phone, String email, String avatarUrl);
    void updateUserStatus(Long userId, Integer status);
}
