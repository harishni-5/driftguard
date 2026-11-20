package dev.driftguard.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VectorIndexTest {

    @TempDir
    Path dir;

    private static float[][] data(int n, int dimension) {
        return VectorBenchmark.clustered(n, dimension, 42).stream().map(VectorIndex::normalized).toArray(float[][]::new);
    }

    @Test
    void exactIndexReturnsTheTrueNearestInOrder() {
        float[][] rows = {{1, 0}, {0, 1}, null, {0.8f, 0.6f}, {-1, 0}};
        ExactVectorIndex exact = new ExactVectorIndex(rows);
        assertEquals(4, exact.size(), "null rows are skipped");
        assertEquals(List.of(0, 3, 1), exact.nearest(new float[] {1, 0}, 3, id -> true));
        assertEquals(List.of(3, 1), exact.nearest(new float[] {1, 0}, 2, id -> id != 0), "filter applies");
    }

    @Test
    void hnswAgreesWithExactSearchOnClusteredData() {
        float[][] rows = data(3_000, 64);
        ExactVectorIndex exact = new ExactVectorIndex(rows);
        try (HnswVectorIndex hnsw = HnswVectorIndex.build(rows)) {
            double recall = 0;
            int queries = 50;
            for (int q = 0; q < queries; q++) {
                float[] query = rows[q * 37];
                Set<Integer> truth = new HashSet<>(exact.nearest(query, 10, id -> true));
                long hits = hnsw.nearest(query, 10, id -> true).stream().filter(truth::contains).count();
                recall += hits / 10.0;
            }
            assertTrue(recall / queries >= 0.95, "recall@10 was " + recall / queries);
        }
    }

    @Test
    void hnswRespectsFiltersAndSkipsMissingRows() {
        float[][] rows = data(1_000, 32);
        rows[5] = null;
        try (HnswVectorIndex hnsw = HnswVectorIndex.build(rows)) {
            assertEquals(999, hnsw.size());
            List<Integer> even = hnsw.nearest(rows[10], 20, id -> id % 2 == 0);
            assertEquals(20, even.size());
            assertTrue(even.stream().allMatch(id -> id % 2 == 0), even.toString());
            assertTrue(hnsw.nearest(rows[10], 5, id -> true).stream().noneMatch(id -> id == 5));
            assertEquals(10, hnsw.nearest(rows[10], 5, id -> true).get(0), "a stored vector is its own nearest neighbour");
        }
    }

    @Test
    void savedIndexLoadsBackWithIdenticalResults() throws Exception {
        float[][] rows = data(2_000, 48);
        List<List<Integer>> before = new ArrayList<>();
        try (HnswVectorIndex built = HnswVectorIndex.build(rows)) {
            for (int q = 0; q < 20; q++) before.add(built.nearest(rows[q * 50], 10, id -> true));
            built.save(dir.resolve("idx"));
        }
        try (HnswVectorIndex loaded = HnswVectorIndex.load(dir.resolve("idx")).orElseThrow()) {
            assertEquals(2_000, loaded.size());
            for (int q = 0; q < 20; q++) assertEquals(before.get(q), loaded.nearest(rows[q * 50], 10, id -> true));
        }
        assertTrue(HnswVectorIndex.load(dir.resolve("missing")).isEmpty());
        Files.writeString(dir.resolve("idx/graph.jvector"), "garbage");
        assertTrue(HnswVectorIndex.load(dir.resolve("idx")).isEmpty(), "a damaged file is rebuilt, not trusted");
    }

    @Test
    void ragIndexUsesHnswWhenAskedAndReusesTheSavedGraph() throws Exception {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            chunks.add(new Chunk(i, "src/f" + i + ".py", Chunk.Kind.CODE, "fn" + i, 1, 3,
                    List.of("def fn" + i + "():", "    return topic_" + (i % 30) + " + value_" + i, "")));
        }
        List<String> progress = new ArrayList<>();
        RagIndex first = RagIndex.build(chunks, new RagTest.WordEmbedder(), null, progress::add, RagIndex.VectorStrategy.HNSW, dir);
        assertTrue(first.describe().contains("hnsw"), first.describe());
        assertTrue(progress.stream().anyMatch(p -> p.startsWith("building the HNSW index")), progress.toString());
        assertEquals("fn7", first.search("return topic_7 value_7", 1, RagIndex.Mode.VECTOR, c -> true).get(0).chunk().name());

        progress.clear();
        RagIndex second = RagIndex.build(chunks, new RagTest.WordEmbedder(), null, progress::add, RagIndex.VectorStrategy.HNSW, dir);
        assertTrue(progress.stream().anyMatch(p -> p.startsWith("loaded the HNSW index from disk")), progress.toString());
        assertEquals("fn7", second.search("return topic_7 value_7", 1, RagIndex.Mode.VECTOR, c -> true).get(0).chunk().name());

        RagIndex small = RagIndex.build(chunks, new RagTest.WordEmbedder(), null, p -> {});
        assertTrue(small.describe().contains("exact"), "below the threshold, AUTO stays exact: " + small.describe());
    }
}
