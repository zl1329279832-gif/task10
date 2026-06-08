package com.campus.trade.controller;

import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.model.dto.request.ArbitrateRequest;
import com.campus.trade.model.dto.request.DisputeRequest;
import com.campus.trade.model.entity.Dispute;
import com.campus.trade.security.UserPrincipal;
import com.campus.trade.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public Result<Map<String, String>> createDispute(@AuthenticationPrincipal UserPrincipal principal,
                                                      @Valid @RequestBody DisputeRequest request) {
        String disputeNo = disputeService.createDispute(principal.getUserId(), request);
        return Result.success(Map.of("disputeNo", disputeNo));
    }

    @GetMapping("/{disputeNo}")
    public Result<Dispute> getDispute(@PathVariable String disputeNo) {
        return Result.success(disputeService.getDispute(disputeNo));
    }

    @PostMapping("/{disputeNo}/evidence")
    public Result<Void> submitEvidence(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable String disputeNo,
                                       @RequestBody Map<String, String> body) {
        disputeService.submitEvidence(disputeNo, principal.getUserId(), body.get("evidence"));
        return Result.success();
    }

    @PostMapping("/{disputeNo}/arbitrate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> arbitrate(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable String disputeNo,
                                  @Valid @RequestBody ArbitrateRequest request) {
        disputeService.arbitrate(disputeNo, principal.getUserId(), request);
        return Result.success();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<Dispute>> listDisputes(@RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        return Result.success(disputeService.listDisputes(status, page, size));
    }
}
