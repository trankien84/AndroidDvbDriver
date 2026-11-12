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
$inventory=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Test-InitUsbInventoryEmpty'},$true)
if($null -eq $inventory){throw 'Missing empty USB inventory guard.'};Invoke-Expression $inventory.Extent.Text
# Shape from the observed Samsung host_manager; addresses and timestamps are synthetic.
$historyOnly=@'
  host_manager={
    num_connects=4
    connections=[
      {
        device_address=/dev/bus/usb/001/011
        mode=0
        timestamp=1000
        manufacturer=29516
        product=21808
        is_headset={
          in=false
          out=false
        }
      }
      {
        device_address=/dev/bus/usb/001/011
        mode=-1
        timestamp=2000
      }
      {
        device_address=/dev/bus/usb/001/012
        mode=0
        timestamp=3000
        manufacturer=29516
        product=21808
        is_headset={
          in=false
          out=false
        }
      }
      {
        device_address=/dev/bus/usb/001/012
        mode=-1
        timestamp=4000
      }
    ]
  }
'@
$historyOnly=$historyOnly.Replace("`r`n","`n")
$oldBlock=[regex]::Match($historyOnly,'(?ms)^  host_manager=\{.*?^  \}').Value
if(-not $oldBlock -or $oldBlock -notmatch 'vendor_id=|vendorId=|device_address='){throw 'History fixture does not reproduce the old false refusal.'};$checks++
$empty="  host_manager={`n    num_connects=0`n  }"
$emptyList="  host_manager={`n    num_connects=0`n    connections=[`n    ]`n  }"
$live="    devices={`n      vendor_id=29516`n      product_id=21808`n    }`n"
$cases=@(
    @('no history',$empty,$true),
    @('explicit empty history',$emptyList,$true),
    @('Samsung history only LF',$historyOnly,$true),
    @('Samsung history only CRLF',$historyOnly.Replace("`n","`r`n"),$true),
    @('filters outside host ignored',$historyOnly+"`n  settings_manager={`n    vendor_id=29516`n    device_address=filter-only`n  }",$true),
    @('live target before history',$historyOnly.Replace('    num_connects=',$live+'    num_connects='),$false),
    @('live unrelated device',$historyOnly.Replace('    num_connects=',$live.Replace('29516','1')+'    num_connects='),$false),
    @('empty-looking devices section',$empty.Replace('    num_connects=',"    devices=[]`n    num_connects="),$false),
    @('missing host','unknown OEM format',$false),
    @('duplicate host',$empty+"`n"+$historyOnly,$false),
    @('missing count',$historyOnly.Replace("    num_connects=4`n",''),$false),
    @('duplicate count',$historyOnly.Replace('    num_connects=4',"    num_connects=4`n    num_connects=4"),$false),
    @('invalid count',$historyOnly.Replace('num_connects=4','num_connects=unknown'),$false),
    @('truncated history',$historyOnly.Replace("    ]`n",''),$false),
    @('unbalanced history record',$historyOnly.Replace("          out=false`n        }","          out=false"),$false),
    @('unknown host field after history',$historyOnly.Replace("    ]`n","    ]`n    devices=[]`n"),$false),
    @('unknown host field before count',$empty.Replace('    num_connects=',"    future_field=true`n    num_connects="),$false),
    @('positive count without history',$empty.Replace('num_connects=0','num_connects=4'),$false),
    @('zero count with records',$historyOnly.Replace('num_connects=4','num_connects=0'),$false),
    @('missing host close',$historyOnly.Substring(0,$historyOnly.Length-3),$false)
)
foreach($case in $cases){
    $actual=@(Test-InitUsbInventoryEmpty $case[1])
    if($actual.Count -ne 1 -or $actual[0] -isnot [bool] -or $actual[0] -ne $case[2]){throw ('USB inventory guard mismatch: '+$case[0])}
    $checks++
}
$identity=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Assert-InitIdentity'},$true)
if($null -eq $identity){throw 'Missing initialization identity guard.'};Invoke-Expression $identity.Extent.Text
$ExpectedBootCount=1
$script:inventoryFixture=$historyOnly
function Invoke-InitAdb([string[]]$Arguments){
    switch -Exact ($Arguments -join ' '){
        'get-state' {return 'device'}
        'shell getprop ro.product.model' {return 'SM-X926B'}
        'shell getprop ro.build.version.sdk' {return '36'}
        'shell settings get global boot_count' {return '1'}
        'shell dumpsys usb' {return $script:inventoryFixture}
        default {throw 'Unexpected fake ADB command.'}
    }
}
Assert-InitIdentity;$checks++
foreach($dump in @($historyOnly.Replace('    num_connects=',$live+'    num_connects='),'unknown OEM format')){
    $script:inventoryFixture=$dump
    try{Assert-InitIdentity;throw 'Expected live/unknown inventory refusal.'}catch{if($_.Exception.Message -cne 'BLOCKED: khởi tạo yêu cầu USB inventory rỗng và schema đã xác minh.'){throw}}
    $checks++
}
Remove-Variable inventoryFixture -Scope Script
Write-Output "PASS: initializer offline cross-language plan/template/helper/heartbeat/inventory guards $checks/$checks; no ADB or real store write."
