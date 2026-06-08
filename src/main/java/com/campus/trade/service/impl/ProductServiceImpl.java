package com.campus.trade.service.impl;

import com.campus.trade.common.BusinessException;
import com.campus.trade.common.PageResult;
import com.campus.trade.common.ResultCode;
import com.campus.trade.mapper.ProductMapper;
import com.campus.trade.mapper.UserMapper;
import com.campus.trade.model.dto.request.ProductCreateRequest;
import com.campus.trade.model.dto.request.ProductUpdateRequest;
import com.campus.trade.model.dto.response.ProductResponse;
import com.campus.trade.model.entity.Product;
import com.campus.trade.model.entity.User;
import com.campus.trade.service.InventoryService;
import com.campus.trade.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final InventoryService inventoryService;

    @Override
    @Transactional
    public Long createProduct(Long sellerId, ProductCreateRequest request) {
        Product product = new Product();
        product.setSellerId(sellerId);
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setCategory(request.getCategory());
        product.setImageUrls(request.getImageUrls());
        product.setStatus(1); // 上架
        productMapper.insert(product);

        // Sync stock to Redis
        inventoryService.syncStockToRedis(product.getId(), product.getStock());

        log.info("商品发布成功: productId={}, sellerId={}, title={}", product.getId(), sellerId, request.getTitle());
        return product.getId();
    }

    @Override
    @Transactional
    public void updateProduct(Long sellerId, Long productId, ProductUpdateRequest request) {
        Product product = getAndCheckOwner(sellerId, productId);

        Product update = new Product();
        update.setId(productId);
        update.setTitle(request.getTitle());
        update.setDescription(request.getDescription());
        update.setPrice(request.getPrice());
        update.setCategory(request.getCategory());
        update.setImageUrls(request.getImageUrls());

        if (request.getStock() != null) {
            update.setStock(request.getStock());
            inventoryService.syncStockToRedis(productId, request.getStock());
        }

        productMapper.updateById(update);
    }

    @Override
    public void updateProductStatus(Long sellerId, Long productId, Integer status) {
        getAndCheckOwner(sellerId, productId);
        productMapper.updateStatus(productId, status);

        if (status == 1) {
            // Re-sync stock when listing
            Product p = productMapper.selectById(productId);
            inventoryService.syncStockToRedis(productId, p.getStock());
        }
    }

    @Override
    public ProductResponse getProductById(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return toResponse(product);
    }

    @Override
    public PageResult<ProductResponse> listProducts(String category, String keyword, Integer status, int page, int size) {
        int offset = (page - 1) * size;
        Integer queryStatus = status != null ? status : 1; // Default to on-shelf
        List<Product> products = productMapper.selectByCondition(null, category, keyword, queryStatus, offset, size);
        long total = productMapper.countByCondition(null, category, keyword, queryStatus);
        List<ProductResponse> responses = products.stream().map(this::toResponse).collect(Collectors.toList());
        return PageResult.of(responses, total, page, size);
    }

    @Override
    public PageResult<ProductResponse> listMyProducts(Long sellerId, int page, int size) {
        int offset = (page - 1) * size;
        List<Product> products = productMapper.selectByCondition(sellerId, null, null, null, offset, size);
        long total = productMapper.countByCondition(sellerId, null, null, null);
        List<ProductResponse> responses = products.stream().map(this::toResponse).collect(Collectors.toList());
        return PageResult.of(responses, total, page, size);
    }

    @Override
    public void deleteProduct(Long userId, Long productId, boolean isAdmin) {
        if (!isAdmin) {
            getAndCheckOwner(userId, productId);
        }
        productMapper.updateStatus(productId, 0); // Logical delete = off-shelf
    }

    private Product getAndCheckOwner(Long sellerId, Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_OWNER);
        }
        return product;
    }

    private ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSellerId(product.getSellerId());
        response.setTitle(product.getTitle());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice());
        response.setStock(product.getStock());
        response.setCategory(product.getCategory());
        response.setImageUrls(product.getImageUrls());
        response.setStatus(product.getStatus());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());

        User seller = userMapper.selectById(product.getSellerId());
        if (seller != null) {
            response.setSellerName(seller.getNickname() != null ? seller.getNickname() : seller.getUsername());
        }
        return response;
    }
}
