![App Icon](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

# Android DVB-T Driver

This driver provides a simple TCP based API that allows
controlling USB DVB-T/DVB-T2 tuners on Android. It gives other apps access to
the raw DVB MPEG-2 TS stream.

The driver is a simplified user space port of a subset of
the V4L2 Linux kernel drivers. Under the hood it uses Android USB Host API
with a small helper library that allows fast kernel controlled USB bulk transfers.

# Build fork TBS5530 trên Windows

Baseline toolchain đã kiểm chứng ngày 2026-11-02 của fork dùng JDK 25, Gradle Wrapper 9.8.2 và AGP 9.5.0. Đây là phiên bản dự án đã chọn, không phải cam kết luôn là bản mới nhất. SDK duy nhất là `D:\Android\Sdk`; cần platform `android-37.2`, Build-Tools `37.0.0`, NDK `30.0.16248370` và CMake `4.1.2`. Build không tự cài package SDK còn thiếu.

Ứng dụng vẫn giữ `minSdk 21` và Java source/target 8; JDK 25 chỉ là JVM chạy build và compiler. AppCompat 1.7.1 và Material 1.13.0 là ngoại lệ có chủ đích so với bản mới nhất để giữ hỗ trợ Android 5.0/5.1. Không nâng Java bytecode lên 25 cho Android.

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_AVD_HOME = 'D:\Android\AVD'
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug --console plain --no-daemon
.\gradlew.bat :usbxfer:testDebugUnitTest :drivers:testDebugUnitTest :dvbservice:testDebugUnitTest --console plain --no-daemon
.\gradlew.bat :app:lintDebug :app:assembleRelease :app:bundleRelease --console plain --no-daemon --max-workers=1
```

Lệnh release dùng một worker vì đã quan sát lỗi khóa thư mục native output trên Windows khi chạy nhiều worker. Đây là cấu hình lệnh đã kiểm chứng, không phải kết luận đã xác định nguyên nhân khóa. Release APK hiện chưa ký để phát hành.

Các lệnh trên chỉ build/kiểm thử phần mềm, không tải firmware, không ghi USB hoặc kiểm chứng tuner thật. TBS5530 chưa được triển khai. Kết quả và giới hạn mới nhất nằm trong [STATUS.md](docs/project/STATUS.md) và [TEST-MATRIX.md](docs/project/TEST-MATRIX.md).

## Chẩn đoán Android: chọn lại interface 0/alt 0

`EXPLICIT_SET_INTERFACE_ALT0` chỉ có trong đường chẩn đoán debug, mặc định **TẮT**. Khi được cấp quyền riêng, ứng dụng gọi đúng một lần `UsbDeviceConnection.setInterface(UsbInterface)` trên interface đã claim bằng `force=false`, ngay trước lần nhận Bulk đầu tiên. Cờ không thay cấu hình, reset, CLEAR_HALT hoặc thêm lệnh vendor; release và các driver khác giữ nguyên.

Host helper `tools\Invoke-TBS5530-AutonomousSession.ps1` nhận `-ExplicitSetInterfaceAlt0` cùng `-Profile EXPERIMENTAL_DIAGNOSTIC -ReadApi NATIVE_USBFS_URB_8X4096`. Cần quyền phiên mới và proof-of-guard như trước; intent chỉ yêu cầu lựa chọn, không cấp quyền. Không ghép cờ này với phép thử 8192 byte. Challenge, grant bất biến và hash kế hoạch riêng đều ràng lựa chọn; giao diện hiển thị cờ đang tắt hay đã yêu cầu quyền.

Trước API, ứng dụng kiểm quyền USB của Android, đúng `734c:5530`, raw descriptor operational config 1/interface 0/alt 0/EP82, High-Speed, owner độc quyền còn hoạt động, K36 AUTO đã khóa và chưa có URB được gửi hoặc đang chờ. API trả `false`, ném exception, mất quyền hoặc rời thiết bị đều dừng trước Bulk, không retry/rearm hoặc nạp firmware lần hai; cleanup drain/quarantine giữ nguyên.

`report.json` chứa thời gian `ANDROID_ELAPSED_REALTIME_NS`, metadata interface/raw descriptor trước và sau, kết quả API và số URB. Đây là `ANDROID_APP_OBSERVED`, không phải packet trên dây hoặc bằng chứng thiết bị đã phát TS. Nếu không thể quan sát sau lỗi, trường tương ứng là `null`, không giả định bằng 0. Helper lưu report ngoài Git dưới thư mục log đã chọn. Kiểm offline/build không xác nhận Android hardware hoặc `TS_SMOKE_PASS`.

Host helper đọc `/proc/uptime` bằng UID của ADB shell, truyền thời gian qua positional argument rồi dùng `run-as` ghi heartbeat trong app-private bằng đổi tên file, tất cả trong cùng một lệnh ADB. UID ứng dụng không cần đọc `/proc/uptime`; không dùng timestamp lấy từ vòng gọi trước. Giới hạn mỗi lệnh runtime vẫn 1500 ms, heartbeat vẫn 2 giây và phiên tối đa 5 phút; hết hạn không gia hạn file, STOP vẫn được ghi. Chỉ đọc hoặc hash `control-ep0.jsonl` sau report terminal đã xác nhận `DRAIN_CONFIRMED`, không URB pending/quarantine, mới dùng giới hạn export 10 giây. Export thất bại không biến phép nhận lỗi thành PASS. Kiểm fake offline: `tools\Test-TBS5530-Heartbeat.ps1` và `tools\Test-TBS5530-AutonomousSession.ps1`, không gọi ADB.

Nguồn API: [Android USB API](https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection#setInterface(android.hardware.usb.UsbInterface)) và [AOSP Android 16 r1](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/java/android/hardware/usb/UsbDeviceConnection.java). AOSP tách `claimInterface(intf, false)` khỏi `setInterface(intf)`; việc claim không chứng minh đã gọi API chọn alt.

### Kiểm việc khởi chạy và heartbeat mà không truy cập tuner

Helper gắn một `launch_token` mới vào mỗi lệnh mở phiên. Token này chỉ dùng để ghép lệnh với challenge mới, không phải quyền USB hoặc physical recovery epoch. Activity chỉ tạo phiên tiếp theo sau khi phiên trước đã dọn tài nguyên và ghi được report; challenge cũ, intent gắn USB và token đã dùng không cấp quyền mới. Không xóa hoặc tạo lại ledger để sửa lỗi khởi chạy.

Dùng `-ControlPlaneOnly` để kiểm khởi chạy, grant, heartbeat và STOP trên Android mà không tạo USB manager/runtime, xin quyền USB, mở tuner hoặc gọi firmware/I²C/Bulk/setInterface. Chế độ này được ràng bất biến vào challenge/grant và bị chặn ở các điểm cấp quyền I/O. Nó từ chối firmware path, physical epoch, AutoUsbPermission và ControlMirror. Kết quả thành công là `CONTROL_PLANE_COMPLETE`, không phải `TS_SMOKE_PASS`.

```powershell
.\tools\Invoke-TBS5530-AutonomousSession.ps1 -Serial '<verified-serial>' -ExpectedModel SM-X926B -ExpectedApi 36 -ExpectedBootCount <verified-boot> -Profile EXPERIMENTAL_DIAGNOSTIC -ReadApi NATIVE_USBFS_URB_8X4096 -ExplicitSetInterfaceAlt0 -ControlPlaneOnly -DurationSeconds 30 -GrantBoundedSession -ProofOfGuardPassed -OutputDirectory 'D:\TSDuck\logs\<new-run>'
```

Report ghi `cleanup_path=NO_RESOURCES_ACQUIRED` chỉ khi chưa bắt đầu cấp tài nguyên và tất cả owner/runtime/request đều vắng. Phiên đã bắt đầu cấp tài nguyên phải đi qua cancel/drain và xác nhận runtime kết thúc; số byte bằng 0 không thay được bằng chứng ấy. Nếu cleanup hoặc ghi audit thất bại, không mở phiên mới. Kiểm offline bằng [Test-TBS5530-Launch.ps1](tools/Test-TBS5530-Launch.ps1), [Test-TBS5530-Heartbeat.ps1](tools/Test-TBS5530-Heartbeat.ps1) và [Test-TBS5530-AutonomousSession.ps1](tools/Test-TBS5530-AutonomousSession.ps1).

## Chẩn đoán Android: đọc lại cấu hình đầu ra TS của Sony

`SONY_TS_OUTPUT_READBACK` chỉ có trong debug, mặc định **TẮT**. Mục đích là kiểm cấu hình đầu ra sau khi Sony báo khóa, không sửa cấu hình hoặc khẳng định đã có dữ liệu ở chân chip/FIFO. Helper [Invoke-TBS5530-AutonomousSession.ps1](tools/Invoke-TBS5530-AutonomousSession.ps1) nhận `-SonyTsOutputReadback -ControlMirror -Profile EXPERIMENTAL_DIAGNOSTIC -ReadApi NATIVE_USBFS_URB_8X4096`. Cần consent riêng cho đúng danh sách đọc; cờ không ghép với alt0, chuẩn bị endpoint, 8192 byte hoặc ControlPlaneOnly.

Có tối đa hai ảnh chụp trạng thái: `AFTER_LOCK` và `BEFORE_FIRST_URB` sau stream-select `8A 0A 00`. Mỗi ảnh gồm 20 giao dịch EP0: đọc sync bank20/10, chọn bank00, đọc từng byte C4/C6/80/81/95/A9/33, chọn lại bank20 rồi đọc sync lần nữa. Chỉ hai selector writes thay bank; không ghi output mode, drive hoặc PLP. Mỗi kế hoạch có hash, quyền dùng một lần, cùng owner/worker và giới hạn 5 giây được kiểm quanh từng lệnh. Không ép đóng syscall đang chạy khi quá hạn.

**Giới hạn nguồn:** C3,96/97,D9, actual PLP và L1 chưa có phép đọc được xác minh, nên ghi `NOT_AVAILABLE`. Nguồn có đọc 81 ở nhánh serial nhưng chưa chứng minh đọc trong chế độ parallel đích an toàn; tính không gây tác dụng phụ trên silicon vẫn `UNKNOWN`. Ảnh chụp không đồng thời (`NONATOMIC`); bank được suy từ kế hoạch (`PLAN_INFERRED`), không phải đã đọc lại bank selector. Xem nguồn và SHA trong [RESEARCH.md](docs/project/RESEARCH.md).

Mọi short/error, mất owner/quyền, đổi lock hoặc lỗi audit đều dừng trước Bulk. Sau I/O không chắc chắn, bank là `UNKNOWN`: không cố restore bằng lệnh mới, không retry hoặc replay firmware. Không có đọc I²C khi URB đang chờ. `report.json` lưu raw registers, sync trước/sau, thời gian, intent/dispatch/completion và kết quả restore; các stage snapshots/audits nằm trong session-private. Helper xuất report và EP0 đã hash sau terminal/drain, ngoài Git. Kiểm phần mềm không thay thế phép đo trên tuner; kết quả mới nhất ở [TEST-MATRIX.md](docs/project/TEST-MATRIX.md).

# Download

* [Download DVB-T Driver on Google Play](https://play.google.com/store/apps/details?id=info.martinmarinov.dvbdriver)
* [Download DVB-T Driver on Amazon](https://www.amazon.com/gp/mas/dl/android?p=info.martinmarinov.dvbdriver)
* Download latest pre-compiled APK from GitHub: [app-release.apk](https://github.com/martinmarinov/AndroidDvbDriver/releases/latest)

# Usage

The driver has an Activity that demonstrates direct connection to hardware
and allows dumping the raw DVB-T/T2 transport stream to a file.

A socket interface is built on top of that functionality to allow the driver
to be used by third party apps over TCP. If you want to use the driver via the
exposed TCP interface, you have to do the following steps from your own app:

1. Launch an android intent
```java
startActivityForResult(new Intent(Intent.ACTION_VIEW)
            .setData(new Uri.Builder().scheme("dtvdriver").build()), SOME_CODE);
```
1. If the activity returns successfully, you will receive two TCP port numbers to bind to.
One of them is the control port where you can send commands and receive responses.
The other port will provide the raw TS stream.
1. You can then start sending commands over the control port and process the TS stream.

For actual details on the protocol, take a look at the `dvbservice` module. There
are no official docs provided, if you would like to volunteer to write one get in touch with me.

There is also a handy "debug" mode that allows the driver to play the pre-recorded TS dumps
as if they are coming from a real device. This mode is extremely useful during development.

Note that this driver does not provide channel/programme scanning/playback capabilities or any transport stream processing.
If you would like to write an app for DVB playback, you have to implement all of these yourself.

## B2 debug: handoff cùng phiên và helper PowerShell

B2 chỉ là phép thử H-AUTOINLEN có giới hạn, chưa xác nhận nhận TS trên Android. Sau bootstrap sạch bằng backend original, app giữ proof private trong cùng session/physical epoch; launch operational riêng không được mượn proof. Attach operational đến muộn có thể được giữ tạm trước owner, nhưng phải cùng deviceId/path/VID:PID, không detach xen giữa, OS permission còn đúng và raw descriptor đủ 50 byte/hash đã pin trước claim/control/Bulk. Sau bước đóng cửa nhận sự kiện trước owner, mọi attach tiếp vẫn dừng theo ABA guard; generic permission và driver khác không đổi. Xem [ADR-048](docs/project/DECISIONS.md).

App ghi `handoff-events.jsonl` bằng cùng clock elapsed ns để phân biệt broadcast, intent, UI publication và permission. [Helper runtime](tools/Invoke-TBS5530-AutonomousSession.ps1) chỉ thu và đối chiếu hash journal sau drain, kể cả dừng trước reload. Không tăng heartbeat/deadline hoặc tự retry. Handoff Samsung thực vẫn chưa được kiểm; fake/emulator không chứng minh OEM sẽ giao event đúng thời hạn.

Các helper Phase 3.1 có tiếng Việt dùng UTF-8 BOM để Windows PowerShell 5.1 không đọc nhầm dấu nháy; PS7 vẫn được hỗ trợ. [Initializer helper](tools/Initialize-TBS5530-ReloadStore.ps1) dùng SHA API tương thích cả hai shell. Kiểm offline encoding bằng [Test-TBS5530-ScriptEncoding.ps1](tools/Test-TBS5530-ScriptEncoding.ps1). Không thay cách cấp quyền: card init store không tuner và card B2 là hai phê duyệt riêng; không chạy helper live chỉ vì tests PASS. Kết quả mới nhất: recovery witness và init store thật đã verified; các run riêng sau sửa initial attach và điều phối permission đã tới bootstrap/reload 132/132 và nhận 65536 byte trên Samsung. Tất cả 16 URB có status 0/actual 4096 nhưng app INVALID_TS, không PAT/PMT, nên chưa TS_SMOKE_PASS hoặc ROOT_CAUSE_CONFIRMED. Handoff operational ghi một broadcast và một intent trước descriptor seal/owner; không hồi tố thành timeline B1. Candidate audit sau drain đã được chạy đúng một quality run mới: audit 35 records/3903 byte có hash seal đúng, 16 URB thành công và 65536 byte nhưng vẫn INVALID_TS/16 sync losses/PAT 0/PMT 0. Đã drain/rút tuner; theo yêu cầu người dùng chỉ hoàn tất chứng cứ/phân tích offline rồi dừng sửa, không run hoặc retry thêm. Xem [trạng thái hiện tại](docs/project/STATUS.md); mọi lượt sau cần card/hash/phê duyệt mới, không tự retry hoặc mở lại budget.

Nếu cài lại làm history trở về đúng prefix bảy records đã biết, [recovery helper](tools/Invoke-TBS5530-HistoryRecovery.ps1) có tùy chọn `-RestoreExactHostWitnessV2` chỉ cho nối năm records còn thiếu từ witness host 996 byte đã khóa. Tùy chọn này cần PS7, `-ExpectedHelperSha256`, APK/helper/plan/card được duyệt riêng, no-tuner, marker hợp lệ và journal dùng một lần trước append/fsync. Không nhận candidate tùy ý, không ghi đè hoặc xóa history/journal, không cấp quyền tuner hoặc chứng nhận toàn lịch sử trước witness. [Tests offline](tools/Test-TBS5530-WitnessRecovery.ps1) dùng hai tệp private qua `-BaselinePath` và `-WitnessPath`, không ghi app; tests PASS không phải quyền chạy live. Mặc định không có tùy chọn này vẫn là recovery cũ chỉ thêm hai khóa cấm.

[Permission-first coordinator](tools/Invoke-TBS5530-PermissionFirstB2.ps1) chỉ điều phối một B2 mới được duyệt: mở màn hình permission-only hiện có trước khi bắt đầu session TTL 300 giây, chờ fresh OS permission tối đa 60 giây rồi gọi runtime helper unchanged. Không tăng TTL hoặc giảm guard upload 285 giây; không I/O tuner hay B2 grant trong phase chuẩn bị, không auto-tap/default/retry. Cần pin coordinator/APK/firmware/helper và card riêng; ba tham số `ExpectedAttachmentHistorySha256`, `ExpectedReloadStoreSha256`, `ExpectedReloadAnchorSha256` bắt buộc lấy từ witness đã được duyệt sau run gần nhất, không reset về hash init. Coordinator kiểm ba pins trước preparation rồi chuyển nguyên cho runtime helper; [tests fake](tools/Test-TBS5530-PermissionFirstB2.ps1) dùng `-FirmwarePath` private và không gọi tablet.

Chỉ boundary audit quan sát của native TS B2 được buffer giới hạn 64 records/65536 byte để tránh ghi/fsync mỗi boundary khi receive. Marker `.started` phải bền trước nhận; sau drain mới commit audit exact và `.complete` kèm SHA. Crash, thiếu audit hoặc lỗi commit không được chứng nhận TS; grant/consume/history/store và mọi authority check vẫn giữ nguyên vị trí và độ bền. Xem [ADR-052](docs/project/DECISIONS.md); benchmark emulator không chứng minh chất lượng TS Samsung đã được sửa.

# Auto start

The driver will emmit `info.martinmarinov.dvbdriver.DVB_DEVICE_ATTACHED` action when a compatible USB tuner has 
been connected to the Android device. You can use this intent to launch your application automatically.

# Supported hardware

Currently:
* RTL2832 with tuner chip R820t
* RTL2832 with tuner chip E4000
* RTL2832 with tuner chip R828D (DVB-T2 support for MN88473, MN88472, CXD2837ER & CXD2841ER devices)
* RTL2832 with tuner chip FC0012
* RTL2832 with tuner chip FC0013
* MyGica T230
* MyGica (Geniatech) T230C DVB-T/T2/C
* MyGica Pad TV Tuner PT360
* EVOLVEO XtraTV stick (Some unbranded Android DVB-T sticks)
* PCTV AndroiDTV (78e)
* Potentially other AF9035 dongles. If your dongle works let me know and I will add it to this list.

The driver is not limited to these devices only. If a device has a Linux kernel driver, then it probably could be ported.
If you have ported a driver for a device, get in touch with me so we can add it to the main driver.

# Apps that use the driver

* [Aerial TV](http://aerialtv.eu/)
* [DVBTTelevizor](https://play.google.com/store/apps/details?id=net.petrjanousek.DVBTTelevizor)

If you would like your app to appear here, open a GitHub issue and I will be happy to add it!

# Important advice

Google Play has very strict rules about apps that can be used to access any TV content. Make sure not to include any TV sceenshots or anything that looks like TV screenshots in your app listing. The description of your app should be very clear that your app does not bundle any content and content is provided by the end user.

Please keep in mind that even if you follow all of the above, your app may still get suspended. The above is just a friendly sugggestion from a developer that has already gone through the worst of this process. This is not official or legal advice.