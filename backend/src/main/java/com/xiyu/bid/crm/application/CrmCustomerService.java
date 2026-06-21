package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CrmCustomerService {

    private static final Logger log = LoggerFactory.getLogger(CrmCustomerService.class);

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmCustomerService(CrmHttpClient httpClient, CrmAuthService authService,
                              CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    public CrmResponseHandler.CrmApiResponse searchCustomers(String keyword, int pageSize) {
        log.info("CRM searchCustomers request: keyword={}, pageSize={}", keyword, pageSize);
        String token = authService.getValidToken();
        Map<String, Object> body = Map.of("keyword", keyword, "pageSize", Math.min(pageSize, 20));
        String baseUrl = properties.getEffectiveCustomerBaseUrl();
        String path = properties.getCustomer().getSearchPath();
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, body);

        if (response.isUnauthorized()) {
            authService.handleUnauthorized();
            token = authService.getValidToken();
            response = httpClient.post(baseUrl, path, token, body);
        }
        log.info("CRM searchCustomers response: keyword={}, code={}, msg={}",
                keyword, response.code(), response.msg());
        return response;
    }

    public CrmResponseHandler.CrmApiResponse getCustomerContacts(List<String> customerIds) {
        log.info("CRM getCustomerContacts request: customerIds={}", customerIds);
        String token = authService.getValidToken();
        String baseUrl = properties.getEffectiveCustomerBaseUrl();
        String path = properties.getCustomer().getContactsPath();
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, Map.of("customerIds", customerIds));
        log.info("CRM getCustomerContacts response: customerIds={}, code={}, msg={}",
                customerIds, response.code(), response.msg());
        return response;
    }
}
