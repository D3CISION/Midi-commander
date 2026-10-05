package hu.spektrumhiba.midibridge;

import android.media.midi.MidiDeviceInfo;
import android.os.Bundle;
import java.util.Locale;

/** Direction is from the APP perspective, not Android's device-port perspective. */
public final class Endpoint {
    public final MidiDeviceInfo device;
    public final int port;
    public final String label;
    public final String key;
    public final String fingerprint;

    Endpoint(MidiDeviceInfo device, MidiDeviceInfo.PortInfo portInfo, String bluetoothName) {
        this.device = device;
        port = portInfo.getPortNumber();
        Bundle p = device.getProperties();
        String name = p.getString(MidiDeviceInfo.PROPERTY_NAME, "");
        String product = p.getString(MidiDeviceInfo.PROPERTY_PRODUCT, "");
        String manufacturer = p.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER, "");
        if (device.getType() == MidiDeviceInfo.TYPE_BLUETOOTH && bluetoothName != null)
            name = bluetoothName;
        if (name.isEmpty()) name = (manufacturer + " " + product).trim();
        if (name.isEmpty()) name = "MIDI #" + device.getId();
        String transport = device.getType() == MidiDeviceInfo.TYPE_USB ? "USB"
                : device.getType() == MidiDeviceInfo.TYPE_BLUETOOTH ? "Bluetooth" : "Virtual";
        String portName = portInfo.getName();
        label = name + " / " + transport + " / "
                + (portName == null || portName.isEmpty() ? "port " + (port + 1) : portName);
        key = device.getId() + ":" + portInfo.getType() + ":" + port;
        fingerprint = transport + "|" + manufacturer + "|" + product + "|" + name
                + "|" + portInfo.getType() + "|" + port;
    }
    boolean preferredInput() {
        String n = label.toLowerCase(Locale.ROOT);
        return device.getType() == MidiDeviceInfo.TYPE_BLUETOOTH
                && (n.contains("chocolate") || n.contains("footctrl") || n.contains("m-vave"));
    }
    boolean preferredOutput() {
        return device.getType() == MidiDeviceInfo.TYPE_USB
                && label.toLowerCase(Locale.ROOT).contains("tonex");
    }
    @Override public String toString() { return label; }
}
