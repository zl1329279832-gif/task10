package com.campus.trade.service.impl;

import com.campus.trade.service.InventoryService;
import com.campus.trade.util.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean lockStock(Long productId, int quantity) {
        String key = String.format(RedisKeyConstants.INVENTORY_STOCK, productId);
        // Ensure key exists
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            log.warn("库存key不存在: {}", key);
            return false;
        }

        Long remaining = redisTemplate.opsForValue().decrement(key, quantity);
        if (remaining == null || remaining < 0) {
            // Rollback
            redisTemplate.opsForValue().increment(key, quantity);
            log.warn("库存不足: productId={}, requested={}, remaining={}",
                    productId, quantity, remaining != null ? remaining + quantity : "unknown");
            return false;
        }

        log.info("库存锁定成功: productId={}, quantity={}, remaining={}", productId, quantity, remaining);
        return true;
    }

    @Override
    public void releaseStock(Long productId, int quantity) {
        String key = String.format(RedisKeyConstants.INVENTORY_STOCK, productId);
        Long result = redisTemplate.opsForValue().increment(key, quantity);
        log.info("库存释放: productId={}, quantity={}, current={}", productId, quantity, result);
    }

    @Override
    public void syncStockToRedis(Long productId, int stock) {
        String key = String.format(RedisKeyConstants.INVENTORY_STOCK, productId);
        redisTemplate.opsForValue().set(key, stock);
        log.info("库存同步到Redis: productId={}, stock={}", productId, stock);
    }

    @Override
    public int getStock(Long productId) {
        String key = String.format(RedisKeyConstants.INVENTORY_STOCK, productId);
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }
}
