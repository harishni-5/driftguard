package dev.driftguard.agents.specdrift;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.driftguard.agents.AuditAgent;
import dev.driftguard.agents.Finding;
import dev.driftguard.agents.Finding.Confidence;
import dev.driftguard.agents.Finding.DecidedBy;
import dev.driftguard.agents.Finding.Severity;
import dev.driftguard.agents.specdrift.DocClaims.Claim;
import dev.driftguard.agents.specdrift.DocClaims.Type;
import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.SourceFile;
import dev.driftguard.manifest.Dependency;
import dev.driftguard.store.Database;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds places where the docs promise something the code doesn't back up: environment variables
 * nothing reads, scripts that don't exist, and links or commands pointing at missing files.
 *
 * Everything here is static. Each check errs toward silence, because a doc tool that cries wolf
 * gets ignored, and when a claim might be satisfied in a way we can't see it goes to review.
 */
public final class SpecDriftAgent implements AuditAgent {

    public static final String NAME = "spec-drift";
    private static final int MAX_PER_CATEGORY = 30;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Pattern DOC_FILE = Pattern.compile("(?i).*\\.(md|mdx|markdown)$");
    private static final Pattern HISTORY_DOC = Pattern.compile(
            "(?i)(^|.*/)(changelog|changes|history|release[-_ ]?notes?|releases|news|upgrading|migration[^/]*)(\\.[a-z]+)?$"
            // Proposals, RFCs and blog posts describe plans or the past, not what the code does today.
            + "|(?i)(^|.*/)(\\.github|changelogs?|releases?|proposals?|rfcs?|adrs?|decisions|blog|posts|plans?|ideas|design-docs?)/.*");
    private static final Pattern TEXT_DOC = Pattern.compile("(?i).*\\.(md|mdx|markdown|rst|txt|adoc)$");

