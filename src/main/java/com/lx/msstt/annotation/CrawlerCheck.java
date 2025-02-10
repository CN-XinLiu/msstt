package com.lx.msstt.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 爬虫校验
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CrawlerCheck {
    /**
     * 时间间隔
     */
    int timeInterval() default 1;

    /**
     * 时间间隔单位
     */
    TimeUnit timeUnit() default TimeUnit.MINUTES;

    /**
     * 过期时间
     */
    long expirationTimeInSeconds() default 180;
}
