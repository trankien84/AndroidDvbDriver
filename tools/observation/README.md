# Quan sát USB đồng thời ở Windows và máy ảo

Bộ công cụ này thực hiện kế hoạch đã chuẩn bị cho TBS5530, không sửa driver Android, không đọc thanh ghi FX2 bằng request chưa chứng minh và không đổi controller. Windows USBPcap và guest usbmon/xHCI là các lớp quan sát host, không phải máy phân tích tín hiệu điện trên dây.

## Phạm vi và quyền

Cần PowerShell 7.5 trở lên. Mặc định không chạy live. [Invoke-LayeredObservation.ps1](Invoke-LayeredObservation.ps1) yêu cầu grant mới có hash bất biến, đúng VM/guest boot, firmware/module, quyền USB ownership, observer, temporary filter/usbmon và một bootstrap/receive có giới hạn. `-WhatIf` chỉ kiểm đầu vào/kế hoạch, không mở capture hoặc thay owner. Không dùng grant hoặc physical receipt cũ để rearm attachment.

Chỉ profile M late đã pin: DVB-T2 K36/594 MHz/8 MHz/manual PLP0, xHCI không đổi, queue M 8×65536 giữ nguyên. Đây không phải buffer experiment, không phải test Android nhận TS và không cho chạy original satellite probe. Source/compiled module/receiver phải giữ hash đã kiểm; không DKMS, cài driver hoặc nâng package.

Một phase tối đa 120 giây, receive tối đa 10 giây/16 MiB, heartbeat 2 giây. Mỗi observer tối đa 90 giây/64 MiB, snaplen 131072. Health có nhịp phát mục tiêu 200 ms; độ cũ tối đa 2000 ms là guard riêng, không phải cam kết Windows luôn lập lịch trong 200 ms. Startup phải hoàn tất lần phát health đầu trước ready; mọi cadence miss/gap được ghi, không che mất. Hết hạn không nới TTL; thiếu observer hoặc chạm cap thì STOP qua guard, không force-close syscall hoặc detach khi chưa drain.

## Thành phần

- [LayeredObservation.psm1](LayeredObservation.psm1): grant và coordinator fail-closed; ghi consume trước runtime, STOP/drain trước cleanup. Không báo TS success từ cleanup hoặc byte count.
- `WindowsObserver.psm1` / `WindowsObserverWorker.ps1`: discovery và worker USBPcap có permit riêng; fixture tách lớp và không được tạo live evidence. Chỉ dừng recorder do worker tạo.
- `GuestObserver.sh`: guest usbmon/raw xHCI trong instance riêng; không nạp usbmon hoặc chạm global tracing nếu chưa được coordinator cấp quyền.
- [GuestMeasuredRun.sh](GuestMeasuredRun.sh): wrapper cho module M đã pin, không thay payload/register plan. Watchdog guest độc lập SSH; chỉ unload module của lượt sau drain. Terminal được ghi và sync vào file tạm rồi đổi tên nguyên tử, tránh protocol đọc phải JSON đang ghi dở.
- [GuestChannel.sh](GuestChannel.sh): một kênh SSH lâu dài trên guest Bash 5 cho heartbeat, STOP, trạng thái và nhận diện khi chuyển phase. Chỉ gia hạn heartbeat khi có lệnh từ host; mất đầu vào hoặc trả lời quá 1500 ms thì dừng an toàn. Protocol dùng LF và đọc trọn dòng, không bỏ phần dòng bị chia giữa các gói SSH. Các permit được chuyển trước khi mở kênh, không dùng SSH/SCP ngắn trong vòng nhận.
- [Verify-Capture.cjs](Verify-Capture.cjs): đọc pcap offline, xác minh đúng 132 firmware controls/coverage/image hash, tách actual khỏi requested. Không nhận 264 controls hoặc partial image thành một bootstrap PASS.

Các worker đã có focused tests và process integration bằng fixture, nhưng chỉ được chạy live sau source hashes và grant mới đã đạt. Không suy file tồn tại hoặc parser PASS thành hardware readiness. Các lần arm bị chặn phải giữ nguyên terminal và output, không ghi đè hoặc mở lại cùng thư mục.

## Chẩn đoán riêng việc khởi động recorder

Khi chưa xác định được gate startup nào lỗi, có thể dùng một grant được duyệt riêng với `startup_diagnostic_only=true`, `allow_firmware_once=false` và `allow_terrestrial_receive=false`. Lượt này chỉ thu trạng thái recorder, không stage firmware, tạo runtime permit, nạp module M hoặc gọi receive; hai runtime seams cũng kiểm quyền độc lập và từ chối. Host vẫn phải ARMED trước attach, guest vẫn giữ nguyên ready barrier và caps. `startup-barrier.txt` ghi từng gate trước cleanup; bằng chứng được thu sau STOP. Đây không phải cách bỏ ready hoặc phép đo TS mới.

## Điều kiện trước khi báo cắm

