package com.campus.trade.controller;

import com.campus.trade.common.Result;
import com.campus.trade.model.dto.response.PaymentResponse;
import com.campus.trade.security.UserPrincipal;
import com.campus.trade.service.PaymentService;
import com.campus.trade.util.AlipayUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/pay/{orderNo}")
    @PreAuthorize("hasRole('BUYER')")
    public Result<PaymentResponse> initiatePayment(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable String orderNo) {
        PaymentResponse response = paymentService.initiatePayment(orderNo, principal.getUserId());
        return Result.success(response);
    }

    @PostMapping("/callback/alipay")
    public String alipayCallback(HttpServletRequest request) {
        Map<String, String> params = AlipayUtil.parseParams(request.getParameterMap());
        return paymentService.handleAlipayCallback(params);
    }

    @GetMapping("/return/alipay")
    public Result<String> alipayReturn(@RequestParam("out_trade_no") String orderNo) {
        return Result.success("支付完成，订单号: " + orderNo);
    }

    @GetMapping("/status/{orderNo}")
    @PreAuthorize("hasRole('BUYER')")
    public Result<String> getPaymentStatus(@PathVariable String orderNo) {
        return Result.success(paymentService.getPaymentStatus(orderNo));
    }
}
