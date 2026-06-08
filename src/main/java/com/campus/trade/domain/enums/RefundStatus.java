package com.campus.trade.domain.enums;
public enum RefundStatus {
    PENDING("待处理"), APPROVED("已批准"), REJECTED("已拒绝"), SUCCESS("退款成功");
    private final String desc;
    RefundStatus(String desc) { this.desc = desc; }
    public String getDesc() { return desc; }
}
