package dev.driftguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.driftguard.agents.AuditAgent;
import dev.driftguard.agents.Finding;
import dev.driftguard.agents.Finding.DecidedBy;
import dev.driftguard.agents.patterndrift.PatternDriftAgent;
import dev.driftguard.agents.specdrift.SpecDriftAgent;
import dev.driftguard.ingest.CodeOrigin;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Each test is a false-alarm pattern found by hand-checking 668 findings on 22 unseen repos
 * (eval/final-check.md). They pin the fixes so the patterns can't come back.
 */
class FinalTestFixesTest {

    @TempDir
    Path dir;

    private List<Finding> pattern(TestRepo repo) throws Exception {
        return new PatternDriftAgent().run(new AuditAgent.Context(repo.snapshot(), null, t -> {})).findings();
    }

    private List<Finding> spec(TestRepo repo) throws Exception {
        return new SpecDriftAgent().run(new AuditAgent.Context(repo.snapshot(), null, t -> {})).findings();
    }

    private static List<String> titles(List<Finding> findings) {
        return findings.stream().map(Finding::title).toList();
    }

    private static final String BODY = """
                const items = await loadItems(account);
                const active = items.filter(isActive);
                const total = active.reduce(sumPrices, 0);
                const label = formatMoney(total);
                return { label, total };
            }
            """;

    // ---- shared: templates and synced copies ------------------------------------------------

    @Test
    void recognisesTemplateFoldersAndFiles() {
        for (String path : List.of("packages/create-llama/templates/components/x/README-template.md",
                "core/templates/tree/react_express/package.json", "preview-kits/baseui/package.json",
                "docs/config.template.json", "scaffold/app/main.py", "{{cookiecutter.slug}}/app.py")) {
            assertTrue(CodeOrigin.isTemplate(path), path);
        }
        for (String path : List.of("src/app/page.tsx", "README.md", "src/templating/engine.py", "docs/guide.md")) {
            assertFalse(CodeOrigin.isTemplate(path), path);
        }
    }

    @Test
    void identicalFileCopiesAreOneLeadNotManyCopiedFunctions() throws Exception {
        StringBuilder core = new StringBuilder();
        for (int i = 0; i < 8; i++) core.append("export async function step").append(i).append("(account) {\n").append(BODY);
        List<Finding> findings = pattern(new TestRepo(dir)
                .file("integrations/core/memory.js", core.toString())
                .file("integrations/plugin-a/core/memory.js", core.toString())
                .file("integrations/plugin-b/core/memory.js", core.toString()));

        assertTrue(findings.stream().noneMatch(f -> f.category().equals("copied-code")), titles(findings).toString());
        Finding file = findings.stream().filter(f -> f.category().equals("copied-file")).findFirst().orElseThrow();
        assertEquals("Same file kept in 3 identical copies: memory.js", file.title());
        assertEquals(DecidedBy.UNVERIFIED, file.decidedBy(), "usually generated, so a lead, not a likely issue");
    }

    @Test
    void twoHandWrittenCopiesAreStillCaught() throws Exception {
        List<Finding> findings = pattern(new TestRepo(dir)
                .file("src/cart.js", "export async function cartTotal(account) {\n" + BODY)
                .file("src/checkout.js", "export async function checkoutSum(account) {\n" + BODY)
                .file("templates/app/cart.js", "export async function templateTotal(account) {\n" + BODY));

        Finding copy = findings.stream().filter(f -> f.category().equals("copied-code")).findFirst().orElseThrow();
        assertTrue(copy.title().contains("2 times"), "the template copy isn't counted: " + copy.title());
    }

    @Test
    void databaseLayersAndPrintVersusLoggingAreLeads() throws Exception {
        List<Finding> findings = pattern(new TestRepo(dir)
                .file("app/store.py", "import sqlalchemy\nimport sqlite3\n")
                .file("app/log.py", "import logging\nlogging.info('x')\n")
                .file("app/cli.py", "print('a')\n")
                .file("app/cli2.py", "print('b')\n")
                .file("app/http.py", "import requests\nimport httpx\n"));

        for (Finding f : findings) {
            boolean http = f.title().contains("HTTP clients");
            assertEquals(http ? DecidedBy.STATIC : DecidedBy.UNVERIFIED, f.decidedBy(), f.title());
        }
        assertEquals(3, findings.size(), titles(findings).toString());
    }

