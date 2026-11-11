#Requires -Version 7.5
[CmdletBinding()]
param([string]$ReportPath)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$module = Import-Module (Join-Path (Split-Path $PSScriptRoot -Parent) 'WindowsObserver.psm1') -Force -PassThru
& $module { Initialize-TbsObserverTypes }
$root = Join-Path $PSScriptRoot ('WindowsRuns-' + [guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($root)
$script:passed=0; $script:failed=0; $script:skipped=0
$script:lines=[Collections.Generic.List[string]]::new()
$script:observers=[Collections.Generic.List[object]]::new()
$script:index=0; $script:reservedWorkers=0

function Assert-Observer {
    param([bool]$Condition,[string]$Name)
    if ($Condition) { $script:passed++; $script:lines.Add("PASS $Name") }
    else { $script:failed++; $script:lines.Add("FAIL $Name") }
}
function New-ObserverCase {
    param([string]$Scenario='HeaderIdle',[int]$MaximumMs=15000,[long]$MaximumBytes=67108864)
    $script:index++
    $name='WindowsCase-' + $script:index
    $permit=@{
        protocol='TBS5530-WINDOWS-OBSERVER/v1'; run_id=$name; phase='operational'; approved=$true; fixture=$true
        root_grant_sha256=('c' * 64); expires_utc=[DateTimeOffset]::UtcNow.AddMinutes(2).ToString("yyyy-MM-ddTHH:mm:ss.fffffffZ")
        capture_mode='Address'; capture_device=('\\.\USBPcap' + (1000+$script:index)); device_address=37
        usbpcap_path='SYNTHETIC_FIXTURE'; output_directory=(Join-Path $root ($name+'-output'))
        maximum_ms=$MaximumMs; maximum_bytes=$MaximumBytes; snaplen=131072; bufferlen=1048576; fixture_case=$Scenario
    }
    return @{ Permit=$permit; Path=(Join-Path $root ($name+'.json')); Sha='' }
}
function Write-ObserverPermit {
    param($Case)
    [IO.File]::WriteAllText($Case.Path,($Case.Permit | ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
    $Case.Sha=(Get-FileHash -LiteralPath $Case.Path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Start-ObserverCase {
    param($Case)
    Write-ObserverPermit $Case
    $observer=Start-TbsWindowsObserver -PermitPath $Case.Path -PermitSha256 $Case.Sha
    $script:observers.Add($observer)
    return $observer
}
function Wait-Observer {
    param($Observer,[switch]$Ready)
    $wait=[Diagnostics.Stopwatch]::StartNew()
    do {
        $status=Get-TbsWindowsObserverStatus -Observer $Observer | ConvertFrom-Json -DateKind String
        if ($null -ne $status.terminal -or ($Ready -and $status.ready)) { return $status }
        if (-not $status.alive) {
            $status=Get-TbsWindowsObserverStatus -Observer $Observer | ConvertFrom-Json -DateKind String
            if ($null -ne $status.terminal) { return $status }
            $detail=& $module {param($Id) $script:Observers[$Id].Stderr.GetAwaiter().GetResult()} $Observer.Process.Id
            throw "Worker exited without terminal (exit $($Observer.Process.ExitCode)): $detail"
        }
        Start-Sleep -Milliseconds 30
    } while ($wait.ElapsedMilliseconds -lt 20000)
    $script:lines.Add('TIMEOUT_STATUS ' + ($status | ConvertTo-Json -Depth 10 -Compress))
    throw 'Fixture worker did not settle'
}
function Assert-Terminal {
    param($Observer,[string[]]$Reasons,[string]$Name)
    $status=Wait-Observer $Observer
    [void]$Observer.Process.WaitForExit(3000)
    Assert-Observer ($status.terminal.reason -cin $Reasons) "$Name reason=$($status.terminal.reason) type=$($status.terminal.failure_type) line=$($status.terminal.failure_line) hresult=$($status.terminal.failure_hresult)"
    Assert-Observer ([bool]$status.terminal.cleanup_confirmed) "$Name owned cleanup"
    Assert-Observer (-not $status.terminal.measurement_valid) "$Name cannot pass live measurement"
    Assert-Observer ($status.terminal.evidence_layer -ceq 'SYNTHETIC_FIXTURE') "$Name synthetic layer"
    foreach ($childId in $status.terminal.owned_child_pids) {
        $other=Get-Process -Id $childId -ErrorAction SilentlyContinue
        Assert-Observer ($null -eq $other) "$Name owned PID $childId exited"
    }
    return $status
}
function Assert-Rejected {
    param($Case,[string]$Name)
    Write-ObserverPermit $Case
    $rejected=$false
    try { $unexpected=Start-TbsWindowsObserver -PermitPath $Case.Path -PermitSha256 $Case.Sha; $script:observers.Add($unexpected) }
    catch { $rejected=$true }
    Assert-Observer $rejected $Name
}
function Invoke-ReservedWorker {
    param($Case,[string]$Hash,[switch]$Consumed)
    [void][IO.Directory]::CreateDirectory($Case.Permit.output_directory)
    $token=[guid]::NewGuid().ToString('N')
    @{protocol=$Case.Permit.protocol;run_id=$Case.Permit.run_id;permit_sha256=$Hash;reservation=$token;fixture=$true} | ConvertTo-Json -Compress | Set-Content -LiteralPath (Join-Path $Case.Permit.output_directory 'reservation.json') -NoNewline
    if ($Consumed) { [IO.File]::WriteAllBytes((Join-Path $Case.Permit.output_directory 'capture.pcap'),[byte[]](10,20,30)) }
    $info=[Diagnostics.ProcessStartInfo]::new()
    $info.FileName=Join-Path $PSHOME 'pwsh.exe'; $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $worker=Join-Path (Split-Path $PSScriptRoot -Parent) 'WindowsObserverWorker.ps1'
    foreach ($argument in @('-NoLogo','-NoProfile','-NonInteractive','-File',$worker,'-PermitPath',$Case.Path,'-PermitSha256',$Hash,'-Directory',$Case.Permit.output_directory,'-Reservation',$token)) { $info.ArgumentList.Add($argument) }
    $process=[Diagnostics.Process]::Start($info)
    $script:reservedWorkers++
    $out=$process.StandardOutput.ReadToEndAsync(); $err=$process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit(10000)) { $process.Kill($true); [void]$process.WaitForExit(2000); throw 'Reserved fixture worker timeout' }
    Assert-Observer ($process.ExitCode -eq 2) 'Reserved worker fails closed'
    Assert-Observer ($out.GetAwaiter().GetResult().Length -eq 0) 'Worker stdout contains no binary or prose'
    $process.Dispose()
    $terminalPath=Join-Path $Case.Permit.output_directory 'terminal.json'
    if (Test-Path -LiteralPath $terminalPath) { return ([IO.File]::ReadAllText($terminalPath) | ConvertFrom-Json -DateKind String) }
    return $null
}

try {
    $expected=@('Get-TbsObservationUsbRoots','Get-TbsObservationUsbTargets','Start-TbsWindowsObserver','Get-TbsWindowsObserverStatus','Stop-TbsWindowsObserver')
    Assert-Observer ((@(Get-Command -Module WindowsObserver).Name | Sort-Object) -join ',' -ceq (($expected | Sort-Object) -join ',')) 'Exact exported API'
    $case=New-ObserverCase
    $observer=Start-ObserverCase $case
    $status=Wait-Observer $observer -Ready
    Assert-Observer ($status.ready -is [bool] -and $status.ready -and $status.alive -and $status.ready_record.linktype -eq 249 -and $status.health.ready) 'Classic USBPcap header gates boolean ready while child alive'
    Assert-Observer ($status.ready_record.capture_bytes -eq 24) 'Header-only fixture consumes stdout'
    Assert-Observer ($status.ready_record.capture_all -eq $false -and $status.ready_record.capture_new -eq $false) 'Address mode filter flags'
    $times=[Collections.Generic.List[DateTimeOffset]]::new()
    $poll=[Diagnostics.Stopwatch]::StartNew()
    while ($poll.ElapsedMilliseconds -lt 650) {
        $health=Get-TbsWindowsObserverStatus $observer | ConvertFrom-Json -DateKind String
        if ($null -ne $health.health) {
            $stamp=[DateTimeOffset]::Parse($health.health.utc)
            if ($times.Count -eq 0 -or $times[$times.Count-1] -ne $stamp) { $times.Add($stamp) }
        }
        Start-Sleep -Milliseconds 15
    }
    $gaps=@(for ($i=1;$i -lt $times.Count;$i++) { ($times[$i]-$times[$i-1]).TotalMilliseconds })
    Assert-Observer ($gaps.Count -ge 3 -and ($gaps | Measure-Object -Maximum).Maximum -le 2000 -and $health.health.cadence_target_ms -eq 200 -and $health.health.freshness_limit_ms -eq 2000) "Cadence target200/freshness2000 (samples=$($times.Count), observed_max=$((($gaps | Measure-Object -Maximum).Maximum)))"
    $stop=Stop-TbsWindowsObserver -Observer $observer -WaitSeconds 10 | ConvertFrom-Json -DateKind String
    Assert-Observer ($stop.state -ceq 'terminal' -and -not $stop.ready -and $stop.terminal.reason -ceq 'STOP_REQUESTED') 'STOP file and wait settle worker'
    Assert-Observer ($stop.terminal.max_health_gap_ms -le 2000 -and $stop.terminal.freshness_limit_ms -eq 2000) 'Worker records cadence gaps and enforces two-second freshness'
    [void](Assert-Terminal $observer @('STOP_REQUESTED') 'STOP')
    Assert-Observer (Test-Path -LiteralPath (Join-Path $observer.Directory 'STOP')) 'Stop writes explicit STOP marker'
    Assert-Rejected $case 'Consumed output directory is not reused'

    $case=New-ObserverCase 'KnownBinary'
    $observer=Start-ObserverCase $case
    [void](Wait-Observer $observer -Ready)
    [void](Stop-TbsWindowsObserver $observer)
    $status=Assert-Terminal $observer @('STOP_REQUESTED') 'Known binary'
    $bytes=[IO.File]::ReadAllBytes((Join-Path $observer.Directory 'capture.pcap'))
    $expectedBytes=[byte[]]::new(536)
    [byte[]]$header=0xd4,0xc3,0xb2,0xa1,2,0,4,0,0,0,0,0,0,0,0,0,0,0,2,0,0xf9,0,0,0
    [Array]::Copy($header,$expectedBytes,24)
    for ($i=0;$i -lt 512;$i++) { $expectedBytes[$i+24]=[byte]($i%256) }
    $expectedSha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($expectedBytes)).ToLowerInvariant()
    Assert-Observer ($bytes.Length -eq 536 -and $status.terminal.capture_sha256 -ceq $expectedSha) 'Binary stdout hash exact, including NUL/non-UTF8 bytes'
    Assert-Observer ((Get-FileHash -LiteralPath (Join-Path $observer.Directory 'capture.pcap')).Hash -ieq $expectedSha) 'Persisted binary hash matches terminal'

    $case=New-ObserverCase 'ByteFlood' 15000 65567
    $observer=Start-ObserverCase $case
    $status=Assert-Terminal $observer @('BYTE_LIMIT') 'Hard byte cap'
    Assert-Observer ($status.terminal.capture_bytes -eq 65567 -and (Get-Item -LiteralPath (Join-Path $observer.Directory 'capture.pcap')).Length -eq 65567) 'Never writes past non-aligned byte cap'

    $case=New-ObserverCase 'HeaderIdle' 15000 24
    $observer=Start-ObserverCase $case
    $status=Assert-Terminal $observer @('BYTE_LIMIT') 'Header-only byte cap'
    Assert-Observer ($status.terminal.capture_bytes -eq 24 -and -not $status.terminal.ready_observed) 'Byte cap enforced without packet payload'
    foreach ($scenario in @('NoBytes','HeaderIdle')) {
        $case=New-ObserverCase $scenario 2000
        $observer=Start-ObserverCase $case
        $status=Assert-Terminal $observer @('TIME_LIMIT') "$scenario monotonic deadline"
        Assert-Observer ($status.terminal.elapsed_ms -lt 4000) "$scenario independent wall deadline"
        if ($scenario -ceq 'NoBytes') { Assert-Observer ($status.terminal.capture_bytes -eq 0 -and -not $status.terminal.ready_observed) 'No traffic never reports ready' }
    }
    $case=New-ObserverCase 'StartupDelay'
    $observer=Start-ObserverCase $case
    [void](Wait-Observer $observer -Ready)
    [void](Stop-TbsWindowsObserver $observer)
    $status=Assert-Terminal $observer @('STOP_REQUESTED') 'Startup barrier450'
    Assert-Observer ($status.terminal.health_first_publish_ms -ge 450 -and $status.terminal.health_ready_requested_ms -ge $status.terminal.health_startup_ms) 'Startup publish/readback complete before ready'
    Assert-Observer ($status.terminal.max_health_gap_ms -lt 2000) 'Startup450 is excluded from active freshness gap'

    $case=New-ObserverCase 'ReadyDelay'
    $observer=Start-ObserverCase $case
    [void](Wait-Observer $observer -Ready)
    $watch=[Diagnostics.Stopwatch]::StartNew(); $observedFreshDelay=$false; $observedCadenceMiss=$false
    do {
        $status=Get-TbsWindowsObserverStatus $observer | ConvertFrom-Json -DateKind String
        $age=([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($status.health.utc)).TotalMilliseconds
        if ($age -gt 200 -and $age -lt 1900 -and $status.ready -and $status.health.alive) {$observedFreshDelay=$true}
        if ($status.health.cadence_misses -ge 1 -and $status.health.max_health_gap_ms -ge 450) {$observedCadenceMiss=$true}
        if ($null -ne $status.terminal -or ($observedFreshDelay -and $observedCadenceMiss)) {break}
        Start-Sleep -Milliseconds 15
    } while ($watch.ElapsedMilliseconds -lt 4000)
    Assert-Observer $observedFreshDelay '450 ms delayed health remains ready within freshness2000'
    Assert-Observer $observedCadenceMiss '450 ms delay increments cadence misses and maxgap'
    Assert-Observer ($null -eq $status.terminal -and $status.alive) 'Cadence miss alone does not terminate recorder'
    $snapshot=$status.health | ConvertTo-Json -Depth 4 | ConvertFrom-Json -DateKind String
    $snapshot.utc=[DateTimeOffset]::UtcNow.AddMilliseconds(-2100).ToString('o')
    $snapshot.health_monotonic_ms=[Environment]::TickCount64-2100
    $fresh=& $module {param($Value) Test-TbsObserverHealthFresh $Value} $snapshot
    Assert-Observer (-not $fresh) 'GetStatus freshness predicate rejects a real-worker snapshot older than2000'
    $snapshot.utc=[DateTimeOffset]::UtcNow.ToString('o')
    $fresh=& $module {param($Value) Test-TbsObserverHealthFresh $Value} $snapshot
    Assert-Observer (-not $fresh) 'Monotonic freshness rejects stale health even with a fresh UTC string'
    [void](Stop-TbsWindowsObserver $observer)
    $status=Assert-Terminal $observer @('STOP_REQUESTED') 'Cadence450 clean stop'
    Assert-Observer ($status.terminal.cadence_misses -ge 1 -and $status.terminal.max_health_gap_ms -ge 450 -and $status.terminal.max_health_gap_ms -le 2000) 'Terminal retains cadence misses without a false freshness fault'

    $case=New-ObserverCase 'ReadyLongDelay'
    $observer=Start-ObserverCase $case
    [void](Wait-Observer $observer -Ready)
    $watch=[Diagnostics.Stopwatch]::StartNew(); $staleNotReady=$false
    do {
        $status=Get-TbsWindowsObserverStatus $observer | ConvertFrom-Json -DateKind String
        $age=([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($status.health.utc)).TotalMilliseconds
        if ($age -gt 2000 -and -not $staleNotReady) {Assert-Observer (-not $status.ready) 'Stale GetStatus older than2000 cannot remain ready';$staleNotReady=$true}
        if ($null -ne $status.terminal) {break}
        Start-Sleep -Milliseconds 10
    } while ($watch.ElapsedMilliseconds -lt 5000)
    $status=Assert-Terminal $observer @('HEALTH_DEADLINE_MISSED') 'Hard freshness2100'
    Assert-Observer (-not $status.ready -and $status.terminal.ready_observed -and $status.terminal.max_health_gap_ms -gt 2000) 'Independent freshness watchdog stops a writer held for2100'
    Assert-Observer ($watch.ElapsedMilliseconds -lt 4500) 'Freshness fault cleanup remains bounded'
    $script:lines.Add("OBSERVATION stale_status_window_seen=$staleNotReady; independent age/monotonic predicates both checked")

    $case=New-ObserverCase 'ReadyLongDelay'
    $observer=Start-ObserverCase $case
    [void](Wait-Observer $observer -Ready)
    $watch=[Diagnostics.Stopwatch]::StartNew()
    do {
        $status=Get-TbsWindowsObserverStatus $observer | ConvertFrom-Json -DateKind String
        $age=([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($status.health.utc)).TotalMilliseconds
        if ($age -ge 250) {break}
        Start-Sleep -Milliseconds 15
    } while ($watch.ElapsedMilliseconds -lt 1500)
    $stopWatch=[Diagnostics.Stopwatch]::StartNew()
    [void](Stop-TbsWindowsObserver $observer)
    $status=Assert-Terminal $observer @('STOP_REQUESTED') 'STOP during a delayed writer'
    Assert-Observer ($age -ge 250 -and $stopWatch.ElapsedMilliseconds -lt 2000) 'STOP interrupts fixture writer delay without extending the live cap'

    foreach ($scenario in @('BadHeader','TruncatedHeader','EarlyExit','StdoutClose','ClockRollback')) {
        $case=New-ObserverCase $scenario
        $observer=Start-ObserverCase $case
        $expectedReason=switch ($scenario) { 'BadHeader' {'INVALID_PCAP_HEADER'} 'TruncatedHeader' {'TRUNCATED_PCAP_HEADER'} 'EarlyExit' {'CHILD_EXIT'} 'StdoutClose' {'STDOUT_CLOSED'} 'ClockRollback' {'UTC_CLOCK_ROLLBACK'} }
        $status=Assert-Terminal $observer @($expectedReason) $scenario
        if ($scenario -cin @('BadHeader','TruncatedHeader','EarlyExit')) { Assert-Observer (-not $status.terminal.ready_observed) "$scenario cannot become ready" }
    }

    $case=New-ObserverCase 'Grandchild'
    $observer=Start-ObserverCase $case
    [void](Wait-Observer $observer -Ready)
    Start-Sleep -Milliseconds 250
    $status=Get-TbsWindowsObserverStatus $observer | ConvertFrom-Json -DateKind String
    Assert-Observer ($status.health.own_pids.Count -ge 3) 'Private job observes recorder grandchild'
    [void](Stop-TbsWindowsObserver $observer)
    $status=Assert-Terminal $observer @('STOP_REQUESTED') 'Process tree cleanup'
    Assert-Observer ($status.terminal.owned_child_pids.Count -ge 2) 'Cleanup includes recorder descendants'
    Assert-Observer ($null -ne (Get-Process -Id $PID -ErrorAction SilentlyContinue)) 'Unrelated test runner survives owned cleanup'

    foreach ($mutation in @('hash','expiry')) {
        $case=New-ObserverCase
        if ($mutation -ceq 'expiry') { $case.Permit.expires_utc=[DateTimeOffset]::UtcNow.AddSeconds(3).ToString("yyyy-MM-ddTHH:mm:ss.fffffffZ") }
        $observer=Start-ObserverCase $case
        [void](Wait-Observer $observer -Ready)
        if ($mutation -ceq 'hash') { [IO.File]::AppendAllText($case.Path,' ') }
        [void](Assert-Terminal $observer @($(if ($mutation -ceq 'hash') {'PERMIT_CHANGED'} else {'PERMIT_EXPIRED'})) "Permit $mutation after spawn")
    }
    foreach ($field in @('approved','fixture','phase','capture_device','device_address','maximum_ms','maximum_bytes','snaplen','bufferlen','root_grant_sha256','expires_utc','usbpcap_path','command')) {
        $case=New-ObserverCase
        switch ($field) {
            'approved' {$case.Permit.approved='true'}
            'fixture' {$case.Permit.fixture='true'}
            'phase' {$case.Permit.phase='cold'}
            'capture_device' {$case.Permit.capture_device+=' --capture-from-all-devices'}
            'device_address' {$case.Permit.device_address='37'}
            'maximum_ms' {$case.Permit.maximum_ms=90001}
            'maximum_bytes' {$case.Permit.maximum_bytes=67108865}
            'snaplen' {$case.Permit.snaplen=65535}
            'bufferlen' {$case.Permit.bufferlen=134217728}
            'root_grant_sha256' {$case.Permit.root_grant_sha256='invalid'}
            'expires_utc' {$case.Permit.expires_utc=[DateTimeOffset]::UtcNow.AddSeconds(-1).ToString('o')}
            'usbpcap_path' {$case.Permit.usbpcap_path=Join-Path $PSHOME 'pwsh.exe'}
            'command' {$case.Permit.command='arbitrary command'}
        }
        Assert-Rejected $case "Reject invalid or injectable $field"
    }
    foreach ($address in @(0,128,-1)) { $case=New-ObserverCase; $case.Permit.device_address=$address; Assert-Rejected $case "Address bounds $address" }
    $case=New-ObserverCase; Write-ObserverPermit $case
    $badHash=$case.Sha -replace '^.',$(if ($case.Sha[0] -ceq '0') {'1'} else {'0'})
    $rejected=$false; try { Start-TbsWindowsObserver $case.Path $badHash | Out-Null } catch { $rejected=$true }
    Assert-Observer $rejected 'SHA checked before spawn'
    $terminal=Invoke-ReservedWorker $case $badHash
    Assert-Observer ($terminal.reason -ceq 'PERMIT_CHANGED' -and $terminal.cleanup_confirmed -and $null -eq $terminal.recorder_pid) 'Worker independently checks SHA before recorder'
    $case=New-ObserverCase; $case.Permit.expires_utc=[DateTimeOffset]::UtcNow.AddSeconds(-2).ToString("yyyy-MM-ddTHH:mm:ssZ"); Write-ObserverPermit $case
    $terminal=Invoke-ReservedWorker $case $case.Sha
    Assert-Observer ($terminal.reason -ceq 'PERMIT_EXPIRED' -and $terminal.cleanup_confirmed) 'Worker independently rejects expired permit'
    $case=New-ObserverCase; Write-ObserverPermit $case
    $terminal=Invoke-ReservedWorker $case $case.Sha -Consumed
    Assert-Observer ($null -eq $terminal -and [IO.File]::ReadAllBytes((Join-Path $case.Permit.output_directory 'capture.pcap')).Length -eq 3) 'Direct worker never overwrites consumed output'

    $case=New-ObserverCase; Write-ObserverPermit $case
    [IO.File]::WriteAllText($case.Path,'{"protocol":', [Text.UTF8Encoding]::new($false)); $case.Sha=(Get-FileHash -LiteralPath $case.Path).Hash.ToLowerInvariant()
    $terminal=Invoke-ReservedWorker $case $case.Sha
    Assert-Observer ($terminal.state -ceq 'fault' -and $null -eq $terminal.recorder_pid -and -not $terminal.measurement_valid -and $terminal.evidence_layer -ceq 'SYNTHETIC_FIXTURE') 'Malformed JSON cannot launch recorder or look live'
    $case=New-ObserverCase; $case.Permit.fixture=$false; $case.Permit.Remove('fixture_case'); $case.Permit.usbpcap_path='C:\Program Files\USBPcap\USBPcapCMD.exe'; Assert-Rejected $case 'Live output restricted to D:\TSDuck without capture or elevation'
    $case=New-ObserverCase; $case.Permit.output_directory=Join-Path $root '..\WindowsEscape'; Assert-Rejected $case 'Deny output path traversal'
    $case=New-ObserverCase; $case.Permit.output_directory='\\server\share\capture'; Assert-Rejected $case 'Deny UNC output'
    $case=New-ObserverCase; $case.Permit.output_directory=(Join-Path $root 'WindowsSpace '); Assert-Rejected $case 'Deny ambiguous trailing space'
    $junctionTarget=Join-Path $root 'WindowsJunctionTarget'; [void][IO.Directory]::CreateDirectory($junctionTarget)
    $junction=Join-Path $root 'WindowsJunction'; [void](New-Item -ItemType Junction -Path $junction -Target $junctionTarget)
    $case=New-ObserverCase; $case.Permit.output_directory=Join-Path $junction 'WindowsOutput'; Assert-Rejected $case 'Deny reparse point ancestor'
    [IO.Directory]::Delete($junction)
    $symlink=Join-Path $root 'WindowsSymlink'
    try {
        [void][IO.Directory]::CreateSymbolicLink($symlink,$junctionTarget)
        $case=New-ObserverCase; $case.Permit.output_directory=Join-Path $symlink 'WindowsOutput'; Assert-Rejected $case 'Deny symbolic link ancestor'
        [IO.Directory]::Delete($symlink)
    } catch { $script:skipped++; $script:lines.Add('NOT RUN symbolic link creation requires an unavailable host privilege; junction denial tested') }

    $case=New-ObserverCase
    $argsAddress=@(& $module {param($Value) Get-TbsObserverCaptureArguments $Value} $case.Permit)
    Assert-Observer (($argsAddress -join '|') -ceq "-d|$($case.Permit.capture_device)|--devices|37|-o|-|-s|131072|-b|1048576") 'Address argv is exactly target only, binary stdout'
    Assert-Observer (-not ($argsAddress -ccontains '-A') -and -not ($argsAddress -ccontains '--capture-from-all-devices') -and -not ($argsAddress -ccontains '--capture-from-new-devices')) 'Address denies all/new flags'
    $case.Permit.phase='cold'; $case.Permit.capture_mode='NewDevices'; $case.Permit.device_address=0
    $case.Permit.root_path='\\.\USB#ROOT_HUB30#SYNTHETIC_' + [guid]::NewGuid().ToString('N')
    $case.Permit.new_devices_only_on_selected_hub=$true
    $argsNew=@(& $module {param($Value) Get-TbsObserverCaptureArguments $Value} $case.Permit)
    Assert-Observer (($argsNew -join '|') -ceq "-d|$($case.Permit.capture_device)|--capture-from-new-devices|-o|-|-s|131072|-b|1048576") 'NewDevices argv excludes existing-device address bits'
    Assert-Observer (-not ($argsNew -ccontains '-A') -and -not ($argsNew -ccontains '--capture-from-all-devices') -and -not ($argsNew -ccontains '--devices')) 'NewDevices never emits all/existing flags'
    $observer=Start-ObserverCase $case
    $status=Wait-Observer $observer -Ready
    Assert-Observer ($status.ready_record.capture_mode -ceq 'NewDevices' -and $status.ready_record.capture_new -and -not $status.ready_record.capture_all -and $status.ready_record.root_path -ceq $case.Permit.root_path) 'Cold NewDevices ready binds selected root'
    [void](Stop-TbsWindowsObserver $observer)
    [void](Assert-Terminal $observer @('STOP_REQUESTED') 'NewDevices fixture')
    $binding=New-ObserverCase
    $binding.Permit.capture_mode='NewDevices'; $binding.Permit.phase='cold'; $binding.Permit.device_address=$null
    $binding.Permit.root_path=$case.Permit.root_path; $binding.Permit.new_devices_only_on_selected_hub=$true
    $observer=Start-ObserverCase $binding
    [void](Wait-Observer $observer -Ready)
    $binding.Permit.root_grant_sha256=('d' * 64)
    [IO.File]::WriteAllText($binding.Path,($binding.Permit | ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
    [void](Assert-Terminal $observer @('PERMIT_CHANGED') 'NewDevices root grant binding')
    $orphanCase=New-ObserverCase 'Grandchild'
    $orphan=Start-ObserverCase $orphanCase
    [void](Wait-Observer $orphan -Ready)
    Start-Sleep -Milliseconds 250
    $orphanStatus=Get-TbsWindowsObserverStatus $orphan | ConvertFrom-Json -DateKind String
    $ownedIds=@($orphanStatus.health.own_pids | Where-Object { $_ -ne $orphan.Process.Id })
    $orphan.Process.Kill(); [void]$orphan.Process.WaitForExit(3000)
    $drain=[Diagnostics.Stopwatch]::StartNew()
    do { $remaining=@($ownedIds | ForEach-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue }); if ($remaining.Count -eq 0) { break }; Start-Sleep -Milliseconds 20 } while ($drain.ElapsedMilliseconds -lt 3000)
    Assert-Observer ($ownedIds.Count -ge 2 -and $remaining.Count -eq 0) 'Kill-on-close private job drains recorder tree after worker failure'
    $orphanStatus=Get-TbsWindowsObserverStatus $orphan | ConvertFrom-Json -DateKind String
    Assert-Observer (-not $orphanStatus.ready -and -not $orphanStatus.alive -and $orphanStatus.state -ceq 'failed_without_terminal') 'Abrupt worker loss cannot leave boolean ready true'
    foreach ($failure in @('phase','bool-string','bool-false','bool-number','missing-root','bad-root','missing-consent','existing-address','missing-root-hash','mode','all-flag','missing-control')) {
        $deny=New-ObserverCase
        $deny.Permit.capture_mode='NewDevices'; $deny.Permit.phase='cold'; $deny.Permit.device_address=$null
        $deny.Permit.root_path=$case.Permit.root_path; $deny.Permit.new_devices_only_on_selected_hub=$true
        switch ($failure) {
            'phase' {$deny.Permit.phase='operational'}
            'bool-string' {$deny.Permit.new_devices_only_on_selected_hub='true'}
            'bool-false' {$deny.Permit.new_devices_only_on_selected_hub=$false}
            'bool-number' {$deny.Permit.new_devices_only_on_selected_hub=1}
            'missing-root' {$deny.Permit.Remove('root_path')}
            'bad-root' {$deny.Permit.root_path='unspecified'}
            'missing-consent' {$deny.Permit.Remove('new_devices_only_on_selected_hub')}
            'existing-address' {$deny.Permit.device_address=37}
            'missing-root-hash' {$deny.Permit.Remove('root_grant_sha256')}
            'mode' {$deny.Permit.capture_mode='AllDevices'}
            'all-flag' {$deny.Permit.capture_all=$true}
            'missing-control' {$deny.Permit.Remove('capture_device')}
        }
        Assert-Rejected $deny "NewDevices rejects $failure"
    }
} catch {
    $script:failed++; $script:lines.Add("FAIL unexpected test failure: $($_.Exception.Message)")
    $script:lines.Add($_.ScriptStackTrace)
} finally {
    foreach ($observer in $script:observers) {
        try { if (-not $observer.Process.HasExited) { [void](Stop-TbsWindowsObserver -Observer $observer -WaitSeconds 10) } } catch { $script:failed++; $script:lines.Add('FAIL fixture cleanup') }
    }
    $stillRunning=@($script:observers | Where-Object { -not $_.Process.HasExited })
    Assert-Observer ($stillRunning.Count -eq 0) 'All owned fixture workers exited'
    if ($stillRunning.Count -eq 0 -and [IO.Directory]::Exists($root)) { [IO.Directory]::Delete($root,$true) }
}
$summary="Kết quả Windows observer: PASS=$script:passed; FAIL=$script:failed; NOT_RUN=$script:skipped; WORKER_PROCESSES=$($script:observers.Count+$script:reservedWorkers). Chỉ fixture tổng hợp; không chạy USBPcap capture, không đổi USB/VM/driver/UAC."
$script:lines.Insert(0,"PowerShell $($PSVersionTable.PSVersion); UTC $([DateTimeOffset]::UtcNow.ToString('o'))")
$script:lines.Add($summary)
$script:lines.Add('API: Get-TbsObservationUsbRoots -UsbPcapPath -> danh sách {capture_device,root_path}; root_path chuẩn hóa thành \\.\USB..., luôn khám phá mới, không ghim USBPcapN.')
$script:lines.Add('API: Get-TbsObservationUsbTargets -UsbPcapPath -> danh sách {capture_device,device_address:int(1..127),connection_path,vid:"734c",pid:"5530"}; chỉ trả thiết bị đúng VID/PID.')
$script:lines.Add('API: Start-TbsWindowsObserver -PermitPath -PermitSha256 -> object {Directory:string,Process:System.Diagnostics.Process}. Thư mục cha phải tồn tại; thư mục output phải mới. reservation.json đánh dấu thư mục đã được dùng.')
$script:lines.Add('API: Get-TbsWindowsObserverStatus -Observer -> chuỗi JSON {protocol,run_id,state,alive:boolean,ready:boolean,ready_record:object|null,health:object|null,terminal:object|null}. health.ready là boolean; ready=false khi health quá 2000 ms (UTC hoặc monotonic), worker chết hoặc có terminal. Metadata cadence_target_ms=200/freshness_limit_ms=2000/cadence_misses/max_health_gap_ms không thêm required permit field.')
$script:lines.Add('API: Stop-TbsWindowsObserver -Observer -WaitSeconds 10 -> cùng cấu trúc JSON. Chỉ tạo STOP và chờ worker của chính lần chạy này; không dừng tuner hoặc client khác.')
$script:lines.Add('Permit: protocol=TBS5530-WINDOWS-OBSERVER/v1; approved và fixture phải là boolean JSON; root_grant_sha256 bắt buộc và nằm trong permit đã kiểm SHA. Caller vẫn phải xác minh quyền gốc và xin consent riêng.')
$script:lines.Add('Address yêu cầu phase=operational và device_address nguyên 1..127. NewDevices yêu cầu phase=cold, capture_mode=NewDevices, device_address=0 (hoặc null/vắng mặt), root_path từ roots mới và new_devices_only_on_selected_hub=true đúng kiểu.')
$script:lines.Add('Live yêu cầu tiến trình đã elevated, USBPcapCMD.exe 1.5.4.0 đúng nơi cài, root/target khám phá mới, output mới dưới D:\TSDuck, maximum_ms=90000, maximum_bytes=67108864, snaplen=131072, bufferlen=1048576. Component không tự mở UAC.')
$script:lines.Add('Fixture dùng cố định WindowsFixtureEmitter.ps1 và danh sách scenario cho phép; usbpcap_path phải là SYNTHETIC_FIXTURE. Giới hạn thấp dành cho fixture không đổi giới hạn live và không cho chọn lệnh tùy ý.')
$script:lines.Add('Files: reservation.json, ready.json, health.json, terminal.json, capture.pcap, STOP. PCAP được tạo mới, ghi nhị phân và kiểm cap trước mỗi lần ghi; SHA-256 được ghi sau drain. JSON dùng File.Replace để thay nguyên tử file đã có, đọc chia sẻ xóa và retry giới hạn cho lỗi chia sẻ Windows (read 80 ms/write 500 ms); hard freshness 2000 ms vẫn chạy độc lập.')
$script:lines.Add('Bằng chứng: fixture luôn SYNTHETIC_FIXTURE / measurement_valid=false. NewDevices cũng không tự xác nhận measurement_valid; parent phải giải mã danh tính target hợp lệ trước firmware. Component không phân loại MPEG-TS hoặc cấp quyền tuner.')
$script:lines.Add('Health dùng thread .NET riêng, chu kỳ 40 ms cộng thời gian ghi, mục tiêu công bố 200 ms; gap>200 được đếm cadence_misses và giữ maxgap, KHÔNG tự kill. HARD freshness cố định 2000 ms có watchdog monotonic riêng để dừng job nếu writer bị treo; không tăng trên 2000, không đổi M/kernel heartbeat, USB timeout hoặc 90 s/64 MiB. Startup publish/flush/readback hoàn tất trước ready; deadline 90 s, expiry và STOP vẫn chạy trong startup.')
$script:lines.Add('Semantics correction đã được parent duyệt ngày 2026-11-10 23:17 +07 sau evidence startup 89/537 ms và tải 208 ms. Tests 450 ms sau ready phải còn fresh và ghi cadence miss; >2000 ms phải fail-closed. EarlyExit vẫn phải CHILD_EXIT, không gộp thành lỗi health trước ready.')
$script:lines.Add('Nguồn: USBPcap tag 1.5.4.0, USBPcapCMD/cmd.c blob ecbb65860aef52fd57f8b9fad2581ca092df2feb khởi tạo capture_all/capture_new FALSE; chỉ cờ tương ứng mới bật TRUE. thread.c đặt bit zero cho capture_new; USBPcapGetDeviceUSBInfo (USBPcapDriver/USBPcapHelperFunctions.c blob 7ba4036f4e95a265bcf6ed3967e11aef02415b4b) truyền bộ lọc khi khởi tạo metadata thiết bị mới. Không lấy mặc định từ GUI.')
$script:lines.Add('SOURCE: https://github.com/desowin/usbpcap/tree/1.5.4.0 . CLI Address argv: -d <discovered> --devices <address> -o - -s 131072 -b 1048576. NewDevices argv: -d <discovered> --capture-from-new-devices -o - -s 131072 -b 1048576. Tuyệt đối không -A, --capture-from-all-devices, --inject-descriptors hoặc --init-non-standard-hwids.')
$script:lines.Add('Giới hạn: không chạy live capture, không xác minh descriptor target/coverage vật lý, không đổi UAC/USB/VM/filter/driver/module. Clock rollback được kiểm bằng lỗi tổng hợp trong fixture, không đổi đồng hồ hệ thống. Giữ nguyên mọi file ngoài phạm vi và không stage/commit/push/memory.')
foreach ($source in @('WindowsObserver.psm1','WindowsObserverWorker.ps1','UsbTopology.cs','fixtures\WindowsFixtureEmitter.ps1','tests\Test-WindowsObserver.ps1')) {
    $sourcePath=Join-Path (Split-Path $PSScriptRoot -Parent) $source
    $script:lines.Add("SHA256 $source $((Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant())")
}
if ($ReportPath) {
    $report=[Tbs5530Observation.SafePaths]::Check($ReportPath)
    if (-not [IO.Directory]::Exists([IO.Path]::GetDirectoryName($report))) { [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($report)) }
    $output=[IO.File]::Open($report,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try { $data=[Text.UTF8Encoding]::new($false).GetBytes(($script:lines -join "`r`n")+"`r`n"); $output.Write($data); $output.Flush($true) } finally { $output.Dispose() }
}
$script:lines | Write-Output
if ($script:failed -gt 0) { exit 1 }
exit 0
