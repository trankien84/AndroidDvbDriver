#Requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Repository,[Parameter(Mandatory)][string]$PriorCardPath,[Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$helper=Join-Path $Repository 'tools\Invoke-TBS5530-B5Session.ps1';$t=$null;$e=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($helper,[ref]$t,[ref]$e)
foreach($f in @($ast.EndBlock.Statements|Where-Object{$_ -is [Management.Automation.Language.FunctionDefinitionAst]})){Invoke-Expression $f.Extent.Text}
$b3=Join-Path $Repository 'tools\Invoke-TBS5530-B3Session.ps1';$prior=Get-Content $PriorCardPath -Raw|ConvertFrom-Json
Import-B5SharedGuards $b3 $prior.b3_helper_sha256;Set-B5Overrides
if(Test-Path $OutputDirectory){throw 'Fresh output required'};$null=New-Item -ItemType Directory -Path $OutputDirectory
$checks=0
function Check([bool]$v,[string]$n){if(-not $v){throw ('FAIL '+$n)};$script:checks++}
function Refuse([scriptblock]$f,[string]$n){$caught=$false;try{& $f|Out-Null}catch{$caught=$true};Check $caught $n}
$ledgerPath=Join-Path $prior.budget_directory 'b5-android-carry-manifest.jsonl';$bytes=[IO.File]::ReadAllBytes($ledgerPath);$hash=Get-B5Hash $bytes
$lines=[Text.Encoding]::UTF8.GetString($bytes).TrimEnd([char]10).Split([char]10)
$card=$prior|ConvertTo-Json -Depth 32|ConvertFrom-Json
$card.serial='offline-migration-new-alias';$card.carry_forward.current_serial=$card.serial
$m=[ordered]@{schema='TBS5530-B5-TARGET-MIGRATION/v1';reason='EXPLICIT_ADB_TARGET_RENEWAL_NO_DEVICE_IO';authorize_ordinal3_k25=$true;windows_authorized=$false;
    inherited_android_consumed=2;next_ordinal=3;max_attempts=3;max_android_total=6;prior_card_path=$PriorCardPath;prior_card_sha256=(Get-FileHash $PriorCardPath -Algorithm SHA256).Hash.ToLowerInvariant();
    prior_ledger_path=$ledgerPath;prior_ledger_sha256=$hash;prior_ledger_bytes=$bytes.Length;prior_serial=$prior.serial;current_serial=$card.serial;
    current_target_identity_verified=$true;current_host_profile='oneplus-gm1900-api30';current_model='GM1900';current_manufacturer='OnePlus';current_api=30;current_boot_count=408;
    consumed=@([ordered]@{ordinal=1;result='PRE_IO_REFUSED';receipt_sha256=(Get-B5Hash ([Text.Encoding]::UTF8.GetBytes($lines[2]+"`n")));review_sha256='29d2bad941cb3771c488816976eee668b763ec1ecae9d3a3728d91da5e0eb62c'},[ordered]@{ordinal=2;result='PRE_IO_REFUSED';receipt_sha256=(Get-B5Hash ([Text.Encoding]::UTF8.GetBytes($lines[4]+"`n")));review_sha256='dc0bc9d20f30d6fda8171283e3700794d46f7148cba790e526a6d318f34a3601'})}
$card|Add-Member target_migration $m;$card=$card|ConvertTo-Json -Depth 32|ConvertFrom-Json
$identityOriginal=${function:Get-B5CarryProcessIdentity}
function Get-B5CarryProcessIdentity([int]$ProcessId){return $null}
$proof=Assert-B5TargetMigration $card 3;Check ($proof.inherited_consumed -eq 2) 'actual JSON parent ledger inherits two attempts'
$baseline=$card|ConvertTo-Json -Depth 32 -Compress
$oldEnd='2026-11-13T14:52:29+07:00';$ownedPath='C:\Program Files\USBPcap\USBPcapCMD.exe'
foreach($case in @('absent','known-reuse','unknown-start','unknown-path','same-exe','old-process')){
    $script:identityCase=$case
    function Get-B5CarryProcessIdentity([int]$ProcessId){
        if($script:identityCase -ceq 'absent'){return $null}
        $v=[pscustomobject]@{name='SearchFilterHost';path='C:\Windows\System32\SearchFilterHost.exe';start=[DateTime]'2026-11-13T16:00:00+07:00'}
        switch($script:identityCase){'unknown-start'{$v.start=$null};'unknown-path'{$v.path=$null};'same-exe'{$v.name='USBPcapCMD';$v.path=$ownedPath};'old-process'{$v.start=[DateTime]'2026-11-13T14:00:00+07:00'}}
        return $v
    }
    $running=Test-B5CarryProcessRunning 408 $ownedPath $oldEnd
    Check ($running -eq ($case -cnotin @('absent','known-reuse'))) ('strong PID identity '+$case)
}
function Get-B5CarryProcessIdentity([int]$ProcessId){return $null}

