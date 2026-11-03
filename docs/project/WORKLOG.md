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

## 2026-11-02 — Phase 1.1: kiểm thử Android runtime, không tuner

- **Thời gian:** 19:31–20:07 +07. Windows 10.0.19045 / PowerShell 7.6.9, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`.
- **Git trước:** modernization tại `6da609ba49dd9f1f4d284291918f68a6378972ff`; người dùng xác nhận đã push, local ref `origin/chore/modernize-android-build` cũng trùng SHA. Không fetch để kiểm remote live. Có hai thay đổi local trước phiên: `.gitignore` đã thêm `!TBS5530.code-workspace`, workspace chưa tracked. Kiểm hash trước/sau khi `git switch -c test/android-runtime-smoke chore/modernize-android-build`: giữ nguyên cả hai. Không stash/reset/stage/commit/push/merge.
- **Guidance:** đọc copilot-instructions, cả 6 scoped instructions, 2 project SKILL.md và hồ sơ mới nhất. Load project-handoff/android-cli/agent-customization; skills tbs-research chỉ đọc vì không nghiên cứu protocol mới. GitNexus list_repos không có target index; AgentMemory lookup không có match; không analyze hoặc sửa Proxy config. Đọc Android docs qua CLI cho instrumentation và edge-to-edge.

### Môi trường và lựa chọn AVD

- `GetEnvironmentVariable` Process/User/Machine: process vẫn JAVA_HOME cũ không tồn tại, thiếu SDK/AVD env; Machine JAVA_HOME hợp lệ `C:\Program Files\Java\latest\jdk-25`, User SDK/AVD đúng. Mỗi lệnh cần thiết set JAVA_HOME từ Machine và SDK/AVD process riêng, không thay global.
- `android --help`, `android info sdk`, `android --sdk=D:\Android\Sdk sdk list`, `emulator list`, `emulator.exe -list-avds`, ADB `devices -l`, MCP `list_avds`: ban đầu không có AVD hoặc device. Có image `system-images;android-37.0;google_apis;x86_64` cài sẵn. User đồng ý tạo AVD riêng qua ask_user.
- Android CLI `emulator create --help` chỉ hỗ trợ chọn profile, không chọn name/path/image chính xác; dùng avdmanager hiện có. `avdmanager create avd --help` trả lỗi cú pháp rồi in đúng options; không ghi gì. Sau đó `'no' | avdmanager.bat create avd -n TBS5530_Runtime_API37 -k 'system-images;android-37.0;google_apis;x86_64' -p D:\Android\AVD\TBS5530_Runtime_API37.avd -d medium_phone`, exit0; không `--force`, không tải SDK/image, không wipe data.
- Emulator process shell1004: `emulator.exe -avd TBS5530_Runtime_API37 -port 5554 -no-snapshot -no-boot-anim -no-audio -no-window -gpu swiftshader_indirect`. Đợi `get-state=device` và `sys.boot_completed=1` có timeout4phút (1005exit0).
- Runtime từ getprop: Android17, SDK37, x86_64; `getconf PAGE_SIZE=4096`; `emu avd name` đúng AVD đã tạo. `pm list features` không có USB host. Không gắn tuner/emulator USB passthrough. Source.properties image revision7, CLI catalog trước đó báo8; ghi metadata/runtime thực, không suy diễn runtime37.2 từ compileSDK.
- `gradlew --version`: Gradle9.8.2/JDK25.0.5.0.3. Đọc scripts và source.properties xác nhận AGP9.4.1/compile37.2/target37/min21, NDK30.0.16248370/CMake4.1.2/BT37.0.0; ADB37.0.1/Emulator37.2.13. Không nâng version/dependency hoặc cài package nào.

### Build, instrumentation và native load

- Thêm `dvbservice/src/androidTest/.../NativeLibrarySmokeTest.java`: assertion `UsbHiSpeedBulk.IS_PLATFORM_SUPPORTED`, gọi direct `System.loadLibrary("UsbXfer")`; không tạo connection/request hoặc ioctl với fd giả. Thêm androidTestImplementation project(:usbxfer) chỉ cho test compile; không external library update.
- `gradlew :app:assembleDebug :dvbservice:assembleDebugAndroidTest --console plain --no-daemon --max-workers=1`: shell1006 exit0,2m4s. ZIP APK có `lib/x86_64/libUsbXfer.so`,7320bytes.
- `:dvbservice:tasks --all` xác nhận connectedDebugAndroidTest tồn tại. Lệnh filtered hai class1012 exit0 nhưng XML chỉ1 registry test; không tuyên bố native PASS từ exit code. Direct `am instrument`1017 trước reinstall test APK lỗi component missing vì Gradle đã gỡ test package sau run.
- `adb -s emulator-5554 install -r <dvbservice-debug-androidTest.apk>` Success; direct `am instrument -w -r -e class info.martinmarinov.dvbservice.NativeLibrarySmokeTest info.martinmarinov.dvbservice.test/androidx.test.runner.AndroidJUnitRunner`1018 chạy **OK (1 test)**, assertion thành công,0.037s.
- Rerun **không filter**, `$env:ANDROID_SERIAL='emulator-5554'; gradlew :dvbservice:connectedDebugAndroidTest --console plain --no-daemon --max-workers=1`1019 exit0/49s,92tasks(9executed/83UP-TO-DATE). XML `dvbservice/build/outputs/androidTest-results/connected/debug/TEST-TBS5530_Runtime_API37(AVD) - 17.xml`: testsuites **2tests/0failures/0errors**, tên chính xác registry.allDevicesAreInXml và NativeLibrarySmokeTest.usbTransferLibraryLoadsWithoutOpeningUsb. Giữ assertion registry cũ, không disable test.
- Chép XML evidence ra session files/phase-1-1 (ngoài Git), SHA256 `689B2B2DED52D8E0649BACABEE09226C2E00D68CADEA270C2D1000890C35BC17`.

### UI, lifecycle và lỗi tìm thấy

- `adb -s emulator-5554 install -r app-debug.apk`1008 Success; `am start -W -n info.martinmarinov.dvbdriver/.WelcomeActivity`: Statusok/COLD,TotalTime6606ms,PID3840.
- MCP describe_ui/screenshot phát hiện overlay **System UI isn't responding**, focused package com.android.systemui, không phải app. Chọn Wait bằng center từ hierarchy; overlay biến mất. Không kill SystemUI/disable security/đổi OS. Ghi firstboot SystemUI ANR là vấn đề môi trường, không ẩn nó.
- Welcome→Diagnostic→Start mở dialog `No devices found` và stacktrace handled DvbException tại DeviceController:64, PID app vẫn sống, hardware/signal/lock unchecked; Tune/Record không bật. Không có app FATAL/UnsatisfiedLinkError/SecurityException trong log PID đã lọc. Đây không phải tuner test.
- UI trước fix có controls bị system bars che: hierarchy rowTune bắt đầu y0; Close đến y2400. Screenshot xác nhận nội dung/status icons chồng. Thêm `SystemBarInsets.java`, dùng systemBars|displayCutout và padding gốc, chỉ API35+, dark icons; gọi từ WelcomeActivity/DvbFrontendActivity/GplLicenseActivity sau setContentView. Không đổi targetSDK/minSDK/USB/service logic.
- `gradlew :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1`1016 exit0/1m54s,162tasks(27executed/135UP-TO-DATE), lint0errors/11warnings, không suppress/baseline mới.
- Reinstall APK fix1020 Success, amstart tạm trả PackageUpdateActivity (system transition); hierarchy tiếp theo đúng Welcome. Sau fix top inset63px/bottom63px, rowTune y63, controls dưới cùng dừng y2337 trên screen2400. MCP screenshot và persisted before/after PNG xác nhận.
- Test Welcome→License→OK quay Welcome; Diagnostic→Home→amstart đưa task lại Diagnostic, state bình thường; Start lại vẫn no-device dialog; Back đóng dialog/rời Diagnostic về Welcome. `ACTION_VIEW dtvdriver:// -p <app>`1024 mở DeviceChooserActivity rồi finish về Welcome khi không có device, không ép start DvbService. Không có caller để assert returned result code nên không báo coverage contract đầy đủ.
- Close button đưa về launcher; warm relaunch1026 Statusok,TotalTime1796ms,PID6344 sống. Filter log PID cuối0match FATAL/linker/security/appANR; dumpsys exit-info chỉ reason16 PACKAGE UPDATED do install-r, không crash. Không khẳng định mọi log trong toàn emulator không có warning.
- Permission: READ/WRITE_EXTERNAL_STORAGE=false, internet/foreground permissions granted; no-tuner UI vẫn chạy. USB permission **NOT RUN** vì không có USB Host/device. DvbService foreground/streaming **NOT RUN** vì chưa có tuner/fixture hợp lệ; dumpsys không có service record của app. Không `am startservice` với intent giả/null.

### Config, bảo toàn reference và cleanup

