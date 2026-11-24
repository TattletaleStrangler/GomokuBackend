package com.oldmermaidman.gomoku.backend.moderation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One direction of a block (table {@code blocks}, FUTURE.md): [blockerId] does not want
 * to meet [blockedId] again. The direction matters, so the pair is unique but not
 * symmetrical.
 */
@Entity
@Table(name = "blocks")
public class BlockEntity {

    @Id
    private UUID id;

    @Column(name = "blocker_id", nullable = false, updatable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false, updatable = false)
    private UUID blockedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected BlockEntity() {
    }

    public BlockEntity(UUID id, UUID blockerId, UUID blockedId, Instant createdAt) {
        this.id = id;
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBlockerId() {
        return blockerId;
    }

    public UUID getBlockedId() {
        return blockedId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}