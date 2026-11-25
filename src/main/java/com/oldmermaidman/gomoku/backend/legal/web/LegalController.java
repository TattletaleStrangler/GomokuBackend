package com.oldmermaidman.gomoku.backend.legal.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The two public pages Google Play asks for (PLAY_RELEASE.md): a privacy policy and a
 * way to ask for account deletion.
 *
 * <p>They are plain HTML, open to everyone and deliberately independent of the game:
 * the store listing links to them, the app links to the privacy policy from its
 * settings, and neither needs an account or JavaScript.
 */
@Controller
@RequestMapping("/legal")
public class LegalController {

    private final String contactEmail;

    public LegalController(@Value("${app.legal.contact-email:}") String contactEmail) {
        this.contactEmail = contactEmail;
    }

    @GetMapping
    public String index() {
        return "redirect:/legal/privacy";
    }

    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute("contactEmail", blankToNull(contactEmail));
        return "legal/privacy";
    }

    @GetMapping("/delete-account")
    public String deleteAccount(Model model) {
        model.addAttribute("contactEmail", blankToNull(contactEmail));
        return "legal/delete-account";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}