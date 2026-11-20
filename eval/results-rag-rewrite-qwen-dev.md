# Retrieval results

Embeddings: nomic-embed-text:latest. Query rewriting: qwen2.5:3b-instruct. Rank = position of the first result from a file that answers the question (0 = not in the top 10).

| Search | Questions | Found first | Found in top 5 | MRR@10 |
|---|---:|---:|---:|---:|
| keyword | 50 | 16 (32%) | 30 (60%) | 0.44 |
| vector | 50 | 24 (48%) | 40 (80%) | 0.61 |
| hybrid | 50 | 20 (40%) | 37 (74%) | 0.54 |
| hybrid+rewrite | 50 | 20 (40%) | 40 (80%) | 0.56 |

| Repo | Question | keyword | vector | hybrid | hybrid+rewrite | Top hybrid result | Rewritten queries |
|---|---|---:|---:|---:|---:|---|---|
| markitdown | How does it decide which converter handles a given file? | 0 | 2 | 2 | 4 | packages/markitdown/src/markitdown/_base_converter.py | file handler; converter chooser; file mapper |
| markitdown | How are third-party plugins discovered and loaded? | 3 | 2 | 1 | 1 | packages/markitdown/src/markitdown/_markitdown.py | plugin discovery; plugin loader; third party plugin loading mechanism |
| markitdown | How does it guess the file type when there's no file extension? | 1 | 2 | 1 | 2 | packages/markitdown/src/markitdown/_markitdown.py | guess_file_type_no_ext; detect_file_type_without_ext; file_type_guesser_no_ext |
| markitdown | How are transcripts pulled from online videos? | 1 | 1 | 1 | 1 | packages/markitdown/src/markitdown/converters/_youtube_converter.py | video transcript pull; online video transcripts; fetch video captions |
| markitdown | How does it ask a language model to describe a picture? | 0 | 1 | 5 | 2 | README.md | ask language model picture; describe image to model; model request image description |
| markitdown | How are spreadsheets turned into markdown tables? | 0 | 0 | 10 | 3 | packages/markitdown/src/markitdown/converters/_pdf_converter.py | spreadsheet to markdown converter; excel to markdown table; convert spreadsheet to markdown |
| markitdown | Which library extracts text from PDF documents? | 3 | 3 | 3 | 4 | packages/markitdown-ocr/README.md | pdf text extraction library; library for extracting PDF text; PDF text extraction library |
| markitdown | How is speech in recordings turned into text? | 1 | 1 | 1 | 1 | packages/markitdown/src/markitdown/converters/_audio_converter.py | speech to text api; speech recognition library; convert audio to text function |
| markitdown | What happens when an optional package needed for a format isn't installed? | 1 | 1 | 1 | 1 | packages/markitdown/src/markitdown/_exceptions.py | install check; package missing; format dependencies |
| markitdown | How are data URIs and file URIs parsed? | 3 | 1 | 2 | 2 | packages/markitdown/src/markitdown/_markitdown.py | data uri parsing; file uri handling; uri parser functions |
| markitdown | Where are equations in Word documents converted to LaTeX? | 1 | 1 | 1 | 1 | packages/markitdown/src/markitdown/converter_utils/docx/pre_process.py | equation conversion; latex conversion; word to latex |
| markitdown | How does it use Azure's cloud document analysis service? | 6 | 5 | 5 | 4 | README.md | azure cloud doc analysis; document analysis azure service; azure cloud document service |
| markitdown | How are saved Outlook email messages converted? | 1 | 1 | 1 | 1 | packages/markitdown/src/markitdown/converters/_outlook_msg_converter.py | outlook email conversion; email message conversion code; convert saved outlook emails |
| markitdown | How would I write my own converter as a plugin? | 5 | 3 | 2 | 2 | packages/markitdown/src/markitdown/_markitdown.py | plugin converter; custom converter plugin; write converter plugin |
| markitdown | How does the OCR add-on read text inside images embedded in PDFs? | 3 | 4 | 3 | 3 | packages/markitdown-ocr/README.md | ocr read pdf images; pdf ocr text extraction; image text extraction ocr |
| markitdown | How are web page tables and links rewritten as markdown? | 0 | 2 | 0 | 7 | README.md | rewrite_tables_markdown; convert_links_markdown; markdown_web_tables |
| markitdown | How does the command line tool read from standard input and choose where to write? | 0 | 7 | 9 | 8 | README.md | command line tool input; standard input reader; output writer command line |
| markitdown | How are Jupyter notebooks converted? | 1 | 1 | 1 | 1 | packages/markitdown/src/markitdown/converters/_ipynb_converter.py | jupyter notebook conversion; convert jupyter notebook; jupyter notebook to other format |
| markitdown | How is photo metadata read with an external command-line tool? | 6 | 0 | 0 | 0 | README.md | read photo metadata; external command line tool metadata; photo metadata external tool |
| markitdown | How are news feeds in XML converted? | 3 | 1 | 1 | 1 | packages/markitdown/src/markitdown/converters/_rss_converter.py | xml parser; feed converter; xml to html converter |
| markitdown | What does it do with compressed archives containing several files? | 9 | 1 | 3 | 2 | packages/markitdown/src/markitdown/converter_utils/_xlsx_images.py | extract_files; unzip_multiple; archive_contents |
| markitdown | How are slide decks converted, including speaker notes? | 2 | 3 | 2 | 1 | packages/markitdown-ocr/README.md | slide conversion; speaker notes conversion; presentation conversion |
| markitdown | How do I run the MCP server over HTTP instead of stdio? | 1 | 1 | 1 | 1 | packages/markitdown-mcp/README.md | run mcp over http; change mcp server output; mcp server http listener |
| markitdown | How are search engine result pages cleaned up? | 3 | 1 | 1 | 1 | packages/markitdown/src/markitdown/converters/_bing_serp_converter.py | clean_pages; search_result_cleaner; page_cleaning |
| markitdown | How do I install support for every file format at once? | 2 | 1 | 1 | 2 | README.md | install all file formats; automate file format support; install multiple file formats |
| ai-chatbot | How can someone use the app without creating an account? | 0 | 0 | 0 | 0 | app/(auth)/register/page.tsx | app login; guest app access; no account usage |
| ai-chatbot | How is the assistant's reply streamed back to the browser? | 0 | 2 | 10 | 3 | components/ai-elements/reasoning.tsx | stream reply to browser; stream response to client; websocket for chat reply |
| ai-chatbot | How does a chat pick up again after the page reloads mid-answer? | 0 | 1 | 0 | 5 | components/chat/sidebar-history.tsx | chat resume; reconnect chat; resume chat session |
| ai-chatbot | How many messages per day is each kind of user allowed? | 1 | 2 | 1 | 1 | lib/ai/entitlements.ts | user message limit; daily message quota per user type; user message allowances |
| ai-chatbot | Where are chat messages saved to the database? | 1 | 1 | 1 | 1 | lib/db/queries.ts | chat messages db; save chat messages to db; database chat messages |
| ai-chatbot | What tables does the database have for chats and messages? | 1 | 0 | 8 | 3 | lib/db/queries.ts | chat tables; messages tables; database tables chats messages |
| ai-chatbot | How are uploaded attachments stored? | 0 | 0 | 10 | 0 | components/chat/multimodal-input.tsx | attachments storage; file storage attachments; uploaded_file_storage |
| ai-chatbot | How does a new conversation get its title? | 0 | 4 | 5 | 0 | lib/ai/prompts.ts | new conversation title; set conversation name; assign chat name |
| ai-chatbot | How does the assistant look up the forecast for a city? | 1 | 1 | 1 | 1 | lib/ai/tools/get-weather.ts | city forecast lookup; weather forecast lookup function; get_city_forecast_api_call |
| ai-chatbot | How does the assistant propose edits to a document the user is writing? | 2 | 3 | 2 | 3 | lib/ai/tools/edit-document.ts | proposeEdits; documentEditor; userInputIntegration |
| ai-chatbot | Which language models can the user choose from? | 2 | 1 | 1 | 2 | lib/ai/providers.ts | user selectable models; available language models; model choice options |
| ai-chatbot | How do thumbs up and thumbs down on answers get recorded? | 4 | 0 | 4 | 2 | components/chat/message-actions.tsx | answer voting; upvote mechanism; downvote system |
| ai-chatbot | How are passwords hashed when someone signs up? | 1 | 1 | 1 | 1 | lib/db/utils.ts | password hashing; signup password; hashed password storage |
| ai-chatbot | How does Python code from the assistant run inside the browser? | 1 | 1 | 3 | 6 | components/chat/code-editor.tsx | browser runtime; python in browser; python web execution |
| ai-chatbot | How are spreadsheet artifacts generated? | 0 | 0 | 0 | 0 | artifacts/sheet/client.tsx | spreadsheet artifact generation; generate spreadsheet artifacts; artifacts from spreadsheets |
| ai-chatbot | How does the sidebar load older conversations as you scroll? | 2 | 1 | 1 | 1 | components/chat/sidebar-history.tsx | sidebar load older; scroll sidebar load; load older conversations |
| ai-chatbot | What instructions does the assistant get before every conversation? | 0 | 0 | 8 | 4 | components/ai-elements/conversation.tsx | assistant instructions; conversation setup; initial prompt |
| ai-chatbot | What happens when a user deletes a conversation? | 2 | 2 | 2 | 1 | app/(chat)/api/history/route.ts | delete conversation logic; user deleted conversation action; onUserConversationDelete |
| ai-chatbot | How are database schema changes applied? | 0 | 9 | 0 | 0 | lib/db/queries.ts | apply schema changes; database schema update; database migration scripts |
| ai-chatbot | Which settings and secrets must be configured before running it? | 1 | 4 | 2 | 3 | .github/workflows/playwright.yml | config settings; environment variables; secrets config |
| ai-chatbot | How are requests throttled so one person can't flood the server? | 7 | 1 | 3 | 1 | app/(chat)/api/history/route.ts | request throttling; rate limiting server; block flood requests |
| ai-chatbot | What happens to a visitor who opens the app without being signed in? | 0 | 1 | 1 | 8 | app/(auth)/api/auth/guest/route.ts | visitor action; unauthenticated user flow; public app behavior |
| ai-chatbot | How can a chat be made public or private? | 0 | 1 | 2 | 1 | components/chat/visibility-selector.tsx | make_chat_public; chat_privacy_setting; enable_chat_visibility |
| ai-chatbot | How are errors turned into responses with the right status code? | 1 | 1 | 1 | 1 | lib/errors.ts | error to response conversion; status code in response; error handling in responses |
| ai-chatbot | How does the editor show the differences between two versions of a document? | 0 | 4 | 0 | 4 | components/chat/version-footer.tsx | editor diff function; document version comparison; text diff algorithm |
