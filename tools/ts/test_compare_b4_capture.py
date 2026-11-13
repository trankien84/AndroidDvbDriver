"""Focused synthetic parity gates; no hardware/files/network."""
import contextlib
import copy
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch

import compare_b4_capture as cmp


def report():
    return {'schema': 'TS-CAPTURE-ANALYSIS/v1', 'input': {'sha256': 'synthetic', 'native_audit': {
                'writer_mapping': 'VERIFIED', 'writer': {'writer_drop_bytes': 0, 'writer_failure': None,
                                                        'raw_mapping_valid': True},
                'native_data_errors': 0,
                'drain': {'submitted': 30, 'completed': 30, 'outstanding': 0, 'quarantined': False}}},
            'completion_frames': {'status': 'OBSERVED_COMPLETION_TIMING', 'observed_duration_ms': 35000,
                                  'baseline': {'status': 'EXPLICIT_REFERENCE', 'bytes': 540672},
                                  'after_5s': {'status': 'OBSERVED', 'full_percent_including_missing': 100,
                                               'metrics': {'cc_errors': 2, 'tei_packets': 0}}},
            'monitor': {'after_5s': {'status': 'OBSERVED', 'statistics': {
                'metrics': {'snr_db': {'median': 25}}}}},
            'pcr': {'by_pid': [{'pid': 100, 'status': 'estimate', 'median_bitrate_bps': 27_500_000}]},
            'psi': {'transport_stream_id': 10, 'pat_programs': 1, 'pmt_programs': 1,
                    'counts': {'sdt_actual_crc_valid_sections': 1},
                    'services': [{'service_id': 1, 'name': 'Test', 'provider': 'Synthetic',
                                  'pmt_pid': 200, 'pcr_pid': 100, 'transport_stream_id': 10,
                                  'original_network_id': 20, 'streams': [{'pid': 100, 'stream_type': 27}]}]}}