1. Tuner phải ngắt khỏi mọi host. Đọc lại VM running/controller, guest identity, module hashes, Windows root-hub mapping và công cụ; không hardcode USBPcapN, guest bus/dev hoặc slot.
2. Duyệt exact scope cho một lượt và các thay đổi tạm cần thiết. Không snapshot, reset/poweroff VM, driver/Registry/service change, GPIO/B7/04xx/LNB hoặc firmware upload lần hai.
3. Arm cold host observer trước attach theo đúng hub đã chọn. Nếu scope bao gồm new devices trên hub, chỉ cắm đúng một tuner và không gắn thêm USB khác; lỗi nhận diện hoặc thiết bị khác làm kết quả không hợp lệ. Temporary VM filter chỉ cho đúng 734c:5530 và được gỡ đúng identity sau cleanup.
4. Trước khi arm, người dùng xác nhận đã ngắt nguồn ít nhất 5 giây/đèn tắt và vẫn giữ tuner rút. Chỉ khi recorder đã sẵn sàng mới báo cắm đúng cổng, cùng cáp/nguồn/RF IN. Có thể duyệt riêng `local_arm_notice=true` để chữ/tiếng bíp xuất hiện ngay trong cửa sổ helper sau ARMED, tránh độ trễ chat; không kéo dài capture cap hoặc cửa sổ chờ. Recorder phải quan sát từ vắng mặt sang default mới; không đợi bấm form sau khi cắm mới cho phép chương trình chạy. Một physical epoch duy nhất giữ qua các preflight chưa cắm, cold và handoff.
5. Xác minh default descriptors, capture target identity và cả hai observer ready trước firmware. Sau đủ 132 controls và drain, firmware staging tạm được gỡ; handoff cần operational descriptors mới và không có A0/firmware path.
6. Arm observers cho địa chỉ operational mới, giữ cùng physical epoch và chỉ chạy một receive. Dữ liệu/capture được hash ngoài Git, không upload raw hoặc firmware.

## Kiểm thử không tuner

```powershell
.\tools\observation\tests\Test-LayeredObservation.ps1
node --test .\tools\observation\tests\Verify-Capture.test.cjs .\tools\observation\tests\Analyze-LayeredCapture.test.cjs
.\tools\observation\tests\Test-ProcessIntegration.ps1
.\tools\observation\tests\Test-WindowsObserver.ps1
.\tools\observation\tests\Test-LiveAdapterContract.ps1
.\tools\observation\tests\Test-GuestChannel.ps1
```

`Test-GuestObserver.sh` và `Test-GuestMeasuredRun.sh` chạy fake sysfs/tracefs trên Linux hoặc Git Bash khi đủ utilities. Cần giữ bố cục `tests/fixtures`; thiếu `setsid`/`prlimit` phải ghi NOT RUN, không thay bằng capture thật.

Các test trên không SSH hoặc mở USB. Test worker/integration riêng phải dùng fixture rõ ràng, không gọi installed USBPcap capture. PASS offline không xác nhận khả năng thu dữ liệu thật hoặc `TS_SMOKE_PASS`.

## Đọc kết quả và cleanup

- Host có bytes nhưng guest không có, với coverage/pairing đầy đủ: khoanh đường proxy/virtual host/guest trong lượt đó; không tự quy lỗi Android kernel.
- Cả hai không có bytes: chưa phân biệt FIFO/board/physical host; không chứng minh FIFO rỗng hoặc tuner hỏng.
- Thiếu capture boundary, lost/truncated, clock/owner không rõ: INCONCLUSIVE, không negative test của tuner.
- Nếu proxy chia/gộp URB, so stream theo thứ tự/hash khi có đủ bằng chứng, không ép ghép 1:1.

Raw `xhci_urb_giveback` có status nhưng print format mặc định không in field đó; giữ trace.dat, raw report và lost-event stats. Không coi raw field là final completion: upstream Linux v6.8 chép `urb->status` vào trace trước khi USB core đặt status cuối, nên `-115` ở raw có thể đi cùng `-2` ở usbmon. Dùng usbmon completion để báo kết quả cho guest; giữ cả hai lớp và source provenance.

Windows host có thể báo lỗi sớm trong khi guest chỉ thấy cancellation lúc timeout. Lượt đo 014626 ghi một `USBD_STATUS_BABBLE_DETECTED` rồi 35 CANCELED ở host, tám cancellation ở guest và mọi actual=0. Không suy guest cancellation thành host không lỗi, không ép 36 IRP thành tám URB 1:1, không gọi host status là wire-level babble hoặc chứng minh FIFO rỗng. Báo cáo riêng có trong [hồ sơ dự án](../../docs/project/RESEARCH.md).

Cleanup cần module streaming=0/outstanding=0 và không còn client của lượt trước khi gỡ tài nguyên tạm. Không tự detach tuner về Windows khi BDA có thể khởi tạo; bước handoff lại cho người dùng phải rõ ràng sau drain.
