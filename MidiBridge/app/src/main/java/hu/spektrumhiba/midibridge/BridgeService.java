package hu.spektrumhiba.midibridge;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/** User-started connected-device foreground service; independent of Activity lifecycle. */
public final class BridgeService extends Service {
    static final String ACTION_START = "hu.spektrumhiba.midibridge.START";
    static final String ACTION_STOP = "hu.spektrumhiba.midibridge.STOP";
    static final String CHANNEL = "midi_route";
    private static final int NOTIFICATION_ID = 41;
    final class LocalBinder extends Binder { BridgeService service() { return BridgeService.this; } }
    private final IBinder binder = new LocalBinder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable listener;
    private boolean foreground, starting, destroying;
    private String lastNotification = "";
    private PowerManager.WakeLock wakeLock;
    MidiEngine engine;

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                "MIDI tov\u00e1bb\u00edt\u00e1s", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Bluetooth MIDI \u2192 USB MIDI kapcsolat");
        channel.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        PowerManager power = getSystemService(PowerManager.class);
        if (power != null) {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MidiBridge:Routing");
            wakeLock.setReferenceCounted(false);
        }
        engine = new MidiEngine(this, this::onEngineChanged);
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    void setListener(Runnable listener) { this.listener = listener; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(startId); return START_NOT_STICKY; }
        if (ACTION_STOP.equals(intent.getAction())) {
            engine.stopRoute("Routing le\u00e1ll\u00edtva.");
            finishForeground();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) { stopSelf(startId); return START_NOT_STICKY; }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            engine.report("A k\u00f6zeli eszk\u00f6z\u00f6k enged\u00e9lye sz\u00fcks\u00e9ges a szolg\u00e1ltat\u00e1shoz.");
            stopSelf(startId); return START_NOT_STICKY;
        }
        try {
            Notification notification = notification("MIDI kapcsolat ind\u00edt\u00e1sa...");
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(NOTIFICATION_ID, notification);
            foreground = true;
            // startRoute first tears down the old route: suppress premature stopForeground.
            starting = true;
            engine.startRoute(intent.getIntExtra("inputId", -1), intent.getIntExtra("inputPort", -1),
                    intent.getIntExtra("outputId", -1), intent.getIntExtra("outputPort", -1));
        } catch (RuntimeException e) {
            engine.stopRoute("A h\u00e1tt\u00e9rszolg\u00e1ltat\u00e1s nem indult: " + e.getMessage());
            stopSelf(startId);
        } finally {
            starting = false;
            onEngineChanged();
        }
        return START_NOT_STICKY; // Never silently restart a live rig after process death.
    }
    private void onEngineChanged() {
        if (destroying || engine == null) return;
        if (foreground && !starting) {
            if (!engine.routeBusy()) finishForeground();
            else {
                String text = engine.opening ? "MIDI portok megnyit\u00e1sa..." : engine.routeState.replace('\n', ' ');
                if (!text.equals(lastNotification)) {
                    lastNotification = text;
                    if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED)
                        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(text));
                }
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire(10 * 60 * 1000L);
                    main.removeCallbacks(renewWakeLock);
                    main.postDelayed(renewWakeLock, 5 * 60 * 1000L);
                }
            }
        }
        if (listener != null) listener.run();
    }
    private final Runnable renewWakeLock = new Runnable() {
        @Override public void run() {
            if (foreground && engine != null && engine.routeBusy() && wakeLock != null) {
                wakeLock.acquire(10 * 60 * 1000L);
                main.postDelayed(this, 5 * 60 * 1000L);
            }
        }
    };
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, BridgeService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("MIDI Bridge akt\u00edv")
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, "Le\u00e1ll\u00edt\u00e1s", stop).build())
                .build();
    }
    private void finishForeground() {
        main.removeCallbacks(renewWakeLock);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false; lastNotification = "";
        stopSelf(); // A bound Activity can still use scanning and output-only tests.
    }
    @Override public void onDestroy() {
        destroying = true; listener = null;
        if (engine != null) engine.destroy();
        main.removeCallbacksAndMessages(null);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
