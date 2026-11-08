package dev.driftguard.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.driftguard.agents.Finding;
import dev.driftguard.ingest.ParsedFile;
import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.SourceFile;
import dev.driftguard.manifest.Dependency;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All SQLite access lives here, so the rest of the code never sees SQL. */
public final class Database implements AutoCloseable {

    private static final int SCHEMA_VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Connection connection;

    private Database(Connection connection) {
        this.connection = connection;
    }

    public static Database open(Path file) throws SQLException, IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        return connect("jdbc:sqlite:" + file.toAbsolutePath());
    }

    public static Database inMemory() throws SQLException, IOException {
        return connect("jdbc:sqlite::memory:");
    }

    private static Database connect(String url) throws SQLException, IOException {
        Connection connection = DriverManager.getConnection(url);
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
            }
            Database database = new Database(connection);
            database.migrate();
            return database;
        } catch (SQLException | IOException | RuntimeException e) {
            // Close on failure too: Windows keeps an open database file locked, so nothing could delete or reuse it.
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private void migrate() throws SQLException, IOException {
        int version;
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            version = result.getInt(1);
        }
        if (version == SCHEMA_VERSION) return;
        if (version != 0) {
            throw new SQLException("Database has schema version " + version + " but this build expects "
                    + SCHEMA_VERSION + ". Point --db at a new file.");
        }
        String schema;
        try (InputStream in = Database.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) throw new IOException("Missing bundled db/schema.sql");
            schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        inTransaction(() -> {
            try (Statement statement = connection.createStatement()) {
                // Comments go first so the split on ; never sees one inside a comment.
                for (String sql : schema.replaceAll("(?m)--.*$", "").split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
                statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            }
        });
    }

    /** Saves a snapshot and returns its id. An identical snapshot is reused, not duplicated. */
    public long saveSnapshot(RepoSnapshot snapshot) throws SQLException {
        try (PreparedStatement find = connection.prepareStatement(
                "SELECT id FROM repos WHERE source = ? AND content_hash = ?")) {
            find.setString(1, snapshot.source());
            find.setString(2, snapshot.contentHash());
            try (ResultSet result = find.executeQuery()) {
                if (result.next()) return result.getLong(1);
            }
        }
        long[] repoId = new long[1];
        inTransaction(() -> {
            repoId[0] = insertRepo(snapshot);
            Map<String, Long> fileIds = insertFiles(repoId[0], snapshot);
            insertParsedFacts(snapshot, fileIds);
            insertDependencies(repoId[0], snapshot.dependencies());
        });
        return repoId[0];
    }

    private long insertRepo(RepoSnapshot snapshot) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO repos (source, commit_sha, content_hash, primary_language, file_count, loc, ingested_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, snapshot.source());
            insert.setString(2, snapshot.commitSha());
            insert.setString(3, snapshot.contentHash());
            insert.setString(4, snapshot.primaryLanguage());
            insert.setInt(5, snapshot.files().size());
            insert.setInt(6, snapshot.totalLoc());
            insert.setString(7, Instant.now().toString());
            insert.executeUpdate();
            return generatedKey(insert);
        }
    }

    private Map<String, Long> insertFiles(long repoId, RepoSnapshot snapshot) throws SQLException {
        Map<String, String> parseErrors = new HashMap<>();
        for (ParsedFile parsed : snapshot.parsedFiles()) {
            if (parsed.parseError() != null) parseErrors.put(parsed.path(), parsed.parseError());
        }
        Map<String, Long> ids = new HashMap<>();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO files (repo_id, path, kind, sha256, size_bytes, loc, parse_error) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            for (SourceFile file : snapshot.files()) {
                insert.setLong(1, repoId);
                insert.setString(2, file.path());
                insert.setString(3, file.kind().name());
                insert.setString(4, file.sha256());
                insert.setLong(5, file.sizeBytes());
                insert.setInt(6, file.loc());
                insert.setString(7, parseErrors.get(file.path()));
                insert.executeUpdate();
                ids.put(file.path(), generatedKey(insert));
            }
        }
        return ids;
    }

    private void insertParsedFacts(RepoSnapshot snapshot, Map<String, Long> fileIds) throws SQLException {
        try (PreparedStatement symbol = connection.prepareStatement(
                        "INSERT INTO symbols (file_id, name, qualified_name, kind, start_line, end_line, is_exported)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?)");
                PreparedStatement importRow = connection.prepareStatement(
                        "INSERT INTO imports (file_id, module, kind, line, names_json, is_external) VALUES (?, ?, ?, ?, ?, ?)");
                PreparedStatement call = connection.prepareStatement(
                        "INSERT INTO call_edges (file_id, caller, callee, line) VALUES (?, ?, ?, ?)")) {
            for (ParsedFile parsed : snapshot.parsedFiles()) {
                long fileId = fileIds.get(parsed.path());
                for (ParsedFile.SymbolDef s : parsed.symbols()) {
                    symbol.setLong(1, fileId);
                    symbol.setString(2, s.name());
                    symbol.setString(3, s.qualifiedName());
                    symbol.setString(4, s.kind());
                    symbol.setInt(5, s.startLine());
                    symbol.setInt(6, s.endLine());
                    symbol.setInt(7, s.exported() ? 1 : 0);
                    symbol.addBatch();
                }
                for (ParsedFile.ImportRef i : parsed.imports()) {
                    importRow.setLong(1, fileId);
                    importRow.setString(2, i.module());
                    importRow.setString(3, i.kind());
                    importRow.setInt(4, i.line());
                    importRow.setString(5, toJson(i.names()));
                    importRow.setInt(6, i.external() ? 1 : 0);
                    importRow.addBatch();
                }
                for (ParsedFile.CallRef c : parsed.calls()) {
                    call.setLong(1, fileId);
                    call.setString(2, c.caller());
                    call.setString(3, c.callee());
                    call.setInt(4, c.line());
                    call.addBatch();
                }
            }
            symbol.executeBatch();
            importRow.executeBatch();
            call.executeBatch();
        }
    }

    private void insertDependencies(long repoId, List<Dependency> dependencies) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO dependencies (repo_id, ecosystem, manifest_path, name, version_spec, scope, line)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (Dependency d : dependencies) {
                insert.setLong(1, repoId);
                insert.setString(2, d.ecosystem().name().toLowerCase());
                insert.setString(3, d.manifestPath());
                insert.setString(4, d.name());
                insert.setString(5, d.versionSpec());
                insert.setString(6, d.scope().name());
                insert.setInt(7, d.line());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public long startRun(long repoId, String toolVersion, Map<String, Object> config) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO audit_runs (repo_id, tool_version, config_json, status, started_at) VALUES (?, ?, ?, 'RUNNING', ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, repoId);
            insert.setString(2, toolVersion);
            insert.setString(3, toJson(config));
            insert.setString(4, Instant.now().toString());
            insert.executeUpdate();
            return generatedKey(insert);
        }
    }

    public void finishRun(long runId, String status, long durationMs) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE audit_runs SET status = ?, finished_at = ?, duration_ms = ? WHERE id = ?")) {
            update.setString(1, status);
            update.setString(2, Instant.now().toString());
            update.setLong(3, durationMs);
            update.setLong(4, runId);
            update.executeUpdate();
        }
    }

    public void saveFindings(long runId, List<Finding> findings) throws SQLException {
        inTransaction(() -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO findings (run_id, agent, category, severity, confidence, decided_by, title, description,"
                            + " file_path, start_line, end_line, evidence_json, fix_suggestion, fingerprint)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Finding f : findings) {
                    insert.setLong(1, runId);
                    insert.setString(2, f.agent());
                    insert.setString(3, f.category());
                    insert.setString(4, f.severity().name());
                    insert.setString(5, f.confidence().name());
                    insert.setString(6, f.decidedBy().name());
                    insert.setString(7, f.title());
                    insert.setString(8, f.description());
                    insert.setString(9, f.filePath());
                    if (f.startLine() == null) insert.setNull(10, Types.INTEGER);
                    else insert.setInt(10, f.startLine());
                    insert.setNull(11, Types.INTEGER);
                    insert.setString(12, toJson(f.evidence()));
                    insert.setString(13, f.fixSuggestion());
                    insert.setString(14, f.fingerprint());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        });
    }

    /** One step of an agent's work. Null token and cost fields mean no LLM call was involved. */
    public record Trace(
            String agent, int step, String event, String model, String input, String output,
            Long inputTokens, Long outputTokens, Double costUsd, Long latencyMs) {}

    public void saveTrace(long runId, Trace trace) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO agent_traces (run_id, agent, step, event, model, input_text, output_text,"
                        + " input_tokens, output_tokens, cost_usd, latency_ms, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            insert.setLong(1, runId);
            insert.setString(2, trace.agent());
            insert.setInt(3, trace.step());
            insert.setString(4, trace.event());
            insert.setString(5, trace.model());
            insert.setString(6, trace.input());
            insert.setString(7, trace.output());
            insert.setObject(8, trace.inputTokens());
            insert.setObject(9, trace.outputTokens());
            insert.setObject(10, trace.costUsd());
            insert.setObject(11, trace.latencyMs());
            insert.setString(12, Instant.now().toString());
            insert.executeUpdate();
        }
    }

    public record RunCost(long inputTokens, long outputTokens, double costUsd, int llmCalls) {}

    public RunCost runCost(long runId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COALESCE(SUM(input_tokens), 0), COALESCE(SUM(output_tokens), 0), COALESCE(SUM(cost_usd), 0),"
                        + " COUNT(input_tokens) FROM agent_traces WHERE run_id = ?")) {
            query.setLong(1, runId);
            try (ResultSet result = query.executeQuery()) {
                return new RunCost(result.getLong(1), result.getLong(2), result.getDouble(3), result.getInt(4));
            }
        }
    }

    /** Row count for one table. The table name must come from our code, never from input. */
    public long count(String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return result.getLong(1);
        }
    }

    private interface SqlWork {
        void run() throws SQLException;
    }

    private void inTransaction(SqlWork work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            work.run();
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static long generatedKey(Statement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw new SQLException("No generated key returned");
            return keys.getLong(1);
        }
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
