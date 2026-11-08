package dev.driftguard.agents;

import java.util.List;

/** One issue an agent reports. */
public record Finding(
        String agent,
        String category,
        Severity severity,
        Confidence confidence,
        DecidedBy decidedBy,
        String title,
        String description,
        String filePath,
        Integer startLine,
        List<String> evidence,
        String fixSuggestion,
        String fingerprint) {

    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW }

    public enum Confidence { HIGH, MEDIUM, LOW }

    /** Who made the call: static analysis, the LLM, or nobody yet (a person should look). */
    public enum DecidedBy { STATIC, LLM, UNVERIFIED }
}
