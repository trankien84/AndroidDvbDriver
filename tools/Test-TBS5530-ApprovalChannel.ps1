# Offline tests only: no ADB process, app data, USB, Gradle or external ledger file.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$tokens = $null; $errors = $null
foreach ($name in @('Run-TBS5530-ApprovalBenchmark.ps1', 'Watch-TBS5530-BootstrapApproval.ps1',
    'Get-TBS5530-ApprovalTarget.ps1', 'Build-TBS5530-ApprovalBenchmark.ps1', 'Tbs5530-ApprovalLedger.ps1', 'Tbs5530-ApprovalTiming.ps1', 'Initialize-TBS5530-HardwareLedger.ps1')) {
    [Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $name), [ref]$tokens, [ref]$errors) | Out-Null
    if ($errors.Count) { throw ('PowerShell parser failed: ' + $name) }
}
$runText = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Run-TBS5530-ApprovalBenchmark.ps1'))
$helperPath = Join-Path $PSScriptRoot 'Watch-TBS5530-BootstrapApproval.ps1'
$helper = Get-Command $helperPath
foreach ($parameter in @('Benchmark', 'BenchmarkDirectory', 'Serial', 'LedgerPath', 'AdbPath', 'DeadlineTimestamp', 'ExpectedIdentity', 'HardTimeoutSeconds')) {
    if (-not $helper.Parameters.ContainsKey($parameter) -or $runText -notmatch ('-' + $parameter + '\b')) {
        throw ('Wrapper/helper parameter mismatch: ' + $parameter)
    }
}
# Exercise real parameter binding; nonexistent ADB path must fail before any process/state.
try {
    & $helperPath -Benchmark -BenchmarkDirectory '/sdcard/Download/tbs5530-approval-benchmark/00000000-0000-0000-0000-000000000000' `
        -Serial '127.0.0.1:1234' -LedgerPath 'Z:\unused-ledger.jsonl' -AdbPath (Join-Path $root 'build\does-not-exist-adb.exe') `
        -DeadlineTimestamp 1 -ExpectedIdentity 'OnePlus|GM1900|30|00000000-0000-0000-0000-000000000000'
    throw 'Expected missing ADB preflight failure'
} catch {
    if ($_.Exception.Message -cne 'Không tìm thấy ADB.') { throw }
}
try {
    & (Join-Path $PSScriptRoot 'Initialize-TBS5530-HardwareLedger.ps1')
    throw 'Enrollment accepted without explicit historical-audit acknowledgment'
} catch {
    if ($_.Exception.Message -notmatch '^BLOCKED: cần xác nhận riêng việc lưu lịch sử') { throw }
}
. (Join-Path $PSScriptRoot 'Tbs5530-ApprovalTiming.ps1')
foreach ($case in @(
    @{ TotalDeadlineExpired = $true; TransportClosed = $true; Expected = 'TOTAL_BENCHMARK_DEADLINE' },
    @{ AndroidAckExpired = $true; Expected = 'ACK_TIMEOUT' },
    @{ TransportClosed = $true; Expected = 'TRANSPORT_LOST' },
    @{ ResponseTimedOut = $true; Expected = 'CHANNEL_RESPONSE_TIMEOUT' },
    @{ IdentityChanged = $true; Expected = 'ADB_IDENTITY_LOST' }
)) {
    $expected = $case.Expected; $case.Remove('Expected')
    if ((Get-TBS5530ChannelStopKind @case) -cne $expected) { throw ('Timing classification failed: ' + $expected) }
}
if ($runText -notmatch '240L' -or $runText -notmatch '-HardTimeoutSeconds 240') { throw 'Overall benchmark bound mismatch' }
$helperText = [IO.File]::ReadAllText($helperPath)
if ($helperText -notmatch '\[ValidateRange\(5, 300\)\]\[int\]\$HardTimeoutSeconds = 300' -or
    $helperText -notmatch '\$HardTimeoutSeconds = 240' -or $helperText -match '\$HardTimeoutSeconds -gt 60') { throw 'Production/benchmark total bounds mismatch' }
