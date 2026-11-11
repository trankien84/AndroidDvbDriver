#!/usr/bin/env bash
# Synthetic trace-cmd replacement. All writes stay in the private fixture tree.
set -eu
root=${GUEST_OBSERVER_FIXTURE_ROOT:?}
[[ -f $root/GuestFixture.marker && $(< "$root/GuestFixture.marker") == TBS5530-OBSERVER-SYNTHETIC/v1 ]] || exit 80
if [[ $# == 2 && $2 == -h ]]; then
    case "$1" in
        record) printf ' -B buffer\n -b buffer-kib\n -C clock\n -m maximum-kib\n -o output\n' ;;
        report) printf ' -R raw\n -N no-plugins\n -t timestamps\n' ;;
        *) exit 81 ;;
    esac
    exit 0
fi
printf '%s\n' "$@" >> "$root/GuestTraceCmd.args"
if [[ ${1:-} == report ]]; then
    [[ $# == 6 && $2 == -R && $3 == -N && $4 == -t && $5 == -i ]] || exit 82
    [[ $6 == "$root/runs/"*/observer/trace.dat && -f $6 ]] || exit 83
    [[ ${GUEST_FIXTURE_SCENARIO:-} != raw-fail ]] || exit 84
    printf 'SYNTHETIC raw xhci_urb_giveback: status=-75 length=4096 actual=0 epnum=2 dir_in=1 type=2 slot_id=1\n'
    [[ ${GUEST_FIXTURE_SCENARIO:-} != raw-lost ]] || printf 'CPU:0 [LOST 2 EVENTS]\n'
    exit 0
fi
[[ ${1:-} == record ]] || exit 85
shift
[[ $# -ge 10 && $1 == -B && $3 == -b && $4 == 64 && $5 == -C && $6 == mono && $7 == -m && $8 == 256 && $9 == -o ]] || exit 86
instance=$2; output=${10}; shift 10
[[ $instance == tbs5530-* && $instance != */* && $output == "$root/runs/"*/observer/trace.dat ]] || exit 87
path="$root/tracefs/instances/$instance"
[[ -d $path && $(< "$path/buffer_size_kb") == 64 && $(< "$path/trace_clock") == mono ]] || exit 88
# Model the source-confirmed Linux 4 KiB/64-bit ring-page rounding and the
# recorder child that trace-cmd excludes from its existing URB event filter.
printf '67\n' > "$path/buffer_size_kb"
bash -c 'trap "exit 0" INT TERM; sleep 90 & wait' &
recorder=$!
printf '%s\n' "$recorder" > "$root/GuestTraceCmd.recorder"
expected=(xhci_urb_enqueue xhci_urb_giveback xhci_handle_transfer xhci_handle_event)
for event in "${expected[@]}"; do
    [[ ${1:-} == -e && ${2:-} == "xhci-hcd:$event" ]] || exit 89
    shift 2
    if [[ $event == xhci_urb_enqueue || $event == xhci_urb_giveback ]]; then
        [[ ${1:-} == -f && ${2:-} == 'epnum == 2 && dir_in == 1 && type == 2' ]] || exit 90
        printf '((%s))&&((common_pid!=%s))\n' "$2" "$recorder" > "$path/events/xhci-hcd/$event/filter"; shift 2
    else
        [[ ${1:-} != -f ]] || exit 91
    fi
    printf '1\n' > "$path/events/xhci-hcd/$event/enable"
done
[[ $# == 3 && $1 == -- && $2 == sleep && $3 =~ ^[0-9]+\.[0-9]{3}$ ]] || exit 92
printf '%s\n' "$$" > "$root/GuestTraceCmd.pid"
[[ ${GUEST_FIXTURE_SCENARIO:-} != partial-init ]] || exit 93
printf 'Guest synthetic trace.dat; signed giveback status=-75\n' > "$output"
printf '1\n' > "$path/tracing_on"
trap 'printf "0\n" > "$path/tracing_on"; exit 0' INT TERM
while :; do
    [[ ! -f $root/GuestAction.exit-trace ]] || exit 8
    if [[ -f $root/GuestAction.trace-cap ]]; then
        truncate -s 67108864 "$output"
        ( trap '' XFSZ; printf 'x' | dd of="$output" bs=1 seek=67108864 conv=notrunc status=none ) 2>/dev/null || true
        rm -- "$root/GuestAction.trace-cap"
    fi
    sleep 0.05
done
