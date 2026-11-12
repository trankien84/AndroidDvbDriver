# SPDX-License-Identifier: GPL-2.0-or-later
[CmdletBinding()]
param([Parameter(Mandatory)][string]$FirmwarePath)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$path=Join-Path $PSScriptRoot 'Invoke-TBS5530-PermissionFirstB2.ps1'
$tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Permission-first coordinator parse failed.'}
foreach($name in @('Test-PermissionFirstReady','Wait-PermissionFirst','Assert-PermissionFirstStoredPins')){
    $fn=$ast.Find({param($n)$n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true)
    if($null -eq $fn){throw 'Missing coordinator guard.'};Invoke-Expression $fn.Extent.Text
}
$token='11111111-1111-4111-8111-111111111111'
function Receipt {
    [pscustomobject]@{version=1;mode='PERMISSION_ONLY';preflight_token=$token;state='GRANTED';reason='OS_PERMISSION_VERIFIED';vid=29516;pid=21808;created_elapsed_ms=1000;deadline_elapsed_ms=61000;elapsed_ms=2000;device_identity_sha256=('a'*64);attachment_generation=0}
}
$checks=0
if(-not(Test-PermissionFirstReady (Receipt) $token 2001)){throw 'Valid fresh receipt refused.'};$checks++
foreach($field in @('version','mode','preflight_token','state','reason','vid','pid','created_elapsed_ms','deadline_elapsed_ms','elapsed_ms','device_identity_sha256','attachment_generation')){
    $r=Receipt;$r.PSObject.Properties.Remove($field)
    if(Test-PermissionFirstReady $r $token 2001){throw ('Missing field accepted '+$field)};$checks++
}
foreach($fault in @('token','stale','future','deadline','denied','identity','generation','typedclock')){
    $r=Receipt;$now=2001L
    switch($fault){
        'token' {$r.preflight_token='other'}
        'stale' {$now=4000}
        'future' {$now=1999}
        'deadline' {$r.deadline_elapsed_ms=62000}
        'denied' {$r.state='DENIED'}
        'identity' {$r.device_identity_sha256='not-a-hash'}
        'generation' {$r.attachment_generation=-1}
        'typedclock' {$r.elapsed_ms='2000'}
    }
    if(Test-PermissionFirstReady $r $token $now){throw ('Unsafe receipt accepted '+$fault)};$checks++
}
$package='info.martinmarinov.dvbdriver'
$script:reply=(Receipt)|ConvertTo-Json -Compress
$script:fakeClock=2001L
function Invoke-PreparationAdb([string[]]$Arguments){return $script:reply}
function Get-PreparationElapsed{return $script:fakeClock}
$ready=Wait-PermissionFirst $token
if($ready.state -cne 'GRANTED'){throw 'Ready flow did not return receipt.'};$checks++
foreach($state in @('DENIED','CANCELLED','TIMED_OUT','UNVERIFIED','unexpected')){
    $r=Receipt;$r.state=$state;$script:reply=$r|ConvertTo-Json -Compress
    $refused=$false;try{$null=Wait-PermissionFirst $token}catch{$refused=$_.Exception.Message -cmatch '^PERMISSION_ONLY_(REFUSED_NO_B2_GRANT|UNKNOWN_STATE)$'}
    if(-not $refused){throw ('Unsafe preflight flow not refused '+$state)};$checks++
}
$r=Receipt;$script:reply=$r|ConvertTo-Json -Compress;$script:fakeClock=4000
try{$null=Wait-PermissionFirst $token;throw 'Expected stale receipt refusal.'}catch{if($_.Exception.Message -cne 'PERMISSION_ONLY_RECEIPT_STALE_OR_MALFORMED'){throw}};$checks++
$hash=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
$base=@{Serial='offline-fixture';ExpectedBootCount=47;ExpectedAppApkSha256=('a'*64);ExpectedCoordinatorSha256=$hash;ExpectedAttachmentHistorySha256=('a'*64);ExpectedReloadStoreSha256=('b'*64);ExpectedReloadAnchorSha256=('c'*64);PhysicalRecoveryEpoch=$token;FirmwarePath=$FirmwarePath;OutputDirectory='D:\TSDuck\logs\offline-never-created';AdbPath=(Join-Path $PSScriptRoot 'no-such-adb.exe')}
$result=& $path @base -OfflinePreflight
if($result -cne 'OFFLINE_PREFLIGHT_PASS: no ADB, permission Activity, B2 grant or device I/O.'){throw 'Offline preflight armed coordinator.'};$checks++
try{& $path @base;throw 'Expected exact approval refusal.'}catch{if($_.Exception.Message -cne 'PERMISSION_FIRST_EXACT_APPROVAL_AND_PHYSICAL_CONFIRMATION_REQUIRED'){throw}};$checks++
try{& $path @base -ApproveOneNewB2 -ProofOfGuardPassed -PhysicalRecoveryConfirmed;throw 'Expected missing ADB refusal.'}catch{if($_.Exception.Message -cne 'PERMISSION_FIRST_PINNED_ADB_MISSING'){throw}};$checks++
$waitCall=@($ast.FindAll({param($n)$n -is [Management.Automation.Language.CommandAst] -and $n.GetCommandName() -ceq 'Wait-PermissionFirst'},$true))
$runtimeCall=@($ast.FindAll({param($n)$n -is [Management.Automation.Language.CommandAst] -and $n.Extent.Text.StartsWith('& $runtime -Serial')},$true))
if($waitCall.Count -ne 1 -or $runtimeCall.Count -ne 1 -or $runtimeCall[0].Extent.StartOffset -le $waitCall[0].Extent.EndOffset){throw 'B2 dispatch occurs before verified permission.'};$checks++
foreach($required in @('-DurationSeconds 300','WaitForExit(1500)','60000','b2-dispatch.once','Flush($true)','-ExpectedAttachmentHistorySha256','1bbb22fb805bca5ba7cfdee24e1ce468fdcfa1687280d451836e16ebef667497')){if(-not $ast.Extent.Text.Contains($required)){throw ('Missing unchanged guard '+$required)}};$checks++
$preparation=$ast.Extent.Text.Substring(0,$runtimeCall[0].Extent.StartOffset)
if($preparation -match 'controlTransfer|bulkTransfer|USBDEVFS|grant\.json|Tbs5530AutonomousDiagnosticActivity|pm clear|\buninstall\b|force-stop'){throw 'Permission preparation starts hardware/session state.'};$checks++
foreach($pin in @('ExpectedAttachmentHistorySha256','ExpectedReloadStoreSha256','ExpectedReloadAnchorSha256')){
    if($runtimeCall[0].Extent.Text -cnotmatch ('-'+$pin+' \$'+$pin+'(?:\s|$)')){throw ('Runtime does not forward exact approved pin '+$pin)}
    $checks++
}
$script:pinCalls=0;$script:pinFault=-1
function Invoke-PreparationAdb([string[]]$Arguments){
    $expectedPaths=@('files/autonomous-v1-samsung-sm-x926b-api36-attachments.txt','no_backup/fx2-reload-v1.txt','no_backup/fx2-reload-v1.anchor')
    $at=$script:pinCalls++
    if(($Arguments[0..3] -join ' ') -cne ('shell run-as '+$package+' sha256sum') -or $Arguments[4] -cne $expectedPaths[$at]){throw 'Unexpected stored-pin read.'}
    $value=@(('a'*64),('b'*64),('c'*64))[$at]
    if($at -eq $script:pinFault){$value='d'*64}
    return $value+'  '+$Arguments[4]
}
Assert-PermissionFirstStoredPins ('a'*64) ('b'*64) ('c'*64)
if($script:pinCalls -ne 3){throw 'Stored witnesses not all read.'};$checks++
foreach($fault in 0..2){
    $script:pinCalls=0;$script:pinFault=$fault
    try{Assert-PermissionFirstStoredPins ('a'*64) ('b'*64) ('c'*64);throw 'Changed stored pin accepted.'}
    catch{if($_.Exception.Message -cne 'PERMISSION_FIRST_APPROVED_HISTORY_OR_STORE_MISMATCH'){throw}}
    if($script:pinCalls -ne $fault+1){throw 'Pin mismatch did not stop immediately.'};$checks++
}
Write-Output "PASS: permission-first coordinator $checks/$checks guards; no real ADB or B2 dispatch."
