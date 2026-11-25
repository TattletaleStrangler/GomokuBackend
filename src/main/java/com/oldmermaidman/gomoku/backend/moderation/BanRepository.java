package com.oldmermaidman.gomoku.backend.moderation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BanRepository extends JpaRepository<BanEntity, UUID> {

    /** True when the account is banned right now — used by login and by matchmaking. */
    @Query("select count(b) > 0 from BanEntity b where b.userId = :userId and b.until > :now")
    boolean isActive(@Param("userId") UUID userId, @Param("now") Instant now);

    /** True only for a ban that closes the whole account, not just ranked play. */
    @Query("""
            select count(b) > 0 from BanEntity b
            where b.userId = :userId and b.until > :now and b.kind = 'ACCOUNT'
            """)
    boolean isAccountBanned(@Param("userId") UUID userId, @Param("now") Instant now);

    /** The bans of an account, newest first, for the user card. */
    List<BanEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}