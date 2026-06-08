package com.campus.trade.service;
import com.campus.trade.dto.response.PaymentResponse;
public interface PaymentService { PaymentResponse createPayment(Long buyerId, String orderNo); String handleAlipayNotify(java.util.Map<String,String> params); String simulatePayNotify(String orderNo, String tradeNo); }
