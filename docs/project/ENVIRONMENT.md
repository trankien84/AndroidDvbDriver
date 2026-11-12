# Kiểm kê môi trường

## 2026-11-12, sau B3 — Samsung đã thử ba cấu hình, tuner đã rút; candidate report mới chưa cài

Samsung SM-X926B/API36/boot47 dùng APK `1a15959d99bef5052b555cdfa3db3771489c1f80a3048bcf391736195fba45e7` trong ba lượt receive thật. Đã drain cả ba, người dùng xác nhận rút tuner/ngắt nguồn sau lượt cuối. Giữ history cũ 1328 byte và reload store/anchor lịch sử; B3 journal theo session không reset chúng. Không VM/WSL/driver/ownership/toolchain thay đổi, không EEPROM hoặc LNB/DC.

Sau live chỉ sửa classification report và kiểm host. APK candidate debug mới SHA `0a57ef9d9bb2976795826f02cc0444e5901e35736192aa8b250263f132f6107c`, chưa cài Samsung hoặc chạy tuner. Đã xuất chỉ đọc APK đang cài đúng hash run và lưu riêng với APK candidate/debug-test/release trong reports/final-apks. Màn hình lúc đầu không Awake nên dừng trước xuất; sau người dùng mở mới kiểm và pull, không tự bật/đổi khóa máy. 87 tệp source đã dùng Samsung được đối soát frozen SHA và lưu ngoài Git, tách bản sửa report sau live.

Toolchain/pins vẫn như mốc trước. Native core không thay sau live, JVM cuối app 99/driver 485 PASS + 5 SKIP/usbxfer 49; build/app lint/release isolation/ABI PASS. Emulator candidate report cuối dùng instance riêng read-only/no-snapshot, focused 12/12 PASS; đã emu kill đúng serial và attached process exit 0. Không dùng Samsung làm emulator test. Ngân sách hữu hạn tổng năm lượt đã hết, không service nhận USB hoặc helper live do phiên còn chạy.

## 2026-11-12, chốt B3 offline tới 21:56 +07 — Emulator đã đóng, Samsung chỉ đọc inventory

Candidate APK debug trên host SHA `1a15959d99bef5052b555cdfa3db3771489c1f80a3048bcf391736195fba45e7`, chưa cài Samsung. JNI stream/legacy build bốn ABI, hai ABI 64-bit ELF align 16 KiB; release isolation đạt. Toolchain/pins không đổi. Chạy emulator instance riêng 5580 với read-only/no-snapshot/no-window/no-audio, không USB passthrough; boot readiness đầu timeout, giữ process rồi kiểm thấy boot_completed=1 trước tests, không restart. Sau test emu kill đúng serial, attached process exit 0, cổng 5580/5581 không listener; log emulator có thông báo snapshot nội bộ khi thoát dù flags không lưu, không suy đã tạo snapshot VM hoặc thay AVD bền.

Đã dùng alias ADB xác minh Samsung model SM-X926B/API36/boot47/Awake cho card, không install/launch app hoặc tuner I/O mới. Định danh/IP/log raw giữ private ngoài Git. Không dùng adb mặc định từ scrcpy; mọi lệnh thiết bị dùng đúng SDK adb.exe và explicit serial. Firmware ngoài Git đã kiểm đúng 8192 byte/SHA b97cf208…; chưa chuyển lên tablet trong nhiệm vụ này. B3 vẫn chờ card/OK B3 và xác nhận cold thực từng lượt.

59 tệp Phase 3.1 Java/C/Python được chuẩn hóa LF; `.ps1` giữ BOM/line ending gốc. Agent helper/natives chỉ fake/offline; host heartbeat fixture Git Bash timeout vẫn FAIL, không cài package hoặc đổi policy. Không VM/WSL/driver/ownership/persistent environment thay đổi. Hồ sơ và manifest ngoài Git ghi các limits này.

## 2026-11-12, chuẩn bị B3 từ 20:31 +07 — Chỉ offline, chưa thay Samsung

Đã kiểm baseline branch `chore/modernize-android-build`, HEAD `9bee570aa5e59879e11b7c5b8b1086b80c5fb05b`, tree sạch. JDK 25.0.5.0.3 qua Machine JAVA_HOME junction, Gradle Wrapper 9.8.2/AGP 9.5.0; source.properties NDK 30.0.16248370/CMake 4.1.2/Build-Tools 37.0.0/ADB 37.0.1/emulator 37.2.13. Giữ SDK `D:\Android\Sdk`, AVD `D:\Android\AVD`, minSdk 21 và Java 8, không cài/nâng toolchain hoặc đổi biến môi trường bền.

Python đã có `C:\Python312\python.exe`, analyzer chỉ stdlib; native host tests dùng GCC 16.2.0 đã có, không MSYS2 shell hoặc cài dependencies mới. AVD hiện có TBS5530_Runtime_API37/x86_64, chưa boot trong mốc này. Không ADB Samsung, install APK, USB ownership, VM/WSL hoặc hardware I/O mới; trạng thái Samsung cuối phiên trước là lịch sử, chưa đo lại.

GitNexus CLI 1.6.12 có help nhưng status không hoàn tất sau hơn 254 giây, đã dừng đúng process tree của phiên; không force/rebuild/repair hoặc chứng nhận index hiện hành. Nguồn hiện tại, diff, JVM và host native tests là căn cứ. Evidence main/analyzer có bundle riêng dưới D:\TSDuck; mọi serial/grant/raw/APK/firmware chỉ ngoài Git.

## 2026-11-12, audit quality live đã đóng — Samsung hiện giữ candidate mới và consumed witnesses mới

Preflight 19:32 +07 xác minh SM-X926B/API 36/user 0/boot 47/Awake/current USB empty, exact installed APK cũ 3ee6cf24… rồi install-r một lần candidate SHA `1efb3aaf796e7c4427a66c713908d5be5f41e18a7c645c1d3c906facd8789839`. Sáu witnesses/history 1162 byte trước/sau exact, không uninstall/clear/rearm. User xác nhận cold off ≥5 giây/đèn tắt/RF IN như B1 và đã cắm trước preparation theo card mới; không default USB hoặc auto-tap.

Run 19:40:55.955–19:41:23.263 +07, một preparation/B2, app-PID collector 19:40:43.398–19:41:23.479 +07 đã đóng. Terminal native outstanding 0/quarantined=false/RUNTIME_DRAIN_CONFIRMED, user đã rút/ngắt nguồn sau thông báo. Sau đó chỉ export 32 tệp app đã lưu/hash và phân tích Node/TSDuck offline, không USB transfer hoặc run thêm. Không hậu kiểm USB/fd mới sau rút; không suy một fd count chưa đo.

Witness cuối: history 1328 byte/16 records/SHA 15666f1a…, journal 308 byte/a6f07eb2… và anchor 224 byte/a1dbd197…; marker/init tombstone/recovery journal hashes nguyên. Candidate audit đã chạy runtime và SYNCED_CLOSED, TS vẫn INVALID_TS. Không lấy hashes baseline 996/1162 byte cho run tương lai hoặc reset store.

Giữ branch/HEAD chore/modernize-android-build / c4afcf9d1cd125670106f26dd84034908322377e, toolchain/SDK/driver/USB ownership không đổi. Source 33 tệp nguyên suốt live, sau đó chỉ README/sáu hồ sơ/báo cáo được cập nhật; không build/tests mới sau run vì không code thay đổi. Hai helper live/collector đã exit, emulator trước đã dừng; không service/watchers do phiên để lại. Người dùng yêu cầu kết thúc phần sửa trong phiên sau thu/phân tích, không chờ quyền để chạy tiếp.

## 2026-11-12, sau B2 nhận dữ liệu và phiên audit offline bắt đầu 18:32 +07

Samsung SM-X926B/API 36 chạy APK 3ee6cf24… trong run 17:55–17:56 +07 đã nhận 65536 byte, app INVALID_TS; user xác nhận rút/ngắt nguồn sau drain. Không có phép thử tuner hoặc install tablet nào sau khi sửa audit. History mới 1162 byte/14 records/hash 855d2134…, reload journal 223 byte/e4f2f2f5… và anchor 159 byte/da80da1e… là witness đã consume, không quay lại baseline init. Không auto-tap/Always/default hoặc đổi khóa/timeout; khi cần ADB tablet phải kiểm màn hình, báo người dùng mở nếu tắt.

Giữ branch/HEAD `chore/modernize-android-build` / `c4afcf9d1cd125670106f26dd84034908322377e`, cùng JDK 25.0.5.0.3/Wrapper 9.8.2/AGP 9.5.0, compileSdk 37.2/minSdk 21/Java 8, SDK/NDK/CMake đã có. Không package/toolchain/persistent environment đổi. Build cuối 351 tasks/35 executed, app JVM 95 tests/0 FAIL/0 SKIP; app debug/test/release/lint exit 0. Editor báo debug/test Java ngoài classpath, nên dùng Gradle compiler/tests làm evidence, không giả editor clean hoặc driver lint PASS.

Emulator riêng API 37/x86_64 cổng 5580, read-only/no-snapshot/no-window/no-audio; Android CLI start help không hỗ trợ cổng/read-only/no-save riêng nên dùng executable emulator đã xác minh với đúng flags. Hai lượt instrumentation fake/cache lần lượt 27 và 28 tests PASS, không production store hoặc USB. Đã emu kill đúng serial, emulator process exit 0 và cổng 5580/5581 không listener. Benchmark audit có biến thiên lớn: lần đầu snapshot 214,8714 ms/buffered 16,1782 ms/postdrain 18,1421 ms; lượt cuối 3662,6792/360,1311/320,9210 ms, cùng 34 records/final bytes 1486. Không suy nguyên nhân biến thiên hoặc tốc độ Samsung từ các số emulator này.

APK audit cuối trên host/emulator SHA `1efb3aaf796e7c4427a66c713908d5be5f41e18a7c645c1d3c906facd8789839`; release SHA `3eac0a8e8ab59c496cc5358bda84c7fa18b3415d2de73dd2c4a907610cb7948c`, chưa installed witness Samsung. Coordinator parameterized SHA `86e457887e0043868e59cecb26e190aaca93f84d08503ffd84e36441d3f6fcad`, 41/41 fake guards PASS; runtime helper 1bbb22fb… không đổi. Bundle `phase31-ts-quality-offline-20261112-183244-7b4e5c12` giữ logs/APKs trước và sau riêng; không collector/emulator do phiên còn chạy. GitNexus index trước đã xác định chậm hơn HEAD một commit, không reindex/repair hoặc dùng graph chứng nhận thay source/diff/tests.

## 2026-11-12, run initial-fix và coordinator permission-first

Tablet đã install-r APK 3ee6cf24… sau exact card riêng, trước/sau history 996 byte/pins nguyên; màn hình Awake/no-tuner preflight đúng, user xác nhận fresh power-off epoch/RF như B1. Run helper vẫn SHA 1bbb22fb…/TTL 300 giây/upload guard 285 giây, exit 1 pre-native budget refusal; logcat collector chỉ app PID đã dừng, user rút sau typed drain. Không reset/rearm/journal clear hoặc toolchain/driver/host đổi.

Coordinator permission-first mới chỉ là script PS7 host, dùng Activity hiện có và fake tests 34/34; không Android source/APK build thêm hoặc ADB tablet trong scope offline này. Bao gồm O_EXCL host dispatch.once/flush, hash pins, fresh permission receipt 60 giây deadline, giữ ADB 1500 ms và runtime TTL 300 giây. Không sửa runtime helper hoặc tự reconnect/auto-tap. Bundle `phase31-permission-first-20261112-173615-2b868332` giữ design/tests/card.

## 2026-11-12, sau B2 — Initial-attach sửa và kiểm hoàn toàn offline/emulator

Giữ branch/HEAD `chore/modernize-android-build` / `c4afcf9d1cd125670106f26dd84034908322377e`, toàn edits trước bảo toàn. Không toolchain/package/env persistent đổi. Dùng cùng Wrapper/JDK/SDK đã đọc trong phiên; app JVM 95 tests và debug/test/release/app lint build exit 0, 351 tasks/34 executed, không driver lint rerun. B2 helper 19 và handoff export 38 checks PASS trong child PowerShell riêng, không ADB tablet.

Emulator AVD TBS5530_Runtime_API37/API 37/x86_64 riêng cổng 5580, read-only/no-snapshot/no-window/no-audio, chỉ install emulator candidate và chạy 20 fake/cache tests PASS; đã emu kill đúng serial, process exit 0, ports 5580/5581 không listener. Không tablet commands, tuner I/O, reset/rearm hoặc ownership change trong scope offline này. Release không class/options initial-attach/recovery debug; current Samsung APK/store/history chưa đo lại, không lấy APK candidate làm installed witness.

## 2026-11-12, phiên 15:39 +07 — Kết quả: Recovery/init thật và B2 một lượt trên Samsung, không đổi host/toolchain

Samsung SM-X926B/API 36/boot 47/user 0, explicit ADB alias; APK mới installed SHA `9493d0b66027827a532a6aa3f0f9db868b67062c0a3b9a4397e45cbb7800fada`. User duyệt riêng recovery exact một lần, init v3 một lần, sau báo cáo Phần I PASS đã gửi nguyên văn TIẾP TỤC B2 và duyệt APK addendum; nguồn chữ Y/cáp/RF IN như B1 được user xác nhận. Màn hình Awake trước các launch, không thay khóa/timeout hoặc auto-tap/default USB. Logcat chỉ PID app, đã dừng collector sau terminal; không log toàn hệ thống.

Recovery 16:20:10–14 +07 và init 16:25:00–04 exit 0; init USB monitor 30 snapshots PASS đã đóng. B2 helper 16:35:52–16:37:25, ARMED xác minh grant/heartbeat lúc 16:36:16 trước yêu cầu cắm; helper exit 1 với typed drain terminal, không native URB hoặc A0 bắt đầu. Tablet được user rút/ngắt nguồn sau drain; chưa đọc dumpsys sau rút. Hậu kiểm fd phụ timeout nên trạng thái fd UNKNOWN, không suy syscall đang chạy hoặc ép đóng app. Không VM/WSL/driver/USB owner/toolchain đổi; emulator riêng đã dừng trước live, không service/helper/collector do phiên còn chạy.

## 2026-11-12, phiên 15:39 +07 — Kiểm recovery v2 bằng build hiện có và emulator riêng

Đọc lại AGP 9.5.0/Wrapper 9.8.2 có checksum, compileSdk 37.2/minSdk 21/Java 8, Build-Tools 37.0.0/NDK 30.0.16248370/CMake 4.1.2 và source.properties hiện có; Gradle runtime JDK 25.0.5.0.3 từ JAVA_HOME Machine, không đổi package/toolchain hoặc persistent env. PS 7.6.9 dùng helper recovery, initializer vẫn kiểm tương thích PS5.1 từ lượt trước. VS Code test discovery không có focused test item và runTask không có invocation context nên dùng Wrapper hiện có; không coi tool failure là tests PASS.

Emulator TBS5530_Runtime_API37/API 37/x86_64, emulator 37.2.13, cổng 5580 riêng, read-only/no-snapshot/no-window/no-audio; boot responsive rồi cài APK chỉ emulator và chạy 18 instrumentation fake/cache tests PASS. Đã emu kill đúng serial, attached process exit 0, cổng 5580/5581 không listener. Tablet không cài APK mới; chỉ đọc screen/marker/journals thấy Awake/initialized và journal v1/v2 absent. GitNexus đúng path/branch nhưng index sau HEAD một commit, không force/reindex hoặc dùng graph làm authority; LSP debug usages rỗng không chứng minh không có callers, source/tests là chuẩn.

## 2026-11-12, phiên 15:25 +07 — Cài lại Samsung theo yêu cầu mới, chỉ đọc dữ liệu sau cài

Samsung SM-X926B/API36/boot47, current user 0, explicit ADB alias giữ riêng; current USB inventory rỗng trước/sau cài. Power ban đầu Awake, ngay trước install chuyển Dozing nên dừng trước lệnh cài; người dùng mở màn hình, kiểm lại Awake rồi mới `adb install -r` đúng APK. Một install exit 0, installed SHA `11983e5c56ba20ec2e32b583fb95ceb68a01493f5094d9335ff0933d0e20d920`; không uninstall/clear hoặc đổi khóa màn hình.

Không launch app thủ công hoặc gọi restore. Sau cài đọc được history 581 byte/7 records và ba targets/tombstone vắng; không có log hệ thống backup/restore để xác định nguồn dữ liệu cũ. Không tự quy nguồn cho Android Auto Backup hoặc coi cài lại đã phục hồi witness mới nhất. Source/helper/APK/toolchain không sửa; chỉ sáu hồ sơ bổ sung. Raw/serial/logs ở bundle `phase31-app-reinstall-20261112-152643-2f333465`, không VM/WSL/emulator/ownership change.

## 2026-11-12, 15:18 +07 — Preflight v2 chỉ đọc, package/user state chưa rõ

Sau phê duyệt exact card/helper v2 và xác nhận vật lý mới, power read thấy Awake rồi preflight qua SM-X926B/API36/boot47/current USB bằng guard đã sửa. Không đổi màn hình hoặc khóa máy. History export bằng exec-out/run-as trả thông báo unknown package dài 54 byte, ADB exit 0; chưa kiểm package manager/user/profile nên không khẳng định nguyên nhân, uninstall hoặc data loss. Không dùng shell exit của ADB thay tính hợp lệ nội dung.

Functions và body của wrapper v2 giữ byte-exact; launcher chỉ remap output sang namespace `approved-preflight` mới để giữ evidence lần lỗi trước, không ghi đè raw/card. Explicit serial vẫn giữ riêng. Không install/initializer/grant/store/tuner hoặc ADB sau failure; không VM/WSL/emulator/toolchain thay đổi. Source/helper v2 giữ nguyên; snapshot source freeze được duyệt trước run khác các docs bổ sung sau run theo quy tắc hồ sơ, không phải runtime code đổi.

## 2026-11-12, phiên 13:28 +07 — Samsung chỉ đọc; sửa helper offline trên hai PowerShell

