// SPDX-License-Identifier: GPL-2.0-or-later
'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { fields, parseTsv, decode } = require('./Decode-TbsUsbState.cjs');

function capture(linux = true) {
  const rows = [];
  let frame = 0;
  function row(values) { return Object.assign(Object.fromEntries(fields.map(f => [f, ''])), values); }
  function transfer(request, bytes, options = {}) {
    const incoming = options.incoming ?? request === 0x91;
    const device = options.device ?? 3, type = options.bulk ? 3 : 2;
    const endpoint = options.bulk ? 0x82 : incoming ? 0x80 : 0;
    const data = Buffer.from(bytes, 'hex');
    const requested = options.requested ?? data.length;
    const common = { 'usb.bus_id': '2', 'usb.device_address': String(device),
      'usb.transfer_type': `0x0${type}`, 'usb.endpoint_address': `0x${endpoint.toString(16)}` };
    const start = row({ ...common, 'frame.number': String(++frame), 'frame.time_epoch': String(frame),
      'usb.bmRequestType': incoming ? '0xc0' : '0x40', 'usb.setup.bRequest': String(request),
      'usb.setup.wLength': String(requested), 'usb.setup.wValue': String(options.value ?? 0),
      'usb.setup.wIndex': '0', 'usb.data_fragment': incoming ? '' : bytes,
      'usb.urb_len': String(requested) });
    const end = row({ ...common, 'frame.number': String(++frame), 'frame.time_epoch': String(frame),
      'usb.urb_len': String(options.actual ?? requested), 'usb.data_len': String(options.actual ?? requested),
      'usb.data_fragment': incoming ? bytes : '' });
    if (linux) {
      Object.assign(start, { 'usb.urb_id': '0x1', 'usb.urb_type': "'S'", 'usb.urb_status': '-115' });
      Object.assign(end, { 'usb.urb_id': '0x1', 'usb.urb_type': "'C'", 'usb.urb_status': String(options.status ?? 0) });
    } else {
      Object.assign(start, { 'usb.irp_id': '0x1', 'usb.irp_info': '0', 'usb.usbd_status': '3221225489' });
      Object.assign(end, { 'usb.irp_id': '0x1', 'usb.irp_info': '1', 'usb.usbd_status': String(options.status ?? 0) });
    }
    rows.push(start, end);
    return { start, end };
  }
  const result = () => decode(rows, 2, [3]);
  return { rows, transfer, result };
}

