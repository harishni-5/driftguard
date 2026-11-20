"use strict";

// Every piece of repo text is inserted with textContent, never as HTML.
const $ = (id) => document.getElementById(id);
const el = (tag, className, text) => {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = text;
  return node;
};

const state = { id: null, audit: null, repoUrl: null, commit: null, filter: "likely", asking: false, history: [] };

async function api(path, body) {
  const options = body === undefined ? {} : {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  };
  const response = await fetch(path, options);
  let data = {};
  try { data = await response.json(); } catch (_) { /* empty body */ }
  if (!response.ok) throw new Error(data.error || "The server didn't respond. Try again.");
  return data;
}

function show(section) {
  for (const id of ["hero", "progress", "results"]) $(id).hidden = id !== section;
}

// ---- starting an audit -------------------------------------------------------------

async function startAudit(repo) {
  $("form-error").hidden = true;
  $("audit-button").disabled = true;
  try {
    const { id } = await api("/api/audit", { repo });
    history.pushState({}, "", "?id=" + encodeURIComponent(id));
    await follow(id);
  } catch (error) {
    show("hero");
    $("form-error").textContent = error.message;
    $("form-error").hidden = false;
  } finally {
    $("audit-button").disabled = false;
  }
}

async function follow(id) {
  state.id = id;
  show("progress");
  $("progress-repo").textContent = "Starting...";
  $("progress-text").textContent = "";
  for (;;) {
    const job = await api("/api/audit/" + encodeURIComponent(id));
    $("progress-repo").textContent = job.repo;
    if (job.status === "DONE") return render(job);
    if (job.status === "FAILED") throw new Error(job.error || "The audit failed.");
    $("progress-text").textContent = job.status === "QUEUED"
      ? (job.ahead > 0 ? `Waiting for ${job.ahead} audit${job.ahead === 1 ? "" : "s"} ahead of you` : "Starting")
      : job.progress;
    await new Promise((resolve) => setTimeout(resolve, 1500));
  }
}

// ---- results ---------------------------------------------------------------------------

function render(job) {
  state.audit = job.audit;
  state.repoUrl = job.url;
  state.commit = job.commit;
  show("results");
  document.title = job.repo + " · DriftGuard";

  const link = $("repo-link");
  link.textContent = job.repo;
  link.href = job.url;
  $("report-link").href = "/report/" + encodeURIComponent(job.id);
  const commit = job.commit ? job.commit.slice(0, 7) : "unknown";
  $("repo-meta").textContent = `Commit ${commit} · ${job.chunks} code and doc sections indexed`;
  showSearchStatus(job);
  watchVectors(job.id);

  const findings = job.audit.findings || [];
  const likely = findings.filter((f) => f.decidedBy !== "UNVERIFIED");
  const review = findings.filter((f) => f.decidedBy === "UNVERIFIED");
  const byAgent = (name) => findings.filter((f) => f.agent === name && f.decidedBy !== "UNVERIFIED").length;
  const stats = $("stats");
  stats.replaceChildren(
    stat(likely.length, "likely issues"),
    stat(review.length, "need your review"),
    stat(byAgent("pattern-drift"), "pattern drift"),
    stat(byAgent("spec-drift") + byAgent("dead-dep"), "docs and dependency issues"),
  );
  state.filter = likely.length > 0 ? "likely" : "all";
  renderFindings();
  state.history = [];
  renderSuggestions();
  selectTab("findings");
}

function showSearchStatus(job) {
  let search = "keyword search";
  if (job.vectors === "ready") search = "hybrid search (keywords + semantic vectors)";
  else if ((job.vectors || "").startsWith("building")) search = "keyword search; semantic search is being built";
  else if ((job.vectors || "").startsWith("unavailable")) search = "keyword search (" + job.vectors.replace("unavailable: ", "") + ")";
  $("search-meta").textContent = (job.canAnswer ? "Answers by a free LLM from " : "Matching code from ") + search
    + (job.canAnswer ? ". Check the sources." : ".");
}

/** Semantic vectors are built after the audit; refresh the status line until they're ready or given up. */
async function watchVectors(id) {
  for (let i = 0; i < 90 && state.id === id; i++) {
    await new Promise((resolve) => setTimeout(resolve, 8000));
    if (state.id !== id) return;
    try {
      const job = await api("/api/audit/" + encodeURIComponent(id));
      showSearchStatus(job);
      if (!(job.vectors || "").startsWith("building")) return;
    } catch (_) { return; }
  }
}

function stat(value, label) {
  const box = el("div", "stat");
  box.append(el("b", null, String(value)), el("span", null, label));
  return box;
}

function githubLink(path, line) {
  if (!state.repoUrl || !path) return null;
  const ref = state.commit || "HEAD";
  return `${state.repoUrl}/blob/${ref}/${path.split("/").map(encodeURIComponent).join("/")}${line ? "#L" + line : ""}`;
}

