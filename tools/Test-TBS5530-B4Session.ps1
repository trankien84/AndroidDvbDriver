# SPDX-License-Identifier: GPL-2.0-or-later
# Fake-only: override device transport before evaluating main. Never invokes ADB or hardware.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory,[Parameter(Mandatory)][string]$ReportDirectory)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$path=Join-Path $PSScriptRoot 'Invoke-TBS5530-B4Session.ps1'
$b3=Join-Path $PSScriptRoot 'Invoke-TBS5530-B3Session.ps1'
$runtime=Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
$repository=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors)
if ($errors.Count) { throw 'B4 parse failed' }
foreach ($function in @($ast.EndBlock.Statements|Where-Object {$_ -is [Management.Automation.Language.FunctionDefinitionAst]})) { Invoke-Expression $function.Extent.Text }
$ExpectedB3HelperSha256=(Get-FileHash -LiteralPath $b3 -Algorithm SHA256).Hash.ToLowerInvariant()
Import-B4SharedGuards $b3 $ExpectedB3HelperSha256
Set-B4Overrides
$root=Assert-B4PrivatePath ([IO.Path]::GetFullPath($OutputDirectory)) 'D:\TSDuck\logs'
$reportRoot=Assert-B4PrivatePath ([IO.Path]::GetFullPath($ReportDirectory)) 'D:\TSDuck\reports'
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
    $directory=Join-Path (Join-Path $root $Name) ('budget-'+$budget)
    return [pscustomobject]@{schema='TBS5530-B4-CARD/v1';approval='OK B4';session_id=$session;budget_id=$budget;max_attempts=4;max_android=4;max_windows=3;
        budget_directory=$directory;session_directory=$directory;raw_directory=(Join-Path $root ($Name+'-raw'));report_directory=$reportRoot;
        live_date=$today.ToString('yyyy-MM-dd');time_zone_id='SE Asia Standard Time';utc_offset_minutes=420;
        duration_ms=30000;max_bytes=268435456;tuner_rssi=$false;rf_input='RF_IN';antenna='PASSIVE';antenna_dc=$false;
        attempts=@([pscustomobject]@{ordinal=1;physical_recovery_epoch=$epoch;allowed_receive_configs=@([pscustomobject]@{queue_depth=16;urb_bytes=16384},[pscustomobject]@{queue_depth=32;urb_bytes=16384})},
            [pscustomobject]@{ordinal=2;physical_recovery_epoch=$epoch2;queue_depth=32;urb_bytes=16384},
            [pscustomobject]@{ordinal=3;physical_recovery_epoch='44444444-4444-4444-8444-444444444444';queue_depth=16;urb_bytes=16384},
            [pscustomobject]@{ordinal=4;physical_recovery_epoch='55555555-5555-4555-8555-555555555555';queue_depth=32;urb_bytes=16384})}
}
$card=Card
Assert-B4Card $card @{} 1 $epoch 16 16384;Check $true 'valid four-attempt same-day B4 card'
Assert-B4Card $card @{} 1 $epoch 32 16384;Check $true 'listed depth32 admitted'
foreach ($fault in @('approval','android5','windows4','budget5','budget-string','duplicate-epoch','ordinal','chunk','depth','rssi-string','duration61','duration-string','bytes','antenna-dc','wrong-rf','wrong-antenna','yesterday','tomorrow','offset','timezone','uuid-path')) {
    $c=Card
    switch ($fault) {
        'approval' {$c.approval='OK B3'}
        'android5' {$c.max_android=5}
        'windows4' {$c.max_windows=4}
        'budget5' {$c.max_attempts=5}
        'budget-string' {$c.max_attempts='4'}
        'duplicate-epoch' {$c.attempts[1].physical_recovery_epoch=$epoch}
        'ordinal' {$c.attempts[1].ordinal=3}
        'chunk' {$c.attempts[0].allowed_receive_configs[0].urb_bytes=65536}
        'depth' {$c.attempts[0].allowed_receive_configs[0].queue_depth=8}
        'rssi-string' {$c.tuner_rssi='false'}
        'duration61' {$c.duration_ms=60001}
        'duration-string' {$c.duration_ms='30000'}
        'bytes' {$c.max_bytes=268435457}
        'antenna-dc' {$c.antenna_dc=$true}
        'wrong-rf' {$c.rf_input='LNB'}
        'wrong-antenna' {$c.antenna='TUNER_POWERED'}
        'yesterday' {$c.live_date=$today.AddDays(-1).ToString('yyyy-MM-dd')}
        'tomorrow' {$c.live_date=$today.AddDays(1).ToString('yyyy-MM-dd')}
        'offset' {$c.utc_offset_minutes=0}
        'timezone' {$c.time_zone_id='NOT_A_TIME_ZONE'}
        'uuid-path' {$c.budget_directory=Join-Path $root ('budget-'+$session)}
    }
    Refuse {Assert-B4Card $c @{} 1 $epoch 16 16384} ('card '+$fault)
}
$c=Card;$c.duration_ms=60000;$c.tuner_rssi=$true;$c.antenna='EXTERNALLY_POWERED_NO_TUNER_DC'
Assert-B4Card $c @{duration_ms=60000;tuner_rssi=$true} 1 $epoch 16 16384;Check $true 'max60s explicit RSSI true and externally powered antenna'
Refuse {Assert-B4Card $c @{tuner_rssi=$false} 1 $epoch 16 16384} 'RSSI invocation must match card'
Refuse {Assert-B4Card $card @{} 1 $epoch2 16 16384} 'epoch binding'
Refuse {Assert-B4Card $card @{} 2 $epoch2 16 16384} 'unlisted depth rejected'
Assert-B4LiveDay $card $today;Check $true 'time zone date boundary uses approved live date'
Refuse {Assert-B4LiveDay $card $today.AddDays(1)} 'midnight requires new approval card'
$ExpectedModel='SM-X926B';$ExpectedApi=36;$ExpectedBootCount=47
$package='info.martinmarinov.dvbdriver';$firmwareSha='b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc'
$ExpectedFrontendGoldenSha256='75875b84fc3f959b35d436560ae19ccf7e52b890173f1f5e24984367f64fe228'
$ExpectedReloadPlanSha256='9add00e12a7fbea9aa4877451db4a28077c64872cd73e6eb91f04facf1fec650'
$ExpectedAppApkSha256=$hash;$B4QueueDepth=16;$B4UrbBytes=16384;$B4DurationMillis=30000;$B4MaxBytes=268435456;$B4TunerRssi=$false
$card|Add-Member source_git_sha ('9'*40)
function Challenge {
    $header="TBS5530-FX2-RELOAD/v1`nHOST samsung-sm-x926b-api36`nINITIAL_HISTORY_SHA256 b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb`n"
    $h=Get-B4Hash ([Text.Encoding]::UTF8.GetBytes($header))
    [pscustomobject]@{policy='TBS5530-AUTONOMOUS/v1';session=$session;launch_token=$epoch;control_plane_only=$false;live_ready=$true;created_elapsed_ms=1000;
        model='SM-X926B';api=36;boot_count=47;host_profile='samsung-sm-x926b-api36';vid=29516;pid=21808;firmware_sha256=$firmwareSha;firmware_protocol='TBS_SOURCE_DUAL_RESET';
        ts_variant='NATIVE_USBFS_STREAM';frontend_experiment='B2_RELOAD_AFTER_SET_CONFIG';golden_sha256=$ExpectedFrontendGoldenSha256;app_apk_sha256=$hash;
        control_mirror=$true;explicit_set_interface_alt0=$false;sony_ts_output_readback=$false;retry=0;max_upload_per_attachment=1;max_reload_per_physical_epoch=1;
        max_session_ms=300000;heartbeat_ms=2000;fx2_reload_option='B2_RELOAD_AFTER_SET_CONFIG';fx2_reload_plan_sha256=$ExpectedReloadPlanSha256;
        bootstrap_proof='SAME_SESSION_ORIGINAL132_APP_BOUNDARIES_DRAIN_REQUIRED';b3_queue_depth=$B4QueueDepth;b3_urb_bytes=16384;b3_max_bytes=268435456;b3_duration_ms=$B4DurationMillis;
        b4_enabled=$true;b4_duration_ms=$B4DurationMillis;b4_max_bytes=268435456;b4_tuner_rssi=$B4TunerRssi;attachment_history_sha256=$null;fx2_reload_store_sha256=$h;
        fx2_reload_anchor_sha256=(Get-B4Hash ([Text.Encoding]::UTF8.GetBytes("TBS5530-FX2-RELOAD-ANCHOR/v1`n$h`n")));source_git_sha=$card.source_git_sha}
}
Assert-B4Challenge (Challenge) $epoch 1001;Check $true 'strict typed challenge default30s'
foreach ($field in @('b4_enabled','b4_duration_ms','b4_max_bytes','b4_tuner_rssi','b3_queue_depth','b3_urb_bytes','b3_max_bytes','b3_duration_ms','app_apk_sha256','firmware_sha256','golden_sha256','fx2_reload_plan_sha256','heartbeat_ms','max_session_ms','attachment_history_sha256')) {
    $v=Challenge;$v.$field='changed'
    Refuse {Assert-B4Challenge $v $epoch 1001} ('challenge '+$field)
}
$B4TunerRssi=$true;$B4DurationMillis=60000
Assert-B4Challenge (Challenge) $epoch 1001;Check $true 'RSSI true/max duration bound into challenge'
$B4TunerRssi=$false;$B4DurationMillis=30000
function Permission([string]$Token) {
    [pscustomobject]@{version=1;mode='PERMISSION_ONLY';preflight_token=$Token;state='GRANTED';reason='OS_PERMISSION_VERIFIED';vid=29516;pid=21808;
        created_elapsed_ms=1000;deadline_elapsed_ms=61000;elapsed_ms=1001;attachment_generation=1;device_identity_sha256=$hash}
}
Assert-B4PermissionReady (Permission $epoch) $epoch 1002;Check $true 'existing permission-only60s receipt accepted within B4 host30s'
$v=Permission $epoch;$v.elapsed_ms=30999
Assert-B4PermissionReady $v $epoch 30999;Check $true 'permission29999ms'
Refuse {Assert-B4PermissionReady $v $epoch 31000} 'permission30000ms refused'
$v=Permission $epoch;$v.elapsed_ms='1001'
Refuse {Assert-B4PermissionReady $v $epoch 1002} 'permission numeric string refused'
function Report {
    [pscustomobject]@{cleanup='DRAIN_CONFIRMED';state='REPORT';result='STRUCTURE_CONFIRMED';terminal=$true;cleanup_path='RUNTIME_DRAIN_CONFIRMED';
        native_urb=$null;native_stream=[pscustomobject]@{outstanding=0;quarantined=$false;first_status=0;first_errno=0;bytes=32768};firmware=$null;
        fx2_reload=[pscustomobject]@{state='COMPLETE';attempted=132;completed=132;detach_ns=-1};last_errno=0;ts_bytes=32768;raw_capture='SYNCED_CLOSED';
        raw_received_bytes=32768;control_mirror='SYNCED_CLOSED';permission_outcome='AUTO_PERMISSION_HANDLED_BY_OS';pre_native_refusal=$false}
}
foreach ($fault in @('status75','errno75','last75','detach','uncertain','outstanding','quarantine','typed-drain','cleanup')) {
    $r=Report
    switch ($fault) {
        'status75' {$r.native_stream.first_status=-75}
        'errno75' {$r.native_stream.first_errno=75}
        'last75' {$r.last_errno=75}
        'detach' {$r.fx2_reload.detach_ns=0}
        'uncertain' {$r.state='ERROR_UNCERTAIN'}
        'outstanding' {$r.native_stream.outstanding=1}
        'quarantine' {$r.native_stream.quarantined=$true}
        'typed-drain' {$r.terminal='true'}
        'cleanup' {$r.cleanup='UNKNOWN'}
    }
    Check (Test-B4SessionClosed $r) ('whole budget closed '+$fault)
}
$r=Report
$paths=@(Get-B4ExportPaths $session)
Check ($paths -ccontains ('files/phase31-native-stream-'+$session+'.ts')) 'backend raw export path'
Check ($paths -ccontains ('files/phase31-signal-monitor-'+$session+'.jsonl')) 'signal monitor root export path'
$remote='files/autonomous-v1-'+$session+'/received.ts'
Assert-B4ExportCall @('shell','run-as',$package,'wc','-c',$remote) $r $session '';Check $true 'size call exact allowlist'
Assert-B4ExportCall @('exec-out','run-as',$package,'sh','-c',('head -c 268435457 '+$remote)) $r $session 'fake.ts';Check $true 'bounded binary export allowlist'
Refuse {Assert-B4ExportCall @('exec-out','run-as',$package,'sh','-c',("'head -c 268435457 "+$remote+"'")) $r $session 'fake.ts'} 'exec-out rejects literal single quotes around command'
Refuse {Assert-B4ExportCall @('exec-out','run-as',$package,'sh','-c',('"head -c 268435457 '+$remote+'"')) $r $session 'fake.ts'} 'exec-out rejects literal double quotes around command'
Refuse {Assert-B4ExportCall @('exec-out','run-as',$package,'cat',$remote) $r $session 'fake.ts'} 'unbounded cat forbidden in B4 only'
Refuse {Assert-B4ExportCall @('shell','run-as',$package,'wc','-c',($remote+';id')) $r $session ''} 'export shell injection forbidden'
$r.native_stream.outstanding=1
Refuse {Assert-B4ExportCall @('shell','run-as',$package,'wc','-c',$remote) $r $session ''} 'export requires typed drain'
$r=Report;$r.native_stream.bytes=8388608;$r.raw_received_bytes=8388608
$class=Get-B4CaptureClassification $r @([pscustomobject]@{path=$remote;present=$true;bytes=8388608})
Check ($class.capture_complete -and $class.classification -ceq 'PENDING_ANALYSIS' -and -not $class.single_run_pass -and -not $class.final_ts_smoke_pass) 'long capture never manufactures quality verdict'
$r.native_stream.bytes=0;$r.raw_received_bytes=0
$class=Get-B4CaptureClassification $r @([pscustomobject]@{path=$remote;present=$true;bytes=0})
Check ($class.capture_complete -and -not $class.single_run_pass -and $class.classification -ceq 'PENDING_ANALYSIS') 'zero-byte drained evidence is reviewable but never quality pass'

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
        return [pscustomobject]@{exit_code=$process.ExitCode;arguments=@($info.ArgumentList);bytes=$output.Length;sha256=(Get-B4Hash ($output.ToArray()));stderr=$errorText}
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
Check ($goodProbe.exit_code -eq 0 -and $goodProbe.bytes -eq $probeBytes.Length -and $goodProbe.sha256 -ceq (Get-B4Hash $probeBytes)) 'exact argv unquoted command preserves binary output'
Check ($goodProbe.arguments.Count -eq 8 -and $goodProbe.arguments[7] -ceq $probeCommand -and
    $goodProbe.arguments[7] -notmatch '^[\x22\x27]|[\x22\x27]$') 'ProcessStartInfo ArgumentList has one raw command argument without literal quotes'
