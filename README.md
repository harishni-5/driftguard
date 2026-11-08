# DriftGuard

Audits codebases for "AI drift": the mess that builds up when AI coding assistants write most of the code.

**Status:** early. Repo ingestion, the SQLite store, Dead-Dep Finder, Pattern-Drift and Spec-Drift work for Python and JS/TS. See [docs/DECISIONS.md](docs/DECISIONS.md) for the plan and why it differs from the original handoff.

## Setup

You need JDK 21+, Python 3.10+, Node 22.18+ and git. You don't need to install Maven or run `npm install`. (22.18 is the minimum the vendored Babel states. The tests also passed on 22.14, but that isn't officially supported.)

Build on macOS or Linux:

```bash
./mvnw package
```

Build on Windows (PowerShell):

```powershell
.\mvnw.cmd package
```

Then check your setup:

```bash
java -jar target/driftguard.jar doctor
```

## Usage

Static analysis only, which is free and needs no API key:

```bash
java -jar target/driftguard.jar audit path/to/repo --no-llm
```

Audit a local folder or a public repo. DriftGuard uses a free LLM for the ambiguous cases if it finds one, and otherwise runs static analysis only:

```bash
java -jar target/driftguard.jar audit https://github.com/owner/repo --out report.md
```

Useful options: `--out` (report path), `--db` (SQLite file, default `~/.driftguard/driftguard.db`), `--llm` (provider), `--model` (model id).

## Free LLM options

No paid API is needed. Auto mode checks these in order:

