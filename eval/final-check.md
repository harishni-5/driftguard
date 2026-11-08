# Final test: hand-checked findings on 22 unseen repos

Date: 2026-11-08. DriftGuard 0.1.0, static analysis only (`--no-llm`), with no rule changes between running and judging.
Every row was judged by reading the evidence and, where needed, the code or doc around it.
**T** = real, **F** = false alarm, **U** = unsure (left out of precision). Repos and exact commits are listed at the end.

| Category | Checked | Real | False | Unsure | Precision |
|---|---:|---:|---:|---:|---:|
| Competing libraries (all) | 40 | 21 | 15 | 4 | **58%** |
| Unused dependencies (all) | 161 | 95 | 61 | 5 | **61%** |
| Docs vs code (all) | 89 | 39 | 50 | 0 | **44%** |
| Copied code (random 50 of 378, seed 20261108) | 50 | 28 | 22 | 0 | **56%** |
| Needs-review leads (random 30 of 2,543, seed 20261109) | 30 | 4 | 24 | 2 | **14%** |

## Competing libraries (all)

| Repo | Finding | Where | Verdict | Reason |
|---|---|---|---|---|
| langchain-ai_open-canvas | 2 icon libraries in apps/web: lucide, react-icons | apps/web/src/components/chat-interface/thread-history.tsx:9 | T | Same job done with different libraries in one project |
| langchain-ai_local-deep-researcher | 2 HTTP clients in this project: httpx, requests | src/ollama_deep_researcher/utils.py:3 | T | Same job done with different libraries in one project |
| langchain-ai_chat-langchain | 2 HTTP clients in this project: httpx, requests | src/tools/pylon_tools.py:12 | T | Same job done with different libraries in one project |
| Pythagora-io_gpt-pilot | 2 database layers in core/db: SQLAlchemy, raw sqlite3 | core/db/fix_migrations.py:3 | F | One-off migration fix script needs raw access |
| Pythagora-io_gpt-pilot | 2 database layers in core/templates/tree/react_express: Mongoose, Prisma | core/templates/tree/react_express/api/models/init.js:22 | F | Template offers both databases on purpose |
| Pythagora-io_gpt-pilot | 2 database layers in core/templates/tree/vite_react/server: Mongoose, Prisma | core/templates/tree/vite_react/server/models/init.js:19 | F | Template offers both databases on purpose |
| Pythagora-io_gpt-pilot | 2 logging approaches in this project: print, logging | core/log/__init__.py:3 | F | print is console UI output, not logging |
| smol-ai_developer | 2 logging approaches in this project: print, logging | smol_dev/prompts.py:13 | F | print is CLI output |
| yoheinakajima_babyagi | 2 logging approaches in this project: print, logging | babyagi/api/__init__.py:6 | T | Same job done with different libraries in one project |
| qodo-ai_pr-agent | 4 HTTP clients in this project: requests, aiohttp, urllib, httpx | pr_agent/algo/ai_handlers/litellm_ai_handler.py:16 | T | Same job done with different libraries in one project |
| qodo-ai_pr-agent | 3 logging approaches in this project: print, logging, loguru | pr_agent/log/__init__.py:9 | F | loguru is the logger; logging is interop; print is CLI output |
| OpenBMB_ChatDev | 2 logging approaches in this project: logging, print | check/check.py:91 | U | Mixed: CLI check scripts and runtime code |
| run-llama_create-llama | 2 HTTP clients in packages/create-llama: got, fetch | packages/create-llama/helpers/index.ts:112 | T | Same job done with different libraries in one project |
| run-llama_create-llama | 2 logging approaches in packages/create-llama: logging, print | packages/create-llama/templates/components/agents/python/blog/app/workflows/planner.py:122 | F | Template code |
| run-llama_create-llama | 2 logging approaches in python/llama-index-server: logging, print | python/llama-index-server/llama_index/server/gen_ui/parse_workflow_code.py:55 | T | Same job done with different libraries in one project |
| arc53_DocsGPT | 3 HTTP clients in docsgpt: requests, urllib, httpx | docsgpt/connectors/mcp.py:360 | T | Same job done with different libraries in one project |
| arc53_DocsGPT | 2 database layers in docsgpt: SQLAlchemy, raw psycopg | docsgpt/agents/tools/postgres.py:3 | F | Postgres tool connects to user databases on purpose |
| arc53_DocsGPT | 2 validation libraries in docsgpt: pydantic, jsonschema | docsgpt/agents/tools/artifact_generator.py:37 | F | jsonschema validates dynamic tool schemas on purpose |
| arc53_DocsGPT | 2 logging approaches in docsgpt: logging, print | docsgpt/api/reference.py:143 | U | Mixed: CLI entry points and library code |
| QuivrHQ_quivr | 2 logging approaches in plugins/alerts: print, logging | plugins/alerts/alerts/__main__.py:2 | F | print in calibration scripts |
| zylon-ai_private-gpt | 3 HTTP clients in this project: httpx, requests, aiohttp | private_gpt/components/readers/docling/api_clients.py:9 | T | Same job done with different libraries in one project |
| zylon-ai_private-gpt | 2 database layers in this project: SQLAlchemy, raw psycopg | private_gpt/components/node_store/patched_postgres_kv_store.py:3 | F | Patched third-party store needs raw access |
| stanford-oval_storm | 2 HTTP clients in this project: requests, httpx | knowledge_storm/utils.py:3 | T | Same job done with different libraries in one project |
| stanford-oval_storm | 2 logging approaches in this project: print, logging | knowledge_storm/interface.py:6 | T | Same job done with different libraries in one project |
| TransformerOptimus_SuperAGI | 3 HTTP clients in this project: requests, aiohttp, httpx | superagi/tools/searx/search_scraper.py:3 | T | Same job done with different libraries in one project |
| TransformerOptimus_SuperAGI | 2 HTTP clients in gui: axios, fetch | gui/utils/utils.js:128 | T | Same job done with different libraries in one project |
| TransformerOptimus_SuperAGI | 2 date libraries in gui: moment, date-fns | gui/utils/utils.js:1 | T | Same job done with different libraries in one project |
| TransformerOptimus_SuperAGI | 2 logging approaches in this project: print, logging | superagi/agent/agent_iteration_step_handler.py:5 | T | Same job done with different libraries in one project |
| mem0ai_mem0 | 3 HTTP clients in this project: urllib, httpx, requests | mem0/client/main.py:10 | T | Same job done with different libraries in one project |
| mem0ai_mem0 | 3 database layers in mem0-ts/src/oss: raw mongodb, raw mysql, raw pg | mem0-ts/src/oss/src/vector_stores/pgvector.ts:1 | F | One adapter per database, on purpose |
| mem0ai_mem0 | 3 database layers in this project: raw sqlite3, raw psycopg, raw pymysql | mem0/vector_stores/azure_mysql.py:10 | F | One adapter per database, on purpose |
| mem0ai_mem0 | 2 HTTP clients in cli/python: httpx, urllib | cli/python/src/mem0_cli/telemetry_sender.py:21 | U | Telemetry sender may avoid dependencies on purpose |
| mem0ai_mem0 | 2 HTTP clients in integrations/hermes-plugin-mem0: httpx, urllib | integrations/hermes-plugin-mem0/_setup.py:16 | F | Setup script avoids dependencies on purpose |
| mem0ai_mem0 | 2 HTTP clients in mem0-ts/src/oss: fetch, axios | mem0-ts/src/oss/src/vector_stores/databricks.ts:1 | T | Same job done with different libraries in one project |
| mem0ai_mem0 | 2 HTTP clients in mem0-ts: fetch, axios | mem0-ts/src/client/mem0.ts:1 | T | Same job done with different libraries in one project |
| mem0ai_mem0 | 2 HTTP clients in server/dashboard: fetch, axios | server/dashboard/src/lib/error-message.ts:1 | T | Same job done with different libraries in one project |
| mem0ai_mem0 | 2 logging approaches in this project: logging, print | integrations/agent-plugin-core/conformance/artifacts.py:53 | U | Mixed: conformance scripts and library code |
| Aider-AI_aider | 2 HTTP clients in this project: requests, httpx | aider/main.py:520 | T | Same job done with different libraries in one project |
| cline_cline | 2 HTTP clients in apps/vscode: axios, fetch | apps/vscode/src/core/controller/marketplace/marketplace-helpers.ts:123 | T | Same job done with different libraries in one project |
| cline_cline | 2 logging approaches in apps/cli: console, pino | apps/cli/src/logging/adapter.ts:12 | F | console is CLI output |

