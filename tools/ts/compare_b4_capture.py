"""Compare offline B4 reports, fail closed on missing parity evidence (stdlib only)."""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import sys

from analyze_ts_capture import monitor_report, sha256


def finite(value):
    return type(value) in (int, float) and math.isfinite(value)


def service_signature(report):
    psi = report.get('psi', {})
    services = psi.get('services', [])
    if (not services or psi.get('pat_programs') != len(services)
            or psi.get('pmt_programs') != len(services)
            or not psi.get('counts', {}).get('sdt_actual_crc_valid_sections')):
        return None
    rows = []
    for service in services:
        if (service.get('pcr_pid') is None or 'transport_stream_id' not in service
                or 'original_network_id' not in service):
            return None
        streams = sorted((s.get('pid'), s.get('stream_type')) for s in service.get('streams', []))
        rows.append((service.get('service_id'), service.get('name'), service.get('provider'),
                     service.get('pmt_pid'), service['pcr_pid'], service['transport_stream_id'],
                     service['original_network_id'], streams))
    return {'transport_stream_id': psi.get('transport_stream_id'), 'services': sorted(rows)}


def compare(windows, android, *, pcr_tolerance_percent=1.0):
    if not finite(pcr_tolerance_percent) or pcr_tolerance_percent < 0:
        raise ValueError('PCR tolerance must be finite and nonnegative')
    criteria = {}

    def criterion(name, win, app, passed, rule):
        criteria[name] = {'status': 'UNKNOWN' if passed is None else 'PASS' if passed else 'FAIL',
                          'windows': win, 'android': app, 'rule': rule}

    def after(report):
        frames = report.get('completion_frames', {})
        a = frames.get('after_5s', {})
        return frames, a if a.get('status') == 'OBSERVED' else {}

    wf, wa = after(windows)
    af, aa = after(android)
    wb, ab = wf.get('baseline', {}), af.get('baseline', {})
    wfpc, afpc = wa.get('full_percent_including_missing'), aa.get('full_percent_including_missing')
    valid_full = (finite(wfpc) and finite(afpc) and wb.get('status') == ab.get('status') == 'EXPLICIT_REFERENCE'
                  and finite(wb.get('bytes')) and wb['bytes'] > 0 and wb.get('bytes') == ab.get('bytes')
                  and wf.get('status') == af.get('status') == 'OBSERVED_COMPLETION_TIMING')
    criterion('full_after5s', wfpc, afpc, afpc >= wfpc - 10 if valid_full else None,
              'FULL% includes inferred MISSING slots; same explicit reference baseline; Android >= Windows - 10 percentage points')

    def error_rate(frames, a):
        metrics = a.get('metrics')
        duration = frames.get('observed_duration_ms')
        if (not isinstance(metrics, dict) or not finite(duration) or duration <= 5000
                or any(not finite(metrics.get(k)) or metrics[k] < 0 for k in ('cc_errors', 'tei_packets'))):
            return None
        return (metrics['cc_errors'] + metrics['tei_packets']) * 60_000 / (duration - 5000)

    wr, ar = error_rate(wf, wa), error_rate(af, aa)
    criterion('cc_plus_tei_per_minute_after5s', wr, ar, ar <= 1.5 * wr if wr is not None and ar is not None else None,
              '(CC errors + TEI packets) / post-5s observed wall minutes; Android <= 1.5 * Windows, including Windows=0')

    def snr(report):
        after_snr = report.get('monitor', {}).get('after_5s', {})
        if after_snr.get('status') != 'OBSERVED':
            return None
        value = (after_snr.get('statistics') or {}).get('metrics', {}).get('snr_db', {}).get('median')
        return value if finite(value) else None

    ws, ans = snr(windows), snr(android)
    criterion('snr_median_after5s', ws, ans, abs(ans - ws) <= 1 if ws is not None and ans is not None else None,
              'Official serialized SNR/CNR median after5s within +/-1 dB; diagnostic curve is not a substitute')

    wservice, aservice = service_signature(windows), service_signature(android)
    criterion('psi_services_pids', wservice, aservice,
              aservice == wservice if wservice is not None and aservice is not None else None,
              'Complete CRC-valid current PAT/PMT/SDT actual, transport/service/name/provider/PMT/PCR/ES PID+type match')

    def pcr_rates(report, signature):
        if signature is None:
            return None
        required = {s[4] for s in signature['services'] if s[4] != 8191}
        observed = {p['pid']: p.get('median_bitrate_bps') for p in report.get('pcr', {}).get('by_pid', [])
                    if p.get('status') == 'estimate' and finite(p.get('median_bitrate_bps')) and p['median_bitrate_bps'] > 0}
        return {pid: observed[pid] for pid in sorted(required)} if required and required <= observed.keys() else None

    wp, ap = pcr_rates(windows, wservice), pcr_rates(android, aservice)
    pcr_match = (wp.keys() == ap.keys()
                 and all(abs(ap[pid] - rate) <= rate * pcr_tolerance_percent / 100 for pid, rate in wp.items())
                 if wp is not None and ap is not None else None)
    criterion('pcr_each_service_pid', wp, ap, pcr_match,
              'Strict contiguous PCR windows for every service PCR PID; relative tolerance %.6g%%; no diagnostic-only substitute' % pcr_tolerance_percent)

    native = android.get('input', {}).get('native_audit', {})
    writer = native.get('writer', {})
    drops, failure, valid = writer.get('writer_drop_bytes'), writer.get('writer_failure'), writer.get('raw_mapping_valid')
    known_writer = (type(drops) is int and 'writer_failure' in writer and type(valid) is bool
                    and native.get('writer_mapping') == 'VERIFIED')
    criterion('writer_drop_zero', None, writer, drops == 0 and failure is None and valid is True if known_writer else None,
              'Android verified completion mappings, writer_drop_bytes=0, writer_failure=null, raw_mapping_valid=true')
    errors = native.get('native_data_errors')
    criterion('native_data_errors_zero', None, errors, errors == 0 if type(errors) is int else None,
              'No pre-drain URB status or nontransient reap syscall errors; EAGAIN/EINTR are not data errors')
    drain = native.get('drain', {})
    drain_known = (type(drain.get('outstanding')) is int and type(drain.get('quarantined')) is bool
                   and type(drain.get('submitted')) is int and type(drain.get('completed')) is int)
    drained = (drain['outstanding'] == 0 and drain['quarantined'] is False
               and drain['submitted'] == drain['completed'] and drain['submitted'] > 0) if drain_known else None
    criterion('drain_audit_certificate', None, drain, drained,
              'AUDIT_SUMMARY_CONFIRMED only: outstanding=0, quarantined=false, submitted=completed>0; not physical host queue proof')
    unknown = [k for k, c in criteria.items() if c['status'] == 'UNKNOWN']
    failed = [k for k, c in criteria.items() if c['status'] == 'FAIL']
    return {'schema': 'B4-CAPTURE-COMPARISON/v1',
            'verdict': 'INSUFFICIENT_EVIDENCE' if unknown else 'PARITY_FAIL' if failed else 'PARITY_PASS',
            'ts_smoke_pass': 'NOT_ASSESSED', 'ts_smoke_reason': 'No agreed near-clean threshold; parity is not TS_SMOKE_PASS.',
            'root_cause': 'UNKNOWN', 'criteria': criteria, 'unknown_criteria': unknown, 'failed_criteria': failed,
            'parameters': {'full_tolerance_percentage_points': 10, 'error_rate_multiplier': 1.5,
                           'snr_tolerance_db': 1, 'pcr_tolerance_percent': pcr_tolerance_percent},
            'inputs': {'windows_capture_sha256': windows.get('input', {}).get('sha256'),
                       'android_capture_sha256': android.get('input', {}).get('sha256')},
            'caveats': ['No automatic RF/host/FX2 cause attribution. SOURCE_LIKE is an inference, not proof against FX2 loss.',
                        'Missing required post-5s/PCR/SDT/SNR/writer/drain evidence takes precedence over failures.',
                        'Monitor cross-host alignment and physical cold-start provenance must be verified by the host report.']}


