# Port TBS5530 sang Android — trạng thái hiện tại

## 2026-11-13, B5 chốt — OnePlus K25 hoàn tất bốn pha/drain; TS còn lỗi, chưa xác nhận nhiễu USB

**Đã chạy đủ OFF1/ON1/OFF2/ON2 trên OnePlus; `TS_SMOKE_PASS=false`.** Ordinal 4 được OK B5 riêng sau kế thừa ba refusal trước grant. Bootstrap/reload đều 132/132, không detach sau reload/`-75`/`-71`; ON1 nhận 34340864 byte, ON2 11141120 byte, data errors 0/writer drop 0/outstanding 0/quarantine false, DRAIN_CONFIRMED. Đây là kết quả debug đúng một lượt K25, chưa production hoặc Phase 4.

SNR trung vị OFF1/ON1/OFF2/ON2 = **25,475 / 18,460 / 22,087 / 20,332 dB**, RSSI hai OFF −64,85 dBm, BER OFF 0 và ON tăng. Chênh OFF−ON là **7,015 rồi 1,755 dB**: ngưỡng ≥6 dB không lặp ở cặp hai, chỉ gợi ý liên hệ, chưa ROOT_CAUSE_CONFIRMED. Sau 5 giây, ON1 CC/TEI 5467,85/31,20 mỗi phút, ON2 5233,57/0; có VTV/PAT/PMT/SDT ở ON1 nhưng ON2 thiếu SDT và PCR chưa ổn định. FULL% UNKNOWN vì chưa baseline K25 độc lập. Xem [RESEARCH.md](RESEARCH.md), [ADR-062](DECISIONS.md).

Helper live `5bdc3334…159bd2b` lỗi export timeout sau drain; budget đã đóng HOST_ABORTED_NO_RETRY. Đã thu lại **40 tệp/95374481 byte chỉ đọc**, SHA/size trước–sau và host khớp, không receive/firmware mới. Tuner đã rút sau drain, không helper/capture/emulator do phiên còn chạy. Candidate helper hậu kiểm `a3480759…ec24660` chỉ sửa deadline binary export sau drain, 23 focused + 90 regression PASS, **NOT USED ON HARDWARE**; APK/native/firmware không đổi.

Đã dùng bốn Android gồm ba preparation refusal/một nhận thật, một Windows capture K25. K36 Android/tách xa hơn 70 cm/phụ kiện NOT RUN; không tự mở ordinal 5/6 hoặc Windows còn lại. Raw/evidence/báo cáo/SVG SNR ở bundle `phase31-b5-20261113-124425-302b54cb` ngoài Git. Bước tiếp chỉ đề xuất phân tích offline và phép lặp hữu hạn mới nếu cần. Giữ các FAIL lịch sử chưa chạy lại; GitNexus index cũ, kết luận dựa source/diff/tests/evidence. Không stage/commit/push.

## 2026-11-13, ordinal 3 qua quyền USB nhưng host từ chối foreground; đang kiểm đúng một K25 bổ sung đã duyệt thiết kế

**OnePlus vẫn chưa nạp firmware/reload hoặc nhận TS B5.** Ordinal 3 đã có quyền OS GRANTED và challenge live_ready=true, nhưng host dừng B5_FOREGROUND_UNVERIFIED trước grant. History 581 byte/hash nguyên. Parser cũ từ chối hai dòng resumed activity trùng hoàn toàn; candidate đã tái hiện FAIL trước/PASS sau trên snapshot OnePlus thực và 13 tests, chưa là quyền live.

Cả ba ordinal đều PRE_GRANT_HOST_REFUSAL và đã được giữ/review, không xóa hoặc replay. Người dùng xác nhận tuner đã rút và duyệt thiết kế riêng [ADR-061](DECISIONS.md): đúng một K25 bổ sung, tổng bốn trong trần sáu, kế thừa sổ cũ/cùng budget/session và cần card/OK mới. Không tự chạy ordinal 5/6. Đang kiểm helper extension và actual card guard; APK/native/firmware không đổi. Windows K25 có lỗi CC/TEI, K36 Android/tách xa vẫn NOT RUN. Không stage/commit/push.

## 2026-11-13, Android đã dùng hai lượt chuẩn bị; lượt K25 cuối đang chờ chuyển target ADB có kiểm chứng

**Chưa có firmware/reload hoặc TS B5 trên OnePlus.** Đã cài đúng candidate một install-r, history 581 byte/hash trước-sau nguyên. Hai lượt đều dừng trước grant: lượt 1 timeout khi tuner còn rút; lượt 2 CANCELLED trong lúc xử lý hộp thoại mở ứng dụng. Đã lưu receipt và review PRE_IO_REFUSED, không xóa hoặc replay lượt. APK/JNI/emulator đã kiểm bên dưới giữ nguyên phạm vi.

Người dùng xác nhận chỉ tiếp tục ordinal 3 K25 cuối, không tăng vô hạn hoặc vượt trần sáu Android. Đã xử lý attach/Open app trước helper, nhưng người dùng lỡ ngắt/nối lại ADB làm alias đã pin mất. Lượt 3 **chưa consume**. Alias mới được kiểm chỉ đọc, cùng model/API/boot/history; không thay vào card ngầm định. Người dùng đã duyệt thiết kế [ADR-060](DECISIONS.md): chuyển target có pin exact sổ cũ/hai lượt đã dùng, chỉ K25 cuối, cần kiểm helper và card/OK mới trước live.

Windows K25 vẫn có mẫu 159999844 byte với CC/TEI; kết quả và chốt receipt giữ nguyên. K36 Android và phép tách xa chưa chạy. Không ROOT_CAUSE_CONFIRMED chất lượng TS hoặc TS_SMOKE_PASS, không stage/commit/push.

## 2026-11-13, Windows K25 đã thu — Chất lượng còn lỗi; tiếp nối Android đang kiểm guard

**Windows K25 có TS và dịch vụ VTV nhưng chưa sạch; Android B5 vẫn NOT RUN.** Helper v2 đã sửa kiểu số JSON, kiểm 150/150 và được duyệt card v2 riêng. Capture có TS 159999844 byte/pcap 163272178 byte, TSDuck exit 0/wall 39,721323 giây. Vì chưa đủ wall 40 giây, receipt đóng CAPTURE_INCOMPLETE; kích thước khớp byte cap là suy luận có căn cứ, không bằng chứng lỗi USB transport.

Phân tích: CC 837/TEI 24036, PAT/PMT/SDT và 13 dịch vụ VTV; PCR ước lượng khoảng 35,58 Mbps nhưng chưa ổn định PASS. SNR mọi raw 19,696/20,347/21,559 dB, RSSI theo AGC −64,85 dBm; L1 64-QAM/5/6/64K khác K36. FULL% UNKNOWN vì chưa có baseline K25 độc lập. Không có phép OFF/ON OnePlus để kết luận nhiễu. Các process capture đã đóng và người dùng đã rút/ngắt nguồn.

Người dùng đã chọn thiết kế tiếp nối chỉ Android, giữ nguyên receipt/card/ledger cũ và kế thừa một Windows đã dùng, không thu lại K25. [ADR-059](DECISIONS.md) yêu cầu carry exact hash/length/status và chứng cứ cleanup, cùng budget/session; đang kiểm helper offline rồi mới trình card/OK mới. Một Windows còn lại chỉ là trần số học, chưa quyền chạy. Không reset/rearm hoặc UUID mới để vượt chốt. App/JNI/emulator 38/38 đã kiểm giữ nguyên; không stage/commit/push.

## 2026-11-13, sau OK B5 — Windows bị chặn trước ARMED do kiểu số JSON; tuner vẫn rút

**Chưa có capture hoặc phép thử tuner B5.** Người dùng đã duyệt card `0c9de165…acae2d`, xác nhận cold và UAC. Helper Windows PID 25592 thoát 1 trước ready/result/budget directory; chưa yêu cầu cắm tuner, không retry. Đối chiếu offline xác định `ConvertFrom-Json` trả Int64 còn guard chỉ nhận Int32, ném `B5_WINDOWS_CARD_SCOPE_INVALID` trước tạo budget. Đây là reproduction có nguồn, không phải stderr của process thật vốn chưa thu được.

Đang sửa helper hẹp để nhận Int32/Int64 đúng giá trị, vẫn chặn chuỗi/bool/số lẻ; giữ nguyên frozen helper/card cũ. Người dùng chọn tiếp tục sau kiểm bản sửa và card mới, **không kế thừa OK cũ cho hash mới**. Các gate app/JNI/emulator bên dưới vẫn đúng phạm vi; chưa TS_SMOKE_PASS hoặc kết luận nhiễu USB. Không stage/commit/push. Xem [WORKLOG.md](WORKLOG.md).

## 2026-11-13, B5 đã kiểm offline và khóa card — Chờ OK B5, chưa chạy tuner

**Phần mềm B5 đã qua các gate liên quan; phần cứng NOT RUN.** Đã sửa tiêu chí demod lock/SNR, thêm BER/L1/RSSI Windows `88=00 → 87=C0`, K25 MANUAL0 với một AUTO fallback, OnePlus-only B5 và bốn pha OFF1/ON1/OFF2/ON2. Bootstrap/reload/drive/stream-select giữ nguyên. SNR raw vẫn lưu khi monitor lỗi nhưng số đo qualified không được công bố nếu cleanup thất bại.

Gate cuối: JVM app 112 PASS, driver 528 PASS + 5 SKIP, usbxfer 62, service 13; native host 87; Python 93; helper Android 90/Windows 33; debug/release/AndroidTest/app lint, 12 ELF/bốn ABI/16 KiB tĩnh/release isolation PASS. Emulator đúng APK candidate **38/38 PASS**, gồm năm lifecycle đã xem lại sau fixture model và CLEAR_TOP; giữ log FAIL trước sửa, driver lint/fake heartbeat lịch sử chưa chạy lại. Emulator đã đóng, process exit 0/cổng 5580–5581 không listener.

Card SHA `0c9de1656690f8a9b736401319e26b0837461715eb6b6f2bde87ad0648acae2d`, APK `90c7c616ba34dc8b891051203aa2d00805cd8195d987ccb2c3cd5fc0da6bf7e4`, manifest 502 source đã đối soát; source HEAD vẫn `6adde85`. Bản card thật giữ private vì explicit ADB target, bản lọc và báo cáo trong bundle `phase31-b5-20261113-124425-302b54cb` ngoài Git. Kế hoạch ba lượt Android trong trần sáu, tối đa hai Windows, bố trí gọn khoảng 33 cm/tách xa hơn 70 cm theo người dùng; không phụ kiện thêm.

**Chờ đúng `OK B5` cho card này.** Chưa install/launch candidate trên OnePlus hoặc BDA/capture mới. Trước từng lượt cần xác nhận cold ít nhất năm giây/đèn tắt và owner độc quyền; `-75`/`-71` hoặc detach sau reload đóng cả phiên. B4 vẫn đóng, Phase 4 chưa mở, chưa TS_SMOKE_PASS hoặc ROOT_CAUSE_CONFIRMED chất lượng TS. Không stage/commit/push. Xem [ADR-058](DECISIONS.md), [TEST-MATRIX.md](TEST-MATRIX.md) và [WORKLOG.md](WORKLOG.md).

## 2026-11-13, B5 đang triển khai offline — B4 không mất demod lock; chưa có phép thử OnePlus mới

**Đã kiểm lại dữ liệu B4: watchdog TS lock gây dừng giả nếu diễn giải là mất khóa demod.** Cả 10 mẫu Android đều sync 6/không unlock; chín mẫu `06` rơi giữa các burst. SNR tính lại từ mọi raw là 15,721/17,510/30,109 dB; Windows 14,009/15,706/19,735 dB. Đây là tính lại dữ liệu cũ, không phải phép đo B5 hoặc bằng chứng nguyên nhân nhiễu đã xác nhận. Xem [RESEARCH.md](RESEARCH.md), mục Đính chính.

Baseline đúng HEAD `6adde854e680948ff993bf552543c3e26a266be9`/nhánh `chore/modernize-android-build`, tree sạch. Đang tích hợp demod lock, SNR/BER/L1/RSSI, bốn pha OFF/ON, K25 và OnePlus `GM1900`/API 30. Golden K25 chỉ khác ba byte tần số so với K36; AUTO fallback có golden retune riêng, 41 kiểm frontend JUnit PASS. Chưa hoàn tất gates tích hợp, chưa freeze card/APK hoặc `OK B5`.

**Đã chốt đính chính RSSI với người dùng:** Windows B4 dùng `88=00`, không phải `04` trong đề bài; B5 chọn cleanup `88=00`, `87=C0`. Golden Android sau init/tune đều kết thúc `C0/00`, đã đối chiếu offline trước card. RSSI chỉ trong OFF; chưa chạy chuỗi này trên tuner. Đầu thu Pantesat và TBS dùng cùng cáp anten/không chia đã ghi ở [ENVIRONMENT.md](ENVIRONMENT.md), không hỏi lại.

Giữ ADR-057 lịch sử, tạo [ADR-058](DECISIONS.md) theo lựa chọn người dùng. Phần cứng **NOT RUN**, Phase 4 chưa mở; ngân sách B4 vẫn đóng. Card B5 phải giữ tối đa sáu Android/hai Windows và dừng cả phiên nếu OnePlus còn `-75`/`-71` hoặc detach sau reload sạch. Không stage/commit/push, raw/định danh ngoài Git; toolchain/driver/ownership không đổi.

