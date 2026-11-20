package dev.driftguard.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.driftguard.llm.LlmClient;
import dev.driftguard.rag.Answerer;
import dev.driftguard.rag.Chunk;
import dev.driftguard.rag.QueryRewriter;
import dev.driftguard.rag.RagIndex;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * The website: a single page plus a small JSON API.
 * <pre>
 *   POST /api/audit         {"repo": "https://github.com/owner/name"}  -> {"id": ...}
 *   GET  /api/audit/{id}    status, progress, and the findings when done
 *   GET  /report/{id}       the full HTML report
 *   POST /api/ask           {"id": ..., "question": ...}  -> answer with cited sources
 * </pre>
 * Built on the JDK's own HTTP server, so the site adds no dependencies.
 */
public final class WebServer implements AutoCloseable {

    static final int MAX_BODY = 16_384;
    static final int MAX_QUESTION = 500;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long HOUR = 60 * 60 * 1000L;
    private static final Map<String, String> STATIC = Map.of(
            "/", "index.html", "/index.html", "index.html", "/app.js", "app.js", "/app.css", "app.css", "/favicon.svg", "favicon.svg");
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8", "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8", "svg", "image/svg+xml");

    private final HttpServer server;
    private final AuditJobs jobs;
    private final LlmClient llm;
    private final RateLimiter auditLimit;
    private final RateLimiter askLimit;

    /**
     * @param embedder for semantic search; null for keywords only
     * @param llm answers questions; null means questions get the matching code only
     */
    public static WebServer create(int port, dev.driftguard.store.Database database, dev.driftguard.rag.Embedder embedder,
                                   LlmClient llm) throws IOException {
        return new WebServer(port, new AuditJobs(database, embedder), llm);
    }

    /** @param embeddingCache a second connection to the same database, used by the background vector worker */
    public static WebServer create(int port, dev.driftguard.store.Database database, dev.driftguard.store.Database embeddingCache,
                                   dev.driftguard.rag.Embedder embedder, LlmClient llm) throws IOException {
        return new WebServer(port, new AuditJobs(database, embeddingCache, embedder), llm);
    }

    WebServer(int port, AuditJobs jobs, LlmClient llm) throws IOException {
        this.jobs = jobs;
        this.llm = llm;
        this.auditLimit = new RateLimiter(6, 60, HOUR, System::currentTimeMillis);
        this.askLimit = new RateLimiter(30, 400, HOUR, System::currentTimeMillis);
        server = HttpServer.create(new InetSocketAddress(port), 64);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/", this::handle);
    }

    /** The port actually bound, useful when started on port 0. */
    public int port() {
        return server.getAddress().getPort();
    }

    public void start() {
        server.start();
    }

