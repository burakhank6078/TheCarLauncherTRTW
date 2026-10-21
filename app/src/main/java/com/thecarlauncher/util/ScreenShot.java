package com.thecarlauncher.util;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.mib.Mib;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Takes a picture of the launcher's own window after a countdown, so any screen can be shown in the
 * project's README exactly as it looks on the unit. The PNG goes to a USB stick / SD card or the
 * Download folder (folder "TheCarLauncher-screenshots"). The window is drawn in software, so map
 * tiles and video that live on their own surface are not in the picture. The street name in the
 * status bar is hidden unless switched off in Setup.
 */
public final class ScreenShot {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ScreenShot() {}

    /** Starts the countdown; {@link LauncherActivity#snapshot} is taken when it ends. */
    public static void later(final LauncherActivity act, int seconds) {
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() { capture(act); }
        }, seconds * 1000L);
    }

    private static void capture(final LauncherActivity act) {
        final Bitmap bmp;
        try {
            bmp = act.snapshot(Prefs.bool(act, Prefs.SHOT_HIDE_STREET, true));
        } catch (Throwable t) {
            Ui.toast(act, Mib.t(act, "Slika ekrana nije uspela", "Screenshot failed"));
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String path = save(act, bmp);
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Ui.toast(act, path != null ? Mib.t(act, "Slika ekrana sačuvana: ", "Screenshot saved: ") + path
                                : Mib.t(act, "Slika ekrana nije uspela", "Screenshot failed"));
                    }
                });
            }
        }, "screenshot").start();
    }

    /** Writes the PNG to the first place that accepts it; returns its path or null. */
    private static String save(LauncherActivity act, Bitmap bmp) {
        String name = "screen-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".png";
        for (File root : AppLog.exportDirs(act)) {
            try {
                File dir = new File(root, "TheCarLauncher-screenshots");
                if (!dir.isDirectory() && !dir.mkdirs()) continue;
                File dest = new File(dir, name);
                FileOutputStream os = new FileOutputStream(dest);
                try {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                } finally {
                    os.close();
                }
                if (dest.length() > 0) return dest.getPath();
            } catch (Throwable ignored) {
                // try the next place
            }
        }
        return null;
    }
}
