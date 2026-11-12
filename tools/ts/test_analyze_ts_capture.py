"""Synthetic regression fixtures; no capture files, packages, network or hardware."""
import contextlib
import io
from pathlib import Path
import struct
import unittest

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


if __name__ == '__main__':
    unittest.main()
