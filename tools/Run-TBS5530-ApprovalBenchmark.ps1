# Explicit test-only execution. Never installs APKs or opens any app data/USB namespace.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$LedgerPath,
    [string]$RuntimeJar,
    [switch]$FullPathInstrumentation,
    [ValidateSet('baseline', 'verified')][string]$AuditMode = 'baseline',
    [switch]$ApproveIsolatedAndroidBenchmark,
    [string]$AdbPath = 'D:\Android\Sdk\platform-tools\adb.exe'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $ApproveIsolatedAndroidBenchmark) { throw 'BLOCKED: cần duyệt riêng benchmark Android không phần cứng.' }
# Failure output is saved only to the private benchmark ledger, never project docs.
if ($Serial -notmatch '^[A-Za-z0-9._-]+:[0-9]+$' -or
    -not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw 'Serial hoặc file thực thi không hợp lệ.' }
if (-not $FullPathInstrumentation -and -not $RuntimeJar) { throw 'Standalone benchmark cần RuntimeJar.' }
if ($FullPathInstrumentation -and $RuntimeJar) { throw 'Instrumentation không dùng RuntimeJar; chạy FakeTests riêng nếu cần.' }
$runtime = $null
if ($RuntimeJar) {
    if (-not (Test-Path -LiteralPath $RuntimeJar -PathType Leaf)) { throw 'Không tìm thấy RuntimeJar.' }
    $runtime = [IO.Path]::GetFullPath($RuntimeJar)
    if ($runtime.Contains('"')) { throw 'Đường dẫn không hợp lệ.' }
}
. (Join-Path $PSScriptRoot 'Tbs5530-ApprovalLedger.ps1')
$LedgerPath = Resolve-TBS5530ApprovalLedgerPath -Path $LedgerPath -RepositoryRoot (Join-Path $PSScriptRoot '..')
if ($LedgerPath.Equals((Get-TBS5530HardwareLedgerPath), [StringComparison]::OrdinalIgnoreCase)) { throw 'BLOCKED: benchmark không dùng hardware ledger.' }
. (Join-Path $PSScriptRoot 'Tbs5530-ApprovalTiming.ps1')
. (Join-Path $PSScriptRoot 'Get-TBS5530-ApprovalTarget.ps1')
$target = Get-TBS5530ApprovalTarget -AdbPath $AdbPath -Serial $Serial
$pinnedIdentity = $target.Identity
function Assert-PinnedTarget {
    $current = Get-TBS5530ApprovalTarget -AdbPath $AdbPath -Serial $Serial
    if ($current.Identity -cne $pinnedIdentity) { throw 'FAIL-STOP: target hoặc boot identity đã đổi; không reconnect.' }
}
$namespace = [Guid]::NewGuid().ToString()
$code = '/sdcard/Download/tbs5530-approval-benchmark-code/' + $namespace
$directory = $(if ($FullPathInstrumentation) {
    '/data/user/0/info.martinmarinov.dvbdriver.phase29offline/files/tbs5530-approval-benchmark/'
} else { '/sdcard/Download/tbs5530-approval-benchmark/' }) + $namespace
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
$stdoutCapture = $null; $stderrCapture = $null
$complete = $false
Write-TBS5530BenchmarkLedgerEntry -Path $LedgerPath -Entry @{ event = 'benchmark_wrapper_start'; wrapper_run = $namespace
    benchmark_directory = $directory; full_path_instrumentation = [bool]$FullPathInstrumentation
    audit_mode = $AuditMode; utc = [DateTime]::UtcNow.ToString('o') }
