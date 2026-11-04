---
name: Windows USB Capture Tooling — Wireshark and USBPcap
description: Apply when diagnosing TBS5530 firmware loading or USB control transfers using installed Wireshark, TShark, Dumpcap and USBPcap on Windows; capture planning and offline URB analysis.
applyTo: "docs/project/**,drivers/src/**/tbs5530/**,tools/**"
---
# TBS5530 Windows USB capture tooling

## Inventory: installed, but verify live state
- The user has **already installed Wireshark and USBPcap on the Windows development machine**. Do not suggest reinstalling either by default. Installed version, program path, USBPcap filter state, privileges and capture interfaces have **not** been independently verified in this session; discover them at runtime.
- Check read-only discovery first in **PowerShell**. Use `Get-Command wireshark,tshark,dumpcap -ErrorAction SilentlyContinue`, `Test-Path 'C:\Program Files\Wireshark\tshark.exe'`, program `-v` or `--version`, and a verified `tshark -D`/`dumpcap -D`. These only inspect configuration; do not equate enumeration with successful capture.
- Find USBPcap files/services/USBPcap interface names through approved read-only enumeration. Do not assume USBPcapCMD.exe is on PATH; verify executable paths before invoking. Do not guess or hardcode a `\\.\USBPcapN` capture device or root-hub index. Confirm current capture-interface ownership and TBS device presence first.
- Wireshark/TShark/Dumpcap command-line options are defined by the installed version and official docs. Consult `--help` or the relevant manual before invoking capture commands. If TShark/USBPcap discovery fails, report the missing link; do not install or adjust drivers yourself.

## Hardware ownership and approved observation
- USBPcap captures **host-side USB Request Blocks (URBs)**, not physical bus signaling, and cannot directly sniff USB traffic between the OnePlus and TBS5530 while the tuner is attached to the phone. Capturing TBS Windows BDA firmware traffic would require the tuner to be connected to the Windows host with its original driver available.
- Capturing a new hardware-init trace is not a harmless read of a file. Before moving the tuner, starting USBPcap capture, opening a vendor application, resetting/powering a device, or starting an experiment that triggers firmware writes, present the exact hardware change, scope, risks and rollback, and request explicit approval.
- Never run TBS ChangeMode Tool, replace BDA with WinUSB/Zadig, install/update/uninstall the USBPcap filter, restart filter services or reboot merely to enable capture without explicit approval. Do not attach the tuner to WSL and Windows at the same time. Never mix OnePlus and Windows as simultaneous USB hosts.
- If approved, identify the TBS5530 by current VID:PID `734c:5530`, current host controller, device address/interface and capture interface. Filter/scope the capture to the relevant device when feasible; avoid indiscriminately collecting traffic from other USB devices. Use bounded duration/size and stop after the specific question is answered.
- Do not assume a USBPcap capture contains the entire firmware transaction or accurately records pre-enumeration activity; inspect completeness, timestamps, setup/response directions, endpoint, request status, and whether the vendor driver actually initialized the device.

## Evidence and privacy
- Prefer analyzing existing `.pcapng/.pcap` files before requesting new hardware actions. Read captures offline with verified `tshark -r` / Wireshark when useful. Preserve original capture unchanged; produce a redacted structured summary of URBs relevant to `0xA0` requests and CPUCS addresses `0x7F92`/`0xE600`.
- Compare URB `bmRequestType`, `bRequest`, `wValue`, `wIndex`, data bytes/length, transfer status and timing with source-grounded Linux/FX2 hypotheses. A failed transfer or a missing request requires interpretation; do not conflate a captured URB with proof of firmware functionality on Android.
- Store raw captures outside Git and shared artifacts, as they may contain traffic from other USB devices, identifiers or firmware data. Do not upload raw `.pcapng`, binary firmware, local USB serials, pairing credentials or personal device information to the repo. Document only relevant redacted facts in `docs/project/RESEARCH.md` and acquisition steps/results in `WORKLOG.md`.
- If a new capture is not needed to distinguish technical hypotheses, use installed tools for read-only inspection or skip capture instead of performing unnecessary hardware work.

## Official references
- https://wiki.wireshark.org/CaptureSetup/USB
- https://www.wireshark.org/docs/man-pages/tshark.html
- https://www.wireshark.org/docs/man-pages/dumpcap.html
