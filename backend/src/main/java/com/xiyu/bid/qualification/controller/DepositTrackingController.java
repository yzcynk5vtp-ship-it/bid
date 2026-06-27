package com.xiyu.bid.qualification.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.qualification.infrastructure.DepositTracking;
import com.xiyu.bid.qualification.infrastructure.DepositTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/knowledge/deposit")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DepositTrackingController {

    private final DepositTrackingRepository depositTrackingRepository;
    private final com.xiyu.bid.service.AuthService authService;
    private final com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository leadAssignmentRepository;

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDepositSummary() {
        BigDecimal totalPaid = depositTrackingRepository.sumTotalAmount();
        BigDecimal totalPending = depositTrackingRepository.sumPendingAmount();
        long pendingCount = depositTrackingRepository.countPendingDeposits();

        Map<String, Object> summary = Map.of(
                "totalPaid", totalPaid != null ? totalPaid : BigDecimal.ZERO,
                "totalPending", totalPending != null ? totalPending : BigDecimal.ZERO,
                "pendingCount", pendingCount
        );

        return ResponseEntity.ok(ApiResponse.success("Success", summary));
    }

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DepositTracking>>> getDepositList() {
        return ResponseEntity.ok(ApiResponse.success("Success", depositTrackingRepository.findAllByOrderByPaymentDateDesc()));
    }

    @PostMapping("/return/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DepositTracking>> markAsReturned(
            @PathVariable Long id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        Optional<DepositTracking> opt = depositTrackingRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        DepositTracking tracking = opt.get();
        
        // Authorization Check
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isGlobalAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") 
                        || a.getAuthority().equals("ROLE_BIDADMIN") 
                        || a.getAuthority().equals("ROLE_BID_TEAMLEADER"));
        
        if (!isGlobalAdmin) {
            if (userDetails == null || userDetails.getUsername() == null) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
            }
            Long userId = authService.resolveUserIdByUsername(userDetails.getUsername());
            Long projectId = tracking.getProjectId();
            
            boolean isLead = leadAssignmentRepository.findByProjectId(projectId)
                    .map(lead -> (lead.getPrimaryLeadUserId() != null && lead.getPrimaryLeadUserId().equals(userId)) ||
                                 (lead.getSecondaryLeadUserId() != null && lead.getSecondaryLeadUserId().equals(userId)))
                    .orElse(false);
                    
            if (!isLead) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
        }

        tracking.setStatus("RETURNED");
        depositTrackingRepository.save(tracking);
        return ResponseEntity.ok(ApiResponse.success("Marked as returned", tracking));
    }
}
