package dev.driftguard.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.IntPredicate;

/**
 * Compares the query with every vector. At repo scale (a few thousand chunks) this takes about a
 * millisecond and is always exactly right, so it's the default below {@link RagIndex#HNSW_THRESHOLD}.
 */
public final class ExactVectorIndex implements VectorIndex {

    private final float[][] vectors; // rows may be null (chunks left unembedded)
    private final int count;

    /** @param vectors unit-length rows, indexed by id; null rows are skipped */
    public ExactVectorIndex(float[][] vectors) {
        this.vectors = vectors;
        int n = 0;
        for (float[] v : vectors) if (v != null) n++;
        this.count = n;
    }

    @Override
    public List<Integer> nearest(float[] query, int k, IntPredicate accept) {
        // A min-heap of the best k keeps this O(n log k) instead of sorting everything.
        PriorityQueue<double[]> best = new PriorityQueue<>((a, b) -> a[1] != b[1] ? Double.compare(a[1], b[1]) : Double.compare(b[0], a[0]));
        for (int i = 0; i < vectors.length; i++) {
            float[] v = vectors[i];
            if (v == null || v.length != query.length || !accept.test(i)) continue;
            double dot = 0;
            for (int d = 0; d < query.length; d++) dot += query[d] * v[d];
            if (best.size() < k) best.add(new double[] {i, dot});
            else if (dot > best.peek()[1]) {
                best.poll();
                best.add(new double[] {i, dot});
            }
        }
        List<double[]> sorted = new ArrayList<>(best);
        sorted.sort((a, b) -> a[1] != b[1] ? Double.compare(b[1], a[1]) : Double.compare(a[0], b[0]));
        return sorted.stream().map(s -> (int) s[0]).toList();
    }

    @Override
    public int size() {
        return count;
    }

    @Override
    public String kind() {
        return "exact";
    }
}
