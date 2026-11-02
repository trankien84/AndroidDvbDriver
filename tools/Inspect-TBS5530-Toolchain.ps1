#Requires -Version 5.1
<#
.SYNOPSIS
    Read-only AndroidDvbDriver toolchain inventory.
.DESCRIPTION
    Correctly reads CMake from externalNativeBuild.cmake (not buildToolsVersion).
    Discovers AVDs from emulator -list-avds and local AVD metadata.
    Avoids assigning to PowerShell automatic variable HOME (case-insensitive).
    Writes a JSON report outside the Git repository unless -NoReport is set.
    Does not install, update, uninstall, launch an AVD, fetch Git, or access USB hardware.
#>
[CmdletBinding()]
param(
    [string]$Repository = (Split-Path -Parent $PSScriptRoot),
    [string]$SdkRoot = '',
    [switch]$NoReport,
    [switch]$PassThru
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Repository = (Resolve-Path -LiteralPath $Repository).Path
if (-not (Test-Path -LiteralPath (Join-Path $Repository 'gradle\wrapper\gradle-wrapper.properties') -PathType Leaf)) {
    throw "Not an AndroidDvbDriver checkout: $Repository"
}
if (-not $SdkRoot) {
    if ($env:ANDROID_HOME) { $SdkRoot = $env:ANDROID_HOME }
    elseif ([Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')) {
        $SdkRoot = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
    } else { $SdkRoot = 'D:\Android\Sdk' }
}

function Read-FileText([string]$Path) {
    if (Test-Path -LiteralPath $Path -PathType Leaf) { return [IO.File]::ReadAllText($Path) }
    return ''
}
function First-Match([string]$Content, [string]$Pattern) {
    $options = [Text.RegularExpressions.RegexOptions]::IgnoreCase
    $m = [regex]::Match($Content, $Pattern, $options)
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}
function Get-NativeCMakeVersion([string]$Content) {
    # Scope the match to externalNativeBuild { cmake { ... } }.
    # Matching a global 'version' otherwise incorrectly picks up buildToolsVersion.
    $scope = [regex]::Match($Content, '(?s)\bexternalNativeBuild\s*\{\s*cmake\s*\{(?<body>.*?)\}')
    if (-not $scope.Success) { return $null }
    return First-Match $scope.Groups['body'].Value '(?m)^\s*version\s*=\s*["'']([0-9.]+)["'']'
}
function Command-Path([string]$Name) {
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}
function Read-CommandSummary([string]$Command, [string[]]$Arguments) {
    if (-not $Command) { return 'Not available' }
    try {
        $output = & $Command @Arguments 2>&1 | Out-String
        $lines = @((($output -replace '\x1B\[[0-9;]*m', '').Trim() -split '\r?\n') | Select-Object -First 5)
        return ($lines -join ' | ')
    } catch {
        return 'Error: ' + $_.Exception.Message
    }
}
function Get-SdkEntries([string]$Subdirectory, [string]$Root) {
    $directory = Join-Path $Root $Subdirectory
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) { return @() }
    return @(Get-ChildItem -LiteralPath $directory -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        $metadata = Read-FileText (Join-Path $_.FullName 'source.properties')
        [pscustomobject]@{
            Package = ($Subdirectory -replace '\\', '/') + ';' + $_.Name
            Folder = $_.Name
            Revision = (First-Match $metadata '(?m)^Pkg\.Revision\s*=\s*([^\r\n]+)')
            Location = $_.FullName
        }
    })
}
function Get-AvdInventory([string]$Emulator, [string[]]$Homes) {
    $emulatorNames = @()
    $emulatorExitCode = $null
    $emulatorError = $null
    $originalAvdHome = [Environment]::GetEnvironmentVariable('ANDROID_AVD_HOME', 'Process')
    $selectedAvdHome = @($Homes | Where-Object { $_ } | Select-Object -First 1)
    if (Test-Path -LiteralPath $Emulator -PathType Leaf) {
        try {
            # Pass the resolved home to the listing process, then restore the caller's environment.
            if ($selectedAvdHome.Count) { $env:ANDROID_AVD_HOME = $selectedAvdHome[0] }
            $output = @(& $Emulator -list-avds 2>&1 | ForEach-Object { "$_" })
            $emulatorExitCode = $LASTEXITCODE
            if ($emulatorExitCode -eq 0) {
                $emulatorNames = @($output | ForEach-Object { $_.Trim() } |
                    Where-Object { $_ -and $_ -notmatch '^(INFO|WARNING|ERROR|Android emulator|emulator:)' })
            } else {
                $emulatorError = "emulator -list-avds exit code: $emulatorExitCode"
            }
        } catch {
            $emulatorError = $_.Exception.Message
        } finally {
            if ($null -eq $originalAvdHome) {
                Remove-Item Env:\ANDROID_AVD_HOME -ErrorAction SilentlyContinue
            } else {
                $env:ANDROID_AVD_HOME = $originalAvdHome
            }
        }
    } else {
        $emulatorError = 'Emulator executable not installed'
    }

    $diskNames = @()
    $directoriesChecked = @()
    foreach ($avdDirectory in @($Homes | Where-Object { $_ } | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $avdDirectory -PathType Container)) { continue }
        $directoriesChecked += $avdDirectory
        # An .ini is the AVD registration; a *.avd folder is a useful fallback.
        $diskNames += @(Get-ChildItem -LiteralPath $avdDirectory -Filter '*.ini' -File -ErrorAction SilentlyContinue |
            ForEach-Object { $_.BaseName })
        $diskNames += @(Get-ChildItem -LiteralPath $avdDirectory -Filter '*.avd' -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { $_.Name -replace '\.avd$', '' })
    }
    $diskNames = @($diskNames | Where-Object { $_ } | Sort-Object -Unique)
    $emulatorNames = @($emulatorNames | Where-Object { $_ } | Sort-Object -Unique)
    $allNames = @(@($emulatorNames) + @($diskNames) | Where-Object { $_ } | Sort-Object -Unique)

    $notes = @()
    if (-not $emulatorNames.Count -and $diskNames.Count) {
        $notes += 'emulator -list-avds returned no names, but AVD metadata exists. Verify ANDROID_AVD_HOME in the current VS Code/PowerShell process.'
    }
    if ($emulatorError) { $notes += $emulatorError }
    return [pscustomobject]@{
        Names = $allNames
        EmulatorNames = $emulatorNames
        MetadataNames = $diskNames
        HomesInspected = $directoriesChecked
        EmulatorExitCode = $emulatorExitCode
        Notes = $notes
    }
}

