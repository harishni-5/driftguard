package dev.driftguard.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.driftguard.AuditPipeline;
import dev.driftguard.agents.deaddep.DeadDepAgent;
import dev.driftguard.agents.patterndrift.PatternDriftAgent;
import dev.driftguard.agents.specdrift.SpecDriftAgent;
import dev.driftguard.store.Database;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlReportTest {

    @TempDir
    Path dir;

    private String report(Path repo) throws Exception {
        try (Database database = Database.open(dir.resolve("db.sqlite"))) {
            AuditPipeline.Result result = AuditPipeline.run(repo.toString(), database, null,
                    List.of(new DeadDepAgent(), new PatternDriftAgent(), new SpecDriftAgent()));
            return HtmlReport.render(result);
        }
    }

    private Path repo(String... pathsAndContents) throws Exception {
        Path root = Files.createDirectories(dir.resolve("repo"));
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            Path file = root.resolve(pathsAndContents[i]);
            Files.createDirectories(file.getParent());
            Files.writeString(file, pathsAndContents[i + 1]);
        }
        return root;
    }

    @Test
    void repoTextIsEscapedSoItCannotRunScripts() throws Exception {
        String html = report(repo(
                "README.md", "See [setup](docs/missing.md) <script>alert('pwned')</script> <img src=x onerror=alert(1)>\n",
                "requirements.txt", "requests\n",
                "app.py", "x = 1\n"));

        assertTrue(html.contains("Broken link: docs/missing.md"), "the finding is there");
        assertFalse(html.contains("<script>alert"), "repo text must not become markup");
        assertFalse(html.contains("<img src=x"), "repo text must not become markup");
        assertTrue(html.contains("&lt;script&gt;alert(&#39;pwned&#39;)&lt;/script&gt;"));
        assertTrue(html.contains("Content-Security-Policy"), "and the page can't load anything from the network anyway");
        assertEquals(1, html.split("<script>", -1).length - 1, "only the report's own filter script");
    }

    @Test
    void groupsLikelyIssuesAndReviewItemsAndShowsStats() throws Exception {
        String html = report(repo(
                "README.md", "```bash\nexport PAYMENTS_API_KEY=x\n```\nSee [setup](docs/missing.md).\n",
                "app.py", "x = 1\n"));

        int likely = html.indexOf("id=\"likely\"");
        int review = html.indexOf("id=\"review\"");
        assertTrue(likely > 0 && review > likely);
        assertTrue(html.indexOf("Broken link: docs/missing.md") < review, "a broken link is a likely issue");
        assertTrue(html.indexOf("Documented variable is never read: PAYMENTS_API_KEY") > review, "an unread variable is a lead");
        assertTrue(html.contains("<caption>Docs vs code</caption>"));
        assertTrue(html.contains("<th scope=\"row\">Paths missing</th>"));
    }

    @Test
    void cleanRepoSaysSo() throws Exception {
        String html = report(repo("app.py", "print('hi')\n"));
        assertTrue(html.contains("None found."));
        assertFalse(html.contains("class=\"filters\""), "no filters when there's nothing to filter");
    }

    @Test
    void linksToTheExactLineOnGithubOnlyWhenTheCommitIsKnown() {
        String sha = "a8989b522fa3672a2ae52f053105e40993b36ca1";
        assertEquals(Optional.of("https://github.com/vercel/ai-chatbot/blob/" + sha + "/"),
                HtmlReport.blobBase("https://github.com/vercel/ai-chatbot.git", sha));
        assertEquals(Optional.empty(), HtmlReport.blobBase("https://github.com/vercel/ai-chatbot", null));
        assertEquals(Optional.empty(), HtmlReport.blobBase("/Users/me/project", sha));
        assertEquals(Optional.empty(), HtmlReport.blobBase("https://gitlab.com/a/b", sha));
    }

    @Test
    void statNamesReadLikeWords() {
        assertEquals("LLM confirmed same job", HtmlReport.humanize("llmConfirmedSameJob"));
        assertEquals("Env vars never read", HtmlReport.humanize("envVarsNeverRead"));
    }
}
