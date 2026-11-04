# Kiểm kê môi trường

## 2026-11-04 — Phase 2.8: capture đã hoàn tất, tuner ở Windows — 18:38 +07

- Hai lượt capture được phê duyệt riêng: 30 giây đầu không có packets vì reconnect muộn;
  180 giây/8 MiB sau có 3028 packets, 232084 byte, chỉ bus3/address4 và không frame bị cắt.
  Capture ngoài Git; không cài/đổi filter/driver/registry. Người dùng tự reconnect đúng cổng USB 2.0,
  không nguồn phụ theo xác nhận. BDA 2.0.0.2 tự phát hai lượt dual-reset và vendor init;
  không có lệnh USB do agent gửi, không coi đó là Android upload.
- TShark đã dừng ghi; wrapper pipeline exit1 ở deadline vì worker elevated không tự thoát.
  Parent thường được dừng bằng PID; worker còn lại bị Access denied trong shell thường.
  Người dùng dừng đúng PID bằng Administrator, kiểm lại không còn TShark/USBPcapCMD.
  Không restart service hoặc dừng theo tên. Cleanup đã xác minh, không còn capture chạy nền.
- Wireless ADB hiện đúng OnePlus GM1900/API 30 sau hai lần fail-stop và người dùng xác nhận nối lại;
  monitor 10 giây bật lại. Tuner ở Windows/OK, không ở Android. Sau regression cuối đã cài lại debug,
  cold launch 996 ms: không có USB, operation/generation 0, pending/cleanup=false, open/claim/close 0.
  Không có session USB cũ được tái dùng.
- Thêm parser Java debug chỉ đọc TSV offline, kiểm trên capture thực với Java 8/major 52;
  fixture JVM là tổng hợp, không firmware/pcap thật. Không thêm dependency hoặc cài công cụ.
- Refresh GitNexus cuối sau thêm parser FAILED: FTS `file_fts` inconsistent trong incremental COPY.
  `status` vẫn báo up-to-date theo HEAD đã index cũ; không tin là graph đã chứa parser mới.
  Không tự drop/rebuild FTS hoặc force analysis để sửa vấn đề ngoài phạm vi; quay về source/diff/tests.

## 2026-11-04 — Phase 2.8: checkpoint chuyển tuner sang Windows, 18:20 +07

- Người dùng duyệt riêng chuyển host và tự cắm tuner vào USB 2.0 laptop; trước đó chỉ dùng nguồn OTG,
  không nguồn/LNB/hub có nguồn hoặc dây cấp điện khác. Chưa duyệt capture/upload Android.
- Có một lần TCP Wireless ADB mất ở checkpoint; dừng trước truy vấn tuner Windows.
  Chỉ sau người dùng xác nhận nối lại mới xác minh serial mới/GM1900/OnePlus/API 30 và owner không pending/open.
  Không pair/reconnect tự động hoặc dùng lại USB session cũ.
- Windows hiện có TBS 5530 DVB BDA (MEDIA/OK), driver 2.0.0.2/oem108.inf/tbsBDA.TBS5530;
  topology EHCI #2 → Generic USB Hub → port 2. Extcap đọc qua Process stdout xác nhận USBPcap3,
  tuner USB address 4. PnP Device_Address=2 không phải địa chỉ USB capture; không dùng lẫn hai giá trị.
- Android dumpsys không còn tuner. Activity còn hiển thị report metadata cũ nên không coi report đó là
  xác nhận attachment hiện tại; owner không open/pending. Driver Running/PnP OK chưa chứng minh firmware/TS.
- Root hub có webcam/Bluetooth; cấm capture-all. Chưa mở capture handle, chưa đo quyền capture,
  không đổi driver/filter/nguồn. Mọi instance ID/raw topology giữ ngoài Git.

## 2026-11-04 — Phase 2.8: xác minh công cụ capture và driver hãng, không thay môi trường

- Baseline thực `ef1154d9c60725150e0d2eaab082d7b644748444`, nhánh `chore/modernize-android-build`.
  Không cài/gỡ/nâng cấp phần mềm hoặc SDK, không thay PATH/registry/driver/USB ownership.
  Toolchain đọc lại từ build/wrapper/package local: AGP 9.5.0, Gradle 9.8.2, JDK 25.0.5.0.3,
  compileSdk 37.2 revision 2, Build-Tools 37.0.0, NDK 30.0.16248370, CMake 4.1.2;
  target 37/min 21/Java 8 giữ nguyên. Không khảo sát latest stable vì không có tác vụ nâng cấp.
- **RUNTIME-OBSERVED:** đúng một TCP target OnePlus GM1900/API 30 online; alias mDNS không phải
  thiết bị thứ hai. USB Host feature có. Monitor local kiểm đúng serial/model/manufacturer/API mỗi 10 giây;
  không ghim IP/port vào hồ sơ. Báo cáo app-private đọc đầu phiên có generation 0/DETECTED,
  permission=false, pending/cleanup=false, open/claim/close 0; đó là file báo cáo đã tồn tại,
  không được gọi là phép đọc raw mới. Kiểm runtime mới sau regression ghi WORKLOG.
- **Capture inventory:** `C:\Program Files\Wireshark` có Wireshark/TShark/Dumpcap 4.6.9;
  `C:\Program Files\USBPcap\USBPcapCMD.exe` và SYS 1.5.4.0.
  Service USBPcap Running/Manual; `tshark -D` và extcap liệt kê USBPcap1–5.
  `dumpcap -D` không liệt kê USBPcap vì đường extcap do TShark cung cấp; không coi đó là capture failure.
  Shell không elevated; chưa kiểm khả năng mở capture handle hoặc ghi trace. Cấu hình extcap liệt kê
  snaplen/buffer/devices/capture-all/capture-new/inject-descriptors, chưa trả mapping tuner cụ thể.
  Không start capture, đổi filter, restart service hoặc cài lại. Tuner chưa chuyển khỏi OnePlus.
