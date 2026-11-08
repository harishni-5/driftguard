package dev.driftguard.agents.deaddep;

import dev.driftguard.ingest.ParsedFile;
import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.SourceFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Answers "is this package used anywhere?" for one part of a repo. */
final class UsageIndex {

    private static final int MAX_EVIDENCE = 5;
    private static final int MAX_EVIDENCE_CHARS = 160;
    private static final Pattern JS_CONFIG_SOURCE = Pattern.compile(
            "(^|.*/)([^/]*\\.config\\.(js|ts|mjs|cjs|mts|cts)|\\.[a-z-]+rc\\.(js|cjs|mjs)|gulpfile\\.[a-z]+|gruntfile\\.[a-z]+)$");

    private final Map<String, List<String>> configLines = new HashMap<>();
    private final List<ParsedFile> parsed = new ArrayList<>();
    private final List<String> filePaths = new ArrayList<>();
    private final Set<String> pythonModules = new HashSet<>();
    private final Set<String> jsPackages = new HashSet<>();
    private final Set<String> externalPythonRoots = new HashSet<>();
    private final Set<String> localPythonModules = new HashSet<>();
    private boolean fatalParseErrors = false;

    /** @param scopeDir repo-relative folder, empty string for the whole repo */
    UsageIndex(RepoSnapshot snapshot, String scopeDir, Map<String, String> manifestConfig) {
        String prefix = scopeDir.isEmpty() ? "" : scopeDir + "/";
        for (SourceFile file : snapshot.files()) {
            if (!file.path().startsWith(prefix)) continue;
            filePaths.add(file.path());
            String lowerPath = file.path().toLowerCase(Locale.ROOT);
            if (file.kind() == SourceFile.Kind.CONFIG || JS_CONFIG_SOURCE.matcher(lowerPath).matches()) {
                configLines.put(file.path(), readLines(file));
            }
            if (file.kind() == SourceFile.Kind.PYTHON) {
                for (String segment : file.path().split("/")) {
                    localPythonModules.add(segment.replaceFirst("\\.(py|pyi|ipynb)$", ""));
                }
            }
        }
        manifestConfig.forEach((path, text) -> {
            if (path.startsWith(prefix)) configLines.put(path, List.of(text.split("\\R")));
        });
        for (ParsedFile file : snapshot.parsedFiles()) {
            if (!file.path().startsWith(prefix)) continue;
            parsed.add(file);
            if (file.parseError() != null && !file.parseError().startsWith("recovered:")) fatalParseErrors = true;
            for (ParsedFile.ImportRef ref : file.imports()) index(file.language(), ref);
        }
    }

    private void index(String language, ParsedFile.ImportRef ref) {
        String module = ref.module();
        if (module.isEmpty() || module.startsWith(".") || module.startsWith("/")) return;
        if (language.equals("python")) {
            pythonModules.add(module);
            for (String name : ref.names()) pythonModules.add(module + "." + name); // from google import genai
            if (ref.external()) externalPythonRoots.add(module.split("\\.")[0]);
        } else {
            jsPackages.add(jsPackageOf(module));
        }
    }

    /** @scope/pkg/sub maps to @scope/pkg, and lodash/fp maps to lodash. */
    static String jsPackageOf(String module) {
        String[] parts = module.split("/");
        return module.startsWith("@") && parts.length >= 2 ? parts[0] + "/" + parts[1] : parts[0];
    }

    boolean pythonModuleImported(String candidate) {
        if (pythonModules.contains(candidate)) return true;
        String prefix = candidate + ".";
        return pythonModules.stream().anyMatch(module -> module.startsWith(prefix));
    }

    boolean jsPackageImported(String packageName) {
        return jsPackages.contains(packageName);
    }

    /** Top-level imports that are neither standard library nor files in this repo. */
    Set<String> externalPythonRootsExcludingLocal() {
        Set<String> roots = new HashSet<>(externalPythonRoots);
        roots.removeAll(localPythonModules);
        return roots;
    }

    boolean anyFileMatches(Pattern pattern) {
        return filePaths.stream().anyMatch(path -> pattern.matcher(path.toLowerCase(Locale.ROOT)).matches());
    }

    boolean hasJsFiles() {
        return parsed.stream().anyMatch(file -> file.language().equals("javascript"));
    }

    /** True when some file couldn't be parsed at all, so "no import found" is weaker evidence. */
    boolean hasFatalParseErrors() {
        return fatalParseErrors;
    }

    /** Lines in config and CI files that mention any of these names. */
    List<String> configMentions(Set<String> names) {
        Pattern pattern = mentionPattern(names);
        List<String> evidence = new ArrayList<>();
        configLines.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<String> lines = entry.getValue();
            for (int i = 0; i < lines.size() && evidence.size() < MAX_EVIDENCE; i++) {
                if (pattern.matcher(lines.get(i)).find()) {
                    evidence.add(entry.getKey() + ":" + (i + 1) + ": " + shorten(lines.get(i)));
                }
            }
        });
        return evidence;
    }

    /** String literals in source code that mention these names, such as plugin lists or lazy loading. */
    List<String> stringMentions(Set<String> names) {
        Pattern pattern = mentionPattern(names);
        List<String> evidence = new ArrayList<>();
        for (ParsedFile file : parsed) {
            if (file.path().endsWith("setup.py")) continue; // its strings are the declarations themselves
            for (String value : file.strings()) {
                if (evidence.size() >= MAX_EVIDENCE) return evidence;
                if (pattern.matcher(value).find()) evidence.add(file.path() + ": string \"" + shorten(value) + "\"");
            }
        }
        return evidence;
    }

    boolean scriptMentions(String scripts, Set<String> names) {
        return scripts != null && mentionPattern(names).matcher(scripts).find();
    }

    private static Pattern mentionPattern(Set<String> names) {
        StringBuilder alternatives = new StringBuilder();
        for (String name : names) {
            if (!alternatives.isEmpty()) alternatives.append('|');
            alternatives.append(Pattern.quote(name));
        }
        return Pattern.compile("(?<![A-Za-z0-9_@/.-])(?:" + alternatives + ")(?![A-Za-z0-9_-])", Pattern.CASE_INSENSITIVE);
    }

    private static String shorten(String text) {
        String trimmed = text.trim();
        return trimmed.length() <= MAX_EVIDENCE_CHARS ? trimmed : trimmed.substring(0, MAX_EVIDENCE_CHARS) + "...";
    }

    private static List<String> readLines(SourceFile file) {
        try {
            return List.of(new String(Files.readAllBytes(file.absolutePath()), StandardCharsets.UTF_8).split("\\R"));
        } catch (IOException e) {
            return List.of();
        }
    }
}
