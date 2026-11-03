// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import static org.junit.Assert.*;

public class Tbs5530RawDescriptorInspectionTest {
    private static final Tbs5530UsbBinding.Device DEVICE=new Tbs5530UsbBinding.Device("/private/secret",42,0x734c,0x5530);
    private static class Worker implements Executor {
        final Queue<Runnable> jobs=new ArrayDeque<>();
        @Override public void execute(Runnable task) { jobs.add(task); }
        void run() { while(!jobs.isEmpty()) jobs.remove().run(); }
    }
    private static class Port implements Tbs5530RawDescriptorInspection.Port {
        boolean present=true, permission=true, nullOpen, failOpen, failRead, failClose;
        int opens, reads, closes, registrations;
        Runnable onOpen=()->{}, onRead=()->{};
        Tbs5530UsbBinding.Events events;
        @Override public List<Tbs5530UsbBinding.Device> devices() { return present?Collections.singletonList(DEVICE):Collections.emptyList(); }
        @Override public boolean hasPermission(Tbs5530UsbBinding.Device device) { return permission; }
        @Override public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events events) {
            registrations++;this.events=events;return ()->registrations--;
        }
        @Override public Tbs5530RawDescriptorInspection.Connection open(Tbs5530UsbBinding.Device device) throws IOException {
            opens++;onOpen.run();if(failOpen)throw new IOException("secret");if(nullOpen)return null;
            return new Tbs5530RawDescriptorInspection.Connection() {
                @Override public byte[] getRawDescriptors() throws IOException {
                    reads++;onRead.run();if(failRead)throw new IOException("secret");return Tbs5530RawDescriptorsTest.tree();
                }
                @Override public void close() throws IOException { closes++;if(failClose)throw new IOException("secret"); }
            };
        }
    }
    private static class Harness {
        final Worker worker=new Worker();final Port port=new Port();Runnable deadline;
        final Tbs5530RawDescriptorInspection operation=new Tbs5530RawDescriptorInspection(port,worker,
                (task,delay)->{deadline=task;return ()->{};},DEVICE,7);
        void run() { operation.start();worker.run(); }
        void clean(int opens,int reads,int closes) {
            assertEquals(opens,port.opens);assertEquals(reads,port.reads);assertEquals(closes,port.closes);
            assertEquals(0,port.registrations);assertFalse(operation.snapshot().pending);
            assertFalse(operation.snapshot().calls.contains("claimInterface"));
            assertFalse(operation.snapshot().calls.contains("controlTransfer"));
        }
    }
    @Test public void successfulReadHasExactlyOneOwnerAndNoClaimOrTransferCapability() {
        Harness h=new Harness();h.run();h.clean(1,1,1);
        assertEquals("READ_AND_CLOSED",h.operation.snapshot().status);
        assertEquals("COMPLETE_RETURNED_TREE",h.operation.snapshot().descriptors.status);
        assertEquals(7,h.operation.snapshot().generation);
        assertEquals(3,h.operation.snapshot().calls.size());
        try {h.operation.start();fail();}catch(IllegalStateException expected){}
        h.operation.close();h.clean(1,1,1);
    }
    @Test public void deniedPermissionAndMissingDeviceNeverOpen() {
        Harness h=new Harness();h.port.permission=false;h.run();h.clean(0,0,0);
        assertEquals("PERMISSION_DENIED",h.operation.snapshot().failure);
        h=new Harness();h.port.present=false;h.run();h.clean(0,0,0);
        assertEquals("ATTACHMENT_CHANGED",h.operation.snapshot().failure);
    }
    @Test public void queuedStaleGenerationCannotOpenOrPublish() {
        Harness h=new Harness();h.operation.start();h.operation.invalidate("STALE_GENERATION");h.worker.run();h.clean(0,0,0);
        assertNull(h.operation.snapshot().descriptors);
        try {h.operation.start();fail();}catch(IllegalStateException expected){}
    }
    @Test public void disconnectDuringOpenClosesOnceWithoutRead() {
        Harness h=new Harness();h.port.onOpen=()->{h.port.present=false;h.port.events.detached(DEVICE);};
        h.run();h.clean(1,0,1);assertNull(h.operation.snapshot().descriptors);
    }
    @Test public void disconnectDuringReadDefersCloseAndDiscardsData() {
        Harness h=new Harness();h.port.onRead=()->{
            h.port.events.detached(DEVICE);assertTrue(h.operation.snapshot().pending);assertEquals(0,h.port.closes);
        };
        h.run();h.clean(1,1,1);assertNull(h.operation.snapshot().descriptors);
    }
    @Test public void permissionLostAfterOpenDoesNotRead() {
        Harness h=new Harness();h.port.onOpen=()->h.port.permission=false;
        h.run();h.clean(1,0,1);
    }
    @Test public void openNullAndOpenFailureDoNotCloseUnownedConnection() {
        Harness h=new Harness();h.port.nullOpen=true;h.run();h.clean(1,0,0);
        assertEquals("FAIL",h.operation.snapshot().status);
        h=new Harness();h.port.failOpen=true;h.run();h.clean(1,0,0);
    }
    @Test public void readFailureStillClosesExactlyOnceWithoutRetry() {
        Harness h=new Harness();h.port.failRead=true;h.run();h.clean(1,1,1);
        assertEquals("RAW_READ_FAILED",h.operation.snapshot().failure);assertNull(h.operation.snapshot().descriptors);
    }
    @Test public void closeFailureIsNotClaimedAsCleanupSuccess() {
        Harness h=new Harness();h.port.failClose=true;h.run();h.clean(1,1,1);
        assertEquals(0,h.operation.snapshot().closeCompleted);assertEquals("CLOSE_FAILED",h.operation.snapshot().failure);
    }
    @Test public void deadlineAndOwnerCloseDuringReadNeverForceCloseOrPublish() {
        Harness h=new Harness();h.port.onRead=()->{h.deadline.run();h.operation.close();assertEquals(0,h.port.closes);};
        h.run();h.clean(1,1,1);assertNull(h.operation.snapshot().descriptors);
    }
    @Test public void samePathNewAttachmentInvalidatesCurrentCapability() {
        Harness h=new Harness();h.port.onOpen=()->h.port.events.attached(DEVICE);
        h.run();h.clean(1,0,1);
    }
    @Test public void unrelatedDetachDoesNotInvalidateTarget() {
        Harness h=new Harness();h.port.onRead=()->h.port.events.detached(new Tbs5530UsbBinding.Device("other",99,1,2));
        h.run();h.clean(1,1,1);assertNotNull(h.operation.snapshot().descriptors);
    }
}
