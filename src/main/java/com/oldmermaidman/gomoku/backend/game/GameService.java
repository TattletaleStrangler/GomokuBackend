package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.common.ApiError;
import com.oldmermaidman.gomoku.backend.common.ApiException;
import com.oldmermaidman.gomoku.backend.game.domain.Board;
import com.oldmermaidman.gomoku.backend.game.domain.ClockCalculator;
import com.oldmermaidman.gomoku.backend.game.domain.ColorChoice;
import com.oldmermaidman.gomoku.backend.game.domain.FinishReason;
import com.oldmermaidman.gomoku.backend.game.domain.GameResult;
import com.oldmermaidman.gomoku.backend.game.domain.GameStatus;
import com.oldmermaidman.gomoku.backend.game.domain.Referee;
import com.oldmermaidman.gomoku.backend.game.domain.Rule;
import com.oldmermaidman.gomoku.backend.game.domain.Stone;
import com.oldmermaidman.gomoku.backend.game.domain.TimeControl;
import com.oldmermaidman.gomoku.backend.game.domain.Visibility;
import com.oldmermaidman.gomoku.backend.game.dto.GameSnapshot;
import com.oldmermaidman.gomoku.backend.game.dto.LobbyGameView;
import com.oldmermaidman.gomoku.backend.config.GameProperties;
import com.oldmermaidman.gomoku.backend.user.UserRepository;
import com.oldmermaidman.gomoku.backend.ws.GameChanged;
import com.oldmermaidman.gomoku.backend.ws.GameEventType;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Rooms and games: the server is the only authority on what happens (TZ_BACKEND.md §10).
 *
 * <p>Every endpoint returns the full {@link GameSnapshot}, so a client never has to
 * patch its own copy: it replaces its state with what the server says. The referee
 * judges the position, the clocks are charged here, and a move that arrives with the
 * wrong move number is rejected instead of being guessed at.
 */
@Service
public class GameService {

    /** No {@code O/0/I/1}: a code is read out loud and typed by hand. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int CODE_ATTEMPTS = 20;

    private static final List<GameStatus> OPEN = List.of(GameStatus.WAITING, GameStatus.ACTIVE);
    private static final List<GameStatus> STARTED =
            List.of(GameStatus.ACTIVE, GameStatus.FINISHED, GameStatus.ABANDONED);

    /** The lobby shows at most this many rooms; one player can only ever hold one. */
    private static final int LOBBY_LIMIT = 50;

    private final GameRepository games;
    private final MoveRepository moves;
    private final UserRepository users;
    private final Clock clock;
    private final GameProperties properties;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;
    private final SecureRandom random = new SecureRandom();

    public GameService(
            GameRepository games,
            MoveRepository moves,
            UserRepository users,
            Clock clock,
            GameProperties properties,
            ApplicationEventPublisher events,
            EntityManager entityManager) {
        this.games = games;
        this.moves = moves;
        this.users = users;
        this.clock = clock;
        this.properties = properties;
        this.events = events;
        this.entityManager = entityManager;
    }

    // ---- rooms ------------------------------------------------------------

    @Transactional
    public GameSnapshot create(
            UUID userId,
            Rule rule,
            TimeControl timeControl,
            ColorChoice creatorColor,
            Visibility visibility) {
        Instant now = clock.instant();
        lockUser(userId);
        failIfPlaying(userId);

        // No choice means the creator takes black, the historical default; no
        // visibility means the room stays private to its code.
        ColorChoice color = creatorColor == null ? ColorChoice.BLACK : creatorColor;
        Visibility listed = visibility == null ? Visibility.PRIVATE : visibility;
        GameEntity game = GameEntity.waitingRoom(nextCode(), rule, timeControl, userId, color, listed, now);
        games.save(game);
        events.publishEvent(new GameChanged(game.getId(), GameEventType.GAME_STATE));
        return snapshot(game, userId, List.of());
    }