- **Driver TBS đã cài:** service `tbsBDA.TBS5530` Running/Auto;
  `C:\Windows\System32\drivers\TBS5530.sys` phiên bản 2.0.0.2, 404168 byte,
  SHA-256 `48f2d8fefd467adc4dafe68c2708439e794c4c1daf69f7db5405b5388cf9e65c`,
  trùng SYS x64 trong ZIP hãng 2025-06-10. PnP query không trả tuner hiện tại;
  service Running không chứng minh TBS đang hoạt động hoặc đang nối Windows.
  ZIP Windows 11 2.1.0.3 chỉ phân tích tĩnh, không cài lên Windows 10.
- **Công cụ offline:** `objdump.exe` hiện có tại `C:\msys64\mingw64\bin`, phiên bản GNU Binutils
  2.47.20260726; chạy trực tiếp executable để đọc PE, không khởi động môi trường MSYS2.
  NDK còn có llvm-objdump. Dùng Node.js/.NET sẵn có để so khớp bytes/ZIP và Git `pdftotext.exe`
  để trích PDF; không cài Ghidra/Python package hoặc thực thi SYS/EXE hãng.
  Firmware/PDF/ZIP/disassembly/logs chỉ ở session local ngoài Git, không đăng firmware hoặc traffic.
- **GitNexus:** CLI không hỗ trợ `status --json`; `status` xác nhận thiếu index, sau đó
  `analyze --index-only` thành công trong 71,5 giây. Refresh sau sửa interfaces debug đạt trong 95,4 giây;
  `status` xác nhận đúng repo/branch/HEAD và up-to-date. Không sinh instructions/skills hoặc sửa tracked files.
  Graph giúp xem callers/impact; source/tests vẫn là thẩm quyền. AgentMemory recall rỗng;
  đã lưu một finding từ nguồn công khai, không gửi code/log/định danh thiết bị.

## 2026-11-04 — Phase 2.7: không đổi toolchain; Wireless ADB có một lần dừng

- Baseline Git `f03e092702edac5134efea633d8a30a51e1716e4`, nhánh `chore/modernize-android-build`. Đã đọc lại khai báo build, wrapper và `source.properties`: AGP 9.5.0, Gradle 9.8.2, JDK 25.0.5.0.3, compileSdk 37.2 revision 2, Build-Tools 37.0.0, NDK 30.0.16248370 và CMake 4.1.2. Giữ targetSdk 37, minSdk 21 và Java source/target 8. Đây là các phiên bản đang khai báo/cài và vừa kiểm thử, không phải tuyên bố về bản stable mới nhất.
- `Inspect-TBS5530-Toolchain.ps1 -NoReport -PassThru` bị execution policy chặn vì script chưa ký số. Không bypass/unblock hoặc sửa policy; thay bằng kiểm tra trực tiếp package và executable. Không cài/gỡ/nâng cấp SDK, JDK hoặc dependency; không đổi biến môi trường User/Machine. Dùng Android CLI đã cài để đọc help và tìm tài liệu; không khởi động/thay AVD hoặc dùng WSL/usbipd.
- Bộ giám sát Wireless ADB kiểm đúng serial đã chọn, model GM1900 và API 30 mỗi 10 giây. Có một lần target mất kết nối khi đang sửa core: dừng toàn bộ, chưa build/test hoặc mở USB; chỉ tiếp tục sau xác nhận của người dùng. Đã tra serial TCP mới, xác minh manufacturer=OnePlus, model=GM1900, API=30 và USB Host=true. Metadata tại checkpoint: generation=0, state=DETECTED, pending=false, open=0. Không tái dùng handle hoặc số phiên gắn thiết bị cũ; không đưa IP/port/serial vào hồ sơ Git.
- Lượt cuối có 218 phương thức kiểm thử JVM, 49 instrumentation tests, build debug/release và lint đạt `PASS` trên OnePlus Android 11/API 30/arm64. Chưa kiểm mới runtime API 21 hoặc USB trên thiết bị dùng trang nhớ 16 KiB. Sau UTP đã cài lại debug APK và khởi động lạnh trong 716 ms, chỉ đọc metadata: permission=false, attachment `734c:5530`, generation=0 và không có tài nguyên đang chờ xử lý. Không đọc raw lần nữa hoặc gửi firmware/vendor request thật.
- Firmware, archive và PDF của nhà sản xuất được tải để phân tích offline vào session local ngoài Git; không đưa vào assets/APK. Archive dài 566825 byte, ảnh firmware dài 8192 byte; hash, nguồn gốc và giấy phép ở [RESEARCH.md](RESEARCH.md). Dùng `pdftotext.exe` có sẵn trong bản Git để đọc PDF, không cài package PDF. Chương trình kiểm tra offline bằng Java 8 không gọi USB. Với Python chỉ kiểm các package có sẵn, không cài thêm hoặc thêm cấu hình repository.

## 2026-11-03–04 — Phase 2.6: raw descriptor trên OnePlus, không đổi toolchain

