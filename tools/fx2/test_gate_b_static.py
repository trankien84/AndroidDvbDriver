"""Offline tests; real firmware is optional via FX2_TEST_FIRMWARE, never bundled."""
import copy
import csv
import hashlib
import io
import os
from pathlib import Path
import unittest
from unittest.mock import patch

from gate_b_static import (IMAGE_LENGTH, Machine, a0_epochs, analyze, bit_address,
                           decode, gpio_effect, linear, porta_function, reconstruct_epoch,
                           setup_packet, vendor_table, verify_image)


class ImageTests(unittest.TestCase):
    def setUp(self):
        self.image = bytes(i & 255 for i in range(IMAGE_LENGTH))
        self.hash = hashlib.sha256(self.image).hexdigest()

    def test_correct_size_and_hash(self):
        verify_image(self.image, self.hash)

    def test_short_length_rejected(self):
        with self.assertRaisesRegex(ValueError, "length"):
            verify_image(self.image[:-1], self.hash)

    def test_long_length_rejected(self):
        with self.assertRaisesRegex(ValueError, "length"):
            verify_image(self.image + b"\x00", self.hash)

    def test_wrong_hash_rejected(self):
        with self.assertRaisesRegex(ValueError, "SHA"):
            verify_image(self.image)

    def test_setup_little_endian_not_instruction_endian(self):
        self.assertEqual(setup_packet(bytes.fromhex("40 a0 92 7f 00 00 40 00")),
                         (0x40, 0xA0, 0x7F92, 0, 64))
        self.assertNotEqual(setup_packet(bytes.fromhex("40 a0 7f 92 00 00 00 40"))[2:],
                            (0x7F92, 0, 64))

    def test_truncated_setup_rejected(self):
        with self.assertRaises(ValueError):
            setup_packet(b"\x40" * 7)


class EpochTests(unittest.TestCase):
    def setUp(self):
        self.image = bytes(i & 255 for i in range(IMAGE_LENGTH))
        self.hash = hashlib.sha256(self.image).hexdigest()
        def transfer(address, data):
            return dict(address=address, data=data, length=len(data), owner=("3", "3"),
                        status=0, request_type=0x40, index=0)
        self.transfers = [transfer(0x7F92, b"\x01"), transfer(0xE600, b"\x01")]
        self.transfers += [transfer(i, self.image[i:i + 64]) for i in range(0, IMAGE_LENGTH, 64)]
        self.transfers += [transfer(0x7F92, b"\x00"), transfer(0xE600, b"\x00")]

    def test_epoch_full_coverage(self):
        self.assertEqual(reconstruct_epoch(self.transfers, self.hash), self.image)

    def test_missing_transfer_rejected(self):
        with self.assertRaises(ValueError):
            reconstruct_epoch(self.transfers[:80] + self.transfers[81:], self.hash)

    def test_identical_overlap_rejected(self):
        self.transfers[3] = copy.deepcopy(self.transfers[2])
        with self.assertRaisesRegex(ValueError, "coverage/order/overlap"):
            reconstruct_epoch(self.transfers, self.hash)

    def test_conflicting_overlap_rejected(self):
        self.transfers[3]["address"] = 0
        self.transfers[3]["data"] = b"\xFF" * 64
        with self.assertRaises(ValueError):
            reconstruct_epoch(self.transfers, self.hash)

    def test_cross_owner_rejected(self):
        self.transfers[50]["owner"] = ("3", "4")
        with self.assertRaisesRegex(ValueError, "owner/epoch"):
            reconstruct_epoch(self.transfers, self.hash)

    def test_combining_two_uploads_rejected(self):
        with self.assertRaises(ValueError):
            reconstruct_epoch(self.transfers + self.transfers, self.hash)

    def test_endian_swapped_address_rejected(self):
        self.transfers[0]["address"] = 0x927F
        with self.assertRaises(ValueError):
            reconstruct_epoch(self.transfers, self.hash)

    def test_release_before_coverage_rejected(self):
        self.transfers[80], self.transfers[130] = self.transfers[130], self.transfers[80]
        with self.assertRaises(ValueError):
            reconstruct_epoch(self.transfers, self.hash)

    def test_failed_completion_rejected(self):
        self.transfers[50]["status"] = 0xC0000001
        with self.assertRaises(ValueError):
            reconstruct_epoch(self.transfers, self.hash)

    def test_truncated_payload_rejected(self):
        self.transfers[50]["data"] = self.transfers[50]["data"][:-1]
        with self.assertRaisesRegex(ValueError, "truncated"):
            reconstruct_epoch(self.transfers, self.hash)

    def test_wrong_direction_rejected(self):
        self.transfers[50]["request_type"] = 0xC0
        with self.assertRaises(ValueError):
            reconstruct_epoch(self.transfers, self.hash)

    def test_nonzero_index_rejected(self):
        self.transfers[50]["index"] = 1
        with self.assertRaises(ValueError):
            reconstruct_epoch(self.transfers, self.hash)

    def test_image_out_of_range_rejected(self):
        self.transfers[50]["address"] = 8192
        with self.assertRaises(ValueError):
            reconstruct_epoch(self.transfers, self.hash)


