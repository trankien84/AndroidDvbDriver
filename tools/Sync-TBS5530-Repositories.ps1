<#
.SYNOPSIS
  Synchronize the project and all TBS5530 reference repositories in one command.
.DESCRIPTION
  No arguments: clone missing repositories, automatically recover incomplete empty
  kernel clones after a verified backup, download selected missing blobs and apply
  clean fast-forward reference updates. MAIN is fetch-only and is never merged.
  TBS retains all six cone directories; CoreELEC stays file-scoped and non-cone.
  Windows-safe manifests are checked before command-scoped NTFS exceptions.
  Every selected file and its filtered Git hash must match before success is logged.
  -CheckOnly checks all repositories and fetches healthy ones for comparison, but
  never clones, repairs, downloads missing sparse blobs, changes patterns or merges.
  Local edits/deletions, nonempty broken worktrees, locks, unsafe names or divergent
  branches are preserved, not force-fixed. Backups remain in workspace logs.
  One foreground run only: no watcher, background service, reset, clean, push,
  hardware I/O or persistent Git protection changes.
.EXAMPLE
  .\tools\Sync-TBS5530-Repositories.ps1
.EXAMPLE
  .\tools\Sync-TBS5530-Repositories.ps1 -CheckOnly
#>
[CmdletBinding()]
param([switch]$CheckOnly)

#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'


function Write-SyncLog {
    param([string]$Message, [string]$Level = 'INFO')
    $line = '{0} [{1}] {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Level, $Message
    Write-Host $line
    Add-Content -LiteralPath $LogFile -Value $line -Encoding UTF8
}
function Invoke-GitChecked {
    param([string[]]$GitArgs, [string]$InputText)
    $oldLazy = [Environment]::GetEnvironmentVariable('GIT_NO_LAZY_FETCH', 'Process')
    $oldLocks = [Environment]::GetEnvironmentVariable('GIT_OPTIONAL_LOCKS', 'Process')
    $preference = $ErrorActionPreference
    try {
        $env:GIT_NO_LAZY_FETCH = '1'
        $env:GIT_OPTIONAL_LOCKS = '0'
        $ErrorActionPreference = 'Continue'
        if ($PSBoundParameters.ContainsKey('InputText')) {
            # PowerShell's native pipeline adds CRLF, which fetch --stdin treats as part
            # of an object refspec. Write UTF-8/LF explicitly on both PS 5.1 and PS 7.
            $start = [Diagnostics.ProcessStartInfo]::new()
            $start.FileName = (Get-Command git -CommandType Application -ErrorAction Stop).Source
            $quoted = foreach ($argument in $GitArgs) {
                $escaped = [regex]::Replace($argument, '(\\*)"', '$1$1\"')
                '"' + [regex]::Replace($escaped, '(\\+)$', '$1$1') + '"'
            }
            $start.Arguments = $quoted -join ' '
            $start.UseShellExecute = $false
            $start.CreateNoWindow = $true
            $start.RedirectStandardInput = $true
            $start.RedirectStandardOutput = $true
            $start.RedirectStandardError = $true
            $process = [Diagnostics.Process]::new()
            $process.StartInfo = $start
            try {
                if (-not $process.Start()) { throw 'Could not start Git.' }
                $stdout = $process.StandardOutput.ReadToEndAsync()
                $stderr = $process.StandardError.ReadToEndAsync()
                $bytes = [Text.UTF8Encoding]::new($false).GetBytes($InputText.Replace("`r`n", "`n").TrimEnd([char[]]"`r`n") + "`n")
                $process.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
                $process.StandardInput.Close()
                $process.WaitForExit()
                $code = $process.ExitCode
                $output = $stdout.Result
                if ($code -ne 0) { $output += $stderr.Result }
            } finally { $process.Dispose() }
        } else {
            $output = & git @GitArgs 2>&1 | Out-String
            $code = $LASTEXITCODE
        }
    } finally {
        $ErrorActionPreference = $preference
        [Environment]::SetEnvironmentVariable('GIT_NO_LAZY_FETCH', $oldLazy, 'Process')
        [Environment]::SetEnvironmentVariable('GIT_OPTIONAL_LOCKS', $oldLocks, 'Process')
    }
    if ($code -ne 0) {
        throw ('git {0} returned exit {1}: {2}' -f ($GitArgs -join ' '), $code, $output.Trim())
    }
    return $output.Trim()
}
function Get-CanonicalUrl {
    param([string]$Url)
    return ($Url.Trim().TrimEnd('/') -replace '\.git$', '').ToLowerInvariant()
}

