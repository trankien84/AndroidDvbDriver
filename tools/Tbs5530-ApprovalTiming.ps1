# Classification and bounded host-side capture; no ADB or hardware operation is performed here.
function Start-TBS5530BoundedTextCapture {
    param([Parameter(Mandatory)][IO.StreamReader]$Reader, [int]$MaximumCharacters = 262144)
    if ($MaximumCharacters -lt 1 -or $MaximumCharacters -gt 262144) { throw 'Capture bound không hợp lệ.' }
    if (-not ('Tbs5530.HostTextCapture' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Text;
using System.Threading.Tasks;
namespace Tbs5530 {
    public sealed class HostTextSnapshot {
        public string Text, ReadError;
        public long CharactersSeen;
        public bool Truncated, Complete;
    }
    public sealed class HostTextCapture {
        private readonly object gate = new object();
        private readonly StringBuilder text = new StringBuilder();
        private readonly int maximum;
        private long seen;
        private bool complete;
        private string readError;
        public Task Completion { get; private set; }
        public HostTextCapture(StreamReader reader, int maximum) {
            this.maximum = maximum;
            Completion = Drain(reader);
        }
        private async Task Drain(StreamReader reader) {
            char[] buffer = new char[4096];
            try {
                int count;
                while ((count = await reader.ReadAsync(buffer, 0, buffer.Length).ConfigureAwait(false)) > 0) {
                    lock (gate) {
                        seen += count;
                        int keep = Math.Min(count, maximum - text.Length);
                        if (keep > 0) text.Append(buffer, 0, keep);
                    }
                }
            } catch (Exception error) {
                lock (gate) { readError = error.GetType().Name; }
            } finally { lock (gate) { complete = true; } }
        }
        public HostTextSnapshot Snapshot() {
            lock (gate) {
                return new HostTextSnapshot { Text = text.ToString(), CharactersSeen = seen,
                    Truncated = seen > maximum, Complete = complete, ReadError = readError };
            }
        }
    }
}
'@
    }
    return [Tbs5530.HostTextCapture]::new($Reader, $MaximumCharacters)
}
function Get-TBS5530BenchmarkChildState {
    param($Child)
    if ($null -eq $Child -or -not $Child.Process.HasExited) { return 'RUNNING' }
    $output = $Child.Stdout.Snapshot()
    if ($Child.Process.ExitCode -ne 0 -or
        $output.Text -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|INSTRUMENTATION_STATUS_CODE: -[12]\b|shortMsg=|Process crashed|FAILURE:|INSTRUMENTATION_CODE: (?!-1\b)-?\d+') {
        return 'FAILED'
    }
    return 'FINISHED'
}
function ConvertFrom-TBS5530StoredFrame {
    param([Parameter(Mandatory)][string]$Line)
    if ($Line -cnotmatch '^STORED ([0-9]+)(?: HOST_ACK_PERSISTED ([0-9]+))?$') {
        throw 'FAIL-STOP: không xác nhận ghi atomic.'
    }
    $ack = 0L; $persisted = 0L
    $hasMeasurement = -not [string]::IsNullOrEmpty($Matches[2])
    if (-not [long]::TryParse($Matches[1], [ref]$ack) -or
        ($hasMeasurement -and -not [long]::TryParse($Matches[2], [ref]$persisted))) {
        throw 'FAIL-STOP: timestamp STORED vượt giới hạn.'
    }
    return @{ host_ack_elapsed_ms = $ack
        host_ack_persisted_elapsed_ms = $(if ($hasMeasurement) { $persisted } else { $null })
        host_ack_persisted_status = $(if ($hasMeasurement) { 'MEASURED' } else { 'NOT_MEASURED' })
        host_ack_persisted_scope = 'TEMP_WRITE_RENAME_COMPLETE_NO_EXPLICIT_FSYNC'
        host_ack_persisted_clock = 'DEVICE_UPTIME_10MS_LOWER_BOUND_UNCLAMPED'
        host_ack_durable_persisted_status = 'NOT_MEASURED' }
}
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
