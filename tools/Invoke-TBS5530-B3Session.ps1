# SPDX-License-Identifier: GPL-2.0-or-later
<#
.SYNOPSIS
Một lượt B3 theo card đã khóa; không tự thử lại hoặc thao tác hộp thoại USB.
.DESCRIPTION
Card JSON TBS5530-B3-CARD/v1 khóa session_id, max_attempts (1..3), model, api,
boot_count, source_git_sha, session_directory, app_apk_sha256, autonomous_helper_sha256,
b3_helper_sha256, firmware_sha256, reload_plan_sha256, golden_sha256 và attempts.
Mỗi phần tử attempts khóa ordinal, physical_recovery_epoch và allowed_receive_configs
(1..3 phần tử queue_depth/urb_bytes); nếu không có allowlist, dùng cặp legacy cố định
queue_depth/urb_bytes. Có allowlist thì nó là thẩm quyền, không union với cặp legacy.
Card còn khóa approval='OK B3', max_bytes=4194304, duration_ms=2000, ttl_ms=300000,
heartbeat_ms=2000, permission_wait_ms=30000. Các epoch phải khác nhau và do người
vận hành cấp sau xác nhận vật lý, không do helper tạo. Lưu card/manifest ngoài Git.
ApproveB3SessionBudget xác nhận ngân sách toàn card; mỗi invocation vẫn cần
PhysicalRecoveryConfirmed, PhysicalColdSeconds >=5, RfInConfirmed và ordinal mới.
Chỉ đọc evidence sau typed terminal/drain; bất định, -75 hoặc detach sau reload
đóng phiên. Capture chỉ có B3_CAPTURE_PENDING_OFFLINE_VALIDATION, không tự kết luận
PASS hoặc root cause. Lượt kế tiếp bắt buộc offline_validation.json của parent cho
lượt trước: decision CONTINUE_AFTER_PASS (mặc định) cần analyzerPass/tsduckPass/
throughputPass/pidServiceMatch đều boolean true, raw_sha256, analysis_manifest_sha256
và analysis_manifest_path đúng evidence đã khóa. Parent kiểm PASS 5.4: sau prefix
breaks=0, CC=0, PAT/PMT, tsanalyze TS/service, throughput Windows ±10%, PID/service match.
RETRY_STARVATION cho counter >0 hoặc RETRY_PRE_IO cho pre_native_refusal=true cho
phép tiếp tục trong budget mà không count PASS; bốn flags vẫn phải boolean, có thể
false. RETRY_PRE_IO cho phép raw_sha256=null chỉ khi export không có received.ts.
Không decision nào vượt session_closed, -75, detach hoặc thiếu drain.
Verdict khóa schema TBS5530-B3-OFFLINE-VALIDATION/v1, card_sha256/session_id/ordinal/
physical_recovery_epoch. Hash verdict được append vào CONSUME kế tiếp và kiểm lại.
Exit 1 bao gồm capture pending, không đồng nghĩa thiếu evidence hoặc chất lượng FAIL.
Không có FinalizeOfflineValidation: parent ghi verdict offline riêng và quyết định
PASS 5.4/hai lượt độc lập; helper không chạy analyzer hoặc tạo verdict hộ parent.
Lỗi host trước khi có khả năng publish grant ghi PRE_GRANT_HOST_REFUSAL, drained=false,
pre_io_refusal=true và evidence host CreateNew; RETRY_PRE_IO có thể kiểm lượt sau mà
không giả drain. Đã bắt đầu publish grant thì thiếu drain vẫn đóng phiên. Không sửa
receipt cũ; chưa có PRE_GRANT_RECONCILIATION hoặc helper amendment để mở lại lịch sử.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [string]$ExpectedModel = 'SM-X926B',
    [int]$ExpectedApi = 36,
    [Parameter(Mandatory)][ValidateRange(0,2147483647)][int]$ExpectedBootCount,
    [ValidateRange(10,300)][int]$DurationSeconds = 300,
    [string]$FirmwarePath,
    [string]$PhysicalRecoveryEpoch,
    [switch]$PhysicalRecoveryConfirmed,
    [ValidateRange(5,3600)][int]$PhysicalColdSeconds = 5,
    [switch]$RfInConfirmed,
    [switch]$GrantBoundedSession,
    [switch]$ProofOfGuardPassed,
    [switch]$ApproveB3SessionBudget,
    [string]$B3CardPath,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedB3CardSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedB3HelperSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedHelperSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedAppApkSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedFrontendGoldenSha256,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedReloadPlanSha256,
    [string]$B3SessionDirectory,
    [ValidateRange(1,3)][int]$B3AttemptOrdinal = 1,
    [ValidateRange(8,32)][int]$B3QueueDepth = 16,
    [ValidateRange(4096,65536)][int]$B3UrbBytes = 16384,
    [string]$OutputDirectory,
    [string]$AdbPath = 'D:\Android\Sdk\platform-tools\adb.exe'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$package = 'info.martinmarinov.dvbdriver'
$firmwareSha = 'b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc'
$script:adbStage = 'B3_PREFLIGHT'
function Get-TBS5530AdbFailure {
    param(
        [ValidateSet('START_FAILED', 'TIMEOUT', 'NONZERO_EXIT')][string]$Kind,
        [ValidatePattern('^[A-Z][A-Z0-9_]{0,63}$')][string]$Stage,
        [ValidatePattern('^[A-Z][A-Z0-9_]{0,63}$')][string]$Operation,
        [ValidateSet(1500, 10000)][int]$TimeoutMillis = 1500
    )
    return "FAIL-STOP: ADB $Kind; stage=$Stage; operation=$Operation; timeout_ms=$TimeoutMillis; không reconnect/fallback."
}
function Test-TBS5530DrainedTerminal([object]$Report) {
    if ($null -eq $Report) { return $false }
    foreach ($field in @('cleanup', 'state', 'result')) { if ($null -eq $Report.PSObject.Properties[$field]) { return $false } }
    if ($Report.cleanup -cne 'DRAIN_CONFIRMED' -or $Report.result -isnot [string] -or [string]::IsNullOrWhiteSpace($Report.result)) { return $false }
    $typed = $null -ne $Report.PSObject.Properties['terminal'] -or $null -ne $Report.PSObject.Properties['cleanup_path']
    if ($typed) {
        if ($null -eq $Report.PSObject.Properties['terminal'] -or $Report.terminal -isnot [bool] -or $Report.terminal -cne $true -or
            $null -eq $Report.PSObject.Properties['cleanup_path'] -or $Report.cleanup_path -cnotin @('NO_RESOURCES_ACQUIRED','RUNTIME_DRAIN_CONFIRMED') -or
            $Report.state -cnotin @('WAIT_DEVICE','NEED_ANDROID_USB_PERMISSION','FX2_DEFAULT','FIRMWARE_BOOTSTRAP','WAIT_REENUMERATION',
                'OPERATIONAL','FRONTEND_INIT','LOCK_CONFIRMED','TS_CAPTURE','REPORT','ERROR_UNCERTAIN')) { return $false }
        if ($Report.cleanup_path -ceq 'NO_RESOURCES_ACQUIRED' -and
            ($null -eq $Report.PSObject.Properties['firmware'] -or $null -ne $Report.firmware -or
             $null -eq $Report.PSObject.Properties['native_urb'] -or $null -ne $Report.native_urb)) { return $false }
    } elseif ($Report.state -cnotin @('REPORT', 'ERROR_UNCERTAIN')) { return $false }
    if ($null -ne $Report.PSObject.Properties['native_urb'] -and $null -ne $Report.native_urb) {
        $urb = $Report.native_urb
        if ($null -eq $urb.PSObject.Properties['outstanding'] -or $null -eq $urb.PSObject.Properties['quarantined'] -or
            ($urb.outstanding -isnot [long] -and $urb.outstanding -isnot [int]) -or
            $urb.outstanding -ne 0 -or $urb.quarantined -isnot [bool] -or $urb.quarantined -cne $false) { return $false }
    }
    return $true
}
function ConvertTo-TBS5530WindowsArgument([string]$Value) {
    $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}
