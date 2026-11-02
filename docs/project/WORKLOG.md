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

- `Get-Date -Format o`, `[Environment]::OSVersion.VersionString`, `$PSVersionTable.PSVersion`, `git --version` → Windows10.0.19045, PS7.6.9, Git2.56.0.windows.3.
- `Get-Command java,android,git,usbipd,wsl.exe`; `java -version`; `[Environment]::GetEnvironmentVariable` trên Process/User/Machine; `Test-Path $env:JAVA_HOME`; `Get-ChildItem 'C:\Program Files\Java'`. PATH java25.0.5.0.3 hoạt động; JAVA_HOME `jdk-25` không tồn tại, JDK thực `jdk-25.0.5.0.3`.
- `android --help`, `android -V`, `android info sdk`, `android --sdk=D:\Android\Sdk info sdk`, `android --sdk=D:\Android\Sdk sdk list`. CLI1.1.16717390; SDK default process khác User, explicit SDK đúng. Đọc source.properties platform34/37.0, BuildTools36, NDK28.2.13676358, CMake3.22.1, emulator37.2.13; cmdline23 qua list. ANDROID_HOME/ANDROID_AVD_HOME thiếu Process nhưng đúng ở User; không sửa biến.
- `D:\Android\Sdk\platform-tools\adb.exe --version` → 37.0.1. `emulator.exe --version` chỉ in thông tin kèm `No AVD specified`; truy vấn đúng sau đó `emulator.exe -version` → 37.2.13.0. Không truyền AVD/boot; D:\Android\AVD rỗng, default avd directory không có.
- `android docs search --help`, `android docs search "USB host UsbDeviceConnection controlTransfer bulkTransfer requestPermission"`, `android --sdk=D:\Android\Sdk docs fetch kb://android/develop/connectivity/usb/host` lọc nội dung cần thiết. CLI tự tải KB index/cache lần đầu; không cài/nâng package SDK hoặc gửi source dự án. Các thông báo JVM UseAllWindowsProcessorGroups được ghi là warning, không biến thành build failure.
- `wsl.exe --list --verbose` → Ubuntu-22.04 Running/v2. `usbipd` thiếu PATH, dùng executable có thật `C:\Program Files\usbipd-win\usbipd.exe --version` và `list` → 5.3.0-54; TBS VID:PID734c:5530 BUSID đọc live6-2, **Shared**, không Attached; quyền phía Windows. Không chạy lsusb/PyUSB hoặc thay ownership; không xác định process Windows giữ handle.

### MCP, nguồn web và kiểm thử

- Qua Gateway: retrieve_tools → call_tool_read `gitnexus:list_repos`, `adb-mcp:list_avds`, `agentmemory:memory_recall` (query TBS5530 AndroidDvbDriver, compact, limit3, budget1000). Công cụ đáp ứng; GitNexus thiếu index dự án; AVD list rỗng; memory matched_count0. Không analyze index, lưu memory, bật emulator, cài APK, đọc log cá nhân hoặc đổi Proxy config.
- session_store_sql: query sessions trong 7 ngày theo repository identity → 0 dòng. Không coi đây là bằng chứng không có bất kỳ lịch sử nào.
- Web official: Gradle releases/compatibility8.7/current; AGP API reference + release notes8.5/8.10/8.13/9.0/9.4/9.5; Android JDK/NDK/ABI/16KiB/behavior36 docs. Research ghi URL, phiên bản thực nhận và xung đột landing AGP/summary search; không suy diễn stable theo ngày.
- **PASS có phạm vi:** Git identity/source objects, workspace resolution, sync syntax, SDK inventory, WSL listing, USB visibility/ownership listing, MCP read-only calls, source analysis.
- **FAIL:** TBS checkout thực thiếu/index thiếu; JAVA_HOME không tồn tại; SDK process/default lệch; workspace ignored (khả năng bàn giao qua Git chưa đủ).
- **BLOCKED:** clean baseline, Gradle runtime dự kiến, GitNexus project index, cam kết không fetch ngầm toàn phiên.
- **NOT RUN:** Gradle/unit/lint/assemble/instrumentation/native ABI/16KiB, APK/UI, WSL USB attach/claim lại, firmware/hash/license/upload, vendor/I2C/chip ID/DVB-T2 lock/MPEG-TS thật. Bằng chứng WSL/PyUSB 2026-11-01 giữ nguyên, gắn rõ lịch sử.

