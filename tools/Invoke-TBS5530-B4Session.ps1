# SPDX-License-Identifier: GPL-2.0-or-later
<#
.SYNOPSIS
Một lượt Android B4 theo card đã khóa; không tự thử lại hoặc tạo verdict.
.DESCRIPTION
TBS5530-B4-CARD/v1 khóa approval='OK B4', live_date (yyyy-MM-dd), time_zone_id
(Windows TimeZoneInfo ID), utc_offset_minutes, budget_id, session_id, budget_directory,
session_directory (bằng budget_directory), raw_directory, report_directory, max_attempts,
max_android (1..4), max_windows (0..3), attempts[ordinal,physical_recovery_epoch,
allowed_receive_configs[queue_depth=16|32,urb_bytes=16384]], model/api/boot_count,
source_git_sha, source_manifest_path/source_manifest_sha256, app_apk_sha256,
autonomous_helper_sha256, b3_helper_sha256, b4_helper_sha256, firmware_sha256,
reload_plan_sha256, golden_sha256, duration_ms (1000..60000, mặc định 30000),
max_bytes=268435456, tuner_rssi (boolean, mặc định false), ttl_ms=300000,
heartbeat_ms=2000, permission_wait_ms=30000, rf_input='RF_IN', antenna='PASSIVE'
hoặc 'EXTERNALLY_POWERED_NO_TUNER_DC', antenna_dc=false.
Source manifest: {source_git_sha, files:[{path:<repo-relative>,sha256:<hex>}]}.
Budget directory có tên budget-<budget_id>, không theo Android session UUID. HEADER,
CONSUME và RECEIPT append-only giữ ngân sách khi đổi UUID; refusal chuẩn bị cũng consume.
Mỗi invocation cần Approval='OK B4', budget/cold/RF/antenna proof riêng. Permission-only
được kiểm trước live challenge/grant; host chỉ đợi 30 giây, không auto-tap.
B4 launch/challenge/grant: b4_enabled=true, b4_duration_ms, b4_max_bytes, b4_tuner_rssi;
b3_queue_depth=16|32, b3_urb_bytes=16384, b3_duration_ms/b3_max_bytes bằng giới hạn B4.
Grant có cả b3_* budget fields tương thích và b4_card_sha256/budget_id/session_id/
attempt_ordinal/max_attempts/physical_cold_seconds. Bootstrap/reload/golden giữ nguyên.
Sau typed drain mới export received.ts, native-stream .ts/.jsonl và
files/phase31-signal-monitor-<session>.jsonl.
Binary export có size/hash trước-sau, giới hạn từng file; không in raw vào console.
Lượt tiếp theo cần offline_validation.json TBS5530-B4-OFFLINE-VALIDATION/v1:
card_sha256,budget_id,session_id,ordinal,physical_recovery_epoch,result,raw_sha256,
export_manifest_sha256,analysis_manifest_path,analysis_manifest_sha256,evidence.
result: B4_ANDROID_PARITY_PASS, RF_LIMITED, B4_ANDROID_GAP, PRE_IO_REFUSED hoặc
PENDING_ANALYSIS. PENDING_ANALYSIS chặn lượt tiếp theo. evidence là array không rỗng
[{path,sha256}] trỏ tới file thật dưới reports/logs đã khóa; mọi export hiện hữu được
kiểm lại hash/size, không có bốn quality flags hoặc RETRY_STARVATION của B3.
Helper chỉ ghi PENDING_ANALYSIS trong host classification, không tạo verdict offline.
-75, detach sau reload, uncertainty hoặc thiếu drain đóng toàn budget; không retry.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [string]$ExpectedModel='SM-X926B',
    [int]$ExpectedApi=36,
    [Parameter(Mandatory)][ValidateRange(0,2147483647)][int]$ExpectedBootCount,
    [ValidateRange(10,300)][int]$DurationSeconds=300,
    [string]$FirmwarePath,
    [string]$PhysicalRecoveryEpoch,
    [switch]$PhysicalRecoveryConfirmed,
    [ValidateRange(5,3600)][int]$PhysicalColdSeconds=5,
    [switch]$RfInConfirmed,
    [switch]$AntennaNoDcConfirmed,
    [switch]$GrantBoundedSession,
    [switch]$ProofOfGuardPassed,
    [switch]$ApproveB4SessionBudget,
    [string]$Approval,
    [string]$B4CardPath,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedB4CardSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedB4HelperSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedB3HelperSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedHelperSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedAppApkSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedFrontendGoldenSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedReloadPlanSha256,
    [string]$B4SessionDirectory,
    [ValidateRange(1,4)][int]$B4AttemptOrdinal=1,
    [ValidateSet(16,32)][int]$B4QueueDepth=16,
    [ValidateSet(16384)][int]$B4UrbBytes=16384,
    [ValidateRange(1000,60000)][int]$B4DurationMillis=30000,
    [ValidateSet(268435456)][int]$B4MaxBytes=268435456,
    [bool]$B4TunerRssi=$false,
    [string]$OutputDirectory,
    [string]$AdbPath='D:\Android\Sdk\platform-tools\adb.exe'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$package='info.martinmarinov.dvbdriver'
$firmwareSha='b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc'
$script:adbStage='B4_PREFLIGHT'