ADB pin explicit alias đã chọn, không reconnect: đọc power/model/API/boot/USB đều exit 0; Samsung SM-X926B/API36/boot47, power ghi `mWakefulness=Awake` lúc 13:30:10 +07. Không tự bật màn hình hoặc đổi timeout. Preflight dừng 13:30:47 trước history/APK/targets/install; không ADB sau failure. Raw/serial chỉ ngoài Git dưới bundle `phase31-b2-live-20261112-133010-a5b7ea58`.

Kiểm sửa offline trên PS7.6.9 và Windows PS5.1 hiện có, mỗi shell 33/33 PASS; child PS5.1 bỏ PSModulePath kế thừa chỉ trong process, không policy/PATH persistent. Không Gradle/build/emulator/dependency/VM/SSH/WSL hoặc ownership change. Source thay đúng initializer helper và test liên quan sau quyền mới; APK/runtime helper/golden/firmware/templates không đổi, source freeze cũ không còn là phê duyệt cho hai tệp sửa.

## 2026-11-12, phiên 13:14 +07 — Kiểm wrapper trên máy tính, không truy cập tablet

PowerShell 7.6.9 / .NET 10.0.14, Windows, cwd repository; HEAD vẫn `c4afcf9d1cd125670106f26dd84034908322377e`, sáu hồ sơ đang có edits của lượt trước được giữ. Bản wrapper offline này dùng ProcessStartInfo.ArgumentList và được kiểm trên PS7, không tuyên bố hỗ trợ PS5.1; helper initializer frozen không sửa. Không cài hoặc đổi công cụ/môi trường.

Tests chạy tiến trình pwsh giả ADB, chỉ thay đường gọi executable và history tổng hợp trong fixture; không gọi adb.exe, tablet, emulator, VM/SSH/WSL hoặc USB. Fixture được xóa sau tests, evidence giữ ngoài Git trong `phase31-wrapper-fix-20261112-131739-13025118`. Trạng thái màn hình tablet hiện UNKNOWN vì chưa đọc máy; lần cần ADB sau phải kiểm, báo người dùng nếu tắt và chờ mở, không tự đổi khóa màn hình.

## 2026-11-12, phiên bắt đầu 12:55 +07 — Samsung chỉ được liệt kê và đọc model, chưa cài APK

- **Checkout:** Branch `chore/modernize-android-build`, HEAD `c4afcf9d1cd125670106f26dd84034908322377e`, tree sạch trước ghi hồ sơ. Hai manifest và 20 source files/11 artifacts khớp size/hash; chỉ sáu hồ sơ được cập nhật sau chốt dừng, không đổi helper/source/APK frozen.
- **Công cụ thực:** Windows 10.0.19045, PowerShell 7.6.9; pinned ADB `D:\Android\Sdk\platform-tools\adb.exe`, version 1.0.41 / 37.0.1-15733141. Android CLI hiện có được đọc `--help`, exit 0; không install/update SDK, dependency hoặc đổi môi trường persistent. Không build/Gradle/emulator/VM/SSH/WSL trong phiên này.
- **Thiết bị:** SDK ADB và ADB MCP đều liệt kê hai aliases của Samsung; wrapper pin một explicit serial từ transport đã chọn, giá trị chỉ lưu private ngoài Git. `getprop ro.product.model` trả đúng `SM-X926B`, exit 0, 10 byte. API36/boot/history/installed APK và current USB inventory chưa được xác minh mới vì wrapper host dừng trước các lệnh đó.
- **Lỗi host đã tái hiện offline:** `CopyToAsync().GetAwaiter().GetResult()` của wrapper mới phát thêm `System.Threading.Tasks.VoidTaskResult` trên PowerShell/.NET hiện tại; hàm trả hai objects thay một chuỗi model. MemoryStream tái hiện đúng 2 objects, không ADB/tuner. Không quy lỗi cho Samsung hoặc helper initializer đã freeze; chưa sửa/chạy lại wrapper live.
- **Phạm vi an toàn:** Người dùng xác nhận đã rút tuner khỏi Samsung và nguồn chữ Y, giữ rút suốt Phần I. Không install/uninstall/clear, launch app, grant, tombstone/store write, tuner I/O hoặc thay quyền sở hữu USB. Không tiến trình helper/capture/emulator do phiên này để lại.
- **Giới hạn công cụ:** Không dùng GitNexus cho kiểm hash và thao tác tài liệu tự chứa; không xác minh lại freshness/FTS và không dựa graph làm evidence. Nguồn hiện tại, manifests, raw model và fixture offline là căn cứ.

Bundle `phase31-b2-live-20261112-125721-9a964d76` dưới `D:\TSDuck\logs` và `D:\TSDuck\reports` giữ raw ADB aliases, scripts, command/exits/hashes và chẩn đoán; không đưa serial/IP/grant hoặc đường dẫn cá nhân vào hồ sơ. Phần I BLOCKED trước install, Phần II NOT RUN.

## 2026-11-12, phiên bắt đầu 11:20 +07 — Sửa handoff và kiểm emulator riêng, không Samsung

- **Checkout:** Xác minh sạch trước sửa, branch chore/modernize-android-build / e8cb2ceab7c6fd7a741fd8230381b4f59935540c. Không stage/commit/push/reset/clean/sync references hoặc đổi toolchain. Source/fixtures/logs hiện tại và kết quả chạy mới là chuẩn; không dùng hash APK cũ.
- **Declared/installed:** Đọc lại AGP 9.5.0, Gradle 9.8.2 có checksum, compileSdk 37.2, Build-Tools 37.0.0, minSdk 21, Java 8, NDK 30.0.16248370 và CMake 4.1.2 từ build và source.properties. JAVA_HOME process đúng JDK 25; Wrapper chạy trên JDK đang có. Không khảo sát latest stable vendor, install/update SDK/dependencies hoặc chỉnh môi trường persistent.
- **Shell/helper:** PS 7.6.9 và Windows PS 5.1.19041.7844, RemoteSigned, không Bypass/Unblock-File. UTF-8 BOM cho bốn scripts non-ASCII sửa trong Phase 3.1; initializer SHA API tương thích .NET Framework đã kiểm thực. Chỉ child PS5.1 bỏ PSModulePath kế thừa từ PS7 để dùng module mặc định; không đổi global policy/PATH.
- **Native/Python:** GCC 16.2.0 hiện có compile `-Wall -Wextra -Werror` cho hai fixtures, không USB syscall; exe riêng đã xóa. Python 3.12.2 tại `C:\Python312\python.exe` được agent cấu hình trước commands, không pip; 77/77 có private firmware/CSV. Node 26.7.0 chạy generator 8/8. Raw firmware/history/TS/APK/trace riêng vẫn ngoài Git dưới `D:\TSDuck`.
- **Emulator:** Android CLI help/list đọc lại; CLI không liệt kê AVD, native `-list-avds` có TBS5530_Runtime_API37. Đọc config/system image, kiểm cổng rồi launch riêng emulator-5580 bằng emulator 37.2.13 với read-only/no-snapshot/no-window/no-audio; không USB passthrough/wipe/AVD sửa. Chờ hữu hạn 240 giây xác nhận API 37/x86_64, boot log 115157 ms. ADB MCP list đúng serial ban đầu offline; SDK ADB được pin `-s emulator-5580` cho mọi install/instrumentation/emu kill, không shell/cài trên physical aliases.
- **Test scope:** App 48 có 44 PASS/4 FAIL lifecycle do Activity trên emulator model ngoài allowlist không tạo session; không sửa guard hoặc gọi failure là SKIP. Driver 19, handoff mới 3 và initializer 5 PASS trong phạm vi fake đã chọn. Actual Samsung lifecycle/current history/store/handoff/B2 vẫn NOT RUN. Sau emu kill, process sở hữu exit 0 và cổng 5580/5581 không listener; không có tuner owner/URB mới cần drain.
- **GitNexus:** Gateway xác minh đúng canonical path/branch nhưng index ban đầu sau HEAD 1 commit. CLI 1.6.12 help có index-only; một refresh index-only với 2 workers exit 0, báo success 127.7 giây nhưng context MCP sau đó vẫn trả staleness behind 1 dù có symbol. Không force/repair hoặc chứng nhận graph fresh/FTS hoàn chỉnh; graph chỉ gợi bề mặt regression, source/diff/tests là căn cứ. Không tạo instruction/skill/config hoặc context files cho index.
- **Delegation:** H1/helper offline có reports và hashes; task native agent lỗi 400 model trước execution, parent tự chạy native thành công, không relaunch hoặc thay model. Không VM/SSH/WSL/USBPcap/TSDuck live, USB ownership hoặc Samsung commands trong phiên này.

Bundle mới `phase31-handoff-offline-20261112` giữ commands/logs/XML/hashes và hai cards mới; xem [TEST-MATRIX.md](TEST-MATRIX.md), [RESEARCH.md](RESEARCH.md), [WORKLOG.md](WORKLOG.md). Không dùng evidence emulator để xác nhận tuner hoặc TS.

## 2026-11-12, phiên 07:38 +07 — Offline H-AUTOINLEN, chưa thao tác Samsung hoặc tuner

- **Checkout:** Xác minh đúng `chore/modernize-android-build` / HEAD `5c1c3a0a02df5aeb63258b01b80e9ccc0116515e`, working tree sạch trước sửa. Bốn references chỉ đọc tại exact SHA trong [RESEARCH.md](RESEARCH.md), không sync/fetch hoặc sửa references. Không stage/commit/push/reset/rearm.
- **Declared/installed đã đọc lại:** AGP 9.5.0, Wrapper Gradle 9.8.2 có checksum, compileSdk 37/minor 2, minSdk 21, Java source/target 8, Build-Tools 37.0.0, NDK 30.0.16248370, CMake 4.1.2. `JAVA_HOME` Process và Machine tới JDK 25 tồn tại; source.properties NDK/CMake khớp. Đây là baseline khai báo/cài và đã chạy JVM, không khảo sát latest vendor hoặc đổi toolchain.
- **Công cụ offline:** Python 3.12.2 được cấu hình tới `C:\Python312\python.exe`, không pip/dependency; Node 26.7.0, TShark 4.6.9, Java 25.0.5.0.3 ở nhánh capture. GCC hiện có chạy native fixture 4096/8192, không cài MSYS2 hoặc dùng nó đổi shell. Firmware/raw/CSV payload và reports vẫn dưới `D:\TSDuck`, không Git.
- **Baseline validation:** Gate B và tests mới cuối có 77/77 PASS với private CSV, giữ lượt đầu 76 PASS/1 SKIP; A2 parser 60/60, source/TRM assertions có giữ failures thật; app 74 JVM/native fixture PASS; driver task `UP-TO-DATE`, XML kế thừa 456/456, không fresh rerun. Chưa dùng kết quả baseline làm B2 PASS. Không ADB/USB/capture/VM/SSH/WSL hoặc đổi ownership trong phần A.
- **GitNexus:** Gateway xác minh canonical path/branch đúng nhưng index b73f110 sau HEAD 2 commits. CLI 1.6.12 có `analyze --index-only --workers 2`; đã thử mode không tạo context files nhưng chỉ hiện banner rồi không tiến triển trong chờ hữu hạn, dừng đúng process tree. Parent không force/repair/retry hoặc chứng nhận FTS. Agent B2 sau đó tự chạy một attempt index-only mới lúc 08:05–08:08, exit 0, có cảnh báo coverage và lookup symbol-not-found; không dùng thành công sau xóa failure trước. Source sau correction không được chứng nhận fresh graph; source/raw/diff/tests vẫn là căn cứ.
- **Emulator cho kiểm phần mềm B:** AVD đã có `TBS5530_Runtime_API37`, API 37/x86_64, emulator 37.2.13. CLI `emulator list` không hiện AVD nhưng native `-list-avds` có; đã kiểm config/source.properties và cổng 5580/5581 chưa có listener. Khởi động riêng `emulator-5580` với `-read-only -no-snapshot -no-window -no-audio`, không USB passthrough, wipe hoặc sửa AVD. Readiness 120 giây đầu FAIL/exit 1, giữ log; boot thực 141699 ms, kiểm sau xác nhận state=device/sys.boot_completed=1. Chưa chạy instrumentation B2 trên lượt preparation này; đã dừng đúng serial và attached process, cổng 5580/5581 không còn listener. Log có “Not saving state: RAM not mapped as shared” cùng thông báo snapshot nội bộ của emulator; không suy toàn bộ AVD bytes không đổi. Đây là emulator fake, không chứng minh tuner. ADB MCP chỉ liệt kê serial để tránh dùng mặc định; hai aliases physical giữ riêng, không cài/launch/shell Samsung.
- **AgentMemory:** Milestone A1 `mem_mwctz3md_253d081fbb7c` lưu với project `git:github.com/trankien84/androiddvbdriver` và agentId `copilot-agent-host`; recall theo nội dung trả đúng ID/content/author. Save response có project đúng; read projection không hiện riêng trường project. Không tạo duplicate để thử metadata; record gốc là snapshot 76 tests/75 PASS/1 SKIP trước thêm edge case thành 77 tests/76 PASS/1 SKIP.

Candidate B2 đầu đã build trên toolchain cũ; app lint PASS nhưng standalone driver lint FAIL cũ ngoài diff. Emulator lần hai chỉ cài APK đã hash vào `emulator-5580`, API 37/x86_64: app 24 PASS/1 FAIL legacy, focused 1/1 PASS riêng, driver 16/16 PASS; B2 isolation/JNI 3/3 nằm trong app suite. Không nới heartbeat hoặc gọi focused PASS là xóa failure đầu. Emulator lần hai đã dừng đúng serial, attached process exit 0, cổng 5580/5581 không listener. Corrected candidate đã giữ original bootstrap B1; chỉ reload dùng JNI, không dùng hashes build đầu làm card final. Corrected emulator lần ba app 30/30 và driver 19/19 PASS, API 37/x86_64; cài test APK chỉ emulator-5580, không physical app/data/store. Sau tests đã emu kill đúng serial, dừng attached process còn chờ, cổng 5580/5581 không listener. Final JVM fresh với ba private fixtures có 464 driver TBS/74 app/32 usbxfer PASS, 0 SKIP, 76/76 tasks executed. APK debug cuối SHA `7883e695dedfea1d5b3ae31dad491e8df28b47114509283b9b5c66375c3776aa` đã copy ra logs và hash lại không đổi.

Bundle `phase31-autoinlen-offline-20261112-073915-d3e4d287` chứa exact commands/cwd/exits/hash và backup sáu hồ sơ trước thay đổi. Samsung APK/history/current owner không được đo lại phiên này; hash history ngày 11/11 là witness lịch sử, không gọi current verified.


## 2026-11-11, phiên 22:18 — Samsung recovery/B1 thật, kết thúc không giữ tuner owner

- **Build/checkout:** Branch chore/modernize-android-build, HEAD nền 8abf57c32bd75cf03508f6db62e03a0825e67868 với edits cũ được giữ. Thêm debug recovery Activity/Permit/no-backup Journal/helper/tests, không source main production. Toolchain/SDK/dependencies không đổi; no stage/commit/push/reference sync.
- **Physical host:** SM-X926B/API36/boot 47, serial/network alias pin rõ và giữ private. APK final cài bằng install-r sau duyệt riêng, SHA f848e12c286aa40e7a0ff413f24df20aba63908a938d3162205e6da2aa727d71; không uninstall/clear. Recovery helper final 9bcb2a46..., autonomous helper 63f096c2..., raw/grants ngoài Git.
- **Recovery runtime:** AppUID không đọc /proc/uptime, shellUID đọc được; helper clock mới truyền uptime qua argument, set-e/host timestamp validation, không tăng deadline. Activity terminal relaunch mới dùng CLEAR_TOP/SINGLE_TOP/onNewIntent, không force-stop/CLEAR_TASK. Journal no-backup O_EXCL/fsync consumed giữ plan, ledger 7→9 đã apply đúng hashes; recovery plan không chạy lại.
- **B1 ownership/firmware:** User duyệt reconciled witness và một fresh attach/RF IN. One original bootstrap 132 controls/3545 ms, permission re-enumeration guard dừng sạch. Handoff operational user duyệt cùng epoch không FirmwarePath/0 A0, chip path 0x197/K36/manual0/drive 54. Native 8×4096 completion -75/0, không firmware retry hoặc đổi USB host/driver.
- **Cleanup:** Trước user remove đã drain/8 complete/outstanding=0/quarantine=false và app USB fd=0; user xác nhận rút/ngắt nguồn tuner. Pinned ADB alias sau đó mất và list devices 0, nên hậu kiểm current host-empty mới NOT_RUN. Không tự reconnect, không cắm lại, không helper/emulator/capture do phiên còn active.
- **Witness cuối:** Firmware history 12 records/996 byte, prefix 747 nguyên, SHA b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb app/host match. Full history trước reconciliation UNKNOWN không đổi thành PASS; mọi run sau cần witness/fresh scope mới và không tái dùng consumed epoch.

Bundle `phase31-recovery-coordinator-20261111-222119-cd9dcedf` dưới D:\TSDuck\reports/logs giữ exact commands/failures/reports. Node/driver 456 tests của lượt trước không tự tính rerun; lượt này app 74/build/lint/11 emulator/37 helper mới PASS. Không ASan/power-loss proof mới hoặc CoreELEC runtime; graph index cũ không dùng làm authority.

## 2026-11-11 — Guarded recovery chỉ build/emulator; tablet giữ nguyên

- **Quyền mới:** User chọn triển khai recovery/hash guard offline, chưa cho cài APK mới, ghi ledger hoặc chạy B1. Samsung chỉ bị đọc lại exact history/APK hashes và current USB host; không launch app/cài/gỡ.
- **Build/test:** Dùng Wrapper và AGP/Gradle/JDK/SDK đang pin, không dependency install hoặc toolchain change. Node 29, JVM driver 456/app 74, emulator 24 tests PASS. Emulator AVD có sẵn chạy tạm port 5580 sau kiểm ports không có owner khác; pin emulator serial mọi install/test, rồi dừng đúng instance. Không sử dụng default device vì Samsung cùng kết nối.
- **Hashes mới:** Debug APK `5cb28185685b991bce689ff6068c6e7d8186ac79c5b059226ad53155a682da1c`, host helper `63f096c20d8ad886510c3dc194d510b9bef03212ae1d2590e158e68b7b691c07`. Samsung 22:07 vẫn APK cũ `bbbf25cc67b50e090c8261c00e38c41501538bbf7c8f73549879ab19bf9ca7ff`, ledger `e398459ceddb2c8f35ddc61b81ee66a31ec1aee300c33bb603dc9f593b0b31f9`, boot 47 và host USB rỗng; candidate 9 dòng chưa apply.
- **Gateway/index:** AgentMemory đã ghi milestone với canonical project/harness đúng và đọc lại content/agent; save response có project, projection expand không hiện project riêng. GitNexus list_repos mới xác minh canonical path/branch đúng nhưng index vẫn ở b73f110, sau HEAD một commit và thiếu dirty changes; không dùng graph làm proof hoặc force repair. Source/diff/tests/evidence hashes là căn cứ.
- **Limits:** Recovery core/append adapter chưa có exported caller/grant/journal/host coordinator live. Không power-failure test hoặc hash-guard benchmark trên physical bootstrap; không nới heartbeat/limits. Không stage/commit/push hoặc sync reference; `.gitnexus` không bị sửa trong lượt này.

