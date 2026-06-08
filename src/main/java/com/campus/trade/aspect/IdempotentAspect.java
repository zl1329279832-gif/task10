package com.campus.trade.aspect;

import com.campus.trade.annotation.Idempotent;
import com.campus.trade.common.BusinessException;
import com.campus.trade.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final RedisTemplate<String, Object> redisTemplate;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attrs.getRequest();
        String idempotentKey = request.getHeader("X-Idempotent-Key");
        if (idempotentKey == null || idempotentKey.isEmpty()) {
            return joinPoint.proceed();
        }

        String redisKey = "idempotent:" + idempotent.bizType() + ":" + idempotentKey;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "1",
                idempotent.expireSeconds(), TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(isNew)) {
            throw new BusinessException(ResultCode.IDEMPOTENT_REJECT);
        }

        try {
            return joinPoint.proceed();
        } catch (Exception e) {
            // On failure, remove the idempotent key so the request can be retried
            redisTemplate.delete(redisKey);
            throw e;
        }
    }
}
