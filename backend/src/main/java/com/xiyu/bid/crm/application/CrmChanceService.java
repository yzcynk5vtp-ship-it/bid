package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoSyncDTO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceDTO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChancePageRequest;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceSearchByTenderRequest;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CRM 商机查询与标讯回传应用服务。
 * <p>职责：
 * <ul>
 *   <li>商机列表查询（代理客户 POST /customer-chance/page-list）</li>
 *   <li>标讯回传（代理客户 POST /customer-chance/bidInfoSync）</li>
 * </ul>
 * <p>副作用层：负责取 Token、调用 HTTP、解析响应、异常处理。
 */
@Service
public class CrmChanceService {

    private static final Logger log = LoggerFactory.getLogger(CrmChanceService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmChanceService(CrmHttpClient httpClient, CrmAuthService authService,
                            CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 查询 CRM 商机列表（分页）。
     *
     * @param request 分页查询条件
     * @return 分页结果，含商机列表和分页信息；查询失败返回空列表
     */
    public CrmChancePageResult pageList(CustomerChancePageRequest request) {
        String token = authService.getValidToken();
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getChance().getPageListPath();
        log.info("CRM chance page-list request: baseUrl={}, path={}, body={}", baseUrl, path, request);
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, request);

        if (response.isUnauthorized()) {
            authService.handleUnauthorized();
            token = authService.getValidToken();
            response = httpClient.post(baseUrl, path, token, request);
        }

        if (!response.success() || response.data() == null) {
            log.warn("CRM chance page-list failed: code={}, msg={}", response.code(), response.msg());
            return new CrmChancePageResult(Collections.emptyList(), 0, 0, 0);
        }

        log.info("CRM chance page-list response: code={}, msg={}, dataSnippet={}",
                response.code(), response.msg(),
                response.data().toString().substring(0, Math.min(500, response.data().toString().length())));
        return parsePageResponse(response.data());
    }

    /**
     * 按标讯信息查询 CRM 商机（产品蓝图匹配规则）。
     * <p>蓝图要求按「招标主体 + 报名截止时间 + 开标时间」组合精确匹配。
     * 真实 CRM 仅支持按 groupName（集团名称）和 evaluationTime（评标时间）过滤，
     * 因此实现为：groupName 精确匹配招标主体，并分别按报名截止时间、开标时间
     * 精确匹配 evaluationTime，最后合并去重。
     *
     * @param request 标讯查询条件
     * @return 合并后的分页结果；查询失败或无招标主体时返回空列表
     */
    public CrmChancePageResult searchByTender(CustomerChanceSearchByTenderRequest request) {
        String tenderer = request.tenderer();
        if (tenderer == null || tenderer.isBlank()) {
            log.debug("searchByTender skipped: tenderer is blank");
            return new CrmChancePageResult(Collections.emptyList(), 0, 0, 0);
        }

        List<LocalDate> targetDates = parseTargetDates(request.registrationDeadline(), request.bidOpeningTime());
        if (targetDates.isEmpty()) {
            log.debug("searchByTender skipped: no valid dates for tenderer={}", tenderer);
            return new CrmChancePageResult(Collections.emptyList(), 0, 0, 0);
        }

        int pageSize = Math.max(1, request.pageSize());
        String token = authService.getValidToken();
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getChance().getPageListPath();

        Map<Long, CustomerChanceVO> merged = new LinkedHashMap<>();
        for (LocalDate targetDate : targetDates) {
            CustomerChancePageRequest pageRequest = buildExactDateRequest(
                    tenderer, targetDate, request.pageIndex(), pageSize);
            CrmChancePageResult result = doPageList(token, baseUrl, path, pageRequest);
            for (CustomerChanceVO vo : result.list()) {
                merged.putIfAbsent(vo.id(), vo);
            }
        }

        List<CustomerChanceVO> list = merged.values().stream()
                .sorted(Comparator.comparing(CustomerChanceVO::id))
                .collect(Collectors.toList());
        return new CrmChancePageResult(list, list.size(), pageSize, request.pageIndex());
    }

    private CrmChancePageResult doPageList(String token, String baseUrl, String path,
                                           CustomerChancePageRequest request) {
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, request);

        if (response.isUnauthorized()) {
            authService.handleUnauthorized();
            token = authService.getValidToken();
            response = httpClient.post(baseUrl, path, token, request);
        }

        if (!response.success() || response.data() == null) {
            log.warn("CRM chance page-list failed: code={}, msg={}", response.code(), response.msg());
            return new CrmChancePageResult(Collections.emptyList(), 0, 0, 0);
        }
        return parsePageResponse(response.data());
    }

