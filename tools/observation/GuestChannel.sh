#!/bin/bash
# SPDX-License-Identifier: GPL-2.0-only
set -Eeuo pipefail
umask 077
[[ $# == 4 ]] || exit 64
root=$1; boot=$2; deadline=$3; fixture=$4
[[ $fixture == 0 || $fixture == 1 ]] || exit 64
[[ -d "$root" && ! -L "$root" && $(realpath -e "$root") == "$root" && $deadline =~ ^[0-9]+$ ]] || exit 64
if [[ $fixture == 0 ]];then
 [[ $root =~ ^/root/phase31-layered-[0-9]{8}-[0-9]{6}-[a-f0-9]{8}/(cold|operational)$ && $(id -u) == 0 && $(cat /proc/sys/kernel/random/boot_id) == "$boot" ]] || exit 65
else
 [[ $root == */tbs-channel-fixture-* && "$root" != /root/* ]] || exit 65
fi
[[ -v EPOCHREALTIME ]] || exit 65
wall_ms(){ local stamp=$EPOCHREALTIME fraction;fraction=${stamp#*.};printf '%s%s' "${stamp%.*}" "${fraction:0:3}"; }
[[ $(wall_ms) -lt $deadline ]] || exit 65
cd "$root"
now_ms(){ local uptime ignored;read -r uptime ignored < /proc/uptime || return 1;[[ $uptime =~ ^([0-9]+)\.([0-9]{2})$ ]] || return 1;printf '%s' "$((10#${BASH_REMATCH[1]}*1000+10#${BASH_REMATCH[2]}*10))"; }
stop_runtime(){ : > STOP_RUNTIME;if [[ $fixture == 0 && -w /sys/module/tbs5530_minimal/parameters/stop ]];then printf 1 > /sys/module/tbs5530_minimal/parameters/stop;fi; }
stop_observer(){ : > STOP_OBSERVER; }
trap 'stop_runtime;stop_observer' EXIT
last=$(now_ms);sequence=0
printf '{"protocol":"TBS5530-GUEST-CHANNEL/v1","ready":true,"fixture":%s}\n' "$fixture"
while :;do
 now=$(now_ms)
 if [[ $(wall_ms) -ge $deadline || $now -lt $last || $((now-last)) -ge 2000 ]];then exit 1;fi
 # A short polling read can discard a line split across SSH packets.
 IFS=' ' read -r -t 1.5 id command extra || exit 1
 now=$(now_ms)
 [[ $(wall_ms) -lt $deadline && $now -ge $last && $((now-last)) -lt 2000 ]] || exit 1
 [[ $id =~ ^[1-9][0-9]*$ && $id -eq $((sequence+1)) && -z ${extra-} ]] || exit 64
 case "$command" in PULSE|STOP_RUNTIME|STOP_OBSERVER|DRAIN|RESULT|IDENTITY|CLOSE) ;; *) exit 64;; esac
 sequence=$id;last=$(now_ms)
 if [[ $command == CLOSE ]];then stop_runtime;stop_observer;printf '{"sequence":%s,"closed":true}\n' "$id";exit 0;fi
 printf '%s' "$last" > host-heartbeat.tmp;mv host-heartbeat.tmp host-heartbeat
 printf '%s' "$last" > observer-heartbeat.tmp;mv observer-heartbeat.tmp observer-heartbeat
 [[ $command != STOP_RUNTIME ]] || stop_runtime
 [[ $command != STOP_OBSERVER ]] || stop_observer
 health=null;terminal=null;observer_terminal=null;idle=true
 if [[ -f observer/health.json ]];then health=$(<observer/health.json);health=${health//$'\r'/};health=${health//$'\n'/};fi
 if [[ -f runtime-terminal.json ]];then terminal=$(<runtime-terminal.json);terminal=${terminal//$'\r'/};terminal=${terminal//$'\n'/};fi
 if [[ -f observer/terminal.json ]];then observer_terminal=$(<observer/terminal.json);observer_terminal=${observer_terminal//$'\r'/};observer_terminal=${observer_terminal//$'\n'/};fi
 if [[ $fixture == 0 ]];then for m in tbs5530_minimal cxd2878 alp;do [[ ! -d /sys/module/$m ]] || idle=false;done;fi
 target='';target_count=0
 if [[ $command == IDENTITY && $fixture == 0 ]];then
  for p in /sys/bus/usb/devices/*;do
   if [[ -f "$p/idVendor" && $(cat "$p/idVendor") == 734c && $(cat "$p/idProduct") == 5530 ]];then
    target_count=$((target_count+1));bound=UNBOUND;[[ ! -e "$p:1.0/driver" && ! -L "$p:1.0/driver" ]] || bound=BOUND
    target="TARGET|${p##*/}|$(cat "$p/busnum")|$(cat "$p/devnum")|$(cat "$p/speed")|$(sha256sum "$p/descriptors" | cut -d' ' -f1)|$bound"
   fi
  done
 fi
 [[ $target_count -lt 2 ]] || exit 65
 now=$(now_ms)
 printf '{"sequence":%s,"guest_now_ms":%s,"health":%s,"runtime_terminal":%s,"observer_terminal":%s,"modules_idle":%s,"target_line":"%s"}\n' "$id" "$now" "$health" "$terminal" "$observer_terminal" "$idle" "$target"
done
