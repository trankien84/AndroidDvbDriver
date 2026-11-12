"""Bounded, dependency-free FX2 firmware analysis. No USB, subprocess or network I/O."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path
import struct

IMAGE_LENGTH = 8192
IMAGE_SHA256 = "b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify_image(data: bytes, expected_hash: str = IMAGE_SHA256) -> None:
    if len(data) != IMAGE_LENGTH:
        raise ValueError("firmware length must be 8192")
    if sha256(data) != expected_hash:
        raise ValueError("firmware SHA-256 mismatch")


def bit_address(address: int) -> dict:
    if not 0 <= address <= 255:
        raise ValueError("bit address outside MCS51 range")
    return {"space": "iram" if address < 128 else "sfr",
            "byte": 0x20 + address // 8 if address < 128 else address & 0xF8,
            "bit": address & 7}


def setup_packet(data: bytes) -> tuple:
    if len(data) != 8:
        raise ValueError("setup packet must have eight bytes")
    return struct.unpack("<BBHHH", data)


def reconstruct_epoch(transfers: list[dict], expected_hash: str = IMAGE_SHA256) -> bytes:
    if len(transfers) != 132:
        raise ValueError("epoch must have exactly 132 A0 transfers")
    expected_resets = [(0, 0x7F92, 1), (1, 0xE600, 1),
                       (130, 0x7F92, 0), (131, 0xE600, 0)]
    for i, address, value in expected_resets:
        if transfers[i]["address"] != address or transfers[i]["data"] != bytes([value]):
            raise ValueError("dual-reset epoch boundary/order mismatch")
    image = bytearray(IMAGE_LENGTH)
    coverage = bytearray(IMAGE_LENGTH)
    owner = transfers[0]["owner"]
    for i, transfer in enumerate(transfers):
        if transfer["owner"] != owner or transfer["status"] != 0:
            raise ValueError("mixed owner/epoch or failed completion")
        if transfer["request_type"] != 0x40 or transfer["index"] != 0:
            raise ValueError("A0 direction/index mismatch")
        data = transfer["data"]
        if len(data) != transfer["length"]:
            raise ValueError("truncated A0 payload")
        if i in (0, 1, 130, 131):
            continue
        start = transfer["address"]
        if len(data) != 64 or start < 0 or start + len(data) > IMAGE_LENGTH:
            raise ValueError("image transfer length/address outside image")
        if start != (i - 2) * 64:
            raise ValueError("image coverage/order/overlap mismatch")
        for j, value in enumerate(data, start):
            if coverage[j]:
                raise ValueError("overlapping A0 write")
            coverage[j] = 1
            image[j] = value
    if any(value != 1 for value in coverage):
        raise ValueError("incomplete A0 coverage")
    verify_image(image, expected_hash)
    return bytes(image)


def a0_epochs(csv_path: Path, expected_hash: str = IMAGE_SHA256) -> list[dict]:
    pending = {}
    completed = []
    previous_frame = -1
    with csv_path.open(encoding="utf-8-sig", newline="") as stream:
        for row in csv.DictReader(stream):
            frame = int(row["frame.number"])
            if frame <= previous_frame:
                raise ValueError("frames not strictly increasing")
            previous_frame = frame
            if row["frame.len"] != row["frame.cap_len"]:
                raise ValueError("truncated capture frame")
            if int(row["usb.endpoint_address"], 0) != 0:
                raise ValueError("non-EP0 record in control input")
            owner = (row["usb.bus_id"], row["usb.device_address"])
            key = owner + (row["usb.irp_id"],)
            direction = int(row["usb.irp_info"], 0) & 1
            if not direction and key in pending:
                raise ValueError("overlapping IRP submit")
            if not direction and row["usb.setup.bRequest"] == "160":
                if row["usb.control_stage"] != "0":
                    raise ValueError("A0 submit not setup stage")
                if int(row["usb.usbd_status"], 0) != 0:
                    raise ValueError("A0 submit status is not success")
                pending[key] = {"frame": frame, "address": int(row["usb.setup.wValue"], 0),
                                "index": int(row["usb.setup.wIndex"], 0),
                                "request_type": int(row["usb.bmRequestType"], 0),
                                "length": int(row["usb.setup.wLength"], 0),
                                "data": bytes.fromhex(row["usb.data_fragment"].replace(":", "")),
                                "owner": owner}
            elif direction and key in pending:
                if row["usb.control_stage"] != "3":
                    raise ValueError("A0 completion not status stage")
                transfer = pending.pop(key)
                transfer.update(status=int(row["usb.usbd_status"], 0), completion_frame=frame)
                completed.append(transfer)
    if pending:
        raise ValueError("missing A0 completion")
    epochs = []
    current = []
    for transfer in completed:
        if transfer["address"] == 0x7F92 and transfer["data"] == b"\x01":
            if current:
                raise ValueError("new epoch before previous release")
        elif not current:
            raise ValueError("A0 transfer outside a reset epoch")
        current.append(transfer)
        if transfer["address"] == 0xE600 and transfer["data"] == b"\x00":
            image = reconstruct_epoch(current, expected_hash)
            epochs.append({"epoch": len(epochs) + 1, "first_frame": current[0]["frame"],
                           "last_frame": current[-1]["completion_frame"], "transfer_count": len(current),
                           "image_bytes": len(image), "coverage_min": 1, "coverage_max": 1,
                           "sha256": sha256(image), "status": "OFFLINE-TESTED",
                           "completion_semantics": "USBPcap status stage USBD_STATUS=0; not RAM readback"})
            current = []
    if current or not epochs:
        raise ValueError("incomplete or absent epoch")
    return epochs


_LENGTH_ROWS = (
    "1231121111111111", "3231121111111111", "3211221111111111", "3211221111111111",
    "2223221111111111", "2223221111111111", "2223221111111111", "2221232222222222",
    "2221132222222222", "3221221111111111", "2221112222222222", "2221333333333333",
    "2221121111111111", "2221131122222222", "1211121111111111", "1211121111111111",
)


def decode(data: bytes, pc: int) -> dict:
    if not 0 <= pc < len(data):
        raise ValueError("PC outside image")
    op = data[pc]
    length = int(_LENGTH_ROWS[op >> 4][op & 15])
    if pc + length > len(data):
        raise ValueError("truncated instruction")
    b = data[pc:pc + length]
    text = f"OP_{op:02X} " + b[1:].hex(" ")
    target = None
    flow = "next"
    if op & 0x1F in (1, 0x11):
        target = ((pc + 2) & 0xF800) | ((op & 0xE0) << 3) | b[1]
        flow = "call" if op & 0x1F == 0x11 else "jump"
        text = f"{'ACALL' if flow == 'call' else 'AJMP'} {target:04X}"
    elif op in (2, 0x12):
        target = int.from_bytes(b[1:3], "big")
        flow = "call" if op == 0x12 else "jump"
        text = f"{'LCALL' if flow == 'call' else 'LJMP'} {target:04X}"
    elif op in (0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80, 0xD5) or 0xB4 <= op <= 0xBF or 0xD8 <= op <= 0xDF:
        target = (pc + length + int.from_bytes(b[-1:], "big", signed=True)) & 0xFFFF
        flow = "jump" if op == 0x80 else "branch"
        names = {0x10: "JBC", 0x20: "JB", 0x30: "JNB", 0x40: "JC", 0x50: "JNC", 0x60: "JZ", 0x70: "JNZ", 0x80: "SJMP", 0xD5: "DJNZ"}
        name = names.get(op, "CJNE" if op < 0xD0 else "DJNZ")
        text = f"{name} " + (b[1:-1].hex(" ") + "," if length > 2 else "") + f"{target:04X}"
    elif op in (0x22, 0x32, 0x73):
        flow = "return" if op != 0x73 else "computed"
        text = {0x22: "RET", 0x32: "RETI", 0x73: "JMP @A+DPTR"}[op]
    elif op == 0x90:
        text = f"MOV DPTR,#{int.from_bytes(b[1:], 'big'):04X}"
    elif op in (0xA2, 0x92, 0xC2, 0xD2, 0xB2):
        text = {0xA2: f"MOV C,bit {b[1]:02X}", 0x92: f"MOV bit {b[1]:02X},C", 0xC2: f"CLR bit {b[1]:02X}", 0xD2: f"SETB bit {b[1]:02X}", 0xB2: f"CPL bit {b[1]:02X}"}[op]
    elif op in (0x74, 0x75, 0x85, 0xE5, 0xF5):
        text = {0x74: lambda: f"MOV A,#{b[1]:02X}", 0x75: lambda: f"MOV direct {b[1]:02X},#{b[2]:02X}", 0x85: lambda: f"MOV direct {b[2]:02X},direct {b[1]:02X}", 0xE5: lambda: f"MOV A,direct {b[1]:02X}", 0xF5: lambda: f"MOV direct {b[1]:02X},A"}[op]()
    elif op in (0x24, 0x34, 0x44, 0x54, 0x94):
        text = f"{ {0x24:'ADD',0x34:'ADDC',0x44:'ORL',0x54:'ANL',0x94:'SUBB'}[op]} A,#{b[1]:02X}"
    elif op in (0x43, 0x53):
        text = f"{'ORL' if op == 0x43 else 'ANL'} direct {b[1]:02X},#{b[2]:02X}"
    elif op in (0x25, 0x35, 0x65, 0xC5):
        text = f"{ {0x25:'ADD A',0x35:'ADDC A',0x65:'XRL A',0xC5:'XCH A'}[op]},direct {b[1]:02X}"
    elif op in (0xC0, 0xD0, 0x05, 0x15):
        text = f"{ {0xC0:'PUSH',0xD0:'POP',0x05:'INC',0x15:'DEC'}[op]} direct {b[1]:02X}"
    elif op in (0, 4, 0x14, 0x33, 0x93, 0xA3, 0xA4, 0xC3, 0xD3, 0xE0, 0xE4, 0xF0):
        text = {0:"NOP",4:"INC A",0x14:"DEC A",0x33:"RLC A",0x93:"MOVC A,@A+DPTR",0xA3:"INC DPTR",0xA4:"MUL AB",0xC3:"CLR C",0xD3:"SETB C",0xE0:"MOVX A,@DPTR",0xE4:"CLR A",0xF0:"MOVX @DPTR,A"}[op]
    elif op == 0x4E:
        text = "ORL A,R6"
    elif 0xA8 <= op <= 0xAF:
        text = f"MOV R{op & 7},direct {b[1]:02X}"
    elif 0x78 <= op <= 0x7F:
        text = f"MOV R{op & 7},#{b[1]:02X}"
    elif 0xE8 <= op <= 0xEF:
        text = f"MOV A,R{op & 7}"
    elif 0xF8 <= op <= 0xFF:
        text = f"MOV R{op & 7},A"
    return {"offset": pc, "hex": b.hex(" ").upper(), "length": length, "text": text, "flow": flow, "target": target}


def linear(data: bytes, start: int, end: int) -> list[dict]:
    result = []
    pc = start
    while pc < end:
        instruction = decode(data, pc)
        if pc + instruction["length"] > end:
            raise ValueError(f"block boundary cuts instruction at {pc:04X}")
        result.append(instruction)
        pc += instruction["length"]
    return result


def vendor_table(data: bytes) -> dict:
    if data[0x56:0x5D] != bytes.fromhex("90 e6 b9 e0 12 0a dc"):
        raise ValueError("vendor bRequest/table call signature mismatch")
    helper = "d0 83 d0 82 f8 e4 93 70 12 74 01 93 70 0d a3 a3 93 f8 74 01 93 f5 82 88 83 e4 73 74 02 93 68 60 ef a3 a3 a3 80 df"
    if data[0xADC:0xB02] != bytes.fromhex(helper):
        raise ValueError("Keil switch helper signature mismatch")
    result = {}
    pc = 0x5D
    while pc + 4 <= len(data):
        address = int.from_bytes(data[pc:pc + 2], "big")
        if address == 0:
            result["default"] = int.from_bytes(data[pc + 2:pc + 4], "big")
            return result
        request = data[pc + 2]
        if str(request) in result or address >= len(data):
            raise ValueError("invalid vendor table")
        result[str(request)] = address
        pc += 3
        if pc >= 0x83:
            raise ValueError("unterminated vendor table")
    raise ValueError("truncated vendor table")


class Machine:
    """Fail-closed concrete executor for only the instructions used in bounded proofs."""
    def __init__(self, code: bytes, pc: int, xdata: dict | None = None):
        self.code, self.pc = code, pc
        self.direct = bytearray(256)
        self.xdata = dict(xdata or {})
        self.carry = 0
        self.trace = []
        self.writes = []
        self.calls = []

    @property
    def acc(self):
        return self.direct[0xE0]

    @acc.setter
    def acc(self, value):
        self.direct[0xE0] = value & 255

    @property
    def dptr(self):
        return self.direct[0x82] | self.direct[0x83] << 8

    def bit(self, address):
        mapping = bit_address(address)
        return (self.direct[mapping["byte"]] >> mapping["bit"]) & 1

    def set_bit(self, address, value):
        mapping = bit_address(address)
        byte, bit = mapping["byte"], mapping["bit"]
        self.direct[byte] = (self.direct[byte] & ~(1 << bit)) | ((value & 1) << bit)

    def run(self, limit=300, stop=None):
        for _ in range(limit):
            if self.pc == stop:
                return
            item = decode(self.code, self.pc)
            self.trace.append(item["offset"])
            b = bytes.fromhex(item["hex"])
            op = b[0]
            self.pc += len(b)
            if op == 0x22:
                if self.calls:
                    self.pc = self.calls.pop()
                    continue
                return
            if op == 0:
                pass
            elif op == 0x12:
                self.calls.append(self.pc)
                self.pc = item["target"]
            elif op == 0x33:
                value = (self.acc << 1) | self.carry
                self.carry = (value >> 8) & 1
                self.acc = value
            elif op == 0x05:
                self.direct[b[1]] = (self.direct[b[1]] + 1) & 255
            elif op == 0xE5:
                self.acc = self.direct[b[1]]
            elif op == 0xAE:
                self.direct[6] = self.direct[b[1]]
            elif op == 0x4E:
                self.acc |= self.direct[6]
            elif op in (0x60, 0x70):
                if (op == 0x60 and self.acc == 0) or (op == 0x70 and self.acc != 0):
                    self.pc = item["target"]
            elif op in (0xC3, 0xD3):
                self.carry = int(op == 0xD3)
            elif op == 0x30:
                if not self.bit(b[1]):
                    self.pc = item["target"]
            elif op == 0x90:
                self.direct[0x82], self.direct[0x83] = b[2], b[1]
            elif op == 0xE0:
                if self.dptr not in self.xdata:
                    raise ValueError(f"unknown xdata read {self.dptr:04X}")
                self.acc = self.xdata[self.dptr]
            elif op == 0xF0:
                self.xdata[self.dptr] = self.acc
                self.writes.append({"offset": item["offset"], "address": self.dptr, "value": self.acc})
            elif op == 0xE4:
                self.acc = 0
            elif op == 0x74:
                self.acc = b[1]
            elif op == 0x75:
                self.direct[b[1]] = b[2]
            elif op == 0xF5:
                self.direct[b[1]] = self.acc
                self.writes.append({"offset": item["offset"], "space": "direct", "address": b[1], "value": self.acc})
            elif op == 0x24:
                value = self.acc + b[1]
                self.carry = int(value > 255)
                self.acc = value
            elif op == 0x04:
                self.acc += 1
            elif op == 0x14:
                self.acc -= 1
            elif op == 0xA2:
                self.carry = self.bit(b[1])
            elif op == 0x92:
                self.set_bit(b[1], self.carry)
                self.writes.append({"offset": item["offset"], "space": "bit", "address": b[1], "value": self.carry, "mapping": bit_address(b[1])})
            elif op == 0xD2:
                self.set_bit(b[1], 1)
            elif op == 0xC2:
                self.set_bit(b[1], 0)
            elif op == 0xB4:
                self.carry = int(self.acc < b[1])
                if self.acc != b[1]:
                    self.pc = item["target"]
            elif op == 0x50:
                if not self.carry:
                    self.pc = item["target"]
            elif op == 0x20:
                if self.bit(b[1]):
                    self.pc = item["target"]
            elif op in (0x02, 0x80):
                self.pc = item["target"]
            elif op == 0xA4:
                value = self.acc * self.direct[0xF0]
                self.acc, self.direct[0xF0] = value & 255, value >> 8
                self.carry = 0
            elif op == 0xC5:
                self.acc, self.direct[b[1]] = self.direct[b[1]], self.acc
            elif op == 0x25:
                value = self.acc + self.direct[b[1]]
                self.carry = int(value > 255)
                self.acc = value
            elif op == 0x73:
                self.pc = self.dptr + self.acc
            elif op == 0x54:
                self.acc &= b[1]
            elif op == 0x44:
                self.acc |= b[1]
            elif op == 0x43:
                self.direct[b[1]] |= b[2]
                self.writes.append({"offset": item["offset"], "space": "direct", "address": b[1], "value": self.direct[b[1]]})
            else:
                raise ValueError(f"unsupported proof opcode {op:02X} at {item['offset']:04X}")
        raise ValueError("execution budget exhausted")


def gpio_effect(data: bytes, payload: bytes) -> dict:
    if len(payload) < 2:
        raise ValueError("two EP0 bytes required; short USB writes are not simulated")
    machine = Machine(data, 0x67B, {0xE740: payload[0], 0xE741: payload[1], 0xE67A: 0xAA})
    machine.run()
    return {"payload": payload.hex(), "iram_21_bit1": machine.bit(9),
            "writes": machine.writes, "trace": [f"0x{pc:04X}" for pc in machine.trace]}


def porta_function(ifconfig: int, portacfg: int, pin: int) -> str:
    if pin == 7:
        if portacfg & 0x80:
            return "FLAGD" if ifconfig & 3 == 3 else "FLAGD_UNDEFINED_OUTSIDE_SLAVE_FIFO"
        if portacfg & 0x40 and ifconfig & 3 == 3:
            return "SLCS#"
        return "GPIO_PA7"
    if pin == 6:
        return "PKTEND#" if ifconfig & 3 == 3 else "GPIO_PA6"
    raise ValueError("only PA6 and PA7 are modeled")


def analyze(data: bytes) -> dict:
    verify_image(data)
    blocks = [("vendor_entry", 0x56, 0x5D), ("vendor_81", 0x223, 0x237),
              ("vendor_8a", 0x237, 0x25F), ("vendor_b7_b8", 0x25F, 0x2AB),
              ("standard_setup", 0x2AB, 0x4CF), ("fifo_init", 0x4CF, 0x5B3),
              ("b7_work", 0x5B3, 0x67B), ("gpio_handler", 0x67B, 0x6E8),
              ("init_callers", 0x86A, 0x8FE), ("switch_helper", 0xADC, 0xB02),
              ("poll_loop", 0xB02, 0xB4F), ("reset_entry", 0xE5F, 0xE6E)]
    listing = {name: linear(data, start, end) for name, start, end in blocks}
    init = Machine(data, 0x4CF, {0xE67A: 0xAA})
    init.run()
    samples = [gpio_effect(data, bytes.fromhex(payload)) for payload in
               ("0a00", "0a01", "0400", "0401", "0402", "0000", "0b00", "0801")]
    callers = []
    for name, instructions in listing.items():
        for item in instructions:
            if item["flow"] == "call":
                callers.append({"block": name, "offset": item["offset"], "target": item["target"]})
    register_model = {
        "IFCONFIG": {"address": 0xE601, "value": 0xCB, "IFCFG": 3, "mode": "SLAVE_FIFO",
                     "IFCLKSRC": 1, "3048MHZ": 1, "internal_clock_mhz": 48,
                     "IFCLKOE": 0, "IFCLKPOL": 0, "ASYNC": 1, "GSTATE": 0},
        "EP2CFG": {"address": 0xE612, "value": 0xE8, "valid": True, "direction": "IN",
                   "type": "BULK", "physical_buffer_bytes": 1024, "buffers": 4},
        "EP2FIFOCFG": {"address": 0xE618, "value": 9, "AUTOIN": 1, "AUTOOUT": 0,
                       "ZEROLENIN": 0, "WORDWIDE": 1, "fifo_data_bus_bits": 16},
        "EP2AUTOINLEN": {"addresses": [0xE620, 0xE621], "value": 512},
        "PINFLAGSAB": {"address": 0xE602, "value": 0xC4, "FLAGA": "EP2PF", "FLAGB": "EP2FF"},
        "PINFLAGSCD": {"address": 0xE603, "value": 0x48, "FLAGC": "EP2EF", "FLAGD": "EP2PF_IF_MUX_ENABLED"},
        "FIFOPINPOLAR": {"address": 0xE609, "value": 0, "PKTEND": "ACTIVE_LOW",
                         "SLOE": "ACTIVE_LOW", "SLRD": "ACTIVE_LOW", "SLWR": "ACTIVE_LOW",
                         "EF": "ACTIVE_LOW", "FF": "ACTIVE_LOW"},
        "OEA": {"direct_address": 0xB2, "value": 0xCC, "caller_write_offset": 0x87A},
        "IOA": {"direct_address": 0x80, "value": 0xCE, "caller_write_offset": 0x87D},
        "PORTACFG": {"address": 0xE670, "reset_value": 0,
                      "write_in_bounded_init": False, "runtime_value": None,
                      "PA7_if_reset_default": porta_function(0xCB, 0, 7),
                      "PA6_if_slave_fifo": porta_function(0xCB, 0, 6)},
    }
    return {"image_length": len(data), "image_sha256": sha256(data), "vendor_table": vendor_table(data),
            "register_model": register_model,
            "bit_mappings": {f"0x{b:02X}": bit_address(b) for b in (9, 0x87, 0x86, 0xA8, 0xA5, 0xA3)},
            "initialization_writes": init.writes, "payload_effects": samples,
            "callers_in_bounded_blocks": callers, "blocks": listing,
            "confidence": "OFFLINE-TESTED bounded concrete paths; not a complete firmware emulator",
            "candidate_G": "BLOCKED_FOR_SAFETY", "hardware_replay_approved": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--firmware", required=True, type=Path)
    parser.add_argument("--ep0-csv", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = analyze(args.firmware.read_bytes())
    if args.ep0_csv:
        result["a0_epochs"] = a0_epochs(args.ep0_csv)
        result["ep0_csv_sha256"] = sha256(args.ep0_csv.read_bytes())
    with args.output.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print(f"PASS: firmware={result['image_sha256']}; blocks={len(result['blocks'])}; epochs={len(result.get('a0_epochs', []))}")


if __name__ == "__main__":
    main()
