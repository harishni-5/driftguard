package dev.driftguard;

import dev.driftguard.agents.AuditAgent;
import dev.driftguard.agents.Finding;
import dev.driftguard.agents.deaddep.DeadDepAgent;
import dev.driftguard.ingest.RepoIngestor;
import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.RepoSource;
import dev.driftguard.llm.LlmClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Small helper for building throwaway repos inside tests. */
final class TestRepo {

    private final Path root;

    TestRepo(Path root) {
        this.root = root;
    }

    TestRepo file(String relativePath, String content) throws IOException {
        Path path = root.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return this;
    }

    Path root() {
        return root;
    }

    RepoSnapshot snapshot() throws IOException, InterruptedException {
        try (RepoSource source = RepoSource.open(root.toString())) {
            return RepoIngestor.ingest(source);
        }
    }

    AuditAgent.Report audit(LlmClient llm) throws IOException, InterruptedException, SQLException {
        return new DeadDepAgent().run(new AuditAgent.Context(snapshot(), llm, trace -> {}));
    }

    static Optional<Finding> findingFor(AuditAgent.Report report, String packageName) {
        return report.findings().stream().filter(f -> f.title().endsWith(": " + packageName)).findFirst();
    }

    static List<String> flagged(AuditAgent.Report report) {
        List<String> names = new ArrayList<>();
        for (Finding finding : report.findings()) names.add(finding.title().substring(finding.title().lastIndexOf(": ") + 2));
        return names;
    }
}
