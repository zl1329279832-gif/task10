package com.campus.trade.common.util;
import cn.hutool.core.util.IdUtil;
public final class BizNoGenerator {
    private BizNoGenerator() {}
    public static String orderNo() { return "ORD"+IdUtil.getSnowflakeNextIdStr(); }
    public static String paymentNo() { return "PAY"+IdUtil.getSnowflakeNextIdStr(); }
    public static String refundNo() { return "REF"+IdUtil.getSnowflakeNextIdStr(); }
    public static String disputeNo() { return "DSP"+IdUtil.getSnowflakeNextIdStr(); }
    public static String arbitrationNo() { return "ARB"+IdUtil.getSnowflakeNextIdStr(); }
    public static String settlementNo() { return "STL"+IdUtil.getSnowflakeNextIdStr(); }
    public static String fundSplitNo() { return "FSP"+IdUtil.getSnowflakeNextIdStr(); }
}