$project = Read-FileText (Join-Path $Repository 'build.gradle')
$app = Read-FileText (Join-Path $Repository 'app\build.gradle')
$native = Read-FileText (Join-Path $Repository 'usbxfer\build.gradle')
$wrapper = Read-FileText (Join-Path $Repository 'gradle\wrapper\gradle-wrapper.properties')
$declared = [ordered]@{
    AGP = (First-Match $project 'com\.android\.application.{0,80}version\s*["'']([0-9.]+)')
    Gradle = (First-Match $wrapper 'gradle-([0-9.]+)-(?:bin|all)\.zip')
    GradleDistributionSha256 = (First-Match $wrapper 'distributionSha256Sum=([0-9a-fA-F]+)')
    CompileSdk = (First-Match $app 'version\s*=\s*release\s*\(\s*([0-9]+)\s*\)')
    CompileSdkMinor = (First-Match $app 'minorApiLevel\s*=\s*([0-9]+)')
    MinSdk = (First-Match $app 'minSdk\s*=\s*([0-9]+)')
    TargetSdk = (First-Match $app 'targetSdk\s*=\s*([0-9]+)')
    BuildTools = (First-Match $app 'buildToolsVersion\s*=\s*["'']([^"'']+)')
    NDK = (First-Match $native 'ndkVersion\s*=\s*["'']([^"'']+)')
    CMake = (Get-NativeCMakeVersion $native)
    JavaSource = (First-Match $app 'sourceCompatibility\s*=\s*JavaVersion\.VERSION_([0-9_]+)')
}