## 2026-11-11 21:05 — Kiểm chỉ đọc Samsung và công cụ recovery local

- **Checkout:** Branch/HEAD vẫn `chore/modernize-android-build` / `8abf57c32bd75cf03508f6db62e03a0825e67868`; bảo toàn edits lượt trước. Thêm Node offline recovery/tests và cập nhật README/hồ sơ. Hash các source runtime/helper từ manifest cũ không đổi; không stage/commit/push hoặc sync references.
- **Device:** ADB ban đầu không có Samsung; không tự connect/reconnect. Sau người dùng xác nhận nối, pin đúng SDK ADB và SM-X926B/API36/boot 47, một network alias/current USB host rỗng. Chỉ shell read-only/exec-out export ledger và marker, không mở app/permission, cài/gỡ hoặc instrumentation.
- **Storage:** Base Samsung ledger vẫn 581 byte/7 dòng với hash e398459c... như checkpoint trước, marker initialized=true. Exact `.bak`/`.new` và marker `.bak` không có. Không dùng filemtime bất thường làm proof recovery; không backup-service hoặc restore command.
- **Tooling:** Node có sẵn chạy 29 tests và CLI không dependency mới. Không thay Gradle/SDK/JDK/NDK/Android config, không Python/VM/WSL/driver/tuner commands. ZIP được đọc tại chỗ bằng API hệ thống, giới hạn 2 MiB mỗi text và 64 MiB text/archive, không giải nén.
- **Gateway:** `retrieve_tools` hiện đã expose lại AgentMemory; recall/smart_search đọc checkpoint Sony theo canonical project `git:github.com/trankien84/androiddvbdriver`. Đây là thay đổi khả dụng của công cụ so phiên trước, không sửa lịch sử thành đã persist. Current source/raw/tests vẫn là chuẩn; không cần GitNexus cho tool offline độc lập.

Report/raw riêng ở `D:\TSDuck\reports\phase31-history-recovery-20261111-210818-73007003` và logs cùng run ID. Candidate chứa denial keys ngoài Git, chưa áp dụng lên app; quyền kiểm chỉ đọc không được hiểu là quyền append hoặc B1.

## 2026-11-11 — Đóng gate B1 trước hardware, giữ Samsung app và history hiện tại

Samsung preflight/control-plane đã PASS và APK giữ lại đúng hash. Ledger Samsung được backup chỉ đọc có 7 entries/581 byte; audit 32 inputs chỉ chứng minh hai epoch 09/11 còn, expected Sony/lifecycle 10/11 thiếu. Không có đủ evidence continuity hoặc nguyên nhân thiếu, không phục hồi/reimport/rearm/app clear. Không phát grant hardware, không tuner URB mới cần drain.

Giữ hai property AGP về leaveApks/uninstallIncompatible ở các lệnh instrumentation vật lý tiếp theo; không sửa global build hoặc User/Machine execution policy. Child Bypass đã dùng chỉ cho scope preflight/control-plane được duyệt, không helper live B1. Samsung vẫn kết nối ADB; tuner được người dùng giữ rút, không đọc lại owner bằng Windows/VM hoặc đổi USB ownership. Những trạng thái offline/emulator trước đó giữ đúng thời điểm của chúng.

Report continuity chỉ đọc và safety blocker ở bundle `phase31-android-first-20261111-172352-0a98eee3`; private ledger/grants/nhận diện ở D:\TSDuck\logs ngoài Git. Canonical project/harness không đổi; Gateway/AgentMemory vẫn không expose nên chưa ghi remote.

## 2026-11-11 — Samsung preflight không tuner sau phê duyệt

- **Thiết bị:** Samsung SM-X926B/API36, boot 47; hai network ADB aliases có cùng identity/boot, chọn một serial rõ ràng giữ private. Current USB host rỗng, port disconnected. Chuỗi vendor_id=29516 trong dumpsys thuộc activity filter, không device attachment; đã phân biệt trước test.
- **Instrumentation:** Lifecycle đúng host PASS 5/5. Runner đầu gỡ APK sau test; không kiểm kê app-data trước nên không kết luận mất dữ liệu. Người dùng được thông báo và duyệt cài lại đúng APK cùng chế độ giữ-app; lượt sau PASS 5/5, APK còn cài và SHA khớp build `bbbf25cc67b50e090c8261c00e38c41501538bbf7c8f73549879ab19bf9ca7ff`. Không gọi clear/uninstall thủ công hoặc sửa ledgers.
- **Tùy chọn AGP đã kiểm:** `android.injected.androidTest.leaveApksInstalledAfterRun` và `android.experimental.testOptions.uninstallIncompatibleApks` có trong BooleanOption của plugin 9.5.0 local. Lượt giữ-app dùng true/false tương ứng; không sửa global config. Đây là biện pháp giữ APK cho các lần kiểm thiết bị thật tiếp theo, không phải chứng nhận backup toàn app-data.
- **History/guard:** History Samsung cùng marker initialized vẫn tồn tại: 7 records/581 byte, SHA `e398459ceddb2c8f35ddc61b81ee66a31ec1aee300c33bb603dc9f593b0b31f9`, format hợp lệ và backup chỉ đọc hash khớp. Chưa dùng filemtime bất thường làm proof continuity; đối chiếu old epoch riêng, không dựng lại store.
- **Control-plane:** Helper đã chạy child-process Bypass trong scope preflight/control-plane người dùng duyệt, terminal CONTROL_PLANE_COMPLETE/NO_RESOURCES_ACQUIRED, firmware/native URB null, 0 mirror records/TS bytes. B1 hardware vẫn NOT_RUN; quyền chung có điều kiện không cho phép C1 hoặc firmware khác.

Raw identity/grants/history backup nằm ngoài Git dưới D:\TSDuck\logs\phase31-android-first-20261111-172352-0a98eee3; summaries redacted ở reports cùng run ID. Các quan sát ADB rỗng ở checkpoint offline trước là lịch sử, không ghi đè bằng trạng thái mới này.

## 2026-11-11 — Môi trường kiểm phần mềm Android-first, không tuner live

- **Checkout:** Branch `chore/modernize-android-build`, HEAD `8abf57c32bd75cf03508f6db62e03a0825e67868`, sạch khi bắt đầu; chỉ edits của nhiệm vụ hiện tại, không stage/commit/push. Bốn reference SHA đọc lại không đổi, không sync hoặc sửa reference.
- **Declared / installed:** AGP 9.5.0, Wrapper Gradle 9.8.2 cùng checksum đã pin, compileSdk 37.2, targetSdk 37, Build-Tools 37.0.0, NDK 30.0.16248370, CMake 4.1.2; JAVA_HOME hiện trỏ JDK 25. MinSdk 21 và Java source/target 8 giữ nguyên. Inspect script chạy thành công không thay package; đây là toolchain đang khai báo/cài và đã build trong lượt này, không khẳng định latest vendor.
- **PowerShell:** Script chưa ký ban đầu bị execution policy chặn. Người dùng cho phép child process `pwsh -NoProfile -ExecutionPolicy Bypass -File ...` chỉ để audit/tests offline; không đổi User/Machine policy, không coi đó là quyền chạy helper hardware. Audit stdout nằm trong bundle ngoài Git.
- **Android:** `D:\Android\Sdk\platform-tools\adb.exe` được chọn rõ thay bản ADB khác trên PATH. Lúc đầu/cuối không physical device. Native emulator 37.2.13 chạy AVD có sẵn `TBS5530_Runtime_API37`, API 37/x86_64, chỉ để fake instrumentation; Android CLI không nhận AVD, nên dùng executable SDK sau đọc help. Đã xác nhận boot bằng `sys.boot_completed=1`, kết thúc đúng instance và ADB cuối không còn device. Không wipe, cài SDK hoặc thay cấu hình AVD.
- **C/native:** GCC 16.2.0 tại `C:\msys64\mingw64\bin\gcc.exe` có sẵn; dùng trực tiếp compiler cho portable C fault injection, không cài MSYS2 hoặc dùng nó thay shell POSIX. ASan runtime không resolve nên NOT_RUN. NDK llvm-readelf kiểm LOAD alignment 0x4000 cho bốn ELF64 trong debug APK; bốn ABI đều có, không native-load trên Android vật lý mới.
- **Build/artifacts:** Debug APK SHA `bbbf25cc67b50e090c8261c00e38c41501538bbf7c8f73549879ab19bf9ca7ff`; helper SHA `616b9e271aaebef304477d15aa540e54f8f425de42976bb06662d22ff914ae94` sau sửa CR chỉ ở added lines. Không chuẩn hóa toàn file mixed EOL lịch sử. Source manifest ghi dirty build; HEAD không thay thế hash APK. Firmware/raw/TS/grants/binary đều ngoài Git.
- **Hardware/VM:** Không VBoxManage/SSH/usbipd, capture mới, claim tuner, bootstrap hoặc vendor/I²C writes thật. Owner tuner hiện tại UNKNOWN vì không đo live. Không dùng CLEAN_DISCONNECTED lịch sử làm hậu kiểm mới. Emulator đã dừng; không có native tuner URB mới cần drain.
- **GitNexus / memory:** CLI GitNexus 1.6.12 và help có thật; status không trả kết quả sau chờ hữu hạn, đã dừng đúng process tree của lệnh đọc. Không reindex/repair, graph/FTS chưa xác minh và không dùng làm căn cứ. Built-in source/diff/tests/raw là chuẩn. Gateway `retrieve_tools`/AgentMemory không được expose trong tool set hiện tại; đã đánh giá checkpoint nhưng không thể lưu remote, không tuyên bố persist.

Báo cáo và logs: `D:\TSDuck\reports\phase31-android-first-20261111-172352-0a98eee3`. Python environment/commands cho công cụ Gate E được agent xác minh riêng theo interpreter có sẵn, không cài dependency; exact command/exit ở manifest Gate E. Không chuyển môi trường, root Android, thay BDA/USB driver hoặc quyền USB.

## 2026-11-11, phiên 10:11 — Chỉ phân tích file BDA/VM đã có

- **Checkout:** Branch `chore/modernize-android-build`, HEAD `b73f11097e594acd2abe1769a65ed03216c8e963`; giữ các edits có trước. Không sửa runtime/Android/helper/module/toolchain, stage/commit/push hoặc sync references. Sáu hồ sơ được backup trước sửa.
- **Công cụ thực tế:** Node v26.7.0 tại C:\Program Files\nodejs, TShark 4.6.9 và TSDuck tsanalyze 3.45-4798. Đọc help/schema fields trước offline commands; ghi stdout/stderr/exit riêng. Không cài dependency hoặc gọi SDK/ADB.
- **Dữ liệu:** Ba pcap BDA cold2/VM cold/VM operational đã hash lại đúng. TS BDA cùng phiên 30.897.800 byte/hash đúng, tsanalyze offline mới xác nhận 164350 packets và 3054 TEI; không lấy PCR bitrate làm throughput. Input manifests và raw output giữ ngoài Git trong D:\TSDuck.
- **Thiết bị/VM:** Không SSH/VBox/usbipd hoặc kiểm owner live trong lượt offline; trạng thái CLEAN_DISCONNECTED sau run 014626 là lịch sử, không phép xác nhận mới. Không yêu cầu cắm/power-cycle, capture hoặc dùng grant/physical epoch cũ.
- **Nguồn:** Bốn reference SHA được đọc lại không đổi; chỉ đọc source hàm tuner/RSSI/TS output và WDK header có sẵn. Không sửa reference hoặc giả định CoreELEC có hardware run. Không dùng nguồn web hoặc tải package để thay local evidence.
- **Kiểm chứng:** 52 tests offline PASS, TShark/TSDuck và report scripts exit 0. Dùng source/raw/hash và tests, không GitNexus vì đây là đối chiếu file có phạm vi cố định; không refresh hoặc chứng nhận index/FTS. Kết quả mới và giới hạn đã lưu vào AgentMemory với canonical project/agentId đã dùng; readback theo ID kiểm riêng, không dùng lịch sử làm chuẩn thay raw.

## 2026-11-11 01:46 — Môi trường phép đo nhiều lớp đã hoàn tất

- **Git/phạm vi:** Branch/HEAD vẫn `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`. Chỉ thêm/sửa bộ quan sát, tests và hồ sơ; giữ source Android cùng các edits có trước, không stage/commit/push hoặc sync references. M late/source/receiver không rebuild hoặc đổi hash.
- **Tool/VM:** Windows PowerShell 7.6.9, Node 26.7.0, USBPcap 1.5.4.0, VirtualBox 7.2.22r175245. VM Ubuntu-24.04 running, cùng boot/kernel 6.8.0-149-generic, guest xHCI on/EHCI off giữ nguyên; strict root SSH. Không nâng/cài package, đổi driver/Registry/controller hoặc snapshot/reset VM.
- **Contract guest đã kiểm:** Requested trace buffer 64 KiB đọc ra 67 KiB theo Linux 6.8, page 4096 byte/header 16 byte; recorder PID exclusions có kiểm PPID/PGID/SID/exe/starttime. Full Linux fake cuối 58/58 PASS trong 156 giây, mọi failures cũ giữ riêng. Harness ready/terminal wait 10/12 giây và runner 275 giây là fake-only; observer 90/84 giây, file 64 MiB, session 5 phút, heartbeat 2 giây không đổi.
- **Lượt đo:** Run 014626 có firmware raw đủ 132 controls/hash, operational không A0, K36 lock và receive_timeout/0 byte. Windows host báo một BABBLE rồi 35 cancellations; guest có tám cancellations, raw xHCI đủ 8/8. Host mapping/root/address và guest descriptor/identity được đọc mới, không dùng địa chỉ lịch sử. WDK local 10.0.19041.0, usb.h:304/339 xác nhận status names.
- **Coverage/cleanup:** Các recorders ready, không lost/drop/truncation được báo; 74 file guest hash khớp hai bản. Cả hai phase cleanup=true/outstanding=0, own modules/filter/usbmon/firmware hardlink/trace instances đã gỡ. Người dùng xác nhận rút sau drain và read-only hậu kiểm CLEAN_DISCONNECTED; không có capture hoặc hardware helper còn chạy. Không auto detach về BDA.
- **Tooling/records:** Source/AST/process tests và raw là chuẩn; GitNexus index có trước tools mới, không refresh hoặc chứng nhận graph/FTS đầy đủ. Đã lưu các checkpoint cùng kết quả đo mới vào AgentMemory với canonical project/agentId; expand theo ID trả đúng record, nội dung và author. Projection readback vẫn không hiện riêng trường project, trong khi save response có đúng metadata; không tạo duplicate để thử. Report/grants/firmware/raw/hash manifests giữ D:\TSDuck ngoài Git; không TS hoặc APK mới trong repository.

## 2026-11-10, phiên 19:32 → 2026-11-11 00:28 — Môi trường triển khai và pre-runtime recorder

- **Checkout:** Giữ `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`, mọi edits có trước và 31 non-record source files đã kiểm hash không đổi trong lượt triển khai. Phạm vi mới là [tools/observation](../../tools/observation/README.md) và hồ sơ. Không stage/commit/push, package/toolchain change hoặc reference sync.
- **Windows:** PowerShell 7.6.9, Node v26.7.0, Git Bash và Windows OpenSSH hiện có; không cài dependency. Helper live chạy UAC theo scope được duyệt. USBPcap 1.5.4.0 target root được rediscover; không hardcode capture number hoặc device address. Source manifest và grant tạo mới từng lượt, giữ cả các run thất bại.
- **VM:** VBoxManage 7.2.22r175245, Ubuntu-24.04 đang chạy, xHCI on/EHCI off giữ nguyên. Strict root SSH xác nhận cùng boot/kernel 6.8.0-149-generic. Không đổi VM/controller, reset/poweroff/snapshot hoặc SSH config. M late và receiver không rebuild; hashes giữ baseline đã pin.
- **Giao tiếp:** Heartbeat/status đang hoạt động dùng một SSH lâu dài, LF và sequence, không tự gia hạn khi host mất. Có 80 PULSE fixture Linux PASS, tối đa 707 ms; command hữu hạn đóng stdin và SSH `-n`, 16 lần chỉ đọc PASS. Không tăng heartbeat 2 giây hoặc USB timeout. Nguyên nhân chung của mọi timeout cũ chưa được chứng minh.
- **Capture có thật:** Run 002355 mở cold Windows USBPcap, tạm load usbmon và tạo xHCI instance riêng sau consent. Default tuner đã thấy đúng ở cả hai phía; guest chưa ready nên chưa insmod M, firmware hoặc receive. Host ghi 2178 byte, guest usbmon 1234 byte và trace.dat 2049067 byte; đây chỉ là bằng chứng trước runtime. Raw/log/grants/nhận diện riêng ở D:\TSDuck, không trong Git.
- **Cleanup/ownership:** Run 002355 xác nhận cleanup true, own modules/filter/usbmon/firmware staging và trace instance đã gỡ. Người dùng sau đó rút khỏi mọi host, ngắt nguồn ít nhất 5 giây/đèn tắt và giữ rút; read-only guest hậu kiểm CLEAN_DISCONNECTED. Không tự detach sang BDA, không dùng địa chỉ hoặc epoch cắm muộn trước đó.
- **GitNexus/AgentMemory:** GitNexus index đã đăng ký đúng root/HEAD nhưng có trước tools mới; không refresh hoặc coi graph PowerShell/Bash là đầy đủ. Source/AST/hash/process tests và raw là chuẩn. Đã lưu checkpoint kênh vào AgentMemory với canonical project/agentId và đọc lại đúng record/author; recall projection không hiện độc lập trường project, không tạo duplicate để thử.

