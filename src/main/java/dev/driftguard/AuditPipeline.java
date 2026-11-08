package dev.driftguard;

import dev.driftguard.agents.AuditAgent;
import dev.driftguard.agents.Finding;
import dev.driftguard.ingest.RepoIngestor;
import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.RepoSource;
import dev.driftguard.llm.LlmClient;
import dev.driftguard.store.Database;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs an audit from start to finish: ingest, save, run agents, save findings.
 * Agents run in a fixed order for now. A planner earns its place only once there
 * are enough agents that skipping some actually saves money.
 */
public final class AuditPipeline {

    public static final String VERSION = "0.1.0";

    public record Result(
            RepoSnapshot snapshot,
            long runId,
            List<Finding> findings,
            Map<String, Map<String, Integer>> statsByAgent,
            List<String> warnings,
            Database.RunCost cost,
            long durationMs,
            String llmModel) {}

    private AuditPipeline() {}

    public static Result run(String input, Database database, LlmClient llm, List<AuditAgent> agents)
            throws IOException, InterruptedException, SQLException {
        long start = System.nanoTime();
        try (RepoSource source = RepoSource.open(input)) {
            RepoSnapshot snapshot = RepoIngestor.ingest(source);
            long repoId = database.saveSnapshot(snapshot);

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("agents", agents.stream().map(AuditAgent::name).toList());
            config.put("llmModel", llm == null ? null : llm.model());
            long runId = database.startRun(repoId, VERSION, config);

            try {
                List<Finding> findings = new ArrayList<>();
                List<String> warnings = new ArrayList<>();
                Map<String, Map<String, Integer>> stats = new LinkedHashMap<>();
                AuditAgent.Context context = new AuditAgent.Context(snapshot, llm, trace -> database.saveTrace(runId, trace));
                for (AuditAgent agent : agents) {
                    AuditAgent.Report report = agent.run(context);
                    findings.addAll(report.findings());
                    warnings.addAll(report.warnings());
                    stats.put(agent.name(), report.stats());
                }
                findings.sort(Comparator.comparing(Finding::severity)
                        .thenComparing(Finding::confidence)
                        .thenComparing(Finding::title));
                database.saveFindings(runId, findings);
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                database.finishRun(runId, "COMPLETED", durationMs);
                return new Result(snapshot, runId, findings, stats, warnings, database.runCost(runId), durationMs, llm == null ? null : llm.model());
            } catch (SQLException | RuntimeException e) {
                database.finishRun(runId, "FAILED", (System.nanoTime() - start) / 1_000_000);
                throw e;
            }
        }
    }
}
