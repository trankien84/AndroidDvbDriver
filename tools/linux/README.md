# TBS5530 — Biến thể chẩn đoán DVB-T2, chỉ build/offline

**Bản hiện tại: operational-only, chờ parent kiểm proof/hash trước mọi load. Không tạo snapshot hoặc nạp firmware.** Công cụ tạo source riêng, không sửa reference/cài module/chuyển USB. Parent quản lý consent, ledger và handoff riêng; build thành công không chứng minh an toàn điện/LNB hoặc nhận TS. Các opt-in params không tự tạo quyền runtime.

## Nguồn và giấy phép

Base: [koreapyj/cxd28xx](https://github.com/koreapyj/cxd28xx), commit `c5fa27eecbe80633cbb365cc44656daea1c0e2e3`. Patch chỉ thay Makefile, tbs5530.c và tbs5530.h; không chép toàn bộ third-party source vào repo. Giữ copyright Davin/TurboSight, Yoonji Park và LICENSE GPLv2 khi tạo scratch. Guard và tests mới dùng SPDX GPL-2.0-only. `terrestrial-diagnostic-base.json` khóa SHA commit, base/output hashes, patch và guard hash.

## Chuẩn bị an toàn

```powershell
.\tools\linux\Prepare-TBS5530-TerrestrialDiagnostic.ps1 -OutputPath 'D:\TSDuck\phase31-terrestrial-review' -WhatIf
.\tools\linux\Prepare-TBS5530-TerrestrialDiagnostic.ps1 -OutputPath 'D:\TSDuck\phase31-terrestrial-review'
```

Output phải mới, dưới D:\TSDuck, không reparse parent. SHA/hash sai hoặc thư mục tồn tại thì dừng. `-WhatIf` chỉ kiểm nguồn/plan; không tạo thư mục, SSH, build hoặc thay hệ thống. Lượt thật copy selected source vào scratch, `git apply --check`, apply patch rồi đối chiếu exact output hashes và chạy source-structure tests. Không fetch, sync, sửa refs, chạy DKMS/install hoặc tự truy cập guest.

Sau quyền build-only riêng, người dùng/parent có thể chuyển scratch sang `/root/phase31-*` hoặc `/home/<user>/phase31-*` trong guest rồi chạy `bash Build-TerrestrialDiagnosticOffline.sh`. Script chỉ compile fake-policy test và bounded Kbuild, kiểm hash headers trước/sau, in metadata/imports. Nó không SSH, load/install module hoặc attach USB. CRC cần kiểm riêng với exact kernel/local Module.symvers; không coi `nm -u` có imports là lỗi nếu modpost resolve đúng.

## Guard và đường gọi thực

1. USB id table chỉ `734c:5530`; probe kiểm lại VID/PID trước cấp state.
2. `diagnostic_enable=false`, `operational_only=false`, `allow_firmware_bootstrap=false` mặc định. Chỉ nhận enabled + operational-only + bootstrap=false, consent còn hiệu lực, heartbeat mới và exact expected bus/dev/profile hash. Profile được parent quan sát: High-Speed/config1/interface0/alt0/ff0000, device18+config32 SHA `22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5`, EP0=64, Bulk IN82/81=512, intervals1/0; không suy RAM firmware hash từ descriptor.
3. Probe operational **không gọi request_firmware hoặc firmware loader/A0**. Hàm bootstrap cũ giữ để truy vết offline nhưng không được probe gọi; writer stub trả EPERM, compiler loại khỏi `.ko`. Bootstrap runtime bị từ chối trong bản này.
4. Tests bootstrap 8192-byte/hash/132-A0-plan vẫn giữ làm regression fake-only; không có firmware payload trong repo và không áp dụng plan đó cho operational run. Không tự chọn initial/operational hoặc re-enumerate.
5. Sau exact operational admission và atomic one-probe consumption: adapter I2C → `dvb_attach(cxd2878_attach)` → đúng **một frontend terrestrial**. Board không còn function/field/call M88/satellite/request_module/I2C client satellite/B7/8A0801, kể cả cleanup/error labels. Không dummy satellite frontend.
6. Board chỉ quảng bá `SYS_DVBT2`. Wrapper nhỏ cho `set_frontend` và `tune` kiểm **594000000 Hz / 8000000 Hz / stream_id 0**, rồi gọi original Sony function pointer. Chỉ chấp nhận PLP0; **AUTO bị từ chối** vì code DVB-T2 hiện ghi stream_id & 0xFF qua nhánh manual, chưa chứng minh AUTO đúng. Không phát minh PLP từ Windows. Voltage/tone/DiSEqC callbacks của exposed frontend bị NULL. Sony `cxd2878.c/.h/_priv.h`, ALP/compat bytes giữ nguyên và kiểm hashes; không gộp thay 0x54→0x55 hoặc sửa register init/tune.
7. Streaming vẫn 8 × 65536/EP82, vendor8A `0A 00`, nhưng completion/resubmit được bọc policy hữu hạn: session≤120s, một receive≤10s, requested≤32MiB kể cả in-flight. Negative/unknown/cap/STOP/heartbeat loss latch fault, không resubmit; empty-success mặc định0-credit vẫn dừng như run đầu. Param `empty_success_credit` chỉ0 hoặc1, cho tối đa một empty-success resubmit toàn phiên, không deliver zero; empty thứ hai terminal, credit không reset bởi data; workqueue process context kill/drain, không kill từ completion. ATSC3 ALP net adapter không được board tạo; alp vẫn là dependency của Sony.

## Bằng chứng offline

`Test-TBS5530-TerrestrialDiagnostic.ps1` kiểm cấu trúc function/body, gate ordering, satellite node/cleanup absence, only frontend registration, wrappers trước Sony và preservation hashes: **25 checks PASS**. Đây không chỉ là grep một tên đã xóa, nhưng cũng không phải chứng minh formal toàn call graph/electrical state.

`guard-test.c` dùng đúng header policy/132-plan với fake writer: **281 cases PASS**, gồm default-off/wrong VID/PID/size/hash, NULL image/writer không I/O, exact addresses/order, short return 0 và negative `-EIO` riêng tại mỗi 132 boundary phải dừng ngay, wrong delivery/frequency/bandwidth/PLP bị reject. Digest input trong harness là seam: không có firmware thật, không claim test này tự kiểm SHA256 implementation. Kernel firmware path dùng SHA256 thật; build/link resolve symbol sha256.

Operational fake-policy **1522 PASS**, exact extracted central-control/I2C path với Sony-like helper bỏ qua lỗi **21 PASS**: first failed control/result và suppressed count được kiểm, không có write sau fault. Thêm **54 checks** trên exact extracted completion/resubmit: strict zero-stop, credit1 với82→empty→188-byte fake success, empty→empty-stop, negative trước/sau empty, cap/deadline/heartbeat/STOP và invalidcredit2; không assert synthetic82/188 là TS thật. Source-path operational **30 PASS**, cùng25 preservation checks trên PS7/PS5.1. Build guest6.8.0-149-generic PASS/MODPOST PASS; ba module alp/cxd2878/tbs5530_ter_diag, **122 CRC records khớp**. Board `depends=dvb-core`, CXD lookup qua dvb_attach và CXD cần alp. Module chưa ký/chưa load. Log/ko nằm ngoài Git trong bundle runtime, không binaries trong tools.

## Chặn runtime còn lại

**BUILD_PROOF_PASS / PARENT_REVIEW_REQUIRED_BEFORE_LOAD.** Operational identity chỉ khóa cached descriptor/profile và attachment tuple do parent vừa đọc, không chứng minh RAM firmware hoặc nguồn LNB OFF. Không assertion trạng thái điện: đã loại satellite software nodes nhưng chưa đo electrical state. Full Sony init/tune bytes giữ nguyên; source pin/hash là plan, không chứng minh mọi register có ý nghĩa điện đã giải mã. Central allowlist chỉ vendor80/90/91 và8A0A00, Sony I2C addresses64/66/44/10/60; first short/negative/unknown latch vĩnh viễn cả control và receive dù Sony bỏ return.

Telemetry giữ `first_rx_seen/first_rx_status/first_rx_length` từ completion đầu kể cả cancellations sau; `empty_successes`, `empty_credits_used`, `empty_resubmits` phân biệt quan sát rỗng, credit đã tiêu thụ và resubmit thực thành công. `/sys/module/tbs5530_ter_diag/parameters/telemetry` đọc key=value; `stop=1` một chiều, `consent=0` revoke không rearm sau probe; `heartbeat` sequence tăng mỗi≤1s, stale≥3s dừng. `session_ms`, `receive_ms`, `max_bytes` chỉ giảm dưới caps. Parent phải giữ durable ledger chống reload cùng attachment vì module-local consumption không tồn tại sau unload. Stop kiểm giữa I/O; một synchronous transfer đang chạy chỉ được chờ kết thúc/timeout, không force-free. Drain dùng usb_kill_urb và control mutex barrier; kernel/HCD bị treo có thể làm chờ drain, host không được force unload/free để ép kết thúc. Proof fake/source chưa phải concurrency stress kernel thật.

Không tự load/bind; parent phải kiểm hash, fresh bus/dev/profile, consent/ledger/receiver/trace và cleanup gate trước một lượt runtime riêng. Không nạp firmware, re-enumerate, đổi owner, snapshot, kernel/core hoặc binding Windows trong module tools.