## 2026-11-10, phiên 18:46 — Preflight quan sát Windows/VM chỉ đọc

- **Checkout:** Branch `chore/modernize-android-build`, HEAD `b73f11097e594acd2abe1769a65ed03216c8e963`; giữ edits có trước. Không đổi toolchain, package, driver, controller hoặc references. Source cũ và ledgers giữ nguyên.
- **VirtualBox/guest:** VBoxManage thực tế 7.2.22r175245; VM Ubuntu-24.04 đang chạy, xHCI bật/EHCI tắt. Strict SSH qua alias ubuntu-VMS xác nhận UID 0, Ubuntu 24.04.5/kernel 6.8.0-149-generic/x86_64. UUID, boot và dữ liệu topology riêng chỉ ở logs. Không start/reset/shutdown/snapshot hoặc thay forwarding.
- **Ownership:** VBox usbhost và guest không có target; người dùng xác nhận tuner rút khỏi mọi host. Không gán USB hoặc coi địa chỉ cũ là ổn định. Không có DVB/capture client liên quan trong guest lúc kiểm; không mở tuner để xác nhận.
- **Windows capture:** USBPcap driver Running, binaries và driver 1.5.4.0 đã có. Direct extcap discovery trả 5 control interfaces, dù dumpcap 4.6.9 tự liệt kê không có USB extcap. Process chưa elevated; chưa mở capture hoặc đổi Registry/service. Không kết luận discovery bằng capture PASS. Extcap snaplen là unsigned; GUI capture-all mặc định true, nên cần kiểm target scope rõ ràng.
- **Guest capture:** tcpdump 4.99.4/libpcap 1.10.4, trace-cmd 3.2.0 và file usbmon module sẵn có; usbmon chưa loaded. Đã đọc event formats, trace-cmd help và clock/filter state. Các event xHCI cần dùng đang tắt; global tracing được giữ nguyên. Không nạp module, mount tracefs, ghi filter hoặc đọc trace stream.
- **Module baseline:** M late board module SHA `5ad5512988800c2ee6954faa4fed8058f33c489f505a01ac6086b2859a2141a6`, source SHA `e1a6d36a2f98ad16ae568eee30c0da130d549d23ca7f3e29cb5b685b26b0ed37`, receiver SHA `04f57dd49fbe279e732ef6a1d5d2187d978d3df48c42d777de9f01fa95269550`; guest hashes/vermagic khớp kernel. Không load hoặc build lại. Không dùng variant early/controller khác cho phép đo observer-only được đề xuất.
- **Offline mới:** Agent dùng Python 3.12.2 hiện có với `-B`, 64 FX2 tests PASS; parent dùng Node policy, 36 tests PASS. Không cài dependency. Report/plan/tests dưới bundle `phase31-observation-ready-20261110-185020-78ccd455`; chưa có observer integration/live trace. GitNexus không được dùng hoặc refresh vì scope là firmware/CLI feasibility và hồ sơ, không có source graph mới cần dựa vào.

## 2026-11-10, phiên 18:02 — Môi trường phân tích file Windows/Android

- **Checkout:** `chore/modernize-android-build`, HEAD `b73f11097e594acd2abe1769a65ed03216c8e963`; giữ toàn bộ edits có trước. Lượt này chỉ cập nhật hồ sơ; script đối chiếu/test/report nằm ngoài Git dưới D:\TSDuck. Không stage/commit/push, đổi toolchain hoặc sửa references.
- **Tool được kiểm:** TShark `C:\Program Files\Wireshark\tshark.exe`, phiên bản 4.6.9; đọc help và xác minh 23 field names trước `-r` offline. Node.js v26.7.0 tại `C:\Program Files\nodejs\node.exe` dùng thư viện chuẩn hiện có cho decoder/comparison; không cài dependency, không chạy Python. Không cần TSDuck executable hoặc môi trường Android để so raw controls.
- **Dữ liệu:** Cold2 pcap giữ 32.202.128 byte/SHA `70fced47614ff8ec17c9e78f79fca16408c23e6bcadcfb6ad25a4c69cd7f9ded`; Android EP0/native/report đã hash lại. Cấu hình FIFO tĩnh cũ được đối chiếu bằng mapping cùng firmware; không đổi thành runtime readback. Output mới là 6751 controls và một cặp Bulk đầu, không phải bản thống kê đầy đủ stream.
- **Thiết bị:** Không kiểm live tablet hoặc tuner, không ADB/USB/firmware/capture/owner transfer. Trạng thái tablet cuối phiên 16:10 vẫn là lịch sử, không xác nhận lại ở lượt này. Không VM/SSH/snapshot hoặc Windows driver/service changes.
- **Nguồn:** Bốn reference SHA giữ nguyên như phiên 16:10, chỉ `rev-parse` lại; không sync/fetch. Hai tìm kiếm web không bổ sung schematic chính hãng đủ tin cậy; không gửi raw/code riêng hoặc nhận diện thiết bị ra ngoài. Không dùng/làm mới GitNexus vì tác vụ là đối chiếu file, không phân tích graph source mới.
- **Bằng chứng mới:** Bundle `phase31-windows-sony-20261110-180339-9e246085` dưới logs/reports có input hashes, TShark arguments, byte-level proof, JSON/CSV và 38 test results. Không có APK mới hoặc thay đổi driver/helper trong lượt này.

## 2026-11-10, phiên 16:10 — Sony readback đã đo trên tablet

- **Git và phạm vi:** Branch `chore/modernize-android-build`, HEAD `b73f11097e594acd2abe1769a65ed03216c8e963`; giữ các thay đổi có trước. Chỉ sửa debug monitor/app/tests/helper và hồ sơ trong AndroidDvbDriver. References không sửa hoặc sync; không stage/commit/push.
- **Toolchain giữ nguyên:** Đọc lại AGP 9.5.0, wrapper 9.8.2 và NDK 30.0.16248370 đang dùng; JAVA_HOME có executable hợp lệ. Build vẫn giữ compile SDK 37.2, Build-Tools 37.0.0, CMake 4.1.2, minSdk 21 và Java 8. Không cài hoặc đổi gói. Wrapper final build/lint/AndroidTest thành công; IDE vẫn báo debug/testDebug Java không trên classpath, không tự sửa workspace.
- **Tablet và APK:** SM-X926B/API 36/boot 46 được xác minh lại trên serial tường minh. Không có tiến trình app trước cài cập nhật bằng Android CLI với `--install-options=-r`; không clear data/ledger. App-debug SHA `4ebf241c0f41f312b44e3f1bb19235c0cc46b047db3256b02a03208b52bb703c`, AndroidTest SHA `cfd0a0aa2c6ce077609bf6dca0822ca882797291cb339040c853da13db2c26ad`. Có 19 approval và 5 lifecycle fake tests PASS khi màn hình thức; giữ riêng 3 lỗi lifecycle lúc Dozing, không quy thành lỗi tuner.
- **Quyền và attachment:** Người dùng duyệt một chương trình Sony với caveat read81, xác nhận power-off ít nhất 5 giây/đèn tắt rồi gắn cùng tablet/cáp/nguồn/RF IN. Giữ một physical epoch qua các preflight và handoff. Hộp thoại USB được người dùng bấm OK; không chọn Always. Đã sửa routing preflight nằm dưới workflow đã drain bằng CLEAR_TOP + SINGLE_TOP. Không ADB reconnect, force-stop, tăng deadline hoặc xóa lịch sử.
- **Hardware mới:** Firmware full 132 giao dịch trong 2834 ms, chỉ một lần. Sau bootstrap terminal/drain, operational không FirmwarePath/A0. Hai Sony snapshots đủ 20 EP0 mỗi lần, cùng bảy giá trị cấu hình; native vẫn tám URB -75/actual 0. Terminal/drain xác nhận outstanding=0/quarantine=false; sau Back, tiến trình app còn tồn tại nhưng không giữ USB fd. Tuner giữ trên tablet, không chuyển Windows/WSL/VM hoặc đổi driver/controller.
- **GitNexus:** CLI 1.6.12 status thấy content drift; `analyze --index-only` exit 0 trong 102,8 giây, có 10.251 nodes/53.341 edges/896 flows. Có warnings về khác ngôn ngữ và flow bị giới hạn; MCP context không tìm thấy symbol monitor mới. Không force/repair hoặc chứng nhận graph/FTS đầy đủ; source, tests và raw là chuẩn. Index chỉ là checkpoint trước các chỉnh sửa cuối.
- **Lưu trữ:** Bundle `phase31-sony-output-20261110-161951-5b3ce827` trong D:\TSDuck\logs và reports có consent receipt, APK hashes, test logs, provenance và 16 raw files đã hash Android-host khớp. Firmware input được đọc lại từ bản staging app-private đã có, xác minh đúng 8192 byte/SHA trước dùng; không tải firmware mới, không đưa vào APK/Git. Ba release class jars đã kiểm không có monitor debug.

## 2026-11-10, phiên 12:09 — Lifecycle mới và B setInterface thật

- **Git:** branch/HEAD giữ `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`. Các thay đổi có trước được giữ nguyên; không stage/commit/push, sync references hoặc đổi toolchain.
- **Build đã đọc lại:** AGP 9.5.0, wrapper 9.8.2, SDK 37.2, Build-Tools 37.0.0, NDK 30.0.16248370, CMake 4.1.2; minSdk21/Java8 không đổi. JAVA_HOME của process có executable hợp lệ; không sửa biến môi trường toàn cục. VS Code task lookup trả Task not found nên dùng wrapper. Fresh regression 565 JVM và debug/release/lint PASS.
- **Tablet:** SM-X926B/API36/boot46 được xác minh lại trên serial tường minh. Người dùng khôi phục ADB hai lần mà không reboot hoặc rút/cắm tuner; không tự reconnect. Read-only inspection app cũ cho thấy không có USB fd trước cài cập nhật. Hardware history SHA giữ nguyên qua cập nhật APK và các kiểm control-plane; không clear data/ledger.
- **APK B thật:** app-debug SHA `8b8dac4061991abc38643315d41b3b1f8383fec5567e68302a080c86f396a05c`; app code không đổi sau build này. AndroidTest APK được build lại riêng để thêm routing test; 5 lifecycle và 16 approval tests PASS. Một lần instrumentation báo Process crashed khi ADB offline; không tìm thấy app fatal trong DropBox/crash buffer, không gán nguyên nhân chưa chứng minh.
- **USB B mới:** một physical epoch do người dùng xác nhận ngắt nguồn ≥5 giây/đèn tắt, cùng tablet/cáp/nguồn/RF IN. Default descriptor 189 byte/hash e2887bad…; bootstrap full 132 trong 3230 ms rồi tự re-enumerate, operational descriptor 50 byte/hash22e2f1df…. Handoff không FirmwarePath/A0. Speed3/HS, config1/interface0/alt0/EP82/81 maxpacket512; setInterface trả true nhưng tám URB vẫn -75/actual 0, drain=8/8/outstanding=0/quarantine=false. Tuner giữ ở tablet, không chuyển Windows/VM.
- **GitNexus:** CLI 1.6.12 index-only exit 0 trong 98,6 s, checkpoint có 9.935 nodes/45.584 edges/869 flows. Graph query giúp định vị nhưng index không bao phủ mọi edit cuối. Không force rebuild/repair hoặc chứng nhận FTS đầy đủ. Source, tests, raw và hash là bằng chứng chính.
- **Artifacts:** `D:\TSDuck\logs\phase31-lifecycle-20261110-121354-96284261` có grant/physical receipts, logs, APK hashes và mười raw B đã kiểm hash Android-host. Reports ở sibling reports cùng run_id. Firmware/grants/serial/TS/raw không vào Git. Không VM hoặc Linux hardware mới; bản sửa là Android/debug/helper trong Phase 3.1.

## 2026-11-10 — Phase 3.1: VM được người dùng bật lại, tablet và toolchain đã kiểm

- **Git:** `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`, sạch đầu phiên; không sync/fetch hoặc sửa references. Official/community/CoreELEC/Go giữ các SHA đã pin ở mục nghiên cứu mới.
- **VM thực tế theo thời điểm:** VBoxManage `7.2.22r175245`, VM `Ubuntu-24.04`, UUID `adae5393-5ebf-43d2-8d48-29565f73f4c1`. Ban đầu poweroff, strict SSH exit 255/Connection refused; sau người dùng tự bật, VM running và strict SSH exit 0/root/Ubuntu24.04.5/kernel6.8.0-149-generic/x86_64. xHCI on, EHCI off; lsusb không có tuner. Không start/reset/shutdown/controller/snapshot từ agent.
- **Tablet:** chọn một serial ADB tường minh, SM-X926B/API36/boot46 được đọc lại trước live. Có hai alias của cùng tablet; sau lần offline trước grant, người dùng tự khôi phục endpoint Wi-Fi mới. Không tự reconnect, root, thay khóa hoặc vô hiệu bảo mật. Không ghi serial/IP vào Git.
- **APK thật:** app-debug SHA `7a86ec95166617e1f53953d59bdcb48564fbdbe21a52c39318d86dfc884dc994`; cài cập nhật bằng Android CLI, không clear data/ledger. Instrumentation driver SHA `f09e704e32f76dad3db1dcf8e80a47d67ef553f5e6bdacca0cbd00ef5b290b00`, app-test SHA `cfa9642579dcf73fa7cbd0e0a2ed52cfb86937ad4ede429225f402d5a9472f93`; fake runtime 4+16 PASS. Sửa EOL source sau build không đổi nội dung logic hoặc APK.
- **USB mới:** A/B đều default descriptor189/hash e2887bad… trước full132; operational descriptor50/hash22e2f1df… được thu sau re-enumeration. Mỗi physical epoch một upload; handoff không A0. A native8×4096 có tám -75/actual0, drain8/8; B đầu dừng heartbeat trước native. Tuner quan sát cuối thuộc tablet, không chuyển Windows/VM.
- **Toolchain đã kiểm và giữ:** declared AGP9.5.0/wrapper9.8.2/SDK37.2/Build-Tools37.0.0/NDK30.0.16248370/CMake4.1.2; minSdk21/Java8. Runtime JDK25.0.5.0.3, Python3.12.2, Node/GCC có sẵn; TShark4.6.9 và TSDuck3.45-4798 đọc file offline. Không cài/nâng dependency, SDK hoặc công cụ. Latest stable published không được audit lại vì không có migration.
- **GitNexus:** CLI 1.6.12 index-only thành công trong 132,3 s, với 9.642 nodes/37.575 edges/843 flows. Meta trỏ đúng repo/HEAD ở checkpoint. MCP query vẫn báo behind 2; snapshot/cache chưa được chứng minh cập nhật. Graph chỉ giúp định vị, không chứng nhận index cuối hoặc FTS hoàn chỉnh. Bằng chứng chính là source/hash/raw/tests; không repair/force rebuild.
- **Artifacts:** raw mới và grants/receipts nằm ở sibling logs của bundle `phase31-state-20261110-100952-10a3635f`. A đã thu 10 files, hash Android-host khớp; B đầu thu 8 files vì native/sample chưa được tạo. Không đổi UNKNOWN thành zero hoặc giả file chưa được tạo là đã capture.
- **Blocker cuối đã đo:** ADB shell đọc `/proc/uptime` được, nhưng cùng lệnh qua `run-as` trả Permission denied/exit 1. Helper đã sửa ranh giới UID và có 82 checks PASS trên mỗi PS7/PS5.1; chưa xác minh wrapper mới trên hardware. Workflow chuẩn bị B thay thế không có grant.json hoặc transfer, nhưng report ghi DRAIN_NOT_CERTIFIED. Không clear task, force-stop, detach, reset hoặc tự chạy thêm. USB lần quan sát cuối vẫn ở tablet; không chứng nhận cleanup workflow cuối từ số I/O bằng 0.

## 2026-11-09 → 2026-11-10 — Linux cold M, xHCI hiện hành và cleanup

- **Git:** branch `chore/modernize-android-build`, HEAD `cbb2aa6892ede102d7b122badec848ae7c483b14`, working tree sạch đầu phiên. References chỉ đọc: TBS `2ce787de9f6e81b4294692a45de15596ae2e1322`, community C `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`, CoreELEC `072f8e99e0ac66d88d7278784e922e08e8253210`, Go `6aaa1899991c875ba0482fa9098adc6c83b01fd8`. Không sync hoặc đổi các SHA.
- **VM đã kiểm mới:** VBoxManage `7.2.22r175245`, VM `Ubuntu-24.04` đang chạy; SSH `ubuntu-VMS` với BatchMode/StrictHostKeyChecking trả root, Ubuntu 24.04.5/kernel `6.8.0-149-generic`/x86_64. Config hiện xHCI on, EHCI off; guest có root 480M/5000M và controller xhci_hcd. Khác ghi chép EHCI lịch sử, không do phiên này đổi. Không snapshot, reboot/reset hoặc cấu hình lại VM/SSH/NAT.
- **Công cụ guest:** headers, GCC 13.3.0, make, readelf, tcpdump 4.99.4 và kmod 31 có sẵn. O-C build 5 module; M build 3 module. Kmod dump modversions báo Invalid argument do header Ubuntu dùng `u32 next/u32 crc/name[]`, không layout cố định; đã đọc ELF và đối chiếu Module.symvers, không force ABI hoặc cài package.
- **USB/firmware thật:** cold lần A bus 2/device 2 rồi tự thành 3; lần B device 4 rồi 5. Default descriptor 189 byte/hash e2887bad… có alt0 không endpoint; operational 50 byte/hash 22e2f1df… có config 1/interface 0/alt 0, EP82/81 maxpacket 512. Mỗi physical epoch đúng một upload 132/full8192/hash b97cf208…, xác minh từ pcap. Không coi descriptor là readback RAM hoặc suy VID:PID đủ chứng minh operational.
- **Quyền và file tạm:** từng grant/cold physical confirmation được lưu ngoài Git. Firmware tên `dvb-usb-id5530.fw` chỉ stage khi đường dẫn và biến thể nén chưa tồn tại; xóa đúng file/hash trước operational. USB filter tạm chỉ 734c:5530, gỡ sau drain. Modules chỉ từ scratch, không install/depmod/DKMS, thay kernel/media core hoặc Windows BDA.
- **Trạng thái cuối đã đo:** Windows PnP OK/TBS 5530 DVB BDA, usbipd Shared; VM không còn tuner hoặc tbs/cxd/alp/dvb_core/usbmon do phiên nạp. xHCI giữ nguyên, filter tạm vắng. Ledgers không xóa/rearm. Raw/captures/grants/modules ở hai bundle `phase31-original-*` dưới D:\TSDuck.
- **Android toolchain đã kiểm:** build declarations giữ AGP 9.5.0/wrapper 9.8.2/Build-Tools 37.0.0/NDK 30.0.16248370/Java 8/minSdk 21; runtime JDK 25.0.5.0.3. Không nâng/cài SDK/JDK/dependency. Fresh JVM 530 và debug/release/lint PASS; native GCC 16.2 và MSVC ASan PASS, NDK syntax bốn ABI/API21 PASS. GCC ASan link thiếu libasan, không cài thêm; Android physical/ADB/instrumentation mới NOT RUN.
- **GitNexus:** CLI 1.6.12 phát hiện; `status --json` không trả sau cửa sổ chờ, đã dừng đúng helper PID/tree. Không certify index current, không reindex/repair hoặc dùng graph làm bằng chứng. Nghiên cứu dựa trên source/hash/diff và tests. Một task-agent không khởi chạy do model unsupported; không có kết quả giả hoặc đổi model để retry.

