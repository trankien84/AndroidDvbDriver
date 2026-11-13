# SPDX-License-Identifier: GPL-2.0-or-later
# Fake-only: override device transport before evaluating main. Never invokes ADB or hardware.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory,[Parameter(Mandatory)][string]$ReportDirectory)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$path=Join-Path $PSScriptRoot 'Invoke-TBS5530-B5Session.ps1'
$b3=Join-Path $PSScriptRoot 'Invoke-TBS5530-B3Session.ps1'
$runtime=Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
$repository=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors)
if ($errors.Count) { throw 'B5 parse failed' }
foreach ($function in @($ast.EndBlock.Statements|Where-Object {$_ -is [Management.Automation.Language.FunctionDefinitionAst]})) { Invoke-Expression $function.Extent.Text }
$ExpectedB3HelperSha256=(Get-FileHash -LiteralPath $b3 -Algorithm SHA256).Hash.ToLowerInvariant()
Import-B5SharedGuards $b3 $ExpectedB3HelperSha256
Set-B5Overrides
$root=Assert-B5PrivatePath ([IO.Path]::GetFullPath($OutputDirectory)) 'D:\TSDuck\logs'
$reportRoot=Assert-B5PrivatePath ([IO.Path]::GetFullPath($ReportDirectory)) 'D:\TSDuck\reports'
if ((Test-Path -LiteralPath $root) -or (Test-Path -LiteralPath $reportRoot)) { throw 'Fresh private fake evidence directories required' }
$null=New-Item -ItemType Directory -Path $root
$null=New-Item -ItemType Directory -Path $reportRoot
$checks=0
function Check([bool]$Condition,[string]$Name) { if (-not $Condition) { throw ('FAIL: '+$Name) }; $script:checks++ }
function Refuse([scriptblock]$Action,[string]$Name) { $caught=$false;try { & $Action|Out-Null } catch { $caught=$true };Check $caught $Name }
function HashFile([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
$epoch='11111111-1111-4111-8111-111111111111'
$epoch2='22222222-2222-4222-8222-222222222222'
$session='33333333-3333-4333-8333-333333333333'
$hash='a'*64
$zone=[TimeZoneInfo]::FindSystemTimeZoneById('SE Asia Standard Time')
$today=[TimeZoneInfo]::ConvertTime([DateTimeOffset]::UtcNow,$zone)
function Card([string]$Name='unit') {
    $budget=[guid]::NewGuid().ToString()
    $directory=Join-Path (Join-Path $root $Name) ('b5-budget-'+$budget)
    return [pscustomobject]@{schema='TBS5530-B5-CARD/v1';approval='OK B5';session_id=$session;budget_id=$budget;max_attempts=4;max_android=6;max_windows=2;
        budget_directory=$directory;session_directory=$directory;raw_directory=(Join-Path $root ($Name+'-raw'));report_directory=$reportRoot;
        live_date=$today.ToString('yyyy-MM-dd');time_zone_id='SE Asia Standard Time';utc_offset_minutes=420;
        duration_ms=30000;max_bytes=268435456;tuner_rssi=$false;rf_input='RF_IN';antenna='PASSIVE';antenna_dc=$false;
        attempts=@([pscustomobject]@{ordinal=1;physical_recovery_epoch=$epoch;allowed_receive_configs=@([pscustomobject]@{queue_depth=16;urb_bytes=16384},[pscustomobject]@{queue_depth=32;urb_bytes=16384})},
            [pscustomobject]@{ordinal=2;physical_recovery_epoch=$epoch2;queue_depth=32;urb_bytes=16384},
            [pscustomobject]@{ordinal=3;physical_recovery_epoch='44444444-4444-4444-8444-444444444444';queue_depth=16;urb_bytes=16384},
            [pscustomobject]@{ordinal=4;physical_recovery_epoch='55555555-5555-4555-8555-555555555555';queue_depth=32;urb_bytes=16384})}
}
$layout='OFF1:10000,ON1:30000,OFF2:10000,ON2:10000'
$FrontendExperiment='B5_K25_MANUAL_PLP0'
function BindCard([object]$C) {
    foreach ($attempt in $C.attempts) {
        $attempt|Add-Member frontend_experiment 'B5_K25_MANUAL_PLP0' -Force
        $attempt|Add-Member allow_k25_auto $true -Force
        $attempt|Add-Member golden_sha256 ('d'*64) -Force
        $attempt|Add-Member reload_plan_sha256 ('e'*64) -Force
        $attempt|Add-Member notes ([pscustomobject]@{separation='Cách xa điện thoại';cable='Cáp ngắn';accessories='Không phụ kiện'}) -Force
        $attempt|Add-Member allowed_receive_configs @([pscustomobject]@{queue_depth=16;urb_bytes=16384}) -Force
    }
    return $C
}
$originalCard=${function:Card}
function Card([string]$Name='unit') { return BindCard (& $originalCard $Name) }
$card=Card
Assert-B5Card $card @{} 1 $epoch 16 16384;Check $true 'valid B5 independent card'
foreach ($fault in @('approval','android7','windows3','depth32','bad-auto','notes','yesterday')) {
    $c=Card
    switch ($fault) {
        'approval' {$c.approval='OK B4'}
        'android7' {$c.max_android=7}
        'windows3' {$c.max_windows=3}
        'depth32' {$c.attempts[0].allowed_receive_configs[0].queue_depth=32}
        'bad-auto' {$c.attempts[0].frontend_experiment='B5_K25_AUTO'}
        'notes' {$c.attempts[0].notes.cable=''}
        'yesterday' {$c.live_date=$today.AddDays(-1).ToString('yyyy-MM-dd')}
    }
    Refuse {Assert-B5Card $c @{} 1 $epoch 16 16384} ('card '+$fault)
}
$six=Card 'six-budget';$six.max_attempts=6;$six.max_android=6
for($ordinal=5;$ordinal -le 6;$ordinal++) {
    $six.attempts+=,[pscustomobject]@{ordinal=$ordinal;golden_sha256=('d'*64);reload_plan_sha256=('e'*64);physical_recovery_epoch=[guid]::NewGuid().ToString();frontend_experiment='B5_K36_MANUAL_PLP0';allow_k25_auto=$false;
        allowed_receive_configs=@([pscustomobject]@{queue_depth=16;urb_bytes=16384});notes=[pscustomobject]@{separation='Xa';cable='Ngắn';accessories='Không'}}
}
Assert-B5Card $six @{} 6 $six.attempts[5].physical_recovery_epoch 16 16384;Check $true 'six Android and two Windows allowed'
Check ($six.attempts[0].frontend_experiment -cne $six.attempts[5].frontend_experiment) 'one card supports K25 and K36 with per-attempt plan pins'
$six.max_attempts=7;Refuse {Assert-B5Card $six @{} 6 $six.attempts[5].physical_recovery_epoch 16 16384} 'seventh Android cannot be added'
$six.max_attempts=6
$originalReview=${function:Read-B5Review}
function Read-B5Review {return [pscustomobject]@{sha256=('c'*64);result='B5_REVIEWED'}}
$ledgerPath=Join-Path $root 'six-budget.jsonl'
$ledgerStream=[IO.FileStream]::new($ledgerPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try {
    $prefix=Add-B5ManifestRecord $ledgerStream ([byte[]]@()) ([pscustomobject]@{event='HEADER';card_sha256=$hash;budget_id=$six.budget_id;max_attempts=6;max_windows=2})
    foreach($ordinal in 1..6) {
        $null=Assert-B5Ledger $prefix $hash $six $ordinal
        $prefix=Add-B5ManifestRecord $ledgerStream $prefix ([pscustomobject]@{event='CONSUME';ordinal=$ordinal;epoch=$six.attempts[$ordinal-1].physical_recovery_epoch;queue_depth=16;urb_bytes=16384;previous_offline_validation_sha256=('c'*64)})
        Refuse {Assert-B5Ledger $prefix $hash $six $ordinal} ('durable consume blocks replay '+$ordinal)
        $prefix=Add-B5ManifestRecord $ledgerStream $prefix ([pscustomobject]@{event='RECEIPT';ordinal=$ordinal;session_closed=$false;drained=$true;result='REVIEW_REQUIRED';queue_depth=16;urb_bytes=16384})
    }
    Refuse {Assert-B5Ledger $prefix $hash $six 7} 'budget exhausted after sixth receipt'
    $changed=$six.PSObject.Copy();$changed.session_id=[guid]::NewGuid().ToString()
    Refuse {Assert-B5Ledger $prefix $hash $changed 1} 'new UUID does not reset consumed budget'
    $tampered=$prefix.Clone();$tampered[10]=[byte]($tampered[10]-bxor 1)
    Refuse {Assert-B5Ledger $tampered $hash $six 7} 'tampered chain rejected'
}finally{$ledgerStream.Dispose();Set-Item Function:Read-B5Review $originalReview}
$adaptive=Card 'adaptive';$adaptive.max_attempts=3;$adaptive.attempts=$adaptive.attempts[0..2]
$adaptive.attempts[2]|Add-Member allowed_frontends @(
    [pscustomobject]@{frontend_experiment='B5_K25_MANUAL_PLP0';golden_sha256=('1'*64);reload_plan_sha256=('2'*64);allow_k25_auto=$true;frequency_hz=506000000;bandwidth_hz=8000000},
    [pscustomobject]@{frontend_experiment='B5_K36_MANUAL_PLP0';golden_sha256=('3'*64);reload_plan_sha256=('4'*64);allow_k25_auto=$false;frequency_hz=594000000;bandwidth_hz=8000000})
Assert-B5Card $adaptive @{} 3 $adaptive.attempts[2].physical_recovery_epoch 16 16384;Check $true 'three-ordinal card preapproves adaptive K25/K36 without budget expansion'
$choice=Select-B5Frontend $adaptive.attempts[2] 'B5_K25_MANUAL_PLP0' ('1'*64) ('2'*64);Check $choice.allow_k25_auto 'adaptive ordinal3 selects exact K25'
$choice=Select-B5Frontend $adaptive.attempts[2] 'B5_K36_MANUAL_PLP0' ('3'*64) ('4'*64);Check (-not $choice.allow_k25_auto) 'adaptive ordinal3 selects exact K36'
Refuse {Select-B5Frontend $adaptive.attempts[2] 'B5_K25_AUTO' ('1'*64) ('2'*64)} 'adaptive direct AUTO refused'
Refuse {Select-B5Frontend $adaptive.attempts[2] 'B5_K36_MANUAL_PLP0' ('1'*64) ('2'*64)} 'adaptive plan substitution refused'
$adaptive.attempts[2].allowed_frontends[0].frequency_hz=514000000
Refuse {Assert-B5Card $adaptive @{} 3 $adaptive.attempts[2].physical_recovery_epoch 16 16384} 'adaptive arbitrary frequency refused'
$adaptive.attempts[2].allowed_frontends[0].frequency_hz=506000000
$originalReview=${function:Read-B5Review}
function Read-B5Review {return [pscustomobject]@{sha256=('c'*64);result='B5_REVIEWED'}}
$adaptiveStream=[IO.FileStream]::new((Join-Path $root 'adaptive-budget.jsonl'),[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try{
    $prefix=Add-B5ManifestRecord $adaptiveStream ([byte[]]@()) ([pscustomobject]@{event='HEADER';card_sha256=$hash;budget_id=$adaptive.budget_id;max_attempts=3;max_windows=2})
    foreach($ordinal in 1..3){
        $null=Assert-B5Ledger $prefix $hash $adaptive $ordinal
        $prefix=Add-B5ManifestRecord $adaptiveStream $prefix ([pscustomobject]@{event='CONSUME';ordinal=$ordinal;epoch=$adaptive.attempts[$ordinal-1].physical_recovery_epoch;queue_depth=16;urb_bytes=16384;previous_offline_validation_sha256=('c'*64);frontend_experiment='B5_K36_MANUAL_PLP0';golden_sha256=('3'*64);reload_plan_sha256=('4'*64)})
        if($ordinal -eq 3){Refuse {Assert-B5Ledger $prefix $hash $adaptive 3} 'adaptive alternative cannot consume ordinal3 twice'}
        $prefix=Add-B5ManifestRecord $adaptiveStream $prefix ([pscustomobject]@{event='RECEIPT';ordinal=$ordinal;session_closed=$false;drained=$true;result='REVIEW_REQUIRED';queue_depth=16;urb_bytes=16384})
    }
    Refuse {Assert-B5Ledger $prefix $hash $adaptive 4} 'adaptive two choices do not create fourth ordinal'
}finally{$adaptiveStream.Dispose();Set-Item Function:Read-B5Review $originalReview}
Refuse {Assert-B5Card $adaptive @{} 4 $epoch 16 16384} 'adaptive card stays three ordinals'
$ExpectedModel='GM1900';$ExpectedApi=30;$ExpectedBootCount=47
$package='info.martinmarinov.dvbdriver';$firmwareSha='b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc'
$ExpectedFrontendGoldenSha256='75875b84fc3f959b35d436560ae19ccf7e52b890173f1f5e24984367f64fe228'
$ExpectedReloadPlanSha256='9add00e12a7fbea9aa4877451db5a28077c64872cd73e6eb91f04facf1fec650'
$ExpectedAppApkSha256=$hash;$B5QueueDepth=16;$B5UrbBytes=16384;$B5DurationMillis=30000;$B5MaxBytes=268435456;$B5Unused=$false
$card|Add-Member source_git_sha ('9'*40)
function Challenge {
    $header="TBS5530-FX2-RELOAD/v1`nHOST oneplus-gm1900-api30`nINITIAL_HISTORY_SHA256 b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb`n"
    $h=Get-B5Hash ([Text.Encoding]::UTF8.GetBytes($header))
    [pscustomobject]@{policy='TBS5530-AUTONOMOUS/v1';session=$session;launch_token=$epoch;control_plane_only=$false;live_ready=$true;created_elapsed_ms=1000;
        model='GM1900';api=30;boot_count=47;host_profile='oneplus-gm1900-api30';vid=29516;pid=21808;firmware_sha256=$firmwareSha;firmware_protocol='TBS_SOURCE_DUAL_RESET';
        ts_variant='NATIVE_USBFS_STREAM';frontend_experiment=$FrontendExperiment;golden_sha256=$ExpectedFrontendGoldenSha256;app_apk_sha256=$hash;
        control_mirror=$true;explicit_set_interface_alt0=$false;sony_ts_output_readback=$false;retry=0;max_upload_per_attachment=1;max_reload_per_physical_epoch=1;
        max_session_ms=300000;heartbeat_ms=2000;fx2_reload_option='B2_RELOAD_AFTER_SET_CONFIG';fx2_reload_plan_sha256=$ExpectedReloadPlanSha256;
        bootstrap_proof='SAME_SESSION_ORIGINAL132_APP_BOUNDARIES_DRAIN_REQUIRED';b3_queue_depth=$B5QueueDepth;b3_urb_bytes=16384;b3_max_bytes=268435456;b3_duration_ms=$B5DurationMillis;
        b5_enabled=$true;b5_rssi_sequence='WINDOWS_88_00_87_C0';b5_allow_k25_auto=($FrontendExperiment -ceq 'B5_K25_MANUAL_PLP0');b5_layout=$layout;b5_stale_prefix_bytes=2048;b5_monitor_period_ms=100;b5_monitor_jitter_ms=20;b5_rssi_samples_per_off=3;attachment_history_sha256=$null;fx2_reload_store_sha256=$h;
        fx2_reload_anchor_sha256=(Get-B5Hash ([Text.Encoding]::UTF8.GetBytes("TBS5530-FX2-RELOAD-ANCHOR/v1`n$h`n")));source_git_sha=$card.source_git_sha}
}
$v=Challenge
$card|Add-Member fx2_reload_store_sha256 $v.fx2_reload_store_sha256 -Force
$card|Add-Member fx2_reload_anchor_sha256 $v.fx2_reload_anchor_sha256 -Force
Assert-B5Challenge $v $epoch 1001;Check $true 'typed B5 challenge'
foreach ($field in @('model','api','b5_enabled','b5_rssi_sequence','b5_allow_k25_auto','b5_layout','b5_stale_prefix_bytes','b5_monitor_period_ms','b5_monitor_jitter_ms','b5_rssi_samples_per_off')) {
    $v=Challenge;$v.$field='changed';Refuse {Assert-B5Challenge $v $epoch 1001} ('challenge '+$field)
}
function Permission([string]$Token) {
    [pscustomobject]@{version=1;mode='PERMISSION_ONLY';preflight_token=$Token;state='GRANTED';reason='OS_PERMISSION_VERIFIED';vid=29516;pid=21808;
        created_elapsed_ms=1000;deadline_elapsed_ms=61000;elapsed_ms=1001;attachment_generation=1;device_identity_sha256=$hash}
}
Assert-B5PermissionReady (Permission $epoch) $epoch 1002;Check $true 'existing permission-only60s receipt accepted within B5 host30s'
$v=Permission $epoch;$v.elapsed_ms=30999
Assert-B5PermissionReady $v $epoch 30999;Check $true 'permission29999ms'
Refuse {Assert-B5PermissionReady $v $epoch 31000} 'permission30000ms refused'
$v=Permission $epoch;$v.elapsed_ms='1001'
Refuse {Assert-B5PermissionReady $v $epoch 1002} 'permission numeric string refused'
function Phases {
    $phases=@();$now=2000000000L;$i=0
    foreach($name in @('OFF1','ON1','OFF2','ON2')) {
        $duration=@(10,30,10,10)[$i]*1000000000L
        $phases+=,[pscustomobject]@{phase=$name;start_ns=$now;end_ns=($now+$duration);clock='CLOCK_MONOTONIC';bytes=$(if($name.StartsWith('ON')){$raw.Length}else{0});
            raw_file=$(if($name.StartsWith('ON')){'received-'+$name+'.ts'}else{$null});stale_prefix_bytes=$(if($name -ceq 'ON1'){2048}else{0});audit_run_id=($session+'-'+$name);drained=$true;quarantined=$false;stop_reason='DURATION'}
        $now+=$duration;$i++
    }
    return $phases
}
$raw=[byte[]](0..255)
function Report {
    [pscustomobject]@{cleanup='DRAIN_CONFIRMED';state='REPORT';result='STRUCTURE_CONFIRMED';terminal=$true;cleanup_path='RUNTIME_DRAIN_CONFIRMED';
        native_urb=$null;native_stream=$null;b5_phases=@(Phases);firmware=$null;
        fx2_reload=[pscustomobject]@{state='COMPLETE';attempted=132;completed=132;detach_ns=-1};last_errno=0;ts_bytes=32768;raw_capture='SYNCED_CLOSED';
        raw_received_bytes=32768;control_mirror='SYNCED_CLOSED';permission_outcome='AUTO_PERMISSION_HANDLED_BY_OS';pre_native_refusal=$false}
}
$r=Report
Check (Test-B5Drained $r) 'four phases typed drain'
Assert-B5PhaseReport $r -Complete;Check $true 'four planned durations'
foreach($fault in @('status75','status71','detach','uncertain','phase-drain','phase-quarantine','typed-drain','layout')) {
    $r=Report
    switch($fault) {
        'status75' {$r.last_errno=75}
        'status71' {$r.last_errno=71}
        'detach' {$r.fx2_reload.detach_ns=0}
        'uncertain' {$r.state='ERROR_UNCERTAIN'}
        'phase-drain' {$r.b5_phases[1].drained=$false}
        'phase-quarantine' {$r.b5_phases[3].quarantined=$true}
        'typed-drain' {$r.terminal='true'}
        'layout' {$r.b5_phases[2].phase='ON2'}
    }
    if($fault -ceq 'layout') {Refuse {Assert-B5PhaseReport $r -Complete} 'phase order'} else {Check (Test-B5SessionClosed $r) ('whole budget closed '+$fault)}
}
$r=Report;$paths=@(Get-B5ExportPaths $session)
Check ($paths -ccontains ('files/autonomous-v1-'+$session+'/received-ON1.ts')) 'ON1 raw export'
Check ($paths -ccontains ('files/autonomous-v1-'+$session+'/received-ON2.ts')) 'ON2 raw export'
$remote='files/autonomous-v1-'+$session+'/received-ON1.ts'
Assert-B5ExportCall @('exec-out','run-as',$package,'sh','-c',('head -c 268435457 '+$remote)) $r $session 'fake.ts';Check $true 'bounded binary export'
$r.b5_phases[1].drained=$false
Refuse {Assert-B5ExportCall @('shell','run-as',$package,'wc','-c',$remote) $r $session ''} 'no export before drain'

# A local shell stands in for adb/run-as, preserving the exact ProcessStartInfo argv.
$offlineShell='C:\Program Files\Git\usr\bin\sh.exe'
if (-not (Test-Path -LiteralPath $offlineShell -PathType Leaf)) { throw 'Offline Git shell required for exec-out argv regression' }
function Invoke-OfflineExecOutProbe([string[]]$DeviceArguments,[string]$Directory) {
    $info=[Diagnostics.ProcessStartInfo]::new($offlineShell)
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.WorkingDirectory=$Directory
    $info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach ($arg in @('-s','offline-fixture')+$DeviceArguments) { $info.ArgumentList.Add($arg) }
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info;$output=[IO.MemoryStream]::new()
    try {
        if (-not $process.Start()) { throw 'Offline exec-out probe start failed' }
        $stderr=$process.StandardError.ReadToEndAsync();$stdout=$process.StandardOutput.BaseStream.CopyToAsync($output)
        $stdin=@'
set -eu
[ "$#" -eq 7 ] || exit 64
[ "$1" = offline-fixture ] && [ "$2" = exec-out ] && [ "$3" = run-as ] || exit 64
[ "$4" = info.martinmarinov.dvbdriver ] && [ "$5" = sh ] && [ "$6" = -c ] || exit 64
PATH=/usr/bin:/bin
export PATH
exec /bin/sh -c "$7"
'@
        $process.StandardInput.Write($stdin.Replace("`r`n","`n")+"`n");$process.StandardInput.Close()
        if (-not $process.WaitForExit(10000)) { Stop-Process -Id $process.Id -Force;throw 'Offline exec-out probe timed out' }
        $null=$stdout.GetAwaiter().GetResult();$errorText=$stderr.GetAwaiter().GetResult()
        return [pscustomobject]@{exit_code=$process.ExitCode;arguments=@($info.ArgumentList);bytes=$output.Length;sha256=(Get-B5Hash ($output.ToArray()));stderr=$errorText}
    } finally { $output.Dispose();$process.Dispose() }
}
$probeRoot=Join-Path $root 'exec-out-argv'
$probeDirectory=Join-Path $probeRoot ('files\autonomous-v1-'+$session)
$null=New-Item -ItemType Directory -Path $probeDirectory -Force
$probeBytes=[byte[]](0..255)
[IO.File]::WriteAllBytes((Join-Path $probeDirectory 'report.json'),$probeBytes)
$probeRemote='files/autonomous-v1-'+$session+'/report.json'
$probeCommand='head -c 1048577 '+$probeRemote
$badProbe=Invoke-OfflineExecOutProbe @('exec-out','run-as',$package,'sh','-c',("'"+$probeCommand+"'")) $probeRoot
Check ($badProbe.exit_code -ne 0 -and $badProbe.bytes -eq 0 -and $badProbe.stderr.Contains('head -c 1048577')) 'exact argv reproduces quoted command treated as inaccessible executable'
$goodProbe=Invoke-OfflineExecOutProbe @('exec-out','run-as',$package,'sh','-c',$probeCommand) $probeRoot
Check ($goodProbe.exit_code -eq 0 -and $goodProbe.bytes -eq $probeBytes.Length -and $goodProbe.sha256 -ceq (Get-B5Hash $probeBytes)) 'exact argv unquoted command preserves binary output'
Check ($goodProbe.arguments.Count -eq 8 -and $goodProbe.arguments[7] -ceq $probeCommand -and
    $goodProbe.arguments[7] -notmatch '^[\x22\x27]|[\x22\x27]$') 'ProcessStartInfo ArgumentList has one raw command argument without literal quotes'
Write-B5NewJson (Join-Path $reportRoot 'exec-out-argv-probe.json') ([pscustomobject]@{offline_only=$true;device_commands=0;before=$badProbe;after=$goodProbe})

# Main is evaluated only after replacing every device transport with fake.
$head=(& git -C $repository rev-parse HEAD).Trim()
$sourceManifest=Join-Path $reportRoot 'source-manifest.json'
Write-B5NewJson $sourceManifest ([pscustomobject]@{source_git_sha=$head;files=@([pscustomobject]@{path='tools\Invoke-TBS5530-B5Session.ps1';sha256=(HashFile $path)})})
$main=$ast.Extent.Text.Substring($ast.Extent.Text.IndexOf('# B5_MAIN:'))
$main=$main -replace 'Import-B5SharedGuards \$b3 \$ExpectedB3HelperSha256','' -replace 'Set-B5Overrides','' -replace 'exit \$exitCode','Write-Output $exitCode'
$main=$main.Replace('$PSCommandPath',("'"+$path+"'" )).Replace('$PSScriptRoot',("'"+$PSScriptRoot+"'"))
$fakeFirmware=Join-Path $root 'fake-firmware.bin';[IO.File]::WriteAllBytes($fakeFirmware,[byte[]]::new(8192))
$firmwareSha=HashFile $fakeFirmware
$raw=[byte[]]::new(4194305);$block=[byte[]](0..250);for($i=0;$i -lt $raw.Length;$i+=251){[Buffer]::BlockCopy($block,0,$raw,$i,[Math]::Min(251,$raw.Length-$i))}
$evidenceBytes=[Text.Encoding]::UTF8.GetBytes('{"fake_only":true}')
$script:fakeCalls=0;$script:binaryExports=0;$script:fakeFault=''
function Invoke-B5Adb([string[]]$DeviceArguments,[string]$InputText='',[string]$BinaryDestination='',[bool]$AfterDrain=$false,[object]$ExpectedExportBytes=-1) {
    $script:fakeCalls++
    $manifest=[Text.Encoding]::UTF8.GetString((Read-B5Manifest $manifestStream))
    if ($manifest -notmatch '"event":"CONSUME"') { throw 'Fake observed device command before durable consume' }
    if ($AfterDrain -and -not $script:fakeTerminal) { throw 'Fake observed export before terminal drain' }
    if ($AfterDrain) { Assert-B5ExportCall $DeviceArguments $Report $Session $BinaryDestination }
    $joined=$DeviceArguments -join ' '
    switch -Regex ($joined) {
        '^get-state$' {if($Serial -cne 'offline-fixture'){throw 'B5_FAKE_SERIAL_MISMATCH'};return 'device'}
        '^shell getprop ro.product.model$' { if($script:fakeFault -ceq 'model'){return 'WRONG'};return 'GM1900' }
        '^shell getprop ro.product.manufacturer$' {return 'OnePlus'}
        '^shell getprop ro.build.version.sdk$' { return '30' }
        '^shell settings get global boot_count$' { return '47' }
        '^shell pm path ' { return 'package:/data/app/fake/base.apk' }
        '^shell sha256sum /data/app/' { return $hash+'  base.apk' }
        '^shell cat /proc/uptime$' { return '1.002 1.000' }
        '^shell dumpsys power$' { return '  mWakefulness=Awake' }
        '^shell dumpsys activity activities$' {
            if ($script:fakeFault -ceq 'foreground') { return 'ResumedActivity: ActivityRecord{fake u0 wrong/.Activity}' }
            return 'ResumedActivity: ActivityRecord{fake u0 info.martinmarinov.dvbdriver/.Tbs5530AutonomousDiagnosticActivity t1}'
        }
        '^shell am start .*Tbs5530PermissionPreflightActivity' { $script:permissionToken=$DeviceArguments[[Array]::IndexOf($DeviceArguments,'preflight_token')+1];return 'Success' }
        'permission-preflight.json' {
            $v=Permission $script:permissionToken
            if ($script:fakeFault -ceq 'permission') { $v.state='DENIED' }
            return ($v|ConvertTo-Json -Compress)
        }
        '^shell am start .*Tbs5530AutonomousDiagnosticActivity' {
            if($DeviceArguments -ccontains 'b4_enabled' -or $DeviceArguments[[Array]::IndexOf($DeviceArguments,'b5_enabled')+1] -cne 'true') {throw 'Fake B5 launch mismatch'}
            $script:launch=$DeviceArguments[[Array]::IndexOf($DeviceArguments,'launch_token')+1];return 'Success'
        }
        'autonomous-v1-challenge.json' { $v=Challenge;$v.launch_token=$script:launch;return ($v|ConvertTo-Json -Depth 15 -Compress) }
        '^shell run-as .*base64 -d >' { $script:stageCalls++;return '' }
        'firmware.bin$' { return $firmwareSha+'  firmware.bin' }
        '^shell set -eu' {
            if ($InputText) {
                $script:grantCalls++
                if ($script:fakeFault -ceq 'grant-uncertain') { throw 'B5_FAKE_GRANT_PUBLISH_UNCERTAIN' }
                $g=$InputText|ConvertFrom-Json
                if ($g.b5_card_sha256 -cne $ExpectedB5CardSha256 -or $g.b5_budget_id -cne $card.budget_id -or $g.b5_attempt_ordinal -ne $B5AttemptOrdinal -or
                    $g.b5_layout -cne $layout -or $g.b5_stale_prefix_bytes -ne 2048 -or $g.b3_queue_depth -ne 16 -or $g.b3_urb_bytes -ne 16384 -or
                    $g.expires_elapsed_ms -ne 301000 -or $g.physical_recovery_epoch -cne $PhysicalRecoveryEpoch) { throw 'Fake grant binding mismatch' }
                return '{"elapsed_ms":1002,"expired":false,"report":null,"permission":null}'
            }
            $r=Report;$r|Add-Member b5_k25_auto_fallback_used $false;$r|Add-Member b5_manual_demod_lock $true;$r|Add-Member b5_actual_frontend_experiment $FrontendExperiment;$r.raw_received_bytes=2*$raw.Length;$r.ts_bytes=2*$raw.Length
            $r|Add-Member launch_token $script:launch;$r|Add-Member frontend_experiment $FrontendExperiment;$r|Add-Member ts_variant 'NATIVE_USBFS_STREAM';$r|Add-Member control_plane_only $false
            foreach($p in @{b5_enabled=$true;b5_rssi_sequence='WINDOWS_88_00_87_C0';b5_allow_k25_auto=($FrontendExperiment -ceq 'B5_K25_MANUAL_PLP0');b5_layout=$layout;b5_stale_prefix_bytes=2048;b5_monitor_period_ms=100;b5_monitor_jitter_ms=20;b5_rssi_samples_per_off=3}.GetEnumerator()){$r|Add-Member $p.Key $p.Value}
            switch ($script:fakeFault) { 'status75' {$r.last_errno=75};'status71' {$r.last_errno=71};'detach' {$r.fx2_reload.detach_ns=123};'drain' {$r.b5_phases[1].drained=$false};'report-layout' {$r.b5_layout='bad'} }
            $script:fakeTerminal=$true
            return (@{elapsed_ms=1003;expired=$false;report=$r;permission=$null}|ConvertTo-Json -Depth 15 -Compress)
        }
        '^shell run-as .* sh -c ' {
            if (-not $AfterDrain) { throw 'Unexpected predrain shell' }
            if ($joined -match 'no_backup/|attachments.txt|receive-audit') { return '' }
            if ($script:fakeFault -ceq 'missing-monitor' -and $joined -match 'phase31-signal-monitor') { return '' }
            return 'PRESENT'
        }
        '^shell run-as .* wc -c ' {
            if ($script:fakeFault -ceq 'oversized' -and $joined -match 'received-ON1.ts$') { return '268435457 raw' }
            return ([string]$(if ($joined -match '\.ts$') {$raw.Length} else {$evidenceBytes.Length}))+' evidence'
        }
        '^shell run-as .* sha256sum ' {
            $b=if ($joined -match '\.ts$') {$raw} else {$evidenceBytes}
            return (Get-B5Hash $b)+'  evidence'
        }
        '^exec-out run-as .* sh -c ' {
            if (-not $AfterDrain -or -not $BinaryDestination) { throw 'Binary export unguarded' }
            # exec-out preserves argv; unlike adb shell, it does not strip shell-command quotes.
            if ($DeviceArguments.Count -ne 6 -or $DeviceArguments[0] -cne 'exec-out' -or $DeviceArguments[1] -cne 'run-as' -or
                $DeviceArguments[2] -cne $package -or $DeviceArguments[3] -cne 'sh' -or $DeviceArguments[4] -cne '-c' -or
                $DeviceArguments[5] -cnotmatch '^head -c (1048577|67108865|268435457) (files|no_backup)/[A-Za-z0-9./-]+$') { throw 'FAKE_EXEC_OUT_LITERAL_COMMAND_QUOTES_OR_ARGV_INVALID' }
            $b=if ($DeviceArguments[5].EndsWith('.ts',[StringComparison]::Ordinal)) {$raw} else {$evidenceBytes}
            [IO.File]::WriteAllBytes($BinaryDestination,$b);$script:binaryExports++;return
        }
        default { throw ('Unexpected fake operation: '+$joined) }
    }
}
$ExpectedB5HelperSha256=HashFile $path;$ExpectedHelperSha256=HashFile $runtime
$Serial='offline-fixture';$DurationSeconds=300;$FirmwarePath=$fakeFirmware;$PhysicalRecoveryEpoch=$epoch;$PhysicalRecoveryConfirmed=$true;$PhysicalColdSeconds=5
$RfInConfirmed=$true;$AntennaNoDcConfirmed=$true;$GrantBoundedSession=$true;$ProofOfGuardPassed=$true;$ApproveB5SessionBudget=$true;$Approval='OK B5'
$B5AttemptOrdinal=1;$AdbPath=$path;$OutputDirectory=''
function SetupIntegration([string]$Name) {
    $script:card=Card $Name;$script:card.attempts[0].frontend_experiment=$FrontendExperiment;$script:card.attempts[0].allow_k25_auto=($FrontendExperiment -ceq 'B5_K25_MANUAL_PLP0');$script:card|Add-Member source_git_sha $head;$v=Challenge
    foreach($attempt in $script:card.attempts){$attempt.golden_sha256=$ExpectedFrontendGoldenSha256;$attempt.reload_plan_sha256=$ExpectedReloadPlanSha256}
    foreach ($p in @{model='GM1900';manufacturer='OnePlus';rssi_sequence='WINDOWS_88_00_87_C0';serial=$Serial;api=30;boot_count=47;layout=$layout;stale_prefix_bytes=2048;monitor_period_ms=100;monitor_jitter_ms=20;rssi_samples_per_off=3;fx2_reload_store_sha256=$v.fx2_reload_store_sha256;fx2_reload_anchor_sha256=$v.fx2_reload_anchor_sha256;source_git_sha=$head;source_manifest_path=$sourceManifest;source_manifest_sha256=(HashFile $sourceManifest);
        app_apk_sha256=$hash;autonomous_helper_sha256=$ExpectedHelperSha256;b3_helper_sha256=$ExpectedB3HelperSha256;b5_helper_sha256=$ExpectedB5HelperSha256;
        firmware_sha256=$firmwareSha;reload_plan_sha256=$ExpectedReloadPlanSha256;golden_sha256=$ExpectedFrontendGoldenSha256;
        duration_ms=$B5DurationMillis;ttl_ms=300000;heartbeat_ms=2000;permission_wait_ms=30000}.GetEnumerator()) { $script:card|Add-Member $p.Key $p.Value -Force }
    $script:B5SessionDirectory=$card.session_directory
    $script:B5CardPath=Join-Path $root ($Name+'-card.json');Write-B5NewJson $B5CardPath $card
    $script:ExpectedB5CardSha256=HashFile $B5CardPath
    $script:fakeTerminal=$false;$script:grantCalls=0;$script:stageCalls=0
}
SetupIntegration 'success'
$result=& {$PSCommandPath=$path;$PSScriptRoot=Split-Path -Parent $path;Invoke-Expression $main}
Check ($result[-1] -eq 1 -and $script:grantCalls -eq 1 -and $script:binaryExports -gt 5) 'one fake permission-first B5 capture dispatch'
$receipt=[IO.File]::ReadAllLines((Join-Path $B5SessionDirectory 'b5-session-manifest.jsonl'))[-1]|ConvertFrom-Json
Check ($receipt.capture_complete -and -not $receipt.session_closed -and $receipt.classification -ceq 'PENDING_ANALYSIS' -and -not $receipt.single_run_pass) 'receipt never certifies Android parity'
$captured=@(Get-ChildItem -LiteralPath $card.raw_directory -Recurse -Filter '*.ts')
Check ($captured.Count -eq 4 -and $captured[0].Length -eq $raw.Length -and (HashFile $captured[0].FullName) -ceq (Get-B5Hash $raw)) 'received and backend raw binary exact beyond4MiB'
$calls=$script:fakeCalls
Refuse {& {$PSCommandPath=$path;$PSScriptRoot=Split-Path -Parent $path;Invoke-Expression $main}} 'same ordinal cannot redispatch'
Check ($script:fakeCalls -eq $calls) 'replay refused before transport'
$FrontendExperiment='B5_K36_MANUAL_PLP0';SetupIntegration 'k36-manual'
$result=& {$PSCommandPath=$path;$PSScriptRoot=Split-Path -Parent $path;Invoke-Expression $main}
Check ($result[-1] -eq 1 -and $script:grantCalls -eq 1) 'K36 selected per-attempt plan pins and no AUTO'
$FrontendExperiment='B5_K25_MANUAL_PLP0'

foreach ($fault in @('permission','foreground','model','serial','grant-uncertain','status75','status71','detach','drain','oversized','missing-monitor','report-layout')) {
    $Serial='offline-fixture';$script:fakeFault=$fault;SetupIntegration ('failure-'+$fault)
    if($fault -ceq 'serial'){$Serial='wrong-serial'}else{$Serial='offline-fixture'}
    $threw=$false
    try {$null=& {$PSCommandPath=$path;$PSScriptRoot=Split-Path -Parent $path;Invoke-Expression $main}}catch{$threw=$true}
    if($fault -ceq 'serial'){Check ($threw -and -not (Test-Path (Join-Path $B5SessionDirectory 'b5-session-manifest.jsonl'))) 'serial pin fails before transport/budget';continue}
    $receipt=[IO.File]::ReadAllLines((Join-Path $B5SessionDirectory 'b5-session-manifest.jsonl'))[-1]|ConvertFrom-Json
    if($fault -cin @('permission','foreground','model','serial')) {
        Check ($threw -and -not $receipt.drained -and $script:grantCalls -eq 0) ('no grant after refusal '+$fault)
    }else{
        Check $receipt.session_closed ('whole budget latched '+$fault)
        Refuse {Assert-B5Ledger ([IO.File]::ReadAllBytes((Join-Path $B5SessionDirectory 'b5-session-manifest.jsonl'))) $ExpectedB5CardSha256 $card 2} ('no replay '+$fault)
    }
}
$Serial='offline-fixture'
foreach ($scriptPath in @($path,$PSCommandPath,$b3)) {
    $b=[IO.File]::ReadAllBytes($scriptPath);$text=[Text.Encoding]::UTF8.GetString($b)
    Check (($b[0..2]-join ',') -ceq '239,187,191' -and -not [regex]::IsMatch($text,'(?<!\r)\n')) 'BOM/CRLF preserved'
}
$summary=[pscustomobject]@{status='PASS';checks=$checks;real_adb_calls=0;fake_transport_calls=$script:fakeCalls;binary_exports=$script:binaryExports;
    b5_helper_sha256=(HashFile $path);b3_helper_sha256=$ExpectedB3HelperSha256;hardware='NOT_RUN';vm='NOT_RUN';network='NOT_RUN';verdict_created_by_helper=$false}
Write-B5NewJson (Join-Path $reportRoot 'test-summary.json') $summary
Write-Output ("PASS: B5 $checks/$checks; chỉ fake, không ADB thật/phần cứng/VM/network; evidence: "+$root)
