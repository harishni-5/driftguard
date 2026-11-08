package dev.driftguard.manifest;

/** One declared dependency. {@code line} is 1-based. */
public record Dependency(
        Ecosystem ecosystem,
        String name,
        String versionSpec,
        Scope scope,
        String manifestPath,
        int line) {

    public enum Ecosystem { PYTHON, JAVASCRIPT }

    public enum Scope { RUNTIME, DEV, OPTIONAL, PEER }
}
