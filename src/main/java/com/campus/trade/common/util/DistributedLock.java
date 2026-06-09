package com.campus.trade.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "trade:lock:";

    /**
     * Lua script: compare-and-delete atomically.
     * Only deletes the key if the stored value matches the caller's token.
     */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";

    /**
     * A handle that pairs the lock key with the unique token used to acquire it.
     * Must be passed to {@link #unlock(LockHandle)} to safely release the lock.
     */
    public record LockHandle(String key, String token) {}

    /**
     * Try to acquire a distributed lock with a custom TTL.
     *
     * @return a {@link LockHandle} on success, or {@code null} if the lock is held by another caller.
     */
    public LockHandle tryLock(String lockKey, long expireMs) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(PREFIX + lockKey, token, expireMs, TimeUnit.MILLISECONDS);
        if (Boolean.TRUE.equals(ok)) {
            return new LockHandle(lockKey, token);
        }
        return null;
    }

    /**
     * Try to acquire a distributed lock with the default 10-second TTL.
     */
    public LockHandle tryLock(String lockKey) {
        return tryLock(lockKey, 10_000);
    }

    /**
     * Release the lock using the handle returned by {@link #tryLock}.
     * Only the owner (the thread that acquired the lock) can release it.
     */
    public void unlock(LockHandle handle) {
        if (handle == null) return;
        try {
            redisTemplate.execute(
                    new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                    Collections.singletonList(PREFIX + handle.key()),
                    handle.token());
        } catch (Exception e) {
            log.warn("Unlock failed: {}", handle.key(), e);
        }
    }
}
