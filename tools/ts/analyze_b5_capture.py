"""Offline B5 four-phase analysis built on the unchanged B4 TS/audit analyzer."""
from __future__ import annotations

import argparse
from bisect import bisect_right
from collections import Counter
import json
from pathlib import Path
import statistics
import sys

import analyze_ts_capture as ts
from compare_b4_capture import compare, finite, service_signature

LAYOUT = (('OFF1', 10_000), ('ON1', 30_000), ('OFF2', 10_000), ('ON2', 10_000))
FRONTENDS = {'B5_K25_MANUAL_PLP0': (506_000_000, 'MANUAL0'),
             'B5_K25_AUTO': (506_000_000, 'AUTO'),
             'B5_K36_MANUAL_PLP0': (594_000_000, 'MANUAL0')}


def jsonl(source):
    rows = [json.loads(line) for line in source.decode('utf-8-sig').splitlines() if line.strip()]
    if any(not isinstance(row, dict) for row in rows):
        raise ValueError('JSONL rows must be objects')
    return rows


def stats(values):
    observed = [value for value in values if finite(value)]
    return {'samples': len(observed), 'min': min(observed) if observed else None,
            'median': statistics.median(observed) if observed else None,
            'max': max(observed) if observed else None}


def validate_phases(report):
    phases = report.get('b5_phases')
    if (report.get('b5_enabled') is not True or report.get('frontend_experiment') not in ('B5_K25_MANUAL_PLP0', 'B5_K36_MANUAL_PLP0')
            or not isinstance(phases, list) or len(phases) > 4):
        raise ValueError('Expected B5 frontend and ordered phase report')
    previous = -1
    for phase, (name, duration) in zip(phases, LAYOUT):
        if (not isinstance(phase, dict) or phase.get('phase') != name
                or phase.get('clock') != 'CLOCK_MONOTONIC'
                or any(type(phase.get(k)) is not int for k in ('start_ns', 'end_ns', 'bytes', 'stale_prefix_bytes'))
                or phase['start_ns'] < previous or phase['end_ns'] < phase['start_ns']
                or not 0 <= phase['bytes'] <= 268_435_456
                or phase['stale_prefix_bytes'] != (2048 if name == 'ON1' else 0)
                or type(phase.get('drained')) is not bool or type(phase.get('quarantined')) is not bool
                or (name.startswith('ON') and phase.get('raw_file') != 'received-' + name + '.ts')
                or (name.startswith('OFF') and (phase['bytes'] or phase.get('raw_file') is not None))):
            raise ValueError('Invalid B5 phase identity, time, byte count or typed drain')
        phase = dict(phase)
        phase['planned_duration_ms'] = duration
        previous = phase['end_ns']
    return phases