Write-B4NewJson (Join-Path $reportRoot 'exec-out-argv-probe.json') ([pscustomobject]@{offline_only=$true;device_commands=0;before=$badProbe;after=$goodProbe})

# Real hash/filesystem fixtures for source and offline review; no test consults device state.
$sourceManifest=Join-Path $reportRoot 'source-manifest.json'
$head=(& git -C $repository rev-parse HEAD).Trim()
Write-B4NewJson $sourceManifest ([pscustomobject]@{source_git_sha=$head;files=@([pscustomobject]@{path='tools\Invoke-TBS5530-B4Session.ps1';sha256=(HashFile $path)})})
$sourceCard=Card 'source'
$sourceCard|Add-Member source_git_sha $head
$sourceCard|Add-Member source_manifest_path $sourceManifest
$sourceCard|Add-Member source_manifest_sha256 (HashFile $sourceManifest)
Assert-B4Source $sourceCard $repository;Check $true 'source HEAD and actual file hashes verified'
$sourceCard.source_manifest_sha256='b'*64
Refuse {Assert-B4Source $sourceCard $repository} 'source manifest substitution refused'
function ReviewFixture([object]$ReviewCard,[int]$Ordinal,[string]$Result='B4_ANDROID_GAP') {
    $folder=Join-Path $ReviewCard.budget_directory ('attempt-'+$Ordinal)
    $rawFolder=Join-Path $ReviewCard.raw_directory ('attempt-'+$Ordinal)
    $null=New-Item -ItemType Directory -Path $folder -Force
    $null=New-Item -ItemType Directory -Path $rawFolder -Force
    $rawPath=Join-Path $rawFolder '02-received.ts';[IO.File]::WriteAllBytes($rawPath,[byte[]](0..255))
    $rawSha=HashFile $rawPath
    $exportPath=Join-Path $folder 'export-manifest.json'
    Write-B4NewJson $exportPath @([pscustomobject]@{path=('files/fake/received.ts');present=$true;file='02-received.ts';local_path=$rawPath;bytes=256;sha256=$rawSha})
    $analysis=Join-Path $reportRoot ($ReviewCard.budget_id+'-analysis-'+$Ordinal+'.json')
    Write-B4NewJson $analysis ([pscustomobject]@{fake_only=$true;quality_not_certified=$true})
    $v=[pscustomobject]@{schema='TBS5530-B4-OFFLINE-VALIDATION/v1';card_sha256=$hash;budget_id=$ReviewCard.budget_id;session_id=$ReviewCard.session_id;ordinal=$Ordinal;
        physical_recovery_epoch=$ReviewCard.attempts[$Ordinal-1].physical_recovery_epoch;result=$Result;raw_sha256=$rawSha;export_manifest_sha256=(HashFile $exportPath);
        analysis_manifest_path=$analysis;analysis_manifest_sha256=(HashFile $analysis);evidence=@([pscustomobject]@{path=$analysis;sha256=(HashFile $analysis)})}
    Write-B4NewJson (Join-Path $folder 'offline_validation.json') $v
    return [pscustomobject]@{event='RECEIPT';ordinal=$Ordinal;session_closed=$false;drained=$true;result='INVALID_TS';capture_complete=$true;
        single_run_pass=$false;classification='PENDING_ANALYSIS';pre_io_refusal=$false;queue_depth=$(if($Ordinal -eq 1){16}else{$ReviewCard.attempts[$Ordinal-1].queue_depth});urb_bytes=16384;
        raw_sha256=$rawSha;export_manifest_sha256=(HashFile $exportPath)}
}
$card=Card 'ledger'
$stream=[IO.FileStream]::new((Join-Path $root 'ledger.jsonl'),[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try {
    Refuse {$other=[IO.FileStream]::new($stream.Name,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None);$other.Dispose()} 'exclusive budget lock'
    $prefix=Add-B4ManifestRecord $stream ([byte[]]@()) ([pscustomobject]@{event='HEADER';card_sha256=$hash;budget_id=$card.budget_id;max_attempts=4;max_windows=3})
    $receipts=@()
    foreach ($ordinal in 1..4) {
        $review=Assert-B4Ledger $prefix $hash $card $ordinal
        $prefix=Add-B4ManifestRecord $stream $prefix ([pscustomobject]@{event='CONSUME';ordinal=$ordinal;epoch=$card.attempts[$ordinal-1].physical_recovery_epoch;
            queue_depth=$(if($ordinal -eq 1){16}else{$card.attempts[$ordinal-1].queue_depth});urb_bytes=16384;previous_offline_validation_sha256=$(if($review){$review.sha256}else{$null})})
        Refuse {Assert-B4Ledger $prefix $hash $card $ordinal} ('durable consume blocks replay '+$ordinal)
        $receipt=ReviewFixture $card $ordinal;$receipts+=,$receipt
        $prefix=Add-B4ManifestRecord $stream $prefix $receipt
        if ($ordinal -eq 1) { $firstPrefix=$prefix.Clone() }
        Check $true ('four distinct ordinals '+$ordinal)
    }
    Refuse {Assert-B4Ledger $prefix $hash $card 5} 'fifth attempt rejected'
    $changed=$card.PSObject.Copy();$changed.session_id=[guid]::NewGuid().ToString()
    Refuse {Assert-B4Ledger $prefix $hash $changed 1} 'new session UUID cannot reset ordinal within budget'
    Refuse {Assert-B4Ledger ([byte[]]($prefix[0..($prefix.Length-2)])) $hash $card 2} 'partial manifest refused'
    Refuse {Add-B4ManifestRecord $stream ([byte[]]@()) ([pscustomobject]@{event='RECEIPT'})} 'manifest prefix mutation refused'
} finally { $stream.Dispose() }
$verdictPath=Join-Path $card.budget_directory 'attempt-1\offline_validation.json'
$original=[IO.File]::ReadAllBytes($verdictPath)
foreach ($fault in @('B3schema','B3decision','pending','raw','analysis','evidence','export','budget','card','epoch','ordinal','missing-evidence')) {
    $v=[Text.Encoding]::UTF8.GetString($original)|ConvertFrom-Json
    switch ($fault) {
        'B3schema' {$v.schema='TBS5530-B3-OFFLINE-VALIDATION/v1'}
        'B3decision' {$v.result='RETRY_STARVATION'}
        'pending' {$v.result='PENDING_ANALYSIS'}
        'raw' {$v.raw_sha256='b'*64}
        'analysis' {$v.analysis_manifest_sha256='b'*64}
        'evidence' {$v.evidence[0].sha256='b'*64}
        'export' {$v.export_manifest_sha256='b'*64}
        'budget' {$v.budget_id=[guid]::NewGuid().ToString()}
        'card' {$v.card_sha256='b'*64}
        'epoch' {$v.physical_recovery_epoch=$epoch2}
        'ordinal' {$v.ordinal=2}
        'missing-evidence' {$v.evidence=@()}
    }
    [IO.File]::WriteAllText($verdictPath,($v|ConvertTo-Json -Depth 12),[Text.UTF8Encoding]::new($false))
    Refuse {Assert-B4Ledger $firstPrefix $hash $card 2} ('verdict '+$fault)
}
foreach ($result in @('B4_ANDROID_PARITY_PASS','RF_LIMITED','B4_ANDROID_GAP')) {
    $v=[Text.Encoding]::UTF8.GetString($original)|ConvertFrom-Json;$v.result=$result
    [IO.File]::WriteAllText($verdictPath,($v|ConvertTo-Json -Depth 12),[Text.UTF8Encoding]::new($false))
    $review=Assert-B4Ledger $firstPrefix $hash $card 2
    Check ($review.result -ceq $result) ('B4 result with real evidence '+$result)
}
[IO.File]::WriteAllBytes($verdictPath,$original)
$rawPath=Join-Path $card.raw_directory 'attempt-1\02-received.ts';$rawOriginal=[IO.File]::ReadAllBytes($rawPath)
[IO.File]::WriteAllBytes($rawPath,[byte[]](1,2,3))
Refuse {Assert-B4Ledger $firstPrefix $hash $card 2} 'raw evidence changed after verdict'
[IO.File]::WriteAllBytes($rawPath,$rawOriginal)

# Integration: all device commands are intercepted; large raw fixture exceeds legacy4MiB.
$main=$ast.Extent.Text.Substring($ast.Extent.Text.IndexOf('# B4_MAIN:')).Replace('exit $exitCode','return $exitCode')
$main=$main.Replace('Import-B4SharedGuards $b3 $ExpectedB3HelperSha256','').Replace('Set-B4Overrides','').
    Replace('$PSCommandPath',("'"+$path+"'")).Replace('$PSScriptRoot',("'"+$PSScriptRoot+"'"))
$realHashCommand=Get-Command Get-FileHash
$fakeFirmware=Join-Path $root 'fake-firmware.bin';[IO.File]::WriteAllBytes($fakeFirmware,[byte[]]::new(8192))
function Get-FileHash([string]$LiteralPath,[string]$Algorithm) {
    if ($LiteralPath -ceq $fakeFirmware) { return [pscustomobject]@{Hash=$firmwareSha} }
    return & $realHashCommand -LiteralPath $LiteralPath -Algorithm $Algorithm
}
$script:fakeCalls=0;$script:binaryExports=0;$script:grantCalls=0;$script:stageCalls=0;$script:launch='';$script:permissionToken='';$script:fakeTerminal=$false;$script:fakeFault=''
$raw=[byte[]]::new(8388608);$block=[byte[]](0..255)
for ($i=0;$i -lt $raw.Length;$i+=256) { [Buffer]::BlockCopy($block,0,$raw,$i,256) }
$evidenceBytes=[Text.Encoding]::UTF8.GetBytes("fake evidence`n")
function Invoke-B4Adb([string[]]$DeviceArguments,[string]$InputText='',[string]$BinaryDestination='',[bool]$AfterDrain=$false) {
    $script:fakeCalls++
    $manifest=[Text.Encoding]::UTF8.GetString((Read-B4Manifest $manifestStream))
    if ($manifest -notmatch '"event":"CONSUME"') { throw 'Fake observed device command before durable consume' }
    if ($AfterDrain -and -not $script:fakeTerminal) { throw 'Fake observed export before terminal drain' }
    if ($AfterDrain) { Assert-B4ExportCall $DeviceArguments $Report $Session $BinaryDestination }
    $joined=$DeviceArguments -join ' '
    switch -Regex ($joined) {
        '^get-state$' { return 'device' }
        '^shell getprop ro.product.model$' { return 'SM-X926B' }
        '^shell getprop ro.build.version.sdk$' { return '36' }
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
            foreach ($pin in @(@('b4_enabled','true'),@('b4_duration_ms',[string]$B4DurationMillis),@('b4_max_bytes','268435456'),@('b4_tuner_rssi',$B4TunerRssi.ToString().ToLowerInvariant()),@('b3_duration_ms',[string]$B4DurationMillis),@('b3_max_bytes','268435456'))) {
                if ($DeviceArguments[[Array]::IndexOf($DeviceArguments,$pin[0])+1] -cne $pin[1]) { throw 'Fake launch contract mismatch' }
            }
            $script:launch=$DeviceArguments[[Array]::IndexOf($DeviceArguments,'launch_token')+1];return 'Success'
        }
        'autonomous-v1-challenge.json' { $v=Challenge;$v.launch_token=$script:launch;return ($v|ConvertTo-Json -Depth 15 -Compress) }
        '^shell run-as .*base64 -d >' { $script:stageCalls++;return '' }
        'firmware.bin$' { return $firmwareSha+'  firmware.bin' }
        '^shell set -eu' {
            if ($InputText) {
                $script:grantCalls++
                if ($script:fakeFault -ceq 'grant-uncertain') { throw 'B4_FAKE_GRANT_PUBLISH_UNCERTAIN' }
                $g=$InputText|ConvertFrom-Json
                if ($g.b4_card_sha256 -cne $ExpectedB4CardSha256 -or $g.b4_budget_id -cne $card.budget_id -or $g.b4_attempt_ordinal -ne $B4AttemptOrdinal -or
                    $g.b4_duration_ms -ne $B4DurationMillis -or $g.b4_max_bytes -ne 268435456 -or $g.b4_tuner_rssi -isnot [bool] -or $g.b4_tuner_rssi -ne $B4TunerRssi -or
                    $g.b3_duration_ms -ne $g.b4_duration_ms -or $g.b3_max_bytes -ne $g.b4_max_bytes -or $g.b3_queue_depth -ne $B4QueueDepth -or
                    $g.expires_elapsed_ms -ne 301000 -or $g.attachment_history_sha256 -ne $null -or $g.physical_recovery_epoch -cne $PhysicalRecoveryEpoch) { throw 'Fake grant binding mismatch' }
                return '{"elapsed_ms":1002,"expired":false,"report":null,"permission":null}'
            }
            $r=Report;$r.native_stream.bytes=$raw.Length;$r.raw_received_bytes=$raw.Length;$r.ts_bytes=$raw.Length
            $r|Add-Member launch_token $script:launch;$r|Add-Member frontend_experiment 'B2_RELOAD_AFTER_SET_CONFIG';$r|Add-Member ts_variant 'NATIVE_USBFS_STREAM';$r|Add-Member control_plane_only $false
            $r|Add-Member b4_enabled $true;$r|Add-Member b4_duration_ms $B4DurationMillis;$r|Add-Member b4_max_bytes $B4MaxBytes;$r|Add-Member b4_tuner_rssi $B4TunerRssi
            switch ($script:fakeFault) { 'status75' {$r.native_stream.first_status=-75};'detach' {$r.fx2_reload.detach_ns=123};'drain' {$r.native_stream.outstanding=1};'report-rssi' {$r.b4_tuner_rssi='false'} }
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
            if ($script:fakeFault -ceq 'oversized' -and $joined -match 'received.ts$') { return '268435457 raw' }
            return ([string]$(if ($joined -match '\.ts$') {$raw.Length} else {$evidenceBytes.Length}))+' evidence'
        }
        '^shell run-as .* sha256sum ' {
            $b=if ($joined -match '\.ts$') {$raw} else {$evidenceBytes}
            return (Get-B4Hash $b)+'  evidence'
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
$ExpectedB4HelperSha256=HashFile $path;$ExpectedHelperSha256=HashFile $runtime
$Serial='offline-fixture';$DurationSeconds=300;$FirmwarePath=$fakeFirmware;$PhysicalRecoveryEpoch=$epoch;$PhysicalRecoveryConfirmed=$true;$PhysicalColdSeconds=5
$RfInConfirmed=$true;$AntennaNoDcConfirmed=$true;$GrantBoundedSession=$true;$ProofOfGuardPassed=$true;$ApproveB4SessionBudget=$true;$Approval='OK B4'
$B4AttemptOrdinal=1;$AdbPath=$path;$OutputDirectory=''
function SetupIntegration([string]$Name) {
    $script:card=Card $Name
    foreach ($p in @{model='SM-X926B';api=36;boot_count=47;source_git_sha=$head;source_manifest_path=$sourceManifest;source_manifest_sha256=(HashFile $sourceManifest);
        app_apk_sha256=$hash;autonomous_helper_sha256=$ExpectedHelperSha256;b3_helper_sha256=$ExpectedB3HelperSha256;b4_helper_sha256=$ExpectedB4HelperSha256;
        firmware_sha256=$firmwareSha;reload_plan_sha256=$ExpectedReloadPlanSha256;golden_sha256=$ExpectedFrontendGoldenSha256;
        duration_ms=$B4DurationMillis;tuner_rssi=$B4TunerRssi;ttl_ms=300000;heartbeat_ms=2000;permission_wait_ms=30000}.GetEnumerator()) { $script:card|Add-Member $p.Key $p.Value -Force }
    $script:B4SessionDirectory=$card.session_directory
    $script:B4CardPath=Join-Path $root ($Name+'-card.json');Write-B4NewJson $B4CardPath $card
    $script:ExpectedB4CardSha256=HashFile $B4CardPath
    $script:fakeTerminal=$false;$script:grantCalls=0;$script:stageCalls=0
}
SetupIntegration 'success'
$result=& {$PSCommandPath=$path;$PSScriptRoot=Split-Path -Parent $path;Invoke-Expression $main}
Check ($result[-1] -eq 1 -and $script:grantCalls -eq 1 -and $script:binaryExports -gt 5) 'one fake permission-first B4 capture dispatch'
$receipt=[IO.File]::ReadAllLines((Join-Path $B4SessionDirectory 'session-manifest.jsonl'))[-1]|ConvertFrom-Json
Check ($receipt.capture_complete -and -not $receipt.session_closed -and $receipt.classification -ceq 'PENDING_ANALYSIS' -and -not $receipt.single_run_pass) 'receipt never certifies Android parity'
$captured=@(Get-ChildItem -LiteralPath $card.raw_directory -Recurse -Filter '*.ts')
Check ($captured.Count -eq 2 -and $captured[0].Length -eq $raw.Length -and (HashFile $captured[0].FullName) -ceq (Get-B4Hash $raw)) 'received and backend raw binary exact beyond4MiB'
$calls=$script:fakeCalls
Refuse {& {$PSCommandPath=$path;$PSScriptRoot=Split-Path -Parent $path;Invoke-Expression $main}} 'same ordinal cannot redispatch'
Check ($script:fakeCalls -eq $calls) 'replay refused before transport'
$goodCardPath=$B4CardPath;$goodHash=$ExpectedB4CardSha256
foreach ($fault in @('permission','foreground','grant-uncertain','status75','detach','drain','oversized','missing-monitor','report-rssi')) {
    $script:fakeFault=$fault;SetupIntegration ('failure-'+$fault)
    $threw=$false
    try { $null=& {$PSCommandPath=$path;$PSScriptRoot=Split-Path -Parent $path;Invoke-Expression $main} } catch { $threw=$true }
    $receipt=[IO.File]::ReadAllLines((Join-Path $B4SessionDirectory 'session-manifest.jsonl'))[-1]|ConvertFrom-Json
    if ($fault -in @('permission','foreground')) {
        Check ($threw -and (Test-B4PreGrantRefusal $receipt) -and -not $receipt.drained -and $script:grantCalls -eq 0) ('preparation refusal consumes ordinal without fake drain '+$fault)
        $attempt=Join-Path $B4SessionDirectory 'attempt-1'
        $analysis=Join-Path $reportRoot ($card.budget_id+'-refusal.json');Write-B4NewJson $analysis ([pscustomobject]@{fake_only=$true;no_grant=$true})
        $v=[pscustomobject]@{schema='TBS5530-B4-OFFLINE-VALIDATION/v1';card_sha256=$ExpectedB4CardSha256;budget_id=$card.budget_id;session_id=$card.session_id;ordinal=1;
            physical_recovery_epoch=$epoch;result='PRE_IO_REFUSED';raw_sha256=$null;export_manifest_sha256=$receipt.export_manifest_sha256;
            analysis_manifest_path=$analysis;analysis_manifest_sha256=(HashFile $analysis);evidence=@([pscustomobject]@{path=$analysis;sha256=(HashFile $analysis)})}
        Write-B4NewJson (Join-Path $attempt 'offline_validation.json') $v
        $review=Assert-B4Ledger ([IO.File]::ReadAllBytes((Join-Path $B4SessionDirectory 'session-manifest.jsonl'))) $ExpectedB4CardSha256 $card 2
        Check ($review.result -ceq 'PRE_IO_REFUSED') ('parent-reviewed prep refusal admits only next ordinal '+$fault)
    } else {
        Check $receipt.session_closed ('session latched closed '+$fault)
        Refuse {Assert-B4Ledger ([IO.File]::ReadAllBytes((Join-Path $B4SessionDirectory 'session-manifest.jsonl'))) $ExpectedB4CardSha256 $card 2} ('no retry '+$fault)
    }
}
$script:fakeFault='';$B4DurationMillis=60000;$B4TunerRssi=$true;SetupIntegration 'rssi-max60'
$result=& {$PSCommandPath=$path;$PSScriptRoot=Split-Path -Parent $path;Invoke-Expression $main}
Check ($result[-1] -eq 1 -and $script:grantCalls -eq 1) 'optional RSSI true and max60s transported unchanged'
foreach ($scriptPath in @($path,$PSCommandPath,$b3)) {
    $b=[IO.File]::ReadAllBytes($scriptPath);$text=[Text.Encoding]::UTF8.GetString($b)
    Check (($b[0..2]-join ',') -ceq '239,187,191' -and -not [regex]::IsMatch($text,'(?<!\r)\n')) 'BOM/CRLF preserved'
}
$summary=[pscustomobject]@{status='PASS';checks=$checks;real_adb_calls=0;fake_transport_calls=$script:fakeCalls;binary_exports=$script:binaryExports;
    b4_helper_sha256=(HashFile $path);b3_helper_sha256=$ExpectedB3HelperSha256;hardware='NOT_RUN';vm='NOT_RUN';network='NOT_RUN';verdict_created_by_helper=$false}
Write-B4NewJson (Join-Path $reportRoot 'test-summary.json') $summary
Write-Output ("PASS: B4 $checks/$checks; chỉ fake, không ADB thật/phần cứng/VM/network; evidence: "+$root)
