# Phase 3.1 — Runner Linux operational-only

Bộ công cụ này dành cho một lượt chẩn đoán có giới hạn trên guest đã được giao đúng TBS5530. Không thay thế guard của module, quyền hệ điều hành hoặc quyết định chuyển host. Không tạo snapshot, thay kernel, cài toàn media stack, bật LNB/DC hoặc chạy A0 firmware.

- `ReceiveK36.c`: dùng Linux DVB API trực tiếp, chỉ profile DVB-T2/K36/594 MHz/8 MHz/PLP0. Không có voltage, tone, DiSEqC hoặc antenna-power ioctl. Giới hạn chờ lock 10 giây, nhận 10 giây và 16 MiB; ghi file mới bằng O_EXCL, stop feed và đóng mọi fd. Self-test `-DTBS_SELF_TEST` không mở thiết bị.
- `RuntimeLedger.ps1`: khóa một lần theo guest boot, bus/devnum và descriptor hash. Ghi bền bằng CreateNew trước module load; đổi run/module hash hoặc terminal result không rearm. Ledger bị mất, hỏng hoặc attachment không rõ phải dừng, không tự phục hồi bằng xóa dữ liệu.
- `tests\Test-RuntimeLedger.ps1`: synthetic tests của ledger, không cần tuner. Thư mục scratch riêng được dọn trước khi công bố PASS.
- `tests\Test-ReceiveK36.sh` và `receiver-fake.c`: self-test profile và năm luồng dùng syscall mocks: nhận thành công, frontend sai, không lock, poll fault và EOVERFLOW. Không mở thiết bị, không tạo TS thật; kiểm stop filter/đóng fd và không phát ioctl nguồn/LNB.
- `Invoke-LinuxRuntime.ps1`/`GuestRuntime.sh`: hai lớp kiểm identity/param/hash, consume ledger trước load, trace trước probe, heartbeat/STOP và watchdog riêng. Sau lỗi vẫn thu bằng chứng, chỉ unload nếu telemetry chứng minh outstanding 0/drained 1/không quarantine. Không tự retry attachment.

Descriptor operational không chứng minh firmware RAM vẫn có hash lịch sử hoặc mọi trạng thái frontend giống Windows. Lượt operational-only chỉ dùng khi vừa kiểm đủ 50 byte descriptor, speed/config/interface/endpoint, owner mới và provenance bàn giao, đồng thời module không phát A0. Nếu có re-enumeration hoặc lỗi không rõ, không tự retry, reset, rebind hoặc upload để cứu lượt thử.

Mọi raw USB/TS/log/identity/ledger giữ dưới `D:\TSDuck` trên Windows, hoặc guest scratch riêng rồi copy vào đó. Không đưa trace, firmware, module binary, credentials hoặc identifiers riêng vào Git. Một lượt nhận thành công phải kiểm TS và cleanup riêng; lock hoặc build PASS chưa phải TS_SMOKE_PASS Android.
