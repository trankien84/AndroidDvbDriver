# SPDX-License-Identifier: GPL-2.0-or-later
# Dot-source only. Offline evidence conversion; no hardware or external commands.
Set-StrictMode -Version 2.0

function Get-TbsTraceValue {
    param($Object, [string]$Name)
    if ($null -eq $Object) { return $null }
    if ($Object -is [System.Collections.IDictionary]) { return $Object[$Name] }
    $p = $Object.PSObject.Properties[$Name]
    if ($null -ne $p) { return $p.Value }
    return $null
}

function ConvertTo-TbsTraceInteger {
    param($Value, [string]$Field, [switch]$Required, [switch]$Hex)
    if ($null -eq $Value) {
        if ($Required) { throw "Missing field: $Field" }
        return $null
    }
    if ($Value -is [bool] -or $Value -is [array]) { throw "Invalid integer: $Field" }
    $text = [string]$Value
    try {
        if ($text -match '^0[xX][0-9a-fA-F]+$') { return [Convert]::ToInt64($text.Substring(2), 16) }
        if ($Hex -and $text -match '^[0-9a-fA-F]+$') { return [Convert]::ToInt64($text, 16) }
        if ($text -notmatch '^-?\d+$') { throw 'integer required' }
        return [long]::Parse($text, [Globalization.CultureInfo]::InvariantCulture)
    } catch { throw "Invalid integer: $Field" }
}

function Get-TbsTraceField {
    param($Layers, [string]$Name, [switch]$Required)
    $v = Get-TbsTraceValue $Layers $Name
    if ($v -is [array]) {
        if ($v.Count -ne 1) { throw "Ambiguous repeated TShark field: $Name" }
        $v = $v[0]
    }
    if ($v -is [pscustomobject] -or $v -is [System.Collections.IDictionary]) {
        throw "Nested TShark JSON unsupported; export flat fields with -T json -e ($Name)"
    }
    if ($Required -and ($null -eq $v -or [string]$v -eq '')) { throw "Missing field: $Name" }
    return $v
}

function Get-Tbs5530TraceExportFields {
    # Validate these names against the installed tshark -G fields before exporting.
    return @('frame.number', 'frame.time_epoch', 'frame.interface_id', 'frame.len', 'frame.cap_len',
        'usb.irp_id', 'usb.irp_info', 'usb.transfer_type', 'usb.endpoint_address',
        'usb.bus_id', 'usb.device_address', 'usb.usbd_status', 'usb.data_len',
        'usb.control_stage', 'usb.bmRequestType', 'usb.setup.bRequest',
        'usb.setup.wValue', 'usb.setup.wIndex', 'usb.setup.wLength',
        'usbhub.setup.bRequest', 'usbhub.setup.wValue', 'usbhub.setup.wIndex', 'usbhub.setup.wLength')
}

function New-TbsTraceRecord {
    param([string]$Format, [string]$CaptureEpoch, [string]$AttachmentEpoch,
        [int]$Index, [string]$SourceName, $Metadata)
    $meta = [ordered]@{}
    foreach ($key in @('host', 'driver', 'build', 'attachment', 'descriptor', 'interface', 'alt', 'lock', 'mode')) {
        $meta[$key] = Get-TbsTraceValue $Metadata $key
    }
    return [pscustomobject][ordered]@{
        schema = 'tbs5530.trace/1'; capture_epoch = $CaptureEpoch; attachment_epoch = $AttachmentEpoch
        metadata = [pscustomobject]$meta
        seq = $Index; source_seq = $null; event = $null
        timestamp = [pscustomobject][ordered]@{
            epoch_seconds = $null; value = $null; end_value = $null; unit = $null; clock_domain = $null
        }
        transfer = $null; transfer_stage = $null; direction = $null; endpoint = $null; bus = $null; device = $null
        setup = $null; requested = $null; requested_source = $null; actual = $null; actual_source = $null
        status = $null; status_domain = $null; syscall_return = $null; syscall_errno = $null
        native = $null
        pairing = [pscustomobject][ordered]@{
            raw_id = $null; generation = $null; transfer_id = $null; state = 'unpaired'
            submit_seq = $null; completion_seq = $null; canceled = $false
        }
        payload = $null
        source = [pscustomobject][ordered]@{
            format = $Format; name = $SourceName; record_index = $Index
            line = $null; byte_offset = $null; json_path = $null; frame = $null; capture_interface = $null
        }
        warnings = @()
    }
}

