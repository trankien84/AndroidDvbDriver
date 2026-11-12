# Công cụ chẩn đoán chỉ dùng offline

## Lập đề xuất bảo toàn lịch sử đã consume

[history_recovery.cjs](history_recovery.cjs) kiểm bản ledger cùng receipt, report và 132 record A0 từ các tệp local đã khóa SHA-256. Công cụ chỉ tạo **đề xuất bổ sung khóa chặn** cho các epoch đã nạp firmware được đối chiếu bằng chứng. Nó không kết nối ADB/USB, không ghi app và không cấp quyền chạy tuner.

```powershell
node --test '.\tools\diagnostics\history_recovery.test.cjs'
node '.\tools\diagnostics\history_recovery.cjs' --input 'D:\TSDuck\logs\<run-id>\recovery-input-private.json' --output-directory 'D:\TSDuck\logs\<run-id>\deny-only-candidate'
```

Input schema `TBS5530-HISTORY-RECOVERY-INPUT/v1` gồm `namespace`, `baseline:{file,sha256}` và `observations`. Mỗi observation có `label`, `epochField`, các pointer `{file,sha256}` tên `receipt`, `report`, `controls`, cùng `attribution` nếu đã có hash anchor. Namespace chỉ nhận Samsung `autonomous-v1-samsung-sm-x926b-api36`; không tự phát hiện host hoặc chấp nhận schema mới. Epoch phải khớp receipt và toàn bộ 132 controls; report phải xác nhận host, epoch binding, full native boundaries và cleanup. `attribution` là mô tả nguồn được cung cấp, không là chữ ký hoặc xác thực nguồn độc lập.

Đầu ra `candidate-not-applied.txt` giữ nguyên từng byte của ledger gốc rồi chỉ thêm `FIRMWARE_CONSUMED <epochKey>` chưa có. `proposal.json` không công bố raw epochs/keys, luôn ghi `fullHistoricalContinuityProven=false`, `appWriteAuthorized=false`, `hardwareAuthorized=false`, `liveGateBlocked=true`. Công cụ không dựng attachmentKey thiếu hoặc tuyên bố phục hồi đầy đủ. Chỉ thêm khóa cấm không tự giải quyết mọi chênh lệch lịch sử.

Input phải là tệp thường local, có kích thước hữu hạn và hash không đổi trước/sau phân tích. Ledger rỗng, trùng, sai UTF-8, BOM, CRLF hoặc thiếu newline bị từ chối theo định dạng store hiện tại. Output phải là thư mục mới dưới `D:\TSDuck`, không ghi đè hoặc tái sử dụng thư mục dở dang. Giữ manifest và candidate riêng ngoài Git; thư mục input/output phải được tin cậy, không bị thay đồng thời. Exit `0` chỉ là tạo đề xuất thành công; exit `2` là bị từ chối, không tự retry/restore. Áp dụng lên app cần kế hoạch và phê duyệt riêng, không có subcommand apply.

## Recovery core, coordinator debug và chốt hash Android

Sau quyết định thiết kế ngày 11/11, source debug có `Tbs5530HistoryRecovery` và `Tbs5530HistoryFence`. Core chỉ nhận exact baseline/candidate hashes, prefix nguyên vẹn và đúng hai denial records; cần quyền hiện hành và consume riêng dùng một lần trước append. Lỗi sau khi vào đường ghi được phân loại `COMMIT_UNKNOWN`, không retry hoặc rollback tự động.

Adapter package-private `Tbs5530HistoryRecoveryStore` dùng reservation hiện có, chỉ Samsung/không tuner/marker đã khởi tạo/không `.bak` hoặc `.new`; đọc và đối chiếu hash trước ghi, mở inode có sẵn bằng `O_APPEND|O_NOFOLLOW` không `O_CREAT`, ghi suffix và sync. Coordinator debug `Tbs5530HistoryRecoveryActivity` hiện có exact immutable grant, UI STOP/foreground, session 120 giây/heartbeat 2 giây và no-backup journal tạo độc quyền trước append. Chỉ nhận đúng baseline/candidate/plan đã pin; journal có sẵn hoặc rỗng cũng chặn, không tự retry/rollback/rearm. Activity được export chỉ ở debug để host launch, không coi launch intent là quyền ghi.