## Unused dependencies (all)

| Repo | Finding | Where | Verdict | Reason |
|---|---|---|---|---|
| langchain-ai_open-canvas | Possibly unused dependency: @blocknote/mantine | apps/web/package.json:25 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: @ffmpeg/ffmpeg | apps/agents/package.json:25 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: @ffmpeg/util | apps/agents/package.json:26 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: @langchain/google-genai | apps/agents/package.json:31 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: @langchain/groq | apps/agents/package.json:32 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: @langchain/ollama | apps/agents/package.json:35 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: @mendable/firecrawl-js | apps/agents/package.json:37 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: @mendable/firecrawl-js | apps/web/package.json:43 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: @radix-ui/react-navigation-menu | apps/web/package.json:54 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: @uiw/react-md-editor | apps/web/package.json:67 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: @vercel/kv | apps/web/package.json:68 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: dotenv | apps/agents/package.json:41 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: framer-motion | apps/agents/package.json:43 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: groq-sdk | apps/agents/package.json:44 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: lodash | apps/agents/package.json:47 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: pdf-parse | apps/web/package.json:83 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: react-json-view | apps/web/package.json:88 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: react-markdown | apps/web/package.json:89 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dependency: zod | apps/web/package.json:99 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dev dependency: @langchain/langgraph-cli | apps/agents/package.json:54 | F | Tool run by command name or used by convention |
| langchain-ai_open-canvas | Possibly unused dev dependency: typescript-eslint | apps/agents/package.json:68 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dev dependency: typescript-eslint | apps/web/package.json:123 | T | Never referenced in its own project |
| langchain-ai_open-canvas | Possibly unused dev dependency: typescript-eslint | packages/shared/package.json:49 | T | Never referenced in its own project |
| langchain-ai_local-deep-researcher | Possibly unused dependency: python-dotenv | pyproject.toml:22 | T | Never referenced in its own project |
| Pythagora-io_gpt-pilot | Possibly unused dependency: body-parser | core/templates/tree/node_express_mongoose/package.json:15 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: chart.js | core/templates/tree/node_express_mongoose/package.json:16 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: connect-flash | core/templates/tree/node_express_mongoose/package.json:17 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: csv-writer | core/templates/tree/node_express_mongoose/package.json:18 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: moment | core/templates/tree/node_express_mongoose/package.json:24 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: @hookform/resolvers | core/templates/tree/vite_react/client/package.json:14 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: @hookform/resolvers | core/templates/tree/vite_react_swagger/client/package.json:14 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: body-parser | core/templates/tree/vite_react/server/package.json:18 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: chart.js | core/templates/tree/vite_react/server/package.json:19 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: connect-flash | core/templates/tree/vite_react/server/package.json:20 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: csv-writer | core/templates/tree/vite_react/server/package.json:23 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: date-fns | core/templates/tree/vite_react/client/package.json:46 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: date-fns | core/templates/tree/vite_react_swagger/client/package.json:46 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: ejs | core/templates/tree/vite_react/server/package.json:25 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: moment | core/templates/tree/vite_react/server/package.json:28 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: pino | core/templates/tree/vite_react/server/package.json:31 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: zod | core/templates/tree/vite_react/client/package.json:62 | F | Template project: its code is generated later |
| Pythagora-io_gpt-pilot | Possibly unused dependency: zod | core/templates/tree/vite_react_swagger/client/package.json:62 | F | Template project: its code is generated later |
| nextjs_saas-starter | Possibly unused dependency: server-only | package.json:33 | T | Never referenced in its own project |
| steven-tey_novel | Possibly unused dependency: @radix-ui/react-select | apps/web/package.json:18 | T | Never referenced in its own project |
| steven-tey_novel | Possibly unused dependency: eventsource-parser | apps/web/package.json:31 | T | Never referenced in its own project |
| steven-tey_novel | Possibly unused dependency: react-markdown | packages/headless/package.json:47 | T | Never referenced in its own project |
| steven-tey_novel | Possibly unused dependency: tippy.js | apps/web/package.json:45 | T | Never referenced in its own project |
| steven-tey_novel | Possibly unused dev dependency: @biomejs/biome | package.json:26 | F | Tool run by command name or used by convention |
| qodo-ai_pr-agent | Possibly unused dependency: @mdx-js/react | docs/package.json:23 | T | Never referenced in its own project |
| qodo-ai_pr-agent | Possibly unused dependency: react-dom | docs/package.json:27 | T | Never referenced in its own project |
| OpenBMB_ChatDev | Possibly unused dependency: @vue-flow/minimap | frontend/package.json:16 | T | Never referenced in its own project |
| OpenBMB_ChatDev | Possibly unused dependency: beautifulsoup4 | pyproject.toml:29 | T | Never referenced in its own project |
| OpenBMB_ChatDev | Possibly unused dependency: beautifulsoup4 | requirements.txt:17 | T | Never referenced in its own project |
| run-llama_create-llama | Possibly unused dependency: @llamaindex/tools | packages/create-llama/templates/types/llamaindexserver/nextjs/package.json:16 | F | Template project: its code is generated later |
| run-llama_create-llama | Possibly unused dependency: @llamaindex/workflow | packages/create-llama/templates/types/llamaindexserver/nextjs/package.json:15 | F | Template project: its code is generated later |
| run-llama_create-llama | Possibly unused dependency: async-retry | packages/create-llama/package.json:45 | T | Never referenced in its own project |
| run-llama_create-llama | Possibly unused dependency: async-sema | packages/create-llama/package.json:46 | T | Never referenced in its own project |
| run-llama_create-llama | Possibly unused dependency: cachetools | packages/create-llama/templates/components/reflex/extractor/pyproject.toml:15 | F | Template project: its code is generated later |
| run-llama_create-llama | Possibly unused dependency: dotenv | packages/create-llama/templates/components/ts-proxy/package.json:10 | F | Template project: its code is generated later |
| run-llama_create-llama | Possibly unused dependency: llama-parse | packages/create-llama/templates/components/reflex/extractor/pyproject.toml:14 | F | Template project: its code is generated later |
| run-llama_create-llama | Possibly unused dependency: python-dotenv | packages/create-llama/templates/components/reflex/contract_review/pyproject.toml:10 | F | Template project: its code is generated later |
| run-llama_create-llama | Possibly unused dependency: python-dotenv | packages/create-llama/templates/components/reflex/extractor/pyproject.toml:11 | F | Template project: its code is generated later |
| run-llama_create-llama | Possibly unused dependency: tar | packages/create-llama/package.json:58 | T | Never referenced in its own project |
| run-llama_create-llama | Possibly unused dependency: yaml | packages/create-llama/package.json:62 | T | Never referenced in its own project |
| run-llama_create-llama | Possibly unused dependency: zod | packages/create-llama/templates/types/llamaindexserver/nextjs/package.json:19 | F | Template project: its code is generated later |
| run-llama_create-llama | Possibly unused dev dependency: bunchee | package.json:36 | F | Tool run by command name or used by convention |
| arc53_DocsGPT | Possibly unused dev dependency: @parcel/packager-ts | extensions/react-widget/package.json:60 | T | Never referenced in its own project |
| arc53_DocsGPT | Possibly unused dev dependency: @parcel/transformer-typescript-types | extensions/react-widget/package.json:64 | T | Never referenced in its own project |
| arc53_DocsGPT | Possibly unused dev dependency: flow-bin | extensions/react-widget/package.json:79 | T | Never referenced in its own project |
| arc53_DocsGPT | Possibly unused dev dependency: svgo | extensions/react-widget/package.json:86 | U | Could be optional, pinned or used indirectly |
| QuivrHQ_quivr | Possibly unused dependency: python-dateutil | contracts/http/v0/checks/requirements-lock.txt:27 | F | Lockfile-style requirements file read as a manifest |
| QuivrHQ_quivr | Possibly unused dependency: python-dotenv | contracts/http/v0/checks/requirements-lock.txt:29 | F | Lockfile-style requirements file read as a manifest |
| QuivrHQ_quivr | Possibly unused dev dependency: python-dotenv | scripts/ci-requirements-base.txt:16 | F | Lockfile-style requirements file read as a manifest |
| Nutlope_llamacoder | Possibly unused dependency: @base-ui/utils | preview-kits/baseui/package.json:10 | F | Template project: its code is generated later |
| Nutlope_llamacoder | Possibly unused dependency: @conform-to/zod | package.json:23 | T | Never referenced in its own project |
| Nutlope_llamacoder | Possibly unused dependency: @date-fns/tz | preview-kits/baseui/package.json:11 | F | Template project: its code is generated later |
| Nutlope_llamacoder | Possibly unused dependency: @floating-ui/react-dom | preview-kits/baseui/package.json:12 | F | Template project: its code is generated later |
| Nutlope_llamacoder | Possibly unused dependency: @floating-ui/utils | preview-kits/baseui/package.json:13 | F | Template project: its code is generated later |
| Nutlope_llamacoder | Possibly unused dependency: @vercel/og | package.json:34 | T | Never referenced in its own project |
| Nutlope_llamacoder | Possibly unused dependency: date-fns | preview-kits/baseui/package.json:18 | F | Template project: its code is generated later |
| Nutlope_llamacoder | Possibly unused dependency: framer-motion | preview-kits/baseui/package.json:20 | F | Template project: its code is generated later |
| Nutlope_llamacoder | Possibly unused dependency: react-dom | preview-kits/baseui/package.json:26 | F | Template project: its code is generated later |
| Nutlope_llamacoder | Possibly unused dependency: shiki | package.json:57 | T | Never referenced in its own project |
| Nutlope_llamacoder | Possibly unused dependency: use-sync-external-store | preview-kits/baseui/package.json:33 | F | Template project: its code is generated later |
| Nutlope_llamacoder | Possibly unused dependency: vaul | preview-kits/baseui/package.json:34 | F | Template project: its code is generated later |
| Nutlope_aicommits | Possibly unused dev dependency: conventional-changelog-conventionalcommits | package.json:51 | F | Tool run by command name or used by convention |
| TransformerOptimus_SuperAGI | Possibly unused dependency: echarts-for-react | gui/package.json:18 | T | Never referenced in its own project |
| TransformerOptimus_SuperAGI | Possibly unused dependency: react-draggable | gui/package.json:31 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: @hookform/resolvers | server/dashboard/package.json:15 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: @mem0/vercel-ai-provider | examples/multimodal-demo/package.json:13 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: @tanstack/react-table | server/dashboard/package.json:39 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: axios | mem0-ts/src/community/package.json:80 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: framer-motion | server/dashboard/package.json:45 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: js-cookie | examples/mem0-demo/package.json:29 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: lodash | server/dashboard/package.json:46 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: mem0ai | examples/vercel-ai-sdk-chat-app/package.json:31 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: partial-json | integrations/vercel-ai-sdk/package.json:43 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: react-syntax-highlighter | server/dashboard/package.json:56 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: recharts | server/dashboard/package.json:57 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: use-debounce | server/dashboard/package.json:62 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: uuid | mem0-ts/src/community/package.json:82 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: zod | examples/multimodal-demo/package.json:34 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: zod | examples/vercel-ai-sdk-chat-app/package.json:34 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: zod | mem0-ts/src/community/package.json:83 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dependency: zod | server/dashboard/package.json:65 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dev dependency: @edge-runtime/vm | integrations/vercel-ai-sdk/package.json:47 | F | Tool run by command name or used by convention |
| mem0ai_mem0 | Possibly unused dev dependency: @vitest/coverage-v8 | integrations/openclaw/package.json:59 | F | Tool run by command name or used by convention |
| mem0ai_mem0 | Possibly unused dev dependency: dotenv | mem0-ts/src/community/package.json:68 | T | Never referenced in its own project |
| mem0ai_mem0 | Possibly unused dev dependency: fix-tsup-cjs | mem0-ts/package.json:93 | U | Could be optional, pinned or used indirectly |
| mem0ai_mem0 | Possibly unused optional dependency: mem0ai | cli/python/pyproject.toml:34 | U | Could be optional, pinned or used indirectly |
| Aider-AI_aider | Possibly unused dependency: scikit-learn | requirements/requirements-help.in:12 | U | Could be optional, pinned or used indirectly |
| cline_cline | Possibly unused dependency: @ai-sdk/otel | sdk/packages/llms/package.json:61 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @ai-sdk/react | apps/examples/vscode/package.json:53 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @bufbuild/protobuf | apps/vscode/package.json:465 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @cline/llms | apps/examples/vscode/package.json:55 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @floating-ui/react | apps/vscode/webview-ui/package.json:21 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @fontsource-variable/geist-mono | apps/vscode/webview-ui/package.json:22 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @grpc/grpc-js | apps/vscode/standalone/runtime-files/package.json:6 | F | Shipped runtime bundle: used by compiled code elsewhere |
| cline_cline | Possibly unused dependency: @grpc/reflection | apps/vscode/standalone/runtime-files/package.json:7 | F | Shipped runtime bundle: used by compiled code elsewhere |
| cline_cline | Possibly unused dependency: @hookform/resolvers | apps/examples/desktop-app/package.json:50 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @paper-design/shaders-react | apps/vscode/webview-ui/package.json:25 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @radix-ui/react-slider | apps/vscode/webview-ui/package.json:33 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @shikijs/langs | apps/examples/desktop-app/package.json:78 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @shikijs/themes | apps/examples/desktop-app/package.json:79 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @streamparser/json | apps/vscode/package.json:493 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @streamparser/json | sdk/packages/llms/package.json:75 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: @vscode/codicons | apps/vscode/package.json:496 | F | Shipped as an extension asset (.vscodeignore) |
| cline_cline | Possibly unused dependency: clone-deep | apps/vscode/package.json:504 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: date-fns | apps/examples/desktop-app/package.json:91 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: default-shell | apps/vscode/package.json:505 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: firebase | apps/vscode/webview-ui/package.json:43 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: framer-motion | apps/vscode/webview-ui/package.json:44 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: grpc-health-check | apps/vscode/standalone/runtime-files/package.json:9 | F | Shipped runtime bundle: used by compiled code elsewhere |
| cline_cline | Possibly unused dependency: magic-string | apps/vscode/webview-ui/package.json:48 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: mermaid | apps/cline-hub/src/webview/package.json:32 | F | Loaded by another package (peer dependency) |
| cline_cline | Possibly unused dependency: nice-grpc-common | apps/vscode/package.json:525 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: open | apps/vscode/standalone/runtime-files/package.json:10 | F | Shipped runtime bundle: used by compiled code elsewhere |
| cline_cline | Possibly unused dependency: os-name | apps/vscode/package.json:530 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: p-mutex | apps/vscode/package.json:531 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: p-timeout | apps/vscode/package.json:532 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: posthog-node | apps/cli/package.json:91 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: posthog-node | apps/examples/desktop-app/package.json:99 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: radix-ui | apps/examples/desktop-app/package.json:100 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: react-reconciler | apps/cli/package.json:94 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: shell-quote | apps/vscode/package.json:541 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: shiki | apps/examples/desktop-app/package.json:106 | F | Loaded by another package (peer dependency) |
| cline_cline | Possibly unused dependency: strip-ansi | apps/vscode/package.json:543 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: tiktoken | evals/package.json:14 | T | Never referenced in its own project |
| cline_cline | Possibly unused dependency: vscode-uri | apps/vscode/standalone/runtime-files/package.json:11 | F | Shipped runtime bundle: used by compiled code elsewhere |
| cline_cline | Possibly unused dependency: zod-to-json-schema | sdk/packages/shared/package.json:65 | T | Never referenced in its own project |
| cline_cline | Possibly unused dev dependency: @bufbuild/buf | apps/vscode/package.json:418 | F | Tool run by command name or used by convention |
| cline_cline | Possibly unused dev dependency: @microsoft/tui-test | apps/vscode/package.json:419 | F | Tool run by command name or used by convention |
| cline_cline | Possibly unused dev dependency: @vitest/coverage-v8 | apps/vscode/webview-ui/package.json:87 | F | Tool run by command name or used by convention |
| cline_cline | Possibly unused dev dependency: @vscode/vsce | apps/vscode/package.json:437 | F | Tool run by command name or used by convention |
| cline_cline | Possibly unused dev dependency: dotenv | apps/vscode/package.json:441 | U | Could be optional, pinned or used indirectly |
| cline_cline | Possibly unused dev dependency: prebuild-install | apps/vscode/package.json:450 | F | Tool run by command name or used by convention |
| cline_cline | Possibly unused dev dependency: protoc-gen-ts | apps/vscode/package.json:451 | F | Tool run by command name or used by convention |
| cline_cline | Possibly unused optional dependency: @swc/core-linux-x64-gnu | apps/vscode/webview-ui/package.json:103 | F | Platform-specific native binary, declared on purpose |
| cline_cline | Possibly unused optional dependency: lightningcss-linux-x64-gnu | apps/vscode/webview-ui/package.json:105 | F | Platform-specific native binary, declared on purpose |
| cline_cline | Possibly unused optional dependency: lightningcss-win32-x64-msvc | apps/vscode/webview-ui/package.json:106 | F | Platform-specific native binary, declared on purpose |
| cline_cline | Possibly unused peer dependency: @aws-sdk/client-bedrock-runtime | sdk/packages/llms/package.json:42 | T | Never referenced in its own project |

