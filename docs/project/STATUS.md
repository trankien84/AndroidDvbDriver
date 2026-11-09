# Port TBS5530 sang Android — trạng thái hiện tại

## 2026-11-09, phiên 19:18 +07 — Phase 3.1: đã có EP0 Android; 8192 byte vẫn EOVERFLOW

**Chưa xác nhận nguyên nhân gốc; Android chưa đạt TS_SMOKE_PASS.** Branch/HEAD trước và sau `chore/modernize-android-build` / `48b53d788db5e023123afbc0a65f77a16ae084ef`; giữ hai thay đổi instructions có trước phiên, không stage/commit/push hoặc sửa references.

- **Android đã đo mới:** người dùng duyệt Samsung A/B và xác nhận hai attachment sau ngắt nguồn ≥5 giây, cùng SM-X926B/API 36/boot 46/cáp/nguồn/RF IN. Hai phiên operational có đúng 190 EP0 controls, cùng toàn bộ OUT bytes; K36 AUTO khóa, bank 00/reg 96–97 thực gửi `54/54`, C3 gửi `00`, stream `8A 0A00`. A dùng 8×4096 và B dùng 8×8192: mỗi lượt 8 submit/reap, mọi completion `-75`, actual 0, syscall return/errno 0, outstanding cuối 0, không quarantine; hai file TS rỗng. Kích thước 8192 không sửa lỗi trong điều kiện đã đo, không tăng tiếp buffer/timeout.
- **Khoảng trống transcript đã đóng:** EP0 A SHA `63761bdcf0db5104e62e72b4027cb0977c40f29790d1d54cb539741dd794c5f2`, B SHA `bb68d798c9622406e46ed955ec6ccc5bfd67118651a599b900373d7ad3d34e97`; hash Android/host khớp. Đây là `ANDROID_APP_OBSERVED`, không usbmon hoặc packet trên dây. Linux raw strict/credit1 đều `FOUND_VERIFIED`, không chạy lại VM.
- **Firmware static:** 64/64 tests PASS, hai Windows A0 epochs riêng đều 132 transfers/8192 byte/hash đã pin. `8A→0237→067B`: selector `04` ghi latch IOA.7, selector `0A` ghi IOA.6; IN81 đọc software bit 20.7, B7 có side effect latch IOA.2. `IFCONFIG=CB` là Slave FIFO; PA7 còn phụ thuộc PORTACFG/wiring chưa biết. **Không chứng minh physical reset hoặc cho phép replay** `0400/0401/B7`.
- **Code/tests:** mirror debug mặc định OFF, không thêm request; native 8192 có exact limits/permit riêng, giữ default 4096, drain/quarantine và legacy/release. Fresh JVM 530/530, native GCC/MSVC ASan hai kích thước, bốn ABI syntax và tablet fake/permit 6/6 PASS; debug/release/lint PASS. Firmware A/B full-length 132/132 trong 3073/3155 ms; UI/budget dừng trước init, sau đó dùng phiên operational mới, không upload lại hoặc rearm history.
- **Hiện trạng cuối:** raw 16 file đã thu/hash trước khi ADB Wi-Fi chuyển offline; không reconnect/reset. Lần quan sát cuối tuner ở tablet, cả A/B đã drain và helper STOP/exit 1 đúng vì không TS. Đã gỡ đúng hai package app cách ly theo người dùng cho phép; nhãn debug mới phân biệt hai entry USB trong app chính, build/lint PASS nhưng APK nhãn cuối chưa cài/chạy. APK dùng cho A/B có SHA `8f51af4a748ee6f7253efb11da4d73e264dc3ef7fdac85fdd1c3cbf39c41c5c0`.

Báo cáo đầy đủ: `D:\TSDuck\reports\phase31-continuation-20261109-192232-71163e13\phase31-continuation-result.md`; raw bundle `D:\TSDuck\logs\phase31-continuation-20261109-192232-71163e13-verified`. Bước tiếp theo là review wiring/pinmux IOA.2/IOA.7 hoặc mapping TS drive đúng CXD6802 để chọn một A/B an toàn; candidate GPIO/drive vẫn `BLOCKED_FOR_SAFETY`. Không lặp cùng phép thử hoặc gọi khác biệt source/static là `ROOT_CAUSE_CONFIRMED`.

## 2026-11-09 — Phase 3.1: tiếp tục tự động, chọn Linux operational-only không nạp lại firmware

Người dùng lúc 00:41 +07 yêu cầu tự hoàn thiện gate và chạy đối chứng hữu hạn, chỉ thông báo khi cần rút/cắm hoặc chuyển thiết bị. Giữ no-snapshot, no-LNB/DC, không thay kernel/media core hoặc binding Windows, không replay vendor chưa giải mã. Quyền này không tự thay các gate phần mềm bằng consent chung.

Đã thông báo chuyển tuner, người dùng xác nhận sẵn sàng; VirtualBox capture đúng thiết bị và guest mới thấy `734c:5530`/High-Speed 480 Mb/s, config 1/interface 0/alt 0, Bulk IN82/81 maxpacket 512. Descriptor 50 byte SHA `22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5` khớp operational profile; không chứng minh hash RAM. Chọn **operational-only không A0/request_firmware**, tránh bootstrap/re-enumeration trong lượt này.

**Đã chạy hai lượt Linux thật, chưa có MPEG-TS:** cả hai admission sạch, nhận CXD6802 và khóa K36 (`status=31`), không A0/B7/LNB từ Linux. Lượt 01:19 có completion 82 byte/status 0 rồi 0 byte/status 0; guard strict tự latch -61 và cancel bảy URB. Trên epoch vật lý mới lúc 01:40, cho đúng một empty-success resubmit, sau đó chờ đủ cửa sổ nhận khoảng 10 giây vẫn không có byte TS; terminal -110 là deadline của diagnostic, không phải HCD error completion. Không có -75/-71 trong hai trace VM, nhưng queue 64 KiB/source C/warm handoff khác Android nên chưa có root-cause proof.

Windows ngay trước lượt thứ hai nhận 7.999.964 byte TS/14 services, TEI 0/sync 0; hai upload có 132 lệnh mỗi lần, đều đúng firmware SHA đã pin. Hai Linux run đều submit/reap 9, outstanding 0/drained 1/quarantine 0, modules đã unload. Tuner đã trả Windows: PnP OK/BDA, usbipd Shared; guest không còn tuner hoặc modules do phiên tạo. Hai ledger giữ consumed/BLOCKED, không rearm, không snapshot hoặc kernel/binding change.

Proof/build giữ 281+1522+21 cases, thêm 54 exact completion checks; source 25+30, MODPOST/122 CRC khớp. Runner có 19 ledger checks mỗi PS7/PS5.1, 28 plan checks và 5 receiver mock flows PASS; normalizer sửa grammar thật, 77/77 mỗi PS7/PS5.1, trace thứ hai normalize 1.300 records đều paired. Báo cáo tổng mới `D:\TSDuck\reports\phase31-linux-runtime-20261109-013202-4ecedf9c\runtime-result.md`. Câu hỏi tiếp theo có giới hạn là readback/mapping TS drive 96–97; chưa tự đổi 54→55 vì source chưa chỉ rõ mapping bit 0. Không lặp ZLP/buffer scan; **ROOT_CAUSE_UNRESOLVED_WITH_NARROWED_SCOPE**, chưa TS_SMOKE_PASS Android.

## 2026-11-08 → 2026-11-09 — Phase 3.1: Windows cold-start có TS, requested length đã đo trực tiếp; Linux diagnostic đang kiểm offline

**Chưa xác nhận nguyên nhân gốc và chưa đạt TS_SMOKE_PASS trên Android.** Branch thực `chore/modernize-android-build`, HEAD `69090a0bcf00b4c6342ec075d90a74964b25ecc9`; giữ nguyên local edits đầu phiên, không stage/commit/push hoặc sửa references. Không sinh phase mới.

- **Windows — RUNTIME-OBSERVED:** USBPcap bắt từ trước attachment đến firmware/init/K36/Bulk/cleanup. Cold2 có 21.136 frame, SHA `70fced47614ff8ec17c9e78f79fca16408c23e6bcadcfb6ad25a4c69cd7f9ded`; hai upload riêng, mỗi lần 132 A0 submits và 8192 byte đúng firmware SHA đã pin. BDA 2.0.0.2 thực dùng Intel EHCI High-Speed, không phải xHCI. File TS mới 30.897.800 byte, 14 services/47 PIDs, nhưng 3.054 TEI và lỗi CC: PASS nhận dữ liệu, không quality PASS.
- **Khoảng trống requested length đã đóng bằng ETW riêng:** USBPORT Dispatch của đúng `734c:5530`/IN82 có 1.440 yêu cầu `TransferBufferLength=0x2000`, tối đa 8 outstanding, thu hồi hết. ETL SHA `73911c746852ba2ce0ef822b7509915d05ae80b9595bd866720209cb222c8d5b`, EventsLost/BuffersLost 0. Không điền ngược 8192 vào pcap cũ/cold2; không coi đây là lý do đủ để đổi buffer Android.
- **Khác biệt init có căn cứ:** Windows ghi bank 00/reg96 và reg97 bằng `55`; Android/TBS source ghi `54`. Reg95 khớp công thức Android. Windows còn IN B7/IN81 và OUT8A `0400/0401` chưa giải mã. Đây là candidate, không phải nguyên nhân hoặc lệnh được phép replay. Chưa có raw Android init transcript đã căn đầy đủ để gọi runtime-to-runtime divergence.
- **Offline — PASS có phạm vi:** normalizer mới 67/67 trên PowerShell 7 và 5.1; chuyển ba native trace lịch sử và cold2, không suy requested từ actual hoặc trộn clock. JVM chạy mới 513/513, native GCC/MSVC AddressSanitizer PASS; debug/release/lint PASS. Không chạy instrumentation hoặc Android tuner mới; giữ nguyên các FAIL UI lịch sử.
- **Linux:** WSL bị chặn trước compile vì headers thiếu Makefile/Kbuild/generated headers/Module.symvers. Guest do người dùng cài đã kiểm Ubuntu 24.04.5/kernel 6.8.0-149-generic, headers đầy đủ, EHCI/2 GiB/2 vCPU; build dependency cài đúng phạm vi. Official TBS và Beta đã compile thật nhưng FAIL API vendor trên stock core. Community C nguyên trạng build đủ 5 module/MODPOST/226 CRC PASS, nhưng probe vệ tinh/LNB chưa an toàn nên chưa load hoặc giao USB. Biến thể diagnostic chỉ DVB-T2 đã build 3 `.ko`/MODPOST và 108 CRC khớp; 281 fake-plan cases và 25 source checks trên cả PS7/PS5.1 PASS. Firmware size/hash, opt-in và K36/8 MHz/PLP0 được khóa; queue vẫn 8×65536. **Chỉ BUILD_OFFLINE_PASS:** cold-state/handoff/replay, I²C fail-stop và nhận hữu hạn chưa đủ để load. Không tự cấp quyền runtime từ hai module params.
- **Quyết định mới:** live snapshot làm VM treo và thất bại `RPC_S_CALL_FAILED/0x800706be`. Người dùng tự phục hồi và yêu cầu **không tạo snapshot VirtualBox nữa**. Tiếp tục build riêng bằng package inventory/rollback theo phạm vi đã duyệt; không tự xóa snapshot/VDI, reset VM hoặc thay kernel/media stack.

Artifacts riêng ở `D:\TSDuck\reports\phase31-fullstack-20261108-172615-404b3ba8`: báo cáo tổng `phase31-fullstack-report.md`, evidence-index, hypothesis matrix, initialization-sequence, urb-first-failure, semantic diff và báo cáo normalizer/Linux. [Công cụ diagnostic](../../tools/linux/README.md) chỉ prepare/build; `linux-runtime-gate.md` liệt kê phần còn chặn. Bước tiếp theo là hoàn tất cold/handoff/fail-stop/bounded receive trước khi xin quyền load/USB riêng; trạng thái tổng vẫn **ROOT_CAUSE_UNRESOLVED_WITH_NARROWED_SCOPE**. Chi tiết và kết quả build tiếp theo được bổ sung tại [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md) và [WORKLOG.md](WORKLOG.md).

## 2026-11-08 — Cập nhật script đồng bộ repositories so với HEAD

Đối chiếu `tools/Sync-TBS5530-Repositories.ps1` tại commit `69090a0bcf00b4c6342ec075d90a74964b25ecc9` với bản hiện tại. Nhánh `chore/modernize-android-build`; giữ nguyên các thay đổi local đã có, chưa stage/commit/push. Đây là một bản ghi tổng hợp của thay đổi cuối cùng, không mô tả các giao diện thử nghiệm trong quá trình sửa.

- **Cách dùng:** gọi trực tiếp để xử lý toàn bộ repositories hoặc thêm `-CheckOnly` để kiểm tra. `-CheckOnly` vẫn có thể fetch repo khỏe để so sánh upstream, nhưng không clone, phục hồi, tải missing sparse blobs, merge hoặc đổi checkout. Workspace được suy từ vị trí script.
- **So với HEAD:** thêm reference CoreELEC theo phạm vi file chọn lọc; giữ sáu cone directories của TBS và main fetch-only. Clone kernel dùng `--no-checkout` trước khi kiểm manifest và materialize để tránh lỗi tên Windows ngoài sparse.
- **Tự phục hồi an toàn:** clone thiếu index và worktree trống được backup `.git`, kiểm hash/locks/metadata rồi tải blobs cần thiết và dựng checkout. Kiểm mọi selected file/hash, không chỉ patterns hoặc vài essential files. Không reset/clean, ghi đè local edits/staged deletions, xóa lock hoặc ép nhánh phân kỳ. Fast-forward dùng đúng SHA đã kiểm manifest.
- **Hiện trạng đã kiểm chỉ đọc:** TBS SHA `2ce787de9f6e81b4294692a45de15596ae2e1322` sạch, đủ 1.357 file/hash; CoreELEC SHA `072f8e99e0ac66d88d7278784e922e08e8253210` thiếu index, 74.109 staged deletions và 27 selected blobs chưa local. Không sửa references thật trong tác vụ viết code.
- **Validation đã chạy cho bản hiện tại:** 74/74 cases PASS trên PowerShell 7.6.9 và 74/74 PASS trên Windows PowerShell 5.1.19041.7802, exit 0, remote/fixtures local và không bypass policy. Parser, help, whitespace, links và cleanup đạt. Không chạy lại tests cho lần gộp ghi chú này.

**Bổ sung sau lần người dùng chạy script lúc 16:32:** TBS kiểm thành công, coax đã cập nhật fast-forward; CoreELEC có backup nhưng fetch OID dừng ở `bad revision`/`did not send all necessary objects`. Đã tái hiện bằng clone partial/shallow thật trên remote local và sửa helper tải OID bằng negotiation=noop cho riêng lệnh fetch. Không phải lỗi tham số người dùng hoặc lỗi NTFS lặp lại; kiểm trước đây chưa bao phủ đúng promisor-pack clone. Regression sau sửa đạt **76/76 PASS trên PowerShell 7 và 76/76 PASS trên Windows PowerShell 5.1**, exit 0, gồm clone promisor/shallow thật. Chi tiết nằm trong [TEST-MATRIX.md](TEST-MATRIX.md).

Chi tiết ở [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md) và [WORKLOG.md](WORKLOG.md). Agent chưa chạy phục hồi trên CoreELEC thật sau bản sửa này; kết quả USB/TS lịch sử phía dưới không thay đổi.

## Phase 3.1 — Đóng gói checkpoint tablet và chỉnh khoảng trắng docs theo yêu cầu — 2026-11-08

Người dùng yêu cầu commit toàn bộ thay đổi hiện tại và push lên `origin/chore/modernize-android-build`. Checkpoint đóng gói gồm native URB diagnostic, permission/preflight, bảo vệ host/attachment, lỗi typed attachment lookup đã kiểm và sáu hồ sơ đã sửa khoảng trắng. Source vẫn khớp fingerprint `127e7f550d7db79cd1ea25cd3f763b49c03a2706c407f05722be30e6c7e4f911`; không chạy lại build hoặc hardware chỉ để xuất bản Git. Các dòng HEAD cũ và câu “chưa commit/push” bên dưới là trạng thái lịch sử trước commit chứa mục này.

Kết quả phần cứng vẫn **BLOCKED_WITH_EVIDENCE tại EOVERFLOW/0 byte TS**, chưa TS_SMOKE_PASS hoặc ROOT_CAUSE_CONFIRMED. Không đưa APK, firmware, capture, grant/ledger hoặc log riêng vào Git. SHA commit và kết quả push chỉ được công nhận sau khi lệnh thực thi thành công và SHA remote được đối chiếu.

## Phase 3.1 — Đã đối chứng trên tablet: khóa K36 nhưng native USB vẫn EOVERFLOW, chưa có TS — 2026-11-08

**Đã vượt blocker kết nối tablet và thực hiện phép nhận thật.** Galaxy Tab S10 Ultra SM-X926B/API 36 nhận TBS5530, đo `USBDEVFS_GET_SPEED=3` (High-Speed), qua probe/init/AUTO K36 và gate khóa sóng. Hai fresh-owner run `ce9579b7-c40a-4aa8-a9c7-59c433402b53` và `fd30da8e-e8f3-42b4-9955-7ec0acbb5749` đều submit/reap đủ 8 URB × 4096 byte; cả 8 completion mỗi run có `urb.status=-75`, `actual_length=0`, trong khi syscall return/errno đều 0. Đã drain đủ 8/8, outstanding 0, không quarantine. **Chưa có Android TS, không TS_SMOKE_PASS hoặc ROOT_CAUSE_CONFIRMED.**

**Đối chứng nguồn và host:** người dùng mô tả dây USB chữ Y: Type-B vào tuner, một Type-A dữ liệu qua đầu chuyển Type-C vào tablet, Type-A còn lại vào sạc điện thoại; không cấp nguồn qua cổng DC 12 V trong sơ đồ này. Đây là điều kiện mới do người dùng cung cấp, chưa đo điện áp/dòng. Việc nhận USB tốt hơn không chứng minh nguồn đã chữa lỗi TS. OnePlus trước đó có 3 completion -75 và 5 completion -71; Samsung nay đều -75. Kết quả không ủng hộ kết luận lỗi chỉ có trên OnePlus; vẫn cần phân biệt firmware/setup/thiết bị/cáp hoặc phần chung của đường nhận Android.

**Sửa phần mềm có kiểm chứng:** `AndroidManager.resolve` có thể mất thiết bị giữa kiểm presence và kiểm permission nhưng ném IOException chung, làm mất thông tin bàn giao sau final release. Đã tái hiện test BEFORE 1 FAIL/25, sửa sang `AttachmentChangedException` hiện có; AFTER drivers 420 + app 68 JVM PASS, guard 28 + library 43 instrumentation PASS trên tablet. Các guard, điều kiện đủ 132 native/full-length và yêu cầu permission/owner mới giữ nguyên. Terminal upload đầu vẫn UNCERTAIN dù native đủ 132; sau người dùng phục hồi vật lý, upload mới sạch 132/132 trong 2891 ms, rồi dừng do attach-confirm quá thời hạn UI. Hai phép nhận tiếp theo dùng owner mới, không upload lại.

**Giới hạn trace:** Perfetto v51.2 bật/tắt phiên bình thường nhưng FtraceStats của cả hai trace ghi ba event xHCI là unknown, không có ftrace bundle; không thu được controller completion code. Sysfs speed/config/alt và tracefs event format bị từ chối. Không suy file trace hoặc CLI exit 0 là capture USB PASS. Hộp thoại USB đã được agent kiểm ảnh và bấm OK, không chọn Luôn cho phép; helper chưa tự xử lý headless được secure UI này.

HEAD vẫn `1202c8abd49a48c3099e718bead13a21a9176660` trên `chore/modernize-android-build`, chưa commit/push. Source fingerprint mới `127e7f550d7db79cd1ea25cd3f763b49c03a2706c407f05722be30e6c7e4f911`, APK hardware SHA `1c51ddf79bb1f3c08dc5b62a219d142e06252d851e0d963c68471dbfd36523a3`. Build debug/release, lint và static PASS có phạm vi; hai UI test FAIL lịch sử chưa được tuyên bố sửa. **Bước phân biệt tiếp theo:** cần đối chiếu firmware/setup Windows từ lúc khởi động tuner, vì trace Windows cũ thiếu giai đoạn này; không tự đổi register, quét buffer hoặc chuyển tuner/ownership. Báo cáo chi tiết `D:\TSDuck\reports\phase31-tablet-ab-20261108-ce9579b7.md` và log gốc trong `D:\TSDuck\logs\phase31-tablet-live-20261107-230448-7cb5c880`.

## Phase 3.1 — Tablet đã nhận TBS5530 tạm thời, nhưng kết nối USB chưa ổn định — 2026-11-07, 22:34 +07

Người dùng báo đèn POWER đã sáng và xác nhận dây dữ liệu nối vào tablet. Kiểm mới qua ADB trên SM-X926B/API 36 thấy lịch sử USB nhận đúng VID:PID 734c:5530 lúc 22:24:38.839 +07, rồi tháo thiết bị khỏi hệ thống lúc 22:24:55.207 +07, cách nhau 16,368 giây. Đây là bằng chứng tablet đã từng nhận diện tuner, khác lần kiểm trước chưa thấy thiết bị. Tuy nhiên snapshot hiện tại và lần kiểm lại sau 5 giây đều không có tuner trong danh sách thiết bị đang kết nối; sysfs chỉ còn root hubs, cổng trở lại vai trò host. Chưa xác định nguyên nhân ngắt kết nối; đèn sáng không chứng minh nguồn hoặc đường dữ liệu ổn định.

Chưa nạp firmware, tune hoặc nhận TS trên tablet trong lượt này. Cần kết nối USB được giữ ổn định trước khi xác minh descriptor, permission và thử DVB-T2. Không suy việc xuất hiện trong lịch sử là thiết bị hiện vẫn kết nối hoặc đã có tín hiệu truyền hình. Bằng chứng riêng tại `D:\TSDuck\logs\tablet-power-on-recheck-20261107-221543-93025e04`.

## Phase 3.1 — Kiểm lại tablet sau thay adapter: vẫn chưa nhận USB — 2026-11-07, 21:50 +07

