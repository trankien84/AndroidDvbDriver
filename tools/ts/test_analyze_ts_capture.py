"""Synthetic regression fixtures; no capture files, packages, network or hardware."""
import contextlib
import io
import json
from pathlib import Path
import struct
import unittest
from unittest.mock import patch

import analyze_ts_capture as ts


def packet(pid=100, cc=0, payload=b'', pusi=False, adaptation=None, tei=False, scrambled=0):
    head = bytes([0x47, (pid >> 8) | (0x40 if pusi else 0) | (0x80 if tei else 0),
                  pid & 255, (scrambled << 6) | (0x30 if adaptation is not None else 0x10) | cc])
    body = (bytes([len(adaptation)]) + adaptation if adaptation is not None else b'') + payload
    if len(body) > 184:
        raise ValueError('fixture packet overflow')
    return head + body + b'\xff' * (184 - len(body))


def adaptation_only(pid, cc, flags=0):
    return bytes([0x47, pid >> 8, pid & 255, 0x20 | cc, 183, flags]) + b'\xff' * 182


def section(tid, extension, body, number=0, last=0, version=0, current=True):
    length = 5 + len(body) + 4
    s = bytes([tid, 0xB0 | (length >> 8), length & 255]) + struct.pack('>H', extension)
    s += bytes([0xC0 | (version << 1) | current, number, last]) + body
    return s + struct.pack('>I', ts.crc32_mpeg(s))


def pat(pairs=((1, 200),), **kwargs):
    return section(0, 10, b''.join(struct.pack('>HH', program, 0xE000 | pid) for program, pid in pairs), **kwargs)


def pmt():
    return section(2, 1, struct.pack('>HHBHH', 0xE000 | 100, 0xF000, 0x1B, 0xE000 | 100, 0xF000))


def sdt():
    name, provider = b'\x15Test HD', b'\x15Provider'
    descriptor = bytes([1, len(provider)]) + provider + bytes([len(name)]) + name
    ds = bytes([0x48, len(descriptor)]) + descriptor
    body = struct.pack('>HBHBH', 20, 255, 1, 0xFC, 0x8000 | len(ds)) + ds
    return section(0x42, 10, body)


def psi_packet(pid, s, cc=0):
    return packet(pid, cc, b'\x00' + s, pusi=True)


def continuous(count, pid=100):
    return b''.join(packet(pid, n % 16, bytes([n % 256])) for n in range(count))


def pcr_bytes(ticks):
    base, ext = divmod(ticks % ts.PCR_WRAP, 300)
    return bytes([(base >> 25) & 255, (base >> 17) & 255, (base >> 9) & 255,
                  (base >> 1) & 255, ((base & 1) << 7) | 0x7E | (ext >> 8), ext & 255])


def pcap(records, endian='<', nano=False, link=249, snap=1000000):
    magic = {('<', False): b'\xd4\xc3\xb2\xa1', ('>', False): b'\xa1\xb2\xc3\xd4',
             ('<', True): b'\x4d\x3c\xb2\xa1', ('>', True): b'\xa1\xb2\x3c\x4d'}[endian, nano]
    header = magic + struct.pack(endian + 'HHIIII', 2, 4, 0, 0, snap, link)
    return header + b''.join(struct.pack(endian + 'IIII', 1, n, len(r), len(r)) + r for n, r in enumerate(records))


def usb(payload=b'', bus=3, device=3, endpoint=0x82, transfer=3, status=0, complete=True, irp=1):
    h = 28 if transfer == 2 else 27
    return struct.pack('<HQIHBHHBBI', h, irp, status, 9, int(complete), bus, device, endpoint, transfer, len(payload)) + (b'\x03' if h == 28 else b'') + payload


class SyncTests(unittest.TestCase):
    def test_continuous(self):
        r = ts.analyze(continuous(100), ts.raw_spans(18800, 4096))
        self.assertEqual((r['sync']['segment_count'], r['sync']['break_count'], r['sync']['packets']), (1, 0, 100))
        self.assertEqual(r['cc']['within_errors'], 0)
        self.assertEqual(r['cc']['within_comparisons'], 99)
        self.assertEqual(r['sync']['segments'][0]['end_urb']['offset_in_urb'], 2415)

    def test_cut_middle(self):
        raw = continuous(60)
        raw = raw[:188 * 20 + 80] + raw[188 * 20 + 117:]
        r = ts.analyze(raw, ts.raw_spans(len(raw), 4096))
        self.assertEqual(r['sync']['segment_count'], 2)
        self.assertEqual(r['sync']['packets'], 59)
        self.assertEqual(r['sync']['segments'][1]['start'], 188 * 21 - 37)
        self.assertEqual(r['cc']['within_errors'], 0)
        self.assertEqual(r['cc']['between_errors'], 1)
        self.assertEqual(r['cc']['between_comparisons'], 1)

    def test_whole_packet_loss(self):
        raw = continuous(60)
        r = ts.analyze(raw[:188 * 20] + raw[188 * 21:])
        self.assertEqual(r['sync']['segment_count'], 1)
        self.assertEqual(r['cc']['within_errors'], 1)

    def test_boundary_positions_and_prefix_not_discarded(self):
        raw = continuous(10) + b'\x00' * 168 + continuous(20)
        r = ts.analyze(raw, ts.raw_spans(len(raw), 4096))
        self.assertEqual(r['sync']['breaks'][0]['distance_from_2048'], -168)
        self.assertEqual(r['sync']['near_2048_breaks'], 1)
        self.assertEqual(r['sync']['packets'], 30)
        self.assertEqual(r['stale_prefix']['status'], 'HYPOTHESIS_NOT_PROVEN')
        self.assertEqual(r['stream_sha256'], ts.sha256(raw))

    def test_noise_truncated_tail_and_short_false_sync(self):
        raw = b'G' + b'\x00' * 100 + continuous(10) + packet()[:70]
        r = ts.analyze(raw)
        self.assertEqual(r['sync']['leading_bytes'], 101)
        self.assertEqual(r['sync']['trailing_bytes'], 70)
        self.assertEqual(r['sync']['packets'], 10)
        self.assertIsNone(r['sync']['segments'][0]['start_urb'])

    def test_explicit_skip_preserves_raw_offsets_hash_and_tail(self):
        tail = packet()[:31]
        raw = b'\x00' * 2048 + psi_packet(0, pat()) + psi_packet(200, pmt()) + continuous(20) + tail
        r = ts.analyze(raw, ts.raw_spans(len(raw), 4096), skip_prefix=2048)
        self.assertEqual(r['stream_sha256'], ts.sha256(raw))
        self.assertEqual(r['stream_bytes'], len(raw))
        self.assertEqual(r['analysis_range']['start'], 2048)
        self.assertEqual(r['sync']['segments'][0]['start'], 2048)
        self.assertEqual(r['sync']['segments'][0]['start_urb']['offset_in_urb'], 2048)
        self.assertEqual((r['sync']['segment_count'], r['sync']['break_count']), (1, 0))
        self.assertEqual(r['cc']['within_errors'], 0)
        self.assertEqual((r['psi']['pat_programs'], r['psi']['pmt_programs']), (1, 1))
        self.assertEqual(r['sync']['tail_incomplete_packet_bytes'], 31)
        self.assertFalse(r['sync']['tail_counts_as_sync_break'])
        self.assertEqual(r['native_queue']['host_hardware_queue'], 'UNKNOWN_NOT_MEASURED')
        for invalid in (-1, len(raw) + 1):
            with self.assertRaises(ValueError):
                ts.analyze(raw, skip_prefix=invalid)

    def test_no_sync_and_empty(self):
        for data in (b'', b'\x00' * 2000, continuous(4)):
            r = ts.analyze(data)
            self.assertEqual(r['sync']['segment_count'], 0)
            self.assertIsNone(r['pcr']['median_bitrate_bps'])

    def test_failed_urb_forces_boundary_even_if_phase_aligned(self):
        r = ts.analyze(continuous(30), barriers=[188 * 10])
        self.assertEqual(r['sync']['segment_count'], 2)
        self.assertTrue(r['sync']['breaks'][0]['forced_by_failed_urb'])
        self.assertEqual(r['cc']['between_comparisons'], 1)


