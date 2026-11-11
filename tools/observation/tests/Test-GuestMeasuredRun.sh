#!/bin/bash
# SPDX-License-Identifier: GPL-2.0-only
set -Eeuo pipefail
root=$(mktemp -d "${TMPDIR:-/tmp}/tbs-runtime-admission.XXXXXX")
trap 'rm -f -- "$root"/*; rmdir -- "$root"' EXIT
source_dir=$(cd -- "$(dirname -- "$0")/.." && pwd -P)
script="$source_dir/GuestMeasuredRun.sh"
bash -n "$script"
checks=0
reject() { local file=$1 hash=$2; if bash "$script" "$file" "$hash" > "$root/out" 2> "$root/err";then echo 'Unsafe runtime admission accepted' >&2;exit 1;fi;checks=$((checks+1)); }
: > "$root/empty"
reject "$root/empty" "$(sha256sum "$root/empty" | cut -d' ' -f1)"
printf 'protocol=TBS5530-MEASURED-RUNTIME/v1\nprotocol=duplicate\n' > "$root/duplicate"
reject "$root/duplicate" "$(sha256sum "$root/duplicate" | cut -d' ' -f1)"
printf 'arbitrary_command=insmod\n' > "$root/unknown"
reject "$root/unknown" "$(sha256sum "$root/unknown" | cut -d' ' -f1)"
printf 'protocol=TBS5530-MEASURED-RUNTIME/v1\r\n' > "$root/crlf"
reject "$root/crlf" "$(sha256sum "$root/crlf" | cut -d' ' -f1)"
reject "$root/empty" "$(printf '0%.0s' {1..64})"
cat > "$root/full" <<'PERMIT'
protocol=TBS5530-MEASURED-RUNTIME/v1
run_id=phase31-layered-20261110-193945-623f1cf1
phase=operational
approved=1
boot_id=11111111-1111-4111-8111-111111111111
usb_path=2-1
busnum=2
devnum=3
descriptor_sha256=22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5
physical_epoch=22222222-2222-4222-8222-222222222222
root_grant_sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
deadline_epoch_ms=9999999999999
module_root=/root/phase31-original-20261109-234104-c6017f83
board_sha256=5ad5512988800c2ee6954faa4fed8058f33c489f505a01ac6086b2859a2141a6
sony_sha256=f7aa958f765bf1b36fd2f504d39daf6d7a31d0b69eb303e9a9f2c791a57f919a
alp_sha256=535ccc3d981f349a829fc1be885456b705ae56c65292439b6dc9bcf6a93aabe0
receiver_sha256=04f57dd49fbe279e732ef6a1d5d2187d978d3df48c42d777de9f01fa95269550
firmware_verified=1
PERMIT
# The complete permit is outside the canonical live root, so it must stop before sysfs or module commands.
reject "$root/full" "$(sha256sum "$root/full" | cut -d' ' -f1)"
# Exercise the exact heartbeat function in a separate shell; replace only the stop callback, never sysfs.
awk '/^require_heartbeat\(\)/,/^\}/' "$script" > "$root/heartbeat-function.sh"
cat > "$root/heartbeat-cases.sh" <<'CASES'
set -Eeuo pipefail
root=$1;cd "$root";declare -A p=();p[deadline_epoch_ms]=$(($(date +%s%3N)+20000));permit="$root/full";expected=$(sha256sum "$permit" | cut -d' ' -f1)
source "$root/heartbeat-function.sh"
check_reject(){ if require_heartbeat;then echo 'Heartbeat unsafe' >&2;exit 1;fi; }
check_reject
printf 0 > host-heartbeat;check_reject
printf 999999999999999 > host-heartbeat;check_reject
now=$(awk '{printf "%.0f",$1*1000}' /proc/uptime);printf '%s' "$now" > host-heartbeat;require_heartbeat
: > STOP_RUNTIME;check_reject;rm STOP_RUNTIME
: > WATCHDOG_STOP;check_reject;rm WATCHDOG_STOP
p[deadline_epoch_ms]=0;check_reject
CASES
if [[ -r /proc/uptime ]];then bash "$root/heartbeat-cases.sh" "$root";checks=$((checks+7));else echo 'Heartbeat real /proc clock cases NOT RUN on this shell';fi
awk '/^publish_runtime_terminal\(\)/,/^\}/' "$script" > "$root/terminal-function.sh"
(
 cd "$root";source "$root/terminal-function.sh";phase=cold;result=7;cleanup_ok=1
 publish_runtime_terminal
 grep -q '"result":7,"cleanup_confirmed":true' runtime-terminal.json
 [[ ! -e runtime-terminal.tmp ]]
 phase=operational;result=0;cleanup_ok=0;publish_runtime_terminal
 grep -q '"phase":"operational","result":0,"cleanup_confirmed":false' runtime-terminal.json
 [[ ! -e runtime-terminal.tmp ]]
)
checks=$((checks+4))
printf 'PASS %s runtime admission/heartbeat/atomic-terminal checks; no device or module commands.\n' "$checks"
