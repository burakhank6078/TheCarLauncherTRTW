package com.thecarlauncher.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostic recorder (Setup > Diagnostic recording). When switched on it writes everything needed
 * to understand this head unit into one text file that can be exported to a USB stick:
 * a system snapshot, this app's own logcat, crashes, Canbus frames and Bluetooth commands.
 * It never records positions, and it only reads: nothing is sent to the car.
 *
 * The file lives in the app's own storage and is capped (two files of ~1.5 MB, the older one is
 * replaced), so it can stay on for days.
 */
public final class AppLog {
    private static final String NAME = "carlauncher-log.txt", OLD = "carlauncher-log.1.txt";
    private static final long MAX = 1500 * 1024;
    public static final String LOG_ENABLED = "diag_log_enabled";

    private static AppLog instance;

    private final Context app;
    private final HandlerThread thread = new HandlerThread("applog");
    private final Handler io;
    private final SimpleDateFormat stamp = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    private File file;
    private PrintWriter out;
    private Process logcat;
    private boolean running;
    private boolean crashHandlerSet;
    /** Bumped on every start and stop so an old logcat reader ends instead of writing twice. */
    private volatile int generation;

    private AppLog(Context c) {
        app = c.getApplicationContext();
        thread.start();
        io = new Handler(thread.getLooper());
    }

    private static synchronized AppLog get(Context c) {
        if (instance == null) instance = new AppLog(c);
        return instance;
    }

    public static boolean enabled(Context c) { return Prefs.bool(c, LOG_ENABLED, false); }

    /** Switches the recorder on or off and starts / stops it straight away. */
    public static void setEnabled(Context c, boolean on) {
        Prefs.put(c, LOG_ENABLED, on);
        if (on) start(c);
        else get(c).stop();
    }

    /** Call once when the launcher starts; does nothing while the recorder is off. */
    public static void start(Context c) {
        if (enabled(c)) get(c).begin();
    }

    /** Records one line; a no-op while the recorder is off. Safe to call from any thread. */
    public static void d(Context c, String tag, String msg) {
        if (c == null || !enabled(c)) return;
        get(c).write(tag, msg);
    }

    // ---- Recording ------------------------------------------------------------------------

    private synchronized void begin() {
        if (running) return;
        running = true;
        io.post(new Runnable() {
            @Override
            public void run() { openFile(); }
        });
        write("LOG", "---- recording started ----");
        installCrashHandler();
        snapshot();
        startLogcat();
        try {
            com.thecarlauncher.car.TwProbe.get().autoStart(app);
        } catch (Throwable t) {
            write("CAN", "auto start failed: " + t);
        }
    }

    private synchronized void stop() {
        if (!running) return;
        write("LOG", "---- recording stopped ----");
        running = false;
        com.thecarlauncher.car.TwProbe.get().closeExtras(); // the radio and phone channels are only for recording
        generation++; // ends the logcat reader
        if (logcat != null) {
            logcat.destroy();
            logcat = null;
        }
    }

    private File dir() {
        File d = app.getExternalFilesDir(null);
        if (d == null) d = app.getFilesDir();
        return d;
    }

    private void openFile() {
        try {
            file = new File(dir(), NAME);
            out = new PrintWriter(new FileWriter(file, true), false);
        } catch (Throwable t) {
            out = null;
        }
    }

    private void write(final String tag, final String msg) {
        final long now = System.currentTimeMillis();
        final String text = tag + " " + msg;
        io.post(new Runnable() {
            @Override
            public void run() { append(stamp.format(new Date(now)) + " " + text); } // SimpleDateFormat only on this thread
        });
    }

    private void append(String line) {
        if (out == null) openFile();
        if (out == null) return;
        out.println(line);
        out.flush();
        if (file != null && file.length() > MAX) rotate();
    }

    private void rotate() {
        try {
            out.close();
            File old = new File(dir(), OLD);
            if (old.exists()) old.delete();
            file.renameTo(old);
        } catch (Throwable ignored) {
        }
        openFile();
    }

