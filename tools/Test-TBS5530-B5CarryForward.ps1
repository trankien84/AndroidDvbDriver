#Requires -Version 7.0
# SPDX-License-Identifier: GPL-2.0-or-later
# Offline only: JSON fixtures and in-memory tampering; never invokes helper main or hardware.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Repository,[Parameter(Mandatory)][string]$PriorCardPath,[Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$path=Join-Path $Repository 'tools\Invoke-TBS5530-B5Session.ps1'
$t=$null;$e=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($path,[ref]$t,[ref]$e)
if($e.Count){throw 'Parse invalid'}
foreach($f in @($ast.EndBlock.Statements|Where-Object{$_ -is [Management.Automation.Language.FunctionDefinitionAst]})){Invoke-Expression $f.Extent.Text}
$b3=Join-Path $Repository 'tools\Invoke-TBS5530-B3Session.ps1'
Import-B5SharedGuards $b3 ((Get-FileHash $b3 -Algorithm SHA256).Hash.ToLowerInvariant());Set-B5Overrides
if(Test-Path $OutputDirectory){throw 'Fresh output required'};$null=New-Item -ItemType Directory -Path $OutputDirectory
$checks=0
function Check([bool]$Value,[string]$Name){if(-not $Value){throw ('FAIL '+$Name)};$script:checks++}
function Refuse([scriptblock]$Action,[string]$Name){$failed=$false;try{& $Action|Out-Null}catch{$failed=$true};Check $failed $Name}
$prior=Get-Content -LiteralPath $PriorCardPath -Raw|ConvertFrom-Json
$ledgerPath=Join-Path $prior.budget_directory 'b5-windows-budget.jsonl'
$resultPath='D:\TSDuck\logs\phase31-b5-20261113-124425-302b54cb\windows-1\windows-capture-result.json'
$ledgerBytes=[IO.File]::ReadAllBytes($ledgerPath);$resultBytes=[IO.File]::ReadAllBytes($resultPath)
$ledgerHash=Get-B5Hash $ledgerBytes;$resultHash=Get-B5Hash $resultBytes
$result=[Text.Encoding]::UTF8.GetString($resultBytes)|ConvertFrom-Json
$lines=[Text.Encoding]::UTF8.GetString($ledgerBytes).TrimEnd([char]10).Split([char]10)
$carry=[ordered]@{schema='TBS5530-B5-CARRY-FORWARD/v1';reason='WINDOWS_DURATION_CAP_CLEAN_EXIT';authorize_android=$true;windows_authorized=$false;owned_processes_stopped=$true;
    windows_consumed=1;max_windows_total=2;windows_remaining_ceiling=1;android_used=0;max_android_attempts=3;
    prior_card_path=$PriorCardPath;prior_card_sha256=(Get-FileHash $PriorCardPath -Algorithm SHA256).Hash.ToLowerInvariant();prior_ledger_path=$ledgerPath;prior_ledger_sha256=$ledgerHash;prior_ledger_bytes=$ledgerBytes.Length;
    prior_result_path=$resultPath;prior_result_sha256=$resultHash;prior_result_bytes=$resultBytes.Length;prior_receipt_sha256=(Get-B5Hash ([Text.Encoding]::UTF8.GetBytes($lines[2]+"`n")));
    owned_process_ids=@($result.helper_pid)+@($result.children|ForEach-Object{$_.pid})}