Đã kiểm mới Galaxy Tab S10 Ultra SM-X926B/API 36 qua ADB, trước và sau khi người dùng cắm lại riêng cáp USB. `dumpsys usb` không liệt kê TBS5530, `num_connects=0`; sysfs chỉ có hai root hub. Tablet đang ở `data_role=host`, `usb_data_status=enabled`; các cờ chặn USB do quản trị/khóa màn hình đều false. Người dùng xác nhận đèn POWER của tuner không sáng với adapter mới; thông số và cực tính adapter mới chưa được xác minh. Không suy từ đó rằng tuner hỏng hoặc nguồn chắc chắn là nguyên nhân.

Đây là kiểm tra kết nối USB, chưa thể đo tín hiệu DVB-T2 hoặc MPEG-TS. Không cài APK, cấp grant phiên, nạp firmware, gửi vendor/I²C hoặc mở luồng URB trong lượt này. Giữ Phase 3.1 và blocker trước bước nhận diện USB. Phép đối chứng tiếp theo có giá trị là kiểm tuner trên Windows với kết nối đã biết hoạt động, hoặc kiểm đường nguồn/cáp bằng thiết bị và bộ cấp nguồn đúng chuẩn; không thử adapter ngẫu nhiên. Bằng chứng mới nằm dưới `D:\TSDuck\logs\tablet-adapter-recheck-20261107-215018-04c39602`.

## Phase 3.1 — CONTINUATION / EOVERFLOW DIAGNOSIS: đã đo native URB; A/B tablet bị chặn nguồn — 2026-11-07

**Kết luận mới: lỗi đã được quan sát trực tiếp ở completion USB, chưa có Android TS và chưa xác định nguyên nhân vật lý.** Run `d902837a-99e0-469d-9d3a-4a916a905cb3` trên OnePlus GM1900/API 30 submit 8 × 4096 thành công, reap 8/8 thành công với syscall errno 0; ba URB status -75/EOVERFLOW và năm URB status -71/EPROTO, tất cả actual_length = 0. Peak outstanding 8, cuối 0, không resubmit, cleanup DRAIN_CONFIRMED/quarantine false. Firmware trước đó 132/132 sạch, chip/init/AUTO K36/lock đã qua trước stream-select. Đây là `URB_DIAGNOSTIC_VALIDATED`, không phải `ROOT_CAUSE_CONFIRMED` hoặc `TS_SMOKE_PASS`.

**Đối chứng tablet:** Người dùng chuyển cùng dây sang Galaxy Tab S10 Ultra SM-X926B/API 36/kernel 6.1.145. Tablet báo “Thiết bị USB công suất cao đã kết nối. Không thể truy cập thiết bị này. Thiết bị đã kết nối cần nguồn điện bên ngoài.” USB host role có, nhưng chưa enumerate 734c:5530; chưa gửi firmware/control/URB trên tablet. Người dùng thử nguồn khác 12 V DC/1 A, báo tuner không lên đèn; cực tính/dòng yêu cầu chưa xác minh và đang tìm nguồn phù hợp. Dừng phần cứng, không khuyến nghị thử adapter chỉ dựa vào điện áp/đầu tròn. Chưa có A/B cùng điều kiện nên không kết luận lỗi riêng OnePlus.

**Phần mềm:** thêm native diagnostic debug-only, exact USB attach filter, permission state/token/generation và preflight 60 giây không I/O trước grant 5 phút; heartbeat gộp giảm số round-trip; tách `ts_smoke_pass` khỏi cấu trúc PAT/PMT. Đã sửa budget preflight trước consume history; giữ mọi record đã consume/uncertain. Lifecycle attach-prompt và token relaunch được sửa sau hardware; allowlist chỉ GM1900/API 30 và SM-X926B/API 36, history tablet riêng. Final source fingerprint `9adfdc4fe371f77c0d2a633eee8d99e9c851632c4e51fbd0f82a284fab3c2477`; final debug SHA `ea6a48ac85e653faf2c6e4112784d32e8bf31e4f2a931cbc41384b1fc7b38ae0` không phải APK đã đo URB (`c2a9cb7c6e61bb9060319137ff4e0aee03f857577a6dad4f60aa12e156b4c9f5`).

**Kiểm chứng cuối:** 511 JVM PASS, native C fault-injection/canary và MSVC AddressSanitizer PASS; debug/release build và lint PASS (0 errors, debug 18/release 10 warnings). OnePlus trước patch lifecycle cuối: 119 app + 43 library instrumentation PASS; tablet final: 118/120 app PASS, 2 UI test FAIL do focus NotificationShade/challenge không tạo, targeted retest vẫn FAIL; library 43 PASS. Không gọi tablet full instrumentation PASS. API 21/16 KiB runtime và hardware trên final APK NOT RUN. Các package test phiên tạo đã gỡ; OnePlus owner/URB đã drain trước chuyển host, helper/Perfetto đã kết thúc.

**Git / bước tiếp:** HEAD `1202c8abd49a48c3099e718bead13a21a9176660`, nhánh `chore/modernize-android-build`, thay đổi chưa commit/push. Sau khi có nguồn đúng chuẩn TBS và tuner enumerate trên tablet, kiểm lại nguồn/USB/ADB, cùng APK/profile rồi chạy một native A/B hữu hạn; không tự nạp lại firmware trên attachment bất định. Báo cáo đầy đủ ngoài Git `D:\TSDuck\reports\phase31-continuation-closeout-20261107.md`; xem ADR-029 và các entry mới trong [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md).

## Phase 3.1 — Đóng gói checkpoint để commit/push theo yêu cầu — 2026-11-07

Người dùng yêu cầu commit toàn bộ thay đổi đã kiểm và push lên `origin/chore/modernize-android-build`. Commit chứa checkpoint này là mốc đóng gói source; SHA `1ad3b251` và các câu “chưa commit/push” ở dưới là trạng thái lịch sử trước đóng gói, không phải HEAD sau commit. Không thay đổi code hoặc chạy thêm USB hardware trong bước xuất bản Git.

Kết quả vẫn **`BLOCKED_WITH_EVIDENCE` tại EOVERFLOW/0 byte TS Android**, không chuyển phase hoặc gọi TS_SMOKE_PASS. Source đã chốt có fingerprint `3fe396af975baea76b00bc21a60c691655a080df549b11418100713f19ebd189`; các kiểm chứng 430 JVM, 97 app + 43 library instrumentation, build/lint/static và giới hạn hardware sau patch giữ nguyên. Không đưa firmware, APK, TS, trace hoặc private logs vào commit. Kết quả push và SHA remote được xác minh sau lệnh, không suy thành công từ yêu cầu này.

## Phase 3.1 — Auto workflow tới Bulk đã chạy; bật OTG vẫn EOVERFLOW, TS chưa đạt — 2026-11-07

**Kết luận hiện tại: `BLOCKED_WITH_EVIDENCE`, chưa `TS_SMOKE_PASS`, không mở phase mới.** Sau handoff/heartbeat/epoch fixes đã kiểm, pipeline tự chạy đúng firmware khi FX2_DEFAULT → permission operational mới → fresh owner → probe/init/AUTO K36/status → stream-select → Bulk. Lượt firmware phục hồi có 132/132 native full-length, image 8192/payload 8196, transition sạch 4706 ms; không hỏi Approve từng ordinal hoặc plan. Terminal UNCERTAIN đầu giữ nguyên lịch sử, không rearm ledger.

**Đã đo lại Android:** GET_SPEED trên fd hiện hữu trả enum 3 (High-Speed) cả default/operational, descriptor IN 82/IN 81 maxpacket 512. Baseline native 4096/500 ms vẫn `-1 / errno75`, 0 byte; một queued variant 8×4096 trả 0, errno không có, 0 byte, đã drain/release hết. Sau người dùng tự bật “Lưu trữ OTG”, UI switch được đọc `CHECKED`; một fresh-owner synchronous retest không upload firmware vẫn errno 75/0 byte. Không kết luận bật OTG đã chữa, thiếu RF, sai PLP hoặc timeout. Không có OTG/cáp/Android host khác để đối chứng trong môi trường hiện tại.

**Phần mềm đã được kiểm:** APK chạy hardware SHA-256 `9ecb806b6f6a04ea1ec121a6330a012b4a0ebfe8b2928d4fe67186c999afe11b`, 429 JVM, 97 app + 43 library instrumentation PASS. Sau quan sát queue completion 0, thêm fail-stop không resubmit slot đó; final **430 JVM + 97 app + 43 library PASS**, debug/release/lint/static bốn ABI/16 KiB/major 52/isolation PASS có phạm vi. Final debug SHA `6d04f7dee03a44cd34a92931a08d689020c9d6ed40617b3d68c58483afd21311` đã cài đè và kiểm SHA, chưa chạy lại USB hardware trên patch cuối. API 21/16 KiB runtime mới và parser trên broadcast Android NOT RUN. Sáu legacy preferences giữ nguyên; ba firmware-history keys là khóa chống tái sử dụng, không phải ba uploads. Test packages phiên tạo đã dọn, không giữ helper hoặc owner.

**Bước thực tế tiếp theo:** cần đối chứng cùng APK/profile trên host Android hoặc bộ OTG khác đã biết tốt, hoặc trace USB/kernel phù hợp theo một phạm vi được duyệt riêng. Không tự đổi register/driver/mode/reset hoặc lặp lại cùng điều kiện lỗi. Báo cáo tổng hợp và terminal JSON dưới `D:\TSDuck\reports`, run OTG cuối `phase31-android-user-otg-sync-20261107-163301-6dedd387`; chi tiết tại [TEST-MATRIX.md](TEST-MATRIX.md), [RESEARCH.md](RESEARCH.md), [WORKLOG.md](WORKLOG.md) và ADR-028. Git vẫn HEAD `1ad3b251f19779450a2c5d9cffc9f37f9c9a5470`, thay đổi chưa commit/push.

## Phase 3.1 — Firmware tự động đã gửi đủ; chưa vượt handoff/attachment guard để thu TS — 2026-11-07

Guard phần mềm đã qua 421 JVM và 94 app + 43 library instrumentation trước lần firmware thật. Phiên `AUTO_VERIFIED` đầu gửi đủ 132/132 native A0 full-length, 8192 byte image/8196 byte payload, rồi terminal `UNCERTAIN/JAVA_IO_EXCEPTION` sau CPU release; cleanup xác nhận, không I²C/tune/TS. Đã dừng mọi write attachment đó, không retry. Người dùng phục hồi vật lý một lần và cấp OS permission mới qua UI, không root hoặc shell grant.

Đã tái hiện software mất permission ngay sau native 132 trước broadcast: BEFORE FAIL, typed handoff patch và các guard hồi quy PASS; không sửa terminal UNCERTAIN cũ thành PASS. Sau rebuild, 95 app + 43 library instrumentation PASS. Attachment phục hồi dừng trước native, ledger chỉ có SESSION_GRANTED và lịch sử upload vẫn giữ 1 record; đang kiểm trường hợp Android tái dùng path/id khiến key bảo vệ va chạm. Không xóa/rearm ledger và chưa gửi thêm firmware từ refusal native = 0.

Windows đã có đối chứng TS/USBPcap như checkpoint dưới; Android hiện vẫn **0 byte TS mới, root cause EOVERFLOW chưa chứng minh, Phase 3.1 chưa hoàn tất**. Bước tiếp là giải quyết bằng chứng generation/ledger theo đúng phục hồi vật lý đã xác nhận, kiểm fake rồi mới chạy một attempt mới; không tự vượt gate để thử queue. Giữ HEAD `1ad3b251f19779450a2c5d9cffc9f37f9c9a5470`, không commit/push; xem [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md) và ADR-028.

## Phase 3.1 — Đối chứng Windows đã có; đang kiểm guard trước phiên Android tự động — 2026-11-07

**Checkpoint tiếp nối trên Git thực:** nhánh `chore/modernize-android-build`, HEAD `1ad3b251f19779450a2c5d9cffc9f37f9c9a5470`; không reset/checkout/fetch/commit/push. Đã có thay đổi tài liệu TSDuck của người dùng trước phiên, được giữ nguyên. Các đoạn dưới ghi HEAD hoặc Windows NOT RUN là lịch sử, không phải trạng thái Git/đối chứng mới.

**Windows đã nhận TS thật:** đọc toàn bộ hai mẫu cũ, xác nhận 14 dịch vụ/46 PID không TEI/TSID 6; mẫu dài có 5.101/223.225 packet TEI (2,285138%). Hai capture K36 mới có TEI/sync 0 nhưng vẫn 41 continuity bất thường, lần lượt 1 và 4 invalid PES. USBPcap hữu hạn sau UAC đã xác nhận High-Speed, peak 8 IRP và actual 8192 byte/completion; requested length không quan sát được, không thấy 0x8A trong cửa sổ trace. Không suy ChangeMode là nguyên nhân hoặc 132–134 Mb/s từ PCR là tốc độ RF thật. Chi tiết và đường dẫn report/hash ở [RESEARCH.md](RESEARCH.md).

**Phần mềm:** đã thêm policy phiên debug hữu hạn, golden dry-run, namespace/attempt ledger mới, helper heartbeat/STOP và một ứng viên nhận queue 8 × 4096 byte chỉ debug/API 26+. Parser nay kiểm PAT/PMT CRC và quan hệ dịch vụ, không coi ba packet sync là TS PASS. Synchronous baseline và manual cũ giữ nguyên. Full JVM 413 methods, debug/release và lint đã PASS ở checkpoint đầu; library instrumentation 43 PASS. App instrumentation lượt đầu có 6 FAIL: 5 do namespace harness không đúng hợp đồng test cũ, 1 do session-backed approval cần điều tra. Chưa bật hardware trên mã chưa qua guard.

**Phần cứng hiện tại:** người dùng đã chuyển tuner từ Windows sang OnePlus và xác nhận OTG/nguồn/coax RF IN sẵn sàng. ADB được pin lại trước grant vì TCP port thay đổi; model GM1900/API 30/cùng boot đã đối chiếu, không fallback giữa giao dịch. Chưa có USB write Android mới trong checkpoint này. Không tuyên bố sửa EOVERFLOW, có Android MPEG-TS hoặc hoàn tất Phase 3.1. Bước tiếp: sửa và kiểm app guard trong namespace cách ly rồi mới chạy đúng synchronous baseline một lần và một ứng viên queue theo phạm vi đã duyệt; dừng nếu firmware/native bất định. Xem ADR-028 và checkpoint tiếp theo.

## Phase 3.1 — AUTO đã khóa K36; TS bị chặn bởi lỗi USB EOVERFLOW — 2026-11-07

**Kết quả hiện tại:** `DVBT2_LOCK_CONFIRMED`, nhưng **chưa có `TS_SMOKE_PASS`**; toàn phase ở trạng thái `PHASE_3_1_BLOCKED_WITH_EVIDENCE` tại đường nhận USB. Năm workflow được duyệt, mỗi workflow mở owner mới và chạy lại probe/init/AUTO/status, đều đọc ID `0x197` rồi đạt `syncState=6`, `tsLock=true`, `unlockDetected=false` ngay mẫu đầu trên K36 / `594000000 Hz` / `8000000 Hz`. AUTO không ghi PLP ID vào AF, dùng AD=0; đây là bằng chứng runtime cho đường AUTO đã thử, không phải đo được selected PLP ID hoặc chứng minh hỗ trợ mọi Multi/Common PLP.

Bốn TS scopes riêng đã gửi stream-select full-length rồi dừng ở Bulk đầu, đều 0 byte/packet: Java 16384 byte, Java 4096 byte, Java 4096 byte sau CLEAR_HALT và native 4096 byte sau CLEAR_HALT. Lượt cuối trên APK v4 đo được `errno=75 (EOVERFLOW)`, không còn chỉ có mã Java -1. Linux định nghĩa lỗi này là endpoint trả dữ liệu vượt max packet hoặc phần buffer còn lại; chưa phân biệt được thiết bị/firmware, host controller hoặc đường liên kết USB. Không gọi đây là timeout, thiếu sóng hoặc sai PLP. Giảm chunk và chuẩn bị endpoint **chưa khắc phục lỗi thực**. Chi tiết phân biệt nguyên nhân ở [RESEARCH.md](RESEARCH.md).

**Bản mã cuối đã kiểm:** HEAD `9621a8028d2cd3e5d98652cde69ba80d27cd7ee2`, nhánh `chore/modernize-android-build`, thay đổi chưa stage/commit/push; source fingerprint `988919f6fe1c8cc070c4488c86caf50dd77cf652397221f96eab6502fb674270`. JVM 361 methods PASS; instrumentation cách ly 67 app + 43 library PASS; debug/release/lint/native bốn ABI và kiểm tĩnh APK PASS có phạm vi. APK debug v4 SHA-256 `9be0e4687de96a2ad2a690b5ca457ec875b916d2aa8128a53a6a18566950ef93` đã chạy lượt errno thật. Không nâng toolchain; API 21/16 KiB runtime mới NOT RUN. GitNexus index chưa xác minh, không dùng graph để chứng nhận kết quả.

**Quyền và dữ liệu:** Một firmware upload mới duy nhất từ FX2_DEFAULT đạt 132/132 A0 full-length; terminal cũ vẫn UNCERTAIN, observation operational về sau được ghi riêng. Năm workflow frontend có permit/hash mới; tổng checked control 776 OUT/160 IN, cộng firmware là 908 OUT/160 IN, không tính descriptor reads hoặc host-only failures. Bốn stream-select, hai CLEAR_HALT được duyệt riêng và bốn Bulk calls, không retry. Không lưu raw TS, không cấp LNB/antenna DC, không reset/power-cycle hoặc đổi USB ownership. Các owner/UI/helper bounded đã kết thúc; consumed stores và firmware ledger được giữ nguyên, không giữ phiên để chờ.

**Một hành động thực tế để tháo gỡ:** Kiểm thử độc lập chính TBS5530 bằng driver/ứng dụng Windows của hãng trên cùng anten K36 594 MHz/8 MHz, ghi nhận có nhận TS hay không. Đây là bước chẩn đoán được đề nghị, **chưa được thực hiện hoặc cấp quyền chuyển thiết bị**; không mở phase con, không tự đổi driver/ownership. Kết quả này giúp phân biệt lỗi riêng host/port Android với đường thiết bị/firmware. Những checkpoint dưới đây là lịch sử; xem [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md), [ENVIRONMENT.md](ENVIRONMENT.md) và ADR-027 trong [DECISIONS.md](DECISIONS.md).

## Phase 3.1 — Lock thật đã đạt; TS đầu lỗi USB, đang sửa và kiểm tiếp — 2026-11-06

AUTO K36 / 594 MHz / 8 MHz đã đạt lock trên hai owner mới được duyệt: ID 0x197, init ASCOT3I, status syncState=6/tsLock=true/unlockDetected=false. Không cần biết trước PLP số để đạt lock; selected PLP và Multi/Common đầy đủ vẫn chưa xác minh.

Sau khi user khôi phục TCP và duyệt workflow mới, TS scope riêng đã gửi một 0x8A full-length và đọc một lần Bulk IN 0x82; result=-1, 0 byte, NO_DATA_OR_USB_FAILURE. Chưa có packet thật hoặc TS_SMOKE_PASS; không suy NO_SIGNAL hay sai PLP từ lỗi USB này. Owner/UI đã đóng sau smoke, helper bounded kết thúc; không tự retry hoặc thêm corrective writes.

Đang kiểm sửa software trong cùng Phase 3.1: chunk 4096 byte theo buffer official thay 16384, transcript TS v2, telemetry kết quả/timeout/read wall time và sửa stale plan hash trong report TS. 4096 là ứng viên có nguồn, chưa chứng minh là root cause hoặc đã sửa transport thật. Lượt hardware khác phải có approval/hash mới sau regression; không upload lại operational. Các checkpoint dưới đây giữ nguyên lịch sử, không phải trạng thái cuối. Chi tiết mới tại [RESEARCH.md](RESEARCH.md).

## Phase 3.1 — Đã khóa DVB-T2 K36 bằng AUTO; ADB mất trước TS — 2026-11-06

**Bằng chứng mới:** `DVBT2_LOCK_CONFIRMED` trên TBS5530 thật, OnePlus GM1900/API 30, APK debug SHA `8941bf684d14a07180150cdbe217b4522d0b61e6d4353168b12b53052cc2e69a`. Người dùng đã duyệt riêng firmware, thử nghiệm AUTO và xác nhận coax thụ động đúng RF IN. Probe mới đọc ID `0x197`, init đạt, một AUTO tune K36 `594000000 Hz` / `8000000 Hz` hoàn tất. Status dừng ngay mẫu đầu: `syncState=6`, `tsLock=true`, `unlockDetected=false`. PLP được chọn chưa đo; lock không chứng minh Common/Multi PLP đầy đủ hoặc TS hợp lệ.

Firmware mới từ FX2_DEFAULT có 132/132 A0 full-length, image 8192 byte; terminal auto-observation giữ `UNCERTAIN/JAVA_IO_EXCEPTION/NOT_OBSERVED`. Observation độc lập sau permission mới đọc operational 50 byte trong 14 ms, không vendor. Không upload lại operational. Control đã kiểm full-length: probe 3 OUT/2 IN, init 65/14, AUTO tune 84/15, status 2/1; tổng 154 OUT/32 IN, không phải số đo native-boundary latency.

**Blocker hiện tại:** TCP ADB chuyển OFFLINE ngay trước chuẩn bị TS; không còn target online. Chưa có TS approval consumed, chưa `0x8A`, chưa Bulk hoặc TS thật; **không gọi `TS_SMOKE_PASS`**. Không tự reconnect, dùng alias, retune hoặc suy NO_SIGNAL. Các helper bounded đã kết thúc; cần người dùng đóng Activity operational trên điện thoại để không giữ owner trong lúc khôi phục ADB. Sau pause phải mở owner mới và có approval mới cho các chương trình đã consumed, không tái dùng init/lock trong bộ nhớ.

