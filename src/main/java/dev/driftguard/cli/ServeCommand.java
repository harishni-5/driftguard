package dev.driftguard.cli;

import dev.driftguard.llm.LlmClient;
import dev.driftguard.llm.LlmFactory;
import dev.driftguard.rag.Embedder;
import dev.driftguard.rag.EmbedderFactory;
import dev.driftguard.store.Database;
import dev.driftguard.web.WebServer;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "serve", mixinStandardHelpOptions = true,
        description = "Run the DriftGuard website: audit public GitHub repos and ask questions about them.")
final class ServeCommand implements Callable<Integer> {

    @Option(names = "--port", description = "Port to listen on. Default: $PORT, else ${DEFAULT-VALUE}.")
    private int port = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 7860;

    @Option(names = "--llm", description = "LLM for answering questions: auto, groq, gemini or ollama. Default: ${DEFAULT-VALUE}.")
    private String llmProvider = "auto";

    @Option(names = "--embed", description = "Embeddings for search: auto, ollama, gemini or none. Default: ${DEFAULT-VALUE}.")
    private String embed = "auto";

    @Option(names = "--db", description = "SQLite file for snapshots and the embedding cache. Default: ${DEFAULT-VALUE}.")
    private Path db = Path.of(System.getProperty("java.io.tmpdir"), "driftguard-web.db");

    /** In auto mode every free cloud provider with a key is used, in order, so one hitting its limit falls back to the next. */
    private LlmClient chooseLlm() {
        LlmFactory factory = LlmFactory.fromSystem();
        if (!llmProvider.equalsIgnoreCase("auto")) return factory.create(llmProvider, null).orElse(null);
        java.util.List<LlmClient> chain = new java.util.ArrayList<>();
        for (String provider : java.util.List.of("groq", "gemini")) {
            try {
                factory.create(provider, null).ifPresent(chain::add);
            } catch (IllegalArgumentException missingKey) {
                // not configured; skip it
            }
        }
        if (chain.isEmpty()) return factory.create("auto", null).orElse(null);
        return chain.size() == 1 ? chain.get(0) : new dev.driftguard.llm.FallbackLlmClient(chain);
    }

    @Override
    public Integer call() throws Exception {
        if (llmProvider.equalsIgnoreCase("claude")) {
            System.err.println("error: the website only uses free LLMs");
            return 1;
        }
        LlmClient llm = chooseLlm();
        Embedder embedder = EmbedderFactory.fromSystem().create(embed).orElse(null);
        Database database = Database.open(db);
        WebServer server = WebServer.create(port, database, embedder, llm);
        server.start();
        System.out.println("DriftGuard website on http://localhost:" + port);
        System.out.println("  answers: " + (llm == null ? "none (set GROQ_API_KEY)" : llm.model()));
        System.out.println("  search:  " + (embedder == null ? "BM25 keywords only" : embedder.model() + " + BM25"));
        CountDownLatch forever = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            forever.countDown();
        }));
        forever.await();
        return 0;
    }
}