function renderFindings() {
  for (const chip of $("filters").querySelectorAll(".chip")) {
    chip.classList.toggle("active", chip.dataset.filter === state.filter);
  }
  const all = state.audit.findings || [];
  const shown = all.filter((f) => state.filter === "all"
    || (state.filter === "likely" ? f.decidedBy !== "UNVERIFIED" : f.decidedBy === "UNVERIFIED"));
  const list = $("findings");
  if (shown.length === 0) {
    list.replaceChildren(el("div", "empty", state.filter === "likely"
      ? "No likely issues found. Check \"Needs review\" for leads."
      : "Nothing here."));
    return;
  }
  list.replaceChildren(...shown.slice(0, 200).map(findingCard));
  if (shown.length > 200) list.append(el("p", "muted", `Showing 200 of ${shown.length}. Open the full report for the rest.`));
}

const AGENT_NAMES = { "pattern-drift": "Pattern-Drift", "spec-drift": "Spec-Drift", "dead-dep": "Dead-Dep" };

function findingCard(f) {
  const card = el("article", "finding");
  const top = el("div", "finding-top");
  top.append(el("span", "pill " + f.severity, f.severity.toLowerCase()));
  top.append(el("span", "pill", AGENT_NAMES[f.agent] || f.agent));
  if (f.decidedBy === "UNVERIFIED") top.append(el("span", "pill review", "needs review"));
  else if (f.decidedBy === "LLM") top.append(el("span", "pill", "LLM-confirmed"));
  card.append(top, el("h4", null, f.title), el("p", null, f.description));
  if (f.filePath) {
    const where = el("p", "where");
    const url = githubLink(f.filePath, f.startLine);
    const label = f.filePath + (f.startLine ? ":" + f.startLine : "");
    if (url) {
      const a = el("a", null, label);
      a.href = url; a.target = "_blank"; a.rel = "noopener";
      where.append(a);
    } else {
      where.textContent = label;
    }
    card.append(where);
  }
  if (f.fixSuggestion) card.append(el("p", "fix", "Fix: " + f.fixSuggestion));
  if (f.evidence && f.evidence.length) {
    const details = el("details");
    details.append(el("summary", null, `Evidence (${f.evidence.length})`));
    const ul = el("ul");
    for (const item of f.evidence.slice(0, 12)) ul.append(el("li", null, item));
    details.append(ul);
    card.append(details);
  }
  return card;
}

// ---- asking ------------------------------------------------------------------------------

function renderSuggestions() {
  const box = $("suggestions");
  const questions = [
    "What does this project do, and where does it start?",
    "How is configuration loaded, and which environment variables matter?",
    "How are errors handled?",
  ];
  box.replaceChildren(...questions.map((q) => {
    const chip = el("button", "chip", q);
    chip.type = "button";
    chip.addEventListener("click", () => ask(q));
    return chip;
  }));
  $("chat").replaceChildren($("chat-empty"));
  $("chat-empty").hidden = false;
}

async function ask(question) {
  if (state.asking || !question.trim()) return;
  state.asking = true;
  $("ask-button").disabled = true;
  $("chat-empty").hidden = true;
  const chat = $("chat");
  chat.append(el("div", "msg user", question));
  const typing = el("div", "typing");
  typing.append(el("i"), el("i"), el("i"));
  chat.append(typing);
  chat.scrollTop = chat.scrollHeight;
  try {
    const reply = await api("/api/ask", { id: state.id, question, history: state.history.slice(-4) });
    typing.replaceWith(answerBlock(reply));
    state.history.push({ question, answer: (reply.answer || "").slice(0, 1500) });
  } catch (error) {
    typing.replaceWith(el("div", "msg bot error-msg", error.message));
  } finally {
    state.asking = false;
    $("ask-button").disabled = false;
    chat.scrollTop = chat.scrollHeight;
  }
}

function answerBlock(reply) {
  const block = el("div", "msg bot");
  const sources = el("div", "sources");
  const sourceNodes = new Map();
  for (const source of reply.sources || []) {
    const details = el("details", "source");
    const summary = el("summary");
    summary.append(el("span", "num", `[${source.n}]`),
      el("span", "path", `${source.path}:${source.startLine}-${source.endLine}${source.name ? "  " + source.name : ""}`));
    details.append(summary, el("pre", null, source.code));
    const open = el("a", "open", "Open on GitHub");
    open.href = source.url; open.target = "_blank"; open.rel = "noopener";
    details.append(open);
    if (source.cited || !(reply.sources || []).some((s) => s.cited)) sources.append(details);
    sourceNodes.set(source.n, details);
  }
  const answer = el("div", "answer");
  if (reply.generalKnowledge) answer.append(el("span", "badge", "General knowledge, not from this repo"));
  formatAnswer(reply.answer || "", answer, sourceNodes);
  block.append(answer);
  if (reply.answerable === false && reply.model && !reply.generalKnowledge) {
    block.append(el("p", "note", "The retrieved code didn't fully answer this. Try naming a file, function or setting."));
  }
  const extra = (reply.searchedFor || []).slice(1);
  if (extra.length) block.append(el("p", "note", "Also searched for: " + extra.join(" · ")));
  if (sources.childElementCount && !reply.generalKnowledge) block.append(sources);
  return block;
}

