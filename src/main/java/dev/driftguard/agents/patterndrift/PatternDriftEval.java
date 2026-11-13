package dev.driftguard.agents.patterndrift;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.driftguard.agents.patterndrift.PatternDriftAgent.Decision;
import dev.driftguard.agents.patterndrift.PatternDriftAgent.GroupReview;
import dev.driftguard.agents.patterndrift.SimilarFunctions.Group;
import dev.driftguard.ingest.RepoIngestor;
import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.RepoSource;
import dev.driftguard.llm.LlmClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Scores the similar-functions LLM check against groups a person labeled by hand.
 *
 * Labels live in a JSON file next to the repo and commit they were made on, so anyone
 * can rerun the same test with another model and compare.
 */
public final class PatternDriftEval {

    /** What a person decided. UNCLEAR groups are kept in the file but left out of the score. */
    public enum Label { SAME_JOB, INTENTIONAL_VARIANTS, DIFFERENT_JOBS, UNCLEAR }

    /** @param members "file::qualifiedName" for each function, which survives line-number changes */
    public record LabeledGroup(List<String> members, Label label, String note) {}

    /** @param source https URL or local folder ({@code ~} allowed); commit pins what was labeled */
    public record RepoEntry(String name, String source, String commit, List<LabeledGroup> groups) {}

    public record LabelFile(String description, List<RepoEntry> repos) {}

    public record Row(String repo, List<String> members, Label label, Decision decision, String purpose,
                      String note, long inputTokens, long outputTokens, String error) {
        boolean scored() {
            return label != Label.UNCLEAR && decision != null;
        }
    }

    public record Score(int labeled, int scored, int truePositive, int falsePositive, int falseNegative,
                        int trueNegative, int unsure, int exactMatches, int errors, int missing,
                        long inputTokens, long outputTokens) {

        public double precision() {
            return ratio(truePositive, truePositive + falsePositive);
        }

        public double recall() {
            return ratio(truePositive, truePositive + falseNegative);
        }

        /** Precision of word similarity alone: every labeled group counts as flagged. */
        public double baselinePrecision() {
            return ratio(truePositive + falseNegative, truePositive + falseNegative + falsePositive + trueNegative);
        }

        private static double ratio(int a, int b) {
            return b == 0 ? Double.NaN : (double) a / b;
        }
    }

    public record Outcome(List<Row> rows, Score score, List<String> warnings, String promptVersion) {}

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private PatternDriftEval() {}

    public static LabelFile read(Path file) throws IOException {
        return JSON.readValue(file.toFile(), LabelFile.class);
    }

    public static void write(Path file, LabelFile labels) throws IOException {
        JSON.writeValue(file.toFile(), labels);
    }

    /** Opens the repo at the labeled commit. A folder under {@code repoDir} named after the repo is reused if it's at that commit. */
    static RepoSource open(RepoEntry repo, Path repoDir, List<String> warnings) throws IOException, InterruptedException {
        if (repoDir != null && Files.isDirectory(repoDir.resolve(repo.name()))) {
            RepoSource local = RepoSource.open(repoDir.resolve(repo.name()).toString());
            if (local.commitSha().filter(repo.commit()::equals).isPresent()) return local;
            local.close();
        }
        if (repo.source().startsWith("https://")) return RepoSource.openAt(repo.source(), repo.commit());

        String path = repo.source().startsWith("~/")
                ? System.getProperty("user.home") + repo.source().substring(1) : repo.source();
        RepoSource local = RepoSource.open(path);
        Optional<String> head = local.commitSha();
        if (repo.commit() != null && !head.filter(repo.commit()::equals).isPresent()) {
            warnings.add(repo.name() + " is at " + head.orElse("an unknown commit") + ", not the labeled " + repo.commit()
                    + ". Groups that changed will show as missing.");
        }
        return local;
    }

    /** Finds every similar-function group, with no cap, as "file::name" member lists. */
    static List<Group> groups(RepoSource source) throws IOException, InterruptedException {
        RepoSnapshot snapshot = RepoIngestor.ingest(source);
        return SimilarFunctions.find(snapshot, new CodeAreas(snapshot)).similar();
    }

    static List<String> key(Group group) {
        return group.members().stream().map(c -> c.file() + "::" + c.name()).sorted().toList();
    }