# Preserve the six established TBS reference directories (cone mode).
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

# CoreELEC/linux-amlogic is a very large kernel repository. Clone with
# --filter=blob:none + --no-checkout, then materialize ONLY these related files.
# Non-cone patterns are literal, repository-root-anchored file paths.
# Include relevant driver framework, frontends, and build declarations so
# that future upstream diffs can be investigated without a full kernel clone.
$CoreElecFiles = @(
    'drivers/media/usb/dvb-usb/tbs5530.c',
    'drivers/media/usb/dvb-usb/tbs5530.h',
    'drivers/media/usb/dvb-usb/dvb-usb.c',
    'drivers/media/usb/dvb-usb/dvb-usb.h',
    'drivers/media/usb/dvb-usb/dvb-usb-common.h',
    'drivers/media/usb/dvb-usb/dvb-usb-urb.c',
    'drivers/media/usb/dvb-usb/usb-urb.c',
    'drivers/media/usb/dvb-usb/dvb-usb-firmware.c',
    'drivers/media/usb/dvb-usb/dvb-usb-i2c.c',
    'drivers/media/usb/dvb-usb/Kconfig',
    'drivers/media/usb/dvb-usb/Makefile',
    'drivers/media/dvb-frontends/cxd2878.c',
    'drivers/media/dvb-frontends/cxd2878.h',
    'drivers/media/dvb-frontends/cxd2878_priv.h',
    'drivers/media/dvb-frontends/m88rs6060.c',
    'drivers/media/dvb-frontends/m88rs6060.h',
    'drivers/media/dvb-frontends/m88rs6060_priv.h',
    'drivers/media/dvb-frontends/Kconfig',
    'drivers/media/dvb-frontends/Makefile',
    'drivers/media/dvb-core/dvb_frontend.c',
    'drivers/media/dvb-core/dvb_demux.c',
    'drivers/media/dvb-core/Kconfig',
    'drivers/media/dvb-core/Makefile',
    'drivers/media/tuners/Kconfig',
    'drivers/media/tuners/Makefile',
    'include/media/dvb_frontend.h',
    'include/media/dvb-usb.h',
    'include/media/dvb_demux.h',
    'include/uapi/linux/dvb/frontend.h'
)
# Some names differ between kernel trees. A file absent from the selected
# CoreELEC commit is not a sync failure; validate only essential files.
$RequiredCoreElecFiles = @(
    'drivers/media/usb/dvb-usb/tbs5530.c',
    'drivers/media/usb/dvb-usb/tbs5530.h',
    'drivers/media/usb/dvb-usb/Kconfig',
    'drivers/media/usb/dvb-usb/Makefile',
    'drivers/media/dvb-frontends/cxd2878.c',
    'drivers/media/dvb-frontends/cxd2878.h',
    'drivers/media/dvb-frontends/cxd2878_priv.h'
)

$Repos = @(
    [pscustomobject]@{
        Name='AndroidDvbDriver'; RelativePath='AndroidDvbDriver'
        Url='https://github.com/signalwareltd/AndroidDvbDriver.git'; Kind='main'
    },
    [pscustomobject]@{
        Name='tbs-linux_media'; RelativePath='references\tbs-linux_media'
        Url='https://github.com/tbsdtv/linux_media.git'; Kind='tbs'
    },
    [pscustomobject]@{
        Name='cxd28xx'; RelativePath='references\cxd28xx'
        Url='https://github.com/koreapyj/cxd28xx.git'; Kind='reference'
    },
    [pscustomobject]@{
        Name='coax'; RelativePath='references\coax'
        Url='https://github.com/Till0196/coax.git'; Kind='reference'
    },
    [pscustomobject]@{
        Name='coreelec-linux-amlogic'; RelativePath='references\coreelec-linux-amlogic'
        Url='https://github.com/CoreELEC/linux-amlogic.git'; Kind='coreelec'
    }
)

