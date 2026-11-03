# TBS5530 test matrix

## 2026-11-03 11:25 +07 — Phase 2.2: concurrency và lifecycle fake

Windows PowerShell; `chore/modernize-android-build`/`6eb23537484a3e1dd263bd5a88f56914c7c5e313` cộng working changes. Không emulator/tuner/Android USB access. Toolchain giữ AGP9.5.0/Gradle9.8.2/JDK25/compile37.2/min21/Java8. Phase2.1/ADR-011 đã tích hợp tại SHA này;83methods PASS trước đó là lịch sử, không gộp vào kết quả mới.

| Gate | Kết quả thực | Bằng chứng |
|---|---|---|
| Reproduction trên baseline trước sửa | FAIL được tái hiện,2/2methods | Fake controlTransfer bị latch giữ; detach/cancel thread không invalidate kịp, assertion invalidated.await FAIL; Gradle exit1/36s |
| Bridge regression | PASS17methods | Exact bytes/limits/directions, atomic0x90/91, descriptor82, errors/no-retry; không đổi protocol |
| Firmware regression | PASS19methods | Synthetic-only immutable plan/hash/ranges/chunks; file implementation và tests không đổi |
| Session/readiness | PASS17methods | Lifecycle cũ + explicit firmware/frontend milestones; grant/open/claim/upload không tự READY, initialized không tự lock/stream |
| Concurrency | PASS10methods | Blocked pointer +detach/cancel; pending cleanup/reopen gate; old/queued transaction rejected; late timeout; callback detach không close trong I/O; release/close một lần kể cả concurrent close; cleanup nhả cả hai khóa trước join worker; error giữa read halves không publish bytes |
| Lifecycle coordinator | PASS32methods | Identity/generation/duplicates7; permission/lifecycle5; exception/ownership/cleanup12; blocked acquisition/detach/monitor8; rejected owner và partial claim không leak/double-close |
| Stress độc lập cuối | PASS5lượt×10methods | Mỗi lượt có40latch-controlled detach/cancel iterations →200vòng qua5lượt; timeout hữu hạn,0fail; không tăng timeout để che race |
| Regression ba module | PASS126methods,0failure/error | drivers112/usbxfer1/dvbservice13; từng task --rerun. TBS suites95methods, trong đó43methods thêm so Phase2.1 |
| Debug build | PASS | :app:assembleDebug; final Gradle exit0/2m23s/177tasks41executed136up-to-date |
| LintDebug | PASS có warning | XML0Error/0Fatal/11Warning; không baseline/suppress mới |
| Java8 compatibility | PASS | javac --release8 cho5corefiles và toàn TBS test package, bytecode52; chỉ JUnit/Hamcrest sẵn có, không Android classpath |
| IDE diagnostics | PASS tại kiểm tra | Không errors ở main/test tbs5530 packages |
| Existing instrumentation/native-load/UI | NOT RUN | JVM-only coordinator chưa gắn Android runtime; không sửa framework binding/JNI/manifest/app. Không coi kết quả lịch sử Phase1.2 là PASS mới |
| Real permission dialog/USB transfer/firmware/native cancel/lock/TS | NOT RUN | Không concrete Android transport/binding/uploader/frontend; fake PASS không chứng minh phần cứng |

Lượt stress đầu bị lỗi tên class trong lệnh JUnitCore trước khi chạy tests, đã sửa invocation và chạy đủ5lượt thành công; không sửa assertion. Chi tiết commands/errors ở [WORKLOG.md](WORKLOG.md). Deferred cleanup và quyền sở hữu ghi [ADR-012](DECISIONS.md).

## 2026-11-03 10:39 +07 — Phase 2.1: Java core và USB giả

Windows PowerShell, nhánh `feat/tbs5530-offline-protocol`, HEAD nền `8588ee7661003052b7d1a0353e23bb3a3dcef8b5` cộng thay đổi chưa commit. Gradle9.8.2/AGP9.5.0/JDK25, compile37.2/target37/min21/Java8 giữ nguyên. Không emulator hoặc tuner; mọi giao dịch dưới đây chỉ qua fake.

