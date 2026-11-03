# TBS5530 test matrix

## 2026-11-04 00:06 +07 — Phase 2.6: raw descriptor chỉ đọc, OnePlus/API30

Baseline đã push `df076d28dd8100d5caa363f1d866a274e3f2b636`, nhánh `chore/modernize-android-build`, cộng thay đổi working tree chưa commit. 180/44 của Phase2.5 giữ nguyên lịch sử bên dưới, không chuyển thành kết quả mới. Runtime GM1900/Android11/API30/arm64, Wireless ADB serial TCP tường minh, không emulator.

| Gate | Kết quả mới | Bằng chứng / giới hạn |
|---|---|---|
| Wireless guard | PASS theo phạm vi; một lần STOP | Serial cũ mất khi sửa code: dừng, chờ xác nhận, tra serial mới, Host/attachment/permission/app owner mới; không tự đổi target. Build dài kiểm mỗi10s |
| Parser JVM debug | PASS7 methods | Null/empty/device-only, bLength0/1/standard quá ngắn, wTotalLength nhỏ/vượt data, mọi điểm cắt tree, nhiều alternate/một interface, hướng02 OUT khác82 IN, không82 vẫn parse, duplicate/count/unknown payload/redaction/immutable/size cap |
| Raw lifecycle JVM debug | PASS12 methods | Deny/missing/stale trước open; detach trong open/read; permission mất; open null/exception; read/close exception; cleanup một lần; deadline/owner close không force-close; không retry; events khác attachment |
| Production regression | PASS | `drivers/src/main` không đổi; test profile vẫn từ chối thiếu82. Capability raw không có claim/vendor/control/bulk API |
| TBS JVM toàn bộ | PASS168 methods | Gồm149 cũ và19 mới; không coi fake transport là hardware PASS |
| JVM toàn bộ module | PASS199 methods | drivers185/usbxfer1/dvbservice13;0 failures/errors; từng test task dùng `--rerun` ở lượt cuối |
| Instrumentation diagnostic | PASS17 tests | OnePlusAPI30, fake raw port, worker khác main,0 production open/claim/transfer, block cùng owner và giữa hai owner; giữ14 tests cũ |
| Instrumentation facade | PASS28 tests | Android/API30, fake Manager/raw transport; không truy cập tuner |
| Native smoke/XML | PASS2 tests | `NativeLibrarySmokeTest` load trên arm64; `DeviceFilterXmlEquivalenceTester`1; không ioctl USB |
| Tổng instrumentation | PASS47 tests | App17+drivers28+dvbservice2;0 failures/errors/skipped; connected tasks thực thi lại sau raw cycle |
| Debug/release APK | PASS | Mã cuối trước USB:2m55s/419 tasks,38 executed/381 up-to-date; sau USB:2m2s/419 tasks,23 executed/396 up-to-date |
| Lint | PASS0 errors/0 fatal/9 warnings | XML lint9.5.0 trên mã cuối; lượt sau USB task chạy lại, analysis/report up-to-date của cùng source. Không gọi11 warnings lịch sử là kết quả mới |
| Java8 | PASS |10 core/debug JVM source compile bằng `javac --release 8`; `javap` parser major52. Main build source/target8/min21 không đổi |
| Debug-only isolation | PASS | DEX debug có6 class diagnostic; release DEX không6 class; release manifest không Activity. Không thay registry/XML production |
| USB Host/metadata mới | PASS |734c:5530/configvalue1/alt0–3 trước raw; metadata-only không open |
| Permission callback thật | PASS tại lượt mới |Người dùng tự Allow;operation1/gen1;callback1/correlation=true;PERMISSION_GRANTED trước cleanup,CLOSED sau;open0/claim0 |
| Raw cycle thật, được duyệt1 lần | PASS read-only |Operation2/gen2;open/read/close1/1;13ms worker;permission/identity=true;failureNONE;pending/cleanup=false;0claim/release/transfer |
| Raw tree thực trả về | PASS parse phạm vi189bytes |SHA256 `e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`;device18+config171;bNumInterfaces1/alt0–3;issues0 |
| Raw so metadata | PASS18/18 endpoints |Đối chiếu ID/alternate/address/direction/type/packet/interval;khôngIN82. Không chứng minh cache bằng descriptor live trên bus |
| TBS ChangeMode | Source/static research |Trang official+ZIP directory+strings trong RAM,không chạy EXE;chưa biết protocol/descriptors/rollback;không hardware PASS |
| Trạng thái cuối sau UTP/cài lại | PASS metadata |Debug APK cuối cold launch,generation0/DETECTED,pending=false,open0/claim0;permission=false.Không xin lại hoặc chạy raw lần2 |
| Streaming readiness/active alternate/firmware state | BLOCKED/UNKNOWN |Không82,khôngGET_INTERFACE/GET_CONFIGURATION;không suy firmware cold/warm hoặc88=TS |
| Firmware/CPU/vendor/I²C/bulk/tuner/TS/ChangeMode execution/reset | NOT RUN |Ngoài phê duyệt;không tự bắt đầu phase kế tiếp |

Lượt focused đầu:19 methods chạy,18PASS/1FAIL do fixture duplicate endpoint sửa nhầm byte attributes (offset62),không phải parser lỗi. Sửa fixture sang address offset61;chạy lại19/19PASS rồi full regression. Chưa phát hiện bug runtime Android ở raw cycle;không nới profile để đổi BLOCKED thành PASS. Chi tiết lệnh và kết quả ở [WORKLOG.md](WORKLOG.md),raw fields/nguồn/giả thuyết ở [RESEARCH.md](RESEARCH.md),quyền sở hữu ở [ADR-017](DECISIONS.md).

## 2026-11-03 22:16 +07 — Phase 2.5: OnePlus 7/API 30, USB thật có giới hạn

Baseline dc50e7304941a7d01aa1d490c13fcf09683a01c9/chore/modernize-android-build, cộng các thay đổi chưa commit. OnePlus GM1900/Android 11/API 30, chỉ định rõ thiết bị qua Wireless ADB (ADB không dây), kết nối OTG trực tiếp. OxygenOS 11.0.9.1.GM57AA do người dùng cung cấp; thuộc tính build/OTA đã ghi trong ENVIRONMENT. Không dùng emulator hoặc chuyển tiếp USB từ Windows.

| Gate thiết bị thật | Kết quả | Bằng chứng |
|---|---|---|
| Kiểm tra kết nối ban đầu | STOP đúng yêu cầu | ADB offline, chưa sửa mã/chạy build/truy cập USB; chỉ tiếp tục sau khi người dùng xác nhận đã nối lại |
| Thiết bị đích sau khi nối lại | PASS | manufacturer OnePlus/model GM1900/API 30/state=device, port mới; không dùng alias mDNS làm thiết bị đích khác |
| Android USB Host/OTG | PASS | feature true, UsbManager và JSON nhận 734c5530; không sửa thiết lập OTG |
| Cấu hình/interface/endpoint | PASS metadata | Một cấu hình có ID 1, interface có ID 0/alternate 0–3; alternate 0 rỗng; alternate 1 có Bulk 01/81/02/04/86/88; không alternate nào có 82, thông tin packet/interval ở RESEARCH |
| Profile an toàn để nhận connection và claim interface | BLOCKED đúng guard | Profile mặc định dùng interface 0/alternate 0 thiếu 82; không gọi setInterface/setConfiguration hoặc thay 88 cho 82 |
| Hộp thoại USB thật | PASS xuất hiện | Ứng dụng yêu cầu quyền, người dùng tự chọn OK; agent không bấm Allow hoặc dùng ADB để vượt bước cấp quyền |
| Callback cấp quyền thực | NOT VERIFIED | Đọc lại sau deadline 30s, callback count 0/correlation false/cleanup đã xong; không gọi kết quả giả lập là PASS thật |
| Quyền hiện hành sau OK | PASS tại thời điểm đọc | Làm mới JSON cho thấy hasPermission true; lượt chỉ xin quyền sau đó ghi nhận quyền đã có sẵn (pre-granted)/state PERMISSION_GRANTED trước cleanup |
| Quyền cuối sau UTP/cài lại APK | PASS quan sát | Metadata cuối ghi permission false; không yêu cầu lại để ép hộp thoại thành PASS |
| Từ chối quyền thật | NOT RUN | Không chủ động thu hồi quyền hoặc bấm Hủy |
| Real open/claim/release/close | BLOCKED/NOT RUN | Không có profile an toàn, toàn bộ counters thực đều là 0; không đọc raw descriptors |
| Rút thiết bị thật/hủy USB handle | NOT RUN | Không có chu kỳ kết nối, không rút cáp hoặc gây treo I/O |
| Firmware/vendor/I²C/reset/bulk/tune/TS | NOT RUN | Không thực hiện, ngoài phase |

