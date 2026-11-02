#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$scriptPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'Inspect-TBS5530-Toolchain.ps1'
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Inventory script has syntax errors.' }
foreach ($name in @('First-Match', 'Get-NativeCMakeVersion', 'Get-AvdInventory')) {
    $definition = $ast.Find({ param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name
    }, $true)
    if (-not $definition) { throw "Missing inventory function: $name" }
    . ([scriptblock]::Create($definition.Extent.Text))
}

$fixture = @'
android {
    buildToolsVersion = '37.0.0'
    externalNativeBuild {
        cmake {
            path = file('src/main/cpp/CMakeLists.txt')
            version = '4.1.2'
        }
    }
}
'@
if ((Get-NativeCMakeVersion $fixture) -ne '4.1.2') { throw 'CMake parser selected the wrong version.' }
if ($null -ne (Get-NativeCMakeVersion "buildToolsVersion = '37.0.0'")) { throw 'CMake parser matched outside its block.' }

$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('tbs-inventory-test-' + [guid]::NewGuid().ToString('N'))
$originalAvdHome = $env:ANDROID_AVD_HOME
try {
    New-Item -ItemType Directory -Path $tempRoot | Out-Null
    $avdHome = Join-Path $tempRoot 'avds'
    New-Item -ItemType Directory -Path (Join-Path $avdHome 'Smoke.avd') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $avdHome 'Smoke.ini') -Value 'path=fixture' -Encoding ASCII
    $fakeEmulator = Join-Path $tempRoot 'emulator.cmd'
    @('@echo off', 'if "%ANDROID_AVD_HOME%"=="" exit /b 3', 'echo Smoke', 'exit /b 0') |
        Set-Content -LiteralPath $fakeEmulator -Encoding ASCII

    Remove-Item Env:\ANDROID_AVD_HOME -ErrorAction SilentlyContinue
    $inventory = Get-AvdInventory $fakeEmulator @($avdHome)
    if ($inventory.Names.Count -ne 1 -or $inventory.Names[0] -ne 'Smoke') { throw 'AVD registration/folder produced duplicate names.' }
    if ($inventory.EmulatorNames.Count -ne 1) { throw 'Resolved AVD home did not reach emulator listing.' }
    if ($null -ne $env:ANDROID_AVD_HOME) { throw 'An absent AVD environment variable was not restored.' }

    $env:ANDROID_AVD_HOME = 'caller-home'
    $inventory = Get-AvdInventory $fakeEmulator @($avdHome)
    if ($env:ANDROID_AVD_HOME -ne 'caller-home') { throw 'Caller AVD environment was not restored.' }

    $inventory = Get-AvdInventory (Join-Path $tempRoot 'missing-emulator.exe') @($avdHome)
    if ($inventory.Names.Count -ne 1 -or $inventory.MetadataNames[0] -ne 'Smoke') { throw 'Metadata fallback failed without an emulator.' }
    if (-not $inventory.Notes.Count) { throw 'Missing emulator was not reported.' }
    Write-Output 'PASS: CMake scope, AVD deduplication, child environment, environment restoration and metadata fallback.'
} finally {
    if ($null -eq $originalAvdHome) {
        Remove-Item Env:\ANDROID_AVD_HOME -ErrorAction SilentlyContinue
    } else {
        $env:ANDROID_AVD_HOME = $originalAvdHome
    }
    if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
}