test('TSV requires every verified field and exact columns', () => {
  assert.throws(() => parseTsv('frame.number\n1'), /Missing field/);
  assert.throws(() => parseTsv(fields.join('\t') + '\n1'), /column count/);
  assert.equal(parseTsv(fields.join('\t') + '\n' + fields.map(() => '').join('\t') + '\n').length, 1);
});
test('unknown bank is null, never inferred zero', () => {
  const c = capture(); c.transfer(0x80, '03c89654');
  assert.equal(c.result().registers[0].bank, null);
});
test('bank and read pointer produce actual TS-lock readback', () => {
  const c = capture(); c.transfer(0x80, '03c80020'); c.transfer(0x90, '01c810'); c.transfer(0x91, '26');
  const r = c.result();
  assert.equal(r.registers[1].operation, 'READ'); assert.equal(r.registers[1].bank, 0x20);
  assert.deepEqual(r.summary.sync[0], { frame: 5, value: 0x26, sync: 6, tsLock: true, unlock: false, beforeFirstBulk: false });
});
test('short bank write invalidates old bank', () => {
  const c = capture(); c.transfer(0x80, '03c80020'); c.transfer(0x80, '03c80000', { actual: 3 }); c.transfer(0x80, '03c89654');
  assert.equal(c.result().registers.at(-1).bank, null);
});
test('negative completion cannot establish register state', () => {
  const c = capture(); c.transfer(0x80, '03c80020', { status: -71 });
  assert.equal(c.result().registers.length, 0);
});
test('A0 invalidates bank and pointer, firmware payload is redacted', () => {
  const c = capture(); c.transfer(0x80, '03c80020'); c.transfer(0x90, '01c810');
  c.transfer(0xa0, '01', { value: 0x7f92 }); c.transfer(0x91, '26'); c.transfer(0x80, '03c89654');
  const r = c.result(); assert.equal(r.registers.at(-1).bank, null); assert.equal(r.controls[2].payload, null);
  assert.ok(r.summary.warnings.some(w => w.reason === 'UNPROVEN_I2C_RESPONSE'));
});
test('one pending pointer cannot be consumed twice', () => {
  const c = capture(); c.transfer(0x90, '01c810'); c.transfer(0x91, '26'); c.transfer(0x91, '26');
  assert.equal(c.result().registers.length, 1);
});
test('intervening write invalidates a read pointer', () => {
  const c = capture(); c.transfer(0x90, '01c810'); c.transfer(0x80, '03c80020'); c.transfer(0x91, '26');
  assert.equal(c.result().registers.filter(r => r.operation === 'READ').length, 0);
});
test('reviewed IN81 software-status poll preserves the Windows I2C pointer', () => {
  const c = capture(false); c.transfer(0x80, '03c80020'); c.transfer(0x90, '01c810');
  c.transfer(0x81, '00', { incoming: true }); c.transfer(0x91, '26');
  assert.equal(c.result().summary.sync[0].value, 0x26);
});
test('unreviewed B7 side effect invalidates the pointer', () => {
  const c = capture(false); c.transfer(0x90, '01c810');
  c.transfer(0xb7, 'b701', { incoming: true }); c.transfer(0x91, '26');
  assert.equal(c.result().registers.length, 0);
});
test('owners and reused URB ids never cross-pair', () => {
  const c = capture(); c.transfer(0x90, '01c810', { device: 4 }); c.transfer(0x91, '26');
  assert.equal(c.result().registers.length, 0);
});
test('Windows submitted status is not mistaken for completion error', () => {
  const c = capture(false); c.transfer(0x80, '03c80020');
  assert.equal(c.result().registers.length, 1);
  assert.equal(c.result().controls[0].lengthEvidence, 'SENT_PAYLOAD_AND_COMPLETION_STATUS_ONLY');
});
test('Linux cancellation is not overflow and requested bytes are independent', () => {
  const c = capture(); c.transfer(0, '', { bulk: true, incoming: true, requested: 65536, actual: 0, status: -2 });
  assert.deepEqual(c.result().summary.bulkStatus, { '-2': 1 });
  assert.equal(c.result().summary.bulkBytes, 0); assert.equal(c.result().summary.firstBulkPayload, null);
});
test('USBPcap actual does not establish requested buffer size', () => {
  const c = capture(false); c.transfer(0, '47', { bulk: true, incoming: true, actual: 8192 });
  const r = c.result(); assert.equal(r.summary.firstBulkPayload.requested, null); assert.equal(r.summary.bulkBytes, 8192);
});
test('unpaired submission remains an explicit warning', () => {
  const c = capture(); c.transfer(0x80, '03c80020'); c.rows.pop();
  assert.equal(c.result().summary.warnings[0].reason, 'UNPAIRED_SUBMISSION');
});
test('overlap and unordered input fail closed', () => {
  const c = capture(); const { start } = c.transfer(0x80, '03c80020');
  c.rows.splice(1, 0, { ...start, 'frame.number': '2' }); c.rows[2]['frame.number'] = '3';
  assert.throws(c.result, /Overlapping/);
  const d = capture(); d.transfer(0x80, '03c80020'); d.rows.reverse(); assert.throws(d.result, /increase/);
});
test('conflicting payload fields fail instead of silently choosing one', () => {
  const c = capture(); const { start } = c.transfer(0x80, '03c80020'); start['usb.capdata'] = '03c80000';
  assert.throws(c.result, /Ambiguous/);
});
test('multi-byte register reads expand individual sync fields correctly', () => {
  const c = capture(); c.transfer(0x80, '03c80020'); c.transfer(0x90, '02c80f'); c.transfer(0x91, '0026');
  assert.equal(c.result().summary.sync[0].value, 0x26);
});
test('two A0 epochs retain independent holds and releases without firmware bytes', () => {
  const c = capture();
  for (let n = 0; n < 2; n++) {
    c.transfer(0xa0, '01', { value: 0x7f92 }); c.transfer(0xa0, '01', { value: 0xe600 });
    c.transfer(0xa0, 'aabb', { value: 0 });
    c.transfer(0xa0, '00', { value: 0x7f92 }); c.transfer(0xa0, '00', { value: 0xe600 });
  }
  const r = c.result(); assert.equal(r.summary.firmware.length, 2);
  for (const e of r.summary.firmware) {
    assert.equal(e.holds.length, 2); assert.equal(e.releases.length, 2); assert.equal(e.transfers, 5); assert.equal(e.imageBytes, 2);
  }
  assert.ok(r.controls.every(r => r.payload === null));
  assert.ok(!JSON.stringify(r).includes('aabb'));
});
test('incomplete A0 epoch is flagged, not silently merged', () => {
  const c = capture(); c.transfer(0xa0, '01', { value: 0x7f92 }); c.transfer(0xa0, '01', { value: 0x7f92 });
  assert.ok(c.result().summary.warnings.some(r => r.reason === 'INCOMPLETE_PREVIOUS_A0_EPOCH'));
});
test('bulk bytes do not create a TS-structure certification', () => {
  const c = capture(); c.transfer(0, '', { bulk: true, incoming: true, requested: 4096, actual: 82 });
  const r = c.result(); assert.equal(r.summary.firstBulkPayload.actual, 82); assert.equal(r.tsSmokePass, undefined);
});
