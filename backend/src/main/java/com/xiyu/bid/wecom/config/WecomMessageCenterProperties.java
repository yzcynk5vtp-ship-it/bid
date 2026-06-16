package com.xiyu.bid.wecom.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 西域统一消息中心「企微消息」配置。
 *
 * <p>我们的代码不保存 corpId/agentId/secret；那些凭据配在消息中心侧，
 * 我们只用 {@code applicationCode} 指明要调用哪个已登记的企微应用。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.wecom.message-center")
public class WecomMessageCenterProperties {

    /** 消息中心 base URL，例如 https://message-center.ehsy.com */
    private String baseUrl = "https://yapi.ehsy.com/mock/406";

    /** 发送企微消息的路径，默认 /qywx/sendMSG */
    private String sendPath = "/qywx/sendMSG";

    /** 企微应用标识（消息中心用 code 区分不同企微账号）。 */
    private String applicationCode = "";

    /** HTTP connect timeout in milliseconds. */
    private int connectTimeoutMs = 3000;

    /** HTTP read timeout in milliseconds. */
    private int readTimeoutMs = 10000;

    /**
     * 完整发送 URL。
     */
    public String sendUrl() {
        return baseUrl + sendPath;
    }
}
