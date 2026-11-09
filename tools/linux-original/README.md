# Đối chứng Linux community nguyên trạng và bản giới hạn tối thiểu

Đây vẫn là **Phase 3.1**. Công cụ này không cài driver, không tự xin quyền phần cứng và không chứng nhận Android nhận được MPEG-TS. References chỉ được đọc; source được copy vào scratch mới dưới `D:\TSDuck`.

## Ba loại cần phân biệt

| Loại | Ý nghĩa | Trạng thái ngày 09–10-11-2026 |
| --- | --- | --- |
| O-C | Community `koreapyj/cxd28xx` nguyên trạng, SHA `c5fa27eecbe80633cbb365cc44656daea1c0e2e3` | Build mới đủ 5 module trên Ubuntu `6.8.0-149-generic`; không load vì probe chạy cả vệ tinh/B7/8A0801 |
| O-T | Official TBS cùng DVB-USB/media framework của vendor | Chưa build/load closure đầy đủ trên guest stock; không coi board-only `.ko` là đủ ABI |
| M | Bản giữ nguồn terrestrial nhưng thêm lớp giới hạn và bỏ probe vệ tinh | Đã bootstrap cold một lần rồi handoff operational không A0; K36 khóa nhưng USB/DVR 0 byte |
| D | Diagnostic cũ trong [linux](../linux/README.md) và [linux-runtime](../linux-runtime/README.md) | Chỉ dùng trace lịch sử EHCI/warm; không chạy lại trong phiên này |

Header community gốc đã định nghĩa **8 URB × 65536 byte**, không phải patch diagnostic tăng từ 4096 lên 65536. Official TBS/CoreELEC mới là nguồn có 8×4096. Khối Sony bank `9C/10` đã bị comment trong source community gốc; không quy việc thiếu nó cho patch D.

## Phạm vi patch M

[Manifest](minimal-source-manifest.json) pin toàn bộ input/output SHA-256. Git giữ nguyên bytes của các file patch, kể cả CRLF trong phần ngữ cảnh nguồn, để không làm sai hash đã pin khi commit hoặc checkout. [Patch terrestrial](minimal-terrestrial.patch) bỏ frontend vệ tinh, các side effect B7/8A0801 và ALP network consumer; giữ cấu hình Sony, terrestrial init/sleep, feed callbacks, demux, stream `8A0A00`, queue và successful resubmit của upstream, kể cả hoàn tất zero. Sai khác khi lỗi được ghi rõ: chặn vendor/I2C ngoài allowlist, short/error fail-stop, heartbeat 2 giây, session 120 giây, receive 10 giây/16 MiB, tối đa 4096 submit, một probe và drain trước free.

[Patch khóa Sony](sony-error-locks.patch) chỉ sửa bảy return lỗi trong init để đi qua nhả mutex, một exit unsupported và nhánh cold-init tự khóa mutex. Không đổi register/payload trên nhánh thành công. Đây là `SAFETY_GUARD`, không phải portability-only; vì có thay đổi hành vi khi lỗi nên M **không tương đương 100% original**. Không cần patch tương thích API để community build trên guest đã kiểm.

## Chuẩn bị và kiểm offline

```powershell
.\tools\linux-original\Prepare-MinimalSource.ps1 -OutputPath 'D:\TSDuck\logs\<new-run>\minimal' -WhatIf
.\tools\linux-original\Prepare-MinimalSource.ps1 -OutputPath 'D:\TSDuck\logs\<new-run>\minimal'
node .\tools\linux-original\Test-MinimalSource.cjs '<original-scratch>' '<minimal-scratch>' '<new-test-output>' '<verified-gcc.exe>'
.\tools\linux-original\tests\Test-ReceiveOriginalK36.ps1
```

Prepare chỉ copy/hash/apply patch, không SSH/build/load. Test callback được trích từ source thật: tái hiện original resubmit sau zero/lỗi âm và kiểm M dừng ở lỗi/cap/deadline. Receiver kiểm adapter, VID:PID, canonical sysfs USB parent, cùng parent của frontend/demux/DVR và tên frontend chính xác trước khi mở đường ghi. Profile chỉ DVB-T2/K36/8 MHz/manual PLP0; full TS PID `0x2000`, `DMX_OUT_TS_TAP`, O_EXCL và STOP/close/fsync có giới hạn. Số byte không tự là TS PASS.

Trên Ubuntu có compiler/headers sẵn, chỉ build trong scratch, không `make install`, DKMS hoặc `depmod`. [Test receiver POSIX](tests/Test-ReceiveOriginalK36.sh) build production nhưng chỉ chạy luồng giả lập. [CRC reader](Check-UbuntuModuleCrc.cjs) nhận `readelf -x __versions` và hai `Module.symvers`; nó dành riêng cho layout đã xác minh từ header guest: `u32 next`, `u32 crc`, tên có padding và NULL sentinel. Không dùng nó cho mọi kernel. Kmod 31 tại guest báo `Invalid argument` khi dump layout này, không đồng nghĩa ABI module hỏng.

