# TBS5530 Android port — current status

## Checkpoint phát hành Phase 2.2 — 2026-11-03 11:33 +07

- Người dùng yêu cầu commit và push11file Phase2.2 trên `chore/modernize-android-build`. Preflight: HEAD local và live origin cùng `6eb23537484a3e1dd263bd5a88f56914c7c5e313`, staged diff rỗng; chỉ có6Javafiles mới/sửa và5hồ sơ.
- Message: `feat: add TBS5530 lifecycle coordinator and safe cancellation`. Hash/kết quả push kiểm từ Git sau thao tác, không ghi trước thành công. Các mục “chưa commit/push” bên dưới là snapshot trước checkpoint.
- Không đổi logic hoặc chạy lại tests trong tác vụ Git; bằng chứng gần nhất là126unit methods PASS/debug PASS/lint0errors11warnings và5lượt stress của Phase2.2. Không hardware/firmware hoặc mở Phase2.3.

## Phase 2.2 — Lifecycle và fake adapter, 2026-11-03 11:25 +07

**Đã hoàn tất phạm vi fake/JVM; chưa tích hợp transport Android hoặc phần cứng.** Nhánh `chore/modernize-android-build`, HEAD `6eb23537484a3e1dd263bd5a88f56914c7c5e313`. Phase2.1/ADR-011 đã commit, fast-forward và push ở chính baseline này; các nhánh feature/docs trước đã xóa local, docs đã xóa origin theo yêu cầu riêng. Không commit/push/merge trong Phase2.2.

- **Hazard tái hiện trước sửa:** hai tests fake blocked transfer + detach/cancel đều FAIL trên baseline vì Bridge giữ session monitor. Sau sửa, lifecycle invalidation không phải đợi physical transfer hoàn tất; cleanup không đóng connection còn được transaction sử dụng.
- **Kiến trúc:** Session có khóa giao dịch riêng và state lock ngắn; cặp0x90/0x91 vẫn atomic. Close/cancel/detach invalidate ngay, chặn giao dịch mới, defer cleanup tới last in-flight transaction và chạy ngoài cả hai khóa. `isCleanupPending`/`getCleanupFailure` phân biệt invalidate với cleanup hoàn tất. Không cưỡng bức hủy synchronous I/O.
- **Coordinator mới:** JVM-only ports cho permission/open/claim; request gắn generation+attachment identity, không chỉ VID/PID. Callback trùng/sai/cũ không acquire. Lease thuộc coordinator cho tới khi Session nhận; rejected/partial acquisitions cleanup riêng đúng một lần. Không Android framework binding/permission dialog/USB transport, không tự READY.
- **PASS thực:** **126 unit methods** (drivers112/usbxfer1/dvbservice13),0failures/errors; debug APK PASS; lint0errors/0fatal/11warnings. Final Gradle2m23s/177tasks41executed136up-to-date. Concurrency10methods chạy độc lập5lượt, cả5PASS, gồm200vòng detach/cancel có điều phối bằng latch. Core/tests compile `javac --release 8`, bytecode52. Xem [TEST-MATRIX.md](TEST-MATRIX.md) và [WORKLOG.md](WORKLOG.md).
- **Readiness riêng biệt:** permission, connection, claim, firmware-upload event, bridge ready, frontend-initialized event, signal lock và streaming không tự thay thế nhau. Chưa có firmware executor/probe/frontend production; các event là fake/caller input.
- **Không đổi:** protocol bytes, FirmwarePlan, JNI/native, driver cũ, registry/XML, TCP ordinals, toolchain/dependencies; ba references giữ SHA khóa/sạch. Instrumentation **NOT RUN** vì chỉ sửa JVM core chưa được nối vào Android runtime; PASS Phase2.1 là bằng chứng lịch sử, không phải lần chạy mới này.

