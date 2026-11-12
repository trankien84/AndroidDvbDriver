"""Offline MPEG-TS / classic USBPcap analysis, using Python 3 stdlib only."""
from __future__ import annotations

import argparse
from bisect import bisect_right
from collections import Counter
import hashlib
import json
from pathlib import Path
import statistics
import struct
import sys

PACKET = 188
PCR_CLOCK = 27_000_000
PCR_WRAP = (1 << 33) * 300


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def crc32_mpeg(data):
    crc = 0xFFFFFFFF
    for byte in data:
        crc ^= byte << 24
        for _ in range(8):
            crc = ((crc << 1) ^ (0x04C11DB7 if crc & 0x80000000 else 0)) & 0xFFFFFFFF
    return crc


def extract_usbpcap(data, bus=None, device=None):
    """Port the wire layout/strict length checks from observation/Verify-Capture.cjs.

    No Node dependency: accept capture-start orphan completions, but disclose them.
    Never infer a device identity from the endpoint alone.
    """
    magics = {b'\xd4\xc3\xb2\xa1': ('<', 1_000_000),
              b'\xa1\xb2\xc3\xd4': ('>', 1_000_000),
              b'\x4d\x3c\xb2\xa1': ('<', 1_000_000_000),
              b'\xa1\xb2\x3c\x4d': ('>', 1_000_000_000)}
    data = bytes(data)
    if len(data) < 24 or data[:4] not in magics:
        raise ValueError('Expected complete classic pcap header (not pcapng)')
    endian, scale = magics[data[:4]]
    major, minor, _, _, snaplen, link = struct.unpack_from(endian + 'HHIIII', data, 4)
    if (major, minor) != (2, 4) or link != 249 or snaplen < 27:
        raise ValueError('Expected pcap 2.4 USBPcap linktype 249 and valid snaplen')
    if bus is not None and not 0 <= bus <= 65535:
        raise ValueError('Bus outside uint16 range')
    if device is not None and not 1 <= device <= 127:
        raise ValueError('Device outside USB address range 1..127')
    at, frame = 24, 0
    pending, rows, identities = {}, [], {}
    while at < len(data):
        if len(data) - at < 16:
            raise ValueError('Truncated pcap record header')
        sec, fraction, captured, wire = struct.unpack_from(endian + 'IIII', data, at)
        at += 16
        frame += 1
        if fraction >= scale or captured != wire or captured > snaplen or captured > len(data) - at:
            raise ValueError('Truncated or malformed pcap record at frame %d' % frame)
        b = data[at:at + captured]
        at += captured
        if len(b) < 27:
            raise ValueError('Short USBPcap header at frame %d' % frame)
        h, irp, status, function, info, rb, rd, ep, transfer, length = struct.unpack_from('<HQIHBHHBBI', b)
        if h < 27 or h > len(b) or h + length != len(b):
            raise ValueError('USBPcap header/payload length mismatch at frame %d' % frame)
        if transfer == 3 and h != 27:
            raise ValueError('Unexpected USBPcap Bulk header length')
        owner = (rb, rd)
        key = (rb, rd, ep, transfer, irp)
        payload = b[h:]
        matched = None
        if not info & 1:
            if key in pending:
                raise ValueError('Overlapping USBPcap IRP submission')
            pending[key] = (frame, payload)
        else:
            matched = pending.pop(key, None)
            if transfer == 2 and status == 0 and matched:
                setup = matched[1]
                if (len(setup) >= 8 and setup[:4] == b'\x80\x06\x00\x01'
                        and len(payload) >= 18 and payload[:2] == b'\x12\x01'):
                    identity = payload[8:14].hex()
                    identities.setdefault(owner, set()).add(identity)
            if transfer == 3 and ep == 0x82:
                rows.append({'frame': frame, 'submit_frame': matched[0] if matched else None,
                             'bus': rb, 'device': rd, 'irp_id': hex(irp), 'status': status,
                             'timestamp_ns': sec * 1_000_000_000 + fraction * (1_000_000_000 // scale),
                             'payload': payload})
    scoped = [r for r in rows if (bus is None or r['bus'] == bus)
              and (device is None or r['device'] == device)]
    owners = sorted({(r['bus'], r['device']) for r in scoped if r['status'] == 0 and r['payload']})
    if len(owners) != 1:
        raise ValueError('Ambiguous or absent successful Bulk IN 0x82 owner: %s; use --bus/--device' % owners)
    owner = owners[0]
    if len(identities.get(owner, ())) > 1:
        raise ValueError('Device address reused by different descriptor identities; split capture into epochs')
    chunks, spans, barriers = [], [], []
    offset = 0
    failures, orphans = [], 0
    for row in scoped:
        if (row['bus'], row['device']) != owner:
            continue
        payload = row.pop('payload')
        if row['status'] != 0:
            failures.append({**row, 'captured_bytes': len(payload), 'stream_offset': offset})
            if offset:
                barriers.append(offset)
            continue
        orphans += row['submit_frame'] is None
        if payload:
            chunks.append(payload)
            spans.append({**row, 'index': len(spans), 'start': offset, 'end': offset + len(payload)})
            offset += len(payload)
    raw = b''.join(chunks)
    return raw, spans, sorted(set(barriers)), {
        'format': 'classic_pcap_usbpcap_249', 'frames': frame,
        'selected_bus': owner[0], 'selected_device': owner[1],
        'successful_payload_urbs': len(spans), 'orphan_successful_completions': orphans,
        'failed_completions': failures,
        'pending_bulk_0x82': sum(k[0:2] == owner and k[2:4] == (0x82, 3) for k in pending),
        'order': 'capture completion order; not inferred submission order',
        'identity_limit': 'Same-address identical-device reconnects cannot be proven absent; scope a single acquisition epoch.'}


def raw_spans(size, urb_size):
    if urb_size is None:
        return []
    if urb_size <= 0:
        raise ValueError('--urb-size must be positive')
    return [{'index': n, 'start': at, 'end': min(at + urb_size, size), 'inferred': True}
            for n, at in enumerate(range(0, size, urb_size))]


def sync_segments(data, minimum=5, barriers=(), start_offset=0):
    if minimum < 2:
        raise ValueError('Need at least two sync packets')
    segments = []
    limits = sorted(set([start_offset, *(b for b in barriers if start_offset < b < len(data)), len(data)]))
    for low, high in zip(limits, limits[1:]):
        at = low
        while at + minimum * PACKET <= high:
            start = data.find(b'G', at, high)
            if start < 0:
                break
            end = start
            while end + PACKET <= high and data[end] == 0x47:
                end += PACKET
            if (end - start) // PACKET >= minimum:
                if segments and start < segments[-1]['end']:
                    previous = segments[-1]
                    previous['end'] = previous['start'] + ((start - previous['start']) // PACKET) * PACKET
                    previous['packets'] = (previous['end'] - previous['start']) // PACKET
                    if previous['packets'] < minimum:
                        segments.pop()
                segments.append({'index': len(segments), 'start': start, 'end': end,
                                 'packets': (end - start) // PACKET})
                at = end if end == high else end - PACKET + 1
            else:
                at = start + 1
    return segments


def parse_packet(packet):
    pid = ((packet[1] & 31) << 8) | packet[2]
    afc = (packet[3] >> 4) & 3
    r = {'pid': pid, 'cc': packet[3] & 15, 'tei': bool(packet[1] & 128),
         'pusi': bool(packet[1] & 64), 'scrambled': packet[3] >> 6,
         'has_payload': bool(afc & 1), 'discontinuity': False, 'pcr': None,
         'invalid': None, 'pcr_error': None, 'payload': b'', 'duplicate_key': packet}
    if afc == 0:
        r['invalid'] = 'reserved adaptation_field_control'
        return r
    payload_start = 4
    if afc & 2:
        length = packet[4]
        if (afc == 2 and length != 183) or (afc == 3 and length > 182):
            r['invalid'] = 'invalid adaptation length'
            return r
        payload_start = 5 + length
        if length:
            r['discontinuity'] = bool(packet[5] & 128)
            if packet[5] & 16:
                if length < 7:
                    r['invalid'] = 'short PCR adaptation'
                    return r
                p = packet[6:12]
                base = (p[0] << 25) | (p[1] << 17) | (p[2] << 9) | (p[3] << 1) | (p[4] >> 7)
                ext = ((p[4] & 1) << 8) | p[5]
                if ext >= 300:
                    r['pcr_error'] = 'invalid PCR extension'
                else:
                    r['pcr'] = base * 300 + ext
                    if p[4] & 0x7E != 0x7E:
                        r['pcr_error'] = 'invalid PCR reserved bits'
                # A legal duplicate may carry an updated PCR, but no other changes.
                r['duplicate_key'] = packet[:6] + b'\x00' * 6 + packet[12:]
    if r['has_payload']:
        r['payload'] = packet[payload_start:]
    return r


class Continuity:
    def __init__(self):
        self.last = {}
        self.counts = Counter()
        self.by_pid = {}
        self.errors = []

    def feed(self, p, segment, offset):
        pid = p['pid']
        if pid == 0x1FFF:
            return 'null'
        if p['invalid'] or p['tei']:
            self.last.pop(pid, None)
            return 'untrusted'
        old = self.last.get(pid)
        duplicate, error = False, False
        repeats = 0
        if old and not p['discontinuity']:
            prev, prev_segment, prev_offset, repeats = old
            scope = 'within' if prev_segment == segment else 'between'
            self.counts[scope + '_comparisons'] += 1
            counts = self.by_pid.setdefault(pid, Counter())
            counts[scope + '_comparisons'] += 1
            expected = (prev['cc'] + int(p['has_payload'])) & 15
            if (p['has_payload'] and prev['has_payload'] and p['cc'] == prev['cc']
                    and p['duplicate_key'] == prev['duplicate_key'] and repeats == 0):
                duplicate = True
                repeats = 1
                self.counts[scope + '_duplicates'] += 1
            elif p['cc'] != expected:
                error = True
                self.counts[scope + '_errors'] += 1
                counts[scope + '_errors'] += 1
                if len(self.errors) < 1000:
                    self.errors.append({'pid': pid, 'scope': scope, 'offset': offset,
                                        'previous_offset': prev_offset, 'previous_segment': prev_segment,
                                        'segment': segment, 'expected': expected, 'actual': p['cc']})
                repeats = 0
            else:
                repeats = 0
        if p['discontinuity']:
            self.counts['signaled_discontinuities'] += 1
            repeats = 0
        self.last[pid] = (p, segment, offset, repeats)
        return 'duplicate' if duplicate else 'error' if error else 'ok'

    def report(self):
        names = ['within_comparisons', 'between_comparisons', 'within_errors', 'between_errors',
                 'within_duplicates', 'between_duplicates', 'signaled_discontinuities']
        return {**{k: self.counts[k] for k in names}, 'errors': self.errors,
                'error_details_truncated': sum(self.counts[k] for k in ('within_errors', 'between_errors')) > len(self.errors),
                'by_pid': {str(k): dict(v) for k, v in sorted(self.by_pid.items())},
                'note': 'Between segments is a boundary comparison, not proof of packet loss; CC wraps modulo 16.'}


class PSIAssembler:
    def __init__(self, emit):
        self.buffer = bytearray()
        self.emit = emit
        self.errors = 0

    def reset(self):
        self.buffer.clear()

    def consume(self, data, allow_new=True):
        self.buffer.extend(data)
        while self.buffer:
            if self.buffer[0] == 255:
                self.reset()
                break
            if len(self.buffer) < 3:
                break
            length = ((self.buffer[1] & 15) << 8) | self.buffer[2]
            if not 9 <= length <= 1021 or not self.buffer[1] & 128:
                self.errors += 1
                self.reset()
                break
            total = length + 3
            if len(self.buffer) < total:
                break
            section = bytes(self.buffer[:total])
            del self.buffer[:total]
            self.emit(section)
            if not allow_new:
                self.reset()
                break

    def feed(self, payload, pusi):
        if not payload:
            return
        if pusi:
            pointer = payload[0]
            if pointer > len(payload) - 1:
                self.errors += 1
                self.reset()
                return
            if self.buffer:
                self.consume(payload[1:1 + pointer], allow_new=False)
                if self.buffer:
                    self.errors += 1
            self.reset()
            self.consume(payload[1 + pointer:])
        elif self.buffer:
            self.consume(payload, allow_new=False)


def descriptors(data):
    at, rows = 0, []
    while at < len(data):
        if at + 2 > len(data) or at + 2 + data[at + 1] > len(data):
            raise ValueError('Malformed descriptor loop')
        end = at + 2 + data[at + 1]
        rows.append((data[at], data[at + 2:end]))
        at = end
    return rows


def dvb_text(data):
    if not data:
        return ''
    encoding = None
    if data[0] == 0x15:
        encoding, data = 'utf-8', data[1:]
    elif data[0] == 0x11:
        encoding, data = 'utf-16-be', data[1:]
    elif data[0] == 0x10 and len(data) >= 3 and data[1] == 0 and 1 <= data[2] <= 15:
        encoding, data = 'iso8859-%d' % data[2], data[3:]
    elif 1 <= data[0] <= 11 and data[0] != 8:
        encoding, data = 'iso8859-%d' % (data[0] + 4), data[1:]
    if encoding:
        try:
            return data.decode(encoding, errors='replace')
        except LookupError:
            return data.decode('ascii', errors='backslashreplace')
    # Python has no ISO-6937 codec. Keep the bytes auditable; do not claim exact decoding.
    return data.decode('ascii', errors='backslashreplace')


class Tables:
    def __init__(self):
        self.counts = Counter()
        self.assemblers = {}
        self.groups = {}
        self.pat = {}
        self.pmts = {}
        self.services = {}
        self.transport_id = None

    def reset(self):
        for a in self.assemblers.values():
            a.reset()

    def feed(self, p, outcome):
        pid = p['pid']
        if pid not in (0, 0x11) and pid not in self.pat.values():
            return
        a = self.assemblers.setdefault(pid, PSIAssembler(lambda s: self.section(pid, s)))
        if p['invalid'] or p['tei'] or p['scrambled'] or p['discontinuity'] or outcome == 'error':
            a.reset()
        if p['invalid'] or p['tei'] or p['scrambled'] or outcome == 'duplicate':
            return
        if p['has_payload']:
            a.feed(p['payload'], p['pusi'])

    def section(self, pid, s):
        if crc32_mpeg(s) != 0:
            self.counts['crc_errors'] += 1
            return
        tid = s[0]
        if (pid == 0 and tid != 0) or (pid == 0x11 and tid not in (0x42, 0x46)) or (pid not in (0, 0x11) and tid != 2):
            self.counts['unexpected_table_id'] += 1
            return
        if len(s) < 12 or s[6] > s[7]:
            self.counts['malformed_sections'] += 1
            return
        self.counts[{0: 'pat', 2: 'pmt', 0x42: 'sdt_actual', 0x46: 'sdt_other'}[tid] + '_crc_valid_sections'] += 1
        if not s[5] & 1:
            self.counts['not_current_sections'] += 1
            return
        ext, version = int.from_bytes(s[3:5], 'big'), (s[5] >> 1) & 31
        key = (pid, tid, ext)
        group = self.groups.get(key)
        if not group or group['version'] != version or group['last'] != s[7]:
            group = {'version': version, 'last': s[7], 'sections': {}}
            self.groups[key] = group
        group['sections'][s[6]] = s
        if len(group['sections']) != group['last'] + 1:
            return
        sections = [group['sections'][i] for i in range(group['last'] + 1)]
        try:
            self.apply(pid, tid, ext, version, sections)
        except (ValueError, IndexError):
            self.counts['malformed_sections'] += 1

    def apply(self, pid, tid, ext, version, sections):
        if tid == 0:
            programs = {}
            for s in sections:
                if (len(s) - 12) % 4:
                    raise ValueError('PAT loop length')
                for at in range(8, len(s) - 4, 4):
                    program, target = struct.unpack_from('>HH', s, at)
                    if program:
                        programs[program] = target & 0x1FFF
            if self.transport_id != ext:
                self.pmts.clear()
                self.services.clear()
            self.transport_id = ext
            self.pat = programs
            self.pmts = {k: v for k, v in self.pmts.items() if programs.get(k) == v['pmt_pid']}
        elif tid == 2:
            if self.pat.get(ext) != pid:
                self.counts['unmapped_pmt'] += 1
                return
            streams = {}
            pcr_pids = set()
            for s in sections:
                if len(s) < 16:
                    raise ValueError('Short PMT')
                pcr_pids.add(int.from_bytes(s[8:10], 'big') & 0x1FFF)
                info_len = int.from_bytes(s[10:12], 'big') & 0xFFF
                if 12 + info_len > len(s) - 4:
                    raise ValueError('PMT program descriptors outside section')
                descriptors(s[12:12 + info_len])
                at = 12 + info_len
                while at < len(s) - 4:
                    if at + 5 > len(s) - 4:
                        raise ValueError('Short PMT ES')
                    stream_type, target, size = struct.unpack_from('>BHH', s, at)
                    size &= 0xFFF
                    if at + 5 + size > len(s) - 4:
                        raise ValueError('PMT ES descriptors outside section')
                    ds = descriptors(s[at + 5:at + 5 + size])
                    streams[target & 0x1FFF] = {'pid': target & 0x1FFF, 'stream_type': stream_type,
                                                'descriptor_tags': [tag for tag, _ in ds]}
                    at += 5 + size
            if len(pcr_pids) != 1:
                raise ValueError('Inconsistent PMT PCR PID')
            self.pmts[ext] = {'service_id': ext, 'pmt_pid': pid, 'pcr_pid': pcr_pids.pop(),
                              'version': version, 'streams': list(streams.values())}
        elif tid == 0x42:
            services = {}
            for s in sections:
                if len(s) < 15:
                    raise ValueError('Short SDT')
                at = 11
                while at < len(s) - 4:
                    if at + 5 > len(s) - 4:
                        raise ValueError('Short SDT service')
                    sid = int.from_bytes(s[at:at + 2], 'big')
                    size = int.from_bytes(s[at + 3:at + 5], 'big') & 0xFFF
                    end = at + 5 + size
                    if end > len(s) - 4:
                        raise ValueError('SDT descriptors outside section')
                    row = {'service_id': sid, 'transport_stream_id': ext,
                           'original_network_id': int.from_bytes(s[8:10], 'big'),
                           'free_ca': bool(s[at + 3] & 16)}
                    for tag, d in descriptors(s[at + 5:end]):
                        if tag == 0x48:
                            if len(d) < 3 or 2 + d[1] >= len(d):
                                raise ValueError('Short service descriptor')
                            split = 2 + d[1]
                            if split + 1 + d[split] != len(d):
                                raise ValueError('Invalid service name length')
                            row.update(service_type=d[0], provider=dvb_text(d[2:split]),
                                       name=dvb_text(d[split + 1:]), descriptor_hex=d.hex())
                    services[sid] = row
                    at = end
            if self.transport_id is None or ext == self.transport_id:
                self.services = services

    def report(self, observed_pids):
        services = []
        roles = {0: ['PAT'], 0x11: ['SDT/BAT'], 0x1FFF: ['null']}
        for sid, pmt_pid in sorted(self.pat.items()):
            row = {'service_id': sid, 'pmt_pid': pmt_pid, 'pcr_pid': None, 'streams': [],
                   'name': None, 'provider': None, **self.services.get(sid, {}), **self.pmts.get(sid, {})}
            services.append(row)
            roles.setdefault(pmt_pid, []).append('PMT:%d' % sid)
            if row['pcr_pid'] is not None:
                roles.setdefault(row['pcr_pid'], []).append('PCR:%d' % sid)
            for stream in row['streams']:
                roles.setdefault(stream['pid'], []).append('ES:%d/type:0x%02x' % (sid, stream['stream_type']))
        return {'counts': dict(self.counts), 'reassembly_errors': sum(a.errors for a in self.assemblers.values()),
                'transport_stream_id': self.transport_id, 'pat_programs': len(self.pat),
                'pmt_programs': len(self.pmts), 'services': services,
                'pid_roles': {str(pid): roles.get(pid, ['unknown']) for pid in sorted(observed_pids)},
                'text_decoding': 'DVB selectors decoded where supported; default ISO-6937 rendered as ASCII/backslash escapes; descriptor_hex preserves bytes.'}


def analyze(data, spans=(), barriers=(), minimum=5, skip_prefix=0):
    if not 0 <= skip_prefix <= len(data):
        raise ValueError('--skip-prefix must be between zero and stream length')
    segments = sync_segments(data, minimum, barriers, skip_prefix)
    starts = [s['start'] for s in spans]

    def position(offset):
        if not spans or offset >= len(data):
            return None
        i = bisect_right(starts, offset) - 1
        if i < 0 or offset >= spans[i]['end']:
            return None
        return {'urb_index': spans[i]['index'], 'offset_in_urb': offset - spans[i]['start'],
                'frame': spans[i].get('frame')}

    breaks = []
    for left, right in zip(segments, segments[1:]):
        pos = position(left['end'])
        relative = pos['offset_in_urb'] if pos else None
        breaks.append({'expected_sync_offset': left['end'], 'next_sync_offset': right['start'],
                       'skipped_bytes': right['start'] - left['end'],
                       'expected_urb_position': pos, 'next_urb_position': position(right['start']),
                       'distance_from_2048': relative - 2048 if relative is not None else None,
                       'near_2048': abs(relative - 2048) <= PACKET if relative is not None else None,
                       'forced_by_failed_urb': left['end'] in barriers or any(left['end'] <= b <= right['start'] for b in barriers)})
    cc, tables = Continuity(), Tables()
    pids, pcr_runs, pcr_windows = {}, {}, []
    diagnostic_runs, diagnostic_windows = {}, []
    prefix_pids, rest_pids = Counter(), Counter()
    totals = Counter()

    def finish_pcr(key, runs=None, windows=None):
        runs = pcr_runs if runs is None else runs
        windows = pcr_windows if windows is None else windows
        run = runs.pop(key, None)
        if run is None:
            return
        ticks = run['elapsed_ticks']
        elapsed = ticks / PCR_CLOCK
        distance = run['last_packet'] - run['first_packet']
        enough = run['samples'] >= 3 and elapsed >= 0.1 and distance >= 100
        windows.append({**run, 'seconds': elapsed, 'packet_distance': distance,
                            'bitrate_bps': distance * PACKET * 8 * PCR_CLOCK / ticks if enough else None,
                            'status': 'estimate' if enough else 'insufficient_span'})

    for segment in segments:
        tables.reset()
        for runs, windows in ((pcr_runs, pcr_windows), (diagnostic_runs, diagnostic_windows)):
            for key in list(runs):
                finish_pcr(key, runs, windows)
        segment.update(start_urb=position(segment['start']), end_urb=position(segment['end'] - 1))
        for packet_index, offset in enumerate(range(segment['start'], segment['end'], PACKET)):
            p = parse_packet(data[offset:offset + PACKET])
            pid = p['pid']
            row = pids.setdefault(pid, Counter())
            row['packets'] += 1
            row['tei'] += p['tei']
            row['scrambled'] += bool(p['scrambled'])
            row['invalid'] += p['invalid'] is not None
            row['pcr_errors'] += p['pcr_error'] is not None
            totals['pcr_errors'] += p['pcr_error'] is not None
            totals['tei'] += p['tei']
            totals['scrambled'] += bool(p['scrambled'])
            totals['invalid'] += p['invalid'] is not None
            (prefix_pids if offset < 2048 else rest_pids)[pid] += 1
            outcome = cc.feed(p, segment['index'], offset)
            tables.feed(p, outcome)
            key = (segment['index'], pid)
            if p['pcr'] is not None:
                row['pcr_samples'] += 1
            for diagnostic, runs, windows in ((False, pcr_runs, pcr_windows),
                                               (True, diagnostic_runs, diagnostic_windows)):
                untrusted = p['tei'] or p['invalid'] or outcome == 'error'
                if untrusted or (not diagnostic and (p['discontinuity'] or p['pcr_error'])):
                    finish_pcr(key, runs, windows)
                if (p['pcr'] is None or p['tei'] or p['invalid'] or outcome == 'duplicate'
                        or (not diagnostic and p['pcr_error'])):
                    continue
                run = runs.get(key)
                if run:
                    delta = (p['pcr'] - run['last_pcr']) % PCR_WRAP
                    if delta == 0 or delta > PCR_CLOCK * 10:
                        finish_pcr(key, runs, windows)
                        run = None
                if run is None:
                    run = {'segment': segment['index'], 'pid': pid, 'first_packet': packet_index,
                           'last_packet': packet_index, 'first_pcr': p['pcr'], 'last_pcr': p['pcr'],
                           'samples': 1, 'elapsed_ticks': 0, 'signaled_discontinuities': 0,
                           'reserved_bit_errors': 0}
                    runs[key] = run
                else:
                    run['elapsed_ticks'] += delta
                    run['samples'] += 1
                    run['last_packet'], run['last_pcr'] = packet_index, p['pcr']
                run['signaled_discontinuities'] += p['discontinuity']
                run['reserved_bit_errors'] += p['pcr_error'] is not None
    for runs, windows in ((pcr_runs, pcr_windows), (diagnostic_runs, diagnostic_windows)):
        for key in list(runs):
            finish_pcr(key, runs, windows)
    rates = [r['bitrate_bps'] for r in pcr_windows if r['bitrate_bps'] is not None]
    diagnostic_rates = [r['bitrate_bps'] for r in diagnostic_windows if r['bitrate_bps'] is not None]
    prefix_end = min(2048, len(data))
    packet_count = sum(s['packets'] for s in segments)
    trailing = len(data) - segments[-1]['end'] if segments else 0
    incomplete_tail = trailing if segments and 0 < trailing < PACKET and data[segments[-1]['end']] == 0x47 else 0
    return {'schema': 'TS-CAPTURE-ANALYSIS/v1', 'stream_bytes': len(data), 'stream_sha256': sha256(data),
            'analysis_range': {'start': skip_prefix, 'end': len(data), 'skipped_prefix_bytes': skip_prefix,
                               'offset_basis': 'original concatenated raw stream, never rebased',
                               'explicit_skip_only': True},
            'native_queue': {'host_hardware_queue': 'UNKNOWN_NOT_MEASURED',
                             'starvation': 'UNKNOWN_NOT_MEASURED',
                             'caveat': 'submitted minus reaped models application outstanding URBs only; it does not prove host-controller hardware queue depth or hardware starvation.'},
            'sync': {'minimum_run_packets': minimum, 'segments': segments, 'segment_count': len(segments),
                     'breaks': breaks, 'break_count': len(breaks),
                     'near_2048_breaks': sum(b['near_2048'] is True for b in breaks),
                     'near_2048_tolerance_bytes': PACKET, 'packets': packet_count,
                     'unparsed_bytes': len(data) - skip_prefix - packet_count * PACKET,
                     'leading_bytes': segments[0]['start'] - skip_prefix if segments else len(data) - skip_prefix,
                     'trailing_bytes': trailing, 'tail_incomplete_packet_bytes': incomplete_tail,
                     'tail_counts_as_sync_break': False},
            'cc': cc.report(), 'totals': dict(totals),
            'pids': [{'pid': k, 'hex': '0x%04x' % k, **dict(v)} for k, v in sorted(pids.items())],
            'psi': tables.report(pids),
            'pcr': {'windows': pcr_windows, 'median_bitrate_bps': statistics.median(rates) if rates else None,
                    'status': 'estimate' if rates else 'UNKNOWN_INSUFFICIENT_CONTIGUOUS_PCR',
                    'minimum_samples': 3, 'minimum_seconds': 0.1, 'minimum_packet_distance': 100,
                    'caveat': 'PCR-clock estimate within individual sync segments; not USB throughput. Loss/TEI can bias it; no cross-segment PCR join.',
                    'diagnostic_only': {'status': 'NONCONFORMANT_HEURISTIC_NOT_VALIDATED_BITRATE',
                                        'median_bitrate_bps': statistics.median(diagnostic_rates) if diagnostic_rates else None,
                                        'eligible_windows': sum(w['bitrate_bps'] is not None for w in diagnostic_windows),
                                        'windows': diagnostic_windows,
                                        'assumptions': 'Ignores signaled PCR discontinuity and PCR reserved-bit errors, but still splits on TEI, invalid TS header, CC errors and non-monotonic PCR. Never substitute this for the strict estimate.'}},
            'stale_prefix': {'label': 'STALE_PREFIX', 'status': 'HYPOTHESIS_NOT_PROVEN',
                             'range': [0, prefix_end], 'sha256': sha256(data[:prefix_end]),
                             'prefix_packet_starts': sum(prefix_pids.values()),
                             'prefix_pids': sorted(prefix_pids), 'later_pids': sorted(rest_pids),
                             'pid_observation_scope': 'selected analysis range only; skipped prefix bytes are not decoded',
                             'first_break_offset': breaks[0]['expected_sync_offset'] if breaks else None,
                             'action': 'No automatic discard. Prefix observations cannot prove stale FIFO data; only an explicit --skip-prefix excludes initial bytes from analysis.'},
            'urbs': list(spans),
            'limitations': ['188-byte TS only; 192/204-byte layouts are not converted.',
                            'Sync runs are byte-pattern evidence, not proof of valid PSI or a clean transport.',
                            'CC excludes null PID, resets on TEI/invalid packets and honors discontinuity; only one consecutive identical duplicate is permitted.',
                            'PSI resets at sync breaks; only complete CRC-valid current tables are published. Missing tables mean unknown, not absent on air.',
                            'URB offsets for raw input are inferred only when --urb-size is supplied; zero-based offsets and exclusive end positions.']}


def markdown(r):
    s, c, psi = r['sync'], r['cc'], r['psi']
    lines = ['# Phân tích MPEG-TS ngoại tuyến', '',
             '- Input SHA-256: `%s`.' % r['input']['sha256'],
             '- Phạm vi phân tích: [%d, %d); offsets vẫn theo raw gốc, không đổi SHA.' % (r['analysis_range']['start'], r['analysis_range']['end']),
             '- Tail chưa đủ gói: %d byte; không tính là sync break.' % s['tail_incomplete_packet_bytes'],
             '- Stream SHA-256: `%s`; %d byte.' % (r['stream_sha256'], r['stream_bytes']),
             '- Đồng bộ: %d đoạn, %d chỗ đứt, %d gói, %d byte ngoài đoạn.' % (s['segment_count'], s['break_count'], s['packets'], s['unparsed_bytes']),
             '- Chỗ đứt gần byte 2048 của URB (±188): %d.' % s['near_2048_breaks'],
             '- CC trong đoạn: %d lỗi / %d so sánh; giữa đoạn: %d lỗi / %d so sánh.' % (c['within_errors'], c['within_comparisons'], c['between_errors'], c['between_comparisons']),
             '- Gói lặp hợp lệ trong/giữa đoạn: %d/%d; TEI: %d; mã hóa: %d; header lỗi: %d.' % (c['within_duplicates'], c['between_duplicates'], r['totals'].get('tei', 0), r['totals'].get('scrambled', 0), r['totals'].get('invalid', 0)),
             '- PAT: %d chương trình; PMT: %d; lỗi CRC: %d.' % (psi['pat_programs'], psi['pmt_programs'], psi['counts'].get('crc_errors', 0)),
             '- Bitrate PCR chuẩn: %s.' % ('UNKNOWN' if r['pcr']['median_bitrate_bps'] is None else '%.6f Mbps (ước lượng)' % (r['pcr']['median_bitrate_bps'] / 1e6)),
             '- PCR lỗi: %d. Ước lượng chẩn đoán bỏ qua cờ discontinuity/reserved bits: %s; không phải bitrate đã xác nhận.' % (r['totals'].get('pcr_errors', 0), 'UNKNOWN' if r['pcr']['diagnostic_only']['median_bitrate_bps'] is None else '%.6f Mbps' % (r['pcr']['diagnostic_only']['median_bitrate_bps'] / 1e6)), '',
             'CC là bộ đếm thứ tự gói theo PID; TEI là cờ báo gói lỗi. PAT/PMT/SDT mô tả dịch vụ và các luồng con. PCR là đồng hồ trong luồng, không phải phép đo tốc độ USB.', '',
             '## Đoạn đồng bộ', '', '| Đoạn | Start | End (exclusive) | Gói | URB đầu/cuối |', '| --- | --- | --- | --- | --- |']
    for seg in s['segments']:
        lines.append('| %d | %d | %d | %d | %s / %s |' % (seg['index'], seg['start'], seg['end'], seg['packets'], seg['start_urb'], seg['end_urb']))
    lines += ['', '## Chỗ đứt', '', '| Sync dự kiến | Sync tiếp | Byte bỏ qua | Vị trí URB | Lệch 2048 |', '| --- | --- | --- | --- | --- |']
    for b in s['breaks']:
        lines.append('| %d | %d | %d | %s | %s |' % (b['expected_sync_offset'], b['next_sync_offset'], b['skipped_bytes'], b['expected_urb_position'], b['distance_from_2048']))
    lines += ['', '## Dịch vụ và PID', '', '| Service ID | Tên | Provider | PMT PID | PCR PID | ES PIDs |', '| --- | --- | --- | --- | --- | --- |']
    def cell(value):
        return str(value if value is not None else 'UNKNOWN').replace('|', '\\|').replace('\n', ' ').replace('\r', ' ')
    for service in psi['services']:
        lines.append('| %s | %s | %s | %s | %s | %s |' % tuple(cell(x) for x in (service['service_id'], service['name'], service['provider'], service['pmt_pid'], service['pcr_pid'], [s['pid'] for s in service['streams']])) )
    lines += ['', '| PID | Gói | TEI | Mã hóa | Vai trò |', '| --- | --- | --- | --- | --- |']
    for p in r['pids']:
        lines.append('| %s | %d | %d | %d | %s |' % (p['hex'], p['packets'], p['tei'], p['scrambled'], cell(', '.join(psi['pid_roles'][str(p['pid'])]))))
    lines += ['', '## STALE_PREFIX — chỉ là giả thuyết', '',
              'Không tự bỏ 2048 byte đầu hoặc bỏ byte giữa luồng. PID đầu: %s; PID sau: %s. Dữ liệu này chưa chứng minh FIFO cũ.' % (r['stale_prefix']['prefix_pids'], r['stale_prefix']['later_pids']), '',
              '## Giới hạn và dữ liệu chi tiết', '',
              'JSON chứa vị trí URB, so sánh CC, các cửa sổ PCR, thống kê CRC, SHA và nguồn trích xuất. Không nối PCR hoặc PSI qua chỗ mất đồng bộ. Bitrate là ước lượng theo đồng hồ PCR; lỗi/mất gói có thể làm sai lệch.', '',
              'Tên DVB mặc định ISO-6937 chỉ hiển thị ASCII/escape nếu không có selector được hỗ trợ; descriptor_hex giữ nguyên byte.', '',
              'Chênh lệch submitted−reaped chỉ là mô hình số URB ứng dụng chưa thu hồi; không chứng minh hàng đợi phần cứng host hoặc hardware starvation. Analyzer không đo hai đại lượng phần cứng này (UNKNOWN).', '']
    lines.extend('- ' + item for item in r['limitations'])
    return '\n'.join(lines) + '\n'


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('input', type=Path)
    ap.add_argument('--format', choices=('auto', 'raw', 'pcap'), default='auto')
    ap.add_argument('--urb-size', type=int, help='raw only: inferred fixed URB length')
    ap.add_argument('--bus', type=lambda s: int(s, 0))
    ap.add_argument('--device', type=lambda s: int(s, 0))
    ap.add_argument('--min-sync-packets', type=int, default=5)
    ap.add_argument('--skip-prefix', type=int, default=0, help='explicit analysis-only prefix exclusion; retain raw SHA and absolute offsets')
    ap.add_argument('--json', type=Path)
    ap.add_argument('--markdown', type=Path)
    ap.add_argument('--extract-raw', type=Path, help='pcap only; exact concatenated successful payloads, never resynchronized')
    args = ap.parse_args(argv)
    try:
        outputs = [p for p in (args.json, args.markdown, args.extract_raw) if p is not None]
        resolved = [p.resolve() for p in outputs]
        if len(set(resolved)) != len(resolved) or args.input.resolve() in resolved or any(p.exists() for p in outputs):
            raise ValueError('Outputs must be distinct new paths, never the input or existing files')
        source = args.input.read_bytes()
        is_pcap = args.format == 'pcap' or (args.format == 'auto' and (source[:4] in (
            b'\xd4\xc3\xb2\xa1', b'\xa1\xb2\xc3\xd4', b'\x4d\x3c\xb2\xa1', b'\xa1\xb2\x3c\x4d', b'\x0a\x0d\x0d\x0a') or args.input.suffix.lower() in ('.pcap', '.pcapng')))
        if is_pcap:
            if args.urb_size is not None:
                raise ValueError('--urb-size is raw-only; pcap has actual completion lengths')
            data, spans, barriers, extraction = extract_usbpcap(source, args.bus, args.device)
        else:
            if args.bus is not None or args.device is not None or args.extract_raw is not None:
                raise ValueError('--bus/--device/--extract-raw require pcap input')
            data, spans, barriers, extraction = source, raw_spans(len(source), args.urb_size), [], {'format': 'raw'}
        result = analyze(data, spans, barriers, args.min_sync_packets, args.skip_prefix)
        result['input'] = {'path': str(args.input.resolve()), 'bytes': len(source), 'sha256': sha256(source), **extraction}
        encoded = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
        if args.json:
            with args.json.open('x', encoding='utf-8', newline='\n') as f:
                f.write(encoded)
        else:
            sys.stdout.write(encoded)
        if args.markdown:
            with args.markdown.open('x', encoding='utf-8', newline='\n') as f:
                f.write(markdown(result))
        if args.extract_raw:
            with args.extract_raw.open('xb') as f:
                f.write(data)
        return 0
    except (OSError, ValueError) as exc:
        print('ERROR: %s' % exc, file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
