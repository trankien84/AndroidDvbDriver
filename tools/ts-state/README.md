# Đối chiếu trạng thái TS từ capture đã có

Bộ giải mã này chỉ đọc file offline. Nó không mở USB, SSH, VM hoặc mạng, không gọi TShark và không đổi firmware hay thiết bị. Mục đích là phân biệt dữ liệu host đã gửi, readback thực tế và trạng thái chưa biết; không chứng nhận MPEG-TS hoặc nguyên nhân gốc.

## Đầu vào và phạm vi

[Decode-TbsUsbState.cjs](Decode-TbsUsbState.cjs) đọc TSV do TShark xuất với các trường trong biến `fields`. Kiểm từng tên bằng `tshark -G fields` trên bản đã cài, rồi xuất bằng `-r <capture>`; không bắt capture mới. Dùng `-T fields -E header=y -E separator=/t -E occurrence=f` và một `-e <field>` cho mỗi trường. Phải giữ thứ tự frame và chọn bus/device tường minh; các địa chỉ đó chỉ có nghĩa trong capture đang xét.

Chỉ dùng bộ giải mã cho firmware TBS5530 đã xác minh riêng: 8192 byte, SHA-256 `b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc`. Việc giữ I²C pointer qua IN `0x81` dựa trên handler FX2 `0x0223` đã kiểm: nó đọc software bit `20.7`, không phải thanh ghi I²C hoặc chân PA7. Không áp dụng ngầm ngữ nghĩa đó cho firmware khác. Xem [bộ kiểm FX2](../fx2/README.md).

```powershell
node .\tools\ts-state\Decode-TbsUsbState.cjs 'D:\TSDuck\reports\<run-id>\late-fields.tsv' 'D:\TSDuck\reports\<run-id>\late-state.json' 2 3
node --test .\tools\ts-state\Decode-TbsUsbState.test.cjs
```

Đầu ra phải là tên mới; không ghi đè. Input tối đa 256 MiB. Raw TSV có thể chứa firmware và TS: chỉ lưu dưới `D:\TSDuck`, không đưa vào Git hoặc dịch vụ bên ngoài. JSON đầu ra bỏ payload A0 nhưng vẫn chứa provenance của thiết bị và giao dịch; giữ ngoài Git. Công cụ không tự xác minh hash firmware, giải mã mọi standard descriptor hoặc đối chiếu clock giữa các host.

## Cách đọc kết quả

- URB được ghép theo bus/device/endpoint/type/identity. Status tại completion mới dùng để kết luận; giá trị status ở submit không phải kết quả transfer.
- Bank chưa biết là `null`, không phải 0. Write thành công mới cập nhật bank; short/error/A0 làm mất trạng thái tương ứng. Readback chỉ có khi `0x90` và `0x91` ghép đúng owner/độ dài. `registers` giữ WRITE và READ riêng.
- Với USBPcap, `actual=0` ở status stage của control OUT không có nghĩa payload không gửi. `fullLength` ở đây chỉ nói đủ payload ghi trong capture và completion status thành công; không phải đo host-reported actual của data stage hoặc silicon ACK. Trường `lengthEvidence` ghi rõ giới hạn này.
- USBPcap Bulk requested length luôn `null`: actual 8192 không chứng minh host đã yêu cầu 8192. Linux usbmon lấy requested từ submission và actual từ completion. `bulk` giữ từng cặp frame để truy vết.
- `summary.sync` chỉ giải nghĩa readback SLV-T `0x64`, bank `0x20`, reg `0x10` theo nguồn CXD2878 đã pin: sync bits 2:0, TS_LOCK bit 5, unlock bit 4. TS_LOCK không chứng minh dữ liệu đã tới bo, FIFO hoặc USB.
- `summary.firmware` chỉ liệt kê ranh giới và độ dài A0; không tự tái tạo ảnh/hash hoặc chứng minh đủ 132 transfers. Phải kiểm ảnh/coverage/reset riêng bằng công cụ FX2. Cảnh báo không được xóa chỉ để có báo cáo sạch.

[Tests giả](Decode-TbsUsbState.test.cjs) kiểm bank/pointer/owner, short/error, IN81 đã review, A0 epochs, dữ liệu thiếu và phân biệt requested/actual. PASS offline không chứng minh tuner thật hoạt động. Không sửa reference, tăng buffer hoặc chạy lại phần cứng để kiểm công cụ này.

## Chuỗi nhận giả lập

[Test-FeedPipeline.cjs](Test-FeedPipeline.cjs) dùng M scratch đã qua [Test-MinimalSource.cjs](../linux-original/Test-MinimalSource.cjs). Nó lấy preamble/callback từ C test đã trích và lấy `start_feed`/`stop_feed` trực tiếp từ M; thay đúng seam `dvb_dmx_swfilter` bằng hàng đợi DVR giả để kiểm bytes đi nguyên vẹn qua feed → completion → demux seam → receiver read. Negative completion và stop không được giao bytes. Start/stop streaming, kernel demux và DVR vẫn là mocks, không phải chứng minh kernel thật hoặc MPEG-TS.

```powershell
node .\tools\ts-state\Test-FeedPipeline.cjs '<minimal-scratch>' '<callback-test-scratch>\minimal-extracted-test.c' 'D:\TSDuck\logs\<run-id>\pipeline-tests' '<verified-gcc.exe>'
```

Output phải mới và nằm ngoài Git. Script không build/load module, không gọi VM/SSH/USB hoặc cài compiler. Chỉ chạy sau khi `Test-MinimalSource.cjs` thành công; preamble hoặc seam không đúng mẫu thì dừng thay vì giả PASS.