## 2026-11-09, phiên 19:18 +07 — Samsung A/B và công cụ offline hiện hành

- **Git:** branch `chore/modernize-android-build`, HEAD `48b53d788db5e023123afbc0a65f77a16ae084ef` trước/sau; có sẵn `.github/copilot-instructions.md` modified và `.github/instructions/virtualbox-ubuntu-ssh.instructions.md` untracked, giữ nguyên. Không sync, stage/commit/push hoặc sửa references.
- **Tablet đã kiểm trực tiếp:** SM-X926B/API 36/boot 46/kernel `6.1.145-android14-11-abX926BXXSBDZI2`, ADB Wi-Fi có hai alias của cùng tablet; chọn một endpoint tường minh, không ghi endpoint/serial vào Git. Native GET_SPEED trả 3, operational EP82 maxpacket 512. Quyền OS vẫn bắt buộc; popup Samsung không luôn hiện trong hierarchy. Tracefs có thư mục nhưng event USB/usbmon không usable bằng quyền shell hiện có; ghi NOT_AVAILABLE_NONROOT, không root/SELinux change.
- **Thiết bị/nguồn:** người dùng xác nhận tuner chưa cắm ban đầu, rồi hai lần ngắt nguồn ≥5 giây/đèn tắt, cùng tablet/cáp/nguồn/RF IN cho A/B. Không đo điện thực tế và không đổi adapter/DC/LNB. RAM mỗi attachment chỉ nạp một lần đủ 132 transfers; các phiên operational mới không A0 hoặc xóa/rearm consumed stores. Cả A/B REPORT/outstanding 0/quarantine false trước khi ADB chuyển offline; không tự reconnect hoặc chứng nhận owner live sau mất ADB.
- **Artifact:** inventory giới hạn bốn gốc D:\TSDuck có 1964 file lúc đầu; tám raw artifact cũ khớp hash/manifest. Linux strict/credit1 text/pcap FOUND_VERIFIED, không VM/SSH/runtime Linux mới. Android mới có 137/190/132/190 EP0 records cho hai bootstrap/operational sessions, 16 file đã thu bền/hash Android-host khớp; raw/firmware/TS/grants vẫn ngoài Git.
- **Toolchain giữ nguyên:** đọc build declarations AGP 9.5.0/wrapper 9.8.2/SDK 37.2/Build-Tools 37.0.0/Java 8/minSdk 21; Java thực 25.0.5.0.3 và JAVA_HOME tồn tại, NDK source.properties 30.0.16248370. Không cài/update SDK/JDK/dependency. TShark 4.6.9 đọc capture cũ; Python 3.12.2 có sẵn được agent Gate B dùng offline sau configure, GCC 16.2/MSVC 19.29/NDK clang 21 kiểm native. Không gọi TSDuck tuner hoặc USB capture mới.
- **Build/test APK:** A/B dùng APK đã cài SHA `8f51af4a748ee6f7253efb11da4d73e264dc3ef7fdac85fdd1c3cbf39c41c5c0`; build debug/release/lint và JVM 530 PASS, tablet fake/permit 6 tests PASS. Sau khi thu A/B mới thêm nhãn Activity debug riêng và build/lint PASS; bản nhãn cuối chưa cài/chạy do ADB offline. Không gán kết quả hardware cho APK mới.
- **Ứng dụng test và chooser:** theo quyền user đã gỡ `.phase29offline`/`.phase29offline.test`, không gỡ app chính hoặc clear data. Query package sau đó xác nhận app chính có hai USB entry: AutonomousDiagnosticActivity và legacy UsbDelegate; hai nhãn không phải bằng chứng có hai driver cùng sở hữu tuner. Nhãn debug được tách, legacy filter giữ nguyên.
- **GitNexus:** CLI/MCP 1.6.12 trỏ đúng repo/index `.gitnexus`, ban đầu chậm một commit. Status CLI không trả sau thời gian chờ nên dừng đúng helper; analyze --index-only sau đó exit 0/121 s, 9000 nodes/30967 edges/788 flows, không FTS error quan sát được. Context Handoff dùng để thấy callers/tests; có cảnh báo cross-language/flow truncation. Index lúc giữa phiên không bao phủ mọi edit/docs cuối; source/diff/tests là bằng chứng chính, không chứng nhận graph cuối current.

Báo cáo tổng: `D:\TSDuck\reports\phase31-continuation-20261109-192232-71163e13\phase31-continuation-result.md`. Không chạy VBoxManage/SSH hoặc snapshots trong phiên này vì raw Linux đã trả lời câu hỏi cần phân biệt.

## 2026-11-09 — Lượt Linux operational-only: handoff mới và công cụ guest

- Branch/HEAD kiểm lại không đổi: `chore/modernize-android-build`/`69090a0bcf00b4c6342ec075d90a74964b25ecc9`; local edits trước đó giữ nguyên, không sync/commit/push hoặc snapshot.
- Sau thông báo và user ready, VBox usbattach đúng tuner thành công; guest Ubuntu 24.04.5/kernel 6.8.0-149-generic thấy EHCI 480, sysfs 2-1/bus 2/dev 2/config 1/interface 0/alt 0. Hai Bulk IN82/81 đều 512, EP0=64; raw descriptor 50 byte có SHA `22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5`. Chỉ dùng tuple này sau kiểm mới, không hardcode BUSID lịch sử.
- Có enumeration latency sau usbattach: lệnh đầu chưa thấy target, lần kiểm trạng thái sau thấy VMCaptured và guest có device; không auto-reattach hoặc gọi lần đầu là USB failure. Diagnostic/TBS/CXD/M88 chưa loaded ở checkpoint chuẩn bị.
- Usbmon stock guest đã được nạp theo scope continuation; debugfs đã mount sẵn. `tcpdump` 4.99.4/libpcap 1.10.4 thấy `usbmon2`/USB_LINUX_MMAPPED. Preflight 3 s với descriptor reads thu 18 packets/1591 byte/dropped 0; không vendor/I²C/firmware. Giữ clock riêng với Windows và Android.
- Công cụ DVB guest ban đầu thiếu; apt simulation 0 upgrade/2 new/0 remove, cài `dvb-tools` và `libdvbv5-0t64` 1.26.1-4build3 thành công, không đổi kernel hoặc service. Receiver dùng trực tiếp Linux DVB API exact profile để tránh defaults LNB/LNA của CLI; đã build/self-test/fake flows không hardware.
- Bundle mới `D:\TSDuck\logs\phase31-linux-runtime-20261109-004247-91506f8c` và sibling reports/traces/captures. Host/guest ledger dùng namespace mới riêng, không rearm history Android hoặc snapshot. Current descriptor không phải chứng cứ firmware image đã readback.
- Runtime lúc 01:19 +07 load board SHA `be006fdadf5b3c4cf6e519204d06eeb809ff4fb6f01830e16012fcaf40286b0d` và dependencies từ scratch, không install vào cây modules. Guest kernel đã taint vì module ngoài cây/unsigned, không đổi signature policy. Probe/K36 đạt, actual 82 rồi zero completion, chưa TS; board/CXD/ALP/dvb_core đã unload. Không reset/reboot guest.
- Epoch mới sau user-confirmed power-off và Windows capture: bus 2/dev 3/HS 480, descriptor hash giữ nguyên. Candidate credit 1 board SHA `07d812a13d511231d8dfc084ab02675e7345a8e1602e8205e8b8d7c0a2612a47` chỉ đổi policy zero-success, giữ Sony/queue. K36 lock đạt nhưng chờ 10 giây vẫn không có TS. Cleanup và rollback cuối đã kiểm: board/CXD/ALP/dvb_core/usbmon đều vắng, guest không còn tuner, Windows PnP OK/TBS5530 DVB BDA và usbipd Shared. Không snapshot, kernel/core/binding change hoặc xóa ledger; warning USBPcap/usbipd vẫn còn.

## 2026-11-08 → 2026-11-09 — Phase 3.1: Windows EHCI, ETW và guest VirtualBox mới

- **Git/artefact:** HEAD thực `69090a0bcf00b4c6342ec075d90a74964b25ecc9`, branch `chore/modernize-android-build`, có local edits trước phiên. Inventory ban đầu dưới `D:\TSDuck` có 420 file/211.465.062 byte; manifest SHA cũ được đối chiếu sidecar thành công. Raw USB/TS/ETL, file grants, source cache và logs vẫn ngoài Git.
- **Windows capture mới:** Windows 10 Pro 19045.7807, TSDuck 3.45-4798, TShark 4.6.9, USBPcap 1.5.4.0. PnP parent chain của TBS5530 đi qua Intel 8 Series/C220 Series USB EHCI #2 - 8C2D, USB Root Hub/Generic Hub. Không mặc định là xHCI vì Android có đường xHCI. Driver TBS 2.0.0.2, SYS 404.168 byte/SHA `48f2d8fefd467adc4dafe68c2708439e794c4c1daf69f7db5405b5388cf9e65c`; mode DVB-T/T2 đo bằng BDA trong lượt mới.
- **Quyền/cleanup:** người dùng đồng ý UAC helper và ExecutionPolicy Bypass chỉ child process cho audit/tests/helper, không đổi User/Machine policy. Recorder giữ PID/arguments/start/end, ETW session có tên riêng. Cold2 ETW rộng đạt cap 64 MiB trước attachment; targeted USBPORT Diagnostic level 4 sau đó có 0 EventsLost/0 BuffersLost và stop exit 0. Tracerpt ghi offset `+06:59`, khác helper `+07:00`; không tự căn đồng hồ. Không cài/đổi USBPcap, BDA, ChangeMode hoặc chạy bind-force.
- **Toolchain Android giữ nguyên:** declarations thực AGP 9.5.0/wrapper 9.8.2, SDK 37.2/Build-Tools 37.0.0, NDK 30.0.16248370/CMake 4.1.2, Java 8/minSdk 21. Audit ban đầu bị RemoteSigned chặn; sau quyền child-process đã chạy exit 0. Build debug/release và lint thành công; JVM fresh 513 tests. Không dùng XML cũ hoặc UP-TO-DATE làm PASS mới.
- **WSL snapshot lúc audit:** tên đăng ký Ubuntu-22.04 nhưng userspace thực Ubuntu 26.04.1, kernel 6.18.54.1-microsoft-standard-WSL2. Build symlink có thật nhưng thiếu Makefile/Kbuild/generated headers/Module.symvers; compiler probe exit 0, Kbuild prerequisite probe exit 2, chưa build TBS module. Không sửa kernel, load module hoặc USB ownership trong nhánh WSL.
- **Thay đổi do người dùng giữa phiên:** sau audit đầu, người dùng cài VirtualBox 7.2.22r175245/VBoxUSBMon 7.2.22.175245, báo đã từng attach tuner qua usbipd và WSL thấy VID:PID. Đó là USER-CONFIRMED ở thời điểm khác; kiểm mới sau đó thấy Windows Shared, không Attached. Không dùng snapshot audit cũ “chưa có VM” làm hiện trạng mới.
- **Guest do người dùng cài và cấp SSH:** alias `ubuntu-VMS`, host key strict đã khớp, root login qua cấu hình hiện có; không ghi credential/endpoint vào Git. Guest Ubuntu 24.04.5 LTS/kernel 6.8.0-149-generic, headers/Makefile/Module.symvers/generated autoconf đầy đủ, 2 GiB/2 vCPU, NAT, EHCI bật/xHCI tắt. Compiler/make ban đầu thiếu; sau user cho phép và apt simulation không upgrade/remove, build-essential/libelf-dev cùng dependencies đã cài exit 0, GCC 13.3.0/Make 4.3. Chưa module load hoặc USB claim.
- **Snapshot không còn là workflow:** live snapshot thất bại exit 1 với `RPC_S_CALL_FAILED (0x800706be)` và làm guest treo theo người dùng; họ tự phục hồi rồi cho tiếp tục không snapshot. Không tạo lại, restore/delete snapshot hoặc tự xóa differencing VDI còn lại. Package inventory không phải backup toàn VM; mọi thay đổi media stack/kernel vẫn cần quyết định riêng.
- **GitNexus:** 1.6.12 index-only thành công tại HEAD hiện hành, 8.595 nodes/26.457 edges, không FTS writeback error quan sát được. Có cảnh báo graph thiếu flows/liên kết khác ngôn ngữ; sau thêm normalizer/docs index không bao phủ mọi thay đổi cuối. Graph chỉ hỗ trợ callers native backend; source và tests là bằng chứng chính.

- **Guest build đã chạy sau phục hồi:** official TBS và Beta minimum closure exit 2 vì frontend API vendor; board-only có 32 CRC khớp nhưng thiếu frontend. Community C nguyên trạng compile/MODPOST đủ 5 `.ko` và 226 CRC khớp, vermagic đúng kernel. Community mặc định không load-ready trong scope no-LNB vì probe có vệ tinh. Biến thể terrestrial riêng đã build 3 module/MODPOST/108 CRC khớp, 281 fake-plan cases và 25 source checks mỗi PowerShell PASS; final board SHA `4fe9a866a45e6381f14f6039e6dff95517848162f2ba34ac056fa82107eb21b9`. Guest source nằm `/root/phase31-terrestrial-diag-0012/source`, modules unsigned/chưa loaded, không thấy tuner trong guest. Windows listing cuối vẫn Shared, không đổi binding hoặc snapshot; chưa có Linux runtime.

Các mốc lịch sử phía dưới giữ nguyên. Không suy module chạy được hoặc có TS từ headers tồn tại, SSH thành công hay compile PASS.

## 2026-11-08 — Môi trường phép đối chứng tablet sau khi USB nhận ổn định

- Phiên bắt đầu từ yêu cầu tiếp nối ngày 07-11 lúc 23:01 +07 và kết thúc kiểm qua ngày 08-11. Tablet đã pin SM-X926B/API 36/arm64-v8a/kernel `6.1.145-android14-11-abX926BXXSBDZI2`, cùng boot trước các grant. ADB dùng Wi-Fi và một endpoint TCP cụ thể; không reconnect/failover giữa giao dịch USB.
- Người dùng xác nhận anten thụ động ở RF IN, đèn POWER ổn định và mô tả nguồn USB qua dây chữ Y: Type-B vào tuner, một Type-A qua Type-C vào tablet, Type-A phụ vào sạc điện thoại. Không có phép đo điện áp/dòng hoặc kiểm chứng cách cách ly nguồn của dây; không coi đây là kết luận cổng DC 12 V hỏng hoặc không cần nguồn đúng chuẩn. Không có thao tác chỉnh nguồn, LNB hoặc anten DC bằng phần mềm.
- Raw sysfs speed/config/alternate setting và tracefs `available_events`/`format` bị Permission denied. Native trên fd đã cấp quyền đo GET_SPEED enum 3; descriptor operational maxpacket 512 là bằng chứng riêng. Không suy tốc độ chỉ từ descriptor.
- Perfetto service v51.2 liệt kê linux.ftrace, có thư mục event xhci-hcd, nhưng FtraceStats báo `xhci_urb_enqueue`, `xhci_urb_giveback`, `xhci_handle_transfer` là unknown qua đường Perfetto. Trace 2 giây và 20 giây lần lượt 1590/1592 byte, không có ftrace bundle hoặc compressed packet. Query cuối xác nhận `Tracing sessions: 0 (started: 0)`; không root, SELinux, setprop hoặc usbmon bypass.
- APK ban đầu SHA `ea6a48ac85e653faf2c6e4112784d32e8bf31e4f2a931cbc41384b1fc7b38ae0` khớp manifest source cũ trước live. Sau sửa kiểu lỗi attachment lookup, source fingerprint `127e7f550d7db79cd1ea25cd3f763b49c03a2706c407f05722be30e6c7e4f911`, debug SHA `1c51ddf79bb1f3c08dc5b62a219d142e06252d851e0d963c68471dbfd36523a3`, release SHA `0027dd13f4b75e4cdbd93cd51225ec2d1a5b7a918f07f0a472ccaa7e6bd5ef53`. Hai native run thật dùng APK debug mới; không gán kết quả đó cho APK cũ.
- User cho phép ExecutionPolicy Bypass chỉ child process cho kiểm thử/helper phiên mới; policy User/Machine không đổi. Build declarations vẫn AGP 9.5.0/wrapper 9.8.2, JDK theo Machine JAVA_HOME, SDK 37.2/Build-Tools 37.0.0/NDK 30.0.16248370/CMake 4.1.2, Java 8/minSdk 21. Không nâng hoặc cài SDK/toolchain.
- Guard 28 và library 43 instrumentation fake-only PASS trước live và sau sửa; các package phase31offline do phiên cài đã gỡ. Cài đè app chính giữ nguyên hash preferences/history ở bước update, sau đó history chỉ append cho lần upload phục hồi được xác nhận; không clear/uninstall app chính hoặc rearm ledger. NotificationShade từng che UI, đã đóng bằng thao tác giao diện trước cấp quyền; không thay cài đặt bảo mật.
- GitNexus 1.6.12 chạy index-only thành công, analyzer tự chọn rebuild vì capability evidence thay đổi; không dùng force/repair hoặc sinh instruction files. Graph dùng hỗ trợ tra facade, có cảnh báo thiếu flow và liên kết khác ngôn ngữ; source/diff/tests vẫn là bằng chứng chính, không chứng nhận index phủ thay đổi nhỏ cuối cùng.

