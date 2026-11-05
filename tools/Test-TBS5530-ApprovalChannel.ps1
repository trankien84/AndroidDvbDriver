# Offline tests only: no ADB process, app data, USB, Gradle or external ledger file.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$testWatch = [Diagnostics.Stopwatch]::StartNew()
$mockChecks = 0
$lastHeartbeat = $null
$heartbeatWrites = 0
$fileIoChildInvocations = 0
$runAsInvocations = 0
$ioPackage = ''
function Assert-MockFailure([scriptblock]$Action, [string]$Expected) {
    try { & $Action; throw 'Mock unexpectedly succeeded' }
    catch { if ($_.Exception.Message -notmatch $Expected) { throw } }
    $script:mockChecks++
}
$tokens = $null; $errors = $null
foreach ($name in @('Run-TBS5530-ApprovalBenchmark.ps1', 'Watch-TBS5530-BootstrapApproval.ps1',
    'Get-TBS5530-ApprovalTarget.ps1', 'Build-TBS5530-ApprovalBenchmark.ps1', 'Tbs5530-ApprovalLedger.ps1', 'Tbs5530-ApprovalTiming.ps1', 'Initialize-TBS5530-HardwareLedger.ps1')) {
    [Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $name), [ref]$tokens, [ref]$errors) | Out-Null
    if ($errors.Count) { throw ('PowerShell parser failed: ' + $name) }
}
$runText = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Run-TBS5530-ApprovalBenchmark.ps1'))
$helperPath = Join-Path $PSScriptRoot 'Watch-TBS5530-BootstrapApproval.ps1'
$helper = Get-Command $helperPath
foreach ($parameter in @('Benchmark', 'FullPathInstrumentation', 'BenchmarkChild', 'BenchmarkDirectory', 'Serial', 'LedgerPath', 'AdbPath', 'DeadlineTimestamp', 'ExpectedIdentity', 'HardTimeoutSeconds')) {
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
$legacyStored = ConvertFrom-TBS5530StoredFrame 'STORED 100'
$measuredStored = ConvertFrom-TBS5530StoredFrame 'STORED 105 HOST_ACK_PERSISTED 100'
if ($legacyStored.host_ack_persisted_status -cne 'NOT_MEASURED' -or $null -ne $legacyStored.host_ack_persisted_elapsed_ms -or
    $measuredStored.host_ack_elapsed_ms -ne 105 -or $measuredStored.host_ack_persisted_elapsed_ms -ne 100 -or
    $measuredStored.host_ack_persisted_scope -cne 'TEMP_WRITE_RENAME_COMPLETE_NO_EXPLICIT_FSYNC' -or
    $measuredStored.host_ack_durable_persisted_status -cne 'NOT_MEASURED') {
    throw 'Persist marker changed legacy ACK timestamp or claimed unmeasured fsync durability'
}
foreach ($line in @('STORED 1 HOST_ACK_PERSISTED -1', 'STORED 1 EXTRA 2', 'STORED 1 HOST_ACK_PERSISTED 999999999999999999999')) {
    Assert-MockFailure { ConvertFrom-TBS5530StoredFrame $line } '^FAIL-STOP:'
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
foreach ($badLine in @(
    ('{"event":"NEW_APPROVAL_CONSUMED","new_approval_id":"' + $id + '","new_approval_id":"22222222-2222-2222-2222-222222222222"}'),
    ('{"event":"NEW_APPROVAL_CONSUMED","new_approval_id":"' + $id + '","new\u005fapproval_id":"22222222-2222-2222-2222-222222222222"}'),
    '{"event":"NEW_APPROVAL_CONSUMED","new_approval_id":"invalid"}',
    '[]', 'null', '{"event":"NEW_APPROVAL_CONSUMED"}'
)) {
    $broken = [IO.MemoryStream]::new([Text.Encoding]::UTF8.GetBytes($badLine + "`n"))
    try { Assert-MockFailure { Assert-TBS5530UnusedApprovalId $broken $id } '^FAIL-STOP:' }
    finally { $broken.Dispose() }
}
$broken = [IO.MemoryStream]::new([Text.Encoding]::UTF8.GetBytes('{"event":"intact_json_but_no_newline"}'))
try { Assert-MockFailure { Assert-TBS5530UnusedApprovalId $broken $id } '^FAIL-STOP: ledger thiếu LF' }
finally { $broken.Dispose() }
foreach ($tail in @(
    '{"event":"HARDWARE_LEDGER_ENROLLED"}', '{"event":"PRIOR_ATTEMPT_CONSUMED"}', '{"event":"unknown"}',
    ('{"event":"NEW_APPROVAL_CONSUMED","new_approval_id":"22222222-2222-2222-2222-222222222222"}' + "`n" +
     '{"event":"NEW_APPROVAL_CONSUMED","new_approval_id":"22222222-2222-2222-2222-222222222222"}')
)) {
    $broken = [IO.MemoryStream]::new()
    try {
        Write-TBS5530PriorConsumedAudit $broken
        $bytes = [Text.Encoding]::UTF8.GetBytes($tail + "`n"); $broken.Write($bytes, 0, $bytes.Length)
        Assert-MockFailure { Assert-TBS5530UnusedApprovalId $broken $id -RequireEnrollment } '^FAIL-STOP:'
    } finally { $broken.Dispose() }
}
$epoch = '33333333-3333-3333-3333-333333333333'
$preparedReport = [pscustomobject]@{ operation = 'DEBUG_ONE_SHOT_FX2'; state = 'PREPARED'; run_epoch = $epoch; pending = $false
    cleanup_pending = $false; cleanup_failure = 'NONE'; failure = 'NONE'; boundary_events = @()
    native_call_started = 0; native_call_completed = 0; full_length_completed = 0; image_bytes_full_length = 0
    payload_bytes_full_length = 0; attempted = 0; completed = 0; transferred_bytes = 0 }
$fastTerminal = $preparedReport | ConvertTo-Json -Depth 8 | ConvertFrom-Json
$fastTerminal.state = 'FAULTED'; $fastTerminal.failure = 'USB_DETACHED'
$fastTerminal.boundary_events = @('ordinal=0 monotonic_ns=1 STOP reason=USB_DETACHED')
$revision = Get-TBS5530ProductionReportRevision $preparedReport
if ((Get-TBS5530ProductionReportDisposition $preparedReport $epoch $revision) -cne 'PREPARED' -or
    (Get-TBS5530ProductionReportDisposition $fastTerminal $epoch $revision) -cne 'TERMINAL' -or
    (Get-TBS5530ProductionReportDisposition $fastTerminal '44444444-4444-4444-4444-444444444444') -cne 'OTHER_EPOCH') { throw 'Report epoch/revision classification failed' }
$fastAudit = Get-TBS5530ProductionTerminalAudit $fastTerminal $id $epoch 'local-mock'
if ($fastAudit.terminal_kind -cne 'PRE_NATIVE_STOP' -or $fastAudit.firmware_pass -ne $false -or
    $fastAudit.firmware_upload_completed -ne $false -or $fastAudit.ready -ne $false) { throw 'Preflight stop misclassified as firmware PASS' }
Assert-MockFailure { Get-TBS5530ProductionReportDisposition $fastTerminal $epoch (Get-TBS5530ProductionReportRevision $fastTerminal) } '^OBSERVATION_FAILED:'
foreach ($mutate in @(
    { $case.pending = 'false' }, { $case.state = 'LOADING' }, { $case.state = 'OPERATIONAL_CANDIDATE' },
    { $case.native_call_started = 1 }, { $case.full_length_completed = -1 }, { $case.boundary_events = @() },
    { $case.run_epoch = '44444444-4444-4444-4444-444444444444' }
)) {
    $case = $fastTerminal | ConvertTo-Json -Depth 8 | ConvertFrom-Json
    . $mutate
    Assert-MockFailure { Get-TBS5530ProductionTerminalAudit $case $id $epoch 'local-mock' } '^OBSERVATION_FAILED:'
}
. (Join-Path $PSScriptRoot 'Get-TBS5530-ApprovalTarget.ps1')
Assert-MockFailure { Get-TBS5530ApprovalTarget -AdbPath (Get-Process -Id $PID).Path -Serial '127.0.0.1:1234' -DeadlineTimestamp 1 } 'hết hạn trước identity preflight'
# Same shell body as the helper, with only OS identity inputs replaced by deterministic stubs.
$ast = [Management.Automation.Language.Parser]::ParseFile($helperPath, [ref]$tokens, [ref]$errors)
function New-LocalCapture([string]$Text, [int]$Maximum = 262144) {
    $reader = [IO.StreamReader]::new([IO.MemoryStream]::new([Text.Encoding]::UTF8.GetBytes($Text)))
    try {
        $capture = Start-TBS5530BoundedTextCapture -Reader $reader -MaximumCharacters $Maximum
        if (-not $capture.Completion.Wait(3000)) { throw 'Local capture did not complete' }
        return $capture
    } finally { $reader.Dispose() }
}
$bounded = (New-LocalCapture ('x' * 300000) 262144).Snapshot()
if ($bounded.Text.Length -ne 262144 -or $bounded.CharactersSeen -ne 300000 -or -not $bounded.Truncated -or -not $bounded.Complete) {
    throw 'Bounded capture lost its truncation/completion metadata'
}
foreach ($text in @('FAILURES!!!', 'INSTRUMENTATION_FAILED: synthetic', 'INSTRUMENTATION_STATUS_CODE: -2',
    'INSTRUMENTATION_CODE: 0', 'shortMsg=synthetic')) {
    $child = [pscustomobject]@{ Process = [pscustomobject]@{ HasExited = $true; ExitCode = 0 }; Stdout = (New-LocalCapture $text) }
    if ((Get-TBS5530BenchmarkChildState $child) -cne 'FAILED') { throw 'ADB exit 0 hid instrumentation failure' }
}
$child = [pscustomobject]@{ Process = [pscustomobject]@{ HasExited = $true; ExitCode = 0 }
    Stdout = (New-LocalCapture "OK (1 test)`nINSTRUMENTATION_CODE: -1") }
if ((Get-TBS5530BenchmarkChildState $child) -cne 'FINISHED') { throw 'Successful child was misclassified' }
& {
    $readerNode = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Read-ChannelLine' }, $true)[0]
    . ([scriptblock]::Create($readerNode.Extent.Text))
    $failureEvidenceMode = $false; $HardTimeoutSeconds = 300; $DeadlineTimestamp = 0L
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $stdout = [pscustomobject]@{ Line = 'ERROR clock_read' }
    $stdout | Add-Member ScriptMethod ReadLineAsync { [Threading.Tasks.Task]::FromResult[string]($this.Line) }
    $process = [pscustomobject]@{ StandardOutput = $stdout }
    function Assert-Time { }
    function Stop-Channel([string]$kind) { throw ($kind + ': channel fixture') }
    Assert-MockFailure { Read-ChannelLine } '^DEVICE_CLOCK_UNAVAILABLE:'
    $stdout.Line = 'ERROR file_io_permission'
    Assert-MockFailure { Read-ChannelLine } '^FILE_IO_PERMISSION:'
    $stdout.Line = 'ERROR channel_read_error'
    Assert-MockFailure { Read-ChannelLine } '^CHANNEL_READ_ERROR:'
    $stdout.Line = 'ERROR ack_deadline'
    Assert-MockFailure { Read-ChannelLine } '^ACK_TIMEOUT:'
}
$assertTimeNode = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Assert-Time' }, $true)[0]
& {
    $Benchmark = $true; $failureEvidenceMode = $false; $waitingForChallenge = $true
    $BenchmarkChild = [pscustomobject]@{ Process = [pscustomobject]@{ HasExited = $true; ExitCode = 0 }
        Stdout = (New-LocalCapture 'FAILURES!!!') }
    function Stop-Channel([string]$kind) { throw ($kind + ': offline child') }
    function Total-DeadlineExpired { throw 'Child failure must be checked before waiting for total deadline' }
    . ([scriptblock]::Create($assertTimeNode.Extent.Text))
    Assert-MockFailure { Assert-Time } '^BENCHMARK_CHILD_FAILED:'
}
$helperTry = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.TryStatementAst] -and
    $node.Body.Extent.Text.Contains('$startEntry =') }, $true)[0]
