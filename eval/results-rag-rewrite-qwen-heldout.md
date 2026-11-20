# Retrieval results

Embeddings: nomic-embed-text:latest. Query rewriting: qwen2.5:3b-instruct. Rank = position of the first result from a file that answers the question (0 = not in the top 10).

| Search | Questions | Found first | Found in top 5 | MRR@10 |
|---|---:|---:|---:|---:|
| keyword | 40 | 15 (38%) | 31 (78%) | 0.55 |
| vector | 40 | 22 (55%) | 31 (78%) | 0.65 |
| hybrid | 40 | 22 (55%) | 34 (85%) | 0.68 |
| hybrid+rewrite | 40 | 21 (53%) | 33 (83%) | 0.66 |

| Repo | Question | keyword | vector | hybrid | hybrid+rewrite | Top hybrid result | Rewritten queries |
|---|---|---:|---:|---:|---:|---|---|
| full-stack-fastapi-template | How does an API request find out which user is logged in? | 0 | 0 | 0 | 0 | backend/app/api/routes/users.py | api auth user; request session user; api find logged in user |
| full-stack-fastapi-template | How are login tokens created and how long do they last? | 6 | 1 | 1 | 1 | backend/app/api/routes/login.py | login token creation; token expiration time; token lifespan |
| full-stack-fastapi-template | Which algorithm hashes user passwords? | 3 | 1 | 1 | 1 | backend/app/core/security.py | password hashing algorithm; user password hash function; algorithm for hashing passwords |
| full-stack-fastapi-template | How does the password reset email flow work? | 1 | 3 | 2 | 2 | packages/react-email/emails/reset_password.tsx | password reset flow; email password reset; email password reset flow |
| full-stack-fastapi-template | Where is the first admin account created on startup? | 4 | 2 | 4 | 0 | deployment-docker-compose.md | admin account startup; first user account creation; startup admin user |
| full-stack-fastapi-template | Which origins may call the API from a browser? | 6 | 0 | 5 | 2 | frontend/README.md | api origins; allowed origins; browser api callers |
| full-stack-fastapi-template | What happens to a user's items when the user is deleted? | 2 | 2 | 2 | 2 | backend/app/api/routes/items.py | user deletion items; user delete items handled; user remove items logic |
| full-stack-fastapi-template | How do database migrations run before the backend starts? | 1 | 1 | 1 | 1 | backend/README.md | migrations run before; database migrations startup; run migrations after server start |
| full-stack-fastapi-template | How is the frontend's API client regenerated after the backend changes? | 2 | 1 | 1 | 1 | frontend/README.md | api_client_regeneration; frontend_api_client_regeneration; backend_changes_api_client_regeneration |
| full-stack-fastapi-template | How does the frontend keep users who aren't logged in out of the dashboard? | 4 | 0 | 0 | 0 | frontend/src/routes/_layout/index.tsx | check frontend login; prevent unauthorized access; dashboard login check |
| full-stack-fastapi-template | Where is SECRET_KEY read and what is it used for? | 2 | 4 | 2 | 2 | deployment.md | read secret key; where used secret key; secret key usage place |
| full-stack-fastapi-template | What does get_current_active_superuser check? | 6 | 0 | 0 | 0 | backend/app/api/routes/users.py | get_current_active_superuser function; active superuser check; superuser status function |
| full-stack-fastapi-template | Where is FIRST_SUPERUSER_PASSWORD used? | 2 | 1 | 2 | 2 | deployment.md | FIRST_SUPERUSER_PASSWORD; env_var_password; config_password_file |
| full-stack-fastapi-template | How is Sentry set up? | 5 | 2 | 1 | 1 | backend/app/main.py | sentry setup; sentry configuration; sentry initialization |
| full-stack-fastapi-template | What does the /utils/test-email/ endpoint do? | 4 | 1 | 1 | 1 | backend/app/api/routes/utils.py | test email endpoint; utils test-email; test_email_endpoint |
| full-stack-fastapi-template | How does read_items limit results to the current user's items? | 1 | 1 | 1 | 1 | backend/app/api/routes/items.py | read_items_logic; user_items_filter; current_user_items_limit |
| full-stack-fastapi-template | How is DATABASE_URL turned into a URL that uses the psycopg driver? | 1 | 1 | 1 | 1 | backend/app/core/config.py | DATABASE_URL; psycopg_url; url_for_psycopg |
| full-stack-fastapi-template | How does update_user_me stop someone taking an email that's already used? | 1 | 1 | 1 | 2 | backend/app/api/routes/users.py | update_user_me logic; duplicate email check; email uniqueness update_user_me |
| full-stack-fastapi-template | How do I deploy this with Docker Compose and Traefik? | 1 | 1 | 1 | 1 | deployment-docker-compose.md | docker-compose.yml; traefik configuration; deploy with docker-compose |
| full-stack-fastapi-template | Where is the useAuth hook's logout implemented? | 1 | 1 | 1 | 1 | frontend/src/hooks/useAuth.ts | useauth logout; auth/logout implementation; auth hook logout code |
| fragments | How is generated code run in a sandbox? | 1 | 1 | 1 | 1 | app/api/sandbox/route.ts | run generated code; sandbox execution environment; code sandbox setup |
| fragments | How is the model's code answer streamed back as structured data? | 8 | 1 | 2 | 3 | app/api/morph-chat/route.ts | stream model answer; structured data model; model output format |
| fragments | How are users limited in how many requests they can send? | 2 | 2 | 1 | 1 | app/api/chat/route.ts | user request limit; request quota users; max user requests |
| fragments | How is the system prompt built from the available templates? | 2 | 1 | 1 | 1 | lib/prompt.ts | build system prompt; available templates function; prompt construction method |
| fragments | Which app templates can the user pick, like Next.js or Streamlit? | 1 | 3 | 2 | 1 | README.md | app_templates; template_options; supported_app_types |
| fragments | How do sign-in and accounts work? | 1 | 1 | 1 | 1 | components/auth.tsx | accounts sign-in; user accounts; login system |
| fragments | How does sharing a fragment create a short link? | 0 | 0 | 0 | 8 | components/fragment-web.tsx | short link creation; fragment to URL mapping; URL shortening function |
| fragments | How do short links redirect to the running app? | 6 | 0 | 5 | 1 | components/auth.tsx | link redirection; app routing; url mapping |
| fragments | How are fast edits applied to existing code instead of regenerating it all? | 2 | 8 | 2 | 2 | lib/schema.ts | fast edits code; code regeneration; existing code changes |
| fragments | How are disposable email addresses rejected at sign-up? | 7 | 2 | 2 | 2 | components/auth.tsx | email validation; disposable email rejection; sign-up email filter |
| fragments | What fields does the model's structured answer contain? | 3 | 0 | 9 | 0 | lib/models.ts | model_answer_fields; structured_response_fields; model_answer_data_keys |
| fragments | How does getModelClient pick the provider SDK for a model? | 1 | 1 | 1 | 1 | lib/models.ts | getModelClient logic; model provider SDK selection; model client SDK choice |
| fragments | Where is E2B_API_KEY configured? | 1 | 1 | 1 | 3 | README.md | E2B_API_KEY; config; environment_variable |
| fragments | What happens when KV_REST_API_URL isn't set? | 1 | 1 | 1 | 1 | lib/ratelimit.ts | kv_rest_api_url not set; handle kv_rest_api_url missing; api_url_behavior_no_env_var |
| fragments | Where are RATE_LIMIT_MAX_REQUESTS and RATE_LIMIT_WINDOW read? | 1 | 3 | 1 | 1 | app/api/chat/route.ts | RATE_LIMIT_MAX_REQUESTS read; RATE_LIMIT_WINDOW read; read RATE_LIMIT_MAX_REQUESTS and RATE_LIMIT_WINDOW |
| fragments | How are API errors turned into responses for the client? | 2 | 1 | 1 | 1 | lib/api-errors.ts | api errors handler; error response api; client error response code |
| fragments | How is the Streamlit sandbox template built? | 3 | 1 | 2 | 3 | sandbox-templates/streamlit-developer/app.py | streamlit sandbox; streamlit template build; streamlit sandbox code |
| fragments | How do I add a new template of my own? | 1 | 1 | 1 | 4 | README.md | new template; add template; template creation |
| fragments | How does the app show the running web app next to the code? | 0 | 0 | 0 | 0 | README.md | app display; web app side by side; runtime preview |
| fragments | Where is the request body for the chat endpoint validated? | 4 | 1 | 1 | 1 | lib/request-schema.ts | request body chat endpoint; validate chat endpoint request; chat endpoint validation logic |
