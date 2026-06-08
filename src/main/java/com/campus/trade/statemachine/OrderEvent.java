package com.campus.trade.statemachine;

public enum OrderEvent {
    PAY,
    PAY_TIMEOUT,
    SHIP,
    CONFIRM_RECEIVE,
    REQUEST_REFUND,
    APPROVE_REFUND,
    REJECT_REFUND,
    OPEN_DISPUTE,
    ARBITRATE,
    COMPLETE,
    CLOSE
}
