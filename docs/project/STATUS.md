# TBS5530 Android port — current status

## Checkpoint Git — 2026-11-02 18:48 +07

- Người dùng yêu cầu tạo commit cho các thay đổi nâng build và bộ hồ sơ/công cụ TBS5530. Message chọn: `chore: prepare TBS5530 development with JDK 25`.
- Nhánh `chore/modernize-android-build`; origin hiện trỏ fork `trankien84/AndroidDvbDriver` qua SSH, upstream giữ `signalwareltd/AndroidDvbDriver`. Khác origin lúc audit đầu vì cấu hình đã đổi ngoài tác vụ này; không tự sửa remotes.
- Kiểm trước commit: unit/debug build command exit0/59s,107tasks(8executed/99UP-TO-DATE); unit tasks dùng kết quả còn hợp lệ, không gọi là chạy lại toàn bộ test methods. Báo cáo31methods0fail, lint0errors/11warnings từ migration vẫn là bằng chứng gần nhất.
- Chỉ đóng gói source/build configs/tests, ignore firmware/captures, hướng dẫn/skills, tasks, scripts và6projectrecords; không binaries build/caches/firmware/TS/logs hay reference repos. Workspace `.code-workspace` vẫn ignored, không force-add.
- Không push trong tác vụ commit; dùng `git log -1` để lấy hash thực tế sau khi hoàn tất. Các snapshot “chưa commit” bên dưới là lịch sử trước checkpoint. Hành động kế tiếp vẫn kiểm thử Android runtime không tuner; không tự thay hardware.

## Hiện trạng mới nhất — 2026-11-02, migration JDK25 đã build/test

**Đã nâng build theo phê duyệt mới người dùng, không còn dừng ở Phase0.** Nhánh `chore/modernize-android-build`, HEAD nền `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`; **chưa commit/push**. Các snapshot bên dưới là lịch sử.

- **Toolchain:** Gradle9.8.2 (wrapper scripts/JAR regenerated, distribution checksum), AGP9.4.1, JDK25.0.5.0.3. compileSdk37.2/target37, **minSdk21 giữ**, Java source/target8 giữ. BuildTools37.0.0/NDK30.0.16248370/CMake4.1.2 đã cài đúng4package trong SDK Windows duy nhất; không images/firmware/reference objects.
- **Latest có điều kiện:** AppCompat1.7.1/Material1.13.0 giữ API21;1.8.0/1.14.0 đòi23 nên không chọn. Test-ext1.3.0/Espresso3.7.0, Mockito5.24.0; bỏ PowerMock/Mockito1. Runtime/public USB/protocol code không sửa; thêm localbroadcastmanager1.1.0 trực tiếp, chỉnh DSL/native config và tests tương thích Windows/Mockito mới.
- **PASS thực:** debug assemble; **31 unit methods0fail** (gồm1utility không regression assertion); instrumentation APK **compile**, chưa chạy Android; release APK unsigned và AAB; lint debug **0errors/11warnings**;4nativeABIs debug/release; ZIP16KiB debug/release và ELF64bit LOAD0x4000. Không coi32bit ELF0x1000 là gate16KiB64bit fail.
- **Lỗi đã xử lý:** missing NDK→cài version đã khai báo; missing transitive LocalBroadcastManager→direct dependency;2unit assertion/mock failures→test-only fixes. Native release từng lỗi Windows không xóa được output khi multiworker; chạy ABI riêng rồi validation tuần tự `--max-workers=1` PASS. Không tắt lint/test hoặc xóa output để né lỗi. Nếu lỗi lock lặp lại, dùng bước tuần tự đã ghi, không kill process khác.
- **Còn warning/NOT RUN:** javac25 cảnh báo source/target8 obsolete; AGP internal Gradle deprecation;11lintwarnings (2dependency pin cố ý, UI/resources). Không tăng minSDK/ẩn cảnh báo chỉ để xanh. Android API21/API37 UI/permission/service, native-load/16KiB runtime, USB Host/TBS firmware/I2C/lock/TS thật **NOT RUN**.
- **References theo lựa chọn mới:** giữ read-only, không chạy Apply script, không tải572blobs ngoài nhu cầu. SHA3refs không đổi, C/Go sạch, TBS vẫn thiếuindex/81399D. Không phải blocker cho Android build đã PASS nhưng chưa có checkout TBS hoàn chỉnh. Không triển khai driver TBS5530.

### Một bước an toàn tiếp theo