function Get-GitStoragePath {
    param([string]$Path, [string]$Name)
    $value = Invoke-GitChecked @('-C',$Path,'rev-parse','--git-path',$Name)
    if ([IO.Path]::IsPathRooted($value)) { return $value }
    return [IO.Path]::GetFullPath((Join-Path $Path $value))
}
function Assert-NoGitOperation {
    param([string]$Path)
    foreach ($name in @('index.lock','HEAD.lock','config.lock','shallow.lock','info/sparse-checkout.lock','MERGE_HEAD','CHERRY_PICK_HEAD','REVERT_HEAD','rebase-merge','rebase-apply')) {
        if (Test-Path -LiteralPath (Get-GitStoragePath $Path $name)) { throw "Git operation/lock exists: $name" }
    }
    $git = Get-GitStoragePath $Path '.'
    $locks = @(Get-ChildItem -LiteralPath $git -Recurse -Force -File -Filter '*.lock' | Select-Object -First 1)
    if ($locks.Count) { throw 'Git lock exists in repository metadata; no synchronization or recovery allowed.' }
    if ($env:GIT_INDEX_FILE -or $env:GIT_WORK_TREE -or $env:GIT_DIR) { throw 'Git environment overrides must be cleared before synchronization.' }
}
function Assert-WindowsSparsePath {
    param([string]$Name)
    foreach ($part in ($Name -split '/')) {
        if ($part -eq '' -or $part -in @('.','..') -or $part -match '[\x00-\x1f<>:"\\|?*\[\]]' -or $part -match '[. ]$' -or
            $part -match '^(?i:con|prn|aux|nul|conin\$|conout\$|com[0-9\u00b9\u00b2\u00b3]|lpt[0-9\u00b9\u00b2\u00b3])(?:\.|$)' -or $part -ieq '.git') {
            throw "Selected sparse path is unsafe on Windows: $Name"
        }
    }
}
function Get-SparseManifest {
    param([string]$Path, [ValidateSet('tbs','coreelec')][string]$Kind, [string]$Revision = 'HEAD')
    $tree = Invoke-GitChecked @('-c','core.quotePath=false','-C',$Path,'ls-tree','-r',$Revision)
    $all = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach ($line in ($tree -split '\r?\n')) {
        if ($line -notmatch '^(\d{6}) (blob|commit) ([0-9a-f]+)\t(.+)$') { throw 'Cannot parse committed tree safely.' }
        $all[$Matches[4]] = [pscustomobject]@{ Path=$Matches[4]; Oid=$Matches[3]; Mode=$Matches[1] }
    }
    $required = if ($Kind -eq 'tbs') { $RequiredTbsFiles } else { $RequiredCoreElecFiles }
    foreach ($file in $required) { if (-not $all.ContainsKey($file)) { throw "Essential file absent from selected commit: $file" } }
    $ancestors = @('')
    foreach ($dir in $TbsDirectories) {
        $parent = $dir
        while ($parent.Contains('/')) { $parent = $parent.Substring(0,$parent.LastIndexOf('/')); $ancestors += $parent }
    }
    $wanted = @($CoreElecFiles) + @($all.Keys | Where-Object { $_ -match '^drivers/media/(?:.*/)?(?:tbs5530|cxd2878|m88rs6060)[^/]*$' })
    $selected = foreach ($file in $all.Keys) {
        $include = $false
        if ($Kind -eq 'coreelec') { $include = $wanted -ccontains $file }
        else {
            foreach ($dir in $TbsDirectories) { if ($file.StartsWith($dir + '/', [StringComparison]::Ordinal)) { $include=$true; break } }
            $slash = $file.LastIndexOf('/')
            $parent = if ($slash -lt 0) { '' } else { $file.Substring(0,$slash) }
            if ($ancestors -ccontains $parent) { $include=$true }
        }
        if ($include) {
            Assert-WindowsSparsePath $file
            if ($all[$file].Mode -notin @('100644','100755')) { throw "Unsupported sparse entry mode: $file" }
            $all[$file]
        }
    }
    $names = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($entry in $selected) {
        if (-not $names.Add($entry.Path)) { throw "Selected sparse paths collide on Windows: $($entry.Path)" }
    }
    return @($selected | Sort-Object Path)
}
function Assert-SparseObjects {
    param([string]$Path, [object[]]$Manifest)
    $oids = @($Manifest | ForEach-Object Oid | Sort-Object -Unique)
    $result = Invoke-GitChecked -GitArgs @('-C',$Path,'cat-file','--batch-check=%(objectname) %(objecttype)') -InputText ($oids -join "`n")
    $missing = @($result -split '\r?\n' | Where-Object { $_ -notmatch '^[0-9a-f]+ blob$' })
    if ($missing.Count) { throw "Selected Git blobs are missing locally ($($missing.Count)); no checkout attempted. Run normal synchronization to download the selected objects automatically." }
}
function Receive-SparseObjects {
    param([string]$Path, [object[]]$Manifest)
    $oids = @($Manifest | ForEach-Object Oid | Sort-Object -Unique)
    $types = Invoke-GitChecked -GitArgs @('-C',$Path,'cat-file','--batch-check=%(objectname) %(objecttype)') -InputText ($oids -join "`n")
    $missing = @($types -split '\r?\n' | Where-Object { $_ -match '^[0-9a-f]+ missing$' } | ForEach-Object { ($_ -split ' ')[0] })
    if ($missing.Count) {
        # This is an exact-object promisor fetch, not branch-history negotiation.
        # Default negotiation can walk promised-but-missing blobs in a real shallow
        # partial clone and fail with "bad revision" before downloading the wants.
        # Match Git's lazy-fetch strategy without enabling implicit lazy fetching.
        Invoke-GitChecked -GitArgs @('-c','fetch.negotiationAlgorithm=noop','-C',$Path,
            'fetch','--quiet','--no-write-fetch-head','--no-tags','--no-recurse-submodules',
            '--filter=blob:none','origin','--stdin') -InputText ($missing -join "`n") | Out-Null
    }
    Assert-SparseObjects $Path $Manifest
}
function Get-DesiredSparsePatterns {
    param([string]$Kind, [object[]]$Manifest)
    if ($Kind -eq 'tbs') { return $TbsDirectories }
    return @((@($CoreElecFiles) + @($Manifest | ForEach-Object Path)) | Sort-Object -Unique | ForEach-Object { '/' + $_ })
}
function Assert-SparseReference {
    param([string]$Path, [ValidateSet('tbs','coreelec')][string]$Kind)
    Assert-NoGitOperation $Path
    if (-not (Test-Path -LiteralPath (Get-GitStoragePath $Path 'index') -PathType Leaf)) {
        throw 'SPARSE_INDEX_MISSING: checkout is incomplete; a normal sync will attempt backup-first recovery when the working tree is empty.'
    }
    $manifest = @(Get-SparseManifest $Path $Kind)
    Assert-SparseObjects $Path $manifest
    if ((Invoke-GitChecked @('-C',$Path,'config','--bool','core.sparseCheckout')) -ne 'true') { throw 'Sparse checkout is not enabled.' }
    $mode = Invoke-GitChecked @('-C',$Path,'config','--bool','core.sparseCheckoutCone')
    $expectedMode = if ($Kind -eq 'tbs') { 'true' } else { 'false' }
    if ($mode -ne $expectedMode) { throw "Unexpected sparse mode: $mode; expected $expectedMode" }
    $listed = Invoke-GitChecked @('-C',$Path,'sparse-checkout','list')
    $actual = @($listed -split '\r?\n' | Where-Object { $_ } | Sort-Object -Unique)
    $desired = @(Get-DesiredSparsePatterns $Kind $manifest | Sort-Object -Unique)
    if (@(Compare-Object $actual $desired -CaseSensitive).Count) { throw 'Sparse selection differs from the reviewed scope; no automatic scope rewrite.' }
    $status = Invoke-GitChecked @('-C',$Path,'status','--porcelain=v1','--untracked-files=all')
    if ($status) { throw 'Local changes/deletions exist; refusing to repair or update.' }
    foreach ($entry in $manifest) {
        $file = Join-Path $Path $entry.Path
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "SPARSE_FILE_MISSING: $($entry.Path); index/patterns alone do not prove materialization." }
    }
    $hashes = Invoke-GitChecked -GitArgs @('-C',$Path,'hash-object','--stdin-paths') -InputText (($manifest | ForEach-Object Path) -join "`n")
    $values = @($hashes -split '\r?\n')
    if ($values.Count -ne $manifest.Count) { throw 'Sparse hash count differs from committed manifest.' }
    for ($i=0; $i -lt $manifest.Count; $i++) {
        if ($values[$i] -ne $manifest[$i].Oid) { throw "Sparse file hash mismatch: $($manifest[$i].Path)" }
    }
}
function Get-MetadataFingerprint {
    param([string]$GitDirectory, [switch]$ContentOnly)
    $base = [IO.Path]::GetFullPath($GitDirectory).TrimEnd([char[]]'\/')
    $parts = foreach ($item in Get-ChildItem -LiteralPath $base -Recurse -Force -File) {
        $relative = $item.FullName.Substring($base.Length + 1)
        if ($relative -match '^objects[\\/]' -and -not $ContentOnly) { continue }
        $hash = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash
        if ($ContentOnly) { '{0}|{1}' -f $relative,$hash }
        else { '{0}|{1}|{2}' -f $relative,$item.LastWriteTimeUtc.Ticks,$hash }
    }
    return (@($parts | Sort-Object) -join "`n")
}
function Get-ReferenceFingerprint {
    param([string]$Path)
    return Get-MetadataFingerprint (Get-GitStoragePath $Path '.')
}
function Initialize-SparseReference {
    param([string]$Path, [ValidateSet('tbs','coreelec')][string]$Kind, [switch]$Fresh, [switch]$Repair, [switch]$AllowObjectDownload)
    Assert-NoGitOperation $Path
    $manifest = @(Get-SparseManifest $Path $Kind)
    if ($AllowObjectDownload -and -not $Repair) { throw 'Object download during recovery requires explicit repair.' }
    if (-not $AllowObjectDownload) { Assert-SparseObjects $Path $manifest }
    if (-not $Fresh -and -not $Repair) { throw 'Existing sparse checkout must use the backup-first recovery path.' }
    if (Test-Path -LiteralPath (Get-GitStoragePath $Path 'index')) { throw 'Index already exists; refusing to overwrite staged or deleted work.' }
    if (@(Get-ChildItem -LiteralPath $Path -Force | Where-Object Name -ne '.git').Count) {
        throw 'Recovery requires an empty working tree; preserve all existing/untracked files and review manually.'
    }
    $backup = $null
    $beforeHead = Invoke-GitChecked @('-C',$Path,'rev-parse','HEAD')
    $before = Get-ReferenceFingerprint $Path
    if ($Repair) {
        Start-Sleep -Seconds 3
        if ((Get-ReferenceFingerprint $Path) -cne $before) { throw 'Git metadata is changing; stop auto-fetch/sync before recovery.' }
        $git = Get-GitStoragePath $Path '.'
        if (-not (Test-Path -LiteralPath $git -PathType Container) -or -not (Test-Path -LiteralPath (Join-Path $Path '.git') -PathType Container)) {
            throw 'Linked worktree recovery is not supported; preserve common Git metadata manually.'
        }
        $backupRoot = if (Get-Variable -Name LogRoot -ErrorAction SilentlyContinue) { $LogRoot } else { Join-Path (Split-Path -Parent $Path) '..\logs' }
        $backup = Join-Path $backupRoot ('sparse-before-' + (Split-Path -Leaf $Path) + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
        New-Item -ItemType Directory -Path $backup -Force | Out-Null
        Copy-Item -LiteralPath $git -Destination (Join-Path $backup 'git') -Recurse -Force
        if ((Get-MetadataFingerprint $git -ContentOnly) -cne (Get-MetadataFingerprint (Join-Path $backup 'git') -ContentOnly)) {
            throw "Backup content differs from source; no recovery attempted. Keep $backup"
        }
        [IO.File]::WriteAllText((Join-Path $backup 'before.txt'), $before, [Text.UTF8Encoding]::new($false))
        Write-Host "Backup preserved outside checkout: $backup"
    }
    if ((Get-ReferenceFingerprint $Path) -cne $before) { throw 'Git metadata changed during backup/preflight; stop other Git clients and retry only after review.' }
    if ($AllowObjectDownload) {
        Receive-SparseObjects $Path $manifest
        if ((Get-ReferenceFingerprint $Path) -cne $before) { throw 'Non-object metadata changed during download; preserve backup and review before checkout.' }
    }
    Assert-NoGitOperation $Path
    $patterns = @(Get-DesiredSparsePatterns $Kind $manifest)
    $mode = if ($Kind -eq 'tbs') { '--cone' } else { '--no-cone' }
    # Only selected, Windows-safe paths may reach this command-scoped NTFS exception.
    # Git still validates excluded AUX paths while building the full sparse index.
    Invoke-GitChecked -GitArgs @('-c','core.protectNTFS=false','-C',$Path,'sparse-checkout','set',$mode,'--no-sparse-index','--stdin') -InputText ($patterns -join "`n") | Out-Null
    Invoke-GitChecked @('-c','core.protectNTFS=false','-C',$Path,'read-tree','-mu','HEAD') | Out-Null
    if ((Invoke-GitChecked @('-C',$Path,'rev-parse','HEAD')) -ne $beforeHead) { throw 'HEAD changed during sparse initialization; preserve backup and inspect manually.' }
    Assert-SparseReference $Path $Kind
    if ($backup) {
        [pscustomobject]@{ Result='PASS'; Head=$beforeHead; Files=$manifest.Count; Kind=$Kind } |
            ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backup 'after.json') -Encoding UTF8
    }
}

