package com.campus.trade.service;
import com.campus.trade.service.impl.OrderServiceImpl;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.enums.OrderStatus;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.OrderStatusLogMapper;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.common.config.TradeConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class OrderTransitionTest {
    @InjectMocks private OrderServiceImpl svc;
    @Mock private OrderMapper orderMapper; @Mock private OrderStatusLogMapper statusLogMapper;
    @Mock private DistributedLock distributedLock; @Mock private TradeConfig tradeConfig;
    @Test void validTransition() {
        Order o = new Order(); o.setId(1L); o.setStatus("PAID");
        when(orderMapper.findById(1L)).thenReturn(o); when(distributedLock.tryLock(anyString())).thenReturn(true);
        when(orderMapper.updateStatus(1L,"PAID","SHIPPED")).thenReturn(1); when(statusLogMapper.insert(any())).thenReturn(1);
        assertThatCode(() -> svc.transitionOrder(1L,"SHIPPED",2L,"ship")).doesNotThrowAnyException();
    }
    @Test void invalidTransition() {
        Order o = new Order(); o.setId(1L); o.setStatus("CREATED");
        when(orderMapper.findById(1L)).thenReturn(o);
        assertThatThrownBy(() -> svc.transitionOrder(1L,"SHIPPED",2L,"skip")).isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException)e).getErrorCode()).isEqualTo(ErrorCode.ORDER_STATUS_INVALID));
    }
    @Test void lockFails() {
        Order o = new Order(); o.setId(1L); o.setStatus("PAID");
        when(orderMapper.findById(1L)).thenReturn(o); when(distributedLock.tryLock("order:1")).thenReturn(false);
        assertThatThrownBy(() -> svc.transitionOrder(1L,"SHIPPED",2L,"x")).isInstanceOf(BizException.class);
        verify(orderMapper, never()).updateStatus(anyLong(), anyString(), anyString());
    }
}