| Gate | Kết quả thực | Assertion/bằng chứng |
|---|---|---|
| Bridge protocol | PASS, 17 methods | Exact EP0 type/request/value/index/buffer/offset/length/2000ms; 0x80 write, 0x90→0x91 read, 0x8a terrestrial route |
| I²C validation | PASS trong Bridge suite | Địa chỉ 0..127, write1..48/read1..50, một byte register pointer; từ chối flags/address/buffer sai trước I/O |
| Atomicity/callback | PASS trong Bridge suite | Hai bridge cùng session không xen vào cặp read; detach trong pointer callback chặn nửa read, output caller không đổi |
| Short/timeout/cancel/error | PASS trong Bridge suite | Đếm đúng số lần gọi, giữ nguyên cause; negative/overlong là I/O failure, 0..length-1 là short; lỗi chặn các lần gọi sau, không retry side effect |
| Endpoint/chip/PLP policy | PASS trong Bridge suite | Descriptor đảo thứ tự vẫn chọn interface0/alternate đã chọn/Bulk IN0x82; missing/ambiguous reject; chip map explicit, manual PLP0 và range0..255 |
| Firmware plan | PASS, 19 methods | Synthetic sizes1/63/64/65/128; exact tail/chunk≤64; stop1 ở 0x7f92/0xe600 → data → restart0 → delay100ms; metadata/hash/ranges/size/overflow/reserved/defensive copies |
| Session/lifecycle | PASS, 16 methods | Permission deny/timeout event, detach, generation cũ, fault latch, streaming khác frontend lock, ownership một phần, reopen khi cleanup bị chặn |
| Firmware failure từng bước | PASS trong Session suite | Executor chỉ có trong test: lỗi ở từng DATA hoặc DELAY khiến UNCERTAIN, không READY, không thêm requests; đường thành công vẫn đòi ready event riêng |
| Cleanup | PASS trong Session suite | Cancel→release→close đúng tài nguyên đã acquire; close nhiều lần không lặp; IOException/RuntimeException không bỏ các bước tiếp; cancellation có thể join worker mà không giữ monitor của session trong đường close bình thường |
| Scripted fake | PASS qua Bridge/Session suites | Bản sao payload trước/sau, exact parameters/order, unexpected call hoặc chưa dùng hết script làm test fail |
| Regression unit toàn module | PASS, 83 methods, 0 failures/0 errors | XML: drivers69 (52 mới +17 cũ), usbxfer1, dvbservice13; các task đều có --rerun |
| Debug APK | PASS | :app:assembleDebug, lượt Gradle cuối exit0/1m45s/177 tasks |
| LintDebug | PASS có warnings | XML0Error/0Fatal/11Warning; không thêm baseline hoặc suppress kiểm tra |
| Java8 core độc lập | PASS | javac --release 8 cho4core files không Android classpath, javap major version52; JDK25 cảnh báo source/target8 obsolete |
| References | PASS chỉ đọc | Ba SHA khóa giữ nguyên; status0entries ở cả ba repositories |
| Android permission/reconnect/native USB/firmware thật/DVB lock/TS | NOT RUN | Không có production adapter/uploader/frontend; fake PASS không chứng minh Android USB Host hoặc tuner hoạt động |
| Emulator/instrumentation/release/16KiB runtime | NOT RUN mới | Không yêu cầu chạy lại trong Phase2.1; không tái sử dụng PASS Phase1.2 như kết quả mới |
| TS packet fixture và PLP retune register sequence | NOT RUN, hoãn | Kế hoạch Phase2.0 rộng hơn phạm vi core hiện tại; chưa có TS reader/frontend để test đúng hành vi |

Lệnh cuối và vị trí XML ghi ở [WORKLOG.md](WORKLOG.md). Các bảng “Phase2.1 planned assertions — tất cả NOT RUN” bên dưới giữ làm lịch sử thiết kế; bảng mới này là kết quả thực thi hiện tại.

## 2026-11-03 00:54 +07 — Xác minh sau phục hồi NTFS do người dùng thực hiện

Windows PowerShell; TBS latest/2ce787de9f6e81b4294692a45de15596ae2e1322. Agent kiểm chỉ đọc với no-lazy-fetch/optional-locks0; không thực thi recovery.