    // ---- Dead-Dep ------------------------------------------------------------------------------

    @Test
    void templatesPlatformBinariesConventionsAndWorkspaceToolsAreNotUnused() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("package.json", """
                        {"name": "root", "private": true, "devDependencies": {"@biomejs/biome": "1", "vitest": "2",
                         "@vitest/coverage-v8": "2", "@bufbuild/buf": "1", "protoc-gen-ts": "0",
                         "lightningcss-linux-x64-gnu": "1", "@swc/core-darwin-arm64": "1", "left-pad": "1"},
                         "scripts": {"proto": "buf generate"}}
                        """)
                .file("packages/web/package.json", """
                        {"name": "web", "scripts": {"lint": "biome check .", "test": "vitest run --coverage"}}
                        """)
                .file("packages/web/index.js", "export const x = 1;\n")
                .file("core/templates/tree/app/package.json", """
                        {"name": "tpl", "dependencies": {"chart.js": "4", "moment": "2"}}
                        """)
                .audit(null);

        assertEquals(List.of("left-pad"), TestRepo.flagged(report));
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("template folders")), report.warnings().toString());
    }

    @Test
    void freezeAndHashPinnedRequirementsAreSkipped() throws Exception {
        StringBuilder freeze = new StringBuilder();
        for (int i = 0; i < 30; i++) freeze.append("transitive-pkg-").append(i).append("==1.0.").append(i).append('\n');
        AuditAgent.Report report = new TestRepo(dir)
                .file("app.py", "import requests\n")
                .file("requirements.txt", "requests==2.32.0\n")
                .file("requirements-milvus.txt", freeze.toString())
                .file("checks/requirements-lock.txt", "python-dateutil==2.9.0 \\\n    --hash=sha256:abc\n")
                .audit(null);

        assertTrue(TestRepo.flagged(report).isEmpty(), TestRepo.flagged(report).toString());
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("pip freeze")), report.warnings().toString());
    }

    // ---- Spec-Drift ------------------------------------------------------------------------------

    private static String shell(String... lines) {
        return "```bash\n" + String.join("\n", lines) + "\n```\n";
    }

    @Test
    void templateDocsSiteLinksBuildOutputsAndSaveAsFilesAreNotBroken() throws Exception {
        List<Finding> findings = spec(new TestRepo(dir)
                .file("templates/rag/README-template.md", "Edit [the workflow](./app/workflow.py).\n")
                .file("website/docs/tips.md", "Use the [/ask command](modes.html).\n")
                .file("website/docs/modes.md", "# Modes\n")
                .file("CONTRIBUTING.md", "Build first, then run:\n" + shell("npm run build", "node ./dist/cli.mjs"))
                .file("docs/agent.md", "Save this as **gemini_agent.py**:\n\n```python\nprint('hi')\n```\n\nThen:\n"
                        + shell("python gemini_agent.py")
                        + "```bash\n# agent_memory.sh -- helper\necho hi\n```\n" + shell("./agent_memory.sh alice"))
                .file("package.json", "{\"scripts\": {\"build\": \"tsc\"}}\n"));

        assertTrue(findings.isEmpty(), titles(findings).toString());
    }

    @Test
    void bunRunWithAFilePathIsAFileNotAScript() throws Exception {
        List<Finding> findings = spec(new TestRepo(dir)
                .file("package.json", "{\"scripts\": {}}\n")
                .file("examples/real.ts", "export {};\n")
                .file("README.md", shell("bun run examples/real.ts", "bun run examples/gone.ts")));

        assertEquals(List.of("Documented command runs a missing file: examples/gone.ts"), titles(findings));
    }
}