| Gate phần mềm | Kết quả thực | Bằng chứng |
|---|---|---|
| Binding chỉ yêu cầu quyền | PASS 2 phương thức mới | Grant/deny/pre-granted đều có 0 lần open, callback trùng không nhận connection hoặc claim interface, không cấp control handle |
| Dữ liệu descriptor OnePlus dùng cho kiểm thử | PASS 1 phương thức mới | Alternate 0 rỗng và alternate 1–3 không có 82 đều bị từ chối; không suy ra 88=TS |
| JVM toàn bộ module | PASS 180 phương thức, 0 failure/error | drivers 166/usbxfer 1/dvbservice 13, từng task dùng --rerun |
| Instrumentation chẩn đoán debug trên API 30 | PASS 14 tests | 12 tests baseline + kiểm JSON/redaction/persistence của chế độ chỉ yêu cầu quyền và việc chặn profile không an toàn; các cổng giả lập không thực hiện I/O phần cứng |
| Instrumentation facade/control trên API 30 | PASS 28 tests | Nhánh API 30/receiver/worker/control giả lập; không thay thế bằng chứng callback USB thật |
| Kiểm tra nhanh native/XML trên API 30 | PASS 2 tests | Load UsbXfer trên runtime arm64 và kiểm registry tương đương XML, không gọi USB ioctl |
| Tổng instrumentation | PASS 44 tests, 0 failure/error/skipped | XML app 14 + drivers 28 + dvbservice 2, thiết bị đích GM1900/Android 11 |
| Debug/release/lint | PASS | Lượt Gradle cuối: 4m32s/418 tasks, 36 executed, 382 up-to-date; lint 0 Error/0 Fatal/11 Warning |
| Java 8/tách diagnostic khỏi release | PASS | javac --release8/major 52; manifest/DEX của release không chứa Activity debug/exporter |
| Đọc báo cáo có cấu trúc | PASS | run-as đọc JSON do AtomicFile ghi, không cần root; có timestamp/operation/generation/metadata/events, không có path/id/serial |

Lượt chạy đầy đủ trước lượt cuối có 179 phương thức unit test/44 instrumentation tests PASS; sau khi bổ sung dữ liệu kiểm thử và events trong JSON, đã chạy lại đủ số lượng cuối 180/44, không dùng số baseline 177/42 làm kết quả mới. Chi tiết lệnh, kiểm tra kết nối và mốc xin quyền thật ở [WORKLOG.md](WORKLOG.md); bảng descriptor và giới hạn ở [RESEARCH.md](RESEARCH.md).

## 2026-11-03 16:51 +07 — Phase 2.4B: QEMU hardware attempt và diagnostic

Windows 10.0.19045/PowerShell; chore/modernize-android-build/c153e05e454f506f5c6639685d2cded51e8c0126 cộng thay đổi trong working tree. Emulator 37.2.13/build 16740729, QEMU 2.12.0-19097-g85fa07f04ef/WHPX; image 37.0 Google APIs rev 7/API 37/x86_64. Phase này có thử chuyển tiếp USB từ host (passthrough), nhưng chưa chạy permission/connection Android thật; tách bảng này khỏi kết quả fake PASS.

| Tầng hardware/emulator | Kết quả | Evidence |
|---|---|---|
| Windows PnP/driver | PASS nhận diện | 734c:5530/TBS5530DVBBDA/OK, driver TBS có chữ ký 2.0.0.2/tbsBDA.TBS5530/oem108.inf |
| Quyền sở hữu trên Windows | PASS điều kiện thử | usbipd 5.3.0, BUSID tra trực tiếp là 6-2, Shared, không Attached; WSL Stopped; không chạy lệnh đổi quyền sở hữu |
| Emulator/WHPX | PASS | 37.2.13/accel-check 0, WHPX dùng được, AVD hiện có; không wipe hoặc load/save snapshot |
| Hỗ trợ usb-host/metadata phía host | PASS có phạm vi | QMP query version 2.12.0, info usbhost 734c5530/480Mb/s; không chứng minh guest nhận thiết bị |
| Backend phía host mở TBS | FAIL/BLOCKED | libusb_open 4.4 returns -12, winusb_open unsupported/unrecognized driver; không phải ACCESS/BUSY/quyền Android |
| QEMU guest nhận TBS | FAIL mục tiêu | Boot cấu hình gốc có root USB rings nhưng không có TBS; placeholder trong info usb không xác định thiết bị mục tiêu |
| Thử cấu hình XHCI thay thế | FAIL/BLOCKED | Một cấu hình theo tài liệu QEMU, chưa xác nhận boot; đã dừng helper; không đổi kernel/QEMU tùy biến |
| Android USB Host feature | FAIL capability | cmd package has-feature android.hardware.usb.host=false |
| Framework/diagnostic nhận diện TBS | BLOCKED/không có thiết bị mục tiêu | dumpsys usb timeout trong passthrough boot; trên AVD thường, diagnostic qua UsbManager ghi 0 devices/0 TBS |
| Android descriptors của TBS | NOT RUN | Config cache phía host 32 bytes/480Mb/s không thay thế config/interface/endpoints Android |
| Permission/dialog/correlation thật | NOT RUN/BLOCKED | Framework không có TBS; không bấm Allow hoặc dùng root grant |
| Open/claim false/release/close thật | NOT RUN/BLOCKED | Không có quyền/thiết bị mục tiêu; 0 chu kỳ kết nối phần cứng |
| Detach thật/dọn callback cũ | NOT RUN/BLOCKED | Không gắn được vào guest, không rút/cắm thiết bị |
| Vendor control/I²C/firmware/reset/LNB/tune/TS | NOT RUN | Không thực hiện, ngoài phạm vi |

| Nhóm phần mềm | Kết quả mới | Evidence |
|---|---|---|
| Kiểm thử hồi quy JVM | PASS 177 methods | drivers 163/usbxfer 1/dvbservice 13; từng task --rerun, 0 failure/error |
| Instrumentation diagnostic chỉ debug | PASS 12 tests | Fake grant/deny/timeout/detach/block/cleanup/redaction/config owner; Activity thật và clipboard focus |
| Instrumentation facade/control | PASS 28 tests | Fake Manager/RawConnection; không chứng minh passthrough phần cứng |
| Native/XML smoke | PASS 2 tests | Load UsbXfer và kiểm registry tương đương XML |
| Tổng instrumentation | PASS 42 tests, 0 failure/error/skipped | XML thực tế: app 12+drivers 28+dvbservice 2 |
| Lượt instrumentation app đầu | FAIL 12 tests/1 failure | Clipboard null khi Activity chưa có focus/bị SystemUI overlay; đã sửa chờ focus và dùng Activity context, giữ assertion rồi chạy lại PASS |
| Build debug/release | PASS | Lượt cuối 7m30s/403 tasks: 53 executed, 350 up-to-date |
| LintDebug | PASS có warning | 0 Error/0 Fatal/11 Warning, không suppress |
| Loại diagnostic khỏi release | PASS | apkanalyzer manifest+DEX: 0 diagnostic classes/entrypoints; release USB Host required true, debug optional (required false) |
| Java 8 | PASS | javac --release8 cho source TBS+debug, bytecode 52; output tạm đã dọn |
| UI diagnostic: kiểm metadata thủ công | PASS trên AVD thường | Install Success/explicit launch 7704ms; không có USB Host, 0 devices/0 open/0 claim; không bấm request cycle vì không có thiết bị mục tiêu |

Log libusb/QEMU thô giữ ở local temp, không commit định danh. Lệnh, nguyên nhân chặn chính xác và cleanup ở [WORKLOG.md](WORKLOG.md); nguồn và phương án Android thật ở [RESEARCH.md](RESEARCH.md). Không nâng kết quả fake thành hardware PASS.

## 2026-11-03 13:38 +07 — Phase 2.4A: managed control transport, fake-only