| Gate | Kết quả | Bằng chứng |
|---|---|---|
| HEAD/branch giữ nguyên | PASS | rev-parse/branch: đúng SHA đã khóa/latest |
| Index và working tree | PASS | index có, status0entries, index.lock không có |
| Sáu sparse paths | PASS | sparse-checkout list đúng phạm vi |
| Manifest file thực | PASS |1.357expected/actual,0missing/extra |
| Nội dung so HEAD | PASS |hash-object --stdin-paths:0mismatch trên1.357file |
| Source TBS/Sony/Montage/helpers | PASS hiện diện |Test-Path và manifest xác nhận, không chỉ object local |
| Persistent protectNTFS override | PASS không đổi |configqueryexit1, không override lưu |
| Backup | PASS hiện diện |backup004911-951/git vàafter.json PASS/1357/đúngSHA; không xóa |
| C/Go references | PASS sạch |status0entries |
| Full kernel build / Android driver / USB hardware | NOT RUN |Phục hồi source không chứng minh firmware/tuner/TS hoạt động |

Các trạng thái index/checkout BLOCKED trong snapshot trước được bảo toàn làm lịch sử, không còn là kết luận mới nhất.

## 2026-11-03 — Phase2.0 source readiness và kế hoạch offline

WindowsPowerShell, nhánh docs/tbs5530-source-architecture, baseline4cf1b64e03a563f66cfb2b2051fb6f4ed05eacbe. SourceSHA TBS2ce787de9f6e81b4294692a45de15596ae2e1322/Cc5fa27eecbe80633cbb365cc44656daea1c0e2e3/Go11228b05fb1626bf41c4e1879ace10cd0a8d575c. Khôngbuild/driver/hardwaretest trongphase này.

| Kiểm tra thực | Trạng thái | Bằng chứng mới |
|---|---|---|
| Baseline main và tạo nhánh tài liệu | PASS |Statussạch,HEAD4cf1b64;git switch-c docs/tbs5530-source-architecture,modernizationkhôngđổi |
| 3referenceidentities | PASS |rev-parse/branch/remotesđúngmốc;C/Go0changes |
| TBS index/working tree | FAIL, checkoutBLOCKED |Index/lockkhôngcó,ls-files0,status81.399D,sáu thưmục đều0file |
| TBS sparseconfiguration | PASS configonly |Sáupaths,cone/sparseflagtrue;khôngphảifilesPASS |
| SelectedGitobjects nghiên cứu | PASS có phạm vi |785/785bloblocal,0missing,15.372.887bytes;no-lazy-fetch |
| Coneancestorobjects | PASS hiện tại |572/572local,0missing;khác lịch sử572missing,khôngtảitrongphase |
| 1.357cone filenamebasiccheck | PASS có giới hạn |0reservedWindowsnamestrongtậpmaterialize;khôngchứngnhậnfilesystem/fulltree |
| Board/Sony/Montage/frameworkdeps | PASS sourceobjects/FAIL disk |Tbs5530/cxd2878/m88rs6060headers+helpers/directLinuxheaderscótree/blob,khôngdisk;khôngfullkernelbuildgate |
| Windowsdefaultindexdry-run | FAIL tái hiện an toàn |read-tree --dry-run -m -u HEAD exit128 invalidpathAUX,nằmngoàisparse;index/lockvẫnkhôngcó |
| Recovery/fetch/WSLcopy | NOT RUN |Kếhoạchbackup/rollback/gateschờphêduyệt;khôngconfigoverridehoặcreset |
| Protocol/PLP/chip/retrydesign | PASS sourceinspection |Exactfile/functions/SHA và khácbiệt ghiRESEARCH;khônghardwareproof |
| Toolchain build/unit/lint/runtime | NOT RUN mới |KếtquảPhase1.2làlịchsử;khôngrerunđểgọiPASSmới |
| Firmware/chipID/USBpermission/lock/TSthật | NOT RUN |KhônghardwareIO,cóchặnfirmwarelicense/provenance/addressmap |

### Phase2.1 planned assertions — tất cả NOT RUN