## Docs vs code (all)

| Repo | Finding | Where | Verdict | Reason |
|---|---|---|---|---|
| Pythagora-io_gpt-pilot | Documented command has no script: npm run worker | core/templates/tree/react_express/README.md:69 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: ../../../../../yaml_instance/file_tool_use_case.yaml | docs/user_guide/en/modules/tooling/function_catalog.md:41 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: ../../../../../yaml_instance/file_tool_use_case.yaml | docs/user_guide/zh/modules/tooling/function_catalog.md:41 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: ../edges.md | docs/user_guide/en/dynamic_execution.md:274 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: ../edges.md | docs/user_guide/zh/dynamic_execution.md:274 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: ../nodes/agent.md | docs/user_guide/en/dynamic_execution.md:276 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: ../nodes/agent.md | docs/user_guide/zh/dynamic_execution.md:276 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: ../workflow_authoring.md | docs/user_guide/en/dynamic_execution.md:275 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: ../workflow_authoring.md | docs/user_guide/zh/dynamic_execution.md:275 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: ./docs/user_guide/en/modules/tooling/index.md | README.md:280 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: ./docs/user_guide/zh/modules/tooling/index.md | README-zh.md:279 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: wiki.md#art | README-zh.md:86 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: wiki.md#art | README.md:87 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: wiki.md#co-tracking | README-zh.md:53 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: wiki.md#co-tracking | README.md:54 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: wiki.md#docker-start | README-zh.md:69 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: wiki.md#docker-start | README.md:70 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: wiki.md#git-mode | README-zh.md:74 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: wiki.md#git-mode | README.md:75 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: wiki.md#human-agent-interaction | README-zh.md:80 | T | Really missing or wrongly pathed |
| OpenBMB_ChatDev | Broken link: wiki.md#human-agent-interaction | README.md:81 | T | Really missing or wrongly pathed |
| run-llama_create-llama | Broken link: ../frontend/README.md | packages/create-llama/templates/components/agents/python/form_filling/README-template.md:24 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: ./app/workflow.py | packages/create-llama/templates/components/use-cases/python/deep_research/README-template.md:84 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: ./app/workflow.py | packages/create-llama/templates/components/use-cases/python/financial_report/README-template.md:42 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: ./data/gdpr.pdf | packages/create-llama/templates/components/reflex/contract_review/README-template.md:42 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: ./src/workflow.py | packages/create-llama/templates/components/use-cases/python/agentic_rag/README-template.md:84 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: ./ui/components/ui_event.jsx | packages/create-llama/templates/components/use-cases/python/document_generator/README-template.md:90 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: DEPLOY.md | packages/create-llama/templates/components/agents/python/deep_research/README-template.md:39 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: DEPLOY.md | packages/create-llama/templates/components/agents/python/financial_report/README-template.md:48 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: DEPLOY.md | packages/create-llama/templates/components/agents/python/form_filling/README-template.md:54 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: app/settings.py | packages/create-llama/templates/components/use-cases/python/financial_report/README-template.md:38 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: app/workflow.ts | packages/create-llama/templates/components/use-cases/typescript/code_generator/README-template.md:35 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: llama_deploy.yml | packages/create-llama/templates/components/use-cases/python/agentic_rag/README-template.md:96 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: llama_deploy.yml | packages/create-llama/templates/components/use-cases/python/code_generator/README-template.md:89 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: llama_deploy.yml | packages/create-llama/templates/components/use-cases/python/deep_research/README-template.md:96 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: llama_deploy.yml | packages/create-llama/templates/components/use-cases/python/document_generator/README-template.md:88 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: src/app/settings.ts | packages/create-llama/templates/components/use-cases/typescript/agentic_rag/README-template.md:30 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: src/app/settings.ts | packages/create-llama/templates/components/use-cases/typescript/code_generator/README-template.md:21 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: src/app/settings.ts | packages/create-llama/templates/components/use-cases/typescript/deep_research/README-template.md:30 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: src/settings.py | packages/create-llama/templates/components/use-cases/python/agentic_rag/README-template.md:11 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: src/settings.py | packages/create-llama/templates/components/use-cases/python/code_generator/README-template.md:11 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: src/settings.py | packages/create-llama/templates/components/use-cases/python/deep_research/README-template.md:11 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: src/settings.py | packages/create-llama/templates/components/use-cases/python/document_generator/README-template.md:11 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: src/workflow.py | packages/create-llama/templates/components/use-cases/python/agentic_rag/README-template.md:85 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: src/workflow.py | packages/create-llama/templates/components/use-cases/python/code_generator/README-template.md:78 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: src/workflow.py | packages/create-llama/templates/components/use-cases/python/deep_research/README-template.md:85 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: src/workflow.py | packages/create-llama/templates/components/use-cases/python/document_generator/README-template.md:77 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: ui/index.ts | packages/create-llama/templates/components/use-cases/python/agentic_rag/README-template.md:89 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: ui/index.ts | packages/create-llama/templates/components/use-cases/python/code_generator/README-template.md:82 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: ui/index.ts | packages/create-llama/templates/components/use-cases/python/deep_research/README-template.md:89 | F | Template doc for a generated project; the link works in the generated project |
| run-llama_create-llama | Broken link: ui/index.ts | packages/create-llama/templates/components/use-cases/python/document_generator/README-template.md:81 | F | Template doc for a generated project; the link works in the generated project |
| arc53_DocsGPT | Documented command runs a missing file: scripts/e2e/compute_hash.py | scripts/e2e/mock_llm_fixtures/README.md:82 | T | Really missing or wrongly pathed |
| QuivrHQ_quivr | Documented command has no script: pnpm run generate | docs/dated/research/forge-generation-pipeline.md:123 | T | Really broken (in dated/historical notes, so low value) |
| QuivrHQ_quivr | Broken link: ../../../multimodal-rag/backend/multimodal_rag_api/docker-compose.yml | docs/dated/research/meilisearch-for-quivr.md:231 | T | Really broken (in dated/historical notes, so low value) |
| QuivrHQ_quivr | Broken link: ../../../multimodal-rag/backend/multimodal_rag_api/src/multimodal_rag_api/api/services/meilisearch/client.py | docs/dated/research/meilisearch-for-quivr.md:231 | T | Really broken (in dated/historical notes, so low value) |
| QuivrHQ_quivr | Broken link: ../../../multimodal-rag/backend/multimodal_rag_api/src/multimodal_rag_api/api/services/search_helpers.py | docs/dated/research/meilisearch-for-quivr.md:231 | T | Really broken (in dated/historical notes, so low value) |
| QuivrHQ_quivr | Broken link: ../../../multimodal-rag/backend/multimodal_rag_api/src/multimodal_rag_api/temporal_worker/activities.py | docs/dated/research/meilisearch-for-quivr.md:231 | T | Really broken (in dated/historical notes, so low value) |
| QuivrHQ_quivr | Broken link: ../../../tests/measurement/workload-v1.json | docs/dated/evidence/the-661-retrieval-baseline.md:7 | T | Really broken (in dated/historical notes, so low value) |
| QuivrHQ_quivr | Broken link: ../../../tests/measurement/workload-v1.json | docs/dated/evidence/the-675-query-floor.md:14 | T | Really broken (in dated/historical notes, so low value) |
| QuivrHQ_quivr | Broken link: ../../connectors/rss.md | docs/dated/design/quivr-v2-ingestion-contracts.md:431 | T | Really broken (in dated/historical notes, so low value) |
| QuivrHQ_quivr | Broken link: ../../connectors/x.md | docs/dated/design/quivr-v2-ingestion-contracts.md:432 | T | Really broken (in dated/historical notes, so low value) |
| QuivrHQ_quivr | Broken link: CHANGELOG.md | .agents/skills/humanizer/README.md:200 | T | Really broken (in dated/historical notes, so low value) |
| Nutlope_aicommits | Documented command runs a missing file: ./dist/cli.mjs | CONTRIBUTING.md:48 | F | Build output (dist/) that exists after the documented build step |
| mem0ai_mem0 | Documented command runs a missing file: agent_memory.sh | skills/mem0-cli/references/workflows.md:443 | F | Tutorial tells the reader to save this file first |
| mem0ai_mem0 | Documented command runs a missing file: dist/index.js | cli/node/README.md:322 | F | Build output (dist/) that exists after the documented build step |
| mem0ai_mem0 | Documented command runs a missing file: dist/index.js | cli/node/development.md:41 | F | Build output (dist/) that exists after the documented build step |
| mem0ai_mem0 | Documented command runs a missing file: gemini_agent.py | docs/cookbooks/frameworks/gemini-3-with-mem0-mcp.mdx:156 | F | Tutorial tells the reader to save this file first |
| mem0ai_mem0 | Documented command runs a missing file: mem0-livekit-voice-agent.py | docs/integrations/livekit.mdx:193 | F | Tutorial tells the reader to save this file first |
| Aider-AI_aider | Broken link: commands.html | aider/website/docs/usage/tips.md:38 | F | Docs-site link to a built .html page; works on the website |
| Aider-AI_aider | Broken link: conventions.html | aider/website/docs/usage/tips.md:72 | F | Docs-site link to a built .html page; works on the website |
| Aider-AI_aider | Broken link: edit.html | aider/website/docs/leaderboards/edit.md:13 | F | Docs-site link to a built .html page; works on the website |
| Aider-AI_aider | Broken link: lint-test.html | aider/website/docs/usage/tips.md:58 | F | Docs-site link to a built .html page; works on the website |
| Aider-AI_aider | Broken link: modes.html | aider/website/docs/usage/tips.md:30 | F | Docs-site link to a built .html page; works on the website |
| Aider-AI_aider | Broken link: warnings.html | aider/website/docs/llms/lm-studio.md:37 | F | Docs-site link to a built .html page; works on the website |
| Aider-AI_aider | Broken link: warnings.html | aider/website/docs/llms/ollama.md:40 | F | Docs-site link to a built .html page; works on the website |
| Aider-AI_aider | Broken link: warnings.html | aider/website/docs/llms/openai-compat.md:37 | F | Docs-site link to a built .html page; works on the website |
| Aider-AI_aider | Broken link: warnings.html | aider/website/docs/llms/other.md:30 | F | Docs-site link to a built .html page; works on the website |
| cline_cline | Documented command has no script: bun run examples | sdk/examples/plugins/README.md:79 | F | `bun run <file>` runs a file, not a script (parser bug) |
| cline_cline | Documented command has no script: bun run my-plugin.ts | .agents/skills/cline-sdk/references/plugins/REFERENCE.md:561 | F | `bun run <file>` runs a file, not a script (parser bug) |
| cline_cline | Documented command has no script: bun run src | .agents/skills/opentui/references/core/REFERENCE.md:38 | F | `bun run <file>` runs a file, not a script (parser bug) |
| cline_cline | Documented command has no script: bun run test:native | .agents/skills/opentui/references/core/gotchas.md:346 | F | Third-party skill docs describing another project |
| cline_cline | Documented command has no script: npm run eval:e2e | evals/README.md:81 | T | Really missing or wrongly pathed |
| cline_cline | Documented command runs a missing file: analyze-issue.sh | docs/cli/samples/github-issue-rca.mdx:112 | F | Tutorial tells the reader to save this file first |
| cline_cline | Broken link: ../../ARCHITECTURE.md | apps/examples/README.md:66 | T | Really missing or wrongly pathed |
| cline_cline | Broken link: ./ARCHITECTURE.md | sdk/packages/README.md:6 | T | Really missing or wrongly pathed |
| cline_cline | Broken link: ./ARCHITECTURE.md | sdk/packages/shared/README.md:6 | T | Really missing or wrongly pathed |
| cline_cline | Broken link: ./DOC.md | apps/cli/DEVELOPMENT.md:5 | T | Really missing or wrongly pathed |
| cline_cline | Broken link: evals/cline-bench/README.md | evals/README.md:143 | T | Really missing or wrongly pathed |
| cline_cline | Broken link: evals/smoke-tests/README.md | evals/README.md:144 | T | Really missing or wrongly pathed |

