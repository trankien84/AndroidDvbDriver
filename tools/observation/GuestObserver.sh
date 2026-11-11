#!/usr/bin/env bash
# Passive guest-side observation only. The coordinator owns consent and tuner STOP.
# CLI: bash GuestObserver.sh <permit-path> <permit-sha256>
# Fixture-only environment: GUEST_OBSERVER_FIXTURE_ROOT=<private synthetic tree>.
# A terminal result never certifies firmware transfers, TS reception, or measurement validity.
# Exit: 0 = handled STOP / cold disappearance, 1 = invalid observation,
# 3 = cleanup not confirmed, 64 = invalid permit, 65 = admission, 69 = missing tools.
# ready.json / health.json / terminal.json are atomic; an invalid admission cannot
# overwrite a stale output directory. The parent must reject a missing terminal.
set -u
set -o pipefail
set +m
export LC_ALL=C
export PATH=/usr/sbin:/usr/bin:/sbin:/bin
umask 077

readonly PROTOCOL='TBS5530-GUEST-OBSERVER/v1'
readonly FILTER='epnum == 2 && dir_in == 1 && type == 2'
readonly MAX_BYTES=67108864
readonly MAX_MS=90000
readonly POLL_SECONDS=0.05
readonly REPORT_MS=2500
readonly HEALTH_CADENCE_TARGET_MS=200
readonly HEALTH_FRESHNESS_MS=2000
readonly -a EVENTS=(xhci_urb_enqueue xhci_urb_giveback xhci_handle_transfer xhci_handle_event)
declare -A P=() OWN_START=() OWN_DRAINED=() TRACE_FILTER_START=()
declare -a OWN_PIDS=()
PERMIT='' PERMIT_SHA='' PERMIT_STAT='' DIR_STAT='' OUT_STAT=''
DIR='' OUT='' SYS='' PROC='' TRACEFS='' FIXTURE_ROOT='' INSTANCE='' INSTANCE_PATH=''
TCPDUMP='' TRACECMD='' CAP_PID=0 TRACE_PID=0 TIMER_PID=0 REPORT_PID=0
INSTANCE_CREATED=0 OUTPUT_CREATED=0 FINISHING=0 READY=0 TRUNCATED=false
REASON='INITIALIZING' LAYER='GUEST_USBMON_XHCI' CLOCK='UNKNOWN'
RAW_EXPORT=false REPORT_EXIT=-1 STATS_VALID=false LOST_EVENTS='null'
START_MS=0 START_UPTIME=0 STOP_UPTIME=0 LAST_UPTIME=0 HARD_END_UPTIME=0
CAP_BYTES=0 TRACE_BYTES=0 TOPOLOGY='' INITIAL_TOPOLOGY='' CPU_COUNT=0 GLOBAL_STATE=''
CLEANUP=true ERROR='' HEADER_VALID=0 BARRIER_REASON='NOT_CHECKED'
PAGE_BYTES=0 RING_PAGE_HEADER_BYTES=16 RING_DATA_BYTES=0 RING_NR_PAGES=0
EXPECTED_RING_KIB=0 TRACE_EXPECTED_EXE=''
HEALTH_LAST_EMIT_MS=0 HEALTH_LAST_GAP_MS=0 HEALTH_MAX_GAP_MS=0
HEALTH_CADENCE_MISSES=0 HEALTH_FRESHNESS_GAP_MISSES=0