    /**
     * Quick match (FEATURES.md, priority B1): joins the oldest public room with the
     * same rule and time control, or opens one and waits in it.
     *
     * <p>The bucket lock serialises the find-or-create, so two players who press the
     * button at the same moment do not each open a room of their own. Within the
     * lock, {@code FOR UPDATE SKIP LOCKED} picks the row without queuing behind a
     * room another request is already taking.
     *
     * <p>With no colour asked for, a quick-play room is opened as {@code ANY}: the
     * opponent decides when they join.
     */
    @Transactional
    public GameSnapshot quickMatch(
            UUID userId, Rule rule, TimeControl timeControl, ColorChoice color) {
        Instant now = clock.instant();
        lockUser(userId);
        failIfPlaying(userId);

        lockMatchmakingBucket(rule, timeControl);

        Optional<GameEntity> waiting =
                games.findFirstWaitingPublicForUpdate(rule.name(), timeControl.name());
        if (waiting.isPresent()) {
            GameEntity game = waiting.get();
            game.join(userId, color, now);
            events.publishEvent(new GameChanged(game.getId(), GameEventType.OPPONENT_JOINED));
            return snapshot(game, userId, List.of());
        }

        ColorChoice creatorColor = color == null ? ColorChoice.ANY : color;
        GameEntity game = GameEntity.waitingRoom(
                nextCode(), rule, timeControl, userId, creatorColor, Visibility.PUBLIC, now);
        games.save(game);
        events.publishEvent(new GameChanged(game.getId(), GameEventType.GAME_STATE));
        return snapshot(game, userId, List.of());
    }

