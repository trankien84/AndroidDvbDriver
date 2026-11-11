using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Win32.SafeHandles;

namespace Tbs5530Observation
{
    public sealed class UsbNode
    {
        public string ConnectionPath { get; internal set; }
        public int Vid { get; internal set; }
        public int Pid { get; internal set; }
        public int Address { get; internal set; }
    }

    // These IOCTLs only query the root-hub cache and node metadata, never a tuner handle.
    public static class UsbTopology
    {
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern SafeFileHandle CreateFile(string name, uint access, uint share,
            IntPtr security, uint disposition, uint flags, IntPtr template);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool DeviceIoControl(SafeFileHandle handle, uint code,
            byte[] input, int inputSize, byte[] output, int outputSize, out int returned, IntPtr overlapped);

        private static SafeFileHandle Open(string name)
        {
            SafeFileHandle handle = CreateFile(name, 0, 3, IntPtr.Zero, 3, 0, IntPtr.Zero);
            if (handle.IsInvalid) { handle.Dispose(); throw new Win32Exception(Marshal.GetLastWin32Error(), "Metadata handle failed"); }
            return handle;
        }

        private static byte[] Query(SafeFileHandle handle, uint code, int index, int minimum)
        {
            byte[] data = new byte[65536];
            Buffer.BlockCopy(BitConverter.GetBytes(index), 0, data, 0, 4);
            int returned;
            if (!DeviceIoControl(handle, code, data, data.Length, data, data.Length, out returned, IntPtr.Zero))
                throw new Win32Exception(Marshal.GetLastWin32Error(), "Metadata IOCTL failed");
            if (returned < minimum || returned > data.Length) throw new InvalidDataException("Truncated hub metadata");
            Array.Resize(ref data, returned);
            return data;
        }

        private static string DevicePath(string value)
        {
            if (value.StartsWith(@"\??\", StringComparison.Ordinal)) value = @"\\.\" + value.Substring(4);
            else if (!value.StartsWith(@"\\", StringComparison.Ordinal)) value = @"\\.\" + value;
            if (!(value.StartsWith(@"\\.\USB", StringComparison.OrdinalIgnoreCase) ||
                  value.StartsWith(@"\\?\USB", StringComparison.OrdinalIgnoreCase)))
                throw new InvalidDataException("Unexpected hub metadata path");
            return value;
        }

        public static string GetRoot(string control)
        {
            if (!System.Text.RegularExpressions.Regex.IsMatch(control, @"^\\\\\.\\USBPcap[1-9][0-9]*$"))
                throw new InvalidDataException("Invalid discovered capture device");
            using (SafeFileHandle handle = Open(control))
            {
                byte[] data = new byte[65536];
                int returned;
                if (!DeviceIoControl(handle, 0x22200C, null, 0, data, data.Length, out returned, IntPtr.Zero))
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "Root-hub cache query failed");
                if (returned < 4 || returned > data.Length || (returned & 1) != 0)
                    throw new InvalidDataException("Invalid root-hub cache response");
                return DevicePath(Encoding.Unicode.GetString(data, 0, returned).TrimEnd('\0'));
            }
        }

        public static UsbNode[] GetNodes(string root)
        {
            var result = new List<UsbNode>();
            Walk(DevicePath(root), "", 0, new HashSet<string>(StringComparer.OrdinalIgnoreCase), result);
            return result.ToArray();
        }

        private static void Walk(string hub, string ports, int depth, HashSet<string> visited, List<UsbNode> result)
        {
            if (depth > 8 || !visited.Add(hub)) throw new InvalidDataException("Hub recursion guard");
            using (SafeFileHandle handle = Open(hub))
            {
                byte[] info = Query(handle, 0x220408, 0, 7);
                int count = info[6];
                if (count < 1 || count > 255) throw new InvalidDataException("Invalid hub port count");
                for (int port = 1; port <= count; port++)
                {
                    byte[] node = Query(handle, 0x220448, port, 35);
                    if (BitConverter.ToUInt32(node, 31) != 1) continue;
                    int address = BitConverter.ToUInt16(node, 25);
                    string chain = ports + "/" + port;
                    result.Add(new UsbNode {
                        ConnectionPath = hub + "|" + chain,
                        Vid = BitConverter.ToUInt16(node, 12), Pid = BitConverter.ToUInt16(node, 14), Address = address
                    });
                    if (node[24] != 0)
                    {
                        byte[] name = Query(handle, 0x220414, port, 10);
                        int actual = checked((int)BitConverter.ToUInt32(name, 4));
                        if (actual < 10 || actual > name.Length || (actual & 1) != 0)
                            throw new InvalidDataException("Invalid child hub name size");
                        Walk(DevicePath(Encoding.Unicode.GetString(name, 8, actual - 8).TrimEnd('\0')),
                            chain, depth + 1, visited, result);
                    }
                }
            }
        }
    }

