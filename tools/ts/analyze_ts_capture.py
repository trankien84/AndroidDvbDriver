"""Offline MPEG-TS / classic USBPcap analysis, using Python 3 stdlib only."""
from __future__ import annotations

import argparse
from bisect import bisect_left, bisect_right
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import statistics
import struct
import sys

PACKET = 188
PCR_CLOCK = 27_000_000
PCR_WRAP = (1 << 33) * 300
DATA_ORIGIN_VERSION = 'FIRST_SUCCESSFUL_CAPTURED_COMPLETION/v2'


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


def native_completions(source, stream_size):
    """Bind a complete native audit to raw bytes; never concatenate cancelled data."""
    rows = [json.loads(line) for line in source.decode('utf-8-sig').splitlines() if line.strip()]
    if not rows or any(not isinstance(r, dict) for r in rows):
        raise ValueError('Native audit must contain JSON objects')
    summaries = [r for r in rows if r.get('summary') is True]
    if len(summaries) != 1 or rows[-1] is not summaries[0]:
        raise ValueError('Native audit requires one final summary')
    summary = summaries[0]
    if summary.get('writer_failure'):
        raise ValueError('Native writer failure: queued raw mapping is not trustworthy')
    dropped_bytes = summary.get('writer_drop_bytes', 0)
    if type(dropped_bytes) is not int or dropped_bytes < 0:
        raise ValueError('Native writer drop count must be a nonnegative integer')
    if (summary.get('writer_written_bytes', stream_size) != stream_size
            or summary.get('delivered_bytes') != stream_size + dropped_bytes):
        raise ValueError('Native delivered/written/drop bytes do not match raw input')
    events = [r for r in rows if 'kind' in r]
    mappings = [r for r in rows if r.get('mapping') is True]
    if (('raw_mapping_valid' in summary or dropped_bytes) and not mappings and summary.get('delivered_bytes')):
        raise ValueError('Native B4 writer requires explicit mappings for delivered input')
    if summary.get('raw_mapping_valid') is False and not dropped_bytes:
        raise ValueError('Native writer mapping invalid without an explained drop boundary')
    mapping_contract = bool(mappings) or 'raw_mapping_valid' in summary
    mapping_by_sequence = {}
    for mapping in mappings:
        sequence = mapping.get('sequence')
        if type(sequence) is not int or sequence in mapping_by_sequence:
            raise ValueError('Native writer mapping sequence missing or repeated')
        mapping_by_sequence[sequence] = mapping
    run_ids = [r.get('run_id') for r in rows if 'run_id' in r]
    if (not run_ids or any(not isinstance(v, str) or not v for v in run_ids)
            or len(set(run_ids)) != 1 or not events):
        raise ValueError('Native audit requires exactly one run identity and event epoch')
    spans, barriers, failures = [], [], []
    offset, source_offset, mapped_drops, previous_event, previous_time = 0, 0, 0, 0, -1
    mapped_sequences = []
    drain_ns, origin_ns = None, None
    sequences = set()
    excluded_drain, failed_calls, uncaptured_completions = 0, 0, 0
    native_call_errors = 0
    uncaptured_tail = False
    for r in events:
        event, before, after = r.get('event'), r.get('before_ns'), r.get('after_ns')
        if (any(type(v) is not int for v in (event, before, after))
                or event != previous_event + 1 or before < previous_time or after < before
                or r.get('clock') != 'CLOCK_MONOTONIC' or r.get('endpoint') != 0x82):
            raise ValueError('Native audit has missing/unordered events, invalid clock or endpoint')
        previous_event, previous_time = event, after
        if r['kind'] == 1 and origin_ns is None:
            origin_ns = before
        if (r['kind'] == 1 and drain_ns is None
                and (r.get('syscall_return') != 0 or r.get('syscall_errno') != 0)):
            native_call_errors += 1
        if r['kind'] == 3 and drain_ns is None:
            drain_ns = before
        if r['kind'] != 2:
            continue
        if drain_ns is not None:
            excluded_drain += 1
            continue
        if r.get('syscall_return') != 0 or r.get('syscall_errno') != 0:
            failed_calls += 1
            native_call_errors += r.get('syscall_errno') not in (4, 11)
            continue
        actual, requested, capacity = (r.get(k) for k in ('actual_length', 'requested_length', 'allocated_capacity'))
        if (any(type(v) is not int for v in (actual, requested, capacity))
                or not 0 <= actual <= requested <= capacity or r.get('urb_status') is None):
            raise ValueError('Native completion has invalid lengths or missing status')
        if r['urb_status'] != 0:
            failures.append({'event': event, 'status': r['urb_status'], 'stream_offset': offset})
            barriers.append(offset)
            continue
        if not actual:
            continue
        sequence = r.get('sequence')
        if type(sequence) is not int or sequence in sequences:
            raise ValueError('Native completion sequence missing or repeated')
        sequences.add(sequence)
        captured = actual
        if mapping_contract:
            mapping = mapping_by_sequence.get(sequence)
            if mapping is None:
                uncaptured_completions += 1
                uncaptured_tail = True
                continue
            captured = mapping.get('length')
            is_drop = mapping.get('dropped')
            if (uncaptured_tail or type(is_drop) is not bool
                    or mapping.get('raw_offset') != (None if is_drop else offset)
                    or mapping.get('source_offset') != source_offset
                    or type(captured) is not int or not 0 < captured <= actual
                    or mapping.get('reap_ns') != after):
                raise ValueError('Native writer mapping disagrees with completion/raw offsets')
            mapped_sequences.append(sequence)
            source_offset += captured
            if is_drop:
                mapped_drops += captured
                barriers.append(offset)
                uncaptured_tail = captured < actual
                continue
        else:
            source_offset += captured
        spans.append({'index': len(spans), 'start': offset, 'end': offset + captured,
                      'timestamp_ns': after, 'sequence': sequence, 'event': event,
                      'completion_actual_length': actual,
                      'timestamp_kind': 'userspace_after_reap_not_kernel_completion'})
        offset += captured
        uncaptured_tail = captured < actual
    if (offset != stream_size or source_offset != summary['delivered_bytes']
            or mapped_drops != dropped_bytes or origin_ns is None or (stream_size and not spans)):
        raise ValueError('Native audit payload lengths do not bind to raw input')
    if mappings and [m['sequence'] for m in mappings] != mapped_sequences:
        raise ValueError('Native writer mapping is incomplete or contains non-data completions')
    first_data_ns = spans[0]['timestamp_ns'] if spans else None
    end_ns = drain_ns if drain_ns is not None else spans[-1]['timestamp_ns'] if spans else origin_ns
    native_begin_ns = summary.get('native_begin_ns')
    if type(native_begin_ns) is not int or not 0 <= native_begin_ns <= origin_ns:
        native_begin_ns = None
    return spans, sorted(set(barriers)), {
        'format': 'native_usbfs_stream_jsonl', 'sha256': sha256(source), 'bytes': len(source),
        'timestamp_kind': 'userspace_after_reap_not_kernel_completion', 'clock': 'CLOCK_MONOTONIC',
        'time_origin_version': DATA_ORIGIN_VERSION,
        'origin_ns': first_data_ns,
        'origin_basis': 'first_successful_captured_data_completion' if spans else 'UNKNOWN_NO_CAPTURED_DATA',
        'submit_begin_ns': origin_ns, 'submit_begin_basis': 'first_submit_before_ns',
        'submit_to_end_ns': end_ns - origin_ns,
        'native_begin_ns': native_begin_ns,
        'native_begin_to_end_ns': end_ns - native_begin_ns if native_begin_ns is not None else None,
        'native_begin_status': 'SUMMARY_RECORDED' if native_begin_ns is not None else 'UNKNOWN_NOT_LOGGED',
        'first_data_delay_ns': first_data_ns - origin_ns if first_data_ns is not None else None,
        'end_ns': end_ns,
        'end_basis': 'first_discard_before_ns' if drain_ns is not None else 'last_data_reap_after_ns',
        'data_completions': len(spans), 'excluded_drain_completions': excluded_drain,
        'failed_reap_calls': failed_calls, 'failed_completions': failures,
        'uncaptured_stop_tail_completions': uncaptured_completions,
        'native_data_errors': len(failures) + native_call_errors,
        'drain': {k: summary.get(k) for k in ('stop_reason', 'submitted', 'completed', 'outstanding',
                                            'quarantined', 'drain_confirmed')},
        'cadence_quality_status': 'UNKNOWN_WRITER_DROP_OR_MAPPING_INVALID' if dropped_bytes else None,
        'writer_mapping': 'VERIFIED_WITH_DROPS_NOT_CONTIGUOUS_SOURCE' if dropped_bytes else 'VERIFIED' if mapping_contract else 'LEGACY_LENGTH_BINDING_ONLY',
        'writer': {k: summary.get(k) for k in ('writer_written_bytes', 'writer_drop_bytes',
                                             'writer_failure', 'raw_mapping_valid', 'raw_offset_scope')},
        'identity_limit': 'Audit run identity and lengths verified; legacy raw content has no independent per-completion hash.'}


