# SPDX-License-Identifier: GPL-2.0-only
#Requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][string]$ScratchParent)
$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot '..\Invoke-LinuxRuntime.ps1'
$run = 'phase31-linux-runtime-20990101-000000-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$log = Join-Path 'D:\TSDuck\logs' $run
$work = Join-Path (Resolve-Path $ScratchParent).Path ('plan-test-' + [guid]::NewGuid().ToString('N'))
$checks = 0
function Check([bool]$Condition, [string]$Name) { if (-not $Condition) { throw "FAIL: $Name" }; $script:checks++ }
New-Item -ItemType Directory -Path $log, $work | Out-Null
try {
    [IO.File]::WriteAllText((Join-Path $log 'session.json'), '{"synthetic":true}')
    $base = @('diagnostic_enable=1', 'operational_only=1', 'allow_firmware_bootstrap=0', 'expected_busnum=2', 'expected_devnum=2', 'expected_profile_sha256=22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5', 'session_ms=120000', 'receive_ms=10000', 'max_bytes=33554432', 'consent=1', 'heartbeat=1')
    $argumentPath = Join-Path $work 'module-arguments.txt'
    $invoke = @{ RunId = $run; ModuleDirectory = '/root/phase31-synthetic/source'; BoardSha256 = ('a' * 64); ModuleArgumentsPath = $argumentPath; LedgerDirectory = $work; WhatIf = $true }
    [IO.File]::WriteAllLines($argumentPath, $base)
    & $runner @invoke | Out-Null
    Check (-not (Test-Path (Join-Path $log 'execution'))) 'valid WhatIf has no execution output'
    Check (@(Get-ChildItem $work -Filter '*.result.json').Count -eq 0) 'WhatIf does not consume ledger'
    [IO.File]::WriteAllLines($argumentPath, @($base + 'empty_success_credit=1'))
    & $runner @invoke | Out-Null
    Check (-not (Test-Path (Join-Path $log 'execution'))) 'one empty-success credit remains WhatIf-only'
    [IO.File]::WriteAllLines($argumentPath, @($base + 'empty_success_credit=2'))
    $caught = $false; try { & $runner @invoke | Out-Null } catch { $caught = $true }
    Check $caught 'reject more than one empty-success credit'
    foreach ($change in @(
        @('diagnostic_enable=1', 'diagnostic_enable=0'),
        @('operational_only=1', 'operational_only=0'),
        @('allow_firmware_bootstrap=0', 'allow_firmware_bootstrap=1'),
        @('session_ms=120000', 'session_ms=120001'),
        @('receive_ms=10000', 'receive_ms=10001'),
        @('max_bytes=33554432', 'max_bytes=33554433'),
        @('consent=1', 'consent=0'),
        @('heartbeat=1', 'heartbeat=0'),
        @('expected_devnum=2', 'unknown=2'),
        @('expected_busnum=2', 'expected_devnum=2'),
        @('expected_devnum=2', 'expected_devnum=2;reboot'),
        @('expected_profile_sha256=22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5', 'expected_profile_sha256=bad')
    )) {
        $mutated = @($base | ForEach-Object { if ($_ -ceq $change[0]) { $change[1] } else { $_ } })
        [IO.File]::WriteAllLines($argumentPath, $mutated)
        $caught = $false; try { & $runner @invoke | Out-Null } catch { $caught = $true }
        Check $caught ('reject ' + $change[1])
        Check (-not (Test-Path (Join-Path $log 'execution'))) 'rejected plan never creates execution output'
    }
} finally {
    if (Test-Path $work) { Remove-Item -LiteralPath $work -Recurse -Force }
    if (Test-Path $log) { Remove-Item -LiteralPath $log -Recurse -Force }
}
"PASS: $checks runtime plan checks; WhatIf only, no SSH/module/device I/O"
