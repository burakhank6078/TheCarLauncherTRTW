package com.thecarlauncher.car;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

import com.thecarlauncher.util.AppLog;
import com.thecarlauncher.util.Prefs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An ELM327 adapter on the car's OBD-II port, over classic Bluetooth (the serial profile). It reads current values (coolant,
 * oil, intake and outside temperature, fuel level, load, rpm, speed, battery voltage) into {@link Obd2Data}; nothing is ever
 * written to the car: only the adapter's set-up commands and Mode 01 requests go out, and {@link Elm327Protocol#allowed}
 * refuses anything else. Off by default (Setup &gt; Vehicle &gt; OBD-II adapter). While the ignition is known to be off the
 * connection is closed, so the adapter does not keep the battery busy overnight. It runs on its own thread, whatever app is
 * in front.
 */
@SuppressLint("MissingPermission")
public final class Elm327Link {
    public static final String ON = "elm_on", ADDR = "elm_addr", NAME = "elm_name";

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final long RETRY_MS = 5000, STEP_MS = 120, REPLY_MS = 3000, FIRST_REPLY_MS = 12_000;
    private static final int FAILS_BEFORE_RECONNECT = 6;
    /** Asked on every round, then one of the slow ones per round. */
    private static final int[] FAST = {Elm327Protocol.RPM, Elm327Protocol.SPEED, Elm327Protocol.LOAD, Elm327Protocol.THROTTLE};
    private static final int[] SLOW = {Elm327Protocol.COOLANT, Elm327Protocol.OIL, Elm327Protocol.AMBIENT, Elm327Protocol.INTAKE,
            Elm327Protocol.FUEL, Elm327Protocol.MODULE_VOLTAGE};

    private static Elm327Link sInstance;

    private final Context app;
    private HandlerThread thread;
    private Handler io;
    private volatile boolean running;
    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;
    private List<Integer> asked = new ArrayList<>();
    private int round, fails;
    private final List<String[]> found = new ArrayList<>(); // name, address of the devices a search has seen
    private BroadcastReceiver finder;
    private Runnable listener;

    public static synchronized Elm327Link get(Context c) {
        if (sInstance == null) sInstance = new Elm327Link(c.getApplicationContext());
        return sInstance;
    }

    private Elm327Link(Context c) { app = c; }

    public static boolean enabled(Context c) { return Prefs.bool(c, ON, false); }

    public void setListener(Runnable r) { listener = r; }

    /** Starts or stops the link to match the switch and the chosen adapter; safe to call as often as you like. */
    public synchronized void sync() {
        boolean want = enabled(app) && !Prefs.str(app, ADDR, "").isEmpty();
        if (want == running) return;
        if (want) {
            running = true;
            thread = new HandlerThread("elm327");
            thread.start();
            io = new Handler(thread.getLooper());
            io.post(loop);
        } else {
            running = false;
            final Handler h = io;
            if (h != null) {
                h.removeCallbacks(loop);
                h.post(new Runnable() {
                    @Override
                    public void run() { closeSocket(); }
                });
            }
            if (thread != null) thread.quitSafely();
            thread = null;
            io = null;
            Obd2Data.get().clear();
            status(enabled(app) ? "no device chosen" : "switched off");
        }
    }