class CaptureTests(unittest.TestCase):
    def setUp(self):
        fixture = EpochTests()
        fixture.setUp()
        self.hash = fixture.hash
        self.rows = []
        for i, transfer in enumerate(fixture.transfers):
            row = {"frame.number": str(2 * i + 1), "frame.len": "100", "frame.cap_len": "100",
                   "usb.bus_id": "3", "usb.device_address": "3", "usb.endpoint_address": "0x00",
                   "usb.irp_id": "reused-after-completion", "usb.irp_info": "0x00", "usb.control_stage": "0",
                   "usb.usbd_status": "0", "usb.setup.bRequest": "160", "usb.bmRequestType": "0x40",
                   "usb.setup.wValue": hex(transfer["address"]), "usb.setup.wIndex": "0",
                   "usb.setup.wLength": str(transfer["length"]), "usb.data_fragment": transfer["data"].hex()}
            completion = dict(row, **{"frame.number": str(2 * i + 2), "usb.irp_info": "0x01",
                                      "usb.control_stage": "3", "usb.setup.bRequest": ""})
            self.rows.extend([row, completion])

    def parse(self):
        stream = io.StringIO()
        writer = csv.DictWriter(stream, fieldnames=list(self.rows[0]))
        writer.writeheader()
        writer.writerows(self.rows)
        stream.seek(0)
        with patch.object(Path, "open", return_value=stream):
            return a0_epochs(Path("unused-in-memory.csv"), self.hash)

    def test_paired_capture_with_reused_irp(self):
        self.assertEqual(self.parse()[0]["transfer_count"], 132)

    def test_missing_completion(self):
        self.rows.pop()
        with self.assertRaisesRegex(ValueError, "completion"):
            self.parse()

    def test_failed_completion(self):
        self.rows[11]["usb.usbd_status"] = "0xc0000001"
        with self.assertRaises(ValueError):
            self.parse()

    def test_failed_submit(self):
        self.rows[10]["usb.usbd_status"] = "0xc0000001"
        with self.assertRaises(ValueError):
            self.parse()

    def test_overlap_irp(self):
        self.rows.pop(1)
        with self.assertRaisesRegex(ValueError, "IRP"):
            self.parse()

    def test_non_a0_submit_cannot_reuse_pending_a0_irp(self):
        self.rows.pop(1)
        self.rows[1]["usb.setup.bRequest"] = "6"
        with self.assertRaisesRegex(ValueError, "IRP"):
            self.parse()

    def test_truncated_capture(self):
        self.rows[10]["frame.cap_len"] = "99"
        with self.assertRaisesRegex(ValueError, "truncated"):
            self.parse()

    def test_frame_order(self):
        self.rows[9]["frame.number"] = self.rows[8]["frame.number"]
        with self.assertRaisesRegex(ValueError, "increasing"):
            self.parse()

    def test_wrong_endpoint(self):
        self.rows[10]["usb.endpoint_address"] = "0x82"
        with self.assertRaisesRegex(ValueError, "EP0"):
            self.parse()

    def test_wrong_stage(self):
        self.rows[11]["usb.control_stage"] = "1"
        with self.assertRaisesRegex(ValueError, "stage"):
            self.parse()

    def test_same_owner_new_epoch_before_release(self):
        self.rows[30]["usb.setup.wValue"] = "0x7f92"
        self.rows[30]["usb.data_fragment"] = "01"
        self.rows[30]["usb.setup.wLength"] = "1"
        with self.assertRaisesRegex(ValueError, "epoch"):
            self.parse()

    def test_upload_without_reset_epoch(self):
        self.rows = self.rows[4:]
        with self.assertRaisesRegex(ValueError, "epoch"):
            self.parse()

    def test_empty_capture(self):
        self.rows[0]["usb.setup.bRequest"] = "6"
        with self.assertRaises(ValueError):
            self.parse()


