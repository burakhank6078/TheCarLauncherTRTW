package com.thecarlauncher.util;

import android.content.Context;
import android.content.Intent;

/**
 * Opens the head unit's own car-settings app (TW platform, com.tw.car). Only its main screen can
 * be started from outside; its other screens (tyres, units, lights ...) are not exported, so the
 * user picks them inside that app. Nothing is sent to the car from here.
 */
public final class FactoryCar {
    private static final String PACKAGE = "com.tw.car";
    private static final String MAIN = "com.tw.car.Golf7MainActivity";

    private FactoryCar() {}

    /** Returns false when the app is not on this unit. */
    public static boolean open(Context c) {
        Intent i = new Intent();
        i.setClassName(PACKAGE, MAIN);
        if (Ui.start(c, i)) return true;
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        return launch != null && Ui.start(c, launch);
    }
}
