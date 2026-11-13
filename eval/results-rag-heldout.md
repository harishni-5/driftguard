# Retrieval results

Embeddings: nomic-embed-text:latest. Rank = position of the first result from a file that answers the question (0 = not in the top 10).

| Search | Questions | Found first | Found in top 5 | MRR@10 |
|---|---:|---:|---:|---:|
| keyword | 40 | 15 (38%) | 31 (78%) | 0.55 |
| vector | 40 | 22 (55%) | 31 (78%) | 0.65 |
| hybrid | 40 | 22 (55%) | 34 (85%) | 0.68 |

| Repo | Question | keyword | vector | hybrid | Top hybrid result |
|---|---|---:|---:|---:|---|
| full-stack-fastapi-template | How does an API request find out which user is logged in? | 0 | 0 | 0 | backend/app/api/routes/users.py |
| full-stack-fastapi-template | How are login tokens created and how long do they last? | 6 | 1 | 1 | backend/app/api/routes/login.py |
| full-stack-fastapi-template | Which algorithm hashes user passwords? | 3 | 1 | 1 | backend/app/core/security.py |
| full-stack-fastapi-template | How does the password reset email flow work? | 1 | 3 | 2 | packages/react-email/emails/reset_password.tsx |
| full-stack-fastapi-template | Where is the first admin account created on startup? | 4 | 2 | 4 | deployment-docker-compose.md |
| full-stack-fastapi-template | Which origins may call the API from a browser? | 6 | 0 | 5 | frontend/README.md |
| full-stack-fastapi-template | What happens to a user's items when the user is deleted? | 2 | 2 | 2 | backend/app/api/routes/items.py |
| full-stack-fastapi-template | How do database migrations run before the backend starts? | 1 | 1 | 1 | backend/README.md |
| full-stack-fastapi-template | How is the frontend's API client regenerated after the backend changes? | 2 | 1 | 1 | frontend/README.md |
| full-stack-fastapi-template | How does the frontend keep users who aren't logged in out of the dashboard? | 4 | 0 | 0 | frontend/src/routes/_layout/index.tsx |
| full-stack-fastapi-template | Where is SECRET_KEY read and what is it used for? | 2 | 4 | 2 | deployment.md |
| full-stack-fastapi-template | What does get_current_active_superuser check? | 6 | 0 | 0 | backend/app/api/routes/users.py |
| full-stack-fastapi-template | Where is FIRST_SUPERUSER_PASSWORD used? | 2 | 1 | 2 | deployment.md |
| full-stack-fastapi-template | How is Sentry set up? | 5 | 2 | 1 | backend/app/main.py |
| full-stack-fastapi-template | What does the /utils/test-email/ endpoint do? | 4 | 1 | 1 | backend/app/api/routes/utils.py |
| full-stack-fastapi-template | How does read_items limit results to the current user's items? | 1 | 1 | 1 | backend/app/api/routes/items.py |
| full-stack-fastapi-template | How is DATABASE_URL turned into a URL that uses the psycopg driver? | 1 | 1 | 1 | backend/app/core/config.py |
| full-stack-fastapi-template | How does update_user_me stop someone taking an email that's already used? | 1 | 1 | 1 | backend/app/api/routes/users.py |
| full-stack-fastapi-template | How do I deploy this with Docker Compose and Traefik? | 1 | 1 | 1 | deployment-docker-compose.md |
| full-stack-fastapi-template | Where is the useAuth hook's logout implemented? | 1 | 1 | 1 | frontend/src/hooks/useAuth.ts |
| fragments | How is generated code run in a sandbox? | 1 | 1 | 1 | app/api/sandbox/route.ts |
| fragments | How is the model's code answer streamed back as structured data? | 8 | 1 | 2 | app/api/morph-chat/route.ts |
| fragments | How are users limited in how many requests they can send? | 2 | 2 | 1 | app/api/chat/route.ts |
| fragments | How is the system prompt built from the available templates? | 2 | 1 | 1 | lib/prompt.ts |
| fragments | Which app templates can the user pick, like Next.js or Streamlit? | 1 | 3 | 2 | README.md |
| fragments | How do sign-in and accounts work? | 1 | 1 | 1 | components/auth.tsx |
| fragments | How does sharing a fragment create a short link? | 0 | 0 | 0 | components/fragment-web.tsx |
| fragments | How do short links redirect to the running app? | 6 | 0 | 5 | components/auth.tsx |
| fragments | How are fast edits applied to existing code instead of regenerating it all? | 2 | 8 | 2 | lib/schema.ts |
| fragments | How are disposable email addresses rejected at sign-up? | 7 | 2 | 2 | components/auth.tsx |
| fragments | What fields does the model's structured answer contain? | 3 | 0 | 9 | lib/models.ts |
| fragments | How does getModelClient pick the provider SDK for a model? | 1 | 1 | 1 | lib/models.ts |
| fragments | Where is E2B_API_KEY configured? | 1 | 1 | 1 | README.md |
| fragments | What happens when KV_REST_API_URL isn't set? | 1 | 1 | 1 | lib/ratelimit.ts |
| fragments | Where are RATE_LIMIT_MAX_REQUESTS and RATE_LIMIT_WINDOW read? | 1 | 3 | 1 | app/api/chat/route.ts |
| fragments | How are API errors turned into responses for the client? | 2 | 1 | 1 | lib/api-errors.ts |
| fragments | How is the Streamlit sandbox template built? | 3 | 1 | 2 | sandbox-templates/streamlit-developer/app.py |
| fragments | How do I add a new template of my own? | 1 | 1 | 1 | README.md |
| fragments | How does the app show the running web app next to the code? | 0 | 0 | 0 | README.md |
| fragments | Where is the request body for the chat endpoint validated? | 4 | 1 | 1 | lib/request-schema.ts |
