# SPDX-License-Identifier: GPL-2.0-or-later
<#
.SYNOPSIS
Một lượt B5 OnePlus permission-first theo card đã khóa; không tự thử lại.
.DESCRIPTION
Ngân sách B5 riêng tối đa sáu Android/hai Windows, TTL 300s, permission 30s.
Card TBS5530-B5-CARD/v1 cần approval OK B5, serial/model/manufacturer/API/boot,
source manifest và helper/APK/firmware/plan hashes như B4. Không reset/rearm B3/B4.
Mỗi lượt cần cold >=5s do người dùng xác nhận và epoch riêng. OFF1/ON1/OFF2/ON2
10/30/10/10s; native 16x16384, ON1 stale prefix 2048; RSSI chỉ ba mẫu mỗi OFF.
Frontend launch chỉ B5_K25_MANUAL_PLP0/B5_K36_MANUAL_PLP0. AUTO chỉ một lần sau
K25 manual không demod lock, trong cùng invocation; không AUTO như lượt riêng.
Card phải chứa frontend_experiment từng attempt, layout, monitor period/jitter/RSSI,
notes {separation, cable, accessories} không chứa định danh cá nhân.
-75/-71/detach/uncertainty hoặc thiếu typed drain đóng toàn bộ ngân sách.
Helper chỉ ghi PENDING_ANALYSIS, không tạo TS_SMOKE_PASS hoặc kết luận nguyên nhân.
B5 Windows dùng ngân sách Windows riêng do parent quản lý; helper này không capture.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [string]$ExpectedModel='GM1900',
    [int]$ExpectedApi=30,
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
    [switch]$ApproveB5SessionBudget,
    [string]$Approval,
    [string]$B5CardPath,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedB5CardSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedB5HelperSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedB3HelperSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedHelperSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedAppApkSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedFrontendGoldenSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedReloadPlanSha256,
    [string]$B5SessionDirectory,
    [ValidateRange(1,6)][int]$B5AttemptOrdinal=1,
    [ValidateSet(16)][int]$B5QueueDepth=16,
    [ValidateSet(16384)][int]$B5UrbBytes=16384,
    [ValidateSet(30000)][int]$B5DurationMillis=30000,
    [ValidateSet(268435456)][int]$B5MaxBytes=268435456,
    [ValidateSet('B5_K25_MANUAL_PLP0','B5_K36_MANUAL_PLP0')][string]$FrontendExperiment='B5_K25_MANUAL_PLP0',
    [string]$OutputDirectory,
    [string]$AdbPath='D:\Android\Sdk\platform-tools\adb.exe'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$package='info.martinmarinov.dvbdriver'
$firmwareSha='b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc'
$script:adbStage='B5_PREFLIGHT'
$layout='OFF1:10000,ON1:30000,OFF2:10000,ON2:10000'

