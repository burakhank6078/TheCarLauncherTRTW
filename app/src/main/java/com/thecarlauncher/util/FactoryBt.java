package com.thecarlauncher.util;

import android.content.Context;
import android.content.Intent;

import java.io.File;

/**
 * The Bluetooth side of the head unit. On QF units the launcher talks to the Bluetooth module
 * itself (paired list, search, pairing). On the TW platform that module belongs to the unit's own
 * Bluetooth app (com.tw.bt), so phones are paired and connected there; the launcher only opens it.
 * Nothing is sent to the unit from here.
 */
public final class FactoryBt {
    private static final String PACKAGE = "com.tw.bt";

    private FactoryBt() {}

    /** True on a unit with the QF Bluetooth app or the Goodocom serial port (the launcher's own pairing pages work there). */
    public static boolean qfUnit(Context c) {
        if (new File("/dev/goc_serial").exists()) return true;
        try {
            c.getPackageManager().getPackageInfo("com.qf.bluetooth", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Opens the unit's own Bluetooth app. Returns false when it is not on this unit. */
    public static boolean open(Context c) {
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        return launch != null && Ui.start(c, launch);
    }
}
