// SPDX-License-Identifier: GPL-2.0-only
'use strict';
const fs=require('node:fs'),crypto=require('node:crypto');
const {usbpcap,summarize}=require('.\\Verify-Capture.cjs');
const {fields,decode}=require('..\\ts-state\\Decode-TbsUsbState.cjs');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
function usbmon(b,bus,device){
 if(b.length<24||b.readUInt32LE(0)!==0xa1b2c3d4||![189,220].includes(b.readUInt32LE(20)))throw Error('Expected classic usbmon pcap');
 const link=b.readUInt32LE(20),rows=[];let frame=0;
 for(let at=24;at<b.length;){
  if(at+16>b.length)throw Error('Truncated usbmon record');const seconds=b.readUInt32LE(at),micro=b.readUInt32LE(at+4),cap=b.readUInt32LE(at+8),wire=b.readUInt32LE(at+12);if(cap!==wire||at+16+cap>b.length)throw Error('Truncated usbmon capture');
  const r=b.subarray(at+16,at+16+cap);at+=16+cap;frame++;if(r.length<48)throw Error('Short usbmon event');
  const row=Object.fromEntries(fields.map(k=>[k,'']));const endpoint=r[10],header=link===220?64:48;if(r.length<header)throw Error('Short usbmon header');
  row['frame.number']=String(frame);row['frame.time_epoch']=seconds+'.'+String(micro).padStart(6,'0');row['usb.bus_id']=String(r.readUInt16LE(12));row['usb.device_address']=String(r[11]);row['usb.urb_id']='0x'+r.readBigUInt64LE(0).toString(16);row['usb.urb_type']=String.fromCharCode(r[8]);row['usb.transfer_type']=String(r[9]);row['usb.endpoint_address']=String(endpoint);row['usb.urb_status']=String(r.readInt32LE(28));row['usb.urb_len']=String(r.readUInt32LE(32));row['usb.data_len']=String(r.readUInt32LE(36));
  const dataLength=r.readUInt32LE(36);if(header+dataLength>r.length)throw Error('usbmon payload beyond record');
  if(r[9]===2&&row['usb.urb_type']==='S'&&r[14]===0){const s=r.subarray(40,48);row['usb.bmRequestType']=String(s[0]);row['usb.setup.bRequest']=String(s[1]);row['usb.setup.wValue']=String(s.readUInt16LE(2));row['usb.setup.wIndex']=String(s.readUInt16LE(4));row['usb.setup.wLength']=String(s.readUInt16LE(6));}
  if(dataLength)row['usb.capdata']=r.subarray(header,header+dataLength).toString('hex');rows.push(row);
 }
 return decode(rows,bus,[device]);
}
function classify(host,guest,coverage){
 if(coverage!==true)return 'INCONCLUSIVE_OBSERVATION_FAILURE';
 if(host>0&&guest===0)return 'HOST_PAYLOAD_GUEST_NONE';if(host===0&&guest===0)return 'NO_PAYLOAD_OBSERVED_AT_EITHER_LAYER_NOT_FIFO_PROOF';if(host>0&&guest>0)return 'BOTH_LAYERS_HAVE_BYTES_STREAM_MATCH_REQUIRED';return 'INCONCLUSIVE_HOST_COVERAGE';
}
if(require.main===module){try{const [hostPath,guestPath,busText,deviceText,output]=process.argv.slice(2);if(!output)throw Error('Usage: node Analyze-LayeredCapture.cjs host.pcap guest.pcap bus device new-output.json');const bus=Number(busText),device=Number(deviceText);if(!Number.isInteger(bus)||!Number.isInteger(device)||device<1||device>127)throw Error('Invalid target scope');const hb=fs.readFileSync(hostPath),gb=fs.readFileSync(guestPath),h=summarize(usbpcap(hb)),g=usbmon(gb,bus,device);const guestBytes=g.bulk.filter(r=>r.status===0).reduce((n,r)=>n+(r.actual??0),0);const result={schema:'TBS5530-LAYERED-ANALYSIS/v1',host_sha256:sha(hb),guest_sha256:sha(gb),host:h,guest:g.summary,guest_successful_bulk_bytes:guestBytes,coverage_status:'REQUIRES_TERMINAL_CAP_LOSS_AND_TARGET_ANCHOR_REVIEW',provisional_result:classify(h.successful_bulk_bytes,guestBytes,false),candidate_with_verified_coverage:classify(h.successful_bulk_bytes,guestBytes,true),root_cause:'UNRESOLVED',ts_smoke_pass:false,clocks_joined:false,urb_one_to_one_assumed:false};fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(result));}catch(e){console.error(e.message);process.exitCode=1;}}
module.exports={usbmon,classify};
