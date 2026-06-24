package com.xiyu.bid.user.config;

import com.xiyu.bid.user.core.AssignmentCandidatePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * User 模块 Policy Bean 注册.
 * 纯核心类（core 包）保持无框架依赖，通过 @Configuration 注册到 Spring 上下文。
 */
@Configuration(proxyBeanMethods = false)
public final class UserPolicyConfig {

    @Bean
    public AssignmentCandidatePolicy assignmentCandidatePolicy() {
        return new AssignmentCandidatePolicy();
    }
}
