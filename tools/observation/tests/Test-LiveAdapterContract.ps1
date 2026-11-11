# SPDX-License-Identifier: GPL-2.0-only
#Requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$source=Join-Path $PSScriptRoot '..\Invoke-LayeredObservation.ps1'
$tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($source,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Layered adapter parse failed'}
$checks=0
function Check([bool]$value,[string]$why){if(-not $value){throw $why};$script:checks++}
foreach($name in 'Parse-GuestIdentity','Make-RuntimePermit','Run-Phase','Set-Heartbeat','Guest-Health'){$fn=$ast.Find({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true);if($null -eq $fn){throw 'Missing adapter seam'};Invoke-Expression $fn.Extent.Text}
$descriptor='e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de'
$valid="TARGET|2-1|2|3|480|$descriptor|UNBOUND"
$r=Parse-GuestIdentity $valid;Check ($r.path -ceq '2-1' -and $r.bus -eq 2 -and $r.device -eq 3) 'Identity parser changed'
Check ($null -eq (Parse-GuestIdentity '')) 'No target became fake identity'
foreach($bad in @($valid+"`n"+$valid,$valid.Replace('|480|','|12|'),$valid.Replace('|UNBOUND','|BOUND'),$valid.Replace('2-1|','x;rm|'),$valid.Replace($descriptor,'unknown'))){$refused=$false;try{Parse-GuestIdentity $bad|Out-Null}catch{$refused=$true};Check $refused 'Bad/ambiguous identity admitted'}
$health=[pscustomobject]@{stdout='{"guest_now_ms":10000,"health":{"uptime_ms":9990,"alive":true,"ready":true}}'};Check ((Guest-Health $health).alive) 'Fresh guest health rejected'
foreach($sample in '{"guest_now_ms":10000,"health":{"uptime_ms":8000,"alive":true,"ready":true}}','{"guest_now_ms":10000,"health":{"uptime_ms":11000,"alive":true,"ready":true}}','{"guest_now_ms":"10000","health":{"uptime_ms":9990,"alive":true,"ready":true}}','{"guest_now_ms":10000,"health":{"uptime_ms":null,"alive":true,"ready":true}}'){ $denied=$false;try{Guest-Health ([pscustomobject]@{stdout=$sample})|Out-Null}catch{$denied=$true};Check $denied 'Stale/future/string guest clock accepted'}
$ended=Guest-Health ([pscustomobject]@{stdout='{"guest_now_ms":10000,"health":{"uptime_ms":1000,"alive":false,"ready":false,"reason":"TARGET_GONE"}}'});Check ($ended.alive -ceq $false -and $ended.reason -ceq 'TARGET_GONE') 'Ended cold observer must remain ended, not become fake alive or throw stale-active state'
$text=[IO.File]::ReadAllText($source)
Check (-not $text.Contains('.GetNewClosure()')) 'Dynamic live adapter scope must not be replaced by anonymous-module closure'
Check ($text.Contains('source_manifest_sha256') -and $text.Contains('REVIEWED_OBSERVER_SOURCE_CHANGED')) 'Grant does not bind observer sources'
Check ($text.Contains('HOST_CAPTURE_TARGET_NOT_PROVEN') -and $text.Contains("'admission',`$pcap")) 'Cold raw identity must precede first write'
Check ($text.Contains('FIRMWARE_INPUT_MISMATCH') -and $text.Contains("'firmware',`$pcap")) 'Firmware verification missing'
Check ($text.Contains('PHASE_DRAIN_NOT_CONFIRMED') -and $text.Contains('HOST_OBSERVER_TERMINAL_INVALID') -and $text.Contains('GUEST_OBSERVER_TERMINAL_INVALID')) 'Terminal/drain gates missing'
Check ($text.Contains("if(`$r.reason -cne 'RUNTIME_FINISHED')")) 'Observer/collection fault must not authorize operational handoff merely because cleanup drained'
foreach($bad in '--activity-clear-task','usbipd attach','--capture-from-all-devices','snapshot take','controlvm reset','poweroff','Stop-Process -Name','taskkill /IM'){Check (-not $text.Contains($bad)) ('Forbidden operation '+$bad)}
Check (-not [regex]::IsMatch($text,"['`"]-A['`"]")) 'Capture-all short flag forbidden'
$heartbeatFn=$ast.Find({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq 'Set-Heartbeat'},$true).Extent.Text
$channelText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '..\GuestChannel.sh'))
Check ($channelText.Contains('/proc/uptime') -and $channelText.Contains('observer-heartbeat.tmp') -and $channelText.Contains('mv observer-heartbeat.tmp observer-heartbeat')) 'Guest monotonic heartbeat must be fresh and atomically replaced'
Check ($heartbeatFn.Contains('Channel-Exchange') -and -not $heartbeatFn.Contains('Remote ')) 'Active heartbeat must not open another SSH connection'
Check ($text.Contains('$p.StandardInput.NewLine="`n"') -and $text.Contains('ReadLineAsync()') -and $text.Contains('WaitMs=1500')) 'Persistent protocol requires LF framing and a bounded reply'
$runFn=$ast.Find({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq 'Run-Phase'},$true).Extent.Text
$activePort=$runFn.Split('stop_observers {')[0]
Check (-not $activePort.Contains('Get-GuestIdentity;') -and -not $activePort.Contains('(Remote ') -and -not $activePort.Contains('Stage ') -and -not $activePort.Contains('Make-RuntimePermit $Phase')) 'Active phase must use the established command channel, not short SSH or SCP'
$grant=[pscustomobject]@{allow_firmware_once=$false;allow_terrestrial_receive=$false}
foreach($seam in 'permit','phase'){$denied=$false;try{if($seam -eq 'permit'){$null=Make-RuntimePermit cold $null}else{$null=Run-Phase cold $null ('a'*64)}}catch{$denied=$_.Exception.Message -ceq 'RUNTIME_DENIED_BY_STARTUP_ONLY_SCOPE'};Check $denied ('Startup-only scope reached runtime seam '+$seam)}
Check ($text.Contains('STARTUP_ONLY_READY_NO_FIRMWARE_OR_RUNTIME') -and $text.Contains('if(-not $startupOnly){') -and $text.Contains('startup-guest-evidence')) 'Startup-only diagnostics must preserve evidence without firmware staging'
Check ($text.Contains("New-ObserverPermit 'cold' `$null -NewDevices;`$state.state='ARMED_WAIT_PHYSICAL';Save-State;Show-LocalArmNotice") -and $text.Contains('LOCAL_ARM_NOTICE_UNAVAILABLE')) 'Approved local notice must occur only after real host readiness and fail if unavailable'
"PASS $checks live-adapter parse/identity/contract checks; no live adapter execution."