- Workspace JSON có4roots relative đúng và default PowerShell. Exception `.gitignore` đã có sẵn trước phase; không thêm trùng/force-add/stage. Hash cuối .gitignore `DB291A4767C7E5C4B900D560E9B12781A24BDB5D287C280A0B076CBF625981A3`, workspace `B715366CEBE4B1C9A74B1ECC4F747024D10EECEB581B4FF1ED0800134F4D11F9`, giữ nguyên so trước branch.
- Cập nhật English toolchain snapshot trong `.github/copilot-instructions.md` và `android-build.instructions.md`; giữ Windows/WSL/usbipd/MCP/hardware safety. Không sửa4instruction còn lại/skills/global settings. Không nâng AGP9.5 hoặc dependencies.
- Reference commands chỉ `--no-lazy-fetch`/GIT_OPTIONAL_LOCKS0 rev-parse/status/sparse list/Test-Path: TBS SHA2ce787de9f6e81b4294692a45de15596ae2e1322 vẫn thiếuindex/81399D, tbs5530.c/.h và cxd2878.c thiếu trên đĩa. C SHA c5fa27eecbe80633cbb365cc44656daea1c0e2e3 và Go11228b05fb1626bf41c4e1879ace10cd0a8d575c sạch. Không sync/fetch/repair/reset hoặc USB/firmware/I2C/vendor writes.
- Unit regression1028 exit0/32s,39tasksUP-TO-DATE: validation task thành công, không nói31methods chạy lại mới. Tests đã chạy thực mới là instrumentation2methods.
- Cleanup: xác nhận `emu avd name` trước `adb -s emulator-5554 emu kill`; shell1004 kết thúc exit0. Lần list ngay3s sau còn serial do shutdown đang diễn ra; recheck1031 `adb devices -l` rỗng. AVD retained để tái sử dụng, không wipe/xóa hoặc dừng thiết bị khác.

### Evidence và bàn giao

- Bằng chứng ngoài Git: session files/phase-1-1 chứa diagnostic-before-insets.png, diagnostic-after-insets.png, no-tuner-after-insets.png, welcome-after-insets.png, instrumentation-results.xml, app-logcat-filtered.txt. Không raw personal device IDs/credentials, không commit log/screenshot build output.
- Hash APKdebug đã runtime test sau fix: `8B4E47A9E60D6153CC3B97F244C45771AD0108BA0C1CA785F72332DA31FE38F6`; testAPK `F85C7D2ADE771570018623D4BD9163AB358D9B4A2530697FEA2F8D484FAB9872`.
- SnapshotSTATUS/ENVIRONMENT/TEST-MATRIX và ADR-008 ghi PASS/FAIL/NOTRUN, không rewrite lịch sử. Chưa stage/commit/push; HEAD vẫn6da609b. Files cần review: 2instructions, local .gitignore/workspace,3Activity+SystemBarInsets, dvbservice/build.gradle+NativeLibrarySmokeTest,5projectrecords. Không sửa RESEARCH vì không phát sinh protocol findings.
- **Điểm dừng:** Phase1.1 hoàn tất trong coverage Android17/API37/x86_64/4KiB. AndroidAPI21/arm64/16KiB runtime, foreground service streaming/USBpermission/TBS thật đều NOTRUN. Next: review và commit có chọn lọc khi được cho phép; không tự portdriver hoặc mở hardwaregate.

## 2026-11-02 21:35 +07 — Commit, merge, push Phase1.1 và xóa nhánh test

- **User approval:** add/commit toàn bộ thay đổi Phase1.1, merge vào chore/modernize-android-build, push origin branch này và xóa test branch. Windows PowerShell, cwd AndroidDvbDriver; không hardware/build/runtime mới.
- **Trước:** test/android-runtime-smoke tại6da609b;15file candidate đúng scope gồm workspace/ignore,2instructions,3Activity+SystemBarInsets,testnative+testmodule dependency và5projectrecords. Index trống; không chạm refs repositories hoặc main master.
- **Preflight:** status/branch-vv/remotes/log, staged diff --check với cr-at-eol, heuristic credential scan0match,0buildoutputs/firmware/captures/localproperties trong candidates. Không coi heuristic là security audit. Saved instrumentation XML2tests0fail0error và lintXML0errors; không khởi emulator để test lại code đã được kiểm chứng ở Phase1.1.
- **Commit:** b51a77e18e363750692d83cc53974b64718e1f51, `fix: stabilize Android runtime after toolchain upgrade`,15files/271insertions/3deletions, kèm Co-authored-by trailer. Không squash hoặc amend commit cũ.
- **Merge:** checkout chore/modernize-android-build khi working tree sạch; `git merge --ff-only test/android-runtime-smoke` thành công6da609b→b51a77e, không merge commit hoặc rewrite lịch sử.
- **Push:** push origin theo upstream branch hiện tại. `git ls-remote --heads origin refs/heads/chore/modernize-android-build refs/heads/test/android-runtime-smoke` xác nhận remote modernization ở b51a77e và không có test branch remote. Không force-push/master/upstream changes.
- **Delete:** chỉ sau remoteverify, `git branch -d test/android-runtime-smoke` thành công; local còn modernization/master. Không cần remote delete vì branch test chưa tồn tại trênorigin.
- **Hồ sơ cuối:** STATUS/WORKLOG cập nhật trên modernization ở commit docs riêng để không giữ hiện trạng “chưa commit/push”; mã commit docs cuối tra gitlog. Kiểm lại remote sau push docs. Không đưa rawlog/screenshot/APK vàoGit.
- **Next:** giữ dừng Phase1.1, review kế hoạch coverage API21/arm64/16KiB hoặc bước portdriver riêng theo yêu cầu mới; không tự triển khai/hardware.

## 2026-11-02 22:37–23:18 +07 — Phase 1.2: AGP9.5 và kiểm kê toolchain

- **Scope:** người dùng cho nâng riêng AGP9.4.1→9.5.0, sửa kiểm kê/JDK/AVD, kiểm build/test và chuẩn hóa hồ sơ; giữ Gradle9.8.2/compile37.2/target37/min21/NDK30.0.16248370/CMake4.1.2/Java8. Không commit/merge/push, khôngSDKinstall/uninstall/cleanup, không thay references/hardware.
- **Git/environment:** WindowsPowerShell7.6.9/Windows10.0.19045, cwd AndroidDvbDriver, nhánh chore/modernize-android-build, HEADace7e9f187d11a685975f707b6743d779fa847d9. Ban đầu có6localchanges của người dùng:2instructionsmodified + instructiontoolchain-lifecycle/skilltoolchain-audit/2scriptsinventory-reviewuntracked. Đã đọc và sửa bổ sung có mục tiêu, không overwrite toàn file hoặc reset/stash/historyrewrite.
- **Skills/instructions:** skilltool dispatcher chưa nhận toolchain-audit (notfound), nhưng đã tìm đúng `.github/skills/toolchain-audit/SKILL.md` và đọc trực tiếp. Loadproject-handoff/android-cli/agent-customization, đọc7scopedinstructions,projectskills/latestdocs. GitNexus:list_repos vẫn khôngtargetindex, khôngrebuild; ADBMCPdùngProxyhiệncó,khôngnewconnection/config.

### Preflight và bản stable

- `web_fetch` Google AGPAPIreference CurrentRelease9.5.0, notes9.5cófinal9.5.0entry, exactGoogleMavenPOM9.5.0. Các URL ở ADR-009; kiểm ngày2026-11-02. CompatibilityGradle>=9.6/JDK>=17/API37; Gradle9.8.2JavamatrixchoJDK25từ9.1.0. Không suy DefaultNDK28/BT36làyêucầudowngrade projectpins30/37.
- Android CLI --help/infoSDK/sdklist/docssearchAGP9.5 và source.properties đối chiếu: còn platform34/37.2,BT37,NDK30,CMake4.1.2,cmdline23,ADB37.0.1,emulator37.2.13;image34TVx86/37.0GoogleAPIsx86_64giữ. Usercleanupđãbỏplatform37.0/BT36/NDK28/CMake3.22.1; phasekhôngxóathêm. Imagerevisionlocal7kháccatalogin8,ghirõmetadatalocal,khôngupdatetoànSDK.
- JAVA_HOMEProcesscũjdk-25khôngtồntại,Usertrống,MachineC:\Program Files\Java\latest\jdk-25hợplệ. BuildchildnạpMachineJAVA_HOME,ANDROID_HOME=D:\Android\Sdk/ANDROID_AVD_HOME=D:\Android\AVD; wrapper--versionxácnhậnLauncher25.0.5.0.3/daemonresolvedjdk25.0.5.0.3. Khôngđổiglobalenv/IDE.
- Audit lần đầu1043bịRemoteSignedchặn. Đãhỏi,ngườidùngchỉđịnhrõ `Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process`. Các lệnhscript dùngtry/finallylưuvàrestorepolicycũ trongchildProcess,khôngUser/Machine. ProcesssauUndefined,CurrentUser/LocalMachineRemoteSigned. KhôngUnblockFilehoặcautobypassmặcđịnhscript/task.

### Lỗi kiểm kê và sửa trực tiếp

- Audit1044đọcCMakeđúng4.1.2nhưngAVDNamesra2tên(TBS5530_Runtime_API37vàTBS5530_Runtime_API37.avd),emulator0/metadatadisk2. NguyênnhânresolvedUserAVDhomechỉdùngđọcđĩakhôngtruyềnsubprocess;DirectoryInfo.BaseNamegiữnguyên.avdsuffix.
- SửaGetAvdInventorytruyềnresolvedhomevàoemulator-list-avds,restorefinally;normalizeNamebỏ.avdsuffixrồidedupe. Lầntest1046pháthiệnrestore nullquaSetEnvironmentVariableđểlạiempty-stringvariable,assertionFAIL; sửaRemove-ItemEnvchoabsentoriginal,giữgiátrịkhác.1047/1050/1056/1068assertionsPASS,emulator1/metadata1/mộttênđúng,khôngròcaller env.
- Thêm -PassThru choJSONobjectpipeline và JDKcandidates/effectivebuildcandidate;vẫnbáoProcessstale,khôngtựđổiJDK. CleanupreviewCMakeparsergiớihạnexternalNativeBuild.cmake,khôngmatchversioncủablockkhác. `scripts/check-env.ps1` dùngchungauditvới-NoReport,khônghaiimplementationlệch.
- Thêm tools/tests/ToolchainInventory.Tests.ps1,khôngcàiframework:ASTchỉloadcácfunctionđãreview;fixturetempscript/listAVDtestCMakewrongscope,dedupe,childenv,absent/presentenvrestorevàmissingemulatormetadatafallback. Fixtuređượcdọntrongfinally. Parser0errors và suitePASSthực. Reviewcleanup1047chỉinKEEP/REVIEWvàCSVngoàiGit,khônguninstall.
- InventoryJSONtrước/sauởparentlogs/toolchain,latesttoolchain-inventory-20261102-231414.jsonxácnhậnAGP9.5/AVD1/CMake4.1.2. Báocáochứađườngdẫn/hostmetadata,khôngcommit hoặcđăngartifactrawreport. Instructions/skillEnglishcậpnhậtbehavior/safetykhônghardcodeperpetuallatest;READMEghi datedbaseline9.5.

