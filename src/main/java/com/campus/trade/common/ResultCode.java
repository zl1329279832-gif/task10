package com.campus.trade.common;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),

    // 用户 1xxx
    USER_EXISTS(1001, "用户名已存在"),
    LOGIN_FAILED(1002, "用户名或密码错误"),
    ACCOUNT_DISABLED(1003, "账号已禁用"),
    PHONE_EXISTS(1004, "手机号已注册"),

    // 商品 2xxx
    PRODUCT_NOT_FOUND(2001, "商品不存在"),
    PRODUCT_OFF_SHELF(2002, "商品已下架"),
    STOCK_INSUFFICIENT(2003, "库存不足"),
    PRODUCT_NOT_OWNER(2004, "非商品所有者"),

    // 订单 3xxx
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_STATUS_INVALID(3002, "订单状态不允许此操作"),
    ORDER_DUPLICATE(3003, "重复下单"),
    ORDER_TIMEOUT(3004, "订单已超时"),
    ORDER_NOT_PARTICIPANT(3005, "非订单参与方"),
    ORDER_CANNOT_BUY_OWN(3006, "不能购买自己的商品"),

    // 支付 4xxx
    PAY_SIGN_INVALID(4001, "支付签名验证失败"),
    PAY_AMOUNT_MISMATCH(4002, "支付金额不匹配"),
    PAY_ALREADY_PROCESSED(4003, "支付已处理"),

    // 退款 5xxx
    REFUND_NOT_ALLOWED(5001, "当前状态不允许退款"),
    REFUND_NOT_FOUND(5002, "退款记录不存在"),
    REFUND_ALREADY_HANDLED(5003, "退款已处理"),

    // 争议 6xxx
    DISPUTE_NOT_FOUND(6001, "争议记录不存在"),
    DISPUTE_ALREADY_CLOSED(6002, "争议已关闭"),
    DISPUTE_NOT_ALLOWED(6003, "当前状态不允许发起争议"),

    // 系统 9xxx
    SYSTEM_ERROR(9999, "系统内部错误"),
    CONCURRENT_CONFLICT(9998, "并发冲突，请重试"),
    IDEMPOTENT_REJECT(9997, "请勿重复提交");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