    public static class SafePaths
    {
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern SafeFileHandle CreateFile(string name, uint access, uint share,
            IntPtr security, uint disposition, uint flags, IntPtr template);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool CreateDirectory(string name, IntPtr security);

        public static string Check(string path)
        {
            if (String.IsNullOrWhiteSpace(path) || !System.Text.RegularExpressions.Regex.IsMatch(path, @"^[A-Za-z]:\\"))
                throw new IOException("A local absolute Windows path is required");
            if (path.IndexOf('/') >= 0 || path.Substring(2).IndexOf(':') >= 0 || path.IndexOfAny(new[] { '*', '?', '\0' }) >= 0)
                throw new IOException("Invalid path syntax");
            string[] parts = path.Substring(3).Split('\\');
            if (parts.Any(p => p == "." || p == ".." || p.EndsWith(".", StringComparison.Ordinal) || p.EndsWith(" ", StringComparison.Ordinal)))
                throw new IOException("Path traversal or ambiguous component");
            string full = Path.GetFullPath(path).TrimEnd('\\');
            if (full.Length < 3) throw new IOException("A volume root is not an output path");
            string cursor = Path.GetPathRoot(full);
            CheckExisting(cursor);
            foreach (string part in full.Substring(3).Split('\\'))
            {
                if (part.Length == 0) throw new IOException("Empty path component");
                cursor = Path.Combine(cursor, part);
                CheckExisting(cursor);
            }
            return full;
        }

