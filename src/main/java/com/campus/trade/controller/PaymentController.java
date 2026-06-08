package com.campus.trade.controller;
import com.campus.trade.common.result.Result;
import com.campus.trade.common.util.SecurityUtil;
import com.campus.trade.dto.response.PaymentResponse;
import com.campus.trade.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@Slf4j @Tag(name="Payment") @RestController @RequestMapping("/api/v1/pay") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    @PostMapping("/{orderNo}") public Result<PaymentResponse> create(@PathVariable String orderNo) { return Result.ok(paymentService.createPayment(SecurityUtil.currentUserId(), orderNo)); }
    @PostMapping("/notify") public String notify(@RequestParam Map<String,String> params) { log.info("Notify: {}", params); return paymentService.handleAlipayNotify(params); }
    @PostMapping("/simulate/{orderNo}") public Result<String> simulate(@PathVariable String orderNo, @RequestParam(required=false) String tradeNo) { return Result.ok(paymentService.simulatePayNotify(orderNo, tradeNo)); }
}
