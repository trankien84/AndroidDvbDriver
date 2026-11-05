# Build only. Never installs, launches, uninstalls, clears data, provisions approval or accesses ADB/USB.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$wrapper = Join-Path $root 'gradlew.bat'
$jdk = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\java.exe'))) { throw 'Không có JDK đã cấu hình.' }
$sdk = 'D:\Android\Sdk'
$aapt = Join-Path $sdk 'build-tools\37.0.0\aapt.exe'
if (-not (Test-Path $wrapper) -or -not (Test-Path $aapt)) { throw 'Thiếu wrapper hoặc Build-Tools đã khai báo; không tự cài.' }
$init = [IO.Path]::GetTempFileName()
$previousJava = $env:JAVA_HOME
$previousSdk = $env:ANDROID_HOME
$script = @'
gradle.beforeProject { project ->
    if (project.path == ':app') {
        project.layout.buildDirectory.set(new File(project.rootProject.projectDir, 'build/phase29-offline-app'))
        project.pluginManager.withPlugin('com.android.application') {
            project.extensions.getByName('androidComponents').finalizeDsl { android ->
                android.buildTypes.getByName('debug').applicationIdSuffix = '.phase29offline'
                android.defaultConfig.testApplicationId = 'info.martinmarinov.dvbdriver.phase29offline.test'
            }
        }
    }
}
'@
try {
    [IO.File]::WriteAllText($init, $script, [Text.UTF8Encoding]::new($false))
    $env:JAVA_HOME = $jdk; $env:ANDROID_HOME = $sdk
    Push-Location $root
    try {
        & $wrapper --init-script $init :app:assembleDebug :app:assembleDebugAndroidTest --console plain --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Build ứng dụng test cách ly thất bại.' }
    } finally { Pop-Location }
    foreach ($item in @(
        @('build\phase29-offline-app\outputs\apk\debug\app-debug.apk', 'info.martinmarinov.dvbdriver.phase29offline'),
        @('build\phase29-offline-app\outputs\apk\androidTest\debug\app-debug-androidTest.apk', 'info.martinmarinov.dvbdriver.phase29offline.test')
    )) {
        $apk = Join-Path $root $item[0]
        $badging = @(& $aapt dump badging $apk)
        if ($LASTEXITCODE -ne 0 -or -not $badging[0].Contains("name='$($item[1])'")) { throw 'Package test không đúng namespace cách ly.' }
        [pscustomobject]@{ package = $item[1]; apk = $apk; installed = $false }
    }
} finally {
    $env:JAVA_HOME = $previousJava; $env:ANDROID_HOME = $previousSdk
    if (Test-Path -LiteralPath $init) { Remove-Item -LiteralPath $init }
}
