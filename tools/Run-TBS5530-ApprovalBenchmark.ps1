# Explicit test-only execution. Never installs APKs or opens any app data/USB namespace.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$LedgerPath,
    [Parameter(Mandatory)][string]$RuntimeJar,
    [switch]$ApproveIsolatedAndroidBenchmark,
    [string]$AdbPath = 'D:\Android\Sdk\platform-tools\adb.exe'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $ApproveIsolatedAndroidBenchmark) { throw 'BLOCKED: cần duyệt riêng benchmark Android không phần cứng.' }
if ($Serial -notmatch '^[A-Za-z0-9._:\[\]-]+$' -or
    -not (Test-Path -LiteralPath $AdbPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $RuntimeJar -PathType Leaf)) { throw 'Serial hoặc file thực thi không hợp lệ.' }
$runtime = [IO.Path]::GetFullPath($RuntimeJar)
if ($runtime.Contains('"')) { throw 'Đường dẫn không hợp lệ.' }
. (Join-Path $PSScriptRoot 'Get-TBS5530-ApprovalTarget.ps1')
$target = Get-TBS5530ApprovalTarget -AdbPath $AdbPath -Serial $Serial
$pinnedIdentity = $target.Identity
function Assert-PinnedTarget {
    $current = Get-TBS5530ApprovalTarget -AdbPath $AdbPath -Serial $Serial
    if ($current.Identity -cne $pinnedIdentity) { throw 'FAIL-STOP: target hoặc boot identity đã đổi; không reconnect.' }
}
$namespace = [Guid]::NewGuid().ToString()
$code = '/sdcard/Download/tbs5530-approval-benchmark-code/' + $namespace
$directory = '/sdcard/Download/tbs5530-approval-benchmark/' + $namespace
function Start-Adb([string]$Arguments) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $AdbPath; $start.Arguments = '-s ' + $Serial + ' ' + $Arguments
    $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $child = [Diagnostics.Process]::new(); $child.StartInfo = $start
    if (-not $child.Start()) { throw 'Không khởi động được ADB.' }
    return $child
}
function Invoke-Bounded([string]$Arguments, [int]$Seconds = 10) {
    $child = Start-Adb $Arguments
    try {
        $output = $child.StandardOutput.ReadToEndAsync(); $errors = $child.StandardError.ReadToEndAsync()
        if (-not $child.WaitForExit($Seconds * 1000)) { $child.Kill(); throw 'FAIL-STOP: ADB quá hạn; không reconnect.' }
        if ($child.ExitCode -ne 0) { throw ('FAIL-STOP: thao tác namespace benchmark lỗi: ' + $errors.Result.Trim()) }
        return $output.Result
    } finally { $child.Dispose() }
}
# Preflight completes BEFORE the first mkdir/push. Recheck before each setup mutation.
$process = $null
$complete = $false
try {
    Assert-PinnedTarget
    Invoke-Bounded ('shell mkdir -p ' + $code) | Out-Null
    Assert-PinnedTarget
    Invoke-Bounded ('push "' + $runtime + '" ' + $code + '/benchmark.jar') | Out-Null
    Assert-PinnedTarget
    $fake = Invoke-Bounded ('shell "CLASSPATH=' + $code + '/benchmark.jar app_process / info.tbs5530.approvalbenchmark.FakeTests"') 30
    Write-Output $fake
    if ($fake -notmatch 'FAKE_TESTS run=[1-9][0-9]* failed=0') { throw 'FAIL-STOP: fake tests không PASS.' }
    Assert-PinnedTarget
    $deadlineTimestamp = [Diagnostics.Stopwatch]::GetTimestamp() + 240L * [Diagnostics.Stopwatch]::Frequency
    function Remaining-HarnessMillis([int]$Maximum) {
        $remainingMs = [long](($deadlineTimestamp - [Diagnostics.Stopwatch]::GetTimestamp()) * 1000 / [Diagnostics.Stopwatch]::Frequency)
        if ($remainingMs -le 0) { throw 'TOTAL_BENCHMARK_DEADLINE: hết 240 giây toàn bộ harness, không phải write ACK timeout.' }
        return [int][Math]::Min($Maximum, $remainingMs)
    }
    $process = Start-Adb ('shell "CLASSPATH=' + $code + '/benchmark.jar app_process / info.tbs5530.approvalbenchmark.Main --isolated-benchmark ' + $directory + '"')
    $errors = $process.StandardError.ReadToEndAsync()
    $ready = $process.StandardOutput.ReadLineAsync()
    if (-not $ready.Wait((Remaining-HarnessMillis 5000)) -or $ready.Result -cne ('BENCHMARK_READY ' + $directory)) {
        throw 'FAIL-STOP: Android Java harness không sẵn sàng.'
    }
    $remaining = $process.StandardOutput.ReadToEndAsync()
    & (Join-Path $PSScriptRoot 'Watch-TBS5530-BootstrapApproval.ps1') -Benchmark -BenchmarkDirectory $directory -Serial $Serial -LedgerPath $LedgerPath -AdbPath $AdbPath -DeadlineTimestamp $deadlineTimestamp -ExpectedIdentity $pinnedIdentity -HardTimeoutSeconds 240
    if (-not $process.WaitForExit((Remaining-HarnessMillis 3000)) -or $process.ExitCode -ne 0) {
        throw 'FAIL-STOP: Android harness không kết thúc thành công.'
    }
    Write-Output $remaining.Result
    # Watch has fsynced all 132 full Android samples and the PASS summary to the local ledger.
    Assert-PinnedTarget
    Invoke-Bounded ('shell rm -rf -- ' + $code + ' ' + $directory) | Out-Null
    $complete = $true
    Write-Output 'Đã xuất đủ mẫu vào ledger ngoài Git và xóa đúng hai namespace test; không mở app data production.'
} finally {
    if ($null -ne $process) {
        if (-not $process.HasExited) { $process.Kill() }
        $process.Dispose()
    }
    if (-not $complete) {
        Write-Warning ('Giữ evidence test tại ' + $code + ' và ' + $directory + '; không reconnect/cleanup khi lỗi. Chỉ dừng ADB con do script tạo; harness tự hết hạn 240 giây.')
    }
}