function Add-TbsTraceWarning {
    param($Record, [string]$Code)
    if ($Record.warnings -notcontains $Code) { $Record.warnings += $Code }
}

function Set-TbsTracePayload {
    param($Record, $Hex, [bool]$Enabled, [int]$ExcerptBytes)
    if (-not $Enabled -or $null -eq $Hex) { return }
    # Never export FX2 firmware or an unclassified control payload.
    if ($Record.transfer -eq 'control' -and ($null -eq $Record.setup -or $null -eq $Record.setup.bRequest -or $Record.setup.bRequest -eq 160)) {
        Add-TbsTraceWarning $Record 'control_payload_suppressed'
        return
    }
    $s = ([string]$Hex) -replace '[:\s]', ''
    if ($s -notmatch '^(?:[0-9a-fA-F]{2})*$') { throw 'Malformed hex payload' }
    $bytes = New-Object byte[] ($s.Length / 2)
    for ($i = 0; $i -lt $bytes.Length; $i++) { $bytes[$i] = [Convert]::ToByte($s.Substring($i * 2, 2), 16) }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
    $excerpt = $null
    if ($ExcerptBytes -gt 0) { $excerpt = $s.Substring(0, [Math]::Min($s.Length, $ExcerptBytes * 2)).ToLowerInvariant() }
    $Record.payload = [pscustomobject]@{ captured_bytes = $bytes.Length; sha256 = $hash; excerpt_hex = $excerpt }
}

function ConvertFrom-TbsNativeRecord {
    param($Object, $Record, [bool]$PayloadEnabled, [int]$ExcerptBytes)
    if ($Object -isnot [pscustomobject]) { throw 'Native JSONL record must be an object' }
    $run = Get-TbsTraceValue $Object 'run_id'
    if ([string]::IsNullOrWhiteSpace([string]$run)) { throw 'Missing field: run_id' }
    $Record.attachment_epoch = $Record.attachment_epoch + ':run:' + $run
    if ((Get-TbsTraceValue $Object 'summary') -eq $true) {
        $Record.event = 'summary'
        $Record.native = [pscustomobject]@{}
        foreach ($key in @('attempted', 'submitted', 'completed', 'outstanding', 'delivered_bytes', 'cleanup_bytes_not_delivered')) {
            $v = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object $key) $key -Required
            if ($v -lt 0) { throw "Negative summary counter: $key" }
            $Record.native | Add-Member -NotePropertyName $key -NotePropertyValue $v
        }
        $q = Get-TbsTraceValue $Object 'quarantined'
        if ($q -isnot [bool]) { throw 'Missing/invalid summary quarantined flag' }
        $Record.native | Add-Member -NotePropertyName quarantined -NotePropertyValue $q
        return
    }
    $op = [string](Get-TbsTraceValue $Object 'operation')
    if ($op -notin @('SUBMIT', 'REAP', 'DISCARD', 'GUARD', 'COPY')) { throw 'Unsupported native operation' }
    if ((Get-TbsTraceValue $Object 'read_api') -cne 'NATIVE_USBFS_URB_8X4096') { throw 'Unsupported native read_api' }
    $Record.source_seq = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'event') 'event' -Required
    $before = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'before_ns') 'before_ns' -Required
    $after = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'after_ns') 'after_ns' -Required
    $clock = [string](Get-TbsTraceValue $Object 'clock')
    if ($before -lt 0 -or $after -lt $before -or [string]::IsNullOrWhiteSpace($clock)) { throw 'Invalid native clock interval/domain' }
    $Record.timestamp.value = [string]$before; $Record.timestamp.end_value = [string]$after
    $Record.timestamp.unit = 'ns'; $Record.timestamp.clock_domain = $clock
    $Record.syscall_return = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'syscall_return') 'syscall_return' -Required
    $Record.syscall_errno = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'syscall_errno') 'syscall_errno' -Required
    if ($Record.syscall_errno -lt 0) { throw 'Negative syscall_errno' }
    $Record.status = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'urb_status') 'urb_status'
    $Record.status_domain = 'linux-urb'
    $Record.endpoint = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'endpoint') 'endpoint' -Required
    $Record.transfer = 'bulk'
    $Record.direction = if (($Record.endpoint -band 128) -ne 0) { 'in' } else { 'out' }
    $slot = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'slot') 'slot' -Required
    $sequence = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'sequence') 'sequence' -Required
    $worker = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'worker') 'worker' -Required
    $Record.pairing.raw_id = "worker:${worker}:slot:$slot"
    $Record.requested = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'requested_length') 'requested_length'
    if ($null -ne $Record.requested) { $Record.requested_source = 'native.requested_length' }
    $rawActual = ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'actual_length') 'actual_length'
    $Record.native = [pscustomobject]@{
        operation = $op; slot = $slot; submission_sequence = $sequence
        allocated_capacity = (ConvertTo-TbsTraceInteger (Get-TbsTraceValue $Object 'allocated_capacity') 'allocated_capacity')
        reported_actual_length = $rawActual
    }
    switch ($op) {
        SUBMIT { $Record.event = if ($Record.syscall_return -eq 0) { 'submit' } else { 'submit_error' } }
        REAP {
            $Record.event = if ($Record.syscall_return -eq 0) { 'complete' } else { 'syscall_error' }
            if ($Record.syscall_return -eq 0) {
                $Record.actual = $rawActual
                if ($null -ne $rawActual) { $Record.actual_source = 'native.actual_length' }
            }
        }
        DISCARD { $Record.event = 'cancel_request' }
        default { $Record.event = 'observation' }
    }
    Set-TbsTracePayload $Record (Get-TbsTraceValue $Object 'payload_hex') $PayloadEnabled $ExcerptBytes
}

