#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-only
set -eu
case "${1-}" in
    ''|--compile-only) ;;
    *) printf '%s\n' 'Cách dùng: sh tests/Test-ReceiveOriginalK36.sh [--compile-only]' >&2; exit 2 ;;
esac
[ "$#" -le 1 ] || exit 2
[ "$(uname -s)" = Linux ] || { printf '%s\n' 'Cần Linux và Linux DVB UAPI headers; không dùng bản mô hình Windows.' >&2; exit 2; }
cc=${CC:-cc}
command -v "$cc" >/dev/null 2>&1 || { printf '%s\n' 'Không tìm thấy C compiler; không tự cài package.' >&2; exit 2; }
root=$(CDPATH= cd -P "$(dirname "$0")/.." && pwd -P)
work="$root/tests/.posix-test-$$"
umask 077
mkdir "$work"
cleanup() {
    rm -f "$work/ReceiveOriginalK36" "$work/receiver-fake" "$work/fake.stdout" "$work/fake.stderr"
    rmdir "$work"
}
trap cleanup 0
trap 'exit 130' INT
trap 'exit 143' TERM
"$cc" -std=c11 -O2 -Wall -Wextra -Werror -pedantic "$root/ReceiveOriginalK36.c" -o "$work/ReceiveOriginalK36"
printf '%s\n' 'PASS production compile/link với Linux DVB headers; không chạy binary production.'
[ "${1-}" != --compile-only ] || exit 0
"$cc" -std=c11 -O2 -Wall -Wextra -Werror -pedantic "$root/tests/Test-ReceiveOriginalK36.c" -o "$work/receiver-fake"
"$work/receiver-fake" --selftest
if "$work/receiver-fake" >"$work/fake.stdout" 2>"$work/fake.stderr"; then
    cat "$work/fake.stdout"
else
    cat "$work/fake.stderr" >&2
    exit 1
fi
printf '%s\n' 'PASS kiểm thử giả lập với Linux DVB UAPI; không mở thiết bị hoặc gọi phần cứng.'
