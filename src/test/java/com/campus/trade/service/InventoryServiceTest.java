package com.campus.trade.service;

import com.campus.trade.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("锁定库存成功")
    void testLockStockSuccess() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(valueOperations.decrement(anyString(), eq(2L))).thenReturn(3L);

        assertTrue(inventoryService.lockStock(1L, 2));
    }

    @Test
    @DisplayName("库存不足锁定失败并回补")
    void testLockStockInsufficientRollback() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(valueOperations.decrement(anyString(), eq(5L))).thenReturn(-2L);

        assertFalse(inventoryService.lockStock(1L, 5));
        verify(valueOperations).increment(anyString(), eq(5L));
    }

    @Test
    @DisplayName("库存key不存在时锁定失败")
    void testLockStockKeyNotExists() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        assertFalse(inventoryService.lockStock(1L, 1));
    }

    @Test
    @DisplayName("释放库存")
    void testReleaseStock() {
        when(valueOperations.increment(anyString(), eq(3L))).thenReturn(8L);

        inventoryService.releaseStock(1L, 3);
        verify(valueOperations).increment(anyString(), eq(3L));
    }

    @Test
    @DisplayName("同步库存到Redis")
    void testSyncStockToRedis() {
        inventoryService.syncStockToRedis(1L, 10);
        verify(valueOperations).set(anyString(), eq(10));
    }

    @Test
    @DisplayName("获取库存")
    void testGetStock() {
        when(valueOperations.get(anyString())).thenReturn(5);
        assertEquals(5, inventoryService.getStock(1L));
    }

    @Test
    @DisplayName("获取不存在的库存返回0")
    void testGetStockNotExists() {
        when(valueOperations.get(anyString())).thenReturn(null);
        assertEquals(0, inventoryService.getStock(1L));
    }
}
