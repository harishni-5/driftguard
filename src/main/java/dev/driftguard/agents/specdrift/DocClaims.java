package dev.driftguard.agents.specdrift;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls checkable claims out of one Markdown file: environment variables the reader is told
 * to set, package scripts they're told to run, and files the doc links to or runs.
 *
 * Only claims with a clear signal are kept (a shell block, a backticked name next to the
 * word "env", a link). Prose is never guessed at, because every guess is a possible false alarm.
 */
final class DocClaims {

    enum Type { ENV_VAR, SCRIPT, LINK, COMMAND_FILE, CREATED_FILE }

    /**
     * @param value  the variable, script name or path as written
     * @param base   folder a command path is relative to (after any {@code cd}), repo-relative, "" for the root
     * @param runner for SCRIPT claims, the package manager used; for others null
     */
    record Claim(Type type, String value, String doc, int line, String text, String base, String runner) {}

    static final Pattern ENV_NAME = Pattern.compile("\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\b");
    private static final Pattern FENCE = Pattern.compile("^\\s*(```+|~~~+)\\s*([\\w+-]*).*$");
    private static final Set<String> SHELL_LANGS = Set.of(
            "", "bash", "sh", "shell", "zsh", "console", "terminal", "cmd", "powershell", "ps1", "pwsh",
            "env", "dotenv", "ini", "properties", "docker", "dockerfile");
    private static final Pattern BACKTICK = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern ENV_CONTEXT = Pattern.compile("(?i)\\b(env|environment|variable|variables|export|\\.env|api key|token|secret)\\b");

    // KEY=value, export KEY=value, set KEY=value, $env:KEY = value. A bare $KEY only reads a variable.
    private static final Pattern SHELL_ENV = Pattern.compile(
            "(?:^|\\s|;)(?:export\\s+|set\\s+)?([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)=|\\$env:([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)\\s*=");
    private static final Pattern SHELL_REFERENCE = Pattern.compile("\\$(?:\\{|env:)?([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)");
    /** docker run -e sets the image's own settings, which the project's code never reads. */
    private static final Pattern CONTAINER_ENV = Pattern.compile("\\bdocker\\s+(run|create)\\b|(^|\\s)-e\\s+[\"']?[A-Z]");
    private static final Pattern PLACEHOLDER = Pattern.compile("^(YOUR|MY|EXAMPLE|SOME|THE)_.*|.*_(YOUR|HERE)(_.*)?$");

    private static final Pattern RUN_SCRIPT = Pattern.compile("\\b(npm|pnpm|yarn|bun)\\s+run\\s+([A-Za-z0-9.][\\w:./-]*)");
    private static final Pattern NPM_SHORTCUT = Pattern.compile("\\bnpm\\s+(start|test)\\b");
    private static final Pattern BARE_SCRIPT = Pattern.compile("\\b(pnpm|yarn|bun)\\s+([A-Za-z][\\w:.-]*)(?=\\s|$)");
    private static final Set<String> MANAGER_COMMANDS = Set.of(
            "add", "install", "i", "ci", "remove", "rm", "uninstall", "dlx", "create", "init", "global", "why",
            "upgrade", "update", "up", "set", "config", "cache", "info", "link", "unlink", "workspace", "workspaces",
            "plugin", "npm", "run", "exec", "x", "outdated", "audit", "pack", "publish", "login", "logout",
            "version", "help", "import", "store", "prune", "patch", "rebuild", "env", "node", "bin", "list", "ls",
            "self-update", "setup", "policies", "constraints", "dedupe", "explain", "licenses", "pm");
    /** bun has its own test runner and bundler, so these don't mean a script. */
    private static final Set<String> BUN_BUILTINS = Set.of("test", "build", "repl", "upgrade", "completions");