### Hồ sơ và bàn giao

- **Chỉ cập nhật:** STATUS.md, ENVIRONMENT.md, RESEARCH.md, DECISIONS.md, WORKLOG.md, TEST-MATRIX.md dưới docs/project; bảo toàn toàn bộ nội dung lịch sử, thêm snapshot mới. Không tạo markdown kế hoạch ngoài sáu hồ sơ đã được yêu cầu.
- **Quyết định:** ADR-004 (bảo toàn checkout và đọc blob), ADR-005 (đề xuất baseline JDK17/process rồi migration nhỏ), ADR-006 (đề xuất userspace một TS, DVB-T2 trước); xem DECISIONS. Chưa chấp thuận thực thi migration/hardware.
- **Blockers:** index/source TBS; environment stale/JAVA_HOME; JDK17/package baseline chưa có; main hồ sơ untracked/workspace ignored; firmware provenance/license/hash; Android vật lý và hardware gate chưa chạy.
- **Next single action:** soạn và xin duyệt một kế hoạch khôi phục index/sparse checkout TBS, nêu cách bảo toàn metadata/trạng thái và tiêu chí kiểm lại; **chưa chạy lệnh khôi phục**. Sau đó mới xin phạm vi baseline build; không tự tiếp Phase1.
- **Branch/HEAD sau:** master / e4c13a5b86ad5ef9be887df58226046dcb42dcd5, không commit/push.
- **Kiểm chứng hồ sơ cuối phiên:** `git diff --name-only`, `git diff --cached --name-only` main không có tracked/staged diff; status vẫn 18 untracked như đầu, sáu tài liệu nằm trong số này nên diff mặc định không thể hiện nội dung. Reference HEAD không đổi, TBS vẫn81.399 D/index thiếu, C/Go vẫn sạch. Kiểm UTF-8 strict cả6 file PASS, không có relative .md link hỏng, VS Code problems cho docs/project trả `No errors found`. Không có bộ documentation tests riêng được phát hiện/chạy. Chỉ sáu hồ sơ được edit; chưa đăng ký version-control bằng commit.
- **Bổ sung trace trực tiếp:** đọc registry/DvbUsbDevice, permission/I2C/frontend/tuner contracts, generic bulk so EP0 RTL, Sony Cxd2841er và Mygica/AF9035 patterns, native C/JNI, TS demux/DataPump/app/service/Request. Phát hiện API chưa có PLP và satellite enums; RESEARCH ghi nguồn cụ thể và lifecycle risks, không thay runtime.

## 2026-11-02 12:52 +07 — Kiểm tra JAVA_HOME và chuẩn bị kế hoạch phục hồi TBS

