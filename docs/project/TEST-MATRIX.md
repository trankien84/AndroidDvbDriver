# TBS5530 test matrix

## 2026-11-02 — Phase1.1 Android runtime, 19:31–20:05 +07

Branch `test/android-runtime-smoke`, HEAD6da609ba49dd9f1f4d284291918f68a6378972ff từ modernization đã push. Runtime AVDTBS5530_Runtime_API37/Android17/API37/x86_64/page4096; không tuner. WindowsPowerShell/SDKD:\Android\Sdk; toolchain không nâng.

| Gate | Kết quả | Bằng chứng thực |
|---|---|---|
| Giữ Git/local changes khi tạo branch | PASS |2filelocal `.gitignore`/workspace giữ hash; branchmới cùng6da609b, không stash/reset/commit |
| Environment build versions | PASS có lưu ý |wrapper--versionJDK25/Gradle9.8.2; scriptsAGP9.4.1/API37.2/NDK30/CMake4.1.2/BT37; processenvnạp riêng |
| AVD/image có sẵn | PASS |TạoAVDriêngsauapproval; dùng37.0x86_64local, không tảiSDK/image |
| Emulator firstboot/SystemUI | FAIL ban đầu, hồi phục |ANRcom.android.systemui overlay; chọnWait, hết overlay. Không quy cho app |
| Cài APKdebug | PASS |adb explicitserialinstall-r Success cả baseline và bảnfix |
| Cold launchWelcome | PASS |amstart-WStatusok/COLD,TotalTime6606ms; PID3840sống, không appfatal |
| UI system bars trước fix | FAIL |Screenshot/hierarchy hàngtune y0,Closeđến2400 bị systembarsche |
| UI system bars sau fix | PASS runtime37 |SystemBarInsetsAPI35+; tunetop63,buttonsmaxbottom2337 so screen2400; statusiconsdarkđọcđược |
| Welcome/License/OK | PASS |Tap theo UIhierarchy, GplLicenseActivity rồi vềWelcome |
| Diagnostic/Start không tuner | PASS |DialogNo devices found,handledDvbExceptionDeviceController:64,Hardwareunchecked,Tune/Recorddisabled; PIDsống |
| Activity Home/resume/Back/Close/relaunch | PASS |Home→amstartđưa task vềDiagnostic;Back→Welcome;CloseraLauncher;warmrelaunch1796ms |
| Entry dtvdriver chooser không tuner | PASS có giới hạn |ACTION_VIEWdtvdriver:// mởChooser rồi finishvềWelcome,không crash; không assertioncallerresultcode |
| App crash/ANR/linker/security | PASS trong smoke window |PID6344filteredlog0fatal/linkage/security/appANR; exit-info chỉPACKAGEUPDATEDdo install-r |
| Service foreground/streaming lifecycle | NOT RUN |Khôngdevice/fixture,không épstartService; không cóDvbServiceServiceRecord |
| USB permission/transfer | NOT RUN |Emulator khôngusb.host,tunerkhônggắn; không fd giả/ioctl |
| Storage permission no-tuner path | PASS có giới hạn |READ/WRITEexternal=false nhưng UI/chọnno-devicevẫnchạy; chưa testrecordTS |
| libUsbXfer x86_64 trong APK | PASS |ZIPentrylib/x86_64/libUsbXfer.so7320bytes |
| Native library runtime loading | PASS |NativeLibrarySmokeTestassertIS_PLATFORM_SUPPORTED+directSystem.loadLibrary;0USBrequests/ioctl |
| Registry/XML instrumentation | PASS |DeviceFilterXmlEquivalenceTester.allDevicesAreInXml assertion giữ nguyên |
| Full connectedDebugAndroidTest | PASS |1019exit0/49s,XMLtestsuites2tests0fail0error,cả2classname/testmethod |
| Builddebug/testAPK và lint | PASS |1006exit0/2m4s;1016assembleDebug/lintDebugexit0/1m54s;lint0errors11warnings |
| Unit regression sau thay đổi | PASS task validation |1028exit0/32s,39tasksUP-TO-DATE,không gọi31methodsđãchạylạimới |
| RuntimeAPI21/arm64/16KiB | NOT RUN |ChỉAPI37x86_64page4KiB; không đánh đồng ELFalignvớiruntime16KiB |
| Workspace/ignore/instructions | PASS config |Exceptionđúngđãcólocal;4relativepathsresolve;2instructions cập nhậtEnglish; khôngstage/commit |
| References/hardware | PASS giữ nguyên/NOT RUN hardware |TBSindexthiếu81399Dsourcefilethiếu,SHAskhôngđổi,C/Go sạch; khôngsync/usbipd/firmware/vendorwrite |

