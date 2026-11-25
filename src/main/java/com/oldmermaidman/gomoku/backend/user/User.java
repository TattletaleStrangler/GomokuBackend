package com.oldmermaidman.gomoku.backend.user;

import com.oldmermaidman.gomoku.backend.game.domain.EloRating;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A player account (table {@code users}, see V1__init.sql).
 *
 * <p>Deletion is anonymisation: the row stays, so the opponent's game history remains
 * readable, but the nickname and the password are gone.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nickname", nullable = false, length = 24)
    private String nickname;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Elo of ranked (quick-play) games; rooms by code never touch it. */
    @Column(nullable = false)
    private int rating;

    /** Players may only play; an administrator may also review reports (B4). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private UserRole role;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** For JPA only. */
    protected User() {
    }

    public User(UUID id, String nickname, String passwordHash, Instant createdAt) {
        this.id = id;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.rating = EloRating.START;
        this.role = UserRole.PLAYER;
    }

    public UUID getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public UserRole getRole() {
        return role;
    }

    /** Promotes the account once, when the configured administrator logs in for the first time. */
    public void promoteToAdmin() {
        this.role = UserRole.ADMIN;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Erases the personal data but keeps the row: {@code del_} plus part of the id is
     * 20 characters, which fits the 3..24 check of the table.
     */
    public void anonymise(Instant now) {
        this.nickname = "del_" + id.toString().replace("-", "").substring(0, 16);
        this.passwordHash = "deleted-" + id;
        this.deletedAt = now;
    }
}