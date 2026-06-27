package com.xiyu.bid.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标识字段在日志输出中需要脱敏。
 * <p>被标注字段的值在 {@link com.xiyu.bid.logging.LogSanitizer} 序列化时会被替换为掩码（如 {@code ***}）。</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {
}