function Import-B5SharedGuards([string]$Path,[string]$ExpectedHash) {
    if ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ExpectedHash) { throw 'B5_B3_HELPER_HASH_MISMATCH' }
    $tokens=$null; $errors=$null
    $ast=[Management.Automation.Language.Parser]::ParseFile($Path,[ref]$tokens,[ref]$errors)
    if ($errors.Count -ne 0) { throw 'B5_SHARED_GUARDS_PARSE_FAILED' }
    $shared=@('Get-TBS5530AdbFailure','Test-TBS5530DrainedTerminal','ConvertTo-TBS5530WindowsArgument','Get-TBS5530Sha256Token',
        'Test-TBS5530LaunchChallenge','New-TBS5530HeartbeatCommand','Read-TBS5530HeartbeatReply','Get-B3Hash','Test-B3Integer','Assert-B3Guid',
        'Read-B3Manifest','Add-B3ManifestRecord','Test-B3PreGrantRefusal','Invoke-B3Adb','Get-B3Elapsed','Assert-B3Identity','Assert-B3Foreground',
        'Assert-B3Apk','Test-B3Drained','Test-B3SessionClosed','Assert-B3PermissionSnapshot','Write-B3NewJson','Write-B3PreGrantEvidence','Exchange-B3Heartbeat')
    foreach ($function in @($ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst]},$true))) {
        if ($function.Name -cnotin $shared) { continue }
        # Import declarations only: never execute B3's main or expand B3 live limits.
        $definition=$function.Extent.Text.Replace('B3','B5')
        if ($function.Name -ceq 'Invoke-B3Adb') {
            $definition=$definition.Replace("[bool]`$AfterDrain = `$false)","[bool]`$AfterDrain = `$false, [object]`$ExpectedExportBytes = -1)")
            $definition=$definition.Replace('$timeout = if ($AfterDrain) { 10000 } else { 1500 }', '$timeout = Get-B5AdbTimeout $DeviceArguments $BinaryDestination $AfterDrain $ExpectedExportBytes')
        }

        if ($function.Name -ceq 'Test-B3Drained') { $definition=$definition.Replace('function Test-B5Drained','function Test-B5BaseDrained') }
        if ($function.Name -ceq 'Test-B3SessionClosed') { $definition=$definition.Replace('function Test-B5SessionClosed','function Test-B5BaseSessionClosed') }
        $definition=$definition -replace '^function ([A-Za-z0-9-]+)', 'function script:$1'
        Invoke-Expression $definition
    }
}
function Get-B5AdbTimeout([string[]]$Arguments,[string]$BinaryDestination,[bool]$AfterDrain,[object]$ExpectedExportBytes=-1) {
    if ([string]::IsNullOrEmpty($BinaryDestination)) { return $(if($AfterDrain){10000}else{1500}) }
    if (-not $AfterDrain -or $Arguments.Count -ne 6 -or $Arguments[0] -cne 'exec-out' -or
        $Arguments[1] -cne 'run-as' -or $Arguments[2] -cne 'info.martinmarinov.dvbdriver' -or
        $Arguments[3] -cne 'sh' -or $Arguments[4] -cne '-c' -or
        $Arguments[5] -cnotmatch '^head -c (?<limit>[0-9]{1,10}) (?<path>(?:files|no_backup)/[A-Za-z0-9./-]+)$' -or
        -not (Test-B5Integer $ExpectedExportBytes) -or $ExpectedExportBytes -lt 0) { throw 'B5_BINARY_EXPORT_DEADLINE_SCOPE_INVALID' }
    $path=$Matches.path;$bound=[long]$Matches.limit;$maximum=Get-B5ExportLimit $path
    if ($ExpectedExportBytes -gt $maximum -or $bound -ne $maximum+1) { throw 'B5_BINARY_EXPORT_SIZE_PROOF_INVALID' }
    return [int][Math]::Min(180000,[Math]::Max(90000,30000+2000*[Math]::Ceiling($ExpectedExportBytes/1048576.0)))
}
function Assert-B5PrivatePath([string]$Path,[string]$Root) {
    $full=[IO.Path]::GetFullPath($Path)
    if (-not $full.StartsWith($Root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -or $full -cne $Path) { throw 'B5_PRIVATE_CANONICAL_PATH_REQUIRED' }
    return $full
}
function Assert-B5LiveDay([object]$Card,[DateTimeOffset]$Now=[DateTimeOffset]::UtcNow) {
    if ($Card.live_date -isnot [string] -or $Card.live_date -cnotmatch '^\d{4}-\d{2}-\d{2}$' -or
        $Card.time_zone_id -isnot [string] -or -not (Test-B5Integer $Card.utc_offset_minutes)) { throw 'B5_LIVE_DAY_INVALID' }
    try { $zone=[TimeZoneInfo]::FindSystemTimeZoneById($Card.time_zone_id); $local=[TimeZoneInfo]::ConvertTime($Now,$zone) }
    catch { throw 'B5_TIME_ZONE_INVALID' }
    if ($local.ToString('yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture) -cne $Card.live_date -or
        $local.Offset.TotalMinutes -ne $Card.utc_offset_minutes) { throw 'B5_APPROVED_LIVE_DAY_EXPIRED' }
}
function Assert-B5Source([object]$Card,[string]$Repository) {
    $head=& git -C $Repository rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $head -cne $Card.source_git_sha) { throw 'B5_SOURCE_HEAD_MISMATCH' }
    $manifestPath=Assert-B5PrivatePath $Card.source_manifest_path 'D:\TSDuck\reports'
    if ((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Card.source_manifest_sha256) { throw 'B5_SOURCE_MANIFEST_HASH_MISMATCH' }
    $manifest=[IO.File]::ReadAllText($manifestPath)|ConvertFrom-Json
    if ($manifest.source_git_sha -cne $Card.source_git_sha -or @($manifest.files).Count -lt 1 -or @($manifest.files).Count -gt 512) { throw 'B5_SOURCE_MANIFEST_INVALID' }
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($file in $manifest.files) {
        if ($file.path -isnot [string] -or [IO.Path]::IsPathRooted($file.path) -or $file.path -match '(^|[\\/])\.\.([\\/]|$)' -or
            $file.sha256 -cnotmatch '^[a-f0-9]{64}$' -or -not $seen.Add($file.path)) { throw 'B5_SOURCE_ENTRY_INVALID' }
        $path=[IO.Path]::GetFullPath((Join-Path $Repository $file.path))
        if (-not $path.StartsWith($Repository.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -or
            (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $file.sha256) { throw 'B5_SOURCE_FILE_CHANGED' }
    }
}
function Assert-B5ForegroundSnapshot([string]$Activities) {
    $lines=@($Activities -split "`n"|Where-Object{$_ -cmatch '^\s*(?:mResumedActivity|ResumedActivity):\s*'})
    if($lines.Count -lt 1 -or $lines.Count -gt 2){throw 'B5_FOREGROUND_UNVERIFIED'}
    $records=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $labels=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($line in $lines){
        if($line -cnotmatch '^\s*(?<label>mResumedActivity|ResumedActivity):\s+(?<record>ActivityRecord\{\S+\s+u[0-9]+\s+info\.martinmarinov\.dvbdriver/\.Tbs5530AutonomousDiagnosticActivity(?:\s+[^\r\n{}]*)?\})\s*\r?$'){
            throw 'B5_FOREGROUND_UNVERIFIED'
        }
        if(-not $labels.Add($Matches.label)){throw 'B5_FOREGROUND_UNVERIFIED'}
        $null=$records.Add($Matches.record)
    }
    if($records.Count -ne 1){throw 'B5_FOREGROUND_UNVERIFIED'}
}

function Set-B5Overrides {
    function script:Assert-B5Foreground {
        $activities=Invoke-B5Adb @('shell','dumpsys','activity','activities')
        Assert-B5ForegroundSnapshot $activities
    }

    function script:Assert-B5Identity {
        if ((Invoke-B5Adb @('get-state')) -cne 'device' -or
            (Invoke-B5Adb @('shell','getprop','ro.product.model')) -cne 'GM1900' -or
            (Invoke-B5Adb @('shell','getprop','ro.product.manufacturer')) -cne 'OnePlus' -or
            (Invoke-B5Adb @('shell','getprop','ro.build.version.sdk')) -cne '30' -or
            (Invoke-B5Adb @('shell','settings','get','global','boot_count')) -cne [string]$ExpectedBootCount) { throw 'B5_IDENTITY_MISMATCH' }
    }
    function script:Test-B5Drained([object]$Report) {
        if (-not (Test-B5BaseDrained $Report)) { return $false }
        if ($null -eq $Report.PSObject.Properties['b5_phases']) { return $false }
        foreach ($phase in @($Report.b5_phases)) {
            if ($null -eq $phase.PSObject.Properties['drained'] -or $phase.drained -isnot [bool] -or -not $phase.drained -or
                $null -eq $phase.PSObject.Properties['quarantined'] -or $phase.quarantined -isnot [bool] -or $phase.quarantined) { return $false }
        }
        return $true
    }
    function script:Test-B5SessionClosed([object]$Report) {
        if (Test-B5BaseSessionClosed $Report) { return $true }
        $items=@($Report)
        foreach ($key in @('native_stream','native_urb','b5_phases')) {
            if ($null -ne $Report.PSObject.Properties[$key]) { $items+=@($Report.$key) }
        }
        foreach ($item in $items) {
            if ($null -eq $item) { continue }
            foreach ($key in @('first_status','first_errno','last_errno','urb_status','syscall_errno')) {
                if ($null -ne $item.PSObject.Properties[$key] -and $item.$key -in @(-75,75,-71,71)) { return $true }
            }
            if ($null -ne $item.PSObject.Properties['stop_reason'] -and $item.stop_reason -match '(^|[^0-9])(71|75)([^0-9]|$)|DETACH|UNCERTAIN') { return $true }
        }
        return $false
    }
    function script:Assert-B5PhaseReport([object]$Report,[switch]$Complete) {
        if ($null -eq $Report.PSObject.Properties['b5_phases'] -or @($Report.b5_phases).Count -gt 4 -or
            ($Complete -and @($Report.b5_phases).Count -ne 4)) { throw 'B5_PHASE_REPORT_MISSING' }
        $names=@('OFF1','ON1','OFF2','ON2');$durations=@(10000,30000,10000,10000);$prior=-1L;$index=0
        foreach ($phase in @($Report.b5_phases)) {
            foreach ($key in @('start_ns','end_ns','bytes','stale_prefix_bytes')) {
                if ($null -eq $phase.PSObject.Properties[$key] -or -not (Test-B5Integer $phase.$key)) { throw 'B5_PHASE_REPORT_TYPE_INVALID' }
            }
            $on=$names[$index].StartsWith('ON',[StringComparison]::Ordinal)
            if ($phase.phase -cne $names[$index] -or $phase.clock -cne 'CLOCK_MONOTONIC' -or $phase.start_ns -lt $prior -or
                $phase.end_ns -lt $phase.start_ns -or $phase.bytes -lt 0 -or $phase.bytes -gt 268435456 -or
                $phase.stale_prefix_bytes -ne $(if($index -eq 1){2048}else{0}) -or
                ($on -and $phase.raw_file -cne ('received-'+$phase.phase+'.ts')) -or
                (-not $on -and ($phase.bytes -ne 0 -or $null -ne $phase.raw_file)) -or
                ($Complete -and ($phase.end_ns-$phase.start_ns) -lt $durations[$index]*1000000L)) { throw 'B5_PHASE_REPORT_SCOPE_INVALID' }
            if ($on) {
                $suffix='-'+$phase.phase
                if ($phase.audit_run_id -isnot [string] -or -not $phase.audit_run_id.EndsWith($suffix,[StringComparison]::Ordinal)) { throw 'B5_PHASE_AUDIT_ID_INVALID' }
                Assert-B5Guid $phase.audit_run_id.Substring(0,$phase.audit_run_id.Length-$suffix.Length)
            }
            $prior=$phase.end_ns;$index++
        }
    }
    function script:Get-B5AllowedFrontends([object]$Attempt) {
        $variants=if ($null -ne $Attempt.PSObject.Properties['allowed_frontends']) { @($Attempt.allowed_frontends) } else { @($Attempt) }
        if ($variants.Count -lt 1 -or $variants.Count -gt 2) { throw 'B5_FRONTEND_VARIANTS_INVALID' }
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($variant in $variants) {
            if ($variant.frontend_experiment -cnotin @('B5_K25_MANUAL_PLP0','B5_K36_MANUAL_PLP0') -or
                -not $seen.Add($variant.frontend_experiment) -or $variant.allow_k25_auto -isnot [bool] -or
                $variant.allow_k25_auto -ne ($variant.frontend_experiment -ceq 'B5_K25_MANUAL_PLP0')) { throw 'B5_FRONTEND_ALLOWLIST_REQUIRED' }
            foreach ($key in @('golden_sha256','reload_plan_sha256')) {
                if ($variant.$key -isnot [string] -or $variant.$key -cnotmatch '^[a-f0-9]{64}$') { throw 'B5_ATTEMPT_PLAN_HASH_REQUIRED' }
            }
            foreach ($key in @('frequency_hz','bandwidth_hz')) {
                if ($null -ne $variant.PSObject.Properties[$key]) {
                    $expected=if($key -ceq 'bandwidth_hz'){8000000}elseif($variant.frontend_experiment -ceq 'B5_K25_MANUAL_PLP0'){506000000}else{594000000}
                    if (-not (Test-B5Integer $variant.$key) -or $variant.$key -ne $expected) { throw 'B5_FRONTEND_FREQUENCY_MISMATCH' }
                }
            }
        }
        return $variants
    }
    function script:Select-B5Frontend([object]$Attempt,[string]$Experiment,[string]$Golden,[string]$Reload) {
        $selected=@(Get-B5AllowedFrontends $Attempt|Where-Object {$_.frontend_experiment -ceq $Experiment -and $_.golden_sha256 -ceq $Golden -and $_.reload_plan_sha256 -ceq $Reload})
        if ($selected.Count -ne 1) { throw 'B5_FRONTEND_CARD_BINDING_MISMATCH' }
        return $selected[0]
    }
    function script:Get-B5AllowedReceiveConfigs([object]$Attempt) {
        $configs=if ($null -ne $Attempt.PSObject.Properties['allowed_receive_configs']) { @($Attempt.allowed_receive_configs) } else { @($Attempt) }
        if ($configs.Count -lt 1 -or $configs.Count -gt 1) { throw 'B5_CARD_RECEIVE_CONFIG_INVALID' }
        $keys=[Collections.Generic.HashSet[int]]::new()
        foreach ($config in $configs) {
            if (-not (Test-B5Integer $config.queue_depth) -or $config.queue_depth -ne 16 -or
                -not (Test-B5Integer $config.urb_bytes) -or $config.urb_bytes -ne 16384 -or -not $keys.Add($config.queue_depth)) { throw 'B5_CARD_RECEIVE_CONFIG_INVALID' }
        }
        return $configs
    }
    function script:Assert-B5Card([object]$Card,[hashtable]$Pins,[int]$Ordinal,[string]$Epoch,[int]$Depth,[int]$Chunk) {
        Assert-B5Guid $Card.session_id; Assert-B5Guid $Card.budget_id
        foreach ($key in $Pins.Keys) {
            if ($null -eq $Card.PSObject.Properties[$key] -or $null -eq $Card.$key -or $Card.$key -cne $Pins[$key] -or
                ((Test-B5Integer $Pins[$key]) -and -not (Test-B5Integer $Card.$key)) -or
                ($Pins[$key] -is [bool] -and $Card.$key -isnot [bool]) -or ($Pins[$key] -is [string] -and $Card.$key -isnot [string])) { throw ('B5_CARD_PIN_MISMATCH:'+$key) }
        }
        if (-not (Test-B5Integer $Card.max_attempts) -or $Card.max_attempts -lt 1 -or $Card.max_attempts -gt 6 -or
            -not (Test-B5Integer $Card.max_android) -or $Card.max_android -lt 1 -or $Card.max_android -gt 6 -or $Card.max_attempts -gt $Card.max_android -or
            -not (Test-B5Integer $Card.max_windows) -or $Card.max_windows -lt 0 -or $Card.max_windows -gt 2 -or
            @($Card.attempts).Count -ne $Card.max_attempts -or $Ordinal -lt 1 -or $Ordinal -gt $Card.max_attempts) { throw 'B5_CARD_BUDGET_INVALID' }
        if ($Card.schema -cne 'TBS5530-B5-CARD/v1' -or $Card.approval -cne 'OK B5' -or
            -not (Test-B5Integer $Card.duration_ms) -or $Card.duration_ms -ne 30000 -or
            -not (Test-B5Integer $Card.max_bytes) -or $Card.max_bytes -ne 268435456 -or
            $Card.antenna_dc -isnot [bool] -or $Card.antenna_dc -or $Card.rf_input -cne 'RF_IN' -or
            $Card.antenna -cnotin @('PASSIVE','EXTERNALLY_POWERED_NO_TUNER_DC')) { throw 'B5_CARD_SCOPE_INVALID' }
        if ($Card.budget_directory -cne $Card.session_directory -or
            [IO.Path]::GetFileName($Card.budget_directory) -cne ('b5-budget-'+$Card.budget_id)) { throw 'B5_STABLE_BUDGET_PATH_REQUIRED' }
        $null=Assert-B5PrivatePath $Card.budget_directory 'D:\TSDuck\logs'
        $null=Assert-B5PrivatePath $Card.raw_directory 'D:\TSDuck'
        $null=Assert-B5PrivatePath $Card.report_directory 'D:\TSDuck\reports'
        Assert-B5LiveDay $Card
        $epochs=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal); $i=0
        foreach ($attempt in $Card.attempts) {
            $i++; Assert-B5Guid $attempt.physical_recovery_epoch
            if (-not (Test-B5Integer $attempt.ordinal) -or $attempt.ordinal -ne $i -or -not $epochs.Add($attempt.physical_recovery_epoch)) { throw 'B5_CARD_ATTEMPT_INVALID' }
            $null=Get-B5AllowedReceiveConfigs $attempt
            $null=Get-B5AllowedFrontends $attempt
            foreach ($note in @('separation','cable','accessories')) {
                if ($attempt.notes.$note -isnot [string] -or [string]::IsNullOrWhiteSpace($attempt.notes.$note) -or $attempt.notes.$note.Length -gt 240) { throw 'B5_SETUP_NOTES_REQUIRED' }
            }
        }
        $selected=$Card.attempts[$Ordinal-1]
        if ($selected.physical_recovery_epoch -cne $Epoch -or @((Get-B5AllowedReceiveConfigs $selected)|Where-Object {$_.queue_depth -eq $Depth -and $_.urb_bytes -eq $Chunk}).Count -ne 1) { throw 'B5_CARD_ATTEMPT_BINDING_MISMATCH' }
    }
    function script:Assert-B5Challenge([object]$Value,[string]$Launch,[long]$Now) {
        if (-not (Test-TBS5530LaunchChallenge $Value $Launch $false $Now)) { throw 'B5_CHALLENGE_STALE_OR_FOREIGN' }
        Assert-B5Guid $Value.session
        $pins=@{model=$ExpectedModel;api=$ExpectedApi;boot_count=$ExpectedBootCount;host_profile='oneplus-gm1900-api30';vid=29516;pid=21808
            firmware_sha256=$firmwareSha;firmware_protocol='TBS_SOURCE_DUAL_RESET';ts_variant='NATIVE_USBFS_STREAM';frontend_experiment=$FrontendExperiment
            golden_sha256=$ExpectedFrontendGoldenSha256;app_apk_sha256=$ExpectedAppApkSha256;control_mirror=$true;explicit_set_interface_alt0=$false;sony_ts_output_readback=$false
            retry=0;max_upload_per_attachment=1;max_reload_per_physical_epoch=1;max_session_ms=300000;heartbeat_ms=2000
            fx2_reload_option='B2_RELOAD_AFTER_SET_CONFIG';fx2_reload_plan_sha256=$ExpectedReloadPlanSha256;bootstrap_proof='SAME_SESSION_ORIGINAL132_APP_BOUNDARIES_DRAIN_REQUIRED'
            b3_queue_depth=$B5QueueDepth;b3_urb_bytes=$B5UrbBytes;b3_max_bytes=$B5MaxBytes;b3_duration_ms=$B5DurationMillis
            b5_enabled=$true;b5_rssi_sequence='WINDOWS_88_00_87_C0';b5_allow_k25_auto=($FrontendExperiment -ceq 'B5_K25_MANUAL_PLP0');b5_layout=$layout;b5_stale_prefix_bytes=2048;b5_monitor_period_ms=100;b5_monitor_jitter_ms=20;b5_rssi_samples_per_off=3}
        foreach ($key in $pins.Keys) {
            if ($null -eq $Value.PSObject.Properties[$key] -or $Value.$key -cne $pins[$key] -or
                ($pins[$key] -is [bool] -and $Value.$key -isnot [bool]) -or ((Test-B5Integer $pins[$key]) -and -not (Test-B5Integer $Value.$key))) { throw ('B5_CHALLENGE_PIN_MISMATCH:'+$key) }
        }
        foreach ($field in @('fx2_reload_store_sha256','fx2_reload_anchor_sha256')) {
            if ($card.$field -cnotmatch '^[a-f0-9]{64}$' -or $Value.$field -cne $card.$field) { throw 'B5_SESSION_RAM_INITIAL_WITNESS_MISMATCH' }
        }
        if ($null -eq $Value.PSObject.Properties['attachment_history_sha256'] -or $null -ne $Value.attachment_history_sha256 -or
            $Value.source_git_sha -cne $card.source_git_sha) { throw 'B5_SESSION_RAM_INITIAL_WITNESS_MISMATCH' }
    }
    function script:Get-B5ExportPaths([string]$Session) {
        Assert-B5Guid $Session
        $base='files/autonomous-v1-'+$Session+'/'
        $names=@('report.json','received-ON1.ts','received-ON2.ts','handoff-events.jsonl','control-ep0.jsonl','reload-a0-native.jsonl','reload-native-boundaries.json',
            'b3-reload-journal.txt','b3-reload-anchor.txt','ledger.jsonl','permission.json','initial-descriptors.bin','receive-audit.jsonl',
            'receive-audit.jsonl.started','receive-audit.jsonl.complete')
        return @($names|ForEach-Object {$base+$_})+@(('files/phase31-native-stream-'+$Session+'-ON1.jsonl'),('files/phase31-native-stream-'+$Session+'-ON2.jsonl'),('files/phase31-native-stream-'+$Session+'-ON1.ts'),('files/phase31-native-stream-'+$Session+'-ON2.ts'),('files/phase31-signal-monitor-'+$Session+'-ON1.jsonl'),('files/phase31-signal-monitor-'+$Session+'-ON2.jsonl'),('files/phase31-signal-monitor-'+$Session+'-OFF1.jsonl'),('files/phase31-signal-monitor-'+$Session+'-OFF2.jsonl'),('files/phase31-signal-rssi-'+$Session+'-OFF1.jsonl'),('files/phase31-signal-rssi-'+$Session+'-OFF2.jsonl'),
            'files/autonomous-v1-oneplus-gm1900-api30-attachments.txt','no_backup/fx2-reload-v1.txt','no_backup/fx2-reload-v1.anchor',
            'no_backup/fx2-reload-v1.initialized','no_backup/fx2-reload-v1-init.consumed.json')
    }
    function script:Get-B5ExportLimit([string]$Path) {
        if ($Path.EndsWith('.ts',[StringComparison]::Ordinal)) { return 268435456 }
        if ($Path.EndsWith('.jsonl',[StringComparison]::Ordinal)) { return 67108864 }
        return 1048576
    }
    function script:Assert-B5ExportCall([string[]]$Arguments,[object]$Report,[string]$Session,[string]$BinaryDestination) {
        if (-not (Test-B5Drained $Report)) { throw 'B5_EXPORT_REQUIRES_TYPED_DRAIN' }
        foreach ($remote in @(Get-B5ExportPaths $Session)) {
            $exists='if [ -f '+$remote+' ]; then printf PRESENT; fi'
            $bounded='head -c '+((Get-B5ExportLimit $remote)+1)+' '+$remote
            foreach ($candidate in @(@('shell','run-as',$package,'sh','-c',("'"+$exists+"'")),@('shell','run-as',$package,'sha256sum',$remote),
                @('shell','run-as',$package,'wc','-c',$remote),@('exec-out','run-as',$package,'sh','-c',$bounded))) {
                if (($Arguments -join "`n") -ceq ($candidate -join "`n")) {
                    if (($Arguments[0] -ceq 'exec-out') -ne (-not [string]::IsNullOrEmpty($BinaryDestination))) { throw 'B5_EXPORT_BINARY_SINK_REQUIRED' }
                    return
                }
            }
        }
        throw 'B5_EXPORT_OUTSIDE_EXACT_READ_ONLY_ALLOWLIST'
    }
    function script:Export-B5Evidence([string]$Session,[object]$Report,[string]$Destination) {
        if (-not (Test-B5Drained $Report)) { throw 'B5_EXPORT_REQUIRES_TYPED_DRAIN' }
        $manifest=@(); $index=0
        foreach ($remote in @(Get-B5ExportPaths $Session)) {
            $index++; $cmd='if [ -f '+$remote+' ]; then printf PRESENT; fi'
            $present=Invoke-B5Adb @('shell','run-as',$package,'sh','-c',("'"+$cmd+"'")) -AfterDrain $true
            if ($present -ceq '') {
                $manifest+=[pscustomobject]@{path=$remote;present=$false}
                $hasStream=$null -ne $Report.PSObject.Properties['b5_phases'] -and @($Report.b5_phases|Where-Object {$_.phase -cin @('ON1','ON2')}).Count -gt 0
                if ($remote.EndsWith('/report.json') -or ($hasStream -and ($remote -match 'received-ON[12]\.ts$|phase31-native-stream-.*\.(ts|jsonl)$|phase31-signal-(monitor|rssi)-.*\.jsonl$|handoff-events\.jsonl$|control-ep0\.jsonl$|reload-a0-native\.jsonl$|reload-native-boundaries\.json$|b3-reload-(journal|anchor)\.txt$'))) { throw 'B5_DRAINED_EVIDENCE_FILE_MISSING' }
                continue
            }
            if ($present -cne 'PRESENT') { throw 'B5_EXPORT_PRESENCE_UNVERIFIED' }
            $size=Invoke-B5Adb @('shell','run-as',$package,'wc','-c',$remote) -AfterDrain $true
            if ($size -cnotmatch '^\s*([0-9]{1,12})(?:\s|$)') { throw 'B5_EXPORT_SIZE_UNVERIFIED' }
            $length=[long]$Matches[1]; $limit=Get-B5ExportLimit $remote
            if ($length -gt $limit) { throw 'B5_EXPORT_SIZE_LIMIT_EXCEEDED' }
            $folder=if ($remote.EndsWith('.ts')) { $rawAttemptPath } else { $Destination }
            $local=Join-Path $folder ($index.ToString('D2')+'-'+[IO.Path]::GetFileName($remote))
            $before=Get-TBS5530Sha256Token (Invoke-B5Adb @('shell','run-as',$package,'sha256sum',$remote) -AfterDrain $true)
            $cmd='head -c '+($limit+1)+' '+$remote
            # exec-out passes argv directly; shell-command quotes would become literal command bytes.
            Invoke-B5Adb @('exec-out','run-as',$package,'sh','-c',$cmd) -BinaryDestination $local -AfterDrain $true -ExpectedExportBytes $length
            $after=Get-TBS5530Sha256Token (Invoke-B5Adb @('shell','run-as',$package,'sha256sum',$remote) -AfterDrain $true)
            $hostHash=(Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash.ToLowerInvariant()
            if ((Get-Item -LiteralPath $local).Length -ne $length -or $hostHash -cne $before -or $hostHash -cne $after) { throw 'B5_EXPORT_SIZE_OR_HASH_MISMATCH' }
            $manifest+=[pscustomobject]@{path=$remote;present=$true;file=[IO.Path]::GetFileName($local);local_path=$local;bytes=$length;sha256=$hostHash}
        }
        Write-B5NewJson (Join-Path $Destination 'export-manifest.json') $manifest
        return ,$manifest
    }
}

function Read-B5Review([object]$Card,[string]$CardHash,[object]$Receipt,[int]$Ordinal) {
    $preGrant=Test-B5PreGrantRefusal $Receipt
    if ($Receipt.session_closed -or (-not $Receipt.drained -and -not $preGrant)) { throw 'B5_PREVIOUS_CAPTURE_NOT_REVIEWABLE' }
    $attempt=Join-Path $Card.budget_directory ('attempt-'+$Ordinal)
    $path=Join-Path $attempt 'offline_validation.json'
    $bytes=[IO.File]::ReadAllBytes($path)
    if ($bytes.Length -gt 65536) { throw 'B5_OFFLINE_VALIDATION_OVERSIZED' }
    $v=[Text.UTF8Encoding]::new($false,$true).GetString($bytes).TrimStart([char]0xFEFF)|ConvertFrom-Json
    if ($v.schema -cne 'TBS5530-B5-OFFLINE-VALIDATION/v1' -or $v.card_sha256 -cne $CardHash -or $v.budget_id -cne $Card.budget_id -or
        $v.session_id -cne $Card.session_id -or -not (Test-B5Integer $v.ordinal) -or $v.ordinal -ne $Ordinal -or
        $v.physical_recovery_epoch -cne $Card.attempts[$Ordinal-1].physical_recovery_epoch -or
        $v.result -cnotin @('B5_REVIEWED','RF_LIMITED','B5_ANDROID_GAP','PRE_IO_REFUSED','PENDING_ANALYSIS')) { throw 'B5_OFFLINE_VALIDATION_SCOPE_OR_RESULT_INVALID' }
    if ($v.result -ceq 'PENDING_ANALYSIS') { throw 'B5_OFFLINE_ANALYSIS_PENDING' }
    if ($v.result -ceq 'PRE_IO_REFUSED') {
        if ($Receipt.pre_io_refusal -isnot [bool] -or -not $Receipt.pre_io_refusal) { throw 'B5_PRE_IO_OBSERVATION_REQUIRED' }
    } elseif ($preGrant -or -not $Receipt.capture_complete) { throw 'B5_CAPTURE_EVIDENCE_REQUIRED' }
    if ($preGrant) {
        $evidencePath=Join-Path $attempt 'pre-grant-evidence-manifest.json'
        if ((Get-FileHash -LiteralPath $evidencePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Receipt.pre_grant_evidence_sha256) { throw 'B5_PRE_GRANT_EVIDENCE_CHANGED' }
        $e=[IO.File]::ReadAllText($evidencePath)|ConvertFrom-Json
        if ($e.schema -cne 'TBS5530-B5-PRE-GRANT-EVIDENCE/v1' -or $e.card_sha256 -cne $CardHash -or $e.session_id -cne $Card.session_id -or
            $e.ordinal -ne $Ordinal -or $e.physical_recovery_epoch -cne $Card.attempts[$Ordinal-1].physical_recovery_epoch -or $e.error_file -cne 'host-refusal.json' -or
            (Get-FileHash -LiteralPath (Join-Path $attempt 'host-refusal.json') -Algorithm SHA256).Hash.ToLowerInvariant() -cne $e.error_sha256) { throw 'B5_PRE_GRANT_ERROR_EVIDENCE_CHANGED' }
    }
    $exportPath=Join-Path $attempt 'export-manifest.json'
    if ($v.export_manifest_sha256 -cnotmatch '^[a-f0-9]{64}$' -or $v.export_manifest_sha256 -cne $Receipt.export_manifest_sha256 -or
        (Get-FileHash -LiteralPath $exportPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $v.export_manifest_sha256) { throw 'B5_EXPORT_MANIFEST_CHANGED' }
    $exports=@([IO.File]::ReadAllText($exportPath)|ConvertFrom-Json)
    foreach ($e in $exports) {
        if ($e.present -isnot [bool]) { throw 'B5_EXPORT_ENTRY_INVALID' }
        if (-not $e.present) { continue }
        $folder=if ($e.path.EndsWith('.ts')) { Join-Path $Card.raw_directory ('attempt-'+$Ordinal) } else { $attempt }
        $null=Assert-B5PrivatePath $e.local_path $folder
        if ($e.sha256 -cnotmatch '^[a-f0-9]{64}$' -or -not (Test-B5Integer $e.bytes) -or $e.bytes -lt 0 -or $e.bytes -gt (Get-B5ExportLimit $e.path) -or
            (Get-Item -LiteralPath $e.local_path).Length -ne $e.bytes -or (Get-FileHash -LiteralPath $e.local_path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $e.sha256) { throw 'B5_EXPORTED_EVIDENCE_CHANGED' }
    }
    $raw=@($exports|Where-Object {$_.path -clike '*/received-ON1.ts' -and $_.present})
    if ($v.result -ceq 'PRE_IO_REFUSED' -and $raw.Count -eq 0) {
        if ($null -ne $v.raw_sha256 -or $null -ne $Receipt.raw_sha256) { throw 'B5_PRE_IO_RAW_MUST_BE_NULL' }
    } elseif ($raw.Count -ne 1 -or $v.raw_sha256 -cne $Receipt.raw_sha256 -or $raw[0].sha256 -cne $Receipt.raw_sha256) { throw 'B5_RAW_EVIDENCE_MISMATCH' }
    $analysisPath=Assert-B5PrivatePath $v.analysis_manifest_path $Card.report_directory
    if ($v.analysis_manifest_sha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath $analysisPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $v.analysis_manifest_sha256 -or
        @($v.evidence).Count -lt 1 -or @($v.evidence).Count -gt 32) { throw 'B5_ANALYSIS_EVIDENCE_REQUIRED' }
    foreach ($e in $v.evidence) {
        $full=[IO.Path]::GetFullPath($e.path)
        if (-not ($full.StartsWith($Card.report_directory.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -or $full.StartsWith($attempt+'\',[StringComparison]::OrdinalIgnoreCase)) -or
            $e.sha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant() -cne $e.sha256) { throw 'B5_VERDICT_EVIDENCE_CHANGED' }
    }
    return [pscustomobject]@{sha256=(Get-B5Hash $bytes);result=$v.result;raw_sha256=$v.raw_sha256;analysis_manifest_sha256=$v.analysis_manifest_sha256;demod_lock_observed=$(if($null -ne $v.PSObject.Properties['demod_lock_observed']){$v.demod_lock_observed}else{$null})}
}
function Assert-B5Ledger([byte[]]$Bytes,[string]$CardHash,[object]$Card,[int]$Ordinal) {
    if ($Bytes.Length -eq 0) { if ($Ordinal -ne 1) { throw 'B5_FIRST_ORDINAL_REQUIRED' }; return }
    $text=[Text.UTF8Encoding]::new($false,$true).GetString($Bytes)
    if (-not $text.EndsWith("`n") -or $text.Contains("`r")) { throw 'B5_MANIFEST_PARTIAL_OR_CORRUPT' }
    $prefix=[byte[]]@(); $next=1; $pending=$false; $previous=$null
    foreach ($line in $text.TrimEnd([char]10).Split([char]10)) {
        $row=$line|ConvertFrom-Json
        if ($row.previous_sha256 -cne (Get-B5Hash $prefix)) { throw 'B5_MANIFEST_CHAIN_INVALID' }
        if ($prefix.Length -eq 0) {
            if ($row.event -cne 'HEADER' -or $row.card_sha256 -cne $CardHash -or $row.budget_id -cne $Card.budget_id -or
                $row.max_attempts -ne $Card.max_attempts -or $row.max_windows -ne $Card.max_windows) { throw 'B5_MANIFEST_BUDGET_MISMATCH' }
            if($null -ne $Card.PSObject.Properties['carry_forward'] -and ($row.carry_sha256 -cne (Get-B5CarryPin $Card) -or $row.prior_windows_ledger_sha256 -cne $Card.carry_forward.prior_ledger_sha256 -or $row.prior_windows_result_sha256 -cne $Card.carry_forward.prior_result_sha256)){throw 'B5_CARRY_MANIFEST_BINDING_MISMATCH'}
        } elseif ($row.event -ceq 'CONSUME' -and -not $pending) {
            if (-not (Test-B5Integer $row.ordinal) -or $row.ordinal -ne $next -or $next -gt $Card.max_attempts -or $row.epoch -cne $Card.attempts[$next-1].physical_recovery_epoch) { throw 'B5_MANIFEST_CONSUME_INVALID' }
            if($null -ne $Card.PSObject.Properties['carry_forward'] -and $row.carry_sha256 -cne (Get-B5CarryPin $Card)){throw 'B5_CARRY_CONSUME_BINDING_MISMATCH'}
            Assert-B5Card $Card @{} $next $row.epoch $row.queue_depth $row.urb_bytes
            if ($null -ne $Card.attempts[$next-1].PSObject.Properties['allowed_frontends']) {
                $null=Select-B5Frontend $Card.attempts[$next-1] $row.frontend_experiment $row.golden_sha256 $row.reload_plan_sha256
            }
            if ($next -gt 1) {
                $review=Read-B5Review $Card $CardHash $previous ($next-1)
                if ($row.previous_offline_validation_sha256 -cne $review.sha256) { throw 'B5_REVIEW_CHANGED_AFTER_CONSUME' }
            }
            $depth=$row.queue_depth; $chunk=$row.urb_bytes; $pending=$true
        } elseif ($row.event -ceq 'RECEIPT' -and $pending) {
            if ($row.ordinal -ne $next -or $row.session_closed -isnot [bool] -or $row.session_closed -or $row.drained -isnot [bool] -or
                (-not $row.drained -and -not (Test-B5PreGrantRefusal $row)) -or [string]::IsNullOrWhiteSpace($row.result) -or
                $row.queue_depth -ne $depth -or $row.urb_bytes -ne $chunk) { throw 'B5_SESSION_CLOSED' }
            $previous=$row; $next++; $pending=$false
        } else { throw 'B5_MANIFEST_SEQUENCE_INVALID' }
        $prefix=[byte[]]($prefix+[Text.Encoding]::UTF8.GetBytes($line+"`n"))
    }
    if ($pending -or $Ordinal -ne $next -or $Ordinal -gt $Card.max_attempts) { throw 'B5_CONSUMED_OR_BUDGET_EXHAUSTED' }
    if ($Ordinal -gt 1) { return Read-B5Review $Card $CardHash $previous ($Ordinal-1) }
}
function Wait-B5Permission([string]$Token) {
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while ($watch.ElapsedMilliseconds -lt 30000) {
        $cmd='if [ -f files/permission-preflight.json ]; then cat files/permission-preflight.json; fi'
        $text=Invoke-B5Adb @('shell','run-as',$package,'sh','-c',("'"+$cmd+"'"))
        if ($text) {
            $v=$text|ConvertFrom-Json
            if ($v.preflight_token -ceq $Token) {
                if ($v.state -cin @('DENIED','CANCELLED','TIMED_OUT','UNVERIFIED')) { throw 'B5_PERMISSION_ONLY_REFUSED' }
                if ($v.state -ceq 'GRANTED') { Assert-B5PermissionReady $v $Token (Get-B5Elapsed); return $v }
                if ($v.state -cnotin @('WAITING','USER_ACTION_REQUIRED')) { throw 'B5_PERMISSION_STATE_UNKNOWN' }
            }
        }
        Start-Sleep -Milliseconds 100
    }
    throw 'B5_PERMISSION_ONLY_30S_TIMEOUT'
}
function Assert-B5PermissionReady([object]$V,[string]$Token,[long]$Now) {
    foreach ($key in @('version','vid','pid','created_elapsed_ms','deadline_elapsed_ms','elapsed_ms','attachment_generation')) { if (-not (Test-B5Integer $V.$key)) { throw 'B5_PERMISSION_RECEIPT_MALFORMED' } }
    if ($V.version -ne 1 -or $V.mode -cne 'PERMISSION_ONLY' -or $V.preflight_token -cne $Token -or $V.state -cne 'GRANTED' -or $V.reason -cne 'OS_PERMISSION_VERIFIED' -or
        $V.vid -ne 29516 -or $V.pid -ne 21808 -or $V.created_elapsed_ms -lt 0 -or $V.attachment_generation -lt 0 -or
        $V.deadline_elapsed_ms-$V.created_elapsed_ms -notin @(30000,60000) -or $V.elapsed_ms -lt $V.created_elapsed_ms -or
        $Now -lt $V.elapsed_ms -or $Now-$V.elapsed_ms -ge 2000 -or $Now-$V.created_elapsed_ms -ge 30000 -or $Now -ge $V.deadline_elapsed_ms -or
        $V.device_identity_sha256 -cnotmatch '^[a-f0-9]{64}$') { throw 'B5_PERMISSION_RECEIPT_STALE_OR_UNVERIFIED' }
}
function Read-B5CarryBytes([string]$Path) { return [IO.File]::ReadAllBytes($Path) }
function Get-B5CarryProcessIdentity([int]$ProcessId) {
    $process=Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $null }
    try { return [pscustomobject]@{name=$process.ProcessName;path=$process.Path;start=$process.StartTime} }
    catch { return [pscustomobject]@{name=$null;path=$null;start=$null} }
}
function Test-B5CarryProcessRunning([int]$ProcessId,[string]$ExpectedPath,[string]$OldEnd) {
    $current=Get-B5CarryProcessIdentity $ProcessId
    if ($null -eq $current) { return $false }
    if ([string]::IsNullOrWhiteSpace($current.name) -or [string]::IsNullOrWhiteSpace($current.path) -or
        $current.start -isnot [DateTime] -or [string]::IsNullOrWhiteSpace($OldEnd)) { return $true }
    $expectedName=[IO.Path]::GetFileNameWithoutExtension($ExpectedPath)
    if ($current.name -ieq $expectedName -or $current.path -ieq $ExpectedPath) { return $true }
    $end=[DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParse($OldEnd,[ref]$end)) { return $true }
    return ([DateTimeOffset]$current.start).ToUniversalTime() -le $end.ToUniversalTime()
}
function Get-B5CarryPin([object]$Card) {
    if ($null -eq $Card.PSObject.Properties['carry_forward']) { return $null }
    return Get-B5Hash ([Text.Encoding]::UTF8.GetBytes(($Card.carry_forward|ConvertTo-Json -Depth 16 -Compress)))
}
function Assert-B5CarryForward([object]$Card) {
    if ($null -eq $Card.PSObject.Properties['carry_forward']) { return $null }
    $carry=$Card.carry_forward
    if ($carry.schema -cne 'TBS5530-B5-CARRY-FORWARD/v1' -or $carry.reason -cne 'WINDOWS_DURATION_CAP_CLEAN_EXIT' -or
        $carry.authorize_android -isnot [bool] -or -not $carry.authorize_android -or
        $carry.windows_authorized -isnot [bool] -or $carry.windows_authorized -or
        $carry.owned_processes_stopped -isnot [bool] -or -not $carry.owned_processes_stopped -or
        $Card.windows_capture.authorized -isnot [bool] -or $Card.windows_capture.authorized) { throw 'B5_CARRY_AUTHORIZATION_INVALID' }
    foreach ($pin in @{windows_consumed=1;max_windows_total=2;windows_remaining_ceiling=1;android_used=0;max_android_attempts=3}.GetEnumerator()) {
        if (-not (Test-B5Integer $carry.($pin.Key)) -or $carry.($pin.Key) -ne $pin.Value) { throw 'B5_CARRY_COUNTS_INVALID' }
    }
    if (-not (Test-B5Integer $Card.max_attempts) -or $Card.max_attempts -ne 3 -or
        -not (Test-B5Integer $Card.max_android) -or $Card.max_android -gt 6 -or $Card.max_android -lt 3 -or
        -not (Test-B5Integer $Card.max_windows) -or $Card.max_windows -ne 0 -or @($Card.attempts).Count -ne 3) { throw 'B5_CARRY_ANDROID_ONLY_BUDGET_REQUIRED' }
    if ($carry.prior_card_sha256 -cne '8f799b34e253df5d8c58c6468c14cb6187999ac566319daada4318f0558d6fe6' -or
        $carry.prior_result_sha256 -cne '2375f83cf06c8b09f9d33680458e93422a1affb4dd9342c546dc71c19051f7c8' -or
        $carry.prior_ledger_sha256 -cne 'd0e3a267bc91dbd33b2726694589a956184c86f42e79737a9173cfd82df5c845' -or
        -not (Test-B5Integer $carry.prior_ledger_bytes) -or $carry.prior_ledger_bytes -ne 443 -or
        -not (Test-B5Integer $carry.prior_result_bytes) -or $carry.prior_result_bytes -ne 5011) { throw 'B5_CARRY_EXACT_PRIOR_PINS_REQUIRED' }
    foreach ($key in @('prior_ledger_sha256','prior_receipt_sha256')) {
        if ($carry.$key -isnot [string] -or $carry.$key -cnotmatch '^[a-f0-9]{64}$') { throw 'B5_CARRY_HASH_REQUIRED' }
    }
    $priorBytes=Read-B5CarryBytes $carry.prior_card_path
    if ($priorBytes.Length -gt 65536 -or (Get-B5Hash $priorBytes) -cne $carry.prior_card_sha256) { throw 'B5_CARRY_PRIOR_CARD_CHANGED' }
    $prior=[Text.UTF8Encoding]::new($false,$true).GetString($priorBytes).TrimStart([char]0xFEFF)|ConvertFrom-Json
    foreach ($key in @('budget_id','session_id','budget_directory','session_directory','raw_directory','report_directory','model','manufacturer','api','boot_count','app_apk_sha256','firmware_sha256','autonomous_helper_sha256','b3_helper_sha256','source_git_sha')) {
        if ($prior.$key -cne $Card.$key) { throw 'B5_CARRY_PRIOR_IDENTITY_CHANGED' }
    }
    if ($carry.prior_serial -isnot [string] -or $carry.prior_serial -cne $prior.serial -or
        $Card.serial -isnot [string] -or $Card.serial -cnotmatch '^[A-Za-z0-9._:-]+$' -or
        $carry.current_serial -isnot [string] -or $carry.current_serial -cne $Card.serial -or
        $carry.current_target_identity_verified -isnot [bool] -or -not $carry.current_target_identity_verified -or
        $carry.current_target_model -cne $Card.model -or $carry.current_target_manufacturer -cne $Card.manufacturer -or
        -not (Test-B5Integer $carry.current_target_api) -or $carry.current_target_api -ne $Card.api -or
        -not (Test-B5Integer $carry.current_target_boot_count) -or $carry.current_target_boot_count -ne $Card.boot_count) { throw 'B5_CARRY_RENEWED_TARGET_INVALID' }
    if ($prior.max_attempts -ne 3 -or $prior.max_windows -ne 2 -or $prior.schema -cne 'TBS5530-B5-CARD/v1') { throw 'B5_CARRY_PRIOR_SCOPE_INVALID' }
    if (($prior.attempts|ConvertTo-Json -Depth 16 -Compress) -cne ($Card.attempts|ConvertTo-Json -Depth 16 -Compress)) { throw 'B5_CARRY_ANDROID_PLAN_CHANGED' }
    $expectedLedger=Join-Path $Card.budget_directory 'b5-windows-budget.jsonl'
    if ($carry.prior_ledger_path -cne $expectedLedger) { throw 'B5_CARRY_LEDGER_PATH_CHANGED' }
    $null=Assert-B5PrivatePath $carry.prior_ledger_path 'D:\TSDuck\logs'
    $null=Assert-B5PrivatePath $carry.prior_result_path 'D:\TSDuck\logs'
    $ledgerBytes=Read-B5CarryBytes $carry.prior_ledger_path
    $resultBytes=Read-B5CarryBytes $carry.prior_result_path
    foreach ($entry in @(@($ledgerBytes,'prior_ledger_bytes','prior_ledger_sha256'),@($resultBytes,'prior_result_bytes','prior_result_sha256'))) {
        if (-not (Test-B5Integer $carry.($entry[1])) -or $carry.($entry[1]) -lt 1 -or $carry.($entry[1]) -gt 1048576 -or
            $entry[0].Length -ne $carry.($entry[1]) -or (Get-B5Hash $entry[0]) -cne $carry.($entry[2])) { throw 'B5_CARRY_PRIOR_EVIDENCE_CHANGED' }
    }
    $text=[Text.UTF8Encoding]::new($false,$true).GetString($ledgerBytes)
    if (-not $text.EndsWith("`n") -or $text.Contains("`r")) { throw 'B5_CARRY_LEDGER_BYTES_INVALID' }
    $lines=$text.TrimEnd([char]10).Split([char]10)
    if ($lines.Count -ne 3) { throw 'B5_CARRY_OTHER_ATTEMPTS_FORBIDDEN' }
    $header=$lines[0]|ConvertFrom-Json;$consume=$lines[1]|ConvertFrom-Json;$receipt=$lines[2]|ConvertFrom-Json
    if ($header.event -cne 'HEADER' -or $header.card_sha256 -cne $carry.prior_card_sha256 -or $header.budget_id -cne $Card.budget_id -or
        -not (Test-B5Integer $header.max_windows) -or $header.max_windows -ne 2 -or
        $consume.event -cne 'CONSUME' -or -not (Test-B5Integer $consume.ordinal) -or $consume.ordinal -ne 1 -or
        -not (Test-B5Integer $consume.frequency_hz) -or $consume.frequency_hz -ne 506000000 -or
        $receipt.event -cne 'RECEIPT' -or -not (Test-B5Integer $receipt.ordinal) -or $receipt.ordinal -ne 1 -or
        $receipt.status -cne 'CAPTURE_INCOMPLETE' -or $receipt.session_closed -isnot [bool] -or -not $receipt.session_closed -or
        (Get-B5Hash ([Text.Encoding]::UTF8.GetBytes($lines[2]+"`n"))) -cne $carry.prior_receipt_sha256) { throw 'B5_CARRY_RECEIPT_SCOPE_INVALID' }
    $result=[Text.UTF8Encoding]::new($false,$true).GetString($resultBytes).TrimStart([char]0xFEFF)|ConvertFrom-Json
    if ($result.status -cne 'CAPTURE_INCOMPLETE' -or $null -ne $result.error -or
        $null -eq $result.PSObject.Properties['ts_wall_seconds'] -or $result.ts_wall_seconds -is [bool] -or
        $result.ts_wall_seconds -isnot [ValueType] -or $result.ts_wall_seconds -le 0 -or $result.ts_wall_seconds -ge 40 -or
        ($null -ne $result.PSObject.Properties['copy_pending'] -and $result.copy_pending -cne $false) -or
        ($null -ne $result.PSObject.Properties['copy_error'] -and $null -ne $result.copy_error)) { throw 'B5_CARRY_RESULT_NOT_CLEAN' }
    $labels=@('usbpcap-cold','ownership-before-bda','bda-mode','b5-ts')
    if (@($result.children).Count -ne 4) { throw 'B5_CARRY_UNKNOWN_CHILDREN' }
    $ids=[Collections.Generic.HashSet[int]]::new()
    $processNames=@{};$processEnds=@{}
    if (-not (Test-B5Integer $result.helper_pid) -or $result.helper_pid -lt 1 -or $result.helper_pid -gt [int]::MaxValue -or
        -not $ids.Add([int]$result.helper_pid)) { throw 'B5_CARRY_PROCESS_PROOF_INVALID' }
    $processNames[[int]$result.helper_pid]='C:\Program Files\PowerShell\7\pwsh.exe'
    $processEnds[[int]$result.helper_pid]=$result.end
    foreach ($label in $labels) {
        $child=@($result.children|Where-Object {$_.label -ceq $label})
        if ($child.Count -ne 1 -or -not (Test-B5Integer $child[0].pid) -or $child[0].pid -lt 1 -or $child[0].pid -gt [int]::MaxValue -or
            -not $ids.Add([int]$child[0].pid) -or [string]::IsNullOrWhiteSpace($child[0].end)) { throw 'B5_CARRY_PROCESS_PROOF_INVALID' }
        $expectedExe=switch($label){'usbpcap-cold'{'C:\Program Files\USBPcap\USBPcapCMD.exe'};'ownership-before-bda'{'C:\Program Files\usbipd-win\usbipd.exe'};'bda-mode'{'C:\Program Files\TSDuck\bin\tslsdvb.exe'};'b5-ts'{'C:\Program Files\TSDuck\bin\tsp.exe'}}
        if($child[0].exe -cne $expectedExe){throw 'B5_CARRY_CHILD_EXECUTABLE_UNKNOWN'}
        $processNames[[int]$child[0].pid]=$expectedExe
        $processEnds[[int]$child[0].pid]=$child[0].end
        $expectedExit=if($label -ceq 'usbpcap-cold'){-1}else{0}
        $expectedStop=if($label -ceq 'usbpcap-cold'){'BDA graph closed; capture complete'}else{'natural'}
        if (-not (Test-B5Integer $child[0].exit) -or $child[0].exit -ne $expectedExit -or $child[0].stop -cne $expectedStop) { throw 'B5_CARRY_CHILD_NOT_CLEAN' }
    }
    if (@($carry.owned_process_ids).Count -ne $ids.Count) { throw 'B5_CARRY_PROCESS_PROOF_INVALID' }
    $proofIds=[Collections.Generic.HashSet[int]]::new()
    foreach ($id in $carry.owned_process_ids) {
        if (-not (Test-B5Integer $id) -or $id -lt 1 -or $id -gt [int]::MaxValue -or -not $ids.Contains([int]$id) -or
            -not $proofIds.Add([int]$id) -or (Test-B5CarryProcessRunning ([int]$id) $processNames[[int]$id] $processEnds[[int]$id])) { throw 'B5_CARRY_OWNED_PROCESS_NOT_STOPPED' }
    }
    if ($carry.terminal_cancellation_reviewed -isnot [bool] -or -not $carry.terminal_cancellation_reviewed -or
        $carry.data_error_review -cne 'NO_DATA_ERROR_REVIEWED_TERMINAL_CANCELLATION_ONLY') { throw 'B5_CARRY_TRANSPORT_REVIEW_REQUIRED' }
    $reviewed=@(
        @('transport_status','0386775de98b63d2ef8c090eaac2b6d6ec662f904cc1948357c88b40d3e4e60e',2375),
        @('teardown_status','17fc126f67ce8f8ef9c1e72b2e8ae0d0056176cf0ce8c32a46ac62d698d26500',1479),
        @('cleanup_evidence','557f23a8aede85fe341d5ecf16c883a73ee60bfbfc51802872f542030397da21',783))
    $reviewBytes=@{}
    foreach ($entry in $reviewed) {
        $key=$entry[0];$pathKey=$key+'_path';$hashKey=$key+'_sha256';$sizeKey=$key+'_bytes'
        $null=Assert-B5PrivatePath $carry.$pathKey 'D:\TSDuck\reports'
        $bytes=Read-B5CarryBytes $carry.$pathKey
        if ($carry.$hashKey -cne $entry[1] -or -not (Test-B5Integer $carry.$sizeKey) -or $carry.$sizeKey -ne $entry[2] -or
            $bytes.Length -ne $entry[2] -or (Get-B5Hash $bytes) -cne $entry[1]) { throw 'B5_CARRY_REVIEW_EVIDENCE_CHANGED' }
        $reviewBytes[$key]=$bytes
    }
    $transport=[Text.UTF8Encoding]::new($false,$true).GetString($reviewBytes.transport_status)|ConvertFrom-Json
    if ($transport.successful_payload_urbs -ne 19565 -or $transport.orphan_successful_completions -ne 0 -or
        $transport.pending_bulk_0x82 -ne 0 -or @($transport.failed_completions).Count -ne 8) { throw 'B5_CARRY_TRANSPORT_REVIEW_UNKNOWN' }
    foreach ($failure in $transport.failed_completions) {
        if ($failure.status -ne 3221291008 -or $failure.stream_offset -ne 160276480 -or $failure.frame -lt 65498 -or $failure.frame -gt 65505) { throw 'B5_CARRY_DATA_ERROR_FORBIDDEN' }
    }
    $teardown=[Text.UTF8Encoding]::new($false,$true).GetString($reviewBytes.teardown_status)
    if (-not $teardown.Contains('USBD_STATUS_CANCELED (0xc0010000)')) { throw 'B5_CARRY_CANCELLATION_STATUS_UNKNOWN' }
    $cleanup=[Text.UTF8Encoding]::new($false,$true).GetString($reviewBytes.cleanup_evidence)|ConvertFrom-Json
    if ($cleanup.ledger_sha256 -cne $carry.prior_ledger_sha256 -or $cleanup.result_sha256 -cne $carry.prior_result_sha256 -or
        $cleanup.user_unplugged_confirmed -isnot [bool] -or -not $cleanup.user_unplugged_confirmed -or
        @($cleanup.owned_processes).Count -ne 3) { throw 'B5_CARRY_CLEANUP_PROOF_UNKNOWN' }
    foreach ($process in $cleanup.owned_processes) {
        if ($process.present -isnot [bool] -or $process.present -or -not $ids.Contains([int]$process.pid)) { throw 'B5_CARRY_CLEANUP_NOT_CONFIRMED' }
    }
    if ($null -ne $result.PSObject.Properties['last_errno'] -and $result.last_errno -in @(-75,75,-71,71)) { throw 'B5_CARRY_TRANSPORT_ERROR_FORBIDDEN' }
    if (Test-Path -LiteralPath (Join-Path $Card.budget_directory 'b5-session-manifest.jsonl')) { throw 'B5_CARRY_ANDROID_ALREADY_USED_OR_UNKNOWN' }
    return [pscustomobject]@{sha256=(Get-B5CarryPin $Card);prior_card_sha256=$carry.prior_card_sha256;prior_ledger_sha256=$carry.prior_ledger_sha256;prior_result_sha256=$carry.prior_result_sha256}
}

function Get-B5TargetMigrationPin([object]$Card) {
    if ($null -eq $Card.PSObject.Properties['target_migration']) { return $null }
    return Get-B5Hash ([Text.Encoding]::UTF8.GetBytes(($Card.target_migration|ConvertTo-Json -Depth 16 -Compress)))
}
function Assert-B5TargetMigration([object]$Card,[int]$Ordinal) {
    if ($null -eq $Card.PSObject.Properties['target_migration']) { return $null }
    $m=$Card.target_migration
    if ($m.schema -cne 'TBS5530-B5-TARGET-MIGRATION/v1' -or $m.reason -cne 'EXPLICIT_ADB_TARGET_RENEWAL_NO_DEVICE_IO' -or
        $Ordinal -ne 3 -or $m.authorize_ordinal3_k25 -isnot [bool] -or -not $m.authorize_ordinal3_k25 -or
        $m.windows_authorized -isnot [bool] -or $m.windows_authorized -or $m.current_target_identity_verified -isnot [bool] -or
        -not $m.current_target_identity_verified -or $m.current_serial -cne $Card.serial -or
        $m.current_host_profile -cne 'oneplus-gm1900-api30' -or $m.current_model -cne 'GM1900' -or
        $m.current_manufacturer -cne 'OnePlus' -or -not (Test-B5Integer $m.current_api) -or $m.current_api -ne 30 -or
        -not (Test-B5Integer $m.current_boot_count) -or $m.current_boot_count -ne 408) { throw 'B5_TARGET_MIGRATION_SCOPE_INVALID' }
    foreach ($pin in @{inherited_android_consumed=2;next_ordinal=3;max_attempts=3;max_android_total=6;prior_ledger_bytes=3219}.GetEnumerator()) {
        if (-not (Test-B5Integer $m.($pin.Key)) -or $m.($pin.Key) -ne $pin.Value) { throw 'B5_TARGET_MIGRATION_COUNTS_INVALID' }
    }
    if ($m.prior_card_sha256 -cne '8b0072a745e7a939083fd02ccf987e5295d4362c7ef26c2dcfad68fb94ba60e9' -or
        $m.prior_ledger_sha256 -cne '1aaa16803da62493db50661f09cf6ffe472fd8333a7593dfb7dff1854f22b263' -or
        $Card.max_attempts -ne 3 -or $Card.max_windows -ne 0) { throw 'B5_TARGET_MIGRATION_EXACT_PINS_REQUIRED' }
    $priorBytes=Read-B5CarryBytes $m.prior_card_path
    if ($priorBytes.Length -gt 65536 -or (Get-B5Hash $priorBytes) -cne $m.prior_card_sha256) { throw 'B5_TARGET_MIGRATION_PRIOR_CARD_CHANGED' }
    $prior=[Text.UTF8Encoding]::new($false,$true).GetString($priorBytes).TrimStart([char]0xFEFF)|ConvertFrom-Json
    foreach ($key in @('budget_id','session_id','budget_directory','session_directory','raw_directory','report_directory','model','manufacturer','api','boot_count','app_apk_sha256','firmware_sha256','autonomous_helper_sha256','b3_helper_sha256','source_git_sha','max_attempts','max_android','max_windows')) {
        if ($Card.$key -cne $prior.$key) { throw 'B5_TARGET_MIGRATION_PRIOR_IDENTITY_CHANGED' }
    }
    if ($m.prior_serial -isnot [string] -or $m.prior_serial -cne $prior.serial -or
        ($Card.attempts|ConvertTo-Json -Depth 16 -Compress) -cne ($prior.attempts|ConvertTo-Json -Depth 16 -Compress)) { throw 'B5_TARGET_MIGRATION_PRIOR_PLAN_CHANGED' }
    $priorCarry=$prior.carry_forward|ConvertTo-Json -Depth 16 -Compress|ConvertFrom-Json
    $currentCarry=$Card.carry_forward|ConvertTo-Json -Depth 16 -Compress|ConvertFrom-Json
    $priorCarry.current_serial=$currentCarry.current_serial
    if (($priorCarry|ConvertTo-Json -Depth 16 -Compress) -cne ($currentCarry|ConvertTo-Json -Depth 16 -Compress)) { throw 'B5_TARGET_MIGRATION_WINDOWS_CARRY_CHANGED' }
    $null=Assert-B5CarryForward $prior
    $null=Assert-B5CarryForward $Card
    if ($m.prior_ledger_path -cne (Join-Path $Card.budget_directory 'b5-android-carry-manifest.jsonl')) { throw 'B5_TARGET_MIGRATION_LEDGER_PATH_CHANGED' }
    $ledger=Read-B5CarryBytes $m.prior_ledger_path
    if ($ledger.Length -ne 3219 -or (Get-B5Hash $ledger) -cne $m.prior_ledger_sha256) { throw 'B5_TARGET_MIGRATION_LEDGER_CHANGED' }
    $text=[Text.UTF8Encoding]::new($false,$true).GetString($ledger)
    $rows=@($text.TrimEnd([char]10).Split([char]10)|ForEach-Object {$_|ConvertFrom-Json})
    if ($rows.Count -ne 5 -or @($rows|Where-Object {$_.event -ceq 'CONSUME'}).Count -ne 2 -or
        @($rows|Where-Object {$_.event -ceq 'RECEIPT'}).Count -ne 2 -or @($m.consumed).Count -ne 2) { throw 'B5_TARGET_MIGRATION_PRIOR_COUNT_CHANGED' }
    $null=Assert-B5Ledger $ledger $m.prior_card_sha256 $prior 3
    $reviewPins=@('29d2bad941cb3771c488816976eee668b763ec1ecae9d3a3728d91da5e0eb62c','dc0bc9d20f30d6fda8171283e3700794d46f7148cba790e526a6d318f34a3601')
    $lines=$text.TrimEnd([char]10).Split([char]10)
    foreach ($i in 1..2) {
        $receipt=$rows[$i*2];$entry=$m.consumed[$i-1]
        if (-not (Test-B5PreGrantRefusal $receipt) -or $receipt.ordinal -ne $i -or
            -not (Test-B5Integer $entry.ordinal) -or $entry.ordinal -ne $i -or $entry.result -cne 'PRE_IO_REFUSED' -or
            $entry.review_sha256 -cne $reviewPins[$i-1] -or
            $entry.receipt_sha256 -cne (Get-B5Hash ([Text.Encoding]::UTF8.GetBytes($lines[$i*2]+"`n")))) { throw 'B5_TARGET_MIGRATION_RECEIPT_NOT_PRE_IO' }
        $review=Read-B5Review $prior $m.prior_card_sha256 $receipt $i
        if ($review.result -cne 'PRE_IO_REFUSED' -or $review.sha256 -cne $entry.review_sha256) { throw 'B5_TARGET_MIGRATION_REVIEW_CHANGED' }
    }
    return [pscustomobject]@{sha256=(Get-B5TargetMigrationPin $Card);prior_card_sha256=$m.prior_card_sha256;prior_ledger_sha256=$m.prior_ledger_sha256;inherited_consumed=2;next_ordinal=3}
}
function Assert-B5TargetMigrationLedger([byte[]]$Bytes,[string]$CardHash,[object]$Card,[int]$Ordinal) {
    if ($Ordinal -ne 3) { throw 'B5_TARGET_MIGRATION_LAST_ORDINAL_ONLY' }
    if ($Bytes.Length -eq 0) { return }
    $text=[Text.UTF8Encoding]::new($false,$true).GetString($Bytes)
    if (-not $text.EndsWith("`n") -or $text.Contains("`r")) { throw 'B5_TARGET_MIGRATION_MANIFEST_CORRUPT' }
    $lines=$text.TrimEnd([char]10).Split([char]10)
    if ($lines.Count -ne 1) { throw 'B5_TARGET_MIGRATION_ALREADY_CONSUMED' }
    $h=$lines[0]|ConvertFrom-Json
    if ($h.event -cne 'HEADER' -or $h.card_sha256 -cne $CardHash -or $h.budget_id -cne $Card.budget_id -or
        $h.session_id -cne $Card.session_id -or $h.target_migration_sha256 -cne (Get-B5TargetMigrationPin $Card) -or
        $h.parent_ledger_sha256 -cne $Card.target_migration.prior_ledger_sha256 -or
        -not (Test-B5Integer $h.inherited_consumed) -or $h.inherited_consumed -ne 2 -or $h.next_ordinal -ne 3 -or
        $h.previous_sha256 -cne (Get-B5Hash ([byte[]]@()))) { throw 'B5_TARGET_MIGRATION_HEADER_MISMATCH' }
}

function Get-B5OneExtraPin([object]$Card) {
    if ($null -eq $Card.PSObject.Properties['one_extra_extension']) { return $null }
    return Get-B5Hash ([Text.Encoding]::UTF8.GetBytes(($Card.one_extra_extension|ConvertTo-Json -Depth 16 -Compress)))
}
function Assert-B5OneExtraExtension([object]$Card,[int]$Ordinal) {
    if ($null -eq $Card.PSObject.Properties['one_extra_extension']) { return $null }
    $x=$Card.one_extra_extension
    if ($Ordinal -ne 4 -or $x.schema -cne 'TBS5530-B5-ONE-EXTRA/v1' -or $x.reason -cne 'DUPLICATE_FOREGROUND_PRE_GRANT_REFUSAL' -or
        $x.authorize_ordinal4_k25 -isnot [bool] -or -not $x.authorize_ordinal4_k25 -or
        $x.windows_authorized -isnot [bool] -or $x.windows_authorized -or $Card.max_windows -ne 0) { throw 'B5_ONE_EXTRA_SCOPE_INVALID' }
    foreach ($pin in @{inherited_android_consumed=3;next_ordinal=4;max_attempts=4;max_android_total=6;prior_ledger_bytes=1943}.GetEnumerator()) {
        if (-not (Test-B5Integer $x.($pin.Key)) -or $x.($pin.Key) -ne $pin.Value) { throw 'B5_ONE_EXTRA_COUNTS_INVALID' }
    }
    if ($Card.max_attempts -ne 4 -or @($Card.attempts).Count -ne 4 -or $x.prior_card_sha256 -cne 'b9cbeb879dc3ab56210de513a945f6b76db53b70a26f5f44a7504193ea51ddd3' -or
        $x.prior_ledger_sha256 -cne 'd12e2fde0fc364fdad688530294d8e0fccc3f0963e27c0d8947ca03622b9c809' -or
        $x.review3_sha256 -cne '1df8323b587f41b648ea08100fe55de2422c4258b264e258e2ee45961aa6ffa3') { throw 'B5_ONE_EXTRA_EXACT_PINS_REQUIRED' }
    $priorBytes=Read-B5CarryBytes $x.prior_card_path
    if ($priorBytes.Length -gt 65536 -or (Get-B5Hash $priorBytes) -cne $x.prior_card_sha256) { throw 'B5_ONE_EXTRA_PRIOR_CARD_CHANGED' }
    $prior=[Text.UTF8Encoding]::new($false,$true).GetString($priorBytes).TrimStart([char]0xFEFF)|ConvertFrom-Json
    foreach ($key in @('budget_id','session_id','budget_directory','session_directory','raw_directory','report_directory','model','manufacturer','api','boot_count','serial','app_apk_sha256','firmware_sha256','autonomous_helper_sha256','b3_helper_sha256','source_git_sha','max_android','max_windows')) {
        if ($Card.$key -cne $prior.$key) { throw 'B5_ONE_EXTRA_PRIOR_IDENTITY_CHANGED' }
    }
    foreach ($key in @('carry_forward','target_migration')) {
        if (($Card.$key|ConvertTo-Json -Depth 20 -Compress) -cne ($prior.$key|ConvertTo-Json -Depth 20 -Compress)) { throw 'B5_ONE_EXTRA_PRIOR_AUTHORITY_CHANGED' }
    }
    if (($Card.attempts[0..2]|ConvertTo-Json -Depth 16 -Compress) -cne ($prior.attempts|ConvertTo-Json -Depth 16 -Compress)) { throw 'B5_ONE_EXTRA_PRIOR_PLAN_CHANGED' }
    $last=$Card.attempts[3];$variants=@(Get-B5AllowedFrontends $last)
    if ($last.ordinal -ne 4 -or $variants.Count -ne 1 -or $variants[0].frontend_experiment -cne 'B5_K25_MANUAL_PLP0' -or
        $variants[0].golden_sha256 -cne '8412748df957ac1750e43a71864994dd9e9b77b40af5b4b8a95e69721fef5422' -or
        $variants[0].reload_plan_sha256 -cne '71620f60a9b35a65c4e138d672695e1a3d491d199dff38c4a8dde787586938a9') { throw 'B5_ONE_EXTRA_K25_ONLY' }
    $migration=Assert-B5TargetMigration $prior 3
    if ($x.prior_ledger_path -cne (Join-Path $Card.budget_directory 'b5-target-migration-manifest.jsonl')) { throw 'B5_ONE_EXTRA_LEDGER_PATH_CHANGED' }
    $bytes=Read-B5CarryBytes $x.prior_ledger_path
    if ($bytes.Length -ne 1943 -or (Get-B5Hash $bytes) -cne $x.prior_ledger_sha256) { throw 'B5_ONE_EXTRA_LEDGER_CHANGED' }
    $text=[Text.UTF8Encoding]::new($false,$true).GetString($bytes)
    if(-not $text.EndsWith("`n") -or $text.Contains("`r")){throw 'B5_ONE_EXTRA_LEDGER_CORRUPT'}
    $lines=$text.TrimEnd([char]10).Split([char]10)
    if($lines.Count -ne 3){throw 'B5_ONE_EXTRA_OTHER_ATTEMPTS_FORBIDDEN'}
    $prefix=[byte[]]@();$rows=@()
    foreach($line in $lines){$row=$line|ConvertFrom-Json;if($row.previous_sha256 -cne (Get-B5Hash $prefix)){throw 'B5_ONE_EXTRA_CHAIN_INVALID'};$rows+=,$row;$prefix=[byte[]]($prefix+[Text.Encoding]::UTF8.GetBytes($line+"`n"))}
    $h=$rows[0];$consume=$rows[1];$receipt=$rows[2]
    if ($h.event -cne 'HEADER' -or $h.card_sha256 -cne $x.prior_card_sha256 -or $h.budget_id -cne $Card.budget_id -or $h.session_id -cne $Card.session_id -or
        $h.parent_ledger_sha256 -cne $prior.target_migration.prior_ledger_sha256 -or $h.target_migration_sha256 -cne $migration.sha256 -or
        $consume.event -cne 'CONSUME' -or $consume.ordinal -ne 3 -or $consume.frontend_experiment -cne 'B5_K25_MANUAL_PLP0' -or
        -not (Test-B5PreGrantRefusal $receipt) -or $receipt.ordinal -ne 3 -or
        $x.receipt3_sha256 -cne (Get-B5Hash ([Text.Encoding]::UTF8.GetBytes($lines[2]+"`n")))) { throw 'B5_ONE_EXTRA_NOT_PRE_IO' }
    $review=Read-B5Review $prior $x.prior_card_sha256 $receipt 3
    if ($review.result -cne 'PRE_IO_REFUSED' -or $review.sha256 -cne $x.review3_sha256) { throw 'B5_ONE_EXTRA_REVIEW_CHANGED' }
    $failure=[IO.File]::ReadAllText((Join-Path $Card.budget_directory 'attempt-3\host-refusal.json'))|ConvertFrom-Json
    if ($failure.error_category -cne 'B5_FOREGROUND_UNVERIFIED' -or $failure.stage -cne 'B5_LAUNCH' -or $failure.grant_may_have_been_published -cne $false) { throw 'B5_ONE_EXTRA_FAILURE_SCOPE_INVALID' }
    return [pscustomobject]@{sha256=(Get-B5OneExtraPin $Card);prior_ledger_sha256=$x.prior_ledger_sha256;prior_card_sha256=$x.prior_card_sha256;carry=(Assert-B5CarryForward $prior);inherited_consumed=3;next_ordinal=4}
}
function Assert-B5OneExtraLedger([byte[]]$Bytes,[string]$CardHash,[object]$Card,[int]$Ordinal) {
    if ($Ordinal -ne 4) { throw 'B5_ONE_EXTRA_ORDINAL4_ONLY' }
    if ($Bytes.Length -eq 0) { return }
    $text=[Text.UTF8Encoding]::new($false,$true).GetString($Bytes)
    if (-not $text.EndsWith("`n") -or $text.Contains("`r")) { throw 'B5_ONE_EXTRA_MANIFEST_CORRUPT' }
    $lines=$text.TrimEnd([char]10).Split([char]10)
    if ($lines.Count -ne 1) { throw 'B5_ONE_EXTRA_ALREADY_CONSUMED' }
    $h=$lines[0]|ConvertFrom-Json
    if ($h.event -cne 'HEADER' -or $h.card_sha256 -cne $CardHash -or $h.budget_id -cne $Card.budget_id -or $h.session_id -cne $Card.session_id -or
        $h.one_extra_sha256 -cne (Get-B5OneExtraPin $Card) -or $h.parent_ledger_sha256 -cne $Card.one_extra_extension.prior_ledger_sha256 -or
        $h.inherited_consumed -ne 3 -or $h.next_ordinal -ne 4 -or $h.max_attempts -ne 4 -or $h.previous_sha256 -cne (Get-B5Hash ([byte[]]@()))) { throw 'B5_ONE_EXTRA_HEADER_MISMATCH' }
}

function Get-B5CaptureClassification([object]$Report,[object[]]$Exports) {
    $capture=-not (Test-B5SessionClosed $Report)
    if ($capture) {
        try { Assert-B5PhaseReport $Report -Complete } catch { $capture=$false }
        foreach ($phase in @($Report.b5_phases|Where-Object {$_.phase -cin @('ON1','ON2')})) {
            $raw=@($Exports|Where-Object {$_.path.EndsWith('/received-'+$phase.phase+'.ts',[StringComparison]::Ordinal) -and $_.present})
            if ($raw.Count -ne 1 -or -not (Test-B5Integer $raw[0].bytes) -or $raw[0].bytes -ne $phase.bytes) { $capture=$false }
        }
    }
    return [pscustomobject]@{classification='PENDING_ANALYSIS';capture_complete=[bool]$capture;single_run_pass=$false;root_cause_confirmed=$false;final_ts_smoke_pass=$false}
}

# B5_MAIN: import declarations, then validate every local pin before device commands.
$b3=Join-Path $PSScriptRoot 'Invoke-TBS5530-B3Session.ps1'
Import-B5SharedGuards $b3 $ExpectedB3HelperSha256
Set-B5Overrides
if (-not $GrantBoundedSession -or -not $ProofOfGuardPassed -or -not $ApproveB5SessionBudget -or $Approval -cne 'OK B5' -or
    -not $PhysicalRecoveryConfirmed -or $PhysicalColdSeconds -lt 5 -or -not $RfInConfirmed -or -not $AntennaNoDcConfirmed) { throw 'B5_EXPLICIT_BUDGET_COLD_RF_ANTENNA_PROOF_REQUIRED' }
Assert-B5Guid $PhysicalRecoveryEpoch
if ($ExpectedModel -cne 'GM1900' -or $ExpectedApi -ne 30 -or $DurationSeconds -ne 300 -or $B5QueueDepth -ne 16 -or $B5UrbBytes -ne 16384) { throw 'B5_SCOPE_INVALID' }
foreach ($pin in @($ExpectedB5CardSha256,$ExpectedB5HelperSha256,$ExpectedB3HelperSha256,$ExpectedHelperSha256,$ExpectedAppApkSha256,$ExpectedFrontendGoldenSha256,$ExpectedReloadPlanSha256)) { if ($pin -cnotmatch '^[a-f0-9]{64}$') { throw 'B5_EXACT_HASHES_REQUIRED' } }
$runtime=Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
if ((Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ExpectedB5HelperSha256 -or
    (Get-FileHash -LiteralPath $runtime -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ExpectedHelperSha256) { throw 'B5_HELPER_HASH_MISMATCH' }
if ((Get-Item -LiteralPath $FirmwarePath).Length -ne 8192 -or (Get-FileHash -LiteralPath $FirmwarePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $firmwareSha) { throw 'B5_FIRMWARE_HASH_MISMATCH' }
$cardBytes=[IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $B5CardPath))
if ($cardBytes.Length -gt 65536 -or (Get-B5Hash $cardBytes) -cne $ExpectedB5CardSha256) { throw 'B5_CARD_HASH_MISMATCH' }
$card=[Text.UTF8Encoding]::new($false,$true).GetString($cardBytes).TrimStart([char]0xFEFF)|ConvertFrom-Json
$sessionPath=[IO.Path]::GetFullPath($B5SessionDirectory)
$pins=@{schema='TBS5530-B5-CARD/v1';approval='OK B5';model=$ExpectedModel;api=$ExpectedApi;boot_count=$ExpectedBootCount;session_directory=$sessionPath
    app_apk_sha256=$ExpectedAppApkSha256;autonomous_helper_sha256=$ExpectedHelperSha256;b3_helper_sha256=$ExpectedB3HelperSha256;b5_helper_sha256=$ExpectedB5HelperSha256
    firmware_sha256=$firmwareSha
    rssi_sequence='WINDOWS_88_00_87_C0';serial=$Serial;manufacturer='OnePlus';layout=$layout;stale_prefix_bytes=2048;monitor_period_ms=100;monitor_jitter_ms=20;rssi_samples_per_off=3;max_bytes=$B5MaxBytes;duration_ms=$B5DurationMillis;ttl_ms=300000;heartbeat_ms=2000;permission_wait_ms=30000}
Assert-B5Card $card $pins $B5AttemptOrdinal $PhysicalRecoveryEpoch $B5QueueDepth $B5UrbBytes
$selected=Select-B5Frontend $card.attempts[$B5AttemptOrdinal-1] $FrontendExperiment $ExpectedFrontendGoldenSha256 $ExpectedReloadPlanSha256
Assert-B5Source $card ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')))
$attemptPath=Join-Path $sessionPath ('attempt-'+$B5AttemptOrdinal)
$rawAttemptPath=Join-Path $card.raw_directory ('attempt-'+$B5AttemptOrdinal)
if ($OutputDirectory -and [IO.Path]::GetFullPath($OutputDirectory) -cne $attemptPath) { throw 'B5_OUTPUT_MUST_MATCH_BUDGET_ATTEMPT' }
if ((Test-Path -LiteralPath $attemptPath) -or (Test-Path -LiteralPath $rawAttemptPath)) { throw 'B5_ATTEMPT_OUTPUT_ALREADY_EXISTS' }
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw 'B5_PINNED_ADB_MISSING' }
if (-not (Test-Path -LiteralPath $sessionPath)) { $null=New-Item -ItemType Directory -Path $sessionPath }
$windowsLedger=Join-Path $sessionPath 'b5-windows-budget.jsonl'
$extensionProof=Assert-B5OneExtraExtension $card $B5AttemptOrdinal
$migrationProof=$null
if($null -ne $extensionProof){
    if($FrontendExperiment -cne 'B5_K25_MANUAL_PLP0'){throw 'B5_ONE_EXTRA_K25_ONLY'}
    $carryProof=$extensionProof.carry
}else{
    $migrationProof=Assert-B5TargetMigration $card $B5AttemptOrdinal
    if($null -ne $migrationProof -and $FrontendExperiment -cne 'B5_K25_MANUAL_PLP0'){throw 'B5_TARGET_MIGRATION_K25_ONLY'}
    $carryProof=Assert-B5CarryForward $card
}
if($null -eq $carryProof -and (Test-Path -LiteralPath $windowsLedger)){
    $last=Get-Content -LiteralPath $windowsLedger -Tail 1|ConvertFrom-Json
    if($last.event -cne 'RECEIPT' -or $last.session_closed -isnot [bool] -or $last.session_closed){throw 'B5_WINDOWS_BUDGET_PENDING_OR_CLOSED'}
}
$manifestName=if($null -ne $extensionProof){'b5-one-extra-manifest.jsonl'}elseif($null -ne $migrationProof){'b5-target-migration-manifest.jsonl'}elseif($null -ne $carryProof){'b5-android-carry-manifest.jsonl'}else{'b5-session-manifest.jsonl'}
$manifestPath=Join-Path $sessionPath $manifestName
$manifestStream=[IO.FileStream]::new($manifestPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$consumed=$false; $granted=$false; $terminalReport=$null; $exitCode=1; $failure='HOST_ABORTED_NO_RETRY'; $challenge=$null; $classification=$null
$captureRawSha=$null; $exportManifestSha=$null; $preGrantEvidenceSha=$null; $hostError='HOST_PRE_GRANT_EXCEPTION'
try {
    $prefix=Read-B5Manifest $manifestStream
    if($null -ne $extensionProof){$extensionProof=Assert-B5OneExtraExtension $card $B5AttemptOrdinal}else{if($null -ne $carryProof){$carryProof=Assert-B5CarryForward $card}}
    if($null -ne $extensionProof){
        Assert-B5OneExtraLedger $prefix $ExpectedB5CardSha256 $card $B5AttemptOrdinal
        Assert-B5Identity;Assert-B5Apk
        $awake=Invoke-B5Adb @('shell','dumpsys','power')
        if($awake -cnotmatch '(?m)^\s*mWakefulness=Awake\r?$'){throw 'B5_SCREEN_NOT_AWAKE_USER_ACTION_REQUIRED'}
        $extensionProof=Assert-B5OneExtraExtension $card $B5AttemptOrdinal
        $prior=$null
        if($prefix.Length -eq 0){$prefix=Add-B5ManifestRecord $manifestStream $prefix ([pscustomobject]@{event='HEADER';card_sha256=$ExpectedB5CardSha256;budget_id=$card.budget_id;session_id=$card.session_id;one_extra_sha256=$extensionProof.sha256;parent_ledger_sha256=$extensionProof.prior_ledger_sha256;inherited_consumed=3;next_ordinal=4;max_attempts=4;max_windows=0})}
    }elseif($null -ne $migrationProof){
        $migrationProof=Assert-B5TargetMigration $card $B5AttemptOrdinal
        Assert-B5TargetMigrationLedger $prefix $ExpectedB5CardSha256 $card $B5AttemptOrdinal
        Assert-B5Identity;Assert-B5Apk
        $awake=Invoke-B5Adb @('shell','dumpsys','power')
        if($awake -cnotmatch '(?m)^\s*mWakefulness=Awake\r?$'){throw 'B5_SCREEN_NOT_AWAKE_USER_ACTION_REQUIRED'}
        $prior=$null
        if($prefix.Length -eq 0){$prefix=Add-B5ManifestRecord $manifestStream $prefix ([pscustomobject]@{event='HEADER';card_sha256=$ExpectedB5CardSha256;budget_id=$card.budget_id;session_id=$card.session_id;one_extra_sha256=$(if($null -ne $extensionProof){$extensionProof.sha256}else{$null});target_migration_sha256=$migrationProof.sha256;parent_ledger_sha256=$migrationProof.prior_ledger_sha256;inherited_consumed=2;next_ordinal=3;max_attempts=3;max_windows=0})}
    }else{
    $prior=Assert-B5Ledger $prefix $ExpectedB5CardSha256 $card $B5AttemptOrdinal
    if($null -ne $carryProof){$carryProof=Assert-B5CarryForward $card}
    if ($prefix.Length -eq 0) { $prefix=Add-B5ManifestRecord $manifestStream $prefix ([pscustomobject]@{event='HEADER';card_sha256=$ExpectedB5CardSha256;budget_id=$card.budget_id;max_attempts=$card.max_attempts;max_windows=$card.max_windows;carry_sha256=$(if($null -ne $carryProof){$carryProof.sha256}else{$null});prior_windows_ledger_sha256=$(if($null -ne $carryProof){$carryProof.prior_ledger_sha256}else{$null});prior_windows_result_sha256=$(if($null -ne $carryProof){$carryProof.prior_result_sha256}else{$null})}) }
    }
    $prefix=Add-B5ManifestRecord $manifestStream $prefix ([pscustomobject]@{event='CONSUME';ordinal=$B5AttemptOrdinal;epoch=$PhysicalRecoveryEpoch;
        session_id=$card.session_id;one_extra_sha256=$(if($null -ne $extensionProof){$extensionProof.sha256}else{$null});target_migration_sha256=$(if($null -ne $migrationProof){$migrationProof.sha256}else{$null});carry_sha256=$(if($null -ne $carryProof){$carryProof.sha256}else{$null});frontend_experiment=$FrontendExperiment;golden_sha256=$ExpectedFrontendGoldenSha256;reload_plan_sha256=$ExpectedReloadPlanSha256;physical_cold_confirmed=$true;cold_seconds=$PhysicalColdSeconds;queue_depth=$B5QueueDepth;urb_bytes=$B5UrbBytes;
        previous_offline_validation_sha256=$(if($prior){$prior.sha256}else{$null});utc=[DateTime]::UtcNow.ToString('o')})
    $consumed=$true
    $null=New-Item -ItemType Directory -Path $attemptPath
    $null=New-Item -ItemType Directory -Path $rawAttemptPath -Force
    Assert-B5Identity; Assert-B5Apk
    $power=Invoke-B5Adb @('shell','dumpsys','power')
    if ($power -cnotmatch '(?m)^\s*mWakefulness=Awake\r?$') { throw 'B5_SCREEN_NOT_AWAKE_USER_ACTION_REQUIRED' }
    $preflightToken=[guid]::NewGuid().ToString()
    $script:adbStage='B5_PERMISSION_ONLY'
    $null=Invoke-B5Adb @('shell','am','start','--activity-clear-top','--activity-single-top','-n',"$package/.Tbs5530PermissionPreflightActivity",'--es','preflight_token',$preflightToken)
    $ready=Wait-B5Permission $preflightToken
    Write-B5NewJson (Join-Path $attemptPath 'permission-ready.private.json') $ready
    Assert-B5LiveDay $card
    $launchToken=[guid]::NewGuid().ToString()
    $launch=@('shell','am','start','--activity-clear-top','--activity-single-top','-n',"$package/.Tbs5530AutonomousDiagnosticActivity",
        '--es','read_api','NATIVE_USBFS_STREAM','--es','frontend_experiment',$FrontendExperiment,'--es','launch_token',$launchToken,
        '--ez','control_plane_only','false','--ez','control_mirror','true','--ei','b3_queue_depth',[string]$B5QueueDepth,'--ei','b3_urb_bytes',[string]$B5UrbBytes,
        '--ei','b3_max_bytes',[string]$B5MaxBytes,'--ei','b3_duration_ms',[string]$B5DurationMillis,'--ez','b5_enabled','true',
        '--ez','b5_allow_k25_auto',($FrontendExperiment -ceq 'B5_K25_MANUAL_PLP0').ToString().ToLowerInvariant(),'--es','b5_layout',$layout,'--ei','b5_stale_prefix_bytes','2048','--ei','b5_monitor_period_ms','100','--ei','b5_monitor_jitter_ms','20','--ei','b5_rssi_samples_per_off','3')
    $script:adbStage='B5_LAUNCH'; $null=Invoke-B5Adb $launch
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while ($watch.ElapsedMilliseconds -lt 10000) {
        $cmd='if [ -f files/autonomous-v1-challenge.json ]; then cat files/autonomous-v1-challenge.json; fi'
        $text=Invoke-B5Adb @('shell','run-as',$package,'sh','-c',("'"+$cmd+"'"))
        if ($text) { $candidate=$text|ConvertFrom-Json; if ($candidate.launch_token -ceq $launchToken) { $challenge=$candidate; break } }
        Start-Sleep -Milliseconds 100
    }
    Assert-B5Challenge $challenge $launchToken (Get-B5Elapsed)
    Assert-B5Foreground; Assert-B5Identity
    Write-B5NewJson (Join-Path $attemptPath 'challenge.private.json') $challenge
    $directory='files/autonomous-v1-'+$challenge.session
    $encoded=[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $FirmwarePath)))
    $cmd='base64 -d > '+$directory+'/firmware.bin'
    $null=Invoke-B5Adb @('shell','run-as',$package,'sh','-c',("'"+$cmd+"'")) $encoded
    if ((Get-TBS5530Sha256Token (Invoke-B5Adb @('shell','run-as',$package,'sha256sum',($directory+'/firmware.bin')))) -cne $firmwareSha) { throw 'B5_STAGED_FIRMWARE_MISMATCH' }
    Assert-B5LiveDay $card
    $expiry=[long]$challenge.created_elapsed_ms+300000
    $grant=@{}; $challenge.PSObject.Properties|ForEach-Object {$grant[$_.Name]=$_.Value}
    $grant.profile='EXPERIMENTAL_DIAGNOSTIC'; $grant.bounded_user_authorization=$true; $grant.rf_in_confirmed=$true
    $grant.host_script_sha256=$ExpectedB5HelperSha256; $grant.expires_elapsed_ms=$expiry
    $grant.physical_recovery_epoch=$PhysicalRecoveryEpoch; $grant.physical_recovery_confirmed=$true
    $grant.b3_card_sha256=$ExpectedB5CardSha256; $grant.b3_session_id=$card.session_id; $grant.b3_attempt_ordinal=$B5AttemptOrdinal
    $grant.b3_max_attempts=$card.max_attempts; $grant.b3_physical_cold_seconds=$PhysicalColdSeconds
    $grant.b5_card_sha256=$ExpectedB5CardSha256; $grant.b5_budget_id=$card.budget_id; $grant.b5_session_id=$card.session_id
    $grant.b5_attempt_ordinal=$B5AttemptOrdinal; $grant.b5_max_attempts=$card.max_attempts; $grant.b5_physical_cold_seconds=$PhysicalColdSeconds
    Write-B5NewJson (Join-Path $attemptPath 'grant.private.json') $grant
    $script:adbStage='B5_GRANT'; $granted=$true
    $initial=Exchange-B5Heartbeat -Grant $grant
    if ($initial.expired) { throw 'B5_EXPIRED_BEFORE_GRANT' }
    $script:adbStage='B5_RUNNING'
    while ($watch.ElapsedMilliseconds -lt 300000) {
        Assert-B5LiveDay $card
        $beat=Exchange-B5Heartbeat
        if ($null -ne $beat.report -and $null -ne $beat.report.PSObject.Properties['terminal'] -and $beat.report.terminal -ceq $true) { $terminalReport=$beat.report; break }
        if ($beat.expired) { break }
        Assert-B5PermissionSnapshot $beat.permission $challenge.session $beat.elapsed_ms
        Start-Sleep -Milliseconds 100
    }
    if ($null -eq $terminalReport) { throw 'B5_TERMINAL_DEADLINE_NO_FORCE_CLOSE' }
    if ($terminalReport.launch_token -cne $launchToken -or $terminalReport.frontend_experiment -cne $FrontendExperiment -or
        $terminalReport.ts_variant -cne 'NATIVE_USBFS_STREAM' -or $terminalReport.control_plane_only -isnot [bool] -or $terminalReport.control_plane_only) { throw 'B5_REPORT_SCOPE_MISMATCH' }
    foreach ($pin in @{b5_enabled=$true;b5_rssi_sequence='WINDOWS_88_00_87_C0';b5_allow_k25_auto=($FrontendExperiment -ceq 'B5_K25_MANUAL_PLP0');b5_layout=$layout;b5_stale_prefix_bytes=2048;b5_monitor_period_ms=100;b5_monitor_jitter_ms=20;b5_rssi_samples_per_off=3}.GetEnumerator()) {
        if ($null -eq $terminalReport.PSObject.Properties[$pin.Key] -or $terminalReport.($pin.Key) -cne $pin.Value -or
            ($pin.Value -is [bool] -and $terminalReport.($pin.Key) -isnot [bool]) -or
            ((Test-B5Integer $pin.Value) -and -not (Test-B5Integer $terminalReport.($pin.Key)))) { throw 'B5_REPORT_LIMITS_MISMATCH' }
    }
    if (-not (Test-B5Drained $terminalReport)) { throw 'B5_DRAIN_UNCERTAIN_NO_FORCE_CLOSE' }
    Assert-B5PhaseReport $terminalReport
    Write-B5NewJson (Join-Path $attemptPath 'terminal-report.json') $terminalReport
    if ($terminalReport.b5_k25_auto_fallback_used -isnot [bool] -or
        ($terminalReport.b5_k25_auto_fallback_used -and ($FrontendExperiment -cne 'B5_K25_MANUAL_PLP0' -or
            $terminalReport.b5_manual_demod_lock -isnot [bool] -or $terminalReport.b5_manual_demod_lock -or
            $terminalReport.b5_actual_frontend_experiment -cne 'B5_K25_AUTO')) -or
        (-not $terminalReport.b5_k25_auto_fallback_used -and $terminalReport.b5_actual_frontend_experiment -cne $FrontendExperiment)) { throw 'B5_FALLBACK_REPORT_SCOPE_INVALID' }
    $script:adbStage='B5_EXPORT_AFTER_DRAIN'
    $exports=Export-B5Evidence $challenge.session $terminalReport $attemptPath
    $exportManifestSha=(Get-FileHash -LiteralPath (Join-Path $attemptPath 'export-manifest.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    $raw=@($exports|Where-Object {$_.path -clike '*/received-ON1.ts' -and $_.present}); if ($raw.Count -eq 1) { $captureRawSha=$raw[0].sha256 }
    $classification=Get-B5CaptureClassification $terminalReport $exports
    Write-B5NewJson (Join-Path $attemptPath 'host-classification.json') $classification
    $classification|ConvertTo-Json -Compress
    $failure=$terminalReport.result
} catch {
    if (-not $granted -and $_.Exception.Message -cmatch '^B5_[A-Z0-9_]+$') { $hostError=$_.Exception.Message }
    throw
} finally {
    if ($granted) { try { $null=Exchange-B5Heartbeat -Stop } catch { Write-Warning 'B5: STOP chưa xác nhận; watchdog heartbeat thật 2 giây, không reconnect hoặc ép đóng syscall.' } }
    try {
        if ($consumed) {
            $closed=Test-B5SessionClosed $terminalReport; $drained=Test-B5Drained $terminalReport
            if ($failure -ceq 'HOST_ABORTED_NO_RETRY') { $closed=$true }
            if (-not $granted) {
                $e=Write-B5PreGrantEvidence $attemptPath $ExpectedB5CardSha256 $card.session_id $B5AttemptOrdinal $PhysicalRecoveryEpoch $hostError $script:adbStage
                $preGrantEvidenceSha=$e.evidence_sha256; $exportManifestSha=$e.export_sha256; $failure='PRE_GRANT_HOST_REFUSAL'; $closed=$false; $drained=$false
            }
            $receipt=[pscustomobject]@{event='RECEIPT';ordinal=$B5AttemptOrdinal;result=$failure;drained=$drained;session_closed=$closed;exit_code=$exitCode;
                single_run_pass=$false;capture_complete=($null -ne $classification -and $classification.capture_complete);raw_sha256=$captureRawSha;
                export_manifest_sha256=$exportManifestSha;queue_depth=$B5QueueDepth;urb_bytes=$B5UrbBytes;grant_may_have_been_published=[bool]$granted;
                pre_grant_evidence_sha256=$preGrantEvidenceSha;pre_io_refusal=(-not $granted -or ($null -ne $terminalReport -and $null -ne $terminalReport.PSObject.Properties['pre_native_refusal'] -and $terminalReport.pre_native_refusal -is [bool] -and $terminalReport.pre_native_refusal));
                classification=$(if (-not $granted) {'PRE_GRANT_HOST_REFUSAL'} else {'PENDING_ANALYSIS'});utc=[DateTime]::UtcNow.ToString('o')}
            $prefix=Add-B5ManifestRecord $manifestStream $prefix $receipt
        }
    } finally { $manifestStream.Dispose() }
}
exit $exitCode
