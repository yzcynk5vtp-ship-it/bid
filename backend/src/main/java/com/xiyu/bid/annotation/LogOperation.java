package com.xiyu.bid.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要在日志中自动记录入参、响应和耗时的方法。
 * <p>切面 {@link com.xiyu.bid.aspect.OperationLogAspect} 会拦截带此注解的方法，
 * 输出结构化日志，便于排查问题和性能分析。</p>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperation {

    /** 日志级别：DEBUG / INFO / WARN / ERROR。 */
    String level() default "INFO";

    /** 是否记录方法入参。 */
    boolean logArgs() default true;

    /** 是否记录方法返回值。 */
    boolean logResult() default true;

    /** 入参 JSON 最大长度，超过则截断。 */
    int maxArgLength() default 2048;

    /** 返回值 JSON 最大长度，超过则截断。 */
    int maxResultLength() default 2048;
}
