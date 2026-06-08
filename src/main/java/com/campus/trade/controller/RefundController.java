package com.campus.trade.controller;

import com.campus.trade.common.Result;
import com.campus.trade.model.dto.request.RefundRequest;
import com.campus.trade.model.entity.RefundRecord;
import com.campus.trade.security.UserPrincipal;
import com.campus.trade.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public Result<Map<String, String>> applyRefund(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody RefundRequest request) {
        String refundNo = refundService.applyRefund(principal.getUserId(), request);
        return Result.success(Map.of("refundNo", refundNo));
    }

    @GetMapping("/{refundNo}")
    public Result<RefundRecord> getRefund(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable String refundNo) {
        Long userId = principal.getRoles().contains("ADMIN") ? null : principal.getUserId();
        return Result.success(refundService.getRefund(refundNo, userId));
    }

    @PostMapping("/{refundNo}/approve")
    @PreAuthorize("hasRole('SELLER')")
    public Result<Void> approveRefund(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable String refundNo,
                                      @RequestBody(required = false) Map<String, String> body) {
        String remark = body != null ? body.get("remark") : null;
        refundService.approveRefund(refundNo, principal.getUserId(), remark);
        return Result.success();
    }

    @PostMapping("/{refundNo}/reject")
    @PreAuthorize("hasRole('SELLER')")
    public Result<Void> rejectRefund(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable String refundNo,
                                     @RequestBody(required = false) Map<String, String> body) {
        String remark = body != null ? body.get("remark") : null;
        refundService.rejectRefund(refundNo, principal.getUserId(), remark);
        return Result.success();
    }

    @GetMapping("/order/{orderNo}")
    public Result<List<RefundRecord>> getRefundsByOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable String orderNo) {
        Long userId = principal.getRoles().contains("ADMIN") ? null : principal.getUserId();
        return Result.success(refundService.getRefundsByOrderNo(orderNo, userId));
    }
}
