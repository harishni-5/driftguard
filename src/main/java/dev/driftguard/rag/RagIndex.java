package dev.driftguard.rag;

import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.llm.LlmClient.LlmException;
import dev.driftguard.store.Database;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Searchable index of one repo: BM25 keywords plus, when an embedding model is available,
 * vectors. Hybrid search merges both rankings with reciprocal rank fusion, so exact names
 * (keywords' strength) and paraphrased questions (vectors' strength) both find their code.
 *
 * Vectors are cached in SQLite by content hash, so a second run only embeds what changed.
 * Vector search uses an exact scan for normal repos and switches to an HNSW graph index
 * (jvector, saved to disk) above {@link #HNSW_THRESHOLD} vectors, where scanning everything
 * starts to cost. See {@code VectorBenchmark} for the measured trade-off.
 */
public final class RagIndex {

    public enum Mode { KEYWORD, VECTOR, HYBRID }

    /** @param keywordRank 1-based rank in keyword results, 0 if absent; same for vectorRank */
    public record Hit(Chunk chunk, double score, int keywordRank, int vectorRank) {}

    /** Stops a huge monorepo from spending an hour embedding on its first run. */
    static final int MAX_EMBEDDED_CHUNKS = 20_000;
    private static final int RRF_K = 60;
    private static final int FUSION_DEPTH = 100;
    /** Weight of the keyword ranking in hybrid fusion; vectors get 1. */
    static final double KEYWORD_WEIGHT = 1.0;
    /**
     * Tests and fixtures repeat the words of the code they test, so they crowd out the code itself for
     * "how does X work" questions. They still show up, just after the code at a similar rank.
     */
    static final double TEST_WEIGHT = 0.7;
    private static final java.util.regex.Pattern TEST_PATH = java.util.regex.Pattern.compile(
            "(?i)(^|.*/)(tests?|__tests__|specs?|e2e|test_files|fixtures?|__mocks__)/.*"
                    + "|(?i).*(^|/)(test_[^/]*|[^/]*_test\\.\\w+|[^/]*\\.(test|spec)\\.\\w+|conftest\\.py)$");

    private final List<Chunk> chunks;
    private final Bm25 bm25;
    private final Embedder embedder;
    private final VectorIndex vectors; // null when keyword-only
    private final List<String> warnings;

    private RagIndex(List<Chunk> chunks, Bm25 bm25, Embedder embedder, VectorIndex vectors, List<String> warnings) {
        this.chunks = chunks;
        this.bm25 = bm25;
        this.embedder = embedder;
        this.vectors = vectors;
        this.warnings = warnings;
    }

    /**
     * @param embedder null for keyword-only search
     * @param cache where vectors are kept between runs; null to keep nothing
     */
    public static RagIndex build(RepoSnapshot snapshot, Embedder embedder, Database cache, Consumer<String> progress)
            throws SQLException {
        return build(Chunker.chunk(snapshot), embedder, cache, progress);
    }

    /** How vectors are searched. AUTO is exact for small repos and HNSW for large ones. */
    public enum VectorStrategy { AUTO, EXACT, HNSW }

    /** Vector count at which AUTO switches from an exact scan to the HNSW graph. */
    public static final int HNSW_THRESHOLD = 5_000;

    /**
     * @param strategy how vectors are searched
     * @param indexDir where HNSW graphs are saved and reused, keyed by model and content; null keeps them in memory only
     */
    public static RagIndex build(RepoSnapshot snapshot, Embedder embedder, Database cache, Consumer<String> progress,
                                 VectorStrategy strategy, java.nio.file.Path indexDir) throws SQLException {
        return build(Chunker.chunk(snapshot), embedder, cache, progress, strategy, indexDir);
    }

    static RagIndex build(List<Chunk> chunks, Embedder embedder, Database cache, Consumer<String> progress) throws SQLException {
        return build(chunks, embedder, cache, progress, VectorStrategy.AUTO, null);
    }

    static RagIndex build(List<Chunk> chunks, Embedder embedder, Database cache, Consumer<String> progress,
                          VectorStrategy strategy, java.nio.file.Path indexDir) throws SQLException {
        List<String> warnings = java.util.Collections.synchronizedList(new ArrayList<>());
        List<List<String>> documents = new ArrayList<>();
        for (Chunk chunk : chunks) {
            List<String> tokens = new ArrayList<>(Tokens.of(chunk.indexText()));
            tokens.addAll(Tokens.of(chunk.name() + " " + chunk.path())); // names and paths count double
            documents.add(tokens);
        }
        Bm25 bm25 = new Bm25(documents);
        if (embedder == null || chunks.isEmpty()) return new RagIndex(chunks, bm25, null, null, warnings);

        float[][] vectors = new float[chunks.size()][];
        int limit = Math.min(chunks.size(), MAX_EMBEDDED_CHUNKS);
        if (chunks.size() > limit) {
            warnings.add("Embedded the first " + limit + " of " + chunks.size() + " chunks; the rest are found by keywords only.");
        }
        List<String> shas = new ArrayList<>();
        for (int i = 0; i < limit; i++) shas.add(sha256(chunks.get(i).indexText()));
        Map<String, float[]> cached = cache == null ? new HashMap<>() : cache.loadEmbeddings(embedder.model(), shas);

        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            float[] vector = cached.get(shas.get(i));
            if (vector != null) vectors[i] = normalized(vector);
            else missing.add(i);
        }
        int done = 0;
        try {
            for (int from = 0; from < missing.size(); from += embedder.batchSize()) {
                List<Integer> batch = missing.subList(from, Math.min(missing.size(), from + embedder.batchSize()));
                List<float[]> embedded = embedder.embedDocuments(batch.stream().map(i -> chunks.get(i).indexText()).toList());
                Map<String, float[]> toSave = new LinkedHashMap<>();
                for (int j = 0; j < batch.size(); j++) {
                    int i = batch.get(j);
                    vectors[i] = normalized(embedded.get(j));
                    toSave.put(shas.get(i), embedded.get(j));
                }
                if (cache != null) cache.saveEmbeddings(embedder.model(), toSave);
                done += batch.size();
                progress.accept("embedded " + done + "/" + missing.size() + " new chunks (" + cached.size() + " cached)");
            }
        } catch (LlmException e) {
            warnings.add("Embedding stopped (" + e.getMessage() + "), so search uses keywords only. Vectors made so far are cached.");
            return new RagIndex(chunks, bm25, null, null, warnings);
        }
        return new RagIndex(chunks, bm25, embedder, vectorIndex(vectors, shas, embedder.model(), strategy, indexDir, progress, warnings), warnings);
    }

    /** Exact scan, or an HNSW graph loaded from (or saved to) {@code indexDir}. */
    static VectorIndex vectorIndex(float[][] vectors, List<String> shas, String model, VectorStrategy strategy,
                                   java.nio.file.Path indexDir, Consumer<String> progress, List<String> warnings) {
        int count = 0;
        for (float[] v : vectors) if (v != null) count++;
        boolean hnsw = strategy == VectorStrategy.HNSW || (strategy == VectorStrategy.AUTO && count >= HNSW_THRESHOLD);
        if (!hnsw || count == 0) return new ExactVectorIndex(vectors);
        // The key covers the model and every embedded chunk's content, so a changed repo never reuses a stale graph.
        StringBuilder key = new StringBuilder(model);
        for (int i = 0; i < vectors.length; i++) if (vectors[i] != null) key.append('|').append(i).append(':').append(shas.get(i));
        java.nio.file.Path dir = indexDir == null ? null : indexDir.resolve(sha256(key.toString()).substring(0, 32));
        if (dir != null) {
            java.util.Optional<HnswVectorIndex> saved = HnswVectorIndex.load(dir);
            if (saved.isPresent()) {
                progress.accept("loaded the HNSW index from disk (" + count + " vectors)");
                return saved.get();
            }
        }
        progress.accept("building the HNSW index (" + count + " vectors)");
        HnswVectorIndex index = HnswVectorIndex.build(vectors);
        if (dir != null) {
            try {
                index.save(dir);
            } catch (java.io.IOException e) {
                warnings.add("Couldn't save the vector index (" + e.getMessage() + "); it will be rebuilt next time.");
            }
        }
        return index;
    }

    /**
     * The same chunks with vectors added, for upgrading a keyword-only index in the background. The
     * original stays usable meanwhile, and if embedding fails the result is keyword-only with a warning.
     */
    public RagIndex withVectors(Embedder embedder, Database cache, Consumer<String> progress) throws SQLException {
        return build(chunks, embedder, cache, progress, VectorStrategy.AUTO, null);
    }

    public List<Chunk> chunks() {
        return chunks;
    }

    public boolean hasVectors() {
        return vectors != null;
    }

    /** "nomic-embed-text:latest + BM25", or "BM25 only". */
    public String describe() {
        return hasVectors() ? embedder.model() + " + BM25 (hybrid, " + vectors.kind() + " vector index)" : "BM25 keywords only";
    }

    public List<String> warnings() {
        return warnings;
    }

    /** Hybrid when vectors exist, keywords otherwise. */
    public List<Hit> search(String query, int k) {
        return search(query, k, hasVectors() ? Mode.HYBRID : Mode.KEYWORD, chunk -> true);
    }

    /**
     * Searches with several phrasings of one question (from {@link QueryRewriter}) and merges the
     * rankings with reciprocal rank fusion. The first query, the question itself, counts double, so
     * rewrites can add results but rarely push out what the plain question found.
     */
    public List<Hit> search(List<String> queries, int k) {
        if (queries.size() == 1) return search(queries.get(0), k);
        Map<Integer, Double> fused = new HashMap<>();
        Map<Integer, Hit> best = new HashMap<>();
        for (int q = 0; q < queries.size(); q++) {
            double weight = q == 0 ? 2.0 : 1.0;
            List<Hit> hits = search(queries.get(q), FUSION_DEPTH);
            for (int r = 0; r < hits.size(); r++) {
                int id = hits.get(r).chunk().id();
                fused.merge(id, weight / (RRF_K + r + 1), Double::sum);
                best.putIfAbsent(id, hits.get(r));
            }
        }
        return fused.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(k)
                .map(e -> new Hit(best.get(e.getKey()).chunk(), e.getValue(), best.get(e.getKey()).keywordRank(),
                        best.get(e.getKey()).vectorRank()))
                .toList();
    }

    public List<Hit> search(String query, int k, Mode mode, Predicate<Chunk> filter) {
        List<Integer> keyword = mode == Mode.VECTOR ? List.of() : keywordRanking(query, filter);
        List<Integer> vector = List.of();
        if (mode != Mode.KEYWORD && hasVectors()) {
            try {
                vector = vectorRanking(embedder.embedQuery(query), filter);
            } catch (LlmException e) {
                if (!warnings.contains("Query embedding failed, so this search used keywords only: " + e.getMessage())) {
                    warnings.add("Query embedding failed, so this search used keywords only: " + e.getMessage());
                }
                if (mode == Mode.VECTOR) return List.of();
                keyword = keyword.isEmpty() ? keywordRanking(query, filter) : keyword;
            }
        }
        if (mode == Mode.KEYWORD || vector.isEmpty()) return toHits(keyword, k, true);
        if (mode == Mode.VECTOR) return toHits(vector, k, false);

        Map<Integer, Double> fused = new HashMap<>();
        Map<Integer, int[]> ranks = new HashMap<>();
        for (int r = 0; r < Math.min(FUSION_DEPTH, keyword.size()); r++) {
            fused.merge(keyword.get(r), KEYWORD_WEIGHT / (RRF_K + r + 1), Double::sum);
            ranks.computeIfAbsent(keyword.get(r), x -> new int[2])[0] = r + 1;
        }
        for (int r = 0; r < Math.min(FUSION_DEPTH, vector.size()); r++) {
            fused.merge(vector.get(r), 1.0 / (RRF_K + r + 1), Double::sum);
            ranks.computeIfAbsent(vector.get(r), x -> new int[2])[1] = r + 1;
        }
        fused.replaceAll((i, score) -> score * prior(chunks.get(i)));
        return fused.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(k)
                .map(e -> new Hit(chunks.get(e.getKey()), e.getValue(), ranks.get(e.getKey())[0], ranks.get(e.getKey())[1]))
                .toList();
    }

    private List<Integer> keywordRanking(String query, Predicate<Chunk> filter) {
        return bm25.score(Tokens.of(query)).entrySet().stream()
                .filter(e -> filter.test(chunks.get(e.getKey())))
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<Integer> vectorRanking(float[] query, Predicate<Chunk> filter) {
        return vectors.nearest(VectorIndex.normalized(query), FUSION_DEPTH, id -> filter.test(chunks.get(id)));
    }

    /** A single ranking, reordered by the same prior hybrid uses so all three modes compare fairly. */
    private List<Hit> toHits(List<Integer> ranking, int k, boolean keyword) {
        List<Hit> hits = new ArrayList<>();
        for (int r = 0; r < Math.min(FUSION_DEPTH, ranking.size()); r++) {
            Chunk chunk = chunks.get(ranking.get(r));
            hits.add(new Hit(chunk, prior(chunk) / (RRF_K + r + 1), keyword ? r + 1 : 0, keyword ? 0 : r + 1));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.subList(0, Math.min(k, hits.size()));
    }

    private static double prior(Chunk chunk) {
        return TEST_PATH.matcher(chunk.path()).matches() ? TEST_WEIGHT : 1.0;
    }

    private static float[] normalized(float[] vector) {
        return VectorIndex.normalized(vector);
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