        private static void CheckExisting(string path)
        {
            try
            {
                if ((File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0)
                    throw new IOException("Reparse points are forbidden");
            }
            catch (FileNotFoundException) { }
            catch (DirectoryNotFoundException) { }
        }

        public static IDisposable LockDirectories(string directory)
        {
            string full = Check(directory);
            var handles = new List<SafeFileHandle>();
            try
            {
                string cursor = Path.GetPathRoot(full);
                var chain = new List<string> { cursor };
                foreach (string part in full.Substring(3).Split('\\')) { cursor = Path.Combine(cursor, part); chain.Add(cursor); }
                foreach (string item in chain)
                {
                    CheckExisting(item);
                    SafeFileHandle handle = CreateFile(item, 0, 3, IntPtr.Zero, 3, 0x02200000, IntPtr.Zero);
                    if (handle.IsInvalid) { handle.Dispose(); throw new Win32Exception(Marshal.GetLastWin32Error(), "Directory lease failed"); }
                    handles.Add(handle);
                    if (!Directory.Exists(item)) throw new IOException("Directory lease target missing");
                    CheckExisting(item);
                }
                return new DirectoryLease(handles);
            }
            catch { foreach (SafeFileHandle handle in handles) handle.Dispose(); throw; }
        }

        public static void CreateNewDirectory(string path)
        {
            path = Check(path);
            if (!CreateDirectory(path, IntPtr.Zero)) throw new Win32Exception(Marshal.GetLastWin32Error(), "Output directory must be new");
            Check(path);
        }

        private sealed class DirectoryLease : IDisposable
        {
            private readonly List<SafeFileHandle> handles;
            public DirectoryLease(List<SafeFileHandle> value) { handles = value; }
            public void Dispose() { foreach (SafeFileHandle handle in handles) handle.Dispose(); }
        }

        private static bool TransientSharing(Exception error)
        {
            int code = error.HResult & 0xffff;
            return (error is IOException || error is UnauthorizedAccessException) && (code == 5 || code == 32 || code == 33);
        }

        public static string ReadJson(string path)
        {
            var wait = Stopwatch.StartNew();
            while (true)
            {
                try
                {
                    using (var input = new FileStream(Check(path), FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
                    {
                        if (input.Length > 131072) throw new InvalidDataException("Status JSON size exceeded");
                        using (var reader = new StreamReader(input, new UTF8Encoding(false, true))) return reader.ReadToEnd();
                    }
                }
                catch (Exception error) when ((TransientSharing(error) || error is FileNotFoundException) && wait.ElapsedMilliseconds < 80) { Thread.Sleep(2); }
            }
        }

        public static void WriteJson(string path, string text, bool createNew)
        {
            path = Check(path);
            string staged = Path.Combine(Path.GetDirectoryName(path), ".WindowsObserver-" + Guid.NewGuid().ToString("N") + ".json");
            try
            {
                using (var output = new FileStream(staged, FileMode.CreateNew, FileAccess.Write, FileShare.None))
                {
                    byte[] bytes = new UTF8Encoding(false, true).GetBytes(text);
                    output.Write(bytes, 0, bytes.Length);
                    output.Flush(true);
                }
                var wait = Stopwatch.StartNew();
                while (true)
                {
                    try
                    {
                        Check(path);
                        if (!createNew && File.Exists(path)) File.Replace(staged, path, null);
                        else File.Move(staged, path, false);
                        break;
                    }
                    catch (Exception error) when (TransientSharing(error) && wait.ElapsedMilliseconds < 500) { Thread.Sleep(2); }
                }
            }
            finally { if (File.Exists(staged)) File.Delete(staged); }
        }
    }

    public sealed class CapturePump : IDisposable
    {
        private Stream input;
        private readonly FileStream output;
        private readonly long maximum;
        private readonly int snaplen;
        private readonly CancellationTokenSource cancel = new CancellationTokenSource();
        private long bytes;
        private int headerValid;
        private int limit;
        public long Bytes { get { return Interlocked.Read(ref bytes); } }
        public bool HeaderValid { get { return Volatile.Read(ref headerValid) != 0; } }
        public bool LimitReached { get { return Volatile.Read(ref limit) != 0; } }
        public string Fault { get; private set; }
        public Task Completion { get; private set; }

        public CapturePump(string path, long maximum, int snaplen)
        {
            this.maximum = maximum; this.snaplen = snaplen;
            output = new FileStream(SafePaths.Check(path), FileMode.CreateNew, FileAccess.Write, FileShare.Read,
                65536, FileOptions.Asynchronous);
        }

        public void Start(Stream source) { if (Completion != null) throw new InvalidOperationException("Pump already started"); input = source; Completion = Task.Run(Run); }

        private static uint Word(byte[] data, int offset, bool little, int count)
        {
            uint value = 0;
            for (int i = 0; i < count; i++) value |= (uint)data[offset + i] << ((little ? i : count - i - 1) * 8);
            return value;
        }

        private async Task Run()
        {
            byte[] buffer = new byte[65536];
            byte[] header = new byte[24];
            int headerBytes = 0;
            try
            {
                while (Bytes < maximum)
                {
                    int read = await input.ReadAsync(buffer.AsMemory(0, (int)Math.Min(buffer.Length, maximum - Bytes)), cancel.Token).ConfigureAwait(false);
                    if (read == 0) break;
                    await output.WriteAsync(buffer.AsMemory(0, read), cancel.Token).ConfigureAwait(false);
                    await output.FlushAsync(cancel.Token).ConfigureAwait(false);
                    Interlocked.Add(ref bytes, read);
                    if (headerBytes < 24)
                    {
                        int take = Math.Min(24 - headerBytes, read);
                        Buffer.BlockCopy(buffer, 0, header, headerBytes, take); headerBytes += take;
                        if (headerBytes == 24)
                        {
                            bool little = Word(header, 0, true, 4) == 0xa1b2c3d4;
                            bool big = Word(header, 0, false, 4) == 0xa1b2c3d4;
                            if ((!little && !big) || Word(header, 4, little, 2) != 2 || Word(header, 6, little, 2) != 4 ||
                                Word(header, 16, little, 4) != snaplen || Word(header, 20, little, 4) != 249)
                                throw new InvalidDataException("INVALID_PCAP_HEADER");
                            Volatile.Write(ref headerValid, 1);
                        }
                    }
                }
                if (Bytes == maximum) Volatile.Write(ref limit, 1);
            }
            catch (OperationCanceledException) { }
            catch (InvalidDataException) { Fault = "INVALID_PCAP_HEADER"; }
            catch (Exception) { if (!cancel.IsCancellationRequested) Fault = "STDOUT_OR_FILE_FAULT"; }
            finally
            {
                try { output.Flush(true); } catch { Fault = "CAPTURE_FLUSH_FAULT"; }
            }
        }

        public void Dispose()
        {
            cancel.Cancel();
            try { input.Dispose(); } catch { }
            if (Completion != null && !Completion.Wait(2000)) throw new IOException("Pump did not drain");
            output.Flush(true); output.Dispose(); cancel.Dispose();
        }
    }

    // Keep health publication independent of PowerShell pipeline scheduling and packet arrival.
    public sealed class HealthPulse : IDisposable
    {
        private readonly CancellationTokenSource cancel = new CancellationTokenSource();
        private readonly Thread loop;
        private readonly Stopwatch clock = Stopwatch.StartNew();
        private readonly RecorderJob job;
        private readonly CapturePump pump;
        private readonly string path, prefix, layer;
        private readonly int workerPid;
        public const int CadenceTargetMilliseconds = 200;
        public const int FreshnessLimitMilliseconds = 2000;
        private readonly object healthState = new object();
        private readonly Timer freshnessWatchdog;
        private long maximumGap, cadenceMisses, lastPublishedMs;
        private int ready, startupCompleted, fixtureReadyDelay;
        private volatile string fault;
        private string failureType, failureStage;
        private int failureHResult;
        private readonly int fixtureStartupDelay;
        private long firstPublishMs, firstReadMs, startupMs, readyRequestedMs = -1;
        public string Fault { get { return fault; } }
        public long MaxGapMilliseconds { get { return Interlocked.Read(ref maximumGap); } }
        public long CadenceMisses { get { return Interlocked.Read(ref cadenceMisses); } }
        public string FailureType { get { return failureType; } }
        public string FailureStage { get { return failureType == null ? null : failureStage; } }
        public int FailureHResult { get { return failureHResult; } }
        public long FirstPublishMilliseconds { get { return Interlocked.Read(ref firstPublishMs); } }
        public long FirstReadMilliseconds { get { return Interlocked.Read(ref firstReadMs); } }
        public long StartupMilliseconds { get { return Interlocked.Read(ref startupMs); } }
        public long ReadyRequestedMilliseconds { get { return Interlocked.Read(ref readyRequestedMs); } }
        public bool StartupCompleted { get { return Volatile.Read(ref startupCompleted) != 0; } }

        public HealthPulse(string directory, string runId, string sha256, string layer, int workerPid,
            RecorderJob job, CapturePump pump, int fixtureStartupDelay = 0)
        {
            if (fixtureStartupDelay != 0 && (layer != "SYNTHETIC_FIXTURE" || fixtureStartupDelay != 450))
                throw new InvalidDataException("Invalid fixture startup delay");
            this.fixtureStartupDelay = fixtureStartupDelay;
            if (!System.Text.RegularExpressions.Regex.IsMatch(runId, @"^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$") ||
                !System.Text.RegularExpressions.Regex.IsMatch(sha256, @"^[a-fA-F0-9]{64}$"))
                throw new InvalidDataException("Invalid health binding");
            this.job = job; this.pump = pump; this.layer = layer; this.workerPid = workerPid;
            path = Path.Combine(directory, "health.json");
            prefix = "{\"protocol\":\"TBS5530-WINDOWS-OBSERVER/v1\",\"run_id\":\"" + runId +
                "\",\"permit_sha256\":\"" + sha256.ToLowerInvariant() + "\",";
            freshnessWatchdog = new Timer(_ => CheckFreshness(), null, Timeout.Infinite, Timeout.Infinite);
            loop = new Thread(Run) { IsBackground = true, Name = "TBS5530 observer health" };
            loop.Start();
        }

        public void SetReady()
        {
            lock (healthState)
            {
                if (!StartupCompleted || fault != null) throw new InvalidOperationException("Health startup is not complete");
                long stamp = clock.ElapsedMilliseconds;
                Interlocked.Exchange(ref readyRequestedMs, stamp);
                lastPublishedMs = stamp;
                Volatile.Write(ref ready, 1);
                freshnessWatchdog.Change(20, 20);
            }
        }

        private void CheckFreshness()
        {
            bool expired = false;
            lock (healthState)
            {
                if (!cancel.IsCancellationRequested && Volatile.Read(ref ready) != 0 && fault == null &&
                    clock.ElapsedMilliseconds - lastPublishedMs > FreshnessLimitMilliseconds)
                {
                    Interlocked.Exchange(ref maximumGap, Math.Max(MaxGapMilliseconds, clock.ElapsedMilliseconds - lastPublishedMs));
                    Interlocked.Increment(ref cadenceMisses);
                    fault = "HEALTH_DEADLINE_MISSED";
                    expired = true;
                }
            }
            if (expired) { try { job.Stop(); } catch { } }
        }

        public void DelayNextReadyPublishForFixture(int milliseconds = 450)
        {
            if (layer != "SYNTHETIC_FIXTURE" || Volatile.Read(ref ready) == 0 || (milliseconds != 450 && milliseconds != 2100))
                throw new InvalidOperationException("Fixture delay requires ready synthetic observer");
            Volatile.Write(ref fixtureReadyDelay, milliseconds);
        }

        private void Run()
        {
            long previous = -1;
            bool warmed = false;
            try
            {
                while (!cancel.IsCancellationRequested)
                {
                    long stamp = clock.ElapsedMilliseconds;
                    bool armed = StartupCompleted && Volatile.Read(ref ready) != 0;
                    if (!armed) previous = -1;
                    if (armed && previous >= 0)
                    {
                        long gap = stamp - previous;
                        Interlocked.Exchange(ref maximumGap, Math.Max(MaxGapMilliseconds, gap));
                        if (gap > CadenceTargetMilliseconds) Interlocked.Increment(ref cadenceMisses);
                        if (gap > FreshnessLimitMilliseconds) { fault = "HEALTH_DEADLINE_MISSED"; job.Stop(); return; }
                    }
                    failureStage = "PROCESS_QUERY";
                    bool alive = !job.Process.HasExited;
                    bool isReady = armed && fault == null && alive && pump.HeaderValid &&
                        pump.Fault == null && !pump.LimitReached && !job.DeadlineReached && !pump.Completion.IsCompleted;
                    int[] children = job.ProcessIds();
                    string ids = String.Join(",", new[] { workerPid }.Concat(children));
                    string value = prefix + "\"utc\":\"" + DateTimeOffset.UtcNow.ToString("o") +
                        "\",\"state\":\"" + (isReady ? "ready" : "starting") +
                        "\",\"alive\":" + (alive ? "true" : "false") +
                        ",\"ready\":" + (isReady ? "true" : "false") +
                        ",\"capture_bytes\":" + pump.Bytes + ",\"worker_pid\":" + workerPid +
                        ",\"recorder_pid\":" + job.Process.Id + ",\"own_pids\":[" + ids +
                        "],\"reason\":\"" + (isReady ? "CAPTURING" : "WAITING_FOR_HEADER") +
                        "\",\"evidence_layer\":\"" + layer +
                        "\",\"cadence_target_ms\":" + CadenceTargetMilliseconds + ",\"freshness_limit_ms\":" + FreshnessLimitMilliseconds +
                        ",\"cadence_misses\":" + CadenceMisses + ",\"max_health_gap_ms\":" + MaxGapMilliseconds +
                        ",\"health_monotonic_ms\":" + Environment.TickCount64 +
                        ",\"measurement_valid\":false,\"reenumeration_coverage\":false,\"tuner_authorized\":false,\"ts_classification\":\"NOT_EVALUATED\"}";
                    long publishStart = clock.ElapsedMilliseconds;
                    if (!warmed && fixtureStartupDelay != 0 && cancel.Token.WaitHandle.WaitOne(fixtureStartupDelay)) return;
                    if (armed && previous >= 0) { int delay = Interlocked.Exchange(ref fixtureReadyDelay, 0); if (delay != 0 && cancel.Token.WaitHandle.WaitOne(delay)) return; }
                    if (fault != null || cancel.IsCancellationRequested) return;
                    failureStage = "JSON_WRITE";
                    SafePaths.WriteJson(path, value, false);
                    bool expiredAtPublish = false;
                    lock (healthState)
                    {
                        long published = clock.ElapsedMilliseconds;
                        if (armed && published - lastPublishedMs > FreshnessLimitMilliseconds)
                        {
                            Interlocked.Exchange(ref maximumGap, Math.Max(MaxGapMilliseconds, published - lastPublishedMs));
                            fault = "HEALTH_DEADLINE_MISSED";
                            expiredAtPublish = true;
                        }
                        if (fault == null) lastPublishedMs = published;
                    }
                    if (expiredAtPublish || fault != null) { job.Stop(); return; }
                    if (!warmed)
                    {
                        Interlocked.Exchange(ref firstPublishMs, clock.ElapsedMilliseconds - publishStart);
                        long readStart = clock.ElapsedMilliseconds;
                        SafePaths.ReadJson(path);
                        Interlocked.Exchange(ref firstReadMs, clock.ElapsedMilliseconds - readStart);
                        Interlocked.Exchange(ref startupMs, clock.ElapsedMilliseconds);
                        warmed = true;
                        Volatile.Write(ref startupCompleted, 1);
                    }
                    previous = armed ? stamp : -1;
                    if (cancel.Token.WaitHandle.WaitOne(40)) break;
                }
            }
            catch (OperationCanceledException) { }
            catch (Exception error) { failureType = error.GetType().FullName; failureHResult = error.HResult; if (fault == null) fault = "HEALTH_WRITE_FAULT"; try { job.Stop(); } catch { } }
        }

        public void Dispose()
        {
            cancel.Cancel();
            freshnessWatchdog.Change(Timeout.Infinite, Timeout.Infinite);
            if (!loop.Join(2000)) throw new IOException("Health publisher did not drain");
            freshnessWatchdog.Dispose();
            cancel.Dispose();
        }
    }

    // A private job contains only the recorder and its descendants. No process-name killing.
    public sealed class RecorderJob : IDisposable
    {
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern SafeFileHandle CreateJobObject(IntPtr attributes, string name);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool SetInformationJobObject(SafeFileHandle job, int kind, IntPtr info, uint size);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool AssignProcessToJobObject(SafeFileHandle job, IntPtr process);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool QueryInformationJobObject(SafeFileHandle job, int kind, IntPtr info, uint size, out uint returned);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool TerminateJobObject(SafeFileHandle job, uint code);
        [StructLayout(LayoutKind.Sequential)]
        private struct BasicLimits
        {
            public long ProcessTime, JobTime; public uint Flags; public UIntPtr MinSet, MaxSet;
            public uint ActiveLimit; public UIntPtr Affinity; public uint Priority, Scheduling;
        }
        [StructLayout(LayoutKind.Sequential)]
        private struct IoCounters { public ulong A, B, C, D, E, F; }
        [StructLayout(LayoutKind.Sequential)]
        private struct ExtendedLimits
        {
            public BasicLimits Basic; public IoCounters Io; public UIntPtr ProcessMemory, JobMemory, PeakProcessMemory, PeakJobMemory;
        }
        private readonly SafeFileHandle job;
        private readonly Stopwatch clock = Stopwatch.StartNew();
        private readonly Timer watchdog;
        private readonly int maximumMs;
        private int deadline;
        private Process process;
        public bool DeadlineReached { get { return Volatile.Read(ref deadline) != 0; } }
        public Process Process { get { return process; } }

        public RecorderJob(int maximumMs)
        {
            this.maximumMs = maximumMs;
            job = CreateJobObject(IntPtr.Zero, null);
            if (job.IsInvalid) throw new Win32Exception(Marshal.GetLastWin32Error(), "Recorder job creation failed");
            var limits = new ExtendedLimits(); limits.Basic.Flags = 0x2000;
            IntPtr memory = Marshal.AllocHGlobal(Marshal.SizeOf<ExtendedLimits>());
            try
            {
                Marshal.StructureToPtr(limits, memory, false);
                if (!SetInformationJobObject(job, 9, memory, (uint)Marshal.SizeOf<ExtendedLimits>()))
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "Recorder job limits failed");
            }
            catch { job.Dispose(); throw; }
            finally { Marshal.FreeHGlobal(memory); }
            watchdog = new Timer(_ => {
                if (clock.ElapsedMilliseconds >= this.maximumMs)
                {
                    Volatile.Write(ref deadline, 1);
                    try { TerminateJobObject(job, 124); } catch { }
                }
            }, null, 10, 10);
        }

        public Process Start(ProcessStartInfo info)
        {
            process = new Process { StartInfo = info };
            if (!process.Start()) throw new IOException("Recorder process failed to start");
            if (!AssignProcessToJobObject(job, process.Handle))
            {
                int error = Marshal.GetLastWin32Error();
                process.Kill(true); process.WaitForExit(2000);
                throw new Win32Exception(error, "Recorder job assignment failed");
            }
            if (clock.ElapsedMilliseconds >= maximumMs) { Volatile.Write(ref deadline, 1); TerminateJobObject(job, 124); }
            return process;
        }

        public int[] ProcessIds()
        {
            IntPtr memory = Marshal.AllocHGlobal(8192);
            try
            {
                uint returned;
                if (!QueryInformationJobObject(job, 3, memory, 8192, out returned))
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "Recorder job query failed");
                int count = Marshal.ReadInt32(memory, 4);
                if (count < 0 || count > (8192 - 8) / IntPtr.Size) throw new IOException("Recorder job PID bounds");
                var result = new int[count];
                for (int i = 0; i < count; i++) result[i] = checked((int)Marshal.ReadIntPtr(memory, 8 + i * IntPtr.Size).ToInt64());
                return result;
            }
            finally { Marshal.FreeHGlobal(memory); }
        }

        public bool Stop()
        {
            watchdog.Change(Timeout.Infinite, Timeout.Infinite);
            if (!TerminateJobObject(job, 0) && ProcessIds().Length != 0) return false;
            var wait = Stopwatch.StartNew();
            while (ProcessIds().Length != 0 && wait.ElapsedMilliseconds < 2000) Thread.Sleep(10);
            return ProcessIds().Length == 0 && (process == null || process.WaitForExit(1000));
        }

        public void Dispose() { try { Stop(); } finally { watchdog.Dispose(); job.Dispose(); if (process != null) process.Dispose(); } }
    }
}
