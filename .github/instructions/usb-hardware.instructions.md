---
name: TBS5530 Hardware and USB Protocol
description: Use for TBS5530 USB firmware, Cypress FX2 bridge, I2C, CXD2878/Ascot3, M88RS6060, DVB frontend and MPEG-TS capture tasks.
---
# TBS5530-specific hardware rules
- Hardware inventory observed: `734c:5530`, interface 0, Bulk IN EP `0x82` and `0x81`, max packet 512 bytes; verified on WSL2 via lsusb/PyUSB. WSL2 interface claim succeeded. Actual tuner bring-up has NOT been verified.
- Compare official `tbsdtv/linux_media` first, then community `cxd28xx`, and `coax` userspace; capture file/functions and exact commit hashes. Where sources conflict, record alternatives and design a verification step.
- Official source has FX2 firmware `dvb-usb-id5530.fw`, vendor requests e.g. `0xA0`, `0x80`, `0x90`, `0x91`, `0x8A`. Source values are NOT permission to send requests to hardware.
- Prioritize DVB-T2; keep room for DVB-C/S/S2/S2X, but do not declare them implemented until tested.
- Distinguish USB enumeration from firmware state, I2C, frontend lock, and TS continuity; tests must state exact preconditions.
- Read-only enumeration and pure mocked tests are fine. Require specific user approval before any firmware upload, vendor/I2C writes, CPU reset, LNB power, `usbipd bind/attach/detach/unbind` or driver swaps.
- All USB ownership and usbipd commands are governed by `.github/instructions/usbipd-wsl.instructions.md`. Re-check live BUSID and do not run WSL2 and Windows DVB operations simultaneously.
- Observe GPL source licensing and check firmware redistribution conditions before bundling firmware in an APK or Git.
