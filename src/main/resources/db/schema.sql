-- DriftGuard schema, version 1.
--
-- Design notes:
--  * A "repo" row is one immutable snapshot: the same source with the same file
--    contents maps to one row, so re-running an audit reuses it and old findings
--    stay comparable.
--  * We store extracted facts (symbols, imports, call edges), not raw ASTs. Raw
--    ASTs are huge, and no agent needs them: helpers can re-parse a file on demand.
--  * audit_runs.config_json records which agents and flags were on. Ablation
--    experiments in the paper are just rows with different configs.
--  * agent_traces records every LLM call with tokens and latency, so cost and
--    time-to-audit in the paper come straight from SQL.

CREATE TABLE repos (
    id              INTEGER PRIMARY KEY,
    source          TEXT NOT NULL,              -- https URL or absolute local path
    commit_sha      TEXT,                       -- NULL when the folder is not a git repo
    content_hash    TEXT NOT NULL,              -- hash of (path, sha256) for every file
    primary_language TEXT,
    file_count      INTEGER NOT NULL,
    loc             INTEGER NOT NULL,
    ingested_at     TEXT NOT NULL,
    UNIQUE (source, content_hash)
);

CREATE TABLE files (
    id              INTEGER PRIMARY KEY,
    repo_id         INTEGER NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    path            TEXT NOT NULL,              -- repo-relative, always forward slashes
    kind            TEXT NOT NULL,              -- PYTHON | JAVASCRIPT | MANIFEST | CONFIG | OTHER
    sha256          TEXT NOT NULL,
    size_bytes      INTEGER NOT NULL,
    loc             INTEGER NOT NULL,
    parse_error     TEXT,                       -- NULL when parsed cleanly or not parseable
    UNIQUE (repo_id, path)
);

CREATE TABLE symbols (
    id              INTEGER PRIMARY KEY,
    file_id         INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    qualified_name  TEXT NOT NULL,              -- e.g. Client.send
    kind            TEXT NOT NULL,              -- function | class | method
    start_line      INTEGER NOT NULL,
    end_line        INTEGER NOT NULL,
    is_exported     INTEGER NOT NULL
);
CREATE INDEX idx_symbols_name ON symbols(name);
CREATE INDEX idx_symbols_file ON symbols(file_id);

CREATE TABLE imports (
    id              INTEGER PRIMARY KEY,
    file_id         INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    module          TEXT NOT NULL,
    kind            TEXT NOT NULL,              -- import | from | require | dynamic | reexport | type
    line            INTEGER NOT NULL,
    names_json      TEXT NOT NULL,              -- JSON array of imported names
    is_external     INTEGER NOT NULL            -- 0 for relative and standard-library imports
);
CREATE INDEX idx_imports_module ON imports(module);
CREATE INDEX idx_imports_file ON imports(file_id);

-- Call sites as written in the source. The callee is a name, not a resolved
-- symbol: the Orphan-Code agent resolves names when it needs to.
CREATE TABLE call_edges (
    id              INTEGER PRIMARY KEY,
    file_id         INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    caller          TEXT,                       -- qualified name, NULL at module level
    callee          TEXT NOT NULL,              -- e.g. foo or client.send
    line            INTEGER NOT NULL
);
CREATE INDEX idx_call_edges_callee ON call_edges(callee);
CREATE INDEX idx_call_edges_file ON call_edges(file_id);

CREATE TABLE dependencies (
    id              INTEGER PRIMARY KEY,
    repo_id         INTEGER NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    ecosystem       TEXT NOT NULL,              -- python | javascript
    manifest_path   TEXT NOT NULL,
    name            TEXT NOT NULL,
    version_spec    TEXT,
    scope           TEXT NOT NULL,              -- RUNTIME | DEV | OPTIONAL | PEER
    line            INTEGER NOT NULL
);
CREATE INDEX idx_dependencies_repo ON dependencies(repo_id);

-- Reserved for Pattern-Drift and Q&A. Keyed by content hash, so unchanged code
-- is never embedded twice, even across different repos and snapshots.
CREATE TABLE embeddings (
    content_sha     TEXT NOT NULL,
    model           TEXT NOT NULL,
    dimensions      INTEGER NOT NULL,
    vector          BLOB NOT NULL,              -- little-endian float32 array
    created_at      TEXT NOT NULL,
    PRIMARY KEY (content_sha, model)
);

CREATE TABLE audit_runs (
    id              INTEGER PRIMARY KEY,
    repo_id         INTEGER NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    tool_version    TEXT NOT NULL,
    config_json     TEXT NOT NULL,
    status          TEXT NOT NULL,              -- RUNNING | COMPLETED | FAILED
    started_at      TEXT NOT NULL,
    finished_at     TEXT,
    duration_ms     INTEGER
);
CREATE INDEX idx_audit_runs_repo ON audit_runs(repo_id);

CREATE TABLE findings (
    id              INTEGER PRIMARY KEY,
    run_id          INTEGER NOT NULL REFERENCES audit_runs(id) ON DELETE CASCADE,
    agent           TEXT NOT NULL,
    category        TEXT NOT NULL,              -- e.g. unused-dependency
    severity        TEXT NOT NULL,              -- CRITICAL | HIGH | MEDIUM | LOW
    confidence      TEXT NOT NULL,              -- HIGH | MEDIUM | LOW
    decided_by      TEXT NOT NULL,              -- STATIC | LLM | UNVERIFIED
    title           TEXT NOT NULL,
    description     TEXT NOT NULL,
    file_path       TEXT,
    start_line      INTEGER,
    end_line        INTEGER,
    evidence_json   TEXT NOT NULL,
    fix_suggestion  TEXT,
    fingerprint     TEXT NOT NULL               -- stable across runs, used for eval matching
);
CREATE INDEX idx_findings_run ON findings(run_id);
CREATE INDEX idx_findings_fingerprint ON findings(fingerprint);

CREATE TABLE agent_traces (
    id              INTEGER PRIMARY KEY,
    run_id          INTEGER NOT NULL REFERENCES audit_runs(id) ON DELETE CASCADE,
    agent           TEXT NOT NULL,
    step            INTEGER NOT NULL,
    event           TEXT NOT NULL,              -- e.g. llm_call | llm_error | stats
    model           TEXT,
    input_text      TEXT,
    output_text     TEXT,
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    cost_usd        REAL,
    latency_ms      INTEGER,
    created_at      TEXT NOT NULL
);
CREATE INDEX idx_agent_traces_run ON agent_traces(run_id);