& {
    $run = 'mock'; $acknowledged = 0; $observed = 0; $watch = [Diagnostics.Stopwatch]::StartNew(); $HardTimeoutSeconds = 240
    function Total-DeadlineExpired { $false }
    function Write-Ledger { throw 'Injected ledger failure' }
    function Save-BenchmarkFailureEvidence { throw 'Injected export failure' }
    $original = [IO.IOException]::new('BENCHMARK_CHILD_FAILED: original failure')
    try { . ([scriptblock]::Create('try { throw $original } catch ' + $helperTry.CatchClauses[0].Body.Extent.Text)) }
    catch {
        if (-not [object]::ReferenceEquals($_.Exception, $original) -or
            -not $_.Exception.Data.Contains('TBS5530FailureLedgerError') -or -not $_.Exception.Data.Contains('TBS5530FailureExportError')) {
            throw 'Helper diagnostics replaced original failure'
        }
    }
}
& {
    $Benchmark = $true; $BenchmarkDirectory = '/isolated-mock'; $run = 'failure-export'; $snapshot = @{ RESULT = '' }
    $script:channelSynchronized = $true; $script:failureEvidenceMode = $false; $script:diagnosticDeadline = 0L
    $calls = [Collections.Generic.List[string]]::new(); $entries = [Collections.Generic.List[object]]::new()
    $input = [pscustomobject]@{ Calls = $calls }
    $input | Add-Member ScriptMethod WriteLine { param($line) $this.Calls.Add([string]$line) }
    $input | Add-Member ScriptMethod Flush { }
    $process = [pscustomobject]@{ HasExited = $false; StandardInput = $input }
    $responses = [Collections.Generic.Queue[string]]::new()
    foreach ($line in @('EVIDENCE_BEGIN benchmark-observations.jsonl', 'EVIDENCE malformed-json', 'EVIDENCE_END 1',
        'EVIDENCE_BEGIN benchmark-audit.jsonl', 'EVIDENCE_END 0',
        'EVIDENCE_BEGIN benchmark-result.json', 'EVIDENCE {"status":"FAIL","usb_calls":0}', 'EVIDENCE_END 1')) { $responses.Enqueue($line) }
    function Assert-Time { }
    function Read-ChannelLine { $responses.Dequeue() }
    function Stop-Channel([string]$kind) { throw ($kind + ': mock evidence') }
    function Write-Ledger($entry) { $entries.Add($entry) }
    foreach ($name in @('Export-BenchmarkEvidence', 'Save-BenchmarkFailureEvidence')) {
        $node = $ast.FindAll({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name }, $true)[0]
        . ([scriptblock]::Create($node.Extent.Text))
    }
    Save-BenchmarkFailureEvidence
    if ($calls.Count -ne 3 -or @($calls | Where-Object { $_ -notlike 'EXPORT *' }).Count -or
        $entries.Count -ne 3 -or -not $entries[0].evidence.malformed_json -or
        $entries[-1].status -cne 'EXPORTED' -or $entries[-1].files['benchmark-audit.jsonl'].records -ne 0 -or
        $entries[-1].creates_permit -ne $false) { throw 'Failure export lost evidence or sent authorization' }
    $script:channelSynchronized = $false
    Save-BenchmarkFailureEvidence
    if ($calls.Count -ne 3 -or $entries[-1].status -cne 'SKIPPED_NO_SYNCHRONIZED_CHANNEL') { throw 'Unsynchronized channel was reused/reconnected' }
    $Benchmark = $false
    Save-BenchmarkFailureEvidence
    if ($entries.Count -ne 4) { throw 'Production used benchmark failure export' }
    $script:failureEvidenceMode = $false
}
foreach ($name in @('Test-PriorApprovalArtifact', 'Same-Binding', 'Select-CurrentProductionArtifacts')) {
    $node = $ast.FindAll({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name }, $true)[0]
    . ([scriptblock]::Create($node.Extent.Text))
}
if ($helperText -match 'stamp=\$minimum|host_seen_elapsed_ms\s*=\s*\[Math\]::Max|clamped_to_request') {
    throw 'Transport timestamps must never be clamped or labelled as adjusted values'
}
$selectorNode = $ast.FindAll({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq 'Select-CurrentProductionArtifacts' }, $true)[0]
if (@($selectorNode.FindAll({ param($n) $n -is [Management.Automation.Language.ContinueStatementAst] }, $true)).Count) {
    throw 'Artifact selector must return normally to the common heartbeat path'
}
$bindingChallenge = [pscustomobject]@{ approval_protocol = 2; challenge = '55555555-5555-5555-5555-555555555555'; run_epoch = $epoch
    install = '66666666-6666-6666-6666-666666666666'; upload_epoch = 1; attachment_hash = ('a' * 64)
    plan = 'TBS_SOURCE_DUAL_RESET'; sha256 = 'b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc'
    hardware_enabled = $false; approval_mode = 'NEW_AUDITED_ATTEMPT_REQUIRED'; legacy_consumed = $true; created_elapsed_ms = 0 }
if ((Get-TBS5530ProductionReportDisposition ([pscustomobject]@{ operation = 'RAW_READ_AND_CLOSED' }) $epoch) -cne 'OTHER_OPERATION') {
    throw 'RAW report was incorrectly required to carry production bootstrap binding'
}
$legacyRequest = '{"challenge":"77777777-7777-7777-7777-777777777777","ordinal":24}' | ConvertFrom-Json
if (-not (Test-PriorApprovalArtifact $legacyRequest $bindingChallenge $true 'REQUEST') -or
    -not (Test-PriorApprovalArtifact ([pscustomobject]@{ challenge = $legacyRequest.challenge; run_epoch = 0 }) $bindingChallenge $true 'REQUEST') -or
    -not (Test-PriorApprovalArtifact ([pscustomobject]@{ run_epoch = '77777777-7777-7777-7777-777777777777'; ordinal = 23 }) $bindingChallenge $true 'OBSERVATION')) {
    throw 'Prior request challenge / run-only observation was not classified before required fields'
}
Assert-MockFailure { Test-PriorApprovalArtifact ([pscustomobject]@{ run_epoch = '77777777-7777-7777-7777-777777777777'; ordinal = 23 }) $bindingChallenge $true 'REQUEST' } '^INVALID_REQUEST_BINDING:'
Assert-MockFailure { Same-Binding $legacyRequest $bindingChallenge } '^INVALID_REQUEST_BINDING:.*approval_protocol'
Assert-MockFailure { Test-PriorApprovalArtifact $legacyRequest $bindingChallenge $false 'REQUEST' } '^INVALID_REQUEST_BINDING:'
$currentMalformed = ('{"challenge":"' + $bindingChallenge.challenge + '","ordinal":1}') | ConvertFrom-Json
Assert-MockFailure { Test-PriorApprovalArtifact $currentMalformed $bindingChallenge $true 'REQUEST' } '^INVALID_REQUEST_BINDING:'
$currentMissingProtocol = ('{"challenge":"' + $bindingChallenge.challenge + '","run_epoch":"' + $epoch + '","ordinal":1}') | ConvertFrom-Json
Assert-MockFailure { Same-Binding $currentMissingProtocol $bindingChallenge } '^INVALID_REQUEST_BINDING:.*approval_protocol'
Assert-MockFailure { Test-PriorApprovalArtifact ([pscustomobject]@{ ordinal = 1 }) $bindingChallenge $true 'REQUEST' } '^INVALID_REQUEST_BINDING:'
Assert-MockFailure { Test-PriorApprovalArtifact ([pscustomobject]@{ challenge = $bindingChallenge.challenge; run_epoch = '77777777-7777-7777-7777-777777777777' }) $bindingChallenge $true 'REQUEST' } '^INVALID_REQUEST_BINDING:'
# Execute the real production loop with in-memory transport, never invoking the helper entry point.
$productionLoop = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.WhileStatementAst] -and
    $node.Extent.Text.Contains('Get-TBS5530ProductionReportDisposition') }, $true)
