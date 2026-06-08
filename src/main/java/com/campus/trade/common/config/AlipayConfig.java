package com.campus.trade.common.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
@Data @Configuration @ConfigurationProperties(prefix="alipay")
public class AlipayConfig {
    private String appId; private String privateKey; private String alipayPublicKey;
    private String serverUrl; private String notifyUrl; private String returnUrl;
    private String signType = "RSA2"; private String charset = "UTF-8";
}
