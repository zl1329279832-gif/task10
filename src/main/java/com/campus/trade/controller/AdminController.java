package com.campus.trade.controller;

import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.model.dto.response.OrderResponse;
import com.campus.trade.model.entity.AuditLogEntity;
import com.campus.trade.security.UserPrincipal;
import com.campus.trade.service.AuditLogService;
import com.campus.trade.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final OrderService orderService;
    private final AuditLogService auditLogService;

    @GetMapping("/orders")
    public Result<PageResult<OrderResponse>> listAllOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(orderService.listAllOrders(status, page, size));
    }

    @PostMapping("/orders/{orderNo}/close")
    public Result<Void> closeOrder(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable String orderNo,
                                   @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "管理员强制关闭");
        orderService.closeOrder(orderNo, reason);
        auditLogService.log(principal.getUserId(), principal.getUsername(),
                "ORDER", "ADMIN_CLOSE", "ORDER", orderNo, reason, null);
        return Result.success();
    }

    @GetMapping("/audit-logs")
    public Result<PageResult<AuditLogEntity>> queryAuditLogs(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(auditLogService.queryLogs(module, action, targetType, targetId, userId, page, size));
    }
}
