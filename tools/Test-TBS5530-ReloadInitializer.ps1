# SPDX-License-Identifier: GPL-2.0-or-later
# Offline only: valid preflight returns before ADB lookup, all negative invocations refuse before execution.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$TemplateDirectory,[Parameter(Mandatory)][string]$PlanPath)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$helper=Join-Path $PSScriptRoot 'Initialize-TBS5530-ReloadStore.ps1'
$tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($helper,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Initializer parse failed.'}
$plan=(Get-FileHash -LiteralPath $PlanPath -Algorithm SHA256).Hash.ToLowerInvariant()
$base=@{Serial='offline-fixture';ExpectedBootCount=1;ExpectedApkSha256=('a'*64);ExpectedHelperSha256=(Get-FileHash -LiteralPath $helper -Algorithm SHA256).Hash.ToLowerInvariant();
    ExpectedPlanSha256=$plan;TemplateDirectory=$TemplateDirectory;OutputDirectory=(Join-Path $PSScriptRoot '..\app\build\never-created-by-offline-preflight');
    AdbPath=(Join-Path $PSScriptRoot 'no-such-adb.exe')}
$checks=0
$reply=& $helper @base -OfflinePreflight|ConvertFrom-Json
if($reply.result -cne 'OFFLINE_PREFLIGHT_PASS_NO_DEVICE_ACCESS' -or $reply.hardware_authorized -ne $false -or $reply.store_initialized -ne $false){throw 'Offline initializer unexpectedly armed.'};$checks++
foreach($field in @('ExpectedHelperSha256','ExpectedPlanSha256')){
    $bad=$base.Clone();$bad[$field]='b'*64
    try{& $helper @bad -OfflinePreflight;throw 'Expected pin refusal.'}catch{if($_.Exception.Message -notmatch '^BLOCKED: (initializer helper|initialization plan)'){throw}};$checks++
}
try{& $helper @base;throw 'Expected no-approval refusal.'}catch{if($_.Exception.Message -notmatch '^BLOCKED: cần exact first-initialization'){throw}};$checks++
try{& $helper @base -ApproveExactFirstInitialization;throw 'Expected no-proof refusal.'}catch{if($_.Exception.Message -notmatch '^BLOCKED: cần exact first-initialization'){throw}};$checks++
try{& $helper @base -ApproveExactFirstInitialization -ProofOfGuardPassed;throw 'Expected missing ADB refusal.'}catch{if($_.Exception.Message -cne 'BLOCKED: thiếu pinned ADB.'){throw}};$checks++
$root=Join-Path $PSScriptRoot ('..\app\build\b2-init-helper-fixture-'+[guid]::NewGuid().ToString());New-Item -ItemType Directory -Path $root|Out-Null
try{
    foreach($name in @('b2-reload-store-template.txt','b2-reload-anchor-template.txt','b2-reload-initialized-template.txt')){Copy-Item -LiteralPath (Join-Path $TemplateDirectory $name) -Destination (Join-Path $root $name)}
    [IO.File]::WriteAllBytes((Join-Path $root 'b2-reload-initialized-template.txt'),[byte[]]@(1,2,3))
    $bad=$base.Clone();$bad.TemplateDirectory=$root
    try{& $helper @bad -OfflinePreflight;throw 'Expected template refusal.'}catch{if($_.Exception.Message -notmatch '^BLOCKED: template'){throw}};$checks++
}finally{foreach($name in @('b2-reload-store-template.txt','b2-reload-anchor-template.txt','b2-reload-initialized-template.txt')){Remove-Item -LiteralPath (Join-Path $root $name)};Remove-Item -LiteralPath $root}
foreach($required in @("@('-s',`$Serial)",'WaitForExit(1500)','grant-approved.json','Flush($true)','first_initialization_authorized','no_tuner_confirmed',
    'fx2-reload-v1-init.consumed.json','Tbs5530ReloadInitActivity','INITIALIZATION_VERIFIED_NO_HARDWARE_AUTHORIZATION')){if(-not $ast.Extent.Text.Contains($required)){throw ('Missing initializer guard '+$required)}}
if($ast.Extent.Text -match '>\s*no_backup/' -or $ast.Extent.Text -match '\b(?:uninstall|pm clear|usbipd|force-stop)\b'){throw 'Forbidden initializer side effect.'}
$heartbeat=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'New-InitHeartbeatCommand'},$true)
if($null -eq $heartbeat){throw 'Missing initialization heartbeat builder.'};Invoke-Expression $heartbeat.Extent.Text
$command=New-InitHeartbeatCommand '44444444-4444-4444-8444-444444444444' $false
$bash='C:\Program Files\Git\bin\bash.exe'
if(-not(Test-Path -LiteralPath $bash)){throw 'Missing existing Git Bash for syntax-only validation.'}
& $bash -n -c $command;if($LASTEXITCODE -ne 0){throw 'Initialization heartbeat shell syntax failed.'};$checks++
try{New-InitHeartbeatCommand '../other' $false;throw 'Expected malformed session refusal.'}catch{if($_.Exception.Message -cne 'FAIL-STOP: session malformed.'){throw}};$checks++
Write-Output "PASS: initializer offline cross-language plan/template/helper/heartbeat guards $checks/$checks; no ADB or real store write."