try {
    if (-not $FullPathInstrumentation) {
        Assert-PinnedTarget
        Invoke-Bounded ('shell mkdir -p ' + $code) | Out-Null
        Assert-PinnedTarget
        Invoke-Bounded ('push "' + $runtime + '" ' + $code + '/benchmark.jar') | Out-Null
        Assert-PinnedTarget
        $fake = Invoke-Bounded ('shell "CLASSPATH=' + $code + '/benchmark.jar app_process / info.tbs5530.approvalbenchmark.FakeTests"') 30
        Write-Output $fake
        if ($fake -notmatch 'FAKE_TESTS run=[1-9][0-9]* failed=0') { throw 'FAIL-STOP: fake tests không PASS.' }
    }
    Assert-PinnedTarget
    $deadlineTimestamp = [Diagnostics.Stopwatch]::GetTimestamp() + 240L * [Diagnostics.Stopwatch]::Frequency
    function Remaining-HarnessMillis([int]$Maximum) {
        $remainingMs = [long](($deadlineTimestamp - [Diagnostics.Stopwatch]::GetTimestamp()) * 1000 / [Diagnostics.Stopwatch]::Frequency)
        if ($remainingMs -le 0) { throw 'TOTAL_BENCHMARK_DEADLINE: hết 240 giây toàn bộ harness, không phải write ACK timeout.' }
        return [int][Math]::Min($Maximum, $remainingMs)
    }
    $arguments = $(if ($FullPathInstrumentation) {
        'shell am instrument -w -r -e class info.martinmarinov.dvbdriver.Tbs5530FullPathBenchmarkTest -e benchmark_directory ' +
            $directory + ' -e audit_mode ' + $AuditMode +
            ' info.martinmarinov.dvbdriver.phase29offline.test/androidx.test.runner.AndroidJUnitRunner'
    } else {
        'shell "CLASSPATH=' + $code + '/benchmark.jar app_process / info.tbs5530.approvalbenchmark.Main --isolated-benchmark ' + $directory + '"'
    })
    $process = Start-Adb $arguments
    $stdoutCapture = Start-TBS5530BoundedTextCapture $process.StandardOutput
    $stderrCapture = Start-TBS5530BoundedTextCapture $process.StandardError
    $childMonitor = [pscustomobject]@{ Process = $process; Stdout = $stdoutCapture; Stderr = $stderrCapture }
    if (-not $FullPathInstrumentation) {
        $readyDeadline = [Diagnostics.Stopwatch]::GetTimestamp() + (Remaining-HarnessMillis 5000) * [Diagnostics.Stopwatch]::Frequency / 1000
        while ($stdoutCapture.Snapshot().Text -notmatch [regex]::Escape('BENCHMARK_READY ' + $directory)) {
            if ((Get-TBS5530BenchmarkChildState $childMonitor) -cne 'RUNNING') { throw 'BENCHMARK_CHILD_FAILED: child kết thúc trước readiness.' }
            if ([Diagnostics.Stopwatch]::GetTimestamp() -ge $readyDeadline) { throw 'FAIL-STOP: Android Java harness không sẵn sàng.' }
            Start-Sleep -Milliseconds 10
        }
    }
    & (Join-Path $PSScriptRoot 'Watch-TBS5530-BootstrapApproval.ps1') -Benchmark -FullPathInstrumentation:$FullPathInstrumentation `
        -BenchmarkDirectory $directory -Serial $Serial -LedgerPath $LedgerPath -AdbPath $AdbPath `
        -DeadlineTimestamp $deadlineTimestamp -ExpectedIdentity $pinnedIdentity -HardTimeoutSeconds 240 -BenchmarkChild $childMonitor
    if (-not $process.WaitForExit((Remaining-HarnessMillis 3000)) -or $process.ExitCode -ne 0) {
        throw 'FAIL-STOP: Android harness không kết thúc thành công.'
    }
    $null = $stdoutCapture.Completion.Wait(1000); $null = $stderrCapture.Completion.Wait(1000)
    $output = $stdoutCapture.Snapshot().Text
    Write-Output $output
    if ((Get-TBS5530BenchmarkChildState $childMonitor) -ceq 'FAILED' -or
        ($FullPathInstrumentation -and $output -notmatch 'OK \(1 test\)')) {
        throw 'FAIL-STOP: instrumentation không xác nhận đúng một test PASS; giữ namespace evidence.'
    }
    # Watch has fsynced all observations and optional audit JSONL before this cleanup.
    Assert-PinnedTarget
    $cleanupArguments = $(if ($FullPathInstrumentation) {
        'shell run-as info.martinmarinov.dvbdriver.phase29offline rm -rf -- ' + $directory
    } else { 'shell rm -rf -- ' + $code + ' ' + $directory })
    Invoke-Bounded $cleanupArguments | Out-Null
    $complete = $true
    Write-TBS5530BenchmarkLedgerEntry -Path $LedgerPath -Entry @{ event = 'benchmark_wrapper_pass'; wrapper_run = $namespace
        benchmark_directory = $directory; audit_mode = $AuditMode; utc = [DateTime]::UtcNow.ToString('o') }
    Write-Output 'Đã xuất mẫu/audit vào ledger ngoài Git rồi xóa đúng namespace test; không mở app data production.'
} catch {
    $originalError = $_
    $metadata = @{ benchmark_directory = $directory; audit_mode = $AuditMode; namespace_preserved = (-not $complete)
        original_error_type = $originalError.Exception.GetType().FullName; original_error = $originalError.Exception.Message
        capture_character_limit = 262144; stdout = $null; stderr = $null; child_exit_code = $null }
    try {
        if ($null -ne $process) {
            if (-not $process.HasExited) { $process.Kill(); $null = $process.WaitForExit(1000) }
            if ($process.HasExited) { $metadata.child_exit_code = $process.ExitCode }
        }
        if ($null -ne $stdoutCapture) { $null = $stdoutCapture.Completion.Wait(1000); $metadata.stdout = $stdoutCapture.Snapshot() }
        if ($null -ne $stderrCapture) { $null = $stderrCapture.Completion.Wait(1000); $metadata.stderr = $stderrCapture.Snapshot() }
    } catch { $metadata.capture_error_type = $_.Exception.GetType().FullName }
    $originalError.Exception.Data['TBS5530BenchmarkFailure'] = $metadata
    try {
        Write-TBS5530BenchmarkLedgerEntry -Path $LedgerPath -Entry @{ event = 'benchmark_wrapper_fail'; wrapper_run = $namespace
            benchmark_directory = $directory; audit_mode = $AuditMode; metadata = $metadata; utc = [DateTime]::UtcNow.ToString('o') }
    } catch { $originalError.Exception.Data['TBS5530EvidenceLedgerError'] = $_.Exception.GetType().FullName }
    throw
} finally {
    if ($null -ne $process) {
        try { if (-not $process.HasExited) { $process.Kill() } }
        catch { Write-Warning 'Không dừng được child đã tạo; không reconnect hoặc cleanup namespace.' }
        finally { $process.Dispose() }
    }
    if (-not $complete) {
        $evidencePaths = $(if ($FullPathInstrumentation) { $directory } else { $code + ' và ' + $directory })
        Write-Warning ('Giữ evidence test tại ' + $evidencePaths + '; không reconnect/cleanup khi lỗi. Chỉ dừng ADB con do script tạo; harness tự hết hạn 240 giây.')
    }
}
