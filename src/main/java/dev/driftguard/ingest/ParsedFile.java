package dev.driftguard.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Facts one language helper extracted from one file. Same shape for every language. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParsedFile(
        String path,
        String language,
        String parseError,
        List<ImportRef> imports,
        List<SymbolDef> symbols,
        List<CallRef> calls,
        List<String> strings) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImportRef(String module, String kind, int line, List<String> names, boolean external) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SymbolDef(String name, String qualifiedName, String kind, int startLine, int endLine, boolean exported) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallRef(String caller, String callee, int line) {}
}
