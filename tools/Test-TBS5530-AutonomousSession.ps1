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
    'WaitForExit(1500)', "@('-s', `$Serial)", 'ControlMirror', 'control_mirror', 'SYNCED_CLOSED',
    'exec-out', 'sha256sum', 'Không ghi đè evidence đã có.')) {
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
foreach ($api in @('QUEUED_USB_REQUEST_8X4096', 'NATIVE_USBFS_URB_8X4096', 'NATIVE_USBFS_URB_8X8192')) {
    try {
        & $path -Serial 'offline-fixture' -ExpectedModel GM1900 -ExpectedApi 30 -ExpectedBootCount 1 -GrantBoundedSession -ProofOfGuardPassed -ReadApi $api
        throw 'Expected experimental-profile refusal.'
    } catch { if ($_.Exception.Message -cne 'BLOCKED: diagnostic receive chỉ thuộc EXPERIMENTAL_DIAGNOSTIC.') { throw } }
}
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
$guidLines = @($text -split "`n" | Where-Object { $_ -match 'NewGuid' })
if (@($guidLines | Where-Object { $_.Trim() -cne '$preflightToken = [guid]::NewGuid().ToString()' }).Count -ne 0 -or
    -not $text.Contains('$grant.physical_recovery_epoch = $PhysicalRecoveryEpoch')) {
    throw 'Only permission correlation may generate a nonce; recovery epoch must come from the operator.'
}
$hashFunction = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Get-TBS5530Sha256Token' }, $true)
if ($null -eq $hashFunction) { throw 'Missing Android hash parser.' }
Invoke-Expression $hashFunction.Extent.Text
$expectedHash = '0859647e1aa77eb3a6f64ff40a092b7ec1b116f7497c84df08a43e44d1c02f4c'
if ((Get-TBS5530Sha256Token ($expectedHash + '  files/run/control-ep0.jsonl' + "`n")) -cne $expectedHash) { throw 'SHA-256 token lost bytes.' }
try { Get-TBS5530Sha256Token '0'; throw 'Expected malformed hash refusal.' }
catch { if ($_.Exception.Message -cne 'Invalid Android SHA-256 response.') { throw } }
$exitFunction = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Get-TBS5530AutonomousExitCode' }, $true)
if ($null -eq $exitFunction) { throw 'Missing terminal exit classifier.' }
Invoke-Expression $exitFunction.Extent.Text
foreach ($case in @(
    @{ result = 'STRUCTURE_CONFIRMED'; cleanup = 'DRAIN_CONFIRMED'; state = 'REPORT'; structure_confirmed = $true; ts_smoke_pass = $true; expected = 0 },
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
$structureOnly = [pscustomobject]@{ result='STRUCTURE_CONFIRMED'; cleanup='DRAIN_CONFIRMED'; state='REPORT'; structure_confirmed=$true; ts_smoke_pass=$false }
if ((Get-TBS5530AutonomousExitCode $structureOnly) -ne 1) { throw 'Structure alone must not pass the quality gate.' }
if (-not $text.Contains('exit $exitCode') -or -not $text.Contains('if ($null -eq $terminalReport)')) { throw 'Missing explicit terminal/deadline exit gate.' }
'PASS: cú pháp, pin serial, bounded grant/proof, physical recovery epoch, terminal exit codes, private namespace, heartbeat STOP và negative preflight; không chạy ADB.'
