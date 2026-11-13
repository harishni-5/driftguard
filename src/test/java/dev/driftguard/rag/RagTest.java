package dev.driftguard.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.driftguard.ingest.RepoIngestor;
import dev.driftguard.ingest.RepoSnapshot;
import dev.driftguard.ingest.RepoSource;
import dev.driftguard.llm.LlmClient;
import dev.driftguard.llm.LlmClient.LlmException;
import dev.driftguard.store.Database;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RagTest {

    @TempDir
    Path dir;

    private RepoSnapshot repo(Map<String, String> files) throws Exception {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = dir.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
        try (RepoSource source = RepoSource.open(dir.toString())) {
            return RepoIngestor.ingest(source);
        }
    }

    private static final String AUTH = """
            import re

            EMAIL = re.compile(r"[^@]+@[^@]+")


            def validate_email(address):
                if not EMAIL.match(address):
                    raise ValueError("bad email")
                return address.lower()


            def hash_password(password, salt):
                import hashlib
                digest = hashlib.sha256((salt + password).encode())
                return digest.hexdigest()
            """;

    private static final String DOCS = """
            # Project

            Intro text for the project.

            ## Deploying

            Run the deploy script with your cloud credentials.
            Then check the health endpoint.
            """;

    /** Same words, same vector: enough to test caching and ranking without a real model. */
    static final class WordEmbedder implements Embedder {
        int documentCalls;
        int embedded;
        String failWith;

        @Override
        public String model() {
            return "test-words";
        }

        @Override
        public List<float[]> embedDocuments(List<String> texts) throws LlmException {
            if (failWith != null) throw new LlmException(failWith);
            documentCalls++;
            embedded += texts.size();
            return texts.stream().map(WordEmbedder::vector).toList();
        }

        @Override
        public float[] embedQuery(String text) {
            return vector(text);
        }

        static float[] vector(String text) {
            float[] v = new float[64];
            for (String token : Tokens.of(text)) v[Math.floorMod(token.hashCode(), 64)] += 1;
            return v;
        }
    }

    // ---- chunking ---------------------------------------------------------------------

    @Test
    void chunksFollowFunctionsAndDocHeadings() throws Exception {
        List<Chunk> chunks = Chunker.chunk(repo(Map.of("app/auth.py", AUTH, "README.md", DOCS)));

        List<String> names = chunks.stream().map(Chunk::name).toList();
        assertTrue(names.contains("validate_email"), names.toString());
        assertTrue(names.contains("hash_password"), names.toString());
        assertTrue(names.contains("Deploying"), names.toString());
        Chunk validate = chunks.stream().filter(c -> c.name().equals("validate_email")).findFirst().orElseThrow();
        assertEquals(6, validate.startLine());
        assertEquals(9, validate.endLine());
        assertTrue(chunks.stream().anyMatch(c -> c.path().equals("app/auth.py") && c.name().isEmpty()
                && c.text().contains("EMAIL = re.compile")), "module-level code is kept as its own chunk");
    }

    @Test
    void longFunctionsAreWindowedAndTemplatesSkipped() throws Exception {
        StringBuilder big = new StringBuilder("def huge():\n");
        for (int i = 0; i < 200; i++) big.append("    step_").append(i).append(" = compute(").append(i).append(")\n");
        List<Chunk> chunks = Chunker.chunk(repo(Map.of("app/big.py", big.toString(),
                "templates/app/main.py", "def generated():\n    return 1\n\n\ndef other():\n    return 2\n")));

        List<Chunk> huge = chunks.stream().filter(c -> c.name().equals("huge")).toList();
        assertTrue(huge.size() >= 4, "200 lines split into overlapping windows: " + huge.size());
        assertTrue(huge.stream().allMatch(c -> c.lines().size() <= Chunker.WINDOW));
        assertTrue(chunks.stream().noneMatch(c -> c.path().startsWith("templates/")), "templates aren't indexed");
    }

    // ---- search -----------------------------------------------------------------------

    @Test
    void keywordSearchFindsCodeFromAPlainQuestion() throws Exception {
        RagIndex index = RagIndex.build(repo(Map.of("app/auth.py", AUTH, "README.md", DOCS)), null, null, m -> {});

        assertFalse(index.hasVectors());
        assertEquals("validate_email", index.search("how are email addresses validated?", 3).get(0).chunk().name());
        assertEquals("Deploying", index.search("how do I deploy this", 3).get(0).chunk().name());
    }

    @Test
    void hybridSearchUsesBothRankingsAndCachesVectors() throws Exception {
        RepoSnapshot snapshot = repo(Map.of("app/auth.py", AUTH, "README.md", DOCS));
        try (Database db = Database.inMemory()) {
            WordEmbedder embedder = new WordEmbedder();
            RagIndex first = RagIndex.build(snapshot, embedder, db, m -> {});
            assertTrue(first.hasVectors());
            int chunks = first.chunks().size();
            assertEquals(chunks, embedder.embedded);

            RagIndex.Hit top = first.search("hash a password with salt", 3).get(0);
            assertEquals("hash_password", top.chunk().name());
            assertTrue(top.keywordRank() > 0 && top.vectorRank() > 0, "found by both: " + top);

            WordEmbedder again = new WordEmbedder();
            RagIndex.build(snapshot, again, db, m -> {});
            assertEquals(0, again.embedded, "unchanged chunks come from the cache");
            assertEquals(chunks, db.count("embeddings"));
        }
    }

    @Test
    void embeddingFailureFallsBackToKeywords() throws Exception {
        WordEmbedder broken = new WordEmbedder();
        broken.failWith = "HTTP 429";
        RagIndex index = RagIndex.build(repo(Map.of("app/auth.py", AUTH)), broken, null, m -> {});

        assertFalse(index.hasVectors());
        assertTrue(index.warnings().get(0).contains("keywords only"), index.warnings().toString());
        assertEquals("validate_email", index.search("validate email", 1).get(0).chunk().name());
    }

    @Test
    void filtersAndModesAreRespected() throws Exception {
        RagIndex index = RagIndex.build(repo(Map.of("app/auth.py", AUTH, "README.md", DOCS)), new WordEmbedder(), null, m -> {});

        List<RagIndex.Hit> docsOnly = index.search("password email deploy", 10, RagIndex.Mode.HYBRID, c -> c.kind() == Chunk.Kind.DOC);
        assertTrue(docsOnly.stream().allMatch(h -> h.chunk().kind() == Chunk.Kind.DOC));
        assertTrue(index.search("password", 5, RagIndex.Mode.VECTOR, c -> true).stream().allMatch(h -> h.keywordRank() == 0));
        assertTrue(index.search("password", 5, RagIndex.Mode.KEYWORD, c -> true).stream().allMatch(h -> h.vectorRank() == 0));
    }

    @Test
    void tokensSplitIdentifiersAndPlurals() {
        assertEquals(List.of("get", "user", "name", "getusername"), Tokens.of("getUserName"));
        assertTrue(Tokens.of("dependencies").contains("dependency"));
        assertTrue(Tokens.of("validate_email").containsAll(List.of("validate", "email", "validateemail")));
    }

    // ---- answering --------------------------------------------------------------------

    private static final class FakeLlm implements LlmClient {
        final Answerer.Reply reply;
        String system;
        String user;

        FakeLlm(Answerer.Reply reply) {
            this.reply = reply;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Result<T> structured(String system, String user, Class<T> type) {
            this.system = system;
            this.user = user;
            return new Result<>((T) reply, 100, 20, 0, 1);
        }

        @Override
        public String model() {
            return "fake";
        }
    }

    @Test
    void answersCiteRealSourcesOnlyAndMaskSecrets() throws Exception {
        RagIndex index = RagIndex.build(repo(Map.of("app/auth.py", AUTH,
                "app/keys.py", "def client():\n    key = \"FAKE0TEST0VALUE0abcdefghijklmn\"\n    return connect(key)\n")), null, null, m -> {});
        FakeLlm llm = new FakeLlm(new Answerer.Reply("Emails are checked by validate_email [1], see also [9].", List.of(1, 9), true));

        Answerer.Answer answer = Answerer.ask(llm, "how is the email checked? and the client key", index.search("email client key", 5));

        assertEquals(List.of(1), answer.cited(), "[9] doesn't exist, so it's dropped");
        assertTrue(answer.answerable());
        assertTrue(llm.system.contains("untrusted"));
        assertTrue(llm.user.contains("<sources>") && llm.user.contains("[1] app/"), llm.user);
        assertFalse(llm.user.contains("FAKE0TEST0VALUE0abcdefghijklmn"), "secret-like strings are masked before sending");
    }

    @Test
    void unanswerableRepliesAreFlagged() throws Exception {
        RagIndex index = RagIndex.build(repo(Map.of("app/auth.py", AUTH)), null, null, m -> {});
        FakeLlm llm = new FakeLlm(new Answerer.Reply("The excerpts don't show billing.", List.of(), false));

        assertFalse(Answerer.ask(llm, "how does billing work?", index.search("email", 3)).answerable());
    }

    @Test
    void sourcesStayInsideTheBudget() throws Exception {
        List<RagIndex.Hit> hits = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 200; i++) lines.add("line " + i + " " + "x".repeat(80));
        for (int i = 0; i < 20; i++) hits.add(new RagIndex.Hit(new Chunk(i, "f" + i + ".py", Chunk.Kind.CODE, "", 1, 200, lines), 1, 1, 0));
        List<RagIndex.Hit> shown = new ArrayList<>();
        String rendered = Answerer.renderSources(hits, shown);

        assertTrue(rendered.length() <= Answerer.MAX_TOTAL_CHARS + Answerer.MAX_SOURCE_CHARS, "length " + rendered.length());
        assertTrue(shown.size() < hits.size());
    }

    // ---- embedders ----------------------------------------------------------------------

    @Test
    void factoryOnlyUsesWhatIsAvailable() {
        EmbedderFactory factory = new EmbedderFactory(name -> null, "http://127.0.0.1:9");
        assertTrue(factory.create("none").isEmpty());
        assertTrue(factory.create("auto").isEmpty(), "no Ollama and no key: keywords only");
        assertTrue(factory.create("local").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> factory.create("gemini"));
        assertThrows(IllegalArgumentException.class, () -> factory.create("ollama"));
        assertThrows(IllegalArgumentException.class, () -> factory.create("openai"));
        assertEquals("gemini-embedding-001",
                new EmbedderFactory(name -> name.equals("GEMINI_API_KEY") ? "k" : null, "http://127.0.0.1:9").create("auto").orElseThrow().model());
    }

    private HttpServer server(String path, String response, AtomicReference<String> body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                    + "|key=" + exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    void ollamaStyleEmbeddingsUseTaskPrefixesAndKeepOrder() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server("/v1/embeddings",
                "{\"data\":[{\"index\":1,\"embedding\":[0,1]},{\"index\":0,\"embedding\":[1,0]}]}", body);
        try {
            Embedder embedder = new HttpEmbedder(HttpEmbedder.Style.OPENAI, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    null, "nomic-embed-text", "search_document: ", "search_query: ", 32, false);
            List<float[]> vectors = embedder.embedDocuments(List.of("first", "second"));
            assertEquals(1.0f, vectors.get(0)[0], "re-ordered by index");
            assertTrue(body.get().contains("search_document: first"), body.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void geminiEmbeddingsSendTaskTypeAndKeyHeader() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server("/v1beta/models/gemini-embedding-001:batchEmbedContents",
                "{\"embeddings\":[{\"values\":[0.5,0.5]}]}", body);
        try {
            Embedder embedder = new HttpEmbedder(HttpEmbedder.Style.GEMINI, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta",
                    "secret-key", "gemini-embedding-001", "", "", 50, true);
            assertEquals(2, embedder.embedQuery("where is auth").length);
            assertTrue(body.get().contains("\"taskType\":\"RETRIEVAL_QUERY\""), body.get());
            assertTrue(body.get().contains("\"outputDimensionality\":768"), body.get());
            assertTrue(body.get().endsWith("|key=secret-key"), "key goes in a header, never the URL");
        } finally {
            server.stop(0);
        }
    }

    // ---- retrieval eval -------------------------------------------------------------------

    @Test
    void retrievalEvalScoresTheFirstRelevantFileAndDemotesTests() throws Exception {
        RepoSnapshot snapshot = repo(Map.of("app/auth.py", AUTH,
                "tests/test_auth.py", "def test_validate_email():\n    assert validate_email('a@b.c') == 'a@b.c'\n    assert validate_email('A@B.C')\n"));
        RagIndex index = RagIndex.build(snapshot, null, null, m -> {});

        List<RagIndex.Hit> hits = index.search("validate email", 5);
        assertEquals("app/auth.py", hits.get(0).chunk().path(), "the code outranks its test: " + hits);
        assertEquals(1, RetrievalEval.rankOfFirstRelevant(hits, List.of("app/auth.py")));
        assertEquals(0, RetrievalEval.rankOfFirstRelevant(hits, List.of("nowhere.py")));
    }
}
