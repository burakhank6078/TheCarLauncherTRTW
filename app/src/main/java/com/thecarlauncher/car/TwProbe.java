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
 * Read-only listener on the TW platform's {@code android.tw.john.TWUtil} (the channel that
 * com.tw.service, com.tw.car and the Canbus decoder use to talk to the MCU). It only listens:
 * nothing is ever written, so the car is not affected.
 *
 * Opening it in one go crashed the app natively on a real unit, so it now advances one step per
 * tap. The step in progress is stored before it runs; if the app dies there, the next start
 * reports which step crashed and refuses to repeat it.
 */
public final class TwProbe {
    private static final TwProbe INSTANCE = new TwProbe();
    private static final String CLASS = "android.tw.john.TWUtil";
    private static final String CRASH = "twprobe_crash_stage";
    private static final String BUSY = "twprobe_busy_stage";
    private static final int LAST = 6;

    private final Map<Integer, String> latest = new LinkedHashMap<>();
    private final Map<Integer, Integer> counts = new LinkedHashMap<>();
    private String status = "not started";
    private final List<String> api = new ArrayList<>();
    private Object util;
    private Class<?> cls;
    private Handler handler;
    private int stage;

    public static TwProbe get() { return INSTANCE; }

    public synchronized String status() { return status; }

    public synchronized List<String> api() { return new ArrayList<>(api); }

    /** One line per message id: id, count, time, arg1/arg2 and a short view of the payload. */
    public synchronized List<String> summary() {
        List<String> out = new ArrayList<>();
        for (Integer id : new TreeSet<>(latest.keySet())) {
            out.add(String.format(Locale.US, "0x%04X x%d %s", id, counts.get(id), latest.get(id)));
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
            status = "all steps done - " + summary().size() + " ids seen";
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
            case 3: { // open with one id only
                Method open = find(cls, "open", short[].class);
                if (open == null) throw new NoSuchMethodException("open(short[])");
                Object r = open.invoke(util, (Object) new short[]{(short) 0xF001});
                status = "step 3 ok: open(1 id) returned " + r;
                break;
            }
            case 4: { // attach our own handler (own thread)
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
                status = "step 4 ok: handler attached";
                break;
            }
            case 5: { // start delivery
                Method go = find(cls, "start");
                if (go != null) go.invoke(util);
                status = "step 5 ok: started (" + (go != null ? "start()" : "no start()") + ")";
                break;
            }
            default: { // listen to a wider block of ids
                Method open = find(cls, "open", short[].class);
                short[] ids = new short[0x100];
                for (int i = 0; i < ids.length; i++) ids[i] = (short) (0xF000 + i);
                Object r = open.invoke(util, (Object) ids);
                status = "step 6 ok: open(256 ids) returned " + r + " - listening";
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
        Integer c = counts.get(m.what);
        counts.put(m.what, c == null ? 1 : c + 1);
        String obj = "";
        if (m.obj instanceof byte[]) {
            byte[] b = (byte[]) m.obj;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < b.length && i < 16; i++) sb.append(String.format("%02X", b[i]));
            obj = " " + sb;
        } else if (m.obj != null) {
            String s = String.valueOf(m.obj);
            obj = " " + (s.length() > 24 ? s.substring(0, 24) : s);
        }
        latest.put(m.what, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                + " " + m.arg1 + "/" + m.arg2 + obj);
    }
}
