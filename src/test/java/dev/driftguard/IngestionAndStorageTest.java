package dev.driftguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.driftguard.agents.deaddep.DeadDepAgent;
import dev.driftguard.ingest.ParsedFile;
import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.RepoSource;
import dev.driftguard.ingest.SourceFile;
import dev.driftguard.report.MarkdownReport;
import dev.driftguard.store.Database;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IngestionAndStorageTest {

    @TempDir
    Path dir;

    private static ParsedFile parsed(RepoSnapshot snapshot, String path) {
        return snapshot.parsedFiles().stream().filter(f -> f.path().equals(path)).findFirst().orElseThrow();
    }

    /** browser-use has a test string like this, and it used to crash the whole audit. */
    @Test
    void loneSurrogateInAPythonStringDoesNotCrashTheParser() throws Exception {
        RepoSnapshot snapshot = new TestRepo(dir)
                .file("weird.py", "import os\nBAD = \"caf\\udce9\"\n")
                .file("ok.py", "import json\n")
                .snapshot();

        assertEquals("os", parsed(snapshot, "weird.py").imports().get(0).module());
        assertEquals("json", parsed(snapshot, "ok.py").imports().get(0).module());
    }

    @Test
    void pythonHelperExtractsSymbolsAndCalls() throws Exception {
        RepoSnapshot snapshot = new TestRepo(dir).file("mod.py", """
                from .local import thing
                import os

                class Client:
                    def send(self):
                        helper()

                    def _private(self):
                        pass

                def helper():
                    def inner():
                        pass
                    os.getcwd()

                def _hidden():
                    pass
                """).snapshot();

        ParsedFile file = parsed(snapshot, "mod.py");
        assertEquals("python", file.language());
        assertFalse(file.imports().get(0).external(), "relative import");
        assertFalse(file.imports().get(1).external(), "standard library");
        assertEquals(List.of("Client", "Client.send", "Client._private", "helper", "helper.inner", "_hidden"),
                file.symbols().stream().map(ParsedFile.SymbolDef::qualifiedName).toList());
        assertEquals(List.of("Client", "Client.send", "helper"),
                file.symbols().stream().filter(ParsedFile.SymbolDef::exported).map(ParsedFile.SymbolDef::qualifiedName).toList());
        assertTrue(file.calls().stream().anyMatch(c -> "Client.send".equals(c.caller()) && c.callee().equals("helper")));
        assertTrue(file.calls().stream().anyMatch(c -> "helper".equals(c.caller()) && c.callee().equals("os.getcwd")));
    }

    @Test
    void pythonDunderAllDecidesWhatIsExported() throws Exception {
        RepoSnapshot snapshot = new TestRepo(dir).file("mod.py", """
                __all__ = ["public"]
                def public(): pass
                def not_listed(): pass
                """).snapshot();

        assertEquals(List.of("public"), parsed(snapshot, "mod.py").symbols().stream()
                .filter(ParsedFile.SymbolDef::exported).map(ParsedFile.SymbolDef::name).toList());
    }

    @Test
    void jsHelperExtractsSymbolsCallsAndJsxUsage() throws Exception {
        RepoSnapshot snapshot = new TestRepo(dir).file("a.tsx", """
                import React from 'react';
                import { local } from './local';
                import fs from 'node:fs';
                export function Page() { return <Widget />; }
                export const arrow = () => helper();
                function helper() { return api.get(); }
                class Service { run() { helper(); } }
                export default Service;
                """).snapshot();

        ParsedFile file = parsed(snapshot, "a.tsx");
        assertEquals(List.of(true, false, false), file.imports().stream().map(ParsedFile.ImportRef::external).toList());
        assertEquals(List.of("Page", "arrow", "Service"), file.symbols().stream()
                .filter(ParsedFile.SymbolDef::exported).map(ParsedFile.SymbolDef::name).toList());
        assertTrue(file.calls().stream().anyMatch(c -> "Page".equals(c.caller()) && c.callee().equals("Widget")));
        assertTrue(file.calls().stream().anyMatch(c -> "arrow".equals(c.caller()) && c.callee().equals("helper")));
        assertTrue(file.calls().stream().anyMatch(c -> "helper".equals(c.caller()) && c.callee().equals("api.get")));
    }

    @Test
    void vueScriptLineNumbersMatchTheOriginalFile() throws Exception {
        RepoSnapshot snapshot = new TestRepo(dir).file("A.vue", """
                <template>
                  <div/>
                </template>
                <script>
                import x from 'pkg';
                </script>
                """).snapshot();

        assertEquals(5, parsed(snapshot, "A.vue").imports().get(0).line());
    }

    @Test
    void skipsDependenciesFoldersVirtualEnvsAndSecrets() throws Exception {
        TestRepo repo = new TestRepo(dir)
                .file("app.py", "x = 1\n")
                .file("node_modules/lib/index.js", "x\n")
                .file("myenv/pyvenv.cfg", "home = /x\n")
                .file("myenv/lib/thing.py", "x = 1\n")
                .file("dist/bundle.js", "x\n")
                .file("static/app.min.js", "x\n")
                .file(".env", "ANTHROPIC_API_KEY=secret\n")
                .file(".env.production", "TOKEN=secret\n")
                .file("certs/server.pem", "secret\n")
                .file("package-lock.json", "{}\n");

        assertEquals(List.of("app.py"), repo.snapshot().files().stream().map(SourceFile::path).toList());
    }

    @Test
    void doesNotFollowSymlinksOutOfTheRepo(@TempDir Path outside) throws Exception {
        Files.writeString(outside.resolve("private.py"), "import stolen\n");
        TestRepo repo = new TestRepo(dir).file("app.py", "x = 1\n");
        Files.createSymbolicLink(dir.resolve("link.py"), outside.resolve("private.py"));
        Files.createSymbolicLink(dir.resolve("linkdir"), outside);

        assertEquals(List.of("app.py"), repo.snapshot().files().stream().map(SourceFile::path).toList());
    }

    @Test
    void readsUtf16RequirementsFromWindowsPowershell() throws Exception {
        new TestRepo(dir).file("main.py", "x = 1\n");
        Files.write(dir.resolve("requirements.txt"), "﻿pandas==2.0\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_16LE));

        assertEquals(List.of("pandas"), new TestRepo(dir).snapshot().dependencies().stream().map(d -> d.name()).toList());
    }

    @Test
    void rejectsUnsafeRepoUrls() {
        for (String bad : List.of("ext::sh -c whoami", "file:///etc/passwd", "http://example.com/a/b",
                "https://user:pass@example.com/a/b", "ssh://git@example.com/a/b", "https://example.com/a b")) {
            assertThrows(IOException.class, () -> RepoSource.open(bad), bad);
        }
    }

    @Test
    void savesFactsAndReusesIdenticalSnapshots() throws Exception {
        TestRepo repo = new TestRepo(dir)
                .file("requirements.txt", "pandas\n")
                .file("main.py", "import os\ndef main():\n    os.getcwd()\n");

        try (Database database = Database.inMemory()) {
            RepoSnapshot snapshot = repo.snapshot();
            long first = database.saveSnapshot(snapshot);
            assertEquals(first, database.saveSnapshot(repo.snapshot()));
            assertEquals(1, database.count("repos"));
            assertEquals(2, database.count("files"));
            assertEquals(1, database.count("symbols"));
            assertEquals(1, database.count("imports"));
            assertEquals(1, database.count("call_edges"));
            assertEquals(1, database.count("dependencies"));

            repo.file("main.py", "import os\n");
            assertNotEquals(first, database.saveSnapshot(repo.snapshot()));
            assertEquals(2, database.count("repos"));
        }
    }

    @Test
    void fullPipelineWritesFindingsTraceAndReport() throws Exception {
        new TestRepo(dir).file("requirements.txt", "requests\npandas\nredis\n").file("main.py", "import requests\n");

        try (Database database = Database.open(dir.resolve("state/test.db"))) {
            AuditPipeline.Result result =
                    AuditPipeline.run(dir.toString(), database, null, List.of(new DeadDepAgent()));

            assertEquals(2, result.findings().size());
            assertEquals("pandas", result.findings().get(0).title().replaceAll(".*: ", ""), "high confidence sorts first");
            assertEquals(2, database.count("findings"));
            assertEquals(1, database.count("agent_traces"));
            assertEquals(1, database.count("audit_runs"));
            assertNotNull(result.snapshot().contentHash());

            String report = MarkdownReport.render(result);
            assertTrue(report.contains("LLM review:** off"));
            assertTrue(report.contains("## Likely issues"));
            assertTrue(report.contains("## Needs your review"));
            assertTrue(report.contains("requirements.txt:2"));
        }
    }

    @Test
    void refusesADatabaseFromAFutureVersion() throws Exception {
        Path file = dir.resolve("future.db");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + file);
                var statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 99");
        }
        assertThrows(java.sql.SQLException.class, () -> Database.open(file));
    }
}