    /** Writes every group with its code, for a person to label. Labels already made are shown next to their group. */
    public static String dump(LabelFile labels, Path repoDir, List<String> warnings) throws IOException, InterruptedException {
        StringBuilder out = new StringBuilder("# Similar-function groups to label\n\n");
        for (RepoEntry repo : labels.repos()) {
            Map<List<String>, LabeledGroup> known = byKey(repo);
            try (RepoSource source = open(repo, repoDir, warnings)) {
                List<Group> groups = groups(source);
                out.append("## ").append(repo.name()).append(" (").append(groups.size()).append(" groups)\n\n");
                int n = 1;
                for (Group group : groups) {
                    LabeledGroup label = known.get(key(group));
                    out.append("### ").append(repo.name()).append(" #").append(n++)
                            .append(String.format(Locale.ROOT, " (similarity %.2f)", group.score()))
                            .append(label == null ? "" : " — labeled " + label.label()).append("\n\n");
                    out.append("members: ").append(JSON.writer().without(SerializationFeature.INDENT_OUTPUT)
                            .writeValueAsString(key(group))).append("\n\n```\n");
                    out.append(PatternDriftAgent.renderSnippets(group)).append("```\n\n");
                }
            }
        }
        return out.toString();
    }

    /** Asks the LLM about every labeled group and scores its answers. Keeps going after a failed call. */
    public static Outcome run(LabelFile labels, Path repoDir, LlmClient llm, Consumer<String> progress)
            throws IOException, InterruptedException {
        return run(labels, repoDir, llm, PatternDriftAgent.CURRENT_PROMPT, progress);
    }

    public static Outcome run(LabelFile labels, Path repoDir, LlmClient llm, String promptVersion, Consumer<String> progress)
            throws IOException, InterruptedException {
        return run(labels, repoDir, llm, promptVersion, null, null, progress);
    }

    /**
     * @param embedder for prompt versions with retrieved context; null searches by keywords only
     * @param cache where embeddings are kept between runs; may be null
     */
    public static Outcome run(LabelFile labels, Path repoDir, LlmClient llm, String promptVersion,
                              dev.driftguard.rag.Embedder embedder, dev.driftguard.store.Database cache, Consumer<String> progress)
            throws IOException, InterruptedException {
        if (!PatternDriftAgent.PROMPTS.containsKey(promptVersion)) {
            throw new IllegalArgumentException("Unknown prompt version " + promptVersion + ". Known: " + PatternDriftAgent.PROMPTS.keySet());
        }
        List<String> warnings = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        int missing = 0;
        for (RepoEntry repo : labels.repos()) {
            if (repo.groups() == null || repo.groups().isEmpty()) continue;
            Map<List<String>, Group> found = new LinkedHashMap<>();
            dev.driftguard.rag.RagIndex rag = null;
            try (RepoSource source = open(repo, repoDir, warnings)) {
                RepoSnapshot snapshot = RepoIngestor.ingest(source);
                for (Group group : SimilarFunctions.find(snapshot, new CodeAreas(snapshot)).similar()) found.put(key(group), group);
                if (PatternDriftAgent.usesContext(promptVersion)) {
                    rag = dev.driftguard.rag.RagIndex.build(snapshot, embedder, cache, progress);
                    warnings.addAll(rag.warnings());
                }
            } catch (java.sql.SQLException e) {
                throw new IOException("Could not use the embedding cache: " + e.getMessage(), e);
            } catch (IOException e) {
                warnings.add("Skipped " + repo.name() + ": " + e.getMessage());
                missing += repo.groups().size();
                continue;
            }
            for (LabeledGroup labeled : repo.groups()) {
                List<String> members = labeled.members().stream().sorted().toList();
                Group group = found.get(members);
                if (group == null) {
                    warnings.add(repo.name() + ": labeled group no longer found: " + members);
                    missing++;
                    continue;
                }
                progress.accept(repo.name() + " " + (rows.size() + 1));
                try {
                    LlmClient.Result<GroupReview> result = PatternDriftAgent.ask(llm, group, promptVersion, rag);
                    GroupReview review = result.value();
                    Decision decision = review.decision() == null ? Decision.UNSURE : review.decision();
                    rows.add(new Row(repo.name(), members, labeled.label(), decision, review.purpose(), labeled.note(),
                            result.inputTokens(), result.outputTokens(), null));
                } catch (LlmClient.LlmException e) {
                    rows.add(new Row(repo.name(), members, labeled.label(), null, null, labeled.note(), 0, 0, e.getMessage()));
                }
            }
        }
        return new Outcome(rows, score(rows, missing), warnings, promptVersion);
    }

