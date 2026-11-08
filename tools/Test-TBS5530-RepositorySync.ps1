#Requires -Version 5.1
# SPDX-License-Identifier: GPL-2.0-or-later
<#
.SYNOPSIS
  Kiểm thử sparse checkout bằng kho Git và remote bare hoàn toàn cục bộ.
.DESCRIPTION
  Không truy cập references thật, mạng, thiết bị hoặc cấu hình Git của người dùng.
  Fixture được tạo trong tools và luôn được dọn trong finally, kể cả khi kiểm thử lỗi.
.EXAMPLE
  .\tools\Test-TBS5530-RepositorySync.ps1
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'Bộ kiểm thử này cần Windows để tái hiện tên AUX không hợp lệ.' }
$script:TestGitExe = (Get-Command git -CommandType Application -ErrorAction Stop).Source
$script:TestSyncPath = Join-Path $PSScriptRoot 'Sync-TBS5530-Repositories.ps1'
$script:TestSuiteRoot = Join-Path $PSScriptRoot ('.repository-sync-tests-' + [guid]::NewGuid().ToString('N'))
$script:TestPassed = 0
$script:TestSkipped = 0
$script:TestEnvironment = @{}
$script:TestUtf8 = New-Object Text.UTF8Encoding($false)

function Invoke-TestGit {
    param([string[]]$Arguments, [switch]$AllowFailure)
    $oldPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = @(& $script:TestGitExe @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $oldPreference }
    $result = [pscustomobject]@{ ExitCode = $exitCode; Output = ($lines -join "`n").Trim() }
    if ($AllowFailure) { return $result }
    if ($exitCode -ne 0) { throw ('Git lỗi ({0}): git {1}`n{2}' -f $exitCode, ($Arguments -join ' '), $result.Output) }
    return $result.Output
}
function Assert-Test {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}
function Invoke-TestCase {
    param([string]$Name, [scriptblock]$Action)
    try { & $Action; $script:TestPassed++; Write-Host ('PASS: ' + $Name) }
    catch { throw ('FAIL: {0}: {1}' -f $Name, $_.Exception.Message) }
}
function Assert-TestThrows {
    param([scriptblock]$Action, [string]$Message)
    $failed = $false
    try { & $Action | Out-Null } catch { $failed = $true }
    Assert-Test $failed $Message
}
function Get-TestSnapshot {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return '<absent>' }
    $prefix = $Path.TrimEnd('\') + '\'
    $rows = @(Get-ChildItem -LiteralPath $Path -Force -Recurse | Sort-Object FullName | ForEach-Object {
        $relative = $_.FullName.Substring($prefix.Length)
        if ($_.PSIsContainer) { 'D ' + $relative }
        else { 'F ' + $relative + ' ' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
    })
    return ($rows -join "`n")
}
function Assert-TestRefusalUnchanged {
    param([string]$Path, [scriptblock]$Action)
    $before = Get-TestSnapshot $Path
    Assert-TestThrows $Action 'Thao tác đáng lẽ phải bị từ chối.'
    Assert-Test ((Get-TestSnapshot $Path) -ceq $before) 'Thao tác bị từ chối đã đổi byte hoặc cấu trúc fixture.'
}
function New-TestClone {
    param([string]$Name, [string]$Branch = 'main')
    $path = Join-Path $script:TestSuiteRoot $Name
    Invoke-TestGit -Arguments @('clone', '--quiet', '--no-checkout', '--no-hardlinks', '--branch', $Branch, $script:TestRemote, $path) | Out-Null
    Assert-Test (-not (Test-Path -LiteralPath (Join-Path $path '.git\index'))) 'Clone --no-checkout không được tạo index.'
    Assert-Test (@(Get-ChildItem -LiteralPath $path -Force | Where-Object Name -ne '.git').Count -eq 0) 'Clone mới không rỗng.'
    return $path
}
function New-TestInitializedClone {
    param([string]$Name, [string]$Kind)
    $path = New-TestClone $Name
    Initialize-SparseReference -Path $path -Kind $Kind -Fresh | Out-Null
    Assert-SparseReference -Path $path -Kind $Kind | Out-Null
    return $path
}
function New-TestRepositoryDefinition {
    param([string]$Name, [string]$Kind, [string]$Url = $script:TestRemote)
    return [pscustomobject]@{ Name = $Name; RelativePath = $Name; Url = $Url; Kind = $Kind }
}
function Remove-TestSelectedBlobs {
    param([string]$Path, [string]$Kind)
    $manifest = @(Get-SparseManifest -Path $Path -Kind $Kind)
    foreach ($oid in @($manifest | ForEach-Object Oid | Sort-Object -Unique)) {
        $objectPath = Join-Path $Path ('.git\objects\' + $oid.Substring(0, 2) + '\' + $oid.Substring(2))
        Assert-Test (Test-Path -LiteralPath $objectPath -PathType Leaf) 'Fixture cần loose object riêng trước khi xóa blob đã chọn.'
        Remove-Item -LiteralPath $objectPath -Force
    }
    Invoke-TestGit -Arguments @('-C', $Path, 'config', 'remote.origin.promisor', 'true') | Out-Null
    Invoke-TestGit -Arguments @('-C', $Path, 'config', 'remote.origin.partialclonefilter', 'blob:none') | Out-Null
}
function Get-TestBackupHeads {
    return @(Get-ChildItem -LiteralPath $LogRoot -Recurse -Force -File -Filter HEAD | Where-Object {
        Test-Path -LiteralPath (Join-Path $_.DirectoryName 'config') -PathType Leaf
    } | ForEach-Object FullName)
}
function Assert-TestNoPersistentOverride {
    param([string]$Path)
    $config = Invoke-TestGit -Arguments @('-C', $Path, 'config', '--get-all', 'core.protectNTFS') -AllowFailure
    Assert-Test ($config.ExitCode -eq 1 -and $config.Output -eq '') 'Không được lưu core.protectNTFS vào cấu hình kho.'
}
function Add-TestTreeEntry {
    param([string]$Path, [string]$Content)
    $blobPath = Join-Path $script:TestSuiteRoot 'blob-input.txt'
    [IO.File]::WriteAllText($blobPath, $Content + "`n", $script:TestUtf8)
    $oid = Invoke-TestGit -Arguments @('--git-dir', $script:TestRemote, 'hash-object', '-w', '--', $blobPath)
    Invoke-TestGit -Arguments @('-c', 'core.protectNTFS=false', '--git-dir', $script:TestRemote, 'update-index', '--add', '--cacheinfo', ('100644,' + $oid + ',' + $Path)) | Out-Null
}
function Save-TestCommit {
    param([string]$Branch, [string]$Parent)
    $tree = Invoke-TestGit -Arguments @('--git-dir', $script:TestRemote, 'write-tree')
    $arguments = @('-c', 'user.name=Repository Sync Tests', '-c', 'user.email=offline@example.invalid', '--git-dir', $script:TestRemote, 'commit-tree', $tree, '-m', ('fixture ' + $Branch))
    if ($Parent) { $arguments += @('-p', $Parent) }
    $commit = Invoke-TestGit -Arguments $arguments
    Invoke-TestGit -Arguments @('--git-dir', $script:TestRemote, 'update-ref', ('refs/heads/' + $Branch), $commit) | Out-Null
    return $commit
}
function Assert-TestManifest {
    param([string]$Path, [string]$Kind, [string[]]$Expected)
    $manifest = @(Get-SparseManifest -Path $Path -Kind $Kind)
    $actual = @($manifest | ForEach-Object { $_.Path } | Sort-Object)
    Assert-Test (($actual -join "`n") -ceq (($Expected | Sort-Object) -join "`n")) ('Manifest thiếu/thừa đường dẫn: ' + $Kind)
    foreach ($entry in $manifest) {
        $treeLine = Invoke-TestGit -Arguments @('-C', $Path, 'ls-tree', 'HEAD', '--', $entry.Path)
        Assert-Test ($treeLine -cmatch ('^' + [regex]::Escape([string]$entry.Mode) + ' blob ' + [regex]::Escape([string]$entry.Oid) + "`t")) ('Mode/OID không khớp HEAD: ' + $entry.Path)
        $file = Join-Path $Path ($entry.Path -replace '/', '\')
        Assert-Test (Test-Path -LiteralPath $file -PathType Leaf) ('Không materialize file đã chọn: ' + $entry.Path)
        $actualOid = Invoke-TestGit -Arguments @('-C', $Path, 'hash-object', '--no-filters', '--', $file)
        Assert-Test ($actualOid -ceq $entry.Oid) ('Hash worktree sai: ' + $entry.Path)
    }
}

try {
    New-Item -ItemType Directory -Path $script:TestSuiteRoot | Out-Null
    foreach ($name in @('GIT_ALLOW_PROTOCOL', 'GIT_NO_LAZY_FETCH', 'GIT_TERMINAL_PROMPT', 'GIT_CONFIG_NOSYSTEM', 'GIT_CONFIG_GLOBAL', 'GIT_OPTIONAL_LOCKS')) {
        $script:TestEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    $env:GIT_ALLOW_PROTOCOL = 'file'
    $env:GIT_NO_LAZY_FETCH = '1'
    $env:GIT_TERMINAL_PROMPT = '0'
    $env:GIT_OPTIONAL_LOCKS = '0'
    $env:GIT_CONFIG_NOSYSTEM = '1'
    $env:GIT_CONFIG_GLOBAL = Join-Path $script:TestSuiteRoot 'empty-gitconfig'
    [IO.File]::WriteAllText($env:GIT_CONFIG_GLOBAL, '', $script:TestUtf8)
    $script:TestRemote = Join-Path $script:TestSuiteRoot 'remote.git'
    Invoke-TestGit -Arguments @('init', '--quiet', '--bare', $script:TestRemote) | Out-Null
    Invoke-TestGit -Arguments @('--git-dir', $script:TestRemote, 'symbolic-ref', 'HEAD', 'refs/heads/main') | Out-Null
    $script:TestCoreFiles = @(
        'drivers/media/usb/dvb-usb/tbs5530.c', 'drivers/media/usb/dvb-usb/tbs5530.h',
        'drivers/media/usb/dvb-usb/dvb-usb.c', 'drivers/media/usb/dvb-usb/dvb-usb.h',
        'drivers/media/usb/dvb-usb/dvb-usb-common.h', 'drivers/media/usb/dvb-usb/dvb-usb-urb.c',
        'drivers/media/usb/dvb-usb/usb-urb.c', 'drivers/media/usb/dvb-usb/dvb-usb-firmware.c',
        'drivers/media/usb/dvb-usb/dvb-usb-i2c.c', 'drivers/media/usb/dvb-usb/Kconfig',
        'drivers/media/usb/dvb-usb/Makefile', 'drivers/media/dvb-frontends/cxd2878.c',
        'drivers/media/dvb-frontends/cxd2878.h', 'drivers/media/dvb-frontends/cxd2878_priv.h',
        'drivers/media/dvb-frontends/m88rs6060.c', 'drivers/media/dvb-frontends/m88rs6060.h',
        'drivers/media/dvb-frontends/m88rs6060_priv.h', 'drivers/media/dvb-frontends/Kconfig',
        'drivers/media/dvb-frontends/Makefile', 'drivers/media/dvb-core/dvb_frontend.c',
        'drivers/media/dvb-core/dvb_demux.c', 'drivers/media/dvb-core/Kconfig',
        'drivers/media/dvb-core/Makefile', 'drivers/media/tuners/Kconfig',
        'drivers/media/tuners/Makefile', 'include/media/dvb_frontend.h',
        'include/media/dvb-usb.h', 'include/media/dvb_demux.h', 'include/uapi/linux/dvb/frontend.h'
    )
    $script:TestDiscoveredFiles = @(
        'drivers/media/usb/dvb-usb/tbs5530-extra.c',
        'drivers/media/common/tbs5530-helper.h',
        'drivers/media/dvb-frontends/cxd2878_monitor.c',
        'drivers/media/dvb-frontends/m88rs6060_firmware.h'
    )
    $script:TestAncestorFiles = @('README', 'drivers/Makefile', 'drivers/media/Makefile',
        'drivers/media/usb/Makefile', 'include/Makefile', 'include/uapi/Makefile', 'include/uapi/linux/Makefile')
    $script:TestExtraTbsFiles = @('drivers/media/tuners/fixture-tuner.c',
        'drivers/media/dvb-core/fixture-core.h', 'include/media/fixture-media.h', 'include/uapi/linux/dvb/fixture-dvb.h')
    $script:TestAllFiles = @($script:TestCoreFiles + $script:TestDiscoveredFiles + $script:TestAncestorFiles + $script:TestExtraTbsFiles + @(
        'drivers/gpu/aux.c', 'drivers/gpu/unselected.c', 'drivers/media/usb/other/unselected.c',
        'drivers/media/common/unselected.c', 'include/uapi/linux/unselected/unused.h'
    ) | Sort-Object -Unique)
    foreach ($file in $script:TestAllFiles) { Add-TestTreeEntry $file ('fixture bytes for ' + $file) }
    $script:TestMainCommit = Save-TestCommit 'main'
    Invoke-TestGit -Arguments @('-c', 'core.protectNTFS=false', '--git-dir', $script:TestRemote, '--work-tree', $script:TestSuiteRoot, 'update-index', '--force-remove', '--', 'drivers/media/usb/dvb-usb/tbs5530.c') | Out-Null
    Save-TestCommit 'missing-essential' | Out-Null
    Add-TestTreeEntry 'drivers/media/usb/dvb-usb/tbs5530.c' 'fixture bytes for drivers/media/usb/dvb-usb/tbs5530.c'
    Add-TestTreeEntry 'drivers/media/dvb-frontends/AUX.c' 'selected reserved name for TBS'
    Add-TestTreeEntry 'drivers/media/AUX/cxd2878_extra.c' 'selected reserved directory for CoreELEC discovery'
    Save-TestCommit 'reserved-selected' | Out-Null

    Invoke-TestCase 'Dot-source không tạo root, references, log hoặc thay đổi fixture' {
        $beforeImport = Get-TestSnapshot $script:TestSuiteRoot
        . $script:TestSyncPath
        Assert-Test ((Get-TestSnapshot $script:TestSuiteRoot) -ceq $beforeImport) 'Dot-source đã ghi file.'
        foreach ($name in @('Initialize-SparseReference', 'Assert-SparseReference', 'Get-SparseManifest', 'Ensure-SparseReference', 'Sync-OneRepository', 'Invoke-RepositorySync')) {
            Assert-Test ($null -ne (Get-Command $name -CommandType Function -ErrorAction SilentlyContinue)) ('Thiếu helper: ' + $name)
        }
    }
    # Import into the enclosing scope after checking the import's side effects.
    . $script:TestSyncPath
    $LogRoot = Join-Path $script:TestSuiteRoot 'logs'
    $LogFile = Join-Path $LogRoot 'repository-sync.log'
    New-Item -ItemType Directory -Path $LogRoot | Out-Null

    Invoke-TestCase 'Tái hiện lỗi cũ: read-tree toàn cây vướng drivers/gpu/aux.c' {
        $path = New-TestClone 'old-read-tree'
        $result = Invoke-TestGit -Arguments @('-C', $path, '-c', 'core.protectNTFS=true', 'read-tree', '-m', '-u', 'HEAD') -AllowFailure
        Assert-Test ($result.ExitCode -ne 0 -and $result.Output -match '(?i)(invalid path|aux\.c)') 'Fixture không tái hiện lỗi tên AUX của checkout toàn cây.'
        Assert-TestNoPersistentOverride $path
    }

    foreach ($kind in @('tbs', 'coreelec')) {
        Invoke-TestCase ($kind + ': clone --no-checkout, khởi tạo mới và Assert lặp lại') {
            $path = New-TestClone ($kind + '-fresh')
            $output = @(Initialize-SparseReference -Path $path -Kind $kind -Fresh)
            Assert-Test ($output.Count -eq 0) 'Initialize phải trả về void.'
            $output = @(Assert-SparseReference -Path $path -Kind $kind)
            Assert-Test ($output.Count -eq 0) 'Assert phải trả về void.'
            Assert-SparseReference -Path $path -Kind $kind
            Assert-Test (-not (Test-Path -LiteralPath (Join-Path $path 'drivers\gpu'))) 'Đã materialize thư mục GPU bị loại.'
            Assert-Test (-not (Test-Path -LiteralPath (Join-Path $path 'drivers\media\usb\other'))) 'Đã materialize thư mục USB ngoài phạm vi.'
            $expected = @($script:TestCoreFiles + $script:TestDiscoveredFiles)
            if ($kind -eq 'tbs') {
                $expected = @($script:TestAllFiles | Where-Object {
                    $_ -in $script:TestAncestorFiles -or $_ -match '^(drivers/media/(usb/dvb-usb|dvb-frontends|dvb-core|tuners)|include/(media|uapi/linux/dvb))/'
                })
                foreach ($file in $script:TestAncestorFiles) {
                    Assert-Test (Test-Path -LiteralPath (Join-Path $path ($file -replace '/', '\'))) ('Cone không giữ ancestor file: ' + $file)
                }
                $listed = (Invoke-TestGit -Arguments @('-C', $path, 'sparse-checkout', 'list')) -split "`n"
                $six = @('drivers/media/usb/dvb-usb', 'drivers/media/dvb-frontends', 'drivers/media/dvb-core', 'drivers/media/tuners', 'include/media', 'include/uapi/linux/dvb')
                Assert-Test ((($listed | Sort-Object) -join "`n") -ceq (($six | Sort-Object) -join "`n")) 'TBS không giữ đúng sáu cone directory.'
            } else {
                Assert-Test (-not (Test-Path -LiteralPath (Join-Path $path 'drivers\media\tuners\fixture-tuner.c'))) 'CoreELEC đã mở rộng sang cả directory.'
                Assert-Test ((Invoke-TestGit -Arguments @('-C', $path, 'config', '--get', 'core.sparseCheckoutCone')) -ceq 'false') 'CoreELEC phải dùng non-cone.'
            }
            Assert-TestManifest $path $kind $expected
            Assert-TestNoPersistentOverride $path
        }
        Invoke-TestCase ($kind + ': thiếu index cần Repair rõ ràng, backup ngoài worktree') {
            $path = New-TestClone ($kind + '-repair')
            Assert-TestRefusalUnchanged $path { Assert-SparseReference -Path $path -Kind $kind }
            Assert-TestRefusalUnchanged $path { Initialize-SparseReference -Path $path -Kind $kind }
            $beforePaths = @(Get-ChildItem -LiteralPath $script:TestSuiteRoot -Force -Recurse -File | ForEach-Object FullName)
            Initialize-SparseReference -Path $path -Kind $kind -Repair
            Assert-SparseReference -Path $path -Kind $kind
            $newBackupHeads = @(Get-ChildItem -LiteralPath $script:TestSuiteRoot -Force -Recurse -File -Filter HEAD | Where-Object {
                $_.FullName -notin $beforePaths -and -not $_.FullName.StartsWith($path + '\', [StringComparison]::OrdinalIgnoreCase) -and
                (Test-Path -LiteralPath (Join-Path $_.DirectoryName 'config') -PathType Leaf)
            })
            Assert-Test ($newBackupHeads.Count -gt 0) 'Repair không tạo backup metadata Git ngoài worktree.'
            foreach ($head in $newBackupHeads) {
                Assert-Test ([IO.File]::ReadAllText($head.FullName) -ceq [IO.File]::ReadAllText((Join-Path $path '.git\HEAD'))) 'HEAD backup không khớp.'
                Assert-Test (Test-Path -LiteralPath (Join-Path $head.DirectoryName 'config')) 'Backup thiếu config.'
            }
            Assert-TestNoPersistentOverride $path
        }
        foreach ($state in @('dirty', 'staged-delete', 'missing-worktree', 'skip-worktree-mismatch')) {
            Invoke-TestCase ($kind + ': giữ nguyên trạng thái ' + $state) {
                $path = New-TestInitializedClone ($kind + '-' + $state) $kind
                $relative = 'drivers/media/usb/dvb-usb/tbs5530.c'
                $file = Join-Path $path 'drivers\media\usb\dvb-usb\tbs5530.c'
                switch ($state) {
                    'dirty' { [IO.File]::AppendAllText($file, 'local user bytes', $script:TestUtf8) }
                    'staged-delete' { Invoke-TestGit -Arguments @('-C', $path, 'rm', '--quiet', '--', $relative) | Out-Null }
                    'missing-worktree' { Remove-Item -LiteralPath $file }
                    'skip-worktree-mismatch' {
                        Invoke-TestGit -Arguments @('-C', $path, 'update-index', '--skip-worktree', '--', $relative) | Out-Null
                        [IO.File]::AppendAllText($file, 'hidden user bytes', $script:TestUtf8)
                    }
                }
                Assert-TestRefusalUnchanged $path { Assert-SparseReference -Path $path -Kind $kind }
                Assert-TestRefusalUnchanged $path { Initialize-SparseReference -Path $path -Kind $kind -Repair }
                Assert-TestNoPersistentOverride $path
            }
        }
        Invoke-TestCase ($kind + ': worktree không rỗng và thiếu index không được sửa') {
            $path = New-TestClone ($kind + '-untracked')
            [IO.File]::WriteAllText((Join-Path $path 'user-notes.txt'), 'must remain byte-for-byte', $script:TestUtf8)
            Assert-TestRefusalUnchanged $path { Initialize-SparseReference -Path $path -Kind $kind -Repair }
        }
        foreach ($lock in @('index.lock', 'HEAD.lock', 'config.lock', 'info\sparse-checkout.lock')) {
            Invoke-TestCase ($kind + ': từ chối lock ' + $lock) {
                $path = New-TestClone ($kind + '-lock-' + ($lock -replace '[\\.]', '-'))
                $lockPath = Join-Path (Join-Path $path '.git') $lock
                New-Item -ItemType Directory -Path (Split-Path -Parent $lockPath) -Force | Out-Null
                [IO.File]::WriteAllText($lockPath, 'owned by another Git process', $script:TestUtf8)
                Assert-TestRefusalUnchanged $path { Initialize-SparseReference -Path $path -Kind $kind -Repair }
            }
        }
        Invoke-TestCase ($kind + ': branch thiếu essential bị chặn trước mọi ghi') {
            $path = New-TestClone ($kind + '-missing-essential') 'missing-essential'
            Assert-TestRefusalUnchanged $path { Initialize-SparseReference -Path $path -Kind $kind -Fresh }
        }
        Invoke-TestCase ($kind + ': đường dẫn được chọn có tên Windows dành riêng bị chặn') {
            $path = New-TestClone ($kind + '-reserved-selected') 'reserved-selected'
            Assert-TestRefusalUnchanged $path { Initialize-SparseReference -Path $path -Kind $kind -Fresh }
            Assert-TestNoPersistentOverride $path
        }
        Invoke-TestCase ($kind + ': blob được chọn thiếu phải fail closed, không fetch') {
            $path = New-TestClone ($kind + '-missing-blob')
            $oid = Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'HEAD:drivers/media/usb/dvb-usb/tbs5530.c')
            $objectPath = Join-Path (Join-Path $path ('.git\objects\' + $oid.Substring(0, 2))) $oid.Substring(2)
            Assert-Test (Test-Path -LiteralPath $objectPath -PathType Leaf) 'Clone fixture không có loose object riêng.'
            Remove-Item -LiteralPath $objectPath -Force
            Invoke-TestGit -Arguments @('-C', $path, 'config', 'remote.origin.promisor', 'true') | Out-Null
            Invoke-TestGit -Arguments @('-C', $path, 'config', 'remote.origin.partialclonefilter', 'blob:none') | Out-Null
            Invoke-TestGit -Arguments @('-C', $path, 'remote', 'set-url', 'origin', (Join-Path $script:TestSuiteRoot 'nonexistent-offline-remote.git')) | Out-Null
            Assert-TestRefusalUnchanged $path { Initialize-SparseReference -Path $path -Kind $kind -Fresh }
            Assert-Test (-not (Test-Path -LiteralPath $objectPath)) 'Helper đã tải lại blob ngầm.'
            Assert-Test ((Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'HEAD')) -ceq $script:TestMainCommit) 'Thiếu blob đã làm đổi HEAD.'
        }
        Invoke-TestCase ($kind + ': tải blob được cho phép chỉ từ remote cục bộ, backup trước tải') {
            $path = New-TestClone ($kind + '-explicit-local-download')
            $downloadManifest = @(Get-SparseManifest -Path $path -Kind $kind)
            foreach ($oid in @($downloadManifest | ForEach-Object Oid | Sort-Object -Unique)) {
                $objectPath = Join-Path (Join-Path $path ('.git\objects\' + $oid.Substring(0, 2))) $oid.Substring(2)
                Assert-Test (Test-Path -LiteralPath $objectPath -PathType Leaf) 'Fixture tải chủ động cần loose object riêng.'
                Remove-Item -LiteralPath $objectPath -Force
            }
            Invoke-TestGit -Arguments @('-C', $path, 'config', 'remote.origin.promisor', 'true') | Out-Null
            Invoke-TestGit -Arguments @('-C', $path, 'config', 'remote.origin.partialclonefilter', 'blob:none') | Out-Null
            Invoke-TestGit -Arguments @('--git-dir', $script:TestRemote, 'config', 'uploadpack.allowAnySHA1InWant', 'true') | Out-Null
            Invoke-TestGit -Arguments @('--git-dir', $script:TestRemote, 'config', 'uploadpack.allowFilter', 'true') | Out-Null
            Assert-TestRefusalUnchanged $path { Initialize-SparseReference -Path $path -Kind $kind -Fresh -AllowObjectDownload }
            $beforeGit = Get-TestSnapshot (Join-Path $path '.git')
            $beforeRefs = Invoke-TestGit -Arguments @('-C', $path, 'show-ref')
            $beforeHead = Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'HEAD')
            $beforeBackupHeads = @(Get-ChildItem -LiteralPath $LogRoot -Recurse -Force -File -Filter HEAD | ForEach-Object FullName)
            Initialize-SparseReference -Path $path -Kind $kind -Repair -AllowObjectDownload
            Assert-SparseReference -Path $path -Kind $kind
            Assert-Test ((Invoke-TestGit -Arguments @('-C', $path, 'show-ref')) -ceq $beforeRefs) 'Tải blob đã thay đổi ref.'
            Assert-Test ((Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'HEAD')) -ceq $beforeHead) 'Tải blob đã thay đổi HEAD.'
            Assert-Test (-not (Test-Path -LiteralPath (Join-Path $path '.git\FETCH_HEAD'))) 'Tải blob không được tạo FETCH_HEAD.'
            $backupHeads = @(Get-ChildItem -LiteralPath $LogRoot -Recurse -Force -File -Filter HEAD | Where-Object {
                $_.FullName -notin $beforeBackupHeads -and
                (Test-Path -LiteralPath (Join-Path $_.DirectoryName 'config') -PathType Leaf)
            })
            Assert-Test ($backupHeads.Count -eq 1) 'Tải chủ động cần đúng một backup Git.'
            Assert-Test ((Get-TestSnapshot $backupHeads[0].DirectoryName) -ceq $beforeGit) 'Backup không khớp metadata trước tải blob.'
            $afterPath = Join-Path (Split-Path -Parent $backupHeads[0].DirectoryName) 'after.json'
            Assert-Test (Test-Path -LiteralPath $afterPath -PathType Leaf) 'Thiếu after.json sau phục hồi.'
            Get-Content -LiteralPath $afterPath -Raw | ConvertFrom-Json | Out-Null
            Assert-TestNoPersistentOverride $path
        }
        Invoke-TestCase ($kind + ': phục hồi clone partial/shallow thật có promisor pack') {
            $path = Join-Path $script:TestSuiteRoot ($kind + '-real-partial-shallow')
            $remoteUri = ([uri]($script:TestRemote.TrimEnd('\') + '\')).AbsoluteUri
            Invoke-TestGit -Arguments @('clone','--quiet','--no-checkout','--single-branch','--depth','1',
                '--filter=blob:none','--branch','main',$remoteUri,$path) | Out-Null
            Assert-Test ((Invoke-TestGit -Arguments @('-C',$path,'rev-parse','--is-shallow-repository')) -eq 'true') 'Fixture phải là shallow clone thật.'
            Assert-Test (@(Get-ChildItem -LiteralPath (Join-Path $path '.git\objects\pack') -Filter '*.promisor').Count -gt 0) 'Fixture thiếu promisor pack; không tái hiện partial clone thật.'
            Assert-Test (-not (Test-Path -LiteralPath (Join-Path $path '.git\index'))) 'Clone chưa checkout không được có index.'
            $manifest = @(Get-SparseManifest -Path $path -Kind $kind)
            $beforeHead = Invoke-TestGit -Arguments @('-C',$path,'rev-parse','HEAD')
            $beforeRefs = Invoke-TestGit -Arguments @('-C',$path,'show-ref')
            $fetchHead = Join-Path $path '.git\FETCH_HEAD'
            $beforeFetchHead = if (Test-Path -LiteralPath $fetchHead) { [IO.File]::ReadAllText($fetchHead) } else { $null }
            $absent = Invoke-TestGit -Arguments @('-C',$path,'cat-file','-e',$manifest[0].Oid) -AllowFailure
            Assert-Test ($absent.ExitCode -ne 0) 'Selected blob phải thiếu thật trước download.'
            Initialize-SparseReference -Path $path -Kind $kind -Repair -AllowObjectDownload
            Assert-SparseReference -Path $path -Kind $kind
            Assert-Test ((Invoke-TestGit -Arguments @('-C',$path,'rev-parse','HEAD')) -ceq $beforeHead) 'Promisor fetch làm thay đổi HEAD.'
            Assert-Test ((Invoke-TestGit -Arguments @('-C',$path,'show-ref')) -ceq $beforeRefs) 'Promisor fetch làm thay đổi refs.'
            $afterFetchHead = if (Test-Path -LiteralPath $fetchHead) { [IO.File]::ReadAllText($fetchHead) } else { $null }
            Assert-Test ($afterFetchHead -ceq $beforeFetchHead) 'Promisor fetch làm thay đổi FETCH_HEAD.'
            $negotiation = Invoke-TestGit -Arguments @('-C',$path,'config','--local','--get','fetch.negotiationAlgorithm') -AllowFailure
            Assert-Test ($negotiation.ExitCode -eq 1) 'Không được lưu negotiationAlgorithm vào repo config.'
            $unselected = Invoke-TestGit -Arguments @('-C',$path,'rev-parse','HEAD:drivers/gpu/unselected.c')
            $unselectedCheck = Invoke-TestGit -Arguments @('-C',$path,'cat-file','-e',$unselected) -AllowFailure
            Assert-Test ($unselectedCheck.ExitCode -ne 0) 'Đã tải blob ngoài selected manifest.'
            Assert-Test (-not (Test-Path -LiteralPath (Join-Path $path 'drivers\gpu'))) 'Đã materialize path ngoài sparse.'
            Assert-TestNoPersistentOverride $path
        }
        Invoke-TestCase ($kind + ': sparse patterns bị sửa phải bị phát hiện') {
            $path = New-TestInitializedClone ($kind + '-patterns') $kind
            $patterns = Join-Path $path '.git\info\sparse-checkout'
            [IO.File]::AppendAllText($patterns, "`n/drivers/gpu/`n", $script:TestUtf8)
            Assert-TestRefusalUnchanged $path { Assert-SparseReference -Path $path -Kind $kind }
        }
    }

    Invoke-TestCase 'CLI công khai chỉ có switch CheckOnly, không lộ helper hoặc cờ cũ' {
        $tokens = $null
        $parseErrors = $null
        $ast = [Management.Automation.Language.Parser]::ParseFile($script:TestSyncPath, [ref]$tokens, [ref]$parseErrors)
        Assert-Test (@($parseErrors).Count -eq 0) 'Script đồng bộ có lỗi cú pháp.'
        $parameters = @($ast.ParamBlock.Parameters)
        Assert-Test ($parameters.Count -eq 1 -and $parameters[0].Name.VariablePath.UserPath -ceq 'CheckOnly') 'CLI phải chỉ khai báo CheckOnly; không tính common parameters của CmdletBinding.'
        Assert-Test ($parameters[0].StaticType -eq [Management.Automation.SwitchParameter]) 'CheckOnly phải là switch.'
        $helpComments = @($tokens | Where-Object Kind -eq Comment | ForEach-Object Text) -join "`n"
        Assert-Test ($helpComments -notmatch '(?i)(?<![\w-])-(Root|Only|Watch|PollMinutes|RepairSparse|AllowObjectDownload|FunctionsOnly)\b') 'Comment/help còn hướng dẫn cờ CLI đã bỏ.'
        foreach ($name in @('Fresh', 'Repair', 'AllowObjectDownload')) {
            Assert-Test ((Get-Command Initialize-SparseReference).Parameters.ContainsKey($name)) ('Helper nội bộ mất seam: ' + $name)
        }
    }

    foreach ($kind in @('tbs', 'coreelec')) {
        Invoke-TestCase ($kind + ': Ensure mặc định tự phục hồi clone hỏng và blob thiếu, backup trước tải') {
            $path = New-TestClone ($kind + '-ensure-broken')
            Remove-TestSelectedBlobs $path $kind
            $beforeGit = Get-TestSnapshot (Join-Path $path '.git')
            $beforeHead = Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'HEAD')
            $beforeRefs = Invoke-TestGit -Arguments @('-C', $path, 'show-ref')
            $beforeBackups = @(Get-TestBackupHeads)
            Ensure-SparseReference -Path $path -Kind $kind
            Assert-SparseReference -Path $path -Kind $kind
            $newBackups = @(Get-TestBackupHeads | Where-Object { $_ -notin $beforeBackups })
            Assert-Test ($newBackups.Count -eq 1) 'Ensure cần đúng một backup trước tự phục hồi.'
            Assert-Test ((Get-TestSnapshot (Split-Path -Parent $newBackups[0])) -ceq $beforeGit) 'Backup tự phục hồi không khớp metadata trước tải blob.'
            Assert-Test ((Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'HEAD')) -ceq $beforeHead) 'Ensure đã đổi HEAD.'
            Assert-Test ((Invoke-TestGit -Arguments @('-C', $path, 'show-ref')) -ceq $beforeRefs) 'Ensure đã đổi refs.'
            Assert-Test (-not (Test-Path -LiteralPath (Join-Path $path '.git\FETCH_HEAD'))) 'Tải selected blobs không được tạo FETCH_HEAD.'
            Assert-TestNoPersistentOverride $path
            $healthy = Get-TestSnapshot $path
            Ensure-SparseReference -Path $path -Kind $kind
            Assert-Test ((Get-TestSnapshot $path) -ceq $healthy) 'Ensure mặc định lần hai không idempotent.'
            Assert-Test ((@(Get-TestBackupHeads | Sort-Object) -join "`n") -ceq ((@($beforeBackups) + $newBackups | Sort-Object) -join "`n")) 'Ensure lần hai tạo backup thừa.'
        }
        Invoke-TestCase ($kind + ': Ensure CheckOnly clone hỏng không tải, backup hoặc sửa') {
            $path = New-TestClone ($kind + '-ensure-checkonly')
            Remove-TestSelectedBlobs $path $kind
            $beforeBackups = @(Get-TestBackupHeads) -join "`n"
            Assert-TestRefusalUnchanged $path { Ensure-SparseReference -Path $path -Kind $kind -CheckOnly }
            Assert-Test ((@(Get-TestBackupHeads) -join "`n") -ceq $beforeBackups) 'CheckOnly đã tạo backup.'
        }
        Invoke-TestCase ($kind + ': Ensure index khỏe nhưng thiếu blob tự tải sau kiểm tra trạng thái') {
            $path = New-TestInitializedClone ($kind + '-ensure-index-missing-blob') $kind
            Remove-TestSelectedBlobs $path $kind
            $beforeIndex = (Get-FileHash -LiteralPath (Join-Path $path '.git\index') -Algorithm SHA256).Hash
            $beforeHead = Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'HEAD')
            Ensure-SparseReference -Path $path -Kind $kind
            Assert-SparseReference -Path $path -Kind $kind
            Assert-Test ((Get-FileHash -LiteralPath (Join-Path $path '.git\index') -Algorithm SHA256).Hash -ceq $beforeIndex) 'Tải blob cho index khỏe đã đổi index.'
            Assert-Test ((Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'HEAD')) -ceq $beforeHead) 'Tải blob cho index khỏe đã đổi HEAD.'
        }
        Invoke-TestCase ($kind + ': Ensure CheckOnly index khỏe thiếu blob không tải ngầm') {
            $path = New-TestInitializedClone ($kind + '-ensure-index-checkonly-blob') $kind
            Remove-TestSelectedBlobs $path $kind
            $beforeBackups = @(Get-TestBackupHeads) -join "`n"
            Assert-TestRefusalUnchanged $path { Ensure-SparseReference -Path $path -Kind $kind -CheckOnly }
            Assert-Test ((@(Get-TestBackupHeads) -join "`n") -ceq $beforeBackups) 'CheckOnly index khỏe đã tạo backup.'
        }
        foreach ($state in @('dirty', 'dirty-missing-blob', 'staged-delete', 'missing-worktree', 'skip-worktree-mismatch', 'untracked', 'untracked-no-index')) {
            Invoke-TestCase ($kind + ': Ensure không ghi đè local ' + $state) {
                $path = New-TestInitializedClone ($kind + '-ensure-refuse-' + $state) $kind
                $file = Join-Path $path 'drivers\media\usb\dvb-usb\tbs5530.c'
                switch ($state) {
                    'dirty' { [IO.File]::AppendAllText($file, 'local user bytes', $script:TestUtf8) }
                    'dirty-missing-blob' {
                        [IO.File]::AppendAllText($file, 'local user bytes', $script:TestUtf8)
                        Remove-TestSelectedBlobs $path $kind
                    }
                    'staged-delete' { Invoke-TestGit -Arguments @('-C', $path, 'rm', '--quiet', '--', 'drivers/media/usb/dvb-usb/tbs5530.c') | Out-Null }
                    'missing-worktree' { Remove-Item -LiteralPath $file }
                    'skip-worktree-mismatch' {
                        Invoke-TestGit -Arguments @('-C', $path, 'update-index', '--skip-worktree', '--', 'drivers/media/usb/dvb-usb/tbs5530.c') | Out-Null
                        [IO.File]::AppendAllText($file, 'hidden user bytes', $script:TestUtf8)
                    }
                    'untracked' { [IO.File]::WriteAllText((Join-Path $path 'user-notes.txt'), 'keep user work', $script:TestUtf8) }
                    'untracked-no-index' {
                        Remove-Item -LiteralPath (Join-Path $path '.git\index')
                        [IO.File]::WriteAllText((Join-Path $path 'user-notes.txt'), 'keep user work', $script:TestUtf8)
                    }
                }
                $beforeBackups = @(Get-TestBackupHeads) -join "`n"
                Assert-TestRefusalUnchanged $path { Ensure-SparseReference -Path $path -Kind $kind }
                Assert-Test ((@(Get-TestBackupHeads) -join "`n") -ceq $beforeBackups) 'Trạng thái local bị từ chối vẫn tạo backup.'
            }
        }
        foreach ($lock in @('index.lock', 'HEAD.lock', 'config.lock', 'info\sparse-checkout.lock')) {
            Invoke-TestCase ($kind + ': Ensure từ chối lock ' + $lock) {
                $path = New-TestClone ($kind + '-ensure-lock-' + ($lock -replace '[\\.]', '-'))
                $lockPath = Join-Path (Join-Path $path '.git') $lock
                New-Item -ItemType Directory -Path (Split-Path -Parent $lockPath) -Force | Out-Null
                [IO.File]::WriteAllText($lockPath, 'owned by another Git process', $script:TestUtf8)
                $beforeBackups = @(Get-TestBackupHeads) -join "`n"
                Assert-TestRefusalUnchanged $path { Ensure-SparseReference -Path $path -Kind $kind }
                Assert-Test ((@(Get-TestBackupHeads) -join "`n") -ceq $beforeBackups) 'Lock bị từ chối vẫn tạo backup.'
            }
        }
    }

    Invoke-TestGit -Arguments @('--git-dir', $script:TestRemote, 'update-ref', 'refs/heads/latest', $script:TestMainCommit) | Out-Null
    Invoke-TestGit -Arguments @('--git-dir', $script:TestRemote, 'update-ref', 'refs/heads/fixture-update', $script:TestMainCommit) | Out-Null
    $Root = $script:TestSuiteRoot
    $CheckOnly = $false
    $updateClones = @{}
    foreach ($kind in @('tbs', 'coreelec')) {
        $updateClones[$kind] = New-TestClone ($kind + '-sync-broken-update') 'fixture-update'
        $updateClones[$kind + '-check'] = New-TestClone ($kind + '-sync-healthy-check') 'fixture-update'
        Initialize-SparseReference -Path $updateClones[$kind + '-check'] -Kind $kind -Fresh
    }
    Invoke-TestGit -Arguments @('-c', 'core.protectNTFS=false', '--git-dir', $script:TestRemote, 'read-tree', $script:TestMainCommit) | Out-Null
    Add-TestTreeEntry 'drivers/media/usb/dvb-usb/tbs5530.c' 'fixture updated selected source'
    $script:TestUpdatedCommit = Save-TestCommit 'fixture-update' $script:TestMainCommit

    foreach ($kind in @('tbs', 'coreelec')) {
        Invoke-TestCase ($kind + ': Sync-OneRepository mặc định phục hồi rồi tiếp tục fast-forward trong một lần') {
            $path = $updateClones[$kind]
            Remove-TestSelectedBlobs $path $kind
            $definition = New-TestRepositoryDefinition (Split-Path -Leaf $path) $kind
            $CheckOnly = $false
            Assert-Test (Sync-OneRepository -Repo $definition) 'Sync-OneRepository không hoàn tất tự phục hồi + đồng bộ.'
            Assert-SparseReference -Path $path -Kind $kind
            Assert-Test ((Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'HEAD')) -ceq $script:TestUpdatedCommit) 'Sync-OneRepository dừng sau phục hồi mà chưa fast-forward.'
            Assert-TestNoPersistentOverride $path
            $beforeBackups = @(Get-TestBackupHeads) -join "`n"
            Assert-Test (Sync-OneRepository -Repo $definition) 'Lặp đồng bộ mặc định thất bại.'
            Assert-Test ((Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'HEAD')) -ceq $script:TestUpdatedCommit) 'Lặp đồng bộ đổi HEAD ngoài ý muốn.'
            Assert-Test ((@(Get-TestBackupHeads) -join "`n") -ceq $beforeBackups) 'Lặp đồng bộ tạo backup thừa.'
        }
        Invoke-TestCase ($kind + ': Sync-OneRepository CheckOnly clone hỏng không fetch hoặc sửa') {
            $path = New-TestClone ($kind + '-sync-check-broken')
            Remove-TestSelectedBlobs $path $kind
            $before = Get-TestSnapshot $path
            $beforeBackups = @(Get-TestBackupHeads) -join "`n"
            $CheckOnly = $true
            Assert-Test (-not (Sync-OneRepository -Repo (New-TestRepositoryDefinition (Split-Path -Leaf $path) $kind))) 'CheckOnly phải báo lỗi clone hỏng.'
            Assert-Test ((Get-TestSnapshot $path) -ceq $before) 'CheckOnly clone hỏng đã đổi checkout/metadata.'
            Assert-Test ((@(Get-TestBackupHeads) -join "`n") -ceq $beforeBackups) 'CheckOnly clone hỏng đã tạo backup.'
        }
        Invoke-TestCase ($kind + ': Sync-OneRepository CheckOnly khỏe có fetch nhưng không merge hoặc đổi index') {
            $path = $updateClones[$kind + '-check']
            $beforeIndex = (Get-FileHash -LiteralPath (Join-Path $path '.git\index') -Algorithm SHA256).Hash
            $beforeFile = [IO.File]::ReadAllText((Join-Path $path 'drivers\media\usb\dvb-usb\tbs5530.c'))
            $CheckOnly = $true
            Assert-Test (Sync-OneRepository -Repo (New-TestRepositoryDefinition (Split-Path -Leaf $path) $kind)) 'CheckOnly khỏe thất bại.'
            Assert-Test ((Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'origin/fixture-update')) -ceq $script:TestUpdatedCommit) 'CheckOnly không fetch remote local.'
            Assert-Test ((Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'HEAD')) -ceq $script:TestMainCommit) 'CheckOnly đã merge.'
            Assert-Test ((Get-FileHash -LiteralPath (Join-Path $path '.git\index') -Algorithm SHA256).Hash -ceq $beforeIndex) 'CheckOnly đã đổi index.'
            Assert-Test ([IO.File]::ReadAllText((Join-Path $path 'drivers\media\usb\dvb-usb\tbs5530.c')) -ceq $beforeFile) 'CheckOnly đã đổi worktree.'
            Assert-SparseReference -Path $path -Kind $kind
        }
        Invoke-TestCase ($kind + ': Sync-OneRepository mặc định clone mới chỉ qua remote local') {
            $name = $kind + '-sync-fresh'
            $CheckOnly = $false
            Assert-Test (Sync-OneRepository -Repo (New-TestRepositoryDefinition $name $kind)) 'Đồng bộ clone mới thất bại.'
            Assert-SparseReference -Path (Join-Path $Root $name) -Kind $kind
        }
    }

    Invoke-TestCase 'Invoke-RepositorySync mặc định xử lý toàn bộ Repos bằng fixture local' {
        $workspace = Join-Path $script:TestSuiteRoot 'orchestrated-workspace'
        New-Item -ItemType Directory -Path $workspace | Out-Null
        $Repos = @(
            (New-TestRepositoryDefinition 'AndroidDvbDriver' 'main'),
            (New-TestRepositoryDefinition 'references\tbs-linux_media' 'tbs'),
            (New-TestRepositoryDefinition 'references\coreelec-linux-amlogic' 'coreelec'),
            (New-TestRepositoryDefinition 'references\cxd28xx' 'reference'),
            (New-TestRepositoryDefinition 'references\coax' 'reference')
        )
        # Ordinary fixture repos must not check out the intentionally invalid AUX tree.
        foreach ($definition in @($Repos | Where-Object Kind -in @('main', 'reference'))) {
            $path = Join-Path $workspace $definition.RelativePath
            Invoke-TestGit -Arguments @('init', '--quiet', $path) | Out-Null
            Invoke-TestGit -Arguments @('-C', $path, 'remote', 'add', 'origin', $script:TestRemote) | Out-Null
            Invoke-TestGit -Arguments @('-C', $path, 'fetch', '--quiet', 'origin', 'main') | Out-Null
            Invoke-TestGit -Arguments @('-C', $path, 'symbolic-ref', 'HEAD', 'refs/heads/main') | Out-Null
            Invoke-TestGit -Arguments @('-C', $path, 'update-ref', 'refs/heads/main', $script:TestMainCommit) | Out-Null
            Initialize-SparseReference -Path $path -Kind 'coreelec' -Fresh
            Invoke-TestGit -Arguments @('-C', $path, 'config', 'branch.main.remote', 'origin') | Out-Null
            Invoke-TestGit -Arguments @('-C', $path, 'config', 'branch.main.merge', 'refs/heads/main') | Out-Null
        }
        $CheckOnly = $false
        Assert-Test (Invoke-RepositorySync -WorkspaceRoot $workspace) 'Orchestrator mặc định báo lỗi với toàn bộ fixture local.'
        foreach ($definition in $Repos) {
            $path = Join-Path $workspace $definition.RelativePath
            Assert-Test (Test-Path -LiteralPath (Join-Path $path '.git\FETCH_HEAD')) ('Orchestrator chưa xử lý repo: ' + $definition.Name)
            Assert-Test ((Invoke-TestGit -Arguments @('-C', $path, 'rev-parse', 'HEAD')) -ceq $script:TestMainCommit) ('Orchestrator đổi HEAD sai: ' + $definition.Name)
            if ($definition.Kind -in @('tbs', 'coreelec')) { Assert-SparseReference -Path $path -Kind $definition.Kind }
        }
        $beforeBackups = @(Get-TestBackupHeads) -join "`n"
        Assert-Test (Invoke-RepositorySync -WorkspaceRoot $workspace) 'Orchestrator mặc định lần hai báo lỗi.'
        Assert-Test ((@(Get-TestBackupHeads) -join "`n") -ceq $beforeBackups) 'Orchestrator lặp tạo backup thừa.'
    }
    Write-Host ('PASS: {0} trường hợp; NOT RUN: {1}; chỉ fixture Git cục bộ, không chạm references thật.' -f $script:TestPassed, $script:TestSkipped)
} catch {
    Write-Host ('FAIL trước cleanup: ' + $_.Exception.Message)
    throw
} finally {
    foreach ($name in $script:TestEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $script:TestEnvironment[$name], 'Process')
    }
    for ($cleanupAttempt = 1; $cleanupAttempt -le 5; $cleanupAttempt++) {
        if (-not (Test-Path -LiteralPath $script:TestSuiteRoot)) { break }
        try { Remove-Item -LiteralPath $script:TestSuiteRoot -Recurse -Force; break }
        catch {
            if ($cleanupAttempt -eq 5) { throw }
            Start-Sleep -Milliseconds 300
        }
    }
}
