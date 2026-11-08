# Decisions and plan

Last updated: 2026-11-07.

## The goal

**A strong resume project first. A paper later, only if the results turn out novel.** No paid APIs.

So every feature has to pass one test: can a recruiter see it working in 2 minutes, and can you explain it well in an interview?

## What changed from the original handoff

| Handoff | Now | Why |
|---|---|---|
| Paper-first, ICSE 2027 | **Project-first, paper optional** | Your call. ICSE was out of reach anyway: most workshop deadlines are Nov 13, 2026. |
| Claude API | **Free options: Groq, Gemini, Ollama** | No budget. One client covers all three, and Claude stays available only if asked for by name. |
| "No direct competitors" | **Competitors exist** | [VibeDrift](https://www.producthunt.com/products/vibedrift), [drift-analyzer](https://pypi.org/project/drift-analyzer/2.9.16/) and [Driftpulse](https://hunted.space/product/driftpulse). Don't say "first" or "no competitors" on your resume or in interviews. Knowing the landscape actually sounds better. |
| LangChain4j | **Thin code of our own** | About 150 lines so far. Writing the agent logic yourself also gives you more to talk about in interviews. |
| Tree-sitter | **Dropped** | Python's `ast` and Babel already cover both languages. |
| LLM planner agent | **Fixed order for now** | With 3 agents that always run, it has nothing to decide. |
| 100-repo benchmark | **20-30 repos** | Enough to give you real numbers for your resume. Grow it only if you go for a paper. |

## Build order

Dead deps are already well served by free tools like deptry and depcheck, so Dead-Dep is the warm-up, not the headline. The headline is what those tools can't do.

| When | Build | What it gives your resume |
|---|---|---|
| Done | Ingestion, SQLite store, Dead-Dep, free LLM support | Working foundation |
| Done | **Pattern-Drift**: competing libraries, copied code, and similar functions checked by an LLM | The headline feature |
| Done | Pattern-Drift quality: held-out eval, prompt v2 (recall 31% → 82%) | Numbers you can defend |
| Done (static part) | **Spec-Drift**: links, commands, scripts and variables the docs promise but the code doesn't have | Second "wow" feature; easy to demo |
| Later, optional | Spec-Drift with an LLM: feature claims in prose ("supports PDF export") checked against code. Error-handling style. Embeddings. | Only if time allows |
| Done | **HTML report** (self-contained, filters, GitHub line links, escapes repo text) and a private GitHub repo | Something a recruiter can see |
| Next | Public README with results and a viewable sample report, then make the repo public | The page a recruiter actually lands on |
| Done | **GitHub Action**: audits a PR's base and head, comments with only the drift that PR added, plus CI on Linux and Windows | Shows real-world engineering |
| Dec 29-Jan 4 | **Test on 20-30 real repos** with hand-checked results | Real numbers, such as "X% of findings confirmed by hand on 25 repos" |
| After that, if time allows | Orphan-Code, Q&A, Spring Boot and React web UI | Nice extras; first to cut if you fall behind |

## Paper, if you want one later

A benchmark is just a test set with known answers: real repos where we know which drift humans later fixed, so any tool can be scored against it. I found no independent one for drift tools, which makes that the most novel angle. Finish the project first. Then, if the numbers are good, grow the test set and aim for an FSE 2027 workshop (around Feb 11, 2027) or Ideas, Visions and Reflections (Feb 12, 2027). Dates are from the [FSE 2027 site](https://conf.researchr.org/home/fse-2027). No result can guarantee acceptance.

## Free LLM strategy

- **Embeddings: not yet.** Word similarity needs no model and runs anywhere, including your laptop with no GPU. Add embeddings only if measured misses justify another model download.
- **Reasoning:** Groq free tier by default, since small local models are too weak (see below).
- **Keep every call small:** static analysis filters first, and the LLM only sees what's left. That's what keeps free-tier limits workable, and it's a good design story for interviews.
- **The LLM never deletes a finding.** It can only re-rank one.
- **Local models are advisory.** Their opinions are shown, but they can't confirm a finding.

Free-tier numbers come from third-party write-ups ([Groq](https://benchlm.ai/md/free-tier/groq.md), [Gemini](https://apisrouter.com/gemini-ai-free)), not official docs, and they change often. Check your own limits page if you hit a wall.

## Results so far

These are sanity checks, not numbers to put on a resume yet.

**Dead-Dep static analysis on 6 public repos** (devika, gpt-engineer, flask, full-stack-fastapi-template, chatbot-ui, taxonomy): 403 dependencies checked, 22 called unused with high confidence, 52 sent to review. Depcheck also flagged all 22. A text search found no real reference for 21 of them; `shiki` is a false alarm, because it's a peer dependency of another package. Deptry ran without each project's dependencies installed, so its misses aren't a fair comparison.

**TicketX (`~/Desktop/ticketX1`):** all 26 dependencies are really used, so Dead-Dep has 0 findings, which is correct. Pattern-Drift's word-similarity step flags 2 groups. One is real: `handleLeaveWaitlist` in `EventDetails.jsx` and `handleLeave` in `MyTickets.jsx` do the same job. The other, `Login` vs `Register` `handleSubmit`, is only a lookalike.

**Pattern-Drift static results on 7 repos, after tuning:**

| Repo | Competing libraries | Copied code groups | Similar groups (leads) |
|---|---:|---:|---:|
| chatbot-ui | 1 | 4 | 27 (15 reported) |
| devika | 1 | 3 | 7 |
| flask | 0 | 1 | 6 |
| full-stack-fastapi-template | 1 | 1 | 4 |
| gpt-engineer | 1 | 0 | 3 |
| taxonomy | 0 | 0 | 1 |
| ticketX1 | 0 | 0 | 2 |

Tuning cut Flask from 26 groups to 7. Some findings were checked by reading the code, not all, so there's no precision number yet. Exact copies are facts, but a project can keep a copy on purpose (Flask's `Flask` and `Blueprint` are an example worth reading before calling it a bug).

**Local model on Pattern-Drift:** `qwen2.5:3b-instruct` reviewed 19 groups with about 15k tokens total. It got both TicketX groups wrong and said SAME_JOB on 12 of the 19. That's why local verdicts became advisory. The token count suggests a free cloud tier should be enough, but a stronger model's accuracy is still unmeasured.

**Groq `openai/gpt-oss-120b` on TicketX (2026-11-08):** both groups judged correctly. The waitlist pair came back as SAME_JOB, and `Login` vs `Register` as INTENTIONAL_VARIANTS. It used 2 calls, about 1.8k tokens, at no cost. That's 2 out of 2, which is a first sign, not an accuracy number. (`llama-3.3-70b-versatile` has been retired on Groq, so the default changed.)

**Live LLM test: devika with local `qwen2.5:3b-instruct`:** the whole pipeline worked in 10.8s at no cost. But the answers were weak. It said `colorama` and `mistletoe` were used because `werkzeug` is imported, which makes no sense. It also skipped 2 of the 8 candidates. That's why the LLM can no longer delete findings, and why Groq is the recommended default.

**Hand-labeled eval set (2026-11-08):** all 50 similar-function groups from the 7 repos above, labeled by reading the code before any LLM saw them: 10 SAME_JOB, 27 INTENTIONAL_VARIANTS, 11 DIFFERENT_JOBS and 2 UNCLEAR (left out of the score). So word similarity alone has about 21% precision (10 of 48), and the LLM check has to earn its place against that. Most look-alikes are per-entity data-access functions (chatbot-ui) or deliberate app/blueprint pairs (Flask). The groups are in `eval/pattern-drift-labels.json`, and `driftguard eval` scores any model against them.

**First eval run: Groq `openai/gpt-oss-120b` (2026-11-08):** 48 groups scored, 0 failed calls, about 58k tokens, $0. Full table in [eval/results-groq.md](../eval/results-groq.md).

| | Precision | Recall |
|---|---:|---:|
| Word similarity alone | 21% (10 of 48) | 100% by definition |
| With the LLM check | **71%** (5 of 7 flagged) | **50%** (5 of 10) |

What this does and doesn't show:
- **The sample is small.** Only 7 groups got flagged, so the 71% could plausibly be anywhere from about 35% to 90%. It's a strong first sign, not a settled number.
- **The model is cautious.** All 5 misses were copies the model called INTENTIONAL_VARIANTS. Two are clear misses: the keyboard handler copied into four pickers, and devika's `emulate_code_writing` copied into three agents. The other three sit on the line between "one per entity" and "copied".
- **My labels aren't fully consistent either.** I called per-bucket storage helpers SAME_JOB but per-table `update*` functions INTENTIONAL_VARIANTS, and the model flagged the second. Some of the disagreement is the labels, not the model.
- **Don't tune the prompt on this set and then quote it.** That would be grading your own homework. Any prompt change needs new held-out repos to measure against.

**Prompt v2 and a held-out set (2026-11-08):** to fix low recall without grading my own homework:
1. Wrote the labeling rules down ([eval/LABELING.md](../eval/LABELING.md)).
2. Labeled 34 groups from 5 new repos (vercel/ai-chatbot, gpt-researcher, AgentGPT, fragments, bolt.new) before any model saw them: 17 SAME_JOB, 10 INTENTIONAL_VARIANTS, 6 DIFFERENT_JOBS, 1 UNCLEAR. gpt-researcher supplies 28 of them, so the set leans on one repo, and fragments had none.
3. Wrote prompt v2 from those rules and the first set's misses only. It says plainly that a copy in another file isn't intentional, that drifted error handling is a sign of drift, and that small duplicates count.

Caveat: v2 encodes the same rules the held-out labels were made with. That's the point (the rules define what drift means), but it means the result shows v2 matches *these* rules, not some universal truth.

**Held-out result, Groq `openai/gpt-oss-120b`:** files [results-heldout-v1.md](../eval/results-heldout-v1.md) and [results-heldout-v2.md](../eval/results-heldout-v2.md).

| Prompt | Precision | Recall | Groups scored |
|---|---:|---:|---:|
| Word similarity alone | 52% | 100% | 33 |
| v1 | 71% (5 of 7) | 31% (5 of 16) | 32 (1 call failed on a network reset) |
| **v2** | **82%** (14 of 17) | **82%** (14 of 17) | 33 |

**v2 is kept.** Even if v1 had got its failed group right, its recall would be 35%. About 47k tokens per run, at no cost.

- The baseline is higher here (52% vs 21%) because gpt-researcher has many real copies. So the honest framing is the gain over the baseline on the same set, not one number on its own.
- v2's 3 misses: two near-identical loaders and retriever builders it called intentional, plus the per-provider API-key lookups. Its 3 false alarms: two React memo comparators, two report prompts that share a copied block, and two benchmark agent builders that share a copied tool. In the last two the model saw real copying inside different jobs, which is a fair argument.
- 17 positives is still small. Precision and recall could each plausibly sit anywhere from about 60% to 95%.
- v2 hasn't been rerun on the development set. That score would be optimistic anyway, because v2 was written by looking at those mistakes.

**Resume-ready claim:** "On a held-out set of 33 hand-labeled groups from 5 open-source repos, LLM verification found 82% of duplicated-logic drift at 82% precision, versus 52% precision for similarity alone. A rewritten prompt raised recall from 31% to 82%."

**Spec-Drift, first version (2026-11-08):** static only, no LLM. It was tuned on the 12 repos above, then measured on 5 it had never seen (anything-llm, crawl4ai, browser-use, open-interpreter, firecrawl). Every finding was checked by hand.

| Check, on 5 unseen repos (before any change) | Real / flagged |
|---|---:|
| Broken links | 39 / 39 |
| Commands that run a missing file | 1 / 1 |
| Missing scripts | 0 / 1 (example text in a `text` block) |
| Variables never read | 4 / 15 (27%) |

What changed because of this:
- Variable findings now go under "Needs your review", never "Likely issues". Docs often describe settings that a separate tool reads (browser-use's skill docs alone had 5), and static analysis can't see that. The "docs name the wrong variable" case stays a likely issue, since it shows the exact variable the code reads.
- General fixes: a variable set in one step and used as `$NAME` in a later one is a shell variable; `text` blocks aren't commands; `NAME = value` with spaces is code, not shell.
- With those rules, the "Likely issues" part scored 40 of 41 (98%) on the unseen repos. These changes were made after seeing them, though, so a fresh set is needed to quote a post-change number.
- **Also found a crash:** a Python string containing a lone surrogate (in browser-use) made the parser helper die and killed the whole audit. Fixed, with a test.

Real examples it found:
- fragments' README says `SUPABASE_URL`, but the code reads `NEXT_PUBLIC_SUPABASE_URL`.
- anything-llm's docs set `GENERATE_SOURCEMAP`, a Create-React-App setting, for a frontend that now uses Vite.
- gpt-researcher's `.claude/` reference for AI assistants documents `MAX_URLS_TO_SCRAPE`, which the code no longer has. That's stale context for the AI assistants themselves.
- crawl4ai's combined SDK reference has 8 links that broke when pages were merged into one file.

**Resume-ready claim:** "Built a static doc-vs-code checker. On 5 unseen open-source repos, 40 of 41 findings it ranked as likely issues were confirmed by hand. Weaker checks, like 'variable never read' at 27%, are routed to review instead."

**Auditing DriftGuard with itself (2026-11-08):** running it on its own repo found 14 false "drift" findings in the vendored Babel parser, plus a decision log read as instructions. Now files over 10,000 lines, or with their own `.LICENSE` file next to them, count as third-party and are skipped by Pattern-Drift. Decision logs are skipped by Spec-Drift like changelogs. What remains on its own repo is one review-level lead: the README quotes another repo's variable as an example.

**Final test on 22 unseen repos (2026-11-08):** static only, no rule changes between running and judging. Every verdict and its reason are in [eval/final-check.md](../eval/final-check.md). The repos: langchain-ai/open-canvas, local-deep-researcher, chat-langchain; Pythagora-io/gpt-pilot; smol-ai/developer; yoheinakajima/babyagi; nextjs/saas-starter; steven-tey/novel; qodo-ai/pr-agent; OpenBMB/ChatDev; run-llama/create-llama; arc53/DocsGPT; QuivrHQ/quivr; zylon-ai/private-gpt; stanford-oval/storm; Nutlope/llamacoder, aicommits, turboseek; TransformerOptimus/SuperAGI; mem0ai/mem0; Aider-AI/aider; cline/cline.

| Likely issues | Checked | Real | False | Unsure | Precision |
|---|---:|---:|---:|---:|---:|
| Competing libraries (all) | 40 | 21 | 15 | 4 | 58% (HTTP/icons/dates: 17/18 = 94%; print vs logging 4/10; database layers 0/7) |
| Unused dependencies (all) | 161 | 95 | 61 | 5 | 61% |
| Docs vs code (all) | 89 | 39 | 50 | 0 | 44% |
| Copied code (random 50 of 378) | 50 | 28 | 22 | 0 | 56% |
| Review leads (random 30 of 2,543) | 30 | 4 | 24 | 2 | 14% (similar functions 4/13; unused-dependency leads 0/15) |

What this means:
- **The earlier static numbers didn't generalize.** 40/41 (docs vs code) and 21/22 (unused deps) came from 5-6 repos without templates or generated code. On 22 varied repos, static likely issues are right a bit over half the time. The LLM-checked duplicate finder (82/82 on held-out) is still the strongest result.
- **One cause dominates: code the project didn't write by hand.** Template projects (create-llama: 30 doc findings and 8 dependencies; gpt-pilot: 18 dependencies; llamacoder preview kits: 9) and generated bundles (mem0's 7 plugin copies built from `integrations/agent-plugin-core`: about 20 of 50 sampled copies).
- **Other causes:** docs-site links to built `.html` pages (aider: 9); build outputs like `dist/cli.mjs`; tutorials that say "save this as x.py" first; `bun run file.ts` misread as a script (parser bug); tools run by command name (`buf`, `vsce`, vitest coverage, biome); platform-specific binaries; lockfile-style requirements read as manifests; `pip freeze` files full of transitive dependencies (all DocsGPT review leads); `urllib.parse` counted as an HTTP client (catalog bug); one database adapter per backend reported as competing layers.
- **Next fix, in order of payoff:** recognize template and generated folders; skip built-page links and `dist/` paths; treat `bun run <file>` as a file; drop database layers and print-vs-logging from likely issues; treat freeze files like lockfiles. **Re-measure on a fresh set of repos.** Quoting improved numbers from these 22 would be tuning on the test.

**Resume-safe claims after this test:** the LLM duplicate check (82% precision and recall on held-out data, recall 31% → 82% from a prompt rewrite); competing HTTP/icon/date libraries at 94% (17/18) on 22 unseen repos; "measured on 22 open-source repos, with 668 static findings and a published per-finding audit". Don't quote 40/41 or 21/22 as general precision anymore.

## What hasn't been verified

- **Gemini live:** needs a free key, and it's only been tested against a local fake server. Groq now works live.
- **Whether a stronger free model is actually good enough:** until that's measured, treat similar-function results as leads.
- **Claude live:** never run, and no longer needed.
- **Windows:** only run on macOS so far.
- **Competitor tools:** read about, not run. Try them before making any comparison.
