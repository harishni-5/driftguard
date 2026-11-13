package dev.driftguard.agents.patterndrift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.driftguard.agents.AuditAgent;
import dev.driftguard.agents.Finding;
import dev.driftguard.agents.specdrift.SpecDriftAgent;
import dev.driftguard.ingest.RepoIngestor;
import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.RepoSource;
import dev.driftguard.rag.RagIndex;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Retrieval feeding the agents: call sites for Pattern-Drift's LLM check, likely moves for Spec-Drift. */
class RetrievedContextTest {

    @TempDir
    Path dir;

    private RepoSnapshot repo(Map<String, String> files) throws Exception {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = dir.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
        try (RepoSource source = RepoSource.open(dir.toString())) {
            return RepoIngestor.ingest(source);
        }
    }

    private static String fn(String name, String verb) {
        return "export async function " + name + "(account) {\n"
                + "  const rows = await database.query('select * from invoices where account = ?', [account]);\n"
                + "  const open = rows.filter(row => row.status === 'open');\n"
                + "  const total = open.reduce((sum, row) => sum + row.amount, 0);\n"
                + "  logger.info('" + verb + " invoices', { account, total });\n"
                + "  return { account, total, count: open.length };\n"
                + "}\n";
    }

    @Test
    void promptV3AddsCallSitesAndDocsButV2DoesNot() throws Exception {
        RepoSnapshot snapshot = repo(Map.of(
                "src/billing.js", fn("summarizeInvoices", "summarized"),
                "src/reports.js", fn("totalOpenInvoices", "totalled"),
                "src/routes.js", "import { summarizeInvoices } from './billing';\n\nexport async function handle(req) {\n"
                        + "  const summary = await summarizeInvoices(req.user.account);\n  return json(summary);\n}\n",
                "docs/billing.md", "# Billing\n\nThe dashboard calls totalOpenInvoices to show what a customer owes.\n"));
        SimilarFunctions.Group group = SimilarFunctions.find(snapshot, new CodeAreas(snapshot)).similar().get(0);
        RagIndex rag = RagIndex.build(snapshot, null, null, m -> {});

        String v3 = PatternDriftAgent.userPrompt(group, rag);
        assertTrue(v3.contains("<context>"), v3);
        assertTrue(v3.contains("summarizeInvoices(req.user.account)"), v3);
        assertTrue(v3.contains("used at src/routes.js:"), v3);
        assertTrue(v3.contains("mentioned in docs/billing.md"), v3);
        assertFalse(v3.substring(v3.indexOf("<context>")).contains("function totalOpenInvoices"), "definitions aren't call sites");

        assertFalse(PatternDriftAgent.userPrompt(group, null).contains("<context>"));
        assertTrue(PatternDriftAgent.PROMPTS.get("v3").contains("<context>"));
        assertTrue(PatternDriftAgent.usesContext("v3") && !PatternDriftAgent.usesContext("v2"));
    }

    @Test
    void noRelatedCodeMeansNoContextBlock() throws Exception {
        RepoSnapshot snapshot = repo(Map.of(
                "src/billing.js", fn("summarizeInvoices", "summarized"),
                "src/reports.js", fn("totalOpenInvoices", "totalled")));
        SimilarFunctions.Group group = SimilarFunctions.find(snapshot, new CodeAreas(snapshot)).similar().get(0);

        assertFalse(PatternDriftAgent.userPrompt(group, RagIndex.build(snapshot, null, null, m -> {})).contains("<context>"));
    }

    @Test
    void brokenDocPathsNameWhereTheFileMoved() throws Exception {
        RepoSnapshot snapshot = repo(Map.of(
                "README.md", "See [setup](docs/setup.md) and [the guide](docs/deploy-guide.md) and [gone](docs/nothing-here.md).\n"
                        + "```bash\npython scripts/seed_db.py\n```\n",
                "documentation/setup.md", "# Setup\n\nInstall it.\n",
                "documentation/deploy-guide-v2.md", "# Deploying\n\nShip it.\n",
                "tools/seed_db.py", "print('seed')\nprint('done')\n"));
        RagIndex rag = RagIndex.build(snapshot, null, null, m -> {});
        AuditAgent.Report report = new SpecDriftAgent().run(new AuditAgent.Context(snapshot, null, t -> {}, rag));

        Map<String, String> fixes = new HashMap<>();
        for (Finding f : report.findings()) fixes.put(f.title().substring(f.title().indexOf(": ") + 2), f.fixSuggestion());
        assertTrue(fixes.get("docs/setup.md").contains("exists at documentation/setup.md"), fixes.toString());
        assertTrue(fixes.get("scripts/seed_db.py").contains("exists at tools/seed_db.py"), fixes.toString());
        assertTrue(fixes.get("docs/deploy-guide.md").contains("similarly named file exists at documentation/deploy-guide-v2.md"), fixes.toString());
        assertTrue(fixes.get("docs/nothing-here.md").startsWith("Point the link"), "no guess when nothing matches: " + fixes);
        assertEquals(3, report.stats().get("pathsLocated"));
    }
}
