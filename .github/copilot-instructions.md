# TBS5530 Android DVB Driver — Copilot instructions

## Scope and roles
- **Only editable application repository:** this `AndroidDvbDriver/` checkout. It is the Android userspace driver fork. Prioritize unrooted Android DVB-T2 with later room for DVB-C and DVB-S/S2/S2X.
- **Read-only source references:** sibling `../references/tbs-linux_media/` (official TBS Linux), `../references/cxd28xx/` (community C), `../references/coax/` (Go userspace). These are separate Git repositories; do not edit, format, commit, or stage their files. A label like `READ-ONLY` in the VS Code workspace is descriptive, not an OS permission control.
- Multi-root VS Code workspace is saved as `TBS5530.code-workspace` inside this repository. `tools/` is also inside this repository, and should not be added as a separate workspace root.
- Respect preexisting user-level Copilot instructions (MCP tool discovery, GitNexus, AgentMemory, shell policy). Never copy secrets or global MCP Proxy settings into the repository.

## Toolchain and operating systems
- **Windows PowerShell is the primary shell** for Git administration, Gradle Wrapper, Java and Android builds. Keep `D:\Android\Sdk` as the single Android SDK install; `ANDROID_HOME=D:\Android\Sdk`, `ANDROID_AVD_HOME=D:\Android\AVD`.
- Verified build baseline (2026-11-02; recheck before use): JDK `25.0.5.0.3`, Gradle Wrapper `9.8.2`, AGP `9.4.1`, compile SDK `37.2`, target SDK `37`, min SDK `21`, NDK `30.0.16248370`, CMake `4.1.2`, Build-Tools `37.0.0`. ADB `37.0.1`, Emulator `37.2.13`, command-line tools `23.0.0`; installed runtime images may differ from compile SDK. Keep Java source/target compatibility at `1.8`; do not emit Java 25 bytecode for Android. Do not upgrade versions during a runtime baseline test.
- `android` is the **official Google Android CLI**; inspect `android --help`, `android info sdk`, `android --sdk=D:\Android\Sdk sdk list` before selecting commands. Use the repository's `./gradlew.bat` (PowerShell `./gradlew.bat` or `.\gradlew.bat`) for reproducible build/test tasks. Do not silently upgrade SDK/NDK/AGP/Gradle or add a second SDK in WSL.
- **WSL2 Ubuntu-22.04** is exclusively for Linux-specific diagnostics (PyUSB, libusb, lsusb, firmware inspection); PowerShell can call `wsl.exe -d Ubuntu-22.04 -- <cmd>` explicitly. The shared repo is `/mnt/d/API/TBS5530-DVB-Android/AndroidDvbDriver`. Do not assume opening a WSL terminal tab redirects Agent execution to Linux.
- For ordinary POSIX helpers prefer the existing global Git Bash policy; use WSL specifically when the task needs an actual Linux environment or USB/IP access.

## Working language and reporting
- **User-facing explanations, plans, status updates and final reports must be in Vietnamese.** Search and read technical material in any language, especially original English documentation.
- Prefer Vietnamese for new research, ADR, worklog and handoff explanations, while preserving existing historical content verbatim. Keep identifiers, command syntax, source code, API names, links and file paths unchanged.
- Do not translate or rewrite existing English documents merely to enforce a language preference; focus on clarity and traceability.

## Hardware and MCP policy
- USB observations from user tests: VID:PID `734c:5530`, interface 0, bulk IN endpoints `0x82` and `0x81` (512-byte max packet); Windows/WSL2 enumeration and PyUSB claim/release passed. Firmware upload, I2C operations, DVB signal lock, MPEG-TS streaming and Android USB Host remain **NOT TESTED** until documented otherwise.
- **No firmware upload, CPU reset, I2C/vendor control write, LNB voltage, USB driver replacement, `usbipd bind/attach/detach/unbind`, hardware-changing steps or OS settings changes without an explicit reviewed plan and user approval.** Safe source inspection, USB descriptors and test doubles are allowed.
- Follow `.github/instructions/usbipd-wsl.instructions.md` for any Windows ↔ WSL2 USB ownership task. Windows runs `usbipd`; WSL2 runs `lsusb`/PyUSB. Verify the current `734c:5530` BUSID; the historic BUSID `6-2` must never be hardcoded.
- Copilot uses the **existing MCP Proxy gateway**, not direct per-project MCP server connections. Discover upstream tools through the global policy (`retrieve_tools` when appropriate); do not invent tool names or edit MCP Proxy configuration. ADB MCP can assist emulator/device UI tests but an emulator cannot validate a real USB DVB tuner.

## Source and dependency rules
- Official `tbsdtv/linux_media` is the first source for board protocol. Compare `koreapyj/cxd28xx` for fixes/streaming and `Till0196/coax` for userspace patterns. Record a **commit SHA + source file/function** for findings; never assume a newer implementation is necessarily more correct.
- Keep changes localized to the existing `app`, `drivers`, `dvbservice`, and `usbxfer` modules. Preserve existing tuner drivers and copyright/license notices; verify GPL compatibility and firmware redistribution separately.
- Do not treat JDK 25 as a reason to generate Java 25 bytecode. Audit current Gradle/AGP versions and compatibility, modernize incrementally on a separate branch, prove a clean build, and only then begin device-specific code.

## Repository sync workflow
- Script: `tools/Sync-TBS5530-Repositories.ps1` (inside this repo). Expected workspace parent: `D:\API\TBS5530-DVB-Android`.
- **Check only:** `.\tools\Sync-TBS5530-Repositories.ps1 -CheckOnly` from Windows PowerShell. Script can fetch but should not merge in this mode.
- **Full sync:** `.\tools\Sync-TBS5530-Repositories.ps1` only with user approval and when source review/hardware testing is idle. It should fetch main repo without merging; update clean reference repos only by fast-forward, preserving the six-directory TBS sparse-checkout.
- Before using reference code in research or implementation, record each checkout's `git rev-parse HEAD` in `docs/project/RESEARCH.md`; never let unreviewed upstream changes silently alter a currently active experiment.
- Logs are outside this Git repository at `../logs/repository-sync.log`. Do not commit logs, firmware blobs, build outputs, personal device IDs or credentials.

## Persistent project records and handoff
- At every new session read `docs/project/STATUS.md`, `docs/project/ENVIRONMENT.md` and recent `docs/project/WORKLOG.md`; consult `RESEARCH.md`, `DECISIONS.md`, `TEST-MATRIX.md` as needed.
- On meaningful work, append evidence and exact execution environment/commands/tests to `WORKLOG.md`, add source-backed discoveries to `RESEARCH.md`, update `TEST-MATRIX.md` and succinct current `STATUS.md`. Don't declare PASS unless the test actually ran.
- Record architectural choices in `DECISIONS.md` and provide a concise next actionable step at the end of each phase.
- Relevant scoped instructions: `.github/instructions/android-build.instructions.md`, `usb-hardware.instructions.md`, `usbipd-wsl.instructions.md`, `adb-mcp.instructions.md`, `repository-sync.instructions.md`, and `project-records.instructions.md`.
- Reusable skills: `.github/skills/tbs-research/SKILL.md` and `.github/skills/project-handoff/SKILL.md`.
