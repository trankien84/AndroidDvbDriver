#Requires -Version 5.1
<#
.SYNOPSIS
  Clone and safely synchronize the TBS5530 Android project's Git repositories.
.DESCRIPTION
  - AndroidDvbDriver: fetch remotes only; never merge/rebase/reset your work.
  - Reference repositories: fetch and fast-forward only if the tree is clean.
  - TBS Linux: preserve a six-directory sparse checkout for DVB-related source.
  - -CheckOnly: fetch existing repos to discover new commits, but never clone,
    merge, change sparse settings, or restore files.
  - Does not run builds, firmware uploads, device I/O, or destructive Git commands.
.EXAMPLE
  .\Sync-TBS5530-Repositories.ps1 -CheckOnly
.EXAMPLE
  .\Sync-TBS5530-Repositories.ps1
#>
[CmdletBinding()]
param(
    [string]$Root,
    [switch]$CheckOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Resolve workspace from script location. Support either placement:
# <workspace>/AndroidDvbDriver/tools (recommended, version-controlled)
# <workspace>/tools (legacy setup).
# The -Root parameter always overrides auto-detection.
if ([string]::IsNullOrWhiteSpace($Root)) {
    $toolsParent = Split-Path -Parent $PSScriptRoot
    if ((Split-Path -Leaf $toolsParent) -eq 'AndroidDvbDriver') {
        $Root = Split-Path -Parent $toolsParent
    } elseif (Test-Path -LiteralPath (Join-Path $toolsParent 'AndroidDvbDriver') -PathType Container) {
        $Root = $toolsParent
    } else {
        throw 'Cannot infer workspace root. Specify -Root explicitly.'
    }
}
$Root = [System.IO.Path]::GetFullPath($Root)

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw 'Git is not available in PATH.'
}
if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
    if ($CheckOnly) { throw "Workspace does not exist: $Root" }
    New-Item -ItemType Directory -Path $Root -Force | Out-Null
}

$ReferenceRoot = Join-Path $Root 'references'
$LogRoot = Join-Path $Root 'logs'
New-Item -ItemType Directory -Path $ReferenceRoot, $LogRoot -Force | Out-Null
$LogFile = Join-Path $LogRoot 'repository-sync.log'

function Write-SyncLog {
    param([string]$Message, [string]$Level = 'INFO')
    $line = '{0} [{1}] {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Level, $Message
    Write-Host $line
    Add-Content -LiteralPath $LogFile -Value $line -Encoding UTF8
}

function Invoke-GitChecked {
    param([string[]]$GitArgs)
    # Output may go to stderr, especially when Git prints progress/fetch warnings.
    $result = & git @GitArgs 2>&1 | Out-String
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        throw ('git {0} returned {1}: {2}' -f ($GitArgs -join ' '), $code, $result.Trim())
    }
    return $result.Trim()
}

function Get-CanonicalUrl {
    param([string]$Url)
    return ($Url.Trim().TrimEnd('/') -replace '\.git$', '').ToLowerInvariant()
}

$TbsDirectories = @(
    'drivers/media/usb/dvb-usb',
    'drivers/media/dvb-frontends',
    'drivers/media/dvb-core',
    'drivers/media/tuners',
    'include/media',
    'include/uapi/linux/dvb'
)
$RequiredTbsFiles = @(
    'drivers/media/usb/dvb-usb/tbs5530.c',
    'drivers/media/usb/dvb-usb/tbs5530.h',
    'drivers/media/dvb-frontends/cxd2878.c',
    'drivers/media/dvb-frontends/cxd2878.h',
    'drivers/media/dvb-frontends/m88rs6060.c'
)

