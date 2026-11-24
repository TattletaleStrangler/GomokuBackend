package com.oldmermaidman.gomoku.backend.game.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The Elo arithmetic (FEATURES.md, priority B2). */
class EloRatingTest {

    @Test
    void equalPlayersMoveByHalfOfK() {
        assertThat(EloRating.after(1200, 1200, 1.0)).isEqualTo(1216);
        assertThat(EloRating.after(1200, 1200, 0.0)).isEqualTo(1184);
        assertThat(EloRating.after(1200, 1200, 0.5)).isEqualTo(1200);
    }

    @Test
    void beatingAStrongerOpponentIsWorthMore() {
        int againstEqual = EloRating.after(1200, 1200, 1.0) - 1200;
        int againstStronger = EloRating.after(1200, 1600, 1.0) - 1200;
        int againstWeaker = EloRating.after(1200, 800, 1.0) - 1200;

        assertThat(againstStronger).isGreaterThan(againstEqual);
        assertThat(againstWeaker).isLessThan(againstEqual);
        // A win can never be worth more than K.
        assertThat(againstStronger).isLessThanOrEqualTo(EloRating.K);
    }

    @Test
    void aDrawAgainstAStrongerOpponentStillGains() {
        assertThat(EloRating.after(1200, 1600, 0.5)).isGreaterThan(1200);
        assertThat(EloRating.after(1200, 800, 0.5)).isLessThan(1200);
    }
}