function Ensure-SparseReference {
    param([string]$Path, [ValidateSet('tbs','coreelec')][string]$Kind, [switch]$CheckOnly)
    Assert-NoGitOperation $Path
    if (-not (Test-Path -LiteralPath (Get-GitStoragePath $Path 'index') -PathType Leaf)) {
        if ($CheckOnly) { throw 'SPARSE_INDEX_MISSING: check-only will not download objects or repair the checkout.' }
        Write-Host "Incomplete $Kind clone: automatically recovering the original HEAD after backup."
        Initialize-SparseReference -Path $Path -Kind $Kind -Repair -AllowObjectDownload
        return
    }
    $status = Invoke-GitChecked @('-C',$Path,'status','--porcelain=v1','--untracked-files=all')
    if ($status) { throw 'Local changes/deletions exist; automatic repair cannot overwrite user work.' }
    $manifest = @(Get-SparseManifest $Path $Kind)
    if ($CheckOnly) { Assert-SparseReference $Path $Kind; return }
    # An index may be healthy while a partial clone has lost selected object payloads.
    Receive-SparseObjects $Path $manifest
    Assert-SparseReference $Path $Kind
}

function Sync-OneRepository {
    param([pscustomobject]$Repo)
    $path = Join-Path $Root $Repo.RelativePath
    try {
        Write-SyncLog "--- $($Repo.Name) ---"
        $isRepo = Test-Path -LiteralPath (Join-Path $path '.git')
        $fresh = $false
        $sparse = $Repo.Kind -in @('tbs','coreelec')
        if (-not $isRepo) {
            if ((Test-Path -LiteralPath $path) -and @(Get-ChildItem -LiteralPath $path -Force).Count -ne 0) {
                throw "Nonempty directory is not a Git repository: $path"
            }
            if ($CheckOnly) {
                Write-SyncLog "MISSING: $path (clone skipped in -CheckOnly)" 'WARN'
                return $true
            }
            if ($Repo.Kind -eq 'tbs') {
                Invoke-GitChecked -GitArgs @('clone','--quiet','--single-branch','--depth','1',
                    '--filter=blob:none','--no-checkout','--branch','latest',$Repo.Url,$path) | Out-Null
            } elseif ($Repo.Kind -eq 'coreelec') {
                # Track the current remote default branch as resolved by Git at clone time.
                Invoke-GitChecked -GitArgs @('clone','--quiet','--single-branch','--depth','1',
                    '--filter=blob:none','--no-checkout',$Repo.Url,$path) | Out-Null
            } else {
                Invoke-GitChecked -GitArgs @('clone','--quiet',$Repo.Url,$path) | Out-Null
            }
            $fresh = $true
            if ($sparse) { Write-SyncLog 'CLONED (initial checkout deferred until sparse manifest validation)' }
            else { Write-SyncLog 'CLONED' }
        }

        if ($sparse) {
            if (-not (Invoke-GitChecked @('-C',$path,'branch','--show-current'))) { throw 'Detached HEAD: no sparse recovery or update.' }
            $origin = Invoke-GitChecked @('-C',$path,'remote','get-url','origin')
            if ((Get-CanonicalUrl $origin) -ne (Get-CanonicalUrl $Repo.Url)) { throw 'Unexpected sparse reference origin; no recovery or checkout allowed.' }
            if ($fresh) {
                Receive-SparseObjects $path @(Get-SparseManifest $path $Repo.Kind)
                Initialize-SparseReference -Path $path -Kind $Repo.Kind -Fresh
            } else {
                Ensure-SparseReference -Path $path -Kind $Repo.Kind -CheckOnly:$CheckOnly
            }
            Assert-SparseReference -Path $path -Kind $Repo.Kind
        }
        $previous = Invoke-GitChecked -GitArgs @('-C',$path,'rev-parse','--verify','HEAD')
        Write-SyncLog "HEAD=$previous"
        if ($Repo.Kind -eq 'main') {
            Invoke-GitChecked -GitArgs @('-C',$path,'fetch','--quiet','--prune','--all') | Out-Null
            $branch = Invoke-GitChecked -GitArgs @('-C',$path,'branch','--show-current')
            Write-SyncLog "MAIN | branch=$branch | fetch only; no checkout or merge"
            return $true
        }

        $origin = Invoke-GitChecked -GitArgs @('-C',$path,'remote','get-url','origin')
        if ((Get-CanonicalUrl $origin) -ne (Get-CanonicalUrl $Repo.Url)) {
            throw "Unexpected origin $origin (expected $($Repo.Url)); skipped"
        }
        $branch = Invoke-GitChecked -GitArgs @('-C',$path,'branch','--show-current')
        if ([string]::IsNullOrWhiteSpace($branch)) { throw 'Detached HEAD: no update.' }
        Invoke-GitChecked -GitArgs @('-C',$path,'fetch','--quiet','--prune','origin') | Out-Null
        $tracking = Invoke-GitChecked -GitArgs @('-C',$path,'rev-parse','--abbrev-ref',
            '--symbolic-full-name','@{upstream}')
        if (-not $tracking.StartsWith('origin/')) { throw "Unexpected upstream: $tracking" }
        $counts = Invoke-GitChecked -GitArgs @('-C',$path,'rev-list','--left-right','--count',
            'HEAD...@{upstream}')
        if ($counts -notmatch '^\s*(\d+)\s+(\d+)\s*$') { throw "Bad ahead/behind: $counts" }
        $ahead = [int]$Matches[1]
        $behind = [int]$Matches[2]
        Write-SyncLog "branch=$branch | tracking=$tracking | ahead=$ahead | behind=$behind"
        $status = Invoke-GitChecked -GitArgs @('-C',$path,'status','--porcelain=v1',
            '--untracked-files=normal')
        if (-not [string]::IsNullOrWhiteSpace($status)) {
            Write-SyncLog 'LOCAL CHANGES: no fast-forward or sparse modifications.' 'WARN'
            return $true
        }
        if ($ahead -gt 0) {
            Write-SyncLog 'LOCAL COMMITS OR DIVERGENCE: skipped.' 'WARN'
            return $true
        }
        if ($behind -gt 0) {
            if ($CheckOnly) {
                Write-SyncLog "UPDATE AVAILABLE: $behind new commit(s); no merge" 'INFO'
            } else {
                if ($sparse) {
                    $targetSha = Invoke-GitChecked @('-C',$path,'rev-parse','@{upstream}')
                    $targetManifest = @(Get-SparseManifest -Path $path -Kind $Repo.Kind -Revision $targetSha)
                    $targetPatterns = @(Get-DesiredSparsePatterns $Repo.Kind $targetManifest | Sort-Object -Unique)
                    $currentManifest = @(Get-SparseManifest $path $Repo.Kind)
                    $currentPatterns = @(Get-DesiredSparsePatterns $Repo.Kind $currentManifest | Sort-Object -Unique)
                    if (@(Compare-Object $targetPatterns $currentPatterns -CaseSensitive).Count) {
                        throw 'Upstream introduces a different sparse selection; review the new scope before updating.'
                    }
                    Receive-SparseObjects $path $targetManifest
                    Assert-SparseReference $path $Repo.Kind
                    Invoke-GitChecked @('-c','core.protectNTFS=false','-C',$path,'merge','--ff-only',$targetSha) | Out-Null
                    Assert-SparseReference $path $Repo.Kind
                } else {
                    Invoke-GitChecked -GitArgs @('-C',$path,'merge','--ff-only','@{upstream}') | Out-Null
                }
                $newSha = Invoke-GitChecked -GitArgs @('-C',$path,'rev-parse','--verify','HEAD')
                Write-SyncLog "UPDATED $previous -> $newSha (fast-forward only)"
                if ($Repo.Kind -eq 'coreelec') {
                    $changes = Invoke-GitChecked -GitArgs @('-C',$path,'diff','--name-only',
                        $previous,$newSha,'--','drivers/media/usb/dvb-usb',
                        'drivers/media/dvb-frontends','drivers/media/dvb-core','drivers/media/tuners')
                    if ($changes) { Write-SyncLog "CoreELEC relevant upstream changes:`n$changes" }
                }
            }
        } else { Write-SyncLog 'UP TO DATE' }

        if ($sparse) {
            Assert-SparseReference $path $Repo.Kind
            Write-SyncLog "$($Repo.Kind) sparse manifest, index and materialized file hashes verified."
        }
        if ($Repo.Kind -eq 'coreelec') {
            try {
                $symref = Invoke-GitChecked @('ls-remote','--symref',$Repo.Url,'HEAD')
                foreach ($line in ($symref -split '\r?\n')) {
                    if ($line -match '^ref:\s+refs/heads/([^\s]+)\s+HEAD' -and $Matches[1] -ne $branch) {
                        Write-SyncLog "CoreELEC default branch now '$($Matches[1])', local '$branch'; manual migration review required." 'WARN'
                    }
                }
            } catch { Write-SyncLog 'Could not inspect current CoreELEC remote default branch.' 'WARN' }
        }
        return $true
    } catch {
        Write-SyncLog "ERROR in $($Repo.Name): $($_.Exception.Message)" 'ERROR'
        return $false
    }
}

