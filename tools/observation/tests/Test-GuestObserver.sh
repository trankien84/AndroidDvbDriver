#!/usr/bin/env bash
# Fake-only tests. No SSH, devices, tracefs mounts, modules, packages, or live grants.
# Run from the repository: bash tools/observation/tests/Test-GuestObserver.sh
# In a staged observer tree: bash tests/Test-GuestObserver.sh
# --portable runs parser/header/format tests without Linux process-group utilities.
# Scratch stays below this tree's tests/fixtures/GuestObserver-work-*.
set -uo pipefail
export LC_ALL=C
umask 077
observer_root=$(cd -- "${BASH_SOURCE[0]%/*}/.." && pwd -P) || exit 1
helper="$observer_root/GuestObserver.sh"
fixtures="$observer_root/tests/fixtures"
mode=${1:-all}
[[ $mode == all || $mode == --portable ]] || exit 64
if [[ $(uname -s) != Linux && ${MSYS:-} != *winsymlinks:nativestrict* ]]; then
    # MSYS otherwise copies symlinks, which would not exercise Linux topology rules.
    export MSYS="${MSYS:+$MSYS }winsymlinks:nativestrict"
    exec bash "${BASH_SOURCE[0]}" "$@"
fi
for binary in bash sed grep sha256sum stat readlink od cmp tr tail date sort cp mv find cat sleep chmod ln mkdir rm wc uname; do
    command -v "$binary" >/dev/null || { printf 'BLOCKED: missing %s\n' "$binary"; exit 77; }
done
work="$fixtures/GuestObserver-work-$$-$RANDOM"
[[ ! -e $work ]] && mkdir -- "$work" || exit 1
declare -A test_children=()
passed=0 failed=0 case_number=0
r='' phase='' permit='' out='' runner=0 pulse=0 foreign=0 last_status=0
run_id='phase31-layered-20261110-193945-623f1cf1'
# These bound the test harness, not the observer's 2 s barrier or 84 s watchdog.
# Readiness matches the coordinator's existing 10 s outer startup window.
readonly FIXTURE_READY_WAIT_MS=10000
readonly FIXTURE_TERMINAL_WAIT_MS=12000
boot_id='11111111-2222-4333-8444-555555555555'

