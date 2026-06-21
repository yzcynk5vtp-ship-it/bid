package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CrmEmployeeService {

    private static final Logger log = LoggerFactory.getLogger(CrmEmployeeService.class);

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmEmployeeService(CrmHttpClient httpClient, CrmAuthService authService,
                              CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    public CrmResponseHandler.CrmApiResponse getEmployeeByToken(String employeeToken) {
        log.info("CRM getEmployeeByToken request: tokenLength={}", employeeToken != null ? employeeToken.length() : 0);
        String token = authService.getValidOssToken();
        String baseUrl = properties.getEffectiveAuthBaseUrl();
        String path = properties.getAuth().getEmployeePath();
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token,
                Map.of("token", employeeToken));
        log.info("CRM getEmployeeByToken response: code={}, msg={}", response.code(), response.msg());
        return response;
    }
}