function Import-B4SharedGuards([string]$Path,[string]$ExpectedHash) {
    if ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ExpectedHash) { throw 'B4_B3_HELPER_HASH_MISMATCH' }
    $tokens=$null; $errors=$null
    $ast=[Management.Automation.Language.Parser]::ParseFile($Path,[ref]$tokens,[ref]$errors)
    if ($errors.Count -ne 0) { throw 'B4_SHARED_GUARDS_PARSE_FAILED' }
    $shared=@('Get-TBS5530AdbFailure','Test-TBS5530DrainedTerminal','ConvertTo-TBS5530WindowsArgument','Get-TBS5530Sha256Token',
        'Test-TBS5530LaunchChallenge','New-TBS5530HeartbeatCommand','Read-TBS5530HeartbeatReply','Get-B3Hash','Test-B3Integer','Assert-B3Guid',
        'Read-B3Manifest','Add-B3ManifestRecord','Test-B3PreGrantRefusal','Invoke-B3Adb','Get-B3Elapsed','Assert-B3Identity','Assert-B3Foreground',
        'Assert-B3Apk','Test-B3Drained','Test-B3SessionClosed','Assert-B3PermissionSnapshot','Write-B3NewJson','Write-B3PreGrantEvidence','Exchange-B3Heartbeat')
    foreach ($function in @($ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst]},$true))) {
        if ($function.Name -cnotin $shared) { continue }
        # Import declarations only: never execute B3's main or expand B3 live limits.
        $definition=$function.Extent.Text.Replace('B3','B4')
        $definition=$definition -replace '^function ([A-Za-z0-9-]+)', 'function script:$1'
        Invoke-Expression $definition
    }
}
function Assert-B4PrivatePath([string]$Path,[string]$Root) {
    $full=[IO.Path]::GetFullPath($Path)
    if (-not $full.StartsWith($Root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -or $full -cne $Path) { throw 'B4_PRIVATE_CANONICAL_PATH_REQUIRED' }
    return $full
}
function Assert-B4LiveDay([object]$Card,[DateTimeOffset]$Now=[DateTimeOffset]::UtcNow) {
    if ($Card.live_date -isnot [string] -or $Card.live_date -cnotmatch '^\d{4}-\d{2}-\d{2}$' -or
        $Card.time_zone_id -isnot [string] -or -not (Test-B4Integer $Card.utc_offset_minutes)) { throw 'B4_LIVE_DAY_INVALID' }
    try { $zone=[TimeZoneInfo]::FindSystemTimeZoneById($Card.time_zone_id); $local=[TimeZoneInfo]::ConvertTime($Now,$zone) }
    catch { throw 'B4_TIME_ZONE_INVALID' }
    if ($local.ToString('yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture) -cne $Card.live_date -or
        $local.Offset.TotalMinutes -ne $Card.utc_offset_minutes) { throw 'B4_APPROVED_LIVE_DAY_EXPIRED' }
}
function Assert-B4Source([object]$Card,[string]$Repository) {
    $head=& git -C $Repository rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $head -cne $Card.source_git_sha) { throw 'B4_SOURCE_HEAD_MISMATCH' }
    $manifestPath=Assert-B4PrivatePath $Card.source_manifest_path 'D:\TSDuck\reports'
    if ((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Card.source_manifest_sha256) { throw 'B4_SOURCE_MANIFEST_HASH_MISMATCH' }
    $manifest=[IO.File]::ReadAllText($manifestPath)|ConvertFrom-Json
    if ($manifest.source_git_sha -cne $Card.source_git_sha -or @($manifest.files).Count -lt 1 -or @($manifest.files).Count -gt 512) { throw 'B4_SOURCE_MANIFEST_INVALID' }
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($file in $manifest.files) {
        if ($file.path -isnot [string] -or [IO.Path]::IsPathRooted($file.path) -or $file.path -match '(^|[\\/])\.\.([\\/]|$)' -or
            $file.sha256 -cnotmatch '^[a-f0-9]{64}$' -or -not $seen.Add($file.path)) { throw 'B4_SOURCE_ENTRY_INVALID' }
        $path=[IO.Path]::GetFullPath((Join-Path $Repository $file.path))
        if (-not $path.StartsWith($Repository.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -or
            (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $file.sha256) { throw 'B4_SOURCE_FILE_CHANGED' }
    }
}
function Set-B4Overrides {
    function script:Get-B4AllowedReceiveConfigs([object]$Attempt) {
        $configs=if ($null -ne $Attempt.PSObject.Properties['allowed_receive_configs']) { @($Attempt.allowed_receive_configs) } else { @($Attempt) }
        if ($configs.Count -lt 1 -or $configs.Count -gt 2) { throw 'B4_CARD_RECEIVE_CONFIG_INVALID' }
        $keys=[Collections.Generic.HashSet[int]]::new()
        foreach ($config in $configs) {
            if (-not (Test-B4Integer $config.queue_depth) -or $config.queue_depth -notin @(16,32) -or
                -not (Test-B4Integer $config.urb_bytes) -or $config.urb_bytes -ne 16384 -or -not $keys.Add($config.queue_depth)) { throw 'B4_CARD_RECEIVE_CONFIG_INVALID' }
        }
        return $configs
    }
    function script:Assert-B4Card([object]$Card,[hashtable]$Pins,[int]$Ordinal,[string]$Epoch,[int]$Depth,[int]$Chunk) {
        Assert-B4Guid $Card.session_id; Assert-B4Guid $Card.budget_id
        foreach ($key in $Pins.Keys) {
            if ($null -eq $Card.PSObject.Properties[$key] -or $null -eq $Card.$key -or $Card.$key -cne $Pins[$key] -or
                ((Test-B4Integer $Pins[$key]) -and -not (Test-B4Integer $Card.$key)) -or
                ($Pins[$key] -is [bool] -and $Card.$key -isnot [bool]) -or ($Pins[$key] -is [string] -and $Card.$key -isnot [string])) { throw ('B4_CARD_PIN_MISMATCH:'+$key) }
        }
        if (-not (Test-B4Integer $Card.max_attempts) -or $Card.max_attempts -lt 1 -or $Card.max_attempts -gt 4 -or
            -not (Test-B4Integer $Card.max_android) -or $Card.max_android -lt 1 -or $Card.max_android -gt 4 -or $Card.max_attempts -gt $Card.max_android -or
            -not (Test-B4Integer $Card.max_windows) -or $Card.max_windows -lt 0 -or $Card.max_windows -gt 3 -or
            @($Card.attempts).Count -ne $Card.max_attempts -or $Ordinal -lt 1 -or $Ordinal -gt $Card.max_attempts) { throw 'B4_CARD_BUDGET_INVALID' }
        if ($Card.schema -cne 'TBS5530-B4-CARD/v1' -or $Card.approval -cne 'OK B4' -or
            -not (Test-B4Integer $Card.duration_ms) -or $Card.duration_ms -lt 1000 -or $Card.duration_ms -gt 60000 -or
            -not (Test-B4Integer $Card.max_bytes) -or $Card.max_bytes -ne 268435456 -or
            $Card.tuner_rssi -isnot [bool] -or $Card.antenna_dc -isnot [bool] -or $Card.antenna_dc -or $Card.rf_input -cne 'RF_IN' -or
            $Card.antenna -cnotin @('PASSIVE','EXTERNALLY_POWERED_NO_TUNER_DC')) { throw 'B4_CARD_SCOPE_INVALID' }
        if ($Card.budget_directory -cne $Card.session_directory -or
            [IO.Path]::GetFileName($Card.budget_directory) -cne ('budget-'+$Card.budget_id)) { throw 'B4_STABLE_BUDGET_PATH_REQUIRED' }
        $null=Assert-B4PrivatePath $Card.budget_directory 'D:\TSDuck\logs'
        $null=Assert-B4PrivatePath $Card.raw_directory 'D:\TSDuck'
        $null=Assert-B4PrivatePath $Card.report_directory 'D:\TSDuck\reports'
        Assert-B4LiveDay $Card
        $epochs=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal); $i=0
        foreach ($attempt in $Card.attempts) {
            $i++; Assert-B4Guid $attempt.physical_recovery_epoch
            if (-not (Test-B4Integer $attempt.ordinal) -or $attempt.ordinal -ne $i -or -not $epochs.Add($attempt.physical_recovery_epoch)) { throw 'B4_CARD_ATTEMPT_INVALID' }
            $null=Get-B4AllowedReceiveConfigs $attempt
        }
        $selected=$Card.attempts[$Ordinal-1]
        if ($selected.physical_recovery_epoch -cne $Epoch -or @((Get-B4AllowedReceiveConfigs $selected)|Where-Object {$_.queue_depth -eq $Depth -and $_.urb_bytes -eq $Chunk}).Count -ne 1) { throw 'B4_CARD_ATTEMPT_BINDING_MISMATCH' }
    }
    function script:Assert-B4Challenge([object]$Value,[string]$Launch,[long]$Now) {
        if (-not (Test-TBS5530LaunchChallenge $Value $Launch $false $Now)) { throw 'B4_CHALLENGE_STALE_OR_FOREIGN' }
        Assert-B4Guid $Value.session
        $pins=@{model=$ExpectedModel;api=$ExpectedApi;boot_count=$ExpectedBootCount;host_profile='samsung-sm-x926b-api36';vid=29516;pid=21808
            firmware_sha256=$firmwareSha;firmware_protocol='TBS_SOURCE_DUAL_RESET';ts_variant='NATIVE_USBFS_STREAM';frontend_experiment='B2_RELOAD_AFTER_SET_CONFIG'
            golden_sha256=$ExpectedFrontendGoldenSha256;app_apk_sha256=$ExpectedAppApkSha256;control_mirror=$true;explicit_set_interface_alt0=$false;sony_ts_output_readback=$false
            retry=0;max_upload_per_attachment=1;max_reload_per_physical_epoch=1;max_session_ms=300000;heartbeat_ms=2000
            fx2_reload_option='B2_RELOAD_AFTER_SET_CONFIG';fx2_reload_plan_sha256=$ExpectedReloadPlanSha256;bootstrap_proof='SAME_SESSION_ORIGINAL132_APP_BOUNDARIES_DRAIN_REQUIRED'
            b3_queue_depth=$B4QueueDepth;b3_urb_bytes=$B4UrbBytes;b3_max_bytes=$B4MaxBytes;b3_duration_ms=$B4DurationMillis
            b4_enabled=$true;b4_duration_ms=$B4DurationMillis;b4_max_bytes=$B4MaxBytes;b4_tuner_rssi=$B4TunerRssi}
        foreach ($key in $pins.Keys) {
            if ($null -eq $Value.PSObject.Properties[$key] -or $Value.$key -cne $pins[$key] -or
                ($pins[$key] -is [bool] -and $Value.$key -isnot [bool]) -or ((Test-B4Integer $pins[$key]) -and -not (Test-B4Integer $Value.$key))) { throw ('B4_CHALLENGE_PIN_MISMATCH:'+$key) }
        }
        $header="TBS5530-FX2-RELOAD/v1`nHOST samsung-sm-x926b-api36`nINITIAL_HISTORY_SHA256 b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb`n"
        $h=Get-B4Hash ([Text.Encoding]::UTF8.GetBytes($header))
        if ($null -eq $Value.PSObject.Properties['attachment_history_sha256'] -or $null -ne $Value.attachment_history_sha256 -or $Value.fx2_reload_store_sha256 -cne $h -or
            $Value.fx2_reload_anchor_sha256 -cne (Get-B4Hash ([Text.Encoding]::UTF8.GetBytes("TBS5530-FX2-RELOAD-ANCHOR/v1`n$h`n"))) -or $Value.source_git_sha -cne $card.source_git_sha) { throw 'B4_SESSION_RAM_INITIAL_WITNESS_MISMATCH' }
    }
    function script:Get-B4ExportPaths([string]$Session) {
        Assert-B4Guid $Session
        $base='files/autonomous-v1-'+$Session+'/'
        $names=@('report.json','received.ts','handoff-events.jsonl','control-ep0.jsonl','reload-a0-native.jsonl','reload-native-boundaries.json',
            'b3-reload-journal.txt','b3-reload-anchor.txt','ledger.jsonl','permission.json','initial-descriptors.bin','receive-audit.jsonl',
            'receive-audit.jsonl.started','receive-audit.jsonl.complete')
        return @($names|ForEach-Object {$base+$_})+@(('files/phase31-native-stream-'+$Session+'.jsonl'),('files/phase31-native-stream-'+$Session+'.ts'),('files/phase31-signal-monitor-'+$Session+'.jsonl'),
            'files/autonomous-v1-samsung-sm-x926b-api36-attachments.txt','no_backup/fx2-reload-v1.txt','no_backup/fx2-reload-v1.anchor',
            'no_backup/fx2-reload-v1.initialized','no_backup/fx2-reload-v1-init.consumed.json')
    }
    function script:Get-B4ExportLimit([string]$Path) {
        if ($Path.EndsWith('.ts',[StringComparison]::Ordinal)) { return 268435456 }
        if ($Path.EndsWith('.jsonl',[StringComparison]::Ordinal)) { return 67108864 }
        return 1048576
    }
    function script:Assert-B4ExportCall([string[]]$Arguments,[object]$Report,[string]$Session,[string]$BinaryDestination) {
        if (-not (Test-B4Drained $Report)) { throw 'B4_EXPORT_REQUIRES_TYPED_DRAIN' }
        foreach ($remote in @(Get-B4ExportPaths $Session)) {
            $exists='if [ -f '+$remote+' ]; then printf PRESENT; fi'
            $bounded='head -c '+((Get-B4ExportLimit $remote)+1)+' '+$remote
            foreach ($candidate in @(@('shell','run-as',$package,'sh','-c',("'"+$exists+"'")),@('shell','run-as',$package,'sha256sum',$remote),
                @('shell','run-as',$package,'wc','-c',$remote),@('exec-out','run-as',$package,'sh','-c',$bounded))) {
                if (($Arguments -join "`n") -ceq ($candidate -join "`n")) {
                    if (($Arguments[0] -ceq 'exec-out') -ne (-not [string]::IsNullOrEmpty($BinaryDestination))) { throw 'B4_EXPORT_BINARY_SINK_REQUIRED' }
                    return
                }
            }
        }
        throw 'B4_EXPORT_OUTSIDE_EXACT_READ_ONLY_ALLOWLIST'
    }
    function script:Export-B4Evidence([string]$Session,[object]$Report,[string]$Destination) {
        if (-not (Test-B4Drained $Report)) { throw 'B4_EXPORT_REQUIRES_TYPED_DRAIN' }
        $manifest=@(); $index=0
        foreach ($remote in @(Get-B4ExportPaths $Session)) {
            $index++; $cmd='if [ -f '+$remote+' ]; then printf PRESENT; fi'
            $present=Invoke-B4Adb @('shell','run-as',$package,'sh','-c',("'"+$cmd+"'")) -AfterDrain $true
            if ($present -ceq '') {
                $manifest+=[pscustomobject]@{path=$remote;present=$false}
                $hasStream=$null -ne $Report.PSObject.Properties['native_stream'] -and $null -ne $Report.native_stream
                if ($remote.EndsWith('/report.json') -or ($hasStream -and ($remote -match 'received\.ts$|phase31-native-stream-.*\.(ts|jsonl)$|phase31-signal-monitor-.*\.jsonl$|handoff-events\.jsonl$|control-ep0\.jsonl$|reload-a0-native\.jsonl$|reload-native-boundaries\.json$|b3-reload-(journal|anchor)\.txt$'))) { throw 'B4_DRAINED_EVIDENCE_FILE_MISSING' }
                continue
            }
            if ($present -cne 'PRESENT') { throw 'B4_EXPORT_PRESENCE_UNVERIFIED' }
            $size=Invoke-B4Adb @('shell','run-as',$package,'wc','-c',$remote) -AfterDrain $true
            if ($size -cnotmatch '^\s*([0-9]{1,12})(?:\s|$)') { throw 'B4_EXPORT_SIZE_UNVERIFIED' }
            $length=[long]$Matches[1]; $limit=Get-B4ExportLimit $remote
            if ($length -gt $limit) { throw 'B4_EXPORT_SIZE_LIMIT_EXCEEDED' }
            $folder=if ($remote.EndsWith('.ts')) { $rawAttemptPath } else { $Destination }
            $local=Join-Path $folder ($index.ToString('D2')+'-'+[IO.Path]::GetFileName($remote))
            $before=Get-TBS5530Sha256Token (Invoke-B4Adb @('shell','run-as',$package,'sha256sum',$remote) -AfterDrain $true)
            $cmd='head -c '+($limit+1)+' '+$remote
            # exec-out passes argv directly; shell-command quotes would become literal command bytes.
            Invoke-B4Adb @('exec-out','run-as',$package,'sh','-c',$cmd) -BinaryDestination $local -AfterDrain $true
            $after=Get-TBS5530Sha256Token (Invoke-B4Adb @('shell','run-as',$package,'sha256sum',$remote) -AfterDrain $true)
            $hostHash=(Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash.ToLowerInvariant()
            if ((Get-Item -LiteralPath $local).Length -ne $length -or $hostHash -cne $before -or $hostHash -cne $after) { throw 'B4_EXPORT_SIZE_OR_HASH_MISMATCH' }
            $manifest+=[pscustomobject]@{path=$remote;present=$true;file=[IO.Path]::GetFileName($local);local_path=$local;bytes=$length;sha256=$hostHash}
        }
        Write-B4NewJson (Join-Path $Destination 'export-manifest.json') $manifest
        return ,$manifest
    }
}

function Read-B4Review([object]$Card,[string]$CardHash,[object]$Receipt,[int]$Ordinal) {
    $preGrant=Test-B4PreGrantRefusal $Receipt
    if ($Receipt.session_closed -or (-not $Receipt.drained -and -not $preGrant)) { throw 'B4_PREVIOUS_CAPTURE_NOT_REVIEWABLE' }
    $attempt=Join-Path $Card.budget_directory ('attempt-'+$Ordinal)
    $path=Join-Path $attempt 'offline_validation.json'
    $bytes=[IO.File]::ReadAllBytes($path)
    if ($bytes.Length -gt 65536) { throw 'B4_OFFLINE_VALIDATION_OVERSIZED' }
    $v=[Text.UTF8Encoding]::new($false,$true).GetString($bytes).TrimStart([char]0xFEFF)|ConvertFrom-Json
    if ($v.schema -cne 'TBS5530-B4-OFFLINE-VALIDATION/v1' -or $v.card_sha256 -cne $CardHash -or $v.budget_id -cne $Card.budget_id -or
        $v.session_id -cne $Card.session_id -or -not (Test-B4Integer $v.ordinal) -or $v.ordinal -ne $Ordinal -or
        $v.physical_recovery_epoch -cne $Card.attempts[$Ordinal-1].physical_recovery_epoch -or
        $v.result -cnotin @('B4_ANDROID_PARITY_PASS','RF_LIMITED','B4_ANDROID_GAP','PRE_IO_REFUSED','PENDING_ANALYSIS')) { throw 'B4_OFFLINE_VALIDATION_SCOPE_OR_RESULT_INVALID' }
    if ($v.result -ceq 'PENDING_ANALYSIS') { throw 'B4_OFFLINE_ANALYSIS_PENDING' }
    if ($v.result -ceq 'PRE_IO_REFUSED') {
        if ($Receipt.pre_io_refusal -isnot [bool] -or -not $Receipt.pre_io_refusal) { throw 'B4_PRE_IO_OBSERVATION_REQUIRED' }
    } elseif ($preGrant -or -not $Receipt.capture_complete) { throw 'B4_CAPTURE_EVIDENCE_REQUIRED' }
    if ($preGrant) {
        $evidencePath=Join-Path $attempt 'pre-grant-evidence-manifest.json'
        if ((Get-FileHash -LiteralPath $evidencePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Receipt.pre_grant_evidence_sha256) { throw 'B4_PRE_GRANT_EVIDENCE_CHANGED' }
        $e=[IO.File]::ReadAllText($evidencePath)|ConvertFrom-Json
        if ($e.schema -cne 'TBS5530-B4-PRE-GRANT-EVIDENCE/v1' -or $e.card_sha256 -cne $CardHash -or $e.session_id -cne $Card.session_id -or
            $e.ordinal -ne $Ordinal -or $e.physical_recovery_epoch -cne $Card.attempts[$Ordinal-1].physical_recovery_epoch -or $e.error_file -cne 'host-refusal.json' -or
            (Get-FileHash -LiteralPath (Join-Path $attempt 'host-refusal.json') -Algorithm SHA256).Hash.ToLowerInvariant() -cne $e.error_sha256) { throw 'B4_PRE_GRANT_ERROR_EVIDENCE_CHANGED' }
    }
    $exportPath=Join-Path $attempt 'export-manifest.json'
    if ($v.export_manifest_sha256 -cnotmatch '^[a-f0-9]{64}$' -or $v.export_manifest_sha256 -cne $Receipt.export_manifest_sha256 -or
        (Get-FileHash -LiteralPath $exportPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $v.export_manifest_sha256) { throw 'B4_EXPORT_MANIFEST_CHANGED' }
    $exports=@([IO.File]::ReadAllText($exportPath)|ConvertFrom-Json)
    foreach ($e in $exports) {
        if ($e.present -isnot [bool]) { throw 'B4_EXPORT_ENTRY_INVALID' }
        if (-not $e.present) { continue }
        $folder=if ($e.path.EndsWith('.ts')) { Join-Path $Card.raw_directory ('attempt-'+$Ordinal) } else { $attempt }
        $null=Assert-B4PrivatePath $e.local_path $folder
        if ($e.sha256 -cnotmatch '^[a-f0-9]{64}$' -or -not (Test-B4Integer $e.bytes) -or $e.bytes -lt 0 -or $e.bytes -gt (Get-B4ExportLimit $e.path) -or
            (Get-Item -LiteralPath $e.local_path).Length -ne $e.bytes -or (Get-FileHash -LiteralPath $e.local_path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $e.sha256) { throw 'B4_EXPORTED_EVIDENCE_CHANGED' }
    }
    $raw=@($exports|Where-Object {$_.path -clike '*/received.ts' -and $_.present})
    if ($v.result -ceq 'PRE_IO_REFUSED' -and $raw.Count -eq 0) {
        if ($null -ne $v.raw_sha256 -or $null -ne $Receipt.raw_sha256) { throw 'B4_PRE_IO_RAW_MUST_BE_NULL' }
    } elseif ($raw.Count -ne 1 -or $v.raw_sha256 -cne $Receipt.raw_sha256 -or $raw[0].sha256 -cne $Receipt.raw_sha256) { throw 'B4_RAW_EVIDENCE_MISMATCH' }
    $analysisPath=Assert-B4PrivatePath $v.analysis_manifest_path $Card.report_directory
    if ($v.analysis_manifest_sha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath $analysisPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $v.analysis_manifest_sha256 -or
        @($v.evidence).Count -lt 1 -or @($v.evidence).Count -gt 32) { throw 'B4_ANALYSIS_EVIDENCE_REQUIRED' }
    foreach ($e in $v.evidence) {
        $full=[IO.Path]::GetFullPath($e.path)
        if (-not ($full.StartsWith($Card.report_directory.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -or $full.StartsWith($attempt+'\',[StringComparison]::OrdinalIgnoreCase)) -or
            $e.sha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant() -cne $e.sha256) { throw 'B4_VERDICT_EVIDENCE_CHANGED' }
    }
    return [pscustomobject]@{sha256=(Get-B4Hash $bytes);result=$v.result;raw_sha256=$v.raw_sha256;analysis_manifest_sha256=$v.analysis_manifest_sha256}
}
function Assert-B4Ledger([byte[]]$Bytes,[string]$CardHash,[object]$Card,[int]$Ordinal) {
    if ($Bytes.Length -eq 0) { if ($Ordinal -ne 1) { throw 'B4_FIRST_ORDINAL_REQUIRED' }; return }
    $text=[Text.UTF8Encoding]::new($false,$true).GetString($Bytes)
    if (-not $text.EndsWith("`n") -or $text.Contains("`r")) { throw 'B4_MANIFEST_PARTIAL_OR_CORRUPT' }
    $prefix=[byte[]]@(); $next=1; $pending=$false; $previous=$null
    foreach ($line in $text.TrimEnd([char]10).Split([char]10)) {
        $row=$line|ConvertFrom-Json
        if ($row.previous_sha256 -cne (Get-B4Hash $prefix)) { throw 'B4_MANIFEST_CHAIN_INVALID' }
        if ($prefix.Length -eq 0) {
            if ($row.event -cne 'HEADER' -or $row.card_sha256 -cne $CardHash -or $row.budget_id -cne $Card.budget_id -or
                $row.max_attempts -ne $Card.max_attempts -or $row.max_windows -ne $Card.max_windows) { throw 'B4_MANIFEST_BUDGET_MISMATCH' }
        } elseif ($row.event -ceq 'CONSUME' -and -not $pending) {
            if (-not (Test-B4Integer $row.ordinal) -or $row.ordinal -ne $next -or $next -gt $Card.max_attempts -or $row.epoch -cne $Card.attempts[$next-1].physical_recovery_epoch) { throw 'B4_MANIFEST_CONSUME_INVALID' }
            Assert-B4Card $Card @{} $next $row.epoch $row.queue_depth $row.urb_bytes
            if ($next -gt 1) {
                $review=Read-B4Review $Card $CardHash $previous ($next-1)
                if ($row.previous_offline_validation_sha256 -cne $review.sha256) { throw 'B4_REVIEW_CHANGED_AFTER_CONSUME' }
            }
            $depth=$row.queue_depth; $chunk=$row.urb_bytes; $pending=$true
        } elseif ($row.event -ceq 'RECEIPT' -and $pending) {
            if ($row.ordinal -ne $next -or $row.session_closed -isnot [bool] -or $row.session_closed -or $row.drained -isnot [bool] -or
                (-not $row.drained -and -not (Test-B4PreGrantRefusal $row)) -or [string]::IsNullOrWhiteSpace($row.result) -or
                $row.queue_depth -ne $depth -or $row.urb_bytes -ne $chunk) { throw 'B4_SESSION_CLOSED' }
            $previous=$row; $next++; $pending=$false
        } else { throw 'B4_MANIFEST_SEQUENCE_INVALID' }
        $prefix=[byte[]]($prefix+[Text.Encoding]::UTF8.GetBytes($line+"`n"))
    }
    if ($pending -or $Ordinal -ne $next -or $Ordinal -gt $Card.max_attempts) { throw 'B4_CONSUMED_OR_BUDGET_EXHAUSTED' }
    if ($Ordinal -gt 1) { return Read-B4Review $Card $CardHash $previous ($Ordinal-1) }
}
function Wait-B4Permission([string]$Token) {
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while ($watch.ElapsedMilliseconds -lt 30000) {
        $cmd='if [ -f files/permission-preflight.json ]; then cat files/permission-preflight.json; fi'
        $text=Invoke-B4Adb @('shell','run-as',$package,'sh','-c',("'"+$cmd+"'"))
        if ($text) {
            $v=$text|ConvertFrom-Json
            if ($v.preflight_token -ceq $Token) {
                if ($v.state -cin @('DENIED','CANCELLED','TIMED_OUT','UNVERIFIED')) { throw 'B4_PERMISSION_ONLY_REFUSED' }
                if ($v.state -ceq 'GRANTED') { Assert-B4PermissionReady $v $Token (Get-B4Elapsed); return $v }
                if ($v.state -cnotin @('WAITING','USER_ACTION_REQUIRED')) { throw 'B4_PERMISSION_STATE_UNKNOWN' }
            }
        }
        Start-Sleep -Milliseconds 100
    }
    throw 'B4_PERMISSION_ONLY_30S_TIMEOUT'
}
function Assert-B4PermissionReady([object]$V,[string]$Token,[long]$Now) {
    foreach ($key in @('version','vid','pid','created_elapsed_ms','deadline_elapsed_ms','elapsed_ms','attachment_generation')) { if (-not (Test-B4Integer $V.$key)) { throw 'B4_PERMISSION_RECEIPT_MALFORMED' } }
    if ($V.version -ne 1 -or $V.mode -cne 'PERMISSION_ONLY' -or $V.preflight_token -cne $Token -or $V.state -cne 'GRANTED' -or $V.reason -cne 'OS_PERMISSION_VERIFIED' -or
        $V.vid -ne 29516 -or $V.pid -ne 21808 -or $V.created_elapsed_ms -lt 0 -or $V.attachment_generation -lt 0 -or
        $V.deadline_elapsed_ms-$V.created_elapsed_ms -notin @(30000,60000) -or $V.elapsed_ms -lt $V.created_elapsed_ms -or
        $Now -lt $V.elapsed_ms -or $Now-$V.elapsed_ms -ge 2000 -or $Now-$V.created_elapsed_ms -ge 30000 -or $Now -ge $V.deadline_elapsed_ms -or
        $V.device_identity_sha256 -cnotmatch '^[a-f0-9]{64}$') { throw 'B4_PERMISSION_RECEIPT_STALE_OR_UNVERIFIED' }
}
function Get-B4CaptureClassification([object]$Report,[object[]]$Exports) {
    $capture=-not (Test-B4SessionClosed $Report)
    if ($capture) {
        $raw=@($Exports|Where-Object {$_.path -clike '*/received.ts' -and $_.present})
        $capture=$null -ne $Report.native_stream -and $Report.raw_capture -ceq 'SYNCED_CLOSED' -and $Report.control_mirror -ceq 'SYNCED_CLOSED' -and
            $Report.permission_outcome -cin @('AUTO_PERMISSION_HANDLED_BY_OS','AUTO_PERMISSION_UI_HANDLED') -and $Report.fx2_reload.state -ceq 'COMPLETE' -and $Report.fx2_reload.attempted -eq 132 -and $Report.fx2_reload.completed -eq 132 -and
            (Test-B4Integer $Report.native_stream.bytes) -and $Report.native_stream.bytes -ge 0 -and $Report.native_stream.bytes -le $B4MaxBytes -and
            $raw.Count -eq 1 -and (Test-B4Integer $raw[0].bytes) -and $raw[0].bytes -ge 0 -and $raw[0].bytes -le $Report.native_stream.bytes -and $Report.raw_received_bytes -eq $raw[0].bytes
    }
    return [pscustomobject]@{classification='PENDING_ANALYSIS';capture_complete=[bool]$capture;single_run_pass=$false;root_cause_confirmed=$false;final_ts_smoke_pass=$false}
}

# B4_MAIN: import declarations, then validate every local pin before device commands.
$b3=Join-Path $PSScriptRoot 'Invoke-TBS5530-B3Session.ps1'
Import-B4SharedGuards $b3 $ExpectedB3HelperSha256
Set-B4Overrides
if (-not $GrantBoundedSession -or -not $ProofOfGuardPassed -or -not $ApproveB4SessionBudget -or $Approval -cne 'OK B4' -or
    -not $PhysicalRecoveryConfirmed -or $PhysicalColdSeconds -lt 5 -or -not $RfInConfirmed -or -not $AntennaNoDcConfirmed) { throw 'B4_EXPLICIT_BUDGET_COLD_RF_ANTENNA_PROOF_REQUIRED' }
Assert-B4Guid $PhysicalRecoveryEpoch
if ($ExpectedModel -cne 'SM-X926B' -or $ExpectedApi -ne 36 -or $DurationSeconds -ne 300 -or $B4QueueDepth -notin @(16,32) -or $B4UrbBytes -ne 16384) { throw 'B4_SCOPE_INVALID' }
foreach ($pin in @($ExpectedB4CardSha256,$ExpectedB4HelperSha256,$ExpectedB3HelperSha256,$ExpectedHelperSha256,$ExpectedAppApkSha256,$ExpectedFrontendGoldenSha256,$ExpectedReloadPlanSha256)) { if ($pin -cnotmatch '^[a-f0-9]{64}$') { throw 'B4_EXACT_HASHES_REQUIRED' } }
$runtime=Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
if ((Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ExpectedB4HelperSha256 -or
    (Get-FileHash -LiteralPath $runtime -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ExpectedHelperSha256) { throw 'B4_HELPER_HASH_MISMATCH' }
if ((Get-Item -LiteralPath $FirmwarePath).Length -ne 8192 -or (Get-FileHash -LiteralPath $FirmwarePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $firmwareSha) { throw 'B4_FIRMWARE_HASH_MISMATCH' }
$cardBytes=[IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $B4CardPath))
if ($cardBytes.Length -gt 65536 -or (Get-B4Hash $cardBytes) -cne $ExpectedB4CardSha256) { throw 'B4_CARD_HASH_MISMATCH' }
$card=[Text.UTF8Encoding]::new($false,$true).GetString($cardBytes).TrimStart([char]0xFEFF)|ConvertFrom-Json
$sessionPath=[IO.Path]::GetFullPath($B4SessionDirectory)
$pins=@{schema='TBS5530-B4-CARD/v1';approval='OK B4';model=$ExpectedModel;api=$ExpectedApi;boot_count=$ExpectedBootCount;session_directory=$sessionPath
    app_apk_sha256=$ExpectedAppApkSha256;autonomous_helper_sha256=$ExpectedHelperSha256;b3_helper_sha256=$ExpectedB3HelperSha256;b4_helper_sha256=$ExpectedB4HelperSha256
    firmware_sha256=$firmwareSha;reload_plan_sha256=$ExpectedReloadPlanSha256;golden_sha256=$ExpectedFrontendGoldenSha256
    max_bytes=$B4MaxBytes;duration_ms=$B4DurationMillis;tuner_rssi=$B4TunerRssi;ttl_ms=300000;heartbeat_ms=2000;permission_wait_ms=30000}
Assert-B4Card $card $pins $B4AttemptOrdinal $PhysicalRecoveryEpoch $B4QueueDepth $B4UrbBytes
Assert-B4Source $card ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')))
$attemptPath=Join-Path $sessionPath ('attempt-'+$B4AttemptOrdinal)
$rawAttemptPath=Join-Path $card.raw_directory ('attempt-'+$B4AttemptOrdinal)
if ($OutputDirectory -and [IO.Path]::GetFullPath($OutputDirectory) -cne $attemptPath) { throw 'B4_OUTPUT_MUST_MATCH_BUDGET_ATTEMPT' }
if ((Test-Path -LiteralPath $attemptPath) -or (Test-Path -LiteralPath $rawAttemptPath)) { throw 'B4_ATTEMPT_OUTPUT_ALREADY_EXISTS' }
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw 'B4_PINNED_ADB_MISSING' }
if (-not (Test-Path -LiteralPath $sessionPath)) { $null=New-Item -ItemType Directory -Path $sessionPath }
$manifestPath=Join-Path $sessionPath 'session-manifest.jsonl'
$manifestStream=[IO.FileStream]::new($manifestPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$consumed=$false; $granted=$false; $terminalReport=$null; $exitCode=1; $failure='HOST_ABORTED_NO_RETRY'; $challenge=$null; $classification=$null
$captureRawSha=$null; $exportManifestSha=$null; $preGrantEvidenceSha=$null; $hostError='HOST_PRE_GRANT_EXCEPTION'
try {
    $prefix=Read-B4Manifest $manifestStream
    $prior=Assert-B4Ledger $prefix $ExpectedB4CardSha256 $card $B4AttemptOrdinal
    if ($prefix.Length -eq 0) { $prefix=Add-B4ManifestRecord $manifestStream $prefix ([pscustomobject]@{event='HEADER';card_sha256=$ExpectedB4CardSha256;budget_id=$card.budget_id;max_attempts=$card.max_attempts;max_windows=$card.max_windows}) }
    $prefix=Add-B4ManifestRecord $manifestStream $prefix ([pscustomobject]@{event='CONSUME';ordinal=$B4AttemptOrdinal;epoch=$PhysicalRecoveryEpoch;
        session_id=$card.session_id;physical_cold_confirmed=$true;cold_seconds=$PhysicalColdSeconds;queue_depth=$B4QueueDepth;urb_bytes=$B4UrbBytes;
        previous_offline_validation_sha256=$(if($prior){$prior.sha256}else{$null});utc=[DateTime]::UtcNow.ToString('o')})
    $consumed=$true
    $null=New-Item -ItemType Directory -Path $attemptPath
    $null=New-Item -ItemType Directory -Path $rawAttemptPath -Force
    Assert-B4Identity; Assert-B4Apk
    $power=Invoke-B4Adb @('shell','dumpsys','power')
    if ($power -cnotmatch '(?m)^\s*mWakefulness=Awake\r?$') { throw 'B4_SCREEN_NOT_AWAKE_USER_ACTION_REQUIRED' }
    $preflightToken=[guid]::NewGuid().ToString()
    $script:adbStage='B4_PERMISSION_ONLY'
    $null=Invoke-B4Adb @('shell','am','start','--activity-clear-top','--activity-single-top','-n',"$package/.Tbs5530PermissionPreflightActivity",'--es','preflight_token',$preflightToken)
    $ready=Wait-B4Permission $preflightToken
    Write-B4NewJson (Join-Path $attemptPath 'permission-ready.private.json') $ready
    Assert-B4LiveDay $card
    $launchToken=[guid]::NewGuid().ToString()
    $launch=@('shell','am','start','--activity-clear-top','--activity-single-top','-n',"$package/.Tbs5530AutonomousDiagnosticActivity",
        '--es','read_api','NATIVE_USBFS_STREAM','--es','frontend_experiment','B2_RELOAD_AFTER_SET_CONFIG','--es','launch_token',$launchToken,
        '--ez','control_plane_only','false','--ez','control_mirror','true','--ei','b3_queue_depth',[string]$B4QueueDepth,'--ei','b3_urb_bytes',[string]$B4UrbBytes,
        '--ei','b3_max_bytes',[string]$B4MaxBytes,'--ei','b3_duration_ms',[string]$B4DurationMillis,'--ez','b4_enabled','true',
        '--ei','b4_duration_ms',[string]$B4DurationMillis,'--ei','b4_max_bytes',[string]$B4MaxBytes,'--ez','b4_tuner_rssi',$B4TunerRssi.ToString().ToLowerInvariant())
    $script:adbStage='B4_LAUNCH'; $null=Invoke-B4Adb $launch
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while ($watch.ElapsedMilliseconds -lt 10000) {
        $cmd='if [ -f files/autonomous-v1-challenge.json ]; then cat files/autonomous-v1-challenge.json; fi'
        $text=Invoke-B4Adb @('shell','run-as',$package,'sh','-c',("'"+$cmd+"'"))
        if ($text) { $candidate=$text|ConvertFrom-Json; if ($candidate.launch_token -ceq $launchToken) { $challenge=$candidate; break } }
        Start-Sleep -Milliseconds 100
    }
    Assert-B4Challenge $challenge $launchToken (Get-B4Elapsed)
    Assert-B4Foreground; Assert-B4Identity
    Write-B4NewJson (Join-Path $attemptPath 'challenge.private.json') $challenge
    $directory='files/autonomous-v1-'+$challenge.session
    $encoded=[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $FirmwarePath)))
    $cmd='base64 -d > '+$directory+'/firmware.bin'
    $null=Invoke-B4Adb @('shell','run-as',$package,'sh','-c',("'"+$cmd+"'")) $encoded
    if ((Get-TBS5530Sha256Token (Invoke-B4Adb @('shell','run-as',$package,'sha256sum',($directory+'/firmware.bin')))) -cne $firmwareSha) { throw 'B4_STAGED_FIRMWARE_MISMATCH' }
    Assert-B4LiveDay $card
    $expiry=[long]$challenge.created_elapsed_ms+300000
    $grant=@{}; $challenge.PSObject.Properties|ForEach-Object {$grant[$_.Name]=$_.Value}
    $grant.profile='EXPERIMENTAL_DIAGNOSTIC'; $grant.bounded_user_authorization=$true; $grant.rf_in_confirmed=$true
    $grant.host_script_sha256=$ExpectedB4HelperSha256; $grant.expires_elapsed_ms=$expiry
    $grant.physical_recovery_epoch=$PhysicalRecoveryEpoch; $grant.physical_recovery_confirmed=$true
    $grant.b3_card_sha256=$ExpectedB4CardSha256; $grant.b3_session_id=$card.session_id; $grant.b3_attempt_ordinal=$B4AttemptOrdinal
    $grant.b3_max_attempts=$card.max_attempts; $grant.b3_physical_cold_seconds=$PhysicalColdSeconds
    $grant.b4_card_sha256=$ExpectedB4CardSha256; $grant.b4_budget_id=$card.budget_id; $grant.b4_session_id=$card.session_id
    $grant.b4_attempt_ordinal=$B4AttemptOrdinal; $grant.b4_max_attempts=$card.max_attempts; $grant.b4_physical_cold_seconds=$PhysicalColdSeconds
    Write-B4NewJson (Join-Path $attemptPath 'grant.private.json') $grant
    $script:adbStage='B4_GRANT'; $granted=$true
    $initial=Exchange-B4Heartbeat -Grant $grant
    if ($initial.expired) { throw 'B4_EXPIRED_BEFORE_GRANT' }
    $script:adbStage='B4_RUNNING'
    while ($watch.ElapsedMilliseconds -lt 300000) {
        Assert-B4LiveDay $card
        $beat=Exchange-B4Heartbeat
        if ($null -ne $beat.report -and $null -ne $beat.report.PSObject.Properties['terminal'] -and $beat.report.terminal -ceq $true) { $terminalReport=$beat.report; break }
        if ($beat.expired) { break }
        Assert-B4PermissionSnapshot $beat.permission $challenge.session $beat.elapsed_ms
        Start-Sleep -Milliseconds 100
    }
    if ($null -eq $terminalReport) { throw 'B4_TERMINAL_DEADLINE_NO_FORCE_CLOSE' }
    if ($terminalReport.launch_token -cne $launchToken -or $terminalReport.frontend_experiment -cne 'B2_RELOAD_AFTER_SET_CONFIG' -or
        $terminalReport.ts_variant -cne 'NATIVE_USBFS_STREAM' -or $terminalReport.control_plane_only -isnot [bool] -or $terminalReport.control_plane_only) { throw 'B4_REPORT_SCOPE_MISMATCH' }
    foreach ($pin in @{b4_enabled=$true;b4_duration_ms=$B4DurationMillis;b4_max_bytes=$B4MaxBytes;b4_tuner_rssi=$B4TunerRssi}.GetEnumerator()) {
        if ($null -eq $terminalReport.PSObject.Properties[$pin.Key] -or $terminalReport.($pin.Key) -cne $pin.Value -or
            ($pin.Value -is [bool] -and $terminalReport.($pin.Key) -isnot [bool]) -or
            ((Test-B4Integer $pin.Value) -and -not (Test-B4Integer $terminalReport.($pin.Key)))) { throw 'B4_REPORT_LIMITS_MISMATCH' }
    }
    if (-not (Test-B4Drained $terminalReport)) { throw 'B4_DRAIN_UNCERTAIN_NO_FORCE_CLOSE' }
    Write-B4NewJson (Join-Path $attemptPath 'terminal-report.json') $terminalReport
    $script:adbStage='B4_EXPORT_AFTER_DRAIN'
    $exports=Export-B4Evidence $challenge.session $terminalReport $attemptPath
    $exportManifestSha=(Get-FileHash -LiteralPath (Join-Path $attemptPath 'export-manifest.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    $raw=@($exports|Where-Object {$_.path -clike '*/received.ts' -and $_.present}); if ($raw.Count -eq 1) { $captureRawSha=$raw[0].sha256 }
    $classification=Get-B4CaptureClassification $terminalReport $exports
    Write-B4NewJson (Join-Path $attemptPath 'host-classification.json') $classification
    $classification|ConvertTo-Json -Compress
    $failure=$terminalReport.result
} catch {
    if (-not $granted -and $_.Exception.Message -cmatch '^B4_[A-Z0-9_]+$') { $hostError=$_.Exception.Message }
    throw
} finally {
    if ($granted) { try { $null=Exchange-B4Heartbeat -Stop } catch { Write-Warning 'B4: STOP chưa xác nhận; watchdog heartbeat thật 2 giây, không reconnect hoặc ép đóng syscall.' } }
    try {
        if ($consumed) {
            $closed=Test-B4SessionClosed $terminalReport; $drained=Test-B4Drained $terminalReport
            if ($failure -ceq 'HOST_ABORTED_NO_RETRY') { $closed=$true }
            if (-not $granted) {
                $e=Write-B4PreGrantEvidence $attemptPath $ExpectedB4CardSha256 $card.session_id $B4AttemptOrdinal $PhysicalRecoveryEpoch $hostError $script:adbStage
                $preGrantEvidenceSha=$e.evidence_sha256; $exportManifestSha=$e.export_sha256; $failure='PRE_GRANT_HOST_REFUSAL'; $closed=$false; $drained=$false
            }
            $receipt=[pscustomobject]@{event='RECEIPT';ordinal=$B4AttemptOrdinal;result=$failure;drained=$drained;session_closed=$closed;exit_code=$exitCode;
                single_run_pass=$false;capture_complete=($null -ne $classification -and $classification.capture_complete);raw_sha256=$captureRawSha;
                export_manifest_sha256=$exportManifestSha;queue_depth=$B4QueueDepth;urb_bytes=$B4UrbBytes;grant_may_have_been_published=[bool]$granted;
                pre_grant_evidence_sha256=$preGrantEvidenceSha;pre_io_refusal=(-not $granted -or ($null -ne $terminalReport -and $null -ne $terminalReport.PSObject.Properties['pre_native_refusal'] -and $terminalReport.pre_native_refusal -is [bool] -and $terminalReport.pre_native_refusal));
                classification=$(if (-not $granted) {'PRE_GRANT_HOST_REFUSAL'} else {'PENDING_ANALYSIS'});utc=[DateTime]::UtcNow.ToString('o')}
            $prefix=Add-B4ManifestRecord $manifestStream $prefix $receipt
        }
    } finally { $manifestStream.Dispose() }
}
exit $exitCode
