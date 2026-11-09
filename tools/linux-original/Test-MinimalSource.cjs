// SPDX-License-Identifier: GPL-2.0-only
const fs = require('fs'), path = require('path'), cp = require('child_process');
if (process.argv.length !== 6) throw Error('Usage: node Test-MinimalSource.cjs <original> <minimal> <new output dir> <gcc>');
const [original, minimal, output, gcc] = process.argv.slice(2);
if (fs.existsSync(output)) throw Error('Refusing existing output directory');
fs.mkdirSync(output);
const upstream = fs.readFileSync(path.join(original, 'tbs5530.c'), 'utf8');
const source = fs.readFileSync(path.join(minimal, 'tbs5530.c'), 'utf8');
const guard = fs.readFileSync(path.join(minimal, 'tbs5530_min_guard.h'), 'utf8');
function extract(text, name) {
    const re = new RegExp('static (?:int|void) ' + name + '\\(');
    const match = re.exec(text);
    if (!match) throw Error('Missing function ' + name);
    const brace = text.indexOf('{', match.index); let depth = 1, end = brace + 1;
    while (depth && end < text.length) { if (text[end] === '{') depth++; if (text[end] === '}') depth--; end++; }
    if (depth) throw Error('Unbalanced source ' + name);
    return text.slice(match.index, end);
}
let sonyExpected = fs.readFileSync(path.join(original, 'cxd2878.c'), 'utf8');
const initStart = sonyExpected.indexOf('static int cxd2878_init(struct dvb_frontend *fe)');
const initEnd = sonyExpected.indexOf('static int cxd2878_read_status', initStart);
let init = sonyExpected.slice(initStart, initEnd).replaceAll('if(ret) return ret;', 'if(ret) goto err;');
init = init.replace('return -ENOTSUPP;', 'ret = -ENOTSUPP;\n\t\tgoto err;');
sonyExpected = sonyExpected.slice(0, initStart) + init + sonyExpected.slice(initEnd);
sonyExpected = sonyExpected.replace('\t\tmutex_lock(&dev->base->i2c_lock);\n\n\tif(!dev->warm)\n\t    cxd2878_init(fe);', '\tif (!dev->warm) {\n\t\tret = cxd2878_init(fe);\n\t\tif (ret) return ret;\n\t}\n\tmutex_lock(&dev->base->i2c_lock);');
if (sonyExpected !== fs.readFileSync(path.join(minimal, 'cxd2878.c'), 'utf8')) throw Error('Sony differs beyond reviewed error/recursive-lock fixes');
for (const file of ['cxd2878.h', 'cxd2878_priv.h', 'alp.c', 'alp.h', 'compat.h']) {
    if (!fs.readFileSync(path.join(original, file)).equals(fs.readFileSync(path.join(minimal, file)))) throw Error('Source changed: ' + file);
}
for (const name of ['tbs5530_cfg', 'tbs5530_fe_ter_init', 'tbs5530_fe_ter_sleep', 'tbs5530_start_feed', 'tbs5530_stop_feed']) {
    if (name === 'tbs5530_cfg') {
        const block = t => t.slice(t.indexOf('static struct cxd2878_config'), t.indexOf('/* -------- ALP'));
        if (block(source) !== block(upstream)) throw Error('Terrestrial config changed');
    } else if (extract(source, name) !== extract(upstream, name)) throw Error('Terrestrial function changed: ' + name);
}
const preamble = `#include <assert.h>
#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
typedef uint8_t u8; typedef uint16_t u16; typedef uint64_t u64;
typedef struct { long long value; } atomic_t;
typedef atomic_t atomic64_t;
#define READ_ONCE(v) (v)
#undef ECONNRESET
#undef ESHUTDOWN
#undef EOVERFLOW
#undef EPROTO
#undef ECANCELED
#undef ETIMEDOUT
#define ECONNRESET 104
#define ESHUTDOWN 108
#define EOVERFLOW 75
#define EPROTO 71
#define ECANCELED 125
#define ETIMEDOUT 110
static long long atomic_cmpxchg(atomic_t *a, long long old, long long next) { long long previous=a->value; if(previous==old) a->value=next; return previous; }
#define GFP_ATOMIC 0
#define URB_BUF_SIZE 65536
#define TBS_OP_HEARTBEAT_MS 2000
#define atomic_read(a) ((a)->value)
#define atomic64_read(a) ((a)->value)
#define atomic_inc(a) ((a)->value++)
#define atomic_dec(a) ((a)->value--)
#define atomic64_add_return(v,a) ((a)->value += (v))
#define spin_lock_irqsave(l,f) ((void)(l), (void)(f))
#define spin_unlock_irqrestore(l,f) ((void)(l), (void)(f))
static unsigned checks;
#define CHECK(x) do { checks++; assert(x); } while (0)
struct tbs5530_dev { bool streaming, min_started, min_loading; u64 min_admitted, min_receive;
    atomic_t min_fault, min_submitted, min_reaped, min_outstanding; atomic64_t min_bytes;
    unsigned min_fw_calls; int min_submit_lock, demux;
    unsigned long urb_complete_ok, urb_complete_empty, urb_complete_err; u64 urb_bytes_total; };
struct urb { struct tbs5530_dev *context; int status, actual_length; void *transfer_buffer; };
static bool stop; static atomic64_t heartbeat_at; static u64 clock_ms;
static unsigned submits, deliveries; static int submit_result;
static u64 tbs_m_now(void) { return clock_ms; }
static int usb_submit_urb(struct urb *u, int flags) { (void)u; (void)flags; submits++; return submit_result; }
static void dvb_dmx_swfilter(void *d, void *b, int n) { (void)d; (void)b; CHECK(n > 0); deliveries++; }
static void reset(struct tbs5530_dev *d) { memset(d,0,sizeof(*d)); d->streaming=true; d->min_admitted=1000; clock_ms=1000; heartbeat_at.value=1000; stop=false; submits=deliveries=0; submit_result=0; }
`;
let code = preamble;
for (const name of ['tbs_m_fail', 'tbs_m_gate', 'tbs_m_disable', 'tbs_m_submit', 'tbs_m_control']) code += '\n' + extract(guard, name);
code += '\n' + extract(upstream, 'tbs5530_urb_complete').replace('tbs5530_urb_complete', 'original_complete');
code += '\n' + extract(source, 'tbs5530_urb_complete');
code += `
int main(void) {
 struct tbs5530_dev d; struct urb u = { .context=&d }; u8 data[64]={0};
 reset(&d); CHECK(!tbs_m_gate(&d)); clock_ms=3000; CHECK(tbs_m_gate(&d)==-ETIMEDOUT);
 clock_ms=1000; CHECK(tbs_m_fail(&d,-EIO)==-ETIMEDOUT);
 reset(&d); stop=true; CHECK(tbs_m_gate(&d)==-ECANCELED);
 reset(&d); d.min_started=true; d.min_receive=1000; clock_ms=11000; heartbeat_at.value=11000; CHECK(tbs_m_gate(&d)==-ETIMEDOUT);
 reset(&d); clock_ms=121000; heartbeat_at.value=121000; CHECK(tbs_m_gate(&d)==-ETIMEDOUT);
 for (unsigned n=0;n<132;n++) {
  reset(&d); d.min_loading=true; d.min_fw_calls=n;
  u16 address=n==0||n==130?0x7f92:n==1||n==131?0xe600:(n-2)*64;
  unsigned len=n<2||n>=130?1:64; data[0]=n<2?1:0;
  CHECK(!tbs_m_control(&d,0xa0,address,0,data,len,1));
  CHECK(tbs_m_control(&d,0xa0,address,1,data,len,1)==-EPERM);
 }
 reset(&d); d.min_loading=true; d.min_fw_calls=132; CHECK(tbs_m_control(&d,0xa0,0,0,data,64,1)==-EPERM);
 reset(&d); CHECK(tbs_m_control(&d,0xa0,0,0,data,64,1)==-EPERM);
 for(unsigned request=0;request<256;request++) {
  if(request==0x80||request==0x90||request==0x91||request==0x8a) continue;
  reset(&d); CHECK(tbs_m_control(&d,request,0,0,data,2,1)==-EPERM);
 }
 reset(&d); data[0]=10; data[1]=0; CHECK(!tbs_m_control(&d,0x8a,0,0,data,2,1));
 reset(&d); data[0]=4; CHECK(tbs_m_control(&d,0x8a,0,0,data,2,1)==-EPERM);
 reset(&d); data[0]=8; data[1]=1; CHECK(tbs_m_control(&d,0x8a,0,0,data,2,1)==-EPERM);
 for(unsigned address=0;address<256;address++) {
  reset(&d); data[0]=2; data[1]=address;
  int ret=tbs_m_control(&d,0x80,0,0,data,3,1);
  CHECK((ret==0)==(address==0xc8||address==0xcc||address==0x88||address==0x20||address==0xc0));
 }
 reset(&d); data[0]=1; data[1]=0xc8; CHECK(!tbs_m_control(&d,0x90,0,0,data,3,1));
 CHECK(!tbs_m_control(&d,0x91,0,0,data,1,0));
 const int statuses[]={0,-75,-71,-EPIPE,-ECONNRESET,-ENOENT,-ESHUTDOWN};
 for(unsigned i=0;i<sizeof(statuses)/sizeof(*statuses);i++) {
  reset(&d); d.min_outstanding.value=1; u.status=statuses[i]; u.actual_length=0;
  tbs5530_urb_complete(&u); CHECK(d.min_outstanding.value==(i==0?1:0)); CHECK(submits==(i==0?1:0)); CHECK(deliveries==0);
  reset(&d); original_complete(&u); CHECK(submits==(i>=4?0:1));
 }
 reset(&d); d.min_outstanding.value=1; u.status=0; u.actual_length=188;
 tbs5530_urb_complete(&u); CHECK(submits==1&&deliveries==1&&d.urb_bytes_total==188);
 reset(&d); d.min_outstanding.value=1; u.actual_length=65537; tbs5530_urb_complete(&u); CHECK(!submits&&!deliveries&&d.min_fault.value==-EOVERFLOW);
 reset(&d); d.min_submitted.value=4096; CHECK(tbs_m_submit(&d,&u)==-E2BIG&&submits==0);
 reset(&d); submit_result=-EIO; CHECK(tbs_m_submit(&d,&u)==-EIO&&d.min_outstanding.value==0&&d.min_fault.value==-EIO);
 reset(&d); tbs_m_disable(&d); CHECK(tbs_m_submit(&d,&u)==-ECANCELED&&submits==0);
 reset(&d); d.min_outstanding.value=1; d.min_bytes.value=16777216; u.actual_length=188; tbs5530_urb_complete(&u); CHECK(!submits&&!deliveries&&d.min_fault.value==-E2BIG);
 printf("PASS %u extracted source checks; original zero/error resubmits reproduced; M gates and callback bounded; no USB\\n",checks);
 return 0;
}
`;
const cfile=path.join(output,'minimal-extracted-test.c'), exe=path.join(output,process.platform==='win32'?'minimal-test.exe':'minimal-test');
fs.writeFileSync(cfile,code);
for (const [command,args] of [[gcc,['-std=c11','-Wall','-Wextra','-Werror','-Wno-unused-label',cfile,'-o',exe]],[exe,[]]]) {
    const r=cp.spawnSync(command,args,{encoding:'utf8'});
    process.stdout.write(r.stdout||''); process.stderr.write(r.stderr||'');
    if(r.status!==0) process.exit(r.status||1);
}
console.log('PASS source preservation: Sony only reviewed error/recursive-lock fixes; ALP/compat, terrestrial config/init/sleep/feed callbacks unchanged.');
