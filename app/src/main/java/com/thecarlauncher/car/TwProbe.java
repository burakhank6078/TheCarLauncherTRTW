package com.thecarlauncher.car;

import android.os.Handler;
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
 * Read-only listener on the TW platform's {@code android.tw.john.TWUtil} (the channel that
 * com.tw.service, com.tw.car and the Canbus decoder use to talk to the MCU). It only listens:
 * nothing is ever written, so the car is not affected. The latest message per id is kept so the
 * Diagnostics page can show which ids carry speed, rpm, doors and so on while the car is driven.
 */
public final class TwProbe {
    private static final TwProbe INSTANCE = new TwProbe();
    private static final String CLASS = "android.tw.john.TWUtil";

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
    public synchronized void start() {
        if (started) return;
        started = true;
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
            // Listen to every id: the real ids are unknown, and listening changes nothing.
            short[] ids = new short[0x10000];
            for (int i = 0; i < ids.length; i++) ids[i] = (short) i;
            Object r = open.invoke(util, (Object) ids);
            Handler h = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message m) { record(m); }
            };
            add.invoke(util, "carlauncher-probe", h);
            if (go != null) go.invoke(util);
            status = "listening (open returned " + r + ")";
        } catch (ClassNotFoundException e) {
            status = "TWUtil not on this unit";
        } catch (Throwable t) {
            Throwable r = t.getCause() != null ? t.getCause() : t;
            status = "error: " + r.getClass().getSimpleName() + " " + r.getMessage();
            started = false;
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
