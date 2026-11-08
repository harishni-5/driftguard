package dev.driftguard.llm;

/** The one thing agents need from an LLM. Tests swap in a fake, so they cost nothing to run. */
public interface LlmClient {

    /** Asks for a JSON answer matching {@code type}, a record class. */
    <T> Result<T> structured(String system, String user, Class<T> type) throws LlmException;

    /** Model id, as saved in traces. */
    String model();

    /**
     * True when this model's verdicts are only an opinion to show a person, and can't
     * confirm a finding. Small local models answered too unreliably in real tests.
     */
    default boolean verdictsAreAdvisory() {
        return false;
    }

    record Result<T>(T value, long inputTokens, long outputTokens, double costUsd, long latencyMs) {}

    /** The call failed or was refused. Agents treat this as "not verified", never as a verdict. */
    final class LlmException extends Exception {
        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }

        public LlmException(String message) {
            super(message);
        }
    }
}