Review diff migration và chọn môi trường Android để kiểm thử UI/permission/service/native-load **không gắn tuner**, sau khi được duyệt khởi chạy/cài APK. Không tự boot emulator hoặc viết hardware. Hướng dẫn build ở [README](../../README.md); lệnh/kết quả/hash chi tiết [WORKLOG.md](WORKLOG.md), [TEST-MATRIX.md](TEST-MATRIX.md), quyết định ADR-007 tại [DECISIONS.md](DECISIONS.md).

## Cập nhật mới nhất — 2026-11-02 12:52 +07

- **JAVA_HOME Machine đã được người dùng sửa đúng** sang `C:\Program Files\Java\latest\jdk-25`; executable trả25.0.5.0.3. Process agent còn giá trị cũ; child đọc lại Machine JAVA_HOME chạy Java thành công. Không sửa global/restart IDE. Gradle8.7/JDK25 vẫn không tương thích, build NOT RUN.
- **Kế hoạch TBS đã điều tra bằng dry-run:** index vẫn thiếu/81.399 D;785 blob trong6 thư mục có sẵn nhưng572 ancestor blob của cone sparse còn thiếu. Restore cùng cone không thể mặc định offline.
- Dry-run read-tree mặc định tái hiện `invalid path .../i2c/aux.c` trên Windows; workaround `-c core.protectNTFS=false` **chỉ trong dry-run** trả0, không lưu config và không tạo index. Chưa phục hồi source.
- **Người dùng đã chọn nhận bộ lệnh tự khôi phục.** Đã bàn giao script ngoài Git `Restore-TbsSparseCheckout-Reviewed.ps1`: preview mặc định chạy PASS (1357 files/572 missing); parser0errors; **Apply/backup/fetch/restore NOT RUN**. Có xác nhận trước Apply, full .git backup, khóa SHA và command-scoped workaround. FETCH_HEAD có thay đổi từ tác nhân chưa rõ trong môi trường dùng chung: dừng tác vụ Git khác trước chạy. Next: người dùng đọc script rồi tự chạy và gửi kết quả đã lược thông tin cá nhân; không tự sửa reference/build/hardware. Chi tiết ở đầu RESEARCH.

## Hiện trạng đã kiểm chứng — 2026-11-02 (Phase 0)

**Phase 0 audit/source research hoàn tất; chỉ sửa hồ sơ. Dừng trước migration, driver, emulator và hardware tests.** Snapshot 2026-11-01 bên dưới là lịch sử, không phải kết quả kiểm tra mới.

- **Main:** `master`, `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`; 18 file untracked có trước phiên, gồm hướng dẫn/hồ sơ/scripts. Không commit/push/stage/đổi nhánh. Workspace 4 roots resolve đúng nhưng bị `.gitignore` loại (`*.code-workspace`).
- **References khóa SHA:** TBS `2ce787de9f6e81b4294692a45de15596ae2e1322`; C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`; Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`.
- **TBS checkout BLOCKED:** sparse list đủ 6 nhưng index không tồn tại, `ls-files=0`, 81.399 staged deletions và source cần thiết không có trên đĩa. Blob HEAD local đọc được; chưa sửa references. C/Go sạch. Không dùng sync script kể cả CheckOnly (vẫn fetch). Giới hạn nguy cơ lazy-fetch từ lần diff đầu và FETCH_HEAD mtime ghi minh bạch ở RESEARCH/WORKLOG; không cam kết metadata hoàn toàn không bị tác động.
- **Môi trường:** Windows10.0.19045/PS7.6.9; PATH Java25.0.5.0.3 chạy được nhưng JAVA_HOME trỏ thư mục **không tồn tại**. SDK D:\Android\Sdk có package đúng inventory; process thiếu ANDROID_HOME/ANDROID_AVD_HOME dù User đã đặt, CLI default nhìn đường dẫn khác. Không tự sửa môi trường.
- **Build:** source thực AGP8.5.2/Gradle8.7, compile34/**target36**/min21, bytecode Java8. JDK25 không hỗ trợ chạy wrapper8.7. **Build/tests NOT RUN**, không phải đã build thất bại. Đề xuất baseline JDK17 trước rồi migration nhỏ; nguồn stable/compatibility và lệnh tương lai ở RESEARCH.
- **USB hiện tại:** usbipd5.3.0-54; TBS734c:5530, BUSID đọc live6-2, **Shared**, quyền phía Windows; Ubuntu22.04 Running/WSL2. Không Attached, không chạy lsusb/PyUSB mới, không firmware/reset/I2C/LNB/ownership writes. USB endpoints/claim lịch sử không chứng minh firmware/tuner hoạt động.
- **Copilot/MCP:** đọc đủ guidance/skills; 2 project skills load thành công. Proxy discovery/read-only calls hoạt động; ADB MCP không thấy AVD, GitNexus không có index dự án, AgentMemory query không có match. Không sửa config/index/global settings. Không xác nhận tự động attach mọi scoped instruction qua API; đã đọc chủ động.
- **Kiến trúc đề xuất:** reuse registry/USB Host/native bulk/I2C/frontend/demux/service; TBS extends DvbUsbDevice trực tiếp, EP0 vendor control, một stream **0x82**, Sony frontend chip-ID dispatch Ascot3/Freia; DVB-T2 trước. Cxd2841er là mẫu frontend, **không** là CXD2878 thay tên; CxUsb generic control dùng bulk, **không** dùng được cho TBS EP0. API hiện chưa có PLP, enum chỉ T/T2/C; satellite cần mở rộng giao thức sau. Không mặc định init Montage/stream song song. Firmware hash/quyền phân phối chưa xác minh. Chi tiết file/hàm/SHA ở RESEARCH.

