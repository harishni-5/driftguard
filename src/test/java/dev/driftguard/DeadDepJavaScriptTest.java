package dev.driftguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.driftguard.agents.AuditAgent;
import dev.driftguard.agents.Finding;
import dev.driftguard.agents.Finding.Confidence;
import dev.driftguard.agents.Finding.DecidedBy;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeadDepJavaScriptTest {

    @TempDir
    Path dir;

    @Test
    void recognisesEveryKindOfRealUse() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("package.json", """
                        {
                          "name": "app",
                          "scripts": {"build": "tsc && next build", "dev": "nodemon src/server.js"},
                          "dependencies": {
                            "next": "14", "react": "18", "react-dom": "18",
                            "lodash": "4", "@acme/ui": "1", "axios": "1", "express": "4", "zod": "3",
                            "pinia": "2", "dayjs": "1", "events": "3"
                          },
                          "devDependencies": {
                            "typescript": "5", "nodemon": "3", "@types/node": "20", "@types/express": "4",
                            "autoprefixer": "10", "eslint": "9", "@babel/core": "7", "@types/babel__core": "7"
                          }
                        }
                        """)
                .file("src/server.js", """
                        const express = require('express');
                        const events = require('events');
                        async function load() { return await import('axios'); }
                        module.exports = { load };
                        """)
                .file("src/page.tsx", """
                        import fp from 'lodash/fp';
                        import { Button } from '@acme/ui/button';
                        import type { ZodType } from 'zod';
                        export const Page = <T,>() => <Button />;
                        """)
                .file("src/Store.vue", """
                        <template><div/></template>
                        <script setup lang="ts">
                        import { defineStore } from 'pinia';
                        </script>
                        """)
                .file("src/index.ts", "export * from 'dayjs';\n")
                .file("postcss.config.js", "module.exports = { plugins: { autoprefixer: {} } };\n")
                .file(".eslintrc.json", "{}\n")
                .file("babel.config.js", "module.exports = {};\n")
                .audit(null);

        assertEquals(List.of(), TestRepo.flagged(report));
    }

    @Test
    void flagsUnusedPackagesWithSeverityByScope() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("package.json", """
                        {"dependencies": {"express": "4", "moment": "2"}, "devDependencies": {"left-pad": "1"}}
                        """)
                .file("index.js", "require('express');\n")
                .audit(null);

        Finding moment = TestRepo.findingFor(report, "moment").orElseThrow();
        assertEquals(Finding.Severity.MEDIUM, moment.severity());
        assertEquals(Confidence.HIGH, moment.confidence());
        assertEquals(DecidedBy.STATIC, moment.decidedBy());
        assertEquals(1, moment.startLine());
        assertEquals(Finding.Severity.LOW, TestRepo.findingFor(report, "left-pad").orElseThrow().severity());
    }

    @Test
    void toolsConfiguredByShortNameNeedReview() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("package.json", """
                        {"devDependencies": {"eslint": "9", "eslint-plugin-react": "7", "@types/unknown-lib": "1"}}
                        """)
                .file(".eslintrc.json", "{\"plugins\": [\"react\"]}\n")
                .file("index.js", "console.log(1);\n")
                .audit(null);

        assertEquals(DecidedBy.UNVERIFIED, TestRepo.findingFor(report, "eslint-plugin-react").orElseThrow().decidedBy());
        assertEquals(DecidedBy.UNVERIFIED, TestRepo.findingFor(report, "@types/unknown-lib").orElseThrow().decidedBy());
        assertFalse(TestRepo.findingFor(report, "eslint").isPresent());
    }

    @Test
    void monorepoPackagesAreJudgedOnTheirOwnCode() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("package.json", "{\"private\": true, \"workspaces\": [\"packages/*\"], \"dependencies\": {\"chalk\": \"5\"}}\n")
                .file("packages/a/package.json", "{\"dependencies\": {\"chalk\": \"5\"}}\n")
                .file("packages/a/index.js", "console.log('a');\n")
                .file("packages/b/package.json", "{\"dependencies\": {\"chalk\": \"5\"}}\n")
                .file("packages/b/index.js", "import chalk from 'chalk';\n")
                .audit(null);

        List<Finding> chalk = report.findings().stream().filter(f -> f.title().endsWith("chalk")).toList();
        assertEquals(1, chalk.size(), chalk.toString());
        assertEquals("packages/a/package.json", chalk.get(0).filePath());
    }

    @Test
    void depsMentionedOnlyInDependencySectionsDoNotCountAsConfigUse() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("package.json", "{\"dependencies\": {\"moment\": \"2\"}, \"overrides\": {\"moment\": \"2.1\"}}\n")
                .file("package-lock.json", "{\"moment\": \"2\"}\n")
                .audit(null);

        assertTrue(TestRepo.findingFor(report, "moment").isPresent());
    }

    // The next four came from false alarms on real repos (taxonomy, full-stack-fastapi-template, chatbot-ui).

    @Test
    void gitHooksCountAsUse() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("package.json", "{\"devDependencies\": {\"pretty-quick\": \"3\"}}\n")
                .file(".husky/pre-commit", "#!/bin/sh\nnpx pretty-quick --staged\n")
                .audit(null);

        assertEquals(List.of(), TestRepo.flagged(report));
    }

    @Test
    void scriptCommandWithDifferentNameIsALeadNotAFact() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("package.json", """
                        {"scripts": {"dev": "email dev --port 3333", "build": "next build"},
                         "dependencies": {"react-email": "6", "next": "14", "next-themes": "1"}}
                        """)
                .audit(null);

        assertEquals(DecidedBy.UNVERIFIED, TestRepo.findingFor(report, "react-email").orElseThrow().decidedBy());
        // `next` is already explained by the next package, so next-themes doesn't get a free pass.
        assertEquals(DecidedBy.STATIC, TestRepo.findingFor(report, "next-themes").orElseThrow().decidedBy());
    }

    @Test
    void packagesLoadedByFrameworksNeedReview() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("package.json", "{\"dependencies\": {\"next\": \"14\", \"sharp\": \"0.33\"}, \"scripts\": {\"b\": \"next build\"}}\n")
                .audit(null);

        assertEquals(DecidedBy.UNVERIFIED, TestRepo.findingFor(report, "sharp").orElseThrow().decidedBy());
    }

    @Test
    void typesForAnUnusedPackageAreNotCountedAsUsed() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("package.json", "{\"dependencies\": {\"pdf-parse\": \"1\"}, \"devDependencies\": {\"@types/pdf-parse\": \"1\"}}\n")
                .file("index.js", "console.log(1);\n")
                .audit(null);

        assertEquals(DecidedBy.STATIC, TestRepo.findingFor(report, "pdf-parse").orElseThrow().decidedBy());
        assertTrue(TestRepo.findingFor(report, "@types/pdf-parse").isPresent());
    }

    @Test
    void syntaxWeCannotFullyParseStillYieldsImports() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("package.json", "{\"dependencies\": {\"express\": \"4\"}}\n")
                .file("index.js", "import express from 'express';\nconst x = ;\n")
                .audit(null);

        assertEquals(List.of(), TestRepo.flagged(report));
    }
}
