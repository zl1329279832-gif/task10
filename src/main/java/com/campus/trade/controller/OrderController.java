package com.campus.trade.controller;
import com.campus.trade.common.result.PageResult;
import com.campus.trade.common.result.Result;
import com.campus.trade.common.util.SecurityUtil;
import com.campus.trade.dto.request.CreateOrderRequest;
import com.campus.trade.dto.request.ShipRequest;
import com.campus.trade.dto.response.OrderResponse;
import com.campus.trade.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@Tag(name="Order") @RestController @RequestMapping("/api/v1/orders") @RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    @PostMapping public Result<OrderResponse> create(@Valid @RequestBody CreateOrderRequest r) { return Result.ok(orderService.createOrder(SecurityUtil.currentUserId(), r)); }
    @GetMapping("/{id}") public Result<OrderResponse> get(@PathVariable Long id) { return Result.ok(orderService.getOrder(id)); }
    @GetMapping("/no/{orderNo}") public Result<OrderResponse> getByNo(@PathVariable String orderNo) { return Result.ok(orderService.getOrderByNo(orderNo)); }
    @GetMapping("/buyer") public Result<PageResult<OrderResponse>> buyerOrders(@RequestParam(required=false) String status, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) { return Result.ok(orderService.listBuyerOrders(SecurityUtil.currentUserId(), status, page, size)); }
    @GetMapping("/seller") public Result<PageResult<OrderResponse>> sellerOrders(@RequestParam(required=false) String status, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) { return Result.ok(orderService.listSellerOrders(SecurityUtil.currentUserId(), status, page, size)); }
    @PostMapping("/{id}/cancel") public Result<Void> cancel(@PathVariable Long id) { orderService.cancelOrder(SecurityUtil.currentUserId(), id); return Result.ok(); }
    @PostMapping("/ship") public Result<Void> ship(@Valid @RequestBody ShipRequest r) { orderService.shipOrder(SecurityUtil.currentUserId(), r); return Result.ok(); }
    @PostMapping("/{id}/receive") public Result<Void> receive(@PathVariable Long id) { orderService.confirmReceive(SecurityUtil.currentUserId(), id); return Result.ok(); }
}