Windows PowerShell; chore/modernize-android-build/8a004a3cc94d8c736da84cad1908b6a270b789a0 cộng thay đổi trong working tree. AGP 9.5.0/Gradle 9.8.2/JDK 25/compile 37.2/target 37/min 21/Java 8 giữ nguyên. Emulator-5554/TBS5530_Runtime_API37 API 37/x86_64; production wrapper đã compile nhưng Manager/RawConnection của tất cả tests đều là fake. Phase 2.3/ADR 013 đã push; 152 unit/21 instrumentation cũ là lịch sử, không mặc định tính lại.

| Gate | Kết quả thực | Bằng chứng |
|---|---|---|
| Control transport mới | PASS 14 methods | Kiểm chính xác type/request/value/index/payload/offset/length/timeout; IN chỉ công bố đầy đủ slice/OUT bất biến; short/negative/zero/overlong; cause của exception; bounds/fields sai; không retry; guard worker/interrupt |
| Ownership/generation | PASS trong transport/binding | Chỉ owner được quản lý và đã claim; không open lần hai; stale handle/FAULTED/UNCERTAIN/closed/unclaimed có 0 IO; tái dùng capability không claim hai lần; handle cũ không đóng connection mới |
| Hoàn tất muộn | PASS trong transport | Detach/timeout logic/close đồng thời khi fake bị block: pending, 0 lần close sớm, output không đổi; cleanup 1 lần sau khi hoàn tất |
| Binding regression | PASS 27 methods | 26 cũ+managed capability factory/cùng 1 connection và các assertion cleanup |
| Descriptor metadata | PASS 10 methods | Đảo thứ tự vẫn chọn 82, không chọn 81 đầu tiên; từ chối missing/duplicate/wrong direction/type/address/interface alternate/maxpacket; metadata bất biến |
| Toàn unit ba module | PASS 177 methods, 0 failure/error | drivers 163/usbxfer 1/dvbservice 13, từng task --rerun; thêm 25 methods so với baseline 152 |
| Android facade/control instrumentation | PASS 28 tests, 0 failure/error/skipped | 19 baseline+9 control/metadata/managed end-to-end; fake Raw kiểm chính xác IN/OUT và ánh xạ kết quả, main thread guard, unclaimed/closed, tham số sai, 1 open/claim/cleanup |
| Native smoke | PASS 1 test | Load thư viện UsbXfer mà không mở USB trên x86_64, không JNI ioctl |
| Device filter equivalence | PASS 1 test | Java Registry↔XML khớp nhau, không đăng ký TBS |
| Debug/lint | PASS | Lượt kết hợp exit 0/8m8s/241 tasks: 50 executed, 191 up-to-date; lint 0 Error/0 Fatal/11 Warning, không suppress |
| Java 8/stress core thuần | PASS 5 lượt×24 tests | javac --release8 không có Android classpath, bytecode 52; transport 14+concurrency 10, gồm 200 vòng kiểm soát bằng latch qua 5 lượt; output tạm đã dọn |
| Tốc độ/identity/USB Host trên Android thật | NOT RUN | VID/interface 0/81/82/512/High Speed trên Windows/WSL là lịch sử; Linux TS 82 là thông tin từ source, không chứng minh phần cứng Android |
| Runtime API 21/23/31/33/34/kích hoạt alternate khác zero | NOT RUN | Không có OS image mới, không setInterface; metadata fake không chứng minh việc kích hoạt |
| Production vendor USB/firmware/frontend/lock/TS | NOT RUN | Không gọi control trên phần cứng, không uploader/frontend hoặc stream reader |

### Phase 2.4B — toàn bộ NOT RUN, chưa được duyệt thực thi

| Gate physical | Trạng thái | Điều kiện để chạy sau |
|---|---|---|
| Phiên bản Android/USB Host/OTG/nguồn/ADB | NOT RUN | Chọn Android thật, xác nhận nguồn/cáp/hub và phê duyệt đọc metadata |
| UsbManager enumeration/descriptors | NOT RUN | Diagnostic debug/test mặc định không hoạt động, chỉ chạy khi người dùng chủ động; ghi VID/PID/interface/endpoint/mps, không ghi serial nhạy cảm |
| Attach/detach/session epoch | NOT RUN | Kế hoạch cắm/rút được duyệt, không chuyển WSL hoặc lặp reconnect |
| Permission deny/grant/callback | NOT RUN | Phê duyệt riêng; người dùng tự chọn trên dialog; kiểm correlation/state/quyền hiện tại |
| Open/claim force false | NOT RUN | Phê duyệt riêng; iface 0/alt 0, background thread, không vendor requests |
| Release/close/cleanup khi detach | NOT RUN | Chỉ cleanup tài nguyên đã acquire; không force-close I/O đang chạy |
| Vendor control/firmware/frontend/TS | NOT RUN, ngoài 2.4B | Cần phase/phê duyệt riêng; enumeration PASS không mở bước này |

Các trường bằng chứng, điều kiện dừng/khôi phục và checklist chi tiết ở [RESEARCH.md](RESEARCH.md); lệnh/kết quả chính xác ở [WORKLOG.md](WORKLOG.md). Không có hardware test nào được ghi PASS trong phase này.

## 2026-11-03 12:39 +07 — Phase 2.3: Android framework binding, không tuner

Windows PowerShell; `chore/modernize-android-build`/`635ee4a57267de6055126efcfee6121b30701433` cộng thay đổi trong working tree. AGP 9.5.0/Gradle 9.8.2/JDK 25.0.5.0.3/compile 37.2/target 37/min 21/Java 8 giữ nguyên. AVD TBS5530_Runtime_API37, emulator-5554, API 37/x86_64; không có Android/tuner thật. Mọi open/claim/request trong binding tests dùng fake Manager/RawConnection, không USB thật.

| Gate | Kết quả thực | Bằng chứng và giới hạn |
|---|---|---|
| Baseline/ADR-012 | PASS identity | Git HEAD và origin kiểm trực tiếp đúng 635ee4a, main sạch trước sửa; kết quả 126 methods Phase 2.2 là lịch sử riêng |
| JVM binding suite | PASS 26 methods | Grant/deny/timeout; dữ liệu sai dạng/trùng/cũ/sai thiết bị; tái dùng path/id+epoch; 2 devices cùng VID/PID; quyền/danh sách hiện tại; timer cũ; open/claim bị block với detach/cancel/deadline; cleanup một phần/suppressed; shutdown worker/scheduler |
| Java 8/stress độc lập | PASS 3 lượt×36 tests | Core không phụ thuộc Android, compile --release8/bytecode 52; binding 26+concurrency 10, 120 vòng kiểm soát bằng latch qua 3 lượt; output tạm đã dọn |
| Regression unit ba module | PASS 152 methods, 0 failure/error | drivers 138/usbxfer 1/dvbservice 13, các task --rerun; Bridge 17/Firmware 19/Session 17/Concurrency 10/Coordinator 32 vẫn PASS |
| Android binding instrumentation | PASS 19 tests, 0 failure/error/skipped | Receiver/PI/fill-in/one-shot/cleanup thật; Manager được inject; chọn descriptor, claim false/release false/ownership; fake open/claim bị block không chặn main; deadline độc lập; shutdown runtime/guard tránh tự đợi |
| Native library smoke | PASS 1 test | NativeLibrarySmokeTest::usbTransferLibraryLoadsWithoutOpeningUsb; chỉ load library trên x86_64, không USB ioctl/transfer |
| Device filter equivalence | PASS 1 test | DeviceFilterXmlEquivalenceTester::allDevicesAreInXml, registry/XML không đổi, không có VID 734c:5530 |
| Nhánh policy API 21/23/31/33/34/37 | PASS assertions trên API 37 | Instrumentation helper kiểm flags/receiver-policy từng API; **không** là OS runtime PASS cho 21/23/31/33/34 |
| Main/receiver không làm acquire | PASS software | Fake open/claim bị giữ bằng latch nhưng main/deadline vẫn tiến; wrapper từ chối open trên main; không join main hoặc chính thread hiện tại |
| Debug APK | PASS | AssembleDebug, install -r Success; lượt unit/build/lint cuối exit 0/2m8s/177 tasks: 22 executed, 155 up-to-date |
| LintDebug | PASS có warning | XML 0 Error/0 Fatal/11 Warning, cùng nhóm cũ; không thêm suppression/baseline |
| No-tuner launch/UI | PASS trong cửa sổ smoke | Cold Status ok 3453ms, Welcome/Diagnostic/START→NoDevicesFound đã được xử lý; log lọc PID 5124 không có fatal/linker/security/ANR của app |
| Emulator SystemUI | FAIL riêng môi trường, đã qua được | Hộp “System UI isn't responding”; chọn Wait bằng selector theo nội dung UI, app tiếp tục hoạt động. Không gộp vào ANR của app |
| Emulator/CLI startup | CLI FAIL→fallback PASS | CLI báo AVD không tồn tại dù có trong list, exit 0 sai; emulator.exe với AVD home chỉ định rõ cho boot_completed 1/API 37/x86_64 |
| Runtime API 21/23/31/33/34 | NOT RUN | Không có image/thiết bị tương ứng trong phạm vi, không tải thêm |
| USB permission dialog và protected USB attach/detach thật | NOT RUN | Callback tổng hợp không chứng minh delivery từ USB service hoặc identity phần cứng/re-enumeration |
| USB Host thật/open/claim/native cancel/firmware/frontend/TS | NOT RUN | Wrappers đã triển khai nhưng không kích hoạt với thiết bị thật; không control transport/uploader/frontend/streaming |

