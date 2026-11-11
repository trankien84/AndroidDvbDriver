# SPDX-License-Identifier: GPL-2.0-or-later
# Offline only: extracted predicates and preflight refusals, never ADB or device I/O.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$path = Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
$tokens=$null; $errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Helper parse failed'}
foreach($name in 'Test-TBS5530LaunchChallenge','Test-TBS5530DrainedTerminal','Get-TBS5530ControlPlaneExitCode'){
    $fn=$ast.Find({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true)
    if($null -eq $fn){throw "Missing predicate $name"};Invoke-Expression $fn.Extent.Text
}
$checks=0
function Check([bool]$value,[string]$why){if(-not $value){throw $why};$script:checks++}
$launch='11111111-1111-4111-8111-111111111111'
function Challenge { [pscustomobject]@{policy='TBS5530-AUTONOMOUS/v1';launch_token=$launch;control_plane_only=$true;live_ready=$false;created_elapsed_ms=1000} }
$r=Challenge;Check (Test-TBS5530LaunchChallenge $r $launch $true 1000) 'Fresh correlated validation rejected'
Check (Test-TBS5530LaunchChallenge $r $launch $true 10999) 'Valid challenge age rejected'
Check (-not(Test-TBS5530LaunchChallenge $r $launch $true 11000)) 'Expired challenge accepted'
Check (-not(Test-TBS5530LaunchChallenge $r $launch $true 999)) 'Future challenge accepted'
Check (-not(Test-TBS5530LaunchChallenge $r '22222222-2222-4222-8222-222222222222' $true 1000)) 'Old token accepted despite a recent timestamp'
Check (-not(Test-TBS5530LaunchChallenge $r $launch $false 1000)) 'Validation challenge authorized live mode'
foreach($token in @('invalid','00000000-0000-0000-0000-000000000000')) {
    $bad=Challenge;$bad.launch_token=$token;Check (-not(Test-TBS5530LaunchChallenge $bad $token $true 1000)) 'Malformed matching nonce accepted'
}
foreach($field in 'launch_token','control_plane_only','live_ready','created_elapsed_ms','policy'){
    $bad=Challenge;$bad.PSObject.Properties.Remove($field)
    Check (-not(Test-TBS5530LaunchChallenge $bad $launch $true 1000)) 'Missing challenge field accepted'
}
foreach($field in 'control_plane_only','live_ready'){
    $bad=Challenge;$bad.$field='true';Check (-not(Test-TBS5530LaunchChallenge $bad $launch $true 1000)) 'String boolean accepted'
}
function Report { [pscustomobject]@{state='WAIT_DEVICE';result='CONTROL_PLANE_COMPLETE';cleanup='DRAIN_CONFIRMED';cleanup_path='NO_RESOURCES_ACQUIRED';terminal=$true;
    firmware=$null;native_urb=$null;interface_selection=$null;launch_token=$launch;control_plane_only=$true;grant_accepted=$true;control_mirror_records=0;ts_bytes=0;ts_smoke_pass=$false} }
$r=Report;Check ((Get-TBS5530ControlPlaneExitCode $r $launch)-eq 0) 'Verified no-I/O report rejected'
foreach($mutation in @(
    @{key='cleanup';value='DRAIN_NOT_CERTIFIED'},@{key='terminal';value=$false},@{key='terminal';value='true'},
    @{key='native_urb';value=[pscustomobject]@{outstanding=1;quarantined=$false}},@{key='firmware';value=[pscustomobject]@{completed=132}},
    @{key='interface_selection';value=[pscustomobject]@{api_calls=1}},@{key='control_plane_only';value=$false},
    @{key='grant_accepted';value=$false},@{key='ts_bytes';value=1},@{key='control_mirror_records';value=1},
    @{key='ts_smoke_pass';value=$true},@{key='ts_smoke_pass';value='false'},@{key='ts_bytes';value='0'},
    @{key='control_mirror_records';value='0'},@{key='launch_token';value='22222222-2222-4222-8222-222222222222'},
    @{key='cleanup_path';value='NOT_CERTIFIED'},@{key='result';value='FOREGROUND_LOST'}
)){$bad=Report;$bad.($mutation.key)=$mutation.value;Check ((Get-TBS5530ControlPlaneExitCode $bad $launch)-ne 0) 'Unsafe validation report accepted'}
foreach($field in 'terminal','firmware','native_urb','interface_selection','grant_accepted','cleanup_path'){
    $bad=Report;$bad.PSObject.Properties.Remove($field);Check ((Get-TBS5530ControlPlaneExitCode $bad $launch)-ne 0) 'Incomplete validation report accepted'
}
foreach($state in 'WAIT_REENUMERATION','NEED_ANDROID_USB_PERMISSION','FRONTEND_INIT','TS_CAPTURE') {
    $drained=Report;$drained.state=$state;$drained.cleanup_path='RUNTIME_DRAIN_CONFIRMED';$drained.result='ATTACH_PERMISSION_UI_TIMEOUT'
    $drained.firmware=[pscustomobject]@{completed=132}
    Check (Test-TBS5530DrainedTerminal $drained) 'Typed terminal/drained bootstrap ignored because the last work state was not REPORT'
    Check ((Get-TBS5530ControlPlaneExitCode $drained $launch)-ne 0) 'A terminal bootstrap was misclassified as control-plane success'
    $drained.terminal=$false;Check (-not(Test-TBS5530DrainedTerminal $drained)) 'Intermediate report accepted before runtime termination'
}
$invalid=Report;$invalid.state='UNKNOWN';Check (-not(Test-TBS5530DrainedTerminal $invalid)) 'Unknown terminal state accepted'
foreach($case in @(@{FirmwarePath='offline-fixture'},@{PhysicalRecoveryEpoch=$launch},@{PhysicalRecoveryConfirmed=$true},@{AutoUsbPermission=$true},@{ControlMirror=$true})){
    $refused=$false
    try{& $path -Serial 'offline-fixture' -ExpectedModel SM-X926B -ExpectedApi 36 -ExpectedBootCount 46 -ControlPlaneOnly -GrantBoundedSession -ProofOfGuardPassed @case}
    catch{if($_.Exception.Message -notlike 'BLOCKED: control-plane-only*'){throw};$refused=$true}
    Check $refused 'Control-plane mode accepted hardware scope before ADB'
}
Write-Output "PASS $checks launch/control-plane checks; no ADB, firmware, USB or permission dialog."
