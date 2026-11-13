"""Focused control pairing/decoding tests; no tuner access or third-party packages."""
import math
import struct
import unittest

import analyze_windows_monitor as monitor


def record(frame, irp, incoming, payload, *, status=0, endpoint=0, transfer=2):
    header = struct.pack('<HQIHBHHBBI', 28, irp, status, 8, incoming, 1, 4,
                         endpoint, transfer, len(payload)) + b'\x00'
    b = header + payload
    return struct.pack('<IIII', 1, frame * 1000, len(b), len(b)) + b


def capture(*events):
    return struct.pack('<IHHIIII', 0xa1b2c3d4, 2, 4, 0, 0, 65535, 249) + b''.join(events)


def identity():
    descriptor = bytes.fromhex('12010002000000404c733055000101020301')
    return [record(1, 9, 0, struct.pack('<BBHHH', 0x80, 6, 0x100, 0, 18)),
            record(2, 9, 1, descriptor)]


class ControlMonitorTests(unittest.TestCase):
    def test_real_capture_golden_values_are_not_misbanked(self):
        # Same raw bytes retained in sanitized external fixture; bank22 is not bank20.
        self.assertTrue(monitor.decode_register(0x20, 0x10, bytes.fromhex('26'))['locked'])
        self.assertFalse(monitor.decode_register(0x20, 0x10, bytes.fromhex('06'))['locked'])
        snr = monitor.decode_register(0x20, 0x28, bytes.fromhex('0370'))
        self.assertEqual(880, snr['snr_raw'])
        self.assertAlmostEqual(20.75555060468097, snr['snr_db_curve'])
        self.assertEqual(19400, snr['official_cnr_millidb'])
        self.assertEqual(19.4, snr['official_cnr_db'])
        self.assertIn('UNKNOWN', monitor.decode_register(0x22, 0x5b, b'\x03')['semantics'])
        self.assertIn('UNKNOWN', monitor.decode_register(0x24, 0x40, bytes.fromhex('010003b5'))['semantics'])

    def test_pair_irps_not_frame_adjacency_or_completion_order(self):
        events = identity() + [
            record(3, 10, 0, struct.pack('<BBHHH', 0xc0, 0x81, 0, 0, 1)),
            record(4, 11, 0, struct.pack('<BBHHH', 0xc0, 0x81, 0, 0, 1)),
            record(5, 11, 1, b'\x01'), record(6, 10, 1, b'\x00')]
        pairs, meta = monitor.control_pairs(capture(*events))
        self.assertTrue(meta['target_descriptor_verified'])
        self.assertEqual([1, 3, 4], [p['frame'] for p in pairs])
        self.assertEqual([b'\x00', b'\x01'], [p['payload'] for p in pairs[1:]])

    def test_i2c_pointer_bank_and_failed_response_not_decoded_as_good(self):
        pairs = []
        def add(frame, bm, request, payload, complete=True):
            pairs.append(dict(frame=frame, completion=frame + 1, time_ns=frame, end_ns=frame + 1,
                              status=0 if complete else 1, complete=complete, bm=bm, request=request,
                              value=0, index=0, requested=len(payload), payload=payload))
        add(1, 0x40, 0x80, bytes.fromhex('03c80022'))
        add(3, 0x40, 0x90, bytes.fromhex('01c85b'))
        add(5, 0xc0, 0x91, bytes.fromhex('03'))
        add(7, 0x40, 0x90, bytes.fromhex('01c85c'))
        add(9, 0xc0, 0x91, bytes.fromhex('02'), False)
        rows, _, warnings = monitor.i2c_inventory(pairs)
        self.assertFalse(warnings)
        self.assertEqual(0x22, rows[1]['bank'])
        self.assertEqual('03', rows[1]['hex'])
        self.assertFalse(rows[2]['complete'])

    def test_reject_truncation_orphan_duplicate_and_unknown_identity(self):
        good = capture(*identity())
        for data in (good[:-1], capture(record(1, 4, 1, b'\x01')), capture(),
                     capture(*identity(), record(3, 10, 0, b'\xc0\x81\x00\x00\x00\x00\x01\x00'),
                             record(4, 10, 0, b'\xc0\x81\x00\x00\x00\x00\x01\x00'))):
            with self.assertRaises(ValueError):
                monitor.control_pairs(data)

    def test_reset_discards_old_bank_and_pointer_evidence(self):
        def row(frame, bm, request, payload):
            return dict(frame=frame, completion=frame + 1, time_ns=frame, end_ns=frame + 1,
                        status=0, complete=True, bm=bm, request=request,
                        value=0, index=0, requested=len(payload), payload=payload)
        rows, _, warnings = monitor.i2c_inventory([
            row(1, 0x40, 0x80, bytes.fromhex('03c80022')),
            row(3, 0x40, 0x90, bytes.fromhex('01c85b')),
            row(5, 0x40, 0xa0, b'\x01'), row(7, 0xc0, 0x91, b'\x03'),
            row(9, 0x40, 0x90, bytes.fromhex('01c85b')), row(11, 0xc0, 0x91, b'\x03')])
        self.assertEqual('read without proven pointer', warnings[0]['error'])
        self.assertIsNone(rows[-1]['bank'])

    def test_zero_and_snr_clamp_edges(self):
        self.assertIsNone(monitor.decode_register(0x20, 0x28, b'\x00\x00')['snr_db_curve'])
        self.assertTrue(math.isfinite(monitor.decode_register(0x20, 0x28, b'\x00\x01')['snr_db_curve']))
        self.assertEqual(monitor.decode_register(0x20, 0x28, (10876).to_bytes(2, 'big'))['snr_db_curve'],
                         monitor.decode_register(0x20, 0x28, b'\xff\xff')['snr_db_curve'])

    def test_snr_is_available_only_with_lock_from_same_freeze_window(self):
        def row(frame, op, bank, reg, value):
            return dict(frame=frame, completion=frame+1, time_ns=frame, end_ns=frame+1,
                        status=0, complete=True, operation=op, address=0xc8, bank=bank,
                        register=reg, requested=len(bytes.fromhex(value)), hex=value)
        rows=[row(1,'write',0x20,1,'01'), row(3,'read',0x20,0x10,'26'),
              row(5,'read',0x20,0x28,'0370'), row(7,'write',0x20,1,'00'),
              row(9,'write',0x20,1,'01'), row(11,'read',0x20,0x28,'0370'),
              row(13,'write',0x20,1,'00')]
        report=monitor.summarize(rows,[],[],{},'abc')
        snr=[r['decoded'] for r in report['read_timeline'] if r['register']==0x28]
        self.assertEqual(19.4,snr[0]['snr_db'])
        self.assertIsNone(snr[1]['snr_db'])

    def test_inventory_retains_all_values_and_excludes_firmware_payload(self):
        pairs, meta = monitor.control_pairs(capture(*identity(),
            record(3, 12, 0, struct.pack('<BBHHH', 0x40, 0xa0, 0, 0, 4) + b'ABCD'),
            record(4, 12, 1, b'')))
        rows, other, warnings = monitor.i2c_inventory(pairs)
        report = monitor.summarize(rows, other, warnings, meta, 'abc')
        self.assertEqual([], report['other_vendor_commands'])
        self.assertNotIn('ABCD', repr(report))


if __name__ == '__main__':
    unittest.main()
