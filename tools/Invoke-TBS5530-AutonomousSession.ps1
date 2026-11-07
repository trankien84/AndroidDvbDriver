# SPDX-License-Identifier: GPL-2.0-or-later
# One bounded debug session; no package changes, reconnect or USB ownership changes.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [Parameter(Mandatory)][string]$ExpectedModel,
    [Parameter(Mandatory)][ValidateRange(21, 99)][int]$ExpectedApi,
    [Parameter(Mandatory)][ValidateRange(0, 2147483647)][int]$ExpectedBootCount,
    [ValidateSet('AUTO_VERIFIED', 'EXPERIMENTAL_DIAGNOSTIC')][string]$Profile = 'AUTO_VERIFIED',
    [ValidateSet('SYNC_NATIVE_ERRNO', 'QUEUED_USB_REQUEST_8X4096', 'NATIVE_USBFS_URB_8X4096')][string]$ReadApi = 'SYNC_NATIVE_ERRNO',
    [ValidateRange(10, 300)][int]$DurationSeconds = 300,
    [string]$FirmwarePath,
    [string]$PhysicalRecoveryEpoch,
    [switch]$PhysicalRecoveryConfirmed,
    [string]$AdbPath = 'D:\Android\Sdk\platform-tools\adb.exe',
    [switch]$GrantBoundedSession,
    [switch]$ProofOfGuardPassed,
    [switch]$AutoUsbPermission,
    [string]$AndroidCliPath,
    [string]$OutputDirectory = 'D:\TSDuck\logs'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $GrantBoundedSession -or -not $ProofOfGuardPassed) { throw 'BLOCKED: cần quyền phiên bounded và kết quả proof-of-guard trước khi chạy thật.' }