## 2026-11-13, B4 cặp 1 — Windows đã thu 40 giây; Android dừng đúng mất TS lock sau 2,28 giây

**Chưa đủ bằng chứng parity; không mở Phase 4.** Người dùng duyệt `OK B4` đúng card `742010f6…`; Windows thu 61698968 byte qua TSDuck/BDA, USBPcap 63706295 byte, các process đã đóng. Giữ nguyên anten thụ động ngoài trời trên nóc nhà, dùng cùng cổng cold2. Người dùng ngắt cả nguồn ≥5 giây rồi chuyển sang Samsung/cáp chữ Y/pin dự phòng theo hướng dẫn.

Android lượt 1 bootstrap/reload đều 132/132, không detach sau reload hoặc `-75`; nhận 2146304 byte trong 2278497924 ns, writer drop 0/failure null, min outstanding phần mềm 15/zero count 0. Monitor có 10 mẫu đọc thành công: đầu `26` LOCKED, chín mẫu sau `06` không TS lock; kéo dài 2002360231 ns nên watchdog dừng `B4_LOCK_LOST`, drain/outstanding 0/quarantine false. Có PAT/PMT, nhưng 214 lỗi CC và mẫu ngắn hơn 5 giây; chưa thể so chất lượng ổn định.

Windows sau 5 giây: FULL 46,79% tính cả vị trí MISSING, CC+TEI khoảng 10665,42/phút, SNR hợp lệ min/trung vị/max 12,9/14,35/16,1 dB. Có 14 dịch vụ PAT/PMT, thiếu SDT và PCR chuẩn; không gọi đối chứng sạch hoặc tự quy lỗi anten. Android chưa có cửa sổ sau 5 giây hoặc SNR tương ứng, nên hiện `INSUFFICIENT_EVIDENCE`, không `B4_ANDROID_PARITY_PASS`/`B4_ANDROID_GAP`/`RF_LIMITED`.

Helper lỗi xuất tệp sau drain vì quoting `exec-out sh -c`; receipt ngân sách đã đóng, không reset/rearm hoặc tự chạy thêm. Người dùng đã rút tuner/ngắt nguồn và cho sửa lỗi trong phiên; đang sửa helper **offline**, không thay APK đã chạy. Đã thu 35 tệp app lưu sẵn bằng đường đọc `cat`, SHA/size tablet-host khớp; evidence ở bundle B4, `budget-06ad7993-257b-4e2c-86ab-7aa68f1f112b/attempt-1/readonly-full-export-b8b36864`. Đã sửa export offline và parent kiểm 131/131 fake PASS, B3 regression 126/126 PASS; bản sửa chưa dùng hardware. Hậu kiểm monitor không thấy lỗi mask/timer hoặc bằng chứng đủ để gọi dừng giả; snapshot không chứng minh lock vật lý liên tục, và EP0 mirror chưa thu giao dịch monitor. Bước tiếp: bổ sung telemetry đối chiếu status trong/ngoài freeze trước khi đề xuất card/hash mới nếu cần phần cứng. Giới hạn toàn B4 vẫn tối đa ba Windows/bốn Android, đã dùng 1/1; chưa có quyền vượt chốt ngân sách đã đóng. Không stage/commit/push.

## 2026-11-13, chuẩn bị B4 — Đã tách lỗi `-75` khỏi chất lượng TS; chưa chạy phần cứng mới

Baseline đầu phiên đúng nhánh `chore/modernize-android-build`, HEAD `8d05adb24de221399deaf2392b7fef1218cfab3d`, working tree sạch. Đã kiểm lại năm report B2/B3 sau reload: có dữ liệu, không detach sau reload, drain sạch; [ADR-056](DECISIONS.md) chấp nhận `TRANSPORT_FIXED_DEBUG` và `ROOT_CAUSE_CONFIRMED` riêng cho lỗi USB `-75`. Điều này không chứng nhận TS sạch hoặc parity Windows/Android.

Đã triển khai thu B4 mặc định 30 giây, tối đa 60 giây/256 MiB, writer không chặn/ring 4 MiB và monitor demod mỗi khoảng 250 ms; giữ bootstrap, reload, MANUAL PLP0, drive `54/54`, `8A 0A00`. Starvation chỉ còn outstanding phần mềm về 0, không dùng horizon tốc độ giả. BER/PER/FER live vẫn UNKNOWN; RSSI tuner bị từ chối vì chưa có chuỗi ghi đã duyệt. Gates: 81/81 Python, 87 native host, JVM app 101/driver 507 PASS + 5 SKIP/usbxfer 61/service 13, debug/release/AndroidTest/app lint và JNI/ELF/release isolation PASS có phạm vi; emulator đúng APK cuối 11/11 PASS và đã đóng. Giữ các FAIL lịch sử, không giả toàn bộ đều PASS.

Phân tích mới xác nhận burst khoảng 242,35 ms/540672 byte, tốc độ burst 27,5 Mbps không phải bitrate multiplex. Windows cũng có CC/TEI và SNR sau 5 giây 12,4/13,3/16,9 dB; PCR chuẩn/SDT còn thiếu. Ba B3 quá ngắn và không monitor nên comparison là `INSUFFICIENT_EVIDENCE`, không tự quy RF hoặc chứng nhận parity. Chi tiết/đính chính trong [RESEARCH.md](RESEARCH.md).

**Phần cứng B4 NOT RUN, chờ đúng `OK B4`.** Card SHA `742010f6e556acb1dcade2890457e18486b462cfb6a074ab3dcca8b37d5c9864`, APK debug `9a11db84775743843b3248592e15153e1c1efed90cd58d3938383fa2571ddfb4`, source manifest 408 tệp đã đối soát. Card và evidence ở bundle `phase31-b4-20261112-235953-98a481b8` ngoài Git. Anten người dùng mô tả thụ động, ngoài trời trên nóc nhà, dùng lại cổng cold2; ngày đo 13/11. Tối đa ba Windows/bốn Android, giữ anten trong từng cặp, mỗi lượt Android người dùng ngắt cả nguồn ít nhất 5 giây. Chưa cài APK mới Samsung hoặc capture/tune mới; Phase 4 chưa mở, không stage/commit/push.

## 2026-11-12, chốt B3 sau năm lượt hữu hạn — Có TS sync liên tục, chưa TS sạch; phần cứng đã đóng

**Mục tiêu nhận TS sạch/ổn định chưa đạt.** Hai lượt chuẩn bị đầu không tới I/O tuner; sau quyền tăng ngân sách và card mới được OK B3, ba lượt nhận thật đều bootstrap/reload 132/132, first data status/errno 0, không detach và drain sạch. Người dùng đã rút tuner/ngắt cả nguồn sau lượt cuối. Không còn lượt được chạy trong tổng năm, không tự tiếp tục vô hạn.

| Cấu hình nhận | Byte / Mbps | Data URB status 0 | Sync break sau prefix | CC / TEI | PAT / PMT | Kết quả |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 16×16384 | 1835008 / 7,336 | 112 | 0 | 166 / 0 | 3 / 0 | FAIL chất lượng/rate |
| 32×16384 | 1720320 / 6,879 | 105 | 0 | 140 / 0 | 1 / 14 | FAIL CC/rate; PID-service khớp Windows |
| 16×65536 | 917504 / 3,667 | 14 | 0 | 50 / 66 | 0 / 0 | FAIL; mã hóa/header lỗi cũng xuất hiện |

Vòng native đã bỏ khoảng Java xen syscall: reap→resubmit tối đa 0,136 ms, nhưng có gap 170–406 ms (16 KiB) hoặc 582/905 ms (64 KiB), chưa biết nguồn dừng hay host/worker bị trì hoãn. Counter rủi ro 6/5/2 không đo queue phần cứng. Không có B3_PASS_SINGLE_RUN, ROOT_CAUSE_CONFIRMED hoặc TS_SMOKE_PASS. Đã sửa report offline để không gọi cancellation −2 khi drain là lỗi data; không sửa hồi tố report thật hoặc cài candidate mới Samsung.

Gates cuối: analyzer 42, native host 74 cases, JVM app 99/driver 485 PASS + 5 SKIP/usbxfer 49; debug/release/AndroidTest/app lint, JNI bốn ABI, ELF 16 KiB và release isolation PASS. Candidate report cuối kiểm emulator focused 12/12 PASS, không cài Samsung. Giữ FAIL driver lint cũ, bốn lifecycle emulator và fake heartbeat timeout. Evidence báo cáo cuối ở bundle main `phase31-b3-main-20261112-204012-2baacf7b` ngoài Git; [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md), [ADR-054/055](DECISIONS.md) ghi chi tiết.

**Bước tiếp theo trong Phase 3.1:** thiết kế telemetry RAM cho poll/EAGAIN/scheduling và timestamp phù hợp để phân biệt gap, kiểm offline rồi mới cân nhắc card hardware hữu hạn mới. Không tiếp tục tăng buffer hoặc chuyển production từ ba lượt FAIL. Phase 4 chỉ là kế hoạch đã ghi bên dưới; không stage/commit/push.

## 2026-11-12, lượt tổng 3 B3 — Đã nhận 1,835 MB liên tục sync, vẫn lỗi CC/thiếu PMT

Permission-first đã tránh mất foreground ban đầu: người dùng xử lý hộp thoại mở app trước grant, Activity permission-only xác minh quyền thật. Card mới được OK B3 với tổng tối đa năm lượt (hai refusal trước I/O đã consume, còn ba), không chạy vô hạn hoặc xóa manifest cũ. APK/native/firmware/frontend không đổi; chỉ bước chuẩn bị người dùng khác.

Lượt tổng 3 bootstrap/reload đều 132/132, không detach; native nhận 1835008 byte trong 2,001 giây, first status/errno 0, dừng deadline, starvation risk 6, throughput khoảng 7,34 Mbps. Max/median reap→resubmit 135769/24692 ns; submitted/completed 128 gồm drain. Outstanding 0/quarantined=false và DRAIN_CONFIRMED, người dùng đã rút/ngắt nguồn ≥5 giây. Không `-75` đầu hoặc firmware uncertainty mới quan sát.

Analyzer sau prefix 2048: một đoạn/0 chỗ đứt, 9749 packets, CC 166 lỗi, TEI/mã hóa 0, PAT hợp lệ 3 nhưng PMT 0. TSDuck nhận TS/PAT, chưa đủ service structure. Không TS_SMOKE_PASS hoặc ROOT_CAUSE_CONFIRMED. Đang đối chiếu audit native để chọn queue/buffer trong allowlist; còn tối đa hai lượt trong tổng năm, mỗi lượt cold riêng. Giữ tuner rút tới thông báo tiếp theo; không stage/commit/push.

## 2026-11-12, sau OK B3 — Lượt chuẩn bị 1 dừng trước grant, chưa tuner I/O

Người dùng duyệt card e397f1fd… và xác nhận tuner rút/ngắt cả nguồn ≥5 giây, màn hình mở. Đã install-r đúng APK `1a15959d…` một lần, installed SHA/witnesses trước sau đúng. Helper consume ordinal 1 rồi launch Activity, nhưng guard foreground chỉ nhận trường `mResumedActivity` cũ nên dừng `B3_FOREGROUND_UNVERIFIED`. Samsung API36 thực dùng `ResumedActivity` global và `topResumedActivity` theo display. Không có yêu cầu cắm; người dùng xác nhận tuner vẫn rút/nguồn tắt.

Chưa stage firmware hoặc publish grant; thư mục session đọc lại chỉ có ledger, không grant/firmware/report/bootstrap-consumed. Không bootstrap/reload/URB/raw B3, do đó không kết quả TS hoặc nguyên nhân gốc. Receipt cũ đã đóng phiên theo guard bảo thủ, được giữ nguyên; không reset/xóa để chạy lại. Tổng ngân sách ba lượt đã dùng một lượt chuẩn bị, còn tối đa hai; sửa helper offline/hash card là bước tiếp, không tự dùng duyệt hash cũ cho code mới.

`TS_SMOKE_PASS=false`, root cause chưa xác nhận. Không stage/commit/push hoặc thay firmware/frontend/native đã freeze. Xem [WORKLOG.md](WORKLOG.md) và evidence main. Các kết quả phần mềm bên dưới vẫn đúng phạm vi, không biến lần bị chặn trước grant thành phép thử native.

## 2026-11-12, B3 offline tới 21:56 +07 — Candidate đã build và kiểm; chờ card/OK B3

**Vòng nhận liên tục debug đã triển khai và qua các gate phần mềm chính; Samsung B3 chưa chạy.** Mặc định 16×16384, 4 MiB hoặc 2 giây, watchdog 100 ms/heartbeat thật 2 giây, audit/parser/raw sau drain; pipeline firmware/frontend B2 giữ nguyên và đường cũ vẫn có. [ADR-054/055](DECISIONS.md) ghi ranh giới an toàn, quyền theo phiên và giới hạn bộ đếm starvation phần mềm.

Kiểm cuối: analyzer 42/42; native stream host 74 cases; app JVM 96 PASS, driver 484 PASS/5 SKIP, usbxfer 49 PASS; debug/release/AndroidTest/app lint PASS; JNI bốn ABI/release isolation/ELF 16 KiB hai ABI 64-bit PASS; emulator focused 12/12 PASS. Driver lint vẫn FAIL cũ ở UsbPermissionObtainer.java:105, lifecycle emulator 1 PASS/4 FAIL và fake heartbeat Git Bash timeout giữ nguyên. Không tuyên bố mọi test đều PASS; xem [TEST-MATRIX.md](TEST-MATRIX.md).

