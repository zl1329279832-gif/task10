package com.campus.trade.controller;

import com.campus.trade.common.Result;
import com.campus.trade.model.dto.response.UserResponse;
import com.campus.trade.security.UserPrincipal;
import com.campus.trade.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return Result.success(userService.getCurrentUser(principal.getUserId()));
    }

    @PutMapping("/me")
    public Result<Void> updateCurrentUser(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, String> body) {
        userService.updateUser(principal.getUserId(),
                body.get("nickname"), body.get("phone"), body.get("email"), body.get("avatarUrl"));
        return Result.success();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserResponse> getUserById(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.updateUserStatus(id, body.get("status"));
        return Result.success();
    }
}
