# SPDX-License-Identifier: GPL-2.0-or-later
<#
.SYNOPSIS
Chuẩn hóa trace USB offline thành JSONL và báo cáo, không truy cập thiết bị.
.DESCRIPTION
NativeJsonl: writer NativeUsbfsUrb của repo, gồm summary cuối tùy chọn.
UsbmonText: text S/C/E bulk/control/interrupt, không nhận usbmon binary/isochronous.
UsbPcapJson: TShark -T json -e với các trường đã kiểm ở phiên bản đang cài.
CaptureEpoch là nhãn phiên capture, không phải thời gian Unix. AttachmentEpoch là
nhãn lần gắn thiết bị; cấp AttachmentBoundaries khi biết reset/tháo cắm trong file.
Metadata chỉ nhận thông tin đã kiểm. Không tự suy host/driver/build/lock/mode.
Mặc định không xuất payload. Không merge clocks hoặc suy requested từ actual.
WhatIf không tạo thư mục/file. Không ghi đè nếu thiếu Force. Lỗi parse không tạo
output. Input phải là UTF-8 và nằm trong MaxInputBytes; MaxRecords giới hạn sự kiện.
.EXAMPLE
.\tools\Convert-TBS5530-Trace.ps1 -InputPath D:\TSDuck\logs\native.jsonl -Format NativeJsonl -CaptureEpoch run-a -AttachmentEpoch attach-a -OutputPath D:\TSDuck\reports\run-a\native.normalized.jsonl
.EXAMPLE
.\tools\Convert-TBS5530-Trace.ps1 -InputPath D:\TSDuck\traces\export.json -Format UsbPcapJson -CaptureEpoch cap-a -AttachmentEpoch attach-a -OutputPath D:\TSDuck\reports\cap-a\usb.normalized.jsonl -WhatIf
#>
[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param(
    [Parameter(Mandatory)][string]$InputPath,
    [Parameter(Mandatory)][ValidateSet('NativeJsonl', 'UsbmonText', 'UsbPcapJson')][string]$Format,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$CaptureEpoch,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$AttachmentEpoch,
    [Parameter(Mandatory)][string]$OutputPath,
    [string]$ReportPath,
    [hashtable]$Metadata = @{}, [hashtable]$AttachmentBoundaries = @{},
    [string]$UsbmonClockDomain = 'usbmon.timestamp_us:unspecified',
    [ValidateRange(1, 1000000)][int]$MaxRecords = 100000,
    [ValidateRange(1, 268435456)][long]$MaxInputBytes = 67108864,
    [switch]$IncludePayload, [ValidateRange(0, 64)][int]$PayloadExcerptBytes = 0,
    [switch]$Force
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Tbs5530-TraceNormalizer.ps1')
$inputFull = (Resolve-Path -LiteralPath $InputPath -ErrorAction Stop).ProviderPath
$outputFull = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
if ([string]::IsNullOrWhiteSpace($ReportPath)) { $ReportPath = $outputFull + '.summary.json' }
$reportFull = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ReportPath)
if ($inputFull -eq $outputFull -or $inputFull -eq $reportFull -or $outputFull -eq $reportFull) {
    throw 'Input, output and report must have distinct paths'
}
foreach ($p in @($outputFull, $reportFull)) {
    if (Test-Path -LiteralPath $p) {
        if ((Get-Item -LiteralPath $p).PSIsContainer -or -not $Force) { throw 'Output/report exists; choose a new path or use -Force' }
        if ((Get-Item -LiteralPath $p).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing to overwrite a linked output' }
    }
}
if (-not $PSCmdlet.ShouldProcess("$outputFull ; $reportFull", 'Write offline normalized evidence')) { return }
$params = @{
    InputPath = $inputFull; Format = $Format; CaptureEpoch = $CaptureEpoch
    AttachmentEpoch = $AttachmentEpoch; Metadata = $Metadata; AttachmentBoundaries = $AttachmentBoundaries
    UsbmonClockDomain = $UsbmonClockDomain; MaxRecords = $MaxRecords; MaxInputBytes = $MaxInputBytes
    IncludePayload = $IncludePayload; PayloadExcerptBytes = $PayloadExcerptBytes
}
$result = Convert-Tbs5530Trace @params
$utf8 = [Text.UTF8Encoding]::new($false)
# Publish each file atomically from a unique sibling, never a system temp directory.
$staged = [Collections.Generic.List[object]]::new()
try {
    foreach ($target in @($outputFull, $reportFull)) {
        $parent = Split-Path -Parent $target
        if (-not (Test-Path -LiteralPath $parent)) { [IO.Directory]::CreateDirectory($parent) | Out-Null }
        $stage = Join-Path $parent ('.normalizer-' + [guid]::NewGuid().ToString('N') + '.partial')
        $stream = [IO.File]::Open($stage, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        $staged.Add([pscustomobject]@{ source = $stage; target = $target })
        $writer = [IO.StreamWriter]::new($stream, $utf8)
        try {
            if ($target -eq $outputFull) {
                foreach ($r in $result.Records) { $writer.WriteLine(($r | ConvertTo-Json -Depth 32 -Compress)) }
            } else { $writer.WriteLine(($result.Summary | ConvertTo-Json -Depth 32)) }
            $writer.Flush(); $stream.Flush($true)
        } finally { $writer.Dispose() }
    }
    foreach ($entry in $staged) {
        if ($Force -and [IO.File]::Exists($entry.target)) { [IO.File]::Replace($entry.source, $entry.target, [NullString]::Value) }
        else { [IO.File]::Move($entry.source, $entry.target) }
    }
} finally {
    foreach ($entry in $staged) { if ([IO.File]::Exists($entry.source)) { [IO.File]::Delete($entry.source) } }
}
$result.Summary