function ConvertFrom-TbsUsbmonRecord {
    param([string]$Line, $Record, [string]$ClockDomain, [bool]$PayloadEnabled, [int]$ExcerptBytes)
    if ($Line -notmatch '^(?<id>[0-9a-fA-F]+)\s+(?<time>\d+)\s+(?<event>[SCE])\s+(?<type>[BCI])(?<dir>[io]):(?<bus>\d+):(?<dev>\d+):(?<ep>\d+)\s+(?<tail>.+)$') {
        throw 'Unsupported/malformed usbmon text (only B/C/I S/C/E, no isochronous or binary input)'
    }
    $m = $Matches
    $digits = $m.id.TrimStart('0').ToLowerInvariant()
    if ($digits.Length -eq 0) { $digits = '0' }
    $Record.pairing.raw_id = $digits
    $Record.timestamp.value = $m.time; $Record.timestamp.unit = 'us'; $Record.timestamp.clock_domain = $ClockDomain
    $Record.transfer = @{ B = 'bulk'; C = 'control'; I = 'interrupt' }[$m.type]
    $Record.direction = if ($m.dir -eq 'i') { 'in' } else { 'out' }
    $Record.bus = ConvertTo-TbsTraceInteger $m.bus 'bus'
    $Record.device = ConvertTo-TbsTraceInteger $m.dev 'device'
    $ep = ConvertTo-TbsTraceInteger $m.ep 'endpoint'
    if ($ep -gt 15) { throw 'Invalid usbmon endpoint number' }
    $Record.endpoint = $ep + $(if ($m.dir -eq 'i') { 128 } else { 0 })
    $Record.event = @{ S = 'submit'; C = 'complete'; E = 'submit_error' }[$m.event]
    $tail = $m.tail
    if ($m.event -eq 'S' -and $m.type -eq 'C') {
        if ($tail -notmatch '^s\s+([0-9a-fA-F]{2})\s+([0-9a-fA-F]{2})\s+([0-9a-fA-F]{4})\s+([0-9a-fA-F]{4})\s+([0-9a-fA-F]{4})\s+(\d+)(?:\s+([<=])(.*))?$') {
            throw 'Malformed usbmon control setup/length'
        }
        $x = $Matches
        $Record.setup = [pscustomobject]@{
            bmRequestType = (ConvertTo-TbsTraceInteger $x[1] 'bmRequestType' -Hex)
            bRequest = (ConvertTo-TbsTraceInteger $x[2] 'bRequest' -Hex)
            wValue = (ConvertTo-TbsTraceInteger $x[3] 'wValue' -Hex)
            wIndex = (ConvertTo-TbsTraceInteger $x[4] 'wIndex' -Hex)
            wLength = (ConvertTo-TbsTraceInteger $x[5] 'wLength' -Hex)
        }
        $Record.direction = if (($Record.setup.bmRequestType -band 128) -ne 0) { 'in' } else { 'out' }
        $Record.requested = ConvertTo-TbsTraceInteger $x[6] 'submission length'
        $Record.requested_source = 'usbmon.submission.length'
        if ($Record.requested -ne $Record.setup.wLength) { Add-TbsTraceWarning $Record 'setup_length_mismatch' }
        if ($x.ContainsKey(7) -and $x[7] -eq '=') { Set-TbsTracePayload $Record $x[8] $PayloadEnabled $ExcerptBytes }
    } else {
        # Missing length is allowed but stays null. The status is not a syscall errno.
        if ($tail -notmatch '^(?<status>-?\d+)(?::\d+)?(?:\s+(?<len>\d+))?(?:\s+(?<mark>[<>=])(?<data>.*))?$') {
            throw 'Malformed usbmon status/length'
        }
        $x = $Matches
        # usbmon uses '>' for an OUT callback with no captured data (not a length).
        if ($x['mark'] -in @('<', '>') -and -not [string]::IsNullOrWhiteSpace($x['data'])) {
            throw 'Malformed usbmon data tag: only = may precede data words'
        }
        $Record.status = ConvertTo-TbsTraceInteger $x.status 'urb status'
        $Record.status_domain = if ($m.event -eq 'S') { 'linux-urb-submission' } else { 'linux-urb' }
        $length = ConvertTo-TbsTraceInteger $x['len'] 'length'
        if ($m.event -eq 'S') {
            $Record.requested = $length
            if ($null -ne $length) { $Record.requested_source = 'usbmon.submission.length' }
        } elseif ($m.event -eq 'C') {
            $Record.actual = $length
            if ($null -ne $length) { $Record.actual_source = 'usbmon.completion.length' }
        }
        if ($x['mark'] -eq '=') { Set-TbsTracePayload $Record $x['data'] $PayloadEnabled $ExcerptBytes }
    }
}

