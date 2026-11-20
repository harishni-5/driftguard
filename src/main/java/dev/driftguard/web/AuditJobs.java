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
        volatile long finishedAt;

        Job(GitHubRepo repo) {
            this.repo = repo;
        }
    }

    static final int MAX_QUEUE = 6;
    static final int KEPT_RESULTS = 12;
    static final long RESULT_TTL_MILLIS = 6 * 60 * 60 * 1000L;
    /** GitHub reports repo size in KB. Bigger repos take too long for a free 2-CPU machine. */
    static final long MAX_REPO_KB = 150_000;

    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "audit-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Database database;
    private final Embedder embedder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    AuditJobs(Database database, Embedder embedder) {
        this.database = database;
        this.embedder = embedder;
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
            AuditPipeline.Result result = AuditPipeline.run(job.repo.cloneUrl(), database, null, agents, embedder,
                    message -> job.progress = "Building the search index: " + message);
            job.commit = result.snapshot().commitSha();
            job.json = JsonReport.render(result);
            job.html = HtmlReport.render(result);
            job.rag = result.rag();
            job.status = Status.DONE;
            job.progress = "Done";
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
    }
}
