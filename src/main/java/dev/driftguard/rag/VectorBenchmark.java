package dev.driftguard.rag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Compares the exact scan with the HNSW index: build time, query latency, and recall@k (the
 * share of the exact top k that HNSW also returns). Real embeddings come from the SQLite cache;
 * larger sizes use synthetic clustered vectors, because uniform random vectors have no structure
 * and make every ANN index look worse than it is on real data.
 */
public final class VectorBenchmark {

    public record Row(String dataset, int vectors, int dimension, int queries, int k, long exactBuildMs, long hnswBuildMs,
                      double exactP50Ms, double exactP95Ms, double hnswP50Ms, double hnswP95Ms, double recall) {
        double speedup() {
            return hnswP50Ms == 0 ? Double.NaN : exactP50Ms / hnswP50Ms;
        }
    }

    private VectorBenchmark() {}

    /** Holds out {@code queries} vectors as queries and indexes the rest. */
    public static Row run(String dataset, List<float[]> all, int queries, int k, Consumer<String> progress) {
        List<float[]> shuffled = new ArrayList<>(all);
        java.util.Collections.shuffle(shuffled, new Random(7));
        int q = Math.min(queries, shuffled.size() / 5);
        float[][] queryVectors = shuffled.subList(0, q).stream().map(VectorIndex::normalized).toArray(float[][]::new);
        float[][] data = shuffled.subList(q, shuffled.size()).stream().map(VectorIndex::normalized).toArray(float[][]::new);
        return run(dataset, data, queryVectors, k, progress);
    }

    static Row run(String dataset, float[][] data, float[][] queries, int k, Consumer<String> progress) {
        progress.accept(dataset + ": building exact index");
        long t0 = System.nanoTime();
        ExactVectorIndex exact = new ExactVectorIndex(data);
        long exactBuild = (System.nanoTime() - t0) / 1_000_000;
        progress.accept(dataset + ": building HNSW index over " + data.length + " vectors");
        HnswVectorIndex hnsw = HnswVectorIndex.build(data);

        int warmup = Math.min(20, queries.length);
        for (int i = 0; i < warmup; i++) {
            exact.nearest(queries[i], k, id -> true);
            hnsw.nearest(queries[i], k, id -> true);
        }
        double[] exactTimes = new double[queries.length];
        double[] hnswTimes = new double[queries.length];
        double recallSum = 0;
        for (int i = 0; i < queries.length; i++) {
            long a = System.nanoTime();
            List<Integer> truth = exact.nearest(queries[i], k, id -> true);
            exactTimes[i] = (System.nanoTime() - a) / 1e6;
            long b = System.nanoTime();
            List<Integer> found = hnsw.nearest(queries[i], k, id -> true);
            hnswTimes[i] = (System.nanoTime() - b) / 1e6;
            Set<Integer> expected = new HashSet<>(truth);
            long hits = found.stream().filter(expected::contains).count();
            recallSum += truth.isEmpty() ? 1 : (double) hits / truth.size();
        }
        hnsw.close();
        return new Row(dataset, data.length, data[0].length, queries.length, k, exactBuild, hnsw.buildMillis(),
                percentile(exactTimes, 50), percentile(exactTimes, 95), percentile(hnswTimes, 50), percentile(hnswTimes, 95),
                recallSum / queries.length);
    }

    /**
     * Gaussian clusters on the unit sphere: {@code n / 100} centres, each point a centre plus noise.
     * Code embeddings cluster the same way (by file, feature and language).
     */
    public static List<float[]> clustered(int n, int dimension, long seed) {
        Random random = new Random(seed);
        int clusters = Math.max(10, n / 100);
        float[][] centres = new float[clusters][];
        for (int c = 0; c < clusters; c++) centres[c] = VectorIndex.normalized(gaussian(random, dimension, 1.0));
        List<float[]> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            float[] centre = centres[random.nextInt(clusters)];
            float[] noise = gaussian(random, dimension, 0.6 / Math.sqrt(dimension));
            float[] v = new float[dimension];
            for (int d = 0; d < dimension; d++) v[d] = centre[d] + noise[d];
            out.add(v);
        }
        return out;
    }

    /** No structure at all: the known worst case for graph indexes, included so the results aren't only easy cases. */
    public static List<float[]> unclustered(int n, int dimension, long seed) {
        Random random = new Random(seed);
        List<float[]> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(gaussian(random, dimension, 1.0));
        return out;
    }

    private static float[] gaussian(Random random, int dimension, double sigma) {
        float[] v = new float[dimension];
        for (int d = 0; d < dimension; d++) v[d] = (float) (random.nextGaussian() * sigma);
        return v;
    }

    private static double percentile(double[] values, int p) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    public static String report(List<Row> rows, String machine) {
        StringBuilder out = new StringBuilder("# Vector index benchmark: exact scan vs HNSW (jvector)\n\n");
        out.append("Machine: ").append(machine).append(". HNSW settings: degree ").append(HnswVectorIndex.MAX_DEGREE)
                .append(", build beam ").append(HnswVectorIndex.BUILD_BEAM).append(", search beam ").append(HnswVectorIndex.SEARCH_BEAM)
                .append(". Latency is per query, after warm-up. Recall@k = share of the exact top k that HNSW also returned.\n\n");
        out.append("| Dataset | Vectors | Dim | Queries | Exact p50 | Exact p95 | HNSW p50 | HNSW p95 | Speed-up (p50) | Recall@k | HNSW build |\n");
        out.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Row r : rows) {
            out.append(String.format(Locale.ROOT, "| %s | %,d | %d | %d | %.2f ms | %.2f ms | %.2f ms | %.2f ms | %.1f× | %.1f%% (k=%d) | %.1f s |%n",
                    r.dataset(), r.vectors(), r.dimension(), r.queries(), r.exactP50Ms(), r.exactP95Ms(), r.hnswP50Ms(), r.hnswP95Ms(),
                    r.speedup(), 100 * r.recall(), r.k(), r.hnswBuildMs() / 1000.0));
        }
        return out.toString();
    }
}