function ConvertFrom-TbsUsbPcapRecord {
    param($Object, $Record, [bool]$PayloadEnabled, [int]$ExcerptBytes)
    $layers = Get-TbsTraceValue (Get-TbsTraceValue $Object '_source') 'layers'
    if ($null -eq $layers) { throw 'Expected TShark -T json array with _source.layers' }
    $Record.source.frame = ConvertTo-TbsTraceInteger (Get-TbsTraceField $layers 'frame.number' -Required) 'frame.number' -Required
    $Record.source_seq = $Record.source.frame
    $Record.source.capture_interface = ConvertTo-TbsTraceInteger (Get-TbsTraceField $layers 'frame.interface_id') 'frame.interface_id'
    $time = [string](Get-TbsTraceField $layers 'frame.time_epoch' -Required)
    if ($time -notmatch '^\d+(?:\.\d+)?$') { throw 'Invalid frame.time_epoch' }
    $Record.timestamp.epoch_seconds = $time; $Record.timestamp.value = $time
    $Record.timestamp.unit = 's'; $Record.timestamp.clock_domain = 'pcap.frame.time_epoch'
    $id = [string](Get-TbsTraceField $layers 'usb.irp_id' -Required)
    if ($id -notmatch '^(?:0x)?[0-9a-fA-F]{1,16}$') { throw 'Invalid usb.irp_id' }
    $digits = ($id.ToLowerInvariant() -replace '^0x', '').TrimStart('0')
    if ($digits.Length -eq 0) { $digits = '0' }
    $Record.pairing.raw_id = '0x' + $digits
    $info = ConvertTo-TbsTraceInteger (Get-TbsTraceField $layers 'usb.irp_info' -Required) 'usb.irp_info' -Required
    $type = ConvertTo-TbsTraceInteger (Get-TbsTraceField $layers 'usb.transfer_type' -Required) 'usb.transfer_type' -Required
    if ($type -notin @(1, 2, 3)) { throw 'Unsupported USBPcap transfer type (isochronous is not supported)' }
    $Record.transfer = @{ 1 = 'interrupt'; 2 = 'control'; 3 = 'bulk' }[[int]$type]
    $Record.event = if (($info -band 1) -eq 0) { 'submit' } else { 'complete' }
    $Record.endpoint = ConvertTo-TbsTraceInteger (Get-TbsTraceField $layers 'usb.endpoint_address' -Required) 'usb.endpoint_address' -Required
    $Record.direction = if (($Record.endpoint -band 128) -ne 0) { 'in' } else { 'out' }
    $Record.bus = ConvertTo-TbsTraceInteger (Get-TbsTraceField $layers 'usb.bus_id' -Required) 'usb.bus_id' -Required
    $Record.device = ConvertTo-TbsTraceInteger (Get-TbsTraceField $layers 'usb.device_address' -Required) 'usb.device_address' -Required
    $Record.status = ConvertTo-TbsTraceInteger (Get-TbsTraceField $layers 'usb.usbd_status') 'usb.usbd_status'
    $Record.status_domain = 'windows-usbd'
    $stage = ConvertTo-TbsTraceInteger (Get-TbsTraceField $layers 'usb.control_stage') 'usb.control_stage'
    $Record.transfer_stage = $stage
    if ($Record.transfer -eq 'control') {
        $Record.direction = $null
        if ($null -eq $stage -or $stage -notin @(0, 1, 2, 3)) { throw 'Missing/invalid USBPcap control stage' }
        if ($stage -eq 1) { $Record.event = 'control_data' }
        elseif ($stage -eq 0 -and ($info -band 1) -eq 1) { throw 'Ambiguous USBPcap setup completion' }
        elseif ($stage -in @(2, 3) -and ($info -band 1) -eq 0) { $Record.event = 'control_stage' }
        $names = @('usb.bmRequestType', 'usb.setup.bRequest', 'usb.setup.wValue', 'usb.setup.wIndex', 'usb.setup.wLength')
        $values = @($names | ForEach-Object { Get-TbsTraceField $layers $_ })
        for ($i = 1; $i -lt 5; $i++) {
            $hub = Get-TbsTraceField $layers ($names[$i] -replace '^usb\.', 'usbhub.')
            if ($null -ne $hub -and $null -ne $values[$i] -and
                (ConvertTo-TbsTraceInteger $hub 'hub setup') -ne (ConvertTo-TbsTraceInteger $values[$i] 'usb setup')) {
                throw 'Conflicting usb/usbhub setup fields'
            }
            if ($null -eq $values[$i]) { $values[$i] = $hub }
        }
        $present = @($values | Where-Object { $null -ne $_ }).Count
        if ($present -gt 0) {
            # Standard-request dissectors sometimes export specialized rather than generic fields.
            # Preserve absent components as null; never reconstruct missing setup bytes.
            if ($present -lt 5) { Add-TbsTraceWarning $Record 'incomplete_setup_fields' }
            $Record.setup = [pscustomobject]@{
                bmRequestType = (ConvertTo-TbsTraceInteger $values[0] $names[0])
                bRequest = (ConvertTo-TbsTraceInteger $values[1] $names[1])
                wValue = (ConvertTo-TbsTraceInteger $values[2] $names[2])
                wIndex = (ConvertTo-TbsTraceInteger $values[3] $names[3])
                wLength = (ConvertTo-TbsTraceInteger $values[4] $names[4])
            }
            $Record.requested = $Record.setup.wLength
            if ($null -ne $Record.requested) { $Record.requested_source = 'usb.setup.wLength' }
            if ($null -ne $Record.setup.bmRequestType) {
                $Record.direction = if (($Record.setup.bmRequestType -band 128) -ne 0) { 'in' } else { 'out' }
            }
        } elseif ($stage -eq 0) { throw 'Missing USBPcap setup packet fields' }
    }
    # data_len is NOT a requested buffer size, especially an IN submit with no payload.
    if ($Record.event -in @('complete', 'control_data')) {
        $Record.actual = ConvertTo-TbsTraceInteger (Get-TbsTraceField $layers 'usb.data_len') 'usb.data_len'
        if ($null -ne $Record.actual) { $Record.actual_source = 'usb.data_len (reported stage length)' }
    }
    $cap = ConvertTo-TbsTraceInteger (Get-TbsTraceField $layers 'frame.cap_len') 'frame.cap_len'
    $len = ConvertTo-TbsTraceInteger (Get-TbsTraceField $layers 'frame.len') 'frame.len'
    if ($null -ne $cap -and $null -ne $len -and $cap -lt $len) { Add-TbsTraceWarning $Record 'capture_truncated' }
    # Payload processing is delayed until setup has been inherited during pairing.
    if ($PayloadEnabled) { return (Get-TbsTraceField $layers 'usb.capdata') }
}

