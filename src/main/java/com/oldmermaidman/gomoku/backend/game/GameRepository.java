package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.game.domain.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * The oldest public room with these settings, locked until the transaction ends.
     *
     * <p>{@code SKIP LOCKED} lets a second quick match take the next room instead of
     * waiting behind this one; the advisory lock in {@code GameService} keeps two
     * requests from each creating a room of their own.
     */
    @Query(value = """
            select * from games
            where status = 'WAITING' and visibility = 'PUBLIC'
              and rule = :rule and time_control = :timeControl
            order by created_at
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<GameEntity> findFirstWaitingPublicForUpdate(
            @Param("rule") String rule, @Param("timeControl") String timeControl);

    /** Public waiting rooms for the lobby; both filters are optional. */
    @Query(value = """
            select * from games
            where status = 'WAITING' and visibility = 'PUBLIC'
              and (cast(:rule as text) is null or rule = cast(:rule as text))
              and (cast(:timeControl as text) is null or time_control = cast(:timeControl as text))
            order by created_at
            limit :limit
            """, nativeQuery = true)
    List<GameEntity> findLobby(
            @Param("rule") String rule,
            @Param("timeControl") String timeControl,
            @Param("limit") int limit);
}