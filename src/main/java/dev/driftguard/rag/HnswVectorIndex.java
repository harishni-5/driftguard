package dev.driftguard.rag;

import io.github.jbellis.jvector.disk.ReaderSupplier;
import io.github.jbellis.jvector.disk.SimpleMappedReader;
import io.github.jbellis.jvector.graph.GraphIndex;
import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.graph.similarity.SearchScoreProvider;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Approximate nearest-neighbour search with an HNSW-style graph from jvector (the vector index
 * inside Apache Cassandra). A query walks the graph from an entry point towards closer and closer
 * neighbours, so it scores a few hundred vectors instead of all of them. The graph can be written
 * to disk and memory-mapped back, so a large repo is indexed once.
 *
 * Vectors are unit length, so dot product ranks exactly like cosine similarity.
 */
public final class HnswVectorIndex implements VectorIndex {

    /** Graph degree: neighbours kept per node. 16 is jvector's and the HNSW paper's usual value. */
    static final int MAX_DEGREE = 16;
    /** Candidates considered while building. Higher builds slower and finds better neighbours. */
    static final int BUILD_BEAM = 100;
    /** Candidates kept while searching, before the best k are returned. Trades speed for recall. */
    static final int SEARCH_BEAM = 100;

    private static final VectorTypeSupport VECTORS = VectorizationProvider.getInstance().getVectorTypeSupport();
    private static final VectorSimilarityFunction SIMILARITY = VectorSimilarityFunction.DOT_PRODUCT;
    private static final String GRAPH_FILE = "graph.jvector";
    private static final String IDS_FILE = "ids.bin";

    private final GraphIndex graph;
    private final RandomAccessVectorValues values; // on-heap list, or null when the graph is on disk
    private final int[] nodeToId;
    private final ReaderSupplier reader;
    private final long buildMillis;

    private HnswVectorIndex(GraphIndex graph, RandomAccessVectorValues values, int[] nodeToId, ReaderSupplier reader, long buildMillis) {
        this.graph = graph;
        this.values = values;
        this.nodeToId = nodeToId;
        this.reader = reader;
        this.buildMillis = buildMillis;
    }

    /** @param rows unit-length vectors indexed by id; null rows are left out */
    public static HnswVectorIndex build(float[][] rows) {
        long start = System.nanoTime();
        List<VectorFloat<?>> list = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        int dimension = -1;
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] == null) continue;
            if (dimension < 0) dimension = rows[i].length;
            if (rows[i].length != dimension) continue;
            list.add(VECTORS.createFloatVector(rows[i]));
            ids.add(i);
        }
        if (list.isEmpty()) throw new IllegalArgumentException("No vectors to index");
        RandomAccessVectorValues values = new ListRandomAccessVectorValues(list, dimension);
        BuildScoreProvider scores = BuildScoreProvider.randomAccessScoreProvider(values, SIMILARITY);
        GraphIndex graph;
        try (GraphIndexBuilder builder = new GraphIndexBuilder(scores, dimension, MAX_DEGREE, BUILD_BEAM, 1.2f, 1.2f)) {
            graph = builder.build(values);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new HnswVectorIndex(graph, values, ids.stream().mapToInt(Integer::intValue).toArray(), null,
                (System.nanoTime() - start) / 1_000_000);
    }

    /** Writes the graph (with its vectors) and the id map into {@code directory}. */
    public void save(Path directory) throws IOException {
        if (values == null) throw new IllegalStateException("Already on disk");
        Files.createDirectories(directory);
        Path tmp = Files.createTempFile(directory, "graph", ".tmp");
        OnDiskGraphIndex.write(graph, values, tmp);
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(directory.resolve(IDS_FILE)))) {
            out.writeInt(nodeToId.length);
            for (int id : nodeToId) out.writeInt(id);
        }
        Files.move(tmp, directory.resolve(GRAPH_FILE), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    /** Memory-maps a graph written by {@link #save}; empty when there's nothing usable there. */
    public static java.util.Optional<HnswVectorIndex> load(Path directory) {
        Path graphFile = directory.resolve(GRAPH_FILE);
        Path idsFile = directory.resolve(IDS_FILE);
        if (!Files.isRegularFile(graphFile) || !Files.isRegularFile(idsFile)) return java.util.Optional.empty();
        try {
            int[] ids;
            try (DataInputStream in = new DataInputStream(Files.newInputStream(idsFile))) {
                ids = new int[in.readInt()];
                for (int i = 0; i < ids.length; i++) ids[i] = in.readInt();
            }
            ReaderSupplier reader = new SimpleMappedReader.Supplier(graphFile);
            OnDiskGraphIndex graph = OnDiskGraphIndex.load(reader);
            if (graph.size() != ids.length) {
                graph.close();
                reader.close();
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new HnswVectorIndex(graph, null, ids, reader, 0));
        } catch (IOException | RuntimeException e) {
            return java.util.Optional.empty(); // a damaged or old file is simply rebuilt
        }
    }

    @Override
    public List<Integer> nearest(float[] query, int k, IntPredicate accept) {
        if (k <= 0) return List.of();
        VectorFloat<?> q = VECTORS.createFloatVector(query);
        Bits allowed = node -> accept.test(nodeToId[node]);
        try (GraphSearcher searcher = new GraphSearcher(graph)) {
            RandomAccessVectorValues source = values != null ? values : (RandomAccessVectorValues) searcher.getView();
            SearchScoreProvider scores = SearchScoreProvider.exact(q, SIMILARITY, source);
            SearchResult result = searcher.search(scores, k, Math.max(k, SEARCH_BEAM), 0f, 0f, allowed);
            List<Integer> out = new ArrayList<>();
            for (SearchResult.NodeScore hit : result.getNodes()) out.add(nodeToId[hit.node]);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int size() {
        return nodeToId.length;
    }

    @Override
    public String kind() {
        return "hnsw (jvector)";
    }

    long buildMillis() {
        return buildMillis;
    }

    @Override
    public void close() {
        try {
            graph.close();
            if (reader != null) reader.close();
        } catch (Exception ignored) {
            // closing a read-only index can't lose data
        }
    }
}
