# SPDX-License-Identifier: GPL-2.0-or-later
# One bounded debug session; no package changes, reconnect or USB ownership changes.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [Parameter(Mandatory)][string]$ExpectedModel,
    [Parameter(Mandatory)][ValidateRange(21, 99)][int]$ExpectedApi,
    [Parameter(Mandatory)][ValidateRange(0, 2147483647)][int]$ExpectedBootCount,
    [ValidateSet('AUTO_VERIFIED', 'EXPERIMENTAL_DIAGNOSTIC')][string]$Profile = 'AUTO_VERIFIED',
    [ValidateSet('SYNC_NATIVE_ERRNO', 'QUEUED_USB_REQUEST_8X4096', 'NATIVE_USBFS_URB_8X4096', 'NATIVE_USBFS_URB_8X8192')][string]$ReadApi = 'SYNC_NATIVE_ERRNO',
    [ValidateRange(10, 300)][int]$DurationSeconds = 300,
    [string]$FirmwarePath,
    [string]$PhysicalRecoveryEpoch,
    [switch]$PhysicalRecoveryConfirmed,
    [string]$AdbPath = 'D:\Android\Sdk\platform-tools\adb.exe',
    [switch]$GrantBoundedSession,
    [switch]$ProofOfGuardPassed,
    [switch]$AutoUsbPermission,
    [switch]$ControlMirror,
    [switch]$ExplicitSetInterfaceAlt0,
    [switch]$SonyTsOutputReadback,
    [ValidateSet('BASELINE_AUTO', 'MANUAL_PLP0_K36', 'B2_RELOAD_AFTER_SET_CONFIG')][string]$FrontendExperiment = 'BASELINE_AUTO',
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedReloadPlanSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedReloadStoreSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedReloadAnchorSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedHelperSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedFrontendGoldenSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedAppApkSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedAttachmentHistorySha256,
    [switch]$ControlPlaneOnly,
    [string]$AndroidCliPath,
    [string]$OutputDirectory = 'D:\TSDuck\logs'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $GrantBoundedSession -or -not $ProofOfGuardPassed) { throw 'BLOCKED: cần quyền phiên bounded và kết quả proof-of-guard trước khi chạy thật.' }
$b2Reload = $FrontendExperiment -ceq 'B2_RELOAD_AFTER_SET_CONFIG'
if ($b2Reload) {
    if ($ExpectedModel -cne 'SM-X926B' -or $ExpectedApi -ne 36 -or $Profile -cne 'EXPERIMENTAL_DIAGNOSTIC' -or
        $ReadApi -cne 'NATIVE_USBFS_URB_8X4096' -or -not $ControlMirror -or $ExplicitSetInterfaceAlt0 -or $SonyTsOutputReadback -or
        $ControlPlaneOnly -or -not $ExpectedFrontendGoldenSha256 -or -not $ExpectedAppApkSha256 -or -not $ExpectedAttachmentHistorySha256 -or
        -not $ExpectedReloadPlanSha256 -or -not $ExpectedReloadStoreSha256 -or -not $ExpectedReloadAnchorSha256 -or
        -not $ExpectedHelperSha256 -or -not $FirmwarePath -or -not $PhysicalRecoveryEpoch -or -not $PhysicalRecoveryConfirmed) {
        throw 'BLOCKED: B2 cần exact Samsung/API36, native4096/MANUAL0/mirror, bootstrap cùng phiên, epoch và đủ hash; không ghép phép thử.'
    }
    if ((Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ExpectedHelperSha256) {
        throw 'BLOCKED: B2 helper khác hash đã duyệt; chưa gọi ADB.'
    }
} elseif ($ExpectedReloadPlanSha256 -or $ExpectedReloadStoreSha256 -or $ExpectedReloadAnchorSha256 -or $ExpectedHelperSha256) {
    throw 'BLOCKED: reload pins chỉ thuộc B2.'
}
if ($FrontendExperiment -ceq 'MANUAL_PLP0_K36' -and ($Profile -cne 'EXPERIMENTAL_DIAGNOSTIC' -or
    $ReadApi -cne 'NATIVE_USBFS_URB_8X4096' -or -not $ControlMirror -or $ExplicitSetInterfaceAlt0 -or
    $SonyTsOutputReadback -or $ControlPlaneOnly -or -not $ExpectedFrontendGoldenSha256 -or -not $ExpectedAppApkSha256 -or
    -not $ExpectedAttachmentHistorySha256)) {
    throw 'BLOCKED: MANUAL_PLP0_K36 cần native4096/experimental/mirror/hash đã kiểm, không ghép phép thử khác.'
}
if ($SonyTsOutputReadback -and ($Profile -cne 'EXPERIMENTAL_DIAGNOSTIC' -or $ReadApi -cne 'NATIVE_USBFS_URB_8X4096' -or
    $ExplicitSetInterfaceAlt0 -or $ControlPlaneOnly -or -not $ControlMirror)) {
    throw 'BLOCKED: SONY_TS_OUTPUT_READBACK cần native4096/experimental/mirror, không ghép alt0 hoặc control-plane-only.'
}
if ($ControlPlaneOnly -and ($FirmwarePath -or $PhysicalRecoveryEpoch -or $PhysicalRecoveryConfirmed -or $AutoUsbPermission -or $ControlMirror)) {
    throw 'BLOCKED: control-plane-only không nhận firmware, physical epoch, USB permission hoặc mirror.'
}
if ($PhysicalRecoveryEpoch) {
    if (-not $PhysicalRecoveryConfirmed -or $PhysicalRecoveryEpoch -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$' -or
        $PhysicalRecoveryEpoch -ceq '00000000-0000-0000-0000-000000000000') {
        throw 'BLOCKED: physical recovery epoch phải là GUID cố định do operator cấp cho sự kiện đã được người dùng xác nhận.'
    }
} elseif ($PhysicalRecoveryConfirmed) { throw 'BLOCKED: xác nhận physical recovery thiếu epoch; không tự tạo GUID.' }
if ($ExplicitSetInterfaceAlt0 -and ($Profile -cne 'EXPERIMENTAL_DIAGNOSTIC' -or $ReadApi -cne 'NATIVE_USBFS_URB_8X4096')) {
    throw 'BLOCKED: EXPLICIT_SET_INTERFACE_ALT0 cần EXPERIMENTAL_DIAGNOSTIC và native baseline 8x4096; không ghép 8192.'
}
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw 'Không tìm thấy ADB đã chọn.' }
if ($ReadApi -ne 'SYNC_NATIVE_ERRNO' -and $Profile -ne 'EXPERIMENTAL_DIAGNOSTIC') { throw 'BLOCKED: diagnostic receive chỉ thuộc EXPERIMENTAL_DIAGNOSTIC.' }
$package = 'info.martinmarinov.dvbdriver'
$expectedHostProfile = if ($ExpectedModel -ceq 'GM1900' -and $ExpectedApi -eq 30) { 'oneplus-gm1900-api30' }
    elseif ($ExpectedModel -ceq 'SM-X926B' -and $ExpectedApi -eq 36) { 'samsung-sm-x926b-api36' }
    else { throw 'BLOCKED: model/API chưa nằm trong danh sách host đã xác minh.' }
