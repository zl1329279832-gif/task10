package com.campus.trade.service;

import com.campus.trade.model.dto.response.PaymentResponse;
import java.util.Map;

public interface PaymentService {
    PaymentResponse initiatePayment(String orderNo, Long buyerId);
    String handleAlipayCallback(Map<String, String> params);
    String getPaymentStatus(String orderNo);
}
