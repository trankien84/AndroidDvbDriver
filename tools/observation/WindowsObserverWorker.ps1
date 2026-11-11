#Requires -Version 7.5
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$PermitPath,
    [Parameter(Mandatory)][string]$PermitSha256,
    [Parameter(Mandatory)][string]$Directory,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{32}$')][string]$Reservation
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$clock = [Diagnostics.Stopwatch]::StartNew()
$started = [DateTimeOffset]::UtcNow
$previousUtc = $started
$protocol = 'TBS5530-WINDOWS-OBSERVER/v1'
$permit = $null; $job = $null; $child = $null; $pump = $null; $pulse = $null; $lease = $null
$permitLease = $null; $binaryLease = $null; $stderrDrain = $null
$reason = 'OBSERVER_START_FAILED'; $cleanup = $true; $ready = $false
$headerValid = $false; $captureBytes = 0L; $captureSha256 = $null; $maxHealthGap = 0L
$seen = [Collections.Generic.HashSet[int]]::new()
$runId = 'unverified'; $layer = 'UNVERIFIED'; $trustedDirectory = $false; $failureType = $null; $failureLine = $null; $failureHResult = $null
$module = Import-Module (Join-Path $PSScriptRoot 'WindowsObserver.psm1') -Force -PassThru

function Write-ObserverJson {
    param([string]$Name, $Value, [bool]$CreateNew=$false)
    [Tbs5530Observation.SafePaths]::WriteJson((Join-Path $Directory $Name), ($Value | ConvertTo-Json -Depth 10 -Compress), $CreateNew)
}

function New-ObserverHealth {
    param([bool]$Alive, [string]$State, [string]$Reason)
    $ids = @($PID)
    if ($null -ne $job) {
        foreach ($childId in $job.ProcessIds()) { [void]$seen.Add($childId); $ids += $childId }
    }
    return [ordered]@{
        protocol=$protocol; run_id=$runId; permit_sha256=$PermitSha256.ToLowerInvariant()
        state=$State; utc=[DateTimeOffset]::UtcNow.ToString('o'); elapsed_ms=$clock.ElapsedMilliseconds
        alive=$Alive; ready=($ready -and $Alive); capture_bytes=$(if ($null -ne $pump) { $pump.Bytes } else { $captureBytes })
        worker_pid=$PID; recorder_pid=$(if ($null -ne $child) { $child.Id } else { $null })
        own_pids=$ids; reason=$Reason; evidence_layer=$layer; measurement_valid=$false
        cadence_target_ms=[Tbs5530Observation.HealthPulse]::CadenceTargetMilliseconds
        freshness_limit_ms=[Tbs5530Observation.HealthPulse]::FreshnessLimitMilliseconds
        cadence_misses=$(if ($null -ne $pulse) { $pulse.CadenceMisses } else { 0L })
        max_health_gap_ms=$(if ($null -ne $pulse) { $pulse.MaxGapMilliseconds } else { 0L })
        health_monotonic_ms=[Environment]::TickCount64
        reenumeration_coverage=$false; tuner_authorized=$false; ts_classification='NOT_EVALUATED'
    }
}