    @Override
    public void close() {
        server.stop(1);
        jobs.close();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (method.equals("GET") && STATIC.containsKey(path)) {
                serveStatic(exchange, STATIC.get(path));
            } else if (method.equals("GET") && path.equals("/health")) {
                send(exchange, 200, "text/plain; charset=utf-8", "ok");
            } else if (method.equals("POST") && path.equals("/api/audit")) {
                startAudit(exchange);
            } else if (method.equals("GET") && path.startsWith("/api/audit/")) {
                auditStatus(exchange, path.substring("/api/audit/".length()));
            } else if (method.equals("GET") && path.startsWith("/report/")) {
                report(exchange, path.substring("/report/".length()));
            } else if (method.equals("POST") && path.equals("/api/ask")) {
                ask(exchange);
            } else {
                error(exchange, 404, "Not found");
            }
        } catch (RuntimeException e) {
            try {
                error(exchange, 500, "Something went wrong on the server.");
            } catch (IOException ignored) {
                // the client is gone
            }
        }
    }

    // ---- API ------------------------------------------------------------------------------

    private void startAudit(HttpExchange exchange) throws IOException {
        Optional<JsonNode> body = readJson(exchange);
        if (body.isEmpty()) return;
        Optional<GitHubRepo> repo = GitHubRepo.parse(body.get().path("repo").asText(""));
        if (repo.isEmpty()) {
            error(exchange, 400, "Paste a public GitHub repository URL, like https://github.com/owner/name");
            return;
        }
        String visitor = visitor(exchange);
        if (!auditLimit.tryAcquire(visitor)) {
            error(exchange, 429, "You've started a lot of audits. Try again in " + auditLimit.minutesUntilFree(visitor) + " minutes.");
            return;
        }
        try {
            AuditJobs.Job job = jobs.submit(repo.get());
            ObjectNode out = JSON.createObjectNode().put("id", job.id);
            sendJson(exchange, 202, out);
        } catch (AuditJobs.QueueFullException e) {
            error(exchange, 503, e.getMessage());
        }
    }

    private void auditStatus(HttpExchange exchange, String id) throws IOException {
        Optional<AuditJobs.Job> found = jobs.get(id);
        if (found.isEmpty()) {
            error(exchange, 404, "This audit has expired. Start it again.");
            return;
        }
        AuditJobs.Job job = found.get();
        ObjectNode out = JSON.createObjectNode()
                .put("id", job.id)
                .put("repo", job.repo.slug())
                .put("url", job.repo.cloneUrl())
                .put("status", job.status.name())
                .put("progress", job.progress);
        if (job.status == AuditJobs.Status.QUEUED) out.put("ahead", jobs.positionInQueue(job));
        if (job.status == AuditJobs.Status.FAILED) out.put("error", job.error);
        if (job.status == AuditJobs.Status.DONE) {
            out.put("commit", job.commit);
            out.set("audit", JSON.readTree(job.json));
            out.put("chunks", job.rag.chunks().size());
            out.put("search", job.rag.describe());
            out.put("vectors", job.vectors);
            out.put("canAnswer", llm != null);
        }
        sendJson(exchange, 200, out);
    }

    private void report(HttpExchange exchange, String id) throws IOException {
        Optional<AuditJobs.Job> job = jobs.get(id).filter(j -> j.status == AuditJobs.Status.DONE);
        if (job.isEmpty()) {
            error(exchange, 404, "Report not found");
            return;
        }
        // The report is self-contained and escapes all repo text; this header also blocks scripts outright.
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; img-src data:; frame-ancestors 'self'");
        send(exchange, 200, "text/html; charset=utf-8", job.get().html);
    }

    private void ask(HttpExchange exchange) throws IOException {
        Optional<JsonNode> body = readJson(exchange);
        if (body.isEmpty()) return;
        String question = body.get().path("question").asText("").strip();
        Optional<AuditJobs.Job> job = jobs.get(body.get().path("id").asText("")).filter(j -> j.status == AuditJobs.Status.DONE);
        if (job.isEmpty()) {
            error(exchange, 404, "This audit has expired. Run it again to ask questions.");
            return;
        }
        if (question.isEmpty() || question.length() > MAX_QUESTION) {
            error(exchange, 400, "Ask a question of up to " + MAX_QUESTION + " characters.");
            return;
        }
        String visitor = visitor(exchange);
        if (!askLimit.tryAcquire(visitor)) {
            error(exchange, 429, "That's a lot of questions. Try again in " + askLimit.minutesUntilFree(visitor) + " minutes.");
            return;
        }
        List<QueryRewriter.Turn> history = history(body.get().path("history"));
        RagIndex rag = job.get().rag;
        // With an LLM, the question is rewritten into the code's own vocabulary (and follow-ups made standalone) first.
        QueryRewriter.Rewrite rewrite = llm == null ? new QueryRewriter.Rewrite(question, List.of())
                : QueryRewriter.rewrite(llm, question, history);
        List<RagIndex.Hit> hits = rag.search(QueryRewriter.searchTerms(rewrite), Answerer.DEFAULT_K);
        ObjectNode out = JSON.createObjectNode();
        ArrayNode searched = out.putArray("searchedFor");
        QueryRewriter.searchTerms(rewrite).forEach(searched::add);
        if (hits.isEmpty()) {
            out.put("answer", "Nothing in this repository matches that question.").put("answerable", false);
            out.putArray("sources");
            sendJson(exchange, 200, out);
            return;
        }
        if (llm == null) {
            out.put("answer", "No language model is configured on this server, so here is the most relevant code.")
                    .put("answerable", false).putNull("model");
            addSources(out.putArray("sources"), job.get(), hits, List.of());
            sendJson(exchange, 200, out);
            return;
        }
        try {
            Answerer.Answer answer = Answerer.ask(llm, question, hits, history);
            out.put("answer", answer.text()).put("answerable", answer.answerable())
                    .put("generalKnowledge", answer.generalKnowledge()).put("model", llm.model());
            addSources(out.putArray("sources"), job.get(), answer.shown(), answer.cited());
            sendJson(exchange, 200, out);
        } catch (LlmClient.LlmException e) {
            String message = e.getMessage() != null && e.getMessage().contains("429")
                    ? "The free language model is at its limit right now. Try again in a minute."
                    : "The language model didn't answer. Try again.";
            error(exchange, 502, message);
        }
    }

    /** Earlier turns sent back by the page: at most the last few, each clipped. */
    static List<QueryRewriter.Turn> history(JsonNode array) {
        List<QueryRewriter.Turn> turns = new java.util.ArrayList<>();
        if (!array.isArray()) return turns;
        int start = Math.max(0, array.size() - QueryRewriter.MAX_HISTORY);
        for (int i = start; i < array.size(); i++) {
            String q = array.get(i).path("question").asText("");
            String a = array.get(i).path("answer").asText("");
            if (q.isBlank()) continue;
            turns.add(new QueryRewriter.Turn(q.length() > MAX_QUESTION ? q.substring(0, MAX_QUESTION) : q,
                    a.length() > 1500 ? a.substring(0, 1500) : a));
        }
        return turns;
    }

    private static void addSources(ArrayNode array, AuditJobs.Job job, List<RagIndex.Hit> hits, List<Integer> cited) {
        for (int i = 0; i < hits.size(); i++) {
            Chunk chunk = hits.get(i).chunk();
            ObjectNode source = array.addObject()
                    .put("n", i + 1)
                    .put("path", chunk.path())
                    .put("name", chunk.name())
                    .put("startLine", chunk.startLine())
                    .put("endLine", chunk.endLine())
                    .put("cited", cited.contains(i + 1))
                    .put("url", job.repo.cloneUrl() + "/blob/" + (job.commit == null ? "HEAD" : job.commit) + "/"
                            + chunk.path() + "#L" + chunk.startLine() + "-L" + chunk.endLine());
            List<String> lines = chunk.lines().subList(0, Math.min(40, chunk.lines().size()));
            source.put("code", String.join("\n", lines));
        }
    }

    // ---- plumbing -------------------------------------------------------------------------

    /** The visitor's address. Hosting platforms put the real one first in X-Forwarded-For. */
    static String visitor(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].strip();
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private Optional<JsonNode> readJson(HttpExchange exchange) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.startsWith("application/json")) {
            error(exchange, 415, "Send JSON");
            return Optional.empty();
        }
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (bytes.length > MAX_BODY) {
            error(exchange, 413, "Request too large");
            return Optional.empty();
        }
        try {
            JsonNode node = JSON.readTree(bytes);
            if (node == null || !node.isObject()) throw new IOException("not an object");
            return Optional.of(node);
        } catch (IOException e) {
            error(exchange, 400, "Invalid JSON");
            return Optional.empty();
        }
    }

    private void serveStatic(HttpExchange exchange, String name) throws IOException {
        try (InputStream in = WebServer.class.getResourceAsStream("/web/" + name)) {
            if (in == null) {
                error(exchange, 404, "Not found");
                return;
            }
            String type = TYPES.get(name.substring(name.lastIndexOf('.') + 1));
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; style-src 'self';"
                            + " frame-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors *");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            send(exchange, 200, type, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, JSON.createObjectNode().put("error", message));
    }

    private static void sendJson(HttpExchange exchange, int status, JsonNode body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", JSON.writeValueAsString(body));
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
    }
}