- Xác minh mới GM1900/API30/state=device qua một serial TCP tường minh; bỏ alias mDNS. Một lần serial TCP cũ biến mất trong lúc sửa diagnostic: dừng ngay trước build/test/USB, chờ người dùng xác nhận, tra cổng mới và cold launch owner mới. USB Host=true, attachment734c:5530, permission=false ban đầu, generation0/pending=false/open0. Không pair/reconnect tự động hoặc chuyển thiết bị.
- Toolchain đang khai báo/đang cài được kiểm trực tiếp: Gradle9.8.2/checksum wrapper giữ nguyên, AGP9.5.0, Java25.0.5.0.3, platform37.2 revision2, Build-Tools37.0.0, NDK30.0.16248370, CMake4.1.2, ADB37.0.1. Compile37.2/target37/min21/Java8 giữ nguyên; runtime OnePlusAPI30/arm64. Không kiểm latest stable vì không có tác vụ nâng cấp; không install/uninstall hoặc đổi môi trường Machine/User. Lệnh build chỉ dùng môi trường tiến trình con.
- Chu kỳ raw được người dùng duyệt riêng: permission callback thật có1 lần/match=true, sau đó raw operation2/generation2, open/getRawDescriptors/close1 lần,13ms, trên worker; không claim/transfer. Tree189 bytes, USBbcd0200/devicebcd0001, configvalue1/wTotalLength171/bNumInterfaces1, alt0–3; không82. Công suất khai báo100mA không phải đo nguồn; packet512 không đo negotiated speed.
- Metadata và raw report đã lọc nằm app-private/session, không có raw bytes, USB serial/path hoặc credentials trong Git. APK/log/cache local không phải deliverable công khai. Kiểm permission cuối sau instrumentation/cài lại được ghi ở WORKLOG; không mặc định quyền giữ qua UTP install/uninstall.
- Không thực thi TBS ChangeMode; ZIP chỉ đọc trong RAM và inspect chuỗi tĩnh, không EXE trên đĩa/không driver Windows. TBS/WSL/Linux snapshots giữ riêng provenance; không chuyển tuner khỏi OnePlus.

## 2026-11-03 — Phase 2.5: OnePlus 7 thật qua Wireless ADB

- Thiết bị xác minh trực tiếp: **OnePlus GM1900 (OnePlus 7), Android 11/API 30**; build `GM1900_14_220617`, OTA `OnePlus7Oxygen_14.P.45_GLO_0450_2206171433`. Người dùng cung cấp OxygenOS 11.0.9.1.GM57AA; thuộc tính ro.oxygen.version trống nên không gọi đây là phiên bản vừa đo bằng thuộc tính hệ thống.
- Wireless ADB (ADB không dây) ban đầu offline: dừng ngay. Sau khi người dùng nối lại và xác nhận, tra serial TCP/port mới rồi xác minh manufacturer/model/API/state trước khi tiếp tục. Có alias mDNS của cùng thiết bị; toàn bộ lệnh chọn đúng một serial TCP, không dùng emulator. Không lưu IP/port/thông tin xác thực ghép nối vào mã nguồn hoặc hồ sơ công khai. Build dài có kiểm tra kết nối mỗi 10s; không đổi ADB tcpip, không ghép nối lại hoặc tự chuyển thiết bị đích.
- Android USB Host feature=true; OTG đang nhận diện TBS 734c:5530. Một cấu hình có ID 1, bốn mục cùng interface ID 0/alternate 0–3; alternate 0 không có endpoint, các alternate khác không có IN 82. Không chỉnh thiết lập OTG, thuộc tính USB hệ thống, kernel, SELinux hoặc driver Windows. Không dùng usbipd/WSL cho kết nối trực tiếp Android.
- Compile SDK 37.2/target 37/min 21/Java 8 và toolchain AGP 9.5.0/Gradle 9.8.2/JDK 25 giữ nguyên. **Runtime kiểm thử là API 30**, không phải API 37. Chức năng xuất báo cáo có cấu trúc dùng AtomicFile/API 17, StandardCharsets/API 19; các nhánh xử lý mới đều nằm trong phạm vi min 21.
- JSON của debug nằm trong vùng riêng của ứng dụng, đọc qua run-as, có metadata đã lọc, timestamp/operation/generation và counters (thời điểm/thao tác/phiên và bộ đếm); không có USB serial/path/OS device ID. Dữ liệu dumpsys thô có thể chứa thông tin cục bộ, không commit log thô. Chưa đo tốc độ USB đã thương lượng; kích thước packet 512 không được dùng thay cho phép đo tốc độ.

## 2026-11-03 — Phase 2.4B: kiểm kê chuyển tiếp USB từ Windows qua QEMU

