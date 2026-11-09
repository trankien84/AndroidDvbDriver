// SPDX-License-Identifier: GPL-2.0-only
const fs = require('fs');
if (process.argv.length < 5) throw Error('Usage: node Check-UbuntuModuleCrc.cjs <kernel.symvers> <local.symvers> <readelf versions files...>');
const [kernel, local, ...inputs] = process.argv.slice(2), exportsMap = new Map();
for (const file of [kernel, local]) {
    for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
        const fields = line.trim().split(/\s+/);
        if (/^0x[0-9a-f]{8}$/i.test(fields[0]) && fields[1]) exportsMap.set(fields[1], fields[0].toLowerCase());
    }
}
let total = 0;
for (const file of inputs) {
    let hex = '';
    for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
        if (!/^\s+0x[0-9a-f]+\s+/i.test(line)) continue;
        for (const token of line.trim().split(/\s+/).slice(1, 5)) {
            if (!/^(?:[0-9a-f]{2}){1,4}$/i.test(token)) break;
            hex += token;
        }
    }
    const bytes = Buffer.from(hex, 'hex');
    if (!bytes.length) throw Error('Empty version section: ' + file);
    let count = 0, offset = 0, sentinel = false;
    while (offset < bytes.length) {
        if (bytes.length - offset >= 8 && bytes.length - offset <= 9 && bytes.subarray(offset).every(value => value === 0)) { offset = bytes.length; sentinel = true; break; }
        if (bytes.length - offset < 9) throw Error('Truncated record: ' + file);
        const size = bytes.readUInt32LE(offset), crc = '0x' + bytes.readUInt32LE(offset + 4).toString(16).padStart(8, '0');
        if (size < 12 || size % 4 || offset + size > bytes.length) throw Error('Invalid Ubuntu variable-length modversion record: ' + file);
        const end = bytes.indexOf(0, offset + 8);
        if (end < 0 || end >= offset + size) throw Error('Unterminated symbol: ' + file);
        const name = bytes.subarray(offset + 8, end).toString('ascii');
        if (!/^[A-Za-z0-9_]+$/.test(name) || exportsMap.get(name) !== crc) throw Error('CRC mismatch: ' + name + ' ' + crc + ' expected=' + exportsMap.get(name));
        count++; offset += size;
    }
    if (!sentinel || !count) throw Error('Missing Ubuntu NULL sentinel or symbol records: ' + file);
    total += count; console.log('PASS ' + file + ': ' + count + ' symbol CRCs');
}
console.log('PASS total=' + total + '; Ubuntu header struct modversion_info { u32 next; u32 crc; char name[]; }; not the obsolete fixed 64-byte layout.');