Lượt instrumentation kết hợp cuối exit 0/4m48s/241 tasks: 53 executed, 188 up-to-date; XML gồm 19 drivers+2 dvbservice. Sau đó chỉ thêm 1 assertion sở hữu JVM và chạy lại unit/build/lint đạt 152 methods; không đổi production logic sau lần instrumentation. Các output kiểm tra nằm trong build directories, không commit. Chi tiết lệnh/AVD cleanup ở [WORKLOG.md](WORKLOG.md).

## 2026-11-03 11:25 +07 — Phase 2.2: concurrency và lifecycle fake

Windows PowerShell; `chore/modernize-android-build`/`6eb23537484a3e1dd263bd5a88f56914c7c5e313` cộng thay đổi trong working tree. Không emulator/tuner hoặc truy cập Android USB. Toolchain giữ AGP 9.5.0/Gradle 9.8.2/JDK 25/compile 37.2/min 21/Java 8. Phase 2.1/ADR-011 đã tích hợp tại SHA này; 83 methods PASS trước đó là lịch sử, không gộp vào kết quả mới.

| Gate | Kết quả thực | Bằng chứng |
|---|---|---|
| Tái hiện trên baseline trước sửa | FAIL được tái hiện, 2/2 methods | Fake controlTransfer bị latch giữ; thread detach/cancel không invalidate kịp, assertion invalidated.await FAIL; Gradle exit 1/36s |
| Bridge regression | PASS 17 methods | Kiểm chính xác bytes/limits/directions, cặp 0x90/91 nguyên tử, descriptor 82, errors/no-retry; không đổi protocol |
| Firmware regression | PASS 19 methods | Plan/hash/ranges/chunks bất biến, chỉ dữ liệu tổng hợp; file implementation và tests không đổi |
| Session/readiness | PASS 17 methods | Vòng đời cũ và các mốc firmware/frontend tường minh; grant/open/claim/upload không tự READY, initialized không tự lock/stream |
| Concurrency | PASS 10 methods | Pointer bị block với detach/cancel; cleanup pending/chặn reopen; từ chối transaction cũ/đang xếp hàng; timeout muộn; callback detach không close trong I/O; release/close một lần kể cả close đồng thời; cleanup nhả cả hai khóa trước join worker; lỗi giữa hai nửa read không công bố bytes |
| Lifecycle coordinator | PASS 32 methods | Identity/generation/duplicates: 7; permission/lifecycle: 5; exception/ownership/cleanup: 12; acquisition bị block/detach/monitor: 8; owner bị từ chối và claim một phần không leak/close hai lần |
| Stress độc lập cuối | PASS 5 lượt×10 methods | Mỗi lượt có 40 vòng detach/cancel kiểm soát bằng latch →200 vòng qua 5 lượt; timeout hữu hạn, 0 fail; không tăng timeout để che race |
| Regression ba module | PASS 126 methods, 0 failure/error | drivers 112/usbxfer 1/dvbservice 13; từng task --rerun. TBS suites có 95 methods, trong đó thêm 43 methods so với Phase 2.1 |
| Debug build | PASS | :app:assembleDebug; Gradle cuối exit 0/2m23s/177 tasks: 41 executed, 136 up-to-date |
| LintDebug | PASS có warning | XML 0 Error/0 Fatal/11 Warning; không baseline/suppress mới |
| Java 8 compatibility | PASS | javac --release8 cho 5 core files và toàn TBS test package, bytecode 52; chỉ dùng JUnit/Hamcrest sẵn có, không Android classpath |
| IDE diagnostics | PASS tại kiểm tra | Không có errors ở main/test tbs5530 packages |
| Existing instrumentation/native-load/UI | NOT RUN | Coordinator chỉ JVM, chưa gắn Android runtime; không sửa framework binding/JNI/manifest/app. Không coi kết quả lịch sử Phase 1.2 là PASS mới |
| Real permission dialog/USB transfer/firmware/native cancel/lock/TS | NOT RUN | Không có Android transport/binding/uploader/frontend cụ thể; fake PASS không chứng minh phần cứng |

Lượt stress đầu bị lỗi tên class trong lệnh JUnitCore trước khi chạy tests; đã sửa cách gọi và chạy đủ 5 lượt thành công, không sửa assertion. Chi tiết lệnh/lỗi ở [WORKLOG.md](WORKLOG.md). Cleanup trì hoãn và quyền sở hữu ghi tại [ADR-012](DECISIONS.md).

## 2026-11-03 10:39 +07 — Phase 2.1: Java core và USB giả

Windows PowerShell, nhánh `feat/tbs5530-offline-protocol`, HEAD nền `8588ee7661003052b7d1a0353e23bb3a3dcef8b5` cộng thay đổi chưa commit. Gradle 9.8.2/AGP 9.5.0/JDK 25, compile 37.2/target 37/min 21/Java 8 giữ nguyên. Không emulator hoặc tuner; mọi giao dịch dưới đây chỉ qua fake.

| Gate | Kết quả thực | Assertion/bằng chứng |
|---|---|---|
| Bridge protocol | PASS, 17 methods | Kiểm chính xác EP0 type/request/value/index/buffer/offset/length/2000ms; 0x80 write, 0x90→0x91 read, 0x8a định tuyến terrestrial |
| I²C validation | PASS trong Bridge suite | Địa chỉ 0..127, write 1..48/read 1..50, một byte register pointer; từ chối flags/address/buffer sai trước I/O |
| Atomicity/callback | PASS trong Bridge suite | Hai bridge cùng session không xen vào cặp read; detach trong pointer callback chặn nửa read, output của caller không đổi |
| Short/timeout/cancel/error | PASS trong Bridge suite | Đếm đúng số lần gọi, giữ nguyên cause; negative/overlong là I/O failure, 0..length-1 là short; lỗi chặn các lần gọi sau, không retry tác dụng phụ |
| Endpoint/chip/PLP policy | PASS trong Bridge suite | Descriptor đảo thứ tự vẫn chọn interface 0/alternate đã chọn/Bulk IN 0x82; từ chối khi thiếu hoặc mơ hồ; chip map tường minh, manual PLP 0 và range 0..255 |
| Firmware plan | PASS, 19 methods | Kích thước tổng hợp 1/63/64/65/128; đuôi khối chính xác/chunk≤64; stop 1 ở 0x7f92/0xe600 → data → restart 0 → delay 100ms; metadata/hash/ranges/size/overflow/reserved/bản sao bảo vệ |
| Session/lifecycle | PASS, 16 methods | Permission deny/timeout event, detach, generation cũ, fault latch, streaming khác frontend lock, ownership một phần, reopen khi cleanup bị chặn |
| Firmware failure từng bước | PASS trong Session suite | Executor chỉ có trong test: lỗi ở từng DATA hoặc DELAY khiến UNCERTAIN, không READY, không thêm requests; đường thành công vẫn đòi ready event riêng |
| Cleanup | PASS trong Session suite | Cancel→release→close đúng tài nguyên đã acquire; close nhiều lần không lặp; IOException/RuntimeException không bỏ các bước tiếp; cancellation có thể join worker mà không giữ monitor của session trong đường close bình thường |
| Scripted fake | PASS qua Bridge/Session suites | Bản sao payload trước/sau, tham số/thứ tự chính xác; lời gọi ngoài dự kiến hoặc chưa dùng hết script làm test fail |
| Regression unit toàn module | PASS, 83 methods, 0 failures/0 errors | XML: drivers 69 (52 mới +17 cũ), usbxfer 1, dvbservice 13; các task đều có --rerun |
| Debug APK | PASS | :app:assembleDebug, lượt Gradle cuối exit 0/1m45s/177 tasks |
| LintDebug | PASS có warnings | XML 0 Error/0 Fatal/11 Warning; không thêm baseline hoặc suppress kiểm tra |
| Java 8 core độc lập | PASS | javac --release 8 cho 4 core files không có Android classpath, javap major version 52; JDK 25 cảnh báo source/target 8 obsolete |
| References | PASS chỉ đọc | Ba SHA khóa giữ nguyên; status có 0 entries ở cả ba repositories |
| Android permission/reconnect/native USB/firmware thật/DVB lock/TS | NOT RUN | Không có production adapter/uploader/frontend; fake PASS không chứng minh Android USB Host hoặc tuner hoạt động |
| Emulator/instrumentation/release/16KiB runtime | NOT RUN mới | Không yêu cầu chạy lại trong Phase 2.1; không tái sử dụng PASS Phase 1.2 như kết quả mới |
| TS packet fixture và PLP retune register sequence | NOT RUN, hoãn | Kế hoạch Phase 2.0 rộng hơn phạm vi core hiện tại; chưa có TS reader/frontend để test đúng hành vi |

