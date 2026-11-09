# SPDX-License-Identifier: GPL-2.0-only
[CmdletBinding()]
param([string]$Compiler = 'gcc')
$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot 'Test-ReceiveOriginalK36.c'
$gcc = Get-Command $Compiler -CommandType Application -ErrorAction Stop
$work = Join-Path $PSScriptRoot ('.fake-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
try {
    $exe = Join-Path $work 'receiver-fake.exe'
    & $gcc.Source -std=c11 -Wall -Wextra -Werror -pedantic $source -o $exe
    if ($LASTEXITCODE -ne 0) { throw 'Không biên dịch được bộ kiểm thử giả lập.' }
    & $exe --selftest
    if ($LASTEXITCODE -ne 0) { throw 'Selftests thất bại.' }
    $stdout = Join-Path $work 'stdout.txt'
    $stderr = Join-Path $work 'stderr.jsonl'
    & $exe 1> $stdout 2> $stderr
    if ($LASTEXITCODE -ne 0) { throw ("Luồng giả lập thất bại.`n" + ((Get-Content $stderr -Tail 12) -join "`n")) }
    $lines = @(Get-Content $stderr)
    if ($lines | Where-Object { $_ -match 'TS_SMOKE_PASS' }) { throw 'Telemetry không được chứng nhận chất lượng TS.' }
    $rows = @($lines | ForEach-Object { $_ | ConvertFrom-Json })
    $caseName = $null
    $last = $null
    $verified = 0
    foreach ($row in $rows) {
        switch ($row.event) {
            'fake_case' { $caseName = $row.case; $last = $null }
            'receive_end' { $last = $row }
            'fake_expect' {
                if ($row.case -ne $caseName -or $null -eq $last -or
                    $last.bytes -ne $row.bytes -or $last.result -ne $row.result -or
                    $last.clock -ne 'CLOCK_MONOTONIC' -or $last.bytes -gt 16777216) {
                    throw "Telemetry không khớp: $caseName"
                }
                if ($caseName -like 'stop_*' -and (-not $last.stopped -or $last.result -eq 0)) {
                    throw "STOP không được xử lý đúng: $caseName"
                }
                $verified++
            }
        }
    }
    $summary = @(Get-Content $stdout)
    $match = [regex]::Match(($summary -join "`n"), 'PASS (\d+) mocked flows')
    if (-not $match.Success -or $verified -ne [int]$match.Groups[1].Value) { throw 'Thiếu kết quả kiểm thử.' }
    $summary | Write-Output
    Write-Output "PASS JSON telemetry: $verified luồng, bytes/result khớp, STOP có lỗi thoát."
} finally {
    if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }
}
