# Implementation / test work log (append-only)

## 2026-11-01 — Configuration package prepared
- **Environment:** Windows/WSL2 reported by user; actual filesystem not accessed by package author.
- **Actions:** Prepared Copilot project guidance, research/handoff Agent Skills, environment inventory, and safe VS Code tasks.
- **Hardware tests performed by package author:** None.
- **Evidence from user's earlier shell commands:** WSL USB attach/enumeration and PyUSB claim/release passed.
- **Next:** Install package in current AndroidDvbDriver checkout, verify status and actual Gradle build baseline.

---
## Work session template

### YYYY-MM-DD — Session title
- **Branch / commit before:**
- **Objective:**
- **Files modified:**
- **Commands actually run (OS and cwd):**
- **Tests observed (PASS/FAIL/NOT RUN):**
- **Research links / ADR changes:**
- **Known issues / risks:**
- **Next single action:**
- **Branch / commit after:**

## 2026-11-02 — Phase 0: audit chỉ đọc, nghiên cứu và chuẩn hóa hồ sơ

- **Thời điểm bắt đầu kiểm tra trực tiếp:** khoảng 12:17 +07; Windows 10.0.19045, PowerShell 7.6.9, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`. WSL2 chỉ được liệt kê từ Windows, không chạy shell bên trong.
- **Branch/HEAD trước:** `master`, `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`; 18 untracked có trước phiên. Không stage/commit/push hoặc checkout/merge/reset/rebase. Không chỉnh code/build scripts/instructions/workspace/global settings.
- **Skills:** load thành công project-handoff, tbs-research và android-cli; đọc file project skills đầy đủ. Đọc copilot-instructions, cả 6 scoped instructions, 6 hồ sơ dự án, workspace, sync/check-env scripts và VS Code tasks. Chưa sửa settings để cưỡng chế auto-attach instructions; giới hạn nhận diện ghi ENVIRONMENT.
- **Phân công chỉ đọc:** build-compatibility hoàn tất audit configs/web chính thức. Agent android-architecture **thất bại** `400 The requested model is not supported`, không có report; parent chuyển semantic_search (rỗng) → glob/grep/view và tự hoàn tất toàn bộ Android trace, không relaunch cùng mục tiêu. Không agent nào được giao triển khai, build hoặc hardware. Parent đối chiếu nguồn TBS/C/Go, Git, môi trường và ghi tài liệu.

### Lệnh Git / kiểm tra filesystem đã chạy

- Mỗi repo: `git -C <repo> rev-parse --show-toplevel`, `remote -v`, `branch --show-current`, `rev-parse HEAD`, `status --porcelain=v1 -uall`, `log -1 --format='%h %cI %s'`; không fetch tường minh. References: TBS `2ce787de9f6e81b4294692a45de15596ae2e1322`, C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`.
- TBS: `sparse-checkout list`, `config --get core.sparseCheckout`, `config --get core.sparseCheckoutCone`, `ls-files`, `ls-files --stage -- <required-source>`, `ls-tree HEAD -- <required-source>`, `Test-Path`, `Get-ChildItem`. 6 sparse paths hiện hữu nhưng source thiếu và index không tồn tại; **81.399 staged D**, không phải lỗi “VS Code đếm nhầm”. C/Go sạch.
- `git diff --cached --shortstat` ban đầu chạy quá 30 giây → đã dừng đúng process tree bằng stop_powershell, không chạy tiếp diff full. Chưa đặt no-lazy-fetch lúc đó. Sau thấy FETCH_HEAD mtime 12:23:15 trong môi trường dùng chung, **không đủ dữ liệu xác định có fetch ngầm hay tác nhân khác**. Không giấu giới hạn no-fetch này. Không thay SHA/index/working tree references. Các lần sau `GIT_NO_LAZY_FETCH=1`, `GIT_OPTIONAL_LOCKS=0`; `cat-file --batch-check` kiểm blob có sẵn rồi `git show HEAD:<path>`; `diff --cached --name-status --no-renames` chỉ đếm tên, không tính nội dung/rename.
- Đọc bridge official và các hàm frontend/tuner/dependencies từ HEAD objects thay vì restore file; source C/Go đọc trực tiếp trên đĩa. Các file/hàm/SHA/URL và khác biệt nằm ở RESEARCH.
- Workspace: `Get-Content TBS5530.code-workspace -Raw | ConvertFrom-Json`, resolve/Test-Path cả 4 roots → PASS. `git ls-files`, `git check-ignore -v TBS5530.code-workspace` → bị ignore `*.code-workspace` tại `.gitignore:73`.
- Parse sync script bằng `System.Management.Automation.Language.Parser.ParseFile` → 0 lỗi. **Không chạy script Sync hoặc check-env**; CheckOnly vẫn fetch/tạo log nên ngoài phạm vi.

### Lệnh môi trường đã chạy và kết quả

- `Get-Date -Format o`, `[Environment]::OSVersion.VersionString`, `$PSVersionTable.PSVersion`, `git --version` → Windows 10.0.19045, PS 7.6.9, Git 2.56.0.windows.3.
- `Get-Command java,android,git,usbipd,wsl.exe`; `java -version`; `[Environment]::GetEnvironmentVariable` trên Process/User/Machine; `Test-Path $env:JAVA_HOME`; `Get-ChildItem 'C:\Program Files\Java'`. Java 25.0.5.0.3 trên PATH hoạt động; JAVA_HOME `jdk-25` không tồn tại, JDK thực là `jdk-25.0.5.0.3`.
- `android --help`, `android -V`, `android info sdk`, `android --sdk=D:\Android\Sdk info sdk`, `android --sdk=D:\Android\Sdk sdk list`. CLI 1.1.16717390; SDK mặc định của process khác User, chỉ định SDK tường minh thì đúng. Đọc source.properties của platform 34/37.0, BuildTools 36, NDK 28.2.13676358, CMake 3.22.1, emulator 37.2.13; cmdline 23 qua list. ANDROID_HOME/ANDROID_AVD_HOME thiếu ở Process nhưng đúng ở User; không sửa biến.
- `D:\Android\Sdk\platform-tools\adb.exe --version` → 37.0.1. `emulator.exe --version` chỉ in thông tin kèm `No AVD specified`; truy vấn đúng sau đó `emulator.exe -version` → 37.2.13.0. Không truyền AVD/boot; D:\Android\AVD rỗng, default avd directory không có.
- `android docs search --help`, `android docs search "USB host UsbDeviceConnection controlTransfer bulkTransfer requestPermission"`, `android --sdk=D:\Android\Sdk docs fetch kb://android/develop/connectivity/usb/host` lọc nội dung cần thiết. CLI tự tải KB index/cache lần đầu; không cài/nâng package SDK hoặc gửi source dự án. Các thông báo JVM UseAllWindowsProcessorGroups được ghi là warning, không biến thành build failure.
- `wsl.exe --list --verbose` → Ubuntu-22.04 Running/v2. `usbipd` thiếu PATH, dùng executable có thật `C:\Program Files\usbipd-win\usbipd.exe --version` và `list` → 5.3.0-54; TBS có VID:PID 734c:5530, BUSID đọc trực tiếp là 6-2, **Shared**, không Attached; quyền phía Windows. Không chạy lsusb/PyUSB hoặc thay quyền sở hữu thiết bị; không xác định process Windows giữ handle.

### MCP, nguồn web và kiểm thử

- Qua Gateway: retrieve_tools → call_tool_read `gitnexus:list_repos`, `adb-mcp:list_avds`, `agentmemory:memory_recall` (query TBS5530 AndroidDvbDriver, compact, limit 3, budget 1000). Công cụ đáp ứng; GitNexus thiếu index dự án; danh sách AVD rỗng; memory trả matched_count 0. Không analyze index, lưu memory, bật emulator, cài APK, đọc log cá nhân hoặc đổi Proxy config.
- session_store_sql: query sessions trong 7 ngày theo repository identity → 0 dòng. Không coi đây là bằng chứng không có bất kỳ lịch sử nào.
- Web chính thức: Gradle releases/compatibility 8.7/current; AGP API reference + release notes 8.5/8.10/8.13/9.0/9.4/9.5; tài liệu Android JDK/NDK/ABI/16 KiB/behavior 36. Research ghi URL, phiên bản thực nhận và xung đột giữa trang AGP với tóm tắt tìm kiếm; không suy diễn stable theo ngày.
- **PASS có phạm vi:** Git identity/source objects, workspace resolution, sync syntax, SDK inventory, WSL listing, USB visibility/ownership listing, MCP read-only calls, source analysis.
- **FAIL:** TBS checkout thực thiếu/index thiếu; JAVA_HOME không tồn tại; SDK process/default lệch; workspace ignored (khả năng bàn giao qua Git chưa đủ).
- **BLOCKED:** clean baseline, Gradle runtime dự kiến, GitNexus project index, cam kết không fetch ngầm toàn phiên.
- **NOT RUN:** Gradle/unit/lint/assemble/instrumentation/native ABI/16 KiB, APK/UI, WSL USB attach/claim lại, firmware/hash/license/upload, vendor/I2C/chip ID/DVB-T2 lock/MPEG-TS thật. Bằng chứng WSL/PyUSB 2026-11-01 giữ nguyên, gắn rõ lịch sử.

### Hồ sơ và bàn giao

- **Chỉ cập nhật:** STATUS.md, ENVIRONMENT.md, RESEARCH.md, DECISIONS.md, WORKLOG.md, TEST-MATRIX.md dưới docs/project; bảo toàn toàn bộ nội dung lịch sử, thêm snapshot mới. Không tạo markdown kế hoạch ngoài sáu hồ sơ đã được yêu cầu.
- **Quyết định:** ADR-004 (bảo toàn checkout và đọc blob), ADR-005 (đề xuất baseline JDK 17/process rồi migration nhỏ), ADR-006 (đề xuất userspace một TS, DVB-T2 trước); xem DECISIONS. Chưa chấp thuận thực thi migration/hardware.
- **Blockers:** index/source TBS; environment stale/JAVA_HOME; JDK 17/package baseline chưa có; main hồ sơ untracked/workspace ignored; firmware provenance/license/hash; Android vật lý và hardware gate chưa chạy.
- **Next single action:** soạn và xin duyệt một kế hoạch khôi phục index/sparse checkout TBS, nêu cách bảo toàn metadata/trạng thái và tiêu chí kiểm lại; **chưa chạy lệnh khôi phục**. Sau đó mới xin phạm vi baseline build; không tự tiếp Phase 1.
- **Branch/HEAD sau:** master / e4c13a5b86ad5ef9be887df58226046dcb42dcd5, không commit/push.
- **Kiểm chứng hồ sơ cuối phiên:** `git diff --name-only`, `git diff --cached --name-only` cho thấy main không có tracked/staged diff; status vẫn 18 untracked như đầu, sáu tài liệu nằm trong số này nên diff mặc định không thể hiện nội dung. Reference HEAD không đổi, TBS vẫn có 81.399 D/index thiếu, C/Go vẫn sạch. Kiểm UTF-8 strict cả 6 file PASS, không có liên kết .md tương đối bị hỏng; VS Code problems cho docs/project trả `No errors found`. Không có bộ documentation tests riêng được phát hiện/chạy. Chỉ sáu hồ sơ được sửa; chưa đưa vào quản lý phiên bản bằng commit.
- **Bổ sung trace trực tiếp:** đọc registry/DvbUsbDevice, permission/I2C/frontend/tuner contracts, generic bulk so EP0 RTL, Sony Cxd2841er và Mygica/AF9035 patterns, native C/JNI, TS demux/DataPump/app/service/Request. Phát hiện API chưa có PLP và satellite enums; RESEARCH ghi nguồn cụ thể và lifecycle risks, không thay runtime.

## 2026-11-02 12:52 +07 — Kiểm tra JAVA_HOME và chuẩn bị kế hoạch phục hồi TBS

- **Yêu cầu:** bắt đầu bước kế tiếp đã đề xuất (lập/xin duyệt kế hoạch), kiểm lại JAVA_HOME sau người dùng sửa; chưa tự khôi phục reference.
- **Environment:** Windows PowerShell, cwd AndroidDvbDriver; main master/e4c13a5b86ad5ef9be887df58226046dcb42dcd5, 18 untracked. TBS latest/2ce787de9f6e81b4294692a45de15596ae2e1322.
- **JAVA_HOME commands:** GetEnvironmentVariable cho Process/User/Machine, Test-Path home/bin/java.exe, Get-Command java, java -version, gọi java.exe Machine trực tiếp. Machine đã đúng C:\Program Files\Java\latest\jdk-25, version 25.0.5.0.3; Process còn C:\Program Files\Java\jdk-25 không có thật. Trong child khác, đọc Machine vào `$env:JAVA_HOME` rồi chạy bin\java.exe: exit 0; không đổi registry env/global/VS Code process. ANDROID_HOME/AVD của User đúng nhưng Process vẫn thiếu.
- **TBS commands:** git --no-lazy-fetch status/rev-parse/branch/remote/sparse-checkout list/ls-tree/cat-file; env GIT_NO_LAZY_FETCH=1/GIT_OPTIONAL_LOCKS=0; kiểm index/index.lock/GIT_INDEX_FILE và số file. Vẫn có 81.399 D, index thiếu, 6 thư mục và 0 file. Có 785 selected blob local, thiếu 572 cone ancestor blob. Kiểm cơ bản 1357 đường dẫn dự kiến materialize: 0 tên Windows không hợp lệ.
- **Dry-run đã chạy:** `git --no-lazy-fetch -C <TBS> read-tree --dry-run -m -u HEAD` → exit 128, invalid path drivers/gpu/drm/nouveau/nvkm/subdev/i2c/aux.c. `git --no-lazy-fetch -c core.protectNTFS=false -C <TBS> read-tree --dry-run -m -u HEAD` → exit 0, index vẫn thiếu. `git config --show-origin --get core.protectNTFS` → exit 1/không có stored override. Không dùng command-scoped false để materialize thật.
- **Bằng chứng thời gian:** FETCH_HEAD trước các phép dò có mtime 12:50:47+07. Không cho rằng đây là tác động của lượt này. Không có fetch/pull/clone/sync hoặc ghi branch/index/source của reference trong lượt này.
- **Giới hạn mới:** dry-run trả 0 không kiểm chứng đủ blobs để restore; cần đồng ý tải 572 ancestors nếu giữ nguyên cone. Invalid AUX path tái hiện được, nguyên nhân mất index ban đầu vẫn chưa biết.
- **Hồ sơ cập nhật:** ENVIRONMENT/RESEARCH/STATUS/TEST-MATRIX/WORKLOG; giữ kết quả cũ. RESEARCH có kế hoạch backup, command-scope workaround, giới hạn tải, gate kiểm chứng/rollback và phương án offline. Không tạo tool/script mới hoặc sửa instruction/build/source.
- **Lựa chọn người dùng:** ask_user trả `prepare-user-recovery`; soạn script tự chạy, không thực thi phục hồi thay người dùng. Đã tạo `Restore-TbsSparseCheckout-Reviewed.ps1` trong session files ngoài repo, giữ references read-only. Có preview mặc định, Apply opt-in+xác nhận, backup .git ngoài repo, kiểm metadata/locks/Windows paths, fetch exact missing blobs không update refs/FETCH_HEAD, read-tree command-scoped protectNTFS=false và postchecks status/identity/file hashes; không reset/clean hoặc hardware.
- **Validation script:** PowerShell Parser.ParseFile: 0 errors; chạy script không tham số: HEAD khóa/1357 cone files/572 missing, index vẫn thiếu; SHA256 EFC40DE33FB4C202C49FB720DD569CB65A6B234B1F0055BD8CEC270D61AD97F5, 13:01:40+07. Không chạy Apply/backup/fetch/restore; không tuyên bố end-to-end PASS.
- **Concurrent metadata:** FETCH_HEAD quan sát lúc 12:56:56, khác lần đầu 12:50:47 dù lệnh chỉ đọc/no-lazy-fetch. Chưa biết tác nhân; hướng dẫn người dùng dừng Git sync khác trước Apply. Script kiểm quãng yên 3 giây và trong backup, không thể loại bỏ mọi race nếu tiến trình khác bắt đầu sau đó. Không kill process khác hoặc sửa settings.
- **Next:** người dùng xem script rồi tự chạy preview/Apply theo phạm vi đã chọn, gửi kết quả đã lược thông tin riêng; không agent ghi reference/build/migration/hardware. JAVA_HOME Machine đã đúng, vấn đề tương thích runtime Gradle 8.7/JDK 25 vẫn còn.

## 2026-11-02 — Nâng build JDK 25 theo yêu cầu mới (bắt đầu 13:06+07)

- **Approval:** cho nâng latest stable và sửa tương thích; ask_user chốt preserve-21/local-read-only. Không chạy script reference Apply, không fetch 572 ancestors. Không ghi vào phần cứng, chạy emulator hoặc install APK. Quyết định mới ADR-007 thay đề xuất baseline JDK 17-first; bản build gốc chưa từng được chạy nên không gọi nó PASS.
- **Git:** tạo/checkout nhánh chore/modernize-android-build từ master/e4c13a5b86ad5ef9be887df58226046dcb42dcd5; giữ 18 untracked gốc, không stage/commit/push. GitNexus list_repos vẫn không có index project; dùng source trực tiếp, không analyze.
- **Versions:** Gradle 9.8.2 có checksum chính thức, AGP 9.4.1 có POM/release docs, SDK compile 37.2/target 37/min 21, NDK 30.0.16248370/CMake 4.1.2/BT 37.0.0; giữ source-target Java 8. AppCompat 1.7.1/Material 1.13.0 là giới hạn min 21; test-ext 1.3.0/Espresso 3.7.0, Mockito 5.24.0/JUnit 4.13.2. URL tài liệu nguồn ở RESEARCH.
- **Môi trường lệnh:** Windows PowerShell 7.6.9/10.0.19045, cwd root app; mỗi process set JAVA_HOME từ Machine, ANDROID_HOME=D:\Android\Sdk, ANDROID_AVD_HOME=D:\Android\AVD; không đổi registry/global PATH.
- **Lệnh đã chạy:** android --help/sdk list --help/sdk list --all (lọc catalog), sdk install --help, docs search AGP minorApiLevel; web_fetch official compatibility/POM/metadata/checksum. Đọc/sửa source của 4 Gradle modules + root/properties + native config/tests/README/task. Không tải source lên dịch vụ bên ngoài.
- `gradlew --version` tải binary distribution 9.8.2, verify checksum, exit 0 (shell 963); Launcher JVM 25.0.5.0.3/daemon dùng JDK thực 25.0.5.0.3. `gradlew help --no-daemon --console plain --warning-mode all` exit 1, NDK not configured (965). Sau thay đổi manifest/lỗi mới cài 4 packages bằng android sdk install; lượt 966 exit 0. Đọc source.properties xác minh, meta/platforms.json min 21.
- `:app:assembleDebug` đầu tiên, lượt 967, exit 1 do thiếu androidx.localbroadcastmanager; thêm dependency trực tiếp 1.1.0, lượt 968 exit 0, BUILD SUCCESSFUL trong 2m39s/95 tasks, 4 ABIs. runTask editor không tìm thấy task nên dùng terminal cùng command và sửa task env để người dùng dùng lại; không tạo tasks.json mới.
- `:usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest :dvbservice:assembleDebugAndroidTest` lượt 971 exit 1: service có 13 tests, gồm 2 fail (hardcode newline Windows và Mockito anyString không match null). Chỉ sửa test: openMocks/MockedStatic/cleanup, isNull matcher, System.lineSeparator; runtime không đổi. XML sau lượt 974: drivers 17/usbxfer 1/dvbservice 13, 0 fail (có 1 ConvertCString utility trong 31 methods).
- `wrapper --gradle-version9.8.2 --distribution-type bin --gradle-distribution-sha256-sum <official> --console plain --no-daemon` lượt 973 PASS; tạo lại wrapper JAR/scripts bằng tool chính thức. CRLF của gradlew.bat do tool tạo bị diff --check mặc định xem CR là whitespace; không thay generated script tùy tiện, kiểm lại cr-at-eol riêng.
- Lượt combined 974 gồm unit/instrumentation compile/lint/release/bundle exit 1 ở native release x86: Unable to delete directory. Instrumentation APK đã package, unit xanh, lint/release tổng chưa hoàn tất. Retry 977 với max-workers 2 lỗi tương tự x86_64; lượt 979 chạy riêng buildCMakeRelWithDebInfo[x86_64] với max-workers 1, PASS trong 25s. Không xóa output/kill process khác; retry 980 release/lint tuần tự với max-workers 1, ghi kết quả khi hoàn tất.
- **APK/native check đã chạy:** zipalign37 -c -P16 4 debug APK exit 0; ZIP có libUsbXfer.so cho arm64-v8a/armeabi-v7a/x86/x86_64; llvm-readelf NDK 30: LOAD 64 bit 0x4000, 32 bit 0x1000. Debug SHA256 BC9FD306B79B4CEA044831547F49465F4650040AD1B90553DA509C8476BAB443. Đây là kiểm tra tĩnh, không phải runtime 16 KiB/USB host.
- **References recheck:** SHA cả 3 không đổi; TBS có 81399 D, index thiếu, C/Go có 0 changes. Không tải TBS objects/thay sparse, không download firmware/images. Toolchain downloads là 4 SDK packages + Gradle binary/deps phục vụ chính build 4 modules, không có SDK thứ hai.
- **Files:** 4 module build.gradle, root build.gradle, gradle.properties, wrapper properties/JAR/scripts, CMakeLists, 3 service test files, README, .vscode/tasks.json và 6 hồ sơ dự án. Không sửa source runtime/public API/driver implementation hoặc instructions.
- **Kết quả cuối lượt 980:** `:app:lintDebug :app:assembleRelease :app:bundleRelease --console plain --no-daemon --max-workers=1` **exit 0**, 3m54s, 286 tasks (155 executed/131 up-to-date). Native release cho 4 ABIs hoàn tất. Lint XML: 0 errors/11 warnings (2 dependency version có chủ đích vì API 21, 9 UI/resources), không suppress/baseline/disable gates.
- **Chốt lượt 983:** `:usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest :dvbservice:assembleDebugAndroidTest :app:assembleDebug --console plain --no-daemon --max-workers=1` exit 0/36s, 138 tasks (8 executed/130 up-to-date). XML: 31 methods, 0 fail. Wrapper được tạo lại giảm native-access warning của launcher nhờ defaults mới; cảnh báo javac 8 obsolete/AGP Gradle deprecated vẫn được ghi rõ.
- **Outputs static verification:** release APK unsigned có 5466380 bytes, SHA256 1D7D415C19A07FD55B1DEDB2FA885C48CD170D8A7802C5077B0E23CB1051C999; release AAB có 4782669 bytes, SHA256 A3E29DA0FBF1F728A9E6477CD59FF6FC1CD2183490610D571CD0AA1DFA06EA93; instrumentation APK có 7411924 bytes, SHA256 760B280C897636AC6CB7C4539AA19FE76FE042BBD48D6DAECBE10A9C30D2B641. zipalign release exit 0; ELF 64 release LOAD 0x4000. Merged manifest min 21/target 37; aapt2 badging có native cho 4 ABIs. Không ký release/publish hoặc chạy Android.
- **Rà phạm vi:** diff --check có bật cr-at-eol PASS cho wrapper.bat CRLF do tool tạo. Wrapper.properties giữ lại license notice cũ sau generator; wrapper scripts mới có Apache 2.0 notice chính thức. Một lệnh rà thử cast manifest-merger txt thành XML thất bại do sai định dạng; kiểm manifest XML thật sau đó xác nhận min 21/target 37, không có source changes từ phép dò.
- **Bàn giao:** STATUS/ENVIRONMENT/RESEARCH/DECISIONS/TEST-MATRIX/WORKLOG và README cập nhật. Chưa commit/push; references giữ read-only/SHA/status như cũ. Runtime Android 21/37/UI/permission/service/native-load 16 KiB/TBS hardware vẫn NOT RUN. Next: review diff và chọn môi trường Android được phép kiểm thử phần mềm không tuner; không tự boot emulator.