    private List<LocalDate> parseTargetDates(String registrationDeadline, String bidOpeningTime) {
        List<LocalDate> dates = new ArrayList<>();
        parseDate(registrationDeadline).ifPresent(dates::add);
        parseDate(bidOpeningTime).ifPresent(dates::add);
        return dates.stream().distinct().collect(Collectors.toList());
    }

    private java.util.Optional<LocalDate> parseDate(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        String trimmed = value.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ISO_LOCAL_DATE
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                if (trimmed.length() > 10) {
                    return java.util.Optional.of(LocalDateTime.parse(trimmed, formatter).toLocalDate());
                } else {
                    return java.util.Optional.of(LocalDate.parse(trimmed, formatter));
                }
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        log.warn("Unable to parse date value: {}", value);
        return java.util.Optional.empty();
    }

    private CustomerChancePageRequest buildExactDateRequest(String tenderer, LocalDate targetDate,
                                                            int pageIndex, int pageSize) {
        String start = targetDate.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end = targetDate.atTime(23, 59, 59).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        CustomerChanceDTO body = new CustomerChanceDTO(
                List.of(tenderer), null, null, null, null, null, null,
                start, end, null, null, null, null, null, null, null, null);
        return new CustomerChancePageRequest(pageIndex, pageSize, body);
    }

    /**
     * 回传标讯状态到 CRM。
     *
     * @param bidInfoSync 标讯回传请求
     * @return true 回传成功，false 回传失败
     */
    public boolean bidInfoSync(BidInfoSyncDTO bidInfoSync) {
        String token = authService.getValidToken();
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getChance().getBidInfoSyncPath();
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, bidInfoSync);

        if (response.isUnauthorized()) {
            authService.handleUnauthorized();
            token = authService.getValidToken();
            response = httpClient.post(baseUrl, path, token, bidInfoSync);
        }

        if (!response.success()) {
            log.warn("CRM bidInfoSync failed: code={}, msg={}", response.code(), response.msg());
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private CrmChancePageResult parsePageResponse(JsonNode data) {
        try {
            int totalCount = data.path("totalCount").asInt(0);
            int pageSize = data.path("pageSize").asInt(0);
            int pageIndex = data.path("pageIndex").asInt(1);
            JsonNode dataListNode = data.path("dataList");

            List<CustomerChanceVO> list;
            if (dataListNode.isArray() && dataListNode.size() > 0) {
                String jsonArray = MAPPER.writeValueAsString(dataListNode);
                CollectionType collectionType = MAPPER.getTypeFactory()
                        .constructCollectionType(List.class, CustomerChanceVO.class);
                list = MAPPER.readValue(jsonArray, collectionType);
            } else {
                list = Collections.emptyList();
            }
            return new CrmChancePageResult(list, totalCount, pageSize, pageIndex);
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Failed to parse CRM chance page response", e);
            return new CrmChancePageResult(Collections.emptyList(), 0, 0, 0);
        }
    }

    /**
     * CRM 商机列表分页查询结果。
     *
     * @param list       商机列表
     * @param totalCount 总记录数
     * @param pageSize   每页大小
     * @param pageIndex  当前页码
     */
    public record CrmChancePageResult(
            List<CustomerChanceVO> list,
            int totalCount,
            int pageSize,
            int pageIndex
    ) {}
}
