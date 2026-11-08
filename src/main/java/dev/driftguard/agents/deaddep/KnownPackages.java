package dev.driftguard.agents.deaddep;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** What you can't work out from imports alone: odd import names, CLI tools, packages used indirectly. */
final class KnownPackages {

    private static final Map<String, List<String>> PYTHON_IMPORT_NAMES = loadImportNames();

    /** Python packages you run or configure rather than import. */
    private static final Set<String> PYTHON_TOOLS = Set.of(
            "pytest", "tox", "nox", "coverage", "black", "ruff", "flake8", "pylint", "mypy", "pyright", "isort",
            "autopep8", "yapf", "bandit", "pre-commit", "sphinx", "mkdocs", "mkdocs-material", "twine", "build",
            "wheel", "setuptools", "pip", "pip-tools", "poetry", "hatch", "hatchling", "pdm", "uv", "bump2version",
            "commitizen", "ipykernel", "ipython", "jupyter", "jupyterlab", "notebook", "ipywidgets", "gunicorn",
            "uvicorn", "hypercorn", "daphne", "waitress", "supervisor", "alembic", "locust", "honcho", "invoke");
    private static final Pattern PYTHON_TOOL_FAMILY = Pattern.compile(
            "^(pytest-|flake8-|pylint-|sphinx-|sphinxcontrib-|mkdocs-|mkdocstrings|pre-commit-|nbconvert|nbformat).*");

    /** Python packages other libraries load for you, so a missing import proves nothing. */
    private static final Set<String> PYTHON_INDIRECT = Set.of(
            "psycopg2", "psycopg2-binary", "psycopg", "psycopg-binary", "pymysql", "mysqlclient",
            "mysql-connector-python", "asyncpg", "aiomysql", "aiosqlite", "cx-oracle", "oracledb", "pyodbc",
            "lxml", "html5lib", "openpyxl", "xlrd", "xlsxwriter", "pyarrow", "fastparquet", "tabulate", "python-multipart",
            "email-validator", "passlib", "bcrypt", "argon2-cffi", "cryptography", "itsdangerous", "aiofiles", "jinja2",
            "httpx", "sentencepiece", "protobuf", "accelerate", "safetensors", "tiktoken", "einops", "torchvision",
            "torchaudio", "redis", "kombu", "eventlet", "gevent", "uvloop", "httptools", "websockets", "wsproto",
            "tzdata", "pytz", "greenlet", "certifi", "urllib3", "charset-normalizer", "idna", "typing-extensions",
            "pydantic-settings", "boto3", "s3fs", "gcsfs", "fsspec", "pymupdf", "pdfminer-six", "sqlalchemy-utils");

    /** JS tools whose command name differs from the package name. */
    private static final Map<String, List<String>> JS_BINARIES = Map.ofEntries(
            Map.entry("typescript", List.of("tsc", "tsserver")),
            Map.entry("@babel/cli", List.of("babel")),
            Map.entry("webpack-cli", List.of("webpack")),
            Map.entry("webpack-dev-server", List.of("webpack")),
            Map.entry("@vue/cli-service", List.of("vue-cli-service")),
            Map.entry("@angular/cli", List.of("ng")),
            Map.entry("@nestjs/cli", List.of("nest")),
            Map.entry("@playwright/test", List.of("playwright")),
            Map.entry("@biomejs/biome", List.of("biome")),
            Map.entry("@swc/cli", List.of("swc")),
            Map.entry("npm-run-all", List.of("run-s", "run-p", "npm-run-all")),
            Map.entry("npm-run-all2", List.of("run-s", "run-p", "npm-run-all")),
            Map.entry("@commitlint/cli", List.of("commitlint")),
            Map.entry("@changesets/cli", List.of("changeset")),
            Map.entry("firebase-tools", List.of("firebase")),
            Map.entry("@sveltejs/kit", List.of("svelte-kit")),
            Map.entry("electron-builder", List.of("electron-builder")),
            Map.entry("dotenv-cli", List.of("dotenv")),
            Map.entry("c8", List.of("c8")),
            Map.entry("nyc", List.of("nyc")));

