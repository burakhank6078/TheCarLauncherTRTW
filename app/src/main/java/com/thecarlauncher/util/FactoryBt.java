package com.thecarlauncher.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import java.io.File;
import java.util.List;

/**
 * The Bluetooth side of the head unit. On QF units the launcher talks to the Bluetooth module
 * itself (paired list, search, pairing). On the TW platform that module belongs to the unit's own
 * Bluetooth app (com.tw.bt), so phones are paired and connected there; the launcher only opens it.
 * Nothing is sent to the unit from here.
 */
public final class FactoryBt {
    private static final String PACKAGE = "com.tw.bt";
    private static final String QF_PACKAGE = "com.qf.bluetooth";

    private FactoryBt() {}

    /** True when the QF Bluetooth app is installed (the factory app that owns the QF module). */
    public static boolean hasQfApp(Context c) {
        try {
            c.getPackageManager().getPackageInfo(QF_PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** True on a unit with the QF Bluetooth app or the Goodocom serial port (the launcher's own pairing pages work there). */
    public static boolean qfUnit(Context c) {
        return new File("/dev/goc_serial").exists() || hasQfApp(c);
    }

    /** Opens the unit's own Bluetooth app. Returns false when it is not on this unit. */
    public static boolean open(Context c) {
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if (launch != null && Ui.start(c, launch)) return true;
        // No launcher entry: any main screen of the package will do.
        List<ResolveInfo> list = c.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).setPackage(PACKAGE), 0);
        for (ResolveInfo r : list) {
            Intent i = new Intent(Intent.ACTION_MAIN).setComponent(
                    new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
            if (Ui.start(c, i)) return true;
        }
        return false;
    }
}
