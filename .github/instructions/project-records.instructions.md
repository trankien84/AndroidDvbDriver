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
- At task start read STATUS/ENVIRONMENT/recent WORKLOG; at end update concise handoff and link deeper evidence.