def markdown(result):
    lines = ['# So sánh đối chứng B4', '', '- Kết luận: `%s`.' % result['verdict'],
             '- TS_SMOKE_PASS: `NOT_ASSESSED`; chưa có ngưỡng gần sạch được thống nhất.',
             '- Nguyên nhân phần cứng: `UNKNOWN`.', '', '| Tiêu chí | Kết quả | Quy tắc |', '| --- | --- | --- |']
    for name, criterion in result['criteria'].items():
        lines.append('| %s | %s | %s |' % (name, criterion['status'], criterion['rule']))
    lines += ['', 'JSON giữ số liệu hai phía, nguồn hash, ngưỡng và tiêu chí thiếu. Không kết luận RF hoặc host từ parity.', '']
    return '\n'.join(lines)


def load_monitor(source, timing_metadata=None, expected_capture_sha256=None):
    try:
        prepared = json.loads(source)
    except ValueError:
        prepared = None
    if isinstance(prepared, dict) and prepared.get('schema') == 'tbs5530.monitor-qualified-summary/1':
        matches = bool(expected_capture_sha256) and prepared.get('capture_sha256') == expected_capture_sha256
        same_origin = prepared.get('after5s_origin') == 'first successful nonempty Bulk82 completion capture timestamp + 5 seconds'
        def stats(values):
            if not isinstance(values, dict):
                values = {}
            snr = {'samples': values.get('n', 0), 'min': values.get('min'),
                   'median': values.get('median'), 'max': values.get('max')}
            return {'samples': values.get('n', 0), 'lock': {'LOCKED': values.get('n', 0)},
                    'sample_scope': 'qualified LOCKED SNR samples only, not every lock poll',
                    'metrics': {'snr_db': snr, **{k: {'samples': 0, 'min': None, 'median': None, 'max': None}
                                                for k in ('ber', 'per', 'fer')}}}
        after = stats(prepared.get('snr_db_after5s'))
        return {'schema': 'SIGNAL-MONITOR-SUMMARY/v1', 'status': 'OBSERVED_QUALIFIED_EXTERNAL_SUMMARY',
                'source_summary_sha256': sha256(source), 'source_summary_bytes': len(source),
                'capture_sha256': prepared.get('capture_sha256'), 'qualification': prepared.get('qualification'),
                'alignment': 'CAPTURE_SHA256_AND_FIRST_BULK_ORIGIN_MATCH' if matches and same_origin else 'UNKNOWN_CLOCK_OR_CAPTURE_ALIGNMENT',
                'total': stats(prepared.get('snr_db')),
                'after_5s': {'status': 'OBSERVED' if matches and same_origin and after['samples'] else 'UNKNOWN_CLOCK_OR_CAPTURE_ALIGNMENT',
                             'statistics': after if matches and same_origin else None}}
    if isinstance(prepared, dict) and prepared.get('schema') == 'SIGNAL-MONITOR-SUMMARY/v1':
        prepared['source_summary_sha256'] = sha256(source)
        prepared['source_summary_bytes'] = len(source)
        if not prepared.get('alignment') or prepared['alignment'].startswith('UNKNOWN'):
            prepared['after_5s'] = {'status': 'UNKNOWN_CLOCK_ALIGNMENT', 'statistics': None}
        return prepared
    return monitor_report(source, timing_metadata)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('windows', type=Path)
    parser.add_argument('android', type=Path)
    parser.add_argument('--windows-monitor', type=Path)
    parser.add_argument('--android-monitor', type=Path)
    parser.add_argument('--pcr-tolerance-percent', type=float, default=1.0)
    parser.add_argument('--json', type=Path)
    parser.add_argument('--markdown', type=Path)
    args = parser.parse_args(argv)
    try:
        inputs = [args.windows, args.android, args.windows_monitor, args.android_monitor]
        outputs = [p for p in (args.json, args.markdown) if p is not None]
        resolved = [p.resolve() for p in outputs]
        if (len(set(resolved)) != len(resolved) or any(p.exists() for p in outputs)
                or any(p is not None and p.resolve() in resolved for p in inputs)):
            raise ValueError('Outputs must be distinct new paths, never existing files or inputs')
        source = [args.windows.read_bytes(), args.android.read_bytes()]
        reports = [json.loads(s) for s in source]
        if any(not isinstance(r, dict) or r.get('schema') != 'TS-CAPTURE-ANALYSIS/v1' for r in reports):
            raise ValueError('Expected TS-CAPTURE-ANALYSIS/v1 reports')
        for report, monitor in zip(reports, (args.windows_monitor, args.android_monitor)):
            if monitor is not None:
                report['monitor'] = load_monitor(monitor.read_bytes(), report.get('input', {}).get('native_audit'),
                                                  report.get('input', {}).get('sha256'))
                report['monitor']['path'] = str(monitor.resolve())
        result = compare(*reports, pcr_tolerance_percent=args.pcr_tolerance_percent)
        result['monitor_inputs'] = {'windows': reports[0].get('monitor'), 'android': reports[1].get('monitor')}
        result['report_inputs'] = [{'path': str(p.resolve()), 'sha256': sha256(s), 'bytes': len(s)}
                                   for p, s in zip((args.windows, args.android), source)]
        encoded = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
        if args.json:
            with args.json.open('x', encoding='utf-8', newline='\n') as output:
                output.write(encoded)
        else:
            sys.stdout.write(encoded)
        if args.markdown:
            with args.markdown.open('x', encoding='utf-8', newline='\n') as output:
                output.write(markdown(result))
        return 0
    except (OSError, ValueError) as exc:
        print('ERROR: %s' % exc, file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
