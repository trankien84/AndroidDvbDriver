#!/usr/bin/env bash
# Synthetic file writer: never opens usbmon or any device.
set -eu
root=${GUEST_OBSERVER_FIXTURE_ROOT:?}
[[ -f $root/GuestFixture.marker && $(< "$root/GuestFixture.marker") == TBS5530-OBSERVER-SYNTHETIC/v1 ]] || exit 80
[[ $# == 9 && $1 == -i && $2 == usbmon2 && $3 == -s && $4 == 131072 && $5 == -U && $6 == -c && $7 == 24000 && $8 == -w ]] || exit 81
out=$9
[[ $out == "$root/runs/"*/observer/usbmon.pcap && ! -e $out ]] || exit 82
printf '%s\n' "$@" > "$root/GuestTcpdump.args"
printf '%s\n' "$$" > "$root/GuestTcpdump.pid"
trap 'exit 0' INT TERM
if [[ ${GUEST_FIXTURE_SCENARIO:-} == no-header ]]; then
    : > "$out"
else
    # Little-endian pcap 2.4, snaplen 131072, LINKTYPE_USB_LINUX_MMAPPED (220).
    printf '\324\303\262\241\002\000\004\000\000\000\000\000\000\000\000\000\000\000\002\000\334\000\000\000' > "$out"
fi
if [[ ${GUEST_FIXTURE_SCENARIO:-} == bad-header ]]; then printf 'not a usbmon capture at all' > "$out"; fi
if [[ ${GUEST_FIXTURE_SCENARIO:-} == descendants ]]; then
    bash -c 'trap "exit 0" TERM; while :; do sleep 0.05; done' &
    printf '%s\n' "$!" > "$root/GuestTcpdump.descendant"
fi
while :; do
    [[ ! -f $root/GuestAction.exit-tcpdump ]] || exit 7
    if [[ -f $root/GuestAction.cap ]]; then
        # Sparse synthetic data exercises the inherited file limit without payloads.
        truncate -s 67108864 "$out"
        ( trap '' XFSZ; printf 'x' | dd of="$out" bs=1 seek=67108864 conv=notrunc status=none ) 2>/dev/null || true
        rm -- "$root/GuestAction.cap"
    fi
    sleep 0.05
done
