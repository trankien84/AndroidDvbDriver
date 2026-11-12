"""SYNTHETIC_TEST_ONLY: bounded local files, never USB or firmware I/O."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import stat
import struct
from pathlib import Path
from typing import BinaryIO
import zlib

LABEL = "SYNTHETIC_TEST_ONLY"
MARKER = b"SYNTHETIC_TEST_ONLY:FX2PATTERNv1".ljust(32, b"\x00")
VERSION = 1
PACKET_SIZE = 512
MAX_TOTAL_BYTES = 1024 * 1024
MAX_PACKETS = MAX_TOTAL_BYTES // PACKET_SIZE
HEADER = struct.Struct("<32sHHIIII")
CRC = struct.Struct("<I")
CRC_OFFSET = PACKET_SIZE - CRC.size
PAYLOAD_SIZE = CRC_OFFSET - HEADER.size
FIXTURE_SUFFIX = ".synthetic-pattern"


def validate_plan(packets: int, seed: int) -> None:
    if type(packets) is not int or not 1 <= packets <= MAX_PACKETS:
        raise ValueError(f"packet count must be 1..{MAX_PACKETS}")
    if type(seed) is not int or not 0 <= seed <= 0xFFFFFFFF:
        raise ValueError("seed must be an unsigned 32-bit integer")


def _payload(sequence: int, seed: int) -> bytes:
    return bytes((seed + sequence + index) & 0xFF for index in range(PAYLOAD_SIZE))


def make_packet(sequence: int, packets: int, seed: int = 0) -> bytes:
    validate_plan(packets, seed)
    if type(sequence) is not int or not 0 <= sequence < packets:
        raise ValueError("sequence outside plan")
    body = HEADER.pack(MARKER, VERSION, PACKET_SIZE, sequence, packets, seed,
                       packets * PACKET_SIZE) + _payload(sequence, seed)
    return body + CRC.pack(zlib.crc32(body) & 0xFFFFFFFF)


def write_fixture(stream: BinaryIO, packets: int, seed: int = 0) -> dict:
    validate_plan(packets, seed)
    digest = hashlib.sha256()
    for sequence in range(packets):
        packet = make_packet(sequence, packets, seed)
        if stream.write(packet) != PACKET_SIZE:
            raise OSError("short fixture write")
        digest.update(packet)
    return _summary("SYNTHETIC_FIXTURE_GENERATED", packets, seed, digest.hexdigest())


def _summary(result: str, packets: int, seed: int, digest: str) -> dict:
    return {"label": LABEL, "result": result, "protocol_version": VERSION,
            "packet_size": PACKET_SIZE, "packets": packets, "seed": seed,
            "total_bytes": packets * PACKET_SIZE, "max_total_bytes": MAX_TOTAL_BYTES,
            "sha256": digest, "hardware_verified": False, "ts_smoke_pass": False}


def _read_packet(stream: BinaryIO) -> bytes:
    packet = bytearray()
    while len(packet) < PACKET_SIZE:
        part = stream.read(PACKET_SIZE - len(packet))
        if not part:
            raise ValueError("truncated or missing packet")
        if len(part) > PACKET_SIZE - len(packet):
            raise ValueError("reader exceeded requested length")
        packet.extend(part)
    return bytes(packet)


def verify_stream(stream: BinaryIO, expected_packets: int, expected_seed: int = 0) -> dict:
    validate_plan(expected_packets, expected_seed)
    digest = hashlib.sha256()
    for sequence in range(expected_packets):
        packet = _read_packet(stream)
        marker, version, size, actual_seq, count, seed, total = HEADER.unpack_from(packet)
        if marker != MARKER:
            raise ValueError(f"packet {sequence}: synthetic marker mismatch")
        if version != VERSION or size != PACKET_SIZE:
            raise ValueError(f"packet {sequence}: version/size mismatch")
        if actual_seq != sequence:
            raise ValueError(f"packet {sequence}: sequence mismatch")
        if (count, seed, total) != (expected_packets, expected_seed,
                                    expected_packets * PACKET_SIZE):
            raise ValueError(f"packet {sequence}: independent plan mismatch")
        stored_crc, = CRC.unpack_from(packet, CRC_OFFSET)
        if stored_crc != zlib.crc32(packet[:CRC_OFFSET]) & 0xFFFFFFFF:
            raise ValueError(f"packet {sequence}: CRC mismatch")
        if packet[HEADER.size:CRC_OFFSET] != _payload(sequence, expected_seed):
            raise ValueError(f"packet {sequence}: payload mismatch")
        digest.update(packet)
    if stream.read(1):
        raise ValueError("trailing bytes or total cap exceeded")
    return _summary("SYNTHETIC_PATTERN_VERIFIED", expected_packets, expected_seed,
                    digest.hexdigest())


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    generate = commands.add_parser("generate", help="write a new labelled local fixture")
    generate.add_argument("--output", type=Path, required=True)
    generate.add_argument("--packets", type=int, default=8)
    generate.add_argument("--seed", type=int, default=0)
    verify = commands.add_parser("verify", help="verify a regular local file, no resync")
    verify.add_argument("--input", type=Path, required=True)
    verify.add_argument("--expected-packets", type=int, required=True)
    verify.add_argument("--seed", type=int, default=0)
    args = parser.parse_args(argv)
    try:
        if args.command == "generate":
            validate_plan(args.packets, args.seed)
            if args.output.suffix != FIXTURE_SUFFIX:
                raise ValueError(f"fixture name must end in {FIXTURE_SUFFIX}")
            if args.output.is_reserved():
                raise ValueError("output must not be a reserved device name")
            with args.output.open("xb") as stream:
                if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
                    raise ValueError("output must be a regular local file")
                result = write_fixture(stream, args.packets, args.seed)
        else:
            validate_plan(args.expected_packets, args.seed)
            if args.input.is_symlink() or not args.input.is_file():
                raise ValueError("input must be a regular local file, not a symlink/device")
            with args.input.open("rb") as stream:
                if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
                    raise ValueError("input must be a regular local file")
                result = verify_stream(stream, args.expected_packets, args.seed)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (OSError, ValueError) as error:
        print(json.dumps({"label": LABEL, "result": "REJECTED", "error": str(error),
                          "hardware_verified": False, "ts_smoke_pass": False}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