if ($productionLoop.Count -ne 1) { throw 'Production loop extraction is ambiguous' }
& {
    $Benchmark = $false; $NewApprovalId = $id; $run = 'offline-run'; $deadline = 300000
    $challenge = [pscustomobject]@{ run_epoch = $epoch; created_elapsed_ms = 0 }
    $pinnedIdentity = 'mock-pinned'; $acknowledged = 0; $observed = 0; $permit = @{}
    $baselineRevision = Get-TBS5530ProductionReportRevision $preparedReport
    $snapshot = @{ IDENTITY = $pinnedIdentity; CHALLENGE = '{}'; RESULT = ($preparedReport | ConvertTo-Json -Depth 8)
        NOW = '1'; REQUEST = ''; OBSERVATION = '' }
    $calls = [Collections.Generic.List[string]]::new(); $entries = [Collections.Generic.List[object]]::new()
    $input = [pscustomobject]@{ Calls = $calls }
    $input | Add-Member ScriptMethod WriteLine { param($line) $this.Calls.Add([string]$line) }
    $input | Add-Member ScriptMethod Flush { }
    $process = [pscustomobject]@{ StandardInput = $input }
    function Assert-Time { }
    function Same-Binding { }
    function Read-ChannelLine { 'STORED 1' }
    function Stop-Channel([string]$kind) { throw ($kind + ': offline stop') }
    function Write-Ledger($entry) { $entries.Add($entry) }
    function Snapshot {
        return @{ IDENTITY = $pinnedIdentity; CHALLENGE = '{}'; RESULT = ($fastTerminal | ConvertTo-Json -Depth 8)
            NOW = '999999'; REQUEST = '{"ordinal":1,"ack_deadline_elapsed_ms":2}'; OBSERVATION = '' }
    }
    . ([scriptblock]::Create($productionLoop[0].Extent.Text)) | Out-Null
    if ($entries.Count -ne 1 -or $entries[0].terminal_kind -cne 'PRE_NATIVE_STOP' -or
        $entries[0].native_call_started -ne 0 -or $acknowledged -ne 0 -or $calls.Count -ne 2 -or
        $calls[0] -cne 'PUT 0 UNTIL 300000') { throw 'Fast terminal required pending/ACK or emitted another PUT' }
}
& {
    $Benchmark = $true; $FullPathInstrumentation = $true; $run = 'offline-benchmark'; $deadline = 300000
    $challenge = [pscustomobject]@{ run_epoch = $epoch; created_elapsed_ms = 0 }; $pinnedIdentity = 'mock-pinned'
    $acknowledged = 132; $observed = 131; $latencies = [Collections.Generic.List[long]]::new()
    $entries = [Collections.Generic.List[object]]::new(); $exports = [Collections.Generic.List[string]]::new()
    $snapshot = @{ IDENTITY = $pinnedIdentity; CHALLENGE = '{}'; NOW = '9000'; REQUEST = '{"ordinal":132,"ack_deadline_elapsed_ms":1}'
        OBSERVATION = ('{"run_epoch":"' + $epoch + '","ordinal":132,"request_elapsed_ms":10,"ack_observed_elapsed_ms":15}')
        RESULT = '{"status":"PASS","roundtrips":132,"native_fake_calls":132,"usb_calls":0,"full_length":132}' }
    function Assert-Time { }
    function Same-Binding { }
    function Write-Ledger($entry) { $entries.Add($entry) }
    function Export-BenchmarkEvidence($name) { $exports.Add($name); if ($name -ceq 'benchmark-observations.jsonl') { 132 } else { 1 } }
    function Stop-Channel([string]$kind) { throw ($kind + ': offline benchmark') }
    . ([scriptblock]::Create($productionLoop[0].Extent.Text))
    if ($observed -ne 132 -or $exports.Count -ne 2 -or $entries[-1].event -cne 'benchmark_android_result') {
        throw 'Completed benchmark was rejected by stale request deadline'
    }
}
& {
    $Benchmark = $true; $FullPathInstrumentation = $false; $run = 'measurement-mock'; $deadline = 300000
    $challenge = $bindingChallenge | ConvertTo-Json | ConvertFrom-Json; $pinnedIdentity = 'mock-pinned'
    $acknowledged = 0; $observed = 0; $lastNonce = ''; $permit = @{}; $latencies = [Collections.Generic.List[long]]::new()
    $entries = [Collections.Generic.List[object]]::new(); $calls = [Collections.Generic.List[string]]::new()
    $input = [pscustomobject]@{ Calls = $calls }
    $input | Add-Member ScriptMethod WriteLine { param($line) $this.Calls.Add([string]$line) }
    $input | Add-Member ScriptMethod Flush { }
    $process = [pscustomobject]@{ StandardInput = $input }
    $request = $challenge | ConvertTo-Json | ConvertFrom-Json
    foreach ($pair in @{ ordinal = 1; request_nonce = '11111111-1111-1111-1111-111111111111'; request_elapsed_ms = 15; ack_deadline_elapsed_ms = 1515 }.GetEnumerator()) {
        $request | Add-Member NoteProperty $pair.Key $pair.Value
    }
    $snapshot = @{ IDENTITY = $pinnedIdentity; CHALLENGE = '{}'; NOW = '10'; OBSERVATION = ''; RESULT = ''
        REQUEST = ($request | ConvertTo-Json -Compress) }
    function Assert-Time { }
    function Same-Binding { }
    function Read-ChannelLine { 'STORED 20 HOST_ACK_PERSISTED 30' }
    function Write-Ledger($entry) { $entries.Add($entry) }
    function Snapshot { @{ IDENTITY = $pinnedIdentity; CHALLENGE = '{}'; NOW = '21'; OBSERVATION = ''; REQUEST = ''
        RESULT = '{"status":"FAIL","failure_kind":"ACK_TIMEOUT"}' } }
    function Stop-Channel([string]$kind) { throw ($kind + ': bounded loop stop') }
    Assert-MockFailure { . ([scriptblock]::Create($productionLoop[0].Extent.Text)) } '^ACK_TIMEOUT:'
    if ($entries.Count -ne 2 -or $entries[0].event -cne 'authorize_benchmark_ordinal' -or
        $entries[1].event -cne 'benchmark_host_ack_persisted' -or $entries[1].ordinal -ne 1 -or
        $entries[1].request_elapsed_ms -ne 15 -or $entries[1].host_ack_elapsed_ms -ne 20 -or
        $entries[1].host_ack_persisted_elapsed_ms -ne 30 -or $calls.Count -ne 2 -or
        $calls[0] -cne 'PUT 15 UNTIL 1515 MEASURE_PERSISTED' -or $calls[1] -match 'host_ack_persisted' -or
        ($calls[1] | ConvertFrom-Json).host_seen_elapsed_ms -ne 10) {
        throw 'Actual host loop failed to append completion measurement separately from immutable authorization/permit'
    }
}
$wrapperAst = [Management.Automation.Language.Parser]::ParseInput($runText, [ref]$tokens, [ref]$errors)
$wrapperTry = $wrapperAst.FindAll({ param($node) $node -is [Management.Automation.Language.TryStatementAst] -and
    $node.Body.Extent.Text.Contains('$process = Start-Adb $arguments') }, $true)[0]
& {
    $directory = '/isolated-mock'; $namespace = 'mock'; $AuditMode = 'baseline'; $complete = $false; $LedgerPath = 'unused'
    $process = [pscustomobject]@{ HasExited = $true; ExitCode = 0 }
    $stdoutCapture = New-LocalCapture 'FAILURES!!! test setup assertion'
    $stderrCapture = New-LocalCapture 'synthetic stderr'
    $entries = [Collections.Generic.List[object]]::new()
    function Write-TBS5530BenchmarkLedgerEntry($Path, $Entry) { $entries.Add($Entry) }
    $original = [IO.IOException]::new('BENCHMARK_CHILD_FAILED: original wrapper failure')
    try { . ([scriptblock]::Create('try { throw $original } catch ' + $wrapperTry.CatchClauses[0].Body.Extent.Text)) }
    catch {
        if (-not [object]::ReferenceEquals($_.Exception, $original)) { throw 'Wrapper replaced original error' }
        $metadata = $_.Exception.Data['TBS5530BenchmarkFailure']
        if ($metadata.stdout.Text -notmatch 'FAILURES!!!' -or $metadata.stderr.Text -cne 'synthetic stderr' -or
            $metadata.child_exit_code -ne 0 -or -not $metadata.namespace_preserved -or
            $entries.Count -ne 1 -or $entries[0].event -cne 'benchmark_wrapper_fail') { throw 'Wrapper lost bounded child output/metadata' }
    }
}
$argumentNode = $wrapperAst.FindAll({ param($node) $node -is [Management.Automation.Language.AssignmentStatementAst] -and
    $node.Left.Extent.Text -ceq '$arguments' }, $true)[0]
