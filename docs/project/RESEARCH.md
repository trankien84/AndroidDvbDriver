# Research log (append-only)

## 2026-11-05 — Phase 2.9: phân biệt handshake trước native và kết quả USB

**Baseline đã kiểm:** `d76bbfb63dd21d55af6ce27fdff0543eb500db72`, nhánh
`chore/modernize-android-build`; working tree sạch đầu phiên, một worktree.
Đã đọc trực tiếp JSON attempt đã lọc lưu ngoài Git của phiên trước, không yêu cầu xuất lại.
Report recovery đang có trên app là dữ liệu ngày 04-11, không phải phép đọc descriptor mới.
Lần kiểm chỉ đọc ngày 05-11 xác nhận đúng một TCP OnePlus GM1900/API 30, USB Host
và tuner `734c:5530`; alias mDNS không được tính thành thiết bị thứ hai.
Preferences production vẫn có `consumed=true`; không sửa hoặc cài lại ứng dụng đó.

### Timeline có thể chứng minh từ report lịch sử

`UNVERIFIED` nghĩa là report cũ không lưu trường đó. Thời gian ở cột cuối bao gồm
handshake, adapter và kiểm owner; **không phải** thời gian riêng của native USB.
Không cộng dồn các duration để dựng timestamp giả.

| Ordinal | Request published / monotonic | Helper observed / target verified / heartbeat / ACK published | Android ACK observed | Native start / end monotonic | API result có trong report | Stop / duration đã ghi |
|---|---|---|---|---|---|---|
| 20 | UNVERIFIED | UNVERIFIED cho từng bước | UNVERIFIED | UNVERIFIED | 64/64; DATA 0x0440 | FULL_LENGTH; 1.134.606 µs |
| 21 | UNVERIFIED | UNVERIFIED cho từng bước | UNVERIFIED | UNVERIFIED | 64/64; DATA 0x0480 | FULL_LENGTH; 1.158.729 µs |
| 22 | UNVERIFIED | UNVERIFIED cho từng bước | UNVERIFIED | UNVERIFIED | 64/64; DATA 0x04C0 | FULL_LENGTH; 1.145.002 µs |
| 23 | UNVERIFIED | UNVERIFIED cho từng bước | UNVERIFIED | UNVERIFIED | 64/64; DATA 0x0500 | FULL_LENGTH; 1.208.014 µs |
| 24 | UNVERIFIED | ACK cuối phiên là 24 theo worklog; thời điểm UNVERIFIED | UNVERIFIED | Không có call 24 trong API results | Không gọi native; DATA dự kiến 0x0540/64 | FAILED_OR_UNKNOWN cũ; 1.510.321 µs |

**OBSERVED:** JSON có 24 adapter requests, 23 API results full length, 1346 payload bytes;
1344/8192 bytes thuộc ảnh. Không có release CPU. `attempted` cũ tăng trước guard,
`completed` chỉ tăng sau khi transport trả về và owner vẫn hợp lệ.
Không được đổi tên hai số này thành native calls.

**SOURCE-CONFIRMED:** helper cũ thực hiện mỗi vòng: một `devices -l`, ba `getprop`,
một uptime, một request read, một permit write và một report read: khoảng tám ADB
subprocess, chưa kể sleep 50 ms. Android đọc permit mỗi 10 ms, thường đọc hai lần
mỗi vòng. Hai nhịp poll không đồng nghĩa ACK đến trong 10 hoặc 50 ms; launch process,
Wi-Fi, ADB shell, filesystem và scheduler đều nằm trong đường đi.
App còn đọc permit riêng cho kiểm foreground/heartbeat trước và sau guard.

Phân bố nearest-rank của **24 duration cũ**, gồm bước fail: min 947,543 ms;
p50 1049,436 ms; p95 1293,526 ms; p99/max 1510,321 ms.
Đây không phải benchmark USB, cũng không đủ mẫu để ước lượng độ tin cậy đuôi dài.

**PROVEN bug từ source:** telemetry gộp lỗi trước native thành FAILED_OR_UNKNOWN/IO_FAILURE;
nhánh Port đóng write gate khi RuntimeException nhưng thiếu IOException.
Test approval dùng target context và xóa preferences production trong `clean()`.
Các lỗi phần mềm này có phép thử tái hiện độc lập, không cần tuner.

**HYPOTHESIS, chưa chứng minh:** ACK 24 đến hoặc được đọc quá deadline 1500 ms.
Chưa biết thời điểm helper thấy request, ghi permit hay app đọc ACK; report không loại trừ
một nhánh `requireCurrent()` thất bại. Không có bằng chứng packet loss, CPU hang,
USB timeout/STALL hoặc lỗi 7F92 gây ra bước 24. Không đổi timeout USB 2000 ms
hoặc chọn E600-only dựa trên giả thuyết đó.

### Telemetry mới và ranh giới tính bền vững

`run_epoch` là mã ngẫu nhiên cho một runner; `epoch` của USB lease vẫn độc lập.
Đếm riêng planned ordinal, guard requested/accepted, native started/completed,
full-length completed, image bytes và payload bytes. Native completion gồm cả return
âm/ngắn và exception đã xác nhận; full-length chỉ đếm return đúng requested length.
Giữ legacy counters có nhãn rõ nghĩa để đọc report cũ, không dùng chúng làm số native.

Lưu audit AtomicFile trước native intent và sau kết quả, gồm timestamp monotonic
ở native start/end và stop provenance. Không xuất message/cause có thể chứa định danh;
exception chain vẫn giữ trong bộ nhớ để phân tích lỗi và cleanup không ghi đè lỗi đầu.
`NATIVE_INTENT_NOT_CALL_START` ghi bền trước call; crash tại khe giữa ghi intent và call
không thể xác nhận đã gọi USB. Không có giao dịch nguyên tử giữa filesystem và USB.
Chỉ gọi `UNKNOWN_IN_FLIGHT` khi bộ đếm cho biết call đã bắt đầu nhưng chưa xác nhận kết thúc.
Guard timeout trước native là `PRE_NATIVE_ACK_TIMEOUT`, không giả thành USB failure.

Sau audit intent, kiểm lại device/permission, authorization và attachment trước call;
IOException/RuntimeException, kết quả âm/ngắn, mismatch hoặc revoke đều đóng write gate.
Không retry, fallback, corrective release, claim interface hay mở streaming.
Đủ 132 writes chỉ cho phép quan sát read-only bằng lease/permission/epoch hiện tại;
không bắt buộc detach/re-enumeration nếu VID/PID giữ nguyên. Có endpoint 0x82 chỉ là
`OPERATIONAL_CANDIDATE`, không phải READY hoặc chứng minh I²C/frontend/TS.


### Checkpoint cuối kênh không USB — giữ cả hai lượt 132 PASS

Sửa parser helper đọc toàn bộ JSON nhiều dòng qua **một file descriptor**, ghép thành một
frame, giới hạn 256 KiB và từ chối lỗi/quá cỡ thay vì cắt. Điều này cần cho report thật
Activity đang `toString(2)`, trong khi fixture minified cũ chưa phát hiện lỗi.
Đọc file descriptor duy nhất giữ snapshot cũ hoặc mới khi AtomicFile rename,
không trộn các thế hệ file. Mock gồm report terminal trên 64 KiB và reject trên 256 KiB.
Ledger phân biệt mode production/benchmark, helper USB calls luôn 0;
terminal ghi native started/completed/full-length/image bytes/stop/cleanup,
không dùng legacy attempted làm native count.

**Live-no-USB cuối:** 132/132 observations PASS, timeout 0, 20 deterministic cases PASS.
Request→Android observe p50 **359 ms**, p95 **506 ms**, p99 **714 ms**, max **759 ms**.
Hai lượt đủ 132 đều giữ trong ledger: 264 mẫu thành công, p95 694 ms, p99 781 ms,
max 1476 ms. Không gộp 76 mẫu của lượt FAIL vào một kết quả “264 PASS”.
Không thể quy toàn bộ chênh lệch giữa các lượt cho code, vì scheduler/mạng không được
kiểm soát như thí nghiệm phòng lab. Các số mới không chứng minh nguyên nhân ACK 24 cũ.

Timeline **lượt cuối**, device elapsed milliseconds; native **NOT RUN**,
không phải timestamp attempt ngày 04-11:

| Ordinal | Request published | Host observed | Host ACK | Android observed | Deadline | Native |
|---|---|---|---|---|---|---|
| 20 | 237377025 | 237377030 | 237377110 | 237377177 | 237378525 | NOT RUN |
| 21 | 237377194 | 237377210 | 237377470 | 237377530 | 237378694 | NOT RUN |
| 22 | 237377550 | 237377560 | 237377650 | 237377738 | 237379050 | NOT RUN |
| 23 | 237377765 | 237377780 | 237377870 | 237377991 | 237379265 | NOT RUN |
| 24 | 237378012 | 237378030 | 237378130 | 237378198 | 237379512 | NOT RUN |

Helper production giữ overall tối đa 300 giây, tương ứng session TTL cũ; giới hạn tổng
60 giây ở bản sửa trung gian không đủ cho 132 vòng. Benchmark tổng 240 giây;
ACK 1500 ms/heartbeat 2000 ms/native timeout 2000 ms không đổi.
Hardware mode được kiểm bằng mock, chưa chạy helper production hoặc cấp permit mới.
Mất target phải fail-stop; chỉ một channel response timeout không được tự ghi thành mất
ADB khi discovery/identity vẫn online. Host ledger ghi riêng loại lỗi và thời gian tổng.

### Đo mới không USB: 132 lượt request → ACK → Android observe

**RUNTIME-OBSERVED, ngày 05-11:** Android Java `app_process` độc lập, không Context,
không app-private data và không dependency `UsbDeviceConnection`, chạy cùng
`Tbs5530ApprovalSession.requireWrite/requireNative` với storage test riêng.
Harness ở `tools/approval-benchmark`, không phải nguồn app production.
Host dùng một persistent ADB shell, giữ binding serial/boot identity và ACK mới cho từng nonce/ordinal.
Kênh benchmark báo 3 ADB subprocess cho phần helper, không phải tổng subprocess của
wrapper push/harness/fake/preflight hoặc monitor độc lập.

Kết quả đủ **132/132**, **0 timeout**, USB calls=0; 20 fake-clock cases cũng PASS trên Android.
Nearest-rank request→observe: p50 **611 ms**, p95 **705 ms**, p99 **783 ms**, max **1476 ms**.
Mẫu max chỉ còn 24 ms so với ACK deadline 1500 ms. Một lượt PASS không bảo đảm Wi-Fi,
Windows sleep hoặc scheduler sau này; không tự tăng deadline hoặc xin upload ngay chỉ vì PASS này.
Các duration cũ gồm adapter/native nên chỉ so đường đi và mức overhead, không công bố tỷ lệ
USB speedup hoặc coi phân bố cũ/mới là thí nghiệm cùng điều kiện.

| Đường đi | Cũ từ source / JSON | Mới đã đo không USB |
|---|---|---|
| Host process mỗi poll | Khoảng 8 ADB subprocess + 50 ms sleep | Một persistent channel, shell builtins; fresh ACK vẫn từng ordinal |
| Android chờ | Poll permit 10 ms, file có thể đọc lại nhiều lần | Clock/storage core dùng cùng deadline 1500 ms; nonce, epoch và final native recheck |
| Tách latency | Chỉ tổng request/adapter/native, không native-only | Request, host seen, host ACK, Android observed và deadline cùng device elapsed clock |
| Tail / giới hạn | max 1510,321 ms tại bước fail 24, nguyên nhân sâu thiếu timestamps | max 1476 ms ở 132 mẫu no-USB; không có native transfer/audit production overhead |

Timeline **benchmark mới**, đơn vị milliseconds từ device elapsedRealtime, không phải timeline
attempt ngày 04-11. Host uptime có độ phân giải 10 ms và clamp cận dưới tới request;
không coi là timestamp nanosecond chính xác. Native start/end là **NOT RUN** trong benchmark.

| Ordinal | Request published | Host request observed | Host ACK published | Android ACK observed | ACK deadline | Native / stop |
|---|---|---|---|---|---|---|
| 20 | 235629986 | 235630020 | 235630450 | 235630621 | 235631486 | NOT RUN; no stop |
| 21 | 235630652 | 235630710 | 235631080 | 235631241 | 235632152 | NOT RUN; no stop |
| 22 | 235631269 | 235631320 | 235631680 | 235631855 | 235632769 | NOT RUN; no stop |
| 23 | 235631882 | 235631910 | 235632300 | 235632473 | 235633382 | NOT RUN; no stop |
| 24 | 235632512 | 235632550 | 235632900 | 235633079 | 235634012 | NOT RUN; no stop |

Một lượt trước đó đủ 76 observations rồi dừng ở bound tổng 60 giây, không phải bằng chứng mất ADB;
discovery/identity vẫn online sau lỗi. Giới hạn tổng benchmark đổi thành 240 giây vì
132 × 1500 ms có thể cần 198 giây, tách khỏi ACK 1500 ms, heartbeat 2000 ms, TTL 300000 ms
và native timeout 2000 ms. Lượt lỗi giữ lịch sử trong ledger; lượt 132 PASS không xóa nó.
Sau PASS đã xuất mẫu vào ledger local ngoài Git và xóa đúng namespace test của lượt thành công.

**Giới hạn cuối:** benchmark không chạy fsync native audit, UsbManager check, synchronous USB
hoặc UI render production; do đó chưa chứng minh latency đủ cho bring-up thật.
Cơ chế guard vẫn fail-closed nếu tail vượt deadline; khi đã có native write thì attempt UNCERTAIN,
không corrective release/retry. Chuỗi log/identity heartbeat chỉ thu hẹp race, không loại bỏ race
mạng mất ngay sau kiểm cuối.

### Readiness bridge/I²C/frontend — chỉ đối chiếu source, không thực thi

Ba checkout sạch, không fetch/sync/sửa: official TBS
`2ce787de9f6e81b4294692a45de15596ae2e1322`, community C
`c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go
`11228b05fb1626bf41c4e1879ace10cd0a8d575c`.

Nguồn truy vết:
[TBS tbs5530.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/usb/dvb-usb/tbs5530.c),
[C tbs5530.c](https://github.com/koreapyj/cxd28xx/blob/c5fa27eecbe80633cbb365cc44656daea1c0e2e3/tbs5530.c),
[Go bridge tbs](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/bridge/tbs/tbs.go),
[Go board](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/board/tbs5530/tbs5530.go)
và bridge/tbs5530 cùng SHA. Đây là thứ tự source, không phải bằng chứng board đã ready.

| Request / hướng | Side effect và điều kiện trước | Response source kỳ vọng | Chắc chắn / phép thử còn cần |
|---|---|---|---|
| Standard raw descriptors, không vendor | Chỉ đọc qua connection đang có permission/epoch hợp lệ; đóng lease nạp trước khi mở observer | Cây hoàn chỉnh; kiểm VID/PID, interface 0/alt 0, Bulk IN 82/81 | Android recovery lịch sử chỉ default. Sau upload mới cần quan sát lại; endpoint không chứng minh frontend |
| A0 OUT 40, dual-reset | Dừng/chạy CPU và ghi RAM; đúng ảnh/hash, một approval đã duyệt | Full length mọi lần; 132 writes, delay 100 ms | Windows có chứng cứ; Android cũ chỉ 23 calls, không đủ ảnh. Không retry |
| 90 OUT 40 → 91 IN C0, value/index 0 | 90 đặt con trỏ I²C với `[readLength, addr<<1, register]`; 91 lấy kết quả. Cả cặp có side effect trên bus, không phải health-check vô hại | OUT 3 byte, IN đúng readLength; source C không kiểm đầy đủ kết quả hai lệnh | Source rõ ở TBS `tbs5530_i2c_transfer` 60–104, C 51–94, Go `WriteRead` 46–54. Cần fixture pointer một byte, mutex và short/error; hardware cần approval riêng |
| 80 OUT 40, value/index 0 | Ghi I²C `[payloadLength+1, addr<<1, payload...]`; có thể đổi thanh ghi frontend | Full length payload+2; Go `Bridge.Write` kiểm giới hạn 62 | TBS 87–96/C 78–87 dùng buffer 50 byte và không kiểm transfer đủ; không sao chép thiếu bounds vào Android |
| 90/91 đọc EEPROM MAC | `tbs5530_read_mac_address` 116–142 lặp 256 địa chỉ với I²C address byte A0; thay con trỏ EEPROM dù không ghi nội dung | IN 1 byte/lần; MAC lấy từ offset 16 | Không cần MAC để chứng minh FX2 đã nạp. Không chạy chuỗi này trong Phase 2.9 |
| 8A OUT 40 `[0A,00]` hoặc `[0A,01]` | Chọn nguồn TS terrestrial/satellite; thay routing dù callback có tham số onoff | OUT 2 byte; source callback không kiểm đầy đủ | TBS streaming_ctrl 167–186/C 149/Go `SelectStreamSource` 74–80. Không coi onoff=false là tắt nguồn đã được chứng minh |
| B7 OUT 40 `[00,00]`, rồi 8A OUT `[08,01]` | GPIO satellite sau attach M88RS6060; delay 10 ms; có thể ảnh hưởng nguồn phần satellite | Source mong OUT 2 byte mỗi lệnh; Go kiểm full length | TBS `tbs5530_frontend_m88rs6060_attach` 202–248/C 495–503/Go `InitializeSatellite` 59–67. Không dùng B7 IN errors của Windows làm readiness protocol Android |

Official `tbs5530_properties` dùng `no_reconnect=1`, generic control endpoint 81,
stream endpoint 82; frontend CXD2878 attach qua I²C tại 64, tuner 60/24 MHz.
C `tbs5530_probe` 690–735 nạp firmware, tạo I²C adapter, URB buffers rồi DVB/frontends.
Go board `open` 109–158 nạp firmware, initialize CXD2878, satellite GPIO và M88RS6060.
Go chủ động initialize cả hai frontend và có bình luận reload mỗi open; **không sao chép**
chính sách đó thành reupload Android hoặc kết luận không có EEPROM trên mọi board.

Không có một bridge-readiness vendor request vô hại được ba nguồn xác lập chung.
Vì vậy không thêm probe 80/90/91/8A/B7 hoặc parser I²C production để làm xanh phase.
Contract hữu ích hiện tại là giữ exact A0 plan và từ chối mọi request ngoài plan;
fixtures về negative/short/exception/ordinal đã kiểm đúng tại bootstrap boundary.
Fixture cho I²C tương lai cần thiết kế cùng bounds/error/locking trước approval riêng,
không phải điều kiện trì hoãn việc sửa handshake trong Phase 2.9.

## 2026-11-04 — Phase 2.9: Android thực đã ghi một phần, dừng ở acknowledgment

**RUNTIME-OBSERVED, 22:51 +07:** Sau phê duyệt riêng chuyển host và một lượt upload,
người dùng cấp permission, tự chọn file SAF và xác nhận UI. Raw trước nạp 189 byte,
MATCH/default thiếu endpoint `0x82`. Android API trả đủ byte cho 23 A0 OUT: hai stop reset và 21 chunk;
1346 byte payload = 2 reset + 1344 image. Lượt chưa hoàn tất 8192 byte.

Runner ghi attempted=24/completed=23, duration 25946 ms. Bước 24 (DATA value 1344/0x0540,
64 byte) ghi FAILED_OR_UNKNOWN sau 1510321 µs, nhưng không có kết quả/call thứ 24 tại
Android port; guard chạy trước lời gọi native. Sau dừng, request/last acknowledged ordinal đều 24.
Điều này tương thích deadline acknowledgment 1500 ms bị lỡ, **chưa chứng minh nguyên nhân sâu**
hoặc việc permit đến trước/sau deadline. Không gọi là USB timeout, A0 STALL hay lỗi 7F92.
Có thể cần telemetry timestamp/stop reason chi tiết hơn và mô phỏng độ trễ handshake offline.
Không tự nới thời hạn hoặc xin attempt thứ hai trong cùng lượt.

Kết quả **UNCERTAIN**, consumed=true, pending/cleanup=false, attach/detach events=0;
không CPU release, corrective writes, retry, fallback, frontend hoặc post-upload descriptors.
Ảnh chưa đủ và CPU có thể halt; cleanup tài nguyên thành công không chứng minh CPU state.

**Recovery RUNTIME-OBSERVED, không phải guarantee:** Người dùng duyệt rồi tự rút/cắm một lần,
chỉ nguồn OTG, không Windows. Permission mới và một owner chẩn đoán mới đọc/đóng raw 14 ms,
open/read/close=1/1/1, claim/transfer=0. Cây 189 byte/hash
`e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`
khớp cây trước nạp, MATCH/BOOTSTRAP_CANDIDATE thiếu endpoint `0x82`. Đây không phải post-firmware
operational profile. Attempt vẫn consumed; chưa đủ điều kiện bridge/I²C của Phase 3.0.

## 2026-11-04 — Phase 2.9: xác nhận chip và ranh giới thử Android

**VENDOR-CONFIRMED:** TBS xác nhận controller USB là Cypress EZ-USB FX2, mã
`CY7C68013` (nguồn: TBS technical support, 2026-11-04; người dùng cung cấp).
Xác nhận này không bao gồm revision, reset semantics, quyền phân phối firmware,
recovery hoặc một bản port Android. Không lưu thư hay thông tin liên hệ.

**SOURCE-CONFIRMED:** Đọc lại `tbs5530_load_firmware` tại official
`2ce787de9f6e81b4294692a45de15596ae2e1322`, C
`c5fa27eecbe80633cbb365cc44656daea1c0e2e3` và `Bridge.LoadFirmware` tại Go
`11228b05fb1626bf41c4e1879ace10cd0a8d575c`; không fetch/sửa references.
Giữ dual-reset và delay 100 ms. Bằng chứng Windows của Phase 2.8 vẫn độc lập
với Android; không sao chép B7 hoặc lần nạp thứ hai.

**Checkpoint trước thực nghiệm (giữ lịch sử):** SAF cho người dùng chọn file; nguồn archive/entry
là lời xác nhận của người dùng, không phải provenance suy ra từ bytes. Size/hash kiểm
bắt buộc trước open. Runner dùng owner cũ, raw fingerprint trước ghi và một lease mới
chỉ đọc sau nạp. `getRawDescriptors()` phản ánh cây Android trả về, không tự chứng minh
alternate/configuration đang active hay bridge/frontend đã sẵn sàng.

**UNVERIFIED / NOT RUN tại checkpoint trước thực nghiệm:** Android A0, transition thực và descriptors sau firmware.
ADB heartbeat kèm acknowledgment cho từng write giảm khoảng trống mất kết nối;
không thể bảo đảm PC/Android đồng thời tuyệt đối ở thời điểm native call bắt đầu.
Khi mất kiểm soát phải ghi UNCERTAIN, chờ call trả về và không ghi khắc phục.
Quyết định/giới hạn và checkpoint ở [ADR-020](DECISIONS.md) và [TEST-MATRIX.md](TEST-MATRIX.md).

## 2026-11-04 — Phase 2.8: đối chiếu CPUCS độc lập và chọn phép đo URB trên board thật

### Kết luận và phạm vi

**Chọn hướng B: có phép đo phân biệt giả thuyết, nhưng chưa đủ bằng chứng để duyệt nạp Android.**
`E600-only` là trình tự được tài liệu FX2 và hai loader độc lập hỗ trợ. Điều đó không chứng minh
board đang cắm là đúng revision, cũng không biến `7F92` thành địa chỉ tương đương với CPUCS.
Không cần chờ TBS để nghiên cứu tiếp. Bước có giá trị tiếp theo là quan sát phản hồi URB của
chính board với driver hãng đã cài, sau phê duyệt chuyển thiết bị và phạm vi capture riêng.
URB là bản ghi yêu cầu/kết quả USB ở phía host, không phải đo tín hiệu điện trên dây.

Baseline thực tế: `ef1154d9c60725150e0d2eaab082d7b644748444`, nhánh
`chore/modernize-android-build`. Ba references giữ SHA official `2ce787de9f6e81b4294692a45de15596ae2e1322`,
C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`;
không fetch/sync hoặc sửa chúng. Toàn bộ nguồn dưới đây truy cập/đối chiếu ngày 2026-11-04.

### Reset-sequence evidence matrix

Các ký hiệu nguồn được mở rộng ngay dưới bảng. `SOURCE-CONFIRMED` là điều source/tài liệu nói;
`OFFLINE-TESTED` là kết quả kiểm file, không phải hành vi board.

| Claim | source/line | FX2 applicable? | evidence strength | contradiction | outcome |
|---|---|---|---|---|---|
| CPUCS là E600; A0/40/index 0, payload 01 giữ reset, 00 release | TRM 2.3.11, 3.8, 7.3 | Có, nếu đúng FX2 | SOURCE-CONFIRMED, tài liệu chip | Không xác nhận chip/revision board | Candidate `DOCUMENTED_FX2_E600_ONLY`, chưa được duyệt chạy |
| A0 do phần cứng xử lý bất kể RENUM; chỉ RAM nội và CPUCS được tài liệu hóa | TRM 3.8, 5.3, 5.6 | Có | SOURCE-CONFIRMED | Không có mô tả phản hồi A0/7F92 | Không kết luận STALL, ACK, alias hoặc vô hại tại 7F92 |
| Linux generic chọn 7F92 cho AN2135/AN2235, E600 cho FX2 | Linux generic, dòng 25–29, 85–129 | Có, nhánh FX2 | SOURCE-CONFIRMED, độc lập TBS | Format ảnh generic là record, không phải raw 5530 | Dùng bằng chứng địa chỉ/reset, không sao chép parser ảnh |
| libusb chọn E600 cho FX2/FX2LP, 7F92 cho nhánh mặc định | libusb, dòng 760–774, 781, 841 | Có, nhánh FX2 | SOURCE-CONFIRMED, độc lập TBS | Nhánh mặc định còn gồm FX1; không khái quát tên họ từ KB | Không dùng libusb để chứng minh mọi FX1 dùng E600 |
| TBS ghi 7F92 rồi E600 khi stop; không kiểm hai kết quả | Official, dòng 283–287 | Source dành cho TBS5530 | SOURCE-CONFIRMED | Release lại kiểm kết quả; không thể suy stop lỗi vẫn luôn thành công | Trình tự lịch sử, không phải giấy phép ghi 7F92 |
| Release tại 7F92 lỗi sẽ bỏ qua release E600 do `ret || call` | Official, dòng 291–310; C, dòng 661–680 | Là logic C, không phụ thuộc chip | SOURCE-CONFIRMED | Bác cách giải thích đơn giản “TBS bỏ qua tất cả lỗi 7F92” | Nếu data lỗi, cả hai release bị bỏ qua; nếu release 7F92 lỗi, E600 bị bỏ qua |
| C dùng cùng dual-reset; Go dừng ngay cả khi stop 7F92 lỗi | C, dòng 654–683; Go, dòng 86–125 | Source board, không đo board | SOURCE-CONFIRMED | Ba nguồn có thể kế thừa cùng thiết kế | Không đếm ba bản sao thành ba phép đo độc lập |
| Windows 2.0.0.2 và 2.1.0.3 đều dựng A0/7F92 rồi A0/E600 | SYS x64, RVA 1C00 và 4CC0; chi tiết bên dưới | Loader hãng TBS5530 | OFFLINE-TESTED; diễn giải control flow là INFERRED | Không có completion status hoặc xác nhận đã đi qua nhánh này | Chưa tìm thấy loader hãng E600-only trong hai gói công khai đã kiểm |
| Kết quả lệnh 7F92 bị ghi đè bằng kết quả E600 trong helper Windows | SYS 2.0.0.2, RVA 1CC0–1D74; 2.1.0.3, RVA 4D83–4E38 | Static board loader | INFERRED từ disassembly | Không biết status thật, timeout hoặc nhánh helper transport bên dưới | Cần trace ghép submission/completion theo URB ID |
| CPU reset không phải power-on reset; endpoint/address/RENUM giữ nguyên | TRM 7.4, 7.5 | Có | SOURCE-CONFIRMED | USB bus reset cũng không xóa RENUM | Không dùng USB reset làm recovery RAM hoặc bắt buộc có disconnect |
| Ảnh Linux trùng nguyên 8192 byte được nhúng trong cả ba SYS | So khớp byte/hash, offsets bên dưới | Tương hợp firmware ở mức file | OFFLINE-TESTED | Không chứng minh đúng revision hoặc đã chạy trên Android | Giải quyết khác biệt ảnh Linux/Windows trong các gói đã kiểm |

