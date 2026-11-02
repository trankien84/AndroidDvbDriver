---
name: Android Build and CLI
description: Use for configuring, building, testing, or modernizing Android Gradle/AGP, Java, NDK/CMake, SDK or emulator tooling.
applyTo: "**/*.gradle,**/*.gradle.kts,**/gradle.properties,**/settings.gradle,**/settings.gradle.kts,gradle/**,**/CMakeLists.txt,**/AndroidManifest.xml"
---
# Android build and command execution
- Build on Windows PowerShell using checked-in `.\gradlew.bat` at the `AndroidDvbDriver` root; use `-Version` or Gradle wrapper to inspect versions before changing them.
- `android` is Google's official Android CLI. Use `android --help` and command-specific `--help` to confirm syntax. `android info sdk` and `android --sdk=D:\Android\Sdk sdk list` identify installed tools. Prefer Android CLI for SDK/AVD/device management when it has the needed operation; use Android SDK `adb.exe` and `sdkmanager.bat` only for operations not exposed or requiring exact commands.
- Expected `ANDROID_HOME=D:\Android\Sdk`; `ANDROID_AVD_HOME=D:\Android\AVD`. Do not install Android SDK into WSL2 or change global SDK settings without approval.
- JDK 25 LTS is installed but original Gradle/AGP may not support it. Verify version compatibility against official release tables; upgrade in reviewed small steps and keep source/target bytecode compatibility separate from Gradle JVM runtime.
- Existing installed snapshots: API 34 and 37.0 platforms; NDK 28.2.13676358; CMake 3.22.1; Build-Tools 36.0.0. Verify at runtime rather than asserting they remain current.
- `usbxfer` contains native USB/CMake code; include ABI-specific native builds in validation. Emulator tests do not prove TBS5530 device integration.
- Prefer read-only environment probes before updates. Do not recreate the Android project with `android create`.