class CCTests(unittest.TestCase):
    def check(self, packets):
        c = ts.Continuity()
        for n, raw in enumerate(packets):
            c.feed(ts.parse_packet(raw), 0, n * 188)
        return c.report()

    def test_wrap(self):
        r = self.check([packet(cc=15), packet(cc=0)])
        self.assertEqual((r['within_errors'], r['within_comparisons']), (0, 1))

    def test_adaptation_only_does_not_increment(self):
        r = self.check([packet(cc=4), adaptation_only(100, 4), packet(cc=5)])
        self.assertEqual((r['within_errors'], r['within_comparisons']), (0, 2))
        self.assertEqual(self.check([packet(cc=4), adaptation_only(100, 5)])['within_errors'], 1)

    def test_signaled_discontinuity(self):
        r = self.check([packet(cc=0), packet(cc=10, adaptation=b'\x80'), packet(cc=11)])
        self.assertEqual((r['within_errors'], r['within_comparisons'], r['signaled_discontinuities']), (0, 1, 1))

    def test_duplicate_once_only(self):
        p = packet(cc=2, payload=b'abc')
        r = self.check([p, p, p])
        self.assertEqual((r['within_errors'], r['within_duplicates']), (1, 1))

    def test_same_counter_changed_payload_is_error(self):
        self.assertEqual(self.check([packet(payload=b'a'), packet(payload=b'b')])['within_errors'], 1)

    def test_duplicate_pcr_can_change(self):
        a = packet(cc=2, adaptation=b'\x10' + pcr_bytes(0))
        b = packet(cc=2, adaptation=b'\x10' + pcr_bytes(270000))
        self.assertEqual(self.check([a, b])['within_duplicates'], 1)

    def test_tei_invalid_reset_and_null_excluded(self):
        r = self.check([packet(cc=0), packet(cc=8, tei=True), packet(cc=3), packet(8191, 9), packet(8191, 1)])
        self.assertEqual(r['within_comparisons'], 0)
        invalid = bytearray(packet())
        invalid[3] = 0
        self.assertIsNotNone(ts.parse_packet(invalid)['invalid'])
        invalid = bytearray(packet(adaptation=b'\x00'))
        invalid[4] = 183
        self.assertIsNotNone(ts.parse_packet(invalid)['invalid'])

    def test_scrambled_cc_still_checked(self):
        r = ts.analyze(b''.join(packet(cc=n % 16, scrambled=2) for n in range(10)))
        self.assertEqual(r['totals']['scrambled'], 10)
        self.assertEqual(r['cc']['within_comparisons'], 9)


