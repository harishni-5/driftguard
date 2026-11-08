package dev.driftguard.manifest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import dev.driftguard.ingest.SourceFile;
import dev.driftguard.manifest.Dependency.Ecosystem;
import dev.driftguard.manifest.Dependency.Scope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads declared dependencies from requirements files, pyproject.toml, Pipfile,
 * setup.py, setup.cfg and package.json. A broken manifest becomes a warning: one
 * bad file should not stop the audit.
 */
public final class ManifestParser {

    /**
     * {@code configText} maps a manifest to its non-dependency content (such as [tool.*]
     * tables), which agents can search for tool usage. A dependency section always
     * mentions every dependency, so it has to be left out of that search.
     * {@code scripts} holds the package.json scripts for each manifest.
     */
    public record Result(
            List<Dependency> dependencies,
            Map<String, String> configText,
            Map<String, String> scripts,
            List<String> warnings) {}

    private static final Pattern REQUIREMENT = Pattern.compile("^([A-Za-z0-9][A-Za-z0-9._-]*)\\s*(\\[[^\\]]*\\])?\\s*(.*)$");
    private static final Pattern EGG = Pattern.compile("[#&]egg=([A-Za-z0-9._-]+)");
    private static final Pattern DEV_NAME = Pattern.compile(
            "(^|[^a-z])(dev|develop|development|test|tests|testing|lint|linting|docs?|ci|typing|qa)([^a-z]|$)");
    private static final Pattern SETUP_PY_REQUIRES = Pattern.compile("install_requires\\s*=\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern QUOTED = Pattern.compile("[\"']([^\"']+)[\"']");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TomlMapper TOML = new TomlMapper();

    private final List<Dependency> dependencies = new ArrayList<>();
    private final Map<String, String> configText = new HashMap<>();
    private final Map<String, String> scripts = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    private ManifestParser() {}

    public static Result parse(List<SourceFile> files) {
        ManifestParser parser = new ManifestParser();
        for (SourceFile file : files) {
            String name = file.path().substring(file.path().lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            boolean manifest = file.kind() == SourceFile.Kind.MANIFEST;
            boolean setupPy = name.equals("setup.py");
            if (!manifest && !setupPy) continue;
            try {
                String text = readText(file);
                if (setupPy) parser.parseSetupPy(file.path(), text);
                else if (name.equals("package.json")) parser.parsePackageJson(file.path(), text);
                else if (name.equals("pyproject.toml")) parser.parsePyproject(file.path(), text);
                else if (name.equals("pipfile")) parser.parsePipfile(file.path(), text);
                else if (name.equals("setup.cfg")) parser.parseSetupCfg(file.path(), text);
                else parser.parseRequirements(file.path(), text);
            } catch (IOException | RuntimeException e) {
                parser.warnings.add("Could not read " + file.path() + ": " + firstLine(e.getMessage()));
            }
        }
        return new Result(parser.dependencies, parser.configText, parser.scripts, parser.warnings);
    }

    /** Handles UTF-16, which is what `pip freeze > requirements.txt` writes in Windows PowerShell. */
    private static String readText(SourceFile file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.absolutePath());
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ---- Python ----------------------------------------------------------------

    private void parseRequirements(String path, String text) {
        // A compiled lockfile lists every transitive package. Only its source file says what was
        // actually asked for, so an unused entry here says nothing about the project.
        String head = text.substring(0, Math.min(text.length(), 500));
        // Hash-pinned files and *-lock.txt are lockfiles too, even without a compiler header.
        String fileName = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (text.contains("--hash=") || fileName.matches(".*[-_.]lock(ed)?[-_.].*|.*[-_.]lock(ed)?\\.txt")) {
            warnings.add("Skipped " + path + ": a hash-pinned lockfile, not a list of direct dependencies.");
            return;
        }
        if (head.contains("pip-compile") || head.contains("uv pip compile")) {
            warnings.add("Skipped " + path + ": generated lockfile, not a list of direct dependencies.");
            return;
        }
        Scope scope =DEV_NAME.matcher(path.toLowerCase(Locale.ROOT)).find() ? Scope.DEV : Scope.RUNTIME;
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int comment = line.startsWith("#") ? 0 : line.indexOf(" #");
            if (comment >= 0) line = line.substring(0, comment).trim();
            if (line.endsWith("\\")) line = line.substring(0, line.length() - 1).trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("-") || line.contains("://")) {
                // Options such as -r or --hash are not deps, but a VCS line may name one.
                Matcher egg = EGG.matcher(line);
                if (egg.find()) add(Ecosystem.PYTHON, egg.group(1), line, scope, path, i + 1);
                else if (line.contains("://") && line.contains(" @ ")) addPep508(line, scope, path, i + 1);
                continue;
            }
            if (line.startsWith(".") || line.startsWith("/")) continue; // local path install
            addPep508(line, scope, path, i + 1);
        }
    }

    private void addPep508(String requirement, Scope scope, String path, int line) {
        Matcher matcher = REQUIREMENT.matcher(requirement.trim());
        if (matcher.matches()) {
            String spec = matcher.group(3).split(";")[0].trim();
            add(Ecosystem.PYTHON, matcher.group(1), spec, scope, path, line);
        }
    }

