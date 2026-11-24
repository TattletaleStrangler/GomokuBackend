package com.oldmermaidman.gomoku.backend.user;

import com.oldmermaidman.gomoku.backend.user.dto.LeaderboardRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The leaderboard (FEATURES.md, B2).
 *
 * <p>One read-only aggregate over finished games. At this scale a query is cheaper
 * than a cache, and a cache would have to be invalidated on every finished game; when
 * the table grows, a short-lived cache can be added here without touching the API.
 */
@Service
public class LeaderboardService {

    /** Nobody reads a longer list; the client asks for fifty by default. */
    private static final int MAX_ROWS = 100;

    private final UserRepository users;

    public LeaderboardService(UserRepository users) {
        this.users = users;
    }

    /** The top players by Elo, each with their win/loss/draw record. */
    @Transactional(readOnly = true)
    public List<LeaderboardRow> top(int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_ROWS);
        List<UserRepository.LeaderboardRowProjection> rows = users.leaderboard(capped);

        List<LeaderboardRow> board = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            UserRepository.LeaderboardRowProjection row = rows.get(i);
            board.add(new LeaderboardRow(
                    i + 1,
                    row.getUserId(),
                    row.getNickname(),
                    row.getRating(),
                    row.getWins(),
                    row.getLosses(),
                    row.getDraws()));
        }
        return board;
    }
}