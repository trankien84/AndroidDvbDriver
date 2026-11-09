# Kiểm chứng tĩnh firmware FX2 — Gate B

Công cụ này chỉ đọc tệp trên máy và giải mã một số đường chạy có giới hạn. Không có USB, SSH, VM, mạng, cài thư viện hoặc phát lại lệnh phần cứng. Firmware không được lưu trong Git và không được nhúng vào công cụ.

## Chạy kiểm thử

Dùng Python 3.12 đã có trên máy. Chạy từ gốc repository; không cần cài thêm thư viện. Lệnh mặc định chạy kiểm thử tổng hợp và bỏ qua nhóm cần firmware riêng.

```powershell
$env:PYTHONDONTWRITEBYTECODE = '1'
& 'C:\Python312\python.exe' -m unittest discover -s '.\tools\fx2' -p 'test_gate_b_static.py' -v
```

Để kiểm tra ảnh thật, đặt `FX2_TEST_FIRMWARE` trỏ đến tệp riêng có đúng 8192 byte và SHA-256 đã khóa trong công cụ. Có thể đặt thêm `FX2_TEST_EP0_CSV` để kiểm hai lượt A0 từ capture lịch sử. Công cụ không đọc thiết bị để lấy firmware hoặc capture.

## Phân tích tệp đã có

```powershell
& 'C:\Python312\python.exe' '.\tools\fx2\gate_b_static.py' --firmware '<private-firmware-path>' --ep0-csv '<private-ep0-csv-path>' --output '<new-private-output-path>'
```

Tệp đầu ra phải là tên mới; công cụ không ghi đè báo cáo cũ. `--ep0-csv` là tùy chọn. CSV phải có header đúng như `a0_epochs()` sử dụng và gồm cả submit/completion EP0, không chỉ setup A0. Khi xuất bằng TShark, dùng `-r` cho capture cũ; không dùng `-i` hoặc bắt capture mới.

## Phạm vi bằng chứng

Kiểm tra A0 tách từng epoch theo hai lệnh giữ CPU và hai lệnh nhả CPU; mỗi lượt phải có 132 transfers, đủ 8192 byte, không overlap, đúng thứ tự, cùng owner và có completion thành công. USB setup dùng little-endian; địa chỉ trong opcode MCS51 dùng big-endian. Status stage thành công không phải phép đọc lại RAM của thiết bị.

Bộ giải mã có độ dài cho các opcode MCS51; opcode `0xA5` là reserved. Một số mnemonic chưa được đặt tên hiển thị `OP_XX`; executor chỉ thực hiện tập lệnh cần cho proof và dừng khi gặp lệnh hoặc giá trị đọc chưa được hỗ trợ. Không dùng linear sweep để khẳng định mọi byte trong ảnh là mã chạy hoặc mọi caller đã được tìm thấy.

Các test ảnh thật kiểm 256 giá trị byte thứ hai và 256 selector, nguồn payload EP0, phân nhánh `0x8A`, phản hồi `0x81`, FIFO init và phân biệt bit IRAM với SFR. Mapping chân chỉ là diễn giải tài liệu, không phải đo điện hoặc xác định sơ đồ bo mạch. `candidate_G` luôn là `BLOCKED_FOR_SAFETY`; kết quả này không cho phép replay.
