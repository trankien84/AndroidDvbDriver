# SPDX-License-Identifier: GPL-2.0-only
#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot '..\Invoke-MinimalRun.ps1'
$run = 'phase31-original-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$root = Join-Path 'D:\TSDuck\logs' $run
New-Item -ItemType Directory -Path $root | Out-Null
$epoch = [guid]::NewGuid().ToString()
$argsForRun = @{ RunId = $run; UsbPath = '2-1'; BoardSha256 = ('a' * 64); PhysicalEpoch = $epoch }
$count = 0
function Expect-Failure([scriptblock]$Action, [string]$Pattern) {
    $failed = $false
    try { & $Action } catch { if ($_.Exception.Message -notmatch $Pattern) { throw }; $failed = $true }
    if (-not $failed) { throw 'Expected failure did not occur' }
}
try {
    Expect-Failure { & $runner @argsForRun -WhatIf } 'Physical confirmation receipt'; $count++
    @{ run = $run; epoch = $epoch; cold = $true } | ConvertTo-Json | Set-Content (Join-Path $root 'physical-confirmed.json')
    & $runner @argsForRun -WhatIf
    if (Test-Path (Join-Path $root 'host-consumed.cold.json')) { throw 'WhatIf consumed an attempt' }; $count++
    Expect-Failure { & $runner @argsForRun -Mode cold -StreamTiming early -WhatIf } 'cannot alter bootstrap'; $count++
    @{ epoch = $epoch; no_firmware = $true; early_stream_select = $false } | ConvertTo-Json | Set-Content (Join-Path $root 'operational-confirmed.json')
    Expect-Failure { & $runner @argsForRun -Mode handoff -StreamTiming early -WhatIf } 'explicit experimental grant'; $count++
    @{ epoch = $epoch; no_firmware = $true; early_stream_select = $true } | ConvertTo-Json | Set-Content (Join-Path $root 'operational-confirmed.json')
    & $runner @argsForRun -Mode handoff -StreamTiming early -WhatIf
    if (Test-Path (Join-Path $root 'host-consumed.handoff.json')) { throw 'WhatIf consumed handoff' }; $count++
    $source = [IO.File]::ReadAllText((Resolve-Path $runner))
    $boundary = $source.IndexOf('$si = [Diagnostics.ProcessStartInfo]::new()')
    if ($boundary -lt 0) { throw 'Runner boundary changed; cannot prove test stops before SSH' }
    $prefix = Join-Path $root 'prefix-no-ssh.ps1'
    [IO.File]::WriteAllText($prefix, $source.Substring(0, $boundary) + "throw 'TEST_STOP_BEFORE_SSH'`n")
    Expect-Failure { & $prefix @argsForRun -Confirm:$false } '^TEST_STOP_BEFORE_SSH$'; $count++
    $ledger = Join-Path $root 'host-consumed.cold.json'; $hash = (Get-FileHash $ledger).Hash
    Expect-Failure { & $prefix @argsForRun -Confirm:$false } 'already exists|already being used|file exists'; $count++
    if ((Get-FileHash $ledger).Hash -ne $hash) { throw 'Replay changed the ledger' }; $count++
    Expect-Failure { & $prefix @argsForRun -Mode handoff -StreamTiming early -Confirm:$false } '^TEST_STOP_BEFORE_SSH$'; $count++
    if (-not (Test-Path (Join-Path $root 'host-consumed.handoff.json'))) { throw 'Independent handoff ledger missing' }; $count++
    Write-Output "PASS $count runner checks: grants, timing, WhatIf, durable consume and replay; no SSH/process/USB calls."
} finally {
    foreach ($name in 'physical-confirmed.json', 'operational-confirmed.json', 'prefix-no-ssh.ps1', 'host-consumed.cold.json', 'host-consumed.handoff.json') {
        $p = Join-Path $root $name
        if (Test-Path -LiteralPath $p -PathType Leaf) { Remove-Item -LiteralPath $p }
    }
    if (@(Get-ChildItem -LiteralPath $root -Force).Count -eq 0) { Remove-Item -LiteralPath $root }
}
