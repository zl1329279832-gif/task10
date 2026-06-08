package com.campus.trade.service.impl;

import com.campus.trade.domain.entity.AuditLog;
import com.campus.trade.mapper.AuditLogMapper;
import com.campus.trade.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;

    @Override
    @Async
    public void log(Long userId, String username, String module, String action,
                    String targetType, Long targetId, String detail) {
        try {
            AuditLog a = new AuditLog();
            a.setUserId(userId);
            a.setUsername(username);
            a.setModule(module);
            a.setAction(action);
            a.setTargetType(targetType);
            a.setTargetId(targetId);
            a.setDetail(detail);
            auditLogMapper.insert(a);
        } catch (Exception e) {
            log.error("Audit log failed: module={},action={}", module, action, e);
        }
    }

    @Override
    public void log(String module, String action, String targetType, Long targetId, String detail) {
        log(null, null, module, action, targetType, targetId, detail);
    }
}