$carry.prior_serial=$prior.serial
$carry.current_serial=$prior.serial
$carry.current_target_identity_verified=$true
$carry.current_target_model=$prior.model;$carry.current_target_manufacturer=$prior.manufacturer;$carry.current_target_api=$prior.api;$carry.current_target_boot_count=$prior.boot_count
$carry.terminal_cancellation_reviewed=$true
$carry.data_error_review='NO_DATA_ERROR_REVIEWED_TERMINAL_CANCELLATION_ONLY'
foreach($entry in @(@('transport_status','windows-k25\usb-completion-status.json'),@('teardown_status','windows-k25\teardown-urb.txt'),@('cleanup_evidence','continuation-predecessor-evidence.json'))){
    $file=Join-Path $prior.report_directory $entry[1]
    $carry[$entry[0]+'_path']=$file;$carry[$entry[0]+'_bytes']=(Get-Item $file).Length;$carry[$entry[0]+'_sha256']=(Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()
}
$card=($prior|ConvertTo-Json -Depth 30|ConvertFrom-Json);$card.max_windows=0;$card.windows_capture|Add-Member authorized $false -Force;$card|Add-Member carry_forward $carry
$card=($card|ConvertTo-Json -Depth 30|ConvertFrom-Json)
$identityOriginal=${function:Get-B5CarryProcessIdentity}
function Get-B5CarryProcessIdentity([int]$ProcessId){return $null}
$proof=Assert-B5CarryForward $card
Check ($proof.sha256 -ceq (Get-B5CarryPin $card)) 'serialized carry success exact hashes/size/process proof'
Check ($card.budget_id -ceq $prior.budget_id -and $card.session_id -ceq $prior.session_id) 'no new UUID'
Check ($card.max_windows -eq 0 -and $card.max_attempts -eq 3) 'Android3 only, no Windows authority'
$baseline=($card|ConvertTo-Json -Depth 30 -Compress)
$renewed=$baseline|ConvertFrom-Json;$renewed.serial='offline-renewed-alias';$renewed.carry_forward.current_serial=$renewed.serial
$renewedProof=Assert-B5CarryForward $renewed;Check ($null -ne $renewedProof) 'renewed explicit alias admitted with same verified model/API/boot'

foreach($fault in @('prior-card-hash','ledger-hash','result-hash','ledger-length','result-length','reason','windows-authorized','android-unauthorized','falseproof','proof-string','prior-identity','new-session','windows-max','attempt4','count-windows0','count-total3','count-android1','count-attempt4','duplicate-pid','unknown-pid','unreviewed-cancel','unknown-data-error','wrong-model','wrong-boot','unverified-alias','wrong-current-alias','wrong-prior-alias')){
    $c=$baseline|ConvertFrom-Json
    switch($fault){
        'prior-card-hash'{$c.carry_forward.prior_card_sha256='a'*64}
        'ledger-hash'{$c.carry_forward.prior_ledger_sha256='a'*64}
        'result-hash'{$c.carry_forward.prior_result_sha256='a'*64}
        'ledger-length'{$c.carry_forward.prior_ledger_bytes++}
        'result-length'{$c.carry_forward.prior_result_bytes++}
        'reason'{$c.carry_forward.reason='UNKNOWN'}
        'windows-authorized'{$c.carry_forward.windows_authorized=$true}
        'android-unauthorized'{$c.carry_forward.authorize_android=$false}
        'falseproof'{$c.carry_forward.owned_processes_stopped=$false}
        'proof-string'{$c.carry_forward.owned_processes_stopped='true'}
        'prior-identity'{$c.budget_id=[guid]::NewGuid().ToString()}
        'new-session'{$c.session_id=[guid]::NewGuid().ToString()}
        'windows-max'{$c.max_windows=1}
        'attempt4'{$c.max_attempts=4}
        'count-windows0'{$c.carry_forward.windows_consumed=0}
        'count-total3'{$c.carry_forward.max_windows_total=3}
        'count-android1'{$c.carry_forward.android_used=1}
        'count-attempt4'{$c.carry_forward.max_android_attempts=4}
        'duplicate-pid'{$c.carry_forward.owned_process_ids[1]=$c.carry_forward.owned_process_ids[0]}
        'unknown-pid'{$c.carry_forward.owned_process_ids[0]=2147483647}
        'unreviewed-cancel'{$c.carry_forward.terminal_cancellation_reviewed=$false}
        'unknown-data-error'{$c.carry_forward.data_error_review='UNKNOWN'}
        'wrong-model'{$c.model='UNKNOWN'}
        'wrong-boot'{$c.boot_count++}
        'unverified-alias'{$c.carry_forward.current_target_identity_verified=$false}
        'wrong-current-alias'{$c.carry_forward.current_serial='other-alias'}
        'wrong-prior-alias'{$c.carry_forward.prior_serial='other-alias'}
    }
    Refuse {Assert-B5CarryForward $c} ('carry rejects '+$fault)
}
$readOriginal=${function:Read-B5CarryBytes}
$processOriginal=${function:Test-B5CarryProcessRunning}
function Test-B5CarryProcessRunning([int]$ProcessId){return $true}
Refuse {Assert-B5CarryForward $card} 'any owned PID still running rejects'
Set-Item Function:Test-B5CarryProcessRunning $processOriginal
foreach($fault in @('missing-ledger','ledger-prefix','ledger-append','wrong-status','nonzero-exit','status75','status71','detach','unclean-copy','unknown-error')){
    $script:readFault=$fault
    function Read-B5CarryBytes([string]$Path){
        if($Path -ceq $ledgerPath){
            if($script:readFault -ceq 'missing-ledger'){throw 'Fake missing prior ledger'}
            if($script:readFault -ceq 'ledger-prefix'){$b=$ledgerBytes.Clone();$b[0]=[byte]33;return ,$b}
            if($script:readFault -ceq 'ledger-append'){return ,[byte[]]($ledgerBytes+[Text.Encoding]::UTF8.GetBytes("{}`n"))}
        }
        if($Path -ceq $resultPath -and $script:readFault -notlike 'ledger-*' -and $script:readFault -cne 'missing-ledger'){
            $r=[Text.Encoding]::UTF8.GetString($resultBytes)|ConvertFrom-Json
            switch($script:readFault){
                'wrong-status'{$r.status='CAPTURE_ABORTED'}
                'nonzero-exit'{$r.children[3].exit=1}
                'status75'{$r.error='-75'}
                'status71'{$r.error='-71'}
                'detach'{$r.error='DETACH'}
                'unclean-copy'{$r|Add-Member copy_pending $true -Force}
                'unknown-error'{$r.error='UNKNOWN'}
            }
            return ,[Text.Encoding]::UTF8.GetBytes(($r|ConvertTo-Json -Depth 20))
        }
        return ,[IO.File]::ReadAllBytes($Path)
    }
    Refuse {Assert-B5CarryForward $card} ('prior evidence tamper rejects '+$fault)
}
Set-Item Function:Read-B5CarryBytes $readOriginal
# Fake successor ledger is created only in the test output, not in the real budget directory.
$stream=[IO.FileStream]::new((Join-Path $OutputDirectory 'fake-carry-manifest.jsonl'),[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$successorHash='e'*64;$pin=Get-B5CarryPin $card
try{
    $prefix=Add-B5ManifestRecord $stream ([byte[]]@()) ([pscustomobject]@{event='HEADER';card_sha256=$successorHash;budget_id=$card.budget_id;max_attempts=3;max_windows=0;carry_sha256=$pin;prior_windows_ledger_sha256=$ledgerHash;prior_windows_result_sha256=$resultHash})
    $null=Assert-B5Ledger $prefix $successorHash $card 1;Check $true 'successor header binds card plus prior evidence'
    $prefix=Add-B5ManifestRecord $stream $prefix ([pscustomobject]@{event='CONSUME';ordinal=1;epoch=$card.attempts[0].physical_recovery_epoch;queue_depth=16;urb_bytes=16384;carry_sha256=$pin;frontend_experiment=$card.attempts[0].allowed_frontends[0].frontend_experiment;golden_sha256=$card.attempts[0].allowed_frontends[0].golden_sha256;reload_plan_sha256=$card.attempts[0].allowed_frontends[0].reload_plan_sha256})
    Refuse {Assert-B5Ledger $prefix $successorHash $card 1} 'durable successor consume blocks replay'
    Refuse {Assert-B5Ledger $prefix ('f'*64) $card 1} 'new card hash cannot reset successor manifest'
    $changed=$baseline|ConvertFrom-Json;$changed.carry_forward.windows_remaining_ceiling=0
    Refuse {Assert-B5Ledger $prefix $successorHash $changed 1} 'changed carry pin refused'
}finally{$stream.Dispose()}
$windowsHelper='D:\TSDuck\reports\phase31-b5-20261113-124425-302b54cb\windows-numeric-v2-5403759e8aa34105aa667c050a8bb1bf\Invoke-TBS5530-B5WindowsCapture-v2.ps1'
$wt=$null;$we=$null;$wa=[Management.Automation.Language.Parser]::ParseFile($windowsHelper,[ref]$wt,[ref]$we)
foreach($f in @($wa.FindAll({param($n)$n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -cin @('Test-B5WindowsInteger','Assert-B5WindowsCard')},$true))){Invoke-Expression $f.Extent.Text}
Refuse {Assert-B5WindowsCard $card $successorHash 1 506000000} 'Android successor never authorizes Windows handoff'
Check ((Get-B5Hash ([IO.File]::ReadAllBytes($ledgerPath))) -ceq $ledgerHash) 'real prior ledger bytes unchanged'
Check ((Get-B5Hash ([IO.File]::ReadAllBytes($resultPath))) -ceq $resultHash) 'real prior result bytes unchanged'
$summary=[ordered]@{status='PASS';checks=$checks;prior_card_sha256=$carry.prior_card_sha256;prior_ledger_sha256=$ledgerHash;prior_result_sha256=$resultHash;helper_sha256=(Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant();real_prior_ledger_mutations=0;helper_main_calls=0;adb_calls=0;hardware='NOT_RUN';fixtures='JSON-serialized approved prior card/carry; injected bytes only in RAM; fake successor ledger only in output directory.'}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'test-summary.json'),($summary|ConvertTo-Json -Depth 7),[Text.UTF8Encoding]::new($false))
$redactedCarry=$card.carry_forward|ConvertTo-Json -Depth 12|ConvertFrom-Json
$redactedCarry.prior_serial='<PRIVATE: exact prior card serial; parent fills in private successor>'
$redactedCarry.current_serial='<PRIVATE: explicitly renewed current serial; parent fills from fresh identity>'
$publicCarry=$redactedCarry|ConvertTo-Json -Depth 12
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'carry-forward-contract.json'),$publicCarry,[Text.UTF8Encoding]::new($false))
Set-Item Function:Get-B5CarryProcessIdentity $identityOriginal
Write-Output "PASS carry-forward $checks/$checks; no ADB/main/hardware or prior ledger mutation. $OutputDirectory"
