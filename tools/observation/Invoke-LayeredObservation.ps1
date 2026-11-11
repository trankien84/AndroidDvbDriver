# SPDX-License-Identifier: GPL-2.0-only
#Requires -Version 7.5
[CmdletBinding(SupportsShouldProcess=$true,ConfirmImpact='High')]
param(
 [Parameter(Mandatory)][ValidatePattern('^phase31-layered-[0-9]{8}-[0-9]{6}-[a-f0-9]{8}$')][string]$RunId,
 [Parameter(Mandatory)][string]$GrantPath,
 [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$GrantSha256,
 [Parameter(Mandatory)][string]$CaptureDevice,
 [Parameter(Mandatory)][string]$ExpectedRootPath,
 [Parameter(Mandatory)][string]$FirmwarePath,
 [string]$VBoxPath='C:\Program Files\Oracle\VirtualBox\VBoxManage.exe',
 [string]$UsbPcapPath='C:\Program Files\USBPcap\USBPcapCMD.exe'
)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'LayeredObservation.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'WindowsObserver.psm1') -Force
$root=Join-Path D:\TSDuck\logs $RunId;$traces=Join-Path D:\TSDuck\traces $RunId;$guest='/root/'+$RunId
$grant=Assert-TbsObservationGrant $GrantPath $GrantSha256 $RunId
$startupOnly=$grant.PSObject.Properties['startup_diagnostic_only'] -and $grant.startup_diagnostic_only
if($grant.capture_device -cne $CaptureDevice -or $grant.expected_root_path -cne $ExpectedRootPath){throw 'CAPTURE_SCOPE_CHANGED_FROM_GRANT'}
$sourceManifest=Join-Path $root 'source-manifest.json'
if((Get-FileHash -LiteralPath $sourceManifest -Algorithm SHA256).Hash.ToLowerInvariant() -cne $grant.source_manifest_sha256){throw 'SOURCE_MANIFEST_CHANGED'}
foreach($item in (Get-Content -LiteralPath $sourceManifest -Raw|ConvertFrom-Json)){
 if($item.file -cnotmatch '^[A-Za-z0-9_.-]+\.(ps1|psm1|sh|cs|cjs)$' -or (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $item.file) -Algorithm SHA256).Hash.ToLowerInvariant() -cne $item.sha256){throw 'REVIEWED_OBSERVER_SOURCE_CHANGED'}
}
$action=$(if($startupOnly){'Startup-only observers with temporary VM filter and usbmon; no firmware staging or runtime'}else{'One scoped layered USB observation with temporary VM filter, usbmon, one cold firmware upload and bounded M late receive'})
if(-not $PSCmdlet.ShouldProcess($RunId,$action)){return}
if(-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'APPROVED_ELEVATED_HELPER_REQUIRED'}
if(-not(Test-Path -LiteralPath $root -PathType Container) -or -not(Test-Path -LiteralPath $traces -PathType Container)){throw 'NEW_RUN_DIRECTORIES_REQUIRED'}
$ssh=(Get-Command ssh.exe -CommandType Application -ErrorAction Stop).Source;$scp=(Get-Command scp.exe -CommandType Application -ErrorAction Stop).Source;$node=(Get-Command node.exe -CommandType Application -ErrorAction Stop).Source
$watch=[Diagnostics.Stopwatch]::StartNew();$state=[ordered]@{run_id=$RunId;protocol='TBS5530-LAYERED-RESULT/v1';state='ADMISSION';reason='NONE';startup_diagnostic_only=[bool]$startupOnly;diagnostics_collected=$false;firmware_verified=$false;operational_attempted=$false;cleanup_confirmed=$false;measurement_pass=$false;phase=@();filter_created=$false;usbmon_created=$false;firmware_staged=$false}
$windows=$null;$guestObserver=$null;$runtime=$null;$filterName=$RunId;$filterCreated=$false;$usbmonCreated=$false;$firmwareStaged=$false;$guestCreated=$false;$controlSeq=0;$identity=$null;$physical=$null;$vboxFault=$false;$channel=$null
function Save-State {Write-TbsObservationJson (Join-Path $root 'live-state.json') $state}
function Show-LocalArmNotice {
 if(-not ($grant.PSObject.Properties['local_arm_notice'] -and $grant.local_arm_notice -ceq $true)){return}
 try{[Console]::Title='TBS5530 - CẮM NGAY - recorder đã sẵn sàng';[Console]::WriteLine("`n*** CẮM NGAY TBS5530 VÀO ĐÚNG CỔNG WINDOWS CŨ ***`nKhông cắm USB khác. Giữ nguyên kết nối đến khi chương trình dừng.`n");[Console]::Beep(1000,250);[Console]::Beep(1400,250)}catch{throw 'LOCAL_ARM_NOTICE_UNAVAILABLE'}
}
function Windows-Status($Observer){$value=Get-TbsWindowsObserverStatus -Observer $Observer;if($value -is [string]){$value=$value|ConvertFrom-Json};return $value}
function Guest-Health($Response){$value=$Response.stdout|ConvertFrom-Json;if($null -eq $value.health){return $null};$h=$value.health;if($h.alive -isnot [bool] -or $h.ready -isnot [bool]){throw 'GUEST_OBSERVER_HEALTH_INVALID'};if(-not $h.alive){return $h};if($h.uptime_ms -isnot [long] -and $h.uptime_ms -isnot [int] -or $value.guest_now_ms -isnot [long] -and $value.guest_now_ms -isnot [int] -or $value.guest_now_ms -lt $h.uptime_ms -or ($value.guest_now_ms-$h.uptime_ms) -ge 2000){throw 'GUEST_OBSERVER_HEALTH_STALE'};return $h}
function Require-Grant {$null=Assert-TbsObservationGrant $GrantPath $GrantSha256 $RunId;if((Get-FileHash -LiteralPath $sourceManifest -Algorithm SHA256).Hash.ToLowerInvariant() -cne $grant.source_manifest_sha256){throw 'SOURCE_MANIFEST_CHANGED'};foreach($item in (Get-Content -LiteralPath $sourceManifest -Raw|ConvertFrom-Json)){if((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $item.file) -Algorithm SHA256).Hash.ToLowerInvariant() -cne $item.sha256){throw 'REVIEWED_OBSERVER_SOURCE_CHANGED'}};if(Test-Path (Join-Path $root 'STOP')){throw 'OPERATOR_STOP'};if($watch.ElapsedMilliseconds -ge 300000){throw 'ROOT_SESSION_DEADLINE'}}
function Remote([string]$Command,[string]$Label,[switch]$AllowFailure){$script:controlSeq++;$prefix=Join-Path $root ('ssh-'+$script:controlSeq+'-'+$Label);$connection=10;$keepalive=3;$timeout=15000;Invoke-TbsObservationCommand $ssh @('-n','-T','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o',('ConnectTimeout='+$connection),'-o',('ServerAliveInterval='+$keepalive),'-o','ServerAliveCountMax=1','ubuntu-VMS',$Command) $timeout $prefix -AllowFailure:$AllowFailure}
function VBox([string[]]$Arguments,[string]$Label){$script:controlSeq++;try{Invoke-TbsObservationCommand $VBoxPath $Arguments 10000 (Join-Path $root ('vbox-'+$script:controlSeq+'-'+$Label))}catch{$script:vboxFault=$true;throw}}
function Stage([string]$Local,[string]$Remote){Require-Grant;$script:controlSeq++;$hash=(Get-FileHash -LiteralPath $Local -Algorithm SHA256).Hash.ToLowerInvariant();$null=Invoke-TbsObservationCommand $scp @('-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=3',$Local,('ubuntu-VMS:'+$Remote)) 10000 (Join-Path $root ('scp-'+$script:controlSeq));$null=Remote "test `"`$(sha256sum '$Remote' | cut -d' ' -f1)`" = '$hash'" 'stage-hash'}
function Start-RemoteProcess([string]$Command,[string]$Prefix){
 $si=[Diagnostics.ProcessStartInfo]::new();$si.FileName=$ssh;$si.UseShellExecute=$false;$si.CreateNoWindow=$true;$si.RedirectStandardOutput=$true;$si.RedirectStandardError=$true
 foreach($a in @('-n','-T','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=10','-o','ServerAliveInterval=3','-o','ServerAliveCountMax=1','ubuntu-VMS',$Command)){$si.ArgumentList.Add($a)}
 $p=[Diagnostics.Process]::new();$p.StartInfo=$si;if(-not $p.Start()){throw 'SSH_WORKER_START_FAILED'}
 [pscustomobject]@{Process=$p;Out=$p.StandardOutput.ReadToEndAsync();Err=$p.StandardError.ReadToEndAsync();Prefix=$Prefix}
}
function Finish-RemoteProcess($Worker,[int]$WaitMs=15000){if($null -eq $Worker){return};if(-not $Worker.Process.WaitForExit($WaitMs)){throw 'REMOTE_WORKER_NOT_TERMINATED_NO_FORCE_CLOSE'};[IO.File]::WriteAllText($Worker.Prefix+'.stdout.txt',$Worker.Out.GetAwaiter().GetResult());[IO.File]::WriteAllText($Worker.Prefix+'.stderr.txt',$Worker.Err.GetAwaiter().GetResult());$Worker.Process.ExitCode}
function Parse-GuestIdentity([string]$Text){
 $rows=@($Text -split '\r?\n'|Where-Object{$_ -match '^TARGET\|'});if($rows.Count -eq 0){return $null};if($rows.Count -ne 1){throw 'AMBIGUOUS_GUEST_TARGETS'};$p=$rows[0] -split '\|';if($p.Count -ne 7 -or $p[1] -cnotmatch '^[0-9]+-[0-9]+(\.[0-9]+)*$' -or $p[2] -notmatch '^\d+$' -or $p[3] -notmatch '^\d+$' -or $p[4] -cne '480' -or $p[5] -cnotmatch '^[a-f0-9]{64}$' -or $p[6] -cne 'UNBOUND'){throw 'GUEST_TARGET_ADMISSION_MISMATCH'}
 [pscustomobject]@{path=$p[1];bus=[int]$p[2];device=[int]$p[3];hash=$p[5]}
}
function Get-GuestIdentity {
 $s='for p in /sys/bus/usb/devices/*; do if test -f "$p/idVendor" && test "$(cat "$p/idVendor")" = 734c && test "$(cat "$p/idProduct")" = 5530; then bound=UNBOUND; if test -e "$p:1.0/driver" || test -L "$p:1.0/driver"; then bound=BOUND; fi; printf "TARGET|%s|%s|%s|%s|%s|%s\n" "${p##*/}" "$(cat "$p/busnum")" "$(cat "$p/devnum")" "$(cat "$p/speed")" "$(sha256sum "$p/descriptors" | cut -d" " -f1)" "$bound"; fi; done'
 Parse-GuestIdentity ((Remote $s 'identity').stdout)
}
function Channel-Exchange([string]$Command,[int]$WaitMs=1500){
 if($null -eq $script:channel -or $script:channel.Process.HasExited -or ($script:channel.PSObject.Properties['InputClosed'] -and $script:channel.InputClosed)){throw 'PERSISTENT_GUEST_CHANNEL_UNAVAILABLE'}
 $script:channel.Sequence++;$seq=$script:channel.Sequence
 try{$script:channel.Process.StandardInput.WriteLine("$seq $Command");$script:channel.Process.StandardInput.Flush();$task=$script:channel.Process.StandardOutput.ReadLineAsync();if(-not $task.Wait($WaitMs)){throw 'PERSISTENT_GUEST_CHANNEL_DEADLINE'};$line=$task.GetAwaiter().GetResult();if(-not $line){throw 'PERSISTENT_GUEST_CHANNEL_CLOSED'};$data=$line|ConvertFrom-Json;if($data.sequence -isnot [int] -and $data.sequence -isnot [long] -or $data.sequence -ne $seq){throw 'PERSISTENT_GUEST_CHANNEL_SEQUENCE_MISMATCH'};[pscustomobject]@{stdout=$line;data=$data}}
 catch{$script:channel|Add-Member -NotePropertyName InputClosed -NotePropertyValue $true -Force;$script:channel.Process.StandardInput.Close();throw}
}
function Start-GuestChannel([string]$Phase){
 $si=[Diagnostics.ProcessStartInfo]::new();$si.FileName=$ssh;$si.UseShellExecute=$false;$si.CreateNoWindow=$true;$si.RedirectStandardInput=$true;$si.RedirectStandardOutput=$true;$si.RedirectStandardError=$true
 $deadline=[DateTimeOffset]::Parse($grant.expires_utc).ToUnixTimeMilliseconds();$cmd="bash '$guest/GuestChannel.sh' '$guest/$Phase' '$($grant.guest_boot_id)' '$deadline' 0"
 foreach($a in @('-T','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=10','-o','ServerAliveInterval=3','-o','ServerAliveCountMax=1','ubuntu-VMS',$cmd)){$si.ArgumentList.Add($a)}
 $p=[Diagnostics.Process]::new();$p.StartInfo=$si;if(-not $p.Start()){throw 'GUEST_CHANNEL_START_FAILED'};$p.StandardInput.NewLine="`n";$script:channel=[pscustomobject]@{Process=$p;Sequence=0;Err=$p.StandardError.ReadToEndAsync();Phase=$Phase}
 $task=$p.StandardOutput.ReadLineAsync();if(-not $task.Wait(15000)){throw 'GUEST_CHANNEL_STARTUP_DEADLINE'};$ready=$task.GetAwaiter().GetResult()|ConvertFrom-Json;if($ready.protocol -cne 'TBS5530-GUEST-CHANNEL/v1' -or $ready.ready -cne $true -or $ready.fixture -ne 0){throw 'GUEST_CHANNEL_INVALID_READY'}
}
function Close-GuestChannel {
 if($null -eq $script:channel){return};if(-not $script:channel.Process.HasExited){if(-not ($script:channel.PSObject.Properties['InputClosed'] -and $script:channel.InputClosed)){$null=Channel-Exchange 'CLOSE'};if(-not $script:channel.Process.WaitForExit(3000)){throw 'GUEST_CHANNEL_NOT_TERMINATED'}}
 [IO.File]::WriteAllText((Join-Path $root ($script:channel.Phase+'-channel.stderr.txt')),$script:channel.Err.GetAwaiter().GetResult())
}
function Set-Heartbeat([string]$Phase,[switch]$StopRuntime,[switch]$StopObserver){
 if($script:channel.Phase -cne $Phase){throw 'GUEST_CHANNEL_WRONG_PHASE'};$command='PULSE';if($StopRuntime){$command='STOP_RUNTIME'};if($StopObserver){$command='STOP_OBSERVER'};Channel-Exchange $command
}
function New-ObserverPermit([string]$Phase,$Target,[switch]$NewDevices){
 Require-Grant;$directory=Join-Path $root $Phase;if(-not(Test-Path $directory)){New-Item -ItemType Directory -Path $directory|Out-Null}
 $winPath=Join-Path $directory 'windows-permit.json';$winOut=Join-Path $traces ($Phase+'-windows');$expiry=([DateTimeOffset]::Parse($grant.expires_utc)).UtcDateTime.ToString('o')
 $permit=[ordered]@{protocol='TBS5530-WINDOWS-OBSERVER/v1';run_id=$RunId;phase=$Phase;approved=$true;fixture=$false;root_grant_sha256=$GrantSha256;expires_utc=$expiry;capture_device=$CaptureDevice;root_path=$ExpectedRootPath;device_address=$(if($NewDevices){0}else{$Target.device_address});capture_mode=$(if($NewDevices){'NewDevices'}else{'Address'});new_devices_only_on_selected_hub=$NewDevices.IsPresent;usbpcap_path=$UsbPcapPath;output_directory=$winOut;maximum_ms=90000;maximum_bytes=67108864;snaplen=131072;bufferlen=1048576}
 if(-not $NewDevices){$permit.Remove('root_path');$permit.Remove('new_devices_only_on_selected_hub')}
 Write-TbsObservationJson $winPath $permit -CreateNew;$hash=(Get-FileHash $winPath -Algorithm SHA256).Hash.ToLowerInvariant();$script:windows=Start-TbsWindowsObserver -PermitPath $winPath -PermitSha256 $hash
 $limit=[Diagnostics.Stopwatch]::StartNew();do{Require-Grant;$s=Windows-Status $script:windows;if($null -ne $s -and $s.ready -ceq $true -and $s.alive -ceq $true){return};if($script:windows.Process.HasExited){throw 'WINDOWS_OBSERVER_EXITED_BEFORE_READY'};Start-Sleep -Milliseconds 80}while($limit.ElapsedMilliseconds -lt 10000);throw 'WINDOWS_OBSERVER_NOT_READY'
}
function Start-GuestObserver([string]$Phase,$Target){
 Require-Grant;$d="$guest/$Phase";$permit=@('protocol=TBS5530-GUEST-OBSERVER/v1',"run_id=$RunId","phase=$Phase",'fixture=0','approved=1',"boot_id=$($grant.guest_boot_id)","busnum=$($Target.bus)","devnum=$($Target.device)","usb_path=$($Target.path)","descriptor_sha256=$($Target.hash)",('deadline_epoch_ms='+[DateTimeOffset]::Parse($grant.expires_utc).ToUnixTimeMilliseconds()),'maximum_ms=90000','maximum_bytes=67108864','snaplen=131072',"root_grant_sha256=$GrantSha256") -join "`n"
 $local=Join-Path (Join-Path $root $Phase) 'guest-observer.permit';[IO.File]::WriteAllText($local,$permit+"`n",[Text.UTF8Encoding]::new($false));Stage $local "$d/observer.permit";$hash=(Get-FileHash $local -Algorithm SHA256).Hash.ToLowerInvariant();Start-GuestChannel $Phase;$null=Set-Heartbeat $Phase
 $script:guestObserver=Start-RemoteProcess "bash '$guest/GuestObserver.sh' '$d/observer.permit' '$hash'" (Join-Path $root ($Phase+'-observer'))
 $wait=[Diagnostics.Stopwatch]::StartNew();do{Require-Grant;$h=Set-Heartbeat $Phase;$v=Guest-Health $h;if($null -ne $v -and $v.alive -ceq $true -and $v.ready -ceq $true){return};if($script:guestObserver.Process.HasExited){throw 'GUEST_OBSERVER_EXITED_BEFORE_READY'};Start-Sleep -Milliseconds 80}while($wait.ElapsedMilliseconds -lt 10000);throw 'GUEST_OBSERVER_NOT_READY'
}
function Make-RuntimePermit([string]$Phase,$Target){
 if($grant.allow_firmware_once -cne $true -or $grant.allow_terrestrial_receive -cne $true){throw 'RUNTIME_DENIED_BY_STARTUP_ONLY_SCOPE'}
 $values=[ordered]@{protocol='TBS5530-MEASURED-RUNTIME/v1';run_id=$RunId;phase=$Phase;approved=1;boot_id=$grant.guest_boot_id;usb_path=$Target.path;busnum=$Target.bus;devnum=$Target.device;descriptor_sha256=$Target.hash;physical_epoch=$script:physical.epoch;root_grant_sha256=$GrantSha256;deadline_epoch_ms=[DateTimeOffset]::Parse($grant.expires_utc).ToUnixTimeMilliseconds();module_root='/root/phase31-original-20261109-234104-c6017f83';board_sha256=$grant.board_sha256;sony_sha256='f7aa958f765bf1b36fd2f504d39daf6d7a31d0b69eb303e9a9f2c791a57f919a';alp_sha256='535ccc3d981f349a829fc1be885456b705ae56c65292439b6dc9bcf6a93aabe0';receiver_sha256='04f57dd49fbe279e732ef6a1d5d2187d978d3df48c42d777de9f01fa95269550';firmware_verified=$(if($Phase -eq 'operational'){1}else{0})}
 $text=($values.GetEnumerator()|ForEach-Object{$_.Key+'='+$_.Value}) -join "`n";$file=Join-Path (Join-Path $root $Phase) 'runtime.permit';[IO.File]::WriteAllText($file,$text+"`n",[Text.UTF8Encoding]::new($false));Stage $file "$guest/$Phase/runtime.permit";(Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Run-Phase([string]$Phase,$Target,[string]$RuntimeHash){
 if($grant.allow_firmware_once -cne $true -or $grant.allow_terrestrial_receive -cne $true){throw 'RUNTIME_DENIED_BY_STARTUP_ONLY_SCOPE'}
 if($RuntimeHash -cnotmatch '^[a-f0-9]{64}$'){throw 'PRESTAGED_RUNTIME_PERMIT_REQUIRED'};$d="$guest/$Phase";$directory=Join-Path $root $Phase;$ctx=@{hash=$runtimeHash;target=$Target;phase=$Phase;windows=$script:windows;guestObserver=$script:guestObserver;runtime=$null}
 $port={param($event,$phase)
  switch($event){
   admit {Require-Grant}
   observers_ready {$w=Windows-Status $ctx.windows;$h=Set-Heartbeat $phase;$v=Guest-Health $h;if($w.ready -cne $true -or $w.alive -cne $true -or $v.alive -cne $true -or $v.ready -cne $true){throw 'OBSERVERS_NOT_READY'}}
   start_runtime {$ctx.runtime=Start-RemoteProcess "timeout -s TERM 70s bash '$guest/GuestMeasuredRun.sh' '$guest/$phase/runtime.permit' '$($ctx.hash)'" (Join-Path $root ($phase+'-runtime'))}
   pump {Require-Grant;$w=Windows-Status $ctx.windows;$h=Set-Heartbeat $phase;$v=Guest-Health $h;$alive=$w.ready -ceq $true -and $w.alive -ceq $true -and $null -ne $v -and $v.alive -ceq $true -and $v.ready -ceq $true -and -not $ctx.guestObserver.Process.HasExited;$coldGone=$false;if($phase -eq 'cold' -and -not $alive){$fresh=Parse-GuestIdentity ((Channel-Exchange 'IDENTITY').data.target_line);$coldGone=($null -eq $fresh -or $fresh.hash -ceq '22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5') -and $null -ne $v -and $v.reason -ceq 'TARGET_GONE' -and $w.ready -ceq $true -and $w.alive -ceq $true};[pscustomobject]@{observers_ok=[bool]$alive;runtime_exited=[bool]$ctx.runtime.Process.HasExited;expected_cold_transition=[bool]$coldGone}}
   runtime_result {$code=Finish-RemoteProcess $ctx.runtime 1000;$r=(Channel-Exchange 'RESULT').data.runtime_terminal;if($null -eq $r){throw 'RUNTIME_TERMINAL_MISSING'};[pscustomobject]@{exit=$code;report=$r}}
   stop_runtime {$null=Set-Heartbeat $phase -StopRuntime;if($null -ne $ctx.runtime){$wait=[Diagnostics.Stopwatch]::StartNew();while(-not $ctx.runtime.Process.HasExited -and $wait.ElapsedMilliseconds -lt 15000){$null=Set-Heartbeat $phase;Start-Sleep -Milliseconds 80};$null=Finish-RemoteProcess $ctx.runtime 1000}}
   drain {$data=(Channel-Exchange 'DRAIN').data;$t=$data.runtime_terminal;$null -ne $t -and $t.cleanup_confirmed -is [bool] -and $t.cleanup_confirmed -and $data.modules_idle -ceq $true}
   stop_observers {
    $null=Set-Heartbeat $phase -StopObserver
    Close-GuestChannel
    if($null -ne $ctx.guestObserver){$null=Finish-RemoteProcess $ctx.guestObserver 15000}
    if($null -ne $ctx.windows){Stop-TbsWindowsObserver -Observer $ctx.windows -WaitSeconds 10|Out-Null}
    $wt=Get-Content (Join-Path $ctx.windows.Directory 'terminal.json') -Raw|ConvertFrom-Json
    if($wt.cleanup_confirmed -isnot [bool] -or -not $wt.cleanup_confirmed -or $wt.reason -cne 'STOP_REQUESTED'){throw 'HOST_OBSERVER_TERMINAL_INVALID'}
    $gt=(Remote "cat '$guest/$phase/observer/terminal.json'" 'observer-terminal').stdout|ConvertFrom-Json
    if($gt.cleanup_confirmed -isnot [bool] -or -not $gt.cleanup_confirmed -or $gt.reason -notin $(if($phase -ceq 'cold'){@('STOP_REQUESTED','TARGET_GONE')}else{@('STOP_REQUESTED')})){throw 'GUEST_OBSERVER_TERMINAL_INVALID'}
    if($gt.evidence_layer -ceq 'SYNTHETIC_FIXTURE' -or $wt.evidence_layer -ceq 'SYNTHETIC_FIXTURE'){throw 'FIXTURE_EVIDENCE_CANNOT_AUTHORIZE_LIVE_HANDOFF'}
   }
   collect {$null=Invoke-TbsObservationCommand $scp @('-r','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=3',('ubuntu-VMS:'+$guest+'/'+$phase),(Join-Path $directory 'guest-evidence')) 20000 (Join-Path $directory 'collect')}
  }
 }
 $r=Invoke-TbsLayeredPhase $Phase $directory $port;$script:runtime=$ctx.runtime;$script:state.phase+=@($r);Save-State;if(-not $r.cleanup_confirmed){throw 'PHASE_DRAIN_NOT_CONFIRMED'};if($r.reason -cne 'RUNTIME_FINISHED'){throw ('PHASE_FAILED_'+$r.reason)};return $r
}
try {
 Save-State;Require-Grant
 $vm=(VBox @('showvminfo',$grant.vm_uuid,'--machinereadable') 'admission').stdout
 if($vm -notmatch '(?m)^VMState="running"' -or $vm -notmatch '(?m)^xhci="on"' -or $vm -notmatch '(?m)^ehci="off"' -or $vm -match '(?m)^USBFilterName'){throw 'VM_CONFIG_OR_EXISTING_FILTER_CONFLICT'}
 $pre=(Remote 'set -eu; id -u; uname -r; cat /proc/sys/kernel/random/boot_id; test ! -e /lib/firmware/dvb-usb-id5530.fw; for m in tbs5530 tbs5530_minimal tbs5530_ter_diag cxd2878 alp; do test ! -d /sys/module/$m; done; echo CLEAN' 'admission').stdout
 if($pre -notmatch '^0\r?\n6\.8\.0-149-generic\r?\n' -or -not $pre.Contains($grant.guest_boot_id) -or -not $pre.Contains('CLEAN')){throw 'GUEST_ADMISSION_FAILED'}
 if($null -ne (Get-GuestIdentity)){throw 'TUNER_MUST_BE_DISCONNECTED_BEFORE_ARM'}
 $roots=@(Get-TbsObservationUsbRoots -UsbPcapPath $UsbPcapPath);$rootMatch=@($roots|Where-Object{$_.capture_device -ceq $CaptureDevice -and $_.root_path -ceq $ExpectedRootPath});if($rootMatch.Count -ne 1){throw 'FRESH_CAPTURE_ROOT_MISMATCH'}
 if(@(Get-TbsObservationUsbTargets -UsbPcapPath $UsbPcapPath).Count -ne 0){throw 'WINDOWS_TARGET_ALREADY_PRESENT'}
 if((Get-FileHash -LiteralPath $FirmwarePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $grant.firmware_sha256 -or (Get-Item $FirmwarePath).Length -ne 8192){throw 'FIRMWARE_INPUT_MISMATCH'}
 $null=Remote "set -eu; test ! -e '$guest'; mkdir '$guest'; mkdir '$guest/cold' '$guest/operational'" 'create-run';$guestCreated=$true
 foreach($name in 'GuestObserver.sh','GuestMeasuredRun.sh','GuestChannel.sh'){$local=Join-Path $PSScriptRoot $name;if([IO.File]::ReadAllText($local).Contains("`r")){throw 'BASH_SOURCE_NOT_LF'};Stage $local "$guest/$name"}
 if(-not $startupOnly){
  Stage $FirmwarePath "$guest/firmware.bin";$firmwareStaged=$true;$state.firmware_staged=$true
  $null=Remote "set -eu; test `"`$(sha256sum '$guest/firmware.bin' | cut -d' ' -f1)`" = '$($grant.firmware_sha256)'; test ! -e /lib/firmware/dvb-usb-id5530.fw; chmod 600 '$guest/firmware.bin'; ln -- '$guest/firmware.bin' /lib/firmware/dvb-usb-id5530.fw" 'stage-firmware'
 }
 $prior=(Remote 'if test -d /sys/module/usbmon; then echo USBMON_PREEXISTING; else echo USBMON_ABSENT; fi' 'usbmon-before').stdout.Trim()
 if($prior -ceq 'USBMON_ABSENT'){$usbmonCreated=$true;$state.usbmon_created=$true;$null=Remote 'modprobe usbmon' 'load-scoped-usbmon'}elseif($prior -cne 'USBMON_PREEXISTING'){throw 'USBMON_PRIOR_STATE_UNKNOWN'}
 $mods=(Remote 'test -d /sys/module/usbmon && echo USBMON_LOADED' 'usbmon-check').stdout;if(-not $mods.Contains('USBMON_LOADED')){throw 'USBMON_NOT_AVAILABLE'}
 $filterCreated=$true;$state.filter_created=$true
 $null=VBox @('usbfilter','add','0',('--target='+$grant.vm_uuid),('--name='+$filterName),'--active=yes','--vendorid=734c','--productid=5530','--remote=no') 'filter-add'
 New-ObserverPermit 'cold' $null -NewDevices;$state.state='ARMED_WAIT_PHYSICAL';Save-State;Show-LocalArmNotice
 $receipt=Join-Path $root 'physical-confirmed.json';$attachWatch=[Diagnostics.Stopwatch]::StartNew()
 if((Get-FileHash -LiteralPath $receipt -Algorithm SHA256).Hash.ToLowerInvariant() -cne $grant.physical_receipt_sha256){throw 'PHYSICAL_RECEIPT_CHANGED'}
 do {Require-Grant;if($script:windows.Process.HasExited){throw 'COLD_RECORDER_EXITED_WHILE_WAITING'};if(Test-Path -LiteralPath $receipt){$physical=[IO.File]::ReadAllText($receipt)|ConvertFrom-Json -DateKind String;if($physical.run_id -cne $RunId -or $physical.power_off_ge_5s_led_off -isnot [bool] -or -not $physical.power_off_ge_5s_led_off -or $physical.epoch -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$' -or $physical.epoch -ceq '00000000-0000-0000-0000-000000000000' -or $physical.source -cne 'USER_CONFIRMED_POWER_OFF_AND_HELD_DISCONNECTED' -or $physical.requires_observed_new_attachment -cne $true){throw 'PHYSICAL_RECEIPT_MISMATCH'};$identity=Get-GuestIdentity;if($null -ne $identity){break}};Start-Sleep -Milliseconds 150}while($attachWatch.ElapsedMilliseconds -lt 45000)
 if($null -eq $identity -or $identity.hash -cne 'e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de'){throw 'FRESH_DEFAULT_ATTACHMENT_NOT_PROVEN'}
 Write-TbsObservationJson (Join-Path $root 'cold-identity-private.json') $identity -CreateNew
 Write-TbsObservationJson (Join-Path $root 'attach-observed-private.json') ([ordered]@{epoch=$physical.epoch;run_id=$RunId;actual_observed_utc=[DateTime]::UtcNow.ToString('o');identity=$identity;admission='ABSENT_BEFORE_ARM_FRESH_DEFAULT_AFTER_ARM'}) -CreateNew
 $pcap=Join-Path $script:windows.Directory 'capture.pcap';$admission=Join-Path $root 'cold-host-admission.json';$null=Invoke-TbsObservationCommand $node @((Join-Path $PSScriptRoot 'Verify-Capture.cjs'),'admission',$pcap,$admission) 10000 (Join-Path $root 'host-admission');$ap=Get-Content $admission -Raw|ConvertFrom-Json;if(@($ap.non_target_descriptors).Count -ne 0 -or @($ap.target_descriptors).Count -lt 1 -or @($ap.target_descriptors|Where-Object{$_.device -ne 0}).Count -ne 1){throw 'HOST_CAPTURE_TARGET_NOT_PROVEN'}
 if(-not $startupOnly){$runtimeHash=Make-RuntimePermit 'cold' $identity};Start-GuestObserver 'cold' $identity
 if($startupOnly){$state.state='FINISHED';$state.reason='STARTUP_ONLY_READY_NO_FIRMWARE_OR_RUNTIME'}else{
 $state.state='COLD_BOOTSTRAP';Save-State;$cold=Run-Phase 'cold' $identity $runtimeHash
 $proof=Join-Path $root 'firmware-verified.json';$null=Invoke-TbsObservationCommand $node @((Join-Path $PSScriptRoot 'Verify-Capture.cjs'),'firmware',$pcap,$proof) 10000 (Join-Path $root 'cold-proof');$state.firmware_verified=$true;Save-State;Stage $proof "$guest/firmware-verified.json"
 $null=Remote "set -eu; test /lib/firmware/dvb-usb-id5530.fw -ef '$guest/firmware.bin'; test `"`$(sha256sum /lib/firmware/dvb-usb-id5530.fw | cut -d' ' -f1)`" = '$($grant.firmware_sha256)'; rm -- /lib/firmware/dvb-usb-id5530.fw" 'remove-owned-firmware';$firmwareStaged=$false;$state.firmware_staged=$false
 $wait=[Diagnostics.Stopwatch]::StartNew();do{Require-Grant;$identity=Get-GuestIdentity;if($null -ne $identity -and $identity.hash -ceq '22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5'){break};Start-Sleep -Milliseconds 100}while($wait.ElapsedMilliseconds -lt 10000)
 if($null -eq $identity -or $identity.hash -cne '22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5'){throw 'OPERATIONAL_HANDOFF_NOT_PROVEN'}
 $targets=@(Get-TbsObservationUsbTargets -UsbPcapPath $UsbPcapPath|Where-Object{$_.capture_device -ceq $CaptureDevice});if($targets.Count -ne 1){throw 'FRESH_OPERATIONAL_HOST_ADDRESS_MISSING'}
 Write-TbsObservationJson (Join-Path $root 'operational-identity-private.json') $identity -CreateNew
 Write-TbsObservationJson (Join-Path $root 'operational-host-private.json') $targets[0] -CreateNew
 $windows=$null;$guestObserver=$null;$runtime=$null;$channel=$null;New-ObserverPermit 'operational' $targets[0];$runtimeHash=Make-RuntimePermit 'operational' $identity;Start-GuestObserver 'operational' $identity;$state.state='OPERATIONAL_RECEIVE';$state.operational_attempted=$true;Save-State;$null=Run-Phase 'operational' $identity $runtimeHash
 $state.state='FINISHED';$state.reason='BOUNDED_MEASUREMENT_RECORDED_NOT_TS_CERTIFICATION'
 }
} catch {$state.state='STOPPED';$state.reason=$_.Exception.Message}
finally {
 $safe=@($state.phase|Where-Object{$_.cleanup_confirmed -cne $true}).Count -eq 0
 if($null -ne $runtime -and -not $runtime.Process.HasExited){try{$null=Set-Heartbeat $(if($state.operational_attempted){'operational'}else{'cold'}) -StopRuntime;$null=Finish-RemoteProcess $runtime 15000}catch{$safe=$false}}
 if($null -ne $guestObserver -and -not $guestObserver.Process.HasExited){try{$null=Set-Heartbeat $(if($state.operational_attempted){'operational'}else{'cold'}) -StopObserver;$null=Finish-RemoteProcess $guestObserver 15000}catch{$safe=$false}}
 if($null -ne $windows){try{if(-not $windows.Process.HasExited){Stop-TbsWindowsObserver -Observer $windows -WaitSeconds 10|Out-Null};$wt=Get-Content (Join-Path $windows.Directory 'terminal.json') -Raw|ConvertFrom-Json;if($wt.cleanup_confirmed -cne $true){$safe=$false}}catch{$safe=$false}}
 if($null -ne $channel){try{Close-GuestChannel}catch{$safe=$false}}
 if($null -ne $guestObserver){try{$phase=$(if($state.operational_attempted){'operational'}else{'cold'});$gt=(Remote "cat '$guest/$phase/observer/terminal.json'" 'final-observer-terminal').stdout|ConvertFrom-Json;if($gt.cleanup_confirmed -cne $true){$safe=$false}}catch{$safe=$false}}
 if($guestCreated){try{$check=(Remote 'for m in tbs5530_minimal cxd2878 alp; do if test -d /sys/module/$m; then echo MODULE_REMAINS:$m; fi; done' 'cleanup-check').stdout;if($check -match 'MODULE_REMAINS'){$safe=$false}}catch{$safe=$false}}
 if($safe -and $firmwareStaged){try{$null=Remote "set -eu; if test -e /lib/firmware/dvb-usb-id5530.fw; then test /lib/firmware/dvb-usb-id5530.fw -ef '$guest/firmware.bin'; test `"`$(sha256sum /lib/firmware/dvb-usb-id5530.fw | cut -d' ' -f1)`" = '$($grant.firmware_sha256)'; rm -- /lib/firmware/dvb-usb-id5530.fw; fi" 'cleanup-owned-firmware';$state.firmware_staged=$false}catch{$safe=$false}}
 if($vboxFault -and $filterCreated){$safe=$false}
 if($safe -and $filterCreated){try{$info=(VBox @('showvminfo',$grant.vm_uuid,'--machinereadable') 'cleanup-filter-state').stdout;$m=[regex]::Matches($info,'(?m)^USBFilterName([0-9]+)="([^"]+)"');$own=@($m|Where-Object{$_.Groups[2].Value -ceq $filterName});if($own.Count -gt 1){throw 'OWNED_FILTER_IDENTITY_CHANGED'};if($own.Count -eq 1){$index=[int]$own[0].Groups[1].Value-1;$null=VBox @('usbfilter','remove',"$index",('--target='+$grant.vm_uuid)) 'remove-owned-filter'};$state.filter_created=$false}catch{$safe=$false}}
 if($safe -and $usbmonCreated){try{$null=Remote 'set -eu; test ! -d /sys/module/tbs5530_minimal; modprobe -r usbmon' 'remove-owned-usbmon';$state.usbmon_created=$false}catch{$safe=$false}}
 if($startupOnly -and $guestCreated -and $null -ne $guestObserver -and $guestObserver.Process.HasExited){try{$null=Invoke-TbsObservationCommand $scp @('-r','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=10',('ubuntu-VMS:'+$guest+'/cold'),(Join-Path $root 'startup-guest-evidence')) 25000 (Join-Path $root 'collect-startup-diagnostic');$state.diagnostics_collected=$true}catch{$state.reason+=';STARTUP_DIAGNOSTIC_COLLECTION_FAILED'}}
 $state.cleanup_confirmed=$safe;$state.elapsed_ms=$watch.ElapsedMilliseconds;Save-State
}
$state|ConvertTo-Json -Depth 24
if($state.state -cne 'FINISHED' -or -not $state.cleanup_confirmed){exit 1}
