# Environment inventory

## 2026-11-02 — Phase 1.2: kiểm kê sau dọn SDK và nâng riêng AGP

**Nguồn:** Windows PowerShell 7.6.9 / Windows 10.0.19045; nhánh `chore/modernize-android-build`, HEAD nền `ace7e9f187d11a685975f707b6743d779fa847d9`. Có sáu thay đổi local do người dùng chuẩn bị trước phase (hai instructions sửa, instruction/skill kiểm kê mới và hai script mới); không reset hoặc commit chúng.

### Packages thực còn lại

`Inspect-TBS5530-Toolchain.ps1`, Android CLI `sdk list` và `source.properties` được đối chiếu, không suy từ inventory cũ:

| Package | Phiên bản/revision local | Vai trò |
|---|---|---|
| platforms/android-34 | revision 3 | Còn trên máy; không chọn để compile hiện tại |
| platforms/android-37.2 | revision 2, API 37.2, PreviewSdkInt 0 | Compile SDK dự án |
| build-tools/37.0.0 | 37.0.0 | Pin dự án |
| ndk/30.0.16248370 | r30 | Pin dự án |
| cmake/4.1.2 | 4.1.2 | Pin dự án |
| cmdline-tools/latest | 23.0 | SDK command-line tools |
| platform-tools | ADB 37.0.1 | CLI device access |
| emulator | 37.2.13 | Emulator binary |
| system-images/android-34/google-tv/x86 | metadata revision 3 | Giữ nguyên; không dùng trong smoke này |
| system-images/android-37.0/google_apis/x86_64 | metadata revision 7 | Image của AVD đã có; CLI catalog hiện in revision 8, không coi đó là package đã cập nhật |

Không còn platform37.0, Build-Tools36, NDK28 hoặc CMake3.22.1 trong inventory local. Image37.0 vẫn còn và độc lập với SDK platform37.0. Không uninstall/cài/update/xóa AVD hoặc SDK trong phase này. Review cleanup chỉ tạo CSV, platform34 là REVIEW chứ không tự kết luận vô dụng cho dự án khác.

### JDK và AVD discovery

- Process agent: JAVA_HOME cũ `C:\Program Files\Java\jdk-25` không tồn tại; User JAVA_HOME trống; Machine đúng `C:\Program Files\Java\latest\jdk-25`. Build commands nạp Machine JAVA_HOME vào child PowerShell, Gradle xác nhận Launcher25.0.5.0.3/daemon resolvedjdk25.0.5.0.3. Không sửa User/Machine environment hoặc restart VS Code.
- ANDROID_HOME/ANDROID_AVD_HOME process thiếu, User đúng D:\Android\Sdk / D:\Android\AVD. Audit resolve được User AVD home nhưng trước sửa **không truyền home này vào emulator subprocess**, nên emulator list rỗng giả. Thư mục `.avd` dùng DirectoryInfo.BaseName còn nguyên suffix, gây thêm tên giả `TBS5530_Runtime_API37.avd` bên cạnh tên đăng ký thật.
- Đã sửa audit: truyền AVD home chỉ trong lúc chạy listing, khôi phục environment ban đầu trong finally; bỏ suffix `.avd` đúng cho tên directory; hợp nhất metadata/emulator. Kết quả thật: **một AVD `TBS5530_Runtime_API37`, emulator=1, metadata=1**. Có regression cho cả variable vắng/có sẵn và emulator không tồn tại. Không tự boot AVD từ audit.
- Audit JSON bổ sung các JAVA_HOME candidates hợp lệ và executable build gợi ý; vẫn báo rõ process stale, **không tự đổi JAVA_HOME**. `scripts/check-env.ps1` chuyển thành entrypoint gọi audit chung để tránh hai cách kiểm kê lệch nhau. `-NoReport -PassThru` hỗ trợ assertions/automation.
- Cleanup-review parser CMake được giới hạn trong externalNativeBuild.cmake; không nhầm buildToolsVersion là CMake. Tất cả báo cáo local ở parent logs/toolchain, không commit machine/host metadata.

### Execution policy

Lần đầu chạy script bị RemoteSigned chặn. Đã hỏi và người dùng **chỉ định rõ** `Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process`. Thực hiện đúng scope Process trong từng child, lưu policy cũ và restore trong finally; không `Unblock-File` hoặc đổi CurrentUser/LocalMachine. Kết quả sau lệnh Process=Undefined, User/Machine vẫn RemoteSigned. Không biến việc bỏ chặn được duyệt lần này thành mặc định tự bypass trong tooling.

