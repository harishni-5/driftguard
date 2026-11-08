package dev.driftguard.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs the Python and Node parser helpers as subprocesses.
 *
 * Protocol: send a JSON array of relative paths on stdin, read one JSON object per
 * line from stdout. Helpers ship inside the jar and are unpacked to a temp folder,
 * and they run from that folder rather than the audited repo, so nothing in the
 * repo can get picked up as a module.
 */
public final class HelperRunner implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long TIMEOUT_MINUTES = 15;
    private static final Pattern PYTHON_VERSION = Pattern.compile("Python (\\d+)\\.(\\d+)");

    private final Path workDir;

    public HelperRunner() throws IOException {
        workDir = Files.createTempDirectory("driftguard-helpers");
        for (String resource : List.of("python/parse_python.py", "js/parse_js.mjs", "js/babel-parser.mjs")) {
            Path target = workDir.resolve(resource.substring(resource.indexOf('/') + 1));
            try (InputStream in = HelperRunner.class.getResourceAsStream("/helpers/" + resource)) {
                if (in == null) throw new IOException("Missing bundled helper: " + resource);
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public List<ParsedFile> parsePython(Path repoRoot, List<String> paths) throws IOException, InterruptedException {
        if (paths.isEmpty()) return List.of();
        List<String> python = findPython().orElseThrow(() -> new IOException(
                "Python 3.10+ not found. Install it and make sure python3, python or py is on your PATH."));
        List<String> command = new ArrayList<>(python);
        command.add("-I"); // isolated mode: ignores PYTHON* env vars and user site-packages
        command.add(workDir.resolve("parse_python.py").toString());
        command.add(repoRoot.toString());
        return run(command, paths);
    }

    public List<ParsedFile> parseJavaScript(Path repoRoot, List<String> paths) throws IOException, InterruptedException {
        if (paths.isEmpty()) return List.of();
        if (nodeVersion().isEmpty()) throw new IOException("Node.js not found. Install it and make sure node is on your PATH.");
        return run(List.of("node", workDir.resolve("parse_js.mjs").toString(), repoRoot.toString()), paths);
    }

    private List<ParsedFile> run(List<String> command, List<String> paths) throws IOException, InterruptedException {
        Path stderrFile = Files.createTempFile(workDir, "stderr", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectError(stderrFile.toFile());
        builder.environment().put("PYTHONDONTWRITEBYTECODE", "1");
        Process process = builder.start();

        // Write on another thread: the helper streams output while we feed it, and
        // doing both on one thread deadlocks once a pipe buffer fills up.
        byte[] input = JSON.writeValueAsBytes(paths);
        Thread writer = new Thread(() -> {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(input);
            } catch (IOException ignored) {
                // The helper died early. The exit-code check below reports it.
            }
        });
        writer.start();

        List<ParsedFile> results = new ArrayList<>(paths.size());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) results.add(JSON.readValue(line, ParsedFile.class));
            }
        }
        writer.join();
        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("Parser helper timed out: " + command.get(0));
        }
        if (process.exitValue() != 0) {
            String stderr = Files.readString(stderrFile, StandardCharsets.UTF_8);
            throw new IOException("Parser helper failed (exit " + process.exitValue() + "): "
                    + stderr.substring(0, Math.min(stderr.length(), 2000)));
        }
        if (results.size() != paths.size()) {
            throw new IOException("Parser helper returned " + results.size() + " results for " + paths.size() + " files");
        }
        return results;
    }

    /** Finds a Python 3.10+ launcher. Windows usually has python or py rather than python3. */
    public static Optional<List<String>> findPython() {
        for (List<String> candidate : List.of(List.of("python3"), List.of("python"), List.of("py", "-3"))) {
            List<String> command = new ArrayList<>(candidate);
            command.add("--version");
            Matcher matcher = PYTHON_VERSION.matcher(capture(command));
            if (matcher.find()) {
                int major = Integer.parseInt(matcher.group(1));
                int minor = Integer.parseInt(matcher.group(2));
                if (major > 3 || (major == 3 && minor >= 10)) return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static Optional<String> pythonVersion() {
        return findPython().map(python -> {
            List<String> command = new ArrayList<>(python);
            command.add("--version");
            return capture(command).trim();
        });
    }

    public static Optional<String> nodeVersion() {
        String output = capture(List.of("node", "--version")).trim();
        return output.startsWith("v") ? Optional.of(output) : Optional.empty();
    }

    private static String capture(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return process.exitValue() == 0 ? output : "";
        } catch (IOException e) {
            return ""; // program not installed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    @Override
    public void close() {
        try (Stream<Path> paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException ignored) {
            // Temp folder cleanup is best effort.
        }
    }
}
