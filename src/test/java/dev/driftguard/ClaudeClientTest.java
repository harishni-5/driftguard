package dev.driftguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.driftguard.agents.deaddep.DeadDepAgent.Decision;
import dev.driftguard.agents.deaddep.DeadDepAgent.VerificationResponse;
import dev.driftguard.llm.ClaudeClient;
import dev.driftguard.llm.LlmClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the real SDK against a local fake of the API. This covers the request we
 * build and how we read responses. It can't prove the live API accepts the request:
 * that needs a real key.
 */
class ClaudeClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<JsonNode> lastRequest = new AtomicReference<>();
    private volatile String responseBody;
    private volatile int responseStatus = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            lastRequest.set(JSON.readTree(exchange.getRequestBody()));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ClaudeClient client() {
        return new ClaudeClient(AnthropicOkHttpClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .apiKey("test-key")
                .maxRetries(0)
                .build(), "claude-sonnet-5-5");
    }

    private static String message(String text, String stopReason) throws IOException {
        return JSON.writeValueAsString(JSON.createObjectNode()
                .put("id", "msg_1").put("type", "message").put("role", "assistant").put("model", "claude-sonnet-5-5")
                .put("stop_reason", stopReason)
                .putNull("stop_sequence")
                .<com.fasterxml.jackson.databind.node.ObjectNode>set("content", JSON.createArrayNode()
                        .add(JSON.createObjectNode().put("type", "text").put("text", text)))
                .set("usage", JSON.createObjectNode().put("input_tokens", 1000).put("output_tokens", 500)));
    }

    @Test
    void sendsSchemaAndReadsTypedAnswerWithCost() throws Exception {
        responseBody = message("{\"verdicts\":[{\"id\":\"a#b\",\"decision\":\"UNUSED\",\"reason\":\"none\"}]}", "end_turn");

        LlmClient.Result<VerificationResponse> result =
                client().structured("system text", "user text", VerificationResponse.class);

        assertEquals(Decision.UNUSED, result.value().verdicts().get(0).decision());
        assertEquals(1000, result.inputTokens());
        assertEquals(500, result.outputTokens());
        assertEquals(0.007, result.costUsd(), 1e-9); // 1000 x $2/M + 500 x $10/M

        JsonNode request = lastRequest.get();
        assertEquals("claude-sonnet-5-5", request.path("model").asText());
        assertEquals("json_schema", request.path("output_config").path("format").path("type").asText());
        assertTrue(request.path("output_config").path("format").path("schema").path("properties").has("verdicts"));
    }

    @Test
    void refusalBecomesAnErrorNotAVerdict() throws Exception {
        responseBody = message("", "refusal");
        LlmClient.LlmException error = assertThrows(LlmClient.LlmException.class,
                () -> client().structured("s", "u", VerificationResponse.class));
        assertTrue(error.getMessage().contains("refusal"));
    }

    @Test
    void truncatedAnswerBecomesAnError() throws Exception {
        responseBody = message("{\"verdicts\":[", "max_tokens");
        assertThrows(LlmClient.LlmException.class, () -> client().structured("s", "u", VerificationResponse.class));
    }

    @Test
    void apiErrorBecomesAnError() throws Exception {
        responseStatus = 401;
        responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
        assertThrows(LlmClient.LlmException.class, () -> client().structured("s", "u", VerificationResponse.class));
    }

    @Test
    void invalidJsonBecomesAnError() throws Exception {
        responseBody = message("not json at all", "end_turn");
        assertThrows(LlmClient.LlmException.class, () -> client().structured("s", "u", VerificationResponse.class));
    }
}
