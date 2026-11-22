package com.oldmermaidman.gomoku.backend.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MoveRepository extends JpaRepository<MoveEntity, MoveId> {

    /** Every move of a game in play order. */
    List<MoveEntity> findByGameIdOrderByPlyAsc(UUID gameId);
}