$firmwareSha = 'b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc'
$watch = [Diagnostics.Stopwatch]::StartNew()
. (Join-Path $PSScriptRoot 'Tbs5530-UsbPermissionUi.ps1')
$uiJob = $null; $uiRequest = $null; $uiPhase = ''; $uiObserved = 0L
$uiHandled = [Collections.Generic.HashSet[string]]::new()
if ($AutoUsbPermission) {
    if (-not $AndroidCliPath) { $AndroidCliPath = (Get-Command android.exe -ErrorAction Stop).Source }
    if (-not (Test-Path -LiteralPath $AndroidCliPath -PathType Leaf)) { throw 'USER_ACTION_REQUIRED: Android CLI không khả dụng.' }
    if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) { throw 'Thiếu thư mục log ngoài Git.' }
}
$script:adbStage = 'INITIAL_IDENTITY'
function Get-TBS5530AdbFailure {
    param(
        [ValidateSet('START_FAILED', 'TIMEOUT', 'NONZERO_EXIT')][string]$Kind,
        [ValidatePattern('^[A-Z][A-Z0-9_]{0,63}$')][string]$Stage,
        [ValidatePattern('^[A-Z][A-Z0-9_]{0,63}$')][string]$Operation,
        [ValidateSet(1500, 10000)][int]$TimeoutMillis = 1500
    )
    return "FAIL-STOP: ADB $Kind; stage=$Stage; operation=$Operation; timeout_ms=$TimeoutMillis; không reconnect/fallback."
}
function Test-TBS5530DrainedTerminal([object]$Report) {
    if ($null -eq $Report) { return $false }
    foreach ($field in @('cleanup', 'state', 'result')) { if ($null -eq $Report.PSObject.Properties[$field]) { return $false } }
    if ($Report.cleanup -cne 'DRAIN_CONFIRMED' -or $Report.result -isnot [string] -or [string]::IsNullOrWhiteSpace($Report.result)) { return $false }
    $typed = $null -ne $Report.PSObject.Properties['terminal'] -or $null -ne $Report.PSObject.Properties['cleanup_path']
    if ($typed) {
        if ($null -eq $Report.PSObject.Properties['terminal'] -or $Report.terminal -isnot [bool] -or $Report.terminal -cne $true -or
            $null -eq $Report.PSObject.Properties['cleanup_path'] -or $Report.cleanup_path -cnotin @('NO_RESOURCES_ACQUIRED','RUNTIME_DRAIN_CONFIRMED') -or
            $Report.state -cnotin @('WAIT_DEVICE','NEED_ANDROID_USB_PERMISSION','FX2_DEFAULT','FIRMWARE_BOOTSTRAP','WAIT_REENUMERATION',
                'OPERATIONAL','FRONTEND_INIT','LOCK_CONFIRMED','TS_CAPTURE','REPORT','ERROR_UNCERTAIN')) { return $false }
        if ($Report.cleanup_path -ceq 'NO_RESOURCES_ACQUIRED' -and
            ($null -eq $Report.PSObject.Properties['firmware'] -or $null -ne $Report.firmware -or
             $null -eq $Report.PSObject.Properties['native_urb'] -or $null -ne $Report.native_urb)) { return $false }
    } elseif ($Report.state -cnotin @('REPORT', 'ERROR_UNCERTAIN')) { return $false }
    if ($null -ne $Report.PSObject.Properties['native_urb'] -and $null -ne $Report.native_urb) {
        $urb = $Report.native_urb
        if ($null -eq $urb.PSObject.Properties['outstanding'] -or $null -eq $urb.PSObject.Properties['quarantined'] -or
            ($urb.outstanding -isnot [long] -and $urb.outstanding -isnot [int]) -or
            $urb.outstanding -ne 0 -or $urb.quarantined -isnot [bool] -or $urb.quarantined -cne $false) { return $false }
    }
    return $true
}
function Get-TBS5530AdbDeadline([string]$Operation, [string[]]$DeviceArguments, [object]$DrainedReport, [string]$Session) {
    if ($null -eq $DrainedReport) { return 1500 }
    if (-not (Test-TBS5530DrainedTerminal $DrainedReport) -or $Session -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$') {
        throw 'FAIL-STOP: export chỉ được đọc sau terminal report và drain đã xác minh.'
    }
    $remote = 'files/autonomous-v1-' + $Session + '/control-ep0.jsonl'
    if ($Operation -cin @('NATIVE_A0_READ','NATIVE_A0_HASH')) {
        if ($DeviceArguments.Count -ne 5 -or $DeviceArguments[4] -cnotmatch
            ('^files/autonomous-v1-' + [regex]::Escape($Session) + '/(?:reload-a0-native\.jsonl|reload-native-boundaries\.json)$')) {
            throw 'FAIL-STOP: native export không thuộc exact allowlist của session đã drain.'
        }
        $remote = $DeviceArguments[4]
    } elseif ($Operation -cin @('HANDOFF_EVENTS_READ','HANDOFF_EVENTS_HASH')) {
        $remote = 'files/autonomous-v1-' + $Session + '/handoff-events.jsonl'
    }
    $expected = if ($Operation -cin @('CONTROL_TRACE_READ','NATIVE_A0_READ','HANDOFF_EVENTS_READ')) { @('exec-out', 'run-as', 'info.martinmarinov.dvbdriver', 'cat', $remote) }
        elseif ($Operation -cin @('CONTROL_TRACE_HASH','NATIVE_A0_HASH','HANDOFF_EVENTS_HASH')) { @('shell', 'run-as', 'info.martinmarinov.dvbdriver', 'sha256sum', $remote) }
        else { throw 'FAIL-STOP: deadline export không áp dụng cho thao tác runtime.' }
    if ($DeviceArguments.Count -ne $expected.Count) { throw 'FAIL-STOP: export không đúng read-only allowlist.' }
    for ($i = 0; $i -lt $expected.Count; $i++) {
        if ($DeviceArguments[$i] -cne $expected[$i]) { throw 'FAIL-STOP: export không đúng read-only allowlist.' }
    }
    return 10000
}
function ConvertTo-TBS5530WindowsArgument([string]$Value) {
    $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}
