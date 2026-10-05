package hu.spektrumhiba.midibridge.core;

import java.util.function.Consumer;

/** Diagnostic parser only. NEVER used to rebuild or forward MIDI messages.
 * Handles fragmented messages, running status, interleaved real-time and bounded SysEx.
 */
public final class MidiMonitor {
    private final Consumer<String> output;
    private int runningStatus, status, expected, used;
    private final int[] data = new int[2];
    private boolean sysex;
    private long sysexSize;
    private final StringBuilder sysexPreview = new StringBuilder();
    private long messages;

    public MidiMonitor(Consumer<String> output) { this.output = output; }
    public void reset() {
        runningStatus = status = expected = used = 0;
        sysex = false; sysexSize = messages = 0; sysexPreview.setLength(0);
    }
    public long getMessageCount() { return messages; }
    public void accept(byte[] bytes, int offset, int count) {
        if (offset < 0 || count < 0 || offset > bytes.length - count)
            throw new IndexOutOfBoundsException();
        for (int i = offset; i < offset + count; i++) accept(bytes[i] & 255);
    }
    private void accept(int b) {
        // Real-time bytes may appear ANYWHERE; they do not alter parser state.
        if (b >= 0xF8) {
            messages++;
            // Clock and active sensing are counted, not logged: avoid UI flooding.
            if (b != 0xF8 && b != 0xFE)
                output.accept("Realtime " + hex(b));
            return;
        }
        if (sysex) {
            if (b == 0xF7) {
                sysexSize++;
                output.accept("SysEx " + sysexSize + " bytes: " + sysexPreview + " ... F7");
                messages++; sysex = false; status = used = expected = 0;
                return;
            }
            if (b < 0x80) {
                sysexSize++;
                if (sysexSize <= 16) sysexPreview.append(' ').append(hex(b));
                return;
            }
            output.accept("SysEx interrupted after " + sysexSize + " bytes");
            sysex = false;
        }
        if (b >= 0x80) {
            used = 0; status = b;
            if (b < 0xF0) {
                runningStatus = b;
                expected = ((b & 0xF0) == 0xC0 || (b & 0xF0) == 0xD0) ? 1 : 2;
            } else {
                runningStatus = 0;
                if (b == 0xF0) {
                    sysex = true; sysexSize = 1; sysexPreview.setLength(0);
                    sysexPreview.append("F0"); expected = 0;
                } else if (b == 0xF1 || b == 0xF3) expected = 1;
                else if (b == 0xF2) expected = 2;
                else { expected = 0; complete(); }
            }
            return;
        }
        if (status == 0) {
            if (runningStatus == 0) return;
            status = runningStatus; used = 0;
            expected = ((status & 0xF0) == 0xC0 || (status & 0xF0) == 0xD0) ? 1 : 2;
        }
        if (expected == 0) return;
        data[used++] = b;
        if (used == expected) complete();
    }
    private void complete() {
        int kind = status & 0xF0, ch = (status & 15) + 1;
        String label;
        switch (kind) {
            case 0x80: label = "Note Off ch=" + ch + " note=" + data[0] + " vel=" + data[1]; break;
            case 0x90: label = "Note On ch=" + ch + " note=" + data[0] + " vel=" + data[1]; break;
            case 0xA0: label = "Poly Pressure ch=" + ch + " note=" + data[0] + " value=" + data[1]; break;
            case 0xB0: label = "CC ch=" + ch + " cc=" + data[0] + " value=" + data[1]; break;
            case 0xC0: label = "PC ch=" + ch + " program=" + data[0] + " (0-based)"; break;
            case 0xD0: label = "Channel Pressure ch=" + ch + " value=" + data[0]; break;
            case 0xE0: label = "Pitch Bend ch=" + ch + " value=" + (data[0] | data[1] << 7); break;
            default: label = "System " + hex(status);
        }
        StringBuilder raw = new StringBuilder(hex(status));
        for (int i = 0; i < expected; i++) raw.append(' ').append(hex(data[i]));
        output.accept(label + "  [" + raw + "]");
        messages++; status = used = expected = 0;
    }
    private static String hex(int b) {
        final char[] h = "0123456789ABCDEF".toCharArray();
        return "" + h[(b >> 4) & 15] + h[b & 15];
    }
}
