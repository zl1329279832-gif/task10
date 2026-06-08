package com.campus.trade.common.util;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;
@Component @RequiredArgsConstructor
public class RedisUtil {
    private final StringRedisTemplate redisTemplate;
    public void set(String k,String v) { redisTemplate.opsForValue().set(k,v); }
    public void set(String k,String v,long t,TimeUnit u) { redisTemplate.opsForValue().set(k,v,t,u); }
    public String get(String k) { return redisTemplate.opsForValue().get(k); }
    public Boolean delete(String k) { return redisTemplate.delete(k); }
    public Boolean hasKey(String k) { return redisTemplate.hasKey(k); }
    public Long increment(String k) { return redisTemplate.opsForValue().increment(k); }
}
