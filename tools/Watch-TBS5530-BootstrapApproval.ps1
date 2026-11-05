# Does not launch UI, send USB requests, reset legacy state or create a new approval ID.
# Production provisioning requires an explicitly supplied NEW audited one-shot approval.
[CmdletBinding()]
param(
    [switch]$ExplicitOneShotApproval,
    [switch]$PhysicalHostAndOtgConfirmed,
    [switch]$Benchmark,
    [switch]$FullPathInstrumentation,
    [object]$BenchmarkChild,
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
if (($FullPathInstrumentation -or $null -ne $BenchmarkChild) -and -not $Benchmark) { throw 'BLOCKED: instrumentation chỉ dành cho benchmark cách ly.' }
if ($Benchmark) {
    $benchmarkPrefix = $(if ($FullPathInstrumentation) {
        '/data/user/0/info.martinmarinov.dvbdriver.phase29offline/files/tbs5530-approval-benchmark/'
    } else { '/sdcard/Download/tbs5530-approval-benchmark/' })
    if ($NewApprovalId -or $ExplicitNewAttemptApproval -or $AcknowledgePriorConsumed -or $PhysicalHostAndOtgConfirmed -or
        $BenchmarkDirectory -cnotmatch ('^' + [regex]::Escape($benchmarkPrefix) + '[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$')) {
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
$ledgerFull = Resolve-TBS5530ApprovalLedgerPath -Path $LedgerPath -RepositoryRoot (Join-Path $PSScriptRoot '..')
if ($Benchmark -and $ledgerFull.Equals((Get-TBS5530HardwareLedgerPath), [StringComparison]::OrdinalIgnoreCase)) { throw 'BLOCKED: benchmark không dùng hardware ledger.' }
. (Join-Path $PSScriptRoot 'Get-TBS5530-ApprovalTarget.ps1')
. (Join-Path $PSScriptRoot 'Tbs5530-ApprovalTiming.ps1')
if (-not $Benchmark -and -not (Test-Path -LiteralPath $ledgerFull -PathType Leaf)) {
    throw 'BLOCKED: production cần ledger canonical đã được operator chuẩn bị ngoài Git; không tự tạo ledger mới.'
}
# Deny concurrent writers. Only append at EOF; never truncate, reset or rewrite history.
$ledger = [IO.FileStream]::new($ledgerFull, $(if ($Benchmark) { [IO.FileMode]::OpenOrCreate } else { [IO.FileMode]::Open }), [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read)
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
$channelSynchronized = $false
$failureEvidenceMode = $false
$diagnosticDeadline = 0L
$waitingForChallenge = [bool]$Benchmark
$watch = [Diagnostics.Stopwatch]::StartNew()
$localDeadline = [Diagnostics.Stopwatch]::GetTimestamp() + [long]$HardTimeoutSeconds * [Diagnostics.Stopwatch]::Frequency
if ($DeadlineTimestamp -le 0 -or $localDeadline -lt $DeadlineTimestamp) { $DeadlineTimestamp = $localDeadline }
$subprocesses = 0
$acknowledged = 0
$observed = 0
$lastHeartbeat = $null
$heartbeatWrites = 0
$fileIoChildInvocations = 0
$runAsInvocations = 0
$ioPackage = ''
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
    if ($failureEvidenceMode) {
        if ([Diagnostics.Stopwatch]::GetTimestamp() -ge $diagnosticDeadline) { Stop-Channel 'FAILURE_EVIDENCE_DEADLINE' }
        return
    }
    if ($Benchmark -and $null -ne $BenchmarkChild) {
        $childState = Get-TBS5530BenchmarkChildState $BenchmarkChild
        if ($childState -ceq 'FAILED' -or ($waitingForChallenge -and $childState -ceq 'FINISHED')) {
            Stop-Channel 'BENCHMARK_CHILD_FAILED'
        }
    }
    if (Total-DeadlineExpired) { Stop-Channel $(if ($Benchmark) { 'TOTAL_BENCHMARK_DEADLINE' } else { 'TOTAL_SESSION_DEADLINE' }) }
}
function Read-ChannelLine {
    Assert-Time
    $remaining = [Math]::Max(1, [Math]::Min(1500, ($HardTimeoutSeconds * 1000 - $watch.ElapsedMilliseconds)))
    if ($failureEvidenceMode) {
        $remaining = [Math]::Max(1, [Math]::Min(1500, ($diagnosticDeadline - [Diagnostics.Stopwatch]::GetTimestamp()) * 1000 / [Diagnostics.Stopwatch]::Frequency))
    } elseif ($DeadlineTimestamp -gt 0) {
        $remaining = [Math]::Min($remaining, [Math]::Max(1, ($DeadlineTimestamp - [Diagnostics.Stopwatch]::GetTimestamp()) * 1000 / [Diagnostics.Stopwatch]::Frequency))
    }
    $totalLimited = $remaining -lt 1500
    try {
        $task = $process.StandardOutput.ReadLineAsync()
        $responseDeadline = [Diagnostics.Stopwatch]::GetTimestamp() + [long][Math]::Ceiling($remaining * [Diagnostics.Stopwatch]::Frequency / 1000)
        while (-not $task.Wait([int][Math]::Min(50, [Math]::Ceiling($remaining)))) {
            Assert-Time
            if ([Diagnostics.Stopwatch]::GetTimestamp() -ge $responseDeadline) {
                if ($totalLimited) { Stop-Channel $(if ($failureEvidenceMode) { 'FAILURE_EVIDENCE_DEADLINE' } elseif ($Benchmark) { 'TOTAL_BENCHMARK_DEADLINE' } else { 'TOTAL_SESSION_DEADLINE' }) }
                Stop-Channel (Get-TBS5530ChannelStopKind -TransportClosed $process.HasExited -ResponseTimedOut $true)
            }
            $remaining = [Math]::Max(1, ($responseDeadline - [Diagnostics.Stopwatch]::GetTimestamp()) * 1000 / [Diagnostics.Stopwatch]::Frequency)
        }
        $line = $task.Result
    } catch [AggregateException] {
        Assert-Time
        Stop-Channel (Get-TBS5530ChannelStopKind -TransportClosed $true)
    }
    Assert-Time
    if ($null -eq $line) { Stop-Channel 'TRANSPORT_LOST' }
    if ($line -ceq 'ERROR identity') { Stop-Channel 'ADB_IDENTITY_LOST' }
    if ($line -cmatch '^ERROR clock_(read|format|range|alignment)$') { Stop-Channel 'DEVICE_CLOCK_UNAVAILABLE' }
    if ($line -ceq 'ERROR ack_deadline') { Stop-Channel 'ACK_TIMEOUT' }
    if ($line -ceq 'ERROR file_io_permission') { Stop-Channel 'FILE_IO_PERMISSION' }
    if ($line -cmatch '^ERROR (channel_read_error|metadata_io|metadata_empty)$') { Stop-Channel 'CHANNEL_READ_ERROR' }
    if ($line.StartsWith('ERROR')) { Stop-Channel 'CHANNEL_PROTOCOL_ERROR' }
    return $line
}
function Snapshot {
    $script:fileIoChildInvocations++
    if ($ioPackage) { $script:runAsInvocations++ }
    $script:channelSynchronized = $false
    $process.StandardInput.WriteLine('POLL'); $process.StandardInput.Flush()
    $result = @{}
    foreach ($prefix in @('IDENTITY', 'NOW', 'CHALLENGE', 'REQUEST', 'OBSERVATION', 'RESULT')) {
        $line = Read-ChannelLine
        if (-not $line.StartsWith($prefix + ' ')) { throw 'FAIL-STOP: sai framing kênh.' }
        $result[$prefix] = $line.Substring($prefix.Length + 1)
    }
    if ((Read-ChannelLine) -cne 'END') { throw 'FAIL-STOP: thiếu framing kết thúc.' }
    $script:channelSynchronized = $true
    if ($Benchmark -and $null -ne $BenchmarkChild -and
        (Get-TBS5530BenchmarkChildState $BenchmarkChild) -cne 'RUNNING' -and -not $result.RESULT) {
        Stop-Channel 'BENCHMARK_CHILD_FAILED'
    }
    return $result
}
function Export-BenchmarkEvidence([string]$Name) {
    if (-not $Benchmark -or $Name -cnotin @('benchmark-observations.jsonl', 'benchmark-audit.jsonl', 'benchmark-result.json')) {
        throw 'FAIL-STOP: chỉ export evidence benchmark đã định danh.'
    }
    Assert-Time
    $script:fileIoChildInvocations++
    if ($ioPackage) { $script:runAsInvocations++ }
    $script:channelSynchronized = $false
    $process.StandardInput.WriteLine('EXPORT ' + $Name); $process.StandardInput.Flush()
    if ((Read-ChannelLine) -cne ('EVIDENCE_BEGIN ' + $Name)) { Stop-Channel 'CHANNEL_PROTOCOL_ERROR' }
    $count = 0
    while ($true) {
        $line = Read-ChannelLine
        if ($line -cmatch '^EVIDENCE_END ([0-9]+)$') {
            if ([int]$Matches[1] -ne $count) { Stop-Channel 'INCOMPLETE_ANDROID_EVIDENCE' }
            break
        }
        if (-not $line.StartsWith('EVIDENCE ')) { Stop-Channel 'CHANNEL_PROTOCOL_ERROR' }
        try { $entry = $line.Substring(9) | ConvertFrom-Json -ErrorAction Stop }
        catch {
            if (-not $failureEvidenceMode) { throw }
            $entry = @{ malformed_json = $true; raw_text = $line.Substring(9) }
        }
        $count++
        if (-not $failureEvidenceMode -and $Name -ceq 'benchmark-observations.jsonl') {
            if ($entry.run_epoch -cne $challenge.run_epoch -or $entry.ordinal -ne $count -or $count -gt 132 -or
                [long]$entry.ack_deadline_elapsed_ms - [long]$entry.request_elapsed_ms -ne 1500 -or
                [long]$entry.host_seen_elapsed_ms -lt [long]$entry.request_elapsed_ms -or
                [long]$entry.host_ack_elapsed_ms -lt [long]$entry.host_seen_elapsed_ms -or
                [long]$entry.ack_observed_elapsed_ms -lt [long]$entry.host_ack_elapsed_ms -or
                [long]$entry.ack_observed_elapsed_ms -ge [long]$entry.ack_deadline_elapsed_ms) {
                Stop-Channel 'INVALID_ANDROID_EVIDENCE'
            }
        }
        Write-Ledger @{ event = $(if ($failureEvidenceMode) { 'benchmark_failure_evidence' } else { 'benchmark_evidence' })
            run = $run; file = $Name; record = $count; evidence = $entry }
    }
    $script:channelSynchronized = $true
    return $count
}
function Save-BenchmarkFailureEvidence {
    if (-not $Benchmark) { return }
    if ($null -ne (Get-Variable snapshot -ErrorAction SilentlyContinue) -and $snapshot.RESULT) {
        Write-Ledger @{ event = 'benchmark_failure_snapshot'; run = $run; file = 'benchmark-result.json'
            evidence = ($snapshot.RESULT | ConvertFrom-Json -ErrorAction Stop) }
    }
    $status = 'SKIPPED_NO_SYNCHRONIZED_CHANNEL'
    $files = @{}
    if ($null -ne $process -and -not $process.HasExited -and $channelSynchronized) {
        # Read-only diagnostics on the SAME channel; this is not extra authorization time.
        $script:failureEvidenceMode = $true
        $script:diagnosticDeadline = [Diagnostics.Stopwatch]::GetTimestamp() + 5L * [Diagnostics.Stopwatch]::Frequency
        $status = 'EXPORTED'
        foreach ($name in @('benchmark-observations.jsonl', 'benchmark-audit.jsonl', 'benchmark-result.json')) {
            try { $files[$name] = @{ records = (Export-BenchmarkEvidence $name); status = 'READ' } }
            catch {
                $files[$name] = @{ status = 'FAILED'; error_type = $_.Exception.GetType().FullName }
                $status = 'PARTIAL_OR_UNAVAILABLE'
                break
            }
        }
    }
    Write-Ledger @{ event = 'benchmark_failure_export'; run = $run; benchmark_directory = $BenchmarkDirectory
        status = $status; files = $files; diagnostic_bound_ms = 5000; creates_permit = $false; reconnect = $false }
}
function Test-PriorApprovalArtifact($Value, $Challenge, [bool]$AllowPriorRun, [string]$Kind) {
    $failure = 'INVALID_' + $Kind + '_BINDING'
    if ($null -eq $Value -or $Value -isnot [pscustomobject]) { throw ($failure + ': metadata phải là JSON object.') }
    $epoch = $Value.PSObject.Properties['run_epoch']
    $nonce = $Value.PSObject.Properties['challenge']
    $guidPattern = '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$'
    if ($null -ne $nonce) {
        if ($nonce.Value -isnot [string] -or $nonce.Value -cnotmatch $guidPattern) {
            throw ($failure + ': challenge không hợp lệ; không suy là prior run.')
        }
        # Classify a proven older challenge before touching protocol or other required fields.
        $prior = $nonce.Value -cne $Challenge.challenge
        if (-not $prior -and ($null -eq $epoch -or $epoch.Value -isnot [string] -or
            $epoch.Value -cnotmatch $guidPattern -or $epoch.Value -cne $Challenge.run_epoch)) {
            throw ($failure + ': current challenge thiếu hoặc sai run_epoch.')
        }
    } elseif ($Kind -ceq 'OBSERVATION' -and $null -ne $epoch -and
        $epoch.Value -is [string] -and $epoch.Value -cmatch $guidPattern) {
        # Core observation JSON intentionally binds only run_epoch, not the full challenge.
        $prior = $epoch.Value -cne $Challenge.run_epoch
    } else { throw ($failure + ': thiếu challenge hoặc không chứng minh được current/prior run.') }
    if ($prior -and -not $AllowPriorRun) { throw ($failure + ': prior artifact xuất hiện sau ACK đầu tiên; không resume.') }
    return $prior
}
function Same-Binding($Value, $Challenge, [string]$FailureKind = 'INVALID_REQUEST_BINDING') {
    foreach ($field in @('approval_protocol', 'challenge', 'run_epoch', 'install', 'upload_epoch', 'attachment_hash', 'plan', 'sha256')) {
        $actual = $(if ($null -ne $Value) { $Value.PSObject.Properties[$field] } else { $null })
        $expected = $(if ($null -ne $Challenge) { $Challenge.PSObject.Properties[$field] } else { $null })
        if ($null -eq $actual -or $null -eq $expected -or $null -eq $actual.Value -or $null -eq $expected.Value -or
            [string]$actual.Value -cne [string]$expected.Value) { throw ($FailureKind + ': thiếu hoặc đổi binding field ' + $field + '.') }
    }
}
function Select-CurrentProductionArtifacts($Snapshot, $Challenge, [int]$Acknowledged, [int]$Observed, [bool]$BenchmarkOnly = $false) {
    $ignored = @{ REQUEST = $false; OBSERVATION = $false }
    foreach ($kind in @('REQUEST', 'OBSERVATION')) {
        if ($Snapshot[$kind]) {
            try { $value = $Snapshot[$kind] | ConvertFrom-Json -ErrorAction Stop }
            catch { throw ('INVALID_' + $kind + '_BINDING: metadata không phải JSON hợp lệ.') }
            if (Test-PriorApprovalArtifact $value $Challenge ($Acknowledged -eq 0 -and $Observed -eq 0) $kind) {
                $ignored[$kind] = $true
                $Snapshot[$kind] = '' # In-memory only; return normally to the common heartbeat path.
            } else {
                if ($kind -ceq 'REQUEST') {
                    Same-Binding $value $Challenge 'INVALID_REQUEST_BINDING'
                    $required = @('ordinal', 'request_nonce', 'request_elapsed_ms', 'ack_deadline_elapsed_ms')
                    if (-not $BenchmarkOnly) { $required += 'new_approval_id' }
                } else { $required = @('run_epoch', 'ordinal', 'request_elapsed_ms', 'ack_observed_elapsed_ms') }
                foreach ($field in $required) {
                    if ($null -eq $value.PSObject.Properties[$field] -or $null -eq $value.PSObject.Properties[$field].Value) {
                        throw ('INVALID_' + $kind + '_BINDING: thiếu field ' + $field + '.')
                    }
                }
            }
        }
    }
    return $ignored
}
try {
    $startEntry = @{ event = 'benchmark_start'; run = $run; utc = [DateTime]::UtcNow.ToString('o'); hardware_enabled = $false }
    if ($Benchmark) { $startEntry.benchmark_directory = $BenchmarkDirectory }
    Write-Ledger $startEntry
    Assert-Time
    $target = Get-TBS5530ApprovalTarget -AdbPath $AdbPath -Serial $Serial -DeadlineTimestamp $DeadlineTimestamp
    $subprocesses += $target.AdbSubprocesses
    if ($ExpectedIdentity -and $target.Identity -cne $ExpectedIdentity) { throw 'FAIL-STOP: target/boot identity đã đổi.' }
    $pinnedIdentity = $target.Identity
    Assert-Time
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $AdbPath
    $start.Arguments = '-s ' + $Serial + ' shell -T sh' # Clock/boot reads stay under shell UID.
    $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    if (-not $process.Start()) { throw 'FAIL-STOP: không tạo được kênh.' }; $subprocesses++
    $process.StandardInput.NewLine = "`n"
    $stderr = $process.StandardError.ReadToEndAsync()
    # /proc/uptime has 10 ms resolution. Record raw samples only; a request ahead
    # of the sampled tick waits at most two ticks, never changes the timestamp or deadline.
    $ioShell = @'
data_directory=$1; command=$2; result_file=$3; wait_directory=$4; missing_path() { probe=$(LC_ALL=C ls -d -- "$1" 2>&1); code=$?; [ "$code" -ne 0 ] || return 1; case "$probe" in *'No such file or directory'*) return 0;; *'Permission denied'*) printf 'ERROR file_io_permission\n'; exit 1;; *) printf 'ERROR channel_read_error\n'; exit 1;; esac; }; if ! cd "$data_directory" 2>/dev/null; then if missing_path "$data_directory" && [ "$command" = POLL ] && [ "$wait_directory" = yes ]; then printf 'CHALLENGE \nREQUEST \nOBSERVATION \nRESULT \n'; exit 0; fi; printf 'ERROR channel_read_error\n'; exit 1; fi; cr=$(printf '\r'); emit() { value=; size=0; if [ -f "$2" ]; then [ -r "$2" ] || { printf 'ERROR file_io_permission\n'; exit 1; }; while IFS= read -r line || [ -n "$line" ]; do line=${line%"$cr"}; size=$((size + ${#line} + 1)); [ "$size" -le 262144 ] || { printf 'ERROR metadata_size\n'; exit 1; }; value="$value $line"; done < "$2" || { printf 'ERROR metadata_io\n'; exit 1; }; [ "$size" -gt 0 ] || { printf 'ERROR channel_read_error\n'; exit 1; }; else missing_path "$2" || { printf 'ERROR channel_read_error\n'; exit 1; }; fi; printf '%s %s\n' "$1" "${value# }"; }; case "$command" in POLL) emit CHALLENGE phase29-challenge.json; emit REQUEST phase29-write-request.json; emit OBSERVATION phase29-write-observation.json; emit RESULT "$result_file";; PUT) payload=; IFS= read -r payload || [ -n "$payload" ] || exit 1; printf '%s' "$payload" > phase29-session-approval.new && mv phase29-session-approval.new phase29-session-approval.json || exit 1;; EXPORT) file=$5; case "$file" in benchmark-observations.jsonl|benchmark-audit.jsonl|benchmark-result.json) :;; *) printf 'ERROR protocol\n'; exit 1;; esac; if [ -f "$file" ]; then [ -r "$file" ] || { printf 'ERROR file_io_permission\n'; exit 1; }; else missing_path "$file" || { printf 'ERROR channel_read_error\n'; exit 1; }; fi; printf 'EVIDENCE_BEGIN %s\n' "$file"; size=0; count=0; if [ "$file" = benchmark-result.json ]; then if [ -f "$file" ]; then emit EVIDENCE "$file"; count=1; fi; elif [ -f "$file" ]; then while IFS= read -r line || [ -n "$line" ]; do line=${line%"$cr"}; size=$((size + ${#line} + 1)); count=$((count + 1)); [ "${#line}" -le 262144 ] && [ "$size" -le 8388608 ] && [ "$count" -le 4096 ] || { printf 'ERROR metadata_size\n'; exit 1; }; printf 'EVIDENCE %s\n' "$line"; done < "$file" || { printf 'ERROR metadata_io\n'; exit 1; }; fi; printf 'EVIDENCE_END %s\n' "$count";; *) printf 'ERROR protocol\n'; exit 1;; esac
'@
    $shell = @'
cd '__DIRECTORY__' || exit 1; LC_ALL=C; export LC_ALL; io_package='__IO_PACKAGE__'; io_script='__IO_SCRIPT__'; io() { if [ -n "$io_package" ]; then run-as "$io_package" sh -c "$io_script" sh '__DATA_DIRECTORY__' "$1" '__RESULT_FILE__' '__WAIT_FOR_DIRECTORY__' "${2-}"; else sh -c "$io_script" sh '__DATA_DIRECTORY__' "$1" '__RESULT_FILE__' '__WAIT_FOR_DIRECTORY__' "${2-}"; fi; }; identity() { maker=$(getprop ro.product.manufacturer); model=$(getprop ro.product.model); api=$(getprop ro.build.version.sdk); read -r boot < /proc/sys/kernel/random/boot_id; identity_value="$maker|$model|$api|$boot"; [ "$identity_value" = '__PINNED_IDENTITY__' ] || { printf 'ERROR identity\n'; exit 1; }; initial_boot=$boot; }; check_identity() { read -r boot < /proc/sys/kernel/random/boot_id || { printf 'ERROR identity\n'; exit 1; }; [ "$boot" = "$initial_boot" ] || { printf 'ERROR identity\n'; exit 1; }; }; read_clock() { up=; rest=; read -r up rest < /proc/uptime || { printf 'ERROR clock_read\n'; exit 1; }; case "$up" in ''|*[!0-9.]*|*.*.*|.*|*.) printf 'ERROR clock_format\n'; exit 1;; esac; sec=${up%.*}; frac=${up#*.}; [ "$up" != "$sec" ] && [ "${#frac}" -eq 2 ] || { printf 'ERROR clock_format\n'; exit 1; }; frac=$((1$frac - 100)); stamp=$((sec * 1000 + frac * 10)); [ "$stamp" -ge 0 ] && [ "$((stamp / 1000))" -eq "$sec" ] || { printf 'ERROR clock_range\n'; exit 1; }; }; now_ms() { clock_minimum=${1:-0}; clock_until=${2:-0}; clock_waits=0; while :; do read_clock; if [ "$clock_until" -gt 0 ] && [ "$stamp" -ge "$clock_until" ]; then printf 'ERROR ack_deadline\n'; exit 1; fi; [ "$stamp" -lt "$clock_minimum" ] || return 0; [ "$((clock_minimum - stamp))" -lt 10 ] && [ "$clock_waits" -lt 2 ] || { printf 'ERROR clock_alignment\n'; exit 1; }; sleep 0.01 || { printf 'ERROR clock_read\n'; exit 1; }; clock_waits=$((clock_waits + 1)); done; }; write_payload() { while :; do case "$payload" in *'"@DEVICE_ACK@"'*) head=${payload%%'"@DEVICE_ACK@"'*}; payload=${payload#*'"@DEVICE_ACK@"'}; printf '%s%s' "$head" "$stamp";; *) printf '%s' "$payload"; break;; esac; done; }; identity; while IFS= read -r command; do case "$command" in POLL) check_identity; snapshot=$(io POLL); status=$?; if [ "$status" -ne 0 ]; then case "$snapshot" in *'ERROR file_io_permission'*) printf 'ERROR file_io_permission\n';; *'ERROR metadata_size'*) printf 'ERROR metadata_size\n';; *) printf 'ERROR channel_read_error\n';; esac; exit 1; fi; now_ms; printf 'IDENTITY %s\n' "$identity_value"; printf 'NOW %s\n' "$stamp"; printf '%s\n' "$snapshot"; printf 'END\n';; PUT\ *) check_identity; minimum=${command#PUT }; measured=no; case "$minimum" in *' MEASURE_PERSISTED') minimum=${minimum%' MEASURE_PERSISTED'}; measured=yes;; esac; valid_until=0; case "$minimum" in *' UNTIL '*) valid_until=${minimum#*' UNTIL '}; minimum=${minimum%%' UNTIL '*};; esac; case "$minimum" in ''|*[!0-9]*) exit 1;; esac; case "$valid_until" in ''|*[!0-9]*) exit 1;; esac; IFS= read -r payload || exit 1; now_ms "$minimum" "$valid_until"; ack_stamp=$stamp; write_payload | io PUT || { printf 'ERROR metadata_io\n'; exit 1; }; if [ "$measured" = yes ]; then now_ms; printf 'STORED %s HOST_ACK_PERSISTED %s\n' "$ack_stamp" "$stamp"; else printf 'STORED %s\n' "$ack_stamp"; fi;; EXPORT\ *) check_identity; file=${command#EXPORT }; case "$file" in benchmark-observations.jsonl|benchmark-audit.jsonl|benchmark-result.json) :;; *) printf 'ERROR protocol\n'; exit 1;; esac; io EXPORT "$file" || { printf 'ERROR metadata_io\n'; exit 1; };; QUIT) exit 0;; *) printf 'ERROR protocol\n'; exit 1;; esac; done
'@
    $remoteDirectory = $(if ($Benchmark) { $BenchmarkDirectory } else { 'files' })
    $ioPackage = $(if (-not $Benchmark) { 'info.martinmarinov.dvbdriver' }
        elseif ($FullPathInstrumentation) { 'info.martinmarinov.dvbdriver.phase29offline' } else { '' })
    $resultFile = $(if ($Benchmark) { 'benchmark-result.json' } else { 'tbs5530-diagnostic.json' })
    $controller = $shell.Replace('__IO_SCRIPT__', $ioShell.Replace("'", "'\''")).Replace('__DIRECTORY__', '.').Replace('__DATA_DIRECTORY__', $remoteDirectory)
    $controller = $controller.Replace('__IO_PACKAGE__', $ioPackage).Replace('__WAIT_FOR_DIRECTORY__', $(if ($Benchmark) { 'yes' } else { 'no' })).Replace('__RESULT_FILE__', $resultFile).Replace('__PINNED_IDENTITY__', $pinnedIdentity)
    $process.StandardInput.WriteLine($controller); $process.StandardInput.Flush()
    $snapshot = Snapshot
    while ($Benchmark -and -not $snapshot.CHALLENGE) {
        if ($snapshot.RESULT) { Stop-Channel 'ANDROID_GUARD_STOP' }
        Start-Sleep -Milliseconds 10
        $snapshot = Snapshot
    }
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
    if (-not $Benchmark -and ([long]$snapshot.NOW + 9 -lt [long]$challenge.created_elapsed_ms)) {
        Stop-Channel 'DEVICE_CLOCK_UNAVAILABLE'
    }
    $initialArtifacts = Select-CurrentProductionArtifacts $snapshot $challenge 0 0 ([bool]$Benchmark)
    $waitingForChallenge = $false
    $deadline = [long]$challenge.created_elapsed_ms + 300000
    $permit = [ordered]@{}
    foreach ($field in @('approval_protocol', 'challenge', 'run_epoch', 'install', 'upload_epoch', 'attachment_hash', 'plan', 'sha256')) {
        $permit[$field] = $challenge.$field
    }
    $permit.explicit_session_upload_approval = $true; $permit.physical_host_and_otg_confirmed = $true
    $permit.host_ledger_committed = $true; $permit.expires_elapsed_ms = $deadline
    $permit.host_clock = 'device_uptime_10ms_lower_bound_raw_wait_for_tick'
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
            legacy_consumed_acknowledged = $true; explicit_new_attempt_approval = $true
            initial_prior_request_ignored = $initialArtifacts.REQUEST; initial_prior_observation_ignored = $initialArtifacts.OBSERVATION
            utc = [DateTime]::UtcNow.ToString('o') }
        $permit.new_approval_id = $NewApprovalId; $permit.explicit_new_attempt_approval = $true
        $permit.legacy_consumed_acknowledged = $true; $permit.legacy_consumed = [bool]$challenge.legacy_consumed
        $permit.host_ledger_event = 'NEW_APPROVAL_CONSUMED'
    }
    $permit.authorized_ordinal = 0
    $lastNonce = ''
    $lastHeartbeat = $null
    $baselineRevision = ''
    if (-not $Benchmark -and $snapshot.RESULT) {
        $initialReport = $snapshot.RESULT | ConvertFrom-Json
        $baselineRevision = Get-TBS5530ProductionReportRevision $initialReport
    }
    while ($true) {
        Assert-Time
        if ($snapshot.IDENTITY -cne $pinnedIdentity) { throw 'FAIL-STOP: channel identity đã đổi; không cấp heartbeat/ACK.' }
        Same-Binding ($snapshot.CHALLENGE | ConvertFrom-Json) $challenge 'INVALID_CHALLENGE_BINDING'
        # A complete failed attempt can finish between polls, before ordinal 1 or any pending=true export.
        # Inspect its epoch/revision/audit BEFORE stale requests, expiry checks or another PUT.
        if (-not $Benchmark -and $snapshot.RESULT) {
            $report = $snapshot.RESULT | ConvertFrom-Json
            if ((Get-TBS5530ProductionReportDisposition -Report $report -RunEpoch $challenge.run_epoch -BaselineRevision $baselineRevision) -ceq 'TERMINAL') {
                $audit = Get-TBS5530ProductionTerminalAudit -Report $report -ApprovalId $NewApprovalId -RunEpoch $challenge.run_epoch -HostRun $run
                if ($audit.native_call_started -gt $acknowledged) { Stop-Channel 'UNAUTHORIZED_NATIVE_BOUNDARY' }
                Write-Ledger $audit
                Write-Output ('Đã dừng cấp ACK tại terminal state=' + $report.state + '; kind=' + $audit.terminal_kind + '; không retry/corrective USB.')
                return
            }
        }
        if ($Benchmark -and $snapshot.RESULT) { $snapshot.REQUEST = '' } # Terminal benchmark never authorizes another request.
        $null = Select-CurrentProductionArtifacts $snapshot $challenge $acknowledged $observed ([bool]$Benchmark)
        $now = [long]$snapshot.NOW
        if ($now -ge $deadline) { throw 'FAIL-STOP: phiên đã hết hạn.' }
        $minimum = [long]$challenge.created_elapsed_ms
        $validUntil = $deadline
        if ($snapshot.REQUEST -and -not ($Benchmark -and $snapshot.RESULT)) {
            $request = $snapshot.REQUEST | ConvertFrom-Json
            Same-Binding $request $challenge
            if (-not $Benchmark -and ($null -eq $request.PSObject.Properties['new_approval_id'] -or
                $request.new_approval_id -cne $NewApprovalId)) { throw 'INVALID_REQUEST_BINDING: request thiếu hoặc khác new approval ID.' }
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
                $permit.host_seen_elapsed_ms = $now
                $validUntil = [Math]::Min($deadline, [long]$request.ack_deadline_elapsed_ms)
                Write-Ledger @{ event = 'authorize_benchmark_ordinal'; run = $run; ordinal = $next; elapsed_ms = $now }
            }
            $minimum = [long]$request.request_elapsed_ms
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
                if ($result.roundtrips -ne 132 -or $observed -ne 132 -or $result.usb_calls -ne 0) { Stop-Channel 'INCOMPLETE_ANDROID_OBSERVATIONS' }
                if ($FullPathInstrumentation -and ($result.native_fake_calls -ne 132 -or $result.full_length -ne 132)) {
                    Stop-Channel 'INCOMPLETE_FAKE_NATIVE_BOUNDARIES'
                }
                Write-Ledger @{ event = 'benchmark_android_result'; run = $run; result = $result }
                if ((Export-BenchmarkEvidence 'benchmark-observations.jsonl') -ne 132) { Stop-Channel 'INCOMPLETE_ANDROID_EVIDENCE' }
                $auditRecords = Export-BenchmarkEvidence 'benchmark-audit.jsonl'
                break
            }
        }
        $permit.heartbeat_elapsed_ms = '@DEVICE_ACK@'
        if ($acknowledged -gt 0) { $permit.host_ack_elapsed_ms = '@DEVICE_ACK@' }
        $payload = $permit | ConvertTo-Json -Compress
        $channelSynchronized = $false
        $script:fileIoChildInvocations++
        if ($ioPackage) { $script:runAsInvocations++ }
        $put = 'PUT ' + $minimum + ' UNTIL ' + $validUntil + $(if ($Benchmark) { ' MEASURE_PERSISTED' } else { '' })
        $process.StandardInput.WriteLine($put); $process.StandardInput.WriteLine($payload); $process.StandardInput.Flush()
        $stored = ConvertFrom-TBS5530StoredFrame (Read-ChannelLine)
        $channelSynchronized = $true
        if ($stored.host_ack_elapsed_ms -lt $minimum -or
            ($null -ne $lastHeartbeat -and $stored.host_ack_elapsed_ms -lt $lastHeartbeat)) {
            Stop-Channel 'INVALID_DEVICE_HEARTBEAT'
        }
        $lastHeartbeat = $stored.host_ack_elapsed_ms
        $heartbeatWrites++
        if ($Benchmark) {
            # Keep the pre-PUT authorization audit unchanged. This separate record measures the
            # completed write/rename, not fsync durability, and never rewrites the permit.
            $stored.event = 'benchmark_host_ack_persisted'; $stored.run = $run; $stored.ordinal = $acknowledged
            if ($acknowledged -gt 0) { $stored.request_elapsed_ms = $permit.request_elapsed_ms }
            Write-Ledger $stored
        }
        Start-Sleep -Milliseconds 10
        $snapshot = Snapshot
    }
    $sorted = @($latencies | Sort-Object)
    $summary = @{ event = 'benchmark_pass'; run = $run; roundtrips = $observed; adb_subprocesses = $subprocesses
        p50_ms = $sorted[[int][Math]::Ceiling($observed * .50) - 1]; p95_ms = $sorted[[int][Math]::Ceiling($observed * .95) - 1]
        p99_ms = $sorted[[int][Math]::Ceiling($observed * .99) - 1]; max_ms = $sorted[-1]; hardware_enabled = $false
        full_path_instrumentation = [bool]$FullPathInstrumentation; audit_records = $auditRecords
        file_io_child_invocations = $fileIoChildInvocations; run_as_invocations = $runAsInvocations
        device_fork_count_scope = 'FILE_IO_DISPATCH_ATTEMPTS_NOT_TOTAL_OS_FORKS'; controller_uid = 'SHELL' }
    Write-Ledger $summary
    $summary | ConvertTo-Json -Compress
} catch {
    $originalError = $_
    $failureKind = 'HOST_HELPER_ERROR'
    if ($originalError.Exception.Message -match '^([A-Z][A-Z_]+):') { $failureKind = $Matches[1] }
    try {
        Write-Ledger @{ event = 'benchmark_fail'; run = $run; acknowledged = $acknowledged; observed = $observed
            heartbeat_writes = $heartbeatWrites; last_heartbeat_elapsed_ms = $lastHeartbeat
            file_io_child_invocations = $fileIoChildInvocations; run_as_invocations = $runAsInvocations; controller_uid = 'SHELL'
            failure_kind = $failureKind; elapsed_host_ms = $watch.ElapsedMilliseconds; total_bound_ms = $HardTimeoutSeconds * 1000
            total_deadline_expired = (Total-DeadlineExpired); utc = [DateTime]::UtcNow.ToString('o'); hardware_enabled = $false }
    } catch { $originalError.Exception.Data['TBS5530FailureLedgerError'] = $_.Exception.GetType().FullName }
    try { Save-BenchmarkFailureEvidence }
    catch { $originalError.Exception.Data['TBS5530FailureExportError'] = $_.Exception.GetType().FullName }
    throw
} finally {
    if ($null -ne $process) {
        try { $process.StandardInput.Close() } catch { }
        try { if (-not $process.HasExited -and -not $process.WaitForExit(1000)) { $process.Kill() } }
        catch { Write-Warning 'Không dừng được channel child; không reconnect.' }
        finally { $process.Dispose() }
    }
    $writer.Dispose(); $ledger.Dispose()
}
