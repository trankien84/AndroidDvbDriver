# SPDX-License-Identifier: GPL-2.0-or-later
# Fake-only: transport overridden before main; never invokes ADB, hardware or package tools.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$path=Join-Path $PSScriptRoot 'Invoke-TBS5530-B3Session.ps1'
$runtime=Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'B3 parse failed.'}
$functions=@($ast.FindAll({param($n)$n -is [Management.Automation.Language.FunctionDefinitionAst]},$true))
foreach($function in $functions){Invoke-Expression $function.Extent.Text}
$main=$ast.Extent.Text.Substring($ast.Extent.Text.IndexOf('# B3_MAIN:')).Replace('exit $exitCode','return $exitCode').
    Replace('$PSCommandPath',("'"+$path+"'")).Replace('$PSScriptRoot',("'"+$PSScriptRoot+"'"))
$root=[IO.Path]::GetFullPath($OutputDirectory)
if(-not $root.StartsWith('D:\TSDuck\logs\',[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $root)){throw 'Fresh private fake evidence directory required.'}
$null=New-Item -ItemType Directory -Path $root
$checks=0
function Check([bool]$Condition,[string]$Name){if(-not $Condition){throw ('FAIL: '+$Name)};$script:checks++}
function Refuse([scriptblock]$Action,[string]$Name){$caught=$false;try{& $Action|Out-Null}catch{$caught=$true};Check $caught $Name}
$epoch='11111111-1111-4111-8111-111111111111'
$epoch2='22222222-2222-4222-8222-222222222222'
$session='33333333-3333-4333-8333-333333333333'
$hash='a'*64
$pins=@{schema='TBS5530-B3-CARD/v1';approval='OK B3';api=36}
function Card {
    [pscustomobject]@{schema='TBS5530-B3-CARD/v1';approval='OK B3';api=36;session_id=$session;session_directory=(Join-Path $root 'budget');max_attempts=2;attempts=@(
        [pscustomobject]@{ordinal=1;physical_recovery_epoch=$epoch;queue_depth=16;urb_bytes=16384},
        [pscustomobject]@{ordinal=2;physical_recovery_epoch=$epoch2;queue_depth=8;urb_bytes=4096})}
}
$card=Card
Assert-B3Card $card $pins 1 $epoch 16 16384;Check $true 'valid card'
foreach($fault in @('approval','numeric-string','budget','duplicate-epoch','ordinal','chunk','depth','epoch-binding','variant-binding')){
    $c=Card;$e=$epoch;$d=16
    switch($fault){
        'approval'{$c.approval='OK B2'}
        'numeric-string'{$c.api='36'}
        'budget'{$c.max_attempts=4}
        'duplicate-epoch'{$c.attempts[1].physical_recovery_epoch=$epoch}
        'ordinal'{$c.attempts[1].ordinal=3}
        'chunk'{$c.attempts[0].urb_bytes=4097}
        'depth'{$c.attempts[0].queue_depth=33}
        'epoch-binding'{$e=$epoch2}
        'variant-binding'{$d=8}
    }
    Refuse {Assert-B3Card $c $pins 1 $e $d 16384} $fault
}
function New-ReviewFixture([object]$ReviewCard,[int]$Ordinal){
    $folder=Join-Path $ReviewCard.session_directory ('attempt-'+$Ordinal)
    $null=New-Item -ItemType Directory -Path $folder -Force
    $rawPath=Join-Path $folder '02-received.ts';[IO.File]::WriteAllBytes($rawPath,[byte[]](0..255))
    $rawSha=Get-B3Hash ([IO.File]::ReadAllBytes($rawPath))
    $exportPath=Join-Path $folder 'export-manifest.json'
    Write-B3NewJson $exportPath @([pscustomobject]@{path='files/fake/received.ts';present=$true;file='02-received.ts';bytes=256;sha256=$rawSha})
    $analysis=Join-Path $folder 'analysis-manifest.json';Write-B3NewJson $analysis ([pscustomobject]@{fake_only=$true;breaksAfterPrefix=0;ccErrors=0;patPmt=$true})
    $verdict=[pscustomobject]@{schema='TBS5530-B3-OFFLINE-VALIDATION/v1';card_sha256=$hash;session_id=$ReviewCard.session_id;ordinal=$Ordinal;
        physical_recovery_epoch=$ReviewCard.attempts[$Ordinal-1].physical_recovery_epoch;analyzerPass=$true;tsduckPass=$true;throughputPass=$true;pidServiceMatch=$true;
        raw_sha256=$rawSha;analysis_manifest_path=$analysis;analysis_manifest_sha256=(Get-B3Hash ([IO.File]::ReadAllBytes($analysis)))}
    Write-B3NewJson (Join-Path $folder 'offline_validation.json') $verdict
    return [pscustomobject]@{event='RECEIPT';ordinal=$Ordinal;session_closed=$false;drained=$true;result='STRUCTURE_CONFIRMED';single_run_pass=$false;
        queue_depth=$ReviewCard.attempts[$Ordinal-1].queue_depth;urb_bytes=$ReviewCard.attempts[$Ordinal-1].urb_bytes;classification='B3_CAPTURE_PENDING_OFFLINE_VALIDATION';capture_complete=$true;raw_sha256=$rawSha;export_manifest_sha256=(Get-B3Hash ([IO.File]::ReadAllBytes($exportPath)))}
}
$allowCard=Card
$allowCard.attempts[0].PSObject.Properties.Remove('queue_depth');$allowCard.attempts[0].PSObject.Properties.Remove('urb_bytes')
$allowCard.attempts[0]|Add-Member allowed_receive_configs @([pscustomobject]@{queue_depth=16;urb_bytes=16384})
$allowCard.attempts[1]|Add-Member allowed_receive_configs @([pscustomobject]@{queue_depth=16;urb_bytes=16384},[pscustomobject]@{queue_depth=32;urb_bytes=16384},[pscustomobject]@{queue_depth=16;urb_bytes=65536})
foreach($config in $allowCard.attempts[1].allowed_receive_configs){
    Assert-B3Card $allowCard $pins 2 $epoch2 $config.queue_depth $config.urb_bytes
    Check $true 'explicit card variant admitted without new approval'
}
Refuse {Assert-B3Card $allowCard $pins 1 $epoch 32 16384} 'first default-only card denies expansion'
Refuse {Assert-B3Card $allowCard $pins 2 $epoch2 32 65536} 'bounded but unlisted receive variant denied'
$card=Card
$reviewReceipt=New-ReviewFixture $card 1
$stream=[IO.FileStream]::new((Join-Path $root 'unit-manifest.jsonl'),[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try{
    $prefix=[byte[]]@()
    $prefix=Add-B3ManifestRecord $stream $prefix ([pscustomobject]@{event='HEADER';card_sha256=$hash;session_id=$session;max_attempts=2})
    Assert-B3Budget $prefix $hash $card 1;Check $true 'header readback'
    $prefix=Add-B3ManifestRecord $stream $prefix ([pscustomobject]@{event='CONSUME';ordinal=1;epoch=$epoch;queue_depth=16;urb_bytes=16384})
    Refuse {Assert-B3Budget $prefix $hash $card 1} 'consume blocks retry'
    $prefix=Add-B3ManifestRecord $stream $prefix $reviewReceipt
    $firstReview=Assert-B3Budget $prefix $hash $card 2;Check ($firstReview.sha256 -cmatch '^[a-f0-9]{64}$') 'reviewed second independent run admitted'
    Refuse {Assert-B3Budget $prefix ('b'*64) $card 2} 'card substitution refused'
    Refuse {Assert-B3Budget ([byte[]]($prefix[0..($prefix.Length-2)])) $hash $card 2} 'partial manifest refused'
    Refuse {Add-B3ManifestRecord $stream ([byte[]]@()) ([pscustomobject]@{event='RECEIPT'})} 'prefix mutation refused'
    $prior=$prefix.Clone()
    $prefix=Add-B3ManifestRecord $stream $prefix ([pscustomobject]@{event='CONSUME';ordinal=2;epoch=$epoch2;queue_depth=8;urb_bytes=4096;previous_offline_validation_sha256=$firstReview.sha256})
    $prefix=Add-B3ManifestRecord $stream $prefix ([pscustomobject]@{event='RECEIPT';ordinal=2;session_closed=$true;drained=$true;result='USB_RECEIVE_FAILURE';single_run_pass=$false;queue_depth=8;urb_bytes=4096})
    Refuse {Assert-B3Budget $prefix $hash $card 3} 'latched closure'
}finally{$stream.Dispose()}
$verdictPath=Join-Path $card.session_directory 'attempt-1\offline_validation.json'
$originalVerdict=[IO.File]::ReadAllBytes($verdictPath)
foreach($fault in @('analyzerPass','tsduckPass','throughputPass','pidServiceMatch','typedBool','rawHash','analysisHash','cardHash','epoch','ordinal')){
    $v=[Text.Encoding]::UTF8.GetString($originalVerdict)|ConvertFrom-Json
    switch($fault){
        'typedBool'{$v.analyzerPass='true'}
        'rawHash'{$v.raw_sha256='b'*64}
        'analysisHash'{$v.analysis_manifest_sha256='b'*64}
        'cardHash'{$v.card_sha256='b'*64}
        'epoch'{$v.physical_recovery_epoch=$epoch2}
        'ordinal'{$v.ordinal=2}
        default{$v.$fault=$false}
    }
    [IO.File]::WriteAllText($verdictPath,($v|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
    Refuse {Assert-B3Budget $prior $hash $card 2} ('offline verdict '+$fault)
}
[IO.File]::WriteAllBytes($verdictPath,$originalVerdict)
$retry=$reviewReceipt.PSObject.Copy();$retry|Add-Member starvation_counter 1
$v=[Text.Encoding]::UTF8.GetString($originalVerdict)|ConvertFrom-Json
$v|Add-Member decision 'RETRY_STARVATION';$v.analyzerPass=$false;$v.tsduckPass=$false;$v.throughputPass=$false;$v.pidServiceMatch=$false
[IO.File]::WriteAllText($verdictPath,($v|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
$review=Read-B3OfflineValidation $card $hash $retry 1
Check ($review.decision -ceq 'RETRY_STARVATION') 'reviewed starvation retry does not become pass'
$retry.starvation_counter=0
Refuse {Read-B3OfflineValidation $card $hash $retry 1} 'starvation retry requires observed counter'
[IO.File]::WriteAllBytes($verdictPath,$originalVerdict)
$preCard=Card;$preCard.session_directory=Join-Path $root 'pre-io'
$preReceipt=New-ReviewFixture $preCard 1;$preReceipt.capture_complete=$false;$preReceipt.classification='B3_NOT_PASS';$preReceipt.raw_sha256=$null
$preReceipt|Add-Member pre_io_refusal $true
$folder=Join-Path $preCard.session_directory 'attempt-1'
$exportPath=Join-Path $folder 'export-manifest.json';[IO.File]::WriteAllText($exportPath,'[]',[Text.UTF8Encoding]::new($false))
$preReceipt.export_manifest_sha256=Get-B3Hash ([IO.File]::ReadAllBytes($exportPath))
$preVerdictPath=Join-Path $folder 'offline_validation.json';$v=[IO.File]::ReadAllText($preVerdictPath)|ConvertFrom-Json
$v|Add-Member decision 'RETRY_PRE_IO';$v.raw_sha256=$null;$v.analyzerPass=$false;$v.tsduckPass=$false;$v.throughputPass=$false;$v.pidServiceMatch=$false
[IO.File]::WriteAllText($preVerdictPath,($v|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
$review=Read-B3OfflineValidation $preCard $hash $preReceipt 1
Check ($review.decision -ceq 'RETRY_PRE_IO') 'pre IO retry preserves explicit review and no raw'
$rawPath=Join-Path $card.session_directory 'attempt-1\02-received.ts';$rawOriginal=[IO.File]::ReadAllBytes($rawPath)
[IO.File]::WriteAllBytes($rawPath,[byte[]](1,2,3))
Refuse {Assert-B3Budget $prior $hash $card 2} 'changed raw refused'
[IO.File]::WriteAllBytes($rawPath,$rawOriginal)
Move-Item -LiteralPath $verdictPath -Destination ($verdictPath+'.held')
Refuse {Assert-B3Budget $prior $hash $card 2} 'missing offline verdict blocks ordinal two'
Move-Item -LiteralPath ($verdictPath+'.held') -Destination $verdictPath
$card3=Card;$card3.session_directory=Join-Path $root 'budget-three';$card3.max_attempts=3
$card3.attempts+= [pscustomobject]@{ordinal=3;physical_recovery_epoch='44444444-4444-4444-8444-444444444444';queue_depth=32;urb_bytes=65536}
Assert-B3Card $card3 $pins 3 $card3.attempts[2].physical_recovery_epoch 32 65536;Check $true 'third bounded starvation variant'
$stream=[IO.FileStream]::new((Join-Path $root 'three-manifest.jsonl'),[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try{
    Refuse { $duplicate=[IO.FileStream]::new($stream.Name,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None);$duplicate.Dispose() } 'exclusive session lock'
    $prefix=Add-B3ManifestRecord $stream ([byte[]]@()) ([pscustomobject]@{event='HEADER';card_sha256=$hash;session_id=$session;max_attempts=3})
    foreach($ordinal in 1..3){
        $review=Assert-B3Budget $prefix $hash $card3 $ordinal
        $prefix=Add-B3ManifestRecord $stream $prefix ([pscustomobject]@{event='CONSUME';ordinal=$ordinal;epoch=$card3.attempts[$ordinal-1].physical_recovery_epoch;
            queue_depth=$card3.attempts[$ordinal-1].queue_depth;urb_bytes=$card3.attempts[$ordinal-1].urb_bytes;previous_offline_validation_sha256=$(if($review){$review.sha256}else{$null})})
        $receipt=New-ReviewFixture $card3 $ordinal
        $prefix=Add-B3ManifestRecord $stream $prefix $receipt
        Check $true ('independent reviewed ordinal '+$ordinal)
    }
    $lastVerdict=Join-Path $card3.session_directory 'attempt-1\offline_validation.json'
    $bytes=[IO.File]::ReadAllBytes($lastVerdict)
    [IO.File]::WriteAllBytes($lastVerdict,[byte[]]($bytes+[byte]32))
    Refuse {Assert-B3Budget $prefix $hash $card3 4} 'consumed verdict bytes cannot change'
    [IO.File]::WriteAllBytes($lastVerdict,$bytes)
    Refuse { Assert-B3Budget $prefix $hash $card3 4 } 'fourth run exceeds frozen budget'
}finally{$stream.Dispose()}
function Report {
    [pscustomobject]@{cleanup='DRAIN_CONFIRMED';state='REPORT';result='STRUCTURE_CONFIRMED';terminal=$true;cleanup_path='RUNTIME_DRAIN_CONFIRMED';
        native_urb=$null;native_stream=[pscustomobject]@{outstanding=0;quarantined=$false;first_status=0;first_errno=0;bytes=32768};
        firmware=$null;fx2_reload=[pscustomobject]@{state='COMPLETE';attempted=132;completed=132;detach_ns=-1};last_errno=0;
        ts_bytes=32768;structure_confirmed=$true;ts_smoke_pass=$true;raw_capture='SYNCED_CLOSED';raw_received_bytes=32768;
        control_mirror='SYNCED_CLOSED';permission_outcome='AUTO_PERMISSION_HANDLED_BY_OS';history_guard_warning='LEGACY_HISTORY_NOT_AUTHORITY_B3_SESSION_BUDGET_AND_COLD_EPOCH_REQUIRED'}
}
$r=Report
Check (Test-B3Drained $r) 'typed stream drain'
foreach($fault in @('legacy-only','terminal-string','outstanding','quarantine','wrong-path')){
    $r=Report
    switch($fault){
        'legacy-only'{$r.PSObject.Properties.Remove('terminal');$r.PSObject.Properties.Remove('cleanup_path')}
        'terminal-string'{$r.terminal='true'}
        'outstanding'{$r.native_stream.outstanding=1}
        'quarantine'{$r.native_stream.quarantined=$true}
        'wrong-path'{$r.cleanup_path='NO_RESOURCES_ACQUIRED'}
    }
    Check (-not(Test-B3Drained $r)) $fault
}
foreach($fault in @('status75','errno75','last75','detach','uncertain')){
    $r=Report
    switch($fault){'status75'{$r.native_stream.first_status=-75};'errno75'{$r.native_stream.first_errno=75};'last75'{$r.last_errno=75};'detach'{$r.fx2_reload.detach_ns=123};'uncertain'{$r.state='ERROR_UNCERTAIN'}}
    Check (Test-B3SessionClosed $r) $fault
}
$r=Report
$exports=@([pscustomobject]@{path='files/session/received.ts';present=$true;bytes=32768})
$one=Get-B3Classification $r ([byte[]]@()) $exports
Check ($one.classification -ceq 'B3_CAPTURE_PENDING_OFFLINE_VALIDATION' -and -not $one.single_run_pass -and -not $one.root_cause_confirmed -and -not $one.final_ts_smoke_pass) 'app flags never certify pass'
$two=Get-B3Classification $r $prior $exports
Check ($two.classification -ceq 'B3_CAPTURE_PENDING_OFFLINE_VALIDATION' -and $two.independent_passes -eq 0 -and -not $two.single_run_pass -and -not $two.root_cause_confirmed -and -not $two.final_ts_smoke_pass) 'prior app receipts cannot certify final result'
$r.ts_smoke_pass=$false
Check (-not (Get-B3Classification $r $prior $exports).final_ts_smoke_pass) 'structural result alone does not pass'
$r=Report;$exports[0].bytes=1
Check (-not (Get-B3Classification $r $prior $exports).single_run_pass) 'raw export length binding'
$package='info.martinmarinov.dvbdriver';$ExpectedModel='SM-X926B';$ExpectedApi=36;$ExpectedBootCount=47;$firmwareSha='b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc'
$ExpectedFrontendGoldenSha256='75875b84fc3f959b35d436560ae19ccf7e52b890173f1f5e24984367f64fe228'
$ExpectedReloadPlanSha256='9add00e12a7fbea9aa4877451db4a28077c64872cd73e6eb91f04facf1fec650'
$ExpectedAppApkSha256=$hash;$B3QueueDepth=16;$B3UrbBytes=16384
$card=Card;$card|Add-Member source_git_sha ('9'*40)
function Challenge {
    $header="TBS5530-FX2-RELOAD/v1`nHOST samsung-sm-x926b-api36`nINITIAL_HISTORY_SHA256 b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb`n"
    $h=Get-B3Hash ([Text.Encoding]::UTF8.GetBytes($header))
    [pscustomobject]@{policy='TBS5530-AUTONOMOUS/v1';session=$session;launch_token=$epoch;control_plane_only=$false;live_ready=$true;
        created_elapsed_ms=1000;model='SM-X926B';api=36;boot_count=47;host_profile='samsung-sm-x926b-api36';vid=29516;pid=21808;
        firmware_sha256=$firmwareSha;firmware_protocol='TBS_SOURCE_DUAL_RESET';ts_variant='NATIVE_USBFS_STREAM';frontend_experiment='B2_RELOAD_AFTER_SET_CONFIG';
        golden_sha256=$ExpectedFrontendGoldenSha256;app_apk_sha256=$hash;control_mirror=$true;explicit_set_interface_alt0=$false;sony_ts_output_readback=$false;
        retry=0;max_upload_per_attachment=1;max_reload_per_physical_epoch=1;max_session_ms=300000;heartbeat_ms=2000;
        fx2_reload_option='B2_RELOAD_AFTER_SET_CONFIG';fx2_reload_plan_sha256=$ExpectedReloadPlanSha256;
        bootstrap_proof='SAME_SESSION_ORIGINAL132_APP_BOUNDARIES_DRAIN_REQUIRED';b3_queue_depth=16;b3_urb_bytes=16384;b3_max_bytes=4194304;b3_duration_ms=2000;
        attachment_history_sha256=$null;fx2_reload_store_sha256=$h;fx2_reload_anchor_sha256=(Get-B3Hash ([Text.Encoding]::UTF8.GetBytes("TBS5530-FX2-RELOAD-ANCHOR/v1`n$h`n")));source_git_sha=('9'*40)}
}
$c=Challenge;Assert-B3Challenge $c $epoch 1001;Check $true 'RAM initial challenge accepted'
foreach($field in @('app_apk_sha256','firmware_sha256','golden_sha256','fx2_reload_plan_sha256','fx2_reload_store_sha256','fx2_reload_anchor_sha256',
    'source_git_sha','b3_queue_depth','b3_urb_bytes','b3_max_bytes','b3_duration_ms','attachment_history_sha256','boot_count','heartbeat_ms','max_session_ms')){
    $c=Challenge;$c.$field='changed'
    Refuse {Assert-B3Challenge $c $epoch 1001} ('challenge pin '+$field)
}
$permission=[pscustomobject]@{session=$session;vid=29516;pid=21808;state='PENDING';requested_elapsed_ms=1000}
Assert-B3PermissionSnapshot $permission $session 30999;Check $true 'human permission 29999ms'
Refuse {Assert-B3PermissionSnapshot $permission $session 31000} 'human permission 30000ms'
$r=Report
$remote='files/autonomous-v1-'+$session+'/received.ts'
Assert-B3ExportCall @('exec-out','run-as',$package,'cat',$remote) $r $session 'fake.ts';Check $true 'exact binary allowlist'
foreach($argsCase in @(
    @('exec-out','run-as',$package,'cat','files/other/received.ts'),
    @('shell','run-as',$package,'rm',$remote),
    @('shell','run-as',$package,'sha256sum',($remote+';id')),
    @('shell','am','start','arbitrary')
)) {Refuse {Assert-B3ExportCall $argsCase $r $session ''} 'export rejects nonallowlisted command'}
Refuse {Assert-B3ExportCall @('exec-out','run-as',$package,'cat',$remote) $r $session ''} 'binary sink mandatory'
$r.native_stream.outstanding=1
Refuse {Assert-B3ExportCall @('exec-out','run-as',$package,'cat',$remote) $r $session 'fake.ts'} 'export refuses active native work'
$base=@{Serial='offline-fixture';ExpectedModel='SM-X926B';ExpectedApi=36;ExpectedBootCount=47;ReadApi='NATIVE_USBFS_STREAM';Profile='EXPERIMENTAL_DIAGNOSTIC';FrontendExperiment='B2_RELOAD_AFTER_SET_CONFIG';ControlMirror=$true}
foreach($fault in @('no-approval','auto-ui','legacy-history','legacy-store','alt0','sony','control-only','profile','frontend')){
    $case=$base.Clone()
    switch($fault){
        'auto-ui'{$case.AutoUsbPermission=$true}
        'legacy-history'{$case.ExpectedAttachmentHistorySha256=$hash}
        'legacy-store'{$case.ExpectedReloadStoreSha256=$hash}
        'alt0'{$case.ExplicitSetInterfaceAlt0=$true}
        'sony'{$case.SonyTsOutputReadback=$true}
        'control-only'{$case.ControlPlaneOnly=$true}
        'profile'{$case.Profile='AUTO_VERIFIED'}
        'frontend'{$case.FrontendExperiment='BASELINE_AUTO'}
    }
    Refuse {& $runtime @case} ('offline routing refusal '+$fault)
}
# Redacted Samsung API36 shape: global authority differs from a secondary display.
$script:foregroundFixture=@'
  Display #0
      topResumedActivity=ActivityRecord{REDACTED u0 info.martinmarinov.dvbdriver/.Tbs5530AutonomousDiagnosticActivity tREDACTED}
  Display #2
      topResumedActivity=ActivityRecord{REDACTED u0 com.sec.android.app.launcher/.activities.LauncherActivity tREDACTED}
  ResumedActivity: ActivityRecord{REDACTED u0 info.martinmarinov.dvbdriver/.Tbs5530AutonomousDiagnosticActivity tREDACTED}
'@
function Invoke-B3Adb([string[]]$DeviceArguments){
    if(($DeviceArguments -join ' ') -cne 'shell dumpsys activity activities'){throw 'Unexpected foreground fixture call'}
    return $script:foregroundFixture
}
Assert-B3Foreground;Check $true 'Samsung global ResumedActivity with secondary launcher accepted'
$validForeground=$script:foregroundFixture
foreach($fault in @('duplicate-global','wrong-global','top-only','lookalike-key','lookalike-component')){
    $script:foregroundFixture=$validForeground
    switch($fault){
        'duplicate-global'{$script:foregroundFixture+="`n  mResumedActivity: ActivityRecord{REDACTED u0 info.martinmarinov.dvbdriver/.Tbs5530AutonomousDiagnosticActivity tREDACTED}"}
        'wrong-global'{$script:foregroundFixture=$validForeground.Replace('  ResumedActivity: ActivityRecord{REDACTED u0 info.martinmarinov.dvbdriver/.Tbs5530AutonomousDiagnosticActivity','  ResumedActivity: ActivityRecord{REDACTED u0 com.sec.android.app.launcher/.activities.LauncherActivity')}
        'top-only'{$script:foregroundFixture=($validForeground -split "`n"|Where-Object {$_ -notmatch '^  ResumedActivity:'}) -join "`n"}
        'lookalike-key'{$script:foregroundFixture=$validForeground.Replace('  ResumedActivity:','  NotResumedActivity:')}
        'lookalike-component'{$script:foregroundFixture=$validForeground.Replace('  ResumedActivity: ActivityRecord{REDACTED u0 info.martinmarinov.dvbdriver/.Tbs5530AutonomousDiagnosticActivity','  ResumedActivity: ActivityRecord{REDACTED u0 info.martinmarinov.dvbdriver/.Tbs5530AutonomousDiagnosticActivityExtra')}
    }
    Refuse {Assert-B3Foreground} ('foreground rejects '+$fault)
}
$script:foregroundFixture=$validForeground.Replace('  ResumedActivity:','  mResumedActivity:')
Assert-B3Foreground;Check $true 'legacy exact global key still accepted'
# Main integration executes with a fake transport. File/hash operations remain real except the synthetic firmware fixture hash.
$realHashCommand=Get-Command Get-FileHash
$fakeFirmware=Join-Path $root 'fake-firmware.bin';[IO.File]::WriteAllBytes($fakeFirmware,[byte[]]::new(8192))
$raw=[byte[]]::new(4194304);$block=[byte[]](0..255)
for($i=0;$i -lt $raw.Length;$i+=256){[Buffer]::BlockCopy($block,0,$raw,$i,256)}
$script:fakeCalls=0;$script:fakeTerminal=$false;$script:launch='';$script:fakeReport=$null;$script:binaryExports=0
$script:fakeFault='';$script:grantCalls=0;$script:stageCalls=0
function Get-FileHash([string]$LiteralPath,[string]$Algorithm){
    if($LiteralPath -ceq $fakeFirmware){return [pscustomobject]@{Hash=$firmwareSha}}
    return & $realHashCommand -LiteralPath $LiteralPath -Algorithm $Algorithm
}
function Invoke-B3Adb([string[]]$DeviceArguments,[string]$InputText='',[string]$BinaryDestination='',[bool]$AfterDrain=$false){
    $script:fakeCalls++
    $manifest=[Text.Encoding]::UTF8.GetString((Read-B3Manifest $manifestStream))
    if($manifest -notmatch '"event":"CONSUME"'){throw 'Fake observed ADB before durable consume'}
    if($AfterDrain -and -not $script:fakeTerminal){throw 'Fake observed export before terminal drain'}
    if($AfterDrain){Assert-B3ExportCall $DeviceArguments $Report $Session $BinaryDestination}
    $joined=$DeviceArguments -join ' '
    switch -Regex ($joined){
        '^get-state$'{return 'device'}
        '^shell getprop ro.product.model$'{return 'SM-X926B'}
        '^shell getprop ro.build.version.sdk$'{return '36'}
        '^shell settings get global boot_count$'{return '47'}
        '^shell pm path '{return 'package:/data/app/fake/base.apk'}
        '^shell sha256sum /data/app/'{return $hash+'  base.apk'}
        '^shell cat /proc/uptime$'{return '1.001 1.000'}
        '^shell dumpsys activity activities$'{
            if($script:fakeFault -ceq 'pregrant'){return 'ResumedActivity: ActivityRecord{REDACTED u0 com.sec.android.app.launcher/.activities.LauncherActivity tREDACTED}'}
            return 'ResumedActivity: ActivityRecord{REDACTED u0 info.martinmarinov.dvbdriver/.Tbs5530AutonomousDiagnosticActivity tREDACTED}'
        }
        '^shell am start '{
            $script:launch=$DeviceArguments[[Array]::IndexOf($DeviceArguments,'launch_token')+1];return 'Success'
        }
        'autonomous-v1-challenge.json'{
            $c=Challenge;$c.launch_token=$script:launch;return ($c|ConvertTo-Json -Depth 12 -Compress)
        }
        '^shell run-as .*base64 -d >'{$script:stageCalls++;return ''}
        'firmware.bin$'{return $firmwareSha+'  firmware.bin'}
        '^shell set -eu'{
            if($InputText){
                $script:grantCalls++
                if($script:fakeFault -ceq 'grant-uncertain'){throw 'B3_FAKE_GRANT_PUBLISH_UNCERTAIN'}
                $g=$InputText|ConvertFrom-Json
                if($g.b3_card_sha256 -cne $ExpectedB3CardSha256 -or $g.physical_recovery_epoch -cne $epoch -or $g.expires_elapsed_ms -ne 301000 -or
                    $g.b3_queue_depth -ne 16 -or $g.b3_urb_bytes -ne 16384 -or $g.attachment_history_sha256 -ne $null){throw 'Fake grant binding mismatch'}
                return '{"elapsed_ms":1001,"expired":false,"report":null,"permission":null}'
            }
            $r=Report;$r.native_stream.bytes=$raw.Length;$r.raw_received_bytes=$raw.Length;$r.ts_bytes=$raw.Length;$r|Add-Member launch_token $script:launch;$r|Add-Member frontend_experiment 'B2_RELOAD_AFTER_SET_CONFIG';$r|Add-Member ts_variant 'NATIVE_USBFS_STREAM';$r|Add-Member control_plane_only $false
            $script:fakeReport=$r;$script:fakeTerminal=$true
            return (@{elapsed_ms=1002;expired=$false;report=$r;permission=$null}|ConvertTo-Json -Depth 15 -Compress)
        }
        '^shell run-as .* sh -c '{
            if(-not $AfterDrain){throw 'Unexpected predrain shell'}
            if($joined -match 'no_backup/|attachments.txt|receive-audit'){return ''}
            return 'PRESENT'
        }
        '^shell run-as .* sha256sum '{
            if(-not $AfterDrain){throw 'Unexpected predrain hash'}
            $bytes=if($joined -match 'received.ts$'){$raw}else{[Text.Encoding]::UTF8.GetBytes("fake evidence`n")}
            return (Get-B3Hash $bytes)+'  evidence'
        }
        '^exec-out run-as .* cat '{
            if(-not $AfterDrain -or -not $BinaryDestination){throw 'Binary export unguarded'}
            $bytes=if($joined -match 'received.ts$'){$raw}else{[Text.Encoding]::UTF8.GetBytes("fake evidence`n")}
            [IO.File]::WriteAllBytes($BinaryDestination,$bytes);$script:binaryExports++;return
        }
        default{throw ('Unexpected fake operation: '+$joined)}
    }
}
$runtimeHash=(Get-FileHash -LiteralPath $runtime -Algorithm SHA256).Hash.ToLowerInvariant()
$b3Hash=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
$B3SessionDirectory=Join-Path $root 'fake-session'
$fullCard=Card
foreach($item in @{
    model='SM-X926B';boot_count=47;source_git_sha=('9'*40);session_directory=$B3SessionDirectory;app_apk_sha256=$hash;
    autonomous_helper_sha256=$runtimeHash;b3_helper_sha256=$b3Hash;firmware_sha256=$firmwareSha;reload_plan_sha256=$ExpectedReloadPlanSha256;
    golden_sha256=$ExpectedFrontendGoldenSha256;max_bytes=4194304;duration_ms=2000;ttl_ms=300000;heartbeat_ms=2000;permission_wait_ms=30000
}.GetEnumerator()){$fullCard|Add-Member $item.Key $item.Value -Force}
$B3CardPath=Join-Path $root 'fake-card.json';Write-B3NewJson $B3CardPath $fullCard
$ExpectedB3CardSha256=(Get-FileHash -LiteralPath $B3CardPath -Algorithm SHA256).Hash.ToLowerInvariant()
$ExpectedB3HelperSha256=$b3Hash;$ExpectedHelperSha256=$runtimeHash
$Serial='offline-fixture';$DurationSeconds=300;$FirmwarePath=$fakeFirmware;$PhysicalRecoveryEpoch=$epoch
$PhysicalRecoveryConfirmed=$true;$PhysicalColdSeconds=5;$RfInConfirmed=$true;$GrantBoundedSession=$true;$ProofOfGuardPassed=$true;$ApproveB3SessionBudget=$true
$B3AttemptOrdinal=1;$AdbPath=$path;$OutputDirectory=''
$result=& { $PSCommandPath=$path; $PSScriptRoot=Split-Path -Parent $path; Invoke-Expression $main }
Check ($result[-1] -eq 1 -and $script:fakeCalls -gt 20 -and $script:binaryExports -gt 5) 'one complete fake dispatch'
$receipt=[IO.File]::ReadAllLines((Join-Path $B3SessionDirectory 'session-manifest.jsonl'))[-1]|ConvertFrom-Json
Check ($receipt.event -ceq 'RECEIPT' -and -not $receipt.single_run_pass -and $receipt.capture_complete -and $receipt.classification -ceq 'B3_CAPTURE_PENDING_OFFLINE_VALIDATION' -and -not $receipt.session_closed) 'durable terminal receipt'
$captured=@(Get-ChildItem -LiteralPath (Join-Path $B3SessionDirectory 'attempt-1') -Filter '*received.ts')
Check ($captured.Count -eq 1 -and (Get-B3Hash ([IO.File]::ReadAllBytes($captured.FullName))) -ceq (Get-B3Hash $raw)) 'binary export byte exact including 0/128/255/CR/LF'
$calls=$script:fakeCalls
Refuse { & { $PSCommandPath=$path; $PSScriptRoot=Split-Path -Parent $path; Invoke-Expression $main } } 'second invocation cannot redispatch ordinal'
Check ($script:fakeCalls -eq $calls) 'replay rejected before transport'
foreach($fault in @('pregrant','grant-uncertain')){
    $script:fakeFault=$fault;$script:fakeTerminal=$false;$script:grantCalls=0;$script:stageCalls=0
    $B3SessionDirectory=Join-Path $root ('fault-'+$fault)
    $faultCard=$fullCard.PSObject.Copy();$faultCard.session_directory=$B3SessionDirectory
    $B3CardPath=Join-Path $root ('card-'+$fault+'.json');Write-B3NewJson $B3CardPath $faultCard
    $ExpectedB3CardSha256=(Get-FileHash -LiteralPath $B3CardPath -Algorithm SHA256).Hash.ToLowerInvariant()
    Refuse {& {Invoke-Expression $main}} ('fake failure '+$fault)
    $manifestPath=Join-Path $B3SessionDirectory 'session-manifest.jsonl'
    $receipt=[IO.File]::ReadAllLines($manifestPath)[-1]|ConvertFrom-Json
    if($fault -ceq 'pregrant'){
        Check ((Test-B3PreGrantRefusal $receipt) -and -not $receipt.drained -and $script:grantCalls -eq 0 -and $script:stageCalls -eq 0) 'future pregrant receipt is not fake drain or grant'
        $attempt=Join-Path $B3SessionDirectory 'attempt-1'
        $analysis=Join-Path $attempt 'analysis.json';Write-B3NewJson $analysis ([pscustomobject]@{fake_only=$true;no_grant=$true})
        $v=[pscustomobject]@{schema='TBS5530-B3-OFFLINE-VALIDATION/v1';card_sha256=$ExpectedB3CardSha256;session_id=$faultCard.session_id;
            ordinal=1;physical_recovery_epoch=$epoch;decision='RETRY_PRE_IO';analyzerPass=$false;tsduckPass=$false;throughputPass=$false;pidServiceMatch=$false;
            raw_sha256=$null;analysis_manifest_path=$analysis;analysis_manifest_sha256=(Get-B3Hash ([IO.File]::ReadAllBytes($analysis)))}
        Write-B3NewJson (Join-Path $attempt 'offline_validation.json') $v
        $review=Assert-B3Budget ([IO.File]::ReadAllBytes($manifestPath)) $ExpectedB3CardSha256 $faultCard 2
        Check ($review.decision -ceq 'RETRY_PRE_IO') 'future pregrant permits reviewed next ordinal without drain claim'
        $proofPath=Join-Path $attempt 'pre-grant-evidence-manifest.json';$proof=[IO.File]::ReadAllBytes($proofPath)
        [IO.File]::WriteAllBytes($proofPath,[byte[]]($proof+[byte]32))
        Refuse {Assert-B3Budget ([IO.File]::ReadAllBytes($manifestPath)) $ExpectedB3CardSha256 $faultCard 2} 'pregrant evidence cannot change'
        [IO.File]::WriteAllBytes($proofPath,$proof)
        $receipt.grant_may_have_been_published=$true
        Check (-not(Test-B3PreGrantRefusal $receipt)) 'grant publication possibility cannot use pregrant exception'
    }else{
        Check ($receipt.session_closed -and -not $receipt.drained -and -not $receipt.pre_io_refusal -and $receipt.grant_may_have_been_published -and $script:grantCalls -eq 1) 'missing drain after potential grant remains closed'
        Refuse {Assert-B3Budget ([IO.File]::ReadAllBytes($manifestPath)) $ExpectedB3CardSha256 $faultCard 2} 'uncertain grant cannot retry'
    }
}
$script:fakeFault=''
# Legacy regression: both scripts must parse, while B3 is routed before legacy B2 history guards.
$null=[Management.Automation.Language.Parser]::ParseFile($runtime,[ref]$tokens,[ref]$errors)
Check ($errors.Count -eq 0) 'runtime parse'
$runtimeText=[IO.File]::ReadAllText($runtime)
Check ($runtimeText.IndexOf("if (`$ReadApi -ceq 'NATIVE_USBFS_STREAM')") -lt $runtimeText.IndexOf("`$b2Reload =")) 'B3 branch precedes historical guards'
Check ($ast.Extent.Text.Contains('StandardOutput.BaseStream.CopyToAsync($file)') -and $ast.Extent.Text.Contains('if ($AfterDrain) { 10000 } else { 1500 }')) 'binary transport and postdrain 10s bound'
$legacyAst=[Management.Automation.Language.Parser]::ParseFile($runtime,[ref]$tokens,[ref]$errors)
foreach($name in @('Get-TBS5530AdbFailure','Test-TBS5530DrainedTerminal','ConvertTo-TBS5530WindowsArgument','Get-TBS5530Sha256Token',
    'Get-TBS5530AutonomousExitCode','Test-TBS5530LaunchChallenge','New-TBS5530HeartbeatCommand','Read-TBS5530HeartbeatReply')){
    $old=$legacyAst.Find({param($n)$n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true)
    $new=$ast.Find({param($n)$n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true)
    Check ($old.Extent.Text.Replace("`r`n","`n") -ceq $new.Extent.Text.Replace("`r`n","`n")) ('shared unchanged guard '+$name)
}
foreach($scriptPath in @($path,$runtime)){
    $bytes=[IO.File]::ReadAllBytes($scriptPath);Check (($bytes[0..2]-join ',') -ceq '239,187,191') 'UTF8 BOM'
}
$summary=[pscustomobject]@{status='PASS';checks=$checks;real_adb_calls=0;fake_transport_calls=$script:fakeCalls;binary_exports=$script:binaryExports;
    b3_helper_sha256=$b3Hash;autonomous_helper_sha256=$runtimeHash;hardware='NOT_RUN';root_cause_confirmed=$false}
Write-B3NewJson (Join-Path $root 'test-summary.json') $summary
Write-Output ("PASS: B3 $checks/$checks; fake-only, no real ADB/hardware/VM/WSL; evidence: "+$root)
