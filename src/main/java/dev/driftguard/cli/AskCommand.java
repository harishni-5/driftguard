package dev.driftguard.cli;

import dev.driftguard.ingest.RepoIngestor;
import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.RepoSource;
import dev.driftguard.llm.LlmClient;
import dev.driftguard.llm.LlmFactory;
import dev.driftguard.rag.Answerer;
import dev.driftguard.rag.Embedder;
import dev.driftguard.rag.EmbedderFactory;
import dev.driftguard.rag.RagIndex;
import dev.driftguard.store.Database;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ask", mixinStandardHelpOptions = true,
        description = "Ask a question about a repo. Answers cite file and line; without an LLM, shows the best-matching code.")
final class AskCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "REPO", description = "Local folder or https:// git URL.")
    private String repo;

    @Parameters(index = "1", paramLabel = "QUESTION", description = "Your question, in quotes.")
    private String question;

    @Option(names = "-k", description = "How many code excerpts to retrieve. Default: ${DEFAULT-VALUE}.")
    private int k = Answerer.DEFAULT_K;

    @Option(names = "--embed", description = "auto, ollama, gemini or none (keywords only). Default: ${DEFAULT-VALUE}.")
    private String embed = "auto";

    @Option(names = "--no-llm", description = "Only search: print the matching code, send nothing to an LLM.")
    private boolean noLlm;

    @Option(names = "--llm", description = "auto, ollama, groq, gemini or claude. Default: ${DEFAULT-VALUE}.")
    private String llmProvider = "auto";

    @Option(names = "--model", description = "LLM model id. Default: picked for the provider.")
    private String model;

    @Option(names = "--db", description = "SQLite file that caches embeddings. Default: ${DEFAULT-VALUE}.")
    private Path db = Path.of(System.getProperty("user.home"), ".driftguard", "driftguard.db");

    @Override
    public Integer call() {
        if (question == null || question.isBlank()) {
            System.err.println("error: the question is empty");
            return 1;
        }
        try (Database database = Database.open(db); RepoSource source = RepoSource.open(repo)) {
            Embedder embedder = EmbedderFactory.fromSystem().create(noLlm ? localOnly(embed) : embed).orElse(null);
            RepoSnapshot snapshot = RepoIngestor.ingest(source);
            RagIndex index = RagIndex.build(snapshot, embedder, database, message -> System.err.print("\r" + message + "   "));
            if (embedder != null) System.err.println();
            List<RagIndex.Hit> hits = index.search(question, Math.max(1, k));
            System.out.println("Searched " + index.chunks().size() + " chunks with " + index.describe());
            index.warnings().forEach(w -> System.err.println("warning: " + w));
            if (hits.isEmpty()) {
                System.out.println("Nothing in the repo matches that question.");
                return 0;
            }

            LlmClient llm = noLlm ? null : LlmFactory.fromSystem().create(llmProvider, model).orElse(null);
            if (llm == null) {
                if (!noLlm) System.err.println("warning: no free LLM found, so showing the matching code only. Run `driftguard doctor` for options.");
                printHits(hits);
                return 0;
            }
            Answerer.Answer answer = Answerer.ask(llm, question, hits);
            System.out.println();
            System.out.println(answer.text());
            if (!answer.answerable()) System.out.println("\n(The retrieved code didn't fully answer this. Try rephrasing, or raise -k.)");
            System.out.println("\nSources:");
            List<Integer> listed = answer.cited().isEmpty()
                    ? java.util.stream.IntStream.rangeClosed(1, answer.shown().size()).boxed().toList() : answer.cited();
            for (int n : listed) System.out.println("  [" + n + "] " + answer.shown().get(n - 1).chunk().location());
            System.out.printf("%nModel: %s, %d input + %d output tokens%n", llm.model(), answer.inputTokens(), answer.outputTokens());
            return 0;
        } catch (IOException | SQLException | IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        } catch (LlmClient.LlmException e) {
            System.err.println("error: the LLM call failed: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("error: interrupted");
            return 1;
        }
    }

    /** --no-llm promises nothing leaves the machine, so cloud embeddings are off too. */
    private static String localOnly(String choice) {
        if (choice.equalsIgnoreCase("gemini")) {
            throw new IllegalArgumentException("--no-llm sends nothing anywhere, so it can't use Gemini embeddings. Use --embed ollama or none.");
        }
        return choice.equalsIgnoreCase("auto") ? "local" : choice;
    }

    private static void printHits(List<RagIndex.Hit> hits) {
        int n = 1;
        for (RagIndex.Hit hit : hits) {
            System.out.println();
            System.out.println("[" + n++ + "] " + hit.chunk().location());
            List<String> lines = hit.chunk().lines();
            for (int i = 0; i < Math.min(12, lines.size()); i++) {
                System.out.printf("  %5d | %s%n", hit.chunk().startLine() + i, lines.get(i));
            }
            if (lines.size() > 12) System.out.println("        | ... " + (lines.size() - 12) + " more lines");
        }
    }
}
