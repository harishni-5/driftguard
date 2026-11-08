package dev.driftguard.ingest;

import java.nio.file.Path;

/** One file in the audited repo. {@code path} is repo-relative with forward slashes. */
public record SourceFile(String path, Path absolutePath, Kind kind, long sizeBytes, int loc, String sha256) {

    public enum Kind { PYTHON, JAVASCRIPT, MANIFEST, CONFIG, OTHER }
}