    /** Writes a crash straight to the file before the process dies. */
    private void installCrashHandler() {
        if (crashHandlerSet) return; // once only; it stays quiet while the recorder is off
        crashHandlerSet = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    if (!running) throw new IllegalStateException("recorder off");
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    synchronized (AppLog.this) {
                        PrintWriter w = new PrintWriter(new FileWriter(new File(dir(), NAME), true), true);
                        w.println(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + " CRASH in thread " + t.getName() + "\n" + sw);
                        w.close();
                    }
                } catch (Throwable ignored) {
                }
                if (previous != null) previous.uncaughtException(t, e);
            }
        });
    }

    /** This app's own logcat (an app can only read its own lines on Android 4.1+). */
    private void startLogcat() {
        final int gen = ++generation;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-v", "threadtime", "-T", "1"});
                    synchronized (AppLog.this) {
                        if (gen != generation) {
                            p.destroy(); // stopped while starting
                            return;
                        }
                        logcat = p;
                    }
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String l;
                    while (gen == generation && (l = r.readLine()) != null) {
                        if (l.length() > 400) l = l.substring(0, 400);
                        write("LC", l);
                    }
                } catch (Throwable t) {
                    write("LC", "logcat not available: " + t);
                }
            }
        }, "applog-logcat").start();
    }

    // ---- System snapshot ------------------------------------------------------------------

    private static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class).invoke(null, key);
        } catch (Throwable t) {
            return "?";
        }
    }

    private void snapshot() {
        write("SYS", "model=" + Build.MODEL + " device=" + Build.DEVICE + " sdk=" + Build.VERSION.SDK_INT
                + " fingerprint=" + Build.FINGERPRINT);
        try {
            PackageInfo me = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            write("SYS", "launcher " + me.versionName + " (" + me.versionCode + ") language="
                    + Prefs.str(app, Prefs.LANGUAGE, "sr"));
        } catch (Throwable ignored) {
        }
        String[] keys = {"ro.tw.version", "ro.systemtw.version", "persist.sys.tw.car_type", "ro.board.platform",
                "ro.build.display.id", "ro.product.board"};
        StringBuilder sb = new StringBuilder();
        for (String k : keys) sb.append(k).append('=').append(prop(k)).append(' ');
        try {
            sb.append("Settings.CAR_TYPE=").append(Settings.System.getString(app.getContentResolver(), "CAR_TYPE"));
        } catch (Throwable ignored) {
        }
        write("SYS", sb.toString());
        // Which sound chip the factory EQ app drives (it reads these to pick its screen).
        String[] audio = {"persist.sys.tw.dsp", "persist.sys.eqtype.2313", "persist.sys.eqtype.3702",
                "persist.sys.eqtype.rohm", "sys.tw.audioSession.btmusicid"};
        StringBuilder ab = new StringBuilder("audio ");
        for (String k : audio) ab.append(k).append('=').append(prop(k)).append(' ');
        try {
            ab.append("Settings.EQ_SETTINGS=").append(Settings.System.getString(app.getContentResolver(), "EQ_SETTINGS"));
        } catch (Throwable ignored) {
        }
        write("SYS", ab.toString());
        try {
            List<PackageInfo> all = app.getPackageManager().getInstalledPackages(0);
            StringBuilder p = new StringBuilder();
            for (PackageInfo pi : all) {
                String n = pi.packageName;
                if (n.startsWith("com.tw.") || n.startsWith("com.dofun") || n.startsWith("com.zjinnova")
                        || n.startsWith("com.qf") || n.contains("bluetooth") || n.contains("zlink")) {
                    boolean sys = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    p.append(n).append(' ').append(pi.versionName).append(sys ? " [sys]" : "").append("\n   ");
                }
            }
            write("PKG", "\n   " + p);
        } catch (Throwable t) {
            write("PKG", "failed: " + t);
        }
        try {
            String[] services = (String[]) Class.forName("android.os.ServiceManager").getMethod("listServices").invoke(null);
            StringBuilder s = new StringBuilder();
            if (services != null) {
                for (String sv : services) {
                    String l = sv.toLowerCase(Locale.ROOT);
                    if (l.contains("tw") || l.contains("car") || l.contains("bt") || l.contains("blue")
                            || l.contains("mcu") || l.contains("qf") || l.contains("avrcp")) s.append(sv).append(' ');
                }
            }
            write("SVC", s.toString());
        } catch (Throwable t) {
            write("SVC", "failed: " + t);
        }
        write("PREF", "bt_pkg=" + Prefs.str(app, Prefs.BT_PKG, null) + " bt_session_pkg="
                + Prefs.str(app, Prefs.BT_SESSION_PKG, null) + " bt_system_keys="
                + Prefs.bool(app, Prefs.BT_SYSTEM_KEYS, false));
    }

    // ---- Export ---------------------------------------------------------------------------

    /** Size of the recorded data in bytes. */
    public static long size(Context c) {
        File d = get(c).dir();
        long n = 0;
        for (String f : new String[]{NAME, OLD}) n += new File(d, f).length();
        return n;
    }

    public static String folder(Context c) { return get(c).dir().getPath(); }

    public static void clear(Context c) {
        final AppLog l = get(c);
        l.io.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (l.out != null) l.out.close();
                } catch (Throwable ignored) {
                }
                l.out = null;
                new File(l.dir(), NAME).delete();
                new File(l.dir(), OLD).delete();
            }
        });
    }

    /** Places a file can be exported to, best first: USB / SD card roots, then the Download folder. */
    public static java.util.List<File> exportDirs(Context c) {
        java.util.List<File> targets = new java.util.ArrayList<>();
        for (File root : Storage.removableRoots(c)) targets.add(root);
        File[] ext = c.getExternalFilesDirs(null);
        if (ext != null) for (int i = 1; i < ext.length; i++) if (ext[i] != null) targets.add(ext[i]);
        targets.add(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS));
        return targets;
    }

    /**
     * Copies the log to a USB stick / SD card (first place that accepts a file), falling back to the
     * Download folder. Returns the written path, or null when nothing worked.
     */
    public static String export(Context c) {
        AppLog l = get(c);
        String name = "carlauncher-log-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
        for (File dir : exportDirs(c)) {
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) continue;
                File dest = new File(dir, name);
                OutputStream os = new FileOutputStream(dest);
                try {
                    for (String f : new String[]{OLD, NAME}) {
                        File src = new File(l.dir(), f);
                        if (!src.exists()) continue;
                        InputStream in = new FileInputStream(src);
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                        in.close();
                    }
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
