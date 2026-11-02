---
name: Android device automation via ADB MCP
description: Use for Android Emulator, AVD start-up, Android device UI inspection, screenshots, taps/swipes, app installation and Logcat testing in the TBS5530 port project.
---
# ADB MCP via existing gateway
- Copilot connects only to the user's existing MCP Proxy. `adb-mcp` is an upstream server managed in that Proxy, not a per-project direct VS Code MCP endpoint.
- Follow global `retrieve_tools` discovery and authorization policy when adb-mcp capabilities are needed. Never hardcode guessed proxy tool names or schemas. If unavailable, inspect status and use built-in `adb.exe` where appropriate, reporting the fallback.
- On Windows, Android SDK is `D:\Android\Sdk`; AVDs are under `D:\Android\AVD`. Do not install another SDK or edit proxy configuration as a side effect of testing.
- Use Android CLI for environment/device management as appropriate; use Gradle wrapper for build; adb-mcp for semantic UI inspection, screenshots, gestures, lifecycle and Logcat; direct ADB only when it is clearer or unavailable through MCP.
- List devices and choose an explicit serial; distinguish physical Android devices, Android TV emulator and mobile emulator. Do not assume a default device.
- Prefer UI hierarchy/semantic targeting to raw tap coordinates; capture before/after state and exact errors. Limit and sanitize logs. Do not disable lock screen/security or wipe user data without explicit approval.
- UI/emulator tests are not DVB hardware tests. Actual USB Host, firmware, tuner and MPEG-TS validation requires a physical Android device and approved hardware test plan.
- Update `docs/project/TEST-MATRIX.md` and `WORKLOG.md` after meaningful tests; never claim an unrun test passed.