## 2026-11-07 — Phase 3.1 continuation: công cụ và khả năng kernel đã kiểm lại

- Git thực `1202c8abd49a48c3099e718bead13a21a9176660`, nhánh `chore/modernize-android-build`, ahead/behind local-tracking 0/0 trước sửa; không fetch/commit/push. Closeout gốc SHA-256 `a1f37626cef5b6401d73fe48a4a5340317ea804725d6fd5cb9ec8d6ba2ec1160`.
- Audit toolchain ban đầu bị RemoteSigned chặn; người dùng cho phép ExecutionPolicy Bypass chỉ child process cho audit/tests/helper phiên này. Audit chạy exit 0, không đổi policy User/Machine hoặc package. JDK 25.0.5.0.3, wrapper 9.8.2, AGP 9.5.0, SDK 37.2/Build-Tools 37.0.0, NDK 30.0.16248370/CMake 4.1.2, Java 8/min 21 giữ nguyên. AVD TBS5530_Runtime_API37 chỉ inventory, không boot. Báo cáo text đúng tại `D:\TSDuck\logs\phase31-toolchain-plain-20261107-1816.txt`; lần export JSON trước đó chỉ chứa Length của output strings, không dùng làm inventory cấu trúc.
- ADB ban đầu không có device, mDNS quảng bá endpoint TLS; kết nối lại trước grant và kiểm GM1900/API 30/arm64-v8a/cùng boot. Pin một endpoint, không failover trong USB I/O. Kernel `4.14.190-perf+`, build Android 11 user/release-keys; kernel Logcat đọc được dưới shell, dmesg/sysfs giá trị/tracefs format bị chặn. Topology DWC3/xHCI đọc được, không ghi serial/IP/boot vào Git.
- Perfetto có service linux.ftrace nhưng tên nhóm xhci-hcd bị phiên bản Android 11 từ chối dấu gạch nối. Hai trace kiểm khả năng 2 giây đã kết thúc, không claim packet-level trace; raw nằm ngoài Git. Không root, setprop, SELinux, driver hoặc USB ownership changes.
- Android CLI layout trên stdout làm mất dấu tiếng Việt trong môi trường hiện tại; dùng `--output` ra UTF-8 giữ nguyên chữ. Hộp thoại UsbPermissionActivity/UsbConfirmActivity của OEM hiện chỉ lộ navigation bar trong hierarchy, nhưng screenshot đọc được. Không coi hierarchy thiếu node là không có dialog; không chọn Luôn mở. Helper có semantic path, fallback trực quan vẫn cần kiểm từng ảnh/token; chưa chứng nhận tự động headless trên OEM này.
- Native host tests dùng GCC 16.2 hiện hữu và MSVC 14.29/AddressSanitizer trong VS 2019 BuildTools, không cài tooling. Debug linked APK có thư viện chẩn đoán riêng đủ bốn ABI; release không có thư viện đó. Static zipalign/ELF64 16 KiB và major 52 đã PASS ở checkpoint build trước patch preflight; gate runtime API 21/16 KiB mới NOT RUN.
- Đã cài test app/test runner namespace phase31offline và library runner riêng của phiên; 118 app + 43 library PASS sau tách hai benchmark yêu cầu namespace phase29offline. Không thay package `info.martinmarinov.drivers.test` đã có trước. Ba package phiên tạo đã gỡ; app production cài đè giữ nguyên SHA preferences và history trước live. Thay đổi history sau pre-native refusal được giữ, không xóa/rearm.

## 2026-11-07 — Phase 3.1: môi trường đã đo sau epoch và OTG retest

- APK chạy ba receive conditions có SHA debug `9ecb806b6f6a04ea1ec121a6330a012b4a0ebfe8b2928d4fe67186c999afe11b`, source fingerprint `7e158a18841307cbce693e38f9b19e8ba70adda553a15138ac7f3d037c148740`, HEAD `1ad3b251f19779450a2c5d9cffc9f37f9c9a5470`. JDK/Gradle/SDK/NDK/CMake/ABI giữ nguyên; full JVM 429, app 97/library 43 instrumentation PASS trước hardware. Final sau zero-completion patch: debug SHA `6d04f7dee03a44cd34a92931a08d689020c9d6ed40617b3d68c58483afd21311`, release SHA `f82573c0ba46d0645eebc194c9d0c07972366819b6a4c2cd07b983e34b4d6085`, source fingerprint `3fe396af975baea76b00bc21a60c691655a080df549b11418100713f19ebd189`. Final 430 JVM/97 app/43 library PASS, static PASS; final APK đã cài đè và kiểm SHA trên điện thoại, không chạy lại USB hardware hoặc gán kết quả cũ cho patch mới.
- Đã thêm JNI chỉ đọc USBDEVFS_GET_SPEED vào library hiện hữu, dùng fd đã được UsbManager cấp. Native fd âm được instrumentation kiểm không ioctl; bốn ABI compile/static PASS. Android thật initial/operational trả enum 3 theo NDK USB_SPEED_HIGH, maxpacket 512 đọc descriptor riêng. Không mở /dev/bus/usb, không root hoặc sysfs permission bypass.
- Người dùng xác nhận một physical recovery sau UNCERTAIN; epoch cố định cho đúng sự kiện đó bound immutable grant, không mint mỗi retry. Firmware phục hồi 132/132 full-length và handoff sạch, duration 4706 ms; OS operational permission mới được cấp bằng UI. Queue và OTG retests đều operational, không upload firmware. Ba history keys chống reuse chỉ tương ứng hai uploads thật cộng một refusal native = 0; không xóa/rearm lịch sử.
- Sau queue result 0/errno unknown, đã dừng, drain và Back. Người dùng báo bật Lưu trữ OTG, UI switch BẬT/CHECKED được đọc trước/sau; agent không tự đổi cài đặt. Một sync retest fresh-owner theo yêu cầu mới vẫn errno 75, 0 bytes. Không có OTG/cáp/Android host khác để đối chứng. Settings key chỉ là bổ sung, trạng thái ON được lấy từ UI, không suy từ tên key.
- Synchronous baseline/queued/OTG reports đều cleanup DRAIN_CONFIRMED; queue outstanding 0/released true. Helpers tự kết thúc, không service/port mới; sáu legacy preferences giữ nguyên. Lint 0 errors/13 warnings, API 21/16 KiB runtime mới NOT RUN. Raw firmware/private grants/boot/serial ngoài Git; terminal redacted và report tổng hợp dưới D:\TSDuck.

## 2026-11-07 — Phase 3.1: môi trường sau hai phiên autonomous và phục hồi vật lý

- Trước firmware thật: wrapper/full JVM 421 PASS, app 94/library 43 instrumentation cách ly PASS; giữ JDK/Gradle/SDK/ABI như checkpoint dưới. Lint 0 errors/13 warnings, static debug/release PASS; release không chứa Activity/policy/queue/uploader debug. Cài đè app production bằng `adb install -r` giữ nguyên SHA sáu preference files.
- Người dùng cho phép tự cấp quyền hệ thống; đã dùng Android CLI đọc UI và bấm OK đúng hộp thoại USB, không sửa OS permission bằng shell/root. Metadata hiển thị quyền cũ trong report không thay terminal permission ĐÃ CẤP. Helper và Activity foreground phản hồi; không giữ màn hình sáng bằng setting hoặc service dài hạn.
- Session firmware đầu full-length 132, native 132/132 và 8192 image/8196 payload, sau đó UNCERTAIN và drain sạch. Chỉ marker `autonomous-v1-history.xml` mới xuất hiện; sáu preference lịch sử giữ nguyên SHA, không sửa/rearm consumed. Người dùng tự rút nguồn khoảng 5 giây/cắm lại theo yêu cầu phục hồi một lần; không software reset/usbipd/driver swap.
- Sau phục hồi, TCP port ADB lại thay; trước grant mới đã pin lại một TCP online sau kiểm cùng GM1900/API 30/boot, không reconnect hoặc fallback trong giao dịch. Tuner vẫn hiện đúng VID/PID; permission mới được cấp qua UI. Refusal với native = 0 sau raw-before không được gọi là upload hoặc hardware error; đang kiểm generation key tái dùng.
- Rebuild handoff patch PASS trong 2 phút 01 giây, isolated build PASS trong 1 phút 09 giây; instrumentation 95 app/22,787 giây và 43 library/2,095 giây PASS. Static ELF/JNI/major 52 và release isolation PASS có phạm vi. API 21/16 KiB runtime mới và queued USB hardware vẫn NOT RUN; không lấy static hoặc firmware full-length thay MPEG-TS proof.

## 2026-11-07 — Phase 3.1: Git mới, TSDuck/USBPcap và môi trường kiểm phiên tự động

- Git live là `1ad3b251f19779450a2c5d9cffc9f37f9c9a5470`, nhánh `chore/modernize-android-build`, origin đúng fork. Khai báo và cài đặt đã đối chiếu: wrapper 9.8.2/AGP 9.5.0, Machine JAVA_HOME `C:\Program Files\Java\latest\jdk-25` chạy 25.0.5.0.3, Java source/target 8, SDK 37.2/Build-Tools 37.0.0, minSdk 21/target 37, NDK 30.0.16248370/CMake 4.1.2. Không nâng/cài/gỡ package. Script audit ban đầu bị ExecutionPolicy chặn; kiểm trực tiếp các declaration/source.properties không được gọi là audit script PASS.
- TSDuck thực cài 3.45-4798 tại `C:\Program Files\TSDuck\bin`; Node 26.7.0 dùng parser offline ngoài Git. TShark và USBPcapCMD hiện hữu; capture thường bị từ chối quyền, người dùng sau đó duyệt một tiến trình UAC hữu hạn. Không thay BDA/WinUSB/filter/registry/mode. BDA mới công bố DVB-T/T2 và lock; hub báo High-Speed. Mọi TS/report/trace mới trong các run Gate A/B dưới `D:\TSDuck`, không ghi đè dữ liệu cũ.
- OnePlus GM1900/API 30/arm64-v8a online được pin TCP rõ ràng. Sau chuyển tuner, TCP port cũ biến mất; trước mọi grant/write đã đọc danh sách mới, xác minh lại cùng model/API/boot rồi chọn endpoint TCP mới, không `adb connect/reconnect` hoặc chuyển alias giữa giao dịch. Không ghi IP/serial/boot identity vào Git. Read-only sysfs không trả bằng chứng USB speed Android; không root hoặc suy link speed từ endpoint maxpacket.
- AVD `TBS5530_Runtime_API37` còn được `emulator -list-avds` liệt kê khi gán AVD home đúng; không boot/wipe hoặc cài image. Runtime API 21 và 16 KiB mới NOT RUN. Queued diagnostic yêu cầu API 26+ để dùng requestWait có timeout; release và synchronous cũ không đổi minSdk.
- Build/tests dùng JAVA_HOME/ANDROID_HOME chỉ trong child. Full JVM đầu 413 PASS; debug/release/lint PASS, 0 errors/13 warnings. Kiểm APK đầu đạt bốn ABI, JNI symbols, ELF64 alignment 16 KiB, zipalign, major 52 (174 debug/83 release classes), debug-only autonomous/queue vắng release và không bundle firmware/TS/trace. Các hash APK cuối sẽ ghi sau final rebuild; không gán APK checkpoint đầu là APK chạy hardware.
- Namespace `.phase31autooffline` được xác minh chưa có, cài 3 APK rồi chạy fake-only; 43 library PASS, app có 6 FAIL do harness/guard đang sửa. Đã gỡ đúng ba package phiên tạo; không uninstall/clear app production hoặc dữ liệu cũ. Đang chuyển harness về namespace `.phase31offline` đã xác minh trống để đáp ứng test cũ, không sửa assertion hoặc nới guard cho PASS.
- Người dùng cho phép ExecutionPolicy Bypass chỉ trong tiến trình self-test/helper phiên này; không thay User/Machine policy. Host helper offline đã PASS, không ADB/USB từ self-test. Firmware sẵn trong Download của OnePlus được đọc ra private session storage, xác minh lại 8192 byte/SHA khóa; chưa upload tuner trong checkpoint này, không bundle APK hoặc tải nguồn mới.
- GitNexus 1.6.12 đã chạy incremental `analyze --index-only .` thành công 103,6 giây, không sinh instructions/skills. Index được dùng hỗ trợ tra owner facade, có cảnh báo thiếu flow do budget và liên kết khác ngôn ngữ; không xem thiếu kết quả là unused. Source tiếp tục đổi sau lần index nên không chứng nhận snapshot đó bao phủ mã cuối. Editor còn báo Java ngoài classpath, trong khi Gradle compile/tests thực chạy được.

## 2026-11-07 — Phase 3.1: môi trường chạy v4 và giới hạn chẩn đoán USB

- Git vẫn `9621a8028d2cd3e5d98652cde69ba80d27cd7ee2` trên `chore/modernize-android-build`, đúng fork; source fingerprint cuối `988919f6fe1c8cc070c4488c86caf50dd77cf652397221f96eab6502fb674270`. Stage trống, chưa commit/push hoặc thay nhánh; ba reference trees không sửa. Hồ sơ cũ bên dưới giữ trạng thái theo thời điểm, không áp lại các câu NOT RUN trước những lượt live mới.
- Các phiên bản khai báo, cài đặt và dùng cho kiểm thử giữ nguyên: Gradle 9.8.2, AGP 9.5.0, JDK Machine 25.0.5.0.3, compile SDK 37.2, minSdk 21/targetSdk 37, Build-Tools 37.0.0, NDK 30.0.16248370 và CMake 4.1.2. Mỗi Gradle child nạp JAVA_HOME từ Machine và chỉ định SDK `D:\Android\Sdk`; không sửa biến môi trường toàn máy, cài/gỡ/nâng dependency hoặc dùng kết quả audit lịch sử thay kiểm mới. Kiểm phiên bản stable mới nhất không thuộc lượt này.
- Đã dùng TCP được pin và kiểm lại model OnePlus GM1900/API 30/boot trước mỗi workflow; sau ADB loss, người dùng tự khôi phục và duyệt workflow mới. Không tự reconnect/fallback mDNS/USB alias; không lưu IP/serial/boot/approval identity trong Git. Cài đè APK production được user duyệt, giữ dữ liệu; so hash Phase29/30/31 consumed stores trước/sau install giống nhau. Phase31 chỉ append consumed mới trong chương trình được duyệt, không clear/uninstall production.
- APK debug v4 đã cài và chạy errno thật: SHA-256 `9be0e4687de96a2ad2a690b5ca457ec875b916d2aa8128a53a6a18566950ef93`; release unsigned `fa4287d94d6558371b9f5a00fdac99d65d59303f327088396f3bbddc9e7652bf` chỉ build/static, không cài release. Bốn ABI có JNI clear/read, ELF64 alignment 16 KiB; Java 8/min 21/target 37 giữ nguyên. Native load/negative-fd chạy API 30; API 21/16 KiB runtime mới NOT RUN.
- Ba package `.phase31offline` chỉ dùng fake-only instrumentation cuối: 67 app/12,351 s + 43 library/1,589 s PASS; không đọc production preferences hoặc USB thật. AAPT kiểm package trước cài; việc gỡ đúng ba package phiên này tạo được ghi ở checkpoint cleanup của [WORKLOG.md](WORKLOG.md), không xóa dữ liệu app chính.
- Phần cứng còn nguồn/antena thụ động RF IN được người dùng xác nhận cho từng workflow; không LNB IN, antenna DC, satellite, EEPROM/flash, capture, WSL/usbipd/BDA, driver swap, reset hoặc power-cycle. Một upload mới duy nhất khi FX2_DEFAULT; sau observation operational không upload nữa. Firmware ledger cuối 812 records/271744 byte, prefix lịch sử không bị đổi; Phase29/30 stores sau lượt v4 vẫn khớp trước lượt đó.
- V4 native nhận `errno=75 (EOVERFLOW)` ngay sau USBDEVFS_BULK trên cùng claimed fd; request 4096 byte/500 ms. Read-only sysfs không xác lập được tốc độ bus của tuner từ quyền hiện có; không suy USB speed chỉ từ descriptor max packet 512 byte, không nâng quyền để lấy thêm dữ liệu. Không có packet capture, host-controller trace hoặc Windows TS đối chứng mới để xác định phía gây overflow.
- Kết thúc smoke đã cancel/drain/close và Back khỏi Activity operational; kiểm `mCurrentFocus` không còn Activity đó. Helper bounded và child đã kết thúc, không giữ màn hình/owner chờ. Chỉ giữ logs/summary/fingerprint riêng ngoài Git; không thu được TS raw để lưu. Quyền hardware của các lượt đã consumed, không còn quyền cho lần thử tiếp.
- GitNexus index chưa xác minh từ CLI 1.6.12 treo banner, không dùng graph/reindex/repair; dùng source/diff/tests. Editor còn diagnostic `UsbHiSpeedBulk.java is not on the classpath`, trong khi wrapper compile/native/JVM/instrumentation PASS; không đổi IDE hoặc dependency để chữa diagnostic ngoài classpath.

## 2026-11-06 — Phase 3.1: toolchain giữ nguyên, kiểm thử Android cách ly

