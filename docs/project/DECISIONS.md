# Architecture decision record (ADR)

## ADR-024 — Phase 3.0: owner operational mới, phê duyệt từng chương trình và gate RF IN trước tune

**Date:** 2026-11-06

**Status:** Implemented and verified có phạm vi: JVM 343/instrumentation cách ly 64 methods và debug/release/lint PASS; upload mới 132 full-length, observation operational riêng, chip-ID 0x197 và frontend init thực đạt. Tune/lock/TS NOT RUN do PLP chưa xác minh, không phải thiếu RF IN. Baseline `722c4916a83074e8cc5b5c6aeeaee9d2f1190785`, không commit hoặc thay toolchain.

### Bổ sung từ ảnh Công báo — 2026-11-06 17:57 +07

Điều 3 khoản 1 trong ảnh xác nhận K36 có trung tâm 594 MHz, giới hạn 590–598 MHz, rộng 8 MHz; chọn bộ ứng viên `594000000 Hz` / `8000000 Hz` có cơ sở tài liệu. Điều 3 khoản 2 điểm d phù hợp quy hoạch khu vực Nam Trung Bộ và Tây Nguyên. Không có PLP trong bốn ảnh; giữ gate manual PLP đã xác minh hoặc cơ chế auto đúng chip được kiểm trước khi tune. Không lấy nguồn quy hoạch thay bằng chứng on-air, không mở rộng scope hardware hoặc scan K37.

### Bổ sung sau live gates và đối chiếu K36

Probe riêng C đạt 3 OUT/2 IN, ID 0x197; init riêng D đạt 65 OUT/14 IN, tuner E-family check đạt. State đạt FRONTEND_INITIALIZED/READY_FOR_TUNE, sau đó người dùng xác nhận coax thụ động an toàn cắm đúng RF IN và đường anten từng thu tốt. Không giữ trạng thái “chưa cắm anten” sau xác nhận này.

Ứng viên K36 SDTV/Vũng Chua, 594 MHz/bandwidth 8 MHz, chưa có PLP. Official `cxd2878_set_dvbt2:2468–2475` và C cùng path chỉ ghi bank 23/AF = stream_id & 0xFF, AD = 1 cho manual PLP; Go `sony/terrestrial.go:144–162` cũng chỉ hiện thực manual. Không biến NO_STREAM_ID_FILTER thành PLP 255, không mặc định PLP 0, không dùng auto của chip khác hoặc ATSC3 để mở gate DVB-T2. Giữ từ chối AUTO_PLP, không phát tune/status/stream/Bulk. Đóng owner và chờ thông số được xác minh hoặc tài liệu Sony đúng chip; không giữ phiên sáng vô hạn. Các phần context/checkpoint trước live bên dưới được giữ để truy vết.

**Context:** Sau khi người dùng rút tuner ở cuối Phase 2.9, firmware RAM, descriptor và permission cũ không còn là bằng chứng hiện hành. Lượt descriptor-only được duyệt mới trong phiên này đọc 189 byte, hash e288…52de, `FX2_DEFAULT`, không endpoint 0x82 ở alt 0; open/read/close 1/1/1, claim/release/transfer 0/0/0, 24 ms. Một approval upload mới sau đó bị tiêu thụ ở host nhưng dừng trước native vì quỹ khởi động còn dưới 240000 ms; không sửa terminal hoặc tái sử dụng GUID đó.

**Decision — operational ownership:** Tái sử dụng `Tbs5530UsbBinding`/`Tbs5530LifecycleCoordinator`/`Tbs5530Session` và worker hiện hữu. Đường debug opt-in kiểm raw mới rồi đối chiếu cây Android trước claim(false), đúng một TBS5530 và hai endpoint IN/BULK 0x82/0x81 tại config 1/interface 0/alt 0. Không hardcode chiều dài 50 byte làm điều kiện duy nhất, không coi bInterval là polling gate cho bulk, không suy USB speed. Operational owner cấm A0 và không gọi bootstrap; default/unknown phải đóng unclaimed. Nhận detach thì invalidate ngay cả khi enumeration đã có path/id giống hệt; cleanup vật lý vẫn đợi giao dịch đồng bộ trả về. Đây là lựa chọn fail-closed, có thể dừng thận trọng khi event đến muộn thay vì giữ owner có nguy cơ stale.

**Decision — bridge/frontend:** Frontend chỉ tạo chương trình bất biến có transcript/SHA, địa chỉ/bank/register, số OUT/IN, delay và công thức byte phụ thuộc read. Probe đầu chỉ SLV-X 0x66, bank 0, high ID 0xFB và low ID 0xFD: 3 OUT/2 IN, trần 10000 ms theo timeout 2000 ms/call, không scan hoặc EEPROM. Gate debug kiểm lại từng USB payload và consume approval bền vững trước OUT; full-length nhưng ID không hợp lệ vẫn dừng. Chỉ sau ID hợp lệ mới đánh dấu bridge ready, sau init mới đánh dấu frontend initialized. Phê duyệt control dùng namespace Phase 3.0 riêng và heartbeat 2000 ms, không chạm permit/consumed/ledger firmware Phase 2.9.

**Decision — phạm vi port:** DVB-T2/CXD6802 0x197 với ASCOT3I E-family, board xtal 24 MHz; frequency phải nhập rõ theo Hz, bandwidth thuộc bảng đã port, manual PLP 0..255. Freia 0x19c/0x39a, tuner variant chưa port, auto PLP và metrics chưa có nguồn thực thi đầy đủ đều trả UNSUPPORTED/NOT_AVAILABLE, không giả thành success. Không bật DVB-S/LNB/tone hoặc nguồn anten.

**Decision — RF và MPEG-TS:** Chuẩn bị frontend không đòi anten. Sau init, dừng `AWAITING_RF_IN_CONNECTION`; tune/status thật cần người dùng xác nhận coax DVB-T2 đúng RF IN, không LNB IN, cùng frequency/bandwidth/PLP và approval write riêng. TS cần runtime lock và scope riêng cho 0x8A [0x0A,0] + Bulk IN 0x82 có trần byte/read/time; UI prototype chọn 64 KiB/16 reads/5 giây, timeout read 500 ms. Parser tìm ít nhất ba packet 188 byte, kiểm sync/TEI/adaptation/continuity và carry; không lưu raw TS. Không có dữ liệu không được tự chẩn đoán là frontend hoặc RF failure.

**Decision — quỹ upload:** Giữ ACK 1500/heartbeat 2000/USB 2000/TTL 300000/start reserve 240000 ms. Nút xác nhận nay kiểm quỹ còn lại trước khi cho bắt đầu; không nới TTL hoặc âm thầm tạo challenge/GUID mới. Tính từ lúc chọn firmware, cửa sổ bắt đầu tối đa khoảng 60 giây và còn phải trừ thời gian preflight. Host reservation đã consumed vẫn là lịch sử dù native bằng 0; một transaction khác cần approval mới độc lập.

**Verification:** 89 focused JUnit PASS trước tích hợp; full JVM/build debug/release/lint đã có lượt PASS trên mã trung gian; 32 fake USB instrumentation và 24 approval deterministic PASS trên OnePlus/API30, package cách ly. Các gate cuối và hardware mới được ghi riêng trong [TEST-MATRIX.md](TEST-MATRIX.md), không lấy các lượt trung gian này thay lần kiểm cuối.

**Alternatives:** Reupload theo cached VID/PID, dùng endpoint 0x81 như TS, claim force=true, gọi READY từ descriptor, scan địa chỉ I²C, auto PLP=0, tune khi chưa RF, hoặc tăng timeout để che thời gian chờ UI: không chọn.

**Limits / reopen trigger:** Chưa có chip-ID/init/lock/TS thực; synchronous USB không có bảo đảm cancel vật lý. Khi có identity variant khác hoặc nguồn board mới, cần đối chiếu nguồn và tests trước khi mở rộng plan; không repair bằng ghi thanh ghi thử. Giấy phép nguồn GPL được giữ; firmware/TS/capture không đưa vào Git/APK.

## ADR-023 — Phase 2.9: phân loại bulk-IN theo thuộc tính có nghĩa, giữ raw observation riêng

**Date:** 2026-11-06

**Status:** Implemented and verified có phạm vi: full JVM 259/build debug+release/lint PASS; app 47/library 37 instrumentation rerun PASS và user-approved raw observation cuối OPERATIONAL_CANDIDATE. Không cấp firmware write/retry/recovery từ thay đổi này.

**Context — runtime:** Transaction độc lập thứ ba, sau user approval/physical/UI, có 132 native full-length/8192 image bytes/8196 payload, hai release full-length và cleanup không pending. Attachment event sát release khiến observation tự động chưa mở được lease mới; user duyệt riêng permission +raw read một lần. Raw hoàn chỉnh 50 byte/hash 22e2…add5 có config 1/interface 0/alt 0, bulk IN 0x82 và 0x81/512 byte; 0x82 bInterval=1, 0x81=0. Classifier debug cũ gán UNKNOWN chỉ vì đòi interval=0 cho bulk IN.

