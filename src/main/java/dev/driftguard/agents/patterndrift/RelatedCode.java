package dev.driftguard.agents.patterndrift;

import dev.driftguard.agents.patterndrift.SimilarFunctions.Candidate;
import dev.driftguard.agents.patterndrift.SimilarFunctions.Group;
import dev.driftguard.rag.Chunk;
import dev.driftguard.rag.RagIndex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Retrieves context for one similar-function group: where its functions are used, and docs
 * that mention them. Two functions that look alike can be merged only if their callers would
 * accept one shared version, and that's often visible only at the call sites.
 */
final class RelatedCode {

    static final int MAX_USES = 4;
    static final int MAX_DOCS = 1;
    private static final int USE_RADIUS = 4;
    private static final int DOC_LINES = 12;
    static final int MAX_CHARS = 3_500;

    private RelatedCode() {}

    /** Empty when nothing relevant was found, so the prompt says nothing rather than something misleading. */
    static String forGroup(RagIndex rag, Group group) {
        if (rag == null) return "";
        Set<String> names = new HashSet<>();
        for (Candidate member : group.members()) {
            String simple = member.simpleName();
            if (simple.length() >= 3) names.add(simple);
        }
        if (names.isEmpty()) return "";
        Pattern use = Pattern.compile("\\b(" + String.join("|", names.stream().map(Pattern::quote).toList()) + ")\\b");
        String query = String.join(" ", names);

        StringBuilder out = new StringBuilder();
        Set<String> seen = new HashSet<>();
        int uses = 0;
        for (RagIndex.Hit hit : rag.search(query, 40, rag.hasVectors() ? RagIndex.Mode.HYBRID : RagIndex.Mode.KEYWORD,
                chunk -> chunk.kind() == Chunk.Kind.CODE && !insideGroup(chunk, group))) {
            if (uses >= MAX_USES) break;
            Chunk chunk = hit.chunk();
            int line = firstMatch(chunk, use, true);
            if (line < 0 || !seen.add(chunk.path() + ":" + (chunk.startLine() + line))) continue;
            out.append(excerpt("used at", chunk, line - USE_RADIUS, line + USE_RADIUS));
            uses++;
        }
        int docs = 0;
        for (RagIndex.Hit hit : rag.search(query, 10, RagIndex.Mode.KEYWORD, chunk -> chunk.kind() == Chunk.Kind.DOC)) {
            if (docs >= MAX_DOCS) break;
            int line = firstMatch(hit.chunk(), use, false);
            if (line < 0) continue;
            out.append(excerpt("mentioned in", hit.chunk(), line - 2, line - 2 + DOC_LINES));
            docs++;
        }
        String text = out.toString();
        return text.length() <= MAX_CHARS ? text : text.substring(0, MAX_CHARS) + "\n(context cut short)\n";
    }

    /** Index of the first line naming one of the functions; for code, skipping the definition lines themselves. */
    private static int firstMatch(Chunk chunk, Pattern use, boolean code) {
        for (int i = 0; i < chunk.lines().size(); i++) {
            String line = chunk.lines().get(i);
            Matcher matcher = use.matcher(line);
            if (!matcher.find()) continue;
            if (code && isDefinition(line, matcher.group(1))) continue;
            return i;
        }
        return -1;
    }

    private static boolean isDefinition(String line, String name) {
        return line.matches(".*\\b(def|function|class)\\s+" + Pattern.quote(name) + "\\b.*")
                || line.matches("\\s*(export\\s+)?(const|let|var)\\s+" + Pattern.quote(name) + "\\s*=.*")
                || line.matches("\\s*(async\\s+)?" + Pattern.quote(name) + "\\s*\\([^)]*\\)\\s*\\{.*"); // method shorthand
    }

    private static boolean insideGroup(Chunk chunk, Group group) {
        for (Candidate member : group.members()) {
            if (member.file().equals(chunk.path()) && chunk.startLine() <= member.endLine() && member.startLine() <= chunk.endLine()) {
                return true;
            }
        }
        return false;
    }

    private static String excerpt(String label, Chunk chunk, int from, int to) {
        int start = Math.max(0, from);
        int end = Math.min(chunk.lines().size() - 1, to);
        StringBuilder out = new StringBuilder("--- " + label + " " + chunk.path() + ":" + (chunk.startLine() + start)
                + (chunk.name().isEmpty() ? "" : " (in " + chunk.name() + ")") + "\n");
        List<String> lines = new ArrayList<>(chunk.lines().subList(start, end + 1));
        for (String line : lines) out.append(PatternDriftAgent.redact(line)).append('\n');
        return out.toString();
    }
}
