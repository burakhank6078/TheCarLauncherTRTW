package com.thecarlauncher.util;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.thecarlauncher.mib.Mib;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Controls a Raise "LAMP&FRGN" ambient light strip over Bluetooth LE, the same way the phone app of
 * that name does, so the launcher's Ambient colour and level also set the strip.
 *
 * Protocol (read from the phone app): a GATT service 0xAE30 with a write characteristic 0xAE01 and a
 * notify characteristic 0xAE02 (some boxes use the 00010203-... service with 2B11 / 2B10). Every
 * message is  2E, type, length, data..., checksum  where checksum = (sum of type, length and data
 * bytes) XOR 0xFF. Type 0x81 data 01 starts a session; type 0x8D data 01 08 R G B R G B sets one
 * colour; type 0x8D data 00 48 L L sets the brightness (L = 0-100, a guess to be confirmed on the
 * strip). The strip takes one connection at a time, so the phone app must be closed.
 *
 * Everything is written to the diagnostic recorder (tag BLE).
 */
@SuppressLint("MissingPermission")
public final class AmbientBle {
    public static final String ON = "ambient_ble_on", ADDR = "ambient_ble_addr";
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID[][] CONFIGS = {
            {UUID.fromString("0000AE30-0000-1000-8000-00805f9b34fb"),
                    UUID.fromString("0000AE01-0000-1000-8000-00805f9b34fb"),
                    UUID.fromString("0000AE02-0000-1000-8000-00805f9b34fb")},
            {UUID.fromString("00010203-0405-0607-0809-0A0B0C0D1910"),
                    UUID.fromString("00010203-0405-0607-0809-0A0B0C0D2B11"),
                    UUID.fromString("00010203-0405-0607-0809-0A0B0C0D2B10")}};

    /** A device seen while searching. */
    public static final class Found {
        public final String name, address;
        public int rssi;

        Found(String name, String address, int rssi) {
            this.name = name;
            this.address = address;
            this.rssi = rssi;
        }
    }

    private static AmbientBle instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Found> found = new ArrayList<>();
    private final LinkedList<byte[]> queue = new LinkedList<>();
    private volatile BluetoothGatt gatt;
    /** Every connection object made here, so none is left open when a new one starts. */
    private final List<BluetoothGatt> all = new ArrayList<>();
    private BluetoothGattCharacteristic write;
    private boolean ready, writing, scanning, syncPending;
    private String status = "";
    private Runnable listener;
    /** Address of the connection connect() is creating right now; null otherwise. */
    private String pendingAddr;
    private volatile int adverts;

    private AmbientBle(Context c) { app = c.getApplicationContext(); }

    public static synchronized AmbientBle get(Context c) {
        if (instance == null) instance = new AmbientBle(c);
        return instance;
    }

    private BluetoothAdapter adapter() {
        BluetoothManager m = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
        return m == null ? null : m.getAdapter();
    }

    public boolean supported() {
        return app.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && adapter() != null;
    }

    public static boolean enabled(Context c) { return Prefs.bool(c, ON, false); }

