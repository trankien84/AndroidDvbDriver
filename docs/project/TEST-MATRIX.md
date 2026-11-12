# TBS5530 test matrix

## 2026-11-11, phiên 22:18 — Recovery apply PASS; B1 MANUAL0 TS FAIL với cleanup PASS

| Kiểm mới | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Host recovery guards cuối | PASS 37 | Exact grant/hash, clock shellUID, no-overwrite/no hardware, launch correlation |
| Emulator coordinator/journal/file/fence | PASS 11/11 | Gồm terminal refusal→fresh token→duplicate-token gate; synthetic cache/no production store |
| App JVM/build/lint/release | PASS 74 và build gates | Wrapper offline, không nâng toolchain; release không recovery Activity/Permit/Journal |
| Recovery attempt 1 | REFUSED_BEFORE_APPEND | Heartbeat 0 do appUID uptime denied; consumed=false/ledger unchanged; sửa helper, duyệt hash mới |
| Recovery attempt 2 | REFUSED_PRE_GRANT | Challenge cũ do Activity reuse; journal chưa consumed, không append; sửa lifecycle và duyệt exact build |
| Recovery callback-fixed | PASS thực trên Samsung | 7→9 records, 581→747 byte, prefix 581 byte-exact, candidate SHA fe9ab4fc..., journal consumed verified; không full-history proof |
| B1 no-I/O permission timeout | NOT_RUN tuner stage | Terminal riêng/drain confirmed, EP0=0/firmware=null/native=null; không coi là MANUAL0 failure |
| Permission-only OS preflight | PASS thực | User bỏ Always/chọn OK, app GRANTED/OS_PERMISSION_VERIFIED trước I/O; không secure dialog bypass |
| B1 bootstrap | PASS firmware transfer | Một upload 132 full-length controls, image 8192/payload 8196, 3545 ms; natural re-enumeration, không double A0 |
| B1 operational handoff | PASS điều kiện/NO_A0 | User duyệt cùng epoch/permission mới, không FirmwarePath/0 A0, fresh owner/High-Speed/maxpacket 512 |
| B1 MANUAL0 tune/lock | PASS lock, actual PLP UNKNOWN | Bank23 AF00/AD01, sync 26, drive 54/54; không readback PLP/L1 hoặc TS pin proof |
| B1 EP0 plan | PASS 191/191 | Controls full-length, một stream 8A0A00; AUTO lịch sử 190, alt0/readback OFF |
| B1 Bulk/TS | FAIL dữ liệu, completion biết rõ | 8×4096 SUBMIT/REAP, syscall=0/errno=0 nhưng 8×status -75/actual=0; payload 0, TS_SMOKE_PASS=false |
| B1 cleanup | PASS | REPORT/USB_RECEIVE_FAILURE, DRAIN_CONFIRMED, outstanding=0/quarantine=false, app USB fd=0 |
| Evidence integrity | PASS 20 files | Hash Android/host operational/native khớp; bootstrap raw hash và machine assertions riêng |
| History hậu bootstrap | PASS append-only | 9→12/747→996 byte, prefix 747 nguyên, witness SHA b6b5935a... app/host match |
| Physical removal | USER_CONFIRMED sau drain | ADB devices 0 sau rút; không ghi fresh host-empty PASS |
| DRIVE55/custom firmware/VM mới | NOT_RUN / safety BLOCKED | Không dùng quyền B1 để mở nhánh khác; root cause unresolved |

Raw/hashes/full commands ở bundle `phase31-recovery-coordinator-20261111-222119-cd9dcedf` trong D:\TSDuck\reports/logs. Không ghép clock Java elapsedRealtime với native monotonic, không dùng zero actual thành zero wire bytes. Manual0 đơn lẻ không đủ; không sửa các kết quả lịch sử bên dưới.

## 2026-11-11 — Recovery/fence build cuối, không ghi tablet

| Kiểm mới trên source recovery/fence | Kết quả | Giới hạn |
| --- | --- | --- |
| Node recovery + decoder | PASS 29/29 | Tạo proposal và refusal overwrite; không app I/O |
| Driver JVM TBS5530 | PASS 456/456 | Gồm 7 recovery/fence cases, real candidate fixture và các regression cũ; không skip |
| App JVM TBS5530 | PASS 74/74 | Guard/lifecycle regression thuần JVM |
| Emulator final latch build | PASS 24/24 | 3 experiment/fence grants + 19 legacy grants + 2 file append/fsync tests; store/cache synthetic |
| Cold emulator suite đầu | FAIL 1/22, giữ lịch sử | Legacy eligible assertion; targeted 4 và full 22 rồi final 24 PASS, không nới heartbeat, cause UNKNOWN |
| Helper frontend/history | PASS 23 checks | Missing hash trước ADB, host witness/grant checks, EP0 export trước final mismatch refusal |
| Helper legacy/launch | PASS suite và 57 checks | Offline child process theo quyền có trước, không live helper |
| Build debug/release/AndroidTest/lint | PASS | Wrapper offline/toolchain cũ; recovery source/debug-only |
| Release class isolation | PASS | apkanalyzer không thấy HistoryRecovery/HistoryFence/FrontendExperiment/AutonomousSession |
| Samsung hậu kiểm 22:07 | PASS chỉ đọc | APK cũ/hash bbbf25cc..., history cũ e398459c..., host USB rỗng, không new APK install/apply |
| Apply coordinator/UI/journal | NOT_IMPLEMENTED_LIVE | Core callback không thay thế caller/grant/journal production; cần rà riêng trước apply |
| Power failure / Samsung write / B1 | NOT_RUN | Không quyền ghi tablet hoặc tuner; TS_SMOKE_PASS=false |

Logs/fixtures/hashes mới trong bundle `phase31-history-recovery-20261111-210818-73007003`. Build cuối source main không đổi; APK/helper debug mới cần freeze và phê duyệt khác card B1 trước. Không suy tests giả thành hardware hoặc full-history PASS.

## 2026-11-11 21:05 — Recovery offline và kiểm tablet chỉ đọc

| Phép kiểm mới | Kết quả | Giới hạn |
| --- | --- | --- |
| Tìm nội dung local ledger | PASS thực thi | 3.203 text files, chỉ backup 7 dòng đã biết; không chứng nhận mọi nơi trên máy |
| ZIP text search | PASS thực thi | 313 text entries trong 5 ZIP, không full/embedded ledger mới, không giải nén hoặc sửa raw |
| Samsung read-only inventory | PASS | SM-X926B/API36/boot 47, host rỗng; base 581 byte, marker initialized; `.bak`/`.new`/marker `.bak` ABSENT |
| Ledger export integrity | PASS | Remote/local SHA khớp baseline e398459c..., không app launch/write hoặc restore |
| Recovery unit tests | PASS 8/8 | Prefix byte-exact, deny-set monotonic, malformed/hash/epoch/host/native counters, duplicate evidence |
| Decoder regression | PASS 21/21 | Cùng runner Node, tổng 29 PASS/0 FAIL/0 SKIP |
| Real-evidence candidate | PASS offline / NOT_APPLIED | 7→9 records, 581→747 byte, SHA fe9ab4fc...; liveGateBlocked=true |
| CLI output tồn tại | PASS refusal / exit 2 | Không overwrite candidate hoặc tái sử dụng directory |
| Proposal đầu | FAIL exit 2, đã sửa verifier | Source/raw dùng exception=NONE, fixture đầu nhầm null; không sửa raw để PASS |
| Editor Node diagnostics | Không lỗi được báo | Không thay bằng chứng tests hoặc hardware |
| JVM/Gradle/instrumentation | NOT_RUN mới | Không đổi runtime, không cài/gỡ app hoặc nâng toolchain |
| Ledger apply / B1 / tuner I/O | NOT_RUN | Chưa phê duyệt ghi app hoặc mở history gate; ROOT_CAUSE_UNRESOLVED/TS_SMOKE_PASS=false |

Bundle `phase31-history-recovery-20261111-210818-73007003` dưới D:\TSDuck\reports có exact commands/stdout/exit, scan coverage và recovery plan. Candidate/input riêng ở logs, không Git. Các PASS phần mềm/hardware cũ giữ nguyên đúng phiên, không tính lại thành PASS mới.

## 2026-11-11 — Kết quả cuối gate lịch sử: B1 không được chạy

| Kiểm sau Samsung preflight | Kết quả | Giới hạn / hành động |
| --- | --- | --- |
| Audit continuity 32 input files | PASS thực thi / continuity UNPROVEN | Hai expected continuation 09/11 MATCH dòng 4/6; Sony/lifecycle 10/11 ABSENT |
| Snapshot history hiện tại | FORMAT_VALID, không chứng nhận đầy đủ | 7 records/581 byte, marker initialized, remote/local hash khớp; backup chỉ đọc |
| Native firmware/tune/Bulk B1 | NOT_RUN_HISTORY_CONTINUITY_UNPROVEN | Grant B1 có điều kiện chưa phát hành, không consumed attempt mới |
| Reset/restore/recreate/rearm history | NOT_RUN | Không chép keys hoặc mở lại grants cũ để vượt gate |
| Samsung lifecycle giữ APK | PASS 5/5 | APK còn/hash đúng sau test; kết quả emulator 4 FAIL lịch sử vẫn giữ |
| Helper control-plane | PASS | NO_RESOURCES_ACQUIRED, firmware/native URB null, mirror=0/TS=0; không tuner PASS |

Chưa xác định vì sao keys thiếu hoặc dữ liệu app có từng mất/restore hay không. `pm path` vắng sau runner không là proof data loss; không có pre-test full-ledger snapshot. Cần recovery plan riêng hoặc bản ledger đầy đủ có provenance trước hardware. Evidence/report trong `phase31-android-first-20261111-172352-0a98eee3` dưới D:\TSDuck; không TS_SMOKE_PASS mới.

## 2026-11-11 — Samsung không tuner: lifecycle và control-plane đã PASS

Sau quyền người dùng cho preflight và một B1 có điều kiện, đã xác minh Samsung SM-X926B/API36, boot 47, hai ADB alias của cùng tablet và current USB host rỗng. Không dùng filter khai báo VID/PID trong dumpsys làm bằng chứng tuner đang gắn. Chưa phát grant B1 hoặc chạy firmware/tune/Bulk mới.

| Kiểm mới trên thiết bị thật | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Lifecycle lần đầu | PASS 5/5 | Đúng Samsung; khác kết quả emulator ngoài allowlist. Không tuner |
| APK sau runner lần đầu | ABSENT | `pm path` không thấy app; chưa suy app-data bị xóa. Đã báo và xin quyết định trước B1 |
| Lifecycle giữ APK, được duyệt riêng | PASS 5/5 | `leaveApksInstalledAfterRun=true`, `uninstallIncompatibleApks=false`; APK còn và hash khớp build đã khóa |
| Helper control-plane 30 giây | PASS / exit 0 | CONTROL_PLANE_COMPLETE, terminal sạch, NO_RESOURCES_ACQUIRED, firmware/native URB null, mirror=0/TS=0 |
| History sau reinstall | PRESENT / FORMAT_VALID | Marker initialized=true,7 records/581 byte, remote/local SHA khớp; chỉ backup/read, không restore/reset/rearm |
| Continuity old epoch | ĐANG KIỂM | Cần đối chiếu khóa cũ, không coi file tồn tại tự nó là proof đủ |
| B1 MANUAL0 hardware | NOT_RUN | Chờ continuity và fresh physical attach được xác nhận; grant có điều kiện chưa bị dùng |

**Quy tắc chạy physical instrumentation:** Với AGP 9.5.0 hiện tại, thêm `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.experimental.testOptions.uninstallIncompatibleApks=false`; kiểm APK hash và ledger trước/sau. Không tự clear/uninstall hoặc dựng lại history để chạy test. Raw riêng tại D:\TSDuck\logs\phase31-android-first-20261111-172352-0a98eee3, summaries ở bundle reports cùng run ID. Xem báo cáo `physical-preflight.md`; các kết quả emulator/offline trước giữ nguyên bên dưới.

## 2026-11-11 — Android-first: phần mềm MANUAL0 và dữ liệu offline, chưa live tuner

Branch `chore/modernize-android-build`, HEAD nền `8abf57c32bd75cf03508f6db62e03a0825e67868` cùng edits chưa commit. Bundle `phase31-android-first-20261111-172352-0a98eee3` dưới D:\TSDuck\reports giữ logs, XML và hashes; mọi PASS dưới đây là lượt mới đúng phạm vi, không sửa lịch sử.

| Phép kiểm | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Gate A/D input audit | PASS offline | 119 inputs; 74 khớp hash cũ, 45 chỉ tự hash, 0 mismatch; tách ba host và clocks |
| Gate A/D regressions | PASS 60/60 | Comparison 20 + decoder 21 + capture 11 + layered 8; không hardware |
| Driver JVM TBS5530 | PASS 449/449 | Gồm 5 tests experiment và 1 private Windows fixture mới; no skip/failure |
| App JVM TBS5530 | PASS 74/74 | Launch/capture/permission và related regression; không hardware |
| App instrumentation cuối | PASS 21/21 | API 37 x86_64 emulator: 2 experiment grant + 19 legacy approval; không mở USB |
| Driver instrumentation | PASS 13/13 | OperationalHandoff, BootstrapIsolation, ExplicitInterface; USB/manager giả |
| Activity lifecycle trên emulator | FAIL 4/5 | Session constructor từ chối model emulator ngoài allowlist; giữ XML/log failure, không mở rộng host hoặc gọi test PASS |
| Lifecycle/control-plane trên Samsung | NOT_RUN | Không physical ADB device cuối lượt; là chốt bắt buộc trước live B1 |
| Helper frontend guards | PASS 17 | Default AUTO, cấm ghép biến, thiếu golden/APK hash từ chối trước ADB |
| Helper legacy/launch guards | PASS | Existing autonomous suite và 57 launch/control-plane checks; script chỉ chạy offline trong child process được duyệt |
| Native C fault injection | PASS | GCC 16.2.0 sẵn có, `-std=c11 -Wall -Wextra -Werror`; core thật 8×4096 và 8×8192, không silicon |
| ASan | NOT_RUN | Không có runtime ASan được compiler resolve; không ghi PASS giả |
| Debug/release/instrumentation compile/lint | PASS | Gradle Wrapper offline, debug APK và release unsigned; source 8/JDK 25 warnings giữ nguyên |
| Release isolation | PASS | `apkanalyzer dex packages --defined-only` không có FrontendExperiment/Autonomous/ControlProbe/NativeUsbfsUrb; minSdk 21 |
| Native ABI/alignment | PASS tĩnh | APK có armeabi-v7a/arm64-v8a/x86/x86_64; 64-bit LOAD align 0x4000; không physical native-load mới |
| Windows TS parser fixture | PASS offline | Hash đúng; 30897800 byte, 164350 packets, 3054 TEI, 1705 CC, 24 PAT/373 PMT, 14 programs có PMT; không Android TS |
| Windows fixture assertion đầu | FAIL đã sửa test có căn cứ | Nhầm zero sync loss với zero malformed; kiểm header độc lập có 115 basic invalid headers, parser 139; giữ XML failure |
| FX2 host pattern mới | PASS 30/30 | Synthetic marker/sequence 512/CRC/cap; không firmware hoặc RF |
| Existing FX2 regressions | PASS 51, SKIP 13 | Thiếu private fixture được ghi SKIP đúng; không coi skip là PASS |
| DRIVE55 proposal | PASS offline / live BLOCKED | Đúng hai literal 54→55, zero controls thêm; BOARD_MAPPING_UNKNOWN |
| Android B1 MANUAL0 / C1 DRIVE55 | NOT_RUN / BLOCKED_FOR_SAFETY | Không fresh attachment, không live grant hoặc vendor/I²C writes mới |
| Custom FX2 firmware / Android TS smoke | NOT_RUN / false | Không upload firmware tùy biến, không EP82 data mới; ROOT_CAUSE_UNRESOLVED |

Lần Gradle regression đầu FAIL do wildcard `*Tbs5530*` bị shell mở rộng thành tên workspace. Đã đổi selector thành argument đầy đủ `--tests=info.martinmarinov.*Tbs5530*`, không sửa assertion để che failure. VS Code test discovery không match source set debug nên dùng runner Gradle của repo. Android CLI không thấy AVD dù native emulator liệt kê được; fallback executable đã kiểm help, emulator tạm chạy rồi được dừng đúng instance. Không cài package hoặc sửa PATH/AVD.

## 2026-11-11, phiên 10:11 — Đối chiếu offline BDA/VM từ raw cũ

Bundle `phase31-bda-vm-offline-20261111-101351-3dad820b`, branch/HEAD giữ `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`. Không suy tests hoặc capture lịch sử thành hardware PASS mới.

| Phép kiểm mới | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Input integrity | PASS | Hash ba pcap và TS BDA cùng phiên khớp; raw không sửa |
| Comparison tests | PASS 20/20 | Completion boundary, bank-select, stale-read, tuner exact address, monitor windows, requested UNKNOWN |
| Existing decoder/capture regressions | PASS 32/32 | 21 decoder và 11 raw USBPcap tests, không hardware |
| TShark 4.6.9 boundary | PASS / exit 0 | Kiểm độc lập descriptor/URB function/first Bulk ở BDA và VM |
| TSDuck 3.45-4798 offline | PASS thực thi | TS BDA 164350 packets, 14 services, 47 PIDs; 3054 TEI và 0 invalid sync, không gọi sạch |
| Firmware comparison | SAME IMAGE / DIFFERENT EPOCH COUNT | BDA hai lần đủ 132, VM cold một lần; không replay double A0 |
| Descriptor/config/alt | MATCH OBSERVED | Cùng operational hash, config=1/interface=0/alt=0/EP82/max packet=512 |
| Pipe-prep hypothesis | NOT SUPPORTED BY RECORDED SCOPE | Không thấy SET_INTERFACE/CLEAR_HALT/reset-pipe trước Bulk; BDA ABORT_PIPE ở teardown, internal pipe state vẫn UNKNOWN |
| Tuner init/tune | BYTE-EXACT 25/11 | Thứ tự/payload gồm reads và writes khớp giữa hai lượt |
| PLP/output | MATCH PROGRAMMED VALUES | Cùng manual PLP0 và output chính; không physical snapshot hoặc actual PLP mới |
| Drive 96/97 | DIFFERENT / CAUSE UNKNOWN | BDA 55/55, VM 54/54; không đổi production từ writes khác |
| RSSI interference | RUNTIME/SOURCE-CONFIRMED | VM ghi tuner 0x88=04 và monitor ngang Bulk; source bốn references xác nhận đường ghi |
| RSSI counter-evidence | VERIFIED OFFLINE | BDA ba RSSI windows vẫn có payload; Android không ghi 0x88=04 vẫn -75 |
| First Bulk | DIFFERENT OBSERVED RESULT | BDA 8192 byte/status=0/2,291 ms; VM BABBLE/0 byte/0,258 ms; requested ở pcap UNKNOWN |
| Root cause / production fix | UNRESOLVED / NOT APPLIED | Chỉ đề xuất kế hoạch cô lập monitor M có fake guards, chưa live variant |
| USB/SSH/VM/capture/firmware | NOT RUN mới | Chỉ đọc file cũ; không cần cắm tuner |
| JVM/Android/build/lint | NOT RUN | Không sửa runtime hoặc toolchain, giữ kết quả lịch sử đúng phiên |

## 2026-11-11 01:46 — Phép đo nhiều lớp đã ghi, TS vẫn 0 byte

Run `phase31-layered-20261111-014626-5c93af62`, branch/HEAD `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`. Report, source manifest, raw và hashes giữ ngoài Git dưới D:\TSDuck. Không gọi FINISHED hoặc cleanup=true là TS_PASS.

| Phép kiểm | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Guest full Linux fake cuối | PASS 58/58 | Exit 0, 156 giây, không process leak; giữ các failure trước theo đúng hash/phiên |
| Grant/coordinator cuối | PASS 89 | Startup-only từ chối runtime, local notice chỉ sau ARMED, không rearm/ghi đè terminal |
| Process integration | PASS 19 | Fixture và stdin EOF; không hardware |
| Live-adapter contract | PASS 32 | Identity/health/source/active-channel/notice ordering; không chạy live trong test |
| Persistent-channel checks | PASS 61 | Sequence, split frame, deadline, EOF/STOP, không đọc song song sau fault |
| Runtime guard/atomic terminal | PASS 17 | Git Bash và Linux fake; module M không đổi |
| Parsers | PASS 19 tests | Owner/pairing/requested/actual/truncation; không suy zero thành FIFO-empty |
| Windows worker | PASS trước live | 192 checks/25 processes và 6 startup workers dưới tải; không rerun source không đổi |
| Helper live | RECORDED / exit 0 | 104567 ms, cold và operational kết thúc; không là TS certification |
| Firmware cold | VERIFIED 132/132 | 8192 byte, đúng image hash/order/full lengths, một bootstrap; không vendor ngoài A0 trong cold |
| Operational handoff | VERIFIED | Descriptor mới cùng epoch, firmware staging đã gỡ, A0 count=0 |
| K36 lock | OBSERVED | Receiver status=31/locked=true, Sony sync=0x26; manual PLP0, không phải Android AUTO |
| Receive/TS | FAIL nhận dữ liệu | receive_timeout/result=1, sample=0 byte; không có TS để đo TEI/PCR/continuity |
| Windows Bulk | OBSERVED host fault | 36 completions: 1 BABBLE_DETECTED và 35 CANCELED, actual đều 0; requested UNKNOWN |
| Guest usbmon | OBSERVED cancellation | 8 submit × 65536 byte, 8 complete -2/actual=0; không là host status -75 |
| Raw xHCI | OBSERVED 8/8 | Enqueue/giveback đủ cặp, length=65536/actual=0; raw -115 là field trước final status |
| EP0 cross-layer | BYTE-EXACT 706/706 | Setup/payload/order khớp, mọi status=0; một 8A/0A00, còn 335 controls sau host fault |
| Coverage review | LIMITED REVIEW COMPLETE | Ready/identity/pairing có proof, không lost/drop/truncation được báo; không ghép clock hoặc wire-level proof |
| Guest terminal conservative flags | PRESERVED | Gán cứng chờ parent review; không sửa raw flags thành PASS, ghi coverage review riêng |
| Raw copy integrity | PASS 74 files | SHA-256 guest và bản Windows đều khớp |
| Drain/cleanup | VERIFIED | submitted=8/reaped=8/outstanding=0; own modules/filter/usbmon/firmware/instance đã gỡ |
| Sau người dùng rút | CLEAN_DISCONNECTED | Read-only hậu kiểm, không helper/capture phần cứng còn chạy |
| JVM/Android/build/lint | NOT RUN lượt này | Tools-only, không đổi source Android hoặc toolchain; giữ các PASS cũ theo phiên |

WDK local xác nhận BABBLE/CANCELED; public upstream Linux v6.8 giải thích raw xHCI status trước USB-core completion. Chưa xác định root cause hoặc đạt Android TS_SMOKE_PASS. Bước tiếp chỉ được đề xuất là so offline với BDA trace đang có, không cấp thêm hardware run.

## 2026-11-10, phiên 19:32 → 2026-11-11 00:28 — Executor và pre-runtime capture

Branch/HEAD giữ `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`. Tests và raw ở bundle `phase31-layered-20261110-193945-623f1cf1` cùng các run riêng dưới D:\TSDuck. Các PASS dưới đây không chứng nhận TS hoặc sửa driver Android.

| Phép kiểm | Kết quả | Phạm vi / bằng chứng |
| --- | --- | --- |
| Grant/coordinator | PASS 83 | UTC-Z giữ string, scope/hash/caps, ready trước consume, STOP/drain, không replay/ghi đè terminal |
| Process/coordinator integration | PASS 19 | Ba Windows fixture flows và child stdin EOF; không USB/SSH live |
| Live-adapter contract | PASS 28 | Identity/freshness/typed health, source pins, không short SSH/SCP trong active port |
| Persistent channel | PASS 61 | Real fixture process, frame chia nhỏ, silence/EOF/STOP, wrong sequence/type/deadline, không reader thứ hai sau fault |
| Linux SSH channel fixture | PASS 80 PULSE | Strict SSH, source hash đúng; 45–707 ms, trung bình 86,4375 ms; không USB |
| SSH setup với stdin đóng | PASS 16 commands | Chỉ đọc, 478–3065 ms; không tăng hardware heartbeat |
| Windows worker | PASS 192 checks/25 processes | Có log trước arm; thêm 6 startup workers dưới tải PASS; fixture không là live evidence |
| Guest Linux worker | PASS 55 checks | Fake sysfs/tracefs; chưa chứng nhận real startup |
| Guest runtime admission/heartbeat | PASS 13 checks | Không load M thật; module/source/receiver plan giữ nguyên |
| USBPcap/usbmon parsers | PASS 19 tests | Requested/actual/status, owner/pairing, truncated/partial admission, zero/zero không là FIFO-empty |
| Arm đầu / UTC và health | BLOCKED trước attach | Giữ nguyên logs; sửa auto DateTime parsing và startup health barrier, không tuner write |
| Run 234746 / cắm muộn | STOPPED trước firmware | Hết cửa sổ trước xác nhận; không tiếp tục attachment thiếu provenance; người dùng đã rút/ngắt nguồn mới |
| Runs 235450/235716/001852 | SSH pre-arm FAIL | Chưa firmware/receive; 001852 cleanup ban đầu chưa rõ, read-only hậu kiểm xác nhận link/module/usbmon vắng; terminal cũ giữ nguyên |
| Run 002355 Windows cold observer | PRE-RUNTIME CAPTURE RECORDED | ARMED rồi nhận đúng default; 28 frames/14 controls, 0 Bulk, không non-target descriptor, cleanup true |
| Run 002355 guest observer | FAIL readiness / cleanup PASS | OBSERVER_NOT_READY, phase rỗng, raw/export/stats được giữ; không dùng lỗi recorder làm tuner FAIL |
| Layered firmware132 / operational receive | NOT RUN / BLOCKED | Chưa có runtime hoặc firmware transfer trong kế hoạch mới; chờ sửa guest startup |
| Android/JVM/APK/lint | NOT RUN lượt này | Không đổi source Android/toolchain; mọi PASS lịch sử giữ đúng phiên |

Người dùng đã rút/ngắt nguồn và giữ tuner rút sau 002355; chưa báo cắm lại. Không lặp hardware trước sửa và kiểm nguyên nhân guest readiness.

## 2026-11-10, phiên 18:46 — Feasibility FX2/host, không capture mới

Branch/HEAD giữ `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`; giữ edits có trước. Bundle `phase31-observation-ready-20261110-185020-78ccd455` dưới D:\TSDuck lưu exact preflight commands/results và report. Không chuyển kết quả chuẩn bị thành hardware PASS.

