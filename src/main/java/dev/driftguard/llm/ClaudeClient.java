package dev.driftguard.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.Usage;
import java.util.Map;

/**
 * Claude through the official Java SDK, using structured outputs for typed answers.
 *
 * Server-side refusal fallback is deliberately left off. With it on, a result could
 * silently come from a different model, which would muddy the paper's evaluation.
 * A refusal becomes an LlmException and the finding stays marked as unverified.
 */
public final class ClaudeClient implements LlmClient {

    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";

    private static final long MAX_TOKENS = 16_000L;

    /** Standard API prices in USD per million tokens: {input, output}. */
    private static final Map<String, double[]> PRICES = Map.of(
            "claude-sonnet-5-5", new double[] {2.00, 10.00},
            "claude-haiku-5-5", new double[] {0.10, 0.50},
            "claude-opus-5-5", new double[] {4.00, 20.00});

    private final AnthropicClient client;
    private final String model;

    public ClaudeClient(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public <T> Result<T> structured(String system, String user, Class<T> type) throws LlmException {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(system)
                .outputConfig(type)
                .addUserMessage(user)
                .build();

        long start = System.nanoTime();
        StructuredMessage<T> response;
        try {
            response = client.messages().create(params);
        } catch (AnthropicException e) {
            throw new LlmException("Claude API call failed: " + e.getMessage(), e);
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        // Check the stop reason first: refused or cut-off output has no valid JSON to read.
        StopReason stop = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) throw new LlmException("Claude declined this request (stop_reason=refusal)");
        if (StopReason.MAX_TOKENS.equals(stop)) throw new LlmException("Claude's answer hit max_tokens and was cut off");

        T value;
        try {
            value = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .orElseThrow(() -> new LlmException("Claude returned no text block"))
                    .text();
        } catch (AnthropicException e) {
            throw new LlmException("Could not parse Claude's structured answer: " + e.getMessage(), e);
        }

        Usage usage = response.usage();
        long inputTokens = usage.inputTokens();
        long outputTokens = usage.outputTokens();
        return new Result<>(value, inputTokens, outputTokens, cost(inputTokens, outputTokens), latencyMs);
    }

    private double cost(long inputTokens, long outputTokens) {
        double[] price = PRICES.get(model);
        if (price == null) return 0; // unknown model: report no cost rather than a wrong one
        return (inputTokens * price[0] + outputTokens * price[1]) / 1_000_000.0;
    }

    @Override
    public String model() {
        return model;
    }
}
