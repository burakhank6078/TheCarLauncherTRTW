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
 * Listener on the TW platform's {@code android.tw.john.TWUtil}, the channel the factory car app (com.tw.car)
 * uses to talk to the Canbus decoder. It opens the same message ids as the factory car app (and, while the
 * diagnostic recorder is on, the ones the Bluetooth and radio apps use) and listens. Writes happen only through
 * {@link #write} and {@link #writeAsync}, which need the "Allow sending commands" switch.
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
    /** The extra ids com.tw.bt opens (the phone: calls, phone book, call log); only listened to while the recorder is on. */
    private static final short[] PHONE_IDS = {262, 274, 515, 769, 1296, -25080, -25059};
    /** Every id com.tw.bt opens, the ones shared with the car app (267, 513) included: anything on them may carry a name or a number. */
    private static final short[] PHONE_ALL = concat(PHONE_IDS, new short[]{267, 513});
    /** The ids com.tw.radio opens besides the shared ones (frequency, band, presets, RDS). */
    private static final short[] RADIO_IDS = {265, 1025, 1026, 1028, 1029, 1030, -25088, -25087};
    /** The radio ids known to carry only tuner data and station text, which are logged as they are; the rest are masked like the phone ids. */
    private static final short[] RADIO_TEXT = {1025, 1026, 1028, 1029, 1030};
    private static final int PROTOCOL_STRING = 0x10A;
    /** Set while the longer id list is being opened; if the app dies there the list is dropped, TWUtil stays usable. */
    private static final String PHONE_TRY = "twprobe_phone_try";
    private static final String PHONE_BAD = "twprobe_phone_bad";

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

    /** The phone and radio ids are not listened to: the recorder was off when the channel was opened (a restart fixes it). */
    public static final int EXTRA_OFF = 0;
    /** The channel was opened with the phone and radio ids. */
    public static final int EXTRA_ON = 1;
    /** The longer list cannot be used on this unit (refused, or the app stopped while opening it); a restart does not help. */
    public static final int EXTRA_UNAVAILABLE = 2;
    private volatile int extra = EXTRA_OFF;
    private volatile int extraSeen;

    /** One of the EXTRA_ constants. */
    public int extraState() { return extra; }

    public boolean phoneListening() { return extra == EXTRA_ON; }

    private static short[] concat(short[]... parts) {
        int n = 0;
        for (short[] p : parts) n += p.length;
        short[] out = new short[n];
        int at = 0;
        for (short[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    public synchronized String status() { return status; }

    public synchronized List<String> api() { return new ArrayList<>(api); }

    /** One line per message: id (0x501 also by frame number), count, time, arg1/arg2, payload. */
    public synchronized List<String> summary() {
        List<String> out = new ArrayList<>();
        for (Long k : new TreeSet<>(latest.keySet())) {
            out.add(String.format(Locale.US, "%s x%d %s", label(k), counts.get(k), latest.get(k)));
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
        if (busy == 3 && sp.getBoolean(PHONE_TRY, false)) {
            // The app died opening the longer id list: stop using that list, but do not lock TWUtil out.
            sp.edit().putBoolean(PHONE_BAD, true).putBoolean(PHONE_TRY, false).putInt(BUSY, 0).commit();
        } else if (busy != 0) {
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
                // While the recorder is on, also listen to the phone and radio ids so a test can show how the unit
                // talks about calls, the phone book and the tuner; without the recorder nothing changes from before.
                SharedPreferences sp = appCtx.getSharedPreferences("launcher", Context.MODE_PRIVATE);
                boolean bad = sp.getBoolean(PHONE_BAD, false);
                boolean phone = com.thecarlauncher.util.AppLog.enabled(appCtx) && !bad;
                short[] ids = phone ? concat(IDS, PHONE_IDS, RADIO_IDS) : IDS;
                Object r;
                try {
                    if (phone) sp.edit().putBoolean(PHONE_TRY, true).commit();
                    r = open.invoke(util, (Object) ids);
                } finally {
                    if (phone) sp.edit().putBoolean(PHONE_TRY, false).commit();
                }
                if (phone && !Integer.valueOf(0).equals(r)) {
                    // The longer list was refused: fall back to the factory car app's own ids on a fresh object.
                    util = newInstance(cls);
                    ids = IDS;
                    r = open.invoke(util, (Object) ids);
                    phone = false;
                    bad = true;
                }
                extra = phone ? EXTRA_ON : bad ? EXTRA_UNAVAILABLE : EXTRA_OFF;
                status = "step 3 ok: open(" + ids.length + " ids" + (phone ? ", with phone and radio" : "") + ") returned " + r;
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
                if (extra == EXTRA_ON && appCtx != null) {
                    // A list longer than any factory app opens may be cut without an error; this line tells a test whether
                    // anything from the phone or radio ids ever arrived.
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (extraSeen == 0) {
                                com.thecarlauncher.util.AppLog.d(appCtx, "CAN", "no phone or radio message in 15 s "
                                        + "(expected if neither was used; if the radio or the phone was in use, the id list may have been cut)");
                            }
                        }
                    }, 15000);
                }
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

    /**
     * What goes into the recording for a byte payload. Anything on an id the Bluetooth app listens to (phone book
     * entries, caller ids) is written as a shape, so the recording holds no name or number; other messages stay
     * as hex, with the chassis number (0x501 frame 31) cut to its first three characters.
     */
    static String describe(int what, int arg1, byte[] b) {
        if (isMasked(what)) return shape(b);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length && i < 24; i++) {
            if (what == 0x501 && arg1 == 31 && i >= 3 && i < 17) sb.append("XX");
            else sb.append(String.format(Locale.US, "%02X", b[i]));
        }
        return sb.toString();
    }

    private static boolean isPhoneId(int what) {
        for (short id : PHONE_ALL) if ((id & 0xFFFF) == (what & 0xFFFF)) return true;
        return false;
    }

    private static boolean isRadioId(int what) {
        for (short id : RADIO_IDS) if ((id & 0xFFFF) == (what & 0xFFFF)) return true;
        return false;
    }

    /** True for the ids whose payloads are written as a shape: the phone ids and the radio ids not known to be tuner data. */
    private static boolean isMasked(int what) {
        if (isPhoneId(what)) return true;
        if (!isRadioId(what)) return false;
        for (short id : RADIO_TEXT) if ((id & 0xFFFF) == (what & 0xFFFF)) return false;
        return true;
    }

    /** True for an id that only the phone or radio listening adds (not one the car app opens as well). */
    private static boolean isExtraId(int what) {
        for (short id : PHONE_IDS) if ((id & 0xFFFF) == (what & 0xFFFF)) return true;
        return isRadioId(what);
    }

    /** Log name of a message kind: the id, plus arg1 for car frames and for the phone and radio ids (where arg1 is the command). */
    private static String label(long key) {
        int what = (int) (key >> 16);
        if (what == 0x501 || isPhoneId(what) || isRadioId(what)) return String.format(Locale.US, "0x%X/%d", what, (int) (key & 0xFFFF));
        return String.format(Locale.US, "0x%X", what);
    }

    /** Digits become #, letters a or A, non-ASCII characters *; punctuation that helps reading stays. */
    static String shape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && i < 64; i++) sb.append(shapeChar(s.charAt(i)));
        if (s.length() > 64) sb.append('…');
        return "\"" + sb + "\"";
    }

    /** Like {@link #shape(String)} for raw bytes: 00 is ~, bytes of 80 and up and control bytes stay as {hex} to show the encoding. */
    static String shape(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length && i < 64; i++) {
            int v = b[i] & 0xFF;
            if (v == 0) sb.append('~');
            else if (v < 0x20 || v >= 0x80) sb.append(String.format(Locale.US, "{%02X}", v));
            else sb.append(shapeChar((char) v));
        }
        if (b.length > 64) sb.append('…');
        return "bytes[" + b.length + "] " + sb;
    }

    private static char shapeChar(char c) {
        if (c >= '0' && c <= '9') return '#';
        if (c >= 'a' && c <= 'z') return 'a';
        if (c >= 'A' && c <= 'Z') return 'A';
        if (c >= 0x80) return '*';
        if (" +-.,/()".indexOf(c) >= 0) return c;
        return '_';
    }

    private synchronized void record(Message m) {
        // 0x501 frames are told apart by arg1 (the Golf7 frame number), the phone ids by arg1 too (the command);
        // other ids by "what" alone.
        long key = ((long) m.what << 16)
                | (m.what == 0x501 || isPhoneId(m.what) || isRadioId(m.what) ? (m.arg1 & 0xFFFF) : 0);
        Integer c = counts.get(key);
        counts.put(key, c == null ? 1 : c + 1);
        String obj = "";
        if (m.obj instanceof byte[]) {
            obj = " " + describe(m.what, m.arg1, (byte[]) m.obj);
        } else if (m.obj != null) {
            String s = String.valueOf(m.obj);
            // Radio text (station names, RDS) is not personal and is wanted as it is; everything else is masked.
            boolean plain = m.what == PROTOCOL_STRING || (isRadioId(m.what) && !isMasked(m.what));
            int cut = isRadioId(m.what) ? 64 : 24;
            obj = " " + (plain ? (s.length() > cut ? s.substring(0, cut) : s) : shape(s));
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
        if (isExtraId(m.what)) extraSeen++;
        // Phone traffic is sparse and wanted in full; the tuner's status changes many times a second, so it
        // goes through the normal change check and pause (two lines a second at most per kind).
        if (isPhoneId(m.what)) logPhone(key, payload);
        else recordToLog(key, payload);
    }

    /**
     * Phone messages are the point of the phone test, so each one is logged (no pause between lines); after the
     * first 60 of a kind, like the entries of a downloaded phone book, only a changed shape and every 25th message.
     */
    private void logPhone(long key, String payload) {
        if (appCtx == null) return;
        int n = counts.get(key);
        long now = System.currentTimeMillis();
        if (n <= 60) emit(key, payload, now);
        else if (!payload.equals(lastLogged.get(key))) emit(key, payload + " #" + n, now);
        else if (n % 25 == 0) emit(key, payload + " #" + n, now);
    }

    /** Sends a frame to the diagnostic recorder when it changed (at most twice a second per frame). */
    private void recordToLog(final long key, String payload) {
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
                            if (p != null && !p.equals(lastLogged.get(key))) emit(key, p, System.currentTimeMillis());
                        }
                    }
                }, 500 - (now - at));
            }
            return;
        }
        pending.remove(key);
        emit(key, payload, now);
    }

    private void emit(long key, String payload, long now) {
        lastLogged.put(key, payload);
        lastLoggedAt.put(key, now);
        com.thecarlauncher.util.AppLog.d(appCtx, "CAN", label(key) + " " + payload);
    }
}
