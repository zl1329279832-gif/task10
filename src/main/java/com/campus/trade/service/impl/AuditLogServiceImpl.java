package com.campus.trade.service.impl;

import com.campus.trade.common.PageResult;
import com.campus.trade.mapper.AuditLogMapper;
import com.campus.trade.model.entity.AuditLogEntity;
import com.campus.trade.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void log(Long userId, String username, String module, String action,
                    String targetType, String targetId, String detail, String ipAddress) {
        AuditLogEntity logEntity = new AuditLogEntity();
        logEntity.setUserId(userId);
        logEntity.setUsername(username);
        logEntity.setModule(module);
        logEntity.setAction(action);
        logEntity.setTargetType(targetType);
        logEntity.setTargetId(targetId);
        logEntity.setDetail(detail);
        logEntity.setIpAddress(ipAddress);
        auditLogMapper.insert(logEntity);
        log.debug("审计日志: module={}, action={}, target={}:{}", module, action, targetType, targetId);
    }

    @Override
    public PageResult<AuditLogEntity> queryLogs(String module, String action, String targetType,
                                                 String targetId, Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<AuditLogEntity> logs = auditLogMapper.selectByCondition(module, action, targetType, targetId, userId, offset, size);
        long total = auditLogMapper.countByCondition(module, action, targetType, targetId, userId);
        return PageResult.of(logs, total, page, size);
    }
}