FRAME_METRICS = ('bytes', 'packets', 'ts_bytes', 'cc_errors', 'cc_comparisons', 'duplicates',
                 'tei_packets', 'null_packets', 'invalid_packets')


def empty_frame_metrics():
    return dict.fromkeys(FRAME_METRICS, 0)


def supported_band(values, low, high, minimum=1, largest=False):
    ordered = sorted(values)
    best, bounds = None, (0, 0)
    for seed in sorted(set(ordered)):
        a, b = bisect_left(ordered, seed * low), bisect_right(ordered, seed * high)
        count = b - a
        if count < minimum:
            continue
        median = (ordered[a + (count - 1) // 2] + ordered[a + count // 2]) / 2
        key = (median, count) if largest else (count, -median)
        if best is None or key > best:
            best, bounds = key, (a, b)
    return ordered[bounds[0]:bounds[1]]


class CompletionFrames:
    """Completion-observed bursts, not demodulator or kernel frame timestamps."""
    def __init__(self, spans, size, skip_prefix, baseline_bytes=None, cadence_ns=None, metadata=None):
        self.spans = spans
        self.frames = []
        self.total = empty_frame_metrics()
        self.after = empty_frame_metrics()
        self.metadata = metadata or {}
        self.available = bool(spans) and all('timestamp_ns' in s for s in spans)
        if baseline_bytes is not None and (type(baseline_bytes) is not int or baseline_bytes <= 0):
            raise ValueError('Frame baseline bytes must be a positive integer')
        if cadence_ns is not None and (type(cadence_ns) is not int or cadence_ns <= 0):
            raise ValueError('Frame cadence must be positive integer nanoseconds')
        self.baseline, self.cadence = baseline_bytes, cadence_ns
        if not self.available:
            if (baseline_bytes is not None or cadence_ns is not None) and 'origin_ns' not in self.metadata:
                raise ValueError('Frame baseline/cadence requires actual completion timestamps')
            return
        previous_end, previous_time = 0, -1
        self.span_frames = []
        for i, s in enumerate(spans):
            t = s['timestamp_ns']
            if (type(t) is not int or t < previous_time or s['start'] != previous_end
                    or s['end'] <= s['start'] or s['end'] > size):
                raise ValueError('Timed spans must cover the raw stream contiguously in timestamp order')
            if not self.frames or t - previous_time > 15_000_000:
                self.frames.append({'index': len(self.frames), 'start': s['start'], 'end': s['end'],
                                    'first_completion_ns': t, 'last_completion_ns': t,
                                    'first_span': i, 'last_span': i, 'metrics': empty_frame_metrics()})
            f = self.frames[-1]
            f.update(end=s['end'], last_completion_ns=t, last_span=i)
            f['metrics']['bytes'] += max(0, s['end'] - max(s['start'], skip_prefix))
            self.span_frames.append(f['index'])
            previous_end, previous_time = s['end'], t
        if previous_end != size:
            raise ValueError('Timed spans do not cover all raw bytes')
        self.starts = [s['start'] for s in spans]
        self.origin = spans[0]['timestamp_ns']
        self.end = self.metadata.get('end_ns', spans[-1]['timestamp_ns'])
        if self.end < spans[-1]['timestamp_ns']:
            raise ValueError('Completion timing end outside observed range')
        self.threshold = self.origin + 5_000_000_000
        self.after_available = self.end >= self.threshold
        self.total['bytes'] = size - skip_prefix
        self.after['bytes'] = sum(max(0, s['end'] - max(s['start'], skip_prefix))
                                  for s in spans if s['timestamp_ns'] >= self.threshold)

    def packet(self, offset, p, outcome, comparisons):
        if not self.available:
            return
        # Assign a crossing packet once, when its final byte has been observed.
        i = bisect_right(self.starts, offset + PACKET - 1) - 1
        f = self.frames[self.span_frames[i]]
        values = {'packets': 1, 'ts_bytes': PACKET, 'cc_errors': int(outcome == 'error'),
                  'cc_comparisons': comparisons, 'duplicates': int(outcome == 'duplicate'),
                  'tei_packets': int(p['tei']), 'null_packets': int(p['pid'] == 0x1FFF),
                  'invalid_packets': int(p['invalid'] is not None)}
        targets = [self.total, f['metrics']]
        if self.spans[i]['timestamp_ns'] >= self.threshold:
            targets.append(self.after)
        for target in targets:
            for key, value in values.items():
                target[key] += value

    def report(self):
        if not self.available:
            return {'schema': 'TS-COMPLETION-FRAMES/v1', 'status': 'UNKNOWN_NO_COMPLETION_TIMESTAMPS',
                    'time_origin_version': DATA_ORIGIN_VERSION, 'origin_ns': None,
                    'origin_basis': 'UNKNOWN_NO_CAPTURED_DATA_COMPLETION',
                    'frames': [], 'total': None, 'after_5s': {'status': 'UNKNOWN_NO_COMPLETION_TIMESTAMPS'}}
        frames = self.frames
        gaps = [b['first_completion_ns'] - a['first_completion_ns'] for a, b in zip(frames, frames[1:])]
        # A dense 10% band rejects isolated short gaps; cadence multiples are not folded blindly.
        band = supported_band(gaps, 0.9, 1.1)
        inferred_cadence = round(statistics.median(band)) if len(band) >= 3 else None
        cadence = self.cadence or inferred_cadence
        candidates = [f['end'] - f['start'] for f in frames[1:-1]
                      if cadence is None or f['last_completion_ns'] - f['first_completion_ns'] < cadence]
        # Only a candidate: a uniformly halved capture cannot establish an absolute FULL baseline.
        large = supported_band(candidates, 0.9, 1 / 0.9, minimum=3, largest=True)
        candidate = statistics.median(large) if large else None
        missing = []
        rates = []
        inference_counts = Counter()
        for i, f in enumerate(frames):
            f['captured_bytes'] = f['end'] - f['start']
            f['start_ms'] = (f['first_completion_ns'] - self.origin) / 1e6
            f['end_ms'] = (f['last_completion_ns'] - self.origin) / 1e6
            f['duration_ms'] = f['end_ms'] - f['start_ms']
            f['capture_edge'] = i in (0, len(frames) - 1)
            f['classification'] = ('UNKNOWN_BASELINE' if self.baseline is None else
                                   'FULL' if f['captured_bytes'] >= 0.9 * self.baseline else 'PARTIAL')
            f['coalesced_frame_possible'] = cadence is not None and f['duration_ms'] * 1e6 >= cadence
            group = self.spans[f['first_span']:f['last_span'] + 1]
            intervals = [b['timestamp_ns'] - a['timestamp_ns'] for a, b in zip(group, group[1:])]
            instant = [(b['end'] - b['start']) * 8e9 / dt for b, dt in zip(group[1:], intervals) if dt > 0]
            duration = group[-1]['timestamp_ns'] - group[0]['timestamp_ns']
            f['burst_bitrate_bps'] = ((f['captured_bytes'] - (group[0]['end'] - group[0]['start'])) * 8e9 / duration
                                      if duration > 0 else None)
            if f['burst_bitrate_bps'] is not None:
                rates.append(f['burst_bitrate_bps'])
            f['gap_before_ms'] = ((group[0]['timestamp_ns'] - self.spans[f['first_span'] - 1]['timestamp_ns']) / 1e6
                                  if i else None)
            fast = 0
            for dt in intervals[:8]:
                if dt >= 200_000:
                    break
                fast += 1
            median_rate = statistics.median(instant) if instant else None
            steady_fraction = (sum(abs(v / median_rate - 1) <= 0.2 for v in instant) / len(intervals)
                               if intervals and median_rate else 0)
            inference = ('UNKNOWN_CAPTURE_START' if not i else 'HOST_DELAYED_CATCHUP_INFERRED' if fast >= 3 else
                         'SOURCE_LIKE_INFERRED' if len(intervals) >= 3 and steady_fraction >= 0.8 else
                         'UNKNOWN_IRREGULAR_OR_INSUFFICIENT')
            f['gap_inference'] = {'label': inference, 'initial_intervals_below_0_2ms': fast,
                                  'steady_rate_fraction_within_20_percent': steady_fraction}
            inference_counts[inference] += 1
            if i and cadence and gaps[i - 1] >= 1.5 * cadence:
                count = max(1, round(gaps[i - 1] / cadence) - 1)
                left = frames[i - 1]
                if not left['coalesced_frame_possible']:
                    missing.append({'classification': 'MISSING', 'evidence': 'INFERRED_CADENCE_SLOTS_NOT_PROVEN_LOSS',
                                    'after_frame': i - 1, 'before_frame': i, 'count': count,
                                    'start_to_start_ms': gaps[i - 1] / 1e6,
                                    'expected_start_ms': [(left['first_completion_ns'] + n * cadence - self.origin) / 1e6
                                                          for n in range(1, count + 1)]})
        counts = dict(Counter(f['classification'] for f in frames))
        counts['MISSING'] = sum(m['count'] for m in missing)
        after_frames = [f for f in frames if f['first_completion_ns'] >= self.threshold]
        after_missing = sum(t >= 5000 for m in missing for t in m['expected_start_ms'])
        full_after = sum(f['classification'] == 'FULL' for f in after_frames)
        first_full = next((f for f in frames if f['classification'] == 'FULL'), None)
        after = {'status': 'OBSERVED' if self.after_available else 'UNKNOWN_CAPTURE_SHORTER_THAN_5S',
                 'metrics': self.after if self.after_available else None,
                 'frame_counts': dict(Counter(f['classification'] for f in after_frames)) if self.after_available else None,
                 'missing_frames': after_missing if self.after_available else None,
                 'straddling_frames_excluded_from_percentage': sum(f['first_completion_ns'] < self.threshold <= f['last_completion_ns'] for f in frames),
                 'full_percent_observed_frames': (100 * full_after / len(after_frames)
                                                   if self.after_available and self.baseline and after_frames else None),
                 'full_percent_including_missing': (100 * full_after / (len(after_frames) + after_missing)
                                                     if self.after_available and self.baseline and len(after_frames) + after_missing else None)}
        interval_rates = [(b['end'] - b['start']) * 8e9 / (b['timestamp_ns'] - a['timestamp_ns'])
                          for a, b in zip(self.spans, self.spans[1:]) if b['timestamp_ns'] > a['timestamp_ns']]
        rate_band = supported_band(interval_rates, 0.8, 1.2)
        cadence_rate = statistics.median(rate_band) if len(rate_band) >= 3 else None
        median_chunk = statistics.median(s['end'] - s['start'] for s in self.spans)
        chunk_incompatible = cadence_rate is not None and median_chunk * 8e9 / cadence_rate > 15_000_000
        grouping_status = (self.metadata.get('cadence_quality_status')
                           or ('UNKNOWN_CHUNK_TIMING_EXCEEDS_15MS' if chunk_incompatible else '15MS_GAP_COMPATIBLE'))
        incompatible = grouping_status != '15MS_GAP_COMPATIBLE'
        if incompatible:
            after.update(frame_counts=None, missing_frames=None, full_percent_observed_frames=None,
                         full_percent_including_missing=None, straddling_frames_excluded_from_percentage=None,
                         grouping_status=grouping_status)
        return {'schema': 'TS-COMPLETION-FRAMES/v1',
                'status': grouping_status if incompatible else 'OBSERVED_COMPLETION_TIMING',
                'timestamp_kind': self.metadata.get('timestamp_kind', 'usbpcap_host_capture_completion_not_bus_signaling'),
                'time_origin_version': DATA_ORIGIN_VERSION,
                'origin_ns': self.origin, 'origin_basis': 'first_successful_captured_data_completion',
                'observed_duration_ms': (self.end - self.origin) / 1e6,
                'algorithm': {'frame_boundary_gap_ms_strictly_greater_than': 15, 'full_fraction': 0.9,
                              'missing_start_gap_cadences_at_least': 1.5, 'host_catchup_interval_ms_strictly_less_than': 0.2,
                              'host_catchup_minimum_initial_intervals': 3,
                              'packet_assignment': 'completion containing final packet byte; bytes clipped by analysis_range',
                              'after_5s': 'origin is the first successful captured data completion on both hosts, independent of submit/native begin or skip-prefix; completion timestamps >= origin+5s; frame percentages use start>=5s, exclude straddlers',
                              'baseline_rule': 'FULL requires explicit independent baseline bytes. Candidate = median of largest supported 10% size band (>=3 non-edge bursts shorter than cadence); candidate alone never qualifies FULL.',
                              'cadence_rule': 'explicit cadence or median of densest +/-10% start-gap band with >=3 observations; multiples not automatically reduced'},
                'baseline': {'status': 'EXPLICIT_REFERENCE' if self.baseline else 'UNKNOWN_UNANCHORED_SIZE_CANDIDATE_ONLY',
                             'bytes': self.baseline, 'candidate_large_frame_median_bytes': candidate if not incompatible else None,
                             'candidate_samples': len(large) if not incompatible else 0},
                'cadence': {'ns': cadence if not incompatible or self.cadence else None,
                            'ms': cadence / 1e6 if cadence and (not incompatible or self.cadence) else None,
                            'status': 'EXPLICIT_REFERENCE' if self.cadence else grouping_status if incompatible else 'INFERRED_MODAL_START_GAP' if cadence else 'UNKNOWN_INSUFFICIENT_GAPS'},
                'frame_counts': None if incompatible else counts,
                'frames': [] if incompatible else frames, 'missing': [] if incompatible else missing,
                'total': self.total, 'after_5s': after,
                'grouping': {'status': grouping_status,
                             'median_completion_bytes': median_chunk,
                             'normalized_completion_interval_ms': median_chunk * 8e3 / cadence_rate if cadence_rate else None,
                             'warning': ('Writer drop prevents contiguous-source cadence qualification.' if self.metadata.get('cadence_quality_status') else
                                         'Do not interpret 64KiB/~19ms batching as one physical frame per completion.' if chunk_incompatible else None)},
                'time_to_first_full_ms': (first_full['last_completion_ns'] - self.origin) / 1e6 if first_full and not incompatible else None,
                'first_full_start_ms': first_full['start_ms'] if first_full and not incompatible else None,
                'burst_rate': {'median_bps': statistics.median(rates) if rates and not incompatible else None,
                               'completion_cadence_rate_bps': cadence_rate,
                               'basis': 'sum completion bytes after first completion / (last-first timestamp); separate from PCR multiplex bitrate. Completion cadence rate uses the densest +/-20% adjacent byte-rate band (>=3 samples), not decoded frame timing.'},
                'gap_inference_counts': {} if incompatible else dict(inference_counts),
                'caveats': ['Frame means an observed completion burst, not a decoded DVB-T2 physical frame.',
                            'FULL is relative to the supplied reference median, not proof of error-free or complete RF reception.',
                            'Catchup favors host delay; steady in-burst cadence is only compatible with source pauses. Neither proves a cause without kernel/bus timestamps.',
                            'MISSING counts inferred cadence slots inside the observation window; coalesced prior bursts are not counted as missing. No extrapolation before/after capture.',
                            'Equal-rate reduced frames need an independent baseline; no absolute FULL qualification from a homogeneous half-frame capture.']}


def monitor_report(source=None, timing_metadata=None):
    """Summarize serialized measurements only; never derive BER/PER from raw registers."""
    if source is None:
        return {'schema': 'SIGNAL-MONITOR-SUMMARY/v1', 'status': 'UNKNOWN_NOT_PROVIDED'}
    rows = [json.loads(line) for line in source.decode('utf-8-sig').splitlines() if line.strip()]
    if any(not isinstance(r, dict) for r in rows):
        raise ValueError('Monitor JSONL requires object rows')
    measurements, ignored = [], 0
    summaries = [r for r in rows if r.get('summary') is True]
    monitor_failure = next((r.get('monitor_failure') for r in summaries if r.get('monitor_failure')), None)
    for row in rows:
        if row.get('summary') is True:
            continue
        start, end = row.get('start_ns', row.get('startNanos')), row.get('end_ns', row.get('endNanos'))
        if (type(start) is not int or type(end) is not int or end < start
                or row.get('clock') != 'CLOCK_MONOTONIC'):
            ignored += 1
            continue
        metrics = row.get('metrics', row)
        if not isinstance(metrics, dict):
            raise ValueError('Monitor metrics must be an object')
        measurements.append({'start_ns': start, 'end_ns': end, 'lock': metrics.get('lock', 'UNKNOWN'),
                             'snr_db': metrics.get('snr_db', metrics.get('officialCnrDb')),
                             'snr_db_curve': metrics.get('snr_db_curve', metrics.get('snrDbCurve')),
                             'snr_raw': metrics.get('snr_raw', metrics.get('snrRaw')),
                             'ber': metrics.get('ber'), 'per': metrics.get('per'), 'fer': metrics.get('fer'),
                             'errors': len(row.get('errors') or []),
                             'cleanup_succeeded': row.get('cleanup_succeeded'),
                             'stop_for_lock_loss': row.get('stop_for_lock_loss'),
                             'lock_loss_ns': row.get('lock_loss_ns')})
    def summarize(selected):
        result = {'samples': len(selected), 'lock': dict(Counter(str(r['lock']) for r in selected)),
                  'errors': sum(r['errors'] for r in selected),
                  'cleanup_failure_samples': sum(r['cleanup_succeeded'] is False for r in selected),
                  'stop_for_lock_loss_samples': sum(r['stop_for_lock_loss'] is True for r in selected),
                  'metrics': {}}
        for key in ('snr_db', 'snr_db_curve', 'snr_raw', 'ber', 'per', 'fer'):
            values = [r[key] for r in selected if type(r[key]) in (int, float) and math.isfinite(r[key])
                      and (key not in ('snr_db', 'snr_db_curve') or r['lock'] == 'LOCKED')]
            result['metrics'][key] = {'samples': len(values), 'min': min(values) if values else None,
                                      'median': statistics.median(values) if values else None,
                                      'max': max(values) if values else None,
                                      'status': 'OBSERVED_SERIALIZED_VALUES' if values else 'UNKNOWN_NO_VALID_MEASUREMENT'}
        return result
    metadata = timing_metadata or {}
    aligned = (metadata.get('clock') == 'CLOCK_MONOTONIC'
               and type(metadata.get('origin_ns')) is int and type(metadata.get('end_ns')) is int)
    selected = ([r for r in measurements if metadata['origin_ns'] <= r['start_ns'] <= r['end_ns'] <= metadata['end_ns']]
                if aligned else [])
    enough = aligned and metadata['end_ns'] - metadata['origin_ns'] >= 5_000_000_000
    after = [r for r in selected if r['start_ns'] >= metadata['origin_ns'] + 5_000_000_000] if enough else []
    return {'schema': 'SIGNAL-MONITOR-SUMMARY/v1', 'status': 'OBSERVED_SERIALIZED_VALUES' if measurements else 'UNKNOWN_NO_VALID_SAMPLES',
            'sha256': sha256(source), 'bytes': len(source), 'rows': len(rows), 'ignored_rows': ignored,
            'monitor_failure': monitor_failure, 'summaries': len(summaries),
            'total': summarize(measurements),
            'capture_window': summarize(selected) if aligned else None,
            'after_5s': {'status': 'UNKNOWN_MONITOR_FAILURE' if monitor_failure else
                        'OBSERVED' if enough and after else 'UNKNOWN_NO_ALIGNED_SAMPLES' if enough else
                        'UNKNOWN_CAPTURE_SHORTER_THAN_5S' if aligned else 'UNKNOWN_CLOCK_ALIGNMENT',
                         'statistics': summarize(after) if enough else None},
            'alignment': 'DECLARED_CLOCK_MONOTONIC_SAME_HOST_EPOCH' if aligned else 'UNKNOWN_NO_ANDROID_CAPTURE_EPOCH',
            'selection': 'whole sample must fit the captured-data window; after5s uses sample start>=first successful captured data completion+5s',
            'time_origin_version': metadata.get('time_origin_version', 'UNKNOWN_ORIGIN_VERSION'),
            'units': {'snr_db': 'dB official serialized CNR', 'snr_db_curve': 'dB diagnostic curve',
                      'snr_raw': 'uint16 monitor value', 'ber': 'UNKNOWN_USE_PRODUCER_CONTRACT',
                      'per': 'UNKNOWN_USE_PRODUCER_CONTRACT', 'fer': 'UNKNOWN_USE_PRODUCER_CONTRACT'},
            'caveat': 'No raw-register interpretation. Missing/unproven BER/PER/FER stay null; no zero substitution. Cross-host parity and clock identity require external provenance.'}


def pcr_pid_report(windows, pids):
    rows = []
    for pid, counts in sorted(pids.items()):
        if not counts.get('pcr_samples') and not counts.get('pcr_errors'):
            continue
        selected = [w for w in windows if w['pid'] == pid]
        rates = [w['bitrate_bps'] for w in selected if w['bitrate_bps'] is not None]
        rows.append({'pid': pid, 'status': 'estimate' if rates else 'UNKNOWN_INSUFFICIENT_CONTIGUOUS_PCR',
                     'eligible_windows': len(rates), 'windows': len(selected),
                     'median_bitrate_bps': statistics.median(rates) if rates else None})
    return rows


def analyze(data, spans=(), barriers=(), minimum=5, skip_prefix=0, *,
            frame_baseline_bytes=None, frame_cadence_ns=None, timing_metadata=None):
    if not 0 <= skip_prefix <= len(data):
        raise ValueError('--skip-prefix must be between zero and stream length')
    segments = sync_segments(data, minimum, barriers, skip_prefix)
    frames = CompletionFrames(spans, len(data), skip_prefix, frame_baseline_bytes,
                              frame_cadence_ns, timing_metadata)
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
            previous_comparisons = cc.counts['within_comparisons'] + cc.counts['between_comparisons']
            outcome = cc.feed(p, segment['index'], offset)
            frames.packet(offset, p, outcome,
                          cc.counts['within_comparisons'] + cc.counts['between_comparisons'] - previous_comparisons)
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
                           'last_packet': packet_index, 'first_offset': offset, 'last_offset': offset,
                           'first_pcr': p['pcr'], 'last_pcr': p['pcr'],
                           'samples': 1, 'elapsed_ticks': 0, 'signaled_discontinuities': 0,
                           'reserved_bit_errors': 0}
                    runs[key] = run
                else:
                    run['elapsed_ticks'] += delta
                    run['samples'] += 1
                    run['last_packet'], run['last_pcr'] = packet_index, p['pcr']
                    run['last_offset'] = offset
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
            'completion_frames': frames.report(),
            'cc': cc.report(), 'totals': dict(totals),
            'pids': [{'pid': k, 'hex': '0x%04x' % k, **dict(v)} for k, v in sorted(pids.items())],
            'psi': tables.report(pids),
            'pcr': {'windows': pcr_windows, 'by_pid': pcr_pid_report(pcr_windows, pids),
                    'median_bitrate_bps': statistics.median(rates) if rates else None,
                    'status': 'estimate' if rates else 'UNKNOWN_INSUFFICIENT_CONTIGUOUS_PCR',
                    'minimum_samples': 3, 'minimum_seconds': 0.1, 'minimum_packet_distance': 100,
                    'caveat': 'PCR-clock estimate within individual sync segments; not USB throughput. Loss/TEI can bias it; no cross-segment PCR join.',
                    'diagnostic_only': {'status': 'NONCONFORMANT_HEURISTIC_NOT_VALIDATED_BITRATE',
                                        'median_bitrate_bps': statistics.median(diagnostic_rates) if diagnostic_rates else None,
                                        'eligible_windows': sum(w['bitrate_bps'] is not None for w in diagnostic_windows),
                                        'by_pid': pcr_pid_report(diagnostic_windows, pids),
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
    timing = r.get('completion_frames', {})
    lines += ['', '## Frame theo timestamp completion', '',
              '- Trạng thái: `%s`.' % timing.get('status', 'UNKNOWN')]
    if timing.get('total') is not None:
        baseline, cadence, after = timing['baseline'], timing['cadence'], timing['after_5s']
        lines += ['- Loại timestamp: `%s`; mốc: `%s`; phiên bản `%s`.' % (timing['timestamp_kind'], timing['origin_basis'], timing['time_origin_version']),
                  '- Baseline: `%s`, %s byte; median frame lớn ứng viên: %s byte. Chỉ baseline độc lập mới cho phép FULL.' % (baseline['status'], baseline['bytes'], baseline['candidate_large_frame_median_bytes']),
                  '- Cadence (nhịp bắt đầu burst): %s ms, `%s`; ranh giới gap >15 ms.' % (cadence['ms'], cadence['status']),
                  '- Số frame: `%s`; thời gian xác nhận FULL đầu: %s ms.' % (timing['frame_counts'], timing['time_to_first_full_ms']),
                  '- Toàn bộ: `%s`.' % timing['total'],
                  '- Sau 5 giây: `%s`; metrics: `%s`; FULL%% quan sát / gồm MISSING: %s / %s.' % (after['status'], after['metrics'], after.get('full_percent_observed_frames'), after.get('full_percent_including_missing')),
                  '- Burst rate median: %s bps; nhịp byte completion trong dải đông nhất: %s bps, tách biệt bitrate PCR.' % (timing['burst_rate']['median_bps'], timing['burst_rate']['completion_cadence_rate_bps']),
                  '- Khả năng chia frame: `%s`.' % timing['grouping'],
                  '', 'Khoảng dồn nhiều completion <0,2 ms gợi ý host nhận muộn. Nhịp đều trong burst chỉ tương thích với nguồn tạm ngừng; không chứng minh nguyên nhân khi thiếu kernel/bus timestamps.',
                  '', '| Frame | Start ms | Byte | Gói | CC lỗi / so sánh | TEI | Null | Loại | Gap inference |',
                  '| --- | --- | --- | --- | --- | --- | --- | --- | --- |']
        for f in timing['frames']:
            m = f['metrics']
            lines.append('| %d | %.3f | %d | %d | %d / %d | %d | %d | %s | %s |' % (
                f['index'], f['start_ms'], m['bytes'], m['packets'], m['cc_errors'], m['cc_comparisons'],
                m['tei_packets'], m['null_packets'], f['classification'], f['gap_inference']['label']))
        lines += ['', 'MISSING là số vị trí cadence suy ra, không phải chứng minh mất frame trên sóng; JSON giữ từng khoảng và giới hạn.', '']
    lines += ['', '## PCR từng PID — chỉ cửa sổ liên mạch', '',
              '| PID | Cửa sổ đủ điều kiện / tổng | Bitrate bps | Trạng thái |', '| --- | --- | --- | --- |']
    for p in r['pcr'].get('by_pid', []):
        lines.append('| %d | %d / %d | %s | %s |' % (p['pid'], p['eligible_windows'], p['windows'], p['median_bitrate_bps'], p['status']))
    monitor = r.get('monitor', {})
    lines += ['', '## Monitor tín hiệu và writer', '',
              '- Monitor: `%s`; sau 5 giây: `%s`.' % (monitor.get('status', 'UNKNOWN_NOT_PROVIDED'), monitor.get('after_5s', {})),
              '- Audit thời gian native: submit_begin_ns=%s; native_begin_ns=%s; first_data_delay_ns=%s; submit_to_end_ns=%s. Các mốc này không thay mốc dữ liệu của after5s/FULL; thiếu native_begin là UNKNOWN, không suy từ quyền hoặc submit.' % tuple(cell(r['input'].get('native_audit', {}).get(k)) for k in ('submit_begin_ns', 'native_begin_ns', 'first_data_delay_ns', 'submit_to_end_ns')),
              '- Writer: `%s`. Thiếu trường B3 là UNKNOWN, không tự coi bằng 0.' % r['input'].get('native_audit', {}).get('writer', 'UNKNOWN_NOT_REPORTED'),
              '- BER/PER/FER chưa có nghĩa được xác minh giữ UNKNOWN/null; không suy từ byte register.', '']
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
    ap.add_argument('--monitor', type=Path, help='optional monitor JSONL measurements; no register decoding or cross-host clock alignment')
    ap.add_argument('--native-audit', type=Path, help='raw only: complete native 15-*.jsonl audit; binds actual completion lengths')
    ap.add_argument('--frame-baseline-bytes', type=int, help='independent reference median frame size; required to qualify FULL/PARTIAL')
    ap.add_argument('--frame-cadence-ms', type=float, help='independent reference start-to-start cadence; otherwise inferred with >=3 similar gaps')
    ap.add_argument('--json', type=Path)
    ap.add_argument('--markdown', type=Path)
    ap.add_argument('--extract-raw', type=Path, help='pcap only; exact concatenated successful payloads, never resynchronized')
    args = ap.parse_args(argv)
    try:
        outputs = [p for p in (args.json, args.markdown, args.extract_raw) if p is not None]
        resolved = [p.resolve() for p in outputs]
        if (len(set(resolved)) != len(resolved) or args.input.resolve() in resolved
                or any(p is not None and p.resolve() in resolved for p in (args.native_audit, args.monitor))
                or any(p.exists() for p in outputs)):
            raise ValueError('Outputs must be distinct new paths, never the input or existing files')
        source = args.input.read_bytes()
        timing_metadata = None
        cadence_ns = None
        if args.frame_cadence_ms is not None:
            if not 0 < args.frame_cadence_ms < 1e9:
                raise ValueError('--frame-cadence-ms must be finite and positive')
            cadence_ns = round(args.frame_cadence_ms * 1e6)
        is_pcap = args.format == 'pcap' or (args.format == 'auto' and (source[:4] in (
            b'\xd4\xc3\xb2\xa1', b'\xa1\xb2\xc3\xd4', b'\x4d\x3c\xb2\xa1', b'\xa1\xb2\x3c\x4d', b'\x0a\x0d\x0d\x0a') or args.input.suffix.lower() in ('.pcap', '.pcapng')))
        if is_pcap:
            if args.urb_size is not None or args.native_audit is not None:
                raise ValueError('--urb-size/--native-audit are raw-only; pcap has actual completion lengths')
            data, spans, barriers, extraction = extract_usbpcap(source, args.bus, args.device)
        else:
            if args.bus is not None or args.device is not None or args.extract_raw is not None:
                raise ValueError('--bus/--device/--extract-raw require pcap input')
            if args.native_audit is not None and args.urb_size is not None:
                raise ValueError('--native-audit provides actual lengths; do not combine with --urb-size')
            data, spans, barriers, extraction = source, raw_spans(len(source), args.urb_size), [], {'format': 'raw'}
            if args.native_audit is not None:
                spans, barriers, timing_metadata = native_completions(args.native_audit.read_bytes(), len(data))
                extraction['native_audit'] = {'path': str(args.native_audit.resolve()), **timing_metadata}
        result = analyze(data, spans, barriers, args.min_sync_packets, args.skip_prefix,
                         frame_baseline_bytes=args.frame_baseline_bytes, frame_cadence_ns=cadence_ns,
                         timing_metadata=timing_metadata)
        result['input'] = {'path': str(args.input.resolve()), 'bytes': len(source), 'sha256': sha256(source), **extraction}
        result['monitor'] = monitor_report(args.monitor.read_bytes() if args.monitor else None, timing_metadata)
        if args.monitor:
            result['monitor']['path'] = str(args.monitor.resolve())
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