## Copied code (random 50 of 378, seed 20261108)

| Repo | Finding | Where | Verdict | Reason |
|---|---|---|---|---|
| mem0ai_mem0 | Same code copied 4 times: generate_response | mem0/llms/deepseek.py:73 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: _tool_result_preview | integrations/agent-plugin-core/python/memory_core.py:1297 | F | Generated plugin bundles built from one shared source |
| zylon-ai_private-gpt | Same code copied 2 times: get_task_packages | private_gpt/arq/task_registry.py:12 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 2 times: _stdin_is_piped | cli/python/src/mem0_cli/app.py:194 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: _install_salt | integrations/agent-plugin-core/python/telemetry.py:134 | F | Generated plugin bundles built from one shared source |
| mem0ai_mem0 | Same code copied 7 times: redact | integrations/agent-plugin-core/python/memory_core.py:168 | F | Generated plugin bundles built from one shared source |
| Pythagora-io_gpt-pilot | Same code copied 2 times: send_front_logs_headers | core/ui/console.py:259 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: record_subagent_stop | integrations/agent-plugin-core/python/memory_core.py:1421 | F | Generated plugin bundles built from one shared source |
| cline_cline | Same code copied 2 times: requiredString | sdk/packages/core/src/cron/service/schedule-tool.ts:146 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: _resolve_email | integrations/agent-plugin-core/python/telemetry.py:774 | F | Generated plugin bundles built from one shared source |
| OpenBMB_ChatDev | Same code copied 2 times: openFileDropdown | frontend/src/pages/BatchRunView.vue:622 | T | Same function copied in hand-written code |
| cline_cline | Same code copied 2 times: unmountRoot | apps/cli/src/commands/auth.ts:352 | T | Same function copied in hand-written code |
| run-llama_create-llama | Same code copied 2 times: get_query_engine_tool | packages/create-llama/templates/components/use-cases/python/agentic_rag/query.py:24 | F | Template copies meant to stand alone |
| langchain-ai_open-canvas | Same code copied 2 times: handleClickOutside | apps/web/src/components/artifacts/actions_toolbar/code/index.tsx:64 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: _doctor_mem0_authentication | integrations/agent-plugin-core/python/memory_core.py:2605 | F | Generated plugin bundles built from one shared source |
| cline_cline | Same code copied 2 times: readIdentifier | apps/cli/src/connectors/adapters/discord.ts:177 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: _post | integrations/agent-plugin-core/python/telemetry.py:789 | F | Generated plugin bundles built from one shared source |
| mem0ai_mem0 | Same code copied 7 times: build_episode | integrations/agent-plugin-core/python/memory_core.py:1493 | F | Generated plugin bundles built from one shared source |
| OpenBMB_ChatDev | Same code copied 2 times: openImageModal | frontend/src/pages/BatchRunView.vue:889 | T | Same function copied in hand-written code |
| TransformerOptimus_SuperAGI | Same code copied 2 times: handleClickOutside | gui/pages/Content/Knowledge/KnowledgeForm.js:46 | T | Same function copied in hand-written code |
| cline_cline | Same code copied 2 times: submitLimitIncreaseRequestRPC | apps/vscode/src/sdk/account-service.ts:261 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: select_checkpoint_events | integrations/agent-plugin-core/python/memory_core.py:537 | F | Generated plugin bundles built from one shared source |
| mem0ai_mem0 | Same code copied 6 times: _launch_handoff | integrations/agent-plugin-core/python/hook_runner.py:97 | F | Generated plugin bundles built from one shared source |
| zylon-ai_private-gpt | Same code copied 2 times: _is_valid_base64 | private_gpt/components/ingest/transformations/describe_image_transform.py:237 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: _rotate_anonymous_id | integrations/agent-plugin-core/python/telemetry.py:305 | F | Generated plugin bundles built from one shared source |
| mem0ai_mem0 | Same code copied 7 times: _legacy_project_map | integrations/agent-plugin-core/python/memory_core.py:278 | F | Generated plugin bundles built from one shared source |
| mem0ai_mem0 | Same code copied 7 times: count | integrations/agent-plugin-core/python/memory_core.py:1227 | F | Generated plugin bundles built from one shared source |
| cline_cline | Same code copied 2 times: isAbortReason | sdk/packages/core/src/hooks/hook-file-hooks.ts:225 | T | Same function copied in hand-written code |
| zylon-ai_private-gpt | Same code copied 2 times: _build_container | private_gpt/components/engines/chat/async_chat_engine.py:1985 | T | Same function copied in hand-written code |
| Pythagora-io_gpt-pilot | Same code copied 2 times: send_epics_and_tasks | core/ui/console.py:137 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: _ordered_unique | integrations/agent-plugin-core/python/memory_core.py:1457 | F | Generated plugin bundles built from one shared source |
| cline_cline | Same code copied 2 times: sourceRank | apps/cli/src/tui/views/config-view-helpers.ts:76 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: extraction_message_batches | integrations/agent-plugin-core/python/memory_core.py:1729 | F | Generated plugin bundles built from one shared source |
| cline_cline | Same code copied 2 times: sortBySourceThenName | apps/cli/src/tui/views/config-view-helpers.ts:91 | T | Same function copied in hand-written code |
| run-llama_create-llama | Same code copied 2 times: collect_answers | packages/create-llama/templates/components/agents/python/deep_research/app/workflows/deep_research.py:287 | F | Template copies meant to stand alone |
| mem0ai_mem0 | Same code copied 7 times: _workspace_cwd | integrations/agent-plugin-core/python/mcp_server.py:132 | F | Generated plugin bundles built from one shared source |
| cline_cline | Same code copied 3 times: mergeExtras, mergeGcp, mergeAws | apps/vscode/src/sdk/model-catalog/effective-config.ts:341 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: error_kind | integrations/agent-plugin-core/python/telemetry.py:536 | F | Generated plugin bundles built from one shared source |
| OpenBMB_ChatDev | Same code copied 2 times: loadWorkflows | frontend/src/pages/BatchRunView.vue:609 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: resolve_search_scope | integrations/agent-plugin-core/python/memory_core.py:271 | F | Generated plugin bundles built from one shared source |
| cline_cline | Same code copied 2 times: getInstance | apps/vscode/src/sdk/account-service.ts:47 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: harness_config | integrations/agent-plugin-core/python/memory_core.py:53 | F | Generated plugin bundles built from one shared source |
| OpenBMB_ChatDev | Same code copied 2 times: removeAttachment | frontend/src/pages/BatchRunView.vue:710 | T | Same function copied in hand-written code |
| OpenBMB_ChatDev | Same code copied 2 times: stopRecording | frontend/src/pages/BatchRunView.vue:843 | T | Same function copied in hand-written code |
| cline_cline | Same code copied 2 times: finiteEpochMs, positiveFiniteNumber | sdk/packages/core/src/hub/discovery/index.ts:192 | T | Same function copied in hand-written code |
| cline_cline | Same code copied 2 times: formatHubUptimeFromStartedAt | apps/cli/src/commands/doctor.ts:400 | T | Same function copied in hand-written code |
| mem0ai_mem0 | Same code copied 7 times: _bool_option | integrations/agent-plugin-core/python/memory_core.py:493 | F | Generated plugin bundles built from one shared source |
| OpenBMB_ChatDev | Same code copied 2 times: _resolve_yaml_path | server/services/batch_run_service.py:251 | T | Same function copied in hand-written code |
| TransformerOptimus_SuperAGI | Same code copied 4 times: summarise_result | superagi/tools/duck_duck_go/duck_duck_go_search.py:156 | T | Same function copied in hand-written code |
| arc53_DocsGPT | Same code copied 2 times: clearPoll | frontend/src/settings/ConvertToWikiModal.tsx:45 | T | Same function copied in hand-written code |

