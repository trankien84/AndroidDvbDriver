# Architecture decision record (ADR)

## ADR-008 — Phase1.1 runtime không tuner và sửa system-bar insets có giới hạn
**Date:** 2026-11-02
**Status:** Accepted trong phạm vi Phase1.1 đã yêu cầu; không phê duyệt portdriver/hardware.
**Context:** Migration6da609bđãpush; chưa runtime. Không cóAVD/device, chỉimage37.0GoogleAPIsx86_64local. Người dùng xác nhận tạoAVDriêng; không tảiimage/SDKmới. ColdbootSystemUIANR có thể cheapp, cần phân biệt app/system. Target37 app UI vẽ dưới systembars checontrols.
**Alternatives:** DùngimageTVx86khôngđúngưu tiên; tảiimage/thiếtbịmới; bỏ quaUIoverlap; hạtarget/opt-outedge-to-edge; ápInsetsvàtestlại.
**Decision:** AVDTBS5530_Runtime_API37 riêng, headless,explicitserial; giữ4KiBpage mặc định, không quảng cáo16KiBruntime. TestregistryXMLhiệncóvà thêm NativeLibrarySmokeTestassertstaticload+directload, không giảfd/ioctl. Một androidTestImplementation project(:usbxfer) cho testcompile, không nâng externaldependencies. Chạy taskkhôngfilter và kiểm XML đủ2test thay chỉ dựaexit0.
**UI decision:** HelperSystemBarInsets dùng AndroidXcoređãcó, chỉAPI35+, systemBars|displayCutout padding và darksystembaricons; gọi3ActivityWelcome/Diagnostic/License. Không thayUSB/serviceprotocol hoặcminSDK;khônghạtargetđểnéedge-to-edge. Bằng chứng before/after screenshot+UIbounds và build/lint đãPASS;API21path giữ behaviorcũnhưngruntimeAPI21chưa chạy.
**Service/permission scope:** Không cóUSB/devicefixture nên DvbServiceforeground/streaming vàUSBpermission **NOTRUN**, không épstartService sai lifecycle. No-tunererrorlàhandledDvbException, không crash; storagepermissionsdeniedkhôngchặnuiđãtest.
**Workspace:** `.gitignore` exception vàworkspacefileđúngđãcólocaltrướclượt, bảo toànhash/relative4roots. Khôngstage/commit hoặc đổi globalsettings. ToolchaininstructionsEnglish cập nhật đúngbaseline9.8.2/9.4.1/JDK25/SDK37.2/NDK30/CMake4.1.2/BT37, giữpolicies.
**Tradeoffs:** EmulatorAPI37x86_64khôngxácnhậnUSBHost/tuner/arm64/16KiB/API21;SystemUIfirstbootANRghiFAILbanđầuvàhồiphụcWait,khônggiấukếtquả. Không thực hiện networkSDKdownload/hardwareownership/referencesrepair.
**Revisit trigger:** ReviewPhase1.1diff trướccommit; bổsungruntimeAPI21/arm64/16KiBtheokếhoạchđượcduyệt nếu cần, không tựtiếnportdriver. Evidence/cmd ởTEST-MATRIX/WORKLOG.

## ADR-001 — Windows primary build, WSL2 diagnostics
**Date:** 2026-11-01
**Status:** Adopted working convention
**Context:** Android SDK/NDK/CMake and official Android CLI are installed under `D:\Android\Sdk` on Windows; WSL2 USB passthrough for TBS5530 works.
**Decision:** Build/ADB/Git from Windows PowerShell, use WSL2 for PyUSB/Linux diagnostic commands, access same repository via `/mnt/d`.
**Alternatives:** All development in WSL2 with duplicate SDK; duplicate repo clones; custom WSL kernel first.
**Tradeoffs:** Single source checkout and SDK; WSL access to NTFS may be slower; must explicitly route commands to correct OS.
**Revisit if:** Linux kernel-module testing becomes necessary or Windows toolchain proves impractical.

## ADR-002 — Hardware source precedence
**Date:** 2026-11-01
**Status:** Adopted
**Decision:** Use official TBS Linux driver as initial protocol reference, community C for fixes/optimizations, Go userspace for transport architecture, AndroidDvbDriver as implementation base. Resolve conflicts by documented evidence and verified hardware behavior, not by repository popularity.

