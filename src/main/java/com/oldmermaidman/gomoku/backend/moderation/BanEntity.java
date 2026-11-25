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
 * One ban (table {@code bans}, FEATURES.md, B4). Rows are kept after they expire, so the
 * panel can show what happened to an account and when.
 */
@Entity
@Table(name = "bans")
public class BanEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "until", nullable = false)
    private Instant until;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private BanKind kind;

    @Column(nullable = false, length = 200, updatable = false)
    private String reason;

    @Column(name = "issued_by", updatable = false)
    private UUID issuedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected BanEntity() {
    }

    public BanEntity(
            UUID id,
            UUID userId,
            Instant until,
            BanKind kind,
            String reason,
            UUID issuedBy,
            Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.until = until;
        this.kind = kind;
        this.reason = reason;
        this.issuedBy = issuedBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getUntil() {
        return until;
    }

    public BanKind getKind() {
        return kind;
    }

    public String getReason() {
        return reason;
    }

    public UUID getIssuedBy() {
        return issuedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Ends the ban now; the row stays as history. */
    public void lift(Instant now) {
        if (until.isAfter(now)) {
            this.until = now;
        }
    }
}