package com.campus.trade.service.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.campus.trade.common.config.AlipayConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.entity.Payment;
import com.campus.trade.domain.enums.OrderStatus;
import com.campus.trade.domain.enums.PaymentStatus;
import com.campus.trade.dto.response.PaymentResponse;
import com.campus.trade.common.util.BizNoGenerator;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.PaymentMapper;
import com.campus.trade.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final AlipayConfig alipayConfig;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    @Override
    @Transactional
    public PaymentResponse createPayment(Long buyerId, String orderNo) {
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        if (!order.getBuyerId().equals(buyerId)) throw new BizException(ErrorCode.ORDER_NOT_BUYER);
        if (!OrderStatus.CREATED.name().equals(order.getStatus()))
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
        if (order.getPayExpireAt().isBefore(LocalDateTime.now()))
            throw new BizException(ErrorCode.ORDER_EXPIRED);

        Payment existing = paymentMapper.findByOrderNo(orderNo);
        if (existing != null && PaymentStatus.SUCCESS.name().equals(existing.getStatus()))
            throw new BizException(ErrorCode.ORDER_ALREADY_PAID);

        Payment payment;
        if (existing != null) {
            payment = existing;
        } else {
            payment = new Payment();
            payment.setPaymentNo(BizNoGenerator.paymentNo());
            payment.setOrderId(order.getId());
            payment.setOrderNo(orderNo);
            payment.setAmount(order.getTotalAmount());
            payment.setPayChannel("ALIPAY");
            payment.setStatus(PaymentStatus.PENDING.name());
            paymentMapper.insert(payment);
        }

        String payForm = buildAlipayForm(payment, order);
        PaymentResponse r = new PaymentResponse();
        r.setPaymentNo(payment.getPaymentNo());
        r.setOrderNo(orderNo);
        r.setPayForm(payForm);
        r.setStatus(payment.getStatus());
        return r;
    }

    @Override
    @Transactional
    public String handleAlipayNotify(Map<String, String> params) {
        boolean signVerified;
        try {
            signVerified = AlipaySignature.rsaCheckV1(params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType());
        } catch (Exception e) {
            log.error("Sign verify error", e);
            return "failure";
        }
        if (!signVerified) {
            log.warn("Sign verify failed");
            return "failure";
        }

        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String tradeStatus = params.get("trade_status");
        String totalAmount = params.get("total_amount");

        Payment payment = paymentMapper.findByPaymentNo(outTradeNo);
        if (payment == null) {
            log.warn("Payment not found: {}", outTradeNo);
            return "failure";
        }

        paymentMapper.incrementNotifyCount(payment.getId());
        paymentMapper.updateCallbackContent(payment.getId(), params.toString());

        if (PaymentStatus.SUCCESS.name().equals(payment.getStatus())) {
            log.info("Dup callback: {}", outTradeNo);
            return "success";
        }
        if (PaymentStatus.CLOSED.name().equals(payment.getStatus())) {
            log.warn("Callback for CLOSED: {}", outTradeNo);
            return "success";
        }

        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus))
            return processSuccess(payment, tradeNo, totalAmount);
        if ("TRADE_CLOSED".equals(tradeStatus)) {
            paymentMapper.updateStatus(payment.getId(), "PENDING", "CLOSED");
            return "success";
        }
        return "success";
    }

    @Override
    @Transactional
    public String simulatePayNotify(String orderNo, String tradeNo) {
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        Payment payment = paymentMapper.findByOrderNo(orderNo);
        if (payment == null) throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        return processSuccess(payment,
                tradeNo != null ? tradeNo : "SIM_" + System.currentTimeMillis(),
                order.getTotalAmount().toPlainString());
    }

    private String processSuccess(Payment payment, String tradeNo, String totalAmount) {
        if (!payment.getAmount().toPlainString().equals(totalAmount)) {
            log.error("Amount mismatch: expected={},actual={}", payment.getAmount(), totalAmount);
            auditService.log(null, null, "PAYMENT", "AMOUNT_MISMATCH", "PAYMENT", payment.getId(),
                    "expected=" + payment.getAmount() + ",actual=" + totalAmount);
        }
        if (paymentMapper.updateStatus(payment.getId(), "PENDING", "SUCCESS") == 0) {
            log.info("Already processed: {}", payment.getPaymentNo());
            return "success";
        }

        LocalDateTime now = LocalDateTime.now();
        paymentMapper.updateTradeNo(payment.getId(), tradeNo, now);

        Order order = orderMapper.findById(payment.getOrderId());
        if (order == null || !OrderStatus.CREATED.name().equals(order.getStatus())) {
            log.warn("Order not in CREATED when callback: {}", order != null ? order.getStatus() : "null");
            return "success";
        }

        orderService.transitionOrder(order.getId(), OrderStatus.PAID.name(), null, "Payment received");
        orderMapper.updatePayInfo(order.getId(), now);
        inventoryService.deductStock(order.getSkuId(), order.getQuantity());
        auditService.log(null, null, "PAYMENT", "SUCCESS", "ORDER", order.getId(), "tradeNo=" + tradeNo);
        log.info("Payment success: orderNo={}, tradeNo={}", order.getOrderNo(), tradeNo);
        return "success";
    }

    private String buildAlipayForm(Payment payment, Order order) {
        try {
            AlipayClient client = new DefaultAlipayClient(
                    alipayConfig.getServerUrl(), alipayConfig.getAppId(), alipayConfig.getPrivateKey(),
                    "json", alipayConfig.getCharset(), alipayConfig.getAlipayPublicKey(), alipayConfig.getSignType());
            AlipayTradePagePayRequest req = new AlipayTradePagePayRequest();
            req.setNotifyUrl(alipayConfig.getNotifyUrl());
            req.setReturnUrl(alipayConfig.getReturnUrl());
            req.setBizContent("{\"out_trade_no\":\"" + payment.getPaymentNo()
                    + "\",\"total_amount\":\"" + payment.getAmount()
                    + "\",\"subject\":\"" + order.getOrderNo()
                    + "\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");
            return client.pageExecute(req).getBody();
        } catch (Exception e) {
            log.error("Alipay form error", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Alipay form failed");
        }
    }
}