foreach($fault in @('cardhash','ledgerhash','size','budget','session','model','boot','alias','falseproof','counts','next','windows','review','receipt','result','priorserial','carry')){
    $c=$baseline|ConvertFrom-Json
    switch($fault){'cardhash'{$c.target_migration.prior_card_sha256='a'*64};'ledgerhash'{$c.target_migration.prior_ledger_sha256='b'*64};'size'{$c.target_migration.prior_ledger_bytes++};'budget'{$c.budget_id=[guid]::NewGuid().ToString()};'session'{$c.session_id=[guid]::NewGuid().ToString()};'model'{$c.model='OTHER'};'boot'{$c.boot_count++};'alias'{$c.target_migration.current_serial='other'};'falseproof'{$c.target_migration.current_target_identity_verified=$false};'counts'{$c.target_migration.inherited_android_consumed=0};'next'{$c.target_migration.next_ordinal=4};'windows'{$c.target_migration.windows_authorized=$true};'review'{$c.target_migration.consumed[0].review_sha256='f'*64};'receipt'{$c.target_migration.consumed[1].receipt_sha256='f'*64};'result'{$c.target_migration.consumed[0].result='PASS'};'priorserial'{$c.target_migration.prior_serial='other'};'carry'{$c.carry_forward.windows_consumed=0}}
    Refuse {Assert-B5TargetMigration $c 3} ('migration rejects '+$fault)
}
foreach($ordinal in @(1,2,4)){Refuse {Assert-B5TargetMigration $card $ordinal} 'only ordinal3 allowed'}
$readOriginal=${function:Read-B5CarryBytes}
function Read-B5CarryBytes([string]$Path){if($Path -ceq $ledgerPath){$copy=$bytes.Clone();$copy[0]=33;return ,$copy};return ,[IO.File]::ReadAllBytes($Path)}
Refuse {Assert-B5TargetMigration $card 3} 'tampered old prefix rejected'
Set-Item Function:Read-B5CarryBytes $readOriginal
$stream=[IO.FileStream]::new((Join-Path $OutputDirectory 'fake-migration.jsonl'),[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$newHash='e'*64
try{
    Assert-B5TargetMigrationLedger ([byte[]]@()) $newHash $card 3;Check $true 'empty separate migration ledger eligible ordinal3 only'
    $prefix=Add-B5ManifestRecord $stream ([byte[]]@()) ([pscustomobject]@{event='HEADER';card_sha256=$newHash;budget_id=$card.budget_id;session_id=$card.session_id;target_migration_sha256=$proof.sha256;parent_ledger_sha256=$hash;inherited_consumed=2;next_ordinal=3})
    Assert-B5TargetMigrationLedger $prefix $newHash $card 3;Check $true 'new header binds parent and successor'
    Refuse {Assert-B5TargetMigrationLedger $prefix ('f'*64) $card 3} 'card change cannot reset migration'
    $prefix=Add-B5ManifestRecord $stream $prefix ([pscustomobject]@{event='CONSUME';ordinal=3;target_migration_sha256=$proof.sha256})
    Refuse {Assert-B5TargetMigrationLedger $prefix $newHash $card 3} 'consumed ordinal3 never replays'
    Refuse {Assert-B5TargetMigrationLedger $prefix $newHash $card 4} 'cannot extend max3'
}finally{$stream.Dispose()}
Check ((Get-B5Hash ([IO.File]::ReadAllBytes($ledgerPath))) -ceq $hash) 'old ledger remains frozen'
$mOut=$card.target_migration|ConvertTo-Json -Depth 16|ConvertFrom-Json;$mOut.prior_serial='<PRIVATE: parent fills exact prior alias>';$mOut.current_serial='<PRIVATE: parent fills renewed verified alias>'
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'target-migration-contract.json'),($mOut|ConvertTo-Json -Depth 16),[Text.UTF8Encoding]::new($false))
$result=[ordered]@{status='PASS';checks=$checks;helper_sha256=(Get-FileHash $helper -Algorithm SHA256).Hash.ToLowerInvariant();prior_ledger_sha256=$hash;prior_ledger_bytes=$bytes.Length;prior_mutations=0;main_calls=0;adb_calls=0;hardware='NOT_RUN'}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'test-summary.json'),($result|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Set-Item Function:Get-B5CarryProcessIdentity $identityOriginal
Write-Output "PASS target migration $checks/$checks no main/ADB/old-ledger mutation. $OutputDirectory"