| Provider | Setup | Good for | Watch out for |
|---|---|---|---|
| **Groq** | Free key from [console.groq.com/keys](https://console.groq.com/keys), set as `GROQ_API_KEY` | Stronger models at high speed | Daily limits. Function code and evidence lines leave your machine. |
| **Gemini** | Free key from [aistudio.google.com/apikey](https://aistudio.google.com/apikey), set as `GEMINI_API_KEY` | A second option | Daily limits. Free-tier prompts may be used to improve Google's products, so avoid it for private code. |
| **Ollama** | `ollama serve`, then `ollama pull qwen2.5:3b` | Unlimited and fully private | Advisory only: small models answered too unreliably, so their opinions are shown but can't confirm a finding. CPU-only laptops are also slow. |

Free-tier limits and model names change often. If a default model has been retired, pass another with `--model`. Long key-like strings in code are masked before anything is sent to an LLM, but that's a safety net, not a guarantee, so use `--no-llm` or Ollama for private code.

Claude also works with `--llm claude` and `ANTHROPIC_API_KEY`, but it's paid, so auto mode never picks it.

## How Dead-Dep Finder decides

| Result | When | In the report |
|---|---|---|
| **Used** | Imported, run from a script or git hook, named in config, or implied by project files (`.tsx` files imply `react`) | Not shown |
| **Unused** (static) | No reference found anywhere in scope | Likely issue |
| **Ambiguous** | Could be used indirectly: a driver, a plugin configured under a short name, a dev tool run by hand, an import with an unknown owner | Sent to the LLM. If it says unused, it becomes a likely issue; otherwise it goes under "Needs your review" |

The LLM only sees ambiguous cases, so clear cases stay repeatable. It can re-rank a finding but never delete one, because free models can be confidently wrong.

## How Pattern-Drift decides

It looks at non-test product code, one project at a time, so a frontend and backend in one repo aren't compared.

| Finding | How it's found | Needs an LLM? |
|---|---|---|
| **Competing libraries**, such as `axios` next to `fetch` | Imports and calls checked against a hand-written list of 19 common jobs | No |
| **Copied code** | Function bodies identical apart from formatting and comments | No |
| **Similar functions** | TF-IDF word similarity picks candidates, then an LLM checks whether they do the same job | To be confirmed, yes. Otherwise they're leads under "Needs your review" |

It skips what looks alike on purpose: framework-required names like `GET` or `loader`, UI components, and generated `components/ui` files. Functions in one file need a much stronger match, because neighbours are usually a family on purpose.

## How Spec-Drift decides

It reads the repo's Markdown docs (READMEs, `docs/`, and AI-assistant files like `.claude/` or `SKILL.md`) and checks what they tell readers to do against the code. It needs no LLM. Changelogs, proposals, RFCs and blog posts are skipped, because they describe the past or the future, not today's code.

| Finding | How it's found | Where it goes |
|---|---|---|
| **Broken link** | A relative link whose file doesn't exist. Site routes like `/docs/intro`, URLs and badge links that point outside the repo are skipped | Likely issue |
| **Command runs a missing file** | `python x.py`, `bash x.sh` or `./x.sh` in a shell block, following the doc's `cd` steps. Skipped after cloning another project or `cd`-ing outside the repo | Likely issue |
| **Missing script** | `npm run x`, `pnpm x` or similar where no package.json has a script `x` (a package's own command, like `pnpm vitest`, is fine) | Likely issue |
| **Docs name the wrong variable** | The docs say `SUPABASE_URL`, but the code reads `NEXT_PUBLIC_SUPABASE_URL` | Likely issue |
| **Variable never read** | The docs say to set a variable that no code, config or script mentions | Needs your review: docs often describe settings a separate tool reads |

## Measuring accuracy

[eval/pattern-drift-labels.json](eval/pattern-drift-labels.json) holds 50 similar-function groups from 7 repos, each labeled by hand before any LLM saw it, and pinned to the exact commit it came from. The `eval` command asks the LLM about every group and scores its answers:

```bash
java -jar target/driftguard.jar eval eval/pattern-drift-labels.json --out eval/results-groq.md
```

It reports precision (how many groups the model called drift really are drift), recall (how many real drift groups it caught), and the same precision for word similarity alone, as a baseline. To label new groups, add a repo to the file and run with `--dump`, which writes every group with its code and calls no LLM.

There are two label files:
- **`pattern-drift-labels.json`** (50 groups, 7 repos) is the development set. Prompt changes were made by looking at mistakes on it, so scores on it are optimistic for any prompt after v1.
- **`pattern-drift-heldout.json`** (34 groups, 5 other repos) was labeled with written rules ([eval/LABELING.md](eval/LABELING.md)) before any model saw it. Quote this one.

`--prompt v1,v2` scores several prompt versions in one run and writes one results file per version.

The labels are one person's judgment. The line between "same job" and "same shape on purpose" is sometimes arguable, and every label has a note explaining it.

## Project layout

```
src/main/java/dev/driftguard/
  cli/        picocli commands (audit, doctor)
  ingest/     clone or open a repo, walk files, run the parser helpers
  manifest/   read requirements.txt, pyproject.toml, Pipfile, setup.py, setup.cfg, package.json
  agents/     AuditAgent interface, Finding, and one package per agent
  llm/        LlmClient interface, provider selection, OpenAI-format client (Groq, Gemini, Ollama), Claude client
  store/      all SQLite access
  report/     Markdown output
src/main/resources/
  helpers/    parser subprocesses: Python (ast) and Node (vendored Babel)
  db/         schema.sql
  knowledge/  PyPI name to import name table
```

**To add an agent:** implement `AuditAgent`, then add it to the list in `AuditCommand`. Agents get the parsed repo, an optional `LlmClient` and a trace sink, and they return findings. Tokens, cost and latency get recorded through the trace sink, so they land in `agent_traces`.

## Tests

```bash
./mvnw test
```

Tests need Python and Node on your PATH but no API key. LLM clients are tested against a local fake server, so tests are free.

## Safety

The audited repo is treated as untrusted:

- Its code is parsed, never run. Helpers run from a temp folder, not from the repo.
- Symlinks aren't followed, so a repo can't point the tool at files outside itself.
- `.env` files, private keys and similar files are skipped, so they can't reach a prompt.
- Repo text sent to an LLM is marked as untrusted data, and verdicts are only accepted for packages we asked about.
- Remote repos must be plain `https://` URLs.

## Known limits

- **Peer dependencies:** a package needed only as another package's peer looks unused. `shiki` under `rehype-pretty-code` is a real example. Lockfiles could fix this.
- **Curated tables:** `KnownPackages` and `python-import-names.txt` are hand-written and will never be complete. Unknown names go to review rather than being called unused.
- **Regex fallback:** files the parsers can't read fall back to a rough import scan, and findings in that scope get lower confidence.
- **Different wording:** Pattern-Drift only compares words, so the same job written in very different words is missed. Embeddings would help. A test documents this.
- **Error-handling style:** differences such as `.then` vs `await` aren't detected yet.
- **Scope:** Java repos aren't audited yet, and neither are multi-language dependency managers such as Bazel.