## Needs-review leads (random 30 of 2,543, seed 20261109)

| Repo | Finding | Where | Verdict | Reason |
|---|---|---|---|---|
| arc53_DocsGPT | Possibly unused dependency: pathable | docsgpt/requirements-milvus.txt:564 | F | Used indirectly, optional, or a pinned transitive dependency |
| OpenBMB_ChatDev | Possibly unused dependency: mem0ai | pyproject.toml:43 | F | Used indirectly, optional, or a pinned transitive dependency |
| mem0ai_mem0 | Possibly the same job done 2 times: get_profile_job | mem0/client/main.py:979 | F | Look alike on purpose (sync/async pair, adapter per backend, per-route) or different jobs |
| arc53_DocsGPT | Possibly the same job done 2 times: embed_query | docsgpt/vectorstore/embeddings_delegated.py:230 | F | Look alike on purpose (sync/async pair, adapter per backend, per-route) or different jobs |
| cline_cline | Possibly unused dev dependency: postcss | apps/examples/vscode/src/webview/package.json:59 | F | Used indirectly, optional, or a pinned transitive dependency |
| arc53_DocsGPT | Possibly unused dependency: importlib-resources | docsgpt/requirements.txt:318 | F | Used indirectly, optional, or a pinned transitive dependency |
| Nutlope_aicommits | Possibly the same job done 2 times: getLatestVersion, fetchLatestVersion | src/commands/update.ts:85 | T | Real duplicated job |
| zylon-ai_private-gpt | Possibly the same job done 2 times: _astream_chat, astream_chat | private_gpt/components/llm/custom/openai.py:256 | F | Look alike on purpose (sync/async pair, adapter per backend, per-route) or different jobs |
| arc53_DocsGPT | Possibly unused dependency: opentelemetry-util-http | docsgpt/requirements-docling.txt:621 | F | Used indirectly, optional, or a pinned transitive dependency |
| langchain-ai_open-canvas | Possibly unused dev dependency: tsx | packages/evals/package.json:34 | U | Can't tell without running the build |
| TransformerOptimus_SuperAGI | Possibly the same job done 4 times: get_agent_execution, get_agent_execution_feed, update_agent_execution_feed, get_agent_execution_permission | superagi/controllers/agent_execution.py:297 | F | Look alike on purpose (sync/async pair, adapter per backend, per-route) or different jobs |
| arc53_DocsGPT | Possibly unused dependency: asgiref | docsgpt/requirements-decisions-torch.txt:62 | F | Used indirectly, optional, or a pinned transitive dependency |
| arc53_DocsGPT | Possibly unused dependency: rpds-py | docsgpt/requirements-decisions-torch.txt:772 | F | Used indirectly, optional, or a pinned transitive dependency |
| mem0ai_mem0 | Possibly the same job done 2 times: list | mem0/vector_stores/elasticsearch.py:281 | F | Look alike on purpose (sync/async pair, adapter per backend, per-route) or different jobs |
| arc53_DocsGPT | Possibly unused dependency: tree-sitter-javascript | docsgpt/requirements-docling.txt:1008 | F | Used indirectly, optional, or a pinned transitive dependency |
| zylon-ai_private-gpt | Possibly the same job done 2 times: _chat, _achat | private_gpt/components/llm/custom/openairesponses.py:544 | F | Look alike on purpose (sync/async pair, adapter per backend, per-route) or different jobs |
| arc53_DocsGPT | Possibly unused dependency: opentelemetry-distro | docsgpt/requirements.txt:446 | F | Used indirectly, optional, or a pinned transitive dependency |
| arc53_DocsGPT | Possibly the same job done 4 times: _build_extraction_llm, factory, _build_llm | docsgpt/graphrag/extraction.py:118 | F | Look alike on purpose (sync/async pair, adapter per backend, per-route) or different jobs |
| arc53_DocsGPT | Possibly the same job done 2 times: menu, actions | frontend/src/agents/components/InsightsSettingsBody.tsx:238 | F | Look alike on purpose (sync/async pair, adapter per backend, per-route) or different jobs |
| arc53_DocsGPT | Possibly unused dependency: opentelemetry-exporter-http-transport | docsgpt/requirements-decisions-torch.txt:463 | F | Used indirectly, optional, or a pinned transitive dependency |
| zylon-ai_private-gpt | Possibly unused optional dependency: openinference-semantic-conventions | pyproject.toml:292 | F | Used indirectly, optional, or a pinned transitive dependency |
| Nutlope_aicommits | Possibly the same job done 2 times: attemptGeneration | src/commands/aicommits.ts:135 | T | Real duplicated job |
| zylon-ai_private-gpt | Possibly the same job done 3 times: _astructured_chat | private_gpt/components/multimodality/audio_handler.py:470 | T | Real duplicated job |
| arc53_DocsGPT | Possibly unused dependency: typing-inspection | docsgpt/requirements-docling.txt:1076 | F | Used indirectly, optional, or a pinned transitive dependency |
| cline_cline | Possibly the same job done 2 times: setWelcomeViewCompleted, markWelcomeViewCompleted | apps/vscode/src/core/controller/state/setWelcomeViewCompleted.ts:12 | T | Real duplicated job |
| cline_cline | Possibly unused dev dependency: @types/jest | apps/vscode/webview-ui/package.json:79 | U | Can't tell without running the build |
| stanford-oval_storm | Possibly the same job done 3 times: log_usage | knowledge_storm/lm.py:210 | F | Look alike on purpose (sync/async pair, adapter per backend, per-route) or different jobs |
| run-llama_create-llama | Possibly unused dev dependency: ruff | python/llama-index-server/pyproject.toml:53 | F | Used indirectly, optional, or a pinned transitive dependency |
| arc53_DocsGPT | Possibly unused dependency: prawcore | docsgpt/requirements-decisions-torch.txt:588 | F | Used indirectly, optional, or a pinned transitive dependency |
| arc53_DocsGPT | Possibly unused dependency: pendulum | docsgpt/requirements-milvus.txt:568 | F | Used indirectly, optional, or a pinned transitive dependency |