    /** Runtime permissions needed to search for and connect to the strip. */
    public static String[] permissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[]{"android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"};
        }
        return new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION};
    }

    public static boolean hasPermissions(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        for (String p : permissions()) {
            if (c.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    public synchronized String status() { return status; }

    public synchronized List<Found> found() { return new ArrayList<>(found); }

    public synchronized boolean isReady() { return ready; }

    public void setListener(Runnable r) { listener = r; }

    private void set(String s) {
        status = s;
        AppLog.d(app, "BLE", s);
        changed();
    }

    private void changed() {
        final Runnable l = listener;
        if (l != null) main.post(l);
    }

    // ---- Switch and start ---------------------------------------------------------------------

    /** Follows the "control the strip" switch; connects to the saved device when switched on. */
    public void setEnabled(boolean on) {
        Prefs.put(app, ON, on);
        retryStep = 0;
        if (on) connectSaved();
        else disconnect();
    }

    // ---- Reconnecting ---------------------------------------------------------------------------

    /** Waits between attempts to reach the saved strip again: it may be off with the ignition, or out of range. */
    private static final long[] RETRY_MS = {5000, 10000, 20000, 30000, 60000};
    private volatile int retryStep;

    private final Runnable retry = new Runnable() {
        @Override
        public void run() {
            if (enabled(app) && gatt == null) connectSaved();
        }
    };

    /** A connection that never got ready (no answer in 25 s) is dropped and tried again. */
    private final Runnable connectTimeout = new Runnable() {
        @Override
        public void run() {
            if (isReady() || gatt == null) return;
            AppLog.d(app, "BLE", "no answer from the strip");
            disconnect();
            set("no answer from the strip; trying again");
            scheduleRetry();
        }
    };

    /** Tries the saved strip again later (5 s, 10 s ... up to a minute) while the switch is on. */
    private void scheduleRetry() {
        if (!enabled(app) || Prefs.str(app, ADDR, "").isEmpty()) return;
        main.removeCallbacks(retry);
        main.postDelayed(retry, RETRY_MS[Math.min(retryStep++, RETRY_MS.length - 1)]);
    }

    /** Called when the launcher starts. */
    public void start() {
        if (enabled(app) && gatt == null) connectSaved(); // not again when the activity is only recreated
    }

    private void connectSaved() {
        String a = Prefs.str(app, ADDR, null);
        if (a != null && !a.isEmpty()) connect(a);
    }

    // ---- Search -------------------------------------------------------------------------------

    private final ScanCallback scan = new ScanCallback() {
        @Override
        public void onScanResult(int type, ScanResult r) {
            adverts++;
            BluetoothDevice d = r.getDevice();
            String name;
            try {
                name = d.getName();
            } catch (SecurityException e) {
                name = null; // the name needs the Bluetooth (nearby devices) permission
            }
            synchronized (AmbientBle.this) {
                for (Found f : found) {
                    if (f.address.equals(d.getAddress())) {
                        f.rssi = r.getRssi();
                        return;
                    }
                }
                found.add(new Found(name == null ? "" : name, d.getAddress(), r.getRssi()));
            }
            AppLog.d(app, "BLE", "found " + name + " " + d.getAddress() + " rssi=" + r.getRssi());
            changed();
        }

        @Override
        public void onScanFailed(int code) {
            scanning = false;
            set("search failed (" + code + "); allow Location for the launcher and switch Bluetooth on");
        }
    };

    public void startScan() {
        if (!hasPermissions(app)) {
            set("allow the Bluetooth (nearby devices) permission, then search again");
            return;
        }
        BluetoothAdapter a = adapter();
        if (!supported() || a == null) {
            set("Bluetooth LE is not available on this unit");
            return;
        }
        if (!a.isEnabled()) {
            set("Bluetooth is off");
            return;
        }
        BluetoothLeScanner s = a.getBluetoothLeScanner();
        if (s == null) {
            set("no Bluetooth LE scanner");
            return;
        }
        if (scanning) { // Android refuses more than five starts in 30 s, so one search at a time
            set("searching...");
            return;
        }
        try {
            AppLog.d(app, "BLE", "search start: sdk=" + Build.VERSION.SDK_INT + " permissions=" + hasPermissions(app)
                    + " location=" + locationOn() + " adapterState=" + a.getState() + " scanMode=" + a.getScanMode()
                    + " discovering=" + a.isDiscovering() + " bonded=" + a.getBondedDevices().size()
                    + " leAdvertising=" + a.isMultipleAdvertisementSupported());
        } catch (Throwable t) {
            AppLog.d(app, "BLE", "adapter info failed: " + t);
        }
        synchronized (this) { found.clear(); }
        adverts = 0;
        try {
            // Low latency: the default low-power mode listens only a fraction of the time, which can
            // miss an advertising strip within a short search.
            s.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scan);
            scanning = true;
            set(locationOn() ? "searching..." : "searching... (Location is off; Android may hide results, switch it on)");
        } catch (Throwable t) {
            set("search failed: " + t);
            return;
        }
        main.removeCallbacks(scanTimeout); // an earlier search must not end this one early
        main.postDelayed(scanTimeout, 10000);
    }

    private final Runnable scanTimeout = new Runnable() {
        @Override
        public void run() { stopScan(); }
    };

    public void stopScan() {
        main.removeCallbacks(scanTimeout);
        if (!scanning) return;
        BluetoothAdapter a = adapter();
        BluetoothLeScanner s = a == null ? null : a.getBluetoothLeScanner(); // null while Bluetooth is off
        scanning = false; // also when the scanner is gone, or a new search would be refused for good
        if (s == null) {
            set("Bluetooth is off");
            return;
        }
        try {
            s.stopScan(scan);
        } catch (Throwable ignored) {
        }
        int n;
        synchronized (this) { n = found.size(); }
        AppLog.d(app, "BLE", "search ended: " + adverts + " advertisements, " + n + " devices, location=" + locationOn());
        set(n == 0 ? "nothing heard: switch the phone's Bluetooth off (the strip talks to one device) and search again"
                : n + " device(s) found");
    }

    /** Android hides Bluetooth LE results while the Location setting is off (before Android 12). */
    private boolean locationOn() {
        if (Build.VERSION.SDK_INT >= 31) return true; // BLUETOOTH_SCAN replaces the Location requirement
        try {
            android.location.LocationManager lm = (android.location.LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
            return lm == null || lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        } catch (Throwable t) {
            return true;
        }
    }

    // ---- Connection ---------------------------------------------------------------------------

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int st, int state) {
            boolean up = state == android.bluetooth.BluetoothProfile.STATE_CONNECTED;
            AppLog.d(app, "BLE", "connection state=" + state + " status=" + st + (g == gatt ? " (held)" : " (other object)"));
            if (g != gatt) {
                // Another connection object than the one we hold. The strip takes one connection, so a
                // link that actually came up wins: it replaces the one still waiting. A newcomer that
                // connect() is just creating is also accepted (its callback may come before
                // connectGatt() has returned). Anything else is a late callback of a closed connection.
                BluetoothGatt old = null;
                boolean take = false;
                String addr = g.getDevice().getAddress();
                synchronized (AmbientBle.this) {
                    boolean pendingMatch = gatt == null && pendingAddr != null && pendingAddr.equalsIgnoreCase(addr);
                    // Only while the held connection is not up yet: a working one is never replaced.
                    boolean wanted = up && !ready && enabled(app) && addr.equalsIgnoreCase(Prefs.str(app, ADDR, ""));
                    if (pendingMatch || wanted) {
                        take = true;
                        old = gatt;
                        gatt = g;
                        ready = false;
                        writing = false;
                        queue.clear();
                    }
                    all.remove(old);
                }
                AppLog.d(app, "BLE", (take ? "took over connection " : "ignored callback of another connection ")
                        + addr + " state=" + state + " status=" + st);
                if (old != null) {
                    try {
                        old.disconnect();
                        old.close();
                    } catch (Throwable ignored) {
                    }
                }
                if (!take) {
                    // A leftover connection, up or down, must not keep the strip's only slot.
                    synchronized (AmbientBle.this) { all.remove(g); }
                    try {
                        if (up) g.disconnect();
                        g.close();
                    } catch (Throwable ignored) {
                    }
                    return;
                }
            }
            if (up) {
                set("connected, reading services");
                g.discoverServices();
            } else {
                synchronized (AmbientBle.this) {
                    ready = false;
                    writing = false;
                    queue.clear();
                }
                main.removeCallbacks(writeTimeout);
                g.close();
                synchronized (AmbientBle.this) {
                    all.remove(g);
                    if (gatt == g) gatt = null;
                }
                set("disconnected (" + st + ")");
                scheduleRetry(); // the strip may only have been switched off or out of range
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int st) {
            if (g != gatt) return; // a connection that was replaced
            for (UUID[] c : CONFIGS) {
                BluetoothGattService s = g.getService(c[0]);
                if (s == null) continue;
                BluetoothGattCharacteristic w = s.getCharacteristic(c[1]);
                BluetoothGattCharacteristic n = s.getCharacteristic(c[2]);
                if (w == null) continue;
                synchronized (AmbientBle.this) { write = w; }
                boolean waiting = false; // true when onDescriptorWrite will start the session
                if (n != null) {
                    g.setCharacteristicNotification(n, true);
                    BluetoothGattDescriptor d = n.getDescriptor(CCCD);
                    if (d != null) {
                        d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        waiting = g.writeDescriptor(d);
                    }
                }
                set("ready (service " + c[0] + ")");
                if (!waiting) startSession(); // no notifications to switch on
                return;
            }
            set("this device has no ambient strip service");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int st) {
            if (g != gatt) return;
            startSession(); // notifications are on
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            if (g != gatt) return;
            AppLog.d(app, "BLE", "rx " + hex(c.getValue()));
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int st) {
            if (g != gatt) return;
            synchronized (AmbientBle.this) { writing = false; }
            main.removeCallbacks(writeTimeout);
            if (st != BluetoothGatt.GATT_SUCCESS) AppLog.d(app, "BLE", "write status " + st);
            next();
        }
    };

    /** The strip answers the characteristic; from here messages may be sent. */
    private void startSession() {
        main.removeCallbacks(connectTimeout);
        retryStep = 0; // reached it: the next loss starts with a short wait again
        synchronized (this) { ready = true; }
        send(pack((byte) 0x81, (byte) 1));
        boolean sync;
        synchronized (this) {
            sync = syncPending;
            syncPending = false;
        }
        if (sync) syncFromPrefs();
        changed();
    }

    /** A write whose callback never came must not block the queue for good. */
    private final Runnable writeTimeout = new Runnable() {
        @Override
        public void run() {
            synchronized (AmbientBle.this) {
                if (!writing) return;
                writing = false;
            }
            AppLog.d(app, "BLE", "write timed out");
            next();
        }
    };

    public void connect(String address) {
        if (!hasPermissions(app)) {
            set("allow the Bluetooth (nearby devices) permission first");
            return;
        }
        BluetoothAdapter a = adapter();
        if (!supported() || a == null || !a.isEnabled()) {
            set("Bluetooth LE is not available or off");
            scheduleRetry(); // Bluetooth may come on a little later
            return;
        }
        stopScanQuietly();
        disconnect();
        Prefs.put(app, ADDR, address);
        try {
            BluetoothDevice d = a.getRemoteDevice(address);
            synchronized (this) {
                syncPending = true;
                pendingAddr = address;
            }
            BluetoothGatt ng = Build.VERSION.SDK_INT >= 23
                    ? d.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE)
                    : d.connectGatt(app, false, callback);
            boolean stale;
            synchronized (this) {
                stale = pendingAddr == null; // disconnect() was called meanwhile
                pendingAddr = null;
                if (ng != null && !stale && !all.contains(ng)) all.add(ng);
                if (!stale && gatt == null) gatt = ng; // not when the callback already took it (or a newer one)
            }
            if (stale) {
                if (ng != null) ng.close();
                return;
            }
            main.removeCallbacks(connectTimeout);
            main.postDelayed(connectTimeout, 25000);
            set("connecting to " + address);
        } catch (Throwable t) {
            set("connect failed: " + t);
        }
    }

    private void stopScanQuietly() {
        BluetoothAdapter a = adapter();
        if (scanning && a != null && a.getBluetoothLeScanner() != null) {
            try {
                a.getBluetoothLeScanner().stopScan(scan);
            } catch (Throwable ignored) {
            }
        }
        scanning = false;
        main.removeCallbacks(scanTimeout);
    }

    public void disconnect() {
        List<BluetoothGatt> open;
        synchronized (this) {
            open = new ArrayList<>(all); // every connection made here, also any that lost track
            all.clear();
            if (gatt != null && !open.contains(gatt)) open.add(gatt);
            gatt = null;
            pendingAddr = null;
            ready = false;
            queue.clear();
            writing = false;
        }
        main.removeCallbacks(writeTimeout);
        main.removeCallbacks(retry);
        main.removeCallbacks(connectTimeout);
        for (BluetoothGatt g : open) {
            try {
                g.disconnect();
                g.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Forgets the saved device. */
    public void forget() {
        disconnect();
        Prefs.put(app, ADDR, "");
        set("no device chosen");
    }

    // ---- Commands -----------------------------------------------------------------------------

    /** Sets the strip to the launcher's current Ambient colour and level. */
    public void syncFromPrefs() {
        if (!enabled(app)) return;
        synchronized (this) {
            if (!ready) {
                syncPending = true;
                if (gatt == null) connectSaved();
                return;
            }
        }
        int color = Prefs.integer(app, Prefs.AMBIENT, Mib.DEFAULT_AMBIENT);
        int level = Prefs.integer(app, Prefs.AMBIENT_LEVEL, 100);
        byte r = (byte) Color.red(color), g = (byte) Color.green(color), b = (byte) Color.blue(color);
        send(pack((byte) 0x8D, (byte) 1, (byte) 8, r, g, b, r, g, b));
        send(pack((byte) 0x8D, (byte) 0, (byte) 0x48, (byte) level, (byte) level));
    }

    /** One framed message: 2E, type, length, data, checksum. */
    static byte[] pack(byte type, byte... data) {
        byte[] out = new byte[data.length + 4];
        out[0] = 0x2E;
        out[1] = type;
        out[2] = (byte) data.length;
        System.arraycopy(data, 0, out, 3, data.length);
        int sum = type + data.length;
        for (byte x : data) sum += x;
        out[out.length - 1] = (byte) (sum ^ 255);
        return out;
    }

    private void send(byte[] msg) {
        synchronized (this) {
            if (!ready) return;
            queue.add(msg);
        }
        next();
    }

    private void next() {
        byte[] msg;
        BluetoothGattCharacteristic w;
        BluetoothGatt g = gatt;
        synchronized (this) {
            if (writing || queue.isEmpty() || write == null || g == null) return;
            msg = queue.removeFirst();
            w = write;
            writing = true;
        }
        // A characteristic that only allows "write without response" needs that write type.
        int props = w.getProperties();
        w.setWriteType((props & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0
                && (props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        w.setValue(msg);
        boolean ok = g.writeCharacteristic(w);
        AppLog.d(app, "BLE", "tx " + hex(msg) + (ok ? "" : " (not accepted)"));
        if (ok) {
            main.removeCallbacks(writeTimeout);
            main.postDelayed(writeTimeout, 1500);
        } else {
            synchronized (this) { writing = false; }
            next(); // this message is dropped; the rest of the queue still goes out
        }
    }

    private static String hex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }
}