| Phép kiểm mới | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| FX2 image/dispatcher review | PASS offline | Hash đúng, đủ 11 handlers; không chứng minh được live peripheral-register read |
| FX2 unit tests | PASS 64/64 | Python 3.12.2, input firmware/CSV riêng đã pin; không USB hoặc runtime RAM read |
| Direct FX2 register observation | BLOCKED | A0 RAM không chứng minh peripheral E6xx; B8 tiêu thụ state, I²C/IRAM không phải SFR read |
| VBox/SSH preflight | PASS read-only | VBox 7.2.22, VM running/xHCI, strict root SSH, Ubuntu 24.04.5/kernel 6.8.0-149; không đổi VM |
| Tuner inventory | NOT PRESENT / user-confirmed disconnected | VBox usbhost và guest không target; không tự attach hoặc lấy địa chỉ lịch sử |
| Windows passive tooling | DISCOVERY PASS | USBPcap 1.5.4.0 driver Running, direct extcap có 5 interfaces; capture open/record NOT RUN, process không elevated |
| Guest capture capability | AVAILABLE / NOT ARMED | tcpdump/trace-cmd có, usbmon file có nhưng chưa loaded; event formats được đọc, không bật tracing |
| xHCI status export | FORMAT-CONFIRMED | Raw field status có; print fmt không in status. Chưa capture binary để báo runtime completion mới |
| M late module/source/receiver | HASH/VERMAGIC MATCH | Cùng bản late/kernel, không load/rebuild hoặc chuyển sang variant early |
| Policy tests lượt đầu | FAIL 1/35 | Missing operational field còn qua gate; giữ log, chưa có executor hoặc hardware |
| Policy tests sau sửa và bổ sung | PASS 36/36 | Strict booleans, scope/caps/hash, epoch/owner/observer và unknown-data classification |
| End-to-end observer/STOP/cap/filter | NOT IMPLEMENTED / NOT RUN | Không dùng policy PASS thay integration readiness; cần hoàn tất trước báo cắm |
| Hardware/USB owner/firmware/capture | NOT RUN | Chưa có exact consent mới, không load usbmon/driver/filter hoặc mở tuner |
| Android/JVM/build/lint | NOT RUN | Không đổi mã chạy hoặc toolchain; giữ các kết quả cũ đúng phiên |

Kế hoạch là PREPARED_NOT_AUTHORIZED_NOT_EXECUTABLE. Người dùng chưa cần cắm/power-cycle hoặc cài gì; chỉ giữ tuner ngắt, VM chạy và chuẩn bị đúng cáp/nguồn/RF IN. Bước kế tiếp là observer coordinator và fake integration, không phải live register probe.

## 2026-11-10, phiên 18:02 — So Windows/Android từ raw đã có, không chạy tuner mới

Branch/HEAD `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`; bundle `phase31-windows-sony-20261110-180339-9e246085` dưới D:\TSDuck. Kết quả mới chỉ là offline; không chuyển historical firmware/TS/cleanup thành một hardware PASS mới.

| Phép kiểm mới | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Input integrity | PASS | Pcap cold2 32.202.128 byte và Android EP0/native/report khớp SHA đã ghi; raw không đổi |
| Pcap byte-level proof | PASS offline | Hai image epochs A0, mỗi epoch đủ 132 transfers/8192 byte/hash đúng; Bulk đầu frame 1650/1659, status=0/actual=8192. Không chứng minh RAM runtime hoặc physical epoch mới |
| TShark controls | PASS | 4.6.9, đủ 23 fields, 6751 controls, 0 warnings; chỉ đưa một cặp Bulk đầu vào tập xuất, không tính toàn queue từ tập này |
| Decoder regression | PASS 21/21 | Owner/bank/pointer/short/epoch và requested/actual được giữ riêng |
| Comparison tests | PASS 17/17 | Stale pre-write read, same-value write, pending write, firmware epoch, bank UNKNOWN, Android owner/clock/encoding và timestamp delta |
| Register comparison | PASS phương pháp | 17 targets/69 events; bảy last-write giống Android snapshots. Không có Windows post-lock snapshot đủ bảy byte |
| Board boundary assertions | PASS offline | Windows hai A0 images, 20 B7 (10 fail/10 success), 8A với 0A00/0400/0401; Android một bootstrap cùng recovery epoch, operational không A0 |
| Vendor81/EP81 distinction | PASS offline | 265 request `0x81` trả byte `00` là software bit; TShark không thấy Bulk EP81 trước EP82 đầu. Không phải FIFO flags |
| Static FIFO model | REUSED/HISTORICAL | Hash lại mapping cùng firmware; không chạy lại FX2 tests hoặc đọc config runtime |
| JVM/Android/build/lint | NOT RUN | Không đổi code chạy hoặc toolchain trong lượt này; các PASS trước giữ nguyên ngày/phiên |
| Hardware/capture/owner switch | NOT RUN | Không ADB/USB/VM/firmware/live capture mới; consent monitor cũ không mở rộng scope |
| Production fix | NOT ESTABLISHED | Không đổi drive/clock/FIFO/PLP, không tăng buffer/delay hoặc replay GPIO |

Khác biệt còn lại là writes 96/97 và requested PLP, không phải nguyên nhân đã chứng minh. Bước tiếp là kế hoạch quan sát FX2/host có nguồn và consent mới trước thực thi; phần chưa có cơ chế đọc được chứng minh giữ BLOCKED. Chi tiết ở [RESEARCH.md](RESEARCH.md).

## 2026-11-10, phiên 16:10 — Sony readback thật, TS vẫn 0 byte

Branch/HEAD giữ `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`. Evidence ở bundle `phase31-sony-output-20261110-161951-5b3ce827`; các kết quả dưới đây không thay thế historical PASS/FAIL.

| Phép thử mới | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Focused compile/driver tests đầu | PASS 37/37 | Sony 8/control 4/smoke 25 ở thời điểm đầu; monitor/app compile PASS |
| Regression JVM sau bổ sung | PASS 578/578 | app 74/drivers 460/usbxfer 31/dvbservice 13, không failure/error/skipped; monitor 9 và smoke 29 trong tổng này |
| Debug/release/lint/AndroidTest APK | PASS | Wrapper final BUILD SUCCESSFUL trong 3 phút 46 giây, 361 tasks; lint 0 errors, debug 18/release 10 warnings; không đổi toolchain |
| Release exclusion | PASS 3 jars | Không có Sony monitor, ControlProbe hoặc TsSmoke debug trong ba release class jars |
| Android fake lượt màn hình khóa | FAIL 3/24 | 19 approval và 2 lifecycle PASS; 3 lifecycle dừng khi Dozing, report FOREGROUND_LOST; không tuner I/O |
| Android fake sau mở khóa | PASS 24/24 | 19 approval + 5 lifecycle, 5,344 giây; permit Sony/default OFF/strict grant/clock và no-I/O lifecycle |
| PowerShell helper cuối | PASS PS7/PS5.1 | Sony scope 8/8, exact Java/helper layout, routing preflight; 57 kiểm launch và 82 kiểm heartbeat mỗi bản; không ADB trong fake |
| Preflight trước permission | BLOCKED trước grant | OS dialog không xác nhận được bằng helper hierarchy, hết 60 giây; người dùng cấp OK thủ công, không chọn Always |
| Bootstrap thật | PASS firmware/drain | Một upload, 132/132 full-length, image 8192 byte, 2834 ms; terminal ATTACH_PERMISSION_UI_TIMEOUT sau re-enumeration được giữ nguyên, chưa có monitor/URB |
| Operational preflight trước routing fix | BLOCKED trước grant | Activity preflight nằm dưới workflow đã drain; token cũ không được nhận. Sửa CLEAR_TOP + SINGLE_TOP; không CLEAR_TASK hoặc rearm |
| Operational preflight chờ OK | BLOCKED trước grant | Helper hết cửa sổ trước OK thủ công; không I²C hoặc URB mới. Giữ cùng epoch và không nạp firmware lại |
| Operational init và baseline controls | PASS control/lock | Không FirmwarePath/A0; 230 EP0 full-length gồm 40 monitor. Bỏ 40 còn 190 records giống byte-exact cả IN/OUT với baseline B |
| Sony AFTER_LOCK | PASS snapshot | 20/20, 11 OUT/9 IN, 619,174461 ms; sync 26 trước/sau, restore bank20 đủ độ dài; không chứng minh TS ở chân |
| Sony BEFORE_FIRST_URB | PASS snapshot | 20/20, 587,510231 ms; bảy byte cùng giá trị với AFTER_LOCK và init, sync 26 trước/sau |
| Native receive | FAIL TS, completion đã biết | 8 submit/8 REAP đều -75/actual 0, syscall return/errno=0, sample 0 byte; không retry/alt0/buffer scan |
| Terminal/cleanup/hash | PASS drain/evidence | REPORT/USB_RECEIVE_FAILURE, outstanding=0/quarantine=false; 16 files Android-host hash khớp, không USB fd sau drain và đã rời workflow |
| Actual PLP/L1, C3/96/97/D9, chân TS/clock/FIFO | NOT AVAILABLE / NOT RUN | Không suy từ TS_LOCK hoặc readback; side-effect-free của read81 trên parallel vẫn UNKNOWN |
| GitNexus | LIMITED | Index-only exit0/102,8s; warnings về scope/flow truncation, MCP không tìm thấy symbol monitor. Không coi graph/FTS là chứng cứ đầy đủ; dùng source/tests/raw |

App-debug SHA256 `4ebf241c0f41f312b44e3f1bb19235c0cc46b047db3256b02a03208b52bb703c`; AndroidTest SHA `cfd0a0aa2c6ce077609bf6dca0822ca882797291cb339040c853da13db2c26ad`. EP0 SHA `39240893ee7e0752caf7ad9d24c3b4c5b270b0da5bf72927238618f377bd65e4`; native `6b8aeb8dd34421857067393af8176acc1b01ceb35fda821ec5f0e8176ecb67bb`. EOL sửa theo từng dòng không đổi nội dung logic. Giữ Phase 3.1 và ROOT_CAUSE_UNRESOLVED, chưa TS_SMOKE_PASS.

## 2026-11-10, phiên 12:09 — Lifecycle sửa và B setInterface đã đo thực

Branch/HEAD `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`; giữ edits có trước. Kết quả mới ở bundle `phase31-lifecycle-20261110-121354-96284261`, không ghi đè historical PASS/FAIL. Raw/grants/physical receipts ngoài Git.

| Phép thử mới | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Launch unit gate | PASS 6/6 | Replay/token malformed, active cleanup, resource mask, acquisition không được đổi thành no-resource |
| Full JVM regression | PASS 565/565 | app 74/drivers 447/usbxfer 31/dvbservice 13, 0 failure/error/skipped; rerun, 366 tasks executed |
| Debug/release/lint | PASS | App/debug và release build; 0 lint errors, debug 18/release 10 warnings |
| Android lifecycle | PASS 5/5 | Real Activity/grant/cleanup, no-I/O mode; includes CLEAR_TOP route qua preflight, token scope và cleanup chưa chứng nhận phải từ chối |
| Android approval regression | PASS 16/16 | Không USB calls; giữ profile/permit/ledger guards |
| Instrumentation lần lỗi | FAIL/UNCLASSIFIED | Một process-crash output khi ADB offline, không app fatal trong DropBox/crash buffer; không xác định nguyên nhân. Sau user khôi phục không reboot, focused route1 và full5 PASS |
| Helper launch/terminal | PASS 57 mỗi PS7/PS5.1 | Fresh token, strict booleans, no-I/O restrictions, typed terminal/drain; không ADB trong fake |
| Helper heartbeat | PASS 82; helper regression PASS | Giữ UID boundary, quoting, expiry,1500ms/2giây; no-rearm orphan staging |
| Control-plane thật lần đầu | FAIL có giới hạn | Call budget guard dừng; grant đã nhận nhưng không tài nguyên, terminal cleanup NO_RESOURCES_ACQUIRED. Giữ log, không nới timeout |
| Control-plane thật sau đó | PASS 2 phiên | Exit0/CONTROL_PLANE_COMPLETE, grant/STOP/terminal/drain; firmware/native/interface=null,0USBfd và hardware history SHA không đổi |
| Launch B trước routing | BLOCKED trước grant | Challenge cũ không khớp token; không firmware hoặc USB I/O. Fix CLEAR_TOP + SINGLE_TOP có fake và Android routing proof |
| B bootstrap thật | PASS firmware/drain | Đúng một upload 132/full 132/image 8192/3230 ms; default→operational. Report WAIT_REENUMERATION nhưng terminal/drain rõ, không upload lần hai |
| B init/K36 AUTO | PASS lock/control | Handoff không FirmwarePath/A0; 190 EP0 full-length, byte-exact toàn bộ giống baseline A |
| B setInterface thật | PASS API execution, không phải TS fix | API calls=1/result=true/duration=958461 ns≈0,958 ms; before/after descriptor/profile không đổi, outstanding=0; app-level, không wire capture |
| B native receive | FAIL nhận TS, valid diagnostic | 8 submit/8 REAP; cả 8 status=-75/actual=0, syscall return/errno=0; sample TS 0 byte |
| B cleanup/raw | PASS | REPORT terminal, DRAIN_CONFIRMED/RUNTIME_DRAIN_CONFIRMED, outstanding=0/quarantine=false. Mười files hash Android-host khớp, đã rời workflow, không thêm receive |
| Android TS_SMOKE_PASS / nguyên nhân gốc | NOT ACHIEVED / UNRESOLVED | Explicit setInterface đơn lẻ không đủ trong điều kiện đã đo; không loại mọi vấn đề USB host |


## 2026-11-10 — Phase 3.1: state differential, Android alt0 và giới hạn từng tầng

Baseline `chore/modernize-android-build` / `b73f11097e594acd2abe1769a65ed03216c8e963`, tree sạch đầu phiên. Chỉ các hàng ghi kiểm mới là kết quả thực chạy phiên này; tái phân tích raw không phải hardware rerun. Artifacts dưới bundle `phase31-state-20261110-100952-10a3635f` và validation Android `gate-d-alt0-20261110-102308-6153665f` ngoài Git.

| Gate / phép thử mới | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Raw provenance / source SHAs | PASS | Hash Windows pcap/TS, M late/early, Android EP0/native khớp; bốn references giữ chỉ đọc |
| Windows raw và TS reanalysis | PASS offline, quality không PASS | Hai A0 epochs riêng đủ 132/8192/hash; first Bulk frame1659 status0/actual8192. TS có 164.350 packet, 14 services, 47 PID, PAT/PMT nhưng 3.054 TEI/2.395 discontinuities |
| Linux Sony TS_LOCK | READBACK_VERIFIED từ raw lịch sử | M late/early đọc bank20/10=26 trước Bulk và chín lần trong pending mỗi lượt. USB vẫn 0 byte; không suy FIFO/pin state |
| Actual PLP/L1/C3/drive readback cuối | NOT_AVAILABLE | Không đoán địa chỉ chip khác, không đổi bank mới khi chưa có quyền |
| State decoder | PASS 21/21 | Owner pairing, UNKNOWN bank, pointer, IN81 có proof, A0 epochs và phân biệt status/requested/actual |
| Fake feed pipeline | PASS 16 checks | Extracted start_feed/callback, fake swfilter/DVR giữ bytes; không kernel USB hoặc MPEG-TS thật |
| M source/receiver/FX2 regression | PASS | 820 extracted callback checks, 80 mocked receiver flows, 64 FX2 tests/0 skip; source/patch/hash reproduction đúng |
| Android focused JVM | PASS 60/60 | Rerun focused selection/policy/smoke, không hardware |
| Owner-state invariant cuối | PASS 54/54 | Ba suites session/smoke/selection chạy fresh sau thêm assertion: state vẫn READY khi streaming boolean=true/claimed. Không có lỗi tự reject do enum STREAMING; không sửa production hoặc rebuild APK |
| Android full JVM | PASS 559/559 | app68 + drivers447 + usbxfer31 + dvbservice13, failures/errors/skipped0; 397 tasks executed trong combined validation |
| Debug/release/lint / androidTest build | PASS | Hai APK instrumentation build; lint 0 errors, debug18/release10 warnings; diagnostic class vắng trong release |
| Tablet fake runtime | PASS 4+16 tests | SM-X926B/API36; fake lifecycle và approval trên Android, không gọi USB |
| Android A bootstrap / handoff mới | PASS firmware/lock, FAIL nhận TS | Fresh attachment người dùng xác nhận; full132/3072ms, handoff không A0,190EP0; tám REAP -75/actual0, sample0, outstanding0/quarantinefalse/drain |
| Android B đầu | BLOCKED trước setInterface | Full132/2940ms; handoff150EP0 full-length rồi HOST_HEARTBEAT_INVALID ở FRONTEND_INIT. Không API/native/sample; không xem là candidate negative |
| Host orchestration | FAIL có giới hạn, giữ evidence | A timeout heartbeat exchange sau receive; B timeout export sau drain. Thu raw riêng bằng readonly collector, SHA Android/host khớp; không đổi USB result thành host success |
| Host helper cuối / UID boundary | PASS offline 82/82 mỗi PS7/PS5.1; runtime NOT_VERIFIED | Clock đọc ở shell UID rồi positional argument vào app UID; giữ runtime 1500 ms/heartbeat 2 giây và expiry. Read-only app UID đọc uptime bị Permission denied đã đo |
| B thay thế được duyệt | BLOCKED trước grant/API | Grant staging không publish; launch sau sửa gặp stale challenge. Control0, firmware/native/interface=null nhưng DRAIN_NOT_CERTIFIED; không coi là TS hoặc drain PASS |
| Cleanup A/B đầu | PASS có phạm vi | DRAIN_CONFIRMED; A native8/8/outstanding0/quarantinefalse, B đầu không native. Không rearm ledger hoặc upload operational |
| Cleanup workflow cuối / B2 | NOT_CERTIFIED / NOT RUN | Guard dừng trước NEW_TASK/CLEAR_TASK/B2; không force-stop, detach hoặc reset. Giữ file và attachment chưa được chứng nhận cleanup |
| Linux hardware mới / GPIO/controller | NOT RUN | VM chỉ kiểm đọc; không snapshot, đổi controller, module load, LNB/DC/B7/04xx |
| Android TS_SMOKE_PASS / root cause | NOT ACHIEVED / UNRESOLVED | Không sample TS; build/fake/lock không thay thế bằng chứng MPEG-TS |


## 2026-11-09 → 2026-11-10 — Phase 3.1: original build, Linux M late/early và cleanup

Git trước/sau `chore/modernize-android-build` / `cbb2aa6892ede102d7b122badec848ae7c483b14`, sạch đầu phiên. Không stage/commit/push/sync hoặc đổi historical PASS. Bằng chứng mới trong hai bundle `phase31-original-20261109-234104-c6017f83` và `phase31-original-20261110-004247-09840fb6` dưới D:\TSDuck.

| Gate / phép thử mới | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Git/source/artifact admission | PASS | Bốn source SHA pin lại; tám raw artifact cũ khớp manifest; references chỉ đọc |
| Original community O-C | PASS build, NOT RUN load | Copy mới/hash nguyên trạng; `make -j2` đủ 5 module/MODPOST/vermagic; 226 symbol CRC khớp guest. Probe vệ tinh/B7/8A0801 chưa an toàn trong scope |
| Original official O-T | NOT BUILT full closure, NOT RUN | Source review vendor media API/layout; không suy ABI từ board-only hoặc tự cài media stack |
| M source/compile/ABI | PASS | Terrestrial config/init/feed và successful receive được bảo toàn; patch safety riêng; 3 module/127 CRC, không install |
| Extracted callback/guard | PASS 820 checks | Tái hiện original resubmit zero/lỗi âm; kiểm M short/error/cap/deadline/submit/drain contract bằng fake, không USB |
| Receiver identity/profile | PASS 80 mocked flows mỗi Windows/Linux | Tên original/128-byte FE field, VID:PID/canonical parent/adapter, frontend/demux/DVR cùng parent; K36/8 MHz/manual PLP0; negative/STOP/cleanup; production Linux compile PASS |
| Runner grant/ledger | PASS 10 checks | WhatIf không consume; early timing cần grant; O_EXCL/fsync chống replay, cold/handoff riêng; dừng trước SSH trong tests |
| CRC parser | PASS 10 fixtures | Layout thật `u32 next/u32 crc/name[]` và NULL sentinel; CRC sai/thiếu export/truncated bị từ chối. Kmod31 dump Invalid argument không bị force qua |
| Cold M bootstrap A | PASS firmware, dừng trước frontend | 132 A0 full-length, coverage 8192/hash đúng; cached EP82 chưa có, tự device2→3, không reset/upload lần hai; cleanup_ok=1 |
| M late operational | FAIL nhận TS; valid diagnostic | K36/status 31, 8A sau lock; tám URB requested 65536 chờ 10 giây rồi cancel -2/actual 0; submitted/reaped 8/8, outstanding 0, DVR 0; -110 là policy |
| M late trace | PASS capture/normalizer | 1492 events paired, dropped 0; không A0/B7, vendor controls full-length; pcap SHA 2470e8fe… |
| Timing patch/offline | PASS | Tái tạo từ pinned M; chỉ hai call sites và flag OFF, exact payload/early-before-init/short-error/baseline tests |
| Cold timing bootstrap B | PASS firmware | Fresh physical epoch do người dùng xác nhận; đúng 132 transfers/8192 byte/hash, device4→5 tự nhiên, không A0 ở handoff |
| M early operational | FAIL nhận TS; timing không đủ sửa | Frame 408 có một 8A0A00 trước init, K36/status 31; tám URB chờ 10 giây rồi cancel -2/actual 0, submitted/reaped 8/8, outstanding 0, DVR 0 |
| Timing capture/normalizer | PASS có giới hạn | 1935 events/dropped 0; boundaries đúng ordinal 60/384, 1934 paired và một missing-completion enumeration. Toàn bộ 132 A0 và 8 Bulk paired riêng; không che cảnh báo |
| Fresh Android JVM | PASS 530/530 | App 68/drivers 431/usbxfer 31; failure/error/skipped 0; wrapper --rerun-tasks, 331 tasks executed |
| Debug/release/lint | PASS | Wrapper :app:assembleDebug/:app:assembleRelease/:app:lintDebug/:app:lintRelease exit 0; không đổi toolchain |
| Native GCC / MSVC ASan | PASS 4096/8192 | Real core fault injection/canary; MSVC lần đầu thiếu /Zi gây warning-as-error, sửa command rồi PASS, không sửa source |
| GCC ASan riêng | NOT RUN runtime | Link FAIL thiếu libasan trong MinGW đã cài; không cài thêm. Không đánh đồng với GCC thường hoặc MSVC ASan |
| NDK core/JNI | PASS syntax-only 4 ABI/API21 | Không suy Android hardware từ syntax; release/debug build kiểm riêng |
| Usbmon normalizer fixtures | PASS 77/77 mỗi PS7/PS5.1 | Fresh tests, không hardware |
| Shell/PowerShell/WhatIf | PASS | bash -n guest; scripts parse, prepare WhatIf/reproduction/hash, runner WhatIf không I/O |
| Cleanup/owner cuối | PASS | URB drain 8/8, outstanding 0 cả hai; modules/usbmon/filter/firmware tạm vắng; Windows PnP OK/BDA/Shared; ledger còn |
| Android live/instrumentation mới | NOT RUN | Không grant Android mới hoặc causal fix; không ADB/firmware/tune/receive mới |
| TS sample/PAT/PMT/quality | FAIL nhận TS, parser NOT RUN | Hai file 0 byte, không có sample ≥32768/sync 188/PAT/PMT/CRC/TEI/CC. Android TS_SMOKE_PASS vẫn chưa đạt |

Kết luận: ROOT_CAUSE_UNRESOLVED_WITH_NARROWED_SCOPE. M không original; timing sớm không đủ sửa trong cấu hình M/xHCI, không chứng minh một source diff là nguyên nhân. Không mở phase mới hoặc chạy lại cùng ledger.

## 2026-11-09, phiên 19:18 +07 — Phase 3.1: EP0 mirror và Samsung 4096/8192

Branch/HEAD trước và sau `chore/modernize-android-build` / `48b53d788db5e023123afbc0a65f77a16ae084ef`. Giữ edits instructions đầu phiên, không stage/commit/push/sync. Không đổi historical PASS/FAIL. Báo cáo/logs mới dưới `D:\TSDuck\reports\phase31-continuation-20261109-192232-71163e13` và sibling logs; module core/firmware proof có bundles riêng được dẫn trong báo cáo tổng.

| Gate / phép thử mới | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Artifact cũ / Linux raw | PASS, FOUND_VERIFIED | Inventory 1964 file; tám hash trọng yếu khớp manifest gốc, gồm cả strict/credit1 text/pcap. Không chạy lại VM hoặc bịa 18 byte thiếu của actual 82 |
| FX2 proof offline | PASS 64/64, exit 0 | Hai epoch A0 riêng đủ 132/8192/hash; 255 độ dài opcode khớp Keil. Không device I/O |
| Mirror JVM | PASS 8/8 | OUT trước delegate, IN chỉ returned slice, zero/negative/oversized/exception, sequence/bounds/no overwrite/drain; không thêm call hoặc đổi timeout, không disk I/O trong transfer |
| Fresh JVM tổng | PASS 530/530 | Drivers 431/app 68/usbxfer 31, failures/errors/skipped 0; wrapper prelive --rerun-tasks, 173 tasks executed |
| Native GCC/MSVC ASan | PASS 4096/8192, exit 0 | Capacity/pointer/actual bounds, cap/depth, no post-error resubmit, cancel/drain/quarantine; fake kernel, không tuner |
| NDK core/JNI bốn ABI | PASS syntax-only | NDK 30.0.16248370/clang 21/API 21; link/runtime được kiểm riêng qua APK và live, không suy từ syntax |
| App fake/permit tablet | Ban đầu 3/4 PASS, sau sửa 4/4 PASS | Test mới kỳ vọng ledger rỗng sai vì SESSION_GRANTED hợp lệ; sửa assertion, không bỏ guard. Kiểm exact 8192 permit khác 4096, scope mirror và default OFF |
| Library fake tablet | PASS 2/2 | Mirror qua Handoff không thêm control; full fake manual/AUTO giữ count 187/188, không tuner I/O |
| Debug/release/lint | PASS | Wrapper assembleDebug/test APK, assembleRelease/lintDebug/lintRelease; JDK 25.0.5.0.3/AGP 9.5.0/Gradle 9.8.2 giữ nguyên |
| Helper PS7 / PS5.1 | PASS | Fake-only/negative/terminal/hash parser; giữ BOM sau lỗi PS5.1 parse. Không đổi policy hoặc gọi ADB trong tests |
| Preflight UI / task reuse | FAIL trước hardware ở hai lượt đầu | Permission 60 s hết hạn, token mới không được task cũ tiếp nhận; đóng màn terminal rồi tạo instance mới, không force-stop/clear data hoặc consume firmware |
| Firmware A | PASS full-length; phiên chưa TS | c347d3b9-ee2c-4580-b296-dedc955f33d4: 132/132, ảnh 8192/payload 8196, 3073 ms; probe 5 controls ID 01/97. Budget từ chối trước init; giữ ERROR_UNCERTAIN, drain sạch |
| Firmware B | PASS full-length; handoff UI timeout | 239293f8-de3a-4629-8d13-e63417ad8625: 132/132, 3155 ms; 132 mirror records. Attach-confirm vượt 10 s, dừng WAIT_REENUMERATION, không init/Bulk |
| Samsung A 8×4096 | FAIL nhận TS; diagnostic valid | 9a531e91-0366-4f71-bc67-2caba9846083: K36 AUTO lock, 190 EP0, 8 submit/reap; 8×-75/actual 0, syscall 0/errno 0, TS file 0; REPORT/drain/outstanding 0/quarantine false |
| Samsung B 8×8192 | FAIL nhận TS; candidate U không sửa lỗi | 4fc265e0-2377-4997-9a26-0d4afc3a99c8: cùng 190 EP0, 8 submit/reap requested/capacity 8192, 8×-75/actual 0, TS 0; cleanup như A |
| Android raw EP0/native | NEWLY_COLLECTED, hash khớp | 16 files readback bằng exec-out binary-safe, SHA Android/host khớp. EP0 A 63761bdc…/B bb68d798…; native A 370f0ffd…/B b30d7b12…; full hash ở RESEARCH/manifest |
| Export hash lần đầu | FAIL parser, không mismatch raw | Host/device cùng hash 0859647e…; sửa command-mode -split bằng parser 64-hex có fake test PS7/PS5.1. Không chạy lại tuner để kiểm |
| Nhãn chooser debug | PASS build/lint; NOT RUN cài/UI cuối | Query xác minh app chính có AutonomousDiagnosticActivity + legacy UsbDelegate; nhãn debug riêng. Đã gỡ app cách ly theo user; ADB offline sau thu bền nên không cài APK nhãn mới |
| PA7 / TS drive / selected PLP A/B mới | BLOCKED_FOR_SAFETY / NOT RUN | Static latch không chứng minh wiring/mức điện; chưa mapping drive đúng chip/effective readback. Không vendor mới, không mở rộng hai attachment đã duyệt |
| Android TS_SMOKE_PASS | BLOCKED, false | Cả A/B file 0; không PAT/PMT/CRC/sample ≥ 32768, không gọi lock/build/Windows TS là Android PASS |

