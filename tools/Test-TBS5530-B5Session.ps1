# SPDX-License-Identifier: GPL-2.0-or-later
# Fake-only: override device transport before evaluating main. Never invokes ADB or hardware.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory,[Parameter(Mandatory)][string]$ReportDirectory,[switch]$RepositoryFixtures)
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
if ($RepositoryFixtures) {
    function Assert-B5PrivatePath([string]$Path,[string]$Root) {
        $full=[IO.Path]::GetFullPath($Path)
        if (-not $full.StartsWith($repository.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -or $full -cne $Path) { throw 'Fake fixture outside repository' }
        return $full
    }
}
$B5TsDrive=85;$ApproveP4OptionalFerrite=$false;$DebugStep=0;$UiRetry=0;$ApproveSessionOptionalStep=$false
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
        b5_ts_drive=$B5TsDrive;b5_enabled=$true;b5_rssi_sequence='WINDOWS_88_00_87_C0';b5_allow_k25_auto=($FrontendExperiment -ceq 'B5_K25_MANUAL_PLP0');b5_layout=$layout;b5_stale_prefix_bytes=2048;b5_monitor_period_ms=100;b5_monitor_jitter_ms=20;b5_rssi_samples_per_off=3;attachment_history_sha256=$null;fx2_reload_store_sha256=$h;
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
            if ($DeviceArguments[[Array]::IndexOf($DeviceArguments,'b5_ts_drive')+1] -cne [string]$B5TsDrive) { throw 'Fake launch drive mismatch' }
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
                if ($g.b5_ts_drive -isnot [long] -and $g.b5_ts_drive -isnot [int]) { throw 'Fake grant drive type mismatch' }
                if ($g.b5_ts_drive -ne $B5TsDrive -or $g.golden_sha256 -cne $ExpectedFrontendGoldenSha256) { throw 'Fake grant drive/plan mismatch' }
                if ($g.b5_card_sha256 -cne $ExpectedB5CardSha256 -or $g.b5_budget_id -cne $card.budget_id -or $g.b5_attempt_ordinal -ne $B5AttemptOrdinal -or
                    $g.b5_layout -cne $layout -or $g.b5_stale_prefix_bytes -ne 2048 -or $g.b3_queue_depth -ne 16 -or $g.b3_urb_bytes -ne 16384 -or
                    $g.expires_elapsed_ms -ne 301000 -or $g.physical_recovery_epoch -cne $PhysicalRecoveryEpoch) { throw 'Fake grant binding mismatch' }
                return '{"elapsed_ms":1002,"expired":false,"report":null,"permission":null}'
            }
            $r=Report;$r|Add-Member b5_k25_auto_fallback_used $false;$r|Add-Member b5_manual_demod_lock $true;$r|Add-Member b5_actual_frontend_experiment $FrontendExperiment;$r.raw_received_bytes=2*$raw.Length;$r.ts_bytes=2*$raw.Length
            $r|Add-Member launch_token $script:launch;$r|Add-Member frontend_experiment $FrontendExperiment;$r|Add-Member ts_variant 'NATIVE_USBFS_STREAM';$r|Add-Member control_plane_only $false
            foreach($p in @{b5_ts_drive=$B5TsDrive;b5_enabled=$true;b5_rssi_sequence='WINDOWS_88_00_87_C0';b5_allow_k25_auto=($FrontendExperiment -ceq 'B5_K25_MANUAL_PLP0');b5_layout=$layout;b5_stale_prefix_bytes=2048;b5_monitor_period_ms=100;b5_monitor_jitter_ms=20;b5_rssi_samples_per_off=3}.GetEnumerator()){$r|Add-Member $p.Key $p.Value}
            switch ($script:fakeFault) { 'status75' {$r.last_errno=75};'status71' {$r.last_errno=71};'detach' {$r.fx2_reload.detach_ns=123};'drain' {$r.b5_phases[1].drained=$false};'report-layout' {$r.b5_layout='bad'} }
            switch ($script:fakeFault) {
                'drive-report-missing' {$r.PSObject.Properties.Remove('b5_ts_drive')}
                'drive-report-string' {$r.b5_ts_drive=[string]$B5TsDrive}
                'drive-report-mismatch' {$r.b5_ts_drive=169-$B5TsDrive}
                'drive-report-bool' {$r.b5_ts_drive=$true}
                'drive-report-fraction' {$r.b5_ts_drive=[double]$B5TsDrive}
            }
            if ($script:fakeFault -cin @('session-ui','session-permission','session-partial','session-uncertain','session-usb','session-reload','session-drain')) {
                $r.b5_phases=@();$r.fx2_reload=$null;$r.ts_bytes=0;$r.raw_received_bytes=0;$r.result='STOPPED'
                $r|Add-Member stop_reason $(if($script:fakeFault -ceq 'session-permission'){'PERMISSION_DENIED'}else{'ATTACH_PERMISSION_UI_TIMEOUT'})
                $r.firmware=[pscustomobject]@{state='OPERATIONAL_CANDIDATE';attempted=132;completed=132;cleanup_failure='NONE';stop_cause='NONE'}
                switch ($script:fakeFault) {
                    'session-partial' {$r.firmware.completed=100}
                    'session-uncertain' {$r.firmware.state='UNCERTAIN';$r.state='ERROR_UNCERTAIN'}
                    'session-usb' {$r.last_errno=75}
                    'session-reload' {$r.fx2_reload=[pscustomobject]@{state='COMPLETE';attempted=132;completed=132;detach_ns=-1}}
                    'session-drain' {$r.cleanup='DRAIN_NOT_CERTIFIED'}
                }
            }
            $script:fakeTerminal=$true
            return (@{elapsed_ms=1003;expired=$false;report=$r;permission=$null}|ConvertTo-Json -Depth 15 -Compress)
        }
        '^shell run-as .* sh -c ' {
            if (-not $AfterDrain) { throw 'Unexpected predrain shell' }
            if ($joined -match 'no_backup/|attachments.txt|receive-audit') { return '' }
            if ($script:fakeFault.StartsWith('session-',[StringComparison]::Ordinal) -and $joined -notmatch '/report.json') { return '' }
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
$Serial='offline-fixture';$script:fakeFault=''

function P4Card([string]$Name) {
    $c=Card $Name;$c.schema='TBS5530-P4-CARD/v1';$c.approval='OK P4';$c.max_attempts=3;$c.max_android=3;$c.max_windows=0;$c.attempts=$c.attempts[0..2]
    foreach ($pin in @{model='GM1900';manufacturer='OnePlus';api=30;phase4_scope='K25_DEBUG_ONLY';max_oneplus=10;max_samsung=2;max_android_debug=3;windows_authorized=$false}.GetEnumerator()) { $c|Add-Member $pin.Key $pin.Value -Force }
    for ($i=0;$i -lt 3;$i++) {
        $c.attempts[$i]|Add-Member ts_drive @(85,84,85)[$i] -Force
        $c.attempts[$i]|Add-Member optional ($i -eq 2) -Force
        $c.attempts[$i]|Add-Member ferrite_only ($i -eq 2) -Force
        $c.attempts[$i].golden_sha256=(@('d','f','d')[$i]*64)
    }
    return ($c|ConvertTo-Json -Depth 20|ConvertFrom-Json)
}
$p4=P4Card 'p4-unit'
Assert-B5Card $p4 @{} 1 $epoch 16 16384 85;Check $true 'serialized P4 accepts OK P4 and drive55'
Assert-B5Card $p4 @{} 2 $epoch2 16 16384 84;Check $true 'serialized P4 drive54 second attempt'
Assert-B5Card $p4 @{} 3 $p4.attempts[2].physical_recovery_epoch 16 16384 85;Check $true 'P4 optional ferrite drive55 third'
$c=P4Card 'p4-two';$c.max_attempts=2;$c.attempts=$c.attempts[0..1]
Assert-B5Card $c @{} 2 $epoch2 16 16384 84;Check $true 'P4 two mandatory attempts without optional'
foreach ($fault in @('approval','schema','phase','model','manufacturer','api','api-string','windows','windows-auth','windows-capture','oneplus','samsung','debug','android','fourth','k36','order','missing-drive','string-drive','bool-drive','fraction-drive','optional','ferrite','carry','migration','extension')) {
    $c=P4Card ('p4-'+$fault)
    switch ($fault) {
        'approval' {$c.approval='OK B5'}
        'schema' {$c.schema='TBS5530-B5-CARD/v1'}
        'phase' {$c.phase4_scope='PRODUCTION'}
        'model' {$c.model='SM-X926B'}
        'manufacturer' {$c.manufacturer='Other'}
        'api' {$c.api=36}
        'api-string' {$c.api='30'}
        'windows' {$c.max_windows=1}
        'windows-auth' {$c.windows_authorized=$true}
        'windows-capture' {$c|Add-Member windows_capture ([pscustomobject]@{authorized=$true})}
        'oneplus' {$c.max_oneplus=11}
        'samsung' {$c.max_samsung=3}
        'debug' {$c.max_android_debug=4}
        'android' {$c.max_android=4}
        'fourth' {$c.max_attempts=4}
        'k36' {$c.attempts[0].frontend_experiment='B5_K36_MANUAL_PLP0';$c.attempts[0].allow_k25_auto=$false}
        'order' {$c.attempts[0].ts_drive=84}
        'missing-drive' {$c.attempts[0].PSObject.Properties.Remove('ts_drive')}
        'string-drive' {$c.attempts[0].ts_drive='85'}
        'bool-drive' {$c.attempts[0].ts_drive=$true}
        'fraction-drive' {$c.attempts[0].ts_drive=85.0}
        'optional' {$c.attempts[1].optional=$true}
        'ferrite' {$c.attempts[2].ferrite_only=$false}
        'carry' {$c|Add-Member carry_forward $null}
        'migration' {$c|Add-Member target_migration $null}
        'extension' {$c|Add-Member one_extra_extension $null}
    }
    Refuse {Assert-B5Card $c @{} 1 $epoch 16 16384 85} ('P4 scope refuses '+$fault)
}
Refuse {Assert-B5Approval $p4 'OK B5'} 'P4 does not manufacture OK B5'
Refuse {Assert-B5Approval (Card 'approval-b5') 'OK P4'} 'OK P4 cannot authorize B5 schema'
Refuse {Assert-B5Card $p4 @{} 2 $epoch2 16 16384 85} 'P4 second drive mismatch'
Refuse {Assert-B5Card $p4 @{} 2 $epoch2 16 16384 '84'} 'card drive argument string refused'
Refuse {Assert-B5Card (Card 'legacy54') @{} 1 $epoch 16 16384 84} 'legacy absent drive permits only85'
foreach ($drive in @(84,85)) {
    $B5TsDrive=$drive;$c=Card ('drive'+$drive);$c.attempts[0]|Add-Member ts_drive $drive
    Assert-B5Card $c @{} 1 $epoch 16 16384 $drive;Check $true ('explicit numeric drive '+$drive)
    $v=Challenge;Assert-B5Challenge $v $epoch 1001;Check $true ('challenge exact drive '+$drive)
    foreach ($bad in @('missing','string','bool','fraction','mismatch')) {
        $v=Challenge
        switch ($bad) {
            'missing' {$v.PSObject.Properties.Remove('b5_ts_drive')}
            'string' {$v.b5_ts_drive=[string]$drive}
            'bool' {$v.b5_ts_drive=$true}
            'fraction' {$v.b5_ts_drive=[double]$drive}
            'mismatch' {$v.b5_ts_drive=169-$drive}
        }
        Refuse {Assert-B5Challenge $v $epoch 1001} ('challenge drive '+$drive+' '+$bad)
    }
}
$B5TsDrive=85
# Ledger tests use synthetic rows and a review stub, never historical state.
$reviewOriginal=${function:Read-B5Review}
function Read-B5Review { return [pscustomobject]@{sha256=('c'*64);result='B5_REVIEWED'} }
function TamperedLedgerRow([byte[]]$Prefix,[object]$Row) {
    $Row|Add-Member previous_sha256 (Get-B5Hash $Prefix)
    return ,[byte[]]($Prefix+[Text.Encoding]::UTF8.GetBytes(($Row|ConvertTo-Json -Compress -Depth 8)+"`n"))
}
$ledgerPath=Join-Path $root 'p4-fake-ledger.jsonl'
$stream=[IO.FileStream]::new($ledgerPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try {
    $prefix=Add-B5ManifestRecord $stream ([byte[]]@()) ([pscustomobject]@{event='HEADER';card_sha256=$hash;budget_id=$p4.budget_id;max_attempts=3;max_windows=0})
    foreach ($ordinal in 1..3) {
        $a=$p4.attempts[$ordinal-1];$null=Assert-B5Ledger $prefix $hash $p4 $ordinal
        $before=$prefix
        foreach ($bad in @('missing','string','mismatch','golden')) {
            $row=[pscustomobject]@{event='CONSUME';ordinal=$ordinal;epoch=$a.physical_recovery_epoch;ts_drive=$a.ts_drive;queue_depth=16;urb_bytes=16384;frontend_experiment=$a.frontend_experiment;golden_sha256=$a.golden_sha256;reload_plan_sha256=$a.reload_plan_sha256;previous_offline_validation_sha256=('c'*64)}
            switch ($bad) {'missing' {$row.PSObject.Properties.Remove('ts_drive')};'string' {$row.ts_drive=[string]$a.ts_drive};'mismatch' {$row.ts_drive=169-$a.ts_drive};'golden' {$row.golden_sha256='0'*64}}
            $tampered=TamperedLedgerRow $before $row
            try { Assert-B5Ledger $tampered $hash $p4 ($ordinal+1)|Out-Null;throw 'bad row accepted' } catch { Check ($_.Exception.Message -in @('B5_TS_DRIVE_BINDING_MISMATCH','B5_FRONTEND_CARD_BINDING_MISMATCH')) ('P4 consume rejects '+$ordinal+' '+$bad) }
        }
        $prefix=Add-B5ManifestRecord $stream $before ([pscustomobject]@{event='CONSUME';ordinal=$ordinal;epoch=$a.physical_recovery_epoch;ts_drive=$a.ts_drive;queue_depth=16;urb_bytes=16384;frontend_experiment=$a.frontend_experiment;golden_sha256=$a.golden_sha256;reload_plan_sha256=$a.reload_plan_sha256;previous_offline_validation_sha256=('c'*64)})
        Refuse {Assert-B5Ledger $prefix $hash $p4 $ordinal} ('P4 consumed replay '+$ordinal)
        $wrong=TamperedLedgerRow $prefix ([pscustomobject]@{event='RECEIPT';ordinal=$ordinal;ts_drive=(169-$a.ts_drive);session_closed=$false;drained=$true;result='REVIEW_REQUIRED';queue_depth=16;urb_bytes=16384})
        Refuse {Assert-B5Ledger $wrong $hash $p4 ($ordinal+1)} ('P4 receipt drive mismatch '+$ordinal)
        $prefix=Add-B5ManifestRecord $stream $prefix ([pscustomobject]@{event='RECEIPT';ordinal=$ordinal;ts_drive=$a.ts_drive;session_closed=$false;drained=$true;result='REVIEW_REQUIRED';queue_depth=16;urb_bytes=16384})
    }
    Refuse {Assert-B5Ledger $prefix $hash $p4 4} 'P4 no fourth debug attempt'
} finally { $stream.Dispose();Set-Item Function:Read-B5Review $reviewOriginal }

# Main-path checks pin the serialized card before any fake transport call.
$Approval='OK P4';$B5TsDrive=85;SetupIntegration 'p4-main'
$c=P4Card 'p4-main'
foreach ($key in @('schema','approval','max_attempts','max_android','max_windows','phase4_scope','max_oneplus','max_samsung','max_android_debug','windows_authorized','attempts')) { $card|Add-Member $key $c.$key -Force }
$card.attempts[0].golden_sha256=$ExpectedFrontendGoldenSha256
foreach ($a in $card.attempts) { $a.reload_plan_sha256=$ExpectedReloadPlanSha256 }
Write-B5NewJson ($B5CardPath+'.p4') $card;$B5CardPath+='.p4';$ExpectedB5CardSha256=HashFile $B5CardPath
$calls=$script:fakeCalls;$Approval='OK B5'
Refuse {& {Invoke-Expression $main}} 'main mismatched approval refused'
Check ($script:fakeCalls -eq $calls) 'wrong P4 approval causes no transport'
$Approval='OK P4'
$result=& {Invoke-Expression $main}
Check ($result[-1] -eq 1 -and $script:grantCalls -eq 1) 'P4 main OK P4 only fake launch/grant/report'
$rows=@([IO.File]::ReadAllLines((Join-Path $B5SessionDirectory 'b5-session-manifest.jsonl'))|ForEach-Object {$_|ConvertFrom-Json})
Check ($rows[1].ts_drive -eq 85 -and $rows[2].ts_drive -eq 85) 'P4 consume and receipt carry drive'
$calls=$script:fakeCalls
Refuse {& {Invoke-Expression $main}} 'P4 main replay refused'
Check ($script:fakeCalls -eq $calls) 'P4 replay no transport'
$B5AttemptOrdinal=2;$PhysicalRecoveryEpoch=$epoch2;$B5TsDrive=84
$savedGolden=$ExpectedFrontendGoldenSha256;$ExpectedFrontendGoldenSha256=$card.attempts[1].golden_sha256
$reviewOriginal=${function:Read-B5Review}
function Read-B5Review { return [pscustomobject]@{sha256=('c'*64);result='B5_REVIEWED'} }
try {
    $result=& {Invoke-Expression $main}
    Check ($result[-1] -eq 1 -and $script:grantCalls -eq 2) 'P4 second54 uses distinct pinned golden and one grant'
    $rows=@([IO.File]::ReadAllLines((Join-Path $B5SessionDirectory 'b5-session-manifest.jsonl'))|ForEach-Object {$_|ConvertFrom-Json})
    Check ($rows[3].ts_drive -eq 84 -and $rows[3].golden_sha256 -cne $rows[1].golden_sha256 -and $rows[4].ts_drive -eq 84) 'P4 drive54 consume and receipt bind separate golden'
} finally { Set-Item Function:Read-B5Review $reviewOriginal }
$calls=$script:fakeCalls;$B5TsDrive=85;$ExpectedFrontendGoldenSha256=$savedGolden
$B5AttemptOrdinal=3;$PhysicalRecoveryEpoch=$card.attempts[2].physical_recovery_epoch;$ApproveP4OptionalFerrite=$false
Refuse {& {Invoke-Expression $main}} 'optional ferrite not autoexecuted'
Check ($script:fakeCalls -eq $calls) 'optional not selected no transport'
$B5AttemptOrdinal=1;$PhysicalRecoveryEpoch=$epoch;$Approval='OK B5'
foreach ($fault in @('none','missing','string','mismatch','bool','fraction')) {
    $B5TsDrive=84;$script:fakeFault=$(if($fault -eq 'none'){''}else{'drive-report-'+$fault})
    SetupIntegration ('drive54-'+$fault)
    $card.attempts[0]|Add-Member ts_drive 84 -Force
    Write-B5NewJson ($B5CardPath+'.drive') $card;$B5CardPath+='.drive';$ExpectedB5CardSha256=HashFile $B5CardPath
    $threw=$false
    try {$result=& {Invoke-Expression $main}} catch {$threw=$true}
    $rows=@([IO.File]::ReadAllLines((Join-Path $B5SessionDirectory 'b5-session-manifest.jsonl'))|ForEach-Object {$_|ConvertFrom-Json})
    if ($fault -eq 'none') { Check (-not $threw -and $script:grantCalls -eq 1 -and $rows[1].ts_drive -eq 84 -and $rows[2].ts_drive -eq 84 -and $rows[2].capture_complete) 'drive54 launch/challenge/grant/terminal/consume/receipt bound' }
    else { Check ($threw -and $rows[2].session_closed) ('terminal drive fails closed '+$fault) }
}
$B5TsDrive=85
function SessionCard([object]$Template,[string]$Id='P4b',[int]$Count=5) {
    $c=$Template|ConvertTo-Json -Depth 20|ConvertFrom-Json
    foreach ($key in @('phase4_scope','max_android_debug','max_oneplus','carry_forward','target_migration','one_extra_extension')) { $c.PSObject.Properties.Remove($key) }
    foreach ($p in @{schema='TBS5530-SESSION-CARD/v1';card_id=$Id;approval=('OK '+$Id);max_attempts=$Count;max_debug_attempts=$Count;
        max_android=9;max_oneplus=9;max_samsung=2;max_windows=0;windows_authorized=$false;ui_retry_per_step=2;ui_retry_total=2;model='GM1900';manufacturer='OnePlus';api=30}.GetEnumerator()) { $c|Add-Member $p.Key $p.Value -Force }
    $c|Add-Member debug_steps @(
        [pscustomobject]@{step=1;ts_drive=85;frontend_experiment='B5_K25_MANUAL_PLP0';optional=$false;golden_sha256=$ExpectedFrontendGoldenSha256;reload_plan_sha256=$ExpectedReloadPlanSha256;allow_k25_auto=$true},
        [pscustomobject]@{step=2;ts_drive=84;frontend_experiment='B5_K36_MANUAL_PLP0';optional=$false;golden_sha256=('f'*64);reload_plan_sha256=('e'*64);allow_k25_auto=$false},
        [pscustomobject]@{step=4;ts_drive=85;frontend_experiment='B5_K25_MANUAL_PLP0';optional=$true;golden_sha256=$ExpectedFrontendGoldenSha256;reload_plan_sha256=$ExpectedReloadPlanSha256;allow_k25_auto=$true}) -Force
    $c.attempts=@(foreach ($n in 1..$Count) {
        [pscustomobject]@{ordinal=$n;physical_recovery_epoch=[guid]::NewGuid().ToString();queue_depth=16;urb_bytes=16384;
            notes=[pscustomobject]@{separation='Xa điện thoại';cable='Cáp ngắn';accessories='Không phụ kiện'}}
    })
    return $c
}
function SetupSessionIntegration([string]$Name) {
    SetupIntegration $Name
    $script:card=SessionCard $card
    $script:Approval='OK P4b';$script:DebugStep=1;$script:UiRetry=0;$script:B5AttemptOrdinal=1;$script:B5TsDrive=85
    $script:ApproveSessionOptionalStep=$false;$script:PhysicalRecoveryEpoch=$card.attempts[0].physical_recovery_epoch
    $script:B5CardPath+='.session';Write-B5NewJson $B5CardPath $card;$script:ExpectedB5CardSha256=HashFile $B5CardPath
}
function SelectSessionAttempt([int]$Ordinal,[int]$Step,[int]$Retry=0) {
    $script:B5AttemptOrdinal=$Ordinal;$script:DebugStep=$Step;$script:UiRetry=$Retry
    $a=Get-B5SessionAttempt $card $Ordinal $Step
    $script:PhysicalRecoveryEpoch=$a.physical_recovery_epoch;$script:B5TsDrive=$a.ts_drive;$script:FrontendExperiment=$a.frontend_experiment
    $script:ExpectedFrontendGoldenSha256=$a.golden_sha256;$script:ExpectedReloadPlanSha256=$a.reload_plan_sha256
}
function SessionRows {
    return @([IO.File]::ReadAllLines((Join-Path $card.budget_directory 'b5-session-manifest.jsonl'))|ForEach-Object {$_|ConvertFrom-Json})
}
function WriteSessionReview([int]$Ordinal) {
    $receipt=@(SessionRows|Where-Object {$_.event -ceq 'RECEIPT' -and $_.ordinal -eq $Ordinal})[0]
    $attempt=Join-Path $card.budget_directory ('attempt-'+$Ordinal)
    $analysis=Join-Path $reportRoot ($card.budget_id+'-analysis-'+$Ordinal+'.json')
    Write-B5NewJson $analysis ([pscustomobject]@{status='OFFLINE_FAKE_REVIEW';ordinal=$Ordinal;hardware='NOT_RUN'})
    $v=[pscustomobject]@{schema='TBS5530-SESSION-OFFLINE-VALIDATION/v1';ordinal=$Ordinal;physical_recovery_epoch=$card.attempts[$Ordinal-1].physical_recovery_epoch;
        result=$(if($null -ne $receipt.retry_reason){$receipt.retry_reason}else{'B5_REVIEWED'});ts_drive=$receipt.ts_drive;
        frontend_experiment=$receipt.frontend_experiment;golden_sha256=$receipt.golden_sha256;reload_plan_sha256=$receipt.reload_plan_sha256;
        receipt_sha256=(Get-B5Hash ([Text.Encoding]::UTF8.GetBytes(($receipt|ConvertTo-Json -Depth 8 -Compress)+"`n")));
        terminal_report_sha256=$receipt.terminal_report_sha256;retry_reason=$receipt.retry_reason;export_manifest_sha256=$receipt.export_manifest_sha256;
        raw_sha256=$receipt.raw_sha256;analysis_manifest_path=$analysis;analysis_manifest_sha256=(HashFile $analysis);
        evidence=@([pscustomobject]@{path=$analysis;sha256=(HashFile $analysis)})}
    $v=Add-B5SessionBinding $v $card $ExpectedB5CardSha256 $receipt.debug_step $receipt.ui_retry
    Write-B5NewJson (Join-Path $attempt 'offline_validation.json') $v
    return $v
}
$c=SessionCard (Card 'session-envelope')
Assert-B5Card $c @{} 1 $c.attempts[0].physical_recovery_epoch 16 16384 85 1 0;Check $true 'SESSION P4b valid schedule and documentary9+2'
Assert-B5Approval $c 'OK P4b';Check $true 'SESSION exact card approval'
foreach ($approvalValue in @('OK B5','OK P4','OK P4B','ok P4b','OK Other','OK P4b ')) { Refuse {Assert-B5Approval $c $approvalValue} ('SESSION approval refuses '+$approvalValue) }
foreach ($fault in @('id','approval','cross-card','case-schema','drive','drive-string','frontend','frontend-auto','optional','optional-string','order','duplicate','p4b-step','p4b-drive','p4b-budget','android13','android-string','samsung','windows','windows-auth','windows-capture','debug7','debug-mismatch','retry-step3','retry-total3','retry-step-string','retry-negative','epoch-reuse','attempt-frontend','legacy')) {
    $c=SessionCard (Card ('session-'+$fault))
    switch ($fault) {
        'id' {$c.card_id='P4b unsafe'}
        'approval' {$c.approval='OK P4'}
        'cross-card' {$c.card_id='Other'}
        'case-schema' {$c.schema='tbs5530-session-card/v1'}
        'drive' {$c.debug_steps[0].ts_drive=86}
        'drive-string' {$c.debug_steps[0].ts_drive='85'}
        'frontend' {$c.debug_steps[0].frontend_experiment='B4_K25'}
        'frontend-auto' {$c.debug_steps[0].frontend_experiment='B5_K25_AUTO'}
        'optional' {$c.debug_steps[1].optional=$true}
        'optional-string' {$c.debug_steps[2].optional='true'}
        'order' {$c.debug_steps=$c.debug_steps[1,0,2]}
        'duplicate' {$c.debug_steps[1].step=1}
        'p4b-step' {$c.debug_steps[2].step=5}
        'p4b-drive' {$c.debug_steps[1].ts_drive=85}
        'p4b-budget' {$c.max_android=10}
        'android13' {$c.max_android=13}
        'android-string' {$c.max_android='9'}
        'samsung' {$c.max_samsung=3}
        'windows' {$c.max_windows=1}
        'windows-auth' {$c.windows_authorized=$true}
        'windows-capture' {$c|Add-Member windows_capture ([pscustomobject]@{authorized=$true})}
        'debug7' {$c.max_debug_attempts=7;$c.max_attempts=7}
        'debug-mismatch' {$c.max_debug_attempts=4}
        'retry-step3' {$c.ui_retry_per_step=3}
        'retry-total3' {$c.ui_retry_total=3}
        'retry-step-string' {$c.ui_retry_per_step='2'}
        'retry-negative' {$c.ui_retry_total=-1}
        'epoch-reuse' {$c.attempts[1].physical_recovery_epoch=$c.attempts[0].physical_recovery_epoch}
        'attempt-frontend' {$c.attempts[0]|Add-Member frontend_experiment 'B5_K25_MANUAL_PLP0'}
        'legacy' {$c|Add-Member carry_forward $null}
    }
    Refuse {Assert-B5Card $c @{} 1 $c.attempts[0].physical_recovery_epoch 16 16384 85 1 0} ('SESSION envelope refuses '+$fault)
}
$c=SessionCard (Card 'session-ceilings') 'Generic'
$c.max_android=10;$c.max_oneplus=10;Assert-B5Card $c @{} 1 $c.attempts[0].physical_recovery_epoch 16 16384 85 1 0;Check $true 'SESSION generic total Android12 ceiling allowed'
$c.max_android=11;$c.max_oneplus=11;Refuse {Assert-B5Card $c @{} 1 $c.attempts[0].physical_recovery_epoch 16 16384 85 1 0} 'SESSION OnePlus11 plus Samsung2 exceeds total12'
$c.max_android=10;$c.max_oneplus=10
Refuse {Assert-B5Card $c @{} 1 $c.attempts[0].physical_recovery_epoch 16 16384 84 1 0} 'SESSION invocation drive substitution'
Refuse {Assert-B5Card $c @{} 1 $c.attempts[0].physical_recovery_epoch 16 16384 85 0 0} 'SESSION explicit step required'
Refuse {Assert-B5Card (Card 'legacy-no-retry') @{} 1 $epoch 16 16384 85 1 1} 'legacy schemas cannot inherit SESSION retry'

# Main-path SESSION tests use the real durable ledger, reviews and source/hash guards.
$script:fakeFault='session-ui';$FrontendExperiment='B5_K25_MANUAL_PLP0';SetupSessionIntegration 'session-retry-flow'
$calls=$script:fakeCalls;$savedApproval=$Approval;$Approval='OK P4B'
Refuse {& {Invoke-Expression $main}} 'SESSION main case-sensitive approval before I/O'
Check ($script:fakeCalls -eq $calls) 'SESSION wrong approval no fake transport'
$Approval=$savedApproval
$null=& {Invoke-Expression $main}
$rows=SessionRows;$r=$rows[-1]
Check ($r.retry_reason -ceq 'UI_ABORT_BEFORE_RELOAD' -and $r.drained -and -not $r.session_closed -and -not $r.capture_complete) 'SESSION real main normalizes UI abort with typed clean132 drain'
Check ($rows[0].max_android -eq 9 -and $rows[0].max_samsung -eq 2 -and $rows[0].max_debug_attempts -eq 5 -and $rows[0].card_id -ceq 'P4b') 'SESSION header records documentary production budgets honestly'
SelectSessionAttempt 2 1 1;$calls=$script:fakeCalls
Refuse {& {Invoke-Expression $main}} 'SESSION UI retry requires real offline review'
Check ($script:fakeCalls -eq $calls) 'SESSION missing review no transport'
$v=WriteSessionReview 1
$review=Read-B5Review $card $ExpectedB5CardSha256 $r 1
Check ($review.result -ceq 'UI_ABORT_BEFORE_RELOAD') 'SESSION real retry review accepted with receipt/terminal/export/analysis hashes'
$wrongReceipt=$r.PSObject.Copy();$wrongReceipt.ts_drive=84
Refuse {Read-B5SessionTerminal $card $wrongReceipt 1} 'SESSION direct terminal review also rejects receipt drive substitution'
$validationPath=Join-Path $card.budget_directory 'attempt-1\offline_validation.json'
$goodValidation=[IO.File]::ReadAllBytes($validationPath)
foreach ($fault in @('card-id','card-hash','schema','step','retry','drive','frontend','golden','receipt','terminal','reason','verdict','pending')) {
    $v=[Text.Encoding]::UTF8.GetString($goodValidation)|ConvertFrom-Json
    switch ($fault) {
        'card-id' {$v.card_id='P4B'}
        'card-hash' {$v.card_sha256='0'*64}
        'schema' {$v.schema='TBS5530-B5-OFFLINE-VALIDATION/v1'}
        'step' {$v.debug_step=2}
        'retry' {$v.ui_retry=1}
        'drive' {$v.ts_drive=84}
        'frontend' {$v.frontend_experiment='B5_K36_MANUAL_PLP0'}
        'golden' {$v.golden_sha256='0'*64}
        'receipt' {$v.receipt_sha256='0'*64}
        'terminal' {$v.terminal_report_sha256='0'*64}
        'reason' {$v.retry_reason='PERMISSION_BEFORE_RELOAD'}
        'verdict' {$v.result='B5_REVIEWED'}
        'pending' {$v.result='PENDING_ANALYSIS'}
    }
    [IO.File]::WriteAllText($validationPath,($v|ConvertTo-Json -Depth 12),[Text.UTF8Encoding]::new($false))
    Refuse {Read-B5Review $card $ExpectedB5CardSha256 $r 1} ('SESSION review rejects '+$fault)
}
[IO.File]::WriteAllBytes($validationPath,$goodValidation)
$terminalPath=Join-Path $card.budget_directory 'attempt-1\terminal-report.json'
$terminalBytes=[IO.File]::ReadAllBytes($terminalPath);[IO.File]::AppendAllText($terminalPath,' ')
Refuse {& {Invoke-Expression $main}} 'SESSION terminal changed blocks retry'
[IO.File]::WriteAllBytes($terminalPath,$terminalBytes)
$script:fakeFault='session-permission';$null=& {Invoke-Expression $main};$rows=SessionRows
Check ($rows[-1].debug_step -eq 1 -and $rows[-1].ui_retry -eq 1 -and $rows[-1].ts_drive -eq 85 -and $rows[-1].retry_reason -ceq 'PERMISSION_BEFORE_RELOAD') 'SESSION same-step same-drive first UI retry consumed ordinal2'
$null=WriteSessionReview 2
SelectSessionAttempt 3 1 2;$script:fakeFault='';$null=& {Invoke-Expression $main};$null=WriteSessionReview 3
$rows=SessionRows
Check ($rows[-1].ui_retry -eq 2 -and $rows[-1].capture_complete) 'SESSION second retry succeeds without new step/drive'
SelectSessionAttempt 4 1 2;$calls=$script:fakeCalls
Refuse {& {Invoke-Expression $main}} 'SESSION cannot repeat retry ordinal on successful capture'
Check ($script:fakeCalls -eq $calls) 'SESSION retry cap failure before transport'
SelectSessionAttempt 4 2 0;$null=& {Invoke-Expression $main};$null=WriteSessionReview 4
$rows=SessionRows
Check ($rows[-1].ts_drive -eq 84 -and $rows[-1].frontend_experiment -ceq 'B5_K36_MANUAL_PLP0' -and $rows[-2].debug_step -eq 2) 'SESSION step2 drive84 K36 pinned launch and ledger'
SelectSessionAttempt 5 2 1;$calls=$script:fakeCalls
Refuse {& {Invoke-Expression $main}} 'SESSION total retry cap across steps before transport'
SelectSessionAttempt 5 4 0
Refuse {& {Invoke-Expression $main}} 'SESSION optional step4 explicit selection required'
Check ($script:fakeCalls -eq $calls) 'SESSION optional refusal no transport'
$ApproveSessionOptionalStep=$true;$null=& {Invoke-Expression $main};$null=WriteSessionReview 5
$rows=SessionRows
Check ($rows.Count -eq 11 -and $rows[-1].debug_step -eq 4 -and $rows[-1].ts_drive -eq 85 -and $script:grantCalls -eq 5) 'SESSION P4b five attempts include two retries and optional drive85'
$ledgerFile=Join-Path $card.budget_directory 'b5-session-manifest.jsonl';$ledgerBytes=[IO.File]::ReadAllBytes($ledgerFile)
Refuse {Assert-B5Ledger $ledgerBytes $ExpectedB5CardSha256 $card 6 4 1} 'SESSION five-slot budget cannot invent sixth attempt'
Refuse {Assert-B5Ledger $ledgerBytes ('b'*64) $card 5 4 0} 'SESSION cross-card ledger hash refused'
foreach ($fault in @('header-id','header-schedule','header-limit-type','consume-step','consume-retry','consume-drive','consume-epoch','consume-cold','receipt-id','receipt-step','receipt-retry','receipt-drive','receipt-terminal','receipt-ordinal-type','receipt-depth-type')) {
    $changed=$rows|ConvertTo-Json -Depth 20|ConvertFrom-Json
    switch ($fault) {
        'header-id' {$changed[0].card_id='P4B'}
        'header-schedule' {$changed[0].schedule_sha256='0'*64}
        'header-limit-type' {$changed[0].max_android='9'}
        'receipt-ordinal-type' {$changed[2].ordinal='1'}
        'receipt-depth-type' {$changed[2].queue_depth='16'}
        'consume-step' {$changed[3].debug_step=2}
        'consume-retry' {$changed[3].ui_retry=0}
        'consume-drive' {$changed[3].ts_drive=84}
        'consume-epoch' {$changed[3].epoch=$changed[1].epoch}
        'consume-cold' {$changed[3].cold_seconds=4}
        'receipt-id' {$changed[2].card_id='Other'}
        'receipt-step' {$changed[2].debug_step=2}
        'receipt-retry' {$changed[2].ui_retry=1}
        'receipt-drive' {$changed[2].ts_drive=84}
        'receipt-terminal' {$changed[2].terminal_report_sha256='0'*64}
    }
    $forged=[byte[]]@()
    foreach ($row in $changed) { $row.PSObject.Properties.Remove('previous_sha256');$forged=TamperedLedgerRow $forged $row }
    Refuse {Assert-B5Ledger $forged $ExpectedB5CardSha256 $card 5 4 0} ('SESSION rehashed forgery rejected '+$fault)
}

# The retry classifier must reject uncertainty even when a human labels it UI.
$ui=[Text.Encoding]::UTF8.GetString($terminalBytes)|ConvertFrom-Json
foreach ($fault in @('partial','uncertain','firmware-missing','firmware-type','firmware-cleanup','firmware-cause','reload','reload-zero','detach','usb75','usb71','usb32','errno-string','drain','drain-type','phases','native','ts','reason','case','no-cleanup')) {
    $r=$ui|ConvertTo-Json -Depth 20|ConvertFrom-Json
    switch ($fault) {
        'partial' {$r.firmware.completed=131}
        'uncertain' {$r.firmware.state='UNCERTAIN'}
        'firmware-missing' {$r.PSObject.Properties.Remove('firmware')}
        'firmware-type' {$r.firmware.attempted='132'}
        'firmware-cleanup' {$r.firmware.cleanup_failure='IOException'}
        'firmware-cause' {$r.firmware.stop_cause='UNKNOWN'}
        'reload' {$r.fx2_reload=[pscustomobject]@{state='COMPLETE';attempted=132;completed=132;detach_ns=-1}}
        'reload-zero' {$r.fx2_reload=[pscustomobject]@{state='PREPARED';attempted=0;completed=0;detach_ns=-1}}
        'detach' {$r.result='DETACHED'}
        'usb75' {$r.last_errno=75}
        'usb71' {$r.last_errno=-71}
        'usb32' {$r.last_errno=32}
        'errno-string' {$r.last_errno='0'}
        'drain' {$r.cleanup='DRAIN_NOT_CERTIFIED'}
        'drain-type' {$r.terminal='true'}
        'phases' {$r.b5_phases=@((Phases)[0])}
        'native' {$r.native_stream=[pscustomobject]@{outstanding=0;quarantined=$false}}
        'ts' {$r.ts_bytes=1}
        'reason' {$r.stop_reason='OWNER_OR_PERMISSION_CHANGED'}
        'case' {$r.stop_reason='ui_abort_before_reload'}
        'no-cleanup' {$r.PSObject.Properties.Remove('cleanup')}
    }
    $refused=$false;try {$refused=$null -eq (Get-B5UiRetryReason $r)}catch{$refused=$true}
    Check $refused ('SESSION retry predicate refuses '+$fault)
}
$r=$ui|ConvertTo-Json -Depth 20|ConvertFrom-Json;$r.firmware=$null
Check ((Get-B5UiRetryReason $r) -ceq 'UI_ABORT_BEFORE_RELOAD') 'SESSION verified no-bootstrap drained UI refusal permitted'

# Independent cap checks do not stub review or device safeguards.
$c=SessionCard (Card 'caps')
$c.ui_retry_per_step=1;$c.max_attempts=4;$c.max_debug_attempts=4;$c.attempts=$c.attempts[0..3]
Refuse {Assert-B5SessionTransition $c ([pscustomobject]@{debug_step=1;ui_retry=1;retry_reason='UI_ABORT_BEFORE_RELOAD';capture_complete=$false}) 1 2 2} 'SESSION per-step cap1 prevents second retry'
$c.ui_retry_per_step=2;$c.ui_retry_total=1
Refuse {Assert-B5SessionTransition $c ([pscustomobject]@{debug_step=2;ui_retry=0;retry_reason='UI_ABORT_BEFORE_RELOAD';capture_complete=$false}) 2 1 2} 'SESSION total cap1 applies across steps'
Refuse {Assert-B5SessionTransition $c $null 2 0 0} 'SESSION cannot skip first schedule step'
Refuse {Assert-B5SessionTransition $c ([pscustomobject]@{debug_step=1;ui_retry=0;retry_reason='UI_ABORT_BEFORE_RELOAD';capture_complete=$false}) 2 0 0} 'SESSION UI abort cannot advance drive'
Refuse {Assert-B5SessionTransition $c ([pscustomobject]@{debug_step=1;ui_retry=0;retry_reason=$null;capture_complete=$true}) 4 0 0} 'SESSION cannot jump past mandatory step2'

foreach ($fault in @('session-partial','session-uncertain','session-usb','session-reload','session-drain','permission')) {
    $FrontendExperiment='B5_K25_MANUAL_PLP0';SetupSessionIntegration ('session-stop-'+$fault);$script:fakeFault=$fault
    try {$null=& {Invoke-Expression $main}}catch{}
    $rows=SessionRows;Check ($null -eq $rows[-1].retry_reason) ('SESSION main no retry reason for '+$fault)
    SelectSessionAttempt 2 1 1;$calls=$script:fakeCalls
    Refuse {& {Invoke-Expression $main}} ('SESSION main blocks successor '+$fault)
    Check ($script:fakeCalls -eq $calls) ('SESSION unsafe successor no transport '+$fault)
}
$script:fakeFault='';$FrontendExperiment='B5_K25_MANUAL_PLP0';SetupSessionIntegration 'session-windows-file'
$null=New-Item -ItemType Directory -Path $card.session_directory
[IO.File]::WriteAllText((Join-Path $card.session_directory 'b5-windows-budget.jsonl'),'{}')
$calls=$script:fakeCalls;Refuse {& {Invoke-Expression $main}} 'SESSION rejects Windows ledger even if metadata says0'
Check ($script:fakeCalls -eq $calls) 'SESSION Windows ledger no transport'
# Six debug slots are possible for another reviewed schedule: four steps + two UI retries.
$FrontendExperiment='B5_K25_MANUAL_PLP0';$script:fakeFault='';SetupSessionIntegration 'session-six-live-fake'
$card.card_id='SixBound';$card.approval='OK SixBound';$card.max_attempts=6;$card.max_debug_attempts=6
$card.attempts+=(SessionCard (Card 'six-slot') 'SixBound' 6).attempts[5]
$last=$card.debug_steps[2]|ConvertTo-Json -Depth 12|ConvertFrom-Json;$last.step=6;$last.optional=$false;$card.debug_steps+=,$last
Write-B5NewJson ($B5CardPath+'.six') $card;$B5CardPath+='.six';$ExpectedB5CardSha256=HashFile $B5CardPath;$Approval='OK SixBound';$ApproveSessionOptionalStep=$true
foreach ($selection in @(@(1,1,0,'session-ui'),@(2,1,1,''),@(3,2,0,'session-ui'),@(4,2,1,''),@(5,4,0,''),@(6,6,0,''))) {
    SelectSessionAttempt $selection[0] $selection[1] $selection[2];$script:fakeFault=$selection[3]
    $null=& {Invoke-Expression $main};$null=WriteSessionReview $selection[0]
}
$rows=SessionRows
Check ($rows.Count -eq 13 -and $rows[-1].ordinal -eq 6 -and $script:grantCalls -eq 6) 'SESSION six actual fake grants stay inside app ordinal/max1..6'
Refuse {Assert-B5Ledger ([IO.File]::ReadAllBytes((Join-Path $card.budget_directory 'b5-session-manifest.jsonl'))) $ExpectedB5CardSha256 $card 7 6 1} 'SESSION seventh debug attempt refused after six durable consumes'

# Offline validator checks real local source/helper/APK fixture hashes; only firmware hash is mocked.
$localCard=$card|ConvertTo-Json -Depth 20|ConvertFrom-Json
$canonicalFirmware='b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc'
$localCard.firmware_sha256=$canonicalFirmware
$localApk=Join-Path $root 'local-pin-apk-fixture.bin';[IO.File]::WriteAllBytes($localApk,$evidenceBytes);$localCard.app_apk_sha256=HashFile $localApk
$localCardPath=Join-Path $root 'local-pin-card.json';Write-B5NewJson $localCardPath $localCard;$localHash=HashFile $localCardPath
$calls=$script:fakeCalls
Refuse {Assert-B5LocalPins $localCardPath $localHash $localCard.approval $repository $fakeFirmware $localApk 1 1 0 85 $localCard.debug_steps[0].golden_sha256 $localCard.debug_steps[0].reload_plan_sha256} 'offline validator refuses synthetic firmware without mock'
function Get-FileHash([string]$LiteralPath,[string]$Algorithm) {
    $v=Microsoft.PowerShell.Utility\Get-FileHash -LiteralPath $LiteralPath -Algorithm $Algorithm
    if ($LiteralPath -ceq $fakeFirmware) { $v.Hash=$canonicalFirmware }
    return $v
}
try {
    $v=Assert-B5LocalPins $localCardPath $localHash $localCard.approval $repository $fakeFirmware $localApk 1 1 0 85 $localCard.debug_steps[0].golden_sha256 $localCard.debug_steps[0].reload_plan_sha256
    Check ($v.status -ceq 'LOCAL_PINS_VERIFIED' -and $v.device_io -ceq 'NOT_RUN') 'offline pin entry reaches verified local source/helpers/APK with explicit fake firmware hash only'
    foreach ($fault in @('cardhash','approval','helper','apk','source','golden','drive')) {
        $c=$localCard|ConvertTo-Json -Depth 20|ConvertFrom-Json
        $h=$localHash;$a=$c.approval;$gold=$c.debug_steps[0].golden_sha256;$drive=85
        switch ($fault) {
            'cardhash' {$h='0'*64}
            'approval' {$a='OK sixbound'}
            'helper' {$c.b5_helper_sha256='0'*64}
            'apk' {$c.app_apk_sha256='0'*64}
            'source' {$c.source_git_sha='0'*40}
            'golden' {$gold='0'*64}
            'drive' {$drive=84}
        }
        $p=$localCardPath+'.'+$fault;Write-B5NewJson $p $c
        if ($fault -cne 'cardhash') { $h=HashFile $p }
        Refuse {Assert-B5LocalPins $p $h $a $repository $fakeFirmware $localApk 1 1 0 $drive $gold $c.debug_steps[0].reload_plan_sha256} ('offline validator real local guard '+$fault)
    }
} finally { Remove-Item Function:Get-FileHash }
Check ($script:fakeCalls -eq $calls) 'offline validator never executes main or device transport'
$DebugStep=0;$UiRetry=0;$ApproveSessionOptionalStep=$false;$B5AttemptOrdinal=1;$B5TsDrive=85;$Approval='OK B5'
foreach ($scriptPath in @($path,$PSCommandPath,$b3)) {
    $b=[IO.File]::ReadAllBytes($scriptPath);$text=[Text.Encoding]::UTF8.GetString($b)
    Check (($b[0..2]-join ',') -ceq '239,187,191' -and (-not $text.Contains("`r") -or -not [regex]::IsMatch($text,'(?<!\r)\n'))) 'BOM and consistent LF or CRLF preserved'
}
$summary=[pscustomobject]@{status='PASS';checks=$checks;real_adb_calls=0;fake_transport_calls=$script:fakeCalls;binary_exports=$script:binaryExports;
    b5_helper_sha256=(HashFile $path);b3_helper_sha256=$ExpectedB3HelperSha256;hardware='NOT_RUN';vm='NOT_RUN';network='NOT_RUN';verdict_created_by_helper=$false}
$contract=@'
HỢP ĐỒNG HELPER SESSION — chỉ offline/fake đã kiểm; parent tạo card thật mới, không sửa lịch sử.

Files: tools\Invoke-TBS5530-B5Session.ps1 và tools\Test-TBS5530-B5Session.ps1. Không app/docs/toolchain/hardware/sync.
Nguồn chính xác của contract: function_lines trong contract.json, trích AST hiện hành.

SCHEMA CARD
Giữ toàn bộ pin B5 thật: session_id/budget_id GUID mới; serial/model=GM1900/manufacturer=OnePlus/api=30/boot_count;
source_git_sha, source_manifest_path dưới D:\TSDuck\reports, source_manifest_sha256 (manifest source_git_sha và files [{path relative,sha256}], 1..512);
app_apk_sha256, autonomous_helper_sha256, b3_helper_sha256, b5_helper_sha256, firmware_sha256;
fx2_reload_store_sha256, fx2_reload_anchor_sha256; giữ witness thật, không tự reset store.
budget_directory=session_directory dưới D:\TSDuck\logs, basename b5-budget-<budget_id>;
raw_directory dưới D:\TSDuck; report_directory dưới D:\TSDuck\reports;
live_date yyyy-MM-dd/time_zone_id/utc_offset_minutes đúng ngày; rf_input=RF_IN, antenna PASSIVE hoặc EXTERNALLY_POWERED_NO_TUNER_DC, antenna_dc=false;
layout=OFF1:10000,ON1:30000,OFF2:10000,ON2:10000; stale_prefix_bytes=2048; monitor_period_ms=100; monitor_jitter_ms=20;
rssi_samples_per_off=3; rssi_sequence=WINDOWS_88_00_87_C0; max_bytes=268435456; duration_ms=30000; ttl_ms=300000; heartbeat_ms=2000; permission_wait_ms=30000.
Thay schema=TBS5530-SESSION-CARD/v1, card_id=P4b, approval=OK P4b đúng hoa/thường.
max_attempts=max_debug_attempts=5; max_android=9 là OnePlus tổng debug+production; max_oneplus=9 (nếu có phải bằng max_android), max_samsung=2; tổng 11<=12.
max_windows=0/windows_authorized=false; ui_retry_per_step=2/ui_retry_total=2. Không cần phase4_scope/max_android_debug; nếu còn chỉ tài liệu, không thay quyền SESSION.
Không carry_forward/target_migration/one_extra_extension hoặc Windows capture authorized.
debug_steps gồm đúng [{step:1,ts_drive:85,optional:false},{step:2,ts_drive:84,optional:false},{step:4,ts_drive:85,optional:true}].
Mỗi step thêm frontend_experiment B5_K25_MANUAL_PLP0 hoặc B5_K36_MANUAL_PLP0, golden_sha256/reload_plan_sha256 thật, allow_k25_auto true cho K25/false cho K36.
K25=506000000 Hz, K36=594000000 Hz, bandwidth 8000000; optional frequency_hz/bandwidth_hz nếu có phải đúng. Không allowed_frontends, không AUTO launch riêng.
attempts có đúng 5 slot [{ordinal:1..5, physical_recovery_epoch:GUID riêng, queue_depth:16,urb_bytes:16384,notes:{separation,cable,accessories}}].
Có thể dùng allowed_receive_configs=[{queue_depth:16,urb_bytes:16384}] thay config trực tiếp. Mỗi note 1..240 ký tự.
Slot KHÔNG chứa ts_drive/frontend_experiment/allowed_frontends/golden_sha256/reload_plan_sha256/debug_step/ui_retry: chúng lấy từ debug_steps lúc chọn.
Mỗi slot là một lần chạy, kể cả abort; cần cold>=5 do người dùng xác nhận. Không tái sử dụng epoch hoặc slot khi retry.
P4b tối đa hữu ích 5 lượt (3 step+2 retry). Schema chung cho phép 6 nếu có >=4 step và tổng retry<=2; max Android cả hai thiết bị<=12.

TẠO CARD THẬT — LỆNH MẪU, KHÔNG CHẠY BỞI TEST
$c=Get-Content -LiteralPath $ReviewedSuccessorPath -Raw|ConvertFrom-Json
$c|Add-Member schema 'TBS5530-SESSION-CARD/v1' -Force; $c|Add-Member card_id 'P4b' -Force; $c.approval='OK P4b'
$c.session_id=[guid]::NewGuid().ToString();$c.budget_id=[guid]::NewGuid().ToString()
$c.budget_directory=Join-Path $FreshLogRoot ('b5-budget-'+$c.budget_id);$c.session_directory=$c.budget_directory
$c.raw_directory=$FreshRawRoot;$c.report_directory=$FreshReportRoot
foreach($p in @{max_attempts=5;max_debug_attempts=5;max_android=9;max_oneplus=9;max_samsung=2;max_windows=0;windows_authorized=$false;ui_retry_per_step=2;ui_retry_total=2}.GetEnumerator()){$c|Add-Member $p.Key $p.Value -Force}
$c|Add-Member debug_steps $ReviewedStepsWithRealPlanHashes -Force
$c.attempts=@(foreach($n in 1..5){[pscustomobject]@{ordinal=$n;physical_recovery_epoch=[guid]::NewGuid().ToString();queue_depth=16;urb_bytes=16384;notes=$ReviewedNotes}})
# Parent cập nhật helper/APK/source manifest hashes/ngày/boot/witness thật, kiểm nguồn và duyệt hash mới; không dùng fake fixture ở suite này.
# Write-B5NewJson dùng CreateNew, từ chối ghi đè; đặt NewCardPath mới dưới D:\TSDuck\reports.
Write-B5NewJson $NewCardPath $c
$CardHash=(Get-FileHash -LiteralPath $NewCardPath -Algorithm SHA256).Hash.ToLowerInvariant()

NẠP VALIDATOR KHÔNG MAIN/DEVICE
$helper=Join-Path $Repository 'tools\Invoke-TBS5530-B5Session.ps1';$t=$null;$e=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($helper,[ref]$t,[ref]$e);if($e.Count){throw 'parse failed'}
foreach($f in @($ast.EndBlock.Statements|Where-Object {$_ -is [Management.Automation.Language.FunctionDefinitionAst]})){Invoke-Expression $f.Extent.Text}
$b3=Join-Path $Repository 'tools\Invoke-TBS5530-B3Session.ps1'
Import-B5SharedGuards $b3 ((Get-FileHash -LiteralPath $b3 -Algorithm SHA256).Hash.ToLowerInvariant());Set-B5Overrides
Assert-B5LocalPins -CardPath $NewCardPath -CardHash $CardHash -ApprovalValue 'OK P4b' -Repository $Repository -FirmwareFile $RealFirmware -ApkFile $RealDebugApk -Ordinal 1 -Step 1 -Retry 0 -Drive 85 -GoldenHash $RealK25Drive85Golden -ReloadHash $RealK25Drive85Reload
# Return LOCAL_PINS_VERIFIED; kiểm thật card/source/helper/APK/firmware, không gọi B5_MAIN hoặc ADB.
# Không dot-source cả script. Validator local chưa xác minh phone/boot/OS permission, chưa consume ledger hoặc thay live grant.
# Không mock hash ngoài fake tests. Parent tự kiểm serial/boot/day/witness và pin app mới; LOCAL_PINS_VERIFIED không thay live guards.

LỊCH VÀ DURABLE LEDGER
Mỗi invocation live có -B5AttemptOrdinal N -DebugStep S -UiRetry R -B5TsDrive D và tất cả switches/hash guards cũ.
Bắt đầu N1/S1/R0/D85. Ví dụ không retry: N1/S1, N2/S2/D84, N3/S4/D85 (optional cần -ApproveSessionOptionalStep).
Ví dụ retry: N1/S1/R0 abort, N2/S1/R1 abort, N3/S1/R2 capture, N4/S2/R0 capture, N5/S4/R0 capture.
Step hoặc drive/plan KHÔNG đổi khi retry; N/epoch tăng; mỗi bước tối đa 2 retry, tổng 2; app grant ordinal/max luôn 1..6.
Helper không tự chạy bước/retry tiếp, không Samsung/release I/O. Production budgets chỉ được parent ghi nhận lúc parent thật sự thực thi, helper không giả enforce production.
Ledger b5-session-manifest.jsonl mở exclusive FileShare.None, chain previous_sha256, flush(true) và readback trước first device command.
HEADER/CONSUME/RECEIPT bind card_schema/card_id/card_sha256/budget_id/session_id/schedule_sha256/debug_step/ui_retry.
HEADER step/retry=0; thêm max_attempts,max_debug_attempts,max_android,max_oneplus,max_samsung,max_windows,ui_retry_per_step,ui_retry_total.
CONSUME: ordinal/epoch/ts_drive/frontend_experiment/golden_sha256/reload_plan_sha256/cold confirmation/config và previous_offline_validation_sha256.
RECEIPT: cùng binding, result/drained/session_closed/capture_complete/raw/export hashes; terminal_report_sha256 và retry_reason.
Thiếu receipt/drain, chain lệch, changed source/card/schedule/plan/review hoặc pending analysis thì không có successor. Không reset/rearm hoặc dùng card mới trên budget cũ.

RETRY VÀ REVIEW
Chỉ terminal typed DRAIN_CONFIRMED, cleanup_path RUNTIME_DRAIN_CONFIRMED/NO_RESOURCES_ACQUIRED, không reload (fx2_reload=null), không native/phases/TS;
firmware=null hoặc OPERATIONAL_CANDIDATE attempted=completed=132, cleanup_failure=stop_cause=NONE.
stop_reason UI_ABORT_BEFORE_RELOAD hoặc ATTACH_PERMISSION_UI_TIMEOUT => UI_ABORT_BEFORE_RELOAD;
PERMISSION_BEFORE_RELOAD hoặc PERMISSION_DENIED => PERMISSION_BEFORE_RELOAD. Không suy reason từ nhãn host hay ý kiến reviewer.
Không retry firmware partial/uncertain, USB error/detach sau reload, drain unconfirmed hoặc host permission-only refusal không có Android drain.
Với SESSION, receipt không đủ capture và không có retry reason sẽ đóng session; không chuyển drive để né abort.
Trước MỌI invocation sau N1 cần attempt-<N-1>\offline_validation.json thật, kể cả retry.
schema=TBS5530-SESSION-OFFLINE-VALIDATION/v1, các binding SESSION như receipt, ordinal/physical_recovery_epoch/ts_drive/frontend_experiment/golden_sha256/reload_plan_sha256.
thêm receipt_sha256=SHA256 UTF8(JSON receipt ConvertTo-Json -Compress -Depth8 + LF), bao gồm previous_sha256; terminal_report_sha256; retry_reason.
result UI_ABORT_BEFORE_RELOAD/PERMISSION_BEFORE_RELOAD phải đúng classifier; capture dùng B5_REVIEWED/RF_LIMITED/B5_ANDROID_GAP. PENDING_ANALYSIS chặn.
export_manifest_sha256/raw_sha256 phải khớp receipt và file; retry không raw dùng null. analysis_manifest_path dưới report_directory + analysis_manifest_sha256;
evidence=[{path,sha256}]1..32, path nằm report_directory hoặc attempt dir. Các file xuất size/hash được đọc lại. Không tạo review giả để mở live.
Legacy B5/P4 approvals/budgets/carry/extensions/drive/phase guards giữ nguyên; chỉ test line endings cho phép BOM+LF hoặc BOM+CRLF nhất quán.

GIỚI HẠN
PASS fake không chứng minh USB/TS phần cứng. Không card thật, APK thật hay firmware thật được thực thi ở suite này.
Một test local validator mock đúng firmware hash duy nhất để đi qua fixture; test khác chứng minh firmware synthetic bị từ chối nếu không mock. Main transport luôn fake.
Raw/session/private state không gửi external; không AgentMemory write hoặc graph query. Không stage/commit/push/VM/ADB/hardware/sync/toolchain.
Parent phải tạo card/source manifest/helper/APK pins thật sau khi helper ổn định; lập live approval mới đúng hash, giữ lịch sử nguyên trạng.
'@
$functions=@($ast.FindAll({param($n)$n -is [Management.Automation.Language.FunctionDefinitionAst]},$true)|ForEach-Object {[pscustomobject]@{name=$_.Name;line=$_.Extent.StartLineNumber}})
[IO.File]::WriteAllText((Join-Path $reportRoot 'parent-contract.txt'),$contract,[Text.UTF8Encoding]::new($false))
Write-B5NewJson (Join-Path $reportRoot 'contract.json') ([pscustomobject]@{schema='TBS5530-SESSION-HELPER-CONTRACT/v1';source_git_sha=$head;b5_helper_sha256=(HashFile $path);test_helper_sha256=(HashFile $PSCommandPath);function_lines=$functions;details='parent-contract.txt';tests='test-summary.json'})
Write-B5NewJson (Join-Path $reportRoot 'test-summary.json') $summary
Write-Output ("PASS: B5 $checks/$checks; chỉ fake, không ADB thật/phần cứng/VM/network; evidence: "+$root)
