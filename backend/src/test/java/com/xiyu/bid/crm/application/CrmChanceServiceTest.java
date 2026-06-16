package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChancePageRequest;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceSearchByTenderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrmChanceServiceTest {

    @Mock
    private CrmHttpClient httpClient;

    @Mock
    private CrmAuthService authService;

    private CrmProperties properties;

    private CrmChanceService service;

    @BeforeEach
    void setUp() {
        properties = new CrmProperties();
        properties.setBaseUrl("http://crm.example.com");
        service = new CrmChanceService(httpClient, authService, properties);
        when(authService.getValidToken()).thenReturn("token");
    }

    @Test
    void searchByTender_withoutTenderer_returnsEmpty() {
        CustomerChanceSearchByTenderRequest request = new CustomerChanceSearchByTenderRequest(
                "", "2026-08-08 23:59:00", "2026-09-18 23:59:00", 1, 10);

        CrmChanceService.CrmChancePageResult result = service.searchByTender(request);

        assertThat(result.list()).isEmpty();
        assertThat(result.totalCount()).isZero();
    }

    @Test
    void searchByTender_withoutValidDates_returnsEmpty() {
        CustomerChanceSearchByTenderRequest request = new CustomerChanceSearchByTenderRequest(
                "山东海化集团有限公司", "", "", 1, 10);

        CrmChanceService.CrmChancePageResult result = service.searchByTender(request);

        assertThat(result.list()).isEmpty();
    }

    @Test
    void searchByTender_queriesGroupNameAndTwoExactEvaluationDates() {
        String tenderer = "山东海化集团有限公司";
        String registrationDeadline = "2026-06-03 23:59:00";
        String bidOpeningTime = "2026-06-04 23:59:00";
        CustomerChanceSearchByTenderRequest request = new CustomerChanceSearchByTenderRequest(
                tenderer, registrationDeadline, bidOpeningTime, 1, 10);

        String responseBody = """
                {"code":0,"totalCount":1,"pageSize":10,"pageIndex":1,"dataList":[\
                {"id":1,"code":"CC1","name":"商机1","groupName":"山东海化集团有限公司","evaluationTime":"2026-06-03"}\
                ]}
                """;
        CrmResponseHandler.CrmApiResponse response = CrmResponseHandler.parse(responseBody);
        when(httpClient.post(any(), any(), any(), any(CustomerChancePageRequest.class)))
                .thenReturn(response);

        CrmChanceService.CrmChancePageResult result = service.searchByTender(request);

        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).code()).isEqualTo("CC1");

        ArgumentCaptor<CustomerChancePageRequest> captor = ArgumentCaptor.forClass(CustomerChancePageRequest.class);
        verify(httpClient, times(2)).post(any(), any(), eq("token"), captor.capture());

        var calls = captor.getAllValues();
        // 第一次按报名截止时间（2026-06-03）查询
        assertThat(calls.get(0).body().groupName()).containsExactly(tenderer);
        assertThat(calls.get(0).body().evaluationStartTime()).startsWith("2026-06-03 00:00:00");
        assertThat(calls.get(0).body().evaluationEndTime()).startsWith("2026-06-03 23:59:59");
        // 第二次按开标时间（2026-06-04）查询
        assertThat(calls.get(1).body().groupName()).containsExactly(tenderer);
        assertThat(calls.get(1).body().evaluationStartTime()).startsWith("2026-06-04 00:00:00");
        assertThat(calls.get(1).body().evaluationEndTime()).startsWith("2026-06-04 23:59:59");
    }

    @Test
    void searchByTender_deduplicatesByIdAndKeepsEvaluationDateMatch() {
        String tenderer = "山东海化集团有限公司";
        CustomerChanceSearchByTenderRequest request = new CustomerChanceSearchByTenderRequest(
                tenderer, "2026-06-03 23:59:00", "2026-06-03 23:59:00", 1, 10);

        // 两个日期相同，CRM 返回同一条两次（实际不会出现，但验证去重）
        String responseBody = """
                {"code":0,"totalCount":2,"pageSize":10,"pageIndex":1,"dataList":[\
                {"id":1,"code":"CC1","name":"商机1","groupName":"山东海化集团有限公司","evaluationTime":"2026-06-03"},\
                {"id":1,"code":"CC1","name":"商机1","groupName":"山东海化集团有限公司","evaluationTime":"2026-06-03"}\
                ]}
                """;
        when(httpClient.post(any(), any(), any(), any(CustomerChancePageRequest.class)))
                .thenReturn(CrmResponseHandler.parse(responseBody));

        CrmChanceService.CrmChancePageResult result = service.searchByTender(request);

        assertThat(result.list()).hasSize(1);
        verify(httpClient, times(1)).post(any(), any(), any(), any(CustomerChancePageRequest.class));
    }
}