## ADR-003 — No automatic hardware writes
**Date:** 2026-11-01
**Status:** Adopted
**Decision:** Firmware upload, I2C writes, LNB power and device-driver swapping require a bounded test plan and explicit user approval. Read-only USB enumeration and source analysis are allowed.

---
## New ADR template
**ID:** ADR-XXX
**Date:**
**Status:** Proposed/Accepted/Superseded
**Context:**
**Alternatives:**
**Decision:**
**Tradeoffs:**
**Verification/revisit trigger:**

## ADR-004 — Điểm dừng Phase 0 và bảo toàn checkout tham khảo
**Date:** 2026-11-02
**Status:** Accepted trong phạm vi audit người dùng yêu cầu; không phê duyệt sửa Git hoặc hardware.
**Context:** Android HEAD `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`; TBS HEAD `2ce787de9f6e81b4294692a45de15596ae2e1322` có sparse patterns đúng nhưng index thiếu và 81.399 staged deletions. Main có 18 file untracked có trước audit. Workspace bị ignore.
**Alternatives:** Chạy sync -CheckOnly; restore/reset/sparse-reapply; tăng giới hạn Git VS Code; tạm đọc object HEAD local.
**Decision:** Không chạy sync, không sửa index/reference checkout. Đọc blob có sẵn bằng `git show` với `GIT_NO_LAZY_FETCH=1`, `GIT_OPTIONAL_LOCKS=0`, ghi SHA và tình trạng thiếu file. Không che cảnh báo bằng settings. Mọi phương án khôi phục cần một kế hoạch riêng và phê duyệt, giữ lại metadata/trạng thái trước sửa.
**Tradeoffs:** Nghiên cứu protocol tiếp tục được nhưng checkout TBS chưa đủ để build/index. Lệnh diff --shortstat ban đầu có rủi ro lazy-fetch, FETCH_HEAD mtime mới không đủ xác định tác nhân; xem [RESEARCH.md](RESEARCH.md), không tuyên bố không có tác động metadata tuyệt đối.
**Verification/revisit trigger:** Chỉ xem checkout sẵn sàng sau khi phương án được duyệt và kiểm lại index, số thay đổi, đủ source/dependencies thực trên đĩa; không chỉ sparse list.

## ADR-005 — Build baseline trước, JDK chạy build tách bytecode Android
**Date:** 2026-11-02
**Status:** Proposed; **chưa triển khai, chưa được phép cài/nâng**.
**Context:** AGP 8.5.2/Gradle 8.7, compile 34/target 36/min 21, Java 8. `JAVA_HOME` hiện trỏ thư mục không tồn tại, PATH Java 25 chạy được nhưng Gradle 8.7 không hỗ trợ. SDK biến process khác User.
**Alternatives:** Nhảy thẳng latest AGP/Gradle/JDK; hạ target SDK; sửa environment global; giữ toolchain baseline và chọn JDK 17 ở process riêng.
**Decision proposed:** Chọn JDK 17 hợp lệ sau khi được duyệt, SDK duy nhất D:\Android\Sdk; chứng minh baseline nguyên trạng trước. Nâng từng cặp tương thích, native pin riêng, dependencies riêng. Giữ Java source/target 1.8 và minSdk21 nếu không có quyết định khác; giữ native Release -O1. Không sửa global hoặc build files trong audit.
**Tradeoffs:** Có thể cần cài thêm JDK17/platform36/NDK baseline tối thiểu theo bước đã duyệt; chậm hơn nâng tất cả một lần nhưng dễ xác định lỗi/rollback. Các nguồn phiên bản latest chưa đồng bộ nên khóa cặp cụ thể khi bắt đầu, không tự theo latest.
**Verification/revisit trigger:** Commands và tiêu chí ở [RESEARCH.md](RESEARCH.md); unit/native/debug/lint/registry instrumentation/release phải được ghi kết quả thực. Không coi wrapper --version hoặc image đã cài là baseline xanh.

