# SPDX-License-Identifier: GPL-2.0-only
#Requires -Version 7.0
[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory)][ValidatePattern('^phase31-original-[0-9]{8}-[0-9]{6}-[a-f0-9]{8}$')][string]$RunId,
    [Parameter(Mandatory)][ValidatePattern('^[0-9]+-[0-9]+(\.[0-9]+)*$')][string]$UsbPath,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$BoardSha256,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9-]{36}$')][string]$PhysicalEpoch,
    [ValidateSet('cold', 'handoff')][string]$Mode = 'cold',
    [ValidateSet('late', 'early')][string]$StreamTiming = 'late'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$ssh = (Get-Command ssh.exe -CommandType Application -ErrorAction Stop).Source
$root = Join-Path 'D:\TSDuck\logs' $RunId
if (-not (Test-Path -LiteralPath $root -PathType Container)) { throw 'Missing run directory' }
$receipt = Join-Path $root 'physical-confirmed.json'
if (-not (Test-Path -LiteralPath $receipt)) { throw 'Physical confirmation receipt is required' }
$approval = Get-Content -LiteralPath $receipt -Raw | ConvertFrom-Json
if ($approval.epoch -cne $PhysicalEpoch -or $approval.run -cne $RunId -or $approval.cold -ne $true) { throw 'Physical confirmation does not match' }
if ($Mode -eq 'cold' -and $StreamTiming -ne 'late') { throw 'Timing experiment cannot alter bootstrap' }
if ($Mode -eq 'handoff') {
    $handoff = Get-Content -LiteralPath (Join-Path $root 'operational-confirmed.json') -Raw | ConvertFrom-Json
    if ($handoff.epoch -cne $PhysicalEpoch -or $handoff.no_firmware -ne $true) { throw 'Operational grant mismatch' }
    if ($StreamTiming -eq 'early' -and $handoff.early_stream_select -ne $true) { throw 'Early stream timing requires an explicit experimental grant' }
}
if (-not $PSCmdlet.ShouldProcess("ubuntu-VMS / $RunId / $UsbPath / $Mode", 'Consume once, exact M mode, K36/PLP0 and bounded receive; heartbeat, drain; no snapshot')) { return }
$ledger = Join-Path $root "host-consumed.$Mode.json"
if ($Mode -eq 'cold' -and (Test-Path -LiteralPath (Join-Path $root 'host-consumed.json'))) { throw 'Historical cold attempt already consumed' }
$stream = [IO.File]::Open($ledger, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
try {
    $bytes = [Text.Encoding]::UTF8.GetBytes((@{ run = $RunId; epoch = $PhysicalEpoch; usb = $UsbPath; board = $BoardSha256; consumed_utc = [DateTime]::UtcNow.ToString('o') } | ConvertTo-Json))
    $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true)
} finally { $stream.Dispose() }
$si = [Diagnostics.ProcessStartInfo]::new()
$si.FileName = $ssh; $si.UseShellExecute = $false; $si.CreateNoWindow = $true
$si.RedirectStandardOutput = $true; $si.RedirectStandardError = $true
foreach ($arg in @('-T', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10', '-o', 'ServerAliveInterval=1', '-o', 'ServerAliveCountMax=1', 'ubuntu-VMS', "timeout -s TERM 70s bash /root/$RunId/GuestMinimalRun.sh $RunId $UsbPath $BoardSha256 $Mode $StreamTiming")) { $si.ArgumentList.Add($arg) }
$p = [Diagnostics.Process]::new(); $p.StartInfo = $si
if (-not $p.Start()) { throw 'Runner did not start; attachment remains consumed' }
$out = $p.StandardOutput.ReadToEndAsync(); $err = $p.StandardError.ReadToEndAsync()
$begin = [DateTime]::UtcNow; $sequence = 2; $heartbeatFailed = $false
while (-not $p.HasExited -and ([DateTime]::UtcNow - $begin).TotalSeconds -lt 80) {
    $command = "if test -w /sys/module/tbs5530_minimal/parameters/heartbeat; then printf '%s' $sequence > /sys/module/tbs5530_minimal/parameters/heartbeat; fi"
    & $ssh -T -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=2 -o ServerAliveInterval=1 -o ServerAliveCountMax=1 ubuntu-VMS $command >> (Join-Path $root "host-heartbeat.stdout.$Mode.txt") 2>> (Join-Path $root "host-heartbeat.stderr.$Mode.txt")
    $code = $LASTEXITCODE
    if ($code -ne 0) { $heartbeatFailed = $true; break }
    $sequence++; Start-Sleep -Milliseconds 350
}
if (-not $p.HasExited) {
    & $ssh -T -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=2 -o ServerAliveInterval=1 -o ServerAliveCountMax=1 ubuntu-VMS 'if test -w /sys/module/tbs5530_minimal/parameters/stop; then printf 1 > /sys/module/tbs5530_minimal/parameters/stop; fi' >> (Join-Path $root "host-stop.stdout.$Mode.txt") 2>> (Join-Path $root "host-stop.stderr.$Mode.txt")
    if (-not $p.WaitForExit(15000)) { throw "Runner still active PID=$($p.Id); do not unload, detach, kill or reset; inspect quarantine" }
}
[IO.File]::WriteAllText((Join-Path $root "host-runner.stdout.$Mode.txt"), $out.GetAwaiter().GetResult())
[IO.File]::WriteAllText((Join-Path $root "host-runner.stderr.$Mode.txt"), $err.GetAwaiter().GetResult())
@{ pid = $p.Id; exit = $p.ExitCode; heartbeat_failed = $heartbeatFailed; heartbeat_count = $sequence - 2; begin_utc = $begin.ToString('o'); end_utc = [DateTime]::UtcNow.ToString('o') } | ConvertTo-Json | Set-Content (Join-Path $root "host-runner.$Mode.json") -Encoding utf8
$code = $p.ExitCode; $p.Dispose()
if ($heartbeatFailed -or $code -ne 0) { throw "Bounded runtime ended nonzero: exit=$code heartbeatFailed=$heartbeatFailed; inspect result, no retry" }
Write-Output 'RUN_FINISHED: inspect USB/DVR/TS evidence and cleanup; this is not TS_SMOKE_PASS.'
