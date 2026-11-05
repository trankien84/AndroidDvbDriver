# Read-only bounded identity preflight, shared by the isolated runner and approval channel.
function Get-TBS5530ApprovalTarget {
    param([Parameter(Mandatory)][string]$AdbPath, [Parameter(Mandatory)][string]$Serial, [long]$DeadlineTimestamp = 0)
    if ($Serial -notmatch '^[A-Za-z0-9._-]+:[0-9]+$') { throw 'FAIL-STOP: cần đúng serial Wireless ADB TCP.' }
    if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw 'Không tìm thấy ADB.' }
    function Invoke-IdentityRead([string]$Arguments) {
        $start = [Diagnostics.ProcessStartInfo]::new()
        $start.FileName = $AdbPath; $start.Arguments = $Arguments
        $start.UseShellExecute = $false; $start.CreateNoWindow = $true
        $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
        $child = [Diagnostics.Process]::new(); $child.StartInfo = $start
        try {
            if ($DeadlineTimestamp -gt 0 -and [Diagnostics.Stopwatch]::GetTimestamp() -ge $DeadlineTimestamp) {
                throw 'FAIL-STOP: toàn bộ harness đã hết hạn trước identity preflight.'
            }
            if (-not $child.Start()) { throw 'FAIL-STOP: không tạo được identity preflight.' }
            $output = $child.StandardOutput.ReadToEndAsync(); $errors = $child.StandardError.ReadToEndAsync()
            $waitMs = 5000
            if ($DeadlineTimestamp -gt 0) {
                $remaining = [long](($DeadlineTimestamp - [Diagnostics.Stopwatch]::GetTimestamp()) * 1000 / [Diagnostics.Stopwatch]::Frequency)
                if ($remaining -le 0) { $child.Kill(); throw 'FAIL-STOP: toàn bộ harness đã hết hạn.' }
                $waitMs = [int][Math]::Min($waitMs, $remaining)
            }
            if (-not $child.WaitForExit($waitMs)) { $child.Kill(); throw 'FAIL-STOP: identity preflight quá hạn; không reconnect.' }
            if ($child.ExitCode -ne 0) { throw 'FAIL-STOP: identity preflight mất kết nối.' }
            if ($DeadlineTimestamp -gt 0 -and [Diagnostics.Stopwatch]::GetTimestamp() -ge $DeadlineTimestamp) {
                throw 'FAIL-STOP: toàn bộ harness đã hết hạn sau identity preflight.'
            }
            return $output.Result
        } finally { $child.Dispose() }
    }
    $lines = @(Invoke-IdentityRead 'devices -l') -split '\r?\n'
    if (@($lines | Where-Object { $_ -match '\s(offline|unauthorized)\b' }).Count) {
        throw 'FAIL-STOP: có thiết bị offline/unauthorized.'
    }
    $online = @($lines | Where-Object { $_ -match '^\S+:\d+\s+device\s' })
    if ($online.Count -ne 1 -or ($online[0] -split '\s+')[0] -cne $Serial) {
        throw 'FAIL-STOP: serial đã đổi hoặc không còn đúng một target TCP online.'
    }
    $raw = Invoke-IdentityRead ('-s ' + $Serial + ' shell "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.sdk; cat /proc/sys/kernel/random/boot_id; pm list features"')
    $values = @($raw.Trim() -split '\r?\n')
    if ($values.Count -lt 5 -or $values[0].Trim() -cne 'OnePlus' -or $values[1].Trim() -cne 'GM1900' -or
        $values[2].Trim() -cne '30' -or $values[3].Trim() -cnotmatch '^[a-f0-9-]{36}$' -or
        -not @($values | Where-Object { $_.Trim() -ceq 'feature:android.hardware.usb.host' }).Count) {
        throw 'FAIL-STOP: cần OnePlus GM1900/API 30 có USB Host; không thao tác USB.'
    }
    return [pscustomobject]@{ Serial = $Serial; Identity = ('OnePlus|GM1900|30|' + $values[3].Trim()); AdbSubprocesses = 2 }
}
