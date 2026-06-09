package com.campus.trade.domain.enums;
public enum FundSplitStatus {
    PENDING("待执行"), EXECUTED("已执行"), CANCELLED("已取消");
    private final String desc;
    FundSplitStatus(String desc) { this.desc = desc; }
    public String getDesc() { return desc; }
}
