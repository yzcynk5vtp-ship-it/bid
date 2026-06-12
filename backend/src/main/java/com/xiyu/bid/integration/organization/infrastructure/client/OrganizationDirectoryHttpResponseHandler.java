package com.xiyu.bid.integration.organization.infrastructure.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryResponseDecision;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryResponseOutcome;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryResponsePolicy;

import java.util.Optional;

/**
 * 组织主数据 HTTP 响应的 JSON 解析与分类决策。
 * <p>
 * 从 {@link OrganizationDirectoryHttpGateway} 拆分出的纯函数工具，
 * 负责将西域 OSS 主数据接口的原始 JSON 响应解析为 {@link Optional}{@code <JsonNode>}，
 * 并根据 {@code code} + 数据负载做分类决策（成功/重试/不可重试）。
 * </p>
 */
final class OrganizationDirectoryHttpResponseHandler {

    static Optional<JsonNode> parseResponse(ObjectMapper objectMapper, String body) throws JsonProcessingException {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(body);
        return classify(root).outcome() == OrganizationDirectoryResponseOutcome.SUCCESS
                ? Optional.of(root)
                : Optional.empty();
    }

    static OrganizationDirectoryResponseDecision classify(JsonNode root) {
        JsonNode code = root.path("code");
        if (!code.isValueNode() || code.isNull()) {
            return new OrganizationDirectoryResponseDecision(
                    OrganizationDirectoryResponseOutcome.SUCCESS, false, "success");
        }
        OrganizationDirectoryResponseDecision decision =
                OrganizationDirectoryResponsePolicy.classify(code.asText(), hasData(root));
        if (decision.retryable()) {
            throw OrganizationDirectoryHttpGatewayException.retryable(decision.message(), null);
        }
        if (decision.outcome() == OrganizationDirectoryResponseOutcome.NON_RETRYABLE_FAILURE) {
            throw OrganizationDirectoryHttpGatewayException.nonRetryable(decision.message(), null);
        }
        return decision;
    }

    private static boolean hasData(JsonNode root) {
        return hasPayload(root.path("data")) || hasPayload(root.path("result"));
    }

    private static boolean hasPayload(JsonNode data) {
        return !data.isMissingNode() && !data.isNull()
                && (!data.isContainerNode() || data.size() > 0);
    }
}
