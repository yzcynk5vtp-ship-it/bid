package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.task.dto.TaskBoardItemDTO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TaskBoardItemMapper 纯核心测试。
 * 验证状态映射、字段映射和可见性判断。
 */
class TaskBoardItemMapperTest {

    @Test
    void mapTaskStatus_inProgress_mapsToTodo() {
        Task task = Task.builder().id(1L).projectId(10L).status(Task.Status.IN_PROGRESS).assigneeId(100L).build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人");
        assertThat(dto.getStatus()).isEqualTo("TODO");
    }

    @Test
    void mapTaskStatus_todo_mapsToTodo() {
        Task task = Task.builder().id(1L).projectId(10L).status(Task.Status.TODO).assigneeId(100L).build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人");
        assertThat(dto.getStatus()).isEqualTo("TODO");
    }

    @Test
    void mapTaskStatus_review_mapsToReview() {
        Task task = Task.builder().id(1L).projectId(10L).status(Task.Status.REVIEW).assigneeId(100L).build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人");
        assertThat(dto.getStatus()).isEqualTo("REVIEW");
    }

    @Test
    void mapTaskStatus_completed_mapsToCompleted() {
        Task task = Task.builder().id(1L).projectId(10L).status(Task.Status.COMPLETED).assigneeId(100L).build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人");
        assertThat(dto.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void mapTaskStatus_null_mapsToTodo() {
        Task task = Task.builder().id(1L).projectId(10L).status(null).assigneeId(100L).build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人");
        assertThat(dto.getStatus()).isEqualTo("TODO");
    }

    @Test
    void fromTask_mapsAssigneeId() {
        Task task = Task.builder().id(1L).projectId(10L).status(Task.Status.TODO).assigneeId(42L).build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人");
        assertThat(dto.getAssigneeId()).isEqualTo(42L);
    }

    @Test
    void fromBidReview_mapsReviewerId() {
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder()
                .id(20L).projectId(10L).reviewerId(99L).submittedBy(2L).status("REVIEWING").build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromBidReview(review, Map.of(10L, "项目"), Map.of(2L, "提交人"));
        assertThat(dto.getReviewerId()).isEqualTo(99L);
    }

    @Test
    void isVisibleTask_cancelled_returnsFalse() {
        Task cancelled = Task.builder().id(1L).status(Task.Status.CANCELLED).build();
        assertThat(TaskBoardItemMapper.isVisibleTask(cancelled)).isFalse();
    }

    @Test
    void isVisibleTask_normal_returnsTrue() {
        Task normal = Task.builder().id(1L).status(Task.Status.TODO).build();
        assertThat(TaskBoardItemMapper.isVisibleTask(normal)).isTrue();
    }
}