function Invoke-PinnedAdb([string[]]$DeviceArguments, [string]$InputText = '', [bool]$PreserveOutput = $false,
    [ValidatePattern('^[A-Z][A-Z0-9_]{0,63}$')][string]$Operation = 'UNCLASSIFIED', [object]$DrainedReport = $null) {
    $sessionId = if ($null -eq $DrainedReport) { '' } else { $challenge.session }
    $timeoutMillis = Get-TBS5530AdbDeadline $Operation $DeviceArguments $DrainedReport $sessionId
    if ($null -ne $DrainedReport -and $InputText) { throw 'FAIL-STOP: export read-only không nhận payload.' }
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $AdbPath; $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true
    $psi.RedirectStandardInput = $true; $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
    $arguments = @('-s', $Serial) + $DeviceArguments
    if ($psi.PSObject.Properties.Name -contains 'ArgumentList') {
        foreach ($argument in $arguments) { $psi.ArgumentList.Add($argument) }
    } else {
        $psi.Arguments = ($arguments | ForEach-Object { ConvertTo-TBS5530WindowsArgument $_ }) -join ' '
    }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $psi
    try {
        try { if (-not $process.Start()) { throw 'Start returned false' } }
        catch { throw (Get-TBS5530AdbFailure 'START_FAILED' $script:adbStage $Operation $timeoutMillis) }
        $output = $process.StandardOutput.ReadToEndAsync(); $errorText = $process.StandardError.ReadToEndAsync()
        if ($InputText) { $process.StandardInput.Write($InputText) }; $process.StandardInput.Close()
        $exited = if ($timeoutMillis -eq 1500) { $process.WaitForExit(1500) } else { $process.WaitForExit(10000) }
        if (-not $exited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            throw (Get-TBS5530AdbFailure 'TIMEOUT' $script:adbStage $Operation $timeoutMillis)
        }
        if ($process.ExitCode -ne 0) { throw (Get-TBS5530AdbFailure 'NONZERO_EXIT' $script:adbStage $Operation $timeoutMillis) }
        $text = $output.GetAwaiter().GetResult()
        if ($PreserveOutput) { return $text }
        return $text.Trim()
    } finally { $process.Dispose() }
}
function Get-TBS5530Sha256Token([string]$Text) {
    if ($Text -cmatch '^([a-f0-9]{64})(?:\s|$)') { return $Matches[1] }
    throw 'Invalid Android SHA-256 response.'
}
function Export-TBS5530HandoffEvents([string]$Session, [object]$Report, [string]$Destination) {
    if (-not (Test-TBS5530DrainedTerminal $Report) -or $Session -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$') {
        throw 'FAIL-STOP: handoff journal export requires a verified drained session.'
    }
    $remote = 'files/autonomous-v1-' + $Session + '/handoff-events.jsonl'
    $command = 'if [ -f ' + $remote + ' ]; then printf PRESENT; fi'
    $present = Invoke-PinnedAdb @('shell', 'run-as', 'info.martinmarinov.dvbdriver', 'sh', '-c', "'$command'") -Operation HANDOFF_EVENTS_EXISTS
    if ($present -ceq '') { return }
    if ($present -cne 'PRESENT') { throw 'FAIL-STOP: unexpected handoff journal presence response.' }
    $local = Join-Path $Destination ('b2-' + $Session + '-handoff-events.jsonl')
    if (Test-Path -LiteralPath $local) { throw 'Không ghi đè evidence đã có.' }
    $text = Invoke-PinnedAdb @('exec-out', 'run-as', 'info.martinmarinov.dvbdriver', 'cat', $remote) '' $true -Operation HANDOFF_EVENTS_READ -DrainedReport $Report
    [IO.File]::WriteAllText($local, $text, [Text.UTF8Encoding]::new($false))
    $sourceHash = Get-TBS5530Sha256Token (Invoke-PinnedAdb @('shell', 'run-as', 'info.martinmarinov.dvbdriver', 'sha256sum', $remote) -Operation HANDOFF_EVENTS_HASH -DrainedReport $Report)
    if ($sourceHash -cne (Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash.ToLowerInvariant()) {
        throw 'FAIL-STOP: handoff journal Android/host hash mismatch; evidence retained.'
    }
    return $local
}
function Get-TBS5530AutonomousExitCode([object]$Report) {
    if ($null -eq $Report) { return 1 }
    foreach ($field in @('result', 'cleanup', 'state', 'structure_confirmed', 'ts_smoke_pass')) {
        if ($null -eq $Report.PSObject.Properties[$field]) { return 1 }
    }
    if ($Report.result -ceq 'STRUCTURE_CONFIRMED' -and $Report.cleanup -ceq 'DRAIN_CONFIRMED' -and
        $Report.state -ceq 'REPORT' -and $Report.structure_confirmed -eq $true -and $Report.ts_smoke_pass -eq $true) { return 0 }
    return 1
}
function Test-TBS5530LaunchChallenge([object]$Value, [string]$Launch, [bool]$ControlOnly, [long]$Now) {
    if ($null -eq $Value -or $Launch -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$' -or
        $Launch -ceq '00000000-0000-0000-0000-000000000000') { return $false }
    foreach ($field in @('launch_token','control_plane_only','live_ready','created_elapsed_ms','policy')) {
        if ($null -eq $Value.PSObject.Properties[$field]) { return $false }
    }
    return $Value.policy -ceq 'TBS5530-AUTONOMOUS/v1' -and $Value.launch_token -ceq $Launch -and
        $Value.control_plane_only -is [bool] -and $Value.control_plane_only -eq $ControlOnly -and
        $Value.live_ready -is [bool] -and $Value.live_ready -eq (-not $ControlOnly) -and
        ($Value.created_elapsed_ms -is [long] -or $Value.created_elapsed_ms -is [int]) -and
        $Now -ge $Value.created_elapsed_ms -and $Now - $Value.created_elapsed_ms -lt 10000
}
function Get-TBS5530ControlPlaneExitCode([object]$Report, [string]$Launch) {
    if (-not (Test-TBS5530DrainedTerminal $Report)) { return 1 }
    foreach ($field in @('launch_token','control_plane_only','grant_accepted','cleanup_path','interface_selection','control_mirror_records','ts_bytes','ts_smoke_pass')) {
        if ($null -eq $Report.PSObject.Properties[$field]) { return 1 }
    }
    if ($Report.launch_token -ceq $Launch -and $Report.result -ceq 'CONTROL_PLANE_COMPLETE' -and
        $Report.control_plane_only -is [bool] -and $Report.control_plane_only -ceq $true -and
        $Report.grant_accepted -is [bool] -and $Report.grant_accepted -ceq $true -and
        $Report.cleanup_path -ceq 'NO_RESOURCES_ACQUIRED' -and $null -eq $Report.interface_selection -and
        ($Report.control_mirror_records -is [int] -or $Report.control_mirror_records -is [long]) -and $Report.control_mirror_records -eq 0 -and
        ($Report.ts_bytes -is [int] -or $Report.ts_bytes -is [long]) -and $Report.ts_bytes -eq 0 -and
        $Report.ts_smoke_pass -is [bool] -and $Report.ts_smoke_pass -ceq $false) { return 0 }
    return 1
}
function Assert-Identity {
    if ((Invoke-PinnedAdb @('get-state') -Operation DEVICE_STATE) -cne 'device' -or
        (Invoke-PinnedAdb @('shell', 'getprop', 'ro.product.model') -Operation MODEL_READ) -cne $ExpectedModel -or
        [int](Invoke-PinnedAdb @('shell', 'getprop', 'ro.build.version.sdk') -Operation API_READ) -ne $ExpectedApi -or
        [int](Invoke-PinnedAdb @('shell', 'settings', 'get', 'global', 'boot_count') -Operation BOOT_READ) -ne $ExpectedBootCount) {
        throw 'FAIL-STOP: serial/model/API/boot thay đổi; không cấp lại quyền.'
    }
}
function Get-Elapsed {
    $text = Invoke-PinnedAdb @('shell', 'cat', '/proc/uptime') -Operation UPTIME_READ
    $seconds = [double]::Parse(($text -split ' ')[0], [Globalization.CultureInfo]::InvariantCulture)
    return [long][Math]::Floor($seconds * 1000)
}
function Write-PrivateJson([string]$Path, [object]$Value) {
    if ($Path -notmatch '^files/autonomous-v1-[a-f0-9-]{36}/(?:grant|heartbeat|permission-ui)\.json$') { throw 'Invalid private session path.' }
    # .hosttmp must not collide with Android AtomicFile .new/.bak recovery names.
    $command = 'cat > ' + $Path + '.hosttmp && mv ' + $Path + '.hosttmp ' + $Path
    Invoke-PinnedAdb @('shell', 'run-as', $package, 'sh', '-c', "'$command'") ($Value | ConvertTo-Json -Compress -Depth 8) -Operation PRIVATE_JSON_WRITE | Out-Null
}
function New-TBS5530HeartbeatCommand([string]$Session, [long]$Created, [long]$Expires, [switch]$Stop, [switch]$PublishGrant) {
    if ($Stop -and $PublishGrant) { throw 'FAIL-STOP: không publish grant trong STOP.' }
    if ($Session -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$' -or
        $Created -lt 0 -or $Created -gt [long]::MaxValue - 300000 -or $Expires -le $Created -or $Expires -gt $Created + 300000) {
        throw 'FAIL-STOP: heartbeat scope/deadline không hợp lệ.'
    }
    # The adb shell UID reads the clock; run-as only publishes app-private files in the same call.
    $template = @'
set -eu
[ "$#" -eq 1 ] || exit 64
uptime=$1
seconds=${uptime%%.*}
fraction=${uptime#*.}
case $seconds in ""|*[!0-9]*|0[0-9]*) exit 64;; esac
case $fraction in ""|*[!0-9]*) exit 64;; esac
[ "$seconds.$fraction" = "$uptime" ] || exit 64
[ "${#seconds}" -le 10 ] || exit 64
fraction=${fraction}000
fraction=${fraction%"${fraction#???}"}
now=${seconds}${fraction}
while [ "${now#0}" != "$now" ]; do now=${now#0}; done
[ -n "$now" ] || now=0
# Compare decimal strings without relying on Android shell integer width.
ge() {
    a=$1 b=$2
    [ "${#a}" -ge "${#b}" ] || return 1
    [ "${#a}" -eq "${#b}" ] || return 0
    while [ -n "$a" ]; do
        ac=${a%"${a#?}"} bc=${b%"${b#?}"}
        [ "$ac" -ge "$bc" ] || return 1
        [ "$ac" -eq "$bc" ] || return 0
        a=${a#?} b=${b#?}
    done
    return 0
}
ge "$now" @CREATED@ || exit 65
expired=false
if ge "$now" @EXPIRES@; then expired=true; fi
if [ "@STOP@" = true ] || [ "$expired" = false ]; then
    if [ "@GRANT@" = true ]; then
        [ ! -e files/autonomous-v1-@SESSION@/grant.json ] || exit 66
        (set -C; cat > files/autonomous-v1-@SESSION@/grant.json.hosttmp) || exit 66
    fi
    printf "{\"session\":\"@SESSION@\",\"elapsed_ms\":%s,\"stop\":@STOP@}" "$now" > files/autonomous-v1-@SESSION@/heartbeat.json.hosttmp
    mv files/autonomous-v1-@SESSION@/heartbeat.json.hosttmp files/autonomous-v1-@SESSION@/heartbeat.json
    if [ "@GRANT@" = true ]; then mv files/autonomous-v1-@SESSION@/grant.json.hosttmp files/autonomous-v1-@SESSION@/grant.json; fi
fi
printf "{\"elapsed_ms\":%s,\"expired\":%s,\"report\":" "$now" "$expired"
if [ -f files/autonomous-v1-@SESSION@/report.json ]; then cat files/autonomous-v1-@SESSION@/report.json; else printf null; fi
printf ",\"permission\":"
if [ -f files/autonomous-v1-@SESSION@/permission.json ]; then cat files/autonomous-v1-@SESSION@/permission.json; else printf null; fi
printf ",\"control_plane\":"
if [ -f files/autonomous-v1-@SESSION@/control-plane.json ]; then cat files/autonomous-v1-@SESSION@/control-plane.json; else printf null; fi
printf "}"
'@
    $inner = $template.Replace('@SESSION@', $Session).Replace('@CREATED@', $Created.ToString([Globalization.CultureInfo]::InvariantCulture)).
        Replace('@EXPIRES@', $Expires.ToString([Globalization.CultureInfo]::InvariantCulture)).Replace('@STOP@', $Stop.IsPresent.ToString().ToLowerInvariant()).
        Replace('@GRANT@', $PublishGrant.IsPresent.ToString().ToLowerInvariant()).Replace("`r`n", "`n")
    $quote = [string][char]39
    $splice = $quote + [char]34 + $quote + [char]34 + $quote
    $quotedInner = $quote + $inner.Replace($quote, $splice) + $quote
    $outer = @'
set -eu
IFS=" " read -r uptime idle < /proc/uptime
exec run-as info.martinmarinov.dvbdriver sh -c @INNER@ heartbeat "$uptime"
'@
    return $outer.Replace('@INNER@', $quotedInner).Replace("`r`n", "`n")
}
function Read-TBS5530HeartbeatReply([string]$Text, [long]$RoundTripMillis, [long]$Created, [long]$Expires,
    [ValidatePattern('^[A-Z][A-Z0-9_]{0,63}$')][string]$Stage = 'HEARTBEAT') {
    if ($RoundTripMillis -lt 0 -or $RoundTripMillis -ge 1500) {
        throw "FAIL-STOP: heartbeat reply vượt call budget 1500ms; stage=$Stage; elapsed_ms=$RoundTripMillis."
    }
    try { $reply = $Text | ConvertFrom-Json } catch { throw 'FAIL-STOP: heartbeat reply JSON không hợp lệ.' }
    foreach ($field in @('elapsed_ms','expired','report','permission')) {
        if ($null -eq $reply -or $null -eq $reply.PSObject.Properties[$field]) { throw 'FAIL-STOP: heartbeat reply thiếu trường bắt buộc.' }
    }
    if (($reply.elapsed_ms -isnot [long] -and $reply.elapsed_ms -isnot [int]) -or $reply.elapsed_ms -lt $Created -or
        $reply.expired -isnot [bool] -or $reply.expired -ne ($reply.elapsed_ms -ge $Expires)) {
        throw 'FAIL-STOP: heartbeat reply clock/expiry không hợp lệ.'
    }
    return $reply
}
function Exchange-Heartbeat([switch]$Stop, [object]$Grant = $null) {
    $command = New-TBS5530HeartbeatCommand $challenge.session ([long]$challenge.created_elapsed_ms) $expiry -Stop:$Stop -PublishGrant:($null -ne $Grant)
    $grantText = if ($null -eq $Grant) { '' } else { $Grant | ConvertTo-Json -Compress -Depth 8 }
    if ($grantText.Length -gt 16384) { throw 'FAIL-STOP: grant vượt giới hạn.' }
    $call = [Diagnostics.Stopwatch]::StartNew()
    $text = Invoke-PinnedAdb @('shell', $command) $grantText -Operation HEARTBEAT_EXCHANGE
    return Read-TBS5530HeartbeatReply $text $call.ElapsedMilliseconds ([long]$challenge.created_elapsed_ms) $expiry -Stage $script:adbStage
}
function Update-UsbPermission([object]$pending, [long]$now) {
    if (-not $AutoUsbPermission) { return }
    if ($null -ne $uiJob) {
        if ($uiJob.Started.Elapsed.TotalSeconds -ge 12) { throw 'USER_ACTION_REQUIRED: UI helper hết thời gian; không bấm hộp thoại không rõ.' }
        if (-not $uiJob.Process.HasExited) { return }
        $job = $uiJob; $script:uiJob = $null
        try {
            if ($job.Process.ExitCode -ne 0) { throw 'USER_ACTION_REQUIRED: UI helper không xác nhận thành công.' }
            if ($uiPhase -ceq 'TAP') {
                $receipt = @{}
                foreach ($key in @('version','session','request_token','attachment_generation','device_identity_sha256','vid','pid')) { $receipt[$key] = $uiRequest.$key }
                $receipt.action = 'GRANT'; $receipt.observed_elapsed_ms = $uiObserved; $receipt.clicked_elapsed_ms = $now
                Write-PrivateJson ($directory + '/permission-ui.json') $receipt
                $uiHandled.Add($uiRequest.request_token) | Out-Null
                Write-Host 'USB permission: đã bấm đúng hộp thoại; ứng dụng phải xác minh quyền và attachment mới.'
                return
            }
            if (-not (Test-TBS5530PendingPermission $pending $challenge.session $now) -or
                $pending.request_token -cne $uiRequest.request_token -or
                $pending.attachment_generation -ne $uiRequest.attachment_generation -or
                $pending.device_identity_sha256 -cne $uiRequest.device_identity_sha256) { return }
            $layoutPath = Join-Path $OutputDirectory ('permission-' + $uiRequest.request_token + '.json')
            if (-not (Test-Path -LiteralPath $layoutPath) -or (Get-Item $layoutPath).Length -gt 1048576) { throw 'USER_ACTION_REQUIRED: hierarchy thiếu hoặc vượt giới hạn.' }
            $windows = Get-Content -LiteralPath $layoutPath -Raw -Encoding utf8 | ConvertFrom-Json
            $foreground = Invoke-PinnedAdb @('shell', 'dumpsys', 'activity', 'activities') -Operation UI_FOREGROUND_READ
            $resumed = ($foreground -split "`n" | Where-Object { $_ -match 'mResumedActivity:' }) -join "`n"
            $selector = Get-TBS5530UsbPermissionButton $windows $resumed
            if (-not $selector -or -not (Test-TBS5530SingleUsbInventory (Invoke-PinnedAdb @('shell', 'dumpsys', 'usb') -Operation USB_INVENTORY_READ))) {
                $uiHandled.Add($uiRequest.request_token) | Out-Null
                Write-Warning 'USER_ACTION_REQUIRED: hierarchy không xác nhận đúng USB permission/TBS5530; không bấm tự động hoặc chọn Always. Giữ heartbeat trong TTL để xử lý UI đã xác minh.'
                return
            }
            $uiBeat = Exchange-Heartbeat
            if ($uiBeat.expired) { throw 'FAIL-STOP: session đã hết hạn trước thao tác UI.' }
            $script:uiPhase = 'TAP'
            $script:uiJob = Start-TBS5530PermissionProcess $AndroidCliPath @('--sdk=D:\Android\Sdk', 'input', "--device=$Serial", 'tap', '--match=single', '--wait=0', $selector)
        } finally { $job.Process.Dispose() }
        return
    }
    if (-not (Test-TBS5530PendingPermission $pending $challenge.session $now) -or $uiHandled.Contains($pending.request_token)) { return }
    $layoutPath = Join-Path $OutputDirectory ('permission-' + $pending.request_token + '.json')
    if (Test-Path -LiteralPath $layoutPath) { throw 'USER_ACTION_REQUIRED: không ghi đè hierarchy của attempt cũ.' }
    $script:uiRequest = $pending; $script:uiObserved = $now; $script:uiPhase = 'LAYOUT'
    $script:uiJob = Start-TBS5530PermissionProcess $AndroidCliPath @('--sdk=D:\Android\Sdk', 'layout', "--device=$Serial", '--flat', '--no-idle', "--output=$layoutPath")
}
Assert-Identity
if ($FirmwarePath) {
    if (-not (Test-Path -LiteralPath $FirmwarePath -PathType Leaf) -or
        (Get-Item -LiteralPath $FirmwarePath).Length -ne 8192 -or
        (Get-FileHash -LiteralPath $FirmwarePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $firmwareSha) {
        throw 'BLOCKED: firmware private input không đúng size/SHA.'
    }
}
if ($AutoUsbPermission) {
    $script:adbStage = 'PERMISSION_PREFLIGHT'
    $preflightToken = [guid]::NewGuid().ToString()
    Invoke-PinnedAdb @('shell', 'am', 'start', '--activity-clear-top', '--activity-single-top', '-n', "$package/.Tbs5530PermissionPreflightActivity", '--es', 'preflight_token', $preflightToken) -Operation PREFLIGHT_LAUNCH | Out-Null
    $preflightWatch = [Diagnostics.Stopwatch]::StartNew()
    $permissionReady = $false; $preflightUiTried = $false
    while ($preflightWatch.Elapsed.TotalSeconds -lt 60) {
        $command = 'if [ -f files/permission-preflight.json ]; then cat files/permission-preflight.json; fi'
        $text = Invoke-PinnedAdb @('shell', 'run-as', $package, 'sh', '-c', "'$command'") -Operation PREFLIGHT_STATUS_READ
        if ($text) {
            $preflight = $text | ConvertFrom-Json
            if ($preflight.preflight_token -ceq $preflightToken -and $preflight.mode -ceq 'PERMISSION_ONLY') {
                if ($preflight.state -ceq 'GRANTED' -and $preflight.vid -eq 29516 -and $preflight.pid -eq 21808 -and
                    (Get-Elapsed) - $preflight.elapsed_ms -ge 0 -and (Get-Elapsed) - $preflight.elapsed_ms -lt 2000) { $permissionReady = $true; break }
                if ($preflight.state -in @('DENIED','CANCELLED','TIMED_OUT','UNVERIFIED')) { throw 'USER_ACTION_REQUIRED: permission preflight không được xác nhận; chưa cấp quyền phiên hoặc ghi USB.' }
                if ($preflight.state -ceq 'USER_ACTION_REQUIRED' -and -not $preflightUiTried) {
                    $preflightUiTried = $true
                    $layoutPath = Join-Path $OutputDirectory ('preflight-' + $preflightToken + '.json')
                    $job = Start-TBS5530PermissionProcess $AndroidCliPath @('--sdk=D:\Android\Sdk', 'layout', "--device=$Serial", '--flat', '--no-idle', "--output=$layoutPath")
                    try {
                        if ($job.Process.WaitForExit(10000) -and $job.Process.ExitCode -eq 0 -and (Test-Path -LiteralPath $layoutPath)) {
                            $windows = Get-Content -LiteralPath $layoutPath -Raw -Encoding utf8 | ConvertFrom-Json
                            $foreground = Invoke-PinnedAdb @('shell','dumpsys','activity','activities') -Operation UI_FOREGROUND_READ
                            $resumed = ($foreground -split "`n" | Where-Object { $_ -match 'mResumedActivity:' }) -join "`n"
                            $selector = Get-TBS5530UsbPermissionButton $windows $resumed
                            if ($selector -and (Test-TBS5530SingleUsbInventory (Invoke-PinnedAdb @('shell','dumpsys','usb') -Operation USB_INVENTORY_READ))) {
                                $tap = Start-TBS5530PermissionProcess $AndroidCliPath @('--sdk=D:\Android\Sdk','input',"--device=$Serial",'tap','--match=single','--wait=0',$selector)
                                try { if (-not $tap.Process.WaitForExit(10000) -or $tap.Process.ExitCode -ne 0) { throw 'USER_ACTION_REQUIRED: không xác nhận được thao tác UI.' } }
                                finally { Stop-TBS5530PermissionProcess $tap }
                            } else { Write-Warning 'USER_ACTION_REQUIRED: dialog bị ẩn khỏi hierarchy; cần xác minh trực quan, không chọn Always.' }
                        }
                    } finally { Stop-TBS5530PermissionProcess $job }
                }
            }
        }
        Start-Sleep -Milliseconds 200
    }
    if (-not $permissionReady) { throw 'USER_ACTION_REQUIRED: permission preflight hết 60 giây; chưa cấp quyền phiên hoặc ghi USB.' }
    Assert-Identity
}
$watch.Restart()
$script:adbStage = 'SESSION_LAUNCH'
$launchToken = [guid]::NewGuid().ToString()
$launch = @('shell', 'am', 'start', '--activity-clear-top', '--activity-single-top', '-n', "$package/.Tbs5530AutonomousDiagnosticActivity", '--es', 'read_api', $ReadApi,
    '--es', 'launch_token', $launchToken, '--ez', 'control_plane_only', ([bool]$ControlPlaneOnly).ToString().ToLowerInvariant())
if ($ControlMirror) { $launch += @('--ez', 'control_mirror', 'true') }
if ($ExplicitSetInterfaceAlt0) { $launch += @('--ez', 'explicit_set_interface_alt0', 'true') }
if ($SonyTsOutputReadback) { $launch += @('--ez','sony_ts_output_readback','true') }
$launch += @('--es', 'frontend_experiment', $FrontendExperiment)
$launchResult = Invoke-PinnedAdb $launch -Operation SESSION_LAUNCH
$launchEvidence = Join-Path $OutputDirectory ('phase31-launch-' + $launchToken + '.txt')
if (Test-Path -LiteralPath $launchEvidence) { throw 'Không ghi đè evidence đã có.' }
$launchResult | Set-Content -LiteralPath $launchEvidence -Encoding utf8
$script:adbStage = 'SESSION_CHALLENGE'
$challenge = $null
while ($watch.Elapsed.TotalSeconds -lt 10) {
    try {
        $challenge = Invoke-PinnedAdb @('shell', 'run-as', $package, 'cat', 'files/autonomous-v1-challenge.json') -Operation CHALLENGE_READ | ConvertFrom-Json
        $now = Get-Elapsed
        if (Test-TBS5530LaunchChallenge $challenge $launchToken ([bool]$ControlPlaneOnly) $now) { break }
        $challenge = $null
    } catch {
        if ($_.Exception.Message.StartsWith('FAIL-STOP: ADB ')) { Write-Warning $_.Exception.Message }
        $challenge = $null
    }
    Start-Sleep -Milliseconds 200
}
if (-not (Test-TBS5530LaunchChallenge $challenge $launchToken ([bool]$ControlPlaneOnly) (Get-Elapsed)) -or $challenge.session -notmatch '^[a-f0-9-]{36}$' -or
    $challenge.model -cne $ExpectedModel -or $challenge.api -ne $ExpectedApi -or $challenge.boot_count -ne $ExpectedBootCount -or
    $challenge.host_profile -cne $expectedHostProfile -or
    $challenge.vid -ne 29516 -or $challenge.pid -ne 21808 -or $challenge.firmware_sha256 -cne $firmwareSha -or
    $challenge.firmware_protocol -cne 'TBS_SOURCE_DUAL_RESET' -or $challenge.ts_variant -cne $ReadApi -or
    $challenge.frontend_experiment -cne $FrontendExperiment -or
    ($ExpectedFrontendGoldenSha256 -and $challenge.golden_sha256 -cne $ExpectedFrontendGoldenSha256) -or
    ($ExpectedAppApkSha256 -and $challenge.app_apk_sha256 -cne $ExpectedAppApkSha256) -or
    ($ExpectedAttachmentHistorySha256 -and $challenge.attachment_history_sha256 -cne $ExpectedAttachmentHistorySha256) -or
    $challenge.control_mirror -ne [bool]$ControlMirror -or
    $challenge.explicit_set_interface_alt0 -isnot [bool] -or $challenge.explicit_set_interface_alt0 -ne [bool]$ExplicitSetInterfaceAlt0 -or
    $challenge.sony_ts_output_readback -isnot [bool] -or $challenge.sony_ts_output_readback -ne [bool]$SonyTsOutputReadback -or
    $challenge.retry -ne 0 -or $challenge.max_upload_per_attachment -ne 1) { throw 'BLOCKED: challenge mới không khớp allowlist.' }
if ($SonyTsOutputReadback) {
    $layout = 'chip=0197|SLVT=64|entryBank=20|sync=20:10:1|select=00|reads=C4:1,C6:1,80:1,81:1,95:1,A9:1,33:1' +
        '|restore=20|sync=20:10:1|timeoutMs=2000|maxMs=5000|NO_CONFIG_WRITE|NO_URB_PENDING|retry=0' +
        '|bankProvenance=PLAN_INFERRED|snapshot=NONATOMIC|read81=SOURCE_SERIAL_RMW_NOT_PARALLEL_PROOF' +
        '|source=TBS:2ce787de9f6e81b4294692a45de15596ae2e1322,CoreELEC:072f8e99e0ac66d88d7278784e922e08e8253210,Community:c5fa27eecbe80633cbb365cc44656daea1c0e2e3'
    if ($challenge.sony_readback_layout -cne $layout) { throw 'BLOCKED: Sony readback layout không khớp source/allowlist đã duyệt.' }
}
if ($FrontendExperiment -ceq 'MANUAL_PLP0_K36' -or $b2Reload) {
    $observedHistory = Get-TBS5530Sha256Token (Invoke-PinnedAdb @('shell', 'run-as', $package, 'sha256sum',
        'files/autonomous-v1-samsung-sm-x926b-api36-attachments.txt') -Operation HISTORY_FENCE_HASH)
    if ($observedHistory -cne $ExpectedAttachmentHistorySha256) { throw 'BLOCKED: history thực tế không khớp hash ngoài app đã duyệt.' }
}
if ($b2Reload) {
    foreach ($pin in @(
        @('fx2_reload_option', 'B2_RELOAD_AFTER_SET_CONFIG'), @('fx2_reload_plan_sha256', $ExpectedReloadPlanSha256),
        @('fx2_reload_store_sha256', $ExpectedReloadStoreSha256), @('fx2_reload_anchor_sha256', $ExpectedReloadAnchorSha256),
        @('bootstrap_proof', 'SAME_SESSION_ORIGINAL132_APP_BOUNDARIES_DRAIN_REQUIRED')
    )) {
        if ($null -eq $challenge.PSObject.Properties[$pin[0]] -or $challenge.($pin[0]) -cne $pin[1]) { throw 'BLOCKED: B2 challenge không khớp card đã duyệt.' }
    }
    $storeHash = Get-TBS5530Sha256Token (Invoke-PinnedAdb @('shell', 'run-as', $package, 'sha256sum', 'no_backup/fx2-reload-v1.txt') -Operation RELOAD_STORE_HASH)
    $anchorHash = Get-TBS5530Sha256Token (Invoke-PinnedAdb @('shell', 'run-as', $package, 'sha256sum', 'no_backup/fx2-reload-v1.anchor') -Operation RELOAD_ANCHOR_HASH)
    if ($storeHash -cne $ExpectedReloadStoreSha256 -or $anchorHash -cne $ExpectedReloadAnchorSha256) { throw 'BLOCKED: B2 store/anchor thật khác hash ngoài app đã duyệt.' }
}
$directory = 'files/autonomous-v1-' + $challenge.session
if ($FirmwarePath) {
    # push only to app-private scratch through run-as; never bundle firmware or touch legacy stores.
    $encoded = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $FirmwarePath)))
    $command = 'base64 -d > ' + $directory + '/firmware.bin'
    $script:adbStage = 'FIRMWARE_STAGING'
    Invoke-PinnedAdb @('shell', 'run-as', $package, 'sh', '-c', "'$command'") $encoded -Operation FIRMWARE_PRIVATE_STAGE | Out-Null
}
$script:adbStage = 'PRE_GRANT_IDENTITY'
Assert-Identity
$now = Get-Elapsed
$expiry = [Math]::Min([long]$challenge.created_elapsed_ms + 300000, $now + $DurationSeconds * 1000L)
$grant = @{}
$challenge.PSObject.Properties | ForEach-Object { $grant[$_.Name] = $_.Value }
$grant.profile = $Profile; $grant.bounded_user_authorization = $true; $grant.rf_in_confirmed = -not $ControlPlaneOnly
$grant.host_script_sha256 = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
$grant.expires_elapsed_ms = $expiry
if ($PhysicalRecoveryEpoch) {
    $grant.physical_recovery_epoch = $PhysicalRecoveryEpoch
    $grant.physical_recovery_confirmed = $true
}
$heartbeatPath = $directory + '/heartbeat.json'
$granted = $false
$exitCode = 1
$terminalReport = $null
$controlReadyBeats = 0
$controlStopSent = $false
try {
    $script:adbStage = 'SESSION_GRANT'
    $initialBeat = Exchange-Heartbeat -Grant $grant
    if ($initialBeat.expired) { throw 'FAIL-STOP: session hết hạn trước grant.' }
    $granted = $true
    $script:adbStage = 'SESSION_RUNNING'
    while ($watch.Elapsed.TotalSeconds -lt $DurationSeconds) {
        $exchange = if ($controlStopSent) { Exchange-Heartbeat -Stop } else { Exchange-Heartbeat }
        if ($null -ne $exchange.report) {
            $parsed = $exchange.report
            if ((Test-TBS5530DrainedTerminal $parsed) -or $parsed.result -ceq 'CLEANUP_UNCERTAIN_NO_FORCE_CLOSE') {
                $terminalReport = $parsed
                $exitCode = if ($ControlPlaneOnly) { Get-TBS5530ControlPlaneExitCode $parsed $launchToken } else { Get-TBS5530AutonomousExitCode $parsed }
                $parsed | ConvertTo-Json -Depth 12 -Compress
                break
            }
        }
        if ($exchange.expired) { break }
        if ($ControlPlaneOnly -and -not $controlStopSent -and $null -ne $exchange.PSObject.Properties['control_plane'] -and $null -ne $exchange.control_plane) {
            $ready = $exchange.control_plane
            if ($ready.session -cne $challenge.session -or $ready.launch_token -cne $launchToken -or
                $ready.ready -cne $true -or $ready.device_io_forbidden -cne $true) { throw 'FAIL-STOP: control-plane receipt không khớp phiên.' }
            $controlReadyBeats++
            if ($controlReadyBeats -ge 3) { Exchange-Heartbeat -Stop | Out-Null; $controlStopSent = $true }
        }
        Update-UsbPermission $exchange.permission $exchange.elapsed_ms
        Start-Sleep -Milliseconds 100
    }
} finally {
    Stop-TBS5530PermissionProcess $uiJob
    if ($granted) {
        $script:adbStage = 'SESSION_STOP'
        try { Exchange-Heartbeat -Stop | Out-Null }
        catch {
            if ($_.Exception.Message.StartsWith('FAIL-STOP: ADB ')) { Write-Warning $_.Exception.Message }
            Write-Warning 'Host không còn liên lạc; Android phải fail-stop sau heartbeat 2 giây. Không reconnect.'
        }
    }
}
if ($null -eq $terminalReport) { throw 'FAIL-STOP: deadline hoặc kết thúc không có terminal report đã xác minh.' }
if ($terminalReport.frontend_experiment -cne $FrontendExperiment) { throw 'FAIL-STOP: report không khớp frontend experiment đã cấp quyền.' }
if ($terminalReport.launch_token -cne $launchToken -or $terminalReport.control_plane_only -isnot [bool] -or
    $terminalReport.control_plane_only -ne [bool]$ControlPlaneOnly) { throw 'FAIL-STOP: terminal report không khớp launch hiện hành.' }
