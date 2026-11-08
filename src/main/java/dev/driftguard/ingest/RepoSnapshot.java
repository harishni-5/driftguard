package dev.driftguard.ingest;

import dev.driftguard.manifest.Dependency;
import dev.driftguard.manifest.ManifestParser;
import java.nio.file.Path;
import java.util.List;

/** Everything ingestion learned about a repo. Agents read from this. */
public record RepoSnapshot(
        String source,
        Path root,
        String commitSha,
        String contentHash,
        String primaryLanguage,
        List<SourceFile> files,
        List<ParsedFile> parsedFiles,
        ManifestParser.Result manifests) {

    public List<Dependency> dependencies() {
        return manifests.dependencies();
    }

    public int totalLoc() {
        return files.stream()
                .filter(f -> f.kind() == SourceFile.Kind.PYTHON || f.kind() == SourceFile.Kind.JAVASCRIPT)
                .mapToInt(SourceFile::loc)
                .sum();
    }
}