$packages = @(
    Get-SdkEntries 'platforms' $SdkRoot
    Get-SdkEntries 'build-tools' $SdkRoot
    Get-SdkEntries 'ndk' $SdkRoot
    Get-SdkEntries 'cmake' $SdkRoot
    Get-SdkEntries 'cmdline-tools' $SdkRoot
)
$imageRoot = Join-Path $SdkRoot 'system-images'
$images = @()
if (Test-Path -LiteralPath $imageRoot -PathType Container) {
    $images = @(Get-ChildItem -LiteralPath $imageRoot -Recurse -Filter 'source.properties' -File -ErrorAction SilentlyContinue |
        ForEach-Object {
            [pscustomobject]@{
                Relative = $_.DirectoryName.Substring($imageRoot.Length).TrimStart('\')
                Revision = (First-Match (Read-FileText $_.FullName) '(?m)^Pkg\.Revision\s*=\s*([^\r\n]+)')
                Location = $_.DirectoryName
            }
        })
}

$android = Command-Path 'android'
$git = Command-Path 'git.exe'
$java = Command-Path 'java.exe'
$wsl = Command-Path 'wsl.exe'
$usbipd = Command-Path 'usbipd.exe'
$pwsh = Command-Path 'pwsh.exe'
$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $SdkRoot 'emulator\emulator.exe'
$manager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'

$avdHome = $env:ANDROID_AVD_HOME
if (-not $avdHome) { $avdHome = [Environment]::GetEnvironmentVariable('ANDROID_AVD_HOME', 'User') }
if (-not $avdHome) { $avdHome = [Environment]::GetEnvironmentVariable('ANDROID_AVD_HOME', 'Machine') }
if (-not $avdHome) { $avdHome = Join-Path $HOME '.android\avd' }
$defaultAvdHome = Join-Path $HOME '.android\avd'
$homes = @($avdHome, $defaultAvdHome)
if ($env:ANDROID_USER_HOME) { $homes += (Join-Path $env:ANDROID_USER_HOME 'avd') }
$avdInfo = Get-AvdInventory $emulator $homes

$javaHomeValid = $false
if ($env:JAVA_HOME) { $javaHomeValid = Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe') -PathType Leaf }
$javaHomes = @('Process', 'User', 'Machine' | ForEach-Object {
    $value = [Environment]::GetEnvironmentVariable('JAVA_HOME', $_)
    [pscustomobject]@{
        Scope = $_
        Home = $value
        Valid = [bool]($value -and (Test-Path -LiteralPath (Join-Path $value 'bin\java.exe') -PathType Leaf))
    }
})
$validJavaHomes = @($javaHomes | Where-Object Valid)
$effectiveJavaHome = $null
if ($validJavaHomes.Count) { $effectiveJavaHome = $validJavaHomes[0].Home }
$effectiveJava = $java
if ($effectiveJavaHome) { $effectiveJava = Join-Path $effectiveJavaHome 'bin\java.exe' }

$report = [ordered]@{
    Generated = Get-Date -Format 'yyyy-MM-ddTHH:mm:ssK'
    Host = $env:COMPUTERNAME
    Repo = $Repository
    Sdk = $SdkRoot
    SdkPresent = (Test-Path -LiteralPath $SdkRoot)
    AVDHome = $avdHome
    Branch = (Read-CommandSummary $git @('-C', $Repository, 'branch', '--show-current'))
    GitStatus = (Read-CommandSummary $git @('-C', $Repository, 'status', '--short'))
    Declared = $declared
    Environment = [ordered]@{
        JAVA_HOME_Process = $env:JAVA_HOME
        JAVA_HOME_ProcessValid = $javaHomeValid
        JAVA_HOME_User = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
        JAVA_HOME_Machine = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
        JAVA_HOME_Candidates = $javaHomes
        JAVA_HOME_EffectiveForBuild = $effectiveJavaHome
        ANDROID_HOME_Process = $env:ANDROID_HOME
        ANDROID_HOME_User = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
        ANDROID_HOME_Machine = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'Machine')
        ANDROID_AVD_HOME_Process = $env:ANDROID_AVD_HOME
        ANDROID_AVD_HOME_User = [Environment]::GetEnvironmentVariable('ANDROID_AVD_HOME', 'User')
    }
    Executables = [ordered]@{
        Java = $java
        JavaVersion = (Read-CommandSummary $java @('-version'))
        BuildJava = $effectiveJava
        BuildJavaVersion = (Read-CommandSummary $effectiveJava @('-version'))
        AndroidCli = $android
        AndroidCliVersion = (Read-CommandSummary $android @('-V'))
        ADB = $adb
        ADBVersion = $(if (Test-Path $adb) { Read-CommandSummary $adb @('version') } else { 'Not installed' })
        Emulator = $emulator
        EmulatorVersion = $(if (Test-Path $emulator) { Read-CommandSummary $emulator @('-version') } else { 'Not installed' })
        Usbipd = $usbipd
        UsbipdVersion = (Read-CommandSummary $usbipd @('--version'))
        WSL = $wsl
        Git = $git
        GitVersion = (Read-CommandSummary $git @('--version'))
        PowerShell = $pwsh
    }
    InstalledSideBySidePackages = $packages
    InstalledSystemImages = $images
    AVDNames = @($avdInfo.Names)
    AVDDiscovery = $avdInfo
    GradleWrapperDists = $(if (Test-Path (Join-Path $env:USERPROFILE '.gradle\wrapper\dists')) {
        @(Get-ChildItem (Join-Path $env:USERPROFILE '.gradle\wrapper\dists') -Directory | Select-Object -ExpandProperty Name)
    } else { @() })
    Sdkmanager = $manager
    SdkmanagerPresent = (Test-Path $manager)
}

$reportObject = [pscustomobject]$report
Write-Host '=== Current project-selected toolchain ===' -ForegroundColor Cyan
$declared | Format-Table -AutoSize | Out-String | Write-Host
Write-Host '=== Installed SDK package folders ===' -ForegroundColor Cyan
$packages | Format-Table Package, Revision -AutoSize | Out-String | Write-Host
Write-Host '=== Runtime environment ===' -ForegroundColor Cyan
Write-Host "SDK: $SdkRoot ; JAVA_HOME (Process): $env:JAVA_HOME ; AVD: $avdHome"
Write-Host "JAVA_HOME executable exists: $javaHomeValid"
Write-Host "Valid build JDK candidate (not applied automatically): $effectiveJavaHome"
Write-Host "ADB: $($report.Executables.ADBVersion)"
Write-Host "AVDs: $(if($avdInfo.Names.Count){$avdInfo.Names -join ', '}else{'(none detected)'})"
Write-Host "AVD sources: emulator=$($avdInfo.EmulatorNames.Count); disk=$($avdInfo.MetadataNames.Count)"
foreach ($note in $avdInfo.Notes) { Write-Warning $note }
if (-not $declared.CMake) { Write-Warning 'CMake version not found inside externalNativeBuild.cmake block; review usbxfer/build.gradle.' }
if (-not $javaHomeValid) { Write-Warning 'Process JAVA_HOME does not point to an existing bin\java.exe; compare User/Machine environment and Gradle JVM.' }

if (-not $NoReport) {
    $logDir = Join-Path (Split-Path -Parent $Repository) 'logs\toolchain'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $target = Join-Path $logDir ('toolchain-inventory-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.json')
    $reportObject | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $target -Encoding UTF8
    Write-Host "Report: $target" -ForegroundColor Green
    Write-Warning 'Report contains local paths and machine information. Review/redact before sharing; never commit it.'
}
Write-Host 'Read-only inventory complete. No SDK installation/uninstall, Git fetch, AVD boot or USB change was performed.' -ForegroundColor Green
if ($PassThru) { Write-Output $reportObject }
