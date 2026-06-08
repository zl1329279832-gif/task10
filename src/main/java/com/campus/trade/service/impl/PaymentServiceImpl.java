package com.campus.trade.service.impl;

import com.campus.trade.common.BusinessException;
import com.campus.trade.common.ResultCode;
import com.campus.trade.config.AlipayConfig;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.PaymentRecordMapper;
import com.campus.trade.model.dto.response.PaymentResponse;
import com.campus.trade.model.entity.Order;
import com.campus.trade.model.entity.PaymentRecord;
import com.campus.trade.model.enums.OrderStatus;
import com.campus.trade.model.enums.PaymentStatus;
import com.campus.trade.service.AuditLogService;
import com.campus.trade.service.PaymentService;
import com.campus.trade.statemachine.OrderEvent;
import com.campus.trade.statemachine.OrderStateMachine;
import com.campus.trade.util.AlipayUtil;
import com.campus.trade.util.RedisKeyConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderMapper orderMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final AlipayConfig alipayConfig;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Override
    public PaymentResponse initiatePayment(String orderNo, Long buyerId) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_PARTICIPANT);
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "订单状态不是待支付");
        }

        // Check if payment deadline has passed
        if (order.getPaymentDeadline() != null && LocalDateTime.now().isAfter(order.getPaymentDeadline())) {
            throw new BusinessException(ResultCode.ORDER_TIMEOUT);
        }

        // Create pending payment record
        PaymentRecord record = new PaymentRecord();
        record.setOrderNo(orderNo);
        record.setAmount(order.getTotalAmount());
        record.setStatus(PaymentStatus.PENDING.name());
        record.setPayChannel("ALIPAY");
        paymentRecordMapper.insert(record);

        // In sandbox, construct a simulated pay URL
        String payUrl = String.format("%s?app_id=%s&out_trade_no=%s&total_amount=%s&subject=%s",
                alipayConfig.getGateway(), alipayConfig.getAppId(),
                orderNo, order.getTotalAmount(), order.getProductTitle());

        log.info("发起支付: orderNo={}, amount={}", orderNo, order.getTotalAmount());

        return PaymentResponse.builder()
                .orderNo(orderNo)
                .amount(order.getTotalAmount())
                .payUrl(payUrl)
                .status(PaymentStatus.PENDING.name())
                .build();
    }

    @Override
    public String handleAlipayCallback(Map<String, String> params) {
        // 1. Verify signature
        if (!AlipayUtil.verifySign(params, alipayConfig.getAlipayPublicKey())) {
            log.warn("支付回调验签失败: params={}", params);
            return "failure";
        }

        String tradeNo = params.get("trade_no");
        String orderNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        String totalAmount = params.get("total_amount");

        log.info("收到支付回调: orderNo={}, tradeNo={}, tradeStatus={}", orderNo, tradeNo, tradeStatus);

        // 2. Distributed lock to prevent concurrent callback processing
        String lockKey = String.format(RedisKeyConstants.LOCK_PAY_CALLBACK, orderNo);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            log.info("支付回调正在处理中，跳过: orderNo={}", orderNo);
            return "success"; // Return success to prevent Alipay from retrying
        }

        try {
            // 3. Idempotent check: check if trade_no already processed
            PaymentRecord existingRecord = paymentRecordMapper.selectByTradeNo(tradeNo);
            if (existingRecord != null && PaymentStatus.SUCCESS.name().equals(existingRecord.getStatus())) {
                log.info("支付回调已处理(幂等): tradeNo={}", tradeNo);
                return "success";
            }

            // 4. Validate order exists
            Order order = orderMapper.selectByOrderNo(orderNo);
            if (order == null) {
                log.error("支付回调对应订单不存在: orderNo={}", orderNo);
                return "failure";
            }

            // 5. Handle callback after order closed
            if (OrderStatus.CLOSED.name().equals(order.getStatus())) {
                log.warn("订单已关闭但收到支付成功回调: orderNo={}, tradeNo={}", orderNo, tradeNo);
                saveCallbackRecord(orderNo, tradeNo, totalAmount, PaymentStatus.CLOSED_IGNORED.name(), params);
                auditLogService.log(null, null, "PAYMENT", "CALLBACK_AFTER_CLOSE",
                        "ORDER", orderNo, "订单已关闭但收到支付成功回调, tradeNo=" + tradeNo, null);
                return "success"; // Acknowledge to prevent Alipay from retrying
            }

            // 6. Handle TRADE_SUCCESS
            if ("TRADE_SUCCESS".equals(tradeStatus) && OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
                // Verify amount
                BigDecimal callbackAmount = new BigDecimal(totalAmount);
                if (callbackAmount.compareTo(order.getTotalAmount()) != 0) {
                    log.error("支付金额不匹配: orderNo={}, expected={}, actual={}", orderNo, order.getTotalAmount(), callbackAmount);
                    auditLogService.log(null, null, "PAYMENT", "AMOUNT_MISMATCH",
                            "ORDER", orderNo, "金额不匹配: 期望=" + order.getTotalAmount() + ", 实际=" + callbackAmount, null);
                    return "failure";
                }

                handlePaySuccess(order, tradeNo, params);
                return "success";
            }

            // 7. Handle other trade statuses or order statuses (e.g., order already paid - out-of-order callback)
            if (!"PENDING_PAYMENT".equals(order.getStatus())) {
                log.info("订单已不在待支付状态，忽略回调: orderNo={}, currentStatus={}, tradeStatus={}",
                        orderNo, order.getStatus(), tradeStatus);
                return "success";
            }

            log.info("非TRADE_SUCCESS回调，忽略: orderNo={}, tradeStatus={}", orderNo, tradeStatus);
            return "success";

        } catch (Exception e) {
            log.error("处理支付回调异常: orderNo={}, tradeNo={}", orderNo, tradeNo, e);
            return "failure"; // Return failure to let Alipay retry
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Transactional
    protected void handlePaySuccess(Order order, String tradeNo, Map<String, String> params) {
        // State machine transition
        OrderStatus newStatus = OrderStateMachine.transition(
                OrderStatus.valueOf(order.getStatus()), OrderEvent.PAY);

        // Update order status with optimistic lock
        int affected = orderMapper.updatePaidAt(order.getOrderNo(), newStatus.name(), order.getVersion());
        if (affected == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_CONFLICT);
        }

        // Save payment record (trade_no UNIQUE constraint ensures idempotency at DB level)
        saveCallbackRecord(order.getOrderNo(), tradeNo, params.get("total_amount"),
                PaymentStatus.SUCCESS.name(), params);

        auditLogService.log(null, null, "PAYMENT", "PAY_SUCCESS",
                "ORDER", order.getOrderNo(), "支付成功, tradeNo=" + tradeNo, null);

        log.info("支付成功: orderNo={}, tradeNo={}", order.getOrderNo(), tradeNo);
    }

    private void saveCallbackRecord(String orderNo, String tradeNo, String amount, String status,
                                     Map<String, String> params) {
        PaymentRecord record = new PaymentRecord();
        record.setOrderNo(orderNo);
        record.setTradeNo(tradeNo);
        record.setAmount(new BigDecimal(amount));
        record.setStatus(status);
        record.setPayChannel("ALIPAY");
        record.setCallbackTime(LocalDateTime.now());
        try {
            record.setCallbackRaw(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            record.setCallbackRaw(params.toString());
        }
        paymentRecordMapper.insert(record);
    }

    @Override
    public String getPaymentStatus(String orderNo) {
        PaymentRecord record = paymentRecordMapper.selectByOrderNo(orderNo);
        if (record == null) {
            return PaymentStatus.PENDING.name();
        }
        return record.getStatus();
    }
}
