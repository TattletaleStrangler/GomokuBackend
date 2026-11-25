package com.oldmermaidman.gomoku.backend.moderation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One decision of an administrator (table {@code admin_actions}, FEATURES.md, B4). */
@Entity
@Table(name = "admin_actions")
public class AdminActionEntity {

    @Id
    private UUID id;

    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;

    @Column(name = "target_user_id", updatable = false)
    private UUID targetUserId;

    @Column(name = "report_id", updatable = false)
    private UUID reportId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private ActionKind action;

    @Column(length = 200, updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected AdminActionEntity() {
    }

    public AdminActionEntity(
            UUID id,
            UUID adminId,
            UUID targetUserId,
            UUID reportId,
            ActionKind action,
            String note,
            Instant createdAt) {
        this.id = id;
        this.adminId = adminId;
        this.targetUserId = targetUserId;
        this.reportId = reportId;
        this.action = action;
        this.note = note;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public UUID getReportId() {
        return reportId;
    }

    public ActionKind getAction() {
        return action;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}