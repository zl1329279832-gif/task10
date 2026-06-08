package com.campus.trade.service;

import com.campus.trade.common.BusinessException;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.ProductMapper;
import com.campus.trade.mapper.RefundRecordMapper;
import com.campus.trade.model.dto.request.RefundRequest;
import com.campus.trade.model.entity.Order;
import com.campus.trade.model.entity.RefundRecord;
import com.campus.trade.model.enums.OrderStatus;
import com.campus.trade.model.enums.RefundStatus;
import com.campus.trade.service.impl.RefundServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private RefundRecordMapper refundRecordMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private ProductMapper productMapper;
    @Mock private InventoryService inventoryService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private RefundServiceImpl refundService;

    private Order buildOrder(String status) {
        Order order = new Order();
        order.setOrderNo("ORD001");
        order.setBuyerId(1L);
        order.setSellerId(2L);
        order.setProductId(1L);
        order.setQuantity(1);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(status);
        order.setVersion(0);
        return order;
    }

    @Test
    @DisplayName("已支付订单申请退款成功")
    void testApplyRefundSuccess() {
        RefundRequest request = new RefundRequest();
        request.setOrderNo("ORD001");
        request.setAmount(new BigDecimal("100.00"));
        request.setReason("商品描述不符");

        when(orderMapper.selectByOrderNo("ORD001")).thenReturn(buildOrder(OrderStatus.PAID.name()));
        when(orderMapper.updateStatusWithVersion(eq("ORD001"), eq(OrderStatus.REFUND_REQUESTED.name()), eq(0)))
                .thenReturn(1);
        when(refundRecordMapper.insert(any())).thenReturn(1);

        String refundNo = refundService.applyRefund(1L, request);
        assertNotNull(refundNo);
        assertTrue(refundNo.startsWith("REF"));
    }

    @Test
    @DisplayName("已完成订单不能退款")
    void testApplyRefundCompletedOrder() {
        RefundRequest request = new RefundRequest();
        request.setOrderNo("ORD001");
        request.setAmount(new BigDecimal("100.00"));
        request.setReason("test");

        when(orderMapper.selectByOrderNo("ORD001")).thenReturn(buildOrder(OrderStatus.COMPLETED.name()));

        assertThrows(BusinessException.class, () -> refundService.applyRefund(1L, request));
    }

    @Test
    @DisplayName("退款金额超过订单金额被拒")
    void testApplyRefundAmountExceeded() {
        RefundRequest request = new RefundRequest();
        request.setOrderNo("ORD001");
        request.setAmount(new BigDecimal("200.00")); // exceeds 100.00
        request.setReason("test");

        when(orderMapper.selectByOrderNo("ORD001")).thenReturn(buildOrder(OrderStatus.PAID.name()));

        assertThrows(BusinessException.class, () -> refundService.applyRefund(1L, request));
    }

    @Test
    @DisplayName("卖家同意退款恢复库存")
    void testApproveRefund() {
        RefundRecord record = new RefundRecord();
        record.setRefundNo("REF001");
        record.setOrderNo("ORD001");
        record.setSellerId(2L);
        record.setStatus(RefundStatus.PENDING.name());

        Order order = buildOrder(OrderStatus.REFUND_REQUESTED.name());

        when(refundRecordMapper.selectByRefundNo("REF001")).thenReturn(record);
        when(orderMapper.selectByOrderNo("ORD001")).thenReturn(order);
        when(orderMapper.updateStatusWithVersion(any(), any(), anyInt())).thenReturn(1);

        refundService.approveRefund("REF001", 2L, "同意退款");

        verify(inventoryService).releaseStock(1L, 1);
        verify(productMapper).restoreStock(1L, 1);
        verify(refundRecordMapper).updateHandle("REF001", RefundStatus.APPROVED.name(), 2L, "同意退款");
    }

    @Test
    @DisplayName("非卖家不能审批退款")
    void testApproveRefundNotSeller() {
        RefundRecord record = new RefundRecord();
        record.setRefundNo("REF001");
        record.setSellerId(2L);
        record.setStatus(RefundStatus.PENDING.name());

        when(refundRecordMapper.selectByRefundNo("REF001")).thenReturn(record);

        assertThrows(BusinessException.class, () -> refundService.approveRefund("REF001", 999L, "ok"));
    }

    @Test
    @DisplayName("已处理退款不能重复处理")
    void testApproveRefundAlreadyHandled() {
        RefundRecord record = new RefundRecord();
        record.setRefundNo("REF001");
        record.setSellerId(2L);
        record.setStatus(RefundStatus.APPROVED.name()); // Already approved

        when(refundRecordMapper.selectByRefundNo("REF001")).thenReturn(record);

        assertThrows(BusinessException.class, () -> refundService.approveRefund("REF001", 2L, "ok"));
    }
}
