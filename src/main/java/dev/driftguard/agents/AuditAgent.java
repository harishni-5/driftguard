package dev.driftguard.agents;

import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.llm.LlmClient;
import dev.driftguard.store.Database;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** One audit specialist. Every new agent (Pattern-Drift, Orphan-Code and so on) implements this. */
public interface AuditAgent {

    String name();

    Report run(Context context) throws SQLException;

    /** @param llm null when running without an LLM (--no-llm, or no API key) */
    record Context(RepoSnapshot snapshot, LlmClient llm, TraceSink traces) {}

    /** @param stats numbers for the report and the paper's tables */
    record Report(List<Finding> findings, Map<String, Integer> stats, List<String> warnings) {}

    @FunctionalInterface
    interface TraceSink {
        void record(Database.Trace trace) throws SQLException;
    }
}
