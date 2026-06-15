package com.xiyu.bid.integration.organization.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.config.properties.ServerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrganizationEventSdkAdvertisedHostConfig - 对外上报地址配置")
class OrganizationEventSdkAdvertisedHostConfigTest {

    private static final String PREFERRED_HOST_KEY
            = "xiyu.integrations.organization.event-sdk.advertised-host";
    private static final String PREFERRED_PORT_KEY
            = "xiyu.integrations.organization.event-sdk.advertised-port";
    private static final String LEGACY_HOST_KEY = "xiyu.org.event.advertised-host";
    private static final String LEGACY_PORT_KEY = "xiyu.org.event.advertised-port";

    private final OrganizationEventSdkAdvertisedHostConfig config
            = new OrganizationEventSdkAdvertisedHostConfig();

    @Test
    @DisplayName("优先使用长配置键对应的值")
    void serverConfig_prefersPreferredKeys() {
        Environment env = environment()
                .withProperty(PREFERRED_HOST_KEY, "172.16.38.78")
                .withProperty(PREFERRED_PORT_KEY, "8080");

        ServerConfig serverConfig = config.serverConfig(env);

        assertThat(serverConfig.getHost()).isEqualTo("172.16.38.78");
        assertThat(serverConfig.getHostAndPort()).isEqualTo("http://172.16.38.78:8080");
    }

    @Test
    @DisplayName("长键缺失时回退到兼容键，解决 XIYU_ORG_EVENT_ADVERTISED_HOST 场景")
    void serverConfig_fallsBackToLegacyKeysWhenPreferredMissing() {
        Environment env = environment()
                .withProperty(LEGACY_HOST_KEY, "172.16.38.78")
                .withProperty(LEGACY_PORT_KEY, "8080");

        ServerConfig serverConfig = config.serverConfig(env);

        assertThat(serverConfig.getHost()).isEqualTo("172.16.38.78");
        assertThat(serverConfig.getHostAndPort()).isEqualTo("http://172.16.38.78:8080");
    }

    @Test
    @DisplayName("长键存在时忽略兼容键")
    void serverConfig_ignoresLegacyWhenPreferredPresent() {
        Environment env = environment()
                .withProperty(PREFERRED_HOST_KEY, "10.0.0.5")
                .withProperty(PREFERRED_PORT_KEY, "9090")
                .withProperty(LEGACY_HOST_KEY, "172.16.38.78")
                .withProperty(LEGACY_PORT_KEY, "8080");

        ServerConfig serverConfig = config.serverConfig(env);

        assertThat(serverConfig.getHost()).isEqualTo("10.0.0.5");
        assertThat(serverConfig.getHostAndPort()).isEqualTo("http://10.0.0.5:9090");
    }

    @Test
    @DisplayName("所有配置键都缺失时不抛出异常")
    void serverConfig_doesNotFailWhenAllKeysMissing() {
        Environment env = environment();

        ServerConfig serverConfig = config.serverConfig(env);

        assertThat(serverConfig.getHostAndPort()).isNotBlank();
    }

    private static MockEnvironment environment() {
        // ServerConfig 父类构造会读取 server.port，必须提供默认值避免 NPE。
        return new MockEnvironment().withProperty("server.port", "18080");
    }
}
