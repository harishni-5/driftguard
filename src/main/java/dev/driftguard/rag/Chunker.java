package dev.driftguard.rag;

import dev.driftguard.ingest.CodeOrigin;
import dev.driftguard.ingest.ParsedFile;
import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.SourceFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a repo into chunks along the lines a person would: one per function, one per doc
 * section. Python and JavaScript use the parsers' symbol table; other text files fall back
 * to fixed windows of lines.
 *
 * Templates, synced copies, very large files and lockfiles are left out: they'd crowd the
 * results with code nobody maintains by hand (see {@link CodeOrigin}).
 */
final class Chunker {

    static final int MAX_FUNCTION_LINES = 150;
    static final int WINDOW = 60;
    private static final int OVERLAP = 10;
    private static final int MAX_FILE_LINES = 10_000;
    private static final int MAX_CONFIG_LINES = 400;
    private static final int CLASS_HEADER_LINES = 15;

    private static final Pattern DOC = Pattern.compile("(?i).*\\.(md|mdx|markdown|rst|txt|adoc)$");
    private static final Pattern OTHER_CODE = Pattern.compile(
            "(?i).*\\.(java|kt|kts|scala|go|rs|rb|php|cs|swift|c|h|cc|cpp|hpp|sql|graphql|proto|prisma|sh|bash)$");
    private static final Pattern CONFIG = Pattern.compile("(?i).*\\.(ya?ml|toml|cfg|ini|json|jsonc)$|(?i)(.*/)?(dockerfile|makefile)$");
    private static final Pattern SKIP = Pattern.compile(
            "(?i).*(lock\\.json|\\.lock|lock\\.ya?ml|\\.min\\.js|\\.map|\\.snap|license(\\.\\w+)?|changelog(\\.\\w+)?|\\.svg)$");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    private Chunker() {}

    static List<Chunk> chunk(RepoSnapshot snapshot) {
        CodeOrigin origin = new CodeOrigin(snapshot.files());
        Map<String, ParsedFile> parsed = new HashMap<>();
        for (ParsedFile file : snapshot.parsedFiles()) parsed.put(file.path(), file);

        List<Chunk> chunks = new ArrayList<>();
        List<SourceFile> files = snapshot.files().stream().sorted(Comparator.comparing(SourceFile::path)).toList();
        for (SourceFile file : files) {
            String path = file.path();
            if (!origin.isHandWritten(path) || SKIP.matcher(path).matches() || path.endsWith(".ipynb")) continue;
            boolean code = file.kind() == SourceFile.Kind.PYTHON || file.kind() == SourceFile.Kind.JAVASCRIPT;
            boolean doc = DOC.matcher(path).matches();
            boolean otherCode = OTHER_CODE.matcher(path).matches();
            boolean config = CONFIG.matcher(path).matches();
            if (!code && !doc && !otherCode && !config) continue;

            List<String> lines = read(file);
            if (lines.isEmpty() || lines.size() > MAX_FILE_LINES || (config && lines.size() > MAX_CONFIG_LINES)) continue;
            if (code && parsed.containsKey(path) && parsed.get(path).parseError() == null) {
                symbolChunks(path, lines, parsed.get(path).symbols(), chunks);
            } else if (doc) {
                docChunks(path, lines, chunks);
            } else {
                windows(path, config ? Chunk.Kind.CONFIG : Chunk.Kind.CODE, "", lines, 1, lines.size(), chunks);
            }
        }
        return chunks;
    }

