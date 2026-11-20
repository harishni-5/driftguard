package dev.driftguard.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.driftguard.store.Database;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebServerTest {

    private WebServer server;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        server = new WebServer(0, new AuditJobs(Database.inMemory(), null), null);
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesThePageWithSecurityHeaders() throws Exception {
        HttpResponse<String> page = get("/");
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("<title>DriftGuard</title>"));
        assertTrue(page.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'self'"));
        assertEquals("nosniff", page.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals(200, get("/app.js").statusCode());
        assertEquals(200, get("/app.css").statusCode());
        assertEquals("ok", get("/health").body());
        assertEquals(404, get("/../pom.xml").statusCode());
        assertEquals(404, get("/web/index.html").statusCode());
    }

    @Test
    void refusesAnythingButPublicGitHubUrls() throws Exception {
        for (String repo : List.of("https://gitlab.com/a/b", "file:///etc/passwd", "https://github.com/a", "not a url",
                "https://github.com.evil.com/a/b", "http://localhost:8080/a/b")) {
            HttpResponse<String> response = post("/api/audit", "{\"repo\": \"" + repo + "\"}");
            assertEquals(400, response.statusCode(), repo);
            assertTrue(response.body().contains("public GitHub repository"), response.body());
        }
        assertEquals(415, http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/api/audit"))
                .POST(HttpRequest.BodyPublishers.ofString("repo=x")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(400, post("/api/audit", "[1,2]").statusCode());
        assertEquals(413, post("/api/audit", "{\"repo\": \"" + "x".repeat(20_000) + "\"}").statusCode());
    }

    @Test
    void unknownAuditsAndQuestionsAreRefusedPolitely() throws Exception {
        assertEquals(404, get("/api/audit/nope").statusCode());
        assertEquals(404, get("/report/nope").statusCode());
        HttpResponse<String> ask = post("/api/ask", "{\"id\": \"nope\", \"question\": \"hi\"}");
        assertEquals(404, ask.statusCode());
        assertTrue(ask.body().contains("expired"));
    }

    @Test
    void parsesTheUrlsPeopleActuallyPaste() {
        assertEquals("vercel/ai-chatbot", GitHubRepo.parse("https://github.com/vercel/ai-chatbot").orElseThrow().slug());
        assertEquals("vercel/ai-chatbot", GitHubRepo.parse("github.com/vercel/ai-chatbot.git").orElseThrow().slug());
        assertEquals("e2b-dev/fragments", GitHubRepo.parse(" https://www.github.com/e2b-dev/fragments/tree/main/lib ").orElseThrow().slug());
        assertEquals("a/b.c", GitHubRepo.parse("https://github.com/a/b.c").orElseThrow().slug());
        assertFalse(GitHubRepo.parse("https://github.com/a/..").isPresent());
        assertFalse(GitHubRepo.parse("https://github.com/-a/b").isPresent());
        assertEquals("https://github.com/a/b", GitHubRepo.parse("https://github.com/a/b/").orElseThrow().cloneUrl());
    }

    @Test
    void rateLimitsPerVisitorAndOverall() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(2, 3, 60_000, now::get);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"), "per-visitor limit");
        assertTrue(limiter.tryAcquire("b"));
        assertFalse(limiter.tryAcquire("c"), "overall limit");
        assertEquals(1, limiter.minutesUntilFree("a"));
        now.set(60_001);
        assertTrue(limiter.tryAcquire("a"), "the window slides");
    }

    @Test
    void historyIsClippedToTheLastFewTurns() throws Exception {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 10; i++) json.append(i == 0 ? "" : ",").append("{\"question\":\"q").append(i).append("\",\"answer\":\"a\"}");
        var turns = WebServer.history(new com.fasterxml.jackson.databind.ObjectMapper().readTree(json.append("]").toString()));
        assertEquals(4, turns.size());
        assertEquals("q9", turns.get(3).question());
        assertTrue(WebServer.history(new com.fasterxml.jackson.databind.ObjectMapper().readTree("\"x\"")).isEmpty());
    }
}
