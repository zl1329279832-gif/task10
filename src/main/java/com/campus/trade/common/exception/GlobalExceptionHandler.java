package com.campus.trade.common.exception;
import com.campus.trade.common.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.stream.Collectors;
@Slf4j @RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class) public Result<Void> handleBiz(BizException e) { log.warn("BizException: code={},msg={}",e.getCode(),e.getMessage()); return Result.fail(e.getCode(),e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public Result<Void> handleVal(MethodArgumentNotValidException e) { return Result.fail(400, e.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage).collect(Collectors.joining("; "))); }
    @ExceptionHandler(ConstraintViolationException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public Result<Void> handleCV(ConstraintViolationException e) { return Result.fail(400, e.getConstraintViolations().stream().map(ConstraintViolation::getMessage).collect(Collectors.joining("; "))); }
    @ExceptionHandler(MissingServletRequestParameterException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public Result<Void> handleMissing(MissingServletRequestParameterException e) { return Result.fail(400,"Missing: "+e.getParameterName()); }
    @ExceptionHandler(HttpMessageNotReadableException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public Result<Void> handleNR(HttpMessageNotReadableException e) { return Result.fail(400,"Malformed body"); }
    @ExceptionHandler(BadCredentialsException.class) @ResponseStatus(HttpStatus.UNAUTHORIZED) public Result<Void> handleBC(BadCredentialsException e) { return Result.fail(ErrorCode.AUTH_LOGIN_FAILED); }
    @ExceptionHandler(AccessDeniedException.class) @ResponseStatus(HttpStatus.FORBIDDEN) public Result<Void> handleAD(AccessDeniedException e) { return Result.fail(ErrorCode.FORBIDDEN); }
    @ExceptionHandler(DuplicateKeyException.class) @ResponseStatus(HttpStatus.CONFLICT) public Result<Void> handleDK(DuplicateKeyException e) { return Result.fail(ErrorCode.IDEMPOTENT_DUPLICATE); }
    @ExceptionHandler(Exception.class) @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) public Result<Void> handleEx(Exception e) { log.error("Unhandled",e); return Result.fail(ErrorCode.INTERNAL_ERROR); }
}
