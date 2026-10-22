package com.thecarlauncher.car;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Read-only listener on the TW platform's {@code android.tw.john.TWUtil}, the channel the factory
 * car app (com.tw.car) uses to talk to the Canbus decoder. It opens the same message ids as the
 * factory app and only listens: nothing is ever written, so the car is not affected.
 *
 * Message layout seen in the factory app: id 0x501 carries data frames (arg1 = frame number,
 * obj = byte[] payload); 0x10A carries the protocol string; 0x20B and 0x9F1C are events.
 *
 * It advances one step per tap. The step in progress is stored before it runs; if the app dies
 * there, the next start reports which step crashed and refuses to repeat it.
 */
public final class TwProbe {
    private static final TwProbe INSTANCE = new TwProbe();
    private static final String CLASS = "android.tw.john.TWUtil";
    private static final String CRASH = "twprobe_crash_stage";
    private static final String BUSY = "twprobe_busy_stage";
    private static final int LAST = 5;
    /** The ids com.tw.car itself opens. */
    private static final short[] IDS = {266, 267, 513, 514, 1281, 1288, 523, -24804};

    private final Map<Long, String> latest = new LinkedHashMap<>();
    private final Map<Long, Integer> counts = new LinkedHashMap<>();
    private String status = "not started";
    private final List<String> api = new ArrayList<>();
    private Object util;
    private Class<?> cls;
    private Handler handler;
    private int stage;
    private Context appCtx;
    private final Map<Long, String> lastLogged = new LinkedHashMap<>();
    private final Map<Long, Long> lastLoggedAt = new LinkedHashMap<>();
    private final Map<Long, String> pending = new LinkedHashMap<>();

    public static TwProbe get() { return INSTANCE; }

    public synchronized String status() { return status; }

    public synchronized List<String> api() { return new ArrayList<>(api); }

    /** One line per message: id (0x501 also by frame number), count, time, arg1/arg2, payload. */
    public synchronized List<String> summary() {
        List<String> out = new ArrayList<>();
        for (Long k : new TreeSet<>(latest.keySet())) {
            int what = (int) (k >> 16);
            int arg = (int) (k & 0xFFFF);
            String id = what == 0x501 ? String.format(Locale.US, "0x501/%d", arg)
                    : String.format(Locale.US, "0x%X", what);
            out.add(String.format(Locale.US, "%s x%d %s", id, counts.get(k), latest.get(k)));
        }
        return out;
    }

    /** Runs the next step; call it once per tap. */
    public synchronized void start(Context ctx) {
        appCtx = ctx.getApplicationContext();
        run(ctx);
        com.thecarlauncher.util.AppLog.d(appCtx, "CAN", status);
    }

    /** Runs every step in turn (used by the diagnostic recorder); a crashed or failing step stops it. */
    public synchronized void autoStart(Context ctx) {
        int guard = 0;
        while (stage < LAST && guard++ <= LAST) {
            int before = stage;
            start(ctx);
            if (stage == before) break;
        }
    }

    private void run(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences("launcher", Context.MODE_PRIVATE);
        int busy = sp.getInt(BUSY, 0);
        if (busy != 0) {
            // The app died while this step was running.
            sp.edit().putInt(CRASH, busy).putInt(BUSY, 0).commit();
        }
        int crashed = sp.getInt(CRASH, 0);
        int next = stage + 1;
        if (crashed != 0 && next >= crashed) {
            status = "step " + crashed + " crashed the app - not repeated";
            return;
        }
        if (next > LAST) {
            status = "listening - " + summary().size() + " message kinds seen";
            return;
        }
        sp.edit().putInt(BUSY, next).commit();
        try {
            step(next);
            stage = next;
        } catch (ClassNotFoundException e) {
            status = "TWUtil not on this unit";
            absent = true;
            stage = LAST;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            status = "step " + next + " error: " + cause.getClass().getSimpleName() + " " + cause.getMessage();
        } finally {
            sp.edit().putInt(BUSY, 0).commit();
        }
    }