**Còn giới hạn:** synchronous control/open/claim không được API bảo đảm có thể cưỡng bức hủy; nếu không trả về thì giữ tài nguyên và chặn reopen thay vì use-after-close. Deferred cleanup có thể chạy trên transaction thread, cancel không được join chính thread đó. Attachment identity/permission receiver thực và negative return phân loại timeout cần được xác minh ở binding sau. Firmware/USB thật/lock/TS **NOT RUN**.

**Bước trước Phase2.3:** review [ADR-012](DECISIONS.md), chốt Android identity/permission callback/threading/deadline contract, rồi xin duyệt phạm vi framework binding với mock/instrumentation không tuner. Không tự hardware bring-up, firmware upload hoặc production frontend. Working tree gồm6Javafiles mới/sửa và5hồ sơ; chưa stage/commit/push.

## Checkpoint tích hợp Phase 2.1 — 2026-11-03 10:46 +07

- Người dùng yêu cầu commit toàn bộ thay đổi Phase2.1, merge sang `chore/modernize-android-build`, push đúng nhánh đó lên origin và xóa nhánh local `feat/tbs5530-offline-protocol` sau khi xác minh thành công.
- Preflight: feature ở `8588ee7`, target local/origin đều `4cf1b64`; target là tổ tiên nên có thể fast-forward, bao gồm cả checkpoint tài liệu Phase2.0. Phạm vi13file:8Javafiles và5hồ sơ; không outputs/reference/firmware.
- Message commit: `feat: add offline TBS5530 USB protocol foundation`. Hash và kết quả merge/push/delete phải kiểm từ Git sau thao tác; mục này ghi yêu cầu và preflight, không khẳng định push trước khi chạy. Những trạng thái “chưa commit/push” bên dưới là lịch sử trước checkpoint.
- Không chạy lại build/test cho tác vụ Git; bằng chứng gần nhất vẫn là83unit methods PASS/debug PASS/lint0errors11warnings của Phase2.1. Không thay code hoặc mở phạm vi hardware.

## Phase 2.1 — Nền tảng giao thức USB offline, 2026-11-03 10:39 +07

**Đã hoàn tất phạm vi offline, chưa hỗ trợ phần cứng TBS5530.** Nhánh `feat/tbs5530-offline-protocol`, HEAD nền `8588ee7661003052b7d1a0353e23bb3a3dcef8b5`. Nhánh được tạo từ checkpoint Phase 2.0 sạch; modernization giữ nguyên `4cf1b64`. Có tám file Java mới và năm hồ sơ cập nhật, **chưa stage/commit/push**.

- **Đã triển khai:** `Tbs5530ControlIo` là giao diện thay thế được bằng fake; `Tbs5530Bridge` mã hóa EP0/I²C và chọn descriptor Bulk IN 0x82; `Tbs5530FirmwarePlan` kiểm dữ liệu giả và tạo kế hoạch bất biến; `Tbs5530Session` quản lý trạng thái, generation và quyền sở hữu tài nguyên. Không có implementation gọi Android USB/JNI, uploader hoặc registry entry.
- **PASS thực:** 52 test methods mới (Bridge17/Firmware19/Session16), tổng **83 methods** trong drivers69/usbxfer1/dvbservice13, không failure/error; debug APK và lintDebug **0 errors, 11 warnings**. Lượt cuối Gradle exit0, 1m45s, 177 tasks:41 executed/136 up-to-date. Bốn core class compile riêng bằng `javac --release 8`, class version52; không cần Android SDK/JNI trên classpath. Chi tiết lệnh và kết quả ở [WORKLOG.md](WORKLOG.md), [TEST-MATRIX.md](TEST-MATRIX.md).
- **An toàn đã kiểm bằng fake:** lỗi/timeout/short transfer không retry; session lỗi chặn I/O tiếp, cặp 0x90/0x91 không xen ngang, detach trong callback không gửi nửa read còn lại; firmware failure tại từng bước không READY; cleanup theo cancel → release → close đúng phần tài nguyên đã sở hữu, chỉ một lần.
- **Nguồn giữ nguyên, working tree sạch khi kiểm lại:** TBS `2ce787de9f6e81b4294692a45de15596ae2e1322`; C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`; Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`. Không sửa references/toolchain/dependencies/driver cũ/API TCP.
- **Giới hạn:** permission và READY là sự kiện do caller cung cấp, chưa có Android permission adapter hoặc readiness probe; cancellation không cưỡng bức ngắt một synchronous transfer đang chạy. Chip/PLP chỉ là policy fixtures, chưa có register programming. Firmware thật, reconnect Android, USB transfer/native cancellation, lock và TS đều **NOT RUN**; nguồn gốc/quyền phân phối firmware và bản đồ địa chỉ thật vẫn chưa duyệt.