| Nhóm | GatePASS dự kiến | GateFAIL dự kiến |
|---|---|---|
| Controlencoding |Exactrequesttype/request/value/index/length/timeoutvàorderedpayloadbytes |Saihoặcthiếuassertion,silentshorttransfer |
| I2Cwrite/read |7bitaddress,write48/read50guard,90+91atomic,invalidinput0IO |Bare-read/TEN/multipointer/mismatchedaddr/overflowbịgửi |
| Firmwarebinaryplan |Syntheticdata/hash/allowedranges/chunks1/63/64/65/128/partialtailđúng;reservedaddressreject |Binarythậtkhôngapproved,overread,capđơnlẻcheoverlap |
| Firmwarefakefailure |Failuremỗistep→FAULTED,khôngREADY/automaticreset/reupload |RetrysideeffecthoặcrestartCPUsaulỗi |
| Endpointmapping |Permuteddescriptorsluônchọninterface0/BULKIN0x82,ambiguityreject |Chọnfirst-IN0x81hoặcđòiOUT0x01 |
| Timeout/retry |Errorcausegiữ,physicalIOcountkhônglặp;faultlatchchặnbase10retryIO |Ignoredtimeout/nestedretryreplay |
| Session/generation |Permissiondeny/detach/latecallback0writes,oldfdinvalidated |Oldsessioncallbackmởdevice/tảifirmware |
| Cleanup |Cancel→release→closeexactlyonce,từngfailacquire/close2lầnkhôngleak |Fdclosewhileworkersactive,double-release,cleanupvendorwrites |
| PLP/chip/valuepolicy |Manual0rõràng,0..255validate,IDmapexplicit,PLPchangecầnewrites |“Auto”claimkhôngbằngchứng,Goactiveretuneno-op,otherIDFreiafallback |
| TSfake/compatibility |188bytefixtureorder/partialreads/oldpacketdrain,wireenumunchanged |Compiles-onlytest,streamingcoilàlock,protocolordinalđổi |

Các plannedcases chỉ ra yêu cầu cho fake/core, không viết productionCXDtables/JNI hoặc testUSBthật. Fixtures/filelist/commands và Phase2.1exitcriteria xemRESEARCH. Documentationvalidation(diff/UTF8/links)ghiWORKLOGsaukhibổsungxong;khôngbiếnbảnthiếtkếthànhtestPASS.

## 2026-11-02 — Phase 1.2: AGP 9.5.0 và toolchain audit

Windows10.0.19045/PowerShell7.6.9; branch chore/modernize-android-build, HEAD nền ace7e9f187d11a685975f707b6743d779fa847d9, diff chưacommit. AGP9.5.0, Gradle9.8.2/JDK25, compile37.2/target37/min21/Java8, NDK30/CMake4.1.2/BT37; khônghardware.

| Gate | Trạng thái cuối | Bằng chứng |
|---|---|---|
| AGP9.5.0 stable/compatibility | PASS nguồn |GoogleCurrentRelease9.5.0, finalrelease notes, exactGoogleMavenPOM; Gradlemin9.6/JDKmin17; không dùngRC |
| Giữ versions còn lại | PASS |Wrapper/checksum/modulepins không đổi; --versionJVM25/Gradle9.8.2 |
| SDK inventory sau cleanup | PASS |AuditJSON/CLI/source.properties đối chiếu;34+37.2platform, BT37/NDK30/CMake4.1.2; khôngcài/gỡ |
| Audit script chạy theo policy | BLOCKED ban đầu→PASS sau approval |RemoteSignedchặn; userchỉđịnh BypassScopeProcess; try/finallyrestoreUndefined,khôngUser/Machinepolicychange |
| AVD discovery ban đầu | FAIL đã sửa |Emulator0names/disk2names; process không nhậnUserAVDhome, directorysuffixgâyphantomname |
| AVD discovery sau sửa | PASS |Một TBS5530_Runtime_API37; emulator1/metadata1, envcallerrestored |
| CMake parser/cleanup review | PASS |externalNativeBuildcmake=4.1.2, không BT37; reviewCSV chỉKEEP/REVIEW,0uninstall |
| PowerShell regression suite | PASS thực |CMakewrong-scope, AVDdedupe, childenv, absent/presentenvrestore, metadatafallback; fixturetemp đãxóa |
| JDK25 build environment | PASS child / FAIL stale parent |Machinevalid, Processoldinvalid; GradleLauncher/Daemon25xácnhận,khôngsửaglobal |
| Debug/release APK | PASS sau retry |1048FAILunable-deletex86;1059exit0/9m12s,325tasks245executed,80UP-TO-DATE |
| Unit tests chạy lại | PASS thực |Task --rerun từngmodule1059; XMLusbxfer1/drivers17/dvbservice13=31methods0fail (1utility) |
| Lintdebug | PASS có warning |1059exit0; XML0Error/Fatal/11Warning,khôngbaseline/suppressgate |
| Release AAB | PASS |1067bundleReleaseexit0/45s,157tasks18executed; warningsdeprecatedAPIvẫncó |
| Native4ABI debug/release | PASS compile/link |APKentrylibUsbXferarm64-v8a/armeabi-v7a/x86/x86_64;ELFoutputtimestampsmớixácnhận |
| ZIP16KiB | PASS static |zipalign37 -c -P16 4 debug/releaseexit0 |
| ELF64bit alignment | PASS static |llvm-readelf NDK30 arm64/x86_64LOAD0x4000 debug/release;32bit0x1000riêng |
| InstrumentationregistryXML/native-load | PASS thực |1055exit0/5m38s, XML2tests0fail0error; cả2classnames/methods |
| Androidlaunch/no-tuner/insets regression | PASS trong smoke |InstallSuccess,COLDStatusok/10080ms,UIhierarchyinsettop63/bottom63,handledNoDevicesFound,BackvềWelcome |
| Appcrash/linker/security | PASS trong cửa sổ test |Filtered appPID5345log0fatal/linkage/security; khônggộpANRlauncher/systemUIthànhappfailure |
| AVD boot/host responsiveness | FAIL ban đầu→hồi phục |Timeout4phút, saugetpropboot1; launcher/SystemUIANRWaitrồihết; khôngwipe/reset |
| AVDshutdown | PASS |Đúngavdname→emukill; emulatorprocess1051exit0,ADBlistcuốirỗng |
| RuntimeAPI21/arm64/16KiB,USBHost/service | NOT RUN |ChỉAndroid17/API37/x86_64/page4096; khôngtuner/fixture |
| TBS firmware/I2C/reset/LNB/TS | NOT RUN |Khôngcommandhardware/referencewrite |