function Join-TbsTracePairs {
    param($Records)
    $active = @{}; $generations = @{}; $ambiguous = @{}
    foreach ($r in $Records) {
        if ($r.event -eq 'summary') { $r.pairing.state = 'not_applicable'; continue }
        $r.pairing.canceled = [bool](($r.event -eq 'complete') -and (
            ($r.status_domain -eq 'linux-urb' -and $r.status -in @(-2, -104)) -or
            ($r.status_domain -eq 'windows-usbd' -and $r.status -eq 3221291008)))
        $key = @($r.capture_epoch, $r.attachment_epoch, $r.timestamp.clock_domain,
            $r.source.capture_interface, $r.bus, $r.device, $r.pairing.raw_id) | ConvertTo-Json -Compress
        $p = $null
        if ($active.ContainsKey($key)) { $p = $active[$key] }
        if ($r.event -eq 'submit' -or ($r.event -eq 'submit_error' -and $null -eq $p)) {
            if ($null -ne $p) {
                foreach ($old in $p.records) { $old.pairing.state = 'missing_completion'; Add-TbsTraceWarning $old 'id_reused_before_completion' }
                # USBPcap/usbmon have no native submission sequence to resolve late completions.
                if ($null -eq $r.native) { $ambiguous[$key] = $true }
            }
            $g = 1
            if ($generations.ContainsKey($key)) { $g = $generations[$key] + 1 }
            $generations[$key] = $g
            $r.pairing.generation = $g
            $r.pairing.transfer_id = "scope=$key;generation=$g"
            $r.pairing.submit_seq = $r.seq
            $r.pairing.state = if ($r.event -eq 'submit_error') { 'submit_failed' } else { 'pending' }
            if ($r.event -eq 'submit') { $active[$key] = @{ submit = $r; records = [Collections.Generic.List[object]]::new() }; $active[$key].records.Add($r) }
            continue
        }
        if ($r.event -notin @('complete', 'submit_error', 'cancel_request', 'control_data', 'control_stage')) {
            $r.pairing.state = 'not_applicable'; continue
        }
        if ($ambiguous.ContainsKey($key)) {
            $r.pairing.state = 'ambiguous_id_reuse'; Add-TbsTraceWarning $r 'ambiguous_id_reuse'
            continue
        }
        if ($null -ne $p) {
            $s = $p.submit
            $mismatch = $s.endpoint -ne $r.endpoint -and $r.transfer -ne 'control'
            $mismatch = $mismatch -or $s.transfer -ne $r.transfer
            if ($null -ne $s.native -and $null -ne $r.native) {
                $mismatch = $mismatch -or $s.native.submission_sequence -ne $r.native.submission_sequence
            }
            if ($null -ne $s.setup -and $null -ne $r.setup) {
                $mismatch = $mismatch -or (($s.setup | ConvertTo-Json -Compress) -cne ($r.setup | ConvertTo-Json -Compress))
            }
            if ($mismatch) { Add-TbsTraceWarning $r 'pair_identity_mismatch'; $p = $null }
        }
        if ($null -eq $p) {
            $r.pairing.state = 'missing_submit'; Add-TbsTraceWarning $r 'missing_submit'
            continue
        }
        $r.pairing.generation = $s.pairing.generation
        $r.pairing.transfer_id = $s.pairing.transfer_id; $r.pairing.submit_seq = $s.seq
        if ($null -eq $r.requested -and $null -ne $s.requested) {
            $r.requested = $s.requested; $r.requested_source = "paired_submit:$($s.seq):$($s.requested_source)"
        } elseif ($null -ne $r.requested -and $null -ne $s.requested -and $r.requested -ne $s.requested) {
            Add-TbsTraceWarning $r 'requested_length_mismatch'
        }
        if ($null -eq $r.setup -and $null -ne $s.setup) { $r.setup = $s.setup; $r.direction = $s.direction }
        $p.records.Add($r)
        if ($r.event -in @('complete', 'submit_error')) {
            $canceled = $r.pairing.canceled
            foreach ($member in $p.records) {
                $member.pairing.state = if ($r.event -eq 'submit_error') { 'submit_failed' } else { 'paired' }
                $member.pairing.completion_seq = $r.seq; $member.pairing.canceled = [bool]$canceled
            }
            $active.Remove($key)
        } else { $r.pairing.state = 'pending' }
    }
    foreach ($p in $active.Values) {
        foreach ($r in $p.records) { $r.pairing.state = 'missing_completion'; Add-TbsTraceWarning $r 'missing_completion_in_window' }
    }
}

