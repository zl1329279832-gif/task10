package com.campus.trade.service.impl;

import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.domain.entity.*;
import com.campus.trade.domain.enums.OrderStateTransition;
import com.campus.trade.domain.enums.OrderStatus;
import com.campus.trade.dto.request.CreateOrderRequest;
import com.campus.trade.dto.request.ShipRequest;
import com.campus.trade.dto.response.OrderResponse;
import com.campus.trade.dto.response.SettlementResponse;
import com.campus.trade.common.result.PageResult;
import com.campus.trade.common.util.BizNoGenerator;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.common.config.TradeConfig;
import com.campus.trade.mapper.*;
import com.campus.trade.service.*;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final UserMapper userMapper;
    private final InventoryService inventoryService;
    private final AuditService auditService;
    private final DistributedLock distributedLock;
    private final TradeConfig tradeConfig;

    @Setter(onMethod_ = {@Autowired, @Lazy})
    private SettlementService settlementService;

    @Override
    @Transactional
    public OrderResponse createOrder(Long buyerId, CreateOrderRequest req) {
        Sku sku = skuMapper.findById(req.getSkuId());
        if (sku == null || sku.getStatus() != 1) throw new BizException(ErrorCode.SKU_NOT_FOUND);
        Product product = productMapper.findById(sku.getProductId());
        if (product == null || product.getStatus() != 1) throw new BizException(ErrorCode.PRODUCT_OFF_SHELF);
        if (product.getSellerId().equals(buyerId)) throw new BizException(400, "Cannot buy your own product");

        inventoryService.lockStock(sku.getId(), req.getQuantity());

        Order order = new Order();
        order.setOrderNo(BizNoGenerator.orderNo());
        order.setBuyerId(buyerId);
        order.setSellerId(product.getSellerId());
        order.setProductId(product.getId());
        order.setSkuId(sku.getId());
        order.setSkuName(sku.getSkuName());
        order.setQuantity(req.getQuantity());
        order.setUnitPrice(sku.getPrice());
        order.setTotalAmount(sku.getPrice().multiply(BigDecimal.valueOf(req.getQuantity())));
        order.setStatus(OrderStatus.CREATED.name());
        order.setAddress(req.getAddress());
        order.setRemark(req.getRemark());
        order.setPayExpireAt(LocalDateTime.now().plusMinutes(tradeConfig.getPayTimeoutMinutes()));
        orderMapper.insert(order);

        logTransition(order.getId(), null, OrderStatus.CREATED.name(), buyerId, "Order created");
        auditService.log(buyerId, null, "ORDER", "CREATE", "ORDER", order.getId(), "orderNo=" + order.getOrderNo());
        return toResponse(order, product, null, null);
    }

    @Override
    public OrderResponse getOrder(Long id) {
        Order o = orderMapper.findById(id);
        if (o == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        return enrich(o);
    }

    @Override
    public OrderResponse getOrderByNo(String no) {
        Order o = orderMapper.findByOrderNo(no);
        if (o == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        return enrich(o);
    }

    @Override
    public PageResult<OrderResponse> listBuyerOrders(Long buyerId, String status, int page, int size) {
        int off = (page - 1) * size;
        return PageResult.of(
                orderMapper.findByBuyerId(buyerId, status, off, size).stream().map(this::enrich).toList(),
                orderMapper.countByBuyerId(buyerId, status), page, size);
    }

    @Override
    public PageResult<OrderResponse> listSellerOrders(Long sellerId, String status, int page, int size) {
        int off = (page - 1) * size;
        return PageResult.of(
                orderMapper.findBySellerId(sellerId, status, off, size).stream().map(this::enrich).toList(),
                orderMapper.countBySellerId(sellerId, status), page, size);
    }

    @Override
    @Transactional
    public void cancelOrder(Long buyerId, Long orderId) {
        Order o = orderMapper.findById(orderId);
        if (o == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        if (!o.getBuyerId().equals(buyerId)) throw new BizException(ErrorCode.ORDER_NOT_BUYER);
        if (!OrderStatus.CREATED.name().equals(o.getStatus())) throw new BizException(ErrorCode.ORDER_CANNOT_CANCEL);

        String lk = "order:" + orderId;
        if (!distributedLock.tryLock(lk)) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            if (orderMapper.updateStatus(orderId, "CREATED", "CANCELLED") == 0)
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
            orderMapper.updateCloseInfo(orderId, LocalDateTime.now(), "Buyer cancelled");
            logTransition(orderId, "CREATED", "CANCELLED", buyerId, "Buyer cancelled");
            inventoryService.releaseStock(o.getSkuId(), o.getQuantity());
        } finally {
            distributedLock.unlock(lk);
        }
    }

    @Override
    @Transactional
    public void shipOrder(Long sellerId, ShipRequest req) {
        Order o = orderMapper.findById(req.getOrderId());
        if (o == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        if (!o.getSellerId().equals(sellerId)) throw new BizException(ErrorCode.ORDER_NOT_SELLER);

        String lk = "order:" + o.getId();
        if (!distributedLock.tryLock(lk)) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            if (orderMapper.updateStatus(o.getId(), "PAID", "SHIPPED") == 0)
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
            orderMapper.updateShipInfo(o.getId(), req.getLogisticsNo(), req.getLogisticsCompany(), LocalDateTime.now());
            logTransition(o.getId(), "PAID", "SHIPPED", sellerId, "Shipped: " + req.getLogisticsNo());
        } finally {
            distributedLock.unlock(lk);
        }
    }

    @Override
    @Transactional
    public void confirmReceive(Long buyerId, Long orderId) {
        Order o = orderMapper.findById(orderId);
        if (o == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        if (!o.getBuyerId().equals(buyerId)) throw new BizException(ErrorCode.ORDER_NOT_BUYER);

        String lk = "order:" + orderId;
        if (!distributedLock.tryLock(lk)) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            if (orderMapper.updateStatus(orderId, "SHIPPED", "RECEIVED") == 0)
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
            orderMapper.updateReceiveInfo(orderId, LocalDateTime.now());
            logTransition(orderId, "SHIPPED", "RECEIVED", buyerId, "Confirmed receipt");
        } finally {
            distributedLock.unlock(lk);
        }

        // Auto-create and execute settlement (escrow release)
        try {
            SettlementResponse sr = settlementService.createSettlement(orderId);
            settlementService.executeSettlement(sr.getId());
        } catch (BizException e) {
            log.warn("Auto-settlement failed for order {}: {}", orderId, e.getMessage());
            auditService.log(buyerId, null, "SETTLEMENT", "AUTO_SETTLE_FAILED",
                    "ORDER", orderId, "error=" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void transitionOrder(Long orderId, String toStatus, Long operatorId, String remark) {
        Order o = orderMapper.findById(orderId);
        if (o == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        OrderStatus from = OrderStatus.valueOf(o.getStatus());
        OrderStatus to = OrderStatus.valueOf(toStatus);
        if (!OrderStateTransition.isValid(from, to))
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, from.name() + " -> " + to.name());

        String lk = "order:" + orderId;
        if (!distributedLock.tryLock(lk)) throw new BizException(ErrorCode.ORDER_LOCK_FAILED);
        try {
            if (orderMapper.updateStatus(orderId, from.name(), to.name()) == 0)
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "Concurrent modification");
            logTransition(orderId, from.name(), to.name(), operatorId, remark);
        } finally {
            distributedLock.unlock(lk);
        }
    }

    @Override
    @Transactional
    public void transitionToPaidInternal(Long orderId, String remark) {
        Order o = orderMapper.findById(orderId);
        if (o == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        OrderStatus from = OrderStatus.valueOf(o.getStatus());
        if (!OrderStateTransition.isValid(from, OrderStatus.PAID))
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, from.name() + " -> PAID");
        if (orderMapper.updateStatus(orderId, from.name(), OrderStatus.PAID.name()) == 0)
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "Concurrent modification");
        logTransition(orderId, from.name(), OrderStatus.PAID.name(), null, remark);
    }

    @Override
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void closeExpiredOrders() {
        for (Order o : orderMapper.findExpiredOrders(50)) {
            String lk = "order:" + o.getId();
            if (!distributedLock.tryLock(lk, 5000)) continue;
            try {
                if (orderMapper.updateStatus(o.getId(), "CREATED", "CANCELLED") > 0) {
                    orderMapper.updateCloseInfo(o.getId(), LocalDateTime.now(), "Payment timeout");
                    logTransition(o.getId(), "CREATED", "CANCELLED", null, "Auto-close timeout");
                    inventoryService.releaseStock(o.getSkuId(), o.getQuantity());
                    log.info("Auto-closed: {}", o.getOrderNo());
                }
            } finally {
                distributedLock.unlock(lk);
            }
        }
    }

    private void logTransition(Long orderId, String from, String to, Long opId, String remark) {
        OrderStatusLog l = new OrderStatusLog();
        l.setOrderId(orderId);
        l.setFromStatus(from != null ? from : "NEW");
        l.setToStatus(to);
        l.setOperatorId(opId);
        l.setRemark(remark);
        statusLogMapper.insert(l);
    }

    private OrderResponse enrich(Order o) {
        return toResponse(o,
                productMapper.findById(o.getProductId()),
                userMapper.findById(o.getBuyerId()),
                userMapper.findById(o.getSellerId()));
    }

    private OrderResponse toResponse(Order o, Product p, User b, User s) {
        OrderResponse r = new OrderResponse();
        r.setId(o.getId());
        r.setOrderNo(o.getOrderNo());
        r.setBuyerId(o.getBuyerId());
        r.setBuyerName(b != null ? b.getNickname() : null);
        r.setSellerId(o.getSellerId());
        r.setSellerName(s != null ? s.getNickname() : null);
        r.setProductId(o.getProductId());
        r.setProductTitle(p != null ? p.getTitle() : null);
        r.setSkuId(o.getSkuId());
        r.setSkuName(o.getSkuName());
        r.setQuantity(o.getQuantity());
        r.setUnitPrice(o.getUnitPrice());
        r.setTotalAmount(o.getTotalAmount());
        r.setStatus(o.getStatus());
        r.setStatusDesc(OrderStatus.valueOf(o.getStatus()).getDesc());
        r.setPayTime(o.getPayTime());
        r.setShipTime(o.getShipTime());
        r.setReceiveTime(o.getReceiveTime());
        r.setCloseTime(o.getCloseTime());
        r.setCloseReason(o.getCloseReason());
        r.setLogisticsNo(o.getLogisticsNo());
        r.setLogisticsCompany(o.getLogisticsCompany());
        r.setAddress(o.getAddress());
        r.setRemark(o.getRemark());
        r.setPayExpireAt(o.getPayExpireAt());
        r.setCreatedAt(o.getCreatedAt());
        return r;
    }
}
