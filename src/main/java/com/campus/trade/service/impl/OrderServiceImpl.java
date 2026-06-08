package com.campus.trade.service.impl;

import com.campus.trade.common.BusinessException;
import com.campus.trade.common.PageResult;
import com.campus.trade.common.ResultCode;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.ProductMapper;
import com.campus.trade.mapper.UserMapper;
import com.campus.trade.model.dto.request.OrderCreateRequest;
import com.campus.trade.model.dto.response.OrderResponse;
import com.campus.trade.model.entity.Order;
import com.campus.trade.model.entity.Product;
import com.campus.trade.model.entity.User;
import com.campus.trade.model.enums.OrderStatus;
import com.campus.trade.service.InventoryService;
import com.campus.trade.service.OrderService;
import com.campus.trade.service.SettlementService;
import com.campus.trade.statemachine.OrderEvent;
import com.campus.trade.statemachine.OrderStateMachine;
import com.campus.trade.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.campus.trade.util.RedisKeyConstants.IDEMPOTENT_ORDER;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final InventoryService inventoryService;
    private final SettlementService settlementService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${order.payment-timeout-minutes:15}")
    private int paymentTimeoutMinutes;

    @Override
    @Transactional
    public OrderResponse createOrder(Long buyerId, OrderCreateRequest request, String idempotentKey) {
        // Idempotent check via Redis
        if (idempotentKey != null) {
            String redisKey = String.format(IDEMPOTENT_ORDER, idempotentKey);
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", 15, TimeUnit.MINUTES);
            if (Boolean.FALSE.equals(isNew)) {
                // Check if order already exists
                Order existing = orderMapper.selectByIdempotentKey(idempotentKey);
                if (existing != null) {
                    return toResponse(existing);
                }
                throw new BusinessException(ResultCode.IDEMPOTENT_REJECT);
            }
        }

        // Validate product
        Product product = productMapper.selectById(request.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (product.getStatus() != 1) {
            throw new BusinessException(ResultCode.PRODUCT_OFF_SHELF);
        }
        if (product.getSellerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.ORDER_CANNOT_BUY_OWN);
        }

        // Lock stock via Redis
        if (!inventoryService.lockStock(product.getId(), request.getQuantity())) {
            throw new BusinessException(ResultCode.STOCK_INSUFFICIENT);
        }

        // Deduct DB stock with optimistic lock
        int affected = productMapper.deductStock(product.getId(), request.getQuantity(), product.getVersion());
        if (affected == 0) {
            // Rollback Redis
            inventoryService.releaseStock(product.getId(), request.getQuantity());
            throw new BusinessException(ResultCode.STOCK_INSUFFICIENT);
        }

        // Create order
        Order order = new Order();
        order.setOrderNo(OrderNoGenerator.generateOrderNo());
        order.setBuyerId(buyerId);
        order.setSellerId(product.getSellerId());
        order.setProductId(product.getId());
        order.setProductTitle(product.getTitle());
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(product.getPrice());
        order.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setPaymentDeadline(LocalDateTime.now().plusMinutes(paymentTimeoutMinutes));
        order.setIdempotentKey(idempotentKey);
        orderMapper.insert(order);

        log.info("订单创建成功: orderNo={}, buyerId={}, productId={}, amount={}",
                order.getOrderNo(), buyerId, product.getId(), order.getTotalAmount());

        return toResponse(order);
    }

    @Override
    public OrderResponse getOrder(String orderNo, Long userId) {
        Order order = getOrderOrThrow(orderNo);
        if (userId != null && !order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_PARTICIPANT);
        }
        return toResponse(order);
    }

    @Override
    public PageResult<OrderResponse> listBuyerOrders(Long buyerId, String status, int page, int size) {
        int offset = (page - 1) * size;
        List<Order> orders = orderMapper.selectByBuyerId(buyerId, status, offset, size);
        long total = orderMapper.countByBuyerId(buyerId, status);
        return PageResult.of(orders.stream().map(this::toResponse).collect(Collectors.toList()), total, page, size);
    }

    @Override
    public PageResult<OrderResponse> listSellerOrders(Long sellerId, String status, int page, int size) {
        int offset = (page - 1) * size;
        List<Order> orders = orderMapper.selectBySellerId(sellerId, status, offset, size);
        long total = orderMapper.countBySellerId(sellerId, status);
        return PageResult.of(orders.stream().map(this::toResponse).collect(Collectors.toList()), total, page, size);
    }

    @Override
    public PageResult<OrderResponse> listAllOrders(String status, int page, int size) {
        int offset = (page - 1) * size;
        List<Order> orders = orderMapper.selectAll(status, offset, size);
        long total = orderMapper.countAll(status);
        return PageResult.of(orders.stream().map(this::toResponse).collect(Collectors.toList()), total, page, size);
    }

    @Override
    @Transactional
    public void cancelOrder(String orderNo, Long buyerId) {
        Order order = getOrderOrThrow(orderNo);
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_PARTICIPANT);
        }

        OrderStatus newStatus = OrderStateMachine.transition(
                OrderStatus.valueOf(order.getStatus()), OrderEvent.CLOSE);

        int affected = orderMapper.updateClosed(orderNo, newStatus.name(), "买家取消", order.getVersion());
        if (affected == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_CONFLICT);
        }

        // Release stock
        inventoryService.releaseStock(order.getProductId(), order.getQuantity());
        productMapper.restoreStock(order.getProductId(), order.getQuantity());

        log.info("订单取消: orderNo={}, buyerId={}", orderNo, buyerId);
    }

    @Override
    @Transactional
    public void shipOrder(String orderNo, Long sellerId) {
        Order order = getOrderOrThrow(orderNo);
        if (!order.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_PARTICIPANT);
        }

        OrderStatus newStatus = OrderStateMachine.transition(
                OrderStatus.valueOf(order.getStatus()), OrderEvent.SHIP);

        int affected = orderMapper.updateShippedAt(orderNo, newStatus.name(), order.getVersion());
        if (affected == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_CONFLICT);
        }

        log.info("卖家发货: orderNo={}, sellerId={}", orderNo, sellerId);
    }

    @Override
    @Transactional
    public void confirmReceive(String orderNo, Long buyerId) {
        Order order = getOrderOrThrow(orderNo);
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_PARTICIPANT);
        }

        OrderStatus newStatus = OrderStateMachine.transition(
                OrderStatus.valueOf(order.getStatus()), OrderEvent.CONFIRM_RECEIVE);

        int affected = orderMapper.updateReceivedAt(orderNo, newStatus.name(), order.getVersion());
        if (affected == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_CONFLICT);
        }

        log.info("买家确认收货: orderNo={}, buyerId={}", orderNo, buyerId);

        // Auto-complete: create settlement
        try {
            Order updated = orderMapper.selectByOrderNo(orderNo);
            OrderStatus completeStatus = OrderStateMachine.transition(
                    OrderStatus.valueOf(updated.getStatus()), OrderEvent.COMPLETE);
            orderMapper.updateStatusWithVersion(orderNo, completeStatus.name(), updated.getVersion());
            settlementService.createSettlement(updated);
            log.info("订单自动完成并创建结算: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("自动完成订单失败，需人工处理: orderNo={}", orderNo, e);
        }
    }

    @Override
    @Transactional
    public void closeOrder(String orderNo, String reason) {
        Order order = getOrderOrThrow(orderNo);
        OrderStatus newStatus = OrderStateMachine.transition(
                OrderStatus.valueOf(order.getStatus()), OrderEvent.CLOSE);

        int affected = orderMapper.updateClosed(orderNo, newStatus.name(), reason, order.getVersion());
        if (affected == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_CONFLICT);
        }

        // Release stock if order was pending payment
        if (OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            inventoryService.releaseStock(order.getProductId(), order.getQuantity());
            productMapper.restoreStock(order.getProductId(), order.getQuantity());
        }

        log.info("订单关闭: orderNo={}, reason={}", orderNo, reason);
    }

    @Override
    public void handleTimeoutOrders() {
        List<Order> timeoutOrders = orderMapper.selectTimeoutOrders();
        for (Order order : timeoutOrders) {
            try {
                OrderStatus newStatus = OrderStateMachine.transition(
                        OrderStatus.valueOf(order.getStatus()), OrderEvent.PAY_TIMEOUT);

                int affected = orderMapper.updateClosed(order.getOrderNo(), newStatus.name(),
                        "支付超时自动关闭", order.getVersion());
                if (affected > 0) {
                    inventoryService.releaseStock(order.getProductId(), order.getQuantity());
                    productMapper.restoreStock(order.getProductId(), order.getQuantity());
                    log.info("超时关闭订单: orderNo={}", order.getOrderNo());
                }
            } catch (Exception e) {
                log.error("关闭超时订单失败: orderNo={}", order.getOrderNo(), e);
            }
        }
    }

    private Order getOrderOrThrow(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setBuyerId(order.getBuyerId());
        response.setSellerId(order.getSellerId());
        response.setProductId(order.getProductId());
        response.setProductTitle(order.getProductTitle());
        response.setQuantity(order.getQuantity());
        response.setUnitPrice(order.getUnitPrice());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus());
        response.setPaymentDeadline(order.getPaymentDeadline());
        response.setPaidAt(order.getPaidAt());
        response.setShippedAt(order.getShippedAt());
        response.setReceivedAt(order.getReceivedAt());
        response.setClosedAt(order.getClosedAt());
        response.setCloseReason(order.getCloseReason());
        response.setCreatedAt(order.getCreatedAt());

        User buyer = userMapper.selectById(order.getBuyerId());
        if (buyer != null) {
            response.setBuyerName(buyer.getNickname() != null ? buyer.getNickname() : buyer.getUsername());
        }
        User seller = userMapper.selectById(order.getSellerId());
        if (seller != null) {
            response.setSellerName(seller.getNickname() != null ? seller.getNickname() : seller.getUsername());
        }
        return response;
    }
}
