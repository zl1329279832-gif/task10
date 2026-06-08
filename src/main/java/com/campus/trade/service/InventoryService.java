package com.campus.trade.service;

public interface InventoryService {
    boolean lockStock(Long productId, int quantity);
    void releaseStock(Long productId, int quantity);
    void syncStockToRedis(Long productId, int stock);
    int getStock(Long productId);
}
