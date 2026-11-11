#Requires -Version 7.5
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ReportDirectory,
    [switch]$Legacy,
    [switch]$LongGap,
    [switch]$DefaultHeader,
    [ValidateRange(1,8)][int]$Rounds=6
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$module=Import-Module (Join-Path (Split-Path $PSScriptRoot -Parent) 'WindowsObserver.psm1') -Force -PassThru
& $module { Initialize-TbsObserverTypes }
$ReportDirectory=[Tbs5530Observation.SafePaths]::Check($ReportDirectory)
[Tbs5530Observation.SafePaths]::CreateNewDirectory($ReportDirectory)
Add-Type -TypeDefinition @'
using System;
using System.Diagnostics;
using System.Threading;
namespace Tbs5530Observation.Fixtures {
    public sealed class WindowsStartupLoad : IDisposable {
        private readonly CancellationTokenSource cancel = new CancellationTokenSource();
        private readonly Thread[] threads;
        private long iterations;
        public long Iterations { get { return Interlocked.Read(ref iterations); } }
        public WindowsStartupLoad(int count) {
            threads = new Thread[count];
            for (int i = 0; i < count; i++) {
                threads[i] = new Thread(() => {
                    var clock = Stopwatch.StartNew();
                    while (!cancel.IsCancellationRequested && clock.ElapsedMilliseconds < 90000) {
                        Thread.SpinWait(10000);
                        Interlocked.Increment(ref iterations);
                    }
                }) { IsBackground = true, Name = "Windows observer fixture CPU load" };
                threads[i].Start();
            }
        }
        public void Dispose() {
            cancel.Cancel();
            foreach (Thread thread in threads) if (!thread.Join(3000)) throw new Exception("Fixture CPU load did not drain");
            cancel.Dispose();
        }
    }
}
'@
$lines=[Collections.Generic.List[string]]::new()
$observers=[Collections.Generic.List[object]]::new()
$failures=0
$load=$null
$start=[DateTimeOffset]::UtcNow
$clock=[Diagnostics.Stopwatch]::StartNew()
$self=[Diagnostics.Process]::GetCurrentProcess()
$cpuStart=$self.TotalProcessorTime.TotalMilliseconds
try {
    $count=[Math]::Min(2,[Math]::Max(1,[Environment]::ProcessorCount-2))
    $load=[Tbs5530Observation.Fixtures.WindowsStartupLoad]::new($count)
    $lines.Add("LOAD threads=$count processors=$([Environment]::ProcessorCount) bounded_ms=90000")
    for ($i=0;$i -lt $Rounds;$i++) {
        $scenario=if ($DefaultHeader) {'HeaderIdle'} elseif ($LongGap) {'ReadyLongDelay'} elseif ($Legacy) {'StartupDelay'} else {@('ImmediateHeader','StartupDelay','ReadyDelay')[$i%3]}
        $name='WindowsStartup-'+$i
        $path=Join-Path $ReportDirectory ($name+'.permit.json')
        $permit=@{
            protocol='TBS5530-WINDOWS-OBSERVER/v1'; run_id=$name; phase='cold'; approved=$true; fixture=$true
            root_grant_sha256=('e'*64); expires_utc=[DateTimeOffset]::UtcNow.AddMinutes(2).ToString("yyyy-MM-ddTHH:mm:ss.fffffffZ")
            capture_mode='NewDevices'; capture_device=('\\.\USBPcap'+(2000+$i)); device_address=0
            root_path='\\.\USB#ROOT_HUB30#SYNTHETIC_STARTUP'; new_devices_only_on_selected_hub=$true
            usbpcap_path='SYNTHETIC_FIXTURE'; output_directory=(Join-Path $ReportDirectory $name)
            maximum_ms=25000; maximum_bytes=67108864; snaplen=131072; bufferlen=1048576; fixture_case=$scenario
        }
        [IO.File]::WriteAllText($path,($permit|ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
        $hash=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        $observer=Start-TbsWindowsObserver -PermitPath $path -PermitSha256 $hash
        $observers.Add($observer)
        $wait=[Diagnostics.Stopwatch]::StartNew(); $readyAt=-1L; $status=$null; $unresponsive=0
        while ($wait.ElapsedMilliseconds -lt 20000) {
            $status=Get-TbsWindowsObserverStatus $observer | ConvertFrom-Json -DateKind String
            if ($null -ne $status.terminal) {break}
            if (-not $status.alive) {throw 'Fixture worker exited without terminal'}
            if ($status.ready -and $readyAt -lt 0) {$readyAt=$wait.ElapsedMilliseconds}
            if ($readyAt -ge 0 -and $status.state -ceq 'unresponsive') {$unresponsive++}
            if (-not $Legacy -and $scenario -cne 'ReadyLongDelay' -and $readyAt -ge 0 -and $wait.ElapsedMilliseconds-$readyAt -ge 1500) {break}
            Start-Sleep -Milliseconds 20
        }
        if ($null -eq $status.terminal) {$status=Stop-TbsWindowsObserver $observer -WaitSeconds 10 | ConvertFrom-Json -DateKind String}
        if (-not $observer.Process.WaitForExit(3000) -or $null -eq $status.terminal) {throw 'Fixture worker did not stop with terminal'}
        $terminal=$status.terminal
        $safe=$terminal.cleanup_confirmed -and $terminal.evidence_layer -ceq 'SYNTHETIC_FIXTURE' -and -not $terminal.measurement_valid
        if ($Legacy) {
            $okay=$safe -and $terminal.reason -ceq 'HEALTH_DEADLINE_MISSED' -and $terminal.max_health_gap_ms -ge 450 -and
                $terminal.ready_observed -and $terminal.health_ready_requested_ms -lt $terminal.health_startup_ms
        } elseif ($scenario -ceq 'ReadyLongDelay') {
            $okay=$safe -and $terminal.reason -ceq 'HEALTH_DEADLINE_MISSED' -and $terminal.ready_observed -and
                $terminal.max_health_gap_ms -gt 2000 -and $observer.Process.ExitCode -eq 2
        } elseif ($scenario -ceq 'ReadyDelay') {
            $okay=$safe -and $terminal.reason -ceq 'STOP_REQUESTED' -and $readyAt -ge 0 -and $terminal.cadence_misses -ge 1 -and
                $terminal.max_health_gap_ms -ge 450 -and $terminal.max_health_gap_ms -le 2000 -and $unresponsive -eq 0
        } else {
            $okay=$safe -and $terminal.reason -ceq 'STOP_REQUESTED' -and $readyAt -ge 0 -and
                $terminal.max_health_gap_ms -le 2000 -and $terminal.health_ready_requested_ms -ge $terminal.health_startup_ms -and
                $terminal.header_valid -and $terminal.capture_bytes -eq 24 -and $unresponsive -eq 0
        }
        if (-not $okay) {$failures++}
        $lines.Add("$(if($okay){'PASS'}else{'FAIL'}) round=$i scenario=$scenario reason=$($terminal.reason) ready_observed=$($terminal.ready_observed) startup_ms=$($terminal.health_startup_ms) first_publish_ms=$($terminal.health_first_publish_ms) first_read_ms=$($terminal.health_first_read_ms) ready_requested_ms=$($terminal.health_ready_requested_ms) active_maxgap_ms=$($terminal.max_health_gap_ms) unresponsive=$unresponsive cleanup=$($terminal.cleanup_confirmed)")
        foreach ($childId in $terminal.owned_child_pids) {if ($null -ne (Get-Process -Id $childId -ErrorAction SilentlyContinue)) {throw 'Owned recorder survived cleanup'}}
    }
} catch {
    $failures++
    $lines.Add("FAIL $($_.Exception.Message)")
    $lines.Add($_.ScriptStackTrace)
} finally {
    foreach ($observer in $observers) {try {if (-not $observer.Process.HasExited) {[void](Stop-TbsWindowsObserver $observer)}}catch{$failures++}}
    if ($null -ne $load) {$iterations=$load.Iterations; $load.Dispose()} else {$iterations=0}
}
$self.Refresh()
$lines.Add("CPU_LOAD iterations=$iterations runner_cpu_ms=$([Math]::Round($self.TotalProcessorTime.TotalMilliseconds-$cpuStart)) wall_ms=$($clock.ElapsedMilliseconds) drained=true")
$lines.Add("Kết quả startup fixture: workers=$($observers.Count) failures=$failures legacy=$Legacy. Không capture thật, không UAC hoặc đổi hardware. STARTED_UTC=$($start.ToString('o'))")
foreach ($source in @('UsbTopology.cs','WindowsObserverWorker.ps1','WindowsObserver.psm1','fixtures\WindowsFixtureEmitter.ps1','fixtures\WindowsStartupProof.ps1')) {
    $lines.Add("SHA256 $source $((Get-FileHash -LiteralPath (Join-Path (Split-Path $PSScriptRoot -Parent) $source)).Hash.ToLowerInvariant())")
}
[Tbs5530Observation.SafePaths]::WriteJson((Join-Path $ReportDirectory 'result.json'),(@{workers=$observers.Count;failures=$failures;legacy=[bool]$Legacy;lines=@($lines)}|ConvertTo-Json -Depth 4),$true)
[IO.File]::WriteAllLines((Join-Path $ReportDirectory 'startup-tests.txt'),$lines,[Text.UTF8Encoding]::new($false))
$lines | Write-Output
if ($failures -gt 0) {exit 1}
exit 0
