---
name: "TBS5530 Windows TSDuck CLI and MPEG-TS Diagnostics"
description: "Use TSDuck on Windows for BDA tuner discovery, DVB-T2 captures, MPEG-TS analysis, logging, and Windows versus Android USB-stream diagnostics."
applyTo: "tools/**,docs/project/**,drivers/src/**/tbs5530/**,app/src/debug/**,.github/instructions/**"
---

# TSDuck on Windows — TBS5530 project instructions

## Purpose, activation, and environment

- **TSDuck is already installed on Windows.** Use it when the task involves Windows BDA tuner discovery, DVB-T2 reception, offline/live MPEG-TS analysis, PSI/SI, service/PID mapping, TEI, continuity, PCR/PTS, transport bitrate validation, or a Windows reference capture for Android driver debugging. The repository-wide `.github/copilot-instructions.md` routes these tasks here even if no `applyTo` glob is involved.
- Windows PowerShell is the preferred shell. Discover installed commands and versions at runtime: `Get-Command tslsdvb,tsp,tsanalyze,tsbitrate -ErrorAction SilentlyContinue`, `tsp --version`, and the relevant command/plugin `--help` screens. Treat the installed executable's help as authoritative for command syntax; never assume an example works across versions without checking.
- Do not reinstall, upgrade, downgrade, modify PATH/Registry, swap BDA drivers, install WinUSB/Zadig, or modify USB ownership as a side effect of analysis. Do not run TBS5530 ChangeMode Tool automatically, change its current setting, configure satellite/LNB, or power an antenna through coax without a separate decision.
- **All TSDuck outputs belong under `D:\TSDuck` on the Windows host.** Use `D:\TSDuck\logs` for command logs, stdout/stderr, manifest and execution metadata; `D:\TSDuck\captures` for newly recorded `.ts`; `D:\TSDuck\reports` for JSON/TXT/CSV/statistics; and `D:\TSDuck\traces` for relevant USBPcap/TShark files. Do not move, rename, overwrite, or delete the user's existing files in `D:\TSDuck` (including files already in its root).
- Create missing subdirectories when needed; use a unique run ID based on timestamp plus a collision-safe suffix for every new artifact. Keep raw transport streams, USB traces, private device details, approval logs, and firmware binaries out of Git and out of third-party services. Only record summarized, redacted evidence in `docs/project/**`.
- Preserve PowerShell-native argument syntax: backslash (`\`) is **not** a PowerShell line-continuation character. Prefer single-line native commands, quoted paths, or proper argument arrays; capture stdout and stderr, `$LASTEXITCODE`, start/end timestamps, elapsed time, and actual input/output file sizes and SHA-256 hashes.

## TBS5530 BDA mode and device ownership

- The user's historical Windows test identified `TBS 5530 BDA Tuner` with VID:PID `734c:5530`. Before ChangeMode, BDA reported DVB-S/DVB-S2 and TSDuck rejected DVB-T2. The user switched TBS5530 ChangeMode to DVB-T/T2; subsequent BDA discovery reported DVB-T/DVB-T2 and Windows captured a terrestrial transport stream. This establishes the **Windows BDA delivery-system mode**, not persistence in EEPROM, physical RF routing changes, or the cause of Android `EOVERFLOW`.
- Before any live tuner operation, verify the tuner is attached to the **Windows host** and has the vendor BDA driver, no other application owns the BDA graph, and the passive coax antenna is connected to **RF IN**. Only one USB host can own this tuner at a time: Windows **or the Android device**. Ask the user to move the tuner or connect coax when required; do not attempt physical operations, WSL usbipd attachment, or a driver swap autonomously.
- Determine the actual tuner index and supported delivery systems using `tslsdvb` output; **do not assume adapter 0**. Use `tslsdvb --verbose --extended-info` for detailed topology or `tslsdvb --test bda-tuners` for BDA filter compatibility diagnostics when necessary. A generic BDA failure does not prove a fault in the tuner or its RF signal.
- Terrestrial test profile: DVB-T2, UHF K36, frequency `594000000` Hz, channel bandwidth `8000000` Hz, RF IN. Do not substitute 7.61–7.77 MHz occupied bandwidth for the 8 MHz tuning channel width. The on-air PLP ID is not known, and the BDA capture working without an explicit PLP does not establish a particular PLP ID.
- After moving the tuner to an Android device, independently re-evaluate its USB attachment, firmware state, USB permission, and operational descriptors. Never reuse Windows BDA state, a previous Android permission, or an old USB owner; do not automatically attribute Android `EOVERFLOW` to ChangeMode.

## Safe discovery and offline analysis

- For a discovery-only run, collect the executable version and BDA enumeration into unique files under `D:\TSDuck\logs`. Do not invoke `tsp -I dvb` if the tuner is attached elsewhere. `tslsdvb` may query the tuner or BDA graph; avoid conflicts with active streaming applications.
- Analyze existing TS samples **offline before starting a new hardware capture**, using the original file unchanged. Verify input existence, SHA-256 and size; identify TS packet size (typically 188 bytes, but do not silently truncate or reinterpret 192/204-byte formats); capture complete tool stdout/stderr and exit codes. A successful JSON parse does not imply a clean transport stream.
- Representative inspection commands (check actual version's help first):

```powershell
$root = 'D:\TSDuck'
'logs','captures','reports','traces' | ForEach-Object { New-Item -ItemType Directory -Force -Path (Join-Path $root $_) | Out-Null }
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
Get-Command tslsdvb,tsp,tsanalyze,tsbitrate -ErrorAction SilentlyContinue
& tsp --version
& tslsdvb --verbose --extended-info *> (Join-Path "$root\logs" "tslsdvb-$runId.txt")
& tslsdvb --test bda-tuners *> (Join-Path "$root\logs" "bda-tuners-$runId.txt")
```

- Example offline commands for an existing sample; run each only after verifying the command exists, directory access, and output name uniqueness:

```powershell
$root = 'D:\TSDuck'
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
$ts = 'D:\TSDuck\tbs5530-k36-long.ts'
if (-not (Test-Path -LiteralPath $ts)) { throw "Input TS not found: $ts" }
Get-FileHash -LiteralPath $ts -Algorithm SHA256
& tsanalyze --json $ts > (Join-Path "$root\reports" "k36-$runId.json")
& tsanalyze --error-analysis $ts > (Join-Path "$root\reports" "k36-errors-$runId.txt")
& tsanalyze --service-analysis --pid-analysis $ts > (Join-Path "$root\reports" "k36-services-pids-$runId.txt")
& tsbitrate --full $ts *> (Join-Path "$root\reports" "k36-bitrate-$runId.txt")
& tsbitrate --full --ignore-errors $ts *> (Join-Path "$root\reports" "k36-bitrate-ignore-$runId.txt")
```

- After **every** native command, record `$LASTEXITCODE` in a manifest and fail on an unexpected nonzero exit code; do not silently continue as if all reports passed. `tsbitrate` may fail due to insufficient or discontinuous PCR, even while another report contains a nominal bitrate value. Do not treat that value as observed RF throughput or USB bus throughput.
- Capture the run environment in a unique manifest: TSDuck version, Git HEAD, OS, actual adapter/BDA mode, VID/PID with identifiers redacted, sample name/hash/size, K36 configuration where applicable, process/exit status, time limits, input/output SHA-256, packet count, error counts and evidence classification. Never place real ADB credentials, USB serial numbers, private paths, permits, or raw firmware payload into committed records.

## Live capture: bounded and reproducible

- Run live DVB input only with the Windows BDA tuner ready, no competing DVB application, and the correct delivery system. Verify the installed version supports `tsp -I dvb --help` and `tsp -P until --help`; inspect all parameter names locally. Use the discovered adapter index instead of hardcoding `0`.
- Representative TSDuck capture pipeline for the verified K36 test, **not a safe unattended command by itself**:

```powershell
# Replace <verified-index> with the adapter actually discovered via tslsdvb.
tsp -I dvb --adapter <verified-index> --delivery-system DVB-T2 --frequency 594000000 --bandwidth 8000000 --signal-timeout 10 --receive-timeout 2000 -P until --seconds 15 --bytes 50000000 -O file "D:\TSDuck\captures\tbs5530-k36-<run-id>.ts"
```

- **A global wall-clock watchdog is mandatory.** The `until --seconds` limiter is triggered by received TS packets; it is insufficient by itself if no packets ever arrive. Implement an independent watchdog (for example, a bounded `System.Diagnostics.Process` runner), plus a maximum file size and capture duration, per-run stdout/stderr logs, process-tree-safe cleanup, and a manifest recording whether the stop was due to the plugin limit, wall-clock budget, cancellation, tuner failure or a transport error. Never execute the sample pipeline unattended without this wrapper.
- Keep a single capture bounded (for example, 15 seconds after first packets, 50 MB and a 45-second host deadline); these are **diagnostic defaults, not permission to start physical experiments without the required device/owner checks**. Do not auto-retry identical failing runs without a new hypothesis. The agent may automate a ready, validated BDA session without asking for each individual CLI step.
- Do not use an open-ended recorder as a background service, persist automatic capture across reboots, or use an external network stream as a data sink. Keep results locally under `D:\TSDuck`.

## Interpreting evidence and the Android EOVERFLOW investigation

- The earlier **10 MB Windows sample** reported 53,192 TS packets, 14 SDTV services, 46 PIDs, zero TEI and zero sync errors; its analysis also recorded continuity anomalies. The later **41,966,300-byte sample** reported 223,225 packets, 14 services, 46 PIDs, **5,101 TEI packets (about 2.285%)**, zero TS sync errors, unexpected continuity discontinuities, and multiple invalid PES reports. These outcomes describe different captures; neither makes Windows transport universally clean.
- `tsanalyze --error-analysis` summary entries are **not the number of corrupted packets**. Report TEI count/rate independently from continuity (expected/unexpected), duplicates, invalid PES and synchronization errors. Missing `BAT`, `TDT`, or `TOT` does not automatically imply a failed USB transport; distinguish optional SI from delivery/reception errors.
- The 41.97 MB sample also yielded implausible/unstable PCR-based bitrate estimates around 132–134 Mb/s while a `tsbitrate --full` invocation reported insufficient PCR. Do not use those estimates as the real 8 MHz multiplex bitrate or as Android USB buffer sizing targets. Check PCR/PTS discontinuities, wall-clock capture duration, and packet gaps before estimating any rate; label alternative estimates as estimates with assumptions.
- Investigate whether TEI and unexpected continuity losses are clustered across PIDs and timestamps or distributed evenly. Compare short and long TS files by hash, packet structure, service/PID identity, PCR/PTS timeline and error distribution. A Windows BDA capture proving TS reception does **not** by itself isolate RF versus BDA/USB host packet loss.
- Android has independently confirmed DVB-T2 lock on K36 but no received TS bytes: several Bulk IN `0x82` reads failed, with `errno=75 (EOVERFLOW)` observed using JNI on one run. TSDuck works above BDA and **does not expose raw URB contents**; successful Windows TS reception alone does not prove the Android root cause or the Windows URB queue structure.
- If packet-level USB evidence is justified, use installed Wireshark/TShark/USBPcap and `.github/instructions/usb-capture-windows.instructions.md`. Record bounded request/completion status, endpoint `0x82`, URB type, requested/actual length, timing and queue depth when available. USBPcap on Windows does **not** capture traffic between a tuner and the Android device. The official Linux TBS driver uses eight 4 KiB URBs, but do not assume the Windows BDA driver uses the same layout.
- Windows ChangeMode initially exposed DVB-S/S2 and later DVB-T/T2; it does not prove a persistent hardware mode or that ChangeMode caused/solved Android `EOVERFLOW`. Compare host USB speed, descriptor/alt-setting, firmware revision/initialization, endpoint scheduling and stream-select sequencing before favoring a hypothesis. Do not keep retrying Java 16 KiB/4 KiB, CLEAR_HALT or synchronous native 4 KiB after those modes have failed on hardware, unless new evidence meaningfully changes the experiment.

## Reporting and references

- Keep run metadata and raw logs under `D:\TSDuck`, and append only redacted conclusions with commands, source/runtime distinction, SHA-256, outcomes and limitations to `docs/project/`. Use `SOURCE-CONFIRMED`, `RUNTIME-OBSERVED`, `OFFLINE-TESTED`, `INFERRED`, `UNVERIFIED`, and `BLOCKED` accurately.
- Explain any meaningful optimization or code change with a before/after measurement and an explicit fallback. Do not call a 0-byte read `TS_SMOKE_PASS`, and do not claim that PAT/PMT availability implies fault-free capture.
- Do not treat a TSDuck CLI analysis as authorization for a new USB firmware write, ChangeMode operation, firmware/driver replacement, EEPROM write, LNB power change, or unbounded capture.
- Official references: https://tsduck.io/docs/tsduck.html ; https://tsduck.io/commands/ ; https://code.visualstudio.com/docs/agent-customization/custom-instructions ; https://kernel.org/doc/html/latest/driver-api/usb/error-codes.html .
