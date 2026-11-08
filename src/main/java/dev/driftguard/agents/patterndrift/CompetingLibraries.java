package dev.driftguard.agents.patterndrift;

import dev.driftguard.agents.Finding;
import dev.driftguard.agents.Finding.Confidence;
import dev.driftguard.agents.Finding.DecidedBy;
import dev.driftguard.agents.Finding.Severity;
import dev.driftguard.agents.patterndrift.ConcernCatalog.Approach;
import dev.driftguard.agents.patterndrift.ConcernCatalog.Concern;
import dev.driftguard.ingest.ParsedFile;
import dev.driftguard.ingest.RepoSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds projects using more than one library for the same job. Fully static. */
final class CompetingLibraries {

    private static final int CASUAL_MIN_FILES = 2;
    private static final int SEVERE_APPROACH_COUNT = 3;

    private record Usage(String file, int line) {}

    private CompetingLibraries() {}

    static List<Finding> find(RepoSnapshot snapshot, CodeAreas areas) {
        Map<String, List<ParsedFile>> byProject = new LinkedHashMap<>();
        for (ParsedFile file : snapshot.parsedFiles()) {
            if (CodeAreas.isProductCode(file.path())) {
                byProject.computeIfAbsent(areas.projectOf(file.path()), key -> new ArrayList<>()).add(file);
            }
        }
        List<Finding> findings = new ArrayList<>();
        byProject.forEach((project, files) -> {
            for (Concern concern : ConcernCatalog.ALL) {
                Finding finding = check(concern, project, files);
                if (finding != null) findings.add(finding);
            }
        });
        return findings;
    }

    private static Finding check(Concern concern, String project, List<ParsedFile> files) {
        Set<String> localFunctions = new HashSet<>();
        for (ParsedFile file : files) {
            for (ParsedFile.SymbolDef symbol : file.symbols()) localFunctions.add(symbol.name());
        }

        Map<Approach, List<Usage>> usage = new LinkedHashMap<>();
        for (Approach approach : concern.approaches()) {
            List<Usage> uses = new ArrayList<>();
            for (ParsedFile file : files) {
                if (!file.language().equals(concern.language())) continue;
                int line = firstUse(approach, file, localFunctions);
                if (line > 0) uses.add(new Usage(file.path(), line));
            }
            boolean counts = approach.casual() ? uses.size() >= CASUAL_MIN_FILES : !uses.isEmpty();
            if (counts) usage.put(approach, uses);
        }
        // print or console next to nothing else is normal, so a real alternative must exist.
        long realApproaches = usage.keySet().stream().filter(approach -> !approach.casual()).count();
        if (usage.size() < 2 || realApproaches < 1) return null;

        List<Map.Entry<Approach, List<Usage>>> ranked = new ArrayList<>(usage.entrySet());
        ranked.sort(Comparator.comparingInt((Map.Entry<Approach, List<Usage>> e) -> -e.getValue().size())
                .thenComparing(e -> e.getKey().name()));
        return toFinding(concern, project, ranked);
    }

    private static int firstUse(Approach approach, ParsedFile file, Set<String> localFunctions) {
        for (ParsedFile.ImportRef ref : file.imports()) {
            for (String module : approach.modules()) {
                String imported = ref.module();
                if (imported.equals(module) || imported.startsWith(module + "/") || imported.startsWith(module + ".")) {
                    return Math.max(ref.line(), 1);
                }
            }
        }
        for (ParsedFile.CallRef call : file.calls()) {
            // A project's own fetch() helper isn't the global fetch.
            if (approach.callees().contains(call.callee()) && !localFunctions.contains(call.callee())) {
                return Math.max(call.line(), 1);
            }
        }
        return 0;
    }

    private static Finding toFinding(Concern concern, String project, List<Map.Entry<Approach, List<Usage>>> ranked) {
        List<String> names = ranked.stream().map(entry -> entry.getKey().name()).toList();
        List<String> evidence = new ArrayList<>();
        for (Map.Entry<Approach, List<Usage>> entry : ranked) {
            List<String> paths = entry.getValue().stream().map(Usage::file).toList();
            evidence.add(entry.getKey().name() + " (" + paths.size() + " files): " + CodeAreas.sample(paths, 3));
        }

        Map.Entry<Approach, List<Usage>> top = ranked.get(0);
        boolean tie = ranked.get(1).getValue().size() == top.getValue().size();
        Usage outlier = ranked.get(ranked.size() - 1).getValue().get(0);
        String where = project.isEmpty() ? "this project" : project;
        String fix = tie
                ? "Pick one and move the rest of the code to it. No approach is clearly more common."
                : top.getKey().name() + " is the most used, so consider moving the other code to it, unless there's a reason for the split.";
        Severity severity = names.size() >= SEVERE_APPROACH_COUNT ? Severity.HIGH : Severity.MEDIUM;
        boolean casualInvolved = ranked.stream().anyMatch(entry -> entry.getKey().casual());

        return new Finding(
                PatternDriftAgent.NAME,
                "competing-libraries",
                casualInvolved ? Severity.LOW : severity,
                casualInvolved ? Confidence.MEDIUM : Confidence.HIGH,
                DecidedBy.STATIC,
                names.size() + " " + concern.title() + " in " + where + ": " + String.join(", ", names),
                "The same job is done " + names.size() + " different ways in non-test code, so the next person or AI session has no clear pattern to follow.",
                outlier.file(),
                outlier.line(),
                evidence,
                fix,
                PatternDriftAgent.fingerprint("competing-libraries|" + project + "|" + concern.language() + "|" + concern.id()));
    }
}
