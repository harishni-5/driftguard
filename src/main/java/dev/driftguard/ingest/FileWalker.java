package dev.driftguard.ingest;

import dev.driftguard.ingest.SourceFile.Kind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lists the files worth auditing. Symlinks are never followed, so a repo cannot
 * point us at files outside itself, and files likely to hold secrets are skipped
 * so they can never end up in a prompt.
 */
public final class FileWalker {

    private static final long MAX_FILE_BYTES = 2 * 1024 * 1024;

    private static final Set<String> SKIPPED_DIRS = Set.of(
            ".git", ".hg", ".svn", "node_modules", "bower_components", "venv", ".venv", "__pycache__",
            "site-packages", "dist", "build", "target", "out", ".next", ".nuxt", ".svelte-kit", ".turbo",
            ".cache", ".parcel-cache", "coverage", "htmlcov", ".tox", ".nox", ".mypy_cache", ".pytest_cache",
            ".ruff_cache", ".ipynb_checkpoints", ".idea", ".vscode", ".gradle", "vendor", "third_party",
            ".eggs", ".terraform", ".yarn", ".pnpm-store");

    private static final Pattern PYTHON = Pattern.compile(".*\\.(py|pyi|ipynb)$");
    private static final Pattern JAVASCRIPT = Pattern.compile(".*\\.(js|jsx|mjs|cjs|ts|tsx|mts|cts|vue|svelte|astro)$");
    private static final Pattern MINIFIED = Pattern.compile(".*(\\.min\\.(js|mjs|cjs)|\\.bundle\\.js|-bundle\\.js)$");
    private static final Pattern REQUIREMENTS = Pattern.compile("(.*requirements.*|.*/requirements/[^/]*)\\.(txt|in)$");
    private static final Pattern MANIFEST_NAME = Pattern.compile("^(package\\.json|pyproject\\.toml|pipfile|setup\\.cfg)$");
    private static final Pattern LOCKFILE = Pattern.compile(
            "^(package-lock\\.json|yarn\\.lock|pnpm-lock\\.yaml|bun\\.lockb?|poetry\\.lock|pipfile\\.lock|uv\\.lock|pdm\\.lock|constraints.*\\.txt)$");
    private static final Pattern SENSITIVE = Pattern.compile(
            "^(\\.env.*|.*\\.(pem|key|p12|pfx|keystore|jks)|id_(rsa|ed25519|ecdsa|dsa).*|\\.npmrc|\\.pypirc|\\.netrc|credentials.*|secrets?\\..*)$");
    private static final Pattern AUTOMATION_DIR = Pattern.compile("(^|.*/)(\\.husky|\\.github|\\.circleci|\\.gitlab|\\.devcontainer)/.*");
    private static final Pattern CONFIG_EXTENSION =Pattern.compile(".*\\.(ya?ml|toml|cfg|ini|json|jsonc|sh|bash|ps1|bat|cmd|mk|css|scss|sass|less|html|htm)$");
    private static final Pattern CONFIG_NAME = Pattern.compile(
            "^(dockerfile.*|.*\\.dockerfile|makefile|procfile|justfile|tiltfile|\\..*rc|\\.[a-z-]*ignore)$");

    private FileWalker() {}

    public static List<SourceFile> walk(Path root) throws IOException {
        List<SourceFile> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) return FileVisitResult.CONTINUE;
                String name = dir.getFileName().toString();
                boolean virtualEnv = Files.exists(dir.resolve("pyvenv.cfg"));
                return SKIPPED_DIRS.contains(name) || name.endsWith(".egg-info") || virtualEnv
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // walkFileTree does not follow links, so a symlink shows up here as non-regular.
                if (!attrs.isRegularFile() || attrs.size() > MAX_FILE_BYTES) return FileVisitResult.CONTINUE;
                String relative = root.relativize(file).toString().replace('\\', '/');
                Kind kind = classify(relative);
                if (kind == null) return FileVisitResult.CONTINUE;
                try {
                    byte[] bytes = Files.readAllBytes(file);
                    files.add(new SourceFile(relative, file, kind, attrs.size(), countLines(bytes), sha256(bytes)));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE; // unreadable file: skip it, don't abort the audit
            }
        });
        files.sort(Comparator.comparing(SourceFile::path));
        return files;
    }

    /** Returns null for files we should ignore completely. */
    static Kind classify(String relativePath) {
        String lowerPath = relativePath.toLowerCase(Locale.ROOT);
        String name = lowerPath.substring(lowerPath.lastIndexOf('/') + 1);
        if (SENSITIVE.matcher(name).matches() || LOCKFILE.matcher(name).matches()) return null;
        if (MINIFIED.matcher(name).matches()) return null;
        if (AUTOMATION_DIR.matcher(lowerPath).matches()) return Kind.CONFIG; // hooks often have no extension
        if (PYTHON.matcher(name).matches()) return Kind.PYTHON;
        if (JAVASCRIPT.matcher(name).matches()) return Kind.JAVASCRIPT;
        if (MANIFEST_NAME.matcher(name).matches() || REQUIREMENTS.matcher(lowerPath).matches()) return Kind.MANIFEST;
        if (CONFIG_EXTENSION.matcher(name).matches() || CONFIG_NAME.matcher(name).matches()) return Kind.CONFIG;
        return Kind.OTHER;
    }

    private static int countLines(byte[] bytes) {
        if (bytes.length == 0) return 0;
        int lines = 1;
        for (byte b : bytes) if (b == '\n') lines++;
        return bytes[bytes.length - 1] == '\n' ? lines - 1 : lines;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
