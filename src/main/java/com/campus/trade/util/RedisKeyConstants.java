package com.campus.trade.util;

public class RedisKeyConstants {
    public static final String INVENTORY_STOCK = "inventory:stock:%d";
    public static final String JWT_BLACKLIST = "jwt:blacklist:%s";
    public static final String JWT_REFRESH = "jwt:refresh:%d";
    public static final String IDEMPOTENT_ORDER = "idempotent:order:%s";
    public static final String IDEMPOTENT_PAY = "idempotent:pay:%s";
    public static final String LOCK_PAY_CALLBACK = "lock:pay:callback:%s";
    public static final String CACHE_PRODUCT = "cache:product:%d";

    private RedisKeyConstants() {}
}