Hai firmware sessions chỉ chuyển sang operational mới sau full 132/cleanup và bằng chứng không có partial init; không reset/rearm ledger hoặc A0 lần hai. Native A/B đều đã drain trước ADB Wi-Fi offline; không auto reconnect/reset hoặc suy owner cuối còn live. Không thử lại phần cứng trên APK nhãn debug mới. Kết luận: ROOT_CAUSE_UNRESOLVED_WITH_NARROWED_SCOPE.

## 2026-11-09 — Phase 3.1: Linux operational-only đã chạy, chưa có TS

HEAD không đổi `69090a0bcf00b4c6342ec075d90a74964b25ecc9`. Bundle `D:\TSDuck\reports\phase31-linux-runtime-20261109-004247-91506f8c`; raw trong sibling logs/traces/captures. Người dùng cho continuation tự động, handoff có thông báo/ready; không snapshot hoặc firmware upload Linux.

| Gate | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Operational module proof/build | PASS có phạm vi | 281+1522+21 fake tests; 25+27 source checks PS7/PS5.1, MODPOST/122 CRC khớp; final board SHA `be006fdadf5b3c4cf6e519204d06eeb809ff4fb6f01830e16012fcaf40286b0d` |
| Runner offline | PASS | Ledger 19 checks mỗi PS7/PS5.1, plan 26 WhatIf/negative, receiver policy+5 mocked flows; không device I/O ở các tests |
| SSH preflight đầu | FAIL trước hardware | Multiline command có CRLF, exit 2; ledger chưa consume và chưa load. Chuẩn hóa LF, giữ execution cũ và dùng execution-lf; không retry USB attempt |
| Guest handoff/admission | PASS runtime | Fresh operational descriptor 50 byte SHA `22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5`, HS 480/config 1/interface 0/alt 0, probe 5 controls đủ 12 bytes |
| Module probe / K36 | PASS runtime | CXD6802 detected; frontend registered, receiver báo FE_HAS_LOCK/status 31; PLP0/K36/8 MHz, không AUTO hoặc LNB/DC |
| I²C/control | PASS transport length | 324 controls/requested 1122/actual 1122, first_failed_control 0; sau zero-stop có 7 suppressed controls, không phát tiếp lên USB |
| Bulk IN82 | RUNTIME-OBSERVED, chưa TS | 9 submit requested 65536; first complete 82/status 0, next 0/status 0; guard tự latch -61, 7 cancel -2. Không -75/-71 trong lượt này; last_status cancel không phải lỗi đầu |
| Capture trace | PASS có truncation giới hạn | 678 events, pcap 55.561 byte SHA `ed7e1861303977af76de61590dcf8f4c8047b37d98e6dd1d05240750da45e8ca`, tcpdump kernel dropped 0. First 82 chỉ capture 64 byte prefix, không bịa 18 byte còn thiếu |
| Firmware/vendor exclusion | PASS quan sát trace | Có requests 80/90/91/8A và standard 6; không A0/B7. Descriptor không chứng minh RAM firmware hash |
| MPEG-TS | FAIL nhận TS | File 0 byte, actual USB 82 byte chưa đủ một packet 188. Không chạy tsanalyze trên file rỗng hoặc gọi lock/bytes là TS_SMOKE_PASS |
| Cleanup / replay | PASS số đo | Submitted/reaped 9, outstanding 0/drained 1/quarantine 0, dependencies/board do phiên load đã unload; host/guest ledger consumed và terminal BLOCKED, không rearm |
| Hardware A/B fix/recovery | NOT_RUN | Giữ queue 64 KiB và Sony bytes, không reg 96/97 patch hoặc replay vendor. Không suy nguyên nhân từ VM khác Android |

**Giới hạn quan trọng của lượt đầu:** zero-length completion có status0 bị chính sách diagnostic dừng ngay. Không dùng lượt này để kết luận original Linux driver cũng không có TS. Đối chứng mới dưới đây đã cho một ZLP tiếp tục; vẫn không phải original Linux driver hoặc Linux native.

### 01:40 +07 — Đối chứng một empty-success credit trên epoch mới

Run `phase31-linux-runtime-20261109-013202-4ecedf9c`; người dùng xác nhận rút USB ≥5 giây/tuner tắt đèn rồi cắm cùng điều kiện. Windows cold capture mới trước VM có hai upload 132 A0 đúng hash và mẫu TS 7.999.964 byte/42.553 packets/14 services/46 PIDs, TEI 0/sync 0; không gọi toàn quality PASS chỉ từ hai chỉ số.

| Gate | Kết quả thực | Giới hạn |
| --- | --- | --- |
| Offline candidate credit 1 | PASS | Giữ 281+1522+21 tests, thêm 54 exact extracted completion checks; source 25+30, MODPOST/122 CRC. Board SHA `07d812a13d511231d8dfc084ab02675e7345a8e1602e8205e8b8d7c0a2612a47`, queue/Sony bytes không đổi |
| Fresh admission / K36 | PASS runtime | Guest bus 2/dev 3, operational hash 50 byte khớp, probe sạch và FE status 31 |
| Control transport | PASS full-length | 635 calls, requested/actual 2070, first_failed 0; không A0/B7, không suppressed controls trước terminal |
| Empty credit | PASS hoạt động thật | First RX status 0/length 0, used 1/resubmits 1; tám URB còn pending tới deadline, không stop ngay ở zero |
| TS receive | FAIL / 0 byte | Receive window khoảng 10 s, actual 0, guard fault -110; không -75/-71 và không HCD timeout completion để quy nguyên nhân |
| URB cleanup | PASS | 9 submit/reap, tám cancel -2, outstanding 0/drained 1/quarantine 0; không force-unload |
| Trace / normalizer | PASS | Pcap 106.205 byte/1300 events/SHA `2ef4dbf135755edbdd8ff26e682c8f497355e2008103b48e15b4b5f53bb9d903`, dropped 0. Text 1300 records đều paired, requested 65536; clock guest unmapped |
| Normalizer regression | PASS 77/77 mỗi runtime | Sửa OUT callback data tag `>` theo Linux docs; giữ requested/actual/null. Lượt lỗi/cleanup cũ không đổi thành PASS |
| Host plan / ledger / receiver | PASS offline | Plan 28 WhatIf/negative, ledger 19 mỗi PS7/PS5.1, receiver 5 mock flows; terminal 3 cases phân biệt BLOCKED với process exit 0 |
| Return to Windows | PASS quan sát mới | Sau user ready, usbdetach; PnP OK/TBS BDA, usbipd Shared, guest không tuner/module. Usbmon cũng unload khi refcount 0 |

**Kết luận đối chứng:** dừng ngay ở zero không phải blocker duy nhất trong điều kiện VM run thứ hai. Vì có epoch/power-cycle/Windows init mới và nhiều khác biệt với Android, chưa ROOT_CAUSE_CONFIRMED. Không thử credit/buffer cao hơn hoặc reload hai attachment đã consumed.

## 2026-11-08 → 2026-11-09 — Phase 3.1: cold-start Windows, ETW requested length và pipeline offline mới

Baseline HEAD `69090a0bcf00b4c6342ec075d90a74964b25ecc9`, branch `chore/modernize-android-build`; không sửa runtime Android hoặc toolchain. Evidence root `D:\TSDuck\reports\phase31-fullstack-20261108-172615-404b3ba8`, raw sibling logs/traces/captures. Các gate ở đây là lượt mới, không đổi PASS/FAIL/UNCERTAIN lịch sử.

| Gate / phép thử | Kết quả thực | Phạm vi / giới hạn |
| --- | --- | --- |
| Inventory tài sản / sidecar | PASS | 420 file đầu phiên; native OnePlus, tablet report/folder có thật; sidecar manifest Gate A khớp SHA |
| Normalizer synthetic | PASS 67/67 mỗi runtime | PS 7.6.9 và Windows PS 5.1.19041.7802, exit 0; missing length, reuse, control pairing, epochs, canceled, native status/errno, malformed, atomic output và privacy. Parent final gặp file lock ở cleanup/exit 1; chuyển công bố PASS ra sau cleanup, chạy lại hai runtime 67/67/exit 0. Fault injection cleanup lỗi xác minh child nonzero, không PASS report/line; không suy tác nhân giữ lock |
| Native trace conversion | PASS offline | Ba file, mỗi file 24 records; OnePlus 3×-75/5×-71, hai tablet mỗi file 8×-75; không gọi là hardware retest |
| Cold2 normalization | PASS có cảnh báo rõ | 21.136 frame nguồn, 21.134 normalized, loại hai pseudo transfer 0xfe; tất cả paired, 8 incomplete_setup_fields giữ null, payload mặc định bỏ |
| Windows cold1 | INCOMPLETE | Recorder có firmware nhưng helper lỗi biến Matches trước TS; giữ raw/log, sửa parser bằng 5/5 cases, không biến exit helper 0 thành capture PASS |
| Windows cold2 timeline | PASS receive/capture | Trước attachment → hai chuỗi firmware → init/K36 → 3.816 submit/complete IN82 → stop. Pcap 32.202.128 byte/SHA `70fced47614ff8ec17c9e78f79fca16408c23e6bcadcfb6ad25a4c69cd7f9ded`, không truncated tail |
| Firmware mỗi upload | PASS provenance | 132 submits/lần, mỗi byte image được ghi một lần mỗi upload; cả hai SHA `b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc`; không bundling blob |
| Windows cold2 TS | PASS bytes/structure, không quality PASS | 30.897.800 byte/164.350 packets, 14 services/47 PIDs; 3.054 TEI, sync 0, CC/PES errors. PCR bitrate khoảng 134 Mb/s không dùng làm thiết kế |
| Cold2 ETW toàn cửa sổ | FAIL coverage | Đạt cap 64 MiB trước attachment, BuffersLost 9; không có target dispatch để suy requested, session tự dừng |
| Targeted ETW requested | PASS số đo trực tiếp | 1.440 Dispatch requested 8192, peak 8; không orphan/duplicate, pending cuối 0. Complete 1.432×8192/status 0 và tám cancel status 0xC0010000; EventsLost/BuffersLost 0, stop exit 0 |
| Targeted Windows TS | PASS receive, không quality certification | 4.999.860 byte/26.595 packets, 14 services/46 PIDs; TEI/sync 0, vẫn có discontinuity báo trước |
| CoreELEC differential | PASS source/offline | Pin năm SHA, 76 file hash; 13 checks nguồn và 6 checks cold2 PASS; không CoreELEC runtime hoặc root-cause proof |
| Android debug/release/lint | PASS | Gradle Wrapper exit 0, 336 tasks/47 executed ở lượt build; giữ Java 8/minSdk 21. Lint có 0 errors, debug 18/release 10 warnings |
| Static ABI/page-size | PASS có phạm vi | Debug có 4 ABI/4 diagnostic libs; release có 4 ABI/0 diagnostic libs. Zipalign 16 KiB của hai APK và ELF64 LOAD alignment đạt; 316 driver classes có major 52. Không thay cho runtime API 21/16 KiB smoke |
| JVM fresh toàn bộ | PASS 513 | `--rerun-tasks`, 81/81 tasks executed, exit 0; drivers 420/app 68/usbxfer 12/dvbservice 13, failures/errors/skipped 0 |
| Native C fault injection | PASS | GCC 16.2 `-Wall -Wextra -Werror`, MSVC 2019 AddressSanitizer; real core/canary bounds, binaries tạm đã dọn |
| WSL compiler/Kbuild admission | Compiler PASS; Kbuild BLOCKED | C syntax exit 0; make exit 2 thiếu Makefile/Kbuild/generated headers/Module.symvers; chưa compile driver |
| VirtualBox/guest/SSH | PASS inventory | Người dùng triển khai; VBox 7.2.22, guest 24.04.5/kernel 6.8.0-149-generic, strict hostkey và headers thực |
| Live snapshot | FAIL, không retry | RPC_S_CALL_FAILED/0x800706be exit 1; người dùng xác nhận treo, tự phục hồi và yêu cầu không tạo snapshot nữa |
| Guest dependency | PASS sau recovery | Apt simulation không upgrade/remove; install build-essential/libelf-dev exit 0, GCC 13.3/Make 4.3; chưa tự thay kernel/media stack |
| Official TBS / Beta minimum closure | FAIL compile, exit 2 | Frontend dùng API vendor ngoài stock DVB core; không port rộng hoặc thay core. Beta audit guest đủ 331 entries, extract chọn lọc; không chạy wrapper |
| Official board-only ABI probe | PASS một phần, NOT_LOAD_READY | `.ko` board có modpost/vermagic và 32 CRC khớp, nhưng thiếu hai frontend; không lấy phép so CRC rỗng làm PASS |
| Community C nguyên trạng | PASS build/ABI, BLOCKED_PROTOCOL_REVIEW | 5 `.ko`, MODPOST PASS, 226 CRC khớp; queue 8×65536 khác primary. Probe tự khởi tạo M88/firmware vệ tinh và B7/8A, chưa chứng minh no-LNB nên không load |
| Diagnostic Linux chỉ DVB-T2 | BUILD_OFFLINE_PASS; RUNTIME_NOT_AUTHORIZED | 3 `.ko`, MODPOST/108 CRC khớp; 281 fake-plan cases, 25 source checks mỗi PS7/PS5.1 và fresh prepare/WhatIf PASS. Bỏ đúng unused cleanup label sau review. Final board SHA `4fe9a866a45e6381f14f6039e6dff95517848162f2ba34ac056fa82107eb21b9`; Sony bytes/8×65536 giữ nguyên, PLP0-only. Cold/handoff/replay/I²C/receive runtime gate chưa đủ |
| Linux/VM module load/usbmon/TS | NOT_RUN | Chưa nạp/cài driver, chuyển tuner hoặc ghi firmware Linux; build PASS không thay hardware test |
| Android instrumentation / hardware retest | NOT_RUN | ADB MCP list_devices hiện không có thiết bị; không reconnect/failover hoặc tự boot emulator. Không dùng build làm bằng chứng tuner; giữ hai FAIL UI lịch sử, chưa TS_SMOKE_PASS |

Nguồn chi tiết: [RESEARCH.md](RESEARCH.md), ADR-030 trong [DECISIONS.md](DECISIONS.md) và báo cáo riêng trong evidence root. Không upload raw USB/TS/ETL hoặc identifiers vào Git.

## 2026-11-08 — Validation thay đổi script đồng bộ so với HEAD

Đối chiếu baseline `69090a0bcf00b4c6342ec075d90a74964b25ecc9` với working tree hiện tại. Git 2.56.0.windows.4; PowerShell 7.6.9 và Windows PowerShell 5.1.19041.7802. Ghi kết quả của bản cuối cùng; không tách các lần thiết kế giao diện chưa commit thành những checkpoint độc lập.

| Gate | Kết quả đã kiểm / giới hạn |
|---|---|
| Cách gọi / help | PASS: gọi trực tiếp hoặc `-CheckOnly`, workspace tự suy; help có đúng hai ví dụ |
| Parser | PASS trên PS7/PS5.1, 0 syntax errors; yêu cầu PowerShell 5.1 còn hiệu lực |
| TBS thật, chỉ đọc | PASS: SHA 2ce787de, index có/status sạch, đủ 1.357 selected files và hash |
| CoreELEC thật, chỉ đọc | BLOCKED: SHA 072f8e99, thiếu index, 74.109 staged deletions, 27 selected blobs chưa local |
| Chẩn đoán CoreELEC bằng helper CheckOnly | PASS an toàn: báo missing index; fingerprint metadata trước/sau không đổi, không fetch/repair |
| Regression bản cuối trên PowerShell 7.6.9 | **PASS 74/74, exit 0**, gồm lượt kiểm lại sau khi pin SHA đích cho sparse merge |
| Regression bản cuối trên Windows PowerShell 5.1.19041.7802 | **PASS 74/74, exit 0**, không bypass ExecutionPolicy |
| Clone/sparse NTFS | PASS trong fixtures: manifest Windows-safe trước checkout, AUX ngoài scope không materialize, TBS cone và CoreELEC file scope đúng |
| Tự phục hồi / selected blob download | PASS trong fixtures: backup đúng trước tải, HEAD/ref giữ nguyên ở bước recovery, không FETCH_HEAD mới, after.json PASS |
| Bảo toàn local work | PASS: dirty/staged deletions/untracked data, locks, path không an toàn và hash mismatch không bị force-fix |
| Runner đủ năm repositories / chạy lại | PASS bằng remote local: main fetch-only, clone/repair/update references và kiểm manifest/hash; chạy lại không mất dữ liệu |
| CheckOnly | PASS trong fixtures: không clone/repair/merge hoặc tải missing sparse blobs; fetch repo khỏe chỉ để so sánh |
| Cleanup | PASS: 0 fixture còn lại, không mạng ngoài hoặc references thật bị sửa |
| Full sync/recovery/CheckOnly network trên references thật | NOT RUN trong tác vụ viết code |
| Android build/tests và USB hardware mới | NOT RUN, không thuộc validation PowerShell |

### Bổ sung lỗi thực tế và regression partial/shallow thật

| Gate bổ sung | Kết quả / giới hạn |
|---|---|
| Lần người dùng chạy lúc 16:32 | TBS/C PASS, coax fast-forward; CoreELEC FAIL ở fetch OID sau backup, chưa materialize |
| OID gây lỗi | Blob `drivers/media/usb/dvb-usb/dvb-usb.h` còn thiếu, không phải ref nhánh; HEAD CoreELEC vẫn 072f8e99 |
| Repro partial/shallow thật trước sửa | FAIL: `bad revision`/necessary objects với remote file local, `--depth 1 --filter=blob:none --no-checkout` và promisor packs thật |
| Thêm filter nhưng giữ default negotiation | FAIL cùng lỗi, không phải do thiếu filter riêng |
| Exact-object fetch với negotiation=noop | PASS đối chứng local; selected blob có sau tải, không cần đổi protection hoặc retry nhiều lần |
| Regression sau sửa trên PowerShell 7 | **PASS 76/76, exit 0**, NOT RUN 0 |
| Regression sau sửa trên Windows PowerShell 5.1 | **PASS 76/76, exit 0**, NOT RUN 0, không bypass policy |
| Hai cases promisor/shallow mới | PASS: selected blobs được tải; HEAD/refs/FETCH_HEAD giữ nguyên; không tải blob ngoài manifest hoặc lưu negotiation config |
| Cleanup sau bản sửa | PASS, không còn fixtures regression hoặc fixture đối chứng riêng |
| Agent chạy lại recovery trên CoreELEC thật | NOT RUN, không fetch hoặc ghi reference thật sau sửa |

Test script là [Test-TBS5530-RepositorySync.ps1](../../tools/Test-TBS5530-RepositorySync.ps1). Đã thêm hai cases partial/shallow thật để tránh lặp khoảng trống của fixtures clone thường bị xóa loose blobs. Kết quả sau bản sửa negotiation là 76/76 trên cả hai runtime như bảng bổ sung, không dùng 74 PASS cũ thay cho code mới. Chi tiết ở [RESEARCH.md](RESEARCH.md) và [WORKLOG.md](WORKLOG.md).

## 2026-11-08 — Tablet A/B thực tế: firmware, K36 lock và lỗi native -75

Phiên bắt đầu ngày 07-11 lúc 23:01 +07. HEAD `1202c8abd49a48c3099e718bead13a21a9176660`, source fingerprint sau sửa `127e7f550d7db79cd1ea25cd3f763b49c03a2706c407f05722be30e6c7e4f911`. Debug hardware SHA `1c51ddf79bb1f3c08dc5b62a219d142e06252d851e0d963c68471dbfd36523a3`; release SHA `0027dd13f4b75e4cdbd93cd51225ec2d1a5b7a918f07f0a472ccaa7e6bd5ef53`.

| Gate / phép thử | Kết quả thực | Bằng chứng / giới hạn |
| --- | --- | --- |
| Tablet và USB trước grant | PASS số đo | SM-X926B/API 36, USB 734c:5530 hiện hữu; không dùng lịch sử để nhận owner |
| Nguồn / anten | USER-CONFIRMED | Anten thụ động RF IN; nguồn qua Type-A phụ của dây USB chữ Y vào sạc điện thoại, data qua Type-C vào tablet; chưa đo điện |
| Proof trước live | PASS | Source khớp manifest đã kiểm; helper negative tests + 18 selector tests PASS; guard 28 + library 43 instrumentation fake-only PASS |
| Permission / preflight | PARTIAL automation | Dialog secure không có node hữu ích trong hierarchy; agent kiểm ảnh và bấm OK, không chọn Luôn cho phép. Các preflight hết 60 giây không tạo grant hoặc native writes |
| Firmware tablet đầu | UNCERTAIN, không đổi lịch sử | Run 8ab6a744: native 132/132 full-length nhưng managed completed 131/8195 byte, failure chain IOException chung; drain sạch, không write thêm attachment đó |
| Race lookup BEFORE | FAIL 1/25 như dự báo | Test mới đi qua chính nhánh resolve-device với danh sách rỗng sau native cuối; handoff chưa nhận typed cause |
| Race lookup AFTER | PASS | Dùng AttachmentChangedException hiện có; 25 OneShotBootstrap methods trong drivers suite PASS, mất thiết bị trước final/short/unknown/cancel/audit vẫn fail closed |
| JVM sau sửa | PASS 488 | Drivers 420 + app 68, failures/errors 0; không gọi 12 usbxfer + 13 service lịch sử là lượt chạy mới |
| Instrumentation sau sửa | PASS 28 + 43 | Guard/JNI/permission và library fake-only trên tablet; không chạy lại toàn bộ 120 UI tests, không xóa hai FAIL lịch sử |
| Build / lint sau sửa | PASS | Debug/release và isolated instrumentation build; lint 0 errors, debug 18/release 10 warnings; giữ Java 8/minSdk 21/toolchain |
| Static sau sửa | PASS có phạm vi | Debug đủ bốn ABI với thư viện diagnostic riêng, release không thư viện đó; zip/ELF64 16 KiB, driver bytecode major 52, không firmware/TS/trace assets |
| Phục hồi vật lý / ledger | PASS provenance | Người dùng xác nhận ngắt cả hai nguồn USB 5 giây, epoch cố định; update APK giữ hash history/preferences, không rearm |
| Firmware mới sau sửa | PASS transfer/handoff đến TRANSITIONING | Run 5fb3c6eb: 132/132 full-length, 8192 image/8196 payload, 2891 ms; attach-confirm quá thời hạn UI khiến phiên dừng sạch trước operational, không firmware retry |
| Native tablet run chính | PASS chẩn đoán / FAIL mục tiêu TS | ce9579b7: fresh operational owner, không firmware; probe/init/AUTO K36/gate lock, GET_SPEED 3, tám submit/reap return 0/errno 0, tám status -75 và actual 0 |
| Native run bổ sung cho quan sát trace | FAIL mục tiêu TS | fd30da8e: cùng profile 8 × 4096, kết quả tám status -75/actual 0; trace không cung cấp dữ liệu xHCI để tương quan |
| Cleanup hai native run | PASS | Mỗi run có 7 discard errno 22 rồi reap đủ 8, outstanding 0, delivered/cleanup bytes 0, quarantine false; không resubmit zero/error |
| Kernel / Perfetto Samsung | ACCESS_DENIED / EVENT_UNAVAILABLE_TO_PERFETTO | Sysfs giá trị/tracefs format bị chặn; FtraceStats của trace 2 giây và 20 giây liệt kê ba xHCI event unknown, không ftrace bundle/event; không chứng nhận cửa sổ trace phủ native |
| TS bytes / PAT-PMT / chất lượng | BLOCKED / NOT RUN | 0 byte Android; chưa đánh giá CRC, TEI/CC hoặc stability; counter 0 không phải quality PASS |
| API 21 / 16 KiB runtime mới | NOT RUN | Không dùng static hoặc API 36 làm bằng chứng runtime này |

Bằng chứng gốc dưới `D:\TSDuck\logs\phase31-tablet-live-20261107-230448-7cb5c880`; run chính đầy đủ `ce9579b7-c40a-4aa8-a9c7-59c433402b53`, run bổ sung `fd30da8e-e8f3-42b4-9955-7ec0acbb5749`. Cả hai terminal có state REPORT, cleanup DRAIN_CONFIRMED, `ts_smoke_pass=false`. Firmware UNCERTAIN cũ không bị sửa; không đổi mode, CLEAR_HALT, SET_INTERFACE, LNB hoặc register ngoài plan.

## 2026-11-07, 22:34 +07 — Tablet có sự kiện nhận tuner, nhưng không giữ kết nối

| Phép kiểm | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Đèn POWER | USER-REPORTED | Người dùng xác nhận đã sáng; không có phép đo điện áp hoặc độ ổn định nguồn |
| ADB và danh tính tablet | PASS số đo | SM-X926B/API 36, cùng boot; endpoint cũ offline trước khi có I/O, đã chọn endpoint mới online và xác minh lại danh tính |
| Nhận diện USB TBS5530 trong lịch sử | OBSERVED | VID:PID 734c:5530 xuất hiện lúc 22:24:38.839 +07 rồi có sự kiện ngắt lúc 22:24:55.207 +07; không suy hiện vẫn kết nối |
| Kết nối USB hiện tại | FAIL mục tiêu ổn định | Hai snapshot cách nhau ít nhất 5 giây không có tuner đang kết nối; sysfs chỉ root hubs, cổng có vai trò host |
| Firmware / DVB-T2 lock / MPEG-TS | BLOCKED / NOT RUN | Không có thiết bị USB hiện tại để mở; không firmware, vendor/I²C, tune hoặc URB |

