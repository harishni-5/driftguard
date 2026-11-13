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
import java.util.EnumMap;
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
    public record Row(String repo, String question, Map<RagIndex.Mode, Integer> ranks, String topFile) {}

    public record Score(int questions, int hitsAt1, int hitsAt5, double mrr) {
        public double hitRate5() {
            return questions == 0 ? Double.NaN : (double) hitsAt5 / questions;
        }

        public double hitRate1() {
            return questions == 0 ? Double.NaN : (double) hitsAt1 / questions;
        }
    }

    public record Outcome(List<Row> rows, Map<RagIndex.Mode, Score> scores, String embeddingModel, List<String> warnings) {}

    static final int DEPTH = 10;

    private RetrievalEval() {}

    public static QuestionFile read(Path file) throws IOException {
        return new ObjectMapper().readValue(file.toFile(), QuestionFile.class);
    }

    /** @param repoDir folder of existing clones named after each repo; others are cloned at the pinned commit */
    public static Outcome run(QuestionFile file, Path repoDir, Embedder embedder, Database cache, Consumer<String> progress)
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
            for (Question question : repo.questions()) {
                Map<RagIndex.Mode, Integer> ranks = new EnumMap<>(RagIndex.Mode.class);
                String top = "";
                for (RagIndex.Mode mode : RagIndex.Mode.values()) {
                    if (mode != RagIndex.Mode.KEYWORD && !index.hasVectors()) continue;
                    List<RagIndex.Hit> hits = index.search(question.q(), DEPTH, mode, chunk -> true);
                    ranks.put(mode, rankOfFirstRelevant(hits, question.expected()));
                    if (mode == RagIndex.Mode.HYBRID || (!index.hasVectors() && !hits.isEmpty())) {
                        top = hits.isEmpty() ? "" : hits.get(0).chunk().path();
                    }
                }
                rows.add(new Row(repo.name(), question.q(), ranks, top));
            }
        }
        Map<RagIndex.Mode, Score> scores = new EnumMap<>(RagIndex.Mode.class);
        for (RagIndex.Mode mode : RagIndex.Mode.values()) {
            List<Integer> ranks = rows.stream().map(r -> r.ranks().get(mode)).filter(r -> r != null).toList();
            if (ranks.isEmpty()) continue;
            int at1 = (int) ranks.stream().filter(r -> r == 1).count();
            int at5 = (int) ranks.stream().filter(r -> r >= 1 && r <= 5).count();
            double mrr = ranks.stream().mapToDouble(r -> r == 0 ? 0 : 1.0 / r).sum() / ranks.size();
            scores.put(mode, new Score(ranks.size(), at1, at5, mrr));
        }
        return new Outcome(rows, scores, vectors ? embedder.model() : null, warnings);
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
        out.append("Embeddings: ").append(outcome.embeddingModel() == null ? "none (keywords only)" : outcome.embeddingModel())
                .append(". Rank = position of the first result from a file that answers the question (0 = not in the top ")
                .append(DEPTH).append(").\n\n");
        out.append("| Search | Questions | Found first | Found in top 5 | MRR@10 |\n|---|---:|---:|---:|---:|\n");
        outcome.scores().forEach((mode, s) -> out.append(String.format(Locale.ROOT, "| %s | %d | %d (%.0f%%) | %d (%.0f%%) | %.2f |%n",
                mode.name().toLowerCase(Locale.ROOT), s.questions(), s.hitsAt1(), 100 * s.hitRate1(), s.hitsAt5(), 100 * s.hitRate5(), s.mrr())));
        out.append("\n| Repo | Question |");
        List<RagIndex.Mode> modes = List.copyOf(outcome.scores().keySet());
        for (RagIndex.Mode mode : modes) out.append(' ').append(mode.name().toLowerCase(Locale.ROOT)).append(" |");
        out.append(" Top hybrid result |\n|---|---|").append("---:|".repeat(modes.size())).append("---|\n");
        for (Row row : outcome.rows()) {
            out.append("| ").append(row.repo()).append(" | ").append(row.question().replace("|", "\\|")).append(" |");
            for (RagIndex.Mode mode : modes) out.append(' ').append(row.ranks().get(mode)).append(" |");
            out.append(" ").append(row.topFile()).append(" |\n");
        }
        return out.toString();
    }
}