function Invoke-RepositorySync {
    param([string]$WorkspaceRoot, [switch]$CheckOnly)
    $Root = [IO.Path]::GetFullPath($WorkspaceRoot)
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git executable was not found in PATH.' }
    if (-not (Test-Path -LiteralPath $Root -PathType Container)) { throw "Workspace does not exist: $Root" }
    $LogRoot = Join-Path $Root 'logs'
    New-Item -ItemType Directory -Path $LogRoot -Force | Out-Null
    $LogFile = Join-Path $LogRoot 'repository-sync.log'
    $hadErrors = $false
    Write-SyncLog "START | workspace=$Root | CheckOnly=$CheckOnly"
    foreach ($repo in $Repos) {
        if (-not (Sync-OneRepository -Repo $repo)) { $hadErrors = $true }
    }
    Write-SyncLog "END | log=$LogFile | errors=$hadErrors"
    return (-not $hadErrors)
}

# Dot-sourcing exposes testable helpers without network or filesystem effects.
if ($MyInvocation.InvocationName -eq '.') { return }
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'gradlew.bat') -PathType Leaf)) {
    throw 'Place this script in the AndroidDvbDriver tools directory before running it.'
}
if (-not (Invoke-RepositorySync -WorkspaceRoot (Split-Path -Parent $projectRoot) -CheckOnly:$CheckOnly)) { exit 1 }