- Git thực lúc bắt đầu: `9621a8028d2cd3e5d98652cde69ba80d27cd7ee2`, `chore/modernize-android-build`, origin `trankien84/AndroidDvbDriver`; không có commit mới sau baseline. Worktree/stage ban đầu sạch; thay đổi cuối chưa stage/commit/push. References chỉ đọc, ba SHA giữ nguyên như [RESEARCH.md](RESEARCH.md).
- Đọc build đang khai báo: Gradle 9.8.2/checksum wrapper hiện hữu, AGP 9.5.0, compile SDK 37.2, target 37/min 21, Build-Tools 37.0.0, NDK 30.0.16248370, CMake 4.1.2, Java source/target 8. JDK Machine JAVA_HOME thực chạy 25.0.5.0.3; mỗi Gradle child dùng giá trị Machine, không thay User/Machine environment hoặc PATH. SDK source.properties xác nhận các package đã chọn, một AVD `TBS5530_Runtime_API37` được liệt kê, không boot/wipe hoặc cài gì.
- `Inspect-TBS5530-Toolchain.ps1 -NoReport` bị ExecutionPolicy chặn vì script chưa ký; không dùng lại approval Bypass Phase 3.0. Kiểm kê trực tiếp JDK/SDK/build files thành công, không gọi đó là script audit PASS hoặc latest-stable audit. VS Code runTask vẫn không tìm task assembleDebug; dùng Gradle Wrapper đã khai báo, không tạo task hoặc sửa terminal profile.
- ADB SDK thực 37.0.1-15733141. Có hai endpoint online cùng model GM1900/API 30 (TCP và USB_OR_MDNS); chọn rõ duy nhất TCP đã đọc model/API, lưu target riêng ngoài Git, không failover alias. Không ghi IP/serial/boot/approval GUID vào hồ sơ. Không dùng listing này để suy tuner đang cắm, có nguồn hoặc còn firmware.
- Chỉ cài ba package mới đã AAPT kiểm: `info.martinmarinov.dvbdriver.phase31offline`, `.phase31offline.test` và `info.martinmarinov.drivers.phase31offline.test`; library tự target chính test package. Trước cài xác nhận chưa có các package đó, ghi inventory riêng để chỉ dọn thứ phiên này tạo. Không cài APK production, không clear/uninstall production hoặc thay consumed/ledger thật. Các thao tác cleanup cuối được ghi tiếp trong [WORKLOG.md](WORKLOG.md).
- Instrumentation cuối dùng manager/raw/approval storage giả, không bấm connect/execute production: app 61 methods PASS/11,247 s, toàn bộ library 43 PASS/1,675 s. Có startup/IME/pause-resume của UI thật nhưng không USB thật. App selector loại host full-path benchmark và audit-cost benchmark Phase 2.9 vốn đòi namespace/harness riêng; không gán hai bài đó PASS mới. API 21/16 KiB runtime mới và host provisioning protocol 2 live NOT RUN.
- Full JVM 355 PASS, debug/release/lint PASS (0 errors/10 warnings cũ); static 148 TBS main classes major 52/minSdk 21/target 37/zipalign/DEX isolation đạt. APK debug SHA-256 `8941bf684d14a07180150cdbe217b4522d0b61e6d4353168b12b53052cc2e69a`, release unsigned `c4bb10ddf715b3ffffde12174a1081a13a6167fabaf1e8328e36253f9f5cde7a`; chưa cài production nên không coi hash này là APK chạy hardware.
- GitNexus CLI 1.6.12 được phát hiện; `status` chỉ in banner và không hoàn tất trong thời gian chờ, đã dừng đúng shell/process tree. Không xác nhận index current hoặc FTS healthy, không reindex/repair hoặc chỉnh MCP. Editor vẫn báo Java ngoài classpath, thêm một diagnostic không khớp compile ở trường compatibility; Gradle compile/tests là bằng chứng, không sửa IDE toàn hệ thống.
- VSQI trả Website Disabled; trang Bộ/HTML QCVN 2020 và ba source Linux công khai đã đọc. PDF 2012 native không được model cung cấp nội dung, không tự trích bằng công cụ hoặc báo đã đọc; fetch bản DOC công khai lỗi transport. Không gửi tài liệu private, firmware, TS hoặc USB audit lên web.
- Phiên này chưa có consent phần cứng mới, chưa đọc descriptor tuner thật, chưa A0/I²C/tune/status/0x8A/Bulk. Không dùng WSL/usbipd/BDA, capture, LNB/antenna DC, power-cycle hoặc driver swap. Owner/init/RF/approval Phase 3.0 đã hết, chỉ còn giá trị lịch sử.

## 2026-11-06 17:57 +07 — Bổ sung nguồn tần số, không thay môi trường

Bốn ảnh Công báo do người dùng cung cấp xác nhận K36 có giới hạn 590–598 MHz, trung tâm 594 MHz và độ rộng 8 MHz; không nêu PLP. Không đọc lại USB, khởi động owner, cấp permit, tune hoặc Bulk từ việc nhận ảnh. Môi trường/toolchain và các kết quả hardware bên dưới giữ nguyên; chưa xác nhận lại attachment/nguồn sau khi owner đã đóng.

## 2026-11-06 — Checkpoint sau live Phase 3.0

- Cùng OnePlus GM1900/API 30/USB Host và nguồn OTG đã được người dùng xác nhận, không đổi toolchain/driver/host. APK debug production mới SHA `5965aced4de6c56efad951586f892dc07b4cc5d17915e64b9526d2ed711a7a36` được cài đè theo approval, consumed stores cũ so hash trước/sau giống hệt; không uninstall/clear production.
- Hai approval firmware độc lập trong phiên: một reservation dừng trước native do thiếu start reserve, một upload hoàn tất 132 full-length. Sau re-enumeration, user tự grant permission mới; raw riêng đọc operational 50 byte, không dùng cached permission/default descriptor để đoán. Không thêm upload sau operational.
- C/D được duyệt riêng: chip-ID 0x197 và init 65 OUT/14 IN đạt. Không có nguồn phụ/LNB/antenna DC, firmware/EEPROM khác, reset/recovery, USB ownership swap hoặc capture. Bridge/init dùng đúng owner/worker cũ, không thêm connection song song để né session.
- Người dùng xác nhận coax thụ động an toàn vào RF IN và anten từng thu tốt sau init. K36/594 MHz/8 MHz là ứng viên từ thông tin công khai, chưa đo tín hiệu hiện tại; PLP chưa xác minh nên không tune/lock/Bulk. Đã đóng Activity operational, resumed trở về launcher, mọi helper đã kết thúc; không giữ màn hình sáng hoặc owner trong lúc chờ PLP.
- Ledger firmware cuối 545 records/182748 byte, prefix history giữ nguyên. C/D có hai consumed approvals/audits riêng; không ghi thông tin nhận dạng hoặc GUID thật vào Git. Raw descriptors/native audit/permit/host contexts và báo cáo riêng lưu ngoài Git, không thay terminal upload cũ bằng observation về sau.
- Full JVM 343 và instrumentation cách ly 64 methods PASS trên mã mới; debug/release/lint PASS với 0 errors/10 warnings. Native API 21/16 KiB runtime mới NOT RUN; OnePlus/API 30 không thay thế các gate đó. Static 145 TBS classes major 52, minSdk 21/target 37, không bundle firmware/TS/capture, debug-only Activity vắng release và zipalign đều đạt.
- GitNexus refresh index-only sau thay đổi Java thành công: 6715 nodes/20257 edges/587 flows, status chỉ drift resource XML không đổi call graph. Impact detachEvent báo 2 upstream symbols/risk LOW trong module Tbs5530; vẫn có flow truncation nên dùng source và regression tests làm bằng chứng chính, không suy callers thiếu là không có tác động. Không sửa FTS hoặc generated instructions.

## 2026-11-06 — Phase 3.0: inventory mới, Android stock và namespace kiểm thử riêng

- Baseline Git thực `722c4916a83074e8cc5b5c6aeeaee9d2f1190785`, branch `chore/modernize-android-build`, origin là fork `trankien84/AndroidDvbDriver`; worktree/stage sạch lúc bắt đầu. Không fetch, sync, checkout, stage, commit hoặc push. Ba references giữ SHA đã ghi trong [RESEARCH.md](RESEARCH.md), chỉ đọc.
- `Inspect-TBS5530-Toolchain.ps1 -NoReport` ban đầu bị ExecutionPolicy chặn vì script chưa ký. Người dùng duyệt riêng Bypass chỉ cho tiến trình kiểm kê; lần chạy sau PASS, không đổi policy User/Machine. Declared/installed vẫn Gradle 9.8.2, AGP 9.5.0, JDK 25.0.5.0.3, compile SDK 37.2, target 37, min 21, Build-Tools 37.0.0, NDK 30.0.16248370, CMake 4.1.2, source/target Java 8. Không audit latest-stable hoặc cài/cập nhật/gỡ package; không suy bản cài là latest.
- SDK thực `D:\Android\Sdk`, AVD home `D:\Android\AVD`; inventory thấy một AVD `TBS5530_Runtime_API37`, không boot/wipe hoặc thay cấu hình. Platform 34 và 37.2 vẫn có; ADB 37.0.1-15733141. PowerShell là shell chính, Gradle Wrapper là runner build. VS Code `runTask` không nhận task assembleDebug dù JSON có task; dùng wrapper trực tiếp, không tạo task mới hoặc đổi terminal profile.
- Android live được pin rõ OnePlus GM1900/API 30 có USB Host qua target TCP, không dùng alias mDNS làm failover. Người dùng xác nhận cắm lại OTG, không nguồn phụ/backfeed/app khác giữ tuner, và tự bấm OK permission. Không root, chmod, SELinux, driver swap, usbipd/WSL, Windows BDA, capture hoặc đổi Wi-Fi/VPN/router. Serial/IP/boot identity không đưa vào Git.
- Lượt descriptor-only trên APK đang cài hash 12a9…526e đạt default FX2 189 byte, 24 ms, 0 control/claim; không lấy operational 50 byte Phase 2.9 làm trạng thái hiện tại. Firmware local trong Download đã có, hash b97c…d0dc đúng khóa; không tải hoặc copy image mới vào APK/Git. RF IN/coax chưa được xác nhận cắm; không kiểm lock hoặc gán NO_SIGNAL.
- Instrumentation dùng init script tạm ngoài Git để đổi app thành `info.martinmarinov.dvbdriver.phase30offline`, test app `.phase30offline.test`, library test `info.martinmarinov.drivers.phase30offline.test` tự target chính nó. AAPT kiểm package/targetPackage trước cài. Fake USB/approval chỉ dùng manager/storage giả; không thay production preferences/ledger hoặc gỡ package `drivers.test` đã có. Các package mới được dọn riêng sau khi lấy kết quả, nếu ghi ở checkpoint cuối.
- GitNexus CLI 1.6.12 báo baseline index cũ/stale/incomplete; `analyze --index-only` đã tạo lại graph thành công, không sinh instructions. Analyzer cảnh báo flow truncation nên không dùng caller vắng làm bằng chứng không tác động. Có refresh sau thay đổi API; kết quả graph/FTS cụ thể ghi cuối phiên, không repair dữ liệu ngoài phạm vi. Editor Java vẫn báo file không ở classpath; dùng compile/tests Gradle làm thẩm quyền, không sửa IDE/classpath toàn hệ thống.
- Approval upload đầu Phase 3.0 tiêu thụ ở host nhưng terminal trước native (`PERMIT_EXPIRED`, 0 A0), ledger thêm đúng ba records từ 275 lên 278; không đổi cờ consumed hoặc terminal cũ. Người dùng duyệt riêng helper với Bypass tiến trình cho mỗi transaction, không biến thành quyền nới policy toàn hệ thống. Approval tiếp theo là một transaction khác, không phải resume GUID trước.

## 2026-11-06 — Môi trường upload thành công và operational observation

- Hardware transaction độc lập thứ ba được duyệt riêng đã chạy đúng OnePlus GM1900/API 30/boot pin, chỉ nguồn OTG theo xác nhận người dùng. Pin 91% trước lượt thật; không nguồn phụ/backfeed, driver/host swap, WSL/usbipd hoặc recovery nguồn. Firmware local đã có được SAF kiểm hash, không tải/copy lại hoặc bundle APK/Git.
- UID split/controller clock shell và app-private I/O run-as/no-clamp hoạt động thực; heartbeat quan sát tiến và ordinal 43 được thấy khi user tự xác nhận UI. Helper kết thúc một lần, upload 132 native full-length/8192 image/8196 payload; hai reservations trước chỉ ở host, không native mới. Không resend/retry sau upload.
- Sát release có attachment event, observation tự động chưa có lease mới; user duyệt riêng cấp quyền mới/raw read. Raw 50 byte có 0x82/0x81 bulk IN 512, interval 0x82=1; classifier debug cũ quá chặt. Chỉ sửa candidate classifier và lưu raw binary private, không main driver/protocol/I²C/streaming.
- Trong lúc cài test riêng sau observation, TCP ADB cũ OFFLINE; user duyệt và tự khôi phục TCP mới, giữ nguyên OTG/nguồn. Pin lại cùng GM1900/API 30/boot; không reconnect tự động hoặc chuyển mDNS alias để tiếp tục. Final isolated tests app 47/library 37 PASS; gỡ đúng test packages mới, không production.
- Update production APK observation tại chỗ theo approval riêng, hash `12a97e1745c031ec5c80565c6816d00f2e53b50d92aef3a47957b4011f57526e`, so legacy và current-attempt stores vẫn true nguyên nội dung. Raw cuối 00:32:55 +07 OPERATIONAL_CANDIDATE, complete 50 byte/hash 22e2…add5, open/read/close 1/1/1, claim/release/transfer 0/0/0; actual binary lưu ngoài Git và hash/length khớp, không reconstruction.
- Không đổi toolchain: minSdk 21/target 37/Java 8 major 52; full JVM 259/build debug + release/lint PASS, 0 errors/10 warnings, static isolation/fw-not-bundled/zipalign đạt. Runtime API 21/16 KiB và frontend/TS chưa chạy. Giữ GitNexus FTS failure đã ghi, không repair ngoài phạm vi; không dùng graph cuối thay evidence.
- UI diagnostic cuối đã đóng/lease cleanup xong, không helper/capture chạy nền. Canonical ledger 92018 bytes/275 records giữ lịch sử 3 new consumed/2 host fail/1 upload terminal; terminal cũ không sửa theo raw observation sau đó. Legacy attempt 04-11 vẫn consumed/uncertain riêng; không reset/rearm hoặc clear data/ledger. HEAD 92a25b4 cùng working tree, không stage/commit/push/chuyển phase.

## UID-private/no-clamp final software gate — 2026-11-05

- Controller persistent ở shell UID đọc uptime/boot, POLL/PUT/EXPORT dưới run-as đúng package riêng; production vẫn private files, instrumentation chỉ private `.phase29offline`. Không su/root/chmod/SELinux/MANAGE_EXTERNAL_STORAGE hoặc đổi SDK/JDK/NDK/CMake.
- Live ba lượt verified cùng OnePlus/boot, seed legacy files và chờ UI 3500 ms, 132/132 PASS mỗi lượt, USB thật 0; ACK max 626/911/670 ms, total Android 71.721/78.765/80.427 ms. No-clamp sample-after-read/tick thực đã qua mock 61 checks; baseline cùng UID/path đo sau PASS 132/132. Trước clamp và shell/external benchmarks giữ đúng checkpoint, không gộp thống kê.
- Helper vẫn 3 ADB subprocess, run-as/file-child dispatch attempts 281/284/282 không được gọi là tổng OS forks. App regression 46 methods rerun sau UID/no-clamp PASS 9,12 giây; mã Android production vẫn cùng full JVM 257/library 37/service 2 và static gates trước đó.
- Export 34 synthetic evidence files/failed namespaces trước gỡ đúng hai test packages mới; giữ production và drivers.test có trước, legacy consumed so nguyên true. Canonical ledger 3031 bytes/8 records/hash sau hai host reservations không đổi; không cấp GUID/permit/helper thật thứ ba. Source/records còn working tree trên HEAD 92a25b4, chưa stage/commit/push.

## 2026-11-05 — Approval độc lập thứ hai vẫn dừng trước upload

- **Privilege probe mới:** Shell UID đọc `/proc/uptime` exit 0/valid; run-as production app UID trên cùng target/boot exit 1/Permission denied. Không phải ADB disconnect và không đổi permission/SELinux/thiết bị để sửa. Benchmark trước dùng shell UID nên thiếu parity; đang split controller clock/boot và app-file I/O, chưa live PASS.
- **Private UID parity checkpoint:** Ba live verified 132/132 PASS với legacy seed/UI wait 3500 ms, app-private I/O qua run-as và clock shell, ACK max 668/1116/952 ms; không USB/ledger thật. Helper 3 ADB subprocess và 281/282/283 run-as dispatch attempts, không gọi là OS fork count. Source no-clamp timestamp vẫn đang sửa, chưa software-ready cho hardware mới.
- **External I/O probe mới:** run-as offline đọc challenge ở app-external path bị Permission denied dù shell đọc được; live fake UID split cũ dừng trước initial grant sau 30,07 giây. Chuyển instrumentation channel sang app-private `/data/user/0/info.martinmarinov.dvbdriver.phase29offline/files/...`, root đã pwd-verify; không chmod/grant external storage hoặc app data production. Harness private đã build PASS, chờ host wrapper validation và live gates.
- **Fake coverage mới:** Test namespace riêng được seed legacy request/permit, chờ current-run initial grant rồi 3500 ms trước owner. Lượt live đầu FAIL binding ở BenchmarkOnly trước native, evidence giữ; production app/host ledger không đổi. Không chạy helper thật/GUID khác trong các sửa này.

- User phê duyệt transaction khác và current physical conditions/UI-ready riêng. TCP cũ không còn, discovery chỉ đọc thấy target đang online ở cổng mới; pin lại cùng GM1900/API 30/boot, không connect hoặc resume approval cũ. Debug APK mới được update-r, legacy guard nguyên nội dung.
- Permission request lại có dialog; ADB MCP UI dump vẫn exit 137, dùng CLI layout no-idle/selector và ADB tap đúng OK từ cây mới, không đổi setting. Fresh raw 189 byte/FX2_DEFAULT/14 ms, open/read/close 1/1/1, transfer 0; SAF cùng file đã hash/metadata khóa, chưa có vendor write.
- Helper reserve GUID khác sau approval, publish v2 initial permit ordinal 0 nhưng heartbeat vẫn initial timestamp hơn 120 giây khi app foreground. TOTAL_SESSION_DEADLINE 300 giây, app current store/native audit 0, report vẫn raw READ_AND_CLOSED. UI đã đóng, ledger hai reservations giữ nguyên 8 records/hash pin mới; không CPU reset/release, không recovery hoặc rearm.
- Đang sửa/test host heartbeat ở nhánh stale request và mở rộng full-path fake UI-wait 3500 ms có legacy seed. Không thay toolchain, phiên bản app code hoặc timeout; chưa có hardware upload thực trong cả hai approvals của phiên này.