[Invoke-TBS5530-HistoryRecovery.ps1](../Invoke-TBS5530-HistoryRecovery.ps1) chỉ chạy sau phê duyệt exact APK/helper/plan, đúng Samsung/boot/no-tuner và baseline hash. Host lưu grant bền, pin launch token mới, truyền clock do shell UID đọc vào run-as (app UID không có quyền đọc uptime trên Samsung), rồi verify terminal/history hash sau append. Không có uninstall/clear/firmware hoặc quyền B1. Bản plan 7→9 dòng đã được user duyệt và apply thật trong lượt 11/11; journal đã consumed, không chạy lại ví dụ apply hoặc xóa journal để thử. Full history trước witness vẫn UNKNOWN.

Đường `MANUAL_PLP0_K36` nay yêu cầu `-ExpectedAttachmentHistorySha256` từ witness ngoài app. App ghi hash snapshot vào challenge/grant, kiểm store trong session và chỉ tăng fence sau consume append-only do chính nó hoàn thành. Helper so hash thực trước grant và sau drain, lưu before/after hashes ngoài app, không tự dùng hash cuối làm quyền run kế tiếp. AUTO/legacy path không tự nhận guard mới hoặc tạo store cho recovery. Đây là chốt phát hiện rollback so witness được duyệt, không proof toàn bộ lịch sử trước witness.

APK/helper build sau thay đổi guard khác hashes trong card B1 cũ. Mọi cài mới hoặc run B1 cần freeze/duyệt lại và xác minh physical scope. Sau recovery, user đã duyệt một B1 MANUAL0 thật: khóa K36 nhưng 8 URB vẫn `-75`/actual0, không TS; cleanup đã xác nhận. Không tự chạy lại hoặc đổi biến từ quyền đã dùng. Tests phần mềm dùng store giả/fixture riêng; chỉ lần apply được duyệt riêng mới thao tác consumed store thật, không dùng instrumentation mặc định tự gỡ app trên tablet.

## B2 reload sau SET_CONFIGURATION — chỉ triển khai debug, chưa chạy tuner

`B2_RELOAD_AFTER_SET_CONFIG` giữ nguyên toàn bộ golden `MANUAL_PLP0_K36`, native 8×4096 và vị trí `8A 0A 00`; chỉ thêm một lượt 132 A0 sau OS permission/claim, trước frontend. Helper `Invoke-TBS5530-AutonomousSession.ps1` yêu cầu APK/helper/frontend/plan/firmware/history/reload-store/anchor hash ngoài app, Samsung SM-X926B/API36 và physical epoch cố định. Không có grant kiểu operational-only hoặc tin cờ host báo bootstrap thành công: app cần private clean proof của bootstrap original đủ 132/132, API boundaries, lease đã drain và cùng physical epoch. Bootstrap giữ nguyên `UsbDeviceConnection.controlTransfer`; telemetry cũ là `ANDROID_APP_OBSERVED`, không phải syscall proof. Đường chuyển bootstrap cũ có thể vẫn fail closed; không nới kiểm attachment để đi tiếp.

Store riêng `no_backup/fx2-reload-v1.txt` bắt đầu bằng header không rỗng, anchor riêng ghi hash mỗi prefix và marker `fx2-reload-v1.initialized` được tạo độc quyền trước cả hai tệp, mỗi epoch chỉ nối một `FX2_RELOAD_CONSUMED <epochKey>` và fsync trước A0 đầu. Không sửa parser/history `FIRMWARE_CONSUMED`. Missing/corrupt/partial append/hash thay đổi/rollback đều chặn và latch; anchor không chống được người có quyền thay cả hai tệp về cùng bản cũ nếu host cũng chấp nhận hash cũ. Vì vậy card phải pin witness mới nhất độc lập, không tự lấy hash hiện tại làm phê duyệt.

