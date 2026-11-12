# SPDX-License-Identifier: GPL-2.0-or-later
# Prepare OS permission without device I/O, then start the unchanged B2 helper with its original TTL.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [Parameter(Mandatory)][int]$ExpectedBootCount,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedAppApkSha256,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedCoordinatorSha256,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedAttachmentHistorySha256,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedReloadStoreSha256,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedReloadAnchorSha256,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$')][string]$PhysicalRecoveryEpoch,
    [Parameter(Mandatory)][string]$FirmwarePath,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$AdbPath='D:\Android\Sdk\platform-tools\adb.exe',
    [switch]$PhysicalRecoveryConfirmed,
    [switch]$ApproveOneNewB2,
    [switch]$ProofOfGuardPassed,
    [switch]$OfflinePreflight
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$runtime=Join-Path $PSScriptRoot 'Invoke-TBS5530-AutonomousSession.ps1'
$runtimeHash='ea30d2911034d1f3ea6cf3929f078e2dff1a1df84c7b2450ec0b4feadcce76bd'
$package='info.martinmarinov.dvbdriver'
$script:preparationCommand=0
function Test-PermissionFirstReady([object]$Value,[string]$Token,[long]$Now){
    if($null -eq $Value){return $false}
    foreach($key in @('version','mode','preflight_token','state','reason','vid','pid','created_elapsed_ms','deadline_elapsed_ms','elapsed_ms','device_identity_sha256','attachment_generation')){
        if($null -eq $Value.PSObject.Properties[$key]){return $false}
    }
    foreach($key in @('created_elapsed_ms','deadline_elapsed_ms','elapsed_ms','attachment_generation')){
        if($Value.$key -isnot [long] -and $Value.$key -isnot [int]){return $false}
    }
    return $Value.version -ceq 1 -and $Value.mode -ceq 'PERMISSION_ONLY' -and $Value.preflight_token -ceq $Token -and
        $Value.state -ceq 'GRANTED' -and $Value.reason -ceq 'OS_PERMISSION_VERIFIED' -and $Value.vid -ceq 29516 -and $Value.pid -ceq 21808 -and
        $Value.created_elapsed_ms -ge 0 -and $Value.deadline_elapsed_ms - $Value.created_elapsed_ms -eq 60000 -and
        $Value.elapsed_ms -ge $Value.created_elapsed_ms -and $Now -ge $Value.elapsed_ms -and $Now - $Value.elapsed_ms -lt 2000 -and
        $Value.elapsed_ms -lt $Value.deadline_elapsed_ms -and $Value.attachment_generation -ge 0 -and
        $Value.device_identity_sha256 -is [string] -and $Value.device_identity_sha256 -cmatch '^[a-f0-9]{64}$'
}
function Invoke-PreparationAdb([string[]]$Arguments){
    $info=[Diagnostics.ProcessStartInfo]::new($AdbPath);$info.UseShellExecute=$false
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($arg in @('-s',$Serial)+$Arguments){$info.ArgumentList.Add($arg)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    try{
        if(-not $process.Start()){throw 'PERMISSION_FIRST_ADB_START_FAILED'}
        $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        if(-not $process.WaitForExit(1500)){Stop-Process -Id $process.Id -Force;throw 'PERMISSION_FIRST_ADB_TIMEOUT_NO_RETRY'}
        $text=$stdout.GetAwaiter().GetResult();$null=$stderr.GetAwaiter().GetResult()
        if($process.ExitCode -ne 0){throw 'PERMISSION_FIRST_ADB_NONZERO_NO_RETRY'}
        $script:preparationCommand++
        [IO.File]::WriteAllText((Join-Path $output ('permission-command-'+$script:preparationCommand.ToString('D3')+'.private.txt')),$text,[Text.UTF8Encoding]::new($false))
        return $text.TrimEnd("`r","`n")
    }finally{$process.Dispose()}
}
function Assert-PermissionFirstStoredPins([string]$History,[string]$Store,[string]$Anchor){
    foreach($pin in @(@('files/autonomous-v1-samsung-sm-x926b-api36-attachments.txt',$History),@('no_backup/fx2-reload-v1.txt',$Store),@('no_backup/fx2-reload-v1.anchor',$Anchor))){
        $observed=Invoke-PreparationAdb @('shell','run-as',$package,'sha256sum',$pin[0])
        if($observed -cnotmatch ('^'+$pin[1]+'\s')){throw 'PERMISSION_FIRST_APPROVED_HISTORY_OR_STORE_MISMATCH'}
    }
}
function Get-PreparationElapsed {
    $up=Invoke-PreparationAdb @('shell','cat','/proc/uptime')
    if($up -cnotmatch '^([0-9]+\.[0-9]+)\s+[0-9]+\.[0-9]+$'){throw 'PERMISSION_FIRST_CLOCK_UNVERIFIED'}
    return [long][Math]::Floor([double]::Parse($Matches[1],[Globalization.CultureInfo]::InvariantCulture)*1000)
}
function Wait-PermissionFirst([string]$Token){
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while($watch.ElapsedMilliseconds -lt 60000){
        $command='if test -f files/permission-preflight.json; then cat files/permission-preflight.json; else printf WAIT; fi'
        $text=Invoke-PreparationAdb @('shell','run-as',$package,'sh','-c',("'"+$command+"'"))
        if($text -cne 'WAIT'){
            $value=$text|ConvertFrom-Json
            if($value.preflight_token -ceq $Token){
                if($value.state -cin @('DENIED','CANCELLED','TIMED_OUT','UNVERIFIED')){throw 'PERMISSION_ONLY_REFUSED_NO_B2_GRANT'}
                if($value.state -ceq 'GRANTED'){
                    if(-not(Test-PermissionFirstReady $value $Token (Get-PreparationElapsed))){throw 'PERMISSION_ONLY_RECEIPT_STALE_OR_MALFORMED'}
                    return $value
                }
                if($value.state -cnotin @('WAITING','USER_ACTION_REQUIRED')){throw 'PERMISSION_ONLY_UNKNOWN_STATE'}
            }
        }
        Start-Sleep -Milliseconds 100
    }
    throw 'PERMISSION_ONLY_60S_TIMEOUT_NO_B2_GRANT'
}
if((Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ExpectedCoordinatorSha256 -or
    (Get-FileHash -LiteralPath $runtime -Algorithm SHA256).Hash.ToLowerInvariant() -cne $runtimeHash){throw 'PERMISSION_FIRST_HELPER_HASH_MISMATCH'}
if((Get-Item -LiteralPath $FirmwarePath).Length -ne 8192 -or (Get-FileHash -LiteralPath $FirmwarePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne 'b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc'){throw 'PERMISSION_FIRST_FIRMWARE_HASH_MISMATCH'}
if($OfflinePreflight){Write-Output 'OFFLINE_PREFLIGHT_PASS: no ADB, permission Activity, B2 grant or device I/O.';return}
if(-not $ApproveOneNewB2 -or -not $ProofOfGuardPassed -or -not $PhysicalRecoveryConfirmed -or $PhysicalRecoveryEpoch -ceq '00000000-0000-0000-0000-000000000000'){throw 'PERMISSION_FIRST_EXACT_APPROVAL_AND_PHYSICAL_CONFIRMATION_REQUIRED'}
if(-not(Test-Path -LiteralPath $AdbPath -PathType Leaf)){throw 'PERMISSION_FIRST_PINNED_ADB_MISSING'}
$output=[IO.Path]::GetFullPath($OutputDirectory)
if(-not $output.StartsWith('D:\TSDuck\logs\',[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $output)){throw 'PERMISSION_FIRST_NEW_OUTPUT_REQUIRED'}
$null=New-Item -ItemType Directory -Path $output
if((Invoke-PreparationAdb @('get-state')) -cne 'device' -or (Invoke-PreparationAdb @('shell','getprop','ro.product.model')) -cne 'SM-X926B' -or
    (Invoke-PreparationAdb @('shell','getprop','ro.build.version.sdk')) -cne '36' -or [int](Invoke-PreparationAdb @('shell','settings','get','global','boot_count')) -ne $ExpectedBootCount){throw 'PERMISSION_FIRST_IDENTITY_MISMATCH'}
$power=Invoke-PreparationAdb @('shell','dumpsys','power')
if($power -cnotmatch '(?m)^\s*mWakefulness=Awake\r?$'){throw 'SCREEN_NOT_AWAKE_USER_ACTION_REQUIRED'}
$apk=Invoke-PreparationAdb @('shell','pm','path',$package)
if($apk -cnotmatch '^package:(/[^\r\n]+\.apk)$'){throw 'PERMISSION_FIRST_APK_PATH_UNVERIFIED'}
$path=$Matches[1];$apkHash=Invoke-PreparationAdb @('shell','sha256sum',$path)
if($apkHash -cnotmatch ('^'+$ExpectedAppApkSha256+'\s')){throw 'PERMISSION_FIRST_APK_HASH_MISMATCH'}
Assert-PermissionFirstStoredPins $ExpectedAttachmentHistorySha256 $ExpectedReloadStoreSha256 $ExpectedReloadAnchorSha256
$token=[guid]::NewGuid().ToString()
$null=Invoke-PreparationAdb @('shell','am','start','--activity-clear-top','--activity-single-top','-n',"$package/.Tbs5530PermissionPreflightActivity",'--es','preflight_token',$token)
Write-Output 'PERMISSION_ONLY: user may approve USB; no B2 session TTL or tuner I/O has started.'
$ready=Wait-PermissionFirst $token
$ready|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $output 'permission-ready.private.json') -Encoding utf8
# The next child creates its challenge/grant only now; never extend an already-started session.
$dispatch=[IO.FileStream]::new((Join-Path $output 'b2-dispatch.once'),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
try{$bytes=[Text.Encoding]::UTF8.GetBytes('FRESH_PERMISSION_VERIFIED_BEFORE_B2_SESSION');$dispatch.Write($bytes,0,$bytes.Length);$dispatch.Flush($true)}finally{$dispatch.Dispose()}
$child=Join-Path $output 'runtime';$null=New-Item -ItemType Directory -Path $child
& $runtime -Serial $Serial -ExpectedModel 'SM-X926B' -ExpectedApi 36 -ExpectedBootCount $ExpectedBootCount -Profile EXPERIMENTAL_DIAGNOSTIC -ReadApi NATIVE_USBFS_URB_8X4096 -DurationSeconds 300 -FirmwarePath $FirmwarePath -PhysicalRecoveryEpoch $PhysicalRecoveryEpoch -PhysicalRecoveryConfirmed -AdbPath $AdbPath -GrantBoundedSession -ProofOfGuardPassed -ControlMirror -FrontendExperiment B2_RELOAD_AFTER_SET_CONFIG -ExpectedReloadPlanSha256 '9add00e12a7fbea9aa4877451db4a28077c64872cd73e6eb91f04facf1fec650' -ExpectedReloadStoreSha256 $ExpectedReloadStoreSha256 -ExpectedReloadAnchorSha256 $ExpectedReloadAnchorSha256 -ExpectedHelperSha256 $runtimeHash -ExpectedFrontendGoldenSha256 '75875b84fc3f959b35d436560ae19ccf7e52b890173f1f5e24984367f64fe228' -ExpectedAppApkSha256 $ExpectedAppApkSha256 -ExpectedAttachmentHistorySha256 $ExpectedAttachmentHistorySha256 -OutputDirectory $child
exit $LASTEXITCODE
