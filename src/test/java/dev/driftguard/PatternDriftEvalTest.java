package dev.driftguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.driftguard.agents.patterndrift.PatternDriftAgent.Decision;
import dev.driftguard.agents.patterndrift.PatternDriftAgent.GroupReview;
import dev.driftguard.agents.patterndrift.PatternDriftEval;
import dev.driftguard.agents.patterndrift.PatternDriftEval.Label;
import dev.driftguard.agents.patterndrift.PatternDriftEval.LabelFile;
import dev.driftguard.agents.patterndrift.PatternDriftEval.LabeledGroup;
import dev.driftguard.agents.patterndrift.PatternDriftEval.RepoEntry;
import dev.driftguard.llm.LlmClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatternDriftEvalTest {

    @TempDir
    Path dir;

    private static final String LEAVE_A = """
            export function EventPage() {
              const handleLeaveWaitlist = async () => {
                setWaitlistBusy(true);
                try {
                  await leaveWaitlist(eventId);
                  setWaitlistEntry(null);
                  loadData();
                } catch (err) {
                  setErrorMsg(err.response?.data?.error || 'Could not leave the waitlist');
                } finally {
                  setWaitlistBusy(false);
                }
              };
            }
            """;

    private static final String LEAVE_B = """
            export function TicketsPage() {
              const handleLeave = async (entry) => {
                setBusyId(entry.id);
                try {
                  await leaveWaitlist(entry.eventId);
                  await load();
                } catch (err) {
                  setError(err.response?.data?.error || 'Could not leave the waitlist');
                } finally {
                  setBusyId(null);
                }
              };
            }
            """;

    private static final List<String> WAITLIST_GROUP = List.of(
            "src/EventPage.jsx::EventPage.handleLeaveWaitlist", "src/TicketsPage.jsx::TicketsPage.handleLeave");

    /** Answers every group the same way and counts calls. */
    private static final class FixedLlm implements LlmClient {
        final Decision decision;
        final List<String> prompts = new ArrayList<>();
        final List<String> systems = new ArrayList<>();

        FixedLlm(Decision decision) {
            this.decision = decision;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Result<T> structured(String system, String user, Class<T> type) {
            prompts.add(user);
            systems.add(system);
            return new Result<>((T) new GroupReview(decision, "leave a waitlist", "state names"), 100, 20, 0, 1);
        }

        @Override
        public String model() {
            return "fixed";
        }
    }

    private LabelFile labels(LabeledGroup... groups) throws Exception {
        new TestRepo(dir).file("src/EventPage.jsx", LEAVE_A).file("src/TicketsPage.jsx", LEAVE_B);
        return new LabelFile("test", List.of(new RepoEntry("app", dir.toString(), null, List.of(groups))));
    }

    @Test
    void agreeingWithTheLabelIsATruePositive() throws Exception {
        FixedLlm llm = new FixedLlm(Decision.SAME_JOB);
        PatternDriftEval.Outcome outcome = PatternDriftEval.run(
                labels(new LabeledGroup(WAITLIST_GROUP, Label.SAME_JOB, "real")), null, llm, step -> {});

        PatternDriftEval.Score score = outcome.score();
        assertEquals(1, score.truePositive());
        assertEquals(1.0, score.precision());
        assertEquals(1.0, score.recall());
        assertEquals(1, llm.prompts.size());
        assertTrue(llm.prompts.get(0).startsWith("<candidates>"), "uses the agent's real prompt");
        assertTrue(PatternDriftEval.report(outcome, "fixed").contains("| app |"));
    }

    @Test
    void missingTheDriftIsAFalseNegativeAndLostGroupsAreReported() throws Exception {
        PatternDriftEval.Outcome outcome = PatternDriftEval.run(labels(
                        new LabeledGroup(WAITLIST_GROUP, Label.SAME_JOB, null),
                        new LabeledGroup(List.of("gone.js::a", "gone.js::b"), Label.DIFFERENT_JOBS, null)),
                null, new FixedLlm(Decision.DIFFERENT_JOBS), step -> {});

        PatternDriftEval.Score score = outcome.score();
        assertEquals(1, score.falseNegative());
        assertEquals(0.0, score.recall());
        assertEquals(1, score.missing());
        assertTrue(outcome.warnings().get(0).contains("no longer found"), outcome.warnings().toString());
    }

    @Test
    void unclearLabelsAreAskedAboutButNotScored() throws Exception {
        PatternDriftEval.Score score = PatternDriftEval.run(
                labels(new LabeledGroup(WAITLIST_GROUP, Label.UNCLEAR, null)), null, new FixedLlm(Decision.SAME_JOB), step -> {}).score();

        assertEquals(0, score.scored());
        assertTrue(Double.isNaN(score.precision()));
    }

    @Test
    void dumpShowsEveryGroupWithItsMembersAndCode() throws Exception {
        String dump = PatternDriftEval.dump(labels(), null, new ArrayList<>());

        assertTrue(dump.contains("\"src/EventPage.jsx::EventPage.handleLeaveWaitlist\""), dump);
        assertTrue(dump.contains("await leaveWaitlist(eventId);"), dump);
    }

    @Test
    void eachPromptVersionSendsItsOwnInstructions() throws Exception {
        FixedLlm llm = new FixedLlm(Decision.SAME_JOB);
        LabelFile file = labels(new LabeledGroup(WAITLIST_GROUP, Label.SAME_JOB, null));

        assertEquals("v1", PatternDriftEval.run(file, null, llm, "v1", step -> {}).promptVersion());
        PatternDriftEval.run(file, null, llm, "v2", step -> {});

        assertTrue(llm.systems.get(0).contains("one adapter per provider"), "v1 wording");
        assertTrue(llm.systems.get(1).contains("Being in separate places is not"), "v2 wording");
        assertTrue(llm.systems.get(1).contains("untrusted data"), "both keep the injection warning");
        assertThrows(IllegalArgumentException.class, () -> PatternDriftEval.run(file, null, llm, "v9", step -> {}));
    }

    private static long count(LabelFile file, Label label) {
        return file.repos().stream().flatMap(r -> r.groups().stream()).filter(g -> g.label() == label).count();
    }

    @Test
    void shippedLabelFilesParse() throws Exception {
        LabelFile dev = PatternDriftEval.read(Path.of("eval/pattern-drift-labels.json"));
        assertEquals(7, dev.repos().size());
        assertEquals(10, count(dev, Label.SAME_JOB));

        LabelFile heldOut = PatternDriftEval.read(Path.of("eval/pattern-drift-heldout.json"));
        assertEquals(5, heldOut.repos().size());
        assertEquals(17, count(heldOut, Label.SAME_JOB));
    }
}