class DecoderTests(unittest.TestCase):
    def test_iram_bit09_is_21_bit1(self):
        self.assertEqual(bit_address(9), {"space": "iram", "byte": 0x21, "bit": 1})
        self.assertNotEqual(bit_address(9)["byte"], 9)

    def test_87_is_ioa_not_pcon(self):
        self.assertEqual(bit_address(0x87), {"space": "sfr", "byte": 0x80, "bit": 7})
        self.assertNotEqual(bit_address(0x87)["byte"], 0x87)

    def test_86_is_ioa6(self):
        self.assertEqual(bit_address(0x86), {"space": "sfr", "byte": 0x80, "bit": 6})

    def test_all_bit_addresses(self):
        for address in range(256):
            with self.subTest(address=address):
                result = bit_address(address)
                self.assertEqual(result["bit"], address % 8)
                self.assertEqual(result["space"], "iram" if address < 128 else "sfr")

    def test_out_of_range_bits_rejected(self):
        for address in (-1, 256):
            with self.assertRaises(ValueError):
                bit_address(address)

    def test_pa7_is_not_automatically_flagd_in_slave_fifo(self):
        self.assertEqual(porta_function(0xCB, 0, 7), "GPIO_PA7")
        self.assertEqual(porta_function(0xCB, 0x40, 7), "SLCS#")
        self.assertEqual(porta_function(0xCB, 0x80, 7), "FLAGD")

    def test_flagd_priority_over_slcs(self):
        self.assertEqual(porta_function(0xCB, 0xC0, 7), "FLAGD")

    def test_pa6_is_pktend_in_slave_fifo(self):
        self.assertEqual(porta_function(0xCB, 0, 6), "PKTEND#")
        self.assertEqual(porta_function(0xCA, 0, 6), "GPIO_PA6")

    def test_flagd_undefined_outside_slave_fifo(self):
        self.assertIn("UNDEFINED", porta_function(0xCA, 0x80, 7))

    def test_ljmp_big_endian(self):
        self.assertEqual(decode(bytes.fromhex("02 06 c3"), 0)["target"], 0x6C3)

    def test_mov_dptr_big_endian(self):
        self.assertEqual(decode(bytes.fromhex("90 e7 41"), 0)["text"], "MOV DPTR,#E741")

    def test_known_instruction_lengths(self):
        samples = {"75 80 ce": 3, "85 0c 10": 3, "a8 21": 2, "b4 0a 00": 3,
                   "d5 21 fd": 3, "46": 1, "96": 1, "a4": 1, "f0": 1,
                   "12 06 7b": 3, "a2 09": 2, "92 87": 2, "d8 fe": 2,
                   "26": 1, "2f": 1, "36": 1, "3f": 1, "a6 21": 2}
        for raw, length in samples.items():
            with self.subTest(raw=raw):
                self.assertEqual(decode(bytes.fromhex(raw), 0)["length"], length)

    def test_relative_branch_signed(self):
        self.assertEqual(decode(bytes.fromhex("80 fe"), 0)["target"], 0)

    def test_mov_direct_source_destination_order(self):
        self.assertEqual(decode(bytes.fromhex("85 0c 10"), 0)["text"], "MOV direct 10,direct 0C")

    def test_instruction_truncation_rejected(self):
        with self.assertRaises(ValueError):
            decode(bytes.fromhex("90 e7"), 0)

    def test_block_misalignment_rejected(self):
        with self.assertRaises(ValueError):
            linear(bytes.fromhex("90 e7 41"), 0, 2)

    def test_unknown_opcode_executor_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "unsupported"):
            Machine(bytes.fromhex("a5 22"), 0).run()

    def test_unknown_xdata_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "unknown xdata"):
            Machine(bytes.fromhex("90 e7 41 e0 22"), 0).run()

    def test_execution_budget(self):
        with self.assertRaisesRegex(ValueError, "budget"):
            Machine(bytes.fromhex("80 fe"), 0).run(limit=10)


