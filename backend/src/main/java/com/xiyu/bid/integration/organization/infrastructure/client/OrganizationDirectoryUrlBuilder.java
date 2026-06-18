package com.xiyu.bid.integration.organization.infrastructure.client;

/**
 * OSS 组织架构网关 URL 构建工具。
 */
final class OrganizationDirectoryUrlBuilder {

    private OrganizationDirectoryUrlBuilder() {
    }

    static String buildUrl(String baseUrl, String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        if (path.startsWith("http")) {
            return path;
        }
        String base = trimRight(baseUrl);
        String cleanPath = trimLeft(path);
        return base + "/" + cleanPath;
    }

    private static String trimLeft(String value) {
        return value == null ? "" : value.replaceFirst("^/+", "");
    }

    private static String trimRight(String value) {
        return value == null ? "" : value.replaceFirst("/+$", "");
    }
}
