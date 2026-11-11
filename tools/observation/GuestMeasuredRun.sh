#!/bin/bash
# SPDX-License-Identifier: GPL-2.0-only
set -Eeuo pipefail
umask 077
[[ $# == 2 ]] || exit 2
permit=$1; expected=$2
[[ "$expected" =~ ^[a-f0-9]{64}$ && -f "$permit" && ! -L "$permit" ]] || exit 2
[[ $(sha256sum "$permit" | cut -d' ' -f1) == "$expected" ]] || exit 3
declare -A p=()
while IFS='=' read -r key value; do
 [[ -n "$key" && -n "$value" && ! -v 'p[$key]' && "$value" != *$'\r'* ]] || exit 3
 case "$key" in protocol|run_id|phase|approved|boot_id|usb_path|busnum|devnum|descriptor_sha256|physical_epoch|root_grant_sha256|deadline_epoch_ms|module_root|board_sha256|sony_sha256|alp_sha256|receiver_sha256|firmware_verified) ;; *) exit 3;; esac
 p[$key]=$value
done < "$permit"
[[ ${#p[@]} == 18 && ${p[protocol]} == TBS5530-MEASURED-RUNTIME/v1 && ${p[approved]} == 1 ]] || exit 3
run=${p[run_id]}; phase=${p[phase]}; usb=${p[usb_path]}; modules=${p[module_root]}
[[ "$run" =~ ^phase31-layered-[0-9]{8}-[0-9]{6}-[a-f0-9]{8}$ && ( "$phase" == cold || "$phase" == operational ) ]] || exit 3
root="/root/$run/$phase"; [[ $(dirname "$permit") == "$root" && $(realpath -e "$root") == "$root" ]] || exit 3
cd "$root"; [[ ! -L . && ! -e runtime.started ]] || exit 3
[[ $(id -u) == 0 && $(uname -r) == 6.8.0-149-generic && $(cat /proc/sys/kernel/random/boot_id) == "${p[boot_id]}" ]] || exit 3
[[ "$usb" =~ ^[0-9]+-[0-9]+(\.[0-9]+)*$ && "$modules" == /root/phase31-original-20261109-234104-c6017f83 ]] || exit 3
[[ ${p[board_sha256]} == 5ad5512988800c2ee6954faa4fed8058f33c489f505a01ac6086b2859a2141a6 && ${p[sony_sha256]} == f7aa958f765bf1b36fd2f504d39daf6d7a31d0b69eb303e9a9f2c791a57f919a && ${p[alp_sha256]} == 535ccc3d981f349a829fc1be885456b705ae56c65292439b6dc9bcf6a93aabe0 && ${p[receiver_sha256]} == 04f57dd49fbe279e732ef6a1d5d2187d978d3df48c42d777de9f01fa95269550 ]] || exit 3
for pair in "minimal/tbs5530_minimal.ko:${p[board_sha256]}" "minimal/cxd2878.ko:${p[sony_sha256]}" "minimal/alp.ko:${p[alp_sha256]}" "ReceiveOriginalK36:${p[receiver_sha256]}"; do
 file=${pair%:*}; hash=${pair##*:}; [[ $(sha256sum "$modules/$file" | cut -d' ' -f1) == "$hash" ]] || exit 3
done
[[ ${p[deadline_epoch_ms]} =~ ^[0-9]+$ && $(date +%s%3N) -lt ${p[deadline_epoch_ms]} && ${p[root_grant_sha256]} =~ ^[a-f0-9]{64}$ && ${p[physical_epoch]} =~ ^[a-f0-9-]{36}$ ]] || exit 3
node="/sys/bus/usb/devices/$usb"
[[ $(cat "$node/idVendor") == 734c && $(cat "$node/idProduct") == 5530 && $(cat "$node/speed") == 480 && $(cat "$node/busnum") == "${p[busnum]}" && $(cat "$node/devnum") == "${p[devnum]}" && $(sha256sum "$node/descriptors" | cut -d' ' -f1) == "${p[descriptor_sha256]}" ]] || exit 3
[[ ! -e "$node:1.0/driver" && ! -L "$node:1.0/driver" && -f observer/ready.json && -f observer/health.json ]] || exit 3
[[ ! -e /sys/module/tbs5530_minimal && ! -e ../QUARANTINE ]] || exit 3
for m in tbs5530 tbs5530_minimal tbs5530_ter_diag cxd2878 m88rs6060 alp; do [[ ! -d /sys/module/$m ]] || exit 3; done
if [[ "$phase" == cold ]]; then
 [[ ${p[descriptor_sha256]} == e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de && ${p[firmware_verified]} == 0 ]] || exit 3
 [[ $(sha256sum /lib/firmware/dvb-usb-id5530.fw | cut -d' ' -f1) == b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc ]] || exit 3
else
 [[ ${p[descriptor_sha256]} == 22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5 && ${p[firmware_verified]} == 1 && ! -e /lib/firmware/dvb-usb-id5530.fw ]] || exit 3
 [[ -f ../firmware-verified.json ]] || exit 3
fi
ledger=/root/phase31-linux-original-ledger-v1
[[ -d "$ledger" && ! -L "$ledger" ]] || exit 3
key=$(printf '%s:%s:%s:%s' "${p[boot_id]}" "${p[busnum]}" "${p[devnum]}" "$usb" | sha256sum | cut -d' ' -f1)
mkdir "$ledger/$key" || exit 3
printf '%s\n' "$run:$phase:${p[physical_epoch]}" > "$ledger/$key/consumed"
sync -f "$ledger/$key/consumed"
: > runtime.started
cp "$node/descriptors" descriptors-before.bin
exec > runtime.stdout.txt 2> runtime.stderr.txt
loaded_core=0; loaded_alp=0; loaded_cxd=0; attempted=0; watchdog=''; cleanup_ok=1
stop_board() { if [[ -w /sys/module/tbs5530_minimal/parameters/stop ]]; then printf 1 > /sys/module/tbs5530_minimal/parameters/stop; fi; }
publish_runtime_terminal() {
 printf '{"phase":"%s","result":%s,"cleanup_confirmed":%s,"firmware_verified":false,"measurement_pass":false}\n' "$phase" "$result" "$([[ $cleanup_ok == 1 ]] && echo true || echo false)" > runtime-terminal.tmp || return 1
 sync -f runtime-terminal.tmp && mv -- runtime-terminal.tmp runtime-terminal.json && sync -f runtime-terminal.json
}
cleanup() {
 result=$?; trap - EXIT INT TERM; set +e
 stop_board
 if [[ -n "$watchdog" ]]; then kill -TERM "$watchdog" 2>/dev/null; wait "$watchdog" 2>/dev/null; fi
 if (( attempted )) && [[ -d /sys/module/tbs5530_minimal ]]; then
  drained=0
  for ((i=0;i<100;i++)); do
   t=$(cat /sys/module/tbs5530_minimal/parameters/telemetry 2>/dev/null)
   printf '%s\n' "$t" >> drain.txt
   if [[ " $t " == *' streaming=0 '* && " $t " == *' outstanding=0 '* || " $t " == *' bound=0 '* ]]; then drained=1; break; fi
   sleep .05
  done
  if (( drained )); then timeout 8s rmmod tbs5530_minimal || cleanup_ok=0; else cleanup_ok=0; fi
 fi
 if (( cleanup_ok && loaded_cxd )); then timeout 8s rmmod cxd2878 || cleanup_ok=0; fi
 if (( cleanup_ok && loaded_alp )); then timeout 8s rmmod alp || cleanup_ok=0; fi
 if (( cleanup_ok && loaded_core )); then timeout 8s rmmod dvb_core || cleanup_ok=0; fi
 dmesg | tail -160 > dmesg-after.txt
 publish_runtime_terminal || cleanup_ok=0
 printf 'result=%s cleanup_ok=%s\n' "$result" "$cleanup_ok" > "$ledger/$key/terminal";sync -f "$ledger/$key/terminal"
 (( cleanup_ok )) || exit 4
 exit "$result"
}
trap cleanup EXIT;trap 'exit 130' INT;trap 'exit 143' TERM
if [[ ! -d /sys/module/dvb_core ]]; then modprobe dvb_core;loaded_core=1;fi
insmod "$modules/minimal/alp.ko";loaded_alp=1
insmod "$modules/minimal/cxd2878.ko";loaded_cxd=1
require_heartbeat() {
 [[ $(sha256sum "$permit" | cut -d' ' -f1) == "$expected" && ! -e STOP_RUNTIME && ! -e WATCHDOG_STOP && $(date +%s%3N) -lt ${p[deadline_epoch_ms]} && -f host-heartbeat ]] || return 1
 local beat now;beat=$(cat host-heartbeat);now=$(awk '{printf "%.0f",$1*1000}' /proc/uptime)
 [[ "$beat" =~ ^[0-9]+$ && $now -ge $beat && $((now-beat)) -lt 2000 ]]
}
# This watchdog is independent of the SSH transport and never performs USB I/O.
owner_pid=$$
(
 while kill -0 "$owner_pid" 2>/dev/null; do
  if ! require_heartbeat;then
   : > WATCHDOG_STOP
   while kill -0 "$owner_pid" 2>/dev/null;do stop_board;sleep .05;done
   exit 0
  fi
  now=$(awk '{printf "%.0f",$1*1000}' /proc/uptime)
  if [[ -w /sys/module/tbs5530_minimal/parameters/heartbeat ]];then printf '%s' "$now" > /sys/module/tbs5530_minimal/parameters/heartbeat;fi
  sleep .1
 done
) & watchdog=$!
require_heartbeat || exit 3
attempted=1
args=(minimal_enable=1 expected_busnum="${p[busnum]}" expected_devnum="${p[devnum]}" heartbeat=1)
if [[ "$phase" == cold ]];then args+=(allow_cold_firmware=1 operational_handoff=0);else args+=(allow_cold_firmware=0 operational_handoff=1);fi
insmod "$modules/minimal/tbs5530_minimal.ko" "${args[@]}"
cat /sys/module/tbs5530_minimal/parameters/telemetry > after-probe.txt
if [[ "$phase" == cold ]];then exit 0;fi
[[ $(cat after-probe.txt) == *'bound=1 '* && $(cat after-probe.txt) == *'fault=0 '* && $(cat after-probe.txt) == *'firmware_calls=0'* ]] || exit 3
parent=$(readlink -f "$node");adapters=()
for n in /sys/class/dvb/dvb*.frontend0;do
 [[ -e "$n" ]] || continue; real=$(readlink -f "$n/device")
 [[ "$real" == "$parent" || "$real" == "$parent/"* ]] || continue
 name=${n##*/};[[ "$name" =~ ^dvb([0-9]+)\.frontend0$ ]] || continue;adapters+=("${BASH_REMATCH[1]}")
done
[[ ${#adapters[@]} == 1 ]] || exit 3
printf '%s\n' "${adapters[0]}" > adapter.txt
timeout -s TERM 28s "$modules/ReceiveOriginalK36" "${adapters[0]}" "$parent" sample.ts > receiver.stdout.txt 2> receiver.stderr.txt
