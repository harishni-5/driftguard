# Deploy the website for free (Render)

Render's free tier runs the website from this repo's `Dockerfile` with no credit card. You get a public URL like `https://driftguard.onrender.com`.

**Free-tier facts:**
- 512 MB of memory. DriftGuard peaked at 211 MB auditing markitdown, with a 280 MB heap.
- A small share of a CPU, so audits take longer than on a laptop.
- 750 hours a month.
- The service sleeps after 15 minutes without visitors, and the next visit takes about a minute to wake it.
- Disk is wiped on restart, so finished audits are rebuilt on demand.

## Steps

1. Sign up at [dashboard.render.com/register](https://dashboard.render.com/register) with **GitHub**.
2. Click **New** → **Blueprint**, connect GitHub, and pick the `driftguard` repo. Render reads [`render.yaml`](../render.yaml).
3. It asks for the two secret values:
   - `GROQ_API_KEY`: from [console.groq.com/keys](https://console.groq.com/keys). Answers questions.
   - `GEMINI_API_KEY`: from [aistudio.google.com/apikey](https://aistudio.google.com/apikey). Fallback answers and embeddings for better search.
4. Click **Deploy Blueprint**. The first build takes about 5–10 minutes. When the service shows **Live**, open its URL.

Every push to `master` redeploys automatically.

## Other hosts

The `Dockerfile` runs anywhere that runs containers. Raise the memory limits (`JAVA_OPTS`, `DRIFTGUARD_MAX_REPO_MB`, `DRIFTGUARD_KEEP_RESULTS`) on bigger machines.
- **Google Cloud Run:** set **CPU always allocated** and **max instances 1**. Audits run in the background between requests, and the results live in memory.
- **Hugging Face Spaces:** [deploy/huggingface](../deploy/huggingface) has a Space setup, but Docker Spaces now need a PRO plan.
