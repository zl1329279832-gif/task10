package com.campus.trade.common.exception;
import lombok.Getter;
@Getter
public enum ErrorCode {
    SUCCESS(200,"success"), BAD_REQUEST(400,"Bad request"), UNAUTHORIZED(401,"Unauthorized"),
    FORBIDDEN(403,"Access denied"), NOT_FOUND(404,"Resource not found"), INTERNAL_ERROR(500,"Internal server error"),
    AUTH_LOGIN_FAILED(1001,"Invalid username or password"), AUTH_TOKEN_EXPIRED(1002,"Token expired"),
    AUTH_TOKEN_INVALID(1003,"Invalid token"), AUTH_ACCOUNT_DISABLED(1004,"Account disabled"),
    PRODUCT_NOT_FOUND(2001,"Product not found"), PRODUCT_OFF_SHELF(2002,"Product is off shelf"),
    SKU_NOT_FOUND(2003,"SKU not found"), INSUFFICIENT_STOCK(2004,"Insufficient stock"),
    PRODUCT_NOT_OWNER(2005,"Not the product owner"),
    ORDER_NOT_FOUND(3001,"Order not found"), ORDER_STATUS_INVALID(3002,"Invalid order status transition"),
    ORDER_NOT_BUYER(3003,"Not the order buyer"), ORDER_NOT_SELLER(3004,"Not the order seller"),
    ORDER_ALREADY_PAID(3005,"Order already paid"), ORDER_EXPIRED(3006,"Order payment expired"),
    ORDER_CANNOT_CANCEL(3007,"Order cannot be cancelled"), ORDER_LOCK_FAILED(3008,"Order is being processed, please retry"),
    PAYMENT_NOT_FOUND(4001,"Payment not found"), PAYMENT_ALREADY_SUCCESS(4002,"Payment already successful"),
    PAYMENT_VERIFY_FAILED(4003,"Payment signature verification failed"),
    PAYMENT_AMOUNT_MISMATCH(4004,"Payment amount mismatch"), PAYMENT_CALLBACK_DUPLICATE(4005,"Duplicate payment callback"),
    PAYMENT_FROZEN(4006,"Payment is frozen, operation not allowed"),
    PAYMENT_NOT_FROZEN(4007,"Payment is not frozen"),
    PAYMENT_FREEZE_FAILED(4008,"Failed to freeze payment"),
    PAYMENT_UNFREEZE_FAILED(4009,"Failed to unfreeze payment"),
    REFUND_NOT_FOUND(5001,"Refund not found"), REFUND_ALREADY_EXISTS(5002,"Refund already applied"),
    REFUND_NOT_ALLOWED(5003,"Refund not allowed in current status"), REFUND_AMOUNT_EXCEED(5004,"Refund amount exceeds order total"),
    DISPUTE_NOT_FOUND(6001,"Dispute not found"), DISPUTE_NOT_PARTICIPANT(6002,"Not a dispute participant"),
    DISPUTE_ALREADY_EXISTS(6003,"Dispute already exists"), DISPUTE_NOT_IN_EVIDENCE(6004,"Dispute not in evidence phase"),
    ARBITRATION_ALREADY_DONE(7001,"Arbitration already completed"),
    ARBITRATION_NOT_FOUND(7002,"Arbitration not found"),
    ARBITRATION_REVERSAL_FAILED(7003,"Arbitration reversal failed"),
    SETTLEMENT_ALREADY_EXISTS(8001,"Settlement already exists for this order"),
    SETTLEMENT_FROZEN(8002,"Settlement is frozen, operation not allowed"),
    SETTLEMENT_NOT_FROZEN(8003,"Settlement is not frozen"),
    SETTLEMENT_FREEZE_FAILED(8004,"Failed to freeze settlement"),
    SETTLEMENT_NOT_FAILED(8005,"Settlement is not in FAILED status for retry"),
    SETTLEMENT_RETRY_FAILED(8006,"Settlement retry failed"),
    FUND_SPLIT_NOT_FOUND(8010,"Fund split record not found"),
    FUND_SPLIT_ALREADY_EXISTS(8011,"Active fund split already exists"),
    FUND_SPLIT_INVALID(8012,"Invalid fund split amounts"),
    FUND_SPLIT_EXECUTE_FAILED(8013,"Fund split execution failed"),
    IDEMPOTENT_DUPLICATE(9001,"Duplicate request"), IDEMPOTENT_PROCESSING(9002,"Request is being processed");
    private final int code; private final String message;
    ErrorCode(int code, String message) { this.code = code; this.message = message; }
}
