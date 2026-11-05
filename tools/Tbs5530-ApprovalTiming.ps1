# Pure classification, also used by deterministic offline tests. No clocks or I/O are read here.
function Get-TBS5530ChannelStopKind {
    param(
        [bool]$TotalDeadlineExpired = $false,
        [bool]$AndroidAckExpired = $false,
        [bool]$TransportClosed = $false,
        [bool]$IdentityChanged = $false,
        [bool]$ResponseTimedOut = $false
    )
    if ($TotalDeadlineExpired) { return 'TOTAL_BENCHMARK_DEADLINE' }
    if ($AndroidAckExpired) { return 'ACK_TIMEOUT' }
    if ($IdentityChanged) { return 'ADB_IDENTITY_LOST' }
    if ($TransportClosed) { return 'TRANSPORT_LOST' }
    if ($ResponseTimedOut) { return 'CHANNEL_RESPONSE_TIMEOUT' }
    return 'CHANNEL_PROTOCOL_ERROR'
}
