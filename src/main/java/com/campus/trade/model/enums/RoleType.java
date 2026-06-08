package com.campus.trade.model.enums;

import lombok.Getter;

@Getter
public enum RoleType {
    BUYER("BUYER", "买家"),
    SELLER("SELLER", "卖家"),
    ADMIN("ADMIN", "管理员");

    private final String code;
    private final String description;

    RoleType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
