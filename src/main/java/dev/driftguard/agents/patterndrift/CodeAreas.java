package dev.driftguard.agents.patterndrift;

import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.SourceFile;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Decides which code counts as "the product" and which project a file belongs to. */
final class CodeAreas {

    // Tests, samples and generated code are allowed to do things differently.
    private static final Pattern NOT_PRODUCT = Pattern.compile(
            "(^|.*/)(tests?|__tests__|__mocks__|spec|specs|e2e|cypress|fixtures?|examples?|samples?|demos?|docs?|"
                    + "scripts?|benchmarks?|stories|\\.storybook|migrations|alembic|generated|__generated__|client/generated)/.*"
                    + "|.*(\\.(test|spec|stories|d|generated|gen)\\.[a-z]+|_test\\.py|_pb2\\.py|/test_[^/]*\\.py|^test_[^/]*\\.py|/conftest\\.py|^conftest\\.py)$"
                    + "|(^|.*/)[^/]*\\.config\\.[a-z]+$");

    // Copied in by generators such as shadcn/ui, so their similarity isn't drift.
    private static final Pattern VENDORED_UI = Pattern.compile("(^|.*/)components/ui/.*");

    private static final Set<String> PROJECT_MARKERS =
            Set.of("package.json", "pyproject.toml", "setup.py", "setup.cfg", "Pipfile", "requirements.txt");

    /** No hand-written source file gets this long; bundles and vendored parsers do. */
    static final int THIRD_PARTY_LINES = 10_000;

    private final Set<String> projectDirs = new HashSet<>();
    private final Set<String> thirdParty = new HashSet<>();

    CodeAreas(RepoSnapshot snapshot) {
        Set<String> paths = new HashSet<>();
        for (SourceFile file : snapshot.files()) paths.add(file.path());
        for (SourceFile file : snapshot.files()) {
            String path = file.path();
            int slash = path.lastIndexOf('/');
            if (PROJECT_MARKERS.contains(path.substring(slash + 1))) projectDirs.add(slash < 0 ? "" : path.substring(0, slash));
            if (file.loc() > THIRD_PARTY_LINES || hasOwnLicense(path, paths)) thirdParty.add(path);
        }
    }

    /** foo.js next to foo.LICENSE or foo.js.LICENSE.txt is someone else's code, copied in with its license. */
    private static boolean hasOwnLicense(String path, Set<String> paths) {
        int dot = path.lastIndexOf('.');
        String stem = dot > path.lastIndexOf('/') ? path.substring(0, dot) : path;
        for (String base : List.of(path, stem)) {
            for (String suffix : List.of(".LICENSE", ".LICENSE.txt", ".license", ".license.txt")) {
                if (paths.contains(base + suffix)) return true;
            }
        }
        return false;
    }

    /** Product code the project wrote itself: not tests or samples, and not vendored or bundled files. */
    boolean isProductCode(String path) {
        return isProductPath(path) && !thirdParty.contains(path);
    }

    static boolean isProductPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return !NOT_PRODUCT.matcher(lower).matches() && !VENDORED_UI.matcher(lower).matches();
    }

    /** Nearest folder with a manifest, so a frontend and backend in one repo aren't compared. */
    String projectOf(String path) {
        String directory = path;
        while (true) {
            int slash = directory.lastIndexOf('/');
            if (slash < 0) return "";
            directory = directory.substring(0, slash);
            if (projectDirs.contains(directory)) return directory;
        }
    }

    /** Files shown as examples in findings. */
    static String sample(List<String> paths, int limit) {
        String shown = String.join(", ", paths.subList(0, Math.min(limit, paths.size())));
        return paths.size() > limit ? shown + " and " + (paths.size() - limit) + " more" : shown;
    }
}
