# Does not launch UI, send USB requests, reset legacy state or create a new approval ID.
# Production provisioning requires an explicitly supplied NEW audited one-shot approval.
[CmdletBinding()]
param(
    [switch]$ExplicitOneShotApproval,
    [switch]$PhysicalHostAndOtgConfirmed,
    [switch]$Benchmark,
    [string]$BenchmarkDirectory,
    [string]$Serial,
    [string]$LedgerPath,
    [string]$NewApprovalId,
    [switch]$ExplicitNewAttemptApproval,
    [switch]$AcknowledgePriorConsumed,
    [long]$DeadlineTimestamp = 0,
    [string]$ExpectedIdentity,
    [ValidateRange(5, 300)][int]$HardTimeoutSeconds = 300,
    [string]$AdbPath = 'D:\Android\Sdk\platform-tools\adb.exe'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($ExplicitOneShotApproval) { throw 'BLOCKED: approval legacy không cấp một attempt mới.' }
if ($Benchmark) {
    if ($NewApprovalId -or $ExplicitNewAttemptApproval -or $AcknowledgePriorConsumed -or $PhysicalHostAndOtgConfirmed -or
        $BenchmarkDirectory -cnotmatch '^/sdcard/Download/tbs5530-approval-benchmark/[a-f0-9-]{36}$') {
        throw 'BLOCKED: benchmark phải dùng namespace riêng và không mang flags hardware.'
    }
} elseif (-not $ExplicitNewAttemptApproval -or -not $AcknowledgePriorConsumed -or -not $PhysicalHostAndOtgConfirmed -or
    $NewApprovalId -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$' -or $BenchmarkDirectory) {
    throw 'BLOCKED: cần NewApprovalId mới do operator cấp SAU approval riêng, xác nhận prior consumed và host/OTG.'
}
if ($Benchmark -and -not $PSBoundParameters.ContainsKey('HardTimeoutSeconds')) { $HardTimeoutSeconds = 240 }
if ($Benchmark -and $HardTimeoutSeconds -gt 240) { throw 'BLOCKED: tổng benchmark tối đa 240 giây; production tối đa TTL 300 giây.' }
if ($Serial -notmatch '^[A-Za-z0-9._-]+:[0-9]+$') { throw 'Cần serial TCP rõ ràng.' }
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw 'Không tìm thấy ADB.' }
. (Join-Path $PSScriptRoot 'Tbs5530-ApprovalLedger.ps1')
if (-not $Benchmark) {
    $canonical = Get-TBS5530HardwareLedgerPath
    if (-not $LedgerPath) { $LedgerPath = $canonical }
    if (-not [IO.Path]::IsPathRooted($LedgerPath) -or
        -not [IO.Path]::GetFullPath($LedgerPath).Equals($canonical, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'BLOCKED: production chỉ dùng ledger canonical LocalApplicationData\TBS5530\Phase29\hardware-attempts.jsonl; không đổi file.'
    }
}
if (-not [IO.Path]::IsPathRooted($LedgerPath)) { throw 'Sổ benchmark phải là đường dẫn tuyệt đối ngoài Git.' }
$ledgerFull = [IO.Path]::GetFullPath($LedgerPath)
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\') + '\'
if ($ledgerFull.StartsWith($repository, [StringComparison]::OrdinalIgnoreCase) -or
    -not (Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($ledgerFull)) -PathType Container)) {
    throw 'Sổ phải ở thư mục đã có, nằm ngoài checkout Git.'
}
$git = (Get-Command git.exe -ErrorAction Stop).Source
$ledgerParent = [IO.Path]::GetDirectoryName($ledgerFull)
$gitProbe = @(& $git -C $ledgerParent rev-parse --is-inside-work-tree --is-inside-git-dir 2>$null)
if ($LASTEXITCODE -eq 0 -and @($gitProbe | Where-Object { $_ -ceq 'true' }).Count) {
    throw 'Sổ không được nằm trong bất kỳ Git checkout hoặc Git metadata nào.'
}
. (Join-Path $PSScriptRoot 'Get-TBS5530-ApprovalTarget.ps1')
. (Join-Path $PSScriptRoot 'Tbs5530-ApprovalLedger.ps1')
. (Join-Path $PSScriptRoot 'Tbs5530-ApprovalTiming.ps1')
if (-not $Benchmark -and -not (Test-Path -LiteralPath $ledgerFull -PathType Leaf)) {
    throw 'BLOCKED: production cần ledger canonical đã được operator chuẩn bị ngoài Git; không tự tạo ledger mới.'
}
# Deny concurrent writers. Only append at EOF; never truncate, reset or rewrite history.
$ledger = [IO.FileStream]::new($ledgerFull, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read)
$writer = $null
try {
    if (-not $Benchmark) { Assert-TBS5530UnusedApprovalId -Stream $ledger -ApprovalId $NewApprovalId -RequireEnrollment }
    $ledger.Position = $ledger.Length
    $writer = [IO.StreamWriter]::new($ledger, [Text.UTF8Encoding]::new($false), 1024, $true)
} catch { $ledger.Dispose(); throw }
function Write-Ledger([hashtable]$Entry) {
    $Entry.mode = $(if ($Benchmark) { 'BENCHMARK_ONLY' } else { 'PRODUCTION_AUTHORIZATION' })
    $Entry.helper_usb_calls = 0
    $Entry.Remove('challenge'); $Entry.Remove('install')
    if (-not $Benchmark) {
        $Entry.Remove('hardware_enabled')
        if ([string]$Entry.event -like 'benchmark_*') { $Entry.event = ([string]$Entry.event).Replace('benchmark_', 'production_authorization_') }
        if ($Entry.event -ceq 'authorize_benchmark_ordinal') { $Entry.event = 'production_authorization_ordinal_authorized' }
    }
    $ledger.Position = $ledger.Length
    $writer.WriteLine(($Entry | ConvertTo-Json -Compress -Depth 8)); $writer.Flush(); $ledger.Flush($true)
}
$process = $null
$watch = [Diagnostics.Stopwatch]::StartNew()
$subprocesses = 0
$acknowledged = 0
$observed = 0
$latencies = [Collections.Generic.List[long]]::new()
$run = [Guid]::NewGuid().ToString()
function Total-DeadlineExpired {
    return ($watch.Elapsed.TotalSeconds -ge $HardTimeoutSeconds -or
        ($DeadlineTimestamp -gt 0 -and [Diagnostics.Stopwatch]::GetTimestamp() -ge $DeadlineTimestamp))
}
function Stop-Channel([string]$Kind) {
    throw [IO.IOException]::new($Kind + ': dừng kênh; không reconnect/retry hoặc gửi USB.')
}
function Assert-Time {
    if (Total-DeadlineExpired) { Stop-Channel $(if ($Benchmark) { 'TOTAL_BENCHMARK_DEADLINE' } else { 'TOTAL_SESSION_DEADLINE' }) }
}
function Read-ChannelLine {
    Assert-Time
    $remaining = [Math]::Max(1, [Math]::Min(1500, ($HardTimeoutSeconds * 1000 - $watch.ElapsedMilliseconds)))
    if ($DeadlineTimestamp -gt 0) {
        $remaining = [Math]::Min($remaining, [Math]::Max(1, ($DeadlineTimestamp - [Diagnostics.Stopwatch]::GetTimestamp()) * 1000 / [Diagnostics.Stopwatch]::Frequency))
    }
    $totalLimited = $remaining -lt 1500
    try {
        $task = $process.StandardOutput.ReadLineAsync()
        if (-not $task.Wait([int][Math]::Ceiling($remaining))) {
            Assert-Time
            if ($totalLimited) { Stop-Channel $(if ($Benchmark) { 'TOTAL_BENCHMARK_DEADLINE' } else { 'TOTAL_SESSION_DEADLINE' }) }
            Stop-Channel (Get-TBS5530ChannelStopKind -TransportClosed $process.HasExited -ResponseTimedOut $true)
        }
        $line = $task.Result
    } catch [AggregateException] {
        Assert-Time
        Stop-Channel (Get-TBS5530ChannelStopKind -TransportClosed $true)
    }
    Assert-Time
    if ($null -eq $line) { Stop-Channel 'TRANSPORT_LOST' }
    if ($line -ceq 'ERROR identity') { Stop-Channel 'ADB_IDENTITY_LOST' }
    if ($line.StartsWith('ERROR')) { Stop-Channel 'CHANNEL_PROTOCOL_ERROR' }
    return $line
}
function Snapshot {
    $process.StandardInput.WriteLine('POLL'); $process.StandardInput.Flush()
    $result = @{}
    foreach ($prefix in @('IDENTITY', 'NOW', 'CHALLENGE', 'REQUEST', 'OBSERVATION', 'RESULT')) {
        $line = Read-ChannelLine
        if (-not $line.StartsWith($prefix + ' ')) { throw 'FAIL-STOP: sai framing kênh.' }
        $result[$prefix] = $line.Substring($prefix.Length + 1)
    }
    if ((Read-ChannelLine) -cne 'END') { throw 'FAIL-STOP: thiếu framing kết thúc.' }
    return $result
}
function Same-Binding($Value, $Challenge) {
    foreach ($field in @('approval_protocol', 'challenge', 'run_epoch', 'install', 'upload_epoch', 'attachment_hash', 'plan', 'sha256')) {
        if ([string]$Value.$field -cne [string]$Challenge.$field) { throw 'FAIL-STOP: binding challenge đã đổi.' }
    }
}
try {
    Write-Ledger @{ event = 'benchmark_start'; run = $run; utc = [DateTime]::UtcNow.ToString('o'); hardware_enabled = $false }
    Assert-Time
    $target = Get-TBS5530ApprovalTarget -AdbPath $AdbPath -Serial $Serial -DeadlineTimestamp $DeadlineTimestamp
    $subprocesses += $target.AdbSubprocesses
    if ($ExpectedIdentity -and $target.Identity -cne $ExpectedIdentity) { throw 'FAIL-STOP: target/boot identity đã đổi.' }
    $pinnedIdentity = $target.Identity
    Assert-Time
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $AdbPath
    $start.Arguments = '-s ' + $Serial + $(if ($Benchmark) { ' shell -T sh' } else { ' shell -T run-as info.martinmarinov.dvbdriver sh' })
    $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    if (-not $process.Start()) { throw 'FAIL-STOP: không tạo được kênh.' }; $subprocesses++
    $process.StandardInput.NewLine = "`n"
    $stderr = $process.StandardError.ReadToEndAsync()
    # /proc/uptime has 10 ms resolution. Clamp its LOWER-BOUND stamp to the request
    # timestamp, not into the future; Android measures exact request/observation times.
    $shell = @'
cd '__DIRECTORY__' || exit 1; LC_ALL=C; export LC_ALL; identity() { maker=$(getprop ro.product.manufacturer); model=$(getprop ro.product.model); api=$(getprop ro.build.version.sdk); read -r boot < /proc/sys/kernel/random/boot_id; identity_value="$maker|$model|$api|$boot"; [ "$identity_value" = '__PINNED_IDENTITY__' ] || { printf 'ERROR identity\n'; exit 1; }; initial_boot=$boot; }; check_identity() { read -r boot < /proc/sys/kernel/random/boot_id || { printf 'ERROR identity\n'; exit 1; }; [ "$boot" = "$initial_boot" ] || { printf 'ERROR identity\n'; exit 1; }; }; now_ms() { read -r up rest < /proc/uptime; sec=${up%.*}; frac=${up#*.}; frac=$((1$frac - 100)); stamp=$((sec * 1000 + frac * 10)); }; cr=$(printf '\r'); emit() { value=; size=0; if [ -f "$2" ]; then while IFS= read -r line || [ -n "$line" ]; do line=${line%"$cr"}; size=$((size + ${#line} + 1)); [ "$size" -le 262144 ] || { printf 'ERROR metadata_size\n'; exit 1; }; value="$value $line"; done < "$2" || { printf 'ERROR metadata_io\n'; exit 1; }; [ "$size" -gt 0 ] || { printf 'ERROR metadata_empty\n'; exit 1; }; fi; printf '%s %s\n' "$1" "${value# }"; }; write_payload() { while :; do case "$payload" in *'"@DEVICE_ACK@"'*) head=${payload%%'"@DEVICE_ACK@"'*}; payload=${payload#*'"@DEVICE_ACK@"'}; printf '%s%s' "$head" "$stamp";; *) printf '%s' "$payload"; break;; esac; done; }; identity; while IFS= read -r command; do case "$command" in POLL) check_identity; printf 'IDENTITY %s\n' "$identity_value"; now_ms; printf 'NOW %s\n' "$stamp"; emit CHALLENGE phase29-challenge.json; emit REQUEST phase29-write-request.json; emit OBSERVATION phase29-write-observation.json; emit RESULT __RESULT_FILE__; printf 'END\n';; PUT\ *) check_identity; minimum=${command#PUT }; case "$minimum" in ''|*[!0-9]*) exit 1;; esac; IFS= read -r payload || exit 1; now_ms; [ "$stamp" -lt "$minimum" ] && stamp=$minimum; write_payload > phase29-session-approval.new && mv phase29-session-approval.new phase29-session-approval.json || exit 1; printf 'STORED %s\n' "$stamp";; QUIT) exit 0;; *) printf 'ERROR protocol\n'; exit 1;; esac; done
'@
    $remoteDirectory = $(if ($Benchmark) { $BenchmarkDirectory } else { 'files' })
    $resultFile = $(if ($Benchmark) { 'benchmark-result.json' } else { 'tbs5530-diagnostic.json' })
    $process.StandardInput.WriteLine($shell.Replace('__DIRECTORY__', $remoteDirectory).Replace('__RESULT_FILE__', $resultFile).Replace('__PINNED_IDENTITY__', $pinnedIdentity)); $process.StandardInput.Flush()
    $snapshot = Snapshot
    if ($snapshot.IDENTITY -cne $pinnedIdentity) { throw 'FAIL-STOP: channel identity sai preflight.' }
    $challenge = $snapshot.CHALLENGE | ConvertFrom-Json
    if ($challenge.approval_protocol -ne 2 -or $challenge.hardware_enabled -ne $false -or $challenge.upload_epoch -ne 1 -or
        $challenge.plan -cne 'TBS_SOURCE_DUAL_RESET' -or
        $challenge.sha256 -cne 'b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc' -or
        $challenge.attachment_hash -cnotmatch '^[a-f0-9]{64}$') { throw 'FAIL-STOP: challenge/plan/hash sai.' }
    if ($Benchmark -and ($challenge.install -cne 'benchmark-only' -or $challenge.approval_mode -cne 'BENCHMARK_ONLY' -or
        $challenge.attachment_hash -cne ('0' * 64))) { throw 'FAIL-STOP: challenge không thuộc benchmark độc lập.' }
    if (-not $Benchmark -and ($challenge.approval_mode -cne 'NEW_AUDITED_ATTEMPT_REQUIRED' -or
        $challenge.install -cnotmatch '^[a-f0-9-]{36}$')) { throw 'FAIL-STOP: thiếu giao thức new attempt.' }
    foreach ($id in @($challenge.challenge, $challenge.run_epoch)) {
        if ($id -cnotmatch '^[a-f0-9-]{36}$') { throw 'FAIL-STOP: epoch không hợp lệ.' }
    }
    $deadline = [long]$challenge.created_elapsed_ms + 300000
    $permit = [ordered]@{}
    foreach ($field in @('approval_protocol', 'challenge', 'run_epoch', 'install', 'upload_epoch', 'attachment_hash', 'plan', 'sha256')) {
        $permit[$field] = $challenge.$field
    }
    $permit.explicit_session_upload_approval = $true; $permit.physical_host_and_otg_confirmed = $true
    $permit.host_ledger_committed = $true; $permit.expires_elapsed_ms = $deadline
    $permit.host_clock = 'device_uptime_10ms_lower_bound_clamped_to_request'
    if (-not $Benchmark) {
        # Irreversible reservation, even if UI is never pressed. Duplicate IDs are refused under the same exclusive ledger lock.
        $bindingText = @('approval_protocol', 'challenge', 'install', 'run_epoch', 'upload_epoch', 'attachment_hash', 'plan', 'sha256') |
            ForEach-Object { [string]$challenge.$_ }
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $bindingHash = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($bindingText -join '|'))))).Replace('-', '').ToLowerInvariant() }
        finally { $sha.Dispose() }
        Write-Ledger @{ event = 'NEW_APPROVAL_CONSUMED'; new_approval_id = $NewApprovalId; run = $run
            binding_sha256 = $bindingHash; run_epoch = $challenge.run_epoch
            attachment_hash = $challenge.attachment_hash; sha256 = $challenge.sha256; plan = $challenge.plan
            upload_epoch = $challenge.upload_epoch; legacy_consumed = [bool]$challenge.legacy_consumed
            legacy_consumed_acknowledged = $true; explicit_new_attempt_approval = $true; utc = [DateTime]::UtcNow.ToString('o') }
        $permit.new_approval_id = $NewApprovalId; $permit.explicit_new_attempt_approval = $true
        $permit.legacy_consumed_acknowledged = $true; $permit.legacy_consumed = [bool]$challenge.legacy_consumed
        $permit.host_ledger_event = 'NEW_APPROVAL_CONSUMED'
    }
    $permit.authorized_ordinal = 0
    $lastNonce = ''
    $sawPending = $false
    while ($true) {
        Assert-Time
        if ($snapshot.IDENTITY -cne $pinnedIdentity) { throw 'FAIL-STOP: channel identity đã đổi; không cấp heartbeat/ACK.' }
        Same-Binding ($snapshot.CHALLENGE | ConvertFrom-Json) $challenge
        $now = [long]$snapshot.NOW
        if ($now -ge $deadline) { throw 'FAIL-STOP: phiên đã hết hạn.' }
        $minimum = [long]$challenge.created_elapsed_ms
        if ($snapshot.REQUEST) {
            $request = $snapshot.REQUEST | ConvertFrom-Json
            Same-Binding $request $challenge
            if (-not $Benchmark -and $request.new_approval_id -cne $NewApprovalId) { throw 'FAIL-STOP: request khác new approval ID.' }
            $next = [int]$request.ordinal
            if ($next -lt 1 -or $next -gt 132 -or $next -lt $acknowledged -or $next -gt $acknowledged + 1) {
                throw 'FAIL-STOP: ordinal bị lùi, bỏ qua hoặc vượt giới hạn.'
            }
            if ($next -eq $acknowledged) {
                if ($lastNonce -cne $request.request_nonce) { throw 'FAIL-STOP: phát lại ordinal với nonce khác.' }
            } else {
                if ($request.request_nonce -cnotmatch '^[a-f0-9-]{36}$' -or
                    [long]$request.ack_deadline_elapsed_ms - [long]$request.request_elapsed_ms -ne 1500) { Stop-Channel 'INVALID_REQUEST_BINDING' }
                if ($now -ge [long]$request.ack_deadline_elapsed_ms) { Stop-Channel 'ACK_TIMEOUT' }
                $acknowledged = $next; $lastNonce = $request.request_nonce
                $permit.authorized_ordinal = $next; $permit.request_nonce = $lastNonce
                $permit.request_elapsed_ms = [long]$request.request_elapsed_ms
                $permit.ack_deadline_elapsed_ms = [long]$request.ack_deadline_elapsed_ms
                $permit.host_seen_elapsed_ms = [Math]::Max($now, [long]$request.request_elapsed_ms)
                Write-Ledger @{ event = 'authorize_benchmark_ordinal'; run = $run; ordinal = $next; elapsed_ms = $now }
            }
            $minimum = [Math]::Max([long]$request.request_elapsed_ms, [long]$permit.host_seen_elapsed_ms)
        }
        if ($snapshot.OBSERVATION) {
            $sample = $snapshot.OBSERVATION | ConvertFrom-Json
            if ($sample.run_epoch -cne $challenge.run_epoch -or [int]$sample.ordinal -gt $acknowledged) { throw 'FAIL-STOP: observation sai epoch.' }
            if ([int]$sample.ordinal -gt $observed) {
                if ([int]$sample.ordinal -ne $observed + 1) { throw 'FAIL-STOP: thiếu observation.' }
                $duration = [long]$sample.ack_observed_elapsed_ms - [long]$sample.request_elapsed_ms
                if ($duration -lt 0 -or $duration -ge 1500) { throw 'FAIL-STOP: observation ngoài deadline.' }
                $latencies.Add($duration); $observed = [int]$sample.ordinal
                Write-Ledger @{ event = 'android_observed'; run = $run; ordinal = $observed; latency_ms = $duration; sample = $sample }
            }
        }
        if ($snapshot.RESULT) {
            $result = $snapshot.RESULT | ConvertFrom-Json
            if ($Benchmark) {
                if ($result.status -cne 'PASS') {
                    if ($null -ne $result.PSObject.Properties['failure_kind']) { Stop-Channel ([string]$result.failure_kind) }
                    if ($null -ne $result.PSObject.Properties['stop_kind'] -and $result.stop_kind -ceq 'PRE_NATIVE_ACK_TIMEOUT') { Stop-Channel 'ACK_TIMEOUT' }
                    Stop-Channel 'ANDROID_GUARD_STOP'
                }
                if ($result.roundtrips -ne 132 -or $observed -ne 132) { Stop-Channel 'INCOMPLETE_ANDROID_OBSERVATIONS' }
                break
            }
            if ($result.operation -ceq 'DEBUG_ONE_SHOT_FX2' -and $result.pending) { $sawPending = $true }
            if ($sawPending -and $acknowledged -gt 0 -and $result.operation -ceq 'DEBUG_ONE_SHOT_FX2' -and -not $result.pending) {
                Write-Ledger (Get-TBS5530ProductionTerminalAudit -Report $result -ApprovalId $NewApprovalId -RunEpoch $challenge.run_epoch -HostRun $run)
                Write-Output ('Đã dừng cấp ACK tại terminal state=' + $result.state + '; không retry/corrective USB.')
                return
            }
        }
        $permit.heartbeat_elapsed_ms = '@DEVICE_ACK@'
        if ($acknowledged -gt 0) { $permit.host_ack_elapsed_ms = '@DEVICE_ACK@' }
        $payload = $permit | ConvertTo-Json -Compress
        $process.StandardInput.WriteLine('PUT ' + $minimum); $process.StandardInput.WriteLine($payload); $process.StandardInput.Flush()
        if ((Read-ChannelLine) -notmatch '^STORED [0-9]+$') { throw 'FAIL-STOP: không xác nhận ghi atomic.' }
        Start-Sleep -Milliseconds 10
        $snapshot = Snapshot
    }
    $sorted = @($latencies | Sort-Object)
    $summary = @{ event = 'benchmark_pass'; run = $run; roundtrips = $observed; adb_subprocesses = $subprocesses
        p50_ms = $sorted[[int][Math]::Ceiling($observed * .50) - 1]; p95_ms = $sorted[[int][Math]::Ceiling($observed * .95) - 1]
        p99_ms = $sorted[[int][Math]::Ceiling($observed * .99) - 1]; max_ms = $sorted[-1]; hardware_enabled = $false }
    Write-Ledger $summary
    $summary | ConvertTo-Json -Compress
} catch {
    $failureKind = 'HOST_HELPER_ERROR'
    if ($_.Exception.Message -match '^([A-Z][A-Z_]+):') { $failureKind = $Matches[1] }
    Write-Ledger @{ event = 'benchmark_fail'; run = $run; acknowledged = $acknowledged; observed = $observed
        failure_kind = $failureKind; elapsed_host_ms = $watch.ElapsedMilliseconds; total_bound_ms = $HardTimeoutSeconds * 1000
        total_deadline_expired = (Total-DeadlineExpired); utc = [DateTime]::UtcNow.ToString('o'); hardware_enabled = $false }
    throw
} finally {
    if ($null -ne $process) {
        try { $process.StandardInput.Close() } catch { }
        if (-not $process.HasExited -and -not $process.WaitForExit(1000)) { $process.Kill() }
        $process.Dispose()
    }
    $writer.Dispose(); $ledger.Dispose()
}
