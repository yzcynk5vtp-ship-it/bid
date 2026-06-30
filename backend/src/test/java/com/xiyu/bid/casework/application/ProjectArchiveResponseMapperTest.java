// Input: ProjectArchiveResponseMapper 列表/Stats 选项取值行为
// Output: Mockito 单元测试 — 验证「投标负责人」从 ProjectLeadAssignment.primaryLeadUserId 解析，不再取 Tender.biddingPersonName
// Pos: backend test source - CO-421 回归
package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.dto.ProjectArchiveResponse;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectArchiveResponseMapperTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private ArchiveFileRepository fileRepository;
    @Mock private ProjectLeadAssignmentRepository leadAssignmentRepository;
    @Mock private UserRepository userRepository;

    private ProjectArchiveResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ProjectArchiveResponseMapper(
                projectRepository, tenderRepository, fileRepository,
                leadAssignmentRepository, userRepository);
    }

    @Test
    void toResponseList_resolvesBidManagerFromPrimaryLeadUserId_notTenderBiddingPerson() {
        // 已立项项目：tender.biddingPersonName="招标平台联系人"（不应被采用）
        //              ProjectLeadAssignment.primaryLeadUserId=99 → User.fullName="张三"（应被采用）
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        Tender tender = Tender.builder()
                .id(10L)
                .projectType("综合")
                .projectManagerName("李项目经理")
                .biddingPersonName("招标平台联系人")
                .purchaserName("招标主体")
                .build();
        ProjectLeadAssignment lead = ProjectLeadAssignment.builder()
                .projectId(100L)
                .primaryLeadUserId(99L)
                .build();
        User leadUser = User.builder().id(99L).fullName("张三").build();

        when(projectRepository.findAllById(List.of(100L))).thenReturn(List.of(project));
        when(tenderRepository.findAllById(List.of(10L))).thenReturn(List.of(tender));
        when(leadAssignmentRepository.findByProjectIdIn(List.of(100L))).thenReturn(List.of(lead));
        when(userRepository.findByIdIn(List.of(99L))).thenReturn(List.of(leadUser));
        lenient().when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of());

        List<ProjectArchiveResponse> result = mapper.toResponseList(List.of(archive));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bidManager()).isEqualTo("张三");
        assertThat(result.get(0).projectManager()).isEqualTo("李项目经理");
    }

    @Test
    void toResponseList_returnsNullBidManager_whenNoLeadAssignment() {
        // 无 ProjectLeadAssignment → bidManager=null（降级策略，不回退 tender）
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        Tender tender = Tender.builder()
                .id(10L)
                .biddingPersonName("招标平台联系人")  // 不应被采用
                .build();

        when(projectRepository.findAllById(List.of(100L))).thenReturn(List.of(project));
        when(tenderRepository.findAllById(List.of(10L))).thenReturn(List.of(tender));
        when(leadAssignmentRepository.findByProjectIdIn(List.of(100L))).thenReturn(List.of());
        lenient().when(userRepository.findByIdIn(anyList())).thenReturn(List.of());
        lenient().when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of());

        List<ProjectArchiveResponse> result = mapper.toResponseList(List.of(archive));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bidManager()).isNull();
    }

    @Test
    void toResponseList_returnsNullBidManager_whenPrimaryLeadUserIdIsNull() {
        // ProjectLeadAssignment 存在但 primaryLeadUserId=null → bidManager=null
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        ProjectLeadAssignment lead = ProjectLeadAssignment.builder()
                .projectId(100L)
                .primaryLeadUserId(null)
                .build();

        when(projectRepository.findAllById(List.of(100L))).thenReturn(List.of(project));
        lenient().when(tenderRepository.findAllById(anyList())).thenReturn(List.of());
        when(leadAssignmentRepository.findByProjectIdIn(List.of(100L))).thenReturn(List.of(lead));
        lenient().when(userRepository.findByIdIn(anyList())).thenReturn(List.of());
        lenient().when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of());

        List<ProjectArchiveResponse> result = mapper.toResponseList(List.of(archive));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bidManager()).isNull();
    }

    @Test
    void collectBidManagers_returnsPrimaryLeadUserNames() {
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        ProjectLeadAssignment lead = ProjectLeadAssignment.builder()
                .projectId(100L)
                .primaryLeadUserId(99L)
                .build();
        User leadUser = User.builder().id(99L).fullName("张三").build();

        when(leadAssignmentRepository.findByProjectIdIn(List.of(100L))).thenReturn(List.of(lead));
        when(userRepository.findByIdIn(List.of(99L))).thenReturn(List.of(leadUser));

        List<String> names = mapper.collectBidManagers(List.of(archive));

        assertThat(names).containsExactly("张三");
    }

    @Test
    void collectBidManagers_emptyWhenNoLeadAssignment() {
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        when(leadAssignmentRepository.findByProjectIdIn(List.of(100L))).thenReturn(List.of());

        List<String> names = mapper.collectBidManagers(List.of(archive));

        assertThat(names).isEmpty();
    }
}
