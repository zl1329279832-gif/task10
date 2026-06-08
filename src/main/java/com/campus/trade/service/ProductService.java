package com.campus.trade.service;

import com.campus.trade.common.PageResult;
import com.campus.trade.model.dto.request.ProductCreateRequest;
import com.campus.trade.model.dto.request.ProductUpdateRequest;
import com.campus.trade.model.dto.response.ProductResponse;

public interface ProductService {
    Long createProduct(Long sellerId, ProductCreateRequest request);
    void updateProduct(Long sellerId, Long productId, ProductUpdateRequest request);
    void updateProductStatus(Long sellerId, Long productId, Integer status);
    ProductResponse getProductById(Long productId);
    PageResult<ProductResponse> listProducts(String category, String keyword, Integer status, int page, int size);
    PageResult<ProductResponse> listMyProducts(Long sellerId, int page, int size);
    void deleteProduct(Long userId, Long productId, boolean isAdmin);
}
