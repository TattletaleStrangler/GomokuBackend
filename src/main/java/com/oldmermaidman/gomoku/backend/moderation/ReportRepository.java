package com.oldmermaidman.gomoku.backend.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<ReportEntity, UUID> {

    /** How many reports one player sent since [after] — the daily cap. */
    long countByReporterIdAndCreatedAtAfter(UUID reporterId, Instant after);

    /** One complaint per game is enough; the index makes a repeat a no-op to report. */
    boolean existsByReporterIdAndGameId(UUID reporterId, UUID gameId);

    long countByStatus(ReportStatus status);

    /** The queue: newest first, optionally only one state. */
    List<ReportEntity> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<ReportEntity> findAllByOrderByCreatedAtDesc();

    /** Everything said about one player — the user card. */
    List<ReportEntity> findByReportedIdOrderByCreatedAtDesc(UUID reportedId);

    long countByReportedId(UUID reportedId);

    /** How many different people complained about this player. */
    @org.springframework.data.jpa.repository.Query(
            "select count(distinct r.reporterId) from ReportEntity r where r.reportedId = :reportedId")
    long countDistinctReporters(
            @org.springframework.data.repository.query.Param("reportedId") UUID reportedId);
}