- **Yêu cầu:** bắt đầu bước kế tiếp đã đề xuất (lập/xin duyệt kế hoạch), kiểm lại JAVA_HOME sau người dùng sửa; chưa tự khôi phục reference.
- **Environment:** Windows PowerShell, cwd AndroidDvbDriver; main master/e4c13a5b86ad5ef9be887df58226046dcb42dcd5,18 untracked. TBS latest/2ce787de9f6e81b4294692a45de15596ae2e1322.
- **JAVA_HOME commands:** GetEnvironmentVariable cho Process/User/Machine, Test-Path home/bin/java.exe, Get-Command java, java -version, gọi java.exe Machine trực tiếp. Machine đã đúng C:\Program Files\Java\latest\jdk-25, version25.0.5.0.3; Process còn C:\Program Files\Java\jdk-25 không có thật. Trong child khác, đọc Machine vào `$env:JAVA_HOME` rồi chạy bin\java.exe:exit0; không đổi registry env/global/VS Code process. ANDROID_HOME/AVD User đúng nhưng Process vẫn thiếu.
- **TBS commands:** git --no-lazy-fetch status/rev-parse/branch/remote/sparse-checkout list/ls-tree/cat-file; env GIT_NO_LAZY_FETCH=1/GIT_OPTIONAL_LOCKS=0; kiểm index/index.lock/GIT_INDEX_FILE và file counts. Vẫn81.399D, index thiếu,6dir0file.785 selected blob local,572 cone ancestor blob thiếu. Kiểm1357 materialized paths cơ bản0 tên Windows không hợp lệ.
- **Dry-run đã chạy:** `git --no-lazy-fetch -C <TBS> read-tree --dry-run -m -u HEAD` → exit128 invalid path drivers/gpu/drm/nouveau/nvkm/subdev/i2c/aux.c. `git --no-lazy-fetch -c core.protectNTFS=false -C <TBS> read-tree --dry-run -m -u HEAD` → exit0, index vẫn thiếu. `git config --show-origin --get core.protectNTFS` → exit1/không có stored override. Không dùng command-scoped false để materialize thật.
- **Bằng chứng thời gian:** FETCH_HEAD trước các phép dò có mtime12:50:47+07. Không cho rằng đây là tác động của lượt này. Không có fetch/pull/clone/sync hoặc branch/index/source write reference trong lượt này.
- **Giới hạn mới:** dry-run0 không kiểm chứng đủ blobs để restore; cần đồng ý tải572 ancestors nếu giữ nguyên cone. Invalid AUX path tái hiện được, nguyên nhân mất index ban đầu vẫn chưa biết.
- **Hồ sơ cập nhật:** ENVIRONMENT/RESEARCH/STATUS/TEST-MATRIX/WORKLOG; giữ kết quả cũ. RESEARCH có kế hoạch backup, command-scope workaround, giới hạn tải, gate kiểm chứng/rollback và phương án offline. Không tạo tool/script mới hoặc sửa instruction/build/source.
- **Lựa chọn người dùng:** ask_user trả `prepare-user-recovery`; soạn script tự chạy, không thực thi phục hồi thay người dùng. Đã tạo `Restore-TbsSparseCheckout-Reviewed.ps1` trong session files ngoài repo, giữ references read-only. Có preview mặc định, Apply opt-in+xác nhận, backup .git ngoài repo, kiểm metadata/locks/Windows paths, fetch exact missing blobs không update refs/FETCH_HEAD, read-tree command-scoped protectNTFS=false và postchecks status/identity/file hashes; không reset/clean hoặc hardware.
- **Validation script:** PowerShell Parser.ParseFile0errors; chạy script không tham số: HEAD khóa/1357cone files/572missing, index vẫn thiếu; SHA256 EFC40DE33FB4C202C49FB720DD569CB65A6B234B1F0055BD8CEC270D61AD97F5,13:01:40+07. Không chạy Apply/backup/fetch/restore; không tuyên bố end-to-end PASS.
- **Concurrent metadata:** FETCH_HEAD quan sát12:56:56, khác lần đầu12:50:47 dù lệnh chỉ đọc/no-lazy-fetch. Chưa biết tác nhân; hướng dẫn người dùng dừng Git sync khác trước Apply. Script kiểm quãng yên3giây và trong backup, không thể loại bỏ mọi race nếu tiến trình khác bắt đầu sau đó. Không kill process khác hoặc sửa settings.
- **Next:** người dùng xem script rồi tự chạy preview/Apply theo phạm vi đã chọn, gửi kết quả đã lược thông tin riêng; không agent ghi reference/build/migration/hardware. JAVA_HOME Machine đã đúng, runtime compatibility Gradle8.7/JDK25 vẫn còn.

## 2026-11-02 — Nâng build JDK25 theo yêu cầu mới (bắt đầu13:06+07)

