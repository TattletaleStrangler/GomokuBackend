package com.oldmermaidman.gomoku.backend.ws;

import com.oldmermaidman.gomoku.backend.testsupport.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The socket feed: authentication of the handshake, the state on connect and the
 * events that follow a move and the end of a game.
 */
@TestPropertySource(properties = {
        "app.rate-limit.per-ip=1000",
        "app.rate-limit.max-failed-logins=1000",
        "app.game.timeout-sweeper-enabled=false"
})
class GameSocketTest extends ApiTestSupport {

    private static final int TIMEOUT_SECONDS = 10;

    private record Player(String token, String nickname) {
    }

    private Player newPlayer() throws Exception {
        String nickname = nickname();
        Response registered = register(nickname, "password-123");
        assertThat(registered.status()).isEqualTo(201);
        return new Player(registered.text("$.accessToken"), nickname);
    }

    private String openGame(Player creator, Player opponent, String timeControl) throws Exception {
        Response created = post("/api/v1/games",
                "{\"rule\":\"FREESTYLE\",\"timeControl\":\"" + timeControl + "\"}", creator.token());
        String code = created.text("$.code");
        assertThat(post("/api/v1/games/join", "{\"code\":\"" + code + "\"}", opponent.token()).status())
                .isEqualTo(200);
        return created.text("$.gameId");
    }

    private Response move(Player player, String gameId, int ply, int x, int y) throws Exception {
        return post("/api/v1/games/" + gameId + "/moves",
                "{\"ply\":" + ply + ",\"x\":" + x + ",\"y\":" + y + "}", player.token());
    }

    /** Opens the socket and collects every message into the queue. */
    private WebSocket openSocket(String token, BlockingQueue<String> messages) throws Exception {
        WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.buildAsync(URI.create("ws://localhost:" + port() + "/ws/game"), new WebSocket.Listener() {
            private final StringBuilder partial = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                partial.append(data);
                if (last) {
                    messages.add(partial.toString());
                    partial.setLength(0);
                }
                socket.request(1);
                return null;
            }
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static String next(BlockingQueue<String> messages) throws InterruptedException {
        return messages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void theOpponentSeesEveryMove() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");

        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocket socket = openSocket(white.token(), messages);
        try {
            // On connect the server sends the current state.
            String state = next(messages);
            assertThat(state).contains("\"type\":\"GAME_STATE\"").contains("\"status\":\"ACTIVE\"");

            assertThat(move(black, gameId, 1, 7, 7).status()).isEqualTo(200);

            String event = next(messages);
            assertThat(event)
                    .contains("\"type\":\"MOVE_APPLIED\"")
                    .contains("\"ply\":2")
                    .contains("\"x\":7")
                    .contains("\"y\":7");
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    @Test
    void theEndOfTheGameArrivesOnTheSocket() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");

        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocket socket = openSocket(white.token(), messages);
        try {
            assertThat(next(messages)).contains("\"type\":\"GAME_STATE\"");

            int[][] blackStones = {{3, 7}, {4, 7}, {5, 7}, {6, 7}, {7, 7}};
            int[][] whiteStones = {{0, 0}, {2, 2}, {4, 4}, {6, 6}};
            for (int i = 0; i < whiteStones.length; i++) {
                move(black, gameId, 2 * i + 1, blackStones[i][0], blackStones[i][1]);
                move(white, gameId, 2 * i + 2, whiteStones[i][0], whiteStones[i][1]);
            }
            // The winning move is the ninth; white's socket receives it as the end.
            assertThat(move(black, gameId, 9, 7, 7).status()).isEqualTo(200);

            String last = null;
            for (int seen = 0; seen < 10; seen++) {
                String event = next(messages);
                if (event == null) {
                    break;
                }
                last = event;
                if (event.contains("\"type\":\"GAME_FINISHED\"")) {
                    break;
                }
            }

            assertThat(last)
                    .contains("\"type\":\"GAME_FINISHED\"")
                    .contains("\"result\":\"BLACK_WIN\"")
                    .contains("\"finishReason\":\"FIVE\"");
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    @Test
    void aSocketWithoutATokenIsRejected() {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        assertThatThrownBy(() -> openSocket(null, messages))
                .isInstanceOf(ExecutionException.class);
    }
}