### Nâng AGP và validation build

- Rootbuild.gradle đổi đúngapplication/libraryplugin9.4.1→9.5.0. Giữnguyênwrapper/checksum,pins4modules/dependencies/CMakeLists/source/runtimehelpersPhase1.1. Không portdriver.
- Lệnh1048: `gradlew.bat :app:assembleDebug :app:assembleRelease :usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest :app:lintDebug --console plain --no-daemon --max-workers=1` FAILsau12m9sởnativeDebugx86Unabletodeletedirectory. LỗiWindowsoutputlockđãquan sáttrước,khôngchứngminhAGPcompatibilityfailure;khôngxóacache/killprocesskhác/skipABI.
- Rerun1059: `gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintDebug :usbxfer:testDebugUnitTest --rerun :drivers:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun --console plain --no-daemon --max-workers=1` **exit0/9m12s**,325tasks245executed/80UP-TO-DATE. UnitXMLmớiusbxfer1/drivers17/dvbservice13=31methods0failure(1ConvertCStringutility),khôngchỉcachedPASS. LintXML0errors11warnings,khôngdisabletest/assertion/lintgate.
- AAB1067 `:app:bundleRelease --console plain --no-daemon --max-workers=1 --warning-mode all` **exit0/45s**,157tasks18executed139UP-TO-DATE. Configuration.setVisibledeprecatedwarningvẫncòntrênAGP9.5dùnotesđềcậpfix;Javac25Java8obsoletecũngvẫncó,khôngẩnhoặcđổibytecode.
- Staticnativechecks1065/1066: zipalign37 -c -P16 4cảdebug/releaseexit0;APKlibUsbXfer.sođủarm64-v8a/armeabi-v7a/x86/x86_64;llvm-readelfNDK30mớicảDebug/RelWithDebInfo4ABI,64bitLOAD0x4000/32bit0x1000. MộtprobeELFregexsaiescape1065đãdừng,sửaquerylọcDirectory.Name1066PASS;khôngảnhhưởngsourcebinary.
- DebugAPK SHA2566D6E1C4953F708CA04E9DC5AD223168514C3745F27106E3AF12033C884D44D53,releaseunsignedAPK7B126F04109EA1F9295336EF8615C8ACCC3743BE8488497BF8DE7EA40AAB52F3;khôngsign/upload.

### AVD/instrumentation/UI regression

- DùngAVDexistingTBS5530_Runtime_API37tạiD:\Android\AVD,khôngtạomới/wipe/download. Boot1051vớiflaggiốngPhase1.1: -port5554 -no-snapshot -no-boot-anim -no-audio -no-window -gpu swiftshader_indirect. Đợi1052timeout4phútFAILbanđầu,sau1054ADBdevice/getpropboot1;khôngreset/rútdata.
- RuntimeAndroid17/API37/x86_64/page4096;emuavdnameexplicitđúng.1055:ANDROID_SERIAL=emulator-5554,`:dvbservice:connectedDebugAndroidTest --console plain --no-daemon --max-workers=1` **exit0/5m38s**,92tasks43executed49UP-TO-DATE. XML2tests0fail0error,gồmallDevicesAreInXmlvànativeloadwithoutUSB. CopyXMLngoàiGitphase-1-2/instrumentation-results.xmlSHA25603269F9A8C91B106FF415A015756384CB0CA3EC4A0CDDE15AC0B3341534B02A6.
- InstallAPKdebugAGP9.5Success;amstartWelcomeCOLDStatusokTotalTime10080ms. UIbịANRlaunchercom.google.android.apps.nexuslauncher/SystemUIoverlayche,chọnWaittheohierarchyMCP,khôngforcekillđổisystemsettings. SaucùngWelcomefocusđúng,topinset63/bottom63nhưPhase1.1.
- Diagnostic→Start→dialogNo devices foundvàhandledDvbException:64;PID5345sống,filteredlog0FATAL/UnsatisfiedLinkError/SecurityException/appANR;BackvềWelcome. Khôngserviceforeground/USBpermissionvìkhôngtuner. KhôngquyANRsystemthànhappcrashhoặctuyênbốemulatorổnđịnhtuyệtđối.
- XácnhậnđúngAVDname,rồi1064`adb -s emulator-5554 emu kill`;emulator1051exit0,ADBdevices1066rỗng. KhôngxóaAVDdata,khôngdừngphysicaldevicekhác.

### Bàn giao và điểm dừng

- UpdaterecordsSTATUS/ENVIRONMENT/DECISIONS/TEST-MATRIX/WORKLOG;historicalresearchgiữnguyên,cácnguồnGooglemớiởADR-009. Githistory/branch/HEADgiữnguyênace7e9f,chưastage/commit/merge/push. Khôngđụngreferencecode/index,usbipd/TBSfirmware/I2C/reset/LNB/TSwrites.
- BaselineAGP9.5chỉđượccoi projectverifiedởcácgateđãchạytrênmáynày;lateststablecóthểđổisaucheckdate. RuntimeAPI21/arm64/16KiB,USBhostthật/foregroundstreamingvàTBSdriver vẫnNOTRUN.
- Next:reviewdiffPhase1.2vàcommitcó chọnlọckhiđượcphêduyệt;theodõiprocessenvstale/nativeoutputlock/emulatorANR,khôngtựportdriverhoặcSDKcleanup.

## 2026-11-02 23:39 +07 — Checkpoint commit/push Phase1.2 theo yêu cầu

- Người dùng yêu cầu add, commit và push; nhánh hiện tại chore/modernize-android-build, HEADtrướcace7e9f187d11a685975f707b6743d779fa847d9. OriginSSHtrankien84/AndroidDvbDriver, upstreamsignalwareltd không thay đổi.
- PreflightGitstatus:15candidatefiles, indextrống; rootpluginsAGP9.5.0, scope audit/test/instructions/docs nhưPhase1.2. `git ls-remote --heads origin refs/heads/chore/modernize-android-build` xác nhận remote cũngace7e9f trước push. Không fetch/rebase/merge/reset.
- Diff --checkcr-at-eolPASS;0candidatebuildoutputs/SDK/firmware/TS/localproperties;heuristicsecretpattern0match,khôngcoilàsecurityaudit. SavedXMLunit31methods0fail,instrumentation2tests0fail0error,lint0errors đãđọc lại. Không chạy test/build/AVD mới trong tác vụ Git này.
- Chuẩn bị commit `build: upgrade AGP to 9.5.0 and fix toolchain inventory`, kèm Co-authored-by trailer. STATUS thêm checkpoint để phân biệt lịch sử chưacommit; hash chính commit đọc từGitlog sau tạo, không tự ghi SHA vònglặp vào commit.
- Push được phê duyệt chỉ nhánh hiện tại lênorigin, không force/master/upstream/referencechanges; xác minh live remote SHA trùngHEAD vàworkingtreesạch sau thao tác. Kết quả publish báo trực tiếp sau xác minh, không ghi trước là thành công.
- Next: dừngPhase1.2; coverageAndroidAPI21/arm64/16KiB/hardware vẫnNOTRUN,không tựportdriver hoặcSDKcleanup.

## 2026-11-03 00:02–00:18 +07 — Phase 2.0: source readiness và thiết kế driver

