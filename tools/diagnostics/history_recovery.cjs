// SPDX-License-Identifier: GPL-2.0-or-later
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { TextDecoder } = require('node:util');

const MAX_FILE = 2 * 1024 * 1024;
const MAX_LEDGER = 1024 * 1024;
const NAMESPACE = 'autonomous-v1-samsung-sm-x926b-api36';
const SCHEMA = 'TBS5530-HISTORY-RECOVERY-INPUT/v1';
const sha256 = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
function requireValue(value, message) { if (!value) throw new Error(message); }
function text(bytes) { return new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(bytes); }
function parseLedger(bytes) {
    requireValue(Buffer.isBuffer(bytes) && bytes.length > 0 && bytes.length <= MAX_LEDGER, 'LEDGER_SIZE');
    const value = text(bytes);
    requireValue(/^(FIRMWARE_CONSUMED [a-f0-9]{64}\n)+$/.test(value), 'LEDGER_FORMAT');
    const records = value.slice(0, -1).split('\n').map(line => line.slice(18));
    requireValue(new Set(records).size === records.length, 'LEDGER_DUPLICATE');
    return records;
}
function verifiedInput(pointer, read, seen) {
    requireValue(pointer && typeof pointer.file === 'string' && /^[a-f0-9]{64}$/.test(pointer.sha256), 'INPUT_POINTER');
    const bytes = read(pointer.file);
    requireValue(Buffer.isBuffer(bytes) && bytes.length <= MAX_FILE, 'INPUT_SIZE');
    requireValue(sha256(bytes) === pointer.sha256, 'INPUT_HASH_CHANGED');
    seen.set(pointer.file, { file: pointer.file, sha256: pointer.sha256, bytes: bytes.length });
    return bytes;
}

