package dev.driftguard.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.driftguard.AuditPipeline;
import dev.driftguard.agents.Finding;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Machine-readable audit, for comparing two commits (see {@link PrComment}) or feeding other tools. */
public final class JsonReport {

    /** Bump when a field changes meaning, so readers can refuse files they don't understand. */
    public static final int SCHEMA = 1;

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Audit(
            int schema,
            String version,
            String source,
            String commit,
            String llmModel,
            List<Finding> findings,
            Map<String, Map<String, Integer>> stats,
            List<String> warnings) {}

    private JsonReport() {}

    public static String render(AuditPipeline.Result result) throws IOException {
        return JSON.writeValueAsString(new Audit(SCHEMA, AuditPipeline.VERSION, result.snapshot().source(),
                result.snapshot().commitSha(), result.llmModel(), result.findings(), result.statsByAgent(), result.warnings()));
    }

    public static Audit read(Path file) throws IOException {
        Audit audit = JSON.readValue(file.toFile(), Audit.class);
        if (audit.schema() != SCHEMA) {
            throw new IOException(file + " uses report schema " + audit.schema() + ", but this DriftGuard reads schema " + SCHEMA + ".");
        }
        return audit;
    }
}