. (Join-Path $PSScriptRoot 'Tbs5530-ApprovalLedger.ps1')
$id = '11111111-1111-1111-1111-111111111111'
$stream = [IO.MemoryStream]::new()
try {
    Assert-TBS5530UnusedApprovalId $stream $id
    $bytes = [Text.Encoding]::UTF8.GetBytes('{"event":"NEW_APPROVAL_CONSUMED","new_approval_id":"' + $id + '"}' + "`n")
    $stream.Write($bytes, 0, $bytes.Length)
    foreach ($attempt in 1..2) {
        try { Assert-TBS5530UnusedApprovalId $stream $id; throw 'Duplicate ID accepted' }
        catch { if ($_.Exception.Message -notmatch '^BLOCKED: NewApprovalId đã xuất hiện') { throw } }
    }
    Assert-TBS5530UnusedApprovalId $stream '22222222-2222-2222-2222-222222222222'
    if ($stream.Length -ne $bytes.Length) { throw 'Ledger history changed' }
} finally { $stream.Dispose() }
$enrollment = [IO.MemoryStream]::new()
try {
    try { Assert-TBS5530UnusedApprovalId $enrollment $id -RequireEnrollment; throw 'Empty hardware ledger accepted' }
    catch { if ($_.Exception.Message -notmatch '^BLOCKED: ledger thiếu first enrollment') { throw } }
    Write-TBS5530PriorConsumedAudit -Stream $enrollment
    $beforeEnrollment = $enrollment.ToArray()
    try { Write-TBS5530PriorConsumedAudit -Stream $enrollment; throw 'Repeated enrollment accepted' }
    catch { if ($_.Exception.Message -notmatch '^BLOCKED: chỉ enroll stream mới') { throw } }
    Assert-TBS5530UnusedApprovalId $enrollment $id -RequireEnrollment
    if ([Convert]::ToBase64String($beforeEnrollment) -cne [Convert]::ToBase64String($enrollment.ToArray())) { throw 'Audit enrollment changed during validation' }
    if ((Get-TBS5530HardwareLedgerPath) -notlike '*\TBS5530\Phase29\hardware-attempts.jsonl') { throw 'Canonical hardware path mismatch' }
} finally { $enrollment.Dispose() }
$bad = [IO.MemoryStream]::new([Text.Encoding]::UTF8.GetBytes("partial{`n"))
try {
    try { Assert-TBS5530UnusedApprovalId $bad $id; throw 'Malformed ledger accepted' }
    catch { if ($_.Exception.Message -notmatch '^FAIL-STOP: ledger hỏng') { throw } }
} finally { $bad.Dispose() }
# Same shell body as the helper, with only OS identity inputs replaced by deterministic stubs.
$ast = [Management.Automation.Language.Parser]::ParseFile($helperPath, [ref]$tokens, [ref]$errors)
$shell = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.StringConstantExpressionAst] -and
    $node.Value.StartsWith("cd '__DIRECTORY__'") }, $true)[0].Value
