package dev.driftguard.cli;

import dev.driftguard.AuditPipeline;
import dev.driftguard.agents.AuditAgent;
import dev.driftguard.agents.deaddep.DeadDepAgent;
import dev.driftguard.agents.patterndrift.PatternDriftAgent;
import dev.driftguard.agents.specdrift.SpecDriftAgent;
import dev.driftguard.llm.LlmClient;
import dev.driftguard.llm.LlmFactory;
import dev.driftguard.report.HtmlReport;
import dev.driftguard.report.MarkdownReport;
import dev.driftguard.store.Database;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "audit", mixinStandardHelpOptions = true, description = "Audit a local folder or an https:// git URL.")
final class AuditCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "REPO", description = "Local folder or https:// git URL.")
    private String repo;

    @Option(names = "--out", description = "Where to write the report. A name ending in .html gets the HTML report, anything else Markdown. Default: ${DEFAULT-VALUE}.")
    private Path out = Path.of("driftguard-report.md");

    @Option(names = "--db", description = "SQLite database file. Default: ${DEFAULT-VALUE}.")
    private Path db = Path.of(System.getProperty("user.home"), ".driftguard", "driftguard.db");

    @Option(names = "--no-llm", description = "Static analysis only. Sends nothing anywhere.")
    private boolean noLlm;

    @Option(names = "--llm", description = "auto, ollama, groq, gemini or claude. auto only uses free options; claude is paid. Default: ${DEFAULT-VALUE}.")
    private String llmProvider = "auto";

    @Option(names = "--model", description = "Model id. Default: picked for the provider.")
    private String model;

    @Override
    public Integer call() {
        try (Database database = Database.open(db)) {
            LlmClient llm = createLlm();
            List<AuditAgent> agents = List.of(new DeadDepAgent(), new PatternDriftAgent(), new SpecDriftAgent());
            AuditPipeline.Result result = AuditPipeline.run(repo, database, llm, agents);
            boolean html = out.getFileName().toString().toLowerCase(java.util.Locale.ROOT).matches(".*\\.html?$");
            Files.writeString(out, html ? HtmlReport.render(result) : MarkdownReport.render(result));

            System.out.printf("Audited %d files in %.1fs: %d findings%n",
                    result.snapshot().files().size(), result.durationMs() / 1000.0, result.findings().size());
            result.statsByAgent().forEach((agent, stats) -> System.out.println("  " + agent + ": " + stats));
            result.warnings().forEach(warning -> System.err.println("warning: " + warning));
            System.out.println("Report: " + out.toAbsolutePath());
            return 0;
        } catch (IOException | SQLException | IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("error: interrupted");
            return 1;
        }
    }

    private LlmClient createLlm() {
        if (noLlm) return null;
        LlmClient llm = LlmFactory.fromSystem().create(llmProvider, model).orElse(null);
        if (llm == null) {
            System.err.println("warning: no free LLM found, so running static analysis only. Run `driftguard doctor` for options.");
        } else {
            System.out.println("Using model: " + llm.model()
                    + (llm.verdictsAreAdvisory() ? " (local: its opinions are shown, but can't confirm a finding)" : ""));
        }
        return llm;
    }
}