    private static final Pattern MD_LINK = Pattern.compile("!?\\[[^\\]]*\\]\\(\\s*<?([^)\\s>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)");
    private static final Pattern REF_LINK = Pattern.compile("^\\s*\\[[^\\]]+\\]:\\s*<?(\\S+?)>?(?:\\s+.*)?$");
    private static final Pattern RUN_FILE = Pattern.compile(
            "(?:^|[\\s;&|])(?:python3?|py|node|bun|deno run|tsx|ts-node|bash|sh|zsh)\\s+(?:-[\\w-]+\\s+)*([\\w./-]+\\.(?:py|js|mjs|cjs|ts|sh))\\b");
    private static final Pattern DOT_SLASH = Pattern.compile("(?:^|[\\s;&|])\\./([\\w./-]+\\.(?:sh|py))\\b");
    /** "Save this as `x.py`", "create a file named x.sh": the reader makes this file, so it won't be in the repo. */
    private static final Pattern CREATES_FILE = Pattern.compile(
            "(?i)\\b(save|create|copy|put|write|add|make)\\b[^.\\n]{0,60}?\\b(as|named|called|into|in|to|file)\\s+(?:<[^>]+>)*[`*\"']*([\\w./-]+\\.[a-z0-9]{1,5})\\b");
    /** First line of a code block naming its file, like "# agent_memory.sh" or "// server.ts". */
    private static final Pattern FILE_COMMENT = Pattern.compile("^\\s*(?:#|//|--)\\s*([\\w./-]+\\.[a-z0-9]{1,5})\\b");
    private static final Pattern FENCE_TITLE = Pattern.compile("(?:title|filename|file)=[\"']?([\\w./-]+\\.[a-z0-9]{1,5})");
    private static final Pattern RUNNABLE_FILE = Pattern.compile(".*(/|\\.(ts|tsx|js|jsx|mjs|cjs|mts|cts|py)$).*");
    private static final Pattern CD = Pattern.compile("(?:^|[\\s;&|])cd\\s+([\\w./-]+)");
    private static final Pattern CLONE = Pattern.compile("\\bgit\\s+clone\\b.*?(?:https?://|git@)\\S*?/([\\w.-]+?)(?:\\.git)?(?:\\s|$)");

    private DocClaims() {}

    static List<Claim> extract(String doc, String text) {
        return extract(doc, text, null);
    }

