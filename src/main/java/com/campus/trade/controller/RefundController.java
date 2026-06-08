package com.campus.trade.controller;
import com.campus.trade.common.result.PageResult;
import com.campus.trade.common.result.Result;
import com.campus.trade.common.util.SecurityUtil;
import com.campus.trade.dto.request.RefundRequest;
import com.campus.trade.dto.response.RefundResponse;
import com.campus.trade.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@Tag(name="Refund") @RestController @RequestMapping("/api/v1/refunds") @RequiredArgsConstructor
public class RefundController {
    private final RefundService refundService;
    @PostMapping public Result<RefundResponse> apply(@Valid @RequestBody RefundRequest r) { return Result.ok(refundService.applyRefund(SecurityUtil.currentUserId(), r)); }
    @GetMapping("/{id}") public Result<RefundResponse> get(@PathVariable Long id) { return Result.ok(refundService.getRefund(id)); }
    @PostMapping("/{id}/approve") public Result<RefundResponse> approve(@PathVariable Long id) { return Result.ok(refundService.approveRefund(SecurityUtil.currentUserId(), id)); }
    @PostMapping("/{id}/reject") public Result<RefundResponse> reject(@PathVariable Long id, @RequestBody Map<String,String> b) { return Result.ok(refundService.rejectRefund(SecurityUtil.currentUserId(), id, b.get("rejectReason"))); }
    @GetMapping("/seller") public Result<PageResult<RefundResponse>> list(@RequestParam(required=false) String status, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) { return Result.ok(refundService.listRefunds(SecurityUtil.currentUserId(), status, page, size)); }
}
