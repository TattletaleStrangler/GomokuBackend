package com.oldmermaidman.gomoku.backend.moderation;

import com.oldmermaidman.gomoku.backend.common.ApiError;
import com.oldmermaidman.gomoku.backend.common.ApiException;
import com.oldmermaidman.gomoku.backend.game.GameEntity;
import com.oldmermaidman.gomoku.backend.game.GameRepository;
import com.oldmermaidman.gomoku.backend.game.MoveEntity;
import com.oldmermaidman.gomoku.backend.game.MoveRepository;
import com.oldmermaidman.gomoku.backend.user.User;
import com.oldmermaidman.gomoku.backend.user.UserRepository;
import com.oldmermaidman.gomoku.backend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the admin panel shows and does (FEATURES.md, B4).
 *
 * <p>A report on its own says nothing, so every card carries the context: the game the
 * complaint is about, how many people complained about the same account and what was
 * already decided. Decisions are written to {@code admin_actions} — nothing is deleted.
 */
@Service
public class AdminService {

    /** A first punishment: no ranked games for a week, rooms by code still work. */
    private static final Duration DEFAULT_RANKED_BAN = Duration.ofDays(7);

    /** "Permanently" is stored as a date far away; the row stays readable as history. */
    private static final Duration ACCOUNT_BAN = Duration.ofDays(365L * 100);

    private final ReportRepository reports;
    private final BanRepository bans;
    private final AdminActionRepository actions;
    private final UserRepository users;
    private final GameRepository games;
    private final MoveRepository moves;
    private final Clock clock;

    public AdminService(
            ReportRepository reports,
            BanRepository bans,
            AdminActionRepository actions,
            UserRepository users,
            GameRepository games,
            MoveRepository moves,
            Clock clock) {
        this.reports = reports;
        this.bans = bans;
        this.actions = actions;
        this.users = users;
        this.games = games;
        this.moves = moves;
        this.clock = clock;
    }

    /** One row of the queue, with the names the admin needs to judge it. */
    public record ReportRow(
            ReportEntity report,
            String reporterNickname,
            String reportedNickname,
            long reportsAgainst,
            long distinctReporters) {
    }

    /** Everything about one report: the game, the accused and the history. */
    public record ReportCard(
            ReportEntity report,
            String reporterNickname,
            User reported,
            long reportsAgainst,
            long distinctReporters,
            List<BanEntity> bans,
            List<AdminActionEntity> actions,
            GameEntity game,
            List<MoveEntity> moves,
            boolean bannedNow) {
    }

    /** The user card, reachable from any report. */
    public record UserCard(
            User user,
            long reportsAgainst,
            long distinctReporters,
            List<BanEntity> bans,
            List<AdminActionEntity> actions,
            boolean bannedNow) {
    }

    @Transactional(readOnly = true)
    public List<ReportRow> queue(ReportStatus status) {
        List<ReportEntity> rows = status == null
                ? reports.findAllByOrderByCreatedAtDesc()
                : reports.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(report -> new ReportRow(
                report,
                nickname(report.getReporterId()),
                nickname(report.getReportedId()),
                reports.countByReportedId(report.getReportedId()),
                reports.countDistinctReporters(report.getReportedId()))).toList();
    }

    @Transactional(readOnly = true)
    public ReportCard card(UUID reportId) {
        ReportEntity report = reports.findById(reportId)
                .orElseThrow(() -> new ApiException(ApiError.NOT_FOUND, "No such report"));
        User reported = users.findById(report.getReportedId())
                .orElseThrow(() -> new ApiException(ApiError.NOT_FOUND, "No such account"));
        GameEntity game = report.getGameId() == null
                ? null
                : games.findById(report.getGameId()).orElse(null);

        return new ReportCard(
                report,
                nickname(report.getReporterId()),
                reported,
                reports.countByReportedId(report.getReportedId()),
                reports.countDistinctReporters(report.getReportedId()),
                bans.findByUserIdOrderByCreatedAtDesc(report.getReportedId()),
                actions.findByTargetUserIdOrderByCreatedAtDesc(report.getReportedId()),
                game,
                game == null ? List.of() : moves.findByGameIdOrderByPlyAsc(game.getId()),
                isBanned(report.getReportedId()));
    }

