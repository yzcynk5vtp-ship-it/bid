package com.xiyu.bid.batch.service;

import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.batch.core.BatchValidationPolicy;
import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.batch.dto.BatchTenderStatusUpdateRequest;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchTenderStatusAppServiceTest {

    private TenderRepository tenderRepository;
    private ProjectRepository projectRepository;
    private ProjectAccessScopeService projectAccessScopeService;
    private ApplicationEventPublisher eventPublisher;
    private BatchTenderStatusAppService service;

    @BeforeEach
    void setUp() {
        tenderRepository = mock(TenderRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectAccessScopeService = mock(ProjectAccessScopeService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        IAuditLogService auditLogService = mock(IAuditLogService.class);
        BatchProjectAccessGuard projectAccessGuard = new BatchProjectAccessGuard(projectAccessScopeService, projectRepository);
        service = new BatchTenderStatusAppService(
                tenderRepository,
                projectAccessGuard,
                new BatchOperationLogService(auditLogService),
                eventPublisher
        );
    }

    @Test
    void shouldReturnPartialFailureWhenTransitionIsInvalid() {
        Tender pending = Tender.builder().id(1L).status(Tender.Status.PENDING_ASSIGNMENT).build();
        Tender bidded = Tender.builder().id(2L).status(Tender.Status.BIDDING).build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(tenderRepository.findById(2L)).thenReturn(Optional.of(bidded));
        when(tenderRepository.saveAll(anyList())).thenReturn(List.of(pending));

        BatchTenderStatusUpdateRequest request = new BatchTenderStatusUpdateRequest();
        request.setTenderIds(List.of(1L, 2L));
        request.setStatus("TRACKING");

        var response = service.batchUpdateStatus(request, null);

        assertFalse(response.getSuccess());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals(Tender.Status.TRACKING, pending.getStatus());
        assertEquals("INVALID_STATUS_TRANSITION", response.getErrors().get(0).getErrorCode());
    }

    @Test
    void shouldTreatSameStatusAsIdempotentSuccess() {
        Tender tracking = Tender.builder().id(1L).status(Tender.Status.TRACKING).build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tracking));

        BatchTenderStatusUpdateRequest request = new BatchTenderStatusUpdateRequest();
        request.setTenderIds(List.of(1L));
        request.setStatus("TRACKING");

        var response = service.batchUpdateStatus(request, null);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getSuccessCount());
        verify(tenderRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldRejectTenderLinkedToProjectOutsideDataScope() {
        Tender pending = Tender.builder().id(1L).status(Tender.Status.PENDING_ASSIGNMENT).build();
        Project project = Project.builder().id(10L).tenderId(1L).build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(projectRepository.findByTenderId(1L)).thenReturn(List.of(project));
        doThrow(new org.springframework.security.access.AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(10L);

        BatchTenderStatusUpdateRequest request = new BatchTenderStatusUpdateRequest();
        request.setTenderIds(List.of(1L));
        request.setStatus("TRACKING");

        var response = service.batchUpdateStatus(request, null);

        assertFalse(response.getSuccess());
        assertEquals(1, response.getFailureCount());
        assertEquals("PERMISSION_DENIED", response.getErrors().get(0).getErrorCode());
        verify(tenderRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldPublishTenderStatusChangedEventWhenStatusChanges() {
        Tender bidded = Tender.builder().id(1L).status(Tender.Status.BIDDING)
                .externalId("SRC:123").title("Tender-1").build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(bidded));
        when(tenderRepository.saveAll(anyList())).thenReturn(List.of(bidded));

        BatchTenderStatusUpdateRequest request = new BatchTenderStatusUpdateRequest();
        request.setTenderIds(List.of(1L));
        request.setStatus("WON");

        service.batchUpdateStatus(request, null);

        ArgumentCaptor<TenderStatusChangedEvent> captor =
                ArgumentCaptor.forClass(TenderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(Tender.Status.BIDDING, captor.getValue().oldStatus());
        assertEquals(Tender.Status.WON, captor.getValue().newStatus());
        assertEquals(1L, captor.getValue().tenderId());
    }

    @Test
    void shouldNotPublishEventWhenStatusUnchanged() {
        Tender tracking = Tender.builder().id(1L).status(Tender.Status.TRACKING).build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tracking));

        BatchTenderStatusUpdateRequest request = new BatchTenderStatusUpdateRequest();
        request.setTenderIds(List.of(1L));
        request.setStatus("TRACKING");

        service.batchUpdateStatus(request, null);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
