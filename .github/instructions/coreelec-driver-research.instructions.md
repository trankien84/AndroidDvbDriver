---
name: "TBS5530 CoreELEC Linux driver differential research"
description: "Use for comparing CoreELEC's TBS5530/CXD2878 kernel implementation to official TBS, other references, Windows traces, or the Android userspace port."
applyTo: "drivers/**/tbs5530/**,tools/Sync-TBS5530-Repositories.ps1,TBS5530.code-workspace,.github/skills/tbs-research/**,docs/project/**"
---
# CoreELEC TBS5530 reference — investigation guidance

## Identity and intent

- Upstream Git remote: `https://github.com/CoreELEC/linux-amlogic.git`; read-only checkout: `../references/coreelec-linux-amlogic` relative to the editable `AndroidDvbDriver` root. This is an Amlogic Linux kernel source tree; **do not use it as a working Android project**.
- The upstream default branch was `5.15.196_20260306` when this rule was authored, but always record the actual `git rev-parse HEAD`, tracked branch, upstream SHA and commit dates. Relevant upstream introduction commits: `e273c9180744304324e860f52c17c20477d5fff3` (`add support for tbs5530/5230`) and `e18888bf5e7fc8355ee4103e48f8536ea891850d` (`add support for sony cxd2878 family`), both source additions rather than verified end-user runtime success.
- CoreELEC source was checked with the branch `5.15.196_20260306` (commit `072f8e99e0ac66d88d7278784e922e08e8253210` at review time). Refresh SHAs on every research run. Do not assume the source equals the official TBS repository simply because some register sequences match.

## Minimum exact-file scope

- `drivers/media/usb/dvb-usb/tbs5530.c` and `tbs5530.h`: VID/PID, firmware loader, I2C bridge, frontend attach, stream selectors, DVB adapter mapping, endpoint, URB lengths and cleanup.
- `drivers/media/dvb-frontends/cxd2878.c`, `cxd2878.h`, `cxd2878_priv.h`: CXD6802 (`0x197`) detection, terrestrial initialisation, PLP, L1/status, parallel/serial TS configuration, unlock/lock and tuner control.
- `drivers/media/dvb-frontends/m88rs6060*`: satellite path only where it affects shared bridge/mode/initialization; never turn on satellite LNB output merely for terrestrial diagnostics.
- Relevant `Kconfig`, `Makefile`, dvb-usb framework/URB handling, DVB core and headers; follow source includes and symbol references. If a required file is absent due to sparse patterns, use `git ls-tree` to distinguish not checked out from not present upstream. Do not disable sparse checkout or clone the full kernel.

## Evidence-driven comparison workflow

1. Read current `docs/project/{STATUS,RESEARCH,DECISIONS,TEST-MATRIX,ENVIRONMENT,WORKLOG}.md` and existing USB evidence. Do not use historic unverified checkpoints as the current state.
2. Record SHAs for CoreELEC, official TBS, community C, Go, and the editable Android branch. Prefer `git show <sha>:<file>` for exact source provenance, not unpinned web snippets. For a missing local source, search exact upstream path and note the offline limitation.
3. Produce a **function-and-transaction diff**, not merely a text diff. Track caller, initial state, register bank/address, vendor request, payload, order/delay, error handling, control flow, and whether a register write happens before first Bulk IN. Compare firmware image hashes where evidence allows.
4. Explicitly check known CoreELEC source behavior: `tbs5530_ter_cable_streaming_ctrl` vendor `0x8A` with two-byte terrestrial payload `0A 00`; stream type Bulk, endpoint `0x82`, `count=8`, `buffersize=4096`. Identify actual semantic differences in Linux framework setup versus Android userspace USB ownership, endpoint initialization, URB submission and cancellation.
5. Cross-reference **Windows cold-start USBPcap/ETW and Linux usbmon** timelines where available. Align by attachment/firmware epoch and event type; never align arbitrary wall-clock and monotonic timestamps without calibration. Separate actual observed USB transactions from source-inferred ones.
6. Rank competing hypotheses for Android `-EOVERFLOW` and `-EPROTO` against evidence (TS routing/FIFO, frontend TS pins, host scheduling, USB protocol, power/cable and permission/lifecycle). Require an experiment whose possible outcomes distinguish hypotheses. No blind register replay, bulk buffer guessing, mode reset or endless retries.
7. Report a matrix: **official TBS / CoreELEC / community C / Go / Windows runtime / Linux runtime / Android runtime**. Label each cell `SOURCE-CONFIRMED`, `RUNTIME-OBSERVED`, `OFFLINE-TESTED`, `HYPOTHESIS`, `UNKNOWN` or `BLOCKED` with file, SHA, function and artifact pointer.
8. Append conclusions to project records without overwriting history. Runtime TS success on one CoreELEC box may support ARM Linux compatibility but does **not** imply success on stock Android or on every box.

## Workflow, safety and synchronization

- Only `AndroidDvbDriver` is editable. Use `.\tools\Sync-TBS5530-Repositories.ps1 -CheckOnly` to check all upstreams without checkout changes; it may fetch healthy repositories. Direct invocation performs one opt-in synchronization pass, including automatic backup-first recovery of eligible empty sparse clones. There are no per-repository selectors, recovery switches or watcher modes. Never run synchronization during an active hardware experiment or merely to validate a coding change.
- Keep raw firmware, `.ts`, usbmon/USBPcap captures, device identifiers and private logs out of Git. Place Windows forensic artifacts under `D:\TSDuck` and cite redacted hashes/provenance in `docs/project/`.
- No firmware upload, kernel module load, USB claim/host switch, Windows driver ChangeMode, LNB voltage or xHCI/USB modification is implied by reading CoreELEC source. Existing USB/hardware instructions govern any real execution.

## Upstream entry points

- https://github.com/CoreELEC/linux-amlogic/blob/5.15.196_20260306/drivers/media/usb/dvb-usb/tbs5530.c
- https://github.com/CoreELEC/linux-amlogic/blob/5.15.196_20260306/drivers/media/dvb-frontends/cxd2878.c
- https://github.com/CoreELEC/linux-amlogic/commit/e273c9180744304324e860f52c17c20477d5fff3
- https://github.com/CoreELEC/linux-amlogic/commit/e18888bf5e7fc8355ee4103e48f8536ea891850d
