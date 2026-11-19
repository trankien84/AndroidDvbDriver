"""Focused offline Phase4 fixtures; không thiết bị hoặc ghi tệp tạm."""
import contextlib
import copy
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch

import analyze_phase4_comparison as p4


def sample(name, stamp, *, ber=0, snr=20, rssi=None, lock='LOCKED'):
    return {'phase': name, 'clock': p4.CLOCK, 'start_ns': stamp, 'end_ns': stamp + 1,
            'ber': ber, 'snr_db': snr, 'rssi_dbm': rssi, 'demod_lock': lock}


def capture():
    phases = []
    for index, name in enumerate(p4.PHASES):
        start = index * 10_000_000_000
        phases.append({'phase': name, 'clock': p4.CLOCK, 'start_ns': start, 'end_ns': start + 9_000_000_000,
                       'samples': [sample(name, start + 1, rssi=-60 if name.startswith('OFF') else None),
                                   sample(name, start + 2, ber=0.0002 if name.startswith('ON') else 0,
                                          snr=18 if name.startswith('ON') else 20)]})
    return {'schema': p4.SCHEMA, 'id': 'fixture', 'phases': phases,
            'provenance': {'source': 'synthetic', 'drive': '8mA', 'layout': 'fixture-33cm',
                           'frontend': {'frequency_hz': 506000000, 'bandwidth_hz': 8000000, 'plp': 'MANUAL0'}}}


def completion(stamp, length=188, status=0, stage='data'):
    return {'timestamp_ns': stamp, 'actual_length': length, 'status': status,
            'stage': stage, 'clock': p4.CLOCK}


def timed_phase():
    # First completion at 1 s; post-warmup window [6, 6.48] has two full bins.
    return {'phase': 'ON1', 'clock': p4.CLOCK, 'start_ns': 0, 'end_ns': 6_480_000_000,
            'completions_complete': True,
            'completions': [completion(stamp) for stamp in
                            (1_000_000_000, 6_000_000_000, 6_010_000_000, 6_020_000_000,
                             6_240_000_000, 6_250_000_000, 6_260_000_000)],
            'quality_after5s': {'clock': p4.CLOCK, 'start_ns': 6_000_000_000, 'end_ns': 6_480_000_000,
                                'ts_bytes': 1128, 'cc_errors': 2, 'tei_packets': 1}}