    @Transactional(readOnly = true)
    public UserCard userCard(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(ApiError.NOT_FOUND, "No such account"));
        return new UserCard(
                user,
                reports.countByReportedId(userId),
                reports.countDistinctReporters(userId),
                bans.findByUserIdOrderByCreatedAtDesc(userId),
                actions.findByTargetUserIdOrderByCreatedAtDesc(userId),
                isBanned(userId));
    }

    @Transactional(readOnly = true)
    public long openReports() {
        return reports.countByStatus(ReportStatus.NEW);
    }

    @Transactional(readOnly = true)
    public List<AdminActionEntity> latestActions() {
        return actions.findFirst20ByOrderByCreatedAtDesc();
    }

    /**
     * Applies a decision to a report (or, for an unban, to an account).
     *
     * @param days how long a ranked ban lasts; null means the default week
     */
    @Transactional
    public void act(
            UUID adminId,
            UUID reportId,
            UUID userId,
            ActionKind action,
            String note,
            Integer days) {
        Instant now = clock.instant();
        ReportEntity report = reportId == null
                ? null
                : reports.findById(reportId)
                        .orElseThrow(() -> new ApiException(ApiError.NOT_FOUND, "No such report"));
        UUID target = report != null ? report.getReportedId() : userId;
        if (target == null) {
            throw new ApiException(ApiError.VALIDATION_FAILED, "An action needs a report or an account");
        }
        String trimmed = note == null || note.isBlank() ? null : note.trim();

        switch (action) {
            case REVIEW -> review(report, ReportStatus.REVIEWED, adminId, trimmed, now);
            case DISMISS -> review(report, ReportStatus.DISMISSED, adminId, trimmed, now);
            case WARN -> review(report, ReportStatus.ACTIONED, adminId, trimmed, now);
            case BAN_RANKED -> {
                review(report, ReportStatus.ACTIONED, adminId, trimmed, now);
                Duration length = days == null ? DEFAULT_RANKED_BAN : Duration.ofDays(days);
                bans.save(new BanEntity(
                        UUID.randomUUID(),
                        target,
                        now.plus(length),
                        BanKind.RANKED,
                        banReason(trimmed),
                        adminId,
                        now));
            }
            case BAN_ACCOUNT -> {
                review(report, ReportStatus.ACTIONED, adminId, trimmed, now);
                bans.save(new BanEntity(
                        UUID.randomUUID(),
                        target,
                        now.plus(ACCOUNT_BAN),
                        BanKind.ACCOUNT,
                        banReason(trimmed),
                        adminId,
                        now));
            }
            case UNBAN -> {
                if (report != null) {
                    review(report, ReportStatus.ACTIONED, adminId, trimmed, now);
                }
                bans.findByUserIdOrderByCreatedAtDesc(target).forEach(ban -> ban.lift(now));
            }
        }

        actions.save(new AdminActionEntity(
                UUID.randomUUID(), adminId, target, reportId, action, trimmed, now));
    }

    /** The admin's nickname from the session, turned into the account id the log wants. */
    @Transactional(readOnly = true)
    public UUID adminId(String nickname) {
        User admin = users.findByNicknameIgnoreCase(nickname)
                .orElseThrow(() -> new ApiException(ApiError.UNAUTHORIZED, "Unknown administrator"));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new ApiException(ApiError.UNAUTHORIZED, "Not an administrator");
        }
        return admin.getId();
    }

    private void review(
            ReportEntity report, ReportStatus status, UUID adminId, String note, Instant now) {
        if (report != null) {
            report.review(status, adminId, note, now);
        }
    }

    private String banReason(String note) {
        return note == null || note.isBlank() ? "See admin_actions" : note;
    }

    private boolean isBanned(UUID userId) {
        return bans.isActive(userId, clock.instant());
    }

    private String nickname(UUID userId) {
        return users.findById(userId).map(User::getNickname).orElse("deleted");
    }
}