**Coverage trap đã xử lý:** lệnh Gradle filtered2classđầu1012exit0 nhưng XMLchỉ1registrytest; không gọi đó là nativePASS. Directam instrument trước installtestAPK lỗi componentmissing; sau install-r1018native1testPASS. Rerun task **không filter**1019XML2tests xác nhận đủ. Không bỏtest/assertion, không tạoPASS chỉ từexitcode.

**Evidence:** screenshots before/after/no-tuner, XML2tests và logfiltered lưu ngoàiGit trong sessionfiles/phase-1-1. APK/hash và exactcommands ở WORKLOG/ENVIRONMENT. Emulator shutdown ghiWORKLOG; khôngxóadata.

## 2026-11-02 — Migration JDK25: kiểm chứng đã chạy

Windows10.0.19045/PowerShell7.6.9, JDK25.0.5.0.3; nhánh chore/modernize-android-build trên nền e4c13a5b86ad5ef9be887df58226046dcb42dcd5, chưa commit. Gradle9.8.2/AGP9.4.1; NDK30/CMake4.1.2/BT37/API37.2; min21.

| Gate | Kết quả cuối | Bằng chứng thực |
|---|---|---|
| JVM25 chạy Gradle9.8.2 | PASS | wrapper --version shell963exit0, Launcher/daemon JDK25; regenerated wrapper task973exit0 |
| Package toolchain | PASS | android sdk install đúng4package966exit0 + source.properties; NDK meta min21 |
| Debug APK | PASS | assembleDebug968exit0, chốt983exit0/36s;4ABI native |
| Unit usbxfer | PASS | XML1method,0failure |
| Unit drivers | PASS | XML17methods,0failure (1ConvertCString utility) |
| Unit dvbservice | PASS | XML13methods,0failure sau Mockito/null/newline fixes;974 và983 |
| Instrumentation test APK compile | PASS | assembleDebugAndroidTest, package trong974; chốt983exit0; APK đã sinh |
| Instrumentation chạy thiết bị | NOT RUN | Không emulator/device serial, không install hoặc boot |
| Lint debug | PASS có warning |980exit0, XML0Error/Fatal/11Warning; không baseline/suppress/abortOnError=false |
| Release APK unsigned | PASS |980exit0/3m54s, app-release-unsigned.apk5466380bytes; không signing/publish |
| Release AAB | PASS |980exit0, app-release.aab4782669bytes; không upload |
| Native4ABIs debug/release | PASS compile/link |arm64-v8a/armeabi-v7a/x86/x86_64; từng bước CMake hoàn tất |
| ZIP16KiB debug/release | PASS static |zipalign37 -c -P16 4 exit0 cho2APK |
| ELF64bit alignment | PASS static |llvm-readelf NDK30: LOAD0x4000 choarm64-v8a/x86_64 debug+release;32bit0x1000 ghi riêng |
| Native-load trên Android16KiB | NOT RUN |Static alignment không chứng minh runtime/USB ioctls |
| minSdk/targetSdk APK | PASS metadata |merged manifest min21,target37; không overrideLibrary; aapt2native4ABIs |
| RuntimeAndroid21/37/UI/permission/service | NOT RUN |Chưa chọn Android/AVD; build không chứng minh behavior target37 |
| TBS reference không tải/sửa | PASS theo phạm vi giữ nguyên |SHA3refs không đổi,C/Go sạch,TBS vẫn81399D;không Apply/fetch572objects |
| Firmware/I2C/LNB/lock/TS thật | NOT RUN |Không driver mới hoặc hardware writes |

**Các lần FAIL trước PASS:** configuration965thiếuNDK; debug967thiếu LocalBroadcastManager; unit971hai fail; release974/977native output directory Windows. Sau sửa đúng dependency/tests và chạy tuần tự max-workers1:979native riêngPASS,980release/lintPASS;983debug/tests/instrumentationcompilePASS. Không xóa test/suppress lint để né lỗi.

**Warnings tồn tại:**11lint (GradleDependency2do pin API21,UnusedResources3,IconLauncherShape3,TextFields/Autofill/LabelFor mỗi1), source/target8obsolete, AGP dùng APIGradledeprecated. Giữ kế hoạch xử lý riêng, không tuyên bố zero-warning hoặc mọi library latest tuyệt đối.

## 2026-11-02 12:52 +07 — JAVA_HOME recheck và dry-run TBS

Windows PowerShell; main SHA `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`; TBS SHA `2ce787de9f6e81b4294692a45de15596ae2e1322`. Không chạy build hoặc khôi phục reference.

