// SPDX-License-Identifier: GPL-2.0-only
'use strict';
const fs=require('node:fs'),crypto=require('node:crypto');
const firmwareSha='b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc';
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
function usbpcap(bytes,allowPartialTail=false){
 if(bytes.length<24||bytes.readUInt32LE(0)!==0xa1b2c3d4||bytes.readUInt32LE(20)!==249)throw Error('Unsupported or incomplete USBPcap header');
 const pending=new Map(),controls=[],bulk=[],devices=new Map();let frame=0;
 for(let off=24;off<bytes.length;){
  if(off+16>bytes.length){if(allowPartialTail)break;throw Error('Truncated record header');}const cap=bytes.readUInt32LE(off+8),wire=bytes.readUInt32LE(off+12);if(cap!==wire)throw Error('Truncated capture');if(off+16+cap>bytes.length){if(allowPartialTail)break;throw Error('Truncated capture');}
  const b=bytes.subarray(off+16,off+16+cap);frame++;off+=16+cap;if(b.length<27)throw Error('Short USBPcap header');
  const h=b.readUInt16LE(0),id=b.readBigUInt64LE(2).toString(),status=b.readUInt32LE(10),event=b[16]&1,bus=b.readUInt16LE(17),device=b.readUInt16LE(19),ep=b[21],type=b[22],length=b.readUInt32LE(23);
  if(h+length!==b.length)throw Error('Record length mismatch');const key=[bus,device,ep,type,id].join(':');
  if(type===2){
   if(h!==28)throw Error('Unexpected control header');
   if(!event){if(length<8||pending.has(key))throw Error('Invalid setup or duplicate IRP');const s=b.subarray(h,h+8);pending.set(key,{frame,bus,device,ep,type,requestType:s[0],request:s[1],value:s.readUInt16LE(2),index:s.readUInt16LE(4),requested:s.readUInt16LE(6),payload:b.subarray(h+8)});}
   else {const s=pending.get(key);if(!s)throw Error('Unpaired control completion');pending.delete(key);const data=s.requestType&128?b.subarray(h):s.payload;const c={...s,completion:frame,status,payload:data,fullLength:status===0&&data.length===s.requested};controls.push(c);if(s.requestType===0x80&&s.request===6&&s.value===0x100&&status===0&&data.length===18){devices.set(bus+':'+device,{bus,device,vid:data.readUInt16LE(8),pid:data.readUInt16LE(10),frame:s.frame});}}
  } else if(type===3){
   if(!event){if(pending.has(key))throw Error('Duplicate Bulk IRP');pending.set(key,{frame,bus,device,ep,type});}
   else {const s=pending.get(key);if(!s)throw Error('Unpaired Bulk completion');pending.delete(key);bulk.push({...s,completion:frame,status,actual:length,payload:b.subarray(h)});}
  }
 }
 return {frames:frame,devices:[...devices.values()],controls,bulk,pending:[...pending.values()]};
}
function verifyFirmware(trace){
 const ops=trace.controls.filter(c=>c.requestType===0x40&&c.request===0xa0);if(ops.length!==132)throw Error('Expected exactly 132 firmware transfers');
 const owner=ops[0].bus+':'+ops[0].device;if(!ops.every(c=>c.bus+':'+c.device===owner&&c.fullLength&&c.index===0))throw Error('Mixed owner or failed firmware transfer');
 const expected=[[0x7f92,1],[0xe600,1]];for(let i=0;i<2;i++)if(ops[i].value!==expected[i][0]||ops[i].payload.length!==1||ops[i].payload[0]!==1)throw Error('Firmware hold mismatch');
 const image=Buffer.alloc(8192);for(let i=0;i<128;i++){const c=ops[i+2];if(c.value!==i*64||c.payload.length!==64)throw Error('Firmware coverage mismatch');c.payload.copy(image,i*64);}
 for(let i=0;i<2;i++){const c=ops[130+i];if(c.value!==expected[i][0]||c.payload.length!==1||c.payload[0]!==0)throw Error('Firmware release mismatch');}
 if(sha(image)!==firmwareSha)throw Error('Firmware image mismatch');
 const forbidden=trace.controls.filter(c=>(c.requestType&0x60)===0x40&&![0xa0].includes(c.request));if(forbidden.length)throw Error('Unexpected vendor I/O during cold bootstrap');
 return {verified:true,firmware_transfers:132,firmware_sha256:firmwareSha,all_full_length:true,owner,first_frame:ops[0].frame,last_frame:ops.at(-1).completion};
}
function summarize(trace){
 const target=trace.devices.filter(d=>d.vid===0x734c&&d.pid===0x5530);const nonTarget=trace.devices.filter(d=>d.vid!==0x734c||d.pid!==0x5530);
 const complete=trace.bulk.filter(r=>r.ep===0x82);return {frames:trace.frames,target_descriptors:target,non_target_descriptors:nonTarget,controls:trace.controls.length,bulk_completed:complete.length,successful_bulk_bytes:complete.filter(r=>r.status===0).reduce((n,r)=>n+r.actual,0),failed_payload_bytes:complete.filter(r=>r.status!==0).reduce((n,r)=>n+r.actual,0),bulk_status:Object.fromEntries([...new Set(complete.map(r=>r.status))].map(k=>[String(k),complete.filter(r=>r.status===k).length])),pending_at_capture_end:trace.pending.map(({frame,bus,device,ep,type})=>({frame,bus,device,ep,type})),wire_signaling_proven:false};
}
if(require.main===module){try{const [mode,input,output]=process.argv.slice(2);if(!['firmware','summary','admission'].includes(mode)||!input||!output)throw Error('Usage: node Verify-Capture.cjs firmware|summary|admission input.pcap new-output.json');const b=fs.readFileSync(input),trace=usbpcap(b,mode==='admission'),result={schema:'TBS5530-USBPCAP-VERIFICATION/v1',input_sha256:sha(b),...summarize(trace),...(mode==='firmware'?verifyFirmware(trace):{})};fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(result));}catch(e){console.error(e.message);process.exitCode=1;}}
module.exports={usbpcap,verifyFirmware,summarize};