## 2026-11-05 — Preflight thật sau approval, helper fail trước native

- **Sau fix offline:** Host strict binding đã có fixture đúng legacy leftovers production; mock 45 negatives/7 production cases/132 loops PASS, không ADB. Full JVM 257 PASS, app/library rerun 46/37 PASS, baseline + 3 verified full-path 132/132 PASS, ACK max 468 ms/dispatch max 494,749 ms, USB thật 0. Canonical ledger hiện 1773 bytes/5 records của reservation thất bại được giữ; parent so hash với snapshot sau failure không đổi trong fakes.
- **Target sau đổi cổng:** TCP target cũ không còn, discovery thấy đúng thiết bị đã online ở cổng mới cùng mDNS alias; chỉ pin lại GM1900/API 30/boot cho no-USB validation, không reconnect hoặc resume hardware/approval đã consumed. Lệnh đầu đọc guard bằng target cũ thất bại nên so string cho false không phải guard bị đổi; kiểm lại đúng target có exit code/identity thì guard nguyên nội dung true. Không đưa serial/IP/boot ID vào Git.
- **Production APK:** Không cập nhật production sau fix final-release audit; cần update giữ data nếu có transaction được duyệt khác. Test namespaces mới đã export 12 synthetic evidence files rồi gỡ, giữ production/ledger/legacy frames và package drivers.test có trước.

- Người dùng đã duyệt riêng một transaction và physical conditions, rồi UI-ready; debug APK cập nhật tại chỗ bằng `adb install -r`, legacy preferences so trước/sau không đổi true. Không cài/gỡ/clear production bằng test tooling hoặc reset ledger.
- Fresh OnePlus GM1900/API 30/boot pin, pin 100% lúc đầu; tuner đúng một `734c:5530`, nguồn chỉ OTG/no backfeed/no other app theo xác nhận user. ADB MCP UI dump bị exit 137; dùng Android CLI layout/semantic selectors và direct ADB tap tại tọa độ vừa đọc làm fallback, không đổi security/IME/mạng.
- Permission-only lần đầu hết hạn trong lúc chờ dialog, user-grant UI xong chạy preflight permission-only mới đạt ĐÃ CẤP/open 0. Fresh raw read-only đạt 189 byte/FX2_DEFAULT, open/read/close 1/1/1, transfer 0, worker đúng, duration 22 ms. Đây là phép đọc mới trước upload, không thay report recovery cũ thành operational.
- File firmware đã có trên điện thoại đúng 8192 byte/SHA khóa; dùng SAF, không tải/copy image mới. IME đã làm sai URL khi gõ key events; đọc lại UI rồi dùng CLI setText đúng URL/entry, không sửa IME hoặc metadata validation.
- Helper đã reserve một GUID mới sau approval nhưng gặp legacy request thiếu protocol/run epoch trước initial permit mới; ACK/observation 0/0, current app attempt prefs/native audit 0. Ledger giữ NEW_APPROVAL_CONSUMED/failure, không xóa file hoặc rearm. UI đã đóng, chưa có firmware/vendor write, CPU reset/release hoặc power recovery mới; phần cứng vẫn chưa bring-up thành công.

## 2026-11-05 — Phase 2.9 tiếp tục từ 92a25b4, kiểm thử cách ly và kênh ADB gián đoạn

- **SOURCE-CONFIRMED:** Đầu phiên HEAD, origin tracking và `git ls-remote origin refs/heads/chore/modernize-android-build` cùng `92a25b4962ff487c169e67649f454e277bcc60e6`; working tree sạch, không checkout/reset/rebase/stage/commit/push hoặc thay reference trees.
- **OFFLINE-TESTED:** Đọc lại build/wrapper và SDK `source.properties`: giữ AGP 9.5.0, Gradle 9.8.2/checksum, JDK 25.0.5.0.3, compileSdk 37.2 revision 2, target 37/min 21, Build Tools 37.0.0, NDK 30.0.16248370, CMake 4.1.2 và Java source/target 8. Không nâng/cài/gỡ toolchain, sửa environment hệ thống hoặc tự chuyển emulator.
- **RUNTIME-OBSERVED:** Đúng OnePlus GM1900/API 30/USB Host qua một TCP Wireless ADB target và alias mDNS cùng máy; boot identity pin ngoài Git. App/test `.phase29offline` được kiểm aapt trước install, library instrumentation tự target `drivers.test`, service smoke tự target `dvbservice.test`; không dùng UTP vào production.
- **RUNTIME-OBSERVED:** Production `phase29-attempt` được so nội dung trước/sau các suite, giữ nguyên `consumed=true`. Không cài lại, uninstall hoặc clear package production, chọn firmware SAF, tạo hardware permit/GUID, mở connection thật hoặc gửi vendor/I²C/reset/release.
- **RUNTIME-OBSERVED có giới hạn:** Lượt full-path đầu dừng trước handshake vì test chưa gọi `getExternalFilesDir()` để khởi tạo vùng scoped storage; helper chờ hết 240 giây, 0 ACK và 0 USB. Sau đó Wireless ADB OFFLINE được xác nhận một lần; không reconnect tự động. Người dùng chọn tự khôi phục ADB; target TCP mới được kiểm lại cùng model/API và boot identity, production guard vẫn không đổi. Không đổi nguồn/OTG hoặc mạng bằng lệnh agent.
- **OFFLINE-TESTED:** Sửa test để dùng `getExternalFilesDir()` và kiểm canonical path chỉ đúng `.phase29offline`; không cấp MANAGE_EXTERNAL_STORAGE, sửa appops hoặc đụng Downloads/production storage. Fault không host sau sửa dừng sau 30,067 giây với `Initial host approval missing`, `usb_calls=0`, chưa chạy runner/native giả.
- **Raw evidence cuối:** Runner debug thêm hai file raw app-private trước upload/sau observation, không đưa bytes vào parser/report/Git, không thêm USB read. Full JVM mới 256 PASS, app/library 46/37 rerun PASS; baseline và ba lượt mã cuối 132/132 PASS, ACK max 490 ms/dispatch max 513,080 ms, raw fixture files/reads 2/2 và USB thật 0. Bằng chứng chi tiết tại RESEARCH/TEST-MATRIX; các con số marker trước đó giữ đúng checkpoint.
- **Bổ sung marker cuối:** Test đo riêng request/observation storage completion; helper chỉ thêm timestamp sau temp-write/rename trong cùng frame, không ghi permit lần hai hoặc đổi ACK deadline. Marker không chứng minh fsync-durable của permit, scope ghi rõ `NO_EXPLICIT_FSYNC`/`NOT_MEASURED`; canonical ledger và native audit vẫn có sync riêng. Baseline và ba lượt mã cuối 132/132 PASS, ACK max 525 ms; giữ riêng các lượt đầu, không xóa evidence.
- **Cleanup sau kiểm thử:** Đã xuất JSONL, hai namespace test lỗi và bốn file audit của native giả ra ngoài Git rồi gỡ đúng ba test packages mới `.phase29offline.test`, `.phase29offline`, `dvbservice.test`. Giữ production và `drivers.test` có trước; so guard sau cleanup vẫn không đổi. Không còn benchmark chạy nền, không thay tuner/nguồn.
- **GitNexus cuối FAILED:** Sau khi marker/tests/docs đã chốt, `analyze --index-only` lỗi FTS `file_fts` inconsistent/COPY. Không drop/recreate/force/repair hoặc coi status theo HEAD là đủ cho graph cuối; dùng source/diff/fingerprints/tests làm bằng chứng. Lỗi index này không phải lỗi build/USB và không cấp phép đổi environment.
- **Giới hạn công cụ:** GitNexus CLI không nhận `status --json`; `status` và `analyze --index-only` đã chạy thành công đầu phiên và refresh sau thay đổi, không sinh instruction files. Editor vẫn báo debug/androidTest ngoài classpath; Gradle compile/tests là bằng chứng, không sửa cấu hình IDE để che cảnh báo. Host scripts được kiểm bằng PowerShell 7; không coi parser Windows PowerShell 5.1 với UTF-8 không BOM là PASS.

## 2026-11-05 — Phase 2.9: môi trường kiểm cuối, không hardware attempt mới

- Toolchain không đổi trong phiên. Full JVM cuối 253 methods PASS; debug/release/lint
  PASS, 0 errors/10 warnings. Output app được tái tạo riêng để bỏ class harness mồ côi
  do lần compile trước khi chuyển source ra tools; không xóa SDK/caches/source/app data.
- `tools/Build-TBS5530-OfflineTestApp.ps1` chỉ build applicationId `.phase29offline`
  và test package riêng, kiểm bằng aapt; không install/deploy/ADB trong script đó.
  Instrumentation thực chạy 22 methods app và 7 methods library trên OnePlus, manager/raw giả.
  Production preferences so trước/sau giữ nguyên consumed=true.
- Harness benchmark dùng app_process dưới shell, namespace riêng trong Downloads,
  không Context/UsbDeviceConnection/production app data. Đủ 132 observations PASS;
  ledger mẫu ngoài Git giữ cả các lượt lỗi trước. Namespace lượt thành công đã xóa sau export.
  Không chuyển target sang AVD hoặc chạy test USB thật.
- Canonical host ledger đã enroll prior CONSUMED/UNCERTAIN audit, không cấp new approval
  hoặc sửa app state. Không đưa ledger, permit, firmware, APK hoặc logs vào Git.
- Bản debug sửa trong working tree chưa cài vào package production. Tuner/nguồn/permission
  cần preflight mới trước mọi đề nghị upload; sự hiện diện USB không chứng minh firmware.
- Helper/parser cuối đã chạy live không USB đủ 132 vòng: p50 359/p95 506/p99 714/max 759 ms.
  Sau export đã dọn namespace test; cả hai package `.phase29offline` mới đã gỡ, không gỡ
  package production hoặc `drivers.test` có trước. Production consumed/preferences không đổi.
- GitNexus refresh cuối FAILED do FTS `file_fts` inconsistent/COPY. Không force/drop/repair;
  graph cuối không được dùng làm chứng cứ an toàn. Không thay environment vì lỗi index này.

## 2026-11-05 — Phase 2.9: giữ toolchain và bảo toàn ứng dụng đã consumed

- Git HEAD thực `d76bbfb63dd21d55af6ce27fdff0543eb500db72`, nhánh
  `chore/modernize-android-build`; một worktree, sạch trước sửa. Không commit/push,
  fetch/sync, đổi branch hoặc sửa ba reference checkouts.
- Đọc lại build/wrapper và SDK: AGP 9.5.0, Gradle 9.8.2 với distribution checksum đã khai báo;
  JDK trên PATH 25.0.5.0.3, compileSdk 37.2 revision 2, Build-Tools 37.0.0,
  NDK 30.0.16248370, CMake 4.1.2. Giữ minSdk 21/target 37/Java source-target 8.
  Không khảo sát hoặc tuyên bố latest stable; không cài/gỡ/nâng công cụ.
- OnePlus GM1900/API 30 có đúng một TCP serial online và alias mDNS cùng thiết bị.
  Kiểm identity và USB Host trước tác vụ; monitor chỉ đọc mỗi 10 giây trong cửa sổ kiểm thử.
  `dumpsys usb` xác nhận tuner `734c:5530` trên Android, không mở connection hoặc transfer.
  Preferences production `phase29-attempt` vẫn `consumed=true`; report raw trên app
  là checkpoint recovery cũ, không phải đọc descriptor mới ngày 05-11.
- Ảnh firmware đã có ngoài Git được đọc lại: 8192 byte, SHA-256
  `b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc`.
  Không copy/deploy/upload ảnh trong phiên này. Không capture, USB ownership/driver/nguồn,
  permission grant, vendor/I²C, reset hoặc release CPU.
- AVD hiện có `TBS5530_Runtime_API37`, x86_64/google_apis, image android-37.0;
  chỉ đọc cấu hình, không boot hoặc chuyển target từ OnePlus sang emulator.
- VS Code `runTask` không resolve label assembleDebug dù task tồn tại; dùng Gradle Wrapper
  trực tiếp. Editor báo các debug Java files ngoài classpath; kết quả compile/tests thật
  được ghi riêng, không sửa global Java settings để che diagnostics.
- GitNexus CLI không nhận `status --json`; dùng `status` sau đó refresh `analyze --index-only`.
  Đầu phiên status cho đúng HEAD đã index; graph chỉ dùng điều hướng, không thay source/tests.
  Agent explore readiness không khởi động được do `400 model not supported`; parent đọc
  ba nguồn trực tiếp, không lặp agent hoặc tác động references.


## 2026-11-04 — Phase 2.9: tuner trở lại OnePlus, attempt đã tiêu thụ — 22:51 +07

- Người dùng duyệt riêng chuyển host, nguồn chỉ OTG/không backfeed và một lượt firmware;
  tự nối TBS5530 vào OnePlus, cấp quyền, chọn file local qua SAF và xác nhận UI.
  Wireless ADB đúng OnePlus/GM1900/API 30 vẫn online; không đổi toolchain/driver hoặc usbipd.
- File firmware đã kiểm được chép riêng vào Downloads trên điện thoại qua Wireless ADB,
  không assets/APK/Git và không upload từ PC. Lượt đầu source URL có trailing space bị
  từ chối trước open/consume; sửa metadata rồi chọn lại đạt size/hash.
- Android attempt UNCERTAIN sau 23 native writes full-length; guard bước 24 chưa tới API.
  Không CPU release/corrective writes. Người dùng duyệt recovery riêng rồi tự rút/cắm một
  lần trên OnePlus, không nguồn phụ và không chuyển Windows.
- Sau recovery: permission/owner mới đọc raw 189 byte/default MATCH thiếu endpoint `0x82`, 14 ms,
  open/read/close 1/1/1, không claim/transfer. Tuner hiện trên OnePlus; Windows không có PnP
  TBS. App diagnostic mới idle, attempt consumed vẫn true. Không build/test instrumentation
  sau hardware để không reinstall/xóa guard; không cấp permit mới.
- Report attempt và raw recovery được lưu ngoài Git dưới dạng JSON chỉ metadata đã lọc.
  Không lưu serial/IP/firmware payload; helper đã dừng cấp acknowledgment ở terminal state.
  Mọi thử nghiệm mới bị chặn cho tới khi phân tích offline guard và có phê duyệt mới.

## 2026-11-04 — Phase 2.8: capture đã hoàn tất, tuner ở Windows — 18:38 +07

- Hai lượt capture được phê duyệt riêng: 30 giây đầu không có packets vì reconnect muộn;
  180 giây/8 MiB sau có 3028 packets, 232084 byte, chỉ bus 3/address 4 và không frame bị cắt.
  Capture ngoài Git; không cài/đổi filter/driver/registry. Người dùng tự reconnect đúng cổng USB 2.0,
  không nguồn phụ theo xác nhận. BDA 2.0.0.2 tự phát hai lượt dual-reset và vendor init;
  không có lệnh USB do agent gửi, không coi đó là Android upload.
- TShark đã dừng ghi; wrapper pipeline exit 1 ở deadline vì worker elevated không tự thoát.
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

- Xác minh mới GM1900/API 30/state=device qua một serial TCP tường minh; bỏ alias mDNS. Một lần serial TCP cũ biến mất trong lúc sửa diagnostic: dừng ngay trước build/test/USB, chờ người dùng xác nhận, tra cổng mới và cold launch owner mới. USB Host=true, attachment 734c:5530, permission=false ban đầu, generation 0/pending=false/open 0. Không pair/reconnect tự động hoặc chuyển thiết bị.
- Toolchain đang khai báo/đang cài được kiểm trực tiếp: Gradle 9.8.2/checksum wrapper giữ nguyên, AGP 9.5.0, Java 25.0.5.0.3, platform 37.2 revision 2, Build-Tools 37.0.0, NDK 30.0.16248370, CMake 4.1.2, ADB 37.0.1. Compile 37.2/target 37/min 21/Java 8 giữ nguyên; runtime OnePlus API 30/arm64. Không kiểm latest stable vì không có tác vụ nâng cấp; không install/uninstall hoặc đổi môi trường Machine/User. Lệnh build chỉ dùng môi trường tiến trình con.
- Chu kỳ raw được người dùng duyệt riêng: permission callback thật có 1 lần/match=true, sau đó raw operation 2/generation 2, open/getRawDescriptors/close 1 lần, 13 ms, trên worker; không claim/transfer. Tree 189 bytes, USB bcd 0200/device bcd 0001, config value 1/wTotalLength 171/bNumInterfaces 1, alt 0–3; không 82. Công suất khai báo 100 mA không phải đo nguồn; packet 512 không đo negotiated speed.
- Metadata và raw report đã lọc nằm app-private/session, không có raw bytes, USB serial/path hoặc credentials trong Git. APK/log/cache local không phải deliverable công khai. Kiểm permission cuối sau instrumentation/cài lại được ghi ở WORKLOG; không mặc định quyền giữ qua UTP install/uninstall.
- Không thực thi TBS ChangeMode; ZIP chỉ đọc trong RAM và inspect chuỗi tĩnh, không EXE trên đĩa/không driver Windows. TBS/WSL/Linux snapshots giữ riêng provenance; không chuyển tuner khỏi OnePlus.

## 2026-11-03 — Phase 2.5: OnePlus 7 thật qua Wireless ADB

- Thiết bị xác minh trực tiếp: **OnePlus GM1900 (OnePlus 7), Android 11/API 30**; build `GM1900_14_220617`, OTA `OnePlus7Oxygen_14.P.45_GLO_0450_2206171433`. Người dùng cung cấp OxygenOS 11.0.9.1.GM57AA; thuộc tính ro.oxygen.version trống nên không gọi đây là phiên bản vừa đo bằng thuộc tính hệ thống.
- Wireless ADB (ADB không dây) ban đầu offline: dừng ngay. Sau khi người dùng nối lại và xác nhận, tra serial TCP/port mới rồi xác minh manufacturer/model/API/state trước khi tiếp tục. Có alias mDNS của cùng thiết bị; toàn bộ lệnh chọn đúng một serial TCP, không dùng emulator. Không lưu IP/port/thông tin xác thực ghép nối vào mã nguồn hoặc hồ sơ công khai. Build dài có kiểm tra kết nối mỗi 10 s; không đổi ADB tcpip, không ghép nối lại hoặc tự chuyển thiết bị đích.
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
