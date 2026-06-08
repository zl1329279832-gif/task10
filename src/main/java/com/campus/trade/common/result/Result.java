package com.campus.trade.common.result;
import com.campus.trade.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
@Data @JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {
    private int code; private String message; private T data; private long timestamp;
    private Result() { this.timestamp = System.currentTimeMillis(); }
    public static <T> Result<T> ok() { Result<T> r = new Result<>(); r.setCode(200); r.setMessage("success"); return r; }
    public static <T> Result<T> ok(T data) { Result<T> r = ok(); r.setData(data); return r; }
    public static <T> Result<T> ok(String message, T data) { Result<T> r = ok(); r.setMessage(message); r.setData(data); return r; }
    public static <T> Result<T> fail(int code, String message) { Result<T> r = new Result<>(); r.setCode(code); r.setMessage(message); return r; }
    public static <T> Result<T> fail(ErrorCode errorCode) { return fail(errorCode.getCode(), errorCode.getMessage()); }
    public static <T> Result<T> fail(ErrorCode errorCode, String detail) { return fail(errorCode.getCode(), errorCode.getMessage() + ": " + detail); }
}
