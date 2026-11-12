# SPDX-License-Identifier: GPL-2.0-or-later
# Offline: execute the B2 post-drain export with a fake ADB function only.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$source = Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Helper parse failed.' }
foreach ($name in @('Test-TBS5530DrainedTerminal', 'Get-TBS5530AdbDeadline', 'Get-TBS5530Sha256Token', 'Export-TBS5530HandoffEvents')) {
    $function = $ast.Find({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name}, $true)
    if ($null -eq $function) { throw ('Missing function: ' + $name) }
    Invoke-Expression $function.Extent.Text
}
$script:checks = 0
function Check([bool]$Value, [string]$Reason) {
    if (-not $Value) { throw $Reason }
    $script:checks++
}
function Denied([scriptblock]$Operation) {
    $rejected = $false
    try { & $Operation | Out-Null } catch { $rejected = $true }
    Check $rejected 'Expected export refusal.'
}
$session = '11111111-1111-4111-8111-111111111111'
$remote = 'files/autonomous-v1-' + $session + '/handoff-events.jsonl'
$journal = '{"event":"ui.post","elapsed_ns":42,"selected":false}' + "`n"
$sha = [Security.Cryptography.SHA256]::Create()
try { $journalHash = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($journal)))).Replace('-', '').ToLowerInvariant() }
finally { $sha.Dispose() }
$script:presence = 'PRESENT'
$script:remoteHash = $journalHash
$script:calls = [Collections.ArrayList]::new()
function Invoke-PinnedAdb([string[]]$DeviceArguments, [string]$InputText = '', [bool]$PreserveOutput = $false,
    [string]$Operation, [object]$DrainedReport = $null) {
    if ($InputText) { throw 'Fake export received write payload.' }
    $deadline = Get-TBS5530AdbDeadline $Operation $DeviceArguments $DrainedReport $session
    [void]$script:calls.Add($Operation)
    switch -CaseSensitive ($Operation) {
        'HANDOFF_EVENTS_EXISTS' {
            $expected = @('shell','run-as','info.martinmarinov.dvbdriver','sh','-c',("'if [ -f " + $remote + ' ]; then printf PRESENT; fi' + "'"))
            Check (($DeviceArguments -join "`n") -ceq ($expected -join "`n") -and $deadline -eq 1500) 'Presence probe drifted from exact read-only path or runtime budget.'
            return $script:presence
        }
        'HANDOFF_EVENTS_READ' {
            Check ($PreserveOutput -and $deadline -eq 10000) 'Journal read must preserve exact text after drain.'
            return $journal
        }
        'HANDOFF_EVENTS_HASH' {
            Check ($deadline -eq 10000) 'Journal hash lost the existing drained-export budget.'
            return $script:remoteHash + '  ' + $remote
        }
        default { throw 'Unexpected fake ADB operation.' }
    }
}
$exportCalls = @($ast.FindAll({param($n) $n -is [Management.Automation.Language.CommandAst] -and $n.GetCommandName() -ceq 'Export-TBS5530HandoffEvents'}, $true))
Check ($exportCalls.Count -eq 1) 'Missing unique main-path handoff evidence export.'
$gate = $exportCalls[0].Parent
while ($null -ne $gate -and $gate -isnot [Management.Automation.Language.IfStatementAst]) { $gate = $gate.Parent }
Check ($null -ne $gate -and $gate.Clauses[0].Item1.Extent.Text -ceq '$b2Reload -and (Test-TBS5530DrainedTerminal $terminalReport)') 'Export must depend only on B2 and verified drain, not reload completion or EP0 mirror.'
$main = $ast.Extent.Text
Check ($gate.Extent.StartOffset -lt $main.IndexOf("if (`$terminalReport.control_mirror -cne 'SYNCED_CLOSED')") -and
    $gate.Extent.StartOffset -lt $main.IndexOf('$historyMatches = $terminalReport.attachment_history_sha256')) 'Journal can be lost before mirror/history failure.'