& {
    $FullPathInstrumentation = $true; $AuditMode = 'verified'; $namespace = $id
    $code = '/sdcard/Download/tbs5530-approval-benchmark-code/' + $namespace
    $directoryNode = $wrapperAst.FindAll({ param($node) $node -is [Management.Automation.Language.AssignmentStatementAst] -and
        $node.Left.Extent.Text -ceq '$directory' }, $true)[0]
    $cleanupNode = $wrapperAst.FindAll({ param($node) $node -is [Management.Automation.Language.AssignmentStatementAst] -and
        $node.Left.Extent.Text -ceq '$cleanupArguments' }, $true)[0]
    . ([scriptblock]::Create($directoryNode.Extent.Text))
    if ($directory -cne ('/data/user/0/info.martinmarinov.dvbdriver.phase29offline/files/tbs5530-approval-benchmark/' + $namespace)) {
        throw 'Instrumentation namespace is not app-private under the exact isolated package'
    }
    . ([scriptblock]::Create($argumentNode.Extent.Text))
    if ($arguments -cne ('shell am instrument -w -r -e class info.martinmarinov.dvbdriver.Tbs5530FullPathBenchmarkTest -e benchmark_directory ' +
        $directory + ' -e audit_mode verified info.martinmarinov.dvbdriver.phase29offline.test/androidx.test.runner.AndroidJUnitRunner')) {
        throw 'Full path instrumentation command is not pinned to the isolated package/class/namespace'
    }
    . ([scriptblock]::Create($cleanupNode.Extent.Text))
    if ($cleanupArguments -cne ('shell run-as info.martinmarinov.dvbdriver.phase29offline rm -rf -- ' + $directory)) {
        throw 'Private benchmark cleanup bypassed the exact isolated run-as package/path'
    }
    $FullPathInstrumentation = $false
    . ([scriptblock]::Create($directoryNode.Extent.Text))
    . ([scriptblock]::Create($cleanupNode.Extent.Text))
    if ($directory -cne ('/sdcard/Download/tbs5530-approval-benchmark/' + $namespace) -or
        $cleanupArguments -cne ('shell rm -rf -- ' + $code + ' ' + $directory)) { throw 'Standalone Download namespace/cleanup changed' }
}
Assert-MockFailure {
    & (Join-Path $PSScriptRoot 'Run-TBS5530-ApprovalBenchmark.ps1') -FullPathInstrumentation -AuditMode verified `
        -ApproveIsolatedAndroidBenchmark -Serial '127.0.0.1:1234' -LedgerPath 'relative.jsonl' -AdbPath (Get-Process -Id $PID).Path
} 'Sổ phải là đường dẫn tuyệt đối'
Assert-MockFailure {
    & $helperPath -Benchmark -FullPathInstrumentation -BenchmarkDirectory ('/sdcard/Download/tbs5530-approval-benchmark/' + $id)
} '^BLOCKED: benchmark'
Assert-MockFailure { & $helperPath -FullPathInstrumentation } '^BLOCKED: instrumentation'
Assert-MockFailure {
    & $helperPath -Benchmark -FullPathInstrumentation `
        -BenchmarkDirectory ('/data/user/0/info.martinmarinov.dvbdriver.phase29offline/files/tbs5530-approval-benchmark/' + $id) `
        -Serial '127.0.0.1:1234' -AdbPath (Join-Path $root 'build\does-not-exist-adb.exe') -LedgerPath 'Z:\unused-ledger.jsonl'
} '^Không tìm thấy ADB\.$'
foreach ($blockedDirectory in @(
    ('/sdcard/Android/data/info.martinmarinov.dvbdriver.phase29offline/files/tbs5530-approval-benchmark/' + $id),
    ('/data/user/0/info.martinmarinov.dvbdriver/files/tbs5530-approval-benchmark/' + $id),
    ('/data/user/1/info.martinmarinov.dvbdriver.phase29offline/files/tbs5530-approval-benchmark/' + $id),
    ('/data/user/0/info.martinmarinov.dvbdriver.phase29offline/files/tbs5530-approval-benchmark/../' + $id),
    ('/data/data/info.martinmarinov.dvbdriver.phase29offline/files/tbs5530-approval-benchmark/' + $id)
)) {
    Assert-MockFailure { & $helperPath -Benchmark -FullPathInstrumentation -BenchmarkDirectory $blockedDirectory } '^BLOCKED: benchmark'
}
Assert-MockFailure {
    & $helperPath -Benchmark -BenchmarkDirectory ('/data/user/0/info.martinmarinov.dvbdriver.phase29offline/files/tbs5530-approval-benchmark/' + $id)
} '^BLOCKED: benchmark'
$shell = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.StringConstantExpressionAst] -and
    $node.Value.StartsWith("cd '__DIRECTORY__'") }, $true)[0].Value
$ioShell = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.StringConstantExpressionAst] -and
    $node.Value.StartsWith('data_directory=$1;') }, $true)[0].Value
if ($ioShell.Contains('/proc/') -or $ioShell.Contains('getprop') -or $helperText -match "' shell -T run-as ") {
    throw 'App UID must never own the controller clock/boot reads'
}
$ioPackageNode = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.AssignmentStatementAst] -and
    $node.Left.Extent.Text -ceq '$ioPackage' -and $node.Extent.Text.Contains('info.martinmarinov.dvbdriver') }, $true)[0]
foreach ($mode in @(
    @{ Benchmark = $false; FullPath = $false; Package = 'info.martinmarinov.dvbdriver' },
    @{ Benchmark = $true; FullPath = $true; Package = 'info.martinmarinov.dvbdriver.phase29offline' },
    @{ Benchmark = $true; FullPath = $false; Package = '' }
)) {
    & {
        $Benchmark = $mode.Benchmark; $FullPathInstrumentation = $mode.FullPath
        . ([scriptblock]::Create($ioPackageNode.Extent.Text))
        if ($ioPackage -cne $mode.Package) { throw 'File I/O delegation selected the wrong package/UID' }
    }
}
$identity = 'OnePlus|GM1900|30|00000000-0000-0000-0000-000000000000'
$shell = $shell.Replace('__IO_SCRIPT__', $ioShell.Replace("'", "'\''")).Replace('__IO_PACKAGE__', 'info.martinmarinov.dvbdriver.phase29offline')
$shell = $shell.Replace('__DIRECTORY__', '.').Replace('__DATA_DIRECTORY__', '.').Replace('__WAIT_FOR_DIRECTORY__', 'no').Replace('__RESULT_FILE__', 'tbs5530-diagnostic.json').Replace('__PINNED_IDENTITY__', $identity)
$shell = $shell.Replace('read -r boot < /proc/sys/kernel/random/boot_id', 'boot=00000000-0000-0000-0000-000000000000')
$stub = 'getprop() { case "$1" in ro.product.manufacturer) printf OnePlus;; ro.product.model) printf GM1900;; ro.build.version.sdk) printf 30;; esac; }; run-as() { [ "$1" = info.martinmarinov.dvbdriver.phase29offline ] || return 1; shift; printf "app-io\n" >> uid-io-log; case "$*" in *"/proc/uptime"*) printf "UID_PERMISSION_DENIED\n"; return 1;; esac; MOCK_UID=app "$@"; }; '
$directory = Join-Path $root ('build\approval-channel-test-' + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $directory | Out-Null
$process = $null
try {
    $coldStartCases = 0
    $benchmarkColdStartCases = 0
    foreach ($scenario in @('PriorFilesThenAck', 'RapidTerminalBeforeAck', 'CurrentMalformed', 'CurrentMissingProtocol', 'CurrentObservationMalformed', 'UnknownUnbound', 'PriorReplayAfterAck', 'UiWaitThen132Acks', 'UiWaitWithoutStart', 'ClockBehindChallenge', 'BenchmarkUiWaitThen132Acks', 'BenchmarkCurrentMalformed')) {
        & {
            param($scenario)
            $Benchmark = $scenario.StartsWith('Benchmark', [StringComparison]::Ordinal)
            $FullPathInstrumentation = $Benchmark
            $bindingChallenge = $bindingChallenge | ConvertTo-Json | ConvertFrom-Json
            if ($Benchmark) {
                $bindingChallenge.install = 'benchmark-only'; $bindingChallenge.approval_mode = 'BENCHMARK_ONLY'
                $bindingChallenge.attachment_hash = '0' * 64
            }
            if ($scenario -ceq 'ClockBehindChallenge') { $bindingChallenge.created_elapsed_ms = 10000 }
            $fixture = Join-Path $directory $scenario
            New-Item -ItemType Directory -Path $fixture | Out-Null
            $requestPath = Join-Path $fixture 'phase29-write-request.json'
            $observationPath = Join-Path $fixture 'phase29-write-observation.json'
            $permitPath = Join-Path $fixture 'phase29-session-approval.json'
            $requestText = $legacyRequest | ConvertTo-Json -Compress
            if ($scenario -cin @('CurrentMalformed', 'BenchmarkCurrentMalformed')) { $requestText = $currentMalformed | ConvertTo-Json -Compress }
            if ($scenario -ceq 'CurrentMissingProtocol') { $requestText = $currentMissingProtocol | ConvertTo-Json -Compress }
            if ($scenario -ceq 'UnknownUnbound') { $requestText = '{"ordinal":24}' }
            $observationText = '{"run_epoch":"77777777-7777-7777-7777-777777777777","ordinal":23}'
            if ($scenario -ceq 'CurrentObservationMalformed') { $observationText = '{"run_epoch":"' + $epoch + '","ordinal":1}' }
            $legacyPermitText = '{"challenge":"77777777-7777-7777-7777-777777777777","authorized_ordinal":23}'
            [IO.File]::WriteAllText($requestPath, $requestText)
            [IO.File]::WriteAllText($observationPath, $observationText)
            [IO.File]::WriteAllText($permitPath, $legacyPermitText)
            $NewApprovalId = $id; $run = 'cold-start-mock'; $pinnedIdentity = 'mock-pinned'
            $acknowledged = 0; $observed = 0; $subprocesses = 0; $HardTimeoutSeconds = 300
            $watch = [Diagnostics.Stopwatch]::StartNew(); $latencies = [Collections.Generic.List[long]]::new()
            $ledger = [IO.FileStream]::new((Join-Path $fixture 'mock-hardware-ledger.jsonl'), [IO.FileMode]::CreateNew,
                [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read)
            if (-not $Benchmark) {
                Write-TBS5530PriorConsumedAudit $ledger
                Assert-TBS5530UnusedApprovalId $ledger $id -RequireEnrollment
            }
            $writer = [IO.StreamWriter]::new($ledger, [Text.UTF8Encoding]::new($false), 1024, $true)
            $entries = [Collections.Generic.List[object]]::new()
            $formatter = $ast.FindAll({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq 'Write-Ledger' }, $true)[0]
            . ([scriptblock]::Create($formatter.Extent.Text.Replace('function Write-Ledger(', 'function Write-FixtureLedger(')))
            function Write-Ledger($Entry) { Write-FixtureLedger $Entry; $entries.Add($Entry) }
            $state = @{ Poll = 0; Now = 10L; PriorFilesPreservedAfterGrant = $false }
            $input = [pscustomobject]@{ Lines = [Collections.Generic.List[string]]::new(); PermitPath = $permitPath; Entries = $entries
                Puts = 0; Clock = $state; Benchmark = $Benchmark; Heartbeats = [Collections.Generic.List[object]]::new() }
            $input | Add-Member ScriptMethod WriteLine { param($line) $this.Lines.Add([string]$line) }
            $input | Add-Member ScriptMethod Flush {
                $expectedReservations = $(if ($this.Benchmark) { 0 } else { 1 })
                if (@($this.Entries | Where-Object { $_.event -ceq 'NEW_APPROVAL_CONSUMED' }).Count -ne $expectedReservations) {
                    throw 'Initial permit bypassed mode-specific mock reservation rules'
                }
                $putPattern = $(if ($this.Benchmark) { '^PUT [0-9]+ UNTIL [0-9]+ MEASURE_PERSISTED$' } else { '^PUT [0-9]+ UNTIL [0-9]+$' })
                if ($this.Lines[-2] -notmatch $putPattern) { throw 'Cold-start fixture sent a PUT for the wrong mode' }
                $payload = $this.Lines[-1].Replace('"@DEVICE_ACK@"', ([string]$this.Clock.Now))
                [IO.File]::WriteAllText($this.PermitPath, $payload)
                $this.Heartbeats.Add(($payload | ConvertFrom-Json))
                $this.Puts++
            }
            $process = [pscustomobject]@{ StandardInput = $input }
            $currentRequest = $bindingChallenge | ConvertTo-Json | ConvertFrom-Json
            foreach ($pair in @{ ordinal = 1; request_nonce = '88888888-8888-8888-8888-888888888888'; request_elapsed_ms = 5
                ack_deadline_elapsed_ms = 1505; new_approval_id = $id }.GetEnumerator()) {
                $currentRequest | Add-Member NoteProperty $pair.Key $pair.Value
            }
            if ($Benchmark) { $currentRequest.PSObject.Properties.Remove('new_approval_id') }
            function Snapshot {
                $state.Poll++
                if ($scenario -cin @('UiWaitThen132Acks', 'UiWaitWithoutStart', 'BenchmarkUiWaitThen132Acks')) {
                    $state.Now = $(if ($state.Poll -le 8) { 10L + ($state.Poll - 1) * 500L }
                        elseif ($scenario -ceq 'UiWaitWithoutStart') { 300000L } else { 3510L + ($state.Poll - 8) * 10L })
                }
                $frame = @{ IDENTITY = $pinnedIdentity; CHALLENGE = ($bindingChallenge | ConvertTo-Json -Compress); NOW = [string]$state.Now
                    REQUEST = [IO.File]::ReadAllText($requestPath); OBSERVATION = [IO.File]::ReadAllText($observationPath)
                    RESULT = $(if ($Benchmark) { '' } else { '{"operation":"RAW_READ_AND_CLOSED"}' }) }
                if ($state.Poll -eq 2) {
                    $state.PriorFilesPreservedAfterGrant = ([IO.File]::ReadAllText($requestPath) -ceq $requestText -and
                        [IO.File]::ReadAllText($observationPath) -ceq $observationText -and $process.StandardInput.Puts -eq 1)
                }
                if ($scenario -cin @('UiWaitThen132Acks', 'UiWaitWithoutStart', 'BenchmarkUiWaitThen132Acks')) {
                    if ($scenario -cin @('UiWaitThen132Acks', 'BenchmarkUiWaitThen132Acks') -and $state.Poll -gt 8) {
                        $ordinal = $state.Poll - 8
                        if ($ordinal -le 132) {
                            $currentRequest.ordinal = $ordinal
                            $currentRequest.request_nonce = '88888888-8888-8888-8888-' + $ordinal.ToString('D12')
                            $currentRequest.request_elapsed_ms = $state.Now - 5
                            $currentRequest.ack_deadline_elapsed_ms = $currentRequest.request_elapsed_ms + 1500
                            [IO.File]::WriteAllText($requestPath, ($currentRequest | ConvertTo-Json -Compress))
                            $frame.REQUEST = [IO.File]::ReadAllText($requestPath)
                        } else { $frame.REQUEST = '' }
                        if ($ordinal -gt 1) {
                            $previous = $process.StandardInput.Heartbeats[-1]
                            $frame.OBSERVATION = @{ run_epoch = $epoch; ordinal = $previous.authorized_ordinal
                                request_elapsed_ms = $previous.request_elapsed_ms; ack_observed_elapsed_ms = $previous.host_ack_elapsed_ms + 1 } | ConvertTo-Json -Compress
                        }
                        if ($Benchmark -and $ordinal -ge 133) {
                            $frame.RESULT = '{"status":"PASS","roundtrips":132,"native_fake_calls":132,"full_length":132,"usb_calls":0}'
                        } elseif ($ordinal -ge 134) { $frame.RESULT = $fastTerminal | ConvertTo-Json -Depth 8 }
                    }
                    if ($state.Poll -gt 142) { throw 'UI-wait fixture exceeded bounded polls' }
                    return $frame
                }
                if ($scenario -ceq 'RapidTerminalBeforeAck' -and $state.Poll -ge 2) { $frame.RESULT = $fastTerminal | ConvertTo-Json -Depth 8 }
                elseif ($state.Poll -eq 3) {
                    # Emulate Android publishing v2 after the initial host grant; the host never edits this file.
                    [IO.File]::WriteAllText($requestPath, ($currentRequest | ConvertTo-Json -Compress))
                    $frame.REQUEST = [IO.File]::ReadAllText($requestPath); $frame.RESULT = $preparedReport | ConvertTo-Json -Depth 8
                } elseif ($state.Poll -ge 4) {
                    if ($scenario -ceq 'PriorReplayAfterAck') {
                        [IO.File]::WriteAllText($requestPath, $requestText)
                        $frame.REQUEST = [IO.File]::ReadAllText($requestPath)
                    }
                    $frame.RESULT = $(if ($scenario -ceq 'PriorReplayAfterAck') { $preparedReport | ConvertTo-Json -Depth 8 } else { $fastTerminal | ConvertTo-Json -Depth 8 })
                }
                if ($state.Poll -gt 4) { throw 'Cold-start fixture exceeded bounded poll count' }
                return $frame
            }
            function Assert-Time { if ($state.Now -ge 300000) { Stop-Channel 'TOTAL_SESSION_DEADLINE' } }
            function Total-DeadlineExpired { $state.Now -ge 300000 }
            function Read-ChannelLine { 'STORED ' + $state.Now + $(if ($Benchmark) { ' HOST_ACK_PERSISTED ' + $state.Now } else { '' }) }
            function Export-BenchmarkEvidence($name) { if ($name -ceq 'benchmark-observations.jsonl') { 132 } else { 1 } }
            function Stop-Channel([string]$kind) { throw ($kind + ': cold-start mock') }
            function Save-BenchmarkFailureEvidence { }
            $start = $helperText.IndexOf('    $snapshot = Snapshot')
            if ($start -lt 0) { throw 'Missing startup extraction boundary' }
            $startup = $helperText.Substring($start, $productionLoop[0].Extent.StartOffset - $start)
            $code = 'try {' + "`n" + $startup + $productionLoop[0].Extent.Text + "`n} catch " + $helperTry.CatchClauses[0].Body.Extent.Text
            try {
                if ($scenario -cin @('CurrentMalformed', 'CurrentMissingProtocol', 'CurrentObservationMalformed', 'UnknownUnbound', 'PriorReplayAfterAck', 'BenchmarkCurrentMalformed')) {
                    Assert-MockFailure { . ([scriptblock]::Create($code)) } '^INVALID_(REQUEST|OBSERVATION)_BINDING:'
                } elseif ($scenario -ceq 'ClockBehindChallenge') {
                    Assert-MockFailure { . ([scriptblock]::Create($code)) } '^DEVICE_CLOCK_UNAVAILABLE:'
                } elseif ($scenario -ceq 'UiWaitWithoutStart') {
                    Assert-MockFailure { . ([scriptblock]::Create($code)) } '^TOTAL_SESSION_DEADLINE:'
                } else { . ([scriptblock]::Create($code)) | Out-Null }
                $expectedRequest = $(if ($scenario -cin @('PriorFilesThenAck', 'UiWaitThen132Acks', 'BenchmarkUiWaitThen132Acks')) { $currentRequest | ConvertTo-Json -Compress } else { $requestText })
                if ([IO.File]::ReadAllText($requestPath) -cne $expectedRequest -or [IO.File]::ReadAllText($observationPath) -cne $observationText) {
                    throw 'Production cold-start changed persisted request/observation outside simulated Android publication'
                }
                $reserved = @($entries | Where-Object { $_.event -ceq 'NEW_APPROVAL_CONSUMED' })
                $authorized = @($entries | Where-Object { $_.event -cin @('production_authorization_ordinal_authorized', 'authorize_benchmark_ordinal') })
                $terminal = @($entries | Where-Object { $_.event -ceq 'NEW_ATTEMPT_TERMINAL' })
                if ($scenario -cin @('CurrentMalformed', 'CurrentMissingProtocol', 'CurrentObservationMalformed', 'UnknownUnbound', 'ClockBehindChallenge', 'BenchmarkCurrentMalformed')) {
                    if ($reserved.Count -ne 0 -or $input.Puts -ne 0 -or [IO.File]::ReadAllText($permitPath) -cne $legacyPermitText) {
                        throw 'Malformed/unbound current metadata issued an initial permit or reserved an ID'
                    }
                } else {
                    if ($Benchmark) {
                        if ($reserved.Count -ne 0 -or -not $state.PriorFilesPreservedAfterGrant -or
                            $input.Heartbeats[0].PSObject.Properties['new_approval_id']) { throw 'Benchmark crossed into production authorization' }
                    } else {
                        if ($reserved.Count -ne 1 -or -not $reserved[0].initial_prior_request_ignored -or
                            -not $reserved[0].initial_prior_observation_ignored -or -not $state.PriorFilesPreservedAfterGrant) { throw 'Prior artifacts were not audited in durable reservation' }
                        Assert-MockFailure { Assert-TBS5530UnusedApprovalId $ledger $NewApprovalId -RequireEnrollment } '^BLOCKED: NewApprovalId'
                    }
                    if ($scenario -cin @('UiWaitThen132Acks', 'UiWaitWithoutStart', 'BenchmarkUiWaitThen132Acks')) {
                        $waiting = @($input.Heartbeats | Where-Object { $_.authorized_ordinal -eq 0 })
                        if ($waiting.Count -ne 8 -or $waiting[-1].heartbeat_elapsed_ms - $waiting[0].heartbeat_elapsed_ms -ne 3500) {
                            throw 'Prior request continue bypassed UI-wait heartbeat refresh'
                        }
                        for ($n = 1; $n -lt $input.Heartbeats.Count; $n++) {
                            if ($input.Heartbeats[$n].heartbeat_elapsed_ms -le $input.Heartbeats[$n - 1].heartbeat_elapsed_ms -or
                                $input.Heartbeats[$n].heartbeat_elapsed_ms - $input.Heartbeats[$n - 1].heartbeat_elapsed_ms -ge 2000 -or
                                $input.Heartbeats[$n].expires_elapsed_ms -ne 300000) { throw 'Heartbeat stale, unchanged, or TTL extended during UI wait/ACKs' }
                        }
                        if ($scenario -cin @('UiWaitThen132Acks', 'BenchmarkUiWaitThen132Acks')) {
                            $expectedPuts = $(if ($Benchmark) { 140 } else { 141 }); $expectedTerminals = $(if ($Benchmark) { 0 } else { 1 })
                            if ($input.Puts -ne $expectedPuts -or $authorized.Count -ne 132 -or $terminal.Count -ne $expectedTerminals -or
                                $observed -ne 132 -or ($Benchmark -and $entries[-1].event -cne 'benchmark_android_result')) { throw 'UI-wait fixture did not complete 132 request/ACK/observation steps' }
                        } elseif ($input.Puts -ne 8 -or $authorized.Count -ne 0 -or $terminal.Count -ne 0 -or
                            $entries[-1].failure_kind -cne 'TOTAL_SESSION_DEADLINE') { throw 'No-UI session exceeded its deadline or granted an ordinal' }
                    } elseif ($scenario -ceq 'RapidTerminalBeforeAck') {
                        if ($input.Puts -ne 1 -or $authorized.Count -ne 0 -or $terminal.Count -ne 1 -or
                            $terminal[0].terminal_kind -cne 'PRE_NATIVE_STOP' -or $terminal[0].firmware_pass -ne $false) { throw 'Rapid production terminal required pending or an ACK' }
                    } elseif ($input.Puts -ne 3 -or $authorized.Count -ne 1 -or
                        ($scenario -ceq 'PriorFilesThenAck' -and $terminal.Count -ne 1) -or
                        ($scenario -ceq 'PriorReplayAfterAck' -and $terminal.Count -ne 0)) { throw 'Production first-start ACK/replay boundary failed' }
                }
            } finally { $writer.Dispose(); $ledger.Dispose() }
        } $scenario
        if ($scenario.StartsWith('Benchmark', [StringComparison]::Ordinal)) { $benchmarkColdStartCases++ } else { $coldStartCases++ }
    }
    $infoPath = Join-Path $directory 'mock-benchmark-evidence.jsonl'
    Write-TBS5530BenchmarkLedgerEntry $infoPath @{ event = 'benchmark_wrapper_start'; wrapper_run = 'mock'; benchmark_directory = '/isolated-mock'; audit_mode = 'verified' }
    Write-TBS5530BenchmarkLedgerEntry $infoPath @{ event = 'benchmark_wrapper_fail'; wrapper_run = 'mock'; metadata = @{ child_exit_code = 0; stdout = @{ Text = 'FAILURES!!!' } } }
    $infoBefore = [IO.File]::ReadAllText($infoPath)
    $info = Get-TBS5530BenchmarkRunInfo -LedgerPath $infoPath
    if ($info.benchmark_directory -cne '/isolated-mock' -or $info.status -cne 'FAIL' -or
        $info.failure_metadata.stdout.Text -cne 'FAILURES!!!' -or [IO.File]::ReadAllText($infoPath) -cne $infoBefore) {
        throw 'Read-only benchmark namespace API changed/recreated evidence'
    }
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
    $clockStart = $shell.IndexOf('read_clock() {')
    $clockEnd = $shell.IndexOf(' write_payload()', $clockStart)
    if ($clockStart -lt 0 -or $clockEnd -lt 0) { throw 'Clock extraction boundaries missing' }
    $clockSource = $shell.Substring($clockStart, $clockEnd - $clockStart)
    $clockFaultCases = 0
    $clockCasesRun = 0
    $clockWaitCases = 0
    $uidPermissionChecks = 0
    foreach ($clockCase in @(
        @{ Input = '{ up=; rest=; false; }'; Expected = 'ERROR clock_read' },
        @{ Input = '{ up=invalid; rest=; true; }'; Expected = 'ERROR clock_format' },
        @{ Input = '{ up=123; rest=; true; }'; Expected = 'ERROR clock_format' },
        @{ Input = '{ up=123.456; rest=; true; }'; Expected = 'ERROR clock_format' },
        @{ Input = '{ up=9223372036854776.00; rest=; true; }'; Expected = 'ERROR clock_range' },
        @{ Input = '{ up=0.00; rest=; true; }'; Expected = 'ERROR clock_alignment'; Put = $true },
        @{ Input = '{ clock_reads=$((${clock_reads:-0} + 1)); if [ "$clock_reads" -eq 1 ]; then up=0.00; else up=0.01; fi; rest=; true; }'; Expected = 'STORED 10'; Put = $true; Minimum = 5; Until = 1500; Code = 0 },
        @{ Input = '{ clock_reads=$((${clock_reads:-0} + 1)); if [ "$clock_reads" -eq 1 ]; then up=0.00; else up=0.01; fi; rest=; true; }'; Expected = 'ERROR ack_deadline'; Put = $true; Minimum = 5; Until = 10 },
        @{ Input = '{ up=0.00; rest=; true; }'; Expected = 'ERROR clock_alignment'; Put = $true; Minimum = 5; Until = 1500 }
    )) {
        $clockDirectory = Join-Path $directory ('clock-' + $clockCasesRun)
        New-Item -ItemType Directory -Path $clockDirectory | Out-Null
        $clockInfo = [Diagnostics.ProcessStartInfo]::new($bash)
        $clockInfo.WorkingDirectory = $clockDirectory; $clockInfo.UseShellExecute = $false
        $clockInfo.RedirectStandardInput = $true; $clockInfo.RedirectStandardOutput = $true; $clockInfo.RedirectStandardError = $true
        $clockChild = [Diagnostics.Process]::new(); $clockChild.StartInfo = $clockInfo
        try {
            if (-not $clockChild.Start()) { throw 'Clock mock shell start failed' }
            $clockOutput = $clockChild.StandardOutput.ReadToEndAsync(); $clockErrors = $clockChild.StandardError.ReadToEndAsync()
            $clockChild.StandardInput.NewLine = "`n"
            if ($clockCase.ContainsKey('Put')) {
                $clockChild.StandardInput.WriteLine($stub + $shell.Replace('read -r up rest < /proc/uptime', $clockCase.Input))
                $clockPut = 'PUT ' + $(if ($clockCase.ContainsKey('Minimum')) { $clockCase.Minimum } else { 10000 })
                if ($clockCase.ContainsKey('Until')) { $clockPut += ' UNTIL ' + $clockCase.Until }
                $clockChild.StandardInput.WriteLine($clockPut)
                $clockChild.StandardInput.WriteLine('{"heartbeat_elapsed_ms":"@DEVICE_ACK@"}')
            } else {
                $clockChild.StandardInput.WriteLine($clockSource.Replace('read -r up rest < /proc/uptime', $clockCase.Input) +
                    ' now_ms; printf "UNEXPECTED_PERMIT_PATH\n"; exit')
            }
            $clockChild.StandardInput.Close()
            if (-not $clockChild.WaitForExit(10000)) { $clockChild.Kill(); throw 'Clock mock shell timeout' }
            $expectedCode = $(if ($clockCase.ContainsKey('Code')) { $clockCase.Code } else { 1 })
            if ($clockChild.ExitCode -ne $expectedCode -or $clockOutput.Result.Trim() -cne $clockCase.Expected) {
                throw 'Raw clock wait/alignment/deadline semantics failed'
            }
            $clockPermitPath = Join-Path $clockDirectory 'phase29-session-approval.json'
            if ($clockCase.ContainsKey('Put') -and $expectedCode -ne 0 -and (Test-Path -LiteralPath $clockPermitPath)) {
                throw 'Misaligned, stalled or expired clock published an initial permit'
            }
            if ($expectedCode -eq 0 -and ([IO.File]::ReadAllText($clockPermitPath) | ConvertFrom-Json).heartbeat_elapsed_ms -ne 10) {
                throw 'Minimum 5 was substituted instead of sampling the real next tick 10'
            }
            if ($expectedCode -ne 0) { $clockFaultCases++ }
            if ($clockCase.ContainsKey('Minimum')) { $clockWaitCases++ }
            $clockCasesRun++
        } finally { $clockChild.Dispose() }
    }
    $fileIoFaultCases = 0
    foreach ($ioCase in @(
        @{ Name = 'Missing'; Prefix = ''; Expected = "CHALLENGE `nREQUEST `nOBSERVATION `nRESULT"; Code = 0 },
        @{ Name = 'Permission'; Prefix = 'ls() { printf "Permission denied\n" >&2; return 1; }; '; Expected = 'ERROR file_io_permission'; Code = 1 },
        @{ Name = 'ReadFailure'; Prefix = 'ls() { printf "Input/output error\n" >&2; return 1; }; '; Expected = 'ERROR channel_read_error'; Code = 1 },
        @{ Name = 'EmptyFile'; Prefix = ''; Expected = 'ERROR channel_read_error'; Code = 1 },
        @{ Name = 'OpenFailure'; Prefix = ''; Expected = 'ERROR metadata_io'; Code = 1 }
    )) {
        $ioCaseDirectory = Join-Path $directory ('io-' + $ioCase.Name)
        New-Item -ItemType Directory -Path $ioCaseDirectory | Out-Null
        $probeScript = $ioCase.Prefix + $ioShell
        if ($ioCase.Name -cin @('EmptyFile', 'OpenFailure')) {
            [IO.File]::WriteAllText((Join-Path $ioCaseDirectory 'phase29-challenge.json'), $(if ($ioCase.Name -ceq 'EmptyFile') { '' } else { '{"test":true}' }))
        }
        if ($ioCase.Name -ceq 'OpenFailure') { $probeScript = $probeScript.Replace('done < "$2"', 'done < missing-open-file') }
        $ioInfo = [Diagnostics.ProcessStartInfo]::new($bash)
        $ioInfo.WorkingDirectory = $ioCaseDirectory; $ioInfo.UseShellExecute = $false
        $ioInfo.RedirectStandardInput = $true; $ioInfo.RedirectStandardOutput = $true; $ioInfo.RedirectStandardError = $true
        $ioChild = [Diagnostics.Process]::new(); $ioChild.StartInfo = $ioInfo
        try {
            if (-not $ioChild.Start()) { throw 'File-I/O mock shell start failed' }
            $ioOutput = $ioChild.StandardOutput.ReadToEndAsync(); $ioErrors = $ioChild.StandardError.ReadToEndAsync()
            $ioChild.StandardInput.NewLine = "`n"
            $ioChild.StandardInput.WriteLine("sh -c '" + $probeScript.Replace("'", "'\''") + "' sh . POLL benchmark-result.json yes")
            $ioChild.StandardInput.Close()
            if (-not $ioChild.WaitForExit(10000)) { $ioChild.Kill(); throw 'File-I/O mock shell timeout' }
            if ($ioChild.ExitCode -ne $ioCase.Code -or $ioOutput.Result.TrimEnd() -cne $ioCase.Expected.TrimEnd()) {
                throw ('Missing file / permission / read error classification failed: ' + $ioCase.Name)
            }
            $fileIoFaultCases++
        } finally { $ioChild.Dispose() }
    }
    $uidInfo = [Diagnostics.ProcessStartInfo]::new($bash)
    $uidInfo.WorkingDirectory = $directory; $uidInfo.UseShellExecute = $false
    $uidInfo.RedirectStandardInput = $true; $uidInfo.RedirectStandardOutput = $true; $uidInfo.RedirectStandardError = $true
    $uidChild = [Diagnostics.Process]::new(); $uidChild.StartInfo = $uidInfo
    try {
        if (-not $uidChild.Start()) { throw 'UID model shell start failed' }
        $uidOutput = $uidChild.StandardOutput.ReadToEndAsync(); $uidErrors = $uidChild.StandardError.ReadToEndAsync()
        $uidChild.StandardInput.NewLine = "`n"
        $uidChild.StandardInput.WriteLine($stub + $clockSource + ' now_ms; printf "SHELL_CLOCK_OK\n"; run-as info.martinmarinov.dvbdriver.phase29offline sh -c ' +
            "'" + ($clockSource + ' now_ms').Replace("'", "'\''") + "'" +
            '; [ "$?" -ne 0 ] || exit 1; printf "APP_CLOCK_DENIED_CONFIRMED\n"; exit 0')
        $uidChild.StandardInput.Close()
        if (-not $uidChild.WaitForExit(10000)) { $uidChild.Kill(); throw 'UID model shell timeout' }
        if ($uidChild.ExitCode -ne 0 -or $uidOutput.Result -notmatch 'SHELL_CLOCK_OK' -or
            $uidOutput.Result -notmatch 'APP_CLOCK_DENIED_CONFIRMED') { throw 'UID permission model did not distinguish shell clock from denied app clock' }
        $uidPermissionChecks++
    } finally { $uidChild.Dispose() }
    $pollIoShell = $ioShell.Replace('emit RESULT "$result_file";;', 'emit RESULT "$result_file"; printf done > poll-read-complete;;')
    $pollClockShell = $shell.Replace($ioShell.Replace("'", "'\''"), $pollIoShell.Replace("'", "'\''"))
    $pollClockShell = $pollClockShell.Replace('identity; while IFS= read -r command;',
        'now_ms() { if [ -f poll-read-complete ]; then stamp=200; else stamp=100; fi; }; identity; while IFS= read -r command;')
    $pollInfo = [Diagnostics.ProcessStartInfo]::new($bash)
    $pollInfo.WorkingDirectory = $directory; $pollInfo.UseShellExecute = $false
    $pollInfo.RedirectStandardInput = $true; $pollInfo.RedirectStandardOutput = $true; $pollInfo.RedirectStandardError = $true
    $pollChild = [Diagnostics.Process]::new(); $pollChild.StartInfo = $pollInfo
    try {
        if (-not $pollChild.Start()) { throw 'Post-read POLL clock fixture start failed' }
        $pollOutput = $pollChild.StandardOutput.ReadToEndAsync(); $pollErrors = $pollChild.StandardError.ReadToEndAsync()
        $pollChild.StandardInput.NewLine = "`n"
        $pollChild.StandardInput.WriteLine($stub + $pollClockShell)
        $pollChild.StandardInput.WriteLine('POLL'); $pollChild.StandardInput.WriteLine('QUIT'); $pollChild.StandardInput.Close()
        if (-not $pollChild.WaitForExit(10000)) { $pollChild.Kill(); throw 'Post-read POLL clock fixture timeout' }
        if ($pollChild.ExitCode -ne 0 -or $pollOutput.Result -notmatch '(?m)^NOW 200\r?$') { throw 'POLL clock was sampled before app file I/O completed' }
    } finally { $pollChild.Dispose() }
    $start = [Diagnostics.ProcessStartInfo]::new($bash)
    $start.WorkingDirectory = $directory; $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Local shell start failed' }
    $process.StandardInput.NewLine = "`n"; $stderr = $process.StandardError.ReadToEndAsync()
    $localReads = 0
    # This is a local Git Bash fixture wait, not the Android ACK budget. Process startup
    # exceeded 3 s under concurrent builds; production Read-ChannelLine remains 1500 ms.
    function Read-LocalLine([int]$WaitMs = 10000) {
        $script:localReads++
        $line = $process.StandardOutput.ReadLineAsync()
        if (-not $line.Wait($WaitMs) -or $null -eq $line.Result) {
            $detail = $(if ($stderr.IsCompleted) { $stderr.Result } else { 'stderr still open' })
            throw ('Local shell protocol timeout at read ' + $script:localReads + ': ' + $detail)
        }
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
    if (@([IO.File]::ReadAllLines((Join-Path $directory 'uid-io-log'))).Count -lt 3) { throw 'POLL/PUT bypassed run-as I/O delegation' }
    $uidPermissionChecks++
    $permit = [IO.File]::ReadAllText((Join-Path $directory 'phase29-session-approval.json')) | ConvertFrom-Json
    if ($permit.heartbeat_elapsed_ms -ne $permit.host_ack_elapsed_ms -or $permit.heartbeat_elapsed_ms -is [string]) { throw 'Numeric device timestamp substitution failed' }
    $reportEpoch = '33333333-3333-3333-3333-333333333333'
    $report = [ordered]@{ operation = 'DEBUG_ONE_SHOT_FX2'; state = 'LOADING'; run_epoch = $reportEpoch; pending = $true
        cleanup_pending = $false; cleanup_failure = 'NONE'; failure = 'NONE'
        native_call_started = 0; native_call_completed = 0; full_length_completed = 0; image_bytes_full_length = 0
        payload_bytes_full_length = 0; attempted = 0; completed = 0; transferred_bytes = 0; note = "escaped`ninside JSON string" }
    $loopMillis = [Collections.Generic.List[double]]::new()
    for ($ordinal = 1; $ordinal -le 132; $ordinal++) {
        $loopWatch = [Diagnostics.Stopwatch]::StartNew()
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
        $process.StandardInput.WriteLine('PUT 10000 MEASURE_PERSISTED')
        $process.StandardInput.WriteLine(('{"authorized_ordinal":' + $ordinal + ',"request_nonce":"' + $nonce + '","heartbeat_elapsed_ms":"@DEVICE_ACK@","host_ack_elapsed_ms":"@DEVICE_ACK@"}'))
        $process.StandardInput.Flush()
        $stored = ConvertFrom-TBS5530StoredFrame (Read-LocalLine)
        if ($stored.host_ack_persisted_status -cne 'MEASURED' -or $stored.host_ack_persisted_elapsed_ms -isnot [long]) { throw '132-loop completion marker missing' }
        $ack = [IO.File]::ReadAllText((Join-Path $directory 'phase29-session-approval.json')) | ConvertFrom-Json
        if ($ack.authorized_ordinal -ne $ordinal -or $ack.request_nonce -cne $nonce -or
            $ack.heartbeat_elapsed_ms -is [string] -or $ack.host_ack_elapsed_ms -ne $ack.heartbeat_elapsed_ms -or
            $ack.host_ack_elapsed_ms -ne $stored.host_ack_elapsed_ms -or $ack.PSObject.Properties['host_ack_persisted_elapsed_ms']) { throw '132-loop ACK content mismatch' }
        $loopMillis.Add($loopWatch.Elapsed.TotalMilliseconds)
    }
    $report['boundary_events'] = @(
        foreach ($n in 1..23) { 'ordinal=' + $n + ' monotonic_ns=1 NATIVE_CALL_START'; 'ordinal=' + $n + ' monotonic_ns=2 NATIVE_CALL_END returned=64 requested=64' }
        'ordinal=24 monotonic_ns=3 STOP reason=PRE_NATIVE_ACK_TIMEOUT'
    )
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
    # Export over the existing local channel; records remain JSONL, not flattened into invalid JSON.
    $observationLines = foreach ($n in 1..132) {
        @{ run_epoch = $reportEpoch; ordinal = $n; request_elapsed_ms = 10; host_seen_elapsed_ms = 11
            host_ack_elapsed_ms = 12; ack_observed_elapsed_ms = 13; ack_deadline_elapsed_ms = 1510 } | ConvertTo-Json -Compress
    }
    [IO.File]::WriteAllLines((Join-Path $directory 'benchmark-observations.jsonl'), $observationLines)
    [IO.File]::WriteAllText((Join-Path $directory 'benchmark-audit.jsonl'), ('{"mode":"verified","native_fake_calls":132}' + "`n"))
    & {
        $Benchmark = $true; $failureEvidenceMode = $false; $challenge = [pscustomobject]@{ run_epoch = $reportEpoch }; $run = 'offline-export'
        $exported = [Collections.Generic.List[object]]::new()
        function Assert-Time { }
        function Read-ChannelLine { Read-LocalLine }
        function Write-Ledger($entry) { $exported.Add($entry) }
        function Stop-Channel([string]$kind) { throw ($kind + ': offline evidence') }
        $exporter = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Export-BenchmarkEvidence' }, $true)[0]
        . ([scriptblock]::Create($exporter.Extent.Text))
        if ((Export-BenchmarkEvidence 'benchmark-observations.jsonl') -ne 132 -or
            (Export-BenchmarkEvidence 'benchmark-audit.jsonl') -ne 1 -or $exported.Count -ne 133 -or
            $exported[-1].evidence.mode -cne 'verified') { throw 'Persistent JSONL evidence export lost records' }
        Assert-MockFailure { Export-BenchmarkEvidence 'tbs5530-diagnostic.json' } '^FAIL-STOP:'
        $Benchmark = $false
        Assert-MockFailure { Export-BenchmarkEvidence 'benchmark-audit.jsonl' } '^FAIL-STOP:'
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
    $process.Dispose(); $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Deterministic measurement shell start failed' }
    $process.StandardInput.NewLine = "`n"; $stderr = $process.StandardError.ReadToEndAsync()
    $clockAndRename = 'now_ms() { if [ -f rename-complete ]; then stamp=1120; else stamp=110; fi; }; io() { sh -c "$io_script" sh . "$1" tbs5530-diagnostic.json no "${2-}" || return; if [ "$1" = PUT ]; then [ ! -f rename-complete ] || return 1; printf done > rename-complete; fi; }; identity; while IFS= read -r command;'
    $measuredShell = $shell.Replace('identity; while IFS= read -r command;', $clockAndRename)
    $process.StandardInput.WriteLine($stub + $measuredShell)
    $process.StandardInput.WriteLine('PUT 100 MEASURE_PERSISTED')
    $process.StandardInput.WriteLine('{"request_nonce":"fixed-mock-nonce","heartbeat_elapsed_ms":"@DEVICE_ACK@","host_ack_elapsed_ms":"@DEVICE_ACK@"}')
    $process.StandardInput.Flush()
    $measured = ConvertFrom-TBS5530StoredFrame (Read-LocalLine)
    $unchangedPermit = [IO.File]::ReadAllText((Join-Path $directory 'phase29-session-approval.json')) | ConvertFrom-Json
    if ($measured.host_ack_elapsed_ms -ne 110 -or $measured.host_ack_persisted_elapsed_ms -ne 1120 -or
        $unchangedPermit.host_ack_elapsed_ms -ne 110 -or $unchangedPermit.heartbeat_elapsed_ms -ne 110 -or
        $unchangedPermit.request_nonce -cne 'fixed-mock-nonce' -or $unchangedPermit.PSObject.Properties['host_ack_persisted_elapsed_ms']) {
        throw 'Completion marker was sampled before rename, rewrote permit, or changed ACK validity timestamp'
    }
    $process.StandardInput.WriteLine('QUIT'); $process.StandardInput.Close()
    if (-not $process.WaitForExit(3000) -or $process.ExitCode -ne 0) { throw 'Deterministic measurement shell exit failed' }
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
    $oversizedLines = '{' + "`n" + '"first":"' + ('x' * 140000) + '",' + "`n" + '"second":"' + ('x' * 140000) + '"' + "`n}"
    [IO.File]::WriteAllText((Join-Path $directory 'tbs5530-diagnostic.json'), $oversizedLines)
    $process.StandardInput.WriteLine($stub + $shell); $process.StandardInput.WriteLine('POLL'); $process.StandardInput.Flush()
    # Buffered POLL rejects the complete read before emitting IDENTITY/NOW or partial metadata.
    # This deliberately invalid 280 KiB file is not an ACK deadline test. Git Bash file reads on
    # Windows can exceed the old 3 s test wait; keep production channel/ACK budgets unchanged.
    $boundWatch = [Diagnostics.Stopwatch]::StartNew()
    if ((Read-LocalLine -WaitMs 30000) -cne 'ERROR metadata_size' -or -not $process.WaitForExit(3000) -or $process.ExitCode -eq 0) { throw 'Oversized report silently truncated/accepted' }
    $oversizeRejectMs = $boundWatch.ElapsedMilliseconds
} finally {
    if ($null -ne $process) {
        if (-not $process.HasExited) { $process.Kill($true); $process.WaitForExit(3000) | Out-Null }
        $process.Dispose()
    }
    try { Remove-Item -LiteralPath $directory -Recurse -Force }
    catch { Write-Warning ('Offline test scratch cleanup pending: ' + $directory) }
}
$sortedLoops = @($loopMillis | Sort-Object)
[ordered]@{ status = 'PASS'; negative_mock_checks = $mockChecks; production_cold_start_cases = $coldStartCases; benchmark_cold_start_cases = $benchmarkColdStartCases
    ui_wait_mock_ms = 3500; ui_wait_then_ack_steps = 132; device_clock_fault_cases = $clockFaultCases; raw_clock_wait_cases = $clockWaitCases
    uid_permission_checks = $uidPermissionChecks; file_io_fault_cases = $fileIoFaultCases
    poll_clock_after_file_io = 'PASS'; timestamp_clamping = 'NONE'
    local_shell_loops = 132; exported_jsonl_records = 133
    local_loop_p50_ms = [Math]::Round($sortedLoops[65], 2); local_loop_p95_ms = [Math]::Round($sortedLoops[125], 2)
    local_loop_p99_ms = [Math]::Round($sortedLoops[130], 2); local_loop_max_ms = [Math]::Round($sortedLoops[-1], 2)
    total_test_ms = $testWatch.ElapsedMilliseconds; oversized_fixture_rejection_ms = $oversizeRejectMs
    failure_evidence_checks = 'PASS'; child_exit_zero_instrumentation_failure = 'PASS'; bounded_capture_limit = 262144
    adb_processes = 0; usb_calls = 0 } | ConvertTo-Json -Compress
'PASS: parser, wrapper/helper isolation, production cold-start persisted leftovers/current-malformed/replay, deadlines, fast terminal without pending/ACK, epoch/revision/native-audit classification, canonical ledger duplicate keys/truncation/restart, 132 local shell/atomic-ACK loops, JSONL export and pinned identity. Local mock timings are NOT Android/ADB/hardware latency.'