| Kiểm tra | Trạng thái | Kết quả thực |
|---|---|---|
| Machine JAVA_HOME hợp lệ sau người dùng sửa | PASS | `C:\Program Files\Java\latest\jdk-25`, thư mục/executable tồn tại, java-version25.0.5.0.3 |
| Process JAVA_HOME hiện tại hợp lệ | FAIL | Agent vẫn nhận đường dẫn cũ không tồn tại từ process cha |
| Child đọc Machine JAVA_HOME và chạy java | PASS | Chỉ cập nhật env trong child; executable chạy exit0 |
| TBS index/source readiness | FAIL | Index/index.lock thiếu,81.399 D;6 selected dirs đều0 file |
| Blob selected directories local | PASS |785/785 blob tồn tại, no-lazy-fetch |
| Blob ancestor của cone đầy đủ local | FAIL |572/572 blob ancestor thiếu; cần approval tải nếu giữ cone |
| Tên file sẽ materialize trên Windows | PASS | Kiểm tên cơ bản1357 cone paths,0 reserved/invalid; không thay thế checkout test |
| read-tree dry-run mặc định | FAIL | exit128, `invalid path .../i2c/aux.c`, ngoài sparse nhưng có trong full index |
| read-tree dry-run command-scoped protectNTFS=false | PASS | exit0; index vẫn không tồn tại; không lưu config; chưa chứng minh restore thật |
| Script phục hồi bàn giao — cú pháp/preview | PASS | Parser0errors; chạy không tham số:1357cone files/572missing, index không được tạo; SHA256 trong RESEARCH |
| Script Apply/backup/fetch/khôi phục thực | NOT RUN | Người dùng chọn nhận bộ lệnh tự chạy; agent chỉ preview, chưa kiểm end-to-end |
| Gradle/build/hardware | NOT RUN | Ngoài phạm vi; JDK25 vẫn không hỗ trợ wrapper8.7 |

## 2026-11-02 — Phase 0: bằng chứng mới (UTC+07)

Windows PowerShell 7.6.9 / Windows 10.0.19045; cwd root AndroidDvbDriver, HEAD `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`. PASS dưới đây chỉ áp dụng đúng phép dò mô tả, không chứng minh driver hoạt động. Nguồn protocol khóa ở [RESEARCH.md](RESEARCH.md); lịch sử 2026-11-01 bên dưới giữ nguyên.

