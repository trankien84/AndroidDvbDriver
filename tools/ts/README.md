# Phân tích TS ngoại tuyến cho B3

`analyze_ts_capture.py` phân tích raw MPEG-TS hoặc classic pcap USBPcap bằng Python 3, chỉ dùng thư viện chuẩn. Không gọi phần cứng, ADB, VM, mạng, Node hay TSDuck. Đã kiểm trên Python 3.12.2. Source dùng LF.

## Chạy

Chạy từ root repository, sau khi chọn Python bằng `configurePythonEnvironment` và lấy lệnh bằng `getPythonExecutableCommand`. Các đường dẫn output phải mới và thư mục cha phải có sẵn. Không ghi đè input hoặc output đã tồn tại.

```powershell
& C:\Python312\python.exe -B -m unittest discover -s tools\ts -p test_analyze_ts_capture.py -v
& C:\Python312\python.exe -B tools\ts\analyze_ts_capture.py D:\TSDuck\sample.ts --urb-size 4096 --json D:\TSDuck\reports\new-run\raw.json --markdown D:\TSDuck\reports\new-run\raw.md
& C:\Python312\python.exe -B tools\ts\analyze_ts_capture.py D:\TSDuck\sample.ts --urb-size 4096 --skip-prefix 2048 --json D:\TSDuck\reports\new-run\after-prefix.json --markdown D:\TSDuck\reports\new-run\after-prefix.md
& C:\Python312\python.exe -B tools\ts\analyze_ts_capture.py D:\TSDuck\sample.pcap --bus 3 --device 3 --extract-raw D:\TSDuck\reports\new-run\extracted.ts --json D:\TSDuck\reports\new-run\usb.json --markdown D:\TSDuck\reports\new-run\usb.md
```

`--format auto|raw|pcap` mặc định tự nhận diện. `--urb-size` chỉ dùng cho raw và chỉ suy vị trí URB từ kích thước được cung cấp; không chứng minh độ dài thực của USB transfer. `--bus`/`--device` nhận số thập phân hoặc `0x...`, chỉ dùng cho pcap. `--min-sync-packets` mặc định 5, tối thiểu 2. Không có `--json` thì xuất JSON ra stdout. Exit 0 nghĩa phân tích thành công, **không** có nghĩa capture sạch; lỗi định dạng hoặc I/O trả exit 2.

`--skip-prefix 2048` chỉ loại phần đầu khỏi **phạm vi phân tích**. SHA, kích thước stream, offsets và vị trí URB vẫn theo raw đầy đủ. `--extract-raw` luôn giữ toàn bộ payload thành công đã chọn, kể cả prefix và tail; không sửa/gộp lại các đoạn đồng bộ. Chạy cả mặc định và skip để so sánh, không tự bỏ prefix ở mọi URB hoặc bỏ byte giữa luồng.

## Trích USBPcap an toàn

Hỗ trợ classic pcap 2.4 linktype 249, cả hai byte order và timestamp micro/nanosecond. Layout USBPcap và kiểm tra độ dài được chuyển từ `tools\observation\Verify-Capture.cjs`; không tái dùng trực tiếp vì yêu cầu chỉ Python stdlib. USBPcap header luôn little-endian, độc lập byte order của pcap.

Chỉ nối payload **completion**, `transfer=Bulk`, endpoint `0x82`, status 0, theo thứ tự completion trong capture. Không lấy submission, endpoint khác hoặc payload completion lỗi. Kiểm chặt global/record/header/payload lengths, timestamp fraction, snaplen và IRP trùng. Không chấp nhận truncated capture hoặc pcapng. Completion thiếu submission đầu capture được giữ nhưng đếm rõ, không suy submitted length từ `dataLength`.

Bộ lọc phải chọn đúng **một** cặp bus/device có dữ liệu thành công; thiếu hoặc nhiều owner đều bị từ chối, kể cả hai device cùng endpoint. Descriptor cho thấy một địa chỉ được tái dùng bởi các identity khác nhau cũng bị từ chối. Không thể chứng minh không có reconnect của thiết bị cùng identity nếu capture thiếu sự kiện; phải chọn capture của một lần thu rõ ràng. Completion lỗi nằm giữa các payload tạo ranh giới phân tích, không cho PSI/PCR đi xuyên phần dữ liệu không biết. Các completion lỗi cuối capture vẫn được báo, không tạo một sync break giả sau gói cuối.

## Ý nghĩa báo cáo