### Phân biệt bốn trạng thái phiên bản

| Thành phần | Declared | Installed | Published stable đã tra | Project verified / hành động |
|---|---|---|---|---|
| AGP | 9.5.0 sau sửa root plugins | Resolve qua Gradle cache/build, không phải SDK package | Google Current Release 9.5.0 ngày kiểm 2026-11-02 | Chỉ nâng 9.4.1→9.5.0; kết quả gate cuối ở TEST-MATRIX |
| Gradle/JDK | Wrapper9.8.2 / JVM25 | 9.8.2 / 25.0.5.0.3 | Không tái tìm mọi latest JDK/Gradle; ma trận9.8.2 xác nhận JVM25 được hỗ trợ | Giữ nguyên, wrapper checksum không đổi |
| SDK/Build-Tools | 37.2 /37.0.0 | Đúng pins | Không có yêu cầu tìm/nâng toàn SDK | Giữ nguyên compile37.2/target37/min21 |
| NDK/CMake |30.0.16248370 /4.1.2 | Đúng pins | Không dùng tuổi package để quyết định nâng/xóa | Giữ nguyên, kiểm ABI/native gates |
| Android runtime | Không quyết định bởi compileSDK | AVDimage37.0 x86_64 | Không tra/tải image mới | API37/4KiB chỉ sau getprop/boot thật; không đại diện arm64/16KiB/API21 |

Google AGP9.5 compatibility: Gradle min9.6.0, JDK min17, API max37; default NDK28.2/BT36 **không phải lệnh bắt buộc downgrade pins**. Gradle9.8.2 hỗ trợ JVM25 từ dòng9.1.0; vẫn cần thực thi regression của dự án. Nguồn chính thức được ghi trong WORKLOG/DECISIONS; versions không được coi là mãi latest.

## 2026-11-02 — Phase1.1 runtime thực trên emulator, không tuner

**Host/cwd:** Windows10.0.19045, PowerShell7.6.9, AndroidDvbDriver root. Baseline commit `6da609ba49dd9f1f4d284291918f68a6378972ff` đã push theo người dùng, local origin-tracking ref cùng SHA. Nhánh `test/android-runtime-smoke` được tạo từ modernization, bảo toàn2local changes `.gitignore`/workspace.

### Environment build giữ nguyên

- Process agent còn JAVA_HOME cũ `C:\Program Files\Java\jdk-25` không tồn tại và thiếu ANDROID_HOME/ANDROID_AVD_HOME; Machine JAVA_HOME đúng `C:\Program Files\Java\latest\jdk-25`, User SDK/AVD đúng D:\Android. Mỗi PowerShell command build/emulator đặt env **trong process đó**, không sửa global/restartVSCode.
- `gradlew.bat --version` xác nhận Gradle9.8.2, LauncherJVM25.0.5.0.3, DaemonJVMresolvedjdk25.0.5.0.3. Build scripts plugin9.4.1, compile37.2/target37/min21; NDK30.0.16248370/CMake4.1.2/BT37.0.0 source.properties xác nhận. Không nâng versions/dependencies.
- Android CLI `--help/info sdk/sdk list`, ADB37.0.1 và Emulator37.2.13 `-version` chạy được. Androiddocs search/fetch dùng KB hiện có cho instrumentation/insets; không càiSDK/image.

### AVD thử nghiệm đã được phê duyệt

| Thuộc tính | Kết quả thực |
|---|---|
| AVD |`TBS5530_Runtime_API37`, tạo riêng qua avdmanager từ image đã cài |
| Đường dẫn |`D:\Android\AVD\TBS5530_Runtime_API37.avd`; không chạm AVD khác |
| Image |`system-images;android-37.0;google_apis;x86_64`; source.properties revision7 tại kiểm tra (catalog CLI trước đó báo8, ưu tiên metadata local và runtime) |
| Profile |`medium_phone`; không cấu hình lại OS/hypervisor |
| Emulator runtime |Android17, SDK37, ABIx86_64, serial thử nghiệm emulator-5554 |
| Page size |`adb ... shell getconf PAGE_SIZE` =4096, **không phải16KiB** |
| USB Host |pm list features không có usb.host; không có tuner chuyển vào emulator |
| Startup flags |`-avd TBS5530_Runtime_API37 -port 5554 -no-snapshot -no-boot-anim -no-audio -no-window -gpu swiftshader_indirect` |
| UI automation |ADB MCP qua Proxy: list_avds/describe_ui/screenshot/tap; serial luôn explicit; direct ADB cho install/am/dumpsys/logcat và Gradle instrumentation |