    private void step(int n) throws Exception {
        switch (n) {
            case 1: // load the class and list its methods; nothing is created yet
                cls = Class.forName(CLASS);
                for (Method m : cls.getMethods()) {
                    if (m.getDeclaringClass() == Object.class) continue;
                    api.add(m.getName() + "(" + m.getParameterTypes().length + ")");
                }
                status = "step 1 ok: class found, " + api.size() + " methods";
                break;
            case 2: // create the object
                util = newInstance(cls);
                status = "step 2 ok: object created";
                break;
            case 3: { // open with the same ids the factory car app uses (0 = ok)
                Method open = find(cls, "open", short[].class);
                if (open == null) throw new NoSuchMethodException("open(short[])");
                Object r = open.invoke(util, (Object) IDS);
                status = "step 3 ok: open(8 ids) returned " + r;
                break;
            }
            case 4: { // start delivery
                Method go = find(cls, "start");
                if (go != null) go.invoke(util);
                status = "step 4 ok: started (" + (go != null ? "start()" : "no start()") + ")";
                break;
            }
            default: { // attach our own handler (own thread)
                Method add = find(cls, "addHandler", String.class, Handler.class);
                if (add == null) throw new NoSuchMethodException("addHandler");
                HandlerThread ht = new HandlerThread("tw-probe");
                ht.start();
                handler = new Handler(ht.getLooper()) {
                    @Override
                    public void handleMessage(Message m) {
                        try {
                            record(m);
                        } catch (Throwable ignored) {
                            // an odd message must never kill the thread
                        }
                    }
                };
                add.invoke(util, "carlauncher-probe", handler);
                status = "step 5 ok: handler attached - listening";
                break;
            }
        }
    }

    private static Object newInstance(Class<?> c) throws Exception {
        for (Constructor<?> k : c.getConstructors()) {
            if (k.getParameterTypes().length == 0) return k.newInstance();
        }
        return c.getConstructors()[0].newInstance(0);
    }

