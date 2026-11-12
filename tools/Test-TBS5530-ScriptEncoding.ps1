# SPDX-License-Identifier: GPL-2.0-or-later
# Offline: prove identical UTF-8 decoding in PowerShell 7 and Windows PowerShell 5.1.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$failures = @()
$checks = 0
foreach ($name in @('Invoke-TBS5530-AutonomousSession.ps1', 'Initialize-TBS5530-ReloadStore.ps1',
    'Test-TBS5530-B2Reload.ps1', 'Test-TBS5530-ReloadInitializer.ps1')) {
    $path = Join-Path $PSScriptRoot $name
    $bytes = [IO.File]::ReadAllBytes($path)
    if ($bytes.Length -lt 3 -or $bytes[0] -ne 0xEF -or $bytes[1] -ne 0xBB -or $bytes[2] -ne 0xBF) {
        $failures += "$name requires a UTF-8 BOM for Windows PowerShell 5.1."
        continue
    }
    $text = $utf8.GetString($bytes, 3, $bytes.Length - 3)
    $tokens = $null; $errors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
    if ($errors.Count -or $ast.Extent.Text -cne $text) {
        $failures += "$name was not parsed as the exact UTF-8 source."
        continue
    }
    $checks++
}
if ($failures.Count) { throw ($failures -join [Environment]::NewLine) }
Write-Output "PASS $checks/4 encoding regressions; exact UTF-8 parser text, no device access."
