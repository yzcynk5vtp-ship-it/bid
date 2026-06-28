package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * CRM 客户负责人查询服务（CO-302 反查路径第二步）.
 *
 * <p>对应接口 25259 {@code POST /customerManager/getCustomerManagerListByCompanyId}：
 * 按公司 ID 查询客户负责人列表。
 *
 * <p><b>Host 注意</b>：该接口部署在 CAC 服务（{@code cacBaseUrl}）上，
 * 不是 {@code customerBaseUrl}。详见 {@link CrmProperties#getEffectiveCacBaseUrl()}。
 *
 * <p>CO-302 issue 5.3 要求"取该客户负责人中的某个角色为标讯的项目负责人"。
 * 当前实现取返回列表中第一条有效（saleNo 非空）的负责人，issue 未强约束
 * 具体 saleType，未来业务明确后可加过滤。
 *
 * <p>注意：本接口返回的 {@code code} 是 <strong>string 类型 "0"</strong>，
 * 与 25338 的 integer 0 不同；{@code CrmResponseHandler.parse} 用
 * {@code asInt(-1)} 解析，Jackson 对 numeric string 可正确转换为 0。
 *
 * <p>降级策略：查询失败、未找到均返回 {@link Optional#empty()}，
 * 不抛异常，由调用方决定后续行为。
 */
@Service
public class CrmCustomerManagerLookupService {

    private static final Logger log = LoggerFactory.getLogger(CrmCustomerManagerLookupService.class);

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmCustomerManagerLookupService(
            CrmHttpClient httpClient,
            CrmAuthService authService,
            CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 按公司 ID 查询客户负责人列表，取第一条有效负责人.
     *
     * @param companyId 公司 ID（来自接口 25338 的返回值）
     * @return 第一条有效负责人；empty 表示未查到
     */
    public Optional<CustomerManagerResult> findByCompanyId(Long companyId) {
        if (companyId == null) {
            log.debug("findByCompanyId skipped: companyId is null");
            return Optional.empty();
        }

        String path = properties.getCustomer().getCustomerManagerListPath();
        Object request = buildRequest(companyId);

        try {
            CrmResponseHandler.CrmApiResponse response = httpClient.post(
                    properties.getEffectiveCacBaseUrl(),
                    path,
                    authService.getValidToken(),
                    request
            );

            if (response.code() != 0) {
                log.warn("findByCompanyId: CRM returned code={}, msg={}", response.code(), response.msg());
                return Optional.empty();
            }

            JsonNode first = findFirstValidManager(response.data());
            if (first == null) {
                log.debug("findByCompanyId: no valid manager for companyId={}", companyId);
                return Optional.empty();
            }

            String saleNo = first.path("saleNo").asText("");
            int saleType = first.path("saleType").asInt(0);
            String saleTypeText = first.path("saleTypeText").asText("");

            log.info("findByCompanyId: companyId={}, saleNo={}, saleType={}",
                    companyId, saleNo, saleType);
            return Optional.of(new CustomerManagerResult(saleNo, saleType, saleTypeText));
        } catch (RuntimeException e) {
            log.warn("findByCompanyId failed for companyId={}: {}", companyId, e.getMessage());
            return Optional.empty();
        }
    }

    private Object buildRequest(Long companyId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyIds", String.valueOf(companyId));
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("pageIndex", 0);
        wrapper.put("pageSize", 20);
        wrapper.put("body", body);
        return wrapper;
    }

    /**
     * 在 dataList 中取第一条有效（saleNo 非空）的负责人.
     */
    private JsonNode findFirstValidManager(JsonNode data) {
        if (data == null) {
            return null;
        }
        JsonNode dataList = data.path("dataList");
        if (!dataList.isArray()) {
            return null;
        }
        for (JsonNode node : dataList) {
            String saleNo = node.path("saleNo").asText("");
            if (!saleNo.isBlank()) {
                return node;
            }
        }
        return null;
    }
}
