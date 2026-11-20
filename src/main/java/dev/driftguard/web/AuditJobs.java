package dev.driftguard.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.driftguard.AuditPipeline;
import dev.driftguard.agents.AuditAgent;
import dev.driftguard.agents.deaddep.DeadDepAgent;
import dev.driftguard.agents.patterndrift.PatternDriftAgent;
import dev.driftguard.agents.specdrift.SpecDriftAgent;
import dev.driftguard.rag.Embedder;
import dev.driftguard.rag.RagIndex;
import dev.driftguard.report.HtmlReport;
import dev.driftguard.report.JsonReport;
import dev.driftguard.store.Database;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs audits one at a time, in arrival order, and keeps recent results (with their search
 * index) in memory so visitors can ask questions about them. One worker keeps memory use and
 * the shared SQLite connection simple; the queue is short so a busy server says so instead of
 * making people wait forever.
 */
final class AuditJobs implements AutoCloseable {

    enum Status { QUEUED, RUNNING, DONE, FAILED }

    /** Mutable state of one audit, read by the HTTP threads, written by the worker. */
    static final class Job {
        final String id = UUID.randomUUID().toString().substring(0, 12);
        final GitHubRepo repo;
        final long createdAt = System.currentTimeMillis();
        volatile Status status = Status.QUEUED;
        volatile String progress = "Waiting in line";
        volatile String error;
        volatile String json;
        volatile String html;
        volatile String commit;
        volatile RagIndex rag;
        /** Semantic search state: "off", "building: ...", "ready", or "unavailable: reason". */
        volatile String vectors = "off";
        volatile long finishedAt;

        Job(GitHubRepo repo) {
            this.repo = repo;
        }
    }

    static final int MAX_QUEUE = 6;
    /** Finished audits kept in memory with their search index. Set DRIFTGUARD_KEEP_RESULTS lower on small hosts. */
    static final int KEPT_RESULTS = envInt("DRIFTGUARD_KEEP_RESULTS", 12);
    static final long RESULT_TTL_MILLIS = 6 * 60 * 60 * 1000L;
    /** GitHub reports repo size in KB. Set DRIFTGUARD_MAX_REPO_MB lower on hosts with little memory or CPU. */
    static final long MAX_REPO_KB = envInt("DRIFTGUARD_MAX_REPO_MB", 150) * 1000L;