if ($PhysicalRecoveryEpoch) {
    if (-not $PhysicalRecoveryConfirmed -or $PhysicalRecoveryEpoch -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$' -or
        $PhysicalRecoveryEpoch -ceq '00000000-0000-0000-0000-000000000000') {
        throw 'BLOCKED: physical recovery epoch phải là GUID cố định do operator cấp cho sự kiện đã được người dùng xác nhận.'
    }
} elseif ($PhysicalRecoveryConfirmed) { throw 'BLOCKED: xác nhận physical recovery thiếu epoch; không tự tạo GUID.' }
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
function Invoke-PinnedAdb([string[]]$DeviceArguments, [string]$InputText = '') {
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $AdbPath; $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true
    $psi.RedirectStandardInput = $true; $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
    $arguments = @('-s', $Serial) + $DeviceArguments
    if ($psi.PSObject.Properties.Name -contains 'ArgumentList') {
        foreach ($argument in $arguments) { $psi.ArgumentList.Add($argument) }
    } else {
        foreach ($argument in $arguments) { if ($argument.Contains('"')) { throw 'Unsupported quoted ADB argument.' } }
        $psi.Arguments = ($arguments | ForEach-Object { '"' + $_ + '"' }) -join ' '
    }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $psi
    try {
        if (-not $process.Start()) { throw 'ADB start failed.' }
        $output = $process.StandardOutput.ReadToEndAsync(); $errorText = $process.StandardError.ReadToEndAsync()
        if ($InputText) { $process.StandardInput.Write($InputText) }; $process.StandardInput.Close()
        if (-not $process.WaitForExit(1500)) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue; throw 'FAIL-STOP: ADB timeout, không reconnect/fallback.' }
        if ($process.ExitCode -ne 0) { throw 'FAIL-STOP: ADB không hoàn tất trên serial đã pin.' }
        return $output.GetAwaiter().GetResult().Trim()
    } finally { $process.Dispose() }
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
function Assert-Identity {
    if ((Invoke-PinnedAdb @('get-state')) -cne 'device' -or
        (Invoke-PinnedAdb @('shell', 'getprop', 'ro.product.model')) -cne $ExpectedModel -or
        [int](Invoke-PinnedAdb @('shell', 'getprop', 'ro.build.version.sdk')) -ne $ExpectedApi -or
        [int](Invoke-PinnedAdb @('shell', 'settings', 'get', 'global', 'boot_count')) -ne $ExpectedBootCount) {
        throw 'FAIL-STOP: serial/model/API/boot thay đổi; không cấp lại quyền.'
    }
}
function Get-Elapsed {
    $text = Invoke-PinnedAdb @('shell', 'cat', '/proc/uptime')
    $seconds = [double]::Parse(($text -split ' ')[0], [Globalization.CultureInfo]::InvariantCulture)
    return [long][Math]::Floor($seconds * 1000)
}
function Write-PrivateJson([string]$Path, [object]$Value) {
    if ($Path -notmatch '^files/autonomous-v1-[a-f0-9-]{36}/(?:grant|heartbeat|permission-ui)\.json$') { throw 'Invalid private session path.' }
    # .hosttmp must not collide with Android AtomicFile .new/.bak recovery names.
    $command = 'cat > ' + $Path + '.hosttmp && mv ' + $Path + '.hosttmp ' + $Path
    Invoke-PinnedAdb @('shell', 'run-as', $package, 'sh', '-c', "'$command'") ($Value | ConvertTo-Json -Compress -Depth 8) | Out-Null
}
function Exchange-Heartbeat([long]$Now) {
    $command = 'cat > ' + $heartbeatPath + '.hosttmp && mv ' + $heartbeatPath + '.hosttmp ' + $heartbeatPath +
        '; printf "{\"report\":"; if [ -f ' + $directory + '/report.json ]; then cat ' + $directory + '/report.json; else printf null; fi' +
        '; printf ",\"permission\":"; if [ -f ' + $directory + '/permission.json ]; then cat ' + $directory + '/permission.json; else printf null; fi; printf "}"'
    $beat = @{ session = $challenge.session; elapsed_ms = $Now; stop = $false } | ConvertTo-Json -Compress
    return (Invoke-PinnedAdb @('shell','run-as',$package,'sh','-c',"'$command'") $beat | ConvertFrom-Json)
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
            $foreground = Invoke-PinnedAdb @('shell', 'dumpsys', 'activity', 'activities')
            $resumed = ($foreground -split "`n" | Where-Object { $_ -match 'mResumedActivity:' }) -join "`n"
            $selector = Get-TBS5530UsbPermissionButton $windows $resumed
            if (-not $selector -or -not (Test-TBS5530SingleUsbInventory (Invoke-PinnedAdb @('shell', 'dumpsys', 'usb')))) {
                $uiHandled.Add($uiRequest.request_token) | Out-Null
                Write-Warning 'USER_ACTION_REQUIRED: hierarchy không xác nhận đúng USB permission/TBS5530; không bấm tự động hoặc chọn Always. Giữ heartbeat trong TTL để xử lý UI đã xác minh.'
                return
            }
            Write-PrivateJson $heartbeatPath @{ session = $challenge.session; elapsed_ms = (Get-Elapsed); stop = $false }
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
    $preflightToken = [guid]::NewGuid().ToString()
    Invoke-PinnedAdb @('shell', 'am', 'start', '-n', "$package/.Tbs5530PermissionPreflightActivity", '--es', 'preflight_token', $preflightToken) | Out-Null
    $preflightWatch = [Diagnostics.Stopwatch]::StartNew()
    $permissionReady = $false; $preflightUiTried = $false
    while ($preflightWatch.Elapsed.TotalSeconds -lt 60) {
        $command = 'if [ -f files/permission-preflight.json ]; then cat files/permission-preflight.json; fi'
        $text = Invoke-PinnedAdb @('shell', 'run-as', $package, 'sh', '-c', "'$command'")
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
                            $foreground = Invoke-PinnedAdb @('shell','dumpsys','activity','activities')
                            $resumed = ($foreground -split "`n" | Where-Object { $_ -match 'mResumedActivity:' }) -join "`n"
                            $selector = Get-TBS5530UsbPermissionButton $windows $resumed
                            if ($selector -and (Test-TBS5530SingleUsbInventory (Invoke-PinnedAdb @('shell','dumpsys','usb')))) {
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
Invoke-PinnedAdb @('shell', 'am', 'start', '-n', "$package/.Tbs5530AutonomousDiagnosticActivity", '--es', 'read_api', $ReadApi) | Out-Null
$challenge = $null
while ($watch.Elapsed.TotalSeconds -lt 10) {
    try {
        $challenge = Invoke-PinnedAdb @('shell', 'run-as', $package, 'cat', 'files/autonomous-v1-challenge.json') | ConvertFrom-Json
        $now = Get-Elapsed
        if ($challenge.policy -ceq 'TBS5530-AUTONOMOUS/v1' -and $now -ge $challenge.created_elapsed_ms -and $now - $challenge.created_elapsed_ms -lt 10000) { break }
        $challenge = $null
    } catch { $challenge = $null }
    Start-Sleep -Milliseconds 200
}
if ($null -eq $challenge -or -not $challenge.live_ready -or $challenge.session -notmatch '^[a-f0-9-]{36}$' -or
    $challenge.model -cne $ExpectedModel -or $challenge.api -ne $ExpectedApi -or $challenge.boot_count -ne $ExpectedBootCount -or
    $challenge.host_profile -cne $expectedHostProfile -or
    $challenge.vid -ne 29516 -or $challenge.pid -ne 21808 -or $challenge.firmware_sha256 -cne $firmwareSha -or
    $challenge.firmware_protocol -cne 'TBS_SOURCE_DUAL_RESET' -or $challenge.ts_variant -cne $ReadApi -or
    $challenge.retry -ne 0 -or $challenge.max_upload_per_attachment -ne 1) { throw 'BLOCKED: challenge mới không khớp allowlist.' }
$directory = 'files/autonomous-v1-' + $challenge.session
if ($FirmwarePath) {
    # push only to app-private scratch through run-as; never bundle firmware or touch legacy stores.
    $encoded = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $FirmwarePath)))
    $command = 'base64 -d > ' + $directory + '/firmware.bin'
    Invoke-PinnedAdb @('shell', 'run-as', $package, 'sh', '-c', "'$command'") $encoded | Out-Null
}
Assert-Identity
$now = Get-Elapsed
$expiry = [Math]::Min([long]$challenge.created_elapsed_ms + 300000, $now + $DurationSeconds * 1000L)
$grant = @{}
$challenge.PSObject.Properties | ForEach-Object { $grant[$_.Name] = $_.Value }
$grant.profile = $Profile; $grant.bounded_user_authorization = $true; $grant.rf_in_confirmed = $true
$grant.expires_elapsed_ms = $expiry
if ($PhysicalRecoveryEpoch) {
    $grant.physical_recovery_epoch = $PhysicalRecoveryEpoch
    $grant.physical_recovery_confirmed = $true
}
$heartbeatPath = $directory + '/heartbeat.json'
$granted = $false
$exitCode = 1
$terminalReport = $null
try {
    Write-PrivateJson $heartbeatPath @{ session = $challenge.session; elapsed_ms = $now; stop = $false }
    Write-PrivateJson ($directory + '/grant.json') $grant; $granted = $true
    while ($watch.Elapsed.TotalSeconds -lt $DurationSeconds) {
        $now = Get-Elapsed
        if ($now -ge $expiry) { break }
        $exchange = Exchange-Heartbeat $now
        Update-UsbPermission $exchange.permission $now
        if ($null -ne $exchange.report) {
            $parsed = $exchange.report
            if ($parsed.cleanup -ceq 'DRAIN_CONFIRMED' -or $parsed.result -ceq 'CLEANUP_UNCERTAIN_NO_FORCE_CLOSE') {
                $terminalReport = $parsed
                $exitCode = Get-TBS5530AutonomousExitCode $parsed
                $parsed | ConvertTo-Json -Depth 12 -Compress
                break
            }
        }
        Start-Sleep -Milliseconds 100
    }
} finally {
    Stop-TBS5530PermissionProcess $uiJob
    if ($granted) {
        try { Write-PrivateJson $heartbeatPath @{ session = $challenge.session; elapsed_ms = (Get-Elapsed); stop = $true } }
        catch { Write-Warning 'Host không còn liên lạc; Android phải fail-stop sau heartbeat 2 giây. Không reconnect.' }
    }
}
if ($null -eq $terminalReport) { throw 'FAIL-STOP: deadline hoặc kết thúc không có terminal report đã xác minh.' }
exit $exitCode