Mô phỏng nguồn 27,5 Mbps/FIFO 2048 tái hiện legacy 35 ms có 15 seams gần 2048; vòng mới với jitter 30 ms thu 4 MiB không rơi, jitter vượt reserve báo rủi ro. Đó là kiểm mô hình, không TS Samsung. Windows cold2 còn CC/TEI lỗi và bitrate chuẩn UNKNOWN nên không gọi đối chứng sạch. Hai B2 lịch sử vẫn INVALID_TS; root cause chưa xác nhận, `TS_SMOKE_PASS=false`.

Đã đọc Samsung SM-X926B/API36/boot47/Awake để điền card, không install/launch/tuner I/O. Emulator sở hữu đã đóng và không cổng listener. Card ngoài Git đã khóa SHA `e397f1fd804d336a41b7983bd0d1bb56ba994be92edeb83b943f1aad73ab17cc`, source manifest 87 tệp và artifact manifest đã đối soát; schema ba lượt/cấu hình hợp lệ. Bước tiếp: trình card và đợi đúng `OK B3`, rồi mỗi lượt người dùng ngắt cả nguồn chữ Y ≥5 giây; tối đa ba lượt, `-75` hoặc detach sau reload đóng phiên. Không stage/commit/push, không Phase 4, VM hoặc sửa references.

## 2026-11-12, nhiệm vụ B3 từ 20:31 +07 — Analyzer đã kiểm, vòng nhận liên tục đang triển khai

Baseline đã kiểm đúng branch `chore/modernize-android-build`, HEAD `9bee570aa5e59879e11b7c5b8b1086b80c5fb05b`, tree sạch lúc bắt đầu. [ADR-054 và ADR-055](DECISIONS.md) mở phạm vi B3 theo yêu cầu mới; giữ an toàn firmware/vendor/nguồn và đường legacy. Cửa sổ permission 30 giây đã kiểm JVM 9/9 PASS; native legacy host baseline PASS. Chưa đủ gates cho candidate B3.

Analyzer mới 42/42 PASS xác nhận hai raw B2 có 17 đoạn/16 chỗ đứt gần 2048, CC trong đoạn 0/104 và 0/109. Windows cold2 đúng SHA sau prefix không mất sync nhưng có 1705 lỗi CC/3054 TEI/14 dịch vụ, không phải đối chứng sạch. PCR chuẩn UNKNOWN; 27,487 Mbps chỉ là ước lượng chẩn đoán có điều kiện. Không nhận các giả định đầu thành bằng chứng mới; xem [RESEARCH.md](RESEARCH.md).

Đang triển khai `NATIVE_USBFS_STREAM`, watchdog và helper ngân sách phiên; chưa freeze APK/card, chưa cài hoặc thử Samsung trong nhiệm vụ này. Phần cứng B3 NOT RUN, `TS_SMOKE_PASS=false`, root cause chưa xác nhận. Chỉ trình card và đợi `OK B3` sau kiểm phần mềm; tối đa ba lượt với power-cycle do người dùng thực hiện. Không stage/commit/push, không VM hoặc sửa references.

### Bước tiếp theo đề xuất — Phase 4

Chỉ là kế hoạch sau B3 đạt yêu cầu: tích hợp TBS5530 vào framework production `DvbUsbDevice` với bootstrap ảnh gốc và reload sau SET_CONFIGURATION, vòng nhận liên tục, PLP auto/manual, rồi thử độc lập OnePlus 7. Không triển khai Phase 4 trong nhiệm vụ này.

## 2026-11-12, sau run audit 19:40–19:41 +07 — Vẫn INVALID_TS; đã thu chứng cứ, dừng sửa theo yêu cầu

**Đã chạy đúng một preparation/B2 mới được duyệt, nhận 65.536 byte nhưng TS vẫn lỗi.** Bootstrap/reload đều 132/132, 16 URB đều status 0/actual 4096/errno 0, audit quan sát 35 records/3903 byte có marker và hash seal đúng, SYNCED_CLOSED. App vẫn mất đồng bộ 16 lần, PAT/PMT 0; không TS_SMOKE_PASS hoặc ROOT_CAUSE_CONFIRMED. Bỏ ghi audit từng boundary chưa đủ làm mẫu TS sạch, chưa biết nguyên nhân gốc.

Cửa sổ 16 COPY giảm từ 674,676 xuống 576,444 ms giữa hai run, nhưng vẫn cách nhau 33,802–43,877 ms; đây không phải phép đo riêng chi phí audit hoặc bằng chứng quy lỗi cho guard/kernel/FIFO. TSDuck raw không nhận dạng TS dù exit 0; bản dẫn xuất có 162 discontinuities/7 duplicates và không service, không phải raw PASS. Handoff lần này tiếp tục ghi một operational broadcast và một intent trước descriptor seal/owner; không hai broadcast hoặc timeline B1.

Đã DRAIN_CONFIRMED/RUNTIME_DRAIN_CONFIRMED, outstanding 0/quarantined=false; user xác nhận rút tuner/ngắt nguồn. Đã thu 32 tệp app lưu sẵn, đối soát SHA tablet/host, phân tích hoàn toàn offline; không run/retry thêm. History cuối 1328 byte/16 records/hash 15666f1a…, prefix 1162 byte nguyên; journal 308 byte/a6f07eb2… và anchor 224 byte/a1dbd197… giữ consumed. Không init/recovery/reset/rearm.

**Chốt phiên:** Người dùng yêu cầu chỉ thu chứng cứ/phân tích, không sửa lỗi tiếp khi chưa biết nguyên nhân. Không sửa thêm source/helper/tests sau freeze, không grant hoặc thử phần cứng mới. Chỉ hồ sơ/README/báo cáo kết quả được cập nhật; không stage/commit/push. Bundle mới `phase31-b2-audit-live-20261112-193034-c05c9411` dưới `D:\TSDuck\logs` và `D:\TSDuck\reports`; xem [RESEARCH.md](RESEARCH.md), [ADR-053](DECISIONS.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md).

## 2026-11-12, sau B2 17:55–17:56 +07 — Đã nhận dữ liệu USB, TS chưa đạt; tối ưu ghi nhật ký chỉ kiểm offline

**B2 đã nhận 65.536 byte, nhưng chưa nhận được luồng truyền hình sạch.** Bootstrap gốc và reload đều hoàn tất 132/132; 16 URB (yêu cầu nhận USB) đều status 0, actual 4096, errno 0, outstanding 0 và đã drain. App báo INVALID_TS: 16 lần mất đồng bộ, không PAT/PMT (bảng mô tả chương trình). Trạng thái là `ROOT_CAUSE_CANDIDATE_H_AUTOINLEN` + `B2_POSITIVE_SINGLE_RUN_TRANSPORT_WITH_INVALID_TS`; chưa ROOT_CAUSE_CONFIRMED hoặc TS_SMOKE_PASS.

Handoff thực có một ATTACH_BROADCAST và một ON_NEW_INTENT của attachment operational, sau detach/bootstrap drain/UI reset; descriptor được seal rồi owner mới bắt đầu. Không quan sát hai broadcast, không hồi tố kết quả này thành nguyên nhân B1 cũ. Reload sạch, cửa sổ release→wait end 1630,924231 ms, không detach. Xem timeline và giới hạn trong [RESEARCH.md](RESEARCH.md).

Lượt permission-only trước đã timeout và đóng trước khi tuner connect; lượt preparation mới trên attachment đang cắm được duyệt riêng, không phải retry tự động. History mới 1162 byte/14 records/hash 855d2134…, reload journal e4f2f2f5… và anchor da80da1e… đã consume; không reset về các pins init cũ. User đã xác nhận rút tuner/ngắt nguồn sau drain.

Theo quyền offline mới, chỉ nhật ký quan sát nhận TS B2 được giữ trong RAM có giới hạn rồi ghi bền sau drain; `.started` ghi bền trước receive, `.complete` chỉ sau commit/hash readback. Thiếu audit hoặc commit lỗi không được chứng nhận TS. Grant/consume/history/store/permission/lock và mọi vị trí kiểm quyền giữ nguyên; không đổi TTL, deadline, queue, kích thước hoặc giới hạn thu. Kiểm cuối app JVM 95/95, emulator fake/cache 28/28 và coordinator 41/41 PASS. Đo emulator đầu: đường ghi khi receive giảm từ 214,8714 ms xuống 16,1782 ms cho 34 records, commit sau drain 18,1421 ms; lượt cuối 3662,6792/360,1311/320,9210 ms cho thấy biến thiên lớn, không phải số đo Samsung hay bằng chứng đã sửa TS.

Bundle nhận dữ liệu: `phase31-permission-first-20261112-173615-2b868332`; bundle tối ưu offline: `phase31-ts-quality-offline-20261112-183244-7b4e5c12`, đều dưới `D:\TSDuck\logs` và `D:\TSDuck\reports`. Bước tiếp là chốt kiểm thử/card/hash và xin duyệt riêng một phép thử chất lượng mới; chưa có quyền hardware cho candidate audit. Giữ lint driver cũ/bốn lifecycle FAIL và việc dọn CRLF sau B2; không stage/commit/push. Xem [ADR-052](DECISIONS.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md).

## 2026-11-12, sau initial-fix live — Initial admission qua, upload bị chặn vì thiếu ngân sách thời gian

Run mới riêng trên APK 3ee6cf24… đã được duyệt và đi qua OS permission callback, ON_NEW_INTENT provisional và raw FX2 profile/seal trước bootstrap. Dừng UPLOAD_PREFLIGHT_REFUSED ở FX2_DEFAULT: attempted/native A0 đều 0, còn 189456 ms nhỏ hơn guard 285000 ms trong TTL 300000 ms. Đã drain, history/store/tombstones giữ nguyên, user rút tuner/ngắt nguồn; không retry hoặc TS/H-AUTOINLEN evidence mới.

User duyệt rà/test offline phương án permission-first: chuẩn bị quyền OS bằng Activity permission-only có sẵn trước khi tạo/grant B2 session, không đổi TTL 300 giây/upload guard 285 giây/heartbeat 2 giây/cửa sổ permission 10 giây. Coordinator host mới đã kiểm 34/34 fake guards, không Android/APK/runtime helper thay đổi. Chưa quyền hardware mới; card/approval riêng sẽ được hỏi trong phiên. Xem [ADR-051](DECISIONS.md), [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md). Giữ ROOT_CAUSE_UNRESOLVED/TS_SMOKE_PASS=false.

## 2026-11-12, sau lượt B2 đã đóng — Đã sửa initial attach offline, chưa chạy tuner mới

**Đã sửa nhánh attach ban đầu đến sau polling selection/request, chỉ cho B2.** Scope provisional được tạo sau grant, trước đăng ký listener/discovery; giữ cùng session/epoch/identity/permission attempt, từ chối mọi detach ở phase ban đầu. Sau OS grant mới đọc raw tree có sẵn, bắt buộc profile FX2_DEFAULT đầy đủ trước bootstrap; seal đóng ngoại lệ rồi chuyển sang scope operational cũ. Generic ABA ngoài B2, operational descriptor 50 byte/hash, bootstrap proof, TTL/heartbeat/deadline và pipeline firmware/frontend/native không đổi.

BEFORE một test lịch ghi nhận FAIL đúng state INVALIDATED; AFTER 7 initial + 14 operational + 30 generic permission PASS. App JVM đầy đủ 95/95, build debug/test/release/app lint và emulator fake 20/20 PASS; giữ failure fixture admission-latch ở lượt AFTER đầu, không nới latch. APK candidate SHA `3ee6cf24540bcf294850df4fff6a0a642ec9c3d9ed8345d23cc290dd2a355722`, chưa cài tablet hoặc chạy phần cứng. Các PASS này không chứng minh Samsung live sẽ qua initial attach hoặc nhận TS.

Phần I thực của lượt trước vẫn PASS, stores/history/tombstones đã consume giữ nguyên; B2 trước dừng an toàn trước bootstrap/reload. Card mới cần duyệt riêng cho một run mới nếu người dùng muốn, không retry từ grant cũ. Bundle offline mới `phase31-initial-attach-offline-20261112-165718-83b1da6b` dưới `D:\TSDuck\logs` và reports; xem [ADR-050](DECISIONS.md), [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md). Không stage/commit/push, không TS_SMOKE_PASS.

## 2026-11-12, phiên 15:39 +07 — Kết quả: Phần I PASS; một B2 dừng trước bootstrap/reload, đã drain và rút tuner

**Đã hoàn tất recovery witness và init no-tuner. B2 duy nhất đã chạy nhưng không tới phép thử reload.** Dừng `ATTACH_DUPLICATE_OR_ABA_UNVERIFIED` tại NEED_ANDROID_USB_PERMISSION, trước bootstrap: firmware/reload/native receive đều null, app EP0 mirror 0 records, không có byte TS. Vì vậy không có thông tin mới về H-AUTOINLEN, không root-cause confirmed; giữ Phase 3.1/ROOT_CAUSE_UNRESOLVED/TS_SMOKE_PASS=false.

