package com.campus.trade.controller;
import com.campus.trade.common.result.PageResult;
import com.campus.trade.common.result.Result;
import com.campus.trade.common.util.SecurityUtil;
import com.campus.trade.dto.response.SettlementResponse;
import com.campus.trade.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@Tag(name="Settlement") @RestController @RequestMapping("/api/v1/settlements") @RequiredArgsConstructor
public class SettlementController {
    private final SettlementService settlementService;
    @PostMapping("/order/{orderId}") public Result<SettlementResponse> create(@PathVariable Long orderId) { return Result.ok(settlementService.createSettlement(orderId)); }
    @PostMapping("/{id}/execute") public Result<Void> execute(@PathVariable Long id) { settlementService.executeSettlement(id); return Result.ok(); }
    @GetMapping("/{id}") public Result<SettlementResponse> get(@PathVariable Long id) { return Result.ok(settlementService.getSettlement(id)); }
    @GetMapping("/seller") public Result<PageResult<SettlementResponse>> my(@RequestParam(required=false) String status, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) { return Result.ok(settlementService.listSettlements(SecurityUtil.currentUserId(), status, page, size)); }
}
