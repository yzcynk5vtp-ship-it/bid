package com.xiyu.bid.integration.organization.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.config.properties.ServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * 覆盖 SDK 的 ServerConfig Bean，解决上报地址为 127.0.0.1:18080 的问题。
 *
 * <p>SDK 原生 {@link ServerConfig#getHostAndPort()} 使用
 * {@link java.net.InetAddress#getLocalHost()} + {@code server.port} 拼地址，
 * 在内网服务器上会误取 127.0.0.1 和 Spring Boot 内部端口。
 *
 * <p>此类通过 @Primary 覆盖 SDK 的 Bean，从
 * {@code xiyu.integrations.organization.event-sdk.advertised-host} 和
 * {@code advertised-port} 读取对外暴露的地址。
 */
@Configuration
@ConditionalOnClass(name = "com.ehsy.eventlibrary.clientsdk.config.properties.ServerConfig")
@ConditionalOnProperty(
        prefix = "xiyu.integrations.organization.event-sdk",
        name = "enabled",
        havingValue = "true"
)
public class OrganizationEventSdkAdvertisedHostConfig {

    @Bean
    @Primary
    public ServerConfig serverConfig(Environment env) {
        return new AdvertisedServerConfig(env);
    }

    /**
     * 覆写 ServerConfig，使用可配置的对外上报地址。
     *
     * <p>兼容两类配置键，避免因为 Spring Boot relaxed binding 规则导致
     * 环境变量名和代码读取的键对不上而回退到 127.0.0.1：
     * <ul>
     *   <li>{@code xiyu.integrations.organization.event-sdk.advertised-host}
     *       ← 对应环境变量
     *       {@code XIYU_INTEGRATIONS_ORGANIZATION_EVENT_SDK_ADVERTISED_HOST}</li>
     *   <li>{@code xiyu.org.event.advertised-host}
     *       ← 对应环境变量
     *       {@code XIYU_ORG_EVENT_ADVERTISED_HOST}</li>
     * </ul>
     */
    static class AdvertisedServerConfig extends ServerConfig {

        private static final String PREFERRED_HOST_KEY
                = "xiyu.integrations.organization.event-sdk.advertised-host";
        private static final String PREFERRED_PORT_KEY
                = "xiyu.integrations.organization.event-sdk.advertised-port";
        private static final String LEGACY_HOST_KEY = "xiyu.org.event.advertised-host";
        private static final String LEGACY_PORT_KEY = "xiyu.org.event.advertised-port";

        private final String advertisedHost;
        private final int advertisedPort;

        AdvertisedServerConfig(Environment env) {
            super(env);
            this.advertisedHost = firstNonBlank(
                    env.getProperty(PREFERRED_HOST_KEY, ""),
                    env.getProperty(LEGACY_HOST_KEY, "")
            );
            this.advertisedPort = firstPositive(
                    env.getProperty(PREFERRED_PORT_KEY, int.class, 0),
                    env.getProperty(LEGACY_PORT_KEY, int.class, 0)
            );
        }

        @Override
        public String getHostAndPort() {
            String host = advertisedHost.isBlank() ? super.getHost() : advertisedHost.trim();
            int port = advertisedPort > 0 ? advertisedPort : getServerPort();
            return "http://" + host + ":" + port;
        }

        @Override
        public String getHost() {
            if (!advertisedHost.isBlank()) {
                return advertisedHost.trim();
            }
            return super.getHost();
        }

        private static String firstNonBlank(String preferred, String fallback) {
            return preferred == null || preferred.isBlank() ? fallback : preferred;
        }

        private static int firstPositive(int preferred, int fallback) {
            return preferred > 0 ? preferred : fallback;
        }
    }
}
