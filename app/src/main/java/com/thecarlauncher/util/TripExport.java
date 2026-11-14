package com.thecarlauncher.util;

import android.content.Context;

import com.thecarlauncher.car.TripCore;
import com.thecarlauncher.car.TripLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The trip history as a CSV file for a USB stick (or the Downloads folder), one line per trip, oldest first, for a
 * spreadsheet. A Turkish spreadsheet wants a semicolon between the values and a comma in the numbers, so the file is made
 * that way when the launcher is in Turkish; otherwise it is the usual comma and a decimal point. It starts with the byte
 * order mark so the accents and the arrows read right.
 */
public final class TripExport {
    public static final String FILE = "TheCarLauncher-yolculuklar.csv";

    private TripExport() {}

    /** The whole file: a header line and a line per trip. trips is as TripLog gives it, newest first. */
    static String csv(List<TripCore.Trip> newestFirst, char sep, char decimal, boolean turkish) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder sb = new StringBuilder();
        String[] head = turkish
                ? new String[]{"Başlangıç", "Bitiş", "Süre (dk)", "Mesafe (km)", "Ortalama hız (km/s)", "En yüksek hız (km/s)",
                "Tüketim (l/100 km)", "Sert fren", "Sert kalkış", "Puan"}
                : new String[]{"Start", "End", "Duration (min)", "Distance (km)", "Average speed (km/h)", "Top speed (km/h)",
                "Consumption (l/100 km)", "Hard braking", "Hard acceleration", "Score"};
        for (int i = 0; i < head.length; i++) sb.append(i == 0 ? "" : String.valueOf(sep)).append(head[i]);
        sb.append("\r\n");
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            TripCore.Trip t = newestFirst.get(i);
            String[] f = {
                    fmt.format(new Date(t.start)),
                    fmt.format(new Date(t.end)),
                    String.valueOf(Math.round(t.durationMs() / 60000.0)),
                    num(t.km, 2, decimal),
                    num(t.avgKmh(), 1, decimal),
                    num(t.maxKmh, 0, decimal),
                    t.consumption >= 0 ? num(t.consumption, 1, decimal) : "",
                    t.brakes >= 0 ? String.valueOf(t.brakes) : "",
                    t.accels >= 0 ? String.valueOf(t.accels) : "",
                    t.score() >= 0 ? String.valueOf(t.score()) : ""};
            for (int k = 0; k < f.length; k++) sb.append(k == 0 ? "" : String.valueOf(sep)).append(f[k]);
            sb.append("\r\n");
        }
        return sb.toString();
    }

    private static String num(double v, int digits, char decimal) {
        return String.format(Locale.US, "%." + digits + "f", v).replace('.', decimal);
    }

    /** Writes the file to the first place that accepts one (USB, SD card, Downloads); the path, or null. */
    public static String export(Context c) {
        boolean tr = "tr".equals(Prefs.str(c, Prefs.LANGUAGE, "sr"));
        String text = "﻿" + csv(TripLog.get(c).trips(), tr ? ';' : ',', tr ? ',' : '.', tr);
        for (File dir : AppLog.exportDirs(c)) {
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) continue;
                File dest = new File(dir, FILE);
                OutputStream os = new FileOutputStream(dest);
                try {
                    os.write(text.getBytes("UTF-8"));
                } finally {
                    os.close();
                }
                if (dest.length() > 0) return dest.getPath();
            } catch (IOException ignored) {
                // try the next place
            }
        }
        return null;
    }
}
