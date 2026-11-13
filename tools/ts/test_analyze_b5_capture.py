"""B5 regression fixtures reuse B4 native/PSI fixtures; no hardware or captures."""
import contextlib
import copy
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch

import analyze_b5_capture as b5
from test_analyze_ts_capture import continuous, encode_audit, native_audit
from test_compare_b4_capture import report as parity_report


def phases():
    now = 0
    result = []
    for name, duration in b5.LAYOUT:
        result.append({'phase': name, 'start_ns': now, 'end_ns': now + duration * 1_000_000,
                       'clock': 'CLOCK_MONOTONIC', 'bytes': 3760 if name.startswith('ON') else 0,
                       'raw_file': 'received-' + name + '.ts' if name.startswith('ON') else None,
                       'stale_prefix_bytes': 2048 if name == 'ON1' else 0,
                       'audit_run_id': 'synthetic-' + name, 'drained': True, 'quarantined': False})
        now += duration * 1_000_000
    return result


def report():
    return {'b5_enabled': True, 'frontend_experiment': 'B5_K25_MANUAL_PLP0', 'b5_phases': phases()}


def inputs():
    result = {}
    for phase in phases():
        if not phase['phase'].startswith('ON'):
            continue
        rows = native_audit()
        for row in rows:
            row['run_id'] = phase['audit_run_id']
            for key in ('before_ns', 'after_ns', 'reap_ns'):
                if key in row:
                    row[key] += phase['start_ns']
        result[phase['phase']] = continuous(20), encode_audit(rows)
    return result


def monitor():
    rows = []
    for phase in phases():
        for index, snr in enumerate((10, 20, 30)):
            start = phase['start_ns'] + 2_000_000_000 + index * 1_000_000_000
            rows.append({'phase': phase['phase'], 'clock': 'CLOCK_MONOTONIC', 'start_ns': start,
                         'end_ns': start + 10_000, 'snr_db': snr, 'ber': index / 1000,
                         'demod_lock': 'LOCKED', 'ts_lock': index == 0,
                         'rssi_dbm': -60 + index if phase['phase'].startswith('OFF') else None,
                         'schema': 'TBS5530-B5-RSSI/v1' if phase['phase'].startswith('OFF') else 'TBS5530-B5-MONITOR/v1',
                         'sequence': 'ASCOT3_WINDOWS_88_00_87_C0', 'windows_sequence_matches': True,
                         'windows_cleanup_88': '00', 'cleanup_87': 'c0', 'cleanup_succeeded': True})
    return encode_audit(rows)


def policy():
    return {'schema': 'B5-SMOKE-POLICY/v1', 'approved_by_user': True, 'card_sha256': 'a' * 64,
            'max_cc_per_minute': 0, 'max_tei_per_minute': 0, 'min_full_percent': 95, 'pcr_stability_max_relative_spread': 0.01}