cleanup() {
    local pid line rest
    local -a fields=()
    trap - EXIT INT TERM HUP
    for pid in "${!test_children[@]}"; do
        if IFS= read -r line 2>/dev/null < "/proc/$pid/stat"; then
            rest=${line##*) }; read -r -a fields <<< "$rest"
            [[ ${fields[19]:-} != "${test_children[$pid]}" ]] || kill -TERM "$pid" 2>/dev/null || true
        fi
    done
    for pid in "${!test_children[@]}"; do wait "$pid" 2>/dev/null || true; done
    if [[ ${GUEST_OBSERVER_TEST_KEEP:-0} == 1 && $failed != 0 ]]; then
        printf 'Synthetic failure artifacts retained: %s\n' "$work" >&2
    elif [[ $work == "$fixtures/GuestObserver-work-"* && -d $work && ! -L $work ]]; then
        find "$work" -depth -delete
    fi
}
trap cleanup EXIT
trap 'exit 130' INT TERM HUP
pass() { ((passed+=1)); printf 'PASS: %s\n' "$1"; }
reject() {
    local evidence
    ((failed+=1)); printf 'FAIL: %s\n' "$1" >&2
    for evidence in "$r/GuestRunner.stderr" "$out/health.json" "$out/terminal.json"; do
        [[ ! -f $evidence ]] || { printf '%s\n' "${evidence#"$work/"}:" >&2; head -c 8192 -- "$evidence" >&2; }
    done
}
assert() {
    local label=$1 started finished assertion_ms
    shift
    [[ -z ${GUEST_OBSERVER_TEST_MATCH:-} || $label == *"$GUEST_OBSERVER_TEST_MATCH"* ]] || return 0
    started=$(now)
    if "$@"; then pass "$label"; else reject "$label"; fi
    assertion_ms=$(( $(now) - started ))
    if declare -F release_case_children >/dev/null; then
        release_case_children
        finished=$(now)
        printf 'CASE_TIMING label=%q assertion_ms=%s total_with_cleanup_ms=%s\n' "$label" "$assertion_ms" "$((finished - started))"
    fi
}
contains() { grep -Fq -- "$2" "$1"; }
not_exists() { [[ ! -e $1 && ! -L $1 ]]; }
sha() { local s; s=$(sha256sum -- "$1"); printf '%s\n' "${s%% *}"; }
now() {
    local value=${EPOCHREALTIME:-}
    if [[ $value =~ ^([0-9]+)\.([0-9]{3}) ]]; then printf '%s%s\n' "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}"; else date +%s%3N; fi
}
write_clock() {
    local n seconds fraction
    n=$(now); seconds=$((n/1000)); fraction=$((n%1000))
    printf '%d.%03d 0.00\n' "$seconds" "$fraction" > "$r/proc/.uptime.next"
    mv -- "$r/proc/.uptime.next" "$r/proc/uptime"
    if [[ ! -f $r/GuestAction.pause-heartbeat ]]; then
        printf '%d\n' "$n" > "$phase/.heartbeat.next"; mv -- "$phase/.heartbeat.next" "$phase/observer-heartbeat"
    fi
}
write_permit() {
    local duration=${1:-15000} fixture=${2:-1} deadline
    deadline=$(( $(now) + duration ))
    printf 'protocol=TBS5530-GUEST-OBSERVER/v1\nrun_id=%s\nphase=%s\nfixture=%s\napproved=1\nboot_id=%s\nbusnum=2\ndevnum=5\nusb_path=2-1\ndescriptor_sha256=%s\ndeadline_epoch_ms=%s\nmaximum_ms=90000\nmaximum_bytes=67108864\nsnaplen=131072\nroot_grant_sha256=%064d\n' \
        "$run_id" "${phase##*/}" "$fixture" "$boot_id" "$(sha "$r/sys/bus/usb/devices/2-1/descriptors")" "$deadline" 0 > "$permit"
}
add_usb_device() {
    local controller=$1 name=$2 bus=$3 dev=$4 vendor=$5 product=$6 driver=$7 path
    path="$r/sys/devices/$controller/usb$bus/$name"
    mkdir -p -- "$path" "$r/sys/bus/pci/drivers/$driver"
    [[ -L $r/sys/devices/$controller/driver ]] || ln -s -- "$r/sys/bus/pci/drivers/$driver" "$r/sys/devices/$controller/driver"
    printf '%s\n' "$vendor" > "$path/idVendor"; printf '%s\n' "$product" > "$path/idProduct"
    printf '%s\n' "$bus" > "$path/busnum"; printf '%s\n' "$dev" > "$path/devnum"; printf '480\n' > "$path/speed"
    printf 'Guest synthetic descriptors\n' > "$path/descriptors"
    ln -s -- "$path" "$r/sys/bus/usb/devices/$name"
}
setup_case() {
    local selected_phase=${1:-operational} event fields field
    ((case_number+=1)); r="$work/GuestCase-$case_number"
    phase="$r/runs/$run_id/$selected_phase"; permit="$phase/observer.permit"; out="$phase/observer"
    mkdir -p -- "$phase" "$r/bin" "$r/sys/bus/usb/devices" "$r/sys/module/usbmon" "$r/proc/sys/kernel/random" "$r/tracefs/instances/GuestForeign" "$r/instance-template/per_cpu/cpu0"
    printf 'TBS5530-OBSERVER-SYNTHETIC/v1\n' > "$r/GuestFixture.marker"
    printf '%s\n' "$boot_id" > "$r/proc/sys/kernel/random/boot_id"
    printf '6.8.0-149-generic\n' > "$r/proc/sys/kernel/osrelease"
    printf 'usbmon 53248 0 - Live 0x0\n' > "$r/proc/modules"
    cp -- "$fixtures/GuestTcpdump.sh" "$r/bin/tcpdump"; cp -- "$fixtures/GuestTraceCmd.sh" "$r/bin/trace-cmd"
    chmod u+x -- "$r/bin/tcpdump" "$r/bin/trace-cmd"
    add_usb_device 'pci0000:00/0000:00:14.0' '2-1' 2 5 734c 5530 xhci_hcd
    add_usb_device 'pci0000:00/0000:00:14.0' 'usb2' 2 1 1d6b 0002 xhci_hcd
    printf 'do not touch global tracing\n' > "$r/tracefs/tracing_on"
    printf 'do not touch global events\n' > "$r/tracefs/set_event"
    printf 'nop\n' > "$r/tracefs/current_tracer"
    printf '1408\n' > "$r/tracefs/buffer_size_kb"
    printf '[local] mono global\n' > "$r/tracefs/trace_clock"
    printf 'foreign client\n' > "$r/tracefs/instances/GuestForeign/trace"
    printf '[local] mono global\n' > "$r/instance-template/trace_clock"
    printf '1408\n' > "$r/instance-template/buffer_size_kb"
    printf '0\n' > "$r/instance-template/tracing_on"
    printf 'entries: 0\noverrun: 0\ncommit overrun: 0\ndropped events: 0\nread events: 0\n' > "$r/instance-template/per_cpu/cpu0/stats"
    for event in xhci_urb_enqueue xhci_urb_giveback xhci_handle_transfer xhci_handle_event; do
        mkdir -p -- "$r/tracefs/events/xhci-hcd/$event" "$r/instance-template/events/xhci-hcd/$event"
        printf '0\n' > "$r/instance-template/events/xhci-hcd/$event/enable"
        printf 'none\n' > "$r/instance-template/events/xhci-hcd/$event/filter"
        if [[ $event == xhci_urb_enqueue || $event == xhci_urb_giveback ]]; then fields='epnum dir_in type'; else fields='type field0 field1 field2 field3'; fi
        {
            printf 'name: %s\n' "$event"
            for field in $fields; do printf ' field:unsigned int %s; offset:32; size:4; signed:0;\n' "$field"; done
            if [[ $event == xhci_urb_giveback ]]; then
                printf ' field:int status; offset:24; size:4; signed:1;\n'
                for field in length actual slot_id; do printf ' field:unsigned int %s; offset:28; size:4; signed:0;\n' "$field"; done
            fi
            printf 'print fmt: "length=%%u actual=%%u"\n'
        } > "$r/tracefs/events/xhci-hcd/$event/format"
    done
    write_clock; write_permit
}
# Load only the reviewed definitions into a subshell; never source or eval a permit.
unit() {
    local label=$1 code=$2
    if ( source <(sed '/^main "\$@"$/,$d' "$helper"); eval "$code" ); then pass "$label"; else reject "$label"; fi
}
parsed='parse_permit "$permit" "$(sha_file "$permit")"'
setup_case
export GUEST_OBSERVER_FIXTURE_ROOT=$r
assert 'Bash syntax: observer and fixtures' bash -n "$helper"
assert 'Bash syntax: tcpdump fixture' bash -n "$fixtures/GuestTcpdump.sh"
assert 'Bash syntax: trace-cmd fixture' bash -n "$fixtures/GuestTraceCmd.sh"
unit 'strict permit accepts exact schema' "$parsed"
unit 'fixture roots remain separate' "$parsed && configure_roots && [[ \$LAYER == SYNTHETIC_FIXTURE && \$SYS == \"\$r/sys\" && \$TRACECMD == \"\$r/bin/trace-cmd\" ]]"
unit 'target identity and exclusive xHCI topology' "$parsed && configure_roots && check_identity"
unit 'signed raw status format is mandatory' "$parsed && configure_roots && OUT=\"\$r\" && validate_formats"
unit 'mono clock is selected only inside owned instance' "$parsed && configure_roots && OUT=\"\$r\" && prepare_instance && [[ \$CLOCK == mono && \$(< \"\$INSTANCE_PATH/buffer_size_kb\") == 64 ]]"
write_clock; write_permit
unit 'fresh heartbeat and valid deadline' "$parsed && configure_roots && check_time_heartbeat"
unit 'health handshake uses JSON booleans alive and ready' "$parsed && configure_roots && OUT=\"\$r/GuestHealth\" && mkdir -- \"\$OUT\" && OUT_STAT=\$(dir_stamp \"\$OUT\") && OUTPUT_CREATED=1 && READY=1 && REASON=RECORDING && CAP_PID=11 && TRACE_PID=12 && TIMER_PID=13 && child_alive() { return 0; }; health && grep -Fq '\"alive\":true,\"ready\":true' \"\$OUT/health.json\" && grep -Fq '\"measurement_valid\":false' \"\$OUT/health.json\""
startup_barrier_metadata() (
    source <(sed '/^main "\$@"$/,$d' "$helper")
    parse_permit "$permit" "$(sha_file "$permit")" && configure_roots || return 1
    OUT="$r/GuestBarrierEvidence"; INSTANCE_PATH="$r/GuestBarrierInstance"; CLOCK=mono
    mkdir -- "$OUT" "$INSTANCE_PATH" || return 1
    OUT_STAT=$(dir_stamp "$OUT"); CAP_PID=11; TRACE_PID=12
    child_alive() { return 0; }
    cp -R -- "$r/instance-template/." "$INSTANCE_PATH/" || return 1
    printf '1\n' > "$INSTANCE_PATH/tracing_on"
    printf '67\n' > "$INSTANCE_PATH/buffer_size_kb"
    printf 'local [mono] global\n' > "$INSTANCE_PATH/trace_clock"
    for event in "${EVENTS[@]}"; do
        printf '1\n' > "$INSTANCE_PATH/events/xhci-hcd/$event/enable"
        case "$event" in
            xhci_urb_*) printf '%s\n' "$FILTER" > "$INSTANCE_PATH/events/xhci-hcd/$event/filter" ;;
        esac
    done
    printf '\324\303\262\241\002\000\004\000\000\000\000\000\000\000\000\000\000\000\002\000\334\000\000\000' > "$OUT/usbmon.pcap"
    instance_ready && [[ $BARRIER_REASON == PASS ]] || return 1
    snapshot_ready_barrier && contains "$OUT/startup-barrier.txt" 'barrier_reason=PASS' && contains "$OUT/startup-barrier.txt" 'pcap_header_valid=true' || return 1
    [[ $RING_DATA_BYTES == 4080 && $RING_NR_PAGES == 17 && $EXPECTED_RING_KIB == 67 ]] || return 1
    for invalid_size in 64 65 66 68 4096 '7 (expanded: 64)' '7 (expanded: 67)' 0067 '67 trailing'; do
        printf '%s\n' "$invalid_size" > "$INSTANCE_PATH/buffer_size_kb"
        ! instance_ready && [[ $BARRIER_REASON == INSTANCE_BUFFER_SIZE_MISMATCH ]] || return 1
    done
    snapshot_ready_barrier && contains "$OUT/startup-barrier.txt" 'barrier_reason=INSTANCE_BUFFER_SIZE_MISMATCH' || return 1
    printf '67\n' > "$INSTANCE_PATH/buffer_size_kb"
    printf '[local] mono global\n' > "$INSTANCE_PATH/trace_clock"
    ! instance_ready && [[ $BARRIER_REASON == INSTANCE_CLOCK_MISMATCH ]] || return 1
    printf 'local [mono] global\n' > "$INSTANCE_PATH/trace_clock"
    printf '0\n' > "$INSTANCE_PATH/events/xhci-hcd/xhci_urb_enqueue/enable"
    ! instance_ready && [[ $BARRIER_REASON == EVENT_DISABLED:xhci_urb_enqueue ]] || return 1
    printf '1\n' > "$INSTANCE_PATH/events/xhci-hcd/xhci_urb_enqueue/enable"
    printf 'epnum == 3\n' > "$INSTANCE_PATH/events/xhci-hcd/xhci_urb_enqueue/filter"
    ! instance_ready && [[ $BARRIER_REASON == URB_FILTER_MISMATCH:xhci_urb_enqueue ]] || return 1
    printf '%s\n' "$FILTER" > "$INSTANCE_PATH/events/xhci-hcd/xhci_urb_enqueue/filter"
    printf 'type == 2\n' > "$INSTANCE_PATH/events/xhci-hcd/xhci_handle_event/filter"
    ! instance_ready && [[ $BARRIER_REASON == RAW_FILTER_PRESENT:xhci_handle_event ]] || return 1
    snapshot_ready_barrier && not_exists "$OUT/ready.json" && contains "$OUT/startup-barrier.txt" 'measurement_valid=false'
)
assert 'startup barrier diagnostics preserve every strict gate without false readiness' startup_barrier_metadata
owned_recorder_filter_contract() (
    source <(sed '/^main "\$@"$/,$d' "$helper")
    TRACE_PID=122677; CPU_COUNT=1; TRACE_EXPECTED_EXE=/GuestFixture/trace-cmd
    OWN_START[$TRACE_PID]=900
    mock_parent=$TRACE_PID; mock_group=$TRACE_PID; mock_session=$TRACE_PID
    mock_start=950; mock_state=S; mock_exe=$TRACE_EXPECTED_EXE
    child_alive() { [[ $1 == "$TRACE_PID" ]]; }
    proc_info() {
        [[ $1 == 122784 ]] || return 1
        PROC_STATE=$mock_state; PROC_PARENT=$mock_parent; PROC_GROUP=$mock_group
        PROC_SESSION=$mock_session; PROC_START=$mock_start
    }
    trace_exe_path() {
        if [[ $1 == "$TRACE_PID" ]]; then printf '%s\n' "$TRACE_EXPECTED_EXE"; else printf '%s\n' "$mock_exe"; fi
    }
    own="(($FILTER))&&((common_pid!=122784))"
    urb_filter_matches_owned "$FILTER" && urb_filter_matches_owned "$own" || return 1
    [[ ${TRACE_FILTER_START[122784]} == 950 ]] || return 1
    for wrong in \
        "(($FILTER))||((common_pid!=122784))" \
        'epnum == 3 && dir_in == 1 && type == 2' \
        'epnum == 2 && type == 2' \
        "(($FILTER))&&((common_pid==122784))" \
        "(($FILTER))&&((common_pid!=122785))" \
        "(($FILTER))&&((common_pid!=122677))" \
        "(($FILTER))&&((common_pid!=122784))&&((common_pid!=122784))" \
        "(($FILTER))&&((common_pid<122784||common_pid>122784))" \
        "(($FILTER))&&((common_pid!=122784)))"; do
        ! urb_filter_matches_owned "$wrong" || return 1
    done
    mock_parent=1; ! urb_filter_matches_owned "$own" || return 1; mock_parent=$TRACE_PID
    mock_group=1; ! urb_filter_matches_owned "$own" || return 1; mock_group=$TRACE_PID
    mock_session=1; ! urb_filter_matches_owned "$own" || return 1; mock_session=$TRACE_PID
    mock_state=Z; ! urb_filter_matches_owned "$own" || return 1; mock_state=S
    mock_exe=/GuestFixture/unrelated; ! urb_filter_matches_owned "$own" || return 1; mock_exe=$TRACE_EXPECTED_EXE
    mock_start=951; ! urb_filter_matches_owned "$own" || return 1
)
assert 'rounded buffer and owned recorder PID exclusions reject wider or unrelated filters' owned_recorder_filter_contract
health_timing_contract() (
    source <(sed '/^main "\$@"$/,$d' "$helper")
    parse_permit "$permit" "$(sha_file "$permit")" && configure_roots || return 1
    OUT="$r/GuestHealthTiming"; mkdir -- "$OUT" || return 1
    OUT_STAT=$(dir_stamp "$OUT"); OUTPUT_CREATED=1; READY=1; REASON=RECORDING
    CAP_PID=11; TRACE_PID=12; TIMER_PID=13
    child_alive() { return 0; }
    mock_time=1303; epoch_ms() { printf '%s\n' "$mock_time"; }; uptime_ms() { printf '%s\n' "$mock_time"; }
    HEALTH_LAST_EMIT_MS=1000
    health && health_age_is_fresh 303 || return 1
    contains "$OUT/health.json" '"alive":true,"ready":true' && contains "$OUT/health.json" '"last_emit_gap_ms":303' && contains "$OUT/health.json" '"cadence_misses":1' && contains "$OUT/health.json" '"freshness_gap_misses":0' || return 1
    health_age_is_fresh 1999 && ! health_age_is_fresh 2000 && ! health_age_is_fresh 2001 && ! health_age_is_fresh -1 || return 1
    mock_time=3303
    health && contains "$OUT/health.json" '"last_emit_gap_ms":2000' && contains "$OUT/health.json" '"cadence_misses":2' && contains "$OUT/health.json" '"freshness_gap_misses":1' && contains "$OUT/health.json" '"measurement_valid":false'
)
assert 'health 303ms is fresh with cadence miss while 2000ms expires' health_timing_contract