Host permit lần đầu dừng trước native vì tên tạm `.json.new` xung đột Android AtomicFile; giữ host consumed, không có PROBE thật từ lần đó. Đã sửa helper thành `.tmp`, tái hiện bằng instrumentation và kiểm channel cách ly; user duyệt PROBE mới rồi đường probe/init/tune/status thật mới đạt lock. Chi tiết và giới hạn đang được bổ sung trong cùng Phase 3.1, không mở phase con. Git HEAD vẫn `9621a8028d2cd3e5d98652cde69ba80d27cd7ee2`, chưa stage/commit/push.

## Phase 3.1 — Phần mềm PLP đã kiểm; chờ approval phần cứng mới — 2026-11-06

**Trạng thái:** `SOFTWARE_READY_AWAITING_EXPLICIT_HARDWARE_APPROVAL`, không phải hoàn tất lock/TS. HEAD thực `9621a8028d2cd3e5d98652cde69ba80d27cd7ee2`, nhánh `chore/modernize-android-build`, origin đúng fork; changes chưa stage/commit/push. Không có commit mới sau baseline lúc bắt đầu; không reset/rebase/checkout/fetch hoặc sửa reference trees.

Đã triển khai Selection MANUAL/AUTO/MONITOR_RESOLVED, tune transcript v2 và AUTO thử nghiệm tách quyền: không ghi AF, bank 0x23 / AD=0, chỉ đề nghị ID 0x197 / K36 / 594 MHz / 8 MHz. Runtime mặc định vẫn từ chối AUTO; đường debug cần consent thử nghiệm riêng theo exact plan/owner. Monitor L1 đúng CXD6802 chưa có nguồn nên không tạo hardware probes hoặc tự chọn Common PLP. Ba chip Linux đối chiếu cung cấp CROSS-CHIP-ANALOGY, **chưa chứng minh AUTO trên tuner này**. QCVN 63:2020 thay bản 2012, được dùng cho yêu cầu máy thu/fixtures, không suy PLP SDTV là 0/1/2 hoặc chứng nhận hợp quy.

**Kiểm mới:** JVM 355 methods PASS; instrumentation fake-only cách ly `.phase31offline` PASS 61 app + 43 library trên GM1900/API 30; debug/release/lint PASS (0 errors/10 warnings cũ), 148 TBS main classes major 52, minSdk 21/target 37, zipalign và kiểm không firmware/TS/capture/test harness trong APK production PASS. Đã sửa hai lỗi deadline TS có test FAIL→PASS, chặn bảo thủ syncState=7 cấp stream, bổ sung sample clock/epoch và lifecycle pause/drain/reconnect/RF reset. Không có benchmark tăng tốc. Fingerprint nguồn đã kiểm: `078edc85048dfc147d268a8b802924dbe296856abe62890ad86abbba2e38d637`.

**Phần cứng mới:** 0 firmware upload, 0 vendor/I²C, 0 tune/poll/stream/Bulk. Kết quả ID 0x197/init Phase 3.0 chỉ là lịch sử; owner/permission/RF/approval cũ không tái dùng. PLP on-air và RF hiện tại chưa đo. Chưa có `DVBT2_LOCK_CONFIRMED` hoặc `TS_SMOKE_PASS` thật. Nếu thiết bị còn default phải xin firmware approval riêng, nếu operational không nạp lại. Trước live phải kiểm provisioning/heartbeat host khớp protocol 2; đường host live này NOT RUN, không lấy helper firmware Phase 2.9 thay thế.

**Bước kế tiếp duy nhất:** checkpoint xin phép workflow bounded được mô tả tại [RESEARCH.md](RESEARCH.md), hoặc chỉ preflight theo lựa chọn người dùng; không gửi USB ghi từ việc chuẩn bị code. Không giữ owner/app/helper phần cứng khi chờ trả lời. Nguồn/QCVN/bảng quyết định: [RESEARCH.md](RESEARCH.md); lựa chọn và rollback: ADR-025 trong [DECISIONS.md](DECISIONS.md); kết quả: [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md), [ENVIRONMENT.md](ENVIRONMENT.md).

## Checkpoint Git Phase 3.0 — 2026-11-06 18:22 +07

- Người dùng yêu cầu commit toàn bộ changes Phase 3.0 và push lên origin của nhánh hiện tại. Preflight xác nhận `chore/modernize-android-build`, HEAD và live origin cùng `722c4916a83074e8cc5b5c6aeeaee9d2f1190785`, stage trống, một worktree và đúng 35 file nguồn/test/resource/hồ sơ đã kiểm.
- Phạm vi không chứa firmware, APK, ảnh Công báo, raw USB/TS/capture, audit, approval GUID hoặc ledger thật. Tệp whitelist init nằm ngoài Git và không được chép vào repository. Các câu “chưa stage/commit/push” bên dưới là checkpoint lịch sử trước yêu cầu này.
- Message chuẩn bị: `feat: add guarded TBS5530 frontend bring-up`. Chỉ xác nhận commit/push sau kết quả Git; không force push, tự merge/rebase hoặc đổi nhánh. Không chạy lại build/tests hoặc phần cứng trong tác vụ Git. Bằng chứng gần nhất vẫn frontend initialized, chưa tune/lock/TS do PLP chưa xác minh.

## Bổ sung 17:57 +07 — K36/594 MHz/8 MHz đã xác nhận từ bảng Công báo

Bốn ảnh người dùng gửi cho thấy Điều 3 khoản 1: K36 có giới hạn 590–598 MHz, trung tâm 594 MHz; độ rộng kênh là 8 MHz. Điều 3 khoản 2 điểm d quy hoạch K36/K37 cho Nam Trung Bộ và Tây Nguyên. Vì vậy giữ ứng viên `594000000 Hz` / `8000000 Hz` có bằng chứng tài liệu, không chỉ suy từ số kênh. Các ảnh không cung cấp PLP hoặc chứng minh trạm đang phát; gate cao nhất vẫn frontend initialized, chưa tune/lock/TS. Không có hardware transaction mới sau khi nhận ảnh. Chi tiết tại [RESEARCH.md](RESEARCH.md).

## Phase 3.0 — Frontend đã init trên phần cứng; chưa tune vì PLP chưa xác minh — 2026-11-06

**Gate cao nhất có bằng chứng mới:** `FRONTEND_INITIALIZED` / `READY_FOR_TUNE`. Lần cắm mới đúng FX2_DEFAULT; sau một reservation dừng trước native, transaction độc lập tiếp theo đã hoàn tất 132/132 A0, image 8192 byte, payload 8196 byte, hai release đều 1/1. Auto-observation vẫn UNCERTAIN/UPLOAD_COMPLETE_OPERATIONAL_UNVERIFIED do attachment và quyền mới; terminal cũ giữ nguyên. Observation riêng được duyệt sau đó đọc operational raw 50 byte, hash 22e2…add5, 0 claim/vendor. Không nạp lại operational.

**Bridge/frontend thật:** Owner operational mới kiểm raw và Android metadata rồi claim(false). Probe slave `0x66`, bank `00`, registers `FB/FD` cho ID `0x197` với 3 OUT/2 IN. Init riêng theo whitelist SHA 6179…82da đạt 65 OUT/14 IN full-length, kiểm tuner E-family đạt; 158 audit events, INTENT đầu → RETURN cuối là 1418 ms, gồm audit và điều phối. Tổng control bridge/init là 68 OUT/16 IN; chưa có tune, đọc lock, stream-select `0x8A` hoặc Bulk IN. Không suy RF lock từ init.

**RF và ứng viên tune:** Người dùng đã xác nhận coax thụ động an toàn cắm đúng RF IN, không LNB IN, đường anten từng thu tốt. Ứng viên ưu tiên là SDTV trạm Vũng Chua/K36, tần số 594000000 Hz, băng thông 8000000 Hz. Nguồn SDTV công bố năm 2019 và bản đồ hiện có xác nhận K36, không chứng minh trạm đang phát tại lúc thử. PLP ID, tức mã luồng cần chọn trong multiplex, chưa xác minh. Source official/C/Go đã đọc chỉ có chọn PLP thủ công, chưa có auto/no-filter được kiểm cho path này; `NO_STREAM_ID_FILTER` không được biến thành PLP 255 hoặc PLP 0. **TUNE_NOT_RUN_ENVIRONMENT_BLOCKER: thiếu PLP hoặc cơ chế chọn PLP được xác minh**, không phải thiếu anten, NO_SIGNAL hoặc lỗi tuner.

**Phần mềm:** Đã nối debug UI/approval/control gate vào lifecycle hiện hữu, port chọn lọc CXD2878/ASCOT3I init/tune/status, TS smoke có giới hạn và parser 188 byte. Full JVM 343 methods PASS; instrumentation cách ly 64 methods PASS; debug/release/lint PASS với 0 errors/10 warnings; Java 8 major 52, minSdk 21, target 37. DEX debug isolation, không bundle firmware và zipalign đều đạt. APK đã cập nhật giữ data, SHA 5965…7a36. Freia, auto PLP và metrics chưa port/kiểm đủ nên từ chối hoặc NOT_AVAILABLE. Không làm app TV/EPG/A/V/production registry hoặc DVB-S.

**Bảo toàn và bước kế tiếp:** HEAD vẫn `722c4916a83074e8cc5b5c6aeeaee9d2f1190785`, nhánh `chore/modernize-android-build`; chưa stage/commit/push. Ledger firmware có 545 records/182748 byte; hash prefix 92018 và 93760 byte khớp lịch sử trước hai attempts mới. C/D dùng consumed/audit riêng; không reuse approval hoặc corrective writes. Đã đóng owner để không giữ phiên chờ thông số; lần thực nghiệm tiếp phải kiểm attachment/permission/owner lại, không dùng init/epoch cũ như quyền sống. Bước kế tiếp trong cùng Phase 3.0: xác minh PLP của multiplex K36 từ nhà phát sóng hoặc thiết bị đo, hoặc tìm tài liệu Sony đúng chip cho auto-PLP và kiểm offline trước khi xin một tune transaction riêng. Chi tiết ở [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md), ADR-024.

## Phase 3.0 — Đang triển khai phần mềm, chưa có approval phần cứng mới — 2026-11-06

Baseline thực là `722c4916a83074e8cc5b5c6aeeaee9d2f1190785`, nhánh `chore/modernize-android-build`, worktree sạch lúc bắt đầu. Người dùng cho biết đã rút TBS5530 sau Phase 2.9; mọi descriptor, permission và owner cũ chỉ là lịch sử. Anten/coax chưa được xác nhận cắm: `ANTENNA_CONNECTION_UNCONFIRMED`. Không yêu cầu cắm anten trước gate thử khóa sóng.

| Khoảng trống từ Phase 2.9 tới Phase 3.0 | Đã chứng minh trước phiên này | Việc đang thực hiện an toàn |
| --- | --- | --- |
| FX2 RAM | Một upload 132 A0 full-length và raw operational riêng | Tái sử dụng loader/approval/ledger, không nạp lại từ cache |
| Operational handoff | Candidate 50 byte sau upload, chưa phải READY | Profile raw chặt hơn, đối chiếu metadata Android, attachment mới và claim đúng owner |
| Bridge/I²C | Encoding 0x80 và 0x90/0x91 có tests; chưa có chip-ID thực | Whitelist probe, register sequence có nguồn và fault injection |
| Frontend DVB-T2 | Cấu hình board từ source, chưa init/tune/lock | Port chọn lọc CXD2878/Ascot3, parameters, trạng thái và approval tách scope |
| MPEG-TS | Endpoint 0x82 từ source, chưa thu | Bulk có giới hạn, parser 188 byte và gate lock/RF IN/approval |

Firmware, I²C, frontend lock và TS mới đều `NOT RUN`. Không thay đổi các terminal/consumed records Phase 2.9; không tự stage/commit/push hoặc mở phase khác. Kết quả kiểm mới sẽ được bổ sung sau khi runner hoàn tất, không lấy PASS lịch sử làm PASS của mã đang sửa.

## Checkpoint Git Phase 2.9 — 2026-11-06 01:17 +07

- Người dùng yêu cầu commit toàn bộ changes hiện có và push lên origin của nhánh `chore/modernize-android-build`. Preflight xác nhận HEAD và live origin cùng `92a25b4962ff487c169e67649f454e277bcc60e6`, stage trống, một worktree.
- Đã đối chiếu SHA-256: 24 file nguồn/test/tools không đổi so với fingerprint kiểm cuối; phạm vi commit dự kiến 30 file gồm sáu hồ sơ. Không đưa firmware, APK, raw descriptors, capture, logs, permit hoặc ledger thật vào Git.
- Message được chọn: `feat: complete guarded Phase 2.9 TBS5530 firmware bring-up`. Kết quả commit/push chỉ được xác nhận từ Git sau thao tác; các dòng “chưa stage/commit/push” bên dưới là checkpoint lịch sử trước yêu cầu này.
- Không chạy lại build/tests hoặc Android/USB trong tác vụ Git; kết quả đã kiểm vẫn OPERATIONAL_CANDIDATE, không READY/frontend/TS và không mở phase mới.

## Phase 2.9 — OPERATIONAL_CANDIDATE, firmware bring-up đã đạt gate — 2026-11-06 00:32 +07

**Kết quả thực:** Transaction độc lập thứ ba được người dùng duyệt riêng đã truyền đúng một chuỗi 132 A0 OUT, native started/completed/full-length đều 132, image 8192/8192 byte, payload 8196 byte; hai CPU release trả full-length. Firmware SHA-256 `b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc`, plan `TBS_SOURCE_DUAL_RESET` không đổi, không retry/fallback/corrective hoặc recovery nguồn. Hai GUID trước chỉ tiêu thụ ở host và có 0 native firmware writes; không được reuse.

Sau release có attachment event; observation tự động dừng trước lease mới, snapshot terminal giữ UNCERTAIN/JAVA_IO_EXCEPTION và UPLOAD_COMPLETE_OPERATIONAL_UNVERIFIED. Không sửa lịch sử này. Người dùng duyệt riêng permission + raw read, rồi duyệt thêm update APK giữ data/một raw read để lưu binary và kiểm classifier; mọi lần quan sát đều 0 claim/release/transfer. Raw cuối 00:32:55 +07: 50 byte/COMPLETE_RETURNED_TREE, config 1/interface 0/alt 0, bulk IN 0x82 và 0x81, packet 512, hash `22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5`, binary đã lưu ngoài Git và kiểm hash/length. Owner raw open/read/close 1/1/1, pending/cleanup false, OPERATIONAL_CANDIDATE.

Classifier debug được sửa đúng một điều kiện bInterval của bulk IN: giá trị 1 không phải polling gate cho bulk IN theo tài liệu USB, không thay descriptor hoặc protocol. Unit repro FAIL trước sửa → PASS; vẫn kiểm complete tree/address/direction/type/packet/config/interface/alt, không nới main operational guard. Cả legacy và current-attempt consumed stores giữ true nguyên nội dung; canonical ledger 275 records giữ terminal cũ và không bị sửa theo observation riêng.

**Số đo hardware thực N=132:** ACK p50/p95/p99/max 738/1001/1046/1067 ms; request-start→native 759,066/1022,626/1066,429/1089,356 ms. Native API riêng 0,304/0,466/0,947/6,155 ms, tổng 48,664 ms; duration transaction 106486 ms gồm điều phối/audit/transition/cleanup, không gọi là USB speed. ACK 1500/heartbeat 2000/USB 2000/TTL 300000 ms giữ nguyên. Tăng ACK hoặc USB timeout không cần thiết từ kết quả này, không bảo đảm zero-failure ngoài mẫu.

**Regression mã cuối:** JVM 259 methods PASS, app 47/library 37 rerun PASS, service 2 trước đó không đổi nguồn; debug/release/lint PASS 0 errors/10 warnings, Java 8 major 52/minSdk 21/target 37/127 TBS classes, debug-only isolation/fw-not-bundled/zipalign đạt. Production debug APK cuối hash `12a97e1745c031ec5c80565c6816d00f2e53b50d92aef3a47957b4011f57526e`, cập nhật tại chỗ theo approval read-only, giữ data. Chi tiết lệnh/FAIL/limits tại [WORKLOG.md](WORKLOG.md), [TEST-MATRIX.md](TEST-MATRIX.md) và bảng trước–sau [RESEARCH.md](RESEARCH.md), ADR-023 tại [DECISIONS.md](DECISIONS.md).

**Ranh giới kết luận:** Phase 2.9 đạt mục tiêu firmware upload + operational descriptor trên board/OnePlus đã kiểm. Chỉ OPERATIONAL_CANDIDATE, không READY, không I²C/frontend/tuning/PLP/DVB lock/MPEG-TS hoặc EEPROM/flash PASS. Không tự chuyển Phase 3.0, chạy vendor init hoặc upload nữa. UI diagnostic đã đóng, lease/worker cleanup hoàn tất; không có helper/capture chạy nền. HEAD 92a25b4 và 30 file thay đổi (24 nguồn/test/tools + 6 hồ sơ) vẫn working tree, không stage/commit/push. Các checkpoint “awaiting/blocker” dưới đây là lịch sử, không thay kết quả mới này.

## Phase 2.9 — SOFTWARE_READY_AWAITING_HARDWARE_APPROVAL sau UID split/no-clamp

**Hai approval trước đã tiêu thụ ở host, không có firmware write A0 mới.** Không tái sử dụng hai GUID đó. Root cause đã kiểm: shell đọc uptime được nhưng run-as app bị Permission denied; clock lỗi từng bị clamp về created. Controller nay chạy shell UID đọc clock/boot, chỉ app-private I/O qua run-as. Channel instrumentation dùng private package test, không external-storage fallback; timestamp lấy sau đọc, không Max/clamp, chỉ chờ tick thật có trần trong deadline. Không đổi quyền hệ thống hoặc timeout.

**Gate live hiện tại đạt:** Ba lượt full-path liên tiếp cùng OnePlus/boot, có legacy seed và chờ UI 3500 ms, đều 132/132 PASS, USB thật 0. ACK p50/p95/p99/max: 505/588/625/626; 524/603/854/911; 543/625/662/670 ms. Tổng Android 71.721/78.765/80.427 ms; max request-start→dispatch 933,022 ms. Mỗi lượt fake full-length 132, audit 264/6.711.056 byte, raw files/reads 2/2; helper vẫn 3 ADB subprocess, run-as dispatch attempts 281/284/282 (không phải OS fork count). Baseline callback audit cũ cùng UID/path/legacy wait đo sau đó PASS: ACK 536/632/646/760 ms, tổng 80.026 ms. Không gộp hoặc thay thế các FAIL lịch sử.

Giữ ACK 1500 ms/heartbeat 2000 ms/USB 2000 ms mỗi call/TTL 300000 ms, reserve trước start 240000 ms; max ACK 911 ms và dispatch 933,022 ms dưới mục tiêu 1200 ms, không bảo đảm zero-failure hoặc USB thật. JVM 257/85 instrumentation có phạm vi trước đó vẫn thuộc cùng mã Android production; app 46 methods chạy lại sau UID test PASS 9,12 giây. Host no-clamp mock 61 checks gồm clock/file/UID faults đã PASS; native audit vẫn checked sync/commit/readback, không chuyển async/journal.

**Bảo toàn/điểm dừng:** Legacy consumed so trước/sau không đổi true; ledger thật 8 records/hash sau hai reservations không đổi qua các phép fake. Production APK hiện hash 40fc…411e, cùng mã Android được kiểm; chưa có native firmware upload mới, endpoint operational mới hoặc CPU recovery. Test packages đã export evidence rồi gỡ, giữ production và drivers.test có trước. Git HEAD 92a25b4, working tree chưa stage/commit/push; GitNexus FTS lỗi cuối không dùng làm bằng chứng.

Bước duy nhất kế tiếp là xin phê duyệt độc lập đúng một transaction khác, xác nhận lại nguồn/OTG hiện tại và UI trên OnePlus. Không tự chạy helper thật/GUID thứ ba từ các kết quả software này; Phase 2.9 firmware bring-up chưa hoàn tất. Báo cáo trước–sau/giới hạn tại [RESEARCH.md](RESEARCH.md), gates tại [TEST-MATRIX.md](TEST-MATRIX.md).

## Phase 2.9 — BLOCKED_WITH_EVIDENCE tại heartbeat trước UI, sau approval độc lập thứ hai

**Private UID parity đã có ba lượt 132/132 PASS:** Legacy files được seed, chờ UI 3500 ms, shell đọc clock và chỉ file I/O qua run-as offline app-private; USB thật 0, ACK max 668/1116/952 ms. Tuy nhiên source còn clamp host_seen/PUT timestamp nhỏ dưới 10 ms, trái yêu cầu không sửa timestamp; đang bỏ clamp và đo lại, chưa lấy checkpoint này làm software-ready hoặc phê duyệt hardware mới.

**Đã có root-cause probe mới hơn giả thuyết bên dưới:** Shell UID đọc `/proc/uptime` exit 0, cùng lệnh dưới `run-as` app UID exit 1/Permission denied trên cùng OnePlus/boot. Source/mock chứng minh clock lỗi từng thành -990 rồi clamp về created. Không phải bằng chứng tuner chậm hoặc cần tăng timeout. Đang tách clock/boot ở shell UID và app-file GET/PUT qua run-as, thêm parity test; không su/root/chmod/đổi bảo mật. Live fake có legacy seed trước grant cũng đã bắt mode-gap INVALID_REQUEST_BINDING, đang sửa dùng chung classifier. Chưa có hardware write mới, không cấp approval/GUID thứ ba.

Người dùng đã duyệt riêng một transaction khác với physical conditions hiện tại và UI-ready mới; không dùng lại approval/GUID thất bại trước. APK sửa cuối được update tại chỗ, legacy guard giữ nguyên; fresh permission/raw lại đạt FX2_DEFAULT 189 byte, read-only 1 read/0 transfer, 14 ms. SAF/hash/metadata/challenge mới đạt, helper reserve GUID khác và publish permit v2/authorized ordinal 0.

Helper sau đó chờ tới TOTAL_SESSION_DEADLINE 300 giây, ACK 0/observation 0; app chưa tạo per-attempt store/native audit, report vẫn raw READ_AND_CLOSED. Trong lúc chờ, app foreground nhưng permit heartbeat quan sát vẫn bằng timestamp initial challenge dù đã qua hơn 120 giây; chưa có chứng cứ để quy lỗi cho người dùng không bấm. Đang tái hiện/fix heartbeat bị bỏ qua khi file request legacy khác challenge vẫn tồn tại, trong cùng Phase 2.9. Chưa reset CPU hoặc ghi firmware mới ở cả hai transaction đã duyệt trong phiên này.