Ban đầu CLI/ADB/MCP đều không thấy AVD/device; sau user approval tạo AVD bằng avdmanager vì AndroidCLIcreate chỉ nhận profile, không có chọn chính xác image/path/name. `android emulator list` không in mục ở lần đầu nhưng emulator -list-avds và MCP sau đó thấy tên; không giả định output rỗng là image thiếu. Không download system image mới. AVDdata dùng mặc định profile, không wipe; sau test gửi `adb -s emulator-5554 emu kill` chỉ đúng AVD này, không xóa file/dữ liệu.

**SystemUI caveat:** firstboot có hộp `System UI isn't responding`; screenshot/hierarchy xác định package com.android.systemui, chọn Wait, overlay biến mất và app test tiếp. Không gộp lỗi này thành app ANR hoặc bỏ qua trong báo cáo. Runtime app no-tuner không yêu cầu storage permissions; dumpsyspackage READ/WRITE_EXTERNAL_STORAGE=false nhưng Welcome/Diagnostic/chooser vẫn chạy; foregroundpermissions khai báo/granted, DvbService chưa active.

**Evidence ngoài Git:** session files/phase-1-1 chứa before/after screenshots, no-tuner screenshot, instrumentation-results.xml và app-logcat-filtered.txt. Không commit raw Logcat/system UI/AVD state. APKdebug mới SHA2568B4E47A9E60D6153CC3B97F244C45771AD0108BA0C1CA785F72332DA31FE38F6; testAPK F85C7D2ADE771570018623D4BD9163AB358D9B4A2530697FEA2F8D484FAB9872.

## 2026-11-02 — Toolchain migration thực trên JDK25

**Host:** Windows10.0.19045 / PowerShell7.6.9; nhánh `chore/modernize-android-build`, HEAD nền `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`, thay đổi chưa commit. Người dùng cho nâng stable mới nhất nhưng xác nhận giữ minSdk21 và references read-only.

| Thành phần | Phiên bản cấu hình/kiểm chứng mới |
|---|---|
| JDK chạy wrapper/daemon/compiler | Oracle25.0.5.0.3; JAVA_HOME Machine hợp lệ `C:\Program Files\Java\latest\jdk-25`, resolved daemon `jdk-25.0.5.0.3` |
| Gradle |9.8.2; wrapper --version PASS trên JVM25; regenerated scripts/JAR bằng wrapper task chính thức |
| AGP |9.4.1, plugin resolve/configure và debug build đã chạy |
| SDK |compile37.2 (`minorApiLevel=2`), target37, min21 |
| Build-Tools |37.0.0, đọc source.properties xác nhận |
| NDK |30.0.16248370 / r30; meta/platforms.json min21/max37; build debug4ABIs đã chạy |
| CMake |4.1.2 từ Android SDK; CMakeLists minimum3.22.1 để không dùng policy cũ bị CMake4 bỏ |
| Java source/target |1.8 (không25); javac25 báo obsolete warning, chưa ẩn warning |
| AppCompat / Material |1.7.1 /1.13.0: giữ API21; latest1.8.0/1.14.0 yêu cầu23 nên không chọn |
| Unit tests |JUnit4.13.2, Mockito5.24.0; bỏ Mockito1/mockito-all/PowerMock |
| Instrumentation dependencies |AndroidX test-ext1.3.0 / Espresso3.7.0, latest stable còn API21 |
| Broadcast dependency |localbroadcastmanager1.1.0 khai báo trực tiếp trong dvbservice thay dựa transitive |

Lệnh nào chạy Gradle cũng set JAVA_HOME từ Machine và ANDROID_HOME=D:\Android\Sdk/ANDROID_AVD_HOME=D:\Android\AVD **trong process đó**, không thay registry/global PATH. VS Code task assembleDebug cập nhật cùng cơ chế; runTask không tìm thấy task trong host nên terminal chạy cùng command.

**Cài package có giới hạn:** sau manifest build thay đổi và validation `gradlew help` FAIL vì NDK chưa cấu hình, chạy `android --sdk=D:\Android\Sdk sdk install <package>` cho đúng4package: ndk/30.0.16248370, cmake/4.1.2, build-tools/37.0.0, platforms/android-37.2; tất cả exit0 và source.properties được đọc lại. Không update toàn SDK, không tải emulator/system images/firmware, không tạo SDK thứ hai. `android.builder.sdkDownload=false` ngăn build tự cài SDK khác.

