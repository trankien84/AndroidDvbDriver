# SPDX-License-Identifier: GPL-2.0-only
#Requires -Version 7.5
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Write-TbsObservationJson {
    param([Parameter(Mandatory)][string]$Path,[Parameter(Mandatory)]$Value,[switch]$CreateNew)
    $text=$Value|ConvertTo-Json -Depth 24
    $bytes=[Text.UTF8Encoding]::new($false).GetBytes($text+"`n")
    if($CreateNew){$s=[IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read);try{$s.Write($bytes);$s.Flush($true)}finally{$s.Dispose()}}
    else {$temp=$Path+'.'+[guid]::NewGuid().ToString('N')+'.tmp';$s=[IO.File]::Open($temp,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read);try{$s.Write($bytes);$s.Flush($true)}finally{$s.Dispose()};[IO.File]::Move($temp,$Path,$true)}
}
function Assert-TbsObservationGrant {
    param([Parameter(Mandatory)][string]$Path,[Parameter(Mandatory)][string]$Sha256,[Parameter(Mandatory)][string]$RunId,[switch]$Fixture,[DateTimeOffset]$Now=[DateTimeOffset]::UtcNow)
    if($Sha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Sha256){throw 'OBSERVATION_GRANT_CHANGED'}
    $g=[IO.File]::ReadAllText((Resolve-Path -LiteralPath $Path).Path)|ConvertFrom-Json -DateKind String
    foreach($key in 'protocol','run_id','approved','fixture','created_utc','expires_utc','vm_uuid','guest_boot_id','capture_device','expected_root_path','source_manifest_sha256','firmware_sha256','board_sha256','allow_usb_ownership','allow_temporary_filter','allow_temporary_usbmon','allow_observers','allow_firmware_once','allow_terrestrial_receive','allow_selected_hub_new_devices','controller','maximum_phase_ms','maximum_observer_ms','maximum_observer_bytes') {if($null -eq $g.PSObject.Properties[$key]){throw ('MISSING_GRANT_FIELD_'+$key)}}
    if($RunId -cnotmatch '^phase31-layered-[0-9]{8}-[0-9]{6}-[a-f0-9]{8}$' -or $g.run_id -cne $RunId -or $g.protocol -cne 'TBS5530-LAYERED-GRANT/v1' -or $g.approved -isnot [bool] -or -not $g.approved -or $g.fixture -isnot [bool] -or $g.fixture -ne $Fixture.IsPresent){throw 'INVALID_OBSERVATION_GRANT'}
    if($g.PSObject.Properties['local_arm_notice'] -and $g.local_arm_notice -isnot [bool]){throw 'INVALID_LOCAL_NOTICE_SCOPE'}
    $startupOnly=$false;if($g.PSObject.Properties['startup_diagnostic_only']){if($g.startup_diagnostic_only -isnot [bool]){throw 'INVALID_DIAGNOSTIC_SCOPE'};$startupOnly=$g.startup_diagnostic_only}
    foreach($flag in 'allow_usb_ownership','allow_temporary_filter','allow_temporary_usbmon','allow_observers','allow_selected_hub_new_devices'){if($g.$flag -isnot [bool] -or -not $g.$flag){throw ('SCOPE_NOT_APPROVED_'+$flag)}}
    foreach($flag in 'allow_firmware_once','allow_terrestrial_receive'){if($g.$flag -isnot [bool] -or $g.$flag -ne (-not $startupOnly)){throw ('RUNTIME_SCOPE_MISMATCH_'+$flag)}}
    $created=[DateTimeOffset]::Parse($g.created_utc);$expires=[DateTimeOffset]::Parse($g.expires_utc)
    if($Now -lt $created -or $Now -ge $expires -or ($expires-$created).TotalMinutes -gt 5){throw 'OBSERVATION_GRANT_EXPIRED_OR_CLOCK_INVALID'}
    if($g.controller -cne 'xHCI_UNCHANGED' -or $g.maximum_phase_ms -is [string] -or $g.maximum_phase_ms -ne 120000 -or $g.maximum_observer_ms -is [string] -or $g.maximum_observer_ms -ne 90000 -or $g.maximum_observer_bytes -is [string] -or $g.maximum_observer_bytes -ne 67108864){throw 'OBSERVATION_LIMITS_CHANGED'}
    if($g.vm_uuid -cnotmatch '^[a-f0-9-]{36}$' -or $g.guest_boot_id -cnotmatch '^[a-f0-9-]{36}$' -or $g.firmware_sha256 -cne 'b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc' -or $g.board_sha256 -cne '5ad5512988800c2ee6954faa4fed8058f33c489f505a01ac6086b2859a2141a6'){throw 'OBSERVATION_IDENTITY_CHANGED'}
    return $g
}
function Invoke-TbsObservationCommand {
    param([Parameter(Mandatory)][string]$Executable,[string[]]$Arguments=@(),[int]$TimeoutMs=10000,[string]$LogPrefix,[switch]$AllowFailure)
    if(-not(Test-Path -LiteralPath $Executable -PathType Leaf)){throw 'OBSERVATION_EXECUTABLE_MISSING'}
    $si=[Diagnostics.ProcessStartInfo]::new();$si.FileName=$Executable;    $si.UseShellExecute=$false;$si.CreateNoWindow=$true;$si.RedirectStandardInput=$true;$si.RedirectStandardOutput=$true;$si.RedirectStandardError=$true
    foreach($a in $Arguments){$si.ArgumentList.Add($a)}
    $p=[Diagnostics.Process]::new();$p.StartInfo=$si
    try {if(-not $p.Start()){throw 'OBSERVATION_CHILD_START_FAILED'};$p.StandardInput.Close();$o=$p.StandardOutput.ReadToEndAsync();$e=$p.StandardError.ReadToEndAsync();$finished=$p.WaitForExit($TimeoutMs);if(-not $finished){Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue;[void]$p.WaitForExit(3000)};$stdout=$o.GetAwaiter().GetResult();$stderr=$e.GetAwaiter().GetResult();$code=$p.ExitCode;if($LogPrefix){[IO.File]::WriteAllText($LogPrefix+'.stdout.txt',$stdout);[IO.File]::WriteAllText($LogPrefix+'.stderr.txt',$stderr);Write-TbsObservationJson ($LogPrefix+'.meta.json') ([ordered]@{executable=$Executable;arguments=$Arguments;pid=$p.Id;completed=$finished;exit=$code;utc=[DateTimeOffset]::UtcNow.ToString('o')})};if((-not $finished -or $code -ne 0) -and -not $AllowFailure){throw ('OBSERVATION_CHILD_FAILED_'+$code)};return [pscustomobject]@{stdout=$stdout;stderr=$stderr;exit=$code;completed=$finished}}
    finally{$p.Dispose()}
}
function Invoke-TbsLayeredPhase {
    param([Parameter(Mandatory)][ValidateSet('cold','operational')][string]$Phase,[Parameter(Mandatory)][string]$Directory,[Parameter(Mandatory)][scriptblock]$Port,[int]$MaximumMs=120000)
    if($MaximumMs -ne 120000){throw 'EXACT_PHASE_LIMIT_REQUIRED'}
    $consumed=Join-Path $Directory 'phase.started';$terminal=Join-Path $Directory 'phase-terminal.json'
    if((Test-Path -LiteralPath $consumed) -or (Test-Path -LiteralPath $terminal)){throw 'PHASE_ALREADY_CONSUMED_OR_RECORDED'}
    $watch=[Diagnostics.Stopwatch]::StartNew();$started=$false;$runtime=$false;$reason='NOT_STARTED';$clean=$false;$result=$null
    try {
        if(Test-Path -LiteralPath (Join-Path $Directory 'STOP')){throw 'OPERATOR_STOP'}
        & $Port 'admit' $Phase | Out-Null
        & $Port 'observers_ready' $Phase | Out-Null
        if(Test-Path -LiteralPath (Join-Path $Directory 'STOP')){throw 'OPERATOR_STOP'}
        Write-TbsObservationJson -Path $consumed -CreateNew -Value ([ordered]@{phase=$Phase;consumed_utc=[DateTimeOffset]::UtcNow.ToString('o');retry=0});$started=$true
        $runtime=$true;& $Port 'start_runtime' $Phase | Out-Null
        while($true){
            if($watch.ElapsedMilliseconds -ge $MaximumMs){throw 'PHASE_DEADLINE'}
            if(Test-Path -LiteralPath (Join-Path $Directory 'STOP')){throw 'OPERATOR_STOP'}
            $state=& $Port 'pump' $Phase
            if($null -eq $state -or $state.observers_ok -isnot [bool] -or $state.runtime_exited -isnot [bool]){throw 'INVALID_LAYERED_PUMP_STATE'}
            if(-not $state.observers_ok -and -not($Phase -ceq 'cold' -and $state.expected_cold_transition -is [bool] -and $state.expected_cold_transition)){throw 'OBSERVER_LOST'}
            if($state.runtime_exited){$result=& $Port 'runtime_result' $Phase;$reason='RUNTIME_FINISHED';break}
            Start-Sleep -Milliseconds 80
        }
    } catch {$reason=$_.Exception.Message}
    finally {
        if($runtime){try{& $Port 'stop_runtime' $Phase|Out-Null}catch{$reason+=';STOP_REQUEST_UNCONFIRMED'}}
        try{$drain=& $Port 'drain' $Phase;$clean=$drain -is [bool] -and $drain}catch{$clean=$false}
        try{& $Port 'stop_observers' $Phase|Out-Null}catch{$clean=$false;$reason+=';OBSERVER_STOP_UNCONFIRMED'}
        try{& $Port 'collect' $Phase|Out-Null}catch{$reason+=';COLLECTION_FAILED'}
        Write-TbsObservationJson -Path $terminal -CreateNew -Value ([ordered]@{phase=$Phase;attempt_consumed=$started;runtime_started=$runtime;reason=$reason;cleanup_confirmed=$clean;runtime_result=$result;measurement_pass=$false;elapsed_ms=$watch.ElapsedMilliseconds;utc=[DateTimeOffset]::UtcNow.ToString('o')})
    }
    return [IO.File]::ReadAllText($terminal)|ConvertFrom-Json
}
Export-ModuleMember -Function Write-TbsObservationJson,Assert-TbsObservationGrant,Invoke-TbsObservationCommand,Invoke-TbsLayeredPhase
