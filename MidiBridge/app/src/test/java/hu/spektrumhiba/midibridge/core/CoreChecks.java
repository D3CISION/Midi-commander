package hu.spektrumhiba.midibridge.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real JVM tests; require only a JDK. Android/Bluetooth/USB behavior is NOT simulated here. */
public final class CoreChecks {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) b[i] = (byte) values[i];
        return b;
    }
    static void feed(MidiMonitor monitor, int... values) {
        byte[] b = bytes(values); monitor.accept(b, 0, b.length);
    }
    public static void transparentSliceAndTimestamp() throws Exception {
        MidiPipe pipe = new MidiPipe();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long[] timestamp = {0};
        pipe.attach((b, o, c, t) -> { out.write(b, o, c); timestamp[0] = t; });
        check(pipe.forward(bytes(0x55, 0xC0, 0x07, 0x66), 1, 2, 987654321L), "not forwarded");
        check(Arrays.equals(out.toByteArray(), bytes(0xC0, 7)), "offset/count changed");
        check(timestamp[0] == 987654321L, "timestamp changed");
        check(Arrays.equals(pipe.counters(), new long[] {2, 2, 1}), "bad counters");
    }
    public static void allBytesAcrossRandomChunks() throws Exception {
        byte[] payload = new byte[131072]; new Random(811).nextBytes(payload);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MidiPipe pipe = new MidiPipe(); pipe.attach((b, o, c, t) -> out.write(b, o, c));
        Random random = new Random(27); int offset = 0;
        while (offset < payload.length) {
            int size = Math.min(payload.length - offset, random.nextInt(2048) + 1);
            pipe.forward(payload, offset, size, offset); offset += size;
        }
        check(Arrays.equals(payload, out.toByteArray()), "byte stream corrupted");
    }
    public static void detachedAndEmptySend() throws Exception {
        MidiPipe pipe = new MidiPipe();
        check(!pipe.forward(bytes(0xC0, 1), 0, 2, 0), "detached pipe sent");
        int[] calls = {0}; pipe.attach((b, o, c, t) -> calls[0]++);
        check(!pipe.forward(new byte[0], 0, 0, 0), "empty chunk sent");
        pipe.detach(); pipe.forward(bytes(0xC0, 1), 0, 2, 0);
        check(calls[0] == 0, "detached pipe invoked sink");
    }
    public static void invalidRange() throws Exception {
        MidiPipe pipe = new MidiPipe(); pipe.attach((b, o, c, t) -> {});
        boolean failed = false;
        try { pipe.forward(new byte[2], 1, 2, 0); } catch (IndexOutOfBoundsException e) { failed = true; }
        check(failed, "invalid range accepted");
    }
    public static void failedSendNotCountedAsDelivered() throws Exception {
        MidiPipe pipe = new MidiPipe(); pipe.attach((b, o, c, t) -> { throw new IOException("disconnect"); });
        boolean failed = false;
        try { pipe.forward(bytes(0xC0, 9), 0, 2, 0); } catch (IOException e) { failed = true; }
        check(failed, "failure hidden");
        check(Arrays.equals(pipe.counters(), new long[] {2, 0, 1}), "failure counted as delivered");
    }
    public static void detachWaitsForInFlightSend() throws Exception {
        MidiPipe pipe = new MidiPipe();
        CountDownLatch sending = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch detached = new CountDownLatch(1), attempting = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        pipe.attach((b, o, c, t) -> {
            sending.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        });
        Thread writer = new Thread(() -> {
            try { pipe.forward(bytes(0xC0, 1), 0, 2, 0); } catch (Throwable e) { failure.set(e); }
        });
        Thread closer = new Thread(() -> { attempting.countDown(); pipe.detach(); detached.countDown(); });
        writer.start(); check(sending.await(3, TimeUnit.SECONDS), "writer never started");
        closer.start(); check(attempting.await(3, TimeUnit.SECONDS), "closer never started");
        check(!detached.await(80, TimeUnit.MILLISECONDS), "detach raced with active write");
        release.countDown(); writer.join(3000); closer.join(3000);
        check(!writer.isAlive() && !closer.isAlive(), "thread did not finish");
        check(failure.get() == null && detached.getCount() == 0, "concurrency failure");
        check(!pipe.forward(bytes(0xC0, 2), 0, 2, 0), "sent after detach");
    }
    public static void fragmentedProgramChange() {
        List<String> lines = new ArrayList<>(); MidiMonitor m = new MidiMonitor(lines::add);
        feed(m, 0xC3); check(lines.isEmpty(), "incomplete PC emitted");
        feed(m, 12); check(lines.size() == 1 && lines.get(0).contains("PC ch=4 program=12"), "bad PC decode");
    }
    public static void runningStatusAndRealtime() {
        List<String> lines = new ArrayList<>(); MidiMonitor m = new MidiMonitor(lines::add);
        feed(m, 0xB0, 7, 0xF8); feed(m, 100, 10); feed(m, 0xFE, 20);
        check(lines.size() == 2, "clock or active sensing flooded log");
        check(lines.get(0).contains("cc=7 value=100"), "CC fragment failed");
        check(lines.get(1).contains("cc=10 value=20"), "running status failed");
        check(m.getMessageCount() == 4, "wrong MIDI message count");
    }
    public static void fragmentedSysexWithRealtime() {
        List<String> lines = new ArrayList<>(); MidiMonitor m = new MidiMonitor(lines::add);
        feed(m, 0xF0, 0x7D, 0x01); feed(m, 0xF8, 0x02, 0xF7);
        check(lines.size() == 1 && lines.get(0).contains("SysEx 5 bytes"), "SysEx counted transport incorrectly");
        check(m.getMessageCount() == 2, "SysEx/clock count wrong");
    }
    public static void sysexMemoryBounded() {
        List<String> lines = new ArrayList<>(); MidiMonitor m = new MidiMonitor(lines::add);
        feed(m, 0xF0); byte[] body = new byte[1_000_000]; m.accept(body, 0, body.length); feed(m, 0xF7);
        check(lines.size() == 1 && lines.get(0).length() < 150, "unbounded SysEx preview");
        check(lines.get(0).contains("1000002 bytes"), "SysEx length incorrect");
    }
    public static void systemCommonCancelsRunningStatus() {
        List<String> lines = new ArrayList<>(); MidiMonitor m = new MidiMonitor(lines::add);
        feed(m, 0xC0, 1, 0xF1, 2, 3); // trailing 3 cannot inherit PC status after F1
        check(lines.size() == 2, "system common did not clear running status");
    }
    public static void monitorResetClearsPartialMessages() {
        List<String> lines = new ArrayList<>(); MidiMonitor m = new MidiMonitor(lines::add);
        feed(m, 0xB0, 7); m.reset(); feed(m, 100);
        check(lines.isEmpty(), "partial message leaked across reset");
        feed(m, 0xC0, 3); check(lines.size() == 1, "parser broken after reset");
    }
    public static void multipleMessagesInOneChunk() {
        List<String> lines = new ArrayList<>(); MidiMonitor m = new MidiMonitor(lines::add);
        feed(m, 0xC0, 1, 2, 3, 0x90, 60, 127, 0x80, 60, 0);
        check(lines.size() == 5, "coalesced messages not decoded");
    }
    public static void interruptedSysexResynchronizes() {
        List<String> lines = new ArrayList<>(); MidiMonitor m = new MidiMonitor(lines::add);
        feed(m, 0xF0, 1, 2, 0xC0, 4);
        check(lines.size() == 2 && lines.get(1).contains("program=4"), "SysEx resync failed");
    }
    public static void main(String[] args) throws Exception {
        String[] names = { "transparentSliceAndTimestamp", "allBytesAcrossRandomChunks", "detachedAndEmptySend",
                "invalidRange", "failedSendNotCountedAsDelivered", "detachWaitsForInFlightSend",
                "fragmentedProgramChange", "runningStatusAndRealtime", "fragmentedSysexWithRealtime",
                "sysexMemoryBounded", "systemCommonCancelsRunningStatus", "monitorResetClearsPartialMessages",
                "multipleMessagesInOneChunk", "interruptedSysexResynchronizes" };
        for (String name : names) { CoreChecks.class.getMethod(name).invoke(null); System.out.println("PASS " + name); }
        System.out.println("PASS " + names.length + "/" + names.length + " core JVM checks. Android/hardware NOT tested.");
    }
}