## 2026-11-02 18:48 +07 — Checkpoint theo yêu cầu commit

- **Phạm vi người dùng yêu cầu:** chuẩn bị commit để sau đó push origin; tác vụ này chỉ commit local, không push. Message `chore: prepare TBS5530 development with JDK 25`, kèm trailer Co-authored-by: Copilot.
- **Môi trường:** Windows PowerShell/root AndroidDvbDriver, nhánh chore/modernize-android-build; HEAD trước e4c13a5b86ad5ef9be887df58226046dcb42dcd5. Main có 34 entries (16 modified/18 untracked), index ban đầu chưa stage. Không sửa references.
- **Remote quan sát mới:** origin git@github.com:trankien84/AndroidDvbDriver.git, upstream https://github.com/signalwareltd/AndroidDvbDriver.git. Chỉ đọc remote-v, không thay remote hoặc fetch/push. Không dùng origin cũ trong audit làm đích push tự động.
- **Review:** status/diffstat, danh sách 34 file và kích thước; heuristic secret scan có 0 match (không phải chứng nhận security audit); parse JSON tasks PASS; diff --check với cr-at-eol PASS. Không stage firmware/captures/logs/localproperties/APK/AAB/native output/cache, không force-add workspace bị ignore. Thay đổi của người dùng trong `.gitignore`, thêm *.fw/*.ts, đã được rà và đưa vào phạm vi commit.
- **Validation đã chạy:** set JAVA_HOME từ Machine + SDK env trong process; `gradlew.bat :usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest :app:assembleDebug --console plain --no-daemon --max-workers=1` exit 0/59s, 107 tasks (8 executed/99 UP-TO-DATE); unit tasks UP-TO-DATE chứ không chạy lại mọi test. Không có lint/release/hardware/Android runtime mới; kết quả migration trước giữ nguyên.
- **Hồ sơ chuẩn bị:** thêm checkpoint STATUS/WORKLOG trước commit để bàn giao origin/thời điểm/phạm vi và kết quả kiểm chứng. Hash commit tra git log sau thực thi, tránh ghi SHA tự tham chiếu vào chính commit. Không thay chỉ dẫn/skills/build ngoài các thay đổi đã có.
- **Tiếp theo:** xác minh commit/index/working tree rồi báo hash; người dùng có thể push nhánh hiện tại tới fork origin. Workspace file hiện bị ignore, không thuộc commit. Android runtime/native-load/USB Host/TBS vẫn NOT RUN.

## 2026-11-02 — Phase 1.1: kiểm thử Android runtime, không tuner

- **Thời gian:** 19:31–20:07 +07. Windows 10.0.19045 / PowerShell 7.6.9, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`.
- **Git trước:** modernization tại `6da609ba49dd9f1f4d284291918f68a6378972ff`; người dùng xác nhận đã push, local ref `origin/chore/modernize-android-build` cũng trùng SHA. Không fetch để kiểm remote live. Có hai thay đổi local trước phiên: `.gitignore` đã thêm `!TBS5530.code-workspace`, workspace chưa tracked. Kiểm hash trước/sau khi `git switch -c test/android-runtime-smoke chore/modernize-android-build`: giữ nguyên cả hai. Không stash/reset/stage/commit/push/merge.
- **Guidance:** đọc copilot-instructions, cả 6 scoped instructions, 2 project SKILL.md và hồ sơ mới nhất. Load project-handoff/android-cli/agent-customization; skills tbs-research chỉ đọc vì không nghiên cứu protocol mới. GitNexus list_repos không có target index; AgentMemory lookup không có match; không analyze hoặc sửa Proxy config. Đọc Android docs qua CLI cho instrumentation và edge-to-edge.

### Môi trường và lựa chọn AVD

- `GetEnvironmentVariable` Process/User/Machine: process vẫn JAVA_HOME cũ không tồn tại, thiếu SDK/AVD env; Machine JAVA_HOME hợp lệ `C:\Program Files\Java\latest\jdk-25`, User SDK/AVD đúng. Mỗi lệnh cần thiết set JAVA_HOME từ Machine và SDK/AVD process riêng, không thay global.
- `android --help`, `android info sdk`, `android --sdk=D:\Android\Sdk sdk list`, `emulator list`, `emulator.exe -list-avds`, ADB `devices -l`, MCP `list_avds`: ban đầu không có AVD hoặc device. Có image `system-images;android-37.0;google_apis;x86_64` cài sẵn. User đồng ý tạo AVD riêng qua ask_user.
- Android CLI `emulator create --help` chỉ hỗ trợ chọn profile, không chọn name/path/image chính xác; dùng avdmanager hiện có. `avdmanager create avd --help` trả lỗi cú pháp rồi in đúng options; không ghi gì. Sau đó `'no' | avdmanager.bat create avd -n TBS5530_Runtime_API37 -k 'system-images;android-37.0;google_apis;x86_64' -p D:\Android\AVD\TBS5530_Runtime_API37.avd -d medium_phone`, exit 0; không `--force`, không tải SDK/image, không wipe data.
- Emulator process ở shell 1004: `emulator.exe -avd TBS5530_Runtime_API37 -port 5554 -no-snapshot -no-boot-anim -no-audio -no-window -gpu swiftshader_indirect`. Đợi `get-state=device` và `sys.boot_completed=1` với timeout 4 phút (lượt 1005 exit 0).
- Runtime từ getprop: Android 17, SDK 37, x86_64; `getconf PAGE_SIZE=4096`; `emu avd name` đúng AVD đã tạo. `pm list features` không có USB host. Không gắn tuner/emulator USB passthrough. Source.properties của image ghi revision 7, CLI catalog trước đó báo 8; ghi metadata/runtime thực, không suy diễn runtime 37.2 từ compileSDK.
- `gradlew --version`: Gradle 9.8.2/JDK 25.0.5.0.3. Đọc scripts và source.properties xác nhận AGP 9.4.1/compile 37.2/target 37/min 21, NDK 30.0.16248370/CMake 4.1.2/BT 37.0.0; ADB 37.0.1/Emulator 37.2.13. Không nâng version/dependency hoặc cài package nào.

### Build, instrumentation và native load

- Thêm `dvbservice/src/androidTest/.../NativeLibrarySmokeTest.java`: assertion `UsbHiSpeedBulk.IS_PLATFORM_SUPPORTED`, gọi direct `System.loadLibrary("UsbXfer")`; không tạo connection/request hoặc ioctl với fd giả. Thêm androidTestImplementation project(:usbxfer) chỉ cho test compile; không external library update.
- `gradlew :app:assembleDebug :dvbservice:assembleDebugAndroidTest --console plain --no-daemon --max-workers=1`: shell 1006 exit 0, 2m4s. ZIP APK có `lib/x86_64/libUsbXfer.so`, 7320 bytes.
- `:dvbservice:tasks --all` xác nhận connectedDebugAndroidTest tồn tại. Lệnh lọc hai class ở lượt 1012 exit 0 nhưng XML chỉ có 1 registry test; không tuyên bố native PASS từ exit code. Gọi trực tiếp `am instrument` ở lượt 1017 trước khi cài lại test APK bị lỗi component missing vì Gradle đã gỡ test package sau lượt chạy.
- `adb -s emulator-5554 install -r <dvbservice-debug-androidTest.apk>` Success; gọi trực tiếp `am instrument -w -r -e class info.martinmarinov.dvbservice.NativeLibrarySmokeTest info.martinmarinov.dvbservice.test/androidx.test.runner.AndroidJUnitRunner` ở lượt 1018 chạy **OK (1 test)**, assertion thành công, 0.037s.
- Rerun **không filter**, `$env:ANDROID_SERIAL='emulator-5554'; gradlew :dvbservice:connectedDebugAndroidTest --console plain --no-daemon --max-workers=1` ở lượt 1019 exit 0/49s, 92 tasks (9 executed/83 UP-TO-DATE). XML `dvbservice/build/outputs/androidTest-results/connected/debug/TEST-TBS5530_Runtime_API37(AVD) - 17.xml`: testsuites **2 tests/0 failures/0 errors**, tên chính xác registry.allDevicesAreInXml và NativeLibrarySmokeTest.usbTransferLibraryLoadsWithoutOpeningUsb. Giữ assertion registry cũ, không disable test.
- Chép XML evidence ra session files/phase-1-1 (ngoài Git), SHA256 `689B2B2DED52D8E0649BACABEE09226C2E00D68CADEA270C2D1000890C35BC17`.

### UI, lifecycle và lỗi tìm thấy

- `adb -s emulator-5554 install -r app-debug.apk` ở lượt 1008 Success; `am start -W -n info.martinmarinov.dvbdriver/.WelcomeActivity`: Status ok/COLD, TotalTime 6606 ms, PID 3840.
- MCP describe_ui/screenshot phát hiện overlay **System UI isn't responding**, focused package com.android.systemui, không phải app. Chọn Wait tại tâm phần tử trong hierarchy; overlay biến mất. Không kill SystemUI/disable security/đổi OS. Ghi SystemUI ANR ở lần boot đầu là vấn đề môi trường, không ẩn nó.
- Welcome→Diagnostic→Start mở dialog `No devices found` và stacktrace DvbException đã được xử lý tại DeviceController:64. PID app vẫn sống; hardware/signal/lock chưa được đánh dấu; Tune/Record không bật. Không có app FATAL/UnsatisfiedLinkError/SecurityException trong log PID đã lọc. Đây không phải tuner test.
- UI trước sửa có controls bị system bars che: hierarchy rowTune bắt đầu ở y 0; Close đến y 2400. Screenshot xác nhận nội dung/status icons chồng nhau. Thêm `SystemBarInsets.java`, dùng systemBars|displayCutout và padding gốc, chỉ API 35+, dark icons; gọi từ WelcomeActivity/DvbFrontendActivity/GplLicenseActivity sau setContentView. Không đổi targetSDK/minSDK/USB/service logic.
- `gradlew :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1` ở lượt 1016 exit 0/1m54s, 162 tasks (27 executed/135 UP-TO-DATE), lint có 0 errors/11 warnings, không thêm suppression/baseline mới.
- Cài lại APK đã sửa ở lượt 1020 Success; am start tạm trả PackageUpdateActivity (chuyển tiếp của hệ thống); hierarchy tiếp theo đúng Welcome. Sau sửa, top inset 63 px/bottom 63 px, rowTune y 63, controls dưới cùng dừng ở y 2337 trên screen 2400. MCP screenshot và PNG trước/sau đã lưu xác nhận.
- Test Welcome→License→OK quay về Welcome; Diagnostic→Home→am start đưa task lại Diagnostic, state bình thường; Start lại vẫn hiện no-device dialog; Back đóng dialog/rời Diagnostic về Welcome. `ACTION_VIEW dtvdriver:// -p <app>` ở lượt 1024 mở DeviceChooserActivity rồi finish về Welcome khi không có device, không ép start DvbService. Không có caller để assert returned result code nên không báo coverage contract đầy đủ.
- Nút Close đưa về launcher; warm relaunch ở lượt 1026: Status ok, TotalTime 1796 ms, PID 6344 sống. Lọc log PID cuối có 0 match FATAL/linker/security/app ANR; dumpsys exit-info chỉ có reason 16 PACKAGE UPDATED do install-r, không crash. Không khẳng định mọi log trong toàn emulator không có warning.
- Permission: READ/WRITE_EXTERNAL_STORAGE=false, internet/foreground permissions granted; no-tuner UI vẫn chạy. USB permission **NOT RUN** vì không có USB Host/device. DvbService foreground/streaming **NOT RUN** vì chưa có tuner/fixture hợp lệ; dumpsys không có service record của app. Không `am startservice` với intent giả/null.

### Config, bảo toàn reference và cleanup

- Workspace JSON có 4 roots tương đối đúng và PowerShell mặc định. Exception `.gitignore` đã có sẵn trước phase; không thêm trùng/force-add/stage. Hash cuối .gitignore `DB291A4767C7E5C4B900D560E9B12781A24BDB5D287C280A0B076CBF625981A3`, workspace `B715366CEBE4B1C9A74B1ECC4F747024D10EECEB581B4FF1ED0800134F4D11F9`, giữ nguyên so với trước khi tạo branch.
- Cập nhật snapshot toolchain bằng tiếng Anh trong `.github/copilot-instructions.md` và `android-build.instructions.md`; giữ quy tắc an toàn Windows/WSL/usbipd/MCP/hardware. Không sửa 4 instructions còn lại/skills/global settings. Không nâng AGP 9.5 hoặc dependencies.
- Các lệnh kiểm reference chỉ gồm rev-parse/status/sparse list/Test-Path, dùng `--no-lazy-fetch` và GIT_OPTIONAL_LOCKS bằng 0: TBS SHA 2ce787de9f6e81b4294692a45de15596ae2e1322 vẫn thiếu index/81399 D, tbs5530.c/.h và cxd2878.c thiếu trên đĩa. C SHA c5fa27eecbe80633cbb365cc44656daea1c0e2e3 và Go 11228b05fb1626bf41c4e1879ace10cd0a8d575c sạch. Không sync/fetch/repair/reset hoặc ghi USB/firmware/I2C/vendor.
- Unit regression ở lượt 1028 exit 0/32s, 39 tasks UP-TO-DATE: validation task thành công, không nói 31 methods được chạy lại mới. Tests đã thực chạy mới là instrumentation 2 methods.
- Cleanup: xác nhận `emu avd name` trước `adb -s emulator-5554 emu kill`; shell 1004 kết thúc exit 0. Lần list ngay 3s sau còn serial do shutdown đang diễn ra; recheck lượt 1031 `adb devices -l` rỗng. Giữ AVD để tái sử dụng, không wipe/xóa hoặc dừng thiết bị khác.

### Evidence và bàn giao

- Bằng chứng ngoài Git: session files/phase-1-1 chứa diagnostic-before-insets.png, diagnostic-after-insets.png, no-tuner-after-insets.png, welcome-after-insets.png, instrumentation-results.xml, app-logcat-filtered.txt. Không raw personal device IDs/credentials, không commit log/screenshot build output.
- Hash APK debug đã kiểm thử runtime sau sửa: `8B4E47A9E60D6153CC3B97F244C45771AD0108BA0C1CA785F72332DA31FE38F6`; test APK `F85C7D2ADE771570018623D4BD9163AB358D9B4A2530697FEA2F8D484FAB9872`.
- Snapshot STATUS/ENVIRONMENT/TEST-MATRIX và ADR-008 ghi PASS/FAIL/NOT RUN, không viết lại lịch sử. Chưa stage/commit/push; HEAD vẫn 6da609b. Files cần review: 2 instructions, local .gitignore/workspace, 3 Activity + SystemBarInsets, dvbservice/build.gradle + NativeLibrarySmokeTest, 5 hồ sơ dự án. Không sửa RESEARCH vì không phát sinh phát hiện về giao thức.
- **Điểm dừng:** Phase 1.1 hoàn tất trong phạm vi Android 17/API 37/x86_64/4 KiB. Android API 21/arm64/16 KiB runtime, foreground service streaming/USB permission/TBS thật đều NOT RUN. Next: review và commit có chọn lọc khi được cho phép; không tự port driver hoặc mở kiểm thử phần cứng.

## 2026-11-02 21:35 +07 — Commit, merge, push Phase 1.1 và xóa nhánh test

- **User approval:** add/commit toàn bộ thay đổi Phase 1.1, merge vào chore/modernize-android-build, push nhánh này lên origin và xóa test branch. Windows PowerShell, cwd AndroidDvbDriver; không có hardware/build/runtime mới.
- **Trước:** test/android-runtime-smoke tại 6da609b; 15 file candidate đúng phạm vi gồm workspace/ignore, 2 instructions, 3 Activity + SystemBarInsets, test native + test module dependency và 5 hồ sơ dự án. Index trống; không chạm reference repositories hoặc main master.
- **Preflight:** status/branch-vv/remotes/log, staged diff --check với cr-at-eol, heuristic credential scan có 0 match, 0 build outputs/firmware/captures/localproperties trong candidates. Không coi heuristic là security audit. Instrumentation XML đã lưu có 2 tests, 0 fail, 0 error và lint XML có 0 errors; không khởi emulator để test lại code đã được kiểm chứng ở Phase 1.1.
- **Commit:** b51a77e18e363750692d83cc53974b64718e1f51, `fix: stabilize Android runtime after toolchain upgrade`, 15 files/271 insertions/3 deletions, kèm Co-authored-by trailer. Không squash hoặc amend commit cũ.
- **Merge:** checkout chore/modernize-android-build khi working tree sạch; `git merge --ff-only test/android-runtime-smoke` thành công, 6da609b→b51a77e, không tạo merge commit hoặc viết lại lịch sử.
- **Push:** push origin theo upstream branch hiện tại. `git ls-remote --heads origin refs/heads/chore/modernize-android-build refs/heads/test/android-runtime-smoke` xác nhận remote modernization ở b51a77e và không có test branch remote. Không force-push hoặc thay master/upstream.
- **Delete:** chỉ sau khi xác minh remote, `git branch -d test/android-runtime-smoke` thành công; local còn modernization/master. Không cần remote delete vì branch test chưa tồn tại trên origin.
- **Hồ sơ cuối:** STATUS/WORKLOG cập nhật trên modernization ở commit docs riêng để không giữ hiện trạng “chưa commit/push”; mã commit docs cuối tra git log. Kiểm lại remote sau push docs. Không đưa raw log/screenshot/APK vào Git.
- **Next:** giữ dừng Phase 1.1, review kế hoạch coverage API 21/arm64/16 KiB hoặc bước port driver riêng theo yêu cầu mới; không tự triển khai/kiểm thử phần cứng.

## 2026-11-02 22:37–23:18 +07 — Phase 1.2: AGP 9.5 và kiểm kê toolchain

- **Scope:** người dùng cho nâng riêng AGP 9.4.1→9.5.0, sửa kiểm kê/JDK/AVD, kiểm build/test và chuẩn hóa hồ sơ; giữ Gradle 9.8.2/compile 37.2/target 37/min 21/NDK 30.0.16248370/CMake 4.1.2/Java 8. Không commit/merge/push, không cài/gỡ/dọn SDK, không thay references/hardware.
- **Git/environment:** Windows PowerShell 7.6.9/Windows 10.0.19045, cwd AndroidDvbDriver, nhánh chore/modernize-android-build, HEAD ace7e9f187d11a685975f707b6743d779fa847d9. Ban đầu có 6 thay đổi local của người dùng: 2 instructions modified + instruction toolchain-lifecycle/skill toolchain-audit/2 scripts inventory-review untracked. Đã đọc và sửa bổ sung có mục tiêu, không ghi đè toàn file hoặc reset/stash/viết lại lịch sử.
- **Skills/instructions:** skill tool dispatcher chưa nhận toolchain-audit (not found), nhưng đã tìm đúng `.github/skills/toolchain-audit/SKILL.md` và đọc trực tiếp. Load project-handoff/android-cli/agent-customization, đọc 7 scoped instructions, project skills và hồ sơ mới nhất. GitNexus:list_repos vẫn không có target index, không rebuild; ADB MCP dùng Proxy hiện có, không tạo connection/config mới.

### Preflight và bản stable

- `web_fetch` đọc Google AGP API reference, Current Release 9.5.0; notes 9.5 có final 9.5.0 entry và đúng Google Maven POM 9.5.0. Các URL ở ADR-009; kiểm ngày 2026-11-02. Compatibility: Gradle >=9.6/JDK >=17/API 37; ma trận Java của Gradle 9.8.2 hỗ trợ JDK 25 từ 9.1.0. Không suy default NDK 28/BT 36 là yêu cầu downgrade project pins 30/37.
- Đối chiếu Android CLI qua --help, thông tin SDK, danh sách SDK, tìm tài liệu AGP 9.5 và source.properties: còn platform 34/37.2, BT 37, NDK 30, CMake 4.1.2, cmdline 23, ADB 37.0.1, emulator 37.2.13; giữ image 34 TV x86/37.0 Google APIs x86_64. Người dùng đã dọn platform 37.0/BT 36/NDK 28/CMake 3.22.1; phase không xóa thêm. Image revision local 7 khác catalog in 8; ghi rõ metadata local, không update toàn SDK.
- JAVA_HOME của Process cũ trỏ tới jdk-25 không tồn tại, User trống, Machine C:\Program Files\Java\latest\jdk-25 hợp lệ. Child build nạp Machine JAVA_HOME, ANDROID_HOME=D:\Android\Sdk/ANDROID_AVD_HOME=D:\Android\AVD; wrapper --version xác nhận Launcher 25.0.5.0.3/daemon resolved JDK 25.0.5.0.3. Không đổi global env/IDE.
- Audit lần đầu, lượt 1043, bị RemoteSigned chặn. Đã hỏi; người dùng chỉ định rõ `Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process`. Các lệnh script dùng try/finally lưu và restore policy cũ trong child Process, không thay User/Machine. Process sau đó là Undefined, CurrentUser/LocalMachine vẫn RemoteSigned. Không UnblockFile hoặc tự bypass mặc định trong script/task.

### Lỗi kiểm kê và sửa trực tiếp

- Audit lượt 1044 đọc CMake đúng 4.1.2 nhưng AVDNames ra 2 tên (TBS5530_Runtime_API37 và TBS5530_Runtime_API37.avd), emulator 0/metadata disk 2. Nguyên nhân: User AVD home đã resolve chỉ dùng đọc đĩa, không truyền vào subprocess; DirectoryInfo.BaseName giữ nguyên suffix .avd.
- Sửa GetAvdInventory để truyền home đã resolve vào emulator-list-avds, restore trong finally; normalizeName bỏ suffix .avd rồi dedupe. Lượt test 1046 phát hiện restore null qua SetEnvironmentVariable để lại biến empty-string, assertion FAIL; sửa Remove-Item Env cho biến ban đầu vắng mặt, giữ các giá trị khác. Assertions ở lượt 1047/1050/1056/1068 PASS: emulator 1/metadata 1/một tên đúng, không rò env ra caller.
- Thêm -PassThru cho JSON object pipeline và JDK candidates/effective build candidate; vẫn báo Process stale, không tự đổi JDK. CMake parser của cleanup review được giới hạn trong externalNativeBuild.cmake, không match version của block khác. `scripts/check-env.ps1` dùng chung audit với -NoReport, tránh hai implementation lệch nhau.
- Thêm tools/tests/ToolchainInventory.Tests.ps1, không cài framework: AST chỉ load các function đã review; fixture script tạm/list AVD kiểm CMake sai scope, dedupe, child env, restore env khi vắng/có sẵn và fallback metadata khi thiếu emulator. Fixture được dọn trong finally. Parser có 0 errors và suite PASS thực. Review cleanup ở lượt 1047 chỉ in KEEP/REVIEW và CSV ngoài Git, không uninstall.
- Inventory JSON trước/sau ở parent logs/toolchain; latest toolchain-inventory-20261102-231414.json xác nhận AGP 9.5/AVD 1/CMake 4.1.2. Báo cáo chứa đường dẫn/host metadata, không commit hoặc đăng raw report làm artifact. Instructions/skill tiếng Anh cập nhật behavior/safety, không hardcode một phiên bản là latest mãi mãi; README ghi baseline 9.5 kèm ngày.

### Nâng AGP và validation build

- Root build.gradle chỉ đổi application/library plugin 9.4.1→9.5.0. Giữ nguyên wrapper/checksum, pins của 4 modules/dependencies/CMakeLists/source/runtime helpers Phase 1.1. Không port driver.
- Lệnh lượt 1048: `gradlew.bat :app:assembleDebug :app:assembleRelease :usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest :app:lintDebug --console plain --no-daemon --max-workers=1` FAIL sau 12m9s ở native Debug x86: Unable to delete directory. Lỗi khóa output trên Windows đã được quan sát trước, không chứng minh AGP không tương thích; không xóa cache/kill process khác/skip ABI.
- Rerun lượt 1059: `gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintDebug :usbxfer:testDebugUnitTest --rerun :drivers:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun --console plain --no-daemon --max-workers=1` **exit 0/9m12s**, 325 tasks, 245 executed/80 UP-TO-DATE. Unit XML mới: usbxfer 1/drivers 17/dvbservice 13 = 31 methods, 0 failure (1 ConvertCString utility), không chỉ dùng PASS từ cache. Lint XML: 0 errors, 11 warnings; không disable test/assertion/lint gate.
- AAB ở lượt 1067: `:app:bundleRelease --console plain --no-daemon --max-workers=1 --warning-mode all` **exit 0/45s**, 157 tasks, 18 executed, 139 UP-TO-DATE. Cảnh báo Configuration.setVisible deprecated vẫn còn trên AGP 9.5 dù notes đề cập fix; Javac 25 Java 8 obsolete cũng vẫn có, không ẩn hoặc đổi bytecode.
- Kiểm native tĩnh ở lượt 1065/1066: zipalign37 -c -P16 4 cho cả debug/release exit 0; APK có libUsbXfer.so đủ arm64-v8a/armeabi-v7a/x86/x86_64; llvm-readelf NDK 30 kiểm mới cả Debug/RelWithDebInfo cho 4 ABI, 64 bit LOAD 0x4000/32 bit 0x1000. Một phép dò ELF có regex sai escape ở lượt 1065 đã dừng; sửa query lọc Directory.Name ở lượt 1066 PASS, không ảnh hưởng source/binary.
- Debug APK SHA256 6D6E1C4953F708CA04E9DC5AD223168514C3745F27106E3AF12033C884D44D53, release unsigned APK 7B126F04109EA1F9295336EF8615C8ACCC3743BE8488497BF8DE7EA40AAB52F3; không ký/upload.

### AVD/instrumentation/UI regression

- Dùng AVD hiện có TBS5530_Runtime_API37 tại D:\Android\AVD, không tạo mới/wipe/download. Boot lượt 1051 với flag giống Phase 1.1: -port5554 -no-snapshot -no-boot-anim -no-audio -no-window -gpu swiftshader_indirect. Lượt chờ 1052 timeout 4 phút, FAIL ban đầu; sau đó lượt 1054 ADB có device/getprop boot 1, không reset/rút data.
- Runtime Android 17/API 37/x86_64/page 4096; kiểm tường minh emu avd name đúng. Lượt 1055: ANDROID_SERIAL=emulator-5554, `:dvbservice:connectedDebugAndroidTest --console plain --no-daemon --max-workers=1` **exit 0/5m38s**, 92 tasks, 43 executed, 49 UP-TO-DATE. XML có 2 tests, 0 fail, 0 error, gồm allDevicesAreInXml và native load without USB. Copy XML ngoài Git: phase-1-2/instrumentation-results.xml, SHA256 03269F9A8C91B106FF415A015756384CB0CA3EC4A0CDDE15AC0B3341534B02A6.
- Install APK debug AGP 9.5 Success; am start Welcome COLD: Status ok, TotalTime 10080 ms. UI bị overlay ANR của launcher com.google.android.apps.nexuslauncher/SystemUI che; chọn Wait theo hierarchy MCP, không force kill hoặc đổi system settings. Sau cùng Welcome focus đúng, top inset 63/bottom 63 như Phase 1.1.
- Diagnostic→Start→dialog No devices found và DvbException:64 đã được xử lý; PID 5345 sống, filtered log có 0 FATAL/UnsatisfiedLinkError/SecurityException/app ANR; Back về Welcome. Không chạy service foreground/USB permission vì không có tuner. Không quy ANR của system thành app crash hoặc tuyên bố emulator ổn định tuyệt đối.
- Xác nhận đúng AVD name, rồi lượt 1064 `adb -s emulator-5554 emu kill`; emulator ở lượt 1051 exit 0, ADB devices ở lượt 1066 rỗng. Không xóa AVD data, không dừng physical device khác.

### Bàn giao và điểm dừng

- Cập nhật hồ sơ STATUS/ENVIRONMENT/DECISIONS/TEST-MATRIX/WORKLOG; giữ nguyên lịch sử research, các nguồn Google mới ở ADR-009. Git history/branch/HEAD giữ nguyên ace7e9f, chưa stage/commit/merge/push. Không đụng reference code/index, không ghi usbipd/TBS firmware/I2C/reset/LNB/TS.
- Baseline AGP 9.5 chỉ được coi là đã kiểm chứng cho dự án ở các gate đã chạy trên máy này; latest stable có thể đổi sau ngày kiểm tra. Runtime API 21/arm64/16 KiB, USB host thật/foreground streaming và TBS driver vẫn NOT RUN.
- Next: review diff Phase 1.2 và commit có chọn lọc khi được phê duyệt; theo dõi process env stale/native output lock/emulator ANR, không tự port driver hoặc dọn SDK.

## 2026-11-02 23:39 +07 — Checkpoint commit/push Phase 1.2 theo yêu cầu

- Người dùng yêu cầu add, commit và push; nhánh hiện tại chore/modernize-android-build, HEAD trước ace7e9f187d11a685975f707b6743d779fa847d9. Origin SSH trankien84/AndroidDvbDriver, upstream signalwareltd không thay đổi.
- Preflight Git status: 15 candidate files, index trống; root plugins AGP 9.5.0, phạm vi audit/test/instructions/docs như Phase 1.2. `git ls-remote --heads origin refs/heads/chore/modernize-android-build` xác nhận remote cũng ở ace7e9f trước push. Không fetch/rebase/merge/reset.
- Diff --check với cr-at-eol PASS; 0 candidate build outputs/SDK/firmware/TS/localproperties; heuristic secret pattern có 0 match, không coi là security audit. Đã đọc lại XML đã lưu: unit 31 methods, 0 fail; instrumentation 2 tests, 0 fail, 0 error; lint 0 errors. Không chạy test/build/AVD mới trong tác vụ Git này.
- Chuẩn bị commit `build: upgrade AGP to 9.5.0 and fix toolchain inventory`, kèm Co-authored-by trailer. STATUS thêm checkpoint để phân biệt lịch sử chưa commit; hash chính commit đọc từ Git log sau tạo, không tự ghi SHA vòng lặp vào commit.
- Push được phê duyệt chỉ cho nhánh hiện tại lên origin, không force/thay master/upstream/reference; xác minh live remote SHA trùng HEAD và working tree sạch sau thao tác. Kết quả publish báo trực tiếp sau xác minh, không ghi trước là thành công.
- Next: dừng Phase 1.2; coverage Android API 21/arm64/16 KiB/hardware vẫn NOT RUN, không tự port driver hoặc dọn SDK.

## 2026-11-03 00:02–00:18 +07 — Phase 2.0: source readiness và thiết kế driver

- **Mục tiêu và giới hạn:** chỉ xác minh nguồn, hoàn thiện thiết kế và kế hoạch Phase 2.1. Không viết code production hoặc test implementation, không build, không gửi USB writes, không sửa reference repositories.
- **Môi trường:** Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`. Baseline main `chore/modernize-android-build` / `4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe`, working tree sạch. Đọc guidance và các phần hồ sơ mới nhất; load skills tbs-research/project-handoff.
- **Nhánh:** sau kiểm status sạch, chạy `git switch -c docs/tbs5530-source-architecture` thành công, cùng HEAD baseline; không chạm nhánh modernization, không stage/commit/push/merge. Các file thay đổi chỉ là RESEARCH/DECISIONS/STATUS/TEST-MATRIX/WORKLOG.

### Kiểm tra mới về repository và objects

- Tất cả Git reads trên references dùng `--no-lazy-fetch`, `GIT_NO_LAZY_FETCH=1`, `GIT_OPTIONAL_LOCKS=0`. Không chạy sync script, fetch, restore, reset, clean, clone/reclone, config write hoặc tải objects.
- `rev-parse --show-toplevel`, `rev-parse HEAD`, `branch --show-current`, `remote -v`, `status --porcelain=v1 -uall`: TBS latest/2ce787de9f6e81b4294692a45de15596ae2e1322 có 81.399 staged D; C main/c5fa27eecbe80633cbb365cc44656daea1c0e2e3 và Go main/11228b05fb1626bf41c4e1879ace10cd0a8d575c sạch. Origin cả ba đúng nguồn đã khóa.
- `Test-Path .git/index/.git/index.lock`, `GIT_INDEX_FILE`, `ls-files`, `sparse-checkout list`, `config --get core.sparseCheckout/core.sparseCheckoutCone`: TBS thiếu index/lock, không override index, ls-files trả 0, đủ sáu patterns và flags true. Đếm file đệ quy: cả sáu thư mục đều có 0 file.
- `ls-tree -r HEAD -- <six-paths>`, `cat-file --batch-check`: 785 paths/785 unique blobs, 0 missing, 15.372.887 bytes uncompressed. Kiểm thêm các cone ancestor root/drivers/media/usb/include/uapi/linux: 572 paths/572 blobs, **0 missing hiện tại**. Không lặp lại kết luận cũ cần tải 572 blobs; chưa xác định ai bổ sung trước phase, tác vụ này không tải.
- Tổng cone dự kiến 1.357 file; kiểm cơ bản tên reserved Windows/ký tự không hợp lệ có 0 lỗi. AUX ngoài sparse vẫn có trong HEAD tree. `config --show-origin --get core.protectNTFS` không có override lưu, exit 1.
- `read-tree --dry-run -m -u HEAD` không ghi: exit 128 `invalid path drivers/gpu/drm/nouveau/nvkm/subdev/i2c/aux.c`; sau đó index/lock vẫn vắng. Không chạy workaround protectNTFS=false hoặc recovery thực ở phase này.
- Board tbs5530.c/.h, Sony cxd2878.c/.h/_priv.h, Montage m88rs6060.c/.h/_priv.h, dvb-usb common/init/firmware/usb-urb, media frontend/math và Linux headers trực tiếp: tree/blob local đọc được, disk thiếu. Makefile bindings kiểm đúng TBS/CXD/Montage. Không khẳng định full kernel build có đủ mọi dependency.
- Source readiness phân đôi: **PASS cho đọc nguồn phục vụ nghiên cứu qua local blobs**, **BLOCKED cho checkout/index/materialized tree**. Kế hoạch backup/rollback và NTFS vs ext4 WSL nằm ở RESEARCH, cần phê duyệt riêng trước mọi ghi. Không tạo WSL checkout hoặc đổi SDK.

### Nghiên cứu mở rộng và kiến trúc

- Dùng kết quả Phase 0 làm nền, đọc lại official bằng `git show HEAD:<path>`/`cat-file blob`; source C/Go đọc bằng view/grep trên đĩa. Ghi file/function/SHA và links cố định trong RESEARCH. Không đồng bộ upstream để thay mốc đang nghiên cứu.
- Làm rõ generic Cypress hexline loader khác loader raw device-specific TBS; framework no_reconnect chỉ tiếp tục sau upload thành công, không bảo đảm Android identity/permission/fd ổn định. Firmware provenance/hash/license/address map vẫn chưa có bằng chứng.
- Official/C manual PLP ghi bank 23/AF/AD mỗi set_dvbt2. Go active-same-system retune chỉ đổi bandwidth; ghi nhận khác biệt nguồn, không claim đã tái hiện lỗi trên tuner thật. Internal manual PLP 0 được đề xuất cho compatibility, không gọi là auto; public PLP API và TCP extension chưa chọn triển khai.
- Chip dispatch official chỉ có 0x197 Ascot3 và 0x19c/0x39a Freia; community macros mở rộng ID; Go mặc định các ID còn lại là Freia. Thiết kế từ chối ID ngoài bảng được review, không mặc định thiết bị người dùng có Ascot3 chỉ từ tên board.
- Đọc Android registry/Creator, DvbUsbDevice, DvbDevice/DataPump, Retry, I2cAdapter, UsbPermissionObtainer, UsbBulkSource/JNI và service/request enums. Phát hiện Retry.retry(4) là 5 attempts, không phải 4; I2cAdapter có 10 iterations. Điều chỉnh adapter production tương lai thành extends DvbDevice để tránh open/close final và lặp lại tác dụng phụ; kết hợp phần đang có thay vì refactor toàn framework.
- Phase 2.1 đề xuất chỉ plain-Java core ControlIo/Bridge/FirmwarePlan/Session + scripted fakes/tests; chưa có Android transport/registry/firmware executor/frontend production/JNI. Test matrix gồm exact bytes, short transfer, conservative limits, firmware tail/address policy, endpoint ordering, fault latch, late callbacks và cleanup; tất cả **PLANNED/NOT RUN**.
- Native streamActive tách khỏi frontendLocked; stream-before-tune của app hiện hữu phải được tính đến. Cleanup không giữ control lock khi join, không fd giả/ioctl thật và không corrective vendor write tự động. API enums/Request ordinals của các driver hiện hữu giữ nguyên.

### Công cụ, lỗi lệnh và bằng chứng

- MCP Proxy GitNexus list_repos không có target index; AgentMemory lookup limit 3/compact không có match; không save memory hoặc build index. Không dùng graph rỗng để khẳng định phạm vi ảnh hưởng an toàn.
- Hai lệnh PowerShell probe ban đầu lỗi parser (`$f:$s` và block ngoặc không khép) đã sửa thành `${f}`/loop đơn giản rồi chạy lại thành công; lỗi parser không gửi lệnh vào Git hoặc hardware. Một output source lớn được đọc lại bằng range/grep có giới hạn, không dump toàn bộ file.
- Các đoạn bảng/timing/register trong RESEARCH là source facts hoặc proposed policy, không hardware results. Không tải firmware, không usbipd/lsusb/PyUSB/ADB/emulator, không thay environment/global settings.
- ADR-010 ghi thiết kế đã chọn và phương án thay thế; lịch sử ADR-006/Phase 0 vẫn giữ, lý do đổi hướng lifecycle nêu rõ. STATUS không gọi toolchain baseline cũ là chưa push; baseline 4cf1b64 giữ nguyên.
- **Next:** review và xin duyệt phạm vi Phase 2.1 offline foundation; nếu muốn materialize TBS checkout thì duyệt kế hoạch recovery riêng. Dừng Phase 2.0, không tự viết driver production hoặc kiểm thử USB thật.

- **Kiểm chứng tài liệu cuối Phase 2.0 (00:21 +07):** diff --check PASS; cả năm hồ sơ UTF-8 hợp lệ, 0 liên kết Markdown tương đối hỏng; VS Code problems không lỗi. Chỉ năm file docs được sửa, 0 staged/0 untracked; HEAD nhánh tài liệu và modernization cùng 4cf1b64. Reference SHA/status không đổi so đầu phase. Không chạy build/test mới vì chỉ sửa tài liệu.

## 2026-11-03 00:54 +07 — Kiểm chứng người dùng phục hồi TBS checkout và dọn script tạm

- Người dùng gửi ảnh terminal Apply PASS, backup tbs-sparse-before-20261103-004911-951. Agent không chạy recovery hoặc ghi reference; kiểm riêng bằng Git/filesystem chỉ đọc.
- TBS HEAD 2ce787de9f6e81b4294692a45de15596ae2e1322/latest, status có 0 entries, index tồn tại với 8.822.766 bytes, không có index.lock. Sáu sparse paths giữ nguyên, config protectNTFS không có override lưu. C/Go status sạch.
- Expected manifest từ ls-tree của sáu paths và ancestor cone: 1.357 file; thực tế 1.357 file, 0 missing/extra. Batch hash-object --stdin-paths theo filters/EOL: 0 mismatch. TBS5530/Sony/Montage/framework source hiện diện; số lượng chi tiết trong RESEARCH. Không có build full kernel/hardware mới.
- Backup/git giữ nguyên, after.json PASS/1357/đúng SHA. Theo yêu cầu trước của người dùng, đã xóa **chỉ** script tạm session Restore-TbsSparseCheckout-Reviewed.ps1 sau khi đủ gate, xác nhận file không còn; không xóa backup/logs/AVD/source khác.
- Main vẫn docs/tbs5530-source-architecture/4cf1b64, không stage/commit/push. STATUS/RESEARCH/TEST-MATRIX/WORKLOG thêm kết quả mới, không xóa lịch sử lỗi. Next: xin duyệt Phase 2.1 offline foundation, chưa tự viết driver production hoặc gửi USB writes.

## 2026-11-03 00:58 +07 — Commit/push hồ sơ Phase 2.0 theo yêu cầu

- Scope: năm file STATUS/RESEARCH/DECISIONS/TEST-MATRIX/WORKLOG, gồm thiết kế Phase 2.1 offline và xác minh 1.357 file checkout NTFS. Không có code production, reference sources, backup hoặc raw logs.
- Trước: nhánh docs/tbs5530-source-architecture tại 4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe, năm tài liệu modified; index trống. Modernization giữ nguyên baseline. Origin đúng fork trankien84/AndroidDvbDriver; nhánh tài liệu chưa có trên origin tại preflight.
- Validation: diff --check PASS, UTF8/liên kết Markdown tương đối PASS, heuristic secret pattern có 0 match; không phải security audit. Không chạy build/unit/emulator/hardware trong tác vụ tài liệu.
- Chuẩn bị commit `docs: finalize TBS5530 architecture and source readiness`, có Co-authored-by trailer, sau đó push explicit nhánh hiện tại với upstream tracking. Không merge, force-push hoặc thay nhánh modernization/master. Hash và kết quả push báo sau kiểm tra live remote; không ghi trước thành công.
- Next: dừng trước Phase 2.1, chỉ triển khai offline foundation khi được duyệt; không USB writes hoặc firmware.

## 2026-11-03 10:39 +07 — Phase 2.1: TBS5530 offline USB protocol foundation

### Preflight và phạm vi

- Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`. Đọc instructions/scoped build/hardware/records, skills tbs-research/project-handoff và hồ sơ Phase 2.0/ADR-010. Git sạch tại `docs/tbs5530-source-architecture`/`8588ee7661003052b7d1a0353e23bb3a3dcef8b5`; tạo `feat/tbs5530-offline-protocol`, không đổi modernization.
- Ba references kiểm lại bằng `git --no-lazy-fetch -C <reference> rev-parse HEAD` và `status --porcelain=v1 -uall`, với `GIT_NO_LAZY_FETCH=1`, `GIT_OPTIONAL_LOCKS=0`: TBS 2ce787de9f6e81b4294692a45de15596ae2e1322, C c5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go 11228b05fb1626bf41c4e1879ace10cd0a8d575c; cả ba có 0 status entries. Đọc lại board/protocol/firmware/chip source, không fetch/sync/ghi config/index.
- GitNexus được kiểm qua gateway: không có index của project; không tự analyze hoặc suy empty graph là không có callers. Thay bằng đọc source, search và unit/regression tests. Không gửi code lên dịch vụ bên thứ ba.
- Toolchain pins giữ nguyên. Child build nạp Machine JAVA_HOME hợp lệ thay process cũ; không sửa User/Machine environment hoặc ExecutionPolicy, không cài/gỡ package/SDK.

### Thay đổi

- Tạo 4 main classes: `Tbs5530ControlIo`, `Tbs5530Bridge`, `Tbs5530FirmwarePlan`, `Tbs5530Session`; tạo fake `ScriptedFakeControlIo` và 3 test suites cùng package tbs5530. Tổng 8 file Java mới, SPDX GPL-2.0-or-later; không có firmware blob hoặc source tables từ reference.
- Bridge kiểm parameters/buffers/flags trước I/O, khóa cặp read, fault latch/no retry, đúng endpoint 82. Phân loại return count lớn hơn length thành I/O failure, không short transfer. Kiểm state/generation sau transfer để detach callback không gửi read tiếp hoặc trả dữ liệu cũ.
- Session giữ streaming khác lock, cleanup đúng ownership kể cả failure và exception; generation cũ không tác động session mới. Plan firmware immutable chỉ dùng synthetic bytes/hash/ranges/chunks; test executor không nằm trong production core.
- Bổ sung ADR-011 cho caller-owned late callbacks, explicit READY/permission timeout và giới hạn synchronous cancellation. Hoãn packet-reader/PLP-retune register tests đến component tương ứng; không giả lập một driver hoàn chỉnh để có PASS.
- Cập nhật STATUS/RESEARCH/DECISIONS/TEST-MATRIX/WORKLOG. Không sửa registry/XML, DvbDevice/DvbUsbDevice/Retry, TCP/enum, driver cũ, Gradle/native hoặc dependency manifests.

### Lệnh kiểm thử thực và kết quả

Các lệnh Gradle đều chạy ở cwd trên, chỉ sửa environment của child:

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_AVD_HOME = 'D:\Android\AVD'
```

- Các lượt kiểm thử tập trung trong quá trình phát triển PASS; snapshot trước 3 tests cuối có 49 methods mới. Không dùng số này làm tổng cuối. Lượt regression đầu PASS 80 methods/2m20s; sau đó thêm assertion cleanup/runtime exception/detach callback và chạy lại toàn bộ.
- **Lệnh cuối**, buộc chạy lại cả ba unit tasks:

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

- **PASS exit 0, BUILD SUCCESSFUL in 1m45s; 177 actionable tasks: 41 executed/136 up-to-date.** XML cuối: Bridge 17/Firmware 19/Session 16 = **52 methods mới**; drivers 69/usbxfer 1/dvbservice 13 = **83 methods**, 0 failures/0 errors. Debug APK thành công. Lint XML có 0 Error/0 Fatal/11 Warning; không thêm suppression hoặc bỏ assertion. Cảnh báo JDK 25 source/target 8 obsolete và Gradle deprecated features vẫn hiển thị.
- Evidence local: `drivers\build\test-results\testDebugUnitTest\TEST-info.martinmarinov.drivers.usb.tbs5530.*.xml`, các XML module usbxfer/dvbservice, `app\build\reports\lint-results-debug.xml`, `app\build\outputs\apk\debug\app-debug.apk`. Không commit outputs/raw logs.
- Kiểm độc lập Java 8 sau thay đổi cuối: gọi JDK Machine `bin\javac.exe --release 8 -d drivers\build\tbs5530-java8-check <4corefiles>`, không Android/JNI classpath: exit 0. `javap -verbose -classpath <output> info.martinmarinov.drivers.usb.tbs5530.Tbs5530Bridge` trả major version 52. Tìm Android/AndroidX imports/native declarations có 0 match; IDE problems có 0 errors. Đã xóa riêng thư mục compile kiểm tra này, xác nhận không còn; không tạo script tạm cần giữ.

### Bàn giao và điểm dừng

- Kiểm cuối: `git diff --check` PASS; đọc UTF-8 strict cho 8 file mới/5 hồ sơ, kiểm trailing whitespace của file mới và liên kết Markdown tương đối PASS. `git status --short --untracked-files=all` chỉ có 5 hồ sơ modified/8 file Java untracked; `git diff --cached --name-only` rỗng. Modernization vẫn ở 4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe. Không có lỗi IDE trong hai package mới.
- Chưa stage/commit/push/merge. Năm hồ sơ modified và tám file Java mới; binaries/SDK/reference không đưa vào Git.
- Không emulator/instrumentation mới, không Windows/WSL/Android USB access, firmware thật/vendor/I²C/LNB/reset/usbipd hoặc tuner tests. Native cancellation/Android permission/reconnect/lock/TS và firmware rights vẫn NOT RUN/chưa duyệt.
- Review core và ADR-011 là bước kế tiếp. Phase 2.2 chỉ đề xuất lifecycle adapter với fake/instrumentation không tuner, cần phạm vi được duyệt; chưa bắt đầu frontend hoặc transport hardware.

## 2026-11-03 10:46 +07 — Checkpoint commit và tích hợp Phase 2.1 theo yêu cầu

- Người dùng phê duyệt add/commit, merge vào `chore/modernize-android-build`, push origin đúng nhánh đó và xóa nhánh feature local sau xác minh. Không xóa nhánh tài liệu hoặc remote branch.
- Preflight Windows PowerShell/main repo: feature `feat/tbs5530-offline-protocol`/8588ee7661003052b7d1a0353e23bb3a3dcef8b5; target local và `git ls-remote --heads origin chore/modernize-android-build` cùng ở 4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe. `rev-list --left-right --count target...HEAD` trả 0/1: target là tổ tiên; fast-forward sẽ gồm checkpoint Phase 2.0 và commit mới Phase 2.1.
- Status chỉ có 5 hồ sơ modified và 8 file Java mới đã kiểm thử; `git diff --check` PASS. Không build/test/hardware lại trong tác vụ Git. Kết quả 83 unit methods/debug/lint ở mục Phase 2.1 vẫn là lần chạy thực gần nhất, không ghi thành một lần PASS mới.
- Staged diff check đầu tiên phát hiện CRLF/mixed EOL trong 8 file Java mới (core.autocrlf=false). Commit chưa chạy vì gate chặn. Chỉ chuẩn hóa 8 file này thành LF, kiểm nội dung không đổi ngoài EOL; stage lại, `git diff --cached --check` PASS và ls-files --eol đều i/lf,w/lf. Không sửa Git config hoặc bỏ whitespace gate; không chạy lại tests vì không đổi logic.
- Commit dự kiến `feat: add offline TBS5530 USB protocol foundation`, có Co-authored-by trailer. Chỉ stage 13 file trong hai package tbs5530 và 5 hồ sơ. Sau commit: switch target, merge --ff-only feature, push explicit target không force; kiểm remote SHA/working tree rồi branch -d feature. Hash và kết quả thực báo sau thao tác, không ghi trước thành công.

## 2026-11-03 11:25 +07 — Phase 2.2: lifecycle và fake adapter

### Khôi phục và checkpoint tích hợp

- Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`. Đọc project/scoped instructions, STATUS/ENVIRONMENT/RESEARCH/DECISIONS/TEST-MATRIX và WORKLOG mới nhất; dùng project-handoff. Không đổi toolchain hoặc reference sources.
- Preflight main sạch, nhánh `chore/modernize-android-build`, HEAD `6eb23537484a3e1dd263bd5a88f56914c7c5e313`. Xác nhận commit này đã chứa Phase 2.1/ADR-011, fast-forward/push ở tác vụ trước. Nhánh local feature/docs và remote docs đã xóa theo yêu cầu riêng trước Phase 2.2. Không commit/merge/push/reset/switch trong phase này.
- Ba references chỉ đọc qua `git --no-lazy-fetch -C <reference> rev-parse HEAD` và `status --porcelain=v1 -uall`, với GIT_NO_LAZY_FETCH=1/GIT_OPTIONAL_LOCKS=0: official 2ce787de9f6e81b4294692a45de15596ae2e1322, C c5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go 11228b05fb1626bf41c4e1879ace10cd0a8d575c; cả ba có 0 entries lúc đầu/cuối. Không fetch/ghi index/config.
- GitNexus list_repos qua gateway không có index project; không tự rebuild và không coi empty graph là an toàn. Đọc source hiện tại, diff và assertions làm bằng chứng. Không chia sẻ source lên dịch vụ bên thứ ba.
- Đọc build/wrapper hiện tại: AGP 9.5.0/Gradle 9.8.2/checksum giữ nguyên, compile 37.2/min 21/source-target 8. SDK platform source.properties: revision 2/ApiLevel 37.2/PreviewSdkInt 0. Child dùng Machine JAVA_HOME có java.exe, SDK D:\Android\Sdk; không đổi biến User/Machine/ExecutionPolicy, không cài package.

### Test tái hiện trước khi sửa

Thêm Tbs5530ConcurrencyTest: fake transfer thread A bị CountDownLatch giữ; thread B nhận detach hoặc cancel. Hai tests đòi invalidation hoàn tất mà không đợi transfer. Chạy trên production core baseline chưa sửa:

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:ANDROID_HOME = 'D:\Android\Sdk'
.\gradlew.bat :drivers:testDebugUnitTest --tests '*Tbs5530ConcurrencyTest' --console plain --no-daemon --max-workers=1
```

**FAIL được tái hiện: 2 tests/2 failures, exit 1, 36s/23 tasks, 2 executed.** Assertion tại invalidated.await không đạt. Đây là defect/giới hạn session monitor đã nêu trong ADR-011, không phải test hardware và không bỏ test để sửa kết quả.

### Thay đổi và xử lý race

- Sửa Tbs5530Session/Tbs5530Bridge: transactionLock riêng, state monitor ngắn; cả cặp 0x90/91 vẫn atomic. Admission và active transaction tracking giữ tài nguyên tới khi thao tác vật lý hoàn tất; invalidation nhanh không đóng connection còn dùng. Kiểm generation/state sau transfer và trước công bố dữ liệu đọc; không retry/vendor recovery.
- Close/detach/cancel đánh dấu pending cleanup; transaction cuối nhả cả hai khóa rồi cleanup cancel→release→close. Công bố cancellationRequested/cleanupPending/cleanupFailure; deferred failures được gắn suppressed trên transfer error, immediate failures throw cho caller. Reopen bị chặn khi pending; concurrent close/callback chỉ có một owner.
- Thêm readiness events/getters firmwareUploaded/frontendInitialized, giữ ready/lock/stream khác nhau; lock yêu cầu frontend initialized. `failed` hỗ trợ permission/acquisition errors trước khi nhận connection. Bổ sung 1 Session test và tăng assertion cũ, không giảm coverage.
- Thêm JVM-only Tbs5530LifecycleCoordinator và 32 tests: Request gắn attachment identity + generation; ports permission/open/claim; claim receipt; local lease cho acquisitions bị từ chối/mới hoàn tất một phần; callback đúng một lần; stale callback không đóng resource mới. Coordinator không giữ monitor qua callback vật lý, không tự READY. Chưa có framework binding/transport cụ thể.
- Concurrency suite cuối có 10 methods, gồm blocked pointer detach/cancel; deferred errors; late timeout; queued stale transaction; reentrant detach; lỗi giữa 0x90/91; join worker ngoài khóa; concurrent close trong cleanup; 40 vòng race có latch. Không sleep tùy tiện hoặc tăng timeout để che test thiếu ổn định.
- Thêm ADR-012 và cập nhật năm hồ sơ; không sửa FirmwarePlan, protocol bytes, registry/XML, driver cũ/Retry, wire enums/Request, native/JNI hoặc dependencies.

### Validation cuối

Sau tách khóa: focused Bridge/Session/Concurrency PASS 35 methods trong 42s. TBS package + debug sau coordinator PASS. Full regression đầu PASS 125 methods; sau khi thêm concurrent-close-during-cleanup test đã chạy lại toàn bộ, không dùng count 125 làm kết quả cuối.

VS Code task `Android: assembleDebug (Windows Gradle Wrapper)` trả Task not found; fallback Gradle wrapper cùng cwd, không sửa tasks/config. Lệnh cuối:

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_AVD_HOME = 'D:\Android\AVD'
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit 0, BUILD SUCCESSFUL in 2m23s; 177 actionable tasks: 41 executed/136 up-to-date.** XML cuối: drivers 112/usbxfer 1/dvbservice 13 = **126 methods, 0 failure/0 error**. TBS suites: Bridge 17/Firmware 19/Session 17/Concurrency 10/Coordinator 32 = 95 methods (thêm 43 methods so với baseline). Debug APK PASS; lint XML có 0 Error/0 Fatal/11 Warning. Warning Java 8 obsolete/Gradle deprecated vẫn hiện, không suppress.

Stress độc lập dùng JDK Machine, JUnit 4.13.2/Hamcrest 1.3 đã có trong Gradle cache, không tải dependency. Compile cả 5 core files và toàn TBS test package bằng `javac --release 8 -cp <JUnit;Hamcrest> -d drivers\build\p22-final-java8 <sources>`, exit 0; `javap -verbose` cho Session major version 52. Chạy 5 lượt:

```powershell
1..5 | ForEach-Object {
    & "$jdk\java.exe" -cp "$out;$cp" org.junit.runner.JUnitCore info.martinmarinov.drivers.usb.tbs5530.Tbs5530ConcurrencyTest
    if ($LASTEXITCODE -ne 0) { throw 'Stress failed' }
}
```

Cả 5 lượt **OK(10 tests)**, mỗi lượt có 40 detach/cancel iterations → 200 vòng; thời gian 0.173/0.148/0.152/0.219/0.179s. Trước đó, 5 lượt với 9 methods PASS khi chưa có test close đồng thời. Một lần gọi stress sai tên class do PowerShell argument, JUnit báo initializationError trước khi chạy tests; sửa command sang tên class đầy đủ, không sửa assertion. Output compile riêng luôn dọn trong finally; không còn p22-final-java8/p22-concurrency-check. Không tạo script tạm cần giữ.

Evidence local ở các module `build\test-results\testDebugUnitTest\TEST-*.xml`, lint `app\build\reports\lint-results-debug.xml`, APK `app\build\outputs\apk\debug\app-debug.apk`; không stage outputs/logs. IDE problems không có errors. Bằng chứng 83 methods ở Phase 2.1 giữ lịch sử riêng, không gọi là lượt test Phase 2.2.

### Giới hạn và bàn giao

- Không instrumentation mới: thay đổi JVM-only chưa được nối vào Android runtime, không Android framework binding/manifest/JNI/registry changes. Các kết quả PASS trước về native load, registry XML và UI là lịch sử, không phải PASS mới. Real USB/permission dialog/firmware/lock/TS/native cancel NOT RUN.
- Đã tra Google UsbDeviceConnection/USB Host guide: controlTransfer EP0 synchronous, timeout tính bằng ms/kết quả âm báo failure; không có cancellation handle riêng bảo đảm ngắt call. Code không cưỡng bức close in-flight; call không trả thì pending/reopen bị chặn. Deferred cleanup có thể chạy trên transaction thread; cancel không được join chính nó. Permission/open/claim timeout là caller/port contract, không thêm scheduler.
- Cần review ADR-012 và Android attachment identity/receiver/PendingIntent/deadline/threading trước Phase 2.3. Không dùng firmware thật/production frontend hoặc bring-up phần cứng khi chưa có kế hoạch/phê duyệt riêng.
- Kiểm cuối: git diff --check PASS; UTF-8 strict/relative links PASS cho 11 file, main core có 0 Android/AndroidX imports; 8 tracked changes + 3 untracked file Java, staged diff rỗng. File concurrency mới được chuẩn hóa CRLF→LF sau khi gate EOL phát hiện, không đổi logic/assertion, không chạy lại tests cho EOL-only. Toàn bộ file Java mới/sửa cuối dùng LF. JDK Machine java -version xác nhận 25.0.5.0.3; các output kiểm tra tạm không còn.
- Main vẫn ở 6eb2353, có 6 file Java mới/sửa và 5 hồ sơ cập nhật, chưa stage/commit/push. Dừng sau Phase 2.2.

## 2026-11-03 11:33 +07 — Checkpoint commit/push Phase 2.2 theo yêu cầu

- Người dùng phê duyệt commit toàn bộ thay đổi Phase 2.2 và push nhánh hiện tại. Preflight Windows PowerShell/main repo: nhánh chore/modernize-android-build, HEAD 6eb23537484a3e1dd263bd5a88f56914c7c5e313; live origin cùng SHA, staged diff rỗng, đúng 11 file (6 file Java và 5 hồ sơ), diff --check PASS.
- Staged diff check phát hiện một dòng trống thừa ở EOF của concurrency test mới, commit chưa chạy. Chỉ bỏ dòng trống đó, không đổi logic/assertion; stage lại và staged diff --check PASS. Không đổi Git whitespace policy.
- Message `feat: add TBS5530 lifecycle coordinator and safe cancellation`, kèm Co-authored-by trailer. Chỉ stage 11 file đã kiểm, không output/cache/firmware/reference. Sau commit push explicit origin/chore/modernize-android-build không force và xác minh SHA remote/working tree. Hash và kết quả thực báo sau kiểm tra, không khẳng định trước thành công.
- Không chạy lại tests cho tác vụ Git/documentation checkpoint. Kết quả 126 unit methods/debug/lint/stress ở mục Phase 2.2 là lần thực chạy gần nhất; không ghi như lần PASS mới. Không thay production logic, dependencies/toolchain/hardware, không bắt đầu Phase 2.3.

## 2026-11-03 12:05–12:42 +07 — Phase 2.3: Android framework binding/instrumentation, không tuner

### Baseline, audit và nguồn

- Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`. Main sạch lúc bắt đầu, nhánh chore/modernize-android-build/635ee4a57267de6055126efcfee6121b30701433; `git ls-remote --heads origin chore/modernize-android-build` cùng SHA. Đây là checkpoint Phase 2.2/ADR-012 đã commit/push, không còn pending như snapshot cũ.
- Đọc instructions v4/scoped build/hardware/ADB/records/toolchain cùng STATUS/ENVIRONMENT/RESEARCH/DECISIONS/WORKLOG/TEST-MATRIX; dùng project-handoff và android-cli. Audit UsbDelegate, manifest/service/worker, registry, DvbUsbDevice, UsbPermissionObtainer và device_filter.xml. Không dùng legacy permission helper cho TBS, không sửa driver khác hoặc nối registry/service.
- `git --no-lazy-fetch`/no-lazy-fetch 1/optional-locks 0 kiểm đầu/cuối: TBS 2ce787de9f6e81b4294692a45de15596ae2e1322, C c5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go 11228b05fb1626bf41c4e1879ace10cd0a8d575c; cả ba có 0 status entries. Không tải/sửa reference, firmware hoặc Git config.
- Semantic source search timeout, fallback glob/grep/view có giới hạn; GitNexus list_repos không có target index, không tự analyze hoặc tuyên bố graph rỗng là an toàn. Gateway discovery ADB/list_avds xác nhận TBS5530_Runtime_API37. Không thêm MCP endpoint hoặc sửa Proxy.
- Kiểm android --help/docs search help/emulator help trước khi dùng. Android CLI docs search/fetch USB Host/receiver/Android 14, Google API PendingIntent/USBManager/Connection và target 37 notes; nguồn/phân biệt API 21/23/31/33/34/37 ghi trong RESEARCH/ADR 013. Không thay build pins/dependencies/SDK/JDK/system env.

### Implementation

- Thêm Tbs5530UsbBinding (engine thuần JVM), Tbs5530AndroidUsbFacade (Android wrappers + Manager được inject/RawConnection), Tbs5530AndroidRuntime (worker + scheduler độc lập), Tbs5530UsbBindingTest và instrumentation Tbs5530AndroidUsbFacadeTest. Sửa Session/Coordinator để logical invalidation không chạy cleanup trên receiver/main; gọi drainCleanup tường minh ở worker, cleanupRunning bảo vệ quyền sở hữu tài nguyên. Tổng 7 file Java mới/sửa, chưa có control transport/uploader/streaming.
- Mỗi permission request có UUID correlation/action/data riêng, package-scoped mutable one-shot PI; API 31 dùng MUTABLE tường minh/API 33 dùng NOT_EXPORTED/typed parcelable, không có unsafe implicit flag. Receiver parse nhanh; acquisition/hasPermission/list checks/open/claim/cleanup nằm ngoài main; deadline logic không đóng connection đang dùng.
- Application attachment epoch + snapshot; callbacks trùng/cũ/sai thiết bị không open. Identity hiện tại/quyền được kiểm trước/sau open/claim; interface ID/alternate thay vì array index; force false, receipt chỉ sau claim true. Khi đổi identity/permission hoặc cleanup fail, giữ cause/suppressed và bảo đảm mỗi tài nguyên chỉ được xử lý một lần.
- Khởi tạo không tự request/open; bindings chưa gắn app/service/registry/XML. Constructors và tests fake không gọi manager thật. Không đổi EP0 encoding/tính nguyên tử I²C/FirmwarePlan/Retry/wire protocol/JNI. Bổ sung ADR 013 và 5 hồ sơ.

### JVM regression và build/lint

Child environment dùng Machine JAVA_HOME đã xác minh ở phase trước, không sửa User/Machine:

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_AVD_HOME = 'D:\Android\AVD'
```

Các lượt focused JVM binding/core đều PASS. Combined validation sau hardening facade/runtime/engine:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :drivers:connectedDebugAndroidTest :dvbservice:connectedDebugAndroidTest :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit 0/4m48s/241 tasks, 53 executed, 188 up-to-date.** Unit snapshot có 151 methods; instrumentation 19 drivers + 2 dvbservice = 21 tests, 0 failure/error/skipped; debug/lint PASS. Sau đó chỉ thêm 1 JVM test identity-changed-during-open/cleanup failure assertion và chạy lại toàn unit/build/lint:

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit 0/2m8s/177 tasks, 22 executed, 155 up-to-date.** XML cuối: drivers 138/usbxfer 1/dvbservice 13 = **152 methods**, 0 failure/0 error; binding có 26 methods mới. Bridge 17/Firmware 19/Session 17/Concurrency 10/Coordinator 32 tiếp tục PASS. Lint XML có 0 Error/0 Fatal/11 Warning (Autofill 1/GradleDependency 2/IconLauncherShape 3/LabelFor 1/TextFields 1/UnusedResources 3), không có suppression/baseline mới. Warning source/target 8 obsolete/deprecated API vẫn hiện, không nâng toolchain để né.

Kiểm độc lập cuối: `javac --release 8 -cp <JUnit4.13.2;Hamcrest1.3 từ cache sẵn có> -d drivers\build\p23-java8-check <6pure-corefiles và toànTBSJVMtestfiles>` PASS, javap Tbs5530UsbBinding có major version 52. JUnitCore Tbs5530UsbBindingTest + Tbs5530ConcurrencyTest chạy 3 lượt, mỗi lượt **OK(36 tests)**/0.448s/0.484s/0.345s, gồm 120 iterations có latch điều khiển của concurrency loop qua 3 lượt. Không tải dependency; output compile riêng đã xóa trong finally, xác nhận không còn.