## ADR-006 — TBS5530 userspace, một stream logic và DVB-T2 trước
**Date:** 2026-11-02
**Status:** Proposed, **source-backed nhưng chưa triển khai hoặc thử phần cứng**.
**Context:** Official TBS, community C và Go cùng route hai frontend qua bulk 0x82; register/control dùng EP0. Tuner Ascot3/Freia phụ thuộc chip ID; GPIO satellite có thể cấp nguồn phần board không cần cho T2.
**Alternatives:** Port kernel Linux/module vào Android; chạy toàn bộ Go runtime; hai frontend stream song song; thêm TBS bridge/Java frontend vào AndroidDvbDriver hiện hữu.
**Decision proposed:** Tái sử dụng Android USB permission/connection, native bulk, I2C/frontend contracts, demux/service. Một TBS device, transport vendor có kiểm tra length/errors và lock atomic I2C, firmware provider tách biệt, Sony frontend với chip-ID dispatch; chọn terrestrial 0x82. Chưa init Montage/LNB nếu không có chứng cứ cần thiết; để extension DVB-C/S/S2/S2X sau. Không quảng cáo chuẩn chưa thử.
**Tradeoffs:** Không copy nguyên callbacks/kernel URB/DVB network stack; Go là mẫu phân lớp, không là bằng chứng timing đúng trên Android. Firmware bản quyền/hash/quyền phân phối cần kiểm riêng, không nhúng blob.
**Verification/revisit trigger:** Mock protocol/firmware-tail/error/PLP/unit tests trước; sau đó chỉ hardware theo kế hoạch được duyệt. Gate riêng Android Host → firmware → I2C/chip ID → lock → TS continuity. Chi tiết file/hàm/SHA và rủi ro tại [RESEARCH.md](RESEARCH.md).

## ADR-007 — Nâng trực tiếp toolchain stable trên JDK25, giữ Android API21
**Date:** 2026-11-02
**Status:** Accepted theo yêu cầu mới người dùng13:06+07; thay đề xuất JDK17-first trong ADR-005 cho lần migration này. Không diễn giải thay đổi này là cho phép hardware writes.
**Context:** Người dùng cho phép nâng project/dependencies và sửa tương thích JDK25; ask_user xác nhận giữ minSdk21, references local read-only, không tải file TBS ngoài nhu cầu. Không có baseline chạy trên toolchain cũ/JDK17.
**Alternatives:** JDK17 baseline rồi nhiều mốc; nâng mọi library bất kể minSdk; giữ nguyên build8.7; nâng stable mới nhất có kiểm chứng và giới hạn tương thích.
**Decision:** Nhánh chore/modernize-android-build, HEAD gốc e4c13a5b86ad5ef9be887df58226046dcb42dcd5. Gradle9.8.2 (checksum distribution+wrapper generator chính thức), AGP9.4.1, JDK25.0.5.0.3, compileSdk37.2/target37/min21, BuildTools37.0.0, NDK30.0.16248370, CMake4.1.2. Giữ source/target Java8, không bytecode25. Chỉ cài4 package SDK đã khai báo, không images/firmware. Build tự cài SDK bị tắt để kiểm soát dung lượng.
**Dependency exceptions:** AppCompat1.7.1/Material1.13.0 giữ API21; latest1.8.0/1.14.0 đòi API23. AndroidX test-ext1.3.0/Espresso3.7.0 vẫn API21; JUnit4.13.2 vẫn runner phù hợp source; Mockito5.24.0 thay Mockito1/PowerMock. localbroadcastmanager1.1.0 khai báo trực tiếp vì dependency bắc cầu không còn; không refactor event bus ngoài migration.
**Tradeoffs:** Không chứng minh original baseline xanh; chỉ so behavior qua regression tests hiện có và public API không đổi. Giữ Java8 sinh warning obsolete trên javac25, không ẩn warning bằng flags. Target37 cần kiểm UI/permission/service thực sau này; compile/assemble không thay runtime test. Native64bit alignment16KiB phải đo ELF/ZIP;32bit giữ hỗ trợ và alignment4KiB riêng.
**References policy:** Không chạy Apply script phục hồi TBS hoặc tải572 ancestor blobs; không sửa index/source reference theo quyết định đã xác nhận. TBS checkout blocker độc lập với build Android. Không commit/push hoặc chạy USB/emulator.
**Verification/revisit trigger:** Unit31methods, debug/release/bundle, instrumentation compile, lint và ABI/ELF/ZIP evidence ghi TEST-MATRIX/WORKLOG. Tuner Android/16KiB runtime/permission/service mới vẫn NOT RUN. Chỉ chốt PASS từng gate sau khi lệnh hoàn tất; rollback bằng thay đổi ngược có chọn lọc, không reset --hard/clean hoặc xóa untracked guidance.
