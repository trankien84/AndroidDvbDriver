// SPDX-License-Identifier: GPL-2.0-only
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const {usbmon,classify}=require('..\\Analyze-LayeredCapture.cjs');
function header(){const b=Buffer.alloc(24);b.writeUInt32LE(0xa1b2c3d4);b.writeUInt16LE(2,4);b.writeUInt16LE(4,6);b.writeUInt32LE(131072,16);b.writeUInt32LE(220,20);return b;}
function event(kind,{id=1,type=3,ep=0x82,dev=3,bus=2,status=0,length=4096,data=Buffer.alloc(0),setup=null}={}){const r=Buffer.alloc(64+data.length);r.writeBigUInt64LE(BigInt(id));r[8]=kind.charCodeAt(0);r[9]=type;r[10]=ep;r[11]=dev;r.writeUInt16LE(bus,12);r[14]=setup?0:45;r[15]=data.length?0:45;r.writeInt32LE(status,28);r.writeUInt32LE(length,32);r.writeUInt32LE(data.length,36);if(setup)setup.copy(r,40);data.copy(r,64);const h=Buffer.alloc(16);h.writeUInt32LE(1);h.writeUInt32LE(r.length,8);h.writeUInt32LE(r.length,12);return Buffer.concat([h,r]);}
test('usbmon preserves requested submit length and actual completion',()=>{const b=Buffer.concat([header(),event('S'),event('C',{length:188,data:Buffer.alloc(188)})]);const d=usbmon(b,2,3);assert.equal(d.bulk[0].requested,4096);assert.equal(d.bulk[0].actual,188);assert.equal(d.bulk[0].status,0);});
test('cancellation stays cancellation, not the earlier host fault',()=>{const b=Buffer.concat([header(),event('S'),event('C',{length:0,status:-2})]);const d=usbmon(b,2,3);assert.equal(d.bulk[0].status,-2);assert.equal(d.bulk[0].actual,0);});
test('wrong device cannot be mixed into selected scope',()=>{const b=Buffer.concat([header(),event('S',{dev:4}),event('C',{dev:4,length:10,data:Buffer.alloc(10)})]);assert.equal(usbmon(b,2,3).bulk.length,0);});
test('truncated usbmon event fails rather than zero-filling missing fields',()=>{const e=event('S');e.writeUInt32LE(800,12);assert.throws(()=>usbmon(Buffer.concat([header(),e]),2,3),/Truncated/);});
test('control setup follows Linux little endian and pending pointer',()=>{const s=Buffer.from('4090000000000300','hex');const b=Buffer.concat([header(),event('S',{type:2,ep:0,setup:s,length:3,data:Buffer.from('01c810','hex')}),event('C',{type:2,ep:0,length:3})]);const d=usbmon(b,2,3);assert.equal(d.controls[0].request,0x90);assert.equal(d.controls[0].requested,3);});
test('missing coverage invalidates even positive bytes',()=>{assert.equal(classify(8192,0,false),'INCONCLUSIVE_OBSERVATION_FAILURE');});
test('zero/zero is not FIFO empty proof',()=>{assert.match(classify(0,0,true),/NOT_FIFO_PROOF/);});
test('positive host only narrows virtual path, not Android',()=>{assert.equal(classify(8192,0,true),'HOST_PAYLOAD_GUEST_NONE');assert.match(classify(8192,8192,true),/STREAM_MATCH_REQUIRED/);});
