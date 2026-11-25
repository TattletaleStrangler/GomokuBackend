package com.oldmermaidman.gomoku.backend.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ErrorEventRepository extends JpaRepository<ErrorEventEntity, UUID> {

    /** The newest errors first; the panel shows the first hundred. */
    List<ErrorEventEntity> findFirst100ByOrderByOccurredAtDesc();

    long countByOccurredAtAfter(Instant after);
}