**Artifacts mới:** debugAPK SHA2566D6E1C4953F708CA04E9DC5AD223168514C3745F27106E3AF12033C884D44D53; releaseunsignedAPK7B126F04109EA1F9295336EF8615C8ACCC3743BE8488497BF8DE7EA40AAB52F3. InstrumentationXMLcopy ngoàiGit SHA25603269F9A8C91B106FF415A015756384CB0CA3EC4A0CDDE15AC0B3341534B02A6. Khôngdùngbáocáocũ làmPASSmới.

**Giới hạn:** source/target8obsoletewarning và Configuration.setVisibledeprecation vẫn xuất hiện trênAGP9.5; không khẳng định đã hết warning vì release notes có fixedissue. Không gọiAABsigned/published. Khôngtựcommit/merge/push hoặcSDKcleanup.

## 2026-11-02 — Phase1.1 Android runtime, 19:31–20:05 +07

Branch `test/android-runtime-smoke`, HEAD6da609ba49dd9f1f4d284291918f68a6378972ff từ modernization đã push. Runtime AVDTBS5530_Runtime_API37/Android17/API37/x86_64/page4096; không tuner. WindowsPowerShell/SDKD:\Android\Sdk; toolchain không nâng.

| Gate | Kết quả | Bằng chứng thực |
|---|---|---|
| Giữ Git/local changes khi tạo branch | PASS |2filelocal `.gitignore`/workspace giữ hash; branchmới cùng6da609b, không stash/reset/commit |
| Environment build versions | PASS có lưu ý |wrapper--versionJDK25/Gradle9.8.2; scriptsAGP9.4.1/API37.2/NDK30/CMake4.1.2/BT37; processenvnạp riêng |
| AVD/image có sẵn | PASS |TạoAVDriêngsauapproval; dùng37.0x86_64local, không tảiSDK/image |
| Emulator firstboot/SystemUI | FAIL ban đầu, hồi phục |ANRcom.android.systemui overlay; chọnWait, hết overlay. Không quy cho app |
| Cài APKdebug | PASS |adb explicitserialinstall-r Success cả baseline và bảnfix |
| Cold launchWelcome | PASS |amstart-WStatusok/COLD,TotalTime6606ms; PID3840sống, không appfatal |
| UI system bars trước fix | FAIL |Screenshot/hierarchy hàngtune y0,Closeđến2400 bị systembarsche |
| UI system bars sau fix | PASS runtime37 |SystemBarInsetsAPI35+; tunetop63,buttonsmaxbottom2337 so screen2400; statusiconsdarkđọcđược |
| Welcome/License/OK | PASS |Tap theo UIhierarchy, GplLicenseActivity rồi vềWelcome |
| Diagnostic/Start không tuner | PASS |DialogNo devices found,handledDvbExceptionDeviceController:64,Hardwareunchecked,Tune/Recorddisabled; PIDsống |
| Activity Home/resume/Back/Close/relaunch | PASS |Home→amstartđưa task vềDiagnostic;Back→Welcome;CloseraLauncher;warmrelaunch1796ms |
| Entry dtvdriver chooser không tuner | PASS có giới hạn |ACTION_VIEWdtvdriver:// mởChooser rồi finishvềWelcome,không crash; không assertioncallerresultcode |
| App crash/ANR/linker/security | PASS trong smoke window |PID6344filteredlog0fatal/linkage/security/appANR; exit-info chỉPACKAGEUPDATEDdo install-r |
| Service foreground/streaming lifecycle | NOT RUN |Khôngdevice/fixture,không épstartService; không cóDvbServiceServiceRecord |
| USB permission/transfer | NOT RUN |Emulator khôngusb.host,tunerkhônggắn; không fd giả/ioctl |
| Storage permission no-tuner path | PASS có giới hạn |READ/WRITEexternal=false nhưng UI/chọnno-devicevẫnchạy; chưa testrecordTS |
| libUsbXfer x86_64 trong APK | PASS |ZIPentrylib/x86_64/libUsbXfer.so7320bytes |
| Native library runtime loading | PASS |NativeLibrarySmokeTestassertIS_PLATFORM_SUPPORTED+directSystem.loadLibrary;0USBrequests/ioctl |
| Registry/XML instrumentation | PASS |DeviceFilterXmlEquivalenceTester.allDevicesAreInXml assertion giữ nguyên |
| Full connectedDebugAndroidTest | PASS |1019exit0/49s,XMLtestsuites2tests0fail0error,cả2classname/testmethod |
| Builddebug/testAPK và lint | PASS |1006exit0/2m4s;1016assembleDebug/lintDebugexit0/1m54s;lint0errors11warnings |
| Unit regression sau thay đổi | PASS task validation |1028exit0/32s,39tasksUP-TO-DATE,không gọi31methodsđãchạylạimới |
| RuntimeAPI21/arm64/16KiB | NOT RUN |ChỉAPI37x86_64page4KiB; không đánh đồng ELFalignvớiruntime16KiB |
| Workspace/ignore/instructions | PASS config |Exceptionđúngđãcólocal;4relativepathsresolve;2instructions cập nhậtEnglish; khôngstage/commit |
| References/hardware | PASS giữ nguyên/NOT RUN hardware |TBSindexthiếu81399Dsourcefilethiếu,SHAskhôngđổi,C/Go sạch; khôngsync/usbipd/firmware/vendorwrite |

