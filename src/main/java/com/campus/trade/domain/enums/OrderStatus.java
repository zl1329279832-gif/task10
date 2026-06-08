package com.campus.trade.domain.enums;
public enum OrderStatus {
    CREATED("待支付"), PAID("已支付"), SHIPPED("已发货"), RECEIVED("已收货"),
    SETTLED("已结算"), CANCELLED("已取消"), REFUNDING("退款中"), REFUNDED("已退款"),
    DISPUTED("争议中"), CLOSED("已关闭");
    private final String desc;
    OrderStatus(String desc) { this.desc = desc; }
    public String getDesc() { return desc; }
}