**Bước an toàn tiếp theo:** review để chốt core offline, sau đó chỉ mở Phase 2.2 riêng cho lifecycle adapter với fake/instrumentation không tuner. Cần thống nhất quyền sở hữu callback đến muộn, cancellation và identity sau reconnect trước khi nối transport Android. Chưa phê duyệt firmware thật, frontend production hay USB hardware. Các snapshot “chưa triển khai/NOT RUN Phase2.1” bên dưới là lịch sử trước lần thực thi này.

## Checkpoint Phase 2.0 — 2026-11-03 00:58 +07

- Người dùng yêu cầu commit và push năm hồ sơ nghiên cứu/thiết kế cùng kết quả xác minh checkout NTFS đã phục hồi. Nhánh phát hành là `docs/tbs5530-source-architecture`, không merge vào modernization.
- Message: `docs: finalize TBS5530 architecture and source readiness`. Hash của checkpoint tra `git log -1`; trạng thái push phải đối chiếu origin sau thao tác. Những mục “chưa commit” bên dưới là lịch sử trước checkpoint.
- Đã kiểm diff, UTF-8 và liên kết tương đối; không chạy lại build/hardware vì chỉ sửa tài liệu. Không đưa source tham khảo, backup hoặc script tạm đã xóa vào commit. Bước tiếp theo vẫn là phê duyệt Phase 2.1 offline foundation.

## TBS checkout đã phục hồi trên NTFS — 2026-11-03 00:54 +07

**Đã kiểm chứng trực tiếp sau khi người dùng tự chạy script phục hồi:** TBS vẫn ở nhánh `latest`, SHA `2ce787de9f6e81b4294692a45de15596ae2e1322`; Git index tồn tại, working tree sạch, không còn 81.399 staged deletions. Sáu sparse paths giữ nguyên.

- Đủ **1.357 file** theo cone manifest tại HEAD, **0 file thiếu/thừa, 0 hash khác**. Các source TBS5530/CXD2878/M88RS6060 và helpers đã có trên đĩa. Không còn blocker checkout đã mô tả trong snapshot Phase2.0 bên dưới.
- Không có index.lock; không lưu override `core.protectNTFS`. Backup thành công còn nguyên tại parent `logs/tbs-sparse-before-20261103-004911-951`, có after.json PASS. Giữ backup; chỉ dọn script tạm theo yêu cầu trước của người dùng sau khi xác minh.
- C/Go working tree sạch. Agent chỉ kiểm đọc reference; phục hồi do người dùng thực hiện. Không build kernel hoặc kiểm thử phần cứng, không thay toolchain.
- Main vẫn nhánh `docs/tbs5530-source-architecture`, HEAD4cf1b64, các hồ sơ chưa commit. **Bước tiếp theo:** review và phê duyệt Phase2.1 offline foundation; không tự bắt đầu driver production/USB writes.


## Phase 2.0 — Source readiness và kiến trúc, 2026-11-03

**Baseline ổn định:** `chore/modernize-android-build` tại **`4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe`**, đã push ở Phase 1.2. Working tree sạch khi bắt đầu. **Nhánh làm việc:** `docs/tbs5530-source-architecture`, cùng HEAD, chỉ thay năm hồ sơ; chưa stage/commit/push. Không đổi toolchain, code production hoặc repository tham khảo.

