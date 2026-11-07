# SPDX-License-Identifier: GPL-2.0-or-later
# One bounded debug session; no package changes, reconnect or USB ownership changes.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [Parameter(Mandatory)][string]$ExpectedModel,
    [Parameter(Mandatory)][ValidateRange(21, 99)][int]$ExpectedApi,
    [Parameter(Mandatory)][ValidateRange(0, 2147483647)][int]$ExpectedBootCount,
    [ValidateSet('AUTO_VERIFIED', 'EXPERIMENTAL_DIAGNOSTIC')][string]$Profile = 'AUTO_VERIFIED',
    [ValidateSet('SYNC_NATIVE_ERRNO', 'QUEUED_USB_REQUEST_8X4096')][string]$ReadApi = 'SYNC_NATIVE_ERRNO',
    [ValidateRange(10, 300)][int]$DurationSeconds = 300,
    [string]$FirmwarePath,
    [string]$PhysicalRecoveryEpoch,
    [switch]$PhysicalRecoveryConfirmed,
    [string]$AdbPath = 'D:\Android\Sdk\platform-tools\adb.exe',
    [switch]$GrantBoundedSession,
    [switch]$ProofOfGuardPassed
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
if ($ReadApi -eq 'QUEUED_USB_REQUEST_8X4096' -and $Profile -ne 'EXPERIMENTAL_DIAGNOSTIC') { throw 'BLOCKED: queued receive chỉ thuộc EXPERIMENTAL_DIAGNOSTIC.' }
$package = 'info.martinmarinov.dvbdriver'
$firmwareSha = 'b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc'
$watch = [Diagnostics.Stopwatch]::StartNew()
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
    foreach ($field in @('result', 'cleanup', 'state', 'structure_confirmed')) {
        if ($null -eq $Report.PSObject.Properties[$field]) { return 1 }
    }
    if ($Report.result -ceq 'STRUCTURE_CONFIRMED' -and $Report.cleanup -ceq 'DRAIN_CONFIRMED' -and
        $Report.state -ceq 'REPORT' -and $Report.structure_confirmed -eq $true) { return 0 }
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
    if ($Path -notmatch '^files/autonomous-v1-[a-f0-9-]{36}/[a-z]+\.json$') { throw 'Invalid private session path.' }
    # .hosttmp must not collide with Android AtomicFile .new/.bak recovery names.
    $command = 'cat > ' + $Path + '.hosttmp && mv ' + $Path + '.hosttmp ' + $Path
    Invoke-PinnedAdb @('shell', 'run-as', $package, 'sh', '-c', "'$command'") ($Value | ConvertTo-Json -Compress -Depth 8) | Out-Null
}
Assert-Identity
if ($FirmwarePath) {
    if (-not (Test-Path -LiteralPath $FirmwarePath -PathType Leaf) -or
        (Get-Item -LiteralPath $FirmwarePath).Length -ne 8192 -or
        (Get-FileHash -LiteralPath $FirmwarePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $firmwareSha) {
        throw 'BLOCKED: firmware private input không đúng size/SHA.'
    }
}
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
        Write-PrivateJson $heartbeatPath @{ session = $challenge.session; elapsed_ms = $now; stop = $false }
        try {
            $report = Invoke-PinnedAdb @('shell', 'run-as', $package, 'cat', ($directory + '/report.json'))
            if ($report) {
                $parsed = $report | ConvertFrom-Json
                if ($parsed.cleanup -ceq 'DRAIN_CONFIRMED' -or $parsed.result -ceq 'CLEANUP_UNCERTAIN_NO_FORCE_CLOSE') {
                    $terminalReport = $parsed
                    $exitCode = Get-TBS5530AutonomousExitCode $parsed
                    $report
                    break
                }
            }
        } catch { if ((Invoke-PinnedAdb @('get-state')) -cne 'device') { throw } }
        Start-Sleep -Milliseconds 350
    }
} finally {
    if ($granted) {
        try { Write-PrivateJson $heartbeatPath @{ session = $challenge.session; elapsed_ms = (Get-Elapsed); stop = $true } }
        catch { Write-Warning 'Host không còn liên lạc; Android phải fail-stop sau heartbeat 2 giây. Không reconnect.' }
    }
}
if ($null -eq $terminalReport) { throw 'FAIL-STOP: deadline hoặc kết thúc không có terminal report đã xác minh.' }
exit $exitCode
