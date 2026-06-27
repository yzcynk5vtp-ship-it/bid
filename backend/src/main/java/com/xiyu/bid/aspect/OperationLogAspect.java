// Input: 带 @LogOperation 注解的方法调用
// Output: 结构化方法调用日志（入参、响应、耗时、异常）
// Pos: Aspect/可观测横切面
package com.xiyu.bid.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.annotation.LogOperation;
import com.xiyu.bid.logging.LogSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * 操作日志切面。
 * <p>拦截所有标注 {@link LogOperation} 的方法，统一记录：</p>
 * <ul>
 *   <li>类名、方法名</li>
 *   <li>入参（脱敏 + 截断）</li>
 *   <li>返回值（脱敏 + 截断）</li>
 *   <li>执行耗时</li>
 *   <li>异常类型与消息</li>
 * </ul>
 * <p>MDC 中已有的 traceId、userId、roleCode 会自动随日志输出，无需切面重复获取。</p>
 */
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    private final LogSanitizer logSanitizer;

    public OperationLogAspect(ObjectMapper objectMapper) {
        this.logSanitizer = new LogSanitizer(objectMapper);
    }

    @Around("@annotation(logOperation)")
    public Object logAround(ProceedingJoinPoint joinPoint, LogOperation logOperation) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        long start = System.currentTimeMillis();

        if (logOperation.logArgs()) {
            String argsJson = logSanitizer.sanitize(joinPoint.getArgs(), logOperation.maxArgLength());
            logAtLevel(logOperation.level(), "op_start class={} method={} args={}", className, methodName, argsJson);
        } else {
            logAtLevel(logOperation.level(), "op_start class={} method={}", className, methodName);
        }

        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            if (logOperation.logResult()) {
                String resultJson = logSanitizer.sanitize(result, logOperation.maxResultLength());
                logAtLevel(logOperation.level(), "op_end class={} method={} elapsed={}ms result={}",
                        className, methodName, elapsed, resultJson);
            } else {
                logAtLevel(logOperation.level(), "op_end class={} method={} elapsed={}ms",
                        className, methodName, elapsed);
            }
            return result;
        } catch (Throwable t) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("op_error class={} method={} elapsed={}ms exception={} message={}",
                    className, methodName, elapsed, t.getClass().getSimpleName(), t.getMessage(), t);
            throw t;
        }
    }

    private void logAtLevel(String level, String format, Object... args) {
        switch (level.toUpperCase()) {
            case "DEBUG" -> log.debug(format, args);
            case "WARN" -> log.warn(format, args);
            case "ERROR" -> log.error(format, args);
            default -> log.info(format, args);
        }
    }
}
