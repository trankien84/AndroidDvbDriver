# SPDX-License-Identifier: GPL-2.0-only
#Requires -Version 7.5
[CmdletBinding()]
param()
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$bash='C:\Program Files\Git\bin\bash.exe';if(-not(Test-Path $bash)){throw 'Git Bash required for portable channel fixture'}
$root=Join-Path ([IO.Path]::GetTempPath()) ('tbs-channel-fixture-'+[guid]::NewGuid().ToString('N'));New-Item -ItemType Directory -Path $root|Out-Null
$checks=0;$p=$null;$stage='startup';$lastCommand='NONE'
function Check([bool]$v,[string]$why){if(-not $v){throw $why};$script:checks++}
try{
 $posix=(& $bash -c 'cd "$1" && pwd -P' sh $root).Trim();if($LASTEXITCODE -ne 0){throw 'Path mapping failed'}
 $scriptPath=(Resolve-Path (Join-Path $PSScriptRoot '..\GuestChannel.sh')).Path
 $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=$bash;$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardInput=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
 foreach($a in @($scriptPath,$posix,'SYNTHETIC_BOOT',[string][DateTimeOffset]::UtcNow.AddSeconds(30).ToUnixTimeMilliseconds(),'1')){$start.ArgumentList.Add($a)}
 $p=[Diagnostics.Process]::new();$p.StartInfo=$start;[void]$p.Start();$p.StandardInput.NewLine="`n";$err=$p.StandardError.ReadToEndAsync();$task=$p.StandardOutput.ReadLineAsync();if(-not $task.Wait(5000)){throw 'Fixture channel not ready'};$ready=$task.Result|ConvertFrom-Json;Check ($ready.ready -ceq $true -and $ready.fixture -eq 1) 'Fixture cannot be mistaken for live'
 $channel=[pscustomobject]@{Process=$p;Sequence=0}
 $tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot '..\Invoke-LayeredObservation.ps1'),[ref]$tokens,[ref]$errors);if($errors.Count){throw 'Live channel adapter parse failed'}
 $fn=$ast.Find({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq 'Channel-Exchange'},$true);Invoke-Expression $fn.Extent.Text
 function Exchange([string]$command){$script:lastCommand=$command;$v=(Channel-Exchange $command).data;Check ($v.sequence -eq $script:channel.Sequence) 'Sequence mismatch';return $v}
 $stage='split-frame';$channel.Sequence++;$p.StandardInput.Write("$($channel.Sequence) PU");$p.StandardInput.Flush();Start-Sleep -Milliseconds 200;$p.StandardInput.Write("LSE`n");$p.StandardInput.Flush();$fragment=$p.StandardOutput.ReadLineAsync();Check ($fragment.Wait(1500) -and ($fragment.Result|ConvertFrom-Json).sequence -eq $channel.Sequence) 'Split SSH frame was discarded by polling read'
 $stage='normal-exchange';$v=Exchange PULSE;Check ($null -eq $v.health) 'Missing health became fake ready';Check (Test-Path (Join-Path $root 'host-heartbeat')) 'Host heartbeat not persisted';Check (Test-Path (Join-Path $root 'observer-heartbeat')) 'Observer heartbeat not persisted'
 $obs=Join-Path $root observer;New-Item -ItemType Directory -Path $obs|Out-Null;$beat=[IO.File]::ReadAllText((Join-Path $root 'host-heartbeat'));[IO.File]::WriteAllText((Join-Path $obs 'health.json'),('{"alive":true,"ready":true,"uptime_ms":'+$beat+'}'))
 $v=Exchange PULSE;Check ($v.health.alive -ceq $true -and $v.guest_now_ms -ge $v.health.uptime_ms) 'Fresh clock sampled before health read'
 $null=Exchange STOP_RUNTIME;Check (Test-Path (Join-Path $root 'STOP_RUNTIME')) 'STOP runtime marker missing'
 $null=Exchange STOP_OBSERVER;Check (Test-Path (Join-Path $root 'STOP_OBSERVER')) 'STOP observer marker missing'
 [IO.File]::WriteAllText((Join-Path $root 'runtime-terminal.json'),'{"cleanup_confirmed":true,"result":1}')
 $v=Exchange DRAIN;Check ($v.runtime_terminal.cleanup_confirmed -ceq $true -and $v.modules_idle -ceq $true) 'Drain report not preserved'
 $v=Exchange IDENTITY;Check ($v.target_line -ceq '') 'Fixture invented a live USB identity'
 $v=Exchange CLOSE;Check ($v.closed -ceq $true) 'Channel close not acknowledged';Check ($p.WaitForExit(3000)) 'Channel process leaked';Check ($p.ExitCode -eq 0) ('Fixture channel exit='+$p.ExitCode+' '+$err.Result)
 foreach($case in 'silence','eof','sequence','unknown','expired','live-path','fixture-flag'){
  $dir=Join-Path $root ('tbs-channel-fixture-'+$case);New-Item -ItemType Directory -Path $dir|Out-Null;$mapped=$posix+'/tbs-channel-fixture-'+$case
  $start.ArgumentList.Clear();$flag=$(if($case -eq 'live-path'){'0'}elseif($case -eq 'fixture-flag'){'2'}else{'1'});$expires=$(if($case -eq 'expired'){[DateTimeOffset]::UtcNow.AddSeconds(-1).ToUnixTimeMilliseconds()}else{[DateTimeOffset]::UtcNow.AddSeconds(10).ToUnixTimeMilliseconds()})
  foreach($a in @($scriptPath,$mapped,'SYNTHETIC_BOOT',[string]$expires,$flag)){$start.ArgumentList.Add($a)}
  $p=[Diagnostics.Process]::new();$p.StartInfo=$start;[void]$p.Start();$p.StandardInput.NewLine="`n";$err=$p.StandardError.ReadToEndAsync();$readyLine=$p.StandardOutput.ReadLineAsync();Check ($readyLine.Wait(5000)) ('Case startup hung: '+$case)
  if($case -in 'expired','live-path','fixture-flag'){Check ($null -eq $readyLine.Result) ('Invalid admission became ready: '+$case)}else{
   Check ($null -ne $readyLine.Result) ('Valid fixture not ready: '+$case)
   if($case -eq 'eof'){$p.StandardInput.Close()};if($case -eq 'sequence'){$p.StandardInput.WriteLine('2 PULSE');$p.StandardInput.Flush()};if($case -eq 'unknown'){$p.StandardInput.WriteLine('1 RUN_ARBITRARY');$p.StandardInput.Flush()}
  }
  Check ($p.WaitForExit(3000)) ('Guard failed to terminate: '+$case);Check ($p.ExitCode -ne 0) ('Guard silently passed: '+$case)
  if($case -in 'silence','eof','sequence','unknown'){Check ((Test-Path (Join-Path $dir STOP_RUNTIME)) -and (Test-Path (Join-Path $dir STOP_OBSERVER))) ('Guard did not request both STOPs: '+$case)}
 }
 foreach($fault in 'wrong-sequence','string-sequence','reply-timeout'){
  $start.ArgumentList.Clear();$body=$(if($fault -eq 'wrong-sequence'){'IFS= read -r line; printf ''{"sequence":99}\n''; cat > /dev/null'}elseif($fault -eq 'string-sequence'){'IFS= read -r line; printf ''{"sequence":"1"}\n''; cat > /dev/null'}else{'IFS= read -r line; sleep .3; printf ''{"sequence":1}\n''; cat > /dev/null'})
  foreach($a in @('-c',$body)){$start.ArgumentList.Add($a)};$p=[Diagnostics.Process]::new();$p.StartInfo=$start;[void]$p.Start();$p.StandardInput.NewLine="`n";$err=$p.StandardError.ReadToEndAsync();$channel=[pscustomobject]@{Process=$p;Sequence=0};$refused=$false
  try{$null=Channel-Exchange PULSE $(if($fault -eq 'reply-timeout'){50}else{1500})}catch{$refused=$true}
  Check ($refused -and $channel.InputClosed -ceq $true) ('Host did not close input after invalid reply: '+$fault);Check ($p.WaitForExit(3000)) ('Invalid-reply fixture leaked: '+$fault)
  $again=$false;try{$null=Channel-Exchange PULSE}catch{$again=$_.Exception.Message -ceq 'PERSISTENT_GUEST_CHANNEL_UNAVAILABLE'};Check $again 'Faulted channel admitted another reply reader'
 }
 "PASS $checks persistent channel process/clock/STOP/admission checks; synthetic paths only, no SSH/USB."
}catch{
 Write-Host ('Fixture failed: stage='+$stage+', last_command='+$lastCommand+', checks='+$checks)
 if($null -ne $p -and $p.HasExited){Write-Host ('Exit='+$p.ExitCode);if($null -ne $err -and $err.IsCompleted){Write-Host $err.Result}}
 throw
}finally{if($null -ne $p -and -not $p.HasExited){$p.StandardInput.Close();if(-not $p.WaitForExit(3000)){Stop-Process -Id $p.Id -Force}};Remove-Item -LiteralPath $root -Recurse -Force}