### AVD và instrumentation

- `adb.exe devices -l` ban đầu không có device; SDK emulator -list-avds với ANDROID_AVD_HOME=D:\Android\AVD có TBS5530_Runtime_API37. Android CLI emulator start --headless báo sai “Device ... doesn't exist” nhưng exit 0; không coi exit 0 là boot PASS.
- Fallback gọi `D:\Android\Sdk\emulator\emulator.exe -avd TBS5530_Runtime_API37 -no-window -no-audio -no-snapshot-save -no-boot-anim` trong shell 71; không tạo/tải/wipe AVD. Kiểm serial tường minh emulator-5554: sys.boot_completed 1, ro.build.version.sdk 37, abi x86_64. Image hiện hữu android-37.0/google_apis/x86_64.
- :drivers:connectedDebugAndroidTest thực chạy 19 methods của Tbs5530AndroidUsbFacadeTest: registration/unregistration thật, mutable PI fill-in/one-shot thật, extras thiếu/sai định dạng, action/data/correlation sai, requests riêng biệt, chọn ID/alternate, claim false/release false, kiểm sau open/claim, chặn main thread, worker shutdown/self-wait, deadline logic và độ phản hồi main khi open/claim bị chặn. Toàn bộ Manager/RawConnection là fake, không có system USB dialog hoặc I/O vật lý.
- :dvbservice:connectedDebugAndroidTest thực chạy DeviceFilterXmlEquivalenceTester::allDevicesAreInXml và NativeLibrarySmokeTest::usbTransferLibraryLoadsWithoutOpeningUsb: **2 PASS**, không fake fd/ioctl.
- XML ở drivers/dvbservice `build\outputs\androidTest-results\connected\debug\TEST-TBS5530_Runtime_API37(AVD) - 17.xml` có root testsuites lần lượt 19/2, 0 fail/error/skipped; testcase names đã kiểm, không chỉ dựa task exit. Unit XML tại mỗi module build/test-results/testDebugUnitTest; lint ở app/build/reports/lint-results-debug.xml. Không stage outputs/raw logs/APK.

