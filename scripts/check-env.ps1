# Non-destructive local environment checks for TBS5530 Android project.
# Run from Windows PowerShell. Does not install software, run builds, or write hardware state.
$ErrorActionPreference = 'Continue'
$Sdk = 'D:\Android\Sdk'
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Repo = Split-Path -Parent $Here

Write-Host '=== Host / paths ===' -ForegroundColor Cyan
Write-Host "Repository: $Repo"
Write-Host "Android SDK: $Sdk ; exists=$(Test-Path $Sdk)"
Write-Host "ANDROID_HOME (current process): $env:ANDROID_HOME"
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "Java:"
& java -version

Write-Host '\n=== Android CLI ===' -ForegroundColor Cyan
if (Get-Command android -ErrorAction SilentlyContinue) {
    & android -V
    & android --sdk=$Sdk info sdk
    & android --sdk=$Sdk sdk list
} else {
    Write-Warning 'android command not found in PATH'
}

Write-Host '\n=== Installed Android packages ===' -ForegroundColor Cyan
foreach ($item in @('platforms','build-tools','ndk','cmake','cmdline-tools')) {
    $path = Join-Path $Sdk $item
    if (Test-Path $path) {
        Write-Host "$item => $((Get-ChildItem $path -Name) -join ', ')"
    } else {
        Write-Warning "Missing: $path"
    }
}

Write-Host '\n=== ADB / Gradle ===' -ForegroundColor Cyan
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
if (Test-Path $adb) { & $adb version } else { Write-Warning 'ADB not found' }
if (Test-Path (Join-Path $Repo 'gradlew.bat')) {
    Write-Host 'gradlew.bat exists. Not executing build automatically.'
} else { Write-Warning 'gradlew.bat not found at expected project root.' }

Write-Host '\n=== WSL and USB/IP ===' -ForegroundColor Cyan
if (Get-Command wsl.exe -ErrorAction SilentlyContinue) { & wsl.exe --list --verbose }
if (Get-Command usbipd.exe -ErrorAction SilentlyContinue) { & usbipd.exe list }
Write-Host 'Read-only check finished. No SDK or firmware changes were made.' -ForegroundColor Green
