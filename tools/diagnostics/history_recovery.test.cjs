// SPDX-License-Identifier: GPL-2.0-or-later
'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createProposal, parseLedger, sha256 } = require('./history_recovery.cjs');
const EPOCH = '11111111-1111-4111-8111-111111111111';
const SECOND = '22222222-2222-4222-8222-222222222222';
const line = key => Buffer.from('FIRMWARE_CONSUMED ' + key + '\n');
const epochKey = epoch => sha256(Buffer.from('TBS5530-PHYSICAL-RECOVERY-EPOCH/v1|' + epoch));
function fixture() {
    const files = new Map();
    function put(file, value) {
        const bytes = Buffer.isBuffer(value) ? value : Buffer.from(JSON.stringify(value));
        files.set(file, bytes); return { file, sha256: sha256(bytes) };
    }
    const manifest = { schema: 'TBS5530-HISTORY-RECOVERY-INPUT/v1', namespace: 'autonomous-v1-samsung-sm-x926b-api36',
        baseline: put('ledger', line('a'.repeat(64))), observations: [] };
    function observation(label = 'SONY_FIXTURE', epoch = EPOCH) {
        const report = { policy: 'TBS5530-AUTONOMOUS/v1', host_profile: 'samsung-sm-x926b-api36',
            physical_recovery_epoch_bound: true, cleanup: 'DRAIN_CONFIRMED',
            firmware: { attempted: 132, completed: 132, native_started: 132, native_completed: 132,
                full_length_completed: 132, guard_requested: 132, guard_accepted: 132,
                image_bytes: 8192, payload_bytes: 8196, cleanup_failure: 'NONE' } };
        const controls = Array.from({ length: 132 }, (_, i) => ({ schema: 'TBS5530-EP0/v1', attachment_epoch: epoch,
            vid: 0x734c, pid: 0x5530, endpoint: 0, request_type: 0x40, request: 0xa0, phase: 'BOOTSTRAP',
            sequence: i + 1, success: true, requested_length: i < 4 ? 1 : 64,
            returned_length: i < 4 ? 1 : 64, exception: 'NONE' }));
        const entry = { label, epochField: 'physical_recovery_epoch',
            receipt: put(label + '-receipt', { physical_recovery_epoch: epoch }),
            report: put(label + '-report', report),
            controls: put(label + '-controls', Buffer.from(controls.map(x => JSON.stringify(x)).join('\n') + '\n')) };
        manifest.observations.push(entry); return entry;
    }
    const entry = observation();
    function change(pointer, mutation) {
        let value;
        if (pointer === manifest.baseline) value = files.get(pointer.file);
        else if (pointer === entry.controls) value = files.get(pointer.file).toString().trimEnd().split('\n').map(JSON.parse);
        else value = JSON.parse(files.get(pointer.file));
        const next = mutation(value) ?? value;
        const bytes = Buffer.isBuffer(next) ? next : Array.isArray(next)
            ? Buffer.from(next.map(x => JSON.stringify(x)).join('\n') + '\n') : Buffer.from(JSON.stringify(next));
        files.set(pointer.file, bytes); pointer.sha256 = sha256(bytes);
    }
    return { files, manifest, entry, put, observation, change, run: () => createProposal(manifest, p => Buffer.from(files.get(p))) };
}
test('proposal preserves every baseline byte and adds only missing proven epoch denials', () => {
    const f = fixture(); f.observation('LIFECYCLE_FIXTURE', SECOND);
    const { candidate, summary } = f.run();
    assert(candidate.subarray(0, f.files.get('ledger').length).equals(f.files.get('ledger')));
    const keys = parseLedger(candidate);
    assert.deepEqual(new Set(keys), new Set(['a'.repeat(64), epochKey(EPOCH), epochKey(SECOND)]));
    assert.equal(summary.addedEpochDenials, 2); assert.equal(summary.removedRecords, 0);
    assert.equal(summary.liveGateBlocked, true); assert.equal(summary.appWriteAuthorized, false);
    assert.equal(summary.fullHistoricalContinuityProven, false);
    assert(!JSON.stringify(summary).includes(EPOCH)); assert(!JSON.stringify(summary).includes(epochKey(EPOCH)));
});
test('already denied or repeated evidence never duplicates or reorders existing records', () => {
    const f = fixture(); const bytes = Buffer.concat([line('a'.repeat(64)), line(epochKey(EPOCH))]);
    f.files.set('ledger', bytes); f.manifest.baseline.sha256 = sha256(bytes);
    f.observation('SAME_EPOCH', EPOCH);
    const result = f.run(); assert(result.candidate.equals(bytes)); assert.equal(result.summary.addedEpochDenials, 0);
    assert(result.summary.observations.every(x => x.alreadyDenied));
});
test('duplicate evidence for a missing epoch adds one denial only', () => {
    const f = fixture(); f.observation('SAME_EPOCH', EPOCH);
    const result = f.run(); assert.equal(result.summary.addedEpochDenials, 1);
    assert.equal(result.summary.observations[1].sharesPriorObservation, true);
});
test('malformed empty truncated BOM CRLF duplicate and invalid UTF8 ledgers fail closed', () => {
    for (const bytes of [Buffer.alloc(0), Buffer.from('FIRMWARE_CONSUMED ' + 'a'.repeat(64)),
        Buffer.from('\ufeffFIRMWARE_CONSUMED ' + 'a'.repeat(64) + '\n'),
        Buffer.from('FIRMWARE_CONSUMED ' + 'a'.repeat(64) + '\r\n'),
        Buffer.concat([line('a'.repeat(64)), line('a'.repeat(64))]), Buffer.from([0xff, 0xfe])]) {
        const f = fixture(); f.files.set('ledger', bytes); f.manifest.baseline.sha256 = sha256(bytes);
        assert.throws(f.run);
    }
});
test('receipt hash and changed input between reads cannot be bypassed', () => {
    const f = fixture(); f.files.set(f.entry.receipt.file, Buffer.from('{}'));
    assert.throws(f.run, /INPUT_HASH_CHANGED/);
    const g = fixture(); let reads = 0;
    assert.throws(() => createProposal(g.manifest, p => {
        if (p === 'ledger' && ++reads > 1) return line('b'.repeat(64)); return g.files.get(p);
    }), /INPUT_CHANGED_DURING_PLAN/);
});
test('uncorroborated epoch or incomplete/foreign controls cannot add denial', () => {
    const mutations = [r => { r[0].attachment_epoch = SECOND; }, r => { r.pop(); },
        r => { r[0].success = false; }, r => { r[0].returned_length = 0; },
        r => { r[0].request = 0x80; }, r => { r[0].sequence = 2; },
        r => { r[0].pid = 0x5520; }, r => { r[0].exception = 'Synthetic failure'; }];
    for (const mutate of mutations) { const f = fixture(); f.change(f.entry.controls, mutate); assert.throws(f.run, /CONTROLS_EPOCH_OR_COMPLETION/); }
});
test('unbound host report or incomplete native counters reject even a matching receipt', () => {
    const mutations = [r => { r.physical_recovery_epoch_bound = false; }, r => { r.host_profile = 'oneplus-gm1900-api30'; },
        r => { r.firmware.native_completed = 131; }, r => { r.firmware.cleanup_failure = 'UNCERTAIN'; }];
    for (const mutate of mutations) { const f = fixture(); f.change(f.entry.report, mutate); assert.throws(f.run, /REPORT_SCOPE|BOOTSTRAP_UNPROVEN/); }
});
test('unknown namespace malformed epoch empty observations and duplicate labels are rejected', () => {
    const f = fixture(); f.manifest.namespace = 'autonomous-v2'; assert.throws(f.run, /INPUT_SCOPE/);
    const g = fixture(); g.change(g.entry.receipt, x => { x.physical_recovery_epoch = 'arbitrary'; }); assert.throws(g.run, /EPOCH_INVALID/);
    const h = fixture(); h.manifest.observations = []; assert.throws(h.run, /OBSERVATION_COUNT/);
    const i = fixture(); i.observation(); assert.throws(i.run, /OBSERVATION_LABEL/);
});
