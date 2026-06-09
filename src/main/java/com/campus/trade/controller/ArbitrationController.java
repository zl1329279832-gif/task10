package com.campus.trade.controller;
import com.campus.trade.common.result.Result;
import com.campus.trade.common.util.SecurityUtil;
import com.campus.trade.domain.entity.Arbitration;
import com.campus.trade.dto.request.ArbitrationRequest;
import com.campus.trade.service.ArbitrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@Tag(name="Arbitration") @RestController @RequestMapping("/api/v1/admin/arbitration") @PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class ArbitrationController {
    private final ArbitrationService arbitrationService;
    @PostMapping public Result<Arbitration> arbitrate(@Valid @RequestBody ArbitrationRequest r) { return Result.ok(arbitrationService.arbitrate(SecurityUtil.currentUserId(), r)); }
    @PostMapping("/reverse") @Operation(summary = "Reverse arbitration ruling") public Result<Arbitration> reverse(@Valid @RequestBody ArbitrationRequest r) { return Result.ok(arbitrationService.reverseArbitration(SecurityUtil.currentUserId(), r)); }
    @GetMapping("/dispute/{disputeId}") @Operation(summary = "Get arbitration by dispute ID") public Result<Arbitration> getByDispute(@PathVariable Long disputeId) { return Result.ok(arbitrationService.getByDisputeId(disputeId)); }
}