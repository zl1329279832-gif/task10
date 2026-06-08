package com.campus.trade.domain.enums;
public enum DisputeStatus {
    OPEN("已开启"), EVIDENCE("举证中"), ARBITRATING("仲裁中"), RESOLVED("已解决"), CLOSED("已关闭");
    private final String desc;
    DisputeStatus(String desc) { this.desc = desc; }
    public String getDesc() { return desc; }
}