- **Phần I PASS thực:** Recovery một lần 581→996 byte/7→12 records, prefix 581 byte nguyên; journal recovery 906 byte/hash 7254d8e7… giữ. Init một lần đúng journal 138 byte/anchor 94 byte/marker 99 byte và hashes đã khóa; tombstone init 1131 byte/SHA `1f5aa7333d6f9019f653677970241c5ba54180531d56b387a2a70dcbd8ed32e8`. History 996 byte/12 records/b6b5935a… trước/sau exact, installed APK 9493d0b6… đúng, USB I/O tuner 0 và monitor 30/current empty/host history không đổi.
- **H1 runtime mới:** SELECTED → +10,075 ms PERMISSION_REQUEST → +23,127 ms một ATTACH_BROADCAST cùng identity → +25,291 ms guard invalidate. Cả bốn events ở main, trước bootstrap, không ON_NEW_INTENT/detach/callback/operational owner trong journal. Một broadcast đến sau polling selection đủ kích guard ở lượt này; chưa chứng minh hai delivery operational hoặc nguyên nhân B1 cũ. Operational handoff/reload chưa được thử.
- **Chốt sau B2:** Helper exit 1 đúng terminal fail, DRAIN_CONFIRMED; history/store/anchor/marker/init tombstone không đổi, session ledger chỉ SESSION_GRANTED. Không retry/split launch hoặc sửa guard rồi chạy lại. Hậu kiểm fd phụ bị timeout nên UNKNOWN, không nhận fd 0; terminal drain đã xác minh, user xác nhận rút tuner/ngắt nguồn chữ Y sau drain. Không hậu kiểm USB mới sau rút.

Đã có [ADR-049](DECISIONS.md) và báo cáo/timeline trong bundle `phase31-witness-recovery-20261112-154425-686a238c` dưới `D:\TSDuck\logs` và reports. Bước tiếp chỉ đề xuất điều tra/sửa offline nhánh attach ban đầu với test tái hiện, card/phê duyệt mới trước lượt phần cứng khác; không dùng grant B2 này cho lượt tiếp. Không stage/commit/push.

## 2026-11-12, phiên 15:39 +07 — Recovery witness v2 đã kiểm phần mềm, chờ exact live approval

**Đã thêm đường khôi phục chỉ nối đúng năm khóa cấm còn thiếu; chưa ghi history tablet.** User duyệt triển khai offline, không sửa recovery v1 mặc định hoặc parser history. V2 khóa before 581 byte/7 records/e398459c… và after 996 byte/12 records/b6b5935a…, giữ prefix, journal exact-plan O_EXCL/fsync trước append; partial/authority loss không retry hoặc rollback. Full history trước witness vẫn UNKNOWN.

JVM focused 10/10, helper 8/8, initializer regression 33/33, encoding 4/4 và emulator final 18/18 PASS; debug/release/AndroidTest/app lint PASS có phạm vi, release vắng recovery. Emulator riêng đã dừng. APK mới SHA `9493d0b66027827a532a6aa3f0f9db868b67062c0a3b9a4397e45cbb7800fada`, recovery helper `0762fb16eae0d9ab547b2400b34730ea673660bb087da1cae8dc36694ecc12b5`, plan `da1ccd0a385706147d405a90529594b407d21301d61676b682e14f1c6ba38eb2`; cần duyệt exact card trước install/apply. Tablet read-only thấy marker initialized, không .bak/.new, journal v1/v2 đều vắng; không tái dùng v1 đã từng chạy.

Bundle `phase31-witness-recovery-20261112-154425-686a238c` dưới `D:\TSDuck\logs` và reports giữ source/tests/APK/card mới. Chưa recovery/init/B2 thật, H1 UNKNOWN và TS_SMOKE_PASS=false. Trong phiên sẽ hỏi người dùng tại chốt cần duyệt, không tự mở B2 trước Phần I PASS và câu riêng. Không stage/commit/push; xem [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md).

## 2026-11-12, phiên 15:25 +07 — Đã cài lại đúng APK; history hiện tại thiếu năm records, init vẫn chặn

**Cài lại PASS, nhưng Phần I chưa PASS.** Người dùng xác nhận đã lỡ gỡ app và yêu cầu cài lại. Đã `install -r` đúng APK một lần, installed SHA `11983e5c56ba20ec2e32b583fb95ceb68a01493f5094d9335ff0933d0e20d920`. Trước cài thấy màn hình Dozing nên đã báo, chờ người dùng mở rồi mới cài; không đổi khóa màn hình hoặc tự bật.

History sau cài có 581 byte/7 records/hash `e398459ceddb2c8f35ddc61b81ee66a31ec1aee300c33bb603dc9f593b0b31f9`, khớp snapshot cũ và là prefix chính xác của witness 996 byte/12 records/hash b6b5935a… ngoài host. Thiếu 415 byte/5 records; chưa có trace để kết luận cơ chế dữ liệu cũ trở lại. Ba reload targets và tombstone đều ABSENT, không được dùng để tự init khi history fence chưa đạt.

Không mở app thủ công, initializer, grant, restore/rearm hoặc tuner I/O; B2 NOT RUN, H1 UNKNOWN, TS_SMOKE_PASS=false. Bước tiếp chỉ đề xuất kế hoạch khôi phục có đối soát phần history thiếu và journal chống dùng lại, được duyệt riêng trước mọi ghi app; không thay witness bằng bản bảy records. Bundle `phase31-app-reinstall-20261112-152643-2f333465` dưới `D:\TSDuck\logs` và reports có installed hash/current bytes/prefix proof. Xem [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md).

## 2026-11-12, 15:18 +07 — Card init v2 đã được duyệt; dừng vì run-as không nhận package

**Phần I vẫn BLOCKED trước install/init; không đọc được history thật.** Sau khi người dùng duyệt exact card v2 và xác nhận tuner rút/nguồn chữ Y ngắt/màn hình mở, preflight mới qua model/API/boot/current USB nhưng `run-as` trả `unknown package: info.martinmarinov.dvbdriver`. ADB exit 0, song stdout 54 byte là thông báo lỗi, không phải history 996 byte/12 records. Guard hash dừng đúng, không dùng lỗi này làm dữ liệu hoặc tự chấp nhận witness mới.

Không install, launch initializer, grant, tombstone/store hoặc tuner I/O; không ADB thêm sau stop. Không kết luận app/data đã mất hoặc tự cài để tạo history rỗng. Cần điều tra chỉ đọc package/user/profile trước một kế hoạch phục hồi riêng; chưa thực hiện. B2 NOT RUN, H1 UNKNOWN, ROOT_CAUSE_UNRESOLVED, TS_SMOKE_PASS=false. Helper v2 đã kiểm 33/33 mỗi shell và giữ hash đã duyệt; snapshot freeze/card ở bundle `phase31-b2-live-20261112-133010-a5b7ea58` giữ nguyên, sáu hồ sơ chỉ bổ sung kết quả sau stop. Xem [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md).

## 2026-11-12, phiên 13:28 +07 — Sửa guard USB sau preflight dừng; card init mới chờ duyệt

**Đã sửa lỗi thứ hai: helper nhầm lịch sử USB với thiết bị đang cắm.** Preflight thật qua Samsung SM-X926B/API36/boot47, màn hình Awake, rồi dừng trước install vì history còn `device_address`. Raw có history connect/disconnect, không có current `devices` trong host block. Người dùng duyệt sửa offline helper/tests và khóa card mới, chưa duyệt init với hash mới.

- Guard mới tách phần current trước `num_connects` khỏi `connections` lịch sử, từ chối schema lạ/truncated hoặc bất kỳ current field nào. Kiểm mới PS7/PS5.1 mỗi shell 33/33 PASS, encoding 4/4 PASS; raw thật offline PASS, injected live device bị chặn, 100 lần truncated 60 ms. Giữ attempt regex test timeout và fixture CRLF lỗi làm evidence.
- APK/runtime B2/plan/templates/TTL120/heartbeat2 giây không đổi; initializer helper mới SHA `edd2961fa7b9fc94abbf1bb663820d4ebca00b72b9b3d20f9fb8bc39f4513351`. Không dùng phê duyệt hash cũ để chạy helper mới; bundle `phase31-b2-live-20261112-133010-a5b7ea58` dưới `D:\TSDuck\logs` và reports có card/manifest mới.
- **Chốt:** Chưa install/launch/grant/tombstone/store, history và APK installed chưa đọc ở lượt này; Phần I vẫn BLOCKED, B2 NOT RUN, H1 UNKNOWN, TS_SMOKE_PASS=false. Chỉ xét preflight/init mới sau duyệt exact card mới; nếu PASS vẫn dừng chờ câu `TIẾP TỤC B2`. Không ADB thêm sau failure, không stage/commit/push. Xem [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md).

## 2026-11-12, phiên 13:14 +07 — Đã sửa wrapper và kiểm offline; chưa chạy lại tablet

**Lỗi báo nhầm `MODEL_MISMATCH` đã được sửa và kiểm offline 12/12 PASS.** Bản wrapper mới ngoài Git bỏ giá trị trả về của lệnh chờ sao chép bất đồng bộ; hàm nay trả đúng một chuỗi, không còn kèm `VoidTaskResult`. Kiểm toàn bộ preflight bằng tiến trình giả cũng PASS; không phải kết quả Samsung thật. Phần I thực vẫn BLOCKED/NOT RUN sau chốt dừng trước, B2 NOT RUN và H1 UNKNOWN.

- Bản sửa/hash và tests nằm trong bundle `phase31-wrapper-fix-20261112-131739-13025118` dưới `D:\TSDuck\logs` và reports. Giữ wrapper lỗi/bundle cũ nguyên để truy vết; 14 tệp source/helper ngoài docs và 11 artifacts frozen không đổi, không sửa lint/lifecycle/CRLF hoặc rủi ro detach đã hoãn.
- Lượt này không dùng ADB tablet, cài APK, launch initializer, grant/store write hoặc tuner. Khi cần ADB tiếp, nếu kiểm thấy màn hình tắt phải báo người dùng mở lại; không tự bật màn hình hoặc đổi timeout khóa máy.
- Bước tiếp chỉ đề xuất: một preflight Samsung mới, chỉ sau quyết định cho phép đọc lại. Chưa có PASS mục 2.4 hoặc hash tombstone mới; không tự mở init/B2, không stage/commit/push. Xem [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md).

## 2026-11-12, phiên bắt đầu 12:55 +07 — Phần I dừng ở preflight host; chưa cài APK hoặc khởi tạo store

**Phần I BLOCKED, không PASS; B2 NOT RUN.** Wrapper thu evidence mới ngoài Git báo nhầm `MODEL_MISMATCH` vì trả thêm `System.Threading.Tasks.VoidTaskResult` vào pipeline. ADB thực trả đúng `SM-X926B`, exit 0; đã tái hiện nguyên nhân hoàn toàn offline bằng MemoryStream. Dừng ngay theo quy tắc không retry, không sửa helper đã freeze hoặc gọi ADB thêm. Chưa install, launch initializer, publish grant, tạo tombstone/store hoặc gửi USB I/O tới tuner.

- **Nền đã kiểm:** Branch `chore/modernize-android-build`, HEAD `c4afcf9d1cd125670106f26dd84034908322377e`, tree sạch lúc bắt đầu; 20/20 tệp freeze, 11/11 artifacts và hai manifest khớp size/hash. Initializer guards offline 9/9 PASS mới, không suy thành actual init PASS.
- **Quyền và trạng thái:** Người dùng đã xác nhận rút tuner khỏi Samsung và ngắt nguồn chữ Y. Chưa đọc `dumpsys usb`, boot, history hoặc installed APK trong preflight bị dừng; ba targets/tombstone và mọi điều kiện actual init vẫn NOT RUN. Không có hash tombstone mới hoặc timeline handoff mới; H1 giữ UNKNOWN.
- **Disposition được duyệt:** Driver lint debug/release cũ là “pre-existing, không chặn B2 debug”; bốn lifecycle FAIL emulator ngoài allowlist là giới hạn môi trường, vẫn giữ FAIL. Không sửa hoặc suppress. **CRLF: dọn sau B2**, không đổi bytes source freeze trong nhiệm vụ này.
- **Rủi ro giữ nguyên:** Sau observe/select operational, `detached()` vẫn reject mọi detach đúng VID:PID dù identity khác. Detach FX2 cũ đến muộn có thể gây `USB_DETACHED`; chưa quan sát ở lượt này. Nếu gặp trong B2 được duyệt sau này, so `device_identity_sha256` trong events rồi dừng, không tự sửa/chạy lại.

**Điểm dừng:** Giữ tuner rút; cần quyết định riêng để sửa/kiểm wrapper offline và cho phép preflight mới. Câu `TIẾP TỤC B2` không đủ mở B2 khi Phần I chưa PASS. Giữ `Phase 3.1`, `ROOT_CAUSE_CANDIDATE_H_AUTOINLEN`, `ROOT_CAUSE_UNRESOLVED`, `TS_SMOKE_PASS=false`. Bundle `phase31-b2-live-20261112-125721-9a964d76` dưới `D:\TSDuck\logs` và `D:\TSDuck\reports`; xem [RESEARCH.md](RESEARCH.md), [DECISIONS.md](DECISIONS.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md). Không stage/commit/push hoặc thay source/helper frozen.

## 2026-11-12, phiên bắt đầu 11:20 +07 — Đã sửa handoff offline; hai card mới chờ duyệt, chưa init/B2 thật

