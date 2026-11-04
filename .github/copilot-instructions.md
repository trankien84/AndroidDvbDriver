# TBS5530 Android DVB Driver — Copilot instructions (v4)

## Scope and project identity
- **Editable repository:** this `AndroidDvbDriver/` fork only. Maintain unrooted stock-Android USB DVB support; prioritize TBS5530 DVB-T2 without regressing current drivers. Do not assume DVB-C/S/S2/S2X is implemented merely because expansion is planned.
- **Read-only sibling references:** `../references/tbs-linux_media` (official TBS Linux), `../references/cxd28xx` (community C) and `../references/coax` (Go userspace). Never edit, stage, commit, or format reference trees. Display labels are not filesystem permissions.
- `TBS5530.code-workspace` and `tools/` belong to this Git repository. The workspace must resolve all four roots using relative paths. Preserve `!TBS5530.code-workspace` in `.gitignore`.
- Respect global user instructions for MCP Proxy discovery, AgentMemory, GitNexus, PowerShell/Git Bash and security. Do not create per-project direct MCP connections or store credentials.

## Source of truth and toolchain freshness
- **DO NOT treat version numbers in instructions, old chats, or historic docs as requirements or as the latest installed versions.** Read active Gradle build files, `gradle/wrapper/gradle-wrapper.properties`, SDK `source.properties`, `JAVA_HOME`, the Android CLI inventory and the current emulator/AVD config before each version-dependent task.
- Distinguish four states: **declared in build**, **installed locally**, **latest stable published by vendor**, and **tested/approved for this project**. The correct version for reproducible builds is the version declared in project build files, until a reviewed migration changes it.
- For tasks about upgrading, verify current stable release notes, compatibility matrices, minSdk constraints, Gradle-JDK compatibility and native 16 KiB page-size support from official upstream sources. Never use preview/canary/alpha/beta/RC just because its version number is newer.
- Android SDK is at `D:\Android\Sdk` (Windows), AVD storage at `D:\Android\AVD`; validate the real filesystem and environment variables rather than assuming parent processes refreshed after a Windows environment edit.
- Official Google `android` CLI is preferred for supported SDK/AVD/device operations; inspect `android --help` and subcommand help before invoking. `sdkmanager.bat` is permitted for exact package management when the CLI does not expose it. Gradle Wrapper (`.\gradlew.bat`) is authoritative for project builds.
- **No automatic upgrade, downgrade, SDK install/uninstall, or cache pruning.** First run `tools/Inspect-TBS5530-Toolchain.ps1`, compare the results against build declarations, propose an exact package-specific plan and seek approval. Never run `sdkmanager --update` indiscriminately.
- Read `.github/instructions/toolchain-lifecycle.instructions.md` and `.github/instructions/android-build.instructions.md` for build and maintenance tasks. Re-run relevant unit, instrumentation, UI, native ABI and release/lint gates after changing toolchain versions; record regressions and evidence.
- Do not infer Java bytecode level from Gradle's runtime JDK. Preserve `minSdk=21` unless an explicit compatibility decision authorizes change. Keep intentionally pinned older libraries that still support API 21.

## Shells and tools
- Windows PowerShell is primary for Git, Android CLI, Gradle, JDK and ADB; verify exact executable path. Git Bash is suitable for ordinary POSIX helpers according to global policy.
- WSL2 `Ubuntu-22.04` is for genuine Linux-only inspection, PyUSB/libusb and USB/IP diagnostics; invoke explicitly using `wsl.exe -d Ubuntu-22.04 -- ...`. Do not install or maintain a duplicate SDK in WSL2.
- Use ADB MCP via the existing MCP Proxy for emulator/device interaction when appropriate. Always inspect tool discovery results and the device serial. Emulator UI and native-library smoke tests do NOT prove the TBS5530 hardware driver.
- **Wireshark and USBPcap are already installed on the Windows development machine (user-confirmed).** Detect executable paths, versions, capture interfaces, permissions and USBPcap driver health at runtime. Do not reinstall, update or reconfigure them automatically. USB capture and any USB ownership/driver changes need task-specific consent; see `.github/instructions/usb-capture-windows.instructions.md`.

## Language and records
- **All user-facing discussion, analysis, logs of decisions, reports and project handoffs in Vietnamese**; keep source identifiers, commands, API names and repository instructions/skills in English. Research vendor docs in any language.
- At session start read the latest sections of `docs/project/STATUS.md`, `ENVIRONMENT.md` and `WORKLOG.md`, plus relevant `RESEARCH.md`, `DECISIONS.md` and `TEST-MATRIX.md`. These contain historical snapshots; prefer the newest dated entry, verify current state before reporting.
- Add factual evidence with Git SHA, environment, exact commands, actual test results and PASS/FAIL/BLOCKED/NOT RUN; never change previous PASS into a new PASS without rerunning. Do not overwrite historical sections to reflect newer conditions.
- Preserve each `docs/project/` file's current headings/tables/ADR conventions. Write readable Vietnamese with correct word spacing, Markdown separators and concise paragraphs; inspect `git diff --check` and manually review new text for run-together words. See `.github/instructions/project-records.instructions.md`.
- **Current branch/runtime phase must be discovered from Git and the top of STATUS.md**, not from this file. After the validated Phase 1.1 baseline, plan toolchain updates as separate, reversible tasks before porting USB hardware logic.

## Hardware and repository safety
- TBS5530 VID:PID `734c:5530`; device enumeration and WSL/PyUSB interface claim were historically observed. Firmware, I²C, DVB lock and TS reception on Android are NOT verified without new tests.
- Do not run `usbipd bind/attach/detach/unbind`, firmware uploads, USB vendor/I²C writes, LNB voltage, driver changes or WSL reset without explicit plan and approval. Resolve the current BUSID using `usbipd list`; never hardcode the historical `6-2`. See `.github/instructions/usbipd-wsl.instructions.md`.
- Official TBS driver is first hardware-protocol reference; compare community C and Go userspace implementations with SHA/file/function provenance, never assume newest community code is correct.
- Keep TBS source synchronization opt-in, outside active debugging. `tools/Sync-TBS5530-Repositories.ps1 -CheckOnly` may fetch; treat it as a network operation, not read-only. Do not auto-reset, rebase, merge or push repositories. Preserve six TBS sparse-checkout paths and review Git index health before repair.
- License/copyright and firmware redistributability require independent review. Keep secret-bearing files, APKs, captured TS, raw logs, local SDK and firmware blobs out of Git.

## Scoped instructions and reusable skills
- Existing: `android-build`, `usb-hardware`, `usbipd-wsl`, `adb-mcp`, `repository-sync`, `project-records` under `.github/instructions/`.
- New: `.github/instructions/toolchain-lifecycle.instructions.md`, `.github/skills/toolchain-audit/SKILL.md`.
- Existing: `.github/skills/tbs-research/SKILL.md` and `project-handoff/SKILL.md`. Preserve all global Copilot customization.
