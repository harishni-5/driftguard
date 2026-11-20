# Deploy the website for free (Hugging Face Spaces)

A Space is a free container on Hugging Face: 2 CPUs, 16 GB of memory, and a public URL like `https://<you>-driftguard.hf.space`. The Space builds DriftGuard straight from this GitHub repo, so it holds only two files.

## Steps

1. Sign up at [huggingface.co](https://huggingface.co/join). It's free; no card needed.
2. Create a Space: [huggingface.co/new-space](https://huggingface.co/new-space).
   - Space name: `driftguard`
   - SDK: **Docker**, template **Blank**
   - Hardware: **CPU basic (free)**
   - Visibility: **Public**
3. Add your keys as **secrets**: Space → **Settings** → **Variables and secrets** → **New secret**.
   - `GROQ_API_KEY`: from [console.groq.com/keys](https://console.groq.com/keys). Needed for answers.
   - `GEMINI_API_KEY` (optional): from [aistudio.google.com/apikey](https://aistudio.google.com/apikey). Adds a fallback when Groq is at its limit, and embeddings for better search.

   Secrets are never shown to visitors and aren't in the Space's files.
4. Add the two files: Space → **Files** → **+ Add file** → **Create a new file**.
   - Name `Dockerfile`, content: [deploy/huggingface/Dockerfile](../deploy/huggingface/Dockerfile). Commit.
   - Open the existing `README.md`, replace everything with [deploy/huggingface/README.md](../deploy/huggingface/README.md). Commit.
5. The Space builds (about 5 minutes; watch **Logs**). When it says **Running**, open the **App** tab.

To deploy new code, push to GitHub, then Space → **Settings** → **Factory rebuild**.

## Good to know

- Free Spaces sleep after 48 hours without visitors and wake on the next visit, which takes about a minute.
- Disk is wiped on every restart, so finished audits and cached embeddings are lost. They're rebuilt on demand.
- Every visitor shares your free Groq quota. The server limits each visitor to 30 questions an hour, and everyone together to 400.
