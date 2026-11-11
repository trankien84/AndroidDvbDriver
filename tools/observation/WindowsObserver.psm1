#Requires -Version 7.5
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:Observers = @{}
$script:Protocol = 'TBS5530-WINDOWS-OBSERVER/v1'

function Initialize-TbsObserverTypes {
    if (-not ('Tbs5530Observation.SafePaths' -as [type])) {
        Add-Type -Path (Join-Path $PSScriptRoot 'UsbTopology.cs')
    }
}

function Read-TbsObserverPermit {
    param([string]$Path, [string]$Sha256)
    Initialize-TbsObserverTypes
    if ($Sha256 -cnotmatch '^[a-fA-F0-9]{64}$') { throw 'INVALID_PERMIT_SHA256' }
    $pathValue = [Tbs5530Observation.SafePaths]::Check($Path)
    $stream = [IO.File]::Open($pathValue, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete)
    try {
        if ($stream.Length -gt 16384 -or $stream.Length -eq 0) { throw 'INVALID_PERMIT_SIZE' }
        $bytes = [byte[]]::new([int]$stream.Length)
        $stream.ReadExactly($bytes)
        if ($stream.Position -ne $stream.Length) { throw 'PERMIT_CHANGED' }
    } finally { $stream.Dispose() }
    $actual = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
    if ($actual -ine $Sha256) { throw 'PERMIT_CHANGED' }
    $text = [Text.UTF8Encoding]::new($false, $true).GetString($bytes)
    $document = [Text.Json.JsonDocument]::Parse($text)
    try {
        if ($document.RootElement.ValueKind -ne [Text.Json.JsonValueKind]::Object) { throw 'INVALID_PERMIT_JSON' }
        $names = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        foreach ($property in $document.RootElement.EnumerateObject()) {
            if (-not $names.Add($property.Name)) { throw 'DUPLICATE_PERMIT_FIELD' }
        }
    } finally { $document.Dispose() }
    $permit = ConvertFrom-Json -InputObject $text -AsHashtable -DateKind String
    $required = @('protocol','run_id','phase','approved','fixture','root_grant_sha256','expires_utc','capture_device','device_address','usbpcap_path','output_directory','maximum_ms','maximum_bytes','snaplen','bufferlen')
    if (-not $permit.ContainsKey('capture_mode')) { $permit.capture_mode = 'Address' }
    if ($permit.capture_mode -ceq 'NewDevices' -and -not $permit.ContainsKey('device_address')) { $permit.device_address = $null }
    foreach ($key in $required) { if (-not $permit.ContainsKey($key)) { throw "MISSING_PERMIT_FIELD:$key" } }
    foreach ($key in $permit.Keys) {
        if ($key -cnotin $required -and $key -cnotin @('fixture_case','capture_mode','root_path','new_devices_only_on_selected_hub')) { throw "UNKNOWN_PERMIT_FIELD:$key" }
    }
    if ($permit.protocol -cne $script:Protocol -or $permit.approved -isnot [bool] -or -not $permit.approved -or $permit.fixture -isnot [bool]) { throw 'PERMIT_NOT_APPROVED' }
    if ($permit.run_id -isnot [string] -or $permit.run_id -cnotmatch '^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$' -or $permit.phase -cnotin @('cold','operational')) { throw 'INVALID_RUN_OR_PHASE' }
    if ($permit.root_grant_sha256 -isnot [string] -or $permit.root_grant_sha256 -cnotmatch '^[a-fA-F0-9]{64}$') { throw 'INVALID_ROOT_GRANT_HASH' }
    if ($permit.expires_utc -isnot [string] -or $permit.expires_utc -cnotmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,7})?Z$') { throw 'INVALID_UTC_EXPIRY' }
    $expires = [DateTimeOffset]::Parse($permit.expires_utc, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None)
    if ($expires -le [DateTimeOffset]::UtcNow) { throw 'PERMIT_EXPIRED' }
    if ($permit.capture_device -isnot [string] -or $permit.capture_device -cnotmatch '^\\\\\.\\USBPcap[1-9][0-9]*$') { throw 'INVALID_CAPTURE_DEVICE' }
    foreach ($key in @('maximum_ms','maximum_bytes','snaplen','bufferlen')) {
        if ($permit[$key] -isnot [long] -and $permit[$key] -isnot [int]) { throw "INVALID_INTEGER:$key" }
    }
    if ($permit.capture_mode -ceq 'Address') {
        if ($permit.phase -cne 'operational') { throw 'ADDRESS_MODE_REQUIRES_OPERATIONAL' }
        if (($permit.device_address -isnot [long] -and $permit.device_address -isnot [int]) -or $permit.device_address -lt 1 -or $permit.device_address -gt 127) { throw 'INVALID_DEVICE_ADDRESS' }
        if ($permit.ContainsKey('new_devices_only_on_selected_hub')) { throw 'ADDRESS_MODE_NEW_DEVICE_OPTIONS_FORBIDDEN' }
    } elseif ($permit.capture_mode -ceq 'NewDevices') {
        if ($permit.phase -cne 'cold' -or ($null -ne $permit.device_address -and (($permit.device_address -isnot [long] -and $permit.device_address -isnot [int]) -or $permit.device_address -ne 0))) { throw 'NEW_DEVICES_REQUIRES_COLD_WITHOUT_ADDRESS' }
        if (-not $permit.ContainsKey('new_devices_only_on_selected_hub') -or $permit.new_devices_only_on_selected_hub -isnot [bool] -or -not $permit.new_devices_only_on_selected_hub) { throw 'NEW_DEVICES_REQUIRES_EXPLICIT_TYPED_CONSENT' }
        if (-not $permit.ContainsKey('root_path') -or $permit.root_path -isnot [string] -or $permit.root_path -cnotmatch '^\\\\[.?]\\USB[^\r\n\x00]+$') { throw 'NEW_DEVICES_REQUIRES_SELECTED_ROOT' }
    } else { throw 'INVALID_CAPTURE_MODE' }
    if ($permit.snaplen -ne 131072 -or $permit.bufferlen -ne 1048576) { throw 'INVALID_CAPTURE_BUFFER' }
    if ($permit.maximum_ms -lt 100 -or $permit.maximum_ms -gt 90000 -or $permit.maximum_bytes -lt 24 -or $permit.maximum_bytes -gt 67108864) { throw 'INVALID_CAPTURE_LIMIT' }
    $permit.output_directory = [Tbs5530Observation.SafePaths]::Check($permit.output_directory)
    if ($permit.fixture) {
        if ($permit.usbpcap_path -cne 'SYNTHETIC_FIXTURE') { throw 'FIXTURE_BACKEND_REQUIRED' }
        if (-not $permit.ContainsKey('fixture_case')) { $permit.fixture_case = 'HeaderIdle' }
        if ($permit.fixture_case -cnotin @('HeaderIdle','ImmediateHeader','StartupDelay','ReadyDelay','ReadyLongDelay','NoBytes','ByteFlood','EarlyExit','BadHeader','TruncatedHeader','StdoutClose','KnownBinary','Grandchild','ClockRollback')) { throw 'INVALID_FIXTURE_CASE' }
    } else {
        if ($permit.ContainsKey('fixture_case')) { throw 'LIVE_FIXTURE_OPTIONS_FORBIDDEN' }
        if ($permit.maximum_ms -ne 90000 -or $permit.maximum_bytes -ne 67108864) { throw 'LIVE_LIMITS_MUST_MATCH_CONTRACT' }
        $liveRoot = [Tbs5530Observation.SafePaths]::Check('D:\TSDuck') + '\'
        if (-not $permit.output_directory.StartsWith($liveRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'LIVE_OUTPUT_OUTSIDE_TSDUCK' }
        $installed = Join-Path ([Environment]::GetFolderPath([Environment+SpecialFolder]::ProgramFiles)) 'USBPcap\USBPcapCMD.exe'
        $permit.usbpcap_path = [Tbs5530Observation.SafePaths]::Check($permit.usbpcap_path)
        if ($permit.usbpcap_path -ine $installed -or -not [IO.File]::Exists($installed) -or [Diagnostics.FileVersionInfo]::GetVersionInfo($installed).FileVersion -ne '1.5.4.0') { throw 'LIVE_USBPCAP_CONTRACT_UNVERIFIED' }
    }
    return $permit
}

