// Input: JPA entity mapping account_borrow_applications table
// Output: AccountBorrowApplication with status machine and lifecycle methods
// Pos: Entity/实体层 — 账号借用审批申请

package com.xiyu.bid.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import com.xiyu.bid.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Account borrow application for the approval workflow. */
@Entity
@Table(name = "account_borrow_applications", indexes = {
    @Index(name = "idx_acct_borrow_applicant", columnList = "applicant_id"),
    @Index(name = "idx_acct_borrow_account", columnList = "account_id"),
    @Index(name = "idx_acct_borrow_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBorrowApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(name = "custodian_id", nullable = false)
    private Long custodianId;

    @Column(length = 500)
    private String purpose;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "expected_return_at")
    private LocalDateTime expectedReturnAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BorrowStatus status = BorrowStatus.PENDING_APPROVAL;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "approval_comment", length = 500)
    private String approvalComment;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Approve the application. Only valid from PENDING_APPROVAL. */
    public void approve(String comment) {
        if (status != BorrowStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "只能在待审批状态下通过申请，当前状态：" + status.getDescription());
        }
        this.status = BorrowStatus.BORROWED;
        this.approvalComment = comment;
        this.approvedAt = LocalDateTime.now();
    }

    /** Reject the application with reason. Only valid from PENDING_APPROVAL. */
    public void reject(String reason) {
        if (status != BorrowStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "只能在待审批状态下拒绝申请，当前状态：" + status.getDescription());
        }
        this.status = BorrowStatus.REJECTED;
        this.rejectReason = reason;
        this.approvedAt = LocalDateTime.now();
    }

    /** Cancel the application (applicant only, when PENDING_APPROVAL). */
    public void cancel() {
        if (status != BorrowStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "只能在待审批状态下撤销申请，当前状态：" + status.getDescription());
        }
        this.status = BorrowStatus.CANCELLED;
    }

    /** Mark the account as returned. Only valid from BORROWED. */
    public void markReturned(LocalDateTime actualReturnedAt) {
        if (status != BorrowStatus.BORROWED) {
            throw new BusinessException(
                    "只能在已借出状态下归还账号，当前状态：" + status.getDescription());
        }
        this.status = BorrowStatus.RETURNED;
        this.returnedAt = actualReturnedAt != null ? actualReturnedAt : LocalDateTime.now();
    }

    /** Borrow application status enumeration. */
    public enum BorrowStatus {
        PENDING_APPROVAL("待审批"),
        BORROWED("已借出"),
        REJECTED("已拒绝"),
        RETURNED("已归还"),
        CANCELLED("已撤销");

        private final String description;

        BorrowStatus(String pDescription) {
            this.description = pDescription;
        }

        public String getDescription() {
            return description;
        }
    }
}