**Source:** Microsoft USB_ENDPOINT_DESCRIPTOR documentation, section bInterval, mô tả polling interval cho interrupt/isochronous, các kiểu khác bỏ qua; trường này do firmware mô tả, không driver sửa. URL https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/usbspec/ns-usbspec-_usb_endpoint_descriptor (đọc 2026-11-06). Official TBS SHA `2ce787de9f6e81b4294692a45de15596ae2e1322`, `tbs5530.c` stream properties dùng USB_BULK endpoint 0x82; community C SHA `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, `tbs5530.c` dùng usb_rcvbulkpipe 0x82; Go SHA `11228b05fb1626bf41c4e1879ace10cd0a8d575c`, `tbs5530.go` mô tả shared bulk 0x82. Đây chỉ đối chiếu loại/address, không chứng minh streaming thật hoặc bInterval=0 là gate.

**Decision:** Bỏ đúng điều kiện interval=0 ở nhánh debug OPERATIONAL_CANDIDATE của bulk IN; vẫn kiểm complete tree/no issues/config 1/interface 0/alt 0/address 0x82/direction IN/attributes bulk/max packet 64 hoặc 512. Parser giữ interval thật và bootstrap-default fingerprint vẫn strict, không sửa descriptor/protocol/transport/registry/main drivers hoặc tự gọi READY. Tests quét interval 0..255 và negative type/address/packet/alternate; repro FAIL trước sửa, focused PASS sau sửa.

**Raw evidence:** Standalone read-only Android port trước đây chỉ trả bytes cho parser/report không giữ binary. Thêm checked-durable file riêng app-private theo observation ID, bounded MAX_BYTES; report vẫn sanitized, không binary/string identifiers vào Git/APK/JSON. Test inject storage không USB. Muốn thêm một read thực để lưu binary/đối chiếu classifier mới phải xin approval read-only riêng; không dùng upload approval để đọc lặp hoặc bootstrap lại.

**Verified observation:** Sau approval riêng bổ sung, APK observation mới update tại chỗ giữ cả legacy và current consumed stores; raw đọc đúng một lần, complete 50 byte/hash khớp observation trước và binary thực đã lưu private/export hash-verified, không reconstruction. Candidate OPERATIONAL_CANDIDATE được quan sát lúc 00:32:55 +07 ngày 2026-11-06, 1 open/read/close, 0 claim/release/transfer, pending/cleanup false. Không gửi A0 sau upload để đạt kết quả này.

**Limits:** 0x82 và full upload chỉ cho OPERATIONAL_CANDIDATE; không READY/frontend/I²C/tune/PLP/TS, không suy silicon revision/EEPROM/speed. Nếu observation mới không đạt thì giữ UPLOAD_COMPLETE_OBSERVATION_PENDING/UNCERTAIN, không retry firmware hoặc recovery tự động.

## ADR-022 — Phase 2.9: host reservation tiêu thụ dù native chưa bắt đầu, không retry cùng approval

### Triển khai no-clamp đã kiểm mock, đang chờ live

Watch/Test mới lấy POLL clock sau app-file I/O và giữ raw sample cho host_seen. PUT chỉ chờ tối đa hai lần sleep 10 ms để đọc tick thật hoặc fail alignment; không gán minimum/created vào timestamp. Chạm ACK deadline trong lúc chờ phải ACK_TIMEOUT trước ghi permit, không gia hạn nonce/deadline. Mock 61 checks gồm sample-after-read/no-clamp/stalled-clock/cross-deadline PASS, không ADB/USB; chưa gọi là live PASS cho tới ba lượt kiểm đủ. Marker sau rename vẫn không chứng minh fsync của host permit.

### Bổ sung no-clamp timestamp trước hardware khác

Private UID parity đã PASS 3 lượt 132 bước nhưng source vẫn lấy POLL clock trước file read, host_seen=max với request và PUT minimum clamp dưới 10 ms. Không dùng tolerance đó làm timestamp giả. Chọn lấy sample thực sau file I/O, nếu cần chờ tick clock thực có trần trong cùng deadline hoặc fail alignment; không grace/đổi deadline hay clamp về created/request. Các lượt có clamp giữ làm checkpoint có phạm vi, cần rerun mã no-clamp trước approval khác.

### Bổ sung namespace sau probe external access

Live fake với run-as file I/O không đọc được app-external challenge trong `/sdcard/Android/data/...phase29offline` dù app Java tạo được và shell đọc được. Dùng instrumentation channel app-private đúng `/data/user/0/info.martinmarinov.dvbdriver.phase29offline/files/...`, kiểm canonical path với Context.getFilesDir; wrapper chỉ chấp nhận đúng prefix test này, không namespace/package tùy ý hoặc production files trong BenchmarkOnly. Không dùng chmod, MANAGE_EXTERNAL_STORAGE, reset app data hoặc fallback về shell-only để làm benchmark xanh giả. Implementation/live gates đang chạy; chưa có hardware permission mới.

### Bổ sung clock/UID parity sau probe runtime

Probe cùng OnePlus/boot: shell đọc `/proc/uptime` được, run-as app bị Permission denied. Clock read lỗi không được clamp về created hoặc giả thành host còn sống; fail-closed DEVICE_CLOCK_UNAVAILABLE. Chọn tách controller clock/boot ở shell UID, chỉ GET/PUT app-private files qua run-as, không thay quyền/root/SELinux hoặc dùng host wall clock. Test no-USB phải mô phỏng đúng privilege split và legacy files chờ UI vượt heartbeat; mock/benchmark chỉ shell UID không đủ làm gate production. Implementation/live validation đang thực hiện, không cấp hardware approval từ quyết định này.

### Bổ sung sau approval độc lập thứ hai

Lần hai đã có initial permit v2 nhưng heartbeat không tiến trong khi chờ UI với legacy request tồn dư; total 300 giây, ACK 0/native 0, chưa có Android per-attempt store. Giữ cả hai host reservations consumed, không reuse và không đánh đồng host consumed với native đã xảy ra. Cần sửa liveness loop và đo no-USB có legacy files +UI wait 3500 ms; không tăng heartbeat/deadline để che việc không publish pulse. Bất kỳ lượt thực khác vẫn cần approval/GUID/physical/UI mới, không tự rearm chỉ vì tuner chưa reset.

### Bổ sung fail-closed ở final release

Source/JVM tái hiện thêm trường hợp write 132 full-length có detach hợp lệ nhưng native result audit lỗi được giữ dưới suppressed exception; nhánh `expectedFinalReleaseDetach` cũ chỉ nhìn cause USB_DETACHED và có thể cho tiếp observation. Test mới FAIL trước sửa; runner nay từ chối exception-chain có AUDIT_FAILED dù suppressed, giữ native 132 full-length và stop provenance/UNCERTAIN, không quan sát tiếp hoặc corrective write. Đây là fault injection phần mềm, không mô tả transaction vừa được duyệt (transaction đó chưa có native). Không thay plan/deadline hoặc quyền retry.

**Date:** 2026-11-05

**Status:** Runtime observed; một approval mới đã tiêu thụ ở host và thất bại trước native vì legacy-frame còn lại, cần fix offline rồi phê duyệt khác nếu thử tiếp. Không tạo phase mới.

**Context:** User đã approve_one/physical conditions và UI-ready riêng; fresh preflight/SAF hash đạt. Host append/fsync NEW_APPROVAL_CONSUMED trước grant, sau đó StrictMode gặp legacy request chỉ có challenge/ordinal và dừng trước initial permit. ACK/observation 0/0; report vẫn RAW READ_AND_CLOSED, không per-attempt app prefs/native audit hiện tại; không native USB mới.

**Decision:** Không dùng lại GUID hoặc approval vừa tiêu thụ chỉ vì CPU chưa reset. Giữ nguyên ledger/failure và legacy consumed, đóng UI/revoke challenge, không xóa request/permit cũ để làm xanh preflight. Fix helper bằng phân biệt stale binding và malformed-current frame, kiểm production-start fixture chứa legacy leftovers; không nới nonce/ordinal/hash/epoch/deadline hoặc auto-reconnect/rearm.

**Boundary:** Ledger host consumed không đồng nghĩa native đã được gọi; ngược lại thiếu native audit/result trong trường hợp này phải kết hợp report/permit/store và source để kết luận dừng trước native, không đổi attempt 04-11 thành PASS. Hardware transaction tiếp theo cần approval/GUID/fresh preflight/UI riêng mới, không retry bằng các flags/ID cũ.

## ADR-021 — Phase 2.9: kiểm được lỗi lưu audit, deadline tại dispatch và ngân sách trước reset

**Date:** 2026-11-05

**Status:** Triển khai trong working tree từ `92a25b4962ff487c169e67649f454e277bcc60e6`; không phê duyệt hoặc thực hiện hardware attempt mới. Các phép đo và giới hạn kiểm chứng tiếp theo được ghi tại [RESEARCH.md](RESEARCH.md) và [TEST-MATRIX.md](TEST-MATRIX.md).

**Context — SOURCE-CONFIRMED:** `AtomicFile.finishWrite()` của Android 11 gọi `FileUtils.sync`, close và rename nhưng chỉ ghi log khi các bước đó lỗi. Vì thế một callback chỉ bắt exception quanh `finishWrite()` chưa đủ phát hiện mọi lỗi lưu bền vững. Nguồn đối chiếu: [AOSP Android 11 AtomicFile.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/core/java/android/util/AtomicFile.java), đọc ngày 2026-11-05; đây là nhánh nguồn công khai, không phải phép đo lỗi filesystem thật trên OnePlus.

**Decision — durability:** Giữ toàn bộ sự kiện và cấu trúc snapshot audit cũ, vẫn serialize đồng bộ và dùng AtomicFile tại từng intent/result. Thêm `Tbs5530DurableFile`: gọi `FileDescriptor.sync()` có exception trước commit và đọc lại để kiểm nội dung sau commit. Dùng chung cho native audit, request/observation/challenge do Android xuất và diagnostic report. Nếu sync hoặc kiểm commit lỗi thì đóng write gate; kết quả native đã về vẫn giữ nguyên và lỗi audit không ghi đè lỗi native âm/ngắn gốc. Không dùng writer bất đồng bộ, không bỏ intent trước native và không giảm stop provenance.

**Evidence — OFFLINE-TESTED/RUNTIME-OBSERVED có phạm vi:** Fault injection trong package `.phase29offline` kiểm sync lỗi, commit im lặng không hoàn tất, lỗi audit trước/sau native giả và mất device trong kiểm quyền cuối. Audit-cost không USB trên OnePlus/API30: mỗi mode 264 checkpoints, 6.711.056 byte serialize; cách cũ tổng 1.604,809 ms, cách kiểm commit tổng 2.077,883 ms. Đây là chi phí tăng để phát hiện lỗi, không phải speedup; chưa có bằng chứng audit là nút thắt chính nên không đổi sang journal tăng dần.

**Decision — final boundary:** Sau khi ACK được kiểm và trước khi gọi USB, kiểm lại device/permission, attachment, write gate rồi `requireDispatch(ordinal)` kiểm đồng hồ monotonic và dùng nonce đúng một lần, không thực hiện I/O. ACK hết hạn tại hoặc sau 1.500 ms vẫn bị từ chối; không có grace period. Điều này thu hẹp khe trễ sau `requireNative`, không thể xóa hoàn toàn race giữa kiểm cuối và API đồng bộ.

**Decision — session budget:** Giữ ACK 1.500 ms, heartbeat 2.000 ms, USB timeout 2.000 ms/call, TTL 300.000 ms và benchmark bound 240.000 ms. Thêm điều kiện còn ít nhất 240.000 ms theo min của hạn permit và TTL trước consume/reset đầu tiên; không kéo dài một attempt đang chạy. Mô hình đo full-path và safety margin phải được kiểm trước hardware; không tuyên bố đáp ứng worst-case 132 × (1.500 + 2.000) ms trong TTL 300 giây. Hết ngân sách trước start phải dừng khi chưa có native write.

**Bổ sung đo lường cuối:** `request_elapsed_ms` là lúc bắt đầu publication; `host_ack_elapsed_ms` được cấp trước khi ghi permit. Thêm marker device uptime sau temp-write/rename trong cùng frame để đo completion file, không đổi timestamp authorization hoặc ghi permit lần hai. Scope `TEMP_WRITE_RENAME_COMPLETE_NO_EXPLICIT_FSYNC`, durable status `NOT_MEASURED`; không dùng marker để cấp quyền, clamp số đo hoặc tuyên bố host permit đã fsync. Canonical host ledger và native intent audit vẫn có sync riêng. Ba lượt có marker mã cuối 132/132 PASS, ACK max 525 ms, final dispatch max 548,464 ms, không cần đổi timeout.

**Bổ sung raw evidence:** Parser/report hiện hữu cố ý chỉ giữ fields/hash, không giữ raw bytes hoặc unknown/string payload. Để đáp ứng phép thử firmware mà không nới báo cáo công khai, thêm sink debug riêng lưu tối đa `Tbs5530RawDescriptors.MAX_BYTES` vào hai file app-private theo run epoch, trước upload và sau fresh observation. Dùng checked durable writer, không thêm USB read, không sửa parser/registry hoặc đưa binary vào report/APK/Git. Lỗi lưu trước upload phải dừng trước consume/native; lỗi sau đủ 132 vẫn giữ counters và metadata đã đọc, kết quả UNCERTAIN, không retry. File raw chỉ xuất có kiểm soát ngoài Git khi cần evidence của đúng attempt được duyệt.

**Crash window/limits:** Intent persist không chứng minh API đã được gọi; audit và USB không phải một giao dịch nguyên tử. Readback không chứng minh khả năng chống mọi mất nguồn/rollback filesystem, không thay consumed store hoặc ledger host và không cho phép retry sau crash. Attempt 04-11 vẫn CONSUMED/UNCERTAIN, không reset/rearm. Không nâng toolchain, đổi protocol FX2 hoặc nhúng firmware.

**Alternatives rejected/reopen:** Không tăng ACK/heartbeat/USB timeout chỉ vì benchmark orchestration lỗi; không chọn E600-only, bỏ ACK từng lệnh, cache permit, async audit hoặc journal khi chưa có số đo/fault tests chứng minh lợi ích. Chỉ mở lại quyết định timeout sau dữ liệu full-path trên đúng OnePlus và kênh thật; hardware luôn cần phê duyệt độc lập mới.

## ADR-020 — Phase 2.9: debug-only one-shot Android EP0 execution with expiring approval

### Bổ sung ngày 2026-11-05 — New audited attempt không rearm attempt cũ

**Decision:** Giữ nguyên `phase29-attempt.consumed=true` của lượt ngày 04-11.
Một attempt tương lai chỉ được xét khi người dùng duyệt riêng, operator cấp GUID mới,
host ledger canonical ngoài Git đã có audit lịch sử và append/fsync
`NEW_APPROVAL_CONSUMED` trước permit đầu tiên. GUID đã xuất hiện thì bị từ chối,
kể cả UI chưa bấm; không tự cấp lại nếu crash hoặc helper chết.
App ghi consumed trong preferences riêng theo GUID trước native write; không xóa hoặc
đổi false cờ legacy. Install/challenge/run epoch/attachment/plan/hash đều phải khớp.
Uninstall/clear data xóa preferences thật; ledger host độc lập vẫn từ chối reuse GUID.
Không bảo đảm chống chủ máy cố ý xóa/rollback ledger; missing/corrupt ledger phải dừng,
không repair/reset tự động.

**Implemented and verified có phạm vi:** Đã ghi audit lịch sử ở canonical ledger qua
initializer chỉ host, `CreateNew` không overwrite, không ADB/app state/permit/grant.
20 deterministic cases chạy trên Android, tests store cách ly và host mock kiểm duplicate/restart.
Đây không phải phê duyệt hardware mới. Chưa chạy helper production với new approval flags.

**Decision — channel:** Giữ fresh nonce/ordinal trước mọi native write, ACK deadline
1500 ms, heartbeat 2000 ms, session TTL 300000 ms và USB timeout 2000 ms riêng biệt.
Kênh ADB persistent pin target/boot, không reconnect; initial identity đầy đủ và boot
identity kiểm mỗi poll/put. Benchmark tổng 240 giây không được hiểu là nới một ACK.
Missing grant trước consume chỉ deny eligibility, không poison preparation bởi UI polling;
sau consume/write hoặc mất foreground thì revoke là trạng thái không thể resume.

**Evidence/limits:** 132/132 live-no-USB request→ACK→Android observe PASS, p50 611 ms,
p95 705 ms, p99 783 ms, max 1476 ms. Margin max chỉ 24 ms, không phải bảo đảm hardware.
Khóa write gate và audit vẫn cần thiết; không tự tăng deadline hoặc tự thử firmware.

### Bổ sung ngày 2026-11-05 — Bảo toàn attempt và provenance trước native

**Status:** Triển khai và kiểm thử trong cùng Phase 2.9 từ baseline `d76bbfb`.
Không có phê duyệt hoặc thực thi hardware attempt mới; approval cũ đã CONSUMED.
Không tạo phase phụ hoặc chuyển frontend.

**Decision:** Không coi request tới transport là native call. Report giữ legacy counters
có nhãn rõ nghĩa và thêm run epoch, planned ordinal, guard requested/accepted,
native started/completed, full-length count, image bytes và payload bytes.
Giữ stop kind có kiểu dữ liệu riêng qua exception chain; lỗi cleanup bổ sung chứ không
xóa nguyên nhân đầu. IOException tại Port cũng đóng write gate, như RuntimeException.
Không corrective release, retry hoặc fallback.

**Decision — durability/test boundary:** Test approval không được dùng hoặc xóa store production.
Uninstall/clear data thật sự xóa app-private preferences; không tuyên bố consumed tồn tại
qua uninstall. Cần audit độc lập phía host và preflight lại sau đổi cài đặt.
Không cài lại APK production đang giữ consumed để làm instrumentation xanh.
Library instrumentation chỉ được chạy khi manifest chứng minh package/targetPackage
cách ly và test dùng manager/raw giả; app tests cần namespace/store test riêng.

**Decision — critical boundary:** Audit intent phải ghi được trước native call; nếu lỗi thì
không gọi USB. Sau audit, kiểm lại quyền ghi/device/epoch. Audit intent không phải call start;
không thể nguyên tử hóa USB và filesystem. Kết quả thật có thể đã về nhưng lỗi owner/audit
sau đó vẫn làm transaction dừng; giữ cả native result lẫn stop provenance, không xóa byte đã trả.
Timestamp native trên Android dùng elapsed realtime để đối chiếu cùng đồng hồ với handshake;
clock trên JVM chỉ là mô phỏng, không dùng làm thời gian đo điện thoại.

**Reopen trigger:** Chỉ xem xét một transaction mới sau gates phần mềm và phép đo không USB,
phê duyệt mới riêng, token/ledger/epoch minh bạch và preflight phần cứng mới.
Không reset, clear, copy hoặc rearm cờ consumed của transaction ngày 04-11.
Giới hạn Android public USB API và race sau kiểm cuối ở ADR gốc vẫn còn nguyên.


### Bổ sung sau một attempt được duyệt — 22:51 +07

Người dùng đã duyệt riêng chuyển host/nguồn OTG/upload và tự xác nhận UI. Approval đã
CONSUMED: 23 Android API calls full-length, runner attempted24 nhưng bước 24 dừng ở guard,
không native call; ảnh chỉ 1344/8192 byte. Kết quả UNCERTAIN, không CPU release/retry/fallback.
Không có post-firmware operational descriptor. Sau phê duyệt recovery riêng và một rút/cắm
OTG bằng tay, raw mới lại MATCH/default thiếu endpoint `0x82`; không coi đó là hoàn tất upload.
Giữ consumed, không xin attempt thứ hai; bước tiếp chỉ phân tích offline acknowledgment và
stop telemetry. Các câu “chưa duyệt/NOT RUN” bên dưới là checkpoint trước thực nghiệm;
[TEST-MATRIX.md](TEST-MATRIX.md) và [WORKLOG.md](WORKLOG.md) ghi kết quả mới hơn.

**Date:** 2026-11-04

**Status:** Đã triển khai trên working tree từ `ead0e4adf5051e878706ad70c3863b90cb8ffb98`.
Phê duyệt phần cứng chưa có; không commit/push. Bằng chứng trước đó: [ADR-019](#adr-019--phase-28-separate-documented-fx2-reset-from-tbs-history-require-board-observation).

**Context:** Đường cũ là `UsbManager` → facade → binding → coordinator → session/lease
→ `Tbs5530ControlTransport` → `Tbs5530ControlIo`; diagnostic raw có reservation chung
và worker riêng của owner. Không thêm owner/worker kết nối song song. Windows đã đo
loader dual-reset thành công nhưng không chứng minh Android hoặc READY.

**Decision — boundary/ownership:** Executor và Android adapter nằm trong `drivers/src/debug`;
UI, permit và SAF nằm trong `app/src/debug`. Main chỉ thêm seam raw descriptors mặc định
throw và truy cập package-private dưới transaction/lease hiện hữu; facade production vẫn
không override `openBootstrap`. Operational 82 guard, registry/XML, frontend và driver cũ
không đổi. Bootstrap không claim, không release interface, không TS; cùng worker giữ
connection. Invalidation chỉ hủy logic; cleanup chờ synchronous call trả về, không force-close.

**Decision — firmware/plan:** Đọc tối đa 8192 byte cộng một byte kiểm trailing từ file do người
dùng chọn; không persist SAF grant hoặc chép blob vào app/Git. URL archive/entry/raw-linear
là metadata người dùng xác nhận; size/SHA-256 vẫn phải đúng khóa hiện hữu. Android path
validate lại ảnh, chỉ `TBS_SOURCE_DUAL_RESET`: 7F92=01, E600=01, 128×64 byte tại 0000–1FC0,
7F92=00, E600=00, delay 100 ms. Tổng 132 writes/8196 byte payload, trong đó 8192 byte image.
Timeout 2000 ms mỗi transfer; full length bắt buộc, kể cả release. Không E600-only/fallback/retry.

**Decision — approval:** Challenge ngẫu nhiên theo lần chuẩn bị/install, attachment hash,
upload epoch 1, plan/hash khóa và hạn tối đa năm phút. Challenge không phải approval.
Helper chỉ chạy với hai xác nhận riêng chuyển host/nguồn OTG và một lượt upload đã được
người dùng duyệt trong phiên. Helper kiểm đúng một TCP OnePlus/API 30, heartbeat và
acknowledgment đúng ordinal trước từng write. App còn yêu cầu foreground và xác nhận UI.
Không tạo permit từ intent/attach/grant/install/launch. Attempt được ghi bền vững trước
bước có thể ghi; duplicate/relaunch/restart không tạo attempt mới. Cài lại không tái dùng
permit cũ vì install/challenge đổi. Không chạy helper thật khi chưa được duyệt.

**Decision — observers/state:** Đăng ký observer từ chuẩn bị ảnh; attach/detach làm stale
approval. Kiểm duy nhất 734c:5530, permission, exact default raw fingerprint thiếu endpoint `0x82`,
owner/epoch/pending/cleanup và permit trước ghi. Lỗi sau bước được thử là UNCERTAIN,
không corrective CPU release. Đủ writes chỉ TRANSITIONING; đóng lease cũ an toàn rồi
mở lease/epoch mới chỉ đọc raw, không xin lại quyền tự động. Có profile với endpoint `0x82` mới chỉ là
OPERATIONAL_CANDIDATE; vẫn không gọi `ready()`. Metadata lưu kết quả API/count/hash,
trường số descriptors, thời gian và stop reason; không path/serial/IP/payload. Report lỗi
thì revoke. Mất foreground cũng revoke, kể cả rotation; không tự tiếp tục lượt ghi.

**Limits/recovery:** Không thể chứng minh độc quyền USB giữa các app qua Android public API;
cần người dùng đóng app giữ tuner và xác nhận không nguồn phụ/backfeed. Reconnect bị Android
bỏ lỡ nhưng tái dùng toàn bộ identity vẫn là giới hạn quan sát. ADB acknowledgment không loại
bỏ race sau lần kiểm cuối; in-flight call không bị cắt, kết quả không rõ là UNCERTAIN.
Không dùng USB reset thay power-on. Rút/cắm tay chỉ là phương án người dùng cân nhắc sau
khi kiểm nguồn; không phải recovery được phần mềm bảo đảm. Nếu thiếu quyền/attachment mới
khi quan sát thì dừng; không reupload hoặc chuyển sang Phase 3.0.

**Alternatives:** Bật boolean upload chung, claim giả, connection thứ hai, tự chạy trên attach,
nhúng firmware, chỉ heartbeat không đối chiếu bước, chọn plan theo fake PASS: không chọn.

**Verification/rollback:** [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md).
Rollback bằng rà diff của debug/seam này; không reset/clean hoặc sửa references. Nếu đã
thực nghiệm lỗi thì rollback code không khôi phục CPU/USB; cần checkpoint và quyết định
vật lý riêng. Không có thử nghiệm Android upload trong checkpoint hiện tại.

## ADR-019 — Phase 2.8: separate documented FX2 reset from TBS history; require board observation

### Bổ sung sau phép đo Windows đã được duyệt — 2026-11-04 18:38 +07

**Status:** Hướng B đã thực hiện phép đo phân biệt trên board thật. Không còn blocker “7F92 có thể
STALL nên phải đợi TBS”. Trace 3028 packets ghi hai lượt dual-reset tự nhiên của BDA 2.0.0.2;
264/264 A0 có completion SUCCESS, hai ảnh dựng lại đúng hash khóa. Descriptor trước/sau lượt đầu
đổi từ default FX2 thiếu82 sang alt0 có82/81. Chi tiết frame/status trong [RESEARCH.md](RESEARCH.md).

**Decision:** Bằng chứng Windows hỗ trợ `TBS_SOURCE_DUAL_RESET` cho chính board trong điều kiện
quan sát, không chứng minh alias CPUCS, E600-only hoặc Android upload. Không chọn E600-only
chỉ từ manual khi dual-reset đã quan sát; cũng không sao chép hai lượt tự nhiên của BDA thành retry Android.
Giữ gate false. Đổi blocker thành preflight/authorization one-shot Android còn thiếu, không gọi phản hồi
board là chưa quan sát nữa. Chưa xây Android uploader ở lượt này vì chưa đủ guard/observer và chưa duyệt
return-host/upload; không bật boolean để thay các điều kiện đó.

**Remaining evidence:** cần lý giải hai đường init tự nhiên/B7 errors của BDA hoặc một kế hoạch Android
một lượt có observer descriptor/epoch độc lập; không biết trạng thái bridge/frontend chỉ từ đủ byte.
Mười B7 vendor IN completion c0000011 sau lượt đầu không phải A0 fail; không được dùng nhánh retry BDA
làm recovery. Source Linux no_reconnect và descriptor mới vẫn là cơ sở thiết kế, không bằng chứng Android.

**Recovery/ownership:** người dùng xác nhận không nguồn phụ và đã chuyển host/reconnect; không chứng minh
EEPROM layout, nhưng giảm khoảng trống nguồn/backfeed của kế hoạch. Một lần mất ADB đã dừng, chỉ tiếp tục
sau xác nhận nối lại. Worker capture elevated đã được người dùng dừng; kiểm không còn capture process.
Hiện tuner ở Windows, không chuyển lại hoặc upload Android khi chưa duyệt riêng. Không tiếp tục frontend.

Các phần dưới ghi quyết định trước capture, được giữ nguyên để truy vết thay đổi chứng cứ.

**Date:** 2026-11-04

**Status:** Triển khai và kiểm offline trên working tree từ baseline Phase 2.7
`ef1154d9c60725150e0d2eaab082d7b644748444`; chọn hướng B. Chưa duyệt capture,
chuyển host hoặc nạp Android. Không commit/push.

**Context:** TRM v2.2 và generic Linux/libusb độc lập xác định E600 cho FX2.
Không có tài liệu chip xác nhận 7F92 là alias CPUCS trên FX2. TBS Linux/C/Go vẫn dual-reset;
đặc biệt C bỏ qua status stop nhưng kiểm release, có short-circuit khiến E600 không được release
nếu bước trước lỗi. Hai SYS hãng 2.0.0.2/2.1.0.3 đều chứa dual-reset và nguyên ảnh firmware Linux
8192 byte. Phân tích tĩnh chưa cho biết completion status thật tại 7F92 trên board đang dùng.
Xem matrix, hashes và RVA trong [RESEARCH.md](RESEARCH.md).

**Decision — evidence:** Không coi câu trả lời TBS là dependency bắt buộc của nghiên cứu.
Thay điều kiện mở lại duy nhất trong ADR-018 bằng các bằng chứng độc lập có thể kiểm chứng:
TRM/generic loader cho protocol chip; static SYS cho loader hãng; URB trace cho phản hồi board;
descriptor/permission mới cho Android sau mọi thử nghiệm được duyệt. Không tự gán hướng A.
ACK không chứng minh alias, STALL không tự chứng minh vô hại, descriptor không xác nhận revision.

**Decision — plans:** Giữ `Tbs5530FirmwarePlan` main nguyên hành vi dual-reset lịch sử.
Thêm `Tbs5530Fx2ProtocolPlan` chỉ trong debug với allowlist chính xác
`TBS_SOURCE_DUAL_RESET` và `DOCUMENTED_FX2_E600_ONLY`, provenance khác nhau.
Candidate E600-only bỏ đúng hai reset metadata 7F92, giữ từng byte DATA và vùng Main RAM 0000–1FFF.
Không biến comparator thành uploader, không fallback/auto-select, không chạy tuần tự hai plans để dò.
Validation ảnh thật vẫn khóa source/entry/size/hash. `hardwareUploadAllowed=false` không đổi;
blocker mới mô tả dữ liệu board/recovery còn thiếu, không giả vờ đã giải quyết phần cứng bằng đổi tên.

**Decision — lifecycle:** Dry-run fake dùng cùng Session/Binding/Coordinator/Transport,
kiểm preflight giả lập trước nhận owner và trước/sau từng bước. Mất ADB trước open tiêu thụ attempt
và không open; mất sau write ghi UNCERTAIN, không retry/corrective writes.
Token/epoch/quyền/cleanup hiện hữu vẫn kiểm từng transfer. Không force-close một call đồng bộ đang chạy.
Thành công chỉ TRANSITIONING; không có phép đánh giá READY trong runner.
Không sửa production facade/profile/registry/XML hoặc thêm đường upload khi launch/attach/grant.
Không xây hardware backend ở hướng B; guard fake không được mô tả thành guard Android thực tế.

**Decision — measurement/approval:** Ưu tiên trace driver Windows 2.0.0.2 đang cài, không đổi driver.
Mọi việc offline và kiểm công cụ hoàn thành trước khi hỏi chuyển tuner. Chuyển host có thể tự kích hoạt
firmware loader hãng, nên cần phê duyệt tác động này và kiểm nguồn/cleanup trước thao tác vật lý.
Sau mapping đúng tuner→hub/interface/address mới xin phép capture giới hạn 30 giây/8192 KiB;
không capture cả root hub hoặc thiết bị khác. Nếu cần reconnect để thấy init, xin phép riêng cho
một reconnect đó. Không mở ChangeMode hoặc tự gọi frontend. Giữ Wireless ADB fail-stop.

**Decision — recovery/gates:** A0 candidate chỉ RAM/CPUCS, không EEPROM. Với đúng FX2,
power-on sau khi mất mọi nguồn nuôi RAM tải lại EEPROM/default; USB reset/CPU reset không tương đương.
Nguồn ngoài/backfeed/EEPROM board chưa biết nên recovery vật lý chưa được chứng minh.
Mỗi attempt tương lai phải khóa plan/hash/VID:PID/attachment epoch/descriptor/quyền/ADB identity,
owner/pending/preflight/approval/stop criteria và nơi lưu trace; phê duyệt một lần không cho retry.
Bất kỳ lỗi/short write/timeout/detach/ADB drop nào sau write đều UNCERTAIN, không release khắc phục.
Sau nạp phải có descriptor/quyền/owner mới và bằng chứng bridge được duyệt riêng; đủ byte không phải READY.

**Alternatives:** (A) chạy E600-only ngay chỉ từ manual; (C) đợi TBS vô thời hạn;
chạy cả hai plans; chấp nhận IO error khi release theo libusb; cài driver Windows 11/HVCI lên Windows 10.
Không chọn: chưa có board/runtime/recovery evidence, hoặc tăng tác động không cần thiết.
License phân phối firmware vẫn là điều kiện phát hành riêng, không chặn kiểm offline nội bộ.

**Verification:** Matrix tests mới và regression trong [TEST-MATRIX.md](TEST-MATRIX.md),
lệnh/exit trong [WORKLOG.md](WORKLOG.md). Tất cả vendor/firmware thật NOT RUN.

**Rollback/reopen trigger:** Thay đổi chỉ debug/test có thể bỏ theo diff được rà soát, không reset/clean
working tree hoặc đụng ba file hướng dẫn có trước. Gate/facade production vẫn từ chối nên không có
USB state cần rollback từ lượt này. Mở lại khi có trace đầy đủ, định danh board đáng tin cậy hoặc tài liệu
độc lập giải quyết khoảng trống, cùng nguồn/recovery đã đánh giá và offline gates đạt.
Sau đó mới xem xét một kế hoạch thử Android và phê duyệt riêng; không tự chuyển sang frontend.

## ADR-018 — Phase 2.7: bootstrap EP0 cùng owner, chỉ dry-run; chặn dual-reset chưa xác minh
**Date:** 2026-11-04
**Status:** Triển khai offline trên working tree từ Phase 2.6 `f03e092702edac5134efea633d8a30a51e1716e4`, nhánh `chore/modernize-android-build`; chưa stage/commit/push. Không phê duyệt phần cứng.
**Context:** Bản chụp mô tả USB OnePlus dài 189 byte khớp các trường chuẩn của Default High-speed FX2; không có endpoint `0x82`. Nạp firmware qua EP0 không cần endpoint TS, nhưng không được bỏ điều kiện kiểm tra streaming hoặc mở connection thứ hai. Archive TBS có ảnh firmware cho TBS5530 dài 8192 byte; kích thước và hash đã được kiểm, dữ liệu nằm trong Main RAM 0000–1FFF. Source ghi cả 7F92 và E600. TRM chỉ chứng minh E600 là CPUCS của FX2; theo tài liệu nhà sản xuất, 7F92 thuộc họ chip cũ và chưa xác minh cách ánh xạ trên board này.
**Decision — fingerprint:** Class debug đánh giá `MATCH`/`PARTIAL_MATCH`/`NO_MATCH` trên các trường chuẩn của device/configuration/interface/endpoint và liệt kê sai khác. Không dùng VID/PID/hash/model/API làm fingerprint. Khớp chính xác cây mặc định chỉ cho kết quả `BOOTSTRAP_CANDIDATE`; profile có `0x82` chỉ là `OPERATIONAL_CANDIDATE`, không phải `READY`. Không suy chip, EEPROM, RENUM, alternate đang active hoặc tốc độ từ kết quả này.
**Decision — ownership:** Giữ cùng Binding/Coordinator/Session/ControlTransport. Nhánh bootstrap có phạm vi package-private nhận connection mà không claim interface. Lease (quyền sở hữu tài nguyên) và session transaction vẫn giữ generation, đối chiếu callback xin quyền, số phiên gắn thiết bị, worker, deadline và cleanup trì hoãn. `requireConnectionOwned` chỉ bỏ điều kiện claim cho quyền sử dụng EP0 riêng; `requireOwned`, `controlIo` và `streamEndpoint` của đường operational vẫn yêu cầu owner đã claim và profile `0x82`. Không sửa facade operational/profile/registry/XML. `Facade.openBootstrap` mặc định từ chối; facade Android không override, nên release/startup/attach không mở bootstrap. Binding kiểm định danh và quyền trước/sau từng transfer. Capability/runner cũ không được ghi hoặc hủy lần thử mới khi OS tái dùng định danh.
**Decision — validation/execution:** Class debug `Tbs5530FirmwareValidation` kiểm URL HTTPS chính hãng, entry, format `RAW_LINEAR`, kích thước 8192 byte, hash và vùng cho phép 0000–1FFF; không có blob trong source. Planner giữ trình tự dual-reset từ source dưới dạng dữ liệu offline; việc mô tả các bước reset không phải quyền thực thi trên phần cứng. Constructor của `Tbs5530BootstrapDryRun` có phạm vi package-private, chỉ nối fake ports trong tests; không có giao diện upload hoặc bộ thực thi Android. Một transaction bao toàn bộ trình tự để cleanup không chen giữa các bước. Kiểm chính xác byte, timeout và thứ tự; không retry. Lỗi sau khi đã thử ghi chuyển sang `UNCERTAIN`, lỗi trước khi ghi chuyển sang `FAULTED`. Hoàn tất chỉ là `TRANSITIONING`; sự kiện firmware-upload và session cleanup không tự chuyển sang `READY`. Lỗi cleanup được giữ cùng lỗi gốc và chặn mở lại bootstrap.
**Decision — gate/recovery:** `hardwareUploadAllowed=false`, không có cờ bật tạm. Không hỏi nạp firmware khi chưa đủ bằng chứng về cách ánh xạ 7F92, revision board và phương án khôi phục. Không tự bỏ 7F92 để làm khác source, cũng không gửi nó chỉ để thử. Không tự gửi CPU release/reset khắc phục khi lỗi, không ngầm nạp trong constructor/permission/startup/attach. Nếu mất Wireless ADB, dừng tất cả. Chỉ tiếp tục sau xác nhận của người dùng, kiểm lại serial/model/API/OTG/ứng dụng/tài nguyên đang chờ xử lý và không dùng handle cũ.
**Alternatives:** Dùng `0x88` làm TS; nới điều kiện operational; claim giả; mở connection riêng; thêm bộ nạp Android bị khóa bằng boolean; tự bỏ legacy reset hoặc sao chép giới hạn E200 của Go. Không chọn vì chưa đủ chứng cứ hoặc làm tăng phạm vi/rủi ro.
**Verification:** Kết quả mới của unit/fake/instrumentation, kiểm ảnh offline bằng Java 8 và build/lint/isolation được ghi trong [TEST-MATRIX.md](TEST-MATRIX.md) và [WORKLOG.md](WORKLOG.md). Không dùng `PASS` trên fake thay cho `PASS` của upload hoặc re-enumeration thật.
**Reopen trigger:** TBS xác nhận cách ánh xạ 7F92, chip/revision board, firmware, phương án khôi phục và quyền phân phối. Sau đó rà soát lại kế hoạch từ source, thêm adapter debug có điều kiện phê duyệt cụ thể và kiểm lại offline trước một câu hỏi phê duyệt phần cứng riêng. Không tự chuyển sang CXD2878/I²C/tuning/TS.

## ADR-017 — Phase 2.6: capability debug chỉ đọc raw descriptors, không nới transport production
**Date:** 2026-11-04
**Status:** Đã triển khai và kiểm trên working tree, baseline `df076d28dd8100d5caa363f1d866a274e3f2b636`, nhánh `chore/modernize-android-build`; chưa stage/commit/push. Checkpoint Phase 2.5 đã push tại SHA này; trạng thái “chưa commit” của ADR-016 bên dưới giữ nguyên lịch sử.
**Context:** Metadata OnePlus không có IN82 nên production profile phải tiếp tục chặn streaming acquisition. Tuy nhiên, đọc descriptor không cần claim hoặc TS endpoint. Bỏ guard trong production/facade hoặc dùng endpoint88 sẽ trộn hai mục tiêu và không có chứng cứ an toàn. Raw diagnostic là yêu cầu mới có phạm vi được người dùng duyệt, không phải bug state machine production cần sửa.
**Decision — boundary:** Tách ba thao tác: metadata không open; permission-only dùng binding/coordinator cũ; raw capability debug yêu cầu quyền đã có và chỉ open/getRawDescriptors/close. `Tbs5530RawDescriptorInspection`, `Tbs5530AndroidRawDescriptorPort`, `Tbs5530RawDescriptors` nằm trong `drivers/src/debug`; chỉ Activity debug gọi khi bấm nút. Port không có claim/setInterface/setConfiguration/control/bulk/reset. Giữ nguyên toàn bộ Session/Binding/Coordinator/AndroidUsbFacade/Descriptors/production registry và state machine hiện tại. Không tự chạy khi attach hoặc tự xin quyền từ raw.
**Decision — ownership:** Retained diagnostic controller loại trừ các thao tác bằng cùng owner và reservation debug trong process. Reservation chỉ giữ token, không singleton connection/runtime/Context. Không mở raw khi binding/session acquisition/cleanup còn pending; không mở production cycle trong lúc raw pending. Hai owner Activity debug không thể mở song song. Raw là one-shot theo operation generation; kiểm VID/PID, identity đầy đủ và permission trước/sau open và sau read; listen attach/detach làm mất hiệu lực thận trọng. Không tái dùng capability sau invalidation; khác generation không công bố dữ liệu. OS vẫn có giới hạn nếu bỏ lỡ reconnect có toàn bộ fields giống nhau.
**Decision — concurrency/cleanup:** Dùng worker/scheduler của owner hiện hữu; main/receiver chỉ chuyển việc hoặc invalidate. Deadline10s là hủy logic, không khẳng định cắt được API native đang block. Connection local do worker giữ, close đúng một lần sau khi read trả về, rồi đóng receiver; owner đóng trước khi runtime shutdown/drain. Pending giữ true khi API chưa trở lại; không force-close, không retry. Close failure không được báo cleanup success và reservation không mở lại. Không claim nên không release interface. Không chuyển session sang READY/CONNECTED giả cho descriptor-only.
**Decision — parsing/report:** Parser Java8 có giới hạn1MiB, unsigned8/16, kiểm bLength>=2, bounds và wTotalLength; descriptor thiếu/malformed/null/empty/truncated được ghi rõ, không loop bLength0. Phân biệt interface ID/index/alternate, bNumInterfaces và số alternate; không suy active alternate. Chỉ giữ trường số standard device/config/interface/endpoint; string và payload unknown không lưu. Có length/SHA256/status/issues, Git baseline+working-tree disclaimer, operation generation, API/model, permission/identity, thread/duration, API calls/counters, pending/failure. Raw bytes không persist; report app-private không USB serial/path/OS ID/credentials.
**Alternatives:** Nới production82, gán88 làTS, gọi facade.open rồi claim giả, thêm raw method vào capability control production hoặc sửa state machine để nhận connection không claim. Không chọn: các cách này mở rộng quyền/đường production mà không cần thiết. Capability debug giới hạn có quyền sở hữu rõ phù hợp hơn, không thay profile.
**Verification:** Mã trước hardware có199 JVM và47 instrumentation PASS, debug/release/lint PASS; final gates và chi tiết exact commands ở TEST-MATRIX/WORKLOG. Hardware được duyệt một chu kỳ: permission callback thật1/match=true, raw189 bytes/hash `e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`, open/read/close1, claim/transfer0, pending=false,13ms. Release manifest/DEX không có6 class diagnostic. Không gọi đây là streaming/firmware PASS.
**Consequences/revisit:** Raw khớp metadata, không82; Android metadata-parser omission không giải thích snapshot này. Cold/warm/USB mode vẫn chưa chứng minh; ChangeMode chỉ nghiên cứu tĩnh, chưa đủ behavior/rollback. Chỉ xem xét firmware/bridge sau plan riêng có source/license/hash/address map/rollback/permission-reenumeration và phê duyệt hardware, không tự bắt đầu phase sau.

## ADR-016 — Phase 2.5: tách kiểm tra quyền khỏi nhận connection/claim interface khi mô tả thiết bị chưa phù hợp
**Date:** 2026-11-03
**Status:** Đã triển khai trên working tree tại baseline `dc50e7304941a7d01aa1d490c13fcf09683a01c9`; chưa commit/push. Baseline này đã chứa Phase 2.4B/ADR-015 và đợt sửa tài liệu.
**Context:** OnePlus 7 GM1900/API 30 thực sự nhận 734c:5530 qua OTG, nhưng interface ID 0/alternate 0 không có endpoint; alternate 1 có Bulk IN 81/86/88, không có 82. Alternate 2/3 cũng không có 82. Profile Android (bộ điều kiện kiểm tra mô tả thiết bị) hiện yêu cầu Bulk IN 82 và mã Linux tại SHA đã khóa dùng 82 cho TS. Không đủ cơ sở coi 88 là TS, đổi alternate hoặc bỏ kiểm tra để claim.
**Decision — permission only:** Thêm lựa chọn `requestPermissionOnly` trong binding hiện hữu và overload của coordinator không nhận connection hoặc claim interface sau khi cấp quyền. Vẫn dùng cùng cơ chế đối chiếu yêu cầu quyền/phản hồi, số phiên, receiver và kiểm tra danh sách thiết bị hiện tại; mặc định `start` cũ giữ hành vi. Công cụ chẩn đoán có nút chỉ yêu cầu quyền; chu kỳ bị vô hiệu nếu metadata không khớp profile mặc định. Không tạo vòng đời USB thứ hai, không đổi Tbs5530Descriptors hoặc tự gọi setInterface/setConfiguration.
**Decision — acquisition gate:** Phải kiểm snapshot descriptor (bản chụp mô tả thiết bị) trước khi người dùng bấm chạy chu kỳ, tránh open chỉ để phát hiện thiếu 82. Snapshot giả lập dùng trong kiểm thử có profile rõ ràng. Snapshot OnePlus là dữ liệu cho kiểm thử hồi quy: từ chối cả alternate 0 rỗng và các alternate 1–3 thiếu 82; không suy firmware đã thay đổi chỉ từ VID/PID hoặc descriptor khác lịch sử.
**Decision — evidence export:** Activity debug ghi JSON bằng AtomicFile vào vùng riêng của ứng dụng tại `files/tbs5530-diagnostic.json`, đọc qua run-as không cần root. Báo cáo gồm timestamp, operation ID, generation, model/API, cấu hình/interface index/ID/alternate/endpoints/interval, quyền, callback count/correlation match, state trước cleanup, counters, pending và lỗi đã lọc. Các trường này ghi thời điểm, thao tác, phiên, mô tả thiết bị, trạng thái, bộ đếm và tài nguyên đang chờ xử lý. Không ghi device path/OS ID/USB serial, UUID correlation hoặc thông tin xác thực. Release không chứa Activity/mã xuất báo cáo.
**Observed permission:** Lượt yêu cầu đầu hiện hộp thoại thật; người dùng chọn OK sau deadline 30s nên receiver đã được dọn, callback_count 0 và session CLOSED, không nhận connection hoặc claim interface. Làm mới xác nhận hasPermission=true; lượt chỉ yêu cầu quyền mới ghi pre-granted (quyền đã có sẵn) và state PERMISSION_GRANTED trước cleanup. Không tuyên bố callback cấp hoặc từ chối quyền thực đã PASS; không tự thu hồi quyền để ép hiện lại hộp thoại. Deadline 30s là chính sách hiện hữu, không được nới chỉ để biến lần quá hạn thành PASS.
**Wireless guard:** Chỉ chọn thiết bị đích GM1900/API 30 đã xác minh, kiểm online trước mọi bước và định kỳ trong build/kiểm thử. Khi mất kết nối, phải dừng sửa mã/build/kiểm thử/truy cập USB và chờ người dùng xác nhận nối lại; không chuyển sang emulator hoặc dùng lại generation/handle cũ. IP/port là dữ liệu cục bộ khi chạy, không ghim trong mã hoặc hồ sơ công khai.
**Consequences/revisit:** Việc nhận diện thiết bị/USB Host và quyền hiện hành được kiểm thật; open/claim/release/close và rút thiết bị thật vẫn BLOCKED/NOT RUN vì profile chưa an toàn. Cần nghiên cứu riêng trạng thái firmware/nguồn gốc descriptor với dữ liệu được phê duyệt; không thực hiện firmware/vendor/I²C/tuner/TS trong phase. Bước sau phải xin phạm vi mới, không coi việc cấp quyền/kết nối OTG là DVB hoạt động.

## ADR-015 — Phase 2.4B: diagnostic chỉ debug và giới hạn Windows USB passthrough
**Date:** 2026-11-03
**Status:** Implemented; bằng chứng kiểm thử ghi trong TEST-MATRIX; không commit/push. Baseline `c153e05e454f506f5c6639685d2cded51e8c0126` đã chứa Phase 2.4A/ADR-014 và đã push. Người dùng cho phép thử chuyển tiếp USB (passthrough), đọc metadata, xin quyền và thực hiện một chu kỳ kết nối; vẫn cấm control/firmware/reset hoặc thay driver.
**Observed blocker:** Emulator 37.2.13/QEMU 2.12.0-19097-g85fa07f04ef nhận usb-host và thấy 734c:5530/480Mb/s ở host. QMP info usbhost không chứng minh guest đã nhận diện thiết bị. LIBUSB_DEBUG=4 cho `winusb_open unsupported API call for 'open' (unrecognized device driver)` và `libusb_open returns -12`; PnP xác nhận TBS BDA 2.0.0.2/tbsBDA.TBS5530/oem108.inf. Thành phần backend không hỗ trợ driver hiện tại; đây không chỉ là thiếu quyền Android hoặc WSL đang giữ tuner. Usbipd ở trạng thái Shared, không Attached; không cần detach WSL.
**Decision — stop boundary:** Không thay WinUSB/libusbK/filter/Zadig hoặc dùng kernel/QEMU tùy biến. Đã thử một cấu hình XHCI theo tài liệu QEMU nhưng không cải thiện, chưa xác nhận boot; dừng, không lặp vô hạn. Cấu hình gốc chỉ thêm log/QMP và guest-reset=false để tìm nguyên nhân; sau kết quả -12 thì dừng passthrough. Không gửi vendor control/firmware/reset, không tự cấp quyền USB. Giá trị false của Android Host feature trong image là giới hạn riêng; không được coi lỗi driver BDA là bằng chứng mọi USB image đều không hỗ trợ.
**Decision — debug owner:** Thêm Activity được gọi tường minh, chỉ trong app/src/debug, không có intent filter USB attach/launcher và không tự open. Debug manifest đặt USB Host là optional để diagnostic chạy cả trên image không có Host; release/main giữ nguyên. Controller gắn với owner, được giữ qua thay đổi cấu hình; dùng binding/facade/runtime/session hiện hữu. Làm mới metadata không open. Chỉ nút do người dùng bấm cho đúng TBS mới bắt đầu một chu kỳ open/claim(false)/release/close sau khi được cấp quyền; không gọi controlIo/controlTransfer, không retry. Deny/timeout không acquire. Cleanup/deadline giữ theo ADR 012–014.
**Decision — evidence/privacy:** UI và báo cáo được sao chép dưới dạng văn bản thuần chỉ có VID/PID, descriptors, state, generation, counters, thread, duration và lớp exception; không có path/OS id/serial hoặc nội dung exception thô. Log libusb thô ở local temp không commit vì chứa định danh thiết bị khác. Không coi việc thiếu TBS hoặc fake PASS là permission/lifecycle hardware PASS; grant/open/claim/detach trên phần cứng giữ BLOCKED/NOT RUN riêng.
**Alternatives:** Thay driver có thể cho phép mở bằng libusb nhưng cần phê duyệt riêng và phương án khôi phục BDA; chưa chứng minh Android image sẽ nhận diện thiết bị. Chuyển sang Android thật để sau phase này. Không cài Android-x86 VM/image tùy biến hoặc chạy USBIP stack mới. Vẫn hoàn thiện diagnostic chỉ debug và kiểm thử hồi quy khi passthrough bị chặn.
**Revisit:** Nếu sau này duyệt đổi driver, phải giữ package BDA và phương án khôi phục, đánh giá xung đột với tuner trên Windows, kiểm từng tầng host/guest/framework; không suy rằng WinUSB giải quyết tất cả. Phương án ít đổi host hơn là Android USB Host thật với OTG/nguồn và phê duyệt metadata/permission/chu kỳ kết nối rõ ràng; không tự chuyển thiết bị trong 2.4B.

## ADR-014 — Phase 2.4A: control capability gắn với connection đang được quản lý
**Date:** 2026-11-03
**Status:** Implemented trong working tree `chore/modernize-android-build`, baseline `8a004a3cc94d8c736da84cad1908b6a270b789a0`; chưa commit/push. Phase 2.3/ADR-013 đã tích hợp và push tại SHA này. Phase 2.4B chỉ lập kế hoạch, chưa phê duyệt thực thi.
**Context:** Facade/Coordinator đã sở hữu một connection được claim. Mở thêm connection hoặc đưa UsbDeviceConnection ra caller sẽ phá quy tắc sở hữu, generation và việc dọn tài nguyên trì hoãn (deferred cleanup). Cách mã hóa EP0 giữ nguyên trong Bridge; Android control API có offset từ API 18, kết quả âm không chỉ rõ timeout.
**Decision — capability:** Thêm Tbs5530ControlTransport implements ControlIo; constructor package-private chỉ do coordinator cấp qua controlIo(Request). Binding.controlIo() chuyển đúng request hiện tại; factory yêu cầu session sở hữu đúng Lease đã claim. Mỗi lời gọi kiểm token/state/owner trước và sau, đi qua Session.transaction hiện hữu, không chịu trách nhiệm close/open và không retry. Connection bổ sung default methods từ chối mặc định (fail-closed) cho control/metadata để fake cũ và driver khác không buộc đổi vòng đời; production facade override đầy đủ.
**Decision — Android boundary:** RawConnection.controlTransfer gọi cùng UsbDeviceConnection.controlTransfer với đủ tham số, không mở/đóng connection mới. OwnedConnection kiểm worker trước khi khóa, kiểm đã claim/chưa close/chưa release; khóa connection bảo vệ cả lời gọi thô và release/close. Giao dịch đang chạy của Session bảo vệ vòng đời connection thật trước invalidation; cleanup sau khi lời gọi trả về và nhả khóa theo ADR-012/013. Không force-close hoặc coi requestCancellation là hủy I/O thật. Tham số timeout được chuyển cho API đồng bộ; owner/scheduler yêu cầu hủy logic của một control call qua session/binding, không thêm timer/executor ẩn cho mỗi transfer.
**Decision — buffers/errors:** Adapter kiểm các trường 8/16 bit, giới hạn buffer không tràn số, length 0..65535 và timeout>0. Dùng bản sao buffer, giữ nguyên offset/length/payload; IN chỉ công bố phần dữ liệu khi thành công đầy đủ và owner/generation còn hợp lệ; OUT không sửa buffer của caller. Kết quả âm/quá dài là IO_FAILURE, thiếu byte là SHORT_TRANSFER; exception timeout tường minh giữ cause/TIMEOUT. Không diễn giải kết quả âm -110 là chắc chắn timeout trên Android. Không tự chuyển READY, chạy firmware/frontend hoặc gửi lệnh ghi khắc phục.
**Decision — descriptors:** Metadata endpoint bất biến, kiểm interface ID/alternate/address/direction/type/duplicate/maxpacket; tái dùng Bridge.selectBulkEndpoint để chọn Bulk IN 82, không chọn IN 81 chỉ vì đứng đầu. Giới hạn TS maxpacket 64/512 trong board profile là chính sách phần mềm thận trọng, không chứng nhận tốc độ; bằng chứng lịch sử 512/USB 2 High Speed trên Windows/WSL không thay phép đo Android. EP81 không làm control endpoint cho adapter; EP0 dùng vendor requests. Không thêm TS reader/JNI.
**Decision — future owner:** Một owner theo session giữ binding+runtime+capabilities, không dùng Activity Context hoặc singleton toàn process. UsbDelegate chỉ báo device intent; registry constructor không dò thiết bị. Owner DvbDevice/diagnostic tương lai phải close binding/invalidate trước khi shutdown runtime, drain ngoài main thread, giữ pending nếu I/O không trả về và chỉ reopen sau cleanup. Không nối TBS vào service cũ/registry/XML trong phase này; không thêm background service mới.
**Alternatives:** Mở connection thứ hai, công khai UsbDeviceConnection thô, để transport tự close mỗi lời gọi, hủy bằng UsbRequest.cancel hoặc force-close. Không chọn vì quyền sở hữu, nguy cơ tranh chấp và hợp đồng API. Giữ adapter là capability hẹp do Session sở hữu; các byte giao thức vẫn nằm trong Bridge. Generic transport có thể biểu diễn nhiều control requests nhưng không phải firmware executor, cũng không cấp quyền gửi chúng lên thiết bị.
**Verification/revisit:** Số lượng và lệnh chính xác ở TEST-MATRIX/WORKLOG; tất cả tests dùng fake RawConnection/Manager. Không dùng control adapter trên phần cứng. Phase 2.4B phải xin duyệt riêng Android Host/nguồn/OTG/điểm vào diagnostic và từng bước enumeration→permission→open/claim false→release/close; vendor transfer/firmware/frontend/TS vẫn cấm. Không có phê duyệt thì toàn bộ bước trên phần cứng là NOT RUN.

## ADR-013 — Phase 2.3: binding Android opt-in, permission correlation và worker deadlines
**Date:** 2026-11-03
**Status:** Implemented trên working tree `chore/modernize-android-build`, baseline `635ee4a57267de6055126efcfee6121b30701433`; chưa commit/push. ADR-012 đã tích hợp và push tại baseline này; snapshot “chưa commit” bên dưới là lịch sử.
**Context:** Registry/XML chưa nhận TBS5530. UsbDelegate chuyển sự kiện attach sang Activity; DvbUsbDevice dùng UsbPermissionObtainer/Future và các hàm open/close là final. Permission helper cũ có thể open trực tiếp từ receiver. Không dùng đường đó cho vòng đời mới vì generation/correlation/deadline của ADR-012 cần hợp đồng riêng. Không thay toàn framework hoặc driver cũ.
**Decision — layering:** Tbs5530UsbBinding giữ logic JVM với Facade/Registration/PermissionCallback/Events/Scheduler/Errors; Tbs5530AndroidUsbFacade bọc UsbManager/Context/PendingIntent, cho phép inject Manager/RawConnection; Tbs5530AndroidRuntime sở hữu một worker duy nhất và một scheduler độc lập. Các lớp chưa nối vào service, registry hoặc Activity nhận attach. Khởi tạo không xin quyền hoặc open thiết bị; engine chỉ đăng ký receiver khi caller chủ động tạo binding.
**Decision — permission:** Mỗi request dùng mã đối chiếu (correlation) ngẫu nhiên, action và data URI riêng, setPackage cho app, one-shot PendingIntent; API 31+ dùng FLAG_MUTABLE để hệ thống điền extras, trước 31 mặc định là mutable, không dùng ALLOW_UNSAFE_IMPLICIT_INTENT. API 33+ dùng context receiver NOT_EXPORTED và typed parcelable; các bản cũ dùng API tương ứng có guard. Receiver xác nhận action/data và kiểu extra rồi chuyển xử lý nhanh; engine kiểm correlation, attachment epoch, state, permission và device list hiện tại trước khi acquire ở worker. Extras sai hoặc thiếu không open; callback trùng/muộn không acquire lần hai. Khi hủy registration, cancel PI và unregister theo cách gọi lại không gây tác dụng thêm; giữ application Context, không Activity Context.
**Decision — identity:** Request gắn path/id/VID/PID với epoch của ứng dụng tăng mỗi lần start, không bắt buộc serial. Attach tại path đang theo dõi làm mất hiệu lực theo hướng thận trọng; không tự reconnect/open. Callback detach chụp lần thử khi nhận sự kiện rồi kiểm danh sách thiết bị hiện tại trên scheduler, không xếp sau acquisition đang bị block. Nếu cùng snapshot thiết bị còn hiện diện, bỏ callback detach có thể đến muộn. OS không cung cấp attachment epoch trong USB broadcast nên không thể phân biệt chắc chắn khi bỏ lỡ detach+reattach mà toàn bộ fields đều giống nhau: cần sự kiện gắn mới tường minh hoặc owner start, rồi kiểm trên phần cứng sau.
**Decision — ownership:** Kiểm identity hiện tại+hasPermission trước/sau open và claim. Chọn interface ID 0/alternate được chỉ định từ descriptor, không dựa vào vị trí mảng; không force=true. Chỉ ghi nhận claim receipt sau khi claim thành công và trước hậu kiểm để khi lỗi vẫn release đúng phần đã acquire. Khi open/claim lỗi hoặc bị từ chối quyền sở hữu, cleanup lease cũ, giữ cause/suppressed. Wrapper không có điểm vào controlTransfer/firmware/streaming.
**Decision — threading/timeouts:** Receiver/main chỉ phân tích/chuyển xử lý và làm mất hiệu lực về logic; permission/open/claim/cleanup chạy trên worker. Deadline xin quyền tính cả thời gian start chờ trong hàng đợi; acquisition có deadline riêng. Task deadline gắn ID riêng để timer đã cancel không vô hiệu hóa stage/generation sau. Scheduler vẫn invalidate được khi worker open/claim bị block. Session.requestCancellation/coordinator.invalidate chỉ đổi state; drainCleanup được gọi ngoài main/receiver. Giữ cách xử lý ADR-012: transfer đang chạy tự drain sau khi hoàn tất, không force-close connection để giả hủy. Chặn reopen khi worker/acquisition/cleanup chưa xong.
**Decision — shutdown:** Đóng binding trước khi shutdown runtime; runtime không join main hoặc chính worker/scheduler hiện tại, không dùng shutdownNow để cưỡng bức I/O. Công việc đã nhận được xử lý hết; nếu thao tác đồng bộ không trả về, runtime có thể chưa kết thúc. Owner phải theo dõi pending/termination thay vì coi shutdown là hoàn tất I/O thật. Worker/scheduler được inject phải tuân hợp đồng; không truyền direct executor cho Android UI.
**Alternatives/tradeoffs:** Tái dùng permission helper global cũ, mutable implicit PI, mở connection trong onReceive, force-close thao tác bị block hoặc thêm background service tự khởi động. Không chọn vì thiếu tách biệt hoặc mở rộng phạm vi. Không dùng immutable PI rồi giả định USB extras vẫn được điền; đã kiểm fill-in mutable thật trên API 37 nhưng USB permission dialog thật vẫn NOT RUN.
**Verification/revisit:** Kết quả JVM unit/instrumentation với fake Manager, receiver/PendingIntent thật và khả năng phản hồi main thread ở TEST-MATRIX. API 21/23/31/33/34 chỉ kiểm các nhánh policy/tài liệu, chưa chạy runtime trên từng OS. Permission prompt phần cứng, nguồn gốc detach, reattach cùng path, deadline kernel, firmware/frontend/TS vẫn NOT RUN. Phase 2.4 phải chốt quyền sở hữu của caller, vòng đời receiver/process và phê duyệt USB hardware riêng; emulator PASS không mở bước phần cứng.

## ADR-012 — Phase 2.2: invalidation tức thời, cleanup sau giao dịch và ownership qua lease
**Date:** 2026-11-03
**Status:** Implemented trong working tree trên `chore/modernize-android-build`, nền `6eb23537484a3e1dd263bd5a88f56914c7c5e313`; chỉ fake/JVM, chưa commit. ADR-011 đã được tích hợp tại đúng baseline này; các snapshot “chưa commit” của ADR-011 bên dưới giữ nguyên lịch sử.
**Evidence trước sửa:** Hai tests mới giữ fake controlTransfer ở thread A rồi detach/cancel ở thread B đều FAIL trên baseline: callback vòng đời không hoàn tất trước khi thả fake I/O. Nguyên nhân là Bridge giữ monitor của session xuyên I/O đồng bộ nên callback không thể invalidate sớm. Callback detach chạy ngay trong transport còn có nguy cơ close connection trước khi hàm transfer trả về.
**Decision — locks/admission:** Tách transactionLock khỏi monitor của session. Cặp 0x90/0x91 vẫn là một giao dịch không xen ngang giữa các bridge cùng session; state lock chỉ bảo vệ dữ liệu nhỏ. Giao dịch đã được nhận trước invalidation có thể còn chạy; không tuyên bố rút lại request đã nhận hoặc ngắt controlTransfer. Kiểm state/generation trước/sau từng transfer và trước khi công bố read buffer. Callback cũ không được công bố output hoặc gửi nửa giao dịch chưa được nhận.
**Decision — invalidation/cleanup:** Close/cancel/detach đổi state và cancellationRequested ngay, chặn nhận giao dịch mới. Nếu còn giao dịch đang chạy, giữ connection nguyên vẹn và isCleanupPending=true; giao dịch cuối thực hiện cancel→release→close sau khi nhả cả hai khóa. Close trả về nghĩa là đã invalidate, không đồng nghĩa I/O thật/cleanup đã xong. Chặn reopen tới khi drain xong. Cancel tài nguyên chỉ dành cho stream đã bắt đầu, không ghi lệnh khắc phục, không join thread đang gọi cleanup. Lỗi cleanup tức thời được throw; lỗi cleanup trì hoãn được lưu qua getCleanupFailure và gắn suppressed trên transfer exception. Vẫn chạy các bước tiếp khi gặp IOException/RuntimeException, không hứa xử lý fatal JVM Error.
**Decision — coordinator:** Thêm lớp JVM mỏng Tbs5530LifecycleCoordinator, không framework binding hoặc USB transport cụ thể. Request chứa generation và AttachmentIdentity của một lần gắn, không chỉ VID/PID. Kết quả xin quyền phải đúng request/identity/generation; xử lý kết quả trùng đúng một lần trước acquire. PermissionPort/ConnectionPort/open/claim/cleanup gọi ngoài monitor của coordinator/session. Claim receipt ghi nhận interface đã acquire, kể cả trước lỗi một phần. Coordinator giữ lease tới khi connected+interfaceClaimed chuyển quyền sở hữu nguyên tử cho session; nếu bị từ chối, chỉ cleanup lease cũ. Coordinator chờ acquisition cũ kết thúc/cleanup trước reconnect; không thêm executor hủy open/claim.
**Decision — readiness:** Connection/claim không tự chuyển READY. Các sự kiện firmwareUploaded, ready, frontendInitialized, frontendLock, streamingStarted độc lập; lock đòi khởi tạo frontend tường minh nhưng streaming không đòi khóa tín hiệu. Không có firmware executor hoặc readiness probe thật. `failed` nhận cả state permission/acquisition để lỗi open/claim chuyển FAULTED mà không giả quyền sở hữu.
**Alternatives:** Giữ một lock xuyên I/O; force-close connection với hy vọng ngắt API đồng bộ; thêm framework thread bất đồng bộ. Không chọn vì lần lượt chặn invalidation, chưa có bảo đảm API/driver hoặc tăng phạm vi không cần thiết. Chọn cleanup trì hoãn; chấp nhận tài nguyên còn bị giữ nếu adapter vi phạm deadline/không trả khỏi lời gọi đồng bộ.
**Verification/revisit:** Kết quả thực ở TEST-MATRIX/WORKLOG, gồm tái hiện FAIL, regression và stress hữu hạn. Trước Phase 2.3 cần review ánh xạ attachment identity/binding receiver PendingIntent, deadline/open/claim, thread chịu trách nhiệm cleanup và kết quả âm không phân loại được timeout. Không tự phê duyệt hardware/firmware/frontend.

## ADR-011 — Phase 2.1: chốt ranh giới core offline và trách nhiệm caller
**Date:** 2026-11-03
**Status:** Implemented và unit-tested trong working tree nhánh `feat/tbs5530-offline-protocol`, nền `8588ee7`; chưa commit, không phê duyệt hardware. Bổ sung ADR-010, không thay snapshot lịch sử bên dưới.
**Context:** Checkout TBS đã được người dùng phục hồi và xác minh ở Phase 2.0. Phase 2.1 được yêu cầu triển khai bốn core class Java 8, fake và ba suites, không có adapter Android hoặc uploader. Các parameters được kiểm lại tại ba SHA khóa trong RESEARCH.
**Decision — transport và fault:** ControlIo chỉ là interface được inject. Bridge giữ khóa monitor của session xuyên cặp 0x90/0x91, kiểm generation/state trước và sau mỗi transfer, chỉ copy kết quả read cho caller khi đủ dữ liệu. Số byte âm hoặc lớn hơn yêu cầu là I/O failure, nhỏ hơn yêu cầu là short transfer. Lỗi làm FAULTED; nếu đang FIRMWARE_TRANSITION thì UNCERTAIN. Không retry hoặc gửi vendor writes để tự khắc phục; các lời gọi sau chỉ throw trước I/O.
**Decision — firmware policy:** Planner chỉ nhận dữ liệu tổng hợp do caller cung cấp; bắt buộc hash SHA-256 và allowed ranges, size cap do policy đặt. Base address hiện chỉ là 0 theo loader chính thức. Reserved ranges có thể bỏ trống nhưng hai địa chỉ CPU 0x7f92/0xe600 luôn bị cấm trong vùng DATA; các bước reset được mô hình riêng. Không chọn hash/cap cho ảnh thật. Các bước bất biến gồm stop/chunks≤64 với đuôi khối chính xác/restart/delay 100ms; không có method thực thi production. Executor fake chỉ tồn tại trong test.
**Decision — ownership:** Caller inject các sự kiện permission/connection/claim/ready/lock/stream. Nếu `connected` trả false vì callback cũ/sai state, tài nguyên chưa được chuyển quyền sở hữu; caller phải tự đóng phần đã acquire. Session chỉ cleanup tài nguyên nhận thành công. Permission timeout cũng là sự kiện của caller, không thêm lớp timer/thread. Không tự suy READY từ việc upload hoàn tất; caller phải có phép xác minh độc lập ở bước tích hợp sau.
**Decision — cleanup/cancellation:** Close thường chụp quyền sở hữu dưới monitor, sau đó cancel → release → close ngoài monitor để tránh giữ khóa khi chờ worker kết thúc. Cancel chỉ áp dụng khi đã bắt đầu streaming, release chỉ khi đã claim. Mỗi bước chỉ chạy một lần; các exception được giữ/ghép, không bỏ bước cleanup còn lại. Bridge chạy đồng bộ: close/detach từ thread khác có thể phải đợi transfer đang giữ monitor kết thúc; adapter tương lai phải thực thi timeout 2000ms thật. Không tuyên bố core hiện có thể cưỡng bức hủy native USB. Callback transport không được join worker đang đợi chính callback đó; kiểm thử callback detach hiện chỉ chứng minh chặn nửa I²C read tiếp theo.
**Scope clarification:** Chip-ID/manual-PLP fixtures chỉ kiểm policy, chưa ghi register. TS packet fragmentation/drain và PLP-retune register tests trong kế hoạch Phase 2.0 được hoãn đến khi có component tương ứng; không tạo TS reader/frontend giả chỉ để có PASS. Giữ nguyên registry, XML, DvbDevice/DvbUsbDevice/Retry, DeliverySystem ordinals và TCP Request.
**Alternatives/tradeoffs:** Thêm async executor, cancellation token framework, production firmware runner hoặc tái thiết kế vòng đời dùng chung sẽ mở rộng phạm vi chưa được yêu cầu. Chọn inject sự kiện và các điểm kết nối nhỏ; chấp nhận phải thiết kế tiếp Android callback/timeout/permission và cancel trước khi dùng phần cứng.
**Verification:** 52 methods mới và 83 unit methods toàn ba module PASS, debug APK PASS, lint 0 errors/11 warnings, javac --release8/class version 52 PASS; chi tiết ở TEST-MATRIX/WORKLOG. Không USB/firmware thật, không runtime/instrumentation mới.
**Reopen trigger/next:** Trước Phase 2.2 phải review quy tắc chuyển quyền sở hữu khi callback đến muộn, thời hạn/cancellation của control transfer, kiểm identity/generation sau reconnect và readiness probe. Mọi firmware thật cần phê duyệt riêng nguồn gốc/license/hash/address map; mọi USB hardware writes cần kế hoạch và chấp thuận mới.

## ADR-010 — Phase 2.0: nền tảng TBS5530 offline trước production adapter
**Date:** 2026-11-03
**Status:** Accepted cho thiết kế và phạm vi đề xuất Phase 2.1; **chưa triển khai**. Không phê duyệt phục hồi reference hoặc ghi USB.
**Context:** Baseline `4cf1b64` đã push; nhánh tài liệu `docs/tbs5530-source-architecture`. TBS index vẫn thiếu và có 81.399 staged deletions. Tuy nhiên, 785 blobs được chọn và 572 blobs ở các thư mục cha hiện đều có local: đủ nghiên cứu qua Git objects, chưa đủ một checkout sử dụng bình thường. Ba nguồn khác nhau ở cách chọn tuner, PLP retune, giới hạn và xử lý lỗi.
**Decision — readiness:** Giữ references chỉ đọc. Ghi riêng PASS cho khả năng đọc nguồn đã kiểm và BLOCKED cho checkout. Không suy từ lịch sử 572 blobs thiếu rằng hiện vẫn phải tải. Phục hồi cần phê duyệt mới, backup và manifest thay đổi; NTFS và ext4 WSL có các đánh đổi ghi trong RESEARCH. Không tạo bản sao WSL hoặc đổi cấu hình Git trong phase này.
**Decision — class boundary:** Phase 2.1 chỉ dùng core Java 8 thuần: `Tbs5530ControlIo`, `Tbs5530Bridge`, `Tbs5530FirmwarePlan`, `Tbs5530Session` và scripted fakes/tests. Không đăng ký Creator, không transport Android/JNI, firmware thật hoặc bảng thanh ghi production. Adapter production về sau dự kiến `Tbs5530DvbDevice extends DvbDevice`, kết hợp các abstractions hiện có.
**Reason for lifecycle change:** Điều chỉnh đề xuất ADR-006/Phase 0 về `extends DvbUsbDevice`. `Retry.retry(4)` thực có tối đa năm attempts; `open/close` là final nên không override được để kiểm soát thất bại/cleanup. Firmware/reset không được tự lặp khi trạng thái không chắc chắn. Giữ nguyên các driver cũ, không refactor framework chung chỉ để thêm board.
**I2C/retry:** Khóa cả cặp 0x90/0x91; kiểm địa chỉ 7-bit, flags và length trước IO. Giới hạn ban đầu write 48/read 50 bytes là guard phần mềm, không tuyên bố là giới hạn hardware. Control error/short transfer đưa session vào FAULTED; các lần gọi lại không gửi IO, kể cả khi base adapter thử lại mười lần. Mặc định không retry thao tác chạm hardware. Cleanup tài nguyên host không cho phép gửi vendor writes để tự phục hồi tuner.
**Firmware:** Chỉ dùng dữ liệu giả và kế hoạch thuần trong Phase 2.1. Ảnh thật cần nguồn gốc, giấy phép, hash, board và address map được duyệt riêng. Cap 0xe200 của Go không đủ loại trừ reset register 0x7f92. Chia khối cuối, thứ tự lệnh và lỗi giữa chừng chỉ kiểm bằng fake. `no_reconnect` là giả định từ source; Android identity/permission/fd phải được kiểm lại nếu detach/re-enumerate.
**PLP/chip policy:** PLP nội bộ nhận int 0..255, mặc định **manual PLP 0**, không gọi auto. Giữ API tune/TCP/enum ordinals hiện có; mở rộng API PLP để sau. Official/C ghi PLP mỗi lần tune nhưng nhánh retune cùng chuẩn của Go chỉ đổi bandwidth: cần test riêng cho đổi PLP. Chọn tuner theo bảng tường minh: 0x197 Ascot3, 0x19c/0x39a Freia; từ chối ID chưa được review thay vì mặc định Freia. Chưa xác minh chip thật.
**Alternatives:** Refactor retry hooks chung để dùng DvbUsbDevice; port toàn Go/kernel stack; triển khai satellite/API extension ngay; tạo generic USB framework mới. Không chọn vì phạm vi và nguy cơ regression lớn hơn lợi ích. Chỉ thêm TBS-specific seams khi cần thay transport hoặc fake.
**Consequences:** Có thể bắt đầu offline tests dù checkout TBS còn BLOCKED, nhưng chưa có hỗ trợ hardware. DVB-C/S/S2/S2X, ATSC3, UI chọn PLP, real streaming/native cancel và firmware redistribution để sau. Unit PASS không thay cho Android USB Host/lock/TS hoặc runtime coverage.
**Verification/revisit:** File/hàm/SHA, sơ đồ, manifest dependency, mức rủi ro và tiêu chí ra Phase 2.1 ở RESEARCH; các tests mới đều NOT RUN. Production/hardware cần review kết quả offline và kế hoạch riêng. Khi đổi SHA upstream phải review diff, không tự sync giữa thí nghiệm.

## ADR-009 — Phase 1.2: nâng riêng AGP 9.5.0 và kiểm kê không đổi hệ thống
**Date:** 2026-11-02
**Status:** Accepted theo yêu cầu Phase 1.2; kết quả kiểm thử riêng ở TEST-MATRIX.
**Context:** Baseline Phase 1.1 đã tích hợp tại ace7e9f; người dùng chuẩn bị instruction/skill/scripts kiểm kê local và đã dọn SDK. API Google hiện xác nhận Current Release 9.5.0, notes có mục final 9.5.0 và tồn tại POM đúng phiên bản 9.5.0. Không dùng kết luận AGP còn RC từ lịch sử trước để chặn bản stable mới được xác minh.
**Decision:** Chỉ đổi plugin application/library 9.4.1→9.5.0. Giữ Gradle 9.8.2/checksum, JDK 25, compile 37.2/target 37/min 21, BT 37, NDK 30.0.16248370, CMake 4.1.2 và Java 8. Không bật tính năng experimental backup/restore test hoặc nâng dependency khác.
**Compatibility evidence:** Google notes 9.5 yêu cầu Gradle>=9.6/JDK>=17/API 37; ma trận Gradle 9.8.2 hỗ trợ JVM 25. NDK/BT mặc định trong notes không phải yêu cầu hạ phiên bản. Bản stable đã công bố khác với bản đã kiểm chứng cho dự án: chỉ xác nhận trạng thái sau qua output build/test thực.
**Audit decision:** Giữ audit chỉ đọc với báo cáo metadata ngoài Git. AVD home đã xác định phải được truyền vào emulator subprocess rồi khôi phục; chuẩn hóa hậu tố thư mục để tránh tên rỗng/trùng giả. Không đổi global environment để làm audit xanh; báo Process/User/Machine/JDK candidate riêng. Dùng một implementation chung qua check-env entrypoint. Sửa phạm vi cleanup CMake đúng pin, tuyệt đối không tự uninstall.
**Execution-policy boundary:** Đã hỏi trước khi script bị policy chặn; người dùng cho phép rõ ràng Bypass Scope Process. Chỉ dùng scope của child Process rồi khôi phục; không thay User/Machine, không mặc định tự bypass trong script/task. Đây không phải thay đổi security policy hệ thống.
**Tradeoffs:** Có tên trong AVD metadata không chứng minh boot/runtime; ma trận vendor không thay native ABI/16KiB/static/runtime tests. JDK 25 vẫn cảnh báo Java 8 obsolete; không ẩn hoặc đổi bytecode để né. Giữ các thay đổi local trước phase, không reset/viết lại lịch sử/commit.
**Rollback/revisit:** Nếu AGP regression không sửa được trong phạm vi, chỉ hoàn nguyên root plugin pins về 9.4.1 theo quyết định riêng và giữ công việc local của người dùng; không reset --hard/SDK rollback/xóa AVD. Dừng sau Phase 1.2, chưa port driver/USB hardware.

**Nguồn chính thức (kiểm 2026-11-02):** [AGP Current Release](https://developer.android.com/reference/tools/gradle-api), [AGP 9.5 notes/compatibility](https://developer.android.com/build/releases/agp-9-5-0-release-notes), [Google Maven exact 9.5.0 POM](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.5.0/gradle-9.5.0.pom), [Gradle 9.8.2 Java matrix](https://docs.gradle.org/9.8.2/userguide/compatibility.html).

## ADR-008 — Phase 1.1 runtime không tuner và sửa system-bar insets có giới hạn
**Date:** 2026-11-02
**Status:** Accepted trong phạm vi Phase 1.1 đã yêu cầu; không phê duyệt port driver/hardware.
**Context:** Migration 6da609b đã push; chưa kiểm runtime. Không có AVD/device, chỉ có image 37.0 Google APIs x86_64 local. Người dùng xác nhận tạo AVD riêng; không tải image/SDK mới. SystemUI ANR khi cold boot có thể che app, cần phân biệt lỗi app với system. Với target 37, UI của app vẽ dưới system bars, làm che các nút điều khiển.
**Alternatives:** Dùng image TV x86 không đúng ưu tiên; tải image/thiết bị mới; bỏ qua UI bị che; hạ target/tắt edge-to-edge; áp dụng insets và test lại.
**Decision:** Tạo AVD TBS5530_Runtime_API37 riêng, headless, chỉ định serial; giữ page 4KiB mặc định, không quảng cáo runtime 16KiB. Test registry XML hiện có và thêm NativeLibrarySmokeTest, kiểm static load+direct load, không giả fd/ioctl. Thêm một androidTestImplementation project(:usbxfer) để compile test, không nâng dependency bên ngoài. Chạy task không filter và kiểm XML đủ 2 test thay vì chỉ dựa vào exit 0.
**UI decision:** Helper SystemBarInsets dùng AndroidX core đã có, chỉ áp dụng API 35+, padding theo systemBars|displayCutout và icon system bar tối; gọi ở 3 Activity Welcome/Diagnostic/License. Không thay USB/service protocol hoặc minSDK; không hạ target để né edge-to-edge. Bằng chứng screenshot trước/sau, giới hạn UI và build/lint đã PASS; đường API 21 giữ hành vi cũ nhưng chưa chạy runtime API 21.
**Service/permission scope:** Không có USB/device fixture nên DvbService foreground/streaming và USB permission **NOT RUN**, không ép startService sai vòng đời. Lỗi không có tuner là DvbException đã được xử lý, không crash; việc từ chối quyền lưu trữ không chặn UI đã test.
**Workspace:** Exception trong `.gitignore` và workspace file đúng đã có local trước lượt này; bảo toàn hash và đường dẫn tương đối của 4 roots. Không stage/commit hoặc đổi global settings. Toolchain instructions bằng English cập nhật đúng baseline 9.8.2/9.4.1/JDK 25/SDK 37.2/NDK 30/CMake 4.1.2/BT 37, giữ policies.
**Tradeoffs:** Emulator API 37 x86_64 không xác nhận USB Host/tuner/arm64/16KiB/API 21; SystemUI ANR ở lần boot đầu được ghi FAIL ban đầu và hồi phục bằng Wait, không giấu kết quả. Không tải SDK qua mạng, đổi quyền sở hữu hardware hoặc sửa references.
**Revisit trigger:** Review diff Phase 1.1 trước commit; bổ sung runtime API 21/arm64/16KiB theo kế hoạch được duyệt nếu cần, không tự tiến sang port driver. Bằng chứng/lệnh ở TEST-MATRIX/WORKLOG.

## ADR-001 — Windows primary build, WSL2 diagnostics
**Date:** 2026-11-01
**Status:** Adopted working convention
**Context:** Android SDK/NDK/CMake và Android CLI chính thức được cài trong `D:\Android\Sdk` trên Windows; USB passthrough qua WSL2 cho TBS5530 hoạt động.
**Decision:** Chạy build/ADB/Git từ Windows PowerShell; dùng WSL2 cho lệnh chẩn đoán PyUSB/Linux, truy cập cùng repository qua `/mnt/d`.
**Alternatives:** Phát triển hoàn toàn trong WSL2 với SDK thứ hai; clone repository trùng; ưu tiên kernel WSL tùy biến.
**Tradeoffs:** Chỉ có một checkout nguồn và một SDK; WSL truy cập NTFS có thể chậm hơn; phải chỉ rõ hệ điều hành chạy lệnh.
**Revisit if:** Cần kiểm thử Linux kernel module hoặc toolchain Windows không còn phù hợp thực tế.

## ADR-002 — Hardware source precedence
**Date:** 2026-11-01
**Status:** Adopted
**Decision:** Dùng driver TBS Linux chính thức làm nguồn giao thức ban đầu, C cộng đồng cho bản sửa/tối ưu, Go userspace cho kiến trúc transport và AndroidDvbDriver làm nền triển khai. Giải quyết khác biệt bằng bằng chứng đã ghi nhận và hành vi phần cứng đã xác minh, không dựa vào độ phổ biến của repository.

## ADR-003 — No automatic hardware writes
**Date:** 2026-11-01
**Status:** Adopted
**Decision:** Upload firmware, ghi I2C, cấp nguồn LNB và đổi driver thiết bị cần kế hoạch thử có giới hạn và phê duyệt rõ ràng của người dùng. Cho phép đọc danh sách USB và phân tích source theo cách chỉ đọc.

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
**Decision proposed:** Chọn JDK 17 hợp lệ sau khi được duyệt, SDK duy nhất D:\Android\Sdk; chứng minh baseline nguyên trạng trước. Nâng từng cặp tương thích, pin native và dependencies riêng. Giữ Java source/target 1.8 và minSdk 21 nếu không có quyết định khác; giữ native Release -O1. Không sửa global hoặc build files trong audit.
**Tradeoffs:** Có thể cần cài thêm JDK 17/platform 36/NDK baseline tối thiểu theo bước đã duyệt; chậm hơn nâng tất cả một lần nhưng dễ xác định lỗi/rollback. Các nguồn phiên bản latest chưa đồng bộ nên khóa cặp cụ thể khi bắt đầu, không tự theo latest.
**Verification/revisit trigger:** Commands và tiêu chí ở [RESEARCH.md](RESEARCH.md); unit/native/debug/lint/registry instrumentation/release phải được ghi kết quả thực. Không coi wrapper --version hoặc image đã cài là baseline xanh.

## ADR-006 — TBS5530 userspace, một stream logic và DVB-T2 trước
**Date:** 2026-11-02
**Status:** Proposed, **source-backed nhưng chưa triển khai hoặc thử phần cứng**.
**Context:** Official TBS, community C và Go cùng route hai frontend qua bulk 0x82; register/control dùng EP0. Tuner Ascot3/Freia phụ thuộc chip ID; GPIO satellite có thể cấp nguồn phần board không cần cho T2.
**Alternatives:** Port kernel Linux/module vào Android; chạy toàn bộ Go runtime; hai frontend stream song song; thêm TBS bridge/Java frontend vào AndroidDvbDriver hiện hữu.
**Decision proposed:** Tái sử dụng Android USB permission/connection, native bulk, I2C/frontend contracts, demux/service. Một TBS device, transport vendor có kiểm tra length/errors và lock atomic I2C, firmware provider tách biệt, Sony frontend với chip-ID dispatch; chọn terrestrial 0x82. Chưa init Montage/LNB nếu không có chứng cứ cần thiết; để extension DVB-C/S/S2/S2X sau. Không quảng cáo chuẩn chưa thử.
**Tradeoffs:** Không copy nguyên callbacks/kernel URB/DVB network stack; Go là mẫu phân lớp, không là bằng chứng timing đúng trên Android. Firmware bản quyền/hash/quyền phân phối cần kiểm riêng, không nhúng blob.
**Verification/revisit trigger:** Mock protocol/firmware-tail/error/PLP/unit tests trước; sau đó chỉ hardware theo kế hoạch được duyệt. Gate riêng Android Host → firmware → I2C/chip ID → lock → TS continuity. Chi tiết file/hàm/SHA và rủi ro tại [RESEARCH.md](RESEARCH.md).

## ADR-007 — Nâng trực tiếp toolchain stable trên JDK 25, giữ Android API 21
**Date:** 2026-11-02
**Status:** Accepted theo yêu cầu mới của người dùng lúc 13:06+07; thay đề xuất ưu tiên JDK 17 trong ADR-005 cho lần migration này. Không diễn giải thay đổi này là cho phép hardware writes.
**Context:** Người dùng cho phép nâng project/dependencies và sửa tương thích JDK 25; ask_user xác nhận giữ minSdk 21, references local chỉ đọc, không tải file TBS ngoài nhu cầu. Không có baseline chạy trên toolchain cũ/JDK 17.
**Alternatives:** Kiểm baseline JDK 17 rồi nâng qua nhiều mốc; nâng mọi library bất kể minSdk; giữ nguyên build 8.7; nâng stable mới nhất có kiểm chứng và giới hạn tương thích.
**Decision:** Nhánh chore/modernize-android-build, HEAD gốc e4c13a5b86ad5ef9be887df58226046dcb42dcd5. Gradle 9.8.2 (checksum distribution+wrapper generator chính thức), AGP 9.4.1, JDK 25.0.5.0.3, compileSdk 37.2/target 37/min 21, BuildTools 37.0.0, NDK 30.0.16248370, CMake 4.1.2. Giữ source/target Java 8, không sinh bytecode 25. Chỉ cài 4 package SDK đã khai báo, không cài images/firmware. Tắt tính năng tự cài SDK khi build để kiểm soát dung lượng.
**Dependency exceptions:** AppCompat 1.7.1/Material 1.13.0 giữ API 21; bản latest 1.8.0/1.14.0 đòi API 23. AndroidX test-ext 1.3.0/Espresso 3.7.0 vẫn hỗ trợ API 21; JUnit 4.13.2 vẫn là runner phù hợp source; Mockito 5.24.0 thay Mockito 1/PowerMock. Khai báo trực tiếp localbroadcastmanager 1.1.0 vì dependency bắc cầu không còn; không refactor event bus ngoài migration.
**Tradeoffs:** Không chứng minh baseline gốc xanh; chỉ so hành vi qua regression tests hiện có và giữ public API không đổi. Giữ Java 8 sinh warning obsolete trên javac 25, không ẩn warning bằng flags. Target 37 cần kiểm UI/permission/service thật sau này; compile/assemble không thay runtime test. Phải đo ELF/ZIP cho alignment native 64 bit 16KiB; giữ hỗ trợ 32 bit và alignment 4KiB riêng.
**References policy:** Không chạy Apply script phục hồi TBS hoặc tải 572 ancestor blobs; không sửa index/source reference theo quyết định đã xác nhận. Blocker checkout TBS độc lập với build Android. Không commit/push hoặc chạy USB/emulator.
**Verification/revisit trigger:** Unit 31 methods, debug/release/bundle, instrumentation compile, lint và bằng chứng ABI/ELF/ZIP ghi ở TEST-MATRIX/WORKLOG. Tuner Android/16KiB runtime/permission/service mới vẫn NOT RUN. Chỉ chốt PASS từng bước sau khi lệnh hoàn tất; rollback bằng thay đổi ngược có chọn lọc, không reset --hard/clean hoặc xóa untracked guidance.