original="$work/GuestOriginalPermit"; cp -- "$permit" "$original"
for bad in duplicate unknown crlf nul no-final-lf unapproved wrong-protocol too-long bad-hash wrong-cap wrong-snaplen bad-bus; do
    cp -- "$original" "$permit"
    case "$bad" in
        duplicate) printf 'approved=1\n' >> "$permit" ;;
        unknown) printf 'unrecognized=1\n' >> "$permit" ;;
        crlf) sed 's/$/\r/' "$original" > "$permit" ;;
        nul) printf '\0' >> "$permit" ;;
        no-final-lf) content=$(< "$original"); printf '%s' "$content" > "$permit" ;;
        unapproved) sed 's/approved=1/approved=0/' "$original" > "$permit" ;;
        wrong-protocol) sed 's@TBS5530-GUEST-OBSERVER/v1@TBS5530-GUEST-OBSERVER/v0@' "$original" > "$permit" ;;
        too-long) printf '%05000d\n' 0 >> "$permit" ;;
        bad-hash) sed 's/descriptor_sha256=./descriptor_sha256=z/' "$original" > "$permit" ;;
        wrong-cap) sed 's/maximum_bytes=67108864/maximum_bytes=67108865/' "$original" > "$permit" ;;
        wrong-snaplen) sed 's/snaplen=131072/snaplen=65535/' "$original" > "$permit" ;;
        bad-bus) sed 's/busnum=2/busnum=02/' "$original" > "$permit" ;;
    esac
    unit "strict permit rejects $bad" "! $parsed"
