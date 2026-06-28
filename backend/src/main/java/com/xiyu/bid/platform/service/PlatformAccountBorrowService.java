// Input: AccountBorrowApplicationRepository, PlatformAccountService, DTOs
// Output: PlatformAccountBorrowService — borrow application lifecycle
// Pos: Service/业务层 — 账号借用审批流程
// 纯核心: submit/approve/reject/cancel/return 的状态流转
// 副作用: Repository 读写, PlatformAccountService 联调

package com.xiyu.bid.platform.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import com.xiyu.bid.platform.dto.BorrowApplicationRequest;
import com.xiyu.bid.platform.entity.AccountBorrowApplication;
import com.xiyu.bid.platform.entity.AccountBorrowApplication.BorrowStatus;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.repository.AccountBorrowApplicationRepository;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** Service managing the platform account borrow application lifecycle. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountBorrowService {

    private final AccountBorrowApplicationRepository applicationRepository;
    private final PlatformAccountRepository accountRepository;
    private final PasswordEncryptionUtil passwordEncryptionUtil;

    /** Submit a new borrow application. */
    @Transactional
    @Auditable(action = "SUBMIT_BORROW", entityType = "AccountBorrowApplication",
              description = "Submitted borrow application")
    public BorrowApplicationDTO submitApplication(
            BorrowApplicationRequest request, User currentUser) {
        PlatformAccount account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("账号不存在: " + request.getAccountId()));

        if (request.getCustodianId() == null
                || !request.getCustodianId().equals(account.getCustodian())) {
            throw new IllegalArgumentException("保管员信息不匹配");
        }

        // Reserve the account by marking it PENDING_APPROVAL
        account.markPendingApproval();
        accountRepository.save(account);

        LocalDateTime expectedReturnAt = request.getExpectedReturnAt() != null
                ? LocalDateTime.parse(request.getExpectedReturnAt())
                : null;

        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .accountId(request.getAccountId())
                .applicantId(currentUser.getId())
                .custodianId(request.getCustodianId())
                .purpose(request.getPurpose())
                .projectName(request.getProjectName())
                .projectId(request.getProjectId())
                .expectedReturnAt(expectedReturnAt)
                .status(BorrowStatus.PENDING_APPROVAL)
                .build();

        AccountBorrowApplication saved = applicationRepository.save(app);
        log.info("Borrow application {} submitted by user {} for account {}",
                saved.getId(), currentUser.getId(), request.getAccountId());
        return toDTO(saved);
    }

    /** Approve a pending borrow application. */
    @Transactional
    @Auditable(action = "APPROVE_BORROW", entityType = "AccountBorrowApplication",
              description = "Approved borrow application")
    public BorrowApplicationDTO approveApplication(Long applicationId, String comment, User currentUser) {
        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));

        requireCustodian(app, currentUser);
        app.approve(comment);
        AccountBorrowApplication saved = applicationRepository.save(app);
        log.info("Borrow application {} approved by user {}", applicationId, currentUser.getId());
        return toDTO(saved);
    }

    /** Reject a pending borrow application. */
    @Transactional
    @Auditable(action = "REJECT_BORROW", entityType = "AccountBorrowApplication",
              description = "Rejected borrow application")
    public BorrowApplicationDTO rejectApplication(
            Long applicationId, String reason, User currentUser) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("拒绝时必须填写原因");
        }

        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));

        requireCustodian(app, currentUser);
        app.reject(reason);
        AccountBorrowApplication saved = applicationRepository.save(app);

        // Release the account back to AVAILABLE
        releaseAccount(saved.getAccountId());

        log.info("Borrow application {} rejected by user {} reason: {}",
                applicationId, currentUser.getId(), reason);
        return toDTO(saved);
    }

    /** Cancel a pending borrow application (by applicant). */
    @Transactional
    @Auditable(action = "CANCEL_BORROW", entityType = "AccountBorrowApplication",
              description = "Cancelled borrow application")
    public BorrowApplicationDTO cancelApplication(Long applicationId, User currentUser) {
        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));

        requireApplicant(app, currentUser);
        app.cancel();
        AccountBorrowApplication saved = applicationRepository.save(app);

        // Release the account back to AVAILABLE
        releaseAccount(saved.getAccountId());

        log.info("Borrow application {} cancelled by user {}", applicationId, currentUser.getId());
        return toDTO(saved);
    }

    /** Return account and update password. */
    @Transactional
    @Auditable(action = "RETURN_BORROW", entityType = "AccountBorrowApplication",
              description = "Returned borrowed account")
    public BorrowApplicationDTO returnAccount(
            Long applicationId, String newPassword, LocalDateTime actualReturnedAt, User currentUser) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度不能少于6位");
        }

        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));

        requireCustodian(app, currentUser);
        app.markReturned(actualReturnedAt);
        AccountBorrowApplication saved = applicationRepository.save(app);

        // Return the account to pool and update password
        PlatformAccount account = accountRepository.findById(saved.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("账号不存在: " + saved.getAccountId()));
        account.returnToPool();
        account.setPassword(passwordEncryptionUtil.encrypt(newPassword));
        accountRepository.save(account);

        log.info("Borrow application {} returned by user {} (password updated)", applicationId, currentUser.getId());
        return toDTO(saved);
    }

    /** Get applications with optional status filter. */
    public List<BorrowApplicationDTO> getApplications(Long applicantId, Long custodianId, String status) {
        List<AccountBorrowApplication> apps;
        if (status != null) {
            BorrowStatus bs = BorrowStatus.valueOf(status);
            apps = applicationRepository.findByStatus(bs);
        } else if (applicantId != null) {
            apps = applicationRepository.findByApplicantId(applicantId);
        } else if (custodianId != null) {
            apps = applicationRepository.findByCustodianId(custodianId);
        } else {
            apps = applicationRepository.findAll();
        }
        return apps.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** Get a single application by ID. */
    public BorrowApplicationDTO getApplication(Long applicationId) {
        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));
        return toDTO(app);
    }

    // ── helpers ──

    private BorrowApplicationDTO toDTO(AccountBorrowApplication app) {
        return BorrowApplicationDTO.builder()
                .id(app.getId())
                .accountId(app.getAccountId())
                .applicantId(app.getApplicantId())
                .custodianId(app.getCustodianId())
                .purpose(app.getPurpose())
                .projectName(app.getProjectName())
                .projectId(app.getProjectId())
                .expectedReturnAt(app.getExpectedReturnAt())
                .status(app.getStatus().name())
                .rejectReason(app.getRejectReason())
                .approvalComment(app.getApprovalComment())
                .approvedAt(app.getApprovedAt())
                .returnedAt(app.getReturnedAt())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }

    private void releaseAccount(Long accountId) {
        accountRepository.findById(accountId).ifPresent(account -> {
            account.returnToPool();
            accountRepository.save(account);
        });
    }

    private void requireCustodian(AccountBorrowApplication app, User currentUser) {
        if (currentUser == null || !currentUser.getId().equals(app.getCustodianId())) {
            throw new IllegalStateException("只有账号绑定联系人可以操作该申请");
        }
    }

    private void requireApplicant(AccountBorrowApplication app, User currentUser) {
        if (currentUser == null || !currentUser.getId().equals(app.getApplicantId())) {
            throw new IllegalStateException("只有申请人可以撤销该申请");
        }
    }
}
