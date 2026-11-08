package dev.driftguard.agents.patterndrift;

import dev.driftguard.ingest.ParsedFile;
import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.SourceFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds groups of functions that look like they do the same job, by comparing the
 * words in them with TF-IDF. It needs no model and runs anywhere, but it only spots
 * functions that share vocabulary, and sharing words doesn't prove sharing a job. So
 * results are candidates, which the agent then asks an LLM to check.
 */
final class SimilarFunctions {

    /**
     * One function.
     *
     * @param lines source lines, for showing to an LLM
     * @param code the same lines with comments and docstrings removed
     */
    record Candidate(String file, String name, String kind, int startLine, int endLine, List<String> lines, List<String> code) {
        String location() {
            return file + ":" + startLine + " " + name;
        }

        String simpleName() {
            return name.substring(name.lastIndexOf('.') + 1);
        }

        /** The body with formatting removed, skipping the signature, so identical code compares equal. */
        String normalizedBody() {
            StringBuilder body = new StringBuilder();
            for (String line : code.subList(1, code.size())) body.append(line.trim()).append('\n');
            return body.toString();
        }
    }

    /** @param score highest pairwise similarity inside the group, from 0 to 1 */
    record Group(List<Candidate> members, double score) {}

    /** @param exactCopies groups with identical bodies, which are facts; similar groups are only candidates */
    record Result(List<Group> exactCopies, List<Group> similar) {}

