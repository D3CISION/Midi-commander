package hu.spektrumhiba.midibridge.core;
import org.junit.Test;
public final class MidiCoreTest {
    @Test public void transparency() throws Exception { CoreChecks.transparentSliceAndTimestamp(); }
    @Test public void randomizedStream() throws Exception { CoreChecks.allBytesAcrossRandomChunks(); }
    @Test public void detachAndEmpty() throws Exception { CoreChecks.detachedAndEmptySend(); }
    @Test public void bounds() throws Exception { CoreChecks.invalidRange(); }
    @Test public void writeFailure() throws Exception { CoreChecks.failedSendNotCountedAsDelivered(); }
    @Test public void closeRace() throws Exception { CoreChecks.detachWaitsForInFlightSend(); }
    @Test public void fragmentedPc() { CoreChecks.fragmentedProgramChange(); }
    @Test public void runningStatus() { CoreChecks.runningStatusAndRealtime(); }
    @Test public void sysexAndClock() { CoreChecks.fragmentedSysexWithRealtime(); }
    @Test public void boundedSysex() { CoreChecks.sysexMemoryBounded(); }
    @Test public void systemCommon() { CoreChecks.systemCommonCancelsRunningStatus(); }
    @Test public void reset() { CoreChecks.monitorResetClearsPartialMessages(); }
    @Test public void coalescedMessages() { CoreChecks.multipleMessagesInOneChunk(); }
    @Test public void sysexAbort() { CoreChecks.interruptedSysexResynchronizes(); }
}
