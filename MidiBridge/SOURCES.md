# Technical sources

Checked for this source project on 2026-10-05. These explain the APIs and build
requirements, not a verified hardware compatibility result for this app.

1. Android MIDI overview: USB/BLE byte streams, device-port direction, fragmented data.
   https://developer.android.com/reference/android/media/midi/package-summary
2. MidiManager: getDevicesForTransport, openDevice, openBluetoothDevice; lifetime of
   the Bluetooth MIDI device opened by the app.
   https://developer.android.com/reference/android/media/midi/MidiManager
3. Android Bluetooth permissions: Android 12+ Nearby devices and older location access.
   https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
4. BLE discovery: timed scans, stopScan, callbacks.
   https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices
5. Foreground service types: connectedDevice declaration and runtime prerequisites.
   https://developer.android.com/develop/background-work/services/fgs/service-types
6. Android Gradle Plugin 8.11: Gradle 8.13, JDK 17, supported API 36, Build Tools 35.0.0.
   https://developer.android.com/build/releases/agp-8-11-0-release-notes
7. Building installable debug APKs; Android Studio build menu and output directories.
   https://developer.android.com/build/build-for-release
8. Gradle 8.13 distribution and wrapper SHA-256 checksums.
   https://gradle.org/release-checksums/
9. Original Chocolate product page (NOT Chocolate Plus): Bluetooth/USB MIDI controller.
   https://www.m-vave.com/product?id=chocolate
10. TONEX Pedal specifications and power/USB accessories. This page alone does NOT
    establish Android USB-MIDI compatibility for every firmware/phone combination.
    https://www.ikmultimedia.com/products/tonexpedal/index.php?p=specs
11. Gradle setup action documentation used by the optional, unexecuted CI workflow.
    https://github.com/gradle/actions/blob/main/docs/setup-gradle.md
12. Android SDK setup action documentation.
    https://github.com/android-actions/setup-android
13. GitHub artifact upload action documentation.
    https://github.com/actions/upload-artifact

The Gradle launcher scripts are custom source-only bootstrappers. The wrapper JAR
is deliberately not included, since it could not be downloaded in the authoring
environment. They fetch the official Gradle 8.13 wrapper and verify its published
SHA-256 before execution. Android build dependencies also require network access
on the first build. The APK itself does not request INTERNET permission.

This is an independent project, not an official IK Multimedia or M-VAVE product.
