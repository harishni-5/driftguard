package dev.driftguard.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits text into search words. getUserName, get_user_name and "user name" all share "user" and "name". */
final class Tokens {

    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final Set<String> STOP = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "do", "does", "for", "from", "how", "if", "in", "is", "it",
            "of", "on", "or", "that", "the", "this", "to", "what", "when", "where", "which", "who", "why", "with",
            "def", "const", "let", "var", "return", "self", "import", "export", "function", "class", "new", "null",
            "none", "true", "false", "public", "private", "static", "final", "void");

    private Tokens() {}

    static List<String> of(String text) {
        List<String> out = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            List<String> parts = new ArrayList<>();
            for (String piece : word.split("_+")) {
                for (String part : CAMEL_BOUNDARY.split(piece)) {
                    if (!part.isEmpty()) parts.add(part.toLowerCase(Locale.ROOT));
                }
            }
            for (String part : parts) add(out, part);
            if (parts.size() > 1) add(out, word.toLowerCase(Locale.ROOT).replace("_", "")); // whole identifier too
        }
        return out;
    }

    private static void add(List<String> out, String token) {
        if (token.length() < 2 || STOP.contains(token)) return;
        out.add(stem(token));
    }

    /** A light plural trim, so "dependencies" finds "dependency" and "files" finds "file". */
    static String stem(String token) {
        if (token.length() > 5 && token.endsWith("ies")) return token.substring(0, token.length() - 3) + "y";
        if (token.length() > 4 && token.endsWith("sses")) return token.substring(0, token.length() - 2);
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) return token.substring(0, token.length() - 1);
        return token;
    }
}