function Convert-Tbs5530Trace {
    <#
    .SYNOPSIS
    Chuẩn hóa bằng chứng USB offline, không gọi TShark hoặc phần cứng.
    .DESCRIPTION
    Giữ thứ tự nguồn và clock riêng. requested không suy từ actual. CaptureEpoch và
    AttachmentEpoch do bên thu thập cấp. AttachmentBoundaries ánh xạ ordinal 1-based
    sang epoch mới khi biết có tháo/cắm hoặc reset; không tự đoán từ thời gian.
    Metadata nhận host/driver/build/attachment/descriptor/interface/alt/lock/mode.
    Mặc định payload=null. IncludePayload chỉ hash các byte có trong capture;
    PayloadExcerptBytes 0..64. Không xuất payload control A0/không rõ setup.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$InputPath,
        [Parameter(Mandatory)][ValidateSet('NativeJsonl', 'UsbmonText', 'UsbPcapJson')][string]$Format,
        [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$CaptureEpoch,
        [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$AttachmentEpoch,
        [hashtable]$Metadata = @{}, [hashtable]$AttachmentBoundaries = @{},
        [string]$UsbmonClockDomain = 'usbmon.timestamp_us:unspecified',
        [ValidateRange(1, 1000000)][int]$MaxRecords = 100000,
        [ValidateRange(1, 268435456)][long]$MaxInputBytes = 67108864,
        [switch]$IncludePayload, [ValidateRange(0, 64)][int]$PayloadExcerptBytes = 0
    )
    if ($PayloadExcerptBytes -gt 0 -and -not $IncludePayload) { throw 'PayloadExcerptBytes requires IncludePayload' }
    $path = (Resolve-Path -LiteralPath $InputPath -ErrorAction Stop).ProviderPath
    $file = Get-Item -LiteralPath $path -ErrorAction Stop
    if ($file.PSIsContainer -or $file.Length -gt $MaxInputBytes) { throw 'Input is a directory or exceeds MaxInputBytes' }
    $inputStream = [IO.File]::Open($path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
    try {
        if ($inputStream.Length -gt $MaxInputBytes) { throw 'Input grew beyond MaxInputBytes' }
        $bytes = New-Object byte[] ([int]$inputStream.Length)
        $read = 0
        while ($read -lt $bytes.Length) {
            $n = $inputStream.Read($bytes, $read, $bytes.Length - $read)
            if ($n -eq 0) { throw 'Incomplete input snapshot' }
            $read += $n
        }
    } finally { $inputStream.Dispose() }
    $utf8 = [Text.UTF8Encoding]::new($false, $true)
    $start = 0
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191) { $start = 3 }
    try { $text = $utf8.GetString($bytes, $start, $bytes.Length - $start) }
    catch { throw 'Input must be valid UTF-8 (UTF-16/binary unsupported)' }
    if ([string]::IsNullOrWhiteSpace($text)) { throw 'Empty trace' }
    $records = [Collections.Generic.List[object]]::new()
    $payloads = @{}; $truncated = $false; $offset = $start; $lineNumber = 0; $inputRecords = 0
    $items = $null
    if ($Format -eq 'UsbPcapJson') {
        if (-not $text.TrimStart().StartsWith('[')) { throw 'UsbPcapJson requires a TShark -T json array; EK/JSONL unsupported' }
        try { $decoded = ConvertFrom-Json -InputObject $text -ErrorAction Stop; $items = @($decoded) }
        catch { throw 'Malformed/incomplete TShark JSON array' }
        if ($items.Count -eq 0) { throw 'Empty TShark JSON array' }
    } else {
        $items = [regex]::Matches($text, '[^\r\n]*(?:\r\n|\n|\r|$)')
    }
    $epoch = $AttachmentEpoch
    foreach ($item in $items) {
        $lineNumber++
        $line = $null; $byteOffset = $offset
        if ($Format -ne 'UsbPcapJson') {
            $line = $item.Value.TrimEnd([char[]]"`r`n")
            $offset += $utf8.GetByteCount($item.Value)
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            if ($Format -eq 'UsbmonText' -and $line.TrimStart().StartsWith('#')) { continue }
        }
        $inputRecords++
        if ($records.Count -ge $MaxRecords) { $truncated = $true; break }
        $index = $records.Count + 1
        if ($AttachmentBoundaries.ContainsKey($index)) { $epoch = [string]$AttachmentBoundaries[$index] }
        elseif ($AttachmentBoundaries.ContainsKey([string]$index)) { $epoch = [string]$AttachmentBoundaries[[string]$index] }
        if ([string]::IsNullOrWhiteSpace($epoch)) { throw 'Empty attachment epoch boundary' }
        $r = New-TbsTraceRecord $Format $CaptureEpoch $epoch $index $file.Name $Metadata
        try {
            switch ($Format) {
                NativeJsonl {
                    $r.source.line = $lineNumber; $r.source.byte_offset = $byteOffset
                    try { $o = $line | ConvertFrom-Json -ErrorAction Stop } catch { throw 'Malformed/incomplete native JSONL object' }
                    ConvertFrom-TbsNativeRecord $o $r ([bool]$IncludePayload) $PayloadExcerptBytes
                }
                UsbmonText {
                    $r.source.line = $lineNumber; $r.source.byte_offset = $byteOffset
                    ConvertFrom-TbsUsbmonRecord $line $r $UsbmonClockDomain ([bool]$IncludePayload) $PayloadExcerptBytes
                }
                UsbPcapJson {
                    $r.source.json_path = '$[' + ($index - 1) + ']'
                    $payloads[$index] = ConvertFrom-TbsUsbPcapRecord $item $r ([bool]$IncludePayload) $PayloadExcerptBytes
                }
            }
            foreach ($field in @('requested', 'actual')) {
                if ($null -ne $r.$field -and $r.$field -lt 0) { throw "Negative length: $field" }
            }
            if ($null -ne $r.endpoint -and ($r.endpoint -lt 0 -or $r.endpoint -gt 255)) { throw 'Invalid endpoint address' }
            if ($null -ne $r.setup) {
                foreach ($field in @('bmRequestType', 'bRequest', 'wValue', 'wIndex', 'wLength')) {
                    $max = if ($field -in @('bmRequestType', 'bRequest')) { 255 } else { 65535 }
                    if ($null -ne $r.setup.$field -and ($r.setup.$field -lt 0 -or $r.setup.$field -gt $max)) { throw "Invalid setup field: $field" }
                }
            }
        } catch {
            # Never include the raw line or payload in errors.
            $location = if ($Format -eq 'UsbPcapJson') { "json_path=$($r.source.json_path)" }
                else { "line=$lineNumber byte_offset=$byteOffset" }
            throw "Trace parse failed: format=$Format record=$index $location : $($_.Exception.Message)"
        }
        $records.Add($r)
    }
    if ($records.Count -eq 0) { throw 'No supported records' }
    Join-TbsTracePairs $records
    if ($Format -eq 'UsbPcapJson' -and $IncludePayload) {
        foreach ($r in $records) { Set-TbsTracePayload $r $payloads[$r.seq] $true $PayloadExcerptBytes }
    }
    $warningCounts = [ordered]@{}; $events = [ordered]@{}; $states = [ordered]@{}
    foreach ($r in $records) {
        foreach ($entry in @(@{ map = $events; key = $r.event }, @{ map = $states; key = $r.pairing.state })) {
            if (-not $entry.map.Contains($entry.key)) { $entry.map[$entry.key] = 0 }
            $entry.map[$entry.key]++
        }
        foreach ($w in $r.warnings) {
            if (-not $warningCounts.Contains($w)) { $warningCounts[$w] = 0 }
            $warningCounts[$w]++
        }
    }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
    return [pscustomobject]@{
        Records = $records.ToArray()
        Summary = [pscustomobject][ordered]@{
            schema = 'tbs5530.trace-summary/1'; format = $Format; input_name = $file.Name
            input_bytes = $bytes.Length; input_sha256 = $hash; capture_epoch = $CaptureEpoch
            records = $records.Count; events = [pscustomobject]$events; pairing_states = [pscustomobject]$states
            warnings = [pscustomobject]$warningCounts; errors = 0; truncated = $truncated
            max_records = $MaxRecords; max_input_bytes = $MaxInputBytes
            payload_enabled = [bool]$IncludePayload; payload_excerpt_bytes = $PayloadExcerptBytes
            limits = @('Giữ thứ tự nguồn; không đồng bộ hoặc trộn các miền đồng hồ.',
                'Thiếu submit/completion trong cửa sổ quan sát không chứng minh thao tác chưa xảy ra.',
                'USBPcap bulk không biết requested; tuyệt đối không suy từ data_len.',
                'Tháo/cắm hoặc reset dùng lại địa chỉ cần AttachmentBoundaries do bên thu thập cấp.',
                'Không kiểm chứng phần cứng, firmware, khóa sóng, tốc độ hay MPEG-TS.')
        }
    }
}
