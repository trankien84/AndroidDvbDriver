#!/bin/bash
# SPDX-License-Identifier: GPL-2.0-only
set -eu
cd -- "$(dirname -- "$0")"
root=$(pwd -P)
case "$root" in /root/phase31-*|/home/*/phase31-*) ;; *) echo 'Refuse non-isolated scratch path'; exit 2;; esac
b="/lib/modules/$(uname -r)/build"
for p in Makefile Module.symvers include/generated/autoconf.h scripts/Makefile.build; do test -f "$b/$p"; done
cat Makefile
sha256sum "$b/Makefile" "$b/Module.symvers" "$b/include/generated/autoconf.h" > headers-before.sha256
gcc -std=c99 -Wall -Wextra -Werror -I. guard-test.c -o guard-test
./guard-test
gcc -std=c99 -Wall -Wextra -Werror -I. operational-test.c -o operational-test
./operational-test
perl -0777 -ne 'if (/(static int tbs5530_control_locked.*?)static u32 tbs5530_i2c_func/s) { print $1 } else { die "Missing control extraction boundary" }' tbs5530_runtime_io.h > control-under-test.inc
gcc -std=c99 -Wall -Wextra -Werror -Wno-unused-function -I. control-path-test.c -o control-path-test
./control-path-test
perl -0777 -ne 'if (/(static int tbs5530_slot.*?)static void tbs5530_drain\(/s) { print $1 } else { die "Missing receive extraction boundary" }' tbs5530_runtime_io.h > receive-under-test.inc
gcc -std=c99 -Wall -Wextra -Werror -Wno-unused-function -I. empty-credit-test.c -o empty-credit-test
./empty-credit-test
timeout 180s make -C "$b" "M=$root" -j2 modules
sha256sum -c headers-before.sha256
for f in ./*.ko; do modinfo "$f"; nm -u "$f"; done
echo 'COMPILE_ONLY: no install/load/USB/snapshot. CRC verification required separately.'
