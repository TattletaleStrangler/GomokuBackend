package com.oldmermaidman.gomoku.backend.game.domain;

/** A board cell, zero-based: {@code (0,0)} is the top-left corner. */
public record Point(int x, int y) {
}