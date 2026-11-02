#Requires -Version 5.1
<# Produces review-only recommendations; never uninstalls any packages. #>
[CmdletBinding()]
param([string]$Repository = (Split-Path -Parent $PSScriptRoot),[string]$SdkRoot='')
$ErrorActionPreference='Stop'
if(-not $SdkRoot){$SdkRoot=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{'D:\Android\Sdk'}}
$Repository=(Resolve-Path -LiteralPath $Repository).Path
function Txt([string]$p){if(Test-Path $p){return [IO.File]::ReadAllText($p)};return ''}
function MatchOne([string]$s,[string]$re){$m=[regex]::Match($s,$re);if($m.Success){return $m.Groups[1].Value};return $null}
$root=Txt (Join-Path $Repository 'app\build.gradle')
$native=Txt (Join-Path $Repository 'usbxfer\build.gradle')
$pins=@{
    'build-tools'=(MatchOne $root 'buildToolsVersion\s*=\s*["'']([^"'']+)')
    'ndk'=(MatchOne $native 'ndkVersion\s*=\s*["'']([^"'']+)')
    'cmake'=(MatchOne $native '(?s)\bexternalNativeBuild\s*\{\s*cmake\s*\{[^}]*?\bversion\s*=\s*["'']([0-9.]+)')
}
$major=MatchOne $root 'version\s*=\s*release\((\d+)\)'
$minor=MatchOne $root 'minorApiLevel\s*=\s*(\d+)'
$compile=if($major){'android-'+$major+$(if($minor){'.'+$minor}else{''})}else{$null}
$pins['platforms']=$compile
$rows=New-Object System.Collections.Generic.List[object]
foreach($family in @('platforms','build-tools','ndk','cmake')){
    $d=Join-Path $SdkRoot $family
    if(-not (Test-Path $d)){continue}
    foreach($folder in Get-ChildItem -LiteralPath $d -Directory){
        $id="$family;$($folder.Name)"
        $pinned=($pins[$family] -and ($pins[$family] -eq $folder.Name))
        $rows.Add([pscustomobject]@{
            Package=$id;
            Action=$(if($pinned){'KEEP - project pin'}else{'REVIEW - may be used by other projects'});
            Present=$true;
            Reason=$(if($pinned){'Currently selected by AndroidDvbDriver'}else{'Not referenced by this project; verify OTHER projects, old regression branches and CI before uninstall'});
            SuggestedCommand=$(if($pinned){''}else{"& `"$SdkRoot\cmdline-tools\latest\bin\sdkmanager.bat`" --sdk_root=`"$SdkRoot`" --uninstall `"$id`""})
        })
    }
}
Write-Host "=== SDK cleanup proposal (NO ACTION) ===" -ForegroundColor Cyan
Write-Host "SDK=$SdkRoot; build pin (not globally authoritative): compile=$compile, ndk=$($pins['ndk']), cmake=$($pins['cmake']), buildTools=$($pins['build-tools'])"
$rows | Format-Table Package,Action -AutoSize
Write-Host 'System images/AVDs, platform-tools, cmdline-tools, Java, Gradle caches and emulator are deliberately excluded from automatic cleanup advice.' -ForegroundColor Yellow
Write-Host 'Any command shown is an EXAMPLE for a future separately approved maintenance window; none has been executed.' -ForegroundColor Yellow
$path=Join-Path (Split-Path -Parent $Repository) 'logs\toolchain'
New-Item -ItemType Directory -Force -Path $path | Out-Null
$filename=Join-Path $path ('sdk-cleanup-review-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.csv')
$rows | Export-Csv -Path $filename -Encoding UTF8 -NoTypeInformation
Write-Host "Saved review: $filename" -ForegroundColor Green