/** Produces a stricter deny list only. Does not authorize a restore, app write or hardware run. */
function createProposal(manifest, read) {
    requireValue(manifest && manifest.schema === SCHEMA && manifest.namespace === NAMESPACE, 'INPUT_SCOPE');
    requireValue(Array.isArray(manifest.observations) && manifest.observations.length > 0
            && manifest.observations.length <= 64, 'OBSERVATION_COUNT');
    const inputs = new Map();
    const baseline = verifiedInput(manifest.baseline, read, inputs);
    const records = parseLedger(baseline), original = new Set(records), additions = new Set();
    const labels = new Set(), observations = [];
    for (const entry of manifest.observations) {
        requireValue(entry && typeof entry.label === 'string' && /^[A-Z0-9_]{1,64}$/.test(entry.label)
                && !labels.has(entry.label), 'OBSERVATION_LABEL');
        labels.add(entry.label);
        requireValue(['physical_epoch', 'physical_recovery_epoch'].includes(entry.epochField), 'EPOCH_FIELD');
        const receipt = JSON.parse(text(verifiedInput(entry.receipt, read, inputs)));
        const epoch = receipt[entry.epochField];
        requireValue(typeof epoch === 'string' && /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(epoch)
                && epoch !== '00000000-0000-0000-0000-000000000000', 'EPOCH_INVALID');
        const report = JSON.parse(text(verifiedInput(entry.report, read, inputs)));
        const firmware = report.firmware;
        requireValue(report.policy === 'TBS5530-AUTONOMOUS/v1' && report.host_profile === 'samsung-sm-x926b-api36'
                && report.physical_recovery_epoch_bound === true && report.cleanup === 'DRAIN_CONFIRMED', 'REPORT_SCOPE');
        requireValue(firmware && ['attempted', 'completed', 'native_started', 'native_completed',
            'full_length_completed', 'guard_requested', 'guard_accepted'].every(key => firmware[key] === 132)
                && firmware.image_bytes === 8192 && firmware.payload_bytes === 8196
                && firmware.cleanup_failure === 'NONE', 'BOOTSTRAP_UNPROVEN');
        const raw = text(verifiedInput(entry.controls, read, inputs));
        requireValue(raw.endsWith('\n'), 'CONTROLS_TRUNCATED');
        const controls = raw.trimEnd().split(/\r?\n/).map(line => JSON.parse(line));
        requireValue(controls.length === 132 && controls.every((row, index) => row.schema === 'TBS5530-EP0/v1'
                && row.attachment_epoch === epoch && row.vid === 0x734c && row.pid === 0x5530
                && row.endpoint === 0 && row.request_type === 0x40 && row.request === 0xa0
                && row.phase === 'BOOTSTRAP' && row.sequence === index + 1 && row.success === true
                && Number.isInteger(row.requested_length) && row.requested_length > 0
                && row.returned_length === row.requested_length && row.exception === 'NONE')
                && controls.reduce((sum, row) => sum + row.returned_length, 0) === 8196, 'CONTROLS_EPOCH_OR_COMPLETION');
        const key = sha256(Buffer.from('TBS5530-PHYSICAL-RECOVERY-EPOCH/v1|' + epoch, 'utf8'));
        const existing = original.has(key);
        const duplicateEvidence = additions.has(key);
        if (!existing) additions.add(key);
        observations.push({ label: entry.label, alreadyDenied: existing, sharesPriorObservation: duplicateEvidence,
            sourceHashesVerified: true, attribution: entry.attribution === 'MATCH_PRIOR_ANCHOR'
                ? 'MATCH_PRIOR_ANCHOR_AS_DECLARED_NOT_INDEPENDENT_AUTHENTICATION' : 'SELF_HASHED_NOT_INDEPENDENTLY_AUTHENTICATED' });
    }
    const ordered = [...additions].sort();
    const suffix = Buffer.from(ordered.map(key => 'FIRMWARE_CONSUMED ' + key + '\n').join(''), 'utf8');
    const candidate = Buffer.concat([baseline, suffix]);
    const candidateKeys = new Set(parseLedger(candidate));
    requireValue(candidate.subarray(0, baseline.length).equals(baseline)
            && records.every(key => candidateKeys.has(key)) && candidateKeys.size === records.length + ordered.length,
            'DENY_SET_NOT_MONOTONIC');
    // Detect inputs replaced between evidence reads; never repair them or silently pick newer data.
    for (const item of inputs.values()) requireValue(sha256(read(item.file)) === item.sha256, 'INPUT_CHANGED_DURING_PLAN');
    const summary = {
        schema: 'TBS5530-HISTORY-DENY-ONLY-PROPOSAL/v1', namespace: NAMESPACE,
        status: 'OFFLINE_PROPOSAL_NOT_APPLIED', baselineSha256: sha256(baseline), candidateSha256: sha256(candidate),
        baselineBytes: baseline.length, candidateBytes: candidate.length, baselineRecords: records.length,
        candidateRecords: candidateKeys.size, addedEpochDenials: ordered.length, removedRecords: 0,
        originalPrefixByteIdentical: true, observations, inputs: [...inputs.values()],
        fullHistoricalContinuityProven: false, missingAttachmentKeysReconstructed: false,
        appWriteAuthorized: false, hardwareAuthorized: false, liveGateBlocked: true,
        limits: ['Only corroborated epoch denials are added; unknown historical entries cannot be reconstructed.',
            'File hashes bind the supplied evidence; they are not independent authentication of its origin.',
            'This proposal is not a full-ledger restore and does not clear the history-continuity blocker.']
    };
    return { candidate, summary };
}
function readFile(file) {
    requireValue(typeof file === 'string' && path.isAbsolute(file) && !file.startsWith('\\\\'), 'LOCAL_ABSOLUTE_FILE_REQUIRED');
    const info = fs.lstatSync(file);
    requireValue(info.isFile() && !info.isSymbolicLink() && info.size <= MAX_FILE, 'REGULAR_BOUNDED_FILE_REQUIRED');
    return fs.readFileSync(file);
}
function main(argv) {
    requireValue(argv.length === 4 && argv[0] === '--input' && argv[2] === '--output-directory', 'USAGE');
    const input = path.resolve(argv[1]), output = path.resolve(argv[3]);
    const relative = path.relative('D:\\TSDuck', output);
    requireValue(relative && relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative), 'OUTPUT_OUTSIDE_TSDUCK');
    const manifestBytes = readFile(input), manifest = JSON.parse(text(manifestBytes));
    const proposal = createProposal(manifest, readFile);
    requireValue(sha256(readFile(input)) === sha256(manifestBytes), 'MANIFEST_CHANGED_DURING_PLAN');
    fs.mkdirSync(output); // Exclusive directory: no overwrite or partial reuse.
    fs.writeFileSync(path.join(output, 'candidate-not-applied.txt'), proposal.candidate, { flag: 'wx' });
    fs.writeFileSync(path.join(output, 'proposal.json'), JSON.stringify(proposal.summary, null, 2) + '\n', { flag: 'wx' });
    console.log(JSON.stringify({ status: proposal.summary.status, baselineRecords: proposal.summary.baselineRecords,
        candidateRecords: proposal.summary.candidateRecords, addedEpochDenials: proposal.summary.addedEpochDenials,
        candidateSha256: proposal.summary.candidateSha256, liveGateBlocked: true }));
}
if (require.main === module) {
    try { main(process.argv.slice(2)); }
    catch (error) { console.error('REFUSED: ' + (error.code || (error instanceof SyntaxError ? 'INVALID_JSON' : error.message)));
        process.exitCode = 2; }
}
module.exports = { createProposal, parseLedger, sha256 };
