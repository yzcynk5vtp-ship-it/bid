package com.xiyu.bid.tender.service;

import com.xiyu.bid.crm.application.CrmCompanySearchService;
import com.xiyu.bid.crm.application.CrmCustomerManagerLookupService;
import com.xiyu.bid.crm.application.CompanySearchResult;
import com.xiyu.bid.crm.application.CustomerManagerResult;
import com.xiyu.bid.crm.domain.CrmProjectMapping;
import com.xiyu.bid.crm.domain.CrmProjectMappingRepository;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TenderAutoAssignmentServiceTest {

    @Mock
    private CrmProjectMappingRepository mappingRepository;

    @Mock
    private CrmCompanySearchService companySearchService;

    @Mock
    private CrmCustomerManagerLookupService customerManagerLookupService;

    @Mock
    private UserRepository userRepository;

    private TenderAutoAssignmentService autoAssignmentService;
    private Tender tender;

    @BeforeEach
    void setUp() {
        autoAssignmentService = new TenderAutoAssignmentService(
                mappingRepository, companySearchService, customerManagerLookupService, userRepository);
        tender = Tender.builder()
                .id(1L)
                .title("测试标讯")
                .purchaserName("上海西域采购中心")
                .budget(new BigDecimal("150.00"))
                .publishDate(LocalDate.now())
                .deadline(LocalDateTime.now().plusDays(20))
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();
    }

    @Test
    @DisplayName("根据业主单位匹配成功 - 返回负责人信息")
    void tryAutoAssign_MatchFound_ShouldReturnManagerInfo() {
        CrmProjectMapping mapping = CrmProjectMapping.builder()
                .id(1L)
                .purchaserName("上海西域采购中心")
                .crmProjectId("CRM-001")
                .projectManagerId("PM-001")
                .projectManagerName("张三")
                .departmentId("DEPT-001")
                .departmentName("销售部")
                .build();

        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.of(mapping));

        AssignmentResult result = autoAssignmentService.tryAutoAssign(tender);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.crmProjectId()).isEqualTo("CRM-001");
        assertThat(result.projectManagerId()).isEqualTo("PM-001");
        assertThat(result.projectManagerName()).isEqualTo("张三");
        assertThat(result.departmentId()).isEqualTo("DEPT-001");
        assertThat(result.departmentName()).isEqualTo("销售部");
        verify(mappingRepository).findByPurchaserName("上海西域采购中心");
    }

    @Test
    @DisplayName("根据业主单位匹配失败 - 返回 noMatch")
    void tryAutoAssign_NoMatch_ShouldReturnNoMatch() {
        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.empty());

        AssignmentResult result = autoAssignmentService.tryAutoAssign(tender);

        assertThat(result.isMatched()).isFalse();
        assertThat(result.crmProjectId()).isNull();
        assertThat(result.projectManagerId()).isNull();
        assertThat(result.projectManagerName()).isNull();
    }

    @Test
    @DisplayName("purchaserName 为空 - 返回 noMatch")
    void tryAutoAssign_BlankPurchaserName_ShouldReturnNoMatch() {
        tender.setPurchaserName(null);

        AssignmentResult result = autoAssignmentService.tryAutoAssign(tender);

        assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("tender 为空 - 返回 noMatch")
    void tryAutoAssign_NullTender_ShouldReturnNoMatch() {
        AssignmentResult result = autoAssignmentService.tryAutoAssign(null);

        assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("purchaserName 前后有空格 - 自动 trim 后匹配")
    void tryAutoAssign_TrimmedPurchaserName_ShouldMatch() {
        tender.setPurchaserName("  上海西域采购中心  ");
        CrmProjectMapping mapping = CrmProjectMapping.builder()
                .purchaserName("上海西域采购中心")
                .projectManagerName("李四")
                .build();

        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.of(mapping));

        AssignmentResult result = autoAssignmentService.tryAutoAssign(tender);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.projectManagerName()).isEqualTo("李四");
    }

    // ── CO-302: CRM 反查路径测试（两步链路：25338 → 25259）─────────────────────────────────

    @Test
    @DisplayName("autoAssignIfPossible_本地映射匹配成功_不调用CRM两步链路")
    void autoAssignIfPossible_LocalMatch_ShouldNotCallCrm() {
        CrmProjectMapping mapping = CrmProjectMapping.builder()
                .purchaserName("上海西域采购中心")
                .projectManagerName("王五")
                .projectManagerId("10086")
                .build();

        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.of(mapping));

        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.projectManagerName()).isEqualTo("王五");
        assertThat(result.projectManagerId()).isEqualTo("10086");
        verify(companySearchService, never()).searchByName(anyString());
        verify(customerManagerLookupService, never()).findByCompanyId(anyLong());
    }

    @Test
    @DisplayName("autoAssignIfPossible_本地匹配失败_25338命中_25259命中_本地User表按工号补齐姓名")
    void autoAssignIfPossible_LocalNoMatch_BothCrmStepsHit_ShouldReturnManager() {
        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.empty());
        when(companySearchService.searchByName("上海西域采购中心"))
                .thenReturn(Optional.of(new CompanySearchResult(100L, "上海西域采购中心", "西域集团")));
        when(customerManagerLookupService.findByCompanyId(100L))
                .thenReturn(Optional.of(new CustomerManagerResult("01097", 16, "百大项目负责人")));
        User manager = User.builder()
                .id(2001L)
                .fullName("李四")
                .employeeNumber("01097")
                .build();
        when(userRepository.findByEmployeeNumber("01097"))
                .thenReturn(Optional.of(manager));

        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);

        assertThat(result.isMatched()).isTrue();
        // saleNo 作为 projectManagerId（工号是稳定唯一标识）
        assertThat(result.projectManagerId()).isEqualTo("01097");
        // 姓名通过本地 User 表按工号反查补齐
        assertThat(result.projectManagerName()).isEqualTo("李四");
        verify(companySearchService).searchByName("上海西域采购中心");
        verify(customerManagerLookupService).findByCompanyId(100L);
        verify(userRepository).findByEmployeeNumber("01097");
    }

    @Test
    @DisplayName("autoAssignIfPossible_本地匹配失败_25338命中_25259未命中_返回noMatch")
    void autoAssignIfPossible_CompanyHitButNoManager_ShouldReturnNoMatch() {
        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.empty());
        when(companySearchService.searchByName("上海西域采购中心"))
                .thenReturn(Optional.of(new CompanySearchResult(100L, "上海西域采购中心", "西域集团")));
        when(customerManagerLookupService.findByCompanyId(100L))
                .thenReturn(Optional.empty());

        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);

        assertThat(result.isMatched()).isFalse();
        verify(companySearchService).searchByName("上海西域采购中心");
        verify(customerManagerLookupService).findByCompanyId(100L);
        // 25259 未命中时不应查 User 表
        verify(userRepository, never()).findByEmployeeNumber(anyString());
    }

    @Test
    @DisplayName("autoAssignIfPossible_本地匹配失败_25338未命中_返回noMatch_不调25259")
    void autoAssignIfPossible_CompanyNoHit_ShouldReturnNoMatchWithoutCallingSecondStep() {
        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.empty());
        when(companySearchService.searchByName("上海西域采购中心"))
                .thenReturn(Optional.empty());

        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);

        assertThat(result.isMatched()).isFalse();
        verify(companySearchService).searchByName("上海西域采购中心");
        verify(customerManagerLookupService, never()).findByCompanyId(anyLong());
    }

    @Test
    @DisplayName("autoAssignIfPossible_25338异常_降级为noMatch")
    void autoAssignIfPossible_CompanySearchException_ShouldReturnNoMatch() {
        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.empty());
        when(companySearchService.searchByName("上海西域采购中心"))
                .thenThrow(new RuntimeException("CRM 25338 timeout"));

        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);

        assertThat(result.isMatched()).isFalse();
        verify(customerManagerLookupService, never()).findByCompanyId(anyLong());
    }

    @Test
    @DisplayName("autoAssignIfPossible_25259异常_降级为noMatch")
    void autoAssignIfPossible_ManagerLookupException_ShouldReturnNoMatch() {
        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.empty());
        when(companySearchService.searchByName("上海西域采购中心"))
                .thenReturn(Optional.of(new CompanySearchResult(100L, "上海西域采购中心", "西域集团")));
        when(customerManagerLookupService.findByCompanyId(100L))
                .thenThrow(new RuntimeException("CRM 25259 timeout"));

        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);

        assertThat(result.isMatched()).isFalse();
        verify(userRepository, never()).findByEmployeeNumber(anyString());
    }

    @Test
    @DisplayName("autoAssignIfPossible_25259命中但本地User表无此工号_仍匹配成功_name为null")
    void autoAssignIfPossible_ManagerNotFoundInLocalUserTable_ShouldStillMatchWithNameNull() {
        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.empty());
        when(companySearchService.searchByName("上海西域采购中心"))
                .thenReturn(Optional.of(new CompanySearchResult(100L, "上海西域采购中心", "西域集团")));
        when(customerManagerLookupService.findByCompanyId(100L))
                .thenReturn(Optional.of(new CustomerManagerResult("99999", 16, "百大项目负责人")));
        when(userRepository.findByEmployeeNumber("99999"))
                .thenReturn(Optional.empty());

        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);

        // 工号匹配成功，即使本地 User 表查不到姓名，仍视为匹配（工号是稳定标识）
        assertThat(result.isMatched()).isTrue();
        assertThat(result.projectManagerId()).isEqualTo("99999");
        assertThat(result.projectManagerName()).isNull();
    }

    @Test
    @DisplayName("tryAutoAssignFromCrm_purchaserName为空_返回noMatch")
    void tryAutoAssignFromCrm_BlankPurchaserName_ShouldReturnNoMatch() {
        tender.setPurchaserName(null);

        AssignmentResult result = autoAssignmentService.tryAutoAssignFromCrm(tender);

        assertThat(result.isMatched()).isFalse();
        verify(companySearchService, never()).searchByName(anyString());
    }

    @Test
    @DisplayName("tryAutoAssignFromCrm_25338和25259都命中_按工号补齐姓名_匹配成功")
    void tryAutoAssignFromCrm_BothStepsHit_ShouldReturnMatched() {
        when(companySearchService.searchByName("上海西域采购中心"))
                .thenReturn(Optional.of(new CompanySearchResult(100L, "上海西域采购中心", "西域集团")));
        when(customerManagerLookupService.findByCompanyId(100L))
                .thenReturn(Optional.of(new CustomerManagerResult("01097", 16, "百大项目负责人")));
        User manager = User.builder()
                .id(2001L)
                .fullName("王五")
                .employeeNumber("01097")
                .build();
        when(userRepository.findByEmployeeNumber("01097"))
                .thenReturn(Optional.of(manager));

        AssignmentResult result = autoAssignmentService.tryAutoAssignFromCrm(tender);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.projectManagerId()).isEqualTo("01097");
        assertThat(result.projectManagerName()).isEqualTo("王五");
    }

    @Test
    @DisplayName("tryAutoAssignFromCrm_25338返回empty_返回noMatch")
    void tryAutoAssignFromCrm_CompanySearchEmpty_ShouldReturnNoMatch() {
        when(companySearchService.searchByName("上海西域采购中心"))
                .thenReturn(Optional.empty());

        AssignmentResult result = autoAssignmentService.tryAutoAssignFromCrm(tender);

        assertThat(result.isMatched()).isFalse();
        verify(customerManagerLookupService, never()).findByCompanyId(anyLong());
    }

    @Test
    @DisplayName("autoAssignIfPossible_tender 为空返回 noMatch")
    void autoAssignIfPossible_NullTender_ShouldReturnNoMatch() {
        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(null);

        assertThat(result.isMatched()).isFalse();
    }
}