def monitor_phases(rows, phases, spans_by_phase):
    results = {}
    program_data = sorted((span['timestamp_ns'], name) for name, spans in spans_by_phase.items() for span in spans)
    program_times = [time for time, _ in program_data]
    for phase in phases:
        name = phase['phase']
        selected = []
        rejected = 0
        for row in rows:
            if row.get('summary') is True or row.get('phase') != name:
                continue
            start, end = row.get('start_ns'), row.get('end_ns')
            if (row.get('clock') != 'CLOCK_MONOTONIC' or type(start) is not int or type(end) is not int
                    or not phase['start_ns'] <= start <= end <= phase['end_ns']):
                rejected += 1
                continue
            selected.append(row)
        selected.sort(key=lambda row: row['start_ns'])
        metrics = [row.get('metrics', row) for row in selected]
        if any(not isinstance(row, dict) for row in metrics):
            raise ValueError('Monitor metrics must be objects')
        snrs = [row.get('snr_db') for row in metrics]
        demod = [1 if row.get('demod_lock') == 'LOCKED' else 0 if row.get('demod_lock') == 'LOST' else None for row in metrics]
        locked = [int(row['ts_lock']) if type(row.get('ts_lock')) is bool else None for row in metrics]
        samples = {'snr_db': stats(snrs), 'ber': stats(row.get('ber') for row in metrics),
                   'demod_lock': stats(demod), 'ts_lock': stats(locked),
                   'rssi_dbm': stats(row.get('rssi_dbm') for row in metrics)}
        on = name.startswith('ON')
        rssi_count = samples['rssi_dbm']['samples']
        data_times = [span['timestamp_ns'] for span in spans_by_phase.get(name, [])]
        pairs, no_previous = [], 0
        for row, snr in zip(selected, snrs):
            if not finite(snr):
                continue
            index = bisect_right(data_times, row['start_ns']) - 1
            if index < 0:
                no_previous += 1
                continue
            pairs.append({'sample_start_ns': row['start_ns'], 'snr_db': snr,
                          'elapsed_since_last_data_ms': (row['start_ns'] - data_times[index]) / 1e6})
        xs = [pair['elapsed_since_last_data_ms'] for pair in pairs]
        ys = [pair['snr_db'] for pair in pairs]
        coefficient = None
        if len(pairs) >= 3 and len(set(xs)) > 1 and len(set(ys)) > 1:
            coefficient = statistics.correlation(xs, ys)
        global_pairs, global_no_previous = [], 0
        for row, snr in zip(selected, snrs):
            if not finite(snr):
                continue
            index = bisect_right(program_times, row['start_ns']) - 1
            if index < 0:
                global_no_previous += 1
                continue
            last_time, last_phase = program_data[index]
            global_pairs.append({'sample_start_ns': row['start_ns'], 'snr_db': snr,
                                 'elapsed_since_last_data_ms': (row['start_ns'] - last_time) / 1e6,
                                 'last_data_phase': last_phase, 'intentional_off_sample': not on,
                                 'cross_phase': last_phase != name})
        global_x = [pair['elapsed_since_last_data_ms'] for pair in global_pairs]
        global_y = [pair['snr_db'] for pair in global_pairs]
        global_r = (statistics.correlation(global_x, global_y)
                    if len(global_pairs) >= 3 and len(set(global_x)) > 1 and len(set(global_y)) > 1 else None)
        invalid_rssi = sum(finite(values.get('rssi_dbm')) and
                           (row.get('schema') != 'TBS5530-B5-RSSI/v1'
                            or row.get('sequence') != 'ASCOT3_WINDOWS_88_00_87_C0'
                            or row.get('windows_sequence_matches') is not True
                            or row.get('windows_cleanup_88') != '00' or row.get('cleanup_87') != 'c0'
                            or row.get('cleanup_succeeded') is not True)
                           for row, values in zip(selected, metrics))
        failure = any(row.get('monitor_failure') for row in rows if row.get('phase') == name)
        results[name] = {'samples': len(selected), 'rejected_samples': rejected, 'metrics': samples,
                         'monitor_failure': failure, 'invalid_rssi_provenance_samples': invalid_rssi,
                         'demod_lock_states': dict(Counter(str(row.get('demod_lock', 'UNKNOWN')) for row in metrics)),
                         'rssi_policy': 'FAIL_RSSI_DURING_ON' if on and rssi_count else
                                        'FAIL_RSSI_WINDOWS_SEQUENCE_PROVENANCE' if invalid_rssi else
                                        'PASS_OFF_THREE_SAMPLES' if not on and rssi_count == 3 else
                                        'UNKNOWN_OFF_RSSI_COUNT' if not on else 'PASS_NO_RSSI_DURING_ON',
                         'snr_vs_elapsed_since_phase_data': {'status': 'OBSERVED_CORRELATION_NOT_CAUSATION' if coefficient is not None else 'UNKNOWN_INSUFFICIENT_NONCONSTANT_PAIRS',
                            'pearson_r': coefficient, 'pairs': pairs, 'samples_before_first_data': no_previous,
                            'basis': 'Same phase CLOCK_MONOTONIC; last successful captured completion <= sample start; never carry ON1 data into OFF2/ON2.'}}
        results[name]['snr_vs_elapsed_since_last_data'] = {
            'status': 'OBSERVED_CORRELATION_NOT_CAUSATION' if global_r is not None else 'UNKNOWN_INSUFFICIENT_NONCONSTANT_PAIRS',
            'pearson_r': global_r, 'pairs': global_pairs, 'samples_before_first_data': global_no_previous,
            'basis': 'Same four-phase CLOCK_MONOTONIC program only; OFF intervals are intentional and explicitly labelled, never inferred USB losses.'}
    return results