### No-tuner app smoke và cleanup

- ADB chỉ định emulator-5554: `install -r app\build\outputs\apk\debug\app-debug.apk` Success; resolve WelcomeActivity; `am start -W` COLD Status ok/TotalTime 3453 ms, PID 5124. Android CLI layout cài layout instrumentation server trên AVD để kiểm hierarchy; không đổi system settings/SDK.
- Emulator hiển thị riêng “System UI isn't responding”. MCP tap_on_text thất bại vì uiautomator busy/exit 137; fallback Android CLI semantic input `id=android:id/aerr_wait`, chọn Wait, app Welcome hiển thị tiếp. Không gộp SystemUI ANR vào app ANR.
- Semantic tap Diagnostic rồi START: dialog Attention/“No devices found”; Logcat của app đã lọc chỉ có DvbException đã xử lý tại DeviceController.run, không có FATAL/UnsatisfiedLinkError/SecurityException/app ANR trong cửa sổ kiểm tra. Không TUNE/TS record/thao tác phần cứng. Không xác minh active DvbService/TS lifecycle khi không có tuner.
- Dừng helper emulator đúng shell 71 bằng stop_powershell sau validation; adb devices cuối rỗng. Không kill process theo tên/wipe AVD, không dọn SDK/firmware. Không tạo script tạm. Engine/test files được chuẩn hóa LF/EOF-only cuối, không đổi logic hoặc assertion, không rerun cho EOL-only.

