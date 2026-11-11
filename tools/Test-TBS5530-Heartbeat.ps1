# SPDX-License-Identifier: GPL-2.0-or-later
# Offline: execute the generated shell against fake clock/files using installed Git Bash, never ADB.
[CmdletBinding()]
param([string]$BashPath = 'C:\Program Files\Git\bin\bash.exe')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not (Test-Path -LiteralPath $BashPath -PathType Leaf)) { throw 'Installed Git Bash required; no WSL fallback.' }
$source = Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Helper parse failed.' }
foreach ($name in @('New-TBS5530HeartbeatCommand','Read-TBS5530HeartbeatReply','Test-TBS5530DrainedTerminal',
    'Get-TBS5530AdbDeadline','Get-TBS5530AdbFailure','Get-TBS5530AutonomousExitCode','ConvertTo-TBS5530WindowsArgument')) {
    $function = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name }, $true)
    if ($null -eq $function) { throw ('Missing function: ' + $name) }
    Invoke-Expression $function.Extent.Text
}
$script:checks = 0
function Check([bool]$Value, [string]$Reason) {
    if (-not $Value) { throw $Reason }
    $script:checks++
}
function Denied([scriptblock]$Operation, [string]$Reason) {
    $rejected = $false
    try { & $Operation | Out-Null } catch { $rejected = $true }
    Check $rejected $Reason
}
$session = '11111111-1111-4111-8111-111111111111'
$secondSession = '22222222-2222-4222-8222-222222222222'
$directory = 'files/autonomous-v1-' + $session
$beatPath = $directory + '/heartbeat.json'
$repo = Split-Path $PSScriptRoot -Parent
$root = Join-Path $repo ('build\heartbeat-host-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $root $directory) -Force | Out-Null
$sentinel = '{"session":"' + $session + '","elapsed_ms":1000,"stop":true}'
function Invoke-FakeClock([string]$Clock, [long]$Created = 1000, [long]$Expires = 301000, [switch]$Stop, [string]$GrantText = '',
    [string]$SessionValue = $session, [switch]$OuterClockDenied) {
    $privateDirectory = 'files/autonomous-v1-' + $SessionValue
    $privateBeat = $privateDirectory + '/heartbeat.json'
    New-Item -ItemType Directory -Path (Join-Path $root $privateDirectory) -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $root 'clock.txt'), $Clock + "`n", [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $root $privateBeat), $sentinel, [Text.UTF8Encoding]::new($false))
    $clockFile = if ($OuterClockDenied) { 'no-clock.txt' } else { 'clock.txt' }
    $command = (New-TBS5530HeartbeatCommand $SessionValue $Created $Expires -Stop:$Stop -PublishGrant:([bool]$GrantText)).Replace('< /proc/uptime', ('< ' + $clockFile))
    $command = $command.Replace('exec run-as ', 'exec "$BASH" app-entry.sh ')
    [IO.File]::WriteAllText((Join-Path $root 'script.sh'),$command.Replace("`r`n", "`n") + "`n",[Text.UTF8Encoding]::new($false))
    $appEntry = @'
