package com.campus.trade.service.impl;

import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.domain.entity.Product;
import com.campus.trade.domain.entity.Sku;
import com.campus.trade.dto.request.CreateProductRequest;
import com.campus.trade.common.result.PageResult;
import com.campus.trade.mapper.ProductMapper;
import com.campus.trade.mapper.SkuMapper;
import com.campus.trade.service.AuditService;
import com.campus.trade.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final AuditService auditService;

    @Override
    @Transactional
    public Product createProduct(Long sellerId, CreateProductRequest req) {
        Product p = new Product();
        p.setSellerId(sellerId);
        p.setTitle(req.getTitle());
        p.setDescription(req.getDescription());
        p.setCategory(req.getCategory());
        p.setImages(req.getImages());
        p.setStatus(1);
        productMapper.insert(p);

        Sku sku = new Sku();
        sku.setProductId(p.getId());
        sku.setSkuName(req.getSkuName());
        sku.setPrice(req.getPrice());
        sku.setStock(req.getStock());
        sku.setLockStock(0);
        sku.setStatus(1);
        skuMapper.insert(sku);

        auditService.log(sellerId, null, "PRODUCT", "CREATE", "PRODUCT", p.getId(), "title=" + p.getTitle());
        return p;
    }

    @Override
    public Product getProduct(Long id) {
        Product p = productMapper.findById(id);
        if (p == null) throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        return p;
    }

    @Override
    public PageResult<Product> listProducts(Integer status, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        return PageResult.of(
                productMapper.findAll(status, keyword, offset, size),
                productMapper.count(status, keyword),
                page, size);
    }

    @Override
    public PageResult<Product> listMyProducts(Long sellerId, Integer status, int page, int size) {
        List<Product> all = productMapper.findBySellerId(sellerId, status);
        int offset = (page - 1) * size;
        int end = Math.min(offset + size, all.size());
        return PageResult.of(
                offset < all.size() ? all.subList(offset, end) : List.of(),
                all.size(), page, size);
    }

    @Override
    @Transactional
    public void updateProductStatus(Long sellerId, Long productId, Integer status) {
        Product p = productMapper.findById(productId);
        if (p == null) throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        if (!p.getSellerId().equals(sellerId)) throw new BizException(ErrorCode.PRODUCT_NOT_OWNER);
        productMapper.updateStatus(productId, status);
        auditService.log(sellerId, null, "PRODUCT", "UPDATE_STATUS", "PRODUCT", productId, "status=" + status);
    }
}