class MetricsTests(unittest.TestCase):
    def test_ber_fraction_median_and_pooled_samples_not_medians(self):
        value = capture()
        value['phases'][2]['samples'] = [sample('OFF2', 20_000_000_001, snr=40)]
        result = p4.summarize(value)
        self.assertEqual(result['groups']['OFF']['ber']['zero_count'], 3)
        self.assertEqual(result['groups']['OFF']['ber']['zero_fraction'], 1)
        self.assertEqual(result['groups']['ON']['ber']['nonzero_fraction'], 0.5)
        self.assertEqual(result['groups']['ON']['ber']['median'], 0.0001)
        self.assertEqual(result['groups']['OFF']['snr_db']['median'], 20)
        self.assertEqual(result['snr_drop_db'], {'1': 1, '2': 21})

    def test_percentile_linear_and_single_sample(self):
        self.assertEqual(p4.percentile([40, 10, 30, 20], 0.1), 13)
        self.assertEqual(p4.percentile([7], 0.1), 7)
        self.assertIsNone(p4.percentile([], 0.1))

    def test_unknown_not_zero_and_no_ts_bit_lock_inference(self):
        value = capture()
        for phase in value['phases']:
            phase['samples'] = [dict(sample(phase['phase'], phase['start_ns'] + 1),
                                     ber=None, snr_db=None, demod_lock=None, ts_lock=False)]
        result = p4.summarize(value)
        self.assertIsNone(result['groups']['OFF']['ber']['zero_fraction'])
        self.assertIsNone(result['groups']['ON']['snr_db']['median'])
        self.assertIsNone(result['phases']['ON1']['demod_lock_losses'])
        self.assertIsNone(result['phases']['ON1']['urb_errors'])
        self.assertIsNone(result['phases']['ON1']['duty_median_percent'])

    def test_summary_rssi_and_loss_episodes(self):
        value = capture()
        phase = value['phases'][0]
        start = phase['start_ns']
        phase['samples'] = [sample('OFF1', start + index + 1, lock=state)
                            for index, state in enumerate(('LOST', 'LOST', 'LOCKED', 'LOST'))]
        phase['samples'] += [{'summary': True, 'phase': 'OFF1', 'demod_lock': 'LOST'},
                             {'schema': 'TBS5530-B5-RSSI/v1', 'phase': 'OFF1', 'clock': p4.CLOCK,
                              'start_ns': 8, 'end_ns': 9, 'rssi_dbm': -64.85}]
        result = p4.summarize(value)
        self.assertEqual(result['phases']['OFF1']['demod_lock_losses'], 2)
        self.assertEqual(result['phases']['OFF1']['ber']['samples'], 4)
        self.assertEqual(result['phases']['OFF1']['rssi_dbm']['median'], -64.85)

    def test_invalid_sample_clock_causes_warning_not_false_loss_zero(self):
        value = capture()
        value['phases'][0]['samples'][0]['clock'] = 'ELAPSED_REALTIME'
        result = p4.summarize(value)
        self.assertTrue(result['warnings'])
        self.assertIsNone(result['phases']['OFF1']['demod_lock_losses'])
        self.assertEqual(result['phases']['ON1']['demod_lock_losses'], 0)

    def test_bad_metrics_bool_negative_ber_nan_and_duplicate_rejected(self):
        for field, bad in (('ber', -1), ('ber', 2), ('snr_db', float('nan')), ('rssi_dbm', True)):
            with self.subTest(field=field, bad=bad):
                value = capture()
                value['phases'][0]['samples'][0][field] = bad
                with self.assertRaises(ValueError):
                    p4.summarize(value)
        value = capture()
        value['phases'][0]['samples'] *= 2
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            p4.summarize(value)

    def test_nested_metrics_and_all_zero_real_values_are_preserved(self):
        value = capture()
        phase = value['phases'][0]
        phase['samples'] = [{'clock': p4.CLOCK, 'start_ns': 1, 'end_ns': 2,
                             'metrics': {'ber': 0, 'snr_db': 0, 'demod_lock': 'LOCKED'}}]
        result = p4.summarize(value)
        self.assertEqual(result['phases']['OFF1']['snr_db']['median'], 0)
        self.assertEqual(result['phases']['OFF1']['ber']['zero_count'], 1)


