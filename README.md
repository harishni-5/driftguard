# DriftGuard

**Finds the mess AI coding assistants leave behind.** When different AI sessions write different parts of a codebase, nobody keeps the whole picture. The same job gets solved three ways, libraries pile up, and the docs describe code that has since changed. DriftGuard audits Python and JS/TS repos for that drift and tells you where it is.

![DriftGuard HTML report for e2b-dev/fragments](docs/images/report.png)

*A real report: [fragments](https://github.com/e2b-dev/fragments)' README tells you to set `SUPABASE_URL`, but the code reads `NEXT_PUBLIC_SUPABASE_URL`. Following the docs leaves the setting empty. ([Full sample report](docs/sample-report.html), download it and open it in a browser.)*

## What it finds

| Agent | Finds | Real example |
|---|---|---|
| **Pattern-Drift** | The same job done in different ways: competing libraries, copied code, and functions that do the same thing under different names | gpt-researcher sends a chat message to its API in four separate handlers, each with different timeouts and error handling |
| **Spec-Drift** | Docs that promise what the code doesn't do: broken links, commands that run deleted files, missing scripts, wrong variable names | gpt-researcher's `.claude/` notes for AI assistants still document `MAX_URLS_TO_SCRAPE`, which the code no longer has |
| **Dead-Dep** | Dependencies declared but never used | Unused packages left in `package.json` and `requirements.txt` after features were removed |

## Results

Every number below comes from hand-checking findings on public repos. Most were measured on repos the tool was never tuned on.

| What was measured | Result |
|---|---|
| **Pattern-Drift**: duplicated logic, on 33 hand-labeled groups from 5 held-out repos | **82% precision, 82% recall**, vs 52% precision for word similarity alone |
| Rewriting the LLM prompt, measured on the same held-out set | Recall went from **31% to 82%**, and precision went up too |
| **Spec-Drift**: findings ranked as likely issues, on 5 repos it had never seen | **40 of 41** confirmed by hand |
| Spec-Drift's weakest check, "variable never read" | 4 of 15 (27%), so it's reported as a lead for review, never as a likely issue |
| **Dead-Dep**: high-confidence unused dependencies across 6 repos | 22 flagged, 21 confirmed by text search (the miss was a peer dependency) |
| Cost of an LLM-checked audit with Groq's free tier | About 1-2k tokens for a small repo, **$0** |

The labels, results and reasoning behind every change are in [eval/](eval/) and [docs/DECISIONS.md](docs/DECISIONS.md). Samples are small (17 positive groups in the held-out set), so treat the percentages as good first evidence, not settled numbers.

## Quick start

You need JDK 21+, Python 3.10+, Node 22.18+ and git. Maven and npm packages aren't needed: the Maven wrapper and a vendored parser are included.

```bash
git clone https://github.com/harishni-5/driftguard.git
cd driftguard
./mvnw package
java -jar target/driftguard.jar audit https://github.com/e2b-dev/fragments --out report.html
```

On Windows, build with `.\mvnw.cmd package`. Run `java -jar target/driftguard.jar doctor` to check your setup.

That runs static analysis only, which is free and sends nothing anywhere. To let a free LLM check the ambiguous cases, set a [Groq key](https://console.groq.com/keys) first:

```bash
read -rs GROQ_API_KEY && export GROQ_API_KEY
java -jar target/driftguard.jar audit path/to/your/repo --out report.html
```

`read -rs` waits for you to paste the key without showing it on screen.

Options: `--out` (`.md` or `.html`), `--no-llm`, `--llm` (`groq`, `gemini`, `ollama` or `claude`), `--model`, `--db`.

## How it works

1. **Parse, never run.** The repo is parsed with Python's own `ast` module and a vendored Babel parser, and the facts land in SQLite.
2. **Static analysis first.** Each agent settles everything it can without an LLM. Copied code, broken links and unused imports are facts, not opinions.
3. **LLM only for what's left.** Ambiguous cases go to a free LLM. It can confirm or re-rank a finding, but **never delete one**, because free models are sometimes confidently wrong. Small local models are advisory only.
4. **Two kinds of results.** "Likely issues" were settled by static analysis or a confirmed LLM check. "Needs your review" are leads. The report always says which is which and why.

Details for each agent: [docs/how-it-works.md](docs/how-it-works.md).

### Design choices worth knowing

- **Measured on held-out data.** Prompt changes were judged on repos labeled before any model saw them, using [written labeling rules](eval/LABELING.md). The `eval` command reruns the whole test with any model.
- **Weak checks are demoted, not hidden.** When a check measured poorly on new repos, it moved to "Needs your review" rather than being tuned until the numbers looked good.
- **Free by default.** Auto mode tries Groq, then Gemini, then local Ollama. It never picks a paid API.
- **Traceable.** Every LLM call is stored with its prompt, answer, tokens and latency.

## Free LLM options

| Provider | Setup | Watch out for |
|---|---|---|
| **Groq** (recommended) | Free key from [console.groq.com/keys](https://console.groq.com/keys), set as `GROQ_API_KEY` | Daily limits. Function code leaves your machine. |
| **Gemini** | Free key from [aistudio.google.com/apikey](https://aistudio.google.com/apikey), set as `GEMINI_API_KEY` | Free-tier prompts may be used to improve Google's products, so avoid it for private code. |
| **Ollama** | `ollama serve`, then `ollama pull qwen2.5:3b` | Fully private, but small models answered too unreliably, so they're advisory only. |

Model names change often. If a default model has been retired, pass another with `--model`.

## Safety

The audited repo is treated as untrusted:

- Its code is parsed, never run. Helpers run from a temp folder, not from the repo.
- Symlinks aren't followed, and `.env` files and private keys are skipped, so they can't reach a prompt.
- Long key-like strings are masked before code goes to an LLM. That's a safety net, not a guarantee, so use `--no-llm` or Ollama for private code.
- Repo text sent to an LLM is marked as untrusted data, and verdicts are only accepted for items that were asked about.
- The HTML report escapes all repo text and loads nothing from the network.

## Tests

```bash
./mvnw test
```

108 tests. They need Python and Node on your PATH but no API key: LLM clients are tested against a local fake server.

## Status and limits

Working: ingestion, the three agents, Markdown and HTML reports, and the eval harness, for Python and JS/TS. Next: a GitHub Action that comments on pull requests.

Known limits: functions that do the same job in very different words are missed; Spec-Drift checks concrete things like links and commands, not prose claims like "supports PDF export"; Java repos aren't audited yet. The full list is in [docs/how-it-works.md](docs/how-it-works.md#known-limits).

Built with Java 21, picocli, SQLite and Jackson.