**Đã bỏ chặn nhầm attach đến muộn trong một phạm vi hẹp trước owner; chưa chứng minh Samsung sẽ handoff thành công.** H1 xác nhận B1 đủ 132/132 rồi lỗi duplicate/ABA tại NEED_ANDROID_USB_PERMISSION, nhưng raw thiếu từng event nên hai attach và race thật vẫn UNKNOWN. Giữ `Phase 3.1`, `ROOT_CAUSE_CANDIDATE_H_AUTOINLEN`, `ROOT_CAUSE_UNRESOLVED`, `TS_SMOKE_PASS=false`.

- **Hướng A:** Scope trong bộ nhớ cùng session/physical epoch, quan sát detach/attach qua runtime close/UI reset; chỉ giữ tạm attach cùng identity/token/generation trước owner, rồi bắt buộc raw descriptor50/hash exact trước mọi claim/control/Bulk. Sau seal hoặc có detach/identity/epoch khác vẫn fail; generic PermissionState/invalidate và proof bootstrap private không đổi. Không split launch/proof bền hoặc rearm ledger.
- **BEFORE/AFTER:** Test lịch giả BEFORE FAIL đúng ATTACH_DUPLICATE_OR_ABA_UNVERIFIED; corrected 14 handoff/30 permission/8 B2 focused PASS. Full JVM fresh driver 481/app 88/usbxfer 32 PASS, 0 SKIP. Native core 4096/8192 và FX2 plan 132 PASS; PS7/PS5.1 heartbeat 82/init 9/encoding 4 và focused export 38 PASS; Python 77/generator 8 PASS. Giữ FAIL fixture descriptor đầu trước khi sửa fixture đúng raw.
- **Build/emulator:** Debug/release/AndroidTest/app lint và bốn ABI/JNI/release isolation PASS có phạm vi; release fresh 155 tasks. Emulator driver 19 PASS; app 48 có 44 PASS/4 FAIL lifecycle trên model ngoài allowlist, trong đó handoff mới 3/init 5 PASS. Standalone driver lint debug/release vẫn FAIL cũ ngoài diff. Không nới heartbeat/deadline/host guard hoặc nói mọi gate xanh.
- **Freeze:** Hai card mới dưới `D:\TSDuck\reports\phase31-handoff-offline-20261112\frozen-cards`, pin commit nền e8cb2ceab7c6fd7a741fd8230381b4f59935540c cộng diff/manifest phiên này, APK và helper mới. Footer in đúng số tệp/path/hash. BOM phục hồi cho PowerShell non-ASCII; hai Python files chỉ đổi LF. Bootstrap original/reload JNI 132/delay 100 ms + wait 1500 ms/MANUAL0/drive 54/8×4096/golden giữ nguyên.

**Điểm dừng:** Duyệt card khởi tạo store không tuner trước; chỉ sau init verified mới xét card B2 riêng cùng disposition lint đề xuất “pre-existing, không chặn B2 debug” và giới hạn lifecycle emulator đã ghi. Card không là grant. Không cài Samsung, init store thật, USB transfer hoặc thao tác tuner trong phiên này. Handoff bất ngờ vẫn phải dừng trước reload, không A0 reload/retry; chỉ tốn lượt rút/cắm và physical epoch/bootstrap consume tương ứng. Emulator riêng đã dừng, không listener ở cổng 5580/5581; không stage/commit/push, branch/HEAD nền giữ nguyên. Xem [RESEARCH.md](RESEARCH.md), [ADR-048](DECISIONS.md), [TEST-MATRIX.md](TEST-MATRIX.md), [WORKLOG.md](WORKLOG.md).

## 2026-11-12, phiên 07:38 +07 — B2 đã kiểm phần mềm; card chờ duyệt, chưa có quyền chạy thật

**Đã tìm được một ứng viên giải thích lỗi: SET_CONFIGURATION gọi firmware routine đổi AUTOINLEN từ 512 thành 1024 byte.** Cổng A `SUPPORTED_OFFLINE`, không phải root cause đã xác nhận. Giữ `Phase 3.1`, `ROOT_CAUSE_UNRESOLVED`, candidate `ROOT_CAUSE_CANDIDATE_H_AUTOINLEN` và `TS_SMOKE_PASS=false`; chỉ xét đổi sau B2 dương tính và một lần lặp độc lập được duyệt.

- **A1:** Ảnh đã pin hash, init `04CF` đặt `0200`; request 9→`0350`→`0DCA` đặt `0400`; request 11 không ghi AUTOINLEN trên đường đã kiểm. RENUM branch được mô phỏng hai trạng thái. Inventory literal có bốn writers, chưa loại trừ mọi DPTR động. 77/77 tests PASS cuối với ảnh và subset CSV riêng, không giảm PASS Gate B cũ.
- **A2/A3/A4:** Windows cold2/prep2 có config giữa hai uploads, 660 controls của năm lượt khớp source Java từng setup/payload. Cold2 release→Sony đầu là **1288,912 ms**, không 0,58 giây; mốc ngắn hơn là EEPROM. TBS/CoreELEC probe mới và Go open lại có đường reload, không chứng minh mandatory probe2 hoặc causal fix. D1/D2 host config sau handoff UNKNOWN; chưa có đối chứng đảo chiều sạch.
- **B cuối:** Bootstrap đã giữ original backend B1; chỉ reload dùng native syscall audit. Giữ MANUAL0/drive 54/native 8×4096 và golden B1, một reload 132 sau claim operational/trước frontend, source delay 100 ms rồi wait 1500 ms. Có private clean proof cùng epoch, store append-only/hash fence và caller init no-tuner có grant/tombstone riêng, không sửa/rearm ledger cũ. Store thật/init/B2 live vẫn NOT RUN.
- **Kiểm cuối:** JVM fresh đủ private fixtures: driver TBS **464/464**, app **74/74**, usbxfer **32/32 PASS**, 0 SKIP; emulator corrected **30 app + 19 driver PASS** gồm original-bootstrap/reload và initializer giả. Debug/release/AndroidTest compile, app lint, helper/native fixtures/release isolation PASS có phạm vi. Standalone driver lint còn **FAIL cũ ngoài diff**; giữ failure emulator candidate đầu và các giới hạn, không tuyên bố mọi gate đều PASS.
- **C/D:** Đã thêm đính chính và [ADR-047](DECISIONS.md). Card `b2-experiment-card.md` cùng card init riêng trong bundle khóa APK/helper/plan/templates và source freeze; actual store/history hiện tại phải xác minh sau quyền no-tuner riêng, không lấy template làm trạng thái installed. Chưa cài APK Samsung hoặc gửi A0 thật; emulator riêng đã dừng/cổng 5580–5581 không listener. Không stage/commit/push.

**Điểm dừng D:** Đã chốt phần mềm và trình card có giới hạn, dừng chờ người dùng duyệt exact hashes. B2 phần cứng chỉ mở sau init no-tuner riêng được duyệt/verified và disposition driver lint FAIL cũ; không tự coi card là grant. Không dùng quyền này cho B7/040x, drive/buffer/interface/readback/RSSI, firmware vá hoặc retry. Branch/HEAD nền đúng `chore/modernize-android-build` / `5c1c3a0a02df5aeb63258b01b80e9ccc0116515e`; không stage/commit/push/sync references. Bundle `phase31-autoinlen-offline-20261112-073915-d3e4d287` dưới `D:\TSDuck\reports`/logs có verdict và evidence; xem [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md).


## 2026-11-11, phiên 22:18 — Recovery đã áp dụng; B1 MANUAL0 khóa K36 nhưng vẫn -75, chưa có TS

**Đã chạy đúng phép thử MANUAL PLP0 trên Samsung; Android vẫn nhận thành công 0 byte EP82.** Có khóa K36/sync `0x26`, nhưng cả 8 URB native 4096 byte đều status `-75`/actual=0. Vì vậy MANUAL0 đơn lẻ không đủ trong điều kiện đã thử; chưa xác định root cause hoặc loại mọi vấn đề PLP. Giữ Phase 3.1/ROOT_CAUSE_UNRESOLVED/TS_SMOKE_PASS=false.

- **Recovery hoàn tất:** Sau phê duyệt riêng từng exact build/helper/plan, ledger 7→9 records, 581→747 byte, prefix cũ giữ nguyên và journal no-backup consumed bền. Không được chạy lại recovery plan. User duyệt dùng ledger đã đối soát + hash fence làm witness từ đây, full history trước witness vẫn UNKNOWN.
- **B1 thực:** Một original firmware bootstrap đủ 132 controls/8192 image byte trong 3545 ms. Sau natural re-enumeration, operational-only được duyệt tiếp với cùng epoch, không FirmwarePath/0 A0. MANUAL0 bank23 AF00/AD01, drive 54/54, stream 8A0A00, 191 full-length EP0 controls; alt0/readback OFF. Actual PLP/L1 vẫn UNKNOWN.
- **Transport/cleanup:** 8 SUBMIT/8 REAP, syscall=0/errno=0 nhưng 8×URB -75/actual=0, payload rỗng. Terminal REPORT/USB_RECEIVE_FAILURE, DRAIN_CONFIRMED, outstanding=0/quarantine=false và app USB fd=0. Thu/hash 20 files operational. User đã rút tuner sau drain; ADB sau rút không còn device nên không khẳng định hậu kiểm host-empty mới.
- **History cuối:** Owned bootstrap append hợp lệ thành 12 records/996 byte, prefix 747 giữ nguyên, app/host SHA `b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb`. Không reset/rearm/retry firmware. APK thực SHA `f848e12c286aa40e7a0ff413f24df20aba63908a938d3162205e6da2aa727d71`; source/main production giữ baseline, coordinator chỉ debug.

**Giới hạn bước tiếp:** Không lặp MANUAL0 hoặc chuyển tổ hợp/DRIVE55/B7/040x/custom firmware/VM tự động. Gate C và E vẫn cần nguồn/safety đúng chip/board, không dùng quyền B1 để vượt. Điểm cuối đã chứng minh tốt là frontend lock/TS config; ranh giới chưa quan sát là TS pins/board→FX2 FIFO→first USB completion. Bundle `phase31-recovery-coordinator-20261111-222119-cd9dcedf` dưới D:\TSDuck\reports có `b1-manual0-result.md`, raw/hash pointers và decision tree; xem [TEST-MATRIX.md](TEST-MATRIX.md), [RESEARCH.md](RESEARCH.md), ADR-046 ở [DECISIONS.md](DECISIONS.md). Không stage/commit/push.

## 2026-11-11 — Đã triển khai recovery core và hash fence; chỉ kiểm offline/emulator, chưa đổi tablet

**Đã hoàn tất phần lõi theo quyết định thiết kế offline; chưa áp dụng candidate và B1 vẫn NOT_RUN.** User duyệt `implement_guarded_recovery_offline`, không duyệt ghi Samsung hoặc tuner. Source debug mới chỉ cho append đúng hai deny records theo exact plan, không bỏ entry cũ; lỗi sau write giữ COMMIT_UNKNOWN và cấm retry/rollback. Adapter mở file có sẵn O_APPEND|O_NOFOLLOW rồi sync, không tạo store rỗng hoặc có exported apply Activity.

- B1 nay bắt buộc hash ledger được host pin trong challenge/grant/helper. Trong phiên, history mismatch bị giữ thành lỗi không thể tự rearm dù bytes cũ trở lại; chỉ advance sau consume append-only của owner. Helper giữ EP0 evidence sau drain trước khi từ chối mismatch và ghi witness ngoài app, không tự duyệt hash cuối cho run sau.
- Kiểm mới PASS: Node 29/29; driver JVM 456/456; app JVM 74/74; emulator 24/24 gồm grant/fence và append/fsync bằng fixture cache riêng. Debug/release/lint/AndroidTest compile PASS; release không có classes recovery/fence. Giữ một legacy eligible failure ở cold emulator đầu, targeted và full sau PASS, không nới heartbeat hoặc xác nhận nguyên nhân timing.
- Samsung hậu kiểm chỉ đọc 22:07 giữ ledger 7 dòng/hash e398459c... và APK cũ/hash bbbf25cc... nguyên trạng, current USB host rỗng. APK mới chỉ ở build/emulator, SHA `5cb28185685b991bce689ff6068c6e7d8186ac79c5b059226ad53155a682da1c`; helper mới SHA `63f096c20d8ad886510c3dc194d510b9bef03212ae1d2590e158e68b7b691c07`. Card/hash B1 cũ không còn đủ để chạy build mới.

**Còn chặn trước apply:** Chưa có caller UI/grant/journal dùng một lần và host coordinator được rà; chưa kiểm power-failure hoặc hash-guard latency trên Samsung. Không tuyên bố live-ready hoặc full-history restore. Cần hoàn thiện/rà đường điều phối này, freeze đúng hashes rồi xin quyền ghi app riêng; không dùng candidate 9 dòng tự mở B1. Kế hoạch và báo cáo `guarded-implementation.md` trong bundle `phase31-history-recovery-20261111-210818-73007003` dưới D:\TSDuck\reports. Giữ Phase 3.1/ROOT_CAUSE_UNRESOLVED/TS_SMOKE_PASS=false; không stage/commit/push.

## 2026-11-11 21:05 — Đã kiểm backup và chuẩn bị recovery chỉ bổ sung khóa chặn; chưa ghi app

