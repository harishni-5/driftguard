package dev.driftguard.cli;

import dev.driftguard.rag.Embedder;
import dev.driftguard.rag.EmbedderFactory;
import dev.driftguard.rag.RetrievalEval;
import dev.driftguard.store.Database;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "eval-rag", mixinStandardHelpOptions = true,
        description = "Score retrieval (keyword, vector, hybrid) against hand-written questions. Uses no LLM.")
final class RagEvalCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "QUESTIONS", description = "Question file, like eval/rag-questions.json.")
    private Path questions;

    @Option(names = "--out", description = "Where to write the results. Default: ${DEFAULT-VALUE}.")
    private Path out = Path.of("rag-results.md");

    @Option(names = "--repo-dir", description = "Folder holding already-cloned repos, named as in the question file.")
    private Path repoDir;

    @Option(names = "--embed", description = "auto, ollama, gemini or none. Default: ${DEFAULT-VALUE}.")
    private String embed = "auto";

    @Option(names = "--db", description = "SQLite file that caches embeddings. Default: ${DEFAULT-VALUE}.")
    private Path db = Path.of(System.getProperty("user.home"), ".driftguard", "driftguard.db");

    @Option(names = "--vector-index", description = "auto, exact or hnsw. Default: ${DEFAULT-VALUE}.")
    private dev.driftguard.rag.RagIndex.VectorStrategy vectorIndex = dev.driftguard.rag.RagIndex.VectorStrategy.AUTO;

    @Option(names = "--rewrite", description = "Also score query rewriting with this LLM provider: groq, gemini or ollama. Off by default.")
    private String rewrite;

    @Override
    public Integer call() {
        try (Database cache = Database.open(db)) {
            Embedder embedder = EmbedderFactory.fromSystem().create(embed).orElse(null);
            System.out.println("Embeddings: " + (embedder == null ? "none, so keyword search only" : embedder.model()));
            dev.driftguard.llm.LlmClient rewriter = rewrite == null ? null
                    : dev.driftguard.llm.LlmFactory.fromSystem().create(rewrite, null).orElse(null);
            if (rewriter != null) System.out.println("Query rewriting: " + rewriter.model());
            RetrievalEval.Outcome outcome = RetrievalEval.run(RetrievalEval.read(questions), repoDir, embedder, cache, rewriter, vectorIndex,
                    message -> System.err.print("\r" + message + "          "));
            System.err.println();
            Files.writeString(out, RetrievalEval.report(outcome));
            for (var entry : outcome.scores().entrySet()) {
                RetrievalEval.Score s = entry.getValue();
                System.out.printf(Locale.ROOT, "%-15s found first %d/%d, top 5 %d/%d (%.0f%%), MRR %.2f%n",
                        entry.getKey(), s.hitsAt1(), s.questions(), s.hitsAt5(), s.questions(),
                        100 * s.hitRate5(), s.mrr());
            }
            outcome.warnings().forEach(w -> System.err.println("warning: " + w));
            System.out.println("Results: " + out.toAbsolutePath());
            return 0;
        } catch (IOException | SQLException | IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("error: interrupted");
            return 1;
        }
    }
}
