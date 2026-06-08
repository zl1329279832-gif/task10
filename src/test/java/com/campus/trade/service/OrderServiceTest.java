package com.campus.trade.service;

import com.campus.trade.common.BusinessException;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.ProductMapper;
import com.campus.trade.mapper.UserMapper;
import com.campus.trade.model.dto.request.OrderCreateRequest;
import com.campus.trade.model.dto.response.OrderResponse;
import com.campus.trade.model.entity.Order;
import com.campus.trade.model.entity.Product;
import com.campus.trade.model.entity.User;
import com.campus.trade.model.enums.OrderStatus;
import com.campus.trade.service.impl.OrderServiceImpl;
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
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private ProductMapper productMapper;
    @Mock private UserMapper userMapper;
    @Mock private InventoryService inventoryService;
    @Mock private SettlementService settlementService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setSellerId(2L);
        testProduct.setTitle("测试商品");
        testProduct.setPrice(new BigDecimal("99.00"));
        testProduct.setStock(5);
        testProduct.setStatus(1);
        testProduct.setVersion(0);

        User buyer = new User();
        buyer.setId(1L);
        buyer.setUsername("buyer1");
        lenient().when(userMapper.selectById(1L)).thenReturn(buyer);

        User seller = new User();
        seller.setId(2L);
        seller.setUsername("seller1");
        lenient().when(userMapper.selectById(2L)).thenReturn(seller);
    }

    @Test
    @DisplayName("创建订单成功")
    void testCreateOrderSuccess() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setProductId(1L);
        request.setQuantity(1);

        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(productMapper.selectById(1L)).thenReturn(testProduct);
        when(inventoryService.lockStock(1L, 1)).thenReturn(true);
        when(productMapper.deductStock(eq(1L), eq(1), eq(0))).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        OrderResponse response = orderService.createOrder(1L, request, "key-001");
        assertNotNull(response);
        assertEquals(OrderStatus.PENDING_PAYMENT.name(), response.getStatus());
        assertEquals(new BigDecimal("99.00"), response.getTotalAmount());
    }

    @Test
    @DisplayName("不能购买自己的商品")
    void testCannotBuyOwnProduct() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setProductId(1L);
        request.setQuantity(1);

        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(productMapper.selectById(1L)).thenReturn(testProduct);

        assertThrows(BusinessException.class, () -> orderService.createOrder(2L, request, "key-002"));
    }

    @Test
    @DisplayName("库存不足创建订单失败")
    void testCreateOrderStockInsufficient() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setProductId(1L);
        request.setQuantity(1);

        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(productMapper.selectById(1L)).thenReturn(testProduct);
        when(inventoryService.lockStock(1L, 1)).thenReturn(false);

        assertThrows(BusinessException.class, () -> orderService.createOrder(1L, request, "key-003"));
    }

    @Test
    @DisplayName("商品已下架不能购买")
    void testCreateOrderProductOffShelf() {
        testProduct.setStatus(0);
        OrderCreateRequest request = new OrderCreateRequest();
        request.setProductId(1L);
        request.setQuantity(1);

        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(productMapper.selectById(1L)).thenReturn(testProduct);

        assertThrows(BusinessException.class, () -> orderService.createOrder(1L, request, "key-004"));
    }

    @Test
    @DisplayName("取消订单释放库存")
    void testCancelOrder() {
        Order order = new Order();
        order.setOrderNo("ORD001");
        order.setBuyerId(1L);
        order.setSellerId(2L);
        order.setProductId(1L);
        order.setQuantity(2);
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setVersion(0);

        when(orderMapper.selectByOrderNo("ORD001")).thenReturn(order);
        when(orderMapper.updateClosed(eq("ORD001"), eq(OrderStatus.CLOSED.name()), eq("买家取消"), eq(0)))
                .thenReturn(1);

        orderService.cancelOrder("ORD001", 1L);

        verify(inventoryService).releaseStock(1L, 2);
        verify(productMapper).restoreStock(1L, 2);
    }

    @Test
    @DisplayName("非买家不能取消订单")
    void testCancelOrderNotBuyer() {
        Order order = new Order();
        order.setOrderNo("ORD001");
        order.setBuyerId(1L);
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());

        when(orderMapper.selectByOrderNo("ORD001")).thenReturn(order);

        assertThrows(BusinessException.class, () -> orderService.cancelOrder("ORD001", 999L));
    }

    @Test
    @DisplayName("超时订单自动关闭")
    void testHandleTimeoutOrders() {
        Order order = new Order();
        order.setOrderNo("ORD_TIMEOUT");
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setProductId(1L);
        order.setQuantity(1);
        order.setVersion(0);

        when(orderMapper.selectTimeoutOrders()).thenReturn(java.util.List.of(order));
        when(orderMapper.updateClosed(eq("ORD_TIMEOUT"), eq(OrderStatus.CLOSED.name()),
                eq("支付超时自动关闭"), eq(0))).thenReturn(1);

        orderService.handleTimeoutOrders();

        verify(inventoryService).releaseStock(1L, 1);
        verify(productMapper).restoreStock(1L, 1);
    }
}