$Repos = @(
    [pscustomobject]@{
        Name = 'AndroidDvbDriver'
        RelativePath = 'AndroidDvbDriver'
        Url = 'https://github.com/signalwareltd/AndroidDvbDriver.git'
        Kind = 'main'
    },
    [pscustomobject]@{
        Name = 'tbs-linux_media'
        RelativePath = 'references\tbs-linux_media'
        Url = 'https://github.com/tbsdtv/linux_media.git'
        Kind = 'tbs'
    },
    [pscustomobject]@{
        Name = 'cxd28xx'
        RelativePath = 'references\cxd28xx'
        Url = 'https://github.com/koreapyj/cxd28xx.git'
        Kind = 'reference'
    },
    [pscustomobject]@{
        Name = 'coax'
        RelativePath = 'references\coax'
        Url = 'https://github.com/Till0196/coax.git'
        Kind = 'reference'
    }
)

$HadErrors = $false
Write-SyncLog "START | workspace=$Root | CheckOnly=$CheckOnly"

foreach ($repo in $Repos) {
    $path = Join-Path $Root $repo.RelativePath
    try {
        Write-SyncLog "--- $($repo.Name) ---"

        # Clone only if the destination is genuinely empty or absent.
        if (-not (Test-Path -LiteralPath (Join-Path $path '.git'))) {
            if ((Test-Path -LiteralPath $path) -and
                @((Get-ChildItem -LiteralPath $path -Force)).Count -ne 0) {
                throw "Not a Git repository and folder is nonempty: $path"
            }
            if ($CheckOnly) {
                Write-SyncLog "MISSING: $path (not cloned in CheckOnly mode)" 'WARN'
                continue
            }
            if ($repo.Kind -eq 'tbs') {
                Invoke-GitChecked -GitArgs @(
                    'clone', '--quiet', '--single-branch', '--depth', '1',
                    '--filter=blob:none', '--sparse', '--branch', 'latest',
                    $repo.Url, $path
                ) | Out-Null
            } else {
                Invoke-GitChecked -GitArgs @('clone', '--quiet', $repo.Url, $path) | Out-Null
            }
            Write-SyncLog 'CLONED'
        }

        $sha = Invoke-GitChecked -GitArgs @('-C', $path, 'rev-parse', '--verify', 'HEAD')
        Write-SyncLog "HEAD=$sha"
        $remoteText = Invoke-GitChecked -GitArgs @('-C', $path, 'remote')
        $remoteNames = @($remoteText -split '\r?\n')
        if ($repo.Kind -eq 'main') {
            # A future fork may have origin = personal fork and upstream = original.
            Invoke-GitChecked -GitArgs @('-C', $path, 'fetch', '--quiet', '--prune', '--all') | Out-Null
            $branch = Invoke-GitChecked -GitArgs @('-C', $path, 'branch', '--show-current')
            Write-SyncLog "MAIN | branch=$branch | fetched remotes; no merge, rebase or checkout"
            continue
        }

        if ($remoteNames -notcontains 'origin') {
            throw 'Reference repository has no origin remote; skipped.'
        }
        $remoteUrl = Invoke-GitChecked -GitArgs @('-C', $path, 'remote', 'get-url', 'origin')
        if ((Get-CanonicalUrl $remoteUrl) -ne (Get-CanonicalUrl $repo.Url)) {
            throw "Unexpected origin remote: $remoteUrl (expected $($repo.Url)); skipped"
        }

        $branch = Invoke-GitChecked -GitArgs @('-C', $path, 'branch', '--show-current')
        if ([string]::IsNullOrWhiteSpace($branch)) {
            throw 'Detached HEAD; skipped to avoid changing the selected commit.'
        }
        Invoke-GitChecked -GitArgs @('-C', $path, 'fetch', '--quiet', '--prune', 'origin') | Out-Null

        $tracking = Invoke-GitChecked -GitArgs @('-C', $path, 'rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{upstream}')
        if (-not $tracking.StartsWith('origin/')) {
            throw "Tracking unexpected upstream '$tracking'; skipped."
        }
        $counts = Invoke-GitChecked -GitArgs @('-C', $path, 'rev-list', '--left-right', '--count', 'HEAD...@{upstream}')
        if ($counts -notmatch '^\s*(\d+)\s+(\d+)\s*$') {
            throw "Cannot parse ahead/behind counts: $counts"
        }
        $ahead = [int]$Matches[1]
        $behind = [int]$Matches[2]
        Write-SyncLog "branch=$branch | tracking=$tracking | ahead=$ahead | behind=$behind"

        $status = Invoke-GitChecked -GitArgs @('-C', $path, 'status', '--porcelain=v1', '--untracked-files=normal')
        $clean = [string]::IsNullOrWhiteSpace($status)
        if (-not $clean) {
            Write-SyncLog 'LOCAL CHANGES: skip auto-update and sparse modifications.' 'WARN'
            continue
        }
        if ($ahead -gt 0) {
            Write-SyncLog 'LOCAL COMMITS / DIVERGENCE: skip auto-update.' 'WARN'
            continue
        }
        if ($behind -gt 0) {
            if ($CheckOnly) {
                Write-SyncLog "UPDATE AVAILABLE: $behind new commit(s), no merge in CheckOnly mode"
            } else {
                Invoke-GitChecked -GitArgs @('-C', $path, 'merge', '--ff-only', '@{upstream}') | Out-Null
                $shortSha = Invoke-GitChecked -GitArgs @('-C', $path, 'rev-parse', '--short', 'HEAD')
                Write-SyncLog "UPDATED (fast-forward only) -> $shortSha"
            }
        } else {
            Write-SyncLog 'UP TO DATE'
        }

        if ($repo.Kind -eq 'tbs') {
            $currentText = Invoke-GitChecked -GitArgs @('-C', $path, 'sparse-checkout', 'list')
            $current = @($currentText -split '\r?\n')
            $missingDirs = @($TbsDirectories | Where-Object { $current -notcontains $_ })
            $missingFiles = @($RequiredTbsFiles | Where-Object {
                -not (Test-Path -LiteralPath (Join-Path $path $_))
            })
            if ($missingDirs.Count -eq 0 -and $missingFiles.Count -eq 0) {
                Write-SyncLog 'TBS sparse checkout: all 6 directories and essential files present.'
            } elseif ($CheckOnly) {
                Write-SyncLog "TBS sparse repair needed: missingDirs=$($missingDirs.Count), missingFiles=$($missingFiles.Count)" 'WARN'
            } else {
                Write-SyncLog 'Applying required six-directory sparse checkout...'
                Invoke-GitChecked -GitArgs (@(
                    '-C', $path, 'sparse-checkout', 'set', '--cone', '--no-sparse-index'
                ) + $TbsDirectories) | Out-Null

                $missingFiles = @($RequiredTbsFiles | Where-Object {
                    -not (Test-Path -LiteralPath (Join-Path $path $_))
                })
                if ($missingFiles.Count -gt 0) {
                    Write-SyncLog "Still missing $($missingFiles.Count) essential file(s); attempting a non-destructive worktree restore."
                    Invoke-GitChecked -GitArgs (@(
                        '-C', $path, 'restore', '--source=HEAD', '--worktree',
                        '--ignore-skip-worktree-bits', '--'
                    ) + $TbsDirectories) | Out-Null
                }

                $missingFiles = @($RequiredTbsFiles | Where-Object {
                    -not (Test-Path -LiteralPath (Join-Path $path $_))
                })
                if ($missingFiles.Count -gt 0) {
                    throw "Missing required TBS files after repair: $($missingFiles -join ', ')"
                }
                Write-SyncLog 'TBS sparse checkout ready (all 6 directories).'
            }
        }
    } catch {
        $HadErrors = $true
        Write-SyncLog "ERROR in $($repo.Name): $($_.Exception.Message)" 'ERROR'
    }
}

Write-SyncLog "END | log=$LogFile"
if ($HadErrors) { exit 1 }
