# Port TBS5530 sang Android — trạng thái hiện tại

## Checkpoint phát hành Phase 2.5 — 2026-11-03 22:43 +07

- Người dùng yêu cầu commit và push 14 file còn lại: 8 file mã nguồn/tests Phase 2.5 và 6 hồ sơ đã biên tập. Nhánh `chore/modernize-android-build`; preflight xác nhận local và origin cùng `dc50e7304941a7d01aa1d490c13fcf09683a01c9`, stage trống. Wireless ADB đã xác minh đúng GM1900/API 30, không thao tác USB.
- Message: `feat: add permission-only USB preflight and private diagnostic reports`. Hash và kết quả push được xác minh từ Git sau thao tác; các mục “chưa commit/push” bên dưới là lịch sử trước checkpoint.
- Không chạy lại build/tests trong tác vụ Git. Bằng chứng gần nhất vẫn là 180 unit methods, 44 instrumentation tests trên OnePlus/API 30, debug/release build và lint 0 errors/11 warnings. Connection cycle thật vẫn BLOCKED bởi profile thiếu IN82; không firmware hoặc vendor requests.

## Checkpoint biên tập tài liệu — 2026-11-03 22:26 +07

Chỉ rà soát và sửa cách diễn đạt trong STATUS, ENVIRONMENT, DECISIONS và TEST-MATRIX: tách từ bị dính, làm rõ câu văn và bảng, giữ nguyên bằng chứng lịch sử. Trạng thái hiện tại vẫn là Phase 2.5 trên nhánh `chore/modernize-android-build`, HEAD `dc50e7304941a7d01aa1d490c13fcf09683a01c9`. Không chạy lại kiểm thử hoặc build, không thao tác phần cứng; các kết quả bên dưới là bằng chứng đã ghi trước đợt biên tập. Xác minh tổng thể hoàn tất lúc 22:39 +07: UTF-8, liên kết, bảng, SHA, lệnh và số liệu lịch sử được giữ nguyên; 8 file mã nguồn/test không thay đổi trong đợt biên tập. Đã dừng khi Wireless ADB mất kết nối và chỉ tiếp tục kiểm cuối sau khi người dùng xác nhận nối lại; không chạy lại build/tests hoặc thao tác USB.

## Phase 2.5 — OnePlus 7 nhận USB thật; chu kỳ kết nối bị chặn bởi mô tả thiết bị, 2026-11-03 22:16 +07

**Đã kiểm USB Host và metadata (thông tin mô tả thiết bị) trên OnePlus 7 GM1900, Android 11/API 30. Chưa có bằng chứng open/claim hoặc DVB hoạt động.** Nhánh `chore/modernize-android-build`, HEAD `dc50e7304941a7d01aa1d490c13fcf09683a01c9`; Phase 2.4B/ADR-015 đã push tại baseline này. Có 8 file mã nguồn/kiểm thử và 6 hồ sơ sửa, chưa stage/commit/push/merge.