- **Mục tiêu và giới hạn:** chỉ xác minh nguồn, hoàn thiện thiết kế và kế hoạch Phase 2.1. Không viết code production hoặc test implementation, không build, không gửi USB writes, không sửa reference repositories.
- **Môi trường:** Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`. Baseline main `chore/modernize-android-build` / `4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe`, working tree sạch. Đọc guidance và các phần hồ sơ mới nhất; load skills tbs-research/project-handoff.
- **Nhánh:** sau kiểm status sạch, chạy `git switch -c docs/tbs5530-source-architecture` thành công, cùng HEAD baseline; không chạm nhánh modernization, không stage/commit/push/merge. Các file thay đổi chỉ là RESEARCH/DECISIONS/STATUS/TEST-MATRIX/WORKLOG.

### Kiểm tra mới về repository và objects

- Tất cả Git reads trên references dùng `--no-lazy-fetch`, `GIT_NO_LAZY_FETCH=1`, `GIT_OPTIONAL_LOCKS=0`. Không chạy sync script, fetch, restore, reset, clean, clone/reclone, config write hoặc tải objects.
- `rev-parse --show-toplevel`, `rev-parse HEAD`, `branch --show-current`, `remote -v`, `status --porcelain=v1 -uall`: TBS latest/2ce787de9f6e81b4294692a45de15596ae2e1322 có 81.399 staged D; C main/c5fa27eecbe80633cbb365cc44656daea1c0e2e3 và Go main/11228b05fb1626bf41c4e1879ace10cd0a8d575c sạch. Origin cả ba đúng nguồn đã khóa.
- `Test-Path .git/index/.git/index.lock`, `GIT_INDEX_FILE`, `ls-files`, `sparse-checkout list`, `config --get core.sparseCheckout/core.sparseCheckoutCone`: TBS thiếu index/lock, không override index, ls-files0, đủ sáu patterns và flags true. Đếm file đệ quy: cả sáu thư mục đều0file.
- `ls-tree -r HEAD -- <six-paths>`, `cat-file --batch-check`:785paths/785unique blobs,0missing,15.372.887bytes uncompressed. Kiểm thêm các cone ancestor root/drivers/media/usb/include/uapi/linux:572paths/572blobs, **0missing hiện tại**. Không lặp lại kết luận cũ cần tải572blobs; chưa xác định ai bổ sung trước phase, tác vụ này không tải.
- Tổng cone dự kiến1.357file, kiểm tên reserved Windows/ký tự không hợp lệ cơ bản0lỗi. AUX ngoài sparse vẫn có trong HEAD tree. `config --show-origin --get core.protectNTFS` không có override lưu, exit1.
- `read-tree --dry-run -m -u HEAD` không ghi: exit128 `invalid path drivers/gpu/drm/nouveau/nvkm/subdev/i2c/aux.c`; sau đó index/lock vẫn vắng. Không chạy workaround protectNTFS=false hoặc recovery thực ở phase này.
- Board tbs5530.c/.h, Sony cxd2878.c/.h/_priv.h, Montage m88rs6060.c/.h/_priv.h, dvb-usb common/init/firmware/usb-urb, media frontend/math và Linux headers trực tiếp: tree/blob local đọc được, disk thiếu. Makefile bindings kiểm đúng TBS/CXD/Montage. Không khẳng định full kernel build có đủ mọi dependency.
- Source readiness phân đôi: **PASS cho đọc nguồn phục vụ nghiên cứu qua local blobs**, **BLOCKED cho checkout/index/materialized tree**. Kế hoạch backup/rollback và NTFS vs ext4 WSL nằm ở RESEARCH, cần phê duyệt riêng trước mọi ghi. Không tạo WSL checkout hoặc đổi SDK.

### Nghiên cứu mở rộng và kiến trúc

- Dùng kết quả Phase0 làm nền, đọc lại official bằng `git show HEAD:<path>`/`cat-file blob`; source C/Go đọc bằng view/grep trên đĩa. Ghi file/function/SHA và links cố định trong RESEARCH. Không đồng bộ upstream để thay mốc đang nghiên cứu.
- Làm rõ generic Cypress hexline loader khác loader raw device-specific TBS; framework no_reconnect chỉ tiếp tục sau upload thành công, không bảo đảm Android identity/permission/fd ổn định. Firmware provenance/hash/license/address map vẫn chưa có bằng chứng.
- Official/C manual PLP ghi bank23/AF/AD mỗi set_dvbt2. Go active-same-system retune chỉ đổi bandwidth; ghi nhận khác biệt nguồn, không claim đã tái hiện lỗi trên tuner thật. Internal manualPLP0 được đề xuất cho compatibility, không gọi là auto; public PLP API và TCP extension chưa chọn triển khai.
- Chip dispatch official chỉ0x197Ascot3 và0x19c/0x39aFreia; community macros mở rộng ID; Go default còn lạiFreia. Thiết kế fail closed trên ID ngoài bảng được review, không mặc định thiết bị người dùng có Ascot3 chỉ từ tên board.
- Đọc Android registry/Creator, DvbUsbDevice, DvbDevice/DataPump, Retry, I2cAdapter, UsbPermissionObtainer, UsbBulkSource/JNI và service/request enums. Phát hiện Retry.retry(4) là5attempts, không4; I2cAdapter có10iterations. Điều chỉnh adapter production tương lai thành extendsDvbDevice để tránh open/close final và replay sideeffects, compose phần đang có thay refactor toàn framework.
- Phase2.1 đề xuất chỉ plain-Java core ControlIo/Bridge/FirmwarePlan/Session + scripted fakes/tests; chưa Android transport/registry/firmware executor/frontend production/JNI. Test matrix exact bytes, shorttransfer, conservative limits, firmwaretail/address policy, endpoint ordering, fault latch, late callbacks và cleanup; tất cả **PLANNED/NOT RUN**.
- Native streamActive tách khỏi frontendLocked; stream-before-tune của app hiện hữu phải được tính đến. Cleanup không giữ control lock khi join, không fd giả/ioctl thật và không corrective vendor write tự động. API enums/Request ordinals của các driver hiện hữu giữ nguyên.

### Công cụ, lỗi lệnh và bằng chứng

- MCP Proxy GitNexus list_repos không có target index; AgentMemory lookup limit3/compact không có match; không save memory hoặc build index. Không dùng graph rỗng để khẳng định blast radius an toàn.
- Hai lệnh PowerShell probe ban đầu lỗi parser (`$f:$s` và block ngoặc không khép) đã sửa thành `${f}`/loop đơn giản rồi chạy lại thành công; lỗi parser không gửi lệnh vào Git hoặc hardware. Một output source lớn được đọc lại bằng range/grep có giới hạn, không dump toàn bộ file.
- Các đoạn bảng/timing/register trong RESEARCH là source facts hoặc proposed policy, không hardware results. Không tải firmware, không usbipd/lsusb/PyUSB/ADB/emulator, không thay environment/global settings.
- ADR-010 ghi thiết kế đã chọn và phương án thay thế; lịch sử ADR-006/Phase0 vẫn giữ, lý do đổi hướng lifecycle nêu rõ. STATUS không gọi toolchain baseline cũ là chưa push; baseline4cf1b64 giữ nguyên.
- **Next:** review và xin duyệt scope Phase2.1 offline foundation; nếu muốn materialize TBS checkout thì duyệt kế hoạch recovery riêng. Dừng Phase2.0, không tự viết driver production hoặc kiểm thử USB thật.

- **Kiểm chứng tài liệu cuối Phase2.0 (00:21 +07):** diff --check PASS; cả năm hồ sơ UTF-8 hợp lệ,0 liên kết Markdown tương đối hỏng; VS Code problems không lỗi. Chỉ năm file docs được sửa,0staged/0untracked; HEAD nhánh tài liệu và modernization cùng4cf1b64. Reference SHA/status không đổi so đầu phase. Không chạy build/test mới vì chỉ sửa tài liệu.

## 2026-11-03 00:54 +07 — Kiểm chứng người dùng phục hồi TBS checkout và dọn script tạm

- Người dùng gửi ảnh terminal Apply PASS, backup tbs-sparse-before-20261103-004911-951. Agent không chạy recovery hoặc ghi reference; kiểm riêng bằng Git/filesystem chỉ đọc.
- TBS HEAD2ce787de9f6e81b4294692a45de15596ae2e1322/latest, status0entries, index tồn tại8.822.766bytes, index.lock không có. Sáu sparse paths giữ nguyên, config protectNTFS không override lưu. C/Go status sạch.
- Expected manifest từ ls-tree sáu paths và ancestor cone:1.357file; actual1.357file,0missing/extra. Batch hash-object --stdin-paths theo filters/EOL:0mismatch. TBS5530/Sony/Montage/frameworksource hiện diện; counts chi tiết trong RESEARCH. Không build fullkernel/hardware mới.
- Backup/git giữ nguyên, after.json PASS/1357/đúngSHA. Theo yêu cầu trước của người dùng, đã xóa **chỉ** script tạm session Restore-TbsSparseCheckout-Reviewed.ps1 sau khi đủ gate, xác nhận file không còn; không xóa backup/logs/AVD/source khác.
- Main vẫn docs/tbs5530-source-architecture/4cf1b64, không stage/commit/push. STATUS/RESEARCH/TEST-MATRIX/WORKLOG thêm kết quả mới, không xóa lịch sử lỗi. Next: xin duyệt Phase2.1 offline foundation, chưa tự viết driver production hoặc gửi USB writes.

## 2026-11-03 00:58 +07 — Commit/push hồ sơ Phase2.0 theo yêu cầu

- Scope: năm file STATUS/RESEARCH/DECISIONS/TEST-MATRIX/WORKLOG, gồm thiết kế Phase2.1 offline và xác minh1.357file checkoutNTFS. Không code production, reference sources, backup hoặc rawlogs.
- Trước: nhánh docs/tbs5530-source-architecture tại4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe, năm tài liệu modified; index trống. Modernization giữ nguyên baseline. Origin đúng fork trankien84/AndroidDvbDriver; nhánh tài liệu chưa có trên origin tại preflight.
- Validation: diff --check PASS, UTF8/relativeMarkdownlinks PASS, heuristicsecretpattern0match; không phải security audit. Không chạy build/unit/emulator/hardware trong tác vụ tài liệu.
- Chuẩn bị commit `docs: finalize TBS5530 architecture and source readiness`, có Co-authored-by trailer, sau đó push explicit nhánh hiện tại với upstream tracking. Không merge, force-push hoặc thay nhánh modernization/master. Hash và kết quả push báo sau kiểm tra live remote; không ghi trước thành công.
- Next: dừng trước Phase2.1, chỉ triển khai offline foundation khi được duyệt; không USB writes hoặc firmware.

## 2026-11-03 10:39 +07 — Phase 2.1: TBS5530 offline USB protocol foundation

### Preflight và phạm vi

- Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`. Đọc instructions/scoped build/hardware/records, skills tbs-research/project-handoff và hồ sơ Phase2.0/ADR-010. Git sạch tại `docs/tbs5530-source-architecture`/`8588ee7661003052b7d1a0353e23bb3a3dcef8b5`; tạo `feat/tbs5530-offline-protocol`, không đổi modernization.
- Ba references kiểm lại bằng `git --no-lazy-fetch -C <reference> rev-parse HEAD` và `status --porcelain=v1 -uall`, với `GIT_NO_LAZY_FETCH=1`, `GIT_OPTIONAL_LOCKS=0`: TBS2ce787de9f6e81b4294692a45de15596ae2e1322, Cc5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go11228b05fb1626bf41c4e1879ace10cd0a8d575c; cả ba0status entries. Đọc lại board/protocol/firmware/chip source, không fetch/sync/config/index write.
- GitNexus được kiểm qua gateway: không có index của project; không tự analyze hoặc suy empty graph là không có callers. Thay bằng đọc source, search và unit/regression tests. Không gửi code lên dịch vụ bên thứ ba.
- Toolchain pins giữ nguyên. Child build nạp Machine JAVA_HOME hợp lệ thay process cũ; không sửa User/Machine environment hoặc ExecutionPolicy, không cài/gỡ package/SDK.

