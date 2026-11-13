#Requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Repository,[Parameter(Mandatory)][string]$PriorCardPath,[Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$helper=Join-Path $Repository 'tools\Invoke-TBS5530-B5Session.ps1';$t=$null;$e=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($helper,[ref]$t,[ref]$e)
foreach($f in @($ast.EndBlock.Statements|Where-Object{$_ -is [Management.Automation.Language.FunctionDefinitionAst]})){Invoke-Expression $f.Extent.Text}
$prior=Get-Content $PriorCardPath -Raw|ConvertFrom-Json;$b3=Join-Path $Repository 'tools\Invoke-TBS5530-B3Session.ps1';Import-B5SharedGuards $b3 $prior.b3_helper_sha256;Set-B5Overrides
if(Test-Path $OutputDirectory){throw 'Fresh output required'};$null=New-Item -ItemType Directory -Path $OutputDirectory
$checks=0
function Check([bool]$v,[string]$n){if(-not $v){throw ('FAIL '+$n)};$script:checks++}
function Refuse([scriptblock]$f,[string]$n){$caught=$false;try{& $f|Out-Null}catch{$caught=$true};Check $caught $n}
$identityOriginal=${function:Get-B5CarryProcessIdentity};function Get-B5CarryProcessIdentity([int]$ProcessId){return $null}
$ledgerPath=Join-Path $prior.budget_directory 'b5-target-migration-manifest.jsonl';$bytes=[IO.File]::ReadAllBytes($ledgerPath);$hash=Get-B5Hash $bytes;$lines=[Text.Encoding]::UTF8.GetString($bytes).TrimEnd([char]10).Split([char]10)
$card=$prior|ConvertTo-Json -Depth 40|ConvertFrom-Json;$card.max_attempts=4
$a=$card.attempts[2]|ConvertTo-Json -Depth 20|ConvertFrom-Json;$a.ordinal=4;$a.physical_recovery_epoch='88888888-8888-4888-8888-888888888888';$a.allowed_frontends=@($a.allowed_frontends|Where-Object{$_.frontend_experiment -ceq 'B5_K25_MANUAL_PLP0'});$card.attempts+=,$a
$x=[ordered]@{schema='TBS5530-B5-ONE-EXTRA/v1';reason='DUPLICATE_FOREGROUND_PRE_GRANT_REFUSAL';authorize_ordinal4_k25=$true;windows_authorized=$false;inherited_android_consumed=3;next_ordinal=4;max_attempts=4;max_android_total=6;
    prior_card_path=$PriorCardPath;prior_card_sha256=(Get-FileHash $PriorCardPath -Algorithm SHA256).Hash.ToLowerInvariant();prior_ledger_path=$ledgerPath;prior_ledger_sha256=$hash;prior_ledger_bytes=$bytes.Length;
    receipt3_sha256=(Get-B5Hash ([Text.Encoding]::UTF8.GetBytes($lines[2]+"`n")));review3_sha256='1df8323b587f41b648ea08100fe55de2422c4258b264e258e2ee45961aa6ffa3'}
