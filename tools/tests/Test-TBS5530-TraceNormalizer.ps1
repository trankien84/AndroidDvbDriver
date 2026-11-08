# SPDX-License-Identifier: GPL-2.0-or-later
<#
.SYNOPSIS
Kiểm thử synthetic offline, không cần Pester/TShark/ADB hay thiết bị.
.EXAMPLE
.\tools\tests\Test-TBS5530-TraceNormalizer.ps1
#>
[CmdletBinding()]
param([string]$ReportPath)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
. (Join-Path $PSScriptRoot '..\Tbs5530-TraceNormalizer.ps1')
$cli = Join-Path $PSScriptRoot '..\Convert-TBS5530-Trace.ps1'
$fixtures = Join-Path $PSScriptRoot 'fixtures\trace-normalizer'
$work = Join-Path $PSScriptRoot ('.trace-normalizer-test-' + [guid]::NewGuid().ToString('N'))
$initialProcessDirectory = [Environment]::CurrentDirectory
[IO.Directory]::CreateDirectory($work) | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
$script:checks = 0
$script:results = [Collections.Generic.List[string]]::new()
function Assert-Trace([bool]$Condition, [string]$Name) {
    $script:checks++
    if (-not $Condition) { throw "FAIL: $Name" }
    $script:results.Add($Name)
}
function Assert-TraceThrows([scriptblock]$Body, [string]$Pattern, [string]$Name) {
    $caught = $false
    try { & $Body | Out-Null } catch { $caught = $_.Exception.Message -match $Pattern }
    Assert-Trace $caught $Name
}
function Write-Fixture([string]$Name, [string]$Text) {
    $p = Join-Path $work $Name
    [IO.File]::WriteAllText($p, $Text, $utf8)
    return $p
}
function Normalize([string]$Path, [string]$Format, [hashtable]$Extra = @{}) {
    return Convert-Tbs5530Trace -InputPath $Path -Format $Format -CaptureEpoch cap -AttachmentEpoch attach @Extra
}
function Pcap-Fixture($Packets) {
    return Write-Fixture 'generated.json' (ConvertTo-Json -InputObject @($Packets) -Depth 20)
}
function New-PcapPacket([int]$Frame, [string]$Id, [int]$Info, [int]$Type = 3) {
    return [pscustomobject]@{ _source = [pscustomobject]@{ layers = [pscustomobject]@{
        'frame.number' = @([string]$Frame); 'frame.time_epoch' = @('1700000000.000000001')
        'usb.irp_id' = @($Id); 'usb.irp_info' = @([string]$Info); 'usb.transfer_type' = @([string]$Type)
        'usb.endpoint_address' = @('0x82'); 'usb.bus_id' = @('1'); 'usb.device_address' = @('2')
    } } }
}
function Add-ControlSetup($Packet, [int]$Stage, [int]$Info, [int]$Request = 138) {
    $l = $Packet._source.layers
    $l.'usb.endpoint_address' = @('0x00'); $l.'usb.irp_info' = @([string]$Info)
    $l | Add-Member -NotePropertyName 'usb.control_stage' -NotePropertyValue @([string]$Stage)
    foreach ($kv in @(@('usb.bmRequestType', '0x40'), @('usb.setup.bRequest', [string]$Request),
        @('usb.setup.wValue', '0x0000'), @('usb.setup.wIndex', '0'), @('usb.setup.wLength', '2'))) {
        $l | Add-Member -NotePropertyName $kv[0] -NotePropertyValue @($kv[1])
    }
}
try {
    $npath = Join-Path $fixtures 'native.jsonl'
    $nativeText = [IO.File]::ReadAllText($npath)
    $n = Normalize $npath NativeJsonl
    Assert-Trace ($n.Records.Count -eq 3 -and $n.Summary.errors -eq 0) 'native events plus summary'
    Assert-Trace ($n.Records[1].syscall_return -eq 0 -and $n.Records[1].syscall_errno -eq 0 -and $n.Records[1].status -eq -75 -and $n.Records[1].actual -eq 0) 'syscall success independent from URB -75 and actual zero'
    Assert-Trace ($n.Records[0].actual -eq $null -and $n.Records[0].native.reported_actual_length -eq 0) 'submit placeholder is not completion actual'
    Assert-Trace ($n.Records[0].pairing.state -eq 'paired' -and $n.Records[1].pairing.submit_seq -eq 1) 'native pairing'
    Assert-Trace ($n.Records[2].timestamp.value -eq $null -and $n.Records[2].event -eq 'summary') 'summary has no invented timestamp'
    Assert-Trace ($n.Records[1].timestamp.value -ceq '200' -and $n.Records[1].timestamp.epoch_seconds -eq $null -and $n.Records[1].timestamp.clock_domain -ceq 'CLOCK_MONOTONIC') 'monotonic clock not mapped to Unix epoch'
    Assert-Trace ($n.Records[0].metadata.host -eq $null -and $n.Records[0].metadata.lock -eq $null) 'unknown metadata remains null'
    Assert-Trace ($n.Records[0].source.byte_offset -eq 0 -and $n.Records[1].source.byte_offset -gt 0) 'native byte offsets'
    $missing = Write-Fixture 'missing.jsonl' ($nativeText -replace ',"requested_length":4096', '')
    $r = Normalize $missing NativeJsonl
    Assert-Trace ($null -eq $r.Records[1].requested -and $r.Records[1].actual -eq 0) 'native missing requested stays null'
    $cancel = Write-Fixture 'cancel.jsonl' ($nativeText.Replace('"urb_status":-75', '"urb_status":-104'))
    $r = Normalize $cancel NativeJsonl
    Assert-Trace $r.Records[1].pairing.canceled 'native canceled completion'
    $failed = Write-Fixture 'failed.jsonl' ($nativeText.Replace('"syscall_return":0', '"syscall_return":-1'))
    $r = Normalize $failed NativeJsonl
    Assert-Trace ($r.Records[0].pairing.state -eq 'submit_failed' -and $r.Records[1].event -eq 'syscall_error') 'failed syscalls not paired as URB completion'
    $r = Normalize $npath NativeJsonl @{ MaxRecords = 1 }
    Assert-Trace ($r.Summary.truncated -and $r.Records.Count -eq 1 -and $r.Records[0].pairing.state -eq 'missing_completion') 'bounded record window reported'
    $r = Normalize $npath NativeJsonl @{ AttachmentBoundaries = @{ 2 = 're-enumerated' } }
    Assert-Trace ($r.Records[1].pairing.state -eq 'missing_submit' -and $r.Records[0].pairing.state -eq 'missing_completion') 'no native pairing across attachment boundary'
    $r = Normalize $npath NativeJsonl @{ Metadata = @{ host = 'synthetic'; alt = 0; lock = $false; mode = 'test' } }
    Assert-Trace ($r.Records[0].metadata.alt -eq 0 -and $r.Records[0].metadata.lock -eq $false -and $r.Records[0].metadata.host -eq 'synthetic') 'explicit zero false and metadata preserved'
    $nativeLines = @($nativeText.TrimEnd() -split '\r?\n')
    $p = Write-Fixture 'native-runs.jsonl' ($nativeLines[0] + "`n" + $nativeLines[1].Replace('"run_id":"synthetic"', '"run_id":"other"'))
    $r = Normalize $p NativeJsonl
    Assert-Trace ($r.Records[1].pairing.state -eq 'missing_submit') 'native run_id isolates attachments'
    $p = Write-Fixture 'native-sequence.jsonl' ($nativeLines[0] + "`n" + $nativeLines[1].Replace('"sequence":1', '"sequence":2'))
    $r = Normalize $p NativeJsonl
    Assert-Trace ($r.Records[1].warnings -contains 'pair_identity_mismatch' -and $r.Records[1].pairing.state -eq 'missing_submit') 'native submission sequence protects slot reuse'
    $p = Write-Fixture 'native-clock.jsonl' ($nativeLines[0] + "`n" + $nativeLines[1].Replace('CLOCK_MONOTONIC', 'CLOCK_BOOTTIME'))
    $r = Normalize $p NativeJsonl
    Assert-Trace ($r.Records[1].pairing.state -eq 'missing_submit') 'different clocks never paired'
    $p = Write-Fixture 'native-order.jsonl' ($nativeLines[1] + "`n" + $nativeLines[0])
    $r = Normalize $p NativeJsonl
    Assert-Trace ($r.Records[0].source_seq -eq 2 -and $r.Records[0].seq -eq 1 -and $r.Records[0].timestamp.value -ceq '200' -and $r.Records[1].timestamp.value -ceq '100') 'original event order preserved without clock sorting'
    $p = Join-Path $work 'bom.jsonl'; $first = $nativeLines[0].Replace('synthetic', 'synthetic-é')
    [IO.File]::WriteAllText($p, ($first + "`r`n" + $nativeLines[1]), [Text.UTF8Encoding]::new($true))
    $r = Normalize $p NativeJsonl
    Assert-Trace ($r.Records[0].source.byte_offset -eq 3 -and $r.Records[1].source.byte_offset -eq (3 + $utf8.GetByteCount($first + "`r`n"))) 'UTF-8 BOM multibyte and CRLF offsets exact'
    $p = Write-Fixture 'discard.jsonl' ($nativeLines[0] + "`n" + $nativeLines[1].Replace('"operation":"REAP"', '"operation":"DISCARD"').Replace('"syscall_return":0', '"syscall_return":-1').Replace('"syscall_errno":0', '"syscall_errno":22').Replace('"urb_status":-75', '"urb_status":null'))
    $r = Normalize $p NativeJsonl
    Assert-Trace ($r.Records[1].syscall_errno -eq 22 -and -not $r.Records[1].pairing.canceled -and $r.Records[1].pairing.state -eq 'missing_completion') 'failed DISCARD does not prove cancellation'

    $u = Normalize (Join-Path $fixtures 'usbmon.txt') UsbmonText
    Assert-Trace ($u.Records.Count -eq 4 -and $u.Records[1].requested -eq 4096 -and $u.Records[1].actual -eq 0) 'usbmon IN submission length is explicit evidence'
    # Synthetic 1u grammar reproducing OUT callback '>' from the guest trace, not its payload.
    $guestFixture = @'
a 100 S Ci:2:002:0 s 80 06 0200 0000 0020 32 <
a 101 C Ci:2:002:0 0 32
b 102 S Co:2:002:0 s 40 80 0000 0000 0004 4 = 00000000
b 103 C Co:2:002:0 0 4 >
c 104 S Bi:2:002:2 -115 65536 <
c 105 C Bi:2:002:2 0 82
c 106 S Bi:2:002:2 -115 65536 <
c 107 C Bi:2:002:2 -2 0
d 108 C Bo:2:002:2 0 >
'@
    $guestPath = Write-Fixture 'guest-usbmon.txt' $guestFixture
    $guest = Normalize $guestPath UsbmonText @{ UsbmonClockDomain = 'guest.usbmon.timestamp_us:unmapped' }
    Assert-Trace ($guest.Records.Count -eq 9 -and $guest.Summary.errors -eq 0) 'guest usbmon OUT callback data tag accepted'
    Assert-Trace ($guest.Records[0].setup.wLength -eq 32 -and $guest.Records[1].requested -eq 32 -and $guest.Records[1].actual -eq 32) 'setup wLength hexadecimal and submission length decimal preserved'
    Assert-Trace ($guest.Records[3].requested -eq 4 -and $guest.Records[3].actual -eq 4 -and $null -eq $guest.Records[3].payload) 'OUT callback > neither changes length nor invents payload'
    Assert-Trace ($guest.Records[5].requested -eq 65536 -and $guest.Records[5].actual -eq 82 -and $guest.Records[5].requested_source -match '^paired_submit:5:usbmon.submission.length$') 'guest first IN82 request 65536 independent from actual 82'
    Assert-Trace ($guest.Records[7].requested -eq 65536 -and $guest.Records[7].actual -eq 0 -and $guest.Records[7].pairing.canceled -and $guest.Records[7].pairing.generation -eq 2) 'guest resubmission and cancellation retain requested 65536'
    Assert-Trace ($null -eq $guest.Records[8].requested -and $null -eq $guest.Records[8].actual) 'OUT data tag alone cannot fill missing length'
    Assert-Trace ($guest.Records[5].timestamp.clock_domain -ceq 'guest.usbmon.timestamp_us:unmapped' -and $guest.Records[5].timestamp.value -ceq '105' -and $null -eq $guest.Records[5].timestamp.epoch_seconds) 'guest timestamp unmapped without clock joins'
    Assert-TraceThrows { Normalize (Write-Fixture 'bad-out-tag.txt' 'a 1 C Co:2:002:0 0 4 > garbage') UsbmonText } 'Malformed usbmon data tag' 'reject data after > without relaxing malformed grammar'
    Assert-TraceThrows { Normalize (Write-Fixture 'bad-in-tag.txt' 'a 1 S Bi:2:002:2 -115 65536 < garbage') UsbmonText } 'Malformed usbmon data tag' 'reject data after <'
    Assert-TraceThrows { Normalize (Write-Fixture 'bad-status.txt' 'a 1 C Co:2:002:0 bad 4 >') UsbmonText } 'Malformed usbmon status/length' 'reject malformed status even with valid OUT tag'
    Assert-Trace ($u.Records[3].setup.bRequest -eq 138 -and $u.Records[3].requested -eq 2 -and $u.Records[3].pairing.state -eq 'paired') 'usbmon control setup inherited'
    Assert-Trace ($null -eq $u.Records[2].payload) 'default payload omitted'
    $u = Normalize (Join-Path $fixtures 'usbmon.txt') UsbmonText @{ IncludePayload = $true; PayloadExcerptBytes = 1 }
    Assert-Trace ($u.Records[2].payload.captured_bytes -eq 2 -and $u.Records[2].payload.sha256.Length -eq 64 -and $u.Records[2].payload.excerpt_hex -ceq '0a') 'bounded optional payload hashes captured bytes'
    $p = Write-Fixture 'mon-missing.txt' "a 1 C Bi:1:2:2 0 8192`nb 2 C Bi:1:2:2 0`nc 3 S Bi:1:2:2 -115 0 <`nc 4 C Bi:1:2:2 0 0`n"
    $r = Normalize $p UsbmonText
    Assert-Trace ($null -eq $r.Records[0].requested -and $r.Records[0].actual -eq 8192 -and $r.Records[0].pairing.state -eq 'missing_submit') 'completion actual 8192 never becomes requested'
    Assert-Trace ($null -eq $r.Records[1].actual -and $r.Records[3].requested -eq 0 -and $r.Records[3].actual -eq 0) 'missing versus measured zero lengths'
    $p = Write-Fixture 'mon-reuse.txt' "a 1 S Bi:1:2:2 -115 4 <`na 2 C Bi:1:2:2 0 4`na 3 S Bi:1:2:2 -115 8 <`na 4 C Bi:1:2:2 -2 0`n"
    $r = Normalize $p UsbmonText
    Assert-Trace ($r.Records[1].pairing.generation -eq 1 -and $r.Records[3].pairing.generation -eq 2 -and $r.Records[3].requested -eq 8 -and $r.Records[3].pairing.canceled) 'usbmon reuse and cancel'
    $p = Write-Fixture 'mon-firmware.txt' 'a 1 S Co:1:2:0 s 40 a0 0000 0000 0002 2 = abcd'
    $r = Normalize $p UsbmonText @{ IncludePayload = $true; PayloadExcerptBytes = 2 }
    Assert-Trace ($r.Records[0].payload -eq $null -and $r.Records[0].warnings -contains 'control_payload_suppressed') 'firmware payload always suppressed'

    $w = Normalize (Join-Path $fixtures 'usbpcap.json') UsbPcapJson
    Assert-Trace ($w.Records.Count -eq 2 -and $w.Records[1].pairing.state -eq 'paired') 'USBPcap flat TShark fields paired'
    Assert-Trace ($null -eq $w.Records[0].requested -and $null -eq $w.Records[0].actual -and $null -eq $w.Records[1].requested -and $w.Records[1].actual -eq 8192) 'USBPcap bulk submit data_len zero does not prove requested'
    Assert-Trace ($w.Records[0].timestamp.epoch_seconds -ceq '1700000000.123456789' -and $w.Records[0].timestamp.clock_domain -ceq 'pcap.frame.time_epoch' -and $w.Records[1].source.json_path -ceq '$[1]') 'pcap exact epoch precision and source offset'
    $a = New-PcapPacket 1 '0x1' 0; $b = New-PcapPacket 2 '0x1' 1
    $c = New-PcapPacket 3 '0x1' 0; $d = New-PcapPacket 4 '0x1' 1
    $d._source.layers | Add-Member -NotePropertyName 'usb.usbd_status' -NotePropertyValue @('0xc0010000')
    $r = Normalize (Pcap-Fixture @($a, $b, $c, $d)) UsbPcapJson
    Assert-Trace ($r.Records[1].pairing.generation -eq 1 -and $r.Records[3].pairing.generation -eq 2 -and $r.Records[3].pairing.canceled) 'IRP reuse and Windows canceled status'
    $r = Normalize (Pcap-Fixture @($a, $c, $d)) UsbPcapJson
    Assert-Trace ($r.Records[0].pairing.state -eq 'missing_completion' -and $r.Records[0].warnings -contains 'id_reused_before_completion' -and $r.Records[2].pairing.state -eq 'ambiguous_id_reuse' -and $null -eq $r.Records[2].pairing.generation -and $null -eq $r.Records[2].requested) 'overlapping IRP reuse cannot identify late completion'
    $r = Normalize (Pcap-Fixture @($a, $b)) UsbPcapJson @{ AttachmentBoundaries = @{ 2 = 'new-attachment' } }
    Assert-Trace ($r.Records[1].pairing.state -eq 'missing_submit') 'IRP cannot cross re-enumeration epoch'
    $b._source.layers.'usb.device_address' = @('3')
    $r = Normalize (Pcap-Fixture @($a, $b)) UsbPcapJson
    Assert-Trace ($r.Records[1].pairing.state -eq 'missing_submit') 'IRP isolated by USB device address'
    $a = New-PcapPacket 1 '0x2' 0 2; Add-ControlSetup $a 0 0
    $b = New-PcapPacket 2 '0x2' 1 2
    $b._source.layers.'usb.endpoint_address' = @('0x80')
    $b._source.layers | Add-Member -NotePropertyName 'usb.control_stage' -NotePropertyValue @('3')
    $b._source.layers | Add-Member -NotePropertyName 'usb.data_len' -NotePropertyValue @('0')
    $r = Normalize (Pcap-Fixture @($a, $b)) UsbPcapJson
    Assert-Trace ($r.Records[1].setup.bRequest -eq 138 -and $r.Records[1].direction -eq 'out' -and $r.Records[1].requested -eq 2 -and $r.Records[1].pairing.state -eq 'paired') 'control response endpoint direction differs from setup direction'
    $data = New-PcapPacket 2 '0x2' 1 2
    $data._source.layers | Add-Member -NotePropertyName 'usb.control_stage' -NotePropertyValue @('1')
    $data._source.layers | Add-Member -NotePropertyName 'usb.data_len' -NotePropertyValue @('2')
    $r = Normalize (Pcap-Fixture @($a, $data, $b)) UsbPcapJson
    Assert-Trace ($r.Records[1].event -eq 'control_data' -and $r.Records[1].pairing.completion_seq -eq 3 -and $r.Records[2].actual -eq 0) 'legacy control data and terminal status remain separate stages'
    $r = Normalize (Pcap-Fixture @($b)) UsbPcapJson
    Assert-Trace ($r.Records[0].setup -eq $null -and $r.Records[0].requested -eq $null -and $r.Records[0].pairing.state -eq 'missing_submit') 'missing control submit never fabricates setup'
    $a = New-PcapPacket 1 '0x3' 0 1
    $r = Normalize (Pcap-Fixture @($a)) UsbPcapJson
    Assert-Trace ($r.Records[0].transfer -ceq 'interrupt') 'verified Wireshark enum 1 interrupt'
    $a._source.layers | Add-Member -NotePropertyName 'frame.len' -NotePropertyValue @('100')
    $a._source.layers | Add-Member -NotePropertyName 'frame.cap_len' -NotePropertyValue @('27')
    $r = Normalize (Pcap-Fixture @($a)) UsbPcapJson
    Assert-Trace ($r.Records[0].warnings -contains 'capture_truncated') 'snaplen truncation is visible'
    $a = New-PcapPacket 1 '0x0000' 0; $b = New-PcapPacket 2 '0' 1
    $r = Normalize (Pcap-Fixture @($a, $b)) UsbPcapJson
    Assert-Trace ($r.Records[1].pairing.state -eq 'paired' -and $r.Records[1].pairing.raw_id -ceq '0x0') 'canonical 64-bit IRP hex spelling including zero'
    $a._source.layers | Add-Member -NotePropertyName 'frame.interface_id' -NotePropertyValue @('0')
    $b._source.layers | Add-Member -NotePropertyName 'frame.interface_id' -NotePropertyValue @('1')
    $r = Normalize (Pcap-Fixture @($a, $b)) UsbPcapJson
    Assert-Trace ($r.Records[1].pairing.state -eq 'missing_submit') 'capture interfaces are isolated'
    $b._source.layers.'usb.irp_info' = @('0')
    $r = Normalize (Pcap-Fixture @($a, $b)) UsbPcapJson
    Assert-Trace ($r.Records[0].pairing.transfer_id -cne $r.Records[1].pairing.transfer_id) 'transfer IDs include capture interface and clock scope'
    $a = New-PcapPacket 1 '0x1' 0 2; Add-ControlSetup $a 0 0
    foreach ($name in @('bRequest', 'wValue', 'wIndex', 'wLength')) {
        $v = $a._source.layers.PSObject.Properties['usb.setup.' + $name].Value
        $a._source.layers.PSObject.Properties.Remove('usb.setup.' + $name)
        $a._source.layers | Add-Member -NotePropertyName ('usbhub.setup.' + $name) -NotePropertyValue $v
    }
    $b = New-PcapPacket 2 '0x1' 1 2
    $b._source.layers | Add-Member -NotePropertyName 'usb.control_stage' -NotePropertyValue @('3')
    $r = Normalize (Pcap-Fixture @($a, $b)) UsbPcapJson
    Assert-Trace ($r.Records[1].requested -eq 2 -and $r.Records[1].setup.bRequest -eq 138 -and $r.Records[0].transfer_stage -eq 0 -and $r.Records[1].transfer_stage -eq 3) 'real TShark usbhub setup aliases and control stage'
    $a._source.layers | Add-Member -NotePropertyName 'usb.setup.bRequest' -NotePropertyValue @('1')
    Assert-TraceThrows { Normalize (Pcap-Fixture @($a)) UsbPcapJson } 'Conflicting usb/usbhub' 'conflicting setup evidence rejected'
    $a = New-PcapPacket 1 '0x1' 0 2; Add-ControlSetup $a 0 0 6
    $a._source.layers.PSObject.Properties.Remove('usb.setup.wValue')
    $a._source.layers.PSObject.Properties.Remove('usb.setup.wIndex')
    $r = Normalize (Pcap-Fixture @($a)) UsbPcapJson
    Assert-Trace ($r.Records[0].setup.wValue -eq $null -and $r.Records[0].setup.wIndex -eq $null -and $r.Records[0].requested -eq 2 -and $r.Records[0].warnings -contains 'incomplete_setup_fields') 'partial standard setup preserves unknown components'
    $a._source.layers.PSObject.Properties.Remove('usb.setup.wLength')
    $a._source.layers.PSObject.Properties.Remove('usb.bmRequestType')
    $r = Normalize (Pcap-Fixture @($a)) UsbPcapJson
    Assert-Trace ($r.Records[0].requested -eq $null -and $r.Records[0].direction -eq $null -and $r.Records[0].requested_source -eq $null) 'partial setup cannot invent length or direction'
    $p = Write-Fixture 'orphan-cancel.txt' 'a 1 C Bi:1:2:2 -104 0'
    $r = Normalize $p UsbmonText
    Assert-Trace ($r.Records[0].pairing.canceled -and $r.Records[0].pairing.state -eq 'missing_submit' -and $null -eq $r.Records[0].syscall_errno) 'canceled status remains evidence even without submit'
    $p = Write-Fixture 'submit-missing.txt' "a 1 S Bi:1:2:2 -115 <`na 2 C Bi:1:2:2 0 8192"
    $r = Normalize $p UsbmonText
    Assert-Trace ($r.Records[1].pairing.state -eq 'paired' -and $null -eq $r.Records[1].requested) 'paired completion cannot fill a missing submission length'

    Assert-TraceThrows { Normalize (Write-Fixture 'bad.jsonl' '{"run_id":') NativeJsonl } 'Malformed/incomplete' 'reject incomplete JSONL'
    Assert-TraceThrows { Normalize (Write-Fixture 'bad.json' '[{"_source":') UsbPcapJson } 'Malformed/incomplete' 'reject truncated JSON array'
    Assert-TraceThrows { Normalize (Write-Fixture 'empty.txt' '') UsbmonText } 'Empty trace' 'reject empty file'
    Assert-TraceThrows { Normalize (Write-Fixture 'unknown.txt' 'not usbmon') UsbmonText } 'Unsupported/malformed' 'reject unknown usbmon grammar'
    Assert-TraceThrows { Normalize $npath NativeJsonl @{ MaxInputBytes = 4 } } 'exceeds MaxInputBytes' 'enforce byte budget'
    $a = New-PcapPacket 1 '0x3' 0; $a._source.layers.'usb.irp_id' = @('0x3', '0x4')
    Assert-TraceThrows { Normalize (Pcap-Fixture @($a)) UsbPcapJson } 'Ambiguous repeated' 'reject ambiguous repeated fields'
    $a = New-PcapPacket 1 '0x3' 0 2; $a._source.layers | Add-Member -NotePropertyName 'usb.control_stage' -NotePropertyValue @('0')
    Assert-TraceThrows { Normalize (Pcap-Fixture @($a)) UsbPcapJson } 'Missing USBPcap setup' 'reject incomplete control setup'
    Assert-TraceThrows { Normalize (Write-Fixture 'unknown.jsonl' '{"run_id":"synthetic","operation":"UNKNOWN"}') NativeJsonl } 'Unsupported native' 'reject unknown native operation'
    Assert-TraceThrows { Normalize $npath NativeJsonl @{ PayloadExcerptBytes = 1 } } 'requires IncludePayload' 'payload opt-in required'
    Assert-TraceThrows { Normalize (Write-Fixture 'negative.jsonl' ($nativeText.Replace('"requested_length":4096', '"requested_length":-1'))) NativeJsonl } 'Negative length' 'reject negative requested'

    $out = Join-Path $work 'cli\normalized.jsonl'
    & $cli -InputPath $npath -Format NativeJsonl -CaptureEpoch cap -AttachmentEpoch attach -OutputPath $out -WhatIf | Out-Null
    Assert-Trace (-not (Test-Path -LiteralPath (Split-Path -Parent $out))) 'WhatIf has no filesystem side effects'
    $s = & $cli -InputPath $npath -Format NativeJsonl -CaptureEpoch cap -AttachmentEpoch attach -OutputPath $out
    Assert-Trace ($s.records -eq 3 -and @(Get-Content -LiteralPath $out).Count -eq 3 -and (Test-Path -LiteralPath ($out + '.summary.json'))) 'CLI persists JSONL and summary'
    $hash = (Get-FileHash -LiteralPath $out).Hash
    Assert-TraceThrows { & $cli -InputPath $npath -Format NativeJsonl -CaptureEpoch cap -AttachmentEpoch attach -OutputPath $out } 'exists' 'CLI rejects overwrite by default'
    Assert-Trace ((Get-FileHash -LiteralPath $out).Hash -eq $hash) 'existing output unchanged'
    & $cli -InputPath $npath -Format NativeJsonl -CaptureEpoch cap -AttachmentEpoch attach -OutputPath $out -Force | Out-Null
    Assert-Trace ((Get-FileHash -LiteralPath $out).Hash -eq $hash) 'Force explicitly replaces existing output'
    Assert-TraceThrows { & $cli -InputPath $npath -Format NativeJsonl -CaptureEpoch cap -AttachmentEpoch attach -OutputPath $npath -Force } 'distinct paths' 'CLI cannot overwrite input'
    $bad = Join-Path $work 'bad.jsonl'; $outBad = Join-Path $work 'failed-output.jsonl'
    Assert-TraceThrows { & $cli -InputPath $bad -Format NativeJsonl -CaptureEpoch cap -AttachmentEpoch attach -OutputPath $outBad } 'Malformed/incomplete' 'CLI malformed input fails clearly'
    Assert-Trace (-not (Test-Path -LiteralPath $outBad)) 'parse failure publishes no output'
} finally {
    # A provider may move the process directory independently of PowerShell's location.
    # Restore only if this runner's private fixture tree became the process directory.
    $processDirectory = [Environment]::CurrentDirectory
    if ($processDirectory -eq $work -or $processDirectory.StartsWith($work + '\', [StringComparison]::OrdinalIgnoreCase)) {
        Write-Verbose "Restoring process directory before cleanup: $processDirectory"
        [Environment]::CurrentDirectory = $initialProcessDirectory
    }
    if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }
}
$report = [pscustomobject]@{ result = 'PASS'; passed = $script:checks; failed = 0; powershell = $PSVersionTable.PSVersion.ToString(); checks = $script:results.ToArray() }
if ($ReportPath) {
    $full = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ReportPath)
    if (Test-Path -LiteralPath $full) { throw 'Test report exists; choose a new path' }
    [IO.Directory]::CreateDirectory((Split-Path -Parent $full)) | Out-Null
    [IO.File]::WriteAllText($full, ($report | ConvertTo-Json -Depth 8), $utf8)
}
"PASS: $($script:checks)/$($script:checks); PowerShell $($PSVersionTable.PSVersion); offline synthetic only."
