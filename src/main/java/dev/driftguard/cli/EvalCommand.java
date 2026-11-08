package dev.driftguard.cli;

import dev.driftguard.agents.patterndrift.PatternDriftEval;
import dev.driftguard.llm.LlmClient;
import dev.driftguard.llm.LlmFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "eval", mixinStandardHelpOptions = true,
        description = "Score the Pattern-Drift LLM check against hand-labeled groups.")
final class EvalCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "LABELS", description = "Label file, e.g. eval/pattern-drift-labels.json.")
    private Path labels;

    @Option(names = "--out", description = "Where to write the results. Default: ${DEFAULT-VALUE}.")
    private Path out = Path.of("eval-results.md");

    @Option(names = "--dump", description = "Write every group with its code to --out for labeling, without calling an LLM.")
    private boolean dump;

    @Option(names = "--repo-dir", description = "Folder holding already-cloned repos, named as in the label file. Saves recloning.")
    private Path repoDir;

    @Option(names = "--llm", description = "auto, ollama, groq, gemini or claude. Default: ${DEFAULT-VALUE}.")
    private String llmProvider = "auto";

    @Option(names = "--model", description = "Model id. Default: picked for the provider.")
    private String model;

    @Option(names = "--prompt", split = ",", description = "Prompt versions to score, e.g. v1,v2. Default: the one the agent uses.")
    private List<String> prompts = List.of(dev.driftguard.agents.patterndrift.PatternDriftAgent.CURRENT_PROMPT);

    private static Path withSuffix(Path path, String suffix) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String renamed = dot < 0 ? name + suffix : name.substring(0, dot) + suffix + name.substring(dot);
        return path.resolveSibling(renamed);
    }

    @Override
    public Integer call() {
        try {
            for (String version : prompts) {
                if (!dev.driftguard.agents.patterndrift.PatternDriftAgent.PROMPTS.containsKey(version)) {
                    throw new IllegalArgumentException("Unknown prompt version " + version + ". Known: "
                            + dev.driftguard.agents.patterndrift.PatternDriftAgent.PROMPTS.keySet());
                }
            }
            PatternDriftEval.LabelFile file = PatternDriftEval.read(labels);
            if (dump) {
                List<String> warnings = new ArrayList<>();
                Files.writeString(out, PatternDriftEval.dump(file, repoDir, warnings));
                warnings.forEach(w -> System.err.println("warning: " + w));
                System.out.println("Groups written to " + out.toAbsolutePath());
                return 0;
            }
            LlmClient llm = LlmFactory.fromSystem().create(llmProvider, model).orElse(null);
            if (llm == null) {
                System.err.println("error: no LLM found. Set GROQ_API_KEY first (see the README).");
                return 1;
            }
            System.out.println("Using model: " + llm.model());
            for (String version : prompts) {
                PatternDriftEval.Outcome outcome = PatternDriftEval.run(file, repoDir, llm, version,
                        step -> System.out.print("\rPrompt " + version + ": checking group " + step + "   "));
                System.out.println();
                Path target = prompts.size() == 1 ? out : withSuffix(out, "-" + version);
                Files.writeString(target, PatternDriftEval.report(outcome, llm.model()));
                PatternDriftEval.Score s = outcome.score();
                System.out.printf("Prompt %s: scored %d groups. Precision %s, recall %s (word similarity alone: %s). Failed calls: %d.%n",
                        version, s.scored(), pct(s.precision()), pct(s.recall()), pct(s.baselinePrecision()), s.errors());
                outcome.warnings().forEach(w -> System.err.println("warning: " + w));
                System.out.println("Results: " + target.toAbsolutePath());
            }
            return 0;
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("error: interrupted");
            return 1;
        }
    }

    private static String pct(double value) {
        return Double.isNaN(value) ? "n/a" : Math.round(value * 100) + "%";
    }
}
