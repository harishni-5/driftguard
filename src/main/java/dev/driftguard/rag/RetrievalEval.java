package dev.driftguard.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.driftguard.ingest.RepoIngestor;
import dev.driftguard.ingest.RepoSource;
import dev.driftguard.store.Database;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Scores retrieval against hand-written questions: for each question, the rank of the first
 * result from a file that answers it. Runs keyword, vector and hybrid search on the same
 * index, so the comparison is only about the ranking method.
 */
public final class RetrievalEval {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Question(String q, List<String> expected) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepoQuestions(String name, String source, String commit, List<Question> questions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuestionFile(String description, List<RepoQuestions> repos) {}

    /** @param ranks 1-based rank of the first relevant result per mode, 0 when not in the top {@link #DEPTH} */
    public record Row(String repo, String question, Map<String, Integer> ranks, String topFile, List<String> rewrites) {}

    public record Score(int questions, int hitsAt1, int hitsAt5, double mrr) {
        public double hitRate5() {
            return questions == 0 ? Double.NaN : (double) hitsAt5 / questions;
        }

        public double hitRate1() {
            return questions == 0 ? Double.NaN : (double) hitsAt1 / questions;
        }
    }

    public record Outcome(List<Row> rows, Map<String, Score> scores, String embeddingModel, String rewriteModel, List<String> warnings) {}

    static final int DEPTH = 10;

    private RetrievalEval() {}

    public static QuestionFile read(Path file) throws IOException {
        return new ObjectMapper().readValue(file.toFile(), QuestionFile.class);
    }

    /** @param repoDir folder of existing clones named after each repo; others are cloned at the pinned commit */
    public static Outcome run(QuestionFile file, Path repoDir, Embedder embedder, Database cache, Consumer<String> progress)
            throws IOException, InterruptedException, SQLException {
        return run(file, repoDir, embedder, cache, null, progress);
    }

    public static final String REWRITE = "hybrid+rewrite";

    /** @param rewriter when given, also scores search after {@link QueryRewriter} (one LLM call per question) */
    public static Outcome run(QuestionFile file, Path repoDir, Embedder embedder, Database cache,
                              dev.driftguard.llm.LlmClient rewriter, Consumer<String> progress)
            throws IOException, InterruptedException, SQLException {
        List<Row> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean vectors = embedder != null;
        for (RepoQuestions repo : file.repos()) {
            RagIndex index;
            try (RepoSource source = open(repo, repoDir)) {
                progress.accept(repo.name() + ": indexing");
                index = RagIndex.build(RepoIngestor.ingest(source), embedder, cache, m -> progress.accept(repo.name() + ": " + m));
            }
            warnings.addAll(index.warnings());
            vectors &= index.hasVectors();
            int n = 0;
            for (Question question : repo.questions()) {
                Map<String, Integer> ranks = new LinkedHashMap<>();
                String top = "";
                for (RagIndex.Mode mode : RagIndex.Mode.values()) {
                    if (mode != RagIndex.Mode.KEYWORD && !index.hasVectors()) continue;
                    List<RagIndex.Hit> hits = index.search(question.q(), DEPTH, mode, chunk -> true);
                    ranks.put(label(mode), rankOfFirstRelevant(hits, question.expected()));
                    if (mode == RagIndex.Mode.HYBRID || (!index.hasVectors() && !hits.isEmpty())) {
                        top = hits.isEmpty() ? "" : hits.get(0).chunk().path();
                    }
                }
                List<String> rewrites = List.of();
                if (rewriter != null) {
                    progress.accept(repo.name() + ": rewriting question " + (++n));
                    QueryRewriter.Rewrite rewrite = QueryRewriter.rewrite(rewriter, question.q(), List.of());
                    rewrites = rewrite.queries();
                    ranks.put(REWRITE, rankOfFirstRelevant(index.search(QueryRewriter.searchTerms(rewrite), DEPTH), question.expected()));
                }
                rows.add(new Row(repo.name(), question.q(), ranks, top, rewrites));
            }
        }
        Map<String, Score> scores = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        for (RagIndex.Mode mode : RagIndex.Mode.values()) labels.add(label(mode));
        labels.add(REWRITE);
        for (String label : labels) {
            List<Integer> ranks = rows.stream().map(r -> r.ranks().get(label)).filter(r -> r != null).toList();
            if (ranks.isEmpty()) continue;
            int at1 = (int) ranks.stream().filter(r -> r == 1).count();
            int at5 = (int) ranks.stream().filter(r -> r >= 1 && r <= 5).count();
            double mrr = ranks.stream().mapToDouble(r -> r == 0 ? 0 : 1.0 / r).sum() / ranks.size();
            scores.put(label, new Score(ranks.size(), at1, at5, mrr));
        }
        return new Outcome(rows, scores, vectors ? embedder.model() : null, rewriter == null ? null : rewriter.model(), warnings);
    }

    static String label(RagIndex.Mode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    static int rankOfFirstRelevant(List<RagIndex.Hit> hits, List<String> expected) {
        for (int i = 0; i < hits.size(); i++) if (expected.contains(hits.get(i).chunk().path())) return i + 1;
        return 0;
    }

    private static RepoSource open(RepoQuestions repo, Path repoDir) throws IOException, InterruptedException {
        if (repoDir != null && Files.isDirectory(repoDir.resolve(repo.name()))) {
            RepoSource local = RepoSource.open(repoDir.resolve(repo.name()).toString());
            if (repo.commit() == null || local.commitSha().filter(repo.commit()::equals).isPresent()) return local;
            local.close();
        }
        return repo.commit() == null ? RepoSource.open(repo.source()) : RepoSource.openAt(repo.source(), repo.commit());
    }

    public static String report(Outcome outcome) {
        StringBuilder out = new StringBuilder("# Retrieval results\n\n");
        out.append("Embeddings: ").append(outcome.embeddingModel() == null ? "none (keywords only)" : outcome.embeddingModel());
        if (outcome.rewriteModel() != null) out.append(". Query rewriting: ").append(outcome.rewriteModel());
        out.append(". Rank = position of the first result from a file that answers the question (0 = not in the top ")
                .append(DEPTH).append(").\n\n");
        out.append("| Search | Questions | Found first | Found in top 5 | MRR@10 |\n|---|---:|---:|---:|---:|\n");
        outcome.scores().forEach((label, s) -> out.append(String.format(Locale.ROOT, "| %s | %d | %d (%.0f%%) | %d (%.0f%%) | %.2f |%n",
                label, s.questions(), s.hitsAt1(), 100 * s.hitRate1(), s.hitsAt5(), 100 * s.hitRate5(), s.mrr())));
        List<String> labels = List.copyOf(outcome.scores().keySet());
        out.append("\n| Repo | Question |");
        for (String label : labels) out.append(' ').append(label).append(" |");
        out.append(" Top hybrid result |");
        if (outcome.rewriteModel() != null) out.append(" Rewritten queries |");
        out.append("\n|---|---|").append("---:|".repeat(labels.size())).append("---|");
        if (outcome.rewriteModel() != null) out.append("---|");
        out.append('\n');
        for (Row row : outcome.rows()) {
            out.append("| ").append(row.repo()).append(" | ").append(row.question().replace("|", "\\|")).append(" |");
            for (String label : labels) out.append(' ').append(row.ranks().get(label)).append(" |");
            out.append(" ").append(row.topFile()).append(" |");
            if (outcome.rewriteModel() != null) out.append(" ").append(String.join("; ", row.rewrites()).replace("|", "\\|")).append(" |");
            out.append('\n');
        }
        return out.toString();
    }
}
