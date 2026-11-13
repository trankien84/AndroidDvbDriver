#Requires -Version 7.0
# SPDX-License-Identifier: GPL-2.0-or-later
# Offline only: simulated child/clock and synthetic export bytes; no ADB/device calls.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$helper=Join-Path $PSScriptRoot 'Invoke-TBS5530-B5Session.ps1'
$t=$null;$e=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($helper,[ref]$t,[ref]$e)
if($e.Count){throw 'Helper parse failed'}
foreach($f in @($ast.EndBlock.Statements|Where-Object{$_ -is [Management.Automation.Language.FunctionDefinitionAst]})){Invoke-Expression $f.Extent.Text}
$b3=Join-Path $PSScriptRoot 'Invoke-TBS5530-B3Session.ps1';Import-B5SharedGuards $b3 ((Get-FileHash $b3 -Algorithm SHA256).Hash.ToLowerInvariant());Set-B5Overrides
if(Test-Path $OutputDirectory){throw 'Fresh output required'};$null=New-Item -ItemType Directory -Path $OutputDirectory
$transport=${function:Invoke-B5Adb}.ToString()
if(-not $transport.Contains('Get-B5AdbTimeout $DeviceArguments $BinaryDestination $AfterDrain $ExpectedExportBytes') -or -not $transport.Contains('WaitForExit($timeout)')){throw 'Deadline policy not wired to real bounded process wait'}
$checks=0
function Check([bool]$v,[string]$n){if(-not $v){throw ('FAIL '+$n)};$script:checks++}
function Refuse([scriptblock]$f,[string]$n){$caught=$false;try{& $f|Out-Null}catch{$caught=$true};Check $caught $n}
class ExportChildFixture {
    [int]$elapsed;[bool]$hung;[bool]$stopped
    ExportChildFixture([int]$elapsed,[bool]$hung){$this.elapsed=$elapsed;$this.hung=$hung}
    [bool]WaitForExit([int]$timeout){return -not $this.hung -and $this.elapsed -le $timeout}
    [void]Stop(){ $this.stopped=$true }
}
$package='info.martinmarinov.dvbdriver';$session='11111111-1111-4111-8111-111111111111'
$remote='files/autonomous-v1-'+$session+'/received-ON1.ts'
$argsBinary=@('exec-out','run-as',$package,'sh','-c',('head -c 268435457 '+$remote))
Check ((Get-B5AdbTimeout @('shell','getprop','ro.product.model') '' $false -1) -eq 1500) 'live control remains 1500 ms'
Check ((Get-B5AdbTimeout @('shell','run-as',$package,'sha256sum',$remote) '' $true -1) -eq 10000) 'after-drain hash remains 10000 ms'
Check ((Get-B5AdbTimeout @('shell','run-as',$package,'wc','-c',$remote) '' $true -1) -eq 10000) 'after-drain size remains 10000 ms'
Check ((Get-B5AdbTimeout $argsBinary 'new.ts' $true 34340864) -eq 96000) 'live observed ON1 size yields bounded 96 s export'
Check ((Get-B5AdbTimeout $argsBinary 'new.ts' $true 11141120) -eq 90000) 'ON2 minimum 90 s'
Check ((Get-B5AdbTimeout $argsBinary 'new.ts' $true 268435456) -eq 180000) 'max byte budget caps deadline 180 s'
foreach($fault in @('before-drain','missing-size','string-size','oversize','wrong-head-cap','unbounded-cat','nonexec')){
    $a=$argsBinary.Clone();$after=$true;$size=34340864
    switch($fault){'before-drain'{$after=$false};'missing-size'{$size=-1};'string-size'{$size='34340864'};'oversize'{$size=268435457};'wrong-head-cap'{$a[5]='head -c 268435456 '+$remote};'unbounded-cat'{$a=@('exec-out','run-as',$package,'cat',$remote)};'nonexec'{$a[0]='shell'}}
    Refuse {Get-B5AdbTimeout $a 'new.ts' $after $size} ('extended timeout scope rejects '+$fault)
}
$child=[ExportChildFixture]::new(20000,$false)
Check (-not $child.WaitForExit(10000)) 'original 10 s would truncate 20 s export'
Check ($child.WaitForExit((Get-B5AdbTimeout $argsBinary 'new.ts' $true 34340864))) 'same 20 s binary accepted within 96 s'
$report=[IO.File]::ReadAllText('D:\TSDuck\logs\phase31-b5-20261113-124425-302b54cb\b5-budget-25afe484-c22d-476f-b5bf-b516b5804a35\attempt-4\terminal-report.json')|ConvertFrom-Json
$synthetic=[byte[]](0..255);$syntheticHash=Get-B5Hash $synthetic
function Get-B5ExportPaths([string]$Session){return @($remote)}
$script:fault='';$script:shaCalls=0;$script:binaryDeadline=0;$script:childStopped=$false
function Invoke-B5Adb([string[]]$DeviceArguments,[string]$InputText='',[string]$BinaryDestination='',[bool]$AfterDrain=$false,[object]$ExpectedExportBytes=-1){
    if(-not $AfterDrain){throw 'Fake export without typed drain'}
    Assert-B5ExportCall $DeviceArguments $Report $Session $BinaryDestination
    if($DeviceArguments[0] -ceq 'exec-out'){
        $script:binaryDeadline=Get-B5AdbTimeout $DeviceArguments $BinaryDestination $AfterDrain $ExpectedExportBytes
        $child=[ExportChildFixture]::new(20000,($script:fault -ceq 'hung'))
        if(-not $child.WaitForExit($script:binaryDeadline)){$child.Stop();$script:childStopped=$child.stopped;throw 'B5_FAKE_BINARY_EXPORT_TIMEOUT'}
        [IO.File]::WriteAllBytes($BinaryDestination,$synthetic)
        if($script:fault -ceq 'size'){[IO.File]::WriteAllBytes($BinaryDestination,[byte[]](1,2))}
        return
    }
    $joined=$DeviceArguments -join ' '
    if($joined -match 'if \[ -f'){return 'PRESENT'}
    if($joined -match ' wc -c '){return '256 synthetic'}
    if($joined -match ' sha256sum '){$script:shaCalls++;return $(if($script:fault -ceq 'hash' -and $script:shaCalls -eq 2){('f'*64)+' synthetic'}else{$syntheticHash+' synthetic'})}
    throw 'Unexpected fake read-only export operation'
}
foreach($fault in @('success','hung','size','hash')){
    $script:fault=$fault;$script:shaCalls=0;$script:childStopped=$false
    $folder=Join-Path $OutputDirectory $fault;$rawAttemptPath=Join-Path $folder 'raw';$null=New-Item -ItemType Directory -Path $rawAttemptPath -Force
    if($fault -ceq 'success'){
        $exports=Export-B5Evidence $session $report $folder
        Check ($exports.Count -eq 1 -and $exports[0].bytes -eq 256 -and $exports[0].sha256 -ceq $syntheticHash -and $script:binaryDeadline -eq 90000) 'actual export producer keeps size/hash certificate after simulated 20 s'
    }else{
        Refuse {Export-B5Evidence $session $report $folder} ('actual export fails closed '+$fault)
        if($fault -ceq 'hung'){Check $script:childStopped 'hung export owned child stopped after bounded deadline'}
        Check (-not(Test-Path (Join-Path $folder 'export-manifest.json'))) ('no success export manifest after '+$fault)
    }
}
$result=[ordered]@{status='PASS';checks=$checks;helper_sha256=(Get-FileHash $helper -Algorithm SHA256).Hash.ToLowerInvariant();real_adb_calls=0;hardware='NOT_RUN';wall_sleep_ms=0;fixture='Fake child elapsed 20 s / hung; actual Export-B5Evidence exercised synthetic size/hash mismatches.'}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'test-summary.json'),($result|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Write-Output "PASS bounded export deadline $checks/$checks; fake clock/child, no ADB or sleeps. $OutputDirectory"
