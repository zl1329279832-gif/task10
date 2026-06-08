package com.campus.trade.common.util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;
@Slf4j @Component @RequiredArgsConstructor
public class DistributedLock {
    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "trade:lock:";
    public boolean tryLock(String lockKey, long expireMs) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(PREFIX+lockKey, Thread.currentThread().getName(), expireMs, TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(ok);
    }
    public boolean tryLock(String lockKey) { return tryLock(lockKey, 10_000); }
    public void unlock(String lockKey) { try { redisTemplate.delete(PREFIX+lockKey); } catch (Exception e) { log.warn("Unlock failed: {}",lockKey,e); } }
}