try {
    & $module { Initialize-TbsObserverTypes }
    $Directory = [Tbs5530Observation.SafePaths]::Check($Directory)
    $lease = [Tbs5530Observation.SafePaths]::LockDirectories($Directory)
    $ownerPath = [Tbs5530Observation.SafePaths]::Check((Join-Path $Directory 'reservation.json'))
    $owner = [IO.File]::ReadAllText($ownerPath) | ConvertFrom-Json
    if ($owner.protocol -cne $protocol -or $owner.reservation -cne $Reservation -or $owner.permit_sha256 -ine $PermitSha256) { throw 'INVALID_OUTPUT_RESERVATION' }
    if ($owner.fixture -isnot [bool]) { throw 'INVALID_OUTPUT_RESERVATION' }
    $layer = if ($owner.fixture) { 'SYNTHETIC_FIXTURE' } else { 'UNVERIFIED' }
    $runId = $owner.run_id
    foreach ($name in @('capture.pcap','ready.json','health.json','terminal.json')) {
        $reservedFile = [Tbs5530Observation.SafePaths]::Check((Join-Path $Directory $name))
        if ([IO.File]::Exists($reservedFile) -or [IO.Directory]::Exists($reservedFile)) { throw 'OUTPUT_ALREADY_CONSUMED' }
    }
    $trustedDirectory = $true
    Write-ObserverJson 'health.json' (New-ObserverHealth $false 'starting' 'VALIDATING_PERMIT') $true
    $permitLease = [Tbs5530Observation.SafePaths]::LockDirectories([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($PermitPath)))
    $permit = & $module { param($Path,$Sha) Read-TbsObserverPermit $Path $Sha } $PermitPath $PermitSha256
    if ($permit.output_directory -cne $Directory -or $permit.run_id -cne $runId -or $permit.fixture -ne $owner.fixture) { throw 'PERMIT_OUTPUT_BINDING_CHANGED' }
    $layer = if ($permit.fixture) { 'SYNTHETIC_FIXTURE' } else { 'USBPCAP_HOST_URB' }
    if (-not $permit.fixture) {
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        try { $administrator = [Security.Principal.WindowsPrincipal]::new($identity).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator) } finally { $identity.Dispose() }
        if (-not $administrator) { throw 'ELEVATION_REQUIRED_NO_UAC_AUTOMATION' }
        $binaryLease = [IO.File]::Open($permit.usbpcap_path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
        $help = & $module { param($Path) Invoke-TbsObserverProbe $Path @('--help') } $permit.usbpcap_path
        if ($help -notmatch '--devices' -or $help -notmatch '--capture-from-all-devices' -or $help -notmatch '--bufferlen') { throw 'LIVE_USBPCAP_CONTRACT_UNVERIFIED' }
        if ($permit.capture_mode -ceq 'NewDevices') {
            $roots = @(Get-TbsObservationUsbRoots -UsbPcapPath $permit.usbpcap_path | Where-Object { $_.capture_device -ceq $permit.capture_device -and $_.root_path -ceq $permit.root_path })
            if ($roots.Count -ne 1) { throw 'LIVE_SELECTED_ROOT_NOT_DISCOVERED' }
        } else {
            $targets = @(Get-TbsObservationUsbTargets -UsbPcapPath $permit.usbpcap_path | Where-Object { $_.capture_device -ceq $permit.capture_device -and $_.device_address -eq $permit.device_address })
            if ($targets.Count -ne 1) { throw 'LIVE_TARGET_NOT_UNIQUELY_DISCOVERED' }
        }
    }
    [void](& $module { param($Path,$Sha) Read-TbsObserverPermit $Path $Sha } $PermitPath $PermitSha256)
    $beforeSpawnUtc = [DateTimeOffset]::UtcNow
    if ($beforeSpawnUtc -lt $previousUtc -or $beforeSpawnUtc -lt $started.AddMilliseconds($clock.ElapsedMilliseconds - 250)) { throw 'UTC_CLOCK_ROLLBACK' }
    $previousUtc = $beforeSpawnUtc
    if ([IO.File]::Exists([Tbs5530Observation.SafePaths]::Check((Join-Path $Directory 'STOP')))) { throw 'STOP_REQUESTED' }
    $remainingMs = [int]($permit.maximum_ms - $clock.ElapsedMilliseconds)
    if ($remainingMs -le 0) { throw 'TIME_LIMIT' }
    $pump = [Tbs5530Observation.CapturePump]::new((Join-Path $Directory 'capture.pcap'), [long]$permit.maximum_bytes, [int]$permit.snaplen)
    $job = [Tbs5530Observation.RecorderJob]::new($remainingMs)
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true; $info.RedirectStandardInput = $true
    if ($permit.fixture) {
        $fixture = [Tbs5530Observation.SafePaths]::Check((Join-Path $PSScriptRoot 'fixtures\WindowsFixtureEmitter.ps1'))
        if (-not [IO.File]::Exists($fixture)) { throw 'FIXTURE_EMITTER_MISSING' }
        $info.FileName = Join-Path $PSHOME 'pwsh.exe'
        foreach ($argument in @('-NoLogo','-NoProfile','-NonInteractive','-File',$fixture,'-Scenario',$permit.fixture_case)) { $info.ArgumentList.Add($argument) }
    } else {
        $info.FileName = $permit.usbpcap_path
        $captureArguments = & $module { param($Value) Get-TbsObserverCaptureArguments $Value } $permit
        foreach ($argument in $captureArguments) { $info.ArgumentList.Add($argument) }
    }
    $child = $job.Start($info)
    [void]$seen.Add($child.Id)
    $stderrDrain = $child.StandardError.BaseStream.CopyToAsync([IO.Stream]::Null)
    $pump.Start($child.StandardOutput.BaseStream)
    if ($permit.fixture -and $permit.fixture_case -cin @('ImmediateHeader','StartupDelay')) {
        $child.StandardInput.WriteLine('GO'); $child.StandardInput.Flush()
        while (-not $pump.HeaderValid -and -not $pump.Fault -and -not $pump.Completion.IsCompleted -and -not $child.HasExited -and -not $job.DeadlineReached) { Start-Sleep -Milliseconds 5 }
    }
    $startupDelay = if ($permit.fixture -and $permit.fixture_case -ceq 'StartupDelay') { 450 } else { 0 }
    $pulse = [Tbs5530Observation.HealthPulse]::new($Directory, $runId, $PermitSha256, $layer, $PID, $job, $pump, $startupDelay)
    if ($permit.fixture -and $permit.fixture_case -cnotin @('ImmediateHeader','StartupDelay')) { $child.StandardInput.WriteLine('GO'); $child.StandardInput.Flush() }
    $reason = ''; $readyPublishedAt = 0L; $readyDelayInjected = $false
    while ($true) {
        $now = [DateTimeOffset]::UtcNow
        if ($now -lt $previousUtc -or $now -lt $started.AddMilliseconds($clock.ElapsedMilliseconds - 250)) { $reason = 'UTC_CLOCK_ROLLBACK'; break }
        $previousUtc = $now
        try { [void](& $module { param($Path,$Sha) Read-TbsObserverPermit $Path $Sha } $PermitPath $PermitSha256) }
        catch { $reason = if ($_.Exception.Message -match 'PERMIT_EXPIRED') { 'PERMIT_EXPIRED' } else { 'PERMIT_CHANGED' }; break }
        if ([IO.File]::Exists([Tbs5530Observation.SafePaths]::Check((Join-Path $Directory 'STOP')))) { $reason = 'STOP_REQUESTED'; break }
        if ($job.DeadlineReached -or $clock.ElapsedMilliseconds -ge $permit.maximum_ms) { $reason = 'TIME_LIMIT'; break }
        if ($pump.Fault) { $reason = $pump.Fault; break }
        if ($pump.LimitReached) { $reason = 'BYTE_LIMIT'; break }
        if ($pulse.Fault) { $reason = $pulse.Fault; break }
        if ($child.HasExited) { $reason = 'CHILD_EXIT'; break }
        if ($pump.Completion.IsCompleted) { $reason = if ($pump.Bytes -gt 0 -and -not $pump.HeaderValid) { 'TRUNCATED_PCAP_HEADER' } else { 'STDOUT_CLOSED' }; break }
        if (-not $ready -and $pump.HeaderValid -and $pulse.StartupCompleted) {
            $value = New-ObserverHealth $true 'ready' 'CAPTURE_HEADER_VALID'
            $value.ready = $true; $value.linktype = 249; $value.snaplen = $permit.snaplen
            $value.capture_device = $permit.capture_device; $value.device_address = $permit.device_address
            $value.capture_all = $false; $value.capture_new = ($permit.capture_mode -ceq 'NewDevices')
            $value.capture_mode = $permit.capture_mode
            $value.root_grant_sha256 = $permit.root_grant_sha256
            $value.root_path = $(if ($permit.ContainsKey('root_path')) { $permit.root_path } else { $null })
            $value.identity_validation = 'PARENT_REQUIRED_BEFORE_FIRMWARE'
            $value.filter_contract = 'USBPcap-1.5.4.0-native-cli-source-ecbb65860aef52fd57f8b9fad2581ca092df2feb'
            Write-ObserverJson 'ready.json' $value $true
            $ready = $true
            $pulse.SetReady()
            $readyPublishedAt = $clock.ElapsedMilliseconds
        }
        if ($permit.fixture -and $permit.fixture_case -cin @('ReadyDelay','ReadyLongDelay') -and $ready -and $clock.ElapsedMilliseconds - $readyPublishedAt -ge 300 -and -not $readyDelayInjected) { $pulse.DelayNextReadyPublishForFixture($(if ($permit.fixture_case -ceq 'ReadyLongDelay') { 2100 } else { 450 })); $readyDelayInjected = $true }
        if ($permit.fixture -and $permit.fixture_case -ceq 'ClockRollback' -and $ready) { $previousUtc = [DateTimeOffset]::UtcNow.AddSeconds(1) }
        Start-Sleep -Milliseconds 20
    }
} catch {
    $message = $_.Exception.Message
    $failureType = $_.Exception.GetBaseException().GetType().FullName
    $failureHResult = $_.Exception.GetBaseException().HResult
    $failureLine = $_.InvocationInfo.ScriptLineNumber
    $reason = if ($message -cmatch '^[A-Z][A-Z0-9_]*(?::[A-Za-z0-9_]+)?$') { $message } else { 'OBSERVER_FAULT' }
} finally {
    if ($null -ne $pulse) {
        try { $pulse.Dispose(); $maxHealthGap = $pulse.MaxGapMilliseconds; if ($pulse.Fault -and $reason -ceq 'STOP_REQUESTED') { $reason = $pulse.Fault } } catch { $cleanup = $false; $reason = 'HEALTH_WRITE_FAULT' }
    }
    if ($null -ne $job) {
        try { foreach ($childId in $job.ProcessIds()) { [void]$seen.Add($childId) }; $cleanup = ($job.Stop() -and $cleanup) } catch { $cleanup = $false }
    }
    if ($null -ne $pump) {
        try { $pump.Dispose() } catch { $cleanup = $false; $reason = 'CAPTURE_FLUSH_FAULT' }
        $captureBytes = $pump.Bytes; $headerValid = $pump.HeaderValid
        if ($pump.Fault -and $reason -ceq 'STOP_REQUESTED') { $reason = $pump.Fault }
    }
    if ($null -ne $stderrDrain) { try { [void]$stderrDrain.Wait(1000) } catch { } }
    if ($trustedDirectory) {
        try {
            $capturePath = [Tbs5530Observation.SafePaths]::Check((Join-Path $Directory 'capture.pcap'))
            if ([IO.File]::Exists($capturePath)) {
                $captureBytes = ([IO.FileInfo]::new($capturePath)).Length
                $captureSha256 = (Get-FileHash -LiteralPath $capturePath -Algorithm SHA256).Hash.ToLowerInvariant()
            }
            $state = if ($reason -ceq 'STOP_REQUESTED' -and $cleanup) { 'stopped' } elseif ($reason -cin @('TIME_LIMIT','BYTE_LIMIT')) { 'limited' } else { 'fault' }
            $terminal = New-ObserverHealth $false $state $reason
            $terminal.capture_bytes = $captureBytes; $terminal.cleanup_confirmed = $cleanup
            $terminal.failure_type = $failureType; $terminal.failure_line = $failureLine; $terminal.failure_hresult = $failureHResult
            $terminal.evidence_layer = $layer; $terminal.capture_sha256 = $captureSha256
            $terminal.header_valid = $headerValid; $terminal.ready_observed = $ready; $terminal.max_health_gap_ms = $maxHealthGap
            $terminal.health_startup_ms = $(if ($null -ne $pulse) { $pulse.StartupMilliseconds } else { $null })
            $terminal.health_first_publish_ms = $(if ($null -ne $pulse) { $pulse.FirstPublishMilliseconds } else { $null })
            $terminal.health_first_read_ms = $(if ($null -ne $pulse) { $pulse.FirstReadMilliseconds } else { $null })
            $terminal.health_ready_requested_ms = $(if ($null -ne $pulse) { $pulse.ReadyRequestedMilliseconds } else { $null })
            $terminal.health_failure_type = $(if ($null -ne $pulse) { $pulse.FailureType } else { $null })
            $terminal.health_failure_stage = $(if ($null -ne $pulse) { $pulse.FailureStage } else { $null })
            $terminal.health_failure_hresult = $(if ($null -ne $pulse) { $pulse.FailureHResult } else { $null })
            $terminal.owned_child_pids = @($seen)
            $terminal.root_grant_sha256 = $(if ($null -ne $permit) { $permit.root_grant_sha256 } else { $null })
            $terminal.capture_device = $(if ($null -ne $permit) { $permit.capture_device } else { $null })
            $terminal.root_path = $(if ($null -ne $permit -and $permit.ContainsKey('root_path')) { $permit.root_path } else { $null })
            $terminal.capture_mode = $(if ($null -ne $permit) { $permit.capture_mode } else { $null })
            $terminal.identity_validation = 'PARENT_REQUIRED_BEFORE_FIRMWARE'
            $terminal.measurement_valid = ($null -ne $permit -and -not $permit.fixture -and $permit.capture_mode -ceq 'Address' -and $state -ceq 'stopped' -and $ready -and $headerValid -and $captureBytes -gt 24 -and $cleanup)
            Write-ObserverJson 'health.json' (New-ObserverHealth $false $state $reason)
            Write-ObserverJson 'terminal.json' $terminal $true
        } catch { $cleanup = $false }
    }
    if ($null -ne $binaryLease) { $binaryLease.Dispose() }
    if ($null -ne $permitLease) { $permitLease.Dispose() }
    if ($null -ne $lease) { $lease.Dispose() }
    if ($null -ne $job) { try { $job.Dispose() } catch { $cleanup = $false } }
}
if ($reason -ceq 'STOP_REQUESTED' -and $cleanup) { exit 0 }
exit 2