| Kiểm tra | Trạng thái | Lệnh / kết quả quan sát |
|---|---|---|
| 4 repository tồn tại, remote/branch/HEAD | PASS | `rev-parse --show-toplevel`, `remote -v`, `branch --show-current`, `rev-parse HEAD`; SHA ghi RESEARCH |
| Main working tree sạch | BLOCKED | `status --porcelain=v1 -uall`: 18 untracked có sẵn; không phải clean checkout để migration |
| Cộng đồng C và Go working tree | PASS | Status 0 thay đổi trước audit |
| TBS sparse configuration | PASS | `sparse-checkout list`: 6 patterns; sparseCheckout/Cone=true |
| TBS checkout/source trên đĩa | FAIL | Index thiếu, `ls-files` 0; 81.399 D; tbs5530/cxd2878/m88rs6060/dependencies thiếu; không restore |
| TBS source trong HEAD objects | PASS | `ls-tree`, `cat-file --batch-check`, `git show` đọc bridge/frontend blob local; không đồng nghĩa checkout tốt |
| Không fetch ngầm trong toàn audit | BLOCKED | Không fetch tường minh; diff --cached --shortstat đầu bị dừng sau 30s; FETCH_HEAD có mtime mới nhưng không có baseline để xác định tác nhân. Các lần đọc sau chặn lazy fetch |
| Workspace paths/JSON | PASS | `ConvertFrom-Json` và `Test-Path`: cả 4 roots resolve đúng |
| Workspace được version-control | FAIL | `git check-ignore -v`: `.gitignore:73`, `*.code-workspace`; không sửa ignore |
| Sync script cú pháp | PASS | PowerShell Parser.ParseFile, 0 errors; **không thực thi script** |
| Instructions/skills có trên đĩa và đọc được | PASS | Copilot instructions + 6 scoped + 2 project SKILL.md; project-handoff/tbs-research được skill tool load |
| VS Code tự attach mọi scoped instruction | NOT RUN | Context có hướng dẫn áp dụng; không có API xác minh từng lần attach, đã chủ động đọc đủ file |
| Java executable PATH | PASS | `java -version`: 25.0.5.0.3 |
| JAVA_HOME hợp lệ | FAIL | `Test-Path C:\Program Files\Java\jdk-25` false; executable thực ở jdk-25.0.5.0.3 |
| Environment SDK/AVD nhất quán | FAIL | Process không có ANDROID_HOME/ANDROID_AVD_HOME, User đúng D:\Android; CLI default chọn đường dẫn khác |
| Android CLI và explicit SDK inventory | PASS | `android --help`, `-V` 1.1.16717390; `--sdk=D:\Android\Sdk info sdk/sdk list` exit0 |
| SDK package versions | PASS | SDK list + source.properties xác nhận 34/37.0, BT36, NDK28.2, CMake3.22.1, CLI tools23; ADB37.0.1, Emulator37.2.13 |
| Android docs capability | PASS | `docs search` USB Host + `docs fetch kb://android/develop/connectivity/usb/host`; CLI tự tạo/tải KB cache, không đổi SDK packages |
| WSL2 hiện có | PASS | `wsl.exe --list --verbose`: Ubuntu-22.04 Running, v2; chưa kiểm kernel lại |
| usbipd executable/version | PASS | Full path Program Files\usbipd-win\usbipd.exe, 5.3.0-54; process PATH không thấy |
| TBS visible / USB ownership | PASS | `usbipd list`: VID:PID734c:5530, BUSID đọc live 6-2, Shared; phía Windows, chưa xác định app giữ handle |
| WSL attach/lsusb/PyUSB trong phiên này | NOT RUN | Thiết bị không Attached; không đổi ownership, không claim; PASS lịch sử không tính lại |
| MCP Proxy discovery/read-only invocation | PASS | retrieve_tools + call_tool_read cho adb-mcp/gitnexus/agentmemory |
| AVD listing qua ADB MCP | PASS | `adb-mcp:list_avds`: No AVDs found; không boot hoặc tạo AVD |
| ADB MCP UI / physical Android connection | NOT RUN | Chưa chọn serial, chưa UI/logcat/install; list_avds không chứng minh có thiết bị Android |
| GitNexus có index dự án | BLOCKED | list_repos không có AndroidDvbDriver/reference; không analyze |
| AgentMemory lookup | PASS | memory_recall compact limit3, query TBS5530 AndroidDvbDriver: matched_count0; không lưu mới |
| Gradle runtime/configuration baseline | BLOCKED | JAVA_HOME sai và Gradle8.7/JDK25 không hỗ trợ; không chạy wrapper/build để tái hiện |
| Assemble/lint/unit/instrumentation/native ABI/16KiB | NOT RUN | Chưa duyệt chạy build hoặc tạo emulator; chưa có APK/binary evidence |
| Firmware file/hash/license/upload | NOT RUN | Chỉ đọc tên/quy trình từ source; không tải/đóng gói/gửi firmware |
| I2C/chip-ID/frontend lock/TS continuity Android thật | NOT RUN | Chưa triển khai driver; không vendor write, reset, LNB |

**Không có attach/detach/bind/unbind hoặc test DVB mới.** Chẩn đoán Git và truy vấn CLI không được tính là kiểm thử phần cứng. Không lưu raw USB listing chứa các thiết bị cá nhân khác vào Git.

## Historical matrix — 2026-11-01 (người dùng cung cấp; không chạy lại)


Use `PASS` only for tests with recorded observations; `NOT RUN` when not executed.

| Test | Environment | Status (2026-11-01) | Evidence |
|---|---|---|---|
| USB device visible in Windows | Windows / usbipd list | PASS | `TBS 5530 DVB BDA`, ID `734c:5530` provided in screenshot |
| WSL USB attach | WSL2 / usbipd-win | PASS | BUSID `6-2`, state Attached in screenshot |
| USB descriptors | WSL2 / lsusb and PyUSB | PASS | 480 Mbps, interface 0, IN EP 0x82 and 0x81, 512 bytes each |
| libusb claim interface 0 | WSL2 / PyUSB | PASS | claim/release prints PASS |
| Driver firmware upload | WSL2/Android | NOT RUN | No test performed |
| USB/I2C control transactions | WSL2/Android | NOT RUN | No test performed |
| AndroidDvbDriver upstream build | Windows/Gradle | NOT RUN | Not confirmed by user |
| APK deployment to Android | Android / ADB | NOT RUN | Not confirmed by user |
| DVB-T2 frontend lock | Android | NOT RUN | Not yet ported |
| MPEG-TS stream capture and continuity | Android | NOT RUN | Not yet ported |

## New test evidence template
- Date/time (timezone):
- Device and firmware state:
- Host OS / kernel / USB passthrough:
- Command/code version:
- Observed result, raw log location and SHA256:
- PASS/FAIL/NOT RUN with rationale:
