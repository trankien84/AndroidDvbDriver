---
name: project-handoff
description: Resume, checkpoint or hand off a TBS5530 Android driver port session while preserving research, test evidence and next steps across Copilot conversations.
---
# Project handoff workflow

At session start (or before starting a new phase):
1. Read `docs/project/STATUS.md`, `docs/project/ENVIRONMENT.md`, recent `docs/project/WORKLOG.md`, and relevant `RESEARCH.md`/`DECISIONS.md`.
2. Verify repository branch/HEAD and `git status` in the **AndroidDvbDriver repository only**. Check current toolchain when it affects the work.
3. Reconstruct what is confirmed by source vs hardware and what is untested. Propose only the next bounded step.

At the end of a meaningful working session:
1. Summarize changed files, branch/commit (if any), Windows and WSL commands run, tests with exact result, blockers, next immediate action.
2. Append to `docs/project/WORKLOG.md`; update test matrix if tests executed.
3. Update the top of `docs/project/STATUS.md` to a concise current state. Record significant choices in `DECISIONS.md`; discoveries in `RESEARCH.md`.
4. Keep log output and binaries outside Git, unless intentionally added as minimal fixtures.
5. Return an actionable handoff: what is done, what is not, and which single safe task to do next.

If nothing was executed, say so: do not fabricate progress.
