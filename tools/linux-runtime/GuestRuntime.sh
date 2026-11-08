#!/bin/bash
# SPDX-License-Identifier: GPL-2.0-only
set -Eeuo pipefail
umask 077
# Parent supplies an already reviewed module argument file, not shell expressions.
if [ "$#" -ne 7 ]; then echo 'Expected run boot bus device attachment-key module-dir board-sha256' >&2; exit 2; fi
run=$1; boot=$2; bus=$3; device=$4; key=$5; modules=$6; board_hash=$7
[[ "$run" =~ ^phase31-linux-runtime-[0-9]{8}-[0-9]{6}-[a-f0-9]{8}$ ]] || exit 2
[[ "$boot" =~ ^[a-f0-9-]{36}$ && "$bus" =~ ^[1-9][0-9]{0,2}$ && "$device" =~ ^[1-9][0-9]{0,2}$ ]] || exit 2
[[ "$key" =~ ^[a-f0-9]{64}$ && "$board_hash" =~ ^[a-f0-9]{64}$ ]] || exit 2
[[ "$modules" =~ ^/root/phase31-[A-Za-z0-9_-]+/source$ ]] || exit 2
root="/root/$run"
cd -- "$root"
[[ "$(pwd -P)" == "$root" && ! -L "$root" ]] || exit 2
exec >runner.stdout.txt 2>runner.stderr.txt
printf 'START %s\n' "$(date -Is)"
[[ "$(cat /proc/sys/kernel/random/boot_id)" == "$boot" ]] || { echo 'Guest boot changed'; exit 3; }
[[ "$(sha256sum "$modules/tbs5530_ter_diag.ko" | cut -d' ' -f1)" == "$board_hash" ]] || exit 3
sha256sum -c runtime-files.sha256
sha256sum -c module-files.sha256
# No firmware, probing tool or USB writes are used by admission.
match=()
for p in /sys/bus/usb/devices/*; do
 if [[ -f "$p/idVendor" && "$(cat "$p/idVendor")" == 734c && "$(cat "$p/idProduct")" == 5530 ]]; then match+=("$p"); fi
done
[[ "${#match[@]}" == 1 ]] || { echo 'Missing/ambiguous tuner'; exit 3; }
p=${match[0]}
[[ "$(cat "$p/busnum")" == "$bus" && "$(cat "$p/devnum")" == "$device" && "$(cat "$p/speed")" == 480 ]] || exit 3
[[ "$(sha256sum "$p/descriptors" | cut -d' ' -f1)" == 22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5 ]] || exit 3
[[ ! -e "$p:1.0/driver" && ! -L "$p:1.0/driver" ]] || { echo 'Interface already bound'; exit 3; }
for m in tbs5530 tbs5530_ter_diag cxd2878 m88rs6060 alp; do [[ ! -d "/sys/module/$m" ]] || { echo "Competing module: $m"; exit 3; }; done
[[ -d /sys/module/usbmon && -e "/sys/kernel/debug/usb/usbmon/${bus}u" ]] || exit 3
[[ -f module-arguments.txt && ! -L module-arguments.txt ]] || exit 3
mapfile -t args < module-arguments.txt
[[ "${#args[@]}" == 11 || "${#args[@]}" == 12 ]] || exit 3
for value in "${args[@]}"; do
 [[ "$value" =~ ^[a-z_0-9]+=[a-f0-9]+$ ]] || exit 3
 name=${value%%=*}
 case "$name" in diagnostic_enable|operational_only|allow_firmware_bootstrap|expected_busnum|expected_devnum|expected_profile_sha256|session_ms|receive_ms|max_bytes|consent|heartbeat) ;; empty_success_credit) [[ "$value" == empty_success_credit=0 || "$value" == empty_success_credit=1 ]] || exit 3;; *) exit 3;; esac
done
for required in diagnostic_enable=1 operational_only=1 allow_firmware_bootstrap=0 "expected_busnum=$bus" "expected_devnum=$device" expected_profile_sha256=22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5 session_ms=120000 receive_ms=10000 max_bytes=33554432 consent=1 heartbeat=1; do
 [[ " $(printf '%s ' "${args[@]}") " == *" $required "* ]] || exit 3
done
# Guest ledger supplements the durable Windows ledger and survives module reload.
ledger=/root/phase31-linux-operational-ledger-v1
[[ -d "$ledger" && ! -L "$ledger" && -f "$ledger/initialized" ]] || { echo 'Guest ledger not initialized'; exit 3; }
mkdir -- "$ledger/$key" || { echo 'Attachment already consumed'; exit 3; }
printf '%s\n' "$run" > "$ledger/$key/consumed-before-load"
sync -f "$ledger/$key/consumed-before-load"
trace_pid=''; text_pid=''; heartbeat_pid=''; loaded_board=0; board_attempted=0; loaded_cxd=0; loaded_alp=0; loaded_core=0; cleanup_ok=1
stop_trace() {
 for id in "$trace_pid" "$text_pid"; do
  if [[ -n "$id" ]] && kill -0 "$id" 2>/dev/null; then kill -INT "$id" 2>/dev/null || true; fi
 done
 for id in "$trace_pid" "$text_pid"; do if [[ -n "$id" ]]; then wait "$id" 2>/dev/null || true; fi; done
}
cleanup() {
 result=$?
 trap - EXIT INT TERM
 set +e
 if (( board_attempted )) && [[ -d /sys/module/tbs5530_ter_diag ]]; then loaded_board=1; fi
 # First request a control-free stop, then unload only modules created by this run.
 if [[ -w /sys/module/tbs5530_ter_diag/parameters/stop ]]; then printf '1' > /sys/module/tbs5530_ter_diag/parameters/stop; fi
 if [[ -n "$heartbeat_pid" ]] && kill -0 "$heartbeat_pid" 2>/dev/null; then kill -TERM "$heartbeat_pid" 2>/dev/null; wait "$heartbeat_pid" 2>/dev/null; fi
 if (( loaded_board )); then
  cat /sys/module/tbs5530_ter_diag/parameters/telemetry > telemetry-before-cleanup.txt 2>/dev/null
  drained=0
  for ((i=0; i<100; i++)); do
   telemetry=$(cat /sys/module/tbs5530_ter_diag/parameters/telemetry 2>/dev/null)
   printf '%s\n' "$telemetry" >> telemetry-cleanup.txt
   if [[ " $telemetry " == *' outstanding=0 '* && " $telemetry " == *' drained=1 '* && " $telemetry " == *' quarantined=0 '* ]]; then drained=1; break; fi
   sleep 0.05
  done
  if (( drained )); then timeout 8s rmmod tbs5530_ter_diag; [[ "$?" == 0 ]] || cleanup_ok=0; else cleanup_ok=0; fi
 fi
 if (( cleanup_ok && loaded_cxd )); then timeout 8s rmmod cxd2878; [[ "$?" == 0 ]] || cleanup_ok=0; fi
 if (( cleanup_ok && loaded_alp )); then timeout 8s rmmod alp; [[ "$?" == 0 ]] || cleanup_ok=0; fi
 if (( cleanup_ok && loaded_core )); then timeout 8s rmmod dvb_core; [[ "$?" == 0 ]] || cleanup_ok=0; fi
 stop_trace
 dmesg | tail -180 > dmesg-after.txt
 printf 'END %s result=%s cleanup_ok=%s\n' "$(date -Is)" "$result" "$cleanup_ok"
 printf 'result=%s\ncleanup_ok=%s\n' "$result" "$cleanup_ok" > result.txt
 if (( ! cleanup_ok )); then printf 'UNCERTAIN\n' > "$ledger/$key/terminal"; exit 4; fi
 printf 'FINISHED_RESULT_%s\n' "$result" > "$ledger/$key/terminal"
 exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# Trace has its own deadline even if the SSH client disappears.
timeout -s INT -k 3s 65s tcpdump -i "usbmon$bus" -s 128 -U -c 24000 -w usbmon.pcap >tcpdump.stdout.txt 2>tcpdump.stderr.txt &
trace_pid=$!
timeout -s INT -k 3s 65s sh -c 'exec cat "$1"' sh "/sys/kernel/debug/usb/usbmon/${bus}u" >usbmon.txt 2>usbmon-text.stderr.txt &
text_pid=$!
for ((i=0; i<40; i++)); do
 [[ -f usbmon.pcap && "$(stat -c %s usbmon.pcap)" -ge 24 ]] && break
 kill -0 "$trace_pid" 2>/dev/null || { echo 'Trace exited before ready'; exit 3; }
 sleep 0.05
done
[[ -s usbmon.pcap && "$(stat -c %s usbmon.pcap)" -ge 24 ]] || { echo 'No capture header'; exit 3; }
if [[ ! -d /sys/module/dvb_core ]]; then modprobe dvb_core; loaded_core=1; fi
insmod "$modules/alp.ko"; loaded_alp=1
insmod "$modules/cxd2878.ko"; loaded_cxd=1
printf 'BEFORE_BOARD %s\n' "$(date -Is)"
# The helper is bounded by its parent PID and 60s; it never performs USB I/O.
(
 parent=$$
 value=2
 for ((i=0; i<120; i++)); do
  kill -0 "$parent" 2>/dev/null || exit 0
  if [[ -w /sys/module/tbs5530_ter_diag/parameters/heartbeat ]]; then
   printf '%s' "$value" > /sys/module/tbs5530_ter_diag/parameters/heartbeat || exit 0
   value=$((value+1))
   if [[ -r /sys/module/tbs5530_ter_diag/parameters/telemetry ]]; then cat /sys/module/tbs5530_ter_diag/parameters/telemetry >> telemetry-progress.txt; fi
  fi
  sleep 0.5
 done
) &
heartbeat_pid=$!
board_attempted=1
insmod "$modules/tbs5530_ter_diag.ko" "${args[@]}"; loaded_board=1
printf 'AFTER_BOARD %s\n' "$(date -Is)"
initial_telemetry=$(cat /sys/module/tbs5530_ter_diag/parameters/telemetry)
printf '%s\n' "$initial_telemetry" > telemetry-after-probe.txt
[[ " $initial_telemetry " == *' fault=0 '* && " $initial_telemetry " == *' admitted=1 '* && " $initial_telemetry " == *' bound=1 '* ]] || { echo 'Probe telemetry did not confirm clean operational admission'; exit 3; }
[[ "$(readlink -f "$p:1.0/driver")" == /sys/bus/usb/drivers/tbs5530_ter_diag ]] || { echo 'Diagnostic did not bind'; exit 3; }
adapters=()
for n in "$p:1.0"/dvb/dvb*.frontend0; do
 [[ -e "$n" ]] || continue
 name=${n##*/}; [[ "$name" =~ ^dvb([0-9]+)\.frontend0$ ]] || continue
 adapters+=("${BASH_REMATCH[1]}")