**Không tìm được ledger đầy đủ mới hơn; B1 vẫn chưa chạy.** Đã kiểm 3.203 tệp text, 313 mục text trong 5 ZIP và các bản `.bak`/`.new` chính xác trên Samsung. Chỉ còn ledger 7 dòng đã biết; file phụ đều không có. Samsung SM-X926B/API36, boot 47, current USB host rỗng; lượt này chỉ đọc/hash/backup, không mở app, cài/gỡ hoặc I/O tuner.

- Thêm [công cụ recovery offline](../../tools/diagnostics/README.md) tạo candidate **deny-only**, nghĩa là chỉ bổ sung khóa cấm cho epoch đã nạp được bằng chứng xác nhận. Giữ nguyên 581 byte/7 dòng cũ, thêm hai khóa Sony/lifecycle ngày 10/11 thành 747 byte/9 dòng; không dựng attachmentKey thiếu hoặc nhận là full restore.
- 8 tests recovery và 21 decoder regressions PASS; proposal trên raw thật PASS, CLI từ chối overwrite đúng exit 2. Runtime, APK, helper và plan B1 đã khóa từ trước không đổi; không chạy lại Gradle hoặc Android instrumentation.
- Candidate chưa áp dụng: `appWriteAuthorized=false`, `liveGateBlocked=true`. Cần phê duyệt riêng exact baseline/candidate hashes và đường ghi durable trước bất kỳ append nào trên app. Không reset, rearm, copy đè hoặc tự rollback về bản 7 dòng; B1 tiếp tục `NOT_RUN_HISTORY_CONTINUITY_UNPROVEN`.

Kế hoạch đầy đủ và hashes nằm trong `D:\TSDuck\reports\phase31-history-recovery-20261111-210818-73007003\recovery-plan.md`; candidate/raw riêng dưới logs cùng run ID. Main manifest cho phép backup là source fact, chưa chứng minh backup/restore hoặc runner gây mất keys. Giữ Phase 3.1/ROOT_CAUSE_UNRESOLVED/TS_SMOKE_PASS=false. Bước kế tiếp là duyệt kế hoạch recovery có giới hạn, không phải cắm tuner hoặc chạy B1 ngay.

## 2026-11-11 — Samsung preflight PASS; B1 bị chặn vì lịch sử consumed chưa đầy đủ

**Chưa chạy B1 hoặc nhận byte EP82 mới.** Samsung SM-X926B/API36, boot 47 đã PASS 5/5 lifecycle; helper control-plane kết thúc sạch với firmware/native URB null và NO_RESOURCES_ACQUIRED. Người dùng đã duyệt một B1 có điều kiện, nhưng điều kiện bảo toàn lịch sử chưa đạt nên chưa phát grant hardware. Giữ Phase 3.1/ROOT_CAUSE_UNRESOLVED/TS_SMOKE_PASS=false.

- Ledger Samsung hiện có 7 entries/581 byte, đúng format và marker initialized; đã backup chỉ đọc, remote/local hash khớp. Hai epoch continuation ngày 09/11 MATCH dòng 4/6, nhưng expected epoch của Sony và lifecycle ngày 10/11 ABSENT. Chưa chứng minh history đầy đủ hoặc nguyên nhân thiếu; không tạo lại/restore/rearm hay dùng epoch mới để vượt gate.
- Runner instrumentation đầu làm APK không còn cài sau test; đã báo người dùng và được duyệt cài lại đúng APK, chạy giữ-app. Lượt sau lifecycle vẫn PASS 5/5, APK giữ lại và hash đúng. Không suy data loss từ APK vắng; chưa có snapshot ledger trước test để quy nguyên nhân thiếu keys cho runner/reinstall.
- Các kiểm phần mềm/offline và code MANUAL0 ở checkpoint trước vẫn hợp lệ. DRIVE55 và custom FX2 firmware vẫn BLOCKED_FOR_SAFETY. Không tuner/firmware/tune/Bulk mới, không đổi source/hashes hoặc consumed stores sau preflight.

**Bước tiếp:** Giữ tuner rút và dữ liệu app nguyên trạng; cần bản ledger đầy đủ có provenance/checksum hoặc kế hoạch recovery riêng được rà và phê duyệt. Không chạy lại firmware/B1 chỉ vì đã có grant có điều kiện. Report `b1-safety-blocker.md`, `attachment-history-continuity.md`, summaries preflight và backup pointers nằm trong bundle `phase31-android-first-20261111-172352-0a98eee3` dưới D:\TSDuck\reports; raw riêng dưới logs cùng run ID. Không stage/commit/push.

## 2026-11-11 — Ưu tiên Android; MANUAL0 đã sẵn sàng ở mức phần mềm, chưa chạy tuner

**Đã hoàn tất phần offline và triển khai phép thử chỉ đổi PLP; chưa có byte EP82 mới trên Android.** Giữ Phase 3.1, `ROOT_CAUSE_UNRESOLVED` và `TS_SMOKE_PASS=false`. Android là đường chính; Windows BDA nhận TS là đối chứng. Không cần làm Linux M nhận TS trước khi tiếp tục Android.

- **A / D — OFFLINE-TESTED:** Kiểm 119 inputs, 74 khớp hash lịch sử và 45 tự hash chưa có anchor cũ, không mismatch; 60 tests offline PASS. Thu hẹp khác ảnh firmware, descriptor và tuner init/tune BDA–VM, không chứng minh board/FIFO/USB PHY. Hash BDA trong prompt thiếu sáu ký tự; raw đúng được ghi ở RESEARCH.
- **B — MANUAL0 phần mềm PASS, live NOT_RUN:** Thêm `MANUAL_PLP0_K36` debug-only/default OFF, dùng API MANUAL có sẵn; chỉ thêm bank23/AF=00 và đổi AD=01. Giữ drive 54/54, native 8×4096, stream timing và limits. Challenge/grant khóa experiment, golden và APK SHA; helper bắt buộc hash đã kiểm, không ghép alt0/readback/8192 hoặc PLP tùy ý.
- **C — BLOCKED_FOR_SAFETY:** Có proposal offline chỉ đổi bank 00/96/97 thành 55/55 tại initialize; không có option live. TBS/community/Go có writes 54/54, nhưng CoreELEC init không có khối drive này. Chưa đủ exact CXD6802 pin/board mapping để chứng nhận hai bit đổi; không suy an toàn hoặc nguyên nhân từ Windows 55/55.
- **E — Công cụ host PASS; firmware/live BLOCKED:** Generator/verifier pattern 512 byte, sequence và CRC chỉ offline, 30 tests PASS; FX2 regression 51 PASS/13 SKIP. Không viết/nạp firmware tùy biến, không A0/GPIO/LNB mới.
- **Kiểm phần mềm:** 449 driver + 74 app unit PASS, 21 app + 13 driver instrumentation dùng USB giả PASS; native C fault injection 8×4096/8192 PASS, debug/release/lint PASS, release không có diagnostic classes, minSdk 21 giữ nguyên, bốn ABI có trong APK và LOAD alignment 64-bit=0x4000. Bốn activity lifecycle tests FAIL trên emulator không thuộc host allowlist; giữ failure, không nới guard. ASan NOT_RUN.

**Bước an toàn duy nhất tiếp theo:** Người dùng kết nối Samsung SM-X926B/API36 qua ADB, giữ tuner rút, để chạy lifecycle/control-plane trước. Chỉ nếu PASS mới xin/xác nhận đúng card B1, fresh physical epoch và OS permission rồi thực hiện một run bounded; chưa có grant live trong lượt này. Không replay B7/040x/double A0, chạy DRIVE55 hoặc lặp VM. Emulator tạm đã dừng; không có tuner run cần drain mới.

Branch/HEAD nền xác minh: `chore/modernize-android-build` / `8abf57c32bd75cf03508f6db62e03a0825e67868`; các edits chưa commit, không stage/push/sync references. Bundle ngoài Git: `D:\TSDuck\reports\phase31-android-first-20261111-172352-0a98eee3`, có overview, experiment card, evidence/source manifests và handoff. Xem [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md), ADR-042 ở [DECISIONS.md](DECISIONS.md).

## 2026-11-11, phiên 10:11 — Đã đối chiếu offline BDA/VM; phát hiện yếu tố gây nhiễu từ RSSI

**Không thấy khác ảnh firmware, descriptor operational hoặc chuỗi init/tune tuner; chưa có byte sửa Android đủ căn cứ.** Khác biệt mới: phép đo cường độ tín hiệu RSSI của VM còn ghi tuner, chạy năm lượt trước Bulk đầu và một lượt ngang lúc host báo BABBLE. Đây là yếu tố cần cô lập, chưa phải nguyên nhân gốc.

- BDA và VM có cùng descriptor hash operational/config=1/interface=0/alt=0/EP82/max packet=512. Tuner init 25 operations và tune 11 operations khớp thứ tự/payload, cùng yêu cầu manual PLP0. C3/C4/C6/80/81/95/A9/33/D9 có cùng last-write; drive 96/97 vẫn khác 55/54, không có post-write readback đủ để quy lỗi.
- VM có tuner 0x88 last-write=04 do RSSI và repeater 0x08 đang mở ngang Bulk; BDA trước Bulk giữ 00 sau tune. Official TBS/CoreELEC/C/Go đều xác nhận RSSI không chỉ đọc. Tuy nhiên, BDA có Bulk thành công trong ba RSSI windows sau đó; Android lịch sử giữ 0x88=00/repeater đóng vẫn -75. Không kết luận tắt RSSI sẽ sửa Android.
- BDA Bulk đầu frames 1650/1659 nhận 8192 byte/status=0 trong 2,291 ms; VM frames 743/744 BABBLE/0 byte trong 0,258 ms. Requested size ở cả hai USBPcap vẫn UNKNOWN. Queue 8 so với 1 tại completion đầu không là proof nguyên nhân vì VM lỗi trước khi queue đầy.
- Đã chạy 52 offline tests PASS và TShark/TSDuck trên file cũ; TS BDA cùng phiên có 3054 TEI/164350 packets, không phải baseline sạch hoàn toàn. Không USB/SSH/VM/capture mới, không sửa runtime hoặc toolchain, không stage/commit/push. Branch/HEAD vẫn `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`.

**Hướng tiếp được đề xuất:** Chuẩn bị riêng kế hoạch M tách RSSI khỏi cửa sổ Bulk đầu, kiểm source/fake và giữ lock/heartbeat/deadline; chưa chạy hardware. Không replay B7/040x/double A0, đổi drive hoặc tăng buffer. Giữ Phase 3.1/ROOT_CAUSE_UNRESOLVED; chưa TS_SMOKE_PASS. Báo cáo `bda-vm-offline-comparison.md` và JSON/provenance ở bundle `phase31-bda-vm-offline-20261111-101351-3dad820b` dưới D:\TSDuck.

## 2026-11-11 01:46 — Đã hoàn tất phép quan sát nhiều lớp; host báo lỗi sớm, chưa có TS

**Lượt đo đã thực hiện và dọn sạch, nhưng TS vẫn 0 byte. Windows host báo BABBLE ngay Bulk đầu; guest chỉ nhận cancellation sau timeout.** Đây là bằng chứng mới về đường host/proxy, chưa phải nguyên nhân gốc hoặc fix Android. Giữ Phase 3.1 và ROOT_CAUSE_UNRESOLVED; chưa TS_SMOKE_PASS.

- Run `phase31-layered-20261111-014626-5c93af62`: một firmware bootstrap đúng 132 controls/8192 byte/hash, handoff operational descriptor mới cùng physical epoch và 0 A0. Giữ M late/K36/manual PLP0/xHCI/8×65536; không thay USB payload, driver Android hoặc controller.
- Receiver khóa K36 với status=31, Sony sync=0x26 rồi `receive_timeout`, 0 byte. Host có 36 Bulk completions: một `0xC0000012`/USBD_STATUS_BABBLE_DETECTED, 35 `0xC0010000`/CANCELED, mọi actual=0. Guest có tám submit 65536 byte và tám completion `-2`/actual=0. Không ép IRP host thành URB guest 1:1.
- Lỗi host đầu tại frames 743/744, chỉ 0,258 ms sau submit theo riêng clock Windows; cancellations đến sau khoảng 10 giây. Có 335 EP0 controls thành công sau lỗi đầu. Tổng 706 controls setup/payload khớp từng byte/thứ tự giữa host và guest; terrestrial `0x8A/0A00` thành công. Không gọi BABBLE host là bằng chứng tín hiệu điện hoặc FIFO rỗng.
- Raw xHCI có đủ 8 enqueue/8 giveback, length=65536/actual=0. Field `urb.status=-115` được trace trước USB core đặt completion cuối theo nguồn upstream v6.8; dùng usbmon `-2` cho kết quả guest. Giữ trace.dat/raw/stats và không ghép clock host–guest.
- Các recorder ready, không truncation/lost/drop được báo; hash 74 file guest giữa hai bản đều khớp. Hai phase drain/cleanup true, outstanding=0; filter/usbmon/firmware hardlink/trace instance đã gỡ. Người dùng đã rút tuner, hậu kiểm CLEAN_DISCONNECTED. Không cần cắm lại và không có phép đo tiếp theo đang chạy.