Caller riêng `Tbs5530ReloadInitActivity` chỉ export trong debug, không có USB attach filter hoặc đường mở tuner. Launch chỉ tạo challenge; phải nhận immutable grant pin APK/helper/init-plan/header/anchor/marker/history, xác minh Samsung/API36/boot, no-tuner và foreground/heartbeat 2 giây. `Tbs5530ReloadInitPermit` buộc phê duyệt first-initialization riêng, `hardware_authorized=false`, `retry=0`; journal O_EXCL/fsync `fx2-reload-v1-init.consumed.json` phải bền trước mọi target create. Adapter package-private kiểm reservation/no-tuner/history giữa các bước rồi tạo marker/journal/anchor bằng O_EXCL/fsync. Tombstone có sẵn dù rỗng, tệp dở hoặc mất marker đều chặn, không reset/rearm. Template chỉ nhận witness lịch sử `b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb`/996 byte/12 records; không phải Samsung đã đọc mới. Chưa init thật và chưa có quyền chạy tablet.

`Initialize-TBS5530-ReloadStore.ps1 -OfflinePreflight` chỉ kiểm template và đối chiếu init plan/helper hash trên máy local, không tìm ADB hoặc chạm thiết bị. Đường apply phải có `-ApproveExactFirstInitialization -ProofOfGuardPassed`, serial/boot/APK/helper/plan đã duyệt, USB inventory rỗng, history đúng và cả ba target/tombstone chưa có. Host lưu exact grant bền trước publish, giữ heartbeat, kiểm terminal/hash sau create; không gửi shell ghi trực tiếp `no_backup`, không cài app hoặc tự chạy B2. Kiểm offline bằng `Test-TBS5530-ReloadInitializer.ps1 -TemplateDirectory <b2-plans> -PlanPath <b2-initialization-plan.txt>`; mọi apply trên máy thật cần card riêng.

Chỉ reload dùng native debug JNI: capability được tạo từ image 8192 byte đã kiểm SHA, tự suy ra setup/payload theo ordinal 1–132 và chỉ gọi `USBDEVFS_CONTROL` trên fd của claimed owner; không có API A0 tùy ý. Boundary ghi thời gian `CLOCK_BOOTTIME`, return/errno thật, tách khỏi mirror quan sát ở tầng app. Mirror BOOTSTRAP/RELOAD bỏ payload A0, giữ SHA payload. Sau release cuối giữ delay nguồn 100 ms, rồi quan sát thêm 1500 ms, nên frontend không bắt đầu sớm hơn 1600 ms sau syscall completion; không gửi B7/GET_DESCRIPTOR chủ động. Detach trong transfer là `UNCERTAIN`; detach/re-enumeration trong cửa sổ chờ là `RELOAD_CAUSED_REENUMERATION`, dừng trước frontend, không retry hoặc release CPU bổ sung.

Kiểm phần mềm không chứng minh TS hoặc cho phép chạy thật. Cài APK, init store, hardware và card D vẫn là cổng phê duyệt riêng; không dùng quyền B1 cũ. Emulator do coordinator chạy riêng, tuyệt đối không dùng physical serial mặc định.

## Mẫu Bulk FX2 chỉ dùng kiểm thử offline

Công cụ tự tạo và kiểm tra dữ liệu giả có nhãn `SYNTHETIC_TEST_ONLY`. Nó chỉ đọc/ghi tệp cục bộ, không giao tiếp USB, không tạo firmware và không tải firmware lên thiết bị. Kết quả hợp lệ không phải `TS_SMOKE_PASS` hoặc bằng chứng Android đã nhận dữ liệu.

## Chạy từ gốc repository

Dùng Python đã có; không cần thư viện ngoài. Ví dụ môi trường dự án hiện có `C:\Python312\python.exe`; không coi đường dẫn này là yêu cầu cài đặt mới.

```powershell
& 'C:\Python312\python.exe' -B -m unittest discover -s '.\tools\diagnostics' -p 'test_fx2_pattern.py' -v
& 'C:\Python312\python.exe' -B '.\tools\diagnostics\fx2_pattern.py' generate --output '<new-output-dir>\sample.synthetic-pattern' --packets 8 --seed 17
& 'C:\Python312\python.exe' -B '.\tools\diagnostics\fx2_pattern.py' verify --input '<new-output-dir>\sample.synthetic-pattern' --expected-packets 8 --seed 17
```

Thư mục đầu ra phải có sẵn. Generator dùng chế độ tạo độc quyền `xb`, không ghi đè; phần mở rộng bắt buộc là `.synthetic-pattern` để tránh nhầm với firmware hoặc TS. Giữ fixture và logs ngoài Git, dưới `D:\TSDuck` theo quy ước dự án. Nếu ghi lỗi, tệp chưa hoàn tất được giữ nguyên làm bằng chứng và verifier sẽ từ chối; không tự sửa hoặc nối thêm vào tệp đó.