    private void parsePyproject(String path, String text) throws IOException {
        JsonNode root = TOML.readTree(text);
        JsonNode project = root.path("project");
        addPep508List(project.path("dependencies"), Scope.RUNTIME, path, text);
        project.path("optional-dependencies").properties().forEach(group ->
                addPep508List(group.getValue(), scopeForGroup(group.getKey()), path, text));
        root.path("dependency-groups").properties().forEach(group ->
                addPep508List(group.getValue(), Scope.DEV, path, text));

        JsonNode poetry = root.path("tool").path("poetry");
        addNameMap(poetry.path("dependencies"), Scope.RUNTIME, path, text);
        addNameMap(poetry.path("dev-dependencies"), Scope.DEV, path, text);
        poetry.path("group").properties().forEach(group ->
                addNameMap(group.getValue().path("dependencies"), scopeForGroup(group.getKey()), path, text));
        root.path("tool").path("pdm").path("dev-dependencies").properties().forEach(group ->
                addPep508List(group.getValue(), Scope.DEV, path, text));

        // Whatever is left under [tool] (pytest, ruff and so on) counts as config.
        JsonNode tool = root.path("tool").deepCopy();
        if (tool instanceof ObjectNode toolObject) {
            toolObject.remove(List.of("poetry", "pdm", "uv"));
            configText.put(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(toolObject));
        }
    }

    private void parsePipfile(String path, String text) throws IOException {
        JsonNode root = TOML.readTree(text);
        addNameMap(root.path("packages"), Scope.RUNTIME, path, text);
        addNameMap(root.path("dev-packages"), Scope.DEV, path, text);
    }

    private void parseSetupPy(String path, String text) {
        Matcher block = SETUP_PY_REQUIRES.matcher(text);
        if (!block.find()) return;
        Matcher quoted = QUOTED.matcher(block.group(1));
        while (quoted.find()) addPep508(quoted.group(1), Scope.RUNTIME, path, lineOf(text, quoted.group(1)));
    }

    private void parseSetupCfg(String path, String text) {
        String[] lines = text.split("\\R", -1);
        boolean inRequires = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.matches("\\s*install_requires\\s*=.*")) {
                inRequires = true;
                String inline = line.substring(line.indexOf('=') + 1).trim();
                if (!inline.isEmpty()) addPep508(inline, Scope.RUNTIME, path, i + 1);
            } else if (inRequires) {
                if (line.isBlank() || !Character.isWhitespace(line.charAt(0))) inRequires = false;
                else if (!line.trim().startsWith("#")) addPep508(line, Scope.RUNTIME, path, i + 1);
            }
        }
    }

    private void addPep508List(JsonNode list, Scope scope, String path, String text) {
        if (!list.isArray()) return;
        for (JsonNode item : list) {
            if (item.isTextual()) addPep508(item.asText(), scope, path, lineOf(text, item.asText()));
        }
    }

    private void addNameMap(JsonNode map, Scope scope, String path, String text) {
        map.properties().forEach(entry -> {
            String name = entry.getKey();
            if (name.equalsIgnoreCase("python")) return;
            String spec = entry.getValue().isTextual() ? entry.getValue().asText() : entry.getValue().toString();
            add(Ecosystem.PYTHON, name, spec, scope, path, lineOf(text, name));
        });
    }

    private static Scope scopeForGroup(String group) {
        return DEV_NAME.matcher(group.toLowerCase(Locale.ROOT)).find() ? Scope.DEV : Scope.OPTIONAL;
    }

    // ---- JavaScript ------------------------------------------------------------

    private void parsePackageJson(String path, String text) throws IOException {
        JsonNode root = JSON.readTree(text);
        if (root == null || !root.isObject()) throw new IOException("not a JSON object");
        Map<String, Scope> sections = Map.of(
                "dependencies", Scope.RUNTIME,
                "devDependencies", Scope.DEV,
                "peerDependencies", Scope.PEER,
                "optionalDependencies", Scope.OPTIONAL);
        for (Map.Entry<String, Scope> section : sections.entrySet()) {
            root.path(section.getKey()).properties().forEach(entry ->
                    add(Ecosystem.JAVASCRIPT, entry.getKey(), entry.getValue().asText(), section.getValue(),
                            path, lineOf(text, "\"" + entry.getKey() + "\"")));
        }
        StringBuilder scriptText = new StringBuilder();
        root.path("scripts").properties().forEach(entry -> scriptText.append(entry.getValue().asText()).append('\n'));
        scripts.put(path, scriptText.toString());

        ObjectNode rest = ((ObjectNode) root).deepCopy();
        rest.remove(List.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies",
                "peerDependenciesMeta", "resolutions", "overrides", "pnpm", "scripts", "name", "description",
                "keywords", "repository", "author", "homepage", "bugs", "license"));
        configText.put(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(rest));
    }

    // ---- shared ----------------------------------------------------------------

    private void add(Ecosystem ecosystem, String name, String spec, Scope scope, String path, int line) {
        dependencies.add(new Dependency(ecosystem, name, spec == null || spec.isBlank() ? null : spec, scope, path, line));
    }

    private static int lineOf(String text, String needle) {
        int index = text.indexOf(needle);
        if (index < 0) return 1;
        int line = 1;
        for (int i = 0; i < index; i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    private static String firstLine(String message) {
        if (message == null) return "unknown error";
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
