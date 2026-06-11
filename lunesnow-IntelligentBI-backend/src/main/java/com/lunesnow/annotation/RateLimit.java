package com.lunesnow.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式限流注解
 * 基于 Redis 令牌桶算法
 *
 * @author lunesnow
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流 key（支持 SpEL 表达式）
     * 默认为方法全限定名
     */
    String key() default "";

    /**
     * 每秒允许的请求数（令牌生成速率）
     */
    double permitsPerSecond() default 10;

    /**
     * 桶容量（最大令牌数）
     */
    double burstCapacity() default 20;

    /**
     * 限流类型
     */
    LimitType limitType() default LimitType.DEFAULT;

    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";

    /**
     * 限流类型枚举
     */
    enum LimitType {
        /**
         * 默认（根据方法名限流）
         */
        DEFAULT,
        /**
         * 根据 IP 限流
         */
        IP,
        /**
         * 根据用户限流
         */
        USER
    }
}