### Thay đổi

- Tạo4main classes: `Tbs5530ControlIo`, `Tbs5530Bridge`, `Tbs5530FirmwarePlan`, `Tbs5530Session`; tạo fake `ScriptedFakeControlIo` và3test suites cùng package tbs5530. Tổng8Javafiles mới, SPDX GPL-2.0-or-later; không firmware blob hoặc source tables từ reference.
- Bridge kiểm parameters/buffers/flags trước I/O, khóa cặp read, fault latch/no retry, đúng endpoint82. Phân loại return count lớn hơn length thành I/O failure, không short transfer. Kiểm state/generation sau transfer để detach callback không gửi read tiếp hoặc trả dữ liệu cũ.
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

- Các lượt focused trong quá trình phát triển PASS; snapshot trước3tests cuối có49newmethods. Không dùng số này làm tổng cuối. Lượt regression đầu PASS80methods/2m20s; sau đó thêm assertion cleanup/runtime exception/detach callback và chạy lại toàn bộ.
- **Lệnh cuối**, buộc chạy lại cả ba unit tasks:

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

- **PASS exit0, BUILD SUCCESSFUL in1m45s;177actionable tasks:41executed/136up-to-date.** XML cuối: Bridge17/Firmware19/Session16 =**52methods mới**; drivers69/usbxfer1/dvbservice13 =**83methods**,0failures/0errors. Debug APK thành công. Lint XML0Error/0Fatal/11Warning; không thêm suppression hoặc bỏ assertion. Cảnh báo JDK25 source/target8 obsolete và Gradle deprecated features vẫn hiển thị.
- Evidence local: `drivers\build\test-results\testDebugUnitTest\TEST-info.martinmarinov.drivers.usb.tbs5530.*.xml`, các XML module usbxfer/dvbservice, `app\build\reports\lint-results-debug.xml`, `app\build\outputs\apk\debug\app-debug.apk`. Không commit outputs/raw logs.
- Kiểm độc lập Java8 sau thay đổi cuối: gọi JDK Machine `bin\javac.exe --release 8 -d drivers\build\tbs5530-java8-check <4corefiles>`, không Android/JNI classpath: exit0. `javap -verbose -classpath <output> info.martinmarinov.drivers.usb.tbs5530.Tbs5530Bridge` trả major version52. Search Android/AndroidX imports/native declarations0match; IDE problems0errors. Đã xóa riêng thư mục compile kiểm tra này, xác nhận không còn; không tạo script tạm cần giữ.

### Bàn giao và điểm dừng

- Kiểm cuối: `git diff --check` PASS; đọc UTF-8 strict cho8file mới/5hồ sơ, trailing-whitespace check file mới và relative Markdown links PASS. `git status --short --untracked-files=all` chỉ có5hồ sơ modified/8Javafiles untracked; `git diff --cached --name-only` rỗng. Modernization vẫn4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe. Không có lỗi IDE trong hai package mới.
- Chưa stage/commit/push/merge. Năm hồ sơ modified và tám file Java mới; binaries/SDK/reference không đưa vào Git.
- Không emulator/instrumentation mới, không Windows/WSL/Android USB access, firmware thật/vendor/I²C/LNB/reset/usbipd hoặc tuner tests. Native cancellation/Android permission/reconnect/lock/TS và firmware rights vẫn NOT RUN/chưa duyệt.
- Review core và ADR-011 là bước kế tiếp. Phase2.2 chỉ đề xuất lifecycle adapter với fake/instrumentation không tuner, cần phạm vi được duyệt; chưa bắt đầu frontend hoặc transport hardware.

## 2026-11-03 10:46 +07 — Checkpoint commit và tích hợp Phase2.1 theo yêu cầu

- Người dùng phê duyệt add/commit, merge vào `chore/modernize-android-build`, push origin đúng nhánh đó và xóa nhánh feature local sau xác minh. Không xóa nhánh tài liệu hoặc remote branch.
- Preflight Windows PowerShell/main repo: feature `feat/tbs5530-offline-protocol`/8588ee7661003052b7d1a0353e23bb3a3dcef8b5; target local và `git ls-remote --heads origin chore/modernize-android-build` cùng4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe. `rev-list --left-right --count target...HEAD` trả0/1: target là tổ tiên; fast-forward sẽ gồm checkpoint Phase2.0 và commit mới Phase2.1.
- Status chỉ5hồ sơ modified và8Javafiles mới đã kiểm thử; `git diff --check` PASS. Không build/test/hardware lại trong tác vụ Git. Kết quả83unit methods/debug/lint ở mục Phase2.1 vẫn là lần chạy thực gần nhất, không ghi thành một lần PASS mới.
- Staged diff check đầu tiên phát hiện CRLF/mixed EOL trong8Javafiles mới (core.autocrlf=false). Commit chưa chạy vì gate chặn. Chỉ chuẩn hóa8file này thành LF, kiểm nội dung không đổi ngoài EOL; stage lại, `git diff --cached --check` PASS và ls-files --eol đều i/lf,w/lf. Không sửa Git config hoặc bỏ whitespace gate; không chạy lại tests vì không đổi logic.
- Commit dự kiến `feat: add offline TBS5530 USB protocol foundation`, có Co-authored-by trailer. Chỉ stage13file trong hai package tbs5530 và5hồ sơ. Sau commit: switch target, merge --ff-only feature, push explicit target không force; kiểm remote SHA/working tree rồi branch -d feature. Hash và kết quả thực báo sau thao tác, không ghi trước thành công.

## 2026-11-03 11:25 +07 — Phase 2.2: lifecycle và fake adapter

### Khôi phục và checkpoint tích hợp

- Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`. Đọc project/scoped instructions, STATUS/ENVIRONMENT/RESEARCH/DECISIONS/TEST-MATRIX và WORKLOG mới nhất; dùng project-handoff. Không đổi toolchain hoặc reference sources.
- Preflight main sạch, nhánh `chore/modernize-android-build`, HEAD `6eb23537484a3e1dd263bd5a88f56914c7c5e313`. Xác nhận commit này đã chứa Phase2.1/ADR-011, fast-forward/push ở tác vụ trước. Nhánh local feature/docs và remote docs đã xóa theo yêu cầu riêng trước Phase2.2. Không commit/merge/push/reset/switch trong phase này.
- Ba references chỉ đọc qua `git --no-lazy-fetch -C <reference> rev-parse HEAD` và `status --porcelain=v1 -uall`, với GIT_NO_LAZY_FETCH=1/GIT_OPTIONAL_LOCKS=0: official2ce787de9f6e81b4294692a45de15596ae2e1322, Cc5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go11228b05fb1626bf41c4e1879ace10cd0a8d575c, cả ba0entries lúc đầu/cuối. Không fetch/index/config writes.
- GitNexus list_repos qua gateway không có index project; không tự rebuild và không coi empty graph là an toàn. Đọc source hiện tại, diff và assertions làm bằng chứng. Không chia sẻ source lên dịch vụ bên thứ ba.
- Đọc current build/wrapper: AGP9.5.0/Gradle9.8.2/checksum giữ nguyên, compile37.2/min21/source-target8. SDK platform source.properties revision2/ApiLevel37.2/PreviewSdkInt0. Child dùng Machine JAVA_HOME có java.exe, SDK D:\Android\Sdk; không đổi biến User/Machine/ExecutionPolicy, không package install.

### Test tái hiện trước khi sửa

Thêm Tbs5530ConcurrencyTest: fake transfer thread A bị CountDownLatch giữ; thread B nhận detach hoặc cancel. Hai tests đòi invalidation hoàn tất mà không đợi transfer. Chạy trên production core baseline chưa sửa:

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:ANDROID_HOME = 'D:\Android\Sdk'
.\gradlew.bat :drivers:testDebugUnitTest --tests '*Tbs5530ConcurrencyTest' --console plain --no-daemon --max-workers=1
```

**FAIL được tái hiện:2tests/2failures, exit1,36s/23tasks2executed.** Assertion tại invalidated.await không đạt. Đây là defect/giới hạn session monitor đã nêu ADR-011, không phải test hardware và không bỏ test để sửa kết quả.

### Thay đổi và xử lý race

