package dev.driftguard.ingest;

import dev.driftguard.ingest.SourceFile.Kind;
import dev.driftguard.manifest.ManifestParser;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Turns a repo folder into a {@link RepoSnapshot}. */
public final class RepoIngestor {

    private RepoIngestor() {}

    public static RepoSnapshot ingest(RepoSource source) throws IOException, InterruptedException {
        List<SourceFile> files = FileWalker.walk(source.root());
        List<String> python = pathsOfKind(files, Kind.PYTHON);
        List<String> javascript = pathsOfKind(files, Kind.JAVASCRIPT);

        List<ParsedFile> parsed = new ArrayList<>();
        try (HelperRunner helpers = new HelperRunner()) {
            parsed.addAll(helpers.parsePython(source.root(), python));
            parsed.addAll(helpers.parseJavaScript(source.root(), javascript));
        }

        return new RepoSnapshot(
                source.source(),
                source.root(),
                source.commitSha().orElse(null),
                contentHash(files),
                primaryLanguage(files),
                files,
                parsed,
                ManifestParser.parse(files));
    }

    private static List<String> pathsOfKind(List<SourceFile> files, Kind kind) {
        return files.stream().filter(f -> f.kind() == kind).map(SourceFile::path).toList();
    }

    private static String primaryLanguage(List<SourceFile> files) {
        int python = files.stream().filter(f -> f.kind() == Kind.PYTHON).mapToInt(SourceFile::loc).sum();
        int javascript = files.stream().filter(f -> f.kind() == Kind.JAVASCRIPT).mapToInt(SourceFile::loc).sum();
        if (python == 0 && javascript == 0) return null;
        return python >= javascript ? "python" : "javascript";
    }

    private static String contentHash(List<SourceFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (SourceFile file : files) { // already sorted by path
                digest.update(file.path().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(file.sha256().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