def data_timeline(spans, phase):
    bursts, gaps = [], []
    previous = None
    for span in spans:
        time = span['timestamp_ns']
        if previous is None or time - previous > 15_000_000:
            if previous is not None:
                gaps.append({'start_ns': previous, 'end_ns': time, 'duration_ms': (time - previous) / 1e6,
                             'kind': 'BETWEEN_DATA_COMPLETIONS'})
            bursts.append({'start_ns': time, 'end_ns': time, 'bytes': 0, 'completions': 0})
        bursts[-1]['end_ns'] = time
        bursts[-1]['bytes'] += span['end'] - span['start']
        bursts[-1]['completions'] += 1
        previous = time
    boundaries = []
    if spans:
        boundaries = [{'kind': 'BEFORE_FIRST_DATA', 'duration_ms': (spans[0]['timestamp_ns'] - phase['start_ns']) / 1e6},
                      {'kind': 'AFTER_LAST_DATA', 'duration_ms': (phase['end_ns'] - spans[-1]['timestamp_ns']) / 1e6}]
    else:
        boundaries = [{'kind': 'NO_DATA_IN_PHASE', 'duration_ms': (phase['end_ns'] - phase['start_ns']) / 1e6}]
    return {'bursts': bursts, 'gaps': gaps, 'boundary_intervals': boundaries,
            'basis': 'Userspace reap timestamps, gap strictly >15ms; not physical DVB-T2 frame timing or proof of source loss.'}


def after5_quality(report):
    frames = report['completion_frames']
    after = frames.get('after_5s', {})
    duration = frames.get('observed_duration_ms')
    metrics = after.get('metrics') or {}
    known = (after.get('status') == 'OBSERVED' and finite(duration) and duration > 5000)
    def rate(key):
        value = metrics.get(key)
        return value * 60_000 / (duration - 5000) if known and finite(value) and value >= 0 else None
    return {'status': after.get('status', 'UNKNOWN'), 'observed_post5s_ms': duration - 5000 if known else None,
            'full_percent': after.get('full_percent_including_missing'),
            'frame_counts': after.get('frame_counts'), 'missing_frames': after.get('missing_frames'),
            'cc_per_minute': rate('cc_errors'), 'tei_per_minute': rate('tei_packets'),
            'psi': (report.get('post5s_tables') or {}).get('psi'),
            'pcr': (report.get('post5s_tables') or {}).get('pcr'),
            'table_scope': 'Only bytes from captured completions >=first data+5s; fresh PSI/CC/PCR state, original offsets retained.'}