$identity = 'OnePlus|GM1900|30|00000000-0000-0000-0000-000000000000'
$shell = $shell.Replace('__DIRECTORY__', '.').Replace('__RESULT_FILE__', 'tbs5530-diagnostic.json').Replace('__PINNED_IDENTITY__', $identity)
$shell = $shell.Replace('read -r boot < /proc/sys/kernel/random/boot_id', 'boot=00000000-0000-0000-0000-000000000000')
$stub = 'getprop() { case "$1" in ro.product.manufacturer) printf OnePlus;; ro.product.model) printf GM1900;; ro.build.version.sdk) printf 30;; esac; }; '
$directory = Join-Path $root ('build\approval-channel-test-' + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $directory | Out-Null
$process = $null
try {
    $auditPath = Join-Path $directory 'mock-canonical-hardware-attempts.jsonl'
    New-TBS5530PriorConsumedAuditFile -Path $auditPath
    $originalAudit = [IO.File]::ReadAllText($auditPath)
    try { New-TBS5530PriorConsumedAuditFile -Path $auditPath; throw 'Existing audit overwritten' }
    catch [IO.IOException] { }
    if ([IO.File]::ReadAllText($auditPath) -cne $originalAudit) { throw 'CreateNew changed existing audit' }
    foreach ($restart in 1..2) {
        $auditFile = [IO.FileStream]::new($auditPath, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read)
        try {
            if ($restart -eq 1) {
                Assert-TBS5530UnusedApprovalId $auditFile $id -RequireEnrollment
                $entry = [Text.Encoding]::UTF8.GetBytes('{"event":"NEW_APPROVAL_CONSUMED","new_approval_id":"' + $id + '"}' + "`n")
                $auditFile.Write($entry, 0, $entry.Length); $auditFile.Flush($true)
            }
            try { Assert-TBS5530UnusedApprovalId $auditFile $id -RequireEnrollment; throw 'Restart reused approval ID' }
            catch { if ($_.Exception.Message -notmatch '^BLOCKED: NewApprovalId đã xuất hiện') { throw } }
            Assert-TBS5530UnusedApprovalId $auditFile '22222222-2222-2222-2222-222222222222' -RequireEnrollment
        } finally { $auditFile.Dispose() }
    }
    $auditText = [IO.File]::ReadAllText($auditPath)
    if (-not $auditText.StartsWith($originalAudit) -or $originalAudit -match 'new_approval_id|phase29-session-approval|[A-Z]:\\|/sdcard|serial') {
        throw 'Enrollment mutated history or leaked grant/path/identity fields'
    }
    [IO.File]::WriteAllText((Join-Path $directory 'phase29-challenge.json'), '{"test":true}')
    $bash = 'C:\Program Files\Git\bin\bash.exe'
    if (-not (Test-Path -LiteralPath $bash)) { throw 'Git Bash missing for offline channel check' }
    $start = [Diagnostics.ProcessStartInfo]::new($bash)
    $start.WorkingDirectory = $directory; $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Local shell start failed' }
    $process.StandardInput.NewLine = "`n"; $stderr = $process.StandardError.ReadToEndAsync()
    function Read-LocalLine {
        $line = $process.StandardOutput.ReadLineAsync()
        if (-not $line.Wait(3000) -or $null -eq $line.Result) { throw 'Local shell protocol timeout' }
        return $line.Result
    }
    $process.StandardInput.WriteLine($stub + $shell); $process.StandardInput.WriteLine('POLL'); $process.StandardInput.Flush()
    foreach ($expected in @(('IDENTITY ' + $identity), 'NOW ', 'CHALLENGE {"test":true}', 'REQUEST ', 'OBSERVATION ', 'RESULT ', 'END')) {
        if (-not (Read-LocalLine).StartsWith($expected)) { throw ('Local shell framing failed at ' + $expected) }
    }
    $process.StandardInput.WriteLine('PUT 10000')
    $process.StandardInput.WriteLine('{"heartbeat_elapsed_ms":"@DEVICE_ACK@","host_ack_elapsed_ms":"@DEVICE_ACK@"}')
    $process.StandardInput.Flush()
    if ((Read-LocalLine) -notmatch '^STORED [0-9]+$') { throw 'Local atomic PUT failed' }
    $permit = [IO.File]::ReadAllText((Join-Path $directory 'phase29-session-approval.json')) | ConvertFrom-Json
    if ($permit.heartbeat_elapsed_ms -ne $permit.host_ack_elapsed_ms -or $permit.heartbeat_elapsed_ms -is [string]) { throw 'Numeric device timestamp substitution failed' }
    $reportEpoch = '33333333-3333-3333-3333-333333333333'
    $report = [ordered]@{ operation = 'DEBUG_ONE_SHOT_FX2'; state = 'LOADING'; run_epoch = $reportEpoch; pending = $true
        cleanup_pending = $false; cleanup_failure = 'NONE'; failure = 'NONE'
        native_call_started = 0; native_call_completed = 0; full_length_completed = 0; image_bytes_full_length = 0
        payload_bytes_full_length = 0; attempted = 0; completed = 0; transferred_bytes = 0; note = "escaped`ninside JSON string" }
    for ($ordinal = 1; $ordinal -le 132; $ordinal++) {
        $report.native_call_started = $ordinal - 1; $report.native_call_completed = $ordinal - 1
        [IO.File]::WriteAllText((Join-Path $directory 'tbs5530-diagnostic.new'), ($report | ConvertTo-Json -Depth 8))
        Move-Item -LiteralPath (Join-Path $directory 'tbs5530-diagnostic.new') -Destination (Join-Path $directory 'tbs5530-diagnostic.json') -Force
        $nonce = [Guid]::NewGuid().ToString()
        [IO.File]::WriteAllText((Join-Path $directory 'phase29-write-request.json'), ('{"ordinal":' + $ordinal + ',"request_nonce":"' + $nonce + '"}'))
        $process.StandardInput.WriteLine('POLL'); $process.StandardInput.Flush()
        foreach ($prefix in @('IDENTITY ', 'NOW ', 'CHALLENGE ', 'REQUEST ', 'OBSERVATION ', 'RESULT ', 'END')) {
            $line = Read-LocalLine
            if (-not $line.StartsWith($prefix)) { throw '132-loop builtin read framing failed' }
            if ($prefix -ceq 'REQUEST ' -and $line -notmatch [regex]::Escape($nonce)) { throw 'Single-line JSON visibility failed' }
            if ($prefix -ceq 'RESULT ') {
                $pretty = $line.Substring(7) | ConvertFrom-Json
                if ($pretty.native_call_started -ne $ordinal - 1 -or $pretty.note -cne $report.note) { throw 'Multiline JSON flatten changed report/string data' }
            }
        }
        $process.StandardInput.WriteLine('PUT 10000')
        $process.StandardInput.WriteLine(('{"authorized_ordinal":' + $ordinal + ',"request_nonce":"' + $nonce + '","heartbeat_elapsed_ms":"@DEVICE_ACK@","host_ack_elapsed_ms":"@DEVICE_ACK@"}'))
        $process.StandardInput.Flush()
        if ((Read-LocalLine) -notmatch '^STORED [0-9]+$') { throw '132-loop atomic ACK failed' }
        $ack = [IO.File]::ReadAllText((Join-Path $directory 'phase29-session-approval.json')) | ConvertFrom-Json
        if ($ack.authorized_ordinal -ne $ordinal -or $ack.request_nonce -cne $nonce -or
            $ack.heartbeat_elapsed_ms -is [string] -or $ack.host_ack_elapsed_ms -ne $ack.heartbeat_elapsed_ms) { throw '132-loop ACK content mismatch' }
    }
    $report.state = 'UNCERTAIN'; $report.pending = $false; $report.failure = 'PRE_NATIVE_ACK_TIMEOUT'
    $report.native_call_started = 23; $report.native_call_completed = 23; $report.full_length_completed = 23
    $report.image_bytes_full_length = 1344; $report.payload_bytes_full_length = 1346
    $report.attempted = 24; $report.completed = 23; $report.transferred_bytes = 1346
    $report['large_telemetry_fixture'] = ('x' * 70000)
    [IO.File]::WriteAllText((Join-Path $directory 'tbs5530-diagnostic.json'), ($report | ConvertTo-Json -Depth 8))
    $process.StandardInput.WriteLine('POLL'); $process.StandardInput.Flush()
    $terminal = $null
    foreach ($prefix in @('IDENTITY ', 'NOW ', 'CHALLENGE ', 'REQUEST ', 'OBSERVATION ', 'RESULT ', 'END')) {
        $line = Read-LocalLine
        if (-not $line.StartsWith($prefix)) { throw 'Pretty terminal report framing failed' }
        if ($prefix -ceq 'RESULT ') {
            $parsedTerminal = $line.Substring(7) | ConvertFrom-Json
            if ($parsedTerminal.large_telemetry_fixture.Length -ne 70000) { throw 'Report above 64 KiB was truncated' }
            $terminal = Get-TBS5530ProductionTerminalAudit -Report $parsedTerminal -ApprovalId $id -RunEpoch $reportEpoch -HostRun 'local-mock'
        }
    }
    if ($terminal.native_call_started -ne 23 -or $terminal.native_call_completed -ne 23 -or $terminal.full_length_completed -ne 23 -or
        $terminal.image_bytes_full_length -ne 1344 -or $terminal.legacy_runner_steps_attempted -ne 24 -or $terminal.legacy_counters_are_native_calls -ne $false -or
        $terminal.stop_reason -cne 'PRE_NATIVE_ACK_TIMEOUT' -or $terminal.cleanup_pending -ne $false -or $terminal.ContainsKey('note')) {
        throw 'Native/legacy/cleanup terminal audit labels incorrect'
    }
    # Exercise the actual helper ledger formatter without invoking the helper or any ADB operation.
    $formatter = $ast.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Write-Ledger'}, $true)[0]
    . ([scriptblock]::Create($formatter.Extent.Text))
    $Benchmark = $false
    $ledger = [IO.FileStream]::new((Join-Path $directory 'helper-mode-audit.jsonl'), [IO.FileMode]::CreateNew, [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read)
    $writer = [IO.StreamWriter]::new($ledger, [Text.UTF8Encoding]::new($false), 1024, $true)
    try {
        foreach ($event in @('benchmark_start', 'authorize_benchmark_ordinal', 'benchmark_fail')) {
            Write-Ledger @{ event = $event; hardware_enabled = $false; challenge = 'must-not-leak'; install = 'must-not-leak' }
        }
        Write-Ledger $terminal
    } finally { $writer.Dispose(); $ledger.Dispose() }
    $auditRecords = @([IO.File]::ReadAllLines((Join-Path $directory 'helper-mode-audit.jsonl')) | ForEach-Object { $_ | ConvertFrom-Json })
    foreach ($record in $auditRecords) {
        if ($record.mode -cne 'PRODUCTION_AUTHORIZATION' -or $record.helper_usb_calls -ne 0 -or
            $record.PSObject.Properties['hardware_enabled'] -or $record.PSObject.Properties['challenge'] -or $record.PSObject.Properties['install']) { throw 'Production mode or privacy ledger labeling failed' }
    }
    if ($auditRecords[0].event -cne 'production_authorization_start' -or $auditRecords[1].event -cne 'production_authorization_ordinal_authorized' -or
        $auditRecords[2].event -cne 'production_authorization_fail') { throw 'Production event names still labeled benchmark' }
    $process.StandardInput.WriteLine('QUIT'); $process.StandardInput.Close()
    if (-not $process.WaitForExit(3000) -or $process.ExitCode -ne 0) { throw 'Local shell exit failed' }
    $before = [IO.File]::ReadAllText((Join-Path $directory 'phase29-session-approval.json'))
    $process.Dispose(); $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Identity rejection shell start failed' }
    $process.StandardInput.NewLine = "`n"; $stderr = $process.StandardError.ReadToEndAsync()
    $process.StandardInput.WriteLine($stub.Replace('printf OnePlus', 'printf WrongManufacturer') + $shell)
    $process.StandardInput.WriteLine('POLL'); $process.StandardInput.Flush()
    if ((Read-LocalLine) -cne 'ERROR identity' -or -not $process.WaitForExit(3000) -or $process.ExitCode -eq 0) {
        throw 'Changed manufacturer did not fail-stop'
    }
    if ([IO.File]::ReadAllText((Join-Path $directory 'phase29-session-approval.json')) -cne $before) { throw 'Identity rejection changed permit' }
    $process.Dispose(); $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Bound rejection shell start failed' }
    $process.StandardInput.NewLine = "`n"; $stderr = $process.StandardError.ReadToEndAsync()
    $oversizedLines = '{' + "`n" + ((1..270 | ForEach-Object { '  "fixture' + $_ + '":"' + ('x' * 1024) + '",' }) -join "`n") + "`n" + '"tail":0' + "`n}"
    [IO.File]::WriteAllText((Join-Path $directory 'tbs5530-diagnostic.json'), $oversizedLines)
    $process.StandardInput.WriteLine($stub + $shell); $process.StandardInput.WriteLine('POLL'); $process.StandardInput.Flush()
    foreach ($prefix in @('IDENTITY ', 'NOW ', 'CHALLENGE ', 'REQUEST ', 'OBSERVATION ')) {
        if (-not (Read-LocalLine).StartsWith($prefix)) { throw 'Bound rejection framing failed' }
    }
    if ((Read-LocalLine) -cne 'ERROR metadata_size' -or -not $process.WaitForExit(3000) -or $process.ExitCode -eq 0) { throw 'Oversized report silently truncated/accepted' }
} finally {
    if ($null -ne $process) {
        if (-not $process.HasExited) { $process.Kill($true); $process.WaitForExit(3000) | Out-Null }
        $process.Dispose()
    }
    try { Remove-Item -LiteralPath $directory -Recurse -Force }
    catch { Write-Warning ('Offline test scratch cleanup pending: ' + $directory) }
}
'PASS: parser, wrapper/helper binding, deadline/ACK/transport classification, enrollment CreateNew/fsync/no-overwrite, duplicate/restart/corrupt canonical-ledger mocks, 132 local builtin-JSON/atomic-ACK loops and pinned identity rejection. No production enrollment or ADB/Android latency claim.'