- Sửa Tbs5530Session/Tbs5530Bridge: transactionLock riêng, state monitor ngắn; cả cặp0x90/91 vẫn atomic. Admission và active transaction tracking giữ tài nguyên tới physical completion, invalidation nhanh không đóng connection còn dùng. Check generation/state sau transfer và trước read publish; no retry/vendor recovery.
- Close/detach/cancel đánh dấu pending cleanup; last transaction nhả cả hai khóa rồi cleanup cancel→release→close. Expose cancellationRequested/cleanupPending/cleanupFailure; deferred failures suppressed trên transfer error, immediate failures throw cho caller. Reopen bị chặn khi pending; concurrent close/callback chỉ một owner.
- Thêm readiness events/getters firmwareUploaded/frontendInitialized, giữ ready/lock/stream khác nhau; lock yêu cầu frontend initialized. `failed` hỗ trợ permission/acquisition errors trước nhận connection. Bổ sung1Session test và tăng assertion cũ, không giảm coverage.
- Thêm JVM-only Tbs5530LifecycleCoordinator và32tests: Request gắn attachment identity+generation; ports permission/open/claim; claim receipt; local lease cho rejected/partial acquisitions; callback đúng một lần; stale callback không đóng resource mới. Coordinator không giữ monitor qua callback vật lý, không tự READY. Không framework binding/transport concrete.
- Concurrency suite cuối10methods, gồm blocked pointer detach/cancel; deferred errors; late timeout; queued stale transaction; reentrant detach; lỗi giữa0x90/91; join worker ngoài khóa; concurrent close trong cleanup;40vòng race có latch. Không sleeps tùy tiện hoặc tăng timeout để che flaky test.
- Thêm ADR-012 và cập nhật năm hồ sơ; không sửa FirmwarePlan, protocol bytes, registry/XML, driver cũ/Retry, wire enums/Request, native/JNI hoặc dependencies.

### Validation cuối

Sau split locks: focused Bridge/Session/Concurrency PASS35methods trong42s. TBS package +debug sau coordinator PASS. Full regression đầu PASS125methods; sau thêm concurrent-close-during-cleanup test chạy lại toàn bộ, không dùng count125 làm kết quả cuối.

VS Code task `Android: assembleDebug (Windows Gradle Wrapper)` trả Task not found; fallback Gradle wrapper cùng cwd, không sửa tasks/config. Lệnh cuối:

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_AVD_HOME = 'D:\Android\AVD'
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit0, BUILD SUCCESSFUL in2m23s;177actionable tasks:41executed/136up-to-date.** XML cuối drivers112/usbxfer1/dvbservice13=**126methods,0failure/0error**. TBS suites: Bridge17/Firmware19/Session17/Concurrency10/Coordinator32=95methods (43methods thêm so baseline). Debug APK PASS; lint XML0Error/0Fatal/11Warning. Warning Java8 obsolete/Gradle deprecated vẫn hiện, không suppress.

Stress độc lập dùng JDK Machine, JUnit4.13.2/Hamcrest1.3 đã có trong Gradle cache, không tải dependency. Compile cả5corefiles và toàn TBS test package bằng `javac --release 8 -cp <JUnit;Hamcrest> -d drivers\build\p22-final-java8 <sources>`, exit0; `javap -verbose` Session major version52. Chạy5lượt:

```powershell
1..5 | ForEach-Object {
    & "$jdk\java.exe" -cp "$out;$cp" org.junit.runner.JUnitCore info.martinmarinov.drivers.usb.tbs5530.Tbs5530ConcurrencyTest
    if ($LASTEXITCODE -ne 0) { throw 'Stress failed' }
}
```

Cả5lượt **OK(10tests)**, mỗi lượt có40detach/cancel iterations →200vòng; time0.173/0.148/0.152/0.219/0.179s. Trước đó5lượt9methods PASS khi chưa có test close đồng thời. Một invocation stress bị sai tên class do PowerShell argument, JUnit initializationError trước khi chạy tests; sửa command sang fully-qualified class name, không sửa assertion. Output compile riêng luôn dọn trong finally; không còn p22-final-java8/p22-concurrency-check. Không tạo script tạm cần giữ.

Evidence local ở các module `build\test-results\testDebugUnitTest\TEST-*.xml`, lint `app\build\reports\lint-results-debug.xml`, APK `app\build\outputs\apk\debug\app-debug.apk`; không stage outputs/logs. IDE problems không errors. Bằng chứng83methods Phase2.1 giữ lịch sử riêng, không gọi là lượt test Phase2.2.

### Giới hạn và bàn giao

- Không instrumentation mới: thay đổi JVM-only chưa được nối vào Android runtime, không Android framework binding/manifest/JNI/registry changes. Native-load/registryXML/UI PASS trước là lịch sử, không PASS mới. Real USB/permission dialog/firmware/lock/TS/native cancel NOT RUN.
- Google UsbDeviceConnection/USB Host guide được tra: controlTransfer EP0 synchronous, timeoutms/negative failure; không cancellation handle riêng bảo đảm ngắt call. Code không cưỡng bức close in-flight; call không trả thì pending/reopen bị chặn. Deferred cleanup có thể trên transaction thread; cancel không được join chính nó. Permission/open/claim timeout là caller/port contract, không thêm scheduler.
- Cần review ADR-012 và Android attachment identity/receiver/PendingIntent/deadline/threading trước Phase2.3. Không firmware thật/production frontend hoặc hardware bring-up khi chưa có kế hoạch/phê duyệt riêng.
- Kiểm cuối: git diff --check PASS; UTF-8 strict/relative links PASS cho11file, main core0Android/AndroidX imports;8tracked changes+3untracked Javafiles, staged diff rỗng. File concurrency mới được chuẩn hóa CRLF→LF sau gate EOL phát hiện, không đổi logic/assertion, không chạy lại tests cho EOL-only. Toàn bộ Javafiles mới/sửa cuối làLF. JDK Machine java -version25.0.5.0.3 xác nhận; các output kiểm tra tạm không còn.
- Main vẫn6eb2353,6Javafiles mới/sửa và5hồ sơ cập nhật, chưa stage/commit/push. Dừng sau Phase2.2.

## 2026-11-03 11:33 +07 — Checkpoint commit/push Phase2.2 theo yêu cầu

- Người dùng phê duyệt commit toàn bộ thay đổi Phase2.2 và push nhánh hiện tại. Preflight Windows PowerShell/main repo: nhánh chore/modernize-android-build, HEAD6eb23537484a3e1dd263bd5a88f56914c7c5e313; live origin cùngSHA, staged diff rỗng, đúng11file (6Javafiles và5hồ sơ), diff --check PASS.
- Staged diff check phát hiện một dòng trống thừa ở EOF của concurrencytest mới, commit chưa chạy. Chỉ bỏ dòng trống đó, không đổi logic/assertion; stage lại và staged diff --check PASS. Không đổi Git whitespace policy.
- Message `feat: add TBS5530 lifecycle coordinator and safe cancellation`, kèm Co-authored-by trailer. Chỉ stage11file đã kiểm, không output/cache/firmware/reference. Sau commit push explicit origin/chore/modernize-android-build không force và xác minh SHA remote/working tree. Hash và kết quả thực báo sau kiểm tra, không khẳng định trước thành công.
- Không chạy lại tests cho tác vụ Git/documentation checkpoint. Kết quả126unit methods/debug/lint/stress ở mục Phase2.2 là lần thực chạy gần nhất; không ghi như lần PASS mới. Không thay production logic, dependencies/toolchain/hardware, không bắt đầu Phase2.3.

## 2026-11-03 12:05–12:42 +07 — Phase 2.3: Android framework binding/instrumentation, không tuner

### Baseline, audit và nguồn

- Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`. Main sạch lúc bắt đầu, nhánh chore/modernize-android-build/635ee4a57267de6055126efcfee6121b30701433; `git ls-remote --heads origin chore/modernize-android-build` cùngSHA. Đây là checkpoint Phase2.2/ADR-012 đã commit/push, không còn pending như snapshot cũ.
- Đọc instructions v4/scoped build/hardware/ADB/records/toolchain cùng STATUS/ENVIRONMENT/RESEARCH/DECISIONS/WORKLOG/TEST-MATRIX; dùng project-handoff và android-cli. Audit UsbDelegate, manifest/service/worker, registry, DvbUsbDevice, UsbPermissionObtainer và device_filter.xml. Không dùng legacy permission helper cho TBS, không sửa driver khác hoặc nối registry/service.
- `git --no-lazy-fetch`/no-lazy-fetch1/optional-locks0 kiểm đầu/cuối: TBS2ce787de9f6e81b4294692a45de15596ae2e1322, Cc5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go11228b05fb1626bf41c4e1879ace10cd0a8d575c; cả ba0status entries. Không tải/sửa reference, firmware hoặc Gitconfig.
- Semantic source search timeout, fallback glob/grep/view có giới hạn; GitNexus list_repos không có targetindex, không tự analyze hoặc tuyên bố emptygraph là safe. Gateway discovery ADB/list_avds xác nhận TBS5530_Runtime_API37. Không thêm MCP endpoint hoặc sửa Proxy.
- Kiểm android --help/docs search help/emulator help trước dùng. AndroidCLI docs search/fetch USBHost/receiver/Android14, GoogleAPI PendingIntent/USBManager/Connection và target37 notes; nguồn/phân biệt API21/23/31/33/34/37 ghi RESEARCH/ADR013. Không thay build pins/dependencies/SDK/JDK/systemenv.

### Implementation

- Thêm Tbs5530UsbBinding (pureJVM engine), Tbs5530AndroidUsbFacade (Android wrappers + injectedManager/RawConnection), Tbs5530AndroidRuntime (worker+independent scheduler), Tbs5530UsbBindingTest và instrumentation Tbs5530AndroidUsbFacadeTest. Sửa Session/Coordinator để logical invalidation không chạy cleanup trên receiver/main; explicit drainCleanup ở worker, cleanupRunning bảo vệ ownership. Tổng7Javafiles mới/sửa, chưa có controltransport/uploader/streaming.
- Mỗi permission request có UUIDcorrelation/action/data riêng, package-scoped mutable one-shot PI; API31explicitMUTABLE/API33NOT_EXPORTED/typedparcelable, không unsafeimplicitflag. Receiver parse nhanh, acquisition/hasPermission/listchecks/open/claim/cleanup nằm ngoài main; deadline logical không đóng connection đang dùng.
- Application attachment epoch +snapshot; duplicate/stale/wrongdevice callbacks không open. Liveidentity/quyền được kiểm trước/sau open/claim; interfaceID/alternate thay vì arrayindex; forcefalse, receipt chỉ sau claimtrue. Khi đổiidentity/permission hoặc cleanupfail giữcause/suppressed và exactly-once ownership.
- Khởi tạo không tự request/open; bindings chưa gắn app/service/registry/XML. Constructors và tests fake không gọi manager thật. Không đổi EP0encoding/I²Catomicity/FirmwarePlan/Retry/wireprotocol/JNI. Bổ sung ADR013 và5hồ sơ.

### JVM regression và build/lint

Child environment dùng MachineJAVA_HOME đã xác minh ở phase trước, không sửa User/Machine:

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

**PASS exit0/4m48s/241tasks53executed188up-to-date.** Unit snapshot151methods; instrumentation19drivers+2dvbservice=21tests,0failure/error/skipped; debug/lintPASS. Sau đó chỉ thêm1JVMtest identity-changed-during-open/cleanupfailure assertion và chạy lại toàn unit/build/lint:

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit0/2m8s/177tasks22executed155up-to-date.** XML cuối: drivers138/usbxfer1/dvbservice13=**152methods**,0failure/0error; binding26methods mới. Bridge17/Firmware19/Session17/Concurrency10/Coordinator32 tiếp tục PASS. LintXML0Error/0Fatal/11Warning (Autofill1/GradleDependency2/IconLauncherShape3/LabelFor1/TextFields1/UnusedResources3), không suppression/baseline mới. Warning source/target8obsolete/deprecatedAPI vẫn hiện, không nâng toolchain để né.

Kiểm độc lập cuối: `javac --release 8 -cp <JUnit4.13.2;Hamcrest1.3 từ cache sẵn có> -d drivers\build\p23-java8-check <6pure-corefiles và toànTBSJVMtestfiles>` PASS, javap Tbs5530UsbBinding majorversion52. JUnitCore Tbs5530UsbBindingTest +Tbs5530ConcurrencyTest chạy3lượt, mỗi lượt **OK(36tests)**/0.448s/0.484s/0.345s, gồm120latch-controlled iterations của concurrencyloop qua3lượt. Không dependencydownload; outputcompile riêng đãxóa trongfinally, xác nhận không còn.

### AVD và instrumentation

- `adb.exe devices -l` ban đầu không có device; SDKemulator -list-avds với ANDROID_AVD_HOME=D:\Android\AVD có TBS5530_Runtime_API37. AndroidCLI emulator start --headless báo sai “Device ... doesn't exist” nhưng exit0; không coi exit0 là bootPASS.
- Fallback gọi `D:\Android\Sdk\emulator\emulator.exe -avd TBS5530_Runtime_API37 -no-window -no-audio -no-snapshot-save -no-boot-anim` trong shell71; không tạo/tải/wipe AVD. Kiểm explicitserial emulator-5554: sys.boot_completed1, ro.build.version.sdk37, abi x86_64. Image hiện hữu android-37.0/google_apis/x86_64.
- :drivers:connectedDebugAndroidTest thực chạy19methods Tbs5530AndroidUsbFacadeTest: actualregistration/unregistration, actualmutablePI fill-in/oneshot, missing/malformedextras, wrongaction/data/correlation, distinctrequests, selectionID/alternate, claimfalse/releasefalse, post-open/post-claim checks, mainthreadguard, worker shutdown/selfwait, logicaldeadline và blockedopen/claim mainresponsiveness. Toàn bộ Manager/RawConnection fake, không systemUSBdialog hoặc physicalIO.
- :dvbservice:connectedDebugAndroidTest thực chạy DeviceFilterXmlEquivalenceTester::allDevicesAreInXml và NativeLibrarySmokeTest::usbTransferLibraryLoadsWithoutOpeningUsb: **2PASS**, không fakefd/ioctl.
- XML ở drivers/dvbservice `build\outputs\androidTest-results\connected\debug\TEST-TBS5530_Runtime_API37(AVD) - 17.xml` root testsuites lần lượt19/2,0fail/error/skipped; testcase names đã kiểm, không chỉ dựa task exit. Unit XML tại mỗi module build/test-results/testDebugUnitTest; lint ở app/build/reports/lint-results-debug.xml. Không stage outputs/rawlogs/APK.

### No-tuner app smoke và cleanup

- ADB explicit emulator-5554 `install -r app\build\outputs\apk\debug\app-debug.apk` Success; resolve WelcomeActivity; `am start -W` COLD Statusok/TotalTime3453ms, PID5124. AndroidCLI layout cài layout instrumentation server trên AVD để kiểm hierarchy; không systemsettings/SDKchange.
- Emulator hiển thị “System UI isn't responding” riêng. MCPtap_on_text thất bại vì uiautomatorbusy/exit137; fallback AndroidCLI semanticinput `id=android:id/aerr_wait`, chọnWait, appWelcome hiển thị tiếp. Không gộpSystemUIANR vàoappANR.
- Semantic tap Diagnostic rồiSTART: dialog Attention/“No devices found”, appfilteredLogcat chỉ handledDvbException tạiDeviceController.run, khôngFATAL/UnsatisfiedLinkError/SecurityException/appANR trongcửasổkiểm. KhôngTUNE/TSrecord/hardwareoperations. Không xác minh activeDvbService/TS lifecycle khi khôngtuner.
- Dừng helper emulator đúng shell71 bằng stop_powershell sau validation; adb devices cuối rỗng. Không killprocess theo tên/wipeAVD, không dọnSDK/firmware. Không tạo script tạm. Engine/testfiles chuẩn hóaLF/EOF-only cuối, không đổi logic hoặc assertion, không rerun cho EOL-only.

### Bàn giao và giới hạn

Kiểm cuối git diff --check, UTF-8 strict/LF/EOF choJavafiles và relative Markdown links PASS trên12file; staged diff rỗng. Không diff app/usbxfer/dvbservice/build/wrapper/registry/legacy permission helper/DeliverySystem. IDE main/unit/instrumentation không errors; references cuối vẫn sạch, ADBdevices rỗng sau dừng helper. Không script/outputkiểmtra tạm còn lại.

Implemented7Javafiles +5hồ sơ, chưa stage/commit/push/merge; HEAD vẫn635ee4a. Runtime API21/23/31/33/34 NOT RUN; policy branch assertions trên37 không thay runtime coverage. PhysicalUSBHost/systemUSBpermissiondialog/attachdetachdelivery/reusedidentity/nativecancel/firmware/frontend/lock/TS NOT RUN. Main constructor/receiver behavior synthetic được kiểm nhưng binding chưa nốiservice hoặc giữprocess sống tự động.

Phase2.4 cần review ADR013, ownerprocess lifetime/receiver/deadline/identity mapping và testplan thiếtbịAndroid. Hardwarebringup chỉ sau phê duyệt riêng +firmwareprovenance/license/hash/addressmap +commandplan/rollback; không tự thêmregistry/uploader/frontend/TS. Dừng Phase2.3.

## 2026-11-03 12:57 +07 — Checkpoint commit/push Phase2.3 theo yêu cầu

- Người dùng phê duyệt commit các thay đổi Phase2.3 và push nhánh hiện tại. Preflight Windows PowerShell/main repo: chore/modernize-android-build, HEAD635ee4a57267de6055126efcfee6121b30701433; live origin cùngSHA, staged diff rỗng, đúng12file (7Javafiles và5hồ sơ), diff --check PASS.
- Message `feat: add Android USB lifecycle binding and instrumentation`, có Co-authored-by trailer. Chỉ stage12file đã kiểm, không outputs/APK/logs/firmware/reference. Push explicit origin/chore/modernize-android-build không force, sau đó xác minh SHA remote và working tree; hash và kết quả thực báo sau kiểm tra.
- Không chạy lại tests cho tác vụ Git/documentation checkpoint. Bằng chứng152unit methods/21instrumentation/debug/lint/stress ở mục Phase2.3 là kết quả thực gần nhất, không gọi là lượt PASS mới. Không đổi production logic/toolchain/dependencies/hardware, không bắt đầu Phase2.4.

## 2026-11-03 13:11–13:40 +07 — Phase2.4A control transport và kế hoạch2.4B

### Khôi phục, contract và nguồn

- Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`. Đọc instructionsv4/scopedhardware/usbipd/build/records, skills project-handoff/tbs-research và latestSTATUS/ENVIRONMENT/RESEARCH/DECISIONS/WORKLOG/TEST-MATRIX. Review ADR010–013, coreControlIo/Bridge/Session/Coordinator/Binding/Facade/Runtime/FirmwarePlan. Main sạch chore/modernize-android-build tại8a004a3cc94d8c736da84cad1908b6a270b789a0; liveorigin cùngSHA, xácnhậnPhase2.3 đãpush.
- References đầu/cuối `git --no-lazy-fetch -C <path> rev-parse HEAD/status --porcelain=v1 -uall` với GIT_NO_LAZY_FETCH=1/GIT_OPTIONAL_LOCKS=0: TBS2ce787de9f6e81b4294692a45de15596ae2e1322, Cc5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go11228b05fb1626bf41c4e1879ace10cd0a8d575c; cảba0entries. Đọc official/Ctbs5530_op_rw/I²C/properties vàGoControlRead/Write, khôngfetch/index/config/sourcewrites.
- GoogleUsbDeviceConnection/UsbEndpoint docs xácnhận offsetAPI18/EP0/countnegative/claimforce/closecontract; sourcegroundedvsWindowsWSLhistoricvsAndroidNOTRUN táchởRESEARCH. GitNexus list_repos khôngtargetindex, khônganalyze/giảgraphsafe. Buildpins currentAGP9.5.0/Gradle9.8.2/checksum/compile37.2/min21/Java8, SDKplatformrevision2/PreviewSdkInt0 khôngđổi.