- Windows 10.0.19045, WHPX 10.0.19045 đã cài và dùng được theo emulator -accel-check, exit 0. Emulator **37.2.13.0/build 16740729**, QMP query-version trả **QEMU 2.12.0**, package `(v2.12.0-19097-g85fa07f04ef)`; không giả định đây là upstream QEMU 11 theo ghi chú phát hành preview.
- SDK D:\Android\Sdk và AVD D:\Android\AVD; ANDROID_HOME/ANDROID_AVD_HOME trong process hiện đúng, khác giá trị cũ trong snapshot Phase 1.2. JAVA_HOME ở Process/Machine cùng là C:\Program Files\Java\latest\jdk-25, User trống. Không sửa môi trường toàn cục hoặc toolchain.
- AVD TBS5530_Runtime_API37, image **android-37.0/google_apis/x86_64 revision 7**, runtime API 37/x86_64, kernel **6.12.81-android16-6-g4f69fc7b210c-ab16167562**. Giữ userdata, no-snapshot-load/no-snapshot-save, không wipe hoặc thay kernel/system image.
- usbipd-win **5.3.0**; thiết bị TBS 734c:5530/BUSID 6-2 được tra trực tiếp, **Shared, không phải Attached** (BUSID chỉ là snapshot, không được ghim cố định). Ubuntu 22.04 WSL2 ở trạng thái Stopped. Không detach vì không cần, không bind/unbind/attach WSL. Tham số usb-host chỉ nhắm đúng TBS, không đổi thiết bị khác.
- PnP **TBS5530DVBBDA/MEDIA/OK**, nhà cung cấp chữ ký TBS/**2.0.0.2**, service **tbsBDA.TBS5530**, INF **oem108.inf**. Không thay bằng WinUSB/Zadig/filter. Metadata phía host qua QMP cho thấy 480 Mb/s; **libusb_open=-12/unrecognized device driver**, chặn passthrough trước khi Android guest nhận diện TBS.
- Guest `android.hardware.usb.host=false`; root USB rings/sysfs hubs không phải TBS. Quyền đọc `dmesg`/thuộc tính USB trong sysfs bị từ chối, không thay root/SELinux. `dumpsys usb` bị timeout khi khởi động với passthrough, còn service hoạt động trong lần khởi động đối chứng; chưa chứng minh image có thể cung cấp USB Host sau khi đổi driver phía host.
- Log khởi động/libusb thô nằm trong thư mục tạm của công cụ local và chứa định danh thiết bị phía host, không commit hoặc đăng thành artifact. Chi tiết lệnh và các tầng kiểm chứng ở WORKLOG/RESEARCH/TEST-MATRIX. Các emulator hỗ trợ kiểm thử đã được điều phối theo PID/shell, không tác động phiên AVD khác.

## 2026-11-02 — Phase 1.2: kiểm kê sau dọn SDK và nâng riêng AGP

**Nguồn:** Windows PowerShell 7.6.9 / Windows 10.0.19045; nhánh `chore/modernize-android-build`, HEAD nền `ace7e9f187d11a685975f707b6743d779fa847d9`. Có sáu thay đổi local do người dùng chuẩn bị trước phase (hai instructions sửa, instruction/skill kiểm kê mới và hai script mới); không reset hoặc commit chúng.

### Các package thực còn lại

`Inspect-TBS5530-Toolchain.ps1`, Android CLI `sdk list` và `source.properties` được đối chiếu, không suy từ inventory cũ:

| Package | Phiên bản/revision local | Vai trò |
|---|---|---|
| platforms/android-34 | revision 3 | Còn trên máy; không chọn để compile hiện tại |
| platforms/android-37.2 | revision 2, API 37.2, PreviewSdkInt 0 | Compile SDK dự án |
| build-tools/37.0.0 | 37.0.0 | Phiên bản dự án ghim |
| ndk/30.0.16248370 | r30 | Phiên bản dự án ghim |
| cmake/4.1.2 | 4.1.2 | Phiên bản dự án ghim |
| cmdline-tools/latest | 23.0 | Công cụ dòng lệnh SDK |
| platform-tools | ADB 37.0.1 | Truy cập thiết bị qua dòng lệnh |
| emulator | 37.2.13 | Chương trình emulator |
| system-images/android-34/google-tv/x86 | metadata revision 3 | Giữ nguyên; không dùng trong lần kiểm tra nhanh này |
| system-images/android-37.0/google_apis/x86_64 | metadata revision 7 | Image của AVD đã có; CLI catalog hiện in revision 8, không coi đó là package đã cập nhật |

Không còn platform 37.0, Build-Tools 36, NDK 28 hoặc CMake 3.22.1 trong kiểm kê local. Image 37.0 vẫn còn và độc lập với SDK platform 37.0. Không uninstall/cài/update/xóa AVD hoặc SDK trong phase này. Review dọn dẹp chỉ tạo CSV; platform 34 là REVIEW chứ không tự kết luận vô dụng cho dự án khác.

### Dò tìm JDK và AVD

- Process agent: JAVA_HOME cũ `C:\Program Files\Java\jdk-25` không tồn tại; User JAVA_HOME trống; Machine đúng `C:\Program Files\Java\latest\jdk-25`. Các lệnh build nạp Machine JAVA_HOME vào child PowerShell; Gradle xác nhận Launcher 25.0.5.0.3/daemon dùng resolved jdk 25.0.5.0.3. Không sửa biến môi trường User/Machine hoặc khởi động lại VS Code.
- ANDROID_HOME/ANDROID_AVD_HOME thiếu ở Process, đúng ở User: D:\Android\Sdk / D:\Android\AVD. Audit xác định được User AVD home nhưng trước khi sửa **không truyền home này vào emulator subprocess**, nên danh sách emulator rỗng giả. Thư mục `.avd` dùng DirectoryInfo.BaseName còn nguyên suffix, gây thêm tên giả `TBS5530_Runtime_API37.avd` bên cạnh tên đăng ký thật.
- Đã sửa audit: chỉ truyền AVD home trong lúc chạy lệnh liệt kê, khôi phục môi trường ban đầu trong finally; bỏ suffix `.avd` đúng cho tên thư mục; hợp nhất metadata/emulator. Kết quả thật: **một AVD `TBS5530_Runtime_API37`, emulator=1, metadata=1**. Có kiểm thử hồi quy cho cả trường hợp biến môi trường vắng/có sẵn và emulator không tồn tại. Không tự khởi động AVD từ audit.
- Audit JSON bổ sung các giá trị JAVA_HOME hợp lệ và executable build gợi ý; vẫn báo rõ process dùng giá trị cũ, **không tự đổi JAVA_HOME**. `scripts/check-env.ps1` chuyển thành điểm vào gọi audit chung để tránh hai cách kiểm kê lệch nhau. `-NoReport -PassThru` hỗ trợ assertions/automation.
- Parser review dọn CMake được giới hạn trong externalNativeBuild.cmake; không nhầm buildToolsVersion là CMake. Tất cả báo cáo local ở parent logs/toolchain, không commit metadata của máy/host.

### Chính sách cho phép chạy script

Lần đầu chạy script bị RemoteSigned chặn. Đã hỏi và người dùng **chỉ định rõ** `Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process`. Thực hiện đúng scope Process trong từng child, lưu policy cũ và restore trong finally; không `Unblock-File` hoặc đổi CurrentUser/LocalMachine. Kết quả sau lệnh Process=Undefined, User/Machine vẫn RemoteSigned. Không biến việc bỏ chặn được duyệt lần này thành mặc định tự bypass trong tooling.

### Phân biệt bốn trạng thái phiên bản

| Thành phần | Đã khai báo | Đã cài | Bản stable đã tra | Đã kiểm chứng cho dự án / hành động |
|---|---|---|---|---|
| AGP | 9.5.0 sau khi sửa root plugins | Được phân giải qua Gradle cache/build, không phải SDK package | Google Current Release 9.5.0 ngày kiểm 2026-11-02 | Chỉ nâng 9.4.1 → 9.5.0; kết quả kiểm chứng cuối ở TEST-MATRIX |
| Gradle/JDK | Wrapper 9.8.2 / JVM 25 | 9.8.2 / 25.0.5.0.3 | Không tìm lại mọi phiên bản JDK/Gradle mới nhất; ma trận 9.8.2 xác nhận JVM 25 được hỗ trợ | Giữ nguyên, wrapper checksum không đổi |
| SDK/Build-Tools | 37.2 / 37.0.0 | Đúng phiên bản đã ghim | Không có yêu cầu tìm/nâng toàn SDK | Giữ nguyên compile 37.2/target 37/min 21 |
| NDK/CMake | 30.0.16248370 / 4.1.2 | Đúng phiên bản đã ghim | Không dùng tuổi package để quyết định nâng/xóa | Giữ nguyên, kiểm ABI/native gates |
| Android runtime | Không quyết định bởi compileSDK | AVD image 37.0 x86_64 | Không tra/tải image mới | API 37/4 KiB chỉ sau getprop/khởi động thật; không đại diện arm64/16 KiB/API 21 |

Tương thích Google AGP 9.5: Gradle tối thiểu 9.6.0, JDK tối thiểu 17, API tối đa 37; mặc định NDK 28.2/BT 36 **không phải yêu cầu bắt buộc hạ phiên bản đã ghim**. Gradle 9.8.2 hỗ trợ JVM 25 từ dòng 9.1.0; vẫn cần thực thi kiểm thử hồi quy của dự án. Nguồn chính thức được ghi trong WORKLOG/DECISIONS; không coi các phiên bản này là mới nhất mãi mãi.

## 2026-11-02 — Phase 1.1: runtime thực trên emulator, không tuner

**Host/cwd:** Windows 10.0.19045, PowerShell 7.6.9, root AndroidDvbDriver. Baseline commit `6da609ba49dd9f1f4d284291918f68a6378972ff` đã push theo người dùng, local origin-tracking ref cùng SHA. Nhánh `test/android-runtime-smoke` được tạo từ modernization, bảo toàn 2 thay đổi local `.gitignore`/workspace.

### Môi trường build giữ nguyên

- Process agent còn JAVA_HOME cũ `C:\Program Files\Java\jdk-25` không tồn tại và thiếu ANDROID_HOME/ANDROID_AVD_HOME; Machine JAVA_HOME đúng `C:\Program Files\Java\latest\jdk-25`, User SDK/AVD đúng D:\Android. Mỗi lệnh PowerShell build/emulator đặt biến môi trường **trong process đó**, không sửa toàn cục/khởi động lại VS Code.
- `gradlew.bat --version` xác nhận Gradle 9.8.2, Launcher JVM 25.0.5.0.3, Daemon JVM dùng resolved jdk 25.0.5.0.3. Build scripts dùng plugin 9.4.1, compile 37.2/target 37/min 21; source.properties xác nhận NDK 30.0.16248370/CMake 4.1.2/BT 37.0.0. Không nâng phiên bản/dependencies.
- Android CLI `--help/info sdk/sdk list`, ADB 37.0.1 và Emulator 37.2.13 `-version` chạy được. Android docs search/fetch dùng Knowledge Base (bộ tài liệu tra cứu) hiện có cho instrumentation/insets; không cài SDK/image.

### AVD thử nghiệm đã được phê duyệt

| Thuộc tính | Kết quả thực |
|---|---|
| AVD |`TBS5530_Runtime_API37`, tạo riêng qua avdmanager từ image đã cài |
| Đường dẫn |`D:\Android\AVD\TBS5530_Runtime_API37.avd`; không chạm AVD khác |
| Image |`system-images;android-37.0;google_apis;x86_64`; source.properties revision 7 tại thời điểm kiểm tra (catalog CLI trước đó báo 8, ưu tiên metadata local và runtime) |
| Profile |`medium_phone`; không cấu hình lại OS/hypervisor |
| Emulator runtime |Android 17, SDK 37, ABI x86_64, serial thử nghiệm emulator-5554 |
| Page size |`adb ... shell getconf PAGE_SIZE` = 4096, **không phải 16 KiB** |
| USB Host |pm list features không có usb.host; không có tuner chuyển vào emulator |
| Startup flags |`-avd TBS5530_Runtime_API37 -port 5554 -no-snapshot -no-boot-anim -no-audio -no-window -gpu swiftshader_indirect` |
| UI automation |ADB MCP qua Proxy: list_avds/describe_ui/screenshot/tap; luôn chỉ định serial; dùng ADB trực tiếp cho install/am/dumpsys/logcat và Gradle instrumentation |

Ban đầu CLI/ADB/MCP đều không thấy AVD/device; sau khi người dùng phê duyệt, đã tạo AVD bằng avdmanager vì Android CLI create chỉ nhận profile, không cho chọn chính xác image/path/name. `android emulator list` không in mục ở lần đầu nhưng emulator -list-avds và MCP sau đó thấy tên; không giả định output rỗng nghĩa là thiếu image. Không tải system image mới. Dữ liệu AVD dùng mặc định của profile, không wipe; sau kiểm thử gửi `adb -s emulator-5554 emu kill` chỉ tới đúng AVD này, không xóa file/dữ liệu.

**Lưu ý SystemUI:** lần khởi động đầu có hộp `System UI isn't responding`; screenshot/hierarchy xác định package com.android.systemui, đã chọn Wait, lớp phủ biến mất và tiếp tục kiểm thử app. Không gộp lỗi này thành app ANR hoặc bỏ qua trong báo cáo. Runtime app không tuner không yêu cầu quyền lưu trữ; dumpsys package cho thấy READ/WRITE_EXTERNAL_STORAGE=false nhưng Welcome/Diagnostic/chooser vẫn chạy; quyền foreground đã khai báo/được cấp, DvbService chưa hoạt động.

**Bằng chứng ngoài Git:** session files/phase-1-1 chứa screenshot trước/sau, screenshot không tuner, instrumentation-results.xml và app-logcat-filtered.txt. Không commit Logcat thô/system UI/trạng thái AVD. APK debug mới có SHA256 8B4E47A9E60D6153CC3B97F244C45771AD0108BA0C1CA785F72332DA31FE38F6; test APK F85C7D2ADE771570018623D4BD9163AB358D9B4A2530697FEA2F8D484FAB9872.

## 2026-11-02 — Nâng toolchain thực trên JDK 25

**Host:** Windows 10.0.19045 / PowerShell 7.6.9; nhánh `chore/modernize-android-build`, HEAD nền `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`, thay đổi chưa commit. Người dùng cho nâng bản stable mới nhất nhưng xác nhận giữ minSdk 21 và references read-only.

| Thành phần | Phiên bản cấu hình/kiểm chứng mới |
|---|---|
| JDK chạy wrapper/daemon/compiler | Oracle 25.0.5.0.3; JAVA_HOME Machine hợp lệ `C:\Program Files\Java\latest\jdk-25`, resolved daemon `jdk-25.0.5.0.3` |
| Gradle |9.8.2; wrapper --version PASS trên JVM 25; tạo lại scripts/JAR bằng wrapper task chính thức |
| AGP |9.4.1, đã chạy plugin resolve/configure và build debug |
| SDK |compile 37.2 (`minorApiLevel=2`), target 37, min 21 |
| Build-Tools |37.0.0, đọc source.properties để xác nhận |
| NDK |30.0.16248370 / r30; meta/platforms.json min 21/max 37; đã build debug cho 4 ABI |
| CMake |4.1.2 từ Android SDK; CMakeLists minimum 3.22.1 để không dùng policy cũ bị CMake 4 bỏ |
| Java source/target |1.8 (không phải 25); javac 25 báo obsolete warning, chưa ẩn cảnh báo |
| AppCompat / Material |1.7.1 / 1.13.0: giữ API 21; bản latest 1.8.0/1.14.0 yêu cầu 23 nên không chọn |
| Unit tests |JUnit 4.13.2, Mockito 5.24.0; bỏ Mockito 1/mockito-all/PowerMock |
| Instrumentation dependencies |AndroidX test-ext 1.3.0 / Espresso 3.7.0, bản stable mới nhất còn hỗ trợ API 21 |
| Broadcast dependency |localbroadcastmanager 1.1.0 khai báo trực tiếp trong dvbservice thay vì dựa vào dependency bắc cầu |

Mọi lệnh chạy Gradle đều đặt JAVA_HOME từ Machine và ANDROID_HOME=D:\Android\Sdk/ANDROID_AVD_HOME=D:\Android\AVD **trong process đó**, không thay registry/PATH toàn cục. VS Code task assembleDebug cập nhật cùng cơ chế; runTask không tìm thấy task trong host nên terminal chạy cùng lệnh.

**Cài package có giới hạn:** sau khi thay đổi khai báo build và bước xác minh `gradlew help` FAIL vì NDK chưa cấu hình, đã chạy `android --sdk=D:\Android\Sdk sdk install <package>` cho đúng 4 package: ndk/30.0.16248370, cmake/4.1.2, build-tools/37.0.0, platforms/android-37.2; tất cả exit 0 và source.properties được đọc lại. Không update toàn SDK, không tải emulator/system images/firmware, không tạo SDK thứ hai. `android.builder.sdkDownload=false` ngăn build tự cài SDK khác.

**Tính toàn vẹn wrapper:** distributionSha256Sum của 9.8.2=`21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`, lấy từ nguồn chính thức downloads.gradle.org. Cài binary-only distribution thay vì all. Không chép JDK path cá nhân vào build scripts. Package 37.2 có metadata Platform.Version dùng placeholder `${PLATFORM_VERSION}` nhưng AndroidVersion.ApiLevel 37.2/PreviewSdkInt 0; compile dùng được, không suy diễn tên Android từ placeholder.

**Không đổi quyền sở hữu hardware/reference:** không chạy script Apply khôi phục TBS, không tải 572 ancestor objects. C/Go vẫn sạch, SHA cả 3 reference giữ nguyên, TBS vẫn có 81399 D. Mọi USB/lock/TS thật và runtime Android/AVD vẫn NOT RUN. Kết quả build/test cuối xem TEST-MATRIX/WORKLOG, không dùng snapshot Phase 0 bên dưới làm hiện trạng mới.

## 2026-11-02 12:52 +07 — Kiểm tra lại JAVA_HOME sau khi người dùng sửa

Windows PowerShell / main HEAD `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`.

- **Machine JAVA_HOME đã đúng:** `C:\Program Files\Java\latest\jdk-25`; `Test-Path` thư mục và `bin\java.exe` đều true; chạy executable này trả Java `25.0.5.0.3`, exit 0.
- **Process agent vẫn giữ giá trị cũ:** `C:\Program Files\Java\jdk-25`, không tồn tại; User JAVA_HOME không có. Đây là môi trường được tiến trình cha truyền lại, không phải người dùng sửa thất bại.
- Đã kiểm tra trong một PowerShell child bằng `$env:JAVA_HOME=[Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')`, rồi chạy `bin\java.exe -version`: PASS. Thay đổi chỉ tồn tại trong child đó, không sửa User/Machine hoặc tiến trình VS Code đang chạy.
- Các lệnh sau nếu cần JDK này phải đọc lại Machine JAVA_HOME trong chính process chạy lệnh; hoặc người dùng khởi động lại VS Code để nhận môi trường mới. **Không tự khởi động lại VS Code hoặc thay cài đặt toàn cục.**
- ANDROID_HOME/ANDROID_AVD_HOME vẫn thiếu Process nhưng đúng ở User (`D:\Android\Sdk`, `D:\Android\AVD`). Chưa đổi biến.
- **Giới hạn vẫn còn:** JDK 25 hợp lệ không làm Gradle 8.7 tương thích. Chưa chạy wrapper/build; đề xuất baseline JDK 17 vẫn giữ nguyên.

## 2026-11-02 — Kiểm chứng trực tiếp trong Phase 0

**Môi trường chạy lệnh:** Windows PowerShell, cwd `D:\API\TBS5530-DVB-Android\AndroidDvbDriver`; thời gian local UTC+07. HEAD Android `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`. Không chạy Gradle, cài SDK, khởi động AVD hoặc thao tác ghi USB.

### Windows, Java và biến môi trường

| Hạng mục | Kết quả thực đo | Ý nghĩa |
|---|---|---|
| Windows | `10.0.19045.0`; ADB cũng báo Windows 10.0.19045 | Xác nhận host, không đánh giá vòng đời hỗ trợ Windows |
| PowerShell | `7.6.9` | Shell audit chính |
| Git | `2.56.0.windows.3` | Không chạy sync script |
| Java trên PATH | Oracle `25.0.5.0.3`, build `25.0.5.0.3+1-LTS-2` | `java -version` chạy được |
| `JAVA_HOME`, Process/Machine | `C:\Program Files\Java\jdk-25`, **không tồn tại** | Chặn Gradle Wrapper trước cả vấn đề tương thích phiên bản |
| JDK thực có | `C:\Program Files\Java\jdk-25.0.5.0.3\bin\java.exe` | Chưa thay `JAVA_HOME` |
| `ANDROID_HOME`, Process | Không có | Tiến trình agent chưa nhận giá trị User |
| `ANDROID_HOME`, User | `D:\Android\Sdk` | Không tự sửa biến global |
| `ANDROID_AVD_HOME`, Process | Không có | MCP/CLI có thể nhìn nhầm thư mục AVD |
| `ANDROID_AVD_HOME`, User | `D:\Android\AVD` | Thư mục tồn tại, hiện rỗng |
| Android CLI | `1.1.16717390`, tìm được qua WinGet link | `--help`, `-V`, `info sdk`, `sdk list`, `docs search/fetch` chạy được |
| `android info sdk` | `C:\Users\anhch\AppData\Local\Android\Sdk` | Default của process không trùng SDK dự kiến; chưa kết luận có SDK thứ hai đã cài |
| `android --sdk=D:\Android\Sdk info sdk` | `D:\Android\Sdk` | Truy vấn rõ SDK hoạt động; không đổi cấu hình |

Lệnh kiểm chứng: `Get-Command java,android,git,usbipd,wsl.exe`, `[Environment]::GetEnvironmentVariable(<name>,<Process/User/Machine>)`, `Test-Path $env:JAVA_HOME`, `Get-ChildItem 'C:\Program Files\Java'`, `java -version`. Đã phân biệt Java tìm qua PATH với `JAVA_HOME` mà wrapper sử dụng.

### SDK tại D:\Android\Sdk

Lệnh `android --sdk=D:\Android\Sdk sdk list` trả exit 0. Đọc thêm `source.properties` để không chỉ dựa vào tên thư mục.

| Package | Phiên bản xác nhận |
|---|---|
| `platforms/android-34` | revision 3; API 34, Android 14 |
| `platforms/android-37.0` | revision 2; metadata API `37.0`, Android 17, `PreviewSdkInt=0` |
| `build-tools/36.0.0` | 36.0.0 |
| `ndk/28.2.13676358` | r28c, 28.2.13676358 |
| `cmake/3.22.1` | 3.22.1 |
| `cmdline-tools/latest` | 23.0.0 |
| `platform-tools` | 37.0.1; `adb.exe --version`: 1.0.41 / 37.0.1-15733141 |
| `emulator` | 37.2.13.0, build 16740729; `emulator.exe -version` |
| System images | API 34 Google TV x86 revision 3; API 37.0 Google APIs x86_64 revision 8 |
| AVD | `D:\Android\AVD` rỗng; default `.android\avd` không tồn tại; MCP `list_avds`: không có AVD |

Có system image **không** có nghĩa đã tạo/boot AVD. Lần thăm dò `emulator.exe --version` in phiên bản kèm lỗi `No AVD specified`; đã sửa thành truy vấn `-version`, không truyền tên AVD, không khởi chạy máy ảo. Android CLI in cảnh báo `UseAllWindowsProcessorGroups` không hỗ trợ trên Windows này nhưng các truy vấn trên vẫn hoàn tất. `android docs search` tự tải bộ chỉ mục Knowledge Base/cache; đây không phải cài/nâng SDK, không gửi mã dự án.

### WSL2, usbipd và quyền sở hữu USB

- `wsl.exe --list --verbose`: `Ubuntu-22.04`, **Running**, version **2**; không chạy lệnh bên trong WSL vì TBS không Attached.
- `usbipd` không có trên PATH của process; đã dùng executable hiện có `C:\Program Files\usbipd-win\usbipd.exe`, không cài lại hoặc sửa PATH.
- `usbipd.exe --version`: `5.3.0-54+Branch.master`, commit `aa3db8b82c4cb5071fd31bc54211606c70886912`.
- `usbipd.exe list`: `734c:5530`, `TBS 5530 DVB BDA`, BUSID **6-2 tại thời điểm kiểm tra**, trạng thái **Shared**, không phải Attached. BUSID được đọc trực tiếp, không lấy từ lịch sử.
- Quyền truy cập ở phía Windows, không chuyển cho WSL2; chưa xác minh ứng dụng Windows nào đang mở thiết bị. `Shared` không chứng minh BDA/tuner đang hoạt động.
- `bind`: chia sẻ bền vững (Administrator); `attach --wsl`: giao tạm cho WSL; `detach`: trả Windows nhưng giữ Shared; `unbind`: bỏ chia sẻ. **Không thực hiện bốn lệnh này**, USB reset, firmware, vendor/I2C write hoặc LNB.
- Kernel WSL và endpoint/claim PyUSB trong phần 2026-11-01 bên dưới là **bằng chứng lịch sử**, chưa đo lại.

### Công cụ Copilot/MCP

- Đã đọc `.github/copilot-instructions.md`, cả 6 scoped instructions, 2 SKILL.md, 6 hồ sơ dự án, workspace, script sync và script check-env (không chạy hai script).
- Hai project skills `project-handoff`, `tbs-research` và skill user `android-cli` được tool `skill` tải thành công. Instructions áp dụng docs/build/sync xuất hiện trong context phiên; cả 6 file đã được chủ động đọc. Không có file yêu cầu nào bị thiếu. Không có API kiểm chứng việc VS Code tự động ghép *mọi* scoped instruction ở mỗi lượt; không tuyên bố auto-attach đã được kiểm thử toàn diện.
- Chỉ qua MCP Proxy: `retrieve_tools` + `call_tool_read` hoạt động. `adb-mcp:list_avds` trả không có AVD; chưa kiểm thử UI, APK, Logcat hoặc thiết bị Android vật lý.
- `gitnexus:list_repos` hoạt động nhưng **không có chỉ mục AndroidDvbDriver hoặc ba reference**. Không rebuild index; nghiên cứu dùng source trực tiếp.
- `agentmemory:memory_recall`, query `TBS5530 AndroidDvbDriver`, compact/limit 3: `matched_count=0`; không suy ra server rỗng hoặc tìm kiếm đã cô lập project. Không lưu memory mới.
- Truy vấn lịch sử phiên 7 ngày theo identity GitHub dự án không trả dòng nào. Tài liệu trên đĩa là nguồn lịch sử chính.
- Chưa kiểm tra `chrome-devtools`: không cần browser/UI cho audit source. Không sửa MCP config hoặc global settings.

## Kiểm kê lịch sử — quan sát ngày 2026-11-01 (giữ nguyên bằng chứng)

Phần dưới là ghi chép người dùng cung cấp trước Phase 0, không thay cho các kết quả kiểm chứng trực tiếp ở trên.

## Windows / Android

| Hạng mục | Quan sát |
|---|---|
| Windows host | Windows 10 `10.0.19045` (theo output ADB; cần kiểm tra lại hiện trạng) |
| Root dự án chính | `D:\API\TBS5530-DVB-Android\AndroidDvbDriver` (dự kiến; cần kiểm tra clone thực) |
| Root SDK | `D:\Android\Sdk` |
| Android CLI | Google `android` CLI chính thức; chưa ghi phiên bản (chạy `android -V`) |
| Java | Oracle JDK `25.0.5` LTS; `JAVA_HOME=C:\Program Files\Java\jdk-25` |
| Build-Tools | `36.0.0` |
| SDK platforms | `android-34`, `android-37.0` |
| NDK | `28.2.13676358` |
| CMake | `3.22.1` |
| Platform-Tools / ADB | `37.0.1` |
| cmdline-tools | `23.0.0` (`latest`) |
| Emulator | `37.2.13`; WHPX được báo đã cài và dùng được; chưa có bằng chứng AVD khởi động thực |
| Emulator images | `android-34/google-tv/x86`; `android-37.0/google_apis/x86_64` |

Dùng `android --sdk=D:\Android\Sdk sdk list` để kiểm kê; dùng `android info sdk` và `android -V` để kiểm tra SDK đang dùng và phiên bản CLI. Không mặc định package là mới nhất chỉ từ tên thư mục. Cập nhật SDK cần được phê duyệt.

## WSL2 / USB

| Hạng mục | Quan sát |
|---|---|
| Distro | `Ubuntu-22.04`, WSL2 |
| Kernel | `6.18.54.1-microsoft-standard-WSL2` |
| USB/IP | `usbipd-win`, thiết bị TBS có BUSID `6-2` lúc kiểm thử; BUSID có thể thay đổi |
| USB ID | `734c:5530` |
| Configuration / Interface | `1` / `0` |
| Bulk input endpoints | `0x82`, `0x81`; mỗi endpoint có max packet size 512 byte |
| Tốc độ | USB 2.0 High Speed / 480 Mbps |
| USB enumeration / interface claim | PASS trên WSL2/PyUSB; không có DVB driver gắn với thiết bị |
| `/dev/dvb` | Không tồn tại trong lần kiểm thử WSL, phù hợp khi không có DVB driver |

Cùng các file dự án có thể được truy cập qua `/mnt/d/API/TBS5530-DVB-Android/AndroidDvbDriver`. Các checkout tham khảo dưới thư mục cùng cấp `references/` phải giữ read-only.

## Ranh giới sử dụng công cụ

- Windows PowerShell: build Android bằng `gradlew.bat`, Android CLI, ADB, Git.
- WSL2: `lsusb`, PyUSB, chẩn đoán chỉ dành cho Linux. Gọi từ Windows bằng `wsl.exe -d Ubuntu-22.04 -- ...`.
- Không có Android SDK thứ hai trong WSL2. Build Android ban đầu không cần Linux kernel driver/nạp firmware.
- Khi TBS5530 đang được chuyển tiếp USB, không thể đồng thời dùng driver BDA trên Windows.

## Baseline dự án (chỉ đọc source, chưa xác minh build local)

AndroidDvbDriver upstream gốc dùng AGP `8.5.2`, Gradle wrapper `8.7`, `compileSdk 34`; không được mặc định JDK 25 tương thích với wrapper cũ này. Hiện đại hóa là bước riêng được theo dõi sau khi chốt baseline source và kiểm kê dependencies. Không khẳng định `assembleDebug` đạt nếu chưa thực thi trên máy local.