**Nguồn có thể truy vết:**

- [TRM v2.2 chính hãng](https://www.infineon.com/assets/row/public/documents/30/44/infineon-ez-usb-fx2-trm-version2-2-usermanual-en.pdf),
  tải lại 5790150 byte, SHA-256 `5b7ee974529ad1a074a804ed19f12eb255b1e797ebb0f604a2ef04e328d4419a`.
  Mục 3.8 xác định Main RAM 0000–1FFF, scratch RAM E000–E1FF và CPUCS E600;
  không cho phép dùng A0 để ghi bộ nhớ ngoài. Mục 7.3–7.5 phân biệt CPU reset, EEPROM boot và USB bus reset.
- [Linux generic khóa SHA 05441d697f7f9a927264f4058cbbc6a03dfcac9d](https://github.com/torvalds/linux/blob/05441d697f7f9a927264f4058cbbc6a03dfcac9d/drivers/media/common/cypress_firmware.c).
  Kiểm đủ byte khi stop/data/release và thoát khi lỗi; không retry. Đây là snapshot source, không phải đề nghị nâng kernel.
- [libusb khóa SHA 987c1170d0018ba82557823a98ae443e8335e11b](https://github.com/libusb/libusb/blob/987c1170d0018ba82557823a98ae443e8335e11b/examples/ezusb.c).
  `ezusb_cpucs` dòng 183–205 chấp nhận `LIBUSB_ERROR_IO` khi release vì thiết bị có thể biến mất.
  **Không sao chép ngoại lệ đó:** thử nghiệm Android vẫn phải ghi `UNCERTAIN` khi release lỗi.
- [Official TBS đã khóa](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/usb/dvb-usb/tbs5530.c),
  [C đã khóa](https://github.com/koreapyj/cxd28xx/blob/c5fa27eecbe80633cbb365cc44656daea1c0e2e3/tbs5530.c),
  [Go đã khóa](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/bridge/tbs/tbs.go).
  `op_rw` trả trực tiếp kết quả `usb_control_msg`, timeout 2000 ms; không chuẩn hóa lỗi thành thành công.
  C luôn xin 64 byte ở khối cuối; Go dùng chiều dài thật. Planner Android giữ chiều dài thật.
- [KB memory 259431](https://community.infineon.com/t5/Knowledge-Base-Articles/Read-and-Write-to-EZ-USB-Internal-Memory-AN21xx-FX-FX1-FX2-FX2LP/ta-p/259431)
  và [KB legacy 248487](https://community.infineon.com/t5/Knowledge-Base-Articles/Generating-script-file-for-the-older-EZ-USB-series-FX-AN21xx/ta-p/248487)
  vẫn trả HTTP 403 khi tải trực tiếp. Search trả trích đoạn legacy 7F92 và E600 mặc định;
  chỉ là hỗ trợ tìm nguồn, không dùng làm bằng chứng board hoặc giải quyết khác biệt FX1 với libusb.
- Lịch sử đường dẫn TBS được kiểm qua GitHub: commit
  [`31a88360f9c082e111cc8a1bcc7e078be5e86970`](https://github.com/tbsdtv/linux_media/commit/31a88360f9c082e111cc8a1bcc7e078be5e86970)
  có message sửa download firmware, nhưng patch thực chỉ tăng hai buffer I²C từ 20 lên 50 byte.
  Không có thay đổi CPUCS trong patch đó; không lấy tiêu đề commit làm bằng chứng đã sửa reset.
  Các entry sau của đường dẫn này sửa I²C/strscpy/disconnect, không cung cấp sequence mới.

### Bốn giả thuyết và phép phân biệt

1. **Legacy compatibility — INFERRED, hợp lý nhưng chưa chứng minh ý định tác giả.**
   Linux generic/libusb phân họ; nhiều file TBS như `tbs5220.c:306/322`, `tbs5925.c:354/370`
   cùng mẫu 7F92/E600 tại SHA official. Đây là dấu hiệu dùng lại template.
   Phản biện quan trọng: C kiểm release 7F92 và sẽ không gửi release E600 nếu lỗi, còn Go kiểm cả stop.
   Vì vậy chưa thể nói board chắc chắn STALL hoặc request bị bỏ qua vô hại.
2. **Alias/vendor-specific behavior — UNVERIFIED.** Không thấy nguồn chip công khai xác nhận alias.
   TRM nói A0 được phần cứng xử lý và E600 là thanh ghi duy nhất được ghi bằng lệnh này,
   nên giả thuyết firmware TBS tự bắt A0 rồi đổi nghĩa cần bằng chứng mạnh hơn.
   ACK trong trace, nếu có, vẫn không chứng minh 7F92 điều khiển CPU.
3. **Sai chip hoặc firmware-state — UNVERIFIED.** Descriptor lịch sử khớp FX2 default HS và ảnh hãng
   có các chuỗi opcode mang địa chỉ E6xx, nhưng firmware đang chạy có thể mô phỏng descriptor.
   Không có PCB marking/revision thực, không yêu cầu mở vỏ. Tài liệu sản phẩm không cho ánh xạ revision→chip.
4. **Loader E600-only khác — SOURCE-CONFIRMED cho generic, chưa thấy trong hai Windows packages.**
   Linux generic/libusb đủ chứng minh candidate FX2 chuẩn. SYS hãng vẫn dual-reset;
   trace có thể xác minh nhánh nào thực sự chạy, nhưng một lần trace dual-reset không chứng minh E600-only đủ cho board.

### Firmware và Windows binary: kết quả kiểm offline mới

[Download center TBS5530](https://www.tbsdtv.com/download/index.html?id=164&path=4) hiện liên kết hai driver
ngày 2025-06-10: Windows 2.0.0.2 và Windows 11/HVCI 2.1.0.3; changelog chỉ ghi sửa DVB-S2X modcode.
Không cài bản 2.1.0.3 lên Windows 10, không chạy setup/dpinst/ChangeMode. Không tuyên bố v1.0 là firmware mới nhất tuyệt đối.

| File chính hãng | Kích thước | SHA-256 |
|---|---:|---|
| Archive firmware v1.0, HTTPS | 566825 | `23ecf817383dc29881ac9cb6f704239969e5f711270ad2073497653ba08791cc` |
| `./dvb-usb-id5530.fw` | 8192 | `b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc` |
| [ZIP Windows 2.0.0.2](https://www.tbsiptv.com/download/tbs5530/TBS5530_release_v2.0.0.2_20250610.zip) | 2397198 | `02903f0e098cfee5a24f693fa215e98d60149230d6b170d2627221978f16a518` |
| [ZIP Windows 11 2.1.0.3](https://www.tbsiptv.com/download/tbs5530/TBS5530_release_v2.1.0.3_win11.zip) | 1780082 | `f260993d21731067ab20691df860366508dbcc711984be6030ca340d0e4e8e05` |
| SYS x64 2.0.0.2 | 404168 | `48f2d8fefd467adc4dafe68c2708439e794c4c1daf69f7db5405b5388cf9e65c` |
| SYS x86 2.0.0.2 | 202136 | `43234f1d7a73e47dc44c040f1fa28e587afd93126a167fec42b7abbbc2a19d0d` |
| SYS x64 2.1.0.3 | 411528 | `69c25564e6f9c5a8968446e40ebc836abc5b49841567f10cfbde2b0285f6a783` |

**OFFLINE-TESTED:** nguyên ảnh 8192 byte trùng tại file offsets `35680` trong x64 2.0.0.2,
`1B890` trong x86 2.0.0.2 và `35350` trong x64 2.1.0.3. SYS đang cài trên Windows có cùng hash
với SYS x64 2.0.0.2 tải từ hãng; service `tbsBDA.TBS5530` đang Running. Đây không phải kết quả tuner chạy trên Windows hôm nay.

`objdump -d -Mintel` chỉ đọc PE, không thực thi SYS. Ở x64 2.0.0.2, helper RVA `1C00`
dựng URB function 17, payload 1 byte từ tham số, request A0/index 0, lần lượt value 7F92 và E600;
call transport tại RVA `1CC0` và `1D4E`. Không có nhánh kiểm status thứ nhất trước lệnh thứ hai;
giá trị trả về bị ghi đè. Caller RVA `2674/26C2` và `26E5/272D` truyền 01 rồi 00 quanh loader,
trỏ ảnh tại RVA `35680`. x64 2.1.0.3 có helper RVA `4CC0`, calls `4D83/4E11`, caller
`5A70/5ABE` và `5AE1/5B29`, cùng cấu trúc. x86 có A0 và value tại VA `113FA/11439`.
**INFERRED:** đây là đường reset loader; chưa biết setup/completion thật trên board.
Không đăng disassembly toàn bộ hoặc binary vào Git. Các RVA là địa chỉ tương đối trong ảnh PE;
VA disassembler x64 2.1.0.3 có thêm image base `140000000`.

Ảnh `RAW_LINEAR`, LJMP tại reset vector trỏ 08FE, vùng dữ liệu 0000–1FFF.
Có 128 khối × 64 byte, gồm cả hai khối toàn zero; không bỏ zero hoặc tự padding.
So khớp từng byte qua planner cho cả hai plans đạt `OFFLINE-TESTED`:
dual-reset 132 writes + delay; E600-only 130 writes + delay 100 ms. Byte cuối bị đổi được hash guard từ chối.

Kiểm chuỗi byte 8051 cho thấy mẫu `MOV DPTR,#E600` tại offsets 04CF/0B53/0B6D/0E62,
mẫu E680 tại 001C/002A/08D5/08E1/0C80/0C89/0CA4; không thấy mẫu DPTR 7F92.
**Đó là tìm mẫu byte, chưa phải chứng minh code reachable.** Descriptor candidate tại offset 098A
mang VID:PID 734c:5530; config candidate 09A6 có Bulk IN82/81, config 09C6 có dạng full-speed khác.
Không dùng bảng nhúng thay cho descriptor đọc mới sau nạp hoặc bằng chứng endpoint hoạt động.

**License/revision — UNVERIFIED:** archive không có LICENSE/COPYING/README/NOTICE rõ ràng.
Việc hãng nhúng cùng ảnh trong SYS hỗ trợ kiểm tương hợp, không cấp quyền nhúng vào APK/Git.
[Datasheet](https://www.tbsiptv.com/download/tbs5530/tbs5530_multi_standard_universal_tv_tuner_box_data_sheet.pdf)
nói USB 2.0 và phụ kiện nguồn LNB 12 V tùy chọn; không đủ biết board thực có nguồn nào đang duy trì RAM.
Không coi 100 mA trong descriptor là phép đo nguồn, không bật LNB hoặc yêu cầu mở vỏ.

### Phép đo được chọn, recovery và ngưỡng phê duyệt

**RUNTIME-OBSERVED:** TShark/Wireshark/Dumpcap 4.6.9, USBPcapCMD/driver 1.5.4.0;
USBPcap service Running, TShark và extcap liệt kê USBPcap1–5. Shell không elevated.
`dumpcap -D` không liệt kê USBPcap; extcap của TShark có, nên không suy capture hỏng.
Cấu hình extcap đọc được các tùy chọn snaplen, buffer, devices và capture-from-new-devices;
chưa có mapping tuner→root hub hoặc phép thử capture/quyền ghi. Không thấy capture cũ trong các thư mục dự án/session đã tìm.

Kế hoạch tối thiểu gồm hai ngưỡng, không tự thực hiện:

1. Xin phép chuyển tuner từ OnePlus sang một cổng laptop do người dùng chọn, sau khi owner Android
   hết pending và được đóng an toàn. Việc cắm vào Windows có thể làm driver hãng tự nạp RAM ngay;
   đó là tác động thật phải được duyệt, không phải “chỉ liệt kê”. Xác nhận nguồn OTG và nguồn ngoài/LNB
   trước khi ngắt; không tự tháo nguồn hay đổi driver. Giữ Wireless ADB, không dùng usbipd/WSL.
2. Khi tuner đã ở Windows, chỉ đọc VID:PID/topology/root hub/device address và quyền capture.
   Sau khi có mapping cụ thể mới xin phép capture trên đúng interface, tối đa 30 giây/8192 KiB,
   snaplen 256 đủ ghi khối 64 byte. Tắt capture-all và descriptor injection; loại thiết bị đang có khác,
   chỉ theo dõi tuner và lần gắn mới đã duyệt trên hub. Nếu không cô lập được traffic, không capture.
   Cần thêm một reconnect đã duyệt để thu khởi tạo tự nhiên nếu lần cắm đầu đã qua; không tự mở app
   có thể tuning/frontend. Nếu BDA tự khởi tạo frontend, chỉ quan sát, không phát thêm lệnh từ agent.
   Không chạy ChangeMode, vendor probe, A0 IN, I²C hoặc thao tác chỉnh filter.

Phân tích offline theo `usb.urb_id`, request setup, wValue/wIndex/wLength, payload,
`usb.usbd_status`/`usb.urb_status` và timeline. Chỉ lọc `usb.setup.bRequest==0xa0` sẽ làm mất
completion không lặp setup; phải ghép URB ID trước khi lọc/xuất bảng.
ACK tại 7F92 không chứng minh CPU reset; STALL rồi E600 thành công sẽ hỗ trợ giả thuyết compatibility,
nhưng không chứng minh mọi board vô hại. Thiếu 7F92 chỉ có ý nghĩa nếu capture đầy đủ đường khởi tạo.
Windows trace không chứng minh đường controlTransfer Android đã chạy.

**Recovery có điều kiện:** kế hoạch A0 được mô hình hóa chỉ ghi RAM/CPUCS, không ghi EEPROM.
Nếu đúng FX2 và mọi nguồn nuôi RAM được loại bỏ, power-on sẽ tải lại cấu hình/EEPROM boot;
CPU reset hoặc USB reset không thay thế việc này. Chưa biết nguồn ngoài/backfeed và EEPROM của board,
nên không hứa “rút USB là chắc phục hồi”. Sau lỗi không gửi release/reset bổ sung hoặc thử plan thứ hai.
Ghi `UNCERTAIN`, chờ đánh giá nguồn và một thao tác vật lý được duyệt riêng; thu metadata/descriptors/quyền
mới sau reconnect, không tái dùng epoch/connection. Giấy phép firmware là điều kiện phát hành riêng,
không chặn phân tích nội bộ.

### Candidate và ranh giới phần mềm

`TBS_SOURCE_DUAL_RESET`: 7F92=01 → E600=01 → 128 DATA → 7F92=00 → E600=00 → delay.
`DOCUMENTED_FX2_E600_ONLY`: E600=01 → 128 DATA tại 0000..1FC0 → E600=00 → delay.
Cùng bmRequestType 40, bRequest A0, wIndex 0, timeout 2000 ms; reset payload 1 byte.
Hai plans là dữ liệu offline có provenance riêng; không có fallback hoặc quyền chạy plan nào.

Thêm comparator trong debug, giữ planner main/Session/Binding/Coordinator/Transport/facade production không đổi.
Dry-run chỉ nối fake trong tests, thêm preflight giả lập để kiểm mất Wireless ADB trước/sau mỗi bước;
không tuyên bố đây là guard Android đã triển khai. Gate vẫn false, facade `openBootstrap` vẫn từ chối.
Không tạo uploader/UI phần cứng khi hướng B còn thiếu trace và recovery.
[ADR-019](DECISIONS.md) thay dependency “chờ TBS” bằng phép đo có điều kiện; không xóa ADR-018 lịch sử.

### Bổ sung sau chuyển host được duyệt — 18:20 +07

**RUNTIME-OBSERVED:** sau người dùng tự chuyển từ OTG sang USB 2.0 Windows, PnP nhận TBS BDA
MEDIA/OK với driver 2.0.0.2 đã cài. USBPcap topology đặt tuner tại USBPcap3/address 4,
trong hub EHCI #2 có thêm webcam/Bluetooth. Chưa bắt trace, chưa biết status 7F92 hoặc việc firmware
đã chạy thành công. Một lần TCP ADB mất đã fail-stop; chỉ tiếp tục sau xác nhận nối lại và kiểm đúng
GM1900/API 30, owner không pending/open. Android dumpsys không còn tuner, report UI cũ không dùng làm
metadata mới. Nguồn chỉ OTG trước khi chuyển là thông tin người dùng xác nhận, không phép đo điện.

### Kết quả trace thật được duyệt — 18:38 +07

**RUNTIME-OBSERVED, Windows BDA 2.0.0.2, cùng board 734c:5530:** phép đo hướng B đã thu được dữ liệu
phân biệt giả thuyết. Lượt đầu 30 giây không kịp reconnect, file 296 byte/0 packets: **FAILED**,
không suy giao thức. Người dùng sau đó duyệt RIÊNG một lượt 180 giây/8 MiB và một reconnect mới.
Lượt hai có 3028 packets, 232084 byte, SHA-256 pcapng
`e3220b64da4adac60862406569ef5151c430f050f6b179096a925b803e913f4f`,
đầu/cuối packet 18:30:59.221881–18:31:04.273827 +07. Tất cả ở bus 3/address 4, không frame bị cắt.
Raw capture chứa firmware nên chỉ ở session local, không đăng hoặc đưa vào Git.

| Lượt tự nhiên của BDA | Bước | Frame request/completion | Payload | Completion USBD_STATUS |
|---|---|---|---|---|
| 1 | A0/7F92 stop | 9/10 | 01 | 00000000 SUCCESS |
| 1 | A0/E600 stop | 11/12 | 01 | 00000000 SUCCESS |
| 1 | 128 DATA, 0000–1FFF | 13–268 | 8192 byte, hash khóa | Tất cả SUCCESS |
| 1 | A0/7F92 release | 269/270 | 00 | 00000000 SUCCESS |
| 1 | A0/E600 release | 271/272 | 00 | 00000000 SUCCESS |
| 2 | A0/7F92 stop | 301/302 | 01 | 00000000 SUCCESS |
| 2 | A0/E600 stop | 303/304 | 01 | 00000000 SUCCESS |
| 2 | 128 DATA, 0000–1FFF | 305–560 | 8192 byte, hash khóa | Tất cả SUCCESS |
| 2 | A0/7F92 release | 561/562 | 00 | 00000000 SUCCESS |
| 2 | A0/E600 release | 563/564 | 00 | 00000000 SUCCESS |

Cả 264 A0 submissions được ghép completion đầy đủ bằng **usb.irp_id + bus/address + frame request**.
USBPcap dùng `usb.irp_id`, không phải `usb.urb_id` (trường sau trống ở capture này).
IRP ID được tái dùng qua nhiều requests, nên không join toàn bộ file chỉ bằng ID.
`usb.request_in` kiểm đúng frame đối ứng, status phải lấy ở completion, không lấy giá trị submission.
Dựng lại hai ảnh DATA đều đúng 8192 byte/hash khóa, không padding/bỏ zero, không có A0 pending.
Parser Java debug và kiểm Node độc lập cùng cho kết quả; fixture tests không chứa capture/firmware thật.

**Giả thuyết được giải quyết:** “7F92 luôn STALL trên board này” bị phản bác trong điều kiện quan sát:
cả bốn writes 7F92 có completion SUCCESS. Không còn dùng việc thiếu xác nhận alias từ TBS làm blocker.
**Điều chưa được chứng minh:** SUCCESS không đo CPU state hoặc chứng minh 7F92 là CPUCS/alias;
không chứng minh mọi board/revision, E600-only trên board hoặc Android control path.

**Chuyển descriptor:** frame 6 trước lượt 1 có alt0–3 và endpoints default 01/81/02/04/86/88,
không82. Frame 298, khoảng 1,939 giây sau đầu trace và trước lượt 2, trả config mới alt0 với IN82/81.
Đây là bằng chứng descriptor thực trên Windows, không chỉ bảng nhúng trong firmware.
VID:PID vẫn 734c:5530 và USB address vẫn 4; không suy cùng connection/epoch hoặc không có disconnect.
Sau lượt 1, Windows tự gửi vendor IN B7 mười lần, completion `c0000011`; không phải A0 và không có
lệnh đó do agent gửi. Có vendor 90/8A/80 tự nhiên sau đó. Không xem Windows tự khởi tạo frontend là
phê duyệt agent gọi frontend hoặc bằng chứng DVB-T2/TS.

**Vì sao vẫn giữ hướng B cho Android, không tự nâng thành A:** driver hãng tự phát hai lượt upload
trong một reconnect; agent không retry hoặc ra lệnh lần hai. Trace cho thấy descriptor đã đổi trước
lượt 2, nhưng chưa biết lý do driver mở/init lần hai, tính đầy đủ của bridge sau đúng một lượt hoặc
điều kiện Android không phụ thuộc nhánh B7/90/8A/80 của BDA. Candidate thực nghiệm Android hợp lý
nhất hiện là `TBS_SOURCE_DUAL_RESET` (132 writes), vì đã quan sát trên chính board; không chọn
E600-only chỉ vì ít lệnh hơn. E600-only vẫn là chuẩn chip nhưng chưa đo trên board.
Không cần xác minh alias để nghiên cứu tiếp; điều kiện tiếp theo là kế hoạch one-shot Android tách khỏi
retry của BDA, nhận diện lại/observer sau nạp và phê duyệt return-host/upload riêng.
Không được biến lần BDA thứ hai thành mẫu auto-retry Android.

**Cleanup capture:** TShark đã dừng theo giới hạn; pipeline còn worker elevated nên script exit 1
ở deadline, không phải parser hoặc firmware transfer fail. Dừng parent thường thành công; dừng worker
PID cụ thể bị Access denied. Người dùng đã dừng worker bằng quyền Administrator; kiểm lại không còn
TShark/USBPcap process. Không restart service/filter hoặc thay driver. Hai trace không có write từ agent.
Tuner hiện ở Windows, chưa trả về OnePlus; Android firmware/READY/TS vẫn **NOT RUN**.

Câu hỏi TBS tùy chọn: chip/PCB revision; semantics A0/7F92; lý do helper Windows bỏ status đầu;
EEPROM/nguồn RAM và recovery; ảnh/hash cho revision; quyền phân phối. Chưa liên hệ hãng.
Firmware/CPU reset/vendor IN hoặc OUT/I²C do agent chủ động gửi, Android upload/tuning/TS: **NOT RUN**.
Các lệnh tự nhiên của BDA đã được ghi rõ trong bảng runtime phía trên, không phải Android bring-up.

## 2026-11-04 — Phase 2.7: fingerprint mặc định FX2, firmware chính hãng và giới hạn nạp

### Baseline và nguồn

Baseline Phase 2.6 đã push: `f03e092702edac5134efea633d8a30a51e1716e4`, nhánh `chore/modernize-android-build`; local/remote-tracking cùng SHA, working tree sạch lúc bắt đầu. Không fetch/sync/checkout/sửa references, không stage/commit/push. Official Linux vẫn `2ce787de9f6e81b4294692a45de15596ae2e1322`, C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`.

GitNexus không có index cho repository này; không dùng graph của dự án khác hoặc tự chạy index tốn kém. AgentMemory recall không có kết quả liên quan. Research agent không khởi chạy được vì model cấu hình không khả dụng; nghiên cứu được thực hiện trực tiếp, không có báo cáo agent để nhận là chứng cứ.

### SOURCE-CONFIRMED — descriptor FX2 và ý nghĩa của fingerprint

URL PDF người dùng đưa (`/24/44/`) trả nội dung rỗng khi tải. Bản cùng tên **EZ-USB FX2 TRM v2.2**, 462 trang, có tại [Infineon `/30/44/`](https://www.infineon.com/assets/row/public/documents/30/44/infineon-ez-usb-fx2-trm-version2-2-usermanual-en.pdf), tải HTTP 200, 5790150 byte bằng `Invoke-WebRequest`. `curl.exe` trả file rỗng ngay cả URL này nên không dùng exit 0 làm bằng chứng nội dung. Trích text bằng `pdftotext.exe -raw` và `-layout` đã có trên máy, không cài package.

Mục 3.3/Table 3-2 và Appendix B/B-1–B-25 khớp các trường tiêu chuẩn trong snapshot OnePlus Phase 2.6, không chỉ VID/PID hoặc hash. Đây là **đối chiếu mới với runtime-observed lịch sử**, không phải một lần đọc USB mới.

| Alternate / interface 0 | Address, hướng, loại, packet, interval: TRM và snapshot OnePlus cùng giá trị |
|---|---|
| 0 | Không endpoint ngoài EP0 |
| 1 | 01 OUT Bulk 512/0; 81 IN Bulk 512/0; 02 OUT Bulk 512/0; 04 OUT Bulk 512/0; 86 IN Bulk 512/0; 88 IN Bulk 512/0 |
| 2 | 01 OUT Interrupt 64/1; 81 IN Interrupt 64/1; 02 OUT Interrupt 512/1; 04 OUT Bulk 512/0; 86 IN Interrupt 512/1; 88 IN Bulk 512/0 |
| 3 | 01 OUT Interrupt 64/1; 81 IN Interrupt 64/1; 02 OUT Isochronous 512/1; 04 OUT Bulk 512/0; 86 IN Isochronous 512/1; 88 IN Bulk 512/0 |

Các trường device đều khớp: bcdUSB=0200, class/subclass/protocol=FF, packet EP0=64 byte và 1 configuration. Configuration có độ dài 171 byte, value=1, 1 interface, attributes=80 và power=50. Interface có class/subclass/protocol=FF, với alt 0–3. Không so VID/PID, bcdDevice hoặc string indices với ID mặc định Cypress: mục 3.4.2 cho phép EEPROM C0 thay VID/PID/DID. TRM lưu ý EP1 có bộ đệm vật lý 64 byte dù descriptor HS alt 1 khai báo 512 byte; không dùng bảng này để bắt đầu bulk transfer.

`Tbs5530Fx2Fingerprint` trả `MATCH`/`PARTIAL_MATCH`/`NO_MATCH` và liệt kê từng trường khác. `MATCH` chỉ gán `BOOTSTRAP_CANDIDATE`. Descriptor hoàn chỉnh có profile Bulk IN `0x82`, interface 0 và alternate 0 mới có thể là `OPERATIONAL_CANDIDATE`, **không phải READY**. Cây mô tả sai định dạng hoặc bị cắt không đủ bằng chứng. Fixture (dữ liệu mẫu kiểm thử) được dựng lại từ trường số đã ghi trong hồ sơ; không lưu chuỗi USB hoặc raw bytes đã thu từ thiết bị. Không có tham số Android model/API, nên cùng descriptor cho cùng kết quả.

**INFERRED, không chứng minh:** fingerprint phù hợp Default High-speed Device, nhưng không xác nhận chip revision, EEPROM C0 thay vì C2/firmware mô phỏng descriptors, RENUM hiện tại, CPU đang chạy, alternate active hoặc negotiated speed. Mục 3.4: C0 chỉ tải ID/config; C2 có các record length/address/data và chạy firmware từ RAM, không phải ảnh tuyến tính. Mục 3.8/3.9: A0 là firmware request, RENUM/DISCON quyết định phản hồi và tái nhận diện. Không suy `.no_reconnect=1` của Linux bảo đảm Android giữ attachment/quyền.

### SOURCE-CONFIRMED — archive và ảnh thật, ngoài Git

Ngày truy cập 2026-11-04. [TBS Linux installation](https://www.tbsiptv.com/help_center/tbs_driver_installation/linux.html) vẫn công bố gói v1.0 qua HTTP. Đã chọn **HTTPS cùng hostname/path**, không thay nhà cung cấp: [archive TBS](https://www.tbsdtv.com/download/document/linux/tbs-tuner-firmwares_v1.0.tar.bz2). Download center và tìm kiếm official không cho thấy một firmware 5530 mới hơn có version/hash được nhà sản xuất xác thực; không gọi gói v1.0 là bản mới nhất tuyệt đối.

| Nội dung | Kích thước | SHA-256 |
|---|---:|---|
| Archive v1.0 | 566825 byte | `23ecf817383dc29881ac9cb6f704239969e5f711270ad2073497653ba08791cc` |
| Entry `./dvb-usb-id5530.fw` | 8192 byte | `b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc` |

Archive có 54 entries; không thấy LICENSE/COPYING/README/NOTICE. Chỉ trích đúng entry cho TBS5530 vào session local ngoài repository bằng `tar -xjf <ARCHIVE> -C <LOCAL_OUT> ./dvb-usb-id5530.fw`; tính hash bằng `Get-FileHash -Algorithm SHA256`. Không đổi tên HEX/IIC thành firmware, không thực thi blob hoặc đưa vào assets/APK/Git. Tên ảnh do driver TBS5530 chỉ định xác nhận họ board ở mức source; không có manifest, chữ ký, version hoặc revision đủ để khẳng định đúng revision vật lý. SHA là dấu nhận diện của nội dung tải, không phải chữ ký TBS.

**Format:** source Linux/C/Go đọc toàn ảnh tuyến tính từ offset 0, không dùng parser Intel HEX/EEPROM. Ảnh dài 8192 byte; lệnh đầu tại reset vector là 8051 LJMP đến địa chỉ 08FE. Dữ liệu phù hợp ảnh RAM tuyến tính, không phải record C0/C2 hoặc văn bản HEX. Không coi reset vector hoặc tên file là bằng chứng xác thực ý nghĩa của mọi lệnh firmware. Toàn bộ địa chỉ dự kiến ghi là 0000–1FFF, gồm 128 khối × 64 byte; khối cuối tại 1FC0 dài 64 byte. Không chèn padding hoặc bỏ phần đuôi. Với dữ liệu tổng hợp dài 1/63/64/65/128 byte, planner/executor kiểm khối cuối đúng độ dài.

**License/distribution: BLOCKED/NOT VERIFIED.** Trang TBS hướng dẫn tải/cài cho DVB adapters, là cơ sở cho bản tải phục vụ phân tích và sử dụng dự kiến; không có giấy phép riêng xác nhận quyền tái phân phối/nhúng APK. GPL của loader không cấp phép blob. Không đưa image/archive vào Git hoặc bản phát hành; cần TBS xác nhận quyền và board revision trước kế hoạch phân phối.

### Address map, CPU reset và hardware gate

[Official `tbs5530_load_firmware`](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/usb/dvb-usb/tbs5530.c), [C cùng hàm](https://github.com/koreapyj/cxd28xx/blob/c5fa27eecbe80633cbb365cc44656daea1c0e2e3/tbs5530.c), [Go `Bridge.LoadFirmware`](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/bridge/tbs/tbs.go) đều có trình tự ghi cả hai địa chỉ. C không kiểm kết quả trả về khi giữ CPU ở reset và luôn yêu cầu 64 byte ở vòng cuối. Go kiểm đủ byte và phần đuôi, nhưng giới hạn E200 và comment không phải bằng chứng về bản đồ RAM FX2. Android không sao chép giới hạn này làm vùng địa chỉ cho phép.

TRM mục 3.8 xác định **E600 là CPUCS của FX2**, bit 0 điều khiển 8051RES. A0 là request đặc biệt luôn do FX2 xử lý bất kể RENUM; CPUCS là thanh ghi FX2 duy nhất được tài liệu cho phép ghi bằng lệnh firmware download. Chương 5 xác định Main RAM tại 0000–1FFF và scratch RAM tại E000–E1FF; scratch RAM không thực thi code. Các vùng E200–E3FF/E480–E5FF/E700–E73F/E800–EFFF được dành riêng; control/status tại E600–E6FF, GPIF tại E400–E47F và các buffer khác không phải vùng nạp ảnh RAM. Ảnh 8192 byte vừa đúng Main RAM; không byte DATA nào chạm 7F92/E600 hoặc vùng ngoài 0000–1FFF. Planner vẫn cấm cả hai địa chỉ trong DATA, kiểm giới hạn, tràn địa chỉ và hash trước khi xây trình tự.

[Infineon knowledge-base về memory](https://community.infineon.com/t5/Knowledge-Base-Articles/Read-and-Write-to-EZ-USB-Internal-Memory-AN21xx-FX-FX1-FX2-FX2LP/ta-p/259431) và [older FX/AN21xx scripting](https://community.infineon.com/t5/Knowledge-Base-Articles/Generating-script-file-for-the-older-EZ-USB-series-FX-AN21xx/ta-p/248487) phân biệt 7F92 của họ FX/AN21xx cũ với E600 của FX1/FX2/FX2LP. Công cụ tìm kiếm trả nội dung, nhưng tải trực tiếp bài đầu nhận lỗi 403. Đây là giới hạn truy cập nguồn, không phải phép đo trên board. **Chưa chứng minh 7F92 là địa chỉ CPUCS tương đương trên FX2; chưa biết hậu quả khi gửi request A0 đến 7F92 trên board thực.** Không âm thầm bỏ 7F92, cũng không gửi cả hai chỉ vì source làm vậy. Cần bằng chứng về chip/board từ TBS và phương án khôi phục đã được rà soát trước khi cho phép thực thi.

Trình tự **mô hình offline theo source**, không được phép gửi lên phần cứng:

1. bmRequestType=40, bRequest=A0, wIndex=0, timeout=2000 ms; wValue=7F92, payload=01 dài 1 byte.
2. Cùng request, địa chỉ E600, payload=01 dài 1 byte.
3. 128 requests, wValue `64*n` với n=0..127; payload là đúng phần ảnh tương ứng, dài 64 byte; địa chỉ bắt đầu các khối từ 0000 đến 1FC0.
4. Ghi payload=00 dài 1 byte đến 7F92, rồi đến E600, với cùng các trường request.
5. Delay 100 ms. Tổng cộng 132 control writes và 1 delay; fake kiểm đầy đủ từng byte, không gọi API USB thật.

Mọi lỗi đều dừng ngay; không retry, tự khắc phục, reset hoặc release CPU sau lỗi. Kể cả khi API trả đủ mọi byte, trạng thái chỉ là `TRANSITIONING`. Chưa có bằng chứng độc lập từ attachment/descriptors mới hoặc bridge, nên không báo `READY` hay operational hardware `PASS`. Sau download, USB có thể disconnect hoặc không; VID/PID có thể giữ nguyên. Không được dùng lại handle, số phiên gắn thiết bị hoặc quyền cũ. Nếu lỗi giữa trình tự, tình trạng RAM/CPU là `UNCERTAIN`; không gửi thêm lệnh ghi khắc phục. Việc tháo/cấp nguồn thủ công phải thuộc kế hoạch khôi phục được đánh giá riêng sau này. Không giả định power cycle luôn khôi phục đúng board khi EEPROM/mode chưa được xác minh.

### Kết luận Phase 2.7

Kiểm tra ảnh thật và dry-run trên fake đều là **offline**, không cấp quyền upload. Chương trình local chạy chính ảnh TBS qua fake port: 132 calls kiểm từng byte/request/thứ tự, 1 open/1 close/0 claim và delay giả lập 100 ms; không dùng UsbDeviceConnection. `hardwareUploadAllowed=false`; production facade `openBootstrap` mặc định từ chối. Không có bộ nạp Android hoặc điểm vào upload thủ công. Câu hỏi phê duyệt upload **chưa được đưa ra** vì còn thiếu bằng chứng về 7F92 và phương án khôi phục board. Firmware/CPU/vendor/I²C/tuner/TS thật đều **NOT RUN**. Bước tiếp theo duy nhất: xác minh với TBS cách ánh xạ 7F92, chip/revision board, phương án khôi phục và quyền firmware; sau đó mới rà soát lại kế hoạch phần cứng. Không tự bắt đầu frontend.

## 2026-11-03 — Phase 2.6: đối chiếu USB mode và đọc descriptor không ghi thiết bị

### Baseline và phân loại bằng chứng

Phase thống nhất, tiếp nối checkpoint Phase 2.5 đã push tại `df076d28dd8100d5caa363f1d866a274e3f2b636`, nhánh `chore/modernize-android-build`. SHA local và remote-tracking cùng giá trị; working tree sạch lúc đầu. Không fetch/reset/merge/commit/push hoặc sửa references. Kết quả 180/44 của Phase 2.5 là lịch sử, không phải kết quả mới.

Ba checkout đã đối chiếu trực tiếp: TBS `2ce787de9f6e81b4294692a45de15596ae2e1322`; community C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`; Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`. Không có chỉ mục AndroidDvbDriver trong GitNexus; dùng source hiện tại thay vì gọi graph của repo khác. AgentMemory không tìm thấy kết quả phù hợp. Tác vụ research phụ không khởi chạy được vì model mặc định không khả dụng; nghiên cứu được thực hiện trực tiếp, không coi đó là báo cáo đã hoàn tất của agent.

### Source-confirmed: trình tự driver không phải phép đo trạng thái firmware

| Nguồn khóa và điểm vào | Trình tự đã đọc từ source | Giới hạn kết luận |
|---|---|---|
| [Official tbs5530.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/usb/dvb-usb/tbs5530.c), `tbs5530_probe`, `tbs5530_properties`, `tbs5530_load_firmware` | Probe gọi `dvb_usb_device_init`; 734c:5530 nằm trong `cold_ids`, `warm_ids` rỗng, không `identify_state`; `.no_reconnect=1`; firmware `dvb-usb-id5530.fw`; TS Bulk IN82, generic bulk control 81 | “Cold” là nhánh chọn theo bảng ID của driver; không đo được firmware đang chạy trên OnePlus. `no_reconnect` cho phép source tiếp tục sau download, không bảo đảm Android không re-enumerate |
| [Official dvb-usb-init.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/usb/dvb-usb/dvb-usb-init.c), `dvb_usb_find_device`, `dvb_usb_device_init` | Đối chiếu cold/warm IDs; nếu cold thì download; khi `no_reconnect` và download thành công thì tiếp tục init | Không có phép kiểm firmware riêng cho board này. Không dùng tên log “warm” sau download làm bằng chứng runtime Android |
| [Community tbs5530.c](https://github.com/koreapyj/cxd28xx/blob/c5fa27eecbe80633cbb365cc44656daea1c0e2e3/tbs5530.c), `tbs5530_probe`, `tbs5530_load_firmware`, `tbs5530_start_streaming` | Probe luôn load cùng tên firmware trước I²C/frontends; ring URB dùng IN82; nguồn TS được chọn bằng 8A `[0A,active_fe]` | Không có phát hiện cold/warm độc lập hoặc bảng descriptor trước/sau; không có bằng chứng 88 là TS |
| [Go board](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/board/tbs5530/tbs5530.go), `open`; [TBS bridge](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/bridge/tbs/tbs.go), `LoadFirmware` | Mở transport, đọc firmware, load rồi initialize frontend; reader dùng IN82. Loader giới hạn 0xe200, chia khối cuối chính xác, kiểm short write | Nhận xét “mỗi open reload” là mô tả của Go; không chứng minh EEPROM hoặc USB mode của board đang cắm. Cap không thay address-map/firmware approval |

Official và C dừng CPU bằng request A0, value 7F92 rồi E600, payload 01; ghi ảnh tuyến tính từ address 0 theo khối 64; restart cùng địa chỉ với payload 00; đợi 100ms. Cả hai vòng C yêu cầu 64 bytes ngay cả khối cuối, không dùng làm mẫu an toàn cho ảnh không chia hết 64. Go dùng khối cuối ngắn đúng và kiểm lỗi từng bước. Không thực thi bất kỳ bước nào. Ảnh được source xử lý như binary tuyến tính, không có Intel HEX parser; không tìm thấy blob `*5530*.fw` trong ba cây, vì vậy hash/format thực và giấy phép phân phối firmware **NOT VERIFIED**. GPL của source không tự cấp quyền phân phối blob. Không tải firmware hoặc đóng gói APK/Git.

Không thấy `usb_set_interface` trong board official/community đã đọc hoặc các file `dvb-usb*.c`; đây không phải bằng chứng USB alternate đang active. Go Linux `pkg/usbio/usbfs/usbfs_linux.go::claim` claim interface number 0; nếu ENOENT có nhánh chọn configuration đầu rồi claim lại, là side effect không được sao chép sang diagnostic. API `SetAlternate` tồn tại nhưng không có trong đường board `open` đã đọc. Không chạy claim/configuration/alternate.

[Official cxd2878.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/dvb-frontends/cxd2878.c), `cxd2878_attach`: sau khi bridge firmware/I²C adapter sẵn sàng, ghi bank 00 tại SLV-X rồi đọc FB và FD, ghép ID 10 bit. “Đọc chip ID” không phải read-only USB descriptor: cần cả vendor/I²C writes và không thuộc phê duyệt Phase 2.6.

### Source-confirmed: TBS5530 ChangeMode Tool, không thực thi

Ngày 2026-11-03, [download chính hãng](https://www.tbsdtv.com/download/index.html?path=4&id=164) có mục **TBS5530 ChangeMode Tool**, trỏ đến [ZIP chính hãng](https://www.tbsiptv.com/download/tbs5530/TBS5530_ChangeMode_Tool.zip). Trang [sản phẩm](https://www.tbsdtv.com/products/tbs5530-multi-standard-universal-tv-tuner-usb-card.html) mô tả USB2.0 và nhiều chuẩn thu; không công bố giao thức đổi mode, descriptor trước/sau hoặc rollback.

Chỉ đọc ZIP và các chuỗi tĩnh trong RAM bằng .NET `HttpClient`/`ZipArchive`; không giải nén file executable xuống đĩa, không khởi chạy hoặc nạp thư viện, không chuyển tuner sang Windows. ZIP 213579 bytes chứa đúng một `TBS5530_ChangeMode_Tool.exe` 522728 bytes, không README/manual. SHA-256 ZIP `c6555841ca048da247692c7200267adf22a1c24bfe56a66c0a00a0a2e7aee6c2`; entry EXE `6da03e94ef1d570c2a4dbccef3a529bf2a56b8cb5e0f5b2ecacf5eddd773afcd`. Hash nhận diện nội dung tải trong lượt nghiên cứu, không xác nhận chữ ký/publisher hoặc độ an toàn để chạy.

Các chuỗi tĩnh ngắn có `DVB-S/S2`, `DVB-T/T2`, `TBS 5530 BDA Tuner`, `TBS 5530 BDA Capture`, `No TBS DVBS device!` và thông báo đợi khoảng 5 giây sau khi set. **Source-confirmed ở mức nội dung binary/UI:** công cụ có dấu hiệu liên quan lựa chọn chuẩn thu và Windows BDA. **Hypothesis:** đổi routing/mode thiết bị qua driver. Chưa có bằng chứng nó đổi firmware RAM, EEPROM, USB operating mode, descriptor, endpoint 82/88 hoặc hỗ trợ rollback. Không suy ra các điều này từ tên tool/chuỗi UI. Cần tài liệu nhà sản xuất hoặc phân tích giao thức riêng được duyệt; execution **NOT RUN**.

### Cách đọc descriptor và giới hạn Android

- Interface **ID** là `bInterfaceNumber`; **index** chỉ vị trí trong mảng API Android; **alternate setting** là lựa chọn descriptor cho cùng ID. `bNumInterfaces` đếm số interface, không đếm các alternate. Android có thể liệt kê nhiều `UsbInterface` cùng ID; không phải lỗi chỉ vì count=4 và ID=0 ở tất cả mục.
- Configuration ID từ Android là `bConfigurationValue`, khác index. `bcdUSB` là phiên bản USB khai báo, `bcdDevice` là phiên bản thiết bị khai báo; không trường nào tự là firmware-running flag. `wTotalLength` là độ dài cây configuration, không bao gồm device descriptor. Packet/interval phải gắn đúng transfer type và tốc độ, không suy negotiated speed chỉ từ 512 bytes.
- [API getRawDescriptors](https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection#getRawDescriptors()) trả dữ liệu descriptor thô. [AOSP Android 11 JNI](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/core/jni/android_hardware_UsbDeviceConnection.cpp), `android_hardware_UsbDeviceConnection_get_desc`, dùng `lseek/read` trên fd với buffer 16384 bytes, không tự gọi controlTransfer trong hàm này. Đây là source AOSP, chưa chứng minh implementation OxygenOS hoàn toàn giống; không cam kết API luôn trả toàn bộ tree hoặc dữ liệu mới được đọc trực tiếp từ bus.
- So sánh raw tree với metadata mới giúp kiểm tra khả năng parser Android bỏ/đổi địa chỉ. Có alternate trong mảng không chứng minh alternate đó đang active. Chưa có `GET_INTERFACE`/`GET_CONFIGURATION`, vì controlTransfer nằm ngoài phạm vi.
- Trước/sau firmware hoặc USB mode, device/config/interface/endpoint descriptors **có thể** thay đổi nếu firmware triển khai khác; không mặc định sẽ đổi VID/PID, không mặc định connection/permission tồn tại sau re-enumeration. Bằng chứng cần hai snapshot cùng board và preconditions rõ, chỉ sau một phép thay đổi đã duyệt.

### Runtime và bảng so sánh

Raw cycle trên thiết bị thật: **NOT RUN tại thời điểm bổ sung mục nghiên cứu này**, chờ kiểm thử mã cuối và phê duyệt một lần. Không dùng metadata cũ như raw bytes mới. Kết quả thực được bổ sung bên dưới sau phép đọc được duyệt.

| Trường | OnePlus Phase 2.5, runtime-observed lịch sử | WSL2 snapshot lịch sử | Driver official / community / Go | Hypothesis và phép phân biệt |
|---|---|---|---|---|
| VID/PID | 734c:5530 | 734c:5530 | Match cùng ID | Không phân biệt cold/warm |
| Configuration | Count 1, value 1 | Không có đủ trường trong hồ sơ | Không có raw tree mẫu | Cần raw bNumConfigurations/value/wTotalLength |
| Interface | ID0, index0–3, alternate0–3 | Interface0; alternate tree chưa lưu | Board source không công bố đầy đủ descriptors | Android flatten alternates là biểu diễn hợp lệ; so raw để loại parser omission |
| Endpoint | Alt0 rỗng; alt1 Bulk01/81/02/04/86/88; alt2/3 hỗn hợp | Bulk IN81/82, packet512 | TS82 ở cả ba; official generic ctrl81 | 02 OUT không phải82 IN;88 không tự là TS; raw giúp xác nhận địa chỉ |
| bcdUSB/bcdDevice/wTotalLength | Chưa đo ở Phase 2.5 | Không lưu trong bằng chứng đang có | Không thể suy từ hằng endpoint | Cần raw; không bịa số cho WSL để lấp bảng |
| Firmware/mode | Chưa xác minh | Không có trước/sau loader và hash tương ứng | Loader ghi firmware ở probe/open | Còn khả năng pre-firmware/default mode, variant hoặc state khác; chưa chọn giả thuyết |


### Runtime-observed mới: raw cycle đã duyệt, 2026-11-04 khoảng 00:00 +07

Người dùng duyệt đúng một chu kỳ open/getRawDescriptors/close. Trước đó phần mềm mới đạt 199 JVM methods và 47 instrumentation tests trên OnePlus/API 30, debug/release/lint PASS; không dùng số 180/44 lịch sử. Có một lần Wireless ADB mất serial TCP cũ trong lúc sửa code, đã dừng ngay và chỉ tiếp tục sau xác nhận người dùng, tra port mới, cold launch owner mới, kiểm Host/attachment/permission/pending; không tái dùng handle/generation cũ.

Permission-only operation1/generation1: người dùng tự Allow; **callback_count=1, correlation_device_matched=true**, state trước cleanup PERMISSION_GRANTED, sau CLOSED, 0 open/claim/close, pending=false. Đây là bằng chứng mới của callback thật, khác lượt deadline của Phase 2.5. Làm mới metadata xác nhận permission=true, đúng 734c:5530, không acquisition/cleanup pending trước raw.

Raw operation2/generation2: **READ_AND_CLOSED**, worker `tbs5530-usb-worker`, 13ms; `openDevice=1/1`, `getRawDescriptors=1/1`, `close=1/1`, claim/release/transfer=0, attachment_identity_matched=true, permission=true, invalidated=false, pending=false, cleanup_pending=false, failure=NONE. Dữ liệu trả về **189 bytes**, SHA-256 `e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`; parser `COMPLETE_RETURNED_TREE`, issues rỗng. Chỉ lưu báo cáo đã lọc trong app-private và session, không lưu raw bytes/string/serial/path vào Git. JSON có SHA baseline và nhãn working-tree chưa được SHA chứng thực, không giả đó là binary của commit đã push.

| Trường raw | Giá trị runtime | Ý nghĩa/giới hạn |
|---|---|---|
| Device descriptor | Length18; bcdUSB=0x0200; bcdDevice=0x0001; class/subclass/protocol=FF/FF/FF; EP0 packet64; VID/PID734c:5530; bNumConfigurations1 | bcdDevice là 0.01 dạng BCD, không phải phép đo firmware version đang chạy |
| Configuration descriptor | Offset18, length9, wTotalLength171; bNumInterfaces1; bConfigurationValue1; bmAttributes80; bMaxPower50 đơn vị 2mA = 100mA | 18+171=189: toàn bộ tree được trả về ở lượt này. Công suất là khai báo descriptor, không phải đo dòng hoặc nguồn thực |
| Interface descriptors | Offset27 alt0 endpoint0; offset36 alt1 endpoint6; offset87 alt2 endpoint6; offset138 alt3 endpoint6; tất cả bInterfaceNumber0, class/subclass/protocolFF | Một interface có bốn alternate; không suy active alternate từ danh sách |

| Alternate/ID | Raw endpoints: address(hex), direction, type, wMaxPacketSize(bytes), bInterval |
|---|---|
| 0 / 0 | Không endpoint |
| 1 / 0 | 01 OUT Bulk512/0;81 IN Bulk512/0;02 OUT Bulk512/0;04 OUT Bulk512/0;86 IN Bulk512/0;88 IN Bulk512/0 |
| 2 / 0 | 01 OUT Interrupt64/1;81 IN Interrupt64/1;02 OUT Interrupt512/1;04 OUT Bulk512/0;86 IN Interrupt512/1;88 IN Bulk512/0 |
| 3 / 0 | 01 OUT Interrupt64/1;81 IN Interrupt64/1;02 OUT Isochronous512/1;04 OUT Bulk512/0;86 IN Isochronous512/1;88 IN Bulk512/0 |

`wMaxPacketSize` không có bits additional transactions trong các endpoint trả về; tất cả bằng0. Không có IN82 ở bất kỳ alternate. Raw và UsbManager khớp từng interface/alternate/address/type/packet/interval. **Loại trừ trong snapshot trả về:** giả thuyết chỉ API metadata Android đổi OUT02 thành IN82, làm mất IN82 khi biểu diễn alternate, hoặc một endpoint82 đang ẩn trong tree đã trả về. **Chưa loại trừ:** kernel/framework cache khác descriptor live trên bus; trạng thái firmware/mode hoặc variant khác snapshot WSL; ảnh firmware Windows/Linux khác nhau. Không gọi vendor/control để làm mới descriptor và không tuyên bố raw API là phép đo bus mới.

Bảng so sánh phía trên được bổ sung bằng các trường raw vừa đo: WSL không có bản tree/hash/preconditions tương ứng trong hồ sơ, vì vậy không thể so bcdUSB/bcdDevice/wTotalLength từng byte hoặc chứng minh transition từ snapshot đó. Ba source vẫn dùng82; không có bản descriptor sample hoặc mapping88 trong nguồn đã kiểm. **Firmware cold/warm và USB operating mode vẫn UNKNOWN; streaming/firmware bring-up BLOCKED**. Read-only inspection hoàn tất không tháo bỏ production guard IN82.

### Phép thử tiếp theo có căn cứ, chưa được phép thực thi

1. Xin TBS tài liệu ChangeMode: tác dụng lên BDA delivery system, EEPROM/RAM, descriptor, USB reconnect, cơ chế khôi phục và mode kỳ vọng cho Linux/Android. Giữ ZIP/EXE hash nghiên cứu, không chạy tool để đoán.
2. Xác minh nguồn, giấy phép, SHA-256, kích thước và address map của ảnh firmware chính hãng; kiểm vùng DATA không đè7F92/E600, khối cuối, rollback/mất nguồn. Không coi tên file hoặc GPL source là đủ.
3. Lập kế hoạch được duyệt riêng cho snapshot trước/sau cùng board/nguồn/cáp, identity/permission sau re-enumeration, single owner và dừng ngay khi lỗi; tách authorization firmware, CPU reset, vendor và I²C. Chưa thực hiện trong Phase2.6.
4. Chỉ cho phép bridge/chip-ID bring-up sau khi mode/firmware phù hợp được xác minh và transport production có đúng Bulk IN82 hoặc một quyết định profile mới có bằng chứng độc lập. Không dùng88 thay82 để qua gate; chip-ID cần vendor/I²C writes riêng, không tự chuyển phase.

## 2026-11-03 — Phase 2.5: OnePlus 7 USB Host thực và profile descriptor chưa phù hợp

### Kết nối và baseline

Nhánh `chore/modernize-android-build`, HEAD `dc50e7304941a7d01aa1d490c13fcf09683a01c9`, sạch khi bắt đầu. Phase 2.4B, diagnostic debug và tài liệu đã push tại SHA này. Lần bắt đầu 21:28, Wireless ADB báo offline: dừng ngay trước code/build/USB. Chỉ tiếp tục sau khi người dùng xác nhận kết nối lại 21:34, kiểm serial TCP mới ở trạng thái device, OnePlus/GM1900/API 30. Có thêm alias mDNS của cùng điện thoại; chỉ chọn một target tường minh, không emulator. Guard kiểm lại model/API/online trước thao tác và mỗi 10s trong Gradle, không hardcode port cũ.

Runtime xác nhận Android 11/API 30, model GM1900, build `GM1900_14_220617`, OTA `OnePlus7Oxygen_14.P.45_GLO_0450_2206171433`. OxygenOS 11.0.9.1.GM57AA là thông tin người dùng cung cấp; property ro.oxygen.version không có giá trị nên không gọi đó là version vừa đọc được. Feature USB Host=true, UsbManager/dumpsys usb thấy 734c:5530 liên tục; không phải passthrough/emulator, không dùng WSL/usbipd. Không cần bật OTG hoặc sửa system properties vì đang enumerate được.

### Descriptors mới đo trên Android

Báo cáo JSON app-private và dumpsys usb khớp: VID 734c/PID 5530, một configuration ID 1, bốn interface entries đều cùng ID 0. Đây là bốn alternate descriptors, **không phải bốn interface vật lý riêng**. Class/subclass/protocol 255/255/255. Không đọc serial, không getRawDescriptors hoặc mở connection.

| Config/index | Interface index | Interface ID | Alternate | Endpoints (address, direction, type, packet, interval) |
|---|---|---|---|---|
|1/0|0|0|0|Không có endpoint |
|1/0|1|0|1|01 OUT Bulk 512/0; 81 IN Bulk 512/0; 02 OUT Bulk 512/0; 04 OUT Bulk 512/0; 86 IN Bulk 512/0; 88 IN Bulk 512/0 |
|1/0|2|0|2|01 OUT Interrupt 64/1; 81 IN Interrupt 64/1; 02 OUT Interrupt 512/1; 04 OUT Bulk 512/0; 86 IN Interrupt 512/1; 88 IN Bulk 512/0 |
|1/0|3|0|3|01 OUT Interrupt 64/1; 81 IN Interrupt 64/1; 02 OUT Isochronous 512/1; 04 OUT Bulk 512/0; 86 IN Isochronous 512/1; 88 IN Bulk 512/0 |

Trong bảng, địa chỉ là hex; packet theo byte; interval giữ giá trị descriptor. Type Android 2=Bulk, 3=Interrupt, 1=Isochronous. Không có 82 trong bất kỳ alternate nào, không được suy OUT 02 tương đương IN 82. MaxPacket 512 phù hợp một khả năng USB High Speed nhưng không phải phép đo negotiated speed trên Android; không gọi USB 2 High Speed vừa được chứng minh.

Nguồn khóa official TBS `2ce787de9f6e81b4294692a45de15596ae2e1322`, C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c` giữ sạch/không fetch. Official [tbs5530.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/usb/dvb-usb/tbs5530.c) properties đặt TS endpoint 82; `tbs5530_load_firmware` có bước download trước frontend nhưng không chứng minh thiết bị hiện tại đang cold/warm. Snapshot WSL lịch sử 81/82 khác Android 81/86/88. Có thể liên quan trạng thái firmware/descriptor mặc định/variant hoặc thời điểm đo; chưa có evidence để chọn một giả thuyết. Không tự nạp ảnh, reset hoặc thử alternate để phân biệt.

Tbs5530Descriptors không vô tình nhầm index với ID: guard hiện từ chối đúng vì alternate 0 rỗng và toàn bộ alternate khác thiếu 82. Profile này có thể chặt hơn một pre-firmware EP0-only lifecycle tương lai, nhưng chưa có quyết định cho phép bỏ TS requirement. Phase này giữ guard, đánh BLOCKED cho claim; không dùng 88 thay 82 hoặc setInterface 1.

### Permission thật và giới hạn chứng minh

Trước test, UsbManager.hasPermission=false. Diagnostic được bổ sung nút chỉ yêu cầu quyền và JSON export trước khi bấm. Dialog hệ thống thật hiển thị “Cho phép DVB-T Driver truy cập null?” (device không có product name); agent không bấm OK. Người dùng chọn OK sau tương tác hỏi xác nhận.

Lượt operation 1/generation 1 đã hết deadline 30s trước khi agent đọc lại: JSON callback_count 0, correlation_device_matched=false, state CLOSED, pending false, cleanup false, open/claim/release/close 0. Refresh metadata sau đó thấy hasPermission=true. Điều này chứng minh quyền hiện hành được cấp bởi OS, nhưng **không chứng minh receiver callback được nhận** khi registration đã timeout/cleanup. Không có bug callback được tái hiện đủ để sửa export/mutability hoặc nới timeout.

Một lượt permission-only mới (không connection cycle) thấy pre-granted, state PERMISSION_GRANTED trước cleanup và CLOSED sau cleanup; callback count 0 đúng vì không request dialog khi có quyền. Không tự revoke permission, không fake callback hoặc ADB grant. Denied thật NOT RUN. Acquisition bị chặn trước open bởi profile thiếu 82, không intentional hung call hoặc physical detach khi chưa có cycle.

### Thay đổi phần mềm và evidence

Binding/coordinator có lựa chọn permission-only tái sử dụng receiver/token/state/deadline cũ, default start không đổi. Diagnostic kiểm profile bằng metadata trước cycle, tắt cycle nhưng vẫn cho permission-only. JSON private có timestamp/operation/generation/model/API/configs/interfaces/endpoints/permission/callback/state/counters/steps; đọc bằng run-as không root. Không path/device ID/serial/UUID credentials; không logs của ứng dụng khác. Dữ liệu JSON chỉ target TBS. Release không chứa exporter.

Regression mới bao phủ grant/deny/pre-granted permission-only với 0 open, unsafe profile block và JSON redaction/persistence; fixture descriptors OnePlus không được hợp thức hóa thành support TS. API runtime 30 khác compile 37.2; AtomicFile/API 17, StandardCharsets/API 19 và JSONObject đã hỗ trợ min 21. PendingIntent mutable mặc định trên 30 và receiver legacy đúng branch; callback grant thực chưa verified nên không quảng cáo API 30 permission delivery PASS.

Bước sau chỉ nghiên cứu provenance/state descriptor và kế hoạch kiểm firmware/USB bridge được duyệt riêng. Trước mọi write cần firmware license/hash/address map/rollback, không lấy enumeration hoặc quyền thành chứng cứ DVB-T2. Physical connection và detach vẫn BLOCKED/NOT RUN, không tự rút cáp hoặc yêu cầu người dùng đổi thiết bị khi chưa thống nhất.

## 2026-11-03 — Phase 2.4B: thử Windows QEMU passthrough và debug diagnostic

### Baseline và phạm vi phê duyệt

Main sạch khi bắt đầu trên chore/modernize-android-build, HEAD `c153e05e454f506f5c6639685d2cded51e8c0126`; đây là Phase 2.4A/ADR-014 đã push. Người dùng phê duyệt thử passthrough (chuyển tiếp USB vào máy ảo), đọc metadata (thông tin mô tả thiết bị), xin permission và chạy một cycle nếu Android framework thấy đúng TBS. Chỉ cho usbipd detach khi trạng thái trực tiếp là Attached; không bind/unbind/attach WSL. Vẫn cấm vendor control, I²C, firmware, reset, LNB, tune, TS và thay driver. Không chuyển sang điện thoại/tablet trong phase.

### Quan sát mới theo tầng

| Tầng | Quan sát thực | Kết luận |
|---|---|---|
| Windows PnP | TBS5530 VID 734c:5530, status OK/MEDIA; signed TBS driver 2.0.0.2/service tbsBDA.TBS5530/oem108.inf | Host nhận thiết bị, không chứng minh Android truy cập được |
| Quyền sở hữu USB | Đã tra BUSID trực tiếp: 6-2, Shared, không Attached; Ubuntu 22.04 Stopped/WSL2 | Windows sở hữu thiết bị, không gọi usbipd detach/bind/unbind/attach |
| Loại thiết bị QEMU | Build hiện tại nhận các thuộc tính usb-host, vendor/product | Không bị chặn bởi loại thiết bị không được hỗ trợ |
| QEMU liệt kê thiết bị host | QMP info usbhost: 734c:5530/480 Mb/s; libusb lưu đệm 1 config, 32 bytes | Backend của host đọc được metadata, không chứng minh mở được thiết bị |
| QEMU mở thiết bị host | `winusb_open: unsupported API call for 'open' (unrecognized device driver)`; `libusb_open open4.4 returns -12` | BLOCKED vì backend không hỗ trợ driver BDA hiện tại |
| Kernel của Android guest | Kernel 6.12.81-android16-6-g4f69fc7b210c-ab16167562/x86_64/API 37; sysfs chỉ có root USB hubs, /dev/bus/usb 001/002/003 chỉ có 001 | Không thấy TBS trong guest; thấy hubs không phải là passthrough PASS |
| Android framework | has-feature android.hardware.usb.host=false; dumpsys usb timeout khi boot với passthrough; khi boot đối chứng, service có và host_connected=false | Image chưa đạt khả năng USB Host; chưa kiểm permission/acquisition trên phần cứng |

Không tìm thấy tiến trình tuner Windows phổ biến nào khớp tên; quét tên process không chứng minh mọi process đều không giữ handle. Tuy nhiên, lỗi -12 do driver không được hỗ trợ khác ACCESS/BUSY; không yêu cầu người dùng đóng apps để né một nguyên nhân không khớp. Không thay driver hệ thống.

### Các thử nghiệm và nguyên nhân

Google [Emulator release notes](https://developer.android.com/studio/releases/emulator) có Windows USB passthrough `-qemu -usb -device usb-host,vendorid=...,productid=...`. [Command line](https://developer.android.com/studio/run/emulator-commandline) mô tả args/QEMU/no-snapshot. [QEMU USB](https://www.qemu.org/docs/master/system/devices/usb.html) phân biệt UHCI/EHCI/XHCI và host passthrough. [libusb Windows](https://github.com/libusb/libusb/wiki/Windows) ghi rằng truy cập USB chung cần WinUSB/libusbK/libusb-win32 hoặc backend phù hợp; driver BDA của hãng không tự được libusb hỗ trợ. Tra nguồn ngày 2026-11-03; không cài QEMU/libusb/driver mới.

1. Đã xác minh AVD hiện hữu; ADB ban đầu không có device, WHPX (10.0.19045) dùng được. Emulator 37.2.13/build 16740729, system image android 37.0/google_apis/x86_64 revision 7; không load/save snapshot hoặc wipe dữ liệu.
2. Lệnh người dùng đề xuất -qemu -usb -device734c5530 đã boot được ADB/API 37 nhưng không thấy thiết bị đích; không coi emulator tồn tại là USB PASS. QEMU monitor qua ADB emu trả “QEMU support no longer available”; thay bằng QMP loopback chỉ đọc, không dùng lệnh hotplug/reset.
3. Đã thử một cấu hình XHCI riêng theo tài liệu QEMU cho USB 2 High Speed, có guest-reset=false/guest-resets-all=false. Không xác nhận được ADB boot và phải dừng helper; không tiếp tục lặp các cấu hình bus. Không tùy chỉnh kernel/image hoặc tạo VM mới.
4. Cấu hình gốc thêm LIBUSB_DEBUG 4, log level 4, QMP loopback và các reset flags=false. QMP query-version xác nhận 2.12.0 `(v2.12.0-19097-g85fa07f04ef)`; info usbhost nhận TBS nhưng libusb open trả -12. Dừng passthrough ngay sau khi đủ bằng chứng, tránh để backend tiếp tục tự thăm dò.

Giới hạn quan trọng: `info usb` có placeholder “USBHostDevice” nhưng không có VID/PID hoặc identity của guest. Điều đó không chứng minh host đã mở thiết bị, kernel Android hoặc UsbManager đã nhận TBS. Các thuộc tính kernel/sysfs và dmesg bị từ chối quyền đọc được ghi NOT READ; không root hoặc vượt SELinux. Debug trên AVD thường dùng để kiểm phần mềm sau khi gặp chặn, không gắn nhãn là phiên passthrough phần cứng thật.

### Debug-only diagnostic và phương án tiếp

Activity Tbs5530UsbDiagnosticActivity chỉ có trong debug, phải gọi tường minh và không có launcher/USB attach filter. Refresh liệt kê capability/VID/PID/configurations/interfaces/endpoints mà không open. Nút request/cycle chủ động chọn TBS, dùng generation/correlation/permission của binding và worker đã có. Chỉ sau khi được grant mới open/claim với force=false; một cycle xong thì release/close, không gọi control API hoặc kiểm readiness/tune/TS.

Controller giữ quyền sở hữu qua thay đổi cấu hình Activity; lần close cuối vô hiệu hóa binding và shutdown runtime, không join trên main hoặc force-close thao tác đang chạy. Copy report lọc path/id/serial/thông báo lỗi thô. Main/release không có diagnostic entry point; giữ nguyên production registry/XML.

Không có TBS trong Android nên dialog, callback grant/deny, open/claim và detach thật đều BLOCKED/NOT RUN; không nhấn Allow hoặc grant qua ADB. Tests dùng fake chỉ kiểm phần mềm, không thay bằng chứng thật. Để thử sau phase, cần Android thật hỗ trợ USB Host, nêu rõ phiên bản, OTG, nguồn hub, ADB wireless hoặc cổng riêng. Dùng debug entry point trên đúng device; người dùng tự chọn tại từng bước xin quyền, không gửi vendor requests. Đổi Windows driver chỉ là phương án cần phê duyệt và có rollback về BDA, không phải việc đã làm hoặc chắc chắn đủ giải quyết image báo USB Host=false.

## 2026-11-03 — Phase 2.4A: managed control transport; kế hoạch 2.4B chưa thực thi

### Baseline, nguồn và loại bằng chứng

Nhánh chore/modernize-android-build, baseline `8a004a3cc94d8c736da84cad1908b6a270b789a0`; local/live origin cùng SHA, sạch lúc bắt đầu. Phase 2.3/ADR-013 đã commit/push tại SHA này; các snapshot còn pending trước đó được giữ làm lịch sử. References chỉ đọc, no-lazy-fetch/optional-locks 0: official `2ce787de9f6e81b4294692a45de15596ae2e1322`, C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`; không sync hoặc thay config/index.

Source chính hãng [tbs5530.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/usb/dvb-usb/tbs5530.c), `tbs5530_op_rw` L35: EP0 vendor 0x40/0xc0, đủ request/value/index/length, timeout 2000 ms; `tbs5530_i2c_transfer` L60: 0x80 write/0x90 pointer/0x91 read, buffer 50, mutex khóa cả cặp; properties L328/342: generic bulk 81 và TS bulk 82. Đối chiếu [community C](https://github.com/koreapyj/cxd28xx/blob/c5fa27eecbe80633cbb365cc44656daea1c0e2e3/tbs5530.c) có cùng dạng op_rw/I²C và [Go bridge](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/bridge/tbs/tbs.go) có ControlWrite/ControlRead kiểm đủ số byte; giới hạn 62 của Go khác giới hạn bảo vệ 48/50 của Android hiện tại. Không nâng giới hạn hoặc copy generic 0x93 vào TBS5530 read. Source chính hãng copy buffer IN bất kể kết quả trả về; đây là hành vi trong nguồn, không phải hành vi nên giữ. Adapter mới không công bố dữ liệu IN khi lỗi.

Nguồn [UsbDeviceConnection](https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection), tra ngày 2026-11-03: overload controlTransfer có offset từ API 18, phù hợp min 21. Hướng truyền lấy từ requestType; count>=0 hoặc số âm khi thất bại, không có errno API để suy mọi kết quả âm là timeout. Claim với force=true có thể ngắt kernel driver; giữ false. Không dùng lại connection sau close. [UsbEndpoint](https://developer.android.com/reference/android/hardware/usb/UsbEndpoint) cung cấp metadata address/direction/type/maxpacket; EP0 là endpoint control đặc biệt, không cần bulk endpoint cho vendor requests.

VID 734c:5530 là USB ID trong source. Interface 0, bulk IN 81/82/maxpacket 512/USB 2 High Speed là quan sát Windows/WSL trước, **không phải enumeration Android mới**. Linux TS 82 là thông tin từ source, không xác nhận device Android sẽ khớp. Maxpacket không đủ chứng nhận tốc độ đã thương lượng, full speed/high speed, biến thể board hoặc trạng thái firmware.

### Thiết kế transport và owner

Tbs5530ControlTransport implements ControlIo là capability (quyền sử dụng transport) chỉ được Coordinator cấp sau connected+claimed; Binding.controlIo() lấy request hiện hành. Không mở connection thứ hai: facadeOwnedConnection/rawUsbDeviceConnection hiện có được lease chuyển tiếp lời gọi. Constructor là package-private; mỗi transaction kiểm `Session.requireOwned(token,lease)`. Handle/token cũ hoặc state FAULTED/UNCERTAIN/closed/unclaimed bị từ chối trước I/O. Các phương thức control/metadata mặc định của Connection từ chối khi chưa đủ điều kiện, giữ tương thích với fake ports cũ, không đổi drivers cũ.

Adapter kiểm các trường byte/ushort, length 0..65535, offset/bounds và timeout dương. Bản sao tạm giữ nguyên dạng tham số; IN chỉ copy vùng được yêu cầu sau khi thành công đầy đủ và kiểm state, OUT không sửa dữ liệu của caller. Short transfer, kết quả âm và overlong có loại lỗi riêng; giữ nguyên exception cause tường minh, không retry. Transaction lồng của Session tái sử dụng khóa 0x90/91 để cặp I²C vẫn nguyên tử, không bị thao tác khác chen vào. Invalidation có hiệu lực tức thời nhưng release/close chờ lời gọi đang chạy trả về. Không stream, không firmware executor, không tự READY/reset.

RawConnection được nối thật với `UsbDeviceConnection.controlTransfer` ở AndroidManager, nhưng mọi test dùng fake Manager/RawConnection. OwnedConnection.requireControlWorker chặn main trước khi lấy lock; kiểm claimed/open ngăn dùng raw capability sai state. Không công khai getter cho FD/UsbDeviceConnection thô. Descriptor metadata chọn interface ID 0/alternate đã chọn, từ chối trùng hoặc mâu thuẫn và tái dùng Bridge.selectBulkEndpoint82. TS maxpacket 64/512 là giới hạn bảo vệ thận trọng của board profile, không đại diện toàn USB spec; không đòi 81 hiện diện để dùng EP0. Metadata không kích hoạt TS reader. Facade chọn descriptor alternate nhưng không tự gọi UsbDeviceConnection.setInterface; chưa chứng minh kích hoạt alternate khác 0. Preflight phần cứng chỉ dùng alternate 0 tới khi có review/phê duyệt riêng.

Owner tương lai nên là một DvbDevice/diagnostic owner theo session, giữ binding/runtime/capability, dùng appContext, không dùng USB singleton static. UsbDelegate chỉ chuyển intent; constructor của registry không tự open/probe. Đã audit lifecycle của service worker hiện hữu nhưng không sửa hoặc thêm service. Cleanup trì hoãn phải chạy ngoài main, invalidate binding trước runtime.shutdown, chờ phần pending ở background. Receiver đăng ký động không giữ process sống: phải thiết kế vòng đời owner/service và các giới hạn chạy nền của OS khi tích hợp thật. Activity chỉ gửi yêu cầu, không sở hữu raw connection xuyên recreation. Diagnostic entry point debug/test mặc định tắt là phương án cho 2.4B; **chưa thêm entry point vật lý** trong phase này.

### Phase 2.4B — Checklist phê duyệt riêng, toàn bộ NOT RUN

Không có phê duyệt phần cứng trong Phase 2.4. Kế hoạch này không phải quyền thực thi; không chạy permission dialog/open/claim hoặc USB vendor requests. Khi xin duyệt phải ghi rõ thiết bị Android, phiên bản OS, phạm vi và từng bước kiểm. Không mặc định cần WSL/usbipd; tuner nối trực tiếp Android USB Host/OTG, không đồng thời thuộc Windows/WSL.

1. **Môi trường:** kiểm phiên bản Android/API, USB Host feature; xác nhận nguồn/cáp OTG/hub có nguồn đáp ứng tài liệu thiết bị. ADB phải quan sát serial được chọn tường minh (ưu tiên wireless ADB nếu cổng USB dùng OTG). Chưa rõ nguồn/cáp thì dừng, không tự thay điện áp/LNB/driver. Chuẩn bị debug/test harness chủ động, mặc định không hoạt động, chỉ đọc metadata; không dùng production registry để tự động mở TBS.
2. **Enumeration:** sau phê duyệt metadata, chụp UsbManager.getDeviceList/VID/PID/path/id/interface ID/alternate/endpoints/address/direction/type/maxpacket, thread/time/session state. Không bắt buộc đọc serial, không ghi định danh cá nhân vào log đưa lên Git. So với 734c:5530/interface 0/TS 82 và lịch sử 81/512 nhưng giữ lại khác biệt, không ép khớp board. Chưa đo tốc độ thì ghi UNKNOWN.
3. **Attach/detach:** ghi epoch (số phiên) và events quanh một lần cắm/rút do người dùng chủ động; kiểm event đến muộn/path dùng lại, không tự lặp reconnect. Metadata của OS không bảo đảm nhận diện đúng phiên phần cứng nếu bỏ lỡ events; ghi rõ giới hạn.
4. **Permission:** phê duyệt riêng cho phép hiện dialog; người dùng tự chọn deny/grant, agent không tự chấp nhận. Ghi correlation đã ẩn/state/generation, hasPermission trực tiếp và việc giao callback trên OS thật; deny/timeout/callback cũ phải có 0 open. Callback không đến thì dừng, không tự nới export/mutability để ép PASS.
5. **Connection:** phê duyệt riêng cho openDevice→claimInterface(force=false)→release→close; chỉ interface 0/alternate 0 theo profile hiện tại, không tự setInterface hoặc ép force. Kiểm đúng 1 owner/1 open/1 claim/1 release/1 close, thread background, pending/cleanup; chỉ test detach khi đang mở sau khi được duyệt. Không vendor control, không I²C, không firmware, không TS.
6. **Stop/rollback:** open trả null, claim trả false, permission/identity thay đổi, lời gọi bị chặn, timeout, cleanup lỗi hoặc descriptor ngoài dự kiến thì dừng ở bước đó, giữ log đã lọc. Invalidate/cleanup tài nguyên đã acquire xong, không force-close thao tác đang chạy. Nếu operation không trả, ghi PENDING và không open mới; không lặp reset/rebind/reload/reconnect. Chỉ tháo cáp/nguồn theo chỉ dẫn được người dùng phê duyệt, không tự gọi usbipd.
7. **Evidence:** không đánh PASS khi chỉ bắt đầu operation; cần kết quả trả về thực, state và cleanup. Chỉ sau đó mới review phase control riêng; tuyệt đối không xem enumeration/permission PASS là cho phép vendor write/firmware.

| Thao tác 2.4B | Kết quả | Android/API, VIDPID/descriptors | Thread, thời gian, code/exception | Connection/session | Thay đổi phần cứng |
|---|---|---|---|---|---|
| Kiểm môi trường/nguồn/OTG | NOT RUN | Chưa chọn Android thật | Chưa đo | Không acquire | Chưa thực hiện |
| Enumeration/descriptors | NOT RUN | Chưa có bằng chứng Android | Chưa đo | Không acquire | Chưa thao tác; OS enumeration có thể có standard USB requests, không vendor writes |
| Attach/detach | NOT RUN | Chưa quan sát | Chưa đo | Không session thật | Chưa cắm/rút trong phase |
| Permission deny/grant/correlation | NOT RUN | Chưa có dialog thật | Chưa đo | Không open | Chưa request permission |
| Open/claim force=false | NOT RUN | Chưa claim | Chưa đo | Không connection thật | Chưa đổi quyền sở hữu trên host |
| Release/close/cleanup khi detach | NOT RUN | Chưa acquire | Chưa đo | Không resource để cleanup | Chưa thực hiện |
| Vendor transfer/firmware/frontend/lock/TS | NOT RUN, ngoài 2.4B | Không thuộc checklist phê duyệt | Không lệnh | Không thực hiện | Cấm trong preflight |

Kết quả 2.4A thực ở TEST-MATRIX/WORKLOG; fake/instrumentation chỉ chứng minh phần mềm, không chứng minh USB enumeration thật hoặc vòng đời connection. Giữ nguyên toàn bộ snapshot lịch sử phía dưới.

## 2026-11-03 12:39 +07 — Phase 2.3: Android USB framework binding và instrumentation

### Checkpoint, audit nguồn và phạm vi

Main sạch ở `chore/modernize-android-build`/`635ee4a57267de6055126efcfee6121b30701433`; live origin cùng SHA, xác nhận Phase 2.2/ADR-012 đã tích hợp/push. Các snapshot “chưa commit” của phase trước được giữ làm lịch sử. Ba references kiểm chỉ đọc vẫn nguyên/sạch: TBS 2ce787de9f6e81b4294692a45de15596ae2e1322, C c5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go 11228b05fb1626bf41c4e1879ace10cd0a8d575c; không đồng bộ/sửa chúng.

Audit Android source tại 635ee4a:
- `dvbservice/AndroidManifest.xml` khai báo UsbDelegate exported cho USB_DEVICE_ATTACHED, service không export; device_filter.xml chưa có TBS5530. UsbDelegate::onCreate chỉ chuyển UsbDevice sang Activity intent rồi finish, không có binding gắn generation với vòng đời.
- DvbUsbDeviceRegistry chỉ có ba creators hiện hữu, query danh sách UsbManager rồi tạo DvbDevice tương ứng. Giữ nguyên để không tự đưa TBS vào luồng người dùng.
- DvbUsbDevice::open là final, lấy permission Future rồi init với retry; UsbPermissionObtainer::obtainFdFor/registerNewBroadcastReceiver dùng action chung, mutable PI (PendingIntent cho phép bổ sung dữ liệu) và có thể openDevice trực tiếp trong onReceive. Không dùng đường này cho TBS mới, cũng không sửa helper cũ khiến drivers cũ thay đổi ngoài phạm vi.
- DvbService worker hiện sở hữu vòng đời DvbServer; TransferThread lo TS/TCP. Chưa tạo TbsDvbDevice nên không nối binding vào service hoặc thay global worker. Binding mới chỉ được khởi tạo bởi caller/test có owner rõ ràng.

### API Android đối chiếu và chọn policy

Tra Android CLI docs search/fetch và các nguồn Google ngày 2026-11-03:
[USB Host](https://developer.android.com/develop/connectivity/usb/host), [UsbManager](https://developer.android.com/reference/android/hardware/usb/UsbManager), [UsbDevice](https://developer.android.com/reference/android/hardware/usb/UsbDevice), [UsbInterface](https://developer.android.com/reference/android/hardware/usb/UsbInterface), [UsbDeviceConnection](https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection), [PendingIntent](https://developer.android.com/reference/android/app/PendingIntent), [broadcasts](https://developer.android.com/develop/background-work/background-tasks/broadcasts), [Android 12 mutability](https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability), [Android 14 safer intents](https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents), [Android 17 target 37 changes](https://developer.android.com/about/versions/17/behavior-changes-17).

| API runtime | Policy đã implement | Bằng chứng/giới hạn |
|---|---|---|
| 21 | Mutable mặc định, one-shot PI/action+URI/package riêng, đăng ký receiver theo API cũ | Không dùng FLAG_IMMUTABLE vì USB extras cần được hệ thống bổ sung; chỉ assertion cho nhánh trên 37, chưa chạy runtime 21 |
| 23 | FLAG_IMMUTABLE đã có nhưng không chọn cho USB result | Không suy template immutable nhận được dữ liệu hệ thống bổ sung; chỉ assertion cho nhánh |
| 31 | FLAG_MUTABLE tường minh | Đáp ứng mutability cho target 31+; không dùng implicit PI không giới hạn phạm vi |
| 33 | RECEIVER_NOT_EXPORTED, typed getParcelableExtra | Policy mới có guard; từ chối dữ liệu thiếu/sai kiểu; chỉ assertion cho nhánh |
| 34 | Mutable PI phải có component hoặc package | setPackage(appPackage), không ALLOW_UNSAFE_IMPLICIT_INTENT; receiver tùy chỉnh đăng ký qua context có export flag |
| 37 | Giữ các guard trên, không nới phạm vi | Instrumentation với receiver+PI thật PASS trên API 37; không phát hiện yêu cầu USB mới trong trang target 37 đã đọc, không suy ra mọi hành vi USB đều đã được chứng minh |

PendingIntent được tạo cho receiver đăng ký qua context nên dùng package scope tường minh, không trỏ component giả trong manifest. Mỗi request có action/data/correlation độc lập, FLAG_ONE_SHOT và token được cancel khi close; không dựa extras làm PI identity vì extras không quyết định PI matching. Receiver dùng applicationContext và vòng đời riêng, chỉ parse action/data/device/granted rồi dispatch; acquisition của connection không chạy trên broadcast/main. Hành vi fill-in/one-shot có test PI thật với extras giả lập; USB permission dialog thật vẫn NOT RUN.

### Component mới và contracts

- [Tbs5530UsbBinding.java](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530UsbBinding.java): JVM engine, Facade/Registration/Events/Scheduler/Errors ports; explicit start chọn device; epoch tăng mỗi attempt, correlation UUID, current request duy nhất. Callback sai/duplicate/stale không consume request khác. Worker kiểm live list+hasPermission trước accept, trước/sau open và claim. Grant/open/claim không tự READY.
- [Tbs5530AndroidUsbFacade.java](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530AndroidUsbFacade.java): chuẩn bị các wrapper cho production framework, inject Manager/RawConnection cho tests. Constructor không thao tác USB hoặc đăng ký receiver; engine owner gọi listen/request registration. Chọn interface ID 0+alternate từ descriptors, không theo vị trí trong array; từ chối thiếu/mơ hồ, claim với force=false. Ghi receipt sau claim thành công, trước các bước kiểm sau đó; release=false là IOException nhưng close vẫn thực hiện đúng một lần. Không có phương thức control/vendor/firmware/streaming.
- [Tbs5530AndroidRuntime.java](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530AndroidRuntime.java): một worker cho acquire/cleanup và scheduler riêng cho deadlines; không join main hoặc để worker tự chờ mình, không dùng shutdownNow để tuyên bố đã hủy thao tác vật lý.
- Session/Coordinator thêm `requestCancellation`/`invalidate` chỉ đổi state và `drainCleanup` ngoài main. Quyền sở hữu cleanup giữ nguyên ADR-012; transfer đang chạy được drain sau khi thao tác vật lý hoàn tất. Guard cleanupRunning ngăn hai drainer nhận cùng tài nguyên. Không đổi EP0 bytes/I²C/FirmwarePlan hoặc Retry.

Deadline logic của permission và acquisition khác thời điểm thao tác vật lý hoàn tất. Deadline ID loại timer cũ đã cancel; acquisition bị block không cản scheduler invalidate. Receiver detach chụp attempt và kiểm live list ngoài acquisition worker, tránh xếp invalidation sau open đang block. Cleanup được xếp trên worker, không đóng connection khi open/claim/transfer còn sử dụng. Worker đã shutdown sai thứ tự không được fallback sang I/O trên main: phải báo lỗi. Owner phải đóng binding trước runtime và theo dõi phần pending.

### Identity và reconnect: điều chứng minh được và không chứng minh được

Identity gồm VID/PID/path/id+epoch của application; không lấy việc đọc serial làm điều kiện. Hai devices cùng VID/PID vẫn tách theo snapshot. Attach mới ở path đang theo dõi sẽ invalidate theo hướng thận trọng, không tự nối lại. Permission cũ dùng correlation/epoch cũ bị loại. Detach đến muộn không đóng connection hiện tại nếu snapshot còn hiện diện; nếu snapshot đã mất thì invalidate. Bước kiểm trong hàng đợi chỉ ảnh hưởng attempt đã chụp, không ảnh hưởng generation mới.

Tuy nhiên, Android USB broadcast không có epoch của application. Nếu detach+reattach dùng lại toàn bộ path/id/VID/PID và app bỏ lỡ attach/detach, API không đủ chứng minh đó là cùng phiên phần cứng. Kiểm live list giảm race (xung đột do thời điểm chạy) nhưng không loại TOCTOU, tức trạng thái có thể đổi giữa lúc kiểm và lúc dùng, giữa hai Binder calls. Đây là giới hạn cần xác minh trên phần cứng; snapshot khớp không có nghĩa physical identity PASS.

### Kết quả kiểm thử và bước kế tiếp

- Đã triển khai mới/sửa 7 Java files, không đăng ký framework trong app manifest/registry/service. Unit tests đã chạy 152 methods, trong đó có 26 methods binding mới; instrumentation đã chạy 19 binding+2 native/registry trên API 37/x86_64; debug/lint PASS, 0 errors, 11 warnings. Counts/lệnh ở TEST-MATRIX/WORKLOG.
- Android framework được xác minh có phạm vi: tạo PI thật, fill-in/one-shot/registration/unregistration, dispatch giả lập, khả năng đáp ứng của worker/deadline. DeviceManager/connection luôn là fake; chưa đo việc hệ thống giao USB broadcast được bảo vệ, dialog, Host vật lý hoặc phiên bản OS khác.
- App không tuner: Install Success/launch Status ok; Welcome/Diagnostic/START xử lý NoDevicesFound, không có app fatal trong cửa sổ log. SystemUI ANR là vấn đề riêng của emulator, không phải app ANR. Không test DvbService active/vòng đời streaming vì cần tuner.
- Phase 2.4 đề xuất: review ADR 013/vòng đời process của owner; kiểm permission callback+identity trên Android thật, không writes, với phê duyệt riêng. Chỉ xem xét khởi tạo phần cứng khi nguồn gốc/giấy phép/hash/address map của firmware và kế hoạch lệnh/rollback được duyệt. Chưa có vendor transport/uploader/CXD frontend/TS, chưa commit/push.

## 2026-11-03 11:25 +07 — Phase 2.2: lifecycle races, ownership và Android API contract

### Checkpoint và nguồn hiện tại

Main sạch lúc bắt đầu ở `chore/modernize-android-build`, HEAD `6eb23537484a3e1dd263bd5a88f56914c7c5e313`: Phase 2.1 và ADR-011 đã được tích hợp, không còn “chưa commit” như snapshot cũ. Không reset/merge/push hoặc tạo nhánh trong Phase 2.2. Ba references kiểm đọc giữ nguyên/sạch: official TBS `2ce787de9f6e81b4294692a45de15596ae2e1322`, community C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`. Không thay protocol bytes hoặc FirmwarePlan nên không mở rộng phần port source/hardware.

GitNexus list_repos hiện không có project này; không tự analyze hoặc coi thiếu index là không có ảnh hưởng. Dùng source hiện tại, tests và diff làm bằng chứng. Các ràng buộc/cơ chế phần cứng ở Phase 2.0/2.1 vẫn chưa được kiểm thử vật lý.

### Những race đã tái hiện và sửa

Hai test mới dùng latch giữ fake `controlTransfer` tại thread A rồi detach/cancel thread B. Trước sửa: **2 tests FAIL** vì Bridge giữ monitor của Session, thread B không invalidate tới khi transfer trả. Đây là phép tái hiện bằng fake, không phải lỗi đo trên thiết bị. Một callback detach chạy cùng transfer thread còn có thể cleanup/close trước khi callback trả khỏi synchronous transfer.

Sau sửa, Session có transactionLock riêng, state monitor chỉ giữ ngắn. Mỗi operation được nhận bằng generation/state check và đánh dấu active; I²C 0x90/91 vẫn chung transaction nên bridge khác không chen vào. Detach/cancel/close invalidate ngay, từ chối operation mới nhưng không thể rút lại operation đã được nhận. Kiểm state sau transport và trước copy read output khiến kết quả cũ không được publish. Transfer đã bắt đầu có thể còn hoàn tất hoặc báo timeout; completion đó không đổi state mới, không retry.

Close khi còn active operation chỉ ghi cancellation intent/state và pending cleanup. Connection không release/close cho tới last active transaction hoàn tất; cleanup chạy sau nhả transactionLock và state monitor. Cách này tránh use-after-close và tránh giữ khóa trong khi cancel join worker. Queued transaction cũ bị chặn trước physical I/O; generation mới chỉ mở sau cleanup hết. Callback detach tự gọi trong transport cũng chỉ defer cleanup, không close bên trong I/O.

Cleanup vẫn cancel→release→close theo phần sở hữu, exactly-once, không corrective writes. Exception ở một bước không bỏ bước sau; deferred failure được lưu và ghép suppressed vào transfer error. Tuy nhiên Resources.cancel không được join chính thread đang chạy cleanup. Không có thread/executor mới trong production core.

### Coordinator và quyền sở hữu

[Tbs5530LifecycleCoordinator.java](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530LifecycleCoordinator.java) là coordinator thuần JVM cho sự kiện Android dự kiến; chưa có BroadcastReceiver/PendingIntent/UsbManager binding. Ports gồm PermissionPort, ConnectionPort, Connection và Claim receipt. AttachmentIdentity mô tả một lần gắn; Request ràng generation với identity, không suy cùng VID/PID là cùng session.

- Permission callback phải đúng request+generation+identity; sai/duplicate/cũ không acquire hoặc consume nhầm pending request.
- Open/claim ngoài mọi lifecycle monitor. Connection tạm thuộc coordinator; claim receipt ghi nhận tài nguyên đã acquire ngay cả trước lỗi một phần. Chỉ chuyển cho Session khi connected/interfaceClaimed được nhận nguyên tử dưới state monitor, không gọi vật lý trong đoạn đó.
- Detach/timeout/cancel trước hoặc trong acquisition khiến kết quả đến muộn bị từ chối và cleanup lease cũ. Session không chịu tài nguyên chưa nhận; callback cũ không được đóng lease mới.
- Một coordinator chờ acquisition cũ trả và cleanup xong trước reconnect. Không cưỡng bức hủy open/claim. API Claim yêu cầu receipt đồng bộ; lỗi IOException/RuntimeException được giữ cause/suppressed, không hứa recovery khi fatal JVM Error.
- Grant/open/claim không READY. Session ghi firmware-upload completion event riêng, bridge ready riêng và frontend initialization riêng; lock chỉ sau init, streaming không đồng nghĩa lock. Không có probe/firmware executor hoặc register writes thật.

### Giới hạn Android API được tra trong phase

Nguồn Google tra ngày 2026-11-03: [UsbDeviceConnection](https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection), mục `controlTransfer`, `close`, `claimInterface`; [USB Host guide](https://developer.android.com/develop/connectivity/usb/host), phần permission/communication.

- `controlTransfer` có timeout tính bằng milliseconds, trả số byte thành công hoặc số âm khi lỗi; overload có offset từ API 18 nên không xung đột min 21. API này không trả mã riêng đủ phân biệt mọi lỗi timeout/permission/detach. Không được tự gán mọi kết quả âm là timeout.
- Không có cancellation handle cho synchronous `controlTransfer` trong contract được đọc. `UsbRequest.cancel` thuộc luồng request bất đồng bộ, không chứng minh hủy được synchronous control call. Không dựa vào close để bảo đảm cancel an toàn hoặc giữ native buffer/FD hợp lệ.
- Google yêu cầu kiểm permission trước giao tiếp và đưa data transmission khỏi UI thread. Framework binding tương lai phải xác minh broadcast action, identity, generation, permission hiện hành; không copy chính sách PendingIntent/receiver một cách máy móc cho mọi API.

Nếu một synchronous call không trả theo deadline, lifecycle vẫn invalidate được nhưng tài nguyên vẫn giữ/pending, không reopen. Đây là lựa chọn an toàn có giới hạn, không cam kết physical cancellation. Các trường hợp kernel/driver thật và native streaming chưa được kiểm.

### Xác minh và điều kiện trước Phase 2.3

126 unit methods PASS trên cả ba module, debug/lint PASS; stress 10 concurrency methods × 5 lượt, gồm 200 vòng detach/cancel, PASS. Counts/lệnh chính xác ở TEST-MATRIX/WORKLOG. Không có instrumentation mới vì toàn bộ thay đổi chỉ thuộc JVM, chưa gắn Android runtime; không gọi permission dialog hoặc thiết bị.

Trước Phase 2.3: review ADR-012, chốt ánh xạ attachment identity/reconnect, vòng đời permission callback/PendingIntent/receiver, deadline của open/claim/control và hợp đồng thread cleanup. Nếu triển khai framework bindings thì thêm mock/instrumentation không tuner và chạy lại registry/native-load hiện hữu. Hardware/firmware cần phê duyệt riêng về nguồn gốc/giấy phép/hash/address map và kế hoạch USB; chưa có frontend production hoặc registry entry. Dừng ở Phase 2.2, chưa commit/push.

## 2026-11-03 10:39 +07 — Phase 2.1: hiện thực hóa protocol core, chỉ kiểm thử offline

### Mốc nguồn và loại bằng chứng

Nhánh mới `feat/tbs5530-offline-protocol` từ checkpoint sạch `8588ee7661003052b7d1a0353e23bb3a3dcef8b5`; không đổi baseline modernization hoặc ba references. TBS `2ce787de9f6e81b4294692a45de15596ae2e1322`, C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, Go `11228b05fb1626bf41c4e1879ace10cd0a8d575c`, working tree của cả ba sạch khi kiểm lại. Không fetch/sync hoặc ghi index/config reference.

Nguồn official vẫn là [tbs5530.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/usb/dvb-usb/tbs5530.c): `tbs5530_op_rw`, `tbs5530_i2c_transfer`, `tbs5530_load_firmware`, `tbs5530_properties`; đối chiếu [C community](https://github.com/koreapyj/cxd28xx/blob/c5fa27eecbe80633cbb365cc44656daea1c0e2e3/tbs5530.c) và [Go tbs bridge](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/bridge/tbs/tbs.go). Chip/PLP đối chiếu official [cxd2878.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/dvb-frontends/cxd2878.c) `cxd2878_init`, `cxd2878_set_dvbt2`, `cxd2878_set_frontend`, cùng khác biệt C/Go đã ghi ở Phase 2.0.

| Loại | Đã xác nhận trong phase | Không được suy ra |
|---|---|---|
| Source-grounded | EP0 types 0x40/0xc0, timeout 2000 ms, mã hóa 0x80/90/91/8a, TS 0x82; A0 reset/chunks 64/delay 100 ms; policy chip/PLP chính hãng | Chip thực, giới hạn vật lý 48/50, Android giữ connection sau firmware |
| Implemented | 4 core classes thuần Java 8, không Android/JNI imports, không transport phần cứng cụ thể; mọi state/quyền sở hữu được truyền vào qua dependency injection | Có driver hoạt động hoặc Android permission handler |
| Unit-tested | 52 methods mới, kiểm chính xác bytes/thứ tự/lỗi/state; tổng 83 methods không failure/error; debug/lint thành công | Silicon timing, firmware thật, lock/TS hoặc native cancellation |
| Chưa có bằng chứng | Nguồn gốc/giấy phép/hash/address map của firmware, warm/cold identity, reconnect, USB Host, tuner/PLP registers | Không đánh dấu PASS chỉ vì fake hoặc build thành công |

### Các component đã tạo

Cùng nằm ở `drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/`:

- [Tbs5530ControlIo.java](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530ControlIo.java): interface nhận đủ control-transfer parameters; lỗi có loại SHORT_TRANSFER/TIMEOUT/IO_FAILURE/CANCELLED/SESSION_UNAVAILABLE và cause. Không implementation USB thật.
- [Tbs5530Bridge.java](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530Bridge.java): validate trước I/O; write 48/read 50 là giới hạn bảo vệ phần mềm; 0x90/0x91 dùng chung session lock. Sau transfer, kiểm lại generation/state để callback detach không dẫn đến request tiếp. Read chỉ copy output sau khi đủ length, không retry. Descriptor giả phải khớp interface 0, alternate đã chọn, Bulk IN 0x82; từ chối trường hợp mơ hồ. Chip map 0x197→Ascot3, 0x19c/0x39a→Freia, từ chối ID khác; manual PLP 0/range 0..255, không ghi frontend register.
- [Tbs5530FirmwarePlan.java](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530FirmwarePlan.java): kiểm SHA-256/size/ranges/overflow trước khi lập plan; start address 0; không cho DATA chồng 0x7f92/0xe600 dù caller thiếu reserved ranges. Mô hình stop 1 ở cả hai CPU registers → chunks ≤64/phần cuối đúng độ dài → restart 0 → DELAY 100 ms; tất cả dùng bản sao bảo vệ dữ liệu gốc. Không nhận file/path/firmware thật, không thực thi plan trong production.
- [Tbs5530Session.java](../../drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/Tbs5530Session.java): phân biệt permission/connected/firmware transition/ready/faulted/uncertain/disconnected/closed. Streaming và frontend lock độc lập. Generation cũ bị bỏ qua; permission deny không I/O; failure latch chặn các lần gọi sau. Resource cleanup một lần, đúng thứ tự theo phần đã sở hữu, vẫn release/close khi cancel lỗi; không corrective writes.

Test package tương ứng có `ScriptedFakeControlIo`, `Tbs5530BridgeTest`, `Tbs5530FirmwarePlanTest`, `Tbs5530SessionTest`. Fake sao chép buffers, kiểm toàn bộ tham số và thứ tự; unexpected/missing call làm fail. Executor firmware chỉ ở test, kiểm failure tại từng bước kể cả delay, không thay uploader production. Cặp đọc concurrent được kiểm bằng thread/latches, không dùng USB.

### Làm rõ ADR-010 và giới hạn

Không đổi hướng ADR-010. ADR-011 ghi chi tiết: caller chịu tài nguyên của `connected` callback bị từ chối; permission timeout là event inject; READY cần explicit event từ phép kiểm độc lập tương lai; synchronous transfer không có cơ chế cưỡng bức hủy từ thread khác. Close thông thường cleanup ngoài monitor, nhưng phải đợi transfer đang giữ monitor kết thúc. Test worker join không chứng minh native USB cancel; adapter sau phải tuân timeout và không tự cleanup/join từ transport callback còn đang chạy.

Không thêm TS reader nên các fixture packet 188 bytes/partial read/drain của kế hoạch Phase 2.0 **NOT RUN/hoãn**; cũng chưa test ghi lại PLP registers khi retune. Trong Phase 2.1 chỉ kiểm lựa chọn endpoint, policy values và sự độc lập giữa streaming với lock. Không thay driver cũ, Retry, DeliverySystem/TCP, registry hoặc USB XML filter. Không thêm dependency hoặc nâng toolchain.

### Phase 2.2 đề xuất — chưa bắt đầu

1. Review core offline và thống nhất hợp đồng callback/cancellation/quyền sở hữu của ADR-011; giữ điều kiện kiểm 83 methods cùng debug/lint.
2. Chỉ khi được duyệt, thiết kế lifecycle adapter Android với fake connection/permission events; kiểm timeout, detach khi thao tác đang chạy, callback cũ và cleanup đúng một lần. Chưa đăng ký thiết bị hoặc có uploader mặc định.
3. Dùng instrumentation không tuner nếu cần xác minh Android callback/threading; tiêu chí hoàn thành là không gọi transport thật khi deny/stale/fault, không rò rỉ tài nguyên, không làm sai registry/XML/native smoke hiện có.
4. Tách phê duyệt firmware thật/USB writes/frontend thành bước sau: phải có giấy phép/nguồn gốc/hash/address map và kế hoạch phần cứng cụ thể. Fake PASS không mở các bước đó.

Kết quả chạy và lệnh chính xác ở [WORKLOG.md](WORKLOG.md), bảng kiểm ở [TEST-MATRIX.md](TEST-MATRIX.md). Dừng sau Phase 2.1, chưa commit/push.

## 2026-11-03 00:54 +07 — Xác nhận checkout TBS phục hồi bởi người dùng

Windows PowerShell tại main repo, nhánh tài liệu `docs/tbs5530-source-architecture`, nền 4cf1b64. Người dùng gửi screenshot script Apply PASS và yêu cầu kiểm lại. Đã kiểm source thực, không chỉ dựa screenshot.

- TBS `latest` / `2ce787de9f6e81b4294692a45de15596ae2e1322` không đổi. `status --porcelain=v1 -uall` không có dòng, index tồn tại (8.822.766 bytes tại kiểm tra), index.lock không có; sáu sparse paths giữ nguyên.
- Tạo manifest dự kiến từ `ls-tree` sáu thư mục cộng cone ancestor files; so filesystem: **1.357 expected /1.357 actual, 0 missing/extra**. `hash-object --stdin-paths` kiểm nội dung theo Git filters/EOL: **0 hash mismatch**. Không dùng checkout sạch thay cho kiểm file thực.
- Số file trong sáu trees: dvb-usb 100, dvb-frontends 438, dvb-core 11, tuners 114, include/media 114, include/uapi/linux/dvb 8. Phần còn lại là cone ancestor files, không tải toàn kernel. Board tbs5530.c/.h, Sony cxd2878.c/.h/_priv.h, Montage m88rs6060.c và framework init/firmware source đều hiện diện.
- `config --show-origin --get core.protectNTFS` exit 1/không lưu override. Backup `../logs/tbs-sparse-before-20261103-004911-951/git` tồn tại, after.json ghi PASS/1357 file/đúng SHA. Không xóa backup, không khẳng định có thể build full kernel từ sáu sparse paths.
- C/Go status sạch. Không fetch, index repair hoặc config write do agent; người dùng đã phục hồi trước lượt kiểm. Theo yêu cầu dọn tạm trước đó, script session `Restore-TbsSparseCheckout-Reviewed.ps1` chỉ được xóa sau gate trên đạt; source/backup không bị dọn.

**Kết luận hiện tại:** checkout-readiness PASS trong phạm vi sparse dùng nghiên cứu; các BLOCKED index/disk trong Phase 2.0 dưới đây là lịch sử đã được giải quyết. Nguồn gốc/quyền sử dụng firmware, nhận diện silicon và mọi test USB thật vẫn chưa xác minh.

## 2026-11-03 — Phase 2.0: source readiness, thiết kế và kế hoạch offline

### Phạm vi, phiên bản và loại bằng chứng

Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`, baseline đã push `4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe`. Main sạch trước tác vụ; tạo nhánh **`docs/tbs5530-source-architecture`** từ đúng baseline để không chỉnh nhánh modernization. Chỉ thay năm hồ sơ, không viết driver/test implementation, không build hoặc chạy phần cứng.

- **Đo trực tiếp trong phase:** Git metadata/tree/object/file existence; đọc source local tại SHA khóa, dry-run không ghi index; kiểm diff tài liệu.
- **Được source xác nhận:** những requests, thứ tự register, API/state bên dưới. Đây không phải bằng chứng silicon/timing hoặc Android USB hoạt động.
- **Đề xuất:** ranh giới các class, giới hạn payload, policy retry/firmware/PLP và test cases. Chưa triển khai/chạy, có thể điều chỉnh trước Phase 2.1 sau review.
- **Chưa có bằng chứng:** firmware provenance/hash/quyền phân phối, chip ID của thiết bị thật, warm/cold/reconnect thực, lock và TS continuity.

| Nguồn | SHA khóa, nhánh | Trạng thái mới đo |
|---|---|---|
| Android fork trankien84/AndroidDvbDriver | `4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe`, baseline modernization | Sạch trước tạo nhánh tài liệu; toolchain không thay |
| tbsdtv/linux_media | `2ce787de9f6e81b4294692a45de15596ae2e1322`, latest | Index thiếu, 81.399 staged D, sáu thư mục tồn tại nhưng đều 0 file |
| koreapyj/cxd28xx | `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, main | Sạch, source thực có trên đĩa |
| Till0196/coax | `11228b05fb1626bf41c4e1879ace10cd0a8d575c`, main | Sạch, source thực có trên đĩa |

Mỗi câu dẫn file/hàm trong các mục TBS/C/Go dùng đúng SHA ở bảng này; links khóa commit, không dùng tên nhánh động làm chứng cứ. Không fetch/sync/index/analyze. GitNexus không có project index; AgentMemory narrow lookup không có match, source/hồ sơ local là chính.

### Source readiness mới: object nghiên cứu đủ, checkout vẫn BLOCKED

Lệnh TBS dùng `git --no-lazy-fetch`, `GIT_NO_LAZY_FETCH=1`, `GIT_OPTIONAL_LOCKS=0`; không sửa config, không dùng recovery script.

| Kiểm tra | Quan sát 2026-11-03 | Kết luận |
|---|---|---|
| HEAD/remote/branch | Đúng SHA và origin official, latest | Mốc nghiên cứu xác định |
| `.git/index`, `index.lock`, `GIT_INDEX_FILE` | Index không có, lock không có, không override index | `ls-files=0`; không phải sparse sạch |
| Status | 81.399 `D` ở index | Giữ nguyên, không stage/commit reference |
| Sparse flags/list | core.sparseCheckout=true, cone=true; đủ sáu patterns | Configuration đúng không chứng minh file được materialize |
| Sáu thư mục thực | Tất cả hiện hữu, 0 file mỗi thư mục | Checkout FAIL/BLOCKED |
| Sáu selected trees | 785 paths/785 unique blobs, **0 missing**, 15.372.887 bytes uncompressed | Đọc protocol/frontend offline được |
| Cone ancestor files | 572 paths/572 blobs, **0 missing hiện tại** | Khác lịch sử 572 missing; không biết tác nhân đã bổ sung, phase này không tải |
| Toàn cone materialize dự kiến | 1.357 paths, kiểm tên Windows cơ bản 0 unsafe | Chưa là bảo đảm checkout thực hoặc toàn filesystem semantics |
| Default read-tree dry-run | exit 128 `invalid path drivers/gpu/drm/nouveau/nvkm/subdev/i2c/aux.c` | Lỗi Windows vẫn tái hiện; index/lock vẫn không được tạo |
| core.protectNTFS config query | Không có override lưu, exit 1 | Không đổi bảo vệ hệ thống/repository |

**Phân biệt rõ:** `tbs5530.c/.h`, `cxd2878.c/.h/_priv.h`, `m88rs6060.c/.h/_priv.h` và framework helpers đều có trong Git tree và **blob local**, nhưng không có trên đĩa/index. Chúng không nằm ngoài sáu patterns nên không được giải thích là “sparse cố ý bỏ qua”. `aux.c` có trong full HEAD tree ngoài sparse, không materialize nhưng Git Windows vẫn validate tên khi dựng full index. Không kết luận đây chắc chắn là nguyên nhân index mất lúc clone đầu vì thiếu log gốc.

**Dependency inventory đã đối chiếu tree + cat-file:**
- Board: `drivers/media/usb/dvb-usb/tbs5530.c/.h`; blob C `a1afafb96e952f07c399d1325a774e6f37974017` (10.257 bytes), header `7197e6013437a021255c335f1e1b64c1a44c2108`.
- Sony: `drivers/media/dvb-frontends/cxd2878.c/.h/cxd2878_priv.h`; C blob `c432fe5e6b8d35b8e21a532b1d36bd4a2471ba5a` (132.388 bytes), header 915db9a02612bfff40632c8bd123c868e600b03a, priv de0326ee8743a294ba728b4ed003839472baf1b4. Code Ascot3/Freia nằm trong C này, không giả định cần một ascot3.c riêng.
- Satellite để tra phụ thuộc tương lai: `m88rs6060.c/.h/_priv.h` local blobs có; không đưa vào scope triển khai T2 đầu.
- Framework: `dvb-usb.h`, `dvb-usb-common.h`, `dvb-usb-init.c`, `dvb-usb-firmware.c`, `usb-urb.c`; `dvb_frontend.c`, `include/media/dvb_frontend.h`, `dvb_math.h`, `include/uapi/linux/dvb/frontend.h`: tree/blob có, disk thiếu. Makefile map CONFIG_DVB_USB_TBS5530→tbs5530.o, CONFIG_DVB_CXD2878→cxd2878.o, CONFIG_DVB_M88RS6060→m88rs6060.o.
- Headers Linux trực tiếp cần diễn giải `include/linux/delay.h`, `i2c.h`, `usb.h`, `firmware.h`, `regmap.h`, `math64.h`: tree/blob local đều có, disk thiếu. Đây là inventory dependency phục vụ nghiên cứu userspace, **không** chứng nhận có toàn bộ transitive headers/build config để build kernel Linux.

### Kế hoạch phục hồi cần phê duyệt riêng — không thực thi

1. Dừng/điều phối mọi tác vụ Git khác trên reference. Khóa SHA, remote, refs, sparse rules và kiểm lại counts/blob availability; không giả định 572 ancestor vẫn thiếu hoặc sẽ luôn đủ.
2. Backup metadata `.git` và inventory working-tree hiện tại ở thư mục ngoài Git, giữ timestamp/hash/permissions và ghi index vốn không có. Backup phải private/local, không commit credentials/config. Kiểm metadata yên trước và trong backup; nếu đổi thì dừng.
3. **Phương án A — giữ NTFS, ít thay đổi:** sau phê duyệt riêng, xem xét command-scoped `-c core.protectNTFS=false` chỉ cho dựng index/working tree tại SHA khóa, cùng no-lazy-fetch và sáu cone rules không đổi. Có thể không cần tải object nữa vì 1.357 blob hiện đều local. Không lưu config, không reset/clean/reclone, không disable sparse hoặc materialize AUX ngoài patterns. Đây là phương án đề xuất, không cấp quyền gửi lệnh ngay.
4. **Phương án B — source trong filesystem ext4 của WSL2:** tạo bản tham khảo riêng tại đường dẫn ext4 được người dùng xác nhận, giữ repository NTFS nguyên trạng. ext4 xử lý AUX/case/symlink như Linux, nhưng Git Windows vẫn không phù hợp để thao tác index có tên reserved trên share WSL; phải dùng Linux Git. Cần phê duyệt riêng việc tạo bản sao, dung lượng metadata/objects và sửa workspace nếu muốn; không cài SDK trong WSL, không chuyển USB. Có thể tận dụng local objects sau khi kiểm tra closure, không hứa bundle/clone offline sẽ thành công nếu full tree còn promisor objects thiếu. Không clone toàn kernel để giải quyết một file board.
5. **Gate phục hồi:** HEAD/branch/remote không đổi; sáu rules giữ nguyên; index có; status sạch; 1.357 expected cone files (hoặc manifest phạm vi mới được duyệt) khớp tree/hash sau EOL normalization; đủ board/Sony/helper deps; không download ngoài kế hoạch/không file AUX ngoài scope.
6. **Rollback:** nếu thất bại, giữ cả trạng thái lỗi và backup, ngừng dùng checkout đó; chỉ hoàn nguyên các đường dẫn/metadata đã thay theo manifest sau review. Không đưa lệnh xóa hàng loạt, không hứa có index cũ để copy lại. Bản WSL mới nếu có sẽ được cô lập, không xóa tự động.

**Lựa chọn hiện tại:** giữ NTFS reference chỉ đọc, nghiên cứu blobs local; **research-readiness PASS có phạm vi, checkout-readiness BLOCKED**. Phê duyệt phục hồi phải là yêu cầu mới, không suy từ Phase 2.0. Full kernel build/driver installation không nằm trong kế hoạch.

### Mở rộng đối chiếu protocol và firmware

Nguồn official [tbs5530.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/usb/dvb-usb/tbs5530.c), funcs `tbs5530_op_rw`, `tbs5530_i2c_transfer`, `tbs5530_load_firmware`, `tbs5530_properties`; C cộng đồng [tbs5530.c](https://github.com/koreapyj/cxd28xx/blob/c5fa27eecbe80633cbb365cc44656daea1c0e2e3/tbs5530.c); Go [bridge/tbs5530](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/bridge/tbs5530/tbs5530.go) và [bridge/tbs](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/bridge/tbs/tbs.go).

| Chức năng | Byte/request được source xác nhận | Thiết kế an toàn đề xuất |
|---|---|---|
| EP0 control | Request type 0x40 OUT/0xc0 IN, value/index tùy request, timeout 2000 ms | Giữ request/value/index/buffer offset/length rõ ràng, kiểm return==length; negative/short/zero là lỗi, không lặp để nối phần thiếu của vendor transaction |
| I2C write | 0x80, value/index 0, payload `[n+1,addr7<<1,data...]` | addr 0..127, chỉ dùng write flags cho phép, chưa dùng helper send/recv gắn TEN; từ chối bare read |
| I2C register read | 0x90 OUT `[readLen,addr7<<1,register]`, sau đó 0x91 IN | Một byte pointer, cùng address cho write/read, khóa cả cặp; nếu 0x90 lỗi thì không gọi 0x91 |
| Giới hạn | Buffer 50 của official/C; Go write tối đa 62 | Khởi đầu thận trọng với write data 1..48 bytes/read 1..50 bytes để không vượt C buffers; đây là policy phần mềm, không khẳng định giới hạn phần cứng; từ chối trước I/O. Nâng giới hạn cần source/test riêng |
| TS route | 0x8a OUT `[0x0a,0]` T/C hoặc `[0x0a,1]` satellite | T2 chỉ dùng terrestrial; không coi selector là cơ chế stop/gate |
| Endpoints | EP0 cho vendor; 0x82 bulk TS; 0x81 generic_bulk_ctrl_endpoint config | Chọn interface ID 0/alternate có mô tả phù hợp, endpoint address 0x82+IN+BULK; không chọn endpoint(0)/IN đầu tiên hoặc yêu cầu OUT 0x01 |
| Firmware | 0xa0, reset registers 0x7f92/0xe600, stop 1 → data offsets từng 64 → restart 0 → 100 ms | Chỉ có planner/fake ở 2.1. Phần binary cuối không đọc quá độ dài; không tự retry upload/CPU write sau lỗi hoặc interrupt |

Official `dvb-usb-firmware.c::usb_cypress_load_firmware` là loader chung parse hexline, khác **device-specific raw contiguous image** (ảnh nhị phân thô liên tục riêng cho thiết bị) trong `tbs5530_load_firmware`; không tái dùng IntelHEX parser chỉ vì đều gọi A0. TBS no_reconnect=1 và nhánh cold của [dvb-usb-init.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/usb/dvb-usb/dvb-usb-init.c)::dvb_usb_device_init tiếp tục khi upload trả 0, không chờ enumerate. Đó là giả định của framework, **không đảm bảo** Android connection/permission/fd vẫn dùng được. Go board.open reload mỗi lần open cũng là policy của Go, không phải bộ phát hiện trạng thái warm/cold của phần cứng.

Firmware candidate do caller cung cấp phải có source/license/hash/board-image-format/allowed-address-map đã duyệt trước khi upload trong production. Giới hạn 0xe200 của Go là giới hạn implementation, không xác nhận layout của mọi blob. Đặc biệt, reset address 0x7f92 nằm dưới giới hạn 0xe200; vì vậy một giới hạn trên không chứng minh image không chồng control register. Fixtures giả lập của Phase 2.1 phải có allowed range/reserved addresses tường minh và từ chối overlap; chưa chọn giới hạn/hash của image thật khi chưa có blob được duyệt. Không bundle/download firmware, không coi tên file hoặc VID/PID là bằng chứng firmware đúng.

Lỗi giữa upload: vào **FAULTED/UNCERTAIN**, không chuyển READY, không tự restart CPU/reset hoặc tìm đường phục hồi phần cứng ngoài kế hoạch. Cleanup chỉ hủy nguồn TS/cancel workers/release interface/close host connection hợp lệ; không phải quyền gửi vendor writes để sửa trạng thái phần cứng. Upload thành công vẫn cần settle/probe/identity mới, chưa tự động reload khi fd cũ chết.

### Frontend, chip dispatch, tuning và PLP: điểm mới cần giữ

Official [cxd2878.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/dvb-frontends/cxd2878.c): `cxd2878_attach` L3997 lấy SLVT 0x64/SLVX 0x66, tuner 0x60 theo board; `cxd2878_init` L3171 clear SLVX 0x02, đợi 4 ms, thiết lập crystal/clock, đợi 2/2/21 ms, tuner bridge/repeater, tuner init, TS pins; `ascot3_init` L807 đọc tuner 0x7f và chọn crystal 24 MHz với code 0x18; `ascot3_tune` L560 nhận kHz; `set_frontend` L3699 chuyển Hz→kHz, gate-on/tune/gate-off/tuneEnd. Không rút ngắn sleep hoặc tự đổi register table vì userspace nhanh hơn.

**Khác biệt chip-ID thực trong code, chưa phải kết quả phần cứng:**
- Official `cxd2878_init/set_frontend`: Ascot3 cho **0x197/CXD6802**; Freia cho **0x19c/CXD6822** và **0x39a/CXD2878A**. Các enums 0x396/0x297 có trong header nhưng không tự đồng nghĩa đường tuner init đã được board này hỗ trợ.
- Macro SONY_TUNER_IS_ASCOT3 trong C [cxd2878.h](https://github.com/koreapyj/cxd28xx/blob/c5fa27eecbe80633cbb365cc44656daea1c0e2e3/cxd2878.h) mở thêm 0x396/0x297, Freia 0x19c/0x39a và Helene cho họ khác. Đây là mở rộng implementation, không tự chọn cho Android trước khi có fixture/hardware.
- Go `cxd2878.go::usesASCOT3` chỉ nhận 0x197, còn lại chọn Freia, trong khi `sony/chip.go::ReadChipID` nhận nhiều ID hơn. Do đó không port rule “mọi ID khác là Freia”; **ID chưa biết/ngoài phạm vi phải bị từ chối**, chỉ dùng bảng đã review tường minh cho 0x197/0x19c/0x39a khi mở rộng production. Phase 2.1 chỉ test bộ phân loại giả, chưa quảng cáo hỗ trợ chip.

**PLP (luồng con trong một kênh DVB-T2):** official `cxd2878_set_dvbt2` L2457 và C community L3300 đều ghi bank 0x23/register 0xaf=`stream_id&0xff`, 0xad=1 **trước** khi rẽ nhánh active/sleep; đây là lựa chọn thủ công, không có xử lý riêng NO_STREAM_ID_FILTER. Vì vậy, theo chứng cứ này, không gọi default 0xff hay zero là auto. Go `sony/terrestrial.go::PLPOrZero` ánh xạ số âm→0; `WakeupDVBT2` dùng manual 0xaf/0xad. Comment “chip has no automatic path” chỉ là khẳng định trong Go, chưa chứng minh khả năng của silicon.

**Khác biệt retune quan trọng:** khi active cùng system, Go `cxd2878.go::Wakeup` chạy `retuneBand`; nhánh T2 chỉ gọi SetBandwidth, không cập nhật PLP. Official ghi lại PLP mỗi lần set_dvbt2, kể cả cùng system. Thiết kế Android phải test rằng đổi PLP cùng frequency/bandwidth thật sự phát lệnh PLP và thu nhận lại tín hiệu, không copy lối tắt của Go. Đây là phát hiện logic source, không phải đã tái hiện lỗi tuner Go trên phần cứng.

Public Android `DvbFrontend.setParams`, `DvbDevice.tune` và `dvbservice/Request.REQ_TUNE` hiện chỉ có freq/bandwidth/system; ordinal DVBT,DVBT2,DVBC của DeliverySystem là wire API. **Chốt đề xuất 2.1:** có giá trị PLP int 0..255 ở core/internal tune config, từ chối -1/256; mặc định để tương thích là **manual PLP 0**, ghi rõ không auto/không scan mọi PLP. Chưa thêm TCP request hoặc UI PLP trong 2.1; API mở rộng tương thích phải là nhiệm vụ sau được duyệt. Giữ bandwidth ở Hz đủ 1_700_000, không `/1_000_000` rồi cắt thành 1 MHz và suy ra băng tần khác. Phase đầu ưu tiên 6/7/8 MHz; 5/1.7 chỉ sau khi bảng/fixture được review.

### Tích hợp Android: lựa chọn ít thay đổi và trạng thái

Các path Android bên dưới dùng baseline 4cf1b64. Kết quả Phase 0 vẫn hữu ích; Phase 2.0 chỉ chốt phần retry/lifecycle chưa đủ rõ.

- `drivers/.../usb/DvbUsbDeviceRegistry.java::Creator` trả **DvbDevice**, không bắt buộc là subclass DvbUsbDevice. Registry construction chỉ match descriptors, không được upload/probe I2C trong constructor/listDevices.
- `DvbUsbDevice.open/close` là final, tự retry power/readConfig/attach/init; tune/status cũng retry. **Hiệu chỉnh nghiên cứu cũ:** `Retry.retry(4)` thực chạy **tối đa 5 attempts** (một lần đầu+4 retry), không phải 4 attempts; I2cAdapter có 10 iterations. Do đó retry bên ngoài có thể lặp các tác động phụ; cleanup khi open lỗi không có finally đầy đủ. Không sửa chung Retry/framework trong phase này.
- **Đề xuất production adapter sau 2.1:** `Tbs5530DvbDevice extends DvbDevice` để sở hữu open/failure/close và bảo đảm tác động phụ chỉ xảy ra đúng một lần; tái dùng Creator, UsbPermissionObtainer, DvbDemux, DvbFrontend/DvbTuner, ByteSource/UsbBulkSource. Đây là điều chỉnh có lý do so với đề xuất Phase 0 extends DvbUsbDevice; không thiết kế lại framework hoặc ép các drivers cũ đổi lifecycle.
- `Tbs5530I2cAdapter extends I2cAdapter` dùng bridge core có khóa và chỉ thử một lần. Do base I2cAdapter tự chạy 10 iterations mà không phân loại lỗi, core phải chốt **FAULTED sau bất kỳ I/O thất bại**: lần gọi lại chỉ throw, **không I/O tiếp**. Sau này có thể xem hook protected cho retry policy trong task riêng nếu cần; 2.1 không sửa base I2cAdapter hoặc dùng retry 10 làm phục hồi phần cứng.
- TS production wrapper `Tbs5530TsSource implements ByteSource` kết hợp UsbBulkSource, chọn route T2 trước queue, cancel/release theo thứ tự; không coi alternate 0 là TBS gate. `DvbDevice.DataPump` start stream trước tune trong luồng app hiện có, nên cho queue chờ dữ liệu khi bridge/frontend ready mà chưa LOCKED; state STREAMING không khẳng định FE_HAS_LOCK. Retune phải ngăn dữ liệu kênh cũ bằng chiến lược demux reset/drain đã được test.
- Close tránh giữ control/session lock khi join DataPump để không deadlock; cancel read trước khi đóng fd; cleanup từng resource trong finally, không close connection đang được JNI dùng. `DvbDevice.close` hiện join không timeout và native cancel/free phụ thuộc lifecycle; đây là điều kiện production cần test, không được coi load-library smoke là PASS cho điều kiện này.
- `dvbservice` giữ loopback control/TS sockets và vòng đời mediaPlayback; `app` giữ luồng chẩn đoán và không tuner, không điều khiển USB giả để mở service. Không thêm satellite enums/lệnh wire trong 2.1.

```mermaid
flowchart TD
  App[app: diagnostic UI] --> Registry[Existing registry / Creator]
  Service[dvbservice: control and TS sockets] --> Registry
  Registry --> Device[Tbs5530DvbDevice - future adapter]
  Device --> Session[Tbs5530Session - bounded state and ownership]
  Session --> Bridge[Tbs5530Bridge - EP0 and I2C protocol]
  Session --> Frontend[Cxd2878Frontend - future DVB-T2 registers]
  Frontend --> Tuner[Ascot3 or Freia: explicit chip map]
  Tuner --> I2C[Existing I2cAdapter contract]
  Frontend --> I2C
  I2C --> Bridge
  Bridge --> Control[Tbs5530ControlIo - narrow transport seam]
  Control --> Fake[Phase2.1 ScriptedFakeControlIo only]
  Control --> Android[Future UsbDeviceConnection adapter]
  Session --> Plan[Tbs5530FirmwarePlan - synthetic fixtures only]
  Device --> TS[Tbs5530TsSource - future ByteSource wrapper]
  TS --> Bulk[Existing UsbBulkSource / native UsbXfer]
  Bulk --> Demux[Existing DvbDemux / InputStream]
  Demux --> Service
  Demux --> App
```

State model có session identity/generation, frontend state và stream flag riêng (không gộp thành enum “streaming=locked”):

| Event | State/result đề xuất | IO/retry/ownership |
|---|---|---|
| Discover/create | DETECTED | Chỉ đọc descriptors, 0 USB writes |
| open→permission | WAIT_PERMISSION | Không lock/chờ Future trên UI thread; deny/timeout/cancel→CLOSED, 0 firmware I/O |
| permission→connection | HOST_OPEN | Xác nhận descriptors/interface/EP 0x82, kiểm kết quả claim; không coi enumerated là warm |
| Approved firmware process | FW_TRANSITION | Plan giả lập ở 2.1; thực thi production cần phê duyệt/giấy phép/hash/address map |
| Write lỗi/detach/không rõ trạng thái sau upload | FAULTED hoặc DISCONNECTED | Invalidate generation/fd, cancel reads; 0 auto retry/reset/reupload |
| settle/probe success | BRIDGE_READY | Chưa test probe chính thức; cần kiểm permission/device identity mới |
| Sony init→tuner init/TS pins | FRONTEND_READY | Ánh xạ ID tường minh, từ chối ID chưa biết; gate trong finally; 0 satellite GPIO |
| tune(valid T2) | ACQUIRING→LOCKED hoặc NO_LOCK | Timeout không cho phép phát lại I/O; giới hạn tune/status, PLP/manual 0 rõ ràng |
| stream open | streamActive=true độc lập với LOCKED | Route T2 0x8a, queue 0x82; không cho 2 frontends stream song song |
| close/failure | CLOSED/FAULTED | Cancel/release/close đúng một lần; không vendor writes khi cleanup |

**Reconnect:** detach event vô hiệu hóa mọi callback cũ mang generation N; thiết bị re-enumerate ở generation N+1 phải được match VID/PID+descriptor/identity và xin permission mới nếu cần. Không tự open/upload từ broadcast hoặc coi cùng PID/path là cùng phiên firmware. Wait/probe có giới hạn thời gian dùng fake clock; I2C probe thật cũng là vendor OUT write nên không được thực hiện ở Phase 2.0/2.1 offline.

### Class/component và danh sách Phase 2.1

**Phase 2.1 được đề xuất là nền tảng giao thức offline, KHÔNG hoàn tất production driver.** Toàn bộ core dùng Java 8 thuần, không UsbDeviceConnection/Android context/JNI để kiểm thử độc lập; không đăng ký Creator, không xin USB permission, không có firmware executor thật. Chỉ thêm abstraction cần cho dependency injection, không thêm framework transport toàn cục.

Base path main: `drivers/src/main/java/info/martinmarinov/drivers/usb/tbs5530/` (chưa tạo):

| File dự kiến | Trách nhiệm và phạm vi 2.1 |
|---|---|
| `Tbs5530ControlIo.java` | Một method control(type,request,value,index,buffer,offset,length,timeout), trả count hoặc lỗi có phân loại; implementation chỉ dùng fake trong test |
| `Tbs5530Bridge.java` | Encode/validate EP0/I2C, cặp giao dịch nguyên tử, số byte trả về/policy lỗi; có thể lồng SessionState/endpoint choice value object nếu đủ gọn |
| `Tbs5530FirmwarePlan.java` | Kiểm bytes cùng policy/allowed ranges đã duyệt bằng hàm thuần, lập chunk plan bất biến; không đọc resource blob thật hoặc có upload API production |
| `Tbs5530Session.java` | State/generation/quyền sở hữu và guard cho session lỗi; clock/sleeper/lifecycle ports được inject và lồng tối thiểu khi cần cho test; không giả làm thiết bị vật lý |

Base path test: `drivers/src/test/java/info/martinmarinov/drivers/usb/tbs5530/` (chưa tạo):
- `ScriptedFakeControlIo.java`: trả kết quả theo kịch bản, copy buffer trước/sau, kiểm chính xác thứ tự lời gọi; lời gọi ngoài dự kiến làm test fail ngay.
- `Tbs5530BridgeTest.java`, `Tbs5530FirmwarePlanTest.java`, `Tbs5530SessionTest.java`: cases và assertions bên dưới; fake byte-source/lifecycle/clock lồng trong test khi chưa cần tái dùng.

Chỉnh sửa 2.1: chỉ cập nhật các hồ sơ STATUS/RESEARCH/DECISIONS/WORKLOG/TEST-MATRIX theo kết quả; **không cần sửa build.gradle/dependency** vì JUnit/Mockito đã có sẵn. Nếu phải sửa source khác để test, review phạm vi trước. Production files hoãn lại (không thuộc 2.1): `Tbs5530DvbDevice`, `Tbs5530DvbDeviceCreator`, `AndroidTbs5530ControlIo`, `Tbs5530I2cAdapter`, `Cxd2878Frontend`, `Ascot3Tuner`/`FreiaTuner`, `Tbs5530TsSource`. Registry/Java IDs/XML filter chỉ thêm cùng đợt production adapter được duyệt; devservice/app không đổi protocol, usbxfer/JNI không sửa trong nền tảng offline.

### Offline test strategy: assertions và tiêu chí

Tất cả hàng dưới **NOT RUN / PLANNED** ở Phase 2.0. Firmware fixtures được sinh từ bytes giả, không copy blob khi giấy phép chưa được duyệt. Fake không gọi USB/ADB/WSL/native.

| Nhóm test | Assertions bắt buộc / PASS | FAIL nếu |
|---|---|---|
| EP0 encoding | Types 0x40/c0, requests 80/90/91/8a, value/index/offset/length/timeouts đúng dự kiến, copy đúng mảng read | Sai byte/thứ tự/length, coi short return là thành công |
| I2C write | 1 byte→`[2,addr<<1,data]`; đúng biên 48; địa chỉ 7 bit/flags; length 0/49 hoặc địa chỉ sai bị từ chối với 0 I/O | Shift 2 lần, gửi bare-read/TEN, tràn buffer |
| I2C read | Gửi đúng bytes qua 0x90 rồi 0x91, khóa cả cặp để không bị chen ngang, biên read 1/50; length 51/nhiều pointer/địa chỉ không khớp bị từ chối với 0 I/O | Gọi 0x91 sau khi 0x90 lỗi, trộn bank read đồng thời, zero-fill response để che lỗi |
| Control short/timeout/error | Count -1/0/short đưa vào FAULTED; giữ cause/request context; lần gọi sau có 0 I/O bổ sung | Tự lặp gửi lại vendor write, bỏ qua exception |
| Firmware validation | Empty/sai digest/board policy/address-overlap/overflow bị từ chối trước mọi I/O; fixtures 1/63/64/65/128 có chunks chính xác, tail 1 không phải 64 | Suy hợp lệ từ filename, coi copyleft là quyền phân phối firmware, đọc quá tail |
| Firmware sequence fake | Stop 1 ở cả hai registers, A0 chunks theo thứ tự offset, restart 0 ở cả hai registers, chỉ đợi 100 ms sau kết quả đủ; lỗi ở mỗi bước→UNCERTAIN, không tự phát lại | Restart/READY sau lỗi, reupload không phê duyệt, callback mới dùng fd cũ |
| Endpoint selection | Các hoán vị của descriptor list giả bất biến luôn chọn ID 0/IN+BULK 0x82; từ chối thiếu/mơ hồ/sai type | Chọn 0x81/endpoint index 0/đòi OUT 0x01 |
| State/generation | Permission deny/detach/reconnect/cancel/callback muộn không writes; chỉ ready sau đủ điều kiện; no-lock khác lỗi I/O | Callback generation cũ mở lại, tiếp tục stream sau disconnected |
| Retries | Đúng 1 physical fake call cho tác động phụ; tối đa 2 attempts chỉ cho thao tác logic read-only đã review trước, chưa chốt lỗi; mặc định 0 retry | Retry CPU/I2C write/TuneEnd, base wrappers lồng nhau gửi lại |
| Resource cleanup | Inject lỗi ở từng acquire, assert cancel→release→close đúng một lần; close 2 lần có 0 I/O bổ sung; join fake workers không deadlock | Đóng fd trước cancel, rò rỉ, double-release, vendor writes khi cleanup |
| Frontend policy fixtures | Bảng ID tường minh 0x197/0x19c/0x39a; PLP 0/255 hợp lệ, -1/256 bị từ chối; đổi PLP cùng mode phải tạo yêu cầu retune mới; giữ đúng Hz/kHz và 1.7 MHz | Mặc định ID khác là Freia, gọi manual 0 là auto, bỏ qua thay đổi PLP |
| TS/demux fake | Packet 188 giả lập bị tách ở ranh giới read vẫn khôi phục đúng thứ tự/PIDs; stop/reopen không lọt packets cũ; stream flag độc lập với lock | Test chỉ đếm mà không assert payload, coi streaming đồng nghĩa lock |

Retry read-only có nghĩa **thao tác logic không làm thay đổi phần cứng**; register read của TBS thực hiện 0x90 OUT nên mặc định **không tự retry** khi chưa rõ trạng thái bus. Nhóm frontend/TS fixtures ở 2.1 chỉ kiểm policy/hợp đồng giá trị/fake session, không dựng register tables CXD hoặc thư viện MPEG parser mới. Nếu cần test DvbDemux thật với Android NativePipe, dành cho phase instrumentation/fixtures ghi sẵn offline riêng; không lấy plain JVM PASS làm bằng chứng cho phần đó.

### Risk ranking và kế hoạch 2.1 có thứ tự

| Mức ảnh hưởng | Rủi ro | Khả năng kiểm thử / xử lý |
|---|---|---|
| Critical | Lặp firmware/reset khi retry hoặc sai device identity | Fake kiểm chính xác thứ tự/chốt state lỗi ở offline; reconnect thật cần phê duyệt phần cứng |
| High | Lỗi index checkout TBS/AUX, nguồn gốc source | Đã đo tree/blob manifest; recovery BLOCKED chờ phê duyệt, không chặn core thuần dùng local blobs |
| High | PLP manual khác auto; Go retune khi active bỏ PLP | Fixture chuẩn theo official 0xaf/0xad; hoãn mở rộng public wire API |
| High | Xung đột chọn nhánh theo chip ID/silicon chưa biết | Ánh xạ tường minh, từ chối ID chưa biết; chưa chạy bước kiểm chip ID/tuner trên phần cứng |
| High | Native cancel/vòng đời fd/demux thread deadlock | Fakes kiểm được thứ tự; JNI stress/Android USB thật là điều kiện production chưa đạt |
| High | Android permission/detach/re-enumeration | Fake generation+Future timeout; test riêng dialog/receiver/Android Host |
| Medium–High | Nhầm EP0/0x81/0x82, số byte/timeouts | Hoán vị descriptors/kiểm requests chính xác/lỗi short read ở offline |
| Medium | Service/app stream trước tune, ordinal public API | Giữ Request/DeliverySystem, state của native wrapper độc lập, giữ các điều kiện kiểm hồi quy hiện có |
| High | Firmware provenance/giấy phép và khả năng phân phối APK | Giữ notices và kiểm giấy phép từng file; firmware hash/nguồn gốc/quyền phân phối vẫn BLOCKED |

**Thứ tự Phase 2.1 đề xuất:** (1) review ADR-010 và phạm vi offline; (2) khóa SHA của nguồn fixture/hợp đồng biên; (3) viết ScriptedFakeControlIo cùng tests ban đầu thất bại để kiểm exact bytes; (4) triển khai bridge/payload+endpoint selector chỉ với fake; (5) kiểm firmware/lập chunk plan/address policy và fake chuỗi lỗi; (6) fake session states/generation/cleanup/retry policy; (7) chạy JUnit tập trung nhóm TBS, rồi toàn bộ unit tests drivers/usbxfer/dvbservice và debug/lint nếu chạm main; (8) cập nhật matrix/lệnh/fixtures/nguồn gốc rồi dừng, không đăng ký board hoặc mở USB thật. Không cần nâng toolchain/cài dependency/images hoặc phục hồi full kernel để bắt đầu core offline.

**Tiêu chí ra Phase 2.1:** mọi case đã cam kết có assertion thật và được chạy; 0 I/O ngoài fake, 0 regression của drivers cũ, registry/XML/Request ordinals không đổi; firmware không được bundle hoặc gửi; đủ state/failure tests trước khi xem xét production adapter. Chỉ mở kiểm thử phần cứng theo kế hoạch được thông qua riêng, không hứa DVB-T2 lock từ unit PASS.

## 2026-11-02 — Migration stable mới trên JDK 25 và ngoại lệ API 21

**Phạm vi phê duyệt:** người dùng lúc 13:06 +07 cho nâng build/dependencies và sửa code tương thích; ask_user xác nhận giữ Android minSdk 21 và references local chỉ đọc. Nhánh mới `chore/modernize-android-build`, nền SHA `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`; không commit/push. Không triển khai TBS driver/hardware.

**Nguồn phiên bản truy xuất lại:**
- [Gradle releases](https://gradle.org/releases/) xác nhận 9.8.2; [checksum binary-only](https://services.gradle.org/distributions/gradle-9.8.2-bin.zip.sha256) redirect downloads.gradle.org, SHA256 21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607. Wrapper tải và --version chạy trên JDK 25.0.5.0.3, exit 0; sau đó sinh lại wrapper scripts/JAR bằng Gradle wrapper task.
- [AGP API table](https://developer.android.com/reference/tools/gradle-api) ghi stable 9.4.1; POM 9.4.1 trên Google Maven tồn tại. [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes): Gradle min 9.6/JDK min 17/API 37; 9.8.2+JDK 25 đã chạy configuration/debug build thực. Không gọi AGP 9.5 rc là latest stable.
- Android CLI `sdk list --all` và source.properties xác nhận các phiên bản stable được chọn: Build Tools 37.0.0, platform 37.2, CMake 4.1.2, NDK 30.0.16248370. [NDK downloads](https://developer.android.com/ndk/downloads), [r30 changelog](https://github.com/android/ndk/wiki/Changelog-r30) xác nhận r30/latest LTS; metadata local min 21/max 37. Chỉ cài 4 packages mới đã khai báo, không cài images.
- [CompileSdkSpec DSL](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/dsl/CompileSdkSpec): `compileSdk { version = release(37) { minorApiLevel = 2 } }`; target 37 riêng, giữ min 21. Bytecode Java 8 độc lập với JVM 25, không tăng lên 25.
- [AppCompat notes](https://developer.android.com/jetpack/androidx/releases/appcompat): 1.8.0 min 23 → chọn 1.7.1. [Material 1.14](https://github.com/material-components/material-components-android/releases/tag/1.14.0) min 23 → chọn [1.13.0](https://github.com/material-components/material-components-android/releases/tag/1.13.0) min 21. Không dùng tools:overrideLibrary để ép runtime Android cũ.
- [AndroidX test notes](https://developer.android.com/jetpack/androidx/releases/test): stable ext-junit 1.3.0/Espresso 3.7.0 và core 1.7.0 nâng min lên 21, không phải 23; chọn các bản stable này. Metadata Maven latest có thể là alpha; đã lọc stable theo release notes.
- [Mockito metadata](https://repo.maven.apache.org/maven2/org/mockito/mockito-core/maven-metadata.xml) và javadoc latest xác nhận 5.24.0. Bỏ Mockito 1/mockito-all/PowerMock; giữ JUnit 4.13.2 theo test framework hiện hữu, không áp JUnit Jupiter major chỉ vì latest.

**Các thay đổi tương thích đã làm:**
- AGP 9 library DSL bỏ targetSdk ở defaultConfig; gỡ consumer keep-file được khai báo nhưng không tồn tại, không tạo file rỗng để che lỗi. Groovy assignment đổi sang `=`; gỡ global BuildConfig/nonFinalResIds cũ (source không dùng BuildConfig/case R.id), giữ AndroidX/non-transitive R.
- Native pin NDK 30/CMake 4; tăng minimum CMake lên 3.22.1 vì CMake 4 bỏ compatibility 3.3.1. Không đổi logic JNI/USB hoặc bỏ ABI; giữ Release `-O1` hiện hữu (AGP release native variant thực là RelWithDebInfo, không suy ra cờ Release này có tác dụng với mọi config).
- Debug javac FAIL thật vì localbroadcastmanager không còn là dependency gián tiếp của UI dependency mới; thêm **direct** dependency 1.1.0 (latest/retired, không refactor event bus), build lại PASS.
- RequestTest chuyển static Log mock từ PowerMockRunner sang Mockito MockedStatic với cleanup After; openMocks/close có lifecycle. Matcher cũ đổi sang ArgumentMatchers.isNull cho getExternalFilesDir(null), vì anyString của Mockito mới không match null.
- StackTraceSerializerTest thay newline hardcode bằng System.lineSeparator để kiểm đúng PrintWriter output trên Windows, không đổi serializer runtime. Lượt unit đầu có 13 service tests, 2 fail; sau sửa assertion/mock, **31 methods tổng cả 3 module đều 0 fail**.
- `android.builder.sdkDownload=false` kiểm soát dung lượng; thiếu packages thì báo lỗi thay vì cài ngầm. Task editor truyền đúng môi trường process; không sửa global JAVA_HOME/SDK.

**Phạm vi ảnh hưởng:** GitNexus:list_repos kiểm lại không có project index; không rebuild. Kiểm impact trực tiếp từ source ở Gradle modules, native config, test mocks, registry/protocol tests. Không chạm public DeliverySystem enum/Request ordinal/USB driver logic. Không dùng index rỗng làm bằng chứng an toàn.

**Kiểm chứng cuối:** debug assemble PASS với 4 native ABIs; unit XML có 31 methods, 0 fail (gồm 1 utility); instrumentation APK compile PASS (chưa chạy trên Android). ZIP align 16 KiB PASS cho debug+release; ELF 64 bit LOAD 0x4000 ở debug+release, 32 bit LOAD 0x1000 riêng.

Release/lint ban đầu lỗi filesystem Windows ở output native; chạy riêng ABI x86_64 với max-workers 1 PASS. Sau đó lượt 980, `:app:lintDebug :app:assembleRelease :app:bundleRelease --max-workers=1`, **exit 0/3m54s**, 286 tasks. Lint XML có 0 errors/11 warnings, đã sinh release APK unsigned/AAB. Lượt 983 chốt debug/tests/instrumentation compile, exit 0/36s. Không chứng minh mọi môi trường đều cần max-workers 1; đây là command đã vượt lỗi Windows quan sát được, chưa xác định nguyên nhân gốc của lock. Không sửa source JNI/OS để né lock. Runtime Android/16 KiB/USB vẫn NOT RUN; xem TEST-MATRIX/WORKLOG.

**Ngoại lệ lint minh bạch:** 2 GradleDependency warnings cho AppCompat/Material được pin phiên bản để giữ API 21; 9 warnings UI/resources ở các file không chỉnh trong migration. Không dùng baseline/abortOnError=false/suppress để bỏ errors. Original baseline chưa chạy nên không khẳng định warning đã được tái hiện trên toolchain cũ hoặc đã có lịch sử PASS.

## 2026-11-02 12:52 +07 — Kế hoạch khôi phục TBS: dry-run và giới hạn Windows

**Môi trường/mốc:** Windows PowerShell, Git 2.56.0.windows.3, TBS `latest` / `2ce787de9f6e81b4294692a45de15596ae2e1322`; main `master` / `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`. Chỉ dò và dry-run, **chưa khôi phục reference**. Mọi lệnh Git đọc TBS dùng `--no-lazy-fetch`, cùng `GIT_NO_LAZY_FETCH=1`, `GIT_OPTIONAL_LOCKS=0`.

**Kết quả mới:**
- Index và index.lock không có; GIT_INDEX_FILE không bị override. Status vẫn có 81.399 staged D; đủ 6 cone paths nhưng cả 6 có 0 file. Không chỉ frontend bị thiếu.
- `git ls-tree -r HEAD -- <six-paths>`: 785 paths/blob; `cat-file --batch-check`: **785/785 blob có sẵn**.
- Cone sparse rules còn giữ file tại root và các ancestor (`drivers`, `drivers/media`, `drivers/media/usb`, `include`, `include/uapi`, `include/uapi/linux`). Kiểm thêm 572 ancestor files: **572/572 blob thiếu**. Tổng cone dự kiến 1357 paths; kiểm tên thiết bị Windows/reserved names và ký tự không hợp lệ trong tập này: 0 lỗi. Đây là kiểm tên cơ bản, chưa phải checkout thực hay bảo đảm toàn bộ quy tắc của filesystem.
- `git --no-lazy-fetch -C <TBS> read-tree --dry-run -m -u HEAD`: **exit 128**, `invalid path 'drivers/gpu/drm/nouveau/nvkm/subdev/i2c/aux.c'`. Git kiểm đường dẫn toàn cây index dù file nằm ngoài sparse selection; AUX là tên đặc biệt trên Windows.
- `git --no-lazy-fetch -c core.protectNTFS=false -C <TBS> read-tree --dry-run -m -u HEAD`: **exit 0**. `-c` chỉ áp dụng cho lệnh này; `config --show-origin --get core.protectNTFS` không có cấu hình được lưu, exit 1. Index vẫn không tồn tại sau dry-run.
- **Không suy ra dry-run thành công là restore offline thành công:** 572 blob ancestor thiếu sẽ cần lấy từ upstream nếu giữ nguyên cone checkout. Không tự fetch hoặc bỏ no-lazy-fetch để vượt chặn.
- FETCH_HEAD mtime khi bắt đầu lượt này là 12:50:47 +07 (trước các phép dò 12:52); không dùng nó làm kết quả thao tác trong lượt này.

**Phân biệt:** invalid AUX path là lỗi tái hiện được của thao tác phục hồi trên Windows; việc nó đã gây mất index ở lần clone ban đầu chỉ là giả thuyết, chưa có log nguồn gốc.

### Kế hoạch đề xuất cho người dùng xem xét — chưa được thực thi

1. Người dùng xác nhận không có Git sync/checkout khác đang chạy; giữ nguyên HEAD TBS. Bảo toàn metadata hiện có vào backup ngoài repository, kèm inventory source/untracked, sparse rules, refs, config và ghi nhận index hiện không tồn tại. Không sửa hay xóa metadata gốc; backup có thể chứa URL/config riêng nên giữ local, không commit/upload.
2. **Giữ nguyên sáu cone paths và SHA.** Chuẩn bị hướng dẫn để người dùng tự khôi phục reference bằng cách xử lý chỉ áp dụng cho từng lệnh Git, không đổi core.protectNTFS global/local. Cần đồng ý việc lấy 572 blob còn thiếu từ origin tại SHA khóa; tải objects không đồng nghĩa pull/merge/update branch. Không dùng full sync script.
3. Chỉ sau backup/approval và kiểm lại tên của tập file thực materialize mới dựng index+working tree ở HEAD đã khóa. Không reset --hard/clean/reclone, không disable sparse checkout, không chạm hardware. Nếu Git đòi tải ngoài phạm vi hoặc lỗi khác thì dừng, không tự đổi chiến lược.
4. Điều kiện sau phục hồi: HEAD/branch/remotes không đổi; 6 patterns giữ nguyên; index tồn tại; status sạch; đủ 1357 cone paths dự kiến và các source/dependencies bắt buộc; 0 file ngoài sparse không mong muốn; hash source khớp HEAD. Không chạy build TBS kernel hoặc driver.
5. Rollback phải theo backup và từng thay đổi thực ghi nhận; không đưa lệnh xóa hàng loạt. Chưa có index cũ để đơn giản copy lại nên không hứa rollback một lệnh. Nếu cần rút lui, dừng và bảo toàn cả trạng thái sau lỗi trước khi người dùng quyết định.

**Phương án offline:** giữ reference nguyên trạng và tiếp tục nghiên cứu 785 blobs có sẵn bằng git show; checkout chưa sẵn sàng. Thay cone patterns để bỏ ancestor hoặc dùng export riêng là thay đổi phạm vi/workflow khác, không tự áp dụng chỉ để tránh tải.

**Quyết định người dùng:** đã chọn `prepare-user-recovery` qua ask_user: soạn bộ lệnh để **người dùng tự chạy**, có backup và lấy objects thiếu tại SHA khóa. Không phải cho phép agent thực thi phục hồi reference.

**Bộ lệnh bàn giao:** `Restore-TbsSparseCheckout-Reviewed.ps1`, lưu ngoài Git trong session artifacts; không copy script hay backup vào reference/app source. Mặc định preview chỉ đọc; `-Apply -AllowObjectDownload` yêu cầu xác nhận `KHOI-PHUC-TBS` trước backup/fetch/read-tree. Script kiểm HEAD/branch/origin/sparse rules/tree rỗng, các đường dẫn sẽ tạo có tên hợp lệ trên Windows, locks và metadata không đổi trong 3 giây. Sau đó sao lưu toàn bộ .git ngoài repo, fetch chính xác OIDs của blobs thiếu bằng `--stdin --no-write-fetch-head --no-tags --no-recurse-submodules`, không có ref đích; chỉ một read-tree dùng `-c core.protectNTFS=false`. Không bỏ bảo vệ cho toàn bộ config. Nếu origin không phục vụ blob OIDs, dừng, không tự dùng phương án khác.

**Kiểm chứng thực:** PowerShell Parser có 0 errors; chạy script **không tham số** trả HEAD khóa, 1357 cone files, 572 missing blobs; index vẫn thiếu. SHA256 script `EFC40DE33FB4C202C49FB720DD569CB65A6B234B1F0055BD8CEC270D61AD97F5` (13:01:40+07). Apply/backup/fetch/restore/post-restore checks **NOT RUN**, script chưa được kiểm xuyên suốt. Sau apply, script chỉ PASS khi HEAD/branch/origin không đổi, status sạch, đủ file bắt buộc và tập cone, hash nội dung sau chuẩn hóa EOL/filter khớp blobs. Không tự rollback xóa dữ liệu nếu fail.

**Cảnh báo môi trường dùng chung:** FETCH_HEAD mtime tiếp tục thay đổi giữa các kiểm tra chỉ đọc (12:50:47 →12:56:56). Không xác định được tác nhân; **phải dừng Git sync/auto-fetch khác trước người dùng chạy Apply**, script sẽ từ chối nếu phát hiện metadata thay đổi khi kiểm/backup. Cửa sổ yên 3 giây không bảo đảm không có tác nhân khởi động sau đó. Người dùng phải phối hợp quyền truy cập, không xóa lock cưỡng bức. Không bắt đầu migration/build/hardware.

## 2026-11-02 — Khóa phiên bản nguồn trước audit Phase 0

- Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`; lệnh `git -C <repo> rev-parse HEAD`, `branch --show-current`, `status --porcelain=v1 -uall`, `remote -v` (chỉ đọc; không fetch).
- Android `signalwareltd/AndroidDvbDriver`, `master`: `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`; 18 file untracked ban đầu.
- TBS `tbsdtv/linux_media`, `latest`: `2ce787de9f6e81b4294692a45de15596ae2e1322`; 81.399 thay đổi hiển thị, cần chẩn đoán index trước khi coi checkout sẵn sàng. Sparse list có đủ sáu thư mục yêu cầu.
- C `koreapyj/cxd28xx`, `main`: `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`; working tree sạch.
- Go `Till0196/coax`, `main`: `11228b05fb1626bf41c4e1879ace10cd0a8d575c`; working tree sạch.
- Các SHA này là mốc nghiên cứu local, không chứng minh đã đồng bộ upstream mới nhất. Không sửa repository tham khảo hoặc index.

## 2026-11-01 — TBS5530 baseline source comparison

**Question:** Which source implementations are suitable for the Android userspace port?

**Sources:**
- Official hardware reference: https://github.com/tbsdtv/linux_media/blob/latest/drivers/media/usb/dvb-usb/tbs5530.c (record exact checkout commit SHA when cloned)
- Community C Linux driver: https://github.com/koreapyj/cxd28xx/blob/main/tbs5530.c
- Community Go userspace driver: https://github.com/Till0196/coax/blob/main/internal/coaxd/usb/bridge/tbs5530/tbs5530.go
- Android destination: https://github.com/signalwareltd/AndroidDvbDriver

**Source-grounded observations:** TBS Linux version includes Cypress FX2 firmware handling, vendor control requests and tuner configuration. The C community driver derives from the official implementation and uses its own URB streaming. Go `coax` contains a userspace TBS5530 USB bridge implementation for comparison.

**Hardware evidence provided by user:** WSL2/PyUSB descriptor enumeration and claim/release succeeded; bulk IN endpoints 0x82 and 0x81 present.

**Still hypotheses / requires verification:** Whether the Go and community C initialization sequence exactly matches the user's device; whether firmware upload resets USB/IP; whether DVB-T2 lock/streaming works under Android. Do not conflate the mere presence of code with verified hardware operation.

**Next experiment:** Verify Android toolchain and baseline build. Firmware upload only under explicit, reviewed hardware test plan.

---

## Template for future research

### YYYY-MM-DD — Title
**Question:**
**Source links, commit SHA, file/function:**
**Source-grounded findings:**
**Actual hardware test evidence:**
**Assumptions/uncertainties:**
**Decision or next verification:**

## 2026-11-02 — Readiness repository và đối chiếu TBS5530 (Phase 0)

**Phạm vi:** chỉ đọc source; không biên dịch/chạy driver. Windows PowerShell tại root AndroidDvbDriver. Mọi tham chiếu trong mục này dùng SHA đã khóa ở đầu tài liệu, không dùng nhánh chuyển động làm bằng chứng.

### Git, sparse checkout và workspace

| Repository | Nhánh / SHA | Kết quả trực tiếp |
|---|---|---|
| AndroidDvbDriver | `master` / `e4c13a5b86ad5ef9be887df58226046dcb42dcd5` | 18 file untracked có sẵn trước audit; không có tracked modification ban đầu; origin đúng fork `signalwareltd/AndroidDvbDriver` |
| TBS | `latest` / `2ce787de9f6e81b4294692a45de15596ae2e1322` | **BLOCKED checkout:** `.git\index` không tồn tại, `git ls-files` = 0; `status -uall` và `diff --cached --name-status --no-renames` báo 81.399 `D` (staged deletions) |
| cxd28xx | `main` / `c5fa27eecbe80633cbb365cc44656daea1c0e2e3` | Clean, origin `koreapyj/cxd28xx` |
| coax | `main` / `11228b05fb1626bf41c4e1879ace10cd0a8d575c` | Clean, origin `Till0196/coax` |

- Lệnh `sparse-checkout list`, `config --get core.sparseCheckout`, `config --get core.sparseCheckoutCone`: đủ **6 patterns**, cả hai flag true. Sáu thư mục hiện hữu nhưng không có source cần thiết; riêng `drivers\media\dvb-frontends` có **0 file**. Có thư mục/pattern không đồng nghĩa checkout hoàn chỉnh.
- `Test-Path`, `git ls-tree HEAD -- <path>`, `git ls-files --stage -- <path>`: `tbs5530.c/.h`, `cxd2878.c/.h/_priv.h`, `m88rs6060.c/.h/_priv.h`, `dvb-usb.h`, `dvb_frontend.c`, `dvb_demux.c` **thiếu trên đĩa và index**, nhưng tồn tại trong cây HEAD.
- `git cat-file --batch-check` xác nhận các blob cần nghiên cứu có sẵn local. Ví dụ `tbs5530.c` = `a1afafb96e952f07c399d1325a774e6f37974017`, 10.257 bytes; `cxd2878.c` = `c432fe5e6b8d35b8e21a532b1d36bd4a2471ba5a`, 132.388 bytes. Đọc bằng `git show HEAD:<path>`, **không restore** file tham khảo.
- Cảnh báo `too many active changes` nếu xuất hiện phù hợp với 81.399 staged deletions, không phải bằng chứng đã sửa hàng chục nghìn source. Nguyên nhân trực tiếp có bằng chứng là index thiếu; nguyên nhân khiến index mất chưa rõ. Không tăng giới hạn VS Code để che cảnh báo, không reset/checkout/sparse-reapply khi chưa duyệt phương án bảo toàn trạng thái.
- Workspace JSON parse được; cả 4 đường dẫn tương đối resolve đúng repo. Nhãn READ-ONLY không khóa hệ điều hành. `TBS5530.code-workspace` **bị ignore bởi `.gitignore:73` (`*.code-workspace`)**, chưa được version-control. Không sửa ignore trong Phase 0.
- Sync script parse bằng `System.Management.Automation.Language.Parser.ParseFile`: **0 lỗi cú pháp**. Root suy ra đúng từ `AndroidDvbDriver\tools`; log ở parent `logs`; 6 sparse paths và required file checks đúng. Tuy nhiên `-CheckOnly` vẫn **fetch**, tạo thư mục/log; full mode còn có clone/merge/sparse set/restore. Main chỉ fetch; references kiểm tra remote/clean/upstream trước fast-forward. Nhánh dirty reference bị skip trước kiểm tra sparse. **Không chạy script, kể cả CheckOnly**, trong Phase 0.

**Giới hạn no-fetch cần bàn giao minh bạch:** không gọi `git fetch/pull/clone` tường minh. Lệnh chẩn đoán ban đầu `git diff --cached --shortstat` trên partial clone chạy quá 30 giây, đã dừng process tree; lúc đó chưa đặt `GIT_NO_LAZY_FETCH`. Sau đó quan sát `.git\FETCH_HEAD` có mtime `2026-11-02 12:23:15 +07`, nội dung vẫn SHA TBS đã khóa. Không có baseline mtime trước lệnh nên **không chứng minh được** do lazy fetch của Git hay tác nhân khác trên môi trường dùng chung; không tuyên bố tuyệt đối không có fetch ngầm. Mọi lần đọc object sau đó đặt `GIT_NO_LAZY_FETCH=1`, `GIT_OPTIONAL_LOCKS=0`; kiểm tra object trước khi dùng. Không sửa index/working tree/HEAD references. Pack mới nhất quan sát có mtime 00:04:14, trước audit; điều này không chứng minh không có trao đổi mạng.

### Nguồn chính hãng — protocol ban đầu

Repository **TBS**, SHA `2ce787de9f6e81b4294692a45de15596ae2e1322`:

- [tbs5530.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/usb/dvb-usb/tbs5530.c): `tbs5530_op_rw` dùng control endpoint 0, request type vendor IN/OUT (`0xc0`/`0x40`), timeout 2000 ms; không phải gửi I2C qua bulk `0x81`.
- Cùng file, `tbs5530_i2c_transfer`: 2 messages (ghi một byte register rồi đọc) → request `0x90` payload `[readLength, address<<1, register]`, sau đó `0x91` đọc; 1 message → `0x80` payload `[writeLength+1, address<<1, data...]`. Lock bao trọn giao dịch. Đây là **address I2C 7-bit**, byte trên đường truyền đã shift; tránh shift hai lần.
- Hàm trên dùng buffer 50 bytes, không kiểm tra đầy đủ length/return của control calls, trả `num` cả khi truyền lỗi. Port Android phải kiểm tra số byte thực, giới hạn payload và giữ nguyên lỗi; không sao chép việc che lỗi. Không coi tên capability `I2C_FUNC_I2C` là hỗ trợ mọi kiểu giao dịch.
- `tbs5530_load_firmware`, `tbs5530_properties`: firmware `dvb-usb-id5530.fw`, `no_reconnect=1`; request `0xa0`, stop CPU tại `0x7f92`/`0xe600`, chunks `0x40`, restart, đợi 100 ms. Loop gốc luôn gửi 64 bytes kể cả cuối image; cần fixture thử image không chia hết 64, lỗi giữa chừng và short transfer. `no_reconnect` là giả định source, không phải quan sát USB/IP/Android thật.
- `tbs5530_cfg`, `tbs5530_frontend_cxd2878_attach`: demod `0x64`, tuner `0x60`, cả hai crystal 24 MHz; TS mode 1, serial-data 0, clock 1/mask 1. [cxd2878.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/dvb-frontends/cxd2878.c): `cxd2878_attach`, `cxd2878_init`, `cxd2878_set_frontend`, `cxd2878_set_dvbt2`, `ascot3_init/tune`, `freia_init/tune`, `cxd2878_read_status`; local dependencies `cxd2878.h`, `cxd2878_priv.h`, media DVB/I2C APIs. Ascot3/Freia chọn theo chip ID; **không xác nhận tuner silicon chỉ từ tên board**.
- `tbs5530_frontend_m88rs6060_attach`: Montage demod `0x69`, tuner `0x2c`, clock 27 MHz, `i2c_wr_max=33`, common TS; board GPIO `0xb7 [0,0]`, `0x8a [8,1]`. [m88rs6060.c](https://github.com/tbsdtv/linux_media/blob/2ce787de9f6e81b4294692a45de15596ae2e1322/drivers/media/dvb-frontends/m88rs6060.c) phụ thuộc `_priv.h`, `.h` và có `request_firmware`. Chỉ khảo sát để chừa extension; không khởi tạo phần satellite trong thiết kế DVB-T2-first trước khi có bằng chứng cần thiết.
- `tbs5530_ter_cable_streaming_ctrl` → `0x8a [0x0a,0]`, satellite → `[0x0a,1]`; không dùng `onoff` để gate stream. `tbs5530_properties`: một adapter, hai frontends dùng **chung TS bulk `0x82`**, 8 URB × 4096 bytes; `generic_bulk_ctrl_endpoint=0x81` là trường cấu hình khác, không phải TS. Không thiết kế stream T2 và satellite đồng thời.

### Đối chiếu cộng đồng C

Repository **cxd28xx**, SHA `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`:

- [tbs5530.c](https://github.com/koreapyj/cxd28xx/blob/c5fa27eecbe80633cbb365cc44656daea1c0e2e3/tbs5530.c): `tbs5530_op_rw`, `tbs5530_i2c_transfer`, `tbs5530_load_firmware` giữ hình dạng requests/firmware gốc; vẫn có các giới hạn xử lý length/error tương tự, không mặc định là bản đã sửa mọi lỗi.
- `tbs5530_probe`, `tbs5530_dvb_init` tự đăng ký I2C/DVB; `tbs5530_start_feed/stop_feed`, `tbs5530_start_streaming`, `tbs5530_urb_complete` thay framework dvb-usb bằng URB riêng, feed counter, `active_fe`, resubmit/error counters và `dvb_dmx_swfilter`. [tbs5530.h](https://github.com/koreapyj/cxd28xx/blob/c5fa27eecbe80633cbb365cc44656daea1c0e2e3/tbs5530.h): 8 URB × **65536 bytes**, khác official 4096. Đây là thay đổi buffering/ownership, **chưa chứng minh** nhanh hoặc đúng hơn trên Android.
- `tbs5530_fe_ter_init/sat_init` chọn `active_fe=0/1`; start streaming vẫn route chung `0x82` bằng `0x8a`. ALP/ATSC3 có riêng callback, sysfs stats và parser; ngoài phạm vi DVB-T2/MPEG-TS phase đầu.
- [cxd2878.c](https://github.com/koreapyj/cxd28xx/blob/c5fa27eecbe80633cbb365cc44656daea1c0e2e3/cxd2878.c): `cxd2878_set_frontend` gọi init khi chưa warm, chọn DVB-T2, mở I2C repeater để tune Ascot3/Freia theo chip ID, đổi Hz→kHz, tắt repeater rồi tuneEnd. `cxd2878_set_dvbt2`/`read_status` là vị trí đối chiếu PLP, bandwidth và lock. Không import ALP/network kernel vào Android chỉ để hỗ trợ DVB-T2.

### Đối chiếu Go userspace

Repository **coax**, SHA `11228b05fb1626bf41c4e1879ace10cd0a8d575c`:

- [bridge/tbs/tbs.go](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/bridge/tbs/tbs.go): `ControlWrite/Read` kiểm tra đủ byte; `LoadFirmware` kiểm tra size `1..0xe200`, chunk cuối `min(offset+64,len)`, kiểm tra lỗi; `Write` có max payload 62. Các giới hạn này là quyết định implementation Go, chưa phải giới hạn phần cứng được đo.
- [bridge/tbs5530/tbs5530.go](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/bridge/tbs5530/tbs5530.go): `Read` từ chối bare read (không suy diễn request `0x93` của board khác); `WriteRead` yêu cầu đúng một byte register; `SelectStreamSource` giữ selector official; `InitializeSatellite` tách GPIO riêng. Chưa thấy kiểm tra readLength tương đương giới hạn 50 của C trong hàm `WriteRead`, nên không copy mù.
- [board/tbs5530/tbs5530.go](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/usb/board/tbs5530/tbs5530.go): `Layout` báo một tuner logic; `open` tải firmware mỗi lần mở và khởi tạo cả Sony/Montage; `Tune`, `StartStream/StopStream`, `Read` tách frontend và transport; `boardkit` tuần tự Tune/Status, bulk read chạy song song. Đây là mẫu userspace, **không** là yêu cầu Android phải luôn init Montage.
- [demod/cxd2878/cxd2878.go](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/internal/coaxd/demod/cxd2878/cxd2878.go): `Initialize` đọc chip ID, `usesASCOT3` chọn CXD6802, còn lại Freia; `Wakeup` tách system/PLP/bandwidth khỏi `Tune(frequencyKHz)`. Không dùng comment “embedded tuner” để kết luận chip thực của thiết bị người dùng.
- [pkg/usbio/usbio.go](https://github.com/Till0196/coax/blob/11228b05fb1626bf41c4e1879ace10cd0a8d575c/pkg/usbio/usbio.go): `Transport.Control`, `BulkReader.Start/Read/Stop`, `ReaderOf`; ring 16 × 64 KiB, giữ carry/pending khi caller buffer nhỏ, fallback bulk đồng bộ. Backend userspace riêng theo OS, **không phải libusb-only**. Android nên dùng UsbDeviceConnection fd/native có sẵn thay vì import whole Go runtime hoặc đòi `/dev/dvb`.

### Đề xuất kiến trúc và cổng kiểm chứng

**Đề xuất, chưa triển khai:** một TBS5530 device trong registry Android → bridge transport vendor EP0 + khóa I2C bao cả write/read → Sony CXD2878 frontend và tuner theo chip ID → chọn TS terrestrial → bulk `0x82` → demux/service hiện hữu. Tách firmware provider khỏi protocol để kiểm tra giấy phép và inject test double; không đóng gói firmware lúc này.

Thứ tự: baseline build riêng → mocked control/I2C/short transfers/firmware chunks → frontend state/PLP/bandwidth/unit Hz-kHz → TS route/cancel/reopen/continuity bằng fixtures → kế hoạch phần cứng được duyệt → Android USB Host vật lý → firmware → I2C/chip ID → DVB-T2 lock → TS continuity. Chỉ báo DVB-T2 hỗ trợ sau thực nghiệm; DVB-C và S/S2/S2X/ATSC3 chưa có cam kết.

**Giấy phép:** official bridge có thông báo GPLv2; community `.c/.h` ghi GPL-2.0-only; Go bridge/board cũng GPL-2.0-only, các file khác có thể khác theo `NOTICE`/SPDX. Phải giữ thông báo từng file và kiểm tra kết hợp toàn ứng dụng; không coi mọi code trong coax là Apache chỉ vì có LICENSE-APACHE. Firmware source, hash, quyền phân phối/APK chưa kiểm chứng; không tải/đóng gói blob.

**Hardware:** phiên này chỉ đọc `usbipd list`; TBS đang Shared, không Attached. Không dùng `lsusb`/PyUSB claim lại. Endpoint 0x81/0x82, 512 bytes/interface claim là lịch sử người dùng; mọi hành vi firmware, I2C, chip ID, lock, TS thật **NOT RUN**.

## 2026-11-02 — Build audit và kế hoạch hiện đại hóa (chưa thực hiện)

**Bằng chứng:** source Android HEAD `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`; đọc local bằng file tools, nghiên cứu nguồn web chính thức qua web_fetch/web_search. Không chạy Gradle task, wrapper --version, migration hoặc dependency resolution. Các phiên bản cài thực tế và lỗi environment nằm ở [ENVIRONMENT.md](ENVIRONMENT.md).

### Baseline thực tế (khác ghi chép lịch sử)

| Thành phần / vị trí | Kết quả |
|---|---|
| `build.gradle`, plugin application/library | AGP **8.5.2** |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle **8.7**; chưa có distributionSha256Sum |
| `app`, `drivers`, `dvbservice`, `usbxfer` build.gradle | **compileSdk 34, targetSdk 36, minSdk 21**, source/target Java **1.8** |
| `settings.gradle` | 4 modules; Google/Maven Central/Plugin Portal; không JCenter trong cấu hình này |
| `gradle.properties` | Xmx 2048m; AndroidX/non-transitive R; BuildConfig global và resource IDs final |
| `.idea/gradle.xml` | `#GRADLE_LOCAL_JAVA_HOME`: IDE có thể chọn JVM khác PowerShell |
| `usbxfer/build.gradle`, CMakeLists.txt | Có native build; không pin NDK/CMake/ABI; minimum CMake 3.3.1, shared `UsbXfer`, log/OpenSLES; Release cố ý `-O1` do ghi chú deadlock Samsung |
| Ba library consumerProguardFiles | Khai báo `consumer-rules.pro` nhưng file thiếu: vấn đề cần đánh giá trước AGP 9, chưa sửa trong Phase 0 |
| Dependency chung | AppCompat 1.5.1, Material 1.7.0, JUnit 4.13.2, AndroidX test-ext 1.1.3, Espresso 3.4.0 |
| Test mocks | Mockito/mockito-all 1.10.19, PowerMock 1.6.5; reflection trên JDK mới là rủi ro, chưa tái hiện lỗi |
| Test inventory | 31 phương thức @Test local/10 lớp, gồm một tiện ích chuyển chuỗi; 1 instrumentation test `DeviceFilterXmlEquivalenceTester` so registry với XML; không thấy test source app |

**Hai blocker riêng biệt:** `JAVA_HOME` không tồn tại dù PATH java chạy được; và Gradle 8.7 không hỗ trợ JDK 25. SDK 36/platform và NDK mặc định AGP 8.5 chưa được cài theo inventory hiện tại; không giả định NDK 28 sẽ tự được chọn vì đã có trên đĩa. compile 34/target 36 cần đánh giá ở baseline, không tuyên bố chắc chắn build FAIL chỉ từ cấu hình và không hạ target để né kiểm chứng.

### Nguồn chính thức truy xuất ngày 2026-11-02

- [Gradle releases](https://gradle.org/releases/): bản đầu danh sách **9.8.2**, ngày **2026-10-19**; nhánh 8.x có 8.14.6. Kết quả tìm kiếm tóm tắt nói 9.6.0 không khớp trang trực tiếp; ưu tiên trang trực tiếp.
- [AGP API reference](https://developer.android.com/reference/tools/gradle-api), trang ghi cập nhật 2026-10-28: bản không hậu tố cao nhất **9.4.1**. [Landing AGP](https://developer.android.com/build/releases/gradle-plugin) redirect 9.5 nhưng [nội dung 9.5](https://developer.android.com/build/releases/agp-9-5-0-release-notes) chỉ xác nhận tới **9.5.0-rc02**. Không gọi 9.5 stable; nguồn chưa hoàn toàn đồng bộ, cần kiểm lại trước migration.
- [Gradle current compatibility](https://docs.gradle.org/current/userguide/compatibility.html): JDK 25 chạy Gradle từ **9.1.0**. [Gradle 8.7 compatibility](https://docs.gradle.org/8.7/userguide/compatibility.html): JVM chạy 8–21; cộng yêu cầu AGP 8.5 thì chọn JDK **17** để lấy baseline. Java source/target 1.8 độc lập với JVM chạy build: không tạo bytecode Java 25 cho Android.

| AGP (release notes chính thức) | Gradle minimum/default | JDK minimum/default | API tối đa | NDK default |
|---|---|---|---|---|
| [8.5.x](https://developer.android.com/build/releases/agp-8-5-0-release-notes) | 8.7 | 17 | 34 | 26.1.10909125 |
| [8.10.x](https://developer.android.com/build/releases/agp-8-10-0-release-notes) | 8.11.1 | 17 | 36 | 27.0.12077973 |
| [8.13.x](https://developer.android.com/build/releases/agp-8-13-0-release-notes) | 8.13 | 17 | 36.1 | 27.0.12077973 |
| [9.0.x](https://developer.android.com/build/releases/agp-9-0-0-release-notes) | 9.1.0 | 17 | 36.1 | 28.2.13676358 |
| [9.4.x](https://developer.android.com/build/releases/agp-9-4-0-release-notes) | 9.6.0 | 17 | 37 | 28.2.13676358 |

JDK minimum 17 **không** có nghĩa mọi JDK lớn hơn đều tương thích. Cặp AGP/Gradle mới nhất được ghi để tham khảo, không phải quyết định tự nâng. Nguồn thay đổi theo thời gian; API 37.0 package local không tự chứng minh AGP cũ hỗ trợ API 37.

### Lộ trình đề xuất, mỗi mốc cần phê duyệt và kiểm chứng

1. Chốt baseline nguyên trạng trước: nhánh mới chỉ khi được duyệt; chọn JDK 17 có thật, SDK `D:\Android\Sdk` trong **process riêng**, không sửa global. Kiểm tra cache wrapper/SDK trước mọi lệnh: `--offline` không chặn wrapper tự tải distribution hay AGP tự cài SDK. Thiếu JDK/package thì báo BLOCKED và xin phạm vi cài, không tự cài.
2. Sau baseline: cặp AGP 8.10.1/Gradle 8.11.1 + compileSdk 36, giữ target 36/min 21/Java 8. Platform 36 hiện thiếu: xin duyệt package tối thiểu, không chọn API 37 chỉ vì có sẵn.
3. Native riêng: pin NDK r28c `28.2.13676358` và CMake `3.22.1` đã xác minh; giữ `-O1`, protocol và ABI hiện hữu. Kiểm tra arm64-v8a, armeabi-v7a, x86/x86_64 theo output thực; không tự bỏ ABI.
4. Chuẩn bị AGP 9 trên 8.x: keep-files thiếu, BuildConfig module DSL thay flag global khi cần, resource-ID flags, library targetSdk DSL. Chỉ thay mock dependencies khi có failure tái hiện; không gộp nâng UI dependencies với toolchain.
5. Mốc 8.13.2/Gradle 8.13 → 9.0.1/9.1.0, giữ JDK 17 để tách biến. Sau khi xanh mới thử JDK 25 trong bước riêng; tiếp đó cân nhắc 9.4.1/9.6.0 và Gradle 9.8.2 riêng. Không bắt buộc nâng mọi mốc nếu không giải quyết nhu cầu đã xác định.

**Breaking changes/rủi ro:** [AGP 9 notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) đổi DSL/variant API, built-in Kotlin, missing keep-files, tested build types, R và compileSdk library; [Gradle 9 migration](https://docs.gradle.org/current/userguide/upgrading_major_version_9.html) đổi Groovy/test discovery. Project hiện Java nhưng vẫn phải kiểm tra plugins. Target 36 đã có: [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16) đòi UI/back/edge-to-edge regression; service mediaPlayback manifest chưa chứng minh lifecycle đúng.

**Native 16 KiB:** [Android page sizes](https://developer.android.com/guide/practices/page-sizes): AGP ≥8.5.1 xử lý ZIP alignment `.so` không nén; NDK r28+ mặc định ELF alignment 16 KiB. Baseline 8.5.2 vượt ngưỡng AGP nhưng **chưa có APK kiểm chứng**. Source không có giả định PAGE_SIZE rõ ràng không đủ chứng minh runtime PASS. Trang nhận được ghi hạn Play 2027-02-01 cho target ≥35/64-bit; vì chính sách có thể đổi, không dùng mốc lịch này thay tiêu chí kiểm thử. [ABI guide](https://developer.android.com/ndk/guides/abis), [JDK guide](https://developer.android.com/build/jdks), [NDK/CMake selection](https://developer.android.com/studio/projects/install-ndk).

### Tiêu chí baseline và lệnh tương lai — tất cả NOT RUN

- Chạy wrapper ở JDK hợp lệ đã duyệt; build original checkout không sửa driver; lưu warning/error, versions và dependencies đã resolve. Không chạy `clean` để xóa output ngoài nhu cầu.
- Thứ tự kiểm chứng nhỏ nhất: wrapper/configuration → unit tests ba library → debug assemble/lint → native ABI/load → instrumentation registry/XML → release/bundle và kiểm tra alignment. Bộ unit tests xanh, APK tồn tại/kiểm được nội dung, native `.so` load được trên target được chọn, không regressing driver cũ mới đủ baseline; UI/emulator không chứng minh tuner thật.

```powershell
.\gradlew.bat --version
.\gradlew.bat help --warning-mode all --console plain --no-daemon
.\gradlew.bat :usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest --stacktrace --console plain
.\gradlew.bat :app:assembleDebug :app:lintDebug --stacktrace --console plain
.\gradlew.bat :app:assembleRelease :app:bundleRelease --stacktrace --console plain
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath --console plain
.\gradlew.bat :dvbservice:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=info.martinmarinov.dvbservice.DeviceFilterXmlEquivalenceTester"
```

Instrumentation chỉ khi có serial Android được chọn rõ ràng và được phép chạy. Kiểm ZIP bằng SDK `build-tools\36.0.0\zipalign.exe -v -c -P 16 4 <apk>`; giải nén APK trong thư mục kiểm chứng riêng, dùng NDK `llvm-objdump.exe -p` từng `.so`, yêu cầu LOAD alignment ≥2**14; runtime 16 KiB kiểm `adb -s <serial> shell getconf PAGE_SIZE` và native-load smoke. Đường dẫn đúng ADB là `D:\Android\Sdk\platform-tools\adb.exe`. Không chạy các lệnh tương lai ở Phase 0.

**Rollback:** lưu baseline và mỗi mốc thành commit riêng chỉ sau khi được cho phép; khi lỗi, dừng ở mốc trước, dùng `git revert <migration-commit>` theo phạm vi được duyệt, hoàn nguyên JVM process, chạy lại cùng tests. Không reset --hard/clean, không xóa SDK hoặc untracked tài liệu có sẵn. Build-system modernization phải xong trước mã TBS5530.

## 2026-11-02 — Kiến trúc AndroidDvbDriver và extension points đã đọc trực tiếp

**Nguồn:** toàn bộ vị trí dưới đây thuộc `signalwareltd/AndroidDvbDriver`, SHA **`e4c13a5b86ad5ef9be887df58226046dcb42dcd5`**, Windows PowerShell/editor tools. [Cây source khóa SHA](https://github.com/signalwareltd/AndroidDvbDriver/tree/e4c13a5b86ad5ef9be887df58226046dcb42dcd5). GitNexus không có index; semantic_search không có kết quả, chuyển glob/grep/view đọc source. Agent explore được giao khảo sát thất bại `400 The requested model is not supported`, không có report; parent tự hoàn tất trace, không tái giao cùng mục tiêu. Không sửa/chạy code.

### Bốn module và luồng thực thi

| Module | File/hàm và vai trò |
|---|---|
| `app` | `src/main/java/info/martinmarinov/dvbdriver/DeviceController.java`, `run/tuneTo`: giao diện chẩn đoán chọn device đầu tiên, open → lấy stream → tune/status, `DataHandler` xử lý dữ liệu/đo; không phải tầng protocol USB riêng |
| `drivers` | `.../drivers/usb/DvbUsbDeviceRegistry.java`, `AVAILABLE_DRIVERS/getUsbDvbDevices/getDvbUsbDeviceFor`: enumerate `UsbManager.getDeviceList`, so DeviceFilter, gọi Creator. Hiện chỉ RTL28xx/CxUsb/AF9035, chưa có TBS5530 |
| `drivers` | `.../drivers/DvbDevice.java`, `tune/getTransportStream/DataPump.run`: tune reset demux; một data pump đọc ByteSource, đưa vào DvbDemux, trả InputStream; interrupt/close dừng worker |
| `usbxfer` | `.../usbxfer/UsbBulkSource.java`, `open/readNext/close`: claim interface, set alternate, queue native bulk, consume vào ByteSink, backoff khi chưa có dữ liệu, stop/release khi đóng |
| `dvbservice` | `.../dvbservice/UsbDelegate.java`, `onCreate`: chuyển USB attach intent; `DeviceChooserActivity` chọn device. `DvbService.onStartCommand`: worker mở device, bind loopback, foreground mediaPlayback, serve và close |
| `dvbservice` | `DvbServer.bind/open/serve`: **hai TCP socket riêng** control/TS, cổng cấp động. `Request.parseAndExecute` điều khiển tune/status/PIDs/capabilities; `TransferThread.run` đọc InputStream, gửi TS qua socket, buffer 5×188 bytes |

Không cần driver kernel DVB hoặc root chỉ để tái sử dụng API này: `UsbPermissionObtainer.obtainFdFor` dùng `UsbManager.hasPermission/requestPermission/openDevice`, trả Future<UsbDeviceConnection>. File `.../drivers/tools/UsbPermissionObtainer.java` có nhánh PendingIntent/receiver theo Android API; phải regression permission denied/detach/reopen trên target mới, không suy ra đúng từ manifest. Android USB Host chính thức đã đọc qua `android docs search/fetch`, nguồn [USB Host](https://developer.android.com/develop/connectivity/usb/host): chọn interface/endpoint và xin quyền trước truyền. Firmware write chưa được phép chỉ vì mở permission thành công.

### Hợp đồng driver có thể tái sử dụng

- `.../drivers/usb/DvbUsbDevice.java:60`, interface **Creator**: `create`, `getSupportedDevices`. Thêm Creator TBS trong **AVAILABLE_DRIVERS** (`DvbUsbDeviceRegistry.java:41`), DeviceFilter VID:PID 734c:5530 và đồng bộ `dvbservice/src/main/res/xml/device_filter.xml`; instrumentation `DeviceFilterXmlEquivalenceTester` bảo vệ tương đương registry/XML. Tìm trong Java/XML hiện không có TBS/VID/PID này.
- `DvbUsbDevice.open` (`:102`, final): obtain connection → `getUsbInterface()` → retry tối đa 4 vòng `powerControl(true)`, `readConfig`, `frontendAttatch/attach`, `getCapabilities`, `tunerAttatch/attatch`, `frontend.init(tuner)`, `init`. Tên `Attatch/attatch` là API hiện có, không đổi spelling ngoài phạm vi.
- Hooks (`DvbUsbDevice.java:249` trở đi): powerControl, readConfig, frontendAttatch, tunerAttatch, init, getUsbInterface, getUsbEndpoint. **Đề xuất TBS extends DvbUsbDevice trực tiếp**: firmware/bridge readiness ở readConfig có state/error rõ; frontend/tuner Sony tách theo contracts. Không nhét vendor A0 vào generic bulk wrapper.
- `DvbUsbDevice.createTsSource` (`:243`) trả UsbBulkSource, có thể override/wrap để route TBS trước stream và quản lý stop; base không có hook `streaming_ctrl` riêng kiểu Linux. `getNumRequests=40`, `getNumPacketsPerRequest=10` là mặc định có thể override. `Af9035DvbDevice.java:967/972` đã override 87 packets ×10 requests. Không copy buffer size từ Linux mà không đo throughput/loss/cancel trên Android.
- `.../drivers/tools/I2cAdapter.java:60`, `transfer` khóa **toàn bộ message array**, retry tối đa 10; implement `masterXfer` map đúng một write hoặc write-pointer/read TBS. `I2GateControl.runInOpenGate` có finally đóng gate, dùng cho tuner repeater. `send/recv` hiện gắn I2C_M_TEN; không dùng máy móc cho TBS 7-bit, validate flags/address/length và throw lỗi thật. Lock USB riêng phải tránh để giao dịch khác chen vào cặp 90/91 khi ghi bank/selector/firmware.
- `DvbFrontend.java:32`: `attach/init/release/setParams/readSnr/readRfStrengthPercentage/readBer/getStatus/setPids/disablePidFilter`; `DvbTuner.java:26`: `attatch/init/setParams/getIfFrequency/...`. Có thể cài frontend CXD2878 và tuner Ascot3/Freia theo chip-ID; không mở rộng mọi frontend cũ chỉ để thêm board.

### USB control và native bulk: phân biệt quan trọng

- `.../usb/rtl28xx/Rtl28xxDvbDevice.java:80`, `controlTransfer/ctrlMsg` là **mẫu cách gọi Android controlTransfer/locking**, không phải protocol TBS: RTL request=0, ngữ nghĩa value/index riêng, TS endpoint 0x81. Helper có nhánh dùng buffer.length thay length; **không tái sử dụng nguyên xi** cho firmware chunk/offset TBS; tạo transport TBS kiểm request/value/index/length/short transfer cụ thể trong phase được duyệt, không sửa RTL ở audit.
- `.../usb/generic/AbstractGenericDvbUsbDevice.java:22`, `dvb_usb_generic_rw` là **bulk control IN/OUT**, không phải EP0 controlTransfer dù tên có “control”. `CxUsbDvbDevice` (`:97` constructor) đòi endpoints 0x81 IN, 0x01 OUT, 0x82 TS theo vị trí cố định. TBS lịch sử chỉ thấy IN 0x81/0x82, official dùng EP0: **không kế thừa CxUsb transport** hoặc giả định có OUT 0x01.
- TBS `getUsbEndpoint` phải tìm theo **address 0x82 + BULK + IN** ở interface 0/alternate đã xác nhận, không lấy endpoint(0) hay “IN đầu tiên”. Không đổi TS sang 0x81 chỉ vì có descriptor.
- `UsbHiSpeedBulk.java:44` load `libUsbXfer`; constructor lấy **UsbDeviceConnection.getFileDescriptor**, packetSize từ endpoint. Platform support false nếu load native thất bại; DvbUsbDevice constructor từ chối unsupported platform, nên native ABI cần test trước board.
- `UsbBulkSource.open`: setInterface → claimInterface(force=true) → start; `UsbHiSpeedBulk.start/read/stop` trên một thread, queue/resubmit/cancel. Dữ liệu Buffer tái sử dụng, phải consume/copy trước read kế tiếp. Base queue mặc định 40×(10×maxPacketSize); nếu maxPacket 512 như lịch sử thì mỗi request 5120 bytes, không phải Linux 4096.
- `usbxfer/src/main/cpp/isorequest.c:20`, JNI allocate/reset/submit/cancel/read/reap dùng `USBDEVFS_URB_TYPE_BULK`, SUBMITURB/DISCARDURB/REAPURB(NDELAY), dù Java class tên IsoRequest. `usbhispdbulk.c:10` dùng USBDEVFS_SETINTERFACE. Không cần port URB kernel driver vào app, nhưng phải test ioctl trên fd Android được cấp quyền, ABI pointer/lifecycle, cancel/reap/disconnect và return/error propagation.
- `UsbHiSpeedBulk.unsetInterface` đặt alternate 0 với comment “stops streaming”; **không được coi alternate 0 chặn được luồng TBS**. Official selector không chặn luồng; phải ngừng queue/cancel đúng lifecycle và quyết định routing trước đọc. `IsoRequest` hiện free native từ finalize; cần theo dõi lifetime/reopen/stress, không refactor/fix trong audit.

### Firmware, Sony frontend và MPEG-TS

- Firmware pattern `Af9035DvbDevice.readFirmware/readConfig/download_firmware` (`:368/:389`), `Si2168.readFirmware/init`, `Mn8847X.loadFirmware`: đọc raw resources, warm/cold và kiểm lỗi. Đây là mẫu orchestration/Resources/exception, **không** là FX2 loader. Chưa có abstraction firmware provider chung hoặc TBS FX2 trong phạm vi source đã khảo sát.
- Driver **mẫu frontend gần nhất là Cxd2841er** (`drivers/.../usb/rtl28xx/Cxd2841er.java:56`): Sony bank/register, SHUTDOWN/SLEEP/ACTIVE, DVB-T2 bandwidth/status/TS clock. Nhưng constructor/field phụ thuộc **Rtl28xxI2cAdapter**, chip list chỉ CXD2837/2841/2843/2854; **không phải CXD2878 và không thể chỉ thêm chip ID**. Dùng làm mẫu style/state/contracts, đối chiếu register với official CXD2878; không refactor toàn họ Sony trong Phase 0.
- `CxUsb/MygicaT230C` là mẫu ghép DVB-T2 frontend+tuner (`frontendAttatch`: Si2168, `tunerAttatch`: Si2157; 0x64/0x60), **không** tương đương silicon/protocol TBS. `Af9035` là mẫu firmware và queue tuning, `Rtl28xx` mẫu EP0; không có một driver duy nhất khớp toàn board.
- `DvbDemux.java:50`, `DvbDmxSwfilter/consume/swfilterPacket`: đồng bộ lại theo 0x47, gom packet chưa đủ qua nhiều lần read, software PID filtering, NativePipe, đầu ra 188 bytes. `getDroppedUsbFps`/continuity/TEI counters hỗ trợ đo nhưng không thay capture thật; phải cấu hình lựa chọn filter/full stream để tránh hiểu nhầm zero bytes là firmware hỏng.
- Luồng TS: native 0x82 → UsbBulkSource.readNext → DvbDevice.DataPump → DvbDemux/NativePipe/InputStream → app DataHandler **hoặc** service TransferThread/TCP. Không phải MPEG decoder và không cần ATSC3 ALP parser cho DVB-T2.

### Giới hạn API và rủi ro cần kiểm chứng ở phase sau

1. `DeliverySystem.java:23`: chỉ **DVBT,DVBT2,DVBC**, ordinal là giao thức TCP; Request.REQ_TUNE giải ordinal, REQ_GET_CAPABILITIES mã hóa bitset. DVB-S/S2/S2X cần mở rộng protocol tương thích về sau, không thể chỉ cài Montage driver rồi quảng cáo hỗ trợ.
2. `DvbFrontend.setParams`, `DvbDevice.tune`, `Request.REQ_TUNE` hiện chỉ frequency/bandwidth/system, **không có tham số PLP/stream_id** (PLP là luồng con trong một kênh DVB-T2). Phải quyết định auto/default PLP hay API mở rộng sau, không tự hứa chọn mọi PLP. SNR/BER/RF trả integer theo app hiện hữu, cần quy đổi/định nghĩa rõ, không gán trực tiếp dBm/CNR từ Go vào phần trăm.
3. Base open retry 4, I2C retry 10, tune retry 4 có thể lặp firmware/reset/giao dịch mà việc lặp lại làm đổi kết quả. Thiết kế TBS state/recovery trước khi ghép hooks, test lỗi giữa upload và disconnect/re-enumeration; không dùng retry để che lỗi.
4. Firmware provenance/hash/rights vẫn NOT RUN; mọi tác vụ ghi phần cứng phải có kế hoạch được duyệt. Chip/timing/selector và Android ioctl/USB permission chưa xác minh trên thiết bị thật.
5. Tests hiện hữu chủ yếu parser/protocol/helper, không có TBS fixture hoặc test native USB phần cứng. Local test files gồm DeviceFilterMatcher, DvbMath, BitReverse, FastIntFilter, RegMap, Request, TsDumpFileUtils, StackTraceSerializer, AlternateUsbInterface; `ConvertCStringToJavaArrayTool` là utility có @Test. Instrumentation registry/XML phải cập nhật đồng bộ khi thêm Creator.

**Kết luận:** AndroidDvbDriver có sẵn đường permission→fd→bulk→TS/service để tái sử dụng, nhưng phần cần port thật là TBS EP0/FX2/I2C/CXD2878+tuner cùng state/error/TS selector, không phải chỉ thêm VID:PID. Mọi thiết kế trên là đề xuất nguồn-backed, chưa thay mã hoặc test hardware.
