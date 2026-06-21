package com.xiyu.bid.crm.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.config.TraceHeaderInjector;

import com.xiyu.bid.crm.config.CrmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


@Component
public class CrmHttpClient {

    private static final Logger log = LoggerFactory.getLogger(CrmHttpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 日志中 body 最大长度，超过截断（避免超大响应刷爆日志） */
    private static final int LOG_BODY_MAX_LEN = 2000;

    private final RestTemplate restTemplate;
    private final CrmProperties properties;

    public CrmHttpClient(CrmProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Posts to a full URL (for multi-BaseUrl routing).
     */
    public CrmResponseHandler.CrmApiResponse post(String baseUrl, String path, String accessToken, Object body) {
        String url = baseUrl + path;
        return executePost(url, path, accessToken, body);
    }

    /**
     * Posts using the legacy single BaseUrl (backward compatible).
     */
    public CrmResponseHandler.CrmApiResponse post(String path, String accessToken, Object body) {
        String url = properties.getBaseUrl() + path;
        return executePost(url, path, accessToken, body);
    }

    /**
     * Posts form-urlencoded data (for OAuth login).
     */
    public CrmResponseHandler.CrmApiResponse postForm(String baseUrl, String path, org.springframework.util.MultiValueMap<String, String> formData) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        TraceHeaderInjector.inject(headers);
        HttpEntity<org.springframework.util.MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        log.info("CRM POST form request: url={}, body={}", url, formData);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("CRM POST form response: url={}, status={}, body={}",
                    url, response.getStatusCode(), truncate(response.getBody()));
            return CrmResponseHandler.parse(response.getBody());
        } catch (RuntimeException e) {
            log.error("CRM POST form failed: url={}, error={}", url, e.getMessage(), e);
            return CrmResponseHandler.CrmApiResponse.parseError(e.getMessage());
        }
    }


    /**
     * Posts JSON with a custom Bearer token (for generateToken etc.).
     */
    public CrmResponseHandler.CrmApiResponse postWithAuth(String baseUrl, String path, String accessToken, String jsonBody) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        TraceHeaderInjector.inject(headers);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
        log.info("CRM POST with auth request: url={}, body={}", url, jsonBody);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("CRM POST with auth response: url={}, status={}, body={}",
                    url, response.getStatusCode(), truncate(response.getBody()));
            return CrmResponseHandler.parse(response.getBody());
        } catch (RuntimeException e) {
            log.error("CRM POST with auth failed: url={}, body={}, error={}", url, jsonBody, e.getMessage(), e);
            return CrmResponseHandler.CrmApiResponse.parseError(e.getMessage());
        }
    }

    private CrmResponseHandler.CrmApiResponse executePost(String url, String path, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        TraceHeaderInjector.inject(headers);

        HttpEntity<Object> request = new HttpEntity<>(body, headers);
        String bodyJson = toJson(body);
        log.info("CRM POST request: url={}, body={}", url, bodyJson);

        int attempt = 0;
        while (true) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
                log.info("CRM POST response: path={}, status={}, body={}",
                        path, response.getStatusCode(), truncate(response.getBody()));
                return CrmResponseHandler.parse(response.getBody());
            } catch (RuntimeException e) {
                if (isRetryable(e) && attempt < properties.getMaxRetries()) {
                    attempt++;
                    long delay = Math.min(properties.getRetryBaseDelayMs() * (1L << (attempt - 1)),
                            properties.getRetryMaxDelayMs());
                    log.warn("CRM request failed (attempt {}/{}), retrying in {}ms: url={}, error={}",
                            attempt, properties.getMaxRetries(), delay, url, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return CrmResponseHandler.CrmApiResponse.parseError("Interrupted during retry");
                    }
                } else {
                    log.error("CRM request failed after {} attempts: url={}, body={}, error={}",
                            attempt, url, bodyJson, e.getMessage(), e);
                    return CrmResponseHandler.CrmApiResponse.parseError(e.getMessage());
                }
            }
        }
    }

    private boolean isRetryable(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        return msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504")
                || msg.contains("timeout") || msg.contains("connect") || msg.contains("Connection refused");
    }

    /** 将 body 对象序列化为 JSON 字符串用于日志；序列化失败时返回 toString。 */
    private String toJson(Object body) {
        if (body == null) return "null";
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException | RuntimeException e) {
            return String.valueOf(body);
        }
    }

    /** 截断 body 到最大长度，避免超大响应刷爆日志。 */
    private String truncate(String value) {
        if (value == null) return "null";
        return value.length() <= LOG_BODY_MAX_LEN ? value : value.substring(0, LOG_BODY_MAX_LEN) + "...(truncated " + value.length() + " chars)";
    }
}
