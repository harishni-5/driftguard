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

class DeadDepPythonTest {

    @TempDir
    Path dir;

    @Test
    void recognisesEveryKindOfRealImport() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements.txt", """
                        requests==2.31.0
                        beautifulsoup4>=4.0  # different import name: bs4
                        scikit-learn
                        google-cloud-storage
                        python-dotenv
                        ujson
                        numpy
                        matplotlib
                        Flask[async]>=2
                        foo-lib
                        """)
                .file("app/main.py", """
                        import requests
                        from bs4 import BeautifulSoup
                        from sklearn.linear_model import LinearRegression
                        from google.cloud import storage
                        from dotenv import load_dotenv
                        import importlib
                        import flask
                        import foolib
                        try:
                            import ujson as json
                        except ImportError:
                            import json
                        np = importlib.import_module("numpy")
                        """)
                .file("analysis.ipynb", """
                        {"cells": [{"cell_type": "code", "source": ["!pip install matplotlib\\n", "import matplotlib.pyplot as plt\\n"]}]}
                        """)
                .audit(null);

        assertEquals(List.of(), TestRepo.flagged(report));
        assertEquals(10, report.stats().get("used"));
    }

    @Test
    void flagsPackageNothingUsesWithHighConfidence() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements.txt", "requests\npandas==2.0\n")
                .file("main.py", "import requests\n")
                .audit(null);

        Finding pandas = TestRepo.findingFor(report, "pandas").orElseThrow();
        assertEquals(Confidence.HIGH, pandas.confidence());
        assertEquals(DecidedBy.STATIC, pandas.decidedBy());
        assertEquals(Finding.Severity.MEDIUM, pandas.severity());
        assertEquals("requirements.txt", pandas.filePath());
        assertEquals(2, pandas.startLine());
        assertFalse(TestRepo.findingFor(report, "requests").isPresent());
    }

    @Test
    void indirectPackagesAreLeadsNotFacts() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements.txt", "sqlalchemy\npsycopg2-binary\ncelery\nsome-plugin\n")
                .file("Procfile", "worker: celery -A app worker\n")
                .file("db.py", """
                        import sqlalchemy
                        PLUGINS = ["some_plugin"]
                        """)
                .audit(null);

        for (String name : List.of("psycopg2-binary", "celery", "some-plugin")) {
            Finding finding = TestRepo.findingFor(report, name).orElseThrow(() -> new AssertionError(name));
            assertEquals(DecidedBy.UNVERIFIED, finding.decidedBy(), name);
            assertEquals(Confidence.LOW, finding.confidence(), name);
        }
        assertFalse(TestRepo.findingFor(report, "celery").orElseThrow().evidence().isEmpty());
    }

    @Test
    void devToolsCountAsUsedWhenConfigured() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements-dev.txt", "pytest\nblack\ntypes-requests\n")
                .file("requirements.txt", "requests\n")
                .file("pyproject.toml", """
                        [build-system]
                        requires = ["setuptools", "wheel"]
                        [tool.pytest.ini_options]
                        addopts = "-q"
                        """)
                .file("main.py", "import requests\n")
                .audit(null);

        assertFalse(TestRepo.findingFor(report, "pytest").isPresent());
        assertFalse(TestRepo.findingFor(report, "types-requests").isPresent());
        assertFalse(TestRepo.findingFor(report, "setuptools").isPresent());
        Finding black = TestRepo.findingFor(report, "black").orElseThrow();
        assertEquals(DecidedBy.UNVERIFIED, black.decidedBy());
        assertEquals(Finding.Severity.LOW, black.severity());
    }

    @Test
    void unknownImportNameLowersConfidenceInsteadOfFalselyAccusing() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements.txt", "weird-dist\n")
                .file("main.py", "import totallydifferent\n")
                .audit(null);

        Finding finding = TestRepo.findingFor(report, "weird-dist").orElseThrow();
        assertEquals(Confidence.MEDIUM, finding.confidence());
        assertEquals(DecidedBy.UNVERIFIED, finding.decidedBy());
    }

    @Test
    void localModulesAreNotMistakenForUnknownPackages() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements.txt", "pandas\n")
                .file("main.py", "import helpers\nfrom mypkg.sub import thing\n")
                .file("helpers.py", "x = 1\n")
                .file("src/mypkg/sub.py", "thing = 1\n")
                .audit(null);

        assertEquals(Confidence.HIGH, TestRepo.findingFor(report, "pandas").orElseThrow().confidence());
    }

    @Test
    void unparseableFileLowersConfidence() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements.txt", "pandas\n")
                .file("broken.py", "def oops(:\n  pass\n")
                .audit(null);

        assertEquals(Confidence.MEDIUM, TestRepo.findingFor(report, "pandas").orElseThrow().confidence());
    }

    @Test
    void importsInPython2OrBrokenFilesStillCount() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements.txt", "pandas\nnumpy\nrequests\n")
                .file("old.py", """
                        import pandas, numpy
                        from requests import get
                        print "python 2 syntax"
                        """)
                .audit(null);

        assertEquals(List.of(), TestRepo.flagged(report));
    }

    @Test
    void pipFreezeOutputIsNotReportedAsHundredsOfFacts() throws Exception {
        StringBuilder freeze = new StringBuilder();
        for (int i = 0; i < 30; i++) freeze.append("pkg").append(i).append("==1.0.").append(i).append('\n');
        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements.txt", freeze.toString())
                .file("main.py", "import pkg0\n")
                .audit(null);

        assertEquals(29, report.findings().size());
        assertTrue(report.findings().stream().allMatch(f -> f.decidedBy() == DecidedBy.UNVERIFIED));
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("pip freeze")));
    }

    @Test
    void readsEveryManifestFormat() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("pyproject.toml", """
                        [project]
                        dependencies = ["pep621-unused>=1"]
                        [project.optional-dependencies]
                        extra = ["optional-unused"]
                        [dependency-groups]
                        dev = ["group-unused"]
                        [tool.poetry.dependencies]
                        python = "^3.10"
                        poetry-unused = "^1.0"
                        """)
                .file("Pipfile", "[packages]\npipfile-unused = \"*\"\n")
                .file("setup.py", """
                        from setuptools import setup
                        setup(name="x", install_requires=["setuppy-unused>=1", "setuptools"])
                        """)
                .file("setup.cfg", "[options]\ninstall_requires =\n    setupcfg-unused\n")
                .file("requirements/base.txt", "-r other.txt\ngit+https://github.com/a/b.git#egg=vcs-unused\n")
                .audit(null);

        List<String> flagged = TestRepo.flagged(report);
        for (String expected : List.of("pep621-unused", "optional-unused", "group-unused", "poetry-unused",
                "pipfile-unused", "setuppy-unused", "setupcfg-unused", "vcs-unused")) {
            assertTrue(flagged.contains(expected), expected + " missing from " + flagged);
        }
        assertFalse(flagged.contains("python"));
    }

    // The next four came from wrong calls on real repos (flask, devika, gpt-engineer).

    @Test
    void generatedLockfilesAreSkipped() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements.txt", "#\n# This file is autogenerated by pip-compile with Python 3.11\n#\namqp==5.3.1\n    # via kombu\n")
                .file("main.py", "x = 1\n")
                .audit(null);

        assertEquals(List.of(), TestRepo.flagged(report));
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("generated lockfile")));
    }

    @Test
    void nestedProjectIsJudgedOnItsOwnCode() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("pyproject.toml", "[project]\ndependencies = [\"requests\"]\n")
                .file("app.py", "import requests\nimport celery\n")
                .file("examples/demo/pyproject.toml", "[project]\ndependencies = [\"celery\"]\n")
                .file("examples/demo/run.py", "print(1)\n")
                .audit(null);

        Finding celery = TestRepo.findingFor(report, "celery").orElseThrow();
        assertEquals("examples/demo/pyproject.toml", celery.filePath());
    }

    @Test
    void distributionNamedAfterItsImportWithASuffixCountsAsUsed() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements.txt", "pdfminer.six\nmystery-extras\n")
                .file("main.py", "from pdfminer.high_level import extract_text\nimport mystery\n")
                .audit(null);

        assertEquals(List.of(), TestRepo.flagged(report));
    }

    @Test
    void sphinxExtensionsListedInConfPyCountAsUsed() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("requirements-docs.txt", "sphinx-copybutton\nsphinx-rtd-theme\n")
                .file("docs/conf.py", "extensions = [\"sphinx_copybutton\"]\nhtml_theme = \"sphinx_rtd_theme\"\n")
                .audit(null);

        assertEquals(List.of(), TestRepo.flagged(report));
    }

    @Test
    void brokenManifestIsAWarningNotACrash() throws Exception {
        AuditAgent.Report report = new TestRepo(dir)
                .file("pyproject.toml", "this is = = not toml [[[\n")
                .file("requirements.txt", "pandas\n")
                .audit(null);

        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("pyproject.toml")));
        assertTrue(TestRepo.findingFor(report, "pandas").isPresent());
    }
}