**Kiểm phần mềm cuối:** Guest full Linux fake 58/58 PASS, parent 89 grant/coordinator, 19 process integration, 32 adapter, 61 channel và 17 runtime checks PASS; parser 19 tests PASS. Giữ mọi failure cũ và phân biệt test harness với hardware guard; không nới heartbeat 2 giây. Không chạy lại JVM/Android/Gradle trong lượt tools-only này.

**Bước tiếp được đề xuất:** So offline ranh giới Bulk đầu/pipe/configuration/init với Windows BDA đã nhận TS, không lặp firmware hoặc đổi FIFO/drive/GPIO. Báo cáo `layered-observation-report.md`, JSON review, timelines và raw hashes nằm trong bundle cùng run dưới D:\TSDuck. Branch/HEAD vẫn `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`; không stage/commit/push hoặc sync references.

## 2026-11-11 01:13 — Đã sửa hai gate đúng nguồn; còn chờ full regression

**Đã sửa cách kiểm bộ đệm và nhận diện recorder child, nhưng chưa cho chạy lại hardware.** Linux v6.8 làm tròn request 64 KiB thành 17 trang dữ liệu, nên báo 67 KiB; trace-cmd v3.2 tự thêm bộ lọc loại recorder children. Gate chỉ nhận kích thước tính đúng và PID có đầy đủ bằng chứng ownership, không tăng buffer M hoặc chấp nhận filter tùy ý.

Kiểm mới có 35 portable checks và hai lượt Linux focused 30 checks PASS. Full suite vẫn FAIL/timeout 115 giây, gồm assertion health age 303 ms so với 200 ms và ba fault cases; đang đối chiếu cadence mục tiêu 200 ms với hard freshness 2000 ms và kiểm cleanup riêng, không nới heartbeat 2 giây. Parent có 17 runtime admission/heartbeat/atomic-terminal checks PASS trên Git Bash và Linux fake; kênh có 61 checks ba lượt PASS và 80 PULSE SSH fixture mới PASS, tối đa 303 ms. Giữ nguyên lần fixture deadline failure trước đó.

Tuner vẫn được người dùng giữ rút; không có firmware/receive mới. Kế hoạch nhiều lớp chưa hoàn tất, Phase 3.1/ROOT_CAUSE_UNRESOLVED giữ nguyên. Chỉ source freeze/grant và báo cắm sau khi regression đạt; chưa stage/commit/push hoặc đổi driver/controller.

## 2026-11-11 00:49 — Đã biết gate guest lỗi; giữ rút tuner để sửa đúng contract

**Snapshot mới xác định recorder bị chặn vì bộ đệm thực là 67 KiB, trong khi gate đòi đúng 64 KiB.** Chưa gọi đây là lỗi tuner hoặc fix đã kiểm. Clock thực là `[mono]`; bộ lọc còn có phần loại PID của trace-cmd cần xác minh ownership và ngữ nghĩa trước sửa.

Lượt `phase31-layered-20261111-004925-0d54a524` được duyệt riêng chỉ khởi động recorder. Grant tắt cả firmware/receive; hai runtime seams từ chối, không stage firmware hoặc tạo runtime permit. Raw host có 14 standard controls, 0 vendor/A0/Bulk; snapshot `startup-barrier.txt` được giữ trước cleanup. Cleanup true, người dùng đã rút/ngắt nguồn mới và giữ rút; hậu kiểm không module/filter/usbmon/firmware/permit, M và receiver vẫn đúng hash. Kiểm parent mới nhất là 88 grant/coordinator, 31 adapter và 61 channel checks PASS; guest instrumentation có 34 portable/30 Linux targeted PASS, không thay kết quả full suite cũ thành một PASS mới.

**Bước tiếp:** Kiểm nguồn Linux/trace-cmd và sửa gate đúng cách làm tròn bộ đệm cùng bộ lọc được sở hữu, chạy fixture trước khi arm lại. Không nới timeout hoặc chấp nhận mọi filter. Phép đo firmware/receive nhiều lớp vẫn BLOCKED và Phase 3.1/ROOT_CAUSE_UNRESOLVED giữ nguyên; chỉ báo cắm sau source/grant mới và ARMED.

## 2026-11-10, phiên 19:32 → 2026-11-11 00:28 — Đã triển khai bộ quan sát; đang sửa gate guest trước firmware

**Bộ ghi Windows đã nhận đúng tuner mới, nhưng bộ ghi trong Ubuntu chưa báo sẵn sàng nên lượt đo dừng trước firmware và receive.** Không có kết quả TS mới; giữ Phase 3.1 và ROOT_CAUSE_UNRESOLVED. Người dùng đã rút/ngắt nguồn lại và giữ tuner rút.

- Thêm [bộ quan sát](../../tools/observation/README.md), grant một lần, coordinator STOP/drain, Windows USBPcap, guest usbmon/raw xHCI và wrapper cho đúng M late đã pin. Giữ K36/manual PLP0, xHCI, queue 8×65536 và mọi USB payload cũ; không sửa driver Android trong lượt này.
- Đã có exact consent cho một phép đo, UAC, temporary filter đúng 734c:5530, usbmon và một firmware 132 nếu default. Các lần arm chưa tới firmware được giữ riêng; không rearm ledger. Lỗi UTC parsing, health startup, stdin SSH và frame chia nhỏ đã được sửa bằng tests, không nới heartbeat phần cứng.
- Kiểm mới nhất: 83 grant/coordinator, 19 process integration, 28 adapter, 61 channel và 19 parser checks PASS. Windows worker 192 checks/25 processes và guest Linux 55 checks PASS trước các lượt arm. Kênh SSH thật chạy 80 PULSE fixture PASS, độ trễ tối đa 707 ms; 16 SSH chỉ đọc với stdin EOF PASS. Không coi các kiểm này là tuner PASS.
- Lượt `phase31-layered-20261111-002355-dd8e8d4f` đã ARMED và thấy default descriptor đúng; host pcap có 28 frames/14 controls, không Bulk. Guest dừng OBSERVER_NOT_READY; phase rỗng, chưa firmware/receive. Cleanup xác nhận không còn own modules/filter/usbmon/firmware staging; trace instance đã gỡ. Đang điều tra startup từ raw/log, không thử lại khi chưa sửa nguyên nhân.

**Bước tiếp:** Sửa và kiểm guest readiness bằng fixture, đóng băng source/grant mới rồi mới báo cắm lại. Không snapshot/reset VM, đổi controller/driver, GPIO/B7/04xx/LNB hoặc đọc FX2 chưa chứng minh. Branch/HEAD vẫn `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`; giữ mọi edits có trước, không stage/commit/push hoặc sync references. Bằng chứng nằm trong bundle triển khai `phase31-layered-20261110-193945-623f1cf1` và các run riêng dưới D:\TSDuck.

## 2026-11-10, phiên 18:46 — Đã kiểm khả năng quan sát host; chưa báo cắm tuner

**Chọn hướng quan sát Windows và guest trong cùng một lượt, không thử request đọc FX2 chưa có contract.** Đã rà đủ 11 handler và kiểm môi trường chỉ đọc. Chưa có capture hoặc hardware run mới; giữ Phase 3.1 và ROOT_CAUSE_UNRESOLVED.

- Direct FX2 register read vẫn BLOCKED: A0 có contract RAM nhưng chưa chứng minh đọc peripheral E6xx; B8 tiêu thụ trạng thái IRAM, các đường 90/92/93→91 là I²C/IRAM. Không thêm request hoặc firmware để đoán. Nhánh offline chạy 64 tests PASS.
- VM Ubuntu-24.04 đang chạy với VirtualBox 7.2.22, kernel 6.8.0-149-generic, xHCI giữ nguyên; strict root SSH hoạt động. Không thấy tuner trong VBox usbhost/guest, và người dùng xác nhận đang rút khỏi mọi host. Không start/reset/snapshot/attach hoặc đổi controller.
- USBPcap 1.5.4.0 discovery trực tiếp có 5 interfaces; tcpdump/trace-cmd đã có, usbmon chưa loaded. Raw giveback có status nhưng print format không in field này; kế hoạch phải giữ binary/raw report. Extcap GUI mặc định capture-all=true, nên target filter và coverage qua re-enumeration còn cần kiểm.
- Kế hoạch giữ nguyên M late, chỉ thêm Windows USBPcap, guest usbmon và raw xHCI trace. Policy offline có 36 tests PASS; đây không phải observer coordinator hoặc kiểm end-to-end. Bộ điều phối STOP/watchdog/cap và tests tích hợp còn thiếu, nên trạng thái là PREPARED_NOT_AUTHORIZED_NOT_EXECUTABLE.

**Chuẩn bị của người dùng:** Giữ tuner ngắt kết nối, giữ VM đang chạy, chuẩn bị đúng cáp/nguồn/coax/RF IN đã dùng. Chưa tắt/bật nguồn hoặc cắm theo epoch cũ. Khi bộ điều phối đã kiểm và có exact consent mới, mới xử lý UAC, chọn cổng/hub mới và xác nhận cấp nguồn. Không cần mua analyzer, cài driver hoặc đổi cấu hình lúc này.

Báo cáo `observation-readiness.md`, kế hoạch JSON, policy tests và bảng firmware capabilities nằm trong bundle `phase31-observation-ready-20261110-185020-78ccd455` dưới D:\TSDuck\reports. Giữ branch/HEAD `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`, mọi edits có trước và references chỉ đọc; chưa stage/commit/push.

## 2026-11-10, phiên 18:02 — Đã đối chiếu Windows; chưa có byte output cần đổi

**Bảy giá trị output Windows đã ghi giống với hai snapshot Android. Chưa có cơ sở sửa port bằng cách đổi chúng.** Windows không có readback đủ bảy byte sau lock, nên không được gọi hai host có cùng snapshot vật lý. Giữ Phase 3.1 và ROOT_CAUSE_UNRESOLVED; kết quả Android gần nhất vẫn tám URB -75/actual 0, không có hardware run mới trong lượt này.

- Hash lại cold2 pcap và Android EP0/native/report; giải mã mới bằng TShark 4.6.9 có 6751 controls, không warnings. Bulk Windows đầu frame 1650/1659 có status=0/actual=8192; requested length trong USBPcap vẫn UNKNOWN.
- Các byte `C4/C6/80/81/95/A9/33` đều có last-write `29/01/28/00/15/00/01`. Riêng các lần Windows đọc C6=00, 80=3F, 95=3D và 33=01 nằm trước lần ghi cuối; không so chúng với Android như final-state mismatch. Bảng đầy đủ ở [RESEARCH.md](RESEARCH.md).
- Những điểm còn mở nằm ngoài bằng chứng bảy byte: drive 96/97 chỉ khác writes, C3/D9 chưa readback, actual PLP/L1 chưa biết, và shared board/FIFO/host state chưa được đo. Có 265 vendor request `0x81` trả byte `00` trên Windows, nhưng đây là software bit IRAM `20.7`, không phải cờ FIFO.
- Chỉ phân tích offline, không ADB/USB/capture mới hoặc đổi host. Có 21 decoder + 17 comparison tests PASS, 17 targets và 69 READ/WRITE events có provenance. Không sửa driver/helper, build lại APK, replay GPIO/B7 hoặc tăng buffer/delay. Giữ branch/HEAD `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963` và mọi edits có trước.

**Bước tiếp:** Chỉ lập kế hoạch quan sát FX2/host có nguồn và consent mới trước hardware. Nếu chưa có cách đọc được chứng minh, giữ phần đó BLOCKED; không tạo lệnh A0/GPIO để lấp chỗ trống. Báo cáo `windows-sony-comparison-result.md`, JSON và CSV nằm trong bundle `phase31-windows-sony-20261110-180339-9e246085` dưới D:\TSDuck\reports. Không stage/commit/push hoặc sync references.

## 2026-11-10, phiên 16:10 — Phase 3.1: đã đọc Sony output thật, vẫn chưa có TS

**Bảy byte cấu hình Sony đã đọc khớp các giá trị init đã ghi, nhưng Android vẫn nhận 0 byte.** Đây là bằng chứng mới về cấu hình, không phải bằng chứng tín hiệu đã ra chân chip hoặc tới FIFO. Branch/HEAD giữ `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`; giữ edits có trước, không stage/commit/push hoặc sửa references.