    /** Outermost functions and methods become chunks; what's left (imports, constants, top-level code) is windowed. */
    static void symbolChunks(String path, List<String> lines, List<ParsedFile.SymbolDef> symbols, List<Chunk> out) {
        List<ParsedFile.SymbolDef> sorted = symbols.stream()
                .filter(s -> s.startLine() >= 1 && s.endLine() >= s.startLine() && s.endLine() <= lines.size())
                .sorted(Comparator.comparingInt(ParsedFile.SymbolDef::startLine)
                        .thenComparing(Comparator.comparingInt(ParsedFile.SymbolDef::endLine).reversed()))
                .toList();
        boolean[] covered = new boolean[lines.size() + 1];
        int takenEnd = 0;
        for (ParsedFile.SymbolDef symbol : sorted) {
            if (symbol.startLine() <= takenEnd) continue; // nested inside a function already taken
            if (symbol.kind().equals("class")) {
                boolean hasMembers = sorted.stream().anyMatch(s -> s != symbol && s.startLine() > symbol.startLine()
                        && s.endLine() <= symbol.endLine());
                if (!hasMembers) {
                    windows(path, Chunk.Kind.CODE, symbol.qualifiedName(), lines, symbol.startLine(), symbol.endLine(), out);
                    mark(covered, symbol.startLine(), symbol.endLine());
                    takenEnd = symbol.endLine();
                } else if (hasMembers) {
                    // The class line, docstring and fields: enough to find "the class that does X".
                    int end = Math.min(symbol.endLine(), symbol.startLine() + CLASS_HEADER_LINES - 1);
                    for (ParsedFile.SymbolDef member : sorted) {
                        if (member.startLine() > symbol.startLine() && member.startLine() <= end) end = member.startLine() - 1;
                    }
                    add(out, path, Chunk.Kind.CODE, symbol.qualifiedName(), lines, symbol.startLine(), end);
                    mark(covered, symbol.startLine(), end);
                }
                continue;
            }
            windows(path, Chunk.Kind.CODE, symbol.qualifiedName(), lines, symbol.startLine(), symbol.endLine(), out);
            mark(covered, symbol.startLine(), symbol.endLine());
            takenEnd = symbol.endLine();
        }
        // Leftover runs of lines, merged and windowed.
        int start = -1;
        for (int line = 1; line <= lines.size() + 1; line++) {
            boolean free = line <= lines.size() && !covered[line];
            if (free && start < 0) start = line;
            if (!free && start >= 0) {
                windows(path, Chunk.Kind.CODE, "", lines, start, line - 1, out);
                start = -1;
            }
        }
    }

    /** One chunk per heading section; long sections are windowed and keep their heading as the name. */
    static void docChunks(String path, List<String> lines, List<Chunk> out) {
        int start = 1;
        String heading = "";
        boolean inFence = false;
        for (int line = 1; line <= lines.size(); line++) {
            String text = lines.get(line - 1);
            if (text.trim().startsWith("```")) inFence = !inFence;
            Matcher matcher = inFence ? null : HEADING.matcher(text);
            if (matcher != null && matcher.matches() && line > start) {
                windows(path, Chunk.Kind.DOC, heading, lines, start, line - 1, out);
                start = line;
            }
            if (matcher != null && matcher.matches()) heading = matcher.group(2);
        }
        windows(path, Chunk.Kind.DOC, heading, lines, start, lines.size(), out);
    }

    private static void windows(String path, Chunk.Kind kind, String name, List<String> lines, int from, int to, List<Chunk> out) {
        int limit = kind == Chunk.Kind.CODE && !name.isEmpty() ? MAX_FUNCTION_LINES : WINDOW;
        if (to - from + 1 <= limit) {
            add(out, path, kind, name, lines, from, to);
            return;
        }
        int step = WINDOW - OVERLAP;
        for (int start = from; start <= to; start += step) {
            int end = Math.min(to, start + WINDOW - 1);
            add(out, path, kind, name, lines, start, end);
            if (end == to) break;
        }
    }

    private static void add(List<Chunk> out, String path, Chunk.Kind kind, String name, List<String> lines, int from, int to) {
        if (to < from) return;
        List<String> slice = lines.subList(from - 1, to);
        long meaningful = slice.stream().filter(l -> l.trim().length() > 2).count();
        if (meaningful < 2) return;
        out.add(new Chunk(out.size(), path, kind, name, from, to, List.copyOf(slice)));
    }

    private static void mark(boolean[] covered, int from, int to) {
        for (int i = from; i <= to && i < covered.length; i++) covered[i] = true;
    }

    private static List<String> read(SourceFile file) {
        try {
            byte[] bytes = Files.readAllBytes(file.absolutePath());
            for (int i = 0; i < Math.min(bytes.length, 8000); i++) if (bytes[i] == 0) return List.of(); // binary
            return List.of(new String(bytes, StandardCharsets.UTF_8).split("\\R", -1));
        } catch (IOException e) {
            return List.of();
        }
    }
}
