package com.oldmermaidman.gomoku.backend.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminActionRepository extends JpaRepository<AdminActionEntity, UUID> {

    /** Everything done to one account, newest first, for the user card. */
    List<AdminActionEntity> findByTargetUserIdOrderByCreatedAtDesc(UUID targetUserId);

    /** The latest decisions, for the dashboard. */
    List<AdminActionEntity> findFirst20ByOrderByCreatedAtDesc();
}