$root = Join-Path (Split-Path $PSScriptRoot -Parent) ('build\handoff-evidence-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
try {
    $challenge = [pscustomobject]@{session=$session}
    $OutputDirectory = $root
    $b2Reload = $true
    $terminalReport = [pscustomobject]@{cleanup='DRAIN_CONFIRMED';terminal=$true;cleanup_path='NO_RESOURCES_ACQUIRED';
        state='WAIT_REENUMERATION';result='STOP_BEFORE_RELOAD';firmware=$null;native_urb=$null}
    Invoke-Expression $gate.Extent.Text
    $local = Join-Path $root ('b2-' + $session + '-handoff-events.jsonl')
    Check ([IO.File]::ReadAllText($local) -ceq $journal -and (Get-FileHash -LiteralPath $local).Hash.ToLowerInvariant() -ceq $journalHash) 'Early terminal without fx2_reload lost exact journal bytes/hash.'
    Check (($script:calls -join ',') -ceq 'HANDOFF_EVENTS_EXISTS,HANDOFF_EVENTS_READ,HANDOFF_EVENTS_HASH') 'Early-stop export skipped an evidence operation.'
    Denied { Export-TBS5530HandoffEvents $session $terminalReport $root }
    Check ([IO.File]::ReadAllText($local) -ceq $journal) 'Existing local evidence was replaced.'
    Remove-Item -LiteralPath $local
    $script:calls.Clear()
    $terminalReport.cleanup_path = 'RUNTIME_DRAIN_CONFIRMED'
    $terminalReport.firmware = [pscustomobject]@{completed=132}
    $terminalReport.result = 'FIRST_FAILURE_BEFORE_RELOAD'
    Invoke-Expression $gate.Extent.Text
    Check ([IO.File]::ReadAllText($local) -ceq $journal -and $script:calls.Count -eq 3) 'Drained bootstrap/handoff failure before reload lost its journal.'
    Remove-Item -LiteralPath $local
    $script:calls.Clear(); $script:presence = ''
    Invoke-Expression $gate.Extent.Text
    Check ($script:calls.Count -eq 1 -and -not (Test-Path -LiteralPath $local)) 'Absent optional journal should not create evidence or fail the run.'
    $script:calls.Clear(); $script:presence = 'PRESENT'; $b2Reload = $false
    Invoke-Expression $gate.Extent.Text
    Check ($script:calls.Count -eq 0) 'Non-B2 path accessed handoff evidence.'
    $b2Reload = $true; $terminalReport.cleanup = 'DRAIN_NOT_CERTIFIED'
    Invoke-Expression $gate.Extent.Text
    Check ($script:calls.Count -eq 0) 'Undrained main path accessed the journal.'
    Denied { Export-TBS5530HandoffEvents $session $terminalReport $root }
    Check ($script:calls.Count -eq 0) 'Direct export accessed ADB before drain.'
    $terminalReport.cleanup = 'DRAIN_CONFIRMED'
    Denied { Export-TBS5530HandoffEvents '../other' $terminalReport $root }
    Check ($script:calls.Count -eq 0) 'Malformed session reached ADB.'
    $script:presence = 'unexpected'
    Denied { Export-TBS5530HandoffEvents $session $terminalReport $root }
    Check (-not (Test-Path -LiteralPath $local)) 'Unexpected presence response created evidence.'
    $script:presence = 'PRESENT'; $script:remoteHash = 'b' * 64
    Denied { Export-TBS5530HandoffEvents $session $terminalReport $root }
    Check ([IO.File]::ReadAllText($local) -ceq $journal) 'Hash mismatch discarded collected evidence.'
    $readArgs = @('exec-out','run-as','info.martinmarinov.dvbdriver','cat',$remote)
    $hashArgs = @('shell','run-as','info.martinmarinov.dvbdriver','sha256sum',$remote)
    Check ((Get-TBS5530AdbDeadline 'HANDOFF_EVENTS_READ' $readArgs $terminalReport $session) -eq 10000) 'Exact drained journal read refused.'
    Check ((Get-TBS5530AdbDeadline 'HANDOFF_EVENTS_HASH' $hashArgs $terminalReport $session) -eq 10000) 'Exact drained journal hash refused.'
    foreach ($badPath in @($remote.Replace($session,'22222222-2222-4222-8222-222222222222'),($remote+';write'),$remote.Replace('handoff-events.jsonl','grant.json'))) {
        Denied { Get-TBS5530AdbDeadline 'HANDOFF_EVENTS_READ' @('exec-out','run-as','info.martinmarinov.dvbdriver','cat',$badPath) $terminalReport $session }
    }
    Denied { Get-TBS5530AdbDeadline 'HANDOFF_EVENTS_READ' $hashArgs $terminalReport $session }
    $terminalReport.native_urb = [pscustomobject]@{outstanding=1;quarantined=$false}
    Denied { Get-TBS5530AdbDeadline 'HANDOFF_EVENTS_READ' $readArgs $terminalReport $session }
} finally {
    $local = Join-Path $root ('b2-' + $session + '-handoff-events.jsonl')
    if (Test-Path -LiteralPath $local) { Remove-Item -LiteralPath $local }
    Remove-Item -LiteralPath $root
}
Write-Output "PASS $script:checks handoff evidence checks; optional, exact path/hash, early-stop post-drain, fake ADB only."
