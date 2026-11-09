#!/bin/bash
# SPDX-License-Identifier: GPL-2.0-only
set -Eeuo pipefail
umask 077
[[ $# == 5 ]] || exit 2
run=$1; usb=$2; board=$3; mode=$4; timing=$5
[[ $timing == late || $timing == early ]] || exit 2
[[ $mode != cold || $timing == late ]] || exit 2
[[ $run =~ ^phase31-original-[0-9]{8}-[0-9]{6}-[a-f0-9]{8}$ && $usb =~ ^[0-9]+-[0-9]+(\.[0-9]+)*$ && $board =~ ^[a-f0-9]{64}$ ]] || exit 2
[[ $mode == cold || $mode == handoff ]] || exit 2
root="/root/$run"
cd "$root"
[[ $(pwd -P) == "$root" && ! -L "$root" && ! -e "runtime.$mode.started" ]] || exit 3
if [[ $mode == cold ]]; then [[ ! -e runtime.started ]] || exit 3; fi
: > "runtime.$mode.started"
exec >"runtime.$mode.stdout.txt" 2>"runtime.$mode.stderr.txt"
p="/sys/bus/usb/devices/$usb"
[[ $(id -u) == 0 && $(uname -r) == 6.8.0-149-generic ]] || exit 3
[[ $(cat "$p/idVendor") == 734c && $(cat "$p/idProduct") == 5530 && $(cat "$p/speed") == 480 ]] || exit 3
[[ ! -e "$p:1.0/driver" && ! -L "$p:1.0/driver" ]] || exit 3
for m in tbs5530 tbs5530_minimal tbs5530_ter_diag cxd2878 m88rs6060 alp; do [[ ! -d /sys/module/$m ]] || exit 3; done
[[ $(sha256sum minimal/tbs5530_minimal.ko | cut -d' ' -f1) == "$board" ]] || exit 3
if [[ $mode == cold ]]; then
 [[ $(sha256sum /lib/firmware/dvb-usb-id5530.fw | cut -d' ' -f1) == b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc ]] || exit 3
else
 [[ -f operational-confirmed && -f firmware-132-verified && ! -e /lib/firmware/dvb-usb-id5530.fw ]] || exit 3
 [[ $(sha256sum "$p/descriptors" | cut -d' ' -f1) == 22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5 ]] || exit 3
fi
[[ -f capture.ready && -s usbmon.pcap && -f physical-confirmed && ! -L physical-confirmed ]] || exit 3
boot=$(cat /proc/sys/kernel/random/boot_id)
bus=$(cat "$p/busnum"); device=$(cat "$p/devnum")
key=$(printf '%s:%s:%s:%s' "$boot" "$bus" "$device" "$usb" | sha256sum | cut -d' ' -f1)
ledger=/root/phase31-linux-original-ledger-v1
[[ -d "$ledger" && ! -L "$ledger" ]] || exit 3
mkdir "$ledger/$key" || exit 3
printf '%s\n' "$run" > "$ledger/$key/consumed"
sync -f "$ledger/$key/consumed"
printf 'boot=%s\nbus=%s\ndevice=%s\nusb=%s\n' "$boot" "$bus" "$device" "$usb" > "attachment.$mode.txt"
sha256sum "$p/descriptors" > "descriptors-before.$mode.sha256"
cp "$p/descriptors" "descriptors-before.$mode.bin"
loaded_core=0; loaded_alp=0; loaded_cxd=0; attempted=0; cleanup_ok=1
cleanup() {
 result=$?
 trap - EXIT INT TERM
 set +e
 if (( attempted )) && [[ -d /sys/module/tbs5530_minimal ]]; then
  printf '1' > /sys/module/tbs5530_minimal/parameters/stop
  drained=0
  for ((i=0;i<100;i++)); do
   t=$(cat /sys/module/tbs5530_minimal/parameters/telemetry 2>/dev/null)
   printf '%s\n' "$t" >> "drain.$mode.txt"
   if [[ " $t " == *' streaming=0 '* && " $t " == *' outstanding=0 '* ]]; then drained=1; break; fi
   if [[ " $t " == *' bound=0 '* ]]; then drained=1; break; fi
   sleep .05
  done
  if (( drained )); then timeout 8s rmmod tbs5530_minimal || cleanup_ok=0; else cleanup_ok=0; fi
 fi
 if (( cleanup_ok && loaded_cxd )); then timeout 8s rmmod cxd2878 || cleanup_ok=0; fi
 if (( cleanup_ok && loaded_alp )); then timeout 8s rmmod alp || cleanup_ok=0; fi
 if (( cleanup_ok && loaded_core )); then timeout 8s rmmod dvb_core || cleanup_ok=0; fi
 dmesg | tail -160 > "dmesg-after.$mode.txt"
 printf 'result=%s\ncleanup_ok=%s\n' "$result" "$cleanup_ok" > "runtime.$mode.result"
 printf 'result=%s cleanup_ok=%s\n' "$result" "$cleanup_ok" > "$ledger/$key/terminal"
 sync -f "$ledger/$key/terminal"
 (( cleanup_ok )) || exit 4
 exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
if [[ ! -d /sys/module/dvb_core ]]; then modprobe dvb_core; loaded_core=1; fi
insmod minimal/alp.ko; loaded_alp=1
insmod minimal/cxd2878.ko; loaded_cxd=1
attempted=1
args=(minimal_enable=1 expected_busnum="$bus" expected_devnum="$device" heartbeat=1)
if [[ $mode == cold ]]; then args+=(allow_cold_firmware=1 operational_handoff=0); else args+=(allow_cold_firmware=0 operational_handoff=1); fi
if [[ $timing == early ]]; then [[ -f timing-early-confirmed ]] || exit 3; args+=(early_stream_select=1); fi
insmod minimal/tbs5530_minimal.ko "${args[@]}"
cat /sys/module/tbs5530_minimal/parameters/telemetry > "after-probe.$mode.txt"
telemetry=$(cat "after-probe.$mode.txt")
[[ "$telemetry" == *'bound=1 '* && "$telemetry" == *'fault=0 '* ]] || exit 3
if [[ $mode == cold ]]; then [[ "$telemetry" == *'firmware_calls=132'* ]] || exit 3; else [[ "$telemetry" == *'firmware_calls=0'* ]] || exit 3; fi
[[ $(readlink -f "$p:1.0/driver") == /sys/bus/usb/drivers/tbs5530_minimal ]] || exit 3
adapters=()
parent=$(readlink -f "$p")
for n in /sys/class/dvb/dvb*.frontend0; do
 [[ -e "$n" ]] || continue
 real=$(readlink -f "$n/device")
 [[ "$real" == "$parent" || "$real" == "$parent/"* ]] || continue
 name=${n##*/}; [[ "$name" =~ ^dvb([0-9]+)\.frontend0$ ]] || continue
 adapters+=("${BASH_REMATCH[1]}")
done
[[ ${#adapters[@]} == 1 ]] || exit 3
printf '%s\n' "${adapters[0]}" > adapter.txt
timeout -s TERM 28s ./ReceiveOriginalK36 "${adapters[0]}" "$parent" sample.ts > receiver.stdout.txt 2> receiver.stderr.txt
