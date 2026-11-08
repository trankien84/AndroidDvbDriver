# Chuẩn hóa bằng chứng USB offline

`Tbs5530-TraceNormalizer.ps1` đưa ba kiểu trace về cùng cấu trúc để đối chiếu, không điều khiển tuner. Script không gọi mạng, ADB, TShark, USBPcap hay firmware. `Convert-TBS5530-Trace.ps1` là giao diện ghi JSONL và báo cáo. Các fixture trong `fixtures\trace-normalizer` hoàn toàn giả lập.

## Chạy kiểm thử

Không cần Pester hay cài thêm thư viện. Hỗ trợ PowerShell 7 và Windows PowerShell 5.1; script dùng UTF-8 có BOM để Windows PowerShell đọc đúng tiếng Việt.

```powershell
.\tools\tests\Test-TBS5530-TraceNormalizer.ps1
& "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -File .\tools\tests\Test-TBS5530-TraceNormalizer.ps1
```

Có thể thêm `-ReportPath D:\TSDuck\reports\<run-id>\normalizer\synthetic-tests.json`. Chọn tên mới: không ghi đè báo cáo test. Các file làm việc của test được tạo trong thư mục con riêng của `tools\tests` rồi dọn bằng `finally`; không dùng thư mục tạm hệ thống. Báo cáo và dòng PASS chỉ được ghi sau khi cleanup thành công; lỗi file lock không được biến thành toàn lượt PASS. Nếu process directory nằm trong chính cây fixture của runner, runner khôi phục directory ban đầu trước khi dọn; không dừng tiến trình khác hoặc bỏ qua lỗi cleanup. Test chỉ sử dụng fixture, không đọc trace thật.

## Chuyển đổi file

Mỗi lần chạy nhận **một file**. Không trộn thời gian giữa Windows, Linux và Android.

```powershell
.\tools\Convert-TBS5530-Trace.ps1 -InputPath .\tools\tests\fixtures\trace-normalizer\native.jsonl -Format NativeJsonl -CaptureEpoch synthetic-capture -AttachmentEpoch synthetic-attachment -OutputPath D:\TSDuck\reports\<run-id>\normalizer\native.jsonl
```

`-WhatIf` không tạo thư mục/file. Mặc định từ chối ghi đè output hoặc báo cáo; chỉ `-Force` mới cho thay thế. Input không được trùng output/báo cáo. Parse hoàn tất trước khi ghi; lỗi parse không tạo output. Mỗi file được công bố bằng đổi tên file staging cùng thư mục; **hai file output và summary không phải một giao dịch nguyên tử chung**. Lỗi I/O khi công bố file thứ hai có thể để lại file thứ nhất; phải kiểm cả hai.

`-ReportPath` mặc định là `<OutputPath>.summary.json`. `-MaxInputBytes` mặc định 64 MiB, trần 256 MiB; file quá lớn bị từ chối, không cắt ngầm. Snapshot input được đọc với chia sẻ chỉ đọc để tránh file đang bị ghi thay đổi giữa chừng. `-MaxRecords` mặc định 100.000, tối đa 1.000.000. Khi dừng trước hết file, summary ghi `truncated=true`. JSON array được kiểm cú pháp toàn bộ trong giới hạn byte; JSONL/usbmon chỉ parse các record trong cửa sổ đã chọn. Không dùng giới hạn record để khẳng định phần cuối file hợp lệ.

### Nhận JSON từ USBPcap bằng TShark

Chỉ đọc file đã thu bằng `-r`. CLI normalizer **không nhận trực tiếp `.pcap/.pcapng`**, không mở interface và không tự chạy TShark. Trước khi export, kiểm phiên bản và từng trường; không mặc định phiên bản mới có cùng tên trường.