set -eu
[ "$#" -eq 6 ] || exit 78
[ "$1" = info.martinmarinov.dvbdriver ] && [ "$2" = sh ] && [ "$3" = -c ] || exit 78
shift 3
# Simulate an app UID denied all access to the clock. Only the outer shell may read it.
case $1 in */proc/uptime*|*clock.txt*) printf "APP_CLOCK_DENIED" >&2; exit 79;; esac
exec "$BASH" -c "$1" "$2" "$3"
'@
    [IO.File]::WriteAllText((Join-Path $root 'app-entry.sh'),$appEntry.Replace("`r`n", "`n") + "`n",[Text.UTF8Encoding]::new($false))
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $BashPath; $psi.Arguments = '--noprofile --norc script.sh'; $psi.WorkingDirectory = $root
    $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true
    $psi.RedirectStandardInput = $true; $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $psi
    try {
        if (-not $process.Start()) { throw 'Fake shell did not start.' }
        $output = $process.StandardOutput.ReadToEndAsync(); $errorText = $process.StandardError.ReadToEndAsync()
        if ($GrantText) { $process.StandardInput.Write($GrantText) }; $process.StandardInput.Close()
        if (-not $process.WaitForExit(5000)) {
            Stop-Process -Id $process.Id -Force
            throw ('Fake shell timeout; clock_fixture=' + $Clock + '; grant_fixture=' + [bool]$GrantText)
        }
        return [pscustomobject]@{ Exit = $process.ExitCode; Text = $output.GetAwaiter().GetResult();
            Beat = [IO.File]::ReadAllText((Join-Path $root $privateBeat)) }
    } finally { $process.Dispose() }
}
try {
    foreach ($case in @(
        @{ Clock = '1.00 0.00'; Expected = 1000L },
        @{ Clock = '1.0099 0.00'; Expected = 1009L },
        @{ Clock = '299.9999 0.00'; Expected = 299999L },
        @{ Clock = '1000000.12 0.00'; Expected = 1000000120L; Created = 1000000000L },
        @{ Clock = '3000000.12 0.00'; Expected = 3000000120L; Created = 3000000000L }
    )) {
        $created = if ($case.ContainsKey('Created')) { $case.Created } else { 1000L }
        $r = Invoke-FakeClock $case.Clock $created ($created + 300000)
        Check ($r.Exit -eq 0) 'Valid fake clock rejected.'
        $reply = Read-TBS5530HeartbeatReply $r.Text 1499 $created ($created + 300000)
        $beat = $r.Beat | ConvertFrom-Json
        Check ($reply.elapsed_ms -eq $case.Expected -and $beat.elapsed_ms -eq $case.Expected) 'Clock precision or large uptime changed.'
        Check (-not $reply.expired -and $beat.stop -ceq $false -and $beat.session -ceq $session) 'Session binding or live heartbeat changed.'
    }
    foreach ($clock in @('301.000 0.00','302.000 0.00')) {
        $r = Invoke-FakeClock $clock
        $reply = Read-TBS5530HeartbeatReply $r.Text 100 1000 301000
        Check ($r.Exit -eq 0 -and $reply.expired) 'Exact expiry must be terminal.'
        Check ($r.Beat -ceq $sentinel) 'Expired heartbeat must not renew or rearm the file.'
    }
    $r = Invoke-FakeClock '302.000 0.00' -Stop
    Check ($r.Exit -eq 0 -and ($r.Beat | ConvertFrom-Json).stop -ceq $true) 'STOP after expiry must remain STOP, never live.'
    foreach ($clock in @('0.999 0.00','-1.00 0.00','1 0.00','1.x 0.00','1.0.0 0.00','01.00 0.00','1.0;touch_bad 0.00',
        '1.0$(touch_injected) 0.00','1.0";touch_injected 0.00',"1.0';touch_injected 0.00",'99999999999.00 0.00','')) {
        $r = Invoke-FakeClock $clock
        Check ($r.Exit -ne 0 -and $r.Beat -ceq $sentinel) 'Invalid or rolled-back clock must not write a heartbeat.'
    }
    foreach ($case in @(
        @{ Session = '../other'; Created = 1000L; Expires = 301000L },
        @{ Session = $session; Created = -1L; Expires = 300000L },
        @{ Session = $session; Created = 1000L; Expires = 1000L },
        @{ Session = $session; Created = 1000L; Expires = 301001L },
        @{ Session = $session; Created = [long]::MaxValue; Expires = [long]::MaxValue }
    )) { Denied { New-TBS5530HeartbeatCommand @case } 'Malformed scope or widened expiry accepted.' }
    $good = '{"elapsed_ms":1200,"expired":false,"report":null,"permission":null}'
    foreach ($duration in @(-1L,1500L,2000L)) { Denied { Read-TBS5530HeartbeatReply $good $duration 1000 301000 } 'Reply accepted outside its 1500ms call budget.' }
    foreach ($bad in @('{}','broken','{"elapsed_ms":"1200","expired":false,"report":null,"permission":null}',
        '{"elapsed_ms":999,"expired":false,"report":null,"permission":null}',
        '{"elapsed_ms":301000,"expired":false,"report":null,"permission":null}',
        '{"elapsed_ms":1200,"expired":"false","report":null,"permission":null}')) {
        Denied { Read-TBS5530HeartbeatReply $bad 100 1000 301000 } 'Malformed or inconsistent heartbeat reply accepted.'
    }
    $report = [pscustomobject]@{ cleanup='DRAIN_CONFIRMED'; state='REPORT'; result='USB_RECEIVE_FAILURE'; structure_confirmed=$false; ts_smoke_pass=$false }
    $remote = $directory + '/control-ep0.jsonl'
    $readArgs = @('exec-out','run-as','info.martinmarinov.dvbdriver','cat',$remote)
    $hashArgs = @('shell','run-as','info.martinmarinov.dvbdriver','sha256sum',$remote)
    Check ((Get-TBS5530AdbDeadline 'HEARTBEAT_EXCHANGE' @('shell') $null '') -eq 1500) 'Runtime deadline changed.'
    Check ((Get-TBS5530AdbDeadline 'CONTROL_TRACE_READ' $readArgs $null '') -eq 1500) 'Missing drain proof gained export deadline.'
    Check ((Get-TBS5530AdbDeadline 'CONTROL_TRACE_READ' $readArgs $report $session) -eq 10000) 'Drained read-only export deadline missing.'
    Check ((Get-TBS5530AdbDeadline 'CONTROL_TRACE_HASH' $hashArgs $report $session) -eq 10000) 'Drained hash deadline missing.'
    foreach ($badReport in @(
        [pscustomobject]@{},
        [pscustomobject]@{cleanup='DRAIN_NOT_CERTIFIED';state='REPORT';result='FAILED'},
        [pscustomobject]@{cleanup='DRAIN_CONFIRMED';state='FRONTEND_INIT';result='FAILED'},
        [pscustomobject]@{cleanup='DRAIN_CONFIRMED';state='WAIT_REENUMERATION';result='FAILED'},
        [pscustomobject]@{cleanup='DRAIN_CONFIRMED';state='REPORT';result='FAILED';native_urb=[pscustomobject]@{outstanding=1;quarantined=$false}},
        [pscustomobject]@{cleanup='DRAIN_CONFIRMED';state='REPORT';result='FAILED';native_urb=[pscustomobject]@{outstanding=0;quarantined=$true}},
        [pscustomobject]@{cleanup='DRAIN_CONFIRMED';state='REPORT';result='FAILED';native_urb=[pscustomobject]@{outstanding='0';quarantined=$false}}
    )) { Denied { Get-TBS5530AdbDeadline 'CONTROL_TRACE_READ' $readArgs $badReport $session } 'Unverified terminal/drain gained long export deadline.' }
    Denied { Get-TBS5530AdbDeadline 'HEARTBEAT_EXCHANGE' $readArgs $report $session } 'Runtime operation gained long deadline.'
    Denied { Get-TBS5530AdbDeadline 'CONTROL_TRACE_READ' @('shell','run-as','info.martinmarinov.dvbdriver','cat',$remote) $report $session } 'Export argument drift accepted.'
    Denied { Get-TBS5530AdbDeadline 'CONTROL_TRACE_READ' @('exec-out','run-as','info.martinmarinov.dvbdriver','cat',($remote + ';write')) $report $session } 'Export shell injection accepted.'
    Denied { Get-TBS5530AdbDeadline 'CONTROL_TRACE_READ' $readArgs $report 'other-session' } 'Export session drift accepted.'
    Check ((Get-TBS5530AutonomousExitCode $report) -eq 1) 'Drained failed receive became PASS.'
    $message = Get-TBS5530AdbFailure 'TIMEOUT' 'EVIDENCE_EXPORT' 'CONTROL_TRACE_READ' 10000
    Check ($message.Contains('timeout_ms=10000') -and -not $message.Contains($session)) 'Export error lost its redacted actual deadline.'
    $exchange = $ast.Find({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq 'Exchange-Heartbeat'},$true)
    $exchangeCalls = @($exchange.FindAll({param($n) $n -is [Management.Automation.Language.CommandAst] -and $n.GetCommandName() -ceq 'Invoke-PinnedAdb'},$true))
    Check ($exchangeCalls.Count -eq 1 -and -not $exchange.Extent.Text.Contains('Get-Elapsed') -and -not $exchange.Extent.Text.Contains('InputText')) 'Heartbeat still uses a host-sampled timestamp or two ADB calls.'
    $main = [IO.File]::ReadAllText($source)
    Check ($main.Contains("@('-s', `$Serial) + `$DeviceArguments") -and $main.Contains('WaitForExit(1500)') -and $main.Contains('WaitForExit(10000)')) 'Pinned serial or per-call deadline lost.'
    Check ($main.Contains('while ($watch.Elapsed.TotalSeconds -lt $DurationSeconds)') -and $main.Contains('300000') -and $main.Contains('Exchange-Heartbeat -Stop')) 'Host deadline or STOP missing.'
    $command = New-TBS5530HeartbeatCommand $session 1000 301000
    Check ($command.Contains('read -r uptime idle < /proc/uptime') -and $command.Contains('exec run-as info.martinmarinov.dvbdriver sh -c') -and
        $command.Contains('heartbeat "$uptime"') -and $command.Contains('uptime=$1') -and $command.Contains('.hosttmp')) 'Shell UID clock read or positional argument broken.'
    Check ([regex]::Matches($command,'/proc/uptime').Count -eq 1 -and $command.IndexOf('/proc/uptime') -lt $command.IndexOf('exec run-as')) 'Clock must be read only before run-as.'
    # The old two-RTT sequence can stamp a beat 2400ms old even when each call is below 1500ms.
    Check ((1200 + 1200) -ge 2000 -and 1200 -lt 1500) 'Old stale-window model invalid.'
    $r = Invoke-FakeClock '3.400 0.00'
    Check (($r.Beat | ConvertFrom-Json).elapsed_ms -eq 3400) 'New heartbeat reused the old host sample of 1000ms.'
    $terminalNative = [pscustomobject]@{cleanup='DRAIN_CONFIRMED';state='ERROR_UNCERTAIN';result='FAILED';native_urb=[pscustomobject]@{outstanding=0L;quarantined=$false}}
    Check ((Get-TBS5530AdbDeadline 'CONTROL_TRACE_READ' $readArgs $terminalNative $session) -eq 10000) 'Drained terminal error cannot export read-only evidence.'
    Denied { New-TBS5530HeartbeatCommand $session 1000 301000 -Stop -PublishGrant } 'STOP must not publish a new grant.'
    $grantText = '{"session":"' + $session + '","expires_elapsed_ms":301000,"retry":0}'
    $r = Invoke-FakeClock '4.500 0.00' -GrantText $grantText
    Check ($r.Exit -eq 0 -and [IO.File]::ReadAllText((Join-Path $root ($directory + '/grant.json'))) -ceq $grantText) 'Grant must remain byte-exact when published with heartbeat.'
    Check (($r.Beat | ConvertFrom-Json).elapsed_ms -eq 4500) 'Initial grant heartbeat still uses the host sample.'
    $r = Invoke-FakeClock '5.000 0.00' -GrantText '{"different":true}'
    Check ($r.Exit -ne 0 -and [IO.File]::ReadAllText((Join-Path $root ($directory + '/grant.json'))) -ceq $grantText) 'Existing grant must never be replaced or rearmed.'
    Remove-Item -LiteralPath (Join-Path $root ($directory + '/grant.json'))
    $r = Invoke-FakeClock '301.000 0.00' -GrantText $grantText
    Check ($r.Exit -eq 0 -and -not (Test-Path (Join-Path $root ($directory + '/grant.json'))) -and $r.Beat -ceq $sentinel) 'Expired initial scope published a live grant or renewed heartbeat.'
    $orphan = Join-Path $root ($directory + '/grant.json.hosttmp')
    [IO.File]::WriteAllText($orphan,'orphan-unpublished',[Text.UTF8Encoding]::new($false))
    $r = Invoke-FakeClock '6.000 0.00' -GrantText $grantText
    Check ($r.Exit -ne 0 -and $r.Beat -ceq $sentinel -and [IO.File]::ReadAllText($orphan) -ceq 'orphan-unpublished') 'Orphan grant must block reuse of the old challenge without overwriting it.'
    $secondGrant = $grantText.Replace($session,$secondSession)
    $r = Invoke-FakeClock '6.500 0.00' -SessionValue $secondSession -GrantText $secondGrant
    Check ($r.Exit -eq 0 -and ($r.Beat | ConvertFrom-Json).session -ceq $secondSession -and
        [IO.File]::ReadAllText((Join-Path $root ('files/autonomous-v1-' + $secondSession + '/grant.json'))) -ceq $secondGrant -and
        [IO.File]::ReadAllText($orphan) -ceq 'orphan-unpublished') 'A new challenge must not touch the old unpublished grant.'
    $r = Invoke-FakeClock '7.000 0.00' -OuterClockDenied
    Check ($r.Exit -ne 0 -and $r.Beat -ceq $sentinel) 'Denied outer clock read must stop before app-private publication.'
    $reader = Join-Path $root 'argument-reader.ps1'
    [IO.File]::WriteAllText($reader,'param([string]$Value); [Console]::Write([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Value)))',[Text.UTF8Encoding]::new($false))
    foreach ($argument in @($command, 'text "quoted" and backslash\', 'literal \\" and ''apostrophe''; no execution')) {
        $psi = [Diagnostics.ProcessStartInfo]::new(); $psi.FileName = (Get-Command powershell.exe -ErrorAction Stop).Source
        $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true; $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
        $psi.Arguments = (@('-NoProfile','-File',$reader,$argument) | ForEach-Object { ConvertTo-TBS5530WindowsArgument $_ }) -join ' '
        $process = [Diagnostics.Process]::Start($psi)
        try {
            $output = $process.StandardOutput.ReadToEndAsync(); $errorText = $process.StandardError.ReadToEndAsync()
            if (-not $process.WaitForExit(10000)) { Stop-Process -Id $process.Id -Force; throw 'Argument round-trip test timed out.' }
            $decoded = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($output.GetAwaiter().GetResult()))
            Check ($process.ExitCode -eq 0 -and $decoded -ceq $argument) 'Windows argument quoting changed the exact remote command.'
        } finally { $process.Dispose() }
    }
    "PASS: $script:checks checks; outer-shell clock/app-clock denied, positional quoting, expiry/STOP, no-rearm orphan, read-only export; no ADB."
} finally {
    $fixtureDirectories = @($directory,('files/autonomous-v1-' + $secondSession))
    $fixtureFiles = @('clock.txt','script.sh','app-entry.sh','argument-reader.ps1')
    foreach ($private in $fixtureDirectories) {
        foreach ($name in @('heartbeat.json','heartbeat.json.hosttmp','report.json','permission.json','grant.json','grant.json.hosttmp')) { $fixtureFiles += $private + '/' + $name }
    }
    foreach ($relative in $fixtureFiles) {
        $file = Join-Path $root $relative
        if (Test-Path -LiteralPath $file -PathType Leaf) { Remove-Item -LiteralPath $file -Force }
    }
    foreach ($relative in @($fixtureDirectories + @('files',''))) {
        $dir = if ($relative) { Join-Path $root $relative } else { $root }
        if (Test-Path -LiteralPath $dir -PathType Container) {
            for ($attempt = 0; ; $attempt++) {
                try { [IO.Directory]::Delete($dir); break }
                catch [IO.IOException] {
                    if ($attempt -ge 9) { throw }
                    Start-Sleep -Milliseconds 50
                }
            }
        }
    }
}