done
cp -- "$original" "$permit"
unit 'wrong permit digest is rejected' '! parse_permit "$permit" "$(printf "%064d" 0)"'
unit 'permit replacement after parse is rejected' "$parsed && configure_roots && cp -- \"\$permit\" \"\$permit.replacement\" && mv -- \"\$permit.replacement\" \"\$permit\"; ! check_permit && [[ \$ERROR == PERMIT_CHANGED ]]"
cp -- "$original" "$permit"
unit 'fixture permit cannot authorize live' "$parsed && P[fixture]=0; ! configure_roots"
unit 'expired permit fails before observer creation' "$parsed && configure_roots && P[deadline_epoch_ms]=1000000000000; ! check_time_heartbeat && [[ \$ERROR == DEADLINE ]]"
write_clock; write_permit
unit 'missing heartbeat is rejected' "$parsed && configure_roots && mv -- \"\$phase/observer-heartbeat\" \"\$phase/GuestSavedHeartbeat\"; ! check_time_heartbeat && [[ \$ERROR == HEARTBEAT_MISSING ]]"
mv -- "$phase/GuestSavedHeartbeat" "$phase/observer-heartbeat"
unit 'pcap header validates linktype and exact snaplen' "$parsed && configure_roots && OUT=\"\$r/GuestPcap\" && mkdir -- \"\$OUT\" && printf '\\324\\303\\262\\241\\002\\000\\004\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\002\\000\\334\\000\\000\\000' > \"\$OUT/usbmon.pcap\" && pcap_header_valid"
unit 'pcap header rejects non-usbmon file' "OUT=\"\$r/GuestBadPcap\"; mkdir -- \"\$OUT\"; printf 'invalid header' > \"\$OUT/usbmon.pcap\"; ! pcap_header_valid"
# An OHCI keyboard is permitted, but another xHCI device is not.
add_usb_device 'pci0000:00/0000:00:12.0' '1-1' 1 2 046d c31c ohci-pci
unit 'OHCI keyboard does not authorize capturing its payload' "$parsed && configure_roots && check_identity"
add_usb_device 'pci0000:00/0000:00:14.0' '3-2' 3 2 1234 5678 xhci_hcd
unit 'other xHCI bus is rejected despite target-only usbmon bus' "$parsed && configure_roots; ! check_identity && [[ \$ERROR == SHARED_XHCI_TRAFFIC ]]"
sed '/field:int status;/d' "$r/tracefs/events/xhci-hcd/xhci_urb_giveback/format" > "$r/GuestNoStatus"
mv -- "$r/GuestNoStatus" "$r/tracefs/events/xhci-hcd/xhci_urb_giveback/format"
unit 'print format cannot substitute for signed raw status' "$parsed && configure_roots && OUT=\"\$r\"; ! validate_formats && [[ \$ERROR == SIGNED_STATUS_FORMAT_REQUIRED ]]"
fixture_raw_contract() {
    mkdir -- "$out" || return 1
    printf 'Guest synthetic binary input\n' > "$out/trace.dat"
    GUEST_FIXTURE_SCENARIO=normal bash "$r/bin/trace-cmd" report -R -N -t -i "$out/trace.dat" > "$r/GuestRawReport" || return 1
    contains "$r/GuestRawReport" 'status=-75' || return 1
    if bash "$r/bin/trace-cmd" report -N -t -i "$out/trace.dat" > "$r/GuestRejectedReport" 2>&1; then return 1; fi
    if bash "$r/bin/trace-cmd" report -R -t -i "$out/trace.dat" > "$r/GuestRejectedReport" 2>&1; then return 1; fi
    if bash "$r/bin/trace-cmd" report -R -N -i "$out/trace.dat" > "$r/GuestRejectedReport" 2>&1; then return 1; fi
}
assert 'synthetic raw exporter requires -R -N -t and preserves signed status' fixture_raw_contract

