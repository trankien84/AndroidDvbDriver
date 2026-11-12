# SPDX-License-Identifier: GPL-2.0-or-later
# Offline only. All helper invocations must refuse before ADB or app access.
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$path=Join-Path $PSScriptRoot 'Invoke-TBS5530-HistoryRecovery.ps1'
$tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Recovery helper syntax failed'}
$text=[IO.File]::ReadAllText($path)
$checks=0
foreach($literal in @('ApproveExactAppend','ProofOfGuardPassed','ExpectedApkSha256','ExpectedBootCount',
    'APPEND_VERIFIED_NO_HARDWARE_AUTHORIZATION','host_script_sha256','full_history_proven=$false',
    'hardware_authorized=$false','WaitForExit(1500)','files/history-recovery-v1-',
    "'--activity-clear-top','--activity-single-top'",'Flush($true)',
    '$report.launch_token -cne $launch','$observedHash -cne $after','$challenge.apk_sha256 -cne $ExpectedApkSha256')){
    if(-not $text.Contains($literal)){throw "Missing guard $literal"};$checks++
}
foreach($literal in @('force-stop','uninstall','pm clear','usbipd','FirmwarePath','Tbs5530AutonomousDiagnosticActivity','rm -','adb connect')){
    if($text.Contains($literal)){throw "Unexpected side effect $literal"};$checks++
}
$base=@{Serial='offline-fixture';ExpectedBootCount=47;ExpectedApkSha256=('a'*64);CandidatePath='not-read';OutputDirectory='not-created'}
foreach($switch in @(@{},@{ApproveExactAppend=$true},@{ProofOfGuardPassed=$true})){
    $refused=$false;try{& $path @base @switch}catch{if($_.Exception.Message -notlike 'BLOCKED: cần duyệt exact append*'){throw};$refused=$true}
    if(-not $refused){throw 'Helper reached I/O without both gates'};$checks++
}
foreach($name in @('Quote-Argument','Get-HashToken','New-RecoveryHeartbeatCommand')){
    $fn=$ast.Find({param($n)$n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true)
    if(-not $fn){throw 'Missing helper function'};Invoke-Expression $fn.Extent.Text
}
if((Get-HashToken (('a'*64)+'  file')) -cne ('a'*64)){throw 'Valid SHA parse failed'};$checks++
foreach($bad in @('garbage',('a'*63)+' file',('A'*64)+' file')){
    $refused=$false;try{Get-HashToken $bad|Out-Null}catch{$refused=$true};if(-not $refused){throw 'Invalid SHA accepted'};$checks++
}
$command=New-RecoveryHeartbeatCommand '11111111-1111-4111-8111-111111111111' $false
if($command.IndexOf('read -r uptime idle < /proc/uptime') -ge $command.IndexOf('exec run-as') -or
    -not $command.Contains('set -eu') -or -not $command.Contains('elapsed_ms')){throw 'Clock must be read by shell UID before run-as'};$checks++
foreach($invalid in @('', 'bad', '11111111-1111-4111-8111-111111111111;echo secret')){
    $refused=$false;try{New-RecoveryHeartbeatCommand $invalid $false|Out-Null}catch{$refused=$true};if(-not $refused){throw 'Untrusted session reached shell command'};$checks++
}
if(-not $text.Contains('$reply.elapsed_ms -lt [long]$challenge.created_ms') -or -not $text.Contains('$clock.ElapsedMilliseconds -ge 1500')){
    throw 'Host must reject zero/stale heartbeat before publishing recovery grant'
};$checks++
Write-Output "PASS $checks recovery helper guards; no ADB, app launch, ledger or hardware I/O."