### Bàn giao và giới hạn

Kiểm cuối git diff --check, UTF-8 strict/LF/EOF cho file Java và liên kết Markdown tương đối PASS trên 12 file; staged diff rỗng. Không có diff app/usbxfer/dvbservice/build/wrapper/registry/legacy permission helper/DeliverySystem. IDE main/unit/instrumentation không có errors; references cuối vẫn sạch, ADB devices rỗng sau dừng helper. Không còn script/output kiểm tra tạm.

Đã triển khai 7 file Java + 5 hồ sơ, chưa stage/commit/push/merge; HEAD vẫn 635ee4a. Runtime API 21/23/31/33/34 NOT RUN; assertions cho các nhánh policy trên 37 không thay runtime coverage. USB Host vật lý/system USB permission dialog/phát attach-detach/identity dùng lại/native cancel/firmware/frontend/lock/TS NOT RUN. Hành vi main constructor/receiver được kiểm bằng tình huống tổng hợp nhưng binding chưa nối service hoặc tự giữ process sống.

Phase 2.4 cần review ADR 013, vòng đời process sở hữu tài nguyên/receiver/deadline/identity mapping và test plan cho thiết bị Android. Bring-up phần cứng chỉ sau phê duyệt riêng + nguồn gốc firmware/license/hash/address map + command plan/rollback; không tự thêm registry/uploader/frontend/TS. Dừng Phase 2.3.

## 2026-11-03 12:57 +07 — Checkpoint commit/push Phase 2.3 theo yêu cầu

- Người dùng phê duyệt commit các thay đổi Phase 2.3 và push nhánh hiện tại. Preflight Windows PowerShell/main repo: chore/modernize-android-build, HEAD 635ee4a57267de6055126efcfee6121b30701433; live origin cùng SHA, staged diff rỗng, đúng 12 file (7 file Java và 5 hồ sơ), diff --check PASS.
- Message `feat: add Android USB lifecycle binding and instrumentation`, có Co-authored-by trailer. Chỉ stage 12 file đã kiểm, không outputs/APK/logs/firmware/reference. Push explicit origin/chore/modernize-android-build không force, sau đó xác minh SHA remote và working tree; hash và kết quả thực báo sau kiểm tra.
- Không chạy lại tests cho tác vụ Git/documentation checkpoint. Bằng chứng 152 unit methods/21 instrumentation/debug/lint/stress ở mục Phase 2.3 là kết quả thực gần nhất, không gọi là lượt PASS mới. Không đổi production logic/toolchain/dependencies/hardware, không bắt đầu Phase 2.4.

## 2026-11-03 13:11–13:40 +07 — Phase 2.4A: control transport và kế hoạch 2.4B

### Khôi phục, contract và nguồn

- Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`. Đọc instructions v4/scoped hardware/usbipd/build/records, skills project-handoff/tbs-research và các phần mới nhất của STATUS/ENVIRONMENT/RESEARCH/DECISIONS/WORKLOG/TEST-MATRIX. Review ADR 010–013, core ControlIo/Bridge/Session/Coordinator/Binding/Facade/Runtime/FirmwarePlan. Main sạch, chore/modernize-android-build tại 8a004a3cc94d8c736da84cad1908b6a270b789a0; live origin cùng SHA, xác nhận Phase 2.3 đã push.
- Kiểm references đầu/cuối bằng `git --no-lazy-fetch -C <path> rev-parse HEAD/status --porcelain=v1 -uall` với GIT_NO_LAZY_FETCH=1/GIT_OPTIONAL_LOCKS=0: TBS 2ce787de9f6e81b4294692a45de15596ae2e1322, C c5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go 11228b05fb1626bf41c4e1879ace10cd0a8d575c; cả ba có 0 entries. Đọc official/C tbs5530_op_rw/I²C/properties và Go ControlRead/Write, không fetch/ghi index/config/source.
- Google UsbDeviceConnection/UsbEndpoint docs xác nhận offset API 18/EP0/count âm/claim force/close contract. RESEARCH tách bằng chứng từ source, lịch sử Windows/WSL và Android NOT RUN. GitNexus list_repos không có target index, không analyze hoặc giả định graph an toàn. Build pins hiện tại AGP 9.5.0/Gradle 9.8.2/checksum/compile 37.2/min 21/Java 8, SDK platform revision 2/PreviewSdkInt 0 không đổi.

### Implementation và ownership

- Thêm Tbs5530ControlTransport: constructor package-private; factory Coordinator.controlIo(Request)/Binding.controlIo chỉ cấp lease cho connection hiện tại đã claim; Session.requireOwned xác minh đúng identity của resource. Không mở connection thứ hai, không close/open trong mỗi call. Lease/Binding chuyển tiếp methods đến connection đang sở hữu; control/metadata mặc định của Connection từ chối an toàn và giữ tương thích fake ports.
- RawConnection.controlTransfer được AndroidManager forward tới cùng UsbDeviceConnection với đủ type/request/value/index/buffer/offset/length/timeout. OwnedConnection kiểm worker trước khi lấy lock, kiểm claimed/not closed/released; raw call + release/close nằm dưới ownership lock; Session active count/deferred cleanup chặn sử dụng sau close. Không retry/reset/ghi vendor để phục hồi/tự READY.
- Scratch buffer giữ payload/offset; IN chỉ copy slice được yêu cầu khi đủ count + generation hiện tại, không công bố output lỗi/cũ; OUT không sửa buffer của caller. Validate fields 8/16 bit/bounds/length 0..65535/timeout dương. Short khác kind với negative/overlong; giữ timeout cause tường minh, Android trả số âm không tự gán TIMEOUT.
- Thêm Descriptors bất biến và validation tái sử dụng Bridge.selectBulkEndpoint82; kiểm interface ID/alternate/direction/type/address trùng/max packet. Packet 64/512 là giới hạn phần mềm của profile, không chứng minh tốc độ; không yêu cầu 81 cho EP0. Không tự gọi setInterface; việc kích hoạt alternate có giá trị khác không chưa được kiểm chứng; không có TS reader/native changes.
- Thêm 14 ControlTransport unit methods + 10 Descriptors methods; Binding 26→27 thêm capability factory/end-to-end single open; instrumentation Facade 19→28 thêm 9 control/metadata/managed end-to-end tests. Không bỏ assertions cũ; sửa FakeRaw để descriptors giả phù hợp validation mới.
- Cập nhật 5 hồ sơ/ADR 014 và kế hoạch 2.4B. Không đổi registry/XML/service/legacy driver/TCP/FirmwarePlan/Bridge bytes/build/dependencies, không có debug entry point vật lý mới.

### Validation thực

VS Code runTask assembleDebug trả Task not found, fallback Gradle wrapper; không sửa tasks. Environment của child:

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_AVD_HOME = 'D:\Android\AVD'
```