## Repos and commits

| Repo | Commit |
|---|---|
| https://github.com/langchain-ai/open-canvas | `0310cecd51f31b3d37a52d6d1060137da3405280` |
| https://github.com/langchain-ai/local-deep-researcher | `96555649619b9c482442f2cba043312bd7692507` |
| https://github.com/langchain-ai/chat-langchain | `1a5fbe967b47bdc5d5f142c515fd8bc0b185bd81` |
| https://github.com/Pythagora-io/gpt-pilot | `9b763fdaf0020c7d8abacc7b58b2b09e57494623` |
| https://github.com/smol-ai/developer | `a6747d1a6ccd983c54a483c4a4fb1fa4bf740e82` |
| https://github.com/yoheinakajima/babyagi | `fa8930ebe72a82e5ad57b356e7cbec96290e5bb2` |
| https://github.com/nextjs/saas-starter | `6e33e58b1e553a41fe22e6b941a7229a002de361` |
| https://github.com/steven-tey/novel | `fa95098e66476c466faebb8211baa5869c101a9c` |
| https://github.com/qodo-ai/pr-agent | `10a107c2203d1c956bddeb4a9a2c2461969f9c1e` |
| https://github.com/OpenBMB/ChatDev | `464a681ba7bda869db97a50c5fa707c9a0fb57a0` |
| https://github.com/run-llama/create-llama | `97a7d9bc2560943ca6bca1ac41d2f84c1b7e6224` |
| https://github.com/arc53/DocsGPT | `5fad192aa3703ab5b16c0833a0e03a3fc0658767` |
| https://github.com/QuivrHQ/quivr | `bb1b19ecf4094dbd1e309e521ce8b5fed4fb539f` |
| https://github.com/zylon-ai/private-gpt | `42ce052278eff7af28232396ebdcbfcf35297f0d` |
| https://github.com/stanford-oval/storm | `fb951af7744dab086e34962e9bc6fe878e145f83` |
| https://github.com/Nutlope/llamacoder | `5d57b706208d1de6718853e3b18c66e97cda420c` |
| https://github.com/Nutlope/aicommits | `98e9c4e4ea025053d4ede2ed4ee08aed9b7e0419` |
| https://github.com/Nutlope/turboseek | `6e00016fb91747cb1a4461aeed29a3737ba7e873` |
| https://github.com/TransformerOptimus/SuperAGI | `c3c1982e7bd6a11cfed53c5a193ea502f924b1b6` |
| https://github.com/mem0ai/mem0 | `f1791daa86d469249c9b002f62644d58a1eb96a0` |
| https://github.com/Aider-AI/aider | `5dc9490bb35f9729ef2c95d00a19ccd30c26339c` |
| https://github.com/cline/cline | `94eecbbe1a2f899c66b118d13656651b0a884e1f` |