Lệnh cuối và vị trí XML ghi ở [WORKLOG.md](WORKLOG.md). Các bảng “Phase 2.1 planned assertions — tất cả NOT RUN” bên dưới giữ làm lịch sử thiết kế; bảng mới này là kết quả thực thi hiện tại.

## 2026-11-03 00:54 +07 — Xác minh sau phục hồi NTFS do người dùng thực hiện

Windows PowerShell; TBS latest/2ce787de9f6e81b4294692a45de15596ae2e1322. Agent kiểm chỉ đọc với no-lazy-fetch/optional-locks 0; không thực thi phục hồi.

| Gate | Kết quả | Bằng chứng |
|---|---|---|
| HEAD/branch giữ nguyên | PASS | rev-parse/branch: đúng SHA đã khóa/latest |
| Index và working tree | PASS | Có index, status có 0 entries, không có index.lock |
| Sáu sparse paths | PASS | sparse-checkout list đúng phạm vi |
| Manifest file thực | PASS | 1.357 expected/actual, 0 missing/extra |
| Nội dung so HEAD | PASS | hash-object --stdin-paths: 0 mismatch trên 1.357 file |
| Source TBS/Sony/Montage/helpers | PASS hiện diện | Test-Path và manifest xác nhận, không chỉ object local |
| Persistent protectNTFS override | PASS không đổi | Truy vấn config exit 1, không có override được lưu |
| Backup | PASS hiện diện | backup004911-951/git và after.json: PASS/1357/đúng SHA; không xóa |
| C/Go references | PASS sạch | Status có 0 entries |
| Full kernel build / Android driver / USB hardware | NOT RUN | Phục hồi source không chứng minh firmware/tuner/TS hoạt động |

Các trạng thái index/checkout BLOCKED trong snapshot trước được bảo toàn làm lịch sử, không còn là kết luận mới nhất.

## 2026-11-03 — Phase 2.0 source readiness và kế hoạch offline

Windows PowerShell, nhánh docs/tbs5530-source-architecture, baseline 4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe. SHA nguồn: TBS 2ce787de9f6e81b4294692a45de15596ae2e1322/C c5fa27eecbe80633cbb365cc44656daea1c0e2e3/Go 11228b05fb1626bf41c4e1879ace10cd0a8d575c. Không build hoặc test driver/hardware trong phase này.

| Kiểm tra thực | Trạng thái | Bằng chứng mới |
|---|---|---|
| Baseline main và tạo nhánh tài liệu | PASS | Status sạch, HEAD 4cf1b64; git switch-c docs/tbs5530-source-architecture, modernization không đổi |
| Identity của 3 references | PASS | rev-parse/branch/remotes đúng mốc; C/Go có 0 changes |
| TBS index/working tree | FAIL, checkout BLOCKED | Không có index/lock; ls-files 0, status 81.399 D, sáu thư mục đều có 0 file |
| Cấu hình sparse TBS | PASS chỉ cấu hình | Sáu paths, cone/sparse flag true; không phải files PASS |
| Git objects được chọn để nghiên cứu | PASS có phạm vi | 785/785 blob local, 0 missing, 15.372.887 bytes; no-lazy-fetch |
| Objects ở thư mục cha của cone | PASS hiện tại | 572/572 local, 0 missing; khác lịch sử thiếu 572, không tải trong phase |
| Kiểm cơ bản 1.357 tên file cone | PASS có giới hạn | 0 tên Windows dành riêng trong tập sẽ materialize; không chứng nhận filesystem/full tree |
| Board/Sony/Montage/framework dependencies | PASS source objects/FAIL disk | Headers của Tbs5530/cxd2878/m88rs6060, helpers và direct Linux headers có tree/blob, không có trên disk; không phải full kernel build gate |
| Dry-run index mặc định trên Windows | FAIL tái hiện an toàn | read-tree --dry-run -m -u HEAD exit 128, invalid path AUX nằm ngoài sparse; vẫn không có index/lock |
| Recovery/fetch/sao chép WSL | NOT RUN | Kế hoạch backup/rollback/gates chờ phê duyệt; không override config hoặc reset |
| Thiết kế protocol/PLP/chip/retry | PASS kiểm source | File/functions/SHA chính xác và khác biệt ghi ở RESEARCH; không chứng minh hardware |
| Toolchain build/unit/lint/runtime | NOT RUN mới | Kết quả Phase 1.2 là lịch sử; không rerun để gọi PASS mới |
| Firmware/chip ID/USB permission/lock/TS thật | NOT RUN | Không hardware IO; còn vướng license/nguồn gốc/address map của firmware |

### Phase 2.1 planned assertions — tất cả NOT RUN

| Nhóm | Gate PASS dự kiến | Gate FAIL dự kiến |
|---|---|---|
| Mã hóa control | Chính xác request type/request/value/index/length/timeout và thứ tự byte payload | Assertion sai hoặc thiếu, bỏ qua short transfer |
| I2C write/read | Địa chỉ 7 bit, guard write 48/read 50, cặp 90+91 nguyên tử, input sai có 0 IO | Gửi bare-read/TEN/nhiều pointer/địa chỉ không khớp/overflow |
| Kế hoạch binary firmware | Dữ liệu tổng hợp/hash/allowed ranges/chunks 1/63/64/65/128/đuôi khối đúng; từ chối địa chỉ dành riêng | Binary thật chưa được duyệt, đọc quá giới hạn, chỉ dùng cap nên bỏ sót overlap |
| Lỗi firmware giả lập | Lỗi mỗi step→FAULTED, không READY/tự reset/reupload | Retry tác dụng phụ hoặc restart CPU sau lỗi |
| Ánh xạ endpoint | Đảo descriptors vẫn chọn interface 0/BULK IN 0x82, từ chối khi mơ hồ | Chọn IN 0x81 đầu tiên hoặc đòi OUT 0x01 |
| Timeout/retry | Giữ cause lỗi, số physical IO không lặp; fault latch chặn IO từ 10 retry của base | Bỏ qua timeout/replay do retry lồng nhau |
| Session/generation | Permission deny/detach/callback muộn có 0 writes; fd cũ bị invalidated | Callback session cũ mở device/tải firmware |
| Cleanup | Cancel→release→close đúng một lần; từng lỗi acquire/close 2 lần không leak | Đóng fd khi worker còn chạy, release hai lần, cleanup gửi vendor writes |
| Chính sách PLP/chip/value | Manual 0 rõ ràng, kiểm 0..255, map ID tường minh, đổi PLP cần lệnh ghi mới | Gọi là “Auto” không có bằng chứng, Go active retune không làm gì, ID khác mặc định Freia |
| TS giả lập/tương thích | Fixture 188 byte đúng thứ tự/partial reads/drain packet cũ, wire enum không đổi | Test chỉ compile, coi streaming là lock, đổi protocol ordinal |

