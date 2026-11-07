# SPDX-License-Identifier: GPL-2.0-or-later
# Synthetic fixtures only; no ADB or input commands.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot '..\Tbs5530-UsbPermissionUi.ps1')
function Assert-Result([bool]$Condition, [string]$Name) { if (-not $Condition) { throw "FAIL: $Name" } }
$foreground = 'mResumedActivity: ActivityRecord{abc u0 com.android.systemui/.usb.UsbPermissionActivity t1}'
function New-Fixture {
    return @([pscustomobject]@{ active=$true; focused=$true; content=@(
        [pscustomobject]@{ 'resource-id'='android:id/message'; text='Cho phép DVB-T Driver truy cập thiết bị USB?' },
        [pscustomobject]@{ 'resource-id'='android:id/button1'; text='OK'; interactions=@('CLICKABLE') },
        [pscustomobject]@{ 'resource-id'='android:id/button2'; text='Hủy'; interactions=@('CLICKABLE') },
        [pscustomobject]@{ class='android.widget.CheckBox'; state=@(); text='Luôn sử dụng' }
    ) })
}
$w = New-Fixture
Assert-Result ((Get-TBS5530UsbPermissionButton $w $foreground) -ceq 'id=/^android:id\/button1$/') 'đúng dialog'
Assert-Result ($null -eq (Get-TBS5530UsbPermissionButton $w 'mResumedActivity: other/.Activity')) 'sai Activity'
Assert-Result ($null -eq (Get-TBS5530UsbPermissionButton $w ($foreground.Replace('UsbPermissionActivity','UsbConfirmActivity')))) 'không chọn default handler'
$w[0].content[0].text='Cho phép Other Driver truy cập USB?'
Assert-Result ($null -eq (Get-TBS5530UsbPermissionButton $w $foreground)) 'sai ứng dụng'
$w=New-Fixture; $w[0].content[3].state=@('CHECKED')
Assert-Result ($null -eq (Get-TBS5530UsbPermissionButton $w $foreground)) 'không đổi Always'
$w=New-Fixture; $w[0].content += $w[0].content[1]
Assert-Result ($null -eq (Get-TBS5530UsbPermissionButton $w $foreground)) 'nút mơ hồ'
$w=New-Fixture; $w[0].content[1].text='Delete'
Assert-Result ($null -eq (Get-TBS5530UsbPermissionButton $w $foreground)) 'không bấm prompt khác'
$w=New-Fixture; $w[0].active=$false
Assert-Result ($null -eq (Get-TBS5530UsbPermissionButton $w $foreground)) 'window không active'
$dump="host_manager={`n devices={`n vendor_id=29516`n product_id=21808`n }`n num_connects=9`n connections={vendor_id=1}"
Assert-Result (Test-TBS5530SingleUsbInventory $dump) 'đúng VID/PID, bỏ lịch sử'
Assert-Result (-not (Test-TBS5530SingleUsbInventory ($dump.Replace('21808','1')))) 'sai PID'
Assert-Result (-not (Test-TBS5530SingleUsbInventory ($dump.Replace('num_connects='," vendor_id=29516`n product_id=21808`n num_connects=")))) 'nhiều thiết bị'
Assert-Result (-not (Test-TBS5530SingleUsbInventory 'unknown OEM format')) 'schema không hiểu'
$p=[pscustomobject]@{version=1;session='session';request_token='11111111-1111-4111-8111-111111111111';attachment_generation=1;device_identity_sha256=('a'*64);vid=29516;pid=21808;state='PENDING';requested_elapsed_ms=1000;elapsed_ms=1100}
Assert-Result (Test-TBS5530PendingPermission $p 'session' 1101) 'pending còn mới'
Assert-Result (-not (Test-TBS5530PendingPermission $p 'other' 1101)) 'khác session'
Assert-Result (-not (Test-TBS5530PendingPermission $p 'session' 3100)) 'quá heartbeat'
Assert-Result (-not (Test-TBS5530PendingPermission $p 'session' 1099)) 'clock tương lai'
$p.state='DENIED'; Assert-Result (-not (Test-TBS5530PendingPermission $p 'session' 1101)) 'không retry denial'
Assert-Result (-not (Test-TBS5530PendingPermission $null 'session' 1101)) 'thiếu challenge'
'PASS: 18 kiểm tra synthetic cho dialog, VID/PID, default, ambiguity, token và freshness; không chạy ADB.'
