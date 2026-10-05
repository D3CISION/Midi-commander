package hu.spektrumhiba.midibridge.core;

import java.io.IOException;
import java.util.Objects;

/** Serial, transparent MIDI byte-stream forwarding. No framing, conversion or filtering. */
public final class MidiPipe {
    @FunctionalInterface public interface Sink {
        void send(byte[] bytes, int offset, int count, long timestamp) throws IOException;
    }
    private Sink sink;
    private long receivedBytes;
    private long forwardedBytes;
    private long receivedChunks;

    public synchronized void attach(Sink destination) {
        sink = Objects.requireNonNull(destination);
        receivedBytes = forwardedBytes = receivedChunks = 0;
    }
    /** Waits for an in-flight send before disconnecting: no send-after-close race. */
    public synchronized void detach() { sink = null; }
    public synchronized boolean forward(byte[] data, int offset, int count, long timestamp)
            throws IOException {
        Objects.requireNonNull(data);
        if (offset < 0 || count < 0 || offset > data.length - count)
            throw new IndexOutOfBoundsException("Invalid MIDI byte range");
        if (sink == null || count == 0) return false;
        receivedBytes += count;
        receivedChunks++;
        sink.send(data, offset, count, timestamp);
        forwardedBytes += count;
        return true;
    }
    public synchronized long[] counters() {
        return new long[] { receivedBytes, forwardedBytes, receivedChunks };
    }
}
