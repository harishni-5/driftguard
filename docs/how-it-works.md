# How DriftGuard works

## The pipeline

```mermaid
flowchart LR
    A[Repo: local folder or https URL] --> B[Ingest]
    B -->|Python ast, Babel| C[(SQLite: files, symbols, imports, calls)]
    C --> D[Dead-Dep]
    C --> E[Pattern-Drift]
    C --> F[Spec-Drift]
    D & E -->|only ambiguous cases| G{{Free LLM: Groq, Gemini or Ollama}}
    G -->|re-rank, never delete| D & E
    D & E & F --> H[Findings + traces in SQLite]
    H --> I[Markdown or HTML report]
```

1. **Ingest.** The repo is cloned (https only, no symlinks) or opened, and every Python and JS/TS file is parsed by a helper subprocess: Python's own `ast` module, and a vendored copy of Babel. The helpers read files and print facts. Nothing in the repo is ever run.
2. **Agents.** Each agent implements `AuditAgent`, gets the parsed repo plus an optional `LlmClient`, and returns findings. Agents run in a fixed order.
3. **LLM, only where needed.** Static analysis settles everything it can. The LLM only sees what's left, which keeps free-tier use small and results repeatable.
4. **Report.** Findings are split into "Likely issues" (static analysis or a confirmed LLM check) and "Needs your review" (leads a person should check).

Every LLM call is recorded in `agent_traces` with its prompt, answer, tokens, cost and latency, so any verdict can be traced back.

## Dead-Dep: unused dependencies

| Result | When | In the report |
|---|---|---|
| **Used** | Imported, run from a script or git hook, named in config, or implied by project files (`.tsx` files imply `react`) | Not shown |
| **Unused** (static) | No reference found anywhere in scope | Likely issue |
| **Ambiguous** | Could be used indirectly: a driver, a plugin configured under a short name, a dev tool run by hand, an import with an unknown owner | Sent to the LLM. If it says unused, it becomes a likely issue; otherwise it goes under "Needs your review" |

The LLM can re-rank a finding but never delete one, because free models can be confidently wrong.

## Pattern-Drift: the same job done different ways

It looks at non-test product code, one project at a time, so a frontend and backend in one repo aren't compared.

| Finding | How it's found | Needs an LLM? |
|---|---|---|
| **Competing libraries**, such as `axios` next to `fetch` | Imports and calls checked against a hand-written list of 19 common jobs | No |
| **Copied code** | Function bodies identical apart from formatting and comments | No |
| **Similar functions** | TF-IDF word similarity picks candidates, then an LLM checks whether they do the same job | To be confirmed, yes. Otherwise they're leads under "Needs your review" |

It skips what looks alike on purpose: framework-required names like `GET` or `loader`, UI components, and generated `components/ui` files. Functions in one file need a much stronger match, because neighbours are usually a family on purpose. Small local models are advisory: their opinion is shown, but it can't confirm a finding.

## Spec-Drift: docs that don't match the code

It reads the repo's Markdown docs (READMEs, `docs/`, and AI-assistant files like `.claude/` or `SKILL.md`) and checks what they tell readers to do against the code. It needs no LLM. Changelogs, proposals, RFCs and blog posts are skipped, because they describe the past or the future, not today's code.

| Finding | How it's found | Where it goes |
|---|---|---|
| **Broken link** | A relative link whose file doesn't exist. Site routes like `/docs/intro`, URLs and badge links that point outside the repo are skipped | Likely issue |
| **Command runs a missing file** | `python x.py`, `bash x.sh` or `./x.sh` in a shell block, following the doc's `cd` steps. Skipped after cloning another project or `cd`-ing outside the repo | Likely issue |
| **Missing script** | `npm run x`, `pnpm x` or similar where no package.json has a script `x` (a package's own command, like `pnpm vitest`, is fine) | Likely issue |
| **Docs name the wrong variable** | The docs say `SUPABASE_URL`, but the code reads `NEXT_PUBLIC_SUPABASE_URL` | Likely issue |
| **Variable never read** | The docs say to set a variable that no code, config or script mentions | Needs your review: docs often describe settings a separate tool reads |

## Measuring accuracy

[eval/pattern-drift-labels.json](../eval/pattern-drift-labels.json) holds 50 similar-function groups from 7 repos, each labeled by hand before any LLM saw it, and pinned to the exact commit it came from. The `eval` command asks the LLM about every group and scores its answers:

```bash
java -jar target/driftguard.jar eval eval/pattern-drift-heldout.json --prompt v1,v2 --out eval/results-heldout.md
```

It reports precision (how many groups the model called drift really are drift), recall (how many real drift groups it caught), and the same precision for word similarity alone, as a baseline. To label new groups, add a repo to the file and run with `--dump`, which writes every group with its code and calls no LLM.

There are two label files:
- **`pattern-drift-labels.json`** (50 groups, 7 repos) is the development set. Prompt changes were made by looking at mistakes on it, so scores on it are optimistic for any prompt after v1.
- **`pattern-drift-heldout.json`** (34 groups, 5 other repos) was labeled with written rules ([eval/LABELING.md](../eval/LABELING.md)) before any model saw it. Quote this one.

The labels are one person's judgment. The line between "same job" and "same shape on purpose" is sometimes arguable, and every label has a note explaining it. Full results and the reasoning behind each change are in [DECISIONS.md](DECISIONS.md).

## Project layout

```
src/main/java/dev/driftguard/
  cli/        picocli commands (audit, eval, doctor)
  ingest/     clone or open a repo, walk files, run the parser helpers
  manifest/   read requirements.txt, pyproject.toml, Pipfile, setup.py, setup.cfg, package.json
  agents/     AuditAgent interface, Finding, and one package per agent
  llm/        LlmClient interface, provider selection, OpenAI-format client (Groq, Gemini, Ollama), Claude client
  store/      all SQLite access
  report/     Markdown and HTML output
src/main/resources/
  helpers/    parser subprocesses: Python (ast) and Node (vendored Babel)
  db/         schema.sql
  knowledge/  PyPI name to import name table
```

**To add an agent:** implement `AuditAgent`, then add it to the list in `AuditCommand`. Agents get the parsed repo, an optional `LlmClient` and a trace sink, and they return findings. Tokens, cost and latency get recorded through the trace sink, so they land in `agent_traces`.

## Known limits

- **Peer dependencies:** a package needed only as another package's peer looks unused. `shiki` under `rehype-pretty-code` is a real example. Lockfiles could fix this.
- **Curated tables:** `KnownPackages` and `python-import-names.txt` are hand-written and will never be complete. Unknown names go to review rather than being called unused.
- **Regex fallback:** files the parsers can't read fall back to a rough import scan, and findings in that scope get lower confidence.
- **Different wording:** Pattern-Drift only compares words, so the same job written in very different words is missed. Embeddings would help. A test documents this.
- **Error-handling style:** differences such as `.then` vs `await` aren't detected yet.
- **Feature claims in prose:** Spec-Drift checks concrete things (links, commands, scripts, variables), not sentences like "supports PDF export".
- **Scope:** Java repos aren't audited yet, and neither are multi-language dependency managers such as Bazel.
