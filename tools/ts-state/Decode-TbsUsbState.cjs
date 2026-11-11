// SPDX-License-Identifier: GPL-2.0-or-later
'use strict';
const fs = require('node:fs');
const crypto = require('node:crypto');

const fields = [
  'frame.number', 'frame.time_epoch', 'usb.bus_id', 'usb.device_address',
  'usb.irp_id', 'usb.irp_info', 'usb.urb_id', 'usb.urb_type', 'usb.transfer_type',
  'usb.endpoint_address', 'usb.control_stage', 'usb.bmRequestType',
  'usb.setup.bRequest', 'usb.setup.wValue', 'usb.setup.wIndex', 'usb.setup.wLength',
  'usb.usbd_status', 'usb.urb_status', 'usb.urb_len', 'usb.data_len',
  'usb.capdata', 'usb.control.Response', 'usb.data_fragment'
];
const hex = value => value.toString(16).padStart(2, '0');
const digest = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
function number(value) {
  if (value === '' || value === undefined) return null;
  if (!/^(?:-?\d+|0x[\da-f]+)$/i.test(value)) throw new Error(`Invalid number: ${value}`);
  const result = Number(value);
  if (!Number.isSafeInteger(result)) throw new Error('Unsafe integer');
  return result;
}
function parseTsv(text) {
  const lines = text.replace(/^\uFEFF/, '').replace(/[\r\n]+$/, '').split(/\r?\n/);
  const header = lines.shift().split('\t');
  for (const key of fields) if (!header.includes(key)) throw new Error(`Missing field: ${key}`);
  return lines.filter(Boolean).map((line, i) => {
    const cells = line.split('\t');
    if (cells.length !== header.length) throw new Error(`Wrong column count at row ${i + 2}`);
    return Object.fromEntries(header.map((key, j) => [key, cells[j]]));
  });
}
function payload(row) {
  const values = ['usb.capdata', 'usb.control.Response', 'usb.data_fragment']
    .map(key => row[key] || '').filter(Boolean).map(value => value.replace(/:/g, '').toLowerCase());
  if (values.some(value => !/^(?:[\da-f]{2})*$/.test(value))) throw new Error('Invalid payload hex');
  if (new Set(values).size > 1) throw new Error('Ambiguous payload fields');
  return Buffer.from(values[0] || '', 'hex');
}
function decode(rows, bus, devices) {
  const pending = new Map(), banks = new Map(), pointers = new Map();
  const controls = [], bulk = [], registers = [], warnings = [];
  let previousFrame = 0;
  for (const row of rows) {
    const frame = number(row['frame.number']);
    if (frame === null || frame <= previousFrame) throw new Error('Frames must increase');
    previousFrame = frame;
    const rowBus = number(row['usb.bus_id']), device = number(row['usb.device_address']);
    if (rowBus !== bus || !devices.includes(device)) continue;
    const transfer = number(row['usb.transfer_type']), endpoint = number(row['usb.endpoint_address']);
    if (transfer !== 2 && !(transfer === 3 && endpoint === 0x82)) continue;
    const linux = !!row['usb.urb_id'];
    const id = linux ? row['usb.urb_id'] : row['usb.irp_id'];
    const event = linux ? row['usb.urb_type'].replace(/'/g, '')
      : (number(row['usb.irp_info']) === 0 ? 'S' : number(row['usb.irp_info']) === 1 ? 'C' : '?');
    if (!id || !['S', 'C', 'E'].includes(event)) throw new Error(`Unknown event at frame ${frame}`);
    const owner = `${rowBus}:${device}`;
    const key = `${owner}:${transfer}:${endpoint}:${id}`;
    if (event === 'S') {
      if (pending.has(key)) throw new Error(`Overlapping URB identity at frame ${frame}`);
      pending.set(key, { row, frame, owner, device, linux });
      continue;
    }
    const start = pending.get(key);
    if (!start) { warnings.push({ frame, reason: 'UNPAIRED_COMPLETION' }); continue; }
    pending.delete(key);
    const s = start.row;
    const status = number(row[linux ? 'usb.urb_status' : 'usb.usbd_status']);
    const actual = number(row[linux ? 'usb.urb_len' : 'usb.data_len']);
    const transaction = { frame: start.frame, completion: frame, bus, device,
      start: s['frame.time_epoch'], end: row['frame.time_epoch'], status, actual };
    if (transfer === 3) {
      bulk.push({ ...transaction, requested: linux ? number(s['usb.urb_len']) : null,
        requestedEvidence: linux ? 'USBMON_SUBMIT' : 'UNKNOWN_USBPCAP' });
      continue;
    }
    const requestType = number(s['usb.bmRequestType']), request = number(s['usb.setup.bRequest']);
    const requested = number(s['usb.setup.wLength']);
    const incoming = requestType === null ? null : !!(requestType & 0x80);
    const bytes = payload(incoming ? row : s);
    const vendor = requestType !== null && (requestType & 0x60) === 0x40;
    const control = { ...transaction, requestType, request, value: number(s['usb.setup.wValue']),
      index: number(s['usb.setup.wIndex']), requested,
      payload: request === 0xa0 && vendor ? null : bytes.toString('hex'),
      payloadBytes: bytes.length, incoming,
      resetValue: vendor && request === 0xa0 && [0x7f92, 0xe600].includes(number(s['usb.setup.wValue'])) && bytes.length === 1 ? bytes[0] : null,
      fullLength: status === 0 && requested !== null && bytes.length === requested &&
        (linux ? actual === requested : incoming ? actual === requested : true),
      lengthEvidence: !linux && !incoming ? 'SENT_PAYLOAD_AND_COMPLETION_STATUS_ONLY' : 'REPORTED_ACTUAL' };
    controls.push(control);
    if (!vendor) continue;
    const bankKey = address => `${start.owner}:${address}`;
    if (request === 0xa0) {
      for (const key of banks.keys()) if (key.startsWith(`${start.owner}:`)) banks.delete(key);
      pointers.delete(start.owner);
      continue;
    }
    if ((request === 0x80 || request === 0x90) && !incoming) {
      if (bytes.length < 3 || (request === 0x80 && bytes.length < 4)) {
        warnings.push({ frame: start.frame, reason: 'SHORT_I2C_ENCODING' }); pointers.delete(start.owner); continue;
      }
      const address = bytes[1] >> 1, register = bytes[2];
      const bank = banks.get(bankKey(address)) ?? null;
      if (request === 0x80) {
        pointers.delete(start.owner);
        const valid = control.fullLength && bytes[0] === bytes.length - 1 && !(bytes[1] & 1);
        if (!valid) { banks.delete(bankKey(address)); pointers.delete(start.owner); continue; }
        const data = bytes.subarray(3);
        registers.push({ frame: start.frame, completion: frame, device, operation: 'WRITE', address, bank, register, data: data.toString('hex') });
        if (register === 0 && data.length === 1 && [0x64, 0x66].includes(address)) banks.set(bankKey(address), data[0]);
      } else {
        pointers.delete(start.owner);
        if (control.fullLength && bytes.length === 3 && !(bytes[1] & 1))
          pointers.set(start.owner, { address, bank, register, length: bytes[0], frame: start.frame });
      }
    } else if (request === 0x91 && incoming) {
      const pointer = pointers.get(start.owner);
      pointers.delete(start.owner);
      if (!pointer || !control.fullLength || pointer.length !== bytes.length) {
        warnings.push({ frame: start.frame, reason: 'UNPROVEN_I2C_RESPONSE' }); continue;
      }
      registers.push({ frame: start.frame, completion: frame, device, operation: 'READ',
        address: pointer.address, bank: pointer.bank, register: pointer.register, data: bytes.toString('hex'), pointerFrame: pointer.frame });
    } else if (!(request === 0x81 && incoming && control.fullLength && bytes.length === 1 && bytes[0] <= 1)) {
      // Pinned FX2 handler 0x0223 reads software bit 20.7, not the I2C pointer.
      pointers.delete(start.owner);
    }
  }
  for (const start of pending.values()) warnings.push({ frame: start.frame, reason: 'UNPAIRED_SUBMISSION' });
  controls.sort((a, b) => a.frame - b.frame);
  bulk.sort((a, b) => a.frame - b.frame);
  const expanded = registers.flatMap(r => Array.from(Buffer.from(r.data, 'hex'), (value, i) =>
    ({ ...r, register: (r.register + i) & 255, value, data: undefined })));
  const firstBulk = bulk[0]?.frame ?? null;
  const sync = expanded.filter(r => r.operation === 'READ' && r.address === 0x64 && r.bank === 0x20 && r.register === 0x10)
    .map(r => ({ frame: r.frame, value: r.value, sync: r.value & 7, tsLock: !!(r.value & 0x20), unlock: !!(r.value & 0x10), beforeFirstBulk: firstBulk !== null && r.frame < firstBulk }));
  const firmware = [];
  for (const c of controls.filter(c => c.request === 0xa0 && c.requestType === 0x40)) {
    // Addresses identify the reviewed reset boundaries; firmware bytes never enter the report.
    if (c.value === 0x7f92 && c.resetValue === 1) {
      const last = firmware.at(-1);
      if (last && last.releases.length !== 2) warnings.push({ frame: c.frame, reason: 'INCOMPLETE_PREVIOUS_A0_EPOCH' });
      firmware.push({ firstFrame: c.frame, holds: [], releases: [], transfers: 0, imageBytes: 0, allFullLength: true });
    }
    const epoch = firmware.at(-1);
    if (!epoch) { warnings.push({ frame: c.frame, reason: 'A0_WITHOUT_EPOCH_BOUNDARY' }); continue; }
    epoch.transfers++; epoch.lastFrame = c.frame; epoch.allFullLength &&= c.fullLength;
    if ([0x7f92, 0xe600].includes(c.value) && c.requested === 1) {
      if (![0, 1].includes(c.resetValue)) warnings.push({ frame: c.frame, reason: 'UNKNOWN_CPU_RESET_VALUE' });
      else (c.resetValue === 1 ? epoch.holds : epoch.releases).push({ frame: c.frame, address: c.value, value: c.resetValue, status: c.status });
    } else epoch.imageBytes += c.requested ?? 0;
  }
  const histogram = list => Object.fromEntries([...new Set(list.map(r => String(r.status)))].map(key => [key, list.filter(r => String(r.status) === key).length]));
  return { schema: 'tbs5530.ts-state/1', scope: { bus, devices },
    evidence: 'HOST_CAPTURE_NOT_WIRE_OR_PHYSICAL_PIN_MEASUREMENT',
    summary: { controls: controls.length, registers: registers.length, bulkTransfers: bulk.length,
      bulkStatus: histogram(bulk), bulkBytes: bulk.reduce((n, r) => n + (r.actual ?? 0), 0),
      firstBulkSubmit: firstBulk, firstBulkPayload: bulk.find(r => r.status === 0 && r.actual > 0) ?? null,
      firmware, sync, warnings }, controls, registers, bulk };
}
function main(argv) {
  if (argv.length !== 4) throw new Error('Usage: node Decode-TbsUsbState.cjs input.tsv new-output.json bus device[,device]');
  const [input, output, busText, deviceText] = argv;
  const bus = number(busText), devices = deviceText.split(',').map(number);
  if (bus === null || devices.some(d => d === null || d < 1 || d > 127)) throw new Error('Invalid explicit scope');
  if (fs.statSync(input).size > 256 * 1024 * 1024) throw new Error('Input exceeds 256 MiB');
  const raw = fs.readFileSync(input);
  const result = decode(parseTsv(raw.toString('utf8')), bus, devices);
  result.inputSha256 = digest(raw);
  fs.writeFileSync(output, JSON.stringify(result, null, 2) + '\n', { flag: 'wx' });
  console.log(JSON.stringify(result.summary));
}
if (require.main === module) { try { main(process.argv.slice(2)); } catch (error) { console.error(error.message); process.exitCode = 1; } }
module.exports = { fields, parseTsv, decode, main };
