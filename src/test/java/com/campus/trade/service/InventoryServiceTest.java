package com.campus.trade.service;
import com.campus.trade.service.impl.InventoryServiceImpl;
import com.campus.trade.domain.entity.Sku;
import com.campus.trade.mapper.SkuMapper;
import com.campus.trade.common.exception.BizException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @InjectMocks private InventoryServiceImpl svc;
    @Mock private SkuMapper skuMapper;
    @Test void lockSuccess() { when(skuMapper.lockStock(1L,2)).thenReturn(1); assertThatCode(() -> svc.lockStock(1L,2)).doesNotThrowAnyException(); }
    @Test void lockFail() { when(skuMapper.lockStock(1L,10)).thenReturn(0); Sku s = new Sku(); s.setId(1L); s.setStock(5); s.setLockStock(3); when(skuMapper.findById(1L)).thenReturn(s); assertThatThrownBy(() -> svc.lockStock(1L,10)).isInstanceOf(BizException.class); }
    @Test void release() { when(skuMapper.releaseLockStock(1L,2)).thenReturn(1); svc.releaseStock(1L,2); verify(skuMapper).releaseLockStock(1L,2); }
    @Test void deduct() { when(skuMapper.deductLockStock(1L,1)).thenReturn(1); svc.deductStock(1L,1); verify(skuMapper).deductLockStock(1L,1); }
}
