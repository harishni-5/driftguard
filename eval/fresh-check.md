# Re-measurement after the fixes: 17 fresh repos

Date: 2026-11-08. Same method as [final-check.md](final-check.md), on repos never used before, with the fixed build
(templates and synced copies skipped, freeze files skipped, more tools known, docs-site links, build outputs, `bun run <file>`,
"save this as" files). No rules were changed after running on these repos. **T** = real, **F** = false alarm, **U** = unsure.

| Category | Checked | Real | False | Unsure | Precision |
|---|---:|---:|---:|---:|---:|
| Competing libraries (all) | 24 | 15 | 9 | 0 | **62%** |
| Docs vs code (all) | 64 | 17 | 45 | 2 | **27%** |
| Unused dependencies (random 60 of 852, seed 20261110) | 60 | 48 | 12 | 0 | **80%** |
| Copied code (random 50 of 1,031, seed 20261111) | 50 | 37 | 13 | 0 | **74%** |
| Needs-review leads (random 30 of 4,273, seed 20261112) | 30 | 4 | 25 | 1 | **14%** |

## Competing libraries (all)

| Repo | Finding | Where | Verdict | Reason |
|---|---|---|---|---|
| pydantic_pydantic-ai | 2 HTTP clients in pydantic_ai_slim: httpx, urllib | pydantic_ai_slim/pydantic_ai/models/bedrock.py:35 | T | Same job done with different libraries in product code |
| openai_openai-agents-python | 3 HTTP clients in this project: aiohttp, httpx, urllib | src/agents/sandbox/session/sinks.py:11 | T | Same job done with different libraries in product code |
| Skyvern-AI_skyvern | 4 HTTP clients in this project: httpx, aiohttp, requests, urllib | skyvern/utils/pinned_transport.py:4 | T | Same job done with different libraries in product code |
| Skyvern-AI_skyvern | 2 HTTP clients in skyvern-frontend: axios, fetch | skyvern-frontend/src/api/AxiosClient.ts:188 | T | Same job done with different libraries in product code |
| Skyvern-AI_skyvern | 2 validation libraries in this project: pydantic, jsonschema | skyvern/cli/mcp_tools/output_tools.py:8 | F | jsonschema/ajv validate dynamic schemas on purpose |
| firecrawl_open-lovable | 2 icon libraries in this project: lucide, react-icons | lib/icons.ts:4 | T | Same job done with different libraries in product code |
| stackblitz-labs_bolt.diy | 2 HTTP clients in this project: fetch, node-fetch | app/lib/fetch.ts:5 | T | Same job done with different libraries in product code |
| stackblitz-labs_bolt.diy | 2 icon libraries in this project: lucide, react-icons | app/components/@settings/tabs/providers/cloud/CloudProvidersTab.tsx:3 | T | Same job done with different libraries in product code |
| browserbase_stagehand | 2 HTTP clients in packages/sdk-python: httpx, urllib | packages/sdk-python/src/stagehand/browser.py:13 | T | Same job done with different libraries in product code |
| coleam00_Archon | 2 validation libraries in packages/workflows: zod, ajv | packages/workflows/src/dry-run/stubs.ts:2 | F | jsonschema/ajv validate dynamic schemas on purpose |
| microsoft_markitdown | 2 HTTP clients in packages/markitdown: requests, urllib | packages/markitdown/src/markitdown/_uri_utils.py:5 | T | Same job done with different libraries in product code |
| topoteretes_cognee | 4 HTTP clients in this project: aiohttp, httpx, requests, urllib | catalog/inventory_sync.py:27 | T | Same job done with different libraries in product code |
| topoteretes_cognee | 2 HTTP clients in cognee-connectors/connectors/linear: aiohttp, requests | cognee-connectors/connectors/linear/cognee_connector_linear/source.py:226 | T | Same job done with different libraries in product code |
| topoteretes_cognee | 2 HTTP clients in cognee-connectors/connectors/notion: aiohttp, httpx | cognee-connectors/connectors/notion/cognee_connector_notion/source.py:150 | T | Same job done with different libraries in product code |
| topoteretes_cognee | 2 validation libraries in this project: pydantic, jsonschema | catalog/loader.py:28 | F | jsonschema/ajv validate dynamic schemas on purpose |
| agno-agi_agno | 4 HTTP clients in libs/agno: httpx, requests, urllib, aiohttp | libs/agno/agno/knowledge/embedder/jina.py:21 | T | Same job done with different libraries in product code |
| agno-agi_agno | 2 HTTP clients in cookbook/gemini_3: httpx, requests | cookbook/gemini_3/16_prompt_caching.py:21 | F | Example, cookbook or showcase code |
| agno-agi_agno | 2 HTTP clients in this project: httpx, requests | cookbook/02_agents/12_multimodal/audio_input_output.py:8 | F | Example, cookbook or showcase code |
| agno-agi_agno | 2 validation libraries in libs/agno: pydantic, jsonschema | libs/agno/agno/os/mcp.py:539 | F | jsonschema/ajv validate dynamic schemas on purpose |
| langchain-ai_open_deep_research | 3 HTTP clients in this project: aiohttp, httpx, requests | src/legacy/utils.py:5 | T | Same job done with different libraries in product code |
| CopilotKit_CopilotKit | 2 HTTP clients in showcase/integrations/google-adk: aiohttp, httpx | showcase/integrations/google-adk/src/agents/_header_forwarding.py:190 | F | Example, cookbook or showcase code |
| CopilotKit_CopilotKit | 2 HTTP clients in showcase/integrations/google-antigravity: httpx, aiohttp | showcase/integrations/google-antigravity/src/agents/_header_forwarding.py:412 | F | Example, cookbook or showcase code |
| CopilotKit_CopilotKit | 2 icon libraries in showcase/shell-docs: lucide, react-icons | showcase/shell-docs/src/components/frontend-logo.tsx:3 | T | Same job done with different libraries in product code |
| CopilotKit_CopilotKit | 2 validation libraries in this project: jsonschema, pydantic | showcase/integrations/_shared/cvdiag_schema.py:15 | F | jsonschema/ajv validate dynamic schemas on purpose |

