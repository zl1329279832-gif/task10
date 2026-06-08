package com.campus.trade.controller;

import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.model.dto.request.ProductCreateRequest;
import com.campus.trade.model.dto.request.ProductUpdateRequest;
import com.campus.trade.model.dto.response.ProductResponse;
import com.campus.trade.security.UserPrincipal;
import com.campus.trade.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public Result<Map<String, Long>> createProduct(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody ProductCreateRequest request) {
        Long productId = productService.createProduct(principal.getUserId(), request);
        return Result.success(Map.of("productId", productId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public Result<Void> updateProduct(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable Long id,
                                      @Valid @RequestBody ProductUpdateRequest request) {
        productService.updateProduct(principal.getUserId(), id, request);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SELLER')")
    public Result<Void> updateProductStatus(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Integer> body) {
        productService.updateProductStatus(principal.getUserId(), id, body.get("status"));
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<ProductResponse> getProduct(@PathVariable Long id) {
        return Result.success(productService.getProductById(id));
    }

    @GetMapping
    public Result<PageResult<ProductResponse>> listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(productService.listProducts(category, keyword, status, page, size));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('SELLER')")
    public Result<PageResult<ProductResponse>> listMyProducts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(productService.listMyProducts(principal.getUserId(), page, size));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
    public Result<Void> deleteProduct(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable Long id) {
        boolean isAdmin = principal.getRoles().contains("ADMIN");
        productService.deleteProduct(principal.getUserId(), id, isAdmin);
        return Result.success();
    }
}
