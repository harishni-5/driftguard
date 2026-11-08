package dev.driftguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.driftguard.agents.AuditAgent;
import dev.driftguard.agents.Finding;
import dev.driftguard.agents.Finding.DecidedBy;
import dev.driftguard.agents.specdrift.SpecDriftAgent;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecDriftTest {

    @TempDir
    Path dir;

    private List<Finding> audit(TestRepo repo) throws Exception {
        return new SpecDriftAgent().run(new AuditAgent.Context(repo.snapshot(), null, trace -> {})).findings();
    }

    private static List<String> titles(List<Finding> findings) {
        return findings.stream().map(Finding::title).toList();
    }

    private static String shell(String... lines) {
        return "```bash\n" + String.join("\n", lines) + "\n```\n";
    }

    // ---- environment variables ------------------------------------------------------

    @Test
    void flagsADocumentedVariableNothingReads() throws Exception {
        List<Finding> findings = audit(new TestRepo(dir)
                .file("README.md", "Set these:\n" + shell("export PAYMENTS_API_KEY=abc", "export DB_URL=postgres://x"))
                .file("app.py", "import os\nurl = os.environ['DB_URL']\n"));

        assertEquals(List.of("Documented variable is never read: PAYMENTS_API_KEY"), titles(findings));
        assertEquals(DecidedBy.UNVERIFIED, findings.get(0).decidedBy(), "a lead, since a separate tool may read it");
        assertEquals("README.md", findings.get(0).filePath());
        assertEquals(3, findings.get(0).startLine());
    }

    @Test
    void readsCountInJsConfigAndPydanticSettings() throws Exception {
        assertTrue(audit(new TestRepo(dir)
                .file("README.md", shell("JWT_SECRET=x", "REDIS_HOST=y", "SERVICE_PORT=1", "APP_LOG_LEVEL=info"))
                .file("server.js", "const s = process.env.JWT_SECRET;\n")
                .file("compose.yml", "environment:\n  - REDIS_HOST\n")
                .file("settings.py", "class Settings(BaseSettings):\n    service_port: int = 80\n")
                .file("config.py", "class Config(BaseSettings):\n    model_config = {'env_prefix': 'APP_'}\n    log_level = 'x'\n"))
                .isEmpty());
    }

    @Test
    void namesTheVariableTheCodeActuallyReads() throws Exception {
        List<Finding> findings = audit(new TestRepo(dir)
                .file("README.md", shell("SUPABASE_URL=https://x.supabase.co"))
                .file("lib/db.ts", "export const url = process.env.NEXT_PUBLIC_SUPABASE_URL;\n"));

        assertEquals("Docs name the wrong variable: SUPABASE_URL (code reads NEXT_PUBLIC_SUPABASE_URL)", findings.get(0).title());
    }

    @Test
    void providerKeysAreFineWhenTheirSdkIsUsed() throws Exception {
        assertTrue(audit(new TestRepo(dir)
                .file("README.md", shell("OPENAI_API_KEY=sk-x", "MISTRAL_API_KEY=y", "NETMIND_API_KEY=z"))
                .file("requirements.txt", "openai\n")
                .file("llm.py", "from langchain_mistralai import ChatMistralAI\nfrom langchain_netmind import Embeddings\n"))
                .isEmpty());
    }

    @Test
    void unknownProviderMentionedInCodeGoesToReview() throws Exception {
        List<Finding> findings = audit(new TestRepo(dir)
                .file("README.md", shell("ACME_API_KEY=x"))
                .file("llm.py", "PROVIDERS = ['openai', 'acme']\n"));

        assertEquals(DecidedBy.UNVERIFIED, findings.get(0).decidedBy());
    }

    @Test
    void ignoresShellVariablesPlaceholdersContainersAndPlatformSettings() throws Exception {
        assertTrue(audit(new TestRepo(dir)
                .file("README.md", "Backups:\n"
                        + shell("BACKUP_DIR=/backup", "mkdir -p $BACKUP_DIR")
                        + shell("docker run -e MYSQL_ROOT_PASSWORD=x -e ALLOW_EMPTY_PASSWORD=yes mysql")
                        + shell("export NODE_ENV=production", "export AWS_REGION=us-east-1", "CMAKE_ARGS=-DX=on pip install y")
                        + "Replace `YOUR_OPENAI_KEY` with your key.\n")
                .file("app.py", "x = 1\n"))
                .isEmpty());
    }

    @Test
    void shellVariablesUsedInALaterStepAndExampleTextAreNotClaims() throws Exception {
        assertTrue(audit(new TestRepo(dir)
                .file("package.json", "{\"scripts\": {}}\n")
                .file("README.md", shell("export IMAGE_GEN=\"$HOME/gen.py\"") + "Then:\n" + shell("python \"$IMAGE_GEN\" run")
                        + "```text\nRepro:\n1. npm run dev\n```\n"
                        + "```\nSCORE_SCHEMA = json.dumps({})\n```\n")
                .file("app.py", "x = 1\n"))
                .isEmpty());
    }

    @Test
    void proposalsAndChangelogsAreNotClaimsAboutTodaysCode() throws Exception {
        assertTrue(audit(new TestRepo(dir)
                .file("docs/proposals/social.md", shell("SOCIAL_MEDIA_PROVIDER=apify"))
                .file("CHANGELOG.md", shell("OLD_SETTING=1"))
                .file("app.py", "x = 1\n"))
                .isEmpty());
    }

    @Test
    void stepsInsideAnotherProjectAreSkipped() throws Exception {
        assertTrue(audit(new TestRepo(dir)
                .file("README.md", "Install the plugin:\n"
                        + shell("git clone https://github.com/acme/other-tool.git")
                        + "Then:\n" + shell("python server.py", "OTHER_SETTING=1")
                        + shell("cd /tmp/bench", "BENCH_MODE=fast bash run.sh"))
                .file("app.py", "x = 1\n"))
                .isEmpty());
    }

    // ---- scripts ------------------------------------------------------------------------

    @Test
    void flagsAScriptThatDoesNotExist() throws Exception {
        List<Finding> findings = audit(new TestRepo(dir)
                .file("package.json", "{\"scripts\": {\"dev\": \"next dev\", \"build\": \"next build\"}, \"devDependencies\": {\"vitest\": \"1\"}}\n")
                .file("README.md", shell("npm run dev", "npm run seed", "pnpm build", "pnpm vitest", "yarn install")));

        assertEquals(List.of("Documented command has no script: npm run seed"), titles(findings));
        assertEquals(Finding.Confidence.HIGH, findings.get(0).confidence());
    }

    // ---- files ----------------------------------------------------------------------------

    @Test
    void flagsBrokenLinksButNotSiteRoutesBadgesOrUrls() throws Exception {
        List<Finding> findings = audit(new TestRepo(dir)
                .file("README.md", """
                        See [setup](docs/setup.md), [config](docs/config.md) and [the API](https://example.com/api.md).
                        [![Tests](../../actions/workflows/test.yml/badge.svg)](../../actions/workflows/test.yml)
                        Read [intro](/docs/intro) and [top](#install).
                        """)
                .file("docs/setup.md", "Back to [readme](../README.md).\n"));

        assertEquals(List.of("Broken link: docs/config.md"), titles(findings));
    }

    @Test
    void followsCdAcrossStepsWhenCheckingCommands() throws Exception {
        List<Finding> findings = audit(new TestRepo(dir)
                .file("backend/main.py", "x = 1\n")
                .file("backend/scripts/prestart.sh", "echo\n")
                .file("README.md", shell("cd backend") + "Then run:\n" + shell("python main.py", "bash scripts/prestart.sh", "python old_main.py")));

        assertEquals(List.of("Documented command runs a missing file: old_main.py"), titles(findings));
    }

    @Test
    void cloningThisRepoThenCdIntoItMeansTheRoot() throws Exception {
        String name = dir.getFileName().toString();
        assertTrue(audit(new TestRepo(dir)
                .file("run.py", "x = 1\n")
                .file("README.md", shell("git clone https://github.com/me/" + name + ".git", "cd " + name, "python run.py")))
                .isEmpty());
    }
}