Lệnh chỉ đọc: ADB inventory, danh tính, `dumpsys usb`, sysfs topology và Logcat giới hạn theo tag USB. Snapshot đầu trước khi người dùng cắm lại dữ liệu có `connected=false`, `data_role=no-data`; snapshot sau có vai trò host nhưng tuner không còn trong danh sách hiện tại. Log gốc lưu ngoài Git tại `D:\TSDuck\logs\tablet-power-on-recheck-20261107-221543-93025e04`. Không xác định firmware profile, USB speed hoặc nguyên nhân ngắt từ sự kiện lịch sử này.

## 2026-11-07, 21:50 +07 — Tablet sau thay adapter: kiểm nhận diện USB

HEAD `1202c8abd49a48c3099e718bead13a21a9176660`, nhánh `chore/modernize-android-build`. Chỉ đọc trạng thái tablet; không chạy lại build hoặc các suite phần mềm.

| Phép kiểm | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| ADB và danh tính | PASS | SM-X926B, API 36, arm64-v8a, kernel 6.1.145; chọn một endpoint cụ thể, không dùng thiết bị mặc định |
| Vai trò cổng USB | PASS số đo | `data_role=host`, `usb_data_status=enabled`; cờ chặn quản trị/khóa màn hình false |
| Nhận diện TBS5530 trước và sau cắm lại USB | FAIL mục tiêu nhận diện | Không có VID:PID 734c:5530 trong danh sách thiết bị; `host_manager` chỉ có `num_connects=0`, sysfs chỉ root hubs |
| Đèn POWER / adapter mới | USER-REPORTED | Người dùng báo đèn không sáng; chưa xác minh nhãn, cực tính, điện áp thực hoặc dòng cấp, không kết luận phần cứng hỏng |
| DVB-T2 lock / MPEG-TS | BLOCKED / NOT RUN | Chưa nhận diện USB nên không thử firmware, init, tune hoặc nhận URB |

Lệnh thực chạy: `adb devices -l`, `adb mdns services`, các lệnh đọc `getprop`, `uname -r`, `dumpsys usb`, sysfs topology, Logcat có giới hạn và Android CLI layout. Bằng chứng riêng tại `D:\TSDuck\logs\tablet-adapter-recheck-20261107-215018-04c39602`; không đưa serial/IP hoặc raw logs vào Git. Không thay USB role, policy, driver, nguồn hoặc ledger.

## 2026-11-07 — Phase 3.1 continuation: native completion và giới hạn đối chứng tablet

Baseline HEAD `1202c8abd49a48c3099e718bead13a21a9176660`; final source fingerprint `9adfdc4fe371f77c0d2a633eee8d99e9c851632c4e51fbd0f82a284fab3c2477`. APK đã đo native SHA `c2a9cb7c6e61bb9060319137ff4e0aee03f857577a6dad4f60aa12e156b4c9f5`; final debug SHA `ea6a48ac85e653faf2c6e4112784d32e8bf31e4f2a931cbc41384b1fc7b38ae0`, release SHA `59cb131b6cb4fad994537f6758d4dd582527e7af0f020d7a239d1ae59f42ca9f`. Không gán hardware cũ cho final APK.

| Gate / phép thử | Kết quả thực | Phạm vi và bằng chứng |
| --- | --- | --- |
| Gate A Windows offline | PASS xác minh, không phải TS sạch | 203 artifact khớp hash; submit IN82 đủ 27 byte không có requested length; 5962 completion actual 8192, peak 8 |
| Kernel capability OnePlus | PARTIAL | Kernel Logcat/topology đọc được; dmesg/sysfs giá trị/tracefs format ACCESS_DENIED; xHCI event dirs có; usbmon không thấy |
| Perfetto xHCI có nhóm | FAIL capture event | CLI exit 0 nhưng `Bad event name`, data source setup FAIL, 638 byte; không biến thành PASS |
| Perfetto tên không nhóm | UNVERIFIED event | 2 giây, 1274 byte, ftrace bật/tắt; chưa chứng minh USB event ghi được |
| Native C core | PASS | GCC 16.2 fault injection/canary; MSVC 14.29 ASan actual core PASS; rerun binary ASan exit 0, không sanitizer diagnostic |
| Full JVM final | PASS 511 | App 68 + drivers 418 + usbxfer 12 + service 13, failures/errors 0; gồm status-vs-errno, bounds/lifetime, permission/preflight/lifecycle/host |
| Build final | PASS | Debug/release linked bốn ABI, isolated test compile; log final build và isolated ngoài Git |
| Lint final | PASS với cảnh báo | 0 errors; debug 18/release 10 warnings, tăng debug text-I18n từ Activity preflight, không suppress |
| Helper offline | PASS | Negative grants/profile/epoch/terminal; 18 semantic UI synthetic; quality gate BEFORE FAIL, AFTER PASS |
| App instrumentation OnePlus | PASS 119 | Bản trước lifecycle/allowlist cuối; 5 JNI actual load/invalid input, 10 permission tests, budget refusal không consume; fake-only |
| Library instrumentation OnePlus | PASS 43 | Không USB thật; namespace runner riêng |
| App instrumentation tablet final | FAIL 2/120 | 118 PASS; hai UI test `tsPreparationPublishesItsOwnHashInsteadOfPreviousControlHash` và `explicitActivityIgnoresExtrasRefreshesAndCopiesSanitizedReport` fail; focus NotificationShade quan sát, targeted 2 rerun vẫn FAIL; không nới assertion |
| Library instrumentation tablet final | PASS 43 | SM-X926B/API 36, fake-only; không chứng minh tuner |
| Preflight budget runtime trước fix | FAIL software, 0 native | Chờ UI vượt budget, history đã consume; lần sau cùng epoch ATTACHMENT_ALREADY_CONSUMED; giữ history, không rearm |
| Bootstrap sau preflight fix | PASS transfer, handoff UI chưa trọn phiên | 132/132 full-length/8192 image/8196 payload, 4418 ms; clean TRANSITIONING rồi attach-confirm làm FOREGROUND_LOST; không retry firmware |
| Operational native URB OnePlus | PASS chẩn đoán / FAIL mục tiêu TS | Run d902837a-99e0-469d-9d3a-4a916a905cb3: 8 submit/8 reap, 3 status -75 + 5 status -71, actual 0; syscall errno 0; GET_SPEED 3; outstanding 0/quarantine false |
| Cleanup native | PASS | 7 discard trả EINVAL vì requests đã completed; reap xác minh toàn bộ 8, 0 cleanup bytes; không free/close khi pending |
| Tablet hardware A/B | BLOCKED trước enumerate | Host role/source có, chỉ root hubs; thông báo cần nguồn ngoài. Không firmware/control/URB tablet |
| TS structure/quality | NOT RUN trên broadcast Android | 0 bytes; không PAT/PMT/CRC/TEI/CC measurement, counter 0 không phải sạch |
| Final APK hardware / API 21 /16 KiB runtime | NOT RUN | Không lấy static hoặc fake/instrumentation API 30/36 thay bằng |

Hai benchmark riêng Phase 2.9 bị runner đầu gọi sai namespace (2/120 FAIL); sau tách đúng phạm vi Phase 3.1, 118 PASS trước bổ sung budget test và 119 PASS sau đó. Hai benchmark không bị sửa assertion và không được gọi PASS mới. Tablet mất endpoint trước install lần đầu nên chưa cài gì; đã repin mDNS đúng model/API/cùng boot trước lượt fake-only, không reconnect giữa native I/O. Package test phiên tạo đã gỡ sau kiểm; app production/stores cũ không bị clear/uninstall.

## 2026-11-07 — Phase 3.1: gate chốt source và cleanup sau hardware no-data

Final source fingerprint `3fe396af975baea76b00bc21a60c691655a080df549b11418100713f19ebd189`; debug SHA `6d04f7dee03a44cd34a92931a08d689020c9d6ed40617b3d68c58483afd21311`, release SHA `f82573c0ba46d0645eebc194c9d0c07972366819b6a4c2cd07b983e34b4d6085`. Khác APK đã chạy receive conditions ở checkpoint dưới vì thêm fail-stop không replenishment count 0; không chạy lại hardware trên patch chốt.

| Gate cuối thực chạy | Kết quả | Evidence / giới hạn |
| --- | --- | --- |
| Full JVM | PASS 430 methods | Drivers 413 + usbxfer 1 + dvbservice 13 + app 3, failures/errors 0 |
| Debug/release/lint | PASS | Wrapper 1 phút, lint 0 errors/13 warnings; không suppress/nâng toolchain |
| Isolated instrumentation build | PASS | Wrapper 1 phút 13 giây, namespace `.phase31offline` đúng hợp đồng test |
| App instrumentation | PASS 97 / 22,22 giây | Fake-only, gồm legacy/manual/session/epoch/native fd âm và report; không USB thật |
| Library instrumentation | PASS 43 / 1,7 giây | Fake full path và forwarding; không suy runtime API 21/16 KiB |
| Static | PASS có phạm vi | Bốn ABI, JNI/read speed, ELF64 16 KiB/zipalign, min 21/target 37, major 52 debug 177/release 84, release isolation và không firmware/TS/trace/private audit |
| Zero-completion no resubmit | BEFORE fake FAIL, AFTER PASS | 14 queue tests cuối; runtime audit cũ 9 submit giữ nguyên, hardware sau patch NOT RUN |
| Cleanup Windows unit tests | FAIL transient sharing violation rồi PASS sau bounded cleanup | FilesystemException xác nhận directory đang được giữ; chỉ cleanup test thử tối đa 10 lần/450 ms rồi vẫn throw, không retry USB hoặc thay capture semantics |
| Final production install | PASS giữ dữ liệu | APK SHA trên điện thoại khớp debug chốt; sáu legacy preferences giữ nguyên, không clear/uninstall production |
| Cleanup test packages/owners | PASS | Gỡ đúng 3 package phiên tạo, không `.phase31offline`/`.phase31autooffline` còn lại; không diagnostic Activity foreground hoặc helper chạy nền |
| Android TS / chất lượng / API 21/16 KiB runtime mới | BLOCKED / NOT RUN | Hardware trước patch có 0 byte; không TS_SMOKE_PASS, không quality/stability chứng nhận |

Audit riêng của ba receive workflows trước patch ghi mỗi lượt 156 OUT/33 IN managed full-length cho frontend, cộng một stream-select; không nhầm với native-boundary counts. Firmware có hai lượt 132 full-length, chỉ lượt phục hồi có handoff sạch; một refusal native = 0 giữa hai lượt không phải upload. Báo cáo tổng hợp `D:\TSDuck\reports\phase31-closeout-20261107-163301-6dedd387.md`; OTG switch CHECKED đã được xác minh nhưng sync retest vẫn errno 75. Phase 3.1 vẫn `BLOCKED_WITH_EVIDENCE`.

## 2026-11-07 — Phase 3.1: hardware epoch, sync/queued và retest sau bật OTG

Source baseline `1ad3b251f19779450a2c5d9cffc9f37f9c9a5470`, fingerprint mã chạy `7e158a18841307cbce693e38f9b19e8ba70adda553a15138ac7f3d037c148740`; APK debug `9ecb806b6f6a04ea1ec121a6330a012b4a0ebfe8b2928d4fe67186c999afe11b`. Không nâng toolchain hoặc thay driver. Các phần dưới là checkpoint lịch sử; không dùng số test cũ làm kết quả source mới.

| Gate thực chạy | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Full JVM epoch | PASS 429 methods | Drivers 412 + usbxfer 1 + service 13 + app 3; failures/errors 0 |
| Debug/release/lint epoch | PASS | Wrapper 2 m 55 s; lint 0 errors/13 warnings; isolated build 1 m 11 s |
| App/library instrumentation epoch | PASS 97/43 | App 18,357 giây + library 1,618 giây; fake-only, gồm native GET_SPEED fd âm/không ioctl và forwarding speed giả |
| Static APK epoch | PASS có phạm vi | Bốn ABI, JNI, ELF64/zipalign 16 KiB, min 21/target 37, major 52 debug 177/release 84 classes; release SHA f82573c0ba46d0645eebc194c9d0c07972366819b6a4c2cd07b983e34b4d6085, debug-only code/capture/firmware không lọt |
| Physical recovery epoch | PASS provenance và chống reuse | Một GUID cố định cho đúng sự kiện ngắt nguồn/cắm lại người dùng đã xác nhận; cùng epoch không upload lần hai, legacy history giữ nguyên và chỉ append |
| Firmware phục hồi, handoff thật | PASS transfer/handoff, không phải TS | Native 132/132 full-length, 8192 image/8196 payload, duration 4706 ms, TRANSITIONING/failure NONE/cleanup NONE; OS hỏi permission mới, đã cấp bằng UI và lấy owner operational mới |
| Sync IN82 sau bootstrap/init/AUTO/status | FAIL mục tiêu TS | Một 0x8A, một read 4096/500 ms = -1/errno 75; 0 byte/packet; managed read 16785000 ns, smoke 68550938 ns, DRAIN_CONFIRMED |
| Android negotiated speed | PASS số đo, không suy descriptor | USBDEVFS_GET_SPEED = 3 tại initial và operational, Linux USB_SPEED_HIGH; maxpacket 512 là quan sát descriptor riêng |
| Queued IN82 8×4096 | FAIL mục tiêu TS, status URB unknown | Fresh operational owner, không firmware upload; read 0/errno null, 0 bytes; managed read 19153906 ns/smoke 55109531 ns; audit 9 submit/9 complete/outstanding 0/released true, cleanup bytes 0 |
| Retest sau người dùng bật Lưu trữ OTG | FAIL mục tiêu TS | UI switch BẬT/CHECKED được đọc trước/sau; fresh owner không firmware, High-Speed 3, read 4096/500 = -1/errno 75, 0 byte; managed read 17883385 ns/smoke 59436927 ns; cleanup DRAIN_CONFIRMED |
| Android TS structure / chất lượng / stability | NOT RUN / BLOCKED | Không có bytes nên PAT/PMT, TEI/CC/PCR không có mẫu để đánh giá; counter 0 không phải CLEAN |
| API 21/16 KiB runtime mới | NOT RUN | Không lấy native API 30/static thay gate runtime |
| Queue zero completion replenishment | BEFORE fake FAIL, patch chốt sau hardware | Không resubmit khi count 0; không sửa audit runtime 9 submit thành 8, chưa chạy lại queued hardware sau patch |

### So sánh trước–sau bổ sung

| Vấn đề | Baseline | Sửa / đo mới | Kiểm sau | Hạn chế |
| --- | --- | --- | --- | --- |
| Firmware handoff | Full 132 nhưng generic IO trước broadcast thành UNCERTAIN | Typed presence/permission + external observation sạch | Fake fail→pass; runtime phục hồi handoff sạch | Terminal đầu vẫn UNCERTAIN, không coi là upload lỗi Bulk |
| Attachment key reuse | Power-cycle vẫn có path/id cũ, refusal native = 0 | Epoch vật lý từ xác nhận người dùng, bound grant và append-only keys | Fake + instrumentation + runtime upload mới đúng 1 | Ba khóa history không có nghĩa ba uploads |
| Speed chỉ suy từ 512 | Sysfs không đủ quyền đọc | GET_SPEED read-only cùng fd | Đo enum 3 thật | High-Speed không loại host controller/link packetization lỗi |
| Scheduling | Sync 4K errno 75 | Một queued 8×4096 không reset/vendor mới | Runtime 0 bytes, drained | Không chứng minh queue là root cause hoặc cách chữa |
| Chế độ OTG | Người dùng báo trước đó chưa bật | UI xác nhận bật, sync retest một lần | Vẫn errno 75 | Không có đường OTG/host khác, không lặp điều kiện cũ |

Run evidence: `D:\TSDuck\logs` và `reports`, các run `phase31-android-epoch-sync-20261107-162518-3c72af72`, `phase31-android-queued-20261107-162659-029afea0`, `phase31-android-user-otg-sync-20261107-163301-6dedd387`. Tất cả helper kết thúc, owner Back/drain sạch. Sáu legacy preferences giữ nguyên; không upload firmware ở queue/OTG retests, không clear/uninstall production hoặc rearm consumed. Kết luận vẫn `BLOCKED_WITH_EVIDENCE` trong Phase 3.1.

## 2026-11-07 — Phase 3.1: guard đã PASS, firmware tự động dừng đúng khi handoff bất định

| Gate thực chạy tiếp | Kết quả | Evidence / giới hạn |
| --- | --- | --- |
| App instrumentation sau sửa heartbeat/harness | PASS 94 methods / 17,552 giây | Heartbeat phiên có trước chương trình nhưng còn mới không bị từ chối; cache cũ không che heartbeat file mới; manual không nới guard |
| Library instrumentation tương ứng | PASS 43 methods / 1,704 giây | Fake-only toàn path, không phần cứng |
| Full JVM trước firmware live đầu | PASS 421 methods | Drivers 404 + usbxfer 1 + service 13 + app capture 3; app không còn NO-SOURCE sau bổ sung capture tests |
| Capture JVM lần đầu final regression | FAIL 1/3 tại cleanup Windows, sau đó PASS 3/3 | Đổi cleanup test sang Files.delete để không che lỗi file; không sửa capture bytes hoặc giới hạn |
| Production install tại chỗ | PASS giữ stores | So SHA 6 preferences trước/sau giống nhau, không clear/uninstall app chính |
| OS USB permission | PASS qua UI hệ thống | Đã đọc đúng hộp thoại và bấm OK theo ủy quyền; không shell grant/root; metadata report cũ có dòng stale nhưng terminal permission đã cấp |
| Autonomous firmware attachment đầu | FULL-LENGTH 132, terminal UNCERTAIN | 132 guard/native start/end/full-length; 8192 image / 8196 payload; sau ordinal 132 có JAVA_IO_EXCEPTION, không raw-after, không I²C/tune/TS; cleanup DRAIN_CONFIRMED |
| Tuân thủ fail-stop sau firmware | PASS | Không retry/write trên attachment đó; người dùng phục hồi vật lý một lần, tạo attachment mới |
| Tái hiện software handoff khi permission mất sau native 132 trước broadcast | BEFORE FAIL 1/2, AFTER PASS targeted | Typed AttachmentChangedException chỉ presence/permission; cancellation/generic IO/short/audit failure vẫn không thành transition sạch; không sửa terminal runtime cũ |
| Full regression sau handoff patch | PASS build/JVM/lint | Wrapper 2 m 01 s; isolated build 1 m 09 s; static bốn ABI/ELF 16 KiB/major 52/release isolation PASS |
| Instrumentation sau handoff patch | PASS 95 app / 22,787 giây + 43 library / 2,095 giây | Fake-only; thêm test handoff clean không đồng nghĩa Android TS đã có |
| Autonomous attachment đã phục hồi | PRE-NATIVE REFUSAL | Session mới chỉ SESSION_GRANTED, raw-before có, native 0/attempted 0; ledger upload cũ còn 1 record. Đang kiểm va chạm attachment identity khi Android tái dùng path/id, không xóa/rearm ledger |
| Synchronous Bulk baseline mới / queued hardware | NOT RUN | Chưa vượt firmware/ledger gate, không có 0x8A/I²C hoặc Bulk mới; Android bytes 0/packet 0 không phải CLEAN |

Terminal redacted tại `D:\TSDuck\reports\phase31-android-handoff-sync-20261107-160808-413355f4-terminal.json`; log phiên đầu `D:\TSDuck\logs\phase31-android-sync-20261107-151003-1e02d546.txt`. Hash firmware đúng khóa; không đưa native audit, boot/serial, grant hoặc firmware vào Git. Sáu legacy preferences giữ nguyên, chỉ có marker `autonomous-v1-history` mới được tạo đúng namespace.

## 2026-11-07 — Phase 3.1: Gate A/B Windows và software trước bật phiên autonomous

Baseline Git thực `1ad3b251f19779450a2c5d9cffc9f37f9c9a5470`, worktree chưa commit; không thay toolchain. Các kết quả dưới là checkpoint trung gian, chưa thay thế final hardware gates. Raw evidence ở `D:\TSDuck` và private session files, không đưa vào Git.

| Gate mới thực chạy | Kết quả | Giới hạn / evidence |
| --- | --- | --- |
| Hai mẫu Windows offline, raw bytes + TSDuck | PASS phân tích, chất lượng có lỗi | 276.417 packet đọc đủ, 15 self-test + 410 kiểm chéo; mẫu dài TEI 5.101/223.225, sync 0; Gate A report/hash tại [RESEARCH.md](RESEARCH.md) |
| tsbitrate strict toàn file | FAIL phép ước lượng đáng tin | Cả hai báo insufficient PCR dù exit 0; wall-clock bitrate cũ NOT MEASURED |
| BDA discovery mới | PASS có phạm vi | DVB-T/T2, signal locked yes; không chứng minh mode bền hoặc Android TS |
| Windows capture thường hữu hạn | PASS nhận TS, không CLEAN | 44.999.868 byte/239.361 packet, 14 dịch vụ, TEI/sync 0, 41 CC bất thường, 1 invalid PES; process 16,406 giây |
| Windows USBPcap thường quyền | BLOCKED, sau đó tháo gỡ bằng UAC được duyệt | Access is denied; không sửa driver/filter để khắc phục |
| Windows TS + USBPcap chỉ tuner | PASS capture và cleanup, không CLEAN | High-Speed, peak 8 IRP, 5.962 successful actual 8192; requested length NOT OBSERVED, snaplen 128; TS có 41 CC bất thường/4 invalid PES |
| Queue receiver fake targeted | PASS 13 tests | Ordered/out-of-order, short, duplicate/foreign/oversize, timeout, submit/cancel failure, drain accounting, worker/clock; không USB thật |
| Parser + TS gate targeted | PASS | PAT/PMT CRC/PSI/chunk, sync-only bị từ chối, TEI được báo riêng; không coi dữ liệu synthetic là RF |
| Policy targeted JVM và app compile | PASS | Wrapper 38 giây; exact/golden plan và session guard compile, chưa thay instrumentation live |
| Full JVM checkpoint đầu | PASS 413 methods | Drivers 399 + usbxfer 1 + dvbservice 13; app unit NO-SOURCE, không gọi là app unit PASS |
| Debug/release/lint checkpoint đầu | PASS | Wrapper 2 m 05 s, lint 0 errors/13 warnings; bytecode/ABI/static PASS có phạm vi |
| Isolated library instrumentation | PASS 43 methods / 1,606 giây | Fake managers, full owner/init/tune/status/TS, PAT/PMT synthetic mới; native fd âm không ioctl tuner |
| Isolated app instrumentation lượt đầu | FAIL 6/90 | 5 mismatch namespace `.phase31autooffline` thay `.phase31offline`, 1 session-backed approval eligibility; giữ failure, đang sửa trước live |
| Autonomous hardware / queued IN82 / Android TS mới | NOT RUN | Không mở gate chỉ dựa trên build hoặc Windows PASS; cần app guard hết lỗi |
| API 21 / 16 KiB runtime mới | NOT RUN | Static alignment/API 30 không thay thế runtime gates này |

### Bảng trước–sau của thay đổi phần mềm, chưa suy ra tăng tốc USB

| Thành phần | Baseline / vấn đề | Sửa có giới hạn | Kiểm đã có | Giới hạn |
| --- | --- | --- | --- | --- |
| TsParser / TsSmoke | Ba packet sync có thể thành structure PASS dù chưa có PSI | Reassemble PSI, CRC, PAT/PMT/ES; quality riêng và raw observer | Targeted PASS, full JVM checkpoint đầu PASS | Android chưa có bytes mới; chưa đo thời gian hoặc tốc độ cải thiện |
| QueuedReceiver / debug backend | Synchronous native 4 K từng errno 75; official dùng queue | Một ứng viên 8×4096, cùng fd/worker, bounded wait/cancel/drain và audit tail bytes | 13 fake tests PASS, compile/API/static PASS | Chưa chứng minh sửa EOVERFLOW; async errno không có thì null |
| Autonomous policy/session/host | Phải hỏi UI và host mỗi ordinal/plan | Một grant bounded, exact attempts mới, ledger namespace mới, STOP/heartbeat | JVM/helper PASS; app instrumentation còn 1 lỗi thật cần sửa | Chưa cấp live grant trên mã chưa qua proof-of-guard |

### Windows so với Android tại checkpoint này

| Số đo | Windows BDA mới | Official Linux source | Android baseline lịch sử / mã mới |
| --- | --- | --- | --- |
| Mode / K36 | DVB-T/T2, lock, TS thật | Terrestrial 0x8A/0A00 | Lịch sử AUTO lock; runtime mới NOT RUN |
| Link / packet size | High-Speed được hub xác nhận; packet-level maxpacket NOT OBSERVED | Endpoint 82, buffer 4096 | Descriptor 512 không thay số đo speed; speed Android NOT MEASURED |
| Queue / transfer | Peak 8 IRP, actual 8192; requested NOT OBSERVED | 8 URB×4096 | Cũ một synchronous 4 K errno 75/0 byte; queue mới chưa hardware |
| TEI / sync / CC | Mẫu mới TEI 0/sync 0, CC 41; cũ dài TEI 2,285138% | Không phải kết quả RF | Chưa có Android packet thật; không dùng counter 0 của no-data làm CLEAN |
| Firmware / stream-select / PCR | Firmware hash NOT OBSERVED, 0x8A không thấy trong trace, PCR không đủ đáng tin | Firmware/source pinned | SHA khóa trước RAM upload; không suy Windows firmware giống Android |

## 2026-11-07 — Phase 3.1: gate cuối và hardware EOVERFLOW, chưa nhận TS

HEAD `9621a8028d2cd3e5d98652cde69ba80d27cd7ee2`, nhánh `chore/modernize-android-build`, worktree chưa commit; source fingerprint cuối `988919f6fe1c8cc070c4488c86caf50dd77cf652397221f96eab6502fb674270`. Windows PowerShell, JDK Machine 25.0.5.0.3, wrapper 9.8.2/AGP 9.5.0, SDK 37.2, NDK 30.0.16248370, CMake 4.1.2; không nâng package. Evidence đầy đủ ngoài Git trong thư mục `files` của session Copilot bắt đầu ngày 2026-11-06; các tên dưới đây không phải tệp để commit.

