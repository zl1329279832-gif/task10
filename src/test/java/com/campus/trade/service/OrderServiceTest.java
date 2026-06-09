package com.campus.trade.service;
import com.campus.trade.service.impl.OrderServiceImpl;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.entity.Sku;
import com.campus.trade.domain.enums.OrderStatus;
import com.campus.trade.domain.enums.OrderStateTransition;
import com.campus.trade.dto.request.CreateOrderRequest;
import com.campus.trade.dto.response.OrderResponse;
import com.campus.trade.mapper.*;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.common.config.TradeConfig;
import com.campus.trade.common.exception.BizException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @InjectMocks private OrderServiceImpl orderService;
    @Mock private OrderMapper orderMapper; @Mock private OrderStatusLogMapper statusLogMapper;
    @Mock private ProductMapper productMapper; @Mock private SkuMapper skuMapper; @Mock private UserMapper userMapper;
    @Mock private InventoryService inventoryService; @Mock private AuditService auditService;
    @Mock private DistributedLock distributedLock; @Mock private TradeConfig tradeConfig;
    @Mock private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setupTransactionTemplate() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().doAnswer(invocation -> {
            Consumer<?> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Nested @DisplayName("State Machine") class SM {
        @Test void validTransitions() {
            assertThat(OrderStateTransition.isValid(OrderStatus.CREATED, OrderStatus.PAID)).isTrue();
            assertThat(OrderStateTransition.isValid(OrderStatus.CREATED, OrderStatus.CANCELLED)).isTrue();
            assertThat(OrderStateTransition.isValid(OrderStatus.PAID, OrderStatus.SHIPPED)).isTrue();
            assertThat(OrderStateTransition.isValid(OrderStatus.SHIPPED, OrderStatus.RECEIVED)).isTrue();
            assertThat(OrderStateTransition.isValid(OrderStatus.RECEIVED, OrderStatus.SETTLED)).isTrue();
        }
        @Test void invalidTransitions() {
            assertThat(OrderStateTransition.isValid(OrderStatus.CREATED, OrderStatus.SHIPPED)).isFalse();
            assertThat(OrderStateTransition.isValid(OrderStatus.CANCELLED, OrderStatus.PAID)).isFalse();
            assertThat(OrderStateTransition.isValid(OrderStatus.SETTLED, OrderStatus.REFUNDED)).isFalse();
            assertThat(OrderStateTransition.isValid(OrderStatus.PAID, OrderStatus.CANCELLED)).isFalse();
        }
        @Test void refundFlow() {
            assertThat(OrderStateTransition.isValid(OrderStatus.PAID, OrderStatus.REFUNDING)).isTrue();
            assertThat(OrderStateTransition.isValid(OrderStatus.REFUNDING, OrderStatus.REFUNDED)).isTrue();
            assertThat(OrderStateTransition.isValid(OrderStatus.REFUNDING, OrderStatus.PAID)).isTrue();
        }
        @Test void disputeFlow() {
            assertThat(OrderStateTransition.isValid(OrderStatus.PAID, OrderStatus.DISPUTED)).isTrue();
            assertThat(OrderStateTransition.isValid(OrderStatus.DISPUTED, OrderStatus.REFUNDED)).isTrue();
            assertThat(OrderStateTransition.isValid(OrderStatus.DISPUTED, OrderStatus.PAID)).isTrue();
        }
    }
    @Nested @DisplayName("Create Order") class CO {
        @Test void success() {
            Sku sku = new Sku(); sku.setId(1L); sku.setProductId(1L); sku.setSkuName("SKU1"); sku.setPrice(new BigDecimal("100")); sku.setStatus(1);
            var p = new com.campus.trade.domain.entity.Product(); p.setId(1L); p.setSellerId(2L); p.setStatus(1);
            when(skuMapper.findById(1L)).thenReturn(sku); when(productMapper.findById(1L)).thenReturn(p);
            when(tradeConfig.getPayTimeoutMinutes()).thenReturn(30);
            when(orderMapper.insert(any())).thenAnswer(i -> { ((Order)i.getArgument(0)).setId(100L); return 1; });
            when(statusLogMapper.insert(any())).thenReturn(1);
            CreateOrderRequest req = new CreateOrderRequest(); req.setSkuId(1L); req.setQuantity(1); req.setAddress("addr");
            OrderResponse r = orderService.createOrder(1L, req);
            assertThat(r.getStatus()).isEqualTo("CREATED"); assertThat(r.getTotalAmount()).isEqualByComparingTo("100");
            verify(inventoryService).lockStock(1L, 1);
        }
        @Test void selfPurchase() {
            Sku sku = new Sku(); sku.setId(1L); sku.setProductId(1L); sku.setStatus(1);
            var p = new com.campus.trade.domain.entity.Product(); p.setId(1L); p.setSellerId(2L); p.setStatus(1);
            when(skuMapper.findById(1L)).thenReturn(sku); when(productMapper.findById(1L)).thenReturn(p);
            CreateOrderRequest req = new CreateOrderRequest(); req.setSkuId(1L); req.setQuantity(1); req.setAddress("x");
            assertThatThrownBy(() -> orderService.createOrder(2L, req)).isInstanceOf(BizException.class);
        }
    }
    @Nested @DisplayName("Cancel") class Cancel {
        @Test void cancelUnpaid() {
            Order o = new Order(); o.setId(100L); o.setBuyerId(1L); o.setSkuId(1L); o.setQuantity(1); o.setStatus("CREATED");
            when(orderMapper.findById(100L)).thenReturn(o); when(distributedLock.tryLock(anyString())).thenReturn(new DistributedLock.LockHandle("test", "token"));
            when(orderMapper.updateStatus(100L, "CREATED", "CANCELLED")).thenReturn(1);
            when(orderMapper.updateCloseInfo(eq(100L), any(), anyString())).thenReturn(1);
            when(statusLogMapper.insert(any())).thenReturn(1);
            orderService.cancelOrder(1L, 100L);
            verify(inventoryService).releaseStock(1L, 1);
        }
        @Test void rejectPaidCancel() {
            Order o = new Order(); o.setId(100L); o.setBuyerId(1L); o.setStatus("PAID");
            when(distributedLock.tryLock(anyString())).thenReturn(new DistributedLock.LockHandle("test", "token"));
            when(orderMapper.findById(100L)).thenReturn(o);
            assertThatThrownBy(() -> orderService.cancelOrder(1L, 100L)).isInstanceOf(BizException.class);
        }
    }
    @Nested @DisplayName("Concurrency") class Conc {
        @Test void optimisticLock() {
            Order o = new Order(); o.setId(100L); o.setBuyerId(1L); o.setSkuId(1L); o.setQuantity(1); o.setStatus("CREATED");
            when(orderMapper.findById(100L)).thenReturn(o); when(distributedLock.tryLock(anyString())).thenReturn(new DistributedLock.LockHandle("test", "token"));
            when(orderMapper.updateStatus(100L, "CREATED", "CANCELLED")).thenReturn(0);
            assertThatThrownBy(() -> orderService.cancelOrder(1L, 100L)).isInstanceOf(BizException.class);
        }
    }
}