**Coverage trap đã xử lý:** lệnh Gradle filtered2classđầu1012exit0 nhưng XMLchỉ1registrytest; không gọi đó là nativePASS. Directam instrument trước installtestAPK lỗi componentmissing; sau install-r1018native1testPASS. Rerun task **không filter**1019XML2tests xác nhận đủ. Không bỏtest/assertion, không tạoPASS chỉ từexitcode.

**Evidence:** screenshots before/after/no-tuner, XML2tests và logfiltered lưu ngoàiGit trong sessionfiles/phase-1-1. APK/hash và exactcommands ở WORKLOG/ENVIRONMENT. Emulator shutdown ghiWORKLOG; khôngxóadata.

## 2026-11-02 — Migration JDK25: kiểm chứng đã chạy

Windows10.0.19045/PowerShell7.6.9, JDK25.0.5.0.3; nhánh chore/modernize-android-build trên nền e4c13a5b86ad5ef9be887df58226046dcb42dcd5, chưa commit. Gradle9.8.2/AGP9.4.1; NDK30/CMake4.1.2/BT37/API37.2; min21.

| Gate | Kết quả cuối | Bằng chứng thực |
|---|---|---|
| JVM25 chạy Gradle9.8.2 | PASS | wrapper --version shell963exit0, Launcher/daemon JDK25; regenerated wrapper task973exit0 |
| Package toolchain | PASS | android sdk install đúng4package966exit0 + source.properties; NDK meta min21 |
| Debug APK | PASS | assembleDebug968exit0, chốt983exit0/36s;4ABI native |
| Unit usbxfer | PASS | XML1method,0failure |
| Unit drivers | PASS | XML17methods,0failure (1ConvertCString utility) |
| Unit dvbservice | PASS | XML13methods,0failure sau Mockito/null/newline fixes;974 và983 |
| Instrumentation test APK compile | PASS | assembleDebugAndroidTest, package trong974; chốt983exit0; APK đã sinh |
| Instrumentation chạy thiết bị | NOT RUN | Không emulator/device serial, không install hoặc boot |
| Lint debug | PASS có warning |980exit0, XML0Error/Fatal/11Warning; không baseline/suppress/abortOnError=false |
| Release APK unsigned | PASS |980exit0/3m54s, app-release-unsigned.apk5466380bytes; không signing/publish |
| Release AAB | PASS |980exit0, app-release.aab4782669bytes; không upload |
| Native4ABIs debug/release | PASS compile/link |arm64-v8a/armeabi-v7a/x86/x86_64; từng bước CMake hoàn tất |
| ZIP16KiB debug/release | PASS static |zipalign37 -c -P16 4 exit0 cho2APK |
| ELF64bit alignment | PASS static |llvm-readelf NDK30: LOAD0x4000 choarm64-v8a/x86_64 debug+release;32bit0x1000 ghi riêng |
| Native-load trên Android16KiB | NOT RUN |Static alignment không chứng minh runtime/USB ioctls |
| minSdk/targetSdk APK | PASS metadata |merged manifest min21,target37; không overrideLibrary; aapt2native4ABIs |
| RuntimeAndroid21/37/UI/permission/service | NOT RUN |Chưa chọn Android/AVD; build không chứng minh behavior target37 |
| TBS reference không tải/sửa | PASS theo phạm vi giữ nguyên |SHA3refs không đổi,C/Go sạch,TBS vẫn81399D;không Apply/fetch572objects |
| Firmware/I2C/LNB/lock/TS thật | NOT RUN |Không driver mới hoặc hardware writes |