if ($ControlPlaneOnly) {
    $evidence = Join-Path $OutputDirectory ('phase31-control-plane-' + $challenge.session + '.json')
    if (Test-Path -LiteralPath $evidence) { throw 'Không ghi đè evidence đã có.' }
    $terminalReport | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $evidence -Encoding utf8
}
if ($SonyTsOutputReadback) {
    if ($terminalReport.sony_ts_output_readback.enabled -cne $true) { throw 'FAIL-STOP: report Sony readback không khớp grant.' }
    $evidence = Join-Path $OutputDirectory ('phase31-sony-output-' + $challenge.session + '.json')
    if (Test-Path -LiteralPath $evidence) { throw 'Không ghi đè evidence đã có.' }
    $terminalReport | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $evidence -Encoding utf8
}
if ($ExplicitSetInterfaceAlt0) {
    if ($terminalReport.explicit_set_interface_alt0 -cne $true) { throw 'FAIL-STOP: report không khớp option alt0.' }
    $evidence = Join-Path $OutputDirectory ('phase31-interface-' + $challenge.session + '.json')
    if (Test-Path -LiteralPath $evidence) { throw 'Không ghi đè evidence đã có.' }
    $terminalReport | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $evidence -Encoding utf8
}
if ($b2Reload -and (Test-TBS5530DrainedTerminal $terminalReport)) {
    $script:adbStage = 'EVIDENCE_EXPORT'
    Export-TBS5530HandoffEvents $challenge.session $terminalReport $OutputDirectory | Out-Null
}
if ($ControlMirror -and (Test-TBS5530DrainedTerminal $terminalReport)) {
    $script:adbStage = 'EVIDENCE_EXPORT'
    if ($terminalReport.control_mirror -cne 'SYNCED_CLOSED') { throw 'FAIL-STOP: EP0 transcript chưa được ghi bền.' }
    $local = Join-Path $OutputDirectory ('phase31-control-' + $challenge.session)
    if (Test-Path -LiteralPath $local) { throw 'Không ghi đè evidence đã có.' }
    New-Item -ItemType Directory -Path $local | Out-Null
    $remote = $directory + '/control-ep0.jsonl'
    $text = Invoke-PinnedAdb @('exec-out', 'run-as', $package, 'cat', $remote) '' $true -Operation CONTROL_TRACE_READ -DrainedReport $terminalReport
    $trace = Join-Path $local 'control-ep0.jsonl'
    [IO.File]::WriteAllText($trace, $text, [Text.UTF8Encoding]::new($false))
    $sourceHash = Get-TBS5530Sha256Token (Invoke-PinnedAdb @('shell', 'run-as', $package, 'sha256sum', $remote) -Operation CONTROL_TRACE_HASH -DrainedReport $terminalReport)
    $hash = (Get-FileHash -LiteralPath $trace -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($sourceHash -cne $hash) { throw 'FAIL-STOP: hash Android/host EP0 transcript không khớp.' }
    @{ run_id=$challenge.session; layer='ANDROID_APP_OBSERVED'; sha256=$hash; bytes=(Get-Item -LiteralPath $trace).Length;
        records=$terminalReport.control_mirror_records; cleanup=$terminalReport.cleanup; read_api=$ReadApi } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $local 'manifest.json') -Encoding utf8
}
if ($FrontendExperiment -ceq 'MANUAL_PLP0_K36' -or $b2Reload) {
    if (-not (Test-TBS5530DrainedTerminal $terminalReport)) { throw 'FAIL-STOP: không đọc history trước drain.' }
    $observedHistory = Get-TBS5530Sha256Token (Invoke-PinnedAdb @('shell', 'run-as', $package, 'sha256sum',
        'files/autonomous-v1-samsung-sm-x926b-api36-attachments.txt') -Operation HISTORY_FENCE_HASH)
    $evidence = Join-Path $OutputDirectory ('phase31-history-fence-' + $challenge.session + '.json')
    if (Test-Path -LiteralPath $evidence) { throw 'Không ghi đè evidence đã có.' }
    $historyMatches = $terminalReport.attachment_history_sha256 -ceq $observedHistory
    @{ baseline_sha256=$ExpectedAttachmentHistorySha256; final_sha256=$observedHistory; session=$challenge.session;
        expected_final_sha256=$terminalReport.attachment_history_sha256; verified=$historyMatches;
        cleanup=$terminalReport.cleanup; source='HOST_READ_AFTER_DRAIN_NOT_AUTOMATIC_APPROVAL_FOR_NEXT_RUN' } |
        ConvertTo-Json | Set-Content -LiteralPath $evidence -Encoding utf8
    if (-not $historyMatches) { throw 'FAIL-STOP: history sau run không khớp fence của app; evidence đã giữ.' }
}
if ($b2Reload) {
    if (-not (Test-TBS5530DrainedTerminal $terminalReport)) { throw 'FAIL-STOP: không đọc reload store trước drain.' }
    foreach ($name in @('reload-a0-native.jsonl','reload-native-boundaries.json')) {
        $remote = $directory + '/' + $name
        $command = 'if [ -f ' + $remote + ' ]; then printf PRESENT; fi'
        $present = Invoke-PinnedAdb @('shell', 'run-as', $package, 'sh', '-c', "'$command'") -Operation NATIVE_A0_EXISTS
        if ($present -cne 'PRESENT') {
            if ($null -ne $terminalReport.fx2_reload -and $terminalReport.fx2_reload.state -ceq 'COMPLETE') {
                throw 'FAIL-STOP: reload COMPLETE thiếu native boundary evidence; không coi là thành công.'
            }
            continue
        }
        $local = Join-Path $OutputDirectory ('b2-' + $challenge.session + '-' + $name)
        if (Test-Path -LiteralPath $local) { throw 'Không ghi đè evidence đã có.' }
        $text = Invoke-PinnedAdb @('exec-out', 'run-as', $package, 'cat', $remote) '' $true -Operation NATIVE_A0_READ -DrainedReport $terminalReport
        [IO.File]::WriteAllText($local, $text, [Text.UTF8Encoding]::new($false))
        $sourceHash = Get-TBS5530Sha256Token (Invoke-PinnedAdb @('shell', 'run-as', $package, 'sha256sum', $remote) -Operation NATIVE_A0_HASH -DrainedReport $terminalReport)
        if ($sourceHash -cne (Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash.ToLowerInvariant()) { throw 'FAIL-STOP: native evidence hash Android/host không khớp.' }
    }
    $observedStore = Get-TBS5530Sha256Token (Invoke-PinnedAdb @('shell', 'run-as', $package, 'sha256sum', 'no_backup/fx2-reload-v1.txt') -Operation RELOAD_STORE_HASH)
    $observedAnchor = Get-TBS5530Sha256Token (Invoke-PinnedAdb @('shell', 'run-as', $package, 'sha256sum', 'no_backup/fx2-reload-v1.anchor') -Operation RELOAD_ANCHOR_HASH)
    if ($terminalReport.fx2_reload_store_sha256 -cne $observedStore -or $terminalReport.fx2_reload_anchor_sha256 -cne $observedAnchor) {
        throw 'FAIL-STOP: reload store/anchor sau drain không khớp owned append fence; không cấp quyền run tiếp.'
    }
    $evidence = Join-Path $OutputDirectory ('phase31-b2-reload-' + $challenge.session + '.json')
    if (Test-Path -LiteralPath $evidence) { throw 'Không ghi đè evidence đã có.' }
    @{ report=$terminalReport; baseline_store_sha256=$ExpectedReloadStoreSha256; final_store_sha256=$observedStore;
        baseline_anchor_sha256=$ExpectedReloadAnchorSha256; final_anchor_sha256=$observedAnchor;
        plan_sha256=$ExpectedReloadPlanSha256; helper_sha256=$ExpectedHelperSha256; next_run_approval=$false } |
        ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $evidence -Encoding utf8
}
exit $exitCode