| Gate thực chạy trên mã cuối | Kết quả | Evidence / giới hạn |
| --- | --- | --- |
| Full JVM v4 | PASS 361 methods | Drivers 347 + usbxfer 1 + service 13, failure/error 0; `phase31-errno-regression.log`, XML module; app unit NO-SOURCE được xác nhận riêng trong `phase31-errno-app-unit.log` |
| Debug/release/lint v4 | PASS | `phase31-errno-regression.log`: BUILD SUCCESSFUL 3 m 45 s, lint 0 errors/10 warnings cũ; không suppress hoặc nâng toolchain |
| Build instrumentation cách ly | PASS | `phase31-errno-isolated-build.log`: 1 m 52 s; thêm assertion forwarding rồi build lại library trong `phase31-errno-isolated-final.log` |
| App instrumentation v4 | PASS 67 methods / 12,351 s | `phase31-errno-app-tests.log`; `.phase31offline`, storage/USB giả, native fd âm không ioctl, không production ledger |
| Library instrumentation v4 | PASS 43 methods / 1,589 s | `phase31-errno-library-tests.log`; full fake manager→owner→probe/init/manual hoặc AUTO/status/TS và errno forwarding |
| APK/JNI/bytecode tĩnh | PASS có phạm vi | `phase31-errno-static.log`: debug/release có bốn ABIs và hai JNI symbols clear/read; ELF64 alignment 16 KiB, zipalign, minSdk 21/targetSdk 37; debug 150/release 78 TBS classes major 52; không firmware/TS/capture/audit/permit hoặc lớp test Phase31 trong DEX |
| API 21 / 16 KiB runtime mới | NOT RUN | Native negative-fd chạy API 30 không thay gate API 21/16 KiB runtime; static alignment không phải runtime proof |
| Host protocol 2 | PASS scope/channel | 26 checks offline, không ADB/USB; isolated shell-clock/app-UID roundtrip có 12 gaps, max 310 ms, không USB; không phải benchmark native transport |
| Firmware mới từ FX2_DEFAULT | PASS full-length, terminal UNCERTAIN giữ nguyên | Một upload 132/132 A0, image 8192/payload 8196 byte; auto-observation UNCERTAIN/JAVA_IO_EXCEPTION/NOT_OBSERVED; permission mới và observation độc lập xác nhận operational 50 byte, không sửa terminal cũ |
| Probe/init/AUTO/status thật | PASS lock, 5 workflows | Mỗi fresh owner có ID 0x197; OUT/IN: probe 3/2, init 65/14, AUTO 84/15, status 2/1; mẫu đầu syncState=6/tsLock=true/unlockDetected=false; không biết trước PLP số, không scan |
| TS Java 16 KiB rồi Java 4 KiB | FAIL mục tiêu TS, terminal NO_DATA_OR_USB_FAILURE | Hai approvals, hai stream-select full-length, mỗi lượt một read=-1/0 byte; chưa có errno, không tự gọi timeout |
| TS Java 4 KiB + CLEAR_HALT | FAIL mục tiêu TS | Approval clear riêng, clear hoàn tất, một stream-select/read=-1/0 byte; `phase31-fourth-endpoint-TS_SMOKE-result-private.json` |
| TS native 4 KiB + CLEAR_HALT v4 | FAIL mục tiêu TS, errno đã đo | Approval workflow/clear mới, một read=-1/errno=75, 0 byte, timeout 500 ms; managed read 13419167 ns/smoke 62119843 ns; `phase31-fifth-errno-TS_SMOKE-result-private.json` |
| MPEG-TS parser trên broadcast thật | NOT RUN do không có dữ liệu | Tổng bốn reads, 0 bytes/packets; sync loss/TEI/continuity bằng 0 chỉ vì chưa nhận data, không phải TS PASS |
| L1/selected PLP/Multi/Common đầy đủ | UNVERIFIED | Không exact-chip monitor allowlist, không gán PLP 0/1/2 từ QCVN; fixtures vẫn fake-only |
| GitNexus / đồ thị phụ thuộc | NOT VERIFIED / NOT USED | Status CLI 1.6.12 không hoàn tất, không reindex/repair; source/diff/tests là evidence độc lập |

Tổng chênh lệch control checked của năm workflow là 776 OUT/160 IN; cộng firmware 132 OUT thành 908 OUT/160 IN. Bốn stream-select và hai clear đã tính trong OUT; bốn Bulk calls là lượt nhận dữ liệu riêng, không phải register IN. Không tính descriptor reads hoặc host-only attempts. Scope/status/TS terminal cũ vẫn giữ; không biến `failure=NONE` của report thành TS PASS khi `state=NO_DATA_OR_USB_FAILURE`.

### Bảng trước/sau bổ sung sau các lượt live

| Blocker / lỗi thật | Root cause hoặc mức chắc chắn | File/hàm sửa | Tái hiện trước sửa | Kiểm sau sửa | Hardware / hiệu năng / rủi ro |
| --- | --- | --- | --- | --- | --- |
| Host permit biến mất trước rename | AtomicFile dùng `.json.new` làm tệp nội bộ và có thể xóa nó khi đọc | Helper riêng `Watch-Phase31-Operational.ps1`, đổi temp sang `.tmp` | Host consumed/stop trước app execute; instrumentation tái hiện collision | PASS AtomicFile regression và channel live; owner/permit mới mới được chạy | Không I²C từ attempt lỗi; không tái dùng permit, không sửa ledger cũ |
| TS report mang hash của status trước | UI không cập nhật lastPlanHash khi prepare TS | `OperationalDiagnosticActivity` | Fake UI ComparisonFailure trong `phase31-ts-hash-before.log` | PASS hash UI/approval và full isolated instrumentation | Native scope cũ đúng; báo cáo cũ đối chiếu bằng audit CONSUMED và OUT delta, không sửa evidence |
| Endpoint preparation mất qua strict wrapper | Thiếu forwarding capability trong binding | `Tbs5530UsbBinding` và full fake path | Library fake FAIL trước forwarding | PASS library 43 gồm clear tùy chọn và errno end-to-end | Clear thật hoàn tất nhưng không chữa Bulk; không gọi đây là transport fix |
| Java chỉ trả -1 không phân biệt lỗi | API không cung cấp errno trong result | JNI `jni_readBulk82`, `UsbHiSpeedBulk`, facade/binding/handoff/coordinator/BulkTransport/TsSmoke/report | Ba TS attempts chỉ có -1; không có test FAIL giả tạo cho feature chưa tồn tại | PASS native fd âm/errno 22, fake -1/errno 32/no retry/stale và full path; v4 thật đo errno 75 | Chẩn đoán đã rõ hơn, chưa sửa root cause EOVERFLOW; không speedup |
| Đổi chunk/clear được đề nghị như ứng viên | Khác biệt source, chưa biết nguyên nhân hardware | TS limits/transcript v2→v3→v4 | Java 16384/4096 đều lỗi | Software guards PASS; hardware clear và native vẫn lỗi | Không tuyên bố giảm chunk/clear đã thành công, không có paired benchmark |

Kết luận `PHASE_3_1_BLOCKED_WITH_EVIDENCE` tại đường USB, giữ `DVBT2_LOCK_CONFIRMED`. Một phép đối chứng được đề nghị, chưa chạy: chính TBS5530 trên Windows driver hãng/cùng anten K36 594 MHz/8 MHz để xem có TS hay không. Không tự chuyển ownership, reset hoặc consume approval mới. Lệnh và cleanup cuối tại [WORKLOG.md](WORKLOG.md); giải thích errno tại [RESEARCH.md](RESEARCH.md), ADR-027 tại [DECISIONS.md](DECISIONS.md).

## 2026-11-06 — Phase 3.1: PLP và đường thu có kiểm soát, chỉ kiểm USB giả

Baseline `9621a8028d2cd3e5d98652cde69ba80d27cd7ee2`, nhánh `chore/modernize-android-build`; kết quả dưới đây áp dụng worktree chưa commit, fingerprint nguồn SHA-256 `078edc85048dfc147d268a8b802924dbe296856abe62890ad86abbba2e38d637`. Môi trường Windows/PowerShell, wrapper Gradle 9.8.2, AGP 9.5.0, JDK 25.0.5.0.3, SDK 37.2; không nâng package. Evidence riêng nằm trong thư mục `files` của session Copilot hiện tại ngoài Git, các tên log dưới đây không chứa identity/permit thật.

| Gate mới | Kết quả | Bằng chứng và giới hạn |
| --- | --- | --- |
| Git preflight | PASS | HEAD bằng baseline, branch/origin đúng, worktree/stage sạch lúc đầu; không tự Git write hoặc sync reference |
| Matrix nguồn PLP | PASS đối chiếu source | Manual đúng CXD6802; AUTO AD=0 trên CXD2841ER/CXD2820R/CXD2880 chỉ CROSS-CHIP-ANALOGY; monitor CXD6802 UNVERIFIED và runtime chặn |
| QCVN 2020 / 2012 | PASS có giới hạn nguồn | Thông báo Bộ xác nhận thay thế/hiệu lực 01/07/2021; HTML 2020 kiểm được yêu cầu và bảng A.2/A.3/A.4. VSQI disabled, DOC 2012 fetch lỗi, attachment PDF không đọc được bởi model; không xác minh trang ảnh hoặc tuyên bố hợp quy |
| Focused lần đầu | FAIL do fixture, đã sửa | 40 methods, 5 failures: golden tuner còn cố định 666 MHz khi thêm K36; fake test kỳ vọng drain mà chưa yêu cầu close. Sửa fixture có byte K36 độc lập và đóng session giả; không đổi register hardware để làm test xanh |
| Full JVM cuối | PASS 355 methods | Drivers 341 + usbxfer 1 + dvbservice 13; app unit NO-SOURCE. Không failure/error/skipped. `phase31-final-regression.log`, XML tại các module build/test-results |
| Golden và fault injection | PASS trong full JVM | Manual 0/1/42/254/255, từ chối -1/256/null; AUTO không AF/FF, AD=0; manual retune AF/id/AD=1; toàn bộ phần tune còn lại không đổi; hash/mode/evidence, sai silicon, epoch/consume/cancel và 99 biên native short của AUTO |
| Synthetic QCVN | PASS fake-only, không phải bài đo hợp quy | SYNTHETIC_TEST_FIXTURE: Single ID 42, Multi Common 0/data 1,2, Multi không Common, data khác Common, cấu hình thay đổi; MONITOR_RESOLVED chưa hỗ trợ bị chặn; không dùng băng chiếm dụng thay 8 MHz |
| Fake full path JVM và Android | PASS | Cùng owner/session/coordinator/control gate, probe → init → manual hoặc AUTO → status → TS giả. Android fake raw có đúng 187/188 control calls cho AUTO/manual, một fake Bulk; không gọi A0 hoặc USB thật |
| Build debug/release và lint cuối | PASS | `phase31-final-regression.log`: BUILD SUCCESSFUL 2 m 11 s; lint 0 errors/10 warnings cũ. Lượt trung gian 13 warnings được sửa ba SetTextI18n mới bằng debug resources, không suppress toàn dự án |
| Build instrumentation cách ly | PASS | `phase31-final-isolated-build.log`: BUILD SUCCESSFUL 1 m 17 s; init script ngoài Git, app `.phase31offline`, app test `.phase31offline.test`, library test `drivers.phase31offline.test` tự target theo AAPT |
| App instrumentation cuối | PASS 61 methods / 11,247 s | `phase31-final-app-instrumentation.log`; approval deterministic/storage/fault/report/UI, boot/owner/hash/max budget, consent AUTO riêng, cursor InputConnection và pause/drain/reconnect/RF reset; không dùng production stores |
| Library instrumentation cuối | PASS 43 methods / 1,675 s | `phase31-final-drivers-instrumentation.log`; toàn bộ library androidTest dùng manager/raw giả, bao gồm owner mới và full path; không thật sự nạp firmware hoặc khóa RF |
| App instrumentation trung gian | FAIL 3/61 → PASS 61/61 | Ba test durability còn assert package Phase 2.9; mở đúng hai namespace cách ly Phase 2.9/3.1 trong test, vẫn từ chối production. Lần FAIL giữ `phase31-app-instrumentation.log`, không gán PASS ngược lịch sử |
| Bytecode và APK | PASS static | 148 TBS main classes major 52; minSdk 21/target 37; debug 5 DEX/release 2 DEX không có Phase31 test harness; operational debug Activity vắng release; firmware/TS/capture entries 0; zipalign 16 KiB PASS cả hai |
| Latest stable / toolchain audit script | NOT RUN / BLOCKED có phạm vi | Script kiểm kê bị ExecutionPolicy chặn; không Bypass hoặc đổi policy. Đã đọc trực tiếp build declarations/JDK/SDK source.properties; không kiểm latest stable và không cài/gỡ/nâng package |
| GitNexus | NOT VERIFIED | CLI 1.6.12 status không hoàn tất trong cửa sổ chờ, đã dừng đúng process tree; không query graph, reindex hoặc repair FTS; source/diff/tests làm bằng chứng độc lập |
| Host provisioning protocol 2 thật | NOT RUN | Đã kiểm app bằng storage/clock giả; trước USB thật phải kiểm helper giữ boot/owner/plan/budget/heartbeat và consume. Không dùng script firmware Phase 2.9 cấp control |
| Native API 21 / 16 KiB runtime mới, host benchmark | NOT RUN | Không đổi JNI/toolchain; static alignment không thay runtime. Không báo speedup hoặc paired latency từ tests giả |
| Firmware / chip-ID / init / tune / lock / TS thật mới | NOT RUN, chưa có approval | 0 A0, 0 vendor/I²C, 0 tune/poll/0x8A/Bulk thật. ID/init Phase 3.0 chỉ là lịch sử; chưa DVBT2_LOCK_CONFIRMED/TS_SMOKE_PASS thật |

### Bảng trước/sau lỗi và thay đổi trong phạm vi

| Blocker hoặc lỗi ban đầu | Root cause / nguồn | File hoặc hàm sửa | Test trước sửa | Test sau sửa | Hiệu năng / hardware / rủi ro còn lại |
| --- | --- | --- | --- | --- | --- |
| Không biểu diễn AUTO an toàn | Constructor int từ chối -1; official chỉ manual, không được encode sentinel | `Tbs5530DvbT2TuneParameters.Selection`, `Frontend.planTune`, `Sequences.tune`, `ControlProbe` | Hành vi từ chối được xác nhận source và test cũ; chưa có hardware tune | PASS golden/manual/auto diff và fake full path | Không tăng tốc đã đo; AUTO vẫn thử nghiệm chờ consent/silicon evidence |
| Sync 7 có TS bit bị coi locked | Điều kiện cũ `>=6` có thể cấp stream khi sync chưa hợp lệ; chọn guard bảo thủ, không tuyên bố sửa silicon semantics | `Frontend.Status` | FAIL 1/1, `phase31-before.log` | PASS test riêng và 256 raw bytes trong full JVM | Không có RF test mới; metrics vẫn NOT_AVAILABLE |
| Audit tiêu hết thời gian nhưng Bulk vẫn bắt đầu | Timeout tính trước audit/storage | `TsSmoke.run` | FAIL test mới, `phase31-focused2.log` (40 test khác PASS) | PASS trong full JVM | NOT MEASURED; không nới timeout, không force-close call |
| TS hợp lệ trả quá hạn vẫn được PASS | Chỉ kiểm cấu trúc, thiếu kiểm duration khi publish | `TsSmoke.run` | FAIL 1/8, `phase31-ts-late-before.log` | PASS 8 smoke tests và full JVM | LIMIT_REACHED không bị nhầm INVALID_TS; OS vẫn có thể trả muộn |
| Bắt đầu chương trình dài sát hết permit | Eligibility cũ không chừa bound+margin; scope chưa gắn owner hash/bound host duyệt | `OperationalApproval` protocol 2 | Source-confirmed; không chạy lại code cũ trên hardware | PASS biên maximum+4999/+5000 ms, owner/hash/protocol/consent/consume và heartbeat tests | Chưa benchmark host thật; margin 5000 ms là safety reserve, không số đo latency |
| Quay lại Activity giữ đối tượng owner đã revoke | onPause hủy quyền nhưng không bỏ pipeline cũ | `OperationalDiagnosticActivity.retireOwner` và `OperationalHandoff.approvalIdentity` | Source-confirmed; không có lượt FAIL UI trước sửa | PASS pause/drain/resume với owner giả, nonce mới dù generation/path lặp; InputConnection giữ text/cursor | Cleanup đợi native đồng bộ; chưa kiểm pause khi có tuner thật |

Lệnh chính xác và danh sách test selector ghi tại [WORKLOG.md](WORKLOG.md). Mọi số native trong fake tests là kiểm phần mềm, không được cộng vào USB hardware counters hoặc suy tín hiệu on-air.

## 2026-11-06 17:57 +07 — Kiểm chứng tài liệu tần số từ ảnh Công báo

| Gate tài liệu | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| K36 frequency/channel width | PASS đối chiếu ảnh, không phải hardware test | Điều 3 khoản 1: 590–598 MHz, trung tâm 594 MHz; rộng 8 MHz. Giữ frequency 594000000 Hz/bandwidth 8000000 Hz |
| Quy hoạch khu vực | PASS đối chiếu ảnh | Điều 3 khoản 2 điểm d: K36/K37 cho Nam Trung Bộ và Tây Nguyên; không chứng minh riêng SDTV đang on-air |
| PLP / tune / lock / TS | PLP UNVERIFIED; hardware NOT RUN | Bốn ảnh không nêu PLP; không gửi thêm USB control hoặc Bulk, không gán NO_SIGNAL/NO_LOCK |

Không chạy lại build/tests vì bổ sung này chỉ ghi tài liệu. Các kết quả phần mềm và hardware bên dưới giữ nguyên phạm vi đã chạy.

## 2026-11-06 — Phase 3.0 live: firmware + operational + chip-ID + init; chưa tune/lock/TS

| Gate live cuối | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Transaction firmware mới độc lập sau pre-native stop | PASS upload completion | 132 guard/native started/completed/full-length, image 8192/payload 8196 byte, release 131/132 đều 1/1; duration runner 108499 ms |
| Auto-observation sau upload | UNCERTAIN, giữ nguyên terminal | Attachment event 1, JAVA_IO_EXCEPTION, after NOT_OBSERVED, UPLOAD_COMPLETE_OPERATIONAL_UNVERIFIED; pending false/cleanup NONE, không retry |
| A: permission + raw mới riêng | PASS OPERATIONAL_CANDIDATE | User tự grant, raw complete 50 byte/hash 22e2…add5, config 1/interface 0/alt 0/0x82+0x81 IN bulk 512; 26 ms, open/read/close 1/1/1, claim/release/vendor 0 |
| C: strict operational owner | PASS acquisition | Một owner mới, fresh raw + metadata và claim(false), không A0; native-boundary open/claim counters chưa được đo riêng |
| C: allowlisted chip-ID | PASS DEMOD_IDENTIFIED | Plan fc68…a0d9, target 0x66/bank 00/FB/FD, 3 OUT/2 IN full-length, ID 0x197; audit 10 events, interval 76 ms gồm audit/checks |
| D: frontend initialization riêng | PASS FRONTEND_INITIALIZED/READY_FOR_TUNE | Plan 6179…82da, 65 OUT/14 IN full-length, tuner E-family/boot/parallel TS checks đạt; audit 158 events, interval 1418 ms gồm source delay/audit/điều phối |
| RF IN/coax sau init | USER_CONFIRMED | Coax thụ động an toàn, không DC ngoài, đúng RF IN không LNB IN, anten từng thu tốt; không phải số đo RF hiện tại |
| K36 candidate | SOURCE-CHECKED, chưa đo on-air | SDTV công bố Vũng Chua/K36 và bản đồ liệt kê Bình Định/K36; ứng viên 594 MHz/8 MHz. PLP chưa xác minh; Công báo 2026 là quy hoạch, không phải measurement |
| Auto PLP / no-filter | UNSUPPORTED/UNVERIFIED | Ba nguồn đã kiểm chỉ có manual bank 23/AF/AD; không dùng sentinel -1 thành PLP 255 hoặc mặc định PLP 0 |
| Tune / lock polling | NOT RUN — ENVIRONMENT BLOCKER | Chưa có PLP hợp lệ hoặc cơ chế auto được xác minh; 0 tune/status. Không ghi NO_SIGNAL/NO_LOCK hay tuner failure |
| TS select / Bulk / parser live | NOT RUN | 0 request 0x8A, 0 Bulk; parser software PASS không thay thế TS live |
| ACK firmware mới, N=132 | PASS dưới 1500 ms | p50/p95/p99/max 783/1012/1064/1098 ms, chỉ event android_observed của đúng run |
| Native API firmware, N=132 | RUNTIME-OBSERVED | p50/p95/p99/max 0,293229/0,446302/1,076823/1,524583 ms, tổng 42,521095 ms; không gọi là bridge/init latency |
| Guard/ledger firmware | PASS prefix integrity | 545 records/182748 byte; prefix 92018 và 93760 byte khớp SHA cũ; terminal của attempt trước và upload unverified không sửa. C/D dùng namespace riêng |
| Static cuối | PASS | 145 TBS main classes major 52, minSdk 21/target 37, debug operational Activity vắng release, firmware/TS/capture entries 0, zipalign cả debug/release đạt |
| Cleanup cuối | UI/helper đã dừng; count native chưa đo riêng | Activity resumed trở về launcher, mọi helper bounded đã kết thúc; không force-close khi native chạy. Không gán release/close count từ fake thành hardware measurement |

**Tổng giao dịch mới có vendor:** một firmware upload hoàn tất 132 A0 (reservation trước đó 0 native), C có 3 OUT/2 IN, D có 65 OUT/14 IN. Tổng OUT/IN là 200/16; 0 tune, 0 status polling, 0 stream-select, 0 Bulk và 0 satellite/LNB/EEPROM. Gate cao nhất là frontend initialized, không phải DVB-T2 locked hoặc TS_SMOKE_PASS.

## 2026-11-06 — Phase 3.0: gates phần mềm và cold-plug mới

| Gate | Kết quả mới | Phạm vi / giới hạn |
| --- | --- | --- |
| Git/toolchain baseline | PASS kiểm thực | HEAD 722c4916, worktree sạch khi bắt đầu; audit không đổi gói sau approval Bypass tiến trình riêng |
| Focused handoff/profile/bulk/parser/smoke | PASS 89 methods / 1,518 giây | `javac --release 8` + JUnitCore, fake-only; chưa có frontend mới tại lượt này |
| Frontend source/golden/fault fixtures | PASS 26 methods | Gradle focused FrontendTest; source-matched plans, ID/junk/variant, tune/BW/PLP, cancellation và lỗi giữa sequence; không phần cứng |
| Exact-plan probe/RF/TS focused | PASS 15 methods | ControlProbeTest 4 + OperationalProfileTest 5 + TsSmokeTest 6, sau khi nối gate debug; không live I²C |
| Full JVM cuối | PASS 343 methods | Drivers 329 + usbxfer 1 + dvbservice 13, 0 failure/error/skipped; app unit NO-SOURCE, không đếm thành test PASS |
| Debug/release APK và lint cuối | PASS | Sau sửa nhãn debug, wrapper build PASS; lint 0 errors/10 warnings cũ. Không suppress lint toàn dự án hoặc tạo baseline |
| Lint trung gian | FAIL → sửa → PASS | String resource RF mới thiếu bản dịch nb; đánh dấu nhãn vận hành debug tiếng Việt `translatable=false`, build/lint lại. Không ghi đè lịch sử FAIL |
| App fake instrumentation cuối | PASS 32 methods / 4,878 giây | 24 approval deterministic (có test quỹ bắt đầu 60000/60001 ms) +8 Phase 3.0 approval/UI; package riêng, không preferences thật |
| Library fake instrumentation cuối | PASS 32 methods / 1,444 giây | 28 AndroidUsbFacade +4 OperationalHandoff; Context/receiver/worker thật nhưng Manager/RawConnection giả, USB thật 0 |
| Test namespace | PASS AAPT/package/targetPackage | App `.phase30offline`, app test `.phase30offline.test`, library test `drivers.phase30offline.test` tự target; không cài test vào production package |
| Production APK update | PASS giữ data | User approval riêng, APK SHA `5965aced4de6c56efad951586f892dc07b4cc5d17915e64b9526d2ed711a7a36`; toàn bộ Phase 2.9 consumed XML hash so trước/sau giống hệt |
| Cold-plug descriptor-only thật | PASS FX2_DEFAULT | User xác nhận OTG và tự grant permission; raw 189 byte/hash e288…52de, COMPLETE/MATCH, 24 ms, open/read/close 1/1/1, claim/release/transfer 0/0/0; pending/cleanup false |
| Approval bootstrap đầu Phase 3.0 | FAULTED/PRE_NATIVE_STOP | GUID mới consumed ở host; reserve thiếu vì khoảng 73,7 giây từ prepare tới start. 0 A0/native/image bytes; raw preflight 189 byte; after NOT_OBSERVED, runner 49 ms, cleanup NONE/pending false |
| Bảo toàn attempt đầu / ledger | PASS kiểm history | Terminal riêng giữ nguyên, ledger từ 275 lên 278 records; không reuse GUID hoặc sửa cờ legacy. Approval upload tiếp theo là độc lập, kết quả phải ghi riêng |
| Live bridge/I²C ID/init/tune/lock/TS | NOT RUN tại checkpoint phần mềm này | Không suy từ fake PASS hoặc firmware; phải qua approval/gate tương ứng |
| RF IN/coax | ANTENNA_CONNECTION_UNCONFIRMED | Chỉ hỏi kết nối vào RF IN, không LNB IN, khi tới gate lock. Không đo hoặc gán NO_SIGNAL |
| API 21/16 KiB runtime/native smoke mới | NOT RUN | minSdk/bytecode/static APK kiểm riêng; toolchain/JNI không đổi, không dùng OnePlus/API 30 làm bằng chứng API 21 hoặc 16 KiB |

Các lượt instrumentation lặp lại không cộng vào tổng methods. Gradle focused đầu phiên từng FAIL vì agent frontend đang ghi file và class Sequences chưa tồn tại; chỉ dùng lượt compile/full gate sau hoàn tất. Kết quả firmware đang chạy sau approval độc lập được ghi ở checkpoint tiếp, không lấy trạng thái LOADING làm PASS.

## 2026-11-06 — Gate phần cứng cuối: OPERATIONAL_CANDIDATE

