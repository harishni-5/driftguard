package dev.driftguard.agents.specdrift;

import dev.driftguard.ingest.SourceFile;
import dev.driftguard.rag.Chunk;
import dev.driftguard.rag.RagIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * When a doc points at a file that isn't there, finds where it most likely went. Most broken
 * doc paths in the hand-checked tests were moves or renames, so naming the new location turns
 * "this link is broken" into a one-line fix.
 */
final class MovedFiles {

    /** @param exact true when a file with the same name exists; false for a weaker, retrieved guess */
    record Suggestion(String path, boolean exact) {}

    private MovedFiles() {}

    static Optional<Suggestion> find(String missing, String doc, List<SourceFile> files, RagIndex rag) {
        String clean = missing.replaceAll("[#?].*$", "").replaceAll("^(\\./)+", "").replaceAll("/+$", "");
        String name = clean.substring(clean.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (name.isEmpty() || !name.contains(".")) return Optional.empty();

        // 1. Same file name somewhere else: the strongest sign of a move. Prefer the one sharing most folders.
        List<String> sameName = new ArrayList<>();
        for (SourceFile file : files) {
            String path = file.path();
            if (path.toLowerCase(Locale.ROOT).endsWith("/" + name) || path.equalsIgnoreCase(name)) sameName.add(path);
        }
        if (!sameName.isEmpty() && sameName.size() <= 3) {
            Set<String> wanted = folders(clean);
            String best = sameName.stream()
                    .max(Comparator.comparingInt((String p) -> overlap(folders(p), wanted)).thenComparing(p -> -p.length()))
                    .orElseThrow();
            return Optional.of(new Suggestion(best, true));
        }
        if (rag == null || !sameName.isEmpty()) return Optional.empty(); // too many same-named files to pick one

        // 2. A renamed file: retrieve by the old name's words, same extension, and require a shared word.
        String extension = name.substring(name.lastIndexOf('.'));
        String stem = name.substring(0, name.lastIndexOf('.'));
        Set<String> stemWords = words(stem);
        if (stemWords.isEmpty()) return Optional.empty();
        String query = stem.replaceAll("[-_.]", " ") + " " + String.join(" ", folders(clean));
        for (RagIndex.Hit hit : rag.search(query, 5, RagIndex.Mode.KEYWORD,
                chunk -> chunk.path().toLowerCase(Locale.ROOT).endsWith(extension) && !chunk.path().equals(doc))) {
            Chunk chunk = hit.chunk();
            String other = chunk.path().substring(chunk.path().lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            Set<String> otherWords = words(other.substring(0, other.lastIndexOf('.') < 0 ? other.length() : other.lastIndexOf('.')));
            if (otherWords.containsAll(stemWords) || stemWords.containsAll(otherWords) && !otherWords.isEmpty()) {
                return Optional.of(new Suggestion(chunk.path(), false));
            }
        }
        return Optional.empty();
    }

    private static Set<String> folders(String path) {
        Set<String> out = new HashSet<>();
        String[] parts = path.toLowerCase(Locale.ROOT).split("/");
        for (int i = 0; i < parts.length - 1; i++) if (!parts[i].isEmpty() && !parts[i].equals("..")) out.add(parts[i]);
        return out;
    }

    private static Set<String> words(String stem) {
        Set<String> out = new HashSet<>();
        for (String word : stem.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) if (word.length() >= 3) out.add(word);
        return out;
    }

    private static int overlap(Set<String> a, Set<String> b) {
        int n = 0;
        for (String x : a) if (b.contains(x)) n++;
        return n;
    }
}
