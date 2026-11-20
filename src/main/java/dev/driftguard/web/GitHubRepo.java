package dev.driftguard.web;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A public GitHub repo, parsed from whatever a visitor pasted: the plain URL, one ending in
 * .git, or a link to a file or branch inside it. Anything else is refused, so the server never
 * clones an arbitrary host.
 */
record GitHubRepo(String owner, String name) {

    private static final Pattern URL = Pattern.compile(
            "^\\s*(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))/([A-Za-z0-9._-]{1,100}?)(?:\\.git)?(?:/.*)?\\s*$");

    static Optional<GitHubRepo> parse(String input) {
        if (input == null || input.length() > 300) return Optional.empty();
        Matcher matcher = URL.matcher(input);
        if (!matcher.matches()) return Optional.empty();
        String name = matcher.group(2);
        if (name.equals(".") || name.equals("..")) return Optional.empty();
        return Optional.of(new GitHubRepo(matcher.group(1), name));
    }

    String cloneUrl() {
        return "https://github.com/" + owner + "/" + name;
    }

    String slug() {
        return owner + "/" + name;
    }
}
