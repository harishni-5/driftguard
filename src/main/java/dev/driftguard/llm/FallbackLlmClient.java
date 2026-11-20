package dev.driftguard.llm;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tries free LLMs in order and moves to the next when one fails, which on free tiers is
 * usually a per-minute limit. A public website needs this: one provider's quota runs out
 * after a few busy minutes, two rarely do at once.
 */
public final class FallbackLlmClient implements LlmClient {

    private final List<LlmClient> clients;

    public FallbackLlmClient(List<LlmClient> clients) {
        if (clients.isEmpty()) throw new IllegalArgumentException("Need at least one LLM");
        this.clients = List.copyOf(clients);
    }

    @Override
    public <T> Result<T> structured(String system, String user, Class<T> type) throws LlmException {
        LlmException last = null;
        for (LlmClient client : clients) {
            try {
                return client.structured(system, user, type);
            } catch (LlmException e) {
                last = e;
            }
        }
        throw last;
    }

    /** "openai/gpt-oss-120b, then gemini-2.5-flash" */
    @Override
    public String model() {
        return clients.stream().map(LlmClient::model).collect(Collectors.joining(", then "));
    }

    @Override
    public boolean verdictsAreAdvisory() {
        return clients.stream().anyMatch(LlmClient::verdictsAreAdvisory);
    }
}