```powershell
. .\tools\Tbs5530-TraceNormalizer.ps1
$tshark = 'C:\Program Files\Wireshark\tshark.exe'
& $tshark -v
if ($LASTEXITCODE -ne 0) { throw 'Không đọc được phiên bản TShark' }
$catalog = @(& $tshark -G fields)
if ($LASTEXITCODE -ne 0) { throw 'Không đọc được danh sách fields' }
$known = @{}
foreach ($line in $catalog) {
    $parts = $line -split "`t"
    if ($parts.Count -gt 2 -and $parts[0] -eq 'F') { $known[$parts[2]] = $true }
}
$fields = @(Get-Tbs5530TraceExportFields)
foreach ($field in $fields) {
    if (-not $known.ContainsKey($field)) { throw "Thiếu trường: $field" }
}
# Tạo sẵn thư mục báo cáo riêng và chọn tên file chưa tồn tại.
$output = 'D:\TSDuck\reports\<run-id>\normalizer\windows-fields.json'
if (Test-Path -LiteralPath $output) { throw 'Không ghi đè export' }
$tsArgs = @('-r', 'D:\TSDuck\traces\<capture>.pcap', '-c', '5000', '-T', 'json')
foreach ($field in $fields) { $tsArgs += @('-e', $field) }
$json = @(& $tshark @tsArgs)
if ($LASTEXITCODE -ne 0) { throw 'TShark export thất bại' }
[IO.File]::WriteAllText($output, ($json -join "`n"), [Text.UTF8Encoding]::new($false))
.\tools\Convert-TBS5530-Trace.ps1 -InputPath $output -Format UsbPcapJson -CaptureEpoch windows-capture -AttachmentEpoch observed-window -OutputPath D:\TSDuck\reports\<run-id>\normalizer\windows.jsonl
```

Danh sách export chỉ chứa header và setup đã giải mã, **không chứa payload firmware/TS**. Parser nhận dạng JSON phẳng `_source.layers` với tên trường có dấu chấm và giá trị scalar hoặc mảng một phần tử. Không nhận JSON lồng đầy đủ, `-T ek`, `jsonraw`, binary usbmon, isochronous, hoặc trường trùng nhiều giá trị. Ngoài `usb.setup.*`, parser nhận bốn trường `usbhub.setup.*` tương ứng; hai cách biểu diễn mâu thuẫn sẽ bị từ chối. Setup thiếu hoàn toàn ở stage Setup bị từ chối rõ vị trí. Một số dissector chuẩn chỉ xuất một phần setup qua các trường generic: giữ thành phần thiếu là `null`, thêm cảnh báo `incomplete_setup_fields`, không điền giá trị đoán hoặc khôi phục byte từ payload. TShark 4.6.9 đã được kiểm với export thật cho bộ trường này; không coi đó là chứng nhận mọi phiên bản.

## Cấu trúc chung `tbs5530.trace/1`

Mỗi dòng là **một sự kiện nguồn**, không phải một transfer đã cộng gộp. `null` nghĩa là không biết/không áp dụng; `0` và `false` là giá trị được ghi nhận, không được đổi thành `null`.

| Nhóm | Ý nghĩa và giới hạn |
|---|---|
| `capture_epoch`, `attachment_epoch` | Nhãn phiên capture và lần gắn thiết bị do bên thu thập cấp; không phải Unix time. Native bổ sung `run_id` nguồn vào phạm vi attachment. |
| `metadata` | `host`, `driver`, `build`, `attachment`, `descriptor`, `interface`, `alt`, `lock`, `mode`. Truyền bằng `-Metadata` chỉ khi có bằng chứng. Mặc định tất cả là `null`; không suy lock hoặc mode từ tên file. Không đưa secrets vào metadata. |
| `seq`, `source_seq` | `seq` là ordinal 1-based trong cửa sổ; `source_seq` là native `event` hoặc `frame.number`. Native submission `sequence` nằm trong `native.submission_sequence`. Không sắp xếp lại theo timestamp. |
| `timestamp` | `epoch_seconds` là chuỗi thập phân giữ độ chính xác của `frame.time_epoch`; native/usbmon không có thời gian Unix thì để `null`. `value`, `end_value`, `unit`, `clock_domain` giữ đồng hồ gốc. Native có khoảng `before_ns`/`after_ns`; summary native không có timestamp. |
| `transfer`, `transfer_stage`, `direction`, `endpoint`, `bus`, `device` | Bulk/control/interrupt, stage USBPcap nguyên gốc hoặc `null`, hướng dữ liệu USB và địa chỉ endpoint. Hướng control lấy từ `bmRequestType`, không nhầm với chiều IRP hoàn thành. |
| `setup` | `bmRequestType`, `bRequest`, `wValue`, `wIndex`, `wLength`, số nguyên được giải mã từ nguồn; chỉ kế thừa từ submit đã ghép chắc chắn. Không diễn giải setup thành khẳng định firmware thành công. |
| `requested`, `requested_source` | Native `requested_length`; usbmon độ dài tại submission, kể cả IN; control có thể dùng `wLength` tường minh. Bulk USBPcap luôn `null`: `data_len=0` ở submit hay `actual=8192` ở completion **không chứng minh kích thước buffer yêu cầu**. |
| `actual`, `actual_source` | Độ dài completion/stage được nguồn báo. Submit native có placeholder actual=0: giữ ở `native.reported_actual_length`, không gọi đó là completion actual. USBPcap `data_len` ở control status có thể bằng 0 dù stage data có byte; không cộng ngầm. |
| `status`, `status_domain` | Signed Linux URB status hoặc Windows USBD status. `linux-urb-submission` phân biệt trạng thái pending usbmon. Không dịch USBD thành Linux errno. |
| `syscall_return`, `syscall_errno` | Chỉ từ native syscall, độc lập với URB status: syscall 0/errno 0 vẫn có thể đi cùng status -75/actual 0. Nguồn không có thì `null`. |
| `native` | Operation, slot, submission sequence, allocated capacity, actual thô; hoặc các counter summary. Allocated capacity không thay thế requested. |
| `pairing` | Raw ID đã chuẩn hóa cách viết hex, generation tăng theo lần submit, transfer ID, submit/completion ordinal, trạng thái và canceled. Không có bằng chứng ghép thì không kế thừa độ dài/setup. |
| `source` | Tên file, format, ordinal, `line` và UTF-8 `byte_offset` cho text/JSONL; `json_path`, `frame`, `capture_interface` cho TShark. Không dùng vị trí buffer StreamReader để đoán offset. Không đưa đường dẫn cá nhân đầy đủ vào mỗi dòng. |
| `payload` | Mặc định `null`. Opt-in có `captured_bytes`, SHA-256 của **byte thật có trong capture**, excerpt hex tối đa 64 byte. Hash không đại diện toàn transfer khi capture bị cắt. |
| `warnings` | Mã cảnh báo như thiếu submit/completion trong cửa sổ, reuse mơ hồ, độ dài không khớp hoặc capture bị cắt. Summary đếm cảnh báo và trạng thái, không coi warning là parse error. |

### Quy tắc ghép và ranh giới thiết bị

Khóa ghép có capture epoch, attachment epoch, clock domain, capture interface, bus/device và ID. Mỗi submit tạo generation mới. ID dùng lại **sau completion** được ghép riêng. ID dùng lại **trước completion** trong USBPcap/usbmon không đủ thông tin phân biệt completion muộn: đánh dấu `ambiguous_id_reuse`, không đoán generation/độ dài cho completion; giữ cách xử lý thận trọng cho ID đó tới epoch mới. Native còn kiểm submission sequence để tránh ghép sai slot.

Thiếu submit hoặc completion là giới hạn của cửa sổ thu, không chứng minh thao tác chưa xảy ra. `DISCARD` là **yêu cầu** hủy, không phải bằng chứng hủy thành công; canceled chỉ nhận từ completion status đã biết (`-2`, `-104` hoặc USBD `0xc0010000`). Endpoint control phản hồi có thể khác bit hướng so với setup. Các stage Setup/Data/Status/Complete giữ thành sự kiện riêng; không cộng số byte của các stage để tạo một actual mới.

Reset/tháo cắm có thể dùng lại cùng bus/address/IRP. Không thể suy ranh giới đó chỉ từ nhãn file hoặc khoảng trống thời gian. Nếu có bằng chứng độc lập, truyền ordinal **sự kiện nguồn 1-based** sang nhãn mới:

```powershell
. .\tools\Tbs5530-TraceNormalizer.ps1
$result = Convert-Tbs5530Trace -InputPath D:\TSDuck\traces\export.json -Format UsbPcapJson -CaptureEpoch capture-a -AttachmentEpoch attach-a -AttachmentBoundaries @{ 123 = 'attach-b' }
```

`UsbmonText` nhận text `S/C/E` của bulk/control/interrupt (không ISO). Data tag `>` ở callback OUT nghĩa là không có data words được thu, không phải độ dài; tag `<` cũng không cung cấp payload. Chỉ `=` có thể đứng trước data words. Parser từ chối dữ liệu đi sau `<` hoặc `>` thay vì chấp nhận tùy ý. Submission length là số thập phân; `wLength` trong setup là số hex, ví dụ `0020` tương ứng 32. Callback actual 82 không thay đổi requested 65536 đã quan sát ở submit. Quy tắc này theo [tài liệu usbmon của Linux](https://docs.kernel.org/usb/usbmon.html), phần Text API, gồm ví dụ OUT callback kết thúc bằng `>`.

Timestamp microsecond giữ nguyên, mặc định miền `usbmon.timestamp_us:unspecified`; dùng `-UsbmonClockDomain guest.usbmon.timestamp_us:unmapped` khi chưa ánh xạ clock của guest sang host. Không tự chuyển thành thời gian Unix. Thư viện không tạo mô hình đồng bộ đồng hồ hay so sánh latency giữa host.

### Payload và an toàn bằng chứng

Chỉ dùng `-IncludePayload` khi thực sự cần; `-PayloadExcerptBytes` mặc định 0 và bị từ chối nếu chưa opt-in. Luôn chặn payload control `bRequest=0xA0` và control không biết setup, kể cả khi opt-in. Đây không phải bộ khử bí mật tổng quát cho mọi giao thức; không đưa trace thật, firmware, metadata riêng tư, TS hay output thực vào Git. Giữ chúng trong run riêng dưới `D:\TSDuck`. Chuẩn hóa thành công chỉ chứng minh parser xử lý được định dạng, không chứng minh tuner nhận MPEG-TS hay nguyên nhân lỗi phần cứng.
