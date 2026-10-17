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
 * nothing is ever written, so the car is not affected. The latest message per id is kept so the
 * Diagnostics page can show which ids carry speed, rpm, doors and so on while the car is driven.
 *
 * Messages are handled on their own thread, and a flag is stored around the risky call: if the
 * app dies there (native crash), the next tap reports it instead of crashing again.
 */
public final class TwProbe {
    private static final TwProbe INSTANCE = new TwProbe();
    private static final String CLASS = "android.tw.john.TWUtil";
    private static final String FLAG = "twprobe_crashed_flag";

    private final Map<Integer, String> latest = new LinkedHashMap<>();
    private final Map<Integer, Integer> counts = new LinkedHashMap<>();
    private String status = "not started";
    private final List<String> api = new ArrayList<>();
    private Object util;
    private boolean started;

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

    /** Starts listening once; safe to call again. */
    public synchronized void start(Context ctx) {
        if (started) return;
        SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences("launcher", Context.MODE_PRIVATE);
        if (sp.getBoolean(FLAG, false)) {
            sp.edit().putBoolean(FLAG, false).commit();
            status = "last attempt crashed the app - disabled (tap again to retry)";
            return;
        }
        started = true;
        sp.edit().putBoolean(FLAG, true).commit();
        try {
            Class<?> c = Class.forName(CLASS);
            for (Method m : c.getMethods()) {
                if (m.getDeclaringClass() == Object.class) continue;
                api.add(m.getName() + "(" + m.getParameterTypes().length + ")");
            }
            util = newInstance(c);
            Method open = find(c, "open", short[].class);
            Method add = find(c, "addHandler", String.class, Handler.class);
            Method go = find(c, "start");
            if (open == null || add == null) {
                status = "TWUtil found, but open/addHandler missing";
                return;
            }
            // The real ids are unknown: listen to the low block and the 0xF000 block (listening
            // changes nothing).
            short[] ids = new short[0x2000];
            for (int i = 0; i < 0x1000; i++) {
                ids[i] = (short) i;
                ids[0x1000 + i] = (short) (0xF000 + i);
            }
            Object r = open.invoke(util, (Object) ids);
            HandlerThread ht = new HandlerThread("tw-probe");
            ht.start();
            Handler h = new Handler(ht.getLooper()) {
                @Override
                public void handleMessage(Message m) {
                    try {
                        record(m);
                    } catch (Throwable ignored) {
                        // an odd message must never kill the thread
                    }
                }
            };
            add.invoke(util, "carlauncher-probe", h);
            if (go != null) go.invoke(util);
            status = "listening (open returned " + r + ")";
        } catch (ClassNotFoundException e) {
            status = "TWUtil not on this unit";
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            status = "error: " + cause.getClass().getSimpleName() + " " + cause.getMessage();
            started = false;
        } finally {
            sp.edit().putBoolean(FLAG, false).commit();
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