- **Wireless ADB (ADB không dây):** lần đầu offline nên dừng ngay. Sau khi người dùng xác nhận nối lại, tra port mới, xác minh GM1900/API 30/state=device và chỉ chọn một serial tường minh. Các lượt build/kiểm thử dài đều kiểm kết nối mỗi 10 giây; không chuyển sang emulator hoặc tự ghép nối lại.
- **USB thật:** USB Host=true; TBS 734c:5530 có một cấu hình với ID 1 và bốn mục cùng interface ID 0, alternate 0–3. Alternate 0 rỗng; alternate 1 có Bulk 01/81/02/04/86/88. Alternate 2/3 có Interrupt/Isochronous như bảng [RESEARCH.md](RESEARCH.md). **Không alternate nào có IN 82**, khác snapshot WSL lịch sử; không tự đổi endpoint, firmware hoặc alternate.
- **Quyền truy cập USB:** hộp thoại hệ thống thật đã hiện, người dùng tự chọn OK. Khi đọc lại, lượt đầu đã hết thời hạn 30 giây và receiver đã được dọn; callback count=0, open/claim=0. Sau khi làm mới, hasPermission=true; lượt chỉ yêu cầu quyền mới xác nhận quyền đã có sẵn và trạng thái PERMISSION_GRANTED trước khi dọn tài nguyên. Không tuyên bố callback cấp/từ chối quyền thật PASS. Sau UTP cài/gỡ kiểm thử, APK cuối được cài lại và metadata hiện permission=false; không tự yêu cầu lại hộp thoại.
- **An toàn khi nhận connection và claim interface:** công cụ chẩn đoán chỉ bật chu kỳ khi metadata khớp profile (bộ điều kiện kiểm tra) mặc định; profile OnePlus hiện bị BLOCKED trước open. Nút chỉ yêu cầu quyền dùng cùng binding/coordinator và bảo đảm việc cấp quyền không dẫn đến nhận connection hoặc claim interface. Không gọi setInterface, setConfiguration, getRawDescriptors, controlTransfer, I²C, bulk, firmware hoặc reset.
- **Báo cáo:** JSON chỉ có trong debug, tại `files/tbs5530-diagnostic.json`, đọc qua run-as; có timestamp/operation/generation, model/API, đầy đủ descriptors, permission/correlation count, state trước cleanup, counters/thread/duration/events và lý do chặn. Đây là các trường về thời điểm, thao tác, phiên, mô tả thiết bị, quyền, trạng thái và bộ đếm chẩn đoán. Không ghi path/device ID/serial/credentials. API 30 được kiểm thật, không nhầm compile 37.2 thành runtime 37.
- **PASS mới:** **180 phương thức unit test** (drivers 166/usbxfer 1/dvbservice 13); **44 instrumentation tests** trên GM1900/API 30 (app 14 + drivers 28 + dvbservice 2), 0 failure/error/skipped. Debug/release APK và lint PASS: 0 errors/0 fatal/11 warnings; Java 8, bytecode 52; manifest/DEX của release không chứa công cụ chẩn đoán. Lượt cuối 4m32s/418 tasks: 36 executed, 382 up-to-date, với việc kiểm tra kết nối xuyên suốt.

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
- **PASS thực:** **177 phương thức kiểm thử đơn vị** (drivers 163/usbxfer 1/dvbservice 13), 0 failure/error; **30 instrumentation tests** (28 facade/control + 2 native/registry) trên AVD API 37/x86_64, 0 failure/error/skipped. APK debug PASS; lint 0 Error/0 Fatal/11 Warning. Lượt tổng hợp exit 0/8m8s/241 tasks: 50 executed, 191 up-to-date. Core Java 8/bytecode 52 và 5 lượt × 24 transport/concurrency tests đều PASS.
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
- **PASS thực:** **126 phương thức kiểm thử đơn vị** (drivers 112/usbxfer 1/dvbservice 13), 0 failures/errors; APK debug PASS; lint 0 errors/0 fatal/11 warnings. Lượt Gradle cuối: 2m23s/177 tasks, 41 executed, 136 up-to-date. 10 phương thức kiểm thử đồng thời chạy độc lập 5 lượt, cả 5 PASS, gồm 200 vòng detach/cancel có điều phối bằng latch. Core/tests compile `javac --release 8`, bytecode 52. Xem [TEST-MATRIX.md](TEST-MATRIX.md) và [WORKLOG.md](WORKLOG.md).
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
- **PASS thực:** 52 phương thức kiểm thử mới (Bridge 17/Firmware 19/Session 16), tổng **83 phương thức** trong drivers 69/usbxfer 1/dvbservice 13, không có failure/error; APK debug và lintDebug **0 errors, 11 warnings**. Lượt Gradle cuối exit 0, 1m45s, 177 tasks: 41 executed/136 up-to-date. Bốn core class compile riêng bằng `javac --release 8`, class version 52; không cần Android SDK/JNI trên classpath. Chi tiết lệnh và kết quả ở [WORKLOG.md](WORKLOG.md), [TEST-MATRIX.md](TEST-MATRIX.md).
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
- **Build/lint/hồi quy:** build APK debug + test PASS; assembleDebug/lintDebug sau khi sửa PASS, lint 0 error/11 warnings; xác minh unit task PASS/32s, 39 UP-TO-DATE (không gọi là chạy lại 31 test). Toolchain giữ Gradle 9.8.2/AGP 9.4.1/JDK 25/compile 37.2/target 37/min 21/NDK 30/CMake 4.1.2/BT 37.
- **Workspace/instructions:** ngoại lệ `!TBS5530.code-workspace` và file workspace đã có local trước Phase 1.1, giữ nguyên byte/hash; 4 root dùng đường dẫn tương đối đều phân giải đúng, file untracked được phép add (chưa stage). Cập nhật 2 file hướng dẫn bằng tiếng Anh để bỏ snapshot toolchain cũ; giữ chính sách Windows/WSL/usbipd/MCP.
- **Kiểm lại reference chỉ đọc:** SHA TBS 2ce787de9f6e81b4294692a45de15596ae2e1322, C c5fa27eecbe80633cbb365cc44656daea1c0e2e3, Go 11228b05fb1626bf41c4e1879ace10cd0a8d575c không đổi. TBS vẫn thiếu index/81399 D và thiếu 3 file source bắt buộc; C/Go sạch. Không sync/reset/restore hoặc chạy lệnh phần cứng.

**NOT RUN:** DvbService foreground/streaming (không có tuner/fixture nên không vào luồng hợp lệ), USB permission dialog, USB bulk/I2C/firmware/lock/TS; runtime API 21/arm64/16 KiB. Phase 1.1 không cho phép kết luận TBS5530 hoạt động.

**Một bước an toàn tiếp theo:** review/commit có chọn lọc diff Phase 1.1 khi người dùng cho phép; nếu cần tăng phạm vi kiểm thử runtime, lên kế hoạch test API 21 hoặc arm64/16 KiB riêng, không tải image/thao tác hardware ngoài phê duyệt. **Dừng Phase 1.1, không tự port driver.** Kết quả/lệnh/hash ở [WORKLOG.md](WORKLOG.md), [TEST-MATRIX.md](TEST-MATRIX.md), môi trường ở [ENVIRONMENT.md](ENVIRONMENT.md), ADR-008 trong [DECISIONS.md](DECISIONS.md).

## Checkpoint Git — 2026-11-02 18:48 +07

- Người dùng yêu cầu tạo commit cho các thay đổi nâng build và bộ hồ sơ/công cụ TBS5530. Message chọn: `chore: prepare TBS5530 development with JDK 25`.
- Nhánh `chore/modernize-android-build`; origin hiện trỏ fork `trankien84/AndroidDvbDriver` qua SSH, upstream giữ `signalwareltd/AndroidDvbDriver`. Khác origin lúc audit đầu vì cấu hình đã đổi ngoài tác vụ này; không tự sửa remotes.
- Kiểm trước commit: lệnh build unit/debug exit 0/59s, 107 tasks (8 executed/99 UP-TO-DATE); unit tasks dùng kết quả còn hợp lệ, không gọi là chạy lại toàn bộ phương thức kiểm thử. Báo cáo 31 methods, 0 fail và lint 0 errors/11 warnings từ migration vẫn là bằng chứng gần nhất.
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