### Một hành động an toàn kế tiếp

**Soạn và xin duyệt kế hoạch khôi phục index/sparse checkout TBS, kèm bảo toàn metadata/trạng thái và tiêu chí kiểm lại; chưa chạy lệnh khôi phục.** Không tự chuyển Phase1. Sau khi giải quyết repository readiness mới xin phạm vi baseline build ở JDK17/SDK đúng.

### Hồ sơ để tiếp tục

[ENVIRONMENT.md](ENVIRONMENT.md): phép dò/phiên bản thực; [RESEARCH.md](RESEARCH.md): nguồn khóa SHA, kiến trúc, migration plan; [DECISIONS.md](DECISIONS.md): ADR-004..006 và trạng thái đề xuất; [TEST-MATRIX.md](TEST-MATRIX.md): PASS/FAIL/BLOCKED/NOT RUN; [WORKLOG.md](WORKLOG.md): lệnh, ngoại lệ, phạm vi thay đổi.

---

## Historical snapshot — 2026-11-01 (giữ nguyên)


**Snapshot date:** 2026-11-01 (created from user-provided Windows/WSL observations; no new commands were run to validate this package).

**Current phase:** Phase 0 — working environment, instructions, and build baseline. USB enumeration/claim diagnostic gate has already passed on WSL2.

**Overall goal:** TBS5530 as an unrooted AndroidDvbDriver userspace USB device; DVB-T2 first, optional DVB-C/S/S2X later.

## Confirmed observations
- Windows recognized `TBS 5530 DVB BDA` (VID:PID `734c:5530`).
- usbipd transferred the device to Ubuntu 22.04 WSL2; `lsusb` and PyUSB saw the tuner at 480 Mbps.
- USB interface 0 claimed and released successfully from PyUSB.
- USB bulk IN endpoints `0x82` and `0x81` reported; both max packet size 512.
- Windows SDK/NDK/CMake/ADB installed at `D:\Android\Sdk`; details in `ENVIRONMENT.md`.

## Not yet verified
- Actual on-disk clone/fork and workspace paths; Gradle baseline build; Android APK install on a target device.
- TBS5530 bridge firmware availability, checksum and hardware upload.
- USB control/I2C operations, DVB-T2 tuner initialization/lock, MPEG-TS capture/continuity.
- Compatibility of planned AGP/Gradle upgrades with installed JDK 25 and project native code.

## Immediate next safe action
1. Confirm main checkout exists and `git status` is clean.
2. Run `scripts/check-env.ps1` on Windows; record actual `android -V` and Gradle wrapper/JVM versions.
3. Create branch `chore/modernize-android-build` only after capturing the baseline; perform incremental migration and build verification; **no hardware writes**.

## Current blockers/risks
- Old upstream Gradle 8.7 cannot be assumed to run under the installed JDK 25; introduce appropriate version upgrades or alternate JDK only in a reviewed plan.
- Android toolchain screenshot does not prove a local APK build succeeded.
- WSL2 USB success does not prove Android userspace firmware/tuner support.

## Working links
- Project environment: `ENVIRONMENT.md`
- Research notes: `RESEARCH.md`
- Progress log: `WORKLOG.md`
- Decisions: `DECISIONS.md`
- Test evidence: `TEST-MATRIX.md`
