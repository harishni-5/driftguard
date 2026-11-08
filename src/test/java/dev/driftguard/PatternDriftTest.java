package dev.driftguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.driftguard.agents.AuditAgent;
import dev.driftguard.agents.Finding;
import dev.driftguard.agents.Finding.DecidedBy;
import dev.driftguard.agents.patterndrift.PatternDriftAgent;
import dev.driftguard.agents.patterndrift.PatternDriftAgent.Decision;
import dev.driftguard.agents.patterndrift.PatternDriftAgent.GroupReview;
import dev.driftguard.llm.LlmClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatternDriftTest {

    @TempDir
    Path dir;

    private AuditAgent.Report audit(TestRepo repo, LlmClient llm) throws Exception {
        return new PatternDriftAgent().run(new AuditAgent.Context(repo.snapshot(), llm, trace -> {}));
    }

    private static List<Finding> ofCategory(AuditAgent.Report report, String category) {
        return report.findings().stream().filter(f -> f.category().equals(category)).toList();
    }

    private static final class FakeLlm implements LlmClient {
        final GroupReview review;
        final String failure;
        final List<String> prompts = new ArrayList<>();
        boolean advisory = false;

        FakeLlm(GroupReview review, String failure) {
            this.review = review;
            this.failure = failure;
        }

        @Override
        public boolean verdictsAreAdvisory() {
            return advisory;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Result<T> structured(String system, String user, Class<T> type) throws LlmException {
            prompts.add(user);
            if (failure != null) throw new LlmException(failure);
            return new Result<>((T) review, 10, 5, 0, 1);
        }

        @Override
        public String model() {
            return "fake";
        }
    }

    // ---- competing libraries ----------------------------------------------------

    @Test
    void flagsCompetingHttpClientsAndNamesTheMostUsed() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("package.json", "{}\n")
                .file("src/a.js", "import axios from 'axios';\naxios.get('/a');\n")
                .file("src/b.js", "import axios from 'axios';\naxios.get('/b');\n")
                .file("src/c.js", "\nexport const load = () => fetch('/c');\n"), null);

        Finding finding = ofCategory(report, "competing-libraries").get(0);
        assertTrue(finding.title().contains("2 HTTP clients"), finding.title());
        assertTrue(finding.title().contains("axios, fetch"), finding.title());
        assertEquals("src/c.js", finding.filePath(), "points at the odd one out");
        assertEquals(2, finding.startLine());
        assertEquals(DecidedBy.STATIC, finding.decidedBy());
        assertEquals(Finding.Confidence.HIGH, finding.confidence());
        assertTrue(finding.fixSuggestion().startsWith("axios is the most used"), finding.fixSuggestion());
        assertTrue(finding.evidence().get(0).startsWith("axios (2 files)"), finding.evidence().toString());
    }

    @Test
    void threeApproachesIsMoreSevere() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("a.py", "import requests\n")
                .file("b.py", "import httpx\n")
                .file("c.py", "from aiohttp import ClientSession\n"), null);

        assertEquals(Finding.Severity.HIGH, ofCategory(report, "competing-libraries").get(0).severity());
    }

    @Test
    void oneApproachIsFine() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("a.js", "import axios from 'axios';\n")
                .file("b.js", "import axios from 'axios';\n"), null);

        assertTrue(ofCategory(report, "competing-libraries").isEmpty());
    }

    @Test
    void frontendAndBackendAreSeparateProjects() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("client/package.json", "{}\n")
                .file("client/api.js", "import axios from 'axios';\n")
                .file("server/package.json", "{}\n")
                .file("server/api.js", "const got = require('got');\n"), null);

        assertTrue(ofCategory(report, "competing-libraries").isEmpty());
    }

    @Test
    void testsAndExamplesMayDoThingsDifferently() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("app/client.py", "import requests\n")
                .file("tests/test_client.py", "import httpx\n")
                .file("test_other.py", "import aiohttp\n")
                .file("examples/demo.py", "import urllib.request\n"), null);

        assertTrue(ofCategory(report, "competing-libraries").isEmpty());
    }

    @Test
    void printIsNormalUnlessARealLoggerIsAlsoUsed() throws Exception {
        assertTrue(ofCategory(audit(new TestRepo(dir)
                .file("a.py", "print('a')\n")
                .file("b.py", "print('b')\n"), null), "competing-libraries").isEmpty());

        new TestRepo(dir).file("c.py", "import logging\nlogging.info('c')\n");
        Finding finding = ofCategory(audit(new TestRepo(dir), null), "competing-libraries").get(0);
        assertTrue(finding.title().contains("logging, print") || finding.title().contains("print, logging"), finding.title());
        assertEquals(Finding.Severity.LOW, finding.severity());
    }

    @Test
    void aSinglePrintDoesNotCount() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("a.py", "import logging\n")
                .file("b.py", "print('one debug line')\n"), null);

        assertTrue(ofCategory(report, "competing-libraries").isEmpty());
    }

    @Test
    void aProjectsOwnFetchFunctionIsNotTheGlobalFetch() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("a.js", "import axios from 'axios';\nexport function fetch(u) { return axios.get(u); }\n")
                .file("b.js", "import { fetch } from './a';\nfetch('/b');\n"), null);

        assertTrue(ofCategory(report, "competing-libraries").isEmpty());
    }

    @Test
    void subpathImportsCount() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("a.js", "import add from 'date-fns/add';\n")
                .file("b.js", "import moment from 'moment';\n"), null);

        assertEquals(1, ofCategory(report, "competing-libraries").size());
    }

    // ---- similar functions -------------------------------------------------------

    private static final String FORMAT_A = """
            export function formatInvoiceDate(invoice) {
              const invoiceDate = new Date(invoice.createdAt);
              const year = invoiceDate.getFullYear();
              const month = String(invoiceDate.getMonth() + 1).padStart(2, '0');
              const day = String(invoiceDate.getDate()).padStart(2, '0');
              return `${year}-${month}-${day}`;
            }
            """;
    private static final String FORMAT_B = """
            export function invoiceDateToString(invoice) {
              const created = new Date(invoice.createdAt);
              const month = String(created.getMonth() + 1).padStart(2, '0');
              const day = String(created.getDate()).padStart(2, '0');
              const year = created.getFullYear();
              return year + '-' + month + '-' + day;
            }
            """;
    private static final String UNRELATED = """
            export function sendWelcomeEmail(account) {
              const recipient = account.primaryAddress;
              const subject = 'Welcome aboard';
              const template = loadTemplate('welcome');
              const html = template.fill({ recipient, subject });
              return mailer.deliver(recipient, subject, html);
            }
            """;

    private TestRepo driftedRepo() throws Exception {
        return new TestRepo(dir)
                .file("src/billing.js", FORMAT_A)
                .file("src/reports.js", FORMAT_B)
                .file("src/email.js", UNRELATED);
    }

    @Test
    void findsTheSameJobWrittenTwiceButNotUnrelatedCode() throws Exception {
        Finding finding = ofCategory(audit(driftedRepo(), null), "similar-functions").get(0);

        assertTrue(finding.title().contains("formatInvoiceDate"), finding.title());
        assertTrue(finding.title().contains("invoiceDateToString"), finding.title());
        assertFalse(finding.title().contains("sendWelcomeEmail"), finding.title());
    }

    @Test
    void withoutAnLlmTheyAreLeadsNotFacts() throws Exception {
        Finding finding = ofCategory(audit(driftedRepo(), null), "similar-functions").get(0);

        assertEquals(DecidedBy.UNVERIFIED, finding.decidedBy());
        assertEquals(Finding.Confidence.LOW, finding.confidence());
        assertEquals(Finding.Severity.LOW, finding.severity());
    }

    @Test
    void llmConfirmationMakesItALikelyIssue() throws Exception {
        FakeLlm llm = new FakeLlm(new GroupReview(Decision.SAME_JOB, "Formats an invoice date", "Only variable names"), null);
        AuditAgent.Report report = audit(driftedRepo(), llm);

        Finding finding = ofCategory(report, "similar-functions").get(0);
        assertEquals(DecidedBy.LLM, finding.decidedBy());
        assertEquals(Finding.Severity.MEDIUM, finding.severity());
        assertTrue(finding.title().startsWith("Same job done 2 times"), finding.title());
        assertTrue(finding.description().contains("Formats an invoice date"));
        assertEquals(1, report.stats().get("llmConfirmedSameJob"));
        assertTrue(llm.prompts.get(0).contains("padStart"), "the LLM sees the code");
    }

    @Test
    void localModelsCanCommentButNotConfirm() throws Exception {
        FakeLlm llm = new FakeLlm(new GroupReview(Decision.SAME_JOB, "Formats an invoice date", null), null);
        llm.advisory = true;
        AuditAgent.Report report = audit(driftedRepo(), llm);

        Finding finding = ofCategory(report, "similar-functions").get(0);
        assertEquals(DecidedBy.UNVERIFIED, finding.decidedBy());
        assertTrue(finding.title().startsWith("Possibly"), finding.title());
        assertTrue(finding.description().contains("SAME_JOB"), "the opinion is still shown");
        assertEquals(0, report.stats().get("llmConfirmedSameJob"));
        assertEquals(1, report.stats().get("stillUnverified"));
    }

    @Test
    void llmSayingNotDriftKeepsItVisibleButLow() throws Exception {
        for (Decision decision : List.of(Decision.INTENTIONAL_VARIANTS, Decision.DIFFERENT_JOBS, Decision.UNSURE)) {
            FakeLlm llm = new FakeLlm(new GroupReview(decision, null, null), null);
            Finding finding = ofCategory(audit(driftedRepo(), llm), "similar-functions").get(0);
            assertEquals(DecidedBy.UNVERIFIED, finding.decidedBy(), decision.name());
            assertTrue(finding.title().startsWith("Possibly"), finding.title());
            assertTrue(finding.description().contains(decision.name()));
        }
    }

    @Test
    void llmFailureDowngradesInsteadOfCrashing() throws Exception {
        AuditAgent.Report report = audit(driftedRepo(), new FakeLlm(null, "HTTP 429"));

        assertEquals(DecidedBy.UNVERIFIED, ofCategory(report, "similar-functions").get(0).decidedBy());
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("HTTP 429")));
    }

    @Test
    void findsSimilarPythonWrittenInADifferentStyle() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("a.py", """
                        def normalize_customer_phone(customer):
                            raw_phone = customer.phone_number.strip()
                            digits = "".join(ch for ch in raw_phone if ch.isdigit())
                            country_prefix = customer.country_code
                            return f"+{country_prefix}{digits}"
                        """)
                .file("b.py", """
                        def format_customer_phone(customer):
                            raw_phone = customer.phone_number.strip()
                            digits = "".join(filter(str.isdigit, raw_phone))
                            country_prefix = customer.country_code
                            return "+" + country_prefix + digits
                        """), null);

        assertEquals(1, ofCategory(report, "similar-functions").size());
    }

    /** A known limit, kept as a test so it's visible: this needs embeddings to catch. */
    @Test
    void sameJobInVeryDifferentWordsIsNotCaughtYet() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("a.py", """
                        def normalize_customer_phone(customer):
                            raw_phone = customer.phone_number.strip()
                            digits = "".join(ch for ch in raw_phone if ch.isdigit())
                            country_prefix = customer.country_code
                            return f"+{country_prefix}{digits}"
                        """)
                .file("b.py", """
                        def to_e164(person):
                            cleaned = re.sub(r"[^0-9]", "", person.mobile)
                            code = lookup_dialing_code(person.region)
                            joined = code + cleaned
                            return "+" + joined
                        """), null);

        assertTrue(ofCategory(report, "similar-functions").isEmpty());
    }

    @Test
    void ignoresTestsGeneratedUiAndTinyFunctions() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("src/a.js", FORMAT_A)
                .file("tests/a.test.js", FORMAT_B)
                .file("src/components/ui/button.js", FORMAT_B)
                .file("src/x.js", "export function a(q) {\n return q.value;\n}\n")
                .file("src/y.js", "export function a(q) {\n return q.value;\n}\n"), null);

        assertTrue(ofCategory(report, "similar-functions").isEmpty());
    }

    // ---- from noise and misses seen on real repos -----------------------------------

    private static final String BODY = """
                const items = await loadItems(account);
                const active = items.filter(isActive);
                const total = active.reduce(sumPrices, 0);
                const label = formatMoney(total);
                return { label, total };
            }
            """;

    @Test
    void identicalBodiesAreReportedAsFactsWithoutAnLlm() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("a.js", "export async function cartTotal(account) {\n" + BODY)
                .file("b.js", "export async function checkoutSum(account) {\n  // recalculated here\n" + BODY.replace("    const", "\tconst")), null);

        Finding copy = ofCategory(report, "copied-code").get(0);
        assertEquals(DecidedBy.STATIC, copy.decidedBy());
        assertEquals(Finding.Confidence.HIGH, copy.confidence());
        assertTrue(copy.title().contains("cartTotal") && copy.title().contains("checkoutSum"), copy.title());
        assertTrue(ofCategory(report, "similar-functions").isEmpty(), "not reported twice");
    }

    /** Found by auditing DriftGuard itself: its vendored Babel parser was reported as drift. */
    @Test
    void vendoredAndBundledFilesAreNotTheProjectsDrift() throws Exception {
        String hugeBundle = "export async function bundledTotal(account) {\n" + BODY + "// filler\n".repeat(10_001);
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("a.js", "export async function cartTotal(account) {\n" + BODY)
                .file("lib/parser.mjs", "export async function vendoredTotal(account) {\n" + BODY)
                .file("lib/parser.LICENSE", "MIT License\n")
                .file("dist-like/app.bundle-ish.js", hugeBundle), null);

        assertTrue(ofCategory(report, "copied-code").isEmpty(), ofCategory(report, "copied-code").toString());
    }

    @Test
    void docstringsAndCommentsDoNotMakeFunctionsLookAlike() throws Exception {
        String docs = "    \"\"\"Render a template by name with the given context and return the resulting text for the client.\n"
                + "    The template context processors run first and signals fire around rendering as usual.\n    \"\"\"\n";
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("a.py", "def alpha(value):\n" + docs + "    first = value + 1\n    second = first * 2\n    third = second - 3\n    fourth = third / 4\n    return fourth\n")
                .file("b.py", "def beta(items):\n" + docs + "    names = sorted(items)\n    unique = set(names)\n    joined = ','.join(unique)\n    upper = joined.upper()\n    return upper\n"), null);

        assertTrue(ofCategory(report, "similar-functions").isEmpty());
    }

    @Test
    void neighboursInOneFileAreAFamilyNotDrift() throws Exception {
        AuditAgent.Report report = audit(new TestRepo(dir).file("codec.js", """
                export function encodeRecordPayload(record) {
                  const serializer = pickSerializer(record.format);
                  const schema = loadSchema(record.format);
                  const checked = schema.validate(record.payload);
                  return serializer.encode(checked);
                }
                export function decodeRecordPayload(record) {
                  const serializer = pickSerializer(record.format);
                  const schema = loadSchema(record.format);
                  const decoded = serializer.decode(record.payload);
                  return schema.validate(decoded);
                }
                """), null);

        assertTrue(ofCategory(report, "similar-functions").isEmpty());
    }

    @Test
    void frameworkShapedCodeIsNotCompared() throws Exception {
        String route = """
                export async function %s(request) {
                  const session = await getSession(request);
                  const account = await findAccount(session);
                  const permissions = await loadPermissions(account);
                  return respond(account, permissions, %s);
                }
                """;
        String component = """
                export function %s({ account }) {
                  const theme = useTheme();
                  const router = useRouter();
                  const session = useSession(account, %s);
                  return <Panel theme={theme} router={router} session={session} />;
                }
                """;
        AuditAgent.Report report = audit(new TestRepo(dir)
                .file("app/a/route.ts", route.formatted("GET", "1"))
                .file("app/b/route.ts", route.formatted("POST", "2"))
                .file("ui/One.jsx", component.formatted("UserPanel", "1"))
                .file("ui/Two.jsx", component.formatted("ItemPanel", "2")), null);

        assertTrue(ofCategory(report, "similar-functions").isEmpty());
    }

    @Test
    void masksKeyLikeStringsBeforeSendingCode() throws Exception {
        FakeLlm llm = new FakeLlm(new GroupReview(Decision.UNSURE, null, null), null);
        String secret = "sk_live_abcdefghijklmnopqrstuvwxyz123456";
        audit(new TestRepo(dir)
                .file("a.js", FORMAT_A.replace("'0'", "'" + secret + "'"))
                .file("b.js", FORMAT_B), llm);

        assertFalse(llm.prompts.get(0).contains(secret));
        assertTrue(llm.prompts.get(0).contains("<redacted>"));
    }
}
