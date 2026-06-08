package com.campus.trade.service;
import com.campus.trade.domain.entity.Product;
import com.campus.trade.dto.request.CreateProductRequest;
import com.campus.trade.common.result.PageResult;
public interface ProductService { Product createProduct(Long sellerId, CreateProductRequest request); Product getProduct(Long id); PageResult<Product> listProducts(Integer status, String keyword, int page, int size); PageResult<Product> listMyProducts(Long sellerId, Integer status, int page, int size); void updateProductStatus(Long sellerId, Long productId, Integer status); }