done
# Some kernels publish DVB devices through the class tree instead of an interface child.
if [[ "${#adapters[@]}" == 0 ]]; then
 usb_real=$(readlink -f "$p")
 for n in /sys/class/dvb/dvb*.frontend0; do
  [[ -e "$n" ]] || continue
  real=$(readlink -f "$n/device")
  [[ "$real" == "$usb_real" || "$real" == "$usb_real/"* ]] || continue
  name=${n##*/}; [[ "$name" =~ ^dvb([0-9]+)\.frontend0$ ]] && adapters+=("${BASH_REMATCH[1]}")
 done
fi
[[ "${#adapters[@]}" == 1 ]] || { echo 'No unique adapter for exact USB interface'; exit 3; }
printf 'ADAPTER=%s\n' "${adapters[0]}"
# Outer timeout also bounds tuner ioctls and unexpected library/kernel delays.
set +e
timeout -s TERM -k 4s 28s ./ReceiveK36 "${adapters[0]}" capture.ts >receive.stdout.txt 2>receive.stderr.txt
receive_rc=$?
set -e
printf 'RECEIVER_EXIT=%s\n' "$receive_rc"
for f in /sys/module/tbs5530_ter_diag/parameters/*; do
 [[ -r "$f" ]] || continue
 printf '%s=' "${f##*/}" >> module-parameters-after.txt
 cat "$f" >> module-parameters-after.txt
 done
sha256sum capture.ts usbmon.pcap usbmon.txt > capture-hashes.txt
exit "$receive_rc"
