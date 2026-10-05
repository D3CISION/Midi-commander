package hu.spektrumhiba.midibridge;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import hu.spektrumhiba.midibridge.core.MidiMonitor;
import hu.spektrumhiba.midibridge.core.MidiPipe;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** All lifecycle operations occur on main; MIDI bytes bypass main and UI entirely. */
final class MidiEngine {
    final EventLog log = new EventLog();
    final BleScanner scanner;
    final Handler main = new Handler(Looper.getMainLooper());
    final boolean midiSupported;
    private final Context context;
    private final MidiManager manager;
    private final Runnable changed;
    private final ArrayList<Endpoint> inputs = new ArrayList<>(), outputs = new ArrayList<>();
    private boolean destroyed, callbackRegistered, receiverRegistered;
    private MidiDevice bluetoothAnchor;
    private String bluetoothName, bluetoothAddress;
    private int bleGeneration, routeGeneration, testGeneration;
    private Runnable bleTimeout, routeTimeout, testTimeout;
    private MidiDevice sourceDevice, destinationDevice, testDevice;
    private MidiOutputPort sourcePort;
    private MidiInputPort destinationPort, testPort;
    private MidiReceiver receiver;
    private MidiPipe currentPipe = new MidiPipe();
    private Endpoint routeInput, routeOutput;
    boolean running, opening, testing, bleConnecting;
    volatile boolean monitorEnabled = true;
    String routeState = "V\u00e1lassz bemenetet \u00e9s kimenetet.";
    String bluetoothState = "Nincs csatlakoztatott Bluetooth MIDI eszk\u00f6z.";
    String usbSummary = "";

    MidiEngine(Context context, Runnable changed) {
        this.context = context;
        this.changed = changed;
        manager = context.getSystemService(MidiManager.class);
        midiSupported = manager != null && context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_MIDI);
        scanner = new BleScanner(context, main, log, changed);
        if (midiSupported) {
            refresh();
            try {
                if (Build.VERSION.SDK_INT >= 33) manager.registerDeviceCallback(
                        MidiManager.TRANSPORT_MIDI_BYTE_STREAM, main::post, deviceCallback);
                else manager.registerDeviceCallback(deviceCallback, main);
                callbackRegistered = true;
            } catch (RuntimeException e) { report("MIDI callback hiba: " + e.getMessage()); }
        } else report("Ez az Android rendszer nem jelzi a MIDI API t\u00e1mogat\u00e1s\u00e1t.");
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33)
            context.registerReceiver(systemReceiver, filter, Context.RECEIVER_EXPORTED);
        else context.registerReceiver(systemReceiver, filter);
        receiverRegistered = true;
        refresh();
        report("MIDI Bridge 0.1.0. MIDI 1.0 byte-stream; nincs hang\u00e1tvitel.");
    }

    List<Endpoint> inputs() { return new ArrayList<>(inputs); }
    List<Endpoint> outputs() { return new ArrayList<>(outputs); }
    long[] counters() { return currentPipe.counters(); }
    boolean hasBluetooth() { return bluetoothAnchor != null; }
    boolean routeBusy() { return running || opening; }
    boolean busy() { return routeBusy() || testing; }
    String activeInputKey() { return routeInput == null ? null : routeInput.key; }
    String activeOutputKey() { return routeOutput == null ? null : routeOutput.key; }
    void report(String message) { log.add(message); changed.run(); }

    @SuppressWarnings("deprecation")
    private Collection<MidiDeviceInfo> devices() {
        if (!midiSupported) return new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33)
            return manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM);
        return Arrays.asList(manager.getDevices());
    }
    void refresh() {
        if (destroyed) return;
        inputs.clear(); outputs.clear();
        try {
            for (MidiDeviceInfo d : devices()) {
                String alias = bluetoothAnchor != null && bluetoothAnchor.getInfo().getId() == d.getId()
                        ? bluetoothName : null;
                for (MidiDeviceInfo.PortInfo p : d.getPorts()) {
                    Endpoint endpoint = new Endpoint(d, p, alias);
                    // Android OUT = the device sends -> the app's INPUT.
                    if (p.getType() == MidiDeviceInfo.PortInfo.TYPE_OUTPUT) inputs.add(endpoint);
                    // Android IN = the device receives -> the app's OUTPUT.
                    if (p.getType() == MidiDeviceInfo.PortInfo.TYPE_INPUT) outputs.add(endpoint);
                }
            }
            inputs.sort(Comparator.comparing(e -> e.label));
            outputs.sort(Comparator.comparing(e -> e.label));
        } catch (RuntimeException e) { log.add("MIDI eszk\u00f6zlista hiba: " + e.getMessage()); }
        usbSummary = usbDiagnostic();
        changed.run();
    }
    private String usbDiagnostic() {
        UsbManager usb = context.getSystemService(UsbManager.class);
        if (usb == null) return "USB host szolg\u00e1ltat\u00e1s nem el\u00e9rhet\u0151.";
        try {
            Collection<UsbDevice> devices = usb.getDeviceList().values();
            if (devices.isEmpty()) return "USB: nincs eszk\u00f6z. Ellen\u0151rizd az OTG/adatk\u00e1belt \u00e9s a ped\u00e1l t\u00e1pj\u00e1t.";
            StringBuilder text = new StringBuilder();
            for (UsbDevice d : devices) {
                if (text.length() > 0) text.append('\n');
                String name = d.getProductName();
                if (name == null) name = d.getDeviceName();
                boolean midiInterface = false;
                for (int i = 0; i < d.getInterfaceCount(); i++) {
                    if (d.getInterface(i).getInterfaceClass() == 1
                            && d.getInterface(i).getInterfaceSubclass() == 3) midiInterface = true;
                }
                text.append(name).append(" / VID:").append(d.getVendorId())
                        .append(" PID:").append(d.getProductId())
                        .append(midiInterface ? " / MIDI interf\u00e9sz" : " / nincs szabv\u00e1nyos MIDI interf\u00e9sz");
            }
            return text.toString();
        } catch (RuntimeException e) { return "USB diagnosztika: " + e.getMessage(); }
    }

    @SuppressLint("MissingPermission")
    void connectBluetooth(BleScanner.Peer peer) {
        if (!midiSupported || destroyed) return;
        if (busy()) { report("El\u0151bb \u00e1ll\u00edtsd le a routingot vagy a tesztet."); return; }
        if (bluetoothAnchor != null && peer.address.equals(bluetoothAddress)) {
            report("Ez a Bluetooth MIDI eszk\u00f6z m\u00e1r csatlakozik."); return;
        }
        scanner.stop();
        disconnectBluetooth();
        final int token = ++bleGeneration;
        bleConnecting = true;
        bluetoothState = "Csatlakoz\u00e1s: " + peer.name;
        report(bluetoothState);
        bleTimeout = () -> {
            if (token != bleGeneration || !bleConnecting) return;
            bleGeneration++; bleConnecting = false;
            bluetoothState = "A Bluetooth MIDI kapcsolat id\u0151t\u00fall\u00e9p\u00e9ssel le\u00e1llt.";
            report(bluetoothState + " Z\u00e1rd be a CubeSuite-ot; ellen\u0151rizd a MIDI m\u00f3dot.");
        };
        main.postDelayed(bleTimeout, 20_000);
        try {
            manager.openBluetoothDevice(peer.device, device -> {
                if (destroyed || token != bleGeneration) { close(device); return; }
                cancel(bleTimeout); bleTimeout = null; bleConnecting = false;
                if (device == null) {
                    bluetoothState = "Bluetooth MIDI megnyit\u00e1sa sikertelen.";
                    report(bluetoothState + " Nem minden BLE eszk\u00f6z MIDI eszk\u00f6z."); return;
                }
                // Retain this anchor: closing it unpublishes the BLE MIDI device.
                bluetoothAnchor = device; bluetoothName = peer.name; bluetoothAddress = peer.address;
                bluetoothState = "Csatlakoztatva: " + peer.name;
                report(bluetoothState); refresh();
            }, main);
        } catch (RuntimeException e) {
            bleGeneration++; cancel(bleTimeout); bleTimeout = null; bleConnecting = false;
            bluetoothState = "Bluetooth kapcsol\u00f3d\u00e1si hiba.";
            report(bluetoothState + " " + e.getMessage());
        }
    }
    void disconnectBluetooth() {
        ++bleGeneration; cancel(bleTimeout); bleTimeout = null; bleConnecting = false;
        if (routeBusy() && ((routeInput != null && routeInput.device.getType() == MidiDeviceInfo.TYPE_BLUETOOTH)
                || (routeOutput != null && routeOutput.device.getType() == MidiDeviceInfo.TYPE_BLUETOOTH)))
            stopRoute("Bluetooth kapcsolat bontva.");
        MidiDevice old = bluetoothAnchor;
        bluetoothAnchor = null; bluetoothName = bluetoothAddress = null;
        close(old);
        bluetoothState = "Nincs csatlakoztatott Bluetooth MIDI eszk\u00f6z.";
        refresh();
    }
    private Endpoint find(List<Endpoint> list, int id, int port) {
        for (Endpoint e : list) if (e.device.getId() == id && e.port == port) return e;
        return null;
    }

    void startRoute(int inputId, int inputPort, int outputId, int outputPort) {
        if (destroyed || !midiSupported) { report("MIDI nem el\u00e9rhet\u0151."); return; }
        if (testing) { report("A tesztk\u00fcld\u00e9s m\u00e9g fut."); return; }
        stopRoute(null);
        Endpoint source = find(inputs, inputId, inputPort);
        Endpoint destination = find(outputs, outputId, outputPort);
        if (source == null || destination == null) {
            routeState = "A kiv\u00e1lasztott MIDI port m\u00e1r nem el\u00e9rhet\u0151."; report(routeState); return;
        }
        if (inputId == outputId) {
            routeState = "Azonos eszk\u00f6zre visszak\u00f6t\u00e9s tiltva (MIDI hurok vesz\u00e9lye).";
            report(routeState); return;
        }
        final int token = ++routeGeneration;
        routeInput = source; routeOutput = destination;
        opening = true; routeState = "MIDI portok megnyit\u00e1sa...";
        currentPipe = new MidiPipe();
        report(routeState);
        routeTimeout = () -> { if (token == routeGeneration && opening)
            stopRoute("MIDI portnyit\u00e1si id\u0151t\u00fall\u00e9p\u00e9s."); };
        main.postDelayed(routeTimeout, 15_000);
        try {
            manager.openDevice(destination.device, device -> {
                if (destroyed || token != routeGeneration) { close(device); return; }
                if (device == null) { stopRoute("A kimeneti MIDI eszk\u00f6z nem nyithat\u00f3 meg."); return; }
                destinationDevice = device;
                try {
                    destinationPort = device.openInputPort(destination.port);
                    if (destinationPort == null) {
                        stopRoute("A kimeneti port foglalt vagy nem el\u00e9rhet\u0151. Z\u00e1rd be a t\u00f6bbi MIDI appot."); return;
                    }
                    manager.openDevice(source.device, opened -> finishRoute(token, opened, source), main);
                } catch (RuntimeException e) { stopRoute("Kimeneti port hiba: " + e.getMessage()); }
            }, main);
        } catch (RuntimeException e) { stopRoute("MIDI megnyit\u00e1si hiba: " + e.getMessage()); }
    }
    private void finishRoute(int token, MidiDevice device, Endpoint source) {
        if (destroyed || token != routeGeneration) { close(device); return; }
        if (device == null) { stopRoute("A bemeneti MIDI eszk\u00f6z nem nyithat\u00f3 meg."); return; }
        sourceDevice = device;
        try {
            sourcePort = device.openOutputPort(source.port);
            if (sourcePort == null) { stopRoute("A bemeneti MIDI port nem nyithat\u00f3 meg."); return; }
            final MidiInputPort target = destinationPort;
            final MidiPipe pipe = currentPipe;
            final MidiMonitor monitor = new MidiMonitor(s -> { if (monitorEnabled) log.midi(s); });
            pipe.attach(target::send);
            receiver = new MidiReceiver() {
                @Override public synchronized void onSend(byte[] data, int offset, int count, long timestamp) {
                    try {
                        // Pass EXACT bytes and Android's monotonic timestamp. No BLE headers here:
                        // MidiManager already removes/adds transport framing on each side.
                        if (pipe.forward(data, offset, count, timestamp)) monitor.accept(data, offset, count);
                    } catch (IOException | RuntimeException e) {
                        pipe.detach(); // First fault blocks subsequent writes immediately.
                        main.post(() -> { if (!destroyed && token == routeGeneration)
                            stopRoute("MIDI tov\u00e1bb\u00edt\u00e1si hiba: " + e.getMessage()); });
                    }
                }
                @Override public void onFlush() {
                    // No local scheduling queue; timestamps and flush are handled by Android's port.
                    try { target.flush(); } catch (IOException e) {
                        main.post(() -> { if (!destroyed && token == routeGeneration)
                            stopRoute("MIDI flush hiba: " + e.getMessage()); });
                    }
                }
            };
            sourcePort.connect(receiver);
            cancel(routeTimeout); routeTimeout = null;
            opening = false; running = true;
            routeState = source.label + "\n\u2192 " + routeOutput.label;
            report("ROUTING AKT\u00cdV: " + routeState.replace('\n', ' '));
        } catch (RuntimeException e) { stopRoute("Bemeneti port hiba: " + e.getMessage()); }
    }
    void stopRoute(String reason) {
        routeGeneration++; cancel(routeTimeout); routeTimeout = null;
        currentPipe.detach();
        running = opening = false;
        if (sourcePort != null && receiver != null) {
            try { sourcePort.disconnect(receiver); } catch (RuntimeException ignored) { }
        }
        receiver = null;
        close(sourcePort); sourcePort = null;
        close(destinationPort); destinationPort = null;
        close(sourceDevice); sourceDevice = null;
        close(destinationDevice); destinationDevice = null;
        routeInput = routeOutput = null;
        routeState = reason == null ? "Routing le\u00e1ll\u00edtva." : reason;
        if (reason != null) log.add(reason);
        changed.run();
    }

    /** Output-only diagnostic: disabled during forwarding to avoid corrupting running status/SysEx. */
    void sendTestPc(int outputId, int outputPort, int channel, int program) {
        if (destroyed || !midiSupported) return;
        if (busy()) { report("PC teszt el\u0151tt \u00e1ll\u00edtsd le a routingot."); return; }
        if (channel < 1 || channel > 16 || program < 0 || program > 127) {
            report("PC: 0-127, MIDI csatorna: 1-16."); return;
        }
        Endpoint destination = find(outputs, outputId, outputPort);
        if (destination == null) { report("Nincs kiv\u00e1lasztott kimenet."); return; }
        final int token = ++testGeneration;
        testing = true; changed.run();
        testTimeout = () -> { if (token == testGeneration && testing) {
            cancelTest(); report("PC teszt id\u0151t\u00fall\u00e9p\u00e9s."); } };
        main.postDelayed(testTimeout, 10_000);
        try {
            manager.openDevice(destination.device, device -> {
                if (destroyed || token != testGeneration) { close(device); return; }
                if (device == null) { cancelTest(); report("A teszt kimenete nem nyithat\u00f3 meg."); return; }
                testDevice = device;
                try {
                    testPort = device.openInputPort(destination.port);
                    if (testPort == null) { cancelTest(); report("A teszt kimeneti portja foglalt."); return; }
                    byte[] pc = {(byte) (0xC0 | (channel - 1)), (byte) program};
                    testPort.send(pc, 0, pc.length, 0);
                    report("TESZT elk\u00fcldve: PC ch=" + channel + " program=" + program
                            + ". Ez a k\u00fcld\u00e9st jelzi, nem a ped\u00e1l visszaigazol\u00e1s\u00e1t.");
                    main.postDelayed(() -> { if (token == testGeneration) cancelTest(); }, 250);
                } catch (IOException | RuntimeException e) {
                    cancelTest(); report("PC teszt hiba: " + e.getMessage());
                }
            }, main);
        } catch (RuntimeException e) { cancelTest(); report("PC teszt hiba: " + e.getMessage()); }
    }
    void cancelTest() {
        testGeneration++; cancel(testTimeout); testTimeout = null;
        close(testPort); testPort = null; close(testDevice); testDevice = null;
        testing = false; changed.run();
    }
    void destroy() {
        if (destroyed) return;
        destroyed = true;
        scanner.stop(); stopRoute(null); cancelTest(); disconnectBluetooth();
        if (callbackRegistered) manager.unregisterDeviceCallback(deviceCallback);
        if (receiverRegistered) context.unregisterReceiver(systemReceiver);
        main.removeCallbacksAndMessages(null);
    }
    private void cancel(Runnable task) { if (task != null) main.removeCallbacks(task); }
    private static void close(Closeable resource) {
        if (resource != null) try { resource.close(); } catch (IOException | RuntimeException ignored) { }
    }
    private final MidiManager.DeviceCallback deviceCallback = new MidiManager.DeviceCallback() {
        @Override public void onDeviceAdded(MidiDeviceInfo info) { if (!destroyed) refresh(); }
        @Override public void onDeviceRemoved(MidiDeviceInfo info) {
            if (destroyed) return;
            int id = info.getId();
            if ((routeInput != null && routeInput.device.getId() == id)
                    || (routeOutput != null && routeOutput.device.getId() == id))
                stopRoute("MIDI eszk\u00f6z lev\u00e1lasztva. Csatlakoztasd \u00fajra, majd ind\u00edtsd a routingot.");
            if (testDevice != null && testDevice.getInfo().getId() == id) cancelTest();
            if (bluetoothAnchor != null && bluetoothAnchor.getInfo().getId() == id) {
                MidiDevice old = bluetoothAnchor; bluetoothAnchor = null;
                bluetoothName = bluetoothAddress = null; close(old);
                bluetoothState = "Bluetooth MIDI kapcsolat megszakadt."; log.add(bluetoothState);
            }
            refresh();
        }
    };
    private final BroadcastReceiver systemReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            if (destroyed) return;
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    scanner.stop(); disconnectBluetooth();
                }
            }
            refresh();
        }
    };
}