Giữ cả hai NEW_APPROVAL_CONSUMED/failure ở ledger, hiện 8 records (enrollment 1, prior consumed 1, production start 2, new consumed 2, fail 2); đã pin hash sau lượt hai. UI đã đóng/revoke, không rerun helper, không cấp GUID khác, không recovery/rút cắm. Cần software fixture chờ UI lâu hơn heartbeat và full-path fake có legacy files trước khi hỏi bất kỳ transaction mới nào. Các checkpoint PASS và lỗi trước bên dưới giữ nguyên lịch sử.

## Phase 2.9 — SOFTWARE_READY_AWAITING_HARDWARE_APPROVAL sau sửa legacy-frame, 2026-11-05

**Chưa có firmware upload mới hoặc operational descriptor.** Transaction đã duyệt bên dưới tiêu thụ GUID ở host rồi dừng trước native; không được dùng lại approval/ID đó. Lỗi stale-frame đã sửa và kiểm lại trong cùng Phase 2.9: helper lọc prior challenge/run trước fields bắt buộc, current malformed vẫn deny trước reservation/grant; fixture dùng đúng old request/permit/RAW report. Mock PASS 45 negative checks/7 production cases/132 loops. Thêm fix giữ AUDIT_FAILED suppressed ở final full-length release + detach để không tiếp observation sai.

**Mã cuối được kiểm mới:** JVM 257 methods PASS, app/library instrumentation 46/37 rerun PASS, service 2 PASS trước đó không đổi; debug/release/lint PASS 0 errors/10 warnings. Baseline fake mới ACK p50/p95/p99/max 364/452/472/474 ms; ba lượt mã cuối liên tiếp 132/132 PASS: 363/436/451/457, 367/453/461/468, 364/447/464/466 ms. Tổng Android 54.124/54.714/51.845 ms; max request-start→dispatch 494,749 ms. Mỗi lượt fake/full-length 132, audit 264, raw files/reads 2/2, USB thật 0; không gộp với FAIL hoặc benchmark trước. Giữ ACK 1500/heartbeat 2000/USB 2000/TTL 300000 ms, không nới deadline.

**Bảo toàn:** Production đã cài bản trước fix final audit, chưa cập nhật mã mới nhất sau transaction thất bại; chỉ test namespaces được cài/gỡ và export evidence. Legacy consumed vẫn true nguyên nội dung; canonical hardware ledger 5 records của reservation/failure đã hash-pin, so sau các dry-runs không đổi. Không clear app/ledger/frames cũ, không native/CPU recovery, không helper production lần hai. Chỉ cần một quyết định mới: có phê duyệt độc lập đúng một transaction khác cùng fresh preflight/nguồn/UI hay không. Nếu chưa duyệt thì dừng ở đây, không commit/push/chuyển phase.

## Checkpoint trước fix — BLOCKED_WITH_EVIDENCE trước native, sau phê duyệt mới ngày 2026-11-05

**Phê duyệt đã được cấp riêng và đã bị tiêu thụ ở host; không có firmware write mới.** Người dùng chọn `approve_one` và xác nhận điều kiện vật lý hiện tại; tiếp đó xác nhận sẵn sàng tự bấm UI trên OnePlus. Debug APK được cập nhật tại chỗ, preferences production so trước/sau vẫn giữ nguyên legacy `consumed=true`. Không uninstall/clear production, không đổi nguồn/OTG hoặc host.

**Fresh preflight đã quan sát:** OnePlus GM1900/API 30/boot đã pin, pin 100% lúc bắt đầu; quyền USB được cấp mới. Raw mới dài 189 byte, COMPLETE_RETURNED_TREE, MATCH/BOOTSTRAP_CANDIDATE/FX2_DEFAULT, hash `e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`, open/read/close 1/1/1, claim/release/transfer 0/0/0. File SAF local đúng 8.192 byte và hash khóa; metadata source/entry được kiểm lại sau tránh IME sửa chữ, không coi UI nhập sai ban đầu là ảnh hợp lệ.

**Blocker tái hiện:** Sau challenge v2 mới và GUID mới được cấp sau approval, helper đã append `NEW_APPROVAL_CONSUMED` rồi dừng ở StrictMode khi gặp request legacy còn trên app chỉ có `challenge,ordinal`, thiếu `approval_protocol/run_epoch`. Initial permit chưa được thay bằng grant mới; ACK/observation 0/0. Report vẫn là fresh raw READ_AND_CLOSED, không bootstrap report; app có 0 per-attempt preferences mới, 0 native audit/raw files theo run epoch mới. Host helper không có USB API; không có native hardware write mới hoặc CPU release.

**Không retry/rearm:** Giữ GUID host đã tiêu thụ và failure ledger, không xóa request/permit cũ hoặc legacy consumed. Đã đóng UI để thu hồi challenge, không khởi helper lần hai hoặc recovery. Đang sửa và kiểm offline đúng nhánh stale-frame này trong cùng Phase 2.9; một transaction phần cứng kế tiếp bắt buộc có phê duyệt/GUID/UI/fresh preflight mới. Không dùng benchmark PASS bên dưới làm firmware bring-up PASS hoặc chuyển phase.

Checkpoint software trước khi phát hiện blocker production được giữ phía dưới; các kết quả PASS ở đó vẫn có phạm vi test riêng, chưa bao phủ đúng legacy-frame tồn dư trên production.

## Phase 2.9 — SOFTWARE_READY_AWAITING_HARDWARE_APPROVAL, 2026-11-05 chiều

**Chưa hoàn tất firmware bring-up.** HEAD và origin đã kiểm là `92a25b4962ff487c169e67649f454e277bcc60e6`, nhánh `chore/modernize-android-build`; các sửa dưới đây đang ở working tree, không stage/commit/push. Không có hardware attempt mới, không tạo GUID/permit thật và không chuyển phase.

- **Đã sửa và kiểm:** Audit phát hiện lỗi sync/commit thay vì chỉ dựa vào AtomicFile log; giữ đủ intent/result và stop provenance. Thêm kiểm deadline không I/O ngay trước dispatch, kiểm lại device/quyền sau authorization và chặn bắt đầu nếu còn dưới 240 giây. Helper/ledger nhận terminal nhanh theo run epoch/boundaries, bảo toàn child failure và không tự reconnect. Chi tiết file/lý do và bảng trước–sau tại [RESEARCH.md](RESEARCH.md), quyết định ADR-021 tại [DECISIONS.md](DECISIONS.md).
- **Full-path không USB, mã cuối có raw evidence riêng:** Baseline callback cũ PASS 132/132, ACK p50/p95/p99/max 363/465/471/472 ms. Ba lượt mã cuối liên tiếp PASS 132/132: 362/452/463/474; 361/449/466/472; 365/447/463/490 ms; tổng Android 54.036/52.618/54.340 ms. Mỗi lượt lưu đúng hai raw fixture files và xác nhận chỉ hai descriptor reads; parser/report vẫn không giữ raw bytes. Các lượt PASS/FAIL trước đó giữ riêng tại RESEARCH/WORKLOG. Mỗi lượt 132 fake full-length và 264 audit checkpoints, USB thật 0; dùng cùng approval/Port/audit/binding/session/coordinator/worker, ảnh và descriptor tổng hợp, không đo Activity/SAF/physical foreground thực.
- **Timeout giữ nguyên:** ACK 1.500 ms, heartbeat 2.000 ms, USB API 2.000 ms/call, TTL 300.000 ms, benchmark 240.000 ms. Ba lượt mã cuối có ACK max 490 ms và request-start → dispatch max 513,080 ms, đều dưới mục tiêu 1.200 ms; không cần nới ACK lên 2.000 ms. Không coi 396 mẫu là bảo đảm không lỗi; ngân sách giả không bảo đảm worst-case USB thật.
- **Regression:** Full JVM PASS 256 methods; app isolated 46 + library 37 + service/native-load 2 = 85 instrumentation methods riêng biệt PASS. Debug/release/lint PASS, 0 lint errors/10 warnings; 127 TBS classes major 52, minSdk 21, DEX debug-only và firmware-not-bundled đạt static. Host mock marker cuối PASS 30 negative checks/132 loops; metadata ACK sau rename được đo riêng, không gọi là fsync-durable. Bảng đầy đủ PASS/FAIL/NOT RUN tại [TEST-MATRIX.md](TEST-MATRIX.md).
- **Audit không được tuyên bố speedup:** Storage-only 264 checkpoints, 6.711.056 byte mỗi mode: cũ 1.604,809 ms, checked commit 2.077,883 ms. Đổi để phát hiện lỗi bền vững, không giảm durability hoặc đổi journal theo phỏng đoán. Host timing mới có margin; không quy hết chênh lệch giữa lượt cho tối ưu code.
- **Bảo toàn và lỗi đã giữ:** Production preferences so trước/sau không đổi, legacy `consumed=true`; canonical host ledger vẫn enrollment 1/prior consumed 1, new events 0. Các lỗi khởi tạo scoped storage, benchmark install binding và no-host fault được giữ ngoài Git, không trộn vào PASS. ADB từng OFFLINE; người dùng tự khôi phục, sau đó kiểm lại cùng model/API/boot và namespace mới, không resume lượt lỗi.

### Một quyết định tiếp theo — chưa có quyền thực thi

Đề nghị phê duyệt riêng đúng một transaction mới trên OnePlus 7, kèm xác nhận hiện tại USB Host + OTG, không nguồn phụ/backfeed, không app khác giữ tuner và đúng một `734c:5530`. Sau phê duyệt mới được cấp GUID, append/fsync `NEW_APPROVAL_CONSUMED` trước permit, cập nhật debug APK tại chỗ nếu bảo toàn guard, fresh preflight boot/permission/owner/descriptor/SAF/hash rồi chờ xác nhận UI riêng. Không dùng permission, report raw hoặc approval cũ.

Plan duy nhất giữ `TBS_SOURCE_DUAL_RESET`: stop 7F92=01 và E600=01; 128 × 64 byte DATA RAM 0000–1FFF; release 7F92=00 và E600=00; delay 100 ms, tổng 132 A0 OUT/8.196 payload bytes. Ảnh 8.192 byte, SHA-256 `b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc`. Fail-stop khi mất bất kỳ guard hoặc native âm/ngắn/exception; không retry/fallback/corrective release hoặc recovery nguồn. Đủ writes chỉ mở lease quan sát mới; endpoint 0x82 chỉ `OPERATIONAL_CANDIDATE`, không READY/frontend/DVB lock/TS.

Attempt 04-11 vẫn CONSUMED/UNCERTAIN với 23 native full-length, 1.344/8.192 image bytes, không CPU release; recovery raw default là lịch sử. Approval upload tương lai không bao gồm rút/cắm, reset/power-cycle hoặc lần thử thứ hai. Mọi can thiệp đó phải hỏi riêng. Chi tiết lệnh/evidence và việc chưa đo tại [WORKLOG.md](WORKLOG.md) và [ENVIRONMENT.md](ENVIRONMENT.md).

## Checkpoint Git Phase 2.9 — 2026-11-05 12:41 +07

- Người dùng yêu cầu commit toàn bộ thay đổi hiện có và push lên origin của nhánh
  `chore/modernize-android-build`. Preflight xác nhận HEAD và live origin cùng
  `d76bbfb63dd21d55af6ce27fdff0543eb500db72`, stage trống, một worktree.
- 28 file nguồn/tools còn đúng fingerprint của lượt kiểm cuối; tổng phạm vi 34 file
  gồm nguồn, tests, tools và sáu hồ sơ. Không đưa firmware, APK, ledger hoặc raw logs vào Git.
- Message: `fix: harden Phase 2.9 bootstrap authorization and telemetry`.
  Commit/push chỉ được xác nhận sau thao tác Git; các ghi chú chưa commit bên dưới là lịch sử.
- Không chạy lại build/tests hoặc thao tác Android/USB trong tác vụ Git này.
  Hardware vẫn `AWAITING_EXPLICIT_NEW_HARDWARE_APPROVAL`; commit không cấp quyền thử mới.


## Phase 2.9 — Phần mềm đã kiểm; chờ phê duyệt hardware mới, 2026-11-05

**Checkpoint cuối helper:** sau sửa đọc đủ pretty JSON qua một file descriptor,
parser giới hạn 256 KiB và phân loại timeout/ledger đúng mode, mock 132 vòng PASS.
Live no-USB cuối **132/132 PASS, 0 timeout**: p50 **359 ms**, p95 **506 ms**,
p99 **714 ms**, max **759 ms**. Lượt 132 PASS trước đó giữ nguyên phía dưới;
264 mẫu từ hai lượt PASS có max 1476 ms, không tuyên bố đã loại mọi outlier.
Production overall giữ tối đa 300 giây theo TTL cũ, benchmark tổng 240 giây;
ACK 1500 ms, heartbeat 2000 ms và native timeout 2000 ms không đổi.
Host ledger đã có prior audit; helper chưa được chạy production, không có token mới.


**Kết quả cuối mới hơn checkpoint bên dưới:** 253 JVM PASS; 29 targeted instrumentation
methods riêng biệt PASS trên OnePlus trong namespace cách ly. Build debug/release/lint
PASS, lint 0 errors/10 warnings, minSdk 21/Java 8/release DEX isolation đạt static.
Harness standalone không còn trong APK debug hoặc release. Không cài lại app production.

Benchmark Android–host **132/132 PASS, 0 timeout, 0 USB calls**: p50 611 ms, p95 705 ms,
p99 783 ms, max 1476 ms. ACK deadline vẫn 1500 ms, heartbeat 2000 ms;
max chỉ còn 24 ms nên không bảo đảm hardware sẽ thành công.
Kênh persistent giảm subprocess, không bỏ fresh nonce/ordinal authorization.
Chi tiết timeline cũ/mới và giới hạn phép so sánh ở [RESEARCH.md](RESEARCH.md).

Legacy consumed vẫn true, không xóa/reset/rearm. Đã lưu canonical host audit cho
attempt cũ, không cấp grant/token mới. Cơ chế new approval dùng GUID riêng, ledger
append/fsync chống reuse và preferences riêng per-attempt; chưa thực thi helper production.
Đủ 132 API full-length chỉ cho phép observer lease mới; detach không bắt buộc,
profile 0x82 vẫn chỉ OPERATIONAL_CANDIDATE, không READY.

**Trạng thái: `AWAITING_EXPLICIT_NEW_HARDWARE_APPROVAL`.** Không upload mới,
không release/reset CPU, vendor/I²C/frontend/TS hoặc recovery nguồn.
Tuner được liệt kê trên OnePlus/API 30; raw default thiếu 82 vẫn là recovery lịch sử,
không phải phép đọc raw mới hoặc firmware thành công ngày 05-11.
HEAD vẫn `d76bbfb63dd21d55af6ce27fdff0543eb500db72`; mọi sửa đổi chưa commit/push.

### Đề nghị duy nhất còn cần người dùng duyệt — chưa thực thi

Nếu muốn thử tiếp, cần duyệt riêng **một transaction mới** và cập nhật debug APK tại chỗ
bằng cơ chế giữ app data, kiểm consumed trước/sau; không uninstall/clear hoặc chạy UTP
vào app production. Preflight lại đúng TCP OnePlus GM1900/API 30, boot/attachment epoch,
owner/pending/cleanup, permission, fingerprint raw mới và ảnh local.
Nguồn phải được người dùng xác nhận lại: OnePlus USB Host qua OTG, không nguồn phụ/backfeed,
không app khác giữ tuner. Không suy nguồn hiện tại từ xác nhận ngày 04-11.

- Candidate duy nhất: `TBS_SOURCE_DUAL_RESET`, 8192 byte, SHA-256
  `b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc`.
- USB: requestType 0x40, request A0, index 0; 7F92=01, E600=01;
  128 × 64 byte RAM 0000–1FFF; 7F92=00, E600=00; delay 100 ms.
  Tổng 132 OUT writes/8196 payload bytes, USB timeout 2000 ms/call.
- Host reservation dùng GUID mới được cung cấp sau approval, ledger canonical đã có
  prior consumed audit; reserve trước permit dù UI chưa bấm. Không reuse GUID cũ.
  Fresh ACK mỗi ordinal trong 1500 ms, heartbeat 2000 ms, session tối đa 5 phút.
- Stop ngay khi ACK/deadline/heartbeat/identity/foreground/permission/device/plan sai,
  native âm/ngắn/exception hoặc audit/cleanup lỗi. Chờ call đồng bộ trả về, không force-close,
  không fallback/retry/corrective release. Sau native write lỗi giữ CONSUMED/UNCERTAIN.
- Thu timeline request/host/Android/native, số call thực, length trả về, stop/cleanup reason,
  hash/epoch và descriptor observer nếu đủ writes. Không thu payload/serial/IP/permit.
- Nếu cần rút/cắm OTG hoặc recovery nguồn thì người dùng phải duyệt và thao tác riêng;
  một approval upload không cho phép recovery hoặc attempt thứ hai.

Chưa có approval cho đề nghị này; không tự chạy helper với các flags mới.

## Phase 2.9 — Tiếp tục sửa handshake và bảo toàn attempt, 2026-11-05

**Không tạo phase mới.** Baseline thực `d76bbfb63dd21d55af6ce27fdff0543eb500db72`,
nhánh `chore/modernize-android-build`, working tree sạch đầu phiên, một worktree.
Đang giữ các thay đổi phần mềm/tests/hồ sơ trong working tree, không commit/push.

Đã sửa phân loại lỗi trước native và native telemetry; IOException tại Port đóng write gate.
Mỗi runner có run epoch, đếm riêng guard/native/full-length/image bytes, audit intent/kết quả,
giữ exception chain đã lọc và cleanup provenance. Diagnostic chỉ debug phân biệt
FX2_DEFAULT, FIRMWARE_UPLOAD_INCOMPLETE và OPERATIONAL_CANDIDATE; không READY.
Approval tests được tách storage/clock, không xóa attempt production.

**Chứng cứ mới có phạm vi:** full JVM snapshot 249 methods PASS; build debug/release/lint
exit 0, release DEX isolation/Java 8/min 21 đạt static. Injected library PortTest lượt đầu
5 tests PASS trên OnePlus nhưng không chạm USB. App tests dùng applicationId suffix riêng:
lượt đầu 1/27 FAIL do assertion namespace, tiếp tục sửa trong phase.
Benchmark Android app_process fake suite 13 PASS; lượt live đầu dừng trước handshake
vì wrapper/helper chưa đồng bộ parameter. Đây không phải benchmark hoặc firmware PASS.
Kết quả rerun cuối được bổ sung riêng sau kiểm chứng.

**Phần cứng:** tuner được liệt kê trên đúng OnePlus GM1900/API 30/USB Host qua Wireless ADB.
Production consumed=true đã kiểm chỉ đọc và không đổi sau instrumentation cách ly.
Raw default thiếu 82 là bằng chứng recovery ngày 04-11, chưa đọc raw mới ngày 05-11.
Không firmware/vendor/I²C/CPU/reset/nguồn/capture mới hoặc tái dùng approval cũ.
Nguyên nhân sâu ACK 24 vẫn UNVERIFIED; đã chứng minh lỗi phân loại/counters/test-store từ source.
Timeline và readiness side-effect matrix: [RESEARCH.md](RESEARCH.md).

**Gate:** hoàn tất phép đo live không USB và rerun phần mềm trước đề nghị hardware mới.
Mọi lượt phần cứng sau này cần phê duyệt mới và ledger/token/epoch không tái dùng;
không clear hoặc rearm consumed. Không chuyển frontend, CXD2878, I²C hoặc TS.


## Checkpoint phát hành Phase 2.9 — 2026-11-04 23:08 +07

- Người dùng yêu cầu commit toàn bộ thay đổi Phase 2.9 và push lên origin của nhánh hiện tại.
  Preflight xác nhận `chore/modernize-android-build`, HEAD và live origin cùng
  `ead0e4adf5051e878706ad70c3863b90cb8ffb98`, stage trống và một worktree chính.
  Phạm vi 18 file: debug executor/approval/UI, seam main, tests, helper và sáu hồ sơ.
- Message dự kiến: `feat: add guarded debug-only TBS5530 Android bootstrap`.
  Chỉ xác nhận commit/push sau kiểm tra Git; không đưa firmware, APK, raw logs hoặc định danh vào Git.
  Các ghi chú chưa commit/push bên dưới là lịch sử trước checkpoint này.
- Không chạy lại build/instrumentation hoặc thao tác USB trong tác vụ Git. Kết quả gần nhất vẫn
  là 241 JVM/56 instrumentation, build debug/release và lint 0 errors/10 warnings của Phase 2.9.
  Hardware attempt UNCERTAIN đã tiêu thụ; recovery raw default không cấp quyền thử lại.


## Phase 2.9 — Một attempt Android UNCERTAIN; phục hồi raw default đã quan sát, 2026-11-04 22:51 +07

**Checkpoint mới nhất:** Người dùng đã duyệt riêng chuyển host/nguồn OTG và một lượt upload,
sau đó tự xác nhận tại UI. Android thực gọi **23 A0 OUT**, đều trả full length:
7F92/E600 stop và 21×64 byte DATA; tổng **1346 byte payload**, trong đó 1344/8192 byte image.
Runner thử bước 24 nhưng chưa tới Android API; guard acknowledgment dừng khoảng 1510 ms.
Không xác nhận nguyên nhân sâu, không gọi là USB timeout/STALL. Attempt consumed=true,
state UNCERTAIN, pending/cleanup=false; không release CPU, retry, fallback hoặc frontend.
Không có descriptors sau firmware; chưa đạt OPERATIONAL_CANDIDATE/READY.

Sau phê duyệt recovery riêng, người dùng rút/cắm đúng một lần trên OnePlus, chỉ nguồn OTG.
Raw mới dùng permission/owner mới: 189 byte/hash `e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`,
MATCH/BOOTSTRAP_CANDIDATE thiếu endpoint `0x82`, open/read/close=1/1/1, 14 ms, không transfer/claim.
Đây là recovery descriptor được quan sát trên lần này, không bảo đảm recovery mọi lỗi.
Tuner hiện ở OnePlus, Windows không còn PnP tuner; Wireless ADB online. Guard consumed còn nguyên.

