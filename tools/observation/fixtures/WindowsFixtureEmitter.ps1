#Requires -Version 7.5
[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('HeaderIdle','ImmediateHeader','StartupDelay','ReadyDelay','ReadyLongDelay','NoBytes','ByteFlood','EarlyExit','BadHeader','TruncatedHeader','StdoutClose','KnownBinary','Grandchild','ClockRollback','Sleeper')][string]$Scenario)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($Scenario -ceq 'Sleeper') { Start-Sleep -Seconds 300; exit 0 }
if ([Console]::ReadLine() -cne 'GO') { exit 8 }
$stdout = [Console]::OpenStandardOutput()
$header = [byte[]](0xd4,0xc3,0xb2,0xa1,2,0,4,0,0,0,0,0,0,0,0,0,0,0,2,0,0xf9,0,0,0)
if ($Scenario -cin @('TruncatedHeader','StdoutClose')) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace Tbs5530Observation.Fixtures {
    public static class WindowsStdout {
        [DllImport("kernel32.dll")] private static extern IntPtr GetStdHandle(int id);
        [DllImport("kernel32.dll")] private static extern bool CloseHandle(IntPtr handle);
        public static void Close() { CloseHandle(GetStdHandle(-11)); }
    }
}
'@
}
switch -CaseSensitive ($Scenario) {
    'EarlyExit' { exit 17 }
    'NoBytes' { Start-Sleep -Seconds 300; exit 0 }
    'BadHeader' { $header[20] = 1 }
    'TruncatedHeader' { $stdout.Write($header,0,7); $stdout.Flush(); [Tbs5530Observation.Fixtures.WindowsStdout]::Close(); Start-Sleep -Seconds 300; exit 0 }
    'Grandchild' {
        $info = [Diagnostics.ProcessStartInfo]::new()
        $info.FileName = Join-Path $PSHOME 'pwsh.exe'; $info.UseShellExecute=$false; $info.CreateNoWindow=$true
        foreach ($argument in @('-NoLogo','-NoProfile','-NonInteractive','-File',$PSCommandPath,'-Scenario','Sleeper')) { $info.ArgumentList.Add($argument) }
        $descendant = [Diagnostics.Process]::Start($info)
    }
}
$stdout.Write($header,0,$header.Length)
if ($Scenario -ceq 'KnownBinary') {
    # Binary edge bytes exercise the raw stdout pipe, not PowerShell's text pipeline.
    $payload = [byte[]]::new(512)
    for ($i=0; $i -lt $payload.Length; $i++) { $payload[$i] = [byte]($i % 256) }
    $stdout.Write($payload,0,$payload.Length)
}
$stdout.Flush()
if ($Scenario -ceq 'StdoutClose') { [Tbs5530Observation.Fixtures.WindowsStdout]::Close(); Start-Sleep -Seconds 300; exit 0 }
if ($Scenario -ceq 'ByteFlood') {
    $data = [byte[]]::new(65536)
    while ($true) { $stdout.Write($data,0,$data.Length); $stdout.Flush() }
}
Start-Sleep -Seconds 300
