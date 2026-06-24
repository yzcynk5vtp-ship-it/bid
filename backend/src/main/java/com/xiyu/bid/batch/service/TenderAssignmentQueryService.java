package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.dto.TenderAssignmentResponse;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.user.core.AssignmentContext;
import com.xiyu.bid.user.dto.AssignmentCandidateDTO;
import com.xiyu.bid.user.service.AssignmentCandidateAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenderAssignmentQueryService {

    private final TenderAssignmentRecordRepository tenderAssignmentRecordRepository;
    private final UserRepository userRepository;
    private final TenderAssignmentViewAssembler assembler;
    private final AssignmentCandidateAppService assignmentCandidateAppService;

    @Transactional(readOnly = true)
    public TenderAssignmentResponse getAssignment(Long tenderId) {
        List<TenderAssignmentResponse.AssignmentRecord> history = tenderAssignmentRecordRepository
                .findByTenderIdOrderByAssignedAtDesc(tenderId)
                .stream()
                .map(assembler::toRecord)
                .toList();

        return TenderAssignmentResponse.builder()
                .latest(history.isEmpty() ? null : history.get(0))
                .history(history)
                .build();
    }

    @Deprecated
    @Transactional(readOnly = true)
    public List<AssignmentCandidateDTO> getCandidates(User currentUser) {
        return assignmentCandidateAppService.findCandidates(
                AssignmentContext.of("tender", null, null), currentUser);
    }
}