**Bước đơn tiếp theo:** phân tích offline thời hạn/handshake acknowledgment và telemetry lỗi trước
khi đề nghị bất kỳ thử nghiệm mới nào; chưa đủ điều kiện Phase 3.0. Không dùng lại approval
đã tiêu thụ hoặc tự tăng timeout để nạp lại. Chi tiết [TEST-MATRIX.md](TEST-MATRIX.md),
[WORKLOG.md](WORKLOG.md), [RESEARCH.md](RESEARCH.md).

### Checkpoint offline trước phê duyệt (giữ để truy vết)

Bộ nạp debug đã kiểm offline; lúc checkpoint này chưa duyệt phần cứng.

Baseline/HEAD `ead0e4adf5051e878706ad70c3863b90cb8ffb98`, nhánh
`chore/modernize-android-build`, một worktree chính; đầu phiên sạch. Chưa stage/commit/push.
Đã có Android USB Host adapter debug dùng owner/worker/binding/session hiện hữu,
SAF kiểm size/hash, dual-reset đúng một lượt, approval hết hạn và observer raw trước/sau.
Production vẫn fail-closed; không registry/XML/frontend/TS hoặc driver khác thay đổi.
Quyết định và giới hạn: [ADR-020](DECISIONS.md); xác nhận chip mới chỉ ghi một lần ở [RESEARCH.md](RESEARCH.md).

- **OFFLINE-TESTED PASS:** 241 JVM (227 drivers + 1 usbxfer + 13 service),
  gồm 11 tests mới và ma trận lỗi ở cả 132 vị trí, ảnh thật ngoài Git đúng hash/8192 byte,
  helper mock/deny, Java 8/major 52 và release isolation. Không firmware trong APK.
- **Instrumentation PASS:** 56 tests (21 app + 33 drivers + 2 service), GM1900/API 30
  không tuner. Debug/release/lint exit 0; lint 0 errors/fatal, 10 warnings.
  Lượt cuối 4 phút 19 giây/421 tasks; chi tiết [TEST-MATRIX.md](TEST-MATRIX.md).
- **RUNTIME-OBSERVED:** Wireless ADB đúng OnePlus online. Sau UTP cài lại debug,
  cold launch 670 ms, không USB/TBS, open/claim 0, pending/cleanup=false;
  chọn firmware và upload đều disabled. Windows vẫn TBS BDA OK; chưa chuyển host.
- **NOT RUN:** Android A0/reset/upload/post-firmware descriptors, hardware cleanup/recovery,
  API 21 runtime. Helper approval chỉ kiểm syntax/mock; chưa chạy với ADB thật.
  Không suy fake/Windows là Android PASS hoặc OPERATIONAL_CANDIDATE thật.
- **BLOCKED: EXPLICIT_HARDWARE_APPROVAL.** Bước đơn tiếp theo là người dùng quyết định
  riêng chuyển TBS về OnePlus OTG (không nguồn phụ) và một lượt dual-reset upload.
  Sau phê duyệt phải preflight mới, chọn file và xác nhận UI; không dùng permission/epoch cũ.
  Không tự bắt đầu Phase 3.0. Mọi lỗi sau write là UNCERTAIN, không retry/corrective writes.
- GitNexus refresh cuối lỗi FTS; không dùng graph đó xác nhận mã cuối. Source/diff/tests đã kiểm.
  AgentMemory nhận lưu bài học chung, nhưng response không chứng minh remote-vs-fallback.


## Checkpoint phát hành Phase 2.8 — 2026-11-04 19:10 +07

- Người dùng yêu cầu commit các thay đổi hiện có và push lên origin của nhánh hiện tại.
  Preflight xác nhận `chore/modernize-android-build`, HEAD và live origin cùng
  `ef1154d9c60725150e0d2eaab082d7b644748444`, stage trống; phạm vi 16 file gồm
  mã/tests debug, sáu hồ sơ và ba file hướng dẫn đã có trước tác vụ Git.
- Message dự kiến: `feat: document TBS5530 reset evidence and validate USB transcripts`.
  Chỉ ghi nhận commit/push thành công sau kiểm tra Git; không đưa firmware, capture, APK hoặc raw logs vào Git.
  Các ghi chú “chưa commit/push” bên dưới giữ nguyên trạng thái lịch sử trước checkpoint này.
- Không chạy lại build/tests hoặc thao tác USB trong tác vụ Git. Bằng chứng gần nhất vẫn là
  230 JVM/49 instrumentation, debug/release và lint 0 errors/9 warnings của Phase 2.8.
  Android upload vẫn NOT RUN; yêu cầu commit/push không cấp quyền thử phần cứng.

## Phase 2.8 — Đã đo dual-reset Windows; Android upload chưa chạy, 2026-11-04

**Checkpoint mới nhất, sau capture 18:38 +07:** hướng B đã thu dữ liệu board thật, không đợi TBS.
Trace được duyệt riêng có 3028 packets trên đúng tuner; hai lượt dual-reset do BDA tự phát,
264/264 A0 completions SUCCESS, hai ảnh DATA đều đúng 8192 byte/hash khóa.
7F92 không STALL trong điều kiện quan sát, nhưng chưa chứng minh nó là alias CPUCS.
Descriptor Windows frame 6 thiếu 82; frame 298 sau lượt đầu có IN 82/81 ở alt 0.
Không có lệnh vendor/I²C từ agent; Windows tự khởi tạo và tự nạp hai lượt, không phải Android retry.

Lượt trace 30 giây đầu rỗng vì reconnect muộn; chỉ sau phê duyệt mới mới đo 180 giây.
TShark dừng theo giới hạn; worker elevated còn sót được người dùng dừng, kiểm không còn capture processes.
Hiện tuner ở Windows/BDA OK; Wireless ADB đã phục hồi đúng OnePlus sau hai lần fail-stop,
đều chỉ tiếp tục sau xác nhận của người dùng.
**Android firmware/READY/frontend/TS NOT RUN.** Candidate thử trên board có cơ sở nhất hiện là dual-reset,
không tự đổi E600-only. Còn thiếu kế hoạch/guard/observer one-shot Android và phê duyệt return-host/upload;
không sao chép hai lượt BDA hoặc B7/vendor init thành retry/khởi tạo frontend Android.

Thêm parser transcript debug với 5 tests tổng hợp; parser Java và Node ghép đúng IRP/frame và hash
trên trace thật. Mã cuối đạt **230 JVM/49 instrumentation**, build debug/release, lint 0 errors/9 warnings
trong 4 phút 2 giây; Java 8/major 52 và release isolation đạt.
Sau UTP đã cài lại debug: OnePlus không có USB, owner không pending/open; tuner Windows vẫn OK.
GitNexus refresh cuối lỗi FTS nên không dùng graph đó làm xác nhận mã cuối;
source/diff và tests là chứng cứ. Chi tiết [TEST-MATRIX.md](TEST-MATRIX.md).
[RESEARCH.md](RESEARCH.md) có bảng frame/status; [ADR-019](DECISIONS.md) có bổ sung sau đo.
Các đoạn dưới là checkpoint offline/trước capture, không phải trạng thái capture mới nhất.

**Đã xác định protocol FX2 chuẩn và đường reset trong driver Windows hãng; chưa nạp firmware thật.**
HEAD thực tế `ef1154d9c60725150e0d2eaab082d7b644748444`, nhánh `chore/modernize-android-build`,
là checkpoint Phase 2.7. Working tree có sẵn ba thay đổi hướng dẫn; giữ nguyên, không stage/commit/push
hoặc sửa references. Kết quả lịch sử 218 JVM/49 instrumentation không được dùng thay kết quả mới.

- **SOURCE-CONFIRMED:** TRM FX2 v2.2, Linux generic và libusb chọn CPUCS E600 cho FX2.
  `DOCUMENTED_FX2_E600_ONLY` là candidate độc lập, không phải source TBS hoặc quyền chạy hardware.
  Source TBS không chỉ “bỏ qua lỗi 7F92”: bỏ status stop, nhưng lỗi release 7F92 làm bỏ release E600.
- **OFFLINE-TESTED:** hai gói Windows chính hãng 2.0.0.2/2.1.0.3 vẫn dựng dual-reset.
  Cả ba SYS x64/x86 đã kiểm nhúng nguyên ảnh Linux 8192 byte, cùng hash đã khóa.
  SYS 2.0.0.2 đang cài trùng hash gói hãng. Không có completion URB để biết phản hồi board ở 7F92;
  không chứng minh alias, STALL hoặc request vô hại. Matrix, RVA/hash và phản biện ở [RESEARCH.md](RESEARCH.md).
- **Quyết định B, không đợi hãng:** chuẩn bị quan sát khởi tạo tự nhiên của driver Windows đã cài.
  Wireshark/TShark 4.6.9, USBPcap 1.5.4.0 và driver Running đã xác minh; chưa capture hoặc chuyển tuner.
  Phải được duyệt chuyển host vì BDA có thể tự nạp RAM. Sau mapping đúng root hub/interface/address
  mới duyệt capture giới hạn; không lấy `tshark -D` làm capture PASS. Recovery còn thiếu dữ liệu nguồn ngoài/backfeed/EEPROM.
- **Mã debug:** thêm comparator hai plan có allowlist/provenance, không sửa planner/main ownership,
  facade operational/profile/registry/XML. Dry-run fake kiểm preflight trước/sau bước; test lỗi từng
  write của cả plans, mất ADB và one-shot. Thành công chỉ TRANSITIONING, không READY.
  Gate vẫn false, không có uploader hoặc entrypoint phần cứng. [ADR-019](DECISIONS.md) mở lại ADR-018
  bằng chứng cứ độc lập thay vì bắt buộc chờ TBS.
- **Kiểm thử mới trên mã cuối:** 225 JVM và 49 instrumentation trên GM1900/API 30 đều đạt,
  build debug/release và lint 0 errors/9 warnings. Lượt regression cuối mất 2 phút 29 giây/419 tasks;
  mã cuối giữ gate false. Chi tiết trong [TEST-MATRIX.md](TEST-MATRIX.md).
  Java 8/major 52 và release isolation đạt; runtime API 21/USB 16 KiB chưa chạy mới.
- **Hardware NOT RUN:** không A0 IN/OUT, CPU reset/release, I²C, mode/configuration/alternate,
  EEPROM, frontend hoặc TS; không claim/open raw cycle mới. Wireless ADB được giám sát đúng OnePlus,
  không pair lại hoặc dùng emulator. Firmware/descriptor sau nạp/IN 82/readiness đều chưa có bằng chứng mới.

**Checkpoint sau đó:** người dùng đã duyệt và tự chuyển tuner sang cổng USB 2.0 Windows,
không có nguồn phụ. Một lần mất TCP ADB đã dừng ngay; chỉ tiếp tục sau xác nhận nối lại.
Windows hiện nhận TBS BDA 2.0.0.2/OK; mapping USBPcap3/address 4 đã xác minh, chưa capture.
Android không còn tuner theo dumpsys; report Activity cũ không được coi là attachment mới.

**Bước tiếp theo:** xin duyệt capture giới hạn và một reconnect trên đúng cổng đã xác định.
Chưa đủ điều kiện đề nghị upload Android;
không thử kế hoạch khác khi một attempt lỗi, không tự chuyển sang CXD2878/frontend.
Các mục Phase 2.7 bên dưới giữ nguyên như lịch sử, kể cả dependency hỏi TBS đã được ADR-019 thay thế.

## Checkpoint phát hành Phase 2.7 — 2026-11-04

- Người dùng yêu cầu commit và push toàn bộ thay đổi Phase 2.7 cùng đợt sửa thứ tự, cách diễn đạt tài liệu lên `origin/chore/modernize-android-build`. Preflight xác nhận HEAD và live origin cùng `f03e092702edac5134efea633d8a30a51e1716e4`, stage trống, đúng 18 file; không đưa firmware, APK, logs hoặc định danh thiết bị vào Git.
- Message dự kiến: `feat: add offline TBS5530 FX2 bootstrap validation`. Hash và kết quả push được kiểm từ Git sau thao tác; các ghi chú “chưa stage/commit/push” bên dưới mô tả trạng thái lịch sử trước checkpoint này.
- Tác vụ Git không chạy lại build/tests hoặc thao tác USB. Bằng chứng gần nhất vẫn là 218 phương thức JVM, 49 instrumentation tests, build debug/release và lint 0 errors/9 warnings của Phase 2.7. Upload phần cứng vẫn BLOCKED/NOT RUN; commit/push không phải phê duyệt nạp firmware.

## Phase 2.7 — Hoàn tất kiểm thử offline; chưa cho phép nạp firmware, 2026-11-04

**Đã đối chiếu mô tả USB và ảnh firmware TBS chính hãng; chưa đủ cơ sở nạp lên phần cứng.** Nhánh `chore/modernize-android-build`, HEAD và remote-tracking cùng ở `f03e092702edac5134efea633d8a30a51e1716e4`, là checkpoint Phase 2.6 đã push. Thay đổi Phase 2.7 chưa stage/commit/push. Ba repository tham khảo giữ đúng SHA đã khóa và không có thay đổi.

- **FX2:** bản chụp mô tả USB dài 189 byte của Phase 2.6 khớp mục 3.3 và Appendix B trong TRM v2.2, gồm 4 alternate settings và 18 endpoint. Fingerprint (bộ đặc điểm dùng để đối chiếu thiết bị) trả `MATCH` → `BOOTSTRAP_CANDIDATE`. Kết quả này không chứng minh EEPROM dùng C0, CPU đang chạy, trạng thái RENUM, tốc độ thực hoặc alternate đang active. Không có endpoint `0x82`; giữ nguyên điều kiện kiểm tra trước khi dùng thiết bị, không chọn `0x88` làm TS.
- **Firmware:** archive chính hãng v1.0 qua HTTPS có `dvb-usb-id5530.fw`, dài 8192 byte, SHA-256 `b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc`. Ảnh tuyến tính tại vùng 0000–1FFF phù hợp với loader và Main RAM. Chưa có quyền tái phân phối rõ ràng; file nhị phân chỉ lưu ngoài Git/APK. Nguồn, hash và giấy phép được ghi tại [RESEARCH.md](RESEARCH.md).
- **Điểm chặn quyết định:** FX2 TRM xác định CPUCS tại E600, nhưng không xác nhận 7F92 là địa chỉ tương đương hoặc có thể ghi an toàn. Source TBS/C/Go dùng cả hai; chưa xác minh cách ánh xạ địa chỉ trên chip/board thực và phương án khôi phục. `hardwareUploadAllowed=false`; **chưa hỏi và chưa nhận phê duyệt nạp firmware**. Firmware/CPU/vendor/I²C/TS thật đều `NOT RUN`. Không tạo bộ nạp Android hoặc giao diện upload để tránh kích hoạt nhầm.
- **Mã nguồn:** thêm fingerprint, kiểm tra firmware và dry-run (chạy thử không dùng USB thật) chỉ dành cho debug. Nhận connection EP0 mà không claim interface qua cùng Binding/Coordinator/Session/Transport; giữ số phiên gắn thiết bị, kiểm quyền và định danh ở mỗi transfer, vô hiệu hóa và dọn tài nguyên trì hoãn. Facade bootstrap mặc định từ chối; profile `0x82`, claim, registry và XML của đường operational giữ nguyên. Hoàn tất trên fake chỉ là `TRANSITIONING`, không phải `READY`. [ADR-018](DECISIONS.md) ghi ranh giới và điều kiện mở lại.
- **Kết quả mới:** 218 phương thức kiểm thử JVM (204 + 1 + 13), 49 instrumentation tests (17 + 30 + 2) trên OnePlus GM1900/API 30; không có failure/error/skipped. Build debug/release và lint đạt; lint có 0 errors, 9 warnings. Kiểm Java 8, bytecode major 52 và minSdk 21; release không chứa 9 nhóm class debug. Lượt mã cuối mất 7 phút 55 giây, gồm 419 tasks. Kiểm tra ảnh thật offline đạt `PASS`, không gọi USB thật. Xem [TEST-MATRIX.md](TEST-MATRIX.md) và [WORKLOG.md](WORKLOG.md).
- **Bảo vệ Wireless ADB:** đã dừng một lần khi target biến mất. Chỉ tiếp tục sau xác nhận của người dùng và kiểm lại serial, model, API, USB Host, ứng dụng và tài nguyên đang chờ xử lý. Không tự reconnect/pair hoặc đổi emulator. Cuối phiên, debug khởi động lạnh chỉ đọc metadata: VID:PID `734c:5530`, không có `0x82`, permission=false, generation=0, state=DETECTED, pending/cleanup=false và open/claim/close đều bằng 0. Không lặp lại phép đọc raw descriptors.

**Bước kế tiếp duy nhất:** lấy xác nhận TBS về 7F92, chip và revision của board, ảnh firmware, phương án khôi phục và quyền sử dụng/phân phối. Sau đó mới rà soát lại kế hoạch và điều kiện trước một đề nghị nạp firmware riêng. Không tự chuyển sang CXD2878/tuning/MPEG-TS. Các mục Phase 2.6/2.5 bên dưới là lịch sử, không được đổi thành kết quả `PASS` mới.

## Checkpoint phát hành Phase 2.6 — 2026-11-04 00:18 +07

- Người dùng yêu cầu commit và push 16 file Phase 2.6 trên nhánh `chore/modernize-android-build`. Preflight xác nhận HEAD và live origin cùng `df076d28dd8100d5caa363f1d866a274e3f2b636`, stage trống; không đưa JSON đính kèm ngoài repository, APK, raw logs hoặc firmware vào Git.
- Message dự kiến: `feat: add debug-only raw USB descriptor inspection`. Hash và kết quả push được xác minh từ Git sau thao tác; các mục “chưa stage/commit/push” bên dưới là lịch sử trước checkpoint này.
- Đã dừng trước stage khi Wireless ADB offline. Sau người dùng xác nhận nối lại, tra cổng mới và xác minh GM1900/API 30/state=device, USB Host=true, metadata 734c:5530, permission=false, generation 0/DETECTED, không pending hoặc open/claim. Không tái dùng USB handle hoặc mở chu kỳ mới.
- Không chạy lại build/tests trong tác vụ Git. Bằng chứng gần nhất vẫn là 199 JVM methods, 47 instrumentation tests, debug/release build, lint 0 errors/9 warnings và một raw cycle đã duyệt; firmware/USB mode và streaming vẫn BLOCKED/UNKNOWN.

## Phase 2.6 — Đã đọc raw descriptors; firmware/USB mode vẫn chưa xác minh, 2026-11-04 00:06 +07

**Diagnostic chỉ đọc đã hoạt động trên OnePlus 7; blocker không phải chỉ do Android biểu diễn alternate. Chưa cho phép firmware/bridge bring-up.** Nhánh `chore/modernize-android-build`, HEAD và remote-tracking `df076d28dd8100d5caa363f1d866a274e3f2b636` (checkpoint Phase 2.5 đã push); thay đổi Phase 2.6 chưa stage/commit/push, không reset/merge. Ba references giữ đúng SHA khóa và sạch.

- **Wireless ADB:** GM1900/API 30/state=device, dùng một serial TCP mới tra, không chọn alias mDNS hoặc emulator. Đã dừng một lần khi serial cũ mất; chỉ tiếp tục sau xác nhận, kiểm lại USB Host, attachment, permission và tạo owner mới. Kiểm kết nối mỗi 10 giây trong build/tests dài; không ghim IP/port vào Git.
- **Permission thật:** người dùng tự Allow; thao tác 1/generation 1 nhận callback đúng một lần, correlation=true. Trạng thái trước cleanup là PERMISSION_GRANTED, sau là CLOSED, không open/claim. Đây là bằng chứng mới so với callback chưa chứng minh ở Phase 2.5.
- **Raw cycle được duyệt một lần:** thao tác 2/generation 2, openDevice/getRawDescriptors/close đúng một lần, 13 ms trên worker, không claim/transfer, permission và identity hợp lệ, không pending/cleanup. Trả 189 bytes, SHA-256 `e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`; parser đọc đủ tree, không lỗi. Không lưu raw bytes/serial/path vào Git.
- **Descriptor mới:** bcdUSB=0200, bcdDevice=0001, EP0 packet=64, device class=FF; configuration value=1, wTotalLength=171, bNumInterfaces=1, công suất khai báo 100 mA; interface ID 0 có alternate 0–3. Alternate 0 rỗng; alternate 1 có Bulk 01/81/02/04/86/88; alternate 2/3 giống metadata lịch sử. Cả 18 endpoint đều khớp giữa raw và metadata, không có IN 82. Không suy alternate đang active, tốc độ thực, cold/warm hoặc 88=TS.
- **Nguồn và mode:** official/C/Go đều chọn TS 82, load firmware ở probe/open; official “cold” là bảng ID, không đo firmware OnePlus. ZIP ChangeMode chính hãng chỉ có EXE; kiểm tra tĩnh thấy nhãn DVB-S/S2, DVB-T/T2 và BDA, không chứng minh đổi USB descriptors/firmware hoặc cơ chế khôi phục. Không chạy tool. Chi tiết ở [RESEARCH.md](RESEARCH.md).
- **Phần mềm:** ba class raw mới chỉ trong `drivers/src/debug`, hai suites JVM debug; controller/activity/report thêm nút raw và cơ chế giữ quyền thao tác độc quyền. Không sửa production Session/Binding/Coordinator/Facade/Descriptors/registry. [ADR-017](DECISIONS.md) chốt capability riêng, không claim/transfer.
- **PASS mới:** 199 JVM methods (TBS 168), 47 instrumentation tests (17 + 28 + 2) trên OnePlus/API 30, không failure/error/skipped. Debug/release build, lint 0 errors/9 warnings, Java 8/bytecode 52, native smoke/XML; release không chứa sáu class diagnostic. Regression cuối sau raw mất 2 m 2 s/419 tasks; không dùng số 180/44 lịch sử thay kết quả mới. Xem [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md).
- **Cuối phiên:** UTP cài/gỡ app; đã cài lại debug APK và khởi động lạnh chỉ đọc metadata, generation 0/DETECTED, không pending/cleanup hoặc open/claim, permission=false. Không xin lại và không chạy raw lần hai. Bằng chứng raw trước đó được giữ trong hồ sơ và báo cáo session đã lọc.

