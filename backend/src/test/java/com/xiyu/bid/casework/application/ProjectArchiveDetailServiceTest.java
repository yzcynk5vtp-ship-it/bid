// Input: ProjectArchiveDetailService.getArchiveDetail 取值行为
// Output: Mockito 单元测试 — 验证「投标负责人」从 ProjectLeadAssignment.primaryLeadUserId 解析，不再取 Tender.biddingPersonName
// Pos: backend test source - CO-421 回归
package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.dto.ProjectArchiveDetailResponse;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ArchiveLogRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
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
    @Mock private ProjectLeadAssignmentRepository leadAssignmentRepository;
    @Mock private UserRepository userRepository;

    private ProjectArchiveDetailService service;

    @BeforeEach
    void setUp() {
        service = new ProjectArchiveDetailService(
                archiveRepository, fileRepository, logRepository,
                projectRepository, tenderRepository,
                leadAssignmentRepository, userRepository);
        lenient().when(fileRepository.findByArchiveIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        lenient().when(logRepository.findByArchiveIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
    }

    @Test
    void getArchiveDetail_returnsPrimaryLeadUserName_notTenderBiddingPerson() {
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
        ProjectLeadAssignment lead = ProjectLeadAssignment.builder()
                .projectId(100L)
                .primaryLeadUserId(99L)
                .build();
        User leadUser = User.builder().id(99L).fullName("张三").build();

        when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(tenderRepository.findById(10L)).thenReturn(Optional.of(tender));
        when(leadAssignmentRepository.findByProjectId(100L)).thenReturn(Optional.of(lead));
        when(userRepository.findById(99L)).thenReturn(Optional.of(leadUser));

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(1L);

        assertThat(resp.bidManager()).isEqualTo("张三");
        assertThat(resp.projectManager()).isEqualTo("李项目经理");
    }

    @Test
    void getArchiveDetail_returnsNullBidManager_whenNoLeadAssignment() {
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
        when(leadAssignmentRepository.findByProjectId(100L)).thenReturn(Optional.empty());
        lenient().when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(1L);

        assertThat(resp.bidManager()).isNull();
    }

    @Test
    void getArchiveDetail_returnsNullBidManager_whenPrimaryLeadUserIdIsNull() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(1L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        ProjectLeadAssignment lead = ProjectLeadAssignment.builder()
                .projectId(100L)
                .primaryLeadUserId(null)
                .build();

        when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        lenient().when(tenderRepository.findById(10L)).thenReturn(Optional.empty());
        when(leadAssignmentRepository.findByProjectId(100L)).thenReturn(Optional.of(lead));

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(1L);

        assertThat(resp.bidManager()).isNull();
    }
}
