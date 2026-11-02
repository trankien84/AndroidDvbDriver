---
name: Android Toolchain Lifecycle and Cleanup
description: Use when auditing installed tools, checking latest stable releases, updating AGP Gradle SDK NDK CMake JDK Android CLI or ADB, or evaluating removal of obsolete packages and caches.
applyTo: "tools/Inspect-TBS5530-Toolchain.ps1,tools/Review-AndroidSdkCleanup.ps1"
---
# Toolchain lifecycle policy

## Four distinct version states
1. `declared`: obtain from active `build.gradle` files, wrapper properties, build variants, test tasks and CMakeLists, not from stale docs.
2. `installed`: observe SDK `source.properties`, CLI inventory, actual executable locations and JDK environment for the current process.
3. `latest_stable`: verify with time-stamped vendor release notes and compatibility docs, *not* an alpha/preview or a scraped version listing.
4. `project_verified`: the exact toolchain and tests in latest `docs/project/TEST-MATRIX.md`; note the original date and device ABI.

## Audit → plan → approve → change → validate
- Run `tools/Inspect-TBS5530-Toolchain.ps1` in Windows PowerShell to collect a **local report**. Reports contain local paths/host details and live in workspace `../logs/toolchain/`; review/redact before sharing and only summarize safe version data in tracked docs. `-NoReport -PassThru` supports structured validation without saving a report. Do not bypass an execution-policy failure without explicit approval; never persist relaxed User/Machine policies as part of an audit.
- AVD discovery must pass the resolved AVD home to the emulator listing process and restore the caller's environment. Cross-check `.ini` registrations and normalize `.avd` directory suffixes; do not report a second device for the folder or mistake metadata-only results for a verified boot.
- Use `tools/Review-AndroidSdkCleanup.ps1` to generate a candidate/keep matrix. Treat candidates as **review-only**; the script MUST NOT uninstall anything.
- Inspect the SDK inventory with `android --sdk=... sdk list`, and when needed `sdkmanager.bat --sdk_root=... --list_installed`, `--list --newer` and installed package `source.properties`. Validate exact command syntax with `--help`. Do not assume all commands are interchangeable.
- Review other projects, active AVD images, CI/pinned NDK/CMake settings, Gradle caches and shared JDK installations before declaring a package unused.
- Never delete the sole selected JDK, a pinned SDK/NDK/CMake/Build-Tools, active system image, current emulator/platform-tools/cmdline-tools, or an AVD's disk image. Keep API 21 support and device/ABI test coverage in the plan.
- For upgrades, propose exact version changes with links and known compatibility; isolate AGP upgrades to a separate commit from SDK cleanup. Preserve Gradle Wrapper integrity including distribution checksum. Do not globally update Android SDK packages.
- For cleanup, prefer official SDK manager exact `--uninstall "package;version"` only after explicit user approval, a backup/rollback plan and checks of live project/AVDs. No `Remove-Item -Recurse` on SDK/AVD/JDK/Gradle directories and no `gradle cleanBuildCache` or daemon/cache purges as an automatic step.
- Run regression gates: `:app:assembleDebug`, existing unit tests, lint, release/bundle, native ABIs and connected instrumentation where explicitly approved; log PASS/FAIL/NOT RUN accurately. Validate device behavior separately from build.
- Prefer installed stable, tested versions over forced novelty. Record version/date of research and propose periodic re-audit rather than claiming perpetual latest.

## Sibling tools / hardware
- MCP Proxy config and global Copilot instructions are outside this repo; read but do not modify. Do not conflate ADB MCP with the ADB platform-tools binary.
- WSL2 is for genuine Linux diagnostics only. `usbipd` is Windows-managed; any USB ownership transfer requires user approval. A toolchain audit never changes physical device state.