## Docs vs code (all)

| Repo | Finding | Where | Verdict | Reason |
|---|---|---|---|---|
| pydantic_pydantic-ai | Documented command runs a missing file: submit.py | src/pydantic_ai_harness/pydantic_ai_harness/render/README.md:219 | T | Really missing, misspelled or wrongly pathed |
| pydantic_pydantic-ai | Broken link: ../toolsets.md | .agents/skills/docs-parity-reviewer/SKILL.md:54 | F | Example link syntax inside code formatting |
| pydantic_pydantic-ai | Broken link: shell.md | .agents/skills/docs-parity-reviewer/SKILL.md:53 | F | Example link syntax inside code formatting |
| openai_openai-agents-python | Broken link: ... | .agents/skills/maintainer-review/references/evaluation-framework.md:276 | F | Example link syntax inside code formatting |
| openai_openai-agents-python | Broken link: ... | .agents/skills/pr-draft-summary/SKILL.md:39 | F | Example link syntax inside code formatting |
| openai_openai-agents-python | Broken link: ../assets/images/harness_with_compute.png | docs/ja/sandbox/guide.md:13 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ../assets/images/harness_with_compute.png | docs/ko/sandbox/guide.md:13 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ../assets/images/harness_with_compute.png | docs/zh/sandbox/guide.md:13 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/extensions/experimental/codex/codex_tool.md | docs/ja/tools.md:919 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/extensions/experimental/codex/codex_tool.md | docs/ko/tools.md:919 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/extensions/experimental/codex/codex_tool.md | docs/zh/tools.md:919 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/extensions/experimental/codex/thread_options.md | docs/ja/tools.md:920 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/extensions/experimental/codex/thread_options.md | docs/ko/tools.md:920 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/extensions/experimental/codex/turn_options.md | docs/ja/tools.md:921 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/extensions/experimental/codex/turn_options.md | docs/ko/tools.md:921 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/lifecycle.md | docs/ja/agents.md:303 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/lifecycle.md | docs/ko/agents.md:303 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/lifecycle.md | docs/zh/agents.md:303 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/mcp/manager.md | docs/ja/mcp.md:369 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/mcp/manager.md | docs/ko/mcp.md:371 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/mcp/manager.md | docs/zh/mcp.md:371 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/memory/openai_conversations_session.md | docs/ja/sessions.md:457 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/memory/openai_conversations_session.md | docs/ko/sessions.md:458 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/memory/openai_conversations_session.md | docs/zh/sessions.md:458 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/realtime/testing.md | docs/ja/testing.md:574 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/realtime/testing.md | docs/ko/testing.md:574 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/realtime/testing.md | docs/zh/testing.md:574 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/testing.md | docs/ja/testing.md:573 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/testing.md | docs/ko/testing.md:573 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/testing.md | docs/zh/testing.md:573 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/voice/testing.md | docs/ja/testing.md:575 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/voice/testing.md | docs/ko/testing.md:575 | F | Translated docs page; the docs framework resolves it to the English path |
| openai_openai-agents-python | Broken link: ref/voice/testing.md | docs/zh/testing.md:575 | F | Translated docs page; the docs framework resolves it to the English path |
| huggingface_smolagents | Broken link: meta-llama/Llama-3.3-70B-Instruct | docs/source/hi/examples/rag.md:116 | T | Really missing, misspelled or wrongly pathed |
| huggingface_smolagents | Broken link: meta-llama/Llama-3.3-70B-Instruct | docs/source/zh/examples/rag.md:106 | T | Really missing, misspelled or wrongly pathed |
| Skyvern-AI_skyvern | Documented command runs a missing file: scripts/package-mcpb.sh | skyvern/cli/mcpb/claude_desktop/README.md:9 | T | Really missing, misspelled or wrongly pathed |
| Skyvern-AI_skyvern | Documented command runs a missing file: scripts/validate_skills_package.py | skyvern/cli/skills/README.md:79 | T | Really missing, misspelled or wrongly pathed |
| Skyvern-AI_skyvern | Broken link: webbench.ai | README.md:391 | T | Really missing, misspelled or wrongly pathed |
| assistant-ui_assistant-ui | Docs name the wrong variable: API_URL (code reads NEXT_PUBLIC_API_URL) | apps/docs/content/docs/cloud/(integrations)/ai-sdk.mdx:47 | U | Docs for users' own apps; the repo's example reads the prefixed name |
| assistant-ui_assistant-ui | Docs name the wrong variable: LANGGRAPH_ASSISTANT_ID (code reads NEXT_PUBLIC_LANGGRAPH_ASSISTANT_ID) | apps/docs/content/docs/runtimes/langchain.mdx:220 | U | Docs for users' own apps; the repo's example reads the prefixed name |
| assistant-ui_assistant-ui | Documented command has no script: npm run setup | apps/docs/content/docs/runtimes/claude-managed-agents.mdx:114 | F | Describes another project or a file the reader makes |
| browserbase_stagehand | Documented command runs a missing file: cli.js | packages/docs/v3/integrations/mcp/configuration.mdx:107 | F | Describes another project (separate SDK or tool) |
| browserbase_stagehand | Documented command runs a missing file: examples/full_example.py | packages/docs/v3/sdk/python.mdx:50 | F | Describes another project (separate SDK or tool) |
| browserbase_stagehand | Documented command runs a missing file: examples/local_example.py | packages/docs/v3/sdk/python.mdx:74 | F | Describes another project (separate SDK or tool) |
| browserbase_stagehand | Documented command runs a missing file: examples/logging_example.py | packages/docs/v3/sdk/python.mdx:87 | F | Describes another project (separate SDK or tool) |
| browserbase_stagehand | Documented command runs a missing file: run_some_script_that_imports_stagehand.ts | packages/docs/v3/configuration/logging.mdx:333 | F | Describes another project (separate SDK or tool) |
| coleam00_Archon | Documented command has no script: npm run special-test-command | .claude/skills/playwright-cli/references/playwright-tests.md:10 | F | Describes another project or a file the reader makes |
| coleam00_Archon | Broken link: ../skills/save-task-list/workshop-guide.md | .claude/workshop/part2-guide.md:31 | T | Really missing, misspelled or wrongly pathed |
| coleam00_Archon | Broken link: file.md | .claude/skills/rulecheck/workshop-guide.md:100 | F | Example link syntax inside code formatting |
| getzep_graphiti | Documented command runs a missing file: server.js | mcp_server/docker/README-falkordb-combined.md:165 | F | Process listing inside a container diagram, not a command |
| getzep_graphiti | Broken link: cursor_rules.md | mcp_server/README.md:640 | T | Really missing, misspelled or wrongly pathed |
| topoteretes_cognee | Documented command runs a missing file: cognee/tests/tasks/translation/integration_test.py | cognee/tests/tasks/translation/README.md:51 | T | Really missing, misspelled or wrongly pathed |
| agno-agi_agno | Documented command runs a missing file: gcs_json_storage_for_agent.py | cookbook/06_storage/gcs/README.md:68 | T | Really missing, misspelled or wrongly pathed |
| langchain-ai_open_deep_research | Broken link: src/legacy/graph.ipynb | src/legacy/legacy.md:114 | T | Really missing, misspelled or wrongly pathed |
| langchain-ai_open_deep_research | Broken link: src/legacy/multi_agent.ipynb | src/legacy/legacy.md:114 | T | Really missing, misspelled or wrongly pathed |
| CopilotKit_CopilotKit | Documented command has no script: npm run openbox:admin:setup | showcase/shell-docs/src/content/docs/cookbook/openbox-governed-copilotkit.mdx:98 | F | Describes another project or a file the reader makes |
| CopilotKit_CopilotKit | Documented command has no script: npm run openbox:verify | showcase/shell-docs/src/content/docs/cookbook/openbox-governed-copilotkit.mdx:99 | F | Describes another project or a file the reader makes |
| CopilotKit_CopilotKit | Documented command runs a missing file: scripts/local-demo.sh | showcase/shell-docs/src/content/snippets/shared/intelligence/self-hosting.mdx:55 | F | Describes another project or a file the reader makes |
| CopilotKit_CopilotKit | Documented command runs a missing file: showcase/scripts/run-e2e-with-aimock.sh | showcase/aimock/README.md:68 | T | Really missing, misspelled or wrongly pathed |
| CopilotKit_CopilotKit | Documented command runs a missing file: weather.py | showcase/integrations/ag2/docs/setup/channels-agent-setup.mdx:5 | F | Describes another project or a file the reader makes |
| CopilotKit_CopilotKit | Broken link: ./PLAYGROUND.md | examples/showcases/chatkit-studio/apps/playground/README.md:133 | T | Really missing, misspelled or wrongly pathed |
| CopilotKit_CopilotKit | Broken link: agent/src/main.py | examples/showcases/pydantic-ai-todos/README.md:147 | T | Really missing, misspelled or wrongly pathed |
| CopilotKit_CopilotKit | Broken link: docs/HANDOFF.md | examples/showcases/open-mcp-client/README.md:54 | T | Really missing, misspelled or wrongly pathed |
| CopilotKit_CopilotKit | Broken link: image.png | community/demos_2024/news-aggregator.md:29 | T | Really missing, misspelled or wrongly pathed |