Các ca dự kiến nêu yêu cầu cho fake/core; không viết production CXD tables/JNI hoặc test USB thật. Fixtures, danh sách file, lệnh và tiêu chí hoàn tất Phase 2.1 xem RESEARCH. Kiểm tài liệu (diff/UTF8/links) được ghi vào WORKLOG sau khi bổ sung xong; không biến bản thiết kế thành test PASS.

## 2026-11-02 — Phase 1.2: AGP 9.5.0 và toolchain audit

Windows 10.0.19045/PowerShell 7.6.9; branch chore/modernize-android-build, HEAD nền ace7e9f187d11a685975f707b6743d779fa847d9, diff chưa commit. AGP 9.5.0, Gradle 9.8.2/JDK 25, compile 37.2/target 37/min 21/Java 8, NDK 30/CMake 4.1.2/BT 37; không hardware.

| Gate | Trạng thái cuối | Bằng chứng |
|---|---|---|
| AGP 9.5.0 stable/compatibility | PASS nguồn | Google Current Release 9.5.0, final release notes, POM Google Maven đúng phiên bản; Gradle tối thiểu 9.6/JDK tối thiểu 17; không dùng RC |
| Giữ các phiên bản còn lại | PASS | Wrapper/checksum/module pins không đổi; --version xác nhận JVM 25/Gradle 9.8.2 |
| SDK inventory sau cleanup | PASS | Đối chiếu audit JSON/CLI/source.properties; platform 34+37.2, BT 37/NDK 30/CMake 4.1.2; không cài/gỡ |
| Audit script chạy theo policy | BLOCKED ban đầu→PASS sau phê duyệt | RemoteSigned chặn; người dùng chỉ định Bypass Scope Process; try/finally khôi phục Undefined, không đổi policy User/Machine |
| AVD discovery ban đầu | FAIL đã sửa | Emulator có 0 tên/disk có 2 tên; process không nhận AVD home của User, hậu tố thư mục gây tên ảo |
| AVD discovery sau sửa | PASS | Một TBS5530_Runtime_API37; emulator 1/metadata 1, environment của caller được khôi phục |
| CMake parser/cleanup review | PASS | externalNativeBuild cmake=4.1.2, không phải BT 37; review CSV chỉ KEEP/REVIEW, 0 uninstall |
| PowerShell regression suite | PASS thực | CMake sai scope, loại AVD trùng, child env, khôi phục env khi trước đó có/không có, metadata fallback; fixture tạm đã xóa |
| JDK 25 build environment | PASS child / FAIL stale parent | Machine hợp lệ, Process giữ giá trị cũ không hợp lệ; Gradle Launcher/Daemon xác nhận 25, không sửa global |
| Debug/release APK | PASS sau retry | 1048 FAIL unable-delete x86; 1059 exit 0/9m12s, 325 tasks: 245 executed, 80 UP-TO-DATE |
| Unit tests chạy lại | PASS thực | Task --rerun từng module 1059; XML usbxfer 1/drivers 17/dvbservice 13=31 methods, 0 fail (1 utility) |
| Lint debug | PASS có warning | 1059 exit 0; XML 0 Error/Fatal/11 Warning, không baseline/suppress gate |
| Release AAB | PASS | 1067 bundleRelease exit 0/45s, 157 tasks: 18 executed; vẫn có warning deprecated API |
| Native 4 ABI debug/release | PASS compile/link | APK có entry libUsbXfer cho arm64-v8a/armeabi-v7a/x86/x86_64; xác nhận timestamp output ELF mới |
| ZIP 16KiB | PASS static | zipalign37 -c -P16 4 debug/release exit 0 |
| ELF 64 bit alignment | PASS static | llvm-readelf NDK 30: arm64/x86_64 LOAD 0x4000 debug/release; 32 bit 0x1000 ghi riêng |
| Instrumentation registry XML/native-load | PASS thực | 1055 exit 0/5m38s, XML 2 tests, 0 fail, 0 error; đủ cả 2 classnames/methods |
| Android launch/no-tuner/insets regression | PASS trong smoke | Install Success, COLD Status ok/10080ms; UI hierarchy có inset top 63/bottom 63; NoDevicesFound đã xử lý, Back về Welcome |
| App crash/linker/security | PASS trong cửa sổ test | Log lọc PID app 5345 có 0 fatal/linkage/security; không gộp ANR launcher/SystemUI thành lỗi app |
| AVD boot/host responsiveness | FAIL ban đầu→hồi phục | Timeout 4 phút, sau đó getprop boot 1; launcher/SystemUI ANR hết sau Wait; không wipe/reset |
| AVD shutdown | PASS | Đúng avd name→emu kill; emulator process 1051 exit 0, ADB list cuối rỗng |
| Runtime API 21/arm64/16KiB, USB Host/service | NOT RUN | Chỉ Android 17/API 37/x86_64/page 4096; không tuner/fixture |
| TBS firmware/I2C/reset/LNB/TS | NOT RUN | Không có lệnh hardware hoặc ghi reference |

**Artifacts mới:** debug APK SHA256 6D6E1C4953F708CA04E9DC5AD223168514C3745F27106E3AF12033C884D44D53; release unsigned APK 7B126F04109EA1F9295336EF8615C8ACCC3743BE8488497BF8DE7EA40AAB52F3. Bản sao instrumentation XML ngoài Git có SHA256 03269F9A8C91B106FF415A015756384CB0CA3EC4A0CDDE15AC0B3341534B02A6. Không dùng báo cáo cũ làm PASS mới.

**Giới hạn:** Vẫn có warning source/target 8 obsolete và deprecation của Configuration.setVisible trên AGP 9.5; không khẳng định hết warning chỉ vì release notes có fixed issue. Không gọi AAB là đã signed/published. Không tự commit/merge/push hoặc cleanup SDK.

## 2026-11-02 — Phase 1.1 Android runtime, 19:31–20:05 +07

Branch `test/android-runtime-smoke`, HEAD 6da609ba49dd9f1f4d284291918f68a6378972ff từ modernization đã push. Runtime AVD TBS5530_Runtime_API37/Android 17/API 37/x86_64/page 4096; không tuner. Windows PowerShell/SDK D:\Android\Sdk; không nâng toolchain.

