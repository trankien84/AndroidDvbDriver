---
name: toolchain-audit
description: Audit the TBS5530 Android Windows toolchain, compare configured, installed, latest stable and project-tested versions, and prepare a reviewed upgrade or cleanup plan without unintended package changes.
---
# Toolchain audit and controlled maintenance

1. Read project-wide instructions, `toolchain-lifecycle.instructions.md` and latest `docs/project/STATUS.md`, `ENVIRONMENT.md`, `TEST-MATRIX.md`.
2. Inspect Git branch/worktree, main Gradle scripts and wrapper metadata. Never infer project versions from historic snapshots.
3. On Windows PowerShell, run `tools/Inspect-TBS5530-Toolchain.ps1` (read-only). The report goes to workspace-level `logs/toolchain`, not tracked sources; `-NoReport -PassThru` provides structured data without saving a report. Verify no secrets before sharing output. If script execution is blocked, report the policy error and seek approval instead of bypassing it automatically.
4. Use `tools/Review-AndroidSdkCleanup.ps1` only to produce a review matrix of side-by-side SDK packages. Do not delete or install packages.
5. Check authoritative vendor pages: Android AGP releases/compatibility, Gradle releases/JDK matrix, Android SDK/NDK package documentation, Android CLI commands, and VS Code Copilot instructions when relevant. Capture URL and checked date.
6. Present a table with **declared / installed / latest stable / tested / action / rationale**. Mark unknowns explicitly. Highlight API 21, native ABIs, AVD image and 16KiB page-size tradeoffs.
7. Request authorization for a specific bounded upgrade/cleanup plan before running installations, uninstalls or changing Gradle files. Never silently update the SDK catalog or use broad `--update`.
8. After approved changes, run appropriate test gates and record source version, toolchain, environment, results and remaining coverage holes in docs. Keep checkpoints and rollback instructions.
9. Answer in Vietnamese; keep instructions and this skill in English.
