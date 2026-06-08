package com.campus.trade.service.impl;

import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.domain.entity.Sku;
import com.campus.trade.mapper.SkuMapper;
import com.campus.trade.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final SkuMapper skuMapper;

    @Override
    public void lockStock(Long skuId, int quantity) {
        if (skuMapper.lockStock(skuId, quantity) == 0) {
            Sku sku = skuMapper.findById(skuId);
            if (sku == null) throw new BizException(ErrorCode.SKU_NOT_FOUND);
            throw new BizException(ErrorCode.INSUFFICIENT_STOCK,
                    "available=" + (sku.getStock() - sku.getLockStock()) + ", requested=" + quantity);
        }
        log.info("Stock locked: skuId={}, qty={}", skuId, quantity);
    }

    @Override
    public void releaseStock(Long skuId, int quantity) {
        if (skuMapper.releaseLockStock(skuId, quantity) == 0)
            log.warn("Release stock failed: skuId={}", skuId);
        else
            log.info("Stock released: skuId={}, qty={}", skuId, quantity);
    }

    @Override
    public void deductStock(Long skuId, int quantity) {
        if (skuMapper.deductLockStock(skuId, quantity) == 0)
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Stock deduction failed");
        log.info("Stock deducted: skuId={}, qty={}", skuId, quantity);
    }
}
