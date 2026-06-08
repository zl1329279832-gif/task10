package com.campus.trade.controller;

import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.model.dto.request.OrderCreateRequest;
import com.campus.trade.model.dto.response.OrderResponse;
import com.campus.trade.security.UserPrincipal;
import com.campus.trade.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public Result<OrderResponse> createOrder(@AuthenticationPrincipal UserPrincipal principal,
                                              @Valid @RequestBody OrderCreateRequest request,
                                              @RequestHeader(value = "X-Idempotent-Key", required = false) String idempotentKey) {
        OrderResponse response = orderService.createOrder(principal.getUserId(), request, idempotentKey);
        return Result.success(response);
    }

    @GetMapping("/{orderNo}")
    public Result<OrderResponse> getOrder(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable String orderNo) {
        Long userId = principal.getRoles().contains("ADMIN") ? null : principal.getUserId();
        return Result.success(orderService.getOrder(orderNo, userId));
    }

    @GetMapping("/buyer")
    @PreAuthorize("hasRole('BUYER')")
    public Result<PageResult<OrderResponse>> listBuyerOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(orderService.listBuyerOrders(principal.getUserId(), status, page, size));
    }

    @GetMapping("/seller")
    @PreAuthorize("hasRole('SELLER')")
    public Result<PageResult<OrderResponse>> listSellerOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(orderService.listSellerOrders(principal.getUserId(), status, page, size));
    }

    @PostMapping("/{orderNo}/cancel")
    @PreAuthorize("hasRole('BUYER')")
    public Result<Void> cancelOrder(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable String orderNo) {
        orderService.cancelOrder(orderNo, principal.getUserId());
        return Result.success();
    }

    @PostMapping("/{orderNo}/ship")
    @PreAuthorize("hasRole('SELLER')")
    public Result<Void> shipOrder(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable String orderNo) {
        orderService.shipOrder(orderNo, principal.getUserId());
        return Result.success();
    }

    @PostMapping("/{orderNo}/receive")
    @PreAuthorize("hasRole('BUYER')")
    public Result<Void> confirmReceive(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable String orderNo) {
        orderService.confirmReceive(orderNo, principal.getUserId());
        return Result.success();
    }
}
