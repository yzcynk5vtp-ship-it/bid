package com.xiyu.bid.config;

import com.xiyu.bid.auth.RedisTokenRevocationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class TokenRevocationConfig {

    @Bean
    @Primary
    @ConditionalOnBean(StringRedisTemplate.class)
    public RedisTokenRevocationService redisTokenRevocationService(StringRedisTemplate redisTemplate) {
        return new RedisTokenRevocationService(redisTemplate);
    }
}