## Unused dependencies (random 60 of 852, seed 20261110)

| Repo | Finding | Where | Verdict | Reason |
|---|---|---|---|---|
| browserbase_stagehand | Possibly unused dependency: zod | packages/examples/cookbooks/persisted-login/typescript/package.json:13 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: lucide-react | examples/integrations/pydantic-ai/package.json:26 | T | Never referenced in its own project (example app) |
| assistant-ui_assistant-ui | Possibly unused dependency: fumadocs-ui | apps/docs/package.json:82 | T | Never referenced in its own project |
| CopilotKit_CopilotKit | Possibly unused dependency: chalk | showcase/scripts/package.json:50 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: recharts | examples/canvas/llamaindex/package.json:70 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: rxjs | examples/v2/angular/storybook/package.json:22 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: @mastra/client-js | examples/canvas/mastra/package.json:21 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: shadcn | examples/showcases/deep-agents-finance-erp/package.json:32 | F | CLI tool configured in components.json |
| CopilotKit_CopilotKit | Possibly unused dependency: partial-json | examples/v2/angular/demo/package.json:48 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: @ai-sdk/openai | examples/showcases/adk-dashboard/package.json:18 | T | Never referenced in its own project (example app) |
| getzep_graphiti | Possibly unused optional dependency: google-genai | mcp_server/pyproject.toml:21 | T | Never referenced in its own project |
| assistant-ui_assistant-ui | Possibly unused dependency: lucide-react | examples/with-a2a/package.json:18 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: ag-ui-protocol | examples/integrations/adk-angular/agent/pyproject.toml:14 | F | Imported as ag_ui (import name not known) |
| topoteretes_cognee | Possibly unused dependency: mantine-form-valibot-resolver | cognee-frontend/package.json:31 | T | Never referenced in its own project |
| assistant-ui_assistant-ui | Possibly unused dev dependency: @assistant-ui/react-ink | packages/react-ink-markdown/package.json:48 | T | Never referenced in its own project |
| CopilotKit_CopilotKit | Possibly unused dev dependency: croner | showcase/shell-dashboard/package.json:34 | F | Used in a test file |
| CopilotKit_CopilotKit | Possibly unused dependency: @radix-ui/react-slider | examples/canvas/llamaindex-composio/package.json:42 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: @radix-ui/react-toggle-group | examples/canvas/llamaindex/package.json:47 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: @copilotkit/core | packages/channels-core/package.json:66 | T | Never referenced in its own project |
| CopilotKit_CopilotKit | Possibly unused dependency: @radix-ui/react-slot | examples/showcases/a2a-travel/package.json:26 | T | Never referenced in its own project (example app) |
| assistant-ui_assistant-ui | Possibly unused dependency: cn | examples/with-chain-of-thought/package.json:21 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: @ag-ui/core | examples/integrations/omnara/package.json:23 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: react-rnd | examples/integrations/claude-sdk-python/package.json:35 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: fast-json-patch | examples/v2/angular/demo/package.json:41 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: @graphql-yoga/plugin-defer-stream | packages/runtime/package.json:101 | T | Never referenced in its own project |
| CopilotKit_CopilotKit | Possibly unused dependency: shiki | examples/canvas/mastra-pm/package.json:32 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: @copilotkit/core | examples/v2/react/demo/package.json:18 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dev dependency: shadcn | examples/integrations/agentcore/frontend/package.json:58 | F | CLI tool configured in components.json |
| CopilotKit_CopilotKit | Possibly unused dependency: shiki | examples/canvas/llamaindex/package.json:71 | F | Loaded by react-shiki (peer) |
| assistant-ui_assistant-ui | Possibly unused dependency: zod | examples/with-opencode/package.json:23 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: ag-ui-protocol | examples/showcases/reskinnable-demo/agent-myelin/pyproject.toml:14 | F | Imported as ag_ui (import name not known) |
| CopilotKit_CopilotKit | Possibly unused dependency: react-is | examples/integrations/agentcore/frontend/package.json:34 | F | Peer of another package |
| CopilotKit_CopilotKit | Possibly unused dependency: @radix-ui/react-context-menu | examples/canvas/llamaindex-composio/package.json:29 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: @radix-ui/react-toast | examples/canvas/llamaindex/package.json:45 | T | Never referenced in its own project (example app) |
| assistant-ui_assistant-ui | Possibly unused dependency: react-dom | examples/bundle-blog-assistant/package.json:17 | F | Peer requirement of the React setup |
| assistant-ui_assistant-ui | Possibly unused dependency: cn | examples/with-langgraph/package.json:21 | T | Never referenced in its own project (example app) |
| assistant-ui_assistant-ui | Possibly unused dependency: cn | examples/with-tanstack/package.json:19 | T | Never referenced in its own project (example app) |
| assistant-ui_assistant-ui | Possibly unused dependency: react-dom | examples/bundle-data-explorer/package.json:19 | F | Peer requirement of the React setup |
| assistant-ui_assistant-ui | Possibly unused peer dependency: react-dom | packages/remote-components-react/package.json:42 | F | Peer requirement of the React setup |
| CopilotKit_CopilotKit | Possibly unused dependency: sse-starlette | examples/integrations/antigravity/agent/pyproject.toml:13 | T | Never referenced in its own project (example app) |
| assistant-ui_assistant-ui | Possibly unused dependency: @assistant-ui/ui | examples/with-nuxt/package.json:19 | T | Never referenced in its own project (example app) |
| assistant-ui_assistant-ui | Possibly unused dependency: @langchain/react | examples/with-langgraph/package.json:19 | T | Never referenced in its own project (example app) |
| coleam00_Archon | Possibly unused dependency: escape-string-regexp | packages/web/package.json:19 | T | Never referenced in its own project |
| CopilotKit_CopilotKit | Possibly unused dependency: @opentelemetry/api | showcase/integrations/strands-typescript/src/agent/package.json:18 | F | Peer of other OpenTelemetry packages |
| assistant-ui_assistant-ui | Possibly unused dependency: @assistant-ui/react-markdown | examples/with-tanstack/package.json:14 | T | Never referenced in its own project (example app) |
| assistant-ui_assistant-ui | Possibly unused dependency: @opentelemetry/exporter-trace-otlp-http | api-surface/package.json:23 | T | Never referenced in its own project |
| assistant-ui_assistant-ui | Possibly unused dependency: lucide-react | examples/with-custom-thread-list/package.json:21 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: react18-json-view | examples/integrations/strands-python/package.json:36 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: uuid | examples/v2/angular/demo/package.json:54 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: @angular/forms | examples/integrations/adk-angular/package.json:24 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: next-themes | examples/canvas/llamaindex-composio/package.json:62 | T | Never referenced in its own project (example app) |
| assistant-ui_assistant-ui | Possibly unused dev dependency: react-dom | packages/eve/package.json:63 | F | Peer requirement of the React setup |
| CopilotKit_CopilotKit | Possibly unused dependency: @modelcontextprotocol/sdk | examples/integrations/strands-typescript/agent/package.json:19 | T | Never referenced in its own project (example app) |
| assistant-ui_assistant-ui | Possibly unused dependency: lucide-react | examples/with-ai-sdk-v7/package.json:20 | T | Never referenced in its own project (example app) |
| assistant-ui_assistant-ui | Possibly unused dependency: @standard-schema/spec | packages/react-pi/package.json:61 | T | Never referenced in its own project |
| assistant-ui_assistant-ui | Possibly unused dependency: remark-frontmatter | apps/docs/package.json:114 | T | Never referenced in its own project |
| CopilotKit_CopilotKit | Possibly unused dependency: @radix-ui/react-tooltip | examples/canvas/llamaindex-composio/package.json:49 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: @libsql/client | showcase/integrations/mastra/package.json:28 | T | Never referenced in its own project (example app) |
| CopilotKit_CopilotKit | Possibly unused dependency: @copilotkit/shared | examples/v1/node-http/package.json:11 | T | Never referenced in its own project (example app) |
| assistant-ui_assistant-ui | Possibly unused dependency: @opentelemetry/otlp-exporter-base | api-surface/package.json:24 | T | Never referenced in its own project |

