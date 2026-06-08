package com.campus.trade.domain.enums;
public enum ArbitrationResult {
    BUYER_WIN("买家胜"), SELLER_WIN("卖家胜"), PARTIAL("部分支持");
    private final String desc;
    ArbitrationResult(String desc) { this.desc = desc; }
    public String getDesc() { return desc; }
}
