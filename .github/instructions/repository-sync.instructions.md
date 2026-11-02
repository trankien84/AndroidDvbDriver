---
name: TBS reference repository synchronization
description: Use when fetching, cloning, updating, repairing or documenting official/community TBS5530 source references or the repo sync PowerShell script.
applyTo: "tools/**/*.ps1"
---
# Repository sync policy
- Script is tracked inside the main repo: `tools/Sync-TBS5530-Repositories.ps1` (previous standalone tools script at workspace root is legacy).
- Windows host workspace parent: `D:\API\TBS5530-DVB-Android`; sibling references are `../references/tbs-linux_media`, `../references/cxd28xx`, and `../references/coax`.
- Use `.\tools\Sync-TBS5530-Repositories.ps1 -CheckOnly` for review: it fetches, but must not merge/rebase/checkout/clone. Full sync runs only with explicit user approval and outside active research/testing.
- Main `AndroidDvbDriver` repo: fetch only (do not merge, reset, rebase or checkout branch automatically). Three reference repos: only clean, tracking, linear fast-forward updates; skip dirty, detached, divergent or unexpected remote states.
- TBS Linux should preserve cone sparse-checkout for `drivers/media/usb/dvb-usb`, `drivers/media/dvb-frontends`, `drivers/media/dvb-core`, `drivers/media/tuners`, `include/media`, `include/uapi/linux/dvb`.
- Check any missing file with `git ls-tree` and index diagnostics before recovery. No `reset --hard`, forced clean, force push, destructive reclone or mutation of reference code.
- Before relying on upstream driver details, record reference SHAs with `git rev-parse HEAD`, and compare upstream changes rather than silently assuming latest is correct.
- Store logs in workspace-level `../logs`; do not commit logs or proprietary firmware.