**Các lần FAIL trước PASS:** configuration965thiếuNDK; debug967thiếu LocalBroadcastManager; unit971hai fail; release974/977native output directory Windows. Sau sửa đúng dependency/tests và chạy tuần tự max-workers1:979native riêngPASS,980release/lintPASS;983debug/tests/instrumentationcompilePASS. Không xóa test/suppress lint để né lỗi.

**Warnings tồn tại:**11lint (GradleDependency2do pin API21,UnusedResources3,IconLauncherShape3,TextFields/Autofill/LabelFor mỗi1), source/target8obsolete, AGP dùng APIGradledeprecated. Giữ kế hoạch xử lý riêng, không tuyên bố zero-warning hoặc mọi library latest tuyệt đối.

## 2026-11-02 12:52 +07 — JAVA_HOME recheck và dry-run TBS

Windows PowerShell; main SHA `e4c13a5b86ad5ef9be887df58226046dcb42dcd5`; TBS SHA `2ce787de9f6e81b4294692a45de15596ae2e1322`. Không chạy build hoặc khôi phục reference.

| Kiểm tra | Trạng thái | Kết quả thực |
|---|---|---|
| Machine JAVA_HOME hợp lệ sau người dùng sửa | PASS | `C:\Program Files\Java\latest\jdk-25`, thư mục/executable tồn tại, java-version25.0.5.0.3 |
| Process JAVA_HOME hiện tại hợp lệ | FAIL | Agent vẫn nhận đường dẫn cũ không tồn tại từ process cha |
| Child đọc Machine JAVA_HOME và chạy java | PASS | Chỉ cập nhật env trong child; executable chạy exit0 |
| TBS index/source readiness | FAIL | Index/index.lock thiếu,81.399 D;6 selected dirs đều0 file |
| Blob selected directories local | PASS |785/785 blob tồn tại, no-lazy-fetch |
| Blob ancestor của cone đầy đủ local | FAIL |572/572 blob ancestor thiếu; cần approval tải nếu giữ cone |
| Tên file sẽ materialize trên Windows | PASS | Kiểm tên cơ bản1357 cone paths,0 reserved/invalid; không thay thế checkout test |
| read-tree dry-run mặc định | FAIL | exit128, `invalid path .../i2c/aux.c`, ngoài sparse nhưng có trong full index |
| read-tree dry-run command-scoped protectNTFS=false | PASS | exit0; index vẫn không tồn tại; không lưu config; chưa chứng minh restore thật |
| Script phục hồi bàn giao — cú pháp/preview | PASS | Parser0errors; chạy không tham số:1357cone files/572missing, index không được tạo; SHA256 trong RESEARCH |
| Script Apply/backup/fetch/khôi phục thực | NOT RUN | Người dùng chọn nhận bộ lệnh tự chạy; agent chỉ preview, chưa kiểm end-to-end |
| Gradle/build/hardware | NOT RUN | Ngoài phạm vi; JDK25 vẫn không hỗ trợ wrapper8.7 |

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
| Android CLI và explicit SDK inventory | PASS | `android --help`, `-V` 1.1.16717390; `--sdk=D:\Android\Sdk info sdk/sdk list` exit0 |
| SDK package versions | PASS | SDK list + source.properties xác nhận 34/37.0, BT36, NDK28.2, CMake3.22.1, CLI tools23; ADB37.0.1, Emulator37.2.13 |
| Android docs capability | PASS | `docs search` USB Host + `docs fetch kb://android/develop/connectivity/usb/host`; CLI tự tạo/tải KB cache, không đổi SDK packages |
| WSL2 hiện có | PASS | `wsl.exe --list --verbose`: Ubuntu-22.04 Running, v2; chưa kiểm kernel lại |
| usbipd executable/version | PASS | Full path Program Files\usbipd-win\usbipd.exe, 5.3.0-54; process PATH không thấy |
| TBS visible / USB ownership | PASS | `usbipd list`: VID:PID734c:5530, BUSID đọc live 6-2, Shared; phía Windows, chưa xác định app giữ handle |
| WSL attach/lsusb/PyUSB trong phiên này | NOT RUN | Thiết bị không Attached; không đổi ownership, không claim; PASS lịch sử không tính lại |
| MCP Proxy discovery/read-only invocation | PASS | retrieve_tools + call_tool_read cho adb-mcp/gitnexus/agentmemory |
| AVD listing qua ADB MCP | PASS | `adb-mcp:list_avds`: No AVDs found; không boot hoặc tạo AVD |
| ADB MCP UI / physical Android connection | NOT RUN | Chưa chọn serial, chưa UI/logcat/install; list_avds không chứng minh có thiết bị Android |
| GitNexus có index dự án | BLOCKED | list_repos không có AndroidDvbDriver/reference; không analyze |
| AgentMemory lookup | PASS | memory_recall compact limit3, query TBS5530 AndroidDvbDriver: matched_count0; không lưu mới |
| Gradle runtime/configuration baseline | BLOCKED | JAVA_HOME sai và Gradle8.7/JDK25 không hỗ trợ; không chạy wrapper/build để tái hiện |
| Assemble/lint/unit/instrumentation/native ABI/16KiB | NOT RUN | Chưa duyệt chạy build hoặc tạo emulator; chưa có APK/binary evidence |
| Firmware file/hash/license/upload | NOT RUN | Chỉ đọc tên/quy trình từ source; không tải/đóng gói/gửi firmware |
| I2C/chip-ID/frontend lock/TS continuity Android thật | NOT RUN | Chưa triển khai driver; không vendor write, reset, LNB |