| Gate cuối | Kết quả mới | Evidence / giới hạn |
| --- | --- | --- |
| Approval thực | 3 transactions được duyệt độc lập | Hai reservations đầu consumed ở host nhưng native mới 0; transaction thứ ba có upload thật. Không reuse GUID, không retry tự động |
| Firmware size/hash/plan | PASS preflight trước write | 8192 byte/SHA b97c…d0dc, TBS_SOURCE_DUAL_RESET, 132 A0 OUT/8196 payload, timeout USB 2000 ms; không fallback hoặc nhúng firmware |
| Native call boundaries thật | PASS 132/132 full-length | Planned/guard requested/accepted/native started/completed/full-length 132, image 8192/payload 8196, 132 API result records; 0 negative/short/exception |
| Final release | PASS full-length cả hai | Ordinal 131 7F92=00 và 132 E600=00 trả 1/1 byte; source delay 100 ms, có attachment event sát final release. Không corrective write |
| Native-only latency | RUNTIME-OBSERVED N=132 | p50/p95/p99/max 0,304/0,466/0,947/6,155 ms, tổng 48,664 ms; Android boundary telemetry, không bus capture |
| ACK actual | PASS 132 fresh ACK/0 timeout | p50/p95/p99/max 738/1001/1046/1067 ms, request→native max 1089,356 ms; giữ deadline 1500 ms |
| Upload cleanup / auto observation | Upload cleanup PASS, observation ban đầu FAIL | Pending false/cleanup NONE; snapshot UNCERTAIN/JAVA_IO_EXCEPTION/epoch 1/after NOT_OBSERVED, attachment 1. Không sửa thành auto-observation PASS |
| Observation riêng đầu sau user approval | PASS raw read, classifier cũ UNKNOWN | Raw 50 byte/no issues/hash 22e2…add5, EP 0x82/0x81 bulk IN 512, bInterval của 0x82 là 1; permission/owner mới, open/read/close 1/1/1, transfer 0. Chưa lưu raw binary trong port cũ |
| Classifier bulk IN interval | FAIL repro → PASS sau fix | USB reference xác nhận bInterval không dùng polling cho bulk; debug bỏ đúng interval=0 gate, negative address/type/packet/alt giữ; main transport không nới |
| Observation cuối được duyệt riêng | PASS OPERATIONAL_CANDIDATE | 00:32:55 +07, raw 50 byte COMPLETE/no issues/hash 22e2…add5, config 1/interface 0/alt 0/EP 0x82+0x81 IN bulk 512; 29 ms, open/read/close 1/1/1, claim/release/transfer 0/0/0, pending/cleanup false |
| Actual raw binary | PASS save/export/hash/length | 50 byte thật được port checked-sync lưu private, SHA khớp report; không reconstruction hoặc Git artifact raw |
| Final JVM/build/lint | PASS 259 methods/build debug/release/lint | 245 drivers +1 usbxfer +13 service, 0 failure/error/skipped; 2 phút 45 giây/357 tasks, 0 errors/10 warnings |
| Final instrumentation | PASS 86 methods có phạm vi | App 47/9,315 giây +library 37/1,398 giây rerun sau observation changes; service 2 PASS trước đó không đổi nguồn. Không tính hardware transaction thành test methods |
| Final APK/static | PASS | 127 TBS classes major 52, minSdk 21/targetSdk 37, debug-only uploader/raw port, harness vắng debug/release, firmware entries 0, zipalign cả hai exit 0; APK hash 12a9…526e |
| Guard/ledger | PASS giữ cả history và consumed | Legacy/current upload store true so nguyên sau update; canonical 275 records/92018 bytes, 2 failures +1 terminal/3 new consumed, không sửa terminal cũ theo observation riêng |
| Frontend/I²C/tune/PLP/DVB lock/TS/EEPROM/flash | NOT RUN | Ngoài phạm vi; OPERATIONAL_CANDIDATE không phải READY/streaming hoặc bảo đảm mọi firmware functions |
| Recovery/retry/Phase 3 | NOT RUN | Không CPU reset/power-cycle/recovery, không upload thứ hai trong cùng approval hoặc chuyển phase |

**Kết luận:** Firmware bring-up gate của Phase 2.9 đã đạt trên board/OnePlus cụ thể bằng 132 full-length và operational raw mới. Trạng thái cuối OPERATIONAL_CANDIDATE, ready=false; report upload cũ và ledger terminal vẫn UPLOAD_COMPLETE_OPERATIONAL_UNVERIFIED vì observation được duyệt riêng sau đó. Giữ mọi FAIL/NOT RUN lịch sử phía dưới.

## Final UID-private/no-clamp/legacy UI-wait gate — 2026-11-05

| Gate hiện tại | Kết quả | Phạm vi / giới hạn |
| --- | --- | --- |
| No-clamp source/mock | PASS 61 checks | Sample sau app-file read, host_seen=raw NOW, PUT chờ tick thật tối đa 2×10 ms trong deadline hoặc deny; không Max/clamp/created fallback |
| Live private UID, verified 1 | PASS 132/132 | ACK 505/588/625/626 ms, total Android 71.721 ms, dispatch max 646,489 ms |
| Live private UID, verified 2 | PASS 132/132 | ACK 524/603/854/911 ms, total Android 78.765 ms, dispatch max 933,022 ms |
| Live private UID, verified 3 | PASS 132/132 | ACK 543/625/662/670 ms, total Android 80.427 ms, dispatch max 689,997 ms |
| Same-UID baseline callback cũ, đo sau | PASS 132/132 | ACK 536/632/646/760 ms, total Android 80.026 ms, audit 2.184,238 ms |
| Verified aggregate | PASS 396 samples, 0 timeout | Nearest-rank 526/606/669/911 ms, không gồm baseline/FAIL; tail 120–132 max 570/669/581 ms |
| Same production privilege/file path | PASS runtime | Controller shell clock/boot, file I/O run-as đúng offline app-private; seed legacy request/permit, chờ grant mới rồi 3500 ms trước owner |
| Fake boundaries mỗi lượt | PASS | Full-length 132, audit 264/6.711.056 bytes, raw files/reads 2/2, real USB 0; ADB helper 3, run-as dispatch 281/284/282 không phải OS forks |
| App regression sau parity | PASS 46 methods | 9,12 giây; source Java production không đổi từ JVM 257/library 37/service 2 PASS trước đó |
| Guard/ledger/cleanup | PASS bảo toàn | Legacy consumed nguyên true; canonical 8 records hash sau 2 reservations trùng, export 34 test evidence files rồi gỡ 2 test packages; không production helper/GUID thứ ba |

Software hiện đủ để hỏi một approval độc lập mới, không đủ để gọi firmware bring-up hoặc operational descriptor PASS. ACK 1500/heartbeat 2000/USB 2000/TTL 300000/benchmark 240000 ms vẫn riêng biệt, không thay deadline giữa attempt. Các lỗi/giả thuyết/checkpoint dưới đây giữ nguyên lịch sử.

## 2026-11-05 — Approval độc lập thứ hai: tổng deadline khi heartbeat không tiến trước UI

| Probe/gate mới sau runtime failure | Kết quả | Giới hạn |
| --- | --- | --- |
| `/proc/uptime` shell UID | PASS read/parse, exit 0 | Cùng target/boot, chỉ đọc |
| `/proc/uptime` run-as production app UID | FAIL Permission denied, exit 1 | Chứng minh parity gap với benchmark shell; không thay quyền |
| Clock fault source/mock | PASS phát hiện 6 fault cases | Không còn -990/clamp-created fallback, lỗi DEVICE_CLOCK_UNAVAILABLE; 50 negatives/10 production fixtures/3500 ms wait đã PASS mock |
| Live fake legacy seed/UI-wait đầu | FAIL INVALID_REQUEST_BINDING | BenchmarkOnly classifier chưa lọc legacy trước fields; trước native/clock, evidence giữ, không ảnh hưởng hardware ledger |
| UID split live với external channel cũ | FAIL trước grant | Initial host approval missing sau 30,07 giây; run-as offline đọc external challenge bị Permission denied, shell đọc được; không USB/ledger thật |
| Offline app-private root probe | PASS read-only | run-as offline pwd đúng `/data/user/0/...phase29offline`, không permission changes |
| Private UID parity +legacy/UI-wait 3500 ms | PASS 3 ×132/132 | ACK max 668/1116/952 ms, dispatch max 688,233/1137,309/971,735 ms; fake full-length 132/raw files 2/reads 2/audit 264/USB 0 mỗi lượt; run-as dispatch 281/282/283 |
| Timestamp no-clamp gate | IN PROGRESS | Source còn POLL-before-read/host_seen max/PUT dưới 10 ms clamp; cần sample sau I/O và chờ tick thực hoặc deny, giữ deadline, rồi rerun 3 |
| Privilege split + unified classification live | PASS checkpoint có phạm vi | Shell clock, app-private files run-as hoạt động; không dùng thay no-clamp gate |

| Boundary | Kết quả | Giới hạn |
| --- | --- | --- |
| Approval/GUID độc lập mới | APPROVED/CONSUMED ở host | User physical current và UI-ready mới, không reuse GUID/flags trước |
| Fresh APK/identity/permission/raw/SAF | PASS preflight | Install-r giữ guard; cùng GM1900/API 30/boot, raw FX2_DEFAULT 189 byte/14 ms, open/read/close 1/1/1, transfer 0; file đúng hash/size |
| Initial permit | PUBLISHED v2, ordinal 0 | Đúng current run; không còn lỗi thiếu property legacy |
| Heartbeat khi chờ | FAIL runtime quan sát | Sau hơn 120 giây vẫn initial timestamp, app foreground; chưa xác nhận root cause tới khi source/mock xong |
| Helper | FAIL TOTAL_SESSION_DEADLINE 300 giây | ACK/observation 0/0; không phải deadline ACK hoặc USB timeout |
| Android upload | NOT STARTED, native mới 0 | App current per-attempt prefs/native audit 0, report raw READ_AND_CLOSED; không CPU stop/release/firmware mới |
| Bảo toàn | PASS fail-stop, ledger giữ 2 reservations | UI đóng/revoke, ledger 8 records hash-pin, legacy consumed giữ nguyên; không retry/recovery |
| Fix/gates sau sự cố | IN PROGRESS | Test seed legacy request/permit và chờ 3500 ms trước worker/native giả, host mock chờ lâu hơn heartbeat; không phải hardware approval tiếp theo |

## 2026-11-05 — Transaction mới đã duyệt, dừng ở host trước firmware

### Sau fix production leftovers và final-release suppressed audit

| Gate mã hiện tại | Kết quả | Evidence / giới hạn |
| --- | --- | --- |
| Full JVM/build/release/lint | PASS 257 methods, exit 0 | 243 drivers +1 usbxfer +13 service; 3 phút 22 giây/357 tasks; không dùng kết quả 256 trước fix làm kết quả mới |
| App/library rerun | PASS 46/37 methods | 9,129/1,426 giây, fake ports trong test namespace; service 2 PASS trước đó không đổi nguồn |
| Host fixture/mocks | PASS 45 negatives +7 production cases | 132 loops/133 exports, final 20.961 ms; stale prior frames lọc trước fields, current malformed deny trước reservation, no ADB/USB |
| Full-path baseline mới | PASS 132/132 | ACK 364/452/472/474 ms; tổng Android 55.762 ms |
| Full-path mã hiện tại 1 | PASS 132/132 | ACK 363/436/451/457 ms; tổng Android 54.124 ms |
| Full-path mã hiện tại 2 | PASS 132/132 | ACK 367/453/461/468 ms; tổng Android 54.714 ms |
| Full-path mã hiện tại 3 | PASS 132/132 | ACK 364/447/464/466 ms; tổng Android 51.845 ms |
| Ba lượt hiện tại | PASS 396 samples, 0 timeout | Nearest-rank 365/447/463/468 ms, request-start→dispatch max 494,749 ms; fake full-length 132/raw files 2/reads 2/audit 264 và USB thật 0 mỗi lượt |
| Ledger và guard sau fakes/cleanup | PASS giữ consumed | Canonical 1773 bytes/5 records, hash trước/sau không đổi, no new production event; legacy preferences vẫn true nguyên nội dung |

Phê duyệt đã tiêu thụ ở host vẫn không được reuse. Phần mềm hiện đã kiểm lại, trạng thái quay về `SOFTWARE_READY_AWAITING_HARDWARE_APPROVAL` cho một quyết định độc lập khác; firmware bring-up thực chưa hoàn tất.

| Gate / boundary | Kết quả quan sát | Giới hạn |
| --- | --- | --- |
| Approval mới và physical/UI-ready | APPROVED trong phiên | Chỉ một transaction, không retry/recovery; không lưu approval ID/permit thật vào Git |
| APK update production tại chỗ | PASS bảo toàn guard | `adb install -r`, hash APK đã khóa, legacy consumed so trước/sau không đổi; không uninstall/clear |
| Fresh identity/power/USB permission | PASS preflight | GM1900/API 30/boot pin, pin 100%, user xác nhận chỉ OTG/no backfeed/no other app; quyền mới, không dùng grant lịch sử |
| Fresh raw descriptor trước upload | PASS FX2_DEFAULT | 189 byte, MATCH/BOOTSTRAP_CANDIDATE, open/read/close 1/1/1, claim/release/transfer 0/0/0, duration 22 ms; không phải operational 82 |
| Local firmware/SAF binding | PASS size/hash/metadata | 8192 byte/hash khóa; sửa URL do IME thay chữ trước selection, không đổi firmware/IME settings |
| Host transaction | FAIL HOST_HELPER_ERROR, GUID consumed | StrictMode missing `approval_protocol` trên legacy request chỉ challenge/ordinal; ledger NEW_APPROVAL_CONSUMED được giữ, ACK/observation 0/0 |
| Android attempt/native mới | NOT STARTED, 0 native writes | Old permit chưa được thay grant mới, report vẫn fresh RAW READ_AND_CLOSED; per-attempt prefs/native audit/raw files theo run mới đều 0; không suy từ counters adapter |
| Firmware release/post-upload observation | NOT RUN | Chưa có native A0 mới; không CPU release/reset/corrective hoặc recovery |
| Legacy-first-start host regression sau sửa | PASS offline | 45 negative checks, 7 production cases với old request/permit/RAW report, 132 loops/133 exports; current malformed bị chặn trước reservation/grant, không ADB/USB hoặc ledger mutation |
| Final release audit+detach fault | FAIL tái hiện → PASS targeted sau sửa | Full-length 132 + detach + AUDIT_FAILED suppressed không được tiếp observation; counters/full-length và provenance giữ nguyên/UNCERTAIN |
| Retry/new GUID | NOT AUTHORIZED | UI đã đóng/revoke; không reuse host GUID, không xóa legacy frames/ledger; cần fix offline và approval mới cho transaction khác |

Software PASS bên dưới chưa bao phủ file legacy tồn dư trong namespace production; không đổi thành firmware PASS. Trạng thái hiện tại `BLOCKED_WITH_EVIDENCE` trong khi sửa và kiểm nhánh helper này.

## 2026-11-05 — Phase 2.9 tiếp tục: gates phần mềm mới trên 92a25b4 cộng working tree

### Chốt sau raw-evidence sink — mới hơn các checkpoint bên dưới

| Gate cuối | Kết quả | Evidence / giới hạn |
| --- | --- | --- |
| Full JVM/build/debug/release/lint | PASS 256 methods, exit 0 | 242 drivers +1 usbxfer +13 service, 0 failure; wrapper 2 phút 42 giây/357 tasks; lint 0 errors/10 warnings |
| App/library instrumentation sau raw sink | PASS 46 +37 methods | App 8,181 giây, library 1,421 giây, namespace cách ly, manager/raw giả; service 2 PASS trước đó không đổi nguồn |
| Static APK/DEX cuối | PASS | 127 TBS classes major 52, minSdk 21/targetSdk 37; raw sink/bootstrap chỉ debug, harness vắng debug/release, firmware entries 0; zipalign cả hai exit 0, lint 0 errors/10 warnings |
| Raw evidence byte/fault tests | PASS | Hai file before/after giữ byte fixture đúng; chỉ hai descriptor reads; lưu trước upload lỗi →0 consume/native; lỗi sau upload →132 full-length và UNCERTAIN, không retry |
| Baseline cuối có raw sink | PASS 132/132 | ACK 363/465/471/472 ms; tổng Android 55.221 ms; raw files/reads 2/2, USB 0 |
| Mã cuối 1 | PASS 132/132 | ACK 362/452/463/474 ms; tổng Android 54.036 ms; raw files/reads 2/2, USB 0 |
| Mã cuối 2 | PASS 132/132 | ACK 361/449/466/472 ms; tổng Android 52.618 ms; raw files/reads 2/2, USB 0 |
| Mã cuối 3 | PASS 132/132 | ACK 365/447/463/490 ms; tổng Android 54.340 ms; raw files/reads 2/2, USB 0 |
| Ba lượt mã cuối riêng | PASS 396 samples, 0 timeout | Nearest-rank 363/450/466/490 ms; request-start → dispatch max 494,732/491,537/513,080 ms; không gộp các lượt trước hoặc FAIL |
| Host mock cuối | PASS 30 negative checks/132 loops | 16.271 ms; p50/p95/p99/max 63,61/83,32/121,39/257,54 ms chỉ local; 0 ADB/USB; canonical ledger 600 bytes/2 records, không đổi/new approval |

### Marker phân biệt ghi file với cấp timestamp ACK — checkpoint trước raw sink

| Gate mã cuối | Kết quả | Giới hạn |
| --- | --- | --- |
| Baseline có marker sau rename | PASS 132/132 | ACK 339/437/453/467 ms; tổng Android 44.888 ms; audit 264, fake/full-length 132/132, USB 0 |
| Mã cuối marker, lượt 1 | PASS 132/132 | ACK 352/440/456/525 ms; tổng Android 51.952 ms |
| Mã cuối marker, lượt 2 | PASS 132/132 | ACK 357/440/459/468 ms; tổng Android 53.353 ms |
| Mã cuối marker, lượt 3 | PASS 132/132 | ACK 362/441/464/468 ms; tổng Android 53.820 ms |
| Ba lượt mã cuối | PASS 396/396, 0 timeout | Nearest-rank 358/440/464/525 ms; request-start → dispatch max 548,464/484,658/492,292 ms; mỗi lượt audit 264, fake full-length 132, USB 0 |
| GitNexus refresh cuối | FAILED, dùng source/tests thay graph cuối | FTS `file_fts` inconsistent/COPY; không force/drop/repair ngoài phạm vi |
| Marker join | PASS 528/528 samples của bốn lượt | Mỗi `(run, ordinal, host_ack_elapsed_ms)` có đúng một marker cho ordinal 1–132; marker ordinal 0 không tính thành write |
| ACK file rename completion | RUNTIME-OBSERVED, không phải fsync | Helper không ghi permit lần hai; scope `TEMP_WRITE_RENAME_COMPLETE_NO_EXPLICIT_FSYNC`, durable status `NOT_MEASURED`. Delta nhỏ âm do sampling/độ phân giải được giữ nguyên, không clamp hoặc dùng cấp quyền |
| Request/observation storage timings | RUNTIME-OBSERVED | Cùng checked writer, start/end đo trên Android nanos; request storage p95 của ba lượt mới 10,629/9,971/10,293 ms; observation storage p95 17,363/15,160/15,809 ms |
| Host mock sau marker | PASS 30 negative checks +132 measured loops | Không ADB/USB; kiểm frame cũ tương thích, marker sau rename và permit giữ timestamp authorization cũ |
| GC mã cuối | RUNTIME-OBSERVED | Ba lượt delta 66/66/65 GC, 855/906/895 ms; lượt thứ hai có 1 blocking GC/8 ms, không che bằng kết quả 0 ở checkpoint ban đầu |

Các số đo ở bảng tiếp theo là checkpoint trước bổ sung marker; giữ nguyên để truy vết, không dùng làm giá trị mới nhất. Regression 254 JVM/85 instrumentation và mã production Android không đổi bởi marker test/host; chỉ bổ sung đo lường, không đổi nonce/deadline/permit policy.

| Gate | Kết quả mới | Evidence / giới hạn |
| --- | --- | --- |
| Full JVM | PASS 254 methods | drivers 240 + usbxfer 1 + dvbservice 13; XML 0 failure/error/skipped. Wrapper cuối exit 0, 2 phút 20 giây; không dùng 253 lịch sử làm kết quả mới |
| Debug/release/lint | PASS exit 0 | `:app:assembleDebug :app:assembleRelease :app:lintDebug` cùng full JVM; lint 0 errors/10 warnings, không suppress mới |
| Static APK/DEX/Java 8 | PASS | 126 TBS classes app+drivers major 52; min 21/target 37, debug có uploader/approval/durable writer, release không có; cả hai APK không có FullPathRig/standalone harness hoặc entry `.fw`; zipalign `-c -P 16 4` cả hai exit 0 |
| Isolated app regression cuối | PASS 46 methods riêng biệt | OnePlus GM1900/API 30, `.phase29offline` và test target riêng; 17 diagnostic, 24 approval/subclass, 1 report và 4 durability/final-dispatch methods; 8,332 giây, manager/raw giả |
| Library regression cuối | PASS 37 methods | `drivers.test` tự target, 1,482 giây; facade/binding/isolation/Port injected, không USB service thật |
| Service registry/native-load | PASS 2 methods | `dvbservice.test` tự target, 0,017 giây; chỉ load libUsbXfer và đối chiếu registry/XML, không mở USB |
| Tổng regression instrumentation | PASS 85 methods riêng biệt | 46 app +37 library +2 service; không cộng hai lần base/subclass hoặc các lần rerun cũ |
| Audit-cost | PASS 1 method | Hai mode, 264 checkpoints/mode và 6.711.056 byte/mode; cũ 1.604,809 ms, checked commit 2.077,883 ms; không phải upload hoặc speedup |
| Full-path baseline trong phiên | PASS 132/132 | ACK 357/450/488/500 ms; tổng Android 54.386 ms, fake Raw calls/full-length 132/132, audit 264 checkpoints, USB thật 0 |
| Full-path mã mới liên tiếp 1 | PASS 132/132 | ACK 351/430/449/452 ms; tổng 49.929 ms; fake/full-length 132/132, audit 264 checkpoints, USB thật 0 |
| Full-path mã mới liên tiếp 2 | PASS 132/132 | ACK 359/442/451/459 ms; tổng 53.521 ms; fake/full-length 132/132, audit 264 checkpoints, USB thật 0 |
| Full-path mã mới liên tiếp 3 | PASS 132/132 | ACK 360/436/457/458 ms; tổng 53.481 ms; fake/full-length 132/132, audit 264 checkpoints, USB thật 0 |
| Full-path tổng hợp mã mới | PASS có phạm vi, 396 mẫu | Nearest-rank p50/p95/p99/max 358/437/452/459 ms, 0 timeout; ordinal 120–132 có max 430/442/451 ms theo lượt. Chỉ một test method full-path được chạy nhiều lần, không gọi là 4 methods độc lập |
| Host mock cuối | PASS 26 negative checks | PowerShell 7, 132 shell/ACK loops, 133 exports, 13.878 ms; p50/p95/p99/max 53,58/66,14/71,93/84,54 ms chỉ local, 0 ADB/USB. Gồm terminal nhanh, child exit 0 nhưng instrumentation FAIL và xuất evidence lỗi có giới hạn |
| Deadline/fault injection | PASS deterministic + injected runtime | Đúng 1.499 ms được chấp nhận, 1.500/1.501 ms bị từ chối; kiểm stale/foreign nonce/ordinal/binding, heartbeat/foreground/consume/storage, budget trước start, audit sync/commit, post-native âm/ngắn/result, dispatch hết hạn, cleanup/detach/final-release/observer fixture |
| Khởi chạy full-path lần đầu | FAIL, được giữ nguyên | Scoped-storage mkdir assertion trước runner; helper trung gian đợi 240 giây, 0 ACK/0 USB. Đã sửa/test storage và phát hiện child exit sớm, không đổi thành firmware PASS |
| Challenge benchmark lệch fixture | FAIL-STOP, được giữ nguyên | Install identity không đúng benchmark-only; sửa fixture, giữ validator strict, không nới hardware binding |
| No-host runtime fault | PASS fail-stop mong đợi | Instrumentation cố ý FAIL `Initial host approval missing` sau 30,067 giây; result FAIL/USB 0, chưa có runner/native giả. Không cộng vào các lượt 132 PASS |
| Kênh ADB gián đoạn | RUNTIME-OBSERVED fail-stop | OFFLINE xác nhận, không tự reconnect; người dùng khôi phục, pin lại đúng model/API/boot, namespace mới; không resume attempt lỗi |
| Production consumed/host ledger | PASS bảo toàn có phạm vi | Nội dung preferences production giữ nguyên true; ledger canonical có 2 records: enrollment 1/prior consumed 1, new consumed/terminal 0/0, bytes không đổi; không new approval/GUID/permit thật |
| Android USB firmware mới | NOT RUN / AWAITING APPROVAL | Không native hardware write mới; attempt 04-11 vẫn 23 calls/full-length, 1.344 image bytes và CONSUMED/UNCERTAIN, không CPU release |
| Raw operational thật mới | NOT OBSERVED | Fixture 0x82 chỉ kiểm phần mềm; raw recovery 04-11 không được dùng làm phép đọc mới hoặc READY |
| Runtime API 21/16 KiB, real host-kill giữa 132 bước, process kill sau native intent, UI-export latency/CPU trace | NOT RUN / NOT MEASURED | Có deterministic/injected coverage theo từng nhánh, không nói đã thực hiện mọi fault vật lý/runtime. Không thay đổi thiết bị hoặc production data để làm xanh test |

**Timeout cuối:** ACK 1.500 ms, heartbeat 2.000 ms, USB 2.000 ms mỗi call, TTL 300.000 ms và benchmark 240.000 ms; cần còn ít nhất 240.000 ms trước consume. Số đo, phân rã pre-ACK/pre-native/post-native và bảng trước–sau tại [RESEARCH.md](RESEARCH.md); quyết định và crash-window tại ADR-021. Các dòng FAIL/NOT RUN lịch sử bên dưới vẫn giữ nguyên.

## 2026-11-05 — Phase 2.9: helper cuối và lượt 132 không USB thứ hai

| Gate | Kết quả | Giới hạn |
|---|---|---|
| Helper mock cuối | PASS | Parser, wrapper binding, tổng deadline vs ACK/transport, canonical ledger enrollment/CreateNew/fsync/duplicate/restart/corrupt, 132 shell loops, pretty report trên 64 KiB, reject trên 256 KiB và pinned identity |
| Android fake runtime cuối | PASS 20 cases | Cùng core approval, clock/storage giả, không USB |
| Live ADB benchmark mã cuối | PASS 132/132, timeout 0 | p50 359/p95 506/p99 714/max 759 ms; helper 3 ADB subprocess, 0 USB; export ledger ngoài Git rồi xóa đúng namespace test |
| Hai lượt 132 PASS | PASS có phạm vi, 264 samples | p95 694/p99 781/max 1476 ms; không gồm 76 observations của lượt FAIL. Không tuyên bố mọi outlier đã biến mất |
| Production helper | OFFLINE MOCK ONLY | Overall tối đa 300 giây như TTL, ACK 1500/heartbeat 2000/native timeout 2000 ms không đổi. Không chạy flags production |
| Cleanup test artifacts | PASS | Xóa hai namespace benchmark lỗi sau giữ evidence, hai package `.phase29offline` tạo trong phiên; không gỡ library test có trước, production consumed/preferences không đổi |
| GitNexus cuối | FAILED refresh, không dùng graph như bằng chứng cuối | FTS `file_fts` inconsistent/COPY; không drop/rebuild/force hoặc sửa repo. Source/diff/tests vẫn là bằng chứng |

## 2026-11-05 — Phase 2.9: kết quả cuối phần mềm, chưa upload mới

Các gates dưới đây là kết quả mới, không thay hoặc xóa các lượt FAIL phía sau.

