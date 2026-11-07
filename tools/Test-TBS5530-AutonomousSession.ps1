# SPDX-License-Identifier: GPL-2.0-or-later
# Offline only: parse/negative preflight tests never start ADB or access a device.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'PowerShell parse failed.' }
$text = [IO.File]::ReadAllText($path)
foreach ($required in @('GrantBoundedSession', 'ProofOfGuardPassed', 'ExpectedBootCount', 'ExpectedModel', 'ExpectedApi',
    'EXPERIMENTAL_DIAGNOSTIC', 'max_upload_per_attachment', 'expires_elapsed_ms', '.hosttmp', 'stop = $true',
    'WaitForExit(1500)', "@('-s', `$Serial)")) {
    if (-not $text.Contains($required)) { throw ('Missing guard: ' + $required) }
}
foreach ($forbidden in @('adb connect', 'adb reconnect', 'pm clear', 'uninstall', 'usbipd', 'force-stop')) {
    if ($text -match [regex]::Escape($forbidden)) { throw ('Forbidden side effect: ' + $forbidden) }
}
try {
    & $path -Serial 'offline-fixture' -ExpectedModel GM1900 -ExpectedApi 30 -ExpectedBootCount 1
    throw 'Expected session grant refusal.'
} catch { if ($_.Exception.Message -notmatch '^BLOCKED: cần quyền phiên') { throw } }
try {
    & $path -Serial 'offline-fixture' -ExpectedModel GM1900 -ExpectedApi 30 -ExpectedBootCount 1 -GrantBoundedSession
    throw 'Expected proof-of-guard refusal.'
} catch { if ($_.Exception.Message -notmatch '^BLOCKED: cần quyền phiên') { throw } }
try {
    & $path -Serial 'offline-fixture' -ExpectedModel GM1900 -ExpectedApi 30 -ExpectedBootCount 1 -GrantBoundedSession -ProofOfGuardPassed -AdbPath (Join-Path $PSScriptRoot 'no-such-adb.exe')
    throw 'Expected missing ADB refusal.'
} catch { if ($_.Exception.Message -cne 'Không tìm thấy ADB đã chọn.') { throw } }
foreach ($case in @(
    @{ PhysicalRecoveryEpoch = '11111111-1111-4111-8111-111111111111'; Expected = '^BLOCKED: physical recovery epoch' },
    @{ PhysicalRecoveryEpoch = 'malformed'; PhysicalRecoveryConfirmed = $true; Expected = '^BLOCKED: physical recovery epoch' },
    @{ PhysicalRecoveryEpoch = '00000000-0000-0000-0000-000000000000'; PhysicalRecoveryConfirmed = $true; Expected = '^BLOCKED: physical recovery epoch' },
    @{ PhysicalRecoveryConfirmed = $true; Expected = '^BLOCKED: xác nhận physical recovery thiếu epoch' }
)) {
    $expected = $case.Expected; $case.Remove('Expected')
    try {
        & $path -Serial 'offline-fixture' -ExpectedModel GM1900 -ExpectedApi 30 -ExpectedBootCount 1 -GrantBoundedSession -ProofOfGuardPassed @case
        throw 'Expected physical recovery refusal.'
    } catch { if ($_.Exception.Message -notmatch $expected) { throw } }
}
if ($text -match 'NewGuid' -or -not $text.Contains('$grant.physical_recovery_epoch = $PhysicalRecoveryEpoch')) {
    throw 'Recovery epoch must come from the operator, not a generated per-attempt nonce.'
}
$exitFunction = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Get-TBS5530AutonomousExitCode' }, $true)
if ($null -eq $exitFunction) { throw 'Missing terminal exit classifier.' }
Invoke-Expression $exitFunction.Extent.Text
foreach ($case in @(
    @{ result = 'STRUCTURE_CONFIRMED'; cleanup = 'DRAIN_CONFIRMED'; state = 'REPORT'; structure_confirmed = $true; expected = 0 },
    @{ result = 'STRUCTURE_CONFIRMED'; cleanup = 'DRAIN_NOT_CERTIFIED'; state = 'REPORT'; structure_confirmed = $true; expected = 1 },
    @{ result = 'STRUCTURE_CONFIRMED'; cleanup = 'DRAIN_CONFIRMED'; state = 'ERROR_UNCERTAIN'; structure_confirmed = $true; expected = 1 },
    @{ result = 'STRUCTURE_CONFIRMED'; cleanup = 'DRAIN_CONFIRMED'; state = 'REPORT'; structure_confirmed = $false; expected = 1 },
    @{ result = 'ATTACHMENT_ALREADY_CONSUMED'; cleanup = 'DRAIN_CONFIRMED'; state = 'REPORT'; structure_confirmed = $false; expected = 1 },
    @{ result = 'USB_RECEIVE_FAILURE'; cleanup = 'DRAIN_CONFIRMED'; state = 'REPORT'; structure_confirmed = $false; expected = 1 },
    @{ result = 'ERROR_UNCERTAIN_NO_RETRY'; cleanup = 'DRAIN_CONFIRMED'; state = 'ERROR_UNCERTAIN'; structure_confirmed = $false; expected = 1 },
    @{ result = 'INVALID_TS'; cleanup = 'DRAIN_CONFIRMED'; state = 'REPORT'; structure_confirmed = $false; expected = 1 },
    @{ result = 'LIMIT_REACHED'; cleanup = 'DRAIN_CONFIRMED'; state = 'REPORT'; structure_confirmed = $false; expected = 1 }
)) {
    $expected = $case.expected; $case.Remove('expected')
    if ((Get-TBS5530AutonomousExitCode ([pscustomobject]$case)) -ne $expected) { throw 'Terminal exit classification failed.' }
}
if ((Get-TBS5530AutonomousExitCode $null) -ne 1 -or (Get-TBS5530AutonomousExitCode ([pscustomobject]@{})) -ne 1) {
    throw 'Missing report must not indicate success.'
}
if (-not $text.Contains('exit $exitCode') -or -not $text.Contains('if ($null -eq $terminalReport)')) { throw 'Missing explicit terminal/deadline exit gate.' }
'PASS: cú pháp, pin serial, bounded grant/proof, physical recovery epoch, terminal exit codes, private namespace, heartbeat STOP và negative preflight; không chạy ADB.'