def smoke_verdict(analyses, phases, policy=None):
    if policy is None:
        policy = {'schema': 'B5-SMOKE-POLICY/v1', 'approved_by_user': True,
                  'basis': 'USER_PROMPT_AFTER5S_ZERO_CC_TEI_PAT_PMT_SDT_VTV_STABLE_PCR',
                  'max_cc_per_minute': 0, 'max_tei_per_minute': 0,
                  'pcr_stability_max_relative_spread': None}
    if (policy.get('schema') != 'B5-SMOKE-POLICY/v1' or policy.get('approved_by_user') is not True
            or ('card_sha256' in policy and (not isinstance(policy['card_sha256'], str) or len(policy['card_sha256']) != 64
                or any(ch not in '0123456789abcdef' for ch in policy['card_sha256'])))
            or any(not finite(policy.get(key)) or policy[key] != 0
                   for key in ('max_cc_per_minute', 'max_tei_per_minute'))
            or ('min_full_percent' in policy and (not finite(policy['min_full_percent']) or not 0 <= policy['min_full_percent'] <= 100))
            or (policy.get('pcr_stability_max_relative_spread') is not None and
                (not finite(policy['pcr_stability_max_relative_spread']) or policy['pcr_stability_max_relative_spread'] < 0))):
        raise ValueError('Invalid explicit user smoke thresholds')
    criteria = {}
    unknown, failed = [], []
    def check(name, value):
        criteria[name] = 'UNKNOWN' if value is None else 'PASS' if value else 'FAIL'
        (unknown if value is None else failed if not value else []).append(name)
    check('four_phases_complete', len(phases) == 4 and all(p['end_ns'] - p['start_ns'] >= duration * 1_000_000
          for p, (_, duration) in zip(phases, LAYOUT)))
    check('phase_drain', all(p.get('drained') is True and p.get('quarantined') is False for p in phases))
    for name in ('ON1', 'ON2'):
        report = analyses.get(name)
        if report is None:
            check(name + '_capture', None)
            continue
        after = after5_quality(report)
        for metric, key, minimum in (('cc_per_minute', 'max_cc_per_minute', False),
                                     ('tei_per_minute', 'max_tei_per_minute', False),
                                     ('full_percent', 'min_full_percent', True)):
            if key not in policy:
                continue
            value = after[metric]
            check(name + '_' + metric, (value >= policy[key] if minimum else value <= policy[key]) if finite(value) else None)
        signature = service_signature({'psi': after.get('psi') or {}})
        check(name + '_pat_pmt_sdt_services', True if signature else None)
        check(name + '_vtv_service', any('VTV' in str(s[1]).upper() for s in signature['services']) if signature else None)
        required = {s[4] for s in signature['services'] if s[4] != 8191} if signature else set()
        observed = {p['pid'] for p in (after.get('pcr') or {}).get('by_pid', []) if p.get('status') == 'estimate' and finite(p.get('median_bitrate_bps'))}
        check(name + '_every_service_pcr', True if required and required <= observed else None)
        tolerance = policy.get('pcr_stability_max_relative_spread')
        windows = (after.get('pcr') or {}).get('windows', [])
        stability = None
        if tolerance is not None and required:
            comparisons = []
            for pid in required:
                rates = [window['bitrate_bps'] for window in windows
                         if window.get('pid') == pid and finite(window.get('bitrate_bps')) and window['bitrate_bps'] > 0]
                if len(rates) < 2:
                    break
                comparisons.append((max(rates) - min(rates)) / statistics.median(rates) <= tolerance)
            if len(comparisons) == len(required):
                stability = all(comparisons)
        check(name + '_pcr_stable', stability)
        native = report['input']['native_audit']
        writer, drain = native.get('writer', {}), native.get('drain', {})
        verified = (native.get('writer_mapping') == 'VERIFIED' and writer.get('writer_drop_bytes') == 0
                    and writer.get('writer_failure') is None and writer.get('raw_mapping_valid') is True)
        check(name + '_writer', verified)
        check(name + '_native_data_errors_zero', native.get('native_data_errors') == 0)
        check(name + '_drain', drain.get('outstanding') == 0 and drain.get('quarantined') is False
              and type(drain.get('submitted')) is int and drain['submitted'] > 0
              and drain.get('completed') == drain['submitted'])
    return {'status': 'INSUFFICIENT_EVIDENCE' if unknown else 'TS_SMOKE_FAIL' if failed else 'TS_SMOKE_PASS',
            'criteria': criteria, 'unknown': unknown, 'failed': failed, 'policy': policy,
            'scope': 'User-approved zero CC/TEI, PAT/PMT/SDT, VTV and stable PCR after first data+5s in both ON phases. Numeric PCR stability undefined means UNKNOWN, never automatic PASS or another hardware authorization.'}