class TimingTests(unittest.TestCase):
    def test_duty_is_duration_not_completion_count_and_rates_include_silence(self):
        result = p4._timing(timed_phase())
        self.assertEqual(result['cycle_count'], 2)
        self.assertEqual([row['active_ns'] for row in result['cycles']], [20_000_000, 20_000_000])
        self.assertEqual(result['cycles'][0]['start_ns'], 6_000_000_000)
        self.assertAlmostEqual(result['duty_median_percent'], 20 / 240 * 100)
        self.assertAlmostEqual(result['duty_total_percent'], 20 / 240 * 100)
        self.assertEqual(result['burst_median_ms'], 20)
        self.assertAlmostEqual(result['quality_after5s']['ts_mbps'], 1128 * 8 / 0.48 / 1e6)
        self.assertEqual(result['quality_after5s']['cc_per_min'], 250)
        self.assertEqual(result['quality_after5s']['tei_per_min'], 125)

    def test_empty_cycle_and_singleton_zero_duration(self):
        phase = timed_phase()
        phase['completions'] = [completion(1_000_000_000), completion(6_000_000_000)]
        phase['quality_after5s']['ts_bytes'] = 188
        result = p4._timing(phase)
        self.assertEqual(result['cycle_count'], 2)
        self.assertEqual(result['duty_median_percent'], 0)
        self.assertEqual(result['burst_median_ms'], 0)

    def test_exact_gap_threshold_and_bin_boundary_clipping(self):
        phase = timed_phase()
        phase['completions'] = [completion(stamp) for stamp in
                                (1_000_000_000, 6_230_000_000, 6_245_000_000, 6_260_000_001)]
        phase['quality_after5s']['ts_bytes'] = 564
        result = p4._timing(phase)
        self.assertAlmostEqual(result['duty_total_percent'], 15 / 480 * 100)
        self.assertEqual(result['burst_median_ms'], 7.5)

    def test_tail_partial_bin_excluded_and_short_capture_unknown(self):
        phase = timed_phase()
        phase['end_ns'] += 100_000_000
        phase['quality_after5s']['end_ns'] = phase['end_ns']
        self.assertEqual(p4._timing(phase)['cycle_count'], 2)
        phase['end_ns'] = 1_000_000_001
        phase['completions'] = [completion(1_000_000_000)]
        phase.pop('quality_after5s')
        result = p4._timing(phase)
        self.assertIsNone(result['duty_median_percent'])
        self.assertIsNone(result['quality_after5s'])

    def test_no_plain_ts_or_incomplete_timestamps_fabricate_duty(self):
        for change in ({'completions': None}, {'completions_complete': False},
                       {'completions': [{'actual_length': 188}]}, {'clock': None}, {'completions': []}):
            with self.subTest(change=change):
                phase = {**timed_phase(), **change, 'raw_ts_bytes': 1000000}
                result = p4._timing(phase)
                self.assertIsNone(result['duty_median_percent'])
                self.assertIsNone(result['quality_after5s'])

    def test_data_errors_and_drain_are_not_successful_data(self):
        phase = timed_phase()
        phase['completions'].insert(1, completion(2_000_000_000, status=-75))
        phase['completions'].append(completion(None, status=-2, stage='drain'))
        result = p4._timing(phase)
        self.assertEqual(result['burst_median_ms'], 20)
        phase['urb_errors'] = 1
        phase['urb_errors_complete'] = True
        phase['samples'] = []
        result = p4.summarize({'id': 'fixture', 'phases': [phase]})
        self.assertEqual(result['phases']['ON1']['urb_errors'], 1)

    def test_invalid_completion_order_clock_type_and_quality_alignment(self):
        for field, bad in (('clock', 'UTC'), ('status', False), ('actual_length', -1), ('timestamp_ns', 99_000_000_000)):
            with self.subTest(field=field):
                phase = timed_phase()
                phase['completions'][0][field] = bad
                with self.assertRaises(ValueError):
                    p4._timing(phase)
        phase = timed_phase()
        phase['completions'].reverse()
        with self.assertRaises(ValueError):
            p4._timing(phase)
        phase = timed_phase()
        phase['quality_after5s']['start_ns'] = 5_000_000_000
        with self.assertRaisesRegex(ValueError, 'Quality window'):
            p4._timing(phase)

    def test_missing_quality_field_unknown_not_zero_and_bad_counts_fail(self):
        phase = timed_phase()
        phase['quality_after5s'].pop('tei_packets')
        result = p4._timing(phase)
        self.assertIsNone(result['quality_after5s']['tei_per_min'])
        phase['quality_after5s']['cc_errors'] = -1
        with self.assertRaises(ValueError):
            p4._timing(phase)


    def test_quality_cannot_exceed_payload_or_tei_packet_count(self):
        for field, bad in (('ts_bytes', 1129), ('ts_bytes', 18800), ('tei_packets', 7)):
            with self.subTest(field=field, bad=bad):
                phase = timed_phase()
                phase['quality_after5s'][field] = bad
                with self.assertRaises(ValueError):
                    p4._timing(phase)


