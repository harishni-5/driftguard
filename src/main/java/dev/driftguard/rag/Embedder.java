package dev.driftguard.rag;

import dev.driftguard.llm.LlmClient.LlmException;
import java.util.List;

/** Turns text into vectors. Tests use a fake; real ones are free (local Ollama or Gemini's free tier). */
public interface Embedder {

    /** Model id, part of the cache key so vectors from different models never mix. */
    String model();

    /** Vectors for repo content, in the same order as {@code texts}. */
    List<float[]> embedDocuments(List<String> texts) throws LlmException;

    /** Some models embed questions differently from documents, so queries get their own call. */
    float[] embedQuery(String text) throws LlmException;

    /** How many texts go in one request. */
    default int batchSize() {
        return 32;
    }

    /** True when text leaves this computer, so callers can say so. */
    default boolean isCloud() {
        return true;
    }
}
