---
name: TBS5530 Windows WSL2 USB Passthrough
description: Use for usbipd-win, USB/IP ownership, sharing or moving the TBS5530 between Windows and Ubuntu WSL2, and for lsusb or PyUSB passthrough diagnostics.
---
# TBS5530 USB ownership with usbipd-win

## Environment and observed evidence
- Windows is the USB host; WSL2 distribution is `Ubuntu-22.04`. `usbipd-win` was already installed and worked in a prior user-provided test. **Do not reinstall or upgrade it automatically.**
- Observed hardware USB VID:PID is `734c:5530` (TBS5530). Historic BUSID `6-2` is **not** a stable identifier: always query the current BUSID.
- Earlier user-provided evidence: Windows/WSL USB enumeration succeeded; `lsusb` showed the tuner; PyUSB read descriptors and successfully claimed/released interface 0. These are historic observations, NOT a fresh test in the current session.
- Firmware upload, I2C operations, DVB-T2 lock, MPEG-TS streaming and stock Android USB Host validation remain unverified unless separately documented.
- `usbipd` commands are executed on Windows, not in Ubuntu. After attachment, `lsusb` and PyUSB operate inside WSL2; no WSL-side usbipd client installation is necessary for this workflow.

## Hard safety boundaries
1. Read-only checks (`usbipd list`, `usbipd --version`, `wsl --list --verbose`, `lsusb`, source inspection) may be performed during an environment audit. USB enumeration confirms only device visibility.
2. **Before** `usbipd bind`, `attach`, `detach` or `unbind`, explain the planned state change and request explicit user approval. Do not execute these commands in Phase 0. The same approval rule applies to WSL restart, driver swaps, firmware transfers, vendor control writes and any command that changes hardware state.
3. Always identify the exact VID:PID and current BUSID from `usbipd list` before proposing any state-changing command. Never hardcode `6-2`.
4. Windows BDA, WSL2/PyUSB and a physically connected Android device must never be treated as simultaneous owners of the same tuner. Stop applications holding the tuner before a handoff.
5. `bind` shares a USB device persistently across Windows reboots and needs an elevated Windows shell. `attach --wsl` temporarily transfers it to WSL2; it can be run from a non-elevated Windows shell after binding. `detach` returns device access to Windows without undoing sharing. `unbind` removes sharing and is NOT a normal return-to-Windows step.
6. A USB unplug/replug or FX2 firmware reset can change BUSID or lose the WSL attachment. Re-evaluate state before suggesting recovery; never auto-reattach without approval.
7. Never imply that `Attached`, `/dev/bus/usb` or `lsusb` indicates a functioning Linux DVB driver. `/dev/dvb` may not exist in a stock WSL2 kernel.

## Read-only discovery (Windows PowerShell)
```powershell
Get-Command usbipd -ErrorAction SilentlyContinue
usbipd --version
usbipd list
wsl --list --verbose
```
- Select the entry matching `734c:5530` and TBS5530. Distinguish `Not shared`, `Shared`, and `Attached` states.
- To inspect WSL visibility without changing ownership, only when already attached:
```powershell
wsl.exe -d Ubuntu-22.04 -- lsusb -d 734c:5530
wsl.exe -d Ubuntu-22.04 -- lsusb -t
```

## Transfer commands (examples ONLY; explicit approval required)
From Windows PowerShell **as Administrator**, only if the target is `Not shared`:
```powershell
usbipd bind --busid <CURRENT_BUSID>
```
From ordinary Windows PowerShell, while a WSL2 distro session is running:
```powershell
usbipd attach --wsl --busid <CURRENT_BUSID>
usbipd list
```
On WSL2:
```bash
lsusb -d 734c:5530
lsusb -t
```
Return to Windows from ordinary PowerShell after closing clients using USB in WSL2:
```powershell
usbipd detach --busid <CURRENT_BUSID>
usbipd list
```
- The expected post-detach state is `Shared`, not `Not shared`.
- `usbipd unbind --busid <CURRENT_BUSID>` needs elevation and is only for an explicitly requested removal of sharing.

## Failures, reporting and documentation
- `lsusb` succeeds but `/dev/dvb` is absent: do not conflate missing DVB kernel modules with broken USB/IP.
- Failure to attach or a disappearing device: first check device listing and active clients; do not reset drivers, change firewall/OS settings, or force-rebind.
- For any approved state-changing operation record command, host/shell, current BUSID, before/after state, exact result, and test evidence in `docs/project/WORKLOG.md`; update `TEST-MATRIX.md` for actual tests. Never record personal device identifiers or secrets in a public repository.

## References
- Microsoft WSL USB instructions: https://learn.microsoft.com/windows/wsl/connect-usb
- usbipd-win WSL guide: https://github.com/dorssel/usbipd-win/wiki/WSL-support
