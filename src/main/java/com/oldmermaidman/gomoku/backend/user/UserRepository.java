package com.oldmermaidman.gomoku.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Nicknames are unique case-insensitively (functional unique index in V1). */
    Optional<User> findByNicknameIgnoreCase(String nickname);

    boolean existsByNicknameIgnoreCase(String nickname);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Locks the account row: creating or joining a room must be serialised per user,
     * otherwise two taps could open two games for the same player.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") UUID id);

    /**
     * Wins, losses and draws of a player over finished games.
     *
     * <p>Native because the game tables are not mapped to entities yet; the query
     * moves to the games module once it exists.
     */
    @Query(value = """
            select
              count(*) filter (where (black_user_id = :id and result = 'BLACK_WIN')
                                  or (white_user_id = :id and result = 'WHITE_WIN')) as wins,
              count(*) filter (where (black_user_id = :id and result = 'WHITE_WIN')
                                  or (white_user_id = :id and result = 'BLACK_WIN')) as losses,
              count(*) filter (where result = 'DRAW') as draws
            from games
            where status = 'FINISHED' and (black_user_id = :id or white_user_id = :id)
            """, nativeQuery = true)
    StatsRow statsFor(@Param("id") UUID id);

    /** Column aliases of {@link #statsFor(UUID)}. */
    interface StatsRow {
        long getWins();

        long getLosses();

        long getDraws();
    }

    /**
     * The leaderboard: players with at least one finished game, best rating first.
     *
     * <p>One aggregate query instead of a stats query per row; the tables are small and
     * the result is capped by {@code limit}. Deleted accounts are anonymised and stay
     * out of the list.
     */
    @Query(value = """
            select u.id as user_id, u.nickname as nickname, u.rating as rating,
                   count(g.id) filter (where (g.black_user_id = u.id and g.result = 'BLACK_WIN')
                                         or (g.white_user_id = u.id and g.result = 'WHITE_WIN')) as wins,
                   count(g.id) filter (where (g.black_user_id = u.id and g.result = 'WHITE_WIN')
                                         or (g.white_user_id = u.id and g.result = 'BLACK_WIN')) as losses,
                   count(g.id) filter (where g.result = 'DRAW') as draws
            from users u
            join games g on g.status = 'FINISHED'
                 and (g.black_user_id = u.id or g.white_user_id = u.id)
            where u.deleted_at is null
            group by u.id, u.nickname, u.rating
            order by u.rating desc, u.nickname asc
            limit :limit
            """, nativeQuery = true)
    List<LeaderboardRowProjection> leaderboard(@Param("limit") int limit);

    /** Column aliases of {@link #leaderboard(int)}. */
    interface LeaderboardRowProjection {
        UUID getUserId();

        String getNickname();

        int getRating();

        long getWins();

        long getLosses();

        long getDraws();
    }
}