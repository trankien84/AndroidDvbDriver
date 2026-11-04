# This helper never starts the UI or sends USB requests. Run only after separate session approval.
[CmdletBinding()]
param(
    [switch]$ExplicitOneShotApproval,
    [switch]$PhysicalHostAndOtgConfirmed,
    [string]$AdbPath = 'D:\Android\Sdk\platform-tools\adb.exe'
)
$ErrorActionPreference = 'Stop'
if (-not $ExplicitOneShotApproval -or -not $PhysicalHostAndOtgConfirmed) {
    throw 'BLOCKED: cần phê duyệt riêng một lượt upload và xác nhận chuyển host/nguồn OTG.'
}
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw 'Không tìm thấy ADB.' }
$package = 'info.martinmarinov.dvbdriver'
$serial = $null
function Assert-Target {
    $lines = @(& $AdbPath devices -l 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'FAIL-STOP: không đọc được danh sách ADB.' }
    $targets = @($lines | Where-Object { $_ -match '^\S+:\d+\s+device\s' })
    if ($targets.Count -ne 1 -or @($lines | Where-Object { $_ -match '\s(offline|unauthorized)\b' }).Count) {
        throw 'FAIL-STOP: Wireless ADB không còn đúng một target online.'
    }
    $current = ($targets[0] -split '\s+')[0]
    if ($script:serial -and $current -ne $script:serial) { throw 'FAIL-STOP: serial đã đổi.' }
    $script:serial = $current
    foreach ($check in @(@('ro.product.manufacturer','OnePlus'), @('ro.product.model','GM1900'), @('ro.build.version.sdk','30'))) {
        $value = (& $AdbPath -s $script:serial shell getprop $check[0] 2>$null) -join ''
        if ($LASTEXITCODE -ne 0 -or $value.Trim() -ne $check[1]) { throw 'FAIL-STOP: sai target.' }
    }
}
function Read-Private([string]$name) {
    $text = (& $AdbPath -s $script:serial shell run-as $package cat "files/$name" 2>$null) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'FAIL-STOP: không đọc được metadata private.' }
    return ($text | ConvertFrom-Json)
}
function Elapsed-Millis {
    $text = (& $AdbPath -s $script:serial shell cat /proc/uptime 2>$null) -join ''
    if ($LASTEXITCODE -ne 0) { throw 'FAIL-STOP: không đọc được đồng hồ target.' }
    return [long]([double]::Parse(($text.Trim() -split '\s+')[0], [Globalization.CultureInfo]::InvariantCulture) * 1000)
}
Assert-Target
$challenge = Read-Private 'phase29-challenge.json'
if ($challenge.plan -cne 'TBS_SOURCE_DUAL_RESET' -or $challenge.upload_epoch -ne 1 -or
    $challenge.sha256 -cne 'b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc' -or
    $challenge.attachment_hash -notmatch '^[a-f0-9]{64}$') { throw 'Sai challenge/plan/hash.' }
$deadline = [long]$challenge.created_elapsed_ms + 300000
$ordinal = 0
Write-Output 'Giám sát phê duyệt một lượt đang chạy; chưa có USB write do helper phát.'
try {
    while ($true) {
        Assert-Target
        $now = Elapsed-Millis
        if ($now -ge $deadline -or $now -lt [long]$challenge.created_elapsed_ms) { break }
        # A request may not exist until the UI confirmation. Missing is not a hardware action.
        $raw = (& $AdbPath -s $serial shell run-as $package cat files/phase29-write-request.json 2>$null) -join "`n"
        if ($LASTEXITCODE -eq 0) {
            $request = $raw | ConvertFrom-Json
            if ($request.challenge -ceq $challenge.challenge) {
                $next = [int]$request.ordinal
                if ($next -lt $ordinal -or $next -gt $ordinal + 1 -or $next -lt 1 -or $next -gt 132) {
                    throw 'FAIL-STOP: thứ tự bước không hợp lệ.'
                }
                $ordinal = $next
            }
        }
        $permit = [ordered]@{
            challenge = $challenge.challenge; install = $challenge.install; upload_epoch = 1
            attachment_hash = $challenge.attachment_hash; plan = $challenge.plan; sha256 = $challenge.sha256
            explicit_session_upload_approval = $true; physical_host_and_otg_confirmed = $true
            expires_elapsed_ms = $deadline; heartbeat_elapsed_ms = $now; authorized_ordinal = $ordinal
        } | ConvertTo-Json -Compress
        $permit | & $AdbPath -s $serial shell run-as $package sh -c '"cat > files/phase29-session-approval.tmp && mv files/phase29-session-approval.tmp files/phase29-session-approval.json"' 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'FAIL-STOP: không ghi được acknowledgment.' }
        $reportText = (& $AdbPath -s $serial shell run-as $package cat files/tbs5530-diagnostic.json 2>$null) -join "`n"
        if ($LASTEXITCODE -eq 0) {
            $report = $reportText | ConvertFrom-Json
            if ($report.operation -eq 'DEBUG_ONE_SHOT_FX2' -and -not $report.pending) {
                Write-Output ('Kết quả ứng dụng: ' + $report.state + '; attempted=' + $report.attempted + '; completed=' + $report.completed)
                break
            }
        }
        Start-Sleep -Milliseconds 50
    }
} finally {
    # On lost ADB do not reconnect, touch USB, or issue corrective writes. The permit expires locally.
    Write-Output 'Đã dừng cấp acknowledgment. Không retry hoặc gửi lệnh USB khắc phục.'
}
