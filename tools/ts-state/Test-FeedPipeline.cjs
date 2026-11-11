// SPDX-License-Identifier: GPL-2.0-only
'use strict';
const fs = require('node:fs'), path = require('node:path'), cp = require('node:child_process');
if (process.argv.length !== 6) throw Error('Usage: node Test-FeedPipeline.cjs minimal-source extracted-test.c new-output-dir gcc');
const [minimal, extracted, output, gcc] = process.argv.slice(2);
if (fs.existsSync(output)) throw Error('Output must be new');
const source = fs.readFileSync(path.join(minimal, 'tbs5530.c'), 'utf8');
function extract(name) {
  const start = source.indexOf('static int ' + name + '(');
  if (start < 0) throw Error('Missing feed callback');
  const brace = source.indexOf('{', start);
  let depth = 1, end = brace + 1;
  while (depth && end < source.length) { if (source[end] === '{') depth++; if (source[end] === '}') depth--; end++; }
  if (depth) throw Error('Unbalanced callback');
  return source.slice(start, end);
}
let preamble = fs.readFileSync(extracted, 'utf8');
const main = preamble.indexOf('int main(void)');
if (main < 0) throw Error('Run Test-MinimalSource.cjs first');
preamble = preamble.slice(0, main);
const dev = 'struct tbs5530_dev { bool streaming, min_started, min_loading;';
if (!preamble.includes(dev)) throw Error('Unexpected extracted test layout');
preamble = preamble.replace(dev, 'struct tbs5530_dev { int feed_count; bool streaming, min_started, min_loading;');
const delivery = 'static void dvb_dmx_swfilter(void *d, void *b, int n) { (void)d; (void)b; CHECK(n > 0); deliveries++; }';
if (!preamble.includes(delivery)) throw Error('Unexpected demux seam');
preamble = preamble.replace(delivery, `static unsigned char fake_dvr[65536];
static size_t fake_length;
static void dvb_dmx_swfilter(void *d, void *b, int n) {
 (void)d; CHECK(n>0); CHECK((size_t)n<=sizeof(fake_dvr)-fake_length);
 memcpy(fake_dvr+fake_length,b,(size_t)n); fake_length+=(size_t)n; deliveries++;
}`);
const harness = `
struct fake_demux { struct tbs5530_dev *priv; };
struct dvb_demux_feed { struct fake_demux *demux; };
static unsigned starts, stops;
static int tbs5530_start_streaming(struct tbs5530_dev *d) { starts++; d->streaming=true; return 0; }
static int tbs5530_stop_streaming(struct tbs5530_dev *d) { stops++; d->streaming=false; return 0; }
${extract('tbs5530_start_feed')}
${extract('tbs5530_stop_feed')}
static size_t receiver_dvr_read(unsigned char *out,size_t max) {
 size_t n=fake_length<max?fake_length:max;
 memcpy(out,fake_dvr,n); memmove(fake_dvr,fake_dvr+n,fake_length-n); fake_length-=n; return n;
}
int main(void) {
 struct tbs5530_dev d; struct urb u={.context=&d}; struct fake_demux dm={.priv=&d}; struct dvb_demux_feed feed={.demux=&dm};
 unsigned char input[188], received[188];
 for(unsigned i=0;i<sizeof(input);i++) input[i]=(unsigned char)(i^0x5a);
 reset(&d); d.streaming=false; CHECK(!tbs5530_start_feed(&feed)); CHECK(starts==1&&d.feed_count==1&&d.streaming);
 CHECK(!tbs5530_start_feed(&feed)); CHECK(starts==1&&d.feed_count==2);
 u.status=0;u.actual_length=188;u.transfer_buffer=input;d.min_outstanding.value=1;
 tbs5530_urb_complete(&u); CHECK(deliveries==1&&fake_length==188&&submits==1);
 CHECK(receiver_dvr_read(received,sizeof(received))==188); CHECK(!memcmp(input,received,sizeof(input))); CHECK(!fake_length);
 CHECK(!tbs5530_stop_feed(&feed)); CHECK(d.streaming&&stops==0);
 CHECK(!tbs5530_stop_feed(&feed)); CHECK(!d.streaming&&stops==1&&d.feed_count==0);
 u.status=-75;u.actual_length=188;d.min_outstanding.value=1;
 tbs5530_urb_complete(&u); CHECK(!fake_length&&deliveries==1);
 reset(&d); d.min_outstanding.value=1;u.status=-75;u.actual_length=0;
 tbs5530_urb_complete(&u); CHECK(!fake_length&&deliveries==0&&submits==0);
 printf("PASS %u pipeline checks: extracted start_feed -> urb_complete -> fake swfilter -> fake DVR read; no kernel or USB\\n",checks);
 return 0;
}
`;
fs.mkdirSync(output);
const file = path.join(output, 'feed-pipeline.c'), exe = path.join(output, 'feed-pipeline.exe');
fs.writeFileSync(file, preamble + harness);
cp.execFileSync(gcc, ['-std=c11','-Wall','-Wextra','-Werror','-Wno-unused-function','-pedantic',file,'-o',exe], { stdio:'inherit' });
cp.execFileSync(exe, [], { stdio:'inherit' });
