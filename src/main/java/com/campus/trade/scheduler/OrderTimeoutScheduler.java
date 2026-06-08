package com.campus.trade.scheduler;

import com.campus.trade.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderService orderService;

    @Scheduled(fixedRate = 60000)
    public void closeTimeoutOrders() {
        log.debug("开始扫描超时订单...");
        orderService.handleTimeoutOrders();
    }
}