class B5Tests(unittest.TestCase):
    def test_reuses_b4_native_and_sync_analyzer_per_on_without_rebasing(self):
        result = b5.analyze(report(), inputs(), monitor())
        self.assertEqual(result['root_cause'], 'UNKNOWN')
        self.assertEqual(result['ts_smoke']['status'], 'INSUFFICIENT_EVIDENCE')
        self.assertEqual(result['on_analyses']['ON1']['analysis_range']['start'], 2048)
        self.assertEqual(result['on_analyses']['ON2']['analysis_range']['start'], 0)
        self.assertEqual(result['on_analyses']['ON1']['stream_sha256'], b5.ts.sha256(continuous(20)))
        self.assertEqual(result['on_analyses']['ON2']['sync']['packets'], 20)
        self.assertEqual(result['phase_metrics']['ON1']['metrics']['snr_db'],
                         {'samples': 3, 'min': 10, 'median': 20, 'max': 30})
        self.assertEqual(result['phase_metrics']['ON1']['metrics']['demod_lock']['median'], 1)
        self.assertEqual(result['phase_metrics']['ON1']['metrics']['ts_lock']['median'], 0)
        self.assertEqual(result['phase_metrics']['OFF1']['rssi_policy'], 'PASS_OFF_THREE_SAMPLES')
        self.assertEqual(result['phase_metrics']['ON1']['metrics']['rssi_dbm']['samples'], 0)

    def test_post5s_tables_are_reassembled_without_reusing_early_pat(self):
        from test_analyze_ts_capture import pat, pmt, psi_packet, sdt
        value = report()
        phase_inputs = {}
        raw = psi_packet(0, pat()) + psi_packet(200, pmt()) + psi_packet(17, sdt()) + continuous(27)
        for phase in value['b5_phases']:
            if not phase['phase'].startswith('ON'):
                continue
            phase['bytes'] = len(raw)
            rows = native_audit(lengths=(1880, 1880, 1880), mappings=True)
            times = iter((100_000_000, 200_000_000, 210_000_000, 6_200_000_000,
                          6_210_000_000, 7_200_000_000, 8_200_000_000, 8_300_000_000))
            reap = None
            for row in rows:
                row['run_id'] = phase['audit_run_id']
                if 'kind' in row:
                    row['before_ns'] = phase['start_ns'] + next(times)
                    row['after_ns'] = row['before_ns'] + 1000
                    if row['kind'] == 2:
                        reap = row['after_ns']
                elif row.get('mapping'):
                    row['reap_ns'] = reap
            phase_inputs[phase['phase']] = raw, encode_audit(rows)
        result = b5.analyze(value, phase_inputs, monitor(), baseline_bytes=1880, cadence_ns=1_000_000_000)
        on2 = result['on_analyses']['ON2']
        self.assertEqual(on2['psi']['pat_programs'], 1)
        self.assertEqual(on2['post5s_tables']['psi']['pat_programs'], 0)
        self.assertEqual(on2['post5s_tables']['analysis_range']['start'], 1880)
        self.assertIsNotNone(on2['after5s_quality']['cc_per_minute'])

    def test_actual_auto_fallback_is_separate_from_requested_manual(self):
        value = report()
        value.update(b5_actual_frontend_experiment='B5_K25_AUTO', b5_k25_auto_fallback_used=True,
                     b5_manual_demod_lock=False)
        result = b5.analyze(value, inputs(), monitor())
        self.assertEqual(result['frontend']['requested_plp'], 'MANUAL0')
        self.assertEqual(result['frontend']['actual_plp'], 'AUTO')
        self.assertEqual(result['frontend']['frequency_hz'], 506_000_000)
        value['b5_manual_demod_lock'] = True
        result = b5.analyze(value, inputs(), monitor())
        self.assertIsNone(result['frontend']['actual_experiment'])
        self.assertEqual(result['frontend']['actual_status'], 'UNKNOWN_FRONTEND_PROVENANCE')

    def test_unknown_values_never_become_zero_or_ts_gated_snr(self):
        rows = b5.jsonl(monitor())
        rows[3]['snr_db'], rows[3]['ber'] = None, None
        rows[4]['snr_db'], rows[4]['ts_lock'] = 30, False
        result = b5.monitor_phases(rows, phases(), {})['ON1']
        self.assertEqual(result['metrics']['snr_db']['samples'], 2)
        self.assertEqual(result['metrics']['snr_db']['min'], 30)
        self.assertEqual(result['metrics']['ber']['samples'], 2)
        self.assertIsNone(result['snr_vs_elapsed_since_last_data']['pearson_r'])

    def test_correlation_separates_program_last_data_from_phase_local_control(self):
        times = {'ON1': [{'timestamp_ns': 11_000_000_000}]}
        result = b5.monitor_phases(b5.jsonl(monitor()), phases(), times)
        correlation = result['ON1']['snr_vs_elapsed_since_last_data']
        self.assertEqual([p['elapsed_since_last_data_ms'] for p in correlation['pairs']], [1000, 2000, 3000])
        self.assertAlmostEqual(correlation['pearson_r'], 1)
        off2 = result['OFF2']['snr_vs_elapsed_since_last_data']['pairs']
        self.assertEqual([p['elapsed_since_last_data_ms'] for p in off2], [31000, 32000, 33000])
        self.assertTrue(all(p['intentional_off_sample'] and p['cross_phase'] for p in off2))
        self.assertEqual(result['ON2']['snr_vs_elapsed_since_phase_data']['samples_before_first_data'], 3)

    def test_timeline_distinguishes_data_gap_and_boundary_no_data(self):
        spans = [{'timestamp_ns': t, 'start': i * 10, 'end': i * 10 + 10}
                 for i, t in enumerate((1_000_000_000, 1_001_000_000, 1_100_000_000))]
        result = b5.data_timeline(spans, phases()[0])
        self.assertEqual([burst['bytes'] for burst in result['bursts']], [20, 10])
        self.assertEqual(result['gaps'][0]['duration_ms'], 99)
        self.assertEqual(b5.data_timeline([], phases()[0])['boundary_intervals'][0]['kind'], 'NO_DATA_IN_PHASE')

    def test_phase_and_native_binding_refuse_wrong_order_epoch_length_and_identity(self):
        for fault in ('order', 'clock', 'length', 'run_id', 'prefix', 'bool-time'):
            value = report()
            if fault == 'order': value['b5_phases'][0]['phase'] = 'ON1'
            elif fault == 'clock': value['b5_phases'][1]['start_ns'] += 2_000_000_000
            elif fault == 'length': value['b5_phases'][1]['bytes'] += 1
            elif fault == 'run_id': value['b5_phases'][1]['audit_run_id'] = 'foreign'
            elif fault == 'prefix': value['b5_phases'][3]['stale_prefix_bytes'] = 2048
            elif fault == 'bool-time': value['b5_phases'][0]['start_ns'] = False
            with self.subTest(fault=fault), self.assertRaises(ValueError):
                b5.analyze(value, inputs(), monitor())

    def test_only_user_policy_can_enable_post5s_smoke_and_missing_stays_unknown(self):
        app = parity_report()
        app['completion_frames']['after_5s']['metrics']['cc_errors'] = 0
        app['psi']['services'][0]['name'] = 'VTV1'
        app['pcr']['windows'] = [{'pid': 100, 'bitrate_bps': 27500000}, {'pid': 100, 'bitrate_bps': 27500000}]
        app['post5s_tables'] = {'psi': app['psi'], 'pcr': app['pcr']}
        analyses = {'ON1': app, 'ON2': copy.deepcopy(app)}
        self.assertEqual(b5.smoke_verdict(analyses, phases())['status'], 'INSUFFICIENT_EVIDENCE')
        self.assertEqual(b5.smoke_verdict(analyses, phases(), policy())['status'], 'TS_SMOKE_PASS')
        analyses['ON2']['completion_frames']['after_5s']['metrics']['tei_packets'] = 1
        self.assertEqual(b5.smoke_verdict(analyses, phases(), policy())['status'], 'TS_SMOKE_FAIL')
        del analyses['ON2']['post5s_tables']
        self.assertEqual(b5.smoke_verdict(analyses, phases(), policy())['status'], 'INSUFFICIENT_EVIDENCE')
        invalid = policy()
        invalid['card_sha256'] = 'z' * 64
        with self.assertRaises(ValueError):
            b5.smoke_verdict(analyses, phases(), invalid)
        analyses['ON2']['post5s_tables'] = copy.deepcopy(analyses['ON1']['post5s_tables'])
        analyses['ON2']['completion_frames']['after_5s']['metrics']['tei_packets'] = 0
        default = b5.smoke_verdict(analyses, phases())
        self.assertEqual(default['criteria']['ON1_vtv_service'], 'PASS')
        self.assertEqual(default['criteria']['ON1_pcr_stable'], 'UNKNOWN')
        self.assertEqual(default['status'], 'INSUFFICIENT_EVIDENCE')
        analyses['ON2']['post5s_tables']['psi']['services'][0]['name'] = 'Other TV'
        self.assertEqual(b5.smoke_verdict(analyses, phases(), policy())['criteria']['ON2_vtv_service'], 'FAIL')
        analyses['ON2']['post5s_tables']['psi']['services'][0]['name'] = 'VTV1'
        analyses['ON2']['post5s_tables']['pcr']['windows'][1]['bitrate_bps'] = 35000000
        self.assertEqual(b5.smoke_verdict(analyses, phases(), policy())['criteria']['ON2_pcr_stable'], 'FAIL')
        for fault in (float('nan'), True, -1, 1):
            value = policy()
            value['max_cc_per_minute'] = fault
            with self.assertRaises(ValueError):
                b5.smoke_verdict(analyses, phases(), value)

    def test_old_linux_rssi_sequence_cannot_qualify_as_windows_samples(self):
        rows = b5.jsonl(monitor())
        rows[0]['sequence'] = 'ASCOT3_USER_SOURCE_88_04'
        rows[0]['windows_sequence_matches'] = False
        result = b5.monitor_phases(rows, phases(), {})['OFF1']
        self.assertEqual(result['invalid_rssi_provenance_samples'], 1)
        self.assertEqual(result['rssi_policy'], 'FAIL_RSSI_WINDOWS_SEQUENCE_PROVENANCE')

    def test_rssi_on_is_not_a_permitted_measurement(self):
        rows = b5.jsonl(monitor())
        rows[3]['rssi_dbm'] = -50
        result = b5.analyze(report(), inputs(), encode_audit(rows))
        self.assertEqual(result['phase_metrics']['ON1']['rssi_policy'], 'FAIL_RSSI_DURING_ON')

    def test_partial_report_keeps_evidence_without_smoke_or_root_cause(self):
        value = report()
        value['b5_phases'] = value['b5_phases'][:2]
        value['b5_phases'][1]['end_ns'] -= 10_000_000_000
        result = b5.analyze(value, inputs(), monitor(), smoke_policy=policy())
        self.assertEqual(result['ts_smoke']['status'], 'INSUFFICIENT_EVIDENCE')
        self.assertEqual(result['root_cause'], 'UNKNOWN')
        self.assertEqual(result['ts_smoke']['criteria']['four_phases_complete'], 'FAIL')

    def test_cli_multiple_monitors_new_outputs_and_policy_pin_required(self):
        data = {'report.json': json.dumps(report()).encode(), 'monitor1.jsonl': monitor(),
                'monitor2.jsonl': b'', 'policy.json': json.dumps(policy()).encode()}
        def read(path):
            return data[path.name]
        output = io.StringIO()
        with patch.object(Path, 'read_bytes', read), contextlib.redirect_stdout(output):
            self.assertEqual(b5.main(['report.json', '--monitor', 'monitor1.jsonl', '--monitor', 'monitor2.jsonl']), 0)
        result = json.loads(output.getvalue())
        self.assertEqual(result['ts_smoke']['status'], 'INSUFFICIENT_EVIDENCE')
        self.assertIn('Nguyên nhân', b5.markdown(result))
        with contextlib.redirect_stderr(io.StringIO()), patch.object(Path, 'read_bytes', read):
            self.assertEqual(b5.main(['report.json', '--monitor', 'monitor1.jsonl', '--smoke-policy', 'policy.json']), 2)
            self.assertEqual(b5.main(['report.json', '--monitor', 'monitor1.jsonl', '--json', 'report.json']), 2)


if __name__ == '__main__':
    unittest.main()
