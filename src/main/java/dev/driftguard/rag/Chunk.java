package dev.driftguard.rag;

import java.util.List;

/**
 * One searchable piece of a repo: a function, a class header, a doc section, or a window
 * of lines from a file the parsers don't understand.
 *
 * @param name symbol or heading; empty for plain line windows
 * @param lines the source lines, for showing to a person or an LLM
 */
public record Chunk(int id, String path, Kind kind, String name, int startLine, int endLine, List<String> lines) {

    public enum Kind { CODE, DOC, CONFIG }

    public String location() {
        return path + ":" + startLine + "-" + endLine + (name.isEmpty() ? "" : " " + name);
    }

    public String text() {
        return String.join("\n", lines);
    }

    /** What gets embedded and keyword-indexed: where it lives, what it's called, then the content. */
    String indexText() {
        return "File: " + path + (name.isEmpty() ? "" : "\nName: " + name) + "\n" + text();
    }
}
