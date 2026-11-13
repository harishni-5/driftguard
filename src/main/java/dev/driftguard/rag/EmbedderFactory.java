package dev.driftguard.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.driftguard.llm.LlmFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Picks an embedding model. Like the LLM choice, auto mode only uses free options, and
 * prefers the local one so code stays on the machine when it can.
 */
public final class EmbedderFactory {

    static final String OLLAMA_DEFAULT = "nomic-embed-text";
    static final String GEMINI_MODEL = "gemini-embedding-001";
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final Function<String, String> env;
    private final String ollamaUrl;

    public EmbedderFactory(Function<String, String> env, String ollamaUrl) {
        this.env = env;
        this.ollamaUrl = ollamaUrl.endsWith("/") ? ollamaUrl.substring(0, ollamaUrl.length() - 1) : ollamaUrl;
    }

    public static EmbedderFactory fromSystem() {
        return new EmbedderFactory(System::getenv, LlmFactory.DEFAULT_OLLAMA_URL);
    }

    /**
     * @param choice auto, ollama, gemini or none
     * @return empty for none, or when auto finds nothing (search then uses keywords only)
     * @throws IllegalArgumentException when a named option can't be used, saying how to fix it
     */
    public Optional<Embedder> create(String choice) {
        switch (choice.toLowerCase(Locale.ROOT)) {
            case "none":
                return Optional.empty();
            case "local": // auto, but never sending text off the machine
                return ollamaEmbedModel().map(this::ollama);
            case "auto": {
                Optional<String> local = ollamaEmbedModel();
                if (local.isPresent()) return Optional.of(ollama(local.get()));
                return isSet("GEMINI_API_KEY") ? Optional.of(gemini()) : Optional.empty();
            }
            case "ollama":
                return Optional.of(ollama(ollamaEmbedModel().orElseThrow(() -> new IllegalArgumentException(
                        "No Ollama embedding model found at " + ollamaUrl + ". Start Ollama and run `ollama pull " + OLLAMA_DEFAULT + "`."))));
            case "gemini":
                if (!isSet("GEMINI_API_KEY")) {
                    throw new IllegalArgumentException("GEMINI_API_KEY isn't set. Get a free key at https://aistudio.google.com/apikey");
                }
                return Optional.of(gemini());
            default:
                throw new IllegalArgumentException("Unknown --embed value: " + choice + ". Use auto, ollama, gemini or none.");
        }
    }

    private Embedder ollama(String model) {
        // nomic-embed-text was trained with these task prefixes and retrieves noticeably better with them.
        boolean nomic = model.startsWith("nomic-embed");
        return new HttpEmbedder(HttpEmbedder.Style.OPENAI, ollamaUrl, null, model,
                nomic ? "search_document: " : "", nomic ? "search_query: " : "", 32, false);
    }

    private Embedder gemini() {
        return new HttpEmbedder(HttpEmbedder.Style.GEMINI, GEMINI_URL, env.apply("GEMINI_API_KEY"), GEMINI_MODEL, "", "", 50, true);
    }

    /** An installed Ollama model with "embed" in its name, preferring nomic-embed-text. */
    Optional<String> ollamaEmbedModel() {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaUrl + "/models")).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();
            List<String> embedModels = new ArrayList<>();
            for (JsonNode entry : new ObjectMapper().readTree(response.body()).path("data")) {
                String id = entry.path("id").asText();
                if (id.toLowerCase(Locale.ROOT).contains("embed")) embedModels.add(id);
            }
            return embedModels.stream().filter(id -> id.startsWith(OLLAMA_DEFAULT)).findFirst()
                    .or(() -> embedModels.stream().findFirst());
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private boolean isSet(String name) {
        String value = env.apply(name);
        return value != null && !value.isBlank();
    }
}
