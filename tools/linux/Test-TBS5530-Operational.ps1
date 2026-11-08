[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$SourcePath)
$ErrorActionPreference = 'Stop'
$s = [IO.File]::ReadAllText((Join-Path $SourcePath 'tbs5530.c'))
$i = [IO.File]::ReadAllText((Join-Path $SourcePath 'tbs5530_runtime_io.h'))
$r = [IO.File]::ReadAllText((Join-Path $SourcePath 'tbs5530_runtime_impl.h'))
$g = [IO.File]::ReadAllText((Join-Path $SourcePath 'tbs5530_operational_guard.h'))
$count = 0
function Check([bool]$Value,[string]$Name) { if (-not $Value) { throw "Operational proof failed: $Name" }; $script:count++ }
function Body([string]$Text,[string]$Name) {
    $m=[regex]::Match($Text,'(?m)^static\s+(?:int|void|bool)\s+'+[regex]::Escape($Name)+'\s*\([^;]*?\)\s*\{')
    if (-not $m.Success) { throw "Missing function $Name" }
    $a=$m.Index+$m.Length-1;$depth=0
    for($j=$a;$j -lt $Text.Length;$j++) { if($Text[$j] -eq '{'){$depth++};if($Text[$j] -eq '}'){$depth--;if(-not $depth){return $Text.Substring($a,$j-$a+1)}} }
    throw 'Unbalanced function body'
}
$probe=Body $s 'tbs5530_probe'
$admit=Body $r 'tbs5530_operational_admission'
$control=Body $i 'tbs5530_control_locked'
$completion=Body $i 'tbs5530_urb_complete'
$shutdown=Body $i 'tbs5530_runtime_shutdown'
Check (-not ($probe -match 'request_firmware|tbs5530_load_firmware|0xa0')) 'no firmware calls in probe'
Check ((Body $s 'tbs5530_diag_write').Contains('return -EPERM')) 'bootstrap writer unavailable in operational build'
Check ($probe.IndexOf('tbs5530_operational_admission') -lt $probe.IndexOf('i2c_add_adapter')) 'admission before registered I2C'
Check ($admit.Contains('tbs_op_profile_check') -and $admit.Contains('atomic_cmpxchg(&probe_spent, 0, 1)')) 'exact admission and irreversible probe consumption'
Check ($admit.Contains('sha256(raw, sizeof(raw), digest)') -and $admit.Contains('raw[50]')) 'cached descriptor hash exact50'
Check ($r.Contains('expected_busnum') -and $r.Contains('expected_devnum') -and $r.Contains('expected_profile_sha256')) 'no guessed attachment identity'
Check ($g.Contains('p->ep81_interval != 0') -and $g.Contains('p->ep82_interval != 1') -and $g.Contains('p->ep82_size != 512')) 'HS endpoint tuple checked'
Check ($g.Contains('request == 0x91') -and $g.Contains('request == 0x80') -and $g.Contains('request == 0x90') -and $g.Contains('request == 0x8a')) 'known control allowlist only'
Check ($g.Contains('data[0] == 0x0a && data[1] == 0')) 'stream selector exact0A00'
Check (-not ($i -match '0xa0|0xb7|request_firmware')) 'operational I/O has no A0 B7 firmware path'
Check ($control.IndexOf('tbs5530_gate_locked') -lt $control.IndexOf('usb_control_msg')) 'gate before control submit'
Check ($control.Contains('tbs_op_control_result') -and $control.Contains('first_failed_control')) 'first failed control telemetry and latch'
Check ($control.Contains('if (!ret && read) memcpy')) 'read buffers copied only exact success'
Check ($i.Contains('mutex_lock(&dev->runtime.control_lock)') -and $i.Contains('return ret;')) 'serialized read pair and actual I2C status'
Check ($s.Contains('tbs5530_finish(dev,dev->fe_ter_ops_orig.set_frontend') -and $s.Contains('tbs5530_finish(dev,dev->fe_ter_ops_orig.tune')) 'wrappers expose ignored inner failure'
Check ($s.Contains('TBS_WRAP_READ(read_status') -and $s.Contains('TBS_WRAP_READ(read_signal_strength')) 'monitor callbacks also latched'
Check (-not $completion.Contains('usb_kill_urb') -and $completion.Contains('schedule_work')) 'completion never synchronous kill'
Check ((Body $i 'tbs5530_drain').Contains('usb_kill_urb')) 'drain in process context'
Check ($shutdown.IndexOf('cancel_delayed_work_sync') -lt $shutdown.IndexOf('tbs5530_drain(dev)') -and $shutdown.Contains('cancel_work_sync')) 'cancel workers before drain/free'
Check ((Body $s 'tbs5530_disconnect').Contains('if (!tbs5530_runtime_shutdown(dev)) return')) 'no force-free on uncertain drain'
Check ($g.Contains('TBS_OP_SESSION_MS 120000') -and $g.Contains('TBS_OP_RECEIVE_MS 10000') -and $g.Contains('32ULL * 1024 * 1024')) 'bounds encoded in shared policy'
Check ($g.Contains('if (!actual)') -and $g.Contains('if (status)') -and $g.Contains('empty_credits_used >= s->empty_success_credit')) 'negative terminal; strict zero default with at most one global credit'
Check ($g.Contains('s->requested + TBS_OP_URB_SIZE > s->byte_limit')) 'requested cap includes in-flight capacity'
Check ($r.Contains('atomic64_set(&heartbeat_at') -and $g.Contains('TBS_OP_HEARTBEAT_MS 3000')) 'SSH loss heartbeat deadline'
Check ($r.Contains('if (!next) return -EPERM') -and $r.Contains('atomic_read(&probe_spent) && next')) 'stop/consent cannot rearm'
Check ($i.Contains('r->drained=false') -and (Body $i 'tbs5530_drain').Contains('mutex_lock(&r->control_lock)')) 'drained includes synchronous control barrier'
Check ($r.Contains('terminal_telemetry') -and $r.Contains('suppressed_controls')) 'terminal telemetry preserved'
Check ($completion.Contains('bool deliver=false, resubmit=false') -and $completion.Contains('deliver=!empty_success')) 'empty may resubmit but never reaches demux'
Check ($r.Contains('static unsigned int empty_success_credit;') -and $r.Contains('empty_success_credit>1')) 'empty credit default zero, hard ceiling one'
Check ($g.Contains('if (!s->first_rx_seen)') -and $r.Contains('first_rx_status=%d')) 'first RX metadata preserved through cancellation'
Write-Output "PASS $count operational source-path checks; not runtime or physical electrical proof."
