package com.campus.trade.common.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;
@Data @Configuration @ConfigurationProperties(prefix="trade.order")
public class TradeConfig {
    private int payTimeoutMinutes = 30;
    private BigDecimal platformFeeRate = new BigDecimal("0.02");
    private int autoReceiveDays = 7;
}