class ComparisonTests(unittest.TestCase):
    def test_equal_threshold_and_over_threshold_each_off(self):
        baseline = p4.summarize(capture())
        for name in ('OFF1', 'OFF2'):
            for field in ('snr_db', 'rssi_dbm'):
                with self.subTest(name=name, field=field):
                    candidate = copy.deepcopy(baseline)
                    candidate['phases'][name][field]['median'] += 2
                    result = p4.compare_runs(baseline, candidate)
                    self.assertEqual(result['off_baseline'], 'WITHIN_2_DB')
                    self.assertEqual(result['comparison'], 'DESCRIPTIVE_ONLY')
                    candidate['phases'][name][field]['median'] += 0.0001
                    self.assertEqual(p4.compare_runs(baseline, candidate)['off_baseline'], 'INCONCLUSIVE')

    def test_missing_off_and_provenance_and_frontend_mismatch(self):
        baseline = p4.summarize(capture())
        candidate = copy.deepcopy(baseline)
        candidate['phases'].pop('OFF2')
        self.assertEqual(p4.compare_runs(baseline, candidate)['off_baseline'], 'UNKNOWN')
        candidate = copy.deepcopy(baseline)
        candidate['provenance'].pop('drive')
        self.assertEqual(p4.compare_runs(baseline, candidate)['comparison'], 'INCONCLUSIVE')
        candidate = copy.deepcopy(baseline)
        candidate['provenance']['frontend']['plp'] = 'AUTO'
        self.assertFalse(p4.compare_runs(baseline, candidate)['same_frontend'])

    def test_no_automatic_emi_pass_even_with_provenance_and_perfect_metrics(self):
        baseline = p4.summarize(capture())
        result = p4.compare_runs(baseline, baseline)
        self.assertEqual(result['emi'], 'UNKNOWN_NOT_AUTOMATICALLY_ASSESSED')
        self.assertNotIn('PASS', result['emi'])


