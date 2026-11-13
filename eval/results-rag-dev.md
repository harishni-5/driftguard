# Retrieval results

Embeddings: nomic-embed-text:latest. Rank = position of the first result from a file that answers the question (0 = not in the top 10).

| Search | Questions | Found first | Found in top 5 | MRR@10 |
|---|---:|---:|---:|---:|
| keyword | 50 | 16 (32%) | 30 (60%) | 0.44 |
| vector | 50 | 24 (48%) | 40 (80%) | 0.61 |
| hybrid | 50 | 20 (40%) | 37 (74%) | 0.54 |

| Repo | Question | keyword | vector | hybrid | Top hybrid result |
|---|---|---:|---:|---:|---|
| markitdown | How does it decide which converter handles a given file? | 0 | 2 | 2 | packages/markitdown/src/markitdown/_base_converter.py |
| markitdown | How are third-party plugins discovered and loaded? | 3 | 2 | 1 | packages/markitdown/src/markitdown/_markitdown.py |
| markitdown | How does it guess the file type when there's no file extension? | 1 | 2 | 1 | packages/markitdown/src/markitdown/_markitdown.py |
| markitdown | How are transcripts pulled from online videos? | 1 | 1 | 1 | packages/markitdown/src/markitdown/converters/_youtube_converter.py |
| markitdown | How does it ask a language model to describe a picture? | 0 | 1 | 5 | README.md |
| markitdown | How are spreadsheets turned into markdown tables? | 0 | 0 | 10 | packages/markitdown/src/markitdown/converters/_pdf_converter.py |
| markitdown | Which library extracts text from PDF documents? | 3 | 3 | 3 | packages/markitdown-ocr/README.md |
| markitdown | How is speech in recordings turned into text? | 1 | 1 | 1 | packages/markitdown/src/markitdown/converters/_audio_converter.py |
| markitdown | What happens when an optional package needed for a format isn't installed? | 1 | 1 | 1 | packages/markitdown/src/markitdown/_exceptions.py |
| markitdown | How are data URIs and file URIs parsed? | 3 | 1 | 2 | packages/markitdown/src/markitdown/_markitdown.py |
| markitdown | Where are equations in Word documents converted to LaTeX? | 1 | 1 | 1 | packages/markitdown/src/markitdown/converter_utils/docx/pre_process.py |
| markitdown | How does it use Azure's cloud document analysis service? | 6 | 5 | 5 | README.md |
| markitdown | How are saved Outlook email messages converted? | 1 | 1 | 1 | packages/markitdown/src/markitdown/converters/_outlook_msg_converter.py |
| markitdown | How would I write my own converter as a plugin? | 5 | 3 | 2 | packages/markitdown/src/markitdown/_markitdown.py |
| markitdown | How does the OCR add-on read text inside images embedded in PDFs? | 3 | 4 | 3 | packages/markitdown-ocr/README.md |
| markitdown | How are web page tables and links rewritten as markdown? | 0 | 2 | 0 | README.md |
| markitdown | How does the command line tool read from standard input and choose where to write? | 0 | 7 | 9 | README.md |
| markitdown | How are Jupyter notebooks converted? | 1 | 1 | 1 | packages/markitdown/src/markitdown/converters/_ipynb_converter.py |
| markitdown | How is photo metadata read with an external command-line tool? | 6 | 0 | 0 | README.md |
| markitdown | How are news feeds in XML converted? | 3 | 1 | 1 | packages/markitdown/src/markitdown/converters/_rss_converter.py |
| markitdown | What does it do with compressed archives containing several files? | 9 | 1 | 3 | packages/markitdown/src/markitdown/converter_utils/_xlsx_images.py |
| markitdown | How are slide decks converted, including speaker notes? | 2 | 3 | 2 | packages/markitdown-ocr/README.md |
| markitdown | How do I run the MCP server over HTTP instead of stdio? | 1 | 1 | 1 | packages/markitdown-mcp/README.md |
| markitdown | How are search engine result pages cleaned up? | 3 | 1 | 1 | packages/markitdown/src/markitdown/converters/_bing_serp_converter.py |
| markitdown | How do I install support for every file format at once? | 2 | 1 | 1 | README.md |
| ai-chatbot | How can someone use the app without creating an account? | 0 | 0 | 0 | app/(auth)/register/page.tsx |
| ai-chatbot | How is the assistant's reply streamed back to the browser? | 0 | 2 | 10 | components/ai-elements/reasoning.tsx |
| ai-chatbot | How does a chat pick up again after the page reloads mid-answer? | 0 | 1 | 0 | components/chat/sidebar-history.tsx |
| ai-chatbot | How many messages per day is each kind of user allowed? | 1 | 2 | 1 | lib/ai/entitlements.ts |
| ai-chatbot | Where are chat messages saved to the database? | 1 | 1 | 1 | lib/db/queries.ts |
| ai-chatbot | What tables does the database have for chats and messages? | 1 | 0 | 8 | lib/db/queries.ts |
| ai-chatbot | How are uploaded attachments stored? | 0 | 0 | 10 | components/chat/multimodal-input.tsx |
| ai-chatbot | How does a new conversation get its title? | 0 | 4 | 5 | lib/ai/prompts.ts |
| ai-chatbot | How does the assistant look up the forecast for a city? | 1 | 1 | 1 | lib/ai/tools/get-weather.ts |
| ai-chatbot | How does the assistant propose edits to a document the user is writing? | 2 | 3 | 2 | lib/ai/tools/edit-document.ts |
| ai-chatbot | Which language models can the user choose from? | 2 | 1 | 1 | lib/ai/providers.ts |
| ai-chatbot | How do thumbs up and thumbs down on answers get recorded? | 4 | 0 | 4 | components/chat/message-actions.tsx |
| ai-chatbot | How are passwords hashed when someone signs up? | 1 | 1 | 1 | lib/db/utils.ts |
| ai-chatbot | How does Python code from the assistant run inside the browser? | 1 | 1 | 3 | components/chat/code-editor.tsx |
| ai-chatbot | How are spreadsheet artifacts generated? | 0 | 0 | 0 | artifacts/sheet/client.tsx |
| ai-chatbot | How does the sidebar load older conversations as you scroll? | 2 | 1 | 1 | components/chat/sidebar-history.tsx |
| ai-chatbot | What instructions does the assistant get before every conversation? | 0 | 0 | 8 | components/ai-elements/conversation.tsx |
| ai-chatbot | What happens when a user deletes a conversation? | 2 | 2 | 2 | app/(chat)/api/history/route.ts |
| ai-chatbot | How are database schema changes applied? | 0 | 9 | 0 | lib/db/queries.ts |
| ai-chatbot | Which settings and secrets must be configured before running it? | 1 | 4 | 2 | .github/workflows/playwright.yml |
| ai-chatbot | How are requests throttled so one person can't flood the server? | 7 | 1 | 3 | app/(chat)/api/history/route.ts |
| ai-chatbot | What happens to a visitor who opens the app without being signed in? | 0 | 1 | 1 | app/(auth)/api/auth/guest/route.ts |
| ai-chatbot | How can a chat be made public or private? | 0 | 1 | 2 | components/chat/visibility-selector.tsx |
| ai-chatbot | How are errors turned into responses with the right status code? | 1 | 1 | 1 | lib/errors.ts |
| ai-chatbot | How does the editor show the differences between two versions of a document? | 0 | 4 | 0 | components/chat/version-footer.tsx |
