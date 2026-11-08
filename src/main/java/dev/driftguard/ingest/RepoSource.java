package dev.driftguard.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** A local folder ready to audit: either the user's own folder or a temporary clone. */
public final class RepoSource implements AutoCloseable {

    // https only, which rules out git's ext:: transport and friends. No user:pass@ either,
    // since the source string gets saved to the database and printed in reports.
    private static final Pattern SAFE_URL = Pattern.compile("^https://[A-Za-z0-9.-]+(:\\d+)?/[A-Za-z0-9._~/-]+$");

    private final String source;
    private final Path root;
    private final boolean temporary;

    private RepoSource(String source, Path root, boolean temporary) {
        this.source = source;
        this.root = root;
        this.temporary = temporary;
    }

    public static RepoSource open(String input) throws IOException, InterruptedException {
        if (input.contains("://")) {
            if (!SAFE_URL.matcher(input).matches()) {
                throw new IOException("Only plain https:// repo URLs are supported, got: " + input);
            }
            return new RepoSource(input, cloneShallow(input), true);
        }
        Path path = Path.of(input).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) throw new IOException("Not a folder: " + path);
        return new RepoSource(path.toString(), path, false);
    }

    /** Clones one exact commit, so an eval sees the same code every time it runs. */
    public static RepoSource openAt(String url, String commit) throws IOException, InterruptedException {
        if (!SAFE_URL.matcher(url).matches()) throw new IOException("Only plain https:// repo URLs are supported, got: " + url);
        if (!commit.matches("[0-9a-f]{40}")) throw new IOException("Expected a full 40-character commit id, got: " + commit);
        Path directory = Files.createTempDirectory("driftguard-clone");
        try {
            git(directory, "init", "--quiet");
            git(directory, "-c", "core.symlinks=false", "fetch", "--quiet", "--depth", "1", "--", url, commit);
            git(directory, "-c", "core.symlinks=false", "checkout", "--quiet", "FETCH_HEAD");
        } catch (IOException e) {
            deleteRecursively(directory);
            throw e;
        }
        return new RepoSource(url, directory, true);
    }

    private static void git(Path directory, String... args) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(args));
        run(command, directory);
    }

    private static Path cloneShallow(String url) throws IOException, InterruptedException {
        Path directory = Files.createTempDirectory("driftguard-clone");
        run(List.of("git", "-c", "core.symlinks=false", "clone", "--quiet", "--depth", "1", "--", url, directory.toString()), directory);
        return directory;
    }

    private static void run(List<String> command, Path directory) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0"); // fail on private repos instead of hanging
        builder.environment().put("GIT_LFS_SKIP_SMUDGE", "1");
        Process process = builder.start();
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(10, TimeUnit.MINUTES);
        if (!finished || process.exitValue() != 0) {
            process.destroyForcibly();
            deleteRecursively(directory);
            throw new IOException("git failed (" + String.join(" ", command.subList(1, command.size())) + "): " + output.trim());
        }
    }

    public Optional<String> commitSha() throws InterruptedException {
        try {
            Process process = new ProcessBuilder("git", "-C", root.toString(), "rev-parse", "HEAD")
                    .redirectErrorStream(true).start();
            process.getOutputStream().close();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 && output.matches("[0-9a-f]{40}") ? Optional.of(output) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty(); // git not installed or not a repo
        }
    }

    public String source() {
        return source;
    }

    public Path root() {
        return root;
    }

    @Override
    public void close() {
        if (temporary) deleteRecursively(root);
    }

    private static void deleteRecursively(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                path.toFile().setWritable(true); // Windows marks git objects read-only
                path.toFile().delete();
            });
        } catch (IOException ignored) {
            // A leftover temp folder is harmless.
        }
    }
}