## Copied code (random 50 of 1,031, seed 20261111)

| Repo | Finding | Where | Verdict | Reason |
|---|---|---|---|---|
| agno-agi_agno | Same code copied 3 times: stop_timer | libs/agno/agno/metrics.py:263 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 2 times: _track_references | libs/agno/agno/agent/_default_tools.py:110 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 2 times: get_run | libs/agno/agno/db/gcs_json/gcs_json_db.py:314 | T | Same function copied in hand-written code |
| browserbase_stagehand | Same code copied 2 times: wait | packages/evals/core/tools/stagehand_code.ts:181 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 8 times: set_log_level | libs/agno/agno/learn/machine.py:1027 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 2 times: delete_user_memory | libs/agno/agno/db/redis/redis.py:1044 | T | Same function copied in hand-written code |
| assistant-ui_assistant-ui | Same code copied 2 times: SidebarMenuItem | packages/ui/src/components/react/ui/base/sidebar.tsx:457 | F | Parallel UI-kit registries (base vs radix, react vs react-native) |
| getzep_graphiti | Same code copied 2 times: save, delete | graphiti_core/driver/operations/has_episode_edge_ops.py:25 | T | Same function copied in hand-written code |
| Skyvern-AI_skyvern | Same code copied 3 times: block_callback, credential_callback, workflow_callback | skyvern/cli/block.py:33 | T | Same function copied in hand-written code |
| openai_openai-agents-python | Same code copied 6 times: build_docker_volume_driver_config | src/agents/extensions/sandbox/blaxel/mounts.py:146 | T | Same function copied in hand-written code |
| CopilotKit_CopilotKit | Same code copied 2 times: commErrorFromStatusSignal | showcase/harness/src/fleet/contracts.ts:449 | T | Same function copied in hand-written code |
| CopilotKit_CopilotKit | Same code copied 2 times: get_stock_price | showcase/integrations/agno/src/agents/main.py:158 | F | Example or demo code, standalone by design |
| agno-agi_agno | Same code copied 2 times: _format_with_line_numbers | libs/agno/agno/fs/toolkit.py:81 | T | Same function copied in hand-written code |
| stackblitz-labs_bolt.diy | Same code copied 2 times: GitLabIcon, GitLabLogo | app/components/@settings/tabs/connectors/ConnectorsTab.tsx:37 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 2 times: count_authz_audit_events | libs/agno/agno/db/postgres/async_postgres.py:6360 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 2 times: get_supported_chunking_strategies | libs/agno/agno/knowledge/reader/firecrawl_reader.py:52 | T | Same function copied in hand-written code |
| assistant-ui_assistant-ui | Same code copied 2 times: clampArray | packages/generative-ui/src/slack/fromSlackBlocks.ts:53 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 2 times: aget_deepseek_reasoning, aget_moonshot_reasoning | libs/agno/agno/reasoning/deepseek.py:111 | T | Same function copied in hand-written code |
| getzep_graphiti | Same code copied 4 times: delete | graphiti_core/driver/falkordb/operations/has_episode_edge_ops.py:75 | T | Same function copied in hand-written code |
| Skyvern-AI_skyvern | Same code copied 3 times: build_log_uri | skyvern/forge/sdk/artifact/storage/azure.py:110 | T | Same function copied in hand-written code |
| getzep_graphiti | Same code copied 4 times: delete_by_uuids | graphiti_core/driver/operations/community_node_ops.py:59 | T | Same function copied in hand-written code |
| Skyvern-AI_skyvern | Same code copied 4 times: _reject_start_fresh_with_address | skyvern/forge/sdk/workflow/models/workflow.py:155 | T | Same function copied in hand-written code |
| Skyvern-AI_skyvern | Same code copied 2 times: _serialized_size, _serialized_chars | skyvern/cli/mcp_tools/response_browser.py:15 | T | Same function copied in hand-written code |
| CopilotKit_CopilotKit | Same code copied 2 times: buildGitHubUrl | showcase/shell-docs/src/app/reference/[...slug]/page.tsx:101 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 2 times: dynamic_selector | cookbook/04_workflows/05_conditional_branching/selector_types.py:92 | F | Example or demo code, standalone by design |
| assistant-ui_assistant-ui | Same code copied 2 times: ComboboxEmpty | packages/ui/src/components/react/ui/base/combobox.tsx:190 | F | Parallel UI-kit registries (base vs radix, react vs react-native) |
| assistant-ui_assistant-ui | Same code copied 2 times: SidebarInput | packages/ui/src/components/react/ui/base/sidebar.tsx:309 | F | Parallel UI-kit registries (base vs radix, react vs react-native) |
| agno-agi_agno | Same code copied 2 times: upsert_run | libs/agno/agno/db/base.py:748 | F | Abstract stub or no-op hook, the same on purpose |
| agno-agi_agno | Same code copied 13 times: _empty_metric_record | libs/agno/agno/db/dynamo/utils.py:368 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 2 times: delete_eval_runs | libs/agno/agno/db/singlestore/singlestore.py:2528 | T | Same function copied in hand-written code |
| CopilotKit_CopilotKit | Same code copied 2 times: ngOnInit, ngOnChanges | packages/angular/src/lib/components/chat/copilot-chat-view.ts:422 | T | Same function copied in hand-written code |
| CopilotKit_CopilotKit | Same code copied 2 times: _tool_result_ids | showcase/integrations/ms-agent-python/src/agents/hitl_in_app_agent.py:77 | F | Example or demo code, standalone by design |
| CopilotKit_CopilotKit | Same code copied 2 times: _inject_headers_async, _inject_headers | showcase/integrations/ms-agent-python/src/agents/_header_forwarding.py:309 | T | Same function copied in hand-written code |
| assistant-ui_assistant-ui | Same code copied 2 times: useVoiceTranscript | packages/ui/src/components/react-native/assistant-ui/elements/voice-conversation.aui.tsx:35 | F | Parallel UI-kit registries (base vs radix, react vs react-native) |
| getzep_graphiti | Same code copied 4 times: save | graphiti_core/driver/falkordb/operations/next_episode_edge_ops.py:45 | T | Same function copied in hand-written code |
| getzep_graphiti | Same code copied 11 times: delete_by_uuids | graphiti_core/driver/falkordb/operations/community_node_ops.py:104 | T | Same function copied in hand-written code |
| getzep_graphiti | Same code copied 11 times: delete_by_group_id | graphiti_core/driver/falkordb/operations/community_node_ops.py:88 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 2 times: unreliable_api_call | cookbook/04_workflows/08_human_in_the_loop/error/01_error_retry_skip.py:24 | F | Example or demo code, standalone by design |
| agno-agi_agno | Same code copied 2 times: get_session | libs/agno/agno/db/base.py:574 | F | Abstract stub or no-op hook, the same on purpose |
| getzep_graphiti | Same code copied 3 times: get_by_uuid | graphiti_core/driver/falkordb/operations/has_episode_edge_ops.py:108 | T | Same function copied in hand-written code |
| CopilotKit_CopilotKit | Same code copied 2 times: chatApiEndpoint, returnAndThrowInDebug | packages/react-core/src/v1-deprecated/context/copilot-context.tsx:345 | T | Same function copied in hand-written code |
| CopilotKit_CopilotKit | Same code copied 2 times: search_flights_impl, _search_flights | showcase/integrations/crewai-conversational-flows/src/agents/tool_rendering.py:202 | F | Example or demo code, standalone by design |
| getzep_graphiti | Same code copied 3 times: load_embeddings_bulk | graphiti_core/driver/falkordb/operations/entity_edge_ops.py:259 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 2 times: to_json | libs/agno/agno/run/agent.py:899 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 2 times: complete_task_run | libs/agno/agno/db/postgres/async_postgres.py:7147 | T | Same function copied in hand-written code |
| openai_openai-agents-python | Same code copied 2 times: on_tool_start | src/agents/lifecycle.py:83 | F | Abstract stub or no-op hook, the same on purpose |
| firecrawl_open-lovable | Same code copied 2 times: FirecrawlLogo, Logo | components/FirecrawlLogo.tsx:1 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 2 times: _validate_user_id | libs/agno/agno/vectordb/cassandra/cassandra.py:111 | T | Same function copied in hand-written code |
| agno-agi_agno | Same code copied 4 times: create_sync_knowledge, create_async_knowledge, create_knowledge | cookbook/07_knowledge/09_archive/readers/from_path.py:32 | F | Example or demo code, standalone by design |
| agno-agi_agno | Same code copied 2 times: save_resources | libs/agno_infra/agno/aws/resources.py:539 | T | Same function copied in hand-written code |

