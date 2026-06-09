package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.core.BatchAssignmentPolicy;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Batch 模块 Policy Bean 适配器.
 * Core 类已纯化为静态方法 + 函数式依赖，此处提供函数式 supplier 注入。
 */
@Configuration(proxyBeanMethods = false)
public final class BatchPolicyConfig {

    /**
     * 部门代码 supplier：从 ProjectAccessScopeService 取当前用户的可访问部门。
     */
    @Bean
    public BiFunction<User, String, List<String>> deptCodesSupplier(ProjectAccessScopeService projectAccessScopeService) {
        return (user, scope) -> projectAccessScopeService.getAllowedDepartmentCodes(user);
    }
}
