package com.campus.trade.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserPrincipal {
    private Long userId;
    private String username;
    private List<String> roles;
}
