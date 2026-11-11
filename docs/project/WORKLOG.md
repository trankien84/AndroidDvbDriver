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
- `:app:assembleDebug` đầu tiên, lượt 967, exit 1 do thiếu androidx.localbroadcastmanager; thêm dependency trực tiếp 1.1.0, lượt 968 exit 0, BUILD SUCCESSFUL trong 2 m 39 s/95 tasks, 4 ABIs. runTask editor không tìm thấy task nên dùng terminal cùng command và sửa task env để người dùng dùng lại; không tạo tasks.json mới.
- `:usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest :dvbservice:assembleDebugAndroidTest` lượt 971 exit 1: service có 13 tests, gồm 2 fail (hardcode newline Windows và Mockito anyString không match null). Chỉ sửa test: openMocks/MockedStatic/cleanup, isNull matcher, System.lineSeparator; runtime không đổi. XML sau lượt 974: drivers 17/usbxfer 1/dvbservice 13, 0 fail (có 1 ConvertCString utility trong 31 methods).
- `wrapper --gradle-version9.8.2 --distribution-type bin --gradle-distribution-sha256-sum <official> --console plain --no-daemon` lượt 973 PASS; tạo lại wrapper JAR/scripts bằng tool chính thức. CRLF của gradlew.bat do tool tạo bị diff --check mặc định xem CR là whitespace; không thay generated script tùy tiện, kiểm lại cr-at-eol riêng.
- Lượt combined 974 gồm unit/instrumentation compile/lint/release/bundle exit 1 ở native release x86: Unable to delete directory. Instrumentation APK đã package, unit xanh, lint/release tổng chưa hoàn tất. Retry 977 với max-workers 2 lỗi tương tự x86_64; lượt 979 chạy riêng buildCMakeRelWithDebInfo[x86_64] với max-workers 1, PASS trong 25 s. Không xóa output/kill process khác; retry 980 release/lint tuần tự với max-workers 1, ghi kết quả khi hoàn tất.
- **APK/native check đã chạy:** zipalign37 -c -P16 4 debug APK exit 0; ZIP có libUsbXfer.so cho arm64-v8a/armeabi-v7a/x86/x86_64; llvm-readelf NDK 30: LOAD 64 bit 0x4000, 32 bit 0x1000. Debug SHA256 BC9FD306B79B4CEA044831547F49465F4650040AD1B90553DA509C8476BAB443. Đây là kiểm tra tĩnh, không phải runtime 16 KiB/USB host.
- **References recheck:** SHA cả 3 không đổi; TBS có 81399 D, index thiếu, C/Go có 0 changes. Không tải TBS objects/thay sparse, không download firmware/images. Toolchain downloads là 4 SDK packages + Gradle binary/deps phục vụ chính build 4 modules, không có SDK thứ hai.
- **Files:** 4 module build.gradle, root build.gradle, gradle.properties, wrapper properties/JAR/scripts, CMakeLists, 3 service test files, README, .vscode/tasks.json và 6 hồ sơ dự án. Không sửa source runtime/public API/driver implementation hoặc instructions.
- **Kết quả cuối lượt 980:** `:app:lintDebug :app:assembleRelease :app:bundleRelease --console plain --no-daemon --max-workers=1` **exit 0**, 3 m 54 s, 286 tasks (155 executed/131 up-to-date). Native release cho 4 ABIs hoàn tất. Lint XML: 0 errors/11 warnings (2 dependency version có chủ đích vì API 21, 9 UI/resources), không suppress/baseline/disable gates.
- **Chốt lượt 983:** `:usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest :dvbservice:assembleDebugAndroidTest :app:assembleDebug --console plain --no-daemon --max-workers=1` exit 0/36 s, 138 tasks (8 executed/130 up-to-date). XML: 31 methods, 0 fail. Wrapper được tạo lại giảm native-access warning của launcher nhờ defaults mới; cảnh báo javac 8 obsolete/AGP Gradle deprecated vẫn được ghi rõ.
- **Outputs static verification:** release APK unsigned có 5466380 bytes, SHA256 1D7D415C19A07FD55B1DEDB2FA885C48CD170D8A7802C5077B0E23CB1051C999; release AAB có 4782669 bytes, SHA256 A3E29DA0FBF1F728A9E6477CD59FF6FC1CD2183490610D571CD0AA1DFA06EA93; instrumentation APK có 7411924 bytes, SHA256 760B280C897636AC6CB7C4539AA19FE76FE042BBD48D6DAECBE10A9C30D2B641. zipalign release exit 0; ELF 64 release LOAD 0x4000. Merged manifest min 21/target 37; aapt2 badging có native cho 4 ABIs. Không ký release/publish hoặc chạy Android.
- **Rà phạm vi:** diff --check có bật cr-at-eol PASS cho wrapper.bat CRLF do tool tạo. Wrapper.properties giữ lại license notice cũ sau generator; wrapper scripts mới có Apache 2.0 notice chính thức. Một lệnh rà thử cast manifest-merger txt thành XML thất bại do sai định dạng; kiểm manifest XML thật sau đó xác nhận min 21/target 37, không có source changes từ phép dò.
- **Bàn giao:** STATUS/ENVIRONMENT/RESEARCH/DECISIONS/TEST-MATRIX/WORKLOG và README cập nhật. Chưa commit/push; references giữ read-only/SHA/status như cũ. Runtime Android 21/37/UI/permission/service/native-load 16 KiB/TBS hardware vẫn NOT RUN. Next: review diff và chọn môi trường Android được phép kiểm thử phần mềm không tuner; không tự boot emulator.

## 2026-11-02 18:48 +07 — Checkpoint theo yêu cầu commit

- **Phạm vi người dùng yêu cầu:** chuẩn bị commit để sau đó push origin; tác vụ này chỉ commit local, không push. Message `chore: prepare TBS5530 development with JDK 25`, kèm trailer Co-authored-by: Copilot.
- **Môi trường:** Windows PowerShell/root AndroidDvbDriver, nhánh chore/modernize-android-build; HEAD trước e4c13a5b86ad5ef9be887df58226046dcb42dcd5. Main có 34 entries (16 modified/18 untracked), index ban đầu chưa stage. Không sửa references.
- **Remote quan sát mới:** origin git@github.com:trankien84/AndroidDvbDriver.git, upstream https://github.com/signalwareltd/AndroidDvbDriver.git. Chỉ đọc remote-v, không thay remote hoặc fetch/push. Không dùng origin cũ trong audit làm đích push tự động.
- **Review:** status/diffstat, danh sách 34 file và kích thước; heuristic secret scan có 0 match (không phải chứng nhận security audit); parse JSON tasks PASS; diff --check với cr-at-eol PASS. Không stage firmware/captures/logs/localproperties/APK/AAB/native output/cache, không force-add workspace bị ignore. Thay đổi của người dùng trong `.gitignore`, thêm *.fw/*.ts, đã được rà và đưa vào phạm vi commit.
- **Validation đã chạy:** set JAVA_HOME từ Machine + SDK env trong process; `gradlew.bat :usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest :app:assembleDebug --console plain --no-daemon --max-workers=1` exit 0/59 s, 107 tasks (8 executed/99 UP-TO-DATE); unit tasks UP-TO-DATE chứ không chạy lại mọi test. Không có lint/release/hardware/Android runtime mới; kết quả migration trước giữ nguyên.
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
- `gradlew :app:assembleDebug :dvbservice:assembleDebugAndroidTest --console plain --no-daemon --max-workers=1`: shell 1006 exit 0, 2 m 4 s. ZIP APK có `lib/x86_64/libUsbXfer.so`, 7320 bytes.
- `:dvbservice:tasks --all` xác nhận connectedDebugAndroidTest tồn tại. Lệnh lọc hai class ở lượt 1012 exit 0 nhưng XML chỉ có 1 registry test; không tuyên bố native PASS từ exit code. Gọi trực tiếp `am instrument` ở lượt 1017 trước khi cài lại test APK bị lỗi component missing vì Gradle đã gỡ test package sau lượt chạy.
- `adb -s emulator-5554 install -r <dvbservice-debug-androidTest.apk>` Success; gọi trực tiếp `am instrument -w -r -e class info.martinmarinov.dvbservice.NativeLibrarySmokeTest info.martinmarinov.dvbservice.test/androidx.test.runner.AndroidJUnitRunner` ở lượt 1018 chạy **OK (1 test)**, assertion thành công, 0.037 s.
- Rerun **không filter**, `$env:ANDROID_SERIAL='emulator-5554'; gradlew :dvbservice:connectedDebugAndroidTest --console plain --no-daemon --max-workers=1` ở lượt 1019 exit 0/49 s, 92 tasks (9 executed/83 UP-TO-DATE). XML `dvbservice/build/outputs/androidTest-results/connected/debug/TEST-TBS5530_Runtime_API37(AVD) - 17.xml`: testsuites **2 tests/0 failures/0 errors**, tên chính xác registry.allDevicesAreInXml và NativeLibrarySmokeTest.usbTransferLibraryLoadsWithoutOpeningUsb. Giữ assertion registry cũ, không disable test.
- Chép XML evidence ra session files/phase-1-1 (ngoài Git), SHA256 `689B2B2DED52D8E0649BACABEE09226C2E00D68CADEA270C2D1000890C35BC17`.

### UI, lifecycle và lỗi tìm thấy

- `adb -s emulator-5554 install -r app-debug.apk` ở lượt 1008 Success; `am start -W -n info.martinmarinov.dvbdriver/.WelcomeActivity`: Status ok/COLD, TotalTime 6606 ms, PID 3840.
- MCP describe_ui/screenshot phát hiện overlay **System UI isn't responding**, focused package com.android.systemui, không phải app. Chọn Wait tại tâm phần tử trong hierarchy; overlay biến mất. Không kill SystemUI/disable security/đổi OS. Ghi SystemUI ANR ở lần boot đầu là vấn đề môi trường, không ẩn nó.
- Welcome→Diagnostic→Start mở dialog `No devices found` và stacktrace DvbException đã được xử lý tại DeviceController:64. PID app vẫn sống; hardware/signal/lock chưa được đánh dấu; Tune/Record không bật. Không có app FATAL/UnsatisfiedLinkError/SecurityException trong log PID đã lọc. Đây không phải tuner test.
- UI trước sửa có controls bị system bars che: hierarchy rowTune bắt đầu ở y 0; Close đến y 2400. Screenshot xác nhận nội dung/status icons chồng nhau. Thêm `SystemBarInsets.java`, dùng systemBars|displayCutout và padding gốc, chỉ API 35+, dark icons; gọi từ WelcomeActivity/DvbFrontendActivity/GplLicenseActivity sau setContentView. Không đổi targetSDK/minSDK/USB/service logic.
- `gradlew :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1` ở lượt 1016 exit 0/1 m 54 s, 162 tasks (27 executed/135 UP-TO-DATE), lint có 0 errors/11 warnings, không thêm suppression/baseline mới.
- Cài lại APK đã sửa ở lượt 1020 Success; am start tạm trả PackageUpdateActivity (chuyển tiếp của hệ thống); hierarchy tiếp theo đúng Welcome. Sau sửa, top inset 63 px/bottom 63 px, rowTune y 63, controls dưới cùng dừng ở y 2337 trên screen 2400. MCP screenshot và PNG trước/sau đã lưu xác nhận.
- Test Welcome→License→OK quay về Welcome; Diagnostic→Home→am start đưa task lại Diagnostic, state bình thường; Start lại vẫn hiện no-device dialog; Back đóng dialog/rời Diagnostic về Welcome. `ACTION_VIEW dtvdriver:// -p <app>` ở lượt 1024 mở DeviceChooserActivity rồi finish về Welcome khi không có device, không ép start DvbService. Không có caller để assert returned result code nên không báo coverage contract đầy đủ.
- Nút Close đưa về launcher; warm relaunch ở lượt 1026: Status ok, TotalTime 1796 ms, PID 6344 sống. Lọc log PID cuối có 0 match FATAL/linker/security/app ANR; dumpsys exit-info chỉ có reason 16 PACKAGE UPDATED do install-r, không crash. Không khẳng định mọi log trong toàn emulator không có warning.
- Permission: READ/WRITE_EXTERNAL_STORAGE=false, internet/foreground permissions granted; no-tuner UI vẫn chạy. USB permission **NOT RUN** vì không có USB Host/device. DvbService foreground/streaming **NOT RUN** vì chưa có tuner/fixture hợp lệ; dumpsys không có service record của app. Không `am startservice` với intent giả/null.

### Config, bảo toàn reference và cleanup

- Workspace JSON có 4 roots tương đối đúng và PowerShell mặc định. Exception `.gitignore` đã có sẵn trước phase; không thêm trùng/force-add/stage. Hash cuối .gitignore `DB291A4767C7E5C4B900D560E9B12781A24BDB5D287C280A0B076CBF625981A3`, workspace `B715366CEBE4B1C9A74B1ECC4F747024D10EECEB581B4FF1ED0800134F4D11F9`, giữ nguyên so với trước khi tạo branch.
- Cập nhật snapshot toolchain bằng tiếng Anh trong `.github/copilot-instructions.md` và `android-build.instructions.md`; giữ quy tắc an toàn Windows/WSL/usbipd/MCP/hardware. Không sửa 4 instructions còn lại/skills/global settings. Không nâng AGP 9.5 hoặc dependencies.
- Các lệnh kiểm reference chỉ gồm rev-parse/status/sparse list/Test-Path, dùng `--no-lazy-fetch` và GIT_OPTIONAL_LOCKS bằng 0: TBS SHA 2ce787de9f6e81b4294692a45de15596ae2e1322 vẫn thiếu index/81399 D, tbs5530.c/.h và cxd2878.c thiếu trên đĩa. C SHA c5fa27eecbe80633cbb365cc44656daea1c0e2e3 và Go 11228b05fb1626bf41c4e1879ace10cd0a8d575c sạch. Không sync/fetch/repair/reset hoặc ghi USB/firmware/I2C/vendor.
- Unit regression ở lượt 1028 exit 0/32 s, 39 tasks UP-TO-DATE: validation task thành công, không nói 31 methods được chạy lại mới. Tests đã thực chạy mới là instrumentation 2 methods.
- Cleanup: xác nhận `emu avd name` trước `adb -s emulator-5554 emu kill`; shell 1004 kết thúc exit 0. Lần list ngay 3 s sau còn serial do shutdown đang diễn ra; recheck lượt 1031 `adb devices -l` rỗng. Giữ AVD để tái sử dụng, không wipe/xóa hoặc dừng thiết bị khác.

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
- Lệnh lượt 1048: `gradlew.bat :app:assembleDebug :app:assembleRelease :usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest :app:lintDebug --console plain --no-daemon --max-workers=1` FAIL sau 12 m 9 s ở native Debug x86: Unable to delete directory. Lỗi khóa output trên Windows đã được quan sát trước, không chứng minh AGP không tương thích; không xóa cache/kill process khác/skip ABI.
- Rerun lượt 1059: `gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintDebug :usbxfer:testDebugUnitTest --rerun :drivers:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun --console plain --no-daemon --max-workers=1` **exit 0/9 m 12 s**, 325 tasks, 245 executed/80 UP-TO-DATE. Unit XML mới: usbxfer 1/drivers 17/dvbservice 13 = 31 methods, 0 failure (1 ConvertCString utility), không chỉ dùng PASS từ cache. Lint XML: 0 errors, 11 warnings; không disable test/assertion/lint gate.
- AAB ở lượt 1067: `:app:bundleRelease --console plain --no-daemon --max-workers=1 --warning-mode all` **exit 0/45 s**, 157 tasks, 18 executed, 139 UP-TO-DATE. Cảnh báo Configuration.setVisible deprecated vẫn còn trên AGP 9.5 dù notes đề cập fix; Javac 25 Java 8 obsolete cũng vẫn có, không ẩn hoặc đổi bytecode.
- Kiểm native tĩnh ở lượt 1065/1066: zipalign37 -c -P16 4 cho cả debug/release exit 0; APK có libUsbXfer.so đủ arm64-v8a/armeabi-v7a/x86/x86_64; llvm-readelf NDK 30 kiểm mới cả Debug/RelWithDebInfo cho 4 ABI, 64 bit LOAD 0x4000/32 bit 0x1000. Một phép dò ELF có regex sai escape ở lượt 1065 đã dừng; sửa query lọc Directory.Name ở lượt 1066 PASS, không ảnh hưởng source/binary.
- Debug APK SHA256 6D6E1C4953F708CA04E9DC5AD223168514C3745F27106E3AF12033C884D44D53, release unsigned APK 7B126F04109EA1F9295336EF8615C8ACCC3743BE8488497BF8DE7EA40AAB52F3; không ký/upload.

### AVD/instrumentation/UI regression

- Dùng AVD hiện có TBS5530_Runtime_API37 tại D:\Android\AVD, không tạo mới/wipe/download. Boot lượt 1051 với flag giống Phase 1.1: -port5554 -no-snapshot -no-boot-anim -no-audio -no-window -gpu swiftshader_indirect. Lượt chờ 1052 timeout 4 phút, FAIL ban đầu; sau đó lượt 1054 ADB có device/getprop boot 1, không reset/rút data.
- Runtime Android 17/API 37/x86_64/page 4096; kiểm tường minh emu avd name đúng. Lượt 1055: ANDROID_SERIAL=emulator-5554, `:dvbservice:connectedDebugAndroidTest --console plain --no-daemon --max-workers=1` **exit 0/5 m 38 s**, 92 tasks, 43 executed, 49 UP-TO-DATE. XML có 2 tests, 0 fail, 0 error, gồm allDevicesAreInXml và native load without USB. Copy XML ngoài Git: phase-1-2/instrumentation-results.xml, SHA256 03269F9A8C91B106FF415A015756384CB0CA3EC4A0CDDE15AC0B3341534B02A6.
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

- Các lượt kiểm thử tập trung trong quá trình phát triển PASS; snapshot trước 3 tests cuối có 49 methods mới. Không dùng số này làm tổng cuối. Lượt regression đầu PASS 80 methods/2 m 20 s; sau đó thêm assertion cleanup/runtime exception/detach callback và chạy lại toàn bộ.
- **Lệnh cuối**, buộc chạy lại cả ba unit tasks:

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

- **PASS exit 0, BUILD SUCCESSFUL in 1 m 45 s; 177 actionable tasks: 41 executed/136 up-to-date.** XML cuối: Bridge 17/Firmware 19/Session 16 = **52 methods mới**; drivers 69/usbxfer 1/dvbservice 13 = **83 methods**, 0 failures/0 errors. Debug APK thành công. Lint XML có 0 Error/0 Fatal/11 Warning; không thêm suppression hoặc bỏ assertion. Cảnh báo JDK 25 source/target 8 obsolete và Gradle deprecated features vẫn hiển thị.
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

**FAIL được tái hiện: 2 tests/2 failures, exit 1, 36 s/23 tasks, 2 executed.** Assertion tại invalidated.await không đạt. Đây là defect/giới hạn session monitor đã nêu trong ADR-011, không phải test hardware và không bỏ test để sửa kết quả.

### Thay đổi và xử lý race

- Sửa Tbs5530Session/Tbs5530Bridge: transactionLock riêng, state monitor ngắn; cả cặp 0x90/91 vẫn atomic. Admission và active transaction tracking giữ tài nguyên tới khi thao tác vật lý hoàn tất; invalidation nhanh không đóng connection còn dùng. Kiểm generation/state sau transfer và trước công bố dữ liệu đọc; không retry/vendor recovery.
- Close/detach/cancel đánh dấu pending cleanup; transaction cuối nhả cả hai khóa rồi cleanup cancel→release→close. Công bố cancellationRequested/cleanupPending/cleanupFailure; deferred failures được gắn suppressed trên transfer error, immediate failures throw cho caller. Reopen bị chặn khi pending; concurrent close/callback chỉ có một owner.
- Thêm readiness events/getters firmwareUploaded/frontendInitialized, giữ ready/lock/stream khác nhau; lock yêu cầu frontend initialized. `failed` hỗ trợ permission/acquisition errors trước khi nhận connection. Bổ sung 1 Session test và tăng assertion cũ, không giảm coverage.
- Thêm JVM-only Tbs5530LifecycleCoordinator và 32 tests: Request gắn attachment identity + generation; ports permission/open/claim; claim receipt; local lease cho acquisitions bị từ chối/mới hoàn tất một phần; callback đúng một lần; stale callback không đóng resource mới. Coordinator không giữ monitor qua callback vật lý, không tự READY. Chưa có framework binding/transport cụ thể.
- Concurrency suite cuối có 10 methods, gồm blocked pointer detach/cancel; deferred errors; late timeout; queued stale transaction; reentrant detach; lỗi giữa 0x90/91; join worker ngoài khóa; concurrent close trong cleanup; 40 vòng race có latch. Không sleep tùy tiện hoặc tăng timeout để che test thiếu ổn định.
- Thêm ADR-012 và cập nhật năm hồ sơ; không sửa FirmwarePlan, protocol bytes, registry/XML, driver cũ/Retry, wire enums/Request, native/JNI hoặc dependencies.

### Validation cuối

Sau tách khóa: focused Bridge/Session/Concurrency PASS 35 methods trong 42 s. TBS package + debug sau coordinator PASS. Full regression đầu PASS 125 methods; sau khi thêm concurrent-close-during-cleanup test đã chạy lại toàn bộ, không dùng count 125 làm kết quả cuối.

VS Code task `Android: assembleDebug (Windows Gradle Wrapper)` trả Task not found; fallback Gradle wrapper cùng cwd, không sửa tasks/config. Lệnh cuối:

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_AVD_HOME = 'D:\Android\AVD'
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit 0, BUILD SUCCESSFUL in 2 m 23 s; 177 actionable tasks: 41 executed/136 up-to-date.** XML cuối: drivers 112/usbxfer 1/dvbservice 13 = **126 methods, 0 failure/0 error**. TBS suites: Bridge 17/Firmware 19/Session 17/Concurrency 10/Coordinator 32 = 95 methods (thêm 43 methods so với baseline). Debug APK PASS; lint XML có 0 Error/0 Fatal/11 Warning. Warning Java 8 obsolete/Gradle deprecated vẫn hiện, không suppress.

Stress độc lập dùng JDK Machine, JUnit 4.13.2/Hamcrest 1.3 đã có trong Gradle cache, không tải dependency. Compile cả 5 core files và toàn TBS test package bằng `javac --release 8 -cp <JUnit;Hamcrest> -d drivers\build\p22-final-java8 <sources>`, exit 0; `javap -verbose` cho Session major version 52. Chạy 5 lượt:

```powershell
1..5 | ForEach-Object {
    & "$jdk\java.exe" -cp "$out;$cp" org.junit.runner.JUnitCore info.martinmarinov.drivers.usb.tbs5530.Tbs5530ConcurrencyTest
    if ($LASTEXITCODE -ne 0) { throw 'Stress failed' }
}
```

Cả 5 lượt **OK(10 tests)**, mỗi lượt có 40 detach/cancel iterations → 200 vòng; thời gian 0.173/0.148/0.152/0.219/0.179 s. Trước đó, 5 lượt với 9 methods PASS khi chưa có test close đồng thời. Một lần gọi stress sai tên class do PowerShell argument, JUnit báo initializationError trước khi chạy tests; sửa command sang tên class đầy đủ, không sửa assertion. Output compile riêng luôn dọn trong finally; không còn p22-final-java8/p22-concurrency-check. Không tạo script tạm cần giữ.

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

**PASS exit 0/4 m 48 s/241 tasks, 53 executed, 188 up-to-date.** Unit snapshot có 151 methods; instrumentation 19 drivers + 2 dvbservice = 21 tests, 0 failure/error/skipped; debug/lint PASS. Sau đó chỉ thêm 1 JVM test identity-changed-during-open/cleanup failure assertion và chạy lại toàn unit/build/lint:

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit 0/2 m 8 s/177 tasks, 22 executed, 155 up-to-date.** XML cuối: drivers 138/usbxfer 1/dvbservice 13 = **152 methods**, 0 failure/0 error; binding có 26 methods mới. Bridge 17/Firmware 19/Session 17/Concurrency 10/Coordinator 32 tiếp tục PASS. Lint XML có 0 Error/0 Fatal/11 Warning (Autofill 1/GradleDependency 2/IconLauncherShape 3/LabelFor 1/TextFields 1/UnusedResources 3), không có suppression/baseline mới. Warning source/target 8 obsolete/deprecated API vẫn hiện, không nâng toolchain để né.

Kiểm độc lập cuối: `javac --release 8 -cp <JUnit4.13.2;Hamcrest1.3 từ cache sẵn có> -d drivers\build\p23-java8-check <6pure-corefiles và toànTBSJVMtestfiles>` PASS, javap Tbs5530UsbBinding có major version 52. JUnitCore Tbs5530UsbBindingTest + Tbs5530ConcurrencyTest chạy 3 lượt, mỗi lượt **OK(36 tests)**/0.448 s/0.484 s/0.345 s, gồm 120 iterations có latch điều khiển của concurrency loop qua 3 lượt. Không tải dependency; output compile riêng đã xóa trong finally, xác nhận không còn.

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

**PASS exit 0/2 m 13 s/23 tasks, 5 executed, 18 up-to-date**, XML Transport 14/Descriptors 10/Binding 27 = 51 methods, 0 failure/error. Lượt tổng hợp cuối:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :drivers:connectedDebugAndroidTest :dvbservice:connectedDebugAndroidTest :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit 0/8 m 8 s/241 tasks, 50 executed, 191 up-to-date.** Unit XML: drivers 163/usbxfer 1/dvbservice 13 = **177 methods, 0 failure/error**. Instrumentation XML root testsuites: drivers 28 + dvbservice 2 = **30 tests, 0 failure/error/skipped**. Hai tests hiện hữu là DeviceFilterXmlEquivalenceTester::allDevicesAreInXml và NativeLibrarySmokeTest::usbTransferLibraryLoadsWithoutOpeningUsb. Build APK debug PASS, lint XML có 0 Error/0 Fatal/11 Warning; không suppress/nâng dependency/toolchain. Cảnh báo source/target 8 obsolete/deprecated API vẫn hiển thị.

Kiểm Java 8 thuần dùng JDK Machine/javac --release8 cho 8 pure core files và TBS JVM tests, JUnit 4.13.2/Hamcrest 1.3 từ cache sẵn có, không Android classpath: PASS; javap Tbs5530ControlTransport có major version 52. JUnitCore TransportTest + ConcurrencyTest chạy 5 lượt, mỗi lượt 24 methods: **OK(24 tests)**/5.174 s/2.102 s/4.118 s/3.252 s/5.256 s, gồm 200 race iterations có latch điều khiển qua 5 lượt. Output drivers/build/p24-java8-check đã xóa trong finally, xác nhận không còn.

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

- Shell 132: ADB boot_completed 1/API 37/x86_64/kernel 6.12.81-android16-6-g4f69fc7b210c-ab16167562. `cmd package has-feature android.hardware.usb.host` false; dumpsys usb timeout 10 s; /sys/bus/usb/devices chỉ có root interfaces/hubs, /dev/bus/usb001/002/003 mỗi bus chỉ có 001, không có TBS. Đọc dmesg và attributes idVendor/idProduct/speed bị denied; không adb root/đổi SELinux.
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

- Unit 177 PASS thực. Task app instrumentation FAIL exit 1/7 m 50 s: 12 tests/1 failure, clipboard null ở explicitActivityIgnoresExtrasRefreshesAndCopiesSanitizedReport line 369. Activity chưa focus/SystemUI overlay khiến Android clipboard read không đạt. Sửa bằng cách chờ window focus có giới hạn và đọc clipboard bằng Activity context; giữ assertion/12 methods, không suppress hoặc disable test.
- Rerun phần chưa đạt/chưa chạy sau sửa:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest :drivers:connectedDebugAndroidTest :dvbservice:connectedDebugAndroidTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit 0/7 m 30 s/403 tasks, 53 executed, 350 up-to-date.** XML thực: app 12/drivers 28/dvbservice 2 = **42 instrumentation tests, 0 failure/error/skipped**. Unit XML: drivers 163/usbxfer 1/dvbservice 13 = **177 methods, 0 failure/error** từ lượt unit đã rerun. Hai baseline smoke tests NativeLibrarySmokeTest và DeviceFilterXmlEquivalenceTester đều PASS mới, không lấy lịch sử thay thế. Debug/release unsigned APK PASS. Lint có 0 Error/0 Fatal/11 Warning cùng nhóm cũ, không suppress.
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
- Guard (bộ kiểm tra kết nối) kiểm danh sách ADB devices, state, model và API trước mọi thao tác thiết bị/build, đồng thời kiểm mỗi 10 s khi Gradle chạy. Wrapper theo dõi PID tiến trình con; nếu guard thất bại thì dừng tiến trình con và ném lỗi WIRELESS_ADB_STOP, không chuyển sang thiết bị khác. Không có lần mất kết nối mới trong các lệnh đã hoàn tất. IP/port/serial cục bộ không được ghi vào mã nguồn hoặc tài liệu công khai.

### Descriptors thực tế và chặn acquisition

- Đọc dumpsys usb và cây giao diện qua Android CLI, không yêu cầu người dùng gửi ảnh/screenshot. Lượt uiautomator dump riêng không tạo được file; chuyển sang Android CLI layout để đọc đúng app. Debug APK baseline khởi động lạnh trong 417 ms. Không bấm chạy chu kỳ khi descriptor (mô tả cấu trúc USB) chưa được phân tích.
- Metadata thật: VID 734c/PID 5530, configuration ID 1, 4 entries cùng interface ID 0, alternate 0–3. Alt 0 rỗng; alt 1 có Bulk 01/81/02/04/86/88, packet 512, interval 0. Alt 2 có Interrupt 01/81 với packet 64, interval 1; Interrupt 02/86 với packet 512, interval 1; Bulk 04/88 với packet 512, interval 0. Alt 3 tương tự nhưng 02/86 là Isochronous. Không có 82. Bảng đầy đủ index/ID/alternate/address/direction/type/packet/interval được ghi trong RESEARCH; JSON được đọc trực tiếp bằng run-as.
- Đối chiếu nguồn chính thức: properties trong tbs5530.c dùng TS 82, còn download firmware được tách riêng; snapshot WSL trước có 81/82, khác hiện tại. Không suy ra firmware đã đổi hoặc dùng 88 thay 82. Tbs5530Descriptors chặn đúng theo profile, không được nới validation hoặc đổi alternate để claim. Open/claim/release/close thật bị BLOCKED; không gọi getRawDescriptors khi chưa open, không gọi setInterface/setConfiguration.

### Sửa diagnostic và permission-only có regression

- Baseline chỉ có nút permission+cycle, chưa có chức năng xuất dữ liệu có cấu trúc vào vùng riêng của app. Đã thêm permission-only (chỉ xin quyền), tái dùng Binding/Coordinator với acquire=false sau grant. Cách start mặc định cũ được giữ nguyên. Snapshot kiểm profile trước chu kỳ; UI tắt chu kỳ với descriptor OnePlus nhưng vẫn cho phép xin quyền. Không tạo vòng đời USB thứ hai, không gọi controlIo/transfer trong diagnostic.
- JSON export chỉ dành cho debug, dùng AtomicFile ghi vào `files/tbs5530-diagnostic.json`; đọc bằng `adb -s <VERIFIED_SERIAL> shell run-as info.martinmarinov.dvbdriver cat files/tbs5530-diagnostic.json`. Các trường gồm timestamp/operation/generation/model/API/configs/interfaces/endpoints/permission/callback/state_before_cleanup/counters/events/thread/duration/result/failure_reason; loại bỏ path, OS ID, USB serial, correlation UUID và credentials. Mã release không export.
- Thêm unit tests cho permission-only với grant/deny/pre-granted, bảo đảm 0 open, cùng fixture mô phỏng descriptor OnePlus. Instrumentation kiểm lưu JSON, lọc dữ liệu, permission-only và profile không an toàn với 0 open. Không bỏ assertion hoặc dùng callback giả rồi gọi là hardware PASS.

### Permission thực: điều đã và chưa chứng minh

- Trước request, JSON ghi operation 0, generation 0, state DETECTED, hasPermission=false và 0 open/claim. Cài APK mới bằng -r trả Success, khởi động trong 874 ms. Metadata profile bị BLOCKED, nút chạy chu kỳ không được kích hoạt.
- 21:55, bấm nút “Chỉ yêu cầu quyền (không mở USB)” sau khi guard đạt. Dialog thật “Cho phép DVB-T Driver truy cập null?” xuất hiện vì thiết bị không có product name. Ask_user yêu cầu người dùng tự chọn; người dùng trả lời đã OK. Agent không bấm Allow hoặc cấp quyền qua ADB/root.
- Khi đọc JSON, operation 1/generation 1 đã CLOSED; callback count=0, correlation=false, permission vẫn là ĐANG CHỜ, pending=false, cleanup=false và 0 open/claim/release/close. Deadline 30 s đã hết trước khi đọc lại, receiver đã cleanup; không gọi callback grant là PASS. Refresh metadata sau đó thấy hasPermission=true: quyền OS đã được cấp, không có thiết bị khác hoặc connection mới.
- Lượt permission-only operation 2 là pre-granted (đã có quyền): không request dialog, state PERMISSION_GRANTED trước cleanup và CLOSED sau cleanup, 0 open/claim/callback, không pending. Không tự thu hồi quyền để hiện lại dialog. Denied thật vẫn NOT RUN; phân biệt rõ timeout với quyền thực tế tại thời điểm đọc. Cập nhật nhãn báo cáo diagnostic thành TIMEOUT thay ĐANG CHỜ sau deadline, không nới timeout để biến test cũ thành PASS.
- UTP instrumentation cài/gỡ target debug APK; sau khi cài lại APK cuối, metadata ghi hasPermission=false. Không suy ra quyền đã cấp phải tồn tại qua cài/gỡ; không tự xin lại. JSON cuối ghi operation 0, state DETECTED, BLOCKED_PROFILE/MISSING_SAFE_TS_ENDPOINT_PROFILE, 0 open/0 claim, USB Host=true và có target. Giữ APK cuối trên điện thoại, chưa rút USB.

### Commands regression và kết quả mới trên API 30

Chỉ đặt môi trường tiến trình con trong PowerShell: lấy JAVA_HOME từ Machine, ANDROID_HOME=D:\Android\Sdk và ANDROID_SERIAL=<VERIFIED_SERIAL>. Guard mỗi 10 s kiểm ADB devices/model/API và chọn serial cụ thể; không dùng emulator mặc định dù có alias mDNS.

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --tests *Tbs5530UsbBindingTest :app:assembleDebug --console plain --no-daemon --max-workers=1
.\gradlew.bat :app:assembleDebug --console plain --no-daemon --max-workers=1
```

Hai lượt kiểm tập trung/build đều PASS: lượt đầu 2 m 54 s, 100 tasks, 18 executed và 82 up-to-date; lượt sau 1 m 13 s, 95 tasks, 12 executed và 83 up-to-date. Lượt đầy đủ trước fixture cuối PASS trong 7 m 2 s, 418 tasks, 85 executed và 333 up-to-date, với 179 unit tests + 44 instrumentation tests. Sau khi bổ sung fixture descriptor và JSON events, đã chạy lại lượt cuối:

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:connectedDebugAndroidTest :drivers:connectedDebugAndroidTest :dvbservice:connectedDebugAndroidTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit 0, 4 m 32 s; 418 tasks: 36 executed và 382 up-to-date.** XML thực tế: drivers 166/usbxfer 1/dvbservice 13 = **180 JVM methods, 0 failure/error**; instrumentation app 14 + drivers 28 + dvbservice 2 = **44 tests, 0 failure/error/skipped**, target GM1900/Android 11/API 30. Tất cả ports trong app instrumentation đều là fake; UI tests chỉ kiểm metadata/export, không bấm xin quyền phần cứng. Fake control của drivers không gửi lệnh tới TBS. NativeLibrarySmokeTest::usbTransferLibraryLoadsWithoutOpeningUsb và DeviceFilterXmlEquivalenceTester::allDevicesAreInXml đều PASS mới trên API 30, không gọi ioctl với FD giả.

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
- `problems` chỉ báo source set debug chưa ở classpath của Java editor, không có bằng chứng biên dịch từ đó; Gradle là phép kiểm thật. Focused đầu chạy 19 tests, 18 PASS/1 FAIL ở fixture duplicate endpoint: test sửa nhầm offset 62 (attributes) thay vì 61 (address). Chỉ sửa fixture, chạy lại 19/19 PASS cùng assembleDebug trong 1 m 49 s/101 tasks. Không che failure hoặc gọi đây là bug parser Android.
- Các full runs trước hardware đều có guard: lượt đầu PASS 5 m 42 s/419 tasks (68 executed/351 up-to-date); sau hoàn thiện reservation giữa owner, lượt mã cuối PASS 2 m 55 s/419 tasks (38 executed/381 up-to-date). 199 JVM methods, 47 instrumentation trên OnePlus, debug/release/lint PASS trước open thật. Không thay profile chỉ để qua guard.

### Permission callback và đúng một raw cycle được duyệt

- Cài `app-debug.apk` bằng `adb -s <VERIFIED_SERIAL> install -r`, start tường minh `info.martinmarinov.dvbdriver/.Tbs5530UsbDiagnosticActivity`; cold launch 666 ms. Metadata mới: GM1900/API 30, Host=true, 734c:5530, permission=false, generation 0/DETECTED, pending/cleanup=false, open/claim/close 0. Không có session acquisition active.
- Ask_user trình bày open/getRawDescriptors/close một lần, không claim/config/alternate/vendor/I²C/bulk/reset/firmware; người dùng phê duyệt đúng chu kỳ. Gateway discovery không trả đúng UI inspection tool, dùng Android CLI layout theo hướng dẫn, lọc cửa sổ ứng dụng ở các lượt sau; không lưu hierarchy thô hoặc thông tin ứng dụng khác vào hồ sơ.
- Bấm nút chỉ yêu cầu quyền trong app. Người dùng tự chọn Allow rồi xác nhận; agent không bấm hộp thoại hệ thống. JSON operation 1/generation 1: callback_count 1/correlation_device_matched=true, state_before_cleanup=PERMISSION_GRANTED, state=CLOSED, open/claim/close 0, pending/cleanup=false. Đây là PASS callback thật mới, không dùng lại lượt quá hạn của Phase 2.5.
- Làm mới metadata, kiểm permission=true, đúng attachment 734c:5530 và không pending; capability tự kiểm full identity/permission trước/sau open/read. Bấm raw đúng một lần. Kết quả operation 2/generation 2 READ_AND_CLOSED; openDevice 1/1, getRawDescriptors 1/1, close 1/1, claim/release/transfer 0; worker tbs5530-usb-worker, 13 ms, identity/permission=true, invalidated=false, failure NONE, pending/cleanup=false. Không thử lần hai.
- Raw 189 bytes, SHA256 `e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`, parser COMPLETE_RETURNED_TREE/issues 0. Device descriptor 18 + config 171; bcdUSB 0200/bcdDevice 0001/EP0 packet 64; configvalue 1/bNumInterfaces 1, alternate 0–3 của ID 0. Cả 18 endpoint khớp metadata về ID/alt/address/direction/type/packet/interval; không IN82. Chi tiết và giới hạn cache/live bus ở RESEARCH. Không suy 88=TS hoặc firmware cold/warm.

### Nghiên cứu ChangeMode và firmware, không execution

- TBS download chính hãng còn link ChangeMode ZIP. Chỉ tải nội dung vào RAM bằng HttpClient, ZipArchive liệt kê đúng một EXE, không README; đọc các chuỗi tĩnh trong RAM, không ghi hoặc chạy EXE. ZIP 213579 bytes/hash `c6555841ca048da247692c7200267adf22a1c24bfe56a66c0a00a0a2e7aee6c2`; EXE 522728 bytes/hash `6da03e94ef1d570c2a4dbccef3a529bf2a56b8cb5e0f5b2ecacf5eddd773afcd`. Thấy nhãn DVB-S/S2, DVB-T/T2 và BDA; chưa chứng minh giao thức/firmware/descriptors/rollback hoặc 82/88. Không chuyển tuner sang Windows.
- Official/C/Go dùng 82; official cold table theo ID và no_reconnect, C/Go load firmware trước frontend. CPU/register và chip-ID sequence chỉ đọc source, không gửi lệnh. Không có blob firmware trong ba checkout đã kiểm nên hash ảnh thật và giấy phép phân phối NOT VERIFIED. Không tải firmware. Các câu hỏi TBS và điều kiện duyệt bring-up đã ghi trong RESEARCH/ADR-017.

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

- Sau raw chạy lại full command: **BUILD SUCCESSFUL 2 m 2 s, 419 tasks, 23 executed/396 up-to-date**. XML mới: drivers 185/usbxfer 1/dvbservice 13=199 JVM methods; TBS package 168. App instrumentation 17 + facade 28 + native 1 + XML 1 =47, 0 failures/errors/skipped. Connected tasks đã thực thi, không đánh up-to-date thành hardware evidence.
- Lint XML: 0 errors/fatal, 9 warnings. Debug/release APK PASS; analysis/report lint lượt cuối up-to-date trên cùng mã đã PASS trước đó. Java 8 compile 10 sources PASS, parser bytecode major 52. Debug DEX có 6 class diagnostic; release DEX không 6 class và manifest không Activity. Native smoke trên arm64 và device-filter XML tương đương PASS. Không chạy native USB ioctl.
- UTP có cài/gỡ app. Sau regression đã cài lại APK debug cuối và cold launch 678 ms, chỉ metadata: generation 0/DETECTED, target 734c:5530, permission=false, pending/cleanup=false, open/claim/close 0. Không yêu cầu quyền lại hoặc đọc raw lần hai. Bằng chứng raw/permission trước đó giữ trong session JSON đã lọc và hồ sơ.
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

## 2026-11-04 — Phase 2.8: điều tra reset độc lập, hai plans offline và checkpoint hướng B

### Baseline, môi trường và bảo toàn thay đổi

- Windows PowerShell tại AndroidDvbDriver; HEAD thực `ef1154d9c60725150e0d2eaab082d7b644748444`,
  nhánh `chore/modernize-android-build`, remote-tracking origin cùng checkpoint.
  Origin SSH là fork trankien84; upstream HTTPS là signalwareltd. Một worktree chính, không checkout.
- Working tree có trước: `.github/copilot-instructions.md`, `.github/instructions/project-records.instructions.md`
  đã sửa và `.github/instructions/usb-capture-windows.instructions.md` chưa tracked.
  Đọc để tuân thủ, không sửa/stage ba file này. Không commit/push/fetch/reset/clean/merge/rebase.
  Ba references đúng SHA khóa, không có thay đổi; Go báo behind 26 so với remote-tracking,
  không sync vì không được suy newest là đúng hoặc cần cập nhật để nghiên cứu reset.
- Đọc hướng dẫn project/scoped, skills và hồ sơ hiện tại, đặc biệt ADR-016–018.
  Load tbs-research/android-cli. Agent nghiên cứu không khởi chạy được do model mặc định không khả dụng;
  tự đọc nguồn trực tiếp, không retry model hoặc nhận có báo cáo agent. Session history query không có kết quả;
  AgentMemory recall rỗng. Không gửi mã nguồn/firmware/logs riêng sang hệ thống bên thứ ba.
- Toolchain kiểm lại build/wrapper/source.properties/JDK: AGP 9.5.0, Gradle 9.8.2,
  JDK 25.0.5.0.3, compile 37.2 revision 2, Build-Tools 37.0.0, NDK 30.0.16248370, CMake 4.1.2.
  Giữ min 21/target 37/Java 8. Không cài/gỡ/nâng cấp package, sửa PATH hoặc User/Machine environment.

### Nghiên cứu và các lệnh chỉ đọc

```powershell
git --no-pager status -sb
git rev-parse HEAD
git remote -v
git worktree list
git -C <REFERENCE> rev-parse HEAD
git -C <REFERENCE> --no-pager status -sb
adb devices -l
adb -s <VERIFIED_TCP_SERIAL> shell getprop ro.product.model
adb -s <VERIFIED_TCP_SERIAL> shell getprop ro.product.manufacturer
adb -s <VERIFIED_TCP_SERIAL> shell getprop ro.build.version.sdk
adb -s <VERIFIED_TCP_SERIAL> shell pm list features
adb -s <VERIFIED_TCP_SERIAL> shell run-as info.martinmarinov.dvbdriver cat files/tbs5530-diagnostic.json
gitnexus status --json
gitnexus status
gitnexus analyze --index-only
```

- Git/ADB đọc thành công, đúng một TCP target GM1900/OnePlus/API 30 và Host=true.
  Không coi alias mDNS là target thứ hai. Monitor session local xác minh cùng serial/model/manufacturer/API
  mỗi 10 giây, không phát hiện mất ADB trong các lượt. Không reconnect/pair/tcpip hoặc dùng emulator.
- `gitnexus status --json` FAILED: CLI không hỗ trợ option; `status` xác nhận chưa index.
  Analyze index-only PASS 71,5 giây, 4514 nodes/12840 edges; status đúng HEAD/up-to-date.
  Context/impact planner trả callers và LOW trong phạm vi query nhưng có truncation, không xem là bảo đảm an toàn.
  Refresh sau debug interfaces PASS 95,4 giây, 4552 nodes/12946 edges; không sinh instructions/skills.
  `detect_changes` báo risk critical theo heuristics (52 symbols/39 affected/9 files, gồm nhiều headings docs),
  output bị cắt; không coi đó là lỗ hổng hay bằng chứng tác động production. Diff/test/DEX mới xác nhận phạm vi.
- Tải TRM PDF HTTPS lại 5790150 byte và đối chiếu hash; `pdftotext -layout` có sẵn.
  Linux generic lấy bằng GitHub MCP khóa SHA; raw download có một HTTP 429, không retry cùng URL liên tục.
  libusb khóa SHA tải thành công. Hai Infineon KB trả 403; search snippet chỉ hỗ trợ tìm nguồn.
  TBS commit message “fixed download firmware error” thực sửa buffer I²C, không CPUCS.
- Firmware archive tải lại HTTPS 566825 byte, đúng hash; `tar -tf`, chỉ trích entry 5530 vào session ngoài Git.
  Entry 8192 byte đúng hash khóa. Hai ZIP Windows chính hãng chỉ trích SYS/INF để đọc;
  không chạy setup/EXE hoặc cài driver. Hash/offset/SHA/URL và matrix trong [RESEARCH.md](RESEARCH.md).
- `objdump -h/-d -Mintel` trên SYS bằng executable sẵn có; Node.js so khớp nguyên ảnh 8192 byte
  tại offsets 35680/1B890/35350 trong ba SYS. Hai x64 helpers dựng dual-reset và ghi đè status đầu;
  đây là static evidence, không runtime capture. Driver cài hiện tại trùng SYS x64 2.0.0.2 hash;
  service Running không đồng nghĩa tuner hoạt động. Không cài Ghidra/Python hoặc thực thi binary hãng.
- TShark/Wireshark 4.6.9, USBPcapCMD/SYS 1.5.4.0; service USBPcap Running.
  `tshark -D`, extcap interfaces/config và `USBPcapCMD --help` đã đọc, không capture.
  `dumpcap -D` không thấy extcap USB; không kết luận driver hỏng. Quyền capture thực chưa đo,
  shell không elevated, mapping tuner→root hub chưa có vì tuner vẫn nối OnePlus.
  `tshark -G fields` xác minh `usb.bmRequestType`, setup fields, urb_id/status/usbd_status;
  `--help` xác nhận autostop duration/filesize. Không lấy serial/IP/network interface inventory vào Git.

### Thay đổi phần mềm và kiểm thử thực tế

- Thêm debug `Tbs5530Fx2ProtocolPlan` và test; giữ main planner và toàn bộ ownership/production không đổi.
  Exact allowlist/provenance, main RAM bound, bỏ đúng hai legacy reset metadata khi mô tả E600-only,
  không tự chọn/fallback. Dry-run fake nhận preflight để mô hình hóa ADB loss và one-shot;
  không tạo Android uploader/entrypoint. Chỉ sửa chuỗi blocker validation, gate vẫn false.
- `runTask` cho task build đã có trả “Task not found” trong host; không sửa task/settings để né.
  Dùng Gradle Wrapper trực tiếp đúng lệnh build/test. Child process đặt JAVA_HOME từ Machine,
  ANDROID_HOME đúng SDK, ANDROID_SERIAL lấy từ TCP target đã kiểm; không đổi môi trường toàn cục.

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --tests '*Tbs5530Fx2ProtocolPlanTest' --tests '*Tbs5530BootstrapDryRunTest' --console plain --no-daemon --max-workers=1
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:connectedDebugAndroidTest --rerun :drivers:connectedDebugAndroidTest --rerun :dvbservice:connectedDebugAndroidTest --rerun :app:assembleDebug :app:assembleRelease :app:lint --console plain --no-daemon --max-workers=1
.\gradlew.bat :drivers:testDebugUnitTest --tests '*Tbs5530BootstrapDryRunTest.hardwareValidationRejectsEmptyWrongFormatSizeSourceAndDigest' :app:assembleDebug :app:assembleRelease :app:lint --console plain --no-daemon --max-workers=1
javac --release 8 -d <SESSION_CLASSES> <13_CORE_DEBUG_SOURCES>
javap -verbose -classpath <SESSION_CLASSES> info.martinmarinov.drivers.usb.tbs5530.Tbs5530Fx2ProtocolPlan
java -cp <SESSION_CLASSES> info.martinmarinov.drivers.usb.tbs5530.FirmwarePlanProbe <LOCAL_FIRMWARE>
apkanalyzer.bat manifest print app\build\outputs\apk\release\app-release-unsigned.apk
git --no-pager diff --check
```

- Targeted PASS exit 0, 20 methods, 58 giây/23 tasks/5 executed.
  Full đầu PASS exit 0, 5 phút 27 giây/419 tasks/67 executed/352 up-to-date;
  XML mới drivers 211/usbxfer 1/dvbservice 13 = 225 JVM, app 17/drivers 30/dvbservice 2 = 49 instrumentation,
  0 failures/errors/skipped. Native smoke arm64 và XML equivalence nằm trong hai tests dvbservice.
- Khi full đầu đang chạy, đổi chuỗi mô tả blocker trong debug validation, không đổi gate.
  Vì vậy không dùng kết quả đó làm attestation mã cuối: focused/build/lint sau đó PASS exit 0,
  2 phút 3 giây/314 tasks/45 executed. Rồi chạy lại nguyên full command phía trên trên mã cuối:
  **PASS exit 0, 2 phút 29 giây/419 tasks/27 executed/392 up-to-date**, cùng 225 JVM/49 instrumentation.
  Lint 0 Error/Fatal, 9 Warning trên cùng mã, không suppress/baseline.
- Java 8 compile 13 sources PASS exit 0, comparator major 52. Chương trình session chỉ đọc ảnh thật,
  kiểm mọi byte/address/control field của hai plans: 132/130 writes mô tả, 1 delay, hardwareCalls=0;
  sửa byte cuối bị từ chối. Không nhúng ảnh vào source/test/APK.
- Quét DEX debug có comparator/runner/fingerprint/validation/raw/diagnostic; release không các nhóm đó,
  release manifest không diagnostic, min 21/target 37. Cả debug/release có bốn native ABI libUsbXfer.
  Không sửa main registry/XML/profile. API 21 runtime, USB trang nhớ 16 KiB và DVB thật NOT RUN.
- Problems editor còn báo debug/testDebug không trên classpath IDE; comparator mới không có syntax error.
  Gradle/Javac thực PASS, không gọi cảnh báo classpath là compile failure của build.

### Runtime cuối, gate và điểm dừng

```powershell
adb -s <VERIFIED_TCP_SERIAL> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <VERIFIED_TCP_SERIAL> shell am start -W -n info.martinmarinov.dvbdriver/.Tbs5530UsbDiagnosticActivity
adb -s <VERIFIED_TCP_SERIAL> shell run-as info.martinmarinov.dvbdriver cat files/tbs5530-diagnostic.json
adb -s <VERIFIED_TCP_SERIAL> shell dumpsys usb
```

- Sau UTP, `run-as` trả unknown package: app đã được test runner gỡ, không phải USB failure.
  Cài lại debug PASS, cold launch 826 ms. Chỉ metadata: Host=true, 734c:5530, alt 0–3, không IN82;
  permission=false, operation/generation 0, DETECTED, pending/cleanup=false,
  open/claim/release/close đều 0. `pidof` có process, dumpsys xác nhận VID/PID;
  raw output không đưa vào Git. Không xin permission hoặc chạy raw descriptor cycle lần nữa.
- Chọn **B**, không đợi TBS: candidate E600-only có cơ sở protocol chip nhưng board responses/recovery
  chưa đủ để nạp Android. Blocker ảnh Linux-vs-Windows đã giải quyết bằng exact byte match;
  blocker hiểu sai C error path đã giải quyết; semantics thật 7F92/nguồn/EEPROM vẫn UNVERIFIED.
  Phải xin duyệt chuyển host vì driver Windows có thể tự nạp ngay khi cắm; sau mapping thật mới duyệt
  capture giới hạn. Không đề nghị phê duyệt upload Android khi chưa đủ điều kiện.
- Recovery chỉ có điều kiện mất mọi nguồn nuôi RAM trên đúng FX2, không hứa USB unplug luôn đủ.
  Không CPU release/reset khắc phục, không retry/plan thứ hai. Các vendor IN/OUT, firmware/CPU,
  I²C/frontend/tuning/TS/config/alternate/reset/EEPROM, chuyển host và capture đều NOT RUN.
  Không có descriptor/quyền/IN82 sau nạp hoặc READY mới. Không tự chuyển phase.
- Cập nhật sáu hồ sơ theo format sẵn có, giữ nguyên phần lịch sử. Kiểm whitespace/diff và rà thủ công
  tiếng Việt/link; không có Markdown linter chạy. Firmware/ZIP/PDF/disassembly/raw logs giữ ngoài Git.
  AgentMemory `memory_save` xác nhận success cho finding từ nguồn công khai, không kèm code/log/định danh.
  Không có xác nhận transport remote-vs-fallback trong response nên không khẳng định lưu remote bền vững.
- Bước tiếp theo duy nhất: người dùng quyết định có cho phép chuyển tuner sang Windows với tác động
  khởi tạo tự nhiên hay không; cần xác nhận nguồn hiện tại trước thao tác. Nếu không duyệt, giữ checkpoint
  offline này, không tự chọn upload Android để thay phép đo.

### Checkpoint sau phê duyệt chuyển host, trước capture — 18:20 +07

- Người dùng phê duyệt riêng chuyển tuner sang Windows, xác nhận trước đó chỉ có nguồn OTG OnePlus,
  không adapter/LNB/hub có nguồn hoặc kết nối cấp điện khác; thông báo đã chuyển vào cổng USB 2.0 laptop.
  Không coi đây là phê duyệt capture hoặc upload Android. Việc tháo/cắm do người dùng thực hiện;
  agent chưa gửi USB write hoặc mở ứng dụng DVB.
- Lệnh đầu sau phản hồi không tìm được đúng TCP target online: FAILED/fail-stop trước mọi truy vấn Windows.
  Đã dừng và hỏi nối lại; người dùng xác nhận `reconnected`. Sau đó xác minh mới GM1900/OnePlus/API 30,
  Host=true, owner report pending/cleanup=false/open/claim/close 0; không tái dùng connection/epoch.
  Bật lại monitor 10 giây với TCP serial mới. Không tự pair/reconnect hoặc chuyển emulator.
- **RUNTIME-OBSERVED:** Windows có đúng TBS 5530 DVB BDA, MEDIA/OK, VID:PID 734c:5530,
  driver 2.0.0.2/oem108.inf/service tbsBDA.TBS5530. Topology EHCI #2 (8 Series/C220),
  Generic USB Hub, port 2. USBPcap extcap mapping xác nhận tuner ở `USBPcap3`, USB address 4,
  parent hub address 1. `DEVPKEY_Device_Address=2` là dữ liệu PnP khác, không dùng làm địa chỉ capture.
  Không lưu instance ID/serial vào Git.
- Truy vấn stdout USBPcap qua PowerShell redirect có lần trả file rỗng dù exit 0.
  Dùng `System.Diagnostics.Process` đọc stdout trực tiếp mới thu được topology; không dùng output rỗng
  làm bằng chứng hub không có thiết bị. Root hub còn có webcam/Bluetooth, nên capture-all không được phép.
  Các thiết bị này không phải target; raw topology chỉ lưu session local.
- Android dumpsys không còn VID/PID tuner. Activity HOT launch vẫn giữ báo cáo metadata cũ;
  không gọi đây là snapshot attachment mới. Report owner vẫn không có open/pending; không USB handles cũ
  nào được tái dùng. Chưa force-stop ứng dụng hoặc thu hồi quyền.
- **Capture NOT RUN:** đã biết đúng interface/address nhưng chưa xin/nhận phê duyệt capture/reconnect.
  Kế hoạch sẽ giới hạn đúng address 4 và thiết bị mới trong duy nhất hub được duyệt, không all-devices,
  không inject-descriptors; cần người dùng không cắm thiết bị khác trên hub trong khoảng đo.
  Mọi dữ liệu raw ngoài Git, chỉ xuất bảng URB đã lọc. Không khẳng định BDA tự nạp đã thành công khi chưa có trace.

### Hai phê duyệt capture tách biệt và trạng thái thu — 18:31 +07

- Người dùng duyệt riêng một trace 30 giây/8192 KiB trên USBPcap3/address 4 và thiết bị mới,
  snaplen 256, không capture-all/inject-descriptors; đồng ý một reconnect cùng cổng, không đổi USB khác.
  Capture pipeline dùng USBPcapCMD xuất stdout vào TShark stdin, không gửi lệnh tới tuner.
  File ngoài Git; UAC/worker nếu có chỉ thuộc capture đã duyệt, không cài hoặc sửa filter.
- **FAILED phép đo đầu:** TShark thực sự sẵn sàng lúc khoảng 18:25:47, tạo header pcapng 296 byte.
  `capinfos` xác minh 0 packets, không timestamp. PnP LastArrivalDate 18:27:28 cho thấy reconnect
  sau cửa sổ 30 giây; vì vậy không có cơ sở kết luận 7F92 hoặc capture driver hỏng.
  Script chờ pipeline hết deadline 60 giây rồi báo exit 1; sau đó không còn TShark/USBPcap processes.
  Không tự lặp capture/reconnect.
- Đã giải thích thất bại và xin phê duyệt MỚI; người dùng đồng ý đúng một lượt 180 giây/8192 KiB
  cùng phạm vi và một reconnect riêng. Không diễn giải đây là retry firmware upload Android.
  Lượt hai sẵn sàng 18:30:13; người dùng xác nhận reconnect, PnP arrival 18:31:01 nằm trong cửa sổ.
  Tại 18:31:04 file tăng lên 232084 byte, tuner vẫn MEDIA/OK, Wireless ADB đúng GM1900/API 30 online.
  Đây chỉ xác nhận có dữ liệu capture, chưa phân tích completion hoặc kết luận bring-up.

### Phân tích trace thật và cleanup — 18:44 +07

- TShark dừng sau 180 giây, ghi 3028 packets/232084 byte; `capinfos` cho khoảng packets
  18:30:59.221881–18:31:04.273827 +07, SHA pcapng
  `e3220b64da4adac60862406569ef5151c430f050f6b179096a925b803e913f4f`.
  Chỉ address 4/bus 3, không frame truncated. Raw ngoài Git vì có nguyên firmware.
- Wrapper pipeline exit 1 ở deadline 210 giây vì USBPcap worker elevated còn tồn tại sau khi TShark dừng.
  `Stop-Process -Id 2208` Access denied; dừng parent PID 15680 thành công nhưng worker vẫn còn.
  Đã dừng việc tiếp tục capture và hỏi người dùng dọn đúng PID bằng Administrator.
  Người dùng xác nhận, kiểm lại không còn TShark/USBPcapCMD: cleanup PASS có phạm vi.
  Không restart service/filter/driver hoặc tự nâng quyền. Không nhầm exit 1 pipeline thành firmware fail.
- Lần xuất TSV đầu dùng `usb.urb_function` không hợp lệ: FAILED export, không USB failure.
  Bỏ trường sai, dùng `usb.irp_id`, irp_info/control_stage/request_in và completion usbd_status.
  `usb.urb_id` rỗng trong USBPcap này, không dùng join đó. IRP ID được tái dùng;
  parser ghép pending theo bus/address/ID và kiểm request frame, không join toàn file chỉ theo ID.
- Node checker và Java parser đều xác nhận 264 A0 submissions/264 paired completions, không A0 pending.
  Hai lượt tự nhiên 132 writes, DATA mỗi lượt 8192 byte cùng hash khóa; cả stop/release 7F92/E600
  đều SUCCESS. Frames 1: 9–272, frames 2: 301–564; bảng reset chi tiết trong RESEARCH.
  Không lệnh vendor từ agent, không tự retry BDA. Những writes này do driver hãng phát tự nhiên
  trong reconnect đã được duyệt, không được đếm là Android control path PASS.
- Descriptor frame 6 trước nạp có default alt 0–3 thiếu 82; frame 298 giữa hai lượt có alt 0 IN82/81,
  VID:PID không đổi. Sau lượt 1 có mười B7 IN errors c0000011 rồi driver tự init lại;
  có 90/8A/80 tự nhiên sau đó. Chưa biết lý do init hai lần hoặc bridge readiness,
  không mở frontend/tuning/TS từ agent và không sao chép nhánh BDA thành retry Android.
- Thêm debug `Tbs5530ResetTranscript` và 5 JVM tests tổng hợp cho IRP reuse/frame pairing,
  failed completion không bị báo success, missing completion, truncated frame/TSV, hash/address/length sai,
  mixed devices, immutable results. Không capture/firmware fixture trong Git.
  Targeted parser PASS exit 0,1 phút 3 giây/23 tasks; Java 8 compile/parser trên TSV thật PASS,
  major 52. Đổi blocker debug thành `ANDROID_ONE_SHOT_PREFLIGHT_AND_UPLOAD_APPROVAL_REQUIRED`;
  gate vẫn false, không còn mô tả board responses là chưa quan sát.
- Hướng B đã giải quyết phép đo: 7F92 không STALL ở board/điều kiện Windows quan sát,
  descriptor chuyển operational-candidate sau lượt đầu. Chưa chứng minh alias/E600-only/Android upload,
  không đủ byte=READY. Tuner vẫn ở Windows; chưa duyệt return-host hoặc upload Android.
  Cần kế hoạch one-shot guard/observer tách khỏi init/retry BDA trước phê duyệt phần cứng kế tiếp.
- Regression sau parser PASS exit 0,4 phút 48 giây/419 tasks/58 executed/361 up-to-date:
  230 JVM (216+1+13), 49 instrumentation (17+30+2), 0 failures/errors/skipped;
  debug/release/lint 0 errors/fatal, 9 warnings. Vì chuỗi blocker đổi trong lượt đang chạy,
  đã khóa mã và chạy lại nguyên full command để không dùng snapshot không xác định làm attestation cuối.
- DEX kiểm mới parser/comparator/runner/validation chỉ có debug, không release. Export manifest thành công;
  lệnh Select-String ban đầu sai thứ tự tham số gây exit 1 ở bước đọc, đã sửa bằng -Path/-Pattern:
  minSdk 21/target 37, không diagnostic. Đây là lỗi helper báo cáo, không build hoặc USB fail.
- **Mã khóa cuối PASS:** nguyên full Gradle command lần cuối exit 0, 4 phút 2 giây,
  419 tasks/55 executed/364 up-to-date; XML mới 230 JVM (216+1+13), 49 instrumentation (17+30+2),
  0 failures/errors/skipped. Lint 0 Error/Fatal, 9 Warning; không đổi mã tiếp sau lượt này.
- Sau UTP cài lại debug thành công, cold launch 996 ms. Metadata thực mới: USB Host=true,
  không có USB/TBS trên OnePlus, operation/generation 0, DETECTED, pending/cleanup=false,
  open/claim/close 0. Windows tuner MEDIA/OK, không còn capture process.
- GitNexus incremental refresh sau parser FAILED do FTS `file_fts` inconsistent (document node thiếu
  khi delete/COPY). `status` vẫn trả up-to-date theo HEAD index cũ, không dùng kết quả đó để tuyên bố
  graph mới sẵn sàng. Không drop index/force repair, tiếp tục xác nhận bằng source/diff/tests.
- AgentMemory đã xác nhận tạo một Lesson về các trường IRP/frame/completion và cleanup capture,
  chỉ chứa phương pháp công cụ chung, không payload/log/định danh riêng. Không lưu runtime capture
  hoặc source dự án sang memory. Backend response không khẳng định remote-vs-fallback.
- Ở kiểm cuối monitor phát hiện TCP ADB mất lần thứ hai: dừng ngay, không build/test/code/USB mới.
  Người dùng xác nhận nối lại; xác minh mới GM1900/OnePlus/API 30/Host=true, report không USB,
  operation/generation 0, DETECTED, pending/cleanup=false, open/claim/close 0. Windows tuner OK,
  không capture process. Không dùng lại connection/session/epoch hoặc tự tiếp tục upload nào.

## 2026-11-04 — Commit và push Phase 2.8 theo yêu cầu

- Người dùng yêu cầu commit các thay đổi hiện có và push lên origin.
  Nhánh `chore/modernize-android-build`; HEAD và live origin trước thao tác cùng
  `ef1154d9c60725150e0d2eaab082d7b644748444`, stage trống, một worktree chính.
  Phạm vi 16 file gồm bảy code/tests debug, sáu hồ sơ và ba file hướng dẫn có trước phiên nghiên cứu.
  Không sửa nội dung hướng dẫn, không sửa references hoặc reset/clean/merge/rebase/force-push.
- Preflight Wireless ADB xác minh đúng TCP target OnePlus GM1900/OnePlus/API 30 đang online;
  không ghi IP/port/serial vào Git. Kiểm phạm vi diff, whitespace và dữ liệu nhạy cảm trước commit;
  không stage firmware/capture/ZIP/APK/raw logs. Chỉ stage danh sách file đã kiểm.
- Message dự kiến `feat: document TBS5530 reset evidence and validate USB transcripts`,
  có Co-authored-by trailer. Kết quả commit/push và SHA được xác minh bằng Git sau thao tác,
  không ghi trước thành công. Không chạy lại build/tests hoặc USB operation;
  kết quả 230 JVM/49 instrumentation/debug/release/lint là bằng chứng Phase 2.8 đã có,
  không phải lượt PASS mới. Android upload/return-host/frontend vẫn NOT RUN.
- Kiểm staged diff phát hiện CRLF ở bốn file Java mới bị báo trailing whitespace.
  Đã dừng trước commit, chỉ chuẩn hóa bốn file sang LF và so sánh xác nhận nội dung chữ không đổi;
  không sửa Git config hoặc tắt whitespace check. `git diff --cached --check` đạt PASS sau chuẩn hóa.

## 2026-11-04 — Phase 2.9: Android debug one-shot đã kiểm offline, chờ phê duyệt

- **Branch/HEAD trước và sau:** `chore/modernize-android-build`,
  `ead0e4adf5051e878706ad70c3863b90cb8ffb98`. Đầu phiên Git sạch, một worktree chính;
  `rev-parse HEAD`, `status -sb`, `remote -v`, `worktree list` đã kiểm. Không stage/commit/push,
  reset/clean/rebase/merge/checkout; không sửa/fetch ba references.
- **Đường cũ và thay đổi:** UsbManager → Android facade → binding → coordinator → session/lease
  → ControlTransport/ControlIo. Thêm raw-descriptor seam mặc định throw và giao tiếp package-private
  dưới cùng lease/transaction. Android bootstrap adapter/runner chỉ debug; dùng runtime/owner cũ,
  không claim hoặc mở song song. SAF local chọn ảnh và metadata archive do người dùng xác nhận;
  kiểm byte/hash trước open. Có approval challenge/install/attachment/epoch/plan/hash hết hạn,
  foreground, xác nhận UI, attempt lưu trước ghi và acknowledgment ADB cho từng ordinal.
  Observer trước nạp; đủ 132 writes chỉ TRANSITIONING. Đóng lease cũ rồi mở epoch mới chỉ đọc;
  có 82 chỉ OPERATIONAL_CANDIDATE, không ready/frontend/TS. Chi tiết [ADR-020](DECISIONS.md).
- **File sửa:** ba main classes `Tbs5530AndroidUsbFacade`, `Tbs5530LifecycleCoordinator`,
  `Tbs5530UsbBinding`; controller/Activity debug; bốn class debug/tests mới ở drivers,
  approval/tests mới ở app; helper `tools/Watch-TBS5530-BootstrapApproval.ps1`;
  STATUS/RESEARCH/DECISIONS/TEST-MATRIX/WORKLOG. ENVIRONMENT không sửa vì host/toolchain
  không đổi. Chip mới chỉ ghi một lần tại RESEARCH, không thư hoặc thông tin liên hệ.
- **Nguồn:** official/C/Go `rev-parse HEAD` giữ SHA đã khóa; đọc lại hàm firmware bằng
  `git show HEAD:drivers/media/usb/dvb-usb/tbs5530.c` và source C/Go read-only. Không nghiên cứu
  lại reset tranh luận; Windows Phase 2.8 vẫn là bằng chứng Windows, không Android.
- **Toolchain:** đọc khai báo build/wrapper, SDK source.properties và JDK thực;
  AGP 9.5.0/Gradle 9.8.2/JDK 25.0.5.0.3, compile 37.2/Build-Tools 37.0.0/NDK 30/CMake 4.1.2,
  min 21/target 37/source-target 8 giữ nguyên. `android --help`, `docs search --help`,
  `docs fetch --help`, `sdk list --help`; docs search USB/SAF và fetch documents-files.
  CLI filter `platforms;android-37.2` không match nhưng filesystem source.properties xác minh;
  không coi kết quả filter rỗng là thiếu SDK hoặc tự cài. Không khảo sát latest stable.
- **Targeted đầu:** wrapper `:drivers:testDebugUnitTest --tests '*Tbs5530OneShotBootstrapTest'
  :app:compileDebugJavaWithJavac --console plain --no-daemon`, exit 0/1 phút 9 giây;
  lúc đó 9 methods mới, sau thêm in-flight/cleanup thành 11. Không dùng kết quả đầu thay suite cuối.
- **Lệnh regression đầy đủ (Windows PowerShell, cwd repo):** JAVA_HOME process trỏ JDK
  `jdk-25.0.5.0.3`, ANDROID_HOME SDK hiện hữu, ANDROID_SERIAL lấy động đúng một TCP target.
  `.\gradlew.bat :usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest
  :app:connectedDebugAndroidTest :drivers:connectedDebugAndroidTest :dvbservice:connectedDebugAndroidTest
  :app:assembleDebug :app:assembleRelease :app:lintDebug --console plain --no-daemon`.
  Lượt đầu exit 0/4 phút 12 giây; sau sửa guard/các race chạy lại exit 0/4 phút 1 giây;
  sửa pending trước enqueue exit 0/3 phút 9 giây; cuối sau UI cuộn toàn trang exit 0/4 phút 19 giây,
  421 tasks/39 executed/382 up-to-date. Một số task không đổi là UP-TO-DATE, không gọi là rerun mới.
  XML kết quả 241 JVM (227+1+13), 56 instrumentation (21+33+2), 0 failure/error/skipped.
  Lint cuối 0 Error/Fatal/10 Warning; warning Java 8 obsolete/Gradle deprecation còn nguyên.
- **Static/ảnh thật:** 107 TBS class major 52; DEX debug có runner/adapter/approval/diagnostic,
  release không có; apkanalyzer manifest min 21/target 37 không diagnostic. Không firmware blob
  trong APK. Ảnh local có sẵn ngoài Git 8192 byte/hash khóa được đọc bằng reader mới và
  `javac --release 8` harness: 132 writes/8196 payload bytes/128 chunks/delay 100, so từng byte;
  reject truncated/extra/modified. Harness xóa sau test; không upload hoặc download firmware.
- **Helper:** PowerShell parse 0 lỗi; gọi không flags exit 1 BLOCKED trước ADB; mock ADB end-to-end
  exit 0, kiểm ordinal/atomic permit/terminal stop. Fake scripts/state xóa sau test. Không chạy helper
  có approval trên target thật. Chưa có quyền chuyển host/upload từ người dùng.
- **Runtime mới:** luôn kiểm OnePlus/GM1900/API 30/USB Host, một TCP target, monitor 10 giây
  trong lệnh dài. Sau UTP cài lại debug exit 0/cold launch 670 ms; UI firmware/upload hiện và disabled,
  không USB/TBS, DETECTED/open 0/claim 0/pending=false/cleanup=false. Windows TBS BDA OK.
  Không dùng alias mDNS như thiết bị thứ hai hoặc permission cached. Không vendor/CPU/USB reset,
  I2C/EEPROM/configuration/interface/firmware transfer hoặc host handoff.
- **Giới hạn công cụ:** VS Code runTask không resolve hai id dù tasks.json hiện hữu; fallback wrapper.
  Editor báo ngoài classpath/duplicate phantom; kiểm source chỉ một changed/main/close, Gradle compile
  PASS. Explore đọc hồ sơ không chạy được (400 model unsupported), không relaunch.
  GitNexus `status --json` không hỗ trợ → `status`; refresh đầu tự rebuild vì flag pending và PASS,
  impact Binding báo 37 symbols/2 modules nên giữ seam nhỏ và regression. Refresh sau mã lớn PASS
  172.7 s; refresh cuối FAILED FTS file_fts inconsistent/COPY. Không drop/force/repair index;
  không dùng graph lỗi như proof mã cuối. Source/diff/tests là bằng chứng.
- **NOT RUN/BLOCKED:** Android hardware upload, descriptors sau nạp thật, source SAF trên tuner,
  USB recovery/lock/TS/API 21 runtime. `BLOCKED: EXPLICIT_HARDWARE_APPROVAL`.
  Bước đơn tiếp theo: đề nghị riêng quyền chuyển tuner về OnePlus OTG và một lượt dual-reset.
  Nếu được duyệt, chạy preflight mới từ đầu; không reuse epoch/connection/approval hoặc thử plan khác.
  Không tự bắt đầu Phase 3.0; physical recovery chỉ do người dùng quyết định sau kiểm nguồn.

- **Memory checkpoint:** Gateway nhận lưu một bài học phương pháp chung, không source/log/firmware/định danh.
  Response success không chứng minh remote-vs-fallback; không tuyên bố đã bảo đảm lưu bền liên phiên.

### Phase 2.9 — Sau phê duyệt: một attempt thật, dừng UNCERTAIN, recovery riêng

- **Phê duyệt rõ ràng:** ask_user nhận cả ba true: chuyển host vật lý, chỉ nguồn OTG/không
  backfeed/đóng app giữ tuner và đúng một dual-reset attempt. Người dùng xác nhận đã nối OnePlus;
  chưa có firmware trên điện thoại. Copy ảnh đã khóa từ local ngoài Git vào Downloads riêng qua
  Wireless ADB, size/hash mới đúng; không bundle/distribute qua APK hoặc upload từ PC.
- **Chuẩn bị thật:** refresh metadata thấy một `734c:5530`/default thiếu endpoint `0x82`, permission chưa có.
  Nút permission-only không open. Người dùng tự OK, nhập nguồn/entry và tự chọn SAF.
  Lần đầu VALIDATION_FAILED do URL trailing space quan sát trực tiếp; open/claim 0, chưa consume.
  Không trim/bypass validation hoặc tự chọn thay người dùng. Sau sửa metadata/chọn lại:
  permission=true, HASH_SIZE_VALIDATED_SOURCE_USER_ATTESTED_SESSION_APPROVAL_REQUIRED,
  challenge epoch 1/plan dual, age 27192 ms; không pending/cleanup/open.
- **Lệnh được duyệt:** `tools/Watch-TBS5530-BootstrapApproval.ps1 -ExplicitOneShotApproval
  -PhysicalHostAndOtgConfirmed`, helper thật chạy sau phê duyệt, chọn serial TCP động đúng
  OnePlus/GM1900/API 30 và cấp heartbeat/ordinal acknowledgment; không tự bấm upload.
  Người dùng tự bấm Nạp đúng một lần. Không operation nào chạy trên emulator/PC libusb.
- **RUNTIME-OBSERVED:** report giữa lượt LOADING, attempted 21/completed 20. Report terminal:
  UNCERTAIN/epoch 1, attempted 24/completed 23, transferred_bytes 1346, duration 25946 ms,
  IO_FAILURE, attachment_events 0, pending=false/cleanup_pending=false/ready=false.
  Port ghi **23 actual UsbDeviceConnection.controlTransfer calls**, mọi call full-length:
  7F92=01/E600=01 (2×1 byte), 21 DATA × 64 byte tại 0000–0500 (1344 image bytes).
  Không call release 7F92/E600 hoặc corrective/retry/fallback.
- **Điểm dừng:** runner ordinal 24 value 1344/0x0540/length 64 FAILED_OR_UNKNOWN,
  duration 1510321 µs; không có API call 24. Guard acknowledgment chạy trước native call;
  sau dừng request và last acknowledged ordinal cùng 24. Phù hợp deadline 1500 ms bị lỡ,
  nhưng không có timestamp chi tiết để xác nhận causal timing; nguyên nhân sâu UNVERIFIED.
  Không gọi là USB timeout hoặc STALL. Helper exit 0 chỉ nghĩa đã dừng cấp acknowledgment
  khi app terminal, không nghĩa firmware PASS. Image thiếu, CPU có thể halt; không reset/release.
- **Raw trước nạp:** COMPLETE_RETURNED_TREE 189 byte, MATCH/default thiếu endpoint `0x82`, SHA-256
  `e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`.
  Không raw post-upload vì chưa tới TRANSITIONING. Không suy READY hoặc bridge/I2C.
- **Recovery được hỏi riêng:** người dùng chọn tự rút/cắm đúng một lần trên OnePlus, chỉ OTG;
  xác nhận đã làm. Không chuyển Windows hoặc gửi USB reset. Sau cleanup/pending=false lưu
  sanitized attempt report ngoài Git, Back/launch diagnostic mới để không reuse connection/epoch.
  Permission cũ mất; người dùng OK request mới. Permission-only report TIMEOUT là lịch sử
  callback, raw port kiểm hasPermission thật=true trước open, không dùng cached grant.
- **Raw sau recovery:** explicit raw-only click theo UI hierarchy; 14 ms,
  READ_AND_CLOSED/pending=false/cleanup=false/permission=true,
  open/read/close 1/1/1, claim/transfer 0, failure NONE. Raw 189 byte/hash ở trên khớp trước nạp,
  fingerprint MATCH/BOOTSTRAP_CANDIDATE thiếu endpoint `0x82`. Đây là recovery descriptor quan sát được,
  không guarantee power-cycle, không firmware-upload PASS.
- **Trạng thái cuối:** Windows PnP TBS 0; tuner ở OnePlus, ADB online đúng target;
  consumed=true vẫn bền sau diagnostic relaunch. Không chạy instrumentation/build sau thực nghiệm
  để tránh reinstall/xóa guard. Không cấp lại permit, không attempt thứ hai hoặc Phase 3.0.
- **Bước đơn tiếp:** phân tích offline handshake latency/stop reason và thêm telemetry/mock độ trễ
  trước bất kỳ kế hoạch hardware mới; cần approval mới, không reuse attempt đã tiêu thụ.

## 2026-11-04 — Commit và push Phase 2.9 theo yêu cầu

- Người dùng yêu cầu commit các thay đổi hiện có và push lên origin nhánh hiện tại.
  Preflight: `chore/modernize-android-build`, HEAD/live origin cùng
  `ead0e4adf5051e878706ad70c3863b90cb8ffb98`, stage trống, một worktree chính, 18 file.
  Không sửa references hoặc chạy reset/clean/rebase/merge/checkout; không force-push.
- Phạm vi gồm debug adapter/runner/approval/UI, raw seam main, tests, helper và sáu hồ sơ.
  Message dự kiến `feat: add guarded debug-only TBS5530 Android bootstrap`, có
  Co-authored-by trailer. Kết quả commit/push sẽ xác minh từ Git sau thao tác.
- `git diff --check` và scan nội dung thêm mới không phát hiện mẫu credential/IP hoặc binary
  ngoài phạm vi. Scan toàn file ban đầu trúng một đoạn WORKLOG lịch sử không nằm trong diff;
  không sửa lịch sử hoặc đưa kết quả scan cũ thành phát hiện trong thay đổi mới.
- Wireless ADB preflight vẫn đúng OnePlus/GM1900/API 30 online; không thao tác USB,
  không cấp permit hoặc thử lại attempt. Không build/test lại: 241 JVM/56 instrumentation,
  debug/release/lint 0 errors/10 warnings là bằng chứng Phase 2.9 trước tác vụ Git.
  Hardware UNCERTAIN và guard consumed giữ nguyên; bước tiếp chỉ phân tích offline handshake.
- Staged check đầu phát hiện CRLF ở bảy file mới; chỉ chuẩn hóa LF, xác minh nội dung chữ không đổi,
  stage lại và staged whitespace check PASS. Không đổi Git config hoặc chạy lại hardware/tests.

## 2026-11-05 — Phase 2.9: sửa guard/native telemetry và kiểm không USB

- **Branch/HEAD trước:** `chore/modernize-android-build`,
  `d76bbfb63dd21d55af6ce27fdff0543eb500db72`; sạch, stage trống, một worktree.
  Không commit/push, checkout, fetch/sync hoặc reset/clean/rebase.
- **Mục tiêu:** tiếp tục cùng Phase 2.9. Đọc lại instructions/skills, ADR-018/019/020,
  hồ sơ mới nhất, uploader/guard/owner/session/transport/UI và JSON sanitized ngoài Git.
  Giữ ranh giới hardware: approval cũ CONSUMED, không firmware/vendor/CPU/I²C mới.
- **Môi trường:** Windows PowerShell; cwd repo hiện hữu. Xác minh wrapper/build/SDK/JDK,
  không đổi version/dependency. Firmware local đọc lại đúng 8192 byte/hash khóa; không copy.
  ADB chọn một TCP serial bằng discovery, xác minh OnePlus/GM1900/API 30/USB Host.
  `dumpsys usb` chỉ đọc thấy tuner, không open/claim/transfer. Monitor từng cửa sổ bounded
  mỗi 10 giây; hết cửa sổ thì re-preflight, không tự reconnect hoặc đổi target.
- **Phân tích:** duration cũ bao gồm handshake, không phải native-only. Timeline 20–24 ghi
  UNVERIFIED cho timestamp thiếu, không dựng thời gian giả. Đã chứng minh test clean xóa
  production store, đếm attempted trước guard, mất typed cause và nhánh IOException không
  khóa write gate. ACK trễ vẫn là giả thuyết, không gọi USB timeout/STALL hoặc lỗi 7F92.
- **Mã parent:** thêm debug stop kinds/telemetry/audit AtomicFile theo run epoch, giữ cause chain;
  sửa Port mọi IOException/RuntimeException/short/negative đều đóng gate, kiểm quyền/device
  sau ACK. Runner tách native counters khỏi legacy adapter counters, giữ cleanup provenance.
  Diagnostic UI/report phân biệt FX2_DEFAULT/incomplete/operational candidate, không READY.
  Giảm report rewrite khi chỉ duration thay đổi; không bỏ audit ở critical boundaries.
- **Approval/helper:** giao agent triển khai core clock/storage injection, test namespace,
  per-ordinal nonce/deadline và persistent channel/benchmark. Không giao quyền ADB/USB/deploy
  production. Agent explore readiness lỗi `400 model not supported`, không có output;
  parent đối chiếu ba nguồn trực tiếp, ghi matrix request/side effect trong RESEARCH.
- **Lệnh targeted thực:** `:drivers:testDebugUnitTest --tests '*Tbs5530OneShotBootstrapTest'
  --tests '*Tbs5530BootstrapTelemetryTest' :app:compileDebugJavaWithJavac
  :app:compileDebugAndroidTestJavaWithJavac --console plain --no-daemon`, exit 0;
  17 methods PASS tại snapshot đầu. Không cài APK bởi lệnh compile này.
- **Core validation:** wrapper `:drivers:testDebugUnitTest :usbxfer:testDebugUnitTest
  :dvbservice:testDebugUnitTest :drivers:assembleDebugAndroidTest`, exit 0, 1 phút 32 giây,
  98 tasks. Sau bổ sung provenance test, full regression mới ghi dưới đây.
- **Instrumentation injected lượt đầu:** kiểm aapt package/targetPackage cùng
  `info.martinmarinov.drivers.test`, `adb install -r -t` chỉ test APK và `am instrument -w -r
  -e class ...Tbs5530AndroidBootstrapPortTest ...drivers.test/...AndroidJUnitRunner`.
  PASS 5 tests/0,074 giây; manager/raw giả, không USB hardware. Production prefs được đọc
  trước/sau và không đổi, consumed=true. Không dùng connected UTP để tránh reinstall production.
- **Regression snapshot:** wrapper `:drivers:testDebugUnitTest :usbxfer:testDebugUnitTest
  :dvbservice:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
  :app:assembleDebugAndroidTest :drivers:assembleDebugAndroidTest --console plain --no-daemon`,
  exit 0/3 phút 25 giây, 387 tasks/63 executed/324 up-to-date. XML 249 JVM
  (235 drivers +1 usbxfer +13 service), 0 failures/errors/skipped. Lint 0 errors/11 warnings;
  warning mới SdCardPath của test harness được đưa vào việc sửa, không suppress baseline.
- **Static snapshot:** 120 TBS classes major 52, min 21/target 37. Debug có uploader/approval,
  release không có các class mới/harness; không entry `.fw` trong cả hai APK.
- **Instrumentation app cách ly:** init script ngoài Git đặt build dir riêng và applicationId
  `.phase29offline`, test target `.phase29offline.test`, kiểm bằng aapt trước install.
  Chỉ chạy ApprovalTest/ApprovalDeterministicTest, không launch diagnostic hoặc cấp USB permission.
  Lượt đầu FAIL 1/27 tại assertion isolation ApprovalTest:21; 26 methods PASS.
  Production prefs không đổi. Tiếp tục sửa assertion đúng namespace, không bỏ safety gate.
- **Benchmark lần đầu:** Build script javac/D8 PASS, dependency scan 0 USB. Trên OnePlus,
  `app_process FakeTests` PASS 13; wrapper sau đó lỗi parameter `DeadlineTimestamp` do snapshot
  helper chưa đồng bộ. Không handshake hardware/permit thật, không USB call.
  Namespace test có hard stop 60 giây, evidence ngoài app data; không coi lượt này benchmark PASS.
- **Chứng cứ / quyết định:** bổ sung ADR-020, RESEARCH timeline/readiness, ENVIRONMENT và matrix.
  Mọi con số ở đây là checkpoint đúng thời điểm; kết quả rerun cuối được bổ sung riêng,
  không đổi FAIL hoặc PASS lịch sử để làm đẹp.

### Phase 2.9 — Rerun cuối và dừng đúng gate hardware mới

- **Sửa tiếp:** assertion isolation chấp nhận đúng app/test `.phase29offline` trước mọi
  files/preferences access. Core thêm per-attempt new approval ID/store, giữ legacy true;
  missing initial grant không poison UI polling, còn sau consume/write thì revoke terminal.
  Host ledger canonical chống vô tình đổi file, CreateNew enrollment, duplicate GUID và
  corrupt ledger đều fail-closed. App uninstall không được giả thành vẫn giữ preferences.
- **Native transition:** bổ sung trường hợp full-length release thứ 132 trùng detach chỉ
  cho phép delay/đóng lease/quan sát read-only với epoch mới. Short/negative/exception ở
  release vẫn UNCERTAIN; không corrective writes. Targeted runner tests PASS, exit 0, 58 giây.
- **JVM có một FAIL mới đã giữ evidence:** lượt sau bổ sung stop-chain test gặp
  ConcurrencyTest `repeatedDetachAndCancelRacesDoNotLeakOrReplay`, timeout đợi thread vào
  fake transfer 1 giây tại dòng 73. Không sửa/tăng timeout test cũ. Focused cùng class rerun
  PASS 10 methods, 55 giây; chưa chứng minh scheduler là nguyên nhân. Full suite cuối PASS.
- **Lệnh full cuối:** wrapper `:drivers:testDebugUnitTest :usbxfer:testDebugUnitTest
  :dvbservice:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
  --console plain --no-daemon`, exit 0, 2 phút, 326 tasks/47 executed/279 up-to-date.
  XML **253 methods** = 239 drivers + 1 usbxfer + 13 service, 0 failure/error/skipped.
- **Tái tạo output app:** DEX scan thấy class harness mồ côi sau chuyển source sang tools.
  Chỉ chạy `:app:clean :app:assembleDebug :app:assembleRelease :app:lintDebug`, exit 0,
  3 phút 31 giây/312 tasks; đây là generated build output, không Git clean/SDK/cache/app-data.
  Scan lại: standalone harness vắng debug/release, approval/uploader chỉ debug;
  119 TBS classes major 52, min 21/target 37, không `.fw`, lint 0 errors/10 warnings.
- **Build test tái dùng:** thêm `tools/Build-TBS5530-OfflineTestApp.ps1`, chỉ build/kiểm
  aapt exact package, không ADB/install. Script chạy PASS, exit 0, 1 phút 23 giây.
  Init script tạm ngoài Git được xóa sau khi có script tái tạo này.
- **Instrumentation mới không trộn lịch sử:** app isolated rerun PASS 42 executions trong đó
  deterministic methods bị chạy cả base và subclass. Để tránh phóng đại, chạy lại chỉ subclass
  ApprovalTest + BootstrapReportTest: **22 methods riêng biệt PASS**, 3,881 giây.
  PortTest self-instrumenting library: **7 methods PASS**, 0,086 giây. Tổng targeted mới 29,
  không gọi là full 56 historical suite. Mọi manager/raw là fake; production prefs so trước/sau
  không đổi consumed=true. Không cài lại app production hoặc xin USB permission.
- **Benchmark lượt 60 giây:** 20 fake cases PASS, persistent channel được 76 observations/77 ACK
  rồi dừng, p50 720/p95 785/p99=max 809 ms. Sau lỗi discovery/identity vẫn online và monitor
  không drop; không reconnect. Source cũ gán hết tổng deadline thành channel loss;
  sửa provenance và tách tổng benchmark 240 giây khỏi ACK 1500/heartbeat 2000/native 2000 ms.
- **Benchmark đủ 132 lần thứ nhất:** Build + Run scripts, cùng core Java guard trên OnePlus,
  PASS 132/132, timeout 0, USB 0; p50 611/p95 705/p99 783/max 1476 ms, helper 3 ADB subprocess.
  Không gọi là USB speedup hoặc bảo đảm hardware; margin tail 24 ms được ghi rõ.
- **Helper review tiếp:** production overall 60 giây không đủ nên giữ lại tối đa 300 giây
  tương ứng TTL cũ. Parser một dòng không đọc được Activity pretty JSON, đổi sang một file
  descriptor/builtins đủ LF/CRLF, bound 256 KiB, fail thay truncate. Ledger production/benchmark
  khác mode và terminal native counters riêng; không ghi raw challenge/install trong audit mới.
- **Lệnh host mock cuối:** `tools/Test-TBS5530-ApprovalChannel.ps1`, exit 0, PASS parser,
  wrapper/deadline/ACK/transport, CreateNew/fsync/no-overwrite, duplicate/restart/corrupt,
  132 builtin JSON/atomic ACK loops, report >64 KiB, reject >256 KiB và pinned identity.
  Không ADB hoặc production grant từ mock.
- **Live no-USB mã cuối:** `tools/Build-TBS5530-ApprovalBenchmark.ps1`, rồi
  `tools/Run-TBS5530-ApprovalBenchmark.ps1 -Serial <discovered TCP serial>
  -LedgerPath <session local ngoài Git> -RuntimeJar <compiled jar>
  -ApproveIsolatedAndroidBenchmark`. Javac/D8/USB dependency scan 0, Android fake 20 PASS;
  **132/132 PASS**, timeout 0, native USB 0, p50 **359**/p95 **506**/p99 **714**/max **759 ms**.
  Helper 3 ADB subprocess không bao wrapper/monitor. Hai lượt đủ 132 giữ 264 mẫu, p95 694/
  p99 781/max 1476 ms; không trộn 76 mẫu của lượt FAIL hoặc tuyên bố đã loại mọi outlier.
  Samples/timeline lưu ledger ngoài Git; namespace test lượt PASS được export rồi xóa.
- **Durable prior audit:** chạy initializer với `-EnrollPriorConsumedAudit
  -AcknowledgeUserProvidedPhase29Evidence`; canonical local ledger ghi prior UNCERTAIN,
  consumed/23 native/1344 image/no CPU release. `hardware_approval=false`,
  `new_approval_issued=false`, `application_state_modified=false`. Không ADB/permit/token.
- **Cleanup:** sau giữ evidence, xóa đúng hai namespace benchmark lỗi do phiên tạo và hai
  package app/test `.phase29offline` mới. Không gỡ `drivers.test` đã có trước.
  So preferences production sau cleanup vẫn không đổi consumed=true. Không xóa firmware,
  source, logs evidence hoặc SDK. Không process còn chạy benchmark; monitor chỉ đọc được dừng cuối.
- **GitNexus:** refresh giữa phiên PASS, 168,4 giây; cuối FAILED FTS `file_fts` inconsistent/COPY.
  Không drop/recreate/force/repair. Không dùng graph lỗi để xác nhận diff cuối; source/tests/diff
  là bằng chứng. Editor classpath limitation giữ nguyên, không đổi cấu hình IDE.
- **Memory:** đã thực hiện writeback bài học đã kiểm; Gateway success nhưng response không
  chứng minh remote-vs-local fallback, không khẳng định lưu bền liên phiên.
- **Kết thúc:** HEAD không đổi `d76bbfb63dd21d55af6ce27fdff0543eb500db72`, stage trống,
  thay đổi chỉ code/tests/tools/sáu hồ sơ, không reference/toolchain/instructions.
  `AWAITING_EXPLICIT_NEW_HARDWARE_APPROVAL`; đề nghị duy nhất trong STATUS gồm deploy giữ
  data/preflight mới và một dual-reset transaction, không retry/fallback/recovery ngầm.
  Không có hardware attempt mới, endpoint thật mới hoặc firmware/READY/frontend/TS PASS.
- **Kiểm chính xác mã cuối:** sau bọc mọi nhánh releaseUpload cleanup thành typed
  CLEANUP_FAILED và thêm assertions, chạy lại full JVM/debug/release/lint cùng command trên:
  exit 0, 2 phút 24 giây, 326 tasks/65 executed/261 up-to-date; 253 JVM vẫn 0 failure/error/skipped,
  lint 0 errors/10 warnings. Không đổi counters hoặc kết quả instrumentation để làm đẹp.
- **Preflight cuối 12:26 +07:** OnePlus GM1900/API 30/USB Host còn online đúng TCP target,
  production consumed=true; report hiện có vẫn RAW_DESCRIPTOR_INSPECTION/READ_AND_CLOSED,
  pending=false/cleanup=false và timestamp recovery cũ. Không có raw descriptors mới.
  Không còn benchmark process; monitor đã kết thúc đúng cửa sổ, không phát hiện mất target.
  `git diff --check` PASS, stage trống, HEAD giữ nguyên.

## 2026-11-05 — Commit và push sửa Phase 2.9 theo yêu cầu

- Người dùng yêu cầu commit toàn bộ thay đổi hiện có và push lên origin nhánh hiện tại.
  Preflight: `chore/modernize-android-build`, HEAD và live origin cùng
  `d76bbfb63dd21d55af6ce27fdff0543eb500db72`, stage trống, một worktree, phạm vi 34 file.
- So SHA-256 của 28 file nguồn/tools với fingerprint kiểm cuối: không thay đổi.
  `git diff --check` PASS; không firmware/APK/ledger/raw logs trong phạm vi commit.
  Chỉ bổ sung checkpoint Git, không sửa source/tests hoặc reference checkouts.
- Message: `fix: harden Phase 2.9 bootstrap authorization and telemetry`, kèm
  Co-authored-by trailer. Kết quả commit/push xác minh từ Git sau thao tác.
- Không chạy lại build/instrumentation, không thao tác Android/USB hoặc cấp approval.
  Bằng chứng phần mềm gần nhất vẫn 253 JVM/29 targeted instrumentation và benchmark
  132/132 không USB; không đổi chúng thành kết quả mới của tác vụ Git.
  Hardware vẫn `AWAITING_EXPLICIT_NEW_HARDWARE_APPROVAL`.
- Staged whitespace check phát hiện CRLF ở 16 file mới; chỉ chuẩn hóa LF và kiểm
  nội dung chữ không đổi, không sửa Git config hoặc logic đã test. Stage lại và kiểm
  whitespace trước commit; không coi đây là lần chạy tests mới.

## 2026-11-05 — Tiếp tục Phase 2.9: full-path fake, audit durability và điểm dừng approval mới

- **Branch/commit trước và sau:** `chore/modernize-android-build`, `92a25b4962ff487c169e67649f454e277bcc60e6`; đầu phiên sạch, HEAD/live origin trùng baseline. Không stage/commit/push, checkout/reset/clean/rebase hoặc sửa references. Mọi sửa còn ở working tree.
- **Đã đọc:** `.github/copilot-instructions.md`, các scoped instructions build/toolchain/records/repository-sync/USB capture, sáu hồ sơ với checkpoint 04–05/11 và ADR-018/019/020, source approval/audit/Port/runner/controller/Activity/binding/coordinator/session/facade. Load project-handoff/android-cli. Agent host đọc và sửa sáu scripts approval/ledger/timing/benchmark/tests, không ADB hoặc hardware; parent xử lý toàn bộ đường Android và runtime.
- **Môi trường xác thực:** PowerShell Windows tại repo; build declarations/wrapper/SDK source.properties và JDK executable vẫn AGP 9.5.0/Gradle 9.8.2/JDK 25.0.5.0.3/compile 37.2/target 37/min 21/BT 37/NDK 30/CMake 4.1.2/Java source-target 8. Không cài/nâng/gỡ công cụ hoặc đổi môi trường hệ thống.
- **Git/graph:** `git status --short --branch`, log, remote, `git ls-remote origin refs/heads/chore/modernize-android-build`; không fetch/sync. `gitnexus status --json` không hỗ trợ; `status`, `analyze --index-only`, `status` đầu phiên PASS 66,7 giây và refresh sau đổi call paths PASS 101,4 giây. Graph dùng để kiểm phạm vi telemetry/dispatch, không coi thiếu caller hoặc phân loại risk là bằng chứng an toàn thay source/tests.
- **Nguồn công khai:** Android CLI `--help`, `docs search --help`, `docs search 'AtomicFile finishWrite fsync IOException SystemClock elapsedRealtime controlTransfer timeout'`; đọc trực tiếp AtomicFile API và AOSP Android 11. Xác nhận `finishWrite` có các lỗi sync/close/rename chỉ log. Không gửi source riêng, email TBS hoặc firmware tới dịch vụ ngoài.
- **Sửa Android:** `Tbs5530DurableFile` checked FD.sync + commit readback, `Tbs5530NativeAudit` giữ cùng full snapshots; nối vào native audit, approval storage và diagnostic report. Intent vẫn sync trước native, post-audit không mất native result/stop gốc. `requireDispatch` kiểm deadline/foreground/one-use nonce không I/O sau final device checks; `requireStartBudget` yêu cầu còn 240 giây trước consume/reset. Giữ ACK/heartbeat/USB timeout/TTL.
- **Sửa host:** Terminal nhanh theo run epoch/revision/boundaries, không phụ thuộc đã thấy pending; strict ledger missing/corrupt/duplicate/key/framing; deadline trước/sau target preflight; wrapper FullPathInstrumentation chỉ namespace `.phase29offline`, child early failure kể cả exit 0, bounded stdout/stderr và optional failure export chỉ qua kênh cũ còn đồng bộ. Không reconnect, retry, permit thật hoặc ledger repair/reset.
- **Mã kiểm thử mới:** `FullPathRig` và `Tbs5530FullPathBenchmarkTest` dùng cùng core approval/production Port/audit/binding/coordinator/session/AndroidRuntime worker, synthetic image/raw descriptors, fake Manager/Raw và Context chặn USB service thật. `Tbs5530DurabilityFaultTest`, `Tbs5530AuditCostTest`, deterministic deadline/budget/dispatch và runner budget tests. Không tạo seam bỏ hash validation trên uploader Android thật.
- **Build/test offline thực:** Wrapper `:drivers:testDebugUnitTest --tests '*Tbs5530OneShotBootstrapTest' --console plain --no-daemon` PASS 33 giây. Full command `:drivers:testDebugUnitTest :usbxfer:testDebugUnitTest :dvbservice:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console plain --no-daemon` chạy lại sau dispatch guard PASS exit 0, 2 phút 20 giây, 326 tasks/63 executed/263 up-to-date. XML cuối 254 methods = 240 drivers +1 usbxfer +13 service, 0 failure/error/skipped; lint 0 errors/10 warnings. Lượt full trước đó PASS 2 phút 50 giây kèm build test APK, không dùng duration đó làm kết quả mã cuối.
- **Build test cách ly:** `tools/Build-TBS5530-OfflineTestApp.ps1` PASS; `:drivers:assembleDebugAndroidTest` PASS, kiểm aapt exact package/targetPackage trước mọi install. Chỉ `adb -s <pinned TCP target> install -r -t` vào `.phase29offline`, `.phase29offline.test`, `drivers.test`, `dvbservice.test`; không dùng connected UTP hoặc cài lại production. Debug APK production mới chỉ build, chưa deploy.
- **Instrumentation cuối:** `am instrument -w -r -e class` chọn diagnostic/approval subclass/report/durability vào `.phase29offline.test/...AndroidJUnitRunner` PASS 46 methods/8,332 giây. Toàn bộ library test target `drivers.test/...AndroidJUnitRunner` PASS 37 methods/1,482 giây; service test target `dvbservice.test/...AndroidJUnitRunner` PASS 2 methods/0,017 giây. Tổng 85 methods riêng biệt, không cộng lặp rerun hoặc base/subclass. Audit-cost riêng PASS 1 method, full-path là cùng 1 method chạy 4 lượt thành công, không phóng đại thành 4 methods độc lập.
- **FAIL và khôi phục có thật:** Full-path đầu assertion mkdir scoped storage trước handshake, helper intermediate đợi tổng 240 giây, 0 ACK/0 USB; đã giữ FAIL trong JSONL. Sửa test gọi `getExternalFilesDir()` và canonical path; không xin quyền/appops mới. Một lượt challenge fail vì fixture install không phải benchmark-only; sửa fixture, không nới host validator. Fault không host sau storage fix kết thúc 30,067 giây với result FAIL/USB 0, chưa chạy runner. Không biến các lỗi preflight thành firmware PASS.
- **ADB gián đoạn:** Target cũ báo OFFLINE tại update test APK, list MCP xác nhận chỉ OFFLINE; không tự connect/reconnect, đổi Wi-Fi/VPN hoặc chuyển emulator. Đã hỏi người dùng bằng form và nhận chọn tự khôi phục Wireless ADB rồi tiếp tục không USB. Target TCP mới được pin lại cùng GM1900/API 30/boot; guard production so nguyên. Không resume namespace/challenge lỗi hoặc thực hiện tác động nguồn/OTG.
- **Lệnh benchmark thực:** `tools/Run-TBS5530-ApprovalBenchmark.ps1 -Serial <pinned target> -LedgerPath <session JSONL ngoài Git> -FullPathInstrumentation -AuditMode baseline -ApproveIsolatedAndroidBenchmark`, sau sửa fixture PASS 132/132, ACK 357/450/488/500 ms; tổng Android 54.386 ms. Parent command shell 69 tự `exit $LASTEXITCODE` nên nhận 128 cũ từ Git expected-fail, không phải instrumentation/helper FAIL; ledger wrapper/helper và `OK (1 test)` đều PASS. Các lượt sau kiểm PowerShell exceptions/`$?`, không diễn giải stale native exit code.
- **Ba lượt mới liên tiếp:** Cùng command với `-AuditMode verified`, không cài/đổi code giữa lượt; PASS 132/132 mỗi lượt, ACK p50/p95/p99/max 351/430/449/452; 359/442/451/459; 360/436/457/458 ms. Tổng Android 49.929/53.521/53.481 ms; helper 3 ADB subprocess/lượt. Mỗi lượt fake calls/full-length 132/132, audit 264 checkpoints, image/payload giả 8192/8196 byte, USB thật 0; đủ delay/cleanup/fresh observation fixture, không READY. Chỉ gộp 396 mẫu mã mới: nearest-rank 358/437/452/459 ms, không trộn FAIL hoặc benchmark lịch sử.
- **Audit có số đo, không speedup giả:** Storage-only cũ/mới 264 checkpoints và 6.711.056 byte/mode, tổng 1.604,809/2.077,883 ms; full-path audit cũ 2.185,105 ms, mới 2.355,012/2.310,075/2.244,889 ms. Chi phí tăng để kiểm được lỗi sync/commit; không có bằng chứng đủ để đổi sang journal hoặc bỏ durability. Phân rã cùng Android clock, GC và ordinal bands trong RESEARCH; thread CPU/syscall flush/UI export latency NOT MEASURED.
- **Mock host cuối:** `& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -File .\tools\Test-TBS5530-ApprovalChannel.ps1; exit $LASTEXITCODE`, exit 0; 26 negative checks, 132 shell loops, 133 exports, 13.878 ms; p50/p95/p99/max 53,58/66,14/71,93/84,54 ms chỉ local, ADB/USB 0. Bounded capture 262144 ký tự/stream; failure evidence và child exit 0 nhưng instrumentation failure PASS. PowerShell 5.1 UTF-8 không BOM parser không đạt, không đổi execution policy hoặc encoding toàn repo.
- **Static mã cuối:** aapt min 21/target 37, 126 TBS classes app+drivers major 52; debug có bootstrap/approval/durable, release không có; FullPathRig/standalone harness vắng cả debug/release, 0 entry `.fw`; zipalign `-c -P 16 4` cả hai APK exit 0. Không dùng static alignment hoặc native-load để tuyên bố runtime 16 KiB/USB API 21.
- **Bảo toàn ledger/guard:** Production preferences đọc trước/sau suites và cleanup không đổi consumed=true. Canonical host ledger đọc lại có 2 records: enrollment 1/prior consumed 1, new consumed/terminal 0/0, LF cuối/schema v1 hợp lệ, file bytes không đổi. Không xuất approval IDs/permit/ledger thật hoặc serial/IP/boot identity vào Git.
- **Evidence/cleanup:** JSONL, stdout lỗi, measured summary JSON và 4 synthetic native audit files lưu ngoài Git trong session files; hai namespace test lỗi đã pull/export trước cleanup. Namespace PASS do wrapper export rồi xóa; sau giữ evidence đã gỡ đúng ba test packages mới `.phase29offline.test`, `.phase29offline`, `dvbservice.test`. Giữ package production và `drivers.test` đã có trước; guard so lại không đổi. Xóa hai script scratch thống kê session, không xóa logs/evidence/firmware/ledger hoặc SDK/cache. Không còn process benchmark chạy nền.
- **Kết luận tại software gate:** `SOFTWARE_READY_AWAITING_HARDWARE_APPROVAL`; ACK 1500/heartbeat 2000/USB 2000/TTL 300000/benchmark 240000 ms, start reserve 240000 ms. Attempt 04-11 vẫn CONSUMED/UNCERTAIN sau 23 native full-length/1344 image bytes, chưa release; không có endpoint thật mới. Bước duy nhất kế tiếp là hỏi phê duyệt riêng một transaction mới và nguồn OTG hiện tại, rồi fresh preflight/cập nhật APK giữ data/xác nhận UI theo STATUS. Không chuyển frontend, I²C, tuning, PLP, TS hoặc phase mới.

### Bổ sung chốt: không nhầm timestamp ACK với file completion

- **Lý do đo thêm, không đổi protocol:** Source xác nhận `request_elapsed_ms` lấy trước request write; `host_ack_elapsed_ms` lấy trước ACK temp-write/rename. Parent thêm bốn timestamp storage vào test harness; agent thêm device uptime sau rename trong cùng PUT frame và ledger event riêng. Không ghi permit lần hai hoặc thay nonce/deadline/ACK validity. Scope marker là `TEMP_WRITE_RENAME_COMPLETE_NO_EXPLICIT_FSYNC`, durable status `NOT_MEASURED`; không tuyên bố permit đã fsync.
- **Build/deploy cách ly:** `Build-TBS5530-OfflineTestApp.ps1` PASS; cài lại đúng hai test packages mới `.phase29offline`/`.phase29offline.test`, không cài production. Cùng command full-path với ledger marker riêng, một baseline rồi ba verified liên tiếp, không thay code/cài package giữa bốn lượt. Command shell 99 exit 0, cả bốn instrumentation `OK (1 test)` và helper PASS.
- **Kết quả mới nhất:** Baseline ACK 339/437/453/467 ms, runner/tổng Android 43.797/44.888 ms, host wrapper UTC 54.279,901 ms. Ba lượt mã cuối ACK 352/440/456/525; 357/440/459/468; 362/441/464/468 ms, tổng Android 51.952/53.353/53.820 ms, host wrapper UTC 66.218,737/67.430,042/66.791,771 ms. Không trộn host UTC vào clock ACK hoặc kết luận speedup vì baseline này nhanh hơn và điều kiện scheduler/mạng không cố định.
- **Ranh giới cuối:** Mỗi lượt 132 fake/full-length, 264 audit checkpoints/6.711.056 byte, USB thật 0; 396 ACK ba lượt verified nearest-rank 358/440/464/525 ms, không timeout. Request-start → final dispatch max 548,464/484,658/492,292 ms. Join marker đúng một mẫu cho mỗi `(run, ordinal, host_ack_elapsed_ms)`, 528 samples của bốn lượt; ordinal 0 là initial/heartbeat, không đếm thành write.
- **Chi phí và giới hạn:** Request storage p95 mới 10,629/9,971/10,293 ms; observation storage p95 17,363/15,160/15,809 ms. Native audit tổng baseline 2.025,589 ms, ba mới 2.443,531/2.221,107/2.292,820 ms. Log observations minified chuẩn hóa tăng 65.231 → 88.463 byte do thêm marker, không tính newline cuối; ledger marker riêng 1.612.172 byte tại checkpoint. Uptime lower-bound 10 ms và sampling sau rename cho delta marker→observed min -2/-4/-3/-2 ms; giữ nguyên, không clamp hoặc cấp quyền từ marker. GC delta verified 66/66/65, thời gian 855/906/895 ms; lượt hai có 1 blocking GC/8 ms, không tuyên bố tất cả đều 0. Thread CPU và fsync-durability của host permit NOT MEASURED.
- **Host validation marker:** Mock PASS 30 negative checks +132 measured loops, ADB/USB 0; frame cũ vẫn tương thích và timestamp authorization cũ được giữ nguyên. Không đổi ACK 1500/heartbeat 2000/USB 2000/TTL 300000/benchmark 240000 ms; đây vẫn Phase 2.9, không tạo phase phụ.
- **Cleanup lần cuối:** Export thêm bốn native audit tổng hợp mới ngoài Git trước khi gỡ lại đúng hai test packages vừa tạo; guard production so sau cleanup vẫn nguyên consumed=true, `drivers.test` có trước không bị gỡ. Giữ cả JSONL FAIL/PASS cũ và mới, summary JSON đã lọc không có IDs; xóa script scratch thống kê marker. Sáu hồ sơ thêm checkpoint marker mới, không sửa lịch sử lỗi hoặc hardware attempt 04-11. Không có firmware upload/CPU release/raw operational thật mới.
- **Graph cuối:** `gitnexus analyze --index-only` sau marker bị FAILED FTS `file_fts` inconsistent/COPY; không drop/recreate/force/repair hoặc gọi graph tiếp. Source/diff/fingerprints và test results vẫn là bằng chứng; đây là giới hạn công cụ, không biến thành firmware FAIL hoặc graph PASS.
- **Kiểm cuối hồ sơ:** `git diff --check` PASS; mới thêm câu/bullet trên một source line, không reflow lịch sử. Rà thủ công các bảng và các câu mới, không chỉ dựa vào regex phát hiện dính chữ. Stage trống; code fingerprints ngoài Git khóa đúng working tree đã kiểm. Hardware approval vẫn chưa được cấp tại checkpoint này.

### Checkpoint software cuối sau bảo toàn raw descriptor

- **Thiếu sót đã sửa:** Parser/report cố ý không giữ raw bytes/unknown payload; chỉ fields/hash chưa đáp ứng yêu cầu raw evidence của attempt. Thêm `RawAudit` sink riêng cho runner debug, lưu hai file private theo run epoch bằng checked writer, không sửa parser/report hoặc đưa binary vào Git/APK, không thêm USB read. Lỗi lưu trước upload chặn trước consume/native; lỗi sau đủ writes giữ counters/metadata và UNCERTAIN, không retry. Hai JVM methods mới kiểm đường byte/fault này.
- **Mã cuối được build/kiểm lại:** Wrapper full JVM/debug/release/lint và `:drivers:assembleDebugAndroidTest` PASS exit 0, 2 phút 42 giây, 357 tasks/70 executed/287 up-to-date. XML 256 methods = 242 drivers +1 usbxfer +13 service, 0 failure; lint 0 errors/10 warnings. Build test cách ly PASS. App isolated rerun 46 methods/8,181 giây và library 37/1,421 giây PASS; service 2 methods trước đó không đổi source/native code.
- **Bốn lượt full-path mới sau raw sink:** Baseline ACK 363/465/471/472 ms, tổng Android 55.221 ms, host UTC 69.305,293 ms. Ba verified liên tiếp 362/452/463/474; 361/449/466/472; 365/447/463/490 ms, tổng Android 54.036/52.618/54.340 ms, host UTC 67.986,521/66.389,942/67.081,527 ms. Command shell 111 exit 0, bốn helper/instrumentation PASS, không cài/đổi code giữa bốn lượt.
- **Không thiếu native/raw evidence giả:** Mỗi lượt 132 fake full-length, 8.192 image/8.196 payload bytes giả, 264 native audit checkpoints/6.711.056 byte serialize, `raw_descriptor_files=2`, `descriptor_reads=2`, USB thật 0; bytes hai raw files được assert khớp fixture. Ba verified mới riêng 396 ACK nearest-rank 363/450/466/490 ms, 0 timeout; request-start → dispatch max 494,732/491,537/513,080 ms. Giữ riêng toàn bộ benchmark cũ, không trộn FAIL hoặc đếm nhiều lần cùng test method.
- **Audit/GC mã cuối:** Tổng audit baseline 2.104,114 ms, ba verified 2.293,906/2.356,181/2.347,036 ms. GC delta baseline 63 lần/862 ms, ba verified 67/860, 67/880, 65/878, blocking GC 0 ở bốn lượt này; lượt marker trước có 1 blocking GC/8 ms vẫn giữ lịch sử. Không tuyên bố speedup hoặc CPU/audit là nguyên nhân chắc chắn của biến động mạng/scheduler.
- **Host cuối:** Mock 30 negative checks/132 measured loops/133 exports PASS exit 0, tổng 16.271 ms, p50/p95/p99/max 63,61/83,32/121,39/257,54 ms chỉ local, ADB/USB 0. Canonical ledger mở FileMode.Open/FileAccess.Read: 600 bytes, 2 records, enrollment 1/prior consumed 1, new consumed/terminal 0/0, length không đổi. Source host temp `phase29-session-approval.new` không trùng AtomicFile temp `.json.new`; sidecars `.json.bak/.json.new` phải kiểm và giữ nguyên trước hardware, không xóa tự động hoặc giả định OEM giống hệt AOSP.
- **Cleanup/evidence cuối:** Export 4 native audit JSON +8 raw binary fixture files ra session files ngoài Git trước gỡ hai test packages vừa tạo; raw lengths 189 byte ×4 và 43 byte ×4 đã đối chiếu, không raw tuner thật. So production preferences sau cleanup vẫn consumed=true nguyên nội dung, giữ `drivers.test` có trước. Summary JSON cuối đã lọc IDs, có đủ percentile/ordinal bands/marker scope, không artifact hóa raw logs/ledger/permit thật. Script scratch cuối đã xóa.
- **Giới hạn và gate:** Không sửa GitNexus FTS ngoài phạm vi; graph cuối không đáng tin, dùng source/tests/fingerprints. ACK 1500/heartbeat 2000/USB 2000/TTL 300000/benchmark 240000 ms, start reserve 240000 ms giữ nguyên. `SOFTWARE_READY_AWAITING_HARDWARE_APPROVAL`; chưa có approval/GUID/permit/hardware write mới. Static mã cuối: 127 TBS classes major 52, minSdk 21/targetSdk 37, bootstrap/raw sink chỉ debug, harness vắng debug/release, firmware entries 0; zipalign cả hai exit 0, lint 0 errors/10 warnings. Debug APK SHA-256 `ae337e4aea703a3d1aadb024fb49ce5a00d2e786f6a3c313a61842724deb80a6`, chỉ build, chưa cài production. AgentMemory đã nhận checkpoint qua Gateway và trả success; response không xác nhận remote-vs-local fallback, không coi đó là bảo đảm lưu bền liên phiên. Bước duy nhất tiếp theo là hỏi phê duyệt riêng đúng một transaction cùng xác nhận nguồn OTG hiện tại; chưa kết thúc firmware bring-up hoặc chuyển phase.

### Transaction mới được duyệt — host fail trước initial permit/native

- **Approval mới thực tế:** Form hỏi riêng đúng một hardware transaction sau software gate; user chọn approve_one và physical_conditions=true, xác nhận OnePlus USB Host qua OTG/no backfeed/no other app. Sau fresh preflight, form UI-ready được user chọn sẵn sàng tự bấm UI ngay khi nút bật. Không suy approval từ prompt hoặc benchmark.
- **Fresh device/APK/guard:** Đúng TCP OnePlus GM1900/API 30/boot pin, pin 100% lúc đầu. APK debug SHA đã khóa được `adb -s <target> install -r` tại chỗ; legacy `phase29-attempt.xml` so nguyên trước/sau, không uninstall/clear. Sidecars approval `.json.bak/.json.new` không thấy, không xóa gì.
- **Permission/raw mới:** Permission-only lần đầu timeout vì thời gian đọc/tap dialog; sau OK và preflight permission-only mới đạt ĐÃ CẤP, open 0. Raw button đọc mới 189 byte/22 ms, hash `e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`, COMPLETE_RETURNED_TREE/MATCH/BOOTSTRAP_CANDIDATE/FX2_DEFAULT. Open/read/close 1/1/1, claim/release/transfer 0/0/0. Không dùng raw recovery 04-11 thay evidence mới.
- **Firmware/SAF:** File đã có `/sdcard/Download/phase29-dvb-usb-id5530.fw` đúng 8192 byte/hash khóa, không copy/deploy image mới. Key events ban đầu bị IME sửa tar→tả; kiểm cây UI phát hiện rồi CLI setText đúng source/entry, không thay IME hoặc bỏ validation. SAF search chọn đúng file sau UI-ready; challenge v2 mới đúng mode/new-attempt/legacy true/hash/plan.
- **Helper chỉ chạy một lần:** Sau preflight target Identity từ Get-TBS5530ApprovalTarget và boot so pin, cấp GUID mới sau approval, lưu context riêng ngoài Git. Chạy Watch với `-NewApprovalId <fresh> -ExplicitNewAttemptApproval -AcknowledgePriorConsumed -PhysicalHostAndOtgConfirmed -ExpectedIdentity <pinned> -HardTimeoutSeconds 300`; không Benchmark/legacy flags. Command background shell 147 khởi động rồi fail ở Watch line 223, StrictMode property approval_protocol không có.
- **Evidence lỗi trước native:** Request legacy trên app chỉ fields challenge/ordinal, không run/protocol; permit vẫn fields cũ thiếu run/protocol, chưa initial grant mới. Canonical ledger nay enrollment 1/prior consumed 1/NEW_APPROVAL_CONSUMED 1/production_authorization_start 1/production_authorization_fail 1; failure HOST_HELPER_ERROR, ACK/observation 0/0, helper USB 0. Report production vẫn fresh RAW_DESCRIPTOR_INSPECTION/READ_AND_CLOSED, pending/cleanup false; current run native audit 0/raw files 0, app new-attempt prefs 0. Không upload A0, CPU stop/release/corrective hoặc raw operational mới.
- **Fail-stop/bảo toàn:** Không rerun helper hoặc reuse GUID vì chưa native; giữ host reservation đã consumed/failure, legacy true và files cũ nguyên trạng. Đóng UI bằng nút ĐÓNG để revoke challenge, không chuyển app/host/nguồn hoặc rút/cắm recovery. Agent host chỉ được giao repro/fix/mock stale-file offline trong cùng Phase 2.9, không ADB/hardware/ledger mutation.
- **Trạng thái:** BLOCKED_WITH_EVIDENCE ở helper; dữ liệu software trước đó vẫn có phạm vi riêng nhưng chưa bao phủ đúng production legacy leftovers. Mọi transaction thử tiếp sau fix cần approval/GUID/fresh preflight/UI mới; không coi approval cũ còn sống.

### Sau fix legacy-frame và audit cuối, quay lại điểm chờ quyết định mới

- **Host repro/fix:** Agent sửa đúng Watch và host fixtures; prior challenge/run được phân loại trước strict fields, thiếu challenge hoặc same-current malformed vẫn deny, RAW report không bị assert bootstrap binding. Current malformed được chặn trước reservation/initial grant. Fixture production mô phỏng chính old request challenge/ordinal +old permit +RAW report rồi Android thay request v2 sau grant, thêm rapid terminal ACK 0 và replay. Không xóa frames production hoặc ledger để né lỗi.
- **Host validation cuối thực:** `pwsh -NoProfile -File .\tools\Test-TBS5530-ApprovalChannel.ps1`, exit 0; 45 negative checks +7 production cases, 132 shell loops/133 exports, 20.961 ms, ADB/USB 0/0. Ledger chỉ đọc 1773 bytes/5 records; hash/length không đổi và parent so với hash-pin sau reservation cũng trùng, không event production mới từ các lượt fake.
- **Lỗi tightly coupled đã tái hiện/sửa:** Test JVM `fullLengthFinalDetachCannotHideSuppressedPostNativeAuditFailure` FAIL trước sửa vì expectedFinalReleaseDetach có thể bỏ qua AUDIT_FAILED suppressed dưới USB_DETACHED sau full-length 132. Sửa runner deny chuỗi có AUDIT_FAILED, giữ typed cause và native results; focused toàn OneShotBootstrapTest PASS 58 giây. Không corrective release hoặc observation tiếp khi audit lỗi; không phải lỗi thực đã gặp ở tuner trong transaction 0 native.
- **Gates mới nhất:** Full wrapper JVM/debug/release/lint/test-APK PASS 3 phút 22 giây, 357 tasks/63 executed/294 up-to-date; XML 257 methods =243 drivers +1 usbxfer +13 service, 0 failure. Isolated app 46 methods/9,129 giây và library 37/1,426 giây rerun PASS; service 2 methods trước đó không đổi source. Build test isolate PASS, không update APK production mới nhất sau failure.
- **Benchmark sau fix:** Shell 173 baseline +3 verified cùng mã host/runner, không install/đổi code giữa bốn lượt, exit 0. Baseline ACK 364/452/472/474 ms, tổng Android 55.762 ms. Ba verified 132/132 PASS, ACK 363/436/451/457; 367/453/461/468; 364/447/464/466 ms, tổng Android 54.124/54.714/51.845 ms. Mỗi lượt native fake/full-length 132, audit 264/6.711.056 serialized bytes, raw files/reads 2/2, USB thật 0. Ba verified riêng 396 samples, nearest-rank 365/447/463/468 ms; final dispatch max 494,749 ms. Tail ordinal 120–132 max 432/432/455 ms; không gộp với FAIL/benchmark trước.
- **Target mới được xác thực, không reconnect:** Cổng TCP trước không còn khi lệnh đọc guard chỉ đọc chạy; phép so string false lúc ADB exit lỗi không phải guard đổi. Discovery tìm target TCP đang online cùng mDNS alias; pin lại cùng GM1900/API 30/boot và kiểm exit code, guard true nguyên nội dung. Chỉ dùng target mới cho no-USB fakes, không resume hardware/approval đã tiêu thụ.
- **Cleanup và static:** Export 12 synthetic files ngoài Git rồi gỡ đúng 2 test packages mới tạo, giữ production/ledger/request/permit cũ và drivers.test có trước. Legacy consumed so sau cleanup trùng; ledger hash trùng. Xóa script scratch thống kê, giữ summary JSON đã lọc IDs. Static mã cuối 127 TBS classes major 52/minSdk 21/targetSdk 37, uploader/raw sink chỉ debug, harness vắng debug/release, firmware entries 0, zipalign cả hai exit 0, lint 0 errors/10 warnings; APK debug mới hash `40fc36681867a67fe2290bb67360eac7f89978efde86a63f1fa73c168304411e` chưa cài production.
- **Điểm dừng:** SOFTWARE_READY_AWAITING_HARDWARE_APPROVAL, cùng Phase 2.9. Transaction đã duyệt thất bại vẫn host consumed/0 native mới; attempt 04-11 vẫn 23 native full/1344 image bytes/UNCERTAIN không release. Không tự chạy helper thật lần 2 hoặc cấp GUID mới. Cần hỏi độc lập một transaction khác; user có thể chọn chờ, không recovery/nguồn hoặc phase tiếp.

### Approval độc lập thứ hai — initial grant có, heartbeat đứng trước UI

- **User approval/UI riêng:** Sau software fix/gates, user chọn approve_one_new và physical_current=true, rồi UI-ready mới. Chưa cấp GUID khi target cũ không còn; discovery chỉ đọc thấy TCP target đang online khác cổng, pin lại cùng GM1900/API 30/boot, không reconnect hoặc resume GUID cũ. Update APK tại chỗ hash mới 40fc…411e, legacy guard so nguyên; không reset dữ liệu.
- **Fresh preflight:** Grant USB mới qua dialog, permission-only sau đó ĐÃ CẤP/open 0. Raw read mới 189 byte/14 ms/hash e288…52de, FX2_DEFAULT/MATCH, open/read/close 1/1/1, transfer 0; file SAF vẫn 8192/hash khóa, source/entry đặt bằng CLI setText không IME sửa chữ. UI-ready được user xác nhận trước khi chọn SAF và tạo challenge/GUID mới để không ăn TTL sớm.
- **Một helper mới duy nhất:** Shell 193 chạy flags production với GUID khác sau approval. Initial permit đúng protocol 2/current run/authorized ordinal 0 đã publish; không còn property-not-found. Sau 120 giây chưa có bootstrap report, một lần đọc thấy UI app active/foreground, current per-attempt store/native audit 0. Permit heartbeat vẫn bằng challenge.created_elapsed_ms chính xác, expires=created+300000, không tiến.
- **Terminal thực:** Shell 193 TOTAL_SESSION_DEADLINE, ledger elapsed_host_ms 300029/total_bound 300000, ACK/observation 0/0/helper USB 0. Report vẫn RAW READ_AND_CLOSED, current app store/native audit 0; chưa có CPU stop/release/vendor A0 mới. Không quy timeout tổng thành ACK deadline/USB timeout hoặc đổ lỗi user không bấm khi heartbeat chưa tiến.
- **Bảo toàn:** UI đóng/revoke sau terminal, không rerun helper/reuse ID hoặc recovery. Canonical ledger 8 records giữ enrollment 1/prior consumed 1/production start 2/new consumed 2/fail 2, hash-pin riêng sau lượt hai; legacy true nguyên nội dung. Hai GUID tiêu thụ ở host không đồng nghĩa hai native hardware attempts; attempt 04-11 cũ vẫn giữ nguyên.
- **Sửa tiếp trong Phase 2.9:** Agent host chỉ source/repro/mock nhánh stale request có thể continue trước heartbeat, không ADB/ledger write. Parent thêm test-only seed legacy request/permit trong fresh benchmark namespace, chờ initial grant current-run rồi UI-wait 3500 ms và requireCurrent trước owner; không chỉ benchmark bắt đầu ngay sau grant như trước. Chưa nới heartbeat 2000/ACK 1500/TTL 300000 hoặc sửa protocol. Trạng thái BLOCKED_WITH_EVIDENCE trong lúc kiểm nhánh này, không tạo phase phụ.

### Probe clock dưới đúng UID và mở rộng parity

- Agent trả bounded fix Watch/Test: không chứng minh foreach continue là root heartbeat-loop; đã bỏ continue để dùng PUT chung. Mock tái hiện `/proc/uptime` read lỗi tạo -990 bị clamp về created, thêm read/format/range/alignment fail-closed DEVICE_CLOCK_UNAVAILABLE, không nới timeout. Mock 50 negatives/10 production fixtures/6 clock faults/legacy wait 3500 ms rồi 132 steps PASS, tổng 34.782 ms, ADB/USB 0.
- Parent build harness mới PASS, aapt kiểm đúng `.phase29offline`/test, pin OnePlus/boot/guard rồi chỉ install test packages. Harness seed prior request/permit, chờ current-run grant rồi UI-wait 3500 ms, cùng fake Port/worker không USB. Live lần đầu shell 211 FAIL INVALID_REQUEST_BINDING/approval_protocol ở BenchmarkOnly trước native; giữ namespace/evidence, không cleanup hoặc hardware ledger write. Đây là mode-classifier gap mới cần fix, không được nới current binding.
- Read-only probe shell 212: `adb -s <target> shell cat /proc/uptime` exit 0/format valid; `adb -s <target> shell run-as info.martinmarinov.dvbdriver cat /proc/uptime` exit 1/Permission denied. Không xuất counter/boot ID/IP vào Git; không su/root/chmod/cấp quyền/SELinux. Đây là bằng chứng UID khác giải thích vì sao clean shell benchmark che clock access production, không phải FX2 chậm.
- Giao agent split controller ở shell UID đọc clock/boot, chỉ app-private GET/PUT qua run-as; unify stale classifier cho BenchmarkOnly/production. Không native API/canonical ledger mutation/GUID thứ ba hoặc approval mới. Cần 3 live full-path mới có privilege parity/3500 ms wait trước khi hỏi hardware khác.

### Sau UID split: phát hiện quyền external storage khác app-private

- Agent chốt controller shell clock/boot, chỉ POLL/PUT/EXPORT run-as, classifier prior/current dùng chung cả hai mode; args wrapper không thêm RunAsPackage tùy ý. Mock 51 negatives/10 production +2 benchmark cold-start cases/6 clock faults/2 UID-model checks PASS, no ADB/USB. Chưa coi mock là runtime parity PASS.
- Live fake shell 218 với legacy seed/UI-wait 3500 ms dừng BENCHMARK_CHILD_FAILED trước initial grant; captured child exit 0 nhưng instrumentation FAIL `Initial host approval missing`, 30,07 giây. Không đếm 3 loop dự định thành 3 tests đã chạy; dừng ngay lượt 1, evidence namespace giữ.
- Read-only probe shell 221: cùng external test challenge file shell cat exit 0/JSON, run-as `.phase29offline` cat exit 1/Permission denied. Root private qua run-as pwd exit 0 khớp `/data/user/0/info.martinmarinov.dvbdriver.phase29offline`; chuyển harness channel sang app-private canonical Context.getFilesDir, build PASS. Không cấp external permission/SELinux/chmod hoặc chỉnh production.
- Agent được giao exact private offline prefix trong wrapper +file access error classification, không arbitrary package/path, không hardware/ledger mutation. Canonical ledger 8 records đã pin từ transaction 2 vẫn không được sửa hoặc reuse; chưa có approval/GUID thứ ba.

### Checkpoint private UID parity, trước bỏ clamp timestamp

- Agent chốt Watch/Run/Test path instrumentation chỉ `/data/user/0/info.martinmarinov.dvbdriver.phase29offline/files/tbs5530-approval-benchmark/<UUID>`, auto run-as offline cho file I/O/cleanup sau PASS, standalone giữ Downloads. Permission denied thành FILE_IO_PERMISSION, read/empty lỗi thành CHANNEL_READ_ERROR, chỉ missing-file thật mới chờ. Mock 60 negatives/10 production +2 benchmark fixtures/5 file-I/O faults PASS, 0 ADB/USB.
- Parent pin lại cùng target/boot/legacy guard và ledger 8 records không đổi, aapt kiểm exact namespaces, install-r-t chỉ test APKs. Shell 228 chạy 3 verified cùng code, legacy seed/UI-wait 3500 ms, mỗi lượt 132/132 PASS, USB thật 0; ACK 512/575/606/668, 524/603/986/1116, 516/587/815/952 ms, tổng Android 77.066/79.626/78.549 ms. Final dispatch max 688,233/1137,309/971,735 ms; fake full-length 132/audit 264/raw files 2/reads 2 mỗi lượt. Run-as/file-I/O child dispatch attempts 281/282/283, ADB subprocess 3, không đếm thay OS forks.
- Baseline callback audit cũ cùng permission/path/UI wait đo sau ba verified bằng shell 230 PASS 132/132, ACK 520/597/632/654 ms, tổng Android 78.394 ms/audit 2.191,032 ms. Ba verified audit tổng 2.313,524/2.367,759/2.289,114 ms. Không so với clean shell benchmark cũ để tuyên bố speedup; khác permission và điều kiện.
- Source timestamp review phát hiện POLL.NOW trước read, host_seen=max(request), PUT clamp minimum dưới 10 ms. Parent yêu cầu bỏ clamp đúng prompt: sample sau read và tick thực bounded hoặc deny, không đổi deadline. Các PASS trên vẫn đúng checkpoint nhưng chưa final no-clamp gate; đang chờ agent fix/mocks rồi rerun 3 private UID. Không helper production/GUID mới hoặc ledger mutation.

### Gate cuối private UID/no-clamp, không phần cứng mới

- Agent đã bỏ mọi clamp: POLL lấy clock sau app-file I/O, host_seen raw NOW; PUT chờ tối đa 2×10 ms lấy tick thật hoặc fail, chạm deadline dừng trước ghi permit. Không sửa minimum/created/request thành timestamp hoặc gia hạn ACK. Timestamp marker sau rename vẫn không phải fsync proof.
- Mock cuối `pwsh -NoProfile -File .\tools\Test-TBS5530-ApprovalChannel.ps1` exit 0, 61 checks; gồm 10 production/2 benchmark cold-start, UI-wait 3500 ms rồi 132 steps, 8 clock faults/3 raw-tick wait/2 UID/5 file-I/O faults. `timestamp_clamping=NONE`, `poll_clock_after_file_io=PASS`, 132 loops/133 exports, ADB/USB 0, tổng 98.017 ms. P50/p95/p99/max 380,9/503,97/693,47/1304,79 ms chỉ mock Windows, không Android latency.
- Parent pin target/boot/guard và ledger 8 records trước shell 236; 3 verified cùng mã no-clamp/UID private/legacy seed/UI wait 3500 ms, 132/132 PASS mỗi lượt, tổng Android 71.721/78.765/80.427 ms. ACK 505/588/625/626; 524/603/854/911; 543/625/662/670 ms, dispatch max 646,489/933,022/689,997 ms. Mỗi lượt fake 132 full/audit 264/6.711.056 serialized bytes/raw 2 files/2 reads/USB thật 0; run-as dispatch 281/284/282/ADB helper 3.
- Baseline callback audit cũ cùng UID/path/UI wait/no-clamp đo sau 3 lượt bằng shell 237 PASS 132/132, ACK 536/632/646/760 ms, tổng Android 80.026 ms/audit 2184,238 ms, run-as 281. Verified audit 2247,719/2353,683/2407,101 ms; không hứa speedup. Ba verified riêng 396 samples, nearest-rank 526/606/669/911 ms, không gộp FAIL hoặc baseline; tail 120–132 max 570/669/581 ms.
- App instrumentation riêng sau UID/no-clamp shell 239 PASS 46 methods/9,12 giây; production Java không đổi từ full JVM 257/library 37/service 2/static 127 major 52/minSdk 21/fw 0/release isolation. Không rerun toàn Gradle để gán PASS mới khi chỉ đổi host/test harness; phạm vi cũ giữ rõ.
- Cleanup shell 242 export 34 synthetic files gồm 24 native/raw evidence và 10 files từ 2 failed external namespaces, sau đó gỡ đúng 2 test packages mới. Production legacy so sau vẫn true nguyên nội dung, canonical ledger 3031 bytes/8 records/hash trùng parent pin; không helper thật/GUID thứ ba, không xóa request/permit/attempt cũ. Script scratch thống kê đã xóa, summary JSON no-clamp lưu riêng ngoài Git.
- Kết luận software: SOFTWARE_READY_AWAITING_HARDWARE_APPROVAL cho quyết định độc lập mới; ACK 1500/heartbeat 2000/USB 2000/TTL 300000/benchmark 240000 ms, reserve 240000 ms giữ nguyên. Max ACK 911/dispatch 933,022 ms dưới 1200 ms không bảo đảm zero-failure; tổng 80,427 giây fake không đủ bảo đảm 132 USB timeouts. Hai approval đã consumed không reuse, cả hai 0 firmware A0 mới, không CPU release/recovery hoặc phase khác.

## 2026-11-06 — Phase 2.9 đạt OPERATIONAL_CANDIDATE sau đúng một upload thực mới

- **Approval thứ ba độc lập:** Sau private UID/no-clamp gates, form hỏi riêng một transaction khác được user chọn approve_one_fresh/physical_current=true/ready_for_ui=true. Không reuse hai GUID trước; không suy quyền từ prompt hoặc software PASS. User tự bấm UI trên OnePlus, parent không tap upload hoặc tự confirm thay user.
- **Fresh preflight:** Get-TBS5530ApprovalTarget/boot pin cùng GM1900/API 30, legacy prefs so nguyên. Installed APK hash 40fc…411e và firmware local hash khóa được đọc thật; pin 91%, nguồn vẫn chỉ OTG theo user. Một lệnh PS đầu lấy ký tự đầu của scalar pm-path khiến sha256sum báo path p không có; sửa bằng array và kiểm exit code, không dùng kết quả lỗi làm preflight PASS. Không cài hoặc thay APK upload khi installed hash đã đúng.
- **Quyền/raw trước upload:** Permission-only mới và OK dialog từ cây UI mới; raw read-only 189 byte/hash e288…52de/FX2_DEFAULT/MATCH, open/read/close 1/1/1, transfer 0, 18 ms. Metadata source/entry đặt bằng CLI setText, không đổi IME; SAF chọn đúng file sau user ready, challenge mới khác cả hai run trước, protocol 2/ACK 1500/heartbeat 2000/hash/plan đúng. Không xóa request/permit/sidecar hoặc ledger.
- **Một helper thật mới:** Shell 253 tạo GUID khác sau fresh preflight, chạy production Watch một lần với explicit flags và canonical ledger. Đã kiểm heartbeat current-run tiến; tại lần đọc nó đang authorized ordinal 43, không còn frozen-created. Chờ helper synchronous call tự trả, không force-close/cancel USB. Helper kết thúc exit 0, không retry/fallback hoặc corrective write.
- **Bằng chứng 132 real native:** Report current run 00:03:48.412 +07 có planned/guard requested/accepted/native started/completed/full-length đều 132, API results 132 full-length, image 8192/payload 8196; release 131/132 returned 1 requested 1. AttemptStore mới consumed true trước write, legacy consumed true nguyên nội dung. Export native audit/current store/raw-before private ngoài Git; không firmware payload/APK/permit/ledger raw vào Git.
- **Transition và observation ban đầu:** Final native end full-length, attachment event 1, FINAL_RELEASE_FULL_LENGTH_DETACH_OBSERVATION_REQUIRED, rồi STOP JAVA_IO_EXCEPTION trước observer epoch mới; snapshot epoch 1/state UNCERTAIN/after NOT_OBSERVED. Source đã đi delay 100 ms và đóng upload lease, pending false/cleanup NONE. Không đổi thành USB timeout hoặc upload fail sau partial; không CPU release khắc phục/USB reset/power-cycle/recovery. Source analysis chỉ kết luận phù hợp observation attachment/permission unavailable, không dựng exception message không được lưu.
- **Ledger immutable:** Canonical 92018 bytes/275 records: enrollment 1/prior consumed 1/production start 3/new consumed 3/fail 2/ordinal authorized 132/Android observed 132/NEW_ATTEMPT_TERMINAL 1. Terminal giữ UPLOAD_COMPLETE_OPERATIONAL_UNVERIFIED, firmware_upload_completed true/firmware_pass false/ready false theo thời điểm helper kết thúc; không append event ngoài schema hoặc sửa terminal theo observation riêng về sau.
- **User approval quan sát riêng thứ nhất:** User chọn approve_read_once cho owner/permission mới và raw read đúng một lần, no transfer. Close/open diagnostic mới có một selector tap đến quá sớm chưa thấy nút, đọc lại UI mới rồi thực hiện permission-only/OK/raw. Report 00:10:59.397 +07: raw complete 50 byte/no issues/hash 22e2…add5, bulk IN 0x82/0x81 512 byte, 10 ms/1 read/0 transfer, nhưng candidate UNKNOWN do bInterval 0x82=1 và classifier cũ đòi 0.
- **Source/bug observation:** Đọc Microsoft USB_ENDPOINT_DESCRIPTOR bInterval: polling cho interrupt/isochronous, kiểu khác bỏ qua; không sửa descriptor firmware để hợp classifier. Ghi ba reference SHAs hiện tại bằng rev-parse, đọc official Git object tbs5530.c USB_BULK/0x82, C usb_rcvbulkpipe0x82 và Go package comments; không fetch/sync hoặc sửa references. Thêm Tbs5530OperationalDescriptorTest fixture numeric chuẩn, test FAIL trước fix; bỏ đúng interval=0 gate của debug OPERATIONAL_CANDIDATE, quét interval 0..255 và negative address/type/packet/alternate, focused PASS. Main operational transport/default bootstrap fingerprint không đổi, không READY.
- **Lưu raw binary:** Standalone AndroidRawDescriptorPort cũ không giữ binary, thêm checked-durable file private theo observation UUID, bounded MAX_BYTES, không đưa raw/string/unknown payload vào JSON/report/Git. Instrumentation test inject storage bytes/save/null/invalid ID không USB. Probe sysfs descriptor cache không tìm được path đọc được, không chmod hoặc đọc bus; numeric fixture hash không khớp raw runtime được giữ là fixture, không gọi là binary thật.
- **Final full software validation:** Wrapper `:drivers:testDebugUnitTest :usbxfer:testDebugUnitTest :dvbservice:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :drivers:assembleDebugAndroidTest --console plain --no-daemon` PASS exit 0, 2 phút 45 giây/357 tasks/70 executed/287 up-to-date. XML 259 methods =245 drivers +1 usbxfer +13 service, 0 failure/error/skipped. Build isolated test PASS. App 47 methods/9,315 giây và library 37/1,398 giây rerun PASS, service 2 PASS trước đó không đổi source; tổng 86 methods có phạm vi, không cộng repeated runs hoặc hardware transaction thành methods.
- **ADB và approval quan sát riêng thứ hai:** Khi install test riêng sau observation, TCP ADB báo OFFLINE; không reconnect hoặc dùng mDNS alias thay target. User chọn khôi phục TCP và approve một raw read mới kèm inplace APK update, giữ nguyên tuner/OTG/nguồn. Discovery/pin lại cùng boot/GM1900/API 30; không dùng lỗi ADB read để giả guard đổi. Update production APK observation hash 12a97e1745c031ec5c80565c6816d00f2e53b50d92aef3a47957b4011f57526e, so cả legacy và current consumed stores không đổi. Không SAF chọn/upload/prepare firmware lại.
- **Operational evidence cuối:** Permission-only observer mới ĐÃ CẤP/open 0; raw đọc đúng một lần sau approval, report 00:32:55.837 +07 OPERATIONAL_CANDIDATE/READ_AND_CLOSED, complete 50 byte/no issues/hash 22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5, config 1/interface 0/alt 0/0x82+0x81 IN bulk 512. Open/read/close 1/1/1, claim/release/transfer 0/0/0, 29 ms, pending/cleanup false, streaming_ready false. Actual binary private export 50 byte/hash/length khớp report và observation trước, không reconstructed. UI đã đóng, không lease/helper/capture chạy nền.
- **Actual timings, N=132:** ACK p50/p95/p99/max 738/1001/1046/1067 ms; request→native 759,066/1022,626/1066,429/1089,356 ms. Native API riêng 0,304/0,466/0,947/6,155 ms, tổng 48,664 ms. Guard accepted→native p50/p95/p99/max 12,136/17,406/26,315/36,624 ms; transaction duration 106486 ms gồm orchestration/audit/transition/cleanup và observation stop, không USB-only. Audit final 50659 bytes/794 events, 132 intents+132 results+1 transition+1 stop; số write/sync critical checkpoints 266 là suy từ source, không actual syscall trace. Không tạo timestamp/cause thiếu.
- **Static và records cuối:** 127 TBS classes major 52, minSdk 21/targetSdk 37; debug có uploader/raw port, release không có; harness vắng cả debug/release, firmware entries 0, zipalign cả hai exit 0, lint 0 errors/10 warnings. Không đổi toolchain. Sáu hồ sơ thêm ngày mới/ADR-023, giữ FAIL/consumed/ledger terminal cũ. HEAD 92a25b4, stage trống, không commit/push hoặc Phase 3.0.
- **Kết luận:** OPERATIONAL_CANDIDATE; Phase 2.9 đạt gate firmware bring-up theo 132 native full-length và operational raw đã quan sát an toàn. Không READY/I²C/frontend/tune/PLP/DVB lock/TS/EEPROM/flash PASS, không new GUID/permit/helper hoặc vendor init khác. Bước kế tiếp chỉ review diff/evidence; commit/push hoặc công việc tiếp theo cần yêu cầu riêng, không tự chạy.

## 2026-11-06 — Commit và push Phase 2.9 theo yêu cầu riêng

- **Phạm vi được yêu cầu:** Commit toàn bộ changes hiện có và push lên origin của nhánh hiện tại, không tạo nhánh hoặc tác động phần cứng.
- **Preflight thực:** `chore/modernize-android-build`, HEAD và `git ls-remote origin refs/heads/chore/modernize-android-build` cùng `92a25b4962ff487c169e67649f454e277bcc60e6`; stage trống, một worktree, 30 file thay đổi gồm 24 file nguồn/test/tools và sáu hồ sơ.
- **Bằng chứng giữ nguyên:** So SHA-256 của toàn bộ 24 file nguồn/test/tools với fingerprint kiểm cuối: không đổi. `git diff --check` PASS; rà candidate additions không thấy credential/private IP/local identity patterns, danh sách chỉ Java/PowerShell/Markdown, không firmware/APK/raw descriptor/capture/log/permit/ledger thật. Kiểm mẫu có phạm vi, không tuyên bố thay thế audit bảo mật toàn repo.
- **Thao tác Git được chuẩn bị:** Stage đúng 30 file đã kiểm, commit message `feat: complete guarded Phase 2.9 TBS5530 firmware bring-up`, kèm Co-authored-by trailer, rồi push không force tới đúng nhánh origin. Xác nhận commit/push và working tree sau thao tác bằng Git, không ghi thành công trước khi có kết quả.
- **Không chạy lại:** Build/JVM/instrumentation hoặc Android/USB. Các kết quả 259 JVM/86 instrumentation có phạm vi, 132 native full-length và raw OPERATIONAL_CANDIDATE là bằng chứng đã kiểm trong phiên trước tác vụ Git; không gán thành test mới. Không đổi toolchain, references, consumed stores hoặc host ledger, không chuyển Phase 3.0.
- **Line endings khi stage:** Lần đầu staged whitespace check FAIL vì bảy file Java mới được lưu CRLF; `git ls-files --eol` xác nhận. Chỉ chuẩn hóa đúng bảy file mới sang UTF-8/LF, kiểm lại text bằng so sánh nguyên văn sau thay CRLF→LF, không thay nội dung logic hoặc Git config. Stage lại và `git diff --cached --check` PASS; không gọi đây là một lần chạy tests mới.

## 2026-11-06 — Phase 3.0 tích hợp: phần mềm, firmware, bridge ID và frontend init; K36 chờ PLP

### Baseline, phạm vi và công cụ

- Đọc attachment Phase 3.0, instructions liên quan, sáu hồ sơ và ADR-018–023; phân biệt terminal/upload/observation lịch sử. Git thực: nhánh `chore/modernize-android-build`, HEAD `722c4916a83074e8cc5b5c6aeeaee9d2f1190785`, worktree/stage sạch lúc bắt đầu. Origin đúng fork, upstream riêng; không checkout/reset/fetch/sync/stage/commit/push/PR. Ba references chỉ đọc, SHA official/C/Go giữ nguyên như [RESEARCH.md](RESEARCH.md).
- Load tbs-research/project-handoff/android-cli; recall lịch sử qua Gateway, dùng source/tests làm thẩm quyền. Hai tác vụ độc lập triển khai frontend và parser, parent nối operational ownership/approvals/Bulk/UI, không tạo driver/session song song để né lifecycle. Không thay global customization hoặc MCP config.
- Audit toolchain lần đầu FAIL ExecutionPolicy (script chưa ký); user cho phép Bypass chỉ trong tiến trình kiểm kê, chạy lại `Inspect-TBS5530-Toolchain.ps1 -NoReport` PASS. Declared/installed không đổi: Gradle 9.8.2/AGP 9.5.0/JDK 25.0.5.0.3/SDK 37.2/Build-Tools 37.0.0/NDK 30.0.16248370/CMake 4.1.2/minSdk 21/target 37/Java 8. Không cài hoặc dọn cache/gói, không thay User/Machine policy.
- Task VS Code assembleDebug không được host tìm thấy; fallback wrapper trực tiếp, không thêm task. Editor Java classpath diagnostic vẫn có, không dùng nó thay Gradle compile. GitNexus stale/incomplete được `analyze --index-only` làm mới; refresh sau API changes thành công, 6715 nodes/20257 edges/587 flows, không sinh instructions. Impact detachEvent có 2 upstream symbols/risk LOW trong module Tbs5530; graph có flow truncation và resource XML drift, không coi caller vắng là safety proof.

### Mã đã triển khai và lỗi được sửa

- [OperationalProfile](../../drivers/src/debug/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530OperationalProfile.java), [OperationalHandoff](../../drivers/src/debug/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530OperationalHandoff.java): fresh raw classification, stricter endpoint/identity/Android metadata validation trước claim(false), đúng một tuner, không bootstrap/A0 trên operational owner. Dùng Binding/Coordinator/Session/Runtime hiện hữu, default/unknown fail-closed.
- [UsbBinding](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530UsbBinding.java): detach invalidate ngay thay vì chờ enumeration mất path. Test tái hiện trường hợp OS đã dùng lại mọi identity field, stale control capability bị chặn; physical cleanup vẫn chờ active transaction trả. Không force-close native call hoặc đổi configuration/alternate.
- [Frontend](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530Frontend.java), [Sequences](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530Cxd2878Sequences.java), [TuneParameters](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530DvbT2TuneParameters.java): source-grounded selective CXD6802 0x197/ASCOT3I E-family init/tune/status, immutable register programs/transcript/SHA/count/bound, dynamic RMW/calibration formula, finite delays/polling, unsupported variants/auto PLP và metrics không giả thành success.
- [ControlProbe](../../drivers/src/debug/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530ControlProbe.java) so từng USB request với exact approved program, consume trước OUT và audit INTENT/RETURN; chỉ ID hợp lệ mới session.ready, init hoàn tất mới frontendInitialized. [OperationalApproval](../../app/src/debug/java/info/martinmarinov/dvbdriver/Tbs5530OperationalApproval.java) có namespace riêng, scope/hash/generation/expiry/heartbeat/foreground/consumed/RF checks; không sửa firmware ledger/store.
- [BulkTransport](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530BulkTransport.java), [TsSmoke](../../drivers/src/debug/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530TsSmoke.java), [TsParser](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530TsParser.java): IN 0x82 cùng owner, lock/RF/approval trước stream select, byte/read/time bounds, RAM-only; parser 3-packet sync/188-byte carry/continuity/adaptation/TEI/null/duplicate. Short Bulk khác EP0 short-transfer; negative/no-data không tự gán timeout hoặc frontend failure.
- [OperationalDiagnosticActivity](../../app/src/debug/java/info/martinmarinov/dvbdriver/Tbs5530OperationalDiagnosticActivity.java) chỉ debug, mở chủ động, không auto-acquire từ intent. Reservation loại trừ màn hình firmware/raw hiện có; UI chuẩn bị plan không ghi USB, cần permit và xác nhận riêng. Tune nhập Hz/BW/PLP rõ, RF IN checkbox không thay write approval; TS scope riêng. Source manifest/resource chỉ thêm ở debug, release không có Activity này.
- Sau pre-native reserve stop thật, sửa eligibility nút upload bằng `hasStartBudget()`, thêm deterministic test biên 60000/60001 ms. Không nới 1500/2000/2000/300000/240000 ms, không tạo hoặc rearm GUID từ UI. Lint warning mới ở RF checkbox được đưa sang debug resource; lần lint kế FAIL thiếu bản dịch nb, sửa `translatable=false` cho nhãn vận hành này rồi rerun PASS, không suppress toàn dự án.

### Kiểm thử đã chạy

Mọi Gradle command dưới đây chạy Windows PowerShell tại root fork, JDK/SDK đã kiểm; không dùng Java launch debugger cho Android build. Init script cho namespace test được tạo ngoài Git và dọn sau khi xong.

- Focused JUnit trực tiếp: `javac --release 8` và `org.junit.runner.JUnitCore` cho UsbBinding/Bulk/Profile/Fingerprint/Parser/TsSmoke: 89 methods PASS, 1,518 giây, USB thật 0. Parser riêng 38 tests PASS/Java 8; frontend riêng `:drivers:testDebugUnitTest --tests '*Tbs5530FrontendTest'` có 26 PASS. Focused integration `--tests '*Tbs5530ControlProbeTest' --tests '*Tbs5530OperationalProfileTest' --tests '*Tbs5530TsSmokeTest'` có 15 PASS.
- Full: `.\gradlew.bat :drivers:testDebugUnitTest :usbxfer:testDebugUnitTest :dvbservice:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :drivers:assembleDebugAndroidTest --console plain --no-daemon` PASS trên mã tích hợp. XML cuối: drivers 329 +usbxfer 1 +dvbservice 13 =343, 0 failures/errors/skipped; app unit NO-SOURCE. Không đếm repeated runs thành methods mới.
- Sau sửa debug resource, chạy lại `:app:assembleDebug :app:assembleRelease :app:lintDebug :drivers:testDebugUnitTest :usbxfer:testDebugUnitTest :dvbservice:testDebugUnitTest`, PASS 1 phút 29 giây; lint 0 errors/10 warnings cũ. Build init-script namespace cách ly `:app:assembleDebug :app:assembleDebugAndroidTest :drivers:assembleDebugAndroidTest` PASS 1 phút 16 giây. Build focused đầu phiên FAIL do file frontend đang được viết chưa có Sequences; không dùng lượt đó làm gate.
- AAPT kiểm package/targetPackage trước cài: app `info.martinmarinov.dvbdriver.phase30offline`, app test `.phase30offline.test`, library test `info.martinmarinov.drivers.phase30offline.test` tự target. Chỉ install các APK cách ly để chạy fake tests, không clear/uninstall production. ADB dùng target TCP đã pin model/API/boot, không failover alias.
- Instrumentation cuối: `am instrument -w -r -e class ...Tbs5530ApprovalDeterministicTest,...Tbs5530OperationalApprovalTest <isolated-app-test>/androidx.test.runner.AndroidJUnitRunner`: 32 methods PASS/4,878 giây. `...Tbs5530OperationalHandoffTest,...Tbs5530AndroidUsbFacadeTest <isolated-library-test>/androidx.test.runner.AndroidJUnitRunner`: 32 PASS/1,444 giây. Tổng 64 methods, fake USB/storage; foreground/reservation UI startup có kiểm. Không coi đây là hardware probe.
- Static cuối: 145 TBS main classes major 52, minSdk 21/target 37; operational debug Activity vắng release, APK có 0 firmware/TS/capture entries; `zipalign -c -P 16 -v 4` cả debug/release PASS. Debug SHA 5965…7a36, release unsigned SHA 4b66…fa7. API 21/16 KiB runtime/native smoke mới NOT RUN vì không có thay native/toolchain và OnePlus/API 30 không chứng minh các môi trường đó.

### Phê duyệt, hardware và bằng chứng mới

- Người dùng nói tuner đã bị rút sau Phase 2.9. Chỉ sau approval descriptor-only và xác nhận OTG/current mới mở diagnostic nội bộ bằng Activity; đây không phải ứng dụng riêng. Đã đính chính khi user không thấy icon riêng. Không yêu cầu anten lúc khởi động; user tự grant USB permission. APK Phase 2.9 đang cài đúng SHA 12a9…526e, không update trước cold descriptor.
- Cold raw: 189 byte/COMPLETE/hash e288…52de/FX2_DEFAULT/MATCH, 24 ms, open/read/close 1/1/1, claim/release/vendor 0/0/0, cleanup/pending false. Không suy operational từ descriptor cũ.
- Approval firmware đầu Phase 3.0: user duyệt một GUID mới và tự chọn SAF/bấm upload. Helper dùng code đã kiểm và canonical ledger. Sau khoảng 73,7 giây từ prepare tới start, reserve không đủ 240000 ms; terminal FAULTED/PERMIT_EXPIRED/PRE_NATIVE_STOP, 0 attempted/guard/native/image/payload, runner 49 ms; raw preflight 189 byte, after NOT_OBSERVED, cleanup NONE/pending false. Ledger 275→278 records; GUID đã consumed host dù app chưa consume/native, không reuse hoặc xóa history. Terminal đã export riêng ngoài Git.
- User duyệt độc lập transaction khác sau giải thích, gồm update APK giữ data/helper tiến trình riêng. Production update-r mới so hash các consumed XML cũ trước/sau giống hệt. Host chờ selection/challenge mới trước khi cấp GUID; user chọn file và bấm ngay. Không đổi timing/hash/dual-reset plan hoặc auto retry.
- Transaction mới thứ hai: 132/132 A0 full-length, image 8192 byte, payload 8196 byte, hai release đều 1/1, guard/native counters đều 132. Duration 108499 ms; attachment event 1, auto-observation dừng JAVA_IO_EXCEPTION trước quyền/lease mới, after NOT_OBSERVED. Terminal UNCERTAIN/UPLOAD_COMPLETE_OPERATIONAL_UNVERIFIED giữ nguyên; không nạp lại operational. Native-only có N=132, p50/p95/p99/max là 0,293229/0,446302/1,076823/1,524583 ms, tổng 42,521095 ms. ACK đúng 132 event android_observed: 783/1012/1064/1098 ms, dưới deadline 1500 ms; không gọi thời gian ADB/host là USB latency.
- A sau upload được duyệt riêng: user tự OK permission mới, một raw read 50 byte/COMPLETE, hash 22e2…add5, candidate operational. Config 1/interface 0/alt 0 có 0x82+0x81 IN bulk 512, bInterval tương ứng 1 và 0. Duration 26 ms, open/read/close 1/1/1, claim/release/vendor đều 0, pending/cleanup false. Không sửa terminal upload theo observation riêng.
- C được duyệt đúng whitelist: strict fresh owner/claim(false), probe slave 0x66, bank 00, registers FB/FD, plan fc68…a0d9. Đúng 3 OUT/2 IN full-length, chip-ID 0x197, DEMOD_IDENTIFIED; audit 10 events, khoảng 76 ms có audit/checks. Không init/tune trong approval C.
- D được duyệt riêng chỉ INIT: targets 0x66/0x64/0x60, plan 6179…82da, 51 writes +14 write/read =65 OUT/14 IN full-length; tuner E-family/boot/TS-mode checks đạt. Audit có 158 events, INTENT đầu → RETURN cuối là 1418 ms; source delay 84 ms đã nằm trong tổng. State FRONTEND_INITIALIZED/READY_FOR_TUNE, UI dừng AWAITING_RF_IN_CONNECTION. Không 0x8A/Bulk/tune/lock polling.
- Ledger firmware cuối có 545 records/182748 byte; prefix 92018 và 93760 byte có hash khớp trước append. C/D có consumed/audit riêng, không sửa ledger firmware. Tổng vendor mới OUT/IN là 200/16: A0 có 132 OUT, bridge 0x80 có 52 OUT, pointer 0x90 có 16 OUT, 0x91 có 16 IN. Không EEPROM/MAC/LNB, driver/host swap, usbipd/WSL, power-cycle, capture hoặc firmware khác. Count operational release/close native không được đo riêng; không điền fake count thành hardware evidence.

### RF IN, K36 và điểm dừng PLP

Sau init mới hỏi đúng checkpoint vật lý. User xác nhận coax thụ động an toàn, không DC ngoài, cắm đúng RF IN không LNB IN, anten từng thu tốt. Sau đó đưa bộ ứng viên SDTV trạm Vũng Chua/K36, 594000000 Hz/8000000 Hz, PLP chưa xác minh và yêu cầu ưu tiên auto/no-filter chỉ nếu source thực sự hỗ trợ.

Đã đọc SDTV article bằng browser body text: nội dung ngày 11/04/2019 xác nhận Vũng Chua/K36; bản đồ hiện có liệt kê Bình Định/K36. Metadata Công báo TT 27/2026 xác nhận quy hoạch 470–694 MHz, không xác nhận on-air/PLP; tải DOCX bằng browser timeout nên không khẳng định đã đọc phụ lục. Không quét tần số khác hoặc mặc định nhà phát sóng đang hoạt động.

Official `cxd2878_set_dvbt2:2468–2475`, C `3311–3318`, Go `sony/terrestrial.go:144–162` chỉ hiện thực manual PLP tại bank 23, AF=id và AD=1. Sentinel `NO_STREAM_ID_FILTER` khi bị `& 0xFF` sẽ thành 255, không phải bỏ lọc; Android giữ từ chối AUTO_PLP, không tự mặc định PLP 0 hoặc ghi AD=0 theo chip Sony khác/ATSC3. Kết quả cuối **TUNE_NOT_RUN_ENVIRONMENT_BLOCKER do PLP hoặc cơ chế chọn PLP chưa xác minh**, không phải chưa cắm anten, NO_SIGNAL/NO_LOCK hay frontend failure. Tune/status/stream/Bulk đều 0.

### Cleanup và bàn giao

- Đã export riêng terminal trước native, terminal upload, operational observation và C/D audit ra session files ngoài Git; không artifact hóa raw/audit/GUID/ledger thật. Tệp whitelist init không chứa firmware/định danh đã được xuất riêng để user xem lại. Không ghi đè báo cáo terminal cũ bằng kết quả follow-up.
- Đóng Activity operational; dumpsys activity cho thấy resumed launcher. Các helper firmware/probe/init bounded đều kết thúc, không force-close native đang chạy hoặc giữ owner/màn hình sáng trong lúc chờ PLP. Uninstall đúng ba package test Phase 3.0 mới tạo đều Success, giữ production và `drivers.test` có trước. Dọn classes/init script/DOCX scratch riêng; giữ mọi evidence private.
- Không tối ưu khi chưa có bằng chứng bottleneck: NO OPTIMIZATION WARRANTED, không dùng khác biệt latency làm speedup. Firmware ACK 1500/heartbeat 2000/USB 2000/TTL 300000/reserve 240000 ms giữ nguyên. Native/caller telemetry và mock/live đã phân biệt ở [TEST-MATRIX.md](TEST-MATRIX.md), source/PLP ở [RESEARCH.md](RESEARCH.md), quyết định ở ADR-024.
- Sáu hồ sơ được cập nhật append-only; HEAD không đổi, không stage/commit/push/PR hoặc mở phase mới. Bước tiếp trong cùng Phase 3.0 là lấy PLP K36 từ nhà phát sóng/thiết bị đo hoặc tài liệu Sony đúng chip; sau đó kiểm offline và xin một tune approval riêng, với attachment/permission/owner mới được xác minh. Không coi init/epoch đã đóng là quyền hiện hành.

## 2026-11-06 17:57 +07 — Đọc bốn ảnh Thông tư để xác nhận tần số K36

- Người dùng cung cấp ảnh read-only của các trang Công báo số 333 ngày 16/06/2026, Thông tư 27/2026/TT-BKHCN. Đọc trực tiếp bảng Điều 3 khoản 1: K36 có giới hạn 590–598 MHz, trung tâm 594 MHz; độ rộng kênh 8 MHz. Chốt ứng viên frequency `594000000 Hz` và bandwidth `8000000 Hz` bằng bằng chứng bảng, không chỉ từ ánh xạ kênh suy ra.
- Điều 2 khoản 7 có Gia Lai trong nhóm Nam Trung Bộ và Tây Nguyên; Điều 3 khoản 2 điểm d quy hoạch K36/K37 cho khu vực đó. Điều 4 khoản 1 có hiệu lực 16/07/2026, khớp metadata web. Không coi quy hoạch là xác nhận trạm SDTV đang phát tại lúc thử, không mở rộng sang K37.
- Bốn ảnh không nêu PLP hoặc auto/no-filter. Giữ blocker PLP/cơ chế chọn PLP đúng chip; không tự chọn 0/255, không có USB/control/tune/status/Bulk mới. Owner đã đóng vẫn không có quyền sống để dùng lại. Không sửa, chép hoặc commit ảnh; lần tải DOCX timeout trước đây vẫn giữ nguyên lịch sử.
- Bổ sung sáu hồ sơ, không đổi source hoặc toolchain. Không chạy lại build/tests cho tài liệu; `git diff --check` và rà chính đoạn mới để kiểm khoảng cách tiếng Việt/Markdown trước bàn giao. Gate cao nhất vẫn FRONTEND_INITIALIZED/READY_FOR_TUNE, không phải LOCKED/TS_SMOKE_PASS.

## 2026-11-06 18:22 +07 — Commit và push Phase 3.0 theo yêu cầu riêng

- **Yêu cầu:** Commit toàn bộ changes đã thực hiện trong Phase 3.0 và push lên origin của nhánh hiện tại; không PR, nhánh mới hoặc thao tác tuner.
- **Preflight:** Nhánh `chore/modernize-android-build`, HEAD và `git ls-remote origin refs/heads/chore/modernize-android-build` cùng `722c4916a83074e8cc5b5c6aeeaee9d2f1190785`; stage trống, một worktree. Phạm vi 35 file gồm 29 nguồn/test/resource và sáu hồ sơ dự án; không thay ba references.
- **Rà candidate:** `git diff --check` PASS trước stage. Quét additions/file mới có phạm vi không thấy pattern private key, token, private IP hoặc approval GUID thật; không coi đây là audit bảo mật toàn repo. Inventory chỉ Java/XML/Markdown thuộc app/drivers/docs, không firmware/APK/TS/pcap/raw binary/log/permit/ledger. Tệp whitelist init và ảnh Công báo bên ngoài Git không được chép vào commit.
- **Thao tác chuẩn bị:** Stage đúng 35 file đã kiểm, kiểm staged diff/whitespace, commit message `feat: add guarded TBS5530 frontend bring-up` có Co-authored-by trailer, push không force tới đúng nhánh origin rồi đối chiếu SHA remote/HEAD và working tree. Kết quả commit/push chỉ xác nhận từ output Git sau thao tác.
- **Không chạy lại:** Build, JVM, instrumentation, APK install hoặc USB. Kết quả 343 JVM/64 instrumentation/debug/release/lint PASS và firmware 132 full-length/ID 0x197/init đạt là bằng chứng từ phiên triển khai vừa hoàn tất; tune/lock/TS vẫn NOT RUN vì PLP chưa xác minh. Không suy thành hardware PASS mới từ việc commit.
- **Line endings:** Staged whitespace check đầu FAIL do 18 file Java/XML mới lưu CRLF. Chỉ chuẩn hóa đúng 18 file mới sang UTF-8/LF, so nguyên văn sau thay CRLF bằng LF để xác nhận không đổi nội dung; hai file parser/test vốn LF giữ nguyên. Stage lại và `git diff --cached --check` PASS, không đổi Git config hoặc logic. Đây không phải một lượt chạy tests mới.

## 2026-11-06 — Phase 3.1: triển khai PLP, sửa deadline TS và tới checkpoint xin phép

- **Branch/commit trước và sau:** `chore/modernize-android-build`, `9621a8028d2cd3e5d98652cde69ba80d27cd7ee2`. `git status --short`, `rev-parse HEAD`, `branch --show-current`, `remote get-url origin` và `log 9621a802..HEAD` xác nhận baseline đúng và ban đầu sạch. Không stage/commit/push/checkout/reset/rebase/fetch/sync hoặc sửa sibling references. Stage cuối vẫn trống.
- **Phạm vi:** Một Phase 3.1 tích hợp, chưa có approval phần cứng mới. Đọc instructions/skills/hồ sơ hiện hành và ADR Phase 2.9/3.0, source/test thực, giữ kết quả cũ làm lịch sử. Agent research không khởi động được vì model mặc định của agent không khả dụng; không relaunch hoặc dùng kết quả giả, hoàn tất đối chiếu trực tiếp.
- **Nguồn:** Official/C/Go giữ SHA đã khóa. Đọc Linux v6.12 tại SHA `adc218676eef25575469234709c2d87185ca223a`: CXD2841ER, CXD2820R và CXD2880 cùng auto AD=0/không AF, nhưng L1 monitor CXD2880 dùng bank khác. Không tìm được source đủ kiểm cho monitor CXD6802 hoặc PLP SDTV K36 trong kết quả công khai đã khảo sát. Lập bảng nhánh manual/auto/monitor/đo độc lập trước coding trong [RESEARCH.md](RESEARCH.md), không dùng phụ lục làm PLP live.
- **Quy chuẩn:** Trang Bộ xác nhận QCVN 63:2020 thay 2012 và hiệu lực 01/07/2021; HTML 2020 kiểm Single ID tùy ý, Multi/Common và tự dò kênh. Ghi đúng Bảng A.2/A.3/A.4 thuộc mục A.2. VSQI disabled; model không nhận nội dung PDF 2012 native và fetch DOC lịch sử lỗi transport, không giả đã đọc trang ảnh. Không chứng nhận hợp quy từ fixture.
- **Mã sửa:** TuneParameters/Sequences/Frontend/ControlProbe thêm PLP mode/evidence, transcript v2, nhánh debug AUTO giới hạn K36/8 MHz/0x197 và consent riêng; monitor chưa đủ nguồn bị chặn. Frontend giữ sample clock/epoch và chặn sync 7 cấp stream. Approval dùng Phase31/protocol 2, boot/USB owner hash, bound được host duyệt và start reserve; không đổi Phase29/30 stores hoặc TTL. UI nhập AUTO/manual rõ, chọn preset K36 bằng nút riêng, không tự scan, pause drain/reconnect và xóa RF consent. TsSmoke tính lại deadline sau audit và từ chối PASS muộn.
- **Tests/resources sửa:** Golden K36 độc lập với fixture 666 MHz cũ; thêm QCVN synthetic, biên mode/hash/status, 99 short boundaries AUTO và fake full path. App test-only PlanFixture/UiTest không có trong production DEX; fake manager/coordinator/library instrumentation đầy đủ. Test bootstrap/durability chỉ mở thêm đúng namespace Phase31 cách ly, không đổi firmware runtime/ledger. Nhãn/số preset debug resources không dịch, tránh ba lint warnings mới.

### Lệnh và bằng chứng phần mềm thực chạy

Tất cả lệnh Gradle chạy Windows PowerShell tại root fork, child gán `$env:JAVA_HOME=[Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')` và `$env:ANDROID_HOME='D:\Android\Sdk'`. Không thay environment toàn máy. Log chi tiết được giữ ngoài Git trong `files` của session Copilot bắt đầu 2026-11-06 19:17 +07; các tên log và fingerprint dưới đây đủ để tìm đúng evidence trong phiên.

- Preflight nguồn: `git -C <reference> rev-parse HEAD`, view/grep source theo SHA, GitHub commit lookup v6.12, web fetch/search công khai; không gửi code private/firmware/log thật lên web. `gitnexus --help`, `status --help`, `status`: bản 1.6.12 chỉ dừng ở banner, đã stop đúng shell/process tree sau chờ; không có graph hiện hành để dùng, không reindex/repair FTS.
- Toolchain: `android --help`, `android --sdk=D:\Android\Sdk sdk list --help`, đọc build/wrapper/source.properties, JDK `-version`, ADB `version`, AVD registrations. `Inspect-TBS5530-Toolchain.ps1 -NoReport` FAIL ExecutionPolicy, không Bypass; không package install/update/cleanup. runTask assembleDebug không tìm task, dùng wrapper thay thế, không tạo task.
- Tái hiện status: `.\gradlew.bat :drivers:testDebugUnitTest --tests '*Tbs5530FrontendTest.reservedSyncSevenCannotAuthorizeTsEvenWithTsBitSet' --console plain --no-daemon` FAIL 1/1 trước sửa; `phase31-before.log` giữ lỗi. Focused PLP lần đầu 40 tests/5 failures do fixture và kỳ vọng cleanup; sửa tests rồi 40 PASS, chỉ test deadline TS mới FAIL trong `phase31-focused2.log`.
- Tái hiện TS muộn: `.\gradlew.bat :drivers:testDebugUnitTest --tests '*Tbs5530TsSmokeTest' --console plain --no-daemon` FAIL 1/8 trước fix publish duration (`phase31-ts-late-before.log`); sau sửa cả hai lỗi deadline, full suite PASS. Không đổi register hoặc timeout hardware để làm xanh test.
- Regression cuối: `.\gradlew.bat :drivers:testDebugUnitTest :usbxfer:testDebugUnitTest :dvbservice:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console plain --no-daemon` PASS 2 m 11 s, `phase31-final-regression.log`. XML: drivers 341 + usbxfer 1 + service 13 = 355, 0 failure/error/skipped, app unit NO-SOURCE. Lint cuối 0 errors/10 warnings cũ; trung gian 13 warnings được sửa đúng ba nhãn mới, không suppress toàn repo.
- Build cách ly: wrapper `--init-script <session-files>\phase31-offline.gradle :app:assembleDebug :app:assembleDebugAndroidTest :drivers:assembleDebugAndroidTest --console plain --no-daemon` PASS 1 m 17 s, `phase31-final-isolated-build.log`. Init đổi build dir/package sang Phase31, library test tự target; AAPT kiểm package/targetPackage trước mọi install. Script tạm đã dọn sau test, không chỉnh build declarations.
- ADB chỉ dùng SDK executable và đúng TCP target được pin riêng sau model/API; hai endpoint online cùng GM1900/API 30, không fallback mDNS. Cài ba package cách ly mới bằng `install -r`, không production update. App selector: `Tbs5530BootstrapApprovalTest,Tbs5530OperationalApprovalTest,Tbs5530Phase31UiTest,Tbs5530UsbDiagnosticTest,Tbs5530BootstrapReportTest,Tbs5530DurabilityFaultTest`, tên fully qualified theo source; runner `am instrument -w -r -e class <selectors> info.martinmarinov.dvbdriver.phase31offline.test/androidx.test.runner.AndroidJUnitRunner` PASS 61 tests/11,247 s. `phase31-final-app-instrumentation.log` giữ kết quả.
- Library runner: `am instrument -w -r info.martinmarinov.drivers.phase31offline.test/androidx.test.runner.AndroidJUnitRunner` PASS 43 tests/1,675 s, `phase31-final-drivers-instrumentation.log`. Mọi manager/raw/control/approval trong các tests đều fake; Android UI/receiver/worker thật không biến thành kiểm tuner thật. App lần trước FAIL 3/61 vì còn assert package Phase29 ở nhánh durability; sửa test guard cho đúng hai namespace rồi rerun toàn bộ selector PASS. Host full-path/audit-cost benchmark riêng NOT RUN.
- Static cuối: đọc header của 148 TBS main classes đều major 52; AAPT debug/release minSdk 21/target 37; đọc các DEX trong APK debug 5/release 2 không có Phase31 test harness, debug operational Activity không có trong release. Firmware/TS/capture entries 0, `zipalign -c -P 16 4 <apk>` PASS cả debug/release. Source fingerprint manifest ngoài Git SHA-256 `078edc85048dfc147d268a8b802924dbe296856abe62890ad86abbba2e38d637`; APK hashes ở [ENVIRONMENT.md](ENVIRONMENT.md). Native API 21/16 KiB runtime mới NOT RUN.

### Quyền, cleanup và bước kế tiếp

- Chỉ thực hiện nghiên cứu, sửa nguồn/docs, build và instrumentation fake-only theo yêu cầu. Không mới firmware/A0, USB vendor/I²C, chip-ID/init thật, tune/poll/0x8A/Bulk, LNB/DC, usbipd/WSL/BDA, reset/power-cycle hoặc capture. Không tái dùng owner/approval/RF/init Phase 3.0 và không suy listing ADB là tuner có nguồn.
- Cả ba package Phase31 đều chưa tồn tại trước phiên, được ghi inventory riêng rồi uninstall đúng ba package sau PASS, mỗi lệnh Success. Giữ production và mọi package có trước. Đã kiểm Activity test không còn resumed, không launch/giữ Activity operational production, không có helper hardware chạy. Chỉ dọn ba bản source Linux tải về và init Gradle tạm trong session, giữ log/fingerprint làm evidence ngoài Git.
- Lưu checkpoint AgentMemory với canonical ID suy từ origin đã kiểm `git:github.com/trankien84/androiddvbdriver`, author `copilot-agent-host`; save thành công và recall cùng project trả đúng record/author. Không chép nguyên raw audit/identity/permit/TS lên memory. Hồ sơ dự án và source/tests vẫn là thẩm quyền.
- Gate hiện tại `SOFTWARE_READY_AWAITING_EXPLICIT_HARDWARE_APPROVAL`; host provisioning protocol 2 thật NOT RUN và phải kiểm khớp trước USB ghi, không dùng helper firmware để cấp control. Các chương trình đề nghị, counts, bounds và rủi ro AUTO ở [RESEARCH.md](RESEARCH.md), ADR-025 ở [DECISIONS.md](DECISIONS.md). Bước kế tiếp duy nhất là hỏi approval minh bạch; nếu không được duyệt, giữ nguyên NOT RUN chứ không gọi phase lock/TS hoàn tất.

## 2026-11-06–07 — Phase 3.1: năm lượt AUTO lock, TS chưa nhận được dữ liệu và errno 75 đã đo

- **Branch/commit trước và sau:** `chore/modernize-android-build`, `9621a8028d2cd3e5d98652cde69ba80d27cd7ee2`, đúng origin fork. Không stage/commit/push, checkout/reset/rebase/fetch hoặc sửa references. Các dòng NOT RUN trong checkpoint trước giữ nguyên ý nghĩa lịch sử, không phải trạng thái cuối của phiên này.
- **Mục tiêu thực hiện:** Tiếp tục cùng Phase 3.1 từ software checkpoint tới lock và TS có approval, sửa lỗi thật trong phạm vi rồi kiểm lại; không tạo phase con hoặc đoán PLP ID. Source fingerprint cuối `988919f6fe1c8cc070c4488c86caf50dd77cf652397221f96eab6502fb674270`; manifest nguồn chỉ ở session files, không Git.
- **Quyền được cấp qua các checkpoint:** Workflow probe/init/AUTO/status/TS bounded và AUTO thử nghiệm riêng, xác nhận RF IN/OTG hiện tại; firmware một upload riêng khi raw là default; pin lại TCP sau user khôi phục; PowerShell Bypass chỉ tiến trình helper được kiểm; các workflow tiếp có approval mới, gồm một clear riêng ở lượt thứ tư và một clear riêng ở lượt v4. Không gộp firmware permit với control, không reuse GUID/generation/consumed state hoặc đổi ledger để chạy lại.

### Firmware mới và host provisioning

Sau khi người dùng cấp permission, raw default mới có 189 byte, open/read/close 1/1/1, 24 ms, không claim/vendor. Image local 8192 byte đúng SHA `b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc`. Helper Phase29 không sửa thực hiện đúng một upload TBS_SOURCE_DUAL_RESET: 132/132 A0 full-length, image 8192/payload 8196 byte, runner 111344 ms gồm điều phối/approval/audit. Terminal auto-observation giữ `UNCERTAIN/JAVA_IO_EXCEPTION/NOT_OBSERVED`; không retry hoặc sửa thành PASS. Permission mới sau chuyển descriptor và observation độc lập đạt operational 50 byte/hash `22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5`, 14 ms, claim/vendor 0. Không upload operational nữa. Firmware ledger từ 545 records/182748 byte thành 812 records/271744 byte, prefix cũ được giữ nguyên.

Host PROBE đầu consumed nhưng dừng trước Android execute vì tệp tạm `.json.new` xung đột AtomicFile reader. Giữ host consumed/stop, không có PROBE/I²C của attempt này. Tái hiện collision bằng instrumentation và sửa helper sang `.tmp`; user duyệt PROBE mới rồi mới chạy. Host dùng shell UID clock/boot, app-private I/O qua run-as, timestamps đọc lại sau I/O; không clamp clock hoặc tăng TTL. Kiểm 26 scope/budget cases offline và channel roundtrip trong package cách ly trước live; host fsync consumed trước permit, app durable consume trước USB.

### Các workflow phần cứng thực đã chạy

| Workflow | Chương trình thành công | TS/ADB terminal | Delta checked OUT/IN |
| --- | --- | --- | --- |
| Thứ nhất | Owner mới, PROBE ID 0x197, INIT, AUTO K36/8 MHz, mẫu status đầu lock | TCP mất trước TS; không TS permit consumed, không 0x8A/Bulk | 154/32 |
| Thứ hai sau khi người dùng khôi phục TCP | Probe/init/AUTO/status mới lại lock | Một 0x8A, Java 16384 byte read=-1, 0 byte; smoke 46530260 ns | 155/32 |
| Thứ ba, chunk 4096 đã kiểm lại | Probe/init/AUTO/status mới lock | Một 0x8A, Java 4096 byte read=-1, 0 byte; timeout 500 ms, managed read 5218021 ns, smoke 31229531 ns | 155/32 |
| Thứ tư, clear có quyền riêng | Probe/init/AUTO/status mới lock; một CLEAR_HALT 0x82 hoàn tất | Một 0x8A, Java 4096 byte read=-1, 0 byte; smoke 50912135 ns | 156/32 |
| Thứ năm, native errno v4 có quyền mới | Probe/init/AUTO/status mới lock; một CLEAR_HALT 0x82 hoàn tất | Một 0x8A, native 4096 byte read=-1/errno=75, 0 byte; timeout 500 ms, managed read 13419167 ns, smoke 62119843 ns | 156/32 |

Mỗi status đều `syncState=6`, `tsLock=true`, `unlockDetected=false` ngay mẫu đầu, nên dừng polling sau 2 OUT/1 IN thay vì giới hạn tối đa 6/3. Mỗi owner dùng probe 3/2, init 65/14 và AUTO 84/15, không AF/id giả. UI counters ở workflow thứ hai là tích lũy 309/64; dùng chênh lệch 155/32 chứ không cộng cả 309 lần nữa. Tổng năm workflows là 776 OUT/160 IN; cộng firmware 132 OUT là 908 OUT/160 IN, không tính descriptor reads, host-only failures hoặc Bulk data như register IN. Có bốn stream-select, hai clear và bốn Bulk calls; tổng 0 byte/packet. Không suy TS PASS từ parser counters bằng 0 hoặc report `failure=NONE`.

### Sửa và kiểm tiếp trong cùng phase

- Sửa report TS dùng stale lastPlanHash của status: fake UI tái hiện ComparisonFailure rồi PASS sau cập nhật prepare TS. Native scope cũ được đối chiếu bằng CONSUMED audit/hash và OUT delta, không sửa report lịch sử. Thêm telemetry requested length/timeout/managed wall time và explicit chunk 4096 theo official; v2 gate 356 JVM/107 instrumentation PASS. Hardware 4096 vẫn lỗi, không tuyên bố chunk là root cause.
- Đối chiếu official `usb-urb.c:usb_urb_init:220` và Linux `usb_clear_halt`/`proc_clearhalt`, thêm opt-in cố định 0x82 cùng fd/worker/generation. Full fake path bắt được thiếu forwarding trong `Tbs5530UsbBinding`; sửa rồi library PASS. Native fd âm không ioctl; app test compile transitive classpath đã sửa bằng reflection test-only, không thêm dependency. V3 đạt 360 JVM/109 instrumentation, debug/release/lint/static PASS trước approval clear. Hardware clear hoàn tất nhưng TS vẫn lỗi; không xác nhận EPIPE/HALT là nguyên nhân.
- Bổ sung native errno v4: synchronous USBDEVFS_BULK như backend Android/libusbhost, capture errno trước JNI cleanup, giữ count riêng. Giới hạn input/buffer và endpoint 0x82 cố định, không mở fd mới hoặc retry; truyền qua đầy đủ facade/binding/handoff/coordinator đến TsSmoke/report. Tests thêm count=-1/errno=32/no retry/stale owner, full fake forwarding và native fd âm/errno=22/buffer không đổi. Transcript v4 có readApi và hash mới, không dùng approval v3 đã consumed.
- Lượt native v4 đo `errno=75 (EOVERFLOW)`; NDK header xác nhận mã 75, tài liệu Linux USB xác nhận ý nghĩa vượt kích thước gói/buffer. Chưa có trace để quy nguyên nhân về thiết bị/firmware/host/link; không gán NO_SIGNAL, sai PLP hoặc timeout. Read-only sysfs không xác lập được tốc độ bus, không nâng quyền/reset để lấy thông tin.

### Lệnh và kết quả cuối trên source fingerprint v4

Tất cả wrapper commands chạy PowerShell tại root fork, gán JAVA_HOME từ Machine và ANDROID_HOME=`D:\Android\Sdk` trong child. Không dùng generic Java launcher để thay Gradle build.

- `.\gradlew.bat :drivers:testDebugUnitTest :usbxfer:testDebugUnitTest :dvbservice:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console plain --no-daemon`: PASS trong 3 m 45 s, log `phase31-errno-regression.log`; XML xác nhận drivers 347 + usbxfer 1 + service 13 = 361 methods, failure/error 0. `:app:testDebugUnitTest` chạy riêng hoàn tất trong 1 m 4 s nhưng task NO-SOURCE, không gọi đó là app unit tests đã chạy.
- Wrapper với `--init-script <session-files>\phase31-offline.gradle :app:assembleDebug :app:assembleDebugAndroidTest :drivers:assembleDebugAndroidTest --console plain --no-daemon`: PASS 1 m 52 s, `phase31-errno-isolated-build.log`; sau thêm assertion errno end-to-end, build lại `:drivers:assembleDebugAndroidTest` PASS, `phase31-errno-isolated-final.log`. Init/package ngoài Git, không đổi dependency/toolchain.
- AAPT kiểm đúng ba package cách ly trước install. `adb -s <pinned-TCP> shell am instrument -w -r -e class <fully-qualified-six-selectors> info.martinmarinov.dvbdriver.phase31offline.test/androidx.test.runner.AndroidJUnitRunner`: PASS 67/12,351 s, `phase31-errno-app-tests.log`. Sáu selectors giữ như checkpoint trước; không chạy host benchmark/audit-cost Phase29 trong namespace sai.
- `adb -s <pinned-TCP> shell am instrument -w -r info.martinmarinov.drivers.phase31offline.test/androidx.test.runner.AndroidJUnitRunner`: PASS 43/1,589 s, `phase31-errno-library-tests.log`. Tất cả USB/permission/storage của hai bộ instrumentation đều injected fake; native test fd-1 không thể ioctl tuner thật.
- Static APK dùng ZIP/DEX entries, AAPT, `llvm-readelf --dyn-syms`, `llvm-readelf -lW`, `zipalign -c -P 16 4`: PASS debug/release bốn ABI, JNI clear/read, ELF64 alignment 16 KiB, min 21/target 37, không firmware/TS/capture/audit/permit/lớp test trong DEX. Header classes.jar debug 150/release 78 TBS classes đều major 52; lint 0 errors/10 warnings. Log `phase31-errno-static.log`. Một checker trung gian tìm nhầm literal SYNTHETIC_TEST_FIXTURE trong debug constructor, không có lớp harness; đã kiểm nguồn và dùng tên lớp test để phân loại. Một checker trỏ nhầm thư mục classes cũ; kết quả cuối đọc JAR thực, không dùng số 0 làm PASS.
- Host helper `-SelfTest`: PASS 26 cases, ADB/USB 0; `-ChannelCheck -TargetFile <private-target>`: PASS 12 gaps/max 310 ms, isolated UID/clock roundtrip, USB 0. Đây là kiểm protocol, không phải throughput benchmark. User chỉ duyệt Bypass tiến trình helper, không đổi policy máy.
- Sau approval v4, kiểm đúng TCP/model/API/boot, `adb install -r` APK debug giữ dữ liệu; hashes Phase29/30/31 stores và firmware ledger trước/sau install không đổi. Helper bounded `Invoke-Phase31-FreshWorkflow.ps1 -ExplicitFreshWorkflowApproval -PhysicalCurrent -EndpointPreparationApproved -RunLabel fifth-errno` chạy một lần, từng program exact golden/hash và permit mới. TS v4 có clear hash `9687b7a1e306c899bce71e76d89b6bdd766937ebadfcb60c5ed3bfddd94f94b0`; không đổi scope sau consume. Evidence riêng `phase31-fifth-errno-*-result-private.json` và các helper logs, không commit.

### Cleanup và kết luận

- Mỗi smoke cancel/drain owned lease rồi đóng Activity bằng Back, không force close giữa native call hoặc corrective writes. Sau lượt cuối kiểm mCurrentFocus không có Activity operational; mọi helper bounded kết thúc. Không giữ foreground/screen/owner chờ. Phase29/30 stores sau lượt v4 không đổi; firmware ledger vẫn có 812 records/271744 byte, không upload lại.
- Đã uninstall đúng ba package phiên tạo: `info.martinmarinov.dvbdriver.phase31offline.test`, `info.martinmarinov.dvbdriver.phase31offline`, `info.martinmarinov.drivers.phase31offline.test`, mỗi lệnh Success; giữ production và mọi package khác. Dọn scratch golden generator/classes; giữ private init/helper/golden/log/fingerprint để tái lập, không đăng ký raw evidence như artifact.
- Đã lưu kết quả mới về AUTO lock và native EOVERFLOW vào AgentMemory bằng project canonical của fork và author `copilot-agent-host`; save trả metadata đúng, recall theo cùng project đọc lại được nội dung và author. Không lưu định danh thiết bị, permit, raw audit hoặc dữ liệu TS. Nguồn hiện tại/hồ sơ/tests vẫn là căn cứ chính, không dùng Memory thay kết quả thực.
- APK đã chạy v4 debug SHA `9be0e4687de96a2ad2a690b5ca457ec875b916d2aa8128a53a6a18566950ef93`, release unsigned `fa4287d94d6558371b9f5a00fdac99d65d59303f327088396f3bbddc9e7652bf` chỉ build/static. Không API 21/16 KiB runtime mới hoặc paired benchmark; không báo speedup. GitNexus NOT VERIFIED/NOT USED, editor classpath warning còn nhưng wrapper gates PASS.
- Giữ `DVBT2_LOCK_CONFIRMED`; mục tiêu TS chưa đạt, kết luận `PHASE_3_1_BLOCKED_WITH_EVIDENCE` tại USB EOVERFLOW, không phải thiếu PLP. Một hành động thực tế đề nghị là đối chứng chính TBS5530 trên Windows driver/ứng dụng hãng cùng anten K36 / 594 MHz / 8 MHz xem có TS không; NOT RUN, chưa có consent chuyển thiết bị/ownership. Không tạo phase con hoặc tự chạy thêm từ các permits đã consumed. Chi tiết trước/sau ở [TEST-MATRIX.md](TEST-MATRIX.md), errno/nguồn ở [RESEARCH.md](RESEARCH.md), quyết định ở ADR-027.

## 2026-11-07 — Phase 3.1 tiếp nối: thực thi đối chứng Windows, policy phiên và proof-of-guard

### Git, quyền và nguồn

- Git đọc live tại root fork: nhánh `chore/modernize-android-build`, HEAD `1ad3b251f19779450a2c5d9cffc9f37f9c9a5470`, đúng origin. Worktree đầu có `.github/copilot-instructions.md` sửa và instruction TSDuck untracked do người dùng; giữ nguyên phần đó. Không fetch/reset/rebase/checkout/cherry-pick/stage/commit/push. Reference SHAs còn official `2ce787de9f6e81b4294692a45de15596ae2e1322`, C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`, chỉ đọc.
- Đã đọc prompt tiếp nối đầy đủ, hồ sơ và scoped instructions, ADR-024–027; dùng project-handoff, tbs-research, agent-customization và android-cli. Người dùng cấp phạm vi debug hữu hạn, không cần hỏi từng firmware ordinal hoặc routine exact plan; đã thống nhất instruction tương ứng và ADR-028, không bypass guard cũ. USB capture mới có consent riêng; sau `Access is denied`, người dùng duyệt một tiến trình UAC bounded. ExecutionPolicy Bypass chỉ được duyệt cho tiến trình self-test/helper, không đổi User/Machine policy.

### Gate A/B Windows thực thi

- Gate A chạy TSDuck 3.45-4798 và Node 26.7.0, đọc toàn bộ 2 file TS cũ bằng raw parser độc lập; 15 self-test + 410 kiểm chéo PASS. Hash/kích thước 7 file cũ được giữ nguyên. Các lệnh cho mỗi mẫu: `tsanalyze --json`, `--error-analysis`, `--service-analysis --pid-analysis`, `tsbitrate --full`, `--full --ignore-errors`; tách stdout/stderr/exit/time trong manifest. Cả 10 process exit 0 nhưng 2 strict bitrate báo insufficient PCR, không coi exit 0 là đo bitrate thành công.
- Mẫu ngắn có 53.192 packet/TEI = 0/CC = 18; mẫu dài 223.225 packet/TEI = 5101 (2,285138%)/CC = 2738 theo TSDuck, sync = 0; 14 dịch vụ/46 PID không TEI/TSID = 6. Phân biệt 54 cùng CC thành 8 bản sao nguyên byte + 46 nội dung khác; 36/92 summary không phải packet lỗi. Report `D:\TSDuck\reports\gate-a-20261107-124416-97b66f634bda\baseline-gate-a.md`, manifest/parser/checks trong `logs` cùng run.
- `tslsdvb --verbose --extended-info` mới exit 0, BDA index 0 công bố DVB-T/DVB-T2 và lock. Capture thường có watchdog toàn process, đúng K36/8 MHz, thu 44.999.868 byte/239.361 packet, TEI/sync = 0, CC = 41/invalid PES = 1; process 16,406 giây. Capture thứ hai kèm trace cũng TEI/sync = 0, CC = 41/invalid PES = 4. Tất cả TS/report/trace dưới `D:\TSDuck`, không upload hoặc vào Git.
- USBPcap nâng quyền theo consent chỉ lọc TBS5530: hub High-Speed, peak 8 IRP, 5970 submit/completion ghép đủ, 5962 successful actual 8192; requested length NOT OBSERVED, snaplen 128. Không thấy 0x8A trong trace, control cuối trước IN82 `03c80020` chưa xác minh ý nghĩa, không replay. Trace hash và report/addendum ở [RESEARCH.md](RESEARCH.md). Đã kiểm các PID task đều kết thúc; không driver/mode/ownership change. Sau đó người dùng tự chuyển tuner sang OnePlus, xác nhận OTG/nguồn/coax RF IN sẵn sàng.

### Phần mềm và kiểm chứng trước live

- Thêm policy/golden/session namespace `autonomous-v1`, per-attempt consumed mới, firmware attachment ledger mới, Activity CANCEL và helper bounded heartbeat. Thêm một ứng viên nhận queue 8 × 4096 qua UsbRequest chỉ debug/API 26+, cùng connection/worker và cancel/drain hữu hạn; tail bytes cleanup được audit riêng, uncertainty giữ buffer/fd, không force close. Manual/synchronous mặc định giữ nguyên.
- Parser kiểm CRC, PSI qua chunk, PAT/PMT/SDT và liên kết chương trình; TS smoke không PASS từ ba sync packet. Tách quality có lỗi khỏi cấu trúc, thêm raw observer exact chunks, cập nhật synthetic full-path fixtures. Không đổi register hoặc đoán stream setup Windows. Không bundle firmware hoặc raw TS vào APK.
- Windows PowerShell tại root fork, JAVA_HOME lấy Machine và SDK chỉ định trong child. `:drivers:testDebugUnitTest --tests '*Tbs5530QueuedReceiverTest'` PASS 13; targeted parser/TsSmoke/queue và library AndroidTest compile PASS trong 31 giây. Targeted policy + app Java/AndroidTest compile PASS trong 38 giây. Helper `Test-TBS5530-AutonomousSession.ps1` chạy process Bypass đã duyệt: PASS cú pháp/negative preflight, ADB/USB = 0.
- Full wrapper `:drivers:testDebugUnitTest :usbxfer:testDebugUnitTest :dvbservice:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console plain --no-daemon` PASS trong 2 phút 05 giây; XML 399 + 1 + 13 = 413 methods, 0 failures/errors, app unit NO-SOURCE. Lint 0 errors/13 warnings, không suppress. Static APK bốn ABI/ELF64 16 KiB/zipalign/Java major 52/debug isolation PASS ở checkpoint đầu; final rebuild còn theo sau.
- Build instrumentation bằng init script ngoài Git PASS trong 1 phút 48 giây. Ba package `.phase31autooffline` được xác minh trống và AAPT kiểm trước install; library 43 PASS / 1,606 giây. App 90 chạy, 6 FAIL: 5 hợp đồng namespace cũ và 1 session-backed approval eligibility. Không che failure hoặc nới guard. Đã uninstall đúng ba package phiên tạo, xác minh `.phase31offline` trống; sửa harness riêng và guard trước khi live. Không clear/uninstall production hoặc thay consumed lịch sử.
- TCP pin cũ biến mất sau chuyển OTG, trước mọi grant/USB write. Đọc live devices, pin rõ TCP mới sau kiểm GM1900/API 30/cùng boot; không connect/reconnect hoặc fallback giữa giao dịch. Firmware sẵn trong Download được đọc private, 8192 byte/SHA khóa, không download nguồn khác. Read-only sysfs Android không xác lập được speed; không nâng quyền.
- GitNexus CLI 1.6.12 incremental `analyze --index-only .` PASS trong 103,6 giây, không sinh file context; hỗ trợ lookup facade, nhưng có cảnh báo flow budget/cross-language và source đổi sau index. Không dùng đồ thị để chứng nhận mã cuối. Editor classpath diagnostics còn, không sửa IDE ngoài phạm vi.

**Trạng thái checkpoint:** đang kiểm sửa app guard, chưa cấp live grant hoặc USB write Android mới; không tuyên bố TS_SMOKE_PASS hoặc Phase 3.1 hoàn tất. Tài liệu mới bổ sung bằng chứng sau commit `1ad3b251`, không sửa các NOT RUN lịch sử thành PASS. Next: chạy lại app instrumentation cách ly sau fix, rồi đúng baseline synchronous một lần và ứng viên queue theo grant, fail-stop nếu firmware/native bất định.

### Tiếp checkpoint — guard PASS và hai terminal Android thật

- App instrumentation sau sửa mốc heartbeat và harness đúng namespace: 94 PASS / 17,552 giây; library 43 PASS / 1,704 giây. Full JVM mới có 421 tests: drivers 404, usbxfer 1, service 13, app capture 3. Lần regression trước có 1/3 capture tests FAIL ở cleanup Windows; đổi cleanup test sang Files.delete để giữ thông tin lỗi và chạy lại 3/3 PASS, không thay bytes hoặc cap capture. Build debug/release/lint và static PASS; không đồng nghĩa TS hardware PASS.
- `adb install -r` production giữ nguyên SHA sáu preference files. Đã đọc hộp thoại hệ thống USB bằng Android CLI và bấm OK theo ủy quyền người dùng, không root/shell grant. Chỉ xin quyền trước owner, chưa I²C; metadata hiển thị stale không thay terminal PERMISSION_GRANTED. Đóng preflight trước helper.
- Helper bounded AUTO_VERIFIED chạy `phase31-android-sync-20261107-151003-1e02d546`, native firmware 132 start/end/full-length, 8192 image/8196 payload. Ngay sau ordinal 132, audit ghi JAVA_IO_EXCEPTION; terminal ERROR_UNCERTAIN/FIRMWARE_UNCERTAIN, cleanup DRAIN_CONFIRMED, 0 TS. Không raw-after, không probe/init/tune/0x8A/Bulk, không retry trên attachment đó. Log redacted dưới D:\TSDuck\logs; private native audit/ledger giữ ngoài Git.
- Người dùng đã ngắt nguồn và cắm lại theo một yêu cầu phục hồi vật lý. Software tái hiện permission mất sau native 132 trước detach broadcast: 2 tests có 1 FAIL trước patch. Thêm typed AttachmentChangedException chỉ cho presence/permission; active cancellation còn generic failure. Chỉ full 132 và không audit failure mới cho transition, giữ count thực; short final, unknown I/O, cancellation hoặc audit failure vẫn UNCERTAIN. Opt-in externalObservation kết thúc upload sạch rồi giao discovery/permission/owner mới cho app, không sửa terminal cũ.
- Wrapper sau patch full regression PASS trong 2 phút 01 giây, isolated build PASS trong 1 phút 09 giây; instrumentation 95 app PASS / 22,787 giây và 43 library PASS / 2,095 giây. Static bốn ABI/ELF64 16 KiB/Java major 52/release isolation PASS. Đã pin lại TCP sau physical recovery với cùng model/API/boot trước grant mới, không reconnect trong transaction.
- Lượt `phase31-android-handoff-sync-20261107-160808-413355f4` dừng FAULTED/attempted = 0/native = 0 sau raw-before. Ledger phiên chỉ SESSION_GRANTED, không FIRMWARE_CONSUMED, lịch sử attachment vẫn 1 consumed record. Không gọi đây là lần upload thứ hai hoặc EOVERFLOW mới. Đang kiểm key boot/path/id bị Android tái dùng; không xóa hoặc rearm history. Report redacted mới dưới D:\TSDuck\reports; helper exit 0 chỉ là process hoàn tất, không được hiểu TS PASS.
- Sau các lượt, sáu legacy preferences còn nguyên; chỉ autonomous-v1-history mới được tạo. Owner/Activity đã Back, helper kết thúc, không giữ native hoặc USB client. Người dùng không phải xác nhận từng ordinal/plan. Chưa có Android Bulk bytes, root cause EOVERFLOW vẫn chưa chứng minh, Phase 3.1 chưa hoàn tất.
### Chốt Phase 3.1 sau đối chứng queued và bật OTG — 2026-11-07

- Recovery epoch cố định cho đúng lần ngắt nguồn/cắm lại người dùng đã xác nhận được bind vào grant bất biến; history chỉ append. Lượt `phase31-android-epoch-sync-20261107-162518-3c72af72` gửi firmware 132/132 native/full-length, 8192 image/8196 payload, snapshot TRANSITIONING/failure NONE/cleanup NONE trong 4706 ms. Android hỏi permission operational mới, đã đọc hộp thoại và bấm OK qua UI đúng ủy quyền; owner mới chạy probe/init/AUTO K36/status tới Bulk. Không hỏi Approve từng ordinal hoặc chương trình.
- USBDEVFS_GET_SPEED chỉ đọc trên fd hiện hữu trả enum 3 = USB_SPEED_HIGH cả default và operational; endpoint descriptor 0x82/0x81 maxpacket 512 là phép đo riêng. Sync baseline mới 4096 byte/500 ms trả -1/errno 75, read wall 16785000 ns, smoke 68550938 ns, 0 byte/packet; terminal KNOWN_COMPLETED_USB_ERROR chỉ sau DRAIN_CONFIRMED. Không gọi timeout hoặc NO_SIGNAL và không suy speed chỉ từ descriptor.
- Sau known return/drain, đúng một `EXPERIMENTAL_DIAGNOSTIC` receive variant trong `phase31-android-queued-20261107-162659-029afea0`: fresh operational owner, không firmware upload, 8×4096 UsbRequest, read result 0/errno null, managed read 19153906 ns/smoke 55109531 ns. Audit thực submitted 9/completed 9/outstanding 0/released true/delivered 0/cleanup bytes 0; không gán errno 75 cho queue. Đã dừng/back/drain, không retry/reset hoặc vendor mới.
- Người dùng không có bộ OTG/cáp/Android host đối chứng, nhưng báo đã bật Lưu trữ OTG và yêu cầu thử lại. UI switch BẬT/CHECKED được xác minh trước/sau; không tự đổi setting. `phase31-android-user-otg-sync-20261107-163301-6dedd387` dùng fresh owner và không firmware, vẫn High-Speed 3, read 4096/500=-1/errno 75, managed read 17883385 ns/smoke 59436927 ns, 0 byte/packet và DRAIN_CONFIRMED. Dừng ở bằng chứng này, không lặp điều kiện cũ hoặc suy toggle đã khắc phục.
- Audit từng ba receive workflows ghi 156 OUT + 33 IN managed full-length cho frontend, cộng 1 stream-select mỗi lượt; không gọi đó là native-boundary count. Firmware audit chứng minh hai uploads 132 trong phiên dài này (đầu UNCERTAIN, phục hồi clean), một refusal native = 0. Ba FIRMWARE_CONSUMED history records là khóa legacy attachment, recovery epoch và attachment+epoch, không phải ba uploads. Sáu legacy preferences nguyên SHA; không rearm/xóa history.
- Runtime queue zero completion đã được replenished trước khi TsSmoke dừng. Thêm regression fake BEFORE FAIL, sửa không resubmit count 0 và vẫn drain các slot đã submit; final queue 14 tests PASS. Không sửa audit 9 submit cũ hoặc tuyên bố hardware đã được kiểm trên patch cuối. Cleanup unit-test Windows lại gặp FileSystemException chia sẻ directory; chỉ cleanup test thử tối đa 10 lần/450 ms rồi throw nếu chưa xóa được, không retry USB, không quy tác nhân giữ handle khi chưa có bằng chứng.
- Final wrapper `:drivers:testDebugUnitTest :usbxfer:testDebugUnitTest :dvbservice:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console plain --no-daemon` PASS trong 1 phút; XML 430 methods = 413 drivers + 1 usbxfer + 13 service + 3 app, failures/errors 0. Lint 0 errors/13 warnings. Isolated build PASS trong 1 phút 13 giây; app 97 instrumentation PASS / 22,22 giây, library 43 PASS / 1,7 giây. Static bốn ABI/JNI/ELF64 16 KiB/zipalign/min 21/target 37/major 52 (debug 177/release 84)/release isolation và no firmware/TS/trace/private-data PASS có phạm vi.
- Final debug SHA256 `6d04f7dee03a44cd34a92931a08d689020c9d6ed40617b3d68c58483afd21311`, release `f82573c0ba46d0645eebc194c9d0c07972366819b6a4c2cd07b983e34b4d6085`, source fingerprint `3fe396af975baea76b00bc21a60c691655a080df549b11418100713f19ebd189`. Final APK đã cài đè và hash trên điện thoại khớp; sáu stores lịch sử giữ nguyên. APK chạy các receive conditions trước patch là `9ecb806b6f6a04ea1ec121a6330a012b4a0ebfe8b2928d4fe67186c999afe11b`; hardware USB sau patch cuối NOT RUN, không gán nhầm bằng chứng.
- Đã gỡ đúng ba package phiên tạo `.phase31offline`, xác nhận không còn `.phase31offline`/`.phase31autooffline`, production vẫn còn, không diagnostic Activity foreground. Các owner và helper Windows/Android đã đóng, không shell session running, không service/port mới hoặc active syscall bị force-close. Thư mục unit-test tạm rỗng đã dọn; private firmware/audit/grants/ledger giữ ngoài Git, raw TS/PCAP trong D:\TSDuck.
- GitNexus final incremental `analyze --index-only .` PASS / 91,7 giây, MCP xác nhận đúng canonical repo/branch/HEAD 1ad3b251 và index 7807 nodes; không FTS/COPY failure mới. Cảnh báo flow-budget/cross-language của lần đầu vẫn giới hạn suy luận; final cleanup unit-test source đổi sau snapshot nên không chứng nhận mọi working-tree line từ graph. Kết luận dựa source, fingerprints, native audits, TSDuck và tests độc lập.
- Đã lưu milestone runtime và blocker vào AgentMemory, project canonical đúng fork, author copilot-agent-host; smart_search expandIds đọc lại đúng record `mem_mw686yun_6b342b1e2cfd`, nội dung/author khớp. Các báo cáo được user yêu cầu đăng ký artifact, raw TS/trace/firmware không đính kèm. Cập nhật đủ sáu hồ sơ, ADR-028 và instruction scope mới, giữ thay đổi TSDuck ban đầu của người dùng; diff --check PASS và rà thủ công văn bản mới.

**Kết luận cuối:** BLOCKED_WITH_EVIDENCE trong cùng Phase 3.1. Auto workflow đã chạy tới Bulk nhưng native Android chưa nhận bytes, chưa có PAT/PMT runtime hoặc chất lượng/stability để chứng nhận. Không commit/push hoặc đổi toolchain. Báo cáo đầy đủ: D:\TSDuck\reports\phase31-closeout-20261107-163301-6dedd387.md. Bước tiếp có giá trị là cùng APK/profile trên host Android/đường OTG khác đã biết tốt, hoặc trace USB/kernel phù hợp được duyệt riêng; không tự root, đổi register, flash hoặc reset để thử vận may.

## 2026-11-07 — Xuất bản checkpoint Phase 3.1 theo yêu cầu người dùng

- Người dùng yêu cầu commit các thay đổi hiện tại và push lên origin của nhánh đang làm việc. Kiểm live trước đóng gói: nhánh chore/modernize-android-build, HEAD trước 1ad3b251f19779450a2c5d9cffc9f37f9c9a5470, upstream origin/chore/modernize-android-build, origin đúng fork trankien84/AndroidDvbDriver. Index trống trước stage; danh sách 46 file chỉ gồm source, tests, tools, instructions và sáu hồ sơ dự án.
- Tính lại fingerprint source bằng đúng phạm vi checkpoint chốt: 3fe396af975baea76b00bc21a60c691655a080df549b11418100713f19ebd189, khớp source đã kiểm. Không chạy lại build/tests hoặc USB hardware chỉ để xuất bản Git; kết quả 430 JVM/97 app/43 library và các giới hạn runtime ở trên giữ nguyên, không gọi là PASS mới.
- Cập nhật checkpoint đóng gói ở đầu STATUS để các SHA và câu chưa commit/push trước đó được hiểu là lịch sử. Commit gồm cả thay đổi instruction TSDuck có trước, theo yêu cầu commit toàn bộ changes hiện tại; không chứa APK/firmware/TS/PCAP/private logs hoặc chỉnh sibling references. Commit có Co-authored-by trailer theo quy ước.
- Staged check mặc định gắn cờ CR ở cuối dòng của các file mới CRLF; `git -c core.whitespace=cr-at-eol diff --cached --check` xác nhận không còn lỗi whitespace. Giữ nguyên bytes source đã kiểm và kiểu xuống dòng Windows, không đổi Git config toàn máy hoặc âm thầm chuẩn hóa source làm lệch fingerprint.
- Chỉ push thường lên đúng origin/chore/modernize-android-build, không force/reset/rebase. SHA remote và working tree được đối chiếu sau lệnh; kết quả xác nhận cuối được báo trong cuộc hội thoại, không ghi trước rằng push đã thành công. Mục tiêu TS vẫn BLOCKED_WITH_EVIDENCE, không chuyển phase.

## 2026-11-07 — Phase 3.1 continuation: native URB, permission và blocker nguồn tablet

- **Git trước/sau:** `chore/modernize-android-build`, HEAD `1202c8abd49a48c3099e718bead13a21a9176660`; trước sửa sạch và local-tracking ahead/behind 0/0. Không fetch, stage, commit, push, reset hoặc sửa references. Final source fingerprint `9adfdc4fe371f77c0d2a633eee8d99e9c851632c4e51fbd0f82a284fab3c2477`; manifest hash từng file ngoài Git.
- **Phạm vi:** giữ Phase 3.1, thêm debug native USBDEVFS URB diagnostic, approval v6, permission generation/preflight/lifecycle, explicit host profile và helper heartbeat gộp. Các test và sáu hồ sơ/ADR-029 được cập nhật. Không production driver logic, toolchain migration hoặc firmware bundle.
- **Lệnh môi trường Windows thực chạy:** `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git rev-list --left-right --count HEAD...@{upstream}`; `gitnexus --version/--help/status`, `gitnexus analyze --index-only .`, `gitnexus context Tbs5530OperationalApproval -r AndroidDvbDriver -l 6`; Android CLI help/docs/layout/screen; `adb devices -l`, `adb mdns services`, reconnect TLS trước grant khi không có I/O, pin model/API/boot. Không WSL hoặc USB/IP commands.
- **Toolchain:** audit ban đầu bị RemoteSigned chặn; người dùng duyệt Bypass chỉ child process. `pwsh -NoProfile -ExecutionPolicy Bypass -File tools\Inspect-TBS5530-Toolchain.ps1 -NoReport` exit 0; source.properties/JDK giữ nguyên. RunTask không tìm thấy task đã khai báo nên dùng wrapper trực tiếp, không tạo task mới.
- **Windows offline:** agent riêng đọc reference và 203 artifact hash; trace submit 27 byte không chứa requested length, completion actual 8192. Report `gate-a-offline-continuation-20261107-174849-cf051d0af2.md`, không live capture hoặc đổi tuner trên Windows.
- **Kernel:** `adb shell dumpsys usb`, sysfs topology, `getprop`, `uname`, `logcat -b kernel -d -t <bounded>`, Perfetto query và hai config 2 giây qua stdin. Dmesg/sysfs giá trị/tracefs format denied; Logcat đọc được. Perfetto nhóm xhci-hcd bị Bad event name, không gọi capture PASS từ exit 0. Trace tự dừng, không root/setprop/SELinux.
- **Build/tests:** wrapper final `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :drivers:testDebugUnitTest :usbxfer:testDebugUnitTest :dvbservice:testDebugUnitTest :app:lintDebug :app:lintRelease --console plain --no-daemon` PASS, 511 JVM. Isolated build dùng init script ngoài Git cho `.phase31offline`, compile instrumentation PASS. Native C fault injection/canary với GCC 16.2 và MSVC 14.29 `/fsanitize=address /std:c11 /W4 /WX` PASS; ASan binary được chạy lại exit 0 và lưu log. Static final debug/release đủ ABI, ELF64/zipalign 16 KiB, bytecode 52 và release isolation PASS; không dùng static thay runtime API 21/16 KiB.
- **Lỗi build đã sửa:** app instrumentation thiếu direct dependency usbxfer cho NativeUsbfsBoundaryTest; thêm androidTestImplementation project. PreflightActivity thiếu import DurableFile; thêm đúng import. Không cài dependency/toolchain mới. Lint final 0 errors, debug 18/release 10 warnings; cảnh báo debug mới là text I18n.
- **Instrumentation:** OnePlus bản preflight đã kiểm 119 app + 43 library PASS. Tablet final 120 app có 2 FAIL UI, library 43 PASS; đã thấy focus NotificationShade, thử đóng và chạy lại đúng 2 vẫn FAIL. Không sửa assertion hoặc khai PASS toàn bộ. Hai benchmark Phase 2.9 đòi namespace riêng bị lần runner đầu gọi sai; giữ NOT RUN trong namespace phù hợp, tách khỏi suite Phase 3.1. Package test tạo bởi phiên đã gỡ, không đụng library-test package đã có trước hoặc clear app production.
- **Hardware/permission:** người dùng xác nhận hai phục hồi vật lý riêng. Lượt đầu native 0 nhưng history consume trước budget check; giữ lịch sử. Đã sửa budget preflight trước history, thêm test không thay marker/history khi từ chối. Permission-only preflight giúp grant mới có đủ budget; firmware run 8ede3bf9 hoàn tất 132/132 trong 4418 ms. Attach-confirm làm foreground stop; không retry firmware. Lượt operational mới không upload, có một guard failure trong init; gộp heartbeat/report/permission rồi fresh owner đi tới native. OS dialog OnePlus ẩn hierarchy nhưng ảnh đọc được; bấm đúng OK theo grant, không chọn Luôn mở.
- **Native chứng cứ:** run `d902837a-99e0-469d-9d3a-4a916a905cb3`, APK SHA `c2a9cb7c6e61bb9060319137ff4e0aee03f857577a6dad4f60aa12e156b4c9f5`, High-Speed 3, init/AUTO K36/lock. Tám submit và tám reap return 0/errno 0, ba completion -75 và năm -71, actual 0 tất cả. Bảy discard errno 22 rồi reap xác minh đủ 8; outstanding 0, không quarantine, cleanup sạch. Audit SHA `1261154f58955417f168b7568ec16cab4418177ac475201e07c70efbed9ce7f0`, terminal SHA `0d241140c9aac11aa4ee4d70421b1bb3e3fddd6c7aa3c3efd8d5da18cf75b99b`. Không TS, không root cause vật lý confirmed.
- **Tablet và nguồn:** user chuyển cùng dây sang SM-X926B/API 36, kernel 6.1.145. Host/source role có nhưng không enumerate tuner, thông báo cần nguồn ngoài được screenshot theo yêu cầu. User cung cấp adapter khác 12 VDC/1 A, nhãn tuner DC 12 V; cực tính/dòng yêu cầu chưa được xác minh. User tự thử, báo không lên đèn và sẽ tìm nguồn khác. Không firmware/control/native USB trên tablet; dừng phần cứng, không suy adapter/tuner hỏng hoặc OnePlus là nguyên nhân.
- **Final artifacts:** debug SHA `ea6a48ac85e653faf2c6e4112784d32e8bf31e4f2a931cbc41384b1fc7b38ae0`, release SHA `59cb131b6cb4fad994537f6758d4dd582527e7af0f020d7a239d1ae59f42ca9f`; khác APK hardware vì có lifecycle/host patch sau đó. Final APK hardware NOT RUN. Báo cáo 10 mục `D:\TSDuck\reports\phase31-continuation-closeout-20261107.md`, screenshot nguồn `D:\TSDuck\logs\phase31-tablet-power-expanded-1933.png`.
- **Handoff:** các helper/trace đã kết thúc, OnePlus đã drain owner trước handoff; consumed history không xóa. GitNexus từng refresh thành công nhưng source cuối đổi nên không claim index cuối current; tests/source/diff là bằng chứng chính. AgentMemory checkpoint được lưu có project/agent metadata. Bước tiếp duy nhất: xác minh nguồn đúng chuẩn TBS, tablet enumerate, rồi A/B cùng APK/profile hữu hạn; không resume grant cũ.

## 2026-11-07, 21:50 +07 — Kiểm lại tablet sau adapter mới

- **Yêu cầu:** người dùng đã nối adapter mới và USB vào tablet, đề nghị kiểm tra kết nối/tín hiệu. Nhánh và HEAD giữ `chore/modernize-android-build` / `1202c8abd49a48c3099e718bead13a21a9176660`; giữ toàn bộ thay đổi source và lần sửa khoảng trắng docs có trước.
- **Thực hiện:** Windows PowerShell tại repository, xác minh đường dẫn ADB/Android CLI; `adb devices -l`, `adb mdns services`, chọn endpoint tablet online cụ thể rồi đọc model/API/ABI/boot/kernel, `dumpsys usb`, danh sách sysfs USB và Logcat giới hạn. Android CLI layout ghi UTF-8 vào log riêng; không cài hoặc chạy app driver. ADB MCP cũng xác nhận endpoint TCP online; các alias mDNS không được coi là các tablet độc lập.
- **Kết quả mới:** SM-X926B/API 36/kernel 6.1.145, ADB đọc trạng thái thành công. Cả trước và sau khi người dùng cắm lại riêng USB, không có TBS5530 trong host device list; `num_connects=0`, chỉ hai root hub, vai trò host và dữ liệu USB enabled, cờ quản trị/khóa màn hình không chặn. Người dùng báo đèn POWER không sáng. Không suy lỗi anten, firmware, adapter hoặc tuner từ các quan sát này.
- **Giới hạn và an toàn:** chưa xác minh thông số/cực tính adapter mới, không yêu cầu thử nguồn khác. Không có USB vendor/I²C, firmware, tune, TS capture, grant, claim hoặc URB. DVB-T2 lock và MPEG-TS NOT RUN vì USB chưa nhận diện. Không reconnect/failover, đổi role, root, sửa policy/driver hoặc đụng ledger.
- **Bằng chứng / bước tiếp:** log gốc ngoài Git tại `D:\TSDuck\logs\tablet-adapter-recheck-20261107-215018-04c39602`, gồm USB trước/sau và topology. Đối chứng tuner trên Windows với đường kết nối đã biết hoạt động có thể tách vấn đề tablet/USB khỏi tuner/nguồn; chưa thực hiện phép đối chứng đó. Cập nhật STATUS và TEST-MATRIX; không chạy lại build/tests, không commit/push.

## 2026-11-07, 22:34 +07 — Kiểm lại sau khi đèn POWER sáng

- **Yêu cầu / môi trường:** người dùng báo kết nối thành công và đèn nguồn sáng; xác nhận dây dữ liệu nối trực tiếp Galaxy Tab S10 Ultra rồi cắm lại riêng USB phía tablet theo yêu cầu kiểm tra. ADB endpoint cũ chuyển offline trước mọi I/O tuner; đã đọc danh sách mới, chọn đúng tablet online và xác minh SM-X926B/API 36/arm64-v8a/kernel 6.1.145, cùng boot. Không chuyển endpoint giữa giao dịch native vì chưa có giao dịch nào.
- **Đã chạy:** Windows PowerShell đọc ADB inventory, danh tính, `dumpsys usb`, `ls -l /sys/bus/usb/devices` và `logcat -d -t 160 -s UsbHostManager UsbUI`. Snapshot đầu cổng no-data; sau xác nhận cắm lại, cổng host và lịch sử có sự kiện tuner. Thêm đúng một phép kiểm ổn định sau 5 giây, không chạy vòng dò vô hạn.
- **Bằng chứng mới:** lịch sử ghi nhận VID:PID 734c:5530 lúc 22:24:38.839 +07 rồi ngắt lúc 22:24:55.207 +07, cách nhau 16,368 giây. Hai snapshot mới không có tuner trong danh sách đang kết nối và sysfs chỉ root hubs. Không đồng nhất lịch sử đã nhận diện với kết nối hiện tại, không kết luận tuner hỏng, nguồn ổn định hoặc đã có DVB-T2.
- **Giới hạn / hồ sơ:** không app install, permission grant, firmware, vendor/I²C, tune hoặc URB; RF lock/MPEG-TS NOT RUN. Giữ source, ledger và Git không commit/push. Cập nhật STATUS và TEST-MATRIX; log gốc ngoài Git tại `D:\TSDuck\logs\tablet-power-on-recheck-20261107-221543-93025e04`. Bước tiếp cần đường USB được giữ kết nối ổn định, không tự đổi nguồn/cáp hoặc reset phần cứng.

## 2026-11-07 23:01 → 2026-11-08 — Tiếp tục Phase 3.1 trên tablet đã nhận USB

- **Yêu cầu / phạm vi:** người dùng yêu cầu tiếp tục việc dang dở và ghi docs bình thường trở lại. Xác nhận anten thụ động RF IN, đèn nguồn ổn định và cho phép Bypass chỉ child process của kiểm/helper. Sơ đồ người dùng mô tả là dây USB chữ Y, Type-B vào tuner, Type-A dữ liệu qua Type-C vào tablet, Type-A phụ vào sạc điện thoại; không đo điện hoặc kết luận cổng DC 12 V là nguyên nhân. Chỉ dùng routine firmware/K36/receive đã khóa, không mode/driver/LNB hoặc vendor write mới.
- **Git / source đầu:** `chore/modernize-android-build`, HEAD `1202c8abd49a48c3099e718bead13a21a9176660`, giữ toàn bộ source uncommitted và lần sửa khoảng trắng docs có trước. SHA ba references giữ nguyên, không fetch/sửa. Source khớp toàn bộ manifest đã kiểm, APK đầu `ea6a48ac85e653faf2c6e4112784d32e8bf31e4f2a931cbc41384b1fc7b38ae0` khớp SHA sau cài thật; firmware private 8192 byte/SHA khóa khớp.
- **Proof trước native:** `pwsh -NoProfile -ExecutionPolicy Bypass -File tools\Test-TBS5530-AutonomousSession.ps1` và `tools\tests\Test-TBS5530-UsbPermissionUi.ps1` PASS, gồm 18 selector checks. Cài ba package cách ly chưa có, chạy 28 app guard/JNI/permission + 43 library fake-only PASS. Một dòng cleanup dùng `Select-Object -Reverse` không hợp lệ sau test; đã dọn đúng ba package bằng lệnh riêng, không gán exit code của harness thành test PASS tổng thể. Không sửa assertion hoặc clear package đã có trước.
- **Host / permission:** pin SM-X926B/API 36/arm64-v8a/cùng boot trên endpoint ADB cụ thể; không failover trong I/O. Sysfs speed/config/alt denied, native GET_SPEED đo enum 3. Secure USB dialog không hiện node hữu ích; kiểm ảnh mới/foreground/VID:PID trước bấm đúng OK, không chọn Luôn cho phép. NotificationShade được đóng bằng thao tác UI, không sửa cài đặt bảo mật. Các preflight 60 giây hết hạn không cấp grant hoặc firmware; không dùng token/terminal cũ để tiếp tục.
- **Firmware tablet đầu:** helper `EXPERIMENTAL_DIAGNOSTIC`, `NATIVE_USBFS_URB_8X4096`, firmware khóa. Run `8ab6a744-d5f3-41a4-a45d-719185d41a9d` có 132 native start/end/full-length và 8192 image/8196 payload, nhưng managed completed 131/8195 byte, terminal UNCERTAIN/JAVA_IO_EXCEPTION trong 2917 ms, cleanup DRAIN_CONFIRMED. Giữ nguyên history và terminal; dừng mọi writes attachment đó. Descriptor sau đó có tên TBS 5530 không bị dùng để tự vượt UNCERTAIN.
- **Điều tra / sửa có bằng chứng:** `AndroidManager.resolve` lookup thiết bị lần nữa khi kiểm permission có thể mất device sau presence check, nhưng ném IOException chung. Tách helper resolve không thay hành vi, thêm regression đi qua chính nhánh missing-device: `:drivers:testDebugUnitTest --tests info.martinmarinov.drivers.usb.tbs5530.Tbs5530OneShotBootstrapTest` BEFORE 25 tests/1 FAIL. Đổi sang `Tbs5530UsbBinding.AttachmentChangedException` hiện có; không thay guard hoặc điều kiện đủ 132. Test mất device trước final release vẫn UNCERTAIN. Runtime đầu thiếu typed chain nên không quy kết duy nhất race này là nguyên nhân của mọi lần handoff cũ.
- **Kiểm sau sửa:** wrapper `:drivers:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease --console plain --no-daemon` PASS trong 3 phút 14 giây; drivers 420 + app 68 JVM PASS, lỗi 0. Isolated build qua init script ngoài Git PASS trong 1 phút 9 giây, rerun 28 guard + 43 library trên tablet PASS. Cài đè APK chính giữ nguyên hash prefs/history ở bước update, package test đã gỡ. Lint 0 errors, debug 18/release 10 warnings. Static sau sửa kiểm zip/ELF64 16 KiB, đủ ABI, diagnostic chỉ debug, driver major 52, không firmware/TS/trace assets; lệnh đầu lỗi nội suy `$v:` được sửa thành `${v}` trước khi chạy lại, không có USB side effect.
- **Phục hồi vật lý:** người dùng xác nhận ngắt cả hai đầu USB-A khỏi tablet/sạc ít nhất 5 giây rồi nối lại cùng sơ đồ. Một epoch cố định được gắn đúng sự kiện này và giữ qua các preflight không có native. Không xóa/rearm key cũ. Run mới `5fb3c6eb-3d14-4f7f-86fe-4b54700c05be` có 132/132 sạch, 8192 image/8196 payload trong 2891 ms, TRANSITIONING; attach-confirm vượt giới hạn UI 10 giây nên phiên dừng sạch trước operational. Không upload thêm trên attachment operational.
- **Native A/B chính:** sau cấp permission operational mới, grant/owner mới `ce9579b7-c40a-4aa8-a9c7-59c433402b53` qua probe/init/AUTO K36/gate lock và stream-select. Tám submit/reap đều return 0/errno 0; cả tám status -75, actual_length 0, requested 4096. Managed read 54.975.692 ns, smoke 77.489.001 ns. Bảy discard errno 22 rồi reap xác minh đủ tám, outstanding 0, bytes 0/quarantine false, không resubmit. Terminal REPORT/USB_RECEIVE_FAILURE/DRAIN_CONFIRMED, `ts_smoke_pass=false`; syscall errno 0 không có nghĩa URB thành công.
- **Kernel / phép đo bổ sung:** Samsung Perfetto v51.2 nhận cấu hình xHCI, đã thử trace 2 giây và 20 giây với giới hạn file 1/8 MiB. Run bổ sung `fd30da8e-e8f3-42b4-9955-7ec0acbb5749` giữ cùng native profile, cũng tám status -75/actual 0 và cleanup sạch; không firmware. Không chứng nhận trace 20 giây phủ đúng I/O. Parser protobuf bounded theo schema official xác minh cả hai trace có FtraceStats liệt kê ba event xHCI unknown, không ftrace bundle/event hoặc packet nén; không có dữ liệu controller để tương quan. Sysfs/tracefs format denied, usbmon không thấy. Không root hoặc thử tên event/transport lặp vô hạn; query cuối 0 tracing sessions.
- **Kết luận và hành động:** USB enumeration và frontend lock đã tiến triển, nhưng hai host Android đều lỗi completion, không chứng minh chỉ OnePlus hoặc nguồn mới chữa TS. Không có bytes để chạy PAT/PMT/CRC/quality trên broadcast Android. Cần dữ liệu mới từ Windows cold-start firmware/setup hoặc analyzer; không tự chuyển ownership, ChangeMode, CLEAR_HALT/SET_INTERFACE hoặc replay control chưa hiểu. Giữ Phase 3.1, chưa ROOT_CAUSE_CONFIRMED/TS_SMOKE_PASS.
- **Artifacts / Git cuối:** source fingerprint `127e7f550d7db79cd1ea25cd3f763b49c03a2706c407f05722be30e6c7e4f911`, debug hardware SHA `1c51ddf79bb1f3c08dc5b62a219d142e06252d851e0d963c68471dbfd36523a3`, release SHA `0027dd13f4b75e4cdbd93cd51225ec2d1a5b7a918f07f0a472ccaa7e6bd5ef53`. Native audit chính SHA `5737decde28157662ba4640a2c1ba68f99e9623fc9c7bbdd3a65172c9598650a`; run bổ sung SHA `b7c938176d9c3459fc2971cdd0fcd4a537f19b4e8506c87f75b6189146766159`. Báo cáo `D:\TSDuck\reports\phase31-tablet-ab-20261108-ce9579b7.md`; raw/private evidence chỉ trong run directory ngoài Git. Tất cả helper hữu hạn đã kết thúc, owner drain và package test đã dọn; không commit/push, không reset stores. GitNexus index-only refresh thành công nhưng có cảnh báo flow/cross-language; graph chỉ hỗ trợ, không xem thiếu quan hệ là không có caller.

## 2026-11-08 — Đóng gói và xuất bản checkpoint theo yêu cầu

- **Yêu cầu / Git trước:** commit toàn bộ changes và push lên origin của nhánh hiện tại. Đã xác minh `chore/modernize-android-build`, HEAD trước `1202c8abd49a48c3099e718bead13a21a9176660`, upstream `origin/chore/modernize-android-build`; URL fetch và push đều đúng fork `trankien84/AndroidDvbDriver`. Index trống, local-tracking ahead/behind 0/0 trước stage; không fetch/reset/rebase/checkout.
- **Phạm vi:** 47 tệp source, tests, tools và docs đang thay đổi, gồm cả chỉnh khoảng trắng lịch sử theo yêu cầu người dùng. Đối chiếu từng tệp trong manifest source sau kiểm: không khác checkpoint fingerprint `127e7f550d7db79cd1ea25cd3f763b49c03a2706c407f05722be30e6c7e4f911`. Danh sách không có APK, firmware, TS/capture, private log, grant/ledger hoặc tệp tham chiếu ngoài repository.
- **Kiểm chứng:** không chạy lại build, unit, instrumentation hoặc USB hardware trong bước đóng gói; giữ nguyên kết quả và giới hạn đã ghi, không tạo PASS mới. Kiểm whitespace với `git -c core.whitespace=cr-at-eol diff --check`; kiểm lại nội dung mới trong STATUS/WORKLOG để tránh dính chữ. Không thay toolchain hoặc source để chuẩn hóa ngoài phạm vi.
- **Xuất bản:** stage đúng các thay đổi trong repository hiện tại, commit có Co-authored-by trailer và push thường lên đúng ref `refs/heads/chore/modernize-android-build`, không force. Kết quả commit/push và SHA remote được xác minh sau lệnh; không ghi trước rằng thành công. Phase 3.1 vẫn BLOCKED_WITH_EVIDENCE tại TS, không thay kết luận phần cứng.

## 2026-11-08 — Cập nhật script đồng bộ TBS/CoreELEC và kiểm thử

- **Baseline / phạm vi:** đối chiếu script được commit tại `69090a0bcf00b4c6342ec075d90a74964b25ecc9` với bản hiện tại trên chore/modernize-android-build. Gộp ghi chú chưa commit thành một bản ghi theo thay đổi cuối cùng. Giữ nguyên phần lịch sử trước tác vụ này và các thay đổi local instructions/workspace đã có; không sửa script hoặc tests trong lần gộp tài liệu, không stage/commit/push.
- **Cách dùng:** gọi `Sync-TBS5530-Repositories.ps1` trực tiếp để xử lý tất cả repositories, hoặc thêm `-CheckOnly` để so sánh mà không clone/repair/merge/đổi checkout. Workspace được suy từ vị trí script; CheckOnly vẫn có network fetch với repo khỏe. Không cần người dùng xác định loại lỗi để chọn luồng phục hồi.
- **Thay đổi so với script HEAD:** thêm CoreELEC partial/shallow và non-cone file scope, khám phá thêm source TBS5530/CXD2878/M88RS6060; giữ đủ sáu cone directories TBS và main fetch-only. Clone kernel dùng --no-checkout, validate manifest/names/mode/case collision trước checkout; kiểm index/patterns/mọi file/hash thay vì chỉ một số essential files.
- **Phục hồi tự động:** chỉ nhận trường hợp thiếu index và working tree trống; kiểm locks/metadata ổn định 3 giây, sao lưu toàn bộ `.git` ngoài checkout và so hash trước khi tải selected OIDs hoặc dựng sparse. Download không cập nhật HEAD/ref/FETCH_HEAD/tags; recovery xác minh xong mới tiếp tục đồng bộ bình thường. Local edits/staged deletions/untracked data/locks/nhánh phân kỳ không bị reset/clean hoặc ghi đè. `core.protectNTFS=false` chỉ có hiệu lực cho từng lệnh, không trở thành config lâu dài. Sparse merge dùng đúng SHA đã validate.
- **Git stdin Windows:** dùng Process stdin UTF-8 không BOM/LF rõ ràng, đọc stdout/stderr đồng thời; tránh CRLF của native pipeline làm fetch --stdin nhận invalid refspec. Không bỏ kiểm exit code hoặc đổi OID để né lỗi.
- **Hiện trạng chỉ đọc:** TBS SHA `2ce787de9f6e81b4294692a45de15596ae2e1322` sạch, index có, đủ 1.357 file theo manifest và hash PASS. CoreELEC SHA `072f8e99e0ac66d88d7278784e922e08e8253210` thiếu index, 74.109 staged deletions, worktree trống; 27 selected files có trong tree nhưng cả 27 blobs thiếu local. Không fetch hoặc phục hồi references thật; helper CheckOnly giữ fingerprint metadata CoreELEC trước/sau không đổi.

### Validation của bản cuối cùng

```powershell
.\tools\Test-TBS5530-RepositorySync.ps1
& "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -File .\tools\Test-TBS5530-RepositorySync.ps1
```

**Kết quả đã chạy:** PowerShell 7.6.9 **74/74 PASS, exit 0** và Windows PowerShell 5.1.19041.7802 **74/74 PASS, exit 0**; có lượt PS7 kiểm lại sau khi pin target SHA. Repositories/bare remotes đều local và chặn giao thức mạng ngoài. Bộ kiểm thử bao phủ clone, TBS/CoreELEC sparse manifest/hash, tự backup/phục hồi/tải blob, AUX, dirty/staged/untracked/locks, CheckOnly, chạy lại và orchestrator đủ năm repositories; main không merge. Không bypass policy; không còn fixture tạm.

Parser, help, UTF-8/liên kết, whitespace và IDE diagnostics đã đạt trong validation script. Full sync/recovery/CheckOnly network trên references thật và Android/USB tests mới NOT RUN ở lượt viết code ban đầu. Các kết quả 74/74 là bằng chứng đã ghi trước lần gộp tài liệu này, không gọi là tests vừa chạy lại. Không suy fixtures PASS thành CoreELEC thật đã được khôi phục hoặc TS đã hoạt động. Chưa commit/push.

### Bổ sung sau lỗi thực tế lúc 16:32 — fetch blob trong shallow partial clone

- Người dùng chạy lệnh thường: main fetch-only, TBS/C sạch, coax cập nhật `11228b05fb1626bf41c4e1879ace10cd0a8d575c` → `6aaa1899991c875ba0482fa9098adc6c83b01fd8`. CoreELEC đã backup tại parent logs, nhưng fetch dừng exit 1: `bad revision '05613d3b20a18d7c9d0c542ee558d6e556449ff5'`, remote không gửi đủ necessary objects. Không gán toàn lượt là thành công.
- Kiểm chỉ đọc, không implicit download: OID là `drivers/media/usb/dvb-usb/dvb-usb.h`; CoreELEC HEAD vẫn `072f8e99e0ac66d88d7278784e922e08e8253210`, promisor=true/filter=blob:none, object còn thiếu. Không sửa HEAD/config/index hoặc xóa backup thật để thử lại.
- Repro cục bộ dùng clone thật `--no-checkout --depth 1 --filter=blob:none` qua file transport, có shallow boundary và promisor pack. Fetch OID theo helper cũ tái hiện cùng bad revision. Đối chứng: thêm filter đơn lẻ vẫn FAIL; negotiationAlgorithm=noop PASS, và noop+filter PASS. Thay helper chỉ ở exact-object fetch bằng command-scoped noop+filter; không bỏ kiểm exit/manifest/hash, không thêm CLI flags hoặc tự fallback full clone.
- Khoảng trống test cũ: clone thường bị xóa loose blobs rồi gắn config promisor không tương đương partial/shallow clone có promisor packs. Thêm hai cases dùng clone partial/shallow thật cho TBS/CoreELEC, kiểm selected blobs thiếu trước tải, đủ sau recovery, HEAD/refs/FETCH_HEAD không đổi, không lưu negotiation config và không tải blob ngoài manifest. Không bỏ assertion cũ hoặc chạy network vào references thật.
- Lượt regression mới đã hoàn tất **76/76 PASS trên PowerShell 7** và **76/76 PASS trên Windows PowerShell 5.1**, cả hai exit 0, NOT RUN 0 (74 ca cũ +2 ca promisor/shallow thật). Lệnh vẫn là `Test-TBS5530-RepositorySync.ps1` và Windows PowerShell 5.1 `-NoProfile -File` như trên; không bypass policy. Fixture đối chứng riêng và hai bộ regression đều được dọn. Backup CoreELEC thật vẫn tồn tại; index thật vẫn thiếu, worktree trống và HEAD không đổi vì agent không thực hiện recovery thật.

## 2026-11-08 → 2026-11-09 — Phase 3.1 full-stack: cold capture Windows, normalizer và Linux guest

- **Branch / commit trước và sau:** `chore/modernize-android-build`, `69090a0bcf00b4c6342ec075d90a74964b25ecc9`; không checkout/reset/stage/commit/push/sync. Giữ local edits instructions/workspace/sync/docs đã có. Chỉ thêm normalizer/tests/fixtures tổng hợp và cập nhật sáu hồ sơ; không sửa runtime Android hoặc references.
- **Đọc hiện trạng:** instructions theo scope, sáu hồ sơ/ADR mới, source bridge/frontend/native/session/guards và bốn references có SHA. Inventory thật `Get-ChildItem D:\TSDuck -Recurse -File` + `Get-FileHash` ghi 420 file/211.465.062 byte; kiểm sidecar manifest Gate A, native OnePlus/tablet và báo cáo tablet có thật. Không tạo capture giả thay file thiếu.
- **Windows được người dùng giao thiết bị:** kiểm PnP/usbipd, version/help/interface USBPcap/TShark/TSDuck, provider `logman query providers`, ETW manifests; user duyệt UAC và child-process Bypass, không đổi policy lâu dài. Recorder arm trước user cắm tuner; chỉ thiết bị mới trên hub đã xác minh. Cold1 bắt firmware nhưng helper lỗi biến `$Matches`; sửa tên, 5/5 offline cases PASS, giữ capture cũ. Sau user phục hồi vật lý, cold2 bắt đủ tới receive/cleanup bằng helper mới có hash/PID/arguments/deadlines riêng.
- **Windows runtime:** BDA 2.0.0.2/DVB-T/T2, TSDuck 3.45-4798, TShark 4.6.9, USBPcap 1.5.4.0, Intel EHCI High-Speed. Cold2 pcap SHA `70fced47614ff8ec17c9e78f79fca16408c23e6bcadcfb6ad25a4c69cd7f9ded`, 21.136 frames; mỗi một trong hai firmware segments có 132 submits/8192 byte đúng SHA đã pin. TS 30.897.800 byte/SHA `11a6a1c098fdf8adae94b8aea71d60162f236b2993fc9169295ec79dd97f1132`, 3.054 TEI và CC/PES errors, không quality PASS.
- **Requested length:** ETW cold2 đạt cap trước attachment, không coi thành công. Chạy một targeted ETW USBPORT riêng sát thời điểm `tsp -I dvb`; 1.440 Dispatch có `TransferBufferLength=0x2000`, tối đa 8 outstanding, 1.440 Complete và pending cuối 0, không orphan hoặc duplicate. ETL SHA `73911c746852ba2ce0ef822b7509915d05ae80b9595bd866720209cb222c8d5b`, EventsLost/BuffersLost 0, stop exit 0. Pcap vẫn giữ `requested=null`; không gán số mới cho capture cũ. Raw tracerpt offset khác helper, không tự ghép clock.
- **Lệnh capture/analysis thực:** helper lưu đầy đủ argument arrays trong `logs\phase31-fullstack-20261108-172615-404b3ba8`; K36 dùng `tsp -I dvb --adapter <verified> --delivery-system DVB-T2 --frequency 594000000 --bandwidth 8000000 --signal-timeout 10 --receive-timeout 2000 -P until --seconds 15 --bytes 50000000 --exclude-last -O file <new-path>`, có watchdog ngoài 45 s. `tsanalyze --json`, `--error-analysis`, `--service-analysis --pid-analysis`, `tracerpt -of XML` và parser offline đều có raw logs/hash. Không dùng `until` một mình để giới hạn no-data.
- **Normalizer:** thêm `tools\Tbs5530-TraceNormalizer.ps1`, `Convert-TBS5530-Trace.ps1`, test/README và ba synthetic fixtures. Schema giữ null/clock/epoch/IRP generation/source offsets/status riêng syscall. Không tự mở USB hoặc xuất firmware payload. 67/67 PASS trên PS 7.6.9 và PS 5.1.19041.7802; ba native files có 24 records mỗi file, cold2 có 21.134 normalized sau khi loại hai pseudo transfers, tất cả paired và 8 partial setup warnings. Parent final gặp cleanup lock sau assertions PASS/exit 1; không biết tác nhân giữ lock. Đã chuyển công bố PASS ra sau cleanup, chạy lại hai runtime với 67/67/exit 0; fault-injection cleanup thất bại xác minh không có PASS report/line, mọi fixture phiên đã dọn.
- **Differential:** 76 source file hashes, 13 source checks và 6 cold2 checks PASS. Windows reg96/reg97=`55`, Android/TBS source=`54`; Windows reg95 khớp formula. IN B7/IN81 và 8A `0400/0401` chưa giải mã, không replay. Không sửa Android buffer/register hoặc gọi mismatch là root cause.
- **Build/tests Windows thực:** `gradlew.bat :app:assembleDebug :app:assembleRelease :drivers:testDebugUnitTest :app:testDebugUnitTest :usbxfer:testDebugUnitTest :dvbservice:testDebugUnitTest :app:lintDebug :app:lintRelease --console plain --no-daemon` exit 0. Vì một số unit tasks UP-TO-DATE, chạy lại bốn `testDebugUnitTest` với `--rerun-tasks`: 513/513, failures/errors/skipped 0, toàn bộ 81 tasks executed. Lint có 0 errors, debug 18/release 10 warnings; GCC 16.2 fault injection và MSVC 2019 ASan exit 0. Zipalign 16 KiB debug/release, bốn ABI/isolation release và ELF64 align PASS; 316 driver classes có major 52. ADB MCP không có devices, instrumentation/hardware Android mới NOT_RUN.
- **WSL chỉ đọc/build admission:** registered Ubuntu-22.04 có userspace thực 26.04.1/kernel 6.18.54.1; symlink build có nhưng Makefile/Kbuild/generated headers/Module.symvers thiếu. C syntax exit 0, make prerequisite exit 2; không compile/load/install hoặc chuyển USB. Không lặp ép kernel WSL.
- **VM do người dùng cài giữa phiên:** VBox 7.2.22/VBoxUSBMon 7.2.22, sau đó Ubuntu 24.04.5/kernel 6.8.0-149-generic. SSH `ubuntu-VMS` dùng strict host key, headers/Symvers đầy đủ, GCC/Make ban đầu thiếu. Người dùng cho snapshot trước build; snapshot bị treo rồi exit 1 với `RPC_S_CALL_FAILED/0x800706be`. Người dùng yêu cầu không snapshot nữa, tự phục hồi và duyệt build tiếp không snapshot; không tự kill/reset/restore/delete VM/VDI. Package inventory trước/sau và apt simulation không upgrade/remove được ghi, rồi `apt-get install --no-upgrade --no-install-recommends build-essential libelf-dev` exit 0, GCC 13.3/Make 4.3; không thay kernel hoặc media stack.
- **Guest build checkpoint:** official minimal closure đã compile thật nhưng fail vendor frontend API trên stock core; board-only probe modpost/vermagic và 32 CRC khớp không đủ load-ready. Không `make install`, load module, attach USB hoặc ghi firmware. Kiểm Beta thay thế có bounds và provenance sẽ được bổ sung sau khi hoàn tất, không che lỗi build đầu.
- **Artifacts / báo cáo:** `D:\TSDuck\reports\phase31-fullstack-20261108-172615-404b3ba8` có evidence-index, hypothesis/root-cause matrix, control-transfer-diff.csv, initialization-sequence, urb-first-failure, semantic source diff, normalizer và Linux reports. Raw/log/firmware/grants/credentials không vào Git. AgentMemory milestone có project/agent metadata đã lưu; quyết định no-snapshot lưu Lesson riêng.
- **Kết luận tại checkpoint:** ROOT_CAUSE_UNRESOLVED_WITH_NARROWED_SCOPE; Windows nhận TS không chứng minh Android TS_SMOKE_PASS. Không mua analyzer hoặc đổi driver/kernel/PLP/buffer mù. Bước kế tiếp cần toàn closure Linux build/ABI hợp lệ và quyền load/USB riêng, hoặc exact-plan A/B Android nhỏ đã được review; không coi timeout hoặc file trace có thật là task PASS.

### 2026-11-09, 00:12–00:33 +07 — Diagnostic terrestrial hoàn tất build/offline, chưa runtime

- Official/Beta closure giữ FAIL lịch sử; Beta audit guest đủ 331 entries trước extract 13 source/metadata, compile exit 2, không chạy vendor wrapper/core replacement. Community C nguyên trạng build đủ 5 `.ko`, MODPOST/226 CRC PASS nhưng default probe vệ tinh bị chặn bởi phạm vi no-LNB.
- Người dùng chọn chuẩn bị diagnostic chỉ DVB-T2, chưa load/attach/firmware. Thêm tám file patch/manifest/prepare/build/guard/tests/README trong `tools\linux`, không full third-party source, firmware hoặc binary. Base pinned community `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`; references không sửa. Guard kiểm VID:PID/opt-in/8192 byte/SHA trước fixed 132 A0 writes; loại toàn bộ satellite board path, giữ Sony bytes và queue 8×65536, chỉ K36/DVB-T2/8 MHz/PLP0.
- Build guest thực 3 module/MODPOST PASS, 108 CRC records khớp exact kernel/local Symvers. Review bỏ đúng unused label cleanup, giữ fallthrough; final board `.ko` SHA `4fe9a866a45e6381f14f6039e6dff95517848162f2ba34ac056fa82107eb21b9`. 281 fake-plan cases PASS, gồm 132 short và 132 negative -EIO/NULL pointers; không firmware thật. Source checks 25 trên PS7 và PS5.1 PASS, fresh WhatIf không output và prepare hash/apply PASS.
- Parent kiểm lại qua SSH đúng `/root/phase31-terrestrial-diag-0012/source`: guard 281 PASS, final hash/vermagic đúng, diagnostic/CXD chưa loaded. Một lần đoán thiếu `/source` gây read-only file missing/exit 1 đã được sửa bằng locate, không giả source đã mất. Windows listing cuối `Shared`; guest chưa có tuner; không snapshot, load/install module, USB handoff hoặc firmware Linux.
- Các gate chưa đạt: cold/operational identity, re-enumeration/owner, chống bootstrap lặp theo attachment, I²C fail-stop và bounded receive/drain. `linux-runtime-gate.md` ghi exact blockers; không dùng module params làm consent hoặc xin runtime khi phần mềm chưa đủ. Trạng thái BUILD_OFFLINE_PASS / RUNTIME_NOT_AUTHORIZED, ROOT_CAUSE vẫn chưa xác nhận.
- Đã cập nhật sáu hồ sơ, báo cáo tổng/evidence index/matrix và hashes; kiểm `git diff --check`, links và nội dung mới. Không stage/commit/push. User no-snapshot được lưu Lesson, milestone và quyết định có project/agent provenance được lưu AgentMemory. Không coi phần việc runtime bị chặn là hoàn tất toàn yêu cầu.

## 2026-11-09, 00:41–01:48 +07 — Phase 3.1: hai lượt Linux operational-only có trace thật

- **Quyền và Git:** người dùng yêu cầu tự hoàn thiện gate và chạy hữu hạn, báo khi cần rút/cắm/chuyển thiết bị. Branch/HEAD vẫn `chore/modernize-android-build`/`69090a0bcf00b4c6342ec075d90a74964b25ecc9`, giữ edits trước phiên, không commit/push/sync hoặc snapshot. Không sửa runtime Android/reference, kernel/core/binding Windows, không LNB/DC hoặc vendor chưa giải mã.
- **Guards module:** thêm operational-only tách khỏi bootstrap, exact profile/hash/bus/dev, consumed probe + host/guest ledger, control mutex/first-error latch, I²C propagate, wrappers, heartbeat/consent/deadline/byte cap và process-context drain/no-force-free. Giữ queue 8×65536/Sony bytes/K36/8 MHz/PLP0; board không A0/request_firmware/B7/satellite. Proof 281+1522+21, source 25+27, MODPOST/122 CRC khớp; parent fresh prepare/hash/build trước load. Board đầu SHA `be006fdadf5b3c4cf6e519204d06eeb809ff4fb6f01830e16012fcaf40286b0d`.
- **Runner mới:** `tools\linux-runtime` gồm host/guest runner, durable ledger, direct DVB API receiver và tests; không snapshot/USB firmware. Có 19 ledger checks mỗi PS7/PS5.1, host plan từ 26 lên 28 checks khi thêm credit; receiver self-test và 5 syscall-mocked flows PASS. LF cho Bash được khóa bằng scoped .gitattributes. Giữ lỗi CRLF preflight trước consume/load, sửa LF và dùng execution-lf, không phải retry hardware. BLOCKED JSON ban đầu có host exit 0 vì cleanup; sửa terminal gate và 3 exact cases, không replay tuner để kiểm.
- **Handoff đầu:** sau thông báo và user ready, VBox usbattach đúng UUID mới. Guest có enumeration latency rồi nhận bus 2/dev 2/HS480/profile 50 byte khớp SHA đã pin; code/manifest luôn dùng đủ hash, không dùng viết tắt làm guard. Nạp usbmon stock, pipeline 3 giây đọc descriptor có 18 events/1591 byte/dropped 0. Cài dvb-tools sau apt simulation không upgrade/remove, chỉ thêm hai packages. Receiver dùng trực tiếp DVB API, không voltage/LNA/DiSEqC ioctls.
- **Run strict lúc 01:19:** module probe sạch/CXD6802, K36 lock status 31; 324 controls có requested/actual 1122, first_failed 0. IN82 có tám submits đầu requested 65536, completion 82/status 0 → một resubmit → completion 0/status 0. Policy tự latch -61 rồi có bảy cancel -2; chín submit/reap, outstanding 0/drained 1/quarantine 0. File TS 0, chưa đủ packet 188; pcap snaplen 128 chỉ capture prefix 64/82 byte. Pcap 55.561 byte/SHA `ed7e1861303977af76de61590dcf8f4c8047b37d98e6dd1d05240750da45e8ca`, dropped 0. Không có -75/-71 hoặc A0/B7. Module/deps unload, ledger consumed/BLOCKED, không rearm.
- **Normalizer thật:** parser fail ở OUT callback tag `>`; thêm grammar đúng Linux docs và 10 regression assertions. 77/77 PASS PS7/PS5.1 gồm cleanup; strict text có 677 nonempty records, 676 paired và một missing-submit, không bịa để khớp 678 pcap events. Lượt cleanup fail cũ giữ nguyên; guard restore CWD nếu nằm trong fixture không chứng minh ai giữ file lock.
- **Đối chứng có căn cứ:** source official/community resubmit status 0/actual 0; strict guard có thể cắt cơ hội nhận TS. Candidate param `empty_success_credit` mặc định 0/trần 1, global không reset, zero không deliver, negative-stop/caps giữ nguyên. Thêm 54 exact extracted completion checks và first-RX/empty telemetry; source 25+30/MODPOST/122 CRC PASS. Board SHA `07d812a13d511231d8dfc084ab02675e7345a8e1602e8205e8b8d7c0a2612a47`.
- **Epoch vật lý mới:** người dùng rút USB ≥5 giây và xác nhận tuner tắt đèn; recorder được arm trước khi cắm. Arm đầu thiếu log directory, chỉ có header 24 byte/exit 1 trước attachment; giữ evidence, sửa prerequisite rồi arm vào windows-prep2. Windows firmware có hai segments, mỗi segment 132 lệnh/8192 byte/hash đã pin. Windows TS có 7.999.964 byte/SHA `d98d94adbf8499454fdf2b4e05d7326633706369dd02774b6498221b4d8c0734`, 14 services/46 PIDs/TEI 0/sync 0. Không dùng hai chỉ số làm toàn quality PASS.
- **Run credit1 lúc 01:40:** sau thông báo và user ready, guest nhận attachment mới bus 2/dev 3 với cùng profile. Probe/K36 sạch, 635 controls có requested/actual 2070 và first_failed 0. First RX status 0/length 0, đúng một credit được dùng và resubmit thật; tám URB chờ tới receive deadline 10 giây. Policy terminal -110, actual 0/file TS 0, tám cancel -2; chín submit/reap, outstanding 0/drained 1/quarantine 0. Pcap 106.205 byte/SHA `2ef4dbf135755edbdd8ff26e682c8f497355e2008103b48e15b4b5f53bb9d903`, 1.300 events/dropped 0; normalize 1.300 records đều paired, clock guest unmapped. Không tăng credit/buffer hoặc chạy tiếp cùng attachment.
- **Differential:** 9 checks PASS. Linux bỏ bank 9C/reg10 mà Windows và Android source có. Linux ghi reg96/97 bằng `54/54`, Windows ghi `55/55`; PLP0, clock chính, HiZ và tuneEnd khớp trong nhóm đã đối chiếu. C có ba clock writes riêng. `03c80020` là bank-select Sony, không phải lệnh FIFO. Chưa có nguồn đủ để xác định mapping bit 0 của 96/97 theo chân hoặc gọi thay đổi đó là tăng dòng một chân; không patch hoặc replay mù. Báo cáo source cache và phép đo nằm trong hai bundles, không trộn clock/runtime/source.
- **Rollback cuối lúc 01:44:** đợi drain rồi unload board/CXD/ALP/dvb_core; usbmon có refcount 0 rồi được unload. Sau thông báo và user ready, VBox usbdetach đúng target; xác minh Windows PnP OK/TBS BDA/usbipd Shared và guest không còn tuner/module. Không snapshot, reset, reboot, đổi driver hoặc xóa VDI/ledger.
- **Artifacts:** bundles `phase31-linux-runtime-20261109-004247-91506f8c` và `phase31-linux-runtime-20261109-013202-4ecedf9c` dưới D:\TSDuck có session/argv/PID/hash/raw trace/TS/telemetry/ledger result; báo cáo tổng mới `runtime-result.md`, module-runtime review, differential, normalization. Raw binaries/firmware/identifiers không vào Git. Milestones và quyết định có project/agent metadata đã lưu AgentMemory.
- **Causal boundary:** Linux VM thực khóa K36 nhưng hai run không có TS; không có completion -75/-71 trong điều kiện đã đo. Credit1 cho thấy immediate zero-stop không phải blocker duy nhất của lượt mới; không chứng minh Linux native hoặc Android cùng nguyên nhân. ROOT_CAUSE_UNRESOLVED_WITH_NARROWED_SCOPE; Android TS_SMOKE_PASS chưa đạt. Bước tiếp theo có nguồn là readback/mapping 96–97 trước A/B mới, không mở rộng credit/buffer scan hoặc gọi task hoàn tất vì cleanup PASS.

## 2026-11-09, phiên 19:18 +07 — Phase 3.1: firmware static, EP0 mirror và Samsung A/B 4096/8192

- **Git trước/sau:** `chore/modernize-android-build` / `48b53d788db5e023123afbc0a65f77a16ae084ef`; giữ hai edits đầu phiên tại copilot-instructions và instruction VirtualBox untracked. Không stage/commit/push, checkout/reset/rebase, sync hoặc sửa references. User cấp Samsung A/B hai attachment mới, xác nhận từng lần ngắt nguồn ≥5 giây/đèn tắt và giữ cáp/nguồn/RF IN; không GPIO/drive/LNB/VM/driver change.
- **Gate A:** đọc attachment/instructions/sáu hồ sơ/ADR028–031 và pin bốn references. Inventory giới hạn bốn gốc D:\TSDuck có 1964 file; tám SHA trọng yếu khớp manifest gốc, strict/credit1 raw usbmon FOUND_VERIFIED. Android audit cũ thiếu EP0 byte-exact; không lặp Linux VM/SSH/capture. TShark 4.6.9 đọc lại Windows cold2 timestamps 593/595/597: 23.883 và 117.847 ms submit-to-submit, không đúng 125 ms hoặc waveform điện.
- **Gate B:** nhánh độc lập thêm `tools\fx2\gate_b_static.py`, tests và README; firmware riêng 8192/hash đã pin, hai Windows prep2 A0 epochs đủ 132/coverage 1/hash riêng. 64/64 tests PASS, 255 opcode lengths khớp Keil. Dispatch 8A→0237→067B, payload boolean từ E741/IRAM 21.1; selector 04→IOA.7, 0A→IOA.6. IN 81 trả software bit 20.7, B7 có latch IOA.2. IFCONFIG=CB/Slave FIFO, PORTACFG/wiring PA7 chưa rõ; không replay hoặc gọi board reset. FIFO quad 1024/AUTOIN 512 không phải requested URB.
- **Triển khai:** mirror debug OFF mặc định, bounded 4096 records/payload 64, không extra USB/disk I/O trong call, giữ buffer/timeout/return/errno-null/clock đúng, flush sau drain và hash readback. Native variant 8×8192 debug có exact chunk/permit/hash riêng, giữ default 4096/cap 65536/maxReads 16/5 s/owned fd/drain 1 s/quarantine; không CLEAR_HALT/SET_INTERFACE/reset. Cập nhật app session/Activity/approval/helper/tests và sáu hồ sơ; không đổi release USB hoặc driver khác. Nhãn Activity debug được tách sau query hai entry trong app chính.
- **Lệnh/kiểm offline thực:** wrapper `:drivers:testDebugUnitTest :app:testDebugUnitTest :usbxfer:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :drivers:assembleDebugAndroidTest --console plain --no-daemon --rerun-tasks` exit 0/173 tasks executed; drivers 431+app 68+usbxfer 31=530 tests, failure/error/skipped 0. Mirror 8 tests PASS; GCC 16.2/MSVC 19.29 ASan 4096/8192 và NDK 30 clang 21 core/JNI syntax bốn ABI PASS. `:app:assembleRelease :app:lintDebug :app:lintRelease` PASS; final debug-label assemble/lint PASS, 0 errors/18 warnings. Không cài package/upgrade toolchain. VS Code task lookup không tìm thấy task nên dùng wrapper, không sửa config.
- **Tablet fake:** build app cách ly bằng `Build-TBS5530-OfflineTestApp.ps1`, cài qua ADB serial tường minh; app guard/permit 4 tests PASS và library Handoff/mirror 2 tests PASS, không tuner I/O. Test mới ban đầu 3/4 do kỳ vọng ledger rỗng sai; sửa đúng SESSION_GRANTED/no attempt/no firmware rồi rerun 4/4. Không thay guard để giấu lỗi. Đã gỡ đúng app/test cách ly sau user cho phép, app chính/history giữ nguyên.
- **Firmware/permission A:** hai preflight 60 s thất bại trước grant/write, task cũ không nhận token mới; đóng màn terminal và tạo instance mới. Không force-stop/clear data hoặc nới timeout. Dialog Samsung đã xem ảnh mới trước OK, không Always. Run c347d3b9-ee2c-4580-b296-dedc955f33d4 full 132/132, 3073 ms, 137 EP0 gồm probe 5 controls ID 01/97; budget thiếu trước INIT, giữ ERROR_UNCERTAIN/drain sạch. Transcript chứng minh chưa partial init nên cấp owner/grant operational mới trên cùng epoch, không nạp firmware lại hoặc rearm ledger.
- **A operational:** run 9a531e91-0366-4f71-bc67-2caba9846083, SM-X926B/API 36/boot 46/HS 3, K36 AUTO khóa; 190 EP0, bank 00/reg 96/97 gửi 54/54, 8A 0A00. Tám submit/reap requested/capacity 4096, 8 completion -75/actual 0, syscall 0/errno 0; REPORT/outstanding 0/quarantine false, file TS 0. EP0 SHA 63761bdcf0db5104e62e72b4027cb0977c40f29790d1d54cb539741dd794c5f2; native SHA 370f0ffd2700cbecba4333dc1db0b113ad66fe33e3aa33b57d0d5d37b42e59d5.
- **B bootstrap/operational:** sau user xác nhận epoch vật lý mới, run 239293f8-de3a-4629-8d13-e63417ad8625 full 132/132, 3155 ms, 132 EP0; attach-confirm vượt 10 s nên WAIT_REENUMERATION/drain, không init/Bulk. Owner/grant mới operational-only run 4fc265e0-2377-4997-9a26-0d4afc3a99c8 không A0: 190 EP0, 8 submit/reap requested/capacity 8192, 8×-75/actual 0, TS 0/REPORT/drain/outstanding 0/quarantine false. EP0 SHA bb68d798c9622406e46ed955ec6ccc5bfd67118651a599b900373d7ad3d34e97; native SHA b30d7b122a80637d1d6c95ac77d402ae98f19b51363fb10c3f13ae3713937148.
- **Phân tích offline mới:** hai init 190 controls có toàn bộ OUT bytes giống nhau; chỉ poll đầu bank 20/reg 10 sequence 186 trả 02/03, sequence 189 cùng 26. Bank9C/reg10=00000000, AD=00/AUTO, C3=00 trước stream; 96/97 là sent bytes, chưa effective readback. Windows 55/55/manual 0 và B7/81/0400/0401 vẫn khác; omission 9C10 riêng Linux không giải thích Android. 8192 không sửa lỗi trong hai attachment, không chứng minh root cause hoặc loại trừ mọi host/FIFO problem.
- **Lỗi công cụ đã sửa:** export EP0 đầu báo hash mismatch giả do command-mode `-split`; raw host/device thực cùng 0859647e…/105921 byte. Dùng parser 64-hex có regression PS7/PS5.1, không chạy lại USB. Hai PS scripts giữ UTF-8 BOM sau PS5.1 parse FAIL, không đổi ExecutionPolicy. Collector CopyToAsync ban đầu phát thêm đối tượng rỗng vào manifest; giữ gốc và tạo `verified-files-only.json` 16 files, mỗi file bytes/hash Android-host khớp, không sửa raw.
- **Artifacts/provenance:** tổng `D:\TSDuck\reports\phase31-continuation-20261109-192232-71163e13\phase31-continuation-result.md`, raw sibling logs `phase31-continuation-20261109-192232-71163e13-verified`; proof FX2 và native 8192 ở bundles riêng. APK A/B SHA 8f51af4a748ee6f7253efb11da4d73e264dc3ef7fdac85fdd1c3cbf39c41c5c0; APK nhãn debug cuối SHA f1113bd3eaad93acb668b602289c8c37181b641911d9003854dc87a05eae05d5 build/lint PASS nhưng chưa cài/chạy, không gán kết quả hardware cho nó.
- **Cleanup và blocker:** A/B đã REPORT/drain sạch, helper STOP/exit 1 đúng vì không TS; raw 16 files thu đủ trước khi ADB Wi-Fi offline. Không reconnect/reset, không giả quan sát owner mới; lần giao cuối tuner ở tablet. Không PA7/drive/PLP mới, VM/SSH/snapshot/kernel/driver/LNB/DC hoặc firmware lần hai. G/S vẫn BLOCKED_FOR_SAFETY vì thiếu wiring/pinmux/mapping/effective state. ROOT_CAUSE_UNRESOLVED_WITH_NARROWED_SCOPE, Android TS_SMOKE_PASS=false; bước tiếp theo là review nguồn đúng bo/chip để chọn exact safe A/B, không lặp buffer/timeout/VM credit.
- **Kiểm tài liệu cuối:** đã đọc và sửa 359 vị trí thiếu khoảng cách trong văn bản mới của sáu hồ sơ và reports, có bảng before/after riêng dưới bundle; không sửa phần lịch sử. UTF-8, cấu trúc Markdown và bốn relative links mới PASS; `git diff --check -- docs\project` và toàn diff với `core.whitespace=cr-at-eol` PASS. DOC_SPACING_PASS có phạm vi, không coi regex là proof ngôn ngữ. AgentMemory đã lưu milestone static và A/B với canonical project chữ thường/author; một record con dùng khác hoa-thường được giữ như ngoại lệ, không migrate tự động.

## 2026-11-09 → 2026-11-10 — Phase 3.1: original closure, cold M và một đối chứng timing sớm

- **Git trước/sau:** `chore/modernize-android-build` / `cbb2aa6892ede102d7b122badec848ae7c483b14`, sạch đầu phiên. Không stage/commit/push/checkout/reset/rebase/sync hoặc sửa references. Chỉ thêm tools/linux-original và cập nhật sáu hồ sơ; raw, modules, firmware, grants và ledgers ở D:\TSDuck.
- **Baseline/instructions:** đọc attachment thực thi, copilot/scoped instructions và sáu hồ sơ mới nhất. Pin lại TBS/C/CoreELEC/Go; tám raw artifact cũ khớp manifest, không chạy lại D. `VBoxManage --version/help/list/showvminfo`, SSH strict root preflight xác nhận VBox 7.2.22r175245/Ubuntu 24.04.5/kernel 6.8.0-149. VM hiện xHCI on/EHCI off, không tự đổi controller. Tuner vắng đầu phiên.
- **Nguồn và closure:** đọc community probe/firmware/Sony/M88/ALP/Makefile và diff D theo hàm. Community thật là 8×65536; bank9C đã comment trong Sony gốc. Nghiên cứu official/CoreELEC độc lập cho thấy vendor API/layout và no_reconnect không refresh descriptor. Original O-C build mới đủ 5 module, input/output source hash không đổi, MODPOST/vermagic/226 CRC khớp; không load probe vệ tinh/B7/8A0801. O-T full closure chưa build, không gọi board-only ABI probe là đủ.
- **Triển khai M:** patch/manifest chuẩn bị vào scratch mới; giữ terrestrial config/init/feed/demux/successful resubmit, bỏ satellite/ALP consumer; default OFF, exact cold/operational profile, full132/hash, allowlist, first-error, heartbeat 2 giây, receive 10 giây/16 MiB, cap 4096 submit và drain. Sony chỉ sửa error-unlock/recursive-lock, không đổi successful register bytes. Receiver mới kiểm VID:PID/adapter/canonical sysfs parent/name và frontend/demux/DVR cùng parent, K36/manual PLP0/full TS. Không sửa receiver diagnostic cũ hoặc runtime Android production.
- **Lệnh build/test:** Windows Node/GCC trích callback source thật 820 checks PASS; receiver 80 mocked flows/JSON PASS trên Windows và Linux, production receiver Linux compile PASS. Prepare -WhatIf/copy/hash/reproduction PASS; guest `make -j2`, `bash -n`, readelf/Module.symvers cho M 127 CRC PASS. Kmod31 --show-modversions báo Invalid argument do layout Ubuntu variable-length; đọc chính header/modpost và ELF để kiểm, không force ABI. Thêm 10 CRC fixtures và 10 runner fake grant/WhatIf/consume/replay checks PASS, không SSH/USB trong fake tests.
- **Quyền/lượt A:** người dùng duyệt cold M, firmware/filter tạm và receive/cleanup; sau capture ready xác nhận ngắt ≥5 giây/đèn tắt/RF IN thụ động. Cold device2/default hash e2887bad… gửi đúng 132 A0 full-length, tái tạo 8192 byte/hash b97cf208… từ pcap. Cached EP82 chưa có nên M dừng trước I2C/Bulk; thiết bị tự re-enumerate thành device3/operational hash 22e2f1df…, không reset/upload lần hai. Người dùng duyệt operational handoff riêng, firmware tạm đã xóa trước load; ledger bootstrap giữ nguyên.
- **M late thật:** board SHA `5ad5512988800c2ee6954faa4fed8058f33c489f505a01ac6086b2859a2141a6`, K36/8 MHz/manual PLP0/status 31; một 8A0A00 sau lock, không A0/B7. Tám URB requested 65536 chờ 10 giây rồi tám cancel -2/actual 0; submitted/reaped 8/8, outstanding 0, bytes 0, policy -110. DVR 0 byte, chưa TS. Pcap SHA `2470e8fec772f5b50ebe692d58897f35e5d8c7133a32876742bb2250bc52827a`, có 1492 events paired/dropped 0. Cleanup sạch và trả Windows trước phép mới.
- **Quyền/lượt B timing:** người dùng duyệt đúng một fresh cold epoch giữ cổng/cáp/nguồn/RF IN, chỉ chuyển 8A0A00 trước init. Patch timing mặc định OFF, exact two-call-site diff và test ordering/short/error/baseline PASS; build/127 CRC PASS. Board SHA `a02c607cefe7bd02361f1629f78e39178e17dd9a88a53078b97d1ee77447678d`. Device4→5, bootstrap đủ 132 transfers/hash; handoff không A0. Frame 408 xác nhận một 8A0A00 trước init, không có lần thứ hai ở feed. K36/status 31 nhưng tám URB chờ 10 giây rồi cancel -2/0 byte, drain 8/8, outstanding 0, DVR 0. Timing sớm không đủ sửa trong cấu hình M/xHCI này.
- **Trace B:** pcap SHA `abdea3f3f7af76b7d4535cec73c99890e0f32638e883bb329bbc3ddb4beb2a34`, có 1935 events/dropped 0. Normalizer cuối dùng source ordinal 60/384, có 1934 records paired và một missing-completion enumeration; A0/Bulk paired đầy đủ. Bản normalized đầu nhầm key địa chỉ với ordinal được bỏ dùng, không sửa raw. TShark dùng usb.urb_len cho completion OUT, không usb.data_len; IN response đúng field usb.control.Response. Short standard descriptor hợp lệ không bị tính là vendor short/error.
- **Regression Android/native mới:** wrapper `:drivers:testDebugUnitTest :app:testDebugUnitTest :usbxfer:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease --console plain --no-daemon --rerun-tasks` exit 0, có 331 tasks executed, JVM 530/530 (68+431+31), không failures/errors/skipped. GCC native thường và MSVC ASan 4096/8192 PASS; MSVC thêm /Zi sau warning-as-error của command ban đầu. GCC ASan link thiếu libasan nên runtime NOT RUN, không cài thêm. NDK core/JNI syntax bốn ABI/API 21 PASS; normalizer fixtures 77/77 mỗi PS7/PS5.1 PASS. VS Code task lookup trả Task not found nên dùng wrapper; một task-agent model unsupported không tạo kết quả, không retry agent/model. Không Android ADB/UI/instrumentation/hardware mới.
- **Artifacts/cleanup:** reports chính `phase31-original-20261109-234104-c6017f83`, timing `phase31-original-20261110-004247-09840fb6`, cùng raw trong sibling logs. Cold A binary chưa copy riêng trước rebuild; giữ SHA build/load nhưng không gán hash handoff hoặc giả giữ binary. Cả hai run có streaming 0/outstanding 0 trước unload; modules phiên tạo/usbmon/filter/firmware tạm đã gỡ, tuner Windows PnP OK/BDA/Shared, VM xHCI giữ nguyên. Ledgers consumed còn, không retry/rearm/snapshot/kernel/driver/LNB/DC hoặc GPIO mới.
- **Kết luận/next:** ROOT_CAUSE_UNRESOLVED_WITH_NARROWED_SCOPE, Linux TS 0 byte; Android live mới NOT RUN, historical TS 0 byte và chưa TS_SMOKE_PASS. Không đưa timing hoặc kernel-driver copy vào Android như fix. Bước tiếp theo là source/readback plan selected-PLP/L1/TS output/FIFO hoặc Android SET_INTERFACE mặc định OFF có grant riêng, không buffer/credit/timing scan. GitNexus status không trả, dừng helper, không chứng nhận index/FTS hoặc dùng graph thay source/tests.
- **Kiểm cuối và persistence:** đã đọc diff thật và đoạn mới, sửa các chỗ thiếu khoảng cách, kiểm bảng/links và git diff --check; DOC_SPACING_PASS có phạm vi, không coi detector hẹp là proof ngôn ngữ. Hai AgentMemory milestone đã lưu/đọc lại đúng project và author. Đã gỡ thêm mc auto-dependency sau xác minh refcount 0; raw/consumed ledgers giữ nguyên.

## 2026-11-10 — Phase 3.1: raw state differential và Android explicit alt0

- **Git trước/sau checkpoint:** `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`, tree sạch đầu phiên. Không stage/commit/push/checkout/reset/rebase/sync hoặc sửa references. Chỉ sửa debug integration/helper/tests, thêm công cụ offline và cập nhật hồ sơ.
- **Preflight thực:** đọc prompt, project/scoped instructions và sáu hồ sơ mới nhất. `VBoxManage --version/help/list/showvminfo`, SSH BatchMode/StrictHostKeyChecking, `usbipd list`, ADB và raw inventory. VM đầu tiên poweroff/SSH255; người dùng tự bật, kiểm lại root SSH0/Ubuntu24.04.5/kernel6.8.0-149/xHCI. Không đổi VM/controller hoặc USB owner; tuner không có ở Windows/guest. Bốn reference SHAs giữ nguyên.
- **Gate A/B offline:** Hash lại pcap Windows cold2/TS, M late/early và Android EP0/native. TShark 4.6.9 xuất TSV; decoder mới ghép URB theo owner, giữ bank UNKNOWN và tách WRITE/READ. Đọc trực tiếp bytes pcap xác minh descriptor, hai A0 epochs và first Bulk submit 1650/complete 1659, status 0/actual 8192. TSDuck `tsanalyze --json` exit 0 trên sample đã pin: 164.350 packet, 14 services, 47 PID, 3.054 TEI và 2.395 discontinuities. Không quality PASS hoặc suy requested buffer từ actual.
- **Phát hiện có giới hạn:** Linux M đọc được Sony TS_LOCK trước Bulk và trong lúc chờ, nhưng USB vẫn không có byte. Windows chọn manual PLP0 giống M. Android có write bank9C giống Windows mà vẫn lỗi, nên chưa có bản sửa chung. Chưa biết PLP thực được chọn, tín hiệu trên chân chip hoặc đường vào FIFO. Báo cáo mới giữ riêng nguồn, readback và giả thuyết; không replay B7/GPIO.
- **Triển khai Android:** agent cùng task thêm flag `EXPLICIT_SET_INTERFACE_ALT0` debug-only OFF, exact identity/operational/High-Speed/owner/idle/K36AUTO/native4096 và grant/plan riêng. False/exception/disconnect dừng trước Bulk, không reset/configuration/CLEAR_HALT/force claim hoặc firmware retry. Release diagnostic class absent. API có budget kiểm sau return, không force-close synchronous call.
- **Validation trước live:** Focused JVM 60/60 và regression 559/559 PASS. Debug/release/lint và hai APK instrumentation build PASS, có 397 tasks executed. Lint có 0 errors, debug 18/release 10 warnings. Cài APK đã pin bằng Android CLI, không xóa dữ liệu hoặc ledger. Trên tablet đã chạy 4 fake lifecycle tests và 16 approval tests, tất cả PASS, không USB. Decoder có 21 tests; pipeline 16 checks; callback 820 checks; receiver 80 flows; FX2 64 tests đều PASS. Không cài dependency hoặc đổi toolchain.
- **Quyền và lượt A:** Người dùng duyệt một cặp A/B và xác nhận ngắt nguồn ít nhất 5 giây, đèn tắt rồi gắn mới. Cáp, nguồn và anten RF IN giữ nguyên. ADB mất kết nối trước grant; người dùng tự khôi phục. Sau khi được cấp OS permission, bootstrap hoàn tất 132 transfers trong 3072 ms và drain. Thiết bị tự re-enumerate, cần quyền operational mới. Handoff cùng physical epoch không nạp firmware lại; K36 AUTO khóa, có 190 EP0 và tám REAP -75/actual 0. Syscall return/errno=0, outstanding=0/quarantine=false. Đã thu 10 files và kiểm hash Android/host khớp. Timeout của host helper không được trộn với kết quả USB.
- **B đầu và host guard:** Người dùng xác nhận attachment B mới. Bootstrap đủ 132 transfers trong 2940 ms rồi tự re-enumerate. Handoff ghi 150 EP0 full-length, giống prefix của A, sau đó dừng HOST_HEARTBEAT_INVALID ở FRONTEND_INIT. Chưa gọi setInterface hoặc native receive. Drain sạch, đã thu 8 files; không giả native/sample chưa được tạo. Không retry epoch đã tiêu thụ. Helper được bổ sung chẩn đoán stage/operation và một-call heartbeat publication, giữ runtime 1500 ms/heartbeat 2 giây. Bộ fake mới có 72 checks PASS trên PS7 và PS5.1; chưa chứng minh nguyên nhân duy nhất của B.
- **B thay thế được duyệt riêng:** Người dùng đồng ý đúng một B mới sau helper fix và xác nhận physical recovery. Initial grant dừng NONZERO_EXIT trước publication; chỉ có file staging và ledger rỗng, chưa có grant.json/heartbeat/report hoặc hardware I/O. Kiểm chỉ đọc `run-as ... cat /proc/uptime` trả exit 1/Permission denied. App UID không có cùng quyền với ADB shell UID. Không root hoặc đổi SELinux. Giữ attachment/epoch và evidence; chỉ tiếp tục sau khi sửa khả năng tương thích và kiểm thử, không rearm hoặc lặp A.
- **Nguồn và artifacts:** Bundle `D:\TSDuck\reports\phase31-state-20261110-100952-10a3635f` có báo cáo, bảng provenance và kết quả test; raw/firmware/grants/serial ở ngoài Git. Android validation ở `D:\TSDuck\logs\gate-d-alt0-20261110-102308-6153665f`. GitNexus CLI 1.6.12 index-only exit 0 trong 132,3 s, meta HEAD b73f110; MCP vẫn báo behind 2 nên không chứng nhận graph/FTS cuối. Source, hash, tests và raw là chuẩn.
- **Checkpoint cuối:** Helper đã sửa đọc clock ở shell UID rồi truyền positional argument vào run-as; parent chạy `Test-TBS5530-Heartbeat.ps1` và bản Windows PowerShell5.1: 82/82 checks mỗi bản PASS. Không đổi heartbeat 2 giây, runtime call 1500 ms hoặc expiry. Wrapper mới chưa đạt Android runtime: launch dùng challenge cũ bị từ chối; workflow chưa grant có report WAIT_DEVICE/FOREGROUND_LOST/DRAIN_NOT_CERTIFIED. Control0 và firmware/native/interface=null không được suy thành drain PASS. Lệnh định tạo task mới đã dừng ở guard; NEW_TASK/CLEAR_TASK/B2 NOT RUN. Không force-stop, reset, detach, rearm hoặc chạy thêm.
- **Kết luận bàn giao:** ROOT_CAUSE_UNRESOLVED, A 0 byte và B setInterface HARDWARE_NOT_RUN. Mục tiêu Android TS chưa đạt. Không Linux hardware mới, snapshot, GPIO, LNB/DC, controller hoặc BDA change; giữ Phase 3.1. Source Java/APK không đổi logic sau build; EOL sửa surgical, helper tests riêng đã chạy. Hồ sơ mới và raw được giữ bền, chưa stage/commit/push. Bước tiếp theo chỉ là chẩn đoán cleanup/lifecycle của workflow cũ trong phạm vi chỉ đọc; không tiếp tục USB khi guard chưa đủ.
- **Kiểm invariant owner cuối:** Source xác nhận session không có enum STREAMING; streamingStarted chỉ đổi boolean, state vẫn READY. Thêm assertion ở test hiện có, kiểm READY/streaming/claimed trước và trong Bulk. Wrapper rerun SessionTest/SmokeTest/ExplicitInterfaceSelectionTest: 54/54 PASS, 23 tasks executed; không sửa production, rebuild APK hoặc chạy live mới.
- **Kiểm tài liệu và persistence:** Đã phát hiện/sửa 36 mục spacing có căn cứ trong báo cáo mới, thêm các sửa thủ công ở đoạn mới; 17 cảnh báo adjacency ban đầu là false positive của detector và đã sửa rule. Đọc lại vùng thay đổi của 13 Markdown, detector cuối 0 findings và git diff --check exit 0; không sửa hàng loạt lịch sử. Liên kết relative vùng mới PASS. Đã dọn 17 intermediate analysis tự tạo, giữ nguyên raw và ledgers. IDE báo debug/testDebug Java không trên classpath của project; wrapper build/tests/lint đã PASS, không tự cấu hình lại workspace. Hai checkpoint AgentMemory đã lưu và đọc lại đúng author/canonical project; không gửi raw hoặc credentials.

## 2026-11-10, phiên 12:09 — Sửa lifecycle, kiểm no-I/O và B setInterface thật

- **Baseline:** Branch/HEAD `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`. Đọc lại instructions và sáu hồ sơ, lưu byte-level backup các file điều phối trước sửa dưới bundle mới. Giữ toàn bộ edits có trước; không stage/commit/push hoặc sửa references/toolchain. Bundle `D:\TSDuck\logs\phase31-lifecycle-20261110-121354-96284261` và sibling reports.
- **Sửa điều phối:** Thêm launch token riêng và gate cho phiên mới sau cleanup/audit. No-resource proof không dựa trên byte count; sau acquisition vẫn bắt buộc runtime drain/termination. Callback cũ có generation guard. Mode ControlPlaneOnly bất biến cấm mọi USB boundary. Helper định tuyến CLEAR_TOP + SINGLE_TOP qua preflight, không CLEAR_TASK hoặc force-stop; typed terminal xử lý cả bootstrap dừng ở WAIT_REENUMERATION.
- **Kiểm phần mềm:** Wrapper focused launch 6/6, fresh full JVM 565/565, debug/release/lint và AndroidTest APK build PASS, có 366 tasks executed. Lint có 0 errors, debug 18/release 10 warnings. Fake launch/terminal 57 mỗi PS7/PS5.1, heartbeat 82 và helper regression PASS. Không cài dependency hoặc nâng toolchain. VS Code task lookup không thấy task; dùng wrapper.
- **Android không I/O:** Kiểm app cũ không USB fd trước cài update; hardware history SHA giữ nguyên, không clear data. Người dùng khôi phục ADB hai lần, boot 46 không đổi và tuner giữ nguyên. Lifecycle 5/5 và approval 16/16 PASS. Hai helper→Android sessions exit 0/CONTROL_PLANE_COMPLETE, grant/heartbeat/STOP/terminal/drain, không firmware/native/interface. Một no-I/O attempt bị guard 1500 ms dừng, giữ FAIL. Một instrumentation báo Process crashed khi ADB offline nhưng không có app fatal trong DropBox/crash buffer. Sau khôi phục, focused route 1 và full 5 PASS; không suy nguyên nhân.
- **Quyền B thật:** Người dùng duyệt một B sau no-I/O gate và xác nhận ngắt nguồn ≥5 giây, đèn tắt rồi gắn mới cùng tablet/cáp/nguồn/RF IN. Đã cấp OS permission default và operational. Các pregrant/routing attempts không I/O và giữ cùng physical epoch; không tự tạo epoch hoặc rearm. Bootstrap thật full 132/image 8192 trong 3230 ms, tự default→operational. Handoff không FirmwarePath/A0.
- **Kết quả B thật:** APK SHA `8b8dac4061991abc38643315d41b3b1f8383fec5567e68302a080c86f396a05c`. K36 AUTO khóa, 190 EP0 full-length giống byte-exact baseline A. setInterface đúng một call/true/958461 ns; before/after descriptor/hash/interface/EP82/speed3 không đổi, outstanding=0. Native có 8 submit/8 REAP đều -75/actual 0, syscall return/errno=0; sample 0 byte. REPORT terminal/USB_RECEIVE_FAILURE, DRAIN_CONFIRMED/RUNTIME_DRAIN_CONFIRMED, outstanding=0/quarantine=false. Helper exit 1 đúng vì không TS; operational B không có orchestration timeout. Thu mười raw files, hash Android-host khớp rồi rời workflow, không thêm receive.
- **Kết luận:** Lifecycle blocker đã sửa và kiểm runtime. Explicit alt0 đơn lẻ không đủ sửa trong điều kiện B; A/B khác physical epochs và APK do lifecycle fix nên không gọi causal A/B hoàn hảo. Giữ default OFF/release không đổi và Phase 3.1, ROOT_CAUSE_UNRESOLVED/TS_SMOKE_PASS chưa đạt. Không buffer/timing/altsetting scan, GPIO/B7/04xx/drive replay, Linux/controller/owner switch hoặc snapshot. Bước kế tiếp là readback Sony output có nguồn và consent riêng; không chạy thêm hardware trong phiên.
- **Kiểm cuối:** Code review chỉ đọc phần lifecycle mới không phát hiện lỗi có độ tin cậy cao. Đã kiểm lại 57 launch/terminal, 82 heartbeat và helper regression trên mỗi PS7/PS5.1; giữ 1500 ms/2 giây. Tài liệu mới được đọc lại, sửa spacing thủ công và kiểm link; không đụng lịch sử cũ. EOL được khôi phục theo từng dòng, chứng minh nội dung logic không đổi. Đã sửa một blank line thừa cuối WORKLOG; detector 8 Markdown không còn findings và git diff --check exit 0. Kiểm cuối chỉ đọc thấy app không còn process/USB fd, tuner vẫn ở tablet. Báo cáo gồm exact commands, hashes, tests và cleanup; raw và ledgers giữ nguyên ngoài Git. AgentMemory checkpoint đã lưu với canonical project/author, không đưa raw hoặc credentials vào memory.

## 2026-11-10, phiên 16:10 — Triển khai và đo Sony TS output readback

- **Baseline và nguồn:** Branch/HEAD giữ `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`, giữ toàn bộ edits có trước. Đọc hồ sơ/instructions, backup byte-level bảy file trước sửa và xác minh bốn reference SHAs; không fetch/sync hoặc sửa references. Agent nguồn chỉ đọc đã lập bảng provenance C4/C6/80/81/95/A9/33 và sync20/10; loại C3/96/97/D9. Không sao chép helper RMW hoặc suy maskFF có read ẩn.
- **Mã mới:** Monitor debug mặc định OFF, hai stage theo thứ tự, exact hash/permit/owner/worker, không URB đã thử hoặc pending. Mỗi snapshot chỉ 20 EP0, trong một session transaction; bank selector là writes duy nhất thay bank. Consume/audit/current/deadline đều được kiểm trước dispatch. Lỗi I/O làm bank UNKNOWN và dừng trước Bulk, không restore/retry thêm. Snapshot tách intent, dispatch và full-length completion; record chỉ đúng stage hiện tại. Native receive window không bị trừ thời gian monitor, đường OFF giữ nguyên baseline.
- **Lệnh Windows/cwd repo:** Chạy wrapper focused `:drivers:testDebugUnitTest --tests '*Tbs5530SonyTsOutputReadbackTest' --tests '*Tbs5530TsSmokeTest' --tests '*Tbs5530ControlProbeTest' :app:compileDebugJavaWithJavac --rerun-tasks --console plain --no-daemon --max-workers=1`, PASS 37 tests ở thời điểm đầu. Lệnh cuối `.\gradlew.bat testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest --console plain --no-daemon --max-workers=1` PASS trong 3 phút 46 giây, 361 tasks; 578 JVM tests không failure/error/skipped. Không nâng hoặc cài dependency/toolchain.
- **Kiểm Android và helper:** Android CLI cài cập nhật app/test bằng `install --device=<verified-serial> --install-options=-r`, không clear data. `adb -s <verified-serial> shell am instrument -w -r -e class info.martinmarinov.dvbdriver.Tbs5530AutonomousApprovalTest,info.martinmarinov.dvbdriver.Tbs5530AutonomousLaunchLifecycleTest info.martinmarinov.dvbdriver.test/androidx.test.runner.AndroidJUnitRunner`: lượt đầu có 3 lifecycle failures/24 khi Dozing và report FOREGROUND_LOST; sau user mở khóa, 24/24 PASS trong 5,344 giây. Helper Sony scope 8/8, exact layout, launch 57 và heartbeat 82 PASS trên cả PS7/PS5.1. Kiểm ba release class jars không chứa Sony monitor/ControlProbe/TsSmoke debug.
- **Consent và bootstrap:** Người dùng duyệt một phép đo Sony hữu hạn, biết caveat read81 trên parallel, rồi xác nhận ngắt nguồn ít nhất 5 giây/đèn tắt và gắn lại cùng tablet/cáp/nguồn/RF IN. Giữ một physical epoch, không mint lại sau fail. Firmware input lấy từ bản app-private đã staging, kiểm 8192 byte/SHA trước dùng. OS permission cần OK thủ công; preflight hết cửa sổ trước grant không I²C/URB. Một bootstrap full 132 trong 2834 ms, sau đó terminal ATTACH_PERMISSION_UI_TIMEOUT đã drain; không upload lần hai.
- **Handoff và routing:** Operational không FirmwarePath/A0, cùng epoch và scope. Một preflight bị token cũ vì Activity nằm dưới workflow đã drain; sửa helper CLEAR_TOP + SINGLE_TOP, bổ sung AST test trên PS7/5.1. Một preflight khác hết hạn trước OK thủ công; vẫn chưa grant hoặc ghi USB mới. Sau OK của user, preflight mới và operational grant chạy được; không CLEAR_TASK, reconnect, nới 1500 ms/heartbeat 2 giây, reset hoặc rearm ledger.
- **Phép đo thật:** Hai snapshots AFTER_LOCK/BEFORE_FIRST_URB đủ 20/20 mỗi lần, thời gian 619,174461 ms và 587,510231 ms, sync26 trước/sau và restore bank20 full-length. Cùng bảy byte `C4=29,C6=01,80=28,81=00,95=15,A9=00,33=01`, khớp init. Tổng 230 EP0; bỏ 40 monitor controls còn 190 records giống byte-exact cả IN/OUT với baseline B. Alt0 OFF/API0. Tám submit/tám REAP đều -75/actual 0, syscall return/errno=0, sample 0 byte. Report terminal REPORT/USB_RECEIVE_FAILURE, outstanding=0/quarantine=false và DRAIN_CONFIRMED.
- **Artifacts và cleanup:** Bundle `phase31-sony-output-20261110-161951-5b3ce827` dưới D:\TSDuck\logs và reports. `Collect-Sony.ps1` chỉ xuất sau terminal/drain; 16 raw files Android-host hash khớp. `Analyze-Sony.cjs` kiểm exact groups/selector/read list, đối chiếu 190 baseline và tám completions; PASS. EP0 SHA `39240893ee7e0752caf7ad9d24c3b4c5b270b0da5bf72927238618f377bd65e4`; native `6b8aeb8dd34421857067393af8176acc1b01ceb35fda821ec5f0e8176ecb67bb`. Đã Back rời workflow; app còn process nhưng không USB fd. Không receive thêm hoặc chuyển host.
- **Giới hạn và kế tiếp:** Snapshot NONATOMIC, bank PLAN_INFERRED; không có đọc trong lúc URB pending. Read81 đã thực hiện không làm mất lock trong lượt này nhưng không chứng nhận side-effect-free. C3/96/97/D9, actual PLP/L1 và tín hiệu chân/clock/FIFO vẫn NOT_AVAILABLE. Giữ Phase 3.1, ROOT_CAUSE_UNRESOLVED và chưa TS_SMOKE_PASS. Chỉ đề nghị đối chiếu Windows raw hiện có trước bất kỳ thử nghiệm drive/GPIO/owner mới.
- **Index và persistence:** `gitnexus analyze --index-only` exit 0/102,8 giây; warnings flow/scope còn giới hạn, MCP context không tìm được symbol monitor. Không force/repair hoặc coi graph là chuẩn. Hai checkpoint AgentMemory riêng cho phần mềm và runtime đã lưu; save response có canonical project/author đúng, recall lọc theo project trả đúng hai record và agentId nhưng adapter không trả trường project để đọc lại độc lập. Không tạo duplicate để kiểm metadata. Hồ sơ, source và raw là bằng chứng chính. Không commit/stage/push, lưu raw/firmware/grants vào Git hoặc thao tác VM/snapshot.

## 2026-11-10, phiên 18:02 — Đối chiếu raw Windows với Sony readback Android

- **Baseline:** Đọc sáu hồ sơ và instructions liên quan; kiểm branch/HEAD vẫn `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`. Bốn reference SHAs không đổi; không fetch/sync hoặc sửa references. Lưu backup byte-level sáu hồ sơ trước sửa vào bundle mới. Giữ mọi edits có trước, không stage/commit/push hoặc đổi toolchain.
- **Mục tiêu và scope:** Tiếp tục hướng đã đề xuất bằng cách so raw Windows có payload với hai snapshot Sony Android. Chỉ đọc file; không ADB/USB, firmware transfer, capture mới, tuner open hoặc đổi host. Không dùng consent monitor trước làm quyền hardware mới. Không cần VM/SSH hoặc GitNexus graph cho tác vụ này.
- **Lệnh Windows/cwd repo:** Kiểm `C:\Program Files\Wireshark\tshark.exe --version/--help`, xác minh 23 trường qua `-G fields`. Chạy TShark `-r <cold2.pcap> -Y 'usb.bus_id==3 && usb.device_address==3 && (usb.transfer_type==2 || frame.number==1650 || frame.number==1659)' -T fields -E header=y -E separator=/t -E occurrence=f` với danh sách fields của decoder. Hash input và byte-level pcap proof được chạy lại bằng thư viện chuẩn Node.js v26.7.0, không cài gói.
- **Kiểm công cụ:** `node --test .\tools\ts-state\Decode-TbsUsbState.test.cjs` PASS 21/21. Script `Compare-WindowsSony.test.cjs` ngoài Git PASS 17/17: pre-write/same-value reads, lock cutoff, in-flight write, bank UNKNOWN, reset epoch, Android owner/clock/pointer/encoding và nanosecond delta. `Compare-WindowsSony.cjs` kiểm 17 targets/69 provenance rows; `Build-Boundary.cjs` kiểm firmware identity/epoch và các vendor requests. Không build APK hoặc chạy lại JVM/Android/hardware trong lượt này.
- **Đầu vào:** Cold2 pcap 32.202.128 byte/SHA `70fced47614ff8ec17c9e78f79fca16408c23e6bcadcfb6ad25a4c69cd7f9ded`. Android EP0/native/report được hash lại. Decoder mới có 6751 controls, không warnings; chỉ xuất cặp Bulk đầu, không dùng tập này tính toàn stream. Byte-level proof tái xác nhận hai Windows image epochs cùng firmware hash; Bulk đầu submit tại frame 1650, complete tại frame 1659, status=0/actual=8192; requested vẫn UNKNOWN.
- **Phát hiện mới:** Bảy Windows last-writes C4/C6/80/81/95/A9/33 là `29/01/28/00/15/00/01`, khớp Android. Windows không có snapshot đủ bảy byte sau lock; `C6=00, 80=3F, 95=3D, 33=01` đều là reads trước write cuối. Không gán chúng thành final-state mismatch. C4/A9 có read sau write nhưng trước lock. C3/D9 cùng writes chưa readback; 96/97 và requested PLP còn khác writes, không chứng minh nguyên nhân.
- **Ranh giới FX2:** Windows có 265 vendor request `0x81` trả byte `00`, là software bit IRAM `20.7` chứ không FIFO flags; không có Bulk EP81 trước Bulk EP82 đầu. Pointer request `0x90` tại frame 1649–1657 cắt qua Bulk submit 1650, không là snapshot hoàn tất trước Bulk. Các sequence hai A0 images/B7/04xx vẫn khác Android, nhưng static async/WORDWIDE/PORTACFG không thay cho pinmux/wiring/runtime readback. Không replay, đổi clock/drive/FIFO hoặc thêm delay. Hai tìm kiếm public không bổ sung schematic chính hãng đủ tin cậy; không dùng kết quả manual tổng hợp để điền mapping.
- **Kết luận và kế tiếp:** Không có production fix được hỗ trợ bởi đối chiếu này. Giữ Phase 3.1/ROOT_CAUSE_UNRESOLVED; TS Android 0 byte là kết quả lịch sử gần nhất, không chạy lại. Chỉ chuẩn bị kế hoạch quan sát FX2/host có nguồn và consent mới; nếu chưa chứng minh được cơ chế đọc thì hardware BLOCKED, không tạo request A0/GPIO hoặc bỏ guard.
- **Artifacts/persistence:** Bundle `phase31-windows-sony-20261110-180339-9e246085` dưới D:\TSDuck\logs và reports có verified inputs, TShark args, raw proof, comparison/boundary JSON, CSV và report. AgentMemory save trả success với ID/project/agentId đúng, nhưng hai lần recall trả record lịch sử khác nên chưa xác nhận độc lập readback mới. Memory prose còn vài lỗi khoảng trắng; bản báo cáo và JSON đã rà là chuẩn, không sao chép nguyên văn Memory sang hồ sơ. Không tìm được API sửa nội dung theo ID an toàn; không duplicate hoặc can thiệp storage. Nếu lỗi định dạng tiếp tục xuất hiện, cần kiểm session/transport logs, chưa có căn cứ quy nguyên nhân cho model/proxy/editor.

## 2026-11-10, phiên 18:46 — Kiểm khả năng và chuẩn bị quan sát FX2/host

- **Baseline:** Đọc sáu hồ sơ và toàn bộ instruction VirtualBox trước planning/discovery; branch/HEAD giữ `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`. Bốn references chỉ đọc, SHAs không đổi. Backup sáu hồ sơ trước sửa; giữ source và edits có trước. Không stage/commit/push, package change hoặc sync.
- **Phạm vi:** Người dùng yêu cầu tiếp tục chuẩn bị và báo việc cần làm. Chỉ cho read-only feasibility/offline; chưa có grant cho capture, USB ownership, firmware, module/filter hoặc tracing mới. Người dùng xác nhận tuner đang rút khỏi mọi host và có thể dành 10–15 phút khi cần. Không coi câu trả lời này là xác nhận LED/power-off hoặc consent live.
- **Nhánh firmware:** Agent độc lập rà đủ 11 handlers và helper, hash input 8192 byte/firmware đúng; 64 tests offline PASS bằng Python 3.12.2 với `-B`, không cài gói. A0 RAM hardware contract không chứng minh đọc peripheral E6xx; B8 tiêu thụ IRAM state và 90/92/93→91 là I²C staging. Kết luận BLOCKED_FOR_LIVE_REGISTER_READ, không tạo request hoặc firmware. Report JSON/Markdown SHA đã kiểm lại; parent không đọc lại source scope được giao.
- **Lệnh host chỉ đọc:** Dùng full path VBoxManage cho `--version`, `help list`, `help showvminfo`, `help controlvm`, `list vms/runningvms/usbhost/extpacks` và `showvminfo --machinereadable`. Bản thực tế 7.2.22r175245, Ubuntu-24.04 running/xHCI, EHCI off. Không snapshot/start/reset/shutdown/modifyvm/usbattach hoặc usbfilter mutation.
- **SSH chỉ đọc:** `ssh -T -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=10 ubuntu-VMS` chạy các nhóm identity, lsusb/sysfs/lsmod, tool help, event formats, trace/filter state, file hashes/vermagic và disk/process inventory. Xác nhận UID 0, Ubuntu 24.04.5/kernel 6.8.0-149-generic/x86_64, không thấy target. Không đọc trace stream, modprobe, insmod, mount, capture hoặc staging guest files. M late board/source/receiver hashes khớp bản đã pin, module chưa load.
- **Windows tooling:** TShark/Dumpcap 4.6.9, USBPcap binaries/driver 1.5.4.0 Running. Native help ban đầu không hiện output; dùng ProcessStartInfo với timeout 10 giây thu stdout/stderr thì help/extcap query exit0. Direct `USBPcapCMD --extcap-interfaces` trả 5 control interfaces, trong khi dumpcap liệt kê 15 interfaces không có USB extcap; không kết luận driver hỏng hoặc reinstall. Extcap-config cho biết unsigned snaplen và GUI capture-all default true. Process không elevated, chưa test capture open hoặc UAC, không sửa Registry/service/driver.
- **Guest tooling:** tcpdump 4.99.4/libpcap 1.10.4, trace-cmd 3.2.0; usbmon module file có nhưng chưa loaded. Event formats có xHCI giveback status/length/actual/endpoint/slot, song print fmt không in status. Kế hoạch phải giữ trace.dat và raw `report -R -N -t` trong instance riêng; không sửa global tracing hiện có. Có đủ disk trong snapshot chỉ đọc, không cleanup guest hoặc package.
- **Phương án chuẩn bị:** Giữ M late, K36/manual PLP0/xHCI/source/module/queue/timing; chỉ đề xuất Windows USBPcap + guest usbmon/raw xHCI trong cùng lượt. Snaplen dự kiến ít nhất 131072, observer 90 giây/64 MiB, module session/receive/heartbeat giữ giới hạn cũ. Chưa map target hub/address vì tuner rút; filter qua re-enumeration và owner qua BDA/VM phải được chứng minh, không dùng địa chỉ cũ hoặc filter thường trực để tiện.
- **Kiểm policy:** Tạo plan JSON và policy/test offline ngoài Git, không executor. Lượt đầu FAIL 1/35 do thiếu field operational còn qua gate; đã sửa và giữ log. Sau bổ sung strict denials/caps/hash/epoch, cuối PASS 36/36, không fail/skip. Mọi missing observer/coverage/clock/bytes vẫn BLOCKED hoặc INCONCLUSIVE, không được đổi thành zero/FIFO-empty/Android success. Đây không phải end-to-end observer test.
- **Tồn tại và việc người dùng:** Observer coordinator/STOP/watchdog/cap thực, target-filter proof và fake integration chưa triển khai. Trạng thái PREPARED_NOT_AUTHORIZED_NOT_EXECUTABLE. Người dùng chỉ giữ tuner ngắt, VM chạy, cáp/nguồn/coax/RF IN sẵn; chưa power-cycle hoặc cắm. Sau các gate còn thiếu mới xin exact consent cho UAC/handoff/temporary filter/usbmon, một bootstrap và receive hữu hạn. Không hardware result mới hoặc ROOT_CAUSE_CONFIRMED.
- **Artifacts/cleanup/persistence:** Bundle `phase31-observation-ready-20261110-185020-78ccd455` dưới D:\TSDuck\logs/reports có private preflight, source review, plan/policy/tests và report. Các SSH/help queries đã kết thúc; không có observer mới cần dừng, VM/module/source/ledger cũ giữ nguyên. Memory mới được soạn/đọc lại trước save; save có đúng canonical project/agentId, recall trả đúng record/content/author. Adapter recall vẫn không hiện project độc lập, không tạo duplicate để thử. Không dùng lại nguyên văn checkpoint spacing lỗi trước đó.

## 2026-11-10, phiên 19:32 → 2026-11-11 00:28 — Triển khai executor và capture trước runtime

- **Baseline/phạm vi:** Người dùng yêu cầu tiếp tục đến hết kế hoạch và báo cắm khi thật sự sẵn sàng. Đọc hồ sơ/scoped instructions, giữ branch/HEAD `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963` và mọi edits cũ. Backup sáu hồ sơ dưới canonical bundle `phase31-layered-20261110-193945-623f1cf1`; 31 non-record source files cũ đã hash đối chiếu không đổi. Không stage/commit/push, sync references hoặc sửa Android/toolchain.
- **Consent:** Có exact scope một phép đo: UAC, new devices đúng một Windows hub và không USB khác, temporary filter đúng 734c:5530, usbmon/trace instance riêng, một firmware 132 giao dịch đúng hash nếu default rồi operational không A0. Giữ M late K36/manual PLP0/8×65536, observer 90 giây/64 MiB, receive 10 giây/16 MiB và heartbeat 2 giây. Không GPIO/B7/04xx/LNB, driver/controller/reset/snapshot. Physical preparation và epoch lưu ngoài Git, không coi địa chỉ cũ là attachment mới.
- **Triển khai:** Parent thêm coordinator/grant, live adapter, GuestMeasuredRun, persistent GuestChannel và hai parser; hai agents độc lập sở hữu Windows và guest observers/tests. Tất cả nằm trong [tools/observation](../../tools/observation/README.md), raw/firmware/grants/logs nằm D:\TSDuck. Module M/source/receiver giữ nguyên binary/hash cũ, không rebuild hoặc thay USB payload.
- **Tests trước live:** Windows cuối 192 checks/25 worker processes PASS và thêm 6 startup workers dưới tải PASS; guest Linux 55 checks PASS sau sửa fake watchdog/process cleanup. Parent mới nhất 83 coordinator/grant, 19 process integration, 28 adapter, 61 channel, 19 parser checks và 13 runtime guard checks PASS. `problems` không lỗi, `bash -n`/PS AST và `git diff --check` đạt khi kiểm; chưa chạy lại JVM/Android/Gradle vì không đổi source đó.
- **Sửa orchestration:** `ConvertFrom-Json -DateKind String` tránh UTC-Z thành DateTime rồi parse local; giữ context bằng hashtable thay `.GetNewClosure()` trong live port. Windows status API trả JSON string cần parse. Startup health barrier và hai khái niệm cadence 200 ms/freshness 2000 ms được tách, không tăng heartbeat hoặc che gaps. Terminal/consume luôn create-new, không replay; runtime start chưa chắc chắn vẫn đi STOP/drain.
- **SSH channel:** Các short SSH timeout được giữ log. Thêm protocol lâu dài: sequence, LF, trả lời trong 1500 ms, chỉ host input gia hạn, EOF/fault STOP, không arbitrary command/eval. Trace Linux fixture chỉ ra `read -t .1` có thể nhận id `5` của sequence 57 khi dòng bị chia gói; sửa đọc trọn dòng và regression `PU`/`LSE` cách 200 ms. Test strict SSH thật có 80 PULSE PASS, 45–707 ms, trung bình 86,4375 ms. Command hữu hạn đóng stdin/SSH `-n`; child EOF và 16 read-only SSH PASS, tối đa 3065 ms. Không chứng minh mọi timeout cũ cùng nguyên nhân.
- **Các lần bị chặn:** Canonical run có UTC/health startup refusal trước tuner. Run 234746 đã ARMED nhưng cắm muộn sau cửa sổ; không firmware/receive và cleanup true, sau đó người dùng rút/ngắt nguồn mới. Runs 235450/235716 timeout admission/create-run, chưa hardware. Run 001852 timeout stage-firmware/cleanup, terminal ban đầu cleanup false được giữ; read-only hậu kiểm xác nhận link firmware/module/usbmon vắng, không ghi đè terminal để biến thành PASS.
- **Run 002355 thật:** Sau sửa stdin và source freeze, helper UAC đã ARMED. Chỉ lúc đó mới báo người dùng cắm đúng cổng; default admission đúng ở host/guest. Host pcap có 28 frames/14 controls, 2178 byte, 0 Bulk, không non-target descriptor. Guest dừng OBSERVER_NOT_READY trước runtime; phase rỗng, firmware_verified=false/operational_attempted=false. Guest usbmon có 1234 byte, trace.dat 2049067 byte; raw export/report exit 0/stats đầy đủ và không lost được báo. Đây không phải phép đo TS âm tính.
- **Cleanup/recovery:** Helper 002355 xác nhận cleanup true, own recorder/process/module/firmware hardlink/filter/usbmon đã dọn, trace instance đã gỡ sau export. Đã scp evidence sau STOP về `guest-diagnostic`, không lặp hardware. Người dùng tiếp tục xác nhận rút khỏi mọi host, ngắt nguồn ít nhất 5 giây/đèn tắt và giữ rút; tạo epoch mới đúng sự kiện xác nhận, read-only guest trả CLEAN_DISCONNECTED. Agent guest đang tìm nguyên nhân startup từ raw/log và sửa fixture, chưa rearm.
- **Lệnh/nguồn:** Windows PowerShell tại repository chạy scripts `Test-LayeredObservation`, `Test-ProcessIntegration`, `Test-LiveAdapterContract`, `Test-GuestChannel`, `node --test` hai parser, Bash syntax check, VBox `--version/list runningvms/showvminfo/list usbhost`, strict SSH/scp hữu hạn. Full arguments/exit codes ở từng run. Bốn `git -C <reference> rev-parse HEAD` không đổi; không fetch. GitNexus index trước tools mới không dùng làm bằng chứng; source/AST/tests/hash/raw là chuẩn.
- **Checkpoint:** ADR-039 và năm hồ sơ còn lại ghi đúng phạm vi, chưa hoàn tất phép đo firmware/receive. AgentMemory checkpoint `mem_mwaydzxv_b0d7dea9e260` có project/agentId trong payload/save response; recall trả đúng record/content/author, không projection project độc lập. Giữ ROOT_CAUSE_UNRESOLVED và Phase3.1. Bước tiếp duy nhất là sửa/kiểm guest readiness trước grant mới và chỉ báo cắm sau ARMED.

## 2026-11-11 00:49 — Chẩn đoán riêng startup, không firmware hoặc tuner runtime

- **Lý do và quyền:** Raw cũ đã mất metadata instance sau cleanup nên chưa biết gate. Sau read-only/source review vẫn thiếu bằng chứng, người dùng duyệt riêng một lượt startup-only với caps cũ và temporary target filter/usbmon. Không dùng full-runtime consent để bỏ ready. Thêm grant `startup_diagnostic_only=true`, hai quyền firmware/receive false; main không stage firmware, tạo runtime permit hoặc gọi M, hai runtime seams từ chối độc lập. Kiểm mới 88 grant/coordinator, 31 adapter, 61 channel checks PASS.
- **Instrumentation:** Agent guest thêm `startup-barrier.txt` trước cleanup, không đổi các gate/timeout hoặc bật global tracing. Có 34 portable và 30 Linux targeted checks PASS trên hash instrumentation; bộ đầy đủ 55 tests trước đó vẫn là lịch sử, không coi là đã chạy lại. Read-only Linux v6.8 source giải thích expanded buffer và metadata trace.dat, chưa đủ fix. Không snapshot hoặc hardware ở nhánh phân tích.
- **Run 004925:** Helper UAC đã ARMED rồi mới báo cắm. Main dừng GUEST_OBSERVER_EXITED_BEFORE_READY; guest terminal OBSERVER_NOT_READY, phase rỗng, firmware_verified=false/operational_attempted=false. Diagnostics thu tự động sau STOP. Snapshot ghi INSTANCE_BUFFER_SIZE_MISMATCH, buffer 67 KiB, clock [mono], pcap header hợp lệ, cả hai child còn sống. Hai URB filters giữ target predicate và thêm `common_pid!=122784`; raw event filters none. Trace wrapper PID khác PID loại trong filter nên phải kiểm ownership, không nhận mọi PID.
- **Raw và cleanup:** Host pcap 2178 byte/SHA `b5d2d7f9619a6b652c2f1b3ccaee2bbc2ec690ab47c3db6a4585938b62d2e102`; 28 frames, 13 GET_DESCRIPTOR và 1 SET_CONFIGURATION, vendor/A0/Bulk đều 0. Guest snapshot/raw/health/formats có manifest riêng. Cleanup true, own instance/filter/usbmon đã gỡ. Người dùng xác nhận rút/ngắt nguồn ít nhất 5 giây/đèn tắt và giữ rút; physical epoch mới chỉ sau actual response. Read-only hậu kiểm không firmware file/hardlink, không runtime permit/module/tuner; M và receiver hashes không đổi.
- **Kết luận tạm:** Đã xác định gate sai khác, chưa có startup fix hoặc firmware/receive result mới. Agent guest đang xác minh page rounding và bộ lọc trace-cmd rồi kiểm fixture; không retry hardware, tăng timeout, sửa controller hoặc suy lỗi FIFO. Giữ Phase 3.1 và ROOT_CAUSE_UNRESOLVED. Logs/reports/raw ở run riêng dưới D:\TSDuck; không commit/push.

## 2026-11-11 01:13 — Hai startup contract fixes có nguồn, regression chưa xong

- **Nguồn và thay đổi guest:** Agent xác minh Linux v6.8 ring_buffer.c/trace.c: trang dữ liệu 4080 byte trên x86_64, page 4096 byte và header 16 byte. Request 65536 byte thành 17 trang, capacity 69360 byte và giá trị báo 67 KiB. trace-cmd v3.2 commit `46952ef9aca9f6deeb9af7a227dfd1d04e147aba` tạo recorder children, thêm PID exclusion AND với target filter. Gate mới chỉ nhận geometry tính đúng và child sống đúng PPID/PGID/SID/exe/starttime; không arbitrary size/filter/PID, không đổi request `-b64`, M hoặc caps. Source/report/hashes ở `guest-ring-filter-fix-report.txt` của run 004925 ngoài Git.
- **Kiểm guest:** Có 35 portable checks PASS và hai lượt Linux focused 30 checks PASS mỗi lượt. Full run một lần exit 124 tại 115 giây; health age 303 ms làm assertion 200 ms fail, cùng ba cases trace-exit/cap/new-xHCI. Giữ raw/log và chưa chứng nhận full PASS. Parent đã yêu cầu đối chiếu contract được duyệt: cadence mục tiêu 200 ms khác hard freshness 2000 ms; giữ M heartbeat 2000 ms/reply 1500 ms, ghi misses riêng và xử lý fault/cleanup bằng focused cases trước full rerun. Chưa hardware hoặc rearm.
- **Parent độc lập:** Runtime terminal chuyển sang sync file tạm rồi rename nguyên tử để channel không đọc JSON dở; có 17 exact-function fake checks PASS cả Git Bash và Linux, không modules/device. M/receiver giữ hash cũ. Một lần channel fixture hụt deadline được giữ; thêm diagnostics rồi dùng Bash built-ins cho clock/đọc JSON nhằm giảm process phụ, không tăng deadline. Sau thay đổi, 61 checks chạy ba lượt PASS; strict SSH fixture có 80 PULSE PASS, 56–303 ms, trung bình 107,15 ms. Không quy mọi độ trễ thành cùng root cause hoặc so hiệu năng có kiểm soát giữa các lượt.
- **Trạng thái:** Người dùng vẫn giữ tuner rút sau recovery xác nhận ở 004925. Chưa firmware/receive hoặc Android test mới; Phase 3.1/ROOT_CAUSE_UNRESOLVED giữ nguyên. Chờ guest regression có kết quả trước source/grant mới. Checkpoint AgentMemory mới lưu đúng canonical project/agentId; không dùng full-suite failure làm tuner failure hoặc tuyên bố kế hoạch hoàn tất.

## 2026-11-11 01:46 — Hoàn tất quan sát nhiều lớp, host BABBLE trước guest cancellation

- **Regression đã đạt trước arm:** Guest full Linux fake cuối có 58 PASS/0 FAIL, exit 0, 156 giây, không process leak. Assertion cadence 200 ms được tách khỏi hard freshness 2000 ms; ba fault cases được kiểm riêng. Hai waits của fake harness thành 10/12 giây sau proof cũ quá ngắn; runner budget 275 giây là fake-only, không đổi hardware caps. Parent xác minh local hashes khớp report cuối trước source freeze. Giữ tất cả failure và reports cũ, không gọi chúng PASS.
- **Hai attempts chưa tới runtime:** Runs 013443 và 013941 ARMED nhưng không thấy fresh default trong cửa sổ chờ 45 giây, phase rỗng/cleanup=true. Run đầu host có enumeration, guest hiện sau cửa sổ; run sau pcap header-only, target chỉ hiện trên Windows/BDA sau STOP. Không tiếp tục những attachment thiếu coverage hoặc suy firmware ngoài cửa sổ. Người dùng đã xác nhận rút/ngắt nguồn mới ở mỗi recovery; không rearm ledger.
- **Đổi cách báo cắm có consent:** Người dùng chọn thông báo chữ/tiếng bíp ngay trong PowerShell sau ARMED để tránh độ trễ biểu mẫu chat. Thêm typed `local_arm_notice`, có test ordering và fail-closed khi không thể thông báo; không tăng cửa sổ chờ hoặc cap. Main được arm bất đồng bộ và vẫn có watchdog/root deadline. Kiểm cuối 89 grant/coordinator và 32 adapter checks PASS.
- **Run thực:** `phase31-layered-20261111-014626-5c93af62` đã đi cold → operational → FINISHED trong 104567 ms. Cold raw chứng minh đúng một bootstrap 132 A0/8192 byte/hash/order/full lengths; handoff descriptor operational mới cùng epoch, firmware hardlink gỡ và A0 count=0. Giữ M late/source/module/receiver, K36/manual PLP0/xHCI/8×65536. Không driver/controller/firmware image change, GPIO/B7/04xx/LNB, reset hoặc snapshot.
- **Kết quả nhận:** Receiver lock status=31, Sony sync=0x26; receive_end là timeout/result=1, sample 0 byte, khoảng 10147,849 ms sau lock. Drain ghi submitted=8/reaped=8/outstanding=0/bytes=0/fault=-110/firmware_calls=0. FINISHED chỉ có nghĩa phép đo đã được ghi, không TS_PASS hoặc root cause đã giải quyết.
- **Host và guest:** Host có 36 Bulk completions: một 0xC0000012 và 35 0xC0010000, actual đều 0. WDK local usb.h:304/339 xác nhận BABBLE_DETECTED/CANCELED. First host Bulk frames 743/744 mất 0,258 ms; cancellations đầu sau khoảng 10032,850 ms cùng clock Windows. Guest usbmon có tám submit requested=65536 và tám cancellation -2/actual=0. Raw xHCI có đủ tám enqueue/giveback, length=65536/actual=0 và urb.status=-115. Không ép IRP/URB 1:1 hoặc ghép clocks.
- **Control-plane và status semantics:** Có 706 EP0 controls ở mỗi phía, setup/payload/order byte-exact và mọi status=0; terrestrial 8A/0A00 thành công, còn 335 controls sau lỗi host đầu. Nguồn upstream Linux v6.8 SHA `e8f897f4afef0031fe618a8e94127a0934896aba` cho thấy trace chép urb.status trước USB core đặt completion cuối, nên không lấy raw -115 thay usbmon -2. Không gọi host BABBLE là proof tín hiệu điện hoặc FIFO-empty.
- **Coverage/integrity:** Các recorder ready, snapshots PASS, target anchors đúng, không truncation/unpaired/lost/drop được báo. Operational guest tcpdump 1434 packets/drop=0; raw export/stats đủ. Guest conservative terminal flags được gán cứng chờ parent review; agent sở hữu source đã xác nhận semantics, parent ghi coverage review riêng và giữ terminal gốc. Có 74 file guest hash đối chiếu với bản Windows, tất cả khớp. Report parser mới có bốn synthetic assertions, hai parser trong repository có 19 tests PASS.
- **Cleanup và người dùng:** Hai phase cleanup=true, own modules/filter/usbmon/firmware hardlink/trace instances đã gỡ sau drain. Strict SSH hậu kiểm không tài nguyên còn lại; VM running/xHCI giữ nguyên. Đã báo có thể rút sau cleanup và người dùng xác nhận rút; hậu kiểm CLEAN_DISCONNECTED. Không có hardware helper/capture còn chạy và không mở phép thử tiếp theo.
- **Lệnh/records:** Windows PowerShell tại repository dùng helper/grant đã pin, `node Analyze-LayeredCapture.cjs`, report script offline, SHA-256 giữa hai bản và strict SSH/VBox chỉ đọc. Full arguments/exit codes giữ trong từng run. Parent cuối có 89/19/32/61/17 checks và parser 19 tests PASS; không chạy lại JVM/Android/Gradle. Báo cáo `layered-observation-report.md`, JSON review, timeline, sanitized xHCI và manifest ở bundle cùng run trong D:\TSDuck. Sáu hồ sơ cùng ADR-040 cập nhật, không stage/commit/push hoặc sync references. Giữ Phase 3.1/ROOT_CAUSE_UNRESOLVED; bước tiếp được đề xuất chỉ là đối chiếu offline với Windows BDA đã có TS.
- **Persistence cuối:** Memory `mem_mwb2ba5u_a9976ddf0b93` và Lesson `lsn_f020049ecdbca4db` được server xác nhận với đúng canonical project; Memory có agentId copilot-agent-host. Expand theo ID trả đúng nội dung/author, projection không hiện riêng project nhưng save response có metadata đúng. Không ghi thêm bản trùng để thử. Focused checks cuối và whitespace/prose review được lưu trong bundle; raw terminals không sửa.

## 2026-11-11, phiên 10:11 — Đối chiếu offline BDA cold2 và VM 014626

- **Phạm vi/baseline:** Người dùng duyệt hướng so file đã có, không hardware. Đọc sáu hồ sơ cùng scoped instructions, giữ branch/HEAD `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963` và mọi edits cũ. Bốn reference SHA đọc lại không đổi. Tạo bundle `phase31-bda-vm-offline-20261111-101351-3dad820b` dưới D:\TSDuck\logs/reports; backup sáu hồ sơ. Không SSH/VBox/USB/ADB/capture hoặc thay toolchain/runtime.
- **Input integrity:** Hash BDA cold2 32.202.128 byte và VM cold/operational 014626 khớp raw đã ghi; TS BDA cùng phiên 30.897.800 byte/hash khớp. Không sửa hoặc copy raw vào Git. Trace bus/device chỉ dùng theo scope file, không suy owner hiện tại.
- **Lệnh đã chạy:** Windows PowerShell tại repository dùng Node v26.7.0 cho parser/report, TShark 4.6.9 `-v/-h/-G fields/-n -r -Y -T fields` và `-V` cho các boundary/URB đặc biệt, TSDuck tsanalyze 3.45-4798 `--help/--json <existing-ts>`. Full arguments/exit codes/stdout/stderr được giữ. tsanalyze JSON mới có 164350 packets, 14 services, 47 PIDs, 3054 TEI và 0 invalid sync; không gọi baseline sạch hoặc dùng PCR bitrate làm throughput.
- **So thực tế:** Cùng firmware hash và operational descriptor/config/alt/EP82/max packet. BDA hai firmware epochs, VM một; không gộp chúng. Tuner init 25 operations và tune 11 operations byte-exact về thứ tự/payload. Cùng manual PLP0 và output last-write chính; drive 96/97 khác 55/54 chưa có causal proof. BDA Bulk đầu 8192 byte/status=0/2,291 ms, VM BABBLE/0 byte/0,258 ms; requested của hai pcap vẫn UNKNOWN.
- **Phát hiện mới:** VM gọi RSSI trước Bulk: năm complete windows rồi một window ngang lỗi; chuỗi đo còn ghi tuner 0x88=04 và mở repeater, đóng lại sau submit đầu 39,378 ms. BDA trước Bulk giữ 0x88=00 sau tune và chưa đo RSSI; đường RSSI sau đó restore 00. Source TBS/CoreELEC/C/Go đều có đường RSSI ghi 04, không là đọc thuần. Repeater reg08 là I²C, không TS-enable.
- **Phản chứng/giới hạn:** BDA có success trong ba RSSI windows hậu Bulk, nên repeater mở không tự chứng minh lỗi. Android Sony-readback cũ không ghi 0x88=04, repeater đóng và vẫn -75; không gọi đây là root cause Android. Queue tại completion đầu là 8 so với 1, nhưng VM lỗi trước khi queue đầy. BDA không thấy pipe reset/SET_INTERFACE/CLEAR_HALT trước Bulk, ABORT_PIPE chỉ ở teardown. VENDOR_DEVICE so với CONTROL_TRANSFER là container API host, không proof wire request khác. ETW targeted khác phiên không điền requested length cho cold2.
- **Kiểm phương pháp:** Parser ban đầu liệt kê hai bank-selects như khác data; đã lọc reg00 selectors và reset state theo A0 cuối, giữ intermediate JSON không làm kết luận. A2 read=00/read=04 tách khỏi last-write=04 giống nhau. Pointer90/frame 1649 pending khi Bulk frame 1650 bắt đầu được giữ riêng. Tuner 0x60 dùng linear-register model có nguồn, không ép bank null của các địa chỉ khác thành zero.
- **Tests:** Có 20 comparison tests mới, 21 decoder và 11 raw capture regressions, tổng 52 PASS/0 FAIL; final scripts và TShark/TSDuck đều exit 0. Synthetic tests kiểm stale read, pending boundary, descriptor malformed, bank/epoch, tuner scope, RSSI windows và requested UNKNOWN. Không chạy JVM/Android/build/lint vì không sửa runtime; kết quả trước giữ đúng phiên.
- **Records/next:** Sáu hồ sơ và ADR-041 ghi bằng chứng/giới hạn, báo cáo `bda-vm-offline-comparison.md`, structured reviewed-v2, source/input manifests và scripts/tests ở bundle ngoài Git. Không stage/commit/push, sync references hoặc thay production. Giữ Phase 3.1/ROOT_CAUSE_UNRESOLVED; chỉ đề xuất kế hoạch M cô lập RSSI khỏi Bulk đầu có lock/heartbeat/deadline và fake guards trước live mới. Không replay B7/040x/double A0, đổi drive hoặc tăng buffer. Không cần cắm tuner lúc này.
- **Persistence/đóng phiên:** Memory `mem_mwbkvsqd_01fdba157d7f` được server lưu với canonical project `git:github.com/trankien84/androiddvbdriver` và agentId `copilot-agent-host`; expand theo ID trả đúng nội dung/author. Projection đọc không hiện riêng project, trong khi save response có metadata đúng; không tạo duplicate. Đã kiểm 10 runtime-source hashes từ manifest live cũ không đổi; dùng raw/source/tests, không GitNexus. Diff-check và review spacing/Markdown mới thực hiện trước closeout có hashes; không xác nhận owner hoặc hardware state mới trong lượt offline.