function Invoke-TbsObserverProbe {
    param([string]$Executable, [string[]]$Arguments)
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Executable; $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info
    try {
        if (-not $process.Start()) { throw 'DISCOVERY_START_FAILED' }
        $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(5000)) { $process.Kill($true); [void]$process.WaitForExit(2000); throw 'DISCOVERY_TIMEOUT' }
        $text = $stdout.GetAwaiter().GetResult() + "`n" + $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0 -or $text.Length -gt 262144) { throw 'DISCOVERY_FAILED' }
        return $text
    } finally { $process.Dispose() }
}

function Get-TbsObservationUsbRoots {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$UsbPcapPath)
    Initialize-TbsObserverTypes
    $pathValue = [Tbs5530Observation.SafePaths]::Check($UsbPcapPath)
    if (-not [IO.File]::Exists($pathValue) -or [Diagnostics.FileVersionInfo]::GetVersionInfo($pathValue).FileVersion -ne '1.5.4.0') { throw 'USBPCAP_VERSION_UNVERIFIED' }
    $discovery = Invoke-TbsObserverProbe $pathValue @('--extcap-interfaces')
    $devices = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($line in ($discovery -split '\r?\n')) {
        if ($line -cmatch '^interface \{value=(\\\\\.\\USBPcap[1-9][0-9]*)\}\{display=[^{}\r\n]*\}$') { [void]$devices.Add($Matches[1]) }
    }
    if ($devices.Count -eq 0) { throw 'NO_DISCOVERED_CONTROL_INTERFACES' }
    foreach ($device in $devices) {
        [pscustomobject]@{ capture_device=$device; root_path=[Tbs5530Observation.UsbTopology]::GetRoot($device) }
    }
}

