package com.oldmermaidman.gomoku.backend.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Sign-in for the admin panel (FEATURES.md, B4): the same nickname and password as the
 * game, but only an account with the {@code ADMIN} role is accepted.
 *
 * <p>A player who tries the panel gets the same "bad credentials" as a wrong password,
 * so the form does not reveal which accounts exist.
 */
@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AdminUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String nickname) throws UsernameNotFoundException {
        User user = users.findByNicknameIgnoreCase(nickname)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new UsernameNotFoundException("no such account"));
        if (user.getRole() != UserRole.ADMIN) {
            throw new UsernameNotFoundException("not an administrator");
        }
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getNickname())
                .password(user.getPasswordHash())
                .authorities("ROLE_ADMIN")
                .build();
    }
}