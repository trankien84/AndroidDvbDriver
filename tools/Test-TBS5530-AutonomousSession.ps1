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
    'EXPERIMENTAL_DIAGNOSTIC', 'max_upload_per_attachment', 'expires_elapsed_ms', '.hosttmp', 'Exchange-Heartbeat -Stop',
    'WaitForExit(1500)', "@('-s', `$Serial)", 'ControlMirror', 'control_mirror', 'SYNCED_CLOSED',
    'exec-out', 'sha256sum', 'Không ghi đè evidence đã có.', 'ExplicitSetInterfaceAlt0', 'explicit_set_interface_alt0',
    'launch_token', 'control_plane_only', 'Test-TBS5530LaunchChallenge', 'Get-TBS5530ControlPlaneExitCode')) {
    if (-not $text.Contains($required)) { throw ('Missing guard: ' + $required) }
}
$adbCalls = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -ceq 'Invoke-PinnedAdb' }, $true))
foreach ($call in $adbCalls) {
    if ($call.Extent.Text -notmatch '-Operation [A-Z][A-Z0-9_]+') { throw 'ADB operation lacks a redacted category.' }
}
$preflightLaunch = @($adbCalls | Where-Object { $_.Extent.Text -match '-Operation PREFLIGHT_LAUNCH' })
if ($preflightLaunch.Count -ne 1 -or -not $preflightLaunch[0].Extent.Text.Contains("'--activity-single-top'") -or
    -not $preflightLaunch[0].Extent.Text.Contains("'--activity-clear-top'")) {
    throw 'Preflight relaunch must reach its own activity through a previously drained workflow.'
}
if (-not $text.Contains("'start', '--activity-clear-top', '--activity-single-top', '-n'")) {
    throw 'Session launch must reach its own existing activity through a permission-only screen.'
}
if ($text.Contains('--activity-clear-task')) { throw 'Session helper must not clear the application task.' }
$failureFunction = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Get-TBS5530AdbFailure' }, $true)
if ($null -eq $failureFunction) { throw 'Missing redacted ADB failure formatter.' }
Invoke-Expression $failureFunction.Extent.Text
$diagnosticChecks = 0
foreach ($kind in @('START_FAILED','TIMEOUT','NONZERO_EXIT')) {
    $message = Get-TBS5530AdbFailure $kind 'PERMISSION_PREFLIGHT' 'PREFLIGHT_STATUS_READ'
    if ($message -cne "FAIL-STOP: ADB $kind; stage=PERMISSION_PREFLIGHT; operation=PREFLIGHT_STATUS_READ; timeout_ms=1500; không reconnect/fallback.") {
        throw 'Redacted failure lost stage, category or unchanged deadline.'
    }
    $diagnosticChecks++
}
foreach ($case in @(
    @{ Kind = 'SECRET_PAYLOAD'; Stage = 'PERMISSION_PREFLIGHT'; Operation = 'PREFLIGHT_LAUNCH' },
    @{ Kind = 'TIMEOUT'; Stage = 'stage token=secret'; Operation = 'PREFLIGHT_LAUNCH' },
    @{ Kind = 'TIMEOUT'; Stage = 'PERMISSION_PREFLIGHT'; Operation = 'operation serial=secret' }
)) {
    $rejected = $false
    try { Get-TBS5530AdbFailure @case | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'Failure label must reject non-category text.' }
    $diagnosticChecks++
}
if ($diagnosticChecks -ne 6 -or $adbCalls.Count -lt 15) { throw 'Missing ADB diagnostic coverage.' }
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
$alt0Refusals = 0
foreach ($case in @(
    @{ Profile = 'AUTO_VERIFIED'; ReadApi = 'NATIVE_USBFS_URB_8X4096' },
    @{ Profile = 'EXPERIMENTAL_DIAGNOSTIC'; ReadApi = 'NATIVE_USBFS_URB_8X8192' },
    @{ Profile = 'EXPERIMENTAL_DIAGNOSTIC'; ReadApi = 'SYNC_NATIVE_ERRNO' },
    @{ Profile = 'EXPERIMENTAL_DIAGNOSTIC'; ReadApi = 'QUEUED_USB_REQUEST_8X4096' }
)) {
    try {
        & $path -Serial 'offline-fixture' -ExpectedModel GM1900 -ExpectedApi 30 -ExpectedBootCount 1 -GrantBoundedSession -ProofOfGuardPassed -ExplicitSetInterfaceAlt0 -AdbPath (Join-Path $PSScriptRoot 'no-such-adb.exe') @case
        throw 'Expected alt0 profile refusal before any ADB.'
    } catch { if ($_.Exception.Message -notmatch '^BLOCKED: EXPLICIT_SET_INTERFACE_ALT0') { throw }; $alt0Refusals++ }
}
try {
    & $path -Serial 'offline-fixture' -ExpectedModel GM1900 -ExpectedApi 30 -ExpectedBootCount 1 -GrantBoundedSession -ProofOfGuardPassed -ExplicitSetInterfaceAlt0 -Profile EXPERIMENTAL_DIAGNOSTIC -ReadApi NATIVE_USBFS_URB_8X4096 -AdbPath (Join-Path $PSScriptRoot 'no-such-adb.exe')
    throw 'Expected accepted alt0 scope to stop at missing ADB.'
} catch { if ($_.Exception.Message -cne 'Không tìm thấy ADB đã chọn.') { throw }; $alt0Refusals++ }
if ($alt0Refusals -ne 5) { throw 'Missing alt0 negative fixture.' }
$sonyRefusals=0
foreach($case in @(
    @{Profile='AUTO_VERIFIED';ReadApi='NATIVE_USBFS_URB_8X4096';ControlMirror=$true},
    @{Profile='EXPERIMENTAL_DIAGNOSTIC';ReadApi='SYNC_NATIVE_ERRNO';ControlMirror=$true},
    @{Profile='EXPERIMENTAL_DIAGNOSTIC';ReadApi='QUEUED_USB_REQUEST_8X4096';ControlMirror=$true},
    @{Profile='EXPERIMENTAL_DIAGNOSTIC';ReadApi='NATIVE_USBFS_URB_8X8192';ControlMirror=$true},
    @{Profile='EXPERIMENTAL_DIAGNOSTIC';ReadApi='NATIVE_USBFS_URB_8X4096'},
    @{Profile='EXPERIMENTAL_DIAGNOSTIC';ReadApi='NATIVE_USBFS_URB_8X4096';ControlMirror=$true;ExplicitSetInterfaceAlt0=$true},
    @{Profile='EXPERIMENTAL_DIAGNOSTIC';ReadApi='NATIVE_USBFS_URB_8X4096';ControlMirror=$true;ControlPlaneOnly=$true}
)) {
    try {
        & $path -Serial offline-fixture -ExpectedModel SM-X926B -ExpectedApi 36 -ExpectedBootCount 46 -GrantBoundedSession -ProofOfGuardPassed -SonyTsOutputReadback -AdbPath (Join-Path $PSScriptRoot 'no-such-adb.exe') @case
        throw 'Expected Sony scope refusal before any ADB.'
    } catch {if($_.Exception.Message -notmatch '^BLOCKED: SONY_TS_OUTPUT_READBACK'){throw};$sonyRefusals++}
}
try {
    & $path -Serial offline-fixture -ExpectedModel SM-X926B -ExpectedApi 36 -ExpectedBootCount 46 -GrantBoundedSession -ProofOfGuardPassed -SonyTsOutputReadback -ControlMirror -Profile EXPERIMENTAL_DIAGNOSTIC -ReadApi NATIVE_USBFS_URB_8X4096 -AdbPath (Join-Path $PSScriptRoot 'no-such-adb.exe')
    throw 'Expected accepted Sony scope to stop at missing ADB.'
} catch {if($_.Exception.Message -cne 'Không tìm thấy ADB đã chọn.'){throw};$sonyRefusals++}
$layoutAssignment=$ast.Find({param($n) $n -is [Management.Automation.Language.AssignmentStatementAst] -and $n.Left.Extent.Text -ceq '$layout' -and $n.Extent.Text.Contains('read81=SOURCE_SERIAL_RMW')},$true)
if($null -eq $layoutAssignment){throw 'Missing exact Sony layout'}
Invoke-Expression $layoutAssignment.Extent.Text
$javaPath=Join-Path $PSScriptRoot '..\drivers\src\debug\java\info\martinmarinov\drivers\usb\tbs5530\Tbs5530SonyTsOutputReadback.java'
$java=[IO.File]::ReadAllText([IO.Path]::GetFullPath($javaPath))
$body=[regex]::Match($java,'(?s)public static String layout\(\)\s*\{(.*?)\n    \}').Groups[1].Value
$javaLayout=([regex]::Matches($body,'"([^"]*)"') | ForEach-Object {$_.Groups[1].Value}) -join ''
if($sonyRefusals -ne 8 -or $javaLayout -ceq '' -or $layout -cne $javaLayout){throw 'Sony scope/layout coverage incomplete'}
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
if (@($guidLines | Where-Object { $_.Trim() -cnotin @('$preflightToken = [guid]::NewGuid().ToString()', '$launchToken = [guid]::NewGuid().ToString()') }).Count -ne 0 -or
    -not $text.Contains('$grant.physical_recovery_epoch = $PhysicalRecoveryEpoch')) {
    throw 'Only permission/launch correlation may generate a nonce; recovery epoch must come from the operator.'
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
'PASS: cú pháp, pin serial, bounded grant/proof, physical recovery epoch, terminal exit codes, private namespace, heartbeat STOP và negative preflight; redacted ADB diagnostics 6/6, Sony scope 8/8 và exact Java/helper layout; không chạy ADB.'
