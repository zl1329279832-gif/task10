package com.campus.trade.aspect;

import com.campus.trade.annotation.AuditLog;
import com.campus.trade.security.UserPrincipal;
import com.campus.trade.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            Long userId = null;
            String username = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
                userId = principal.getUserId();
                username = principal.getUsername();
            }

            String ipAddress = null;
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                ipAddress = request.getRemoteAddr();
            }

            // Extract target ID from method parameters
            String targetId = extractTargetId(joinPoint);

            // Build detail from method args
            String detail = buildDetail(joinPoint);

            auditLogService.log(userId, username, auditLog.module(), auditLog.action(),
                    auditLog.targetType(), targetId, detail, ipAddress);
        } catch (Exception e) {
            log.error("记录审计日志失败", e);
        }

        return result;
    }

    private String extractTargetId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] params = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < params.length; i++) {
            String name = params[i].getName();
            if (("orderNo".equals(name) || "refundNo".equals(name) || "disputeNo".equals(name)
                    || "settlementNo".equals(name) || "id".equals(name))
                    && args[i] != null) {
                return args[i].toString();
            }
        }
        return null;
    }

    private String buildDetail(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            Map<String, Object> map = new HashMap<>();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    if (args[i] != null && !(args[i] instanceof UserPrincipal)
                            && !(args[i] instanceof HttpServletRequest)) {
                        map.put(paramNames[i], args[i]);
                    }
                }
            }
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }
}