    /** @param repoName this repo's folder name, so cloning it isn't mistaken for cloning another project */
    static List<Claim> extract(String doc, String text, String repoName) {
        List<Claim> claims = new ArrayList<>();
        String[] lines = text.split("\r?\n", -1);
        boolean inFence = false;
        String fenceMarker = null;
        boolean shellBlock = false;
        boolean envTable = false;
        String base = "";
        Set<String> blockReferences = new java.util.HashSet<>();
        boolean firstLineOfBlock = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNo = i + 1;
            Matcher fence = FENCE.matcher(line);
            if (fence.matches()) {
                String marker = fence.group(1);
                if (!inFence) {
                    inFence = true;
                    fenceMarker = marker.substring(0, 3);
                    shellBlock = SHELL_LANGS.contains(fence.group(2).toLowerCase(Locale.ROOT));
                    Matcher title = FENCE_TITLE.matcher(line);
                    if (title.find()) claims.add(new Claim(Type.CREATED_FILE, title.group(1), doc, lineNo, line.trim(), "", null));
                    firstLineOfBlock = true;
                    // base carries over: readers follow a doc's steps in order, so a cd in step 2 still holds in step 3.
                    continue;
                }
                if (marker.startsWith(fenceMarker)) {
                    inFence = false;
                    continue;
                }
            }
            if (inFence) {
                if (firstLineOfBlock) {
                    firstLineOfBlock = false;
                    Matcher named = FILE_COMMENT.matcher(line);
                    if (named.find()) claims.add(new Claim(Type.CREATED_FILE, named.group(1), doc, lineNo, line.trim(), "", null));
                }
                if (shellBlock) {
                    base = shellLine(doc, lineNo, line, base, claims);
                    Matcher clone = CLONE.matcher(line);
                    if (clone.find() && repoName != null && !clone.group(1).equalsIgnoreCase(repoName)) base = "/outside";
                    Matcher reference = SHELL_REFERENCE.matcher(line);
                    while (reference.find()) blockReferences.add(reference.group(1));
                }
                continue;
            }
            proseLine(doc, lineNo, line, claims, envTable);
            if (line.trim().startsWith("|")) {
                if (line.toLowerCase(Locale.ROOT).matches(".*\\|\\s*(env|environment|variable|var|name|key)[^|]*\\|.*")) envTable = true;
            } else if (!line.isBlank()) {
                envTable = false;
            }
        }
        // A variable the doc sets and then uses as $NAME in its own commands is a shell variable, not app config.
        claims.removeIf(c -> c.type() == Type.ENV_VAR && blockReferences.contains(c.value()));
        return claims;
    }

    /** Returns the folder later lines run in, so {@code cd backend} then {@code python main.py} checks backend/main.py. */
    private static String shellLine(String doc, int lineNo, String raw, String base, List<Claim> claims) {
        String line = raw.replaceFirst("^\\s*(\\$|>|PS[^>]*>|#\\s*\\$)\\s+", "");
        if (line.trim().startsWith("#") && !line.contains("=")) return base; // a comment
        String trimmed = line.trim();

        // After cd-ing into some other project, its settings aren't this repo's.
        if (!base.startsWith("/") && !CONTAINER_ENV.matcher(line).find()) {
            Matcher env = SHELL_ENV.matcher(line);
            while (env.find()) addEnv(env.group(1) != null ? env.group(1) : env.group(2), doc, lineNo, trimmed, claims);
        }

        Matcher run = RUN_SCRIPT.matcher(line);
        while (run.find()) {
            // `bun run src/index.ts` runs a file, not a package.json script.
            Type type = RUNNABLE_FILE.matcher(run.group(2)).matches() ? Type.COMMAND_FILE : Type.SCRIPT;
            claims.add(new Claim(type, run.group(2), doc, lineNo, trimmed, base, run.group(1) + " run"));
        }
        Matcher shortcut = NPM_SHORTCUT.matcher(line);
        while (shortcut.find()) claims.add(new Claim(Type.SCRIPT, shortcut.group(1), doc, lineNo, trimmed, base, "npm"));
        Matcher bare = BARE_SCRIPT.matcher(line);
        while (bare.find()) {
            if (MANAGER_COMMANDS.contains(bare.group(2))) continue;
            if (bare.group(1).equals("bun") && BUN_BUILTINS.contains(bare.group(2))) continue;
            if (RUNNABLE_FILE.matcher(bare.group(2)).matches()) continue; // bun x.ts runs a file, handled below
            claims.add(new Claim(Type.SCRIPT, bare.group(2), doc, lineNo, trimmed, base, bare.group(1)));
        }

        Matcher file = RUN_FILE.matcher(line);
        while (file.find()) claims.add(new Claim(Type.COMMAND_FILE, file.group(1), doc, lineNo, trimmed, base, null));
        Matcher dot = DOT_SLASH.matcher(line);
        while (dot.find()) claims.add(new Claim(Type.COMMAND_FILE, dot.group(1), doc, lineNo, trimmed, base, null));

        Matcher cd = CD.matcher(line);
        while (cd.find()) base = cd(base, cd.group(1));
        return base;
    }

    private static void addEnv(String name, String doc, int lineNo, String text, List<Claim> claims) {
        if (PLACEHOLDER.matcher(name).matches()) return;
        claims.add(new Claim(Type.ENV_VAR, name, doc, lineNo, text, "", null));
    }

    /** Folder after {@code cd target}. A result starting with "/" means outside the repo, where we can't judge. */
    static String cd(String base, String target) {
        if (base.startsWith("/")) return base;
        if (target.startsWith("/") || target.startsWith("~") || target.startsWith("$")) return "/outside";
        String current = base;
        for (String part : target.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (current.isEmpty()) return "/outside";
                int slash = current.lastIndexOf('/');
                current = slash < 0 ? "" : current.substring(0, slash);
            } else {
                current = current.isEmpty() ? part : current + "/" + part;
            }
        }
        return current;
    }

    private static void proseLine(String doc, int lineNo, String line, List<Claim> claims, boolean envTable) {
        Matcher creates = CREATES_FILE.matcher(line);
        while (creates.find()) claims.add(new Claim(Type.CREATED_FILE, creates.group(3), doc, lineNo, line.trim(), "", null));
        Matcher link = MD_LINK.matcher(line);
        while (link.find()) claims.add(new Claim(Type.LINK, link.group(1), doc, lineNo, line.trim(), "", null));
        Matcher ref = REF_LINK.matcher(line);
        if (ref.matches()) claims.add(new Claim(Type.LINK, ref.group(1), doc, lineNo, line.trim(), "", null));

        boolean envContext = envTable || ENV_CONTEXT.matcher(line).find();
        Matcher tick = BACKTICK.matcher(line);
        while (tick.find()) {
            String inside = tick.group(1).trim();
            if (envContext && ENV_NAME.matcher(inside).matches()) {
                addEnv(inside, doc, lineNo, line.trim(), claims);
            } else {
                // `KEY=value` written inline is an instruction to set it, whatever the sentence says.
                Matcher assign = Pattern.compile("^(?:export\\s+)?(" + ENV_NAME.pattern() + ")=").matcher(inside);
                if (assign.find()) addEnv(assign.group(1), doc, lineNo, line.trim(), claims);
            }
        }
    }
}