if [[ $mode == --portable ]]; then
    printf 'Portable: %d PASS, %d FAIL; Linux integration NOT RUN.\n' "$passed" "$failed"
    ((failed == 0)); exit $?
fi
missing=''
for binary in setsid prlimit timeout truncate; do command -v "$binary" >/dev/null || missing+=" $binary"; done
if [[ -n $missing || $(uname -s) != Linux ]]; then
    printf 'BLOCKED: Linux integration requires genuine Linux setsid/prlimit and /proc process groups; missing:%s\n' "$missing"
    printf 'Portable: %d PASS, %d FAIL. Run on Linux: bash tests/Test-GuestObserver.sh\n' "$passed" "$failed"
    ((failed == 0)) || exit 1
    exit 77
fi

track_child() {
    local pid=$1 line rest
    local -a fields=()
    IFS= read -r line < "/proc/$pid/stat" || return 1
    rest=${line##*) }; read -r -a fields <<< "$rest"
    test_children[$pid]=${fields[19]}
}
release_case_children() {
    local pid line rest end
    local -a fields=()
    if ((runner > 0)) && [[ -d $phase ]]; then : > "$phase/STOP_OBSERVER"; fi
    # Failed assertions must not leave recorders and heartbeat writers running
    # while later cases replace their synthetic clocks or compete for the CPU.
    end=$(( $(now) + 4000 ))
    while ((runner > 0 && $(now) < end)); do
        if ! IFS= read -r line 2>/dev/null < "/proc/$runner/stat"; then break; fi
        rest=${line##*) }; [[ ${rest%% *} != Z ]] || break
        sleep 0.05
    done
    for pid in "${!test_children[@]}"; do
        if IFS= read -r line 2>/dev/null < "/proc/$pid/stat"; then
            rest=${line##*) }; read -r -a fields <<< "$rest"
            [[ ${fields[19]:-} != "${test_children[$pid]}" ]] || kill -TERM "$pid" 2>/dev/null || true
        fi
    done
    for pid in "${!test_children[@]}"; do wait "$pid" 2>/dev/null || true; unset 'test_children[$pid]'; done
    if ((failed > 0)) && [[ -f $out/terminal.json ]]; then printf 'Post-cleanup terminal: ' >&2; cat -- "$out/terminal.json" >&2; fi
    runner=0; pulse=0; foreign=0
}
start_pulse() {
    ( while :; do write_clock; sleep 0.05; done ) &
    pulse=$!; track_child "$pulse"
}
stop_pulse() { kill -TERM "$pulse" 2>/dev/null || true; wait "$pulse" 2>/dev/null || true; unset 'test_children[$pulse]'; pulse=0; }
launch() {
    local scenario=${1:-normal}
    export GUEST_OBSERVER_FIXTURE_ROOT=$r
    start_pulse
    GUEST_FIXTURE_SCENARIO=$scenario bash "$helper" "$permit" "$(sha "$permit")" > "$r/GuestRunner.stdout" 2> "$r/GuestRunner.stderr" &
    runner=$!; track_child "$runner"
    printf 'FIXTURE_PROCESS case=%s helper_pid=%s heartbeat_pid=%s\n' "${r##*/}" "$runner" "$pulse"
}
wait_file() {
    local file=$1 end=$(( $(now) + ${2:-$FIXTURE_READY_WAIT_MS} ))
    while [[ ! -f $file ]]; do
        if (( $(now) >= end )); then
            printf 'FIXTURE_WAIT_TIMEOUT case=%s file=%s budget_ms=%s helper_pid=%s\n' "${r##*/}" "${file##*/}" "${2:-$FIXTURE_READY_WAIT_MS}" "$runner" >&2
            return 1
        fi
        kill -0 "$runner" 2>/dev/null || return 1
        sleep 0.05
    done
}
await_terminal() {
    wait_file "$out/terminal.json" "$FIXTURE_TERMINAL_WAIT_MS" || { cat "$r/GuestRunner.stderr" >&2; return 1; }
    wait "$runner"; last_status=$?; unset 'test_children[$runner]'
    stop_pulse
    return 0
}
clean_result() {
    local pid_file pid line rest
    contains "$out/terminal.json" '"cleanup_confirmed":true' && contains "$out/health.json" '"alive":false' && contains "$out/health.json" '"ready":false' || return 1
    [[ $(< "$r/tracefs/tracing_on") == 'do not touch global tracing' && $(< "$r/tracefs/set_event") == 'do not touch global events' && $(< "$r/tracefs/instances/GuestForeign/trace") == 'foreign client' ]] || return 1
    for pid_file in "$r"/GuestTcpdump.pid "$r"/GuestTraceCmd.pid "$r"/GuestTcpdump.descendant "$r"/GuestTraceCmd.recorder; do
        [[ -f $pid_file ]] || continue
        pid=$(< "$pid_file")
        if [[ -r /proc/$pid/stat ]]; then
            IFS= read -r line < "/proc/$pid/stat"; rest=${line##*) }
            [[ ${rest%% *} == Z ]] || return 1
        fi
    done
    [[ $(find "$r/tracefs/instances" -mindepth 1 -maxdepth 1 -type d | wc -l) == 1 ]]
}
case_stop() {
    setup_case; launch descendants
    wait_file "$out/ready.json" || return 1
    contains "$out/ready.json" '"evidence_layer":"SYNTHETIC_FIXTURE"' && contains "$out/ready.json" '"measurement_valid":false' && contains "$out/ready.json" '"pcap_header_valid":true' && contains "$out/ready.json" '"per_cpu_buffer_effective_kib":67' && contains "$out/startup-barrier.txt" "owned_recorder_pid=$(< "$r/GuestTraceCmd.recorder") " || return 1
    local stamp sample age sampled_at health_sample max_age=0 cadence_misses=0
    contains "$out/health.json" '"alive":true' && contains "$out/health.json" '"ready":true' && contains "$out/ready.json" '"ready":true' || return 1
    for ((sample=0; sample<12; sample++)); do
        health_sample=$(< "$out/health.json"); sampled_at=$(now)
        [[ $health_sample =~ \"emitted_epoch_ms\":([0-9]+) ]] || return 1
        stamp=${BASH_REMATCH[1]}
        # Measure at sampling, not after scheduling a separate sed/date process.
        age=$((sampled_at - stamp))
        ((age <= max_age)) || max_age=$age
        ((age <= 200)) || ((cadence_misses+=1))
        ((age < 2000)) || { printf 'Health freshness expired at >=2000 ms: %s ms\n' "$age" >&2; return 1; }
        sleep 0.05
    done
    printf 'HEALTH_SAMPLE_METRICS samples=12 max_age_ms=%d cadence_target_ms=200 cadence_misses=%d freshness_limit_ms=2000 freshness_failures=0\n' "$max_age" "$cadence_misses"
    contains "$out/health.json" '"cadence_target_ms":200' && contains "$out/health.json" '"freshness_limit_ms":2000' || return 1
    sleep 3600 & foreign=$!; track_child "$foreign"
    : > "$phase/STOP_OBSERVER"
    await_terminal && [[ $last_status == 0 ]] && clean_result && kill -0 "$foreign" && contains "$out/terminal.json" '"reason":"STOP_REQUESTED"' && contains "$out/terminal.json" '"raw_export_complete":true' && contains "$out/terminal.json" '"stats_complete":true' && contains "$out/trace-raw.txt" 'status=-75'
}
assert 'ready, fresh health, STOP, raw export, owned descendants, foreign client isolation' case_stop

case_deadline() {
    setup_case; write_permit 4000; launch
    await_terminal && clean_result && contains "$out/terminal.json" '"reason":"DEADLINE"' && contains "$out/terminal.json" '"capture_bytes":24' && contains "$out/terminal.json" '"measurement_valid":false'
}
assert 'no packets still stops on independent deadline' case_deadline

case_mutation() {
    setup_case; launch; wait_file "$out/ready.json" || return 1
    printf 'unknown=1\n' >> "$permit"
    await_terminal && clean_result && contains "$out/terminal.json" '"reason":"PERMIT_CHANGED"'
}
assert 'permit mutation immediately invalidates recording' case_mutation
case_heartbeat() {
    setup_case; launch; wait_file "$out/ready.json" || return 1
    : > "$r/GuestAction.pause-heartbeat"
    await_terminal && clean_result && contains "$out/terminal.json" '"reason":"HEARTBEAT_STALE"'
}
assert 'lost parent heartbeat stops both passive observers' case_heartbeat
case_child() {
    local role=$1
    setup_case; launch; wait_file "$out/ready.json" || return 1
    : > "$r/GuestAction.exit-$role"
    await_terminal && clean_result && contains "$out/terminal.json" '"reason":"CHILD_EXIT"'
}
assert 'unexpected tcpdump exit' case_child tcpdump
assert 'unexpected trace-cmd exit' case_child trace
case_cap() {
    local path marker=${1:-cap}
    setup_case; launch; wait_file "$out/ready.json" || return 1
    : > "$r/GuestAction.$marker"
    await_terminal && clean_result && contains "$out/terminal.json" '"reason":"FILE_CAP"' && contains "$out/terminal.json" '"truncated":true' || return 1
    while IFS= read -r path; do (( $(stat -c %s "$path") <= 67108864 )) || return 1; done < <(find "$out" -type f)
}
assert '64 MiB usbmon ceiling is inherited, detected and marked invalid' case_cap cap
assert '64 MiB trace ceiling is inherited, detected and marked invalid' case_cap trace-cap
case_topology() {
    setup_case; launch; wait_file "$out/ready.json" || return 1
    add_usb_device 'pci0000:00/0000:00:14.0' '3-3' 3 3 1234 5678 xhci_hcd
    await_terminal && clean_result && contains "$out/terminal.json" '"reason":"SHARED_XHCI_TRAFFIC"'
}
assert 'new xHCI traffic invalidates target-only observation' case_topology
case_descriptor() {
    setup_case; launch; wait_file "$out/ready.json" || return 1
    printf 'Guest changed descriptors\n' > "$r/sys/bus/usb/devices/2-1/descriptors"
    await_terminal && clean_result && contains "$out/terminal.json" '"reason":"DESCRIPTORS_CHANGED"'
}
assert 'identity descriptor mutation' case_descriptor
case_global_change() {
    setup_case; launch; wait_file "$out/ready.json" || return 1
    printf 'other client changed tracing\n' > "$r/tracefs/current_tracer"
    await_terminal && clean_result && contains "$out/terminal.json" '"reason":"GLOBAL_TRACE_STATE_CHANGED"' && [[ $(< "$r/tracefs/current_tracer") == 'other client changed tracing' ]]
}
assert 'global trace state changes fail closed without resetting another client' case_global_change
case_gone() {
    local selected_phase=$1 expected=$2
    setup_case "$selected_phase"; launch; wait_file "$out/ready.json" || return 1
    rm -- "$r/sys/bus/usb/devices/2-1"
    await_terminal && clean_result && contains "$out/terminal.json" "\"reason\":\"$expected\"" && contains "$out/terminal.json" '"firmware_success":false' && contains "$out/terminal.json" '"capture_validity":"UNKNOWN"'
}
assert 'cold disappearance is not firmware success' case_gone cold TARGET_GONE
assert 'operational disappearance is a fault' case_gone operational TARGET_GONE_OPERATIONAL
case_partial() {
    setup_case; launch partial-init
    await_terminal && clean_result && not_exists "$out/ready.json" && contains "$out/terminal.json" '"ready":false'
}
assert 'partial initialization cleans first observer and own instance' case_partial
case_bad_header() {
    local scenario=$1
    setup_case; launch "$scenario"
    await_terminal && clean_result && not_exists "$out/ready.json" && contains "$out/terminal.json" '"reason":"OBSERVER_NOT_READY"'
}
assert 'missing pcap header never becomes ready' case_bad_header no-header
assert 'invalid pcap header never becomes ready' case_bad_header bad-header
case_report() {
    local scenario=$1 expected=$2
    setup_case; launch "$scenario"; wait_file "$out/ready.json" || return 1
    : > "$phase/STOP_OBSERVER"
    await_terminal && clean_result && contains "$out/terminal.json" "$expected" && contains "$out/terminal.json" '"measurement_valid":false'
}
assert 'raw export failure is not certified evidence' case_report raw-fail '"raw_export_complete":false'
assert 'raw lost events remain visible' case_report raw-lost '"lost_events":true'
case_admission() {
    local scenario=$1 output
    setup_case; export GUEST_OBSERVER_FIXTURE_ROOT=$r
    case "$scenario" in
        missing) rm -- "$permit" ;;
        expired) write_permit -100 ;;
        stale) mkdir -- "$out"; printf 'preserve previous evidence\n' > "$out/GuestExisting" ;;
        symlink) mv -- "$permit" "$phase/GuestRealPermit"; ln -s -- "$phase/GuestRealPermit" "$permit" ;;
    esac
    output=$(GUEST_FIXTURE_SCENARIO=normal bash "$helper" "$permit" "$(if [[ -f $permit ]]; then sha "$permit"; else printf '%064d' 0; fi)" 2>&1)
    [[ $? != 0 && ! -e $r/GuestTcpdump.pid && ! -e $r/GuestTraceCmd.pid ]] || return 1
    if [[ $scenario == stale ]]; then [[ $(< "$out/GuestExisting") == 'preserve previous evidence' && ! -e $out/terminal.json ]]; else not_exists "$out"; fi
}
assert 'missing permit creates no evidence directory' case_admission missing
assert 'expired permit starts no observers' case_admission expired
assert 'stale output is never reused or overwritten' case_admission stale
assert 'symlink permit is rejected' case_admission symlink
printf 'TOTAL: %d PASS, %d FAIL; SYNTHETIC_FIXTURE only; live NOT RUN.\n' "$passed" "$failed"
((failed == 0))
