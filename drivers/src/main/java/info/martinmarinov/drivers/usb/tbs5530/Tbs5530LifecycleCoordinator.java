// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JVM-only lifecycle ports. Opening and claiming do not establish device readiness.
 * Use one coordinator per session. A blocked acquisition finishes on its callback thread;
 * cancellation invalidates the session but cannot forcibly interrupt an open/claim port.
 */
public final class Tbs5530LifecycleCoordinator implements AutoCloseable {
    /** The attachment id must change on reattachment, even when the OS reuses its path/id. */
    public static final class AttachmentIdentity {
        private final String devicePath;
        private final int deviceId;
        private final int vendorId;
        private final int productId;
        private final long attachmentId;

        public AttachmentIdentity(String devicePath, int deviceId, int vendorId,
                                  int productId, long attachmentId) {
            if (devicePath == null || devicePath.isEmpty() || deviceId < 0
                    || vendorId < 0 || vendorId > 0xffff || productId < 0 || productId > 0xffff
                    || attachmentId < 0) {
                throw new IllegalArgumentException("Invalid attachment identity");
            }
            this.devicePath = devicePath;
            this.deviceId = deviceId;
            this.vendorId = vendorId;
            this.productId = productId;
            this.attachmentId = attachmentId;
        }

        public String getDevicePath() { return devicePath; }
        public int getDeviceId() { return deviceId; }
        public int getVendorId() { return vendorId; }
        public int getProductId() { return productId; }
        public long getAttachmentId() { return attachmentId; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof AttachmentIdentity)) return false;
            AttachmentIdentity that = (AttachmentIdentity) other;
            return deviceId == that.deviceId && vendorId == that.vendorId
                    && productId == that.productId && attachmentId == that.attachmentId
                    && devicePath.equals(that.devicePath);
        }

        @Override public int hashCode() {
            return Objects.hash(devicePath, deviceId, vendorId, productId, attachmentId);
        }
    }

    public static final class Request {
        private final long token;
        private final AttachmentIdentity identity;
        private boolean pending = true;

        private Request(long token, AttachmentIdentity identity) {
            this.token = token;
            this.identity = identity;
        }

        public long getToken() { return token; }
        public AttachmentIdentity getIdentity() { return identity; }
    }

    public interface PermissionPort {
        void request(Request request) throws IOException;
    }

    public interface ConnectionPort {
        Connection open(AttachmentIdentity identity) throws IOException;
    }

    public interface Connection extends Tbs5530Session.Resources {
        /** Mark ownership immediately after a successful claim, including before later failures. */
        void claim(Claim claim) throws IOException;
        default Tbs5530Descriptors.Endpoint streamEndpoint() throws IOException {
            throw new IOException("Stream endpoint metadata is unavailable");
        }
        default byte[] rawDescriptors() throws IOException {
            throw new IOException("Raw descriptor observation is unavailable");
        }
        default int bulkTransfer(byte[] buffer, int length, int timeoutMillis) throws IOException {
            throw new IOException("Bulk transfer is unavailable");
        }
        default int lastBulkErrno() throws IOException { return -1; }
        default void prepareBulkIn82() throws IOException { throw new IOException("Endpoint preparation unavailable"); }
        default void requireControlWorker() throws IOException { }
        default int controlTransfer(int requestType, int request, int value, int index,
                                    byte[] buffer, int offset, int length, int timeoutMillis) throws IOException {
            throw new IOException("Control transfer is not implemented by this connection");
        }
    }

    /** A synchronous ownership receipt; a port must not retain it after claim returns. */
    public static final class Claim {
        private final AtomicBoolean claimed = new AtomicBoolean();
        private Claim() { }
        public void acquired() { claimed.set(true); }
    }

    private final Tbs5530Session session;
    private final PermissionPort permissions;
    private final ConnectionPort connections;
    // These fields and Request.pending use the session's monitor for pure state changes only.
    private Request current;
    private int acquiring;
    private Lease owned;

    public Tbs5530LifecycleCoordinator(Tbs5530Session session, PermissionPort permissions,
                                      ConnectionPort connections) {
        this.session = Objects.requireNonNull(session, "session");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public Request requestPermission(AttachmentIdentity identity) throws IOException {
        Objects.requireNonNull(identity, "identity");
        Request request;
        synchronized (session) {
            if (acquiring != 0) throw new IllegalStateException("Acquisition cleanup is pending");
            request = new Request(session.requestPermission(), identity);
            current = request;
        }
        try {
            permissions.request(request);
        } catch (IOException | RuntimeException e) {
            IOException failure = asIo("Permission request failed", e);
            synchronized (session) {
                if (matches(request) && request.pending) {
                    request.pending = false;
                    session.failed(request.token, failure);
                }
            }
            throw failure;
        }
        return request;
    }

    /** Returns whether this callback was consumed, not whether the device became ready. */
    public boolean permissionResult(Request request, AttachmentIdentity identity, boolean granted)
            throws IOException {
        return permissionResult(request, identity, granted, true);
    }

    public boolean permissionResult(Request request, AttachmentIdentity identity, boolean granted, boolean acquire)
            throws IOException {
        return permissionResult(request, identity, granted, acquire, true);
    }

    boolean permissionResult(Request request, AttachmentIdentity identity, boolean granted,
                             boolean acquire, boolean claimInterface) throws IOException {
        synchronized (session) {
            if (!matches(request) || !request.identity.equals(identity) || !request.pending
                    || session.getState() != Tbs5530Session.State.WAIT_PERMISSION) return false;
            request.pending = false;
            if (!session.permissionResult(request.token, granted)) return false;
            if (!granted || !acquire) return true;
            acquiring++;
        }
        acquire(request, claimInterface);
        return true;
    }

    private void acquire(Request request, boolean claimInterface) throws IOException {
        Lease local = null;
        Throwable failure = null;
        try {
            synchronized (session) {
                if (!canConnect(request)) return;
            }
            Connection connection = connections.open(request.identity);
            if (connection == null) throw new IOException("Connection port returned no connection");
            local = new Lease(connection, !claimInterface);
            synchronized (session) {
                if (!canConnect(request)) return;
            }
            if (claimInterface) {
                connection.claim(local.claim);
                if (!local.claim.claimed.get()) throw new IOException("Claim did not confirm ownership");
            }
            synchronized (session) {
                if (canConnect(request) && session.connected(request.token, local)) {
                    owned = local;
                    local = null;
                    if (claimInterface) session.interfaceClaimed(request.token);
                }
            }
        } catch (IOException | RuntimeException e) {
            failure = asIo("Connection acquisition failed", e);
            synchronized (session) {
                if (matches(request)) session.failed(request.token, failure);
            }
        } catch (Error e) {
            failure = e;
            synchronized (session) {
                if (matches(request)) session.failed(request.token, failure);
            }
        } finally {
            try {
                if (local != null) failure = local.dispose(failure);
            } finally {
                synchronized (session) { acquiring--; }
            }
            if (failure instanceof IOException) throw (IOException) failure;
            if (failure instanceof Error) throw (Error) failure;
        }
    }

    public Tbs5530ControlIo controlIo(Request request) throws IOException {
        synchronized (session) {
            if (!matches(request) || owned == null) {
                throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.SESSION_UNAVAILABLE,
                        "No managed connection for request");
            }
            session.requireOwned(request.token, owned);
            return new Tbs5530ControlTransport(session, request.token, owned);
        }
    }

    Tbs5530ControlIo bootstrapControlIo(Request request) throws IOException {
        synchronized (session) {
            if (!matches(request) || owned == null || !owned.bootstrap) {
                throw new IOException("No bootstrap connection for request");
            }
            session.requireConnectionOwned(request.token, owned);
            return new Tbs5530ControlTransport(session, request.token, owned, false);
        }
    }

    byte[] bootstrapRawDescriptors(Request request) throws IOException {
        final Lease lease;
        synchronized (session) {
            if (!matches(request) || owned == null || !owned.bootstrap) throw new IOException("No bootstrap owner");
            lease = owned;
        }
        lease.requireControlWorker();
        final byte[][] result = new byte[1][];
        session.transaction(request.token, () -> {
            session.requireConnectionOwned(request.token, lease);
            try {
                result[0] = lease.connection.rawDescriptors();
                session.requireConnectionOwned(request.token, lease);
            } catch (IOException | RuntimeException failure) {
                session.failed(request.token, failure);
                throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.IO_FAILURE,
                        "Descriptor observation failed", failure);
            }
        });
        return result[0];
    }

    public Tbs5530Descriptors.Endpoint streamEndpoint(Request request) throws IOException {
        Lease lease;
        synchronized (session) {
            if (!matches(request) || owned == null) throw new IOException("No current claimed connection");
            lease = owned;
            session.requireOwned(request.token, lease);
        }
        Tbs5530Descriptors.Endpoint metadata = lease.connection.streamEndpoint();
        synchronized (session) { session.requireOwned(request.token, lease); }
        return metadata;
    }

    public Tbs5530BulkTransport bulkIo(Request request) throws IOException {
        synchronized (session) {
            if (!matches(request) || owned == null) throw new IOException("No current bulk owner");
            session.requireOwned(request.token, owned);
            Lease lease = owned;
            return new Tbs5530BulkTransport(session, request.token, lease, lease.connection::requireControlWorker,
                    lease.connection::bulkTransfer, lease.connection::prepareBulkIn82, lease.connection::lastBulkErrno);
        }
    }

    public boolean permissionTimedOut(Request request) throws IOException {
        synchronized (session) {
            if (!matches(request) || !request.pending
                    || session.getState() != Tbs5530Session.State.WAIT_PERMISSION) return false;
            request.pending = false;
        }
        return session.cancel(request.token);
    }

    /** Invalidates without invoking resource callbacks; drainCleanup belongs on the owner's worker. */
    public boolean invalidate(Request request, boolean detached) {
        synchronized (session) {
            if (!matches(request)) return false;
            request.pending = false;
            return session.requestCancellation(request.token, detached);
        }
    }

    public boolean isAcquiring() { synchronized (session) { return acquiring != 0; } }

    public boolean cancel(Request request) throws IOException {
        synchronized (session) {
            if (!matches(request)) return false;
            request.pending = false;
        }
        return session.cancel(request.token);
    }

    public boolean detached(Request request, AttachmentIdentity identity) throws IOException {
        synchronized (session) {
            if (!matches(request) || !request.identity.equals(identity)) return false;
            request.pending = false;
        }
        return session.detached(request.token);
    }

    @Override public void close() throws IOException {
        synchronized (session) {
            if (current != null) current.pending = false;
        }
        session.close();
    }

    private boolean matches(Request request) {
        return request != null && current == request && session.getGeneration() == request.token;
    }

    private boolean canConnect(Request request) {
        return matches(request) && session.getState() == Tbs5530Session.State.PERMISSION_GRANTED;
    }

    private static IOException asIo(String message, Exception failure) {
        return failure instanceof IOException ? (IOException) failure : new IOException(message, failure);
    }

    private static final class Lease implements Tbs5530Session.Resources, Tbs5530ControlTransport.Connection {
        @Override public void requireControlWorker() throws IOException { connection.requireControlWorker(); }
        @Override public int controlTransfer(int requestType, int request, int value, int index,
                                             byte[] buffer, int offset, int length, int timeoutMillis) throws IOException {
            if (closed.get() || !bootstrap && !claim.claimed.get()) throw new IOException("Managed connection is closed or unclaimed");
            return connection.controlTransfer(requestType, request, value, index, buffer, offset, length, timeoutMillis);
        }
        final Connection connection;
        final Claim claim = new Claim();
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicBoolean cancelled = new AtomicBoolean();

        final boolean bootstrap;

        Lease(Connection connection, boolean bootstrap) {
            this.connection = connection;
            this.bootstrap = bootstrap;
        }

        @Override public void cancel() throws IOException {
            if (cancelled.compareAndSet(false, true)) connection.cancel();
        }

        @Override public void release() throws IOException {
            if (claim.claimed.compareAndSet(true, false)) connection.release();
        }

        @Override public void close() throws IOException {
            if (closed.compareAndSet(false, true)) connection.close();
        }

        Throwable dispose(Throwable failure) {
            try { release(); } catch (IOException | RuntimeException e) {
                failure = append(failure, e);
            }
            try { close(); } catch (IOException | RuntimeException e) {
                failure = append(failure, e);
            }
            return failure;
        }

        private static Throwable append(Throwable first, Exception next) {
            IOException failure = asIo("Resource cleanup failed", next);
            if (first == null) return failure;
            if (first != failure) first.addSuppressed(failure);
            return first;
        }
    }
}