- **Đồng bộ:** tìm chuỗi byte `0x47` cách nhau 188 byte; các đoạn có start/end exclusive, số gói và vị trí đầu/cuối URB. Khi phase mới bắt đầu trong gói đang xét, gói cắt ngang bị loại khỏi cả hai đoạn. Có byte sync không tự chứng minh gói hoặc chương trình hợp lệ. Không chuyển đổi TS 192/204 byte.
- **Chỗ đứt:** ghi offset sync dự kiến, offset đoạn tiếp, khoảng bỏ qua, vị trí URB, độ lệch so với byte 2048. `near_2048` nghĩa khoảng cách không quá 188 byte, không nghĩa đúng tại 2048. `tail_incomplete_packet_bytes` báo phần cuối có sync nhưng chưa đủ 188 byte; **tail không tính sync break**. Byte ngoài phạm vi skip được ghi riêng.
- **CC:** bộ đếm thứ tự gói trên từng PID, tách so sánh/lỗi trong và giữa các đoạn. PID null `0x1FFF` không có yêu cầu CC. Payload tăng modulo 16; adaptation-only giữ CC; cờ discontinuity đặt lại baseline. TEI hoặc TS header hỏng làm mất baseline đáng tin cậy. Chỉ một lần lặp liên tiếp cùng PID/cùng CC/cùng nội dung được phép; PCR được phép thay đổi trong gói lặp. Gói thứ ba cùng CC hoặc cùng CC nhưng payload khác là lỗi. Một phép so sánh giữa đoạn không chứng minh chính xác số gói mất vì CC chỉ có 4 bit.
- **TEI/mã hóa/PID:** đếm trên các gói trong đoạn đồng bộ. Lỗi reserved bits hoặc extension PCR được ghi riêng, không biến một TS header còn đọc được thành gói không thể so sánh CC.
- **PAT/PMT/SDT:** ghép PSI qua nhiều gói, xử lý pointer field, nhiều section/gói và nhiều section/table. Chỉ công bố bảng đủ section, current, CRC-32/MPEG-2 đúng. Không ghép qua sync break, CC error, discontinuity, TEI hoặc nội dung mã hóa. Bảng dịch vụ nối PAT→PMT→PCR/ES PID và tên từ SDT actual. Thiếu bảng là chưa quan sát được, không chứng minh trên sóng không có. SDT other chỉ được đếm, không trộn dịch vụ khác vào multiplex hiện tại. Có raw descriptor hex; ISO-6937 mặc định không có codec stdlib nên tên ngoài ASCII có thể hiện escape thay vì đoán sai.
- **PCR:** ước lượng bitrate từ khoảng cách gói và đồng hồ PCR trong một đoạn. Cửa sổ cần ít nhất 3 mẫu, 100 ms và khoảng cách 100 gói. Không nối qua sync break, TEI, CC error, PCR lỗi hoặc PCR discontinuity; tính wrap 33-bit base/300 extension. Không đủ dữ liệu thì `UNKNOWN_INSUFFICIENT_CONTIGUOUS_PCR`, không điền 0.
- **PCR chẩn đoán:** `diagnostic_only` là phép thử giả thuyết bỏ qua cờ PCR discontinuity và reserved bits sai, nhưng vẫn dừng khi TEI/CC/header lỗi hoặc PCR không tăng. Có các cửa sổ và số cờ vi phạm để kiểm lại. Kết quả này **không** thay thế bitrate chuẩn hoặc chứng minh tốc độ USB; capture lỗi có thể gây sai lệch ngay cả khi con số gần kỳ vọng.
- **STALE_PREFIX:** báo hash/range/PID của đầu 2048 byte, PID sau và chỗ đứt đầu. Chỉ ghi `HYPOTHESIS_NOT_PROVEN`; không tự discard hoặc kết luận FIFO cũ. Việc skip làm sạch một đối chứng cũng không đủ xác định nguyên nhân phần cứng.
- **Hàng đợi native:** `submitted−reaped` chỉ mô tả số URB ứng dụng chưa thu hồi, không đo trực tiếp hàng đợi bộ điều khiển USB. `native_queue` luôn phân biệt mô hình ứng dụng với hardware queue/starvation chưa đo (`UNKNOWN_NOT_MEASURED`). Analyzer không đọc native metrics hoặc suy hardware starvation từ số đếm này.

## Kiểm thử và đối chứng B3

Tests tổng hợp bao gồm luồng liên tục, cắt giữa gói, mất nguyên gói, wrap/adaptation/discontinuity/duplicate CC, PSI nhiều gói/pointer/CRC/PAT/PMT/SDT, PCR ngắn/dài/wrap/lỗi, skip-prefix giữ nguyên SHA/offset/tail, pcap đa owner/endian/truncation/malformed/IRP/device identity. Không ghi fixture raw vào Git.

Đối chứng thực và lệnh/exit code được lưu riêng trong `D:\TSDuck\reports\phase31-b3-offline-20261112-204528-cc4555f6` và logs cùng run ID. Input gốc giữ nguyên. `verified` chứa sáu kết quả analyzer theo source cuối và logs 42 tests PASS. `final` lưu kiểm độc lập header CC/trích pcap và đối chứng TSDuck; các kết quả khác là lần kiểm ban đầu, không phải baseline cuối.

- Hai B2 SHA `d3f61c27…6f0132` và `1dcfef5e…4e9379`: kiểm độc lập 17 đoạn/16 chỗ đứt, cả 16 gần byte 2048; CC trong đoạn 0 nhưng số phép so sánh thực là **104/109**, không ép thành 100/108. Không quan sát PAT/PMT; bỏ prefix đầu vẫn còn 15 chỗ đứt, không cứu được luồng.
- Capture Windows không có tên `bda-cold2.pcap` trên filesystem; xác định được file `windows-retry2\cold-start.pcap` bằng SHA đầy đủ `70fced47614ff8ec17c9e78f79fca16408c23e6bcadcfb6ad25a4c69cd7f9ded`. Trích riêng bus 3/device 3. Sau skip 2048 còn một đoạn đồng bộ nhưng không phải capture CC/TEI sạch; PAT/PMT đủ 14 dịch vụ. PCR chuẩn chưa đủ cửa sổ hợp lệ; phép chẩn đoán có giả định riêng khoảng 27,487 Mbps. Xem JSON/Markdown và báo cáo kiểm độc lập thay vì gọi đây là TS smoke PASS.