class ComparisonTests(unittest.TestCase):
    def test_exact_inclusive_parity_boundaries_and_not_smoke(self):
        win, app = report(), report()
        app['completion_frames']['after_5s']['full_percent_including_missing'] = 90
        app['completion_frames']['after_5s']['metrics']['cc_errors'] = 3
        app['monitor']['after_5s']['statistics']['metrics']['snr_db']['median'] = 26
        app['pcr']['by_pid'][0]['median_bitrate_bps'] *= 1.01
        result = cmp.compare(win, app)
        self.assertEqual(result['verdict'], 'PARITY_PASS')
        self.assertEqual(result['ts_smoke_pass'], 'NOT_ASSESSED')
        self.assertEqual(result['root_cause'], 'UNKNOWN')
        self.assertEqual(result['criteria']['cc_plus_tei_per_minute_after5s']['windows'], 4)
        self.assertEqual(result['criteria']['cc_plus_tei_per_minute_after5s']['android'], 6)

    def test_each_threshold_fail_is_parity_fail_not_rf(self):
        for gate in ('full', 'cc', 'snr', 'pcr', 'services', 'native', 'drain', 'writer'):
            app = report()
            if gate == 'full': app['completion_frames']['after_5s']['full_percent_including_missing'] = 89.99
            elif gate == 'cc': app['completion_frames']['after_5s']['metrics']['tei_packets'] = 2
            elif gate == 'snr': app['monitor']['after_5s']['statistics']['metrics']['snr_db']['median'] = 26.01
            elif gate == 'pcr': app['pcr']['by_pid'][0]['median_bitrate_bps'] *= 1.02
            elif gate == 'services': app['psi']['services'][0]['name'] = 'Other'
            elif gate == 'native': app['input']['native_audit']['native_data_errors'] = 1
            elif gate == 'drain': app['input']['native_audit']['drain']['outstanding'] = 1
            elif gate == 'writer': app['input']['native_audit']['writer']['writer_drop_bytes'] = 1
            with self.subTest(gate=gate):
                result = cmp.compare(report(), app)
                self.assertEqual(result['verdict'], 'PARITY_FAIL')
                self.assertEqual(result['root_cause'], 'UNKNOWN')

    def test_missing_required_evidence_is_insufficient_even_with_failure(self):
        for field in ('after5s', 'pcr', 'sdt', 'snr', 'writer', 'drain', 'batch64k', 'baseline'):
            app = report()
            app['input']['native_audit']['native_data_errors'] = 1
            if field == 'after5s': app['completion_frames']['after_5s'] = {'status': 'UNKNOWN_CAPTURE_SHORTER_THAN_5S'}
            elif field == 'pcr': app['pcr']['by_pid'] = []
            elif field == 'sdt': app['psi']['counts'] = {}
            elif field == 'snr': app['monitor'] = {}
            elif field == 'writer': app['input']['native_audit']['writer'] = {}
            elif field == 'drain': app['input']['native_audit']['drain'] = {}
            elif field == 'batch64k': app['completion_frames']['status'] = 'UNKNOWN_CHUNK_TIMING_EXCEEDS_15MS'
            elif field == 'baseline': app['completion_frames']['baseline']['bytes'] = 270336
            with self.subTest(field=field):
                result = cmp.compare(report(), app)
                self.assertEqual(result['verdict'], 'INSUFFICIENT_EVIDENCE')
                self.assertTrue(result['unknown_criteria'])
                self.assertIn('native_data_errors_zero', result['failed_criteria'])

    def test_windows_zero_errors_requires_android_zero(self):
        win, app = report(), report()
        win['completion_frames']['after_5s']['metrics']['cc_errors'] = 0
        self.assertEqual(cmp.compare(win, app)['verdict'], 'PARITY_FAIL')
        app['completion_frames']['after_5s']['metrics']['cc_errors'] = 0
        self.assertEqual(cmp.compare(win, app)['verdict'], 'PARITY_PASS')

    def test_pcr_tolerance_explicit_and_diagnostic_not_substitute(self):
        app = report()
        app['pcr']['by_pid'][0]['median_bitrate_bps'] *= 1.02
        result = cmp.compare(report(), app, pcr_tolerance_percent=3)
        self.assertEqual(result['verdict'], 'PARITY_PASS')
        self.assertEqual(result['parameters']['pcr_tolerance_percent'], 3)
        app['pcr']['by_pid'][0]['status'] = 'UNKNOWN_INSUFFICIENT_CONTIGUOUS_PCR'
        app['pcr']['diagnostic_only'] = copy.deepcopy(report()['pcr'])
        self.assertEqual(cmp.compare(report(), app)['verdict'], 'INSUFFICIENT_EVIDENCE')
        for tolerance in (float('nan'), float('inf'), -1):
            with self.assertRaises(ValueError):
                cmp.compare(report(), report(), pcr_tolerance_percent=tolerance)

    def test_optional_prepared_monitor_preserves_external_alignment_and_nulls(self):
        summary = {'schema': 'SIGNAL-MONITOR-SUMMARY/v1', 'alignment': 'VERIFIED_EXTERNAL_HOST_REPORT',
                   'after_5s': report()['monitor']['after_5s']}
        r = cmp.load_monitor(json.dumps(summary).encode())
        self.assertEqual(r['after_5s']['status'], 'OBSERVED')
        self.assertIn('source_summary_sha256', r)
        summary['alignment'] = 'UNKNOWN_EPOCH'
        self.assertEqual(cmp.load_monitor(json.dumps(summary).encode())['after_5s']['status'], 'UNKNOWN_CLOCK_ALIGNMENT')

    def test_windows_qualified_monitor_requires_exact_capture_and_origin(self):
        summary = {'schema': 'tbs5530.monitor-qualified-summary/1', 'capture_sha256': 'golden',
                   'after5s_origin': 'first successful nonempty Bulk82 completion capture timestamp + 5 seconds',
                   'snr_db': {'n': 21, 'min': 12.4, 'median': 13.3, 'max': 19.4},
                   'snr_db_after5s': {'n': 18, 'min': 12.4, 'median': 13.3, 'max': 16.9},
                   'ber': None, 'per': None}
        source = json.dumps(summary).encode()
        r = cmp.load_monitor(source, expected_capture_sha256='golden')
        self.assertEqual(r['after_5s']['status'], 'OBSERVED')
        self.assertEqual(r['after_5s']['statistics']['metrics']['snr_db']['median'], 13.3)
        self.assertIsNone(r['after_5s']['statistics']['metrics']['ber']['median'])
        self.assertIn('UNKNOWN', cmp.load_monitor(source, expected_capture_sha256='other')['after_5s']['status'])
        summary['after5s_origin'] = 'unknown'
        self.assertIn('UNKNOWN', cmp.load_monitor(json.dumps(summary).encode(), expected_capture_sha256='golden')['after_5s']['status'])

    def test_cli_new_outputs_schema_hash_and_refuses_overwrite(self):
        source = json.dumps(report()).encode()
        output = io.StringIO()
        with patch.object(Path, 'read_bytes', lambda _: source), contextlib.redirect_stdout(output):
            self.assertEqual(cmp.main(['win.json', 'android.json']), 0)
        result = json.loads(output.getvalue())
        self.assertEqual(result['verdict'], 'PARITY_PASS')
        self.assertEqual(result['report_inputs'][0]['sha256'], cmp.sha256(source))
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(cmp.main(['win.json', 'android.json', '--json', 'win.json']), 2)
        self.assertIn('PARITY_PASS', cmp.markdown(result))


if __name__ == '__main__':
    unittest.main()
