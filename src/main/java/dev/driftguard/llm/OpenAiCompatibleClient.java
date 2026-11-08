package dev.driftguard.llm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Talks to any server using the OpenAI chat format. Groq, Gemini and Ollama all
 * offer this, so one class covers every free option. Free models vary in how well
 * they follow a schema, so the schema goes in the prompt and answers are parsed
 * tolerantly. An answer that still doesn't fit becomes an error, never a guess.
 */
public final class OpenAiCompatibleClient implements LlmClient {

    private static final int MAX_ATTEMPTS = 4;
    private static final long MAX_WAIT_SECONDS = 60;
    private static final int MAX_OUTPUT_TOKENS = 4096;
    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final Duration requestTimeout;
    private final long defaultWaitMillis;
    private final boolean advisory;

    /**
     * @param apiKey null for servers that don't need one, like local Ollama
     * @param requestTimeout local models on a CPU can take minutes per call
     * @param defaultWaitMillis wait before retrying when the server doesn't say how long
     */
    public OpenAiCompatibleClient(
            String baseUrl, String apiKey, String model, Duration requestTimeout, long defaultWaitMillis) {
        this(baseUrl, apiKey, model, requestTimeout, defaultWaitMillis, false);
    }

    /** @param advisory see {@link LlmClient#verdictsAreAdvisory()} */
    public OpenAiCompatibleClient(
            String baseUrl, String apiKey, String model, Duration requestTimeout, long defaultWaitMillis, boolean advisory) {
        this.advisory = advisory;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.requestTimeout = requestTimeout;
        this.defaultWaitMillis = defaultWaitMillis;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public <T> Result<T> structured(String system, String user, Class<T> type) throws LlmException {
        String fullSystem = system + "\n\nReply with only a JSON object, with no other text. It must match this shape:\n"
                + schemaOf(type);
        long start = System.nanoTime();
        JsonNode response = post(requestBody(fullSystem, user));
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        JsonNode choice = response.path("choices").path(0);
        if ("length".equals(choice.path("finish_reason").asText())) {
            throw new LlmException("The model's answer hit the length limit and was cut off");
        }
        String content = choice.path("message").path("content").asText("");
        T value;
        try {
            value = JSON.readValue(extractJson(content), type);
        } catch (IOException e) {
            throw new LlmException("The model's answer didn't match the expected JSON: " + shorten(content), e);
        }
        JsonNode usage = response.path("usage");
        return new Result<>(value, usage.path("prompt_tokens").asLong(), usage.path("completion_tokens").asLong(), 0.0, latencyMs);
    }

    private String requestBody(String system, String user) throws LlmException {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0); // as repeatable as these models allow
        body.put("max_tokens", MAX_OUTPUT_TOKENS);
        body.putObject("response_format").put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        try {
            return JSON.writeValueAsString(body);
        } catch (IOException e) {
            throw new LlmException("Could not build the request", e);
        }
    }

    private JsonNode post(String body) throws LlmException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null) request.header("Authorization", "Bearer " + apiKey);

        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new LlmException("Could not reach " + baseUrl + ": " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("Interrupted while waiting for the model", e);
            }
            int status = response.statusCode();
            boolean retryable = status == 429 || status == 503;
            if (retryable && attempt < MAX_ATTEMPTS) {
                sleep(waitMillis(response, attempt));
                continue;
            }
            if (status / 100 != 2) {
                String hint = status == 429 ? " (free-tier limit reached; wait and try again)" : "";
                throw new LlmException("HTTP " + status + hint + ": " + shorten(response.body()));
            }
            try {
                return JSON.readTree(response.body());
            } catch (IOException e) {
                throw new LlmException("The server returned something that isn't JSON: " + shorten(response.body()), e);
            }
        }
    }

    /** Free tiers rate-limit a lot, so respect Retry-After, up to a limit. */
    private long waitMillis(HttpResponse<String> response, int attempt) {
        return response.headers().firstValue("retry-after").map(value -> {
            try {
                return Math.min(Long.parseLong(value.trim()), MAX_WAIT_SECONDS) * 1000;
            } catch (NumberFormatException e) {
                return defaultWaitMillis * attempt;
            }
        }).orElse(defaultWaitMillis * attempt);
    }

    private static void sleep(long millis) throws LlmException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting to retry", e);
        }
    }

    /** Pulls the JSON out of answers wrapped in reasoning tags, code fences or chatter. */
    static String extractJson(String content) {
        String text = THINK_BLOCK.matcher(content).replaceAll("");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    /** A small JSON-shaped description of a record class, for the prompt. */
    static String schemaOf(Type type) {
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == List.class) {
            return "[" + schemaOf(parameterized.getActualTypeArguments()[0]) + ", ...]";
        }
        if (type instanceof Class<?> c) {
            if (c.isRecord()) {
                StringBuilder out = new StringBuilder("{");
                RecordComponent[] components = c.getRecordComponents();
                for (int i = 0; i < components.length; i++) {
                    if (i > 0) out.append(", ");
                    out.append('"').append(components[i].getName()).append("\": ").append(schemaOf(components[i].getGenericType()));
                }
                return out.append('}').toString();
            }
            if (c.isEnum()) {
                StringBuilder out = new StringBuilder("one of");
                for (Object constant : c.getEnumConstants()) out.append(" \"").append(constant).append('"');
                return out.toString();
            }
            return c.getSimpleName().toLowerCase(Locale.ROOT);
        }
        return "value";
    }

    private static String shorten(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public boolean verdictsAreAdvisory() {
        return advisory;
    }
}