## Needs-review leads (random 30 of 4,273, seed 20261112)

| Repo | Finding | Where | Verdict | Reason |
|---|---|---|---|---|
| browserbase_stagehand | Documented variable is never read: SEARCH_API_KEY | packages/docs/v3/best-practices/mcp-integrations.mdx:154 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| CopilotKit_CopilotKit | Possibly the same job done 3 times: onKeyDown | packages/angular/src/generative-ui/lib/standard/composites/entity-card.ts:256 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| stackblitz-labs_bolt.diy | Possibly the same job done 3 times: getDiligentDanPrompt, getSimpleSalPrompt, getTinyTimPrompt | app/lib/common/prompts/diligent-dan.ts:13 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| agno-agi_agno | Possibly the same job done 2 times: refresh_message_media_urls, arefresh_message_media_urls | libs/agno/agno/utils/media_offload.py:705 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| microsoft_markitdown | Possibly the same job done 3 times: _image_to_html | packages/markitdown-ocr/src/markitdown_ocr/_docx_converter_with_ocr.py:37 | T | Real duplicated job |
| Skyvern-AI_skyvern | Possibly the same job done 2 times: get_browser_session | skyvern/client/client.py:2317 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| coleam00_Archon | Possibly the same job done 2 times: upsertWorkflowNodeSession, upsertWorkflowRunNodeSession | packages/core/src/db/workflow-node-sessions.ts:50 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| agno-agi_agno | Possibly the same job done 5 times: list_projects, list_products, list_tasks | libs/agno/agno/company/manager.py:255 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| agno-agi_agno | Possibly the same job done 2 times: drop_opted_out_member_media, collect_opted_out_media_ids | libs/agno/agno/utils/agent.py:657 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| assistant-ui_assistant-ui | Possibly the same job done 2 times: delete | packages/cloud/src/AssistantCloudThreadMessages.ts:152 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| CopilotKit_CopilotKit | Possibly unused dependency: openai | examples/integrations/langgraph-python/agent/pyproject.toml:10 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| stackblitz-labs_bolt.diy | Possibly unused dev dependency: eslint-plugin-prettier | package.json:191 | U | Can't tell without running the linter |
| coleam00_Archon | Possibly the same job done 2 times: createRunModelBindingsMetadata, logRunModelOverrides | packages/workflows/src/model-validation.ts:532 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| CopilotKit_CopilotKit | Possibly the same job done 2 times: limitMessagesToTokenCount | packages/runtime/src/v1-deprecated/service-adapters/anthropic/utils.ts:5 | T | Real duplicated job |
| agno-agi_agno | Possibly the same job done 4 times: transition_project, transition_task | libs/agno/agno/db/postgres/async_postgres.py:6832 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| agno-agi_agno | Possibly the same job done 6 times: _create_table | libs/agno/agno/db/mysql/async_mysql.py:174 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| pydantic_pydantic-ai | Possibly the same job done 2 times: prepare_request | pydantic_ai_slim/pydantic_ai/models/anthropic/__init__.py:1467 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| CopilotKit_CopilotKit | Possibly unused dev dependency: pytest-asyncio | packages/intelligence-langgraph-python/pyproject.toml:19 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| topoteretes_cognee | Possibly the same job done 6 times: decorator, wrapper, wait, sleep_and_retry_sync | cognee/infrastructure/databases/graph/neo4j_driver/deadlock_retry.py:25 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| Skyvern-AI_skyvern | Possibly the same job done 2 times: _handler_get_past_episodes_for_block, _handler_get_episodes_for_run | skyvern/services/script_reviewer_v3/skills/investigate.py:176 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| Skyvern-AI_skyvern | Possibly the same job done 2 times: get_workflow_runs_by_id, get_workflow_runs_by_id_legacy | skyvern/forge/sdk/routes/agent_protocol.py:6094 | T | Real duplicated job |
| agno-agi_agno | Possibly the same job done 2 times: async_reasoning_demo, sync_reasoning_demo | cookbook/03_teams/16_search_coordination/02_coordinated_reasoning_rag.py:125 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| pydantic_pydantic-ai | Possibly the same job done 2 times: client, _transport | src/pydantic_clai2/pydantic_clai2/builtin_plugins/posthog.py:248 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| Skyvern-AI_skyvern | Possibly the same job done 2 times: create_task, create_task_v2 | evaluation/core/__init__.py:43 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| agno-agi_agno | Possibly the same job done 2 times: _load_team_from_db, _load_workflow_from_db | libs/agno/agno/tools/studio_runner.py:2095 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| assistant-ui_assistant-ui | Possibly the same job done 5 times: handleUpdateCustom, updateCustom | packages/core/src/react/client/InMemoryThreadList.ts:222 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| agno-agi_agno | Possibly the same job done 3 times: get_session_runs | libs/agno/agno/client/os.py:1878 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| agno-agi_agno | Possibly the same job done 2 times: make_memories, _make_memories | libs/agno/agno/agent/_managers.py:29 | T | Real duplicated job |
| topoteretes_cognee | Possibly the same job done 3 times: get_node_delete_data | cognee/infrastructure/databases/graph/ladybug/adapter.py:1616 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |
| openai_openai-agents-python | Possibly unused dependency: pyjwt | examples/realtime/twilio/requirements.txt:4 | F | Look alike on purpose (sync/async, per-entity, per-adapter), different jobs, or used indirectly |

