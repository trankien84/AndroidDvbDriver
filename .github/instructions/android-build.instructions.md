---
name: Android Build and CLI
description: Use for configuring, building, testing or modernizing Android Gradle Plugin, Gradle, SDK/NDK/CMake, Java and emulator tooling.
applyTo: "**/*.gradle,**/*.gradle.kts,**/gradle.properties,**/settings.gradle,**/settings.gradle.kts,gradle/**,**/CMakeLists.txt,**/AndroidManifest.xml"
---
# Android build and command execution
- Windows PowerShell and the checked-in `.\gradlew.bat` are authoritative for the project. Check wrapper distributions/sha256 and plugin declarations before deciding an upgrade.
- Determine active `compileSdk`, `targetSdk`, `minSdk`, `buildToolsVersion`, `ndkVersion`, externalNativeBuild CMake version and Java `sourceCompatibility`/`targetCompatibility` from **current source**, not instructions or old snapshots.
- The expected SDK/AVD locations are `D:\Android\Sdk` and `D:\Android\AVD`; confirm local environment (`ANDROID_HOME`, `ANDROID_AVD_HOME`, `JAVA_HOME`) and `source.properties`. Reinitialize a child shell environment when needed, without silently changing machine-wide settings.
- `android` is Google's official Android CLI. Check `android --help`/subcommand help first and use Android CLI for supported operations. Use SDK `sdkmanager.bat --sdk_root=...` when exact installed packages or package-specific uninstall/install are necessary.
- Before any package change, run `tools/Inspect-TBS5530-Toolchain.ps1` and consult `toolchain-lifecycle.instructions.md`. Never update everything, never delete side-by-side packages based solely on their age, and never remove files manually from `Sdk` as a substitute for SDK package removal.
- Use stable releases verified against official vendor release notes and compatibility matrices. Distinguish **published latest stable** from **project-tested baseline**. Avoid dynamic Gradle versions or preview SDKs unless explicitly approved.
- Keep `minSdk 21` and Java 8 source/target compatibility unless explicit decisions justify changing them; JDK 25 as Gradle JVM does not imply Java 25 bytecode. Review AndroidX dependency minSdk constraints before bumping.
- `usbxfer` has JNI/CMake and native ABIs. A toolchain migration must verify four ABI builds, static 16 KiB alignment for 64-bit ABIs, release/debug artifacts, local units, lint, and targeted instrumentation/native-load tests where a compatible device is available.
- Do not automatically install new Android system images/AVDs, overwrite existing AVD data, force-stop hardware clients, or start a physical USB test to "validate" build tool changes.