**BLOCKED/NOT RUN:** khả năng streaming, alternate đang active, firmware cold/warm và USB mode, hash/giấy phép/address map firmware thật, vendor/I²C/chip ID/tuner/TS, chạy ChangeMode/reset hoặc ghi configuration/alternate. Kế tiếp chỉ chuẩn bị tài liệu TBS về mode và cơ chế khôi phục, xác minh firmware và kế hoạch trước/sau trên cùng board được duyệt riêng. Giữ production IN 82; không tự bước sang phase tiếp theo.

## Checkpoint phát hành Phase 2.5 — 2026-11-03 22:43 +07

- Người dùng yêu cầu commit và push 14 file còn lại: 8 file mã nguồn/tests Phase 2.5 và 6 hồ sơ đã biên tập. Nhánh `chore/modernize-android-build`; preflight xác nhận local và origin cùng `dc50e7304941a7d01aa1d490c13fcf09683a01c9`, stage trống. Wireless ADB đã xác minh đúng GM1900/API 30, không thao tác USB.
- Message: `feat: add permission-only USB preflight and private diagnostic reports`. Hash và kết quả push được xác minh từ Git sau thao tác; các mục “chưa commit/push” bên dưới là lịch sử trước checkpoint.
- Không chạy lại build/tests trong tác vụ Git. Bằng chứng gần nhất vẫn là 180 unit methods, 44 instrumentation tests trên OnePlus/API 30, debug/release build và lint 0 errors/11 warnings. Connection cycle thật vẫn BLOCKED bởi profile thiếu IN 82; không firmware hoặc vendor requests.

## Checkpoint biên tập tài liệu — 2026-11-03 22:26 +07

Chỉ rà soát và sửa cách diễn đạt trong STATUS, ENVIRONMENT, DECISIONS và TEST-MATRIX: tách từ bị dính, làm rõ câu văn và bảng, giữ nguyên bằng chứng lịch sử. Trạng thái hiện tại vẫn là Phase 2.5 trên nhánh `chore/modernize-android-build`, HEAD `dc50e7304941a7d01aa1d490c13fcf09683a01c9`. Không chạy lại kiểm thử hoặc build, không thao tác phần cứng; các kết quả bên dưới là bằng chứng đã ghi trước đợt biên tập. Xác minh tổng thể hoàn tất lúc 22:39 +07: UTF-8, liên kết, bảng, SHA, lệnh và số liệu lịch sử được giữ nguyên; 8 file mã nguồn/test không thay đổi trong đợt biên tập. Đã dừng khi Wireless ADB mất kết nối và chỉ tiếp tục kiểm cuối sau khi người dùng xác nhận nối lại; không chạy lại build/tests hoặc thao tác USB.

## Phase 2.5 — OnePlus 7 nhận USB thật; chu kỳ kết nối bị chặn bởi mô tả thiết bị, 2026-11-03 22:16 +07

**Đã kiểm USB Host và metadata (thông tin mô tả thiết bị) trên OnePlus 7 GM1900, Android 11/API 30. Chưa có bằng chứng open/claim hoặc DVB hoạt động.** Nhánh `chore/modernize-android-build`, HEAD `dc50e7304941a7d01aa1d490c13fcf09683a01c9`; Phase 2.4B/ADR-015 đã push tại baseline này. Có 8 file mã nguồn/kiểm thử và 6 hồ sơ sửa, chưa stage/commit/push/merge.

- **Wireless ADB (ADB không dây):** lần đầu offline nên dừng ngay. Sau khi người dùng xác nhận nối lại, tra port mới, xác minh GM1900/API 30/state=device và chỉ chọn một serial tường minh. Các lượt build/kiểm thử dài đều kiểm kết nối mỗi 10 giây; không chuyển sang emulator hoặc tự ghép nối lại.
- **USB thật:** USB Host=true; TBS 734c:5530 có một cấu hình với ID 1 và bốn mục cùng interface ID 0, alternate 0–3. Alternate 0 rỗng; alternate 1 có Bulk 01/81/02/04/86/88. Alternate 2/3 có Interrupt/Isochronous như bảng [RESEARCH.md](RESEARCH.md). **Không alternate nào có IN 82**, khác snapshot WSL lịch sử; không tự đổi endpoint, firmware hoặc alternate.
- **Quyền truy cập USB:** hộp thoại hệ thống thật đã hiện, người dùng tự chọn OK. Khi đọc lại, lượt đầu đã hết thời hạn 30 giây và receiver đã được dọn; callback count=0, open/claim=0. Sau khi làm mới, hasPermission=true; lượt chỉ yêu cầu quyền mới xác nhận quyền đã có sẵn và trạng thái PERMISSION_GRANTED trước khi dọn tài nguyên. Không tuyên bố callback cấp/từ chối quyền thật PASS. Sau UTP cài/gỡ kiểm thử, APK cuối được cài lại và metadata hiện permission=false; không tự yêu cầu lại hộp thoại.
- **An toàn khi nhận connection và claim interface:** công cụ chẩn đoán chỉ bật chu kỳ khi metadata khớp profile (bộ điều kiện kiểm tra) mặc định; profile OnePlus hiện bị BLOCKED trước open. Nút chỉ yêu cầu quyền dùng cùng binding/coordinator và bảo đảm việc cấp quyền không dẫn đến nhận connection hoặc claim interface. Không gọi setInterface, setConfiguration, getRawDescriptors, controlTransfer, I²C, bulk, firmware hoặc reset.
- **Báo cáo:** JSON chỉ có trong debug, tại `files/tbs5530-diagnostic.json`, đọc qua run-as; có timestamp/operation/generation, model/API, đầy đủ descriptors, permission/correlation count, state trước cleanup, counters/thread/duration/events và lý do chặn. Đây là các trường về thời điểm, thao tác, phiên, mô tả thiết bị, quyền, trạng thái và bộ đếm chẩn đoán. Không ghi path/device ID/serial/credentials. API 30 được kiểm thật, không nhầm compile 37.2 thành runtime 37.
- **PASS mới:** **180 phương thức unit test** (drivers 166/usbxfer 1/dvbservice 13); **44 instrumentation tests** trên GM1900/API 30 (app 14 + drivers 28 + dvbservice 2), 0 failure/error/skipped. Debug/release APK và lint PASS: 0 errors/0 fatal/11 warnings; Java 8, bytecode 52; manifest/DEX của release không chứa công cụ chẩn đoán. Lượt cuối 4 m 32 s/418 tasks: 36 executed, 382 up-to-date, với việc kiểm tra kết nối xuyên suốt.

**BLOCKED/NOT RUN:** open/claim(false)/release/close thật, rút thiết bị thật, đọc raw descriptors, vendor/firmware/frontend/lock/TS. Không ép claim alternate 1 hoặc nhận 88 là TS; cần nghiên cứu descriptor/trạng thái firmware và được duyệt phạm vi tiếp theo. [ADR-016](DECISIONS.md) ghi quyết định tách việc xin quyền khỏi nhận connection/claim interface và giới hạn bằng chứng.

**Điểm dừng:** giữ debug APK cuối trên điện thoại, không rút cáp/reset hoặc mở thêm chu kỳ. Kế tiếp rà soát khác biệt descriptor và nguồn gốc firmware trước khi xin phép khởi động phần cứng riêng; việc nhận diện thiết bị/cấp quyền không chứng minh DVB-T2 hoạt động. Không tự chuyển phase hoặc commit/push.

## Checkpoint phát hành Phase 2.4B và tài liệu — 2026-11-03 19:17 +07

- Người dùng yêu cầu commit và push các thay đổi còn lại: 5 file công cụ chẩn đoán/debug tests và 6 hồ sơ đã biên tập. Nhánh `chore/modernize-android-build`; preflight xác nhận local và origin cùng `c153e05e454f506f5c6639685d2cded51e8c0126`, vùng stage trống.
- Message: `feat: add debug USB preflight diagnostics and polish project docs`. Hash và kết quả push được kiểm từ Git sau thao tác; các mục “chưa commit/push” bên dưới là lịch sử trước checkpoint này.
- Không chạy lại build hoặc tests trong tác vụ Git. Bằng chứng gần nhất của Phase 2.4B vẫn là 177 unit methods, 42 instrumentation tests, debug/release build và lint 0 errors/11 warnings. USB passthrough vẫn bị chặn bởi backend không hỗ trợ driver BDA; chưa có permission hoặc connection cycle thật trên Android.

## Rà soát cách diễn đạt — 2026-11-03 17:35 +07

Tác vụ biên tập sáu hồ sơ dự án nhằm tách các từ bị dính và làm câu văn dễ đọc hơn, không thay đổi bằng chứng lịch sử. Nhánh `chore/modernize-android-build`, HEAD `c153e05e454f506f5c6639685d2cded51e8c0126`; các thay đổi Phase 2.4B chưa stage/commit/push/merge được giữ nguyên. Không chạy lại kiểm thử, build hoặc thao tác phần cứng trong tác vụ biên tập này. Việc xác minh chung cả sáu hồ sơ được thực hiện sau khi hoàn tất biên tập.

## Phase 2.4B — Chuyển tiếp USB bị chặn ở lớp Windows, 2026-11-03 16:51 +07

**Đã thử xuyên suốt trong phạm vi cho phép; TBS5530 chưa xuất hiện trong Android guest (máy ảo Android). Đã hoàn thiện công cụ chẩn đoán chỉ dành cho debug và kiểm thử hồi quy.** Nhánh `chore/modernize-android-build`, HEAD `c153e05e454f506f5c6639685d2cded51e8c0126` xác nhận checkpoint Phase 2.4A/ADR-014 đã commit/push. Có 5 file debug/test mới và 6 hồ sơ cập nhật, chưa stage/commit/push/merge.

- **Trở ngại có log thực:** Emulator 37.2.13/QEMU 2.12.0 nhận usb-host và QMP thấy thiết bị phía host 734c:5530/480 Mb/s, nhưng `winusb_open` báo “unrecognized device driver”; `libusb_open` trả **-12/LIBUSB_ERROR_NOT_SUPPORTED**. Windows dùng TBS BDA 2.0.0.2/tbsBDA.TBS5530. Không đổi driver hoặc cố thử lại liên tục.
- **Quyền sở hữu USB:** BUSID 6-2 được tra lại trực tiếp, ở trạng thái Shared, không phải Attached; WSL Ubuntu 22.04 ở trạng thái Stopped. Không cần usbipd detach, không bind/unbind/attach WSL. Không tìm thấy ứng dụng tuner phổ biến nào khi quét theo tên tiến trình; không coi phép quét này là bằng chứng mọi handle đều trống.
- **Máy ảo Android:** AVD API 37/x86_64 khởi động được với cấu hình gốc; sysfs chỉ có root USB hubs, không có TBS. USB Host feature=false. Metadata phía host qua QMP không phải kết quả Android nhận diện thiết bị. Quyền đọc dmesg/các thuộc tính sysfs bị từ chối, dumpsys usb bị timeout khi khởi động với passthrough; không dùng biện pháp né giới hạn bằng root/SELinux/kernel.
- **Công cụ chẩn đoán:** Activity phải được gọi tường minh, chỉ có trong debug và không tự chạy; Refresh chỉ đọc metadata. Chỉ khi người dùng bấm nút mới yêu cầu quyền hoặc chạy một chu kỳ bằng binding hiện hữu. UI trên AVD thường xác nhận không có USB Host/0 devices/0 open/0 claim. Báo cáo lọc định danh, không có control API. Manifest/DEX của release **không chứa công cụ chẩn đoán**, USB Host required=true của release không đổi; debug có cấu hình optional riêng.
- **PASS phần mềm mới:** 177 phương thức kiểm thử JVM, **42 instrumentation tests** (12 diagnostic + 28 facade + 2 native/XML), 0 failure/error/skipped sau khi sửa kiểm thử clipboard để chờ cửa sổ nhận focus. APK debug/release và lint PASS, 0 Error/0 Fatal/11 Warning; Java 8, bytecode 52. Emulator từng có lớp phủ báo Launcher/SystemUI ANR, đã chọn Wait; không gộp thành lỗi ứng dụng.

**Hardware permission/open/claim/release/close/detach đều BLOCKED/NOT RUN** vì Android framework không có TBS; không hiện hộp thoại, không bypass/grant ADB, không gửi control vendor/I²C/firmware/reset/LNB/tune/TS. Thử XHCI có căn cứ nhưng không cải thiện; đã dừng và trả môi trường về trạng thái không còn emulator hỗ trợ kiểm thử, không đổi BDA/quyền sở hữu USB.

**Bước tiếp theo sau phase:** review [ADR-015](DECISIONS.md), rồi chọn thiết bị Android thật hỗ trợ USB Host/OTG/nguồn và phê duyệt kế hoạch metadata → quyền do người dùng cấp → một chu kỳ, hoặc review riêng việc thay WinUSB với phương án khôi phục BDA. Không tự chuyển thiết bị hoặc mở Phase 2.5; xem [RESEARCH.md](RESEARCH.md)/[TEST-MATRIX.md](TEST-MATRIX.md)/[WORKLOG.md](WORKLOG.md) để phân biệt từng tầng PASS/FAIL/BLOCKED.

## Checkpoint phát hành Phase 2.4A — 2026-11-03 15:05 +07

- Người dùng yêu cầu commit và push 16 file Phase 2.4A trên `chore/modernize-android-build`. Preflight: local/live origin cùng `8a004a3cc94d8c736da84cad1908b6a270b789a0`, staged diff rỗng; đúng 11 file Java và 5 hồ sơ.
- Message: `feat: add managed USB control transport and descriptor validation`. Hash và kết quả push phải xác minh từ Git sau thao tác. Những mục “chưa commit/push” bên dưới là snapshot trước checkpoint.
- Không chạy lại kiểm thử trong tác vụ Git; bằng chứng gần nhất là 177 phương thức kiểm thử đơn vị, 30 instrumentation tests, debug/lint và 5 lượt stress đã chạy trong Phase 2.4A. Phase 2.4B vẫn NOT RUN, chưa phê duyệt phần cứng.

## Phase 2.4A — Lớp truyền lệnh USB Android có quản lý, 2026-11-03 13:38 +07

**Đã hoàn tất 2.4A bằng USB giả và kiểm thử Android; đã chuẩn bị kế hoạch 2.4B. Toàn bộ kiểm thử USB thật vẫn NOT RUN, chờ phê duyệt riêng.** Nhánh `chore/modernize-android-build`, HEAD `8a004a3cc94d8c736da84cad1908b6a270b789a0`, đúng checkpoint Phase 2.3/ADR-013 đã push. Có 11 file Java mới/sửa và 5 hồ sơ; chưa stage/commit/push/merge.

- **Transport (lớp truyền dữ liệu):** Tbs5530ControlTransport chỉ được coordinator/binding cấp sau khi connection hiện tại đã được nhận và claim. Handle gắn với generation, tức số phiên hiện hành; không mở connection thứ hai hoặc đưa UsbDeviceConnection cho bên gọi. Mỗi lần gọi đều kiểm owner/token/claim/state trước và sau. Truyền thiếu dữ liệu, kết quả âm và lỗi không được tự thử lại, không công bố dữ liệu IN. Không tự READY, thăm dò, reset hoặc nạp firmware.
- **Lifecycle (vòng đời phiên):** tái sử dụng Session.transaction, giữ nguyên tính liên tục của cặp I²C 0x90/0x91. Detach, timeout logic và close vô hiệu hóa session ngay; release/close chờ giao dịch đang chạy trả về. Chặn main thread trước khi lấy khóa; không cưỡng bức đóng hoặc giả định UsbRequest.cancel hủy được lời gọi đồng bộ.
- **Descriptors (mô tả thiết bị):** metadata bất biến, chọn Bulk IN 0x82 theo interface ID 0/alternate/direction/type; từ chối trường hợp thiếu, trùng hoặc mâu thuẫn. Packet size 64/512 là giới hạn phần mềm của profile, không chứng minh tốc độ USB. Không chọn 0x81 chỉ vì đứng trước, không có TS reader hoặc tự gọi setInterface.
- **PASS thực:** **177 phương thức kiểm thử đơn vị** (drivers 163/usbxfer 1/dvbservice 13), 0 failure/error; **30 instrumentation tests** (28 facade/control + 2 native/registry) trên AVD API 37/x86_64, 0 failure/error/skipped. APK debug PASS; lint 0 Error/0 Fatal/11 Warning. Lượt tổng hợp exit 0/8 m 8 s/241 tasks: 50 executed, 191 up-to-date. Core Java 8/bytecode 52 và 5 lượt × 24 transport/concurrency tests đều PASS.
- **Giữ nguyên:** toolchain, dependencies, min 21/source-target 8, EP0 bytes/Bridge/FirmwarePlan, native/JNI, driver cũ, service/registry/XML/TCP. Ba repository tham khảo giữ SHA khóa và sạch. Instrumentation dùng fake Manager/RawConnection; wrapper gọi UsbDeviceConnection.controlTransfer **chưa được kích hoạt trên phần cứng**.

**Kế hoạch 2.4B:** [RESEARCH.md](RESEARCH.md) có danh sách kiểm tra môi trường/nguồn/OTG → nhận diện thiết bị → quyền do người dùng cấp → open/claim(false) → release/close, bảng bằng chứng và điều kiện dừng. Cần chọn Android Host cụ thể và phê duyệt riêng từng phạm vi. Chưa tạo hoặc chạy điểm vào chẩn đoán cho USB thật. [ADR-014](DECISIONS.md) ghi thiết kế quyền sở hữu và bên sở hữu trong tương lai.

**Giới hạn:** path/id được dùng lại khi bỏ lỡ reconnect không đủ chứng minh đó là cùng thiết bị; lời gọi đồng bộ không trả về vẫn PENDING/chặn mở lại; kết quả âm không phân loại được mọi timeout. Việc kích hoạt alternate khác 0 và runtime OS 21/23/31/33/34 chưa được kiểm chứng. USB thật, firmware, frontend, DVB lock và TS đều **NOT RUN**. Dừng sau 2.4A và kế hoạch 2.4B, không tự khởi động phần cứng.

## Checkpoint phát hành Phase 2.3 — 2026-11-03 12:57 +07

- Người dùng yêu cầu commit và push 12 file Phase 2.3 trên `chore/modernize-android-build`. Preflight: local và live origin cùng `635ee4a57267de6055126efcfee6121b30701433`, staged diff rỗng; đúng 7 file Java mới/sửa và 5 hồ sơ.
- Message: `feat: add Android USB lifecycle binding and instrumentation`. Hash/kết quả push kiểm từ Git sau thao tác; không ghi trước thành công. Các mục “chưa commit/push” bên dưới là snapshot trước checkpoint.
- Không chạy lại kiểm thử trong tác vụ Git: bằng chứng gần nhất vẫn là 152 phương thức kiểm thử đơn vị, 21 instrumentation tests, debug/lint và 3 lượt stress đã chạy ở Phase 2.3. Không đổi logic hoặc mở Phase 2.4/phần cứng.

## Phase 2.3 — Lớp kết nối với Android USB framework, 2026-11-03 12:39 +07

**Đã hoàn tất binding và kiểm thử phần mềm, chưa dùng USB tuner thật.** Nhánh `chore/modernize-android-build`, HEAD `635ee4a57267de6055126efcfee6121b30701433` đúng baseline đã push và chứa ADR-012. Có 7 file Java mới/sửa cùng 5 hồ sơ; chưa stage/commit/push/merge.

- **Kiến trúc:** Tbs5530UsbBinding giữ định danh, thông tin ghép yêu cầu với phản hồi và vòng đời phiên trên JVM; Tbs5530AndroidUsbFacade bọc UsbManager/PendingIntent/receiver/connection; Tbs5530AndroidRuntime sở hữu worker và scheduler độc lập. Chưa nối service/UsbDelegate/registry/XML; constructor không tự yêu cầu quyền hoặc mở thiết bị. Các wrapper không có API vendor control/firmware/streaming.
- **Permission (quyền truy cập):** mỗi request có action/data/correlation riêng, PendingIntent dùng một lần và giới hạn trong package; API 31+ dùng MUTABLE cho fill-in extras, API 33+ dùng receiver NOT_EXPORTED. Receiver chỉ parse/dispatch; worker kiểm current attempt/epoch, danh sách thiết bị trực tiếp và hasPermission trước/sau open/claim. Interface chọn bằng ID/alternate, force=false; quyền sở hữu mới nhận một phần hoặc bị từ chối được cleanup theo đúng lease.
- **Threading (điều phối luồng):** thời hạn permission/acquisition độc lập, cancellation chỉ vô hiệu hóa; không được coi thao tác vật lý/cleanup đã hoàn tất khi chưa xong. Session/coordinator thêm điểm tách logical invalidate/drain để receiver/main không chạy cleanup. Không cưỡng bức đóng connection đang open/claim/transfer hoặc join main/self-worker.
- **PASS thực:** **152 phương thức kiểm thử đơn vị** (drivers 138/usbxfer 1/dvbservice 13), 0 failure/error; **21 instrumentation tests** trên AVD TBS5530_Runtime_API37, API 37/x86_64 (19 binding + 2 native/registry). Main thread vẫn phản hồi khi fake open/claim bị chặn; PendingIntent fill-in/one-shot thực và việc đăng ký/dọn receiver đều đạt. APK debug PASS, lint 0 errors/0 fatal/11 warnings. Lệnh chính xác ở [WORKLOG.md](WORKLOG.md), phạm vi chứng minh ở [TEST-MATRIX.md](TEST-MATRIX.md).
- **Runtime không tuner:** cài APK Success, cold start Status ok/3453 ms, Welcome → Diagnostic → START hiện “No devices found” và được xử lý, không có fatal/linkage/security exception trong cửa sổ Logcat ứng dụng đã lọc. Emulator có SystemUI ANR riêng, đã chọn Wait; không coi đó là app ANR. Emulator hỗ trợ kiểm thử đã dừng sau kiểm thử, không wipe dữ liệu.
- **Giữ nguyên:** EP0/I²C/FirmwarePlan, driver cũ, enum/TCP, registry/XML, native/JNI và toolchain/dependencies; ba repository tham khảo giữ SHA khóa/sạch. API 21/23/31/33/34 chỉ có nhánh chính sách/tài liệu, **chưa chạy runtime trên các OS đó**. Permission dialog/USB Host thật, firmware/frontend/lock/TS **NOT RUN**.