    private static Method find(Class<?> c, String name, Class<?>... params) {
        try {
            return c.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Opens and starts the channel (steps 1-4) without attaching the listener; enough to send. */
    private void openOnly(Context ctx) {
        int guard = 0;
        while (stage < 4 && guard++ < 4) {
            int before = stage;
            start(ctx);
            if (stage == before) break;
        }
    }

    /** True when an earlier native crash is recorded. */
    public boolean hadCrash(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("launcher", Context.MODE_PRIVATE).getInt(CRASH, 0) != 0;
    }

    /** Why the last write was refused or failed; empty after a successful one. */
    private String writeError = "";
    private volatile boolean absent;
    private Handler writer;

    public synchronized String writeError() { return writeError; }

    /** Cheap, never waits for a running write, so safe on the UI thread: commands are allowed and TWUtil is neither missing nor known to crash. */
    public boolean usable(Context ctx) {
        return !absent && !hadCrash(ctx)
                && com.thecarlauncher.util.Prefs.bool(ctx.getApplicationContext(), com.thecarlauncher.util.Prefs.TW_COMMANDS, false);
    }

    private synchronized Handler writerHandler() {
        if (writer == null) {
            HandlerThread t = new HandlerThread("tw-write");
            t.start();
            writer = new Handler(t.getLooper());
        }
        return writer;
    }

    /**
     * Asks the unit to send one settings frame (as the factory car app does when a page opens: message
     * 10 with bytes 1 and the frame number) and makes sure the listener is on to receive the answer.
     * Only while "Allow sending commands" is on; otherwise nothing happens. Runs on its own thread.
     */
    public void requestFrameAsync(Context ctx, final int frame) {
        final Context app = ctx.getApplicationContext();
        if (!usable(app)) return;
        writerHandler().post(new Runnable() {
            @Override
            public void run() {
                synchronized (TwProbe.this) {
                    autoStart(app);
                    write(app, 0x501, 10, 2, new byte[]{1, (byte) frame});
                }
            }
        });
    }

    /** Receives the outcome of {@link #writeAsync} on the main thread; error is empty on success. */
    public interface Result {
        void done(boolean ok, String error);
    }

    /**
     * {@link #write} on a separate thread, so opening the channel and the native call never block the
     * UI or the thread that delivers car messages. The result (may be null) comes back on the main thread.
     */
    public void writeAsync(Context ctx, final int what, final int arg1, final int arg2, final Object obj, final Result cb) {
        final Context app = ctx.getApplicationContext();
        writerHandler().post(new Runnable() {
            @Override
            public void run() {
                final boolean ok;
                final String err;
                synchronized (TwProbe.this) {
                    ok = write(app, what, arg1, arg2, obj);
                    err = writeError;
                }
                if (cb != null) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() { cb.done(ok, err); }
                    });
                }
            }
        });
    }

    /** Forgets an earlier native crash so TWUtil may be tried again (called when the switch is toggled). */
    public synchronized void clearCrash(Context ctx) {
        ctx.getApplicationContext().getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().remove(CRASH).commit();
        if (stage < 4) status = "not started";
    }

    /**
     * Sends one message over TWUtil, the same call the unit's own apps make (tyre pressure reset,
     * Bluetooth keys). Only while "Allow sending commands to the unit" is on in Setup > Vehicle.
     * Starts the channel first when needed. Returns false when refused or when TWUtil is not usable;
     * {@link #writeError()} says why. True means TWUtil accepted the call and did not answer with an
     * error (a negative number).
     */
    public synchronized boolean write(Context ctx, int what, int arg1, int arg2, Object obj) {
        appCtx = ctx.getApplicationContext();
        writeError = "";
        if (!com.thecarlauncher.util.Prefs.bool(appCtx, com.thecarlauncher.util.Prefs.TW_COMMANDS, false)) {
            writeError = "commands are not allowed";
            return false;
        }
        if (stage < 4) openOnly(ctx);
        if (util == null || stage < 4) {
            // status() has the details (and the recorder logged them); the user gets a short fixed reason.
            writeError = absent ? "TWUtil not on this unit"
                    : status.contains("crashed the app") ? "TWUtil crashed the app earlier - not repeated"
                    : "TWUtil could not be opened";
            return false;
        }
        try {
            Method w = obj == null ? find(cls, "write", int.class, int.class, int.class)
                    : find(cls, "write", int.class, int.class, int.class, Object.class);
            if (w == null) {
                writeError = "TWUtil has no matching write";
                return false;
            }
            Object r = obj == null ? w.invoke(util, what, arg1, arg2) : w.invoke(util, what, arg1, arg2, obj);
            com.thecarlauncher.util.AppLog.d(appCtx, "CAN", String.format(Locale.US,
                    "write 0x%X %d/%d%s -> %s", what, arg1, arg2, obj instanceof byte[] ? " " + hex((byte[]) obj) : "", r));
            if (r instanceof Number && ((Number) r).intValue() < 0) {
                writeError = "TWUtil answered " + r;
                return false;
            }
            return true;
        } catch (Throwable t) {
            com.thecarlauncher.util.AppLog.d(appCtx, "CAN", "write failed: " + t);
            writeError = String.valueOf(t.getCause() != null ? t.getCause() : t);
            return false;
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    private synchronized void record(Message m) {
        // 0x501 frames are told apart by arg1 (the Golf7 frame number); other ids by "what" alone.
        long key = ((long) m.what << 16) | (m.what == 0x501 ? (m.arg1 & 0xFFFF) : 0);
        Integer c = counts.get(key);
        counts.put(key, c == null ? 1 : c + 1);
        String obj = "";
        if (m.obj instanceof byte[]) {
            byte[] b = (byte[]) m.obj;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < b.length && i < 24; i++) sb.append(String.format("%02X", b[i]));
            obj = " " + sb;
        } else if (m.obj != null) {
            String s = String.valueOf(m.obj);
            obj = " " + (s.length() > 24 ? s.substring(0, 24) : s);
        }
        // Golf 7 trip frames (19 since start, 20 long-term, 21 since refuelling) feed the trip pages.
        // The car's own service reminder (frame 30), when it was asked for (Vehicle > Service).
        if (m.what == 0x501 && m.arg1 == 30 && m.obj instanceof byte[]) ServiceData.get().decodeFrame((byte[]) m.obj);
        // Doors, belt and the chassis number for the Vehicle status page.
        if (m.what == 0x501 && m.obj instanceof byte[]) VehicleState.get().decodeFrame(appCtx, m.arg1, (byte[]) m.obj);
        // Only while "Take trip data from OBD" is on; the listener itself also serves the recorder.
        if (m.what == 0x501 && m.obj instanceof byte[] && appCtx != null
                && com.thecarlauncher.util.Prefs.bool(appCtx, com.thecarlauncher.util.Prefs.TRIP_OBD, false)
                && ObdData.get().decodeFrame(m.arg1, (byte[]) m.obj)) {
            ObdData.get().save(appCtx);
        }
        String payload = m.arg1 + "/" + m.arg2 + obj;
        latest.put(key, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " " + payload);
        recordToLog(key, m.what, payload);
    }

    /** Sends a frame to the diagnostic recorder when it changed (at most twice a second per frame). */
    private void recordToLog(final long key, final int what, String payload) {
        if (appCtx == null) return;
        long now = System.currentTimeMillis();
        String prev = lastLogged.get(key);
        if (payload.equals(prev)) {
            pending.remove(key); // back to the logged value
            return;
        }
        Long at = lastLoggedAt.get(key);
        if (at != null && now - at < 500) {
            // Too soon after the last line: keep the newest value and log it once the pause is over,
            // so a quick second change is not lost.
            boolean first = pending.put(key, payload) == null;
            if (first && handler != null) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (TwProbe.this) {
                            String p = pending.remove(key);
                            if (p != null && !p.equals(lastLogged.get(key))) emit(key, what, p, System.currentTimeMillis());
                        }
                    }
                }, 500 - (now - at));
            }
            return;
        }
        pending.remove(key);
        emit(key, what, payload, now);
    }

    private void emit(long key, int what, String payload, long now) {
        lastLogged.put(key, payload);
        lastLoggedAt.put(key, now);
        String id = what == 0x501 ? String.format(Locale.US, "0x501/%d", (int) (key & 0xFFFF))
                : String.format(Locale.US, "0x%X", what);
        com.thecarlauncher.util.AppLog.d(appCtx, "CAN", id + " " + payload);
    }
}
