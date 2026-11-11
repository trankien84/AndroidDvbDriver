# SPDX-License-Identifier: GPL-2.0-only
#Requires -Version 7.5
[CmdletBinding()]
param()
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot '..\LayeredObservation.psm1') -Force
Import-Module (Join-Path $PSScriptRoot '..\WindowsObserver.psm1') -Force
$root=Join-Path ([IO.Path]::GetTempPath()) ('tbs-observation-integration-'+[guid]::NewGuid().ToString('N'));New-Item -ItemType Directory -Path $root|Out-Null
$run='phase31-layered-20261110-193945-623f1cf1';$checks=0
function Check([bool]$value,[string]$why){if(-not $value){throw $why};$script:checks++}
function Status($worker){Get-TbsWindowsObserverStatus -Observer $worker|ConvertFrom-Json}
try {
 $pwsh=(Get-Process -Id $PID).Path;$stdinProbe=Invoke-TbsObservationCommand $pwsh @('-NoProfile','-Command','if([Console]::In.ReadToEnd().Length -ne 0){exit 2};[Console]::Out.Write("EOF_OBSERVED")') 5000 (Join-Path $root 'stdin-eof')
 Check ($stdinProbe.completed -ceq $true -and $stdinProbe.exit -eq 0 -and $stdinProbe.stdout -ceq 'EOF_OBSERVED') 'Bounded children inherited an open console stdin'
 foreach($fault in 'none','operator-stop','observer-exit'){
  $d=Join-Path $root $fault;New-Item -ItemType Directory -Path $d|Out-Null
  $permit=[ordered]@{protocol='TBS5530-WINDOWS-OBSERVER/v1';run_id=$run;phase='operational';approved=$true;fixture=$true;root_grant_sha256=('a'*64);expires_utc=[DateTime]::UtcNow.AddMinutes(3).ToString('o');capture_device='\\.\USBPcap99';device_address=3;capture_mode='Address';usbpcap_path='SYNTHETIC_FIXTURE';output_directory=(Join-Path $d 'observer');maximum_ms=90000;maximum_bytes=67108864;snaplen=131072;bufferlen=1048576;fixture_case='HeaderIdle'}
  $file=Join-Path $d 'permit.json';Write-TbsObservationJson $file $permit -CreateNew;$hash=(Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()
  $ctx=@{worker=$null;started=$false;stopped=$false;fault=$fault;pumps=0;dir=$d;file=$file;hash=$hash}
  $port={param($event,$phase)
   switch($event){
    admit { }
    observers_ready {
     $ctx.worker=Start-TbsWindowsObserver -PermitPath $ctx.file -PermitSha256 $ctx.hash
     $w=[Diagnostics.Stopwatch]::StartNew();do{$s=Status $ctx.worker;if($s.ready -ceq $true -and $s.alive -ceq $true){return};if($ctx.worker.Process.HasExited){throw ('FIXTURE_EXITED: '+(Get-Content (Join-Path $ctx.worker.Directory 'terminal.json') -Raw))};Start-Sleep -Milliseconds 50}while($w.ElapsedMilliseconds -lt 10000);throw ('FIXTURE_NOT_READY: '+($s|ConvertTo-Json -Depth 6 -Compress))
    }
    start_runtime {$ctx.started=$true;if($ctx.fault -eq 'operator-stop'){[IO.File]::WriteAllText((Join-Path $ctx.dir 'STOP'),'STOP')};if($ctx.fault -eq 'observer-exit'){Stop-TbsWindowsObserver -Observer $ctx.worker -WaitSeconds 10|Out-Null}}
    pump {$ctx.pumps++;$s=Status $ctx.worker;return [pscustomobject]@{observers_ok=($s.alive -ceq $true -and $s.ready -ceq $true);runtime_exited=($ctx.pumps -ge 3);expected_cold_transition=$false}}
    runtime_result {return [pscustomobject]@{result=0;fixture=$true}}
    stop_runtime {$ctx.stopped=$true}
    drain {return $true}
    stop_observers {if($null -ne $ctx.worker){Stop-TbsWindowsObserver -Observer $ctx.worker -WaitSeconds 10|Out-Null}}
    collect { }
   }
  }
  $result=Invoke-TbsLayeredPhase operational $d $port
  if(-not $ctx.started){throw ('Synthetic readiness failed: '+$result.reason)}
  Check $ctx.started 'Synthetic runtime did not begin after readiness';Check $ctx.stopped 'Runtime STOP hook was not called';Check (-not $result.measurement_pass) 'Fixture produced a live result';Check $ctx.worker.Process.HasExited 'Owned recorder worker leaked'
  $terminal=Get-Content (Join-Path $ctx.worker.Directory 'terminal.json') -Raw|ConvertFrom-Json;Check ($terminal.measurement_valid -ceq $false) 'Fixture promoted to measurement'
  if($fault -eq 'none'){Check ($result.reason -ceq 'RUNTIME_FINISHED') 'Normal fake process flow failed'}
  if($fault -eq 'operator-stop'){Check ($result.reason -ceq 'OPERATOR_STOP') 'STOP marker was ignored'}
  if($fault -eq 'observer-exit'){Check ($result.reason -ceq 'OBSERVER_LOST') 'Observer exit did not stop runtime'}
 }
 "PASS $checks process/coordinator integration checks; synthetic worker only, no USB/SSH."
} finally {Remove-Item -LiteralPath $root -Recurse -Force}
