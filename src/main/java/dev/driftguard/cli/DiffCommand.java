package dev.driftguard.cli;

import dev.driftguard.report.JsonReport;
import dev.driftguard.report.PrComment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "diff", mixinStandardHelpOptions = true,
        description = "Compare two JSON audits (a PR's base and head) and write a Markdown PR comment about what changed.")
final class DiffCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "BASE", description = "JSON audit of the base commit.")
    private Path base;

    @Parameters(index = "1", paramLabel = "HEAD", description = "JSON audit of the head commit.")
    private Path head;

    @Option(names = "--out", description = "Where to write the comment. Default: ${DEFAULT-VALUE}.")
    private Path out = Path.of("driftguard-comment.md");

    @Option(names = "--repo-url", description = "https://github.com/owner/repo, so findings link to the head commit's lines.")
    private String repoUrl;

    @Option(names = "--link-commit", description = "Commit to link lines to, such as a PR's head when the audit ran on a temporary merge commit.")
    private String linkCommit;

    @Option(names = "--fail-on", description = "none, likely or any: exit with code 2 when the PR adds findings of that kind. Default: ${DEFAULT-VALUE}.")
    private String failOn = "none";

    @Override
    public Integer call() {
        try {
            PrComment.FailOn threshold = PrComment.FailOn.valueOf(failOn.toUpperCase(Locale.ROOT));
            JsonReport.Audit before = JsonReport.read(base);
            JsonReport.Audit after = JsonReport.read(head);
            PrComment.Diff diff = PrComment.diff(before, after);
            Files.writeString(out, PrComment.render(diff, before, after, repoUrl, linkCommit));
            System.out.printf("New likely issues: %d. New leads: %d. Fixed: %d.%nComment: %s%n",
                    diff.newLikely().size(), diff.newLeads().size(), diff.fixed().size(), out.toAbsolutePath());
            return diff.shouldFail(threshold) ? 2 : 0;
        } catch (IllegalArgumentException e) {
            System.err.println("error: --fail-on must be none, likely or any");
            return 1;
        } catch (IOException e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        }
    }
}
