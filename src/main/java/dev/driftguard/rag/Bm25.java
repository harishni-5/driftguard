package dev.driftguard.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Okapi BM25 keyword search. Exact names (a function, an env var, a file) are where
 * embeddings are weakest, so this is half of every hybrid search, and all of it when no
 * embedding model is available.
 */
final class Bm25 {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final List<Map<String, Integer>> termCounts = new ArrayList<>();
    private final int[] lengths;
    private final Map<String, List<Integer>> postings = new HashMap<>();
    private final double averageLength;

    Bm25(List<List<String>> documents) {
        lengths = new int[documents.size()];
        long total = 0;
        for (int i = 0; i < documents.size(); i++) {
            Map<String, Integer> counts = new HashMap<>();
            for (String token : documents.get(i)) counts.merge(token, 1, Integer::sum);
            termCounts.add(counts);
            lengths[i] = documents.get(i).size();
            total += lengths[i];
            for (String token : counts.keySet()) postings.computeIfAbsent(token, k -> new ArrayList<>()).add(i);
        }
        averageLength = documents.isEmpty() ? 1 : Math.max(1.0, (double) total / documents.size());
    }

    /** Scores for every document that shares a word with the query. */
    Map<Integer, Double> score(List<String> query) {
        Map<Integer, Double> scores = new HashMap<>();
        int n = lengths.length;
        for (String token : query.stream().distinct().toList()) {
            List<Integer> docs = postings.get(token);
            if (docs == null) continue;
            double idf = Math.log(1 + (n - docs.size() + 0.5) / (docs.size() + 0.5));
            for (int doc : docs) {
                int tf = termCounts.get(doc).get(token);
                double norm = tf * (K1 + 1) / (tf + K1 * (1 - B + B * lengths[doc] / averageLength));
                scores.merge(doc, idf * norm, Double::sum);
            }
        }
        return scores;
    }
}
