package com.campus.trade.service;
public interface InventoryService { void lockStock(Long skuId, int quantity); void releaseStock(Long skuId, int quantity); void deductStock(Long skuId, int quantity); }