    /** Read by the OS, shells, runtimes or CI rather than by the project's code. */
    private static final Set<String> PLATFORM_ENV = Set.of(
            "NODE_ENV", "NODE_OPTIONS", "LD_LIBRARY_PATH", "DYLD_LIBRARY_PATH", "CUDA_VISIBLE_DEVICES", "HTTP_PROXY",
            "HTTPS_PROXY", "NO_PROXY", "ALL_PROXY", "GITHUB_TOKEN", "GH_TOKEN", "PYTHONDONTWRITEBYTECODE",
            "TF_CPP_MIN_LOG_LEVEL", "TOKENIZERS_PARALLELISM", "OMP_NUM_THREADS", "PIP_INDEX_URL", "UV_INDEX_URL",
            "NEXT_TELEMETRY_DISABLED", "DO_NOT_TRACK", "FORCE_COLOR", "NO_COLOR", "TZ_DATA", "JAVA_HOME", "ANDROID_HOME",
            "XDG_CONFIG_HOME", "XDG_DATA_HOME", "XDG_CACHE_HOME", "SSL_CERT_FILE", "REQUESTS_CA_BUNDLE", "NODE_EXTRA_CA_CERTS",
            "DOCKER_HOST", "DOCKER_BUILDKIT", "COMPOSE_PROJECT_NAME", "COMPOSE_FILE", "PYTHON_VERSION", "NODE_VERSION");
    private static final List<String> PLATFORM_PREFIXES = List.of(
            "AWS_", "AZURE_", "GOOGLE_CLOUD_", "GCLOUD_", "VERCEL_", "NETLIFY_", "RAILWAY_", "RENDER_", "FLY_", "HEROKU_",
            "GITHUB_", "CI_", "RUNNER_", "DOCKER_", "COMPOSE_", "NPM_CONFIG_", "PIP_", "UV_", "POETRY_", "CONDA_",
            "HF_", "HUGGINGFACE_", "TRANSFORMERS_", "LANGCHAIN_", "LANGSMITH_", "OTEL_", "SENTRY_", "PG", "POSTGRES_", "MYSQL_",
            "CMAKE_", "WANDB_", "NVM_", "CUDA_", "TORCH_", "PYTORCH_", "NEXT_TELEMETRY", "TURBO_", "NX_");
    /**
     * The standard key each provider's SDK reads by itself, keyed by the word that shows up in its
     * package or import name. If the project uses that SDK, the docs are right to mention the key.
     */
    private static final Map<String, String> SDK_KEYS = Map.ofEntries(
            Map.entry("OPENAI_API_KEY", "openai"), Map.entry("OPENAI_BASE_URL", "openai"), Map.entry("OPENAI_API_BASE", "openai"),
            Map.entry("OPENAI_ORG_ID", "openai"), Map.entry("ANTHROPIC_API_KEY", "anthropic"), Map.entry("GROQ_API_KEY", "groq"),
            Map.entry("MISTRAL_API_KEY", "mistral"), Map.entry("COHERE_API_KEY", "cohere"), Map.entry("FIREWORKS_API_KEY", "fireworks"),
            Map.entry("TOGETHER_API_KEY", "together"), Map.entry("DEEPSEEK_API_KEY", "deepseek"), Map.entry("XAI_API_KEY", "xai"),
            Map.entry("PERPLEXITY_API_KEY", "perplexity"), Map.entry("PPLX_API_KEY", "perplexity"), Map.entry("GOOGLE_API_KEY", "google"),
            Map.entry("GEMINI_API_KEY", "gemini"), Map.entry("GOOGLE_GENERATIVE_AI_API_KEY", "google"), Map.entry("TAVILY_API_KEY", "tavily"),
            Map.entry("E2B_API_KEY", "e2b"), Map.entry("REPLICATE_API_TOKEN", "replicate"), Map.entry("PINECONE_API_KEY", "pinecone"),
            Map.entry("AI_GATEWAY_API_KEY", "ai"), Map.entry("OPENROUTER_API_KEY", "openrouter"), Map.entry("NVIDIA_API_KEY", "nvidia"),
            Map.entry("HUGGINGFACEHUB_API_TOKEN", "huggingface"), Map.entry("VOYAGE_API_KEY", "voyage"), Map.entry("JINA_API_KEY", "jina"));
    /** Frameworks only expose variables with these prefixes to browser code. */
    private static final List<String> PUBLIC_PREFIXES = List.of("NEXT_PUBLIC_", "VITE_", "REACT_APP_", "EXPO_PUBLIC_", "NUXT_PUBLIC_", "PUBLIC_");
    /** Libraries that read their own variables, so the project's code never mentions them. */
    private static final Map<String, List<String>> SDK_PREFIXES = Map.ofEntries(
            Map.entry("OPENAI_", List.of("openai", "@ai-sdk/openai", "langchain-openai", "@langchain/openai")),
            Map.entry("ANTHROPIC_", List.of("anthropic", "@anthropic-ai/sdk", "@ai-sdk/anthropic", "langchain-anthropic")),
            Map.entry("GROQ_", List.of("groq", "groq-sdk", "@ai-sdk/groq", "langchain-groq")),
            Map.entry("MISTRAL_", List.of("mistralai", "@mistralai/mistralai", "@ai-sdk/mistral", "langchain-mistralai")),
            Map.entry("COHERE_", List.of("cohere", "cohere-ai", "langchain-cohere")),
            Map.entry("TAVILY_", List.of("tavily-python", "@tavily/core", "langchain-tavily")),
            Map.entry("GOOGLE_", List.of("google-genai", "google-generativeai", "@ai-sdk/google", "langchain-google-genai")),
            Map.entry("GEMINI_", List.of("google-genai", "google-generativeai", "@ai-sdk/google")),
            Map.entry("REPLICATE_", List.of("replicate")),
            Map.entry("PINECONE_", List.of("pinecone", "pinecone-client", "@pinecone-database/pinecone")),
            Map.entry("E2B_", List.of("e2b", "@e2b/code-interpreter", "e2b-code-interpreter")),
            Map.entry("AUTH_", List.of("next-auth", "@auth/core")),
            Map.entry("NEXTAUTH_", List.of("next-auth")),
            Map.entry("DATABASE_URL", List.of("prisma", "@prisma/client", "drizzle-kit")));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Report run(Context context) throws SQLException {
        RepoSnapshot snapshot = context.snapshot();
        List<String> warnings = new ArrayList<>();

        List<Claim> claims = new ArrayList<>();
        StringBuilder code = new StringBuilder();
        StringBuilder python = new StringBuilder();
        Set<String> scripts = new HashSet<>();
        int docs = 0;
        for (SourceFile file : snapshot.files()) {
            String text = read(file);
            if (text == null) continue;
            if (DOC_FILE.matcher(file.path()).matches()) {
                if (HISTORY_DOC.matcher(file.path()).matches()) continue;
                docs++;
                claims.addAll(DocClaims.extract(file.path(), text, repoName(snapshot.source())));
            } else if (!TEXT_DOC.matcher(file.path()).matches()) {
                code.append(text).append('\n');
                if (file.kind() == SourceFile.Kind.PYTHON) python.append(text).append('\n');
                if (file.path().endsWith("package.json")) scripts.addAll(scriptNames(text));
            }
        }
        Set<String> dependencies = new HashSet<>();
        for (Dependency dependency : snapshot.dependencies()) dependencies.add(dependency.name().toLowerCase(Locale.ROOT));
        // Words from every imported module and dependency name, to tell which SDKs the project uses.
        Set<String> sdkWords = new HashSet<>();
        snapshot.parsedFiles().forEach(parsed -> parsed.imports().forEach(i -> words(i.module(), sdkWords)));
        dependencies.forEach(d -> words(d, sdkWords));

        Checks checks = new Checks(snapshot.root(), code.toString(), python.toString(), scripts, dependencies, snapshot.files(),
                sdkWords, repoName(snapshot.source()));
        List<Finding> findings = new ArrayList<>();
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("docsScanned", docs);
        findings.addAll(capped(checks.envVars(claims, stats), "env-var", warnings));
        findings.addAll(capped(checks.scripts(claims, stats), "missing-script", warnings));
        findings.addAll(capped(checks.paths(claims, stats), "missing-file", warnings));

        context.traces().record(new Database.Trace(NAME, 0, "stats", null, null, stats.toString(), null, null, null, null));
        return new Report(findings, stats, warnings);
    }

