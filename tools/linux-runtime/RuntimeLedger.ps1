# SPDX-License-Identifier: GPL-2.0-only
Set-StrictMode -Version 2.0
function Get-TbsLinuxAttachmentKey {
    param([Parameter(Mandatory)][string]$BootId, [Parameter(Mandatory)][int]$Bus,
        [Parameter(Mandatory)][int]$Device, [Parameter(Mandatory)][string]$DescriptorSha256)
    if ($BootId -notmatch '^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$' -or $Bus -lt 1 -or $Bus -gt 999 -or $Device -lt 1 -or $Device -gt 127 -or $DescriptorSha256 -cnotmatch '^[a-f0-9]{64}$') { throw 'Invalid verified attachment identity' }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes("tbs5530-linux-operational-v1|$BootId|$Bus|$Device|$DescriptorSha256")))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function Assert-TbsLinuxLedgerDirectory {
    param([Parameter(Mandatory)][string]$Directory)
    $full = [IO.Path]::GetFullPath($Directory)
    if (-not (Test-Path -LiteralPath $full -PathType Container)) { throw 'Ledger directory must be explicitly initialized' }
    for ($p = Get-Item -LiteralPath $full; $null -ne $p; $p = $p.Parent) {
        if ($p.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Ledger path cannot contain reparse points' }
    }
    return $full
}
function Assert-TbsLinuxAttemptAvailable {
    param([Parameter(Mandatory)][string]$Directory, [Parameter(Mandatory)][string]$AttachmentKey)
    $full = Assert-TbsLinuxLedgerDirectory $Directory
    if ($AttachmentKey -cnotmatch '^[a-f0-9]{64}$') { throw 'Invalid attachment key' }
    if (Test-Path -LiteralPath (Join-Path $full ($AttachmentKey + '.json'))) { throw 'Attachment already consumed or uncertain; no replay' }
}
function Add-TbsLinuxAttempt {
    param([Parameter(Mandatory)][string]$Directory, [Parameter(Mandatory)][string]$AttachmentKey,
        [Parameter(Mandatory)][string]$RunId, [Parameter(Mandatory)][string]$ModuleSha256)
    $full = Assert-TbsLinuxLedgerDirectory $Directory
    if ($AttachmentKey -cnotmatch '^[a-f0-9]{64}$' -or $ModuleSha256 -cnotmatch '^[a-f0-9]{64}$' -or $RunId -notmatch '^phase31-[a-z0-9-]{1,100}$') { throw 'Invalid attempt metadata' }
    $record = [ordered]@{ schema = 'tbs5530.linux-attempt/1'; state = 'CONSUMED_BEFORE_MODULE_LOAD'; key = $AttachmentKey; run = $RunId; module_sha256 = $ModuleSha256; created_utc = [DateTime]::UtcNow.ToString('o'); mode = 'OPERATIONAL_ONLY_NO_A0' }
    $path = Join-Path $full ($AttachmentKey + '.json')
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($record | ConvertTo-Json -Compress))
    $stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
    return [pscustomobject]@{ path = $path; key = $AttachmentKey; state = $record.state }
}
function Write-TbsLinuxAttemptResult {
    param([Parameter(Mandatory)][string]$Directory, [Parameter(Mandatory)][string]$AttachmentKey,
        [Parameter(Mandatory)][ValidateSet('COMPLETED', 'BLOCKED', 'UNCERTAIN')][string]$State,
        [Parameter(Mandatory)][string]$Detail)
    $full = Assert-TbsLinuxLedgerDirectory $Directory
    if ($AttachmentKey -cnotmatch '^[a-f0-9]{64}$' -or [string]::IsNullOrWhiteSpace($Detail)) { throw 'Invalid result metadata' }
    $consumed = Join-Path $full ($AttachmentKey + '.json')
    if (-not (Test-Path -LiteralPath $consumed -PathType Leaf)) { throw 'Missing consumed attempt; never recreate it' }
    $record = Get-Content -LiteralPath $consumed -Raw | ConvertFrom-Json
    if ($record.schema -ne 'tbs5530.linux-attempt/1' -or $record.key -cne $AttachmentKey -or $record.state -ne 'CONSUMED_BEFORE_MODULE_LOAD') { throw 'Malformed consumed record; keep blocked' }
    $path = Join-Path $full ($AttachmentKey + '.result.json')
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes((@{ state = $State; detail = $Detail; at_utc = [DateTime]::UtcNow.ToString('o') } | ConvertTo-Json -Compress))
    $stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
}