**Wrapper integrity:** distributionSha256Sum9.8.2=`21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`, lấy từ official downloads.gradle.org. Cài binary-only distribution thay vì all. Không chép JDK path cá nhân vào build scripts. Package37.2 có metadata Platform.Version placeholder `${PLATFORM_VERSION}` nhưng AndroidVersion.ApiLevel37.2/PreviewSdkInt0; compile dùng được, không suy diễn tên Android từ placeholder.

**Không đổi hardware/reference ownership:** không chạy script Apply khôi phục TBS, không tải572 ancestor objects. C/Go vẫn sạch, SHA cả3refs giữ nguyên, TBS vẫn81399D. Mọi USB/lock/TS thật và runtime Android/AVD vẫn NOT RUN. Kết quả build/test cuối xem TEST-MATRIX/WORKLOG, không dùng snapshot Phase0 bên dưới làm hiện trạng mới.

## 2026-11-02 12:52 +07 — Kiểm tra lại JAVA_HOME sau khi người dùng sửa

Windows PowerShell / main HEAD `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`.

- **Machine JAVA_HOME đã đúng:** `C:\Program Files\Java\latest\jdk-25`; `Test-Path` thư mục và `bin\java.exe` đều true; chạy executable này trả Java `25.0.5.0.3`, exit0.
- **Process agent vẫn giữ giá trị cũ:** `C:\Program Files\Java\jdk-25`, không tồn tại; User JAVA_HOME không có. Đây là môi trường được tiến trình cha truyền lại, không phải người dùng sửa thất bại.
- Đã kiểm tra trong một PowerShell child bằng `$env:JAVA_HOME=[Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')`, rồi chạy `bin\java.exe -version`: PASS. Thay đổi chỉ tồn tại trong child đó, không sửa User/Machine hoặc tiến trình VS Code đang chạy.
- Các lệnh sau nếu cần JDK này phải đọc lại Machine JAVA_HOME trong chính process chạy lệnh; hoặc người dùng khởi động lại VS Code để nhận environment mới. **Không tự restart VS Code hoặc thay global settings.**
- ANDROID_HOME/ANDROID_AVD_HOME vẫn thiếu Process nhưng đúng ở User (`D:\Android\Sdk`, `D:\Android\AVD`). Chưa đổi biến.
- **Giới hạn vẫn còn:** JDK25 hợp lệ không làm Gradle8.7 tương thích. Chưa chạy wrapper/build; đề xuất baseline JDK17 vẫn giữ nguyên.

## 2026-11-02 — Kiểm chứng trực tiếp trong Phase 0

