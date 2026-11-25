package com.oldmermaidman.gomoku.backend.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CrashReportRepository extends JpaRepository<CrashReportEntity, UUID> {

    /** The newest crashes first; the panel groups them by fingerprint. */
    List<CrashReportEntity> findFirst200ByOrderByCreatedAtDesc();

    long countByCreatedAtAfter(Instant after);

    /** Retention: reports older than the cutoff go away, so the table cannot grow forever. */
    long deleteByCreatedAtBefore(Instant cutoff);
}