- Thêm [Sony readback debug](../../README.md#chẩn-đoán-android-đọc-lại-cấu-hình-đầu-ra-ts-của-sony), mặc định TẮT, permit riêng, cùng owner/worker và không có URB pending. Hai snapshot `AFTER_LOCK` và `BEFORE_FIRST_URB` đều đủ 20/20 EP0, restore bank20 thành công, mất khoảng 619,174 ms và 587,510 ms. Không đổi output/drive/PLP; alt0 TẮT, 0 API calls.
- Cả hai lần: bank00 `C4=29,C6=01,80=28,81=00,95=15,A9=00,33=01`; sync bank20/10 trước/sau đều `26`. Tổng 230 EP0; bỏ đúng 40 monitor controls còn 190 giao dịch giống byte-exact cả IN/OUT với baseline. Chưa đọc C3/96/97/D9, actual PLP hoặc L1.
- Native vẫn 8 submit/8 REAP, tất cả `-75/actual 0`, syscall return/errno=0, sample 0 byte. Report terminal `REPORT/USB_RECEIVE_FAILURE`, drain=8/8, outstanding=0/quarantine=false. Thu/hash 16 raw files Android-host khớp; app không còn USB fd sau drain và đã rời workflow. Không thử thêm hardware.
- Người dùng duyệt đúng một chương trình monitor và xác nhận cấp nguồn mới. Firmware chỉ một lần đủ 132 giao dịch trong 2834 ms; bootstrap dừng ở cửa sổ permission sau re-enumeration nhưng đã drain. Handoff operational không FirmwarePath/A0, giữ nguyên physical epoch. Các preflight chưa grant không ghi USB; đã sửa routing preflight bằng CLEAR_TOP + SINGLE_TOP, không CLEAR_TASK hoặc nới guard.
- Kiểm phần mềm: 578 JVM tests, debug/release/lint và AndroidTest build PASS; 19 approval + 5 lifecycle fake Android PASS khi tablet thức. Giữ 3 lifecycle failures trước đó khi Dozing/FOREGROUND_LOST. Helper Sony scope 8/8, 57 kiểm launch và 82 kiểm heartbeat PASS trên PS7/5.1; 3 release jars không có monitor debug.

**Giới hạn và bước tiếp:** Snapshot không đồng thời, bank là `PLAN_INFERRED`; đọc 81 có nguồn ở nhánh serial nhưng chưa có chứng nhận an toàn trên parallel. Giữ Phase 3.1, `ROOT_CAUSE_UNRESOLVED`, chưa `TS_SMOKE_PASS`. Bước an toàn tiếp theo là đối chiếu các giá trị này với trace Windows hiện có để khoanh ranh giới TS pin/clock → FIFO FX2; chưa ghi drive/GPIO, tăng buffer hoặc chuyển host. Bằng chứng và nguồn ở [RESEARCH.md](RESEARCH.md), [TEST-MATRIX.md](TEST-MATRIX.md), bundle `phase31-sony-output-20261110-161951-5b3ce827` dưới D:\TSDuck.

## 2026-11-10, phiên 12:09 — Phase 3.1: đã sửa lifecycle và kiểm đường cấp grant trên Android

**Đường khởi chạy/grant/heartbeat/cleanup đã chạy được trên tablet mà không truy cập tuner.** Đây là bản sửa bộ điều phối, chưa phải bằng chứng nhận TS hoặc nguyên nhân gốc của lỗi USB. Branch/HEAD giữ `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`; giữ toàn bộ thay đổi có trước, không stage/commit/push hoặc sửa references.

- Thêm `launch_token` để helper chỉ nhận challenge của đúng lần mở mới. Activity xử lý launch thường sau khi cleanup phiên trước đã được chứng minh; callback cũ bị chặn theo generation. Số byte bằng 0 không thay được chứng cứ cleanup. Trường hợp chưa cấp bất kỳ tài nguyên nào được ghi rõ `cleanup_path=NO_RESOURCES_ACQUIRED`; phiên đã bắt đầu acquisition vẫn phải drain/runtime termination.
- Thêm `-ControlPlaneOnly`, ràng bất biến vào challenge/grant và cấm USB manager/runtime, permission request, device open, firmware/I²C/Bulk/setInterface. Đã kiểm thực helper→Android: hai phiên exit 0/CONTROL_PLANE_COMPLETE, grant được nhận, STOP và cleanup thành công; hardware history SHA không đổi, app không giữ USB fd. Một lần no-I/O bị guard 1500 ms dừng được giữ nguyên là FAIL có giới hạn, không nới timeout.
- Sửa routing bằng CLEAR_TOP + SINGLE_TOP để tới đúng Activity qua màn hình permission; không CLEAR_TASK, force-stop hoặc clear data. Kiểm mới: JVM 565/565, debug/release/lint PASS; 5 lifecycle Android và 16 approval tests PASS; fake launch/terminal 57 và heartbeat 82 checks PASS. Lint 0 errors, debug 18/release 10 warnings như baseline. Helper nhận terminal đã drain theo trường tường minh, không bỏ qua bootstrap dừng ở WAIT_REENUMERATION.
- **B thật đã chạy tới API:** người dùng duyệt attachment mới, giữ cùng tablet/K36 AUTO/native8×4096/cáp/nguồn/RF IN. Bootstrap full 132 trong 3230 ms, handoff không A0. `setInterface(alt0)` gọi đúng một lần, trả true trong khoảng 0,958 ms; raw descriptor/config/interface/endpoint và outstanding=0 không đổi trước/sau. Đây là app-level evidence, không phải packet trace trên dây.
- **Kết quả B:** 190 EP0 full-length giống byte-exact toàn bộ baseline A; tám submit/tám REAP đều -75/actual 0, sample TS 0 byte. Report terminal REPORT/USB_RECEIVE_FAILURE, DRAIN_CONFIRMED, outstanding=0/quarantine=false. Mười raw files đã thu/hash Android-host khớp; đã rời workflow sau drain, không chạy thêm. APK A/B khác nhau vì sửa lifecycle và dùng physical epochs khác nhau, không gọi đây là A/B hoàn hảo.

Hướng dẫn mới ở [README](../../README.md#kiểm-việc-khởi-chạy-và-heartbeat-mà-không-truy-cập-tuner). Báo cáo và raw mới trong bundle `phase31-lifecycle-20261110-121354-96284261` dưới D:\TSDuck. **Explicit setInterface đơn lẻ không đủ sửa EOVERFLOW trong điều kiện đã thử**; không loại mọi lỗi USB host hoặc kết luận bo/FIFO hỏng. Vẫn Phase 3.1, ROOT_CAUSE_UNRESOLVED và chưa TS_SMOKE_PASS. Bước có giá trị tiếp theo là lập phép đọc trạng thái Sony output có nguồn và quyền riêng; không lặp buffer/altsetting, reset hoặc replay GPIO.

## 2026-11-10 — Phase 3.1: đã kiểm TS_LOCK từ raw, thêm đối chứng Android alt0 có guard

**Chưa có TS trên Android; nguyên nhân gốc vẫn chưa xác nhận.** Tiếp tục trên `chore/modernize-android-build`, HEAD `b73f11097e594acd2abe1769a65ed03216c8e963`, tree sạch đầu phiên. Không stage/commit/push, đổi branch hoặc sửa references.

- **Phát hiện mới từ raw đã hash lại:** Windows và Linux M late/early đều trả Sony bank20/10=26 trước Bulk; mỗi M còn có chín readback 26 trong lúc pending. Đây là sync=6/TS_LOCK=1/unlock=0, không chỉ nhãn FE_HAS_LOCK. Chưa chứng minh TS ra chân chip hoặc vào FIFO FX2. Windows manual PLP0 giống M; actual selected PLP/L1-post vẫn NOT_AVAILABLE. Android đã ghi bank9C/10 giống Windows mà vẫn -75, nên 9C không phải lời giải chung.
- **Mã Android:** thêm `EXPLICIT_SET_INTERFACE_ALT0`, chỉ debug, mặc định OFF, exact operational/High-Speed/owner/idle/K36 AUTO/native8×4096 guards và grant/plan riêng. API false/exception/disconnect dừng trước Bulk, không retry/reset/force claim hoặc upload lại. Release giữ nguyên; lớp diagnostic không có trong release jar. Xem [hướng dẫn](../../README.md#chẩn-đoán-android-chọn-lại-interface-0alt-0).
- **Lượt A mới trên tablet:** bootstrap full 132, operational không A0, K36 khóa, 190 EP0; tám REAP -75/actual 0, sample 0 byte, outstanding=0/quarantine=false và DRAIN_CONFIRMED. **B đầu chưa đo setInterface:** bootstrap full 132 nhưng handoff dừng bởi HOST_HEARTBEAT_INVALID trong frontend init sau 150 EP0, trước API/native; không retry attachment đã tiêu thụ.
- **Kiểm thử mới:** focused JVM 60/60, regression 559/559, debug/release/lint và hai APK instrumentation build PASS; tablet fake 4+16 tests PASS. [Decoder và pipeline giả](../../tools/ts-state/README.md) có 21 tests/16 checks; callback M 820, receiver 80 và FX2 64 tests PASS. Không coi các test này là TS_SMOKE_PASS.
- **Môi trường/quyền:** đầu phiên VM tắt/SSH bị từ chối; người dùng tự bật lại và strict root SSH đã kiểm thành công trên Ubuntu 24.04.5/kernel6.8.0-149, xHCI giữ nguyên. Người dùng duyệt một cặp Android A/B, xác nhận fresh attachments và OS permission; không có quyền GPIO/controller/LNB/DC mới. Các lượt đã drain; tuner giữ ở tablet, ledgers không xóa/rearm.

- **Điểm dừng cuối:** người dùng duyệt một B thay thế. Helper một-call đầu vướng `run-as` không được đọc `/proc/uptime`, dừng trước publish grant; đã sửa đọc clock ở ADB shell UID rồi truyền vào app UID, 82 fake checks PASS trên PS7/PS5.1. Sau đó challenge cũ bị từ chối. Report workflow chưa được grant là FOREGROUND_LOST/DRAIN_NOT_CERTIFIED, dù EP0=0 và chưa có firmware/native; vì vậy không coi là drain PASS. Guard dừng trước mọi task clear hoặc lượt B2. Không force-stop, reset, rearm hoặc chạy thêm. **setInterface trên hardware vẫn NOT RUN.**

Báo cáo: `D:\TSDuck\reports\phase31-state-20261110-100952-10a3635f\phase31-ts-state-differential-result.md`, cùng first-divergence, Sony matrix và provenance CSV. Bước tiếp theo chỉ đọc/lập bằng chứng về vòng đời workflow cũ và cleanup trước khi xin phép bất kỳ thử nghiệm mới; không thao tác tuner khi drain chưa được chứng nhận. Vẫn Phase 3.1, **ROOT_CAUSE_UNRESOLVED**, chưa Android TS_SMOKE_PASS.

## 2026-11-09 → 2026-11-10 — Phase 3.1: đối chứng Linux gần nguyên gốc và timing sớm vẫn chưa có TS

**Chưa xác nhận nguyên nhân gốc; Android chưa đạt TS_SMOKE_PASS.** Git trước/sau vẫn `chore/modernize-android-build` / `cbb2aa6892ede102d7b122badec848ae7c483b14`, sạch đầu phiên. Không stage/commit/push, đổi branch hoặc sửa references.

- **Original community:** đã build mới đủ 5 module trên Ubuntu `6.8.0-149-generic`, source hash giữ nguyên và 226 symbol CRC khớp. Không load nguyên trạng vì probe còn chạy firmware/reset vệ tinh, B7 và 8A0801. Official TBS cần cả vendor media framework; chưa build/load closure đầy đủ trên guest stock, không coi board-only `.ko` là đủ.
- **Sửa hai nhận định về source:** community gốc đã dùng 8×65536; bank9C/10 đã bị comment trong Sony gốc, không do diagnostic D tự đổi. M mới giữ terrestrial/demux/successful resubmit nhưng thêm giới hạn và bỏ đường vệ tinh; không được gọi M là original. Chi tiết trong [công cụ đối chứng](../../tools/linux-original/README.md).
- **Linux thật:** người dùng duyệt cold M rồi handoff không firmware, sau đó một timing experiment trên attachment mới. Cả hai bootstrap có đúng 132 A0 full-length, tái tạo 8192 byte từ pcap khớp hash đã pin; thiết bị tự re-enumerate default→operational. Guard chặn dùng endpoint cache default, không reset hoặc nạp firmware lần hai. VM hiện là xHCI, không phải EHCI như D lịch sử; phiên này không đổi controller.
- **Hai phép nhận M:** K36/8 MHz/manual PLP0 khóa status 31. Timing mặc định gửi 8A0A00 sau lock; timing riêng gửi đúng một lệnh trước init. Cả hai có tám URB requested 65536 chờ khoảng 10 giây rồi tám cancel -2/actual 0; submitted/reaped 8/8, outstanding 0, USB/DVR 0 byte. -110 là deadline policy, không HCD completion. Timing sớm không đủ sửa trong cấu hình đã đo; không gọi khác biệt với D hoặc Android là causal proof.
- **Validation mới:** JVM 530/530, debug/release/lint; native GCC và MSVC ASan 4096/8192; NDK syntax bốn ABI; normalizer 77/77 mỗi PS7/PS5.1 PASS. Thêm 820 extracted checks, receiver 80 mocked flows trên Windows/Linux, M 127 CRC, tests runner 10 và CRC parser 10 PASS. GCC ASan không link vì thiếu libasan, không cài thêm. Android live/instrumentation mới NOT RUN; chưa có causal fix để port.
- **Cleanup đã kiểm:** tuner trả Windows/PnP OK/BDA và usbipd Shared; guest không còn modules phiên tạo/usbmon/filter/firmware tạm. Giữ consumed ledgers, không snapshot, kernel/driver Windows/LNB/DC hoặc GPIO mới.

Báo cáo chính: `D:\TSDuck\reports\phase31-original-20261109-234104-c6017f83\closeout.md`; bundle timing `phase31-original-20261110-004247-09840fb6`. Raw/modules/firmware/grants ở ngoài Git. Bước tiếp theo có giới hạn là đối chiếu selected-PLP/L1 và TS output/FIFO với Windows trước khi chọn phép đọc hoặc Android SET_INTERFACE mặc định OFF có grant mới; không chạy lại buffer/credit/timing scan. Trạng thái: **ROOT_CAUSE_UNRESOLVED_WITH_NARROWED_SCOPE**, vẫn Phase 3.1.

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