- **Approval:** cho nâng latest stable và sửa tương thích; ask_user chốt preserve-21/local-read-only. Không chạy script reference Apply, không fetch572ancestors. Không hardware writes/emulator/install APK. Quyết định mới ADR-007 thay đề xuất baselineJDK17-first; original build chưa từng được chạy nên không gọi nó PASS.
- **Git:** tạo/checkout nhánh chore/modernize-android-build từ master/e4c13a5b86ad5ef9be887df58226046dcb42dcd5; giữ18untracked gốc, không stage/commit/push. GitNexus list_repos vẫn không index project; dùng source trực tiếp, không analyze.
- **Versions:** Gradle9.8.2 checksum official, AGP9.4.1 POM/release docs, SDK compile37.2/target37/min21, NDK30.0.16248370/CMake4.1.2/BT37.0.0; source-targetJava8 giữ. AppCompat1.7.1/Material1.13.0 là giới hạn min21; test-ext1.3.0/Espresso3.7.0, Mockito5.24.0/JUnit4.13.2. Tài liệu nguồn URLs ở RESEARCH.
- **Môi trường lệnh:** Windows PowerShell7.6.9/10.0.19045, cwd root app; mỗi process set JAVA_HOME từ Machine, ANDROID_HOME=D:\Android\Sdk, ANDROID_AVD_HOME=D:\Android\AVD; không đổi registry/global PATH.
- **Lệnh đã chạy:** android --help/sdk list --help/sdk list --all (lọc catalog), sdk install --help, docs search AGP minorApiLevel; web_fetch official compatibility/POM/metadata/checksum; source read/edit4Gradle modules+root/properties+native config/tests/README/task. Không tải source lên external services.
- `gradlew --version` tải binary distribution9.8.2, verify checksum, exit0 (shell963); Launcher JVM25.0.5.0.3/daemon actualjdk25.0.5.0.3. `gradlew help --no-daemon --console plain --warning-mode all` exit1 NDK not configured (965). Sau manifest changes/failure mới cài4packages bằng android sdk install;966exit0. Đọc source.properties xác minh, meta/platforms.json min21.
- `:app:assembleDebug` đầu967exit1 do thiếu androidx.localbroadcastmanager; thêm direct1.1.0,968exit0 BUILD SUCCESSFUL2m39s/95tasks,4ABIs. runTask editor không tìm task nên dùng terminal cùng command và sửa task env để người dùng dùng lại; không tạo tasks.json mới.
- `:usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest :dvbservice:assembleDebugAndroidTest`971exit1: service13tests có2fail (hardcode newlineWindows và Mockito anyString không match null). Sửa test-only openMocks/MockedStatic/cleanup, isNull matcher, System.lineSeparator; runtime không đổi. XML sau974:drivers17/usbxfer1/dvbservice13,0fail (có1ConvertCString utility trong31methods).
- `wrapper --gradle-version9.8.2 --distribution-type bin --gradle-distribution-sha256-sum <official> --console plain --no-daemon`973PASS; regenerated wrapperJAR/scripts bằng tool chính thức. CRLF generated gradlew.bat bị diff --check default xem CR là whitespace; không thay generated script tùy tiện, kiểm lại cr-at-eol riêng.
- Combined974unit/instrumentation compile/lint/release/bundle exit1 ở native release x86 Unable to delete directory; instrumentation APK đã package,unit xanh,lint/release tổng chưa hoàn tất. Retry977max-workers2 lỗi tương tự x86_64;979 chạy riêng buildCMakeRelWithDebInfo[x86_64] max-workers1 PASS25s. Không xóa output/kill process khác; retry980release/lint tuần tự max-workers1, ghi kết quả khi hoàn tất.
- **APK/native check đã chạy:** zipalign37 -c -P16 4 debug APK exit0; ZIP có libUsbXfer.so arm64-v8a/armeabi-v7a/x86/x86_64; llvm-readelf NDK30 LOAD64bit0x4000,32bit0x1000. Debug SHA256 BC9FD306B79B4CEA044831547F49465F4650040AD1B90553DA509C8476BAB443. Đây là static validation, không runtime16KiB/USB host.
- **References recheck:** SHA cả3không đổi; TBS81399D,index thiếu,C/Go0changes. Không tải TBS objects/thay sparse, không download firmware/images. Toolchain downloads là4SDKpackages+Gradle binary/deps phục vụ chính build4modules, không second SDK.
- **Files:**4module build.gradle,rootbuild.gradle,gradle.properties,wrapperproperties/JAR/scripts,CMakeLists,3service test files,README,.vscode/tasks.json và6projectrecords. Không source runtime/publicAPI/driver implementation hoặc instruction edits.
- **Kết quả cuối980:** `:app:lintDebug :app:assembleRelease :app:bundleRelease --console plain --no-daemon --max-workers=1` **exit0**,3m54s,286tasks(155executed/131uptodate). Native4ABIsrelease hoàn tất. LintXML0errors/11warnings (2dependencyversion có chủ đích API21,9UI/resources), không suppress/baseline/disable gates.
- **Chốt983:** `:usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest :dvbservice:assembleDebugAndroidTest :app:assembleDebug --console plain --no-daemon --max-workers=1` exit0/36s,138tasks(8executed/130uptodate). XML31methods0fail. Regenerated wrapper giảm native-access warning launcher nhờ defaults mới; javac8obsolete/AGPGradledeprecated warnings vẫn minh bạch.
- **Outputs static verification:** releaseAPKunsigned5466380bytes SHA2561D7D415C19A07FD55B1DEDB2FA885C48CD170D8A7802C5077B0E23CB1051C999; releaseAAB4782669bytes SHA256A3E29DA0FBF1F728A9E6477CD59FF6FC1CD2183490610D571CD0AA1DFA06EA93; instrumentationAPK7411924bytes SHA256760B280C897636AC6CB7C4539AA19FE76FE042BBD48D6DAECBE10A9C30D2B641. zipalign releaseexit0; ELF64releaseLOAD0x4000. Merged manifestmin21/target37; aapt2badgingnative4ABIs. Không signingrelease/publish hoặc chạy Android.
- **Rà phạm vi:** diff --check có bậtcr-at-eol PASS cho wrapper.batCRLFgenerated. Wrapper.properties giữ lại license notice cũ sau generator; wrapper scripts mới có Apache2.0noticechính thức. Một lệnh rà thử cast manifest-merger txt thànhXML thất bại do sai định dạng; kiểm manifest XML thật sau đó xác nhậnmin21/target37, không có source changes từ probe.
- **Bàn giao:** STATUS/ENVIRONMENT/RESEARCH/DECISIONS/TEST-MATRIX/WORKLOG vàREADME cập nhật. Chưa commit/push; references giữ read-only/SHA/status như cũ. RuntimeAndroid21/37/UI/permission/service/native-load16KiB/TBS hardware vẫnNOTRUN. Next: reviewdiff và chọn môi trường Android được phép kiểm thử phần mềm không tuner; không tự boot emulator.