$card|Add-Member one_extra_extension $x;$card=$card|ConvertTo-Json -Depth 40|ConvertFrom-Json
$proof=Assert-B5OneExtraExtension $card 4;Check ($proof.inherited_consumed -eq 3) 'actual JSON one extra inherits3 pre-IO attempts'
Assert-B5Card $card @{} 4 $a.physical_recovery_epoch 16 16384;Check $true 'card max4 and one exact K25 variant'
$baseline=$card|ConvertTo-Json -Depth 40 -Compress
foreach($fault in @('prior-card','ledger','size','review','receipt','window','falseproof','counts','next5','max5','budget','session','K36','oldplan','carry','migration')){
    $c=$baseline|ConvertFrom-Json
    switch($fault){'prior-card'{$c.one_extra_extension.prior_card_sha256='a'*64};'ledger'{$c.one_extra_extension.prior_ledger_sha256='a'*64};'size'{$c.one_extra_extension.prior_ledger_bytes++};'review'{$c.one_extra_extension.review3_sha256='a'*64};'receipt'{$c.one_extra_extension.receipt3_sha256='a'*64};'window'{$c.one_extra_extension.windows_authorized=$true};'falseproof'{$c.one_extra_extension.authorize_ordinal4_k25=$false};'counts'{$c.one_extra_extension.inherited_android_consumed=0};'next5'{$c.one_extra_extension.next_ordinal=5};'max5'{$c.max_attempts=5};'budget'{$c.budget_id=[guid]::NewGuid().ToString()};'session'{$c.session_id=[guid]::NewGuid().ToString()};'K36'{$c.attempts[3].allowed_frontends[0].frontend_experiment='B5_K36_MANUAL_PLP0';$c.attempts[3].allowed_frontends[0].allow_k25_auto=$false};'oldplan'{$c.attempts[0].physical_recovery_epoch=[guid]::NewGuid().ToString()};'carry'{$c.carry_forward.android_used=3};'migration'{$c.target_migration.inherited_android_consumed=0}}
    Refuse {Assert-B5OneExtraExtension $c 4} ('extension rejects '+$fault)
}
foreach($n in @(1,2,3,5,6)){Refuse {Assert-B5OneExtraExtension $card $n} 'only ordinal4 authorized'}
$readerOriginal=${function:Read-B5CarryBytes}
function Read-B5CarryBytes([string]$Path){if($Path -ceq $ledgerPath){$b=$bytes.Clone();$b[0]=33;return ,$b};return ,[IO.File]::ReadAllBytes($Path)}
Refuse {Assert-B5OneExtraExtension $card 4} 'prior ledger prefix tamper'
Set-Item Function:Read-B5CarryBytes $readerOriginal
$stream=[IO.FileStream]::new((Join-Path $OutputDirectory 'fake-one-extra.jsonl'),[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try{
    $newHash='e'*64;Assert-B5OneExtraLedger ([byte[]]@()) $newHash $card 4;Check $true 'new separate extension ledger eligible4'
    $prefix=Add-B5ManifestRecord $stream ([byte[]]@()) ([pscustomobject]@{event='HEADER';card_sha256=$newHash;budget_id=$card.budget_id;session_id=$card.session_id;one_extra_sha256=$proof.sha256;parent_ledger_sha256=$hash;inherited_consumed=3;next_ordinal=4;max_attempts=4})
    Assert-B5OneExtraLedger $prefix $newHash $card 4;Check $true 'header binds newcard parent and inheritedcounts'
    Refuse {Assert-B5OneExtraLedger $prefix ('f'*64) $card 4} 'newhash cannot reset extension'
    $prefix=Add-B5ManifestRecord $stream $prefix ([pscustomobject]@{event='CONSUME';ordinal=4;one_extra_sha256=$proof.sha256})
    Refuse {Assert-B5OneExtraLedger $prefix $newHash $card 4} 'ordinal4 consumed once'
    Refuse {Assert-B5OneExtraLedger $prefix $newHash $card 5} 'no fifth or sixth authority'
}finally{$stream.Dispose()}
# Real saved OnePlus API30 plus Samsung grammar, with fake transport only.
$fixture='D:\TSDuck\logs\phase31-b5-20261113-124425-302b54cb\android3-foreground-after-refusal-private.txt'
Assert-B5ForegroundSnapshot ([IO.File]::ReadAllText($fixture));Check $true 'actual OnePlus duplicate labels accepted'
$record='ActivityRecord{abc123 u0 info.martinmarinov.dvbdriver/.Tbs5530AutonomousDiagnosticActivity t1}'
foreach($label in @('mResumedActivity','ResumedActivity')){Assert-B5ForegroundSnapshot ($label+': '+$record);Check $true 'Samsung single-field grammar'}
foreach($fault in @('record','user','task','component','same-label','three','malformed')){
    $other=$record;switch($fault){'record'{$other=$other.Replace('abc123','def456')};'user'{$other=$other.Replace('u0','u10')};'task'{$other=$other.Replace('t1','t2')};'component'{$other=$other.Replace('DiagnosticActivity','OtherActivity')};'malformed'{$other=$other.TrimEnd('}')}}
    $snapshot="mResumedActivity: $record`nResumedActivity: $other";if($fault -ceq 'same-label'){$snapshot="ResumedActivity: $record`nResumedActivity: $record"};if($fault -ceq 'three'){$snapshot+="`nmResumedActivity: $record"}
    Refuse {Assert-B5ForegroundSnapshot $snapshot} ('ambiguous foreground '+$fault)
}
Check ((Get-B5Hash ([IO.File]::ReadAllBytes($ledgerPath))) -ceq $hash) 'old ledger bytes preserved'
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'one-extra-contract.json'),($card.one_extra_extension|ConvertTo-Json -Depth 16),[Text.UTF8Encoding]::new($false))
$r=[ordered]@{status='PASS';checks=$checks;helper_sha256=(Get-FileHash $helper -Algorithm SHA256).Hash.ToLowerInvariant();prior_ledger_sha256=$hash;prior_mutations=0;adb_calls=0;main_calls=0;hardware='NOT_RUN';actual_foreground_fixture_sha256=(Get-FileHash $fixture -Algorithm SHA256).Hash.ToLowerInvariant()}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'test-summary.json'),($r|ConvertTo-Json),[Text.UTF8Encoding]::new($false));Set-Item Function:Get-B5CarryProcessIdentity $identityOriginal
Write-Output "PASS one extra + foreground $checks/$checks no main/ADB/old-ledger mutation. $OutputDirectory"
