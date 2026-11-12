# SPDX-License-Identifier: GPL-2.0-or-later
# Offline negative fixtures only. Every invocation must stop before ADB lookup/execution.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$helper = Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($helper, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'B2 helper parse failed.' }
$hash = 'a' * 64
$base = @{
    Serial='offline-fixture'; ExpectedModel='SM-X926B'; ExpectedApi=36; ExpectedBootCount=1
    FrontendExperiment='B2_RELOAD_AFTER_SET_CONFIG'; Profile='EXPERIMENTAL_DIAGNOSTIC'; ReadApi='NATIVE_USBFS_URB_8X4096'
    GrantBoundedSession=$true; ProofOfGuardPassed=$true; ControlMirror=$true
    PhysicalRecoveryEpoch='44444444-4444-4444-8444-444444444444'; PhysicalRecoveryConfirmed=$true
    FirmwarePath='offline-no-firmware.bin'; ExpectedFrontendGoldenSha256=$hash; ExpectedAppApkSha256=$hash
    ExpectedAttachmentHistorySha256=$hash; ExpectedReloadPlanSha256=$hash; ExpectedReloadStoreSha256=$hash
    ExpectedReloadAnchorSha256=$hash; ExpectedHelperSha256=$hash
    AdbPath=(Join-Path $PSScriptRoot 'no-such-adb.exe')
}
$checks = 0
foreach ($change in @(
    @{ExpectedModel='GM1900'}, @{ExpectedApi=30}, @{ReadApi='NATIVE_USBFS_URB_8X8192'}, @{Profile='AUTO_VERIFIED'},
    @{ControlMirror=$false}, @{ExplicitSetInterfaceAlt0=$true}, @{SonyTsOutputReadback=$true}, @{ControlPlaneOnly=$true},
    @{ExpectedReloadStoreSha256=$null}, @{ExpectedReloadPlanSha256=$null}, @{ExpectedReloadAnchorSha256=$null},
    @{ExpectedHelperSha256=$null}, @{ExpectedAppApkSha256=$null}, @{ExpectedAttachmentHistorySha256=$null},
    @{PhysicalRecoveryEpoch=$null}, @{PhysicalRecoveryConfirmed=$false}, @{FirmwarePath=$null}
)) {
    $case = $base.Clone(); foreach ($key in $change.Keys) { if ($null -eq $change[$key]) { $case.Remove($key) } else { $case[$key]=$change[$key] } }
    try { & $helper @case; throw 'Expected B2 scope refusal before any ADB.' }
    catch { if ($_.Exception.Message -notmatch '^BLOCKED: B2 cần exact') { throw } }
    $checks++
}
try { & $helper @base; throw 'Expected helper hash mismatch refusal.' }
catch { if ($_.Exception.Message -notmatch '^BLOCKED: B2 helper khác hash') { throw } }
$checks++
$base.ExpectedHelperSha256=(Get-FileHash -LiteralPath $helper -Algorithm SHA256).Hash.ToLowerInvariant()
try { & $helper @base; throw 'Expected exact scope to stop at missing ADB.' }
catch { if ($_.Exception.Message -cne 'Không tìm thấy ADB đã chọn.') { throw } }
$checks++
foreach ($field in @('fx2_reload_option','fx2_reload_plan_sha256','fx2_reload_store_sha256','fx2_reload_anchor_sha256',
    'SAME_SESSION_ORIGINAL132_APP_BOUNDARIES_DRAIN_REQUIRED','RELOAD_STORE_HASH','RELOAD_ANCHOR_HASH')) {
    if (-not $ast.Extent.Text.Contains($field)) { throw ('Missing B2 pin: '+$field) }
}
Write-Output "PASS: B2 negative scope/helper hash fixtures $checks/$checks; no ADB or store writes."
