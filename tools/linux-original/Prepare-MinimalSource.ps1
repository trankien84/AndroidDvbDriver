# SPDX-License-Identifier: GPL-2.0-only
#Requires -Version 7.0
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory)][string]$OutputPath,
    [string]$SourcePath = (Join-Path $PSScriptRoot '..\..\..\references\cxd28xx'),
    [switch]$EarlyStreamTiming
)
$ErrorActionPreference = 'Stop'
$git = (Get-Command git -CommandType Application -ErrorAction Stop).Source
$manifest = Get-Content (Join-Path $PSScriptRoot 'minimal-source-manifest.json') -Raw | ConvertFrom-Json
$source = (Resolve-Path -LiteralPath $SourcePath).Path
$output = [IO.Path]::GetFullPath($OutputPath)
if (-not $output.StartsWith('D:\TSDuck\', [StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $output)) { throw 'Output must be new scratch under D:\TSDuck' }
$parent = Get-Item -LiteralPath (Split-Path $output -Parent)
for ($item = $parent; $null -ne $item; $item = $item.Parent) {
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'No output through reparse points' }
}
$head = & $git -C $source rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $head -cne $manifest.sourceSHA) { throw 'Source revision mismatch' }
foreach ($file in $manifest.files) {
    if ($file.name -notmatch '^[A-Za-z0-9_.-]+$' -or (Get-FileHash (Join-Path $source $file.name)).Hash.ToLowerInvariant() -cne $file.sha256) { throw "Source hash mismatch: $($file.name)" }
}
foreach ($patch in $manifest.patches) {
    if ($patch.name -notmatch '^[a-z-]+\.patch$' -or (Get-FileHash (Join-Path $PSScriptRoot $patch.name)).Hash.ToLowerInvariant() -cne $patch.sha256) { throw 'Patch hash mismatch' }
}
if ((Get-FileHash (Join-Path $PSScriptRoot 'tbs5530_min_guard.h')).Hash.ToLowerInvariant() -cne $manifest.guardSHA256) { throw 'Guard hash mismatch' }
if ($EarlyStreamTiming) {
    $timing = Get-Content (Join-Path $PSScriptRoot 'early-timing-manifest.json') -Raw | ConvertFrom-Json
    if ((Get-FileHash (Join-Path $PSScriptRoot 'minimal-source-manifest.json')).Hash.ToLowerInvariant() -cne $timing.baseManifestSHA256 -or (Get-FileHash (Join-Path $PSScriptRoot 'early-stream-timing.patch')).Hash.ToLowerInvariant() -cne $timing.patchSHA256) { throw 'Timing patch provenance mismatch' }
}
if (-not $PSCmdlet.ShouldProcess($output, 'Copy pinned source and apply source-preserving M patches; no SSH/build/load/USB')) { return }
New-Item -ItemType Directory -Path $output | Out-Null
foreach ($file in $manifest.files) { Copy-Item -LiteralPath (Join-Path $source $file.name) -Destination (Join-Path $output $file.name) }
foreach ($patch in $manifest.patches) {
    $p = Join-Path $PSScriptRoot $patch.name
    & $git -C $output apply --check -- $p
    if ($LASTEXITCODE -ne 0) { throw 'Patch admission failed; scratch preserved' }
    & $git -C $output apply -- $p
    if ($LASTEXITCODE -ne 0) { throw 'Patch apply failed; scratch preserved' }
}
Copy-Item (Join-Path $PSScriptRoot 'tbs5530_min_guard.h') (Join-Path $output 'tbs5530_min_guard.h')
foreach ($file in $manifest.files) {
    if ((Get-FileHash (Join-Path $output $file.name)).Hash.ToLowerInvariant() -cne $file.outputSHA256) { throw "Output hash mismatch: $($file.name)" }
}
if ($EarlyStreamTiming) {
    $p = Join-Path $PSScriptRoot 'early-stream-timing.patch'
    & $git -C $output apply --check -- $p
    if ($LASTEXITCODE -ne 0) { throw 'Early timing patch admission failed' }
    & $git -C $output apply -- $p
    if ($LASTEXITCODE -ne 0) { throw 'Early timing patch apply failed' }
    foreach ($file in $timing.files) {
        if ($file.name -notmatch '^tbs5530(\.c|_min_guard\.h)$' -or (Get-FileHash (Join-Path $output $file.name)).Hash.ToLowerInvariant() -cne $file.sha256) { throw 'Early timing output hash mismatch' }
    }
}
Write-Output 'PREPARED_M_NOT_ORIGINAL: hashes verified; no build, firmware staging or hardware operations.'