    /** Public rooms waiting for an opponent, oldest first (FEATURES.md, lobby). */
    @Transactional(readOnly = true)
    public List<LobbyGameView> lobby(Rule rule, TimeControl timeControl) {
        List<GameEntity> rooms = games.findLobby(
                rule == null ? null : rule.name(),
                timeControl == null ? null : timeControl.name(),
                LOBBY_LIMIT);
        List<UUID> creators = rooms.stream()
                .map(GameEntity::getBlackUserId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, String> nicknames = new HashMap<>();
        users.findAllById(creators).forEach(user -> nicknames.put(user.getId(), user.getNickname()));

        return rooms.stream()
                .map(room -> new LobbyGameView(
                        room.getId(),
                        room.getCode(),
                        room.getRule().name(),
                        room.getTimeControl().name(),
                        room.getCreatorColor().name(),
                        nicknames.get(room.getBlackUserId()),
                        room.getCreatedAt()))
                .toList();
    }

    /**
     * Closes public rooms nobody joined in time, so the lobby does not fill with
     * ghosts. Private rooms are left alone: their creator may be waiting for a
     * particular person.
     */
    @Transactional
    public int expireWaitingRooms() {
        Instant now = clock.instant();
        Duration ttl = properties.matchmaking().roomTtl();
        int expired = 0;
        for (GameEntity room : games.findByStatus(GameStatus.WAITING)) {
            if (room.getVisibility() != Visibility.PUBLIC
                    || !room.getCreatedAt().plus(ttl).isBefore(now)) {
                continue;
            }
            room.abandon(now);
            events.publishEvent(new GameChanged(room.getId(), GameEventType.GAME_STATE));
            expired++;
        }
        return expired;
    }

    /**
     * Serialises quick match for the rest of the transaction on a (rule, clock)
     * bucket. The lock lives in Postgres, so it holds across application instances.
     */
    private void lockMatchmakingBucket(Rule rule, TimeControl timeControl) {
        entityManager.createNativeQuery(
                        "select pg_advisory_xact_lock(hashtext('gomoku-matchmaking'), hashtext(?1))")
                .setParameter(1, rule.name() + ':' + timeControl.name())
                .getResultList();
    }

    @Transactional
    public GameSnapshot join(UUID userId, String rawCode, ColorChoice joinerColor) {
        Instant now = clock.instant();

        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        GameEntity game = games.findByCodeAndStatusIn(code, List.of(GameStatus.WAITING))
                .orElseThrow(() -> {
                    boolean started = games.existsByCodeAndStatusIn(code, STARTED);
                    return new ApiException(
                            started ? ApiError.ROOM_FULL : ApiError.ROOM_NOT_FOUND,
                            started ? "This room is already full" : "No room with that code");
                });

        // Own code is a more useful answer than "you are already playing".
        if (game.getBlackUserId().equals(userId)) {
            throw new ApiException(ApiError.SELF_JOIN, "You cannot join your own room");
        }

        lockUser(userId);
        failIfPlaying(userId);

        game.join(userId, joinerColor, now);
        events.publishEvent(new GameChanged(game.getId(), GameEventType.OPPONENT_JOINED));
        return snapshot(game, userId, List.of());
    }

    @Transactional(readOnly = true)
    public GameSnapshot get(UUID userId, UUID gameId) {
        return snapshotFor(userId, gameId);
    }

    /** The game as one of its players sees it; also used when pushing events. */
    @Transactional(readOnly = true)
    public GameSnapshot snapshotFor(UUID userId, UUID gameId) {
        GameEntity game = load(gameId);
        assertParticipant(game, userId);
        return snapshot(game, userId, movesOf(gameId));
    }

    /** The open game of a player, if any — used by the socket on connect. */
    @Transactional(readOnly = true)
    public Optional<UUID> openGameFor(UUID userId) {
        return games.findOpenFor(userId, OPEN).map(GameEntity::getId);
    }

    /** Both players of a game, for pushing events to their sockets. */
    @Transactional(readOnly = true)
    public List<UUID> participantsOf(UUID gameId) {
        GameEntity game = load(gameId);
        return Stream.of(game.getBlackUserId(), game.getWhiteUserId()).filter(Objects::nonNull).toList();
    }

    // ---- game actions -----------------------------------------------------

    @Transactional
    public GameSnapshot move(UUID userId, UUID gameId, int ply, int x, int y) {
        Instant now = clock.instant();
        GameEntity game = load(gameId);
        assertParticipant(game, userId);

        // A flag that fell before this request wins over the move itself.
        expireIfFlagged(game, now);
        if (game.getStatus() != GameStatus.ACTIVE) {
            throw new ApiException(ApiError.GAME_FINISHED, "The game is not running");
        }
        // While a request is pending the opponent answers it instead of moving.
        if (game.hasPendingUndo()) {
            throw new ApiException(ApiError.UNDO_ALREADY_REQUESTED, "Answer the undo request first");
        }

        Stone colour = game.colourOf(userId);
        if (game.turn() != colour) {
            throw new ApiException(ApiError.NOT_YOUR_TURN, "It is the other player's turn",
                    Map.of("turn", game.turn().name()));
        }
        if (ply != game.getPly()) {
            throw new ApiException(ApiError.WRONG_PLY, "Wrong move number",
                    Map.of("expectedPly", game.getPly()));
        }

        Board board = boardOf(movesOf(gameId));
        Referee.MoveResult result = Referee.apply(board, x, y, colour, game.getRule());
        switch (result.outcome()) {
            case INVALID -> throw new ApiException(ApiError.OUT_OF_BOARD, "Cell is outside the board");
            case OCCUPIED -> throw new ApiException(ApiError.CELL_OCCUPIED, "Cell is already taken");
            default -> {
                // PLAYED: the stone is on the board and the position is judged below.
            }
        }

        Long remainingAfter = chargeClock(game, colour, now);
        moves.save(new MoveEntity(gameId, ply, x, y, colour, now, remainingAfter));
        finishIfOver(game, result, colour, now);

        events.publishEvent(new GameChanged(
                gameId,
                game.getStatus() == GameStatus.FINISHED
                        ? GameEventType.GAME_FINISHED
                        : GameEventType.MOVE_APPLIED));
        return snapshot(game, userId, movesOf(gameId));
    }

    @Transactional
    public GameSnapshot resign(UUID userId, UUID gameId) {
        Instant now = clock.instant();
        GameEntity game = load(gameId);
        assertParticipant(game, userId);

        expireIfFlagged(game, now);
        if (game.getStatus() != GameStatus.ACTIVE) {
            throw new ApiException(ApiError.GAME_FINISHED, "The game is not running");
        }

        Stone colour = game.colourOf(userId);
        game.finish(
                colour == Stone.BLACK ? GameResult.WHITE_WIN : GameResult.BLACK_WIN,
                FinishReason.RESIGN,
                now);
        events.publishEvent(new GameChanged(gameId, GameEventType.GAME_FINISHED));
        return snapshot(game, userId, movesOf(gameId));
    }

    /** Leaving a waiting room closes it; leaving a running game is a resignation. */
    @Transactional
    public GameSnapshot leave(UUID userId, UUID gameId) {
        Instant now = clock.instant();
        GameEntity game = load(gameId);
        assertParticipant(game, userId);

        if (game.getStatus() == GameStatus.WAITING) {
            game.abandon(now);
        } else if (game.getStatus() == GameStatus.ACTIVE) {
            expireIfFlagged(game, now);
            if (game.getStatus() == GameStatus.ACTIVE) {
                Stone colour = game.colourOf(userId);
                game.finish(
                        colour == Stone.BLACK ? GameResult.WHITE_WIN : GameResult.BLACK_WIN,
                        FinishReason.ABANDON,
                        now);
            }
        }
        events.publishEvent(new GameChanged(gameId, GameEventType.GAME_FINISHED));
        return snapshot(game, userId, movesOf(gameId));
    }

    // ---- undo requests ----------------------------------------------------

    /**
     * Asks the opponent to take the last move back.
     *
     * <p>Allowed only in the first seconds after the move ({@code app.game.undo.window}),
     * once per move and at most {@code app.game.undo.limit} times per game. While the
     * request is pending the only running clock — the opponent's — is frozen.
     */
    @Transactional
    public GameSnapshot requestUndo(UUID userId, UUID gameId) {
        Instant now = clock.instant();
        GameEntity game = load(gameId);
        assertParticipant(game, userId);

        expireIfFlagged(game, now);
        requireActive(game);

        if (game.hasPendingUndo()) {
            throw new ApiException(ApiError.UNDO_ALREADY_REQUESTED, "An undo request is already pending");
        }

        Stone me = game.colourOf(userId);
        if (game.turn() == me) {
            throw new ApiException(ApiError.UNDO_NOT_ALLOWED, "You have no move to take back");
        }

        List<MoveEntity> played = movesOf(gameId);
        MoveEntity last = played.isEmpty() ? null : played.getLast();
        if (last == null || last.getColor() != me) {
            throw new ApiException(ApiError.UNDO_TOO_LATE, "The opponent has already moved");
        }
        if (Duration.between(last.getPlayedAt(), now).compareTo(properties.undo().window()) > 0) {
            throw new ApiException(ApiError.UNDO_TOO_LATE, "Too late to take the move back");
        }
        if (last.isUndoRequested()) {
            throw new ApiException(ApiError.UNDO_ALREADY_REQUESTED, "This move was already asked about");
        }
        if (game.undosUsedBy(me) >= properties.undo().limit()) {
            throw new ApiException(ApiError.UNDO_LIMIT_REACHED, "No undo requests left");
        }

        last.markUndoRequested();
        game.requestUndo(userId, last.getPly(), now.plus(properties.undo().decisionTimeout()));

        if (game.getTimeControl().isTimed()) {
            Stone toMove = game.turn();
            long stored = Objects.requireNonNull(game.remainingOf(toMove), "timed game without a clock");
            game.freezeClock(toMove, ClockCalculator.remainingAt(now, game.getTurnStartedAt(), stored));
        }

        events.publishEvent(new GameChanged(gameId, GameEventType.UNDO_REQUESTED));
        return snapshot(game, userId, played);
    }

    /**
     * The opponent answers. A late answer counts as a refusal: the deadline belongs to
     * the server, and the same rule closes a request nobody answered at all.
     */
    @Transactional
    public GameSnapshot decideUndo(UUID userId, UUID gameId, boolean accept) {
        Instant now = clock.instant();
        GameEntity game = load(gameId);
        assertParticipant(game, userId);
        requireActive(game);

        if (!game.hasPendingUndo()) {
            throw new ApiException(ApiError.UNDO_NOT_REQUESTED, "There is no undo request");
        }
        if (game.getUndoRequestedBy().equals(userId)) {
            throw new ApiException(ApiError.UNDO_NOT_REQUESTED, "The opponent decides, not the requester");
        }

        if (!accept || !now.isBefore(game.getUndoDeadline())) {
            game.clearUndoRequest();
            game.resumeClock(now);
            events.publishEvent(new GameChanged(gameId, GameEventType.UNDO_DECIDED));
            return snapshot(game, userId, movesOf(gameId));
        }

        List<MoveEntity> played = movesOf(gameId);
        MoveEntity undone = played.getLast();
        Stone mover = undone.getColor();

        // The move had earned its increment; taking it back takes the increment too.
        Long remaining = game.getTimeControl().isTimed()
                ? Math.max(0L, Objects.requireNonNull(game.remainingOf(mover), "timed game without a clock")
                        - game.getTimeControl().incrementMs())
                : null;

        game.acceptUndo(mover, undone.getPly(), undone.getX(), undone.getY(), remaining, now);
        moves.delete(undone);

        events.publishEvent(new GameChanged(gameId, GameEventType.UNDO_APPLIED));
        return snapshot(game, userId, movesOf(gameId));
    }

    /**
     * Closes requests nobody answered, so a request cannot outlive its deadline even
     * if the opponent has gone offline. Called by the same sweeper as the clocks.
     *
     * @return how many requests were refused by this call
     */
    @Transactional
    public int expireUndoRequests() {
        Instant now = clock.instant();
        int expired = 0;
        for (GameEntity game : games.findByStatus(GameStatus.ACTIVE)) {
            if (!game.hasPendingUndo() || now.isBefore(game.getUndoDeadline())) {
                continue;
            }
            game.clearUndoRequest();
            game.resumeClock(now);
            events.publishEvent(new GameChanged(game.getId(), GameEventType.UNDO_DECIDED));
            expired++;
        }
        return expired;
    }

    // ---- timeouts ---------------------------------------------------------

    /**
     * Finishes every running game whose clock ran out. Called by the sweeper, so a
     * game ends even when both players have gone silent.
     *
     * @return how many games were finished by this call
     */
    @Transactional
    public int expireFlagged() {
        Instant now = clock.instant();
        int finished = 0;
        for (GameEntity game : games.findByStatus(GameStatus.ACTIVE)) {
            expireIfFlagged(game, now);
            if (game.getStatus() != GameStatus.ACTIVE) {
                events.publishEvent(new GameChanged(game.getId(), GameEventType.GAME_FINISHED));
                finished++;
            }
        }
        return finished;
    }

    // ---- internals --------------------------------------------------------

    private static void requireActive(GameEntity game) {
        if (game.getStatus() != GameStatus.ACTIVE) {
            throw new ApiException(ApiError.GAME_FINISHED, "The game is not running");
        }
    }

    private GameEntity load(UUID gameId) {
        return games.findById(gameId)
                .orElseThrow(() -> new ApiException(ApiError.GAME_NOT_FOUND, "No such game"));
    }

    private static void assertParticipant(GameEntity game, UUID userId) {
        if (!game.isParticipant(userId)) {
            throw new ApiException(ApiError.NOT_A_PARTICIPANT, "You are not playing this game");
        }
    }

    private void lockUser(UUID userId) {
        users.lockById(userId)
                .orElseThrow(() -> new ApiException(ApiError.UNAUTHORIZED, "Unknown account"));
    }

    private void failIfPlaying(UUID userId) {
        games.findOpenFor(userId, OPEN).ifPresent(open -> {
            throw new ApiException(
                    ApiError.ACTIVE_GAME_EXISTS,
                    "Finish or leave the current game first",
                    Map.of("gameId", open.getId().toString()));
        });
    }

    private void expireIfFlagged(GameEntity game, Instant now) {
        if (game.getStatus() != GameStatus.ACTIVE || !game.getTimeControl().isTimed()) {
            return;
        }
        Stone mover = game.turn();
        long stored = Objects.requireNonNull(game.remainingOf(mover), "timed game without a clock");

        if (ClockCalculator.hasFlagFallen(now, game.getTurnStartedAt(), stored)) {
            game.flag(mover, now);
        }
    }

    private Long chargeClock(GameEntity game, Stone mover, Instant now) {
        if (!game.getTimeControl().isTimed()) {
            game.chargeMove(mover, 0L, now);
            return null;
        }
        long stored = Objects.requireNonNull(game.remainingOf(mover), "timed game without a clock");
        long remainingAfter = ClockCalculator.afterMove(
                now, game.getTurnStartedAt(), stored, game.getTimeControl().incrementMs());
        game.chargeMove(mover, remainingAfter, now);
        return remainingAfter;
    }

    private static void finishIfOver(GameEntity game, Referee.MoveResult result, Stone colour, Instant now) {
        switch (result.outcome()) {
            case WIN -> game.finish(
                    colour == Stone.BLACK ? GameResult.BLACK_WIN : GameResult.WHITE_WIN,
                    FinishReason.FIVE,
                    now);
            case FORBIDDEN -> game.finish(GameResult.WHITE_WIN, FinishReason.FORBIDDEN, now);
            case DRAW -> game.finish(GameResult.DRAW, FinishReason.NONE, now);
            default -> {
                // The game continues.
            }
        }
    }

    private List<MoveEntity> movesOf(UUID gameId) {
        return moves.findByGameIdOrderByPlyAsc(gameId);
    }

    private static Board boardOf(List<MoveEntity> played) {
        Board board = new Board();
        for (MoveEntity move : played) {
            board.place(move.getX(), move.getY(), move.getColor());
        }
        return board;
    }

    private String nextCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!games.existsByCodeAndStatusIn(code, OPEN)) {
                return code;
            }
        }
        throw new ApiException(ApiError.INTERNAL_ERROR, "Could not allocate a room code");
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    // ---- snapshots --------------------------------------------------------

    private GameSnapshot snapshot(GameEntity game, UUID viewerId, List<MoveEntity> played) {
        Instant now = clock.instant();
        Map<UUID, String> nicknames = nicknamesOf(game);
        Stone viewer = game.colourOf(viewerId);
        // Whose move it is stays known even while a pending request freezes the clock:
        // `turn` says who plays, `clocks.running` says whether time is spent.
        Stone turn = game.getStatus() == GameStatus.ACTIVE ? game.turn() : null;
        Stone running = turn != null && game.getTurnStartedAt() != null ? turn : null;

        return new GameSnapshot(
                game.getId(),
                game.getCode(),
                game.getRule().name(),
                game.getTimeControl().name(),
                game.getStatus().name(),
                player(game.getBlackUserId(), nicknames),
                player(game.getWhiteUserId(), nicknames),
                viewer == null ? null : viewer.name(),
                game.getPly(),
                turn == null ? null : turn.name(),
                played.stream()
                        .map(m -> new GameSnapshot.MoveView(m.getPly(), m.getX(), m.getY(), m.getColor().name()))
                        .toList(),
                clocksOf(game, now, running),
                now,
                game.getResult().name(),
                game.getFinishReason().name(),
                undoRemainingOf(game, viewer),
                undoView(game, viewer),
                ghostView(game, viewer));
    }

    /** What the "Отмена" button shows: the viewer's attempts left in this game. */
    private int undoRemainingOf(GameEntity game, Stone viewer) {
        if (viewer == null) {
            return 0;
        }
        return Math.max(0, properties.undo().limit() - game.undosUsedBy(viewer));
    }

    /** The pending request, if any. */
    private static GameSnapshot.UndoView undoView(GameEntity game, Stone viewer) {
        if (!game.hasPendingUndo() || viewer == null) {
            return null;
        }
        Stone requester = game.colourOf(game.getUndoRequestedBy());
        return new GameSnapshot.UndoView(
                requester == null ? null : requester.name(),
                game.getUndoDeadline(),
                game.getUndoPly() == null ? 0 : game.getUndoPly());
    }

    /**
     * The stone an accepted undo removed. Only its owner sees it: the opponent is not
     * told, so the client cannot draw a ghost that should not be there.
     */
    private static GameSnapshot.MoveView ghostView(GameEntity game, Stone viewer) {
        if (game.getUndoGhostPly() == null
                || game.getUndoGhostColor() == null
                || game.getUndoGhostColor() != viewer) {
            return null;
        }
        return new GameSnapshot.MoveView(
                game.getUndoGhostPly(),
                game.getUndoGhostX(),
                game.getUndoGhostY(),
                game.getUndoGhostColor().name());
    }

    private static GameSnapshot.PlayerView player(UUID userId, Map<UUID, String> nicknames) {
        return userId == null ? null : new GameSnapshot.PlayerView(userId, nicknames.get(userId));
    }

    private static GameSnapshot.ClockView clocksOf(GameEntity game, Instant now, Stone running) {
        if (!game.getTimeControl().isTimed()) {
            return new GameSnapshot.ClockView(null, null, null);
        }
        return new GameSnapshot.ClockView(
                liveRemaining(game, Stone.BLACK, now, running),
                liveRemaining(game, Stone.WHITE, now, running),
                running == null ? null : running.name());
    }

    private static Long liveRemaining(GameEntity game, Stone colour, Instant now, Stone running) {
        Long stored = game.remainingOf(colour);
        if (stored == null) {
            return null;
        }
        if (running != colour) {
            return stored;
        }
        return ClockCalculator.remainingAt(now, game.getTurnStartedAt(), stored);
    }

    private Map<UUID, String> nicknamesOf(GameEntity game) {
        List<UUID> ids = Stream.of(game.getBlackUserId(), game.getWhiteUserId())
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, String> nicknames = new HashMap<>();
        users.findAllById(ids).forEach(user -> nicknames.put(user.getId(), user.getNickname()));
        return nicknames;
    }
}