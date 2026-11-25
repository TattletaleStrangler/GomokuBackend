package com.oldmermaidman.gomoku.backend.moderation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A complaint about an opponent (table {@code reports}, FUTURE.md). Free text is
 * deliberately absent: a short list of reasons is enough to act on and cannot leak
 * anything the reporter did not mean to say.
 */
@Entity
@Table(name = "reports")
public class ReportEntity {

    @Id
    private UUID id;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Column(name = "reported_id", nullable = false, updatable = false)
    private UUID reportedId;

    @Column(name = "game_id", updatable = false)
    private UUID gameId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private ReportReason reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Where the report is in the queue; nothing is ever deleted. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportStatus status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** The admin's own words, for the next person who opens this report. */
    @Column(length = 200)
    private String note;

    /** For JPA only. */
    protected ReportEntity() {
    }

    public ReportEntity(
            UUID id,
            UUID reporterId,
            UUID reportedId,
            UUID gameId,
            ReportReason reason,
            Instant createdAt) {
        this.id = id;
        this.reporterId = reporterId;
        this.reportedId = reportedId;
        this.gameId = gameId;
        this.reason = reason;
        this.createdAt = createdAt;
        this.status = ReportStatus.NEW;
    }

    /** Records the decision and who made it. */
    public void review(ReportStatus status, UUID adminId, String note, Instant now) {
        this.status = status;
        this.reviewedBy = adminId;
        this.reviewedAt = now;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public UUID getReportedId() {
        return reportedId;
    }

    public UUID getGameId() {
        return gameId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getNote() {
        return note;
    }
}