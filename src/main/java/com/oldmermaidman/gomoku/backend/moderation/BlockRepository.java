package com.oldmermaidman.gomoku.backend.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BlockRepository extends JpaRepository<BlockEntity, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}