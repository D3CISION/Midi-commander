package hu.spektrumhiba.midibridge;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Foreground-only BLE discovery. Open devices through MidiManager, not a custom BLE codec. */
final class BleScanner {
    static final ParcelUuid MIDI_SERVICE = ParcelUuid.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700");
    static final class Peer {
        final BluetoothDevice device;
        final String name, address;
        final boolean midiAdvertised, likelyMidi;
        final int rssi;
        Peer(BluetoothDevice device, String name, String address, boolean midiAdvertised, int rssi) {
            this.device = device; this.name = name; this.address = address;
            this.midiAdvertised = midiAdvertised; this.rssi = rssi;
            String n = name.toLowerCase(Locale.ROOT);
            likelyMidi = midiAdvertised || n.contains("chocolate") || n.contains("footctrl")
                    || n.contains("m-vave") || n.contains("mvave") || n.contains("cuvave");
        }
        String label() {
            return name + "\n" + address + "  /  " + rssi + " dBm  /  "
                    + (midiAdvertised ? "BLE MIDI" : likelyMidi ? "MIDI-jel\u00f6lt" : "ismeretlen BLE");
        }
    }
    private final BluetoothAdapter adapter;
    private final Handler main;
    private final EventLog log;
    private final Runnable changed;
    private final LinkedHashMap<String, Peer> peers = new LinkedHashMap<>();
    private BluetoothLeScanner scanner;
    private ScanCallback callback;
    private Runnable timeout;
    private boolean scanning;
    private long generation;

    BleScanner(Context context, Handler main, EventLog log, Runnable changed) {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        this.main = main; this.log = log; this.changed = changed;
    }
    BluetoothAdapter adapter() { return adapter; }
    boolean isScanning() { return scanning; }
    List<Peer> peers(boolean showAll) {
        ArrayList<Peer> list = new ArrayList<>();
        for (Peer p : peers.values()) if (showAll || p.likelyMidi) list.add(p);
        list.sort(Comparator.comparing((Peer p) -> !p.likelyMidi)
                .thenComparing((Peer p) -> -p.rssi));
        return list;
    }
    @SuppressLint("MissingPermission") // Activity requests runtime permission; catch revocation below.
    void start() {
        if (scanning) return;
        try {
            if (adapter == null || !adapter.isEnabled()) {
                log.add("A Bluetooth nincs bekapcsolva vagy nem el\u00e9rhet\u0151."); changed.run(); return;
            }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { log.add("BLE keres\u0151 nem el\u00e9rhet\u0151."); changed.run(); return; }
            peers.clear();
            long token = ++generation;
            callback = new ScanCallback() {
                @Override public void onScanResult(int type, ScanResult result) {
                    main.post(() -> { if (scanning && token == generation) add(result); });
                }
                @Override public void onBatchScanResults(List<ScanResult> results) {
                    main.post(() -> {
                        if (scanning && token == generation) for (ScanResult r : results) add(r);
                    });
                }
                @Override public void onScanFailed(int code) {
                    main.post(() -> {
                        if (token != generation) return;
                        stop(); log.add("Bluetooth scan hiba: " + code
                                + ". T\u00fal s\u0171r\u0171 keres\u00e9sn\u00e9l v\u00e1rj 30 m\u00e1sodpercet."); changed.run();
                    });
                }
            };
            scanning = true;
            // No UUID filter: some Chocolate firmware versions may omit it in advertisements.
            // Unknown devices remain available behind the explicit 'all BLE' UI switch.
            scanner.startScan(null, new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build(), callback);
            timeout = () -> {
                if (token != generation) return;
                stop(); log.add("Bluetooth keres\u00e9s v\u00e9ge. Tal\u00e1lat: " + peers.size()); changed.run();
            };
            main.postDelayed(timeout, 12_000);
            log.add("Bluetooth scan: 12 m\u00e1sodperc."); changed.run();
        } catch (RuntimeException e) {
            stop(); log.add("Bluetooth keres\u00e9si hiba: " + e.getMessage()); changed.run();
        }
    }
    @SuppressLint("MissingPermission")
    private void add(ScanResult result) {
        try {
            BluetoothDevice d = result.getDevice();
            String address = d.getAddress();
            if (!peers.containsKey(address) && peers.size() >= 80) return;
            ScanRecord record = result.getScanRecord();
            String name = record == null ? null : record.getDeviceName();
            if (name == null || name.isEmpty()) name = d.getName();
            if (name == null || name.isEmpty()) name = "N\u00e9vtelen BLE eszk\u00f6z";
            boolean midi = record != null && record.getServiceUuids() != null
                    && record.getServiceUuids().contains(MIDI_SERVICE);
            Peer old = peers.get(address);
            if (old != null && old.midiAdvertised) midi = true;
            peers.put(address, new Peer(d, name, address, midi, result.getRssi()));
            changed.run();
        } catch (SecurityException e) {
            stop(); log.add("A Bluetooth enged\u00e9ly visszavonva."); changed.run();
        }
    }
    @SuppressLint("MissingPermission")
    void stop() {
        generation++;
        if (timeout != null) main.removeCallbacks(timeout);
        timeout = null;
        if (scanner != null && callback != null) {
            try { scanner.stopScan(callback); } catch (RuntimeException ignored) { }
        }
        scanning = false; scanner = null; callback = null;
        changed.run();
    }
}