### Implementation và ownership

- ThêmTbs5530ControlTransport: package-privateconstructor, factoryCoordinator.controlIo(Request)/Binding.controlIo chỉ cấp lease currentclaimed; Session.requireOwned xácminh actualresourceidentity. Khôngconnectionthứhai, khôngclose/opentrongmỗicall. Lease/Bindingforward methods đếnconnectionowned hiệncó; defaultConnectioncontrol/metadatafailclosed giữfakeportscompatible.
- RawConnectioncontrolTransfer được AndroidManager forward tới cùngUsbDeviceConnection vớiđủtype/request/value/index/buffer/offset/length/timeout. OwnedConnectionguardworkertrướclock, claimed/notclosed/released; rawcall+release/close dướiownershiplock; Sessionactivecount/deferredcleanup chặnuseafterclose. No retry/reset/recoveryvendorwrites/READY.
- Bufferscratch giữpayload/offset, INcopyrequested slice chỉ khi đủcount+currentgeneration, lỗi/staleoutputkhôngpublish; OUT khôngmutatecaller. Validate8/16bitfields/bounds/0..65535length/positive timeout. Shortvsnegative/overlongkháckind, explicittimeoutcausegiữ, AndroidnegativekhôngtựgánTIMEOUT.
- ThêmimmutableDescriptors vàvalidationreuseBridge.selectBulkEndpoint82; interfaceID/alternate/direction/type/addressduplicate/maxpacket. Packet64/512 làguardsoftwareprofile, khôngspeedproof; khôngyêucầu81choEP0. KhôngsetInterface tựđộng, nonzeroalternateactivationchưaverified, khôngTSreader/nativechanges.
- Thêm14ControlTransportunitmethods+10Descriptorsmethods; Binding26→27 thêmcapabilityfactory/endtoend singleopen; instrumentationFacade19→28 thêm9control/metadata/managedendtoendtests. Không bỏassertionscũ, sửaFakeRaw để códescriptors thậtgiảphùhợpvalidationmới.
- Cậpnhật5records/ADR014 vàkếhoạch2.4B. Khôngregistry/XML/service/legacydriver/TCP/FirmwarePlan/Bridgebytes/build/dependencieschanges, khôngdebugentrypointphysicalmới.

### Validation thực

VSCode runTask assembleDebug trảTasknotfound, fallback Gradlewrapper; khôngsửatasks. Childenv:

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_AVD_HOME = 'D:\Android\AVD'
```

Focused command:

```powershell
.\gradlew.bat :drivers:testDebugUnitTest --tests '*Tbs5530ControlTransportTest' --tests '*Tbs5530UsbBindingTest' --tests '*Tbs5530DescriptorsTest' --console plain --no-daemon --max-workers=1
```

**PASS exit0/2m13s/23tasks5executed18up-to-date**, XMLTransport14/Descriptors10/Binding27=51methods,0failure/error. Finalcombined:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :drivers:testDebugUnitTest --rerun :usbxfer:testDebugUnitTest --rerun :dvbservice:testDebugUnitTest --rerun :drivers:connectedDebugAndroidTest :dvbservice:connectedDebugAndroidTest :app:assembleDebug :app:lintDebug --console plain --no-daemon --max-workers=1
```

**PASS exit0/8m8s/241tasks50executed191up-to-date.** UnitXMLdrivers163/usbxfer1/dvbservice13=**177methods,0failure/error**. InstrumentationXMLroot testsuites:drivers28+dvbservice2=**30tests,0failure/error/skipped**. Haiexistingtests làDeviceFilterXmlEquivalenceTester::allDevicesAreInXml vàNativeLibrarySmokeTest::usbTransferLibraryLoadsWithoutOpeningUsb. DebugAPKbuildPASS, lintXML0Error/0Fatal/11Warning, khôngsuppress/dependency/toolchainupgrade. Source/target8obsolete/deprecatedAPIwarnings vẫnhiểnthị.

PureJava8check dùngJDKMachine/javac --release8 cho8purecorefiles vàTBSJVMtests, JUnit4.13.2/Hamcrest1.3 từcachesẵncó, khôngAndroidclasspath:PASS; javapTbs5530ControlTransport majorversion52. JUnitCoreTransportTest+ConcurrencyTest chạy5lượt mỗi24methods: **OK(24tests)**/5.174s/2.102s/4.118s/3.252s/5.256s, gồm200latch-controlled raceiterations qua5lượt. Output drivers/build/p24-java8-check xóatrongfinally, xácnhậnkhôngcòn.

### Emulator và phạm vi chứng minh

- ADBdevicesbanđầurỗng. AndroidCLIhelp kiểmtrướcstart; emulator-list cóTBS5530_Runtime_API37 nhưng CLIstart báoDevicekhôngtồntại/exit0sai; fallback explicit `emulator.exe -avd TBS5530_Runtime_API37 -no-window -no-audio -no-snapshot-save -no-boot-anim` shell105 vớiAVDhomeD:\Android\AVD. Khôngnewimage/AVD/wipe. Kiểmboot_completed1/API37/x86_64/explicitserialemulator-5554.
- Instrumentation tấtcảManager/RawConnectionfake. Productionframeworkwrapper chỉcompile, khôngcallcontrolonrealUSB. Managedendtoendtest facade→coordinator→control→FakeRaw kiểmexactIN/OUT vàsingleopen/claim/cleanup. Descriptor/directrawtests+mainthreadprotection thựcchạytrênemulator, khôngđượcgọihardwarePASS.
- Dừngemulatorhelper đúngshell105 sauvalidation, adbdevicesrỗng; khôngkillprocessbằngtên hoặcchangesystemUSB. KhôngchạyappUIjourneymới vìphasekhôngđổiappUI, khôngtái sửdụngPhase2.3UI PASS nhưtestmới.
- Evidence trongmodulebuild/test-results/testDebugUnitTest vàbuild/outputs/androidTest-results/connected/debug XML, app/build/reports/lint-results-debug.xml, debugAPK; khôngstageoutputs/rawlogs. IDEproblems0errors. EOL-onlynormalize haiJavafilesmới cuối, khônglogicchange, khôngrerunchoformatting.

### Phase2.4B và bàn giao

Toàn bộ2.4B **NOT RUN**, chưacóapprovalriêng. RESEARCHchứa environment/OTG/nguồn/AndroidHost/enumeration/permissionmanual/openclaimforcefalse/releaseclose/detachcleanup checklist, stopconditions/rollback vàevidencetable AndroidAPI/VIDPID/descriptors/thread/time/code/state/hardwarechange. Khôngphysicaldiagnosticentrypoint hiệnđểchạytựđộng. Chỉsauapprovalcụthể mới pháttriển/chạyharnessdefaultinactive; khôngvendorwrites/I²C/firmware/frontend/TS trongpreflight.

Kiểm cuối: git diff --check, UTF-8 strict, LF/EOF Javafiles và relative Markdown links đều PASS trên16file; staged diff rỗng. Không diff build/wrapper/app/usbxfer/dvbservice/registry/Bridge/FirmwarePlan. IDE không errors; ba references sạch, output compile riêng/emulator helper đã dọn. Không physical preflight hoặc USB state change trong phase.

HEAD vẫn8a004a3, chưastage/commit/push/merge. Owner tươnglai giữbinding/runtime/capability theosession, invalidate trướcshutdown, drainoffmain, khôngglobalUSBservice mới. Callkhôngtrả→PENDING/chặnreopen, khôngforceclose; missedreusedidentity khônggiảchắcchắn. Dừng2.4A+kếhoạch2.4B, khôngbringup tựđộng.

## 2026-11-03 15:05 +07 — Checkpoint commit/push Phase2.4A theo yêu cầu

- Người dùng phê duyệt commit các thay đổi và push nhánh hiện tại. Preflight Windows PowerShell/main repo: chore/modernize-android-build, HEAD8a004a3cc94d8c736da84cad1908b6a270b789a0; live origin cùng SHA, staged diff rỗng, đúng16file (11Javafiles và5hồ sơ), diff --check PASS.
- Message `feat: add managed USB control transport and descriptor validation`, có Co-authored-by trailer. Chỉ stage16file đã kiểm, không outputs/APK/logs/firmware/reference. Push explicit origin/chore/modernize-android-build không force rồi xác minh SHA remote và working tree; hash và kết quả thực báo sau kiểm tra, không ghi trước thành công.
- Không chạy lại tests cho tác vụ Git/documentation checkpoint. Bằng chứng177unit methods/30instrumentation/debug/lint/stress trong mục Phase2.4A là kết quả thực gần nhất, không gọi là lượt PASS mới. Không đổi logic/toolchain/dependencies/hardware. Phase2.4B vẫn NOT RUN, cần phê duyệt riêng; không bắt đầu firmware/frontend/TS.
