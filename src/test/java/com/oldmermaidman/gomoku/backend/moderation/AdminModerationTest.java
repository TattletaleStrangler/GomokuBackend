package com.oldmermaidman.gomoku.backend.moderation;

import com.oldmermaidman.gomoku.backend.game.GameRepository;
import com.oldmermaidman.gomoku.backend.game.domain.GameStatus;
import com.oldmermaidman.gomoku.backend.game.domain.Visibility;
import com.oldmermaidman.gomoku.backend.telemetry.CrashReportRequest;
import com.oldmermaidman.gomoku.backend.telemetry.TelemetryService;
import com.oldmermaidman.gomoku.backend.testsupport.GameTestSupport;
import com.oldmermaidman.gomoku.backend.user.AdminUserDetailsService;
import com.oldmermaidman.gomoku.backend.user.User;
import com.oldmermaidman.gomoku.backend.user.UserRepository;
import com.oldmermaidman.gomoku.backend.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The admin panel behind the reports (FEATURES.md, B4).
 *
 * <p>An admin decision is recorded, a ranked ban closes quick match but not the rooms by
 * code, an unban restores it, and a crash sent twice is one group.
 */
class AdminModerationTest extends GameTestSupport {

    @Autowired
    private AdminService admin;

    @Autowired
    private ReportRepository reports;

    @Autowired
    private BanRepository bans;

    @Autowired
    private AdminActionRepository actions;

    @Autowired
    private UserRepository users;

    @Autowired
    private AdminUserDetailsService adminDetails;

    @Autowired
    private TelemetryService telemetry;

    @Autowired
    private GameRepository gameRepository;

    @BeforeEach
    void cleanPublicRooms() {
        gameRepository.deleteAll(gameRepository.findByStatus(GameStatus.WAITING).stream()
                .filter(room -> room.getVisibility() == Visibility.PUBLIC)
                .toList());
    }

    @Test
    void aRankedBanClosesQuickMatchAndUnbanRestoresIt() throws Exception {
        Player adminPlayer = newPlayer();
        Player trouble = newPlayer();
        Player victim = newPlayer();
        promote(adminPlayer);

        String gameId = openGame(trouble, victim, "UNTIMED");
        action(trouble, gameId, "resign");
        assertThat(report(victim, gameId, "CHEATING").status()).isEqualTo(204);

        var report = reports.findAll().stream()
                .filter(row -> row.getReportedId().equals(idOf(trouble)))
                .findFirst()
                .orElseThrow();

        admin.act(idOf(adminPlayer), report.getId(), null, ActionKind.BAN_RANKED, "engine-like timing", 7);

        assertThat(reports.findById(report.getId()).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.ACTIONED);
        assertThat(actions.findByTargetUserIdOrderByCreatedAtDesc(idOf(trouble))).hasSize(1);
        assertThat(bans.isActive(idOf(trouble), now())).isTrue();

        Response banned = quickMatch(trouble, "FREESTYLE", "UNTIMED");
        assertThat(banned.status()).isEqualTo(403);
        assertThat(banned.text("$.code")).isEqualTo("BANNED");

        // Rooms by code are not affected by a ranked ban.
        Response room = createGame(trouble, "FREESTYLE", "UNTIMED");
        assertThat(room.status()).isEqualTo(201);
        assertThat(action(trouble, room.text("$.gameId"), "leave").status()).isEqualTo(200);

        admin.act(idOf(adminPlayer), null, idOf(trouble), ActionKind.UNBAN, "appealed", null);

        assertThat(bans.isActive(idOf(trouble), now())).isFalse();
        assertThat(quickMatch(trouble, "FREESTYLE", "UNTIMED").status()).isEqualTo(200);
    }

    @Test
    void theQueueCarriesTheNamesAndTheContext() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        action(alice, gameId, "resign");
        assertThat(report(bob, gameId, "STALLING").status()).isEqualTo(204);

        var queue = admin.queue(ReportStatus.NEW);

        assertThat(queue).isNotEmpty();
        var row = queue.stream()
                .filter(candidate -> candidate.reportedNickname().equals(alice.nickname()))
                .findFirst()
                .orElseThrow();
        assertThat(row.reporterNickname()).isEqualTo(bob.nickname());
        assertThat(row.reportsAgainst()).isEqualTo(1);
        assertThat(row.distinctReporters()).isEqualTo(1);

        var card = admin.card(row.report().getId());
        assertThat(card.game().getId().toString()).isEqualTo(gameId);
        assertThat(card.moves()).isEmpty();
    }

    @Test
    void theSameCrashTwiceIsOneGroup() {
        String stacktrace = "java.lang.NullPointerException: boom\n\tat com.x.Y.z(Y.kt:12)";
        CrashReportRequest request = new CrashReportRequest(
                "install-1", "1.20", 21, "Pixel", "14", "ru", "main",
                "java.lang.NullPointerException", "boom", stacktrace);

        telemetry.recordCrash(request, null);
        telemetry.recordCrash(request, null);

        var groups = telemetry.crashGroups();
        // Other tests leave their own crashes in the shared database: find ours.
        var group = groups.stream()
                .filter(candidate -> "java.lang.NullPointerException".equals(candidate.exception()))
                .findFirst()
                .orElseThrow();
        assertThat(group.count()).isEqualTo(2);
        assertThat(group.message()).isEqualTo("boom");
    }

    @Test
    void aCrashCanBeSentWithoutSigningIn() throws Exception {
        String body = """
                {"appVersion":"1.20","versionCode":21,"exception":"java.lang.IllegalStateException",
                 "stacktrace":"java.lang.IllegalStateException: x\\n\\tat com.x.Y.z(Y.kt:1)"}
                """;

        Response accepted = post("/api/v1/crash-reports", body, null);

        assertThat(accepted.status()).isEqualTo(202);
    }

    @Test
    void thePanelIsClosedToPlayersAndToAnonymousVisitors() throws Exception {
        Player player = newPlayer();

        assertThat(get("/admin/reports", null).status()).isNotEqualTo(200);
        assertThat(get("/admin/reports", player.token()).status()).isNotEqualTo(200);

        // Only a promoted account may sign in to the panel.
        assertThatThrownBy(() -> adminDetails.loadUserByUsername(player.nickname()))
                .isInstanceOf(UsernameNotFoundException.class);

        promote(player);
        assertThat(adminDetails.loadUserByUsername(player.nickname()).getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    private java.util.UUID idOf(Player player) {
        return users.findByNicknameIgnoreCase(player.nickname()).orElseThrow().getId();
    }

    private void promote(Player player) {
        User user = users.findByNicknameIgnoreCase(player.nickname()).orElseThrow();
        user.promoteToAdmin();
        users.save(user);
        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
    }
}