function Get-TbsObservationUsbTargets {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$UsbPcapPath)
    foreach ($root in @(Get-TbsObservationUsbRoots -UsbPcapPath $UsbPcapPath)) {
        foreach ($node in [Tbs5530Observation.UsbTopology]::GetNodes($root.root_path)) {
            if ($node.Vid -eq 0x734c -and $node.Pid -eq 0x5530 -and $node.Address -ge 1 -and $node.Address -le 127) {
                [pscustomobject]@{
                    capture_device = $root.capture_device; device_address = $node.Address
                    connection_path = $node.ConnectionPath; vid = '734c'; pid = '5530'
                }
            }
        }
    }
}

function Get-TbsObserverCaptureArguments {
    param($Permit)
    # USBPcap 1.5.4.0 cmd.c (blob ecbb65860aef52fd57f8b9fad2581ca092df2feb):
    # capture_all and capture_new start FALSE; their no-argument flags alone enable them.
    # Source: https://github.com/desowin/usbpcap/tree/1.5.4.0
    # GUI defaults are not CLI defaults. thread.c sets bit zero for capture_new;
    # USBPcapGetDeviceUSBInfo propagates it only when initializing new device metadata.
    # NewDevices is a hub-wide consent scope; parent must validate identities before firmware.
    $filter = if ($Permit.capture_mode -ceq 'NewDevices') { @('--capture-from-new-devices') } else { @('--devices', [string]$Permit.device_address) }
    return @('-d', $Permit.capture_device) + $filter + @('-o', '-', '-s', [string]$Permit.snaplen, '-b', [string]$Permit.bufferlen)
}