**Giới hạn:** device path/id của OS không chứa attachment epoch; nếu bỏ lỡ detach + reattach và toàn bộ trường được dùng lại thì không thể phân biệt chắc chắn. Detach đến muộn được kiểm bằng danh sách thiết bị trực tiếp, không tự đóng thiết bị hiện tại chỉ vì VID/PID giống nhau. Lời gọi đồng bộ không trả về vẫn pending/chặn mở lại; bên sở hữu executor phải đóng binding trước runtime, không thể bảo đảm dừng thao tác native đang bị chặn. Dynamic receiver không giữ process hoạt động sau khi owner mất lifecycle.

**Trước Phase 2.4:** review [ADR-013](DECISIONS.md), framework binding/quyền sở hữu process và identity/deadline trên thiết bị Android thật. Chỉ khởi động phần cứng sau phê duyệt riêng, xác minh nguồn gốc/license/hash/address map của firmware và có kế hoạch lệnh/khôi phục; không tự bật registry, uploader, frontend hoặc TS.

## Checkpoint phát hành Phase 2.2 — 2026-11-03 11:33 +07

- Người dùng yêu cầu commit và push 11 file Phase 2.2 trên `chore/modernize-android-build`. Preflight: HEAD local và live origin cùng `6eb23537484a3e1dd263bd5a88f56914c7c5e313`, staged diff rỗng; chỉ có 6 file Java mới/sửa và 5 hồ sơ.
- Message: `feat: add TBS5530 lifecycle coordinator and safe cancellation`. Hash/kết quả push kiểm từ Git sau thao tác, không ghi trước thành công. Các mục “chưa commit/push” bên dưới là snapshot trước checkpoint.
- Không đổi logic hoặc chạy lại kiểm thử trong tác vụ Git; bằng chứng gần nhất là 126 phương thức kiểm thử đơn vị PASS/debug PASS/lint 0 errors, 11 warnings và 5 lượt stress của Phase 2.2. Không thao tác hardware/firmware hoặc mở Phase 2.3.

## Phase 2.2 — Vòng đời phiên và adapter giả, 2026-11-03 11:25 +07

**Đã hoàn tất phạm vi fake/JVM; chưa tích hợp transport Android hoặc phần cứng.** Nhánh `chore/modernize-android-build`, HEAD `6eb23537484a3e1dd263bd5a88f56914c7c5e313`. Phase 2.1/ADR-011 đã commit, fast-forward và push ở chính baseline này; các nhánh feature/docs trước đã xóa local, docs đã xóa trên origin theo yêu cầu riêng. Không commit/push/merge trong Phase 2.2.

- **Nguy cơ tái hiện trước khi sửa:** hai kiểm thử fake blocked transfer + detach/cancel đều FAIL trên baseline vì Bridge giữ session monitor. Sau khi sửa, việc vô hiệu hóa lifecycle không phải đợi giao dịch vật lý hoàn tất; cleanup không đóng connection còn được transaction sử dụng.
- **Kiến trúc:** Session có khóa giao dịch riêng và state lock ngắn; cặp 0x90/0x91 vẫn atomic (không bị giao dịch khác xen ngang). Close/cancel/detach vô hiệu hóa ngay, chặn giao dịch mới, hoãn cleanup tới giao dịch cuối đang chạy và thực hiện ngoài cả hai khóa. `isCleanupPending`/`getCleanupFailure` phân biệt việc vô hiệu hóa với cleanup hoàn tất. Không cưỡng bức hủy synchronous I/O.
- **Coordinator mới:** các cổng chỉ dùng JVM cho permission/open/claim; request gắn generation + attachment identity, không chỉ VID/PID. Callback trùng/sai/cũ không acquire. Lease thuộc coordinator cho tới khi Session nhận; trường hợp acquire bị từ chối hoặc mới hoàn tất một phần được cleanup riêng đúng một lần. Không có Android framework binding/permission dialog/USB transport, không tự READY.
- **PASS thực:** **126 phương thức kiểm thử đơn vị** (drivers 112/usbxfer 1/dvbservice 13), 0 failures/errors; APK debug PASS; lint 0 errors/0 fatal/11 warnings. Lượt Gradle cuối: 2 m 23 s/177 tasks, 41 executed, 136 up-to-date. 10 phương thức kiểm thử đồng thời chạy độc lập 5 lượt, cả 5 PASS, gồm 200 vòng detach/cancel có điều phối bằng latch. Core/tests compile `javac --release 8`, bytecode 52. Xem [TEST-MATRIX.md](TEST-MATRIX.md) và [WORKLOG.md](WORKLOG.md).
- **Các mức sẵn sàng riêng biệt:** permission, connection, claim, firmware-upload event, bridge ready, frontend-initialized event, signal lock và streaming không tự thay thế nhau. Chưa có firmware executor/probe/frontend production; các event là fake/caller input.
- **Không đổi:** protocol bytes, FirmwarePlan, JNI/native, driver cũ, registry/XML, TCP ordinals, toolchain/dependencies; ba repository tham khảo giữ SHA khóa/sạch. Instrumentation **NOT RUN** vì chỉ sửa JVM core chưa được nối vào Android runtime; PASS Phase 2.1 là bằng chứng lịch sử, không phải lần chạy mới này.

**Còn giới hạn:** synchronous control/open/claim không được API bảo đảm có thể cưỡng bức hủy; nếu không trả về thì giữ tài nguyên và chặn mở lại thay vì dùng tài nguyên đã đóng. Cleanup được hoãn có thể chạy trên transaction thread; cancel không được join chính thread đó. Attachment identity/permission receiver thực và việc phân loại timeout từ kết quả âm cần được xác minh ở binding sau. Firmware/USB thật/lock/TS **NOT RUN**.

**Bước trước Phase 2.3:** review [ADR-012](DECISIONS.md), chốt hợp đồng Android identity/permission callback/threading/deadline, rồi xin duyệt phạm vi framework binding với mock/instrumentation không tuner. Không tự khởi động phần cứng, nạp firmware hoặc triển khai production frontend. Working tree gồm 6 file Java mới/sửa và 5 hồ sơ; chưa stage/commit/push.

## Checkpoint tích hợp Phase 2.1 — 2026-11-03 10:46 +07

- Người dùng yêu cầu commit toàn bộ thay đổi Phase 2.1, merge sang `chore/modernize-android-build`, push đúng nhánh đó lên origin và xóa nhánh local `feat/tbs5530-offline-protocol` sau khi xác minh thành công.
- Preflight: feature ở `8588ee7`, target local/origin đều `4cf1b64`; target là tổ tiên nên có thể fast-forward, bao gồm cả checkpoint tài liệu Phase 2.0. Phạm vi 13 file: 8 file Java và 5 hồ sơ; không có outputs/reference/firmware.
- Message commit: `feat: add offline TBS5530 USB protocol foundation`. Hash và kết quả merge/push/delete phải kiểm từ Git sau thao tác; mục này ghi yêu cầu và preflight, không khẳng định push trước khi chạy. Những trạng thái “chưa commit/push” bên dưới là lịch sử trước checkpoint.
- Không chạy lại build/test cho tác vụ Git; bằng chứng gần nhất vẫn là 83 phương thức kiểm thử đơn vị PASS/debug PASS/lint 0 errors, 11 warnings của Phase 2.1. Không thay code hoặc mở phạm vi phần cứng.

## Phase 2.1 — Nền tảng giao thức USB offline, 2026-11-03 10:39 +07

**Đã hoàn tất phạm vi offline, chưa hỗ trợ phần cứng TBS5530.** Nhánh `feat/tbs5530-offline-protocol`, HEAD nền `8588ee7661003052b7d1a0353e23bb3a3dcef8b5`. Nhánh được tạo từ checkpoint Phase 2.0 sạch; modernization giữ nguyên `4cf1b64`. Có tám file Java mới và năm hồ sơ cập nhật, **chưa stage/commit/push**.

- **Đã triển khai:** `Tbs5530ControlIo` là giao diện thay thế được bằng fake; `Tbs5530Bridge` mã hóa EP0/I²C và chọn descriptor Bulk IN 0x82; `Tbs5530FirmwarePlan` kiểm dữ liệu giả và tạo kế hoạch bất biến; `Tbs5530Session` quản lý trạng thái, generation và quyền sở hữu tài nguyên. Không có implementation gọi Android USB/JNI, uploader hoặc registry entry.
- **PASS thực:** 52 phương thức kiểm thử mới (Bridge 17/Firmware 19/Session 16), tổng **83 phương thức** trong drivers 69/usbxfer 1/dvbservice 13, không có failure/error; APK debug và lintDebug **0 errors, 11 warnings**. Lượt Gradle cuối exit 0, 1 m 45 s, 177 tasks: 41 executed/136 up-to-date. Bốn core class compile riêng bằng `javac --release 8`, class version 52; không cần Android SDK/JNI trên classpath. Chi tiết lệnh và kết quả ở [WORKLOG.md](WORKLOG.md), [TEST-MATRIX.md](TEST-MATRIX.md).
- **An toàn đã kiểm bằng fake:** không tự thử lại khi lỗi/timeout/truyền thiếu dữ liệu; session lỗi chặn I/O tiếp, cặp 0x90/0x91 không bị xen ngang, detach trong callback không gửi nửa read còn lại; lỗi firmware tại từng bước không dẫn đến READY; cleanup theo cancel → release → close đúng phần tài nguyên đã sở hữu, chỉ một lần.
- **Nguồn giữ nguyên, working tree sạch khi kiểm lại:** TBS `2ce787de9f6e81b4294692a45de15596ae2e1322`; C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`; Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`. Không sửa references/toolchain/dependencies/driver cũ/API TCP.
- **Giới hạn:** permission và READY là sự kiện do bên gọi cung cấp, chưa có adapter quyền Android hoặc bước thăm dò mức sẵn sàng; cancellation không cưỡng bức ngắt giao dịch đồng bộ đang chạy. Chip/PLP chỉ là dữ liệu kiểm thử chính sách, chưa có lập trình thanh ghi. Firmware thật, reconnect Android, USB transfer/native cancellation, lock và TS đều **NOT RUN**; nguồn gốc/quyền phân phối firmware và bản đồ địa chỉ thật vẫn chưa duyệt.

**Bước an toàn tiếp theo:** review để chốt core offline, sau đó chỉ mở Phase 2.2 riêng cho lifecycle adapter với fake/instrumentation không tuner. Cần thống nhất quyền sở hữu callback đến muộn, cancellation và identity sau reconnect trước khi nối transport Android. Chưa phê duyệt firmware thật, frontend production hay USB hardware. Các snapshot “chưa triển khai/NOT RUN Phase 2.1” bên dưới là lịch sử trước lần thực thi này.

## Checkpoint Phase 2.0 — 2026-11-03 00:58 +07

- Người dùng yêu cầu commit và push năm hồ sơ nghiên cứu/thiết kế cùng kết quả xác minh checkout NTFS đã phục hồi. Nhánh phát hành là `docs/tbs5530-source-architecture`, không merge vào modernization.
- Message: `docs: finalize TBS5530 architecture and source readiness`. Hash của checkpoint tra `git log -1`; trạng thái push phải đối chiếu origin sau thao tác. Những mục “chưa commit” bên dưới là lịch sử trước checkpoint.
- Đã kiểm diff, UTF-8 và liên kết tương đối; không chạy lại build/hardware vì chỉ sửa tài liệu. Không đưa source tham khảo, backup hoặc script tạm đã xóa vào commit. Bước tiếp theo vẫn là phê duyệt Phase 2.1 offline foundation.

## TBS checkout đã phục hồi trên NTFS — 2026-11-03 00:54 +07

**Đã kiểm chứng trực tiếp sau khi người dùng tự chạy script phục hồi:** TBS vẫn ở nhánh `latest`, SHA `2ce787de9f6e81b4294692a45de15596ae2e1322`; Git index tồn tại, working tree sạch, không còn 81.399 staged deletions. Sáu sparse paths giữ nguyên.

- Đủ **1.357 file** theo cone manifest tại HEAD, **0 file thiếu/thừa, 0 hash khác**. Source TBS5530/CXD2878/M88RS6060 và helpers đã có trên đĩa. Không còn trở ngại checkout đã mô tả trong snapshot Phase 2.0 bên dưới.
- Không có index.lock; không lưu override `core.protectNTFS`. Backup thành công còn nguyên tại parent `logs/tbs-sparse-before-20261103-004911-951`, có after.json PASS. Giữ backup; chỉ dọn script tạm theo yêu cầu trước của người dùng sau khi xác minh.
- Working tree C/Go sạch. Agent chỉ đọc kiểm tra reference; việc phục hồi do người dùng thực hiện. Không build kernel hoặc kiểm thử phần cứng, không thay toolchain.
- Main vẫn ở nhánh `docs/tbs5530-source-architecture`, HEAD 4cf1b64, các hồ sơ chưa commit. **Bước tiếp theo:** review và phê duyệt nền tảng offline Phase 2.1; không tự bắt đầu driver production/thao tác ghi USB.


## Phase 2.0 — Mức sẵn sàng của source và kiến trúc, 2026-11-03

**Baseline ổn định:** `chore/modernize-android-build` tại **`4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe`**, đã push ở Phase 1.2. Working tree sạch khi bắt đầu. **Nhánh làm việc:** `docs/tbs5530-source-architecture`, cùng HEAD, chỉ thay năm hồ sơ; chưa stage/commit/push. Không đổi toolchain, code production hoặc repository tham khảo.

### Mức sẵn sàng đã kiểm lại

- **Checkout TBS vẫn BLOCKED:** SHA `2ce787de9f6e81b4294692a45de15596ae2e1322`, nhánh latest. Git index bị thiếu; status có 81.399 staged deletions. Sáu thư mục sparse đã cấu hình nhưng đều có 0 file. `read-tree --dry-run` vẫn trả exit 128 do tên AUX trong phần cây ngoài sparse; index và lock không thay đổi.
- **Đủ đọc nguồn qua Git objects trong phạm vi nghiên cứu:** 785 blobs ở sáu thư mục và **572 blobs ở các thư mục cha đều có local**, tổng cộng 1.357 file dự kiến khi materialize. **572 blobs thiếu là kết quả lịch sử, không còn đúng ở lần kiểm này.** Không tải objects trong phase; chưa biết tác nhân đã bổ sung trước đó. Source board/Sony/Montage, helpers và Linux headers trực tiếp đọc được nhưng chưa có file trên đĩa. Không chứng nhận có thể build toàn kernel.
- **C/Go sạch:** C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`. Không fetch/sync/reset/repair hoặc đổi config. Kế hoạch backup, rollback, tiêu chí phục hồi và so sánh NTFS với ext4 WSL ở RESEARCH; mọi phục hồi cần phê duyệt riêng.

### Kết luận thiết kế mới

- USB control/I2C đi qua **EP0**; MPEG-TS dùng một bulk endpoint **0x82**. Không nhầm với 0x81 hoặc cơ chế bulk-control của CxUsb. Firmware TBS dùng ảnh nhị phân raw, không dùng loader Intel HEX generic.
- **PLP là luồng con của kênh DVB-T2.** Nguồn chính thức ghi lại lựa chọn PLP mỗi lần tune; nhánh retune cùng chuẩn trong Go chỉ đổi bandwidth. Đề xuất mặc định **chọn thủ công PLP 0**, không gọi là tự động. API/TCP cho người dùng chọn PLP để sau.
- **Cách chọn chip khác nhau giữa các nguồn:** nguồn chính thức chọn Ascot3 cho 0x197, Freia cho 0x19c/0x39a; nguồn cộng đồng mở thêm ID. Không mặc định mọi ID khác đều là Freia. Chip chưa biết phải bị từ chối an toàn; chip thực tế vẫn chưa đo.
- **Thử lại/vòng đời:** `Retry.retry(4)` thực có tối đa năm lần thử. `DvbUsbDevice.open/close` là final và tự thử lại, có nguy cơ lặp firmware/reset. Adapter production tương lai dự kiến kế thừa **DvbDevice**, kết hợp permission/I2C/frontend/native TS/demux hiện có, không refactor các driver cũ. Sau lỗi làm trạng thái thiết bị không chắc chắn, không gửi IO tiếp hoặc tự phục hồi phần cứng.
- **Phase 2.1 đề xuất chỉ làm nền tảng offline:** ControlIo/Bridge/FirmwarePlan/Session và scripted fakes với assertions. Chưa đăng ký VID/PID, chưa viết adapter USB thật, uploader hay frontend production. Danh sách file, thứ tự công việc, rủi ro và test cases nằm ở RESEARCH/ADR-010. Toàn bộ tests mới **NOT RUN**.

**Điểm dừng:** hoàn tất nghiên cứu và thiết kế Phase 2.0; chưa phục hồi checkout. Không firmware/I2C/vendor writes/reset/usbipd hoặc test phần cứng. Không build/regression mới vì chỉ sửa tài liệu. **Một bước an toàn kế tiếp:** xin duyệt Phase 2.1 offline foundation theo ADR-010; tách việc phục hồi checkout TBS thành phê duyệt riêng.

Nguồn khóa SHA, sơ đồ, phương án phục hồi, danh sách file và tiêu chí PASS/FAIL: [RESEARCH.md](RESEARCH.md). Quyết định: [DECISIONS.md](DECISIONS.md). Mức sẵn sàng và kiểm thử dự kiến: [TEST-MATRIX.md](TEST-MATRIX.md).

## Checkpoint phát hành Phase 1.2 — 2026-11-02 23:39 +07

- Người dùng yêu cầu add, commit và push toàn bộ thay đổi Phase 1.2 lên `origin/chore/modernize-android-build` (fork `trankien84/AndroidDvbDriver`). Message chọn: `build: upgrade AGP to 9.5.0 and fix toolchain inventory`.
- Phạm vi 15 file: root AGP pins, audit/review/check-env và kiểm thử hồi quy, instructions/skill, README và 5 hồ sơ dự án; không có outputs/cache/SDK/AVD/logs/firmware/reference code. Baseline trước checkpoint là `ace7e9f187d11a685975f707b6743d779fa847d9`.
- Đã kiểm diff và bằng chứng đã lưu: unit 31 methods, 0 fail; instrumentation 2 tests, 0 fail, 0 error; lint 0 errors. Tác vụ Git này không chạy lại build/emulator/hardware. Các snapshot “chưa commit/push” bên dưới là trạng thái trước checkpoint; hash commit chứa mục này tra `git log -1`, trạng thái phát hành phải đối chiếu live origin ref.
- Không merge, force-push, sửa master/upstream hoặc bật hardware gate. Bước tiếp theo là review mốc toolchain đã chốt và lập kế hoạch bao phủ kiểm thử còn thiếu theo yêu cầu mới, không tự port driver.

## Phase 1.2 — AGP 9.5.0 và kiểm kê toolchain, 2026-11-02 22:37–23:18 +07

**Đã hoàn tất trong phạm vi phần mềm, chưa commit/merge/push.** Nhánh vẫn `chore/modernize-android-build`, HEAD `ace7e9f187d11a685975f707b6743d779fa847d9`. Bảo toàn sáu file hướng dẫn/công cụ local người dùng chuẩn bị trước phase; không đổi Git history hoặc reference repositories.

- **Toolchain cuối:** AGP **9.5.0 stable** (Google Current Release + final notes + exact POM xác minh ngày kiểm); Gradle **9.8.2**, JDK **25.0.5.0.3**, compileSdk **37.2**, target **37**, min **21**, NDK **30.0.16248370**, CMake **4.1.2**, Build-Tools **37.0.0**, Java source/target **8** giữ nguyên. Chỉ đổi hai plugin pins trong root build.gradle, không nâng dependencies/SDK khác.
- **PASS thực:** APK debug/release và AAB release; **31 phương thức kiểm thử đơn vị/0 failure** chạy lại với task --rerun (gồm một utility), lint **0 errors/11 warnings**; instrumentation **2 tests/0 fail/0 error** trên AVD Android 17/API 37/x86_64/4 KiB, gồm native-load và registry/XML. Debug/release đủ 4 ABI, ZIP 16 KiB PASS và ELF 64 bit LOAD 0x4000. Không coi đây là runtime 16 KiB/arm64/Android API 21 hoặc USB thật.
- **Audit đã sửa:** đường dẫn AVD home đã xác định được truyền vào process liệt kê emulator rồi khôi phục; bỏ tên trùng có suffix `.avd`; kết quả một AVD thật `TBS5530_Runtime_API37`, emulator 1/metadata 1. Thêm kết quả có cấu trúc qua -PassThru và các JDK có thể dùng, không tự đổi môi trường toàn cục. Parser dọn CMake được giới hạn đúng block. Check-env dùng chung audit; script kiểm thử hồi quy tự chứa đạt PASS, không cài thêm framework test.
- **Kiểm kê sau khi người dùng dọn:** platforms 34/37.2, BT 37, NDK 30, CMake 4.1.2, cmdline 23, ADB 37.0.1, emulator 37.2.13, hai image 34 TV x86/37.0 Google APIs x86_64. Đã bỏ package cũ khỏi snapshot hiện tại nhưng không sửa lịch sử. Phase này không uninstall/install/update SDK hoặc xóa AVD.
- **JAVA_HOME:** Machine đúng, Process agent vẫn dùng đường dẫn cũ; Gradle child nạp Machine và wrapper xác nhận JDK 25. Không thay registry/biến môi trường User/Machine. Script ban đầu bị execution policy chặn; người dùng chỉ định Bypass ở Scope Process, đã thực hiện đúng trong child và khôi phục, không bật bypass mặc định trong script/task.
- **Các lưu ý đã ghi:** lần build đầu Windows không xóa được native output x86; chạy lại đúng task thành công, không xóa cache hoặc dừng process. AVD ban đầu bị timeout sau 4 phút khởi động rồi phản hồi trở lại; Launcher/SystemUI ANR hồi phục sau khi chọn Wait, không phải app ANR. Kiểm tra nhanh cài/mở/no-tuner/insets/Back của app PASS, log đã lọc có 0 fatal/linker/security. Cảnh báo Java 8 obsolete và AGP Configuration.setVisible vẫn còn, không ẩn chỉ để báo cáo xanh.
- **Instructions/tài liệu toolchain:** giữ hướng dẫn tiếng Anh do người dùng chuẩn bị với bốn trạng thái declared/installed/published stable/project verified; README ghi baseline có ngày, không gọi phiên bản là latest mãi mãi. `toolchain-audit` có trên đĩa và đã đọc trực tiếp dù skill dispatcher chưa nhận diện; không tự sửa host/plugin toàn cục để ép nhận.
- **Dọn sau kiểm thử:** AVD đã được tắt đúng serial sau smoke; không wipe dữ liệu. Không thao tác ghi TBS/usbipd/firmware/vendor/I2C, không chạm references. Không port driver.

**Bước an toàn tiếp theo:** review diff Phase 1.2 và commit có chọn lọc nếu được yêu cầu. Vẫn phải theo dõi biến môi trường Process cũ, khóa file native trên Windows và emulator ANR; chỉ mở phạm vi kiểm thử API 21/arm64/16 KiB theo kế hoạch riêng. Dừng Phase 1.2. Chi tiết [ENVIRONMENT.md](ENVIRONMENT.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md), ADR-009 tại [DECISIONS.md](DECISIONS.md).

## Tích hợp Phase 1.1 — 2026-11-02 21:35 +07

- Theo yêu cầu người dùng, đã commit 15 file Phase 1.1: **`b51a77e18e363750692d83cc53974b64718e1f51`**, `fix: stabilize Android runtime after toolchain upgrade`.
- Đã merge **fast-forward** từ `test/android-runtime-smoke` vào **`chore/modernize-android-build`**, push origin fork `trankien84/AndroidDvbDriver`; `git ls-remote` xác nhận target remote ở đúng `b51a77e` sau push.
- Nhánh test local đã xóa an toàn bằng `git branch -d` sau khi xác nhận push. Không có nhánh test tương ứng trên origin tại kiểm tra, nên không có remote branch cần xóa. Không force-push, không đổi master/upstream hoặc repository tham khảo.
- Hồ sơ cập nhật trạng thái tích hợp ở một commit tài liệu tiếp theo; dùng Git log/remote ref để lấy HEAD cuối. Các snapshot “chưa commit/push” bên dưới là lịch sử trước tích hợp, không phải hiện trạng.
- Kiểm chứng trong tác vụ Git: staged diff/whitespace PASS, không có artifacts/secrets theo mẫu rà; báo cáo instrumentation Phase 1.1 vẫn là 2 tests, 0 fail, 0 error và lint 0 error. Không chạy lại emulator/build/hardware trong tác vụ này.

**Bước tiếp theo:** review mốc runtime đã tích hợp và chọn kế hoạch kiểm thử coverage còn thiếu khi được phê duyệt. Không tự chuyển sang port driver hoặc ghi phần cứng.

## Phase 1.1 — Kiểm tra nhanh Android runtime, 2026-11-02 19:31–20:05 +07

**Baseline đã commit/push:** `6da609ba49dd9f1f4d284291918f68a6378972ff` trên `chore/modernize-android-build`, origin fork `trankien84/AndroidDvbDriver`; remote-tracking ref local trùng HEAD và người dùng xác nhận đã push. **Nhánh làm việc hiện tại:** `test/android-runtime-smoke`, cùng HEAD, thay đổi Phase 1.1 **chưa commit/push/merge**. Những đoạn “chưa commit” về migration bên dưới là lịch sử. Emulator thử nghiệm đã được tắt, dữ liệu AVD giữ lại để tái sử dụng.

- **Runtime đã chạy:** AVD riêng `TBS5530_Runtime_API37`, Android 17/API 37, Google APIs x86_64, page size 4096; dùng image 37.0 đã cài, không tải SDK/image. Không phải runtime 37.2 hoặc thiết bị Android thật. Người dùng đã phê duyệt tạo AVD riêng vì ban đầu chưa có AVD/device.
- **PASS:** cài/mở APK, Welcome → License → OK, Diagnostic → Start → `No devices found` → OK, Home/resume, Back và Close/relaunch. PID còn hoạt động, không có app crash/ANR/linker/security failure trong log đã lọc; DvbException được xử lý khi không có tuner là kết quả mong đợi. `dtvdriver://` chooser kết thúc khi không có device, không khởi DvbService sai lifecycle.
- **PASS thực instrumentation:** `:dvbservice:connectedDebugAndroidTest` không filter, XML **2 tests/0 fail/0 error**: registry/XML equivalence và native library smoke. `UsbHiSpeedBulk.IS_PLATFORM_SUPPORTED=true`, gọi trực tiếp `System.loadLibrary("UsbXfer")` thành công trên x86_64; không tạo USB requests hoặc ioctl. Runtime 16 KiB/arm64/USB thật chưa thử.
- **Lỗi giao diện phát hiện và đã sửa:** target 37 vẽ dưới system bars, che hàng tune/status text và đặt Close sát gesture bar. Helper `SystemBarInsets` áp dụng insets systemBars/displayCutout cho 3 Activity, chỉ API 35+; có screenshot + UI bounds trước/sau. Không đổi tuner/service/protocol, không tắt edge-to-edge bằng biện pháp né targetSdk.
- **Vấn đề môi trường:** lần khởi động đầu có ANR `com.android.systemui` (không phải app); chọn Wait qua UI và lớp phủ biến mất. App exit-history chỉ có PACKAGE UPDATED do cài APK đã sửa; chưa chứng minh emulator không có lỗi SystemUI trên mọi lần khởi động.
- **Build/lint/hồi quy:** build APK debug + test PASS; assembleDebug/lintDebug sau khi sửa PASS, lint 0 error/11 warnings; xác minh unit task PASS/32 s, 39 UP-TO-DATE (không gọi là chạy lại 31 test). Toolchain giữ Gradle 9.8.2/AGP 9.4.1/JDK 25/compile 37.2/target 37/min 21/NDK 30/CMake 4.1.2/BT 37.
- **Workspace/instructions:** ngoại lệ `!TBS5530.code-workspace` và file workspace đã có local trước Phase 1.1, giữ nguyên byte/hash; 4 root dùng đường dẫn tương đối đều phân giải đúng, file untracked được phép add (chưa stage). Cập nhật 2 file hướng dẫn bằng tiếng Anh để bỏ snapshot toolchain cũ; giữ chính sách Windows/WSL/usbipd/MCP.
- **Kiểm lại reference chỉ đọc:** SHA TBS 2ce787de9f6e81b4294692a45de15596ae2e1322, C c5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go 11228b05fb1626bf41c4e1879ace10cd0a8d575c không đổi. TBS vẫn thiếu index/81399 D và thiếu 3 file source bắt buộc; C/Go sạch. Không sync/reset/restore hoặc chạy lệnh phần cứng.

