package com.oldmermaidman.gomoku.backend.moderation;

import com.oldmermaidman.gomoku.backend.common.ApiError;
import com.oldmermaidman.gomoku.backend.common.ApiException;
import com.oldmermaidman.gomoku.backend.game.GameEntity;
import com.oldmermaidman.gomoku.backend.game.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Blocking and reporting an opponent (FUTURE.md).
 *
 * <p>Both act on the other player of a game the caller is in — there is no user search,
 * so nobody can block a stranger they never met. Blocking is idempotent and used by the
 * lobby and by quick match; reporting is stored for the admin panel of B4.
 */
@Service
public class ModerationService {

    /** A daily cap, so a report cannot be used as a weapon against everybody. */
    private static final int MAX_REPORTS_PER_DAY = 20;

    private final GameRepository games;
    private final BlockRepository blocks;
    private final ReportRepository reports;
    private final Clock clock;

    public ModerationService(
            GameRepository games,
            BlockRepository blocks,
            ReportRepository reports,
            Clock clock) {
        this.games = games;
        this.blocks = blocks;
        this.reports = reports;
        this.clock = clock;
    }

    /** Stops pairing the caller with the opponent of [gameId]; blocking twice is a no-op. */
    @Transactional
    public void block(UUID userId, UUID gameId) {
        UUID opponent = opponentOf(userId, gameId);
        if (blocks.existsByBlockerIdAndBlockedId(userId, opponent)) {
            return;
        }
        blocks.save(new BlockEntity(UUID.randomUUID(), userId, opponent, clock.instant()));
    }

    /** Records a complaint about the opponent of [gameId]. */
    @Transactional
    public void report(UUID userId, UUID gameId, ReportReason reason) {
        UUID opponent = opponentOf(userId, gameId);
        // One complaint per game is enough: a repeat is accepted silently, so the client
        // does not have to distinguish "sent" from "already sent".
        if (reports.existsByReporterIdAndGameId(userId, gameId)) {
            return;
        }
        Instant dayAgo = clock.instant().minus(Duration.ofDays(1));
        if (reports.countByReporterIdAndCreatedAtAfter(userId, dayAgo) >= MAX_REPORTS_PER_DAY) {
            throw new ApiException(ApiError.RATE_LIMITED, "Too many reports today");
        }
        reports.save(new ReportEntity(
                UUID.randomUUID(), userId, opponent, gameId, reason, clock.instant()));
    }

    /** The other player of a game the caller is in; a waiting room has nobody yet. */
    private UUID opponentOf(UUID userId, UUID gameId) {
        GameEntity game = games.findById(gameId)
                .orElseThrow(() -> new ApiException(ApiError.GAME_NOT_FOUND, "No such game"));
        if (!game.isParticipant(userId)) {
            throw new ApiException(ApiError.NOT_A_PARTICIPANT, "You are not playing this game");
        }
        UUID opponent = game.getBlackUserId().equals(userId)
                ? game.getWhiteUserId()
                : game.getBlackUserId();
        if (opponent == null) {
            throw new ApiException(ApiError.VALIDATION_FAILED, "There is nobody to moderate yet");
        }
        return opponent;
    }
}