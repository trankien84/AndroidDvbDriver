# Non-destructive environment check used by the existing VS Code task.
# Keep one implementation for SDK, JDK and AVD discovery.
$ErrorActionPreference = 'Stop'
$Repo = Split-Path -Parent $PSScriptRoot
& (Join-Path $Repo 'tools\Inspect-TBS5530-Toolchain.ps1') -Repository $Repo -NoReport
