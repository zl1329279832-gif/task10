package com.campus.trade.domain.enums;
public enum SettlementStatus {
    PENDING("待结算"), SETTLED("已结算"), FAILED("结算失败"), FROZEN("已冻结"), REVERSED("已冲正");
    private final String desc;
    SettlementStatus(String desc) { this.desc = desc; }
    public String getDesc() { return desc; }
}
