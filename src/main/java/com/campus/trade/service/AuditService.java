package com.campus.trade.service;
public interface AuditService { void log(Long userId, String username, String module, String action, String targetType, Long targetId, String detail); void log(String module, String action, String targetType, Long targetId, String detail); }