**Không có attach/detach/bind/unbind hoặc test DVB mới.** Chẩn đoán Git và truy vấn CLI không được tính là kiểm thử phần cứng. Không lưu raw USB listing chứa các thiết bị cá nhân khác vào Git.

## Historical matrix — 2026-11-01 (người dùng cung cấp; không chạy lại)


Use `PASS` only for tests with recorded observations; `NOT RUN` when not executed.

| Test | Environment | Status (2026-11-01) | Evidence |
|---|---|---|---|
| USB device visible in Windows | Windows / usbipd list | PASS | `TBS 5530 DVB BDA`, ID `734c:5530` provided in screenshot |
| WSL USB attach | WSL2 / usbipd-win | PASS | BUSID `6-2`, state Attached in screenshot |
| USB descriptors | WSL2 / lsusb and PyUSB | PASS | 480 Mbps, interface 0, IN EP 0x82 and 0x81, 512 bytes each |
| libusb claim interface 0 | WSL2 / PyUSB | PASS | claim/release prints PASS |
| Driver firmware upload | WSL2/Android | NOT RUN | No test performed |
| USB/I2C control transactions | WSL2/Android | NOT RUN | No test performed |
| AndroidDvbDriver upstream build | Windows/Gradle | NOT RUN | Not confirmed by user |
| APK deployment to Android | Android / ADB | NOT RUN | Not confirmed by user |
| DVB-T2 frontend lock | Android | NOT RUN | Not yet ported |
| MPEG-TS stream capture and continuity | Android | NOT RUN | Not yet ported |

## New test evidence template
- Date/time (timezone):
- Device and firmware state:
- Host OS / kernel / USB passthrough:
- Command/code version:
- Observed result, raw log location and SHA256:
- PASS/FAIL/NOT RUN with rationale:
