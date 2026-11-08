package dev.driftguard.cli;

import dev.driftguard.ingest.HelperRunner;
import dev.driftguard.llm.LlmFactory;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "doctor", mixinStandardHelpOptions = true, description = "Check that everything DriftGuard needs is installed.")
final class DoctorCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        boolean ok = true;
        System.out.println("Java:   " + System.getProperty("java.version"));

        var python = HelperRunner.pythonVersion();
        System.out.println("Python: " + python.orElse("MISSING - install Python 3.10 or newer"));
        ok &= python.isPresent();

        var node = HelperRunner.nodeVersion();
        System.out.println("Node:   " + node.orElse("MISSING - install Node.js"));
        ok &= node.isPresent();

        // All optional: with none of these, audits still run with static analysis only.
        var ollama = LlmFactory.fromSystem().ollamaModel(null);
        System.out.println("Free LLM options (optional):");
        System.out.println("  Ollama: " + ollama.map(model -> "ready, using " + model)
                .orElse("not running or no model (run: ollama serve, then: ollama pull qwen2.5:3b)"));
        System.out.println("  Groq:   " + keyStatus("GROQ_API_KEY", "https://console.groq.com/keys"));
        System.out.println("  Gemini: " + keyStatus("GEMINI_API_KEY", "https://aistudio.google.com/apikey"));

        System.out.println(ok ? "Ready." : "Not ready: fix the MISSING items above.");
        return ok ? 0 : 1;
    }

    private static String keyStatus(String name, String whereToGetOne) {
        String value = System.getenv(name);
        return value != null && !value.isBlank() ? "key found" : "no " + name + " (free key: " + whereToGetOne + ")";
    }
}