def analyze(report, phase_inputs, monitor_source, *, baseline_bytes=None, cadence_ns=None, smoke_policy=None):
    phases = validate_phases(report)
    analyses, spans_by_phase, timeline = {}, {}, {}
    for phase in phases:
        name = phase['phase']
        if not name.startswith('ON'):
            continue
        if name not in phase_inputs:
            continue
        raw, audit = phase_inputs[name]
        if len(raw) != phase['bytes']:
            raise ValueError('Phase raw byte count disagrees with report')
        rows = jsonl(audit)
        if {row.get('run_id') for row in rows if 'run_id' in row} != {phase.get('audit_run_id')}:
            raise ValueError('Phase native run identity disagrees with report')
        spans, barriers, metadata = ts.native_completions(audit, len(raw))
        if (metadata['clock'] != phase['clock'] or metadata['submit_begin_ns'] < phase['start_ns']
                or metadata['end_ns'] > phase['end_ns']):
            raise ValueError('Native audit lies outside phase monotonic window')
        skipped = min(phase['stale_prefix_bytes'], len(raw))
        analysis = ts.analyze(raw, spans, barriers, skip_prefix=skipped, frame_baseline_bytes=baseline_bytes,
                              frame_cadence_ns=cadence_ns, timing_metadata=metadata)
        analysis['input'] = {'sha256': ts.sha256(raw), 'bytes': len(raw), 'native_audit': metadata}
        phase_rows = [row for row in jsonl(monitor_source) if row.get('phase') == name]
        analysis['monitor'] = ts.monitor_report(('\n'.join(json.dumps(row) for row in phase_rows) + '\n').encode(), metadata)
        threshold = metadata['origin_ns'] + 5_000_000_000 if type(metadata.get('origin_ns')) is int else None
        start = next((span['start'] for span in spans if threshold is not None and span['timestamp_ns'] >= threshold), None)
        if start is not None:
            post = ts.analyze(raw, spans, barriers, skip_prefix=max(skipped, start), timing_metadata=metadata)
            analysis['post5s_tables'] = {'psi': post['psi'], 'pcr': post['pcr'], 'analysis_range': post['analysis_range']}
        analysis['after5s_quality'] = after5_quality(analysis)
        analyses[name], spans_by_phase[name] = analysis, spans
        timeline[name] = data_timeline(spans, phase)
    metrics = monitor_phases(jsonl(monitor_source), phases, spans_by_phase)
    verdict = smoke_verdict(analyses, phases, smoke_policy)
    if verdict['status'] == 'TS_SMOKE_PASS':
        bad_rssi = [name for name, summary in metrics.items() if summary['rssi_policy'].startswith('FAIL')]
        missing = [name for name, summary in metrics.items()
                   if summary['rssi_policy'].startswith('UNKNOWN') or summary['rejected_samples'] or summary['monitor_failure']
                   or any(summary['metrics'][key]['samples'] == 0 for key in ('snr_db', 'ber', 'demod_lock', 'ts_lock'))]
        if bad_rssi:
            verdict = {**verdict, 'status': 'TS_SMOKE_FAIL', 'rssi_policy_failure': bad_rssi}
        elif missing:
            verdict = {**verdict, 'status': 'INSUFFICIENT_EVIDENCE', 'missing_phase_metrics': missing}
    requested = report['frontend_experiment']
    actual = report.get('b5_actual_frontend_experiment')
    fallback = report.get('b5_k25_auto_fallback_used')
    actual_known = (actual in FRONTENDS and type(fallback) is bool
                    and ((fallback is False and actual == requested)
                         or (fallback is True and requested == 'B5_K25_MANUAL_PLP0'
                             and actual == 'B5_K25_AUTO' and report.get('b5_manual_demod_lock') is False)))
    if not actual_known and verdict['status'] == 'TS_SMOKE_PASS':
        verdict = {**verdict, 'status': 'INSUFFICIENT_EVIDENCE', 'missing_frontend_provenance': True}
    frequency, requested_plp = FRONTENDS[requested]
    return {'schema': 'B5-CAPTURE-ANALYSIS/v1', 'frontend': {'requested_experiment': requested,
            'actual_experiment': actual if actual_known else None,
            'actual_status': 'REPORT_BOUND' if actual_known else 'UNKNOWN_FRONTEND_PROVENANCE',
            'fallback_used': fallback, 'manual_demod_lock': report.get('b5_manual_demod_lock'),
            'frequency_hz': frequency, 'bandwidth_hz': 8_000_000,
            'requested_plp': requested_plp, 'actual_plp': FRONTENDS[actual][1] if actual_known else None},
            'phase_metrics': metrics, 'data_timeline': timeline, 'on_analyses': analyses,
            'ts_smoke': verdict, 'root_cause': 'UNKNOWN', 'monitor_sha256': ts.sha256(monitor_source),
            'limitations': ['Một lượt không chứng minh nguyên nhân RF, nguồn điện, cáp hoặc host.',
                           'SNR/BER/RSSI chỉ lấy giá trị đã xuất; thiếu dữ liệu giữ null, không thay bằng zero.',
                           'FULL/PARTIAL/MISSING và CC/TEI/PAT/PMT/SDT/PCR dùng analyzer B4, giữ cảnh báo thời gian và byte gốc.',
                           'Các khoảng OFF không mở Bulk IN; RSSI chỉ ba mẫu mỗi OFF.']}


