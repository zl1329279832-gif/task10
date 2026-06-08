package com.campus.trade.service;

import com.campus.trade.config.AlipayConfig;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.PaymentRecordMapper;
import com.campus.trade.model.entity.Order;
import com.campus.trade.model.entity.PaymentRecord;
import com.campus.trade.model.enums.OrderStatus;
import com.campus.trade.model.enums.PaymentStatus;
import com.campus.trade.service.impl.PaymentServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private PaymentRecordMapper paymentRecordMapper;
    @Mock private AlipayConfig alipayConfig;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(alipayConfig.getAlipayPublicKey()).thenReturn("test-public-key");
        // Use reflection to set objectMapper
        try {
            var field = PaymentServiceImpl.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(paymentService, objectMapper);
        } catch (Exception ignored) {}
    }

    private Map<String, String> buildCallbackParams(String orderNo, String tradeNo, String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", orderNo);
        params.put("trade_no", tradeNo);
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", amount);
        params.put("sign", "mock_sign_for_sandbox");
        params.put("sign_type", "RSA2");
        return params;
    }

    private Order buildOrder(String orderNo, String status, BigDecimal amount) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setTotalAmount(amount);
        order.setBuyerId(1L);
        order.setSellerId(2L);
        order.setProductId(1L);
        order.setVersion(0);
        return order;
    }

    @Test
    @DisplayName("正常支付回调处理成功")
    void testHandleCallbackSuccess() {
        String orderNo = "ORD001";
        String tradeNo = "T001";
        Map<String, String> params = buildCallbackParams(orderNo, tradeNo, "100.00");

        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(paymentRecordMapper.selectByTradeNo(tradeNo)).thenReturn(null);
        when(orderMapper.selectByOrderNo(orderNo)).thenReturn(
                buildOrder(orderNo, OrderStatus.PENDING_PAYMENT.name(), new BigDecimal("100.00")));
        when(orderMapper.updatePaidAt(eq(orderNo), eq(OrderStatus.PAID.name()), eq(0))).thenReturn(1);
        when(paymentRecordMapper.insert(any())).thenReturn(1);

        String result = paymentService.handleAlipayCallback(params);
        assertEquals("success", result);
        verify(orderMapper).updatePaidAt(eq(orderNo), eq(OrderStatus.PAID.name()), eq(0));
    }

    @Test
    @DisplayName("重复回调幂等处理")
    void testDuplicateCallbackIdempotent() {
        String orderNo = "ORD001";
        String tradeNo = "T001";
        Map<String, String> params = buildCallbackParams(orderNo, tradeNo, "100.00");

        PaymentRecord existing = new PaymentRecord();
        existing.setStatus(PaymentStatus.SUCCESS.name());

        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(paymentRecordMapper.selectByTradeNo(tradeNo)).thenReturn(existing);

        String result = paymentService.handleAlipayCallback(params);
        assertEquals("success", result);
        verify(orderMapper, never()).updatePaidAt(any(), any(), anyInt());
    }

    @Test
    @DisplayName("订单关闭后收到回调")
    void testCallbackAfterOrderClosed() {
        String orderNo = "ORD001";
        String tradeNo = "T001";
        Map<String, String> params = buildCallbackParams(orderNo, tradeNo, "100.00");

        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(paymentRecordMapper.selectByTradeNo(tradeNo)).thenReturn(null);
        when(orderMapper.selectByOrderNo(orderNo)).thenReturn(
                buildOrder(orderNo, OrderStatus.CLOSED.name(), new BigDecimal("100.00")));

        String result = paymentService.handleAlipayCallback(params);
        assertEquals("success", result);
        verify(orderMapper, never()).updatePaidAt(any(), any(), anyInt());
        verify(paymentRecordMapper).insert(any());
        verify(auditLogService).log(any(), any(), eq("PAYMENT"), eq("CALLBACK_AFTER_CLOSE"),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("验签失败拒绝回调")
    void testInvalidSignature() {
        Map<String, String> params = new HashMap<>();
        params.put("sign", "invalid_sign");
        params.put("out_trade_no", "ORD001");

        String result = paymentService.handleAlipayCallback(params);
        assertEquals("failure", result);
    }

    @Test
    @DisplayName("订单已支付时忽略重复的TRADE_SUCCESS回调")
    void testCallbackWhenAlreadyPaid() {
        String orderNo = "ORD001";
        String tradeNo = "T002";
        Map<String, String> params = buildCallbackParams(orderNo, tradeNo, "100.00");

        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(paymentRecordMapper.selectByTradeNo(tradeNo)).thenReturn(null);
        when(orderMapper.selectByOrderNo(orderNo)).thenReturn(
                buildOrder(orderNo, OrderStatus.PAID.name(), new BigDecimal("100.00")));

        String result = paymentService.handleAlipayCallback(params);
        assertEquals("success", result);
        verify(orderMapper, never()).updatePaidAt(any(), any(), anyInt());
    }
}
