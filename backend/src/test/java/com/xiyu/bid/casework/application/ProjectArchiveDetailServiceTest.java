// Input: ProjectArchiveDetailService.getArchiveDetail 取值行为
// Output: Mockito 单元测试 — 验证「投标负责人」从 ProjectInitiationDetails.biddingLeaderName 解析
// Pos: backend test source - CO-421 回归
package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.dto.ProjectArchiveDetailResponse;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ArchiveLogRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectArchiveDetailServiceTest {

    @Mock private ProjectArchiveRepository archiveRepository;
    @Mock private ArchiveFileRepository fileRepository;
    @Mock private ArchiveLogRepository logRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private ProjectInitiationDetailsRepository initiationDetailsRepository;

    private ProjectArchiveDetailService service;

    @BeforeEach
    void setUp() {
        service = new ProjectArchiveDetailService(
                archiveRepository, fileRepository, logRepository,
                projectRepository, tenderRepository,
                initiationDetailsRepository);
        lenient().when(fileRepository.findByArchiveIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        lenient().when(logRepository.findByArchiveIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
    }

    @Test
    void getArchiveDetail_returnsBiddingLeaderName_notTenderBiddingPerson() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(1L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        Tender tender = Tender.builder()
                .id(10L)
                .projectType("综合")
                .projectManagerName("李项目经理")
                .biddingPersonName("招标平台联系人")  // 不应被采用
                .purchaserName("招标主体")
                .build();
        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .projectId(100L)
                .biddingLeaderName("张三")
                .build();

        when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(tenderRepository.findById(10L)).thenReturn(Optional.of(tender));
        when(initiationDetailsRepository.findByProjectId(100L)).thenReturn(Optional.of(details));

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(1L);

        assertThat(resp.bidManager()).isEqualTo("张三");
        assertThat(resp.projectManager()).isEqualTo("李项目经理");
    }

    @Test
    void getArchiveDetail_returnsNullBidManager_whenNoInitiationDetails() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(1L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        Tender tender = Tender.builder()
                .id(10L)
                .biddingPersonName("招标平台联系人")  // 不应被采用
                .build();

        when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(tenderRepository.findById(10L)).thenReturn(Optional.of(tender));
        when(initiationDetailsRepository.findByProjectId(100L)).thenReturn(Optional.empty());

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(1L);

        assertThat(resp.bidManager()).isNull();
    }

    @Test
    void getArchiveDetail_returnsNullBidManager_whenBiddingLeaderNameIsBlank() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(1L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .projectId(100L)
                .biddingLeaderName("   ")  // 空白字符串
                .build();

        when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        lenient().when(tenderRepository.findById(10L)).thenReturn(Optional.empty());
        when(initiationDetailsRepository.findByProjectId(100L)).thenReturn(Optional.of(details));

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(1L);

        assertThat(resp.bidManager()).isNull();
    }
}
