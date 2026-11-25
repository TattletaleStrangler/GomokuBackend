package com.oldmermaidman.gomoku.backend.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Promotes the configured account to administrator on startup (FEATURES.md, B4).
 *
 * <p>There is no way to become an admin from the game, on purpose. The owner registers
 * an ordinary account, sets {@code ADMIN_NICKNAME} to that nickname and restarts; from
 * then on the account may open {@code /admin}. With no nickname configured nothing
 * happens, which is the right default for a server that is not administered yet.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final String nickname;

    public AdminBootstrap(
            UserRepository users,
            @Value("${app.admin.nickname:}") String nickname) {
        this.users = users;
        this.nickname = nickname;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (nickname.isBlank()) {
            log.info("app.admin.nickname is empty: the admin panel has no administrator yet");
            return;
        }
        users.findByNicknameIgnoreCase(nickname).ifPresentOrElse(
                user -> {
                    if (user.getRole() == UserRole.ADMIN) {
                        log.info("Administrator '{}' is ready", user.getNickname());
                        return;
                    }
                    user.promoteToAdmin();
                    users.save(user);
                    log.info("Promoted '{}' to administrator", user.getNickname());
                },
                () -> log.warn(
                        "app.admin.nickname='{}' has no account yet: register it, then restart",
                        nickname));
    }
}