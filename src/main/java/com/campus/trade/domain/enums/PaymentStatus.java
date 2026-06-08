package com.campus.trade.domain.enums;
public enum PaymentStatus {
    PENDING("待支付"), SUCCESS("支付成功"), CLOSED("已关闭");
    private final String desc;
    PaymentStatus(String desc) { this.desc = desc; }
    public String getDesc() { return desc; }
}