## Hai cửa firmware và operational

Cold `FX2_DEFAULT` có descriptor 189 byte, SHA `e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`, alt0 không endpoint. Không yêu cầu EP82 trước A0 và không submit URB trên cache default. Chỉ mode cold được gọi loader ảnh raw 8192 byte/hash đã pin và đúng 132 transfers; sau full-length, nếu cache chưa EP82 thì dừng probe. Không tự reset hoặc upload lại.

Trong lượt thật thiết bị tự re-enumerate device2→3, có operational descriptor 50 byte/hash `22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5`. Handoff cần grant riêng, receipt full132 và cùng physical epoch; mode `operational_handoff=1,allow_cold_firmware=0` không gọi `request_firmware` hoặc A0. File firmware tạm đã xóa trước handoff. Hai ledger entries riêng giữ nguyên, không rearm cold attempt.

## Đối chứng timing riêng, không phải original

[Patch timing](early-stream-timing.patch) tùy chọn chuyển đúng một `8A0A00` từ start-feed sau lock sang trước terrestrial init; không đổi payload, Sony/PLP/drive/queue hoặc demux. [Manifest timing](early-timing-manifest.json) pin output hai file thay đổi. Prepare mặc định không áp dụng; cần `-EarlyStreamTiming`, kiểm bằng [test trích nguồn](Test-EarlyStream.cjs), grant mới và host flag `-Mode handoff -StreamTiming early`. Không bật flag timing ở bootstrap.

Người dùng đã duyệt một fresh physical epoch mới cho timing. Bundle `phase31-original-20261110-004247-09840fb6` ghi full132/hash đúng, natural device4→5, `8A0A00` duy nhất tại frame 408 trước init, sau đó K36/status31. Tám URB vẫn pending 10 giây rồi cancel `-2/actual0`; sample 0 byte. Đổi riêng timing sớm không đủ sửa việc nhận TS trong cấu hình M/xHCI đã đo. Hai attachment vật lý khác nhau, không xem đây là phép kiểm soát hoàn hảo mọi trạng thái của bo. Không mở rộng sang B7/GPIO/drive hoặc chạy lại cùng ledger.

## Live runner không phải lệnh chạy tự do

[Host runner](Invoke-MinimalRun.ps1) và [guest runner](GuestMinimalRun.sh) đòi receipt physical/grant, source/module hash, exact sysfs path và namespace chưa dùng. Dùng `-WhatIf` để kiểm kế hoạch; chỉ bỏ WhatIf sau khi đã có quyền tương ứng. Host heartbeat dùng SSH pinned, có keepalive/deadline; kernel tự dừng khi heartbeat cũ. Guest trace phải được arm và kiểm PID/file trước probe. Không tự arm capture, đổi USB owner/controller hoặc tạo firmware hệ thống từ runner.

Chỉ load module từ scratch theo thứ tự đã kiểm. Cleanup yêu cầu `streaming=0,outstanding=0`, sau đó unload đúng module phiên tạo; nếu không xác nhận drain thì giữ quarantine, không force-free, detach hoặc reset. Filter/firmware tạm chỉ được gỡ theo grant, đúng identity/hash. Mọi snapshot, LNB/DC, B7, 8A04xx, thay driver Windows và thay kernel vẫn bị cấm trong scope này.

## Kết quả và giới hạn

Bundle `D:\TSDuck\reports\phase31-original-20261109-234104-c6017f83`: bootstrap full132/hash PASS; operational K36/status31 PASS; 8×65536 pending khoảng 10 giây rồi tám cancel `-2/actual0`, không successful Bulk completion, DVR 0 byte. Terminal `-110` là deadline của M, không HCD báo lỗi. VM thực tế là xHCI, không đổi controller; D lịch sử EHCI/warm nên không coi đây là A/B một biến.

Regression mới: JVM 530, debug/release/lint; native GCC và MSVC ASan 4096/8192; NDK syntax bốn ABI; normalizer 77/77 mỗi PS7/PS5.1; callback 820 và receiver 80 luồng trên Windows/Linux PASS có phạm vi. GCC ASan không link được vì thiếu `libasan` trong MinGW đã cài; không cài thêm. Android live, instrumentation mới và TS parser trên sample rỗng là NOT RUN. Chưa có causal fix để port; `ROOT_CAUSE_UNRESOLVED_WITH_NARROWED_SCOPE`, không TS_SMOKE_PASS.
