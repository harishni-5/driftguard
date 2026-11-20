package dev.driftguard.cli;

import dev.driftguard.rag.VectorBenchmark;
import dev.driftguard.store.Database;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "bench-vectors", mixinStandardHelpOptions = true,
        description = "Benchmark the exact vector scan against the HNSW index: latency and recall.")
final class BenchVectorsCommand implements Callable<Integer> {

    @Option(names = "--sizes", split = ",", description = "Synthetic dataset sizes. Default: ${DEFAULT-VALUE}.")
    private List<Integer> sizes = List.of(10_000, 50_000, 100_000);

    @Option(names = "--dim", description = "Synthetic vector dimension (nomic-embed-text and Gemini here use 768). Default: ${DEFAULT-VALUE}.")
    private int dimension = 768;

    @Option(names = "--queries", description = "Queries per dataset. Default: ${DEFAULT-VALUE}.")
    private int queries = 200;

    @Option(names = "-k", description = "Neighbours per query. Default: ${DEFAULT-VALUE}.")
    private int k = 10;

    @Option(names = "--hard", description = "Also run a worst case: unclustered random vectors of this size (0 = skip). Default: ${DEFAULT-VALUE}.")
    private int hardSize = 50_000;

    @Option(names = "--real-model", description = "Also benchmark real cached embeddings of this model. Default: ${DEFAULT-VALUE}.")
    private String realModel = "nomic-embed-text:latest";

    @Option(names = "--db", description = "SQLite file with cached embeddings. Default: ${DEFAULT-VALUE}.")
    private Path db = Path.of(System.getProperty("user.home"), ".driftguard", "driftguard.db");

    @Option(names = "--out", description = "Where to write the results. Default: ${DEFAULT-VALUE}.")
    private Path out = Path.of("vector-benchmark.md");

    @Override
    public Integer call() throws Exception {
        List<VectorBenchmark.Row> rows = new ArrayList<>();
        if (Files.exists(db)) {
            try (Database database = Database.open(db)) {
                List<float[]> real = database.allEmbeddings(realModel, 200_000);
                if (real.size() >= 500) rows.add(print(VectorBenchmark.run("real code embeddings (" + realModel + ")", real, queries, k, this::progress)));
                else System.err.println("Only " + real.size() + " cached " + realModel + " vectors; skipping the real dataset.");
            }
        }
        for (int size : sizes) {
            List<float[]> data = VectorBenchmark.clustered(size + queries, dimension, size);
            rows.add(print(VectorBenchmark.run("synthetic clustered", data, queries, k, this::progress)));
        }
        if (hardSize > 0) {
            List<float[]> data = VectorBenchmark.unclustered(hardSize + queries, dimension, 99);
            rows.add(print(VectorBenchmark.run("worst case: random, no clusters", data, queries, k, this::progress)));
        }
        String machine = Runtime.getRuntime().availableProcessors() + " cores, Java " + Runtime.version().feature()
                + ", " + System.getProperty("os.name") + " " + System.getProperty("os.arch");
        Files.writeString(out, VectorBenchmark.report(rows, machine));
        System.out.println("Results: " + out.toAbsolutePath());
        return 0;
    }

    private VectorBenchmark.Row print(VectorBenchmark.Row r) {
        System.err.println();
        System.out.printf(java.util.Locale.ROOT, "%-45s %,8d vectors  exact p50 %6.2f ms  hnsw p50 %6.2f ms  recall@%d %5.1f%%  build %.1fs%n",
                r.dataset(), r.vectors(), r.exactP50Ms(), r.hnswP50Ms(), r.k(), 100 * r.recall(), r.hnswBuildMs() / 1000.0);
        return r;
    }

    private void progress(String message) {
        System.err.print("\r" + message + "          ");
    }
}