    private static List<Finding> capped(List<Finding> findings, String what, List<String> warnings) {
        if (findings.size() <= MAX_PER_CATEGORY) return findings;
        warnings.add("Spec-Drift found " + findings.size() + " " + what + " issues and reports the first " + MAX_PER_CATEGORY + ".");
        return findings.subList(0, MAX_PER_CATEGORY);
    }

    private static String read(SourceFile file) {
        try {
            return new String(Files.readAllBytes(file.absolutePath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static void words(String name, Set<String> into) {
        if (name == null) return;
        for (String word : name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) if (!word.isEmpty()) into.add(word);
    }

    private static String repoName(String source) {
        String trimmed = source.replaceAll("[/\\\\]+$", "").replaceAll("\\.git$", "");
        return trimmed.substring(Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\')) + 1);
    }

    private static Set<String> scriptNames(String packageJson) {
        Set<String> names = new HashSet<>();
        try {
            JsonNode root = JSON.readTree(packageJson);
            if (root != null) root.path("scripts").fieldNames().forEachRemaining(names::add);
        } catch (IOException ignored) {
            // A broken package.json is Dead-Dep's problem to report.
        }
        return names;
    }

    /** The individual checks, kept apart from file loading so tests can feed them directly. */
    static final class Checks {
        private final Path root;
        private final String code;
        private final String python;
        private final Set<String> scripts;
        private final Set<String> dependencies;
        private final Set<String> knownFiles = new HashSet<>();
        private final Set<String> sdkWords;
        private final String repoName;

        Checks(Path root, String code, String python, Set<String> scripts, Set<String> dependencies, List<SourceFile> files,
               Set<String> sdkWords, String repoName) {
            this.root = root;
            this.code = code;
            this.python = python;
            this.scripts = scripts;
            this.dependencies = dependencies;
            this.sdkWords = sdkWords;
            this.repoName = repoName;
            for (SourceFile file : files) knownFiles.add(file.path());
        }

        // ---- environment variables ---------------------------------------------------

        List<Finding> envVars(List<Claim> claims, Map<String, Integer> stats) {
            Map<String, List<Claim>> byName = group(claims, Type.ENV_VAR);
            List<Finding> findings = new ArrayList<>();
            int unread = 0;
            for (Map.Entry<String, List<Claim>> entry : byName.entrySet()) {
                String name = entry.getKey();
                if (isPlatform(name) || isRead(name)) continue;
                String sdkWord = SDK_KEYS.get(name);
                if (sdkWord != null && usesSdk(sdkWord)) continue; // the provider's SDK reads it
                String provider = providerOf(name);
                if (provider != null && usesSdk(provider)) continue; // e.g. NETMIND_API_KEY with langchain_netmind imported
                unread++;
                Claim first = entry.getValue().get(0);
                String renamed = publicName(name);
                if (renamed != null) {
                    findings.add(new Finding(NAME, "env-var", Severity.MEDIUM, Confidence.HIGH, DecidedBy.STATIC,
                            "Docs name the wrong variable: " + name + " (code reads " + renamed + ")",
                            "The docs tell readers to set " + name + ", but the code reads " + renamed
                                    + ". Following the docs leaves the setting empty, and the framework only exposes the prefixed name to browser code.",
                            first.doc(), first.line(), evidence(entry.getValue()),
                            "Change the docs to " + renamed + ".", fingerprint("env-var|" + name)));
                    continue;
                }
                String sdk = sdkThatMayReadIt(name);
                if (sdk == null && sdkWord == null) sdk = providerMentioned(name);
                boolean dynamic = builtDynamically(name);
                boolean sure = sdk == null && !dynamic;
                String why = sure
                        ? "The docs tell readers to set " + name + ", but no code, config or script in the repo mentions it. Either the setting was"
                                + " removed or renamed, or a separate tool the docs describe reads it."
                        : "The docs tell readers to set " + name + " and no code mentions it by name, but "
                                + (sdk != null ? "the " + sdk + " may read it on its own." : "the code builds variable names at runtime, so it may still be read.");
                // Always a lead, never a likely issue: on 5 unseen repos only 4 of 15 were real, because docs
                // often describe settings read by a separate tool or service the repo doesn't contain.
                findings.add(new Finding(NAME, "env-var", Severity.MEDIUM, sure ? Confidence.MEDIUM : Confidence.LOW,
                        DecidedBy.UNVERIFIED,
                        "Documented variable is never read: " + name, why, first.doc(), first.line(), evidence(entry.getValue()),
                        "If the setting was removed, delete it from the docs. If it was renamed, document the new name.",
                        fingerprint("env-var|" + name)));
            }
            stats.put("envVarsDocumented", byName.size());
            stats.put("envVarsNeverRead", unread);
            return findings;
        }

        private String publicName(String name) {
            for (String prefix : PUBLIC_PREFIXES) {
                if (Pattern.compile("\\b" + Pattern.quote(prefix + name) + "\\b").matcher(code).find()) return prefix + name;
            }
            return null;
        }

        /** "mistral" also matches the import word "mistralai". */
        private boolean usesSdk(String word) {
            if (sdkWords.contains(word)) return true;
            if (word.length() < 4) return false;
            for (String w : sdkWords) if (w.startsWith(word)) return true;
            return false;
        }

        private static String providerOf(String name) {
            java.util.regex.Matcher m = Pattern.compile("^([A-Z0-9]+)_API_(KEY|TOKEN|BASE|URL)$").matcher(name);
            if (!m.matches() || m.group(1).length() < 3) return null;
            return m.group(1).toLowerCase(Locale.ROOT);
        }

        /** FOO_API_KEY where the code names "foo" as a provider string: an SDK we can't see may read it. */
        private String providerMentioned(String name) {
            String provider = providerOf(name);
            if (provider == null) return null;
            return Pattern.compile("[\"']" + Pattern.quote(provider) + "[\"']").matcher(code).find() ? provider + " SDK" : null;
        }

        private static boolean isPlatform(String name) {
            if (PLATFORM_ENV.contains(name)) return true;
            for (String prefix : PLATFORM_PREFIXES) if (name.startsWith(prefix)) return true;
            return false;
        }

        boolean isRead(String name) {
            if (Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(code).find()) return true;
            // pydantic-settings maps the field openai_api_key to OPENAI_API_KEY, sometimes behind an env_prefix.
            for (int i = name.indexOf('_'); i >= 0; i = name.indexOf('_', i + 1)) {
                String prefix = name.substring(0, i + 1);
                String field = name.substring(i + 1).toLowerCase(Locale.ROOT);
                if (code.contains("\"" + prefix + "\"") || code.contains("'" + prefix + "'")) {
                    if (Pattern.compile("\\b" + Pattern.quote(field) + "\\b").matcher(python).find()) return true;
                }
            }
            return Pattern.compile("\\b" + Pattern.quote(name.toLowerCase(Locale.ROOT)) + "\\s*:").matcher(python).find();
        }

        private String sdkThatMayReadIt(String name) {
            for (Map.Entry<String, List<String>> entry : SDK_PREFIXES.entrySet()) {
                if (!name.startsWith(entry.getKey())) continue;
                for (String library : entry.getValue()) if (dependencies.contains(library)) return library;
            }
            String first = name.substring(0, name.indexOf('_')).toLowerCase(Locale.ROOT);
            for (String dependency : dependencies) {
                for (String token : dependency.split("[-_./@]")) if (token.equals(first) && first.length() > 2) return dependency;
            }
            return null;
        }

        /** f"{provider}_API_KEY", `${name}_URL`, prefix + "_TOKEN" and similar. */
        boolean builtDynamically(String name) {
            for (int i = name.indexOf('_'); i > 0; i = name.indexOf('_', i + 1)) {
                String suffix = Pattern.quote(name.substring(i));
                String prefix = Pattern.quote(name.substring(0, i + 1));
                if (Pattern.compile("\\}" + suffix + "\\b|\\+\\s*[\"']" + suffix + "[\"']|[\"']" + prefix + "[\"']\\s*\\+|[\"']" + prefix + "\\{|" + prefix + "\\$\\{")
                        .matcher(code).find()) return true;
            }
            return false;
        }

        // ---- package scripts ---------------------------------------------------------

        List<Finding> scripts(List<Claim> claims, Map<String, Integer> stats) {
            Map<String, List<Claim>> byName = group(claims, Type.SCRIPT);
            List<Finding> findings = new ArrayList<>();
            int missing = 0;
            for (Map.Entry<String, List<Claim>> entry : byName.entrySet()) {
                String script = entry.getKey();
                if (scripts.contains(script)) continue;
                List<Claim> uses = entry.getValue();
                boolean viaRun = uses.stream().anyMatch(c -> c.runner().endsWith(" run") || c.runner().equals("npm"));
                // `pnpm vitest` and `yarn prisma` run a package's own command, not a script.
                if (!viaRun && (dependencies.contains(script) || scripts.isEmpty())) continue;
                if (script.equals("start") && knownFiles.contains("server.js")) continue; // npm start falls back to it
                missing++;
                Claim first = uses.get(0);
                findings.add(new Finding(NAME, "missing-script", Severity.MEDIUM, viaRun ? Confidence.HIGH : Confidence.MEDIUM,
                        viaRun ? DecidedBy.STATIC : DecidedBy.UNVERIFIED,
                        "Documented command has no script: " + first.runner() + " " + script,
                        "The docs tell readers to run `" + first.runner() + " " + script + "`, but no package.json in the repo defines a \""
                                + script + "\" script, so the command fails" + (viaRun ? "." : " unless a tool with that name is installed globally."),
                        first.doc(), first.line(), evidence(uses),
                        "Add the script to package.json, or update the docs to the command that replaced it.",
                        fingerprint("missing-script|" + script)));
            }
            stats.put("scriptsDocumented", byName.size());
            stats.put("scriptsMissing", missing);
            return findings;
        }

        // ---- files -------------------------------------------------------------------

        List<Finding> paths(List<Claim> claims, Map<String, Integer> stats) {
            List<Finding> findings = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int checked = 0;
            for (Claim claim : claims) {
                if (claim.type() != Type.LINK && claim.type() != Type.COMMAND_FILE) continue;
                List<String> candidates = claim.type() == Type.LINK ? linkTargets(claim) : commandTargets(claim);
                if (candidates.isEmpty()) continue;
                checked++;
                if (candidates.stream().anyMatch(this::exists)) continue;
                if (!seen.add(claim.doc() + "|" + claim.value())) continue;
                boolean link = claim.type() == Type.LINK;
                findings.add(new Finding(NAME, "missing-file", link ? Severity.LOW : Severity.MEDIUM, Confidence.HIGH, DecidedBy.STATIC,
                        (link ? "Broken link: " : "Documented command runs a missing file: ") + claim.value(),
                        link ? "This link points at a file that isn't in the repo, so readers get a 404."
                                : "The docs tell readers to run " + claim.value() + ", but that file isn't in the repo"
                                        + (claim.base().isEmpty() ? "." : " (looked in " + claim.base() + "/ after the doc's cd)."),
                        claim.doc(), claim.line(), List.of(claim.doc() + ":" + claim.line() + "  " + shorten(claim.text())),
                        link ? "Point the link at the file's new location, or remove it." : "Update the command to the script's current path.",
                        fingerprint("missing-file|" + claim.doc() + "|" + claim.value())));
            }
            stats.put("pathsChecked", checked);
            stats.put("pathsMissing", findings.size());
            return findings;
        }

        /** Repo-relative paths the link could mean. Empty when it's not a file link we can judge. */
        private List<String> linkTargets(Claim claim) {
            String target = claim.value();
            String lower = target.toLowerCase(Locale.ROOT);
            if (lower.matches("^[a-z][a-z0-9+.-]*:.*") || target.startsWith("#") || target.startsWith("//")
                    || target.startsWith("{") || target.contains("{{") || target.contains("<")) return List.of();
            target = target.replaceAll("[#?].*$", "");
            if (target.isEmpty()) return List.of();
            target = URLDecoder.decode(target.replace("+", "%2B"), StandardCharsets.UTF_8);
            String name = target.substring(target.lastIndexOf('/') + 1);
            // Docs sites route extensionless links like /docs/intro, which aren't files.
            if (!name.contains(".") && !target.endsWith("/")) return List.of();
            boolean readme = claim.doc().toLowerCase(Locale.ROOT).matches("(.*/)?readme[^/]*\\.md");
            if (target.startsWith("/")) return readme ? judgeable(List.of(normalize("", target.substring(1)))) : List.of();
            String docDir = claim.doc().contains("/") ? claim.doc().substring(0, claim.doc().lastIndexOf('/')) : "";
            return judgeable(List.of(normalize(docDir, target)));
        }

        /** Drops paths that lead outside the repo, such as GitHub's ../../actions badge links. */
        private static List<String> judgeable(List<String> paths) {
            return paths.stream().filter(p -> !p.startsWith("/")).toList();
        }

        private List<String> commandTargets(Claim claim) {
            String target = claim.value();
            if (target.startsWith("/") || target.startsWith("~") || target.contains("$") || target.contains("<")) return List.of();
            if (target.matches("(?i)(path/to|your|my|example)[^/]*/.*")) return List.of();
            String base = claim.base();
            if (base.startsWith("/")) return List.of(); // the doc cd'd outside the repo
            if (!base.isEmpty() && !Files.isDirectory(root.resolve(base), LinkOption.NOFOLLOW_LINKS)) {
                // `git clone ...this repo; cd repo` means the root. Any other unknown folder is another project.
                if (base.equals(repoName)) base = "";
                else if (base.startsWith(repoName + "/")) base = base.substring(repoName.length() + 1);
                else return List.of();
            }
            // The doc may expect the reader to already be in the folder that holds the file.
            String suffix = "/" + (target.startsWith("./") ? target.substring(2) : target);
            if (knownFiles.stream().anyMatch(f -> f.endsWith(suffix))) return List.of();
            String docDir = claim.doc().contains("/") ? claim.doc().substring(0, claim.doc().lastIndexOf('/')) : "";
            List<String> options = new ArrayList<>();
            options.add(normalize(base, target));
            options.add(normalize(docDir, target));
            // Also the repo root: after `git clone` and `cd some-repo`, commands run there.
            options.add(normalize("", target));
            return judgeable(options);
        }

        private static String normalize(String base, String relative) {
            String joined = DocClaims.cd(base, relative.startsWith("./") ? relative.substring(2) : relative);
            return joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
        }

        private boolean exists(String repoRelative) {
            if (repoRelative == null) return true;
            if (knownFiles.contains(repoRelative)) return true;
            Path resolved = root.resolve(repoRelative).normalize();
            if (!resolved.startsWith(root)) return true; // points outside the repo: not ours to judge
            return Files.exists(resolved, LinkOption.NOFOLLOW_LINKS);
        }
    }

    // ---- helpers ------------------------------------------------------------------------

    private static Map<String, List<Claim>> group(List<Claim> claims, Type type) {
        Map<String, List<Claim>> byValue = new LinkedHashMap<>();
        for (Claim claim : claims) if (claim.type() == type) byValue.computeIfAbsent(claim.value(), k -> new ArrayList<>()).add(claim);
        return byValue;
    }

    private static List<String> evidence(List<Claim> claims) {
        return claims.stream().map(c -> c.doc() + ":" + c.line() + "  " + shorten(c.text())).distinct().limit(5).toList();
    }

    private static String shorten(String text) {
        return text.length() <= 140 ? text : text.substring(0, 137) + "...";
    }

    static String fingerprint(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
