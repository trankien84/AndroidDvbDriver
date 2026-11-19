"""So sánh OFF/ON ngoại tuyến; không mở thiết bị và không tự kết luận EMI.

API: load_b5(bundle, export=None), load_capture(path), load_dump(sidecar, ts=None),
summarize(capture), compare_runs(baseline, candidate), markdown(summaries). Chỉ dùng stdlib và analyzer
B5 hiện có (không sửa analyzer). CLI: --b5 BUNDLE [--b5-export EXPORT]
[--b5-provenance JSON] [--capture JSON ...] [--json NEW] [--markdown NEW].
Không có --markdown thì in bảng Markdown ra stdout; output phải là tệp mới.

Đầu vào production: JSON schema PHASE4-CAPTURE/v1, id, provenance (object có
source, drive, layout, frontend; frontend nên chứa tần số/băng thông/PLP), phases.
provenance chỉ ghi nguồn/bố trí không chứa serial, token hoặc log riêng tư.
Mỗi phase có phase OFF1/ON1/OFF2/ON2, clock CLOCK_MONOTONIC, start_ns, end_ns;
samples là list hoặc đường dẫn JSONL tương đối manifest. Mẫu giống B5 monitor:
start_ns/end_ns/clock và snr_db, ber (pre-BCH), rssi_dbm, demod_lock LOCKED/LOST;
metrics object lồng cũng được nhận. summary=true không phải mẫu. RSSI riêng
có schema TBS5530-B5-RSSI/v1; không tính thành mẫu demod mất khóa.

ON có completions (list hoặc đường dẫn JSONL), completions_complete=true khi
log đầy đủ; record gồm timestamp_ns, clock, actual_length, status (0 thành công),
stage=data hoặc drain. Drain không tính lỗi dữ liệu. urb_errors +
urb_errors_complete=true là tổng lỗi URB/data syscall đã kiểm, không phải errno
của submit thay cho completion. Thiếu field giữ UNKNOWN; field sai từ chối.
quality_after5s={clock, start_ns, end_ns, ts_bytes, cc_errors, tei_packets} là
kết quả analyzer packet 188 byte trên đúng các completion >= first-data+5s,
fresh CC state như B5.after5_quality. start_ns phải bằng first-data+5s, end_ns bằng
phase.end_ns; không dùng PCR bitrate hay plain TS để suy thời gian/duty.
Có thể bỏ field chưa đo; không mặc định thành 0. JSON không nhận NaN/Infinity.

Duty: chia cửa sổ sau first-data+5s thành các bin đầy đủ 240ms, bỏ đuôi chưa đủ.
Burst là nhóm completion dữ liệu thành công có khoảng cách <=15ms; duration
last-first (singleton=0, không gán thời gian USB giả). Duty từng bin là phần
thời gian giao với các burst /240ms, gồm cả bin im lặng nếu log đầy đủ. Đây là
ước lượng theo userspace completion, không phải bus occupancy hoặc frame DVB.
Báo cả median duty và duty toàn cửa sổ bin; không áp cho plain TS/thiếu timestamp.
SNR p10 dùng nội suy tuyến tính hạng (n-1)*0.1. Drop = OFF median - ON median.
Demod losses đếm đợt LOST quan sát được mỗi pha (LOST đầu pha tính một); missing
monitor làm số đợt UNKNOWN. Không dùng ts_lock để suy demod loss.

B5 cũ: export session-report.json, phase31-signal-{monitor,rssi}-*.jsonl,
phase31-native-stream-*-ON*.{ts,jsonl}. Không cần đọc analysis summary cũ.
Mặc định export là <TSDuck>/logs/<bundle-name>/android4-readonly-export.
Đọc lại raw/monitor/audit, dùng B5 analyzer tính chất lượng và kiểm ánh xạ;
không dùng các min/median summary để dựng zero fraction hoặc p10.

Dump production: --dump-sidecar TS.usb.json (lặp được), --dump-ts TS tùy chọn
(lặp đúng số sidecar nếu dùng). Mặc định lấy ts_file trong cùng thư mục, chỉ
filename an toàn; kiểm ts_size và ts_sha256 nếu có. Nhận sidecar
TBS5530-DUMP-DIAGNOSTICS/v1 từ Tbs5530DumpDiagnostics.write sau close.
recording_start_ns/end_ns và native_window_start_ns/end_ns cùng CLOCK_MONOTONIC.
Duty chỉ có khi log completion đầy đủ, native_finished=true, overflow=false,
clock_invalid=false và native window bao phủ recording window. Không giả định
native actual_length nối trực tiếp thành TS file: bytes xếp hàng có thể cũ hơn.

Mapping consumer: consumer_offsets=[{timestamp_ns,byte_offset},...] tối đa 4096,
consumer_offsets_complete=true chỉ khi after-write log đủ, không rollback,
không overflow; marker đầu chính xác (recording_start_ns,0), marker cuối
(recording_end_ns,ts_size). Chọn marker đầu >=recording_start_ns+5s; crop TS tại
byte_offset đó, fresh CC state, tìm sync tiến tới và giữ original offsets/lỗi
ở giữa. Khoảng tính rate thực là marker.timestamp_ns..recording_end_ns; KHÔNG
nội suy offset tại đúng 5s. Duty dùng cùng khoảng khi có marker hợp lệ; thiếu
mapping vẫn tính duty riêng từ recording_start+5s nếu log native đầy đủ, nhưng
CC/TEI/Mbps UNKNOWN. JSON giữ target/crop/lateness/duration/offsets và caveat.
Các thời gian là quan sát consumer/userspace, không phải IRQ/FX2 hoặc on-air.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import statistics
import sys

PHASES = ('OFF1', 'ON1', 'OFF2', 'ON2')
CLOCK = 'CLOCK_MONOTONIC'
SCHEMA = 'PHASE4-CAPTURE/v1'
CYCLE_NS = 240_000_000
GAP_NS = 15_000_000
WARMUP_NS = 5_000_000_000


def number(value):
    return type(value) in (int, float) and math.isfinite(value)


def integer(value):
    return type(value) is int and value >= 0


def _constant(value):
    raise ValueError('Non-finite JSON number: ' + value)


def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'), parse_constant=_constant)


def read_rows(value, base):
    if value is None:
        return None
    if isinstance(value, str):
        value = [json.loads(line, parse_constant=_constant)
                 for line in (base / value).read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    if not isinstance(value, list) or any(not isinstance(row, dict) for row in value):
        raise ValueError('Records must be objects in a list or JSONL file')
    return value


def percentile(values, fraction):
    if not values:
        return None
    values = sorted(values)
    rank = (len(values) - 1) * fraction
    low = int(rank)
    return values[low] + (values[math.ceil(rank)] - values[low]) * (rank - low)


def distribution(values):
    return {'samples': len(values), 'median': statistics.median(values) if values else None,
            'p10': percentile(values, 0.1)}


def ber_distribution(values):
    result = distribution(values)
    zero = sum(value == 0 for value in values)
    return {**result, 'zero_count': zero if values else None,
            'zero_fraction': zero / len(values) if values else None,
            'nonzero_fraction': (len(values) - zero) / len(values) if values else None}


def _window(phase):
    start, end = phase.get('start_ns'), phase.get('end_ns')
    if start is None or end is None or phase.get('clock') is None:
        return None
    if not integer(start) or not integer(end) or end <= start or phase['clock'] != CLOCK:
        raise ValueError('Invalid phase monotonic window')
    return start, end


def _samples(phase, warnings):
    window = _window(phase)
    accepted = []
    seen = set()
    for row in phase.get('samples') or []:
        if row.get('summary') is True:
            continue
        start, end = row.get('start_ns'), row.get('end_ns')
        if (window is None or row.get('clock') != CLOCK or not integer(start) or not integer(end)
                or not window[0] <= start <= end <= window[1]):
            warnings.append(phase['phase'] + ': mẫu thiếu/sai timestamp bị loại')
            continue
        if row.get('phase', phase['phase']) != phase['phase']:
            raise ValueError('Sample phase mismatch')
        metrics = row.get('metrics', row)
        if not isinstance(metrics, dict):
            raise ValueError('Sample metrics must be an object')
        identity = (start, end, row.get('schema'))
        if identity in seen:
            raise ValueError('Duplicate sample')
        seen.add(identity)
        for key in ('snr_db', 'ber', 'rssi_dbm'):
            value = metrics.get(key)
            if value is not None and (not number(value) or (key == 'ber' and not 0 <= value <= 1)):
                raise ValueError('Invalid metric: ' + key)
        accepted.append((start, metrics, row.get('schema') == 'TBS5530-B5-RSSI/v1'))
    return sorted(accepted, key=lambda item: item[0])


def _timing(phase):
    empty = {'duty_median_percent': None, 'duty_total_percent': None,
             'cycle_count': None, 'cycles': None, 'burst_median_ms': None, 'quality_after5s': None}
    window = _window(phase)
    rows = phase.get('completions')
    if window is None or rows is None:
        return empty
    data, payloads = [], []
    previous = None
    complete = phase.get('completions_complete') is True
    for row in rows:
        if row.get('stage') == 'drain':
            continue
        stamp, length, status = row.get('timestamp_ns'), row.get('actual_length'), row.get('status')
        if any(value is None for value in (stamp, length, status)) or row.get('clock') is None or row.get('stage') is None:
            complete = False
            continue
        if (not integer(stamp) or not integer(length) or type(status) is not int
                or row['clock'] != CLOCK or row['stage'] != 'data'
                or not window[0] <= stamp <= window[1] or (previous is not None and stamp < previous)):
            raise ValueError('Invalid or unordered data completion')
        previous = stamp
        if status == 0 and length > 0:
            data.append(stamp)
            payloads.append((stamp, length))
    recording_origin = phase.get('timing_basis') == 'RECORDING_START'
    if not complete or (not data and not recording_origin):
        return empty
    bursts = []
    for stamp in data:
        if not bursts or stamp - bursts[-1][1] > GAP_NS:
            bursts.append([stamp, stamp])
        else:
            bursts[-1][1] = stamp
    origin = window[0] if recording_origin else data[0]
    threshold = phase.get('timing_start_ns', origin + WARMUP_NS)
    if not integer(threshold) or threshold < origin + WARMUP_NS:
        raise ValueError('Invalid post-warmup timing start')
    post_bursts = [(start, end) for start, end in bursts if start >= threshold]
    result = {**empty, 'burst_median_ms': statistics.median((end - start) / 1e6 for start, end in post_bursts)
              if post_bursts else None}
    cycles = max(0, (window[1] - threshold) // CYCLE_NS)
    if cycles:
        duties, cycle_rows = [], []
        index = 0
        for cycle in range(cycles):
            start = threshold + cycle * CYCLE_NS
            end = start + CYCLE_NS
            while index < len(bursts) and bursts[index][1] <= start:
                index += 1
            active = 0
            cursor = index
            while cursor < len(bursts) and bursts[cursor][0] < end:
                left, right = bursts[cursor]
                active += max(0, min(end, right) - max(start, left))
                cursor += 1
            duties.append(active / CYCLE_NS * 100)
            cycle_rows.append({'start_ns': start, 'end_ns': end, 'active_ns': active, 'duty_percent': duties[-1]})
        result.update(duty_median_percent=statistics.median(duties),
                      duty_total_percent=statistics.mean(duties), cycle_count=cycles, cycles=cycle_rows)
    quality = phase.get('quality_after5s')
    if quality is not None:
        if not isinstance(quality, dict):
            raise ValueError('quality_after5s must be an object')
        if quality.get('clock') is None or quality.get('start_ns') is None or quality.get('end_ns') is None:
            return result
        if (quality['clock'] != CLOCK or type(quality['start_ns']) is not int
                or type(quality['end_ns']) is not int or quality['start_ns'] != threshold
                or quality['end_ns'] != window[1] or window[1] <= threshold):
            raise ValueError('Quality window must be first-data+5s to audited end')
        duration = (window[1] - threshold) / 1e9
        rates = {'duration_s': duration}
        for field, out, factor in (('ts_bytes', 'ts_mbps', 8 / 1e6),
                                   ('cc_errors', 'cc_per_min', 60), ('tei_packets', 'tei_per_min', 60)):
            count = quality.get(field)
            if count is not None and not integer(count):
                raise ValueError('Invalid after5s count: ' + field)
            rates[out] = count * factor / duration if count is not None else None
        ts_bytes = quality.get('ts_bytes')
        if ts_bytes is not None:
            if ts_bytes % 188 or ts_bytes > sum(length for stamp, length in payloads if stamp >= threshold):
                raise ValueError('TS bytes exceed captured post5s payload or are not complete 188-byte packets')
            if quality.get('tei_packets') is not None and quality['tei_packets'] > ts_bytes // 188:
                raise ValueError('TEI count exceeds TS packets')
        result['quality_after5s'] = rates
    return result


def load_capture(path):
    path = Path(path)
    result = read_json(path)
    if not isinstance(result, dict) or result.get('schema') != SCHEMA:
        raise ValueError('Expected ' + SCHEMA)
    if not isinstance(result.get('id'), str) or not result['id'].strip():
        raise ValueError('Capture id must be a nonempty string')
    if not isinstance(result.get('provenance', {}), dict):
        raise ValueError('provenance must be an object')
    phases = result.get('phases')
    if not isinstance(phases, list):
        raise ValueError('phases must be a list')
    seen = set()
    previous = None
    last_phase = -1
    for phase in phases:
        if not isinstance(phase, dict) or phase.get('phase') not in PHASES or phase['phase'] in seen:
            raise ValueError('Invalid or duplicate phase')
        ordinal = PHASES.index(phase['phase'])
        if ordinal <= last_phase:
            raise ValueError('Phases must follow OFF1/ON1/OFF2/ON2 order')
        last_phase = ordinal
        seen.add(phase['phase'])
        window = _window(phase)
        if window:
            if previous is not None and window[0] < previous:
                raise ValueError('Overlapping or unordered phase windows')
            previous = window[1]
        for field in ('samples', 'completions'):
            phase[field] = read_rows(phase.get(field), path.parent)
    result['input_sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result


def _consumer_crop(sidecar, raw):
    start, end = sidecar['recording_start_ns'], sidecar['recording_end_ns']
    result = {'status': 'UNKNOWN_MISSING_CONSUMER_OFFSETS', 'quality': None,
              'target_start_ns': start + WARMUP_NS,
              'basis': 'Consumer after-write observations, not native completion offsets or on-air time.'}
    rows = sidecar.get('consumer_offsets')
    if rows is None:
        return result
    if not isinstance(rows, list) or len(rows) > 4096:
        raise ValueError('consumer_offsets must be a list bounded to 4096 markers')
    if sidecar.get('consumer_offsets_complete') is not True:
        return {**result, 'status': 'UNKNOWN_INCOMPLETE_CONSUMER_OFFSETS'}
    previous_time, previous_offset = None, None
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError('Consumer markers must be objects')
        stamp, offset = row.get('timestamp_ns'), row.get('byte_offset')
        if (not integer(stamp) or not integer(offset) or not start <= stamp <= end
                or offset > len(raw) or (previous_time is not None and stamp <= previous_time)
                or (previous_offset is not None and offset < previous_offset)):
            raise ValueError('Invalid or unordered consumer marker time/offset')
        previous_time, previous_offset = stamp, offset
    if (len(rows) < 2 or rows[0] != {'timestamp_ns': start, 'byte_offset': 0}
            or rows[-1] != {'timestamp_ns': end, 'byte_offset': len(raw)}):
        return {**result, 'status': 'UNKNOWN_UNANCHORED_CONSUMER_OFFSETS'}
    selected = next((row for row in rows if row['timestamp_ns'] >= start + WARMUP_NS), None)
    if selected is None or selected['timestamp_ns'] >= end:
        return {**result, 'status': 'UNKNOWN_NO_POST5S_OBSERVATION_WINDOW'}
    crop_time, crop_offset = selected['timestamp_ns'], selected['byte_offset']
    duration = (end - crop_time) / 1e9
    result.update(status='OBSERVED_CONSUMER_CROP', crop_start_ns=crop_time, end_ns=end,
                  crop_start_offset=crop_offset, end_offset=len(raw), duration_s=duration,
                  threshold_lateness_ns=crop_time - (start + WARMUP_NS),
                  interpolation=False, cc_state='FRESH_AT_CROP')
    import analyze_ts_capture as ts
    analysis = ts.analyze(raw, skip_prefix=crop_offset)
    sync = analysis['sync']
    result.update(sync_packets=sync['packets'], unparsed_bytes=sync['unparsed_bytes'],
                  leading_bytes=sync['leading_bytes'], sync_breaks=sync['break_count'],
                  sync_segments=[{'start': row['start'], 'end': row['end']} for row in sync['segments']],
                  offset_basis='Original TS byte offsets; never rebase or delete errors between sync segments.')
    if not sync['packets']:
        return {**result, 'status': 'UNKNOWN_NO_TS_SYNC_IN_CROP'}
    packets = sync['packets']
    cc = analysis['cc']['within_errors'] + analysis['cc']['between_errors']
    tei = analysis['totals']['tei']
    result['quality'] = {'duration_s': duration, 'start_ns': crop_time, 'end_ns': end,
                         'ts_bytes': packets * 188, 'cc_errors': cc, 'tei_packets': tei,
                         'ts_mbps': packets * 188 * 8 / duration / 1e6,
                         'cc_per_min': cc * 60 / duration, 'tei_per_min': tei * 60 / duration,
                         'basis': 'AT_OR_AFTER_RECORDING_START_PLUS_5S_CONSUMER_OBSERVATION'}
    return result


def load_dump(sidecar_path, ts_path=None, *, run_id=None):
    """Đọc sidecar Dump sau close; không dựng offset TS từ native URB length."""
    sidecar_path = Path(sidecar_path)
    source = sidecar_path.read_bytes()
    sidecar = json.loads(source.decode('utf-8-sig'), parse_constant=_constant)
    if not isinstance(sidecar, dict) or sidecar.get('schema') != 'TBS5530-DUMP-DIAGNOSTICS/v1':
        raise ValueError('Expected TBS5530-DUMP-DIAGNOSTICS/v1')
    name = sidecar.get('ts_file')
    if not isinstance(name, str) or not name or name in ('.', '..') or any(ch in name for ch in ('/', '\\', ':')):
        raise ValueError('ts_file must be a safe same-directory filename')
    ts_path = Path(ts_path) if ts_path is not None else sidecar_path.parent / name
    if ts_path.name != name:
        raise ValueError('TS filename disagrees with sidecar')
    raw = ts_path.read_bytes()
    if not integer(sidecar.get('ts_size')) or sidecar['ts_size'] != len(raw):
        raise ValueError('TS size disagrees with sidecar')
    raw_hash = hashlib.sha256(raw).hexdigest()
    if sidecar.get('ts_sha256') is not None and sidecar['ts_sha256'] != raw_hash:
        raise ValueError('TS SHA256 disagrees with sidecar')
    phase = {'phase': 'ON1', 'clock': sidecar.get('clock'),
             'start_ns': sidecar.get('recording_start_ns'), 'end_ns': sidecar.get('recording_end_ns')}
    if _window(phase) is None:
        raise ValueError('Dump requires an explicit monotonic recording window')
    start, end = phase['start_ns'], phase['end_ns']
    native_start, native_end = sidecar.get('native_window_start_ns'), sidecar.get('native_window_end_ns')
    if ((native_start is not None and not integer(native_start))
            or (native_end is not None and not integer(native_end))):
        raise ValueError('Invalid native window timestamp')
    if native_start is not None and native_end is not None and native_end <= native_start:
        raise ValueError('Invalid native window order')
    complete = (sidecar.get('completions_complete') is True and sidecar.get('native_finished') is True
                and sidecar.get('overflow') is False and sidecar.get('clock_invalid') is False
                and sidecar.get('truncated') is not True and native_start is not None and native_end is not None
                and native_start <= start < end <= native_end)
    rows = read_rows(sidecar.get('completions'), sidecar_path.parent)
    if rows is not None:
        previous = None
        for row in rows:
            if (row.get('clock') != CLOCK or row.get('stage') != 'data'
                    or not integer(row.get('timestamp_ns')) or not integer(row.get('actual_length'))
                    or type(row.get('status')) is not int or not start <= row['timestamp_ns'] <= end
                    or (previous is not None and row['timestamp_ns'] < previous)):
                raise ValueError('Invalid Dump recording-window completion')
            previous = row['timestamp_ns']
    crop = _consumer_crop(sidecar, raw)
    phase.update(samples=[], completions=rows, completions_complete=complete, timing_basis='RECORDING_START',
                 dump_alignment=crop, consumer_quality=crop['quality'])
    if 'crop_start_ns' in crop:
        phase['timing_start_ns'] = crop['crop_start_ns']
    if complete and rows is not None:
        phase.update(urb_errors=sum(row['status'] != 0 for row in rows), urb_errors_complete=True)
    frequency, bandwidth = sidecar.get('frequency_hz'), sidecar.get('bandwidth_hz')
    provenance = {'source': sidecar.get('source', 'UNKNOWN'), 'drive': sidecar.get('drive', 'UNKNOWN'),
                  'layout': sidecar.get('layout', 'UNKNOWN'),
                  'frontend': {'frequency_hz': frequency, 'bandwidth_hz': bandwidth, 'plp': sidecar.get('plp', 'UNKNOWN')},
                  'timestamp_kind': sidecar.get('timestamp_kind', 'UNKNOWN_NOT_LOGGED'),
                  'alignment_caveat': 'Queued TS bytes can precede recording; consumer write time and userspace reap time are not on-air/FX2 timestamps.'}
    identifier = run_id if run_id is not None else sidecar.get('id', ts_path.stem)
    if not isinstance(identifier, str) or not identifier.strip():
        raise ValueError('Dump id must be a nonempty string')
    return {'schema': SCHEMA, 'id': identifier, 'phases': [phase], 'provenance': provenance,
            'input_sha256': hashlib.sha256(source).hexdigest(), 'raw_sha256': raw_hash,
            'warnings': ([] if complete else ['Dump: native completion log không đủ để tính duty/URB errors.'])
                        + ([] if crop['quality'] is not None else ['Dump: ' + crop['status']])}


def _one(folder, pattern):
    matches = list(folder.glob(pattern))
    if len(matches) != 1:
        raise ValueError('Expected exactly one B5 input: ' + pattern)
    return matches[0]


def load_b5(bundle, export=None):
    import analyze_b5_capture as b5
    bundle = Path(bundle)
    export = Path(export) if export else bundle.parent.parent / 'logs' / bundle.name / 'android4-readonly-export'
    report_path = export / 'session-report.json'
    report = read_json(report_path)
    b5.validate_phases(report)
    monitor_paths = [_one(export, 'phase31-signal-monitor-*-' + name + '.jsonl') for name in PHASES]
    monitor_paths += [_one(export, 'phase31-signal-rssi-*-' + name + '.jsonl') for name in ('OFF1', 'OFF2')]
    monitor = b'\n'.join(path.read_bytes() for path in monitor_paths)
    inputs = {name: (_one(export, 'phase31-native-stream-*-' + name + '.ts').read_bytes(),
                     _one(export, 'phase31-native-stream-*-' + name + '.jsonl').read_bytes()) for name in ('ON1', 'ON2')}
    analysis = b5.analyze(report, inputs, monitor)
    phases = []
    monitor_rows = b5.jsonl(monitor)
    for original in report['b5_phases']:
        name = original['phase']
        phase = {**original, 'samples': [row for row in monitor_rows if row.get('phase') == name]}
        if name in inputs:
            raw, audit = inputs[name]
            spans, _, metadata = b5.ts.native_completions(audit, len(raw))
            on = analysis['on_analyses'][name]
            after = on['completion_frames'].get('after_5s', {})
            phase.update(start_ns=metadata['submit_begin_ns'], end_ns=metadata['end_ns'],
                         completions=[{'timestamp_ns': span['timestamp_ns'], 'actual_length': span['end'] - span['start'],
                                       'status': 0, 'stage': 'data', 'clock': CLOCK} for span in spans],
                         completions_complete=True, urb_errors=metadata['native_data_errors'], urb_errors_complete=True)
            if after.get('status') == 'OBSERVED':
                phase['quality_after5s'] = {**after['metrics'], 'clock': CLOCK,
                                           'start_ns': metadata['origin_ns'] + WARMUP_NS, 'end_ns': metadata['end_ns']}
            # Monitor precedes first submit and may end after drain: retain its phase window.
            phase['sample_window'] = [original['start_ns'], original['end_ns']]
        phases.append(phase)
    return {'schema': SCHEMA, 'id': 'B5', 'phases': phases, 'provenance': {},
            'frontend': analysis['frontend'], 'report_sha256': hashlib.sha256(report_path.read_bytes()).hexdigest(),
            'monitor_sha256': analysis['monitor_sha256'],
            'raw_sha256': {name: on['input']['sha256'] for name, on in analysis['on_analyses'].items()}}


def summarize(capture):
    warnings, phases = list(capture.get('warnings', [])), {}
    pooled = {'OFF': {'ber': [], 'snr_db': [], 'rssi_dbm': []}, 'ON': {'ber': [], 'snr_db': [], 'rssi_dbm': []}}
    for phase in capture['phases']:
        sample_phase = phase
        if 'sample_window' in phase:
            sample_phase = {**phase, 'start_ns': phase['sample_window'][0], 'end_ns': phase['sample_window'][1]}
        warnings_before = len(warnings)
        samples = _samples(sample_phase, warnings)
        values = {key: [metrics[key] for _, metrics, _ in samples if metrics.get(key) is not None]
                  for key in ('ber', 'snr_db', 'rssi_dbm')}
        mode = 'OFF' if phase['phase'].startswith('OFF') else 'ON'
        for key, observed in values.items():
            pooled[mode][key].extend(observed)
        states = [metrics.get('demod_lock') for _, metrics, rssi in samples if not rssi]
        losses, previous = 0, None
        known = bool(states)
        for state in states:
            if state not in ('LOCKED', 'LOST'):
                known = False
            elif state == 'LOST' and previous != 'LOST':
                losses += 1
            previous = state
        errors = phase.get('urb_errors')
        if errors is not None and not integer(errors):
            raise ValueError('urb_errors must be a nonnegative integer')
        phases[phase['phase']] = {'ber': ber_distribution(values['ber']), 'snr_db': distribution(values['snr_db']),
                                  'rssi_dbm': distribution(values['rssi_dbm']),
                                  'demod_lock_losses': losses if known and len(warnings) == warnings_before else None,
                                  'urb_errors': errors if phase.get('urb_errors_complete') is True else None,
                                  **_timing(phase)}
        if 'dump_alignment' in phase:
            phases[phase['phase']].update(quality_after5s=phase['consumer_quality'],
                                          dump_alignment=phase['dump_alignment'],
                                          timing_basis='CONSUMER_MARKER' if 'timing_start_ns' in phase else 'RECORDING_START_PLUS_5S',
                                          timing_start_ns=phase.get('timing_start_ns', phase['start_ns'] + WARMUP_NS),
                                          urb_error_basis='FAILED_COMPLETION_STATUS_ONLY_NO_SYSCALL_AUDIT')
    groups = {mode: {key: ber_distribution(values) if key == 'ber' else distribution(values)
                     for key, values in metrics.items()} for mode, metrics in pooled.items()}
    drops = {}
    for index in (1, 2):
        off = phases.get('OFF' + str(index), {}).get('snr_db', {}).get('median')
        on = phases.get('ON' + str(index), {}).get('snr_db', {}).get('median')
        drops[str(index)] = off - on if off is not None and on is not None else None
    return {'id': capture['id'], 'provenance': capture.get('provenance', {}), 'phases': phases,
            'groups': groups, 'snr_drop_db': drops, 'warnings': sorted(set(warnings)),
            'emi': 'UNKNOWN_NOT_AUTOMATICALLY_ASSESSED',
            'hashes': {key: value for key, value in capture.items() if key.endswith('sha256')}}


def compare_runs(baseline, candidate):
    deltas = {}
    for name in ('OFF1', 'OFF2'):
        deltas[name] = {}
        for field in ('snr_db', 'rssi_dbm'):
            old = baseline['phases'].get(name, {}).get(field, {}).get('median')
            new = candidate['phases'].get(name, {}).get(field, {}).get('median')
            deltas[name][field] = abs(new - old) if new is not None and old is not None else None
    values = [value for metrics in deltas.values() for value in metrics.values()]
    drift = 'INCONCLUSIVE' if any(value is not None and value > 2 for value in values) else (
        'WITHIN_2_DB' if all(value is not None for value in values) else 'UNKNOWN')
    provenance = [run.get('provenance', {}) for run in (baseline, candidate)]
    known = all(all(p.get(key) not in (None, '', 'UNKNOWN', {}) for key in ('source', 'drive', 'layout', 'frontend'))
                for p in provenance)
    same_frontend = known and provenance[0]['frontend'] == provenance[1]['frontend']
    return {'off_deltas_db': deltas, 'off_baseline': drift,
            'comparison': 'DESCRIPTIVE_ONLY' if drift == 'WITHIN_2_DB' and same_frontend else 'INCONCLUSIVE',
            'provenance_complete': known, 'same_frontend': bool(same_frontend),
            'emi': 'UNKNOWN_NOT_AUTOMATICALLY_ASSESSED'}


def _fmt(value):
    return 'UNKNOWN' if value is None else format(value, '.9g') if number(value) else str(value).replace('|', '\\|').replace('\n', ' ').replace('\r', ' ')


def _ber(metric):
    return '%s/%s (%s); med=%s' % (_fmt(metric['zero_count']), metric['samples'],
                                  _fmt(metric['zero_fraction']), _fmt(metric['median']))


def markdown(results):
    lines = ['# So sánh OFF/ON Phase 4 (ngoại tuyến)', '',
             'B5 đứng đầu; UNKNOWN là thiếu bằng chứng, không phải zero. Không tự PASS hoặc kết luận EMI.', '',
             '| Lượt | BER OFF zero/n (fraction); median | BER ON zero/n (fraction); median | SNR OFF med/p10 dB | SNR ON med/p10 dB | Drop cặp 1/2 dB | RSSI OFF/ON med dBm | TS sau 5s Mbps ON1/2 | Duty med (tổng) % ON1/2 | Burst median ms ON1/2 | CC/min ON1/2 | TEI/min ON1/2 | Demod loss OFF1/ON1/OFF2/ON2 | URB lỗi ON1/2 | OFF so B5 | EMI |',
             '| ' + ' | '.join(['---'] * 16) + ' |']
    for result in results:
        phases, groups = result['phases'], result['groups']
        def ons(field, quality=False):
            return ' / '.join(_fmt((phases.get(name, {}).get('quality_after5s') or {}).get(field)
                                  if quality else phases.get(name, {}).get(field)) for name in ('ON1', 'ON2'))
        row = [result['id'], _ber(groups['OFF']['ber']), _ber(groups['ON']['ber'])]
        row += [' / '.join(_fmt(groups[mode]['snr_db'][key]) for key in ('median', 'p10')) for mode in ('OFF', 'ON')]
        row += [' / '.join(_fmt(result['snr_drop_db'][key]) for key in ('1', '2')),
                ' / '.join(_fmt(groups[mode]['rssi_dbm']['median']) for mode in ('OFF', 'ON')),
                ons('ts_mbps', True),
                ' / '.join('%s (%s)' % (_fmt(phases.get(name, {}).get('duty_median_percent')),
                                        _fmt(phases.get(name, {}).get('duty_total_percent'))) for name in ('ON1', 'ON2')),
                ons('burst_median_ms'),
                ons('cc_per_min', True), ons('tei_per_min', True),
                ' / '.join(_fmt(phases.get(name, {}).get('demod_lock_losses')) for name in PHASES),
                ons('urb_errors'), result.get('comparison', {}).get('off_baseline', 'BASELINE'), result['emi']]
        lines.append('| ' + ' | '.join(_fmt(value) for value in row) + ' |')
    lines += ['', '## Cách đọc và giới hạn', '',
              '- BER gộp mẫu hợp lệ theo OFF/ON (không lấy trung bình các median); JSON giữ từng pha và nonzero fraction.',
              '- SNR drop giữ riêng hai cặp, tránh che mức giảm không lặp. p10 nội suy tuyến tính.',
              '- Duty theo completion, bin 240 ms đầy đủ, burst gap >15 ms; JSON giữ thời gian có dữ liệu từng bin, cả bin im lặng. B5/normalized dùng first-data+5s. Dump dùng marker consumer đầu >=recording-start+5s; thiếu mapping chỉ tính duty từ recording-start+5s. Tổng là trung bình theo thời gian, không phải thời gian chiếm bus.',
              '- TS Mbps dùng ts_bytes / thời gian quan sát gồm cả im lặng. Dump crop theo offset marker, giữ thời gian thực và độ trễ so với mốc 5s, không nội suy. CC/TEI fresh state tại crop; thiếu mapping thì cả ba UNKNOWN. Không dùng PCR bitrate.',
              '- So sánh từng OFF1/OFF2 với B5: SNR và RSSI median lệch tuyệt đối >2 dB => INCONCLUSIVE; thiếu => UNKNOWN.',
              '- Nguồn, drive, layout, frontend phải được người thu ghi rõ; không suy bố trí từ dữ liệu. Ngay cả đủ provenance và lệch <=2 dB chỉ cho so sánh mô tả.',
              '- Không có bộ tiêu chí EMI đã duyệt/đủ bằng chứng mọi tiêu chí; mọi kết luận EMI vẫn UNKNOWN. Công cụ không xác nhận đầu vào production đã được đo trên hardware.']
    for result in results:
        lines += ['', '### ' + _fmt(result['id']), '',
                  '- Provenance: `' + json.dumps(result['provenance'], ensure_ascii=False).replace('`', "'") + '`.',
                  '- So sánh: `' + json.dumps(result.get('comparison', {}), ensure_ascii=False) + '`.']
        for name in PHASES:
            if name in result['phases']:
                metric = result['phases'][name]['ber']
                lines.append('- %s BER: %s; nonzero fraction=%s.' % (name, _ber(metric), _fmt(metric['nonzero_fraction'])))
                dump = result['phases'][name].get('dump_alignment')
                if dump is not None:
                    lines.append('- %s Dump: `%s`; crop consumer=%s ns, offset=%s, duration=%s s; duty start=%s ns. Không nội suy hoặc coi timestamp là thời gian phát RF.' % (
                        name, dump['status'], _fmt(dump.get('crop_start_ns')), _fmt(dump.get('crop_start_offset')),
                        _fmt(dump.get('duration_s')), _fmt(result['phases'][name].get('timing_start_ns'))))
        lines += ['- ' + warning for warning in result['warnings']]
    return '\n'.join(lines) + '\n'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--b5', required=True, type=Path)
    parser.add_argument('--b5-export', type=Path)
    parser.add_argument('--b5-provenance', type=Path)
    parser.add_argument('--capture', action='append', default=[], type=Path)
    parser.add_argument('--dump-sidecar', action='append', default=[], type=Path,
                        help='Post-close TBS5530-DUMP-DIAGNOSTICS/v1; infer safe same-directory ts_file')
    parser.add_argument('--dump-ts', action='append', default=[], type=Path,
                        help='Optional explicit raw path for each --dump-sidecar, in the same order')
    parser.add_argument('--json', type=Path)
    parser.add_argument('--markdown', type=Path)
    args = parser.parse_args(argv)
    try:
        outputs = [path for path in (args.json, args.markdown) if path is not None]
        if len({path.resolve() for path in outputs}) != len(outputs) or any(path.exists() for path in outputs):
            raise ValueError('Outputs must be distinct new paths')
        if args.dump_ts and len(args.dump_ts) != len(args.dump_sidecar):
            raise ValueError('Explicit --dump-ts paths must match the number of --dump-sidecar inputs')
        baseline = load_b5(args.b5, args.b5_export)
        if args.b5_provenance:
            provenance = read_json(args.b5_provenance)
            if not isinstance(provenance, dict):
                raise ValueError('B5 provenance must be an object')
            baseline['provenance'] = provenance
        results = [summarize(baseline)]
        ids = {'B5'}
        candidates = [load_capture(path) for path in args.capture]
        candidates += [load_dump(path, args.dump_ts[index] if args.dump_ts else None)
                       for index, path in enumerate(args.dump_sidecar)]
        for candidate in candidates:
            result = summarize(candidate)
            if result['id'] in ids:
                raise ValueError('Run ids must be unique')
            ids.add(result['id'])
            result['comparison'] = compare_runs(results[0], result)
            results.append(result)
        text = markdown(results)
        if args.json:
            with args.json.open('x', encoding='utf-8', newline='\n') as output:
                json.dump({'schema': 'PHASE4-COMPARISON/v1', 'runs': results}, output, ensure_ascii=False, indent=2, allow_nan=False)
                output.write('\n')
        if args.markdown:
            with args.markdown.open('x', encoding='utf-8', newline='\n') as output:
                output.write(text)
        else:
            sys.stdout.write(text)
        return 0
    except (OSError, ValueError, KeyError, TypeError) as exc:
        print('ERROR: ' + str(exc), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
