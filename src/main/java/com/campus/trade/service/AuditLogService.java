package com.campus.trade.service;

import com.campus.trade.common.PageResult;
import com.campus.trade.model.entity.AuditLogEntity;

public interface AuditLogService {
    void log(Long userId, String username, String module, String action,
             String targetType, String targetId, String detail, String ipAddress);
    PageResult<AuditLogEntity> queryLogs(String module, String action, String targetType,
                                          String targetId, Long userId, int page, int size);
}
