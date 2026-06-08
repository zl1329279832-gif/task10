package com.campus.trade.controller;

import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.model.entity.Settlement;
import com.campus.trade.security.UserPrincipal;
import com.campus.trade.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping("/seller")
    @PreAuthorize("hasRole('SELLER')")
    public Result<PageResult<Settlement>> listSellerSettlements(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(settlementService.listSellerSettlements(principal.getUserId(), page, size));
    }

    @GetMapping("/{settlementNo}")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
    public Result<Settlement> getSettlement(@PathVariable String settlementNo) {
        return Result.success(settlementService.getSettlement(settlementNo));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<Settlement>> listAllSettlements(@RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "10") int size) {
        return Result.success(settlementService.listAllSettlements(page, size));
    }
}