**NOT RUN:** DvbService foreground/streaming (không có tuner/fixture nên không vào luồng hợp lệ), USB permission dialog, USB bulk/I2C/firmware/lock/TS; runtime API 21/arm64/16 KiB. Phase 1.1 không cho phép kết luận TBS5530 hoạt động.

**Một bước an toàn tiếp theo:** review/commit có chọn lọc diff Phase 1.1 khi người dùng cho phép; nếu cần tăng phạm vi kiểm thử runtime, lên kế hoạch test API 21 hoặc arm64/16 KiB riêng, không tải image/thao tác hardware ngoài phê duyệt. **Dừng Phase 1.1, không tự port driver.** Kết quả/lệnh/hash ở [WORKLOG.md](WORKLOG.md), [TEST-MATRIX.md](TEST-MATRIX.md), môi trường ở [ENVIRONMENT.md](ENVIRONMENT.md), ADR-008 trong [DECISIONS.md](DECISIONS.md).

## Checkpoint Git — 2026-11-02 18:48 +07

- Người dùng yêu cầu tạo commit cho các thay đổi nâng build và bộ hồ sơ/công cụ TBS5530. Message chọn: `chore: prepare TBS5530 development with JDK 25`.
- Nhánh `chore/modernize-android-build`; origin hiện trỏ fork `trankien84/AndroidDvbDriver` qua SSH, upstream giữ `signalwareltd/AndroidDvbDriver`. Khác origin lúc audit đầu vì cấu hình đã đổi ngoài tác vụ này; không tự sửa remotes.
- Kiểm trước commit: lệnh build unit/debug exit 0/59 s, 107 tasks (8 executed/99 UP-TO-DATE); unit tasks dùng kết quả còn hợp lệ, không gọi là chạy lại toàn bộ phương thức kiểm thử. Báo cáo 31 methods, 0 fail và lint 0 errors/11 warnings từ migration vẫn là bằng chứng gần nhất.
- Chỉ đóng gói source/build configs/tests, quy tắc ignore firmware/captures, hướng dẫn/skills, tasks, scripts và 6 hồ sơ dự án; không có binaries build/caches/firmware/TS/logs hay reference repos. Workspace `.code-workspace` vẫn bị ignore, không force-add.
- Không push trong tác vụ commit; dùng `git log -1` để lấy hash thực tế sau khi hoàn tất. Các snapshot “chưa commit” bên dưới là lịch sử trước checkpoint. Hành động kế tiếp vẫn kiểm thử Android runtime không tuner; không tự thay hardware.

## Hiện trạng mới nhất — 2026-11-02, migration JDK 25 đã build/test

**Đã nâng build theo phê duyệt mới của người dùng, không còn dừng ở Phase 0.** Nhánh `chore/modernize-android-build`, HEAD nền `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`; **chưa commit/push**. Các snapshot bên dưới là lịch sử.

- **Toolchain:** Gradle 9.8.2 (tạo lại wrapper scripts/JAR, có distribution checksum), AGP 9.4.1, JDK 25.0.5.0.3. compileSdk 37.2/target 37, **giữ minSdk 21**, giữ Java source/target 8. BuildTools 37.0.0/NDK 30.0.16248370/CMake 4.1.2 đã cài đúng 4 package trong SDK Windows duy nhất; không tải images/firmware/reference objects.
- **Latest có điều kiện:** AppCompat 1.7.1/Material 1.13.0 giữ API 21; 1.8.0/1.14.0 đòi 23 nên không chọn. Test-ext 1.3.0/Espresso 3.7.0, Mockito 5.24.0; bỏ PowerMock/Mockito 1. Không sửa runtime/public USB/protocol code; thêm localbroadcastmanager 1.1.0 trực tiếp, chỉnh DSL/native config và kiểm thử để tương thích Windows/Mockito mới.
- **PASS thực:** assemble debug; **31 phương thức kiểm thử đơn vị, 0 fail** (gồm 1 utility không có assertion hồi quy); instrumentation APK **compile**, chưa chạy Android; APK release unsigned và AAB; lint debug **0 errors/11 warnings**; 4 native ABI debug/release; ZIP 16 KiB debug/release và ELF 64 bit LOAD 0x4000. Không coi ELF 32 bit 0x1000 là thất bại của gate 16 KiB dành cho 64 bit.
- **Lỗi đã xử lý:** thiếu NDK → cài phiên bản đã khai báo; thiếu LocalBroadcastManager bắc cầu → khai báo dependency trực tiếp; 2 lỗi unit assertion/mock → chỉ sửa test. Native release từng gặp lỗi Windows không xóa được output khi dùng nhiều worker; chạy ABI riêng rồi xác minh tuần tự `--max-workers=1` PASS. Không tắt lint/test hoặc xóa output để né lỗi. Nếu lỗi lock lặp lại, dùng bước tuần tự đã ghi, không dừng process khác.
- **Còn cảnh báo/NOT RUN:** javac 25 cảnh báo source/target 8 obsolete; AGP có cảnh báo bỏ dần API Gradle nội bộ; 11 lint warnings (2 dependency pin cố ý, UI/resources). Không tăng minSDK/ẩn cảnh báo chỉ để báo cáo xanh. Android API 21/API 37 UI/permission/service, native-load/runtime 16 KiB, USB Host/TBS firmware/I2C/lock/TS thật **NOT RUN**.
- **References theo lựa chọn mới:** giữ read-only, không chạy Apply script, không tải 572 blobs ngoài nhu cầu. SHA của 3 reference không đổi, C/Go sạch, TBS vẫn thiếu index/81399 D. Không phải trở ngại cho Android build đã PASS nhưng chưa có checkout TBS hoàn chỉnh. Không triển khai driver TBS5530.

### Một bước an toàn tiếp theo

Review diff migration và chọn môi trường Android để kiểm thử UI/permission/service/native-load **không gắn tuner**, sau khi được duyệt khởi chạy/cài APK. Không tự khởi động emulator hoặc ghi phần cứng. Hướng dẫn build ở [README](../../README.md); lệnh/kết quả/hash chi tiết ở [WORKLOG.md](WORKLOG.md), [TEST-MATRIX.md](TEST-MATRIX.md), quyết định ADR-007 tại [DECISIONS.md](DECISIONS.md).

## Cập nhật mới nhất — 2026-11-02 12:52 +07

- **JAVA_HOME Machine đã được người dùng sửa đúng** sang `C:\Program Files\Java\latest\jdk-25`; executable trả 25.0.5.0.3. Process agent còn giá trị cũ; child đọc lại Machine JAVA_HOME và chạy Java thành công. Không sửa môi trường toàn cục/khởi động lại IDE. Gradle 8.7/JDK 25 vẫn không tương thích, build NOT RUN.
- **Kế hoạch TBS đã điều tra bằng dry-run:** index vẫn thiếu/81.399 D; 785 blob trong 6 thư mục có sẵn nhưng 572 ancestor blob của cone sparse còn thiếu. Không thể mặc định khôi phục cùng cone hoàn toàn offline.
- Dry-run read-tree mặc định tái hiện `invalid path .../i2c/aux.c` trên Windows; workaround `-c core.protectNTFS=false` **chỉ trong dry-run** trả 0, không lưu config và không tạo index. Chưa phục hồi source.
- **Người dùng đã chọn nhận bộ lệnh tự khôi phục.** Đã bàn giao script ngoài Git `Restore-TbsSparseCheckout-Reviewed.ps1`: preview mặc định chạy PASS (1357 files/572 missing); parser 0 errors; **Apply/backup/fetch/restore NOT RUN**. Có xác nhận trước Apply, backup toàn bộ .git, khóa SHA và workaround chỉ áp dụng cho từng lệnh. FETCH_HEAD có thay đổi từ tác nhân chưa rõ trong môi trường dùng chung: dừng tác vụ Git khác trước khi chạy. Tiếp theo: người dùng đọc script rồi tự chạy và gửi kết quả đã lược thông tin cá nhân; không tự sửa reference/build/hardware. Chi tiết ở đầu RESEARCH.

## Hiện trạng đã kiểm chứng — 2026-11-02 (Phase 0)

**Đã hoàn tất kiểm kê/nghiên cứu source Phase 0; chỉ sửa hồ sơ. Dừng trước migration, driver, emulator và kiểm thử phần cứng.** Snapshot 2026-11-01 bên dưới là lịch sử, không phải kết quả kiểm tra mới.

- **Main:** `master`, `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`; 18 file untracked có trước phiên, gồm hướng dẫn/hồ sơ/scripts. Không commit/push/stage/đổi nhánh. Workspace có 4 root phân giải đúng nhưng bị `.gitignore` loại (`*.code-workspace`).
- **References khóa SHA:** TBS `2ce787de9f6e81b4294692a45de15596ae2e1322`; C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`; Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`.
- **TBS checkout BLOCKED:** sparse list đủ 6 nhưng index không tồn tại, `ls-files=0`, 81.399 staged deletions và source cần thiết không có trên đĩa. Blob HEAD local đọc được; chưa sửa references. C/Go sạch. Không dùng sync script kể cả CheckOnly (vẫn fetch). Giới hạn nguy cơ lazy-fetch từ lần diff đầu và FETCH_HEAD mtime ghi minh bạch ở RESEARCH/WORKLOG; không cam kết metadata hoàn toàn không bị tác động.
- **Môi trường:** Windows 10.0.19045/PS 7.6.9; Java 25.0.5.0.3 trên PATH chạy được nhưng JAVA_HOME trỏ tới thư mục **không tồn tại**. SDK D:\Android\Sdk có package đúng kiểm kê; process thiếu ANDROID_HOME/ANDROID_AVD_HOME dù User đã đặt, CLI mặc định nhìn đường dẫn khác. Không tự sửa môi trường.
- **Build:** source thực AGP 8.5.2/Gradle 8.7, compile 34/**target 36**/min 21, bytecode Java 8. JDK 25 không hỗ trợ chạy wrapper 8.7. **Build/tests NOT RUN**, không phải đã build thất bại. Đề xuất baseline JDK 17 trước rồi migration nhỏ; nguồn stable/compatibility và lệnh tương lai ở RESEARCH.
- **USB hiện tại:** usbipd 5.3.0-54; TBS 734c:5530, BUSID đọc trực tiếp là 6-2, **Shared**, quyền phía Windows; Ubuntu 22.04 Running/WSL2. Không Attached, không chạy lsusb/PyUSB mới, không ghi firmware/reset/I2C/LNB/quyền sở hữu. USB endpoints/claim lịch sử không chứng minh firmware/tuner hoạt động.
- **Copilot/MCP:** đọc đủ hướng dẫn/skills; 2 project skills tải thành công. Các lệnh khám phá/đọc qua Proxy hoạt động; ADB MCP không thấy AVD, GitNexus không có index dự án, truy vấn AgentMemory không có kết quả khớp. Không sửa config/index/cài đặt toàn cục. Không xác nhận qua API rằng mọi scoped instruction đều được tự động ghép; đã chủ động đọc.
- **Kiến trúc đề xuất:** tái sử dụng registry/USB Host/native bulk/I2C/frontend/demux/service; TBS kế thừa DvbUsbDevice trực tiếp, dùng EP0 vendor control, một stream **0x82**, chọn Sony frontend theo chip-ID Ascot3/Freia; ưu tiên DVB-T2. Cxd2841er là mẫu frontend, **không** phải CXD2878 được đổi tên; CxUsb generic control dùng bulk, **không** dùng được cho TBS EP0. API hiện chưa có PLP, enum chỉ T/T2/C; truyền hình vệ tinh cần mở rộng giao thức sau. Không mặc định khởi tạo Montage/stream song song. Hash/quyền phân phối firmware chưa được xác minh. Chi tiết file/hàm/SHA ở RESEARCH.

### Một hành động an toàn kế tiếp

**Soạn và xin duyệt kế hoạch khôi phục index/sparse checkout TBS, kèm bảo toàn metadata/trạng thái và tiêu chí kiểm lại; chưa chạy lệnh khôi phục.** Không tự chuyển Phase 1. Sau khi giải quyết mức sẵn sàng của repository mới xin phạm vi build baseline ở JDK 17/SDK đúng.

### Hồ sơ để tiếp tục

[ENVIRONMENT.md](ENVIRONMENT.md): phép dò/phiên bản thực; [RESEARCH.md](RESEARCH.md): nguồn khóa SHA, kiến trúc, migration plan; [DECISIONS.md](DECISIONS.md): ADR-004..006 và trạng thái đề xuất; [TEST-MATRIX.md](TEST-MATRIX.md): PASS/FAIL/BLOCKED/NOT RUN; [WORKLOG.md](WORKLOG.md): lệnh, ngoại lệ, phạm vi thay đổi.

---

## Snapshot lịch sử — 2026-11-01 (giữ nguyên bằng chứng)


**Ngày snapshot:** 2026-11-01 (được lập từ quan sát Windows/WSL do người dùng cung cấp; không chạy lệnh mới để xác minh bộ tài liệu này).

**Phase hiện tại:** Phase 0 — môi trường làm việc, hướng dẫn và baseline build. Bước chẩn đoán nhận diện/claim USB đã đạt trên WSL2.

**Mục tiêu chung:** hỗ trợ TBS5530 như thiết bị USB trong AndroidDvbDriver ở không gian người dùng, không cần root Android; ưu tiên DVB-T2, có thể mở rộng DVB-C/S/S2X sau.

## Các quan sát đã xác nhận
- Windows nhận diện `TBS 5530 DVB BDA` (VID:PID `734c:5530`).
- usbipd chuyển thiết bị sang Ubuntu 22.04 WSL2; `lsusb` và PyUSB thấy tuner ở tốc độ 480 Mbps.
- PyUSB claim và release thành công USB interface 0.
- Thiết bị báo USB bulk IN endpoints `0x82` và `0x81`; cả hai có max packet size 512.
- SDK/NDK/CMake/ADB Windows được cài tại `D:\Android\Sdk`; chi tiết ở `ENVIRONMENT.md`.

## Chưa được xác minh
- Clone/fork thực trên đĩa và đường dẫn workspace; baseline build Gradle; cài APK Android trên thiết bị đích.
- Tình trạng sẵn có, checksum và việc nạp firmware bridge TBS5530 lên phần cứng.
- Thao tác USB control/I2C, khởi tạo/lock tuner DVB-T2, thu MPEG-TS và tính liên tục của dữ liệu.
- Khả năng tương thích của kế hoạch nâng AGP/Gradle với JDK 25 đã cài và mã native của dự án.

## Hành động an toàn ngay tiếp theo
1. Xác nhận checkout chính tồn tại và `git status` sạch.
2. Chạy `scripts/check-env.ps1` trên Windows; ghi nhận `android -V` thực và phiên bản Gradle wrapper/JVM.
3. Chỉ tạo nhánh `chore/modernize-android-build` sau khi ghi lại baseline; nâng từng bước và xác minh build; **không ghi phần cứng**.

## Trở ngại/rủi ro hiện tại
- Không thể mặc định Gradle 8.7 cũ của upstream chạy được trên JDK 25 đã cài; chỉ nâng phiên bản phù hợp hoặc dùng JDK khác theo kế hoạch đã review.
- Screenshot toolchain Android không chứng minh đã build APK thành công trên máy local.
- USB hoạt động trên WSL2 không chứng minh firmware/tuner được hỗ trợ trong không gian người dùng Android.

## Liên kết làm việc
- Môi trường dự án: `ENVIRONMENT.md`
- Ghi chép nghiên cứu: `RESEARCH.md`
- Nhật ký tiến độ: `WORKLOG.md`
- Quyết định: `DECISIONS.md`
- Bằng chứng kiểm thử: `TEST-MATRIX.md`
