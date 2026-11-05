# Explicit host audit enrollment only. No ADB, app storage, grant, token, permit or USB operation.
[CmdletBinding()]
param(
    [switch]$EnrollPriorConsumedAudit,
    [switch]$AcknowledgeUserProvidedPhase29Evidence
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $EnrollPriorConsumedAudit -or -not $AcknowledgeUserProvidedPhase29Evidence) {
    throw 'BLOCKED: cần xác nhận riêng việc lưu lịch sử consumed/UNCERTAIN từ evidence Phase 2.9; không phải approval thử hardware mới.'
}
. (Join-Path $PSScriptRoot 'Tbs5530-ApprovalLedger.ps1')
$path = Get-TBS5530HardwareLedgerPath
$parent = [IO.Path]::GetDirectoryName($path)
# Refuse every existing file, including empty/corrupt files. Never reset, repair or migrate implicitly.
if (Test-Path -LiteralPath $path) { throw 'BLOCKED: ledger canonical đã tồn tại; không overwrite/truncate/remove hoặc enroll lại.' }
# Validate existing ancestry before creating even the canonical parent directory.
$ancestor = [IO.DirectoryInfo]::new($parent)
$existingParent = $null
while ($null -ne $ancestor) {
    if ($ancestor.Exists) {
        if ($null -eq $existingParent) { $existingParent = $ancestor.FullName }
        if (($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'BLOCKED: đường dẫn canonical đi qua reparse point; cần operator kiểm tra riêng.'
        }
    }
    $ancestor = $ancestor.Parent
}
if ($null -eq $existingParent) { throw 'BLOCKED: thiếu canonical parent có thật.' }
$git = (Get-Command git.exe -ErrorAction Stop).Source
$probe = @(& $git -C $existingParent rev-parse --is-inside-work-tree --is-inside-git-dir 2>$null)
if ($LASTEXITCODE -eq 0 -and @($probe | Where-Object { $_ -ceq 'true' }).Count) {
    throw 'BLOCKED: canonical hardware ledger phải nằm ngoài mọi Git checkout/metadata.'
}
[IO.Directory]::CreateDirectory($parent) | Out-Null
# CreateNew denies races with enrollment/another writer. Failure leaves evidence, never auto-deletes it.
New-TBS5530PriorConsumedAuditFile -Path $path
[pscustomobject]@{ status = 'PRIOR_CONSUMED_AUDIT_ENROLLED'; schema_version = 1; canonical_ledger = $true
    prior_state = 'UNCERTAIN'; prior_native_calls = 23; prior_image_bytes = 1344; cpu_release_sent = $false
    hardware_approval = $false; new_approval_issued = $false; application_state_modified = $false }