| Gate | Kết quả | Bằng chứng thực |
|---|---|---|
| Giữ Git/local changes khi tạo branch | PASS | 2 file local `.gitignore`/workspace giữ hash; branch mới cùng 6da609b, không stash/reset/commit |
| Environment build versions | PASS có lưu ý | wrapper --version xác nhận JDK 25/Gradle 9.8.2; scripts: AGP 9.4.1/API 37.2/NDK 30/CMake 4.1.2/BT 37; process env được nạp riêng |
| AVD/image có sẵn | PASS | Tạo AVD riêng sau phê duyệt; dùng image 37.0 x86_64 local, không tải SDK/image |
| Emulator first boot/SystemUI | FAIL ban đầu, hồi phục | ANR com.android.systemui hiện overlay; chọn Wait thì hết overlay. Không quy lỗi cho app |
| Cài APK debug | PASS | adb với serial chỉ định, install-r Success cho cả baseline và bản sửa |
| Cold launch Welcome | PASS | am start-W: Status ok/COLD, TotalTime 6606ms; PID 3840 còn sống, không fatal của app |
| UI system bars trước sửa | FAIL | Screenshot/hierarchy: hàng tune ở y 0, Close đến 2400 bị system bars che |
| UI system bars sau sửa | PASS runtime 37 | SystemBarInsets API 35+; tune top 63, buttons max bottom 2337 so với screen 2400; status icons tối, đọc được |
| Welcome/License/OK | PASS | Tap theo UI hierarchy vào GplLicenseActivity rồi về Welcome |
| Diagnostic/Start không tuner | PASS | Dialog No devices found; DvbException đã xử lý tại DeviceController:64, Hardware unchecked, Tune/Record disabled; PID còn sống |
| Activity Home/resume/Back/Close/relaunch | PASS | Home→am start đưa task về Diagnostic; Back→Welcome; Close ra Launcher; warm relaunch 1796ms |
| Entry dtvdriver chooser không tuner | PASS có giới hạn | ACTION_VIEW dtvdriver:// mở Chooser rồi finish về Welcome, không crash; không có assertion cho caller result code |
| App crash/ANR/linker/security | PASS trong smoke window | Log lọc PID 6344 có 0 fatal/linkage/security/ANR app; exit-info chỉ có PACKAGEUPDATED do install-r |
| Service foreground/streaming lifecycle | NOT RUN | Không device/fixture, không ép startService; không có ServiceRecord của DvbService |
| USB permission/transfer | NOT RUN | Emulator không có usb.host, không gắn tuner; không fd giả/ioctl |
| Storage permission no-tuner path | PASS có giới hạn | READ/WRITE external=false nhưng UI/đường chọn no-device vẫn chạy; chưa test record TS |
| libUsbXfer x86_64 trong APK | PASS | ZIP entry lib/x86_64/libUsbXfer.so: 7320 bytes |
| Native library runtime loading | PASS | NativeLibrarySmokeTest kiểm IS_PLATFORM_SUPPORTED+gọi trực tiếp System.loadLibrary; 0 USB requests/ioctl |
| Registry/XML instrumentation | PASS | Assertion DeviceFilterXmlEquivalenceTester.allDevicesAreInXml giữ nguyên |
| Full connectedDebugAndroidTest | PASS | 1019 exit 0/49s, XML testsuites: 2 tests, 0 fail, 0 error; đủ cả 2 classname/testmethod |
| Build debug/test APK và lint | PASS | 1006 exit 0/2m4s; 1016 assembleDebug/lintDebug exit 0/1m54s; lint 0 errors, 11 warnings |
| Unit regression sau thay đổi | PASS task validation | 1028 exit 0/32s, 39 tasks UP-TO-DATE; không gọi là 31 methods đã chạy lại mới |
| Runtime API 21/arm64/16KiB | NOT RUN | Chỉ API 37 x86_64 page 4KiB; không đánh đồng ELF alignment với runtime 16KiB |
| Workspace/ignore/instructions | PASS config | Exception đúng đã có local; 4 relative paths resolve; 2 instructions cập nhật bằng English; không stage/commit |
| References/hardware | PASS giữ nguyên/NOT RUN hardware | TBS thiếu index và source file, 81399 D; SHAs không đổi, C/Go sạch; không sync/usbipd/firmware/vendor write |

**Coverage trap đã xử lý:** Lệnh Gradle đầu có filter 2 class, 1012 exit 0 nhưng XML chỉ có 1 registry test; không gọi đó là native PASS. Gọi trực tiếp am instrument trước khi install test APK bị lỗi thiếu component; sau install-r, 1018 native 1 test PASS. Rerun task **không filter**, 1019 XML có 2 tests, xác nhận đủ. Không bỏ test/assertion, không tạo PASS chỉ từ exit code.

**Evidence:** Screenshots trước/sau/no-tuner, XML 2 tests và log đã lọc lưu ngoài Git trong session files/phase-1-1. APK/hash và lệnh chính xác ở WORKLOG/ENVIRONMENT. Emulator shutdown ghi ở WORKLOG; không xóa data.

## 2026-11-02 — Migration JDK 25: kiểm chứng đã chạy

Windows 10.0.19045/PowerShell 7.6.9, JDK 25.0.5.0.3; nhánh chore/modernize-android-build trên nền e4c13a5b86ad5ef9be887df58226046dcb42dcd5, chưa commit. Gradle 9.8.2/AGP 9.4.1; NDK 30/CMake 4.1.2/BT 37/API 37.2; min 21.

| Gate | Kết quả cuối | Bằng chứng thực |
|---|---|---|
| JVM 25 chạy Gradle 9.8.2 | PASS | wrapper --version ở shell 963 exit 0, Launcher/daemon JDK 25; task tái tạo wrapper 973 exit 0 |
| Package toolchain | PASS | android sdk install đúng 4 package, 966 exit 0 và source.properties; NDK meta min 21 |
| Debug APK | PASS | assembleDebug 968 exit 0, chốt 983 exit 0/36s; 4 ABI native |
| Unit usbxfer | PASS | XML 1 method, 0 failure |
| Unit drivers | PASS | XML 17 methods, 0 failure (1 ConvertCString utility) |
| Unit dvbservice | PASS | XML 13 methods, 0 failure sau sửa Mockito/null/newline; 974 và 983 |
| Instrumentation test APK compile | PASS | assembleDebugAndroidTest, package trong 974; chốt 983 exit 0; APK đã sinh |
| Instrumentation chạy thiết bị | NOT RUN | Không emulator/device serial, không install hoặc boot |
| Lint debug | PASS có warning | 980 exit 0, XML 0 Error/Fatal/11 Warning; không baseline/suppress/abortOnError=false |
| Release APK unsigned | PASS | 980 exit 0/3m54s, app-release-unsigned.apk 5466380 bytes; không signing/publish |
| Release AAB | PASS | 980 exit 0, app-release.aab 4782669 bytes; không upload |
| Native 4 ABIs debug/release | PASS compile/link | arm64-v8a/armeabi-v7a/x86/x86_64; từng bước CMake hoàn tất |
| ZIP 16KiB debug/release | PASS static | zipalign37 -c -P16 4 exit 0 cho 2 APK |
| ELF 64 bit alignment | PASS static | llvm-readelf NDK 30: LOAD 0x4000 cho arm64-v8a/x86_64 debug+release; 32 bit 0x1000 ghi riêng |
| Native-load trên Android 16KiB | NOT RUN | Static alignment không chứng minh runtime/USB ioctls |
| minSdk/targetSdk APK | PASS metadata | Merged manifest min 21, target 37; không overrideLibrary; aapt2 xác nhận native 4 ABIs |
| Runtime Android 21/37/UI/permission/service | NOT RUN | Chưa chọn Android/AVD; build không chứng minh hành vi target 37 |
| TBS reference không tải/sửa | PASS theo phạm vi giữ nguyên | SHA của 3 refs không đổi, C/Go sạch, TBS vẫn có 81399 D; không Apply/fetch 572 objects |
| Firmware/I2C/LNB/lock/TS thật | NOT RUN | Không driver mới hoặc hardware writes |

**Các lần FAIL trước PASS:** Configuration 965 thiếu NDK; debug 967 thiếu LocalBroadcastManager; unit 971 có hai fail; release 974/977 lỗi native output directory trên Windows. Sau sửa đúng dependency/tests và chạy tuần tự max-workers 1: 979 native riêng PASS, 980 release/lint PASS; 983 debug/tests/instrumentation compile PASS. Không xóa test/suppress lint để né lỗi.

**Warnings tồn tại:** 11 lint (GradleDependency 2 do pin API 21, UnusedResources 3, IconLauncherShape 3, TextFields/Autofill/LabelFor mỗi 1), source/target 8 obsolete, AGP dùng Gradle API deprecated. Giữ kế hoạch xử lý riêng, không tuyên bố zero-warning hoặc mọi library đều latest tuyệt đối.

## 2026-11-02 12:52 +07 — JAVA_HOME recheck và dry-run TBS

Windows PowerShell; main SHA `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`; TBS SHA `2ce787de9f6e81b4294692a45de15596ae2e1322`. Không chạy build hoặc khôi phục reference.

| Kiểm tra | Trạng thái | Kết quả thực |
|---|---|---|
| Machine JAVA_HOME hợp lệ sau người dùng sửa | PASS | `C:\Program Files\Java\latest\jdk-25`, thư mục/executable tồn tại, java-version 25.0.5.0.3 |
| Process JAVA_HOME hiện tại hợp lệ | FAIL | Agent vẫn nhận đường dẫn cũ không tồn tại từ process cha |
| Child đọc Machine JAVA_HOME và chạy java | PASS | Chỉ cập nhật env trong child; executable chạy exit 0 |
| TBS index/source readiness | FAIL | Thiếu index/index.lock, 81.399 D; 6 thư mục được chọn đều có 0 file |
| Blob local ở các thư mục được chọn | PASS | 785/785 blob tồn tại, no-lazy-fetch |
| Blob ở thư mục cha của cone có đủ local | FAIL | Thiếu 572/572 blob ở thư mục cha; cần phê duyệt tải nếu giữ cone |
| Tên file sẽ materialize trên Windows | PASS | Kiểm cơ bản tên của 1357 cone paths, 0 reserved/invalid; không thay thế checkout test |
| read-tree dry-run mặc định | FAIL | exit 128, `invalid path .../i2c/aux.c`, ngoài sparse nhưng có trong full index |
| read-tree dry-run command-scoped protectNTFS=false | PASS | exit 0; index vẫn không tồn tại; không lưu config; chưa chứng minh restore thật |
| Script phục hồi bàn giao — cú pháp/preview | PASS | Parser có 0 errors; chạy không tham số: 1357 cone files/572 missing, không tạo index; SHA256 trong RESEARCH |
| Script Apply/backup/fetch/khôi phục thực | NOT RUN | Người dùng chọn nhận bộ lệnh tự chạy; agent chỉ preview, chưa kiểm end-to-end |
| Gradle/build/hardware | NOT RUN | Ngoài phạm vi; JDK 25 vẫn không hỗ trợ wrapper 8.7 |

