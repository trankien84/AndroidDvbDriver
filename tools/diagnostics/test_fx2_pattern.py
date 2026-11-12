"""Offline tests with in-memory fixtures; no firmware or temporary directories."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import struct
import unittest
from unittest.mock import mock_open, patch
import zlib

from fx2_pattern import (CRC_OFFSET, HEADER, LABEL, MARKER, MAX_PACKETS,
                         MAX_TOTAL_BYTES, PACKET_SIZE, main, make_packet,
                         validate_plan, verify_stream, write_fixture)


class PatternTests(unittest.TestCase):
    def fixture(self, packets=3, seed=0):
        stream = io.BytesIO()
        result = write_fixture(stream, packets, seed)
        self.assertEqual(result["total_bytes"], len(stream.getvalue()))
        return stream.getvalue()

    def reject(self, data, pattern=None, packets=3, seed=0):
        with self.assertRaisesRegex(ValueError, pattern or "."):
            verify_stream(io.BytesIO(data), packets, seed)

    def test_crc_standard_check_vector(self):
        self.assertEqual(zlib.crc32(b"123456789"), 0xCBF43926)

    def test_fixed_layout_and_little_endian(self):
        packet = make_packet(1, 3, 0x12345678)
        self.assertEqual(len(packet), 512)
        self.assertEqual(len(MARKER), 32)
        self.assertEqual(HEADER.size, 52)
        self.assertEqual(CRC_OFFSET, 508)
        self.assertEqual(packet[:32], MARKER)
        self.assertEqual(packet[32:52], bytes.fromhex(
            "01 00 00 02 01 00 00 00 03 00 00 00 78 56 34 12 00 06 00 00"))
        self.assertEqual(packet[52:56], bytes.fromhex("79 7a 7b 7c"))
        self.assertEqual(struct.unpack_from("<I", packet, 508)[0], zlib.crc32(packet[:508]))

    def test_crc_against_independent_bitwise_reference(self):
        packet = make_packet(0, 1, 17)
        crc = 0xFFFFFFFF
        for value in packet[:508]:
            crc ^= value
            for _ in range(8):
                crc = (crc >> 1) ^ (0xEDB88320 if crc & 1 else 0)
        self.assertEqual(packet[508:], (crc ^ 0xFFFFFFFF).to_bytes(4, "little"))

    def test_roundtrip_and_evidence_label(self):
        data = self.fixture(seed=0xFFFFFFFF)
        result = verify_stream(io.BytesIO(data), 3, 0xFFFFFFFF)
        self.assertEqual(result["label"], LABEL)
        self.assertEqual(result["result"], "SYNTHETIC_PATTERN_VERIFIED")
        self.assertFalse(result["hardware_verified"])
        self.assertFalse(result["ts_smoke_pass"])
        self.assertEqual(result["sha256"], hashlib.sha256(data).hexdigest())

    def test_repeatable_generation(self):
        self.assertEqual(self.fixture(), self.fixture())
        self.assertNotEqual(self.fixture(), self.fixture(seed=1))

    def test_single_packet(self):
        self.assertEqual(verify_stream(io.BytesIO(self.fixture(1)), 1)["packets"], 1)

    def test_hard_total_cap(self):
        data = self.fixture(MAX_PACKETS)
        result = verify_stream(io.BytesIO(data), MAX_PACKETS)
        self.assertEqual(len(data), MAX_TOTAL_BYTES)
        self.assertEqual(result["total_bytes"], MAX_TOTAL_BYTES)
        self.reject(data + b"x", "trailing", MAX_PACKETS)

    def test_bad_plan_rejected_before_io(self):
        for count in (0, -1, MAX_PACKETS + 1, 1.0, True, "3"):
            with self.subTest(count=count), self.assertRaises(ValueError):
                validate_plan(count, 0)
        for seed in (-1, 0x100000000, 1.0, True, "0"):
            with self.subTest(seed=seed), self.assertRaises(ValueError):
                validate_plan(1, seed)
        class NoIO:
            def read(self, size):
                raise AssertionError("must not read")
            def write(self, value):
                raise AssertionError("must not write")
        with self.assertRaises(ValueError):
            write_fixture(NoIO(), MAX_PACKETS + 1)
        with self.assertRaises(ValueError):
            verify_stream(NoIO(), 0)

    def test_bad_sequence_argument(self):
        for value in (-1, 3, True, 0.0):
            with self.subTest(value=value), self.assertRaises(ValueError):
                make_packet(value, 3)

    def test_empty_or_truncated(self):
        data = self.fixture()
        for size in (0, 1, 511, 512, 1024, len(data) - 1):
            with self.subTest(size=size):
                self.reject(data[:size], "truncated")

    def test_no_resynchronization(self):
        data = self.fixture()
        self.reject(b"x" + data, "marker")
        self.reject(data[1:], "marker")

    def test_crc_corruption(self):
        data = bytearray(self.fixture())
        data[HEADER.size + 20] ^= 1
        self.reject(data, "CRC")
        data = bytearray(self.fixture())
        data[CRC_OFFSET] ^= 1
        self.reject(data, "CRC")

    def test_payload_with_recomputed_crc_still_rejected(self):
        data = bytearray(self.fixture())
        data[52] ^= 1
        struct.pack_into("<I", data, CRC_OFFSET, zlib.crc32(data[:CRC_OFFSET]))
        self.reject(data, "payload")

    def test_each_header_field_checked_even_with_valid_crc(self):
        for offset, message in ((0, "marker"), (32, "version"), (34, "size"),
                                (36, "sequence"), (40, "plan"), (44, "plan"), (48, "plan")):
            data = bytearray(self.fixture())
            data[offset] ^= 1
            struct.pack_into("<I", data, CRC_OFFSET, zlib.crc32(data[:CRC_OFFSET]))
            with self.subTest(offset=offset):
                self.reject(data, message)

    def test_duplicate_gap_and_reorder(self):
        packets = [make_packet(index, 3) for index in range(3)]
        for order in ((0, 0, 2), (0, 2, 1), (1, 0, 2)):
            with self.subTest(order=order):
                self.reject(b"".join(packets[index] for index in order), "sequence")

    def test_mixed_plan_and_wrong_expected_seed(self):
        data = self.fixture()
        self.reject(data, "plan", seed=1)
        self.reject(make_packet(0, 3) + make_packet(1, 4) + make_packet(2, 3), "plan")
        self.reject(data, "plan", packets=2)

    def test_extra_packet_and_trailing_byte(self):
        data = self.fixture()
        for extra in (b"x", make_packet(0, 3)):
            self.reject(data + extra, "trailing")

    def test_real_ts_and_zero_bytes_cannot_pass(self):
        self.reject(b"\x47" + b"\x00" * 1535, "marker")
        self.reject(b"\x00" * 1536, "marker")

    def test_fragmented_reads_bounded_to_one_packet(self):
        class Fragmented(io.BytesIO):
            def read(self, size=-1):
                if not 0 <= size <= PACKET_SIZE:
                    raise AssertionError("unbounded read")
                return super().read(min(size, 17))
        self.assertEqual(verify_stream(Fragmented(self.fixture()), 3)["packets"], 3)

    def test_oversized_reader_response_rejected(self):
        class BadReader:
            def read(self, size):
                return b"x" * (size + 1)
        with self.assertRaisesRegex(ValueError, "reader"):
            verify_stream(BadReader(), 1)

    def test_short_write_rejected(self):
        class ShortWriter:
            def write(self, data):
                return len(data) - 1
        with self.assertRaisesRegex(OSError, "short"):
            write_fixture(ShortWriter(), 1)


class CliTests(unittest.TestCase):
    def run_cli(self, args):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = main(args)
        result = json.loads(output.getvalue())
        self.assertEqual(result["label"], LABEL)
        return code, result

    def test_generate_exclusive_labelled_file(self):
        opener = mock_open()
        opener.return_value.write.side_effect = len
        with patch.object(Path, "open", opener), patch("fx2_pattern.os.fstat") as fstat:
            fstat.return_value.st_mode = 0o100600
            code, result = self.run_cli(["generate", "--output", "test.synthetic-pattern"])
        self.assertEqual(code, 0)
        opener.assert_called_once_with("xb")
        self.assertEqual(result["total_bytes"], 4096)

    def test_refuse_overwrite(self):
        with patch.object(Path, "open", side_effect=FileExistsError("already exists")):
            code, result = self.run_cli(["generate", "--output", "test.synthetic-pattern"])
        self.assertEqual(code, 2)
        self.assertEqual(result["result"], "REJECTED")

    def test_unsafe_filename_and_invalid_cap_never_open(self):
        for args in (["generate", "--output", "firmware.hex"],
                     ["generate", "--output", "test.synthetic-pattern", "--packets", "2049"],
                     ["verify", "--input", "x", "--expected-packets", "0"]):
            with self.subTest(args=args), patch.object(Path, "open") as opener:
                code, _ = self.run_cli(args)
                self.assertEqual(code, 2)
                opener.assert_not_called()

    def test_missing_symlink_or_device_never_open(self):
        for symlink, regular in ((True, True), (False, False)):
            with patch.object(Path, "is_symlink", return_value=symlink), \
                 patch.object(Path, "is_file", return_value=regular), \
                 patch.object(Path, "open") as opener:
                code, _ = self.run_cli(["verify", "--input", "x", "--expected-packets", "1"])
                self.assertEqual(code, 2)
                opener.assert_not_called()

    def test_verify_regular_file(self):
        opener = mock_open()
        opener.return_value.read.side_effect = [make_packet(0, 1), b""]
        with patch.object(Path, "is_symlink", return_value=False), \
             patch.object(Path, "is_file", return_value=True), \
             patch.object(Path, "open", opener), \
             patch("fx2_pattern.os.fstat") as fstat:
            fstat.return_value.st_mode = 0o100600
            code, result = self.run_cli(["verify", "--input", "x", "--expected-packets", "1"])
        self.assertEqual(code, 0)
        self.assertEqual(result["result"], "SYNTHETIC_PATTERN_VERIFIED")

    def test_verify_rechecks_file_type_after_open(self):
        with patch.object(Path, "is_symlink", return_value=False), \
             patch.object(Path, "is_file", return_value=True), \
             patch.object(Path, "open", mock_open()), \
             patch("fx2_pattern.os.fstat") as fstat:
            fstat.return_value.st_mode = 0o020600
            code, _ = self.run_cli(["verify", "--input", "x", "--expected-packets", "1"])
        self.assertEqual(code, 2)

    def test_reserved_output_name_never_opened(self):
        with patch.object(Path, "is_reserved", return_value=True), \
             patch.object(Path, "open") as opener:
            code, _ = self.run_cli(["generate", "--output", "NUL.synthetic-pattern"])
            self.assertEqual(code, 2)
            opener.assert_not_called()

    def test_nonregular_output_never_written(self):
        opener = mock_open()
        with patch.object(Path, "open", opener), patch("fx2_pattern.os.fstat") as fstat:
            fstat.return_value.st_mode = 0o020600
            code, _ = self.run_cli(["generate", "--output", "test.synthetic-pattern"])
            self.assertEqual(code, 2)
            opener.return_value.write.assert_not_called()

    def test_expected_count_is_required(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
            main(["verify", "--input", "x"])
        self.assertEqual(error.exception.code, 2)


if __name__ == "__main__":
    unittest.main()