    private void status(String s) {
        Obd2Data.get().setStatus(s);
        Runnable l = listener;
        if (l != null) l.run();
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            Handler h = io;
            if (!running || h == null) return;
            long next = STEP_MS;
            try {
                int ign = VehicleState.get().ignition();
                if (ign == 0) {
                    if (socket != null) {
                        closeSocket();
                        Obd2Data.get().clear();
                    }
                    status("waiting for the ignition");
                    next = RETRY_MS;
                } else {
                    if (socket == null) connect();
                    pollOnce();
                }
            } catch (IOException e) {
                closeSocket();
                Obd2Data.get().clear();
                status("connection lost");
                AppLog.d(app, "ELM", "link: " + e.getMessage());
                next = RETRY_MS;
            } catch (RuntimeException e) {
                closeSocket();
                status("connection lost");
                AppLog.d(app, "ELM", "error: " + e);
                next = RETRY_MS;
            }
            if (running && io != null) io.postDelayed(this, next);
        }
    };

    private void connect() throws IOException {
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null || !ad.isEnabled()) {
            status("Bluetooth is off");
            throw new IOException("Bluetooth is off");
        }
        status("connecting");
        BluetoothDevice dev = ad.getRemoteDevice(Prefs.str(app, ADDR, ""));
        ad.cancelDiscovery();
        BluetoothSocket s;
        try {
            s = dev.createRfcommSocketToServiceRecord(SPP);
            s.connect();
        } catch (IOException first) {
            // some adapters only answer on channel 1 of the insecure link
            try {
                s = (BluetoothSocket) dev.getClass().getMethod("createRfcommSocket", int.class).invoke(dev, 1);
                s.connect();
            } catch (Exception second) {
                throw new IOException("cannot connect: " + first.getMessage());
            }
        }
        socket = s;
        in = s.getInputStream();
        out = s.getOutputStream();
        fails = 0;
        round = 0;
        init();
    }

    /** The adapter's set-up and the list of the values the car gives. */
    private void init() throws IOException {
        send("ATZ", FIRST_REPLY_MS);
        for (String c : new String[]{"ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATAT1"}) send(c, REPLY_MS);
        List<Integer> supported = new ArrayList<>();
        int[] bases = {0x00, 0x20, 0x40, 0x60};
        for (int b : bases) {
            if (b != 0 && !supported.contains(b)) break; // the next block is only there when the last one says so
            String r = send(Elm327Protocol.request(b), FIRST_REPLY_MS);
            int[] mask = Elm327Protocol.payload(r, b);
            if (mask == null) break;
            supported.addAll(Elm327Protocol.supported(b, mask));
        }
        asked = new ArrayList<>();
        for (int pid : FAST) if (supported.contains(pid)) asked.add(pid);
        for (int pid : SLOW) if (supported.contains(pid)) asked.add(pid);
        AppLog.d(app, "ELM", "supported " + supported.size() + ", asked " + asked.size());
        status(asked.isEmpty() ? "no data from the car yet" : "connected");
    }

    private void pollOnce() throws IOException {
        if (asked.isEmpty()) {
            // connected but the car gave nothing yet (ignition just on?): look again
            init();
            return;
        }
        List<Integer> now = new ArrayList<>();
        for (int pid : FAST) if (asked.contains(pid)) now.add(pid);
        List<Integer> slow = new ArrayList<>();
        for (int pid : SLOW) if (asked.contains(pid)) slow.add(pid);
        if (!slow.isEmpty()) now.add(slow.get(round % slow.size()));
        boolean any = false;
        for (int pid : now) {
            String r = send(Elm327Protocol.request(pid), REPLY_MS);
            double v = Elm327Protocol.value(pid, Elm327Protocol.payload(r, pid));
            if (Double.isNaN(v)) continue;
            Obd2Data.get().set(pid, v);
            any = true;
        }
        if (round % 10 == 0) {
            double volts = Elm327Protocol.voltage(send("ATRV", REPLY_MS));
            if (!Double.isNaN(volts)) {
                Obd2Data.get().setAdapterVolts(volts);
                any = true;
            }
        }
        round++;
        fails = any ? 0 : fails + 1;
        if (fails >= FAILS_BEFORE_RECONNECT) {
            closeSocket();
            throw new IOException("no answers");
        }
        status(any ? "connected" : "no data from the car yet");
    }

    /** One request: only what {@link Elm327Protocol#allowed} lets through is sent; the reply is read up to the prompt. */
    private String send(String cmd, long timeoutMs) throws IOException {
        if (!Elm327Protocol.allowed(cmd)) throw new IllegalArgumentException("not allowed: " + cmd);
        while (in.available() > 0) in.read(); // anything left over from before
        out.write((cmd + "\r").getBytes("US-ASCII"));
        out.flush();
        StringBuilder sb = new StringBuilder();
        long until = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < until) {
            if (in.available() > 0) {
                int ch = in.read();
                if (ch < 0) throw new IOException("closed");
                if (ch == '>') return sb.toString();
                sb.append((char) ch);
            } else {
                SystemClock.sleep(10);
            }
        }
        return sb.toString(); // no prompt in time: what came is parsed as far as it goes
    }

    private void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
        in = null;
        out = null;
    }

    // ---- finding and pairing the adapter ----------------------------------------------------------

    /** The devices already paired with the unit: {name, address}. */
    public List<String[]> paired() {
        List<String[]> out = new ArrayList<>();
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) return out;
        try {
            for (BluetoothDevice d : ad.getBondedDevices()) out.add(new String[]{d.getName() == null ? "" : d.getName(), d.getAddress()});
        } catch (SecurityException ignored) {
        }
        return out;
    }

    public synchronized List<String[]> found() { return new ArrayList<>(found); }

    /** Looks for devices for about 12 seconds; the listener hears about each one. */
    public void search() {
        final BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) return;
        stopSearch();
        synchronized (this) { found.clear(); }
        finder = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d == null) return;
                synchronized (Elm327Link.this) {
                    for (String[] f : found) if (f[1].equals(d.getAddress())) return;
                    found.add(new String[]{d.getName() == null ? "" : d.getName(), d.getAddress()});
                }
                Runnable l = listener;
                if (l != null) l.run();
            }
        };
        app.registerReceiver(finder, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        ad.startDiscovery();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() { stopSearch(); }
        }, 12_000);
    }

    public void stopSearch() {
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        try {
            if (ad != null && ad.isDiscovering()) ad.cancelDiscovery();
        } catch (SecurityException ignored) {
        }
        BroadcastReceiver f = finder;
        finder = null;
        if (f != null) {
            try {
                app.unregisterReceiver(f);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Runnable l = listener;
        if (l != null) l.run();
    }

    /** Pairs with a device (Android asks for the PIN, usually 1234 or 0000), then chooses it. */
    public void choose(String name, String address) {
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        Prefs.put(app, NAME, name);
        Prefs.put(app, ADDR, address);
        try {
            BluetoothDevice d = ad == null ? null : ad.getRemoteDevice(address);
            if (d != null && d.getBondState() == BluetoothDevice.BOND_NONE) d.createBond();
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
        if (running && io != null) {
            io.post(new Runnable() { // a new adapter: drop the old link, the loop connects to the one chosen
                @Override
                public void run() { closeSocket(); }
            });
        }
        sync();
    }
}
