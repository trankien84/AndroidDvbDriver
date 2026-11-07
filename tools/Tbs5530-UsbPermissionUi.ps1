# SPDX-License-Identifier: GPL-2.0-or-later
# Pure selectors and bounded child-process helpers; no permission is granted by this module.
function Get-TBS5530UsbPermissionButton {
    param([object[]]$Windows, [string]$Foreground, [string]$ApplicationLabel = 'DVB-T Driver')
    if ($Foreground -notmatch 'com\.android\.systemui/(?:\.usb\.|com\.android\.systemui\.usb\.)UsbPermissionActivity(?:\s|\})') { return $null }
    $active = @($Windows | Where-Object { $_.PSObject.Properties['active'] -and $_.active -eq $true -and $_.PSObject.Properties['focused'] -and $_.focused -eq $true })
    if ($active.Count -ne 1) { return $null }
    $nodes = @($active[0].content)
    $messages = @($nodes | Where-Object { $_.PSObject.Properties['text'] -and $_.text.Contains($ApplicationLabel) -and $_.text -match 'USB' })
    $buttons = @($nodes | Where-Object { $_.PSObject.Properties['resource-id'] -and $_.'resource-id' -ceq 'android:id/button1' -and $_.interactions -contains 'CLICKABLE' })
    $denials = @($nodes | Where-Object { $_.PSObject.Properties['resource-id'] -and $_.'resource-id' -ceq 'android:id/button2' })
    $checkedDefaults = @($nodes | Where-Object { $_.PSObject.Properties['class'] -and $_.class -match 'CheckBox$' -and $_.PSObject.Properties['state'] -and $_.state -contains 'CHECKED' })
    if ($messages.Count -ne 1 -or $buttons.Count -ne 1 -or $denials.Count -ne 1 -or $checkedDefaults.Count -ne 0) { return $null }
    if (-not $buttons[0].PSObject.Properties['text'] -or $buttons[0].text -notmatch '^(OK|Allow|Cho phép|Đồng ý)$') { return $null }
    return 'id=/^android:id\/button1$/'
}
function Test-TBS5530SingleUsbInventory {
    param([string]$Dump)
    $hostSection = [regex]::Match($Dump, '(?s)host_manager=\{(.*?)num_connects=')
    if (-not $hostSection.Success) { return $false }
    $vendors = [regex]::Matches($hostSection.Groups[1].Value, '(?m)^\s+vendor_id=(\d+)\s*$')
    $products = [regex]::Matches($hostSection.Groups[1].Value, '(?m)^\s+product_id=(\d+)\s*$')
    return $vendors.Count -eq 1 -and $products.Count -eq 1 -and $vendors[0].Groups[1].Value -ceq '29516' -and $products[0].Groups[1].Value -ceq '21808'
}
function Test-TBS5530PendingPermission {
    param([object]$Value, [string]$Session, [long]$Now)
    if ($null -eq $Value) { return $false }
    foreach ($name in @('version','session','request_token','attachment_generation','device_identity_sha256','vid','pid','state','requested_elapsed_ms','elapsed_ms')) {
        if (-not $Value.PSObject.Properties[$name]) { return $false }
    }
    return $Value.version -eq 1 -and $Value.session -ceq $Session -and $Value.state -ceq 'PENDING' -and
        $Value.request_token -cmatch '^[a-f0-9-]{36}$' -and $Value.device_identity_sha256 -cmatch '^[a-f0-9]{64}$' -and
        $Value.attachment_generation -ge 0 -and $Value.vid -eq 29516 -and $Value.pid -eq 21808 -and
        $Value.requested_elapsed_ms -ge 0 -and $Value.requested_elapsed_ms -le $Value.elapsed_ms -and
        $Value.elapsed_ms -le $Now -and $Now - $Value.elapsed_ms -lt 2000
}
function Start-TBS5530PermissionProcess {
    param([string]$Executable, [string[]]$Arguments)
    if (-not (Test-Path -LiteralPath $Executable -PathType Leaf)) { throw 'USER_ACTION_REQUIRED: thiếu Android CLI đã kiểm.' }
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $Executable; $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = [Text.Encoding]::UTF8; $psi.StandardErrorEncoding = [Text.Encoding]::UTF8
    foreach ($argument in $Arguments) { $psi.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $psi
    try {
        if (-not $process.Start()) { throw 'USER_ACTION_REQUIRED: không khởi chạy được UI helper.' }
        return [pscustomobject]@{ Process = $process; Output = $process.StandardOutput.ReadToEndAsync(); Error = $process.StandardError.ReadToEndAsync(); Started = [Diagnostics.Stopwatch]::StartNew() }
    } catch { $process.Dispose(); throw }
}
function Stop-TBS5530PermissionProcess {
    param([object]$Job)
    if ($null -eq $Job) { return }
    try { if (-not $Job.Process.HasExited) { Stop-Process -Id $Job.Process.Id -Force -ErrorAction Stop } }
    finally { $Job.Process.Dispose() }
}
