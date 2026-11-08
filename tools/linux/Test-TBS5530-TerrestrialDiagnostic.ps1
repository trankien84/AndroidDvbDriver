[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$SourcePath)
$ErrorActionPreference = 'Stop'
$s = [IO.File]::ReadAllText((Join-Path $SourcePath 'tbs5530.c'))
$h = [IO.File]::ReadAllText((Join-Path $SourcePath 'tbs5530.h'))
$g = [IO.File]::ReadAllText((Join-Path $SourcePath 'tbs5530_diag_guard.h'))
$operational = Test-Path -LiteralPath (Join-Path $SourcePath 'tbs5530_runtime_io.h')
$count = 0
function Assert-Guard([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "Guard proof failed: $Name" }
    $script:count++
}
function Body([string]$Name) {
    $m = [regex]::Match($s, '(?m)^static\s+(?:int|void)\s+(?:__maybe_unused\s+)?' + [regex]::Escape($Name) + '\s*\([^;]*?\)\s*\{')
    if (-not $m.Success) { throw "Function missing: $Name" }
    $start = $m.Index + $m.Length - 1
    $depth = 0
    for ($i = $start; $i -lt $s.Length; $i++) {
        if ($s[$i] -eq '{') { $depth++ }
        if ($s[$i] -eq '}') { $depth--; if ($depth -eq 0) { return $s.Substring($start, $i - $start + 1) } }
    }
    throw 'Unbalanced function.'
}
$probe = Body 'tbs5530_probe'
$fw = Body 'tbs5530_load_firmware'
$dvb = Body 'tbs5530_dvb_init'
$exit = Body 'tbs5530_dvb_exit'
Assert-Guard ($s -match 'static bool diagnostic_enable;' -and $s -match 'module_param\(diagnostic_enable, bool, 0400\)') 'default-off read-only opt-in'
if (-not $operational) {
Assert-Guard ($s -match 'static bool allow_firmware_bootstrap;' -and $probe.Contains('!diagnostic_enable || !allow_firmware_bootstrap')) 'explicit firmware-plan gate'
Assert-Guard ($probe.IndexOf('!diagnostic_enable') -lt $probe.IndexOf('tbs5530_load_firmware')) 'probe gate before firmware'
}
else {
    Assert-Guard ($probe.Contains('!operational_only || allow_firmware_bootstrap')) 'operational admission rejects bootstrap'
    Assert-Guard (-not $probe.Contains('tbs5530_load_firmware(')) 'no firmware call reachable from operational probe'
}
Assert-Guard ($probe.IndexOf('0x734c') -lt $probe.IndexOf('kzalloc') -and $probe.Contains('0x5530')) 'identity gate before state'
Assert-Guard ($fw.IndexOf('fw->size != 8192') -lt $fw.IndexOf('sha256(')) 'size before SHA read'
Assert-Guard ($fw.IndexOf('sha256(') -lt $fw.IndexOf('tbs_diag_fw_run')) 'real kernel hash before write plan'
Assert-Guard (-not ($fw -match 'tbs5530_op_rw|usb_control_msg')) 'firmware has no bypass writes'
Assert-Guard ($g.IndexOf('tbs_diag_gate(vid') -lt $g.IndexOf('ret = write(context')) 'shared authorization before writer'
Assert-Guard ($g.Contains('step < 132') -and $g.Contains('(step - 2) * 64') -and $g.Contains('0x7f92') -and $g.Contains('0xe600')) 'exact bounded 132-plan addresses'
Assert-Guard ($g.Contains('ret != (int)length') -and $g.Contains('return ret < 0 ? ret : -EIO')) 'short/error fail-stop'
Assert-Guard (-not ($s -match 'm88rs6060|fe_sat|request_module|0xb7|i2c_new_client_device')) 'no satellite node anywhere in board source'
Assert-Guard (-not ($h -match 'fe_sat|i2c_client_sat')) 'no satellite cleanup state'
Assert-Guard ($dvb.Contains('dvb_attach(cxd2878_attach') -and -not $dvb.Contains('alp_attach')) 'only terrestrial frontend exposed'
Assert-Guard (-not ($exit -match 'module_put|i2c_unregister_device|fe_sat')) 'cleanup no satellite calls'
Assert-Guard ($s.Contains('ops.delsys[0] = SYS_DVBT2') -and $s.Contains('ops.set_voltage = NULL')) 'DVB-T2-only capability no voltage ops'
Assert-Guard ((Body 'tbs5530_diag_tune').IndexOf('tbs5530_diag_profile') -lt (Body 'tbs5530_diag_tune').IndexOf('fe_ter_ops_orig.tune')) 'tune wrapper guards original'
Assert-Guard ((Body 'tbs5530_diag_set_frontend').IndexOf('tbs5530_diag_profile') -lt (Body 'tbs5530_diag_set_frontend').IndexOf('fe_ter_ops_orig.set_frontend')) 'frontend wrapper guards original'
Assert-Guard ($g.Contains('frequency == 594000000') -and $g.Contains('bandwidth == 8000000') -and $g.Contains('plp == 0 ?')) 'K36 profile PLP0 only; AUTO unverified and rejected'
Assert-Guard ($h -match '#define NUM_URBS\s+8' -and $h -match '#define URB_BUF_SIZE\s+65536') 'queue geometry unchanged'
$base = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'terrestrial-diagnostic-base.json') -Raw | ConvertFrom-Json
foreach ($entry in $base.Files | Where-Object { $_.Path -match '^(cxd|alp|compat)' }) {
    Assert-Guard ((Get-FileHash -LiteralPath (Join-Path $SourcePath $entry.Path)).Hash -eq $entry.SHA256) "Sony/ALP unchanged $($entry.Path)"
}
Write-Output "PASS $count source-structure and preservation checks; not physical electrical safety proof."
