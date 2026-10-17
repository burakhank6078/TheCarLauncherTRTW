package com.thecarlauncher.car;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
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
        latest.put(key, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                + " " + m.arg1 + "/" + m.arg2 + obj);
    }
}
