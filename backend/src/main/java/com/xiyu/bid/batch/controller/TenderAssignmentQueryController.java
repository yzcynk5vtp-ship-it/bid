package com.xiyu.bid.batch.controller;

import com.xiyu.bid.batch.dto.TenderAssignmentResponse;
import com.xiyu.bid.batch.service.TenderAssignmentQueryService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.user.core.AssignmentContext;
import com.xiyu.bid.user.dto.AssignmentCandidateDTO;
import com.xiyu.bid.user.service.AssignmentCandidateAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TenderAssignmentQueryController {

    private final TenderAssignmentQueryService tenderAssignmentQueryService;
    private final AssignmentCandidateAppService assignmentCandidateAppService;

    @GetMapping("/{id}/assignment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenderAssignmentResponse>> getTenderAssignment(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tenderAssignmentQueryService.getAssignment(id)));
    }

    @Deprecated
    @GetMapping("/assignment-candidates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AssignmentCandidateDTO>>> getTenderAssignmentCandidates(
            @AuthenticationPrincipal User currentUser) {
        List<AssignmentCandidateDTO> candidates = assignmentCandidateAppService.findCandidates(
                AssignmentContext.of("tender", null, null), currentUser);
        return ResponseEntity.ok(ApiResponse.success(candidates));
    }
}