function Get-TBS5530Sha256Token([string]$Text) {
    if ($Text -cmatch '^([a-f0-9]{64})(?:\s|$)') { return $Matches[1] }
    throw 'Invalid Android SHA-256 response.'
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
function Test-TBS5530LaunchChallenge([object]$Value, [string]$Launch, [bool]$ControlOnly, [long]$Now) {
    if ($null -eq $Value -or $Launch -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$' -or
        $Launch -ceq '00000000-0000-0000-0000-000000000000') { return $false }
    foreach ($field in @('launch_token','control_plane_only','live_ready','created_elapsed_ms','policy')) {
        if ($null -eq $Value.PSObject.Properties[$field]) { return $false }
    }
    return $Value.policy -ceq 'TBS5530-AUTONOMOUS/v1' -and $Value.launch_token -ceq $Launch -and
        $Value.control_plane_only -is [bool] -and $Value.control_plane_only -eq $ControlOnly -and
        $Value.live_ready -is [bool] -and $Value.live_ready -eq (-not $ControlOnly) -and
        ($Value.created_elapsed_ms -is [long] -or $Value.created_elapsed_ms -is [int]) -and
        $Now -ge $Value.created_elapsed_ms -and $Now - $Value.created_elapsed_ms -lt 10000
}
function New-TBS5530HeartbeatCommand([string]$Session, [long]$Created, [long]$Expires, [switch]$Stop, [switch]$PublishGrant) {
    if ($Stop -and $PublishGrant) { throw 'FAIL-STOP: không publish grant trong STOP.' }
    if ($Session -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$' -or
        $Created -lt 0 -or $Created -gt [long]::MaxValue - 300000 -or $Expires -le $Created -or $Expires -gt $Created + 300000) {
        throw 'FAIL-STOP: heartbeat scope/deadline không hợp lệ.'
    }
    # The adb shell UID reads the clock; run-as only publishes app-private files in the same call.
    $template = @'
set -eu
[ "$#" -eq 1 ] || exit 64
uptime=$1
seconds=${uptime%%.*}
fraction=${uptime#*.}
case $seconds in ""|*[!0-9]*|0[0-9]*) exit 64;; esac
case $fraction in ""|*[!0-9]*) exit 64;; esac
[ "$seconds.$fraction" = "$uptime" ] || exit 64
[ "${#seconds}" -le 10 ] || exit 64
fraction=${fraction}000
fraction=${fraction%"${fraction#???}"}
now=${seconds}${fraction}
while [ "${now#0}" != "$now" ]; do now=${now#0}; done
[ -n "$now" ] || now=0
# Compare decimal strings without relying on Android shell integer width.
ge() {
    a=$1 b=$2
    [ "${#a}" -ge "${#b}" ] || return 1
    [ "${#a}" -eq "${#b}" ] || return 0
    while [ -n "$a" ]; do
        ac=${a%"${a#?}"} bc=${b%"${b#?}"}
        [ "$ac" -ge "$bc" ] || return 1
        [ "$ac" -eq "$bc" ] || return 0
        a=${a#?} b=${b#?}
    done
    return 0
}
ge "$now" @CREATED@ || exit 65
expired=false
if ge "$now" @EXPIRES@; then expired=true; fi
if [ "@STOP@" = true ] || [ "$expired" = false ]; then
    if [ "@GRANT@" = true ]; then
        [ ! -e files/autonomous-v1-@SESSION@/grant.json ] || exit 66
        (set -C; cat > files/autonomous-v1-@SESSION@/grant.json.hosttmp) || exit 66
    fi
    printf "{\"session\":\"@SESSION@\",\"elapsed_ms\":%s,\"stop\":@STOP@}" "$now" > files/autonomous-v1-@SESSION@/heartbeat.json.hosttmp
    mv files/autonomous-v1-@SESSION@/heartbeat.json.hosttmp files/autonomous-v1-@SESSION@/heartbeat.json
    if [ "@GRANT@" = true ]; then mv files/autonomous-v1-@SESSION@/grant.json.hosttmp files/autonomous-v1-@SESSION@/grant.json; fi
fi
printf "{\"elapsed_ms\":%s,\"expired\":%s,\"report\":" "$now" "$expired"
if [ -f files/autonomous-v1-@SESSION@/report.json ]; then cat files/autonomous-v1-@SESSION@/report.json; else printf null; fi
printf ",\"permission\":"
if [ -f files/autonomous-v1-@SESSION@/permission.json ]; then cat files/autonomous-v1-@SESSION@/permission.json; else printf null; fi
printf ",\"control_plane\":"
if [ -f files/autonomous-v1-@SESSION@/control-plane.json ]; then cat files/autonomous-v1-@SESSION@/control-plane.json; else printf null; fi
printf "}"
'@
    $inner = $template.Replace('@SESSION@', $Session).Replace('@CREATED@', $Created.ToString([Globalization.CultureInfo]::InvariantCulture)).
        Replace('@EXPIRES@', $Expires.ToString([Globalization.CultureInfo]::InvariantCulture)).Replace('@STOP@', $Stop.IsPresent.ToString().ToLowerInvariant()).
        Replace('@GRANT@', $PublishGrant.IsPresent.ToString().ToLowerInvariant()).Replace("`r`n", "`n")
    $quote = [string][char]39
    $splice = $quote + [char]34 + $quote + [char]34 + $quote
    $quotedInner = $quote + $inner.Replace($quote, $splice) + $quote
    $outer = @'
set -eu
IFS=" " read -r uptime idle < /proc/uptime
exec run-as info.martinmarinov.dvbdriver sh -c @INNER@ heartbeat "$uptime"
'@
    return $outer.Replace('@INNER@', $quotedInner).Replace("`r`n", "`n")
}
function Read-TBS5530HeartbeatReply([string]$Text, [long]$RoundTripMillis, [long]$Created, [long]$Expires,
    [ValidatePattern('^[A-Z][A-Z0-9_]{0,63}$')][string]$Stage = 'HEARTBEAT') {
    if ($RoundTripMillis -lt 0 -or $RoundTripMillis -ge 1500) {
        throw "FAIL-STOP: heartbeat reply vượt call budget 1500ms; stage=$Stage; elapsed_ms=$RoundTripMillis."
    }
    try { $reply = $Text | ConvertFrom-Json } catch { throw 'FAIL-STOP: heartbeat reply JSON không hợp lệ.' }
    foreach ($field in @('elapsed_ms','expired','report','permission')) {
        if ($null -eq $reply -or $null -eq $reply.PSObject.Properties[$field]) { throw 'FAIL-STOP: heartbeat reply thiếu trường bắt buộc.' }
    }
    if (($reply.elapsed_ms -isnot [long] -and $reply.elapsed_ms -isnot [int]) -or $reply.elapsed_ms -lt $Created -or
        $reply.expired -isnot [bool] -or $reply.expired -ne ($reply.elapsed_ms -ge $Expires)) {
        throw 'FAIL-STOP: heartbeat reply clock/expiry không hợp lệ.'
    }
    return $reply
}
function Get-B3Hash([byte[]]$Bytes) {
    $digest = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($digest.ComputeHash($Bytes))).Replace('-','').ToLowerInvariant() }
    finally { $digest.Dispose() }
}
function Test-B3Integer([object]$Value) { return $Value -is [int] -or $Value -is [long] }
function Assert-B3Guid([object]$Value) {
    if ($Value -isnot [string] -or $Value -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$' -or
        $Value -ceq '00000000-0000-0000-0000-000000000000') { throw 'B3_GUID_UNVERIFIED' }
}
function Assert-B3Card([object]$Card, [hashtable]$Pins, [int]$Ordinal, [string]$Epoch, [int]$Depth, [int]$Chunk) {
    if ($null -eq $Card) { throw 'B3_CARD_MISSING' }
    Assert-B3Guid $Card.session_id
    foreach ($key in $Pins.Keys) {
        if ($null -eq $Card.PSObject.Properties[$key] -or $null -eq $Card.$key -or $Card.$key -cne $Pins[$key] -or
            ((Test-B3Integer $Pins[$key]) -and -not (Test-B3Integer $Card.$key)) -or
            ($Pins[$key] -is [string] -and $Card.$key -isnot [string])) {
            throw ('B3_CARD_PIN_MISMATCH:' + $key)
        }
    }
    if (-not (Test-B3Integer $Card.max_attempts) -or $Card.max_attempts -lt 1 -or $Card.max_attempts -gt 3 -or
        @($Card.attempts).Count -ne $Card.max_attempts -or $Ordinal -gt $Card.max_attempts) { throw 'B3_CARD_BUDGET_INVALID' }
    $epochs = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $i = 0
    foreach ($attempt in $Card.attempts) {
        $i++
        Assert-B3Guid $attempt.physical_recovery_epoch
        if (-not (Test-B3Integer $attempt.ordinal) -or $attempt.ordinal -ne $i -or -not $epochs.Add($attempt.physical_recovery_epoch)) {
            throw 'B3_CARD_ATTEMPT_INVALID'
        }
        $null = Get-B3AllowedReceiveConfigs $attempt
    }
    $selected = @($Card.attempts)[$Ordinal - 1]
    $listed = @((Get-B3AllowedReceiveConfigs $selected) | Where-Object { $_.queue_depth -eq $Depth -and $_.urb_bytes -eq $Chunk })
    if ($selected.physical_recovery_epoch -cne $Epoch -or $listed.Count -ne 1) {
        throw 'B3_CARD_ATTEMPT_BINDING_MISMATCH'
    }
}
function Get-B3AllowedReceiveConfigs([object]$Attempt) {
    $configs = if ($null -ne $Attempt.PSObject.Properties['allowed_receive_configs']) { @($Attempt.allowed_receive_configs) } else { @($Attempt) }
    if ($configs.Count -lt 1 -or $configs.Count -gt 3) { throw 'B3_CARD_RECEIVE_CONFIG_INVALID' }
    $keys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($config in $configs) {
        if ($null -eq $config -or -not (Test-B3Integer $config.queue_depth) -or $config.queue_depth -lt 8 -or $config.queue_depth -gt 32 -or
            -not (Test-B3Integer $config.urb_bytes) -or $config.urb_bytes -lt 4096 -or $config.urb_bytes -gt 65536 -or $config.urb_bytes % 512 -ne 0 -or
            -not $keys.Add(([string]$config.queue_depth+':'+$config.urb_bytes))) { throw 'B3_CARD_RECEIVE_CONFIG_INVALID' }
    }
    return $configs
}
function Read-B3Manifest([IO.FileStream]$Stream) {
    if ($Stream.Length -gt 65536) { throw 'B3_MANIFEST_OVERSIZED' }
    $Stream.Position = 0
    $bytes = [byte[]]::new([int]$Stream.Length)
    $at = 0
    while ($at -lt $bytes.Length) {
        $n = $Stream.Read($bytes, $at, $bytes.Length - $at)
        if ($n -le 0) { throw 'B3_MANIFEST_SHORT_READ' }; $at += $n
    }
    return ,$bytes
}
function Add-B3ManifestRecord([IO.FileStream]$Stream, [byte[]]$Prefix, [object]$Record) {
    if ((Get-B3Hash (Read-B3Manifest $Stream)) -cne (Get-B3Hash $Prefix)) { throw 'B3_MANIFEST_PREFIX_CHANGED' }
    $Record | Add-Member -NotePropertyName previous_sha256 -NotePropertyValue (Get-B3Hash $Prefix)
    $suffix = [Text.UTF8Encoding]::new($false, $true).GetBytes(($Record | ConvertTo-Json -Compress -Depth 8) + "`n")
    $Stream.Position = $Stream.Length
    $Stream.Write($suffix, 0, $suffix.Length); $Stream.Flush($true)
    $expected = [byte[]]($Prefix + $suffix)
    if ((Get-B3Hash (Read-B3Manifest $Stream)) -cne (Get-B3Hash $expected)) { throw 'B3_MANIFEST_READBACK_FAILED' }
    return ,$expected
}
function Test-B3PreGrantRefusal([object]$Receipt) {
    if ($null -eq $Receipt) { return $false }
    foreach ($key in @('result','classification','pre_io_refusal','grant_may_have_been_published','drained','session_closed')) {
        if ($null -eq $Receipt.PSObject.Properties[$key]) { return $false }
    }
    return $Receipt.result -ceq 'PRE_GRANT_HOST_REFUSAL' -and $Receipt.classification -ceq 'PRE_GRANT_HOST_REFUSAL' -and
        $Receipt.pre_io_refusal -is [bool] -and $Receipt.pre_io_refusal -and
        $Receipt.grant_may_have_been_published -is [bool] -and -not $Receipt.grant_may_have_been_published -and
        $Receipt.drained -is [bool] -and -not $Receipt.drained -and $Receipt.session_closed -is [bool] -and -not $Receipt.session_closed
}
function Assert-B3Budget([byte[]]$Bytes, [string]$CardHash, [object]$Card, [int]$Ordinal) {
    if ($Bytes.Length -eq 0) { if ($Ordinal -ne 1) { throw 'B3_MANIFEST_FIRST_ORDINAL_REQUIRED' }; return }
    $text = [Text.UTF8Encoding]::new($false,$true).GetString($Bytes)
    if (-not $text.EndsWith("`n") -or $text.Contains("`r")) { throw 'B3_MANIFEST_PARTIAL_OR_CORRUPT' }
    $prefix = [byte[]]@(); $expectedOrdinal = 1; $pending = $false; $previousReceipt = $null
    foreach ($line in $text.TrimEnd([char]10).Split([char]10)) {
        $row = $line | ConvertFrom-Json
        if ($row.previous_sha256 -cne (Get-B3Hash $prefix)) { throw 'B3_MANIFEST_CHAIN_INVALID' }
        if ($prefix.Length -eq 0) {
            if ($row.event -cne 'HEADER' -or $row.card_sha256 -cne $CardHash -or $row.session_id -cne $Card.session_id -or
                $row.max_attempts -ne $Card.max_attempts) { throw 'B3_MANIFEST_CARD_MISMATCH' }
        } elseif ($row.event -ceq 'CONSUME' -and -not $pending) {
            if ($row.ordinal -ne $expectedOrdinal -or $row.ordinal -gt $Card.max_attempts -or
                $row.epoch -cne @($Card.attempts)[$expectedOrdinal - 1].physical_recovery_epoch) { throw 'B3_MANIFEST_CONSUME_INVALID' }
            Assert-B3Card $Card @{} $expectedOrdinal $row.epoch $row.queue_depth $row.urb_bytes
            if ($expectedOrdinal -gt 1) {
                $review = Read-B3OfflineValidation $Card $CardHash $previousReceipt ($expectedOrdinal - 1)
                if ($row.previous_offline_validation_sha256 -cne $review.sha256) { throw 'B3_OFFLINE_VALIDATION_CHANGED_AFTER_CONSUME' }
            }
            $consumedDepth=$row.queue_depth; $consumedChunk=$row.urb_bytes
            $pending = $true
        } elseif ($row.event -ceq 'RECEIPT' -and $pending) {
            if ($row.ordinal -ne $expectedOrdinal -or $row.session_closed -isnot [bool] -or $row.session_closed -or
                $row.drained -isnot [bool] -or (-not $row.drained -and -not (Test-B3PreGrantRefusal $row)) -or
                [string]::IsNullOrWhiteSpace($row.result)) { throw 'B3_SESSION_CLOSED' }
            if ($row.queue_depth -ne $consumedDepth -or $row.urb_bytes -ne $consumedChunk) { throw 'B3_RECEIPT_RECEIVE_CONFIG_MISMATCH' }
            $previousReceipt = $row
            $pending = $false; $expectedOrdinal++
        } else { throw 'B3_MANIFEST_SEQUENCE_INVALID' }
        $prefix = [byte[]]($prefix + [Text.Encoding]::UTF8.GetBytes($line + "`n"))
    }
    if ($pending -or $Ordinal -ne $expectedOrdinal -or $Ordinal -gt $Card.max_attempts) { throw 'B3_CONSUMED_OR_BUDGET_EXHAUSTED' }
    if ($Ordinal -gt 1) { return Read-B3OfflineValidation $Card $CardHash $previousReceipt ($Ordinal - 1) }
}
function Read-B3OfflineValidation([object]$Card, [string]$CardHash, [object]$Receipt, [int]$Ordinal) {
    $preGrant = Test-B3PreGrantRefusal $Receipt
    if ($null -eq $Receipt -or $Receipt.ordinal -ne $Ordinal -or $Receipt.session_closed -or (-not $Receipt.drained -and -not $preGrant) -or
        $Receipt.capture_complete -isnot [bool] -or $Receipt.single_run_pass -isnot [bool] -or $Receipt.single_run_pass) {
        throw 'B3_PREVIOUS_CAPTURE_NOT_REVIEWABLE'
    }
    $attempt = Join-Path $Card.session_directory ('attempt-'+$Ordinal)
    $verdictPath = Join-Path $attempt 'offline_validation.json'
    if (-not (Test-Path -LiteralPath $verdictPath -PathType Leaf)) { throw 'B3_PREVIOUS_OFFLINE_VALIDATION_REQUIRED' }
    $bytes = [IO.File]::ReadAllBytes($verdictPath)
    if ($bytes.Length -gt 16384) { throw 'B3_OFFLINE_VALIDATION_OVERSIZED' }
    $verdict = [Text.UTF8Encoding]::new($false,$true).GetString($bytes).TrimStart([char]0xFEFF) | ConvertFrom-Json
    $decision = if ($null -ne $verdict.PSObject.Properties['decision']) { $verdict.decision } else { 'CONTINUE_AFTER_PASS' }
    if ($decision -cnotin @('CONTINUE_AFTER_PASS','RETRY_PRE_IO','RETRY_STARVATION')) { throw 'B3_OFFLINE_DECISION_INVALID' }
    foreach ($key in @('analyzerPass','tsduckPass','throughputPass','pidServiceMatch')) {
        if ($null -eq $verdict.PSObject.Properties[$key] -or $verdict.$key -isnot [bool] -or ($decision -ceq 'CONTINUE_AFTER_PASS' -and -not $verdict.$key)) {
            throw ('B3_OFFLINE_VALIDATION_NOT_PASS:'+ $key)
        }
    }
    if ($preGrant) {
        if ($decision -cne 'RETRY_PRE_IO') { throw 'B3_PRE_GRANT_REQUIRES_PRE_IO_REVIEW' }
        $evidencePath = Join-Path $attempt 'pre-grant-evidence-manifest.json'
        $evidenceBytes = [IO.File]::ReadAllBytes($evidencePath)
        if ($Receipt.pre_grant_evidence_sha256 -cnotmatch '^[a-f0-9]{64}$' -or
            (Get-B3Hash $evidenceBytes) -cne $Receipt.pre_grant_evidence_sha256) { throw 'B3_PRE_GRANT_EVIDENCE_CHANGED' }
        $evidence = [Text.UTF8Encoding]::new($false,$true).GetString($evidenceBytes) | ConvertFrom-Json
        if ($evidence.schema -cne 'TBS5530-B3-PRE-GRANT-EVIDENCE/v1' -or $evidence.card_sha256 -cne $CardHash -or
            $evidence.session_id -cne $Card.session_id -or $evidence.ordinal -ne $Ordinal -or $evidence.physical_recovery_epoch -cne $Card.attempts[$Ordinal-1].physical_recovery_epoch -or
            $evidence.error_file -cne 'host-refusal.json' -or
            (Get-FileHash -LiteralPath (Join-Path $attempt 'host-refusal.json') -Algorithm SHA256).Hash.ToLowerInvariant() -cne $evidence.error_sha256) {
            throw 'B3_PRE_GRANT_ERROR_EVIDENCE_CHANGED'
        }
    }
    if (($decision -ceq 'CONTINUE_AFTER_PASS' -and (-not $Receipt.capture_complete -or $Receipt.classification -cne 'B3_CAPTURE_PENDING_OFFLINE_VALIDATION')) -or
        ($decision -ceq 'RETRY_PRE_IO' -and ($null -eq $Receipt.PSObject.Properties['pre_io_refusal'] -or $Receipt.pre_io_refusal -isnot [bool] -or -not $Receipt.pre_io_refusal)) -or
        ($decision -ceq 'RETRY_STARVATION' -and ($null -eq $Receipt.PSObject.Properties['starvation_counter'] -or
            -not (Test-B3Integer $Receipt.starvation_counter) -or $Receipt.starvation_counter -le 0))) { throw 'B3_OFFLINE_DECISION_OBSERVATION_MISMATCH' }
    if ($verdict.schema -cne 'TBS5530-B3-OFFLINE-VALIDATION/v1' -or $verdict.card_sha256 -cne $CardHash -or
        $verdict.session_id -cne $Card.session_id -or -not (Test-B3Integer $verdict.ordinal) -or $verdict.ordinal -ne $Ordinal -or
        $verdict.physical_recovery_epoch -cne @($Card.attempts)[$Ordinal-1].physical_recovery_epoch) { throw 'B3_OFFLINE_VALIDATION_SCOPE_MISMATCH' }
    foreach ($value in @($Receipt.export_manifest_sha256,$verdict.analysis_manifest_sha256)) {
        if ($value -isnot [string] -or $value -cnotmatch '^[a-f0-9]{64}$') { throw 'B3_OFFLINE_EVIDENCE_HASH_REQUIRED' }
    }
    $exportPath = Join-Path $attempt 'export-manifest.json'
    $exportBytes = [IO.File]::ReadAllBytes($exportPath)
    if ((Get-B3Hash $exportBytes) -cne $Receipt.export_manifest_sha256) { throw 'B3_OFFLINE_EXPORT_MANIFEST_CHANGED' }
    $exports = [Text.UTF8Encoding]::new($false,$true).GetString($exportBytes) | ConvertFrom-Json
    $raw = @($exports | Where-Object { $_.path -clike '*/received.ts' -and $_.present })
    if ($decision -ceq 'RETRY_PRE_IO' -and $raw.Count -eq 0) {
        if ($null -ne $Receipt.raw_sha256 -or $null -eq $verdict.PSObject.Properties['raw_sha256'] -or $null -ne $verdict.raw_sha256) {
            throw 'B3_OFFLINE_PRE_IO_RAW_MUST_BE_NULL'
        }
    } else {
        if ($raw.Count -ne 1 -or $raw[0].file -cnotmatch '^[0-9]{2}-received\.ts$' -or
            $Receipt.raw_sha256 -cnotmatch '^[a-f0-9]{64}$' -or $raw[0].sha256 -cne $Receipt.raw_sha256 -or
            $verdict.raw_sha256 -cne $Receipt.raw_sha256) { throw 'B3_OFFLINE_RAW_BINDING_MISMATCH' }
        $rawPath = Join-Path $attempt $raw[0].file
        if ((Get-FileHash -LiteralPath $rawPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Receipt.raw_sha256 -or
            (Get-Item -LiteralPath $rawPath).Length -ne $raw[0].bytes) { throw 'B3_OFFLINE_RAW_CHANGED' }
    }
    $analysisPath = [IO.Path]::GetFullPath($verdict.analysis_manifest_path)
    if (-not ($analysisPath.StartsWith('D:\TSDuck\reports\',[StringComparison]::OrdinalIgnoreCase) -or
        $analysisPath.StartsWith('D:\TSDuck\logs\',[StringComparison]::OrdinalIgnoreCase)) -or
        (Get-FileHash -LiteralPath $analysisPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $verdict.analysis_manifest_sha256) {
        throw 'B3_OFFLINE_ANALYSIS_MANIFEST_CHANGED'
    }
    return [pscustomobject]@{sha256=(Get-B3Hash $bytes);raw_sha256=$Receipt.raw_sha256;analysis_manifest_sha256=$verdict.analysis_manifest_sha256;decision=$decision}
}
function Invoke-B3Adb([string[]]$DeviceArguments, [string]$InputText = '', [string]$BinaryDestination = '', [bool]$AfterDrain = $false) {
    $timeout = if ($AfterDrain) { 10000 } else { 1500 }
    if ($AfterDrain) { Assert-B3ExportCall $DeviceArguments $Report $Session $BinaryDestination }
    elseif ($BinaryDestination) { throw 'B3_BINARY_EXPORT_BEFORE_DRAIN_FORBIDDEN' }
    $info = [Diagnostics.ProcessStartInfo]::new($AdbPath)
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    $argsList = @('-s',$Serial) + $DeviceArguments
    if ($info.PSObject.Properties.Name -contains 'ArgumentList') { foreach ($arg in $argsList) { $info.ArgumentList.Add($arg) } }
    else { $info.Arguments = ($argsList | ForEach-Object { ConvertTo-TBS5530WindowsArgument $_ }) -join ' ' }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info; $file = $null
    try {
        if ($BinaryDestination) { $file = [IO.FileStream]::new($BinaryDestination,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None) }
        try { if (-not $process.Start()) { throw 'start' } } catch { throw (Get-TBS5530AdbFailure START_FAILED $script:adbStage B3_CALL $timeout) }
        $stderr = $process.StandardError.ReadToEndAsync()
        $stdout = if ($file) { $process.StandardOutput.BaseStream.CopyToAsync($file) } else { $process.StandardOutput.ReadToEndAsync() }
        if ($InputText) { $process.StandardInput.Write($InputText) }; $process.StandardInput.Close()
        if (-not $process.WaitForExit($timeout)) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            throw (Get-TBS5530AdbFailure TIMEOUT $script:adbStage B3_CALL $timeout)
        }
        $null = $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw (Get-TBS5530AdbFailure NONZERO_EXIT $script:adbStage B3_CALL $timeout) }
        if ($file) { $null = $stdout.GetAwaiter().GetResult(); $file.Flush($true); return }
        return $stdout.GetAwaiter().GetResult().TrimEnd("`r","`n")
    } finally { if ($file) { $file.Dispose() }; $process.Dispose() }
}
function Get-B3Elapsed {
    $text = Invoke-B3Adb @('shell','cat','/proc/uptime')
    if ($text -cnotmatch '^([0-9]+\.[0-9]+)\s+[0-9]+\.[0-9]+$') { throw 'B3_CLOCK_UNVERIFIED' }
    return [long][Math]::Floor([double]::Parse($Matches[1],[Globalization.CultureInfo]::InvariantCulture) * 1000)
}
function Assert-B3Identity {
    if ((Invoke-B3Adb @('get-state')) -cne 'device' -or
        (Invoke-B3Adb @('shell','getprop','ro.product.model')) -cne $ExpectedModel -or
        (Invoke-B3Adb @('shell','getprop','ro.build.version.sdk')) -cne '36' -or
        (Invoke-B3Adb @('shell','settings','get','global','boot_count')) -cne [string]$ExpectedBootCount) { throw 'B3_IDENTITY_MISMATCH' }
}
function Assert-B3Foreground {
    $activities = Invoke-B3Adb @('shell','dumpsys','activity','activities')
    $resumed = @($activities -split "`n" | Where-Object { $_ -cmatch '^\s*(?:mResumedActivity|ResumedActivity):\s*' })
    if ($resumed.Count -ne 1 -or $resumed[0] -cnotmatch '^\s*(?:mResumedActivity|ResumedActivity):\s+ActivityRecord\{\S+\s+u[0-9]+\s+info\.martinmarinov\.dvbdriver/\.Tbs5530AutonomousDiagnosticActivity(?:\s|\})') {
        throw 'B3_FOREGROUND_UNVERIFIED'
    }
}
function Assert-B3Apk {
    $text = Invoke-B3Adb @('shell','pm','path',$package)
    if ($text -cnotmatch '^package:(/[A-Za-z0-9_./=+~-]+\.apk)$') { throw 'B3_APK_PATH_UNVERIFIED' }
    $hash = Get-TBS5530Sha256Token (Invoke-B3Adb @('shell','sha256sum',$Matches[1]))
    if ($hash -cne $ExpectedAppApkSha256) { throw 'B3_INSTALLED_APK_MISMATCH' }
}
function Assert-B3Challenge([object]$Value, [string]$Launch, [long]$Now) {
    if (-not (Test-TBS5530LaunchChallenge $Value $Launch $false $Now)) { throw 'B3_CHALLENGE_STALE_OR_FOREIGN' }
    Assert-B3Guid $Value.session
    $pins = @{
        model=$ExpectedModel; api=$ExpectedApi; boot_count=$ExpectedBootCount; host_profile='samsung-sm-x926b-api36'
        vid=29516; pid=21808; firmware_sha256=$firmwareSha; firmware_protocol='TBS_SOURCE_DUAL_RESET'
        ts_variant='NATIVE_USBFS_STREAM'; frontend_experiment='B2_RELOAD_AFTER_SET_CONFIG'; golden_sha256=$ExpectedFrontendGoldenSha256
        app_apk_sha256=$ExpectedAppApkSha256; control_mirror=$true; explicit_set_interface_alt0=$false; sony_ts_output_readback=$false
        retry=0; max_upload_per_attachment=1; max_reload_per_physical_epoch=1; max_session_ms=300000; heartbeat_ms=2000
        fx2_reload_option='B2_RELOAD_AFTER_SET_CONFIG'; fx2_reload_plan_sha256=$ExpectedReloadPlanSha256
        bootstrap_proof='SAME_SESSION_ORIGINAL132_APP_BOUNDARIES_DRAIN_REQUIRED'
        b3_queue_depth=$B3QueueDepth; b3_urb_bytes=$B3UrbBytes; b3_max_bytes=4194304; b3_duration_ms=2000
    }
    foreach ($key in $pins.Keys) {
        if ($null -eq $Value.PSObject.Properties[$key] -or $Value.$key -cne $pins[$key] -or
            ($pins[$key] -is [bool] -and $Value.$key -isnot [bool]) -or
            ($pins[$key] -is [int] -and -not (Test-B3Integer $Value.$key))) { throw ('B3_CHALLENGE_PIN_MISMATCH:'+$key) }
    }
    $header = "TBS5530-FX2-RELOAD/v1`nHOST samsung-sm-x926b-api36`nINITIAL_HISTORY_SHA256 b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb`n"
    $headerHash = Get-B3Hash ([Text.Encoding]::UTF8.GetBytes($header))
    $anchorHash = Get-B3Hash ([Text.Encoding]::UTF8.GetBytes("TBS5530-FX2-RELOAD-ANCHOR/v1`n$headerHash`n"))
    if ($null -eq $Value.PSObject.Properties['attachment_history_sha256'] -or $null -ne $Value.attachment_history_sha256 -or
        $Value.fx2_reload_store_sha256 -cne $headerHash -or $Value.fx2_reload_anchor_sha256 -cne $anchorHash -or
        $Value.source_git_sha -cnotmatch '^[a-f0-9]{40}$' -or $Value.source_git_sha -cne $card.source_git_sha) { throw 'B3_SESSION_RAM_INITIAL_WITNESS_MISMATCH' }
}
function Test-B3Drained([object]$Report) {
    if (-not (Test-TBS5530DrainedTerminal $Report) -or $null -eq $Report.PSObject.Properties['terminal']) { return $false }
    if ($null -eq $Report.PSObject.Properties['native_stream']) {
        return $null -ne $Report.PSObject.Properties['ts_bytes'] -and (Test-B3Integer $Report.ts_bytes) -and $Report.ts_bytes -eq 0
    }
    if ($null -ne $Report.native_stream) {
        $stream = $Report.native_stream
        foreach ($key in @('outstanding','quarantined')) { if ($null -eq $stream.PSObject.Properties[$key]) { return $false } }
        if (-not (Test-B3Integer $stream.outstanding) -or $stream.outstanding -ne 0 -or
            $stream.quarantined -isnot [bool] -or $stream.quarantined -or $Report.cleanup_path -cne 'RUNTIME_DRAIN_CONFIRMED') { return $false }
    }
    return $true
}
function Test-B3SessionClosed([object]$Report) {
    if (-not (Test-B3Drained $Report)) { return $true }
    $nativeStream = if ($null -ne $Report.PSObject.Properties['native_stream']) { $Report.native_stream } else { $null }
    $nativeUrb = if ($null -ne $Report.PSObject.Properties['native_urb']) { $Report.native_urb } else { $null }
    foreach ($entry in @(@($Report,'last_errno',75),@($nativeStream,'first_status',-75),@($nativeStream,'first_errno',75),
        @($nativeUrb,'urb_status',-75),@($nativeUrb,'syscall_errno',75))) {
        if ($null -ne $entry[0] -and $null -ne $entry[0].PSObject.Properties[$entry[1]] -and $entry[0].($entry[1]) -eq $entry[2]) { return $true }
    }
    if ($null -ne $Report.PSObject.Properties['fx2_reload'] -and $null -ne $Report.fx2_reload -and $null -ne $Report.fx2_reload.PSObject.Properties['detach_ns'] -and
        (Test-B3Integer $Report.fx2_reload.detach_ns) -and $Report.fx2_reload.detach_ns -ge 0) { return $true }
    if ($Report.state -ceq 'ERROR_UNCERTAIN' -or $Report.result -cmatch 'UNCERTAIN|DETACH|AUDIT_UNAVAILABLE') { return $true }
    return $false
}
function Get-B3ExportPaths([string]$Session) {
    Assert-B3Guid $Session
    $base = 'files/autonomous-v1-' + $Session + '/'
    $names = @('report.json','received.ts','handoff-events.jsonl','control-ep0.jsonl','reload-a0-native.jsonl',
        'reload-native-boundaries.json','b3-reload-journal.txt','b3-reload-anchor.txt','ledger.jsonl','permission.json',
        'initial-descriptors.bin','receive-audit.jsonl','receive-audit.jsonl.started','receive-audit.jsonl.complete')
    $paths = @($names | ForEach-Object { $base + $_ }) + @(('files/phase31-native-stream-' + $Session + '.jsonl'),
        'files/autonomous-v1-samsung-sm-x926b-api36-attachments.txt','no_backup/fx2-reload-v1.txt','no_backup/fx2-reload-v1.anchor',
        'no_backup/fx2-reload-v1.initialized','no_backup/fx2-reload-v1-init.consumed.json')
    return $paths
}
function Assert-B3ExportCall([string[]]$Arguments, [object]$Report, [string]$Session, [string]$BinaryDestination) {
    if (-not (Test-B3Drained $Report)) { throw 'B3_EXPORT_REQUIRES_TYPED_DRAIN' }
    foreach ($remote in @(Get-B3ExportPaths $Session)) {
        $exists = 'if [ -f ' + $remote + ' ]; then printf PRESENT; fi'
        $expected = @(
            @('shell','run-as',$package,'sh','-c',("'"+$exists+"'")),
            @('shell','run-as',$package,'sha256sum',$remote),
            @('exec-out','run-as',$package,'cat',$remote)
        )
        foreach ($candidate in $expected) {
            if ($Arguments.Count -eq $candidate.Count -and ($Arguments -join "`n") -ceq ($candidate -join "`n")) {
                if (($Arguments[0] -ceq 'exec-out') -ne (-not [string]::IsNullOrEmpty($BinaryDestination))) { throw 'B3_EXPORT_BINARY_SINK_REQUIRED' }
                return
            }
        }
    }
    throw 'B3_EXPORT_OUTSIDE_EXACT_READ_ONLY_ALLOWLIST'
}
function Export-B3Evidence([string]$Session, [object]$Report, [string]$Destination) {
    Assert-B3Guid $Session
    if (-not (Test-B3Drained $Report)) { throw 'B3_EXPORT_REQUIRES_TYPED_DRAIN' }
    $base = 'files/autonomous-v1-' + $Session + '/'
    $paths = @(Get-B3ExportPaths $Session)
    $manifest = @(); $index = 0
    foreach ($remote in $paths) {
        $index++
        $command = 'if [ -f ' + $remote + ' ]; then printf PRESENT; fi'
        $present = Invoke-B3Adb @('shell','run-as',$package,'sh','-c',("'"+$command+"'")) -AfterDrain $true
        if ($present -ceq '') {
            $manifest += [pscustomobject]@{path=$remote; present=$false}
            if ($remote -ceq ($base+'report.json') -or ($null -ne $Report.PSObject.Properties['native_stream'] -and $null -ne $Report.native_stream -and
                $remote -cin @(($base+'received.ts'),($base+'handoff-events.jsonl'),($base+'control-ep0.jsonl'),
                    ($base+'reload-a0-native.jsonl'),($base+'reload-native-boundaries.json'),($base+'b3-reload-journal.txt'),
                    ($base+'b3-reload-anchor.txt'),('files/phase31-native-stream-'+$Session+'.jsonl')))) { throw 'B3_DRAINED_EVIDENCE_FILE_MISSING' }
            Write-Warning ('B3 evidence không có: '+$remote); continue
        }
        if ($present -cne 'PRESENT') { throw 'B3_EXPORT_PRESENCE_UNVERIFIED' }
        $name = [IO.Path]::GetFileName($remote)
        $local = Join-Path $Destination ($index.ToString('D2')+'-'+$name)
        $sourceHash = Get-TBS5530Sha256Token (Invoke-B3Adb @('shell','run-as',$package,'sha256sum',$remote) -AfterDrain $true)
        Invoke-B3Adb @('exec-out','run-as',$package,'cat',$remote) -BinaryDestination $local -AfterDrain $true
        $afterHash = Get-TBS5530Sha256Token (Invoke-B3Adb @('shell','run-as',$package,'sha256sum',$remote) -AfterDrain $true)
        $hostHash = (Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($sourceHash -cne $hostHash -or $afterHash -cne $hostHash) { throw 'B3_BINARY_EXPORT_HASH_MISMATCH' }
        $manifest += [pscustomobject]@{path=$remote; present=$true; file=[IO.Path]::GetFileName($local); bytes=(Get-Item -LiteralPath $local).Length; sha256=$hostHash}
    }
    Write-B3NewJson (Join-Path $Destination 'export-manifest.json') $manifest
    return ,$manifest
}
function Get-B3Classification([object]$Report, [byte[]]$PreviousManifest, [object[]]$Exports) {
    $capture = -not (Test-B3SessionClosed $Report)
    if ($capture) {
        $capture = $null -ne $Report.PSObject.Properties['native_stream'] -and $null -ne $Report.native_stream -and
            $Report.raw_capture -ceq 'SYNCED_CLOSED' -and $Report.control_mirror -ceq 'SYNCED_CLOSED' -and
            $Report.permission_outcome -cin @('AUTO_PERMISSION_HANDLED_BY_OS','AUTO_PERMISSION_UI_HANDLED') -and
            $Report.fx2_reload.state -ceq 'COMPLETE' -and $Report.fx2_reload.attempted -eq 132 -and $Report.fx2_reload.completed -eq 132 -and
            $Report.native_stream.first_status -eq 0 -and $Report.native_stream.first_errno -eq 0 -and
            (Test-B3Integer $Report.native_stream.bytes) -and $Report.native_stream.bytes -gt 0 -and $Report.native_stream.bytes -le 4194304
        $raw = @($Exports | Where-Object { $_.path -clike '*/received.ts' -and $_.present })
        $capture = $capture -and $raw.Count -eq 1 -and $raw[0].bytes -eq $Report.native_stream.bytes -and
            $Report.raw_received_bytes -eq $Report.native_stream.bytes
    }
    # App flags and old receipts cannot certify PASS 5.4. Parent reviews frozen offline evidence.
    return [pscustomobject]@{classification=$(if($capture){'B3_CAPTURE_PENDING_OFFLINE_VALIDATION'}else{'B3_NOT_PASS'});
        capture_complete=$capture;single_run_pass=$false;independent_passes=0;root_cause_confirmed=$false;final_ts_smoke_pass=$false;
        root_cause='ROOT_CAUSE_NOT_CONFIRMED';final_result='NOT_CONFIRMED';
        starvation_scope='SOFTWARE_QUEUE_OBSERVATION_NOT_HARDWARE_COMPLETION_PROOF'}
}
function Assert-B3PermissionSnapshot([object]$Value,[string]$Session,[long]$Now) {
    if ($null -eq $Value) { return }
    if ($Value.session -cne $Session -or $Value.vid -ne 29516 -or $Value.pid -ne 21808 -or
        $Value.state -cnotin @('UNVERIFIED','PENDING','CALLBACK_GRANTED','GRANTED','DENIED','INVALIDATED','CANCELLED')) {
        throw 'B3_PERMISSION_SCOPE_UNVERIFIED'
    }
    if ($Value.state -cin @('PENDING','CALLBACK_GRANTED') -and
        (-not (Test-B3Integer $Value.requested_elapsed_ms) -or $Now -lt $Value.requested_elapsed_ms -or
            $Now - $Value.requested_elapsed_ms -ge 30000)) { throw 'B3_PERMISSION_30S_WINDOW_EXPIRED' }
}
function Write-B3NewJson([string]$Path,[object]$Value) {
    $stream = [IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try { $bytes=[Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Value -Depth 20)+"`n"); $stream.Write($bytes,0,$bytes.Length); $stream.Flush($true) }
    finally { $stream.Dispose() }
}
function Write-B3PreGrantEvidence([string]$AttemptPath,[string]$CardHash,[string]$SessionId,[int]$Ordinal,
    [string]$Epoch,[string]$ErrorCategory,[string]$Stage) {
    # Host-only evidence is not an Android drain or export; no device reads on this path.
    Write-B3NewJson (Join-Path $AttemptPath 'host-refusal.json') ([pscustomobject]@{result='PRE_GRANT_HOST_REFUSAL';
        error_category=$ErrorCategory;stage=$Stage;grant_may_have_been_published=$false;drain='NOT_APPLICABLE_NO_GRANT';
        card_sha256=$CardHash;session_id=$SessionId;ordinal=$Ordinal;physical_recovery_epoch=$Epoch})
    $errorSha=(Get-FileHash -LiteralPath (Join-Path $AttemptPath 'host-refusal.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-B3NewJson (Join-Path $AttemptPath 'pre-grant-evidence-manifest.json') ([pscustomobject]@{
        schema='TBS5530-B3-PRE-GRANT-EVIDENCE/v1';card_sha256=$CardHash;session_id=$SessionId;ordinal=$Ordinal;
        physical_recovery_epoch=$Epoch;error_file='host-refusal.json';error_sha256=$errorSha})
    Write-B3NewJson (Join-Path $AttemptPath 'export-manifest.json') @()
    return [pscustomobject]@{evidence_sha256=(Get-FileHash -LiteralPath (Join-Path $AttemptPath 'pre-grant-evidence-manifest.json') -Algorithm SHA256).Hash.ToLowerInvariant();
        export_sha256=(Get-FileHash -LiteralPath (Join-Path $AttemptPath 'export-manifest.json') -Algorithm SHA256).Hash.ToLowerInvariant()}
}
function Exchange-B3Heartbeat([switch]$Stop,[object]$Grant=$null) {
    $command = New-TBS5530HeartbeatCommand $challenge.session ([long]$challenge.created_elapsed_ms) $expiry -Stop:$Stop -PublishGrant:($null -ne $Grant)
    $input = if ($null -eq $Grant) { '' } else { $Grant | ConvertTo-Json -Depth 12 -Compress }
    if ($input.Length -gt 16384) { throw 'B3_GRANT_OVERSIZED' }
    $call = [Diagnostics.Stopwatch]::StartNew()
    $text = Invoke-B3Adb @('shell',$command) $input
    return Read-TBS5530HeartbeatReply $text $call.ElapsedMilliseconds ([long]$challenge.created_elapsed_ms) $expiry -Stage $script:adbStage
}
# B3_MAIN: local guards precede any ADB or manifest mutation.
if (-not $GrantBoundedSession -or -not $ProofOfGuardPassed -or -not $ApproveB3SessionBudget -or
    -not $PhysicalRecoveryConfirmed -or -not $RfInConfirmed) { throw 'B3_EXPLICIT_SESSION_BUDGET_COLD_RF_PROOF_REQUIRED' }
Assert-B3Guid $PhysicalRecoveryEpoch
if ($ExpectedModel -cne 'SM-X926B' -or $ExpectedApi -ne 36 -or $DurationSeconds -ne 300 -or $B3UrbBytes % 512 -ne 0) { throw 'B3_SCOPE_INVALID' }
foreach ($pin in @($ExpectedB3CardSha256,$ExpectedB3HelperSha256,$ExpectedHelperSha256,$ExpectedAppApkSha256,$ExpectedFrontendGoldenSha256,$ExpectedReloadPlanSha256)) {
    if ($pin -cnotmatch '^[a-f0-9]{64}$') { throw 'B3_EXACT_HASHES_REQUIRED' }
}
$runtime = Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
if ((Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ExpectedB3HelperSha256 -or
    (Get-FileHash -LiteralPath $runtime -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ExpectedHelperSha256) { throw 'B3_HELPER_HASH_MISMATCH' }
if (-not (Test-Path -LiteralPath $FirmwarePath -PathType Leaf) -or (Get-Item -LiteralPath $FirmwarePath).Length -ne 8192 -or
    (Get-FileHash -LiteralPath $FirmwarePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $firmwareSha) { throw 'B3_FIRMWARE_HASH_MISMATCH' }
$cardBytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $B3CardPath))
if ($cardBytes.Length -gt 16384 -or (Get-B3Hash $cardBytes) -cne $ExpectedB3CardSha256) { throw 'B3_CARD_HASH_MISMATCH' }
$cardText = [Text.UTF8Encoding]::new($false,$true).GetString($cardBytes).TrimStart([char]0xFEFF)
$card = $cardText | ConvertFrom-Json
$sessionPath = [IO.Path]::GetFullPath($B3SessionDirectory)
if ($card.source_git_sha -cnotmatch '^[a-f0-9]{40}$') { throw 'B3_CARD_SOURCE_SHA_REQUIRED' }
$pins = @{schema='TBS5530-B3-CARD/v1'; approval='OK B3'; model=$ExpectedModel; api=$ExpectedApi; boot_count=$ExpectedBootCount; session_directory=$sessionPath
    app_apk_sha256=$ExpectedAppApkSha256; autonomous_helper_sha256=$ExpectedHelperSha256; b3_helper_sha256=$ExpectedB3HelperSha256
    firmware_sha256=$firmwareSha; reload_plan_sha256=$ExpectedReloadPlanSha256; golden_sha256=$ExpectedFrontendGoldenSha256
    max_bytes=4194304; duration_ms=2000; ttl_ms=300000; heartbeat_ms=2000; permission_wait_ms=30000}
Assert-B3Card $card $pins $B3AttemptOrdinal $PhysicalRecoveryEpoch $B3QueueDepth $B3UrbBytes
if (-not $sessionPath.StartsWith('D:\TSDuck\logs\',[StringComparison]::OrdinalIgnoreCase)) { throw 'B3_PRIVATE_SESSION_PATH_REQUIRED' }
$attemptPath = Join-Path $sessionPath ('attempt-'+$B3AttemptOrdinal)
if ($OutputDirectory -and [IO.Path]::GetFullPath($OutputDirectory) -cne $attemptPath) { throw 'B3_OUTPUT_MUST_MATCH_SESSION_ATTEMPT' }
if (Test-Path -LiteralPath $attemptPath) { throw 'B3_ATTEMPT_OUTPUT_ALREADY_EXISTS' }
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw 'B3_PINNED_ADB_MISSING' }
if (-not (Test-Path -LiteralPath $sessionPath)) { $null=New-Item -ItemType Directory -Path $sessionPath }
$manifestPath = Join-Path $sessionPath 'session-manifest.jsonl'
$manifestStream = [IO.FileStream]::new($manifestPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$consumed=$false; $granted=$false; $terminalReport=$null; $exitCode=1; $failure='HOST_ABORTED_NO_RETRY'; $challenge=$null; $classification=$null
$captureRawSha=$null; $exportManifestSha=$null; $preGrantEvidenceSha=$null; $hostError='HOST_PRE_GRANT_EXCEPTION'
try {
    $prefix = Read-B3Manifest $manifestStream
    $priorReview = Assert-B3Budget $prefix $ExpectedB3CardSha256 $card $B3AttemptOrdinal
    if ($prefix.Length -eq 0) {
        $prefix = Add-B3ManifestRecord $manifestStream $prefix ([pscustomobject]@{event='HEADER';card_sha256=$ExpectedB3CardSha256;session_id=$card.session_id;max_attempts=$card.max_attempts})
    }
    $prefix = Add-B3ManifestRecord $manifestStream $prefix ([pscustomobject]@{event='CONSUME';ordinal=$B3AttemptOrdinal;epoch=$PhysicalRecoveryEpoch;
        physical_cold_confirmed=$true;cold_seconds=$PhysicalColdSeconds;queue_depth=$B3QueueDepth;urb_bytes=$B3UrbBytes;
        previous_offline_validation_sha256=$(if($priorReview){$priorReview.sha256}else{$null});utc=[DateTime]::UtcNow.ToString('o')})
    $consumed=$true
    $null=New-Item -ItemType Directory -Path $attemptPath
    Assert-B3Identity; Assert-B3Apk
    $launchToken=[guid]::NewGuid().ToString()
    $launch=@('shell','am','start','--activity-clear-top','--activity-single-top','-n',"$package/.Tbs5530AutonomousDiagnosticActivity",
        '--es','read_api','NATIVE_USBFS_STREAM','--es','frontend_experiment','B2_RELOAD_AFTER_SET_CONFIG','--es','launch_token',$launchToken,
        '--ez','control_plane_only','false','--ez','control_mirror','true','--ei','b3_queue_depth',[string]$B3QueueDepth,'--ei','b3_urb_bytes',[string]$B3UrbBytes)
    $script:adbStage='B3_LAUNCH'
    $null=Invoke-B3Adb $launch
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while ($watch.ElapsedMilliseconds -lt 10000) {
        $command='if [ -f files/autonomous-v1-challenge.json ]; then cat files/autonomous-v1-challenge.json; fi'
        $text=Invoke-B3Adb @('shell','run-as',$package,'sh','-c',("'"+$command+"'"))
        if ($text) {
            $candidate=$text|ConvertFrom-Json
            if ($candidate.launch_token -ceq $launchToken) { $challenge=$candidate; break }
        }
        Start-Sleep -Milliseconds 100
    }
    Assert-B3Challenge $challenge $launchToken (Get-B3Elapsed)
    Assert-B3Foreground; Assert-B3Identity
    Write-B3NewJson (Join-Path $attemptPath 'challenge.private.json') $challenge
    Write-Warning 'B3: history/no_backup cũ không cấp quyền; giữ nguyên và chỉ export nếu tồn tại sau drain.'
    $directory='files/autonomous-v1-'+$challenge.session
    $encoded=[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $FirmwarePath)))
    $command='base64 -d > '+$directory+'/firmware.bin'
    $null=Invoke-B3Adb @('shell','run-as',$package,'sh','-c',("'"+$command+"'")) $encoded
    $remoteHash=Get-TBS5530Sha256Token (Invoke-B3Adb @('shell','run-as',$package,'sha256sum',($directory+'/firmware.bin')))
    if ($remoteHash -cne $firmwareSha) { throw 'B3_STAGED_FIRMWARE_MISMATCH' }
    $expiry=[long]$challenge.created_elapsed_ms+300000
    $grant=@{}; $challenge.PSObject.Properties | ForEach-Object { $grant[$_.Name]=$_.Value }
    $grant.profile='EXPERIMENTAL_DIAGNOSTIC'; $grant.bounded_user_authorization=$true; $grant.rf_in_confirmed=$true
    $grant.host_script_sha256=$ExpectedB3HelperSha256; $grant.expires_elapsed_ms=$expiry
    $grant.physical_recovery_epoch=$PhysicalRecoveryEpoch; $grant.physical_recovery_confirmed=$true
    $grant.b3_card_sha256=$ExpectedB3CardSha256; $grant.b3_session_id=$card.session_id; $grant.b3_attempt_ordinal=$B3AttemptOrdinal
    $grant.b3_max_attempts=$card.max_attempts; $grant.b3_physical_cold_seconds=$PhysicalColdSeconds
    Write-B3NewJson (Join-Path $attemptPath 'grant.private.json') $grant
    $script:adbStage='B3_GRANT'
    $granted=$true
    $initial=Exchange-B3Heartbeat -Grant $grant
    if ($initial.expired) { throw 'B3_EXPIRED_BEFORE_GRANT' }
    $script:adbStage='B3_RUNNING'
    while ($watch.ElapsedMilliseconds -lt 300000) {
        $beat=Exchange-B3Heartbeat
        if ($null -ne $beat.report -and $null -ne $beat.report.PSObject.Properties['terminal'] -and $beat.report.terminal -ceq $true) {
            $terminalReport=$beat.report; break
        }
        if ($beat.expired) { break }
        Assert-B3PermissionSnapshot $beat.permission $challenge.session $beat.elapsed_ms
        Start-Sleep -Milliseconds 100
    }
    if ($null -eq $terminalReport) { throw 'B3_TERMINAL_DEADLINE_NO_FORCE_CLOSE' }
    if ($terminalReport.launch_token -cne $launchToken -or $terminalReport.frontend_experiment -cne 'B2_RELOAD_AFTER_SET_CONFIG' -or
        $terminalReport.ts_variant -cne 'NATIVE_USBFS_STREAM' -or $terminalReport.control_plane_only -isnot [bool] -or $terminalReport.control_plane_only) {
        throw 'B3_REPORT_SCOPE_MISMATCH'
    }
    if (-not (Test-B3Drained $terminalReport)) { throw 'B3_DRAIN_UNCERTAIN_NO_FORCE_CLOSE' }
    Write-B3NewJson (Join-Path $attemptPath 'terminal-report.json') $terminalReport
    Write-Warning ('B3 history_guard_warning: '+$terminalReport.history_guard_warning)
    $script:adbStage='B3_EXPORT_AFTER_DRAIN'
    $exports=Export-B3Evidence $challenge.session $terminalReport $attemptPath
    $exportManifestSha=(Get-FileHash -LiteralPath (Join-Path $attemptPath 'export-manifest.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    $rawExports=@($exports | Where-Object { $_.path -clike '*/received.ts' -and $_.present })
    if($rawExports.Count -eq 1){$captureRawSha=$rawExports[0].sha256}
    $classification=Get-B3Classification $terminalReport $prefix $exports
    Write-B3NewJson (Join-Path $attemptPath 'host-classification.json') $classification
    $classification | ConvertTo-Json -Compress
    $exitCode=if($classification.single_run_pass){0}else{1}
    $failure=$terminalReport.result
} catch {
    if (-not $granted -and $_.Exception.Message -cmatch '^B3_[A-Z0-9_]+$') { $hostError=$_.Exception.Message }
    throw
} finally {
    if ($granted) {
        try { $null=Exchange-B3Heartbeat -Stop } catch { Write-Warning 'B3: STOP không được xác nhận; watchdog dùng heartbeat thật 2 giây, không reconnect hoặc đóng syscall.' }
    }
    try {
        if ($consumed) {
            $closed=Test-B3SessionClosed $terminalReport
            $drained=Test-B3Drained $terminalReport
            if ($failure -ceq 'HOST_ABORTED_NO_RETRY') { $closed=$true }
            if (-not $granted) {
                $hostEvidence=Write-B3PreGrantEvidence $attemptPath $ExpectedB3CardSha256 $card.session_id $B3AttemptOrdinal $PhysicalRecoveryEpoch $hostError $script:adbStage
                $preGrantEvidenceSha=$hostEvidence.evidence_sha256; $exportManifestSha=$hostEvidence.export_sha256
                $failure='PRE_GRANT_HOST_REFUSAL'; $closed=$false; $drained=$false
            }
            $receipt=[pscustomobject]@{event='RECEIPT';ordinal=$B3AttemptOrdinal;result=$failure;drained=$drained;session_closed=$closed;
                exit_code=$exitCode;single_run_pass=$false;capture_complete=($null -ne $classification -and $classification.capture_complete);
                raw_sha256=$captureRawSha;export_manifest_sha256=$exportManifestSha;queue_depth=$B3QueueDepth;urb_bytes=$B3UrbBytes;
                grant_may_have_been_published=[bool]$granted;pre_grant_evidence_sha256=$preGrantEvidenceSha;
                pre_io_refusal=(-not $granted -or ($null -ne $terminalReport -and $null -ne $terminalReport.PSObject.Properties['pre_native_refusal'] -and $terminalReport.pre_native_refusal -is [bool] -and $terminalReport.pre_native_refusal));
                starvation_counter=$(if($null -ne $terminalReport -and $null -ne $terminalReport.PSObject.Properties['native_stream'] -and
                    $null -ne $terminalReport.native_stream -and $null -ne $terminalReport.native_stream.PSObject.Properties['starvation_counter']){$terminalReport.native_stream.starvation_counter}else{0});
                classification=$(if(-not $granted){'PRE_GRANT_HOST_REFUSAL'}elseif($classification){$classification.classification}else{'B3_NOT_PASS'});
                terminal_result=$(if($terminalReport){$terminalReport.result}else{'UNAVAILABLE'});utc=[DateTime]::UtcNow.ToString('o')}
            $prefix=Add-B3ManifestRecord $manifestStream $prefix $receipt
        }
    } finally { $manifestStream.Dispose() }
}
exit $exitCode