class DumpTests(unittest.TestCase):
    def fixture(self):
        from test_analyze_ts_capture import packet
        raw = b''.join(packet(100, 14, tei=True) for _ in range(8))
        raw += b''.join(packet(100, 9 if n == 4 else n % 16, payload=bytes([n]), tei=n == 9) for n in range(12))
        sidecar = {'schema': 'TBS5530-DUMP-DIAGNOSTICS/v1', 'clock': p4.CLOCK,
                   'ts_file': 'dump.ts', 'ts_size': len(raw), 'recording_start_ns': 1_000_000_000,
                   'recording_end_ns': 7_000_000_000, 'native_window_start_ns': 0,
                   'native_window_end_ns': 8_000_000_000, 'native_finished': True,
                   'completions_complete': True, 'overflow': False, 'clock_invalid': False,
                   'drive': 85, 'frequency_hz': 506000000, 'bandwidth_hz': 8000000,
                   'timestamp_kind': 'userspace_after_reap_not_kernel_completion',
                   'source': 'SYNTHETIC_OFFLINE_FIXTURE',
                   'completions': [completion(t) for t in (1_100_000_000, 6_000_000_000, 6_010_000_000,
                                                          6_100_000_000, 6_110_000_000, 6_120_000_000,
                                                          6_340_000_000, 6_350_000_000, 6_360_000_000)],
                   'consumer_offsets_complete': True,
                   'consumer_offsets': [{'timestamp_ns': 1_000_000_000, 'byte_offset': 0},
                                        {'timestamp_ns': 5_900_000_000, 'byte_offset': 7 * 188},
                                        {'timestamp_ns': 6_100_000_000, 'byte_offset': 8 * 188},
                                        {'timestamp_ns': 7_000_000_000, 'byte_offset': len(raw)}]}
        return sidecar, raw

    def load(self, sidecar, raw, explicit=False):
        source = json.dumps(sidecar).encode()
        def read(path):
            return source if path.name.endswith('.json') else raw
        with patch.object(Path, 'read_bytes', read):
            return p4.load_dump(Path('fixture') / 'dump.ts.usb.json', Path('elsewhere') / 'dump.ts' if explicit else None)

    def test_coarse_marker_uses_actual_duration_fresh_cc_and_same_duty_window(self):
        sidecar, raw = self.fixture()
        result = p4.summarize(self.load(sidecar, raw))
        phase = result['phases']['ON1']
        quality, mapping = phase['quality_after5s'], phase['dump_alignment']
        self.assertEqual(mapping['crop_start_offset'], 8 * 188)
        self.assertEqual(mapping['target_start_ns'], 6_000_000_000)
        self.assertEqual(mapping['threshold_lateness_ns'], 100_000_000)
        self.assertFalse(mapping['interpolation'])
        self.assertEqual(quality['duration_s'], 0.9)
        self.assertEqual(quality['ts_bytes'], 12 * 188)
        self.assertEqual(quality['cc_errors'], 2)
        self.assertEqual(quality['tei_packets'], 1)
        self.assertAlmostEqual(quality['ts_mbps'], 12 * 188 * 8 / 0.9 / 1e6)
        self.assertAlmostEqual(quality['cc_per_min'], 2 * 60 / 0.9)
        self.assertEqual(phase['cycles'][0]['start_ns'], quality['start_ns'])
        self.assertEqual(phase['cycle_count'], 3)
        self.assertEqual(phase['urb_errors'], 0)
        self.assertEqual(result['provenance']['drive'], 85)
        self.assertIn('not_kernel', result['provenance']['timestamp_kind'])
        self.assertEqual(result['emi'], 'UNKNOWN_NOT_AUTOMATICALLY_ASSESSED')

    def test_exact_five_second_marker_and_explicit_raw_path(self):
        sidecar, raw = self.fixture()
        sidecar['consumer_offsets'][2]['timestamp_ns'] = 6_000_000_000
        result = p4.summarize(self.load(sidecar, raw, explicit=True))['phases']['ON1']
        self.assertEqual(result['quality_after5s']['duration_s'], 1)
        self.assertEqual(result['dump_alignment']['threshold_lateness_ns'], 0)
        self.assertEqual(result['timing_start_ns'], 6_000_000_000)

    def test_missing_incomplete_or_unanchored_mapping_keeps_quality_unknown_but_duty_known(self):
        for mutation in ('missing', 'incomplete', 'start', 'end'):
            with self.subTest(mutation=mutation):
                sidecar, raw = self.fixture()
                if mutation == 'missing':
                    sidecar.pop('consumer_offsets')
                elif mutation == 'incomplete':
                    sidecar['consumer_offsets_complete'] = False
                elif mutation == 'start':
                    sidecar['consumer_offsets'][0]['byte_offset'] = 188
                else:
                    sidecar['consumer_offsets'][-1]['byte_offset'] -= 188
                result = p4.summarize(self.load(sidecar, raw))['phases']['ON1']
                self.assertIsNone(result['quality_after5s'])
                self.assertIsNotNone(result['duty_total_percent'])
                self.assertEqual(result['timing_start_ns'], 6_000_000_000)
                self.assertTrue(result['dump_alignment']['status'].startswith('UNKNOWN'))

    def test_incomplete_native_does_not_invalidate_independent_consumer_quality(self):
        for field, value in (('completions_complete', False), ('native_finished', False),
                             ('overflow', True), ('truncated', True), ('clock_invalid', True),
                             ('native_window_start_ns', 2_000_000_000), ('native_window_end_ns', 6_500_000_000)):
            with self.subTest(field=field):
                sidecar, raw = self.fixture()
                sidecar[field] = value
                phase = p4.summarize(self.load(sidecar, raw))['phases']['ON1']
                self.assertIsNotNone(phase['quality_after5s'])
                self.assertIsNone(phase['duty_total_percent'])
                self.assertIsNone(phase['urb_errors'])

    def test_flagged_consumer_rollback_is_unknown_but_does_not_block_native_duty(self):
        sidecar, raw = self.fixture()
        sidecar['consumer_offsets_complete'] = False
        sidecar['consumer_offsets'][2]['timestamp_ns'] = 4_000_000_000
        sidecar['consumer_offsets'][2]['byte_offset'] = 0
        phase = p4.summarize(self.load(sidecar, raw))['phases']['ON1']
        self.assertIsNone(phase['quality_after5s'])
        self.assertIsNotNone(phase['duty_total_percent'])

    def test_no_native_to_ts_byte_mapping_is_invented(self):
        sidecar, raw = self.fixture()
        sidecar.pop('consumer_offsets')
        for row in sidecar['completions']:
            row['actual_length'] = 65536
        phase = p4.summarize(self.load(sidecar, raw))['phases']['ON1']
        self.assertIsNone(phase['quality_after5s'])
        self.assertIsNotNone(phase['duty_median_percent'])

    def test_empty_complete_native_window_has_zero_observed_duty_not_fake_ts_metrics(self):
        sidecar, raw = self.fixture()
        sidecar['completions'] = []
        sidecar.pop('consumer_offsets')
        phase = p4.summarize(self.load(sidecar, raw))['phases']['ON1']
        self.assertEqual(phase['duty_total_percent'], 0)
        self.assertIsNone(phase['quality_after5s'])

    def test_marker_only_at_end_after_five_seconds_has_no_rate_window(self):
        sidecar, raw = self.fixture()
        sidecar['consumer_offsets'] = [sidecar['consumer_offsets'][0], sidecar['consumer_offsets'][-1]]
        phase = p4.summarize(self.load(sidecar, raw))['phases']['ON1']
        self.assertIsNone(phase['quality_after5s'])
        self.assertEqual(phase['dump_alignment']['status'], 'UNKNOWN_NO_POST5S_OBSERVATION_WINDOW')
        sidecar['recording_end_ns'] = 6_000_000_000
        sidecar['consumer_offsets'][-1]['timestamp_ns'] = 6_000_000_000
        sidecar['completions'] = []
        phase = p4.summarize(self.load(sidecar, raw))['phases']['ON1']
        self.assertIsNone(phase['cycle_count'])
        self.assertIsNone(phase['quality_after5s'])

    def test_mid_packet_crop_advances_sync_without_rebasing_offsets(self):
        sidecar, raw = self.fixture()
        sidecar['consumer_offsets'][2]['byte_offset'] += 1
        phase = p4.summarize(self.load(sidecar, raw))['phases']['ON1']
        mapping = phase['dump_alignment']
        self.assertEqual(mapping['crop_start_offset'], 8 * 188 + 1)
        self.assertEqual(mapping['sync_segments'][0]['start'], 9 * 188)
        self.assertEqual(mapping['leading_bytes'], 187)
        self.assertEqual(phase['quality_after5s']['ts_bytes'], 11 * 188)

    def test_sync_break_and_tei_inside_crop_are_not_deleted_or_hidden(self):
        sidecar, raw = self.fixture()
        raw = raw[:14 * 188] + b'bad' + raw[14 * 188:]
        sidecar['ts_size'] = len(raw)
        sidecar['consumer_offsets'][-1]['byte_offset'] = len(raw)
        phase = p4.summarize(self.load(sidecar, raw))['phases']['ON1']
        self.assertEqual(phase['dump_alignment']['sync_breaks'], 1)
        self.assertEqual(phase['dump_alignment']['unparsed_bytes'], 3)
        self.assertEqual(phase['quality_after5s']['tei_packets'], 1)
        self.assertEqual(phase['quality_after5s']['ts_bytes'], 12 * 188)

    def test_corrupt_consumer_time_offset_types_rollback_or_capacity_rejected(self):
        for mutation in ('time', 'offset', 'bool', 'beyond', 'capacity', 'duplicate'):
            with self.subTest(mutation=mutation):
                sidecar, raw = self.fixture()
                rows = sidecar['consumer_offsets']
                if mutation == 'time':
                    rows[2]['timestamp_ns'] = 5_000_000_000
                elif mutation == 'offset':
                    rows[2]['byte_offset'] = 0
                elif mutation == 'bool':
                    rows[2]['byte_offset'] = True
                elif mutation == 'beyond':
                    rows[2]['byte_offset'] = len(raw) + 1
                elif mutation == 'capacity':
                    sidecar['consumer_offsets'] = rows * 1025
                else:
                    rows.insert(2, rows[1])
                with self.assertRaises(ValueError):
                    self.load(sidecar, raw)

    def test_unsafe_filename_size_hash_and_recording_clock_fail_closed(self):
        for field, value in (('ts_file', '..\\dump.ts'), ('ts_file', '../dump.ts'), ('ts_file', 'C:dump.ts'),
                             ('ts_size', 1), ('ts_sha256', 'bad'), ('clock', 'ELAPSED_REALTIME'),
                             ('recording_end_ns', 0), ('native_window_start_ns', -1)):
            with self.subTest(field=field, value=value):
                sidecar, raw = self.fixture()
                sidecar[field] = value
                with self.assertRaises(ValueError):
                    self.load(sidecar, raw)

    def test_no_sync_keeps_rates_unknown_and_failed_urb_status_is_retained(self):
        sidecar, raw = self.fixture()
        raw = b'X' * len(raw)
        sidecar['completions'][2]['status'] = -75
        phase = p4.summarize(self.load(sidecar, raw))['phases']['ON1']
        self.assertIsNone(phase['quality_after5s'])
        self.assertEqual(phase['dump_alignment']['status'], 'UNKNOWN_NO_TS_SYNC_IN_CROP')
        self.assertEqual(phase['urb_errors'], 1)

    def test_missing_native_fields_no_data_and_outside_recording_window(self):
        sidecar, raw = self.fixture()
        for field in ('native_window_start_ns', 'native_finished', 'overflow'):
            broken = copy.deepcopy(sidecar)
            broken.pop(field)
            phase = p4.summarize(self.load(broken, raw))['phases']['ON1']
            self.assertIsNone(phase['duty_total_percent'])
            self.assertIsNotNone(phase['quality_after5s'])
        for mutation in ('outside', 'unordered', 'wrong_clock'):
            broken = copy.deepcopy(sidecar)
            if mutation == 'outside':
                broken['completions'][0]['timestamp_ns'] = 0
            elif mutation == 'unordered':
                broken['completions'].reverse()
            else:
                broken['completions'][0]['clock'] = 'UTC'
            with self.assertRaises(ValueError):
                self.load(broken, raw)

    def test_cli_sidecar_loader_and_explicit_path_count_guard(self):
        sidecar, raw = self.fixture()
        loaded = self.load(sidecar, raw)
        output = io.StringIO()
        with patch.object(p4, 'load_b5', return_value={**capture(), 'id': 'B5'}), \
                patch.object(p4, 'load_dump', return_value=loaded) as loader, contextlib.redirect_stdout(output):
            self.assertEqual(p4.main(['--b5', 'b5', '--dump-sidecar', 'dump.ts.usb.json', '--dump-ts', 'dump.ts']), 0)
            loader.assert_called_once_with(Path('dump.ts.usb.json'), Path('dump.ts'))
        self.assertIn('| dump |', output.getvalue())
        self.assertIn('OBSERVED_CONSUMER_CROP', output.getvalue())
        with patch.object(p4, 'load_b5') as baseline, contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(p4.main(['--b5', 'b5', '--dump-ts', 'dump.ts']), 2)
            baseline.assert_not_called()


