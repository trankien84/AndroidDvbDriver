// SPDX-License-Identifier: GPL-2.0-only
const fs = require('fs'), path = require('path'), cp = require('child_process');
if (process.argv.length !== 3) throw Error('Usage: node Test-UbuntuModuleCrc.cjs <new output directory>');
const root = process.argv[2]; if (fs.existsSync(root)) throw Error('Output already exists'); fs.mkdirSync(root);
const tool = path.join(__dirname, '..', 'Check-UbuntuModuleCrc.cjs'), symbols = path.join(root, 'kernel.symvers'), local = path.join(root, 'local.symvers');
fs.writeFileSync(symbols, '0x12345678\tsymbol\tkernel\tEXPORT_SYMBOL\n'); fs.writeFileSync(local, '');
const record = Buffer.alloc(16); record.writeUInt32LE(16); record.writeUInt32LE(0x12345678, 4); record.write('symbol', 8);
const valid = Buffer.concat([record, Buffer.alloc(9)]); let count = 0;
function check(name, bytes, succeeds, symbolText) {
    fs.writeFileSync(symbols, symbolText === undefined ? '0x12345678\tsymbol\tkernel\tEXPORT_SYMBOL\n' : symbolText);
    const file = path.join(root, name + '.txt'); let text = "Hex dump of section '__versions':\n";
    for (let offset = 0; offset < bytes.length; offset += 16) {
        let groups = []; for (let i = offset; i < Math.min(offset + 16, bytes.length); i += 4) groups.push(bytes.subarray(i, Math.min(i + 4, bytes.length)).toString('hex'));
        text += '  0x' + offset.toString(16).padStart(8, '0') + ' ' + groups.join(' ') + '  ...\n';
    }
    fs.writeFileSync(file, text);
    const result = cp.spawnSync(process.execPath, [tool, symbols, local, file], { encoding: 'utf8' });
    if ((result.status === 0) !== succeeds) throw Error(name + ': unexpected status ' + result.status + '\n' + result.stderr);
    count++;
}
check('valid-null8', valid.subarray(0, valid.length - 1), true);
check('valid-null9', valid, true);
check('missing-sentinel', record, false);
check('empty-symbol-list', Buffer.alloc(9), false);
const badCrc = Buffer.from(valid); badCrc.writeUInt32LE(0, 4); check('bad-crc', badCrc, false);
check('missing-export', valid, false, '');
const badSize = Buffer.from(valid); badSize.writeUInt32LE(15); check('unaligned-size', badSize, false);
const unterminated = Buffer.from(valid); unterminated.fill(65, 8, 16); check('unterminated-symbol', unterminated, false);
check('truncated-record', record.subarray(0, 12), false);
check('trailing-junk', Buffer.concat([valid, Buffer.from([1])]), false);
console.log('PASS ' + count + ' Ubuntu CRC parser fixtures; no kernel/module operations.');
