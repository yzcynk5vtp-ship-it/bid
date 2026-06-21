package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CrmMenuService {

    private static final Logger log = LoggerFactory.getLogger(CrmMenuService.class);

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmMenuService(CrmHttpClient httpClient, CrmAuthService authService,
                          CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    public CrmResponseHandler.CrmApiResponse getMenuTree(String systemType) {
        log.info("CRM getMenuTree request: systemType={}", systemType);
        String token = authService.getValidOssToken();
        String baseUrl = properties.getEffectiveAuthBaseUrl();
        String path = properties.getAuth().getMenuTreePath();
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token,
                Map.of("systemType", systemType));
        log.info("CRM getMenuTree response: systemType={}, code={}, msg={}", systemType, response.code(), response.msg());
        return response;
    }
}
