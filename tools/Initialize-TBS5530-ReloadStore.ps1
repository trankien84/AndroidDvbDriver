# SPDX-License-Identifier: GPL-2.0-or-later
# Exact first initialization through the debug caller only; no USB open, install, retry or direct store write.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [Parameter(Mandatory)][ValidateRange(0,2147483647)][int]$ExpectedBootCount,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedApkSha256,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedHelperSha256,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedPlanSha256,
    [Parameter(Mandatory)][string]$TemplateDirectory,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$AdbPath='D:\Android\Sdk\platform-tools\adb.exe',
    [switch]$ApproveExactFirstInitialization,
    [switch]$ProofOfGuardPassed,
    [switch]$OfflinePreflight
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$history='b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb'
$store='8ac0c7dbfcc4240bbb0e36d83faf5c073bafd0345133def28d17ee568f6d32e3'
$anchor='63de1971b15ea4b5d66b08697cee4a5a1c450260fb5e21a58d2b28afd43b31c4'
$marker='6be60f6d30960aebaec234eb059d666f0d785fed4c3793bb952b365caf1ef6a2'
$protocol='TBS5530-RELOAD-INITIALIZE/v1'
function Get-InitSha([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function Get-ReloadInitializationPlan {
    return $protocol+'|host=SM-X926B:36|history='+$history+'|historyBytes=996|historyRecords=12'+
        '|store='+$store+'|storeBytes=138|anchor='+$anchor+'|anchorBytes=94|marker='+$marker+
        '|markerBytes=99|order=APPROVAL_TOMBSTONE,MARKER,JOURNAL,ANCHOR|O_EXCL|FSYNC_FILE_AND_DIRECTORY'+
        '|NO_TUNER|NO_DEVICE_OPEN|hardwareAuthorized=false|retry=0|reset=false|rearm=false'
}
function Test-ReloadInitializationTemplates {
    $expected=@(
        @('b2-reload-store-template.txt',138,$store), @('b2-reload-anchor-template.txt',94,$anchor),
        @('b2-reload-initialized-template.txt',99,$marker)
    )
    foreach($item in $expected){
        $file=Get-Item -LiteralPath (Join-Path $TemplateDirectory $item[0])
        if($file.PSIsContainer -or ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $file.Length -ne $item[1] -or
            (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -cne $item[2]) {
            throw 'BLOCKED: template không khớp exact bytes/hash hoặc không phải tệp thường.'
        }
    }
    if((Get-InitSha ([Text.Encoding]::UTF8.GetBytes((Get-ReloadInitializationPlan)))) -cne $ExpectedPlanSha256){
        throw 'BLOCKED: initialization plan khác hash được duyệt.'
    }
    if((Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ExpectedHelperSha256){
        throw 'BLOCKED: initializer helper khác hash được duyệt.'
    }
}
Test-ReloadInitializationTemplates
if($OfflinePreflight){
    [pscustomobject]@{result='OFFLINE_PREFLIGHT_PASS_NO_DEVICE_ACCESS';plan_sha256=$ExpectedPlanSha256;helper_sha256=$ExpectedHelperSha256;
        history_sha256=$history;store_sha256=$store;anchor_sha256=$anchor;marker_sha256=$marker;hardware_authorized=$false;store_initialized=$false}|ConvertTo-Json
    return
}
if(-not $ApproveExactFirstInitialization -or -not $ProofOfGuardPassed){throw 'BLOCKED: cần exact first-initialization approval và proof-of-guard riêng; không phải quyền hardware.'}
if(-not(Test-Path -LiteralPath $AdbPath -PathType Leaf)){throw 'BLOCKED: thiếu pinned ADB.'}
$output=[IO.Path]::GetFullPath($OutputDirectory)
if(-not $output.StartsWith('D:\TSDuck\logs\',[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $output)){throw 'BLOCKED: output phải là thư mục mới dưới D:\TSDuck\logs.'}
New-Item -ItemType Directory -Path $output|Out-Null
$package='info.martinmarinov.dvbdriver'
function Quote-InitArgument([string]$Value){'"'+[regex]::Replace([regex]::Replace($Value,'(\\*)"','$1$1\"'),'(\\+)$','$1$1')+'"'}
function Invoke-InitAdb([string[]]$Arguments){
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$AdbPath;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $info.Arguments=((@('-s',$Serial)+$Arguments)|ForEach-Object{Quote-InitArgument $_}) -join ' '
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    try{
        if(-not $process.Start()){throw 'FAIL-STOP: ADB start failed.'}
        $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        if(-not $process.WaitForExit(1500)){Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue;throw 'FAIL-STOP: ADB deadline 1500 ms; không reconnect/retry.'}
        $text=$stdout.GetAwaiter().GetResult();$null=$stderr.GetAwaiter().GetResult()
        if($process.ExitCode -ne 0){throw 'FAIL-STOP: ADB nonzero exit; không fallback.'}
        return $text.TrimEnd("`r","`n")
    }finally{$process.Dispose()}
}
function Get-InitHashToken([string]$Text){if($Text -cnotmatch '^([a-f0-9]{64})\s+'){throw 'FAIL-STOP: thiếu SHA256.'};return $Matches[1]}
function Assert-InitIdentity {
    if((Invoke-InitAdb @('get-state')) -cne 'device' -or (Invoke-InitAdb @('shell','getprop','ro.product.model')) -cne 'SM-X926B' -or
        (Invoke-InitAdb @('shell','getprop','ro.build.version.sdk')) -cne '36' -or
        [int](Invoke-InitAdb @('shell','settings','get','global','boot_count')) -ne $ExpectedBootCount){throw 'BLOCKED: model/API/boot khác card.'}
    $usb=Invoke-InitAdb @('shell','dumpsys','usb');$block=[regex]::Match($usb,'(?ms)^  host_manager=\{.*?^  \}').Value
    if(-not $block -or $block -match 'vendor_id=|vendorId=|device_address='){throw 'BLOCKED: khởi tạo yêu cầu USB inventory rỗng.'}
}
Assert-InitIdentity
if((Get-InitHashToken (Invoke-InitAdb @('shell','run-as',$package,'sha256sum','files/autonomous-v1-samsung-sm-x926b-api36-attachments.txt'))) -cne $history){throw 'BLOCKED: history witness thật đã đổi; không init.'}
$absent='for f in no_backup/fx2-reload-v1.txt no_backup/fx2-reload-v1.anchor no_backup/fx2-reload-v1.initialized no_backup/fx2-reload-v1-init.consumed.json; do if test -e "$f" || test -L "$f"; then exit 65; fi; done; printf ABSENT'
if((Invoke-InitAdb @('shell','run-as',$package,'sh','-c',("'"+$absent+"'"))) -cne 'ABSENT'){throw 'BLOCKED: store hoặc tombstone đã tồn tại; không reset.'}
$launch=[guid]::NewGuid().ToString()
Invoke-InitAdb @('shell','am','start','--activity-clear-top','--activity-single-top','-n',"$package/.Tbs5530ReloadInitActivity",'--es','launch_token',$launch)|Set-Content (Join-Path $output 'launch.txt')
$watch=[Diagnostics.Stopwatch]::StartNew();$challenge=$null
while($watch.Elapsed.TotalSeconds -lt 10){
    $reply=Invoke-InitAdb @('shell','run-as',$package,'sh','-c',"'if test -f files/reload-init-v1-challenge.json; then cat files/reload-init-v1-challenge.json; else echo WAIT; fi'")
    if($reply -cne 'WAIT'){$value=$reply|ConvertFrom-Json;if($value.launch_token -ceq $launch){$challenge=$value;break}}
    Start-Sleep -Milliseconds 100
}
$expected=@{protocol=$protocol;model='SM-X926B';api=36;boot_count=$ExpectedBootCount;apk_sha256=$ExpectedApkSha256;plan_sha256=$ExpectedPlanSha256;
    history_sha256=$history;history_bytes=996;history_records=12;store_sha256=$store;anchor_sha256=$anchor;marker_sha256=$marker;store_bytes=138;anchor_bytes=94;marker_bytes=99}
if(-not $challenge -or $challenge.session -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$'){throw 'BLOCKED: thiếu challenge đúng launch.'}
foreach($key in $expected.Keys){if($null -eq $challenge.PSObject.Properties[$key] -or $challenge.$key -cne $expected[$key]){throw ('BLOCKED: initialization challenge mismatch '+$key)}}
$challenge|ConvertTo-Json -Depth 6|Set-Content (Join-Path $output 'challenge.json')
$directory='files/reload-init-v1-'+$challenge.session
$grant=@{};$challenge.PSObject.Properties|ForEach-Object{$grant[$_.Name]=$_.Value}
$grant.first_initialization_authorized=$true;$grant.no_tuner_confirmed=$true;$grant.hardware_authorized=$false;$grant.retry=0
$grant.host_script_sha256=$ExpectedHelperSha256;$grant.expires_ms=[long]$challenge.created_ms+120000L
$approved=[Text.Encoding]::UTF8.GetBytes(($grant|ConvertTo-Json -Compress -Depth 6))
$journal=[IO.FileStream]::new((Join-Path $output 'grant-approved.json'),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
try{$journal.Write($approved,0,$approved.Length);$journal.Flush($true)}finally{$journal.Dispose()}
function New-InitHeartbeatCommand([string]$Session,[bool]$Stop){
    if($Session -cnotmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$'){throw 'FAIL-STOP: session malformed.'}
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
printf '{"session":"@SESSION@","elapsed_ms":%s,"stop":@STOP@}' "$elapsed" > files/reload-init-v1-@SESSION@/heartbeat.hosttmp
mv files/reload-init-v1-@SESSION@/heartbeat.hosttmp files/reload-init-v1-@SESSION@/heartbeat.json
printf '{"elapsed_ms":%s,"report":' "$elapsed"
if test -f files/reload-init-v1-@SESSION@/report.json; then cat files/reload-init-v1-@SESSION@/report.json; else printf null; fi
printf '}\n'
'@
    $inner=$inner.Replace('@SESSION@',$Session).Replace('@STOP@',$Stop.ToString().ToLowerInvariant()).Replace("`r`n","`n")
    $quote=[string][char]39;$splice=$quote+[char]34+$quote+[char]34+$quote
    $quoted=$quote+$inner.Replace($quote,$splice)+$quote
    $outer=@'
set -eu
IFS=" " read -r uptime idle < /proc/uptime
exec run-as info.martinmarinov.dvbdriver sh -c @INNER@ heartbeat "$uptime"
'@
    return $outer.Replace('@INNER@',$quoted).Replace("`r`n","`n")
}
function Exchange-Init([bool]$Stop){
    $clock=[Diagnostics.Stopwatch]::StartNew();$reply=Invoke-InitAdb @('shell',(New-InitHeartbeatCommand $challenge.session $Stop))|ConvertFrom-Json
    if($clock.ElapsedMilliseconds -ge 1500 -or ($reply.elapsed_ms -isnot [long] -and $reply.elapsed_ms -isnot [int]) -or
        $reply.elapsed_ms -lt [long]$challenge.created_ms -or (-not $Stop -and $reply.elapsed_ms -ge [long]$grant.expires_ms)){throw 'FAIL-STOP: initialization clock/expiry mismatch.'}
    return $reply
}
$report=$null;$granted=$false
try{
    Assert-InitIdentity
    if((Get-InitHashToken (Invoke-InitAdb @('shell','run-as',$package,'sha256sum','files/autonomous-v1-samsung-sm-x926b-api36-attachments.txt'))) -cne $history){throw 'BLOCKED: history đã đổi trước grant.'}
    $encoded=[Convert]::ToBase64String($approved)
    $null=Exchange-Init $false
    $command='test ! -e '+$directory+'/grant.json && printf %s '+$encoded+' | base64 -d > '+$directory+'/grant.hosttmp && mv '+$directory+'/grant.hosttmp '+$directory+'/grant.json'
    Invoke-InitAdb @('shell','run-as',$package,'sh','-c',("'"+$command+"'"))|Out-Null
    $granted=$true;$watch.Restart()
    while($watch.Elapsed.TotalSeconds -lt 120){$reply=Exchange-Init $false;if($null -ne $reply.report){$report=$reply.report;break};Start-Sleep -Milliseconds 100}
}finally{if($granted){try{$null=Exchange-Init $true}catch{Write-Warning 'Mất liên lạc: app tự dừng theo heartbeat; không reconnect/retry.'}}}
if(-not $report){throw 'FAIL-STOP: thiếu terminal report; không retry/reset.'}
$report|ConvertTo-Json -Depth 8|Set-Content (Join-Path $output 'report.json')
if($report.launch_token -cne $launch -or $report.session -cne $challenge.session -or $report.terminal -isnot [bool] -or -not $report.terminal -or
    $report.result -cne 'INITIALIZATION_VERIFIED_NO_HARDWARE_AUTHORIZATION' -or $report.consumed -isnot [bool] -or -not $report.consumed -or
    $report.plan_sha256 -cne $ExpectedPlanSha256 -or $report.hardware_authorized -isnot [bool] -or $report.hardware_authorized -or $report.usb_io_count -ne 0){throw 'FAIL-STOP: initialization chưa verified; giữ tombstone và mọi failure.'}
$verified=@{}
foreach($item in @(@('fx2-reload-v1.txt','store_sha256',$store),@('fx2-reload-v1.anchor','anchor_sha256',$anchor),@('fx2-reload-v1.initialized','marker_sha256',$marker))){
    $observed=Get-InitHashToken (Invoke-InitAdb @('shell','run-as',$package,'sha256sum',('no_backup/'+$item[0])))
    if($observed -cne $item[2] -or $report.($item[1]) -cne $item[2]){throw 'FAIL-STOP: app/host initialized hash mismatch.'};$verified[$item[1]]=$observed
}
if((Get-InitHashToken (Invoke-InitAdb @('shell','run-as',$package,'sha256sum','files/autonomous-v1-samsung-sm-x926b-api36-attachments.txt'))) -cne $history){throw 'FAIL-STOP: history bị đổi; không cấp quyền B2.'}
$verified.hardware_authorized=$false;$verified.next_run_approval=$false;$verified|ConvertTo-Json|Set-Content (Join-Path $output 'host-verified.json')
Write-Output 'INITIALIZATION_VERIFIED_NO_HARDWARE_AUTHORIZATION: không tự chạy B2 hoặc cấp lại grant.'
