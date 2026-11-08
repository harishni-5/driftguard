package dev.driftguard.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.driftguard.AuditPipeline;
import dev.driftguard.agents.Finding;
import dev.driftguard.agents.Finding.Confidence;
import dev.driftguard.agents.Finding.DecidedBy;
import dev.driftguard.agents.Finding.Severity;
import dev.driftguard.agents.deaddep.DeadDepAgent;
import dev.driftguard.agents.patterndrift.PatternDriftAgent;
import dev.driftguard.agents.specdrift.SpecDriftAgent;
import dev.driftguard.report.JsonReport.Audit;
import dev.driftguard.store.Database;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrCommentTest {

    @TempDir
    Path dir;

    private static Finding finding(String title, DecidedBy by, String fingerprint) {
        return new Finding("spec-drift", "missing-file", Severity.LOW, Confidence.HIGH, by, title, "why",
                "README.md", 3, List.of(), "fix", fingerprint);
    }

    private static Audit audit(String commit, Finding... findings) {
        return new Audit(JsonReport.SCHEMA, "0.1.0", "/repo", commit, null, List.of(findings), Map.of(), List.of());
    }

    @Test
    void reportsOnlyWhatThePrAddedAndWhatItFixed() {
        Finding old = finding("Broken link: old.md", DecidedBy.STATIC, "aaa");
        Finding fixedOne = finding("Broken link: gone.md", DecidedBy.STATIC, "bbb");
        Finding added = finding("Broken link: new.md", DecidedBy.STATIC, "ccc");
        Finding lead = finding("Documented variable is never read: X_KEY", DecidedBy.UNVERIFIED, "ddd");

        PrComment.Diff diff = PrComment.diff(audit("1111111aaa", old, fixedOne), audit("2222222bbb", old, added, lead));
        String comment = PrComment.render(diff, audit("1111111aaa"), audit("2222222bbb"), "https://github.com/me/app");

        assertEquals(List.of(added), diff.newLikely());
        assertEquals(List.of(lead), diff.newLeads());
        assertEquals(List.of(fixedOne), diff.fixed());
        assertTrue(comment.startsWith(PrComment.MARKER));
        assertTrue(comment.contains("adds **1 likely issue** and **1 lead to review**. It fixes **1 existing finding**."), comment);
        assertTrue(comment.contains("[README.md:3](https://github.com/me/app/blob/2222222bbb/README.md#L3)"), comment);
        assertFalse(comment.contains("old.md"), "findings that were already there aren't repeated");
        assertTrue(diff.shouldFail(PrComment.FailOn.LIKELY));
        assertFalse(diff.shouldFail(PrComment.FailOn.NONE));
    }

    @Test
    void linksToThePrHeadWhenTheAuditRanOnAMergeCommit() {
        Finding added = finding("Broken link: new.md", DecidedBy.STATIC, "ccc");
        PrComment.Diff diff = PrComment.diff(audit("1111111"), audit("mmmmmmm", added));
        String comment = PrComment.render(diff, audit("1111111"), audit("mmmmmmm"), "https://github.com/me/app", "abcdef1234");

        assertTrue(comment.contains("/blob/abcdef1234/README.md#L3"), comment);
        assertTrue(comment.contains("head `abcdef1`"), comment);
    }

    @Test
    void saysSoWhenNothingChanged() {
        Finding same = finding("Broken link: old.md", DecidedBy.STATIC, "aaa");
        PrComment.Diff diff = PrComment.diff(audit("1", same), audit("2", same));

        assertTrue(PrComment.render(diff, audit("1"), audit("2"), null).contains("No new drift in this pull request."));
        assertFalse(diff.shouldFail(PrComment.FailOn.ANY));
    }

    @Test
    void repoTextCannotPingPeopleOrInjectLinksOrHtml() {
        String hostile = "Broken link: @octocat [click](https://evil.example) <img src=x> | col";
        String safe = PrComment.md(hostile);

        assertFalse(safe.contains("@octocat"), "no notification");
        assertFalse(safe.contains("[click](https"), "no clickable link");
        assertFalse(safe.contains("<img"), "no HTML");
        assertTrue(safe.contains("\\|"), "can't break the table");
    }

    @Test
    void movingCodeIsNotNewDriftButAddingACopyIs() throws Exception {
        String body = """
                  const items = await loadItems(account);
                  const active = items.filter(isActive);
                  const total = active.reduce(sumPrices, 0);
                  const label = formatMoney(total);
                  return { label, total };
                }
                """;
        Path repo = Files.createDirectories(dir.resolve("repo"));
        Files.writeString(repo.resolve("a.js"), "export async function cartTotal(account) {\n" + body);
        Files.writeString(repo.resolve("README.md"), "See [setup](docs/setup.md).\n");
        Audit base = auditOf(repo);

        // Same broken link, pushed down a few lines; plus a new copy of cartTotal.
        Files.writeString(repo.resolve("README.md"), "# App\n\nIntro.\n\nSee [setup](docs/setup.md).\n");
        Files.writeString(repo.resolve("b.js"), "export async function checkoutSum(account) {\n" + body);
        Audit head = auditOf(repo);

        PrComment.Diff diff = PrComment.diff(base, head);
        assertEquals(1, diff.newLikely().size(), diff.toString());
        assertTrue(diff.newLikely().get(0).title().startsWith("Same code copied 2 times"), diff.toString());
        assertTrue(diff.fixed().isEmpty());
    }

    private Audit auditOf(Path repo) throws Exception {
        Path json = Files.createTempFile(dir, "audit", ".json");
        try (Database database = Database.open(dir.resolve("db.sqlite"))) {
            AuditPipeline.Result result = AuditPipeline.run(repo.toString(), database, null,
                    List.of(new DeadDepAgent(), new PatternDriftAgent(true), new SpecDriftAgent()));
            Files.writeString(json, JsonReport.render(result));
        }
        return JsonReport.read(json);
    }
}
