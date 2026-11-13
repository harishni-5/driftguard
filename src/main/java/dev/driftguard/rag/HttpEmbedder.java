package dev.driftguard.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.driftguard.llm.LlmClient.LlmException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Embeddings over HTTP from the two free options:
 * <ul>
 *   <li>Ollama's OpenAI-style {@code /v1/embeddings} (local, nothing leaves the machine)</li>
 *   <li>Gemini's {@code batchEmbedContents} (free tier, supports separate query and document modes)</li>
 * </ul>
 */
final class HttpEmbedder implements Embedder {

    enum Style { OPENAI, GEMINI }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ATTEMPTS = 4;
    private static final int MAX_CHARS = 6000; // both models cut off around 2,000 tokens anyway
    static final int GEMINI_DIMENSIONS = 768;

    private final Style style;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String documentPrefix;
    private final String queryPrefix;
    private final int batchSize;
    private final boolean cloud;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    HttpEmbedder(Style style, String baseUrl, String apiKey, String model, String documentPrefix, String queryPrefix,
                 int batchSize, boolean cloud) {
        this.style = style;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.documentPrefix = documentPrefix;
        this.queryPrefix = queryPrefix;
        this.batchSize = batchSize;
        this.cloud = cloud;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public int batchSize() {
        return batchSize;
    }

    @Override
    public boolean isCloud() {
        return cloud;
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) throws LlmException {
        return embed(texts.stream().map(t -> documentPrefix + t).toList(), "RETRIEVAL_DOCUMENT");
    }

    @Override
    public float[] embedQuery(String text) throws LlmException {
        return embed(List.of(queryPrefix + text), "RETRIEVAL_QUERY").get(0);
    }

    private List<float[]> embed(List<String> texts, String geminiTask) throws LlmException {
        List<String> clipped = texts.stream().map(t -> t.length() <= MAX_CHARS ? t : t.substring(0, MAX_CHARS)).toList();
        ObjectNode body = JSON.createObjectNode();
        String url;
        if (style == Style.OPENAI) {
            url = baseUrl + "/embeddings";
            body.put("model", model);
            ArrayNode input = body.putArray("input");
            clipped.forEach(input::add);
        } else {
            url = baseUrl + "/models/" + model + ":batchEmbedContents";
            ArrayNode requests = body.putArray("requests");
            for (String text : clipped) {
                ObjectNode request = requests.addObject();
                request.put("model", "models/" + model);
                request.putObject("content").putArray("parts").addObject().put("text", text);
                request.put("taskType", geminiTask);
                request.put("outputDimensionality", GEMINI_DIMENSIONS);
            }
        }
        JsonNode response = post(url, body.toString());
        List<float[]> vectors = new ArrayList<>();
        if (style == Style.OPENAI) {
            JsonNode data = response.path("data");
            float[][] ordered = new float[data.size()][];
            for (JsonNode item : data) ordered[item.path("index").asInt(vectors.size())] = toFloats(item.path("embedding"));
            for (float[] vector : ordered) vectors.add(vector);
        } else {
            for (JsonNode item : response.path("embeddings")) vectors.add(toFloats(item.path("values")));
        }
        if (vectors.size() != texts.size() || vectors.stream().anyMatch(v -> v == null || v.length == 0)) {
            throw new LlmException("The embedding server returned " + vectors.size() + " vectors for " + texts.size() + " texts");
        }
        return vectors;
    }

    private JsonNode post(String url, String body) throws LlmException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(cloud ? 1 : 5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null && style == Style.GEMINI) request.header("x-goog-api-key", apiKey);
        if (apiKey != null && style == Style.OPENAI) request.header("Authorization", "Bearer " + apiKey);
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new LlmException("Could not reach " + baseUrl + ": " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("Interrupted while embedding", e);
            }
            int status = response.statusCode();
            if ((status == 429 || status == 503) && attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(5_000L * attempt); // free tiers rate-limit; back off and retry
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LlmException("Interrupted while waiting to retry", e);
                }
                continue;
            }
            if (status / 100 != 2) {
                String text = response.body();
                throw new LlmException("Embedding request failed with HTTP " + status + ": "
                        + (text.length() > 300 ? text.substring(0, 300) + "..." : text));
            }
            try {
                return JSON.readTree(response.body());
            } catch (IOException e) {
                throw new LlmException("The embedding server returned something that isn't JSON", e);
            }
        }
    }

    private static float[] toFloats(JsonNode array) {
        if (!array.isArray()) return null;
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) out[i] = (float) array.get(i).asDouble();
        return out;
    }
}
