package com.campus.trade.controller;
import com.campus.trade.common.result.PageResult;
import com.campus.trade.common.result.Result;
import com.campus.trade.domain.entity.AuditLog;
import com.campus.trade.domain.entity.Dispute;
import com.campus.trade.domain.entity.OrderStatusLog;
import com.campus.trade.mapper.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@Tag(name="Admin") @RestController @RequestMapping("/api/v1/admin") @PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class AdminController {
    private final AuditLogMapper auditLogMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final DisputeMapper disputeMapper;
    @GetMapping("/audit-logs") public Result<PageResult<AuditLog>> logs(@RequestParam(required=false) String module, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="50") int size) {
        int off = (page-1)*size; return Result.ok(PageResult.of(auditLogMapper.findByModule(module, off, size), auditLogMapper.countByModule(module), page, size));
    }
    @GetMapping("/orders/{orderId}/status-log") public Result<List<OrderStatusLog>> statusLog(@PathVariable Long orderId) { return Result.ok(statusLogMapper.findByOrderId(orderId)); }
    @GetMapping("/audit-logs/{targetType}/{targetId}") public Result<List<AuditLog>> entityLogs(@PathVariable String targetType, @PathVariable Long targetId) { return Result.ok(auditLogMapper.findByTarget(targetType, targetId)); }
    @GetMapping("/disputes") public Result<PageResult<Dispute>> disputes(@RequestParam(required=false) String status, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        int off = (page-1)*size; return Result.ok(PageResult.of(disputeMapper.findAll(status, off, size), disputeMapper.count(status), page, size));
    }
}