@unittest.skipUnless(os.environ.get("FX2_TEST_FIRMWARE"), "private firmware not supplied")
class RealFirmwareTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.image = Path(os.environ["FX2_TEST_FIRMWARE"]).read_bytes()
        verify_image(cls.image)

    def test_dispatch_8a_81_b7(self):
        table = vendor_table(self.image)
        self.assertEqual((table["138"], table["129"], table["183"]), (0x237, 0x223, 0x25F))
        self.assertEqual(table["default"], 0x2A7)

    def test_switch_helper_corruption_rejected(self):
        changed = bytearray(self.image)
        changed[0xADC] ^= 1
        with self.assertRaises(ValueError):
            vendor_table(changed)

    def test_0a00_not_pa7(self):
        result = gpio_effect(self.image, bytes.fromhex("0a00"))
        self.assertEqual(result["iram_21_bit1"], 0)
        self.assertEqual(result["writes"][-1]["address"], 0x86)
        self.assertNotIn("0x06C3", result["trace"])

    def test_0400_sets_pa7_latch_zero(self):
        result = gpio_effect(self.image, bytes.fromhex("0400"))
        self.assertEqual((result["writes"][-1]["address"], result["writes"][-1]["value"]), (0x87, 0))
        self.assertIn("0x06C3", result["trace"])

    def test_0401_sets_pa7_latch_one(self):
        result = gpio_effect(self.image, bytes.fromhex("0401"))
        self.assertEqual((result["writes"][-1]["address"], result["writes"][-1]["value"]), (0x87, 1))

    def test_payload_second_byte_nonzero_is_boolean_not_lsb(self):
        for value in range(256):
            with self.subTest(value=value):
                result = gpio_effect(self.image, bytes([4, value]))
                self.assertEqual(result["writes"][-1]["value"], int(value != 0))

    def test_all_payload_selectors_bounded(self):
        for selector in range(256):
            with self.subTest(selector=selector):
                result = gpio_effect(self.image, bytes([selector, 1]))
                bit_writes = [write for write in result["writes"] if write.get("space") == "bit" and write["address"] != 9]
                self.assertEqual(any(w["address"] == 0x87 for w in bit_writes), selector == 4)
                self.assertEqual(any(w["address"] == 0x86 for w in bit_writes), selector == 10)

    def test_short_payload_rejected(self):
        with self.assertRaises(ValueError):
            gpio_effect(self.image, bytes([4]))

    def test_8a_ready_control_path_uses_ep0_payload(self):
        machine = Machine(self.image, 0x237, {0xE6A0: 0, 0xE740: 4, 0xE741: 1})
        machine.run(stop=0x2A9)
        self.assertEqual(machine.bit(0x87), 1)
        self.assertIn(0x25A, machine.trace)
        self.assertIn(0x6C3, machine.trace)

    def test_8a_busy_does_not_immediately_consume_payload(self):
        machine = Machine(self.image, 0x237, {0xE6A0: 2, 0xE740: 4, 0xE741: 1})
        with self.assertRaisesRegex(ValueError, "budget"):
            machine.run(limit=100)
        self.assertNotIn(0x67B, machine.trace)

    def test_81_reads_iram_bit07_not_ioa7(self):
        for bit07 in (0, 1):
            machine = Machine(self.image, 0x223)
            machine.set_bit(7, bit07)
            machine.set_bit(0x87, 1 - bit07)
            machine.run(stop=0x2A9)
            self.assertEqual(machine.xdata[0xE740], bit07)
            self.assertEqual(machine.xdata[0xE68B], 1)

    def test_fifo_init(self):
        result = analyze(self.image)
        writes = result["initialization_writes"]
        by_address = {write["address"]: write["value"] for write in writes if write.get("space") not in ("direct", "bit")}
        self.assertEqual(by_address[0xE601], 0xCB)
        self.assertEqual(by_address[0xE612], 0xE8)
        self.assertEqual(by_address[0xE618], 9)
        self.assertEqual((by_address[0xE620], by_address[0xE621]), (2, 0))
        self.assertEqual([w["value"] for w in writes if w["address"] == 0xE604], [0x80, 2, 4, 6, 8, 0])
        self.assertFalse(result["hardware_replay_approved"])

    def test_private_capture_epochs(self):
        path = os.environ.get("FX2_TEST_EP0_CSV")
        if not path:
            self.skipTest("private capture CSV not supplied")
        epochs = a0_epochs(Path(path))
        self.assertEqual(len(epochs), 2)
        self.assertTrue(all(e["transfer_count"] == 132 for e in epochs))


if __name__ == "__main__":
    unittest.main(verbosity=2)
