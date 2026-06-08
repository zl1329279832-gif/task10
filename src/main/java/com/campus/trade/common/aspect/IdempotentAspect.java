package com.campus.trade.common.aspect;
import com.campus.trade.common.annotation.Idempotent;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.mapper.IdempotentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;
@Slf4j @Aspect @Component @RequiredArgsConstructor
public class IdempotentAspect {
    private final IdempotentMapper idempotentMapper;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();
    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        String[] paramNames = nameDiscoverer.getParameterNames(method);
        Object[] args = pjp.getArgs();
        var ctx = new StandardEvaluationContext();
        if (paramNames != null) for (int i=0;i<paramNames.length;i++) ctx.setVariable(paramNames[i], args[i]);
        String key = parser.parseExpression(idempotent.key()).getValue(ctx, String.class);
        String bizType = idempotent.bizType();
        com.campus.trade.domain.entity.Idempotent record = new com.campus.trade.domain.entity.Idempotent();
        record.setIdempotentKey(key); record.setBizType(bizType); record.setStatus("PROCESSING");
        try {
            idempotentMapper.insert(record);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            com.campus.trade.domain.entity.Idempotent existing = idempotentMapper.findByKey(bizType, key);
            if (existing != null && "SUCCESS".equals(existing.getStatus())) { log.info("Idempotent dup: {}",key); return null; }
            throw new BizException(ErrorCode.IDEMPOTENT_PROCESSING);
        }
        try {
            Object result = pjp.proceed();
            idempotentMapper.updateStatus(record.getId(), "SUCCESS", null);
            return result;
        } catch (Exception ex) {
            idempotentMapper.updateStatus(record.getId(), "FAIL", ex.getMessage());
            throw ex;
        }
    }
}