    /** JS packages a build tool picks up because certain files exist, with no import needed. */
    private static final Map<String, Pattern> JS_IMPLIED_BY_FILES = Map.ofEntries(
            Map.entry("react", Pattern.compile(".*\\.(jsx|tsx)$")),
            Map.entry("vue", Pattern.compile(".*\\.vue$")),
            Map.entry("svelte", Pattern.compile(".*\\.svelte$")),
            Map.entry("astro", Pattern.compile(".*\\.astro$")),
            Map.entry("typescript", Pattern.compile(".*(\\.(ts|tsx|mts|cts)|(^|/)tsconfig[^/]*\\.json)$")),
            Map.entry("sass", Pattern.compile(".*\\.(scss|sass)$")),
            Map.entry("node-sass", Pattern.compile(".*\\.(scss|sass)$")),
            Map.entry("less", Pattern.compile(".*\\.less$")),
            Map.entry("tailwindcss", Pattern.compile("(^|.*/)tailwind\\.config\\.[a-z]+$")),
            Map.entry("postcss", Pattern.compile("(^|.*/)(postcss\\.config\\.[a-z]+|\\.postcssrc.*)$")),
            Map.entry("eslint", Pattern.compile("(^|.*/)(eslint\\.config\\.[a-z]+|\\.eslintrc.*)$")),
            Map.entry("prettier", Pattern.compile("(^|.*/)(prettier\\.config\\.[a-z]+|\\.prettierrc.*)$")),
            Map.entry("vite", Pattern.compile("(^|.*/)vite\\.config\\.[a-z]+$")),
            Map.entry("vitest", Pattern.compile("(^|.*/)vitest\\.config\\.[a-z]+$")),
            Map.entry("jest", Pattern.compile("(^|.*/)jest\\.config\\.[a-z]+$")),
            Map.entry("webpack", Pattern.compile("(^|.*/)webpack[^/]*\\.config\\.[a-z]+$")),
            Map.entry("rollup", Pattern.compile("(^|.*/)rollup\\.config\\.[a-z]+$")),
            Map.entry("next", Pattern.compile("(^|.*/)next\\.config\\.[a-z]+$")),
            Map.entry("nuxt", Pattern.compile("(^|.*/)nuxt\\.config\\.[a-z]+$")),
            Map.entry("@babel/core", Pattern.compile("(^|.*/)(babel\\.config\\.[a-z]+|\\.babelrc.*)$")));

    /** A JS package that the named package needs alongside it without any import of its own. */
    private static final Map<String, List<String>> JS_REQUIRED_BY = Map.of(
            "react-dom", List.of("next", "gatsby", "react-scripts", "@remix-run/react", "react-email"),
            "react", List.of("next", "gatsby", "react-scripts", "react-dom", "react-native", "expo"),
            "react-native", List.of("expo"));

    private static final Pattern JS_TOOL_FAMILY = Pattern.compile(
            "^(eslint-|@eslint/|@typescript-eslint/|prettier-|@trivago/prettier|babel-|@babel/|postcss-|stylelint"
                    + "|tailwindcss-|@tailwindcss/|autoprefixer|.*-loader$|webpack-|rollup-|@rollup/|vite-|@vitejs/"
                    + "|jest-|ts-jest|@testing-library/|@storybook/|storybook|husky|lint-staged|@commitlint/"
                    + "|@types/|@tsconfig/|@next/|@nuxt/|@nuxtjs/|@sveltejs/|cypress|playwright|@playwright/).*");

    private static final Set<String> JS_TOOLS = Set.of(
            "eslint", "prettier", "typescript", "ts-node", "tsx", "nodemon", "concurrently", "cross-env", "rimraf",
            "vite", "vitest", "jest", "mocha", "chai", "webpack", "rollup", "esbuild", "parcel", "turbo", "nx",
            "lerna", "sass", "less", "tailwindcss", "postcss", "autoprefixer", "pm2", "serve", "http-server",
            "shx", "tsup", "dotenv-cli", "prisma", "drizzle-kit", "electron", "wait-on", "npm-check-updates");

    /** JS packages other libraries load for you: optional backends and database drivers. */
    private static final Set<String> JS_INDIRECT = Set.of(
            "sharp", "pg", "pg-hstore", "mysql", "mysql2", "sqlite3", "better-sqlite3", "tedious", "oracledb",
            "bufferutil", "utf-8-validate", "encoding", "canvas", "jsdom", "happy-dom", "@swc/core");

    private static final Pattern SCRIPT_COMMAND = Pattern.compile(
            "(?:^|&&|\\|\\||;|\\||\\bnpx\\s+|\\bpnpm\\s+exec\\s+|\\bbunx\\s+|\\byarn\\s+|\\bcross-env\\s+(?:\\S+=\\S+\\s+)*)\\s*([a-z][a-z0-9_-]*)",
            Pattern.MULTILINE);
    private static final Set<String> SHELL_COMMANDS = Set.of(
            "npm", "npx", "yarn", "pnpm", "bun", "bunx", "node", "cd", "rm", "cp", "mv", "mkdir", "echo", "cat", "exit",
            "true", "false", "test", "sh", "bash", "python", "python3", "pip", "git", "docker", "curl", "sleep", "set",
            "export", "run", "exec", "install", "add", "build", "dev", "start", "lint", "tsc", "cross-env");