def markdown(result):
    lines = ['# Phân tích B5 ngoại tuyến', '', '- TS smoke: `%s`.' % result['ts_smoke']['status'],
             '- Nguyên nhân: `UNKNOWN`; không kết luận từ một lượt.', '',
             '| Pha | Số mẫu | SNR min/median/max (dB) | RSSI |', '| --- | --- | --- | --- |']
    for phase, summary in result['phase_metrics'].items():
        snr = summary['metrics']['snr_db']
        lines.append('| %s | %d | %s / %s / %s | %s |' % (phase, summary['samples'], snr['min'], snr['median'], snr['max'], summary['rssi_policy']))
    return '\n'.join(lines + ['', 'JSON giữ số liệu BER, demod/TS lock, tương quan SNR theo khoảng cách thời gian tới dữ liệu gần nhất, burst/gap và chất lượng TS từng ON.', ''])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--monitor', type=Path, action='append', required=True,
                        help='Repeat for each ON monitor and OFF RSSI JSONL; originals stay unchanged')
    for phase in ('on1', 'on2'):
        parser.add_argument('--' + phase + '-ts', type=Path)
        parser.add_argument('--' + phase + '-audit', type=Path)
    parser.add_argument('--baseline-bytes', type=int)
    parser.add_argument('--cadence-ns', type=int)
    parser.add_argument('--windows-report', type=Path, help='Optional existing B4 TS-CAPTURE-ANALYSIS/v1 reference; no new capture')
    parser.add_argument('--smoke-policy', type=Path)
    parser.add_argument('--expected-smoke-policy-sha256')
    parser.add_argument('--expected-card-sha256')
    parser.add_argument('--json', type=Path)
    parser.add_argument('--markdown', type=Path)
    args = parser.parse_args(argv)
    try:
        inputs = [args.report, *args.monitor, args.on1_ts, args.on1_audit, args.on2_ts, args.on2_audit, args.smoke_policy, args.windows_report]
        outputs = [p for p in (args.json, args.markdown) if p is not None]
        resolved = [p.resolve() for p in outputs]
        if len(set(resolved)) != len(resolved) or any(p.exists() for p in outputs) or any(p and p.resolve() in resolved for p in inputs):
            raise ValueError('Outputs must be new distinct paths, never inputs')
        phase_inputs = {}
        for name in ('ON1', 'ON2'):
            raw, audit = getattr(args, name.lower() + '_ts'), getattr(args, name.lower() + '_audit')
            if bool(raw) != bool(audit):
                raise ValueError('Each ON raw requires its exact audit')
            if raw:
                phase_inputs[name] = (raw.read_bytes(), audit.read_bytes())
        policy = None
        if args.smoke_policy:
            source = args.smoke_policy.read_bytes()
            policy = json.loads(source)
            if (ts.sha256(source) != args.expected_smoke_policy_sha256
                    or not args.expected_card_sha256 or policy.get('card_sha256') != args.expected_card_sha256):
                raise ValueError('Smoke policy and card require explicit matching SHA256 pins')
        report_source = args.report.read_bytes()
        result = analyze(json.loads(report_source), phase_inputs, b'\n'.join(path.read_bytes() for path in args.monitor),
                         baseline_bytes=args.baseline_bytes, cadence_ns=args.cadence_ns, smoke_policy=policy)
        result['report_sha256'] = ts.sha256(report_source)
        if args.windows_report:
            source = args.windows_report.read_bytes()
            windows = json.loads(source)
            if not isinstance(windows, dict) or windows.get('schema') != 'TS-CAPTURE-ANALYSIS/v1':
                raise ValueError('Windows reference must be a B4 TS-CAPTURE-ANALYSIS/v1 report')
            result['windows_reference_report_sha256'] = ts.sha256(source)
            result['windows_comparison'] = {name: compare(windows, analysis)
                                            for name, analysis in result['on_analyses'].items()}
        text = json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + '\n'
        if args.json:
            with args.json.open('x', encoding='utf-8', newline='\n') as output:
                output.write(text)
        else:
            sys.stdout.write(text)
        if args.markdown:
            with args.markdown.open('x', encoding='utf-8', newline='\n') as output:
                output.write(markdown(result))
        return 0
    except (OSError, ValueError, TypeError, KeyError) as exc:
        print('ERROR: %s' % exc, file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