## Repos and commits

| Repo | Commit |
|---|---|
| https://github.com/pydantic/pydantic-ai | `0f7648a1b0685389ea368ea4f7b754b393cea8c7` |
| https://github.com/openai/openai-agents-python | `27c75c5f9c5fd957d28c04fb7e9eeacaad4630a9` |
| https://github.com/huggingface/smolagents | `96f33faaf028479119ec8d34507b47694cf14e34` |
| https://github.com/letta-ai/letta | `8816d3a8516893bfdc0fa14bfb1235226012e295` |
| https://github.com/Skyvern-AI/skyvern | `a8be7439e0d134c4ff0e317d81aba94cc29638a1` |
| https://github.com/assistant-ui/assistant-ui | `eb65bd4558b769550093003c95e5867ad0ec8eba` |
| https://github.com/firecrawl/open-lovable | `806b8072f28afea11fa2b028cac1449eff74c511` |
| https://github.com/stackblitz-labs/bolt.diy | `353b44c8f386aeb15abdf277515d78ff50f7578e` |
| https://github.com/browserbase/stagehand | `310957518a931c1a84c5968b13282cc30acd1be9` |
| https://github.com/coleam00/Archon | `442c90f10b3d24fc823ce5b73bfc50932c1a4844` |
| https://github.com/microsoft/markitdown | `ed178952a6672abdfd35b5f9e76a80b6cefc086e` |
| https://github.com/getzep/graphiti | `024b83679e48e41d59a7d1714ac0870aff2e4676` |
| https://github.com/topoteretes/cognee | `8853378eae5366addb064e758e560a456146ae1a` |
| https://github.com/modelcontextprotocol/servers | `36380287ed3b15d115b55f546617d723072b1296` |
| https://github.com/agno-agi/agno | `f45ccee83dcd332c6a2cf46b0a0add3e7f7d3e68` |
| https://github.com/langchain-ai/open_deep_research | `1b7d2e80db9faa586165c60e09096dbbfd483a64` |
| https://github.com/CopilotKit/CopilotKit | `c7815d9ac96f48fb3a64ad8c18e176a870c81763` |
