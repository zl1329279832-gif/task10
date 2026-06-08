package com.campus.trade.controller;
import com.campus.trade.common.result.PageResult;
import com.campus.trade.common.result.Result;
import com.campus.trade.common.util.SecurityUtil;
import com.campus.trade.dto.request.DisputeRequest;
import com.campus.trade.dto.request.EvidenceRequest;
import com.campus.trade.dto.response.DisputeResponse;
import com.campus.trade.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@Tag(name="Dispute") @RestController @RequestMapping("/api/v1/disputes") @RequiredArgsConstructor
public class DisputeController {
    private final DisputeService disputeService;
    @PostMapping public Result<DisputeResponse> create(@Valid @RequestBody DisputeRequest r) { return Result.ok(disputeService.createDispute(SecurityUtil.currentUserId(), r)); }
    @GetMapping("/{id}") public Result<DisputeResponse> get(@PathVariable Long id) { return Result.ok(disputeService.getDispute(id)); }
    @PostMapping("/evidence") public Result<Void> evidence(@Valid @RequestBody EvidenceRequest r) { disputeService.submitEvidence(SecurityUtil.currentUserId(), r); return Result.ok(); }
    @PostMapping("/{id}/escalate") public Result<Void> escalate(@PathVariable Long id) { disputeService.escalateToArbitration(id); return Result.ok(); }
    @GetMapping public Result<PageResult<DisputeResponse>> list(@RequestParam(required=false) String status, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) { return Result.ok(disputeService.listDisputes(status, page, size)); }
}
