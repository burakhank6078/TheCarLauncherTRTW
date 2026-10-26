package com.thecarlauncher.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.thecarlauncher.car.VehicleState;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Day or night for the map, from the car's headlights (car info service #5): night while they are on, day while they are
 * off. The lights must keep their new state for a few seconds before the mode flips, so flashing the high beam or a
 * short tunnel does not make the map jump. Only used when "Automatic night map" is on; the lights are only known while
 * "Take trip data from OBD" is on, otherwise nothing changes. Brightness is never touched.
 */
public final class DayNight {
    public interface Listener { void onDayNightChanged(); }

    /** How long the lights have to hold their new state. */
    public static final long HOLD_MS = 4000;

    /** The decision without Android, so it can be tested. */
    static final class Core {
        boolean night;
        private int pending = -1;
        private long since;

        /** lights: 1 on, 0 off, -1 not known (keeps the current mode). True when the mode flipped. */
        boolean step(long now, int lights) {
            if (lights < 0) {
                pending = -1;
                return false;
            }
            boolean want = lights == 1;
            if (want == night) {
                pending = -1;
                return false;
            }
            if (pending != lights) {
                pending = lights;
                since = now;
                return false;
            }
            if (now - since >= HOLD_MS) {
                night = want;
                pending = -1;
                return true;
            }
            return false;
        }

        void reset() {
            night = false;
            pending = -1;
        }
    }

    private static DayNight sInstance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Core core = new Core();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public static synchronized DayNight get(Context c) {
        if (sInstance == null) sInstance = new DayNight(c.getApplicationContext());
        return sInstance;
    }

    private DayNight(Context c) { app = c; }

    public void addListener(Listener l) { listeners.addIfAbsent(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    /** True when the automatic mode is on and the headlights say night. */
    public boolean night() {
        return Prefs.bool(app, Prefs.AUTO_NIGHT, false) && core.night;
    }

    /** Call about once a second on the main thread. */
    public void tick() {
        if (!Prefs.bool(app, Prefs.AUTO_NIGHT, false)) {
            if (core.night) {
                core.reset();
                notifyChanged();
            }
            return;
        }
        if (core.step(System.currentTimeMillis(), VehicleState.get().lights())) notifyChanged();
    }

    private void notifyChanged() {
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : listeners) l.onDayNightChanged();
            }
        });
    }
}
