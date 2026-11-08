package dev.driftguard.llm;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Picks which LLM to use. Auto mode only ever picks free options. Claude costs money,
 * so it runs only when asked for by name, even if an Anthropic key happens to be set.
 */
public final class LlmFactory {

    public static final String DEFAULT_OLLAMA_URL = "http://localhost:11434/v1";

    private static final String GROQ_URL = "https://api.groq.com/openai/v1";
    private static final String GROQ_MODEL = "openai/gpt-oss-120b";
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/openai";
    private static final String GEMINI_MODEL = "gemini-2.5-flash";
    private static final Duration CLOUD_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration LOCAL_TIMEOUT = Duration.ofMinutes(15); // CPU-only laptops are slow
    private static final long RETRY_WAIT_MILLIS = 5_000;

    private final Function<String, String> env;
    private final String ollamaUrl;

    public LlmFactory(Function<String, String> env, String ollamaUrl) {
        this.env = env;
        this.ollamaUrl = ollamaUrl;
    }

    public static LlmFactory fromSystem() {
        return new LlmFactory(System::getenv, DEFAULT_OLLAMA_URL);
    }

    /**
     * @param provider auto, ollama, groq, gemini or claude
     * @param model overrides the provider's default; may be null
     * @return empty when auto mode finds nothing free to use
     * @throws IllegalArgumentException when a named provider can't be used, with a message saying why
     */
    public Optional<LlmClient> create(String provider, String model) {
        switch (provider.toLowerCase(Locale.ROOT)) {
            case "auto":
                if (isSet("GROQ_API_KEY")) return Optional.of(groq(model));
                if (isSet("GEMINI_API_KEY")) return Optional.of(gemini(model));
                return ollamaModel(model).map(this::ollama);
            case "ollama":
                return Optional.of(ollama(ollamaModel(model).orElseThrow(() -> new IllegalArgumentException(
                        "Ollama isn't available at " + ollamaUrl + ". Start it with `ollama serve`, and download a model with `ollama pull qwen2.5:3b`."))));
            case "groq":
                requireKey("GROQ_API_KEY", "https://console.groq.com/keys");
                return Optional.of(groq(model));
            case "gemini":
                requireKey("GEMINI_API_KEY", "https://aistudio.google.com/apikey");
                return Optional.of(gemini(model));
            case "claude":
                requireKey("ANTHROPIC_API_KEY", "https://console.anthropic.com/settings/keys (paid)");
                return Optional.of(new ClaudeClient(AnthropicOkHttpClient.fromEnv(), model != null ? model : ClaudeClient.DEFAULT_MODEL));
            default:
                throw new IllegalArgumentException("Unknown --llm value: " + provider + ". Use auto, ollama, groq, gemini or claude.");
        }
    }

    private LlmClient groq(String model) {
        return new OpenAiCompatibleClient(GROQ_URL, env.apply("GROQ_API_KEY"), model != null ? model : GROQ_MODEL, CLOUD_TIMEOUT, RETRY_WAIT_MILLIS);
    }

    private LlmClient gemini(String model) {
        return new OpenAiCompatibleClient(GEMINI_URL, env.apply("GEMINI_API_KEY"), model != null ? model : GEMINI_MODEL, CLOUD_TIMEOUT, RETRY_WAIT_MILLIS);
    }

    private LlmClient ollama(String model) {
        return new OpenAiCompatibleClient(ollamaUrl, null, model, LOCAL_TIMEOUT, RETRY_WAIT_MILLIS, true);
    }

    /** The requested model if Ollama is up, otherwise the first installed chat model. */
    public Optional<String> ollamaModel(String requested) {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaUrl + "/models")).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();
            if (requested != null) return Optional.of(requested);
            for (JsonNode entry : new ObjectMapper().readTree(response.body()).path("data")) {
                String id = entry.path("id").asText();
                if (!id.isEmpty() && !id.toLowerCase(Locale.ROOT).contains("embed")) return Optional.of(id);
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty(); // Ollama isn't running
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private void requireKey(String name, String whereToGetOne) {
        if (!isSet(name)) throw new IllegalArgumentException(name + " isn't set. Get a key at " + whereToGetOne);
    }

    private boolean isSet(String name) {
        String value = env.apply(name);
        return value != null && !value.isBlank();
    }
}
