package com.campus.trade.service.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.campus.trade.common.config.AlipayConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.common.util.DistributedLock;
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
    private final DistributedLock distributedLock;

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

        // Fast-path: already processed — just acknowledge
        if (PaymentStatus.SUCCESS.name().equals(payment.getStatus())) {
            log.info("Dup callback for SUCCESS payment: {}", outTradeNo);
            return "success";
        }
        if (PaymentStatus.CLOSED.name().equals(payment.getStatus())) {
            log.warn("Callback for CLOSED payment: {}", outTradeNo);
            return "success";
        }

        // Only record callback details for payments that are still actionable
        paymentMapper.incrementNotifyCount(payment.getId());
        paymentMapper.updateCallbackContent(payment.getId(), params.toString());

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
        String lockKey = "order:" + payment.getOrderId();
        if (!distributedLock.tryLock(lockKey)) {
            log.warn("Lock contention on order:{}, callback will be retried by Alipay", payment.getOrderId());
            return "failure";
        }
        try {
            // ── Re-read payment inside lock to avoid stale snapshot ──
            Payment fresh = paymentMapper.findById(payment.getId());
            if (fresh == null) {
                log.error("Payment vanished inside lock: {}", payment.getPaymentNo());
                return "success";
            }

            // Already processed (duplicate / concurrent callback won the race)
            if (PaymentStatus.SUCCESS.name().equals(fresh.getStatus())) {
                log.info("Duplicate callback (already SUCCESS): {}", payment.getPaymentNo());
                return "success";
            }
            if (!PaymentStatus.PENDING.name().equals(fresh.getStatus())) {
                log.warn("Payment not PENDING inside lock: status={}", fresh.getStatus());
                return "success";
            }

            // ── 1. Order must exist and be in CREATED state ──
            Order order = orderMapper.findById(payment.getOrderId());
            if (order == null || !OrderStatus.CREATED.name().equals(order.getStatus())) {
                String actualStatus = order != null ? order.getStatus() : "NULL";
                log.warn("Late callback rejected — order status={}, paymentNo={}", actualStatus, payment.getPaymentNo());
                auditService.log(null, null, "PAYMENT", "CALLBACK_REJECTED_ORDER_NOT_CREATED",
                        "ORDER", payment.getOrderId(),
                        "orderStatus=" + actualStatus + ",tradeNo=" + tradeNo);
                return "success";
            }

            // ── 2. Amount must match exactly ──
            if (!payment.getAmount().toPlainString().equals(totalAmount)) {
                log.error("Amount mismatch: expected={}, actual={}, paymentNo={}",
                        payment.getAmount(), totalAmount, payment.getPaymentNo());
                auditService.log(null, null, "PAYMENT", "AMOUNT_MISMATCH",
                        "PAYMENT", payment.getId(),
                        "expected=" + payment.getAmount() + ",actual=" + totalAmount);
                return "success";
            }

            // ── 3. trade_no must not be already claimed by another payment ──
            if (tradeNo != null) {
                Payment existingByTrade = paymentMapper.findByTradeNo(tradeNo);
                if (existingByTrade != null && !existingByTrade.getId().equals(payment.getId())) {
                    log.error("trade_no={} already used by paymentId={}, rejecting for paymentId={}",
                            tradeNo, existingByTrade.getId(), payment.getId());
                    auditService.log(null, null, "PAYMENT", "TRADE_NO_DUPLICATE",
                            "PAYMENT", payment.getId(),
                            "tradeNo=" + tradeNo + ",existingPaymentId=" + existingByTrade.getId());
                    return "success";
                }
            }

            // ── All guards passed — safe to transition ──

            // 4. Optimistic-lock payment PENDING → SUCCESS
            if (paymentMapper.updateStatus(payment.getId(), "PENDING", "SUCCESS") == 0) {
                log.info("Payment CAS failed (already processed): {}", payment.getPaymentNo());
                return "success";
            }

            // 5. Record trade_no and paidAt
            LocalDateTime now = LocalDateTime.now();
            paymentMapper.updateTradeNo(payment.getId(), tradeNo, now);

            // 6. Transition order CREATED → PAID (lock-free, caller holds the lock)
            try {
                orderService.transitionToPaidInternal(order.getId(), "Payment callback received");
            } catch (BizException e) {
                // Order transition failed (concurrent cancel won the DB race) —
                // payment is already SUCCESS; this inconsistency needs manual reconciliation.
                log.error("Order transition to PAID failed after payment SUCCESS: orderId={}, error={}",
                        order.getId(), e.getMessage());
                auditService.log(null, null, "PAYMENT", "ORDER_TRANSITION_FAILED",
                        "ORDER", order.getId(),
                        "paymentStatus=SUCCESS,orderTransitionError=" + e.getMessage());
                return "success";
            }

            // 7. Record pay time on order
            orderMapper.updatePayInfo(order.getId(), now);

            // 8. Deduct inventory (stock was locked at order creation)
            inventoryService.deductStock(order.getSkuId(), order.getQuantity());

            // 9. Audit trail
            auditService.log(null, null, "PAYMENT", "SUCCESS", "ORDER", order.getId(),
                    "tradeNo=" + tradeNo);
            log.info("Payment success: orderNo={}, tradeNo={}", order.getOrderNo(), tradeNo);
            return "success";

        } catch (BizException e) {
            // Any expected business error — audit it and acknowledge callback
            log.warn("BizException in processSuccess: paymentNo={}, error={}",
                    payment.getPaymentNo(), e.getMessage());
            auditService.log(null, null, "PAYMENT", "CALLBACK_BIZ_ERROR",
                    "PAYMENT", payment.getId(), "error=" + e.getMessage());
            return "success";
        } finally {
            distributedLock.unlock(lockKey);
        }
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
