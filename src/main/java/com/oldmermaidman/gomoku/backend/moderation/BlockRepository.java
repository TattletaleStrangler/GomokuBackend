package com.oldmermaidman.gomoku.backend.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<BlockEntity, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /** The accounts one player has blocked, newest first — the block list screen. */
    List<BlockEntity> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    /** Lifts one block; the returned count says whether there was one to lift. */
    long deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}