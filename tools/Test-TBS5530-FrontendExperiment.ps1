# SPDX-License-Identifier: GPL-2.0-or-later
# Offline negative preflight only: every invocation must refuse before ADB discovery.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Helper parse failed' }
$text = [IO.File]::ReadAllText($path)
$checks = 0
foreach ($literal in @('frontend_experiment', 'ExpectedFrontendGoldenSha256', 'MANUAL_PLP0_K36',
    '$challenge.frontend_experiment -cne $FrontendExperiment',
    '$challenge.golden_sha256 -cne $ExpectedFrontendGoldenSha256',
    '$terminalReport.frontend_experiment -cne $FrontendExperiment',
    '$challenge.attachment_history_sha256 -cne $ExpectedAttachmentHistorySha256',
    '$observedHistory -cne $ExpectedAttachmentHistorySha256',
    '$terminalReport.attachment_history_sha256 -ceq $observedHistory', 'HOST_READ_AFTER_DRAIN_NOT_AUTOMATIC_APPROVAL_FOR_NEXT_RUN')) {
    if (-not $text.Contains($literal)) { throw "Missing frontend guard: $literal" }; $checks++
}
$base = @{ Serial='offline-fixture'; ExpectedModel='SM-X926B'; ExpectedApi=36; ExpectedBootCount=46;
    GrantBoundedSession=$true; ProofOfGuardPassed=$true; FrontendExperiment='MANUAL_PLP0_K36';
    Profile='EXPERIMENTAL_DIAGNOSTIC'; ReadApi='NATIVE_USBFS_URB_8X4096'; ControlMirror=$true;
    ExpectedFrontendGoldenSha256=('a' * 64); ExpectedAppApkSha256=('b' * 64); ExpectedAttachmentHistorySha256=('c' * 64) }
foreach ($mutation in @(
    @{Profile='AUTO_VERIFIED'}, @{ReadApi='SYNC_NATIVE_ERRNO'}, @{ReadApi='NATIVE_USBFS_URB_8X8192'},
    @{ReadApi='QUEUED_USB_REQUEST_8X4096'}, @{ControlMirror=$false}, @{ExplicitSetInterfaceAlt0=$true},
    @{SonyTsOutputReadback=$true}, @{ControlPlaneOnly=$true}, @{ExpectedFrontendGoldenSha256=$null}, @{ExpectedAppApkSha256=$null},
    @{ExpectedAttachmentHistorySha256=$null}
)) {
    $argsCopy = $base.Clone()
    foreach ($key in $mutation.Keys) {
        if ($null -eq $mutation[$key]) { $argsCopy.Remove($key) } else { $argsCopy[$key] = $mutation[$key] }
    }
    $refused = $false
    try { & $path @argsCopy }
    catch { if ($_.Exception.Message -notlike 'BLOCKED: MANUAL_PLP0_K36*') { throw }; $refused=$true }
    if (-not $refused) { throw 'Combined or unpinned frontend experiment reached ADB' }; $checks++
}
$parameter = $ast.ParamBlock.Parameters | Where-Object { $_.Name.VariablePath.UserPath -ceq 'FrontendExperiment' }
if ($parameter.DefaultValue.Value -cne 'BASELINE_AUTO' -or $parameter.Extent.Text.Contains('TS_DRIVE_55_55')) {
    throw 'Default changed or unsafe drive experiment became launchable'
}; $checks++
$traceExport = $text.IndexOf('$sourceHash = Get-TBS5530Sha256Token')
$finalFence = $text.LastIndexOf('$historyMatches = $terminalReport.attachment_history_sha256')
if ($traceExport -lt 0 -or $finalFence -le $traceExport) { throw 'Final fence must preserve drained EP0 evidence before any mismatch refusal' }; $checks++
Write-Output "PASS $checks frontend experiment guards; no ADB, USB, firmware or live session."
