package dev.driftguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.driftguard.agents.AuditAgent;
import dev.driftguard.agents.Finding.DecidedBy;
import dev.driftguard.agents.deaddep.DeadDepAgent.Decision;
import dev.driftguard.agents.deaddep.DeadDepAgent.VerificationResponse;
import dev.driftguard.llm.LlmClient;
import dev.driftguard.llm.LlmFactory;
import dev.driftguard.llm.OpenAiCompatibleClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers the OpenAI-format client against a local fake server, and provider selection. */
class FreeLlmTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GOOD = "{\"verdicts\":[{\"id\":\"a#b\",\"decision\":\"USED\",\"reason\":\"driver\"}]}";

    private record Reply(int status, String body, String retryAfter) {}

    private HttpServer server;
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final AtomicReference<JsonNode> lastRequest = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile String modelsBody = "{\"data\":[]}";

    @TempDir
    Path dir;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            lastRequest.set(JSON.readTree(exchange.getRequestBody()));
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            Reply reply = replies.remove();
            if (reply.retryAfter() != null) exchange.getResponseHeaders().add("Retry-After", reply.retryAfter());
            send(exchange, reply.status(), reply.body());
        });
        server.createContext("/v1/models", exchange -> send(exchange, 200, modelsBody));
        server.start();
    }

    private static void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private OpenAiCompatibleClient client(String key) {
        return new OpenAiCompatibleClient(url(), key, "test-model", Duration.ofSeconds(5), 1);
    }

    private static String completion(String content, String finishReason) throws IOException {
        var root = JSON.createObjectNode();
        var choice = root.putArray("choices").addObject();
        choice.put("finish_reason", finishReason);
        choice.putObject("message").put("role", "assistant").put("content", content);
        root.putObject("usage").put("prompt_tokens", 100).put("completion_tokens", 20);
        return JSON.writeValueAsString(root);
    }

    @Test
    void sendsSchemaInPromptAndReadsTypedAnswer() throws Exception {
        replies.add(new Reply(200, completion(GOOD, "stop"), null));

        LlmClient.Result<VerificationResponse> result = client("my-key").structured("sys", "usr", VerificationResponse.class);

        assertEquals(Decision.USED, result.value().verdicts().get(0).decision());
        assertEquals(100, result.inputTokens());
        assertEquals(0.0, result.costUsd());
        assertEquals("Bearer my-key", lastAuth.get());
        JsonNode request = lastRequest.get();
        assertEquals("test-model", request.path("model").asText());
        assertEquals("json_object", request.path("response_format").path("type").asText());
        String system = request.path("messages").path(0).path("content").asText();
        assertTrue(system.contains("verdicts"), system);
        assertTrue(system.contains("\"UNUSED\""), system);
        assertEquals("usr", request.path("messages").path(1).path("content").asText());
    }

    @Test
    void localServersGetNoAuthHeader() throws Exception {
        replies.add(new Reply(200, completion(GOOD, "stop"), null));
        client(null).structured("s", "u", VerificationResponse.class);
        assertEquals(null, lastAuth.get());
    }

    @Test
    void toleratesFencesReasoningAndChatterAroundTheJson() throws Exception {
        for (String messy : List.of(
                "```json\n" + GOOD + "\n```",
                "<think>maybe {this} or that</think>\n" + GOOD,
                "Sure! Here you go:\n" + GOOD + "\nHope that helps.")) {
            replies.add(new Reply(200, completion(messy, "stop"), null));
            assertEquals(1, client(null).structured("s", "u", VerificationResponse.class).value().verdicts().size(), messy);
        }
    }

    @Test
    void waitsAndRetriesWhenRateLimited() throws Exception {
        replies.add(new Reply(429, "{}", "0"));
        replies.add(new Reply(503, "{}", null));
        replies.add(new Reply(200, completion(GOOD, "stop"), null));

        client(null).structured("s", "u", VerificationResponse.class);

        assertEquals(3, calls.get());
    }

    @Test
    void givesUpAfterRepeatedRateLimitsAndExplainsWhy() {
        for (int i = 0; i < 4; i++) replies.add(new Reply(429, "{\"error\":\"slow down\"}", "0"));
        LlmClient.LlmException error = assertThrows(LlmClient.LlmException.class,
                () -> client(null).structured("s", "u", VerificationResponse.class));
        assertTrue(error.getMessage().contains("free-tier limit"), error.getMessage());
        assertEquals(4, calls.get());
    }

    @Test
    void badAnswersBecomeErrorsNotVerdicts() throws Exception {
        for (Reply bad : List.of(
                new Reply(200, completion("no json here", "stop"), null),
                new Reply(200, completion("{\"verdicts\":[{\"id\":\"a\",\"decision\":\"PROBABLY\",\"reason\":\"x\"}]}", "stop"), null),
                new Reply(200, completion("{\"verdicts\":[", "length"), null),
                new Reply(401, "{\"error\":\"bad key\"}", null),
                new Reply(200, "<html>not json</html>", null))) {
            replies.add(bad);
            assertThrows(LlmClient.LlmException.class,
                    () -> client(null).structured("s", "u", VerificationResponse.class), bad.toString());
        }
    }

    @Test
    void worksWithTheRealAgentEndToEnd() throws Exception {
        String answer = "{\"verdicts\":[{\"id\":\"requirements.txt#psycopg2-binary\",\"decision\":\"USED\",\"reason\":\"Postgres driver\"}]}";
        replies.add(new Reply(200, completion(answer, "stop"), null));

        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements.txt", "psycopg2-binary\nlxml\n")
                .file("main.py", "x = 1\n")
                .audit(client(null));

        assertTrue(TestRepo.findingFor(report, "psycopg2-binary").orElseThrow().description().contains("Postgres driver"));
        assertEquals(DecidedBy.UNVERIFIED, TestRepo.findingFor(report, "lxml").orElseThrow().decidedBy());
    }

    // ---- picking a provider ------------------------------------------------------

    private static final String NOTHING_LISTENING = "http://127.0.0.1:1/v1";

    @Test
    void autoNeverPicksPaidClaudeEvenWithAKeySet() {
        LlmFactory factory = new LlmFactory(Map.of("ANTHROPIC_API_KEY", "sk-real-money")::get, NOTHING_LISTENING);
        assertTrue(factory.create("auto", null).isEmpty());
    }

    @Test
    void autoPrefersFreeCloudKeysThenLocalOllama() {
        modelsBody = "{\"data\":[{\"id\":\"nomic-embed-text\"},{\"id\":\"qwen2.5:3b\"}]}";

        assertEquals("openai/gpt-oss-120b",
                new LlmFactory(Map.of("GROQ_API_KEY", "k", "GEMINI_API_KEY", "k")::get, url()).create("auto", null).orElseThrow().model());
        assertEquals("gemini-2.5-flash",
                new LlmFactory(Map.of("GEMINI_API_KEY", "k")::get, url()).create("auto", null).orElseThrow().model());
        assertEquals("qwen2.5:3b",
                new LlmFactory(Map.<String, String>of()::get, url()).create("auto", null).orElseThrow().model(),
                "skips embedding models");
    }

    @Test
    void onlyLocalModelsAreAdvisory() {
        modelsBody = "{\"data\":[{\"id\":\"qwen2.5:3b\"}]}";
        assertTrue(new LlmFactory(Map.<String, String>of()::get, url()).create("ollama", null).orElseThrow().verdictsAreAdvisory());
        assertEquals(false, new LlmFactory(Map.of("GROQ_API_KEY", "k")::get, url()).create("groq", null).orElseThrow().verdictsAreAdvisory());
    }

    @Test
    void localModelCannotConfirmAnUnusedDependency() throws Exception {
        String answer = "{\"verdicts\":[{\"id\":\"requirements.txt#lxml\",\"decision\":\"UNUSED\",\"reason\":\"nothing parses XML\"}]}";
        replies.add(new Reply(200, completion(answer, "stop"), null));

        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements.txt", "lxml\n")
                .file("main.py", "x = 1\n")
                .audit(new OpenAiCompatibleClient(url(), null, "m", Duration.ofSeconds(5), 1, true));

        assertEquals(DecidedBy.UNVERIFIED, TestRepo.findingFor(report, "lxml").orElseThrow().decidedBy());
        assertEquals(0, report.stats().get("llmConfirmedUnused"));
    }

    @Test
    void namedProviderExplainsWhatIsMissing() {
        LlmFactory factory = new LlmFactory(Map.<String, String>of()::get, NOTHING_LISTENING);
        for (String provider : List.of("groq", "gemini", "claude", "ollama", "nonsense")) {
            assertThrows(IllegalArgumentException.class, () -> factory.create(provider, null), provider);
        }
    }
}
