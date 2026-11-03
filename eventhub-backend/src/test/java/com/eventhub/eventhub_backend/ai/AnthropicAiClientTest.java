package com.eventhub.eventhub_backend.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicAiClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastApiKey = new AtomicReference<>();
    private final AtomicReference<String> lastVersion = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "{}";
    private volatile long delayMillis = 0;

    @BeforeEach
    void startFakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/messages", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastApiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            lastVersion.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopFakeServer() {
        server.stop(0);
    }

    @Test
    void sendsTheKeyAndTool_andReturnsTheToolInput() {
        responseBody = """
                {"id":"msg_1","type":"message","role":"assistant","stop_reason":"tool_use",
                 "content":[{"type":"tool_use","id":"t1","name":"propose_event",
                             "input":{"title":"Hack Night","free":true}}],
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """;

        Map<String, Object> result = client("secret-key").callTool(
                "system text", "user text", "propose_event", "describe", Map.of("type", "object"), 512);

        assertThat(result).containsEntry("title", "Hack Night").containsEntry("free", true);
        assertThat(lastApiKey.get()).isEqualTo("secret-key");
        assertThat(lastVersion.get()).isEqualTo("2023-06-01");
        assertThat(lastBody.get())
                .contains("\"model\":\"test-model\"")
                .contains("\"max_tokens\":512")
                .contains("\"tool_choice\"")
                .contains("\"name\":\"propose_event\"")
                .contains("system text")
                .contains("user text");
    }

    @Test
    void completeJoinsTheTextBlocks() {
        responseBody = """
                {"content":[{"type":"text","text":"Hello"},{"type":"text","text":"there"}]}
                """;

        assertThat(client("k").complete("sys", "hi", 100)).isEqualTo("Hello\nthere");
    }

    @Test
    void withoutAKey_nothingIsSent() {
        assertThat(client("").isConfigured()).isFalse();

        assertFailure(503, () -> client("").complete("sys", "hi", 100));
        assertThat(lastBody.get()).isNull();
    }

    @Test
    void failuresFromTheServiceAreTranslated() {
        responseBody = "{\"error\":\"nope\"}";

        status = 401;
        assertFailure(503, () -> client("k").complete("s", "u", 10));
        status = 404;
        assertFailure(503, () -> client("k").complete("s", "u", 10));
        status = 429;
        assertFailure(429, () -> client("k").complete("s", "u", 10));
        status = 500;
        assertFailure(502, () -> client("k").complete("s", "u", 10));
    }

    @Test
    void aWrongToolInTheAnswer_isRejected() {
        responseBody = """
                {"content":[{"type":"tool_use","id":"t1","name":"something_else","input":{"a":1}}]}
                """;

        assertFailure(502, () -> client("k").callTool("s", "u", "propose_event", "d", Map.of(), 10));
    }

    @Test
    void aSlowService_timesOut() {
        responseBody = "{\"content\":[]}";
        delayMillis = 2500;
        AnthropicAiClient impatient = new AnthropicAiClient("k", "test-model", baseUrl(), 1);

        assertFailure(504, () -> impatient.complete("s", "u", 10));
    }

    @Test
    void aResponseWithNoContent_isRejected() {
        responseBody = "{\"content\":null}";

        assertFailure(502, () -> client("k").complete("s", "u", 10));
        assertThat(List.of()).isEmpty();
    }

    private AnthropicAiClient client(String key) {
        return new AnthropicAiClient(key, "test-model", baseUrl(), 5);
    }

    private String baseUrl() {
        return "http://" + server.getAddress().getAddress().getHostAddress() + ":" + server.getAddress().getPort();
    }

    private void assertFailure(int expectedStatus, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(expectedStatus));
    }
}