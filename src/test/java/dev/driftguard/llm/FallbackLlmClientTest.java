package dev.driftguard.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class FallbackLlmClientTest {

    private record Fixed(String model, String answer, String failure) implements LlmClient {
        @Override
        @SuppressWarnings("unchecked")
        public <T> Result<T> structured(String system, String user, Class<T> type) throws LlmException {
            if (failure != null) throw new LlmException(failure);
            return new Result<>((T) answer, 1, 1, 0, 1);
        }
    }

    @Test
    void movesToTheNextProviderWhenOneIsAtItsLimit() throws Exception {
        LlmClient llm = new FallbackLlmClient(List.of(new Fixed("groq", null, "HTTP 429"), new Fixed("gemini", "from gemini", null)));
        assertEquals("from gemini", llm.structured("s", "u", String.class).value());
        assertEquals("groq, then gemini", llm.model());
    }

    @Test
    void reportsTheLastFailureWhenAllFail() {
        LlmClient llm = new FallbackLlmClient(List.of(new Fixed("a", null, "first"), new Fixed("b", null, "second")));
        assertEquals("second", assertThrows(LlmClient.LlmException.class, () -> llm.structured("s", "u", String.class)).getMessage());
    }
}