| Gate | Kết quả mới nhất | Phạm vi |
|---|---|---|
| Full JVM sau fix final-release | PASS 253 methods | drivers 239 + usbxfer 1 + dvbservice 13; XML 0 failures/errors/skipped. Gồm đúng detach sau full-length write 132 chỉ chuyển observation, short release vẫn UNCERTAIN |
| Build debug/release/lint | PASS exit 0 | Full command sau typed cleanup cuối 2 phút 24 giây, 326 tasks/65 executed/261 up-to-date. Rebuild riêng output app trước đó PASS 3 phút 31 giây, 312 tasks; không deploy app production |
| APK static cuối | PASS | Harness standalone vắng cả debug/release; approval/uploader chỉ debug. 119 TBS classes major 52, minSdk 21/target 37, không `.fw` APK. Lint 0 errors/10 warnings, không suppress mới |
| Approval/report instrumentation distinct | PASS 22 methods | 20 deterministic + 1 AndroidStorage isolation + 1 report-revision; chạy subclass và report class, không đếm lặp base. App/test `.phase29offline`, không tuner access |
| Port injected instrumentation | PASS 7 methods | `drivers.test`, manager/raw fake; âm/ngắn/IOException/RuntimeException, ACK timeout, native-boundary expiry, mất device/quyền sau ACK |
| Tổng targeted instrumentation mới | PASS 29 methods riêng biệt | 22 app + 7 drivers; không gọi là full 56 historical instrumentation suite hoặc hardware PASS |
| Live ADB no-USB benchmark | PASS 132/132; timeout 0 | Cùng core requireWrite/requireNative; p50 611/p95 705/p99 783/max 1476 ms; 3 ADB subprocess phần helper, namespace test đã export và xóa sau PASS |
| Safety margin benchmark | Còn rủi ro tail | Max cách deadline 1500 ms chỉ 24 ms. Không bao native transfer/production native-audit/UI overhead; không tự nới deadline hoặc bảo đảm firmware thành công |
| Host prior audit | PASS lưu local ngoài Git | Initializer canonical CreateNew/fsync ghi prior CONSUMED/UNCERTAIN; hardware_approval=false/new_approval_issued=false/app modified=false |
| Consumed production | PASS bảo toàn | Preferences nội dung không đổi sau các suite; legacy true, không tạo production new-attempt preferences hoặc permit mới |
| Hardware mới | NOT RUN / AWAITING_EXPLICIT_NEW_HARDWARE_APPROVAL | Không upload/reset/release, không vendor/I²C/frontend/TS. Candidate duy nhất dual-reset; cần preflight mới và phê duyệt riêng |
| API 21 runtime | NOT RUN | Static min 21/major 52 không chứng minh runtime API 21 |

## 2026-11-05 — Phase 2.9: rerun cách ly và benchmark có giới hạn

Các kết quả sau mới hơn snapshot 249 JVM phía dưới; vẫn không USB thật.

| Gate | Kết quả | Bằng chứng / giới hạn |
|---|---|---|
| Approval/report instrumentation rerun | PASS 42 executions | OnePlus/API 30, app `.phase29offline` và test target riêng. 20 deterministic methods chạy qua base và subclass, thêm 1 isolation + 1 report revision; không gọi là 42 tình huống độc lập. Không launch Activity thật hoặc mở UsbManager |
| Port instrumentation rerun | PASS 7 tests | `drivers.test` tự instrument; thêm đúng ACK nhưng mất device/permission và permit hết hạn sau audit intent. 0 native hardware calls |
| Consumed production sau hai suite | PASS bảo toàn | Nội dung preferences production đọc trước/sau không đổi, consumed=true. Không reinstall/uninstall/clear app production |
| Full JVM một lượt sau bổ sung | FAIL 1 method cũ | `repeatedDetachAndCancelRacesDoNotLeakOrReplay`, chờ thread vào fake transfer 1 giây ở ConcurrencyTest:73 hết hạn; không sửa hoặc tăng timeout để che lỗi |
| Focused concurrency rerun | PASS 10 methods | Không sửa mã, wrapper focused exit 0/55 giây. Chưa kết luận nguyên nhân scheduler chỉ từ rerun; full gates cuối phải chạy lại |
| App-process deterministic rerun | PASS 20 fake cases | Cùng core Java approval, runtime Android thật, clock/storage giả; không phải hardware test |
| Persistent channel benchmark 60 giây | FAIL / PARTIAL 76 observed, 77 ACK | 76 request→ACK→Android observe hợp lệ: p50 720/p95 785/p99=max 809 ms. Chưa đủ 132. Sau lỗi discovery+identity vẫn online đúng target, monitor không báo mất ADB; không reconnect |
| Phân loại timeout benchmark | Đang sửa/kiểm lại tại checkpoint | Giới hạn tổng 60 giây không đủ cho 132 vòng khoảng 700 ms. Không đổi ACK 1500 ms, heartbeat 2000 ms, permit TTL 5 phút hoặc USB timeout 2000 ms. Bound tổng phải tách khỏi deadline từng write |

## 2026-11-05 — Phase 2.9: kiểm phần mềm sau attempt đã consumed

Baseline `d76bbfb63dd21d55af6ce27fdff0543eb500db72` cộng working tree.
Không dùng kết quả 241 JVM/56 instrumentation lịch sử thay kết quả mới.
Không cài lại, uninstall hoặc clear data package production đang giữ consumed.

| Gate | Kết quả mới | Bằng chứng / giới hạn |
|---|---|---|
| Git / evidence đầu phiên | PASS chỉ đọc | Nhánh đúng, một worktree, sạch đầu phiên. Đọc JSON sanitized attempt/recovery đã có; không dựng timestamp thiếu |
| Production attempt | RUNTIME-OBSERVED bảo toàn | SharedPreferences consumed=true. So nội dung trước/sau library test và isolated app test: không đổi |
| Firmware local | OFFLINE-TESTED PASS | 8192 byte/hash khóa, đọc ảnh đã có ngoài Git; không copy/upload |
| Targeted JVM lần đầu | PASS 17 methods | OneShotBootstrapTest 14 + BootstrapTelemetryTest 3; compile app và Android tests cùng lượt, exit 0 |
| Full JVM / regression snapshot | PASS 249 methods | drivers 235 + usbxfer 1 + dvbservice 13; XML 0 failures/errors/skipped. Có 132 vị trí pre-native ACK fail và 4×132 native fault scenarios trong các methods, không tính thành số methods |
| Native boundary injected trên OnePlus | PASS lượt đầu 5 tests | Chỉ package/targetPackage `info.martinmarinov.drivers.test`; manager/raw giả. IOException, RuntimeException, âm/ngắn, ACK fail, không thêm USB call sau gate đóng. Không phải USB hardware test |
| Approval app instrumentation cách ly lần đầu | FAIL 1/27 | Dùng app suffix `.phase29offline` và test riêng, không production. 26 methods qua; assertion package/path isolation ở ApprovalTest:21 chưa nhận namespace mới. Tiếp tục sửa/kiểm lại trong Phase 2.9, không bỏ gate |
| Android app_process fake suite lượt đầu | PASS 13 tests | Harness chỉ core approval và clock/storage giả; dependency scan 0 USB references, không Context/prefs production |
| Live-no-USB benchmark lần đầu | FAILED trước handshake | Fake suite 13 PASS, sau đó wrapper truyền `DeadlineTimestamp` nhưng helper snapshot chưa có parameter; không phát approval thật, không gọi USB. Namespace riêng có hard stop 60 giây |
| Build debug/release/lint snapshot | PASS exit 0 | Wrapper 3 phút 25 giây, 387 tasks/63 executed/324 up-to-date. Lint 0 errors/11 warnings; một warning mới SdCardPath thuộc harness test-only, đang xử lý tách khỏi APK |
| Release DEX snapshot / bytecode | PASS static | Runner/adapter/approval/telemetry/harness vắng release; 120 TBS classes major 52; minSdk 21/target 37. Không `.fw` trong APK debug/release |
| ADB / owner | RUNTIME-OBSERVED | Đúng OnePlus GM1900/API 30/USB Host; tuner còn được liệt kê. Report raw production là checkpoint cũ READ_AND_CLOSED/pending=false, không descriptor mới |
| Hardware attempt mới / frontend / TS | NOT RUN | Không A0/vendor/I²C/EEPROM/CPU reset/release, không capture/chuyển nguồn/usbipd/permission grant. Approval cũ không tái dùng |
| API 21 runtime | NOT RUN | Static minSdk/major 52 không thay test thiết bị API 21 |


## 2026-11-04 — Phase 2.9: một attempt Android thật và recovery riêng

Các kết quả này mới hơn checkpoint offline phía dưới. Không chạy lại instrumentation/build
sau thực nghiệm, tránh cài lại/xóa attempt guard đã consumed. Không thay mã sau khi chạy hardware.

| Gate | Kết quả thực | Bằng chứng / giới hạn |
|---|---|---|
| Phê duyệt | APPROVED rồi CONSUMED | Người dùng xác nhận riêng chuyển Windows→OnePlus, chỉ OTG/không backfeed và một lượt dual-reset; tự chọn SAF và tự bấm Nạp đúng một lần |
| Firmware preflight | PASS trước open | 8192 byte/hash khóa, source metadata user-attested. Lần chọn đầu FAILED vì URL trailing space, không open/write/consume; người dùng sửa rồi chọn lại |
| Permission/ownership | RUNTIME-OBSERVED | GM1900/API 30 qua Wireless ADB; một tuner, permission có, raw MATCH thiếu endpoint `0x82`; observer/epoch 1/worker hiện hữu, không claim |
| A0 thật qua Android API | PARTIAL, không upload PASS | 23 actual calls đều full length: 2×1 byte reset stop + 21×64 byte image =1346 byte payload; 1344/8192 byte image |
| Bước 24 / handshake | FAILED trước Android API | value 0x0540/length 64, runner attempted 24/completed 23; guard dừng sau 1510321 µs. Không native call 24. Sau dừng ack ordinal 24; deadline 1500 ms là giải thích phù hợp nhưng nguyên nhân sâu UNVERIFIED |
| Trạng thái attempt | UNCERTAIN | Duration 25946 ms, IO_FAILURE, attach events 0, pending=false/cleanup=false, consumed=true; không CPU release/retry/fallback hoặc lần upload 2 |
| Firmware transition/post-upload descriptors | NOT RUN / NOT REACHED | Chưa đủ image/132 writes; không TRANSITIONING hoặc OPERATIONAL_CANDIDATE, không READY/frontend/TS |
| Recovery vật lý | RUNTIME-OBSERVED có điều kiện | Người dùng duyệt riêng và rút/cắm đúng một lần trên OnePlus, chỉ OTG. Không software USB reset hoặc chuyển Windows |
| Raw sau recovery | PASS đọc descriptor, không bootstrap PASS | Permission/owner mới, 14 ms; open/read/close 1/1/1, claim/transfer 0; 189 byte/MATCH/BOOTSTRAP_CANDIDATE thiếu endpoint `0x82`, hash khớp trước nạp |
| Ownership/guard cuối | RUNTIME-OBSERVED | Tuner trên OnePlus; Windows PnP tuner 0; ADB đúng OnePlus online. Guard consumed=true sau mở diagnostic mới; không tái cấp permit hoặc retry |
| Bước tiếp | BLOCKED đối với hardware mới | Phân tích/mô phỏng offline acknowledgment latency và stop telemetry; mọi attempt tương lai cần phê duyệt mới, không reuse authorization |


## 2026-11-04 — Phase 2.9: debug Android bootstrap, chưa thực nghiệm tuner

Baseline `ead0e4adf5051e878706ad70c3863b90cb8ffb98` cộng working tree, không commit.
Giữ AGP 9.5.0/Gradle 9.8.2/JDK 25.0.5.0.3/compile 37.2/Build-Tools 37.0.0,
NDK 30.0.16248370/CMake 4.1.2/target 37/min 21/source-target 8; không cài hoặc nâng cấp.

| Gate | Kết quả mới | Bằng chứng / giới hạn |
|---|---|---|
| One-shot runner fake | OFFLINE-TESTED PASS, 11 methods mới | 132 writes/8196 payload bytes, 8192 image bytes, 100 ms; 4 kiểu lỗi × 132 vị trí; deny trước open, identity/raw/fingerprint/permission/epoch/ADB, cleanup/in-flight, quan sát lease mới và không READY |
| Ảnh thật local | OFFLINE-TESTED PASS | Reader/planner mới chạy bằng Java `--release 8`, đúng size/hash khóa; so từng DATA chunk, từ chối truncated/extra/modified; không USB và không blob fixture |
| Android adapter injected | PASS, 3 instrumentation methods mới | Manager/raw fake trên Android thật; full/short/negative/exception, thứ tự/fields, worker, observer, single lease, acknowledgment; không gọi UsbManager phần cứng |
| Approval boundary | PASS, 4 instrumentation methods mới | Thiếu permit, foreground, explicit flags, expiry/heartbeat, challenge/install/hash/attachment/plan, duplicate, restart/reinstall, ordinal acknowledgment |
| JVM toàn bộ | PASS: 241 methods | drivers 227 + usbxfer 1 + dvbservice 13; 0 failures/errors/skipped. Test runner mới có 528 trường hợp lỗi trong 1 method, không tính thành 528 testcases riêng |
| Instrumentation | PASS: 56 tests | app 21 + drivers 33 + dvbservice 2; GM1900/API 30, không tuner; 0 failures/errors/skipped. Native smoke/XML và 49 tests cũ giữ trong suite |
| Build/lint mã cuối | PASS, exit 0 | Lượt cuối 4 phút 19 giây, 421 tasks/39 executed/382 up-to-date; debug/release/lint. Lint 0 Error/Fatal, 10 Warning; không suppress hoặc thêm baseline |
| Java/release isolation | PASS static | 107 class TBS major 52; uploader/runner/approval/diagnostic có debug, vắng release DEX; release manifest không diagnostic, min 21/target 37; không .fw hoặc firmware blob trong hai APK |
| Approval PowerShell helper | OFFLINE-TESTED PASS | Parse 0 errors; không flags → BLOCKED exit 1 trước ADB; mock ADB end-to-end cấp đúng ordinal/ghi atomic/stop terminal exit 0. Không chạy helper với target thật |
| UI sau UTP | RUNTIME-OBSERVED PASS có phạm vi | Cài debug exit 0/cold launch 670 ms; nút firmware/upload hiện và disabled; report DETECTED, open/claim 0, pending/cleanup false. Không USB/TBS trên OnePlus; Windows BDA OK |
| Wireless ADB | RUNTIME-OBSERVED | Chọn một TCP target, xác minh OnePlus/GM1900/API 30/Host; giám sát mỗi 10 giây trong lệnh dài. Không tự pair/reconnect/đổi target |
| Tooling/editor | Có giới hạn | VS Code task không resolve được, dùng wrapper trực tiếp. Java editor báo ngoài classpath/duplicate phantom; Gradle compile/test thực thành công, source chỉ một định nghĩa mỗi method. Không sửa global cấu hình |
| GitNexus cuối | FAILED refresh | Đầu và giữa phiên refresh PASS; cuối gặp FTS file_fts inconsistent/COPY. Không force/drop/rebuild thủ công; không dùng graph cuối làm chứng cứ an toàn |
| Android hardware firmware/transition/post-descriptors | NOT RUN / BLOCKED | Chưa duyệt chuyển host hoặc một lượt upload; firmware chưa chọn qua UI trên tuner; chưa có descriptor Android sau firmware |
| Frontend/I²C/tuning/PLP/MPEG-TS/READY | NOT RUN, ngoài Phase 2.9 | Không A0 IN, B7, 80/90/91/8A, EEPROM, setInterface/setConfiguration/reset hoặc stream |
| API 21 runtime / recovery vật lý | NOT RUN | Static minSdk/major 52 không thay thế thiết bị API 21; không đảm bảo power-cycle recovery từ fake |


## 2026-11-04 — Phase 2.8: kết quả sau trace Windows và parser offline

Phần này mới hơn checkpoint offline 225 JVM phía dưới. Firmware Android vẫn NOT RUN;
Windows writes dưới đây do BDA hãng tự phát trong reconnect được duyệt, không phải agent/Android uploader.

| Gate | Kết quả mới | Bằng chứng / giới hạn |
|---|---|---|
| Trace thứ nhất 30 giây | FAILED phép đo | 296 byte/0 packets; reconnect sau cửa sổ, không đủ dữ liệu protocol. Không tự retry |
| Trace thứ hai 180 giây | RUNTIME-OBSERVED | Người dùng duyệt riêng; 3028 packets/232084 byte, chỉ bus 3/address 4, không frame truncated, raw ngoài Git |
| A0 setup/completion Windows | RUNTIME-OBSERVED | 264/264 cặp, hai lượt tự nhiên × 132 writes. Tất cả completion SUCCESS, không A0 pending; không lấy submission status làm kết quả |
| 7F92/E600 trên board này | RUNTIME-OBSERVED | Mỗi địa chỉ stop 01/release 00 thành công ở cả hai lượt; không STALL trong điều kiện quan sát. Không chứng minh alias, CPU state hoặc mọi revision |
| Ảnh DATA trong capture | OFFLINE-TESTED | Hai ảnh dựng lại đều 8192 byte/hash khóa, 128 × 64 byte, đúng vùng 0000–1FFF; không bỏ zero/padding |
| Descriptor Windows | RUNTIME-OBSERVED | Frame 6 có default alt 0–3 thiếu 82, frame 298 sau lượt đầu có alt 0 với IN82/81. Không khẳng định Android descriptor hoặc READY |
| BDA tự init lần hai | RUNTIME-OBSERVED, chưa giải thích | Mười B7 IN completion c0000011 sau lượt đầu; có vendor 90/8A/80 tự nhiên. Không agent I²C, không sao chép retry sang Android |
| Capture cleanup | PASS sau hỗ trợ người dùng | TShark dừng; wrapper exit 1 do elevated worker còn sống. Dừng parent bằng PID, worker Access denied; người dùng dừng đúng PID, kiểm không còn capture process. Không đổi service/filter |
| Parser transcript mới | OFFLINE-TESTED: 5 methods PASS | IRP ID tái dùng/frame correlation, failed completion, thiếu cặp, truncation, hash/address/length sai, mixed devices/rows; fixture tổng hợp không firmware thật |
| Parser trên trace thật | OFFLINE-TESTED PASS | Java parser và Node checker độc lập cho cùng hai ảnh/hash/264 pairs; đầu ra chỉ frame/reset/status/hash, không raw payload/IRP IDs công khai |
| JVM sau parser | PASS: 230 methods | drivers 216 + usbxfer 1 + dvbservice 13; 0 failures/errors/skipped. Tăng 5 methods so với checkpoint 225 |
| Instrumentation sau parser | PASS: 49 tests | App 17 + drivers 30 + dvbservice 2 trên GM1900/API 30 khi tuner ở Windows; native smoke/XML vẫn trong suite, không Android USB hardware |
| Debug/release/lint mã cuối | PASS | 4 phút 2 giây/419 tasks/55 executed/364 up-to-date; lint 0 errors/fatal, 9 warnings. Toàn bộ unit/connected tasks chạy lại trên mã đã khóa |
| Java 8/release isolation sau parser | PASS static | Parser biên dịch `--release 8`, major 52; parser/comparator/runner/validation không có trong release DEX, debug có; manifest min 21/target 37 không diagnostic |
| Metadata mã cuối | RUNTIME-OBSERVED | Sau UTP cài lại debug/cold launch 996 ms: không có USB trên OnePlus, operation/generation 0, DETECTED, pending/cleanup=false, open/claim/close 0. Windows vẫn TBS BDA OK |
| GitNexus cuối | FAILED refresh | Incremental gặp FTS file_fts inconsistent. `status` vẫn báo up-to-date theo HEAD cũ, không dùng làm proof graph mới; không tự drop index/force repair |
| Wireless ADB | RUNTIME-OBSERVED: hai fail-stops | Dừng trước Windows query và ở kiểm cuối khi TCP target mất; chỉ sau hai xác nhận nối lại mới kiểm GM1900/OnePlus/API 30/owner, không tái dùng USB session |
| Android upload/return-host/READY/frontend/TS | NOT RUN | Tuner hiện ở Windows. Chưa duyệt chuyển về/upload; gate false, không có hardware backend hoặc retry kế hoạch khác |

## 2026-11-04 — Phase 2.8: hai reset plans offline; phép đo board và upload NOT RUN

Baseline Phase 2.7 `ef1154d9c60725150e0d2eaab082d7b644748444`, cộng working tree chưa commit.
Toolchain giữ AGP 9.5.0/Gradle 9.8.2/JDK 25.0.5.0.3/compile 37.2/target 37/min 21/Java 8,
Build-Tools 37.0.0/NDK 30.0.16248370/CMake 4.1.2. Runtime GM1900/API 30/arm64 thật qua Wireless ADB;
không emulator, không gọi hardware port trong tests.

| Gate | Kết quả mới | Bằng chứng / giới hạn |
|---|---|---|
| Plan comparator | OFFLINE-TESTED: 3 methods PASS | Exact allowlist; null/unknown/case/space bị từ chối; giữ dual-reset lịch sử; E600-only không chứa 7F92; provenance khác; immutable; full image và tail không padding; từ chối DATA vượt 1FFF |
| Bootstrap runner | OFFLINE-TESTED: 17 methods PASS | 13 methods cũ và 4 mới; lỗi từng vị trí trong 132/130 writes của ảnh tổng hợp 8192 byte; không retry/corrective release; mất ADB trước open hoặc sau write; attempt tiêu thụ; TRANSITIONING không thành READY khi không/có attach mới |
| Failure/lifecycle regression | PASS trong suite | Denied permission, epoch/generation cũ, detach/identity tái dùng, I/O pending, lỗi cleanup, short/negative/timeout/cancel, deferred close khi synchronous call còn chạy. Hai plans không tự fallback |
| Ảnh hãng thật qua planner | OFFLINE-TESTED PASS | 8192 byte/hash khóa; kiểm mọi DATA byte/address/control field; dual 132 writes và E600-only 130 writes, mỗi plan 1 delay; sửa byte cuối bị từ chối. Không UsbDeviceConnection, không dùng firmware fixture trong Git |
| JVM toàn bộ mã cuối | PASS: 225 methods | drivers 211 + usbxfer 1 + dvbservice 13; 0 failures/errors/skipped. Ba task unit chạy `--rerun`; tăng 7 methods so với 218 lịch sử |
| Instrumentation app | PASS: 17 tests | Diagnostic fake trên GM1900/API 30, không mở tuner |
| Instrumentation drivers | PASS: 30 tests | 28 facade + 2 bootstrap isolation; facade production vẫn từ chối bootstrap trước manager, EP0 fake không claim |
| Native smoke / XML | PASS: 2 tests | NativeLibrarySmokeTest load arm64 và DeviceFilterXmlEquivalenceTester; không USB ioctl |
| Tổng instrumentation mã cuối | PASS: 49 tests | 17 + 30 + 2, 0 failures/errors/skipped; connected tasks thực thi lại, không lấy XML cũ làm PASS mới |
| Debug/release build, lint | PASS | Lượt cuối 2 phút 29 giây, 419 tasks, 27 executed/392 up-to-date; lint 0 errors/fatal, 9 warnings trên cùng mã. Không suppress/baseline hoặc nâng toolchain |
| Java 8 / minSdk | PASS static | 13 core/debug source `javac --release 8`, comparator bytecode major 52; release manifest min 21/target 37. API 21 runtime NOT RUN |
| Release isolation | PASS static | Comparator/runner/validation/fingerprint/raw/diagnostic không có trong release DEX; release manifest không diagnostic. Debug có comparator; cả hai APK có bốn ABI libUsbXfer. Production registry/XML/main source không đổi |
| App sau UTP | RUNTIME-OBSERVED | UTP gỡ app; cài lại debug thành công, cold launch 826 ms. Metadata mới Host=true, 734c:5530, alt 0–3, không có 82; permission=false, operation/generation 0, DETECTED, pending/cleanup=false, open/claim/release/close 0 |
| Wireless ADB guard | RUNTIME-OBSERVED | Đúng TCP serial/model/manufacturer/API được kiểm đầu phiên, trước device operations và mỗi 10 giây trong lệnh dài; không phát hiện mất target trong các lượt này; alias mDNS không phải thiết bị thứ hai |
| Firmware/SYS/PDF provenance | OFFLINE-TESTED | Tải HTTPS, size/hash xác minh mới; ba SYS nhúng nguyên ảnh firmware; phân tích tĩnh dual-reset. Không coi đó là board/Android PASS |
| Capture tooling | RUNTIME-OBSERVED inventory | TShark 4.6.9, USBPcap 1.5.4.0, service Running và 5 interfaces qua extcap; quyền capture/topology tuner chưa kiểm. Không có capture mới |
| URB board / A0 IN-OUT / CPU reset-release / firmware transition | NOT RUN | Chọn hướng B; chưa duyệt chuyển host/capture/upload. Không biết completion 7F92, recovery nguồn, descriptor sau nạp hoặc IN82 mới |
| EEPROM / setInterface / setConfiguration / USB reset / frontend / tuning / TS | NOT RUN | Không thay production gate/registry, không bắt đầu CXD2878 hoặc phase kế tiếp |
| Markdown và whitespace | PASS giới hạn | `git diff --check` và rà thủ công phần mới; không có Markdown linter được cấu hình/chạy, không gọi đây là Markdown lint |

Lượt targeted ban đầu: 20 methods, 0 failures/errors/skipped, 58 giây/23 tasks.
Lượt full đầu: 225 JVM/49 instrumentation, 5 phút 27 giây/419 tasks/67 executed.
Sau đổi chuỗi blocker (gate vẫn false), focused/build/lint đạt 2 phút 3 giây/314 tasks;
regression full cuối ở bảng mới xác minh lại toàn bộ mã cuối. Không cộng lặp các lượt thành tổng tests khác.
Cảnh báo Java source/target 8 và Gradle 10 giữ nguyên. API 21 runtime, thiết bị USB trang nhớ 16 KiB
và tốc độ USB thực chưa có phép đo mới. Xem lệnh/exit trong [WORKLOG.md](WORKLOG.md).

## 2026-11-04 — Phase 2.7: bootstrap offline, hardware upload BLOCKED

Baseline Phase 2.6 đã push `f03e092702edac5134efea633d8a30a51e1716e4`, nhánh `chore/modernize-android-build`, cộng working tree chưa commit. Không dùng kết quả lịch sử 199/47 làm `PASS` mới. Toolchain khai báo và đã kiểm local giữ AGP 9.5.0, Gradle 9.8.2, JDK 25.0.5.0.3, compileSdk 37.2, targetSdk 37, minSdk 21, Java 8, NDK 30 và CMake 4.1.2. Runtime là GM1900/API 30/arm64; chỉ dùng serial Wireless TCP đã chọn tường minh.