    // Tuned on real repos: at 0.60 most results were normal sibling functions.
    static final double SIMILARITY_THRESHOLD = 0.70;
    private static final double SAME_FILE_THRESHOLD = 0.92;
    private static final int MIN_CODE_LINES = 5;
    private static final int MAX_LINES = 300;
    private static final int MIN_DISTINCT_TOKENS = 8;
    private static final int MAX_GROUP_SIZE = 6;
    private static final double COMMON_TOKEN_SHARE = 0.25;
    private static final int NAME_TOKEN_WEIGHT = 3;

    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final Set<String> TRIVIAL_NAMES = Set.of("constructor", "render", "main", "tostring", "tojson");
    // Names a framework requires. Routes share a shape because the framework says so.
    private static final Set<String> FRAMEWORK_NAMES = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "generateMetadata", "generateStaticParams",
            "getServerSideProps", "getStaticProps", "getStaticPaths", "loader", "action", "load", "middleware");
    private static final Pattern COMPONENT_FILE = Pattern.compile(".*\\.(jsx|tsx|vue|svelte|astro)$");
    private static final Pattern PY_COMMENTS = Pattern.compile("(?s)\"\"\".*?\"\"\"|'''.*?'''|#[^\\n]*");
    private static final Pattern JS_COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|(?<![:'\"`])//[^\\n]*");
    private static final Set<String> STOP_WORDS = Set.of(
            "and", "any", "are", "args", "as", "assert", "async", "await", "break", "case", "catch", "class", "cls",
            "const", "continue", "def", "default", "del", "elif", "else", "enum", "except", "export", "extends", "false",
            "finally", "for", "from", "function", "global", "has", "import", "in", "interface", "is", "kwargs", "lambda",
            "len", "let", "new", "none", "nonlocal", "not", "null", "number", "object", "of", "or", "pass", "private",
            "protected", "public", "raise", "return", "self", "static", "str", "string", "super", "switch", "the", "this",
            "throw", "true", "try", "type", "typeof", "undefined", "var", "void", "while", "with", "yield", "div", "span",
            "name", "value", "data", "result", "get", "set");

    private SimilarFunctions() {}

    static Result find(RepoSnapshot snapshot, CodeAreas areas) {
        List<Candidate> all = collect(snapshot, areas);

        Map<String, List<Candidate>> byBody = new HashMap<>();
        for (Candidate candidate : all) {
            byBody.computeIfAbsent(areas.projectOf(candidate.file()) + "\0" + candidate.normalizedBody(), key -> new ArrayList<>()).add(candidate);
        }
        List<Group> exactCopies = new ArrayList<>();
        Set<Candidate> alreadyReported = new HashSet<>();
        for (List<Candidate> copies : byBody.values()) {
            if (copies.size() < 2) continue;
            copies.sort(Comparator.comparing(Candidate::file).thenComparingInt(Candidate::startLine));
            exactCopies.add(new Group(copies, 1.0));
            alreadyReported.addAll(copies.subList(1, copies.size())); // one stays, in case it's similar to others
        }
        exactCopies.sort(Comparator.comparingInt((Group group) -> -group.members().size())
                .thenComparing(group -> group.members().get(0).location()));

        List<Candidate> pool = all.stream()
                .filter(candidate -> !alreadyReported.contains(candidate) && !isFrameworkShaped(candidate))
                .toList();
        return new Result(exactCopies, similarGroups(pool, areas));
    }

    /** Route handlers and UI components share vocabulary because of the framework, not drift. */
    private static boolean isFrameworkShaped(Candidate candidate) {
        String name = candidate.simpleName();
        if (FRAMEWORK_NAMES.contains(name)) return true;
        boolean component = !name.isEmpty() && Character.isUpperCase(name.charAt(0)) && !name.contains(".");
        return component && COMPONENT_FILE.matcher(candidate.file()).matches();
    }

    private static List<Group> similarGroups(List<Candidate> candidates, CodeAreas areas) {
        List<Map<String, Double>> vectors = vectorize(candidates);

        int count = candidates.size();
        int[] parent = new int[count];
        for (int i = 0; i < count; i++) parent[i] = i;
        Map<Integer, Double> bestScore = new HashMap<>();

        Map<String, List<Integer>> postings = new HashMap<>();
        for (int i = 0; i < count; i++) {
            for (String token : vectors.get(i).keySet()) postings.computeIfAbsent(token, key -> new ArrayList<>()).add(i);
        }
        for (int i = 0; i < count; i++) {
            Map<Integer, Double> dots = new HashMap<>();
            for (Map.Entry<String, Double> entry : vectors.get(i).entrySet()) {
                for (int j : postings.get(entry.getKey())) {
                    if (j > i) dots.merge(j, entry.getValue() * vectors.get(j).get(entry.getKey()), Double::sum);
                }
            }
            for (Map.Entry<Integer, Double> dot : dots.entrySet()) {
                int j = dot.getKey();
                if (dot.getValue() < SIMILARITY_THRESHOLD) continue;
                if (!comparable(candidates.get(i), candidates.get(j), dot.getValue(), areas)) continue;
                int rootI = find(parent, i);
                int rootJ = find(parent, j);
                double score = Math.max(dot.getValue(),
                        Math.max(bestScore.getOrDefault(rootI, 0.0), bestScore.getOrDefault(rootJ, 0.0)));
                parent[rootJ] = rootI;
                bestScore.put(rootI, score);
            }
        }

        Map<Integer, List<Candidate>> members = new HashMap<>();
        for (int i = 0; i < count; i++) members.computeIfAbsent(find(parent, i), key -> new ArrayList<>()).add(candidates.get(i));

        List<Group> groups = new ArrayList<>();
        members.forEach((root, group) -> {
            if (group.size() >= 2) {
                group.sort(Comparator.comparing(Candidate::file).thenComparingInt(Candidate::startLine));
                groups.add(new Group(group.subList(0, Math.min(MAX_GROUP_SIZE, group.size())), bestScore.get(root)));
            }
        });
        groups.sort(Comparator.comparingDouble(Group::score).reversed()
                .thenComparing(group -> group.members().get(0).location()));
        return groups;
    }

    /**
     * Compare within a project only, and never a function with one nested inside it.
     * Neighbours in one file are usually a family on purpose (dump and load), and drift
     * mostly shows up across files, so same-file pairs need a much stronger match.
     */
    private static boolean comparable(Candidate a, Candidate b, double score, CodeAreas areas) {
        if (!areas.projectOf(a.file()).equals(areas.projectOf(b.file()))) return false;
        if (!a.file().equals(b.file())) return true;
        boolean overlapping = a.startLine() <= b.endLine() && b.startLine() <= a.endLine();
        return !overlapping && score >= SAME_FILE_THRESHOLD;
    }

    private static List<Candidate> collect(RepoSnapshot snapshot, CodeAreas areas) {
        Map<String, SourceFile> filesByPath = new HashMap<>();
        for (SourceFile file : snapshot.files()) filesByPath.put(file.path(), file);

        List<Candidate> candidates = new ArrayList<>();
        for (ParsedFile parsed : snapshot.parsedFiles()) {
            if (!areas.isProductCode(parsed.path()) || parsed.path().endsWith(".ipynb") || parsed.symbols().isEmpty()) continue;
            List<String> lines = readLines(filesByPath.get(parsed.path()));
            for (ParsedFile.SymbolDef symbol : parsed.symbols()) {
                if (symbol.kind().equals("class")) continue;
                if (TRIVIAL_NAMES.contains(symbol.name().toLowerCase(Locale.ROOT))) continue;
                if (symbol.name().startsWith("__") && symbol.name().endsWith("__")) continue;
                int length = symbol.endLine() - symbol.startLine() + 1;
                if (length > MAX_LINES || symbol.endLine() > lines.size()) continue;
                List<String> source = lines.subList(symbol.startLine() - 1, symbol.endLine());
                List<String> code = withoutComments(parsed.path(), source);
                if (code.size() < MIN_CODE_LINES) continue;
                candidates.add(new Candidate(parsed.path(), symbol.qualifiedName(), symbol.kind(), symbol.startLine(),
                        symbol.endLine(), source, code));
            }
        }
        return candidates;
    }

    /** Non-blank code lines. Rough but good enough: a # inside a string just loses a few words. */
    static List<String> withoutComments(String path, List<String> source) {
        Pattern comments = path.endsWith(".py") || path.endsWith(".pyi") ? PY_COMMENTS : JS_COMMENTS;
        String stripped = comments.matcher(String.join("\n", source)).replaceAll("");
        return stripped.lines().filter(line -> !line.isBlank()).toList();
    }

    private static List<String> readLines(SourceFile file) {
        try {
            return List.of(new String(Files.readAllBytes(file.absolutePath()), StandardCharsets.UTF_8).split("\\R", -1));
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Unit-length TF-IDF vectors. Words in nearly every function are dropped as noise. */
    private static List<Map<String, Double>> vectorize(List<Candidate> candidates) {
        List<Map<String, Integer>> counts = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Candidate candidate : candidates) {
            Map<String, Integer> tokens = tokenCounts(candidate);
            counts.add(tokens);
            for (String token : tokens.keySet()) documentFrequency.merge(token, 1, Integer::sum);
        }
        int total = candidates.size();
        double commonLimit = Math.max(5, total * COMMON_TOKEN_SHARE);

        List<Map<String, Double>> vectors = new ArrayList<>();
        for (Map<String, Integer> tokens : counts) {
            Map<String, Double> vector = new HashMap<>();
            if (tokens.size() >= MIN_DISTINCT_TOKENS) {
                double squaredLength = 0;
                for (Map.Entry<String, Integer> entry : tokens.entrySet()) {
                    int frequency = documentFrequency.get(entry.getKey());
                    if (frequency > commonLimit) continue;
                    double weight = (1 + Math.log(entry.getValue())) * (1 + Math.log((1.0 + total) / (1.0 + frequency)));
                    vector.put(entry.getKey(), weight);
                    squaredLength += weight * weight;
                }
                double length = Math.sqrt(squaredLength);
                vector.replaceAll((token, weight) -> weight / length);
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private static Map<String, Integer> tokenCounts(Candidate candidate) {
        Map<String, Integer> counts = new HashMap<>();
        Set<String> nameTokens = new HashSet<>(split(candidate.simpleName()));
        for (String token : nameTokens) counts.merge(token, NAME_TOKEN_WEIGHT, Integer::sum);
        // The first line holds the name again, so body words start at the second line.
        for (int i = 1; i < candidate.code().size(); i++) {
            Matcher matcher = WORD.matcher(candidate.code().get(i));
            while (matcher.find()) {
                for (String token : split(matcher.group())) counts.merge(token, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** getUserName and get_user_name both become user, name (minus stop words). */
    private static List<String> split(String identifier) {
        List<String> tokens = new ArrayList<>();
        for (String part : identifier.split("_+")) {
            for (String word : CAMEL_BOUNDARY.split(part)) {
                String lower = word.toLowerCase(Locale.ROOT);
                if (lower.length() >= 3 && !STOP_WORDS.contains(lower)) tokens.add(lower);
            }
        }
        return tokens;
    }

    private static int find(int[] parent, int node) {
        while (parent[node] != node) {
            parent[node] = parent[parent[node]];
            node = parent[node];
        }
        return node;
    }
}