**Môi trường chạy lệnh:** Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`; thời gian local UTC+07. HEAD Android `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`. Không chạy Gradle, cài SDK, khởi động AVD hoặc thao tác ghi USB.

### Windows, Java và biến môi trường

| Hạng mục | Kết quả thực đo | Ý nghĩa |
|---|---|---|
| Windows | `10.0.19045.0`; ADB cũng báo Windows 10.0.19045 | Xác nhận host, không đánh giá vòng đời hỗ trợ Windows |
| PowerShell | `7.6.9` | Shell audit chính |
| Git | `2.56.0.windows.3` | Không chạy sync script |
| Java trên PATH | Oracle `25.0.5.0.3`, build `25.0.5.0.3+1-LTS-2` | `java -version` chạy được |
| `JAVA_HOME`, Process/Machine | `C:\Program Files\Java\jdk-25`, **không tồn tại** | Chặn Gradle Wrapper trước cả vấn đề tương thích phiên bản |
| JDK thực có | `C:\Program Files\Java\jdk-25.0.5.0.3\bin\java.exe` | Chưa thay `JAVA_HOME` |
| `ANDROID_HOME`, Process | Không có | Tiến trình agent chưa nhận giá trị User |
| `ANDROID_HOME`, User | `D:\Android\Sdk` | Không tự sửa biến global |
| `ANDROID_AVD_HOME`, Process | Không có | MCP/CLI có thể nhìn nhầm thư mục AVD |
| `ANDROID_AVD_HOME`, User | `D:\Android\AVD` | Thư mục tồn tại, hiện rỗng |
| Android CLI | `1.1.16717390`, tìm được qua WinGet link | `--help`, `-V`, `info sdk`, `sdk list`, `docs search/fetch` chạy được |
| `android info sdk` | `C:\Users\anhch\AppData\Local\Android\Sdk` | Default của process không trùng SDK dự kiến; chưa kết luận có SDK thứ hai đã cài |
| `android --sdk=D:\Android\Sdk info sdk` | `D:\Android\Sdk` | Truy vấn rõ SDK hoạt động; không đổi cấu hình |

Lệnh kiểm chứng: `Get-Command java,android,git,usbipd,wsl.exe`, `[Environment]::GetEnvironmentVariable(<name>,<Process/User/Machine>)`, `Test-Path $env:JAVA_HOME`, `Get-ChildItem 'C:\Program Files\Java'`, `java -version`. Đã phân biệt Java tìm qua PATH với `JAVA_HOME` mà wrapper sử dụng.

### SDK tại D:\Android\Sdk

Lệnh `android --sdk=D:\Android\Sdk sdk list` trả exit 0. Đọc thêm `source.properties` để không chỉ dựa vào tên thư mục.

| Package | Phiên bản xác nhận |
|---|---|
| `platforms/android-34` | revision 3; API 34, Android 14 |
| `platforms/android-37.0` | revision 2; metadata API `37.0`, Android 17, `PreviewSdkInt=0` |
| `build-tools/36.0.0` | 36.0.0 |
| `ndk/28.2.13676358` | r28c, 28.2.13676358 |
| `cmake/3.22.1` | 3.22.1 |
| `cmdline-tools/latest` | 23.0.0 |
| `platform-tools` | 37.0.1; `adb.exe --version`: 1.0.41 / 37.0.1-15733141 |
| `emulator` | 37.2.13.0, build 16740729; `emulator.exe -version` |
| System images | API 34 Google TV x86 revision 3; API 37.0 Google APIs x86_64 revision 8 |
| AVD | `D:\Android\AVD` rỗng; default `.android\avd` không tồn tại; MCP `list_avds`: không có AVD |

Có system image **không** có nghĩa đã tạo/boot AVD. Lần thăm dò `emulator.exe --version` in phiên bản kèm lỗi `No AVD specified`; đã sửa thành truy vấn `-version`, không truyền tên AVD, không khởi chạy máy ảo. Android CLI in cảnh báo `UseAllWindowsProcessorGroups` không hỗ trợ trên Windows này nhưng các truy vấn trên vẫn hoàn tất. `android docs search` tự tải bộ chỉ mục Knowledge Base/cache; đây không phải cài/nâng SDK, không gửi mã dự án.

### WSL2, usbipd và quyền sở hữu USB

- `wsl.exe --list --verbose`: `Ubuntu-22.04`, **Running**, version **2**; không chạy lệnh bên trong WSL vì TBS không Attached.
- `usbipd` không có trên PATH của process; đã dùng executable hiện có `C:\Program Files\usbipd-win\usbipd.exe`, không cài lại hoặc sửa PATH.
- `usbipd.exe --version`: `5.3.0-54+Branch.master`, commit `aa3db8b82c4cb5071fd31bc54211606c70886912`.
- `usbipd.exe list`: `734c:5530`, `TBS 5530 DVB BDA`, BUSID **6-2 tại thời điểm kiểm tra**, trạng thái **Shared**, không phải Attached. BUSID được đọc trực tiếp, không lấy từ lịch sử.
- Quyền truy cập ở phía Windows, không chuyển cho WSL2; chưa xác minh ứng dụng Windows nào đang mở thiết bị. `Shared` không chứng minh BDA/tuner đang hoạt động.
- `bind`: chia sẻ bền vững (Administrator); `attach --wsl`: giao tạm cho WSL; `detach`: trả Windows nhưng giữ Shared; `unbind`: bỏ chia sẻ. **Không thực hiện bốn lệnh này**, USB reset, firmware, vendor/I2C write hoặc LNB.
- Kernel WSL và endpoint/claim PyUSB trong phần 2026-11-01 bên dưới là **bằng chứng lịch sử**, chưa đo lại.

### Công cụ Copilot/MCP

- Đã đọc `.github/copilot-instructions.md`, cả 6 scoped instructions, 2 SKILL.md, 6 hồ sơ dự án, workspace, script sync và script check-env (không chạy hai script).
- Hai project skills `project-handoff`, `tbs-research` và skill user `android-cli` được tool `skill` tải thành công. Instructions áp dụng docs/build/sync xuất hiện trong context phiên; cả 6 file đã được chủ động đọc. Không có file yêu cầu nào bị thiếu. Không có API kiểm chứng việc VS Code tự động ghép *mọi* scoped instruction ở mỗi lượt; không tuyên bố auto-attach đã được kiểm thử toàn diện.
- Chỉ qua MCP Proxy: `retrieve_tools` + `call_tool_read` hoạt động. `adb-mcp:list_avds` trả không có AVD; chưa kiểm thử UI, APK, Logcat hoặc thiết bị Android vật lý.
- `gitnexus:list_repos` hoạt động nhưng **không có chỉ mục AndroidDvbDriver hoặc ba reference**. Không rebuild index; nghiên cứu dùng source trực tiếp.
- `agentmemory:memory_recall`, query `TBS5530 AndroidDvbDriver`, compact/limit 3: `matched_count=0`; không suy ra server rỗng hoặc tìm kiếm đã cô lập project. Không lưu memory mới.
- Truy vấn lịch sử phiên 7 ngày theo identity GitHub dự án không trả dòng nào. Tài liệu trên đĩa là nguồn lịch sử chính.
- Chưa kiểm tra `chrome-devtools`: không cần browser/UI cho audit source. Không sửa MCP config hoặc global settings.

## Historical inventory — observed 2026-11-01 (giữ nguyên)

Phần dưới là ghi chép người dùng cung cấp trước Phase 0, không thay cho các kết quả kiểm chứng trực tiếp ở trên.

## Windows / Android

| Item | Observed |
|---|---|
| Windows host | Windows 10 `10.0.19045` (per ADB output; verify this remains current) |
| Primary project root | `D:\API\TBS5530-DVB-Android\AndroidDvbDriver` (planned; check actual clone) |
| SDK root | `D:\Android\Sdk` |
| Android CLI | Official Google `android` CLI; version NOT captured (run `android -V`) |
| Java | Oracle JDK `25.0.5` LTS; `JAVA_HOME=C:\Program Files\Java\jdk-25` |
| Build-Tools | `36.0.0` |
| SDK platforms | `android-34`, `android-37.0` |
| NDK | `28.2.13676358` |
| CMake | `3.22.1` |
| Platform-Tools / ADB | `37.0.1` |
| cmdline-tools | `23.0.0` (`latest`) |
| Emulator | `37.2.13`; WHPX reported installed and usable; no actual AVD boot demonstrated |
| Emulator images | `android-34/google-tv/x86`; `android-37.0/google_apis/x86_64` |

Use `android --sdk=D:\Android\Sdk sdk list` for inventory; `android info sdk` and `android -V` for active SDK and CLI version. Do not assume packages are the latest based solely on folder names. SDK updates require approval.

## WSL2 / USB

| Item | Observed |
|---|---|
| Distro | `Ubuntu-22.04`, WSL2 |
| Kernel | `6.18.54.1-microsoft-standard-WSL2` |
| USB/IP | `usbipd-win`, TBS device BUSID `6-2` during test; BUSID may change |
| USB ID | `734c:5530` |
| Configuration / Interface | `1` / `0` |
| Bulk input endpoints | `0x82`, `0x81`; each 512-byte max packet size |
| Speed | USB 2.0 High Speed / 480 Mbps |
| USB enumeration / interface claim | PASS on WSL2/PyUSB; no DVB driver attached |
| `/dev/dvb` | Not present during WSL test, expected without DVB driver |

The same project files are available via `/mnt/d/API/TBS5530-DVB-Android/AndroidDvbDriver`. The reference checkouts under sibling `references/` should remain read-only.

## Tool boundaries

- Windows PowerShell: Android builds with `gradlew.bat`, Android CLI, ADB, Git.
- WSL2: `lsusb`, PyUSB, Linux-only diagnostics. Invoke from Windows with `wsl.exe -d Ubuntu-22.04 -- ...`.
- No second Android SDK inside WSL2. No Linux kernel driver/firmware flash required for initial Android build.
- Active TBS5530 USB passthrough prevents simultaneous use with Windows' BDA driver.

## Baseline project (source inspection only, not local build verification)

Original upstream AndroidDvbDriver uses AGP `8.5.2`, Gradle wrapper `8.7`, `compileSdk 34`; JDK 25 must not be assumed compatible with this old wrapper. Modernization is a separate tracked step after source baseline and dependency audit. Never claim `assembleDebug` passes without executing it locally.
