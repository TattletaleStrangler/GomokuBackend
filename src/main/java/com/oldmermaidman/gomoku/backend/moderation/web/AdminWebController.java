package com.oldmermaidman.gomoku.backend.moderation.web;

import com.oldmermaidman.gomoku.backend.game.MoveEntity;
import com.oldmermaidman.gomoku.backend.moderation.ActionKind;
import com.oldmermaidman.gomoku.backend.moderation.AdminService;
import com.oldmermaidman.gomoku.backend.moderation.ReportStatus;
import com.oldmermaidman.gomoku.backend.telemetry.TelemetryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The admin panel (FEATURES.md, B4): server-rendered pages behind a session login.
 *
 * <p>It is deliberately small: a queue of reports with the context needed to judge them,
 * the same for server errors and client crashes, and one form that applies a decision.
 * Everything it changes is recorded in {@code admin_actions}.
 */
@Controller
@RequestMapping("/admin")
public class AdminWebController {

    private final AdminService admin;
    private final TelemetryService telemetry;

    public AdminWebController(AdminService admin, TelemetryService telemetry) {
        this.admin = admin;
        this.telemetry = telemetry;
    }

    @GetMapping("/login")
    public String login() {
        return "admin/login";
    }

    @GetMapping
    public String home() {
        return "redirect:/admin/reports";
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) ReportStatus status, Model model) {
        model.addAttribute("rows", admin.queue(status));
        model.addAttribute("status", status);
        model.addAttribute("statuses", ReportStatus.values());
        addCounters(model);
        return "admin/reports";
    }

    @GetMapping("/reports/{id}")
    public String report(@PathVariable UUID id, Model model) {
        var card = admin.card(id);
        model.addAttribute("card", card);
        model.addAttribute("moveLabels", labels(card.moves()));
        model.addAttribute("actions", ActionKind.values());
        addCounters(model);
        return "admin/report";
    }

    @GetMapping("/users/{id}")
    public String user(@PathVariable UUID id, Model model) {
        model.addAttribute("card", admin.userCard(id));
        model.addAttribute("actions", ActionKind.values());
        addCounters(model);
        return "admin/user";
    }

    @PostMapping("/reports/{id}/act")
    public String act(
            @PathVariable UUID id,
            @RequestParam ActionKind action,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Integer days,
            Principal principal) {
        admin.act(admin.adminId(principal.getName()), id, null, action, note, days);
        return "redirect:/admin/reports/" + id;
    }

    @PostMapping("/users/{id}/act")
    public String actOnUser(
            @PathVariable UUID id,
            @RequestParam ActionKind action,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Integer days,
            Principal principal) {
        admin.act(admin.adminId(principal.getName()), null, id, action, note, days);
        return "redirect:/admin/users/" + id;
    }

    @GetMapping("/errors")
    public String errors(Model model) {
        model.addAttribute("errors", telemetry.recentErrors());
        addCounters(model);
        return "admin/errors";
    }

    @GetMapping("/crashes")
    public String crashes(Model model) {
        model.addAttribute("groups", telemetry.crashGroups());
        addCounters(model);
        return "admin/crashes";
    }

    private void addCounters(Model model) {
        Instant dayAgo = Instant.now().minus(Duration.ofDays(1));
        model.addAttribute("openReports", admin.openReports());
        model.addAttribute("errorsToday", telemetry.serverErrorsSince(dayAgo));
        model.addAttribute("crashesToday", telemetry.crashesSince(dayAgo));
    }

    /** Board coordinates the way the players see them: column letter and row number. */
    private static List<String> labels(List<MoveEntity> moves) {
        return moves.stream()
                .map(move -> "" + (char) ('A' + move.getX()) + (15 - move.getY()))
                .toList();
    }
}