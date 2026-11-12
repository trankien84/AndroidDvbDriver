# SPDX-License-Identifier: GPL-2.0-or-later
[CmdletBinding()]
param([Parameter(Mandatory)][string]$BaselinePath,[Parameter(Mandatory)][string]$WitnessPath)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$helper=Join-Path $PSScriptRoot 'Invoke-TBS5530-HistoryRecovery.ps1'
$tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($helper,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Witness helper parse failed.'}
$checks=0
$before=[IO.File]::ReadAllBytes($BaselinePath);$after=[IO.File]::ReadAllBytes($WitnessPath)
if($before.Length -ne 581 -or $after.Length -ne 996 -or (Get-FileHash -LiteralPath $BaselinePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne 'e398459ceddb2c8f35ddc61b81ee66a31ec1aee300c33bb603dc9f593b0b31f9' -or (Get-FileHash -LiteralPath $WitnessPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne 'b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb'){throw 'Private exact witness fixture mismatch.'}
for($i=0;$i -lt $before.Length;$i++){if($before[$i] -ne $after[$i]){throw 'Witness prefix differs.'}}
$checks++
$argsBase=@{Serial='offline-fixture';ExpectedBootCount=47;ExpectedApkSha256=('a'*64);CandidatePath=$WitnessPath;OutputDirectory=(Join-Path $PSScriptRoot '..\app\build\witness-never-created');AdbPath=(Join-Path $PSScriptRoot 'no-such-adb.exe');RestoreExactHostWitnessV2=$true}
try{& $helper @argsBase;throw 'Expected approval refusal.'}catch{if($_.Exception.Message -notmatch '^BLOCKED: cần duyệt exact append'){throw}};$checks++
try{& $helper @argsBase -ApproveExactAppend -ProofOfGuardPassed -ExpectedHelperSha256 ('b'*64);throw 'Expected helper hash refusal.'}catch{if($_.Exception.Message -cne 'BLOCKED: witness helper hash differs from approval.'){throw}};$checks++
$helperHash=(Get-FileHash -LiteralPath $helper -Algorithm SHA256).Hash.ToLowerInvariant()
try{& $helper @argsBase -ApproveExactAppend -ProofOfGuardPassed -ExpectedHelperSha256 $helperHash;throw 'Expected missing ADB refusal.'}catch{if($_.Exception.Message -cne 'BLOCKED: thiếu pinned ADB.'){throw}};$checks++
$selected=$ast.EndBlock.Statements | Where-Object {($_ -is [Management.Automation.Language.AssignmentStatementAst] -and $_.Left.Extent.Text -in @('$before','$after','$plan','$protocol','$afterBytes')) -or ($_ -is [Management.Automation.Language.IfStatementAst] -and $_.Extent.Text.StartsWith('if($RestoreExactHostWitnessV2)') -and $_.Extent.Text.Contains('$canonical='))}
$Serial='offline-fixture';$RestoreExactHostWitnessV2=$true;$ExpectedHelperSha256=$helperHash;$PSCommandPathSaved=$PSCommandPath
$planFixture=($selected|ForEach-Object{$_.Extent.Text}) -join "`n"
$planFixture=$planFixture.Replace('$PSCommandPath',''''+$helper+'''')
Invoke-Expression $planFixture
if($plan -cne 'da1ccd0a385706147d405a90529594b407d21301d61676b682e14f1c6ba38eb2' -or $afterBytes -ne 996 -or $protocol -cne 'TBS5530-HISTORY-WITNESS-APPLY/v2'){throw 'Cross-language witness plan differs.'};$checks++
$guard=$ast.Find({param($n)$n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq 'Test-InitUsbInventoryEmpty'},$true)
if($null -eq $guard){throw 'Missing current USB guard.'}
$init=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Initialize-TBS5530-ReloadStore.ps1'),[ref]$tokens,[ref]$errors)
$initGuard=$init.Find({param($n)$n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq 'Test-InitUsbInventoryEmpty'},$true)
if($guard.Extent.Text -cne $initGuard.Extent.Text){throw 'Recovery and initializer inventory guard differ.'};$checks++
foreach($needle in @('history-recovery-v1-','grant-approved.json','Flush($true)','expires_ms','120000L','WaitForExit(1500)','EXACT_HOST_WITNESS_V2','full_history_proven=$false')){if(-not $ast.Extent.Text.Contains($needle)){throw ('Missing witness guard '+$needle)}};$checks++
if($ast.Extent.Text -match 'pm clear|\buninstall\b|usbipd|force-stop|>\s*no_backup/'){throw 'Forbidden recovery side effect.'};$checks++
Write-Output "PASS: witness recovery offline guards $checks/$checks; exact prefix/hash/plan, no ADB or app write."
