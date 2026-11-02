---
name: tbs-research
description: Research TBS5530 hardware driver behavior by comparing official TBS Linux source, community C driver, and the Go userspace port; save verifiable findings in the project research log.
---
# Research a TBS5530 driver behavior

When investigating an API, firmware sequence, I2C transaction, tuner register, or MPEG-TS transport:

1. Read `docs/project/STATUS.md`, then `docs/project/RESEARCH.md` and relevant decisions. Identify current assumptions and missing evidence.
2. Record `git rev-parse HEAD` for each checkout under `../references/`; do not run full sync without user approval. Find the implementation in the official TBS `tbsdtv/linux_media` `latest` branch, recording a concrete commit SHA rather than only a moving branch URL.
3. Compare `koreapyj/cxd28xx`, then `Till0196/coax`. Cite exact file/function and note when code differs or is a refactor, not necessarily a bug fix.
4. Separate: (a) source-grounded fact, (b) observed hardware result, (c) hypothesis needing validation.
5. Do not issue live USB control writes, upload firmware, or change USB driver ownership automatically. Suggest a bounded test plan and seek explicit approval for state-changing operations.
6. Append a concise research entry to `docs/project/RESEARCH.md` with verified URLs, commit SHAs, alternatives, and next action. Update `STATUS.md` if the result changes the plan.
7. Tell the user what was verified and what remains untested.

Main references:
- https://github.com/tbsdtv/linux_media/blob/latest/drivers/media/usb/dvb-usb/tbs5530.c
- https://github.com/koreapyj/cxd28xx/blob/main/tbs5530.c
- https://github.com/Till0196/coax/tree/main/internal/coaxd/usb/bridge/tbs5530
