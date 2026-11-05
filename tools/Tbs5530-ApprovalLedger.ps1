# Ledger helpers contain no ADB, app storage or hardware operation.
function Get-TBS5530HardwareLedgerPath {
    $local = [Environment]::GetFolderPath([Environment+SpecialFolder]::LocalApplicationData)
    if (-not $local) { throw 'BLOCKED: không xác định được LocalApplicationData canonical.' }
    return [IO.Path]::GetFullPath((Join-Path $local 'TBS5530\Phase29\hardware-attempts.jsonl'))
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
function Get-TBS5530ProductionTerminalAudit {
    param([Parameter(Mandatory)]$Report, [Parameter(Mandatory)][string]$ApprovalId,
        [Parameter(Mandatory)][string]$RunEpoch, [Parameter(Mandatory)][string]$HostRun)
    $required = @('operation', 'state', 'run_epoch', 'pending', 'cleanup_pending', 'cleanup_failure', 'failure',
        'native_call_started', 'native_call_completed', 'full_length_completed', 'image_bytes_full_length',
        'payload_bytes_full_length', 'attempted', 'completed', 'transferred_bytes')
    foreach ($field in $required) {
        if ($null -eq $Report.PSObject.Properties[$field]) { throw ('OBSERVATION_FAILED: terminal report thiếu ' + $field) }
    }
    if ($Report.operation -cne 'DEBUG_ONE_SHOT_FX2' -or $Report.pending -ne $false -or $Report.run_epoch -cne $RunEpoch) {
        throw 'OBSERVATION_FAILED: terminal report sai operation, epoch hoặc vẫn pending.'
    }
    $record = @{ event = 'NEW_ATTEMPT_TERMINAL'; mode = 'PRODUCTION_AUTHORIZATION'; helper_usb_calls = 0
        new_approval_id = $ApprovalId; run = $HostRun; run_epoch = $RunEpoch; state = $Report.state
        native_call_started = $Report.native_call_started; native_call_completed = $Report.native_call_completed
        full_length_completed = $Report.full_length_completed; image_bytes_full_length = $Report.image_bytes_full_length
        payload_bytes_full_length = $Report.payload_bytes_full_length; stop_reason = $Report.failure
        cleanup_pending = $Report.cleanup_pending; cleanup_failure = $Report.cleanup_failure
        legacy_runner_steps_attempted = $Report.attempted; legacy_runner_steps_completed = $Report.completed
        legacy_runner_payload_bytes = $Report.transferred_bytes; legacy_counters_are_native_calls = $false }
    if ($null -ne $Report.PSObject.Properties['failure_chain']) { $record.stop_reason_chain = $Report.failure_chain }
    return $record
}
function Assert-TBS5530UnusedApprovalId {
    param([Parameter(Mandatory)][IO.Stream]$Stream, [Parameter(Mandatory)][string]$ApprovalId, [switch]$RequireEnrollment)
    if ($ApprovalId -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$') { throw 'BLOCKED: NewApprovalId phải là GUID chuẩn mới, do operator cung cấp.' }
    $Stream.Position = 0
    $enrolled = $false
    $historicalAudit = $false
    $first = $true
    $reader = [IO.StreamReader]::new($Stream, [Text.UTF8Encoding]::new($false, $true), $true, 1024, $true)
    try {
        while ($null -ne ($line = $reader.ReadLine())) {
            if ([string]::IsNullOrWhiteSpace($line) -or $line.Length -gt 16384) { throw 'FAIL-STOP: ledger có dòng trống/quá cỡ.' }
            try { $record = $line | ConvertFrom-Json -ErrorAction Stop } catch { throw 'FAIL-STOP: ledger hỏng; không sửa/reset tự động.' }
            if ($null -eq $record -or $null -eq $record.PSObject.Properties['event']) { throw 'FAIL-STOP: ledger thiếu event.' }
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
