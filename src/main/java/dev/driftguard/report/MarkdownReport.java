package dev.driftguard.report;

import dev.driftguard.AuditPipeline;
import dev.driftguard.agents.Finding;
import dev.driftguard.agents.Finding.DecidedBy;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Writes the audit as Markdown. Findings that still need a human go in their own section. */
public final class MarkdownReport {

    private MarkdownReport() {}

    public static String render(AuditPipeline.Result result) {
        StringBuilder out = new StringBuilder();
        out.append("# DriftGuard audit\n\n");
        out.append("- **Repo:** `").append(result.snapshot().source()).append("`\n");
        if (result.snapshot().commitSha() != null) {
            out.append("- **Commit:** `").append(result.snapshot().commitSha()).append("`\n");
        }
        out.append("- **Size:** ").append(result.snapshot().files().size()).append(" files, ")
                .append(result.snapshot().totalLoc()).append(" lines of Python/JS\n");
        out.append(String.format(Locale.ROOT, "- **Time:** %.1fs%n", result.durationMs() / 1000.0));
        out.append(llmLine(result)).append("\n\n");

        List<Finding> confident = result.findings().stream().filter(f -> f.decidedBy() != DecidedBy.UNVERIFIED).toList();
        List<Finding> needsReview = result.findings().stream().filter(f -> f.decidedBy() == DecidedBy.UNVERIFIED).toList();

        out.append("## Summary\n\n");
        out.append("Likely issues: **").append(confident.size()).append("**. Needs your review: **")
                .append(needsReview.size()).append("**.\n\n");
        for (Map.Entry<String, Map<String, Integer>> agent : result.statsByAgent().entrySet()) {
            out.append("| ").append(agent.getKey()).append(" | count |\n|---|---|\n");
            agent.getValue().forEach((key, value) -> out.append("| ").append(key).append(" | ").append(value).append(" |\n"));
            out.append('\n');
        }

        if (!result.warnings().isEmpty()) {
            out.append("## Warnings\n\n");
            result.warnings().forEach(warning -> out.append("- ").append(warning).append('\n'));
            out.append('\n');
        }

        long parseErrors = result.snapshot().parsedFiles().stream()
                .filter(f -> f.parseError() != null && !f.parseError().startsWith("recovered:"))
                .count();
        if (parseErrors > 0) {
            out.append("> ").append(parseErrors)
                    .append(" file(s) could not be parsed, so anything inside them was not seen.\n\n");
        }

        out.append("## Likely issues\n\n");
        if (confident.isEmpty()) out.append("None found.\n\n");
        confident.forEach(finding -> appendFinding(out, finding));

        if (!needsReview.isEmpty()) {
            out.append("## Needs your review\n\n");
            out.append("Static analysis could not settle these, and any LLM opinion shown may be wrong. Treat them as leads, not facts.\n\n");
            needsReview.forEach(finding -> appendFinding(out, finding));
        }
        return out.toString();
    }

    private static String llmLine(AuditPipeline.Result result) {
        if (result.llmModel() == null) return "- **LLM review:** off (static analysis only)";
        return String.format(Locale.ROOT, "- **LLM review:** `%s`, %d calls, %d input + %d output tokens, about $%.4f",
                result.llmModel(), result.cost().llmCalls(), result.cost().inputTokens(), result.cost().outputTokens(), result.cost().costUsd());
    }

    private static void appendFinding(StringBuilder out, Finding finding) {
        out.append("### ").append(finding.title()).append("\n\n");
        out.append("- **Severity:** ").append(finding.severity())
                .append(" | **Confidence:** ").append(finding.confidence())
                .append(" | **Decided by:** ").append(finding.decidedBy()).append('\n');
        if (finding.filePath() != null) {
            out.append("- **Where:** `").append(finding.filePath());
            if (finding.startLine() != null) out.append(':').append(finding.startLine());
            out.append("`\n");
        }
        out.append("- **Why:** ").append(finding.description()).append('\n');
        if (!finding.evidence().isEmpty()) {
            out.append("- **Evidence:**\n");
            finding.evidence().forEach(line -> out.append("  - `").append(line.replace("`", "'")).append("`\n"));
        }
        if (finding.fixSuggestion() != null) out.append("- **Fix:** ").append(finding.fixSuggestion()).append('\n');
        out.append('\n');
    }
}