Focused command:

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --tests '*Tbs5530ControlTransportTest' --tests '*Tbs5530UsbBindingTest' --tests '*Tbs5530DescriptorsTest' --console plain --no-daemon --max-workers=1
```

**PASS exit 0/2m13s/23 tasks, 5 executed, 18 up-to-date**, XML Transport 14/Descriptors 10/Binding 27 = 51 methods, 0 failure/error. Lượt tổng hợp cuối:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :drivers:connectedDebugAndroidTest :dvbservice:connectedDebugAndroidTest :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit 0/8m8s/241 tasks, 50 executed, 191 up-to-date.** Unit XML: drivers 163/usbxfer 1/dvbservice 13 = **177 methods, 0 failure/error**. Instrumentation XML root testsuites: drivers 28 + dvbservice 2 = **30 tests, 0 failure/error/skipped**. Hai tests hiện hữu là DeviceFilterXmlEquivalenceTester::allDevicesAreInXml và NativeLibrarySmokeTest::usbTransferLibraryLoadsWithoutOpeningUsb. Build APK debug PASS, lint XML có 0 Error/0 Fatal/11 Warning; không suppress/nâng dependency/toolchain. Cảnh báo source/target 8 obsolete/deprecated API vẫn hiển thị.

Kiểm Java 8 thuần dùng JDK Machine/javac --release8 cho 8 pure core files và TBS JVM tests, JUnit 4.13.2/Hamcrest 1.3 từ cache sẵn có, không Android classpath: PASS; javap Tbs5530ControlTransport có major version 52. JUnitCore TransportTest + ConcurrencyTest chạy 5 lượt, mỗi lượt 24 methods: **OK(24 tests)**/5.174s/2.102s/4.118s/3.252s/5.256s, gồm 200 race iterations có latch điều khiển qua 5 lượt. Output drivers/build/p24-java8-check đã xóa trong finally, xác nhận không còn.

### Emulator và phạm vi chứng minh

- ADB devices ban đầu rỗng. Kiểm Android CLI help trước start; emulator-list có TBS5530_Runtime_API37 nhưng CLI start báo Device không tồn tại/exit 0 sai. Fallback gọi tường minh `emulator.exe -avd TBS5530_Runtime_API37 -no-window -no-audio -no-snapshot-save -no-boot-anim` ở shell 105 với AVD home D:\Android\AVD. Không có image/AVD mới hoặc wipe. Kiểm boot_completed 1/API 37/x86_64/serial tường minh emulator-5554.
- Instrumentation dùng toàn bộ Manager/RawConnection fake. Wrapper framework production chỉ compile, không gọi control trên USB thật. Managed end-to-end test facade→coordinator→control→FakeRaw kiểm chính xác IN/OUT và single open/claim/cleanup. Descriptor/direct raw tests + bảo vệ main thread thực chạy trên emulator, không được gọi là hardware PASS.
- Dừng emulator helper đúng shell 105 sau validation, adb devices rỗng; không kill process bằng tên hoặc đổi system USB. Không chạy app UI journey mới vì phase không đổi app UI; không tái sử dụng UI PASS của Phase 2.3 như test mới.
- Evidence trong module build/test-results/testDebugUnitTest và build/outputs/androidTest-results/connected/debug XML, app/build/reports/lint-results-debug.xml, debug APK; không stage outputs/raw logs. IDE problems có 0 errors. Chỉ chuẩn hóa EOL hai file Java mới ở cuối, không đổi logic, không rerun cho formatting.

### Phase 2.4B và bàn giao

Toàn bộ 2.4B **NOT RUN**, chưa có phê duyệt riêng. RESEARCH chứa checklist environment/OTG/nguồn/Android Host/enumeration/permission thủ công/open-claim với force false/release-close/detach cleanup, điều kiện dừng/rollback và bảng bằng chứng Android API/VIDPID/descriptors/thread/time/code/state/thay đổi phần cứng. Hiện không có diagnostic entry point vật lý để chạy tự động. Chỉ sau phê duyệt cụ thể mới phát triển/chạy harness mặc định không hoạt động; không ghi vendor/I²C/firmware/frontend/TS trong preflight.

Kiểm cuối: git diff --check, UTF-8 strict, LF/EOF file Java và liên kết Markdown tương đối đều PASS trên 16 file; staged diff rỗng. Không diff build/wrapper/app/usbxfer/dvbservice/registry/Bridge/FirmwarePlan. IDE không có errors; ba references sạch, output compile riêng/emulator helper đã dọn. Không có physical preflight hoặc thay đổi trạng thái USB trong phase.

HEAD vẫn 8a004a3, chưa stage/commit/push/merge. Owner tương lai giữ binding/runtime/capability theo session, invalidate trước shutdown, drain ngoài main, không có global USB service mới. Call không trả → PENDING/chặn reopen, không force close; không giả định chắc chắn identity khi bỏ lỡ việc tái sử dụng. Dừng 2.4A + kế hoạch 2.4B, không bring-up tự động.

## 2026-11-03 15:05 +07 — Checkpoint commit/push Phase 2.4A theo yêu cầu

- Người dùng phê duyệt commit các thay đổi và push nhánh hiện tại. Preflight Windows PowerShell/main repo: chore/modernize-android-build, HEAD 8a004a3cc94d8c736da84cad1908b6a270b789a0; live origin cùng SHA, staged diff rỗng, đúng 16 file (11 file Java và 5 hồ sơ), diff --check PASS.
- Message `feat: add managed USB control transport and descriptor validation`, có Co-authored-by trailer. Chỉ stage 16 file đã kiểm, không outputs/APK/logs/firmware/reference. Push explicit origin/chore/modernize-android-build không force rồi xác minh SHA remote và working tree; hash và kết quả thực báo sau kiểm tra, không ghi trước thành công.
- Không chạy lại tests cho tác vụ Git/documentation checkpoint. Bằng chứng 177 unit methods/30 instrumentation/debug/lint/stress trong mục Phase 2.4A là kết quả thực gần nhất, không gọi là lượt PASS mới. Không đổi logic/toolchain/dependencies/hardware. Phase 2.4B vẫn NOT RUN, cần phê duyệt riêng; không bắt đầu firmware/frontend/TS.

## 2026-11-03 16:06–16:51 +07 — Phase 2.4B xuyên suốt: passthrough, diagnostic và regression

### Preflight và approval

- Windows PowerShell/cwd D:\API\TBS5530-DVB-Android\AndroidDvbDriver. Main sạch, chore/modernize-android-build/c153e05e454f506f5c6639685d2cded51e8c0126 xác nhận Phase 2.4A/ADR 014 đã push. Đọc instructions v4/scoped usbipd/build/ADB/records, phần mới nhất của 6 hồ sơ/ADR 010–014; dùng project-handoff/android-cli. Không commit/push/merge/reset hoặc đổi references/toolchain.
- Người dùng cho phép USB passthrough/enumeration/permission và một connection cycle nếu Android thấy target; chỉ usbipd detach nếu trạng thái live là Attached. Không thay driver/reset/control vendor/firmware/I²C/LNB/tune/TS. Không chuyển sang Android vật lý trong phase.
- Get-Command xác nhận git/android/usbipd/wsl; Process JAVA_HOME mới đúng Machine C:\Program Files\Java\latest\jdk-25; ANDROID_HOME/AVD_HOME của process/User đúng D:\Android\Sdk và D:\Android\AVD. Không sửa global env. Usbipd 5.3.0 list trực tiếp: 6-2/734c5530 Shared, không Attached; Ubuntu 22.04 WSL2 Stopped. Không detach/bind/unbind/attach WSL. PnP state OK/MEDIA, signed TBS 2.0.0.2/tbsBDA.TBS5530/oem108.inf.
- Quét process theo tên DVB/TBS/SmartDVB/ProgDVB/VLC/MediaPortal không thấy tuner client; không coi đó là quét mọi handle. ADB ban đầu rỗng/không emulator, không ảnh hưởng phiên khác. Emulator 37.2.13/build 16740729, accel-check 0/WHPX 10.0.19045 usable; AVD TBS5530_Runtime_API37/image android 37.0/google_apis/x86_64 revision 7; bảo toàn userdata/no-wipe.

### QEMU trials và blocker đã xác minh

Nguồn Google Emulator release notes + command line/QEMU USB/libusb Windows được tra ngày 2026-11-03; Android CLI docs search/fetch xác nhận công bố Windows usb-host. Lệnh gốc người dùng đề xuất được chạy đúng target và đặt -qemu cuối:

```powershell
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_AVD_HOME = 'D:\Android\AVD'
$emulator = 'D:\Android\Sdk\emulator\emulator.exe'
& $emulator -avd TBS5530_Runtime_API37 -no-snapshot-load -no-snapshot-save -no-window -no-audio -qemu -usb -device 'usb-host,vendorid=0x734c,productid=0x5530'
```

- Shell 132: ADB boot_completed 1/API 37/x86_64/kernel 6.12.81-android16-6-g4f69fc7b210c-ab16167562. `cmd package has-feature android.hardware.usb.host` false; dumpsys usb timeout 10s; /sys/bus/usb/devices chỉ có root interfaces/hubs, /dev/bus/usb001/002/003 mỗi bus chỉ có 001, không có TBS. Đọc dmesg và attributes idVendor/idProduct/speed bị denied; không adb root/đổi SELinux.
- QEMU help qua raw binary không có output; emulator -devicehelp báo charpipe error. `usb-host,help` qua cùng AVD nhận được thuộc tính vendorid/productid/guest-reset/loglevel nhưng exit 1073741845. Không phải unsupported device. ADB emu qemu monitor info USB báo QEMU support no longer available.
- Dừng shell 132 trước khi thử một cấu hình bus khác theo QEMU docs:

```powershell
& $emulator -avd TBS5530_Runtime_API37 -no-snapshot-load -no-snapshot-save -no-window -no-audio -qemu -device 'qemu-xhci,id=tbsxhci' -device 'usb-host,bus=tbsxhci.0,vendorid=0x734c,productid=0x5530,loglevel=4,guest-reset=false,guest-resets-all=false'
```

- Shell 142 không xác nhận boot ADB; poll helper gặp null khi boot chưa sẵn sàng. Đã sửa/thêm null guard nhưng ADB vẫn đợi; dừng đúng shell 144/142, không loop XHCI vô hạn. Không dùng kernel/custom QEMU/USBIP/VM thay thế.
- Lượt cuối chẩn đoán cấu hình gốc thêm QMP loopback và libusb log, reset flags false:

```powershell
$env:LIBUSB_DEBUG = '4'
& $emulator -avd TBS5530_Runtime_API37 -no-snapshot-load -no-snapshot-save -no-window -no-audio -qemu -qmp 'tcp:127.0.0.1:45454,server,nowait' -usb -device 'usb-host,vendorid=0x734c,productid=0x5530,loglevel=4,guest-reset=false,guest-resets-all=false'
```

- Shell 148 chỉ truy vấn QMP qmp_capabilities/query-version/human-monitor-command info usbhost/info usb. Version 2.12.0 `(v2.12.0-19097-g85fa07f04ef)`; host info nhận 734c5530/bus 4 addr 4/480 Mb/s. Guest info chỉ có placeholder Device 0.0 USB Host Device, không có VID/guest enumeration. Log target: `winusb_open unsupported API call for 'open' (unrecognized device driver)` và `libusb_open open4.4 returns -12`. Theo libusb API, -12 = LIBUSB_ERROR_NOT_SUPPORTED, không phải ACCESS/BUSY/Android permission; Windows BDA không phải backend generic được hỗ trợ. Dừng shell 148 ngay sau khi đủ evidence, không thay driver hoặc hỏi Allow khi không có target.
- Usbipd cuối vẫn Shared/BDA, không ghi thay đổi quyền sở hữu. QMP/libusb thô có định danh USB khác ở tool temp, không commit/copy raw logs/đăng artifact. Chỉ tóm tắt target và error đã lọc. Tất cả host open thất bại trước transfer; không có vendor requests/firmware/reset được thực hiện.

### Debug diagnostic và safe completion

- Thêm 5 file: app/src/debug/AndroidManifest.xml, Tbs5530DiagnosticController.java, Tbs5530DiagnosticMetadata.java, Tbs5530UsbDiagnosticActivity.java và app/src/androidTest/.../Tbs5530UsbDiagnosticTest.java. Chỉ có debug Activity mở tường minh, không launcher/USB attach filter, không auto open theo extras. Debug USB Host optional để chạy trên image không có feature; giữ main/release USB Host required true.
- Controller dùng binding/facade/runtime/session hiện hữu, giữ qua Activity recreation, chặn cycle mới khi pending. Refresh chỉ đọc metadata; người dùng chọn đúng TBS mới request permission. Grant mới thực hiện một open/claim false cycle/rồi release-close; deny/timeout có 0 open. Không gọi controlIo/controlTransfer hoặc recovery/retry. Báo cáo plain text lọc path/id/serial/exception messages; thread/duration/counters/state hiển thị kết quả trả về thực, không gọi PASS khi thiếu cleanup.
- Sau blocker, boot AVD thường ở shell 150 để regression, không retry passthrough. Cài diagnostic thực trả Success; khởi động tường minh có kết quả COLD Status ok, 7704 ms. UI cho thấy không có USB Host, không có USB devices hoặc TBS, 0 open/0 claim; chu kỳ ở trạng thái NOT RUN. Không nhấn request permission vì không có target. Output layout tiếng Việt của Android CLI bị lỗi hiển thị dấu do encoding stdout; agent kiểm source UTF-8/Unicode thật, không có U+FFFD.
- Launcher/SystemUI ANR overlays xuất hiện trên emulator; chọn Wait theo ngữ nghĩa để đóng, không tắt lock/security/đổi system settings. MCP describe_ui gặp uiautomator dump busy 137; dùng Android CLI semantic layout/input làm fallback. Không gộp System ANR thành diagnostic app ANR.

### Regression, lỗi test đã sửa và release exclusion

Child env dùng Machine JAVA_HOME/SDK/AVD/ANDROID_SERIAL tường minh emulator-5554, không đổi package. Lượt đầu:

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:connectedDebugAndroidTest :drivers:connectedDebugAndroidTest :dvbservice:connectedDebugAndroidTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console plain --no-daemon --max-workers=1
```

- Unit 177 PASS thực. Task app instrumentation FAIL exit 1/7m50s: 12 tests/1 failure, clipboard null ở explicitActivityIgnoresExtrasRefreshesAndCopiesSanitizedReport line 369. Activity chưa focus/SystemUI overlay khiến Android clipboard read không đạt. Sửa bằng cách chờ window focus có giới hạn và đọc clipboard bằng Activity context; giữ assertion/12 methods, không suppress hoặc disable test.
- Rerun phần chưa đạt/chưa chạy sau sửa:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest :drivers:connectedDebugAndroidTest :dvbservice:connectedDebugAndroidTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit 0/7m30s/403 tasks, 53 executed, 350 up-to-date.** XML thực: app 12/drivers 28/dvbservice 2 = **42 instrumentation tests, 0 failure/error/skipped**. Unit XML: drivers 163/usbxfer 1/dvbservice 13 = **177 methods, 0 failure/error** từ lượt unit đã rerun. Hai baseline smoke tests NativeLibrarySmokeTest và DeviceFilterXmlEquivalenceTester đều PASS mới, không lấy lịch sử thay thế. Debug/release unsigned APK PASS. Lint có 0 Error/0 Fatal/11 Warning cùng nhóm cũ, không suppress.
- `apkanalyzer manifest print` debug thấy diagnostic/USB Host required false; release USB Host required true/không có Activity. `apkanalyzer dex packages --defined-only <releaseAPK>` có 0 diagnostic matches; debug có controller/activity. Không chỉ suy từ folder src/debug.
- JDK Machine `javac --release8 -cp D:\Android\Sdk\platforms\android-37.2\android.jar -d app\build\p24b-java8-check <allTBSmain+debugJavafiles>` PASS; javap DiagnosticActivity có major version 52; output tạm đã xóa trong finally. Không có dependencies mới/nâng toolchain/thay native.

### Bàn giao

Passthrough mục tiêu FAIL/BLOCKED ở host libusb BDA; guest TBS enumeration/descriptor/permission/cycle/detach thật chưa đạt, ghi NOT RUN/BLOCKED riêng. Không có permission dialog hoặc bypass ADB, không thao tác cáp vật lý/thay driver. Android Host feature false là blocker khác của image, chưa được sửa; đổi WinUSB cũng không cam kết đủ.

Kiểm cuối: git diff --check, UTF-8 strict/LF/EOF file Java và liên kết Markdown tương đối PASS trên 11 file; staged diff rỗng. Không có diff app/src/main/release/build, drivers/usbxfer/dvbservice/wrapper/registry. IDE debug/androidTest có 0 errors. Emulator helper shell 150 đã dừng sau validation, adb devices cuối rỗng; trước đó các shell 132/142/148/QMP đã dừng, không đụng session AVD khác. Cả 3 references có SHA khóa/sạch, usbipd TBS vẫn Shared/BDA. Không còn script hoặc output compile tạm; raw logs local không đưa vào Git.

Đề xuất sau phase: Android thật có USB Host/OTG/nguồn hub/phê duyệt metadata → dialog do người dùng chọn → một cycle với debug diagnostic; không chuyển điện thoại trong phase 2.4B. Nếu chọn host driver thì review riêng, phải có rollback BDA/vòng đời tuner Windows. Không mở Phase 2.5/firmware/frontend/TS. Git vẫn c153e05, chưa stage/commit/push/merge; chỉ có 5 file debug/test mới + 6 hồ sơ.

## 2026-11-03 17:35 +07 — Rà soát cách viết trong sáu hồ sơ dự án

- **Yêu cầu:** rà toàn bộ sáu hồ sơ để sửa chữ dính và câu khó đọc; giữ mọi mục lịch sử, thứ tự, ngày, SHA, lệnh, trích dẫn lỗi và kết quả kiểm chứng. Không rút gọn lịch sử thành bản tóm tắt.
- **Phạm vi:** STATUS.md, ENVIRONMENT.md, RESEARCH.md, DECISIONS.md, TEST-MATRIX.md và WORKLOG.md. Mỗi phần được sửa trong file được giao; WORKLOG đã được đọc hết 620 dòng gốc, gồm 66 tiêu đề, không bỏ mục nào. Giữ nguyên các khối mã và tên API/đường dẫn kỹ thuật; các phần tiếng Anh cũ chỉ cần sửa nếu có lỗi khoảng trắng.
- **Git và thay đổi có sẵn:** nhánh chore/modernize-android-build, HEAD c153e05. Bảo toàn 5 file debug/test Phase 2.4B chưa commit; tác vụ biên tập không stage/commit/push, không sửa code, references hoặc cấu hình.
- **Giới hạn kiểm tra:** chỉ kiểm nội dung tài liệu, UTF-8/LF và tính toàn vẹn của bằng chứng. Parent đã lưu dấu kiểm tra của 5 file code và SHA/URL/khối mã lịch sử từ HEAD, đồng thời chuẩn bị script kiểm Markdown/liên kết/khối mã/token được bảo vệ cho sáu hồ sơ; kiểm cuối thực hiện sau khi các phần biên tập hoàn tất. Mục này không ghi trước kết quả kiểm cuối.
- **Không chạy mới:** build, unit/instrumentation tests, emulator hoặc kiểm thử phần cứng. Các kết quả đã ghi trong lịch sử không được coi là kết quả chạy lại trong tác vụ biên tập.

## 2026-11-03 19:17 +07 — Commit và push công cụ chẩn đoán cùng tài liệu

- Người dùng phê duyệt commit và push tất cả thay đổi còn lại của Phase 2.4B và đợt biên tập tài liệu. Preflight: nhánh chore/modernize-android-build, local/live origin cùng c153e05e454f506f5c6639685d2cded51e8c0126; stage trống, đúng 11 file (5 debug/test và 6 hồ sơ), git diff --check PASS.
- Đợt biên tập đã hoàn tất kiểm tra sáu hồ sơ: UTF-8, liên kết, cấu trúc bảng, code fences, SHA và lệnh lịch sử được giữ; fingerprint xác nhận 5 file mã nguồn không bị thay đổi bởi biên tập. Các file kiểm tra tạm đã dọn.
- Message `feat: add debug USB preflight diagnostics and polish project docs`, có Co-authored-by trailer. Chỉ stage 11 file nêu trên; không APK, raw logs, firmware hoặc reference sources. Push tường minh lên origin/chore/modernize-android-build, không force; xác minh SHA remote và working tree sau thao tác, không ghi trước thành công.
- Không chạy lại build/test/hardware cho tác vụ Git và checkpoint tài liệu. Kết quả 177 unit methods/42 instrumentation tests/debug/release/lint của Phase 2.4B vẫn là bằng chứng thực gần nhất, không phải lượt PASS mới. Không đổi driver Windows, không mở Phase 2.5 hoặc hardware bring-up.

## 2026-11-03 21:28–22:16 +07 — Phase 2.5: OnePlus 7/API 30, enumeration và permission có giới hạn

### Wireless guard và baseline

- 21:28, `adb devices -l` báo OnePlus offline: dừng ngay, không sửa mã, build, chạy test hoặc thao tác USB. Người dùng xác nhận nối lại lúc 21:34; đã tra serial TCP với port mới, trạng thái device, model GM1900, manufacturer OnePlus, API 30 và Android 11. Có alias mDNS của cùng điện thoại nhưng mọi lệnh dùng một serial TCP đã xác minh. Không tự chọn emulator, pair lại, đổi tcpip 5555 hoặc tiếp tục chỉ vì thiết bị tự online.
- Repository chính sạch khi bắt đầu, nhánh chore/modernize-android-build tại `dc50e7304941a7d01aa1d490c13fcf09683a01c9`; Phase 2.4B/ADR 015 đã push. Đã đọc instructions, các quy tắc hardware/ADB/build/records và phần mới nhất của 6 hồ sơ/ADR 010–015; dùng project-handoff/tbs-research. Ba reference repositories được kiểm đầu/cuối, đúng SHA: TBS 2ce787de9f6e81b4294692a45de15596ae2e1322, C c5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go 11228b05fb1626bf41c4e1879ace10cd0a8d575c; có 0 changes, dùng no-lazy-fetch. Không sửa references hoặc toolchain.
- Thuộc tính runtime: build GM1900_14_220617, OTA OnePlus7Oxygen_14.P.45_GLO_0450_2206171433; ro.oxygen.version trống. OxygenOS 11.0.9.1.GM57AA là phiên bản người dùng cung cấp. USB Host feature=true, không cần đổi thiết lập OTG. Không dùng WSL/usbipd hoặc driver Windows trong kiểm thử trực tiếp trên Android.
- Guard (bộ kiểm tra kết nối) kiểm danh sách ADB devices, state, model và API trước mọi thao tác thiết bị/build, đồng thời kiểm mỗi 10s khi Gradle chạy. Wrapper theo dõi PID tiến trình con; nếu guard thất bại thì dừng tiến trình con và ném lỗi WIRELESS_ADB_STOP, không chuyển sang thiết bị khác. Không có lần mất kết nối mới trong các lệnh đã hoàn tất. IP/port/serial cục bộ không được ghi vào mã nguồn hoặc tài liệu công khai.