function Start-TbsWindowsObserver {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$PermitPath, [Parameter(Mandatory)][string]$PermitSha256)
    $permit = Read-TbsObserverPermit $PermitPath $PermitSha256
    $output = $permit.output_directory
    $parent = [IO.Path]::GetDirectoryName($output)
    $lease = [Tbs5530Observation.SafePaths]::LockDirectories($parent)
    $outputLease = $null
    try {
        if (-not $permit.fixture) {
            $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
            try { $administrator = [Security.Principal.WindowsPrincipal]::new($identity).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator) } finally { $identity.Dispose() }
            if (-not $administrator) { throw 'ELEVATION_REQUIRED_NO_UAC_AUTOMATION' }
        }
        $worker = [Tbs5530Observation.SafePaths]::Check((Join-Path $PSScriptRoot 'WindowsObserverWorker.ps1'))
        $shell = Join-Path $PSHOME 'pwsh.exe'
        if (-not [IO.File]::Exists($worker) -or -not [IO.File]::Exists($shell)) { throw 'OBSERVER_RUNTIME_MISSING' }
        [void](Read-TbsObserverPermit $PermitPath $PermitSha256)
        [Tbs5530Observation.SafePaths]::CreateNewDirectory($output)
        $outputLease = [Tbs5530Observation.SafePaths]::LockDirectories($output)
        $reservation = [guid]::NewGuid().ToString('N')
        $owner = @{ protocol=$script:Protocol; run_id=$permit.run_id; permit_sha256=$PermitSha256.ToLowerInvariant(); reservation=$reservation; fixture=$permit.fixture }
        [Tbs5530Observation.SafePaths]::WriteJson((Join-Path $output 'reservation.json'), ($owner | ConvertTo-Json -Compress), $true)
        $info = [Diagnostics.ProcessStartInfo]::new()
        $info.FileName = $shell; $info.UseShellExecute = $false; $info.CreateNoWindow = $true
        $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
        foreach ($argument in @('-NoLogo','-NoProfile','-NonInteractive','-File',$worker,'-PermitPath',[IO.Path]::GetFullPath($PermitPath),'-PermitSha256',$PermitSha256,'-Directory',$output,'-Reservation',$reservation)) { $info.ArgumentList.Add($argument) }
        $process = [Diagnostics.Process]::new(); $process.StartInfo = $info
        try { if (-not $process.Start()) { throw 'WORKER_START_FAILED' } } catch { $process.Dispose(); throw }
        $observer = [pscustomobject]@{ Directory=$output; Process=$process }
        $observer.PSObject.TypeNames.Insert(0, 'Tbs5530.WindowsObserver')
        $script:Observers[$process.Id] = @{ Observer=$observer; Reservation=$reservation; PermitSha256=$PermitSha256.ToLowerInvariant(); RunId=$permit.run_id; Stdout=$process.StandardOutput.ReadToEndAsync(); Stderr=$process.StandardError.ReadToEndAsync() }
        return $observer
    } finally { if ($null -ne $outputLease) { $outputLease.Dispose() }; $lease.Dispose() }
}

function Get-TbsObserverOwnership {
    param($Observer)
    if ($null -eq $Observer -or $Observer.Process -isnot [Diagnostics.Process] -or -not $script:Observers.ContainsKey($Observer.Process.Id)) { throw 'OBSERVER_NOT_OWNED' }
    $owned = $script:Observers[$Observer.Process.Id]
    if (-not [object]::ReferenceEquals($Observer, $owned.Observer)) { throw 'OBSERVER_NOT_OWNED' }
    [void][Tbs5530Observation.SafePaths]::Check($Observer.Directory)
    $ownerPath = [Tbs5530Observation.SafePaths]::Check((Join-Path $Observer.Directory 'reservation.json'))
    $owner = [Tbs5530Observation.SafePaths]::ReadJson($ownerPath) | ConvertFrom-Json
    if ($owner.reservation -cne $owned.Reservation -or $owner.permit_sha256 -cne $owned.PermitSha256) { throw 'OUTPUT_OWNERSHIP_CHANGED' }
    return $owned
}

