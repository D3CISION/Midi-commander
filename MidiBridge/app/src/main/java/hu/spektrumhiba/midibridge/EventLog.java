package hu.spektrumhiba.midibridge;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Bounded, in-memory log. No disk, network, or per-MIDI-event UI updates. */
final class EventLog {
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
    private long version;
    private long midiWindowStart;
    private int midiWindowCount;
    private long dropped;
    synchronized void add(String message) {
        if (lines.size() >= 160) lines.removeFirst();
        lines.addLast(format.format(new Date()) + "  " + message);
        version++;
    }
    synchronized void midi(String message) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - midiWindowStart >= 1000) {
            if (dropped > 0) add("Monitor: " + dropped + " log lines suppressed; MIDI NOT dropped.");
            midiWindowStart = now; midiWindowCount = 0; dropped = 0;
        }
        if (midiWindowCount++ < 40) add(message); else dropped++;
    }
    synchronized String text() { return String.join("\n", lines); }
    synchronized long version() { return version; }
    synchronized void clear() { lines.clear(); dropped = 0; version++; }
}
