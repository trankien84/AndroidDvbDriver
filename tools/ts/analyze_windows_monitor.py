"""Read-only, sanitized TBS5530 control/I2C inventory from classic USBPcap."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import json
import math
from pathlib import Path
import statistics
import struct

CAPTURE_SHA256 = '70fced47614ff8ec17c9e78f79fca16408c23e6bcadcfb6ad25a4c69cd7f9ded'


def control_pairs(data):
    """Strict pairing by USB identity and IRP, never by adjacent frames."""
    magics = {b'\xd4\xc3\xb2\xa1': ('<', 1000000), b'\xa1\xb2\xc3\xd4': ('>', 1000000),
              b'\x4d\x3c\xb2\xa1': ('<', 1000000000), b'\xa1\xb2\x3c\x4d': ('>', 1000000000)}
    if len(data) < 24 or data[:4] not in magics:
        raise ValueError('Expected classic pcap')
    endian, scale = magics[data[:4]]
    major, minor, _, _, snaplen, link = struct.unpack_from(endian + 'HHIIII', data, 4)
    if (major, minor, link) != (2, 4, 249):
        raise ValueError('Expected USBPcap linktype 249, pcap 2.4')
    at, frame = 24, 0
    pending, pairs, descriptors = {}, [], set()
    bulk = []
    origin = None
    while at < len(data):
        if at + 16 > len(data):
            raise ValueError('Truncated pcap record')
        sec, fraction, cap, wire = struct.unpack_from(endian + 'IIII', data, at)
        at += 16
        frame += 1
        if fraction >= scale or cap != wire or cap > snaplen or at + cap > len(data):
            raise ValueError('Invalid pcap record at frame %d' % frame)
        b = data[at:at + cap]
        at += cap
        if len(b) < 27:
            raise ValueError('Short USBPcap header')
        h, irp, status, _, info, bus, device, ep, transfer, length = struct.unpack_from('<HQIHBHHBBI', b)
        if h < 27 or h + length != len(b):
            raise ValueError('Invalid USBPcap lengths')
        timestamp = sec * 1000000000 + fraction * (1000000000 // scale)
        if origin is None:
            origin = timestamp
        if transfer == 3 and ep == 0x82 and info & 1:
            bulk.append({'bus': bus, 'device': device, 'frame': frame, 'time_ns': timestamp - origin,
                         'status': status, 'actual': length})
        if transfer != 2:
            continue
        if h != 28:
            raise ValueError('Expected USBPcap control header')
        key = bus, device, ep, irp
        payload = b[h:]
        if not info & 1:
            if key in pending or len(payload) < 8:
                raise ValueError('Duplicate or short control submission')
            bm, request, value, index, requested = struct.unpack_from('<BBHHH', payload)
            pending[key] = dict(frame=frame, time_ns=timestamp - origin, bus=bus, device=device,
                                bm=bm, request=request, value=value, index=index,
                                requested=requested, payload=payload[8:])
        else:
            submit = pending.pop(key, None)
            if submit is None:
                raise ValueError('Orphan control completion at frame %d' % frame)
            result = payload if submit['bm'] & 128 else submit['payload']
            pair = dict(submit, completion=frame, end_ns=timestamp - origin,
                        status=status, payload=result,
                        complete=status == 0 and len(result) == submit['requested'])
            pairs.append(pair)
            if (submit['bm'], submit['request'], submit['value']) == (0x80, 6, 0x100) and pair['complete'] and len(result) == 18:
                if result[8:12] == bytes.fromhex('4c733055'):
                    descriptors.add((bus, device))
    if len(descriptors) != 1:
        raise ValueError('Expected exactly one descriptor-proven 734c:5530 owner')
    owner = next(iter(descriptors))
    scoped_bulk = [{k: v for k, v in b.items() if k not in ('bus', 'device')}
                   for b in bulk if (b['bus'], b['device']) == owner]
    return sorted((p for p in pairs if (p['bus'], p['device']) == owner), key=lambda p: p['frame']), {
        'frames': frame, 'target_descriptor_verified': True,
        'pending_controls': len(pending), 'clock': 'capture relative ns; not Android clock',
        'bulk_completion_timeline': scoped_bulk,
        'bulk_time_limit': 'USBPcap completion timestamp, not physical bus arrival or continuous RF throughput',
    }


def i2c_inventory(pairs):
    banks, pointer = {}, None
    rows, other, warnings = [], [], []
    for p in pairs:
        bm, req, payload = p['bm'], p['request'], p['payload']
        common = {k: p[k] for k in ('frame', 'completion', 'time_ns', 'end_ns', 'status', 'complete')}
        if req == 0xa0 and (bm & 0x60) == 0x40:
            banks.clear()
            pointer = None
            continue
        if bm == 0x40 and req == 0x80:
            pointer = None
            if len(payload) < 3 or payload[0] != len(payload) - 1 or payload[1] & 1:
                warnings.append({'frame': p['frame'], 'error': 'invalid I2C write'})
                continue
            addr, reg, values = payload[1], payload[2], payload[3:]
            row = dict(common, operation='write', address=addr, bank=banks.get(addr), register=reg, hex=values.hex())
            rows.append(row)
            if reg == 0 and len(values) == 1 and addr in (0xc8, 0xcc):
                banks[addr] = values[0] if p['complete'] else None
                row['selected_bank'] = banks[addr]
        elif bm == 0x40 and req == 0x90:
            if pointer is not None:
                warnings.append({'frame': p['frame'], 'error': 'unconsumed I2C pointer'})
            if len(payload) != 3 or payload[1] & 1 or not 1 <= payload[0] <= 50 or not p['complete']:
                pointer = None
                warnings.append({'frame': p['frame'], 'error': 'invalid I2C pointer'})
            else:
                pointer = (p, payload[1], banks.get(payload[1]), payload[2], payload[0])
        elif bm == 0xc0 and req == 0x91:
            if pointer is None:
                warnings.append({'frame': p['frame'], 'error': 'read without proven pointer'})
                continue
            ptr, addr, bank, reg, length = pointer
            pointer = None
            rows.append(dict(common, operation='read', address=addr, bank=bank, register=reg,
                             pointer_frame=ptr['frame'], pointer_completion=ptr['completion'],
                             requested=length, complete=p['complete'] and length == len(payload), hex=payload.hex()))
        elif req != 0xa0 and (bm & 0x60) == 0x40:
            other.append(dict(common, bm=bm, request=req, value=p['value'], index=p['index'],
                              requested=p['requested'], hex=payload.hex() if req in (0x81, 0x8a) else None))
            if not (bm == 0xc0 and req == 0x81 and p['complete'] and len(payload) == 1 and payload[0] <= 1):
                pointer = None
    if pointer:
        warnings.append({'frame': pointer[0]['frame'], 'error': 'pending pointer at end'})
    return rows, other, warnings


def sony_log10(value):
    fractions = (0, 4, 9, 13, 17, 21, 25, 29, 32, 36, 39, 43, 46, 49, 52, 55,
                 58, 61, 64, 67, 70, 73, 75, 78, 81, 83, 86, 88, 91, 93, 95, 98)
    count = value.bit_length() - 1
    logarithm = count * 100
    if count > 0:
        index = (value << (5 - count) if count <= 5 else value >> (count - 5)) & 31
        logarithm += fractions[index]
    return (100 * logarithm + 166) // 332


def decode_register(bank, register, payload):
    """Only CXD2878 mappings proven in pinned sources; no cross-chip guesses."""
    b = bytes(payload)
    if bank == 0x20 and register == 0x10 and len(b) == 1:
        sync = b[0] & 7
        return {'sync_state': sync, 'ts_lock': bool(b[0] & 0x20), 'unlock_detected': bool(b[0] & 0x10),
                'locked': sync == 6 and bool(b[0] & 0x20) and not b[0] & 0x10,
                'lock_state': 'UNKNOWN' if sync == 7 else 'LOCKED' if sync == 6 and b[0] & 0x20 and not b[0] & 0x10 else 'LOST'}
    if bank == 0x20 and register == 0x28 and len(b) == 2:
        raw = int.from_bytes(b, 'big')
        r = min(raw, 10876)
        db = 10 * math.log10(r / (12600 - r)) + 32 if r else None
        md_b = 100 * (sony_log10(r) - sony_log10(12600 - r)) + 30500 if r else None
        return {'snr_raw': raw, 'snr_db_curve': db,
                'official_cnr_millidb': md_b, 'official_cnr_db': md_b / 1000 if md_b is not None else None,
                'formula': 'official integer-log curve minus1500 mdB; snr_db_curve uses real log',
                'validity': 'requires a separate contemporaneous locked status; scalar decode alone does not certify lock'}
    if bank == 0x20 and register == 0x5b and len(b) in (1, 2):
        out = {'official_code_rate': ['1/2', '3/5', '2/3', '3/4', '4/5', '5/6', '1/3', '2/5'][b[0] & 7],
               'basis': 'cxd2878_read_status bank20; not verified active PLP signaling'}
        if len(b) == 2:
            out['official_modulation'] = ['QPSK', '16-QAM', '64-QAM', '256-QAM'][b[1] & 7] if b[1] & 7 < 4 else None
        return out
    if bank == 0x20 and register == 0x5c and len(b) == 1:
        return {'official_modulation': ['QPSK', '16-QAM', '64-QAM', '256-QAM'][b[0] & 7] if b[0] & 7 < 4 else None,
                'basis': 'second byte of cxd2878 bank20 5B(2); active PLP interpretation UNKNOWN'}
    return {'semantics': 'UNKNOWN for CXD6802 in pinned sources'}


def summarize(rows, other, warnings, capture_meta, digest):
    groups = defaultdict(list)
    timeline = []
    frozen, frozen_status = False, None
    for r in rows:
        if r['address'] == 0xc8 and r['operation'] == 'write' and r['register'] == 1:
            frozen = r['complete'] and r['hex'] == '01'
            frozen_status = None
        if r['operation'] == 'read':
            key = (r['address'], r['bank'], r['register'], r['requested'])
            groups[key].append(r)
            if r['address'] == 0xc8:
                r = dict(r, decoded=decode_register(r['bank'], r['register'], bytes.fromhex(r['hex'])) if r['complete'] else {'semantics': 'UNKNOWN: failed read'})
                if r['bank'] == 0x20 and r['register'] == 0x10 and frozen:
                    frozen_status = r['decoded'].get('lock_state')
                if r['bank'] == 0x20 and r['register'] == 0x28:
                    r['decoded']['same_freeze_lock_state'] = frozen_status if frozen else 'UNKNOWN'
                    r['decoded']['snr_db'] = r['decoded'].get('official_cnr_db') if frozen and frozen_status == 'LOCKED' else None
            timeline.append(r)
    inventory = []
    for (addr, bank, reg, length), values in sorted(groups.items(), key=lambda kv: str(kv[0])):
        gaps = [(b['time_ns'] - a['time_ns']) / 1e6 for a, b in zip(values, values[1:])]
        inventory.append({'address': '%02X' % addr, 'bank': None if bank is None else '%02X' % bank,
                          'register': '%02X' % reg, 'length': length, 'count': len(values),
                          'successful': sum(v['complete'] for v in values),
                          'values': dict(Counter(v['hex'] for v in values)),
                          'first_frame': values[0]['frame'], 'last_frame': values[-1]['completion'],
                          'first_ms': values[0]['time_ns'] / 1e6, 'last_ms': values[-1]['end_ns'] / 1e6,
                          'median_gap_ms': statistics.median(gaps) if gaps else None,
                          'min_gap_ms': min(gaps) if gaps else None, 'max_gap_ms': max(gaps) if gaps else None,
                          'semantics': decode_register(bank, reg, bytes.fromhex(values[0]['hex'])) if addr == 0xc8 else 'UNKNOWN / tuner or LED excluded from core'})
    writes = defaultdict(list)
    for r in rows:
        if r['operation'] == 'write':
            writes[(r['address'], r['bank'], r['register'], r['hex'])].append(r)
    return {'schema': 'tbs5530.windows-monitor/1', 'capture_sha256': digest, **capture_meta,
            'warnings': warnings, 'read_inventory': inventory, 'read_timeline': timeline,
            'write_inventory': [{'address': '%02X' % a, 'bank': None if b is None else '%02X' % b,
                                 'register': '%02X' % r, 'hex': h, 'count': len(v),
                                 'first_frame': v[0]['frame'], 'last_frame': v[-1]['completion'],
                                 'first_ms': v[0]['time_ns'] / 1e6, 'last_ms': v[-1]['end_ns'] / 1e6}
                                for (a, b, r, h), v in writes.items()],
            'other_vendor_commands': other,
            'limits': ['No firmware, USB identity or TS payload exported.',
                       'Windows returned values do not prove register meaning or Android equivalence.',
                       'BER/FER/PER/IFAGC/L1/PLP/TI remain UNKNOWN without a same-chip verified mapping.']}


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('capture', type=Path)
    ap.add_argument('--output', required=True, type=Path)
    ap.add_argument('--expected-sha256', default=CAPTURE_SHA256)
    args = ap.parse_args()
    data = args.capture.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != args.expected_sha256.lower():
        raise ValueError('Capture SHA-256 mismatch')
    pairs, meta = control_pairs(data)
    rows, other, warnings = i2c_inventory(pairs)
    report = summarize(rows, other, warnings, meta, digest)
    with args.output.open('x', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
        f.write('\n')
    print(json.dumps({'reads': len(report['read_timeline']), 'register_groups': len(report['read_inventory']),
                      'warnings': len(warnings), 'output': str(args.output)}))


if __name__ == '__main__':
    main()