fail() { ERROR="$1"; return 1; }
epoch_ms() {
    local value=${EPOCHREALTIME:-}
    if [[ $value =~ ^([0-9]+)\.([0-9]{3}) ]]; then
        printf '%s%s\n' "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}"
    else
        date +%s%3N
    fi
}
uptime_ms() {
    local value seconds fraction
    IFS=' ' read -r value _ < "$PROC/uptime" || return 1
    [[ $value =~ ^([0-9]{1,12})\.([0-9]{1,9})$ ]] || return 1
    seconds=${BASH_REMATCH[1]}; fraction=${BASH_REMATCH[2]}000
    printf '%s\n' "$((10#$seconds * 1000 + 10#${fraction:0:3}))"
}
file_size() { local n; n=$(stat -c %s -- "$1" 2>/dev/null) || n=0; printf '%s\n' "$n"; }
file_stamp() { stat -c '%d:%i:%s:%y:%z' -- "$1" 2>/dev/null; }
dir_stamp() { stat -c '%d:%i' -- "$1" 2>/dev/null; }
output_intact() { canonical "$OUT" && [[ $(dir_stamp "$OUT") == "$OUT_STAT" ]]; }
sha_file() { local value; value=$(sha256sum -- "$1") || return 1; printf '%s\n' "${value%% *}"; }
regular() { [[ -f $1 && ! -L $1 ]]; }
canonical() { [[ $(readlink -e -- "$1" 2>/dev/null) == "$1" && ! -L $1 ]]; }
private_owned() {
    local uid mode
    uid=$(stat -c %u -- "$1") || return 1
    mode=$(stat -c %a -- "$1") || return 1
    [[ $uid == "$EUID" && $mode =~ ^[0-7]{3,4}$ ]] && (( (8#$mode & 0022) == 0 ))
}

parse_permit() {
    local line key value count=0 size last clean
    [[ $# == 2 && $2 =~ ^[[:xdigit:]]{64}$ ]] || return 1
    PERMIT=$1; PERMIT_SHA=${2,,}
    [[ $PERMIT == /* ]] && regular "$PERMIT" && canonical "$PERMIT" || return 1
    size=$(file_size "$PERMIT"); ((size > 0 && size <= 4096)) || return 1
    [[ $(sha_file "$PERMIT") == "$PERMIT_SHA" ]] || return 1
    # Shell read drops NUL bytes, so reject them (and CRLF) before parsing.
    tr -d '\000\r' < "$PERMIT" | cmp -s - "$PERMIT" || return 1
    last=$(tail -c 1 -- "$PERMIT" | od -An -tu1); [[ ${last//[[:space:]]/} == 10 ]] || return 1
    while IFS= read -r line; do
        [[ $line =~ ^([a-z_][a-z0-9_]*)=([A-Za-z0-9_./:-]+)$ ]] || return 1
        key=${BASH_REMATCH[1]}; value=${BASH_REMATCH[2]}
        case "$key" in
            protocol|run_id|phase|fixture|approved|boot_id|busnum|devnum|usb_path|descriptor_sha256|deadline_epoch_ms|maximum_ms|maximum_bytes|snaplen|root_grant_sha256) ;;
            *) return 1 ;;
        esac
        [[ ! ${P[$key]+exists} ]] || return 1
        P[$key]=$value; ((count+=1))
    done < "$PERMIT"
    (( count == 15 )) || return 1
    [[ ${P[protocol]} == "$PROTOCOL" && ${P[approved]} == 1 ]] || return 1
    [[ ${P[run_id]} =~ ^phase31-layered-[0-9]{8}-[0-9]{6}-[0-9a-f]{8}$ ]] || return 1
    [[ ${P[phase]} == cold || ${P[phase]} == operational ]] || return 1
    [[ ${P[fixture]} == 0 || ${P[fixture]} == 1 ]] || return 1
    [[ ${P[boot_id]} =~ ^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$ ]] || return 1
    [[ ${P[busnum]} =~ ^[1-9][0-9]{0,2}$ && ${P[devnum]} =~ ^[1-9][0-9]{0,2}$ ]] || return 1
    (( P[busnum] <= 255 && P[devnum] <= 127 )) || return 1
    [[ ${P[usb_path]} =~ ^[1-9][0-9]{0,2}-[1-9][0-9]{0,2}(\.[1-9][0-9]{0,2})*$ ]] || return 1
    [[ ${P[usb_path]%%-*} == "${P[busnum]}" ]] || return 1
    [[ ${P[descriptor_sha256]} =~ ^[[:xdigit:]]{64}$ && ${P[root_grant_sha256]} =~ ^[[:xdigit:]]{64}$ ]] || return 1
    [[ ${P[deadline_epoch_ms]} =~ ^[1-9][0-9]{12}$ ]] || return 1
    [[ ${P[maximum_ms]} == 90000 && ${P[maximum_bytes]} == 67108864 && ${P[snaplen]} == 131072 ]] || return 1
    DIR=${PERMIT%/*}
    [[ ${PERMIT##*/} =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || return 1
    private_owned "$PERMIT" && private_owned "$DIR" || return 1
    PERMIT_STAT=$(file_stamp "$PERMIT"); DIR_STAT=$(dir_stamp "$DIR")
    [[ $(sha_file "$PERMIT") == "$PERMIT_SHA" ]] || return 1
}

configure_roots() {
    local mounts_type mounts_point boot
    if [[ ${P[fixture]} == 1 ]]; then
        LAYER='SYNTHETIC_FIXTURE'
        FIXTURE_ROOT=${GUEST_OBSERVER_FIXTURE_ROOT:-}
        [[ -n $FIXTURE_ROOT && $FIXTURE_ROOT == /* && $FIXTURE_ROOT != / && $FIXTURE_ROOT != */ ]] || fail FIXTURE_ROOT_INVALID || return 1
        canonical "$FIXTURE_ROOT" && private_owned "$FIXTURE_ROOT" || { fail FIXTURE_ROOT_INVALID; return 1; }
        case "$FIXTURE_ROOT/" in /sys/*|/proc/*|/dev/*|/run/*|/tmp/*|/var/tmp/*) fail FIXTURE_ROOT_UNSAFE; return 1 ;; esac
        regular "$FIXTURE_ROOT/GuestFixture.marker" && [[ $(< "$FIXTURE_ROOT/GuestFixture.marker") == 'TBS5530-OBSERVER-SYNTHETIC/v1' ]] || { fail FIXTURE_MARKER_INVALID; return 1; }
        SYS="$FIXTURE_ROOT/sys"; PROC="$FIXTURE_ROOT/proc"; TRACEFS="$FIXTURE_ROOT/tracefs"
        [[ $DIR == "$FIXTURE_ROOT/runs/${P[run_id]}/${P[phase]}" ]] || { fail FIXTURE_PERMIT_PATH; return 1; }
        for boot in "$SYS" "$PROC" "$TRACEFS" "$FIXTURE_ROOT/bin"; do
            canonical "$boot" && private_owned "$boot" || { fail FIXTURE_ROOT_UNSAFE; return 1; }
        done
        # Internal sysfs-style links are allowed, but none may escape the synthetic tree.
        while IFS= read -r -d '' boot; do
            boot=$(readlink -e -- "$boot") || { fail FIXTURE_SYMLINK_ESCAPE; return 1; }
            [[ $boot == "$FIXTURE_ROOT/"* ]] || { fail FIXTURE_SYMLINK_ESCAPE; return 1; }
        done < <(find "$FIXTURE_ROOT" -type l -print0)
        TCPDUMP="$FIXTURE_ROOT/bin/tcpdump"; TRACECMD="$FIXTURE_ROOT/bin/trace-cmd"
        regular "$TCPDUMP" && regular "$TRACECMD" && [[ -x $TCPDUMP && -x $TRACECMD ]] || { fail FIXTURE_BINARIES_MISSING; return 1; }
    else
        [[ $EUID == 0 ]] || { fail ROOT_REQUIRED; return 1; }
        [[ ! ${GUEST_OBSERVER_FIXTURE_ROOT+x} && ! ${GUEST_FIXTURE_SCENARIO+x} ]] || { fail FIXTURE_LIVE_MIX; return 1; }
        [[ $DIR == "/root/${P[run_id]}/${P[phase]}" ]] || { fail LIVE_PERMIT_PATH; return 1; }
        SYS=/sys; PROC=/proc; TRACEFS=''
        while read -r _ mounts_point mounts_type _; do
            if [[ $mounts_type == tracefs ]]; then
                [[ -z $TRACEFS ]] || { fail TRACEFS_AMBIGUOUS; return 1; }
                TRACEFS=$mounts_point
            fi
        done < /proc/mounts
        [[ $TRACEFS == /sys/kernel/tracing || $TRACEFS == /sys/kernel/debug/tracing ]] || { fail TRACEFS_NOT_MOUNTED; return 1; }
        [[ $(stat -f -c %T -- "$TRACEFS") == tracefs ]] || { fail TRACEFS_NOT_MOUNTED; return 1; }
        TCPDUMP=$(command -v tcpdump) && TRACECMD=$(command -v trace-cmd) || { fail OBSERVER_TOOLS_MISSING; return 1; }
    fi
    boot=$(< "$PROC/sys/kernel/random/boot_id") || return 1
    [[ ${boot,,} == "${P[boot_id],,}" ]] || { fail BOOT_CHANGED; return 1; }
    [[ $(< "$PROC/sys/kernel/osrelease") == 6.8.0-149-generic ]] || { fail KERNEL_MISMATCH; return 1; }
    [[ -d $SYS/module/usbmon ]] && grep -q '^usbmon ' "$PROC/modules" || { fail USBMON_NOT_LOADED; return 1; }
    [[ -d $TRACEFS/instances && ! -L $TRACEFS/instances ]] || { fail TRACE_ISOLATION_UNAVAILABLE; return 1; }
    configure_ring_contract
}

configure_ring_contract() {
    local bits
    if [[ ${P[fixture]} == 1 ]]; then
        PAGE_BYTES=4096; bits=64
        TRACE_EXPECTED_EXE=$(readlink -e -- "/proc/$$/exe") || TRACE_EXPECTED_EXE=''
    else
        PAGE_BYTES=$(getconf PAGESIZE) && bits=$(getconf LONG_BIT) || { fail TRACE_PAGE_GEOMETRY_UNAVAILABLE; return 1; }
        TRACE_EXPECTED_EXE=$(readlink -e -- "$TRACECMD") || { fail TRACE_EXECUTABLE_UNAVAILABLE; return 1; }
    fi
    # Linux v6.8 buffer_data_page = u64 timestamp + native local_t commit.
    # Keep this source-verified 4 KiB / 64-bit geometry explicit, not a range.
    [[ $PAGE_BYTES == 4096 && $bits == 64 ]] || { fail TRACE_PAGE_GEOMETRY_UNSUPPORTED; return 1; }
    RING_DATA_BYTES=$((PAGE_BYTES - RING_PAGE_HEADER_BYTES))
    RING_NR_PAGES=$(((64 * 1024 + RING_DATA_BYTES - 1) / RING_DATA_BYTES))
    EXPECTED_RING_KIB=$((RING_NR_PAGES * RING_DATA_BYTES / 1024))
}

global_fingerprint() {
    local file
    local -a paths=()
    for file in tracing_on current_tracer buffer_size_kb trace_clock set_event; do
        regular "$TRACEFS/$file" || return 1
        paths+=("$TRACEFS/$file")
    done
    # One bounded list of metadata files avoids serial fork/exec delays that can
    # otherwise starve the health heartbeat on a single-vCPU guest.
    sha256sum -- "${paths[@]}"
}

check_permit() {
    local boot
    canonical "$PERMIT" && regular "$PERMIT" && [[ $(file_stamp "$PERMIT") == "$PERMIT_STAT" && $(sha_file "$PERMIT") == "$PERMIT_SHA" ]] || { fail PERMIT_CHANGED; return 1; }
    if ((OUTPUT_CREATED)); then health || { fail HEALTH_WRITE_FAILED; return 1; }; fi
    canonical "$DIR" && [[ $(dir_stamp "$DIR") == "$DIR_STAT" ]] || { fail PERMIT_DIRECTORY_CHANGED; return 1; }
    boot=$(< "$PROC/sys/kernel/random/boot_id")
    [[ ${boot,,} == "${P[boot_id],,}" ]] || { fail BOOT_CHANGED; return 1; }
    [[ $(< "$PROC/sys/kernel/osrelease") == 6.8.0-149-generic ]] || { fail KERNEL_MISMATCH; return 1; }
    if [[ -n $GLOBAL_STATE ]]; then
        [[ $(global_fingerprint) == "$GLOBAL_STATE" ]] || { fail GLOBAL_TRACE_STATE_CHANGED; return 1; }
    fi
    if (( OUTPUT_CREATED )); then
        canonical "$OUT" && [[ $(dir_stamp "$OUT") == "$OUT_STAT" ]] || { fail OUTPUT_DIRECTORY_CHANGED; return 1; }
    fi
}

check_time_heartbeat() {
    local now up beat size
    now=$(epoch_ms); up=$(uptime_ms) || { fail CLOCK_INVALID; return 1; }
    [[ $now =~ ^[0-9]{13}$ ]] || { fail CLOCK_INVALID; return 1; }
    (( now >= P[deadline_epoch_ms] )) && { fail DEADLINE; return 1; }
    (( START_MS == 0 || now >= START_MS )) || { fail CLOCK_REGRESSED; return 1; }
    (( LAST_UPTIME == 0 || up >= LAST_UPTIME )) || { fail CLOCK_REGRESSED; return 1; }
    LAST_UPTIME=$up
    (( HARD_END_UPTIME == 0 || up < HARD_END_UPTIME )) || { fail MAXIMUM_TIME; return 1; }
    [[ ! -e $DIR/STOP_OBSERVER && ! -L $DIR/STOP_OBSERVER ]] || { fail STOP_REQUESTED; return 1; }
    regular "$DIR/observer-heartbeat" || { fail HEARTBEAT_MISSING; return 1; }
    size=$(file_size "$DIR/observer-heartbeat"); ((size > 0 && size <= 32)) || { fail HEARTBEAT_INVALID; return 1; }
    beat=$(< "$DIR/observer-heartbeat")
    [[ $beat =~ ^(0|[1-9][0-9]{0,14})$ ]] || { fail HEARTBEAT_INVALID; return 1; }
    # Read the clock after the heartbeat: the parent can atomically replace it
    # while file checks run, and a fresh heartbeat must not look future-dated.
    up=$(uptime_ms) || { fail CLOCK_INVALID; return 1; }
    (( up >= LAST_UPTIME )) || { fail CLOCK_REGRESSED; return 1; }
    LAST_UPTIME=$up
    (( beat <= up && up - beat <= 2000 )) || { fail HEARTBEAT_STALE; return 1; }
}

controller_of() {
    local path=$1 driver
    while [[ $path == "$SYS/devices/"* ]]; do
        if [[ -L $path/driver ]]; then
            driver=$(readlink -e -- "$path/driver") || return 1
            case "${driver##*/}" in
                xhci_hcd|xhci-hcd|ohci_hcd|ohci-pci|ehci_hcd|ehci-pci|uhci_hcd) printf '%s\n' "$driver"; return 0 ;;
            esac
        fi
        path=${path%/*}
    done
    return 1
}

check_identity() {
    local entry path name vendor product bus dev speed controller descriptor records='' count=0 target='' target_path='' shared=0
    for entry in "$SYS"/bus/usb/devices/*; do
        [[ -f $entry/idVendor && -f $entry/idProduct ]] || continue
        path=$(readlink -e -- "$entry") || { fail TOPOLOGY_CHANGED; return 1; }
        [[ $path == "$SYS/devices/"* ]] || { fail TOPOLOGY_UNKNOWN; return 1; }
        name=${entry##*/}
        vendor=$(< "$entry/idVendor"); product=$(< "$entry/idProduct")
        bus=$(< "$entry/busnum"); dev=$(< "$entry/devnum"); speed=$(< "$entry/speed")
        [[ $vendor =~ ^[[:xdigit:]]{4}$ && $product =~ ^[[:xdigit:]]{4}$ && $bus =~ ^[0-9]{1,3}$ && $dev =~ ^[0-9]{1,3}$ && $speed =~ ^[0-9]+(\.[0-9]+)?$ ]] || { fail TOPOLOGY_UNKNOWN; return 1; }
        controller=$(controller_of "$path") || { fail TOPOLOGY_UNKNOWN; return 1; }
        records+="$name|$path|$vendor|$product|$bus|$dev|$speed|$controller"$'\n'
        [[ $name =~ ^usb[0-9]+$ ]] && continue
        if [[ ${vendor,,}:${product,,} == 734c:5530 ]]; then
            ((count+=1)); target=$entry; target_path=$path
            [[ ${controller##*/} == xhci_hcd || ${controller##*/} == xhci-hcd ]] || { fail TARGET_NOT_XHCI; return 1; }
        elif [[ ${controller##*/} == xhci_hcd || ${controller##*/} == xhci-hcd || $bus == "${P[busnum]}" ]]; then
            shared=1
        fi
    done
    if (( count == 0 )); then
        if [[ ${P[phase]} == cold ]]; then fail TARGET_GONE; else fail TARGET_GONE_OPERATIONAL; fi
        return 1
    fi
    ((count == 1)) || { fail IDENTITY_AMBIGUOUS; return 1; }
    ((shared == 0)) || { fail SHARED_XHCI_TRAFFIC; return 1; }
    [[ ${target##*/} == "${P[usb_path]}" && $(< "$target/busnum") == "${P[busnum]}" && $(< "$target/devnum") == "${P[devnum]}" && $(< "$target/speed") == 480 ]] || { fail IDENTITY_CHANGED; return 1; }
    descriptor=$(sha_file "$target/descriptors") || { fail DESCRIPTORS_UNREADABLE; return 1; }
    [[ $descriptor == "${P[descriptor_sha256],,}" ]] || { fail DESCRIPTORS_CHANGED; return 1; }
    TOPOLOGY=$(printf '%s' "$records" | sort | sha256sum); TOPOLOGY=${TOPOLOGY%% *}
    [[ -z $INITIAL_TOPOLOGY || $TOPOLOGY == "$INITIAL_TOPOLOGY" ]] || { fail TOPOLOGY_CHANGED; return 1; }
}

# /proc here is only for ownership of this script's children, never a device root.
proc_info() {
    local line rest
    IFS= read -r line 2>/dev/null < "/proc/$1/stat" || return 1
    rest=${line##*) }; read -r -a PROC_FIELDS <<< "$rest"
    ((${#PROC_FIELDS[@]} >= 20)) || return 1
    # Fields after comm begin at state(3): pgrp(5), session(6), starttime(22).
    PROC_STATE=${PROC_FIELDS[0]}; PROC_PARENT=${PROC_FIELDS[1]}; PROC_GROUP=${PROC_FIELDS[2]}; PROC_SESSION=${PROC_FIELDS[3]}; PROC_START=${PROC_FIELDS[19]}
}
spawn_owned() {
    local name=$1 pid tries
    shift
    # Hold the owned session leader until the parent records its starttime.
    # A short report must not disappear before ownership can be established.
    setsid prlimit --fsize="$MAX_BYTES:$MAX_BYTES" -- bash -c '
        gate=$1; shift
        for ((attempt=0; attempt<200; attempt++)); do
            if [[ -f $gate && ! -L $gate ]]; then rm -- "$gate"; exec "$@"; fi
            sleep 0.01
        done
        exit 125
    ' observer-owned-exec "$OUT/.$name.start" "$@" > "$OUT/$name.stdout.log" 2> "$OUT/$name.stderr.log" &
    pid=$!; OWN_PIDS+=("$pid"); OWN_START[$pid]=''
    SPAWN_PID=$pid
    for ((tries=0; tries<20; tries++)); do
        if proc_info "$pid"; then
            OWN_START[$pid]=$PROC_START
            if [[ $PROC_GROUP == "$pid" && $PROC_SESSION == "$pid" && $PROC_STATE != Z ]]; then
                : > "$OUT/.$name.start"
                return 0
            fi
        fi
        kill -0 "$pid" 2>/dev/null || break
        sleep 0.01
    done
    fail CHILD_START_FAILED
}
child_alive() {
    local pid=$1
    ((pid > 0)) && proc_info "$pid" && [[ $PROC_STATE != Z && $PROC_START == "${OWN_START[$pid]:-}" && $PROC_GROUP == "$pid" && $PROC_SESSION == "$pid" ]]
}
owned_members() {
    local pid=$1 item found=1 leader_alive=0
    [[ ! ${OWN_DRAINED[$pid]+done} ]] || return 1
    # A reused group leader is not ours, even if its numeric pid is identical.
    if proc_info "$pid"; then
        [[ $PROC_START == "${OWN_START[$pid]:-}" ]] || return 2
        if [[ $PROC_STATE != Z ]]; then
            leader_alive=1
            if [[ $PROC_GROUP == "$pid" && $PROC_SESSION == "$pid" ]]; then return 0; fi
        fi
    fi
    for item in /proc/[0-9]*/stat; do
        item=${item%/stat}; item=${item##*/}
        if proc_info "$item" && [[ $PROC_STATE != Z && $PROC_GROUP == "$pid" && $PROC_SESSION == "$pid" ]]; then found=0; break; fi
    done
    # Once the leader and its entire group are gone, this session cannot gain
    # another member. Avoid rescanning /proc for every later cleanup poll.
    if ((found == 1 && leader_alive == 0)); then OWN_DRAINED[$pid]=1; fi
    return "$found"
}
signal_owned() {
    local pid=$1 signal=$2
    if owned_members "$pid"; then kill -"$signal" -- "-$pid" 2>/dev/null || true; fi
    # Handle initialization failure before setsid, but only the exact owned child.
    if proc_info "$pid" && [[ -n ${OWN_START[$pid]:-} && $PROC_START == "${OWN_START[$pid]}" && $PROC_STATE != Z && $PROC_SESSION != "$pid" ]]; then
        kill -"$signal" "$pid" 2>/dev/null || true
    fi
}

update_sizes() {
    local path n
    CAP_BYTES=$(file_size "$OUT/usbmon.pcap"); TRACE_BYTES=0
    for path in "$OUT"/trace.dat*; do
        [[ -f $path ]] || continue
        n=$(file_size "$path"); ((TRACE_BYTES+=n))
    done
}
check_caps() {
    local path n batch=0
    update_sizes
    for path in "$OUT"/*; do
        [[ -f $path ]] || continue
        n=$(file_size "$path")
        if ((n >= MAX_BYTES)); then TRUNCATED=true; fail FILE_CAP; return 1; fi
        ((batch+=1))
        if ((batch % 4 == 0 && OUTPUT_CREATED)); then health || { fail HEALTH_WRITE_FAILED; return 1; }; fi
    done
    if ((CAP_BYTES >= MAX_BYTES - 131072 || TRACE_BYTES >= MAX_BYTES - 131072)); then TRUNCATED=true; fail FILE_CAP; return 1; fi
}
health_age_is_fresh() {
    [[ $1 =~ ^(0|[1-9][0-9]{0,14})$ ]] && (( $1 < HEALTH_FRESHNESS_MS ))
}
health() {
    local alive=false ready=false cap_alive=false trace_alive=false up=0 emitted=0
    ((OUTPUT_CREATED)) || return 0
    output_intact || return 1
    child_alive "$CAP_PID" && cap_alive=true
    child_alive "$TRACE_PID" && trace_alive=true
    [[ $cap_alive == true && $trace_alive == true ]] && child_alive "$TIMER_PID" && alive=true
    [[ $alive == true && $READY == 1 && $REASON == RECORDING ]] && ready=true
    up=$(uptime_ms) || up=0
    emitted=$(epoch_ms)
    HEALTH_LAST_GAP_MS=0
    if ((HEALTH_LAST_EMIT_MS > 0 && emitted >= HEALTH_LAST_EMIT_MS)); then
        HEALTH_LAST_GAP_MS=$((emitted - HEALTH_LAST_EMIT_MS))
        ((HEALTH_LAST_GAP_MS <= HEALTH_CADENCE_TARGET_MS)) || ((HEALTH_CADENCE_MISSES+=1))
        health_age_is_fresh "$HEALTH_LAST_GAP_MS" || ((HEALTH_FRESHNESS_GAP_MISSES+=1))
        ((HEALTH_LAST_GAP_MS <= HEALTH_MAX_GAP_MS)) || HEALTH_MAX_GAP_MS=$HEALTH_LAST_GAP_MS
    fi
    HEALTH_LAST_EMIT_MS=$emitted
    printf '{"protocol":"%s","run_id":"%s","phase":"%s","evidence_layer":"%s","measurement_valid":false,"pid":%d,"tcpdump_pid":%d,"trace_pid":%d,"alive":%s,"ready":%s,"tcpdump_alive":%s,"trace_alive":%s,"capture_bytes":%d,"trace_bytes":%d,"uptime_ms":%d,"emitted_epoch_ms":%d,"cadence_target_ms":%d,"freshness_limit_ms":%d,"last_emit_gap_ms":%d,"max_emit_gap_ms":%d,"cadence_misses":%d,"freshness_gap_misses":%d,"reason":"%s"}\n' \
        "$PROTOCOL" "${P[run_id]}" "${P[phase]}" "$LAYER" "$$" "$CAP_PID" "$TRACE_PID" "$alive" "$ready" "$cap_alive" "$trace_alive" "$CAP_BYTES" "$TRACE_BYTES" "$up" "$emitted" "$HEALTH_CADENCE_TARGET_MS" "$HEALTH_FRESHNESS_MS" "$HEALTH_LAST_GAP_MS" "$HEALTH_MAX_GAP_MS" "$HEALTH_CADENCE_MISSES" "$HEALTH_FRESHNESS_GAP_MISSES" "$REASON" > "$OUT/.health.json.next" && mv -f -- "$OUT/.health.json.next" "$OUT/health.json"
}
pcap_header_valid() {
    local -a bytes=()
    local raw endian snap link
    regular "$OUT/usbmon.pcap" || return 1
    raw=$(od -An -v -tu1 -N24 -- "$OUT/usbmon.pcap") || return 1
    read -r -a bytes <<< "${raw//$'\n'/ }"
    ((${#bytes[@]} == 24)) || return 1
    case "${bytes[0]} ${bytes[1]} ${bytes[2]} ${bytes[3]}" in
        '212 195 178 161'|'77 60 178 161') endian=le ;;
        '161 178 195 212'|'161 178 60 77') endian=be ;;
        *) return 1 ;;
    esac
    if [[ $endian == le ]]; then
        [[ ${bytes[4]} == 2 && ${bytes[5]} == 0 && ${bytes[6]} == 4 && ${bytes[7]} == 0 ]] || return 1
        snap=$((bytes[16] + (bytes[17]<<8) + (bytes[18]<<16) + (bytes[19]<<24)))
        link=$((bytes[20] + (bytes[21]<<8) + (bytes[22]<<16) + (bytes[23]<<24)))
    else
        [[ ${bytes[4]} == 0 && ${bytes[5]} == 2 && ${bytes[6]} == 0 && ${bytes[7]} == 4 ]] || return 1
        snap=$(((bytes[16]<<24) + (bytes[17]<<16) + (bytes[18]<<8) + bytes[19]))
        link=$(((bytes[20]<<24) + (bytes[21]<<16) + (bytes[22]<<8) + bytes[23]))
    fi
    ((snap == 131072 && (link == 220 || link == 189)))
}

validate_formats() {
    local event field format
    for event in "${EVENTS[@]}"; do
        format="$TRACEFS/events/xhci-hcd/$event/format"
        regular "$format" && (( $(file_size "$format") <= 65536 )) || { fail TRACE_FORMAT_MISSING; return 1; }
        if [[ $event == xhci_urb_enqueue || $event == xhci_urb_giveback ]]; then
            for field in epnum dir_in type; do
                grep -Eq "field:[^;]*[[:space:]]$field;" "$format" || { fail TRACE_FORMAT_INCOMPATIBLE; return 1; }
            done
        else
            for field in type field0 field1 field2 field3; do
                grep -Eq "field:[^;]*[[:space:]]$field;" "$format" || { fail TRACE_FORMAT_INCOMPATIBLE; return 1; }
            done
        fi
        if [[ $event == xhci_urb_giveback ]]; then
            grep -Eq 'field:int status;[[:space:]]*offset:24;[[:space:]]*size:4;[[:space:]]*signed:1;' "$format" || { fail SIGNED_STATUS_FORMAT_REQUIRED; return 1; }
            for field in length actual slot_id; do
                grep -Eq "field:[^;]*[[:space:]]$field;" "$format" || { fail TRACE_FORMAT_INCOMPATIBLE; return 1; }
            done
        fi
        cp -- "$format" "$OUT/$event.format" || return 1
    done
}
prepare_instance() {
    local item cpu clock_text
    INSTANCE="tbs5530-${P[run_id]#phase31-layered-}-${P[phase]}-$$"
    INSTANCE_PATH="$TRACEFS/instances/$INSTANCE"
    [[ ! -e $INSTANCE_PATH && ! -L $INSTANCE_PATH ]] && mkdir -- "$INSTANCE_PATH" || { fail TRACE_INSTANCE_COLLISION; return 1; }
    INSTANCE_CREATED=1
    if [[ ${P[fixture]} == 1 ]]; then
        # Only synthetic ordinary files are populated here; live tracefs creates its own files.
        cp -R -- "$FIXTURE_ROOT/instance-template/." "$INSTANCE_PATH/" || return 1
    fi
    for item in trace_clock buffer_size_kb tracing_on; do
        regular "$INSTANCE_PATH/$item" && [[ -w $INSTANCE_PATH/$item ]] || { fail TRACE_ISOLATION_UNAVAILABLE; return 1; }
    done
    clock_text=$(< "$INSTANCE_PATH/trace_clock")
    CLOCK='KERNEL_DEFAULT'
    for item in $clock_text; do
        item=${item#\[}; item=${item%\]}
        if [[ $item == mono ]]; then
            printf 'mono\n' > "$INSTANCE_PATH/trace_clock" || return 1
            CLOCK=mono
            break
        fi
    done
    printf '64\n' > "$INSTANCE_PATH/buffer_size_kb" || return 1
    CPU_COUNT=0
    for cpu in "$INSTANCE_PATH"/per_cpu/cpu[0-9]*; do [[ -d $cpu ]] && ((CPU_COUNT+=1)); done
    # trace-cmd -m bounds each CPU's alternating spool to 2 x 256 KiB.
    ((CPU_COUNT > 0 && CPU_COUNT <= 64)) || { fail TRACE_CPU_BOUND_UNAVAILABLE; return 1; }
}
trace_exe_path() { readlink -e -- "/proc/$1/exe"; }
trace_filter_pid_owned() {
    local pid=$1 start
    [[ $pid =~ ^[1-9][0-9]{0,9}$ && -n $TRACE_EXPECTED_EXE ]] || return 1
    child_alive "$TRACE_PID" || return 1
    proc_info "$pid" || return 1
    [[ $PROC_STATE != Z && $PROC_PARENT == "$TRACE_PID" && $PROC_GROUP == "$TRACE_PID" && $PROC_SESSION == "$TRACE_PID" ]] || return 1
    start=$PROC_START
    ((start >= OWN_START[$TRACE_PID])) || return 1
    [[ ! ${TRACE_FILTER_START[$pid]+seen} || ${TRACE_FILTER_START[$pid]} == "$start" ]] || return 1
    [[ $(trace_exe_path "$TRACE_PID") == "$TRACE_EXPECTED_EXE" && $(trace_exe_path "$pid") == "$TRACE_EXPECTED_EXE" ]] || return 1
    proc_info "$pid" || return 1
    [[ $PROC_STATE != Z && $PROC_START == "$start" && $PROC_PARENT == "$TRACE_PID" && $PROC_GROUP == "$TRACE_PID" && $PROC_SESSION == "$TRACE_PID" ]] || return 1
    TRACE_FILTER_START[$pid]=$start
}
urb_filter_matches_owned() {
    local compact=${1//[[:space:]]/} base='epnum==2&&dir_in==1&&type==2'
    local char depth=0 i atom rest pid count=0
    local -A seen=()
    # Conjunction-only grammar: grouping cannot widen the three required atoms.
    # OR, ranges, other fields, changed operands and unbalanced groups fail closed.
    for ((i=0; i<${#compact}; i++)); do
        char=${compact:i:1}
        case "$char" in '(') ((depth+=1)) ;; ')') ((depth-=1)); ((depth >= 0)) || return 1 ;; esac
    done
    ((depth == 0)) || return 1
    compact=${compact//[()]/}
    [[ $compact != "$base" ]] || return 0
    [[ $compact == "$base&&"* ]] || return 1
    rest=${compact#"$base&&"}
    while [[ -n $rest ]]; do
        atom=${rest%%'&&'*}
        [[ $atom =~ ^common_pid!=([1-9][0-9]{0,9})$ ]] || return 1
        pid=${BASH_REMATCH[1]}
        [[ ! ${seen[$pid]+duplicate} ]] || return 1
        seen[$pid]=1; ((count+=1)); ((count <= CPU_COUNT)) || return 1
        trace_filter_pid_owned "$pid" || return 1
        [[ $rest == *'&&'* ]] || return 0
        rest=${rest#*'&&'}
        [[ -n $rest ]] || return 1
    done
    return 1
}

instance_ready() {
    local event effective selected_clock
    [[ -d $INSTANCE_PATH && ! -L $INSTANCE_PATH ]] || { BARRIER_REASON=INSTANCE_UNAVAILABLE; return 1; }
    [[ $(< "$INSTANCE_PATH/tracing_on") == 1 ]] || { BARRIER_REASON=INSTANCE_TRACING_DISABLED; return 1; }
    [[ $EXPECTED_RING_KIB == 67 && $(< "$INSTANCE_PATH/buffer_size_kb") == "$EXPECTED_RING_KIB" ]] || { BARRIER_REASON=INSTANCE_BUFFER_SIZE_MISMATCH; return 1; }
    selected_clock=$(< "$INSTANCE_PATH/trace_clock")
    if [[ $CLOCK == mono ]]; then
        [[ $selected_clock == mono || " $selected_clock " == *' [mono] '* ]] || { BARRIER_REASON=INSTANCE_CLOCK_MISMATCH; return 1; }
    fi
    for event in "${EVENTS[@]}"; do
        [[ $(< "$INSTANCE_PATH/events/xhci-hcd/$event/enable") == 1 ]] || { BARRIER_REASON="EVENT_DISABLED:$event"; return 1; }
        effective=$(< "$INSTANCE_PATH/events/xhci-hcd/$event/filter")
        if [[ $event == xhci_urb_enqueue || $event == xhci_urb_giveback ]]; then
            urb_filter_matches_owned "$effective" || { BARRIER_REASON="URB_FILTER_MISMATCH:$event"; return 1; }
        else
            [[ $effective == none || $effective == 0 ]] || { BARRIER_REASON="RAW_FILTER_PRESENT:$event"; return 1; }
        fi
    done
    BARRIER_REASON=PASS
}

snapshot_ready_barrier() {
    local file event value
    output_intact || return 1
    # Capture only owned startup metadata before record cleanup changes it.
    # Final pcap/trace files cannot establish what was ready before STOP.
    {
        printf 'protocol=%s\nevidence_layer=%s\nmeasurement_valid=false\n' "$PROTOCOL" "$LAYER"
        printf 'sample_epoch_ms=%s\nbarrier_reason=%s\n' "$(epoch_ms)" "$BARRIER_REASON"
        printf 'requested_buffer_kib=64\nexpected_buffer_kib=%s\nring_page_bytes=%s\nring_page_header_bytes=%s\n' "$EXPECTED_RING_KIB" "$PAGE_BYTES" "$RING_PAGE_HEADER_BYTES"
        printf 'trace_pid=%s\n' "$TRACE_PID"
        for value in "${!TRACE_FILTER_START[@]}"; do printf 'owned_recorder_pid=%s starttime=%s\n' "$value" "${TRACE_FILTER_START[$value]}"; done
        printf 'pcap_bytes=%s\n' "$(file_size "$OUT/usbmon.pcap")"
        if pcap_header_valid; then printf 'pcap_header_valid=true\n'; else printf 'pcap_header_valid=false\n'; fi
        if child_alive "$CAP_PID"; then printf 'tcpdump_alive=true\n'; else printf 'tcpdump_alive=false\n'; fi
        if child_alive "$TRACE_PID"; then printf 'trace_alive=true\n'; else printf 'trace_alive=false\n'; fi
        for file in tracing_on buffer_size_kb trace_clock; do
            value=UNAVAILABLE
            if regular "$INSTANCE_PATH/$file"; then IFS= read -r -n 1024 value < "$INSTANCE_PATH/$file" || true; fi
            printf 'instance_%s=%q\n' "$file" "$value"
        done
        for event in "${EVENTS[@]}"; do
            for file in enable filter; do
                value=UNAVAILABLE
                if regular "$INSTANCE_PATH/events/xhci-hcd/$event/$file"; then IFS= read -r -n 1024 value < "$INSTANCE_PATH/events/xhci-hcd/$event/$file" || true; fi
                printf '%s_%s=%q\n' "$event" "$file" "$value"
            done
        done
    } > "$OUT/.startup-barrier.txt.next" && mv -f -- "$OUT/.startup-barrier.txt.next" "$OUT/startup-barrier.txt"
}

record_stats() {
    local cpu key value seen total=0 files=0 ok=1
    [[ -d $INSTANCE_PATH ]] || return 0
    : > "$OUT/trace-stats.txt"
    for cpu in "$INSTANCE_PATH"/per_cpu/cpu[0-9]*/stats; do
        [[ -f $cpu ]] || continue
        ((files+=1)); printf '%s\n' "${cpu#"$INSTANCE_PATH/"}" >> "$OUT/trace-stats.txt"
        cat -- "$cpu" >> "$OUT/trace-stats.txt" || ok=0
        seen=0
        while IFS=: read -r key value; do
            value=${value//[[:space:]]/}
            case "$key" in
                overrun|'commit overrun'|'dropped events')
                    [[ $value =~ ^[0-9]+$ ]] || { ok=0; continue; }
                    ((total+=value)); ((seen+=1)) ;;
            esac
        done < "$cpu"
        ((seen == 3)) || ok=0
    done
    if ((files == CPU_COUNT && files > 0 && ok)); then
        STATS_VALID=true
        if ((total > 0)); then LOST_EVENTS=true; else LOST_EVENTS=false; fi
    fi
}
stop_children() {
    local pid signal count any status
    for signal in INT TERM KILL; do
        for pid in "${OWN_PIDS[@]}"; do signal_owned "$pid" "$signal"; done
        case "$signal" in INT) count=12 ;; TERM) count=6 ;; KILL) count=4 ;; esac
        while ((count-- > 0)); do
            any=0
            for pid in "${OWN_PIDS[@]}"; do
                if owned_members "$pid"; then any=1; fi
            done
            ((any)) || break
            update_sizes; health || true; sleep "$POLL_SECONDS"
        done
    done
    for pid in "${OWN_PIDS[@]}"; do
        if owned_members "$pid"; then CLEANUP=false; else
            status=$?; ((status == 2)) && CLEANUP=false
        fi
        if proc_info "$pid" && [[ $PROC_STATE != Z && $PROC_START == "${OWN_START[$pid]:-}" ]]; then CLEANUP=false; fi
        if ! child_alive "$pid"; then wait "$pid" 2>/dev/null || true; fi
    done
}
remove_instance() {
    ((INSTANCE_CREATED)) || return 0
    [[ -e $INSTANCE_PATH || -L $INSTANCE_PATH ]] || return 0
    [[ -d $INSTANCE_PATH && ! -L $INSTANCE_PATH && $(readlink -e -- "$INSTANCE_PATH") == "$INSTANCE_PATH" ]] || { CLEANUP=false; return; }
    if [[ $CLEANUP == true ]]; then
        printf '0\n' > "$INSTANCE_PATH/tracing_on" || CLEANUP=false
        if [[ ${P[fixture]} == 1 ]]; then
            find "$INSTANCE_PATH" -depth -delete || CLEANUP=false
        else
            rmdir -- "$INSTANCE_PATH" || CLEANUP=false
        fi
        [[ ! -e $INSTANCE_PATH ]] || CLEANUP=false
    fi
}
export_raw() {
    local pid until_ms now status=0
    regular "$OUT/trace.dat" && (( $(file_size "$OUT/trace.dat") > 0 )) || return 0
    # Preserve raw fields, not the print format that omits giveback.status.
    spawn_owned report "$TRACECMD" report -R -N -t -i "$OUT/trace.dat" || { RAW_EXPORT=false; return; }
    REPORT_PID=$SPAWN_PID; pid=$REPORT_PID
    until_ms=$(( $(epoch_ms) + REPORT_MS ))
    while child_alive "$pid"; do
        now=$(epoch_ms)
        ((now < until_ms)) || { signal_owned "$pid" KILL; status=124; break; }
        update_sizes; health || true; sleep "$POLL_SECONDS"
    done
    if ((status == 0)); then wait "$pid" || status=$?; else wait "$pid" 2>/dev/null || true; fi
    REPORT_EXIT=$status
    mv -- "$OUT/report.stdout.log" "$OUT/trace-raw.txt" || return 0
    if ((status == 0 && $(file_size "$OUT/trace-raw.txt") > 0 && $(file_size "$OUT/trace-raw.txt") < MAX_BYTES)); then RAW_EXPORT=true; fi
    if grep -Eq '(LOST [1-9][0-9]* EVENTS|MISSED EVENTS)' "$OUT/trace-raw.txt"; then LOST_EVENTS=true; fi
}
finish() {
    local incoming=${1:-1} instance_removed=true
    ((FINISHING == 0 && OUTPUT_CREATED == 1)) || return
    FINISHING=1
    trap '' INT TERM HUP
    trap - EXIT
    if [[ $REASON == INITIALIZING || $REASON == RECORDING ]]; then REASON=${ERROR:-INTERNAL_ERROR}; fi
    if regular "$OUT/watchdog.reason"; then REASON=$(< "$OUT/watchdog.reason"); fi
    stop_children
    if ! output_intact; then
        # Do not overwrite a replacement directory, even to publish a failure result.
        remove_instance
        printf 'GuestObserver: OUTPUT_DIRECTORY_CHANGED; terminal unavailable; observers stopped\n' >&2
        exit 3
    fi
    record_stats
    export_raw
    # Also account for report descendants before removing the owned instance.
    stop_children
    remove_instance
    ((INSTANCE_CREATED == 0)) || [[ ! -e $INSTANCE_PATH ]] || instance_removed=false
    check_caps || true
    STOP_UPTIME=$(uptime_ms) || STOP_UPTIME=0
    health || CLEANUP=false
    printf '{"protocol":"%s","run_id":"%s","phase":"%s","evidence_layer":"%s","measurement_valid":false,"observation_invalid":true,"capture_validity":"UNKNOWN","requires_parent_certification":true,"firmware_success":false,"reason":"%s","pid":%d,"tcpdump_pid":%d,"trace_pid":%d,"ready":%s,"capture_bytes":%d,"trace_bytes":%d,"truncated":%s,"raw_export_complete":%s,"report_exit_code":%d,"stats_complete":%s,"lost_events":%s,"cleanup_confirmed":%s,"instance_removed":%s,"started_epoch_ms":%d,"stopped_uptime_ms":%d}\n' \
        "$PROTOCOL" "${P[run_id]}" "${P[phase]}" "$LAYER" "$REASON" "$$" "$CAP_PID" "$TRACE_PID" "$([[ $READY == 1 ]] && printf true || printf false)" "$CAP_BYTES" "$TRACE_BYTES" "$TRUNCATED" "$RAW_EXPORT" "$REPORT_EXIT" "$STATS_VALID" "$LOST_EVENTS" "$CLEANUP" "$instance_removed" "$START_MS" "$STOP_UPTIME" > "$OUT/.terminal.json.next" && mv -f -- "$OUT/.terminal.json.next" "$OUT/terminal.json"
    printf '%s\n' "GuestObserver: $REASON; cleanup_confirmed=$CLEANUP; measurement_valid=false" >&2
    # Zero means a requested stop / expected cold disappearance was handled, not valid evidence.
    if [[ $CLEANUP != true ]]; then exit 3; fi
    if [[ $REASON == STOP_REQUESTED || ( $REASON == TARGET_GONE && ${P[phase]} == cold ) ]]; then exit 0; fi
    exit "$incoming"
}
on_signal() {
    REASON=SIGNALLED
    regular "$OUT/watchdog.reason" && REASON=$(< "$OUT/watchdog.reason")
    exit 1
}

main() {
    local binary remaining budget duration deadline_reason ready_until now help status
    for binary in sha256sum readlink stat od tr cmp tail date sort grep cp mv find cat sleep setsid prlimit timeout bash getconf; do
        command -v "$binary" >/dev/null || { printf 'GuestObserver: missing %s\n' "$binary" >&2; return 69; }
    done
    parse_permit "$@" || { printf 'GuestObserver: PERMIT_INVALID\n' >&2; return 64; }
    configure_roots || { printf 'GuestObserver: %s\n' "${ERROR:-ADMISSION_FAILED}" >&2; return 65; }
    GLOBAL_STATE=$(global_fingerprint) || { printf 'GuestObserver: GLOBAL_TRACE_STATE_UNREADABLE\n' >&2; return 65; }
    check_permit && check_time_heartbeat || { printf 'GuestObserver: %s\n' "$ERROR" >&2; return 65; }
    OUT="$DIR/observer"
    [[ ! -e $OUT && ! -L $OUT ]] && mkdir -- "$OUT" || { printf 'GuestObserver: STALE_OUTPUT\n' >&2; return 65; }
    OUTPUT_CREATED=1; OUT_STAT=$(dir_stamp "$OUT")
    START_MS=$(epoch_ms); START_UPTIME=$(uptime_ms) || return 1
    # Leave time for bounded signal/drain/export, inside the 90-second capture wall.
    remaining=$((P[deadline_epoch_ms] - START_MS)); budget=$((MAX_MS - 6000))
    deadline_reason=MAXIMUM_TIME
    if ((remaining < budget)); then budget=$remaining; deadline_reason=DEADLINE; fi
    ((budget > 0)) || { ERROR=DEADLINE; return 1; }
    HARD_END_UPTIME=$((START_UPTIME + budget)); printf -v duration '%d.%03d' "$((budget/1000))" "$((budget%1000))"
    trap 'finish $?' EXIT
    trap on_signal INT TERM HUP
    proc_info "$$" || { ERROR=PROCESS_OWNERSHIP_UNAVAILABLE; return 1; }
    spawn_owned watchdog bash -c 'sleep "$1"; if IFS= read -r v < "/proc/$3/stat"; then v=${v##*) }; read -r -a f <<< "$v"; if [[ ${f[19]} == "$4" ]]; then printf "%s\n" "$5" > "$2/watchdog.reason"; kill -TERM "$3"; fi; fi' observer-watchdog "$duration" "$OUT" "$$" "$PROC_START" "$deadline_reason" || return 1
    TIMER_PID=$SPAWN_PID
    check_identity || return 1
    INITIAL_TOPOLOGY=$TOPOLOGY
    validate_formats || return 1
    # -B is specified before every event; the unnamed/global trace buffer is never selected.
    timeout 1 prlimit --fsize="$MAX_BYTES:$MAX_BYTES" -- "$TRACECMD" record -h > "$OUT/record-help.txt" 2>&1 || true
    timeout 1 prlimit --fsize="$MAX_BYTES:$MAX_BYTES" -- "$TRACECMD" report -h > "$OUT/report-help.txt" 2>&1 || true
    for help in B b C m o; do grep -Eq -- "(^|[[:space:],])-$help([[:space:],]|$)" "$OUT/record-help.txt" || { ERROR=TRACE_INSTANCE_OPTIONS_UNSUPPORTED; return 1; }; done
    for help in R N t; do grep -Eq -- "(^|[[:space:],])-$help([[:space:],]|$)" "$OUT/report-help.txt" || { ERROR=RAW_REPORT_UNSUPPORTED; return 1; }; done
    prepare_instance || return 1
    check_permit && check_time_heartbeat && check_identity || return 1
    spawn_owned tcpdump "$TCPDUMP" -i "usbmon${P[busnum]}" -s 131072 -U -c 24000 -w "$OUT/usbmon.pcap" || return 1
    CAP_PID=$SPAWN_PID
    local -a clock_args=()
    [[ $CLOCK != mono ]] || clock_args=(-C mono)
    spawn_owned trace "$TRACECMD" record -B "$INSTANCE" -b 64 "${clock_args[@]}" -m 256 -o "$OUT/trace.dat" \
        -e xhci-hcd:xhci_urb_enqueue -f "$FILTER" \
        -e xhci-hcd:xhci_urb_giveback -f "$FILTER" \
        -e xhci-hcd:xhci_handle_transfer -e xhci-hcd:xhci_handle_event \
        -- sleep "$duration" || return 1
    TRACE_PID=$SPAWN_PID
    ready_until=$(( $(epoch_ms) + 2000 ))
    while :; do
        health || { ERROR=HEALTH_WRITE_FAILED; return 1; }
        check_time_heartbeat && check_permit || return 1
        health || { ERROR=HEALTH_WRITE_FAILED; return 1; }
        check_identity || return 1
        health || { ERROR=HEALTH_WRITE_FAILED; return 1; }
        check_caps || return 1
        child_alive "$CAP_PID" && child_alive "$TRACE_PID" && child_alive "$TIMER_PID" || { ERROR=CHILD_EXIT; return 1; }
        REASON=INITIALIZING; health || { ERROR=HEALTH_WRITE_FAILED; return 1; }
        BARRIER_REASON=PCAP_HEADER_PENDING
        if pcap_header_valid && instance_ready; then
            snapshot_ready_barrier || { ERROR=STARTUP_DIAGNOSTIC_WRITE_FAILED; return 1; }
            break
        fi
        now=$(epoch_ms)
        if ((now >= ready_until)); then
            snapshot_ready_barrier || { ERROR=STARTUP_DIAGNOSTIC_WRITE_FAILED; return 1; }
            ERROR=OBSERVER_NOT_READY
            return 1
        fi
        sleep "$POLL_SECONDS"
    done
    HEADER_VALID=1; READY=1; REASON=RECORDING
    health || { ERROR=HEALTH_WRITE_FAILED; return 1; }
    printf '{"protocol":"%s","run_id":"%s","phase":"%s","evidence_layer":"%s","measurement_valid":false,"pid":%d,"tcpdump_pid":%d,"trace_pid":%d,"alive":true,"ready":true,"instance":"%s","clock":"%s","pcap_header_valid":true,"snaplen":131072,"maximum_ms":90000,"maximum_bytes":67108864,"per_cpu_buffer_kib":64,"per_cpu_buffer_effective_kib":67,"ring_page_bytes":4096,"ring_page_header_bytes":16,"per_cpu_spool_kib":256,"raw_report_required":true,"urb_filter":"%s","raw_events_unfiltered":true,"capture_bytes":%d,"trace_bytes":%d}\n' \
        "$PROTOCOL" "${P[run_id]}" "${P[phase]}" "$LAYER" "$$" "$CAP_PID" "$TRACE_PID" "$INSTANCE" "$CLOCK" "$FILTER" "$CAP_BYTES" "$TRACE_BYTES" > "$OUT/.ready.json.next" && mv -- "$OUT/.ready.json.next" "$OUT/ready.json" || { ERROR=READY_WRITE_FAILED; return 1; }
    while :; do
        health || { ERROR=HEALTH_WRITE_FAILED; return 1; }
        check_time_heartbeat && check_permit || return 1
        health || { ERROR=HEALTH_WRITE_FAILED; return 1; }
        check_identity || return 1
        health || { ERROR=HEALTH_WRITE_FAILED; return 1; }
        check_caps || return 1
        child_alive "$CAP_PID" && child_alive "$TRACE_PID" && child_alive "$TIMER_PID" || { ERROR=CHILD_EXIT; return 1; }
        health || { ERROR=HEALTH_WRITE_FAILED; return 1; }
        sleep "$POLL_SECONDS"
    done
}

main "$@"
status=$?
if ((OUTPUT_CREATED)); then REASON=${ERROR:-INTERNAL_ERROR}; finish "$status"; fi
exit "$status"
