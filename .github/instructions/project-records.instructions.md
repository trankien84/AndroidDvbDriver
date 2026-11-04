---
name: Research, experiment records and handoff
description: Use when researching driver source, writing docs, reviewing evidence, tracking tests, or preparing a resumable Copilot session handoff.
applyTo: "docs/project/**"
---
# Documentation and verification
- `STATUS.md`: short current phase, last verified result, next bounded task, blocker and research reference SHAs.
- `ENVIRONMENT.md`: actual Windows/Android/WSL toolchain paths and versions, observed vs assumed.
- `RESEARCH.md`: date, question, source file/function, URL and checkout commit SHA, source fact vs hardware fact vs hypothesis, verification plan.
- `WORKLOG.md`: append-only sessions with commands (OS + cwd), changed files, exact test output, next step.
- `DECISIONS.md`: ADR-style decision with rationale, alternatives, tradeoffs and reopen trigger.
- `TEST-MATRIX.md`: distinguish PASS/FAIL/BLOCKED/NOT RUN; include device/runtime and evidence without secrets/identifying data.
- Do not rewrite historic evidence or invent tests, hashes, timings, firmware behavior or successful APK builds.

## Readable Vietnamese and existing Markdown layout
- Before editing, inspect the latest 1–2 sections of **the specific target file**. Preserve its established hierarchy, date-heading style, table structure, ADR metadata and append-only chronology; do not reformat all historical content.
- Write complete, clear Vietnamese sentences. Briefly explain specialist terms once when needed, and keep research facts, hypotheses, measurements, decisions and next actions separate. Prefer short paragraphs and compact tables over compressed keyword chains.
- **No joined words:** check Vietnamese word boundaries manually. Never output fragments such as `đãPASS`, `tạiOnePlus`, `vàfirmware`, `Phase2.8đã`, or `` `FX2`là ``. Put a space after ordinary punctuation and on both sides of inline-code spans when prose continues. Preserve intentional no-space constructs inside code, device IDs, hex values, hashes, links and file paths.
- Follow valid Markdown: a blank line around headings, tables and fenced code blocks; one space after heading/list markers; readable table cell spacing; sensible line wrapping without splitting URLs, hashes or technical identifiers. Use normal UTF-8 and preserve existing Vietnamese diacritics.
- Avoid a giant unbroken line of prose or a one-row table packed with an entire research report. Use a short executive finding followed by evidence and implications as appropriate to the destination file.
- After updating docs, run `git diff --check`, inspect the exact added lines, visually review newly written paragraphs for glued words/awkward punctuation, and check relative links and Markdown heading/table layout. An automated whitespace check does **not** detect every Vietnamese spacing error; do not claim a complete proof without manual review.
- Do not silently repair or reflow older sections. If historical formatting needs a broad cleanup, propose a separate scoped change and protect historical evidence.

- At task start read STATUS/ENVIRONMENT/recent WORKLOG; at end update concise handoff and link deeper evidence.
