# Ledger helpers contain no ADB, app storage or hardware operation.
function Get-TBS5530HardwareLedgerPath {
    $local = [Environment]::GetFolderPath([Environment+SpecialFolder]::LocalApplicationData)
    if (-not $local) { throw 'BLOCKED: không xác định được LocalApplicationData canonical.' }
    return [IO.Path]::GetFullPath((Join-Path $local 'TBS5530\Phase29\hardware-attempts.jsonl'))
}
function Resolve-TBS5530ApprovalLedgerPath {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$RepositoryRoot)
    if (-not [IO.Path]::IsPathRooted($Path)) { throw 'Sổ phải là đường dẫn tuyệt đối ngoài Git.' }
    $full = [IO.Path]::GetFullPath($Path)
    $repository = [IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\') + '\'
    $parent = [IO.Path]::GetDirectoryName($full)
    if ($full.StartsWith($repository, [StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $parent -PathType Container)) {
        throw 'Sổ phải ở thư mục đã có, nằm ngoài checkout Git.'
    }
    $git = (Get-Command git.exe -ErrorAction Stop).Source
    $probe = @(& $git -C $parent rev-parse --is-inside-work-tree --is-inside-git-dir 2>$null)
    if ($LASTEXITCODE -eq 0 -and @($probe | Where-Object { $_ -ceq 'true' }).Count) {
        throw 'Sổ không được nằm trong bất kỳ Git checkout hoặc Git metadata nào.'
    }
    return $full
}
function Write-TBS5530BenchmarkLedgerEntry {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][hashtable]$Entry)
    if ([IO.Path]::GetFullPath($Path).Equals((Get-TBS5530HardwareLedgerPath), [StringComparison]::OrdinalIgnoreCase)) {
        throw 'BLOCKED: benchmark không được ghi canonical hardware ledger.'
    }
    $stream = [IO.FileStream]::new($Path, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read)
    $writer = $null
    try {
        if ($stream.Length -gt 0) {
            $stream.Position = $stream.Length - 1
            if ($stream.ReadByte() -ne 10) { throw 'FAIL-STOP: benchmark ledger thiếu LF cuối; không append.' }
        }
        $stream.Position = $stream.Length
        $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false), 1024, $true)
        $Entry.mode = 'BENCHMARK_ONLY'; $Entry.helper_usb_calls = 0
        $writer.WriteLine(($Entry | ConvertTo-Json -Compress -Depth 16)); $writer.Flush(); $stream.Flush($true)
    } finally { if ($null -ne $writer) { $writer.Dispose() }; $stream.Dispose() }
}
function Get-TBS5530BenchmarkRunInfo {
    param([Parameter(Mandatory)][string]$LedgerPath)
    # Read-only lookup: never creates a namespace, ledger, approval or connection.
    $stream = [IO.FileStream]::new($LedgerPath, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    $reader = [IO.StreamReader]::new($stream, [Text.UTF8Encoding]::new($false, $true))
    $info = $null
    try {
        while ($null -ne ($line = $reader.ReadLine())) {
            $record = $line | ConvertFrom-Json -ErrorAction Stop
            if ($record.event -ceq 'benchmark_wrapper_start') {
                $info = [ordered]@{ wrapper_run = $record.wrapper_run; benchmark_directory = $record.benchmark_directory
                    audit_mode = $record.audit_mode; status = 'STARTED'; failure_metadata = $null }
            } elseif ($null -ne $info -and $record.event -cin @('benchmark_wrapper_fail', 'benchmark_wrapper_pass') -and
                $record.wrapper_run -ceq $info.wrapper_run) {
                $info.status = $(if ($record.event -ceq 'benchmark_wrapper_fail') { 'FAIL' } else { 'PASS' })
                if ($record.event -ceq 'benchmark_wrapper_fail') { $info.failure_metadata = $record.metadata }
            }
        }
        if ($null -eq $info) { throw 'NOT_RECORDED: ledger cũ chưa ghi namespace; dùng warning gốc, không recreate.' }
        return [pscustomobject]$info
    } finally { $reader.Dispose(); $stream.Dispose() }
}
function Write-TBS5530PriorConsumedAudit {
    param([Parameter(Mandatory)][IO.Stream]$Stream)
    if (-not $Stream.CanSeek -or -not $Stream.CanWrite -or $Stream.Length -ne 0) {
        throw 'BLOCKED: chỉ enroll stream mới, trống; không overwrite/reset lịch sử.'
    }
    $records = @(
        [ordered]@{ event = 'HARDWARE_LEDGER_ENROLLED'; schema = 'tbs5530-phase29-hardware-ledger'; schema_version = 1
            utc = [DateTime]::UtcNow.ToString('o'); prior_consumed_acknowledged = $true
            user_provided_phase29_evidence_acknowledged = $true; ledger_policy = 'CANONICAL_APPEND_ONLY_NO_REARM'
            hardware_approval = $false; new_approval_issued = $false },
        [ordered]@{ event = 'PRIOR_ATTEMPT_CONSUMED'; phase = '2.9'; evidence_checkpoint_git_sha = 'd76bbfb'
            provenance = 'USER_PROVIDED_PHASE29_EVIDENCE'; state = 'UNCERTAIN'; attempt_consumed = $true
            native_calls = 23; image_bytes = 1344; cpu_release_sent = $false; firmware_upload_completed = $false }
    )
    $writer = [IO.StreamWriter]::new($Stream, [Text.UTF8Encoding]::new($false), 1024, $true)
    try {
        $writer.NewLine = "`n"
        foreach ($record in $records) { $writer.WriteLine(($record | ConvertTo-Json -Compress)) }
        $writer.Flush()
        if ($Stream -is [IO.FileStream]) { $Stream.Flush($true) } else { $Stream.Flush() }
    } finally { $writer.Dispose() }
}
function New-TBS5530PriorConsumedAuditFile {
    # Path is injected only for offline filesystem tests. The public initializer has no Path parameter.
    param([Parameter(Mandatory)][string]$Path)
    $stream = [IO.FileStream]::new($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read)
    try { Write-TBS5530PriorConsumedAudit -Stream $stream } finally { $stream.Dispose() }
}
function Get-TBS5530ProductionReportRevision {
    param([Parameter(Mandatory)]$Report)
    $parts = foreach ($field in @('operation', 'run_epoch', 'state', 'planned_ordinal', 'guard_requested', 'guard_accepted',
        'native_call_started', 'native_call_completed', 'full_length_completed', 'image_bytes_full_length',
        'payload_bytes_full_length', 'pending', 'cleanup_pending', 'failure', 'cleanup_failure', 'boundary_events')) {
        $property = $Report.PSObject.Properties[$field]
        if ($null -ne $property) { $property.Value | ConvertTo-Json -Compress -Depth 8 }
        else { 'null' }
    }
    return ($parts -join '|')
}
function Get-TBS5530ProductionReportDisposition {
    param([Parameter(Mandatory)]$Report, [Parameter(Mandatory)][string]$RunEpoch, [string]$BaselineRevision)
    if ($null -eq $Report.PSObject.Properties['operation'] -or $Report.operation -cne 'DEBUG_ONE_SHOT_FX2') { return 'OTHER_OPERATION' }
    if ($null -eq $Report.PSObject.Properties['run_epoch'] -or $Report.run_epoch -cne $RunEpoch) { return 'OTHER_EPOCH' }
    if ($null -eq $Report.PSObject.Properties['pending'] -or $Report.pending -isnot [bool]) {
        throw 'OBSERVATION_FAILED: pending phải là boolean.'
    }
    if ($Report.pending) { return 'PENDING' }
    if ($Report.state -ceq 'PREPARED') { return 'PREPARED' }
    if ($Report.state -cnotin @('FAULTED', 'UNCERTAIN', 'OPERATIONAL_CANDIDATE')) {
        throw 'OBSERVATION_FAILED: report không pending nhưng chưa có terminal state hợp lệ.'
    }
    if ($BaselineRevision -and (Get-TBS5530ProductionReportRevision $Report) -ceq $BaselineRevision) {
        throw 'OBSERVATION_FAILED: terminal revision đã có trước phiên host; không cấp permit.'
    }
    return 'TERMINAL'
}
function Get-TBS5530ProductionTerminalAudit {
    param([Parameter(Mandatory)]$Report, [Parameter(Mandatory)][string]$ApprovalId,
        [Parameter(Mandatory)][string]$RunEpoch, [Parameter(Mandatory)][string]$HostRun)
    $required = @('operation', 'state', 'run_epoch', 'pending', 'cleanup_pending', 'cleanup_failure', 'failure',
        'native_call_started', 'native_call_completed', 'full_length_completed', 'image_bytes_full_length',
        'payload_bytes_full_length', 'attempted', 'completed', 'transferred_bytes', 'boundary_events')
    foreach ($field in $required) {
        if ($null -eq $Report.PSObject.Properties[$field]) { throw ('OBSERVATION_FAILED: terminal report thiếu ' + $field) }
    }
    if ((Get-TBS5530ProductionReportDisposition -Report $Report -RunEpoch $RunEpoch) -cne 'TERMINAL' -or
        $Report.cleanup_pending -isnot [bool]) {
        throw 'OBSERVATION_FAILED: terminal report sai operation, epoch, state hoặc kiểu pending.'
    }
    foreach ($field in @('native_call_started', 'native_call_completed', 'full_length_completed', 'image_bytes_full_length',
        'payload_bytes_full_length', 'attempted', 'completed', 'transferred_bytes')) {
        $value = $Report.$field
        if (($value -isnot [int] -and $value -isnot [long]) -or $value -lt 0) {
            throw ('OBSERVATION_FAILED: terminal counter không hợp lệ: ' + $field)
        }
    }
    if ($Report.native_call_started -gt 132 -or $Report.native_call_completed -gt $Report.native_call_started -or
        $Report.full_length_completed -gt $Report.native_call_completed -or $Report.image_bytes_full_length -gt 8192 -or
        $Report.payload_bytes_full_length -gt 8196 -or $Report.image_bytes_full_length -gt $Report.payload_bytes_full_length) {
        throw 'OBSERVATION_FAILED: terminal counters mâu thuẫn.'
    }
    $events = @($Report.boundary_events)
    if ($Report.state -cin @('FAULTED', 'UNCERTAIN')) {
        if ($Report.failure -isnot [string] -or $Report.failure -ceq 'NONE' -or -not $Report.failure -or
            -not @($events | Where-Object { $_ -is [string] -and $_ -match (' STOP reason=' + [regex]::Escape($Report.failure) + '$') }).Count -or
            ($Report.state -ceq 'FAULTED') -ne ($Report.native_call_started -eq 0)) {
            throw 'OBSERVATION_FAILED: terminal stop thiếu audit hoặc sai native boundary.'
        }
    } elseif ($Report.native_call_started -ne 132 -or $Report.native_call_completed -ne 132 -or
        $Report.full_length_completed -ne 132 -or $Report.image_bytes_full_length -ne 8192 -or
        $Report.payload_bytes_full_length -ne 8196 -or $Report.failure -cne 'NONE' -or $Report.cleanup_pending -or
        $Report.cleanup_failure -cne 'NONE') {
        throw 'OBSERVATION_FAILED: operational candidate thiếu đủ 132 native full-length hoặc cleanup.'
    }
    if (@($events | Where-Object { $_ -is [string] -and $_ -match ' NATIVE_CALL_START$' }).Count -ne $Report.native_call_started -or
        @($events | Where-Object { $_ -is [string] -and $_ -match ' NATIVE_CALL_END ' }).Count -ne $Report.native_call_completed) {
        throw 'OBSERVATION_FAILED: native counters thiếu boundary audit.'
    }
    $uploadComplete = ($Report.full_length_completed -eq 132 -and $Report.image_bytes_full_length -eq 8192 -and
        $Report.payload_bytes_full_length -eq 8196)
    $record = @{ event = 'NEW_ATTEMPT_TERMINAL'; mode = 'PRODUCTION_AUTHORIZATION'; helper_usb_calls = 0
        new_approval_id = $ApprovalId; run = $HostRun; run_epoch = $RunEpoch; state = $Report.state
        terminal_kind = $(if ($Report.native_call_started -eq 0) { 'PRE_NATIVE_STOP' }
            elseif ($uploadComplete) { 'UPLOAD_COMPLETE_OPERATIONAL_UNVERIFIED' } else { 'NATIVE_ATTEMPT_STOPPED' })
        firmware_upload_completed = $uploadComplete; firmware_pass = $false; ready = $false
        native_call_started = $Report.native_call_started; native_call_completed = $Report.native_call_completed
        full_length_completed = $Report.full_length_completed; image_bytes_full_length = $Report.image_bytes_full_length
        payload_bytes_full_length = $Report.payload_bytes_full_length; stop_reason = $Report.failure
        cleanup_pending = $Report.cleanup_pending; cleanup_failure = $Report.cleanup_failure
        boundary_event_count = $events.Count; boundary_audit_verified = $true
        legacy_runner_steps_attempted = $Report.attempted; legacy_runner_steps_completed = $Report.completed
        legacy_runner_payload_bytes = $Report.transferred_bytes; legacy_counters_are_native_calls = $false }
    if ($null -ne $Report.PSObject.Properties['failure_chain']) { $record.stop_reason_chain = $Report.failure_chain }
    return $record
}
function ConvertFrom-TBS5530LedgerLine {
    param([Parameter(Mandatory)][string]$Line)
    try {
        $record = $Line | ConvertFrom-Json -ErrorAction Stop
        $tokens = [regex]::Matches($Line, '"(?:\\.|[^"\\])*"|[{}\[\]:,]')
        $scopes = [Collections.Generic.Stack[object]]::new()
        for ($i = 0; $i -lt $tokens.Count; $i++) {
            $token = $tokens[$i].Value
            switch ($token) {
                '{' { $scopes.Push([Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)) }
                '[' { $scopes.Push($null) }
                '}' { $null = $scopes.Pop() }
                ']' { $null = $scopes.Pop() }
                default {
                    if ($token.StartsWith('"') -and $i + 1 -lt $tokens.Count -and $tokens[$i + 1].Value -ceq ':') {
                        $key = $token | ConvertFrom-Json -ErrorAction Stop
                        if (-not $scopes.Peek().Add($key)) { throw 'Duplicate JSON property' }
                    }
                }
            }
        }
        if (-not $Line.TrimStart().StartsWith('{') -or $null -eq $record.PSObject.Properties['event']) { throw 'Missing event object' }
        return $record
    } catch { throw 'FAIL-STOP: ledger hỏng hoặc trùng JSON key; không sửa/reset tự động.' }
}
function Assert-TBS5530UnusedApprovalId {
    param([Parameter(Mandatory)][IO.Stream]$Stream, [Parameter(Mandatory)][string]$ApprovalId, [switch]$RequireEnrollment)
    if ($ApprovalId -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$') { throw 'BLOCKED: NewApprovalId phải là GUID chuẩn mới, do operator cung cấp.' }
    if (-not $Stream.CanSeek -or -not $Stream.CanRead -or -not $Stream.CanWrite) { throw 'FAIL-STOP: ledger stream không hỗ trợ kiểm tra append.' }
    if ($Stream.Length -gt 0) {
        $Stream.Position = $Stream.Length - 1
        if ($Stream.ReadByte() -ne 10) { $Stream.Position = $Stream.Length; throw 'FAIL-STOP: ledger thiếu LF cuối; có thể bị cắt khi crash.' }
    }
    $Stream.Position = 0
    $enrolled = $false
    $historicalAudit = $false
    $first = $true
    $lineNumber = 0
    $consumedIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $reader = [IO.StreamReader]::new($Stream, [Text.UTF8Encoding]::new($false, $true), $true, 1024, $true)
    try {
        while ($null -ne ($line = $reader.ReadLine())) {
            if ([string]::IsNullOrWhiteSpace($line) -or $line.Length -gt 16384) { throw 'FAIL-STOP: ledger có dòng trống/quá cỡ.' }
            $lineNumber++
            $record = ConvertFrom-TBS5530LedgerLine $line
            if ($RequireEnrollment -and (($lineNumber -eq 2 -and $record.event -cne 'PRIOR_ATTEMPT_CONSUMED') -or
                ($lineNumber -gt 1 -and $record.event -ceq 'HARDWARE_LEDGER_ENROLLED') -or
                ($lineNumber -gt 2 -and $record.event -ceq 'PRIOR_ATTEMPT_CONSUMED'))) {
                throw 'FAIL-STOP: ledger trùng enrollment hoặc sai thứ tự prior consumed audit.'
            }
            if ($RequireEnrollment -and $record.event -cnotin @('HARDWARE_LEDGER_ENROLLED', 'PRIOR_ATTEMPT_CONSUMED',
                'NEW_APPROVAL_CONSUMED', 'NEW_ATTEMPT_TERMINAL', 'production_authorization_start',
                'production_authorization_ordinal_authorized', 'production_authorization_fail', 'android_observed')) {
                throw 'FAIL-STOP: ledger có event không xác định.'
            }
            if ($record.event -ceq 'NEW_APPROVAL_CONSUMED') {
                $consumed = $record.PSObject.Properties['new_approval_id']
                if ($null -eq $consumed -or $consumed.Value -isnot [string] -or
                    $consumed.Value -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$' -or
                    -not $consumedIds.Add($consumed.Value)) {
                    throw 'FAIL-STOP: consumed record thiếu ID chuẩn hoặc bị trùng.'
                }
            }
            if ($first) {
                $header = @{}; foreach ($property in $record.PSObject.Properties) { $header[$property.Name] = $property.Value }
                $enrolled = ($header['event'] -ceq 'HARDWARE_LEDGER_ENROLLED' -and
                    $header['schema'] -ceq 'tbs5530-phase29-hardware-ledger' -and $header['schema_version'] -eq 1 -and
                    $header['prior_consumed_acknowledged'] -is [bool] -and $header['prior_consumed_acknowledged'] -eq $true -and
                    $header['user_provided_phase29_evidence_acknowledged'] -is [bool] -and $header['user_provided_phase29_evidence_acknowledged'] -eq $true -and
                    $header['ledger_policy'] -ceq 'CANONICAL_APPEND_ONLY_NO_REARM' -and
                    $header['hardware_approval'] -is [bool] -and $header['hardware_approval'] -eq $false -and
                    $header['new_approval_issued'] -is [bool] -and $header['new_approval_issued'] -eq $false)
                $first = $false
            } elseif ($record.event -ceq 'PRIOR_ATTEMPT_CONSUMED') {
                $audit = @{}; foreach ($property in $record.PSObject.Properties) { $audit[$property.Name] = $property.Value }
                $historicalAudit = ($audit['phase'] -ceq '2.9' -and $audit['evidence_checkpoint_git_sha'] -ceq 'd76bbfb' -and
                    $audit['provenance'] -ceq 'USER_PROVIDED_PHASE29_EVIDENCE' -and $audit['state'] -ceq 'UNCERTAIN' -and
                    $audit['attempt_consumed'] -is [bool] -and $audit['attempt_consumed'] -eq $true -and
                    $audit['native_calls'] -eq 23 -and $audit['image_bytes'] -eq 1344 -and
                    $audit['cpu_release_sent'] -is [bool] -and $audit['cpu_release_sent'] -eq $false -and
                    $audit['firmware_upload_completed'] -is [bool] -and $audit['firmware_upload_completed'] -eq $false)
            }
            $property = $record.PSObject.Properties['new_approval_id']
            if ($null -ne $property -and [string]$property.Value -ieq $ApprovalId) {
                throw 'BLOCKED: NewApprovalId đã xuất hiện trong ledger; không reuse hoặc rearm.'
            }
        }
        if ($RequireEnrollment -and (-not $enrolled -or -not $historicalAudit)) {
            throw 'BLOCKED: ledger thiếu first enrollment và lịch sử prior consumed hợp lệ; cần enroll riêng, không reset.'
        }
    } finally { $reader.Dispose(); $Stream.Position = $Stream.Length }
}