    private KnownPackages() {}

    static boolean isJsIndirect(String packageName) {
        return JS_INDIRECT.contains(packageName);
    }

    /** Commands in package.json scripts that no declared package obviously provides. */
    static Set<String> unexplainedScriptCommands(String scripts, Set<String> declaredPackages) {
        Set<String> provided = new LinkedHashSet<>(SHELL_COMMANDS);
        for (String declared : declaredPackages) provided.addAll(jsCommandNames(declared));
        Set<String> commands = new LinkedHashSet<>();
        if (scripts == null) return commands;
        var matcher = SCRIPT_COMMAND.matcher(scripts);
        while (matcher.find()) {
            if (!provided.contains(matcher.group(1))) commands.add(matcher.group(1));
        }
        return commands;
    }

    /** True if the command could come from this package: react-email can provide `email`. */
    static boolean couldProvideCommand(String packageName, String command) {
        for (String segment : packageName.split("[-/@]")) {
            if (segment.equals(command)) return true;
        }
        return false;
    }

    private static Map<String, List<String>> loadImportNames() {
        Map<String, List<String>> names = new HashMap<>();
        try (InputStream in = KnownPackages.class.getResourceAsStream("/knowledge/python-import-names.txt");
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int equals = line.indexOf('=');
                names.put(line.substring(0, equals), List.of(line.substring(equals + 1).split(",")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names;
    }

    static String normalizePython(String name) {
        return name.toLowerCase(Locale.ROOT).replace('_', '-').replace('.', '-');
    }

    /** Lowercase with separators removed, so foo-lib matches foolib. */
    static String collapse(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[-_.]", "");
    }

    static boolean hasCuratedImportName(String distribution) {
        return PYTHON_IMPORT_NAMES.containsKey(normalizePython(distribution));
    }

    /** Every module name this PyPI package might be imported as. */
    static Set<String> pythonImportCandidates(String distribution) {
        String normalized = normalizePython(distribution);
        Set<String> candidates = new LinkedHashSet<>(PYTHON_IMPORT_NAMES.getOrDefault(normalized, List.of()));
        List<String> forms = new ArrayList<>(List.of(normalized));
        if (normalized.startsWith("python-")) forms.add(normalized.substring(7));
        if (normalized.startsWith("py-")) forms.add(normalized.substring(3));
        if (normalized.endsWith("-python")) forms.add(normalized.substring(0, normalized.length() - 7));
        for (String form : forms) {
            candidates.add(form.replace('-', '_'));
            candidates.add(form.replace('-', '.')); // namespace packages: zope.interface
        }
        candidates.add(distribution);
        return candidates;
    }

    /** For types-requests or requests-stubs, returns requests. Otherwise null. */
    static String pythonStubTarget(String distribution) {
        String normalized = normalizePython(distribution);
        if (normalized.startsWith("types-")) return normalized.substring(6);
        if (normalized.endsWith("-stubs")) return normalized.substring(0, normalized.length() - 6);
        return null;
    }

    static boolean isPythonTool(String distribution) {
        String normalized = normalizePython(distribution);
        return PYTHON_TOOLS.contains(normalized) || PYTHON_TOOL_FAMILY.matcher(normalized).matches();
    }

    static boolean isPythonIndirect(String distribution) {
        return PYTHON_INDIRECT.contains(normalizePython(distribution));
    }

    static List<String> jsCommandNames(String packageName) {
        List<String> names = new ArrayList<>(JS_BINARIES.getOrDefault(packageName, List.of()));
        names.add(packageName);
        if (packageName.startsWith("@")) names.add(packageName.substring(packageName.indexOf('/') + 1));
        return names;
    }

    static Pattern jsImpliedByFiles(String packageName) {
        return JS_IMPLIED_BY_FILES.get(packageName);
    }

    static List<String> jsRequiredBy(String packageName) {
        return JS_REQUIRED_BY.getOrDefault(packageName, List.of());
    }

    static boolean isJsTool(String packageName) {
        return JS_TOOLS.contains(packageName) || JS_TOOL_FAMILY.matcher(packageName).matches();
    }

    /** Maps @types/foo to foo and @types/babel__core to @babel/core. Null if not an @types package. */
    static String jsTypesTarget(String packageName) {
        if (!packageName.startsWith("@types/")) return null;
        String target = packageName.substring(7);
        int scopeSeparator = target.indexOf("__");
        return scopeSeparator < 0 ? target
                : "@" + target.substring(0, scopeSeparator) + "/" + target.substring(scopeSeparator + 2);
    }
}