    static Score score(List<Row> rows, int missing) {
        int labeled = 0, scored = 0, tp = 0, fp = 0, fn = 0, tn = 0, unsure = 0, exact = 0, errors = 0;
        long in = 0, out = 0;
        for (Row row : rows) {
            in += row.inputTokens();
            out += row.outputTokens();
            if (row.error() != null) errors++;
            if (row.label() == Label.UNCLEAR) continue;
            labeled++;
            if (!row.scored()) continue;
            scored++;
            boolean actual = row.label() == Label.SAME_JOB;
            boolean predicted = row.decision() == Decision.SAME_JOB;
            if (row.decision() == Decision.UNSURE) unsure++;
            if (row.decision().name().equals(row.label().name())) exact++;
            if (predicted && actual) tp++;
            else if (predicted) fp++;
            else if (actual) fn++;
            else tn++;
        }
        return new Score(labeled, scored, tp, fp, fn, tn, unsure, exact, errors, missing, in, out);
    }

    public static String report(Outcome outcome, String model) {
        Score s = outcome.score();
        StringBuilder out = new StringBuilder("# Pattern-Drift eval: `" + model + "`, prompt " + outcome.promptVersion() + "\n\n");
        out.append("A group counts as drift when it was labeled SAME_JOB. The model \"flags\" a group when it answers SAME_JOB, ")
                .append("which is what turns a lead into a likely issue in the real report. UNSURE counts as not flagged.\n\n");
        out.append("| Measure | Value |\n|---|---|\n");
        out.append("| Groups scored | ").append(s.scored()).append(" of ").append(s.labeled()).append(" labeled |\n");
        out.append("| Precision (flagged groups that really are drift) | ").append(pct(s.precision())).append(" |\n");
        out.append("| Recall (drift groups the model flagged) | ").append(pct(s.recall())).append(" |\n");
        out.append("| Word similarity alone, no LLM (precision) | ").append(pct(s.baselinePrecision())).append(" |\n");
        out.append("| Exact 4-way agreement | ").append(s.exactMatches()).append(" of ").append(s.scored()).append(" |\n");
        out.append("| Answered UNSURE | ").append(s.unsure()).append(" |\n");
        out.append("| Failed calls / missing groups | ").append(s.errors()).append(" / ").append(s.missing()).append(" |\n");
        out.append("| Tokens | ").append(s.inputTokens()).append(" in, ").append(s.outputTokens()).append(" out |\n\n");
        out.append("|  | Model said SAME_JOB | Model said something else |\n|---|---:|---:|\n");
        out.append("| **Labeled SAME_JOB** | ").append(s.truePositive()).append(" | ").append(s.falseNegative()).append(" |\n");
        out.append("| **Labeled not drift** | ").append(s.falsePositive()).append(" | ").append(s.trueNegative()).append(" |\n\n");
        out.append("## Every group\n\n| Repo | Functions | Label | Model | Match | Model's purpose | Labeler's note |\n|---|---|---|---|---|---|---|\n");
        for (Row row : outcome.rows()) {
            String names = String.join("<br>", row.members().stream().map(m -> m.replace("::", " `") + "`").toList());
            String match = !row.scored() ? "-" : (row.decision() == Decision.SAME_JOB) == (row.label() == Label.SAME_JOB) ? "yes" : "**no**";
            out.append("| ").append(row.repo()).append(" | ").append(names).append(" | ").append(row.label())
                    .append(" | ").append(row.decision() == null ? "error: " + cell(row.error()) : row.decision())
                    .append(" | ").append(match).append(" | ").append(cell(row.purpose())).append(" | ").append(cell(row.note())).append(" |\n");
        }
        if (!outcome.warnings().isEmpty()) {
            out.append("\n## Warnings\n\n");
            outcome.warnings().forEach(w -> out.append("- ").append(w).append('\n'));
        }
        return out.toString();
    }

    private static String pct(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }

    private static Map<List<String>, LabeledGroup> byKey(RepoEntry repo) {
        Map<List<String>, LabeledGroup> map = new LinkedHashMap<>();
        if (repo.groups() != null) repo.groups().forEach(g -> map.put(g.members().stream().sorted().toList(), g));
        return map;
    }
}