    static int envInt(String name, int fallback) {
        try {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? fallback : Math.max(1, Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final ExecutorService worker = daemon("audit-worker");
    /** Embedding runs apart from audits, so a paced cloud quota never holds up the next audit. */
    private final ExecutorService vectorWorker = daemon("vector-worker");
    private final Database database;
    private final Database embeddingCache;
    private final Embedder embedder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    AuditJobs(Database database, Embedder embedder) {
        this(database, database, embedder);
    }

    /** @param embeddingCache its own connection, since the vector worker writes while audits run */
    AuditJobs(Database database, Database embeddingCache, Embedder embedder) {
        this.database = database;
        this.embeddingCache = embeddingCache;
        this.embedder = embedder;
    }

    private static ExecutorService daemon(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    Optional<Job> get(String id) {
        synchronized (jobs) {
            return Optional.ofNullable(jobs.get(id));
        }
    }

    /** A recent finished or running audit of the same repo is reused, so popular repos cost nothing. */
    Job submit(GitHubRepo repo) throws QueueFullException {
        synchronized (jobs) {
            evict();
            for (Job job : jobs.values()) {
                if (job.repo.slug().equalsIgnoreCase(repo.slug()) && job.status != Status.FAILED) return job;
            }
            long waiting = jobs.values().stream().filter(j -> j.status == Status.QUEUED || j.status == Status.RUNNING).count();
            if (waiting >= MAX_QUEUE) throw new QueueFullException();
            Job job = new Job(repo);
            jobs.put(job.id, job);
            worker.submit(() -> run(job));
            return job;
        }
    }

    int positionInQueue(Job job) {
        synchronized (jobs) {
            int ahead = 0;
            for (Job other : jobs.values()) {
                if (other == job) break;
                if (other.status == Status.QUEUED || other.status == Status.RUNNING) ahead++;
            }
            return ahead;
        }
    }

    static final class QueueFullException extends Exception {
        QueueFullException() {
            super("The server is busy with other audits. Try again in a few minutes.");
        }
    }

    private void run(Job job) {
        job.status = Status.RUNNING;
        try {
            job.progress = "Checking the repository";
            checkSize(job.repo);
            job.progress = "Cloning and parsing";
            List<AuditAgent> agents = List.of(new DeadDepAgent(), new PatternDriftAgent(), new SpecDriftAgent());
            // Static analysis only: audits are fast and free, and the LLM quota is kept for questions.
            // Keyword search is ready with the audit; vectors follow in the background (see addVectors).
            AuditPipeline.Result result = AuditPipeline.run(job.repo.cloneUrl(), database, null, agents, null, message -> {});
            job.commit = result.snapshot().commitSha();
            job.json = JsonReport.render(result);
            job.html = HtmlReport.render(result);
            job.rag = result.rag();
            job.status = Status.DONE;
            job.progress = "Done";
            if (embedder != null) {
                job.vectors = "building: waiting";
                vectorWorker.submit(() -> addVectors(job));
            }
        } catch (Exception e) {
            job.error = friendly(e);
            job.status = Status.FAILED;
        } catch (OutOfMemoryError e) {
            job.error = "This repository is too large for the free server.";
            job.status = Status.FAILED;
        } finally {
            job.finishedAt = System.currentTimeMillis();
        }
    }

    /** Embeds the job's chunks and swaps in the hybrid index; questions use keywords until it's ready. */
    private void addVectors(Job job) {
        try {
            RagIndex upgraded = job.rag.withVectors(embedder, embeddingCache, message -> job.vectors = "building: " + message);
            if (upgraded.hasVectors()) {
                job.rag = upgraded;
                job.vectors = "ready";
            } else {
                String reason = upgraded.warnings().isEmpty() ? "no vectors" : upgraded.warnings().get(0);
                job.vectors = "unavailable: " + (reason.contains("429") || reason.contains("quota")
                        ? "the free embedding quota is used up for now" : reason.length() > 160 ? reason.substring(0, 160) + "..." : reason);
            }
        } catch (Exception | OutOfMemoryError e) {
            job.vectors = "unavailable: " + e.getClass().getSimpleName();
        }
    }

    /** Fails early on private, missing or huge repos. If GitHub's API is unavailable, the clone decides. */
    private void checkSize(GitHubRepo repo) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo.slug()))
                    .timeout(Duration.ofSeconds(10)).header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "DriftGuard").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) throw new IOException("Repository not found. Only public GitHub repositories can be audited.");
            if (response.statusCode() != 200) return;
            JsonNode info = new ObjectMapper().readTree(response.body());
            if (info.path("private").asBoolean(false)) throw new IOException("This repository is private.");
            long kb = info.path("size").asLong(0);
            if (kb > MAX_REPO_KB) {
                throw new IOException("This repository is " + kb / 1000 + " MB, above the " + MAX_REPO_KB / 1000
                        + " MB limit of the free server. Run the command-line tool on it instead.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private static String friendly(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (message.startsWith("git failed")) return "Couldn't clone the repository. Check that it exists and is public.";
        return message.length() > 300 ? message.substring(0, 300) + "..." : message;
    }

    /** Drops expired results, then the oldest finished ones beyond the limit, freeing their indexes. */
    private void evict() {
        long now = System.currentTimeMillis();
        jobs.values().removeIf(j -> j.finishedAt > 0 && now - j.finishedAt > RESULT_TTL_MILLIS);
        jobs.values().removeIf(j -> j.status == Status.FAILED && now - j.finishedAt > 10 * 60 * 1000L);
        List<Job> finished = new ArrayList<>(jobs.values().stream().filter(j -> j.status == Status.DONE).toList());
        while (finished.size() > KEPT_RESULTS) jobs.remove(finished.remove(0).id);
    }

    @Override
    public void close() {
        worker.shutdownNow();
        vectorWorker.shutdownNow();
    }
}
