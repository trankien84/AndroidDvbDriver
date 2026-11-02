---
name: Android Build and CLI
description: Use for configuring, building, testing, or modernizing Android Gradle/AGP, Java, NDK/CMake, SDK or emulator tooling.
applyTo: "**/*.gradle,**/*.gradle.kts,**/gradle.properties,**/settings.gradle,**/settings.gradle.kts,gradle/**,**/CMakeLists.txt,**/AndroidManifest.xml"
---
# Android build and command execution
- Build on Windows PowerShell using checked-in `.\gradlew.bat` at the `AndroidDvbDriver` root; use `-Version` or Gradle wrapper to inspect versions before changing them.
- `android` is Google's official Android CLI. Use `android --help` and command-specific `--help` to confirm syntax. `android info sdk` and `android --sdk=D:\Android\Sdk sdk list` identify installed tools. Prefer Android CLI for SDK/AVD/device management when it has the needed operation; use Android SDK `adb.exe` and `sdkmanager.bat` only for operations not exposed or requiring exact commands.
- Expected `ANDROID_HOME=D:\Android\Sdk`; `ANDROID_AVD_HOME=D:\Android\AVD`. Do not install Android SDK into WSL2 or change global SDK settings without approval.
- Verified baseline (2026-11-02): JDK 25.0.5.0.3 runs Gradle Wrapper 9.8.2 and AGP 9.4.1. Keep Java source/target at 1.8 and minSdk 21; the build JVM version is not the Android bytecode level. AppCompat 1.7.1 and Material 1.13.0 are deliberate API-21-compatible pins.
- Build scripts select compileSdk 37.2, targetSdk 37, Build-Tools 37.0.0, NDK 30.0.16248370 and CMake 4.1.2. Older installed packages are not the active build baseline. Verify selected versions and runtime image separately; do not upgrade AGP/Gradle/dependencies or install SDK packages during runtime smoke testing.
- The agent process may inherit stale JAVA_HOME/SDK variables. Set JAVA_HOME from the verified Machine environment and ANDROID_HOME/ANDROID_AVD_HOME in the command process when needed; do not modify global environment settings or restart the IDE automatically.
- `usbxfer` contains native USB/CMake code; include ABI-specific native builds in validation. Emulator tests do not prove TBS5530 device integration.
- Prefer read-only environment probes before updates. Do not recreate the Android project with `android create`.
