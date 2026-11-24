package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.game.domain.GameStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameRepository extends JpaRepository<GameEntity, UUID> {

    /** A room or game by its code, restricted to the statuses in question. */
    Optional<GameEntity> findByCodeAndStatusIn(String code, Collection<GameStatus> statuses);

    boolean existsByCodeAndStatusIn(String code, Collection<GameStatus> statuses);

    /**
     * The open game of a player, whatever their colour. Only one is allowed at a
     * time (TZ_BACKEND.md §4, F-B10).
     */
    @Query("""
            select g from GameEntity g
            where g.status in :statuses and (g.blackUserId = :userId or g.whiteUserId = :userId)
            """)
    Optional<GameEntity> findOpenFor(@Param("userId") UUID userId, @Param("statuses") Collection<GameStatus> statuses);

    /**
     * Running games, for the timeout sweeper.
     *
     * <p>It filters in Java on purpose: whether a clock has run out depends on that
     * player's own remaining time, which SQL cannot compare without a second table.
     * The number of simultaneous games is small by design (TZ_BACKEND.md §15).
     */
    List<GameEntity> findByStatus(GameStatus status);

    /**
     * The oldest public ranked room these settings and this rating can take, locked
     * until the transaction ends (FEATURES.md, B2).
     *
     * <p>The band starts at {@code bandBase} and widens by {@code bandStep} for every
     * {@code bandStepSeconds} the room has waited, up to {@code bandMax}: the longer
     * somebody waits, the more opponents become acceptable. {@code SKIP LOCKED} lets a
     * second quick match take the next room instead of queuing behind this one; the
     * advisory lock in {@code GameService} keeps two requests from each creating a room.
     */
    @Query(value = """
            select g.* from games g
            join users u on u.id = g.black_user_id
            where g.status = 'WAITING' and g.visibility = 'PUBLIC' and g.ranked = true
              and g.rule = :rule and g.time_control = :timeControl
              and abs(u.rating - :rating) <= least(
                    :bandMax,
                    :bandBase + :bandStep * floor(
                        extract(epoch from (cast(:now as timestamptz) - g.created_at))
                        / :bandStepSeconds))
              and not exists (
                    select 1 from blocks b
                    where (b.blocker_id = :userId and b.blocked_id = g.black_user_id)
                       or (b.blocker_id = g.black_user_id and b.blocked_id = :userId))
            order by g.created_at
            limit 1
            for update of g skip locked
            """, nativeQuery = true)
    Optional<GameEntity> findWaitingRankedForUpdate(
            @Param("rule") String rule,
            @Param("timeControl") String timeControl,
            @Param("rating") int rating,
            @Param("userId") UUID userId,
            @Param("bandBase") int bandBase,
            @Param("bandStep") int bandStep,
            @Param("bandStepSeconds") int bandStepSeconds,
            @Param("bandMax") int bandMax,
            @Param("now") Instant now);

    /** Public waiting rooms for the lobby; both filters are optional and blocks hide rows. */
    @Query(value = """
            select * from games
            where status = 'WAITING' and visibility = 'PUBLIC'
              and (cast(:rule as text) is null or rule = cast(:rule as text))
              and (cast(:timeControl as text) is null or time_control = cast(:timeControl as text))
              and not exists (
                    select 1 from blocks b
                    where (b.blocker_id = :viewer and b.blocked_id = black_user_id)
                       or (b.blocker_id = black_user_id and b.blocked_id = :viewer))
            order by created_at
            limit :limit
            """, nativeQuery = true)
    List<GameEntity> findLobby(
            @Param("rule") String rule,
            @Param("timeControl") String timeControl,
            @Param("viewer") UUID viewer,
            @Param("limit") int limit);

    /**
     * Finished games of a player, newest first (FEATURES.md, B2). Waiting rooms that
     * were abandoned before anybody joined never appear: their status is ABANDONED.
     */
    @Query("""
            select g from GameEntity g
            where g.status = :status and (g.blackUserId = :userId or g.whiteUserId = :userId)
            order by g.finishedAt desc
            """)
    List<GameEntity> findHistory(
            @Param("userId") UUID userId,
            @Param("status") GameStatus status,
            Pageable pageable);
}