| Gate | Kết quả mới | Bằng chứng / giới hạn |
|---|---|---|
| Wireless guard | PASS theo phạm vi; 1 lần STOP | Kiểm mỗi 10 giây; phát hiện target mất khi sửa core và dừng mọi việc. Sau xác nhận của người dùng mới tra cổng và kiểm lại model/API/USB Host/ứng dụng/tài nguyên đang chờ. Không pair/reconnect tự động hoặc dùng USB handle cũ |
| FX2 fingerprint | UNIT-TESTED: 6 methods PASS | Dựng lại 189 byte từ trường số trong hồ sơ; khớp default HS, thay từng nhóm trường, kiểm PARTIAL/NO_MATCH, operational với `0x82` và unknown với `0x88`, mọi điểm cắt dữ liệu; không phụ thuộc VID/PID/bcdDevice/model/API |
| Bootstrap dry-run | UNIT-TESTED: 13 methods PASS | Fake EP0 kiểm type=40, request=A0, index=0, value/payload, timeout=2000 ms, thứ tự và delay=100 ms; kích thước 1/63/64/65/128/8192 byte. Không claim/release/retry hoặc ghi khắc phục. Bao phủ lỗi từng bước ghi, short/negative/timeout/IO/cancel, quyền/định danh/detach/ID tái dùng/epoch, I/O đang chạy, cleanup trì hoãn và cleanup lỗi |
| Firmware policy regression | PASS toàn suite cũ | `Tbs5530FirmwarePlanTest` vẫn kiểm null/empty/hash/size/ranges, địa chỉ dành riêng 7F92/E600, tràn địa chỉ và khối cuối; không nới vùng cho phép theo giới hạn E200 của Go |
| Ảnh TBS thật, chỉ offline | PASS validation/planning | 8192 byte, hash `b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc`; chương trình Java xác nhận 133 bước = 132 writes + delay, gồm 128 × 64 byte DATA tại 0000–1FFF. Fake port kiểm từng byte/request/thứ tự trong 132 calls, 1 open/1 close/0 claim; sửa byte cuối bị từ chối; 0 calls tới UsbDeviceConnection |
| JVM toàn bộ | PASS: 218 methods | drivers 204, usbxfer 1, dvbservice 13; 0 failures/errors/skipped. Riêng TBS có 187 methods. Ba task `testDebugUnitTest --rerun` chạy trong lượt cuối |
| Instrumentation app | PASS: 17 tests | Diagnostic dùng fake trên GM1900/API 30; không mở tuner |
| Instrumentation drivers | PASS: 30 tests | 28 tests facade cũ + 2 tests bootstrap isolation mới: production facade từ chối bootstrap trước manager; fake EP0 không claim dùng cùng worker/owner, không có operational stream |
| Instrumentation native/XML | PASS: 2 tests | Load UsbXfer trên arm64 và kiểm registry/XML tương đương; không gọi native USB ioctl |
| Tổng instrumentation | PASS: 49 tests | 17 + 30 + 2; 0 failures/errors/skipped. Connected tasks chạy với `--rerun`; đếm từng testsuite/testcase lồng trong XML, không chỉ dựa vào exit 0 |
| Debug/release APK và lint | PASS trên mã cuối | Lượt cuối mất 7 phút 55 giây, gồm 419 tasks: 86 executed, 333 up-to-date. Lint có 0 Error/0 Fatal/9 Warning; không thêm baseline hoặc suppress |
| Java 8/minSdk | PASS static | `javac --release 8` biên dịch 13 source core/debug; BootstrapDryRun có bytecode major 52; manifest release dùng minSdk 21/targetSdk 37. Runtime API 21: NOT RUN |
| Debug/release isolation | PASS static | 9 nhóm class có trong debug, không có trong release; release manifest không có Activity diagnostic. Debug/release chứa 4 ABI native. Không sửa registry/XML/filter production |
| Metadata OnePlus cuối | RUNTIME-OBSERVED | Cài lại debug sau UTP, cold launch 716 ms. Host=true, VID:PID `734c:5530`, alt 0–3, không có `0x82`; permission=false, generation=0, state=DETECTED, pending/cleanup=false, open/claim/close đều bằng 0 |
| Raw descriptors mới / alternate đang active / tốc độ | NOT RUN / UNKNOWN | Không lặp raw cycle; dùng snapshot Phase 2.6 đã có. Metadata cuối không chứng minh tốc độ, trạng thái CPU hoặc firmware |
| Firmware upload/CPU reset/vendor/I²C/LNB/tune/TS | NOT RUN | Điều kiện phần cứng BLOCKED do chưa xác minh 7F92 và phương án khôi phục board; không xin phê duyệt upload khi thiếu điều kiện, không ghi phần cứng |
| Re-enumeration/descriptors/`0x82`/bridge readiness sau nạp | NOT RUN | Không upload nên không có bằng chứng trước/sau mới hoặc READY; hoàn tất trên fake chỉ là TRANSITIONING |
| Toolchain inventory script | BLOCKED bởi execution policy | Script chưa ký số bị policy chặn; không bypass/unblock hoặc đổi policy User/Machine. Đọc build/wrapper/source.properties/executables trực tiếp, không đổi package |

Lượt focused có 18 methods `PASS`, debug build mất 1 phút 48 giây. Lượt full đầu có 217 phương thức JVM và 49 instrumentation tests `PASS` trong 7 phút 39 giây, trên mã trước khi bổ sung epoch guard và report. Lượt cuối 218/49 ở bảng trên mới là mã hoàn tất; không gộp lượt đầu thành chứng cứ mã cuối. Giữ các cảnh báo source 8, deprecated API, Unsafe và việc không tương thích Gradle 10; không che cảnh báo. Chi tiết lệnh ở [WORKLOG.md](WORKLOG.md), source/firmware ở [RESEARCH.md](RESEARCH.md), ranh giới sở hữu tài nguyên ở [ADR-018](DECISIONS.md).

## 2026-11-04 00:06 +07 — Phase 2.6: raw descriptor chỉ đọc, OnePlus/API 30

Baseline đã push `df076d28dd8100d5caa363f1d866a274e3f2b636`, nhánh `chore/modernize-android-build`, cộng thay đổi working tree chưa commit. 180/44 của Phase 2.5 giữ nguyên lịch sử bên dưới, không chuyển thành kết quả mới. Runtime GM1900/Android 11/API 30/arm64, Wireless ADB serial TCP tường minh, không emulator.

| Gate | Kết quả mới | Bằng chứng / giới hạn |
|---|---|---|
| Wireless guard | PASS theo phạm vi; một lần STOP | Serial cũ mất khi sửa code: dừng, chờ xác nhận, tra serial mới, Host/attachment/permission/app owner mới; không tự đổi target. Build dài kiểm mỗi 10 s |
| Parser JVM debug | PASS 7 methods | Null/empty/device-only, bLength 0/1/standard quá ngắn, wTotalLength nhỏ/vượt data, mọi điểm cắt tree, nhiều alternate/một interface, hướng 02 OUT khác 82 IN, không 82 vẫn parse, duplicate/count/unknown payload/redaction/immutable/size cap |
| Raw lifecycle JVM debug | PASS 12 methods | Deny/missing/stale trước open; detach trong open/read; permission mất; open null/exception; read/close exception; cleanup một lần; deadline/owner close không force-close; không retry; events khác attachment |
| Production regression | PASS | `drivers/src/main` không đổi; test profile vẫn từ chối thiếu 82. Capability raw không có claim/vendor/control/bulk API |
| TBS JVM toàn bộ | PASS 168 methods | Gồm 149 cũ và 19 mới; không coi fake transport là hardware PASS |
| JVM toàn bộ module | PASS 199 methods | drivers 185/usbxfer 1/dvbservice 13; 0 failures/errors; từng test task dùng `--rerun` ở lượt cuối |
| Instrumentation diagnostic | PASS 17 tests | OnePlus API 30, fake raw port, worker khác main, 0 production open/claim/transfer, block cùng owner và giữa hai owner; giữ 14 tests cũ |
| Instrumentation facade | PASS 28 tests | Android/API 30, fake Manager/raw transport; không truy cập tuner |
| Native smoke/XML | PASS 2 tests | `NativeLibrarySmokeTest` load trên arm64; `DeviceFilterXmlEquivalenceTester` 1; không ioctl USB |
| Tổng instrumentation | PASS 47 tests | App 17 + drivers 28 + dvbservice 2; 0 failures/errors/skipped; connected tasks thực thi lại sau raw cycle |
| Debug/release APK | PASS | Mã cuối trước USB: 2 m 55 s/419 tasks, 38 executed/381 up-to-date; sau USB: 2 m 2 s/419 tasks, 23 executed/396 up-to-date |
| Lint | PASS 0 errors/0 fatal/9 warnings | XML lint 9.5.0 trên mã cuối; lượt sau USB task chạy lại, analysis/report up-to-date của cùng source. Không gọi 11 warnings lịch sử là kết quả mới |
| Java 8 | PASS | 10 core/debug JVM source compile bằng `javac --release 8`; `javap` parser major 52. Main build source/target 8/min 21 không đổi |
| Debug-only isolation | PASS | DEX debug có 6 class diagnostic; release DEX không 6 class; release manifest không Activity. Không thay registry/XML production |
| USB Host/metadata mới | PASS | 734c:5530/configvalue 1/alt 0–3 trước raw; metadata-only không open |
| Permission callback thật | PASS tại lượt mới | Người dùng tự Allow; operation 1/gen 1; callback 1/correlation=true; PERMISSION_GRANTED trước cleanup, CLOSED sau; open 0/claim 0 |
| Raw cycle thật, được duyệt 1 lần | PASS read-only | Operation 2/gen 2; open/read/close 1/1; 13 ms worker; permission/identity=true; failure NONE; pending/cleanup=false; 0 claim/release/transfer |
| Raw tree thực trả về | PASS parse phạm vi 189 bytes | SHA256 `e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de`; device 18 + config 171; bNumInterfaces 1/alt 0–3; issues 0 |
| Raw so metadata | PASS 18/18 endpoints | Đối chiếu ID/alternate/address/direction/type/packet/interval; không IN82. Không chứng minh cache bằng descriptor live trên bus |
| TBS ChangeMode | Source/static research | Trang official + ZIP directory + strings trong RAM, không chạy EXE; chưa biết protocol/descriptors/rollback; không hardware PASS |
| Trạng thái cuối sau UTP/cài lại | PASS metadata | Debug APK cuối cold launch, generation 0/DETECTED, pending=false, open 0/claim 0; permission=false. Không xin lại hoặc chạy raw lần 2 |
| Streaming readiness/active alternate/firmware state | BLOCKED/UNKNOWN | Không 82, không GET_INTERFACE/GET_CONFIGURATION; không suy firmware cold/warm hoặc 88=TS |
| Firmware/CPU/vendor/I²C/bulk/tuner/TS/ChangeMode execution/reset | NOT RUN | Ngoài phê duyệt; không tự bắt đầu phase kế tiếp |

Lượt focused đầu: 19 methods chạy, 18 PASS/1 FAIL do fixture duplicate endpoint sửa nhầm byte attributes (offset 62), không phải parser lỗi. Sửa fixture sang address offset 61; chạy lại 19/19 PASS rồi full regression. Chưa phát hiện bug runtime Android ở raw cycle; không nới profile để đổi BLOCKED thành PASS. Chi tiết lệnh và kết quả ở [WORKLOG.md](WORKLOG.md), raw fields/nguồn/giả thuyết ở [RESEARCH.md](RESEARCH.md), quyền sở hữu ở [ADR-017](DECISIONS.md).

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
| Callback cấp quyền thực | NOT VERIFIED | Đọc lại sau deadline 30 s, callback count 0/correlation false/cleanup đã xong; không gọi kết quả giả lập là PASS thật |
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
| Debug/release/lint | PASS | Lượt Gradle cuối: 4 m 32 s/418 tasks, 36 executed, 382 up-to-date; lint 0 Error/0 Fatal/11 Warning |
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
| Build debug/release | PASS | Lượt cuối 7 m 30 s/403 tasks: 53 executed, 350 up-to-date |
| LintDebug | PASS có warning | 0 Error/0 Fatal/11 Warning, không suppress |
| Loại diagnostic khỏi release | PASS | apkanalyzer manifest+DEX: 0 diagnostic classes/entrypoints; release USB Host required true, debug optional (required false) |
| Java 8 | PASS | javac --release8 cho source TBS+debug, bytecode 52; output tạm đã dọn |
| UI diagnostic: kiểm metadata thủ công | PASS trên AVD thường | Install Success/explicit launch 7704 ms; không có USB Host, 0 devices/0 open/0 claim; không bấm request cycle vì không có thiết bị mục tiêu |

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
| Debug/lint | PASS | Lượt kết hợp exit 0/8 m 8 s/241 tasks: 50 executed, 191 up-to-date; lint 0 Error/0 Fatal/11 Warning, không suppress |
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
| Debug APK | PASS | AssembleDebug, install -r Success; lượt unit/build/lint cuối exit 0/2 m 8 s/177 tasks: 22 executed, 155 up-to-date |
| LintDebug | PASS có warning | XML 0 Error/0 Fatal/11 Warning, cùng nhóm cũ; không thêm suppression/baseline |
| No-tuner launch/UI | PASS trong cửa sổ smoke | Cold Status ok 3453 ms, Welcome/Diagnostic/START→NoDevicesFound đã được xử lý; log lọc PID 5124 không có fatal/linker/security/ANR của app |
| Emulator SystemUI | FAIL riêng môi trường, đã qua được | Hộp “System UI isn't responding”; chọn Wait bằng selector theo nội dung UI, app tiếp tục hoạt động. Không gộp vào ANR của app |
| Emulator/CLI startup | CLI FAIL→fallback PASS | CLI báo AVD không tồn tại dù có trong list, exit 0 sai; emulator.exe với AVD home chỉ định rõ cho boot_completed 1/API 37/x86_64 |
| Runtime API 21/23/31/33/34 | NOT RUN | Không có image/thiết bị tương ứng trong phạm vi, không tải thêm |
| USB permission dialog và protected USB attach/detach thật | NOT RUN | Callback tổng hợp không chứng minh delivery từ USB service hoặc identity phần cứng/re-enumeration |
| USB Host thật/open/claim/native cancel/firmware/frontend/TS | NOT RUN | Wrappers đã triển khai nhưng không kích hoạt với thiết bị thật; không control transport/uploader/frontend/streaming |

Lượt instrumentation kết hợp cuối exit 0/4 m 48 s/241 tasks: 53 executed, 188 up-to-date; XML gồm 19 drivers+2 dvbservice. Sau đó chỉ thêm 1 assertion sở hữu JVM và chạy lại unit/build/lint đạt 152 methods; không đổi production logic sau lần instrumentation. Các output kiểm tra nằm trong build directories, không commit. Chi tiết lệnh/AVD cleanup ở [WORKLOG.md](WORKLOG.md).

## 2026-11-03 11:25 +07 — Phase 2.2: concurrency và lifecycle fake

Windows PowerShell; `chore/modernize-android-build`/`6eb23537484a3e1dd263bd5a88f56914c7c5e313` cộng thay đổi trong working tree. Không emulator/tuner hoặc truy cập Android USB. Toolchain giữ AGP 9.5.0/Gradle 9.8.2/JDK 25/compile 37.2/min 21/Java 8. Phase 2.1/ADR-011 đã tích hợp tại SHA này; 83 methods PASS trước đó là lịch sử, không gộp vào kết quả mới.

| Gate | Kết quả thực | Bằng chứng |
|---|---|---|
| Tái hiện trên baseline trước sửa | FAIL được tái hiện, 2/2 methods | Fake controlTransfer bị latch giữ; thread detach/cancel không invalidate kịp, assertion invalidated.await FAIL; Gradle exit 1/36 s |
| Bridge regression | PASS 17 methods | Kiểm chính xác bytes/limits/directions, cặp 0x90/91 nguyên tử, descriptor 82, errors/no-retry; không đổi protocol |
| Firmware regression | PASS 19 methods | Plan/hash/ranges/chunks bất biến, chỉ dữ liệu tổng hợp; file implementation và tests không đổi |
| Session/readiness | PASS 17 methods | Vòng đời cũ và các mốc firmware/frontend tường minh; grant/open/claim/upload không tự READY, initialized không tự lock/stream |
| Concurrency | PASS 10 methods | Pointer bị block với detach/cancel; cleanup pending/chặn reopen; từ chối transaction cũ/đang xếp hàng; timeout muộn; callback detach không close trong I/O; release/close một lần kể cả close đồng thời; cleanup nhả cả hai khóa trước join worker; lỗi giữa hai nửa read không công bố bytes |
| Lifecycle coordinator | PASS 32 methods | Identity/generation/duplicates: 7; permission/lifecycle: 5; exception/ownership/cleanup: 12; acquisition bị block/detach/monitor: 8; owner bị từ chối và claim một phần không leak/close hai lần |
| Stress độc lập cuối | PASS 5 lượt×10 methods | Mỗi lượt có 40 vòng detach/cancel kiểm soát bằng latch →200 vòng qua 5 lượt; timeout hữu hạn, 0 fail; không tăng timeout để che race |
| Regression ba module | PASS 126 methods, 0 failure/error | drivers 112/usbxfer 1/dvbservice 13; từng task --rerun. TBS suites có 95 methods, trong đó thêm 43 methods so với Phase 2.1 |
| Debug build | PASS | :app:assembleDebug; Gradle cuối exit 0/2 m 23 s/177 tasks: 41 executed, 136 up-to-date |
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
| Bridge protocol | PASS, 17 methods | Kiểm chính xác EP0 type/request/value/index/buffer/offset/length/2000 ms; 0x80 write, 0x90→0x91 read, 0x8a định tuyến terrestrial |
| I²C validation | PASS trong Bridge suite | Địa chỉ 0..127, write 1..48/read 1..50, một byte register pointer; từ chối flags/address/buffer sai trước I/O |
| Atomicity/callback | PASS trong Bridge suite | Hai bridge cùng session không xen vào cặp read; detach trong pointer callback chặn nửa read, output của caller không đổi |
| Short/timeout/cancel/error | PASS trong Bridge suite | Đếm đúng số lần gọi, giữ nguyên cause; negative/overlong là I/O failure, 0..length-1 là short; lỗi chặn các lần gọi sau, không retry tác dụng phụ |
| Endpoint/chip/PLP policy | PASS trong Bridge suite | Descriptor đảo thứ tự vẫn chọn interface 0/alternate đã chọn/Bulk IN 0x82; từ chối khi thiếu hoặc mơ hồ; chip map tường minh, manual PLP 0 và range 0..255 |
| Firmware plan | PASS, 19 methods | Kích thước tổng hợp 1/63/64/65/128; đuôi khối chính xác/chunk≤64; stop 1 ở 0x7f92/0xe600 → data → restart 0 → delay 100 ms; metadata/hash/ranges/size/overflow/reserved/bản sao bảo vệ |
| Session/lifecycle | PASS, 16 methods | Permission deny/timeout event, detach, generation cũ, fault latch, streaming khác frontend lock, ownership một phần, reopen khi cleanup bị chặn |
| Firmware failure từng bước | PASS trong Session suite | Executor chỉ có trong test: lỗi ở từng DATA hoặc DELAY khiến UNCERTAIN, không READY, không thêm requests; đường thành công vẫn đòi ready event riêng |
| Cleanup | PASS trong Session suite | Cancel→release→close đúng tài nguyên đã acquire; close nhiều lần không lặp; IOException/RuntimeException không bỏ các bước tiếp; cancellation có thể join worker mà không giữ monitor của session trong đường close bình thường |
| Scripted fake | PASS qua Bridge/Session suites | Bản sao payload trước/sau, tham số/thứ tự chính xác; lời gọi ngoài dự kiến hoặc chưa dùng hết script làm test fail |
| Regression unit toàn module | PASS, 83 methods, 0 failures/0 errors | XML: drivers 69 (52 mới +17 cũ), usbxfer 1, dvbservice 13; các task đều có --rerun |
| Debug APK | PASS | :app:assembleDebug, lượt Gradle cuối exit 0/1 m 45 s/177 tasks |
| LintDebug | PASS có warnings | XML 0 Error/0 Fatal/11 Warning; không thêm baseline hoặc suppress kiểm tra |
| Java 8 core độc lập | PASS | javac --release 8 cho 4 core files không có Android classpath, javap major version 52; JDK 25 cảnh báo source/target 8 obsolete |
| References | PASS chỉ đọc | Ba SHA khóa giữ nguyên; status có 0 entries ở cả ba repositories |
| Android permission/reconnect/native USB/firmware thật/DVB lock/TS | NOT RUN | Không có production adapter/uploader/frontend; fake PASS không chứng minh Android USB Host hoặc tuner hoạt động |
| Emulator/instrumentation/release/16 KiB runtime | NOT RUN mới | Không yêu cầu chạy lại trong Phase 2.1; không tái sử dụng PASS Phase 1.2 như kết quả mới |
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
| Debug/release APK | PASS sau retry | 1048 FAIL unable-delete x86; 1059 exit 0/9 m 12 s, 325 tasks: 245 executed, 80 UP-TO-DATE |
| Unit tests chạy lại | PASS thực | Task --rerun từng module 1059; XML usbxfer 1/drivers 17/dvbservice 13=31 methods, 0 fail (1 utility) |
| Lint debug | PASS có warning | 1059 exit 0; XML 0 Error/Fatal/11 Warning, không baseline/suppress gate |
| Release AAB | PASS | 1067 bundleRelease exit 0/45 s, 157 tasks: 18 executed; vẫn có warning deprecated API |
| Native 4 ABI debug/release | PASS compile/link | APK có entry libUsbXfer cho arm64-v8a/armeabi-v7a/x86/x86_64; xác nhận timestamp output ELF mới |
| ZIP 16 KiB | PASS static | zipalign37 -c -P16 4 debug/release exit 0 |
| ELF 64 bit alignment | PASS static | llvm-readelf NDK 30: arm64/x86_64 LOAD 0x4000 debug/release; 32 bit 0x1000 ghi riêng |
| Instrumentation registry XML/native-load | PASS thực | 1055 exit 0/5 m 38 s, XML 2 tests, 0 fail, 0 error; đủ cả 2 classnames/methods |
| Android launch/no-tuner/insets regression | PASS trong smoke | Install Success, COLD Status ok/10080 ms; UI hierarchy có inset top 63/bottom 63; NoDevicesFound đã xử lý, Back về Welcome |
| App crash/linker/security | PASS trong cửa sổ test | Log lọc PID app 5345 có 0 fatal/linkage/security; không gộp ANR launcher/SystemUI thành lỗi app |
| AVD boot/host responsiveness | FAIL ban đầu→hồi phục | Timeout 4 phút, sau đó getprop boot 1; launcher/SystemUI ANR hết sau Wait; không wipe/reset |
| AVD shutdown | PASS | Đúng avd name→emu kill; emulator process 1051 exit 0, ADB list cuối rỗng |
| Runtime API 21/arm64/16 KiB, USB Host/service | NOT RUN | Chỉ Android 17/API 37/x86_64/page 4096; không tuner/fixture |
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
| Cold launch Welcome | PASS | am start-W: Status ok/COLD, TotalTime 6606 ms; PID 3840 còn sống, không fatal của app |
| UI system bars trước sửa | FAIL | Screenshot/hierarchy: hàng tune ở y 0, Close đến 2400 bị system bars che |
| UI system bars sau sửa | PASS runtime 37 | SystemBarInsets API 35+; tune top 63, buttons max bottom 2337 so với screen 2400; status icons tối, đọc được |
| Welcome/License/OK | PASS | Tap theo UI hierarchy vào GplLicenseActivity rồi về Welcome |
| Diagnostic/Start không tuner | PASS | Dialog No devices found; DvbException đã xử lý tại DeviceController:64, Hardware unchecked, Tune/Record disabled; PID còn sống |
| Activity Home/resume/Back/Close/relaunch | PASS | Home→am start đưa task về Diagnostic; Back→Welcome; Close ra Launcher; warm relaunch 1796 ms |
| Entry dtvdriver chooser không tuner | PASS có giới hạn | ACTION_VIEW dtvdriver:// mở Chooser rồi finish về Welcome, không crash; không có assertion cho caller result code |
| App crash/ANR/linker/security | PASS trong smoke window | Log lọc PID 6344 có 0 fatal/linkage/security/ANR app; exit-info chỉ có PACKAGEUPDATED do install-r |
| Service foreground/streaming lifecycle | NOT RUN | Không device/fixture, không ép startService; không có ServiceRecord của DvbService |
| USB permission/transfer | NOT RUN | Emulator không có usb.host, không gắn tuner; không fd giả/ioctl |
| Storage permission no-tuner path | PASS có giới hạn | READ/WRITE external=false nhưng UI/đường chọn no-device vẫn chạy; chưa test record TS |
| libUsbXfer x86_64 trong APK | PASS | ZIP entry lib/x86_64/libUsbXfer.so: 7320 bytes |
| Native library runtime loading | PASS | NativeLibrarySmokeTest kiểm IS_PLATFORM_SUPPORTED+gọi trực tiếp System.loadLibrary; 0 USB requests/ioctl |
| Registry/XML instrumentation | PASS | Assertion DeviceFilterXmlEquivalenceTester.allDevicesAreInXml giữ nguyên |
| Full connectedDebugAndroidTest | PASS | 1019 exit 0/49 s, XML testsuites: 2 tests, 0 fail, 0 error; đủ cả 2 classname/testmethod |
| Build debug/test APK và lint | PASS | 1006 exit 0/2 m 4 s; 1016 assembleDebug/lintDebug exit 0/1 m 54 s; lint 0 errors, 11 warnings |
| Unit regression sau thay đổi | PASS task validation | 1028 exit 0/32 s, 39 tasks UP-TO-DATE; không gọi là 31 methods đã chạy lại mới |
| Runtime API 21/arm64/16 KiB | NOT RUN | Chỉ API 37 x86_64 page 4 KiB; không đánh đồng ELF alignment với runtime 16 KiB |
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
| Debug APK | PASS | assembleDebug 968 exit 0, chốt 983 exit 0/36 s; 4 ABI native |
| Unit usbxfer | PASS | XML 1 method, 0 failure |
| Unit drivers | PASS | XML 17 methods, 0 failure (1 ConvertCString utility) |
| Unit dvbservice | PASS | XML 13 methods, 0 failure sau sửa Mockito/null/newline; 974 và 983 |
| Instrumentation test APK compile | PASS | assembleDebugAndroidTest, package trong 974; chốt 983 exit 0; APK đã sinh |
| Instrumentation chạy thiết bị | NOT RUN | Không emulator/device serial, không install hoặc boot |
| Lint debug | PASS có warning | 980 exit 0, XML 0 Error/Fatal/11 Warning; không baseline/suppress/abortOnError=false |
| Release APK unsigned | PASS | 980 exit 0/3 m 54 s, app-release-unsigned.apk 5466380 bytes; không signing/publish |
| Release AAB | PASS | 980 exit 0, app-release.aab 4782669 bytes; không upload |
| Native 4 ABIs debug/release | PASS compile/link | arm64-v8a/armeabi-v7a/x86/x86_64; từng bước CMake hoàn tất |
| ZIP 16 KiB debug/release | PASS static | zipalign37 -c -P16 4 exit 0 cho 2 APK |
| ELF 64 bit alignment | PASS static | llvm-readelf NDK 30: LOAD 0x4000 cho arm64-v8a/x86_64 debug+release; 32 bit 0x1000 ghi riêng |
| Native-load trên Android 16 KiB | NOT RUN | Static alignment không chứng minh runtime/USB ioctls |
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
| Không fetch ngầm trong toàn audit | BLOCKED | Không fetch tường minh; diff --cached --shortstat đầu bị dừng sau 30 s; FETCH_HEAD có mtime mới nhưng không có baseline để xác định tác nhân. Các lần đọc sau chặn lazy fetch |
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
| Assemble/lint/unit/instrumentation/native ABI/16 KiB | NOT RUN | Chưa duyệt chạy build hoặc tạo emulator; chưa có APK/binary evidence |
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