/** A small, safe Markdown subset: paragraphs, bullet and numbered lists, `code`, **bold** and [n] citations. */
function formatAnswer(text, container, sourceNodes) {
  let list = null;
  let paragraph = null;
  for (const raw of text.split(/\n/)) {
    const line = raw.trimEnd();
    const bullet = line.match(/^\s*(?:[-*•]|\d+[.)])\s+(.*)$/);
    if (!line.trim()) { list = null; paragraph = null; continue; }
    if (bullet) {
      if (!list) {
        list = el(/^\s*\d/.test(line) ? "ol" : "ul");
        container.append(list);
      }
      const item = el("li");
      inline(bullet[1], item, sourceNodes);
      list.append(item);
      paragraph = null;
      continue;
    }
    list = null;
    if (!paragraph) {
      paragraph = el("p");
      container.append(paragraph);
    } else {
      paragraph.append(document.createElement("br"));
    }
    inline(line, paragraph, sourceNodes);
  }
}

function inline(text, parent, sourceNodes) {
  const pattern = /(`[^`]+`|\*\*[^*]+\*\*|\[\d{1,2}\])/g;
  let last = 0;
  for (const match of text.matchAll(pattern)) {
    if (match.index > last) parent.append(text.slice(last, match.index));
    const token = match[0];
    if (token.startsWith("`")) parent.append(el("code", null, token.slice(1, -1)));
    else if (token.startsWith("**")) parent.append(el("strong", null, token.slice(2, -2)));
    else {
      const n = Number(token.slice(1, -1));
      const target = sourceNodes.get(n);
      if (target) {
        const cite = el("button", "cite", String(n));
        cite.type = "button";
        cite.title = "Show source " + n;
        cite.addEventListener("click", () => {
          target.open = true;
          target.classList.add("flash");
          target.scrollIntoView({ block: "nearest", behavior: "smooth" });
          setTimeout(() => target.classList.remove("flash"), 1200);
        });
        parent.append(cite);
      } else {
        parent.append(token);
      }
    }
    last = match.index + token.length;
  }
  if (last < text.length) parent.append(text.slice(last));
}

// ---- tabs and wiring -----------------------------------------------------------------------

function selectTab(name) {
  $("tab-findings").setAttribute("aria-selected", String(name === "findings"));
  $("tab-ask").setAttribute("aria-selected", String(name === "ask"));
  $("findings-pane").classList.toggle("shown", name === "findings");
  $("ask-pane").classList.toggle("shown", name === "ask");
}

function autoGrow(textarea) {
  textarea.style.height = "auto";
  textarea.style.height = Math.min(textarea.scrollHeight, 140) + "px";
}

document.addEventListener("DOMContentLoaded", () => {
  $("audit-form").addEventListener("submit", (event) => {
    event.preventDefault();
    startAudit($("repo-input").value.trim());
  });
  for (const chip of document.querySelectorAll(".examples .chip")) {
    chip.addEventListener("click", () => {
      $("repo-input").value = chip.dataset.repo;
      startAudit(chip.dataset.repo);
    });
  }
  for (const chip of $("filters").querySelectorAll(".chip")) {
    chip.addEventListener("click", () => { state.filter = chip.dataset.filter; renderFindings(); });
  }
  $("tab-findings").addEventListener("click", () => selectTab("findings"));
  $("tab-ask").addEventListener("click", () => selectTab("ask"));
  $("new-audit").addEventListener("click", () => {
    history.pushState({}, "", "/");
    $("repo-input").value = "";
    document.title = "DriftGuard";
    show("hero");
    $("repo-input").focus();
  });
  const question = $("question");
  question.addEventListener("input", () => autoGrow(question));
  question.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      $("ask-form").requestSubmit();
    }
  });
  $("ask-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const text = question.value.trim();
    question.value = "";
    autoGrow(question);
    ask(text);
  });

  const params = new URLSearchParams(location.search);
  if (params.get("id")) {
    follow(params.get("id")).catch((error) => {
      show("hero");
      $("form-error").textContent = error.message;
      $("form-error").hidden = false;
    });
  } else if (params.get("repo")) {
    $("repo-input").value = params.get("repo");
    startAudit(params.get("repo"));
  }
  window.addEventListener("popstate", () => location.reload());
});
