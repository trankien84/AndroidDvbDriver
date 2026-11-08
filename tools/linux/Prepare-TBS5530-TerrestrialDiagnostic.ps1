[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param(
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [string]$SourcePath = (Join-Path $PSScriptRoot '..\..\..\references\cxd28xx')
)
$ErrorActionPreference = 'Stop'
$env:GIT_NO_LAZY_FETCH = '1'
$env:GIT_OPTIONAL_LOCKS = '0'
$git = (Get-Command git -ErrorAction Stop).Source
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'terrestrial-diagnostic-base.json') -Raw | ConvertFrom-Json
$source = (Resolve-Path -LiteralPath $SourcePath).Path
$output = [IO.Path]::GetFullPath($OutputPath)
$allowed = (Resolve-Path -LiteralPath 'D:\TSDuck').Path.TrimEnd('\') + '\'
if (-not $output.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) { throw 'Output phải là thư mục scratch mới dưới D:\TSDuck.' }
if (Test-Path -LiteralPath $output) { throw 'Output đã tồn tại; không ghi đè.' }
$parent = Split-Path $output -Parent
if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw 'Parent output phải tồn tại.' }
for ($item = Get-Item -LiteralPath $parent; $null -ne $item; $item = $item.Parent) {
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Không nhận output qua reparse point.' }
}
$head = & $git -C $source rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $head -ne $manifest.SourceSHA) { throw 'Reference SHA không khớp pinned baseline.' }
$patch = Join-Path $PSScriptRoot 'tbs5530-terrestrial-diagnostic.patch'
$guard = Join-Path $PSScriptRoot 'support\tbs5530_diag_guard.h'
if ((Get-FileHash -LiteralPath $patch).Hash -ne $manifest.PatchSHA256) { throw 'Patch hash mismatch.' }
if ((Get-FileHash -LiteralPath $guard).Hash -ne $manifest.GuardSHA256) { throw 'Guard hash mismatch.' }
foreach ($entry in $manifest.Files) {
    if ($entry.Path -notmatch '^[A-Za-z0-9_.-]+$') { throw 'Unsafe manifest path.' }
    if ((Get-FileHash -LiteralPath (Join-Path $source $entry.Path)).Hash -ne $entry.SHA256) { throw "Base hash mismatch: $($entry.Path)" }
}
foreach ($asset in $manifest.Assets) {
    if ($asset.Source -notmatch '^(support|tests)\\[A-Za-z0-9_.-]+$|^Build-TerrestrialDiagnosticOffline\.sh$' -or $asset.Output -notmatch '^[A-Za-z0-9_.-]+$') { throw 'Unsafe asset path.' }
    $assetPath = Join-Path $PSScriptRoot $asset.Source
    if ((Get-FileHash -LiteralPath $assetPath).Hash -ne $asset.SHA256) { throw "Asset hash mismatch: $($asset.Source)" }
}
if (-not $PSCmdlet.ShouldProcess($output, 'Copy pinned source vào scratch, apply patch và kiểm hash; không SSH/build/load')) { return }
New-Item -ItemType Directory -Path $output | Out-Null
foreach ($entry in $manifest.Files) { Copy-Item -LiteralPath (Join-Path $source $entry.Path) -Destination (Join-Path $output $entry.Path) }
& $git -C $output apply --check -- $patch
if ($LASTEXITCODE -ne 0) { throw 'git apply --check thất bại; giữ scratch để kiểm.' }
& $git -C $output apply -- $patch
if ($LASTEXITCODE -ne 0) { throw 'git apply thất bại; không build.' }
foreach ($entry in $manifest.Files) {
    if ((Get-FileHash -LiteralPath (Join-Path $output $entry.Path)).Hash -ne $entry.OutputSHA256) { throw "Output mismatch: $($entry.Path)" }
}
foreach ($asset in $manifest.Assets) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $asset.Source) -Destination (Join-Path $output $asset.Output)
}
& (Join-Path $PSScriptRoot 'Test-TBS5530-TerrestrialDiagnostic.ps1') -SourcePath $output
& (Join-Path $PSScriptRoot 'Test-TBS5530-Operational.ps1') -SourcePath $output
Write-Output 'PREPARED_ONLY: source và patch đã kiểm; không SSH, install, load, USB hoặc snapshot.'