### Descriptors thực tế và chặn acquisition

- Đọc dumpsys usb và cây giao diện qua Android CLI, không yêu cầu người dùng gửi ảnh/screenshot. Lượt uiautomator dump riêng không tạo được file; chuyển sang Android CLI layout để đọc đúng app. Debug APK baseline khởi động lạnh trong 417ms. Không bấm chạy chu kỳ khi descriptor (mô tả cấu trúc USB) chưa được phân tích.
- Metadata thật: VID 734c/PID 5530, configuration ID 1, 4 entries cùng interface ID 0, alternate 0–3. Alt 0 rỗng; alt 1 có Bulk 01/81/02/04/86/88, packet 512, interval 0. Alt 2 có Interrupt 01/81 với packet 64, interval 1; Interrupt 02/86 với packet 512, interval 1; Bulk 04/88 với packet 512, interval 0. Alt 3 tương tự nhưng 02/86 là Isochronous. Không có 82. Bảng đầy đủ index/ID/alternate/address/direction/type/packet/interval được ghi trong RESEARCH; JSON được đọc trực tiếp bằng run-as.
- Đối chiếu nguồn chính thức: properties trong tbs5530.c dùng TS 82, còn download firmware được tách riêng; snapshot WSL trước có 81/82, khác hiện tại. Không suy ra firmware đã đổi hoặc dùng 88 thay 82. Tbs5530Descriptors chặn đúng theo profile, không được nới validation hoặc đổi alternate để claim. Open/claim/release/close thật bị BLOCKED; không gọi getRawDescriptors khi chưa open, không gọi setInterface/setConfiguration.

### Sửa diagnostic và permission-only có regression

- Baseline chỉ có nút permission+cycle, chưa có chức năng xuất dữ liệu có cấu trúc vào vùng riêng của app. Đã thêm permission-only (chỉ xin quyền), tái dùng Binding/Coordinator với acquire=false sau grant. Cách start mặc định cũ được giữ nguyên. Snapshot kiểm profile trước chu kỳ; UI tắt chu kỳ với descriptor OnePlus nhưng vẫn cho phép xin quyền. Không tạo vòng đời USB thứ hai, không gọi controlIo/transfer trong diagnostic.
- JSON export chỉ dành cho debug, dùng AtomicFile ghi vào `files/tbs5530-diagnostic.json`; đọc bằng `adb -s <VERIFIED_SERIAL> shell run-as info.martinmarinov.dvbdriver cat files/tbs5530-diagnostic.json`. Các trường gồm timestamp/operation/generation/model/API/configs/interfaces/endpoints/permission/callback/state_before_cleanup/counters/events/thread/duration/result/failure_reason; loại bỏ path, OS ID, USB serial, correlation UUID và credentials. Mã release không export.
- Thêm unit tests cho permission-only với grant/deny/pre-granted, bảo đảm 0 open, cùng fixture mô phỏng descriptor OnePlus. Instrumentation kiểm lưu JSON, lọc dữ liệu, permission-only và profile không an toàn với 0 open. Không bỏ assertion hoặc dùng callback giả rồi gọi là hardware PASS.

### Permission thực: điều đã và chưa chứng minh

- Trước request, JSON ghi operation 0, generation 0, state DETECTED, hasPermission=false và 0 open/claim. Cài APK mới bằng -r trả Success, khởi động trong 874ms. Metadata profile bị BLOCKED, nút chạy chu kỳ không được kích hoạt.
- 21:55, bấm nút “Chỉ yêu cầu quyền (không mở USB)” sau khi guard đạt. Dialog thật “Cho phép DVB-T Driver truy cập null?” xuất hiện vì thiết bị không có product name. Ask_user yêu cầu người dùng tự chọn; người dùng trả lời đã OK. Agent không bấm Allow hoặc cấp quyền qua ADB/root.
- Khi đọc JSON, operation 1/generation 1 đã CLOSED; callback count=0, correlation=false, permission vẫn là ĐANG CHỜ, pending=false, cleanup=false và 0 open/claim/release/close. Deadline 30s đã hết trước khi đọc lại, receiver đã cleanup; không gọi callback grant là PASS. Refresh metadata sau đó thấy hasPermission=true: quyền OS đã được cấp, không có thiết bị khác hoặc connection mới.
- Lượt permission-only operation 2 là pre-granted (đã có quyền): không request dialog, state PERMISSION_GRANTED trước cleanup và CLOSED sau cleanup, 0 open/claim/callback, không pending. Không tự thu hồi quyền để hiện lại dialog. Denied thật vẫn NOT RUN; phân biệt rõ timeout với quyền thực tế tại thời điểm đọc. Cập nhật nhãn báo cáo diagnostic thành TIMEOUT thay ĐANG CHỜ sau deadline, không nới timeout để biến test cũ thành PASS.
- UTP instrumentation cài/gỡ target debug APK; sau khi cài lại APK cuối, metadata ghi hasPermission=false. Không suy ra quyền đã cấp phải tồn tại qua cài/gỡ; không tự xin lại. JSON cuối ghi operation 0, state DETECTED, BLOCKED_PROFILE/MISSING_SAFE_TS_ENDPOINT_PROFILE, 0 open/0 claim, USB Host=true và có target. Giữ APK cuối trên điện thoại, chưa rút USB.

### Commands regression và kết quả mới trên API 30

Chỉ đặt môi trường tiến trình con trong PowerShell: lấy JAVA_HOME từ Machine, ANDROID_HOME=D:\Android\Sdk và ANDROID_SERIAL=<VERIFIED_SERIAL>. Guard mỗi 10s kiểm ADB devices/model/API và chọn serial cụ thể; không dùng emulator mặc định dù có alias mDNS.

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --tests *Tbs5530UsbBindingTest :app:assembleDebug --console plain --no-daemon --max-workers=1
.\gradlew.bat :app:assembleDebug --console plain --no-daemon --max-workers=1
```

Hai lượt kiểm tập trung/build đều PASS: lượt đầu 2m54s, 100 tasks, 18 executed và 82 up-to-date; lượt sau 1m13s, 95 tasks, 12 executed và 83 up-to-date. Lượt đầy đủ trước fixture cuối PASS trong 7m2s, 418 tasks, 85 executed và 333 up-to-date, với 179 unit tests + 44 instrumentation tests. Sau khi bổ sung fixture descriptor và JSON events, đã chạy lại lượt cuối:

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:connectedDebugAndroidTest :drivers:connectedDebugAndroidTest :dvbservice:connectedDebugAndroidTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit 0, 4m32s; 418 tasks: 36 executed và 382 up-to-date.** XML thực tế: drivers 166/usbxfer 1/dvbservice 13 = **180 JVM methods, 0 failure/error**; instrumentation app 14 + drivers 28 + dvbservice 2 = **44 tests, 0 failure/error/skipped**, target GM1900/Android 11/API 30. Tất cả ports trong app instrumentation đều là fake; UI tests chỉ kiểm metadata/export, không bấm xin quyền phần cứng. Fake control của drivers không gửi lệnh tới TBS. NativeLibrarySmokeTest::usbTransferLibraryLoadsWithoutOpeningUsb và DeviceFilterXmlEquivalenceTester::allDevicesAreInXml đều PASS mới trên API 30, không gọi ioctl với FD giả.

- Build debug/release APK PASS; lint XML có 0 Error/0 Fatal/11 Warning. `apkanalyzer manifest print` và `dex packages --defined-only` xác nhận release có 0 diagnostic/exporter classes; USB Host required=true không đổi. Không nâng build tools/dependencies hoặc thêm suppression.
- Dùng JDK Machine, javac --release8 với android 37.2 jar cho nguồn TBS main + debug: PASS; javap cho DiagnosticActivity có major version 52. Thư mục kiểm tra app/build/p25-java8-check đã được xóa trong finally, xác nhận không còn. Compile 37.2 không đồng nghĩa runtime 37; runtime thực tế là 30.

### Bàn giao và giới hạn

Enumeration/USB Host và hasPermission tại thời điểm đọc là PASS thực; dialog đã xuất hiện và người dùng đã chọn OK. Callback grant thật chưa được xác minh vì hết deadline; open/claim/release/close thật và physical detach vẫn NOT RUN/BLOCKED do descriptor profile. Không cố gây tình huống I/O bị chặn, không claim với force, không gọi setInterface/setConfiguration hoặc thực hiện vendor/I²C/reset/firmware/LNB/tune/bulk/TS. Không tương tác với app ngoài diagnostic và dialog USB đã được duyệt.

Kiểm cuối: Wireless ADB vẫn đúng GM1900/API 30/state=device; không có lần mất kết nối mới sau khi người dùng xác nhận reconnect. UTF-8/liên kết Markdown và git diff --check PASS; IDE không errors; stage trống. Không diff build/wrapper/app main/release, native/usbxfer/dvbservice, registry hoặc production descriptor/facade validation. Ba references đúng SHA khóa và sạch. Không script/output compile tạm còn lại; raw logs ngoài Git. Giữ debug APK cuối trên thiết bị, không force-stop ứng dụng khác hoặc thay thiết lập hệ thống.

Cần review ADR-016 và descriptor/state/provenance trước phase bridge/firmware được cấp phép riêng. Không đánh giá driver/DVB là ready chỉ từ enumeration hoặc grant. Repository chính vẫn ở dc50e73; 8 file source/test và 6 hồ sơ sửa, chưa stage/commit/push/merge. Không tự chuyển phase hoặc yêu cầu cắm/rút lặp.

## 2026-11-03 22:26 +07 — Biên tập lại sáu hồ sơ sau Phase 2.5 theo yêu cầu

- Người dùng yêu cầu rà lại cả 6 hồ sơ dự án để sửa chữ dính và câu khó đọc. Chỉ biên tập tài liệu; phần việc tại đây chỉ sửa WORKLOG.md, đọc hết lịch sử và giữ thứ tự các mục, bằng chứng, số liệu, trạng thái, lệnh và định danh kỹ thuật.
- Giữ nguyên 8 file mã nguồn/test Phase 2.5 chưa commit trên nhánh chore/modernize-android-build, HEAD dc50e73. Không sửa mã nguồn, references hoặc instructions; không stage/commit/push, chạy build/tests, ADB hoặc thao tác phần cứng.
- Việc xác minh chung cả 6 hồ sơ do parent thực hiện sau biên tập; mục này không ghi trước kết quả PASS.
- **Xác minh hoàn tất lúc 22:39 +07:** lượt kiểm cuối trước đó bị dừng ngay khi Wireless ADB mất kết nối. Sau khi người dùng xác nhận nối lại lúc 22:38, tra serial TCP mới và xác minh OnePlus GM1900/API 30/state=device; không tái sử dụng connection hoặc generation USB cũ, không thao tác USB.
- Đối chiếu với snapshot trước biên tập, bao gồm nội dung Phase 2.5 chưa commit: cả 6 hồ sơ giữ nguyên SHA, URL, inline code, lệnh trong code fences, cấu trúc bảng và số liệu. UTF-8, liên kết Markdown và git diff --check PASS. Cảnh báo thứ tự chữ số trong STATUS chỉ do thêm checkpoint biên tập; phần lịch sử giữ nguyên thứ tự chữ số. Không còn mẫu chữ dính phổ biến đã kiểm ngoài code/URL.
- Fingerprint SHA-256 xác nhận cả 8 file mã nguồn/test không thay đổi trong đợt biên tập. Không chạy lại build/tests hoặc phần cứng, không stage/commit/push. File kiểm tra và snapshot tạm được dọn sau xác minh.

## 2026-11-03 22:43 +07 — Commit và push Phase 2.5 cùng tài liệu đã biên tập

- Người dùng phê duyệt commit và push các thay đổi còn lại. Preflight: nhánh chore/modernize-android-build, local/live origin cùng dc50e7304941a7d01aa1d490c13fcf09683a01c9; stage trống, đúng 14 file (8 mã nguồn/tests và 6 hồ sơ), git diff --check PASS. Guard xác nhận OnePlus GM1900/API 30 online bằng serial hiện hành; không dùng handle USB cũ hoặc thao tác USB.
- Message `feat: add permission-only USB preflight and private diagnostic reports`, có Co-authored-by trailer. Chỉ stage 14 file đã kiểm; không APK, raw logs, pairing credentials, firmware hoặc references. Push tường minh origin/chore/modernize-android-build, không force; xác minh SHA remote và working tree sau thao tác, không ghi trước thành công.
- Không chạy lại build/test/hardware cho tác vụ Git và checkpoint này. Bằng chứng gần nhất của Phase 2.5 là 180 unit methods, 44 instrumentation tests, debug/release build và lint 0 errors/11 warnings. Không thay guard descriptor, không mở connection cycle thật hoặc firmware/vendor bring-up.
- Lượt commit đầu dừng trước stage do Wireless ADB mất kết nối. Sau xác nhận nối lại lúc 22:45, đã tra serial mới và xác minh OnePlus GM1900/API 30/state=device. Kiểm diff phát hiện một dòng trống thừa ở cuối WORKLOG; chỉ bỏ dòng đó, không sửa logic hoặc bằng chứng. Không chỉnh bản đính kèm chỉ đọc; mọi thay đổi tài liệu ở repository chính.

## 2026-11-03 23:34–2026-11-04 00:10 +07 — Phase 2.6 thống nhất: USB mode, raw descriptors và hồi quy

### Baseline, hướng nghiên cứu và guard

- Đọc toàn bộ copilot-instructions, build/toolchain/hardware/ADB/project-records instructions, ADR-010–016 và phần hiện hành của sáu hồ sơ. Git local, origin tracking và commit người dùng cung cấp cùng `df076d28dd8100d5caa363f1d866a274e3f2b636`, nhánh `chore/modernize-android-build`, working tree sạch ban đầu. Đây là checkpoint Phase 2.5 đã push. Không fetch/reset/merge/stage/commit/push; không sửa ba references.
- SHA references đo đầu/cuối: official `2ce787de9f6e81b4294692a45de15596ae2e1322`, C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`; status porcelain không có thay đổi. Dùng source trực tiếp; GitNexus không có index repo này. AgentMemory recall không kết quả. Research subagent không khởi chạy được vì model mặc định không khả dụng; không thử model khác hoặc coi là kết quả agent. Đã nghiên cứu trực tiếp và ghi nguồn ở RESEARCH.
- Wireless guard đầu xác minh GM1900/API 30/state=device bằng serial TCP; không dùng alias mDNS. Trong lúc sửa diagnostic, serial đã chọn biến mất và danh sách có cổng mới: dừng ngay, dùng ask_user yêu cầu xác nhận. Sau người dùng xác nhận mới tra serial mới, kiểm model/API/USB Host, app-private metadata và khởi động lạnh owner mới. Không open USB trước hoặc trong ngắt kết nối, không tái dùng connection/generation cũ.
- Guard mỗi 10 giây khi chạy Gradle: kiểm `adb devices -l`, `adb -s <VERIFIED_SERIAL> get-state`, model/API; nếu mất thì dừng cây tiến trình theo PID cụ thể. Script theo dõi nằm trong session files, không ghim serial/IP vào repository. Không tự reconnect/pair, không chuyển emulator hoặc thiết bị khác.

### Implementation và kiểm thử trước USB

- Thêm ba class debug-only trong drivers: `Tbs5530RawDescriptors` parser JVM giới hạn/bounds/unsigned/counts, `Tbs5530RawDescriptorInspection` capability một lần có worker/deadline/identity/permission/invalidation/close, `Tbs5530AndroidRawDescriptorPort` chỉ open/getRawDescriptors/close. Thêm hai suites ở testDebug, tổng 19 phương thức.
- Controller debug là owner: tách metadata, permission-only và raw; chặn song song bằng active/pending và reservation token giữa các owner trong process. Không singleton connection/Context. Activity có nút raw chủ động, không attach handler. JSON app-private giữ trường số đã lọc/hash/operation generation/permission/thread/duration/counters/pending/error, không raw bytes/string/path/serial. App build thêm Git baseline vào BuildConfig debug, có nhãn working-tree không được SHA chứng thực. Không đổi version/toolchain/dependencies hoặc production lifecycle/transport/registry.
- `problems` chỉ báo source set debug chưa ở classpath của Java editor, không có bằng chứng biên dịch từ đó; Gradle là phép kiểm thật. Focused đầu chạy19 tests,18 PASS/1 FAIL ở fixture duplicate endpoint: test sửa nhầm offset62 (attributes) thay vì61 (address). Chỉ sửa fixture, chạy lại19/19 PASS cùng assembleDebug trong1m49s/101 tasks. Không che failure hoặc gọi đây là bug parser Android.
- Các full runs trước hardware đều có guard: lượt đầu PASS5m42s/419 tasks (68 executed/351 up-to-date); sau hoàn thiện reservation giữa owner, lượt mã cuối PASS2m55s/419 tasks (38 executed/381 up-to-date). 199 JVM methods, 47 instrumentation trên OnePlus, debug/release/lint PASS trước open thật. Không thay profile chỉ để qua guard.

### Permission callback và đúng một raw cycle được duyệt

- Cài `app-debug.apk` bằng `adb -s <VERIFIED_SERIAL> install -r`, start tường minh `info.martinmarinov.dvbdriver/.Tbs5530UsbDiagnosticActivity`; cold launch666ms. Metadata mới: GM1900/API30, Host=true, 734c:5530, permission=false, generation0/DETECTED, pending/cleanup=false, open/claim/close0. Không có session acquisition active.
- Ask_user trình bày open/getRawDescriptors/close một lần, không claim/config/alternate/vendor/I²C/bulk/reset/firmware; người dùng phê duyệt đúng chu kỳ. Gateway discovery không trả đúng UI inspection tool, dùng Android CLI layout theo hướng dẫn, lọc cửa sổ ứng dụng ở các lượt sau; không lưu hierarchy thô hoặc thông tin ứng dụng khác vào hồ sơ.
- Bấm nút chỉ yêu cầu quyền trong app. Người dùng tự chọn Allow rồi xác nhận; agent không bấm hộp thoại hệ thống. JSON operation1/generation1: callback_count1/correlation_device_matched=true, state_before_cleanup=PERMISSION_GRANTED, state=CLOSED, open/claim/close0, pending/cleanup=false. Đây là PASS callback thật mới, không dùng lại lượt quá hạn của Phase2.5.
- Làm mới metadata, kiểm permission=true, đúng attachment734c:5530 và không pending; capability tự kiểm full identity/permission trước/sau open/read. Bấm raw đúng một lần. Kết quả operation2/generation2 READ_AND_CLOSED; openDevice1/1, getRawDescriptors1/1, close1/1, claim/release/transfer0; worker tbs5530-usb-worker,13ms, identity/permission=true, invalidated=false, failureNONE, pending/cleanup=false. Không thử lần hai.
- Raw189 bytes, SHA256 `e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`, parser COMPLETE_RETURNED_TREE/issues0. Device descriptor18 + config171; bcdUSB0200/bcdDevice0001/EP0packet64; configvalue1/bNumInterfaces1, alternate0–3 của ID0. Cả18 endpoint khớp metadata về ID/alt/address/direction/type/packet/interval; khôngIN82. Chi tiết và giới hạn cache/live bus ở RESEARCH. Không suy88=TS hoặc firmware cold/warm.

### Nghiên cứu ChangeMode và firmware, không execution

- TBS download chính hãng còn link ChangeMode ZIP. Chỉ tải nội dung vào RAM bằng HttpClient, ZipArchive liệt kê đúng một EXE, không README; đọc các chuỗi tĩnh trong RAM, không ghi hoặc chạy EXE. ZIP213579bytes/hash `c6555841ca048da247692c7200267adf22a1c24bfe56a66c0a00a0a2e7aee6c2`; EXE522728bytes/hash `6da03e94ef1d570c2a4dbccef3a529bf2a56b8cb5e0f5b2ecacf5eddd773afcd`. Thấy nhãn DVB-S/S2,DVB-T/T2 và BDA; chưa chứng minh giao thức/firmware/descriptors/rollback hoặc82/88. Không chuyển tuner sang Windows.
- Official/C/Go dùng82; official cold table theoID và no_reconnect, C/Go load firmware trước frontend. CPU/register và chip-ID sequence chỉ đọc source, không gửi lệnh. Không có blob firmware trong ba checkout đã kiểm nên hash ảnh thật và giấy phép phân phối NOT VERIFIED. Không tải firmware. Các câu hỏi TBS và điều kiện duyệt bring-up đã ghi trong RESEARCH/ADR-017.

### Commands và regression cuối

