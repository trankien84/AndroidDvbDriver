"""Offline AUTOINLEN proofs. Firmware input is external and hash locked; no USB I/O."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from gate_b_static import Machine, decode, linear, sha256, verify_image

REGISTERS = (0xE620, 0xE621)


def literal_writers(code: bytes) -> dict:
    """Over-approximate literal DPTR references, never label unknown A as a constant."""
    references, writes, unresolved = [], [], []
    for start in range(len(code) - 2):
        if code[start] != 0x90:
            continue
        address = int.from_bytes(code[start + 1:start + 3], "big")
        if address not in REGISTERS:
            continue
        references.append(start)
        pc, acc, dptr = start + 3, None, address
        for _ in range(32):
            if pc >= len(code):
                break
            item = decode(code, pc)
            op = code[pc]
            if op == 0xF0:
                if dptr in REGISTERS:
                    writes.append(dict(reference=start, offset=pc, address=dptr, value=acc))
            elif op == 0x74:
                acc = code[pc + 1]
            elif op == 0xE4:
                acc = 0
            elif op == 0x04:
                acc = None if acc is None else (acc + 1) & 255
            elif op == 0x14:
                acc = None if acc is None else (acc - 1) & 255
            elif op == 0xA3:
                dptr = (dptr + 1) & 65535
            elif op == 0x90:
                break
            elif op not in (0, 0xF0):
                unresolved.append(dict(offset=pc, reference=start, reason="block ends or unsupported effect"))
                break
            pc += item["length"]
    unique = {(w["offset"], w["address"], w["value"]): w for w in writes}
    return dict(references=references, writes=list(unique.values()), boundaries=unresolved,
                coverage="All byte offsets scanned for MOV DPTR,#E620/E621; not a proof against dynamically constructed DPTR or MOVX @Ri")


def execute_standard(code: bytes, request: int, value: int) -> Machine:
    machine = Machine(code, 0x02AB, {0xE6B9: request, 0xE6BA: value})
    machine.run(stop=0x04C7)
    if machine.pc != 0x04C7:
        raise ValueError("standard dispatcher did not reach status path")
    return machine


def analyze_autoinlen(code: bytes) -> dict:
    verify_image(code)
    init = Machine(code, 0x04CF, {0xE67A: 0xAA})
    init.run()
    init_writes = [w for w in init.writes if w.get("space") is None and w["address"] in REGISTERS]
    for address, value in ((0xE60B, 3), (0xE601, 0xCB), (0xE618, 9), (0xE612, 0xE8)):
        if [(w["address"], w["value"]) for w in init.writes if w.get("space") is None and w["address"] == address] != [(address, value)]:
            raise ValueError("init register model mismatch")
    if [w["value"] for w in init.writes if w.get("space") is None and w["address"] == 0xE604] != [0x80, 2, 4, 6, 8, 0]:
        raise ValueError("FIFO reset sequence mismatch")
    if code[0x0877:0x087A] != bytes.fromhex("12 04 cf"):
        raise ValueError("startup init call mismatch")
    inventory = literal_writers(code)
    standard = {}
    for request, entry, handler, iram in ((9, 0x0350, 0x0DCA, 0x2D), (11, 0x034A, 0x0E77, 0x2B)):
        samples = []
        for value in range(256):
            machine = execute_standard(code, request, value)
            if entry not in machine.trace or handler not in machine.trace or machine.direct[iram] != value:
                raise ValueError("standard request path/value proof failed")
            target_writes = [w for w in machine.writes if w.get("space") is None and w["address"] in REGISTERS]
            expected = [(0xE620, 4), (0xE621, 0)] if request == 9 else []
            if [(w["address"], w["value"]) for w in target_writes] != expected:
                raise ValueError("unexpected AUTOINLEN writes in standard request")
            if value == 1:
                samples.append(dict(trace=machine.trace, writes=machine.writes))
        standard[str(request)] = dict(status="PASS", values_tested=256, entry=entry, handler=handler, samples=samples)
    renum = []
    for usbcs in (0, 2):
        machine = Machine(code, 0x08D5, {0xE680: usbcs})
        stop = 0x0C7D if usbcs == 0 else 0x08E1
        machine.run(stop=stop)
        if machine.pc != stop or (usbcs == 0 and not machine.bit(9)):
            raise ValueError("RENUM branch proof failed")
        renum.append(dict(usbcs=usbcs, stop=stop, trace=machine.trace, iram_21_bit1=machine.bit(9)))
    expected = {(w["offset"], w["address"], w["value"]) for w in init_writes}
    expected.update((w["offset"], w["address"], w["value"]) for w in standard["9"]["samples"][0]["writes"] if w.get("space") is None)
    concrete = {(offset, address): value for offset, address, value in expected}
    for writer in inventory["writes"]:
        key = (writer["offset"], writer["address"])
        if writer["value"] is None and key in concrete:
            writer["value"] = concrete[key]
            writer["value_provenance"] = "bounded executor from routine entry"
    actual = {(w["offset"], w["address"], w["value"]) for w in inventory["writes"]}
    if actual != expected:
        raise ValueError("literal writer inventory has additional or missing writers")
    if [(w["address"], w["value"]) for w in init_writes] != [(0xE620, 2), (0xE621, 0)]:
        raise ValueError("init AUTOINLEN differs from 512")
    return dict(image_sha256=sha256(code), image_bytes=len(code), status="FIRMWARE_STATIC_CONFIRMED",
                init_writes=init.writes, literal_inventory=inventory, standard_requests=standard, renum=renum,
                listings={name: linear(code, start, end) for name, start, end in
                          (("set_configuration", 0x0DCA, 0x0DE3), ("set_interface", 0x0E77, 0x0E7F), ("renum_branch", 0x08D5, 0x08E1))},
                global_dynamic_writer_exclusion="UNKNOWN", hardware_causality="NOT_HARDWARE_CONFIRMED")


def markdown(result: dict) -> str:
    rows = ["# Kiểm chứng tĩnh H-AUTOINLEN", "", f"Ảnh ngoài Git: {result['image_bytes']} byte, SHA-256 `{result['image_sha256']}`. Không I/O thiết bị.", "",
            "| Nhận định | Kết quả | Bằng chứng |", "| --- | --- | --- |",
            "| Init đặt 512 byte | PASS | Caller 0877; executor từ 04CF kiểm REVCTL=03, FIFO reset 80/02/04/06/08/00, IFCONFIG=CB, EP2FIFOCFG=09, EP2CFG=E8; mọi write trong JSON |",
            "| bRequest 9 → 0350 → 0DCA, ghi 0400 | PASS | 256 giá trị wValue low byte; dừng trước status path 04C7 |",
            "| bRequest 11 → 034A → 0E77 không ghi AUTOINLEN | PASS | 256 giá trị, lưu 2B |",
            "| USBCS.RENUM chọn bỏ qua lời gọi 0C7D | PASS | Nhánh RENUM=0 và RENUM=1; chưa mô phỏng USB re-enumeration thật |",
            "| Inventory mọi literal DPTR E620/E621 | PASS | Quét mọi byte offset, không chỉ init |",
            "| Loại trừ mọi địa chỉ DPTR động / MOVX @Ri | UNKNOWN | Không có chứng minh toàn bộ call graph; không dùng linear sweep làm proof hoàn chỉnh |", "",
            "## Các lệnh ghi tìm thấy", "", "| MOVX offset | Địa chỉ | Giá trị A |", "| --- | --- | --- |"]
    for item in result["literal_inventory"]["writes"]:
        value = "UNKNOWN" if item["value"] is None else f"{item['value']:02X}"
        rows.append(f"| {item['offset']:04X} | {item['address']:04X} | {value} |")
    rows += ["", "Kết luận: `FIRMWARE_STATIC_CONFIRMED` cho các đường đã kiểm; `NOT_HARDWARE_CONFIRMED` cho nguyên nhân babble/-75. Việc RENUM giữ nguyên sau RAM reload trên Android vẫn là giả thuyết cần kiểm live có duyệt.", ""]
    return "\n".join(rows)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--firmware", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    result = analyze_autoinlen(args.firmware.read_bytes())
    paths = [args.output_dir / f"autoinlen-static-report.{ext}" for ext in ("json", "md")]
    if any(path.exists() for path in paths):
        raise ValueError("report already exists")
    for path, text in zip(paths, (json.dumps(result, ensure_ascii=False, indent=2) + "\n", markdown(result))):
        with path.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(text)
    print("PASS: bounded paths; global dynamic writer exclusion UNKNOWN")


if __name__ == "__main__":
    main()
