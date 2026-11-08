# SPDX-License-Identifier: GPL-2.0-only
[CmdletBinding()]
param([Parameter(Mandatory)][string]$ScratchParent)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\RuntimeLedger.ps1')
$parent = (Resolve-Path -LiteralPath $ScratchParent).Path
$work = Join-Path $parent ('ledger-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
$checks = 0
function Check([bool]$Condition, [string]$Name) { if (-not $Condition) { throw "FAIL: $Name" }; $script:checks++ }
function Reject([scriptblock]$Body, [string]$Name) { $caught = $false; try { & $Body | Out-Null } catch { $caught = $true }; Check $caught $Name }
try {
    $boot = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
    $sha = '22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5'
    $key = Get-TbsLinuxAttachmentKey $boot 2 2 $sha
    Check ($key -cmatch '^[a-f0-9]{64}$') 'key shape'
    Check ($key -ceq (Get-TbsLinuxAttachmentKey $boot 2 2 $sha)) 'stable key'
    Check ($key -cne (Get-TbsLinuxAttachmentKey $boot 2 3 $sha)) 'device generation changes key'
    Check ($key -cne (Get-TbsLinuxAttachmentKey 'ffffffff-bbbb-cccc-dddd-eeeeeeeeeeee' 2 2 $sha)) 'guest boot changes key'
    Reject { Get-TbsLinuxAttachmentKey 'bad' 2 2 $sha } 'bad boot'
    Reject { Get-TbsLinuxAttachmentKey $boot 0 2 $sha } 'bad bus'
    Reject { Get-TbsLinuxAttachmentKey $boot 2 128 $sha } 'bad device'
    Reject { Get-TbsLinuxAttachmentKey $boot 2 2 'bad' } 'bad descriptor hash'
    Reject { Add-TbsLinuxAttempt (Join-Path $work 'missing') $key 'phase31-test' ('a' * 64) } 'no silent ledger initialization'
    Assert-TbsLinuxAttemptAvailable $work $key
    Check (@(Get-ChildItem $work -File).Count -eq 0) 'availability probe does not consume'
    $attempt = Add-TbsLinuxAttempt $work $key 'phase31-test' ('a' * 64)
    Reject { Assert-TbsLinuxAttemptAvailable $work $key } 'availability rejects consumed before staging'
    Check ((Get-Content $attempt.path -Raw | ConvertFrom-Json).state -ceq 'CONSUMED_BEFORE_MODULE_LOAD') 'durable consume before caller can load'
    $hash = (Get-FileHash $attempt.path).Hash
    Reject { Add-TbsLinuxAttempt $work $key 'phase31-other' ('b' * 64) } 'different run or module cannot rearm attachment'
    Check ((Get-FileHash $attempt.path).Hash -ceq $hash) 'attempt immutable'
    Write-TbsLinuxAttemptResult $work $key 'BLOCKED' 'Synthetic guard refusal; no I/O'
    Check (Test-Path -LiteralPath (Join-Path $work ($key + '.result.json'))) 'result is separate'
    Reject { Add-TbsLinuxAttempt $work $key 'phase31-test' ('a' * 64) } 'terminal result does not rearm'
    Reject { Write-TbsLinuxAttemptResult $work $key 'COMPLETED' 'No replacement permitted' } 'result cannot overwrite'
    [IO.File]::WriteAllText($attempt.path, '{', [Text.UTF8Encoding]::new($false))
    Reject { Write-TbsLinuxAttemptResult $work $key 'COMPLETED' 'Malformed state stays blocked' } 'corrupt consumed record fails'
    Reject { Add-TbsLinuxAttempt $work $key 'phase31-test' ('a' * 64) } 'corrupt record cannot rearm'
} finally { if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force } }
"PASS: $checks ledger checks; synthetic only; no device I/O"
