# Compile-only standalone Android Java harness. Does not invoke Gradle, ADB, install or USB.
[CmdletBinding()]
param(
    [string]$AndroidJar = 'D:\Android\Sdk\platforms\android-37.2\android.jar',
    [string]$D8Path = 'D:\Android\Sdk\build-tools\37.0.0\d8.bat',
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\build\approval-benchmark')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$out = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $out.Equals((Join-Path $root 'build\approval-benchmark'), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Output phải là build\approval-benchmark cố định để cleanup không chạm thư mục khác.'
}
foreach ($path in @($AndroidJar, $D8Path)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Thiếu công cụ đã khai báo: $path" }
}
$javac = (Get-Command javac.exe -ErrorAction Stop).Source
$jarCommand = Get-Command jar.exe -ErrorAction SilentlyContinue
if ($null -ne $jarCommand) { $jar = $jarCommand.Source }
else {
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $settings = @(& $java -XshowSettings:properties -version 2>&1)
    $jdkHomes = @($settings | ForEach-Object { if ([string]$_ -match '^\s*java.home\s*=\s*(.+)$') { $Matches[1].Trim() } })
    if ($jdkHomes.Count -ne 1) { throw 'Không xác định được JDK hiện hành.' }
    $jar = Join-Path $jdkHomes[0] 'bin\jar.exe'
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'JDK hiện hành thiếu jar.exe.' }
}
$junit = @(Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\junit\junit\4.13.2" -Filter junit-4.13.2.jar -Recurse)
$hamcrest = @(Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.hamcrest\hamcrest-core\1.3" -Filter hamcrest-core-1.3.jar -Recurse)
if ($junit.Count -ne 1 -or $hamcrest.Count -ne 1) { throw 'Cần dependency JUnit/Hamcrest đã có trong cache; không tự tải.' }
$classes = Join-Path $out 'classes'; $dex = Join-Path $out 'dex'
# Remove only generated harness outputs so moved/removed classes cannot leak into a later DEX.
foreach ($generated in @($classes, $dex)) {
    if (Test-Path -LiteralPath $generated) { Remove-Item -LiteralPath $generated -Recurse -Force }
}
New-Item -ItemType Directory -Force $classes, $dex | Out-Null
$sources = @(
    'app\src\debug\java\info\martinmarinov\dvbdriver\Tbs5530ApprovalProtocol.java',
    'app\src\debug\java\info\martinmarinov\dvbdriver\Tbs5530ApprovalSession.java',
    'tools\approval-benchmark\src\info\tbs5530\approvalbenchmark\Main.java',
    'tools\approval-benchmark\src\info\martinmarinov\dvbdriver\IsolatedApprovalBenchmark.java',
    'drivers\src\debug\java\info\martinmarinov\drivers\usb\tbs5530\Tbs5530BootstrapStop.java',
    'app\src\androidTest\java\info\martinmarinov\dvbdriver\Tbs5530ApprovalDeterministicTest.java',
    'tools\approval-benchmark\src\info\tbs5530\approvalbenchmark\FakeTests.java'
) | ForEach-Object { Join-Path $root $_ }
# Reject production/USB dependencies before compiling. Android SystemClock is only in Main.
foreach ($source in $sources) {
    $text = [IO.File]::ReadAllText($source)
    if ($text -match 'android\.hardware\.usb|android\.content\.Context|getTargetContext|phase29-attempt|Tbs5530UsbBinding|Tbs5530FirmwareValidation') {
        throw "Harness không được phụ thuộc USB hoặc storage production: $source"
    }
}
& $javac --release 8 -encoding UTF-8 -classpath "$AndroidJar;$($junit[0].FullName);$($hamcrest[0].FullName)" -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'javac FAILED.' }
$compiled = Join-Path $out 'harness-classes.jar'
& $jar cf $compiled -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar FAILED.' }
& $D8Path --min-api 21 --lib $AndroidJar --output $dex $compiled $junit[0].FullName $hamcrest[0].FullName
if ($LASTEXITCODE -ne 0) { throw 'D8 FAILED.' }
$dexFile = Join-Path $dex 'classes.dex'
$bytes = [IO.File]::ReadAllBytes($dexFile)
$strings = [Text.Encoding]::UTF8.GetString($bytes)
if ($strings -match 'Landroid/hardware/usb/|phase29-attempt|getTargetContext|info/martinmarinov/dvbdriver/Tbs5530BootstrapApproval') {
    throw 'DEX chứa dependency production/USB ngoài dự kiến.'
}
$runtimeJar = Join-Path $out 'approval-benchmark.jar'
& $jar cf $runtimeJar -C $dex classes.dex
if ($LASTEXITCODE -ne 0) { throw 'Không đóng gói được DEX.' }
[pscustomobject]@{ status = 'COMPILED_ONLY'; dex = $dexFile; runtime_jar = $runtimeJar; sha256 = (Get-FileHash $dexFile -Algorithm SHA256).Hash
    main = 'info.tbs5530.approvalbenchmark.Main'; fake_tests = 'info.tbs5530.approvalbenchmark.FakeTests'; usb_dependencies = 0 }