### Mức sẵn sàng đã kiểm lại

- **Checkout TBS vẫn BLOCKED:** SHA `2ce787de9f6e81b4294692a45de15596ae2e1322`, nhánh latest. Git index bị thiếu; status có 81.399 staged deletions. Sáu thư mục sparse đã cấu hình nhưng đều có 0 file. `read-tree --dry-run` vẫn trả exit128 do tên AUX trong phần cây ngoài sparse; index và lock không thay đổi.
- **Đủ đọc nguồn qua Git objects trong phạm vi nghiên cứu:** 785 blobs ở sáu thư mục và **572 blobs ở các thư mục cha đều có local**, tổng cộng 1.357 file dự kiến khi materialize. **572 blobs thiếu là kết quả lịch sử, không còn đúng ở lần kiểm này.** Không tải objects trong phase; chưa biết tác nhân đã bổ sung trước đó. Source board/Sony/Montage, helpers và Linux headers trực tiếp đọc được nhưng chưa có file trên đĩa. Không chứng nhận có thể build toàn kernel.
- **C/Go sạch:** C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`. Không fetch/sync/reset/repair hoặc đổi config. Kế hoạch backup, rollback, tiêu chí phục hồi và so sánh NTFS với ext4 WSL ở RESEARCH; mọi phục hồi cần phê duyệt riêng.

### Kết luận thiết kế mới

- USB control/I2C đi qua **EP0**; MPEG-TS dùng một bulk endpoint **0x82**. Không nhầm với 0x81 hoặc cơ chế bulk-control của CxUsb. Firmware TBS dùng ảnh nhị phân raw, không dùng loader Intel HEX generic.
- **PLP là luồng con của kênh DVB-T2.** Official ghi lại lựa chọn PLP mỗi lần tune; nhánh retune cùng chuẩn trong Go chỉ đổi bandwidth. Đề xuất mặc định **manual PLP0**, không gọi là auto. API/TCP cho người dùng chọn PLP để sau.
- **Chip dispatch khác nhau giữa các nguồn:** official chọn Ascot3 cho 0x197, Freia cho 0x19c/0x39a; community mở thêm ID. Không dùng fallback mọi ID khác là Freia. Chip chưa biết phải bị từ chối an toàn; silicon thực tế vẫn chưa đo.
- **Retry/lifecycle:** `Retry.retry(4)` thực có tối đa năm attempts. `DvbUsbDevice.open/close` là final và tự retry, có nguy cơ lặp firmware/reset. Adapter production tương lai dự kiến extends **DvbDevice**, kết hợp permission/I2C/frontend/native TS/demux hiện có, không refactor các driver cũ. Sau lỗi làm trạng thái thiết bị không chắc chắn, không gửi IO tiếp hoặc tự phục hồi phần cứng.
- **Phase 2.1 đề xuất chỉ làm nền tảng offline:** ControlIo/Bridge/FirmwarePlan/Session và scripted fakes với assertions. Chưa đăng ký VID/PID, chưa viết adapter USB thật, uploader hay frontend production. Danh sách file, thứ tự công việc, rủi ro và test cases nằm ở RESEARCH/ADR-010. Toàn bộ tests mới **NOT RUN**.

**Điểm dừng:** hoàn tất nghiên cứu và thiết kế Phase 2.0; chưa phục hồi checkout. Không firmware/I2C/vendor writes/reset/usbipd hoặc test phần cứng. Không build/regression mới vì chỉ sửa tài liệu. **Một bước an toàn kế tiếp:** xin duyệt Phase 2.1 offline foundation theo ADR-010; tách việc phục hồi checkout TBS thành phê duyệt riêng.

Nguồn khóa SHA, sơ đồ, phương án phục hồi, file list và tiêu chí PASS/FAIL: [RESEARCH.md](RESEARCH.md). Quyết định: [DECISIONS.md](DECISIONS.md). Readiness và tests dự kiến: [TEST-MATRIX.md](TEST-MATRIX.md).

## Checkpoint phát hành Phase 1.2 — 2026-11-02 23:39 +07

- Người dùng yêu cầu add, commit và push toàn bộ thay đổi Phase1.2 lên `origin/chore/modernize-android-build` (fork `trankien84/AndroidDvbDriver`). Message chọn: `build: upgrade AGP to 9.5.0 and fix toolchain inventory`.
- Phạm vi15file: root AGP pins, audit/review/check-env và regression tests, instructions/skill, README và5projectrecords; không outputs/cache/SDK/AVD/logs/firmware/reference code. Baseline trước checkpoint là `ace7e9f187d11a685975f707b6743d779fa847d9`.
- Đã kiểm diff và evidence đã lưu: unit31methods0fail, instrumentation2tests0fail0error, lint0errors. Tác vụ Git này không chạy lại build/emulator/hardware. Các snapshot “chưa commit/push” bên dưới là trạng thái trước checkpoint; hash commit chứa mục này tra `git log -1`, trạng thái phát hành phải đối chiếu live origin ref.
- Không merge, force-push, sửa master/upstream hoặc bật hardware gate. Bước tiếp theo là review mốc toolchain đã chốt và lập kế hoạch coverage còn thiếu theo yêu cầu mới, không tự portdriver.

## Phase 1.2 — AGP 9.5.0 và kiểm kê toolchain, 2026-11-02 22:37–23:18 +07

**Đã hoàn tất trong phạm vi phần mềm, chưa commit/merge/push.** Nhánh vẫn `chore/modernize-android-build`, HEAD `ace7e9f187d11a685975f707b6743d779fa847d9`. Bảo toàn sáu file hướng dẫn/công cụ local người dùng chuẩn bị trước phase; không đổi Git history hoặc reference repositories.

- **Toolchain cuối:** AGP **9.5.0 stable** (Google Current Release + final notes + exact POM xác minh ngày kiểm); Gradle **9.8.2**, JDK **25.0.5.0.3**, compileSdk **37.2**, target **37**, min **21**, NDK **30.0.16248370**, CMake **4.1.2**, Build-Tools **37.0.0**, Java source/target **8** giữ nguyên. Chỉ đổi hai plugin pins trong root build.gradle, không nâng dependencies/SDK khác.
- **PASS thực:** debug/release APK và release AAB; **31 unit methods/0failure** chạy lại với task --rerun (gồm một utility), lint **0errors/11warnings**; instrumentation **2tests/0fail/0error** trên AVD Android17/API37/x86_64/4KiB, gồm native-load và registry/XML. Debug/release đủ4ABI, ZIP16KiB PASS và ELF64bit LOAD0x4000. Không coi đây là runtime16KiB/arm64/AndroidAPI21 hoặc USB thật.
- **Audit đã sửa:** resolved AVD home truyền vào emulator listing process rồi restore; bỏ tên trùng suffix `.avd`; kết quả một AVD thật `TBS5530_Runtime_API37`, emulator1/metadata1. Thêm structured -PassThru và JDK candidates, không tự đổi globalenv. CMake cleanup parser giới hạn đúng block. Check-env dùng chung audit, regression script tự chứa PASS, không cài thêm framework test.
- **Inventory sau user cleanup:** platforms34/37.2, BT37, NDK30, CMake4.1.2, cmdline23, ADB37.0.1, emulator37.2.13, hai images34TVx86/37.0GoogleAPIsx86_64. Đã bỏ packages cũ khỏi snapshot hiện tại nhưng không sửa lịch sử. Phase này không uninstall/install/update SDK hoặc xóaAVD.
- **JAVA_HOME:** Machine đúng, Process agent vẫn đường dẫn cũ; Gradle child nạp Machine và wrapper xác nhậnJDK25. Không thay registry/User/Machine env. Script ban đầu bị execution policy chặn; người dùng chỉ định Bypass ScopeProcess, đã làm đúng childscope và restore, không bật bypass mặc định trong script/task.
- **Caveats đã ghi:** lần build đầu lỗi Windows unable-delete nativeoutputx86; rerun đúng task thành công, không xóa cache/kill processes. AVD boottimeout4phút ban đầu rồi responsive; launcher/SystemUI ANR hồi phục Wait, không appANR. App cài/mở/no-tuner/insets/Back smoke PASS, filteredlog0fatal/linker/security. Warning Java8obsolete và AGP Configuration.setVisible vẫn còn, không suppress để xanh.
- **Instructions/toolchain docs:** giữ English guidance do người dùng chuẩn bị với bốn trạng thái declared/installed/publishedstable/projectverified; README ghi baseline có ngày, không gọi version mãi latest. `toolchain-audit` có trên đĩa và đã đọc trực tiếp dù skill dispatcher chưa discover; không tự sửa globalhost/plugin để ép nhận.
- **Cleanup:** AVD đã được tắt đúng serial sau smoke; không wipe dữ liệu. Không TBS/usbipd/firmware/vendor/I2C writes, không chạm references. Không portdriver.

**Bước an toàn tiếp theo:** review diff Phase1.2 và commit có chọn lọc nếu được yêu cầu. Vấn đề Processenvstale/Windowsnativefilelock/emulatorANR vẫn phải theo dõi; mở coverage API21/arm64/16KiB chỉ theo kế hoạch riêng. Dừng Phase1.2. Chi tiết [ENVIRONMENT.md](ENVIRONMENT.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md), ADR-009 tại [DECISIONS.md](DECISIONS.md).

## Tích hợp Phase1.1 — 2026-11-02 21:35 +07

- Theo yêu cầu người dùng, đã commit 15 file Phase1.1: **`b51a77e18e363750692d83cc53974b64718e1f51`**, `fix: stabilize Android runtime after toolchain upgrade`.
- Đã merge **fast-forward** từ `test/android-runtime-smoke` vào **`chore/modernize-android-build`**, push origin fork `trankien84/AndroidDvbDriver`; `git ls-remote` xác nhận target remote ở đúng `b51a77e` sau push.
- Nhánh test local đã xóa an toàn bằng `git branch -d` sau khi xác nhận push. Không có nhánh test tương ứng trên origin tại kiểm tra, nên không có remote branch cần xóa. Không force-push, không đổi master/upstream hoặc repository tham khảo.
- Hồ sơ cập nhật trạng thái tích hợp ở một commit tài liệu tiếp theo; dùng Git log/remote ref để lấy HEAD cuối. Các snapshot “chưa commit/push” bên dưới là lịch sử trước tích hợp, không phải hiện trạng.
- Kiểm chứng trong tác vụ Git: staged diff/whitespace PASS, không artifacts/secrets theo mẫu rà, báo cáo instrumentation Phase1.1 vẫn2tests0fail0error và lint0error. Không chạy lại emulator/build/hardware trong tác vụ này.

**Bước tiếp theo:** review mốc runtime đã tích hợp và chọn kế hoạch kiểm thử coverage còn thiếu khi được phê duyệt. Không tự chuyển sang port driver hoặc ghi phần cứng.

## Phase 1.1 — Android runtime smoke, 2026-11-02 19:31–20:05 +07

**Baseline đã commit/push:** `6da609ba49dd9f1f4d284291918f68a6378972ff` trên `chore/modernize-android-build`, origin fork `trankien84/AndroidDvbDriver`; remote-tracking ref local trùng HEAD và người dùng xác nhận đã push. **Nhánh làm việc hiện tại:** `test/android-runtime-smoke`, cùng HEAD, thay đổi Phase1.1 **chưa commit/push/merge**. Những đoạn “chưa commit” về migration bên dưới là lịch sử. Emulator thử nghiệm đã được tắt, dữ liệu AVD giữ lại để tái sử dụng.

- **Runtime đã chạy:** AVD riêng `TBS5530_Runtime_API37`, Android17/API37, Google APIs x86_64, page size4096; dùng image37.0 đã cài, không tải SDK/image. Không phải runtime37.2 hoặc thiết bị Android thật. Có user approval tạo AVD riêng vì ban đầu chưa có AVD/device.
- **PASS:** cài/mở APK, Welcome→License→OK, Diagnostic→Start→`No devices found`→OK, Home/resume, Back và Close/relaunch. PID sống, không app crash/ANR/linker/security failure trong log đã lọc; handled DvbException khi không tuner là kết quả mong đợi. `dtvdriver://` chooser kết thúc không có device, không khởi DvbService sai lifecycle.
- **PASS thực instrumentation:** `:dvbservice:connectedDebugAndroidTest` không filter, XML **2tests/0fail/0error**: registry/XML equivalence và native library smoke. `UsbHiSpeedBulk.IS_PLATFORM_SUPPORTED=true`, direct `System.loadLibrary("UsbXfer")` thành công trên x86_64; không tạo USB requests hoặc ioctl. Runtime16KiB/arm64/USB thật chưa thử.
- **Lỗi giao diện phát hiện và đã sửa:** target37 vẽ dưới system bars, che hàng tune/status text và Close sát gesture bar. Helper `SystemBarInsets` áp dụng insets systemBars/displayCutout cho3Activity, chỉ API35+; trước/sau có screenshot+UIbounds. Không đổi tuner/service/protocol, không tắt edge-to-edge bằng workaround targetSdk.
- **Vấn đề môi trường:** firstboot có ANR `com.android.systemui` (không phải app); chọn Wait qua UI và overlay hết. App exit-history chỉ có PACKAGE UPDATED do cài APK sửa; chưa chứng minh emulator không có lỗi SystemUI trên mọi lần boot.
- **Build/lint/regression:** debug+testAPK buildPASS; assembleDebug/lintDebug sau fixPASS, lint0error/11warnings; unit task validationPASS/32s,39UP-TO-DATE (không gọi là chạy lại31test). Toolchain giữ Gradle9.8.2/AGP9.4.1/JDK25/compile37.2/target37/min21/NDK30/CMake4.1.2/BT37.
- **Workspace/instructions:** exception `!TBS5530.code-workspace` và file workspace đã có local trước Phase1.1, giữ nguyên byte/hash; resolve4relative roots đúng, file untracked được phép add (chưa stage). Cập nhật2instruction files bằng tiếng Anh để bỏ snapshottoolchaincũ; giữ chính sách Windows/WSL/usbipd/MCP.
- **Reference recheck chỉ đọc:** SHA TBS2ce787de9f6e81b4294692a45de15596ae2e1322, Cc5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go11228b05fb1626bf41c4e1879ace10cd0a8d575c không đổi. TBSindexvẫnthiếu/81399D,source3filebắtbuộcthiếu; C/Go sạch. Không sync/reset/restore hoặc hardwarecommands.

**NOT RUN:** DvbService foreground/streaming (không tuner/fixture nên không vào luồng hợp lệ), USB permission dialog, USB bulk/I2C/firmware/lock/TS; runtime API21/arm64/16KiB. Phase1.1 không cho phép kết luận TBS5530 hoạt động.

**Một bước an toàn tiếp theo:** review/commit có chọn lọc diff Phase1.1 khi người dùng cho phép; nếu cần tăng runtime coverage, lên kế hoạch test API21 hoặc arm64/16KiB riêng, không tải image/hardware ngoài phê duyệt. **Dừng Phase1.1, không tự port driver.** Kết quả/lệnh/hash ở [WORKLOG.md](WORKLOG.md), [TEST-MATRIX.md](TEST-MATRIX.md), môi trường ở [ENVIRONMENT.md](ENVIRONMENT.md), ADR-008 trong [DECISIONS.md](DECISIONS.md).

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
