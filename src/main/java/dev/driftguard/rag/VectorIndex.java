package dev.driftguard.rag;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * Nearest-neighbour search over unit-length vectors, by cosine similarity. Two kinds:
 * {@link ExactVectorIndex} compares the query with every vector (perfect recall, cost grows
 * with size) and {@link HnswVectorIndex} walks a navigable graph (approximate, near-constant
 * cost). {@link RagIndex} picks one by size; the benchmark compares them.
 */
public interface VectorIndex extends AutoCloseable {

    /**
     * @param query unit-length query vector
     * @param accept ids allowed in the result (for filtered search)
     * @return ids of the closest vectors, best first, at most {@code k}
     */
    List<Integer> nearest(float[] query, int k, IntPredicate accept);

    /** Number of vectors indexed. */
    int size();

    /** "exact" or "hnsw (jvector)", for logs and reports. */
    String kind();

    @Override
    default void close() {}

    /** Unit-length copy; zero vectors stay zero. */
    static float[] normalized(float[] vector) {
        double length = 0;
        for (float v : vector) length += v * v;
        length = Math.sqrt(length);
        if (length == 0) return vector.clone();
        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) out[i] = (float) (vector[i] / length);
        return out;
    }
}
