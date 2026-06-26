package com.xiyu.bid.export.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.tender.service.TenderProjectAccessGuard;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Encapsulates export access control logic: filtering which projects / tenders
 * the current user is allowed to include in an Excel export.
 */
@RequiredArgsConstructor
public class ExportAccessFilter {

    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final TenderProjectAccessGuard tenderProjectAccessGuard;

    public Set<Long> exportableProjectIds() {
        if (projectAccessScopeService.currentUserHasAdminAccess()) {
            return null;
        }
        return projectAccessScopeService.filterAccessibleProjects(projectRepository.findAll()).stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public boolean canExportProject(Project project, Set<Long> exportableProjectIds) {
        return exportableProjectIds == null
                || (project != null && project.getId() != null && exportableProjectIds.contains(project.getId()));
    }

    public boolean canExportTender(Tender tender) {
        if (tender == null) return false;
        // Uses the batch-optimized method behind the scenes with a singleton list.
        // It's still O(1) query-wise if we just pass one, but PagedEntityExporter exports in stream.
        // The proper way is TenderProjectAccessGuard which now checks data scope accurately.
        return !tenderProjectAccessGuard.filterVisibleTenders(List.of(tender)).isEmpty();
    }
}
