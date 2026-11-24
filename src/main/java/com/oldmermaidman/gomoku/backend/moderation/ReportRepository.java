package com.oldmermaidman.gomoku.backend.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<ReportEntity, UUID> {

    /** How many reports one player sent since [after] — the daily cap. */
    long countByReporterIdAndCreatedAtAfter(UUID reporterId, Instant after);
}