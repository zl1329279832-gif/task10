package com.campus.trade.controller;
import com.campus.trade.common.result.Result;
import com.campus.trade.common.util.SecurityUtil;
import com.campus.trade.dto.response.SettlementResponse;
import com.campus.trade.service.EscrowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@Tag(name="Escrow") @RestController @RequestMapping("/api/v1/admin/escrow") @PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class EscrowController {
    private final EscrowService escrowService;
    @PostMapping("/freeze/{orderId}") @Operation(summary = "Freeze escrow funds for an order") public Result<Void> freeze(@PathVariable Long orderId, @RequestParam String reason) { escrowService.freezeFunds(orderId, SecurityUtil.currentUserId(), reason); return Result.ok(); }
    @PostMapping("/unfreeze/{orderId}") @Operation(summary = "Unfreeze escrow funds for an order") public Result<Void> unfreeze(@PathVariable Long orderId, @RequestParam String reason) { escrowService.unfreezeFunds(orderId, SecurityUtil.currentUserId(), reason); return Result.ok(); }
    @PostMapping("/settlement/{settlementId}/retry") @Operation(summary = "Retry a failed settlement") public Result<SettlementResponse> retrySettlement(@PathVariable Long settlementId) { return Result.ok(escrowService.retrySettlement(settlementId, SecurityUtil.currentUserId())); }
}