class PSITests(unittest.TestCase):
    def test_crc32_mpeg_known_independent_vector(self):
        self.assertEqual(ts.crc32_mpeg(b'123456789'), 0x0376E6E7)

    def test_pat_pmt_sdt_crc_and_service_pid_table(self):
        r = ts.analyze(psi_packet(0, pat()) + psi_packet(200, pmt()) + psi_packet(17, sdt()) + continuous(10))
        self.assertEqual((r['psi']['pat_programs'], r['psi']['pmt_programs']), (1, 1))
        s = r['psi']['services'][0]
        self.assertEqual((s['name'], s['provider'], s['pcr_pid']), ('Test HD', 'Provider', 100))
        self.assertEqual(s['streams'][0]['stream_type'], 0x1B)
        self.assertIn('ES:1/type:0x1b', r['psi']['pid_roles']['100'])
        self.assertIn('Test HD', ts.markdown({**r, 'input': {'sha256': 'test'}}))

    def test_section_reassembly_across_packets_and_urbs(self):
        large = pat(tuple((i, 200 + i) for i in range(1, 51)))
        raw = packet(0, 0, b'\x00' + large[:183], pusi=True) + packet(0, 1, large[183:]) + continuous(10)
        r = ts.analyze(raw, ts.raw_spans(len(raw), 200))
        self.assertEqual(r['psi']['pat_programs'], 50)

    def test_pointer_completes_old_then_multiple_sections(self):
        received = []
        a = ts.PSIAssembler(received.append)
        s, t = pat(), pat(((2, 201),))
        a.feed(b'\x00' + s[:10], True)
        a.feed(bytes([len(s) - 10]) + s[10:] + t + b'\xff', True)
        self.assertEqual(received, [s, t])

    def test_pointer_overrun_and_malformed_section(self):
        a = ts.PSIAssembler(lambda s: None)
        a.feed(b'\xff\x01', True)
        a.feed(b'\x00\x00\xbf\xff', True)
        self.assertEqual(a.errors, 2)

    def test_crc_rejected(self):
        s = bytearray(pat())
        s[-1] ^= 1
        r = ts.analyze(psi_packet(0, s) + continuous(10))
        self.assertEqual(r['psi']['pat_programs'], 0)
        self.assertEqual(r['psi']['counts']['crc_errors'], 1)

    def test_cc_loss_and_segment_break_do_not_join_psi(self):
        large = pat(tuple((i, 200 + i) for i in range(1, 51)))
        for raw in (packet(0, 0, b'\x00' + large[:183], pusi=True) + packet(0, 2, large[183:]) + continuous(10),
                    continuous(6) + packet(0, 0, b'\x00' + large[:183], pusi=True) + b'\x00' * 31 + packet(0, 1, large[183:]) + continuous(6)):
            self.assertEqual(ts.analyze(raw)['psi']['pat_programs'], 0)

    def test_scrambled_tei_and_duplicate_psi(self):
        p = psi_packet(0, pat())
        for mask, index in ((0x80, 1), (0x80, 3)):
            bad = bytearray(p)
            bad[index] |= mask
            self.assertEqual(ts.analyze(bytes(bad) + continuous(10))['psi']['pat_programs'], 0)
        r = ts.analyze(p + p + continuous(10))
        self.assertEqual(r['psi']['counts']['pat_crc_valid_sections'], 1)

    def test_multisection_and_version_replacement(self):
        t = ts.Tables()
        t.section(0, pat(((1, 200),), number=0, last=1))
        self.assertEqual(t.pat, {})
        t.section(0, pat(((2, 201),), number=1, last=1))
        self.assertEqual(t.pat, {1: 200, 2: 201})
        t.section(0, pat(((3, 202),), version=1, current=False))
        self.assertEqual(len(t.pat), 2)
        t.section(0, pat(((3, 202),), version=1))
        self.assertEqual(t.pat, {3: 202})

    def test_unsupported_dvb_encoding_does_not_crash(self):
        self.assertEqual(ts.dvb_text(b'\x10\x00\x0cABC'), 'ABC')

    def test_malformed_crc_valid_pmt_not_published(self):
        t = ts.Tables()
        t.section(0, pat())
        t.section(200, section(2, 1, b'\xe0\x64\xff\xff'))
        self.assertEqual(t.pmts, {})
        self.assertEqual(t.counts['malformed_sections'], 1)


class PCRTests(unittest.TestCase):
    def stream(self, count=4000, rate=27_500_000, initial=0, discontinuity=None):
        packets = []
        for n in range(count):
            adaptation = None
            if n % 100 == 0:
                ticks = initial + round(n * 188 * 8 * ts.PCR_CLOCK / rate)
                adaptation = bytes([0x90 if n == discontinuity else 0x10]) + pcr_bytes(ticks)
            packets.append(packet(cc=n % 16, adaptation=adaptation))
        return b''.join(packets)

    def test_sufficient_pcr_and_wrap(self):
        for initial in (0, ts.PCR_WRAP - 270000):
            r = ts.analyze(self.stream(initial=initial))
            self.assertAlmostEqual(r['pcr']['median_bitrate_bps'], 27_500_000, delta=5)

    def test_bad_pcr_bits_do_not_invalidate_cc_and_diagnostic_is_separate(self):
        raw = bytearray(self.stream())
        for n in range(0, 4000, 100):
            raw[n * 188 + 5] |= 128
            raw[n * 188 + 10] &= 0x81
        r = ts.analyze(bytes(raw))
        self.assertEqual(r['totals']['invalid'], 0)
        self.assertEqual(r['totals']['pcr_errors'], 40)
        self.assertEqual(r['cc']['within_errors'], 0)
        self.assertIsNone(r['pcr']['median_bitrate_bps'])
        self.assertAlmostEqual(r['pcr']['diagnostic_only']['median_bitrate_bps'], 27_500_000, delta=5)
        self.assertIn('NOT_VALIDATED', r['pcr']['diagnostic_only']['status'])

    def test_invalid_pcr_extension_is_not_ts_header_failure(self):
        pcr = pcr_bytes(0)
        pcr = pcr[:4] + bytes([pcr[4] | 1, 255])
        p = ts.parse_packet(packet(adaptation=b'\x10' + pcr))
        self.assertIsNone(p['pcr'])
        self.assertIsNone(p['invalid'])
        self.assertEqual(p['pcr_error'], 'invalid PCR extension')

    def test_short_segment_unknown(self):
        r = ts.analyze(self.stream(300))
        self.assertIsNone(r['pcr']['median_bitrate_bps'])

    def test_no_cross_segment_or_signaled_discontinuity_join(self):
        raw = self.stream(1000)
        r = ts.analyze(raw + b'\x00' + raw)
        self.assertTrue(all(w['bitrate_bps'] is None for w in r['pcr']['windows']))
        r = ts.analyze(self.stream(discontinuity=2000))
        self.assertEqual(len(r['pcr']['windows']), 2)
        self.assertTrue(all(w['samples'] == 20 for w in r['pcr']['windows']))


class PcapTests(unittest.TestCase):
    def test_filter_success_completion_bulk_in82_only_and_completion_order(self):
        source = pcap([usb(b'ignored', complete=False, irp=1), usb(b'out', endpoint=2, irp=2),
                       usb(b'bad', status=1, irp=1), usb(b'abc', irp=2), usb(b'def', irp=3)])
        raw, spans, barriers, meta = ts.extract_usbpcap(source)
        self.assertEqual(raw, b'abcdef')
        self.assertEqual([s['frame'] for s in spans], [4, 5])
        self.assertEqual(len(meta['failed_completions']), 1)
        self.assertEqual(meta['orphan_successful_completions'], 2)

    def test_ambiguous_device_bus_and_explicit_filter(self):
        source = pcap([usb(b'a', device=3), usb(b'b', device=4), usb(b'c', bus=4)])
        with self.assertRaisesRegex(ValueError, 'Ambiguous'):
            ts.extract_usbpcap(source)
        with self.assertRaisesRegex(ValueError, 'Ambiguous'):
            ts.extract_usbpcap(source, device=3)
        raw, _, _, meta = ts.extract_usbpcap(source, bus=3, device=4)
        self.assertEqual((raw, meta['selected_device']), (b'b', 4))

    def test_endian_and_nanosecond_headers(self):
        for endian in ('<', '>'):
            for nano in (False, True):
                raw, spans, _, _ = ts.extract_usbpcap(pcap([usb(b'a'), usb(b'b', irp=2)], endian, nano))
                self.assertEqual(raw, b'ab')
                self.assertEqual(spans[1]['timestamp_ns'], 1000000000 + (1 if nano else 1000))

    def test_truncated_global_record_payload_and_snaplen(self):
        good = pcap([usb(b'abc')])
        for malformed in (good[:15], good[:25], good[:-1], pcap([usb(b'abc')], snap=27)):
            with self.subTest(length=len(malformed)), self.assertRaises(ValueError):
                ts.extract_usbpcap(malformed)
        truncated = bytearray(good)
        struct.pack_into('<I', truncated, 36, 31)
        with self.assertRaises(ValueError):
            ts.extract_usbpcap(truncated)

    def test_malformed_usb_lengths_and_short_header(self):
        for record in (b'\x00' * 20, b'\x00' * 27, usb(b'abc')[:23] + struct.pack('<I', 999) + b'abc'):
            with self.assertRaises(ValueError):
                ts.extract_usbpcap(pcap([record]))

    def test_wrong_linktype_pcapng_empty_no_success(self):
        for source in (pcap([usb(b'a')], link=189), b'\x0a\x0d\x0d\x0a' * 8,
                       pcap([]), pcap([usb(b'bad', status=1)])):
            with self.assertRaises(ValueError):
                ts.extract_usbpcap(source)

    def test_overlapping_irp_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Overlapping'):
            ts.extract_usbpcap(pcap([usb(complete=False), usb(complete=False), usb(b'a')]))

    def test_changed_device_identity_same_address_rejected(self):
        setup = b'\x80\x06\x00\x01\x00\x00\x12\x00'
        descriptor = b'\x12\x01' + b'\x00' * 6 + b'\x4c\x73\x30\x55\x00\x01' + b'\x00' * 4
        other = descriptor[:10] + b'\x01\x02' + descriptor[12:]
        records = [usb(setup, endpoint=0, transfer=2, complete=False), usb(descriptor, endpoint=0, transfer=2),
                   usb(setup, endpoint=0, transfer=2, complete=False), usb(other, endpoint=0, transfer=2), usb(b'a')]
        with self.assertRaisesRegex(ValueError, 'reused'):
            ts.extract_usbpcap(pcap(records))

    def test_failed_transfer_marks_join_barrier(self):
        raw, _, barriers, _ = ts.extract_usbpcap(pcap([usb(continuous(10)), usb(status=1), usb(continuous(10))]))
        self.assertEqual(barriers, [1880])
        self.assertEqual(ts.analyze(raw, barriers=barriers)['sync']['segment_count'], 2)

    def test_cli_prevents_overwrite_input_and_invalid_raw_flags(self):
        here = Path(__file__)
        for args in ([str(here), '--json', str(here)], [str(here), '--urb-size', '0'],
                     [str(here), '--bus', '3'], [str(here), '--min-sync-packets', '1']):
            with contextlib.redirect_stderr(io.StringIO()), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(ts.main(args), 2)