## 2026-11-02 18:48 +07 — Checkpoint theo yêu cầu commit

- **Phạm vi người dùng yêu cầu:** chuẩn bị commit để sau đó push origin; tác vụ này chỉ commit local, không push. Message `chore: prepare TBS5530 development with JDK 25`, kèm trailer Co-authored-by: Copilot.
- **Môi trường:** Windows PowerShell/rootAndroidDvbDriver, nhánh chore/modernize-android-build; HEADtrước e4c13a5b86ad5ef9be887df58226046dcb42dcd5. Main34entries(16modified/18untracked), index ban đầu chưa stage. Không sửa references.
- **Remote quan sát mới:** origin git@github.com:trankien84/AndroidDvbDriver.git, upstream https://github.com/signalwareltd/AndroidDvbDriver.git. Chỉ đọc remote-v, không thay remote hoặc fetch/push. Không dùng origin cũ trong audit làm đích push tự động.
- **Review:** status/diffstat, danh sách34file và kích thước; heuristic secret scan0match (không phải chứng nhận security audit); JSONtasks parsePASS; diff --check với cr-at-eolPASS. Không stage firmware/captures/logs/localproperties/APK/AAB/nativeoutput/cache, không force-add ignored workspace. User thay `.gitignore` thêm *.fw/*.ts đã được rà và đưa vào phạm vi commit.
- **Validation đã chạy:** set JAVA_HOME từ Machine+SDKenv trong process; `gradlew.bat :usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest :app:assembleDebug --console plain --no-daemon --max-workers=1` exit0/59s,107tasks(8executed/99UP-TO-DATE); unit tasks UP-TO-DATE chứ không chạy lại mọi test. Không lint/release/hardware/Androidruntime mới; kết quả migration trước giữ nguyên.
- **Hồ sơ chuẩn bị:** thêm checkpointSTATUS/WORKLOG trước commit để bàn giao origin/thời điểm/phạm vi và kết quả kiểm chứng. Hash commit tra gitlog sau thực thi, tránh ghi self-referential SHA vào chính commit. Không thay chỉ dẫn/skills/build ngoài các thay đổi đã có.
- **Tiếp theo:** xác minh commit/index/workingtree rồi báo hash; người dùng có thể push nhánh hiện tại tới fork origin. Workspace file hiện ignored không thuộc commit. Android runtime/native-load/USBHost/TBS vẫnNOTRUN.