PowerShell tại repository chính; JAVA_HOME lấy từ Machine cho child process, ANDROID_HOME/ANDROID_SERIAL đặt trong child. Toolchain đã đọc từ build/wrapper và source.properties, không nâng/cài/gỡ.

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --tests *Tbs5530Raw* :app:assembleDebug --console plain --no-daemon --max-workers=1
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:connectedDebugAndroidTest :drivers:connectedDebugAndroidTest :dvbservice:connectedDebugAndroidTest :app:assembleDebug :app:assembleRelease :app:lint --console plain --no-daemon --max-workers=1
adb -s <VERIFIED_SERIAL> shell run-as info.martinmarinov.dvbdriver cat files/tbs5530-diagnostic.json
javac --release 8 -d <SESSION_TEMP_CLASSES> <EIGHT_CORE_AND_TWO_DEBUG_JVM_SOURCES>
javap -verbose -classpath <SESSION_TEMP_CLASSES> info.martinmarinov.drivers.usb.tbs5530.Tbs5530RawDescriptors
apkanalyzer.bat manifest print app\build\outputs\apk\release\app-release-unsigned.apk
git --no-pager diff --check
```

- Sau raw chạy lại full command: **BUILD SUCCESSFUL2m2s,419 tasks,23 executed/396 up-to-date**. XML mới: drivers185/usbxfer1/dvbservice13=199 JVM methods; TBS package168. App instrumentation17 + facade28 + native1 + XML1 =47,0 failures/errors/skipped. Connected tasks đã thực thi, không đánh up-to-date thành hardware evidence.
- Lint XML:0 errors/fatal,9 warnings. Debug/release APK PASS; analysis/report lint lượt cuối up-to-date trên cùng mã đã PASS trước đó. Java8 compile10 sources PASS, parser bytecode major52. Debug DEX có6 class diagnostic; release DEX không6 class và manifest không Activity. Native smoke trên arm64 và device-filter XML tương đương PASS. Không chạy native USB ioctl.
- UTP có cài/gỡ app. Sau regression đã cài lại APK debug cuối và cold launch678ms, chỉ metadata: generation0/DETECTED, target734c:5530, permission=false, pending/cleanup=false, open/claim/close0. Không yêu cầu quyền lại hoặc đọc raw lần hai. Bằng chứng raw/permission trước đó giữ trong session JSON đã lọc và hồ sơ.
- Có 16 file thay đổi: app build + 3 debug classes + 1 instrumentation suite, 3 drivers debug classes + 2 testDebug suites, 6 hồ sơ dự án. Stage trống; không instructions/reference/production registry/profile thay đổi. Raw logs, binary APK, executable tool, firmware và thông tin ghép nối không được thêm Git.
- **Điểm dừng:** mục tiêu chẩn đoán read-only hoàn tất trong phạm vi duyệt; production streaming và firmware/mode vẫn BLOCKED/UNKNOWN. Không firmware/CPU reset/vendor/I²C/config/alternate/bulk/tune/LNB/TS/ChangeMode execution. Bước kế tiếp chỉ chuẩn bị tài liệu TBS, firmware provenance/license/hash/address map/rollback và kế hoạch trước/sau cùng board cần phê duyệt mới; không tự bắt đầu phase tiếp theo.

## 2026-11-04 00:18 +07 — Commit và push Phase 2.6 theo yêu cầu

- Người dùng phê duyệt commit toàn bộ changes Phase 2.6 và push lên origin của nhánh hiện tại. Preflight: `chore/modernize-android-build`, local và live origin cùng `df076d28dd8100d5caa363f1d866a274e3f2b636`, stage trống, đúng 16 file gồm mã/debug tests/build metadata và sáu hồ sơ. Không thay ba references; không reset/merge/rebase hoặc force-push.
- Lượt đầu dừng trước stage do Wireless ADB offline. Sau xác nhận của người dùng, tra serial TCP mới và kiểm GM1900/API 30/state=device, USB Host=true, app đang chạy; metadata 734c:5530, permission=false, generation 0/DETECTED, pending/cleanup=false, open/claim=0. Không tự reconnect/pair hoặc tái dùng handle USB cũ.
- Message `feat: add debug-only raw USB descriptor inspection`, có Co-authored-by trailer. Chỉ stage 16 file đã kiểm; JSON đính kèm app-private/session ở ngoài repository không được sao chép vào Git. Kiểm mẫu secrets/IP-port và `git diff --check` trước commit; push tường minh `origin HEAD:refs/heads/chore/modernize-android-build`, không force. Kết quả commit/push và SHA được xác minh sau thao tác, không ghi trước thành công.
- Không chạy lại build/tests hoặc USB cycle trong tác vụ Git. Kết quả 199 JVM/47 instrumentation, debug/release/lint và raw read là bằng chứng Phase 2.6 vừa ghi phía trên, không phải lượt chạy mới.

## 2026-11-04 — Phase 2.7: FX2 bootstrap offline, không phê duyệt hoặc ghi phần cứng

### Baseline, guard và môi trường

- Windows PowerShell tại AndroidDvbDriver, nhánh `chore/modernize-android-build`, HEAD và remote-tracking cùng `f03e092702edac5134efea633d8a30a51e1716e4`; working tree và stage sạch đầu phiên. Không fetch/reset/clean/merge/stage/commit/push. Ba repository tham khảo chỉ đọc, có HEAD đúng SHA đã khóa: official `2ce787de9f6e81b4294692a45de15596ae2e1322`, C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`.
- Đã đọc copilot/scoped build/toolchain/records/hardware/ADB instructions, ADR-010–017 và các hồ sơ liên quan; load project-handoff/tbs-research/android-cli. GitNexus thiếu index cho repository này; AgentMemory không có kết quả liên quan. Research agent không khởi chạy được vì model không khả dụng; không thử model khác, nghiên cứu trực tiếp.
- `adb devices -l` xác nhận đúng một TCP target ở state=device; `adb -s <VERIFIED_SERIAL> shell getprop` xác nhận manufacturer=OnePlus, model=GM1900, API=30. Không coi alias mDNS là thiết bị khác. Bộ giám sát local kiểm mỗi 10 giây, phát hiện target mất khi đang sửa core: dừng ngay cả sửa mã/build/test và hỏi người dùng kết nối lại. Sau xác nhận mới tra port mới, kiểm định danh, Host=true và metadata app-private: generation=0, state=DETECTED, pending=false, open=0. Không có thao tác USB hoặc lệnh ghi phần cứng đang chờ trước/sau ngắt ADB. Không tự reconnect/pair, dùng serial cũ hoặc tự tiếp tục chỉ vì alias online.
- Đọc toolchain từ build/wrapper, `source.properties` local và java-version: AGP 9.5.0, Gradle 9.8.2, JDK 25.0.5.0.3, compileSdk 37.2, Build-Tools 37, NDK 30.0.16248370 và CMake 4.1.2; targetSdk 37, minSdk 21, Java source/target 8 không đổi. `Inspect-TBS5530-Toolchain.ps1 -NoReport -PassThru` bị policy chặn vì script chưa ký số; không bypass hoặc đổi policy. Không cài/gỡ package hoặc dependency, không dùng WSL/usbipd/AVD hoặc đổi driver host.

### Nghiên cứu firmware/FX2 và thay đổi mã

- Tải archive TBS v1.0 qua HTTPS ngoài repository: 566825 byte, hash `23ecf817383dc29881ac9cb6f704239969e5f711270ad2073497653ba08791cc`, có 54 entries và đúng entry cho TBS5530. Chỉ trích `./dvb-usb-id5530.fw`: 8192 byte, hash `b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc`. Ảnh tuyến tính theo loader, vùng 0000–1FFF, gồm 128 × 64 byte. Không có license/README trong archive; không đưa blob vào Git/APK. Trang download không có version mới cho TBS5530 đủ xác minh; không tuyên bố đây là bản mới nhất tuyệt đối.
- URL PDF `/24/44/` trong prompt trả rỗng; đã tìm bản chính hãng `/30/44/`, dài 5790150 byte, gồm 462 trang. `curl` có exit 0 nhưng file dài 0 byte, nên đổi sang `Invoke-WebRequest` và kiểm kích thước. Dùng `pdftotext.exe` đã có với tùy chọn raw/layout. Table 3-2/Appendix B khớp 18 endpoints của snapshot Phase 2.6; không đọc raw mới. TRM xác nhận E600 là CPUCS, nhưng chưa có cách ánh xạ 7F92 trên FX2/board đủ an toàn. Bài knowledge base của nhà sản xuất phân biệt họ chip cũ; tải trực tiếp bài nhận lỗi 403, nên không coi trích đoạn tìm kiếm là bằng chứng về chip thực. URL và chi tiết nguồn ở [RESEARCH.md](RESEARCH.md).
- Sửa 5 core files: Session thêm kiểm owner không đòi claim cho EP0; Coordinator có tùy chọn package-private nhận connection mà không claim và giữ quyền sở hữu qua lease; Binding thêm điểm vào bootstrap package-private, epoch và kiểm quyền/định danh, với facade mặc định từ chối. Runner cũ không được hủy lần thử mới. Transport tách `claimedRequired` nhưng giữ mặc định cũ. FirmwarePlan chỉ cập nhật mô tả offline, không cấp quyền thực thi phần cứng hoặc đổi trình tự.
- Thêm 3 class debug: Fx2Fingerprint so các trường số tiêu chuẩn; FirmwareValidation ghim nguồn/hash/size/format/ranges; BootstrapDryRun chỉ chạy trên fake, một lần. Không có Android bootstrap adapter hoặc giao diện upload; app controller chỉ bổ sung fingerprint trong raw JSON đã có, không tự đọc raw hoặc upload. Thêm 2 suites testDebug với 19 methods và 1 suite instrumentation với 2 tests. Không sửa operational Descriptors/Bridge/AndroidUsbFacade/registry/XML/native/dependencies/build files.
- Dry-run kiểm chính xác request 40/A0, index=0, timeout=2000 ms, payload và thứ tự: giữ CPU qua 7F92 → giữ qua E600 → các khối dữ liệu → release qua 7F92 → release qua E600 → delay 100 ms. Không ghi khắc phục sau bất kỳ lỗi nào; kết quả âm không mặc định là timeout. Transaction ngoài của Session ngăn close giữa các bước hoặc khi native I/O còn chạy; vô hiệu hóa ngay, giữ pending và cleanup sau khi lời gọi trả về. Runner giữ `UNCERTAIN` sau lỗi đã thử ghi, không để trạng thái cleanup `CLOSED` che lấp; hoàn tất mọi bước chỉ là `TRANSITIONING`. Lỗi cleanup được giữ và chặn mở lại bootstrap. Test riêng kiểm runner/capability cũ không ghi hoặc hủy owner có epoch mới khi OS tái dùng ID.

### Lệnh và kiểm chứng mới

Mọi lệnh Gradle chạy qua wrapper local trong session, kiểm Wireless ADB mỗi 10 giây và trước/sau lệnh. Chỉ trong tiến trình con mới đặt JAVA_HOME lấy từ Machine, ANDROID_HOME và ANDROID_SERIAL. Nếu kiểm tra lỗi thì dừng đúng cây tiến trình theo PID; không sửa task repository chỉ để thêm guard. Không ghi IP/port vào Git.

```powershell
adb devices -l
adb -s <VERIFIED_SERIAL> shell getprop ro.product.model
adb -s <VERIFIED_SERIAL> shell getprop ro.build.version.sdk
.\gradlew.bat :drivers:testDebugUnitTest --tests *Tbs5530Fx2FingerprintTest --tests *Tbs5530BootstrapDryRunTest :app:assembleDebug --console plain --no-daemon --max-workers=1
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:connectedDebugAndroidTest --rerun :drivers:connectedDebugAndroidTest --rerun :dvbservice:connectedDebugAndroidTest --rerun :app:assembleDebug :app:assembleRelease :app:lint --console plain --no-daemon --max-workers=1
javac --release 8 -d <LOCAL_CLASSES> <13_CORE_AND_DEBUG_JVM_SOURCES> <LOCAL_FirmwareOfflineProbe.java>
java -cp <LOCAL_CLASSES> info.martinmarinov.drivers.usb.tbs5530.FirmwareOfflineProbe <LOCAL_dvb-usb-id5530.fw>
javap -verbose -classpath <LOCAL_CLASSES> info.martinmarinov.drivers.usb.tbs5530.Tbs5530BootstrapDryRun
apkanalyzer.bat manifest print app\build\outputs\apk\release\app-release-unsigned.apk
adb -s <VERIFIED_SERIAL> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <VERIFIED_SERIAL> shell am start -W -n info.martinmarinov.dvbdriver/.Tbs5530UsbDiagnosticActivity
adb -s <VERIFIED_SERIAL> shell run-as info.martinmarinov.dvbdriver cat files/tbs5530-diagnostic.json
git --no-pager diff --check
```

- Lượt focused có 18 methods, 0 failure/error; debug build `PASS` trong 1 phút 48 giây, gồm 101 tasks: 19 executed, 82 up-to-date. Lượt full đầu có 217 phương thức JVM và 49 instrumentation tests, mất 7 phút 39 giây, gồm 419 tasks với 82 executed. Sau đó bổ sung epoch guard, test runner cũ, report và cleanup; không dùng lượt đầu thay kết quả mã cuối.
- **Full mã cuối PASS trong 7 phút 55 giây**, gồm 419 tasks: 86 executed, 333 up-to-date. XML ghi drivers 204 + usbxfer 1 + dvbservice 13 = 218 phương thức JVM; riêng TBS có 187. Các suites instrumentation lồng nhau ghi app 17 + drivers (28 + 2) + dvbservice (1 + 1) = 49 tests trên OnePlus/API 30; 0 failure/error/skipped. Đếm testcases thực, không coi XML parent thiếu counters là 0 tests. Unit/connected tasks chạy với `--rerun`.
- Build debug/release `PASS`; lint XML có 0 Error/0 Fatal/9 Warning, không suppress hoặc thêm baseline. Giữ cảnh báo source 8, deprecated APIs, Unsafe và Gradle 10. Biên dịch 13 source bằng Java 8 đạt `PASS`, bytecode major 52. Kiểm ảnh thật offline đạt `PASS`: 133 steps = 132 writes + delay 100 ms; sửa byte cuối bị từ chối do SHA không khớp. Chạy chính ảnh qua fake port, kiểm từng byte/request/thứ tự trong 132 calls, với 1 open/1 close/0 claim/0 calls tới UsbDeviceConnection; không đưa blob fixture vào repository.
- Quét DEX xác nhận 9 nhóm class có trong debug, không có trong release. Release manifest không có diagnostic, dùng minSdk 21/targetSdk 37; APK debug/release có 4 native ABIs. Native smoke chạy trên arm64, không gọi USB ioctl. Không chạy runtime API 21 hoặc USB trên thiết bị dùng trang nhớ 16 KiB; không báo `PASS` mới cho các mục này.
- Sau UTP đã cài lại debug APK và cold launch trong 716 ms, chỉ đọc metadata: Host=true, VID:PID `734c:5530`, alt 0–3, không có `0x82`, permission=false, generation=0, state=DETECTED, pending/cleanup=false, open/claim/close đều bằng 0. Không xin quyền hoặc chạy raw cycle lần nữa. Raw descriptors dài 189 byte và hash là bằng chứng lịch sử Phase 2.6; metadata mới không phải phép đọc raw mới.

### Approval, điểm dừng và bước tiếp theo

- **Hardware preflight BLOCKED:** kích thước, hash và vùng địa chỉ của ảnh RAM hợp lệ, nhưng chưa đủ bằng chứng về cách ánh xạ 7F92, chip/revision board thực và phương án khôi phục. Không đưa câu hỏi phê duyệt upload khi thiếu điều kiện; chưa xin/chưa nhận phê duyệt phần cứng. `hardwareUploadAllowed=false`, Android facade bootstrap mặc định từ chối. Mọi firmware/CPU reset/vendor/I²C/LNB/bulk/tuner/TS/ChangeMode thật đều `NOT RUN`.
- Không có bằng chứng sau nạp về attachment, permission, descriptors, endpoint `0x82` hoặc bridge readiness. Lệnh ghi trên fake không gọi UsbDeviceConnection và không chứng minh upload/re-enumeration thật. Không tự nạp sau reconnect ADB hoặc bước sang frontend.
- Cập nhật STATUS/RESEARCH/DECISIONS (ADR-018)/TEST-MATRIX/WORKLOG/ENVIRONMENT; giữ lịch sử cũ và lưu logs/PDF/archive/firmware local ngoài Git. Kiểm cuối có 18 files (12 tracked sửa + 6 mới), stage trống, 3 references không có thay đổi; `git diff --check` đạt `PASS`. Quét phần thêm không có IP:port, ADB alias, private key hoặc blob. Lần quét toàn hồ sơ ban đầu bắt trúng chuỗi lịch sử, nên kiểm lại đúng phần diff; không sửa lịch sử để che kết quả. Chưa stage/commit/push. Bước tiếp theo duy nhất: TBS xác nhận 7F92/chip/board/phương án khôi phục và quyền firmware; sau khi rà soát lại kế hoạch và điều kiện mới cân nhắc một phê duyệt upload riêng.

## 2026-11-04 — Sửa thứ tự và cách diễn đạt tài liệu Phase 2.7

- Theo phản hồi của người dùng, chuyển toàn bộ mục Phase 2.7 trong [RESEARCH.md](RESEARCH.md) lên ngay dưới tiêu đề, trước Phase 2.6. Giữ nguyên thứ tự và nội dung các mục nghiên cứu cũ; WORKLOG vẫn thêm mục mới ở cuối.
- Rà soát và sửa các đoạn Phase 2.7 vừa thêm trong STATUS, ENVIRONMENT, DECISIONS, TEST-MATRIX, RESEARCH và WORKLOG: tách chữ/số/đơn vị bị dính, thêm dấu câu và viết lại câu khó đọc. Không thay số liệu kiểm thử, nguồn, SHA hoặc kết luận về phần cứng.
- Kiểm so sánh với bản trước biên tập: số mục, toàn bộ nội dung lịch sử, danh sách SHA/URL và khối lệnh được bảo toàn; hash xác nhận 12 file code/test không đổi. `git diff --check` đạt PASS; rà chữ/số dính còn lại chỉ thấy định danh hoặc địa chỉ kỹ thuật hợp lệ. Wireless ADB được xác minh đúng GM1900/API 30; không mở USB hoặc chạy diagnostic phần cứng.
- Chỉ sửa tài liệu, không chạy lại build/tests, không stage/commit/push. Các kết quả Phase 2.7 phía trên vẫn là bằng chứng của lượt kiểm thử trước, không phải PASS mới của tác vụ biên tập.

## 2026-11-04 — Commit và push Phase 2.7 theo yêu cầu

- Người dùng phê duyệt commit và push toàn bộ thay đổi hiện có của Phase 2.7 cùng đợt biên tập sáu hồ sơ. Nhánh `chore/modernize-android-build`; preflight xác nhận HEAD và live origin cùng `f03e092702edac5134efea633d8a30a51e1716e4`, stage trống, đúng 18 file gồm code/tests/debug và tài liệu. Không reset/merge/rebase hoặc force-push.
- Wireless ADB xác minh đúng OnePlus GM1900/API 30/state=device qua một serial TCP được tra từ danh sách hiện tại; không dùng alias mDNS làm target khác. Không USB operation hoặc tái dùng handle/generation. Kiểm `git diff --check`, phạm vi file và dữ liệu nhạy cảm trong nội dung thêm; không firmware/archive/APK/raw logs hoặc IP/port/serial trong commit.
- Message dự kiến `feat: add offline TBS5530 FX2 bootstrap validation`, có Co-authored-by trailer. Stage tường minh 18 file đã kiểm và push `origin HEAD:refs/heads/chore/modernize-android-build`; hash và kết quả remote được xác minh sau thao tác, không ghi trước thành công.
- Kiểm staged diff phát hiện CRLF trong 6 file Java mới bị Git báo trailing whitespace với `core.autocrlf=false`. Dừng trước commit, chỉ chuẩn hóa 6 file đó sang LF và xác minh nội dung chữ không đổi. Không sửa cấu hình Git hoặc tắt kiểm tra; `git diff --cached --check` đạt PASS sau chuẩn hóa.
- Không chạy lại build/tests cho tác vụ Git. Kết quả 218 JVM/49 instrumentation/debug/release/lint của Phase 2.7 là bằng chứng đã có, không phải lượt PASS mới. Hardware upload vẫn BLOCKED/NOT RUN, không xin hoặc nhận phê duyệt USB writes từ yêu cầu commit/push.