## 2026-11-02 — Phase 0: bằng chứng mới (UTC+07)

Windows PowerShell 7.6.9 / Windows 10.0.19045; cwd root AndroidDvbDriver, HEAD `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`. PASS dưới đây chỉ áp dụng đúng phép dò mô tả, không chứng minh driver hoạt động. Nguồn protocol khóa ở [RESEARCH.md](RESEARCH.md); lịch sử 2026-11-01 bên dưới giữ nguyên.

| Kiểm tra | Trạng thái | Lệnh / kết quả quan sát |
|---|---|---|
| 4 repository tồn tại, remote/branch/HEAD | PASS | `rev-parse --show-toplevel`, `remote -v`, `branch --show-current`, `rev-parse HEAD`; SHA ghi RESEARCH |
| Main working tree sạch | BLOCKED | `status --porcelain=v1 -uall`: 18 untracked có sẵn; không phải clean checkout để migration |
| Cộng đồng C và Go working tree | PASS | Status 0 thay đổi trước audit |
| TBS sparse configuration | PASS | `sparse-checkout list`: 6 patterns; sparseCheckout/Cone=true |
| TBS checkout/source trên đĩa | FAIL | Index thiếu, `ls-files` 0; 81.399 D; tbs5530/cxd2878/m88rs6060/dependencies thiếu; không restore |
| TBS source trong HEAD objects | PASS | `ls-tree`, `cat-file --batch-check`, `git show` đọc bridge/frontend blob local; không đồng nghĩa checkout tốt |
| Không fetch ngầm trong toàn audit | BLOCKED | Không fetch tường minh; diff --cached --shortstat đầu bị dừng sau 30s; FETCH_HEAD có mtime mới nhưng không có baseline để xác định tác nhân. Các lần đọc sau chặn lazy fetch |
| Workspace paths/JSON | PASS | `ConvertFrom-Json` và `Test-Path`: cả 4 roots resolve đúng |
| Workspace được version-control | FAIL | `git check-ignore -v`: `.gitignore:73`, `*.code-workspace`; không sửa ignore |
| Sync script cú pháp | PASS | PowerShell Parser.ParseFile, 0 errors; **không thực thi script** |
| Instructions/skills có trên đĩa và đọc được | PASS | Copilot instructions + 6 scoped + 2 project SKILL.md; project-handoff/tbs-research được skill tool load |
| VS Code tự attach mọi scoped instruction | NOT RUN | Context có hướng dẫn áp dụng; không có API xác minh từng lần attach, đã chủ động đọc đủ file |
| Java executable PATH | PASS | `java -version`: 25.0.5.0.3 |
| JAVA_HOME hợp lệ | FAIL | `Test-Path C:\Program Files\Java\jdk-25` false; executable thực ở jdk-25.0.5.0.3 |
| Environment SDK/AVD nhất quán | FAIL | Process không có ANDROID_HOME/ANDROID_AVD_HOME, User đúng D:\Android; CLI default chọn đường dẫn khác |
| Android CLI và explicit SDK inventory | PASS | `android --help`, `-V` 1.1.16717390; `--sdk=D:\Android\Sdk info sdk/sdk list` exit 0 |
| SDK package versions | PASS | SDK list và source.properties xác nhận 34/37.0, BT 36, NDK 28.2, CMake 3.22.1, CLI tools 23; ADB 37.0.1, Emulator 37.2.13 |
| Android docs capability | PASS | `docs search` USB Host + `docs fetch kb://android/develop/connectivity/usb/host`; CLI tự tạo/tải KB cache, không đổi SDK packages |
| WSL2 hiện có | PASS | `wsl.exe --list --verbose`: Ubuntu-22.04 Running, v2; chưa kiểm kernel lại |
| usbipd executable/version | PASS | Full path Program Files\usbipd-win\usbipd.exe, 5.3.0-54; process PATH không thấy |
| TBS visible / USB ownership | PASS | `usbipd list`: VID:PID 734c:5530, BUSID đọc trực tiếp 6-2, Shared; phía Windows, chưa xác định app giữ handle |
| WSL attach/lsusb/PyUSB trong phiên này | NOT RUN | Thiết bị không Attached; không đổi ownership, không claim; PASS lịch sử không tính lại |
| MCP Proxy discovery/read-only invocation | PASS | retrieve_tools + call_tool_read cho adb-mcp/gitnexus/agentmemory |
| AVD listing qua ADB MCP | PASS | `adb-mcp:list_avds`: No AVDs found; không boot hoặc tạo AVD |
| ADB MCP UI / physical Android connection | NOT RUN | Chưa chọn serial, chưa UI/logcat/install; list_avds không chứng minh có thiết bị Android |
| GitNexus có index dự án | BLOCKED | list_repos không có AndroidDvbDriver/reference; không analyze |
| AgentMemory lookup | PASS | memory_recall compact limit 3, query TBS5530 AndroidDvbDriver: matched_count 0; không lưu mới |
| Gradle runtime/configuration baseline | BLOCKED | JAVA_HOME sai và Gradle 8.7/JDK 25 không hỗ trợ; không chạy wrapper/build để tái hiện |
| Assemble/lint/unit/instrumentation/native ABI/16KiB | NOT RUN | Chưa duyệt chạy build hoặc tạo emulator; chưa có APK/binary evidence |
| Firmware file/hash/license/upload | NOT RUN | Chỉ đọc tên/quy trình từ source; không tải/đóng gói/gửi firmware |
| I2C/chip-ID/frontend lock/TS continuity Android thật | NOT RUN | Chưa triển khai driver; không vendor write, reset, LNB |

**Không có attach/detach/bind/unbind hoặc test DVB mới.** Chẩn đoán Git và truy vấn CLI không được tính là kiểm thử phần cứng. Không lưu raw USB listing chứa các thiết bị cá nhân khác vào Git.

## Historical matrix — 2026-11-01 (người dùng cung cấp; không chạy lại)


Use `PASS` only for tests with recorded observations; `NOT RUN` when not executed.

| Test | Environment | Status (2026-11-01) | Evidence |
|---|---|---|---|
| USB device visible in Windows | Windows / usbipd list | PASS | Screenshot do người dùng cung cấp có `TBS 5530 DVB BDA`, ID `734c:5530` |
| WSL USB attach | WSL2 / usbipd-win | PASS | Screenshot có BUSID `6-2`, trạng thái Attached |
| USB descriptors | WSL2 / lsusb and PyUSB | PASS | 480 Mbps, interface 0, IN EP 0x82 và 0x81, mỗi endpoint 512 bytes |
| libusb claim interface 0 | WSL2 / PyUSB | PASS | claim/release in ra PASS |
| Driver firmware upload | WSL2/Android | NOT RUN | Chưa thực hiện test |
| USB/I2C control transactions | WSL2/Android | NOT RUN | Chưa thực hiện test |
| AndroidDvbDriver upstream build | Windows/Gradle | NOT RUN | Chưa được người dùng xác nhận |
| APK deployment to Android | Android / ADB | NOT RUN | Chưa được người dùng xác nhận |
| DVB-T2 frontend lock | Android | NOT RUN | Chưa port |
| MPEG-TS stream capture and continuity | Android | NOT RUN | Chưa port |

## New test evidence template
- Date/time (timezone):
- Device and firmware state:
- Host OS / kernel / USB passthrough:
- Command/code version:
- Observed result, raw log location and SHA256:
- PASS/FAIL/NOT RUN with rationale:
