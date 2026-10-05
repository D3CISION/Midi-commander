package hu.spektrumhiba.midibridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Native Android UI, no runtime libraries or accounts. */
public final class MainActivity extends Activity {
    private static final int PERMISSIONS = 100, ENABLE_BLUETOOTH = 101, ENABLE_LOCATION = 102;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BridgeService service;
    private boolean bindingRequested, visible, renderQueued;
    private Runnable pendingPermissionAction;
    private boolean pendingScanPermission;
    private Spinner inputSpinner, outputSpinner, channelSpinner;
    private EditText programEdit;
    private Switch allBle, monitorSwitch, keepScreen;
    private TextView connectionState, bleState, usbState, counters, logText;
    private LinearLayout bleResults;
    private Button scanButton, startButton, stopButton, testButton, disconnectButton;
    private List<Endpoint> inputItems = new ArrayList<>(), outputItems = new ArrayList<>();
    private String inputSignature = "#initial#", outputSignature = "#initial#", peerSignature = "";
    private long logVersion = -1;
    private SharedPreferences preferences;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        View root = findViewById(R.id.root);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return windowInsets;
            });
            root.requestApplyInsets();
        }
        inputSpinner = findViewById(R.id.inputSpinner);
        outputSpinner = findViewById(R.id.outputSpinner);
        channelSpinner = findViewById(R.id.channelSpinner);
        programEdit = findViewById(R.id.programEdit);
        allBle = findViewById(R.id.allBle);
        monitorSwitch = findViewById(R.id.monitorSwitch);
        keepScreen = findViewById(R.id.keepScreen);
        connectionState = findViewById(R.id.connectionState);
        bleState = findViewById(R.id.bleState);
        usbState = findViewById(R.id.usbState);
        counters = findViewById(R.id.counters);
        logText = findViewById(R.id.logText);
        logText.setMovementMethod(new ScrollingMovementMethod());
        bleResults = findViewById(R.id.bleResults);
        scanButton = findViewById(R.id.scanButton);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        testButton = findViewById(R.id.testButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        ArrayList<String> channels = new ArrayList<>();
        for (int i = 1; i <= 16; i++) channels.add(Integer.toString(i));
        channelSpinner.setAdapter(adapter(channels));
        channelSpinner.setSelection(Math.max(0, Math.min(15, preferences.getInt("testChannel", 1) - 1)));
        programEdit.setText(Integer.toString(preferences.getInt("testPc", 0)));
        monitorSwitch.setChecked(preferences.getBoolean("monitor", true));
        keepScreen.setChecked(preferences.getBoolean("screen", false));
        applyScreenSetting();
        monitorSwitch.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean("monitor", checked).apply();
            if (ready()) service.engine.monitorEnabled = checked;
        });
        keepScreen.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean("screen", checked).apply(); applyScreenSetting();
        });
        allBle.setOnCheckedChangeListener((button, checked) -> { peerSignature = ""; render(); });
        scanButton.setOnClickListener(v -> {
            if (!ready()) return;
            if (service.engine.scanner.isScanning()) service.engine.scanner.stop();
            else ensurePermissions(this::beginScan, true, false);
        });
        disconnectButton.setOnClickListener(v -> { if (ready()) service.engine.disconnectBluetooth(); });
        findViewById(R.id.refreshButton).setOnClickListener(v -> { if (ready()) service.engine.refresh(); });
        startButton.setOnClickListener(v -> ensurePermissions(this::startRouting, false, true));
        stopButton.setOnClickListener(v -> { if (ready()) service.engine.stopRoute("Routing le\u00e1ll\u00edtva."); });
        testButton.setOnClickListener(v -> sendTest());
        findViewById(R.id.copyButton).setOnClickListener(v -> {
            if (!ready()) return;
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("MIDI Bridge", diagnosticText()));
            toast("Napl\u00f3 a v\u00e1g\u00f3lapra m\u00e1solva.");
        });
        findViewById(R.id.clearButton).setOnClickListener(v -> {
            if (ready()) { service.engine.log.clear(); render(); }
        });
        findViewById(R.id.settingsButton).setOnClickListener(v -> openAppSettings());
        findViewById(R.id.helpButton).setOnClickListener(v -> showHelp());
        connectionState.setText("MIDI szolg\u00e1ltat\u00e1s ind\u00edt\u00e1sa...");
        startButton.setEnabled(false); stopButton.setEnabled(false); testButton.setEnabled(false);
    }
    private ArrayAdapter<String> adapter(List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }
    private boolean ready() { return service != null; }
    @Override public void onStart() {
        super.onStart(); visible = true;
        bindingRequested = bindService(new Intent(this, BridgeService.class), connection, BIND_AUTO_CREATE);
        main.post(tick);
    }
    @Override public void onStop() {
        visible = false; main.removeCallbacksAndMessages(null); renderQueued = false;
        if (service != null) {
            service.setListener(null);
            // Scanning is never left running after the user leaves the screen.
            service.engine.scanner.stop();
        }
        if (bindingRequested) unbindService(connection);
        bindingRequested = false; service = null;
        super.onStop();
    }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!visible) return;
            service = ((BridgeService.LocalBinder) binder).service();
            service.engine.monitorEnabled = monitorSwitch.isChecked();
            service.setListener(MainActivity.this::queueRender);
            inputSignature = outputSignature = "#initial#"; peerSignature = ""; logVersion = -1;
            render();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            connectionState.setText("A MIDI szolg\u00e1ltat\u00e1s le\u00e1llt. Nyisd meg \u00fajra az appot.");
            startButton.setEnabled(false); stopButton.setEnabled(false); testButton.setEnabled(false);
        }
    };
    private void queueRender() {
        if (!visible || renderQueued) return;
        renderQueued = true;
        main.postDelayed(() -> { renderQueued = false; render(); }, 120);
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() { if (visible) { render(); main.postDelayed(this, 500); } }
    };
    private static String signature(List<Endpoint> endpoints) {
        StringBuilder s = new StringBuilder();
        for (Endpoint e : endpoints) s.append(e.key).append(e.label).append('\n');
        return s.toString();
    }
    private Endpoint selected(boolean input) {
        Spinner spinner = input ? inputSpinner : outputSpinner;
        List<Endpoint> list = input ? inputItems : outputItems;
        int position = spinner.getSelectedItemPosition();
        return position >= 0 && position < list.size() ? list.get(position) : null;
    }
    private void updateEndpoints(boolean input, List<Endpoint> next) {
        String sig = signature(next);
        if (sig.equals(input ? inputSignature : outputSignature)) return;
        Endpoint previous = selected(input);
        String priorKey = previous == null ? null : previous.key;
        String activeKey = input ? service.engine.activeInputKey() : service.engine.activeOutputKey();
        String saved = preferences.getString(input ? "input" : "output", "");
        int chosen = -1;
        for (int i = 0; i < next.size(); i++) if (next.get(i).key.equals(activeKey)) chosen = i;
        if (chosen < 0) for (int i = 0; i < next.size(); i++) if (next.get(i).key.equals(priorKey)) chosen = i;
        if (chosen < 0) for (int i = 0; i < next.size(); i++) if (next.get(i).fingerprint.equals(saved)) chosen = i;
        if (chosen < 0) for (int i = 0; i < next.size(); i++)
            if (input ? next.get(i).preferredInput() : next.get(i).preferredOutput()) { chosen = i; break; }
        if (chosen < 0) for (int i = 0; i < next.size(); i++)
            if (next.get(i).device.getType() == (input ? android.media.midi.MidiDeviceInfo.TYPE_BLUETOOTH
                    : android.media.midi.MidiDeviceInfo.TYPE_USB)) { chosen = i; break; }
        ArrayList<String> labels = new ArrayList<>();
        for (Endpoint e : next) labels.add(e.label);
        if (labels.isEmpty()) labels.add(input ? "Nincs MIDI bemenet" : "Nincs MIDI kimenet");
        Spinner spinner = input ? inputSpinner : outputSpinner;
        if (input) { inputItems = next; inputSignature = sig; }
        else { outputItems = next; outputSignature = sig; }
        spinner.setAdapter(adapter(labels)); spinner.setSelection(Math.max(0, chosen));
    }
    private void render() {
        if (!ready() || !visible) return;
        MidiEngine e = service.engine;
        updateEndpoints(true, e.inputs()); updateEndpoints(false, e.outputs());
        connectionState.setText((e.running ? "AKT\u00cdV\n" : e.opening ? "CSATLAKOZ\u00c1S\n" : "LE\u00c1LL\u00cdTVA\n") + e.routeState);
        bleState.setText(e.bluetoothState);
        usbState.setText(e.usbSummary);
        inputSpinner.setEnabled(!e.busy()); outputSpinner.setEnabled(!e.busy());
        startButton.setEnabled(e.midiSupported && !e.busy() && selected(true) != null && selected(false) != null);
        stopButton.setEnabled(e.routeBusy());
        testButton.setEnabled(!e.busy() && selected(false) != null);
        scanButton.setEnabled(!e.busy() && !e.bleConnecting);
        scanButton.setText(e.scanner.isScanning() ? R.string.scan_stop : R.string.scan);
        disconnectButton.setEnabled(e.hasBluetooth() || e.bleConnecting);
        long[] c = e.counters();
        counters.setText("Be: " + c[0] + " byte  |  Ki: " + c[1] + " byte\nBe\u00e9rkezett adatblokkok: " + c[2]);
        long v = e.log.version();
        if (v != logVersion) {
            logVersion = v; logText.setText(e.log.text());
            logText.post(() -> {
                if (logText.getLayout() != null)
                    logText.scrollTo(0, Math.max(0, logText.getLayout().getHeight() - logText.getHeight()
                            + logText.getTotalPaddingTop() + logText.getTotalPaddingBottom()));
            });
        }
        renderPeers(e);
    }
    private void renderPeers(MidiEngine e) {
        List<BleScanner.Peer> peers = e.scanner.peers(allBle.isChecked());
        // Stable sorting while scanning avoids moving targets under the user's finger.
        peers.sort(java.util.Comparator.comparing((BleScanner.Peer p) -> !p.likelyMidi)
                .thenComparing(p -> p.address));
        StringBuilder signature = new StringBuilder().append(e.busy()).append(e.bleConnecting)
                .append(e.scanner.isScanning()).append(allBle.isChecked());
        for (BleScanner.Peer p : peers) signature.append(p.address).append(p.name).append(p.midiAdvertised);
        if (signature.toString().equals(peerSignature)) return;
        peerSignature = signature.toString(); bleResults.removeAllViews();
        if (peers.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(e.scanner.isScanning() ? "Keres\u00e9s..."
                    : "Nincs list\u00e1zott eszk\u00f6z. Ind\u00edts keres\u00e9st; sz\u00fcks\u00e9g eset\u00e9n mutass minden BLE eszk\u00f6zt.");
            empty.setPadding(0, 12, 0, 12); bleResults.addView(empty);
            return;
        }
        int count = 0;
        for (BleScanner.Peer peer : peers) {
            if (++count > 40) break;
            Button button = new Button(this);
            button.setAllCaps(false); button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setText(peer.label()); button.setTextSize(13); button.setMinHeight(dp(64));
            button.setEnabled(!e.busy() && !e.bleConnecting);
            button.setOnClickListener(v -> ensurePermissions(() -> {
                if (!ready()) return;
                if (!peer.likelyMidi) new AlertDialog.Builder(this).setTitle("Ismeretlen BLE eszk\u00f6z")
                        .setMessage("Nem hirdet szabv\u00e1nyos MIDI szolg\u00e1ltat\u00e1st. Megpr\u00f3b\u00e1lod MIDI-k\u00e9nt megnyitni?")
                        .setPositiveButton("Csatlakoz\u00e1s", (d, which) -> { if (ready()) service.engine.connectBluetooth(peer); })
                        .setNegativeButton("M\u00e9gse", null).show();
                else service.engine.connectBluetooth(peer);
            }, false, false));
            bleResults.addView(button, new LinearLayout.LayoutParams(-1, -2));
        }
    }
    private int dp(int pixels) { return Math.round(pixels * getResources().getDisplayMetrics().density); }
    private void startRouting() {
        if (!ready()) return;
        Endpoint in = selected(true), out = selected(false);
        if (in == null || out == null) { toast("V\u00e1lassz bemenetet \u00e9s kimenetet."); return; }
        preferences.edit().putString("input", in.fingerprint).putString("output", out.fingerprint).apply();
        service.engine.scanner.stop();
        Intent intent = new Intent(this, BridgeService.class).setAction(BridgeService.ACTION_START)
                .putExtra("inputId", in.device.getId()).putExtra("inputPort", in.port)
                .putExtra("outputId", out.device.getId()).putExtra("outputPort", out.port);
        try { startForegroundService(intent); }
        catch (RuntimeException ex) { service.engine.report("Nem ind\u00edthat\u00f3 a szolg\u00e1ltat\u00e1s: " + ex.getMessage()); }
    }
    private void sendTest() {
        if (!ready()) return;
        Endpoint out = selected(false);
        if (out == null) { toast("V\u00e1lassz kimenetet."); return; }
        try {
            int program = Integer.parseInt(programEdit.getText().toString().trim());
            int channel = channelSpinner.getSelectedItemPosition() + 1;
            if (program < 0 || program > 127) throw new NumberFormatException();
            preferences.edit().putInt("testPc", program).putInt("testChannel", channel)
                    .putString("output", out.fingerprint).apply();
            ensurePermissions(() -> { if (ready()) service.engine.sendTestPc(
                    out.device.getId(), out.port, channel, program); }, false, false);
        } catch (NumberFormatException ex) { toast("A Program Change sz\u00e1ma 0 \u00e9s 127 k\u00f6z\u00f6tt legyen."); }
    }
    private ArrayList<String> missingPermissions(boolean scan) {
        ArrayList<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            addMissing(permissions, Manifest.permission.BLUETOOTH_CONNECT);
            if (scan) addMissing(permissions, Manifest.permission.BLUETOOTH_SCAN);
        } else if (scan) addMissing(permissions, Manifest.permission.ACCESS_FINE_LOCATION);
        return permissions;
    }
    private void addMissing(List<String> permissions, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) permissions.add(permission);
    }
    private void ensurePermissions(Runnable action, boolean scan, boolean notifications) {
        if (!ready() || pendingPermissionAction != null) return;
        ArrayList<String> needed = missingPermissions(scan);
        if (notifications && Build.VERSION.SDK_INT >= 33
                && !preferences.getBoolean("notificationAsked", false)) {
            addMissing(needed, Manifest.permission.POST_NOTIFICATIONS);
            preferences.edit().putBoolean("notificationAsked", true).apply();
        }
        if (needed.isEmpty()) { action.run(); return; }
        pendingPermissionAction = action; pendingScanPermission = scan;
        requestPermissions(needed.toArray(new String[0]), PERMISSIONS);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSIONS) return;
        Runnable next = pendingPermissionAction; pendingPermissionAction = null;
        if (missingPermissions(pendingScanPermission).isEmpty()) { if (next != null) next.run(); }
        else new AlertDialog.Builder(this).setTitle("Enged\u00e9ly sz\u00fcks\u00e9ges")
                .setMessage(Build.VERSION.SDK_INT >= 31
                        ? "A keres\u00e9shez \u00e9s kapcsol\u00f3d\u00e1shoz enged\u00e9lyezd a K\u00f6zeli eszk\u00f6z\u00f6ket."
                        : "Android 8-11 alatt a Bluetooth-keres\u00e9shez helyenged\u00e9ly sz\u00fcks\u00e9ges. Az app nem hat\u00e1rozza meg a helyzeted.")
                .setPositiveButton("Be\u00e1ll\u00edt\u00e1sok", (d, which) -> openAppSettings())
                .setNegativeButton("M\u00e9gse", null).show();
    }
    @android.annotation.SuppressLint("MissingPermission")
    private void beginScan() {
        if (!ready()) return;
        BluetoothAdapter adapter = service.engine.scanner.adapter();
        if (adapter == null) { toast("A k\u00e9sz\u00fcl\u00e9k nem t\u00e1mogatja a Bluetooth-ot."); return; }
        try {
            if (!adapter.isEnabled()) {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BLUETOOTH); return;
            }
            if (Build.VERSION.SDK_INT <= 30 && !locationEnabled()) {
                new AlertDialog.Builder(this).setTitle("Bluetooth scan Android 8-11 alatt")
                        .setMessage("Kapcsold be a telefon Hely funkci\u00f3j\u00e1t is a BLE-keres\u00e9shez. Az app nem olvas GPS-koordin\u00e1t\u00e1kat.")
                        .setPositiveButton("Helybe\u00e1ll\u00edt\u00e1s", (d, which) -> startActivityForResult(
                                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), ENABLE_LOCATION))
                        .setNegativeButton("M\u00e9gse", null).show(); return;
            }
            service.engine.scanner.start();
        } catch (RuntimeException e) { service.engine.report("Bluetooth ind\u00edt\u00e1si hiba: " + e.getMessage()); }
    }
    private boolean locationEnabled() {
        LocationManager manager = getSystemService(LocationManager.class);
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= 28) return manager.isLocationEnabled();
        try { return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER); }
        catch (RuntimeException e) { return false; }
    }
    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Binding may not have finished yet after returning from Android's settings screen.
        if ((requestCode == ENABLE_BLUETOOTH && resultCode == RESULT_OK)
                || (requestCode == ENABLE_LOCATION && locationEnabled())) {
            main.postDelayed(() -> { if (ready()) ensurePermissions(this::beginScan, true, false); }, 350);
        }
    }
    private void applyScreenSetting() {
        if (keepScreen.isChecked()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }
    private String diagnosticText() {
        MidiEngine e = service.engine;
        return "MIDI Bridge 0.1.0\nAndroid API " + Build.VERSION.SDK_INT + " / " + Build.MANUFACTURER + " " + Build.MODEL
                + "\n" + e.usbSummary + "\n" + e.bluetoothState + "\n" + e.routeState + "\n\n" + e.log.text();
    }
    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("Chocolate \u2192 TONEX")
                .setMessage("1. A TONEX saj\u00e1t t\u00e1ppal m\u0171k\u00f6dj\u00f6n. USB adatk\u00e1bellel / OTG adapterrel k\u00f6sd a telefonhoz.\n\n"
                        + "2. A Chocolate MIDI-m\u00f3dban k\u00fcldj\u00f6n PC/CC-jeleket, ne billenty\u0171parancsokat. Be\u00e1ll\u00edt\u00e1s ut\u00e1n z\u00e1rd be a CubeSuite-ot.\n\n"
                        + "3. Bluetooth scan, enged\u00e9lyez\u00e9s, majd koppints a Chocolate / FootCtrl nev\u00e9re. Nem Bluetooth-hangp\u00e1ros\u00edt\u00e1st haszn\u00e1lunk.\n\n"
                        + "4. INPUT: Chocolate. OUTPUT: TONEX. Ind\u00edtsd a tov\u00e1bb\u00edt\u00e1st. A MIDI-csatorn\u00e1k egyezzenek a k\u00e9t ped\u00e1lon.\n\n"
                        + "5. A PC teszt routing n\u00e9lk\u00fcl ellen\u0151rzi a kimenetet. PC 0 az els\u0151 programsz\u00e1m; a ped\u00e1l saj\u00e1t kijelz\u0151je m\u00e1shogy sz\u00e1mozhat.\n\n"
                        + "Hiba: nincs bej\u00f6v\u0151 byte \u2192 Chocolate / kapcsolat / MIDI-m\u00f3d. Van be \u00e9s ki, de nem v\u00e1lt \u2192 csatorna, PC/CC vagy TONEX be\u00e1ll\u00edt\u00e1s.\n\n"
                        + "Ha USB eszk\u00f6z l\u00e1tszik, de MIDI kimenet nincs, az Android nem tett el\u00e9rhet\u0151v\u00e9 MIDI-portot. Az USB t\u00f6lt\u00e9s vagy hangkapcsolat \u00f6nmag\u00e1ban nem el\u00e9g.\n\n"
                        + "A h\u00e1tt\u00e9rszolg\u00e1ltat\u00e1s akt\u00edv routing mellett fut. A gy\u00e1rt\u00f3i energiatakar\u00e9koss\u00e1g megszak\u00edthatja: pr\u00f3b\u00e1ld ki lez\u00e1rt kijelz\u0151vel is.\n\n"
                        + "Nincs automatikus \u00fajracsatlakoz\u00e1s \u00e9s nincs CC\u2192PC konverzi\u00f3. Nincs internet, hangfelv\u00e9tel vagy felh\u0151. Hardveres pr\u00f3ba sz\u00fcks\u00e9ges.")
                .setPositiveButton("Rendben", null).show();
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