class InputTests(unittest.TestCase):
    def test_inline_and_relative_jsonl_input(self):
        value = capture()
        rows = value['phases'][0]['samples']
        value['phases'][0]['samples'] = 'samples.jsonl'
        manifest = json.dumps(value)
        def read(path, **kwargs):
            return '\n'.join(json.dumps(row) for row in rows) if path.name == 'samples.jsonl' else manifest
        with patch.object(Path, 'read_text', read), patch.object(Path, 'read_bytes', return_value=manifest.encode()):
            loaded = p4.load_capture(Path('fixtures') / 'capture.json')
        self.assertEqual(loaded['phases'][0]['samples'], rows)
        self.assertEqual(len(loaded['input_sha256']), 64)

    def test_corrupt_schema_phase_overlap_duplicate_nan_json(self):
        values = [[], {'schema': 'OTHER'}, {**capture(), 'phases': [None]}]
        duplicate = capture()
        duplicate['phases'].append(duplicate['phases'][0])
        values.append(duplicate)
        overlap = capture()
        overlap['phases'][1]['start_ns'] = 1
        values.append(overlap)
        unordered = capture()
        unordered['phases'].reverse()
        for phase in unordered['phases']:
            phase.pop('clock')
        values.append(unordered)
        for value in values:
            with self.subTest(value=str(value)[:40]), patch.object(p4, 'read_json', return_value=value):
                with self.assertRaises(ValueError):
                    p4.load_capture('capture.json')
        for text in ('{', '{"ber": NaN}', '{"ber": Infinity}'):
            with patch.object(Path, 'read_text', return_value=text), self.assertRaises(ValueError):
                p4.read_json('corrupt.json')

    def test_b5_adapter_reuses_raw_analyzer_not_existing_summary(self):
        from test_analyze_b5_capture import report, inputs, monitor
        source = inputs()
        files = {}
        for name in ('ON1', 'ON2'):
            files['phase31-native-stream-fixture-' + name + '.ts'] = source[name][0]
            files['phase31-native-stream-fixture-' + name + '.jsonl'] = source[name][1]
        for name in p4.PHASES:
            rows = [json.loads(line) for line in monitor().splitlines() if json.loads(line)['phase'] == name]
            files['phase31-signal-monitor-fixture-' + name + '.jsonl'] = ('\n'.join(json.dumps(row) for row in rows)).encode()
        for name in ('OFF1', 'OFF2'):
            files['phase31-signal-rssi-fixture-' + name + '.jsonl'] = b''
        files['session-report.json'] = json.dumps(report()).encode()
        def one(folder, pattern):
            import fnmatch
            return folder / next(name for name in files if fnmatch.fnmatch(name, pattern))
        with patch.object(p4, '_one', one), patch.object(p4, 'read_json', return_value=report()), \
                patch.object(Path, 'read_bytes', lambda path: files[path.name]):
            result = p4.summarize(p4.load_b5(Path('b5'), Path('export')))
        self.assertEqual(result['id'], 'B5')
        self.assertEqual(result['phases']['OFF1']['ber']['zero_count'], 1)
        self.assertAlmostEqual(result['phases']['OFF1']['ber']['nonzero_fraction'], 2 / 3)
        self.assertEqual(result['phases']['ON1']['urb_errors'], 0)
        self.assertIn('ON1', result['hashes']['raw_sha256'])

    def test_markdown_one_table_row_per_run_and_escape(self):
        baseline = p4.summarize(capture())
        baseline['id'] = 'B5'
        candidate = copy.deepcopy(baseline)
        candidate['id'] = 'new|run\nother'
        text = p4.markdown([baseline, candidate])
        rows = [line for line in text.splitlines() if line.startswith('| ')]
        self.assertEqual(len(rows), 4)
        self.assertTrue(rows[2].startswith('| B5 |'))
        self.assertIn('new\\|run other', rows[3])
        self.assertIn('UNKNOWN', text)

    def test_cli_errors_do_not_overwrite_and_emit_markdown_not_json(self):
        output = io.StringIO()
        with patch.object(p4, 'load_b5', return_value=capture()), contextlib.redirect_stdout(output):
            self.assertEqual(p4.main(['--b5', 'b5']), 0)
        self.assertTrue(output.getvalue().startswith('# So sánh'))
        with patch.object(Path, 'exists', return_value=True), patch.object(p4, 'load_b5') as load, \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(p4.main(['--b5', 'b5', '--markdown', 'existing.md']), 2)
            load.assert_not_called()
        with patch.object(p4, 'load_b5', side_effect=ValueError('corrupt')), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(p4.main(['--b5', 'bad']), 2)


if __name__ == '__main__':
    unittest.main()
