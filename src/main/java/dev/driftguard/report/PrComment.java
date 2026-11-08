package dev.driftguard.report;

import dev.driftguard.agents.Finding;
import dev.driftguard.agents.Finding.DecidedBy;
import dev.driftguard.report.JsonReport.Audit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Compares the audit of a pull request's base with the audit of its head and writes one
 * Markdown comment about what the PR changed: new findings, and old ones it fixed.
 *
 * Findings match by fingerprint, which ignores line numbers, so moving code around isn't
 * reported as new drift. Everything taken from the repo is escaped before it lands in a
 * public comment: no @mentions, links or HTML can be smuggled in through a file name.
 */
public final class PrComment {

    /** Lets the action find and update its own comment instead of posting a new one each push. */
    public static final String MARKER = "<!-- driftguard-pr-comment -->";
    private static final int MAX_LISTED = 25;
    private static final String PROJECT_URL = "https://github.com/harishni-5/driftguard";

    public enum FailOn { NONE, LIKELY, ANY }

    public record Diff(List<Finding> newLikely, List<Finding> newLeads, List<Finding> fixed) {
        public boolean shouldFail(FailOn failOn) {
            return switch (failOn) {
                case NONE -> false;
                case LIKELY -> !newLikely.isEmpty();
                case ANY -> !newLikely.isEmpty() || !newLeads.isEmpty();
            };
        }
    }

    private PrComment() {}

    public static Diff diff(Audit base, Audit head) {
        Set<String> before = fingerprints(base.findings());
        Set<String> after = fingerprints(head.findings());
        Comparator<Finding> order = Comparator.comparing(Finding::severity).thenComparing(Finding::title);
        List<Finding> added = head.findings().stream().filter(f -> !before.contains(f.fingerprint())).sorted(order).toList();
        return new Diff(
                added.stream().filter(f -> f.decidedBy() != DecidedBy.UNVERIFIED).toList(),
                added.stream().filter(f -> f.decidedBy() == DecidedBy.UNVERIFIED).toList(),
                base.findings().stream().filter(f -> !after.contains(f.fingerprint())).sorted(order).toList());
    }

    /**
     * @param repoUrl like https://github.com/owner/repo, for links to the head commit's lines; may be null
     */
    public static String render(Diff diff, Audit base, Audit head, String repoUrl) {
        Optional<String> blobBase = HtmlReport.blobBase(repoUrl, head.commit());
        StringBuilder out = new StringBuilder(MARKER).append("\n### DriftGuard\n\n");

        if (diff.newLikely().isEmpty() && diff.newLeads().isEmpty()) {
            out.append("No new drift in this pull request.");
        } else {
            out.append("This pull request adds ").append(count(diff.newLikely().size(), "likely issue", "likely issues"));
            if (!diff.newLeads().isEmpty()) out.append(" and ").append(count(diff.newLeads().size(), "lead to review", "leads to review"));
            out.append('.');
        }
        if (!diff.fixed().isEmpty()) out.append(" It fixes ").append(count(diff.fixed().size(), "existing finding", "existing findings")).append('.');
        out.append("\n\n");

        if (!diff.newLikely().isEmpty()) table(out, diff.newLikely(), blobBase);
        if (!diff.newLeads().isEmpty()) {
            out.append("<details><summary>").append(count(diff.newLeads().size(), "lead to review", "leads to review")).append("</summary>\n\n")
                    .append("Static analysis couldn't settle these. They may be fine.\n\n");
            table(out, diff.newLeads(), blobBase);
            out.append("</details>\n\n");
        }
        if (!diff.fixed().isEmpty()) {
            out.append("<details><summary>").append(count(diff.fixed().size(), "finding fixed", "findings fixed")).append("</summary>\n\n");
            diff.fixed().stream().limit(MAX_LISTED).forEach(f -> out.append("- ").append(md(f.title())).append('\n'));
            if (diff.fixed().size() > MAX_LISTED) out.append("- and ").append(diff.fixed().size() - MAX_LISTED).append(" more\n");
            out.append("\n</details>\n\n");
        }

        out.append("<sub>Compares this PR's head");
        if (head.commit() != null) out.append(" `").append(shortSha(head.commit())).append('`');
        out.append(" with its base");
        if (base.commit() != null) out.append(" `").append(shortSha(base.commit())).append('`');
        out.append(head.llmModel() == null ? ", static analysis only" : ", with LLM checks by " + md(head.llmModel()))
                .append(". Findings are leads, not verdicts. [About DriftGuard](").append(PROJECT_URL).append(")</sub>\n");
        return out.toString();
    }

    private static void table(StringBuilder out, List<Finding> findings, Optional<String> blobBase) {
        out.append("| Severity | Finding | Where |\n|---|---|---|\n");
        for (Finding f : findings.stream().limit(MAX_LISTED).toList()) {
            out.append("| ").append(f.severity().name().toLowerCase(Locale.ROOT)).append(" | ").append(md(f.title()))
                    .append(" | ").append(where(f, blobBase)).append(" |\n");
        }
        if (findings.size() > MAX_LISTED) out.append("\nAnd ").append(findings.size() - MAX_LISTED).append(" more in the full report.\n");
        out.append('\n');
    }

    private static String where(Finding f, Optional<String> blobBase) {
        if (f.filePath() == null) return "";
        String label = md(f.filePath() + (f.startLine() != null ? ":" + f.startLine() : ""));
        boolean linkable = blobBase.isPresent() && !f.filePath().contains("..") && !f.filePath().startsWith("/")
                && f.filePath().matches("[\\w./@+-]+");
        if (!linkable) return label;
        return "[" + label + "](" + blobBase.get() + f.filePath() + (f.startLine() != null ? "#L" + f.startLine() : "") + ")";
    }

    private static Set<String> fingerprints(List<Finding> findings) {
        Set<String> set = new HashSet<>();
        for (Finding f : findings) set.add(f.fingerprint());
        return set;
    }

    private static String count(int n, String one, String many) {
        return "**" + n + " " + (n == 1 ? one : many) + "**";
    }

    private static String shortSha(String sha) {
        return sha.substring(0, Math.min(7, sha.length()));
    }

    /**
     * Makes repo text inert in a comment: Markdown and HTML characters are escaped, and @ gets a
     * zero-width space so a crafted name can't notify real users.
     */
    static String md(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (char c : text.replace('\n', ' ').replace('\r', ' ').toCharArray()) {
            switch (c) {
                case '\\', '`', '*', '_', '[', ']', '|', '#', '!', '~' -> out.append('\\').append(c);
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '@' -> out.append("@​");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
