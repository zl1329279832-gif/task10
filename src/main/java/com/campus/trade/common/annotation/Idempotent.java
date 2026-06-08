package com.campus.trade.common.annotation;
import java.lang.annotation.*;
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) @Documented
public @interface Idempotent {
    String key(); String bizType() default "DEFAULT"; long expireSeconds() default 60;
}
