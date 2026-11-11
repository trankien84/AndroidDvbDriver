# SPDX-License-Identifier: GPL-2.0-only
#Requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot '..\LayeredObservation.psm1') -Force
$root=Join-Path ([IO.Path]::GetTempPath()) ('tbs-layered-fixture-'+[guid]::NewGuid().ToString('N'));New-Item -ItemType Directory -Path $root|Out-Null
$checks=0
function Check([bool]$value,[string]$why){if(-not $value){throw $why};$script:checks++}
try {
 $id='phase31-layered-20261110-193945-623f1cf1';$now=[DateTimeOffset]::UtcNow
 $grant=[ordered]@{protocol='TBS5530-LAYERED-GRANT/v1';run_id=$id;approved=$true;fixture=$true;created_utc=$now.AddSeconds(-1).ToString('o');expires_utc=$now.AddMinutes(4).ToString('o');vm_uuid='11111111-1111-4111-8111-111111111111';guest_boot_id='22222222-2222-4222-8222-222222222222';capture_device='\\.\USBPcap99';expected_root_path='SYNTHETIC_ROOT';source_manifest_sha256=('1'*64);firmware_sha256='b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc';board_sha256='5ad5512988800c2ee6954faa4fed8058f33c489f505a01ac6086b2859a2141a6';allow_usb_ownership=$true;allow_temporary_filter=$true;allow_temporary_usbmon=$true;allow_observers=$true;allow_firmware_once=$true;allow_terrestrial_receive=$true;allow_selected_hub_new_devices=$true;controller='xHCI_UNCHANGED';maximum_phase_ms=120000;maximum_observer_ms=90000;maximum_observer_bytes=67108864}
 $path=Join-Path $root 'grant.json';Write-TbsObservationJson -Path $path -Value $grant -CreateNew;$hash=(Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant()
 $accepted=Assert-TbsObservationGrant $path $hash $id -Fixture;Check ($accepted.fixture -ceq $true) 'Fixture grant rejected'
 $z=[ordered]@{};foreach($key in $grant.Keys){$z[$key]=$grant[$key]};$z.created_utc=$now.AddSeconds(-1).UtcDateTime.ToString('o');$z.expires_utc=$now.AddMinutes(4).UtcDateTime.ToString('o');$zp=Join-Path $root 'utc-z.json';Write-TbsObservationJson $zp $z -CreateNew;$zh=(Get-FileHash $zp -Algorithm SHA256).Hash.ToLowerInvariant();$za=Assert-TbsObservationGrant $zp $zh $id -Fixture;Check ($za.created_utc -is [string]) 'UTC Z timestamp auto-converted then reparsed in local timezone'
 foreach($mode in 'live','hash','expired','future','limits','string','module','missing','scope','session'){
  $bad=[ordered]@{};foreach($key in $grant.Keys){$bad[$key]=$grant[$key]};switch($mode){live {$bad.fixture=$false};expired {$bad.expires_utc=$now.AddSeconds(-1).ToString('o')};future {$bad.created_utc=$now.AddMinutes(1).ToString('o')};limits {$bad.maximum_phase_ms=120001};string {$bad.approved='true'};module {$bad.board_sha256='0'*64};missing {$bad.Remove('allow_observers')};scope {$bad.allow_firmware_once=$false};session {$bad.expires_utc=$now.AddMinutes(10).ToString('o')}}
  $badPath=Join-Path $root ($mode+'.json');Write-TbsObservationJson -Path $badPath -Value $bad -CreateNew;$h=(Get-FileHash $badPath -Algorithm SHA256).Hash.ToLowerInvariant();if($mode -eq 'hash'){$h='0'*64};$denied=$false;try{Assert-TbsObservationGrant $badPath $h $id -Fixture|Out-Null}catch{$denied=$true};Check $denied ('Unsafe grant accepted '+$mode)
 }
 $localNotice=[ordered]@{};foreach($key in $grant.Keys){$localNotice[$key]=$grant[$key]};$localNotice.local_arm_notice='true';$np=Join-Path $root 'notice-invalid.json';Write-TbsObservationJson $np $localNotice -CreateNew;$nh=(Get-FileHash $np -Algorithm SHA256).Hash.ToLowerInvariant();$refused=$false;try{Assert-TbsObservationGrant $np $nh $id -Fixture|Out-Null}catch{$refused=$_.Exception.Message -ceq 'INVALID_LOCAL_NOTICE_SCOPE'};Check $refused 'Local notice flag must be a typed boolean'
 $diagnostic=[ordered]@{};foreach($key in $grant.Keys){$diagnostic[$key]=$grant[$key]};$diagnostic.startup_diagnostic_only=$true;$diagnostic.allow_firmware_once=$false;$diagnostic.allow_terrestrial_receive=$false
 $dp=Join-Path $root 'startup-only.json';Write-TbsObservationJson $dp $diagnostic -CreateNew;$dh=(Get-FileHash $dp -Algorithm SHA256).Hash.ToLowerInvariant();$dg=Assert-TbsObservationGrant $dp $dh $id -Fixture;Check ($dg.startup_diagnostic_only -ceq $true -and $dg.allow_firmware_once -ceq $false) 'Startup-only scope must deny firmware and runtime'
 foreach($fault in 'firmware','receive','string','missing-diagnostic'){$changed=[ordered]@{};foreach($key in $diagnostic.Keys){$changed[$key]=$diagnostic[$key]};switch($fault){firmware{$changed.allow_firmware_once=$true};receive{$changed.allow_terrestrial_receive=$true};string{$changed.startup_diagnostic_only='true'};missing-diagnostic{$changed.Remove('startup_diagnostic_only')}};$fp=Join-Path $root ('diagnostic-'+$fault+'.json');Write-TbsObservationJson $fp $changed -CreateNew;$fh=(Get-FileHash $fp -Algorithm SHA256).Hash.ToLowerInvariant();$denied=$false;try{Assert-TbsObservationGrant $fp $fh $id -Fixture|Out-Null}catch{$denied=$true};Check $denied ('Unsafe startup-only scope admitted '+$fault)}
 foreach($fault in 'none','admit','ready','start','lost','pump-type','runtime-result','stop','drain','collect','early-stop'){
  $dir=Join-Path $root $fault;New-Item -ItemType Directory -Path $dir|Out-Null;$ctx=@{events=[Collections.Generic.List[string]]::new();pumps=0;fault=$fault};if($fault -eq 'early-stop'){[IO.File]::WriteAllText((Join-Path $dir 'STOP'),'STOP')}
  $port={param($event,$phase) $ctx.events.Add($event)
   switch($event){
    admit {if($ctx.fault -eq 'admit'){throw 'FAKE_ADMISSION_REFUSAL'}}
    observers_ready {if($ctx.fault -eq 'ready'){throw 'FAKE_NOT_READY'}}
    start_runtime {if($ctx.fault -eq 'start'){throw 'FAKE_START_UNCERTAIN'}}
    pump {$ctx.pumps++;if($ctx.fault -eq 'pump-type'){return [pscustomobject]@{observers_ok='true';runtime_exited=$true}};return [pscustomobject]@{observers_ok=($ctx.fault -ne 'lost');runtime_exited=$true;expected_cold_transition=$false}}
    runtime_result {if($ctx.fault -eq 'runtime-result'){throw 'FAKE_RESULT_ERROR'};return [pscustomobject]@{result=0;fixture=$true}}
    stop_runtime {if($ctx.fault -eq 'stop'){throw 'FAKE_STOP_ERROR'}}
    drain {return ($ctx.fault -ne 'drain')}
    collect {if($ctx.fault -eq 'collect'){throw 'FAKE_COLLECT_ERROR'}}
   }
  }.GetNewClosure()
  $result=Invoke-TbsLayeredPhase -Phase operational -Directory $dir -Port $port
  Check (-not $result.measurement_pass) 'Coordinator claimed TS success'
  Check ($ctx.events.Contains('stop_observers')) 'Observers not stopped'
  if($fault -in 'admit','ready','early-stop'){Check (-not $result.runtime_started) 'Runtime started before ready/admission';Check (-not(Test-Path (Join-Path $dir 'phase.started'))) 'Denied phase consumed unexpectedly'}
  else {Check $result.attempt_consumed 'Attempt was not durably consumed';Check ($ctx.events.Contains('stop_runtime')) 'Missing runtime stop on started/uncertain child'}
  if($fault -eq 'lost'){Check ($result.reason -eq 'OBSERVER_LOST') 'Lost observer ignored'}
  if($fault -eq 'drain'){Check (-not $result.cleanup_confirmed) 'Drain failure promoted to cleanup PASS'}
  if($fault -eq 'none'){Check $result.cleanup_confirmed 'Clean synthetic cleanup failed';Check ($result.reason -eq 'RUNTIME_FINISHED') 'Synthetic completion failed'}
  $beforeHash=(Get-FileHash (Join-Path $dir 'phase-terminal.json') -Algorithm SHA256).Hash;$refused=$false;try{Invoke-TbsLayeredPhase operational $dir $port|Out-Null}catch{$refused=$_.Exception.Message -ceq 'PHASE_ALREADY_CONSUMED_OR_RECORDED'};Check $refused 'Attempt or recorded refusal replayed';Check (((Get-FileHash (Join-Path $dir 'phase-terminal.json') -Algorithm SHA256).Hash) -ceq $beforeHash) 'Terminal evidence overwritten'
 }
 $save=Join-Path $root 'atomic.json';Write-TbsObservationJson $save @{value=1} -CreateNew;$denied=$false;try{Write-TbsObservationJson $save @{value=2} -CreateNew}catch{$denied=$true};Check $denied 'Evidence overwritten'
 Write-Output "PASS $checks coordinator/grant checks; synthetic only, no USB/SSH/process runtime."
} finally {Remove-Item -LiteralPath $root -Recurse -Force}
