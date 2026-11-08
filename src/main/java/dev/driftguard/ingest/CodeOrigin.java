package dev.driftguard.ingest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tells hand-written code apart from code the project doesn't maintain by hand: templates that
 * get copied into generated projects, and whole files kept as identical copies (usually built
 * or synced from one source). All three agents use this, so a finding is never raised against
 * code whose "drift" is really by design.
 *
 * Found on the final 22-repo test: templates (create-llama, gpt-pilot, llamacoder) and generated
 * plugin bundles (mem0) caused more false alarms than everything else together.
 */
public final class CodeOrigin {

    /** Folders whose content is a template for some other project. */
    private static final Pattern TEMPLATE_DIR = Pattern.compile(
            "(^|.*/)(templates?|scaffolds?|scaffolding|boilerplates?|preview-kits?|project-templates?|starter-templates?"
                    + "|\\{\\{[^/]*\\}\\}|cookiecutter[^/]*)/.*");
    /** Files that are templates themselves: README-template.md, config.template.json, x.jinja2. */
    private static final Pattern TEMPLATE_FILE = Pattern.compile(".*([./_-]template|\\.tmpl|\\.jinja2?|\\.j2)(\\.[a-z0-9]+)?$");

    /** A file this long, kept identical in this many places, is a built or synced copy, not hand-edited code. */
    static final int MIN_COPY_LINES = 30;
    static final int MIN_COPIES = 3;

    private final Map<String, List<String>> copiesOf = new HashMap<>();
    private final Set<String> syncedCopies = new HashSet<>();

    public CodeOrigin(List<SourceFile> files) {
        Map<String, List<String>> bySha = new HashMap<>();
        for (SourceFile file : files) {
            if (file.loc() < MIN_COPY_LINES || file.sha256() == null) continue;
            if (file.kind() != SourceFile.Kind.PYTHON && file.kind() != SourceFile.Kind.JAVASCRIPT) continue;
            bySha.computeIfAbsent(file.sha256(), k -> new ArrayList<>()).add(file.path());
        }
        for (List<String> same : bySha.values()) {
            if (same.size() < MIN_COPIES) continue;
            List<String> sorted = same.stream().sorted().toList();
            for (String path : sorted) {
                copiesOf.put(path, sorted);
                syncedCopies.add(path);
            }
        }
    }

    public static boolean isTemplate(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return TEMPLATE_DIR.matcher(lower).matches() || TEMPLATE_FILE.matcher(lower).matches();
    }

    /** True when the file is one of {@value #MIN_COPIES}+ identical copies of a substantial source file. */
    public boolean isSyncedCopy(String path) {
        return syncedCopies.contains(path);
    }

    /** Every identical copy of this file, sorted; empty if it isn't a synced copy. */
    public List<String> copiesOf(String path) {
        return copiesOf.getOrDefault(path, List.of());
    }

    /** Groups of identical files, each sorted, for reporting once per group. */
    public List<List<String>> copyGroups() {
        return copiesOf.values().stream().distinct().sorted((a, b) -> a.get(0).compareTo(b.get(0))).toList();
    }

    /** Hand-written code worth auditing: not a template and not a synced copy. */
    public boolean isHandWritten(String path) {
        return !isTemplate(path) && !isSyncedCopy(path);
    }
}
