---
name: tbs-research
description: Research and verify TBS5530 firmware, bridge, CXD2878/CXD6802 frontend, USB FIFO/Bulk MPEG-TS and cross-host EOVERFLOW behavior by comparing official TBS, CoreELEC/Amlogic Linux, community C, Go, Windows and Android evidence.
---

# Research TBS5530 driver behavior across source and hosts

Use this skill for firmware/RAM loading, Cypress FX2, `0x8A` streaming, I2C, Sony CXD2878/CXD6802 + ASCOT3, PLP, DVB-T2 lock, USB `0x82`, native URB statuses, host/kernel diagnostics, or source updates.

## Step 1 — Establish the current baseline

Read `docs/project/STATUS.md`, `RESEARCH.md`, `DECISIONS.md`, `TEST-MATRIX.md`, `ENVIRONMENT.md` and `WORKLOG.md`, concentrating on the most recent dated entries. Determine branch HEAD, uncommitted changes, latest device/host tests and the first unresolved failure. Do not overwrite historical results or pretend old test statuses were rerun. Distinguish firmware/lock success from 0-byte TS reception.

## Step 2 — Identify and version all reference code

The editable repository is **only** `AndroidDvbDriver`. References, all read-only:

| Role                         | Checkout                               | Upstream                 | Files to start with                                                                         |
| ---------------------------- | -------------------------------------- | ------------------------ | ------------------------------------------------------------------------------------------- |
| Official hardware baseline   | `../references/tbs-linux_media`        | `tbsdtv/linux_media`     | `drivers/media/usb/dvb-usb/tbs5530.c`, `drivers/media/dvb-frontends/cxd2878.c`, `usb-urb.c` |
| CoreELEC ARM/Linux alternate | `../references/coreelec-linux-amlogic` | `CoreELEC/linux-amlogic` | same paths, `cxd2878_priv.h`, `Kconfig`, `Makefile`                                         |
| Community C                  | `../references/cxd28xx`                | `koreapyj/cxd28xx`       | board driver, demod/tuner helpers                                                           |
| Userspace Go                 | `../references/coax`                   | `Till0196/coax`          | `internal/coaxd/usb/bridge/tbs5530`                                                         |

Run `git -C <path> rev-parse HEAD` for each available checkout, and record an exact SHA for every claim. The current default CoreELEC branch was `5.15.196_20260306` at authoring time; verify it afresh. Use `.github/instructions/coreelec-driver-research.instructions.md` for this reference. If CoreELEC is not cloned, report `MISSING` and optionally run the opt-in sync script after checking user authorization. Never auto-sync during live USB experimentation.

## Step 3 — Build the differential evidence matrix

Compare **behavior**, not only source text:

1. Board match, USB descriptors/configuration/interface, firmware entry/hash, reset/release/re-enumeration, `no_reconnect` and failure handling.
2. Vendor requests `0x80`, `0x90`, `0x91`, `0x8A`; I2C address/bank/register writes and read-backs with context, timing and provenance.
3. Sony CXD6802 `0x197`, tuner init, TS output mode/polarity, PLP/L1 signaling and actual DVB-T2 lock semantics.
4. FX2 FIFO/stream routing, terrestrial `0x8A` payload, USB endpoint `0x82`, CLEAR_HALT/SET_INTERFACE, URB count, requested and actual lengths, cancellation, error status and kernel lifetime.
5. Windows BDA control sequence from true cold start, Linux driver sequence (WSL/VM), Android recorded sequence; align identity and epochs, mark missing capture windows and never infer a missing request was never sent.
6. CoreELEC's TBS5530 source configures **8 × 4096-byte Bulk URBs on `0x82`** and terrestrial `0x8A` payload `0A 00`; this is a source baseline, **not** proof of successful runtime in CoreELEC. Note differences such as frontend attach and cleanup and inspect source before copying anything.

Use one row per **meaningful transaction/state transition**, with columns: phase, driver/source SHA, call site, USB request or I2C address, expected effect, observed effect and confidence. Where register semantics are unknown, state so; do not guess.

## Step 4 — Research and rank hypotheses

Assign evidence level to each claim: `SOURCE-CONFIRMED`, `RUNTIME-OBSERVED`, `OFFLINE-TESTED`, `HYPOTHESIS`, `BLOCKED`, `UNKNOWN`. Distinguish `syscall=0/errno=0` from `urb.status=-75` or `-71` and `actual_length=0`. Two Android hosts sharing a failure do not prove the device is defective. A Windows BDA stream or CoreELEC driver source does not prove the Android init path is correct.

Find the **earliest provable divergence** between a working stream and Android. Prioritize investigations that can falsify more than one hypothesis (e.g., TS pin/FIFO routing, firmware state, USB request ordering, host/controller behavior, and physical power). Avoid blind PLP scanning, buffer or timeout increases, and repeated identical failing experiments.

## Step 5 — Test safely, then document

Source research and mock/offline tests are permitted; native hardware writes are not granted by this skill. A bounded, user-authorized autonomous session may cover previously verified firmware RAM upload and terrestrial/receive-only operations without per-transfer prompts; follow `../../instructions/autonomous-debug.instructions.md` and `../../instructions/usb-hardware.instructions.md`. USB ownership transfer, Windows driver mode changes, unreviewed registers, uncertain-firmware recovery and LNB power require their own explicit decision.

When source differences support a safe change: document the experiment and its failure/success conditions, implement only in the editable main repo, run regression, and record exact before/after URB and MPEG-TS evidence. If still blocked, choose WSL/Linux driver baseline, a conventional Linux VM USB pass-through, or bounded Windows tracing; do not automatically switch to Linux Live USB or purchase an analyzer.

Append verified URLs, commit SHAs, source file/functions, trace hashes and remaining uncertainties to `docs/project/RESEARCH.md`. Update `STATUS.md`, `DECISIONS.md` and `TEST-MATRIX.md` when decisions or test outcomes change. Keep user-facing docs Vietnamese and this skill English; preserve readable Markdown and do not commit raw captures/firmware.

## References

- Official: https://github.com/tbsdtv/linux_media/blob/latest/drivers/media/usb/dvb-usb/tbs5530.c
- CoreELEC TBS: https://github.com/CoreELEC/linux-amlogic/blob/5.15.196_20260306/drivers/media/usb/dvb-usb/tbs5530.c
- CoreELEC CXD2878: https://github.com/CoreELEC/linux-amlogic/blob/5.15.196_20260306/drivers/media/dvb-frontends/cxd2878.c
- CoreELEC introduction: https://github.com/CoreELEC/linux-amlogic/commit/e273c9180744304324e860f52c17c20477d5fff3
- Community C: https://github.com/koreapyj/cxd28xx
- Userspace Go: https://github.com/Till0196/coax/tree/main/internal/coaxd/usb/bridge/tbs5530
