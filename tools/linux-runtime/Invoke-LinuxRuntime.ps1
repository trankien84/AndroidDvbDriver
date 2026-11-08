# SPDX-License-Identifier: GPL-2.0-only
#Requires -Version 7.0
[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory)][ValidatePattern('^phase31-linux-runtime-[0-9]{8}-[0-9]{6}-[a-f0-9]{8}$')][string]$RunId,
    [Parameter(Mandatory)][ValidatePattern('^/root/phase31-[A-Za-z0-9_-]+/source$')][string]$ModuleDirectory,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$BoardSha256,
    [Parameter(Mandatory)][string]$ModuleArgumentsPath,
    [Parameter(Mandatory)][string]$LedgerDirectory,
    [ValidatePattern('^execution(?:-[a-z0-9]+)?$')][string]$ExecutionLabel = 'execution',
    [ValidateSet('ubuntu-VMS')][string]$SshAlias = 'ubuntu-VMS'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'RuntimeLedger.ps1')
$ssh = (Get-Command ssh.exe -ErrorAction Stop).Source
$scp = (Get-Command scp.exe -ErrorAction Stop).Source
$root = 'D:\TSDuck'
$log = Join-Path "$root\logs" $RunId
if (-not (Test-Path -LiteralPath (Join-Path $log 'session.json'))) { throw 'Expected session manifest is missing' }
$guest = '/root/' + $RunId
$argumentFile = (Resolve-Path -LiteralPath $ModuleArgumentsPath).Path
$arguments = @([IO.File]::ReadAllLines($argumentFile) | Where-Object { $_ -ne '' })
if ($arguments.Count -ne 11 -and $arguments.Count -ne 12) { throw 'Expected exact operational module argument set' }
$allowed = @('diagnostic_enable', 'operational_only', 'allow_firmware_bootstrap', 'expected_busnum', 'expected_devnum', 'expected_profile_sha256', 'session_ms', 'receive_ms', 'max_bytes', 'consent', 'heartbeat', 'empty_success_credit')
foreach ($value in $arguments) {
    $parts = $value -split '=', 2
    if ($parts.Count -ne 2 -or $parts[0] -notin $allowed) { throw 'Unknown module parameter' }
    if ($parts[0] -eq 'expected_profile_sha256') {
        if ($parts[1] -cne '22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5') { throw 'Descriptor parameter mismatch' }
    } elseif ($parts[1] -notmatch '^\d+$') { throw 'Invalid numeric module parameter' }
    if ($parts[0] -eq 'empty_success_credit' -and $parts[1] -notin '0', '1') { throw 'Only one explicitly reviewed empty-success credit is allowed' }
}
foreach ($required in @('diagnostic_enable=1', 'operational_only=1', 'allow_firmware_bootstrap=0', 'session_ms=120000', 'receive_ms=10000', 'max_bytes=33554432', 'consent=1', 'heartbeat=1')) {
    if ($arguments -notcontains $required) { throw "Exact module limit or consent missing: $required" }
}
$names = @($arguments | ForEach-Object { ($_ -split '=')[0] })
if (@($names | Sort-Object -Unique).Count -ne $names.Count) { throw 'Duplicate module parameter' }
$ledger = Assert-TbsLinuxLedgerDirectory $LedgerDirectory
if (-not $PSCmdlet.ShouldProcess("$SshAlias ; $RunId", 'Consume attachment once, trace before operational-only module load, K36 bounded receive and cleanup; no A0/snapshot')) { return }
$execution = Join-Path $log $ExecutionLabel
if (Test-Path -LiteralPath $execution) { throw 'Execution directory exists; never replay a run' }
New-Item -ItemType Directory -Path $execution | Out-Null
function Invoke-Tool([string]$Executable, [string[]]$ToolArguments, [string]$Label, [int]$Seconds = 20, [switch]$AllowFailure) {
    $si = [Diagnostics.ProcessStartInfo]::new()
    $si.FileName = $Executable; $si.UseShellExecute = $false; $si.CreateNoWindow = $true
    $si.RedirectStandardOutput = $true; $si.RedirectStandardError = $true
    foreach ($arg in $ToolArguments) { $si.ArgumentList.Add($arg) }
    $p = [Diagnostics.Process]::new(); $p.StartInfo = $si
    $start = [DateTime]::UtcNow
    if (-not $p.Start()) { throw 'Child process did not start' }
    $stdout = $p.StandardOutput.ReadToEndAsync(); $stderr = $p.StandardError.ReadToEndAsync()
    $done = $p.WaitForExit($Seconds * 1000)
    if (-not $done) { $p.Kill($true); $p.WaitForExit() }
    $text = $stdout.GetAwaiter().GetResult(); $errorText = $stderr.GetAwaiter().GetResult(); $exitCode = $p.ExitCode
    [IO.File]::WriteAllText((Join-Path $execution ($Label + '.stdout.txt')), $text)
    [IO.File]::WriteAllText((Join-Path $execution ($Label + '.stderr.txt')), $errorText)
    @{ pid = $p.Id; executable = $Executable; arguments = $ToolArguments; start_utc = $start.ToString('o'); end_utc = [DateTime]::UtcNow.ToString('o'); exit = $exitCode; watchdog_killed = -not $done } | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $execution ($Label + '.meta.json')) -Encoding utf8
    $p.Dispose()
    if ((-not $done -or $exitCode -ne 0) -and -not $AllowFailure) { throw "$Label failed: exit=$exitCode; finished=$done" }
    return [pscustomobject]@{ stdout = $text; stderr = $errorText; exit = $exitCode; completed = $done }
}
$sshArgs = @('-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10', $SshAlias)
$preflight = @'
set -eu
printf 'boot=%s\n' "$(cat /proc/sys/kernel/random/boot_id)"
count=0
for p in /sys/bus/usb/devices/*; do
 if test -f "$p/idVendor" && test "$(cat "$p/idVendor")" = 734c && test "$(cat "$p/idProduct")" = 5530; then
  count=$((count+1)); printf 'bus=%s\ndevice=%s\nspeed=%s\n' "$(cat "$p/busnum")" "$(cat "$p/devnum")" "$(cat "$p/speed")"
  printf 'descriptor=%s\n' "$(sha256sum "$p/descriptors" | cut -d' ' -f1)"
  test ! -e "$p:1.0/driver" && test ! -L "$p:1.0/driver"
 fi
done
test "$count" = 1
'@
$preflight = $preflight.Replace("`r`n", "`n")
$identity = Invoke-Tool $ssh ($sshArgs + @($preflight)) 'preflight'
$values = @{}
foreach ($line in ($identity.stdout -split '\r?\n')) { if ($line -match '^(boot|bus|device|speed|descriptor)=(.+)$') { if ($values.ContainsKey($Matches[1])) { throw 'Duplicate identity field' }; $values[$Matches[1]] = $Matches[2] } }
if ($values.Count -ne 5 -or $values.speed -ne '480' -or $values.descriptor -cne '22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5') { throw 'Operational admission mismatch' }
$key = Get-TbsLinuxAttachmentKey $values.boot ([int]$values.bus) ([int]$values.device) $values.descriptor
Assert-TbsLinuxAttemptAvailable $ledger $key
# The kernel independently checks these values. A stale host argument file cannot authorize a new attachment.
if ($arguments -notcontains ('expected_busnum=' + $values.bus) -or $arguments -notcontains ('expected_devnum=' + $values.device) -or $arguments -notcontains 'operational_only=1' -or $arguments -notcontains 'diagnostic_enable=1' -or $arguments -contains 'allow_firmware_bootstrap=1') { throw 'Operational module arguments do not match fresh attachment' }
$files = @('ReceiveK36.c', 'GuestRuntime.sh')
foreach ($file in $files) {
    if ($file.EndsWith('.sh') -and [IO.File]::ReadAllText((Join-Path $PSScriptRoot $file)).Contains("`r")) { throw 'Bash source must use LF before staging; no hardware attempted' }
    $null = Invoke-Tool $scp @('-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10', (Join-Path $PSScriptRoot $file), ($SshAlias + ':' + $guest + '/' + $file)) ('stage-' + $file.Replace('.', '-'))
}
$null = Invoke-Tool $scp @('-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10', $argumentFile, ($SshAlias + ':' + $guest + '/module-arguments.txt')) 'stage-module-arguments'
$prepare = "set -eu; cd '$guest'; test ! -e runner.stdout.txt; test `"`$(sha256sum '$ModuleDirectory/tbs5530_ter_diag.ko' | cut -d' ' -f1)`" = '$BoardSha256'; gcc -std=c11 -Wall -Wextra -Werror -DTBS_SELF_TEST ReceiveK36.c -o receive-policy-test; ./receive-policy-test; gcc -std=c11 -Wall -Wextra -Werror ReceiveK36.c -o ReceiveK36; bash -n GuestRuntime.sh; sha256sum GuestRuntime.sh ReceiveK36 module-arguments.txt > runtime-files.sha256; sha256sum '$ModuleDirectory/alp.ko' '$ModuleDirectory/cxd2878.ko' '$ModuleDirectory/tbs5530_ter_diag.ko' > module-files.sha256; test -d /root/phase31-linux-operational-ledger-v1; test -f /root/phase31-linux-operational-ledger-v1/initialized"
$null = Invoke-Tool $ssh ($sshArgs + @($prepare)) 'prepare-guest' 30
$attempt = Add-TbsLinuxAttempt $ledger $key $RunId $BoardSha256
$terminal = 'UNCERTAIN'
try {
    $command = "timeout -s TERM -k 12s 85s bash '$guest/GuestRuntime.sh' '$RunId' '$($values.boot)' '$($values.bus)' '$($values.device)' '$key' '$ModuleDirectory' '$BoardSha256'"
    $run = Invoke-Tool $ssh ($sshArgs + @($command)) 'bounded-runtime' 105 -AllowFailure
    # Copy evidence even for a nonzero receiver; filenames and source directory are exact and private.
    $destination = Join-Path $execution 'guest'
    New-Item -ItemType Directory -Path $destination | Out-Null
    $null = Invoke-Tool $scp @('-r', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10', ($SshAlias + ':' + $guest + '/.'), $destination) 'collect-evidence' 60
    $resultFile = Join-Path $destination 'result.txt'
    if (Test-Path -LiteralPath $resultFile) {
        $resultText = [IO.File]::ReadAllText($resultFile)
        if ($resultText -match '(?m)^cleanup_ok=1\s*$') { $terminal = if ($run.exit -eq 0) { 'COMPLETED' } else { 'BLOCKED' } }
    }
    @{ state = $terminal; runtime_exit = $run.exit; attachment_key = $key; board_sha256 = $BoardSha256; no_a0 = 'MUST_VERIFY_TRACE'; ts_smoke_pass_android = $false } | ConvertTo-Json | Set-Content (Join-Path $execution 'result.json') -Encoding utf8
} finally { Write-TbsLinuxAttemptResult $ledger $key $terminal 'Single bounded operational-only attempt; see execution result and traces. Do not replay.' }
if ($terminal -eq 'UNCERTAIN') { throw 'Cleanup or execution remains uncertain; no further writes or replay' }
Get-Content -LiteralPath (Join-Path $execution 'result.json')
if ($terminal -ne 'COMPLETED') { throw 'Bounded runtime completed cleanup but did not receive TS; inspect BLOCKED result and never replay attachment' }