Verifier yêu cầu số packet và seed kỳ vọng từ kế hoạch độc lập, không tự tin metadata bên trong tệp. Chỉ nhận tệp thường, không symlink hoặc đường dẫn thiết bị; không tìm lại marker để bỏ qua dữ liệu lỗi. Nó đọc từng khối tối đa 512 byte, chịu được một packet bị chia thành nhiều lần đọc; file phải kết thúc đúng số packet. Thư mục chứa input phải được tin cậy và không bị thay thế đồng thời; đây không phải công cụ bảo vệ trước filesystem thù địch.

Exit `0` nghĩa tạo/kiểm tệp thành công; exit `2` nghĩa tham số, tệp hoặc nội dung bị từ chối. JSON luôn có `label`, `hardware_verified=false`, `ts_smoke_pass=false`; kết quả kiểm thành công là `SYNTHETIC_PATTERN_VERIFIED`, không phải `BULK_DIAGNOSTIC_PATTERN_VERIFIED`.

## Protocol phiên bản 1

Mỗi record dài đúng **512 byte**. Các trường số là unsigned little-endian. Một record là đơn vị định dạng; ranh giới read/URB không nhất thiết là ranh giới packet USB thực.

| Offset | Số byte | Nội dung |
| --- | --- | --- |
| 0 | 32 | ASCII `SYNTHETIC_TEST_ONLY:FX2PATTERNv1`, thêm byte NUL tới đủ 32 |
| 32 | 2 | Phiên bản `1` |
| 34 | 2 | Kích thước record `512` |
| 36 | 4 | Sequence bắt đầu `0`, tăng đúng `1`, không wrap trong một tệp |
| 40 | 4 | Tổng số record trong kế hoạch, từ `1` tới `2048` |
| 44 | 4 | Seed `0..4294967295`, mặc định `0` |
| 48 | 4 | Tổng byte, đúng `record_count × 512`, tối đa `1048576` |
| 52 | 456 | Payload tại vị trí `i=0..455`: `(seed + sequence + i) & 0xFF` |
| 508 | 4 | CRC-32/ISO-HDLC của byte `0..507`, lưu little-endian |

CRC dùng polynomial chuẩn `0x04C11DB7` (reflected `0xEDB88320`), init/xorout `0xFFFFFFFF`, refin/refout=true; check vector ASCII `123456789` là `0xCBF43926`. Python dùng `zlib.crc32(body) & 0xFFFFFFFF`. CRC phát hiện lỗi ngẫu nhiên, không xác thực nguồn hoặc ngăn giả mạo. SHA-256 toàn tệp trong kết quả dùng đối chiếu artifact.

Các lỗi marker/version/size, metadata khác kế hoạch, CRC, payload, thiếu/lặp/đảo sequence, thiếu byte và dư byte đều bị từ chối. Hard cap áp dụng trước I/O và khi kiểm độ dài; không có chế độ unbounded, resync hoặc tự chấp nhận capture rỗng. Unit tests dùng bộ nhớ, không ghi thư mục tạm.

## Liên hệ với phân tích FX2

`tools\fx2\gate_b_static.py` kiểm ảnh firmware 8192 byte đã khóa hash và mô phỏng một số nhánh 8051; công cụ này kiểm **định dạng dữ liệu giả do host tạo**, không đọc ảnh đó hoặc mở rộng executor. Không có import giữa hai công cụ. Regression FX2 mặc định bỏ qua tests cần firmware riêng; không gọi phần bị skip là PASS.

AN70983 minh họa Bulkloop development board với EP2 OUT/EP6 IN, khác EP2 IN `0x82` của TBS5530. AN61345 minh họa FPGA làm master cho Slave FIFO, không chứng minh CPU-only phù hợp bo TBS5530. Chưa có mapping pinmux/board và recovery của firmware tùy biến, vì vậy không thêm source firmware, uploader, vendor request hoặc đường chạy live. Bản thiết kế riêng phải chứng minh an toàn trước khi xem xét nạp RAM; quyền chạy công cụ này không cấp quyền phần cứng.