def timed_bursts(sizes, start_slots=None, catchup=False):
    """Synthetic 242 ms T2-like cadence, independent 27.5 Mb/s completion clock."""
    spans, offset = [], 0
    for frame, size in enumerate(sizes):
        start = (start_slots[frame] if start_slots else frame) * 242_000_000
        chunks = [(at, min(4096, size - at)) for at in range(0, size, 4096)]
        for index, (at, length) in enumerate(chunks):
            elapsed = round(at * 8e9 / 27_500_000)
            if catchup and frame == 1 and index < 5:
                elapsed = index * 50_000
            spans.append({'index': len(spans), 'start': offset, 'end': offset + length,
                          'timestamp_ns': 1_000_000_000 + start + elapsed})
            offset += length
    raw = continuous((offset + 187) // 188)[:offset]
    return raw, spans


def native_audit(lengths=(1880, 1880), mappings=False):
    rows, sequence, offset = [], 0, 0
    def event(kind, actual=0, status=None):
        nonlocal sequence
        sequence += kind == 1
        before = 1_000_000_000 + len(rows) * 1_000_000
        rows.append({'run_id': 'synthetic', 'event': sum('kind' in r for r in rows) + 1,
                     'kind': kind, 'endpoint': 130, 'clock': 'CLOCK_MONOTONIC',
                     'before_ns': before, 'after_ns': before + 1000,
                     'sequence': sequence, 'syscall_return': 0, 'syscall_errno': 0,
                     'urb_status': status, 'actual_length': actual,
                     'requested_length': max(4096, actual), 'allocated_capacity': max(4096, actual)})
    for length in lengths:
        event(1)
        event(2, length, 0)
        if mappings:
            rows.append({'mapping': True, 'sequence': sequence, 'source_offset': offset,
                         'raw_offset': offset, 'length': length, 'dropped': False,
                         'reap_ns': rows[-1]['after_ns']})
        offset += length
    event(3)
    event(2, 512, -2)
    rows.append({'run_id': 'synthetic', 'summary': True, 'delivered_bytes': offset,
                 'writer_written_bytes': offset, 'writer_drop_bytes': 0, 'writer_failure': False})
    return rows


def encode_audit(rows):
    return ('\n'.join(json.dumps(r) for r in rows) + '\n').encode()


class FrameTests(unittest.TestCase):
    def test_242ms_full_partial_missing_and_separate_burst_rate(self):
        baseline = 540672
        sizes = [baseline, baseline, baseline // 2, baseline, baseline, baseline]
        raw, spans = timed_bursts(sizes, [0, 1, 2, 4, 5, 6])
        r = ts.analyze(raw, spans, frame_baseline_bytes=baseline)['completion_frames']
        self.assertEqual(r['frame_counts'], {'FULL': 5, 'PARTIAL': 1, 'MISSING': 1})
        self.assertEqual(r['cadence']['ms'], 242)
        self.assertEqual(r['baseline']['candidate_large_frame_median_bytes'], baseline)
        self.assertAlmostEqual(r['burst_rate']['median_bps'], 27_500_000, delta=10)
        self.assertGreater(r['time_to_first_full_ms'], 150)
        self.assertEqual(r['first_full_start_ms'], 0)
        self.assertEqual(r['missing'][0]['expected_start_ms'], [726])
        self.assertEqual(r['frames'][1]['gap_inference']['label'], 'SOURCE_LIKE_INFERRED')
        self.assertEqual(r['total']['packets'], len(raw) // 188)
        self.assertEqual(r['total']['bytes'], len(raw))

    def test_homogeneous_half_frame_does_not_self_qualify_full(self):
        raw, spans = timed_bursts([270336] * 7)
        unknown = ts.analyze(raw, spans)['completion_frames']
        self.assertEqual(unknown['baseline']['candidate_large_frame_median_bytes'], 270336)
        self.assertEqual(unknown['frame_counts']['UNKNOWN_BASELINE'], 7)
        self.assertIsNone(unknown['time_to_first_full_ms'])
        anchored = ts.analyze(raw, spans, frame_baseline_bytes=540672)['completion_frames']
        self.assertEqual(anchored['frame_counts']['PARTIAL'], 7)
        self.assertNotIn('FULL', anchored['frame_counts'])

    def test_delayed_host_catchup_is_inferred_not_kernel_proof(self):
        raw, spans = timed_bursts([540672] * 5, catchup=True)
        r = ts.analyze(raw, spans)['completion_frames']
        f = r['frames'][1]
        self.assertEqual(f['gap_inference']['label'], 'HOST_DELAYED_CATCHUP_INFERRED')
        self.assertEqual(f['gap_inference']['initial_intervals_below_0_2ms'], 4)
        self.assertIn('not_bus_signaling', r['timestamp_kind'])

    def test_packet_crossing_completion_and_frame_boundaries_assigned_once(self):
        raw = continuous(20)
        spans = [{'index': 0, 'start': 0, 'end': 200, 'timestamp_ns': 0},
                 {'index': 1, 'start': 200, 'end': len(raw), 'timestamp_ns': 16_000_000}]
        r = ts.analyze(raw, spans)['completion_frames']
        self.assertEqual([f['metrics']['packets'] for f in r['frames']], [1, 19])
        self.assertEqual([f['metrics']['bytes'] for f in r['frames']], [200, len(raw) - 200])
        self.assertEqual(sum(f['metrics']['cc_comparisons'] for f in r['frames']), 19)
        self.assertEqual(r['total']['packets'], 20)

    def test_after5s_exact_threshold_cc_tei_null_and_skip(self):
        raw = continuous(8) + packet(cc=12) + packet(101, tei=True) + packet(8191) + continuous(4, 101)
        spans = [{'index': 0, 'start': 0, 'end': 8 * 188 + 50, 'timestamp_ns': 0},
                 {'index': 1, 'start': 8 * 188 + 50, 'end': len(raw), 'timestamp_ns': 5_000_000_000}]
        r = ts.analyze(raw, spans, skip_prefix=188, frame_baseline_bytes=1000,
                       frame_cadence_ns=5_000_000_000)['completion_frames']
        m = r['after_5s']['metrics']
        self.assertEqual((m['packets'], m['tei_packets'], m['null_packets'], m['cc_errors']), (7, 1, 1, 1))
        self.assertEqual(m['bytes'], len(raw) - 8 * 188 - 50)
        self.assertEqual(r['total']['packets'], 14)
        self.assertEqual(r['after_5s']['full_percent_observed_frames'], 100)

    def test_after5s_missing_denominator_and_short_capture_unknown(self):
        raw, spans = timed_bursts([18800] * 25, list(range(23)) + [24, 25])
        r = ts.analyze(raw, spans, frame_baseline_bytes=18800, frame_cadence_ns=242_000_000)['completion_frames']
        a = r['after_5s']
        self.assertEqual(a['frame_counts'], {'FULL': 4})
        self.assertEqual(a['missing_frames'], 1)
        self.assertEqual(a['full_percent_observed_frames'], 100)
        self.assertEqual(a['full_percent_including_missing'], 80)
        raw, spans = timed_bursts([18800] * 3)
        a = ts.analyze(raw, spans)['completion_frames']['after_5s']
        self.assertIsNone(a['metrics'])
        self.assertIsNone(a['full_percent_observed_frames'])
        self.assertIn('SHORTER', a['status'])

    def test_after5s_straddler_metrics_but_no_full_percentage(self):
        raw = continuous(20)
        spans = [{'index': 0, 'start': 0, 'end': 188, 'timestamp_ns': 0},
                 {'index': 1, 'start': 188, 'end': 1880, 'timestamp_ns': 4_999_000_000},
                 {'index': 2, 'start': 1880, 'end': 3760, 'timestamp_ns': 5_001_000_000}]
        t = ts.analyze(raw, spans, frame_baseline_bytes=3760,
                       timing_metadata={'origin_ns': 0})['completion_frames']
        self.assertEqual(t['after_5s']['metrics']['packets'], 10)
        self.assertEqual(t['after_5s']['straddling_frames_excluded_from_percentage'], 1)
        self.assertIsNone(t['after_5s']['full_percent_observed_frames'])

    def test_strict_15ms_boundary_90percent_full_and_1_5missing(self):
        raw = continuous(30)
        spans = [{'index': 0, 'start': 0, 'end': 1880, 'timestamp_ns': 0},
                 {'index': 1, 'start': 1880, 'end': 3840, 'timestamp_ns': 15_000_000},
                 {'index': 2, 'start': 3840, 'end': 5640, 'timestamp_ns': 150_000_000}]
        t = ts.analyze(raw, spans, frame_baseline_bytes=2000,
                       frame_cadence_ns=100_000_000)['completion_frames']
        self.assertEqual(len(t['frames']), 2)
        self.assertEqual(t['frame_counts']['FULL'], 2)
        self.assertEqual(t['frame_counts']['MISSING'], 1)
        spans[-1]['timestamp_ns'] -= 1
        t = ts.analyze(raw, spans, frame_cadence_ns=100_000_000)['completion_frames']
        self.assertEqual(t['frame_counts']['MISSING'], 0)

    def test_64k_19ms_batching_does_not_become_one_frame_per_completion(self):
        raw, spans = timed_bursts([540672] * 5)
        # Rebatch the same bytes in 64KiB completions at 27.5 Mb/s within each burst.
        big = []
        offset = 0
        for frame in range(5):
            for at in range(0, 540672, 65536):
                length = min(65536, 540672 - at)
                big.append({'index': len(big), 'start': offset, 'end': offset + length,
                            'timestamp_ns': frame * 242_000_000 + round((at + length) * 8e9 / 27_500_000)})
                offset += length
        r = ts.analyze(raw, big, frame_baseline_bytes=540672, frame_cadence_ns=242_000_000)['completion_frames']
        self.assertEqual(r['status'], 'UNKNOWN_CHUNK_TIMING_EXCEEDS_15MS')
        self.assertEqual(r['frames'], [])
        self.assertIsNone(r['frame_counts'])
        self.assertIsNone(r['time_to_first_full_ms'])
        self.assertEqual(r['total']['packets'], len(raw) // 188)
        self.assertIsNone(r['burst_rate']['median_bps'])
        self.assertAlmostEqual(r['burst_rate']['completion_cadence_rate_bps'], 27_500_000, delta=10)

    def test_onsets_not_idle_gaps_determine_missing(self):
        raw, spans = timed_bursts([262144] * 4, [0, 1, 2, 4])
        r = ts.analyze(raw, spans, frame_cadence_ns=242_000_000)['completion_frames']
        self.assertTrue(all(160 < f['gap_before_ms'] < 180 for f in r['frames'][1:3]))
        self.assertEqual(r['frame_counts']['MISSING'], 1)
        self.assertGreater(r['frames'][3]['gap_before_ms'], 400)

    def test_timing_and_options_validation_raw_backward_compatible(self):
        raw = continuous(20)
        self.assertIn('UNKNOWN', ts.analyze(raw, ts.raw_spans(len(raw), 4096))['completion_frames']['status'])
        for kwargs in ({'frame_baseline_bytes': 0}, {'frame_cadence_ns': -1}, {'frame_baseline_bytes': 1880}):
            with self.assertRaises(ValueError):
                ts.analyze(raw, **kwargs)
        spans = [{'index': 0, 'start': 0, 'end': 1880, 'timestamp_ns': 1},
                 {'index': 1, 'start': 1880, 'end': len(raw), 'timestamp_ns': 0}]
        with self.assertRaises(ValueError):
            ts.analyze(raw, spans)

    def test_pcr_per_pid_windows_split_on_discontinuity_and_no_cross_join(self):
        raw = PCRTests().stream(count=8000, discontinuity=4000)
        r = ts.analyze(raw, barriers=[6000 * 188])
        p = r['pcr']['by_pid'][0]
        self.assertEqual((p['pid'], p['eligible_windows'], p['windows']), (100, 3, 3))
        self.assertAlmostEqual(p['median_bitrate_bps'], 27_500_000, delta=5)
        self.assertEqual([w['first_offset'] for w in r['pcr']['windows']], [0, 4000 * 188, 6000 * 188])
        self.assertTrue(all(w['last_offset'] >= w['first_offset'] for w in r['pcr']['windows']))


class MonitorTests(unittest.TestCase):
    def test_after5s_lock_snr_statistics_and_unknown_ber_per(self):
        rows = [{'clock': 'CLOCK_MONOTONIC', 'start_ns': n * 1_000_000_000,
                 'end_ns': n * 1_000_000_000 + 10, 'lock': 'LOCKED' if n < 6 else 'LOST',
                 'snr_db': 20 + n, 'snr_db_curve': 20.5 + n, 'ber': None, 'per': None,
                 'raw': [{'bank': 22, 'register': 64, 'hex': '0000'}], 'errors': []} for n in (1, 5, 6)]
        r = ts.monitor_report(encode_audit(rows), {'clock': 'CLOCK_MONOTONIC', 'origin_ns': 0, 'end_ns': 7_000_000_000})
        a = r['after_5s']['statistics']
        self.assertEqual(a['samples'], 2)
        self.assertEqual(a['lock'], {'LOCKED': 1, 'LOST': 1})
        self.assertEqual((a['metrics']['snr_db']['min'], a['metrics']['snr_db']['median'], a['metrics']['snr_db']['max']), (25, 25, 25))
        self.assertIsNone(a['metrics']['ber']['median'])
        self.assertIsNone(a['metrics']['per']['min'])
        self.assertIn('UNKNOWN', a['metrics']['ber']['status'])

    def test_b4_monitor_schema_summary_and_failure_not_qualified_snr(self):
        rows = [{'schema': 'TBS5530-B4-MONITOR/v1', 'clock': 'CLOCK_MONOTONIC',
                 'start_ns': 5_000_000_000, 'end_ns': 5_000_000_100, 'timestamp_ns': 5_000_000_100,
                 'lock': 'LOCKED', 'snr_raw': 300, 'snr_db': 13.3, 'ber': None, 'per': None,
                 'cleanup_succeeded': True, 'stop_for_lock_loss': False, 'reads': [], 'errors': []},
                {'summary': True, 'samples': 1, 'monitor_failure': None, 'tuner_rssi': False}]
        meta = {'clock': 'CLOCK_MONOTONIC', 'origin_ns': 0, 'end_ns': 6_000_000_000}
        r = ts.monitor_report(encode_audit(rows), meta)
        self.assertEqual(r['ignored_rows'], 0)
        self.assertEqual(r['summaries'], 1)
        self.assertEqual(r['after_5s']['statistics']['metrics']['snr_db']['median'], 13.3)
        rows[-1]['monitor_failure'] = 'failed'
        self.assertEqual(ts.monitor_report(encode_audit(rows), meta)['after_5s']['status'], 'UNKNOWN_MONITOR_FAILURE')

    def test_no_cross_host_clock_alignment_or_fabricated_zero(self):
        source = encode_audit([{'clock': 'CLOCK_MONOTONIC', 'startNanos': 0, 'endNanos': 10,
                                'metrics': {'lock': 'LOCKED', 'officialCnrDb': 27.25}},
                               {'clock': 'wallclock', 'start_ns': 0, 'end_ns': 1}])
        r = ts.monitor_report(source)
        self.assertEqual(r['ignored_rows'], 1)
        self.assertEqual(r['total']['metrics']['snr_db']['median'], 27.25)
        self.assertIsNone(r['after_5s']['statistics'])
        self.assertEqual(r['after_5s']['status'], 'UNKNOWN_CLOCK_ALIGNMENT')
        r = ts.monitor_report(source, {'clock': 'CLOCK_MONOTONIC', 'origin_ns': 0, 'end_ns': 2_000_000_000})
        self.assertIn('SHORTER', r['after_5s']['status'])
        self.assertEqual(ts.monitor_report()['status'], 'UNKNOWN_NOT_PROVIDED')


class NativeTimingTests(unittest.TestCase):
    def test_native_reap_lengths_origin_drain_and_mapping(self):
        for mapping in (False, True):
            source = encode_audit(native_audit(mappings=mapping))
            spans, barriers, meta = ts.native_completions(source, 3760)
            self.assertEqual([(s['start'], s['end']) for s in spans], [(0, 1880), (1880, 3760)])
            self.assertEqual(barriers, [])
            self.assertEqual(meta['excluded_drain_completions'], 1)
            self.assertEqual(meta['origin_ns'], spans[0]['timestamp_ns'])
            self.assertEqual(meta['submit_begin_ns'], 1_000_000_000)
            self.assertIsNone(meta['native_begin_ns'])
            self.assertEqual(meta['writer_mapping'], 'VERIFIED' if mapping else 'LEGACY_LENGTH_BINDING_ONLY')
            r = ts.analyze(continuous(20), spans, timing_metadata=meta)['completion_frames']
            self.assertIn('not_kernel', r['timestamp_kind'])
            self.assertLess(r['observed_duration_ms'], 10)

    def test_first_data_delay_500ms_does_not_start_after5s_early(self):
        rows = native_audit(lengths=(1880, 1880, 1880), mappings=True)
        events = [r for r in rows if 'kind' in r]
        times = [(1_000_000_000, 1_000_001_000), (1_499_999_000, 1_500_000_000),
                 (1_500_001_000, 1_500_002_000), (6_249_999_000, 6_250_000_000),
                 (6_250_001_000, 6_250_002_000), (6_499_999_000, 6_500_000_000),
                 (6_600_000_000, 6_600_001_000), (6_600_002_000, 6_600_003_000)]
        for event, (before, after) in zip(events, times):
            event.update(before_ns=before, after_ns=after)
        reaps = {r['sequence']: r['after_ns'] for r in events if r['kind'] == 2 and r['urb_status'] == 0}
        for row in rows:
            if row.get('mapping'):
                row['reap_ns'] = reaps[row['sequence']]
        rows[-1]['native_begin_ns'] = 900_000_000
        spans, barriers, meta = ts.native_completions(encode_audit(rows), 5640)
        r = ts.analyze(continuous(30), spans, barriers, frame_baseline_bytes=1880,
                       timing_metadata=meta)['completion_frames']
        self.assertEqual(r['origin_ns'], 1_500_000_000)
        self.assertEqual(r['origin_basis'], 'first_successful_captured_data_completion')
        self.assertEqual(r['time_origin_version'], 'FIRST_SUCCESSFUL_CAPTURED_COMPLETION/v2')
        self.assertEqual(r['time_to_first_full_ms'], 0)
        self.assertEqual(r['observed_duration_ms'], 5100)
        self.assertEqual(r['after_5s']['metrics']['packets'], 10)
        self.assertEqual(r['after_5s']['metrics']['bytes'], 1880)
        self.assertEqual(meta['submit_begin_ns'], 1_000_000_000)
        self.assertEqual(meta['native_begin_ns'], 900_000_000)
        self.assertEqual(meta['first_data_delay_ns'], 500_000_000)
        self.assertEqual(meta['submit_to_end_ns'], 5_600_000_000)
        supplied_submit_origin = {**meta, 'origin_ns': meta['submit_begin_ns']}
        direct = ts.analyze(continuous(30), spans, frame_baseline_bytes=1880,
                            timing_metadata=supplied_submit_origin)['completion_frames']
        self.assertEqual(direct['origin_ns'], spans[0]['timestamp_ns'])
        self.assertEqual(direct['after_5s']['metrics']['packets'], 10)
        monitor = [{'clock': 'CLOCK_MONOTONIC', 'start_ns': start, 'end_ns': start + 100,
                    'lock': 'LOCKED', 'snr_db': snr} for start, snr in ((6_250_000_000, 20), (6_500_000_000, 21))]
        after = ts.monitor_report(encode_audit(monitor), meta)['after_5s']['statistics']
        self.assertEqual(after['samples'], 1)
        self.assertEqual(after['metrics']['snr_db']['median'], 21)

    def test_native_rejects_drops_failure_missing_audit_wrong_size_and_bad_clock(self):
        for change in ('drop', 'failure', 'length', 'clock', 'event', 'mapping', 'missing_summary', 'extra_summary', 'run'):
            rows = native_audit(mappings=True)
            if change == 'drop': rows[-1]['writer_drop_bytes'] = 1
            elif change == 'failure': rows[-1]['writer_failure'] = 'disk failure'
            elif change == 'length': rows[1]['actual_length'] = 1800
            elif change == 'clock': rows[1]['clock'] = 'wallclock'
            elif change == 'event': rows[1]['event'] = 99
            elif change == 'mapping': rows[2]['raw_offset'] = None
            elif change == 'missing_summary': rows.pop()
            elif change == 'extra_summary': rows.append(rows[-1].copy())
            elif change == 'run': rows[0]['run_id'] = 'different'
            with self.subTest(change=change), self.assertRaises(ValueError):
                ts.native_completions(encode_audit(rows), 3760)

    def test_b4_mapping_caps_last_completion_and_omits_stop_tail(self):
        rows = native_audit(lengths=(1880, 1880, 1880), mappings=True)
        mappings = [r for r in rows if r.get('mapping')]
        mappings[1]['length'] = 1000
        rows.remove(mappings[2])
        rows[-1].update(delivered_bytes=2880, writer_written_bytes=2880, raw_mapping_valid=True)
        spans, _, meta = ts.native_completions(encode_audit(rows), 2880)
        self.assertEqual([s['end'] for s in spans], [1880, 2880])
        self.assertEqual(spans[-1]['completion_actual_length'], 1880)
        self.assertEqual(meta['uncaptured_stop_tail_completions'], 1)
        # A missing middle mapping is never silently collapsed into contiguous data.
        rows = native_audit(lengths=(1880, 1880, 1880), mappings=True)
        rows = [r for r in rows if not (r.get('mapping') and r['sequence'] == 2)]
        rows[-1].update(delivered_bytes=3760, writer_written_bytes=3760)
        with self.assertRaises(ValueError):
            ts.native_completions(encode_audit(rows), 3760)

    def test_b4_zero_byte_stop_is_reviewable_without_frame_qualification(self):
        rows = native_audit(lengths=(1880,))
        rows[-1].update(delivered_bytes=0, writer_written_bytes=0, raw_mapping_valid=True)
        spans, barriers, meta = ts.native_completions(encode_audit(rows), 0)
        self.assertEqual(spans, [])
        self.assertEqual(meta['uncaptured_stop_tail_completions'], 1)
        self.assertIsNone(meta['origin_ns'])
        self.assertEqual(meta['submit_begin_ns'], 1_000_000_000)
        monitor = encode_audit([{'clock': 'CLOCK_MONOTONIC', 'start_ns': 0, 'end_ns': 1, 'lock': 'UNKNOWN'}])
        self.assertEqual(ts.monitor_report(monitor, meta)['after_5s']['status'], 'UNKNOWN_CLOCK_ALIGNMENT')
        r = ts.analyze(b'', spans, barriers, frame_baseline_bytes=540672, timing_metadata=meta)
        self.assertEqual(r['stream_bytes'], 0)
        self.assertIn('UNKNOWN', r['completion_frames']['status'])
        self.assertIsNone(r['completion_frames']['total'])

    def test_b4_drop_mapping_keeps_raw_offsets_barriers_and_unknown_cadence(self):
        rows = native_audit(lengths=(1880, 1880, 1880), mappings=True)
        mappings = [r for r in rows if r.get('mapping')]
        mappings[1].update(dropped=True, raw_offset=None)
        mappings[2]['raw_offset'] = 1880
        rows[-1].update(delivered_bytes=5640, writer_written_bytes=3760, writer_drop_bytes=1880,
                        raw_mapping_valid=False)
        spans, barriers, meta = ts.native_completions(encode_audit(rows), 3760)
        self.assertEqual([(s['start'], s['end']) for s in spans], [(0, 1880), (1880, 3760)])
        self.assertEqual(barriers, [1880])
        self.assertEqual(meta['writer']['writer_drop_bytes'], 1880)
        r = ts.analyze(continuous(20), spans, barriers, frame_baseline_bytes=1880, timing_metadata=meta)
        self.assertEqual(r['sync']['segment_count'], 2)
        self.assertEqual(r['completion_frames']['status'], 'UNKNOWN_WRITER_DROP_OR_MAPPING_INVALID')
        self.assertIsNone(r['completion_frames']['frame_counts'])
        self.assertIsNone(r['completion_frames']['time_to_first_full_ms'])

    def test_native_failed_completion_barrier_before_drain_not_cancel_error(self):
        rows = native_audit()
        rows[1]['urb_status'] = -75
        rows[1]['actual_length'] = 0
        rows[-1]['delivered_bytes'] = rows[-1]['writer_written_bytes'] = 1880
        spans, barriers, meta = ts.native_completions(encode_audit(rows), 1880)
        self.assertEqual(barriers, [0])
        self.assertEqual(len(spans), 1)
        self.assertEqual(len(meta['failed_completions']), 1)
        self.assertEqual(meta['excluded_drain_completions'], 1)

    def test_cli_native_audit_and_frame_baseline(self):
        raw, audit = continuous(20), encode_audit(native_audit())
        output = io.StringIO()
        def read(path):
            return audit if path.suffix == '.jsonl' else raw
        with patch.object(Path, 'read_bytes', read), contextlib.redirect_stdout(output):
            self.assertEqual(ts.main(['synthetic.ts', '--native-audit', 'audit.jsonl', '--frame-baseline-bytes', '3760']), 0)
        result = json.loads(output.getvalue())
        self.assertEqual(result['completion_frames']['frame_counts']['FULL'], 1)
        self.assertEqual(result['input']['native_audit']['sha256'], ts.sha256(audit))
        for args in (['--frame-cadence-ms', 'nan'], ['--frame-cadence-ms', 'inf'],
                     ['--native-audit', 'audit.jsonl', '--urb-size', '4096']):
            with patch.object(Path, 'read_bytes', read), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(ts.main(['synthetic.ts', *args]), 2)


if __name__ == '__main__':
    unittest.main()