function Test-TbsObserverHealthFresh {
    param($Health)
    if ($null -eq $Health) { return $false }
    $age = ([DateTimeOffset]::UtcNow - [DateTimeOffset]::Parse($Health.utc)).TotalMilliseconds
    if ($age -lt 0 -or $age -gt [Tbs5530Observation.HealthPulse]::FreshnessLimitMilliseconds) { return $false }
    $monotonic = $Health.PSObject.Properties['health_monotonic_ms']
    if ($null -ne $monotonic) {
        $elapsed = [Environment]::TickCount64 - [long]$monotonic.Value
        if ($elapsed -lt 0 -or $elapsed -gt [Tbs5530Observation.HealthPulse]::FreshnessLimitMilliseconds) { return $false }
    }
    return $true
}

function Get-TbsWindowsObserverStatus {
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Observer)
    $owned = Get-TbsObserverOwnership $Observer
    $status = [ordered]@{ protocol=$script:Protocol; run_id=$owned.RunId; state='starting'; alive=(-not $Observer.Process.HasExited); ready=$false; ready_record=$null; health=$null; terminal=$null; cadence_target_ms=[Tbs5530Observation.HealthPulse]::CadenceTargetMilliseconds; freshness_limit_ms=[Tbs5530Observation.HealthPulse]::FreshnessLimitMilliseconds }
    foreach ($name in @('ready','health','terminal')) {
        $pathValue = [Tbs5530Observation.SafePaths]::Check((Join-Path $Observer.Directory ($name + '.json')))
        if ([IO.File]::Exists($pathValue)) {
            $item = [Tbs5530Observation.SafePaths]::ReadJson($pathValue) | ConvertFrom-Json -DateKind String
            if ($item.protocol -cne $script:Protocol -or $item.run_id -cne $owned.RunId -or $item.permit_sha256 -cne $owned.PermitSha256) { throw 'STATUS_BINDING_CHANGED' }
            $status[$(if ($name -ceq 'ready') { 'ready_record' } else { $name })] = $item
        }
    }
    if ($null -ne $status.terminal) { $status.state = 'terminal' }
    elseif (-not $status.alive) { $status.state = 'failed_without_terminal' }
    elseif ($null -ne $status.ready_record) {
        $fresh = Test-TbsObserverHealthFresh $status.health
        if (-not $fresh) {
            $latest = [Tbs5530Observation.SafePaths]::ReadJson((Join-Path $Observer.Directory 'health.json')) | ConvertFrom-Json -DateKind String
            if ($latest.protocol -cne $script:Protocol -or $latest.run_id -cne $owned.RunId -or $latest.permit_sha256 -cne $owned.PermitSha256) { throw 'STATUS_BINDING_CHANGED' }
            $status.health = $latest
            $fresh = Test-TbsObserverHealthFresh $latest
        }
        if ($fresh -and $status.health.alive -and $status.health.ready) { $status.ready = $true; $status.state = 'ready' }
        else { $status.state = 'unresponsive' }
    }
    return ($status | ConvertTo-Json -Depth 12 -Compress)
}

function Stop-TbsWindowsObserver {
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Observer, [ValidateRange(0,30)][int]$WaitSeconds=10)
    [void](Get-TbsObserverOwnership $Observer)
    $stop = [Tbs5530Observation.SafePaths]::Check((Join-Path $Observer.Directory 'STOP'))
    try { $file = [IO.File]::Open($stop, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read); $file.Dispose() }
    catch [IO.IOException] { if (-not [IO.File]::Exists($stop)) { throw } }
    [void]$Observer.Process.WaitForExit($WaitSeconds * 1000)
    return (Get-TbsWindowsObserverStatus $Observer)
}

Export-ModuleMember -Function Get-TbsObservationUsbRoots,Get-TbsObservationUsbTargets,Start-TbsWindowsObserver,Get-TbsWindowsObserverStatus,Stop-TbsWindowsObserver
