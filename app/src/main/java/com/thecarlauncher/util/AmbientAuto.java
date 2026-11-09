package com.thecarlauncher.util;

import android.content.Context;

import com.thecarlauncher.car.AmbientAutoLogic;
import com.thecarlauncher.car.VehicleState;

/**
 * The Android side of {@link AmbientAutoLogic}: reads the settings and the car's ignition and headlight flags, and when the
 * share for the strip changes sends the strip its levels again. Called once a second by CarEvents' own thread, so it works
 * whatever app is in front. The screen's colour is not touched, only what the strip gets.
 */
public final class AmbientAuto {
    private static final AmbientAuto INSTANCE = new AmbientAuto();

    private final AmbientAutoLogic logic = new AmbientAutoLogic();

    private AmbientAuto() {}

    public static AmbientAuto get() { return INSTANCE; }

    public synchronized void step(Context c, long now) {
        AmbientAutoLogic.Params p = new AmbientAutoLogic.Params(
                Prefs.bool(c, Prefs.AMBIENT_AUTO, false),
                Prefs.bool(c, Prefs.AMBIENT_AUTO_OFF, true),
                Prefs.integer(c, Prefs.AMBIENT_AUTO_DELAY, 30),
                Prefs.integer(c, Prefs.AMBIENT_AUTO_NIGHT, 40),
                Prefs.integer(c, Prefs.AMBIENT_AUTO_DAY, 100));
        VehicleState vs = VehicleState.get();
        int percent = logic.step(now, vs.ignition(), vs.lights(), p);
        if (AmbientBle.setAutoPercent(percent)) {
            AppLog.d(c, "BLE", "automation share " + percent);
            AmbientBle.get(c).syncFromPrefs();
        }
    }
}
