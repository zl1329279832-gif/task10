package com.campus.trade.controller;
import com.campus.trade.common.result.PageResult;
import com.campus.trade.common.result.Result;
import com.campus.trade.common.util.SecurityUtil;
import com.campus.trade.domain.entity.Product;
import com.campus.trade.domain.entity.Sku;
import com.campus.trade.dto.request.CreateProductRequest;
import com.campus.trade.mapper.SkuMapper;
import com.campus.trade.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@Tag(name="Product") @RestController @RequestMapping("/api/v1/products") @RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final SkuMapper skuMapper;
    @GetMapping public Result<PageResult<Product>> list(@RequestParam(required=false) Integer status, @RequestParam(required=false) String keyword, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) { return Result.ok(productService.listProducts(status!=null?status:1, keyword, page, size)); }
    @GetMapping("/{id}") public Result<Map<String,Object>> get(@PathVariable Long id) { return Result.ok(Map.of("product", productService.getProduct(id), "skus", skuMapper.findByProductId(id))); }
    @PostMapping public Result<Product> create(@Valid @RequestBody CreateProductRequest r) { return Result.ok(productService.createProduct(SecurityUtil.currentUserId(), r)); }
    @GetMapping("/my") public Result<PageResult<Product>> my(@RequestParam(required=false) Integer status, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) { return Result.ok(productService.listMyProducts(SecurityUtil.currentUserId(), status, page, size)); }
    @PutMapping("/{id}/status") public Result<Void> status(@PathVariable Long id, @RequestParam Integer status) { productService.updateProductStatus(SecurityUtil.currentUserId(), id, status); return Result.ok(); }
}
