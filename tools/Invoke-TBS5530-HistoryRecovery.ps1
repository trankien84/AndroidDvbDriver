# SPDX-License-Identifier: GPL-2.0-or-later
# One exact append-only history reconciliation. No firmware, tuner, USB permission, install or retry.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [Parameter(Mandatory)][ValidateRange(0,2147483647)][int]$ExpectedBootCount,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedApkSha256,
    [Parameter(Mandatory)][string]$CandidatePath,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$AdbPath='D:\Android\Sdk\platform-tools\adb.exe',
    [switch]$ApproveExactAppend,
    [switch]$ProofOfGuardPassed,
    [switch]$RestoreExactHostWitnessV2,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedHelperSha256
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if(-not $ApproveExactAppend -or -not $ProofOfGuardPassed){throw 'BLOCKED: cần duyệt exact append và proof-of-guard, không phải quyền hardware.'}
$before='e398459ceddb2c8f35ddc61b81ee66a31ec1aee300c33bb603dc9f593b0b31f9'
$after='fe9ab4fc788822da4553afd8f40d576ceb768b030c949a8a1b80931e77281612'
$plan='28138fcf310666a0a1d2d2b79ae98eaed8e5aae734befabdb3926af8d27f3ba7'
$protocol='TBS5530-HISTORY-APPLY/v1'
$afterBytes=747
if($RestoreExactHostWitnessV2){
    if(-not $ExpectedHelperSha256 -or (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ExpectedHelperSha256){throw 'BLOCKED: witness helper hash differs from approval.'}
    $after='b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb'
    $afterBytes=996;$protocol='TBS5530-HISTORY-WITNESS-APPLY/v2'
    $canonical='TBS5530-HISTORY-WITNESS-RECOVERY/v2|namespace=autonomous-v1-samsung-sm-x926b-api36|before='+$before+'|after='+$after+'|beforeBytes=581|afterBytes=996|beforeRecords=7|afterRecords=12|added=5|NO_USB|APPEND_ONLY|EXACT_HOST_WITNESS|FULL_HISTORY_UNPROVEN|NO_HARDWARE_AUTHORIZATION'
    $sha=[Security.Cryptography.SHA256]::Create()
    try{$plan=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical)))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
}
$package='info.martinmarinov.dvbdriver'
$history='files/autonomous-v1-samsung-sm-x926b-api36-attachments.txt'
if(-not(Test-Path -LiteralPath $AdbPath -PathType Leaf)){throw 'BLOCKED: thiếu pinned ADB.'}
$candidate=Get-Item -LiteralPath $CandidatePath
if($candidate.Length -ne $afterBytes -or (Get-FileHash -LiteralPath $candidate.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -cne $after){throw 'BLOCKED: candidate không khớp exact bytes/hash đã duyệt.'}
$output=[IO.Path]::GetFullPath($OutputDirectory)
if(-not $output.StartsWith('D:\TSDuck\logs\',[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $output)){throw 'BLOCKED: output phải là directory mới dưới D:\TSDuck\logs.'}
New-Item -ItemType Directory -Path $output|Out-Null
function Quote-Argument([string]$Value){'"'+[regex]::Replace([regex]::Replace($Value,'(\\*)"','$1$1\"'),'(\\+)$','$1$1')+'"'}
function Invoke-RecoveryAdb([string[]]$Arguments){
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$AdbPath;$info.UseShellExecute=$false
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true;$info.CreateNoWindow=$true
    $info.Arguments=((@('-s',$Serial)+$Arguments)|ForEach-Object{Quote-Argument $_}) -join ' '
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    try{
        if(-not $process.Start()){throw 'FAIL-STOP: ADB start failed.'}
        $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        if(-not $process.WaitForExit(1500)){Stop-Process -Id $process.Id -Force;throw 'FAIL-STOP: ADB deadline 1500 ms, không reconnect.'}
        $text=$stdout.GetAwaiter().GetResult();$errorText=$stderr.GetAwaiter().GetResult()
        if($process.ExitCode -ne 0){throw ('FAIL-STOP: ADB exit '+$process.ExitCode+'; không fallback.')}
        return $text.TrimEnd("`r","`n")
    }finally{$process.Dispose()}
}
function Get-HashToken([string]$Text){
    if($Text -cnotmatch '^([a-f0-9]{64})\s+'){throw 'FAIL-STOP: missing SHA256.'};return $Matches[1]
}
function Read-Private([string]$Path){Invoke-RecoveryAdb @('exec-out','run-as',$package,'cat',$Path)}
function Write-Private([string]$Path,[byte[]]$Bytes){
    if($Path -cnotmatch '^files/history-recovery-v1-[a-f0-9-]{36}/(?:candidate\.txt|grant\.json)$'){throw 'FAIL-STOP: invalid staging path.'}
    $encoded=[Convert]::ToBase64String($Bytes)
    $command='test ! -e '+$Path+' && printf %s '+$encoded+' | base64 -d > '+$Path+'.hosttmp && mv '+$Path+'.hosttmp '+$Path
    Invoke-RecoveryAdb @('shell','run-as',$package,'sh','-c',"'$command'")|Out-Null
}
function Test-InitUsbInventoryEmpty([string]$Dump) {
    $hosts=[regex]::Matches($Dump,'(?ms)^  host_manager=\{\r?\n(?<body>.*?)^  \}[ \t]*\r?$')
    if($hosts.Count -ne 1){return $false}
    $body=$hosts[0].Groups['body'].Value
    $counts=[regex]::Matches($body,'(?m)^    num_connects=(0|[1-9][0-9]*)[ \t]*\r?\n')
    if($counts.Count -ne 1 -or $body.Substring(0,$counts[0].Index).Trim().Length -ne 0){return $false}
    # AOSP writes current devices before num_connects; connections after it is history.
    $tail=$body.Substring($counts[0].Index+$counts[0].Length)
    if($tail.Length -eq 0){return $counts[0].Groups[1].Value -ceq '0'}
    $history=[regex]::Match($tail,'\A    connections=\[\r?\n(?<records>(?: {6}[^\r\n]*\r?\n)*)    \]\r?\n\z')
    if(-not $history.Success){return $false}
    $records=$history.Groups['records'].Value
    $depth=0
    foreach($brace in [regex]::Matches($records,'[{}\[\]]')){
        if($brace.Value -ceq '{'){$depth++}
        elseif($brace.Value -ceq '}'){$depth--;if($depth -lt 0){return $false}}
        else{return $false}
    }
    if($depth -ne 0){return $false}
    return ($records.Trim().Length -eq 0 -and $counts[0].Groups[1].Value -ceq '0') -or
        ($records.Trim().Length -gt 0 -and $counts[0].Groups[1].Value -cne '0' -and $records.Trim().StartsWith('{') -and $records.Trim().EndsWith('}'))
}
function Assert-ReadOnlyIdentity {
    if((Invoke-RecoveryAdb @('shell','getprop','ro.product.model')) -cne 'SM-X926B' -or
        (Invoke-RecoveryAdb @('shell','getprop','ro.build.version.sdk')) -cne '36' -or
        [int](Invoke-RecoveryAdb @('shell','settings','get','global','boot_count')) -ne $ExpectedBootCount){throw 'BLOCKED: model/API/boot mismatch.'}
    $usb=Invoke-RecoveryAdb @('shell','dumpsys','usb')
    if(-not(Test-InitUsbInventoryEmpty $usb)){throw 'BLOCKED: recovery cần host USB rỗng và schema đã xác minh.'}
}
Assert-ReadOnlyIdentity
$baseline=Read-Private $history
$bytes=[Text.Encoding]::UTF8.GetBytes($baseline+"`n")
$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
if($bytes.Length -ne 581 -or $hash -cne $before -or (Get-HashToken (Invoke-RecoveryAdb @('shell','run-as',$package,'sha256sum',$history))) -cne $before){throw 'BLOCKED: history baseline đã đổi; không ghi.'}
[IO.File]::WriteAllBytes((Join-Path $output 'history-before.txt'),$bytes)
$launch=[guid]::NewGuid().ToString()
$launchArgs=@('shell','am','start','--activity-clear-top','--activity-single-top','-n',"$package/.Tbs5530HistoryRecoveryActivity",'--es','launch_token',$launch)
if($RestoreExactHostWitnessV2){$launchArgs+=@('--es','recovery_mode','EXACT_HOST_WITNESS_V2')}
Invoke-RecoveryAdb $launchArgs|Set-Content (Join-Path $output 'launch.txt')
$watch=[Diagnostics.Stopwatch]::StartNew();$challenge=$null
while($watch.Elapsed.TotalSeconds -lt 10){
    $reply=Invoke-RecoveryAdb @('shell','run-as',$package,'sh','-c',"'if test -f files/history-recovery-v1-challenge.json; then cat files/history-recovery-v1-challenge.json; else echo WAIT; fi'")
    if($reply -cne 'WAIT'){$value=$reply|ConvertFrom-Json;if($value.launch_token -ceq $launch){$challenge=$value;break}}
    Start-Sleep -Milliseconds 100
}
if(-not $challenge -or $challenge.protocol -cne $protocol -or $challenge.model -cne 'SM-X926B' -or
    $challenge.api -ne 36 -or $challenge.boot_count -ne $ExpectedBootCount -or $challenge.apk_sha256 -cne $ExpectedApkSha256 -or
    $challenge.plan_sha256 -cne $plan -or $challenge.before_sha256 -cne $before -or $challenge.after_sha256 -cne $after -or
    $challenge.before_bytes -ne 581 -or $challenge.after_bytes -ne $afterBytes -or $challenge.session -cnotmatch '^[a-f0-9-]{36}$'){throw 'BLOCKED: challenge không khớp exact scope.'}
$directory='files/history-recovery-v1-'+$challenge.session
$challenge|ConvertTo-Json -Depth 6|Set-Content (Join-Path $output 'challenge.json')
Write-Private ($directory+'/candidate.txt') ([IO.File]::ReadAllBytes($candidate.FullName))
$grant=@{};$challenge.PSObject.Properties|ForEach-Object{$grant[$_.Name]=$_.Value}
$grant.append_only_authorized=$true;$grant.no_tuner_confirmed=$true;$grant.hardware_authorized=$false;$grant.full_history_proven=$false
$grant.expires_ms=[long]$challenge.created_ms+120000L
$grant.host_script_sha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
$approvedBytes=[Text.Encoding]::UTF8.GetBytes(($grant|ConvertTo-Json -Depth 6))
$hostJournal=[IO.FileStream]::new((Join-Path $output 'grant-approved.json'),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
try{$hostJournal.Write($approvedBytes,0,$approvedBytes.Length);$hostJournal.Flush($true)}finally{$hostJournal.Dispose()}
function New-RecoveryHeartbeatCommand([string]$Session,[bool]$Stop){
    if($Session -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$'){throw 'FAIL-STOP: recovery session malformed.'}
    $inner=@'
set -eu
[ "$#" -eq 1 ] || exit 64
up=$1
whole=${up%.*}
frac=${up#*.}
case $whole in ""|*[!0-9]*) exit 64;; esac
case $frac in ""|*[!0-9]*) exit 64;; esac
[ "$whole.$frac" = "$up" ] || exit 64
[ "${#whole}" -le 10 ] || exit 64
frac=${frac}000
frac=$(printf %.3s "$frac")
elapsed=$((whole * 1000 + 1$frac - 1000))
printf '{"session":"@SESSION@","elapsed_ms":%s,"stop":@STOP@}' "$elapsed" > files/history-recovery-v1-@SESSION@/heartbeat.hosttmp
mv files/history-recovery-v1-@SESSION@/heartbeat.hosttmp files/history-recovery-v1-@SESSION@/heartbeat.json
printf '{"elapsed_ms":%s,"report":' "$elapsed"
if test -f files/history-recovery-v1-@SESSION@/report.json; then cat files/history-recovery-v1-@SESSION@/report.json; else printf null; fi
printf '}\n'
'@
    $inner=$inner.Replace('@SESSION@',$Session).Replace('@STOP@',$Stop.ToString().ToLowerInvariant()).Replace("`r`n","`n")
    $quote=[string][char]39;$splice=$quote+[char]34+$quote+[char]34+$quote
    $quoted=$quote+$inner.Replace($quote,$splice)+$quote
    # The shell UID reads /proc/uptime; the app UID only writes its own private files.
    $outer=@'
set -eu
IFS=" " read -r uptime idle < /proc/uptime
exec run-as info.martinmarinov.dvbdriver sh -c @INNER@ heartbeat "$uptime"
'@
    return $outer.Replace('@INNER@',$quoted).Replace("`r`n","`n")
}
function Exchange-Recovery([bool]$Stop){
    $clock=[Diagnostics.Stopwatch]::StartNew()
    $reply=Invoke-RecoveryAdb @('shell',(New-RecoveryHeartbeatCommand $challenge.session $Stop))|ConvertFrom-Json
    if($clock.ElapsedMilliseconds -ge 1500 -or ($reply.elapsed_ms -isnot [long] -and $reply.elapsed_ms -isnot [int]) -or
        $reply.elapsed_ms -lt [long]$challenge.created_ms -or (-not $Stop -and $reply.elapsed_ms -ge [long]$grant.expires_ms)){
        throw 'FAIL-STOP: heartbeat clock/expiry không khớp; không publish grant.'
    }
    if($null -eq $reply.report){return 'WAIT'}
    return $reply.report|ConvertTo-Json -Compress -Depth 8
}
$report=$null;$granted=$false
try{
    Exchange-Recovery $false|Out-Null
    Write-Private ($directory+'/grant.json') ([Text.Encoding]::UTF8.GetBytes(($grant|ConvertTo-Json -Compress -Depth 6)))
    $granted=$true;$watch.Restart()
    while($watch.Elapsed.TotalSeconds -lt 120){
        $reply=Exchange-Recovery $false
        if($reply.StartsWith('{')){$report=$reply|ConvertFrom-Json;break};Start-Sleep -Milliseconds 100
    }
}finally{if($granted){try{Exchange-Recovery $true|Out-Null}catch{Write-Warning 'Không liên lạc; app phải tự dừng theo heartbeat. Không reconnect/retry.'}}}
if(-not $report){throw 'FAIL-STOP: thiếu terminal, không retry hoặc rollback.'}
$report|ConvertTo-Json -Depth 6|Set-Content (Join-Path $output 'report.json')
$observedHash=Get-HashToken (Invoke-RecoveryAdb @('shell','run-as',$package,'sha256sum',$history))
@{observed_sha256=$observedHash;report_sha256=$report.history_sha256;hardware_authorized=$false;full_history_proven=$false}|ConvertTo-Json|Set-Content (Join-Path $output 'host-after.json')
if($report.launch_token -cne $launch -or $report.session -cne $challenge.session -or $report.terminal -isnot [bool] -or -not $report.terminal -or
    $report.result -cne 'APPEND_VERIFIED_NO_HARDWARE_AUTHORIZATION' -or $report.consumed -isnot [bool] -or -not $report.consumed -or
    $report.history_sha256 -cne $after -or $observedHash -cne $after -or $report.usb_io_count -ne 0 -or
    $report.hardware_authorized -isnot [bool] -or $report.hardware_authorized -or $report.full_history_proven -isnot [bool] -or $report.full_history_proven){throw 'FAIL-STOP: recovery chưa verified; giữ evidence, không retry/rollback.'}
$final=Read-Private $history
[IO.File]::WriteAllText((Join-Path $output 'history-after.txt'),$final+"`n",[Text.UTF8Encoding]::new($false))
if((Get-FileHash (Join-Path $output 'history-after.txt')).Hash.ToLowerInvariant() -cne $after){throw 'FAIL-STOP: host export hash mismatch.'}
if($RestoreExactHostWitnessV2){Write-Output 'APPEND_VERIFIED: exact host witness 7→12; không firmware, không quyền B2, không full-history proof.'}
else{Write-Output 'APPEND_VERIFIED: 7→9 deny records; không firmware, không quyền B1, không full-history proof.'}
