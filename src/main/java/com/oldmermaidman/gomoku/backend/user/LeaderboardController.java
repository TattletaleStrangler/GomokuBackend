package com.oldmermaidman.gomoku.backend.user;

import com.oldmermaidman.gomoku.backend.user.dto.LeaderboardRow;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The leaderboard: the top players by Elo (FEATURES.md, B2). */
@RestController
@RequestMapping("/api/v1")
public class LeaderboardController {

    private final LeaderboardService leaderboard;

    public LeaderboardController(LeaderboardService leaderboard) {
        this.leaderboard = leaderboard;
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardRow> leaderboard(@RequestParam(defaultValue = "50") int limit) {
        return leaderboard.top(limit);
    }
}