#!/bin/bash
# SPDX-License-Identifier: GPL-2.0-only
set -eu
root=$(cd -- "$(dirname -- "$0")/.." && pwd -P)
case "$root" in /root/phase31-*|/home/*/phase31-*) ;; *) echo 'Tests require isolated guest scratch'; exit 2;; esac
work=$(mktemp -d "$root/.receiver-test-XXXXXX")
trap 'rm -rf -- "$work"' EXIT
wrap='open close fsync ioctl clock_gettime nanosleep poll read write'
args=()
for symbol in $wrap; do args+=("-Wl,--wrap=$symbol"); done
gcc -std=c11 -Wall -Wextra -Werror -DTBS_SELF_TEST "$root/ReceiveK36.c" -o "$work/policy"
"$work/policy"
gcc -std=c11 -Wall -Wextra -Werror "$root/ReceiveK36.c" "$root/tests/receiver-fake.c" "${args[@]}" -o "$work/fake"
for scenario in 0 1 2 3 4; do
 set +e
 TBS_FAKE_CASE=$scenario "$work/fake" 0 synthetic.ts >"$work/$scenario.stdout" 2>"$work/$scenario.stderr"
 result=$?
 set -e
 if [[ "$scenario" == 0 ]]; then test "$result" == 0; else test "$result" == 1; fi
 grep -q "FAKE_ONLY_RESULT scenario=$scenario" "$work/$scenario.stderr"
 cat "$work/$scenario.stderr"
done
test ! -e synthetic.ts
printf 'PASS 5 fake-device flows: success, wrong identity, no lock, poll fault, EOVERFLOW; no physical I/O\n'
