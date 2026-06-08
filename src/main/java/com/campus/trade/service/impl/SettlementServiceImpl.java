package com.campus.trade.service.impl;

import com.campus.trade.common.BusinessException;
import com.campus.trade.common.PageResult;
import com.campus.trade.common.ResultCode;
import com.campus.trade.mapper.SettlementMapper;
import com.campus.trade.model.entity.Order;
import com.campus.trade.model.entity.Settlement;
import com.campus.trade.service.AuditLogService;
import com.campus.trade.service.SettlementService;
import com.campus.trade.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementMapper settlementMapper;
    private final AuditLogService auditLogService;

    @Value("${settlement.platform-fee-rate:0.02}")
    private BigDecimal platformFeeRate;

    @Override
    public void createSettlement(Order order) {
        // Check if settlement already exists for this order
        Settlement existing = settlementMapper.selectByOrderNo(order.getOrderNo());
        if (existing != null) {
            log.info("结算记录已存在: orderNo={}", order.getOrderNo());
            return;
        }

        BigDecimal amount = order.getTotalAmount();
        BigDecimal fee = amount.multiply(platformFeeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal actualAmount = amount.subtract(fee);

        Settlement settlement = new Settlement();
        settlement.setSettlementNo(OrderNoGenerator.generateSettlementNo());
        settlement.setOrderNo(order.getOrderNo());
        settlement.setSellerId(order.getSellerId());
        settlement.setAmount(amount);
        settlement.setPlatformFee(fee);
        settlement.setActualAmount(actualAmount);
        settlement.setStatus("PENDING");
        settlementMapper.insert(settlement);

        auditLogService.log(null, null, "SETTLEMENT", "CREATE",
                "ORDER", order.getOrderNo(),
                "结算创建: amount=" + amount + ", fee=" + fee + ", actual=" + actualAmount, null);

        log.info("结算记录创建: settlementNo={}, orderNo={}, amount={}, fee={}, actual={}",
                settlement.getSettlementNo(), order.getOrderNo(), amount, fee, actualAmount);
    }

    @Override
    public Settlement getSettlement(String settlementNo) {
        Settlement settlement = settlementMapper.selectBySettlementNo(settlementNo);
        if (settlement == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "结算记录不存在");
        }
        return settlement;
    }

    @Override
    public PageResult<Settlement> listSellerSettlements(Long sellerId, int page, int size) {
        int offset = (page - 1) * size;
        List<Settlement> list = settlementMapper.selectBySellerId(sellerId, offset, size);
        long total = settlementMapper.countBySellerId(sellerId);
        return PageResult.of(list, total, page, size);
    }

    @Override
    public PageResult<Settlement> listAllSettlements(int page, int size) {
        int offset = (page - 1) * size;
        List<Settlement> list = settlementMapper.selectAll(offset, size);
        long total = settlementMapper.countAll();
        return PageResult.of(list, total